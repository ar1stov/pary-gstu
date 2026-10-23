package com.example.pary.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Что сообщил установщик Android. */
sealed interface InstallEvent {
    /** Нужно подтверждение пользователя: [intent] открывает системное окно установки. */
    class Confirm(val intent: Intent) : InstallEvent

    class Failure(val message: String) : InstallEvent

    data object Success : InstallEvent
}

/** Мост между приёмником и экраном: экран открывает окно подтверждения и показывает ошибки. */
@OptIn(ExperimentalCoroutinesApi::class)
object InstallEvents {
    private val mutableEvents = MutableSharedFlow<InstallEvent>(replay = 1, extraBufferCapacity = 4)
    val events: SharedFlow<InstallEvent> = mutableEvents.asSharedFlow()

    fun emit(event: InstallEvent) {
        mutableEvents.tryEmit(event)
    }

    /** Событие обработано: убираем его, чтобы оно не повторилось при следующем открытии экрана. */
    fun consumed() = mutableEvents.resetReplayCache()
}

/** Получает результат установки от PackageInstaller. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) InstallEvents.emit(InstallEvent.Confirm(confirm))
            }
            PackageInstaller.STATUS_SUCCESS -> InstallEvents.emit(InstallEvent.Success)
            else -> InstallEvents.emit(
                InstallEvent.Failure(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()),
            )
        }
    }

    companion object {
        const val ACTION = "com.example.pary.INSTALL_RESULT"
    }
}
