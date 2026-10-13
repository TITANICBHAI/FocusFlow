package com.tbtechs.focusflow.data.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Central JSON and data-field limits shared by legacy settings migration and
 * task-tag validation.
 *
 * Keep changes to parser bounds here so the streaming preflight and record
 * validators use the same contract.
 */
object BackupJsonLimits {
    const val MAX_FILE_BYTES = 8 * 1024 * 1024
    const val MAX_DEPTH = 12
    const val MAX_TASKS = 20_000
    const val MAX_STRING_CHARS = 20_000
    const val MAX_TITLE_CHARS = 1_000
    const val MAX_TAG_CHARS = 100
    const val MAX_PACKAGE_CHARS = 255
    const val MAX_ID_CHARS = 128
    const val MAX_PACKAGES_PER_LIST = 5_000
    const val MAX_BLOCKED_WORDS = 5_000
    const val MAX_BLOCKED_WORD_CHARS = 200
    const val MAX_ALLOWANCE_ENTRIES = 1_000
    const val MAX_RECURRING_SCHEDULES = 500
    const val MAX_GREYOUT_WINDOWS = 5_000
    const val MAX_PRESETS = 500
    const val MAX_OVERLAY_QUOTES = 500
    const val MAX_TAGS_PER_TASK = 100
    const val MAX_REMINDERS_PER_TASK = 100
    const val MAX_JSON_NODES = 2_000_000
}

class BackupJsonFormatException(message: String) : IllegalArgumentException(message)

/**
 * Checks JSON syntax and resource bounds without constructing a JSON tree.
 *
 * It also decodes object keys while scanning, allowing duplicate detection
 * across escaped spellings such as "a" and "\\u0061".
 */
