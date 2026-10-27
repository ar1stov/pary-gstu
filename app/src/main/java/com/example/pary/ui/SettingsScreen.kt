package com.example.pary.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.pary.data.ThemeMode
import com.example.pary.ui.theme.pary

private val LeadOptions = listOf(15, 30, 60)
private val MorningTimes = listOf(6 * 60 + 30, 7 * 60, 7 * 60 + 30, 8 * 60)
private val EveningTimes = listOf(19 * 60, 20 * 60, 21 * 60, 22 * 60)

/** Что система разрешает приложению: от этого зависит, дойдут ли напоминания и проверка изменений. */
data class SystemStatus(val notifications: Boolean, val exactAlarms: Boolean, val batteryUnrestricted: Boolean)

/** «7:00» из минут от полуночи. */
fun clockLabel(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    status: SystemStatus,
    versionLabel: String,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onRemindersChange: (Boolean) -> Unit,
    onLeadChange: (Int) -> Unit,
    onModeChange: (ReminderMode) -> Unit,
    onMorningChange: (Boolean) -> Unit,
    onMorningTimeChange: (Int) -> Unit,
    onEveningChange: (Boolean) -> Unit,
    onEveningTimeChange: (Int) -> Unit,
    onChangeAlertsChange: (Boolean) -> Unit,
    onSendSample: () -> Unit,
    onSendDigestSample: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenGithub: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Section(stringResource(R.string.theme_title)) {
                    Chips {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> R.string.theme_system
                                                ThemeMode.LIGHT -> R.string.theme_light
                                                ThemeMode.DARK -> R.string.theme_dark
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                Section(stringResource(R.string.section_reminders)) {
                    SwitchRow(stringResource(R.string.setting_reminders), settings.remindersEnabled, onRemindersChange)
                    if (settings.remindersEnabled) {
                        Hint(stringResource(R.string.reminder_lead_hint))
                        Chips {
                            LeadOptions.forEach { minutes ->
                                FilterChip(
                                    selected = settings.reminderLeadMinutes == minutes,
                                    onClick = { onLeadChange(minutes) },
                                    label = { Text(if (minutes == 60) "1 ч" else "$minutes мин") },
                                )
                            }
                        }
                        Chips {
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
                        TextButton(onClick = onSendSample) { Text(stringResource(R.string.show_sample)) }
                    }
                }

                Section(stringResource(R.string.section_digest)) {
                    SwitchRow(stringResource(R.string.setting_digest_morning), settings.morningDigestEnabled, onMorningChange)
                    if (settings.morningDigestEnabled) {
                        Chips {
                            MorningTimes.forEach { minutes ->
                                FilterChip(
                                    selected = settings.morningDigestMinutes == minutes,
                                    onClick = { onMorningTimeChange(minutes) },
                                    label = { Text(clockLabel(minutes)) },
                                )
                            }
                        }
                    }
                    SwitchRow(stringResource(R.string.setting_digest_evening), settings.eveningDigestEnabled, onEveningChange)
                    if (settings.eveningDigestEnabled) {
                        Chips {
                            EveningTimes.forEach { minutes ->
                                FilterChip(
                                    selected = settings.eveningDigestMinutes == minutes,
                                    onClick = { onEveningTimeChange(minutes) },
                                    label = { Text(clockLabel(minutes)) },
                                )
                            }
                        }
                    }
                    Hint(stringResource(R.string.digest_hint))
                    TextButton(onClick = onSendDigestSample) { Text(stringResource(R.string.show_digest_sample)) }
                }

                Section(stringResource(R.string.section_changes)) {
                    SwitchRow(stringResource(R.string.setting_change_alerts), settings.changeAlertsEnabled, onChangeAlertsChange)
                    Hint(stringResource(R.string.changes_background_hint))
                }

                Section(stringResource(R.string.section_reliability)) {
                    Hint(stringResource(R.string.reliability_intro))
                    StatusRow(stringResource(R.string.status_notifications), status.notifications, stringResource(R.string.allow), onRequestNotifications)
                    StatusRow(stringResource(R.string.status_exact_alarms), status.exactAlarms, stringResource(R.string.allow), onOpenExactAlarmSettings)
                    StatusRow(stringResource(R.string.status_battery), status.batteryUnrestricted, stringResource(R.string.open_settings), onOpenBatterySettings)
                    Hint(stringResource(R.string.battery_how_to))
                    TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.open_app_settings)) }
                }

                Section(stringResource(R.string.section_about)) {
                    Text(text = versionLabel, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onCheckUpdates) { Text(stringResource(R.string.check_updates)) }
                        TextButton(onClick = onOpenGithub) { Text(stringResource(R.string.source_code)) }
                    }
                    TextButton(onClick = onReport) { Text(stringResource(R.string.report_problem)) }
                    Hint(stringResource(R.string.report_hint))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusRow(label: String, ok: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (ok) "✓" else "✗",
            style = MaterialTheme.typography.titleMedium,
            color = if (ok) MaterialTheme.pary.practice else MaterialTheme.pary.error,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!ok) TextButton(onClick = onAction) { Text(actionLabel) }
    }
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
