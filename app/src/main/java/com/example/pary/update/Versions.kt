package com.example.pary.update

/** Чистые функции для проверки обновлений: разбор версий и адресов (их удобно проверять тестами). */
object Versions {
    const val REPO = "ar1stov/pary-gstu"

    private val TAG = Regex("^v?(\\d+(?:\\.\\d+)*)$")
    private val HASH = Regex("[0-9a-fA-F]{64}")

    /** «v1.2» -> «1.2»; null, если тег не похож на версию. */
    fun fromTag(tag: String): String? = TAG.matchEntire(tag.trim())?.groupValues?.get(1)

    /** Тег из адреса, на который GitHub перенаправляет /releases/latest, например «…/releases/tag/v1.2». */
    fun tagFromLocation(location: String?): String? {
        val tag = location?.substringAfter("/releases/tag/", missingDelimiterValue = "")?.substringBefore('?')?.trim('/')
        return tag?.takeIf { it.isNotEmpty() }
    }

    /** Версия [candidate] новее [installed]: сравниваем числа по частям, недостающие части считаем нулями. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun apkUrl(tag: String, version: String): String =
        "https://github.com/$REPO/releases/download/$tag/Pary-GGTU-$version.apk"

    fun releaseUrl(tag: String): String = "https://github.com/$REPO/releases/tag/$tag"

    /** Контрольная сумма из файла `.sha256`: берём первое слово из 64 шестнадцатеричных символов. */
    fun expectedHash(text: String): String? =
        text.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { HASH.matches(it) }?.lowercase()

    private fun parts(version: String): List<Int> = version.split('.').map { it.toIntOrNull() ?: 0 }
}
