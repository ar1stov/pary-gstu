package com.example.pary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pary.R
import com.example.pary.data.AppSettings
import com.example.pary.data.ReminderMode

private val LeadOptions = listOf(15, 30, 60)

@Composable
fun NotificationSettingsDialog(
    settings: AppSettings,
    isExactAlarmAllowed: Boolean,
    onRemindersChange: (Boolean) -> Unit,
    onLeadChange: (Int) -> Unit,
    onModeChange: (ReminderMode) -> Unit,
    onChangeAlertsChange: (Boolean) -> Unit,
    onSendSample: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SwitchRow(stringResource(R.string.setting_reminders), settings.remindersEnabled, onRemindersChange)
                if (settings.remindersEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LeadOptions.forEach { minutes ->
                            FilterChip(
                                selected = settings.reminderLeadMinutes == minutes,
                                onClick = { onLeadChange(minutes) },
                                label = { Text(if (minutes == 60) "1 ч" else "$minutes мин") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.reminderMode == ReminderMode.FIRST_ONLY,
                            onClick = { onModeChange(ReminderMode.FIRST_ONLY) },
                            label = { Text(stringResource(R.string.reminder_mode_first)) },
                        )
                        FilterChip(
                            selected = settings.reminderMode == ReminderMode.EVERY_LESSON,
                            onClick = { onModeChange(ReminderMode.EVERY_LESSON) },
                            label = { Text(stringResource(R.string.reminder_mode_every)) },
                        )
                    }
                    if (!isExactAlarmAllowed) {
                        Text(
                            text = stringResource(R.string.exact_alarm_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenExactAlarmSettings) {
                            Text(stringResource(R.string.exact_alarm_allow))
                        }
                    }
                }
                SwitchRow(stringResource(R.string.setting_change_alerts), settings.changeAlertsEnabled, onChangeAlertsChange)
                TextButton(onClick = onSendSample) { Text(stringResource(R.string.show_sample)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
