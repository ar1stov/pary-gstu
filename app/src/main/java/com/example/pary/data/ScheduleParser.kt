package com.example.pary.data

import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/** Разбор ответов sc.gstu.by. Бросает IOException, если ответ не такой, как ожидаем. */
object ScheduleParser {
    fun parseGroups(json: String): GroupSearchResult {
        val data = dataOf(json)
        val array = data.optJSONArray("groups") ?: JSONArray()
        val groups = (0 until array.length()).map { i ->
            val g = array.getJSONObject(i)
            GroupInfo(
                slug = g.getString("slug"),
                name = g.getString("name"),
                course = g.optInt("course", 0),
                faculty = g.str("facultyName") ?: "",
            )
        }
        return GroupSearchResult(groups, data.optBoolean("hasMore", false))
    }

    fun parseSchedule(json: String): List<Lesson> {
        val data = dataOf(json)
        val items = data.optJSONArray("scheduleItems") ?: throw IOException("Нет scheduleItems")
        val groupSlug = data.optJSONObject("entity")?.str("slug")
        return (0 until items.length()).mapNotNull { parseLesson(items.getJSONObject(it), groupSlug) }
    }

    private fun parseLesson(o: JSONObject, groupSlug: String?): Lesson? {
        val day = DAYS.indexOf(o.str("dayOfWeek"))
        val first = Dates.parseIso(o.str("startDate"))
        val last = Dates.parseIso(o.str("endDate"))
        // Воскресенье и записи без дат приложение показать не может.
        if (day !in 0..5 || first == null || last == null) return null

        return Lesson(
            dayOfWeek = day,
            number = o.optInt("lessonNumber", 0),
            start = (o.str("startTime") ?: "").take(5),
            end = (o.str("endTime") ?: "").take(5),
            subject = (o.optJSONObject("subject")?.str("name") ?: "").replace(Regex("\\s+"), " ").trim(),
            type = o.optJSONObject("lessonType")?.str("name"),
            teachers = if (o.optBoolean("hideTeachers")) emptyList() else names(o.optJSONArray("teachers"), "shortName"),
            rooms = if (o.optBoolean("hideClassrooms")) emptyList() else names(o.optJSONArray("classrooms"), "roomNumber"),
            weekType = when (o.str("weekType")) {
                // На сайте EVEN — «над чертой», ODD — «под чертой».
                "EVEN" -> WeekType.ABOVE
                "ODD" -> WeekType.BELOW
                else -> WeekType.ALL
            },
            firstDay = first,
            lastDay = last,
            saturdayWeeks = (1..4).filter { o.optBoolean("week$it") && !o.isNull("week$it") }.toSet(),
            subgroups = subgroupsOf(o.optJSONArray("groups"), groupSlug),
        )
    }

    /** Подгруппы, указанные для самой группы (в списке groups у потоковых пар есть и другие группы). */
    private fun subgroupsOf(groups: JSONArray?, groupSlug: String?): Set<Int> {
        if (groups == null || groupSlug == null) return emptySet()
        for (i in 0 until groups.length()) {
            val g = groups.getJSONObject(i)
            if (g.str("slug") != groupSlug) continue
            val subgroups = g.optJSONArray("subgroups") ?: return emptySet()
            return (0 until subgroups.length()).map { subgroups.getJSONObject(it).getInt("subgroupNumber") }.toSet()
        }
        return emptySet()
    }

    private fun dataOf(json: String): JSONObject {
        val root = JSONObject(json)
        if (!root.optBoolean("success")) throw IOException(root.str("message") ?: "Сервер вернул ошибку")
        return root.getJSONObject("data")
    }

    private fun names(array: JSONArray?, key: String): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.getJSONObject(it).str(key) }
    }

    /** Строка или null: в отличие от optString не превращает JSON null в текст "null". */
    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

    private val DAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
}
