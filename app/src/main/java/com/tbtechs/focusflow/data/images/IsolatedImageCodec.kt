package com.tbtechs.focusflow.data.images

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * An [ImageCodec] that performs every native image operation in [ImageDecodeService], an
 * isolated process. This is a second layer: callers keep their own magic-byte, size and
 * dimension checks.
 *
 * It fails closed. If the isolated process cannot start, crashes, or does not answer within
 * [timeoutMillis], the call returns null (or the neutral default for orientation) and the
 * caller rejects the image. A crashing decoder only takes the isolated process down; the
 * next call starts a fresh one.
 *
 * Calls block, so call them from a background thread (all current callers already do).
 * Requests are serialised. The isolated process is released after a short idle period.
 */
class IsolatedImageCodec(
    context: Context,
    private val timeoutMillis: Long = ImageDecodeProtocol.REQUEST_TIMEOUT_MILLIS,
    private val idleMillis: Long = ImageDecodeProtocol.IDLE_DISCONNECT_MILLIS,
) : ImageCodec {
    private val appContext = context.applicationContext

    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "image-decode-client").apply { isDaemon = true }
    }
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "image-decode-idle").apply { isDaemon = true }
    }
    private val replyThread by lazy { HandlerThread("image-decode-reply").also { it.start() } }
    private val replyMessenger by lazy {
        Messenger(
            Handler(replyThread.looper, Handler.Callback { message ->
                onReply(message)
                true
            }),
        )
    }

    private val requestLock = ReentrantLock()
    private val requestIds = AtomicLong(0)
    @Volatile private var link: Link? = null
    @Volatile private var pending: Pending? = null
    private var idleTask: ScheduledFuture<*>? = null

    override fun bounds(bytes: ByteArray): ImageBounds? {
        val reply = call(ImageDecodeProtocol.OP_BOUNDS, bytes, Bundle(), wantOutput = false) ?: return null
        return if (reply.a1 > 0 && reply.a2 > 0) ImageBounds(reply.a1, reply.a2) else null
    }

    override fun orientation(bytes: ByteArray): Int {
        val reply = call(ImageDecodeProtocol.OP_ORIENTATION, bytes, Bundle(), wantOutput = false)
        return reply?.a1 ?: 1
    }

    override fun reencode(bytes: ByteArray, maxLongEdge: Int, quality: Int): ByteArray? {
        val extras = Bundle().apply {
            putInt(ImageDecodeProtocol.KEY_MAX_EDGE, maxLongEdge)
            putInt(ImageDecodeProtocol.KEY_QUALITY, quality)
            putBoolean(ImageDecodeProtocol.KEY_DETECT, true)
        }
        return call(ImageDecodeProtocol.OP_REENCODE, bytes, extras, wantOutput = true)?.output
    }

    override fun reencodeWithOrientation(
        bytes: ByteArray,
        maxLongEdge: Int,
        quality: Int,
        orientation: Int,
        pngSource: Boolean,
    ): ByteArray? {
        val extras = Bundle().apply {
            putInt(ImageDecodeProtocol.KEY_MAX_EDGE, maxLongEdge)
            putInt(ImageDecodeProtocol.KEY_QUALITY, quality)
            putInt(ImageDecodeProtocol.KEY_ORIENTATION, orientation)
            putBoolean(ImageDecodeProtocol.KEY_PNG, pngSource)
        }
        return call(ImageDecodeProtocol.OP_REENCODE, bytes, extras, wantOutput = true)?.output
    }

    // ── Plumbing ────────────────────────────────────────────────────────────

    private class Exchange(val a1: Int, val a2: Int, val output: ByteArray?)

    private class Pending(val id: Long) {
        val latch = CountDownLatch(1)
        @Volatile var ok = false
        @Volatile var a1 = 0
        @Volatile var a2 = 0

        fun fail() {
            ok = false
            latch.countDown()
        }
    }

    private class Link : ServiceConnection {
        val latch = CountDownLatch(1)
        @Volatile var messenger: Messenger? = null
        @Volatile var dead = false
        @Volatile var onDied: (() -> Unit)? = null

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            messenger = binder?.let { Messenger(it) }
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) = died()
        override fun onBindingDied(name: ComponentName?) = died()
        override fun onNullBinding(name: ComponentName?) = died()

        private fun died() {
            dead = true
            messenger = null
            latch.countDown()
            onDied?.invoke()
        }
    }

    private fun call(op: Int, bytes: ByteArray, extras: Bundle, wantOutput: Boolean): Exchange? {
        val future = executor.submit(Callable { exchange(op, bytes, extras, wantOutput) })
        return try {
            // Lock wait (<= timeoutMillis) plus the request itself (<= timeoutMillis).
            future.get(timeoutMillis * 2 + 1_000L, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            forceReset()
            future.cancel(true)
            null
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            forceReset()
            future.cancel(true)
            null
        } catch (_: ExecutionException) {
            null
        } catch (_: CancellationException) {
            null
        }
    }

    private fun exchange(op: Int, bytes: ByteArray, extras: Bundle, wantOutput: Boolean): Exchange? {
        if (!requestLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) return null
        try {
            idleTask?.cancel(false)
            val deadline = SystemClock.elapsedRealtime() + timeoutMillis
            val activeLink = ensureLink(deadline) ?: return null
            val service = activeLink.messenger ?: return null

            val inPipe = ParcelFileDescriptor.createPipe()
            val outPipe = if (wantOutput) ParcelFileDescriptor.createPipe() else null
            val request = Pending(requestIds.incrementAndGet())
            pending = request
            activeLink.onDied = { request.fail() }
            try {
                val message = Message.obtain(null, op).apply {
                    replyTo = replyMessenger
                    data = Bundle(extras).apply {
                        putLong(ImageDecodeProtocol.KEY_ID, request.id)
                        putParcelable(ImageDecodeProtocol.KEY_INPUT, inPipe[0])
                        if (outPipe != null) putParcelable(ImageDecodeProtocol.KEY_OUTPUT, outPipe[1])
                    }
                }
                try {
                    service.send(message)
                } catch (_: RemoteException) {
                    closeQuietly(inPipe[1])
                    outPipe?.let { closeQuietly(it[0]) }
                    dropLink()
                    return null
                } finally {
                    // Our copies of the service's ends must be closed or it never sees EOF.
                    closeQuietly(inPipe[0])
                    outPipe?.let { closeQuietly(it[1]) }
                }

                executor.execute {
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(inPipe[1]).use { it.write(bytes) }
                    } catch (_: IOException) {
                        // The service will see a truncated input and fail the request.
                    }
                }

                var output: ByteArray? = null
                if (outPipe != null) {
                    output = try {
                        ParcelFileDescriptor.AutoCloseInputStream(outPipe[0]).use {
                            readBounded(it, ImageDecodeProtocol.MAX_OUTPUT_BYTES)
                        }
                    } catch (_: IOException) {
                        null
                    }
                }

                val remaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1_000L)
                val answered = request.latch.await(remaining, TimeUnit.MILLISECONDS)
                if (!answered || (activeLink.dead && !request.ok)) {
                    // Timed out or the process died: start from a fresh process next time.
                    dropLink()
                    return null
                }
                if (!request.ok) return null
                if (wantOutput && (output == null || output.size != request.a1)) return null
                return Exchange(request.a1, request.a2, output)
            } finally {
                pending = null
                activeLink.onDied = null
            }
        } finally {
            scheduleIdleDisconnect()
            requestLock.unlock()
        }
    }

    private fun ensureLink(deadlineMillis: Long): Link? {
        link?.let { existing ->
            if (!existing.dead && existing.messenger != null) return existing
            dropLink()
        }
        val fresh = Link()
        val intent = Intent(appContext, ImageDecodeService::class.java)
        val bound = try {
            appContext.bindService(intent, Context.BIND_AUTO_CREATE, executor, fresh)
        } catch (_: SecurityException) {
            false
        }
        if (!bound) {
            try { appContext.unbindService(fresh) } catch (_: IllegalArgumentException) { }
            return null
        }
        link = fresh
        val wait = deadlineMillis - SystemClock.elapsedRealtime()
        val connected = wait > 0 && fresh.latch.await(wait, TimeUnit.MILLISECONDS)
        if (!connected || fresh.dead || fresh.messenger == null) {
            dropLink()
            return null
        }
        return fresh
    }

    private fun dropLink() {
        val current = link ?: return
        link = null
        current.dead = true
        try { appContext.unbindService(current) } catch (_: IllegalArgumentException) { }
    }

    /** Safe to call while another thread holds the request lock; it never waits for it. */
    private fun forceReset() {
        dropLink()
        pending?.fail()
    }

    private fun scheduleIdleDisconnect() {
        idleTask?.cancel(false)
        idleTask = try {
            scheduler.schedule(
                {
                    if (requestLock.tryLock()) {
                        try { dropLink() } finally { requestLock.unlock() }
                    }
                },
                idleMillis,
                TimeUnit.MILLISECONDS,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun onReply(message: Message) {
        val data = message.data
        val request = pending ?: return
        if (data.getLong(ImageDecodeProtocol.KEY_ID) != request.id) return // stale reply
        request.ok = message.what == ImageDecodeProtocol.REPLY_OK
        request.a1 = data.getInt(ImageDecodeProtocol.KEY_A1)
        request.a2 = data.getInt(ImageDecodeProtocol.KEY_A2)
        request.latch.countDown()
    }

    private fun readBounded(stream: InputStream, maxBytes: Int): ByteArray? {
        val sink = ByteArrayOutputStream(64 * 1024)
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (count > maxBytes - total) return null
            sink.write(buffer, 0, count)
            total += count
        }
        return sink.toByteArray()
    }

    private fun closeQuietly(descriptor: ParcelFileDescriptor) {
        try { descriptor.close() } catch (_: IOException) { }
    }
}
