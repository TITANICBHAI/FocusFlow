package com.tbtechs.focusflow.data.images

/**
 * Wire format between [IsolatedImageCodec] (main process) and [ImageDecodeService]
 * (isolated process). Requests and replies are plain [android.os.Message]s; image bytes
 * travel through pipes (file descriptors) because a Binder transaction is limited to ~1 MB.
 */
internal object ImageDecodeProtocol {
    const val OP_BOUNDS = 1
    const val OP_ORIENTATION = 2
    const val OP_REENCODE = 3

    const val REPLY_OK = 100
    const val REPLY_FAILED = 101

    const val KEY_ID = "id"
    const val KEY_INPUT = "in"
    const val KEY_OUTPUT = "out"
    const val KEY_MAX_EDGE = "maxEdge"
    const val KEY_QUALITY = "quality"
    const val KEY_ORIENTATION = "orientation"
    const val KEY_PNG = "png"
    /** True: the service reads orientation and PNG-ness itself ([ImageCodec.reencode]). */
    const val KEY_DETECT = "detect"
    const val KEY_A1 = "a1"
    const val KEY_A2 = "a2"
    /** Short human-readable reason sent back when a request fails (shown to the user). */
    const val KEY_REASON = "reason"

    /** The isolated process refuses larger inputs; the stores enforce tighter limits themselves. */
    const val MAX_INPUT_BYTES = 24 * 1024 * 1024
    const val MAX_OUTPUT_BYTES = 16 * 1024 * 1024

    /** Hard limit for one request, including starting the isolated process. */
    const val REQUEST_TIMEOUT_MILLIS = 20_000L
    /** The isolated process is released after this long without a request. */
    const val IDLE_DISCONNECT_MILLIS = 20_000L
}
