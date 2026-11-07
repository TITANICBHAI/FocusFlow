package com.tbtechs.focusflow.analytics.phone

/** CSV encoding for the fixed-size hourly / bucket arrays stored in the phone-behaviour tables. */
object PhoneCodec {
    fun encodeLongs(values: List<Long>): String = values.joinToString(",")

    fun encodeInts(values: List<Int>): String = values.joinToString(",")

    /** Splits on ','; a blank/null text or a bad token yields 0; the result is padded/truncated to exactly [size]. */
    fun decodeLongs(text: String?, size: Int): List<Long> {
        val tokens = tokensOf(text)
        return List(size) { index -> tokens.getOrNull(index)?.trim()?.toLongOrNull() ?: 0L }
    }

    /** Same rules as [decodeLongs], for Int values. */
    fun decodeInts(text: String?, size: Int): List<Int> {
        val tokens = tokensOf(text)
        return List(size) { index -> tokens.getOrNull(index)?.trim()?.toIntOrNull() ?: 0 }
    }

    private fun tokensOf(text: String?): List<String> =
        if (text.isNullOrBlank()) emptyList() else text.split(',')
}
