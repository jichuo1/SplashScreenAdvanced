package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.ui.component.HeaderCard
import com.SplashScreenAdvanced.xposedmodule.ui.component.DropDownPreference
import com.SplashScreenAdvanced.xposedmodule.ui.component.SwitchPreference
import com.SplashScreenAdvanced.xposedmodule.ui.component.TextPreference
import com.SplashScreenAdvanced.xposedmodule.utils.BackupUtils
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.layout.LocalHyperXLayoutConfig
import dev.lackluster.hyperx.ui.preference.DropDownEntry
import dev.lackluster.hyperx.ui.preference.ItemPosition
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup
import dev.lackluster.hyperx.ui.theme.UiStyle
import org.koin.compose.koinInject
import java.time.LocalDateTime

/**
 * 基础设置 界面
 */
@Composable
fun BasicPage() {
    HyperXPage(
        title = stringResource(R.string.basic_settings),
    ) {
        item(key = "header") {
            HeaderCard(imageResID = R.drawable.demo_basic, title = "BASIC")
        }
        itemPreferenceGroup(key = "module") {
            ModuleAppSettings()
        }
        itemPreferenceGroup(
            titleRes = R.string.backup_restore_title,
            position = ItemPosition.Last
        ) {
            BackupAndRestore()
        }
    }
}

/**
 * 模块 App 相关设置项
 */
@Composable
private fun ModuleAppSettings() {
    val context = LocalContext.current
    val repo = koinInject<GlobalPreferencesRepository>()
    val enableLog = rememberPreferenceState(Preferences.Log.ENABLE_LOG)
    val uiConfig = LocalHyperXLayoutConfig.current
    val miuixChrome = uiConfig.uiStyle.isMiuix
    val miuixStyleTitle = stringResource(R.string.ui_style_miuix)
    val materialYouStyleTitle = stringResource(R.string.ui_style_material_you)
    val uiStyleEntries = remember(miuixStyleTitle, materialYouStyleTitle) {
        listOf(
            DropDownEntry(UiStyle.Miuix.prefValue, miuixStyleTitle),
            DropDownEntry(UiStyle.MaterialYou.prefValue, materialYouStyleTitle),
        )
    }

    LaunchedEffect(Unit) {
        if (enableLog.value && (System.currentTimeMillis() - repo.get(Preferences.Log.ENABLE_LOG_TIMESTAMP)) > 86400000) {
            enableLog.value = false
        }
    }

    // 启用日志
    SwitchPreference(
        title = stringResource(R.string.enable_log),
        summary = stringResource(R.string.enable_log_tips),
        checked = enableLog
    ) {
        if (it) {
            repo.update(Preferences.Log.ENABLE_LOG_TIMESTAMP, System.currentTimeMillis())
        }
    }
    // 隐藏桌面图标
    SwitchPreference(
        title = stringResource(R.string.hide_icon),
        key = Preferences.Icon.ENABLE_HIDE_ICON
    ) {
        val newState = if (it) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, "${BuildConfig.APPLICATION_ID}.Home"),
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
    // 界面风格：MIUI / Material You。此项只影响模块 App 外观，未激活也可切换。
    DropDownPreference(
        title = stringResource(R.string.ui_style),
        summary = stringResource(R.string.ui_style_tips),
        entries = uiStyleEntries,
        key = Preferences.Module.UI_STYLE,
        ignoreModuleActiveStatus = true,
    )
    // 模糊效果。
    // 这两项不需要 onCheckedChange 再手动同步一份状态: key 的写入会经
    // GlobalPreferencesRepository.update 落到 uiConfigFlow, 各页面统一从
    // LocalHyperXLayoutConfig 读取
    SwitchPreference(
        title = stringResource(R.string.blur),
        summary = if (miuixChrome) null else stringResource(R.string.blur_miuix_only),
        key = Preferences.Module.MODULE_APP_BLUR,
        enabled = miuixChrome,
    )
    // 自适应布局
    SwitchPreference(
        title = stringResource(R.string.split_view),
        summary = stringResource(R.string.split_view_tips),
        key = Preferences.Module.SPLIT_VIEW
    )
}

/**
 * 备份与恢复设置
 */
@Composable
private fun BackupAndRestore() {
    val context = LocalContext.current

    // todo: 待测试
    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { BackupUtils.handleCreateDocument(context, it) }
    )
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { BackupUtils.handleReadDocument(context, it) }
    )

    // 备份设置项
    TextPreference(title = stringResource(R.string.backup)) {
        backupLauncher.launch("SplashScreenAdvanced_${LocalDateTime.now()}.json")
    }
    // 恢复设置项
    TextPreference(title = stringResource(R.string.restore)) {
        restoreLauncher.launch(arrayOf("application/json"))
    }
}
