package com.tbtechs.focusflow.data.backup

import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

object BackupFileManager {
    /**
     * Reads a content:// or file:// URI through the Android document provider.
     * [BackupJsonPreflight] enforces both the byte limit and strict UTF-8 decoding.
     */
    fun readUri(contentResolver: ContentResolver, uri: Uri): String {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open: $uri")
        return readStream(input)
    }

    /**
     * Writes UTF-8 content to a URI returned by ACTION_CREATE_DOCUMENT.
     */
    fun writeToUri(contentResolver: ContentResolver, uri: Uri, content: String) {
        val output = contentResolver.openOutputStream(uri)
            ?: throw IOException("Cannot write to: $uri")
        writeStream(output, content)
    }

    internal fun readStream(input: InputStream): String =
        input.use(BackupJsonPreflight::readUtf8Bounded)

    internal fun writeStream(output: OutputStream, content: String) {
        output.use {
            it.write(content.toByteArray(StandardCharsets.UTF_8))
            it.flush()
        }
    }
}
