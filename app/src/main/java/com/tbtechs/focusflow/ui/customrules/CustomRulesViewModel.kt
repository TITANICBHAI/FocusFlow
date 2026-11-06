package com.tbtechs.focusflow.ui.customrules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.customrules.CaptureHolder
import com.tbtechs.focusflow.enforcement.customrules.CaptureResult
import com.tbtechs.focusflow.enforcement.customrules.CustomRuleStore
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRule
import com.tbtechs.focusflow.enforcement.customrules.CustomScreenRules
import com.tbtechs.focusflow.enforcement.customrules.ImportResult
import com.tbtechs.focusflow.enforcement.customrules.NodeSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * Owns the custom screen rules for every rules screen and for the Defense tab.
 *
 * Created once in the navigation graph. All writes go through [CustomRuleStore], which
 * the accessibility service reads on every event, so a change takes effect immediately.
 */
class CustomRulesViewModel(private val appContext: Context) : ViewModel() {
    private val prefs = appContext.getSharedPreferences(
        AppBlockerAccessibilityService.PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    private val _rules = MutableStateFlow(CustomRuleStore.read(prefs))
    val rules: StateFlow<List<CustomScreenRule>> = _rules.asStateFlow()

    /** The rule being edited. Shared by the edit and pick-node screens. */
    private val _draft = MutableStateFlow<CustomScreenRule?>(null)
    val draft: StateFlow<CustomScreenRule?> = _draft.asStateFlow()

    /** The latest screen capture, including failed ones (blank package, no nodes). */
    val capture: StateFlow<CaptureResult?> = CaptureHolder.snapshot

    /** Captured nodes of the latest successful capture. */
    val snapshot: StateFlow<List<NodeSnapshot>> = CaptureHolder.snapshot
        .map { it?.nodes.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun refresh() {
        _rules.value = CustomRuleStore.read(prefs)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        CustomRuleStore.setEnabled(prefs, id, enabled)
        refresh()
    }

    /** Returns a validation error, or null when the rule was saved. */
    fun save(rule: CustomScreenRule): String? {
        val clean = rule.copy(
            name = rule.name.trim(),
            packageName = rule.packageName.trim(),
        )
        val error = CustomScreenRules.validate(clean, appContext.packageName)
        if (error != null) return error
        val current = CustomRuleStore.read(prefs)
        val exists = current.any { it.id == clean.id }
        if (!exists && current.size >= CustomScreenRules.MAX_RULES) {
            return "You can have at most ${CustomScreenRules.MAX_RULES} rules."
        }
        val next = if (exists) {
            // Enabling is only done from the toggles; an edit never changes it.
            current.map { if (it.id == clean.id) clean.copy(enabled = it.enabled) else it }
        } else {
            current + clean
        }
        CustomRuleStore.write(prefs, next)
        refresh()
        _draft.value = null
        return null
    }

    fun delete(id: String) {
        CustomRuleStore.write(prefs, CustomRuleStore.read(prefs).filterNot { it.id == id })
        refresh()
        if (_draft.value?.id == id) _draft.value = null
    }

    /** Opens an existing rule for editing, or starts an empty new one when [ruleId] is null. */
    fun startDraft(ruleId: String?) {
        val existing = ruleId?.let { id -> _rules.value.firstOrNull { it.id == id } }
        _draft.value = existing ?: CustomScreenRule(
            id = UUID.randomUUID().toString(),
            name = "",
            packageName = "",
            enabled = false,
            matchers = emptyList(),
        )
    }

    fun updateDraft(transform: (CustomScreenRule) -> CustomScreenRule) {
        val current = _draft.value ?: return
        _draft.value = transform(current)
    }

    fun clearDraft() {
        _draft.value = null
    }

    /** Forget the last capture so the capture screen starts clean. */
    fun clearCapture() {
        CaptureHolder.snapshot.value = null
    }

    fun exportJson(): String = CustomScreenRules.exportEnvelope(_rules.value)

    /** Reads import text without saving anything. */
    fun previewImport(text: String): ImportResult =
        CustomScreenRules.parseImport(text, appContext.packageName)

    /**
     * Adds the valid rules from [text] after the existing ones (existing rules are kept).
     * Stops at [CustomScreenRules.MAX_RULES]. Imported rules are disabled. The result lists
     * the rules actually added and everything that was refused, including those over the limit.
     */
    fun import(text: String): ImportResult {
        val parsed = previewImport(text)
        val current = CustomRuleStore.read(prefs)
        val room = (CustomScreenRules.MAX_RULES - current.size).coerceAtLeast(0)
        val added = parsed.accepted.take(room)
        val rejected = parsed.rejected.toMutableList()
        val overflow = parsed.accepted.size - added.size
        if (overflow > 0) {
            rejected += "$overflow rule(s) not added: the limit is ${CustomScreenRules.MAX_RULES} rules."
        }
        if (added.isNotEmpty()) {
            CustomRuleStore.write(prefs, current + added)
            refresh()
        }
        return ImportResult(added, rejected)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (!modelClass.isAssignableFrom(CustomRulesViewModel::class.java)) {
                    throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
                }
                val ctx = runCatching { AppModule.applicationContext }.getOrNull()
                    ?: throw IllegalStateException("AppModule not initialized to create CustomRulesViewModel")
                return CustomRulesViewModel(ctx.applicationContext) as T
            }
        }
    }
}
