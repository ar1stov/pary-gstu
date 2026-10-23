package com.example.pary

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
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.data.ThemeMode
import com.example.pary.ui.GroupSelectScreen
import com.example.pary.ui.ScheduleScreen
import com.example.pary.ui.theme.ParyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val groups = GroupRepository(applicationContext)
        val schedules = ScheduleRepository(applicationContext)
        val settings = SettingsRepository(applicationContext)
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
                    AppContent(groups, schedules, settings, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppContent(
    groups: GroupRepository,
    schedules: ScheduleRepository,
    settings: SettingsRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // isLoaded нужен, чтобы не мигал экран выбора, пока группа читается с диска.
    var isLoaded by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf<GroupInfo?>(null) }

    LaunchedEffect(groups) {
        groups.selectedGroup.collect {
            group = it
            isLoaded = true
        }
    }

    if (!isLoaded) return

    val selected = group
    if (selected == null) {
        GroupSelectScreen(
            search = schedules::searchGroups,
            onGroupSelected = { scope.launch { groups.saveGroup(it) } },
            modifier = modifier,
        )
    } else {
        ScheduleScreen(
            group = selected,
            repository = schedules,
            onChangeGroup = { scope.launch { groups.clearGroup() } },
            onSubgroupChange = { scope.launch { groups.saveSubgroup(it) } },
            settingsRepository = settings,
        )
    }
}
