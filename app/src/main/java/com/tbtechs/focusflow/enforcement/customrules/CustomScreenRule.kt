package com.tbtechs.focusflow.enforcement.customrules

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
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

/**
 * Result of reading an import file: rules that can be added, the reasons others were refused,
 * and the rules skipped because the same rule already exists (in your rules or earlier in the file).
 */
data class ImportResult(
    val accepted: List<CustomScreenRule>,
    val rejected: List<String>,
    val duplicates: List<String> = emptyList(),
)

object CustomScreenRules {
    const val MAX_RULES = 30
    const val MAX_MATCHERS = 5
    const val MAX_FIELD_LEN = 200
    // Big enough for a full export of 30 maximum-size rules (about 170,000 characters pretty-printed).
    const val MAX_IMPORT_CHARS = 262_144

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
            return "One element has no id, text or description, so it is too broad: it would match every element of that type on the screen. Add one of those to it, or remove it."
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

    // ----- Duplicates -----

    private fun key(value: String?, trim: Boolean): String? {
        val v = if (trim) value?.trim() else value
        return v?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
    }

    /**
     * What a matcher looks for, written so two matchers that match the same things compare equal.
     * Case never matters to matching. The view id and class name are trimmed when a rule is read,
     * but text and description are not, so a trailing space in those still counts.
     */
    private fun matcherKey(m: NodeMatcher): List<Any?> = listOf(
        key(m.viewIdSuffix, trim = true),
        key(m.className, trim = true),
        key(m.textContains, trim = false),
        key(m.descContains, trim = false),
        m.selectedOnly,
    )

    /**
     * True when two rules would block exactly the same screens: same app, same set of matchers
     * and the same match mode. The name, id and on/off state are ignored, and so is the order
     * or repetition of matchers. With one matcher, "all" and "any" mean the same thing.
     */
    fun isSameRule(a: CustomScreenRule, b: CustomScreenRule): Boolean {
        if (!a.packageName.trim().equals(b.packageName.trim(), ignoreCase = true)) return false
        val am = a.matchers.map { matcherKey(it) }.toSet()
        val bm = b.matchers.map { matcherKey(it) }.toSet()
        if (am != bm) return false
        return am.size <= 1 || a.matchMode == b.matchMode
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
        val mode = MatchMode.values().firstOrNull { it.name.equals(modeName?.trim(), ignoreCase = true) } ?: MatchMode.ALL
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
        // Indented so a person can read it, and an AI assistant or a reviewer can follow it.
        return o.toString(2)
    }

    // ----- Reading pasted text -----

    private val FENCE = Regex("```[A-Za-z]*[ \\t]*\\r?\\n?([\\s\\S]*?)```")

    /** Characters that chat apps and editors add around pasted text and that are not part of the JSON. */
    private fun stripInvisible(text: String): String =
        text.replace("\uFEFF", "")
            .replace(Regex("[\u200B\u200C\u200D\u2060]"), "")
            .replace('\u00A0', ' ')

    private fun straightenQuotes(text: String): String =
        text.replace('\u201C', '"').replace('\u201D', '"').replace('\u2018', '\'').replace('\u2019', '\'')

    /**
     * What is worth trying to read as JSON, best guess first: the text as pasted; the inside of a
     * ``` code block (assistants often wrap JSON in one); and the part from the first bracket to the
     * last one (when there is a sentence before or after). Each is also tried with curly quotes
     * turned straight, because some chat apps replace the quotes.
     */
    internal fun importCandidates(raw: String): List<String> {
        // The text exactly as pasted comes first, so a file that is already valid is never altered.
        val out = LinkedHashSet<String>()
        for (source in listOf(raw.trim(), stripInvisible(raw).trim())) {
            val base = mutableListOf(source)
            FENCE.find(source)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { base += it }
            val first = source.indexOfFirst { it == '{' || it == '[' }
            val last = source.indexOfLast { it == '}' || it == ']' }
            if (first >= 0 && last > first) base += source.substring(first, last + 1)
            base.forEach { out += it; out += straightenQuotes(it) }
        }
        return out.toList()
    }

    /** True for something that looks like rules: a FocusFlow export, one rule, or a list with an object in it. */
    private fun looksLikeRules(root: Any): Boolean = when (root) {
        is JSONObject -> root.has("format") || root.has("matchers") || root.has("packageName")
        is JSONArray -> (0 until root.length()).any { root.optJSONObject(it) != null }
        else -> false
    }

