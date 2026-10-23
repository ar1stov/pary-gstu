package com.example.pary.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pary.R
import com.example.pary.data.GroupInfo
import com.example.pary.data.GroupSearchResult
import com.example.pary.ui.theme.pary
import com.example.pary.ui.theme.ParyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
fun GroupSelectScreen(
    search: suspend (String) -> GroupSearchResult,
    onGroupSelected: (GroupInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<GroupSearchResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    // Ищем через 300 мс после последнего символа, чтобы не слать запрос на каждую букву.
    LaunchedEffect(query, retry) {
        delay(300)
        isLoading = true
        hasError = false
        try {
            result = search(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            hasError = true
        }
        isLoading = false
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.university),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.select_group_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_group_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
        )
        val groups = result?.groups.orEmpty()
        when {
            hasError -> MessageState(
                text = stringResource(R.string.groups_load_error),
                action = { Button(onClick = { retry++ }) { Text(stringResource(R.string.retry)) } },
            )
            isLoading && groups.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            groups.isEmpty() -> MessageState(
                title = stringResource(R.string.group_not_found),
                text = stringResource(R.string.group_not_found_hint),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(groups, key = { it.slug }) { group -> GroupCard(group) { onGroupSelected(group) } }
                if (result?.hasMore == true) {
                    item {
                        Text(
                            text = stringResource(R.string.group_more),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(group: GroupInfo, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = group.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = stringResource(R.string.group_course_faculty, group.course, group.faculty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.pary.accent,
            )
        }
    }
}

@Composable
private fun MessageState(text: String, title: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun GroupSelectScreenPreview() {
    ParyTheme {
        GroupSelectScreen(
            search = {
                GroupSearchResult(
                    listOf(GroupInfo("ip-21", "ИП-21", 2, "Энергетический"), GroupInfo("ip-22", "ИП-22", 2, "Энергетический")),
                    hasMore = false,
                )
            },
            onGroupSelected = {},
        )
    }
}
