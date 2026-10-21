package com.tbtechs.focusflow.ui.navigation

import com.tbtechs.focusflow.data.model.AppSettings

/**
 * Resolves the text-scale owner for destinations outside the root tab content.
 *
 * A null sourceTab means the destination was entered directly (for example
 * from an external intent or a startup navigation), so it uses General.
 */
internal object RouteTextScaleContext {
    const val SOURCE_TAB_ARGUMENT = "sourceTab"
    const val HOME_QUICK_ADD_SCREEN = "schedule:quick_add"
    const val HOME_TASK_DETAILS_SCREEN = "schedule:task_details"
    const val HOME_SUBTASK_DETAILS_SCREEN = "schedule:subtask_details"
    const val HOME_EDIT_TASK_SCREEN = "schedule:edit_task"
    const val HOME_TASK_LIST_SCREEN = "schedule:task_list"
    const val FOCUS_STANDALONE_PANEL_SCREEN = "focus:standalone_panel"
    const val FOCUS_STANDALONE_SETUP_SCREEN = "focus:standalone_setup"
    const val FOCUS_EXTENSION_SCREEN = "focus:extension"

    private val sharedRoutes = setOf(
        Routes.ACTIVE,
        Routes.HOW_TO_USE,
        Routes.PERMISSIONS,
    )

    private val ownerTabByRoute = mapOf(
        Routes.TEXT_SIZE_SETTINGS to Routes.SETTINGS,
        Routes.SETTINGS_HOW_TO_USE to Routes.SETTINGS,
        Routes.FOCUSFLOW_FILE_GUIDE to Routes.SETTINGS,
        Routes.USER_PROFILE to Routes.SETTINGS,
        Routes.CHANGELOG to Routes.SETTINGS,
        Routes.PRIVACY_POLICY to Routes.SETTINGS,
        Routes.TERMS_OF_SERVICE to Routes.SETTINGS,
        Routes.REPORTS to Routes.STATS,
        Routes.REPORT to Routes.STATS,
        Routes.ALWAYS_ON to Routes.DEFENSE,
        Routes.BLOCK_DEFENSE to Routes.DEFENSE,
        Routes.HOME_LAUNCHER_SETUP to Routes.DEFENSE,
        Routes.KEYWORD_BLOCKER to Routes.DEFENSE,
        Routes.PASSWORD_PROTECTION to Routes.DEFENSE,
        Routes.VPN_BLOCK_LIST to Routes.DEFENSE,
    )

    private val sourceContextRoutes = sharedRoutes + ownerTabByRoute.keys

    fun routeBase(route: String?): String? = route?.substringBefore('?')

    fun screenScaleKey(tabRoute: String, screenId: String): String =
        "$tabRoute::$screenId"

    fun screenScaleFor(
        tabRoute: String,
        screenId: String,
        settings: AppSettings,
        inheritedScale: Float,
    ): Float = settings.screenTextScales[screenScaleKey(tabRoute, screenId)] ?: inheritedScale

    fun routePattern(routePattern: String): String {
        val separator = if ('?' in routePattern) '&' else '?'
        return "$routePattern$separator$SOURCE_TAB_ARGUMENT={$SOURCE_TAB_ARGUMENT}"
    }

    fun supportsSourceContext(route: String): Boolean =
        routeBase(route)?.let { it in sourceContextRoutes } == true

    fun routeWithSourceTab(route: String, sourceTab: String?): String {
        val validSourceTab = rootTabRoute(sourceTab) ?: return route
        if (!supportsSourceContext(route)) return route
        val alreadyHasSourceTab = route.substringAfter('?', "")
            .split('&')
            .any { it.substringBefore('=').trim() == SOURCE_TAB_ARGUMENT }
        if (alreadyHasSourceTab) {
            return route
        }
        val separator = if ('?' in route) '&' else '?'
        return "$route$separator$SOURCE_TAB_ARGUMENT=$validSourceTab"
    }

    /**
     * Returns the scale context to attach when navigating from currentRoute
     * into destinationRoute. Shared routes retain the current effective tab;
     * tab-owned destinations switch to their owner when an app-origin context
     * exists. A direct entry remains context-free and therefore General-sized.
     */
    fun sourceTabForDestination(
        currentRoute: String?,
        currentSourceTab: String?,
        destinationRoute: String,
    ): String? {
        val currentTab = effectiveSourceTab(currentRoute, currentSourceTab) ?: return null
        val destinationBase = routeBase(destinationRoute) ?: return null
        if (destinationBase in Routes.tabRoutes) return null

        return ownerTabByRoute[destinationBase] ?: currentTab
    }

    fun scaleFor(
        route: String,
        sourceTab: String?,
        settings: AppSettings,
    ): Float {
        val base = routeBase(route) ?: return settings.generalTextScale
        val source = rootTabRoute(sourceTab)
        val scaleOwner = if (base in Routes.tabRoutes) {
            null
        } else {
            source?.let { ownerTabByRoute[base] ?: it }
        }
        val screenOverrideOwner = source?.takeIf { owner ->
            base !in Routes.tabRoutes &&
                (ownerTabByRoute[base] == null || ownerTabByRoute[base] == owner)
        }
        val screenOverride = screenOverrideOwner?.let { owner ->
            settings.screenTextScales[screenScaleKey(owner, base)]
        }
        if (screenOverride != null) return screenOverride

        return when (base) {
            Routes.HOME -> settings.homeTextScale ?: settings.generalTextScale
            Routes.FOCUS -> settings.focusTextScale ?: settings.generalTextScale
            Routes.STATS -> settings.statsTextScale ?: settings.generalTextScale
            Routes.SETTINGS -> settings.settingsTextScale ?: settings.generalTextScale
            Routes.DEFENSE -> settings.defenseTextScale ?: settings.generalTextScale
            in sharedRoutes -> source?.let { scaleForTab(it, settings) }
                ?: settings.generalTextScale
            else -> {
                val ownerTab = ownerTabByRoute[base]
                if (ownerTab != null && source != null) {
                    scaleForTab(ownerTab, settings)
                } else {
                    settings.generalTextScale
                }
            }
        }
    }

    private fun effectiveSourceTab(route: String?, sourceTab: String?): String? {
        val base = routeBase(route) ?: return null
        if (base in Routes.tabRoutes) return base

        val incomingTab = rootTabRoute(sourceTab) ?: return null
        return ownerTabByRoute[base] ?: incomingTab
    }

    private fun rootTabRoute(route: String?): String? =
        route?.takeIf { it in Routes.tabRoutes }

    private fun scaleForTab(tabRoute: String, settings: AppSettings): Float =
        when (tabRoute) {
            Routes.HOME -> settings.homeTextScale ?: settings.generalTextScale
            Routes.FOCUS -> settings.focusTextScale ?: settings.generalTextScale
            Routes.STATS -> settings.statsTextScale ?: settings.generalTextScale
            Routes.SETTINGS -> settings.settingsTextScale ?: settings.generalTextScale
            Routes.DEFENSE -> settings.defenseTextScale ?: settings.generalTextScale
            else -> settings.generalTextScale
        }
}