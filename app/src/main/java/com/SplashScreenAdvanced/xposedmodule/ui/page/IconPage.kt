package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.ui.component.DropDownPreference
import com.SplashScreenAdvanced.xposedmodule.ui.component.HeaderCard
import com.SplashScreenAdvanced.xposedmodule.ui.component.SwitchPreference
import com.SplashScreenAdvanced.xposedmodule.ui.component.TextPreference
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ShrinkIconType
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils
import com.SplashScreenAdvanced.xposedmodule.utils.IconPackManager
import com.SplashScreenAdvanced.xposedmodule.utils.sr.IconCacheStore
import com.SplashScreenAdvanced.xposedmodule.utils.sr.IconScanService
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.navigation.Navigator
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.preference.DropDownEntry
import dev.lackluster.hyperx.ui.preference.ItemPosition
import dev.lackluster.hyperx.ui.preference.core.rememberPreferenceState
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * 图标 界面
 */
@Composable
fun IconPage() {
    val navigator = LocalNavigator.current
    HyperXPage(
        title = stringResource(R.string.icon_settings),
    ) {
        item(key = "header") {
            HeaderCard(imageResID = R.drawable.demo_icon, title = "ICON")
        }
        itemPreferenceGroup(key = "common") {
            CommonSettingsGroup()
        }
        itemPreferenceGroup(key = "default-icon") {
            DefaultIconSettingsGroup(navigator)
        }
        itemPreferenceGroup(key = "hide-icon", position = ItemPosition.Last) {
            HideSplashIconSettingsGroup(navigator)
        }
    }
}

/**
 * 通用设置
 */
