package com.tbtechs.focusflow.enforcement

data class AllowanceUsageRecord(
    val mode: String?,
    val date: String?,
    val count: Int,
    val windowStartMs: Long,
    val usedMs: Long,
    val confirmedUsedMs: Long,
    val confirmedCount: Int,
    val confirmedAtMs: Long,
    val estimatedExtraMs: Long,
    val estimatedExtraOpens: Int,
)

data class AllowanceReadResult(
    val count: Int,
    val usedMs: Long,
    val windowStartMs: Long,
    val windowExpired: Boolean,
    val remaining: Long,
    val exhausted: Boolean,
)

data class AllowanceLedgerSnapshot(
    val usageJson: String?,
    val usageByPackage: Map<String, AllowanceUsageRecord>,
)

data class AllowanceCountUpdate(val packageName: String, val count: Int)

data class AllowanceTimeUpdate(
    val packageName: String,
    val mode: String,
    val usedMs: Long,
    val windowStartMs: Long = 0L,
)
