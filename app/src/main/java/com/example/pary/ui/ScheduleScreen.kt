package com.example.pary.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pary.R
import com.example.pary.data.AppSettings
import com.example.pary.data.DayItem
import com.example.pary.data.Dates
import com.example.pary.data.GroupInfo
import com.example.pary.data.Lesson
import com.example.pary.data.dayItems
import com.example.pary.data.formatGap
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.data.isFinishedAt
import com.example.pary.data.isRunningAt
import com.example.pary.data.on
import com.example.pary.data.subgroupNumbers
import com.example.pary.ui.theme.ParyColors
import com.example.pary.ui.theme.lessonAccent
import com.example.pary.notify.Notifications
import com.example.pary.notify.Reminders
import com.example.pary.notify.onScheduleChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Понедельник недели, которую показываем сегодня; в воскресенье пар нет, поэтому берём следующую. */
private fun weekStartToShow(today: Long): Long {
    val dow = Dates.dayOfWeek(today)
    return if (dow == 6) today + 1 else today - dow
}

private fun dayIndexToShow(today: Long): Int = Dates.dayOfWeek(today).let { if (it == 6) 0 else it }

// Дни образуют одну ленту по 6 в неделе (Пн–Сб), на 52 недели в каждую сторону от текущей:
// свайп с субботы ведёт на понедельник следующей недели.
private const val DAYS_PER_WEEK = 6
private const val WEEKS_RANGE = 52
private const val PAGE_COUNT = DAYS_PER_WEEK * (2 * WEEKS_RANGE + 1)

