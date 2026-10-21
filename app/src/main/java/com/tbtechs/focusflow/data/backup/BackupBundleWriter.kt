package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.images.SubtaskImageStore
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal sealed interface BundleExportPreparation {
    data class Ready(val plan: BundleExportPlan) : BundleExportPreparation
    data class TooLarge(val projectedBytes: Long) : BundleExportPreparation
    data class Failure(val message: String) : BundleExportPreparation
}

internal data class BundleExportPlan(
    val manifestBytes: ByteArray,
    val assets: List<BundleAssetEntry>,
    val projectedBytes: Long,
)

internal data class BundleAssetEntry(
    val assetId: String,
    val entryName: String,
    val byteCount: Int,
)

internal class BackupBundleWriter(
    private val imageStore: SubtaskImageStore,
) {
    fun prepare(
        manifestJson: String,
        assetIds: Set<String>,
    ): BundleExportPreparation {
        if (assetIds.isEmpty()) {
            return BundleExportPreparation.Failure("A bundle must contain at least one image.")
        }
        if (assetIds.size > BackupJsonLimits.MAX_BUNDLE_ASSETS) {
            return BundleExportPreparation.TooLarge(Long.MAX_VALUE)
        }
        val manifestBytes = manifestJson.toByteArray(StandardCharsets.UTF_8)
        if (manifestBytes.size > BackupJsonLimits.MAX_BUNDLE_MANIFEST_BYTES) {
            return BundleExportPreparation.TooLarge(manifestBytes.size.toLong())
        }
        val assets = mutableListOf<BundleAssetEntry>()
        var totalBytes = manifestBytes.size.toLong() + BackupJsonLimits.BUNDLE_OVERHEAD_RESERVE_BYTES
        assetIds.sorted().forEach { assetId ->
            if (!ASSET_ID_PATTERN.matches(assetId)) {
                return BundleExportPreparation.Failure("An image reference is invalid.")
            }
            val bytes = imageStore.openForExport(assetId)
                ?: return BundleExportPreparation.Failure(
                    "Image $assetId is unavailable or has changed. No backup was written.",
                )
            val byteCount = bytes.size
            bytes.fill(0)
            if (byteCount > BackupJsonLimits.MAX_BUNDLE_ASSET_ENTRY_BYTES) {
                return BundleExportPreparation.TooLarge(
                    totalBytes + byteCount.toLong(),
                )
            }
            assets += BundleAssetEntry(
                assetId = assetId,
                entryName = "assets/$assetId.webp",
                byteCount = byteCount,
            )
            totalBytes += byteCount
            if (totalBytes > BackupJsonLimits.MAX_BUNDLE_BYTES) {
                return BundleExportPreparation.TooLarge(totalBytes)
            }
        }
        return BundleExportPreparation.Ready(
            BundleExportPlan(
                manifestBytes = manifestBytes,
                assets = assets,
                projectedBytes = totalBytes,
            ),
        )
    }

    fun write(plan: BundleExportPlan, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            val manifest = ZipEntry(MANIFEST_ENTRY).apply {
                method = ZipEntry.DEFLATED
                time = DETERMINISTIC_ZIP_TIME
            }
            zip.putNextEntry(manifest)
            zip.write(plan.manifestBytes)
            zip.closeEntry()

            plan.assets.forEach { asset ->
                val bytes = imageStore.openForExport(asset.assetId)
                    ?: throw IllegalStateException(
                        "Image ${asset.assetId} is unavailable or has changed.",
                    )
                try {
                    require(bytes.size == asset.byteCount) {
                        "Image ${asset.assetId} changed after export review."
                    }
                    val crc = CRC32().apply { update(bytes) }
                    val entry = ZipEntry(asset.entryName).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = bytes.size.toLong()
                        this.crc = crc.value
                        time = DETERMINISTIC_ZIP_TIME
                    }
                    zip.putNextEntry(entry)
                    zip.write(bytes)
                    zip.closeEntry()
                } finally {
                    bytes.fill(0)
                }
            }
        }
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val DETERMINISTIC_ZIP_TIME = 315_532_800_000L
        val ASSET_ID_PATTERN = Regex("^[a-f0-9]{64}$")
    }
}
