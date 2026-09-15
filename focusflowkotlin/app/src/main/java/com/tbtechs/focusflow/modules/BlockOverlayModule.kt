package com.tbtechs.focusflow.modules

/**
 * Compatibility constants retained for the relocated accessibility service.
 *
 * Overlay persistence and configuration are owned by BlockOverlayController.
 * This object keeps the existing reference stable while the remaining
 * enforcement call sites are migrated incrementally.
 */
object BlockOverlayModule {
    val DEFAULT_QUOTES = listOf(
        "The present moment is the only time over which we have dominion.",
        "Focus is the art of knowing what to ignore.",
        "Deep work is the superpower of the 21st century.",
        "Your future self is watching. Don't let them down.",
        "One task at a time. One step at a time. One breath at a time.",
        "Discipline is choosing between what you want now and what you want most.",
        "The successful warrior is the average person with laser-like focus.",
        "Where attention goes, energy flows.",
        "Distraction is the enemy of vision.",
        "Every time you resist the urge to check, you grow stronger.",
        "You don't need to check your phone. The world can wait.",
        "Protect your attention like you protect your money.",
        "Clarity comes from action, not thought.",
        "Small disciplines repeated with consistency lead to great achievements.",
        "The cost of distraction is the loss of the life you could have built.",
    )
}