---
name: Navigation icon states
description: The Focus and Defense tab icon visual rules for the Kotlin app.
---

Use the supplied Ionicons-style Focus tab vectors for nav only: a filled active timer with ring/needle cutouts and an open-ring inactive timer with a solid needle, both at 22dp in the bottom bar and intrinsic size in the drawer. Keep the larger Ready-to-Focus artwork on the separate FocusFlowTimerIcon component with its existing gradient and filled hand. For Defense, preserve the existing outline while unselected; use the supplied filled shield-checkmark vector at its normal 22dp size only when the Defense tab is selected.

**Why:** The user supplied different, explicit Focus nav active/inactive timer artwork and asked to keep the Ready-to-Focus illustration unchanged. The user also supplied a replacement shield vector because the previous selected Defense shield rendering looked poor.

**How to apply:** Use FocusTabIcons.Active/Inactive for the Focus nav item in the bottom bar and drawer; do not alter the ReadyToFocusPanel's FocusFlowTimerIcon call. Use the supplied filled Defense shield only for the selected Defense state in the bottom bar and drawer; leave the other tabs and generic Material timer/shield icons unchanged.