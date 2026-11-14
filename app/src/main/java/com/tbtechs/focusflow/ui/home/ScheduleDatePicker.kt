package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Which day the Home schedule is showing. `null` means "today", so it also follows midnight.
 *
 * Held for the life of the process and put back to today whenever the app leaves the screen
 * (see MainActivity.onStop), so opening FocusFlow always starts on today.
 */
object ScheduleDateState {
    var selected by mutableStateOf<LocalDate?>(null)
        private set

    fun current(): LocalDate = selected ?: LocalDate.now()

    fun isToday(): Boolean = selected == null || selected == LocalDate.now()

    fun select(date: LocalDate) {
        selected = if (date == LocalDate.now()) null else date
    }

    fun resetToToday() {
        selected = null
    }
}

/**
 * Material3's date picker speaks UTC midnight, whatever the phone's time zone is. Converting
 * with the system zone shows (or returns) the neighbouring day in some zones.
 */
internal fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

internal fun Task.isOnDate(date: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Boolean =
    runCatching { Instant.parse(startTime).atZone(zone).toLocalDate() == date }.getOrDefault(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleDatePickerDialog(
    initial: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    showTodayButton: Boolean = true,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        colors = DatePickerDefaults.colors(containerColor = DarkCard),
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) onPick(millis.fromPickerMillis()) else onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
            ) { Text("OK", color = BrandPrimary) }
        },
        dismissButton = {
            Row {
                if (showTodayButton) {
                    TextButton(onClick = { onPick(LocalDate.now()) }, shape = RoundedCornerShape(14.dp)) {
                        Text("Today", color = BrandPrimary)
                    }
                }
                TextButton(onClick = onDismiss, shape = RoundedCornerShape(14.dp)) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            }
        },
    ) {
        DatePicker(
            state = pickerState,
            colors = DatePickerDefaults.colors(
                containerColor = DarkCard,
                titleContentColor = DarkTextPrimary,
                headlineContentColor = DarkTextPrimary,
                weekdayContentColor = DarkTextSecondary,
                subheadContentColor = DarkTextSecondary,
                yearContentColor = DarkTextPrimary,
                currentYearContentColor = BrandPrimary,
                selectedYearContentColor = Color.White,
                selectedYearContainerColor = BrandPrimary,
                dayContentColor = DarkTextPrimary,
                selectedDayContentColor = Color.White,
                selectedDayContainerColor = BrandPrimary,
                todayContentColor = BrandPrimary,
                todayDateBorderColor = BrandPrimary,
            ),
        )
    }
}
