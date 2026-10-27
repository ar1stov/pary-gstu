package com.example.pary.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pary.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Что сейчас показывать пользователю в окне обновления. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data class Available(val info: UpdateInfo) : UpdateUiState

    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateUiState

    /** Нужно разрешить установку из этого приложения в системных настройках. */
    data class NeedPermission(val info: UpdateInfo) : UpdateUiState

    /** Файл передан установщику: ждём, пока пользователь подтвердит. */
    data class Installing(val info: UpdateInfo) : UpdateUiState

    data class Failed(val info: UpdateInfo, val message: String) : UpdateUiState
}

/** Проверка обновлений при запуске и сам процесс обновления. */
class UpdateController(private val context: Context, private val scope: CoroutineScope) {
    private val installer = UpdateInstaller(context)

    var state by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        private set

    private val info: UpdateInfo?
        get() = when (val s = state) {
            is UpdateUiState.Available -> s.info
            is UpdateUiState.Downloading -> s.info
            is UpdateUiState.NeedPermission -> s.info
            is UpdateUiState.Installing -> s.info
            is UpdateUiState.Failed -> s.info
            UpdateUiState.Idle -> null
        }

    /** Проверяет, не вышла ли новая версия; не чаще раза в полчаса, ошибки сети молча игнорирует. */
    suspend fun check() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now
        val found = try {
            UpdateChecker.check(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (found != null && state == UpdateUiState.Idle) state = UpdateUiState.Available(found)
    }

    /**
     * Проверка по кнопке в настройках, без ограничения «раз в полчаса».
     * true: найдена новая версия, false: установлена последняя, null: проверить не удалось.
     */
    suspend fun checkNow(): Boolean? {
        lastCheck = System.currentTimeMillis()
        val found = try {
            UpdateChecker.check(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        if (found == null) return false
        if (state == UpdateUiState.Idle) state = UpdateUiState.Available(found)
        return true
    }

    /** Нажали «Обновить»: скачиваем и передаём установщику. */
    fun start() {
        val target = info ?: return
        scope.launch {
            try {
                if (!installer.canInstall()) {
                    state = UpdateUiState.NeedPermission(target)
                    installer.openInstallPermissionSettings()
                    return@launch
                }
                state = UpdateUiState.Downloading(target, 0)
                val file = installer.download(target) { percent -> state = UpdateUiState.Downloading(target, percent) }
                installer.install(file)
                state = UpdateUiState.Installing(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                state = UpdateUiState.Failed(target, e.message.orEmpty())
            } catch (e: Exception) {
                state = UpdateUiState.Failed(target, context.getString(R.string.update_failed_generic))
            }
        }
    }

    /** Установщик Android сообщил об ошибке. */
    fun installFailed(reason: String) {
        val target = info ?: return
        val message = context.getString(R.string.update_install_failed) + if (reason.isNotBlank()) " ($reason)" else ""
        state = UpdateUiState.Failed(target, message)
    }

    /** Закрыли окно: плашка остаётся, пока приложение открыто. */
    fun reset() {
        info?.let { state = UpdateUiState.Available(it) }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000L

        /** Время последней проверки живёт, пока жив процесс: так не спрашиваем GitHub при каждом повороте экрана. */
        var lastCheck = 0L
    }
}
