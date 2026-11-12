package com.tbtechs.focusflow.data.images

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Decodes and re-encodes untrusted images inside an *isolated* process
 * (`android:isolatedProcess="true"`): no app data, no network, no permissions.
 * A native decoder bug can therefore only crash or compromise this throw-away process,
 * not the process that also hosts the Accessibility service.
 *
 * It does exactly one thing: bytes in through a pipe, result out through a pipe / reply.
 * It must not touch [com.tbtechs.focusflow.di.AppModule], the database or any file.
 */
class ImageDecodeService : Service() {
    private var worker: HandlerThread? = null
    private var messenger: Messenger? = null
    private val codec = AndroidBitmapImageCodec()

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("image-decode").also { it.start() }
        worker = thread
        messenger = Messenger(
            Handler(thread.looper, Handler.Callback { message ->
                handle(message)
                true
            }),
        )
    }

    override fun onBind(intent: Intent?): IBinder? = messenger?.binder

    override fun onDestroy() {
        worker?.quitSafely()
        worker = null
        super.onDestroy()
    }

    private fun handle(message: Message) {
        val replyTo = message.replyTo
        val data: Bundle = message.data
        val input = BundleCompat.getParcelable(data, ImageDecodeProtocol.KEY_INPUT, ParcelFileDescriptor::class.java)
        val output = BundleCompat.getParcelable(data, ImageDecodeProtocol.KEY_OUTPUT, ParcelFileDescriptor::class.java)
        val id = data.getLong(ImageDecodeProtocol.KEY_ID)

        var ok = false
        var a1 = 0
        var a2 = 0
        var encoded: ByteArray? = null
        var reason: String? = null
        try {
            val bytes = input?.let { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                    readBounded(stream, ImageDecodeProtocol.MAX_INPUT_BYTES)
                }
            }
            if (bytes != null && bytes.isNotEmpty()) {
                when (message.what) {
                    ImageDecodeProtocol.OP_BOUNDS -> {
                        val found = codec.bounds(bytes)
                        if (found != null) {
                            ok = true
                            a1 = found.width
                            a2 = found.height
                        } else {
                            reason = "the file is not a readable image"
                        }
                    }
                    ImageDecodeProtocol.OP_ORIENTATION -> {
                        a1 = codec.orientation(bytes)
                        ok = true
                    }
                    ImageDecodeProtocol.OP_REENCODE -> {
                        val maxEdge = data.getInt(ImageDecodeProtocol.KEY_MAX_EDGE)
                        val quality = data.getInt(ImageDecodeProtocol.KEY_QUALITY)
                        encoded = if (data.getBoolean(ImageDecodeProtocol.KEY_DETECT)) {
                            codec.reencode(bytes, maxEdge, quality)
                        } else {
                            codec.reencodeWithOrientation(
                                bytes = bytes,
                                maxLongEdge = maxEdge,
                                quality = quality,
                                orientation = data.getInt(ImageDecodeProtocol.KEY_ORIENTATION, 1),
                                pngSource = data.getBoolean(ImageDecodeProtocol.KEY_PNG),
                            )
                        }
                        val result = encoded
                        if (result != null && result.size <= ImageDecodeProtocol.MAX_OUTPUT_BYTES) {
                            ok = true
                            a1 = result.size
                        } else {
                            reason = if (result == null) "the encoder produced nothing"
                            else "the converted image is too large"
                            encoded = null
                        }
                    }
                }
            } else {
                reason = "the image data did not arrive"
            }
        } catch (t: Throwable) {
            ok = false
            encoded = null
            reason = when (t) {
                is OutOfMemoryError -> "not enough memory for this picture"
                else -> t.javaClass.simpleName
            }
        }

        // Always close the output pipe so the other side sees end-of-stream.
        val toWrite = encoded
        try {
            output?.let { pfd ->
                ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { stream ->
                    if (ok && toWrite != null) stream.write(toWrite)
                }
            }
        } catch (_: Throwable) {
            ok = false
        }

        try {
            replyTo?.send(
                Message.obtain(
                    null,
                    if (ok) ImageDecodeProtocol.REPLY_OK else ImageDecodeProtocol.REPLY_FAILED,
                ).apply {
                    this.data = Bundle().apply {
                        putLong(ImageDecodeProtocol.KEY_ID, id)
                        putInt(ImageDecodeProtocol.KEY_A1, a1)
                        putInt(ImageDecodeProtocol.KEY_A2, a2)
                        if (!ok) putString(ImageDecodeProtocol.KEY_REASON, reason ?: "unknown")
                    }
                },
            )
        } catch (_: Throwable) {
            // The client is gone; nothing to report to.
        }
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
}
