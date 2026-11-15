package com.tbtechs.focusflow.enforcement

/**
 * The glass maths for the launcher, kept free of Android so it can be tested.
 *
 * Glassiness 0 is a plain black launcher. Above that the wallpaper fades in over the first
 * [FADE_END] of the slider and from there on is shown at full strength: nothing dims it.
 */
object LauncherGlass {
    /** Glass fraction (0..1) at which the wallpaper is fully visible. */
    const val FADE_END = 0.15f

    /** How much of the wallpaper shows, 0..1, for a glass fraction of 0..1. */
    fun wallpaperFade(glassFraction: Float): Float = (glassFraction / FADE_END).coerceIn(0f, 1f)
}
