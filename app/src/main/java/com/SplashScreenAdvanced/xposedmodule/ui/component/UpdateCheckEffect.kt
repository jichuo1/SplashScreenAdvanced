package com.SplashScreenAdvanced.xposedmodule.ui.component

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker
import com.SplashScreenAdvanced.xposedmodule.utils.update.UpdateCheckManager
import dev.lackluster.hyperx.ui.dialog.AlertDialog
import dev.lackluster.hyperx.ui.dialog.AlertDialogMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_DIALOG_NOTES_LENGTH = 2_000

/**
 * 更新检查结果的全局渲染层：挂载在 MainActivity 的 AppContent 根部，
 * 无论用户停留在哪个页面都能收到自动/手动检查的结果。
 *
 * - [UpdateCheckManager.UpdateCheckState.NewVersion] → 对话框
 * - Latest / Failed → Toast（仅手动检查会投递这两种状态）
 */
@Composable
fun UpdateCheckEffect() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by UpdateCheckManager.state.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            UpdateCheckManager.UpdateCheckState.Latest -> {
                context.toast(R.string.update_latest)
                UpdateCheckManager.dismiss()
            }
            UpdateCheckManager.UpdateCheckState.Failed -> {
                context.toast(R.string.update_check_failed)
                UpdateCheckManager.dismiss()
            }
            else -> Unit
        }
    }

    val newVersion = state as? UpdateCheckManager.UpdateCheckState.NewVersion ?: return
    val release = newVersion.release
    AlertDialog(
        visible = true,
        onDismissRequest = { UpdateCheckManager.dismiss() },
        title = stringResource(
            R.string.update_new_version,
            release.displayName + if (release.prerelease) {
                " " + stringResource(R.string.update_prerelease_badge)
            } else ""
        ),
        message = release.releaseNotes
            .take(MAX_DIALOG_NOTES_LENGTH)
            .let { notes ->
                if (release.releaseNotesTruncated ||
                    release.releaseNotes.length > MAX_DIALOG_NOTES_LENGTH
                ) {
                    notes.trimEnd() + "\n\n" + stringResource(R.string.update_notes_truncated)
                } else notes.ifEmpty { null }
            },
        mode = AlertDialogMode.NegativeAndPositive,
        negativeText = stringResource(R.string.button_cancel),
        positiveText = stringResource(R.string.update_view_release),
        onPositiveButton = {
            UpdateCheckManager.dismiss()
            openReleasePage(scope, context, release.htmlUrl)
        },
        onNegativeButton = { UpdateCheckManager.dismiss() }
    )
}

/** 探测官方 Release 页可达性，不可达时回退 kkgithub 镜像页，再用系统浏览器打开。 */
private fun openReleasePage(scope: CoroutineScope, context: Context, releaseUrl: String) {
    scope.launch {
        val target = runCatching {
            withContext(Dispatchers.IO) {
                GitHubReleaseChecker.resolveReleaseDetailsDestination(releaseUrl).url
            }
        }.getOrDefault(releaseUrl)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
        } catch (_: Exception) {
            context.toast(R.string.no_browser)
        }
    }
}
