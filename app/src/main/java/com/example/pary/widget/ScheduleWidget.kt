package com.example.pary.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.example.pary.MainActivity
import com.example.pary.R
import com.example.pary.data.Dates
import com.example.pary.data.GroupInfo
import com.example.pary.data.GroupRepository
import com.example.pary.data.Lesson
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.data.dayToShow
import com.example.pary.data.isFinishedAt
import com.example.pary.data.isRunningAt
import com.example.pary.data.on
import com.example.pary.notify.ChangeNotifier
import com.example.pary.ui.theme.ParyColors
import com.example.pary.ui.theme.ParyPalette
import com.example.pary.ui.theme.lessonAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Виджет «Расписание на сегодня»: берёт данные из того же кэша, что и приложение. */
open class ScheduleWidget(private val isCompact: Boolean = false) : GlanceAppWidget() {

    // Большому виджету нужен точный размер: по нему считается высота строки, чтобы вмещались ровно 4 пары.
    override val sizeMode: SizeMode get() = if (isCompact) SizeMode.Single else SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = ScheduleRepository(context)

        // Сразу рисуем то, что сохранено на телефоне; из сети догружаем уже после первой отрисовки,
        // иначе виджет после смены группы ждал бы ответа сайта и показывал бы старое.
        val startVersion = WidgetVersion.flow.value
        val first = loadData(context, repository)

