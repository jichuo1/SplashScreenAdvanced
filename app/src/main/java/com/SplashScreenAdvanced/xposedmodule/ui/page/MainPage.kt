package com.SplashScreenAdvanced.xposedmodule.ui.page

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.LocalAppUiState
import com.SplashScreenAdvanced.xposedmodule.ui.component.TextPreference
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ModulePreferenceRes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ModuleStatusType
import com.SplashScreenAdvanced.xposedmodule.utils.execShell
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import dev.lackluster.hyperx.navigation.HyperXRoute
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.navigation.Navigator
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close2
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import dev.lackluster.hyperx.ui.theme.currentCardPressFeedback
import dev.lackluster.hyperx.ui.theme.currentCardShowIndication

/**
 * 主界面 Page
 */
@Composable
fun MainPage() {
    val navigator = LocalNavigator.current
    val dialogRestartVisibility = remember { mutableStateOf(false) }

    HyperXPage(
        title = stringResource(R.string.app_name),
        navigationIcon = { },
        actions = {
            IconButton(
                modifier = Modifier
                    .padding(end = 21.dp)
                    .size(40.dp),
                onClick = {
                    dialogRestartVisibility.value = true
                },
                holdDownState = dialogRestartVisibility.value
            ) {
                Icon(
                    imageVector = MiuixIcons.Close2,
                    contentDescription = "Dialog"
                )
            }
        }
    ) {
        item(key = "status") {
            TopCard()
        }
        itemPreferenceGroup(key = "basic") {
            ModuleSettingPreference(ModulePreferenceRes.BasicSettings, navigator)
        }
        itemPreferenceGroup(key = "features") {
            ModuleSettingPreference(ModulePreferenceRes.CustomScopeSettings, navigator)
            ModuleSettingPreference(ModulePreferenceRes.IconSettings, navigator)
            ModuleSettingPreference(ModulePreferenceRes.BottomSettings, navigator)
            ModuleSettingPreference(ModulePreferenceRes.BackgroundSettings, navigator)
            ModuleSettingPreference(ModulePreferenceRes.DisplaySettings, navigator)
            if (LocalAppUiState.current.devMode) {
                ModuleSettingPreference(ModulePreferenceRes.DevSettings, navigator)
            }
        }
        itemPreferenceGroup(key = "about") {
            ModuleSettingPreference(ModulePreferenceRes.About, navigator)
        }
        item(key = "hint") {
            Text(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                text = stringResource(R.string.main_activity_hint),
                fontSize = MiuixTheme.textStyles.subtitle.fontSize,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }

    RestartDialog(dialogRestartVisibility)
}

/**
 * 首页顶部状态卡片
 */
@Composable
private fun TopCard() {
    val uiState = LocalAppUiState.current
    val moduleStatusTypeRes = getModuleStatusType(
        moduleActive = uiState.moduleActive,
        androidRestartNeeded = uiState.androidRestartNeeded,
        systemUIRestartNeeded = uiState.systemUIRestartNeeded
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp, top = 12.dp),
        colors = CardDefaults.defaultColors(colorResource(moduleStatusTypeRes.cardBackground)),
        pressFeedbackType = currentCardPressFeedback(),
        showIndication = currentCardShowIndication(),
        onLongPress = {
            // todo: 目前 execShell 并没有能力判断命令执行成功与否, 在未获取到 root 时, 卡片仍为可点击状态但没有任何提示
            if (moduleStatusTypeRes != ModuleStatusType.INACTIVE) {
                execShell("am broadcast -a android.telephony.action.SECRET_CODE -d android_secret_code://5776733 android")
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                modifier = Modifier
                    .padding(16.dp)
                    .size(28.dp),
                painter = painterResource(moduleStatusTypeRes.stateIconRes),
                colorFilter = ColorFilter.tint(color = MiuixTheme.colorScheme.onBackground),
                contentDescription = null
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 16.dp)
            ) {
                Text(
                    modifier = Modifier.padding(top = 16.dp),
                    text = stringResource(moduleStatusTypeRes.stateTextRes),
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onBackground
                )
                Text(
                    modifier = Modifier.padding(vertical = 2.dp),
                    text = stringResource(R.string.module_version, BuildConfig.VERSION_NAME),
                    fontSize = MiuixTheme.textStyles.body1.fontSize,
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
                Crossfade(uiState.moduleActive, label = "moduleActive") { isActive ->
                    if (isActive) {
                        Text(
                            modifier = Modifier.padding(bottom = 16.dp),
                            text = stringResource(
                                R.string.xposed_framework_version,
                                uiState.xposedFrameworkName,
                                uiState.xposedApiVersion
                            ),
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RestartDialog(
    show: MutableState<Boolean>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /**
     * 执行 su 命令, 稍等片刻后如果本进程还活着, 说明没拿到 root, 提示用户
     *
     * 原先是在 onClick 里直接 [execShell] + `Thread.sleep(300)`: 前者要 fork `su` 进程并写管道,
     * 后者是硬阻塞, 两者都跑在主线程上, 点一下就是数百毫秒的卡顿
     */
    fun runRestartCommand(command: String) {
        scope.launch {
            withContext(Dispatchers.IO) { execShell(command) }
            delay(300)
            context.toast(R.string.no_root)
        }
    }

    OverlayDialog(
        title = stringResource(R.string.restart_title),
        summary = stringResource(R.string.restart_message),
        show = show.value,
        onDismissRequest = { show.value = false },
    ) {
        Column {
            // 重启手机 按钮
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.reboot),
                onClick = { runRestartCommand("reboot") }
            )
            Spacer(Modifier.height(12.dp))

            // 重启系统界面 按钮
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.restart_system_ui),
                onClick = {
                    runRestartCommand("pkill -f com.android.systemui && pkill -f com.SplashScreenAdvanced.xposedmodule")
                }
            )
            Spacer(Modifier.height(12.dp))

            // 取消 按钮
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                text = stringResource(R.string.button_cancel),
                onClick = {
                    show.value = false
                }
            )
        }
    }
}

/**
 * 根据 [ModulePreferenceRes] 提供的资源来创建模块首页设置项
 *
 * @param modulePreferenceRes 模块偏好配置资源，包含图标资源、标题资源和导航目标
 * @param navigator 导航控制器，用于执行页面跳转。如果为 null，则不会触发导航
 * @param onClick 点击事件的回调函数。如果不为 null，则会在点击时执行此回调
 */
@Composable
fun ModuleSettingPreference(
    modulePreferenceRes: ModulePreferenceRes,
    navigator: Navigator? = null,
    onClick: (() -> Unit)? = null
) {
    TextPreference(
        icon = ImageIcon(resId = modulePreferenceRes.iconRes),
        title = stringResource(modulePreferenceRes.stringRes),
        ignoreModuleActiveStatus = true
    ) {
        onClick?.invoke()
        modulePreferenceRes.navigateTo?.let { route ->
            navigator?.popUntil { it is HyperXRoute.Main }
            navigator?.push(route)
        }
    }
}

/**
 * 根据提供的条件确定模块现实的激活状态
 *
 * @param moduleActive 一个布尔值，表示模块是否处于激活状态
 * @param androidRestartNeeded 一个可空的布尔值，表示是否需要 Android 重启, 空为未获取到数据
 * @param systemUIRestartNeeded 一个可空的布尔值，表示是否需要系统 UI 重启, 空为未获取到数据
 * @return 返回表示当前模块状态的 [ModuleStatusType]
 */
private fun getModuleStatusType(
    moduleActive: Boolean,
    androidRestartNeeded: Boolean?,
    systemUIRestartNeeded: Boolean?
): ModuleStatusType = when {
    moduleActive && androidRestartNeeded == true -> ModuleStatusType.ACTIVE_ANDROID_RESTART
    moduleActive && systemUIRestartNeeded == true -> ModuleStatusType.ACTIVE_SYSTEM_UI_RESTART
    moduleActive -> ModuleStatusType.ACTIVE_NO_NEED_RESTART
    else -> ModuleStatusType.INACTIVE
}
