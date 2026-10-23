package com.example.pary.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.pary.MainActivity
import com.example.pary.R
import com.example.pary.data.Dates
import com.example.pary.data.GroupInfo
import com.example.pary.data.GroupRepository
import com.example.pary.data.Lesson
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.data.isFinishedAt
import com.example.pary.data.isRunningAt
import com.example.pary.data.nextDayWithLessons
import com.example.pary.data.on
import com.example.pary.notify.ChangeNotifier
import com.example.pary.ui.theme.ParyColors
import com.example.pary.ui.theme.lessonAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Виджет «Расписание на сегодня»: берёт данные из того же кэша, что и приложение. */
open class ScheduleWidget(private val isCompact: Boolean = false) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val group = GroupRepository(context).selectedGroup.first()
        val repository = ScheduleRepository(context)

        var snapshot = group?.let { repository.cached(it.slug) }
        val isStale = snapshot == null || System.currentTimeMillis() - snapshot.savedAt > STALE_AFTER_MS
        if (group != null && isStale) {
            // Не вышло обновить — показываем то, что было сохранено.
            snapshot = try {
                repository.refresh(group.slug, group.subgroup).also { fresh ->
                    if (fresh.changes.isNotEmpty() && SettingsRepository(context).current().changeAlertsEnabled) {
                        ChangeNotifier.post(context, group.name, fresh.changes)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snapshot
            }
        }

        val lessons = snapshot?.lessons
        val now = System.currentTimeMillis()
        val today = Dates.todayOf(now)
        val nowMinutes = Dates.minutesOfDayAt(now)
        if (group != null && lessons != null) {
            WidgetScheduler.scheduleNext(context, lessons, group.subgroup, now)
        }
        provideContent { WidgetContent(context, group, lessons, today, nowMinutes, isCompact) }
    }

    private companion object {
        const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L
    }
}

/** Компактный виджет 4×2: две ближайшие пары. */
class CompactScheduleWidget : ScheduleWidget(isCompact = true)

private fun color(c: Color) = ColorProvider(c)

private fun style(c: Color, size: Int, bold: Boolean = false) =
    TextStyle(color = color(c), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

@Composable
private fun WidgetContent(
    context: Context,
    group: GroupInfo?,
    lessons: List<Lesson>?,
    today: Long,
    nowMinutes: Int,
    isCompact: Boolean,
) {
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(color(ParyColors.Background))
            .cornerRadius(22.dp)
            .padding(if (isCompact) 10.dp else 14.dp)
            .clickable(openApp),
    ) {
        when {
            group == null -> Message(context.getString(R.string.widget_pick_group))
            lessons == null -> Message(context.getString(R.string.widget_no_data))
            isCompact -> CompactSchedule(context, group, lessons, today, nowMinutes, openApp)
            else -> Schedule(context, group, lessons, today, nowMinutes, openApp)
        }
    }
}

@Composable
private fun Message(text: String, modifier: GlanceModifier = GlanceModifier) {
    Text(text = text, modifier = modifier, style = style(ParyColors.Text, 14))
}

@Composable
private fun Schedule(
    context: Context,
    group: GroupInfo,
    lessons: List<Lesson>,
    today: Long,
    nowMinutes: Int,
    openApp: Action,
) {
    val shownDay = lessons.nextDayWithLessons(today, group.subgroup)
    val day = shownDay ?: today
    val isAbove = Dates.isAboveWeek(day)
    val week = context.getString(if (isAbove) R.string.week_above else R.string.week_below)

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(text = Dates.longLabel(day), style = style(ParyColors.TextMuted, 12))
            Text(text = group.name, style = style(ParyColors.Text, 22, bold = true))
        }
        Text(
            text = "● $week",
            style = style(if (isAbove) ParyColors.Above else ParyColors.Below, 12, bold = true),
        )
    }
    if (shownDay == null) {
        Message(context.getString(R.string.widget_no_lessons_soon), GlanceModifier.padding(top = 10.dp))
        return
    }
    if (shownDay != today) {
        Text(
            text = context.getString(R.string.widget_no_lessons_today),
            modifier = GlanceModifier.padding(top = 6.dp),
            style = style(ParyColors.Accent, 12),
        )
    }
    LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(top = 8.dp)) {
        items(lessons.on(day, group.subgroup)) { lesson ->
            LessonRow(lesson, isNow = day == today && lesson.isRunningAt(nowMinutes), openApp = openApp)
        }
    }
}

private class Shown(val day: Long, val lessons: List<Lesson>)

@Composable
private fun CompactSchedule(
    context: Context,
    group: GroupInfo,
    lessons: List<Lesson>,
    today: Long,
    nowMinutes: Int,
    openApp: Action,
) {
    // Сегодня показываем только ещё не закончившиеся пары; когда их нет, ближайший день с парами.
    val left = lessons.on(today, group.subgroup).filter { !it.isFinishedAt(nowMinutes) }
    val shown = if (left.isNotEmpty()) {
        Shown(today, left)
    } else {
        lessons.nextDayWithLessons(today + 1, group.subgroup)?.let { Shown(it, lessons.on(it, group.subgroup)) }
    }
    if (shown == null) {
        Text(text = group.name, style = style(ParyColors.Text, 16, bold = true))
        Message(context.getString(R.string.widget_no_lessons_soon), GlanceModifier.padding(top = 6.dp))
        return
    }

    val isAbove = Dates.isAboveWeek(shown.day)
    val dateText = Dates.label(shown.day)
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = group.name, style = style(ParyColors.Text, 16, bold = true))
        Text(
            text = "  $dateText",
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = style(ParyColors.TextMuted, 11),
        )
        Text(text = "●", style = style(if (isAbove) ParyColors.Above else ParyColors.Below, 12, bold = true))
    }
    LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(top = 6.dp)) {
        items(shown.lessons) { lesson ->
            LessonRow(
                lesson,
                isNow = shown.day == today && lesson.isRunningAt(nowMinutes),
                openApp = openApp,
                compact = true,
            )
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, isNow: Boolean, openApp: Action, compact: Boolean = false) {
    val accent = lessonAccent(lesson.type)
    val main = if (compact) 12 else 13
    val small = if (compact) 10 else 11
    var modifier = GlanceModifier.fillMaxWidth()
        .padding(vertical = if (compact) 3.dp else 4.dp, horizontal = 6.dp)
        .clickable(openApp)
    if (isNow) modifier = modifier.background(color(ParyColors.AccentContainer)).cornerRadius(12.dp)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.width(44.dp)) {
            Text(text = lesson.start, style = style(ParyColors.Text, main, bold = true))
            Text(text = lesson.end, style = style(ParyColors.TextMuted, small))
        }
        Box(modifier = GlanceModifier.width(3.dp).fillMaxHeight().background(color(accent))) {}
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Text(text = lesson.subject, maxLines = 1, style = style(ParyColors.Text, main, bold = isNow))
            Row {
                lesson.type?.let { Text(text = it, maxLines = 1, style = style(accent, small)) }
                val rest = listOfNotNull(
                    lesson.rooms.joinToString(", ").ifEmpty { null },
                    lesson.teachers.joinToString(", ").ifEmpty { null },
                ).joinToString(" · ")
                if (rest.isNotEmpty()) {
                    Text(
                        text = (if (lesson.type != null) " · " else "") + rest,
                        maxLines = 1,
                        style = style(ParyColors.TextMuted, small),
                    )
                }
            }
        }
    }
}
