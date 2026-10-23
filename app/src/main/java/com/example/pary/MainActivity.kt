package com.example.pary

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.pary.data.GroupInfo
import com.example.pary.data.GroupRepository
import com.example.pary.data.ScheduleRepository
import com.example.pary.data.SettingsRepository
import com.example.pary.ui.GroupSelectScreen
import com.example.pary.ui.ScheduleScreen
import com.example.pary.ui.theme.ParyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val groups = GroupRepository(applicationContext)
        val schedules = ScheduleRepository(applicationContext)
        val settings = SettingsRepository(applicationContext)
        setContent {
            ParyTheme {
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
