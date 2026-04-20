package com.tbtechs.nodespy.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CaptureStore {

    private const val MAX_CAPTURES = 30
    private const val DEDUP_WINDOW_MS = 800L

    private val _captures = MutableStateFlow<List<NodeCapture>>(emptyList())
    val captures: StateFlow<List<NodeCapture>> = _captures.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _loggingEnabled = MutableStateFlow(true)
    val loggingEnabled: StateFlow<Boolean> = _loggingEnabled.asStateFlow()

    private val _screenshotEnabled = MutableStateFlow(false)
    val screenshotEnabled: StateFlow<Boolean> = _screenshotEnabled.asStateFlow()

    private val _bubblePinnedIds = MutableStateFlow<Set<String>>(emptySet())
    val bubblePinnedIds: StateFlow<Set<String>> = _bubblePinnedIds.asStateFlow()

    private val _bubbleActiveCaptureId = MutableStateFlow<String?>(null)
    val bubbleActiveCaptureId: StateFlow<String?> = _bubbleActiveCaptureId.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    fun setLoggingEnabled(enabled: Boolean) {
        _loggingEnabled.value = enabled
    }

    fun setScreenshotEnabled(enabled: Boolean) {
        _screenshotEnabled.value = enabled
    }

    fun addCapture(capture: NodeCapture) {
        if (!_loggingEnabled.value) return
        val current = _captures.value
        val last = current.firstOrNull()
        if (last != null &&
            last.pkg == capture.pkg &&
            last.activityClass == capture.activityClass &&
            last.nodes.size == capture.nodes.size &&
            capture.timestamp - last.timestamp < DEDUP_WINDOW_MS) {
            return
        }
        val updated = (listOf(capture) + current).take(MAX_CAPTURES)
        _captures.value = updated
        if (_bubbleActiveCaptureId.value == null) {
            _bubbleActiveCaptureId.value = capture.id
        }
    }

    fun updateLatestScreenshot(path: String) {
        val current = _captures.value
        if (current.isEmpty()) return
        val updated = current.toMutableList()
        updated[0] = updated[0].copy(screenshotPath = path)
        _captures.value = updated
    }

    fun setBubblePinnedIds(ids: Set<String>) {
        _bubblePinnedIds.value = ids
    }

    fun addBubblePinnedId(id: String) {
        _bubblePinnedIds.value = _bubblePinnedIds.value + id
    }

    fun removeBubblePinnedId(id: String) {
        _bubblePinnedIds.value = _bubblePinnedIds.value - id
    }

    fun clearBubblePins() {
        _bubblePinnedIds.value = emptySet()
        _bubbleActiveCaptureId.value = _captures.value.firstOrNull()?.id
    }

    fun setBubbleActiveCaptureId(id: String?) {
        _bubbleActiveCaptureId.value = id
        _bubblePinnedIds.value = emptySet()
    }

    fun remove(id: String) {
        _captures.value = _captures.value.filter { it.id != id }
    }

    fun clearAll() {
        _captures.value = emptyList()
        _bubblePinnedIds.value = emptySet()
        _bubbleActiveCaptureId.value = null
    }

    fun findById(id: String): NodeCapture? =
        _captures.value.firstOrNull { it.id == id }

    fun latest(): NodeCapture? = _captures.value.firstOrNull()
}
