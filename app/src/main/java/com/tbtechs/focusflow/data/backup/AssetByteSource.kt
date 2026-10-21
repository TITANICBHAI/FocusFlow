package com.tbtechs.focusflow.data.backup

import java.io.Closeable
import java.util.Base64

/**
 * Supplies one bounded image at a time. Asset IDs are logical identifiers,
 * never paths supplied by an imported file.
 */
internal interface AssetByteSource : Closeable {
    fun open(assetId: String): ByteArray?

    fun contains(assetId: String): Boolean

    override fun close() = Unit
}

internal object EmptyAssetByteSource : AssetByteSource {
    override fun open(assetId: String): ByteArray? = null
    override fun contains(assetId: String): Boolean = false
}

internal class InlineAssetByteSource(
    private val assets: Map<String, BackupAsset>,
) : AssetByteSource {
    override fun open(assetId: String): ByteArray? {
        val data = assets[assetId]?.data ?: return null
        return runCatching { Base64.getDecoder().decode(data) }.getOrNull()
    }

    override fun contains(assetId: String): Boolean = assets[assetId]?.data != null
}

internal class CombinedAssetByteSource(
    private val primary: AssetByteSource,
    private val inline: AssetByteSource,
) : AssetByteSource {
    override fun open(assetId: String): ByteArray? =
        primary.open(assetId) ?: inline.open(assetId)

    override fun contains(assetId: String): Boolean =
        primary.contains(assetId) || inline.contains(assetId)

    override fun close() {
        primary.close()
        inline.close()
    }
}
