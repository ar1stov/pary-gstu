package com.example.pary.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Открытый API сайта sc.gstu.by. Он не документирован, поэтому всё читаем с запасом. */
class ScheduleApi(private val baseUrl: String = "https://sc.gstu.by/api") {

    /** Поиск групп по названию; пустой запрос вернёт первые 10 групп. */
    suspend fun searchGroups(query: String): GroupSearchResult {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return ScheduleParser.parseGroups(get("/schedules/autocomplete?q=$q"))
    }

    /** Сырой JSON расписания группы: его же сохраняем в кэш. */
    suspend fun fetchGroupScheduleJson(slug: String): String =
        get("/schedules/group/${URLEncoder.encode(slug, "UTF-8")}")

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