private fun pageOf(weekOffset: Int, dayIndex: Int): Int = (WEEKS_RANGE + weekOffset) * DAYS_PER_WEEK + dayIndex

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    group: GroupInfo,
    repository: ScheduleRepository,
    onChangeGroup: () -> Unit,
    onSubgroupChange: (Int) -> Unit,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
) {
    var lessons by remember(group.slug) { mutableStateOf<List<Lesson>?>(null) }
    var savedAt by remember(group.slug) { mutableStateOf<Long?>(null) }
    var isLoading by remember(group.slug) { mutableStateOf(true) }
    var hasError by remember(group.slug) { mutableStateOf(false) }
    var reload by remember(group.slug) { mutableIntStateOf(0) }
    var changes by remember(group.slug) { mutableStateOf<List<String>>(emptyList()) }
    var showChanges by remember { mutableStateOf(false) }
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    var showSettings by remember { mutableStateOf(false) }
    // Разрешение на уведомления (Android 13+): включаем напоминание только после согласия.
    var pendingPermissionAction by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted && action != null) scope.launch { action() }
    }
    fun withNotificationPermission(action: suspend () -> Unit) {
        if (Notifications.canPost(appContext)) {
            scope.launch { action() }
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(group.slug, reload) {
        isLoading = true
        hasError = false
        repository.cached(group.slug)?.let {
            lessons = it.lessons
            savedAt = it.savedAt
        }
        changes = repository.pendingChanges(group.slug)
        try {
            val fresh = repository.refresh(group.slug, group.subgroup)
            lessons = fresh.lessons
            savedAt = fresh.savedAt
            changes = repository.pendingChanges(group.slug)
            onScheduleChanged(appContext) // виджет читает тот же кэш
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            hasError = true
        }
        isLoading = false
    }

    // Раз в полминуты обновляем «сейчас»: так подсветка идущей пары и «сегодня» не устаревают.
    val nowMillis by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val today = Dates.todayOf(nowMillis)
    val nowMinutes = Dates.minutesOfDayAt(nowMillis)

    val baseWeekStart = remember { weekStartToShow(Dates.today()) }
    val pagerState = rememberPagerState(initialPage = pageOf(0, dayIndexToShow(Dates.today()))) { PAGE_COUNT }
    val page = pagerState.currentPage
    val weekStart = baseWeekStart + 7L * (page / DAYS_PER_WEEK - WEEKS_RANGE)
    val dayIndex = page % DAYS_PER_WEEK
    val selectedDay = weekStart + dayIndex
    val isCurrentWeek = weekStart == weekStartToShow(today)
    fun goToPage(target: Int) {
        scope.launch { pagerState.animateScrollToPage(target.coerceIn(0, PAGE_COUNT - 1)) }
    }

    val loaded = lessons
    val subgroups = remember(loaded) { loaded.orEmpty().subgroupNumbers() }

    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Header(
                title = Dates.longLabel(selectedDay),
                groupName = group.name,
                isLoading = isLoading,
                notificationsOn = settings.remindersEnabled,
                onRefresh = { reload++ },
                onOpenNotifications = { showSettings = true },
                onChangeGroup = onChangeGroup,
            )
            WeekSwitcher(
                weekStart = weekStart,
                isCurrentWeek = isCurrentWeek,
                onPrev = { goToPage(page - DAYS_PER_WEEK) },
                onNext = { goToPage(page + DAYS_PER_WEEK) },
                onToday = {
                    val weekOffset = ((weekStartToShow(today) - baseWeekStart) / 7).toInt()
                    goToPage(pageOf(weekOffset, dayIndexToShow(today)))
                },
            )
            DayStrip(
                weekStart = weekStart,
                selected = dayIndex,
                today = today,
                hasLessons = { i -> loaded?.on(weekStart + i, group.subgroup)?.isNotEmpty() ?: true },
                onSelect = { goToPage(page - dayIndex + it) },
            )
            if (subgroups.isNotEmpty()) {
                SubgroupChips(subgroups, group.subgroup, onSubgroupChange)
            }
            if (changes.isNotEmpty()) {
                ChangesBanner(count = changes.size, onClick = { showChanges = true })
            }
            if (hasError && loaded != null) {
                OfflineBanner(formatSavedAt(savedAt))
            }
            when {
                loaded != null -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    // По умолчанию страницы выравниваются по центру: короткий день «плавал» бы посередине.
                    verticalAlignment = Alignment.Top,
                ) { p ->
                    val day = baseWeekStart + 7L * (p / DAYS_PER_WEEK - WEEKS_RANGE) + p % DAYS_PER_WEEK
                    DayLessons(
                        lessons = loaded.on(day, group.subgroup),
                        isToday = day == today,
                        nowMinutes = nowMinutes,
                    )
                }
                hasError -> ErrorState(onRetry = { reload++ })
                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (showChanges) {
        ChangesDialog(
            changes = changes,
            onAcknowledge = {
                showChanges = false
                changes = emptyList()
                scope.launch { repository.dismissChanges(group.slug) }
            },
            onDismiss = { showChanges = false },
        )
    }

    if (showSettings) {
        NotificationSettingsDialog(
            settings = settings,
            isExactAlarmAllowed = Reminders.canScheduleExact(appContext),
            onRemindersChange = { on ->
                if (on) withNotificationPermission { settingsRepository.setRemindersEnabled(true) }
                else scope.launch { settingsRepository.setRemindersEnabled(false) }
            },
            onLeadChange = { scope.launch { settingsRepository.setReminderLeadMinutes(it) } },
            onChangeAlertsChange = { on ->
                if (on) withNotificationPermission { settingsRepository.setChangeAlertsEnabled(true) }
                else scope.launch { settingsRepository.setChangeAlertsEnabled(false) }
            },
            onSendSample = {
                withNotificationPermission { Reminders.postSample(appContext, settings.reminderLeadMinutes) }
            },
            onOpenExactAlarmSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appContext.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + appContext.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun Header(
    title: String,
    groupName: String,
    isLoading: Boolean,
    notificationsOn: Boolean,
    onRefresh: () -> Unit,
    onOpenNotifications: () -> Unit,
    onChangeGroup: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onChangeGroup),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = groupName, style = MaterialTheme.typography.headlineMedium)
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.open_group_menu),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onOpenNotifications) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(R.string.notifications_button),
                tint = if (notificationsOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun WeekSwitcher(
    weekStart: Long,
    isCurrentWeek: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val isAbove = Dates.isAboveWeek(weekStart)
    val color = if (isAbove) ParyColors.Above else ParyColors.Below
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.prev_week))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Tag(
                text = "● " + stringResource(if (isAbove) R.string.week_above else R.string.week_below),
                color = color,
            )
            Text(
                text = weekRange(weekStart),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!isCurrentWeek) {
                Text(
                    text = stringResource(R.string.to_current_week),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onToday).padding(4.dp),
                )
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_week))
        }
    }
}

@Composable
private fun DayStrip(
    weekStart: Long,
    selected: Int,
    today: Long,
    hasLessons: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0..5) {
            val day = weekStart + i
            val isSelected = i == selected
            val dim = !hasLessons(i) && !isSelected
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = Dates.DayNames[i],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(if (dim) 0.45f else 1f),
                )
                Text(
                    text = Dates.civil(day).third.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    modifier = Modifier.alpha(if (dim) 0.45f else 1f),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                day != today -> Color.Transparent
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.primary
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun SubgroupChips(subgroups: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == 0,
            onClick = { onSelect(0) },
            label = { Text(stringResource(R.string.subgroup_all)) },
        )
        subgroups.forEach { number ->
            FilterChip(
                selected = selected == number,
                onClick = { onSelect(number) },
                label = { Text(stringResource(R.string.subgroup_n, number)) },
            )
        }
    }
}

@Composable
private fun ChangesBanner(count: Int, onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.changes_banner, count),
        style = MaterialTheme.typography.labelLarge,
        color = ParyColors.Accent,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ParyColors.Accent.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(12.dp),
    )
}

@Composable
private fun ChangesDialog(changes: List<String>, onAcknowledge: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.changes_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                changes.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = onAcknowledge) { Text(stringResource(R.string.got_it)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun OfflineBanner(savedAt: String) {
    Text(
        text = stringResource(R.string.offline_cached, savedAt),
        style = MaterialTheme.typography.labelMedium,
        color = ParyColors.Lab,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ParyColors.Lab.copy(alpha = 0.14f))
            .padding(12.dp),
    )
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.schedule_load_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun DayLessons(lessons: List<Lesson>, isToday: Boolean, nowMinutes: Int) {
    if (lessons.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "☕", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.no_lessons),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.no_lessons_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(dayItems(lessons)) { item ->
            when (item) {
                is DayItem.LessonItem -> LessonCard(
                    lesson = item.lesson,
                    isNow = isToday && item.lesson.isRunningAt(nowMinutes),
                    isDone = isToday && item.lesson.isFinishedAt(nowMinutes),
                )
                is DayItem.Gap -> GapRow(item.minutes)
            }
        }
    }
}

/** Окно между парами: тонкая линия с подписью «Окно · 1 ч 35 мин». */
@Composable
private fun GapRow(minutes: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(
            text = stringResource(R.string.gap_label, formatGap(minutes)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonCard(lesson: Lesson, isNow: Boolean, isDone: Boolean) {
    val accent = lessonAccent(lesson.type)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isNow) ParyColors.AccentContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isNow) BorderStroke(1.5.dp, accent) else null,
        modifier = Modifier.fillMaxWidth().alpha(if (isDone) 0.6f else 1f),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min).padding(14.dp)) {
            Column(modifier = Modifier.width(52.dp)) {
                Text(text = lesson.start, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = lesson.end,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().clip(CircleShape).background(accent))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isNow) {
                    Text(
                        text = stringResource(R.string.now_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
                Text(text = lesson.subject, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lesson.type?.let { Tag(it, accent) }
                    lesson.rooms.joinToString(", ").ifEmpty { null }?.let { Tag(it, MaterialTheme.colorScheme.onSurfaceVariant) }
                    lesson.subgroups.sorted().joinToString(", ").ifEmpty { null }?.let {
                        Tag(stringResource(R.string.lesson_subgroups, it), ParyColors.Below)
                    }
                }
                if (lesson.teachers.isNotEmpty()) {
                    Text(
                        text = lesson.teachers.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Небольшая цветная метка: тип занятия, аудитория, подгруппа, неделя. */
@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** «19–24 окт» или «28 сен – 3 окт», если неделя захватывает два месяца. */
private fun weekRange(weekStart: Long): String {
    val (_, m1, d1) = Dates.civil(weekStart)
    val (_, m2, d2) = Dates.civil(weekStart + 5)
    return if (m1 == m2) "$d1–$d2 ${Dates.MonthNames[m2 - 1]}" else "$d1 ${Dates.MonthNames[m1 - 1]} – $d2 ${Dates.MonthNames[m2 - 1]}"
}

private fun formatSavedAt(millis: Long?): String =
    if (millis == null || millis == 0L) "—"
    else SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("ru")).format(Date(millis))
