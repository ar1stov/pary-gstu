package com.example.pary.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.example.pary.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateException(message: String) : Exception(message)

/** Скачивает обновление, проверяет его и передаёт установщику Android. */
class UpdateInstaller(private val context: Context) {

    /** Разрешил ли пользователь этому приложению устанавливать другие приложения (с Android 8). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Открывает системную страницу, где это разрешение включается. */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * Скачивает файл и проверяет: контрольная сумма совпадает с опубликованной, это наше приложение,
     * версия новее установленной, подпись та же, что у установленной (иначе Android всё равно откажет).
     */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val expected = Versions.expectedHash(fetchText(info.sha256Url))
            ?: throw UpdateException(context.getString(R.string.update_no_checksum))
        val dir = File(context.cacheDir, "updates").apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(dir, "Pary-GGTU-${info.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")

        val connection = open(info.apkUrl)
        try {
            if (connection.responseCode !in 200..299) {
                throw UpdateException(context.getString(R.string.update_download_failed, connection.responseCode))
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected) {
            file.delete()
            throw UpdateException(context.getString(R.string.update_bad_checksum))
        }
        verifyApk(file)
        file
    }

    /** Передаёт файл установщику Android; он сам попросит подтверждение (результат придёт в [InstallResultReceiver]). */
    suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("pary.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java).setAction(InstallResultReceiver.ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            session.commit(result.intentSender)
        }
    }

    private fun verifyApk(file: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = pm.getPackageArchiveInfo(file.path, flags)
            ?: throw UpdateException(context.getString(R.string.update_bad_file))
        if (archive.packageName != context.packageName) {
            throw UpdateException(context.getString(R.string.update_bad_file))
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            throw UpdateException(context.getString(R.string.update_not_newer))
        }
        val newCerts = certificateHashes(archive)
        val oldCerts = certificateHashes(installed)
        if (newCerts.isNotEmpty() && oldCerts.isNotEmpty() && newCerts != oldCerts) {
            throw UpdateException(context.getString(R.string.update_signature_mismatch))
        }
    }

    @Suppress("DEPRECATION")
    private fun certificateHashes(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.orEmpty().map { digest.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    private fun fetchText(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) {
                throw UpdateException(context.getString(R.string.update_no_checksum))
            }
            return connection.inputStream.use { it.readBytes().decodeToString().take(1000) }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "ParyGGTU-Updater")
        return connection
    }
}
