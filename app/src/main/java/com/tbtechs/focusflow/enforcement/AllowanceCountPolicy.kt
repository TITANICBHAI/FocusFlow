package com.tbtechs.focusflow.enforcement

/**
 * The count-mode rule for "may this app stay open", in one place so it can be tested.
 *
 * Count mode counts an open the moment the app reaches the foreground, before the allowance
 * check runs. So at the check, [countIncludingThisOpen] already includes the open being judged.
 * The project's rule is that the Nth open stays usable until the person leaves, and only the
 * (N+1)th is blocked. That means the open being judged is fine while the count is at most the
 * limit, and a session that was already let in stays let in.
 */
object AllowanceCountPolicy {
    fun isOpenUsable(
        countIncludingThisOpen: Int,
        limit: Int,
        sessionAlreadyAdmitted: Boolean,
    ): Boolean = sessionAlreadyAdmitted || countIncludingThisOpen <= limit
}