object BackupJsonPreflight {
    /**
     * Reads no more than the contract byte limit and rejects malformed UTF-8
     * instead of allowing a CharsetReader to replace bad bytes with U+FFFD.
     * The caller owns and closes [input].
     */
    fun readUtf8Bounded(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var byteCount = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val next = input.read()
                if (next < 0) break
                if (byteCount == BackupJsonLimits.MAX_FILE_BYTES) {
                    throw BackupJsonFormatException("Backup exceeds the 8 MiB file limit.")
                }
                output.write(next)
                byteCount++
                continue
            }
            if (count > BackupJsonLimits.MAX_FILE_BYTES - byteCount) {
                throw BackupJsonFormatException("Backup exceeds the 8 MiB file limit.")
            }
            output.write(buffer, 0, count)
            byteCount += count
        }

        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (_: Exception) {
            throw BackupJsonFormatException("Backup is not valid UTF-8.")
        }
    }

    fun validateAndStripBom(text: String): String {
        val normalized = text.removePrefix("\uFEFF")
        val byteCount = utf8ByteCount(normalized)
        if (byteCount > BackupJsonLimits.MAX_FILE_BYTES) {
            throw BackupJsonFormatException("Backup exceeds the 8 MiB file limit.")
        }
        Scanner(normalized).scanDocument()
        return normalized
    }

    private fun utf8ByteCount(value: String): Int {
        var bytes = 0L
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.code <= 0x7f -> bytes += 1
                char.code <= 0x7ff -> bytes += 2
                char.isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) {
                        throw BackupJsonFormatException("Backup contains malformed Unicode text.")
                    }
                    bytes += 4
                    index++
                }
                char.isLowSurrogate() ->
                    throw BackupJsonFormatException("Backup contains malformed Unicode text.")
                else -> bytes += 3
            }
            if (bytes > BackupJsonLimits.MAX_FILE_BYTES) {
                throw BackupJsonFormatException("Backup exceeds the 8 MiB file limit.")
            }
            index++
        }
        return bytes.toInt()
    }

    private class Scanner(private val text: String) {
        private var cursor = 0
        private var nodes = 0

        fun scanDocument() {
            skipWhitespace()
            if (cursor >= text.length) fail("Backup is empty.")
            parseValue(depth = 0)
            skipWhitespace()
            if (cursor != text.length) fail("Backup contains trailing data.")
        }

        private fun parseValue(depth: Int) {
            if (depth > BackupJsonLimits.MAX_DEPTH) {
                fail("Backup exceeds the maximum JSON nesting depth.")
            }
            nodes++
            if (nodes > BackupJsonLimits.MAX_JSON_NODES) {
                fail("Backup exceeds the maximum JSON node count.")
            }
            skipWhitespace()
            when (peek()) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't' -> consumeLiteral("true")
                'f' -> consumeLiteral("false")
                'n' -> consumeLiteral("null")
                '-', in '0'..'9' -> parseNumber()
                else -> fail("Backup contains invalid JSON syntax.")
            }
        }

        private fun parseObject(depth: Int) {
            expect('{')
            skipWhitespace()
            if (takeIf('}')) return

            val keys = HashSet<String>()
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("JSON object keys must be strings.")
                val key = parseString()
                if (!keys.add(key)) {
                    fail("Backup contains a duplicate JSON object key.")
                }
                skipWhitespace()
                expect(':')
                parseValue(depth + 1)
                skipWhitespace()
                when {
                    takeIf('}') -> return
                    takeIf(',') -> Unit
                    else -> fail("Backup contains invalid JSON object syntax.")
                }
            }
        }

        private fun parseArray(depth: Int) {
            expect('[')
            skipWhitespace()
            if (takeIf(']')) return
            while (true) {
                parseValue(depth + 1)
                skipWhitespace()
                when {
                    takeIf(']') -> return
                    takeIf(',') -> Unit
                    else -> fail("Backup contains invalid JSON array syntax.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (cursor < text.length) {
                val char = text[cursor++]
                when {
                    char == '"' -> {
                        if (result.length > BackupJsonLimits.MAX_STRING_CHARS) {
                            fail("Backup contains a string exceeding the character limit.")
                        }
                        return result.toString()
                    }
                    char == '\\' -> appendEscape(result)
                    char.code < 0x20 -> fail("Backup contains an unescaped control character.")
                    char.isHighSurrogate() -> {
                        if (cursor >= text.length || !text[cursor].isLowSurrogate()) {
                            fail("Backup contains a lone Unicode surrogate.")
                        }
                        result.append(char).append(text[cursor++])
                    }
                    char.isLowSurrogate() -> fail("Backup contains a lone Unicode surrogate.")
                    else -> result.append(char)
                }
                if (result.length > BackupJsonLimits.MAX_STRING_CHARS) {
                    fail("Backup contains a string exceeding the character limit.")
                }
            }
            fail("Backup contains an unterminated string.")
        }

        private fun appendEscape(target: StringBuilder) {
            if (cursor >= text.length) fail("Backup contains an incomplete string escape.")
            when (val escaped = text[cursor++]) {
                '"', '\\', '/' -> target.append(escaped)
                'b' -> target.append('\b')
                'f' -> target.append('\u000C')
                'n' -> target.append('\n')
                'r' -> target.append('\r')
                't' -> target.append('\t')
                'u' -> {
                    val first = readHexCodeUnit()
                    when {
                        first.isHighSurrogate() -> {
                            if (cursor + 1 >= text.length ||
                                text[cursor] != '\\' ||
                                text[cursor + 1] != 'u'
                            ) {
                                fail("Backup contains a lone Unicode surrogate escape.")
                            }
                            cursor += 2
                            val second = readHexCodeUnit()
                            if (!second.isLowSurrogate()) {
                                fail("Backup contains an invalid Unicode surrogate pair.")
                            }
                            target.append(first).append(second)
                        }
                        first.isLowSurrogate() ->
                            fail("Backup contains a lone Unicode surrogate escape.")
                        else -> target.append(first)
                    }
                }
                else -> fail("Backup contains an invalid string escape.")
            }
        }

        private fun readHexCodeUnit(): Char {
            if (cursor + 4 > text.length) fail("Backup contains an incomplete Unicode escape.")
            var value = 0
            repeat(4) {
                val digit = text[cursor++].digitToIntOrNull(16)
                    ?: fail("Backup contains an invalid Unicode escape.")
                value = (value shl 4) or digit
            }
            return value.toChar()
        }

        private fun parseNumber() {
            takeIf('-')
            when {
                takeIf('0') -> {
                    if (peek() in '0'..'9') fail("Backup contains an invalid JSON number.")
                }
                peek() in '1'..'9' -> {
                    cursor++
                    while (peek() in '0'..'9') cursor++
                }
                else -> fail("Backup contains an invalid JSON number.")
            }
            if (takeIf('.')) {
                if (peek() !in '0'..'9') fail("Backup contains an invalid JSON number.")
                while (peek() in '0'..'9') cursor++
            }
            if (peek() == 'e' || peek() == 'E') {
                cursor++
                if (peek() == '+' || peek() == '-') cursor++
                if (peek() !in '0'..'9') fail("Backup contains an invalid JSON number.")
                while (peek() in '0'..'9') cursor++
            }
        }

        private fun consumeLiteral(expected: String) {
            if (!text.regionMatches(cursor, expected, 0, expected.length)) {
                fail("Backup contains invalid JSON syntax.")
            }
            cursor += expected.length
        }

        private fun skipWhitespace() {
            while (cursor < text.length) {
                val char = text[cursor]
                if (char != ' ' && char != '\t' && char != '\r' && char != '\n') break
                cursor++
            }
        }

        private fun expect(char: Char) {
            if (!takeIf(char)) fail("Backup contains invalid JSON syntax.")
        }

        private fun takeIf(char: Char): Boolean {
            if (cursor < text.length && text[cursor] == char) {
                cursor++
                return true
            }
            return false
        }

        private fun peek(): Char = text.getOrElse(cursor) { '\u0000' }

        private fun fail(message: String): Nothing = throw BackupJsonFormatException(message)
    }
}