    /**
     * The JSON parser on Android is forgiving: it reads text with curly quotes as a valid object
     * with odd keys. So a candidate that parses is only taken when it looks like rules; if none does,
     * the first one that parsed is returned so the caller can say what is wrong with it.
     */
    private fun readRoot(raw: String): Any? {
        var firstParsed: Any? = null
        for (candidate in importCandidates(raw)) {
            val parsed: Any? = try {
                when {
                    candidate.startsWith("{") -> JSONObject(candidate)
                    candidate.startsWith("[") -> JSONArray(candidate)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
            if (parsed == null) continue
            if (looksLikeRules(parsed)) return parsed
            if (firstParsed == null) firstParsed = parsed
        }
        return firstParsed
    }

    private val MATCHER_KEYS = setOf("viewIdSuffix", "className", "textContains", "descContains", "selectedOnly")

    /**
     * Problems that would quietly change what a rule blocks if they were ignored, so the rule is
     * refused instead. A matcher that is dropped or loses a field under "all" matches MORE screens
     * than the writer meant, which is how a typo such as "txt" would over-block. Returns null when fine.
     */
    private fun strictProblem(o: JSONObject): String? {
        val mode = o.stringOrNull("matchMode")?.trim()
        if (mode != null && MatchMode.values().none { it.name.equals(mode, ignoreCase = true) }) {
            return "Match mode must be ALL or ANY, not \"$mode\"."
        }
        if (!o.has("matchers") || o.isNull("matchers")) return null // validate() asks for a matcher
        val arr = o.optJSONArray("matchers") ?: return "\"matchers\" must be a list."
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: return "Matcher ${i + 1} is not an object."
            val unknown = m.keys().asSequence().firstOrNull { it !in MATCHER_KEYS }
            if (unknown != null) {
                return "Matcher ${i + 1} has an unknown field \"$unknown\". Allowed: viewIdSuffix, className, textContains, descContains, selectedOnly."
            }
            val sel = m.opt("selectedOnly")
            val okSel = sel == null || sel == JSONObject.NULL || sel is Boolean ||
                (sel is String && (sel.equals("true", true) || sel.equals("false", true)))
            if (!okSel) return "Matcher ${i + 1}: selectedOnly must be true or false."
        }
        return null
    }

    /**
     * Reads an export file. Every accepted rule gets a new random id and starts disabled,
     * whatever the file says. Rules that fail [validate] are listed in `rejected`. A rule that is
     * the same as one in [existing], or as an earlier rule in the file, is listed in `duplicates`
     * and not accepted. Invalid rules are reported as invalid first, never as duplicates.
     */
    fun parseImport(
        text: String,
        ownPackage: String,
        existing: List<CustomScreenRule> = emptyList(),
    ): ImportResult {
        if (text.length > MAX_IMPORT_CHARS) {
            return ImportResult(emptyList(), listOf("The text is too long to be a rule file."))
        }
        val root = readRoot(text)
            ?: return ImportResult(emptyList(), listOf("This is not valid JSON."))
        // A FocusFlow export is an object with format, version and rules. A bare list of rules, or
        // one rule on its own, is also accepted: assistants often return those. Every rule is
        // checked the same way whichever form it came in.
        val arr: JSONArray = when (root) {
            is JSONArray -> root
            is JSONObject -> when {
                root.has("format") -> {
                    if (root.optString("format", "") != EXPORT_FORMAT) {
                        return ImportResult(emptyList(), listOf("This is not a FocusFlow screen rules file."))
                    }
                    val version = root.optInt("version", -1)
                    if (version > EXPORT_VERSION) {
                        return ImportResult(
                            emptyList(),
                            listOf("This file is from a newer version of FocusFlow. Update the app to import it."),
                        )
                    }
                    if (version != EXPORT_VERSION) {
                        return ImportResult(emptyList(), listOf("Unsupported rules file version."))
                    }
                    root.optJSONArray("rules")
                        ?: return ImportResult(emptyList(), listOf("The file has no rules."))
                }
                root.has("matchers") || root.has("packageName") -> JSONArray().put(root)
                else -> return ImportResult(emptyList(), listOf("This is not a FocusFlow screen rules file."))
            }
            else -> return ImportResult(emptyList(), listOf("This is not valid JSON."))
        }
        if (arr.length() == 0) {
            return ImportResult(emptyList(), listOf("The file has no rules."))
        }

        val accepted = mutableListOf<CustomScreenRule>()
        val rejected = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            if (accepted.size >= MAX_RULES) {
                rejected += "${arr.length() - i} more rule(s) skipped: the limit is $MAX_RULES rules."
                break
            }
            val obj = arr.optJSONObject(i)
            val parsed = try {
                obj?.let { ruleFromJson(it) }
            } catch (e: Exception) {
                null
            }
            if (obj == null || parsed == null) {
                rejected += "Entry ${i + 1}: not a valid rule."
                continue
            }
            val problem = strictProblem(obj)
            if (problem != null) {
                rejected += "${parsed.name.ifBlank { "Entry ${i + 1}" }}: $problem"
                continue
            }
            val fresh = parsed.copy(
                id = UUID.randomUUID().toString(),
                enabled = false,
            )
            val error = validate(fresh, ownPackage)
            if (error != null) {
                rejected += "${fresh.name.ifBlank { "Entry ${i + 1}" }}: $error"
                continue
            }
            val same = existing.firstOrNull { isSameRule(it, fresh) }
            val earlier = accepted.firstOrNull { isSameRule(it, fresh) }
            when {
                same != null -> duplicates += "${fresh.name}: already in your rules as \"${same.name}\"."
                earlier != null -> duplicates += "${fresh.name}: the same as \"${earlier.name}\" in this file."
                else -> accepted += fresh
            }
        }
        return ImportResult(accepted, rejected, duplicates)
    }
}
