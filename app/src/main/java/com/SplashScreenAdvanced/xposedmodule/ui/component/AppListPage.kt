package com.SplashScreenAdvanced.xposedmodule.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.utils.notEqualsTo
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import dev.lackluster.hyperx.core.utils.HanziToPinyin
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.ui.preference.core.PreferenceKey
import org.koin.compose.koinInject
import dev.lackluster.hyperx.ui.dialog.AlertDialog
import dev.lackluster.hyperx.ui.dialog.AlertDialogMode
import dev.lackluster.hyperx.ui.layout.HyperXScaffold
import dev.lackluster.hyperx.ui.layout.LocalHyperXLayoutConfig
import dev.lackluster.hyperx.ui.layout.LocalLayoutPadding
import dev.lackluster.hyperx.ui.preference.PreferenceGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import dev.lackluster.hyperx.ui.theme.hyperXOverScrollVertical
import dev.lackluster.hyperx.ui.theme.hyperXScrollEndHaptic
import dev.lackluster.hyperx.ui.theme.rememberHyperXListOverscrollEffect

@Composable
fun AppListPage(
    title: String,
    checkedListKey: PreferenceKey<Set<String>>,
    extraContent: (LazyListScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    val store = koinInject<GlobalPreferencesRepository>()

    val navigator = LocalNavigator.current
    val uiConfig = LocalHyperXLayoutConfig.current
    val blurEnabled = uiConfig.isBlurActive
    val layoutPadding = LocalLayoutPadding.current

    val containerColor = MiuixTheme.colorScheme.surface
    val blurTintAlpha = if (containerColor.luminance() >= 0.5f) {
        uiConfig.lightBlurAlpha
    } else {
        uiConfig.darkBlurAlpha
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val isTopPopupExpanded = remember { mutableStateOf(false) }
    val showTopPopup = remember { mutableStateOf(false) }
    val modifiedDialogVisibility = remember { mutableStateOf(false) }

    var selectSystemAppRequest by remember { mutableStateOf(false) }
    var clearSelectedRequest by remember { mutableStateOf(false) }
    var queryString by remember { mutableStateOf("") }
    var sortTrigger by remember { mutableIntStateOf(0) }
    val saveSuccessfulText = stringResource(R.string.save_successful)

    // 完整应用列表
    var appInfoList by remember { mutableStateOf<List<MyAppInfo>>(emptyList()) }

    // 在列表中的条目
    var appInfoFilter by remember { mutableStateOf<List<MyAppInfo>>(emptyList()) }

    // 保存前的配置。必须 remember: 这是"进入页面时的基线", 用来判断有没有未保存的改动,
    // 不 remember 的话每次重组都会重新读一遍远程 prefs 并重建 Set
    val tmpCheckedList = remember(checkedListKey) {
        mutableSetOf<String>().apply { addAll(store.get(checkedListKey)) }
    }

    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    BackHandler(true) {
        val currentCheckedList = appInfoList.filter { it.isChecked.value }.map {
            it.packageName
        }.toSet()
        if (currentCheckedList.notEqualsTo(tmpCheckedList)) {
            modifiedDialogVisibility.value = true
        } else {
            navigator.pop()
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        // 应用基础信息走共享缓存, 图标不在这里加载 —— 交给行内的 rememberAppIcon 按需取
        val installedApps = loadInstalledApps(context)

        appInfoList = withContext(Dispatchers.Default) {
            installedApps.map { app ->
                MyAppInfo(
                    appName = app.appName,
                    packageName = app.packageName,
                    isChecked = mutableStateOf(app.packageName in tmpCheckedList),
                    isSystemApp = app.isSystemApp
                )
            }.sortedWith(
                // 按应用类别排序：已勾选的应用优先显示
                compareByDescending<MyAppInfo> { it.isChecked.value }
                    .thenBy(java.text.Collator.getInstance(java.util.Locale.getDefault())) { it.appName }
            )
        }
        isLoading = false
    }

    LaunchedEffect(selectSystemAppRequest) {
        if (selectSystemAppRequest) {
            launch(Dispatchers.Default) {
                appInfoList.filter { it.isSystemApp }.forEach {
                    it.isChecked.value = true
                }
                // 触发重新过滤和排序
                withContext(Dispatchers.Main) {
                    sortTrigger++
                }
            }
        }
    }

    LaunchedEffect(clearSelectedRequest) {
        if (clearSelectedRequest) {
            launch(Dispatchers.Default) {
                appInfoList.forEach {
                    it.isChecked.value = false
                }
                // 触发重新过滤和排序
                withContext(Dispatchers.Main) {
                    sortTrigger++
                }
            }
        }
    }

    // 防抖 + 过滤排序。LaunchedEffect 在 key 变化时本就会取消上一次协程,
    // 原先那个 queryJob 是 composable 的局部变量, 每次重组都被重置为 null,
    // queryJob?.cancel() 永远是空操作, 属于误导性的死代码
    LaunchedEffect(appInfoList, queryString, sortTrigger) {
        if (appInfoList.isEmpty()) return@LaunchedEffect

        delay(if (queryString.isNotBlank()) 300 else 50)

        // 在后台线程进行过滤和排序
        val sorted = withContext(Dispatchers.Default) {
            val filtered = if (queryString.isBlank()) {
                appInfoList
            } else {
                appInfoList.filter {
                    it.appName.contains(queryString, true) || it.packageName.contains(queryString, true) ||
                            HanziToPinyin.toPinyin(it.appName).contains(queryString, true)
                }
            }

            // 排序：已勾选的应用优先，然后按应用名称排序
            filtered.sortedWith(
                compareByDescending<MyAppInfo> { it.isChecked.value }
                    .thenBy(java.text.Collator.getInstance(java.util.Locale.getDefault())) { it.appName }
            )
        }

        appInfoFilter = sorted
    }

    HyperXScaffold(
        modifier = Modifier
            .fillMaxSize()
            .hyperXScrollEndHaptic(),
        containerColor = containerColor,
        layoutPadding = layoutPadding,
        topBar = { contentPadding ->
            TopAppBar(
                color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
                title = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier
                            .padding(start = 21.dp)
                            .size(40.dp),
                        onClick = {
                            val currentCheckedList = appInfoList.filter { it.isChecked.value }.map {
                                it.packageName
                            }.toSet()
                            if (currentCheckedList.notEqualsTo(tmpCheckedList)) {
                                modifiedDialogVisibility.value = true
                            } else {
                                navigator.pop()
                            }
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(26.dp),
                            imageVector = MiuixIcons.Back,
                            contentDescription = "Back",
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                },
                actions = {
                    if (isTopPopupExpanded.value) {
                        OverlayListPopup(
                            show = showTopPopup.value,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = {
                                showTopPopup.value = false
                                isTopPopupExpanded.value = false
                            }
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = stringResource(R.string.select_system_apps),
                                    optionSize = 2,
                                    isSelected = false,
                                    onSelectedIndexChange = {
                                        selectSystemAppRequest = true
                                        clearSelectedRequest = false
                                        showTopPopup.value = false
                                        isTopPopupExpanded.value = false
                                    },
                                    index = 0
                                )
                                DropdownImpl(
                                    text = stringResource(R.string.clear_selected_apps),
                                    optionSize = 2,
                                    isSelected = false,
                                    onSelectedIndexChange = {
                                        selectSystemAppRequest = false
                                        clearSelectedRequest = true
                                        showTopPopup.value = false
                                        isTopPopupExpanded.value = false
                                    },
                                    index = 1
                                )
                            }
                        }
                        SideEffect {
                            if (!showTopPopup.value) {
                                showTopPopup.value = true
                            }
                        }
                    }
                    AnimatedVisibility(
                        queryString.isBlank()
                    ) {
                        IconButton(
                            modifier = Modifier
                                .padding(end = 21.dp)
                                .size(40.dp),
                            onClick = {
                                isTopPopupExpanded.value = true
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.MoreCircle,
                                contentDescription = "Menu"
                            )
                        }
                    }
                },
                defaultWindowInsetsPadding = false,
                titlePadding = 28.dp + contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                navigationIconPadding = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                actionIconPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
            )
        },
        bottomBar = { contentPadding ->
            val captionBarBottomPadding by rememberUpdatedState(
                WindowInsets.captionBar.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
            )
            val buttonPaddingValues = with(LocalLayoutDirection.current) {
                PaddingValues(
                    start = contentPadding.calculateStartPadding(this) + 16.dp,
                    top = 16.dp,
                    end = contentPadding.calculateEndPadding(this) + 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + captionBarBottomPadding + 16.dp
                )
            }
            Surface(
                color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {
                    HorizontalDivider(
                        thickness = 0.75.dp,
                        color = MiuixTheme.colorScheme.dividerLine
                    )
                    TextButton(
                        modifier = Modifier
                            .padding(buttonPaddingValues)
                            .fillMaxWidth(),
                        text = stringResource(R.string.save),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        minHeight = 50.dp,
                        onClick = {
                            val currentCheckedList = appInfoList.filter { it.isChecked.value }.map {
                                it.packageName
                            }.toMutableSet()
                            store.update(checkedListKey, currentCheckedList)
                            tmpCheckedList.apply {
                                clear()
                                addAll(store.get(checkedListKey))
                            }
                            coroutineScope.launch {
                                context.toast(saveSuccessfulText)
                            }
                        }
                    )
                }
            }
        },
        blurTopBar = blurEnabled,
        blurBottomBar = blurEnabled,
        blurTintAlpha = blurTintAlpha,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .hyperXOverScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = paddingValues,
            overscrollEffect = rememberHyperXListOverscrollEffect()
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
                        insideMargin = PaddingValues(all = 16.dp),
                        summary = stringResource(R.string.save_hint),
                        summaryColor = BasicComponentDefaults.titleColor(),
                        startAction = {
                            Image(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp),
                                imageVector = MiuixIcons.Info,
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface)
                            )
                        }
                    )
                }
            }
            extraContent?.let { it1 -> it1() }
            if (isLoading) {
                item {
                    SmallTitle(
                        text = stringResource(R.string.loading),
                        modifier = Modifier.padding(top = 6.dp),
                        textColor = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
            } else {
                // key 只用包名: 原先把 index、列表长度和勾选状态都拼进了 key,
                // 排序一变 key 就全变, LazyColumn 会丢弃并重建所有 item, 复用完全失效
                itemsIndexed(appInfoFilter, key = { _, item -> item.packageName }) { index, item ->
                    val topCornerRadius = if (index == 0) CardDefaults.CornerRadius else 0.dp
                    val bottomCornerRadius = if (index == appInfoFilter.size - 1) CardDefaults.CornerRadius else 0.dp
                    SpliceCard(
                        topCornerRadius,
                        bottomCornerRadius
                    ) {
                        SwitchPreference(
                            icon = rememberAppIcon(item.packageName),
                            title = item.appName,
                            summary = item.packageName,
                            checked = item.isChecked,
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
    AlertDialog(
        visibility = modifiedDialogVisibility,
        title = stringResource(R.string.not_saved_title),
        message = stringResource(R.string.not_saved_hint),
        cancelable = false,
        mode = AlertDialogMode.NegativeAndPositive,
        negativeText = stringResource(R.string.button_abandonment),
        positiveText = stringResource(R.string.button_reedit),
        onNegativeButton = {
            modifiedDialogVisibility.value = false
            navigator.pop()
        }
    )
}

@Composable
fun SpliceCard(
    topCornerRadius: Dp,
    bottomCornerRadius: Dp,
    skipTopPadding: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember(topCornerRadius, bottomCornerRadius) {
        if (topCornerRadius == 0.dp && bottomCornerRadius == 0.dp)
            RectangleShape
        else
            RoundedCornerShape(
                topStart = topCornerRadius, topEnd = topCornerRadius,
                bottomStart = bottomCornerRadius, bottomEnd = bottomCornerRadius
            )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = if (topCornerRadius != 0.dp && !skipTopPadding) 6.dp else 0.dp,
                bottom = if (bottomCornerRadius != 0.dp) 6.dp else 0.dp
            ),
        shape = shape,
        color = MiuixTheme.colorScheme.surfaceContainer,
        contentColor = MiuixTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.padding(CardDefaults.InsideMargin),
            content = content
        )
    }
}

data class MyAppInfo(
    val appName: String,
    val packageName: String,
    // 该 isChecked 用于存储应用是否被勾选, 0 为未勾选, 1 为勾选
    var isChecked: MutableState<Boolean>,
    val isSystemApp: Boolean
)