@Composable
private fun CommonSettingsGroup() {
    val context = LocalContext.current
    val repo = koinInject<GlobalPreferencesRepository>()

    // 图标包列表预处理
    val selectedIconPackIndex = remember { mutableIntStateOf(0) }
    var availableIconPackItems by remember {
        mutableStateOf(listOf(DropDownEntry(value = 0, title = "None", summary = "None")))
    }
    LaunchedEffect(Unit) {
        val packs = withContext(Dispatchers.IO) {
            IconPackManager(context).getAvailableIconPacks()
                .filter { it.key != "None" }
                .toList()
                .mapIndexed { index, (packageName, iconPackName) ->
                    DropDownEntry(value = index + 1, title = iconPackName, summary = packageName)
                }
        }
        availableIconPackItems = listOf(DropDownEntry(value = 0, title = "None", summary = "None")) + packs
        selectedIconPackIndex.intValue = availableIconPackItems.indexOfFirst {
            it.summary == repo.get(Preferences.Icon.ICON_PACK_PACKAGE_NAME)
        }.takeIf { it != -1 } ?: run {
            repo.update(Preferences.Icon.ICON_PACK_PACKAGE_NAME, "None")
            context.toast(R.string.icon_pack_is_removed)
            0
        }
    }
    // 绘制图标圆角
    SwitchPreference(
        title = stringResource(R.string.draw_round_corner),
        key = Preferences.Display.ENABLE_DRAW_ROUND_CORNER
    )
    // 缩小图标
    val shrinkIcon = rememberPreferenceState(Preferences.Icon.SHRINK_ICON)
    DropDownPreference(
        title = stringResource(R.string.shrink_icon),
        entries = ShrinkIconType.entries.mapIndexed { index, type -> DropDownEntry(value = index, title = stringResource(type.stringID)) },
        selectedIndex = shrinkIcon
    )
    AnimatedVisibility(
        visible = shrinkIcon.value != ShrinkIconType.NotShrinkIcon.ordinal,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        // 为缩小的图标添加模糊背景
        SwitchPreference(
            title = stringResource(R.string.add_icon_blur_bg),
            key = Preferences.Icon.ENABLE_ADD_ICON_BLUR_BG
        )
    }
    // 替换图标获取方式
    SwitchPreference(
        title = stringResource(R.string.replace_icon),
        summary = stringResource(R.string.replace_icon_tips),
        key = Preferences.Icon.ENABLE_REPLACE_ICON
    )
    // 图标画质增强
    //
    // 用档位而非开关: DropDownEntry 的下标语义与 Preferences.Icon.ENHANCE_LEVEL 的取值一一对应,
    // 取值会被 IconEnhanceEngine 直接当作处理强度(档位越高重采样与锐化越强)。
    val enhanceLevel = rememberPreferenceState(Preferences.Icon.ENHANCE_LEVEL)
    DropDownPreference(
        title = stringResource(R.string.icon_enhance),
        summary = stringResource(R.string.icon_enhance_tips),
        entries = listOf(
            DropDownEntry(value = 0, title = stringResource(R.string.icon_enhance_off)),
            DropDownEntry(value = 1, title = stringResource(R.string.icon_enhance_standard)),
            DropDownEntry(value = 2, title = stringResource(R.string.icon_enhance_high)),
            DropDownEntry(value = 3, title = stringResource(R.string.icon_enhance_ultra))
        ),
        selectedIndex = enhanceLevel
    )
    // 硬件加速仅在增强开启时有意义; 与档位选择器同进出
    AnimatedVisibility(
        visible = enhanceLevel.value != 0,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        SwitchPreference(
            title = stringResource(R.string.icon_enhance_gpu),
            summary = stringResource(R.string.icon_enhance_gpu_tips),
            key = Preferences.Icon.ENHANCE_GPU
        )
    }
    // 离线超分工厂: 手动触发一次批量预处理, 产物经 ContentProvider 供 SystemUI 侧复用
    val scanState = remember { mutableStateOf("") }
    // 轮询刷新统计行: 扫描进行中条目数与体积都在变, 页面停留期间每 2s 重读一次
    // (读的是进程内索引缓存 + 一次目录遍历, 开销可忽略)
    LaunchedEffect(Unit) {
        while (true) {
            scanState.value = withContext(Dispatchers.IO) {
                val index = IconCacheStore.readIndex(context)
                val mb = IconCacheStore.totalBytes(context) / 1024f / 1024f
                context.getString(R.string.icon_cache_stats, index.entries.size, mb)
            }
            delay(2000)
        }
    }
    // POST_NOTIFICATIONS 仅声明不会自动授予: 被拒时扫描照常跑, 但进度/取消通知不显示
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) context.toast(R.string.sr_scan_notification_denied)
        IconScanService.start(context)
    }
    TextPreference(
        title = stringResource(R.string.sr_factory),
        summary = stringResource(R.string.sr_factory_tips) + "\n" + scanState.value,
        onClick = {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                IconScanService.start(context)
            } else {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    )

    if (DeviceUtils.isHyperOS) {
        // 移除小米图标描边
        SwitchPreference(
            title = stringResource(R.string.remove_icon_stroke),
            key = Preferences.Icon.ENABLE_REMOVE_ICON_STROKE
        )
        // 使用小米大图标
        SwitchPreference(
            title = stringResource(R.string.use_miui_large_icon),
            key = Preferences.Icon.ENABLE_USE_MIUI_LARGE_ICON
        )
    }

    // 使用图标包
    DropDownPreference(
        title = stringResource(R.string.use_icon_pack),
        entries = availableIconPackItems,
        selectedIndex = selectedIconPackIndex
    ) {
        repo.update(
            Preferences.Icon.ICON_PACK_PACKAGE_NAME,
            availableIconPackItems[it].summary ?: "None"
        )
    }
}

/**
 * 忽略应用主动设置的图标
 */
@Composable
private fun DefaultIconSettingsGroup(navigator: Navigator) {
    val context = LocalContext.current
    val ignoreAppIcon = rememberPreferenceState(Preferences.Icon.ENABLE_DEFAULT_STYLE)
    SwitchPreference(
        title = stringResource(R.string.default_style),
        summary = stringResource(R.string.default_style_tips),
        checked = ignoreAppIcon
    ) { newValue ->
        if (newValue) {
            context.toast(R.string.custom_scope_message)
        }
    }
    AnimatedVisibility(
        visible = ignoreAppIcon.value,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        TextPreference(title = stringResource(R.string.default_style_list)) {
            navigator.push(Route.IgnoreAppIcon)
        }
    }
}

/**
 * 不显示图标
 */
@Composable
private fun HideSplashIconSettingsGroup(navigator: Navigator) {
    val context = LocalContext.current
    val hideSplashIcon = rememberPreferenceState(Preferences.Icon.ENABLE_HIDE_SPLASH_SCREEN_ICON)
    SwitchPreference(
        title = stringResource(R.string.hide_splash_screen_icon),
        checked = hideSplashIcon
    ) { newValue ->
        if (newValue) {
            context.toast(R.string.custom_scope_message)
        }
    }
    AnimatedVisibility(
        visible = hideSplashIcon.value,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        TextPreference(
            title = stringResource(R.string.default_style_list),
            onClick = { navigator.push(Route.HideIcon) }
        )
    }
}
