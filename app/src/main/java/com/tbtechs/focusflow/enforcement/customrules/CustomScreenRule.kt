package com.tbtechs.focusflow.enforcement.customrules

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One thing to look for on screen. A node matches when EVERY non-blank field matches. */
data class NodeMatcher(
    val viewIdSuffix: String? = null, // compared with endsWith, case-insensitive, e.g. "reel_recycler"
    val className: String? = null, // exact, case-insensitive, e.g. "android.widget.ImageView"
    val textContains: String? = null, // case-insensitive substring of node text
    val descContains: String? = null, // case-insensitive substring of contentDescription
    // True: the element must also be in its "selected" state (the highlighted tab, the chosen
    // page). This is what tells "the Reels tab is the one you are on" from "a Reels button exists".
    val selectedOnly: Boolean = false,
) {
    fun isEmpty(): Boolean =
        viewIdSuffix.isNullOrBlank() && className.isNullOrBlank() &&
            textContains.isNullOrBlank() && descContains.isNullOrBlank()

    /** A matcher of only className is too broad (matches every ImageView). */
    fun isTooBroad(): Boolean =
        viewIdSuffix.isNullOrBlank() && textContains.isNullOrBlank() && descContains.isNullOrBlank()
}

enum class MatchMode { ALL, ANY }

data class CustomScreenRule(
    val id: String, // UUID string
    val name: String,
    val packageName: String,
    val enabled: Boolean = false,
    val matchMode: MatchMode = MatchMode.ALL,
    val matchers: List<NodeMatcher>,
)

/** Result of reading an import file: rules that can be added and the reasons others were refused. */
data class ImportResult(
    val accepted: List<CustomScreenRule>,
    val rejected: List<String>,
)

object CustomScreenRules {
    const val MAX_RULES = 30
    const val MAX_MATCHERS = 5
    const val MAX_FIELD_LEN = 200
    const val MAX_IMPORT_CHARS = 65_536

    const val EXPORT_FORMAT = "focusflow.screenrules"
    const val EXPORT_VERSION = 1

    /** Screens that must never be blockable by a rule. The app's own package is passed to [validate]. */
    val forbiddenPackages: Set<String> = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    /** Returns a short human-readable error, or null when the rule is fine to save. */
    fun validate(rule: CustomScreenRule, ownPackage: String): String? {
        val pkg = rule.packageName.trim()
        if (rule.name.isBlank()) return "Give the rule a name."
        if (pkg.isBlank()) return "Enter the app package."
        if (pkg in forbiddenPackages || pkg == ownPackage) {
            return "This app can't be blocked by a screen rule."
        }
        if (rule.matchers.isEmpty()) return "Add at least one matcher."
        if (rule.matchers.size > MAX_MATCHERS) return "Use at most $MAX_MATCHERS matchers."
        if (rule.matchers.any { it.isEmpty() }) return "A matcher is empty. Fill in a field or remove it."
        if (rule.matchers.any { it.isTooBroad() }) {
            return "A matcher with only a class name is too broad. Add a view id, text or description."
        }
        val longest = buildList {
            add(rule.name)
            add(pkg)
            rule.matchers.forEach { m ->
                m.viewIdSuffix?.let { add(it) }
                m.className?.let { add(it) }
                m.textContains?.let { add(it) }
                m.descContains?.let { add(it) }
            }
        }.maxOf { it.length }
        if (longest > MAX_FIELD_LEN) return "A field is longer than $MAX_FIELD_LEN characters."
        return null
    }

    // ----- JSON -----

    private fun matcherToJson(m: NodeMatcher): JSONObject {
        val o = JSONObject()
        if (!m.viewIdSuffix.isNullOrBlank()) o.put("viewIdSuffix", m.viewIdSuffix)
        if (!m.className.isNullOrBlank()) o.put("className", m.className)
        if (!m.textContains.isNullOrBlank()) o.put("textContains", m.textContains)
        if (!m.descContains.isNullOrBlank()) o.put("descContains", m.descContains)
        if (m.selectedOnly) o.put("selectedOnly", true)
        return o
    }

    private fun ruleToJson(r: CustomScreenRule): JSONObject {
        val o = JSONObject()
        o.put("id", r.id)
        o.put("name", r.name)
        o.put("packageName", r.packageName)
        o.put("enabled", r.enabled)
        o.put("matchMode", r.matchMode.name)
        val arr = JSONArray()
        r.matchers.forEach { arr.put(matcherToJson(it)) }
        o.put("matchers", arr)
        return o
    }

