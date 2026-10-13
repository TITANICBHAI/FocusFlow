package com.tbtechs.focusflow.enforcement

/**
 * Pure rules for separating user-selected VPN packages from derived snapshots.
 */
internal object ExplicitVpnPolicy {
    data class MigrationPlan(
        val explicitPackagesToWrite: List<String>?,
        val markMigrated: Boolean,
    )

    fun selectExplicitCandidates(
        explicitKeyExists: Boolean,
        explicitPackages: List<String>?,
        migrationComplete: Boolean,
        derivedSnapshot: List<String>,
        policyGeneration: Long,
    ): List<String> {
        val candidates = when {
            explicitKeyExists -> explicitPackages.orEmpty()
            migrationComplete -> emptyList()
            else -> migrationPlan(
                alreadyMigrated = false,
                explicitKeyExists = false,
                derivedSnapshot = derivedSnapshot,
                policyGeneration = policyGeneration,
            )?.explicitPackagesToWrite.orEmpty()
        }
        return candidates.distinct().sorted()
    }

    fun effectiveTargets(
        explicitCandidates: List<String>,
        focusTargets: List<String>,
    ): List<String> = (explicitCandidates + focusTargets).distinct().sorted()

    fun hasPersistentExplicitTargets(
        explicitCandidates: List<String>,
    ): Boolean = explicitCandidates.isNotEmpty()

    fun migrationPlan(
        alreadyMigrated: Boolean,
        explicitKeyExists: Boolean,
        derivedSnapshot: List<String>,
        policyGeneration: Long,
    ): MigrationPlan? {
        if (alreadyMigrated) return null
        val packagesToWrite = when {
            explicitKeyExists -> null
            policyGeneration == 0L -> derivedSnapshot.distinct().sorted()
            else -> emptyList()
        }
        return MigrationPlan(
            explicitPackagesToWrite = packagesToWrite,
            markMigrated = true,
        )
    }
}