        provideContent {
            // Пока сессия виджета жива, повторное обновление не вызывает provideGlance заново, а лишь
            // перерисовывает то же содержимое: без этого после смены подгруппы виджет оставался бы старым.
            // Поэтому данные хранятся в состоянии и перечитываются при каждом новом номере обновления.
            val version by WidgetVersion.flow.collectAsState()
            var data by remember { mutableStateOf(first) }
            LaunchedEffect(version) {
                if (version != startVersion) data = loadData(context, repository)
                val group = data.group ?: return@LaunchedEffect
                val shown = data.snapshot
                if (shown == null || data.nowMillis - shown.savedAt > STALE_AFTER_MS) {
                    val fresh = refreshOnce(context, repository, group)
                    data = data.copy(snapshot = fresh ?: data.snapshot, loading = false, nowMillis = System.currentTimeMillis())
                }
                data.snapshot?.let { WidgetScheduler.scheduleNext(context, it.lessons, group.subgroup, System.currentTimeMillis()) }
            }
            val today = Dates.todayOf(data.nowMillis)
            val nowMinutes = Dates.minutesOfDayAt(data.nowMillis)
            WidgetContent(context, data.group, data.snapshot?.lessons, today, nowMinutes, isCompact, data.loading)
        }
    }

    /** Всё, что нужно для отрисовки виджета; берётся только с телефона, без сети. */
    private class WidgetData(
        val group: GroupInfo?,
        val snapshot: ScheduleRepository.Snapshot?,
        val loading: Boolean,
        val nowMillis: Long,
    ) {
        fun copy(
            snapshot: ScheduleRepository.Snapshot? = this.snapshot,
            loading: Boolean = this.loading,
            nowMillis: Long = this.nowMillis,
        ) = WidgetData(group, snapshot, loading, nowMillis)
    }

    private suspend fun loadData(context: Context, repository: ScheduleRepository): WidgetData {
        val group = GroupRepository(context).selectedGroup.first()
        val cached = group?.let { repository.cached(it.slug) }
        return WidgetData(group, cached, loading = group != null && cached == null, nowMillis = System.currentTimeMillis())
    }

    private companion object {
        const val STALE_AFTER_MS = 3 * 60 * 60 * 1000L

        /** Два виджета обновляются одновременно: второй не идёт в сеть, если первый уже всё загрузил. */
        val refreshLock = Mutex()

        /** Свежее расписание или то, что было сохранено, если сайт не ответил; null, если нет ничего. */
        suspend fun refreshOnce(context: Context, repository: ScheduleRepository, group: GroupInfo): ScheduleRepository.Snapshot? =
            refreshLock.withLock {
                val latest = repository.cached(group.slug)
                if (latest != null && System.currentTimeMillis() - latest.savedAt <= STALE_AFTER_MS) return@withLock latest
                try {
                    repository.refresh(group.slug, group.subgroup).also { fresh ->
                        if (fresh.changes.isNotEmpty() && SettingsRepository(context).current().changeAlertsEnabled) {
                            ChangeNotifier.post(context, group.name, fresh.changes)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    latest
                }
            }
    }
}

/** Компактный виджет 4×2: две ближайшие пары. */
class CompactScheduleWidget : ScheduleWidget(isCompact = true)

/** Какой цвет брать из палитры. Виджет показывает светлую палитру днём и тёмную ночью, по теме телефона. */
private typealias Pick = (ParyPalette) -> Color

private fun color(pick: Pick) = ColorProvider(day = pick(ParyColors.Light), night = pick(ParyColors.Dark))

private fun style(pick: Pick, size: Int, bold: Boolean = false) =
    TextStyle(color = color(pick), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

@Composable
private fun WidgetContent(
    context: Context,
    group: GroupInfo?,
    lessons: List<Lesson>?,
    today: Long,
    nowMinutes: Int,
    isCompact: Boolean,
    loading: Boolean,
) {
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(color { it.background })
            .cornerRadius(22.dp)
            .padding(if (isCompact) 10.dp else 14.dp)
            .clickable(openApp),
    ) {
        when {
            group == null -> Message(context.getString(R.string.widget_pick_group))
            lessons == null -> Message(context.getString(if (loading) R.string.widget_loading else R.string.widget_no_data))
            isCompact -> CompactSchedule(context, group, lessons, today, nowMinutes, openApp)
            else -> Schedule(context, group, lessons, today, nowMinutes, openApp)
        }
    }
}

@Composable
private fun Message(text: String, modifier: GlanceModifier = GlanceModifier) {
    Text(text = text, modifier = modifier, style = style({ it.text }, 14))
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
    val shownDay = lessons.dayToShow(today, nowMinutes, group.subgroup)
    val day = shownDay ?: today
    val isAbove = Dates.isAboveWeek(day)
    val week = context.getString(if (isAbove) R.string.week_above else R.string.week_below)

    // Сегодня пары были, но закончились, или их не было совсем: короткая пометка рядом с датой.
    val note = if (shownDay != null && shownDay != today) {
        val hadLessonsToday = lessons.on(today, group.subgroup).isNotEmpty()
        context.getString(if (hadLessonsToday) R.string.widget_today_done else R.string.widget_no_lessons_today)
    } else {
        null
    }

    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row {
                Text(text = Dates.longLabel(day), maxLines = 1, style = style({ it.textMuted }, 12))
                if (note != null) {
                    Text(text = " · $note", maxLines = 1, style = style({ it.accent }, 12))
                }
            }
            Text(text = group.name, style = style({ it.text }, 20, bold = true))
        }
        Text(
            text = "● $week",
            style = style({ if (isAbove) it.above else it.below }, 12, bold = true),
        )
    }
    if (shownDay == null) {
        Message(context.getString(R.string.widget_no_lessons_soon), GlanceModifier.padding(top = 10.dp))
        return
    }

    // Высота строки подбирается так, чтобы в оставшееся место помещались ровно ROWS_VISIBLE пар:
    // от высоты виджета отнимаем поля, шапку и отступ под ней.
    val rowHeight = ((LocalSize.current.height.value - 2 * WIDGET_PADDING_DP - HEADER_DP - LIST_GAP_DP) / ROWS_VISIBLE - 0.5f)
        .coerceAtLeast(MIN_ROW_DP).dp
    val dayLessons = lessons.on(day, group.subgroup)
    if (dayLessons.size <= ROWS_VISIBLE) {
        // До четырёх пар: место делится поровну между четырьмя строками по настоящей высоте виджета
        // (пустые строки держат размер), поэтому пары заполняют его ровно при любой высоте.
        Column(modifier = GlanceModifier.fillMaxSize().padding(top = LIST_GAP_DP.dp)) {
            dayLessons.forEach { lesson ->
                LessonRow(
                    lesson,
                    isNow = day == today && lesson.isRunningAt(nowMinutes),
                    openApp = openApp,
                    isDone = day == today && lesson.isFinishedAt(nowMinutes),
                    rowHeight = rowHeight,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
            repeat(ROWS_VISIBLE - dayLessons.size) { Box(modifier = GlanceModifier.defaultWeight()) {} }
        }
    } else {
        // Больше четырёх пар: список прокручивается, видимых строк по-прежнему четыре.
        LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(top = LIST_GAP_DP.dp)) {
            items(dayLessons) { lesson ->
                LessonRow(
                    lesson,
                    isNow = day == today && lesson.isRunningAt(nowMinutes),
                    openApp = openApp,
                    isDone = day == today && lesson.isFinishedAt(nowMinutes),
                    rowHeight = rowHeight,
                    fixedHeight = true,
                )
            }
        }
    }
}