    private fun rulesToJsonArray(rules: List<CustomScreenRule>): JSONArray {
        val arr = JSONArray()
        rules.forEach { arr.put(ruleToJson(it)) }
        return arr
    }

    /** Reads a string field; a missing or JSON-null value gives null (never the text "null"). */
    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key, "").takeIf { it.isNotBlank() }
    }

    private fun matcherFromJson(o: JSONObject): NodeMatcher = NodeMatcher(
        viewIdSuffix = o.stringOrNull("viewIdSuffix")?.trim(),
        className = o.stringOrNull("className")?.trim(),
        textContains = o.stringOrNull("textContains"),
        descContains = o.stringOrNull("descContains"),
        selectedOnly = o.optBoolean("selectedOnly", false),
    )

    /** Parses one rule object. The id may be blank here; callers decide what that means. */
    private fun ruleFromJson(o: JSONObject): CustomScreenRule {
        val modeName = o.stringOrNull("matchMode")
        val mode = MatchMode.values().firstOrNull { it.name == modeName } ?: MatchMode.ALL
        val matchers = mutableListOf<NodeMatcher>()
        val arr = o.optJSONArray("matchers")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                matchers += matcherFromJson(m)
            }
        }
        return CustomScreenRule(
            id = o.stringOrNull("id")?.trim().orEmpty(),
            name = o.stringOrNull("name")?.trim().orEmpty(),
            packageName = o.stringOrNull("packageName")?.trim().orEmpty(),
            enabled = o.optBoolean("enabled", false),
            matchMode = mode,
            matchers = matchers,
        )
    }

    fun toJson(rules: List<CustomScreenRule>): String = rulesToJsonArray(rules).toString()

    /** Never throws: bad input gives an empty list and bad entries are skipped. */
    fun fromJson(json: String): List<CustomScreenRule> {
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        val out = mutableListOf<CustomScreenRule>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.optJSONObject(i) ?: continue
                val rule = ruleFromJson(o)
                if (rule.id.isBlank()) continue
                out += rule
            } catch (e: Exception) {
                // skip this entry only
            }
        }
        return out
    }

    fun exportEnvelope(rules: List<CustomScreenRule>): String {
        val o = JSONObject()
        o.put("format", EXPORT_FORMAT)
        o.put("version", EXPORT_VERSION)
        o.put("rules", rulesToJsonArray(rules))
        return o.toString()
    }

    /**
     * Reads an export file. Every accepted rule gets a new random id and starts disabled,
     * whatever the file says. Rules that fail [validate] are listed in `rejected`.
     */
    fun parseImport(text: String, ownPackage: String): ImportResult {
        if (text.length > MAX_IMPORT_CHARS) {
            return ImportResult(emptyList(), listOf("The text is too long to be a rule file."))
        }
        val root = try {
            JSONObject(text.trim())
        } catch (e: Exception) {
            return ImportResult(emptyList(), listOf("This is not valid JSON."))
        }
        if (root.optString("format", "") != EXPORT_FORMAT) {
            return ImportResult(emptyList(), listOf("This is not a FocusFlow screen rules file."))
        }
        if (root.optInt("version", -1) != EXPORT_VERSION) {
            return ImportResult(emptyList(), listOf("Unsupported rules file version."))
        }
        val arr = root.optJSONArray("rules")
            ?: return ImportResult(emptyList(), listOf("The file has no rules."))

        val accepted = mutableListOf<CustomScreenRule>()
        val rejected = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            if (accepted.size >= MAX_RULES) {
                rejected += "${arr.length() - i} more rule(s) skipped: the limit is $MAX_RULES rules."
                break
            }
            val parsed = try {
                arr.optJSONObject(i)?.let { ruleFromJson(it) }
            } catch (e: Exception) {
                null
            }
            if (parsed == null) {
                rejected += "Entry ${i + 1}: not a valid rule."
                continue
            }
            val fresh = parsed.copy(
                id = UUID.randomUUID().toString(),
                enabled = false,
            )
            val error = validate(fresh, ownPackage)
            if (error != null) {
                rejected += "${fresh.name.ifBlank { "Entry ${i + 1}" }}: $error"
            } else {
                accepted += fresh
            }
        }
        return ImportResult(accepted, rejected)
    }
}
