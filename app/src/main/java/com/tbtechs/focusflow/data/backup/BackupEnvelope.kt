package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.Task
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupEnvelope(
    val kind: String,
    val version: Int,
    val exportedAt: String,
    val exportedAtHuman: String,
    val platform: BackupPlatform,
    val settings: JsonObject,
    val tasks: List<Task>,
    val presetSections: List<BackupPresetSection>,
    val summary: BackupSummary,
    val appVersion: String?,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val assets: Map<String, BackupAsset> = emptyMap(),
)

@Serializable
data class BackupAsset(
    val mime: String? = null,
    val data: String? = null,
    val file: String? = null,
    val sha256: String? = null,
)

@Serializable
data class BackupPlatform(
    val os: String,
)

@Serializable
data class BackupPresetSection(
    val id: String,
    val name: String,
    val configured: Boolean,
    val appPackages: List<String>? = null,
    val vpnPackages: List<String>? = null,
    val itemCount: Int? = null,
    val details: JsonObject? = null,
)

@Serializable
data class BackupSummary(
    val taskCount: Int,
    val blockedWordCount: Int,
    val greyoutWindowCount: Int,
    val dailyAllowanceCount: Int,
)

sealed interface BackupParseResult {
    data class Success(
        val envelope: BackupEnvelope,
        val warnings: List<String> = emptyList(),
    ) : BackupParseResult
    data class Failure(val message: String) : BackupParseResult
}
