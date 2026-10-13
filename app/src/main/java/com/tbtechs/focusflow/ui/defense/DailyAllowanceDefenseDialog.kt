package com.tbtechs.focusflow.ui.defense

import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.ui.settings.DailyAllowanceModal
import com.tbtechs.focusflow.ui.settings.dailyAllowanceEntriesFromJson

@Composable
fun DailyAllowanceDefenseDialog(
    settings: AppSettings,
    onSave: (List<DailyAllowanceEntry>) -> Unit,
    onClose: () -> Unit,
) {
    DailyAllowanceModal(
        visible = true,
        selectedEntries = dailyAllowanceEntriesFromJson(settings.dailyAllowanceConfigJson),
        onSave = onSave,
        onVerifyDefensePin = { false },
        onClose = onClose,
    )
}
