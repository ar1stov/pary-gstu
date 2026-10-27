package com.example.pary.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.pary.R
import com.example.pary.data.GroupInfo
import com.example.pary.data.Lesson
import com.example.pary.data.ScheduleExport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Что показать автору приложения вместе с сообщением об ошибке: версия, телефон, группа. */
fun diagnosticsText(context: Context, group: GroupInfo?, savedAt: Long?): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    val updated = if (savedAt == null || savedAt == 0L) "—" else SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(savedAt))
    val groupLine = group?.let { "${it.name}, подгруппа ${if (it.subgroup == 0) "все" else it.subgroup.toString()}" } ?: "не выбрана"
    return buildString {
        appendLine("Пары ГГТУ ${info.versionName} (${info.longVersionCode})")
        appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Группа: $groupLine")
        append("Расписание обновлено: $updated")
    }
}

/** Копирует сведения о приложении в буфер обмена и открывает чат с автором: их остаётся только вставить. */
fun reportProblem(context: Context, group: GroupInfo?, savedAt: Long?) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Пары ГГТУ", diagnosticsText(context, group, savedAt)))
    Toast.makeText(context, R.string.report_copied, Toast.LENGTH_LONG).show()
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/qwertyarstv")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Отправляет текст в другое приложение (мессенджер, заметки). */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Сохраняет расписание в файл календаря и открывает его: календарь телефона предложит добавить пары. */
fun exportCalendar(context: Context, group: GroupInfo, lessons: List<Lesson>, fromDay: Long, days: Int, notes: Map<String, String>) {
    val dir = File(context.cacheDir, "calendar").apply { mkdirs() }
    val file = File(dir, "pary-${group.slug}.ics")
    file.writeText(ScheduleExport.calendar(group, lessons, fromDay, days, notes), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/calendar").addFlags(flags)
    try {
        context.startActivity(view)
    } catch (e: android.content.ActivityNotFoundException) {
        val send = Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