private const val ROWS_VISIBLE = 4
private const val WIDGET_PADDING_DP = 14f
private const val HEADER_DP = 40f
private const val LIST_GAP_DP = 6f
private const val MIN_ROW_DP = 26f

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
    // Тот же день, что показывает виджет побольше; сегодня только ещё не закончившиеся пары.
    val shown = lessons.dayToShow(today, nowMinutes, group.subgroup)?.let { day ->
        val all = lessons.on(day, group.subgroup)
        Shown(day, if (day == today) all.filter { !it.isFinishedAt(nowMinutes) }.ifEmpty { all } else all)
    }
    if (shown == null) {
        Text(text = group.name, style = style({ it.text }, 16, bold = true))
        Message(context.getString(R.string.widget_no_lessons_soon), GlanceModifier.padding(top = 6.dp))
        return
    }

    val isAbove = Dates.isAboveWeek(shown.day)
    val dateText = Dates.label(shown.day)
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = group.name, style = style({ it.text }, 16, bold = true))
        Text(
            text = "  $dateText",
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = style({ it.textMuted }, 11),
        )
        Text(text = "●", style = style({ if (isAbove) it.above else it.below }, 12, bold = true))
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
private fun LessonRow(
    lesson: Lesson,
    isNow: Boolean,
    openApp: Action,
    compact: Boolean = false,
    isDone: Boolean = false,
    /** Высота строки большого виджета: по ней выбираем размер шрифта, а при [fixedHeight] ещё и задаём её. */
    rowHeight: Dp? = null,
    fixedHeight: Boolean = false,
    modifier: GlanceModifier = GlanceModifier,
) {
    val accent: Pick = { lessonAccent(lesson.type, it) }
    val textColor: Pick = if (isDone) { { it.textMuted } } else { { it.text } }
    // В невысокой строке две строки текста по 13 и 11 sp не уместятся: берём мельче.
    val dense = compact || (rowHeight != null && rowHeight < 36.dp)
    val main = if (dense) 12 else 13
    val small = if (dense) 10 else 11
    var rowModifier = modifier.fillMaxWidth()
    if (fixedHeight && rowHeight != null) rowModifier = rowModifier.height(rowHeight)
    rowModifier = rowModifier
        .padding(vertical = if (dense) 2.dp else 4.dp, horizontal = 6.dp)
        .clickable(openApp)
    // Фон задаётся у каждой строки, в том числе прозрачный: иначе RemoteViews при перерисовке оставляет
    // фон, который строка получила, пока шла пара, и подсвеченными оказываются сразу две пары.
    rowModifier = rowModifier
        .background(if (isNow) color { it.accentContainer } else ColorProvider(day = Color.Transparent, night = Color.Transparent))
        .cornerRadius(12.dp)

    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.width(44.dp)) {
            Text(text = lesson.start, style = style(textColor, main, bold = true))
            Text(text = lesson.end, style = style({ it.textMuted }, small))
        }
        Box(modifier = GlanceModifier.width(3.dp).fillMaxHeight().background(color(accent))) {}
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Text(text = lesson.subject, maxLines = 1, style = style(textColor, main, bold = isNow))
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
                        style = style({ it.textMuted }, small),
                    )
                }
            }
        }
    }
}
