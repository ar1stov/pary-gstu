package com.example.pary

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.pary.data.AppSettings
import com.example.pary.data.GroupInfo
import com.example.pary.data.GroupRepository
import com.example.pary.data.NotesRepository
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.data.ThemeMode
import com.example.pary.notify.ScheduleSync
import com.example.pary.ui.GroupSelectScreen
import com.example.pary.ui.ScheduleScreen
import com.example.pary.ui.theme.ParyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Какой день открыть по ярлыку на иконке: 0 — сегодня, 1 — завтра; null — ничего не просили. */
    private var openDayRequest by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ScheduleSync.enqueue(applicationContext)
        readShortcut(intent)
        val groups = GroupRepository(applicationContext)
        val schedules = ScheduleRepository(applicationContext)
        val settings = SettingsRepository(applicationContext)
        val notes = NotesRepository(applicationContext)
        setContent {
            val appSettings by settings.settings.collectAsState(initial = AppSettings())
            val dark = when (appSettings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Значки в строке состояния и панели навигации: светлые на тёмном фоне и тёмные на светлом.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            ParyTheme(darkTheme = dark) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppContent(
                        groups = groups,
                        schedules = schedules,
                        settings = settings,
                        notes = notes,
                        openDayRequest = openDayRequest,
                        onOpenDayHandled = { openDayRequest = null },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readShortcut(intent)
    }

    private fun readShortcut(intent: Intent?) {
        if (intent?.action != "com.example.pary.OPEN_DAY") return
        openDayRequest = intent.getStringExtra("day_offset")?.toIntOrNull()
    }
}

@Composable
fun AppContent(
    groups: GroupRepository,
    schedules: ScheduleRepository,
    settings: SettingsRepository,
    notes: NotesRepository,
    openDayRequest: Int?,
    onOpenDayHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // isLoaded нужен, чтобы не мигал экран выбора, пока группа читается с диска.
    var isLoaded by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf<GroupInfo?>(null) }
    val recentGroups by groups.recentGroups.collectAsState(initial = emptyList())

    LaunchedEffect(groups) {
        groups.selectedGroup.collect {
            group = it
            isLoaded = true
        }
    }

    if (!isLoaded) return

    val selected = group
    if (selected == null) {
        // Приложение только что установлено: «Что нового» ему показывать не нужно.
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) {
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            settings.setLastSeenVersion(version)
        }
        GroupSelectScreen(
            search = schedules::searchGroups,
            onGroupSelected = { scope.launch { groups.saveGroup(it) } },
            modifier = modifier,
        )
    } else {
        ScheduleScreen(
            group = selected,
            repository = schedules,
            recentGroups = recentGroups,
            onChangeGroup = { scope.launch { groups.clearGroup() } },
            onSwitchGroup = { scope.launch { groups.saveGroup(it) } },
            onSubgroupChange = { scope.launch { groups.saveSubgroup(it) } },
            settingsRepository = settings,
            notesRepository = notes,
            openDayRequest = openDayRequest,
            onOpenDayHandled = onOpenDayHandled,
        )
    }
}
