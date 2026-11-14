package com.tbtechs.focusflow.enforcement

import org.json.JSONObject

/**
 * The package an allowance entry belongs to.
 *
 * Entries saved from the Settings screens carry the app under `package`, entries saved from
 * Stats and from backups carry both `package` and `packageName`, and the enforcement layer
 * historically only looked for `packageName`. Reading either key keeps every saved limit
 * working, whichever screen created it.
 */
internal fun JSONObject.allowancePackage(): String =
    optString("packageName", "").ifBlank { optString("package", "") }
