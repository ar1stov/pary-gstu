package com.example.pary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pary.R
import com.example.pary.ui.theme.pary
import com.example.pary.update.UpdateInfo
import com.example.pary.update.UpdateUiState

private fun UpdateUiState.info(): UpdateInfo? = when (this) {
    is UpdateUiState.Available -> info
    is UpdateUiState.Downloading -> info
    is UpdateUiState.NeedPermission -> info
    is UpdateUiState.Installing -> info
    is UpdateUiState.Failed -> info
    UpdateUiState.Idle -> null
}

/** Плашка над списком пар: сообщает о новой версии и ходе обновления. */
@Composable
fun UpdateBanner(state: UpdateUiState, onClick: () -> Unit) {
    val version = state.info()?.versionName ?: return
    val text = when (state) {
        is UpdateUiState.Available -> stringResource(R.string.update_banner_available, version)
        is UpdateUiState.Downloading -> stringResource(R.string.update_banner_downloading, version, state.percent)
        is UpdateUiState.NeedPermission -> stringResource(R.string.update_banner_permission, version)
        is UpdateUiState.Installing -> stringResource(R.string.update_banner_installing, version)
        is UpdateUiState.Failed -> stringResource(R.string.update_banner_failed, version)
        UpdateUiState.Idle -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.pary.practice,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.pary.practice.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(12.dp),
    )
}

/** Окно обновления: что делать в каждом состоянии. */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val info = state.info() ?: return
    val uriHandler = LocalUriHandler.current

    val body: @Composable () -> Unit = {
        when (state) {
            is UpdateUiState.Available -> Text(stringResource(R.string.update_dialog_available))
            is UpdateUiState.Downloading -> {
                Text(stringResource(R.string.update_dialog_downloading, state.percent))
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            is UpdateUiState.NeedPermission -> Text(stringResource(R.string.update_dialog_permission))
            is UpdateUiState.Installing -> Text(stringResource(R.string.update_dialog_installing))
            is UpdateUiState.Failed -> Text(state.message)
            UpdateUiState.Idle -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_dialog_title, info.versionName)) },
        text = body,
        confirmButton = {
            when (state) {
                is UpdateUiState.Available -> TextButton(onClick = onStart) { Text(stringResource(R.string.update_action)) }
                is UpdateUiState.NeedPermission -> TextButton(onClick = onStart) { Text(stringResource(R.string.update_continue)) }
                is UpdateUiState.Failed -> TextButton(onClick = onStart) { Text(stringResource(R.string.retry)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_hide)) }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateUiState.Available, is UpdateUiState.Failed ->
                    TextButton(onClick = { uriHandler.openUri(info.releaseUrl) }) { Text(stringResource(R.string.update_whats_new)) }
                else -> Unit
            }
        },
    )
}
