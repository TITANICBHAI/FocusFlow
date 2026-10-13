package com.tbtechs.focusflow.ui.navigation

/**
 * Route names mirror ARCHITECTURE.md §3.1 exactly.
 *
 * LauncherActivity remains separate from this list because it is an Android
 * HOME activity, not a normal Compose destination.
 */
object Routes {
    const val HOME = "home"
    const val FOCUS = "focus"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val IMPORT_CONFIRM = "import_confirm"
    const val TEXT_SIZE_SETTINGS = "text_size_settings"
    const val SETTINGS_HOW_TO_USE = "settings_how_to_use"
    const val FOCUSFLOW_FILE_GUIDE = "focusflow_file_guide"
    const val DEFENSE = "defense"
    const val ACTIVE = "active"
    const val ALWAYS_ON = "always_on"
    const val BLOCK_DEFENSE = "block_defense"
    const val CHANGELOG = "changelog"
    const val HOME_LAUNCHER_SETUP = "home_launcher_setup"
    const val HOW_TO_USE = "how_to_use"
    const val KEYWORD_BLOCKER = "keyword_blocker"
    const val ONBOARDING = "onboarding"
    const val PASSWORD_PROTECTION = "password_protection"
    const val PERMISSIONS = "permissions"
    const val PRIVACY_POLICY = "privacy_policy"
    const val REPORTS = "reports"
    /** Prompt 3 compatibility slug retained alongside the architecture route. */
    const val REPORT = "report"
    const val TERMS_OF_SERVICE = "terms_of_service"
    const val USER_PROFILE = "user_profile"
    const val VPN_BLOCK_LIST = "vpn_block_list"
    const val NOT_FOUND = "not_found"

    val tabRoutes: Set<String> = setOf(HOME, FOCUS, STATS, SETTINGS, DEFENSE)

    val architectureRoutes: Set<String> = setOf(
        HOME,
        FOCUS,
        STATS,
        SETTINGS,
        IMPORT_CONFIRM,
        TEXT_SIZE_SETTINGS,
        SETTINGS_HOW_TO_USE,
        FOCUSFLOW_FILE_GUIDE,
        DEFENSE,
        ACTIVE,
        ALWAYS_ON,
        BLOCK_DEFENSE,
        CHANGELOG,
        HOME_LAUNCHER_SETUP,
        HOW_TO_USE,
        KEYWORD_BLOCKER,
        ONBOARDING,
        PASSWORD_PROTECTION,
        PERMISSIONS,
        PRIVACY_POLICY,
        REPORTS,
        REPORT,
        TERMS_OF_SERVICE,
        USER_PROFILE,
        VPN_BLOCK_LIST,
    )

    /**
     * Destinations that may be opened by an external VIEW intent. Keep
     * stateful startup and active-session routes internal.
     */
    val externalLinkableRoutes: Set<String> = setOf(
        HOME,
        FOCUS,
        STATS,
        SETTINGS,
        DEFENSE,
        ALWAYS_ON,
        BLOCK_DEFENSE,
        CHANGELOG,
        HOME_LAUNCHER_SETUP,
        HOW_TO_USE,
        KEYWORD_BLOCKER,
        PASSWORD_PROTECTION,
        PERMISSIONS,
        PRIVACY_POLICY,
        REPORTS,
        REPORT,
        TERMS_OF_SERVICE,
        USER_PROFILE,
        VPN_BLOCK_LIST,
    )

    /**
     * Converts both deep-link slugs (`privacy-policy`) and internal route
     * strings (`privacy_policy`) to a safe externally linkable destination.
     */
    fun fromPath(path: String?): String {
        val normalized = path
            ?.trim()
            ?.removePrefix("/")
            ?.substringBefore("?")
            ?.substringBefore("/")
            ?.lowercase()
            .orEmpty()
        val route = when (normalized) {
            "privacy-policy" -> PRIVACY_POLICY
            "terms-of-service" -> TERMS_OF_SERVICE
            "how-to-use" -> HOW_TO_USE
            "block-defense" -> BLOCK_DEFENSE
            "home-launcher" -> HOME_LAUNCHER_SETUP
            "always-on" -> ALWAYS_ON
            "keyword-blocker" -> KEYWORD_BLOCKER
            "password-protection" -> PASSWORD_PROTECTION
            "user-profile" -> USER_PROFILE
            "vpn-block-list" -> VPN_BLOCK_LIST
            else -> normalized
        }
        if (normalized.isBlank()) return HOME
        return if (route in externalLinkableRoutes) route else NOT_FOUND
    }
}