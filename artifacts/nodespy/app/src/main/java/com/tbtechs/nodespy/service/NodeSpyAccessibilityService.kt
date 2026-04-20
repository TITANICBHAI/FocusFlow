package com.tbtechs.nodespy.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.tbtechs.nodespy.data.CaptureStore
import com.tbtechs.nodespy.data.NodeCapture
import com.tbtechs.nodespy.data.NodeEntry
import com.tbtechs.nodespy.data.NodeFlags
import com.tbtechs.nodespy.notifications.NotificationHelper
import java.io.File
import java.io.FileOutputStream

class NodeSpyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: NodeSpyAccessibilityService? = null
    }

    private val counter = intArrayOf(0)

    override fun onServiceConnected() {
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 200
        }
        NotificationHelper.createCaptureChannel(applicationContext)
        CaptureStore.setServiceRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == applicationContext.packageName) return
        if (!CaptureStore.loggingEnabled.value) return

        val root = rootInActiveWindow ?: return
        try {
            counter[0] = 0
            val nodes = mutableListOf<NodeEntry>()
            captureNode(root, null, 0, nodes)

            val capture = NodeCapture(
                timestamp = System.currentTimeMillis(),
                pkg = pkg,
                activityClass = event.className?.toString() ?: "",
                screenW = resources.displayMetrics.widthPixels,
                screenH = resources.displayMetrics.heightPixels,
                nodes = nodes
            )
            CaptureStore.addCapture(capture)

            if (canPostNotifications()) {
                NotificationHelper.showCaptureNotification(
                    context = applicationContext,
                    captureId = capture.id,
                    pkg = pkg,
                    nodeCount = nodes.size,
                    activityClass = event.className?.toString() ?: ""
                )
            }

            if (CaptureStore.screenshotEnabled.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestScreenshot()
            }
        } finally {
            root.recycle()
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer, screenshot.colorSpace
                    ) ?: run { screenshot.hardwareBuffer.close(); return }
                    screenshot.hardwareBuffer.close()
                    val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap.recycle()
                    saveScreenshot(bitmap)
                }
                override fun onFailure(errorCode: Int) {}
            }
        )
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        try {
            val dir = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES), "nodespy"
            ).also { it.mkdirs() }
            val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bitmap.recycle()
            CaptureStore.updateLatestScreenshot(file.absolutePath)
        } catch (_: Exception) {
            bitmap.recycle()
        }
    }

    private fun captureNode(
        node: AccessibilityNodeInfo,
        parentId: String?,
        depth: Int,
        list: MutableList<NodeEntry>
    ): String {
        val id = "n${counter[0]++}"
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val entry = NodeEntry(
            id = id,
            parentId = parentId,
            cls = node.className?.toString() ?: "",
            resId = node.viewIdResourceName,
            text = node.text?.toString()?.takeIf { it.isNotBlank() },
            desc = node.contentDescription?.toString()?.takeIf { it.isNotBlank() },
            hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                node.hintText?.toString()?.takeIf { it.isNotBlank() } else null,
            boundsL = bounds.left,
            boundsT = bounds.top,
            boundsR = bounds.right,
            boundsB = bounds.bottom,
            flags = NodeFlags(
                enabled = node.isEnabled,
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                scrollable = node.isScrollable,
                checkable = node.isCheckable,
                checked = node.isChecked,
                focused = node.isFocused,
                selected = node.isSelected,
                visible = node.isVisibleToUser,
                password = node.isPassword,
                editable = node.isEditable
            ),
            depth = depth
        )
        list.add(entry)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val childId = captureNode(child, id, depth + 1, list)
            entry.childIds.add(childId)
            child.recycle()
        }
        return id
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        NotificationHelper.cancelCaptureNotification(applicationContext)
        CaptureStore.setServiceRunning(false)
        super.onDestroy()
    }
}
