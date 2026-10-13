package com.tbtechs.focusflow.ui.stats

internal fun shouldRequestDayRatingInputFocus(
    requestNonce: Int,
    isEditing: Boolean,
    hasSavedRating: Boolean,
): Boolean =
    requestNonce > 0 && (isEditing || !hasSavedRating)
