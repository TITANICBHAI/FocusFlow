package com.tbtechs.focusflow.ui.active

/**
 * The blocks that act on what is shown inside an app, not on the whole app: the user's custom
 * screen rules plus the Block YouTube Shorts, Block Instagram Reels and install-protection
 * switches. The Active screen lists them and the status icon counts them as one protection layer.
 * Pure logic, no Android types.
 */
internal data class ScreenBlocks(
    val customRuleNames: List<String>,
    val youtubeShorts: Boolean,
    val instagramReels: Boolean,
    val installActions: Boolean,
) {
    val count: Int
        get() = customRuleNames.size + listOf(youtubeShorts, instagramReels, installActions).count { it }

    val isActive: Boolean get() = count > 0

    /** Short badge text for the card header. */
    val status: String get() = if (count == 0) "Not active" else "$count active"

    /** One line per active block, in the order the card shows them. */
    val lines: List<String>
        get() = buildList {
            if (youtubeShorts) add("YouTube Shorts")
            if (instagramReels) add("Instagram Reels")
            if (installActions) add("Install and uninstall protection")
            customRuleNames.forEach { add("Custom rule: $it") }
        }

    companion object {
        /** [ruleNames] are the names of the rules that are switched on; a blank name reads as "Untitled rule". */
        fun of(
            ruleNames: List<String>,
            youtubeShorts: Boolean,
            instagramReels: Boolean,
            installActions: Boolean,
        ): ScreenBlocks = ScreenBlocks(
            customRuleNames = ruleNames.map { it.trim().ifEmpty { "Untitled rule" } },
            youtubeShorts = youtubeShorts,
            instagramReels = instagramReels,
            installActions = installActions,
        )
    }
}
