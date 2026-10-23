package com.example.pary.update

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Новая версия, которую можно скачать. */
data class UpdateInfo(
    val versionName: String,
    val tag: String,
    val apkUrl: String,
    val sha256Url: String,
    val releaseUrl: String,
)

/**
 * Узнаёт, вышла ли новая версия. Вместо API GitHub (у него лимит 60 запросов в час на адрес, а в
 * университете у многих студентов один адрес) спрашиваем обычную страницу /releases/latest:
 * GitHub перенаправляет её на страницу последнего релиза, и из адреса видно номер версии.
 */
object UpdateChecker {
    private const val LATEST_URL = "https://github.com/${Versions.REPO}/releases/latest"

    /** Установленная версия, например «1.1». */
    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    /** Информация о новой версии или null, если установлена последняя (или проверить не удалось). */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "ParyGGTU-Updater")
            val tag = Versions.tagFromLocation(connection.getHeaderField("Location")) ?: return@withContext null
            val version = Versions.fromTag(tag) ?: return@withContext null
            if (!Versions.isNewer(version, installedVersion(context))) return@withContext null
            val apkUrl = Versions.apkUrl(tag, version)
            UpdateInfo(version, tag, apkUrl, "$apkUrl.sha256", Versions.releaseUrl(tag))
        } finally {
            connection.disconnect()
        }
    }
}
