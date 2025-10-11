package com.gswxxn.restoresplashscreen.ui.apppage

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.gswxxn.restoresplashscreen.R
import com.gswxxn.restoresplashscreen.data.DataConst
import com.gswxxn.restoresplashscreen.data.Pages
import com.gswxxn.restoresplashscreen.ui.MainActivity
import com.gswxxn.restoresplashscreen.ui.component.MyAppInfo
import com.gswxxn.restoresplashscreen.ui.component.SpliceCard
import com.gswxxn.restoresplashscreen.ui.component.TextPreference
import com.gswxxn.restoresplashscreen.utils.CommonUtils.toMap
import com.highcapable.yukihookapi.hook.factory.prefs
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.lackluster.hyperx.compose.base.BasePageDefaults
import dev.lackluster.hyperx.compose.base.HazeScaffold
import dev.lackluster.hyperx.compose.base.IconSize
import dev.lackluster.hyperx.compose.base.ImageIcon
import dev.lackluster.hyperx.compose.navigation.navigateTo
import dev.lackluster.hyperx.compose.preference.PreferenceGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.getWindowSize
import top.yukonga.miuix.kmp.utils.overScrollVertical

/**
 * 单独配置背景颜色
 */
@Composable
fun BgIndividualPage(
    navController: NavController,
    adjustPadding: PaddingValues,
    mode: BasePageDefaults.Mode,
    blurEnabled: MutableState<Boolean> = MainActivity.blurEnabled
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    var queryString by remember { mutableStateOf("") }

    // 完整应用列表
    var appInfoList by remember { mutableStateOf<List<MyAppInfo>>(emptyList()) }

    // 在列表中的条目
    var appInfoFilter by remember { mutableStateOf<List<MyAppInfo>>(emptyList()) }

    var queryJob: Job? = null
    var isLoading by remember { mutableStateOf(true) }
    val deviceDarkMode = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        launch {
            isLoading = true
            delay(500)
            val configMapPrefs =
                if (deviceDarkMode) DataConst.INDIVIDUAL_BG_COLOR_APP_MAP_DARK else DataConst.INDIVIDUAL_BG_COLOR_APP_MAP
            val tmpCheckedList = mutableMapOf<String, String>().apply {
                clear()
                putAll(context.prefs().get(configMapPrefs).toMap())
            }
            val pm = context.packageManager
            appInfoList = pm.getInstalledApplications(0).map {
                MyAppInfo(
                    it.loadLabel(pm).toString(),
                    it.packageName,
                    it.loadIcon(pm),
                    mutableStateOf(it.packageName in tmpCheckedList.keys),
                    it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                )
            }.toList()
            isLoading = false
        }
    }

    LaunchedEffect(appInfoList, queryString) {
        if (appInfoList.isEmpty()) return@LaunchedEffect
        appInfoFilter = emptyList()
        queryJob?.cancel()
        queryJob = launch {
            if (queryString.isBlank()) {
                delay(100)
                appInfoFilter = appInfoList.toMutableList().apply {
                    sortBy { it.appName }
                    sortByDescending { it.isChecked.value }
                }
            } else {
                delay(300)
                appInfoFilter = appInfoList.filter {
                    it.appName.contains(queryString, true) or it.packageName.contains(queryString, true)
                }.toMutableList().apply {
                    sortBy { it.appName }
                    sortByDescending { it.isChecked.value }
                }
            }
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    val systemBarInsets =
        WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal).asPaddingValues()
    val navigationIconPadding = PaddingValues.Absolute(
        left = if (mode != BasePageDefaults.Mode.SPLIT_RIGHT) systemBarInsets.calculateLeftPadding(layoutDirection) else 0.dp
    )

    HazeScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { contentPadding ->
            TopAppBar(
                color = if (blurEnabled.value) Color.Transparent else MiuixTheme.colorScheme.background,
                title = stringResource(R.string.configure_bg_colors_individually),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier
                            .padding(navigationIconPadding)
                            .padding(start = 21.dp)
                            .size(40.dp),
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(26.dp),
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                },
                defaultWindowInsetsPadding = false,
                horizontalPadding = 28.dp + contentPadding.calculateLeftPadding(LocalLayoutDirection.current)
            )
        },
        blurTopBar = blurEnabled.value,
        blurBottomBar = blurEnabled.value,
        hazeStyle = HazeStyle(
            blurRadius = 25.dp,
            noiseFactor = 0f,
            backgroundColor = MiuixTheme.colorScheme.background,
            tint = HazeTint(MiuixTheme.colorScheme.background.copy(0.67f))
        ),
        adjustPadding = adjustPadding,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .height(getWindowSize().height.dp)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = paddingValues,
            overscrollEffect = null
        ) {
            item {
                SearchBar(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp),
                    inputField = {
                        InputField(
                            query = queryString,
                            onQueryChange = { queryString = it },
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { },
                            label = stringResource(R.string.search_hint)
                        )
                    },
                    expanded = false,
                    onExpandedChange = { },
                    content = { }
                )
            }
            item {
                PreferenceGroup {
                    BasicComponent(
                        insideMargin = PaddingValues(16.dp),
                        summary = stringResource(R.string.custom_bg_color_sub_setting_hint),
                        summaryColor = BasicComponentDefaults.titleColor(),
                        leftAction = {
                            Image(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp),
                                imageVector = MiuixIcons.Useful.Info,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                            )
                        }
                    )
                }
            }
            if (isLoading) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.loading),
                        modifier = Modifier.padding(top = 6.dp),
                        textColor = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            } else {
                itemsIndexed(appInfoFilter, key = { index, item ->
                    item.packageName + item.isChecked + index + appInfoFilter.size
                }) { index, item ->
                    val topCornerRadius = if (index == 0) CardDefaults.CornerRadius else 0.dp
                    val bottomCornerRadius = if (index == appInfoFilter.size - 1) CardDefaults.CornerRadius else 0.dp
                    SpliceCard(
                        topCornerRadius,
                        bottomCornerRadius
                    ) {
                        TextPreference(
                            icon = ImageIcon(
                                iconBitmap = item.icon.toBitmap().asImageBitmap(),
                                iconSize = IconSize.App
                            ),
                            title = item.appName,
                            summary = item.packageName
                        ) {
                            navController.navigateTo(
                                "${Pages.CONFIG_COLOR_PICKER}?PkgName=${item.packageName}"
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}