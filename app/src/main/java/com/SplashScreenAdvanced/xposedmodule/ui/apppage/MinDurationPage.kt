package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.SpliceCard
import com.SplashScreenAdvanced.xposedmodule.ui.component.loadInstalledApps
import com.SplashScreenAdvanced.xposedmodule.ui.component.rememberAppIcon
import com.SplashScreenAdvanced.xposedmodule.utils.CommonUtils.notEqualsTo
import com.SplashScreenAdvanced.xposedmodule.utils.CommonUtils.toMap
import com.SplashScreenAdvanced.xposedmodule.utils.CommonUtils.toSet
import com.SplashScreenAdvanced.xposedmodule.utils.RemotePreferenceStore
import dev.lackluster.hyperx.core.utils.HanziToPinyin
import dev.lackluster.hyperx.navigation.LocalNavigator
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.PreferenceIconSlot
import dev.lackluster.hyperx.ui.dialog.AlertDialog
import dev.lackluster.hyperx.ui.dialog.AlertDialogMode
import dev.lackluster.hyperx.ui.dialog.EditTextDialog
import dev.lackluster.hyperx.ui.layout.HyperXScaffold
import dev.lackluster.hyperx.ui.layout.LocalHyperXLayoutConfig
import dev.lackluster.hyperx.ui.layout.LocalLayoutPadding
import dev.lackluster.hyperx.ui.preference.EditTextInputType
import dev.lackluster.hyperx.ui.preference.EditTextPreference
import dev.lackluster.hyperx.ui.preference.PreferenceGroup
import dev.lackluster.hyperx.ui.preference.ValuePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.Collator
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * 基础设置 - 遮罩最小持续时长
 */
@Composable
fun MinDurationPage() {
    val context = LocalContext.current
    val store = koinInject<RemotePreferenceStore>()

    val navigator = LocalNavigator.current
    val uiConfig = LocalHyperXLayoutConfig.current
    val blurEnabled = uiConfig.isBlurEnabled
    val layoutPadding = LocalLayoutPadding.current

    val containerColor = MiuixTheme.colorScheme.surface
    val blurTintAlpha = if (containerColor.luminance() >= 0.5f) {
        uiConfig.lightBlurAlpha
    } else {
        uiConfig.darkBlurAlpha
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val modifiedDialogVisibility = remember { mutableStateOf(false) }

    val dialogMessage = stringResource(R.string.set_min_duration) + "\n" + stringResource(R.string.set_min_duration_unit)
    var queryString by remember { mutableStateOf("") }
    var sortTrigger by remember { mutableIntStateOf(0) }

    val emptyMapString = stringResource(R.string.not_set_min_duration)

    // 完整应用列表
    var appInfoList by remember { mutableStateOf<List<DurationAppInfo>>(emptyList()) }

    // 在列表中的条目
    var appInfoFilter by remember { mutableStateOf<List<DurationAppInfo>>(emptyList()) }

    // 保存前的配置。必须 remember: 这是"进入页面时的基线", 用来判断有没有未保存的改动,
    // 不 remember 的话每次重组都会重新读一遍远程 prefs 并重建集合
    val tmpCheckedList = remember {
        mutableSetOf<String>().apply { addAll(store.get(Preferences.AppList.MIN_DURATION_LIST)) }
    }
    val tmpConfigMap = remember {
        mutableMapOf<String, String>().apply {
            putAll(store.get(Preferences.AppList.MIN_DURATION_CONFIG_MAP).toMap())
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }

    BackHandler(true) {
        val currentCheckedList = appInfoList.filter { it.isChecked.value }.map {
            it.packageName
        }.toSet()
        val currentConfigMap = appInfoList.filter {
            it.config.value != null && it.config.value != emptyMapString
        }.map {
            "${it.packageName}_${it.config.value}"
        }.toSet()
        if (currentCheckedList.notEqualsTo(tmpCheckedList) || currentConfigMap.notEqualsTo(tmpConfigMap.toSet())) {
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
                DurationAppInfo(
                    appName = app.appName,
                    packageName = app.packageName,
                    isChecked = mutableStateOf(app.packageName in tmpCheckedList),
                    config = mutableStateOf(tmpConfigMap[app.packageName])
                )
            }.sortedWith(
                // 按应用类别排序：已勾选且有配置的应用优先显示
                compareByDescending<DurationAppInfo> { it.isChecked.value }
                    .thenByDescending { it.config.value != null }
                    .thenBy(Collator.getInstance(Locale.getDefault())) { it.appName }
            )
        }
        isLoading = false
    }

    // LaunchedEffect 在 key 变化时本就会取消上一次协程, 原先那个 queryJob 是 composable 局部变量,
    // 每次重组都被重置为 null, cancel() 永远是空操作
    LaunchedEffect(appInfoList, queryString, sortTrigger) {
        if (appInfoList.isEmpty()) return@LaunchedEffect

        delay(if (queryString.isNotBlank()) 300.milliseconds else 50.milliseconds)

        // 在后台线程进行过滤和排序
        appInfoFilter = withContext(Dispatchers.Default) {
            val filtered = if (queryString.isBlank()) {
                appInfoList
            } else {
                appInfoList.filter {
                    it.appName.contains(queryString, true) || it.packageName.contains(queryString, true) ||
                            HanziToPinyin.toPinyin(it.appName).contains(queryString, true)
                }
            }

            // 排序：已勾选的应用优先，有配置的其次，然后按应用名称排序
            filtered.sortedWith(
                compareByDescending<DurationAppInfo> { it.isChecked.value }
                    .thenByDescending { it.config.value != null }
                    .thenBy(Collator.getInstance(Locale.getDefault())) { it.appName }
            )
        }
    }

    HyperXScaffold(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic(),
        containerColor = containerColor,
        layoutPadding = layoutPadding,
        topBar = { contentPadding ->
            TopAppBar(
                color = if (blurEnabled) Color.Transparent else MiuixTheme.colorScheme.surface,
                title = stringResource(R.string.min_duration_title),
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
                            val currentConfigMap = appInfoList.filter {
                                it.config.value != null && it.config.value != emptyMapString
                            }.map {
                                "${it.packageName}_${it.config.value}"
                            }.toSet()
                            if (currentCheckedList.notEqualsTo(tmpCheckedList) || currentConfigMap.notEqualsTo(tmpConfigMap.toSet())) {
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
                    HorizontalDivider()
                    TextButton(
                        modifier = Modifier
                            .padding(buttonPaddingValues)
                            .fillMaxWidth(),
                        text = stringResource(R.string.save),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        minHeight = 50.dp,
                        onClick = {
                            // 用页面自己的 rememberCoroutineScope, 而不是 new 一个 CoroutineScope:
                            // 后者不属于任何生命周期, 每点一次保存就泄漏一个永不取消的作用域
                            coroutineScope.launch {
                                val currentCheckedList = appInfoList.filter { it.isChecked.value }.map {
                                    it.packageName
                                }.toMutableSet()
                                val currentConfigMap = appInfoList.filter {
                                    it.config.value != null && it.config.value != emptyMapString
                                }.map {
                                    "${it.packageName}_${it.config.value}"
                                }.toMutableSet()

                                withContext(Dispatchers.Default) {
                                    store.put(Preferences.AppList.MIN_DURATION_LIST, currentCheckedList)
                                    store.put(Preferences.AppList.MIN_DURATION_CONFIG_MAP, currentConfigMap)
                                    tmpCheckedList.apply {
                                        clear()
                                        addAll(store.get(Preferences.AppList.MIN_DURATION_LIST))
                                    }
                                    tmpConfigMap.apply {
                                        clear()
                                        putAll(store.get(Preferences.AppList.MIN_DURATION_CONFIG_MAP).toMap())
                                    }
                                }

                                Toast.makeText(
                                    context,
                                    context.getString(R.string.save_successful),
                                    Toast.LENGTH_SHORT
                                ).show()
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
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = paddingValues,
            overscrollEffect = null
        ) {
            item {
                SearchBar(
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
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
                        summary = stringResource(R.string.min_duration_sub_setting_hint),
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
            item {
                PreferenceGroup {
                    val defaultMinDuration = remember {
                        mutableIntStateOf(store.get(Preferences.Display.MIN_DURATION))
                    }
                    EditTextPreference(
                        title = stringResource(R.string.set_default_min_duration),
                        text = defaultMinDuration.intValue.toString(),
                        inputType = EditTextInputType.Number,
                        valuePosition = ValuePosition.Value,
                        dialogMessage = stringResource(R.string.set_min_duration_unit),
                        onTextChange = { newText ->
                            newText.toIntOrNull()?.let { newValue ->
                                if (newValue in 0..2000) {
                                    store.put(Preferences.Display.MIN_DURATION, newValue)
                                    defaultMinDuration.intValue = newValue
                                }
                            }
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
                item {
                    SmallTitle(
                        text = stringResource(R.string.min_duration_separate_configuration),
                        modifier = Modifier.padding(top = 6.dp),
                        textColor = MiuixTheme.colorScheme.onBackgroundVariant
                    )
                }
                // key 只用包名: 把 index / 列表长度 / 勾选状态拼进 key 会让排序一变就重建全部 item
                itemsIndexed(appInfoFilter, key = { _, item -> item.packageName }) { index, item ->
                    val topCornerRadius = if (index == 0) CardDefaults.CornerRadius else 0.dp
                    val bottomCornerRadius = if (index == appInfoFilter.size - 1) CardDefaults.CornerRadius else 0.dp
                    val skipTopPadding = index == 0
                    SpliceCard(
                        topCornerRadius,
                        bottomCornerRadius,
                        skipTopPadding
                    ) {
                        MinDurationPreference(
                            icon = rememberAppIcon(item.packageName),
                            title = item.appName,
                            summary = item.packageName,
                            checked = item.isChecked,
                            defValue = item.config.value?.toIntOrNull() ?: 0,
                            dialogMessage = dialogMessage,
                            onCheckedChange = {
                                // 在用户切换选中状态后，延迟触发重新排序
                                coroutineScope.launch {
                                    delay(200.milliseconds)
                                    sortTrigger++
                                }
                            },
                            onValueChange = { text, value ->
                                if (value == 0) {
                                    item.config.value = null
                                } else {
                                    item.config.value = text
                                }
                                // 配置变更后触发重新排序
                                coroutineScope.launch {
                                    delay(200.milliseconds)
                                    sortTrigger++
                                }
                            }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                }
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
fun MinDurationPreference(
    icon: ImageIcon? = null,
    title: String,
    summary: String? = null,
    checked: MutableState<Boolean>,
    defValue: Int = 0,
    dialogMessage: String? = null,
    onCheckedChange: (() -> Unit)? = null,
    onValueChange: ((String, Int) -> Unit)? = null,
) {
    val emptyString = stringResource(R.string.not_set_min_duration)
    val dialogVisibility = remember { mutableStateOf(false) }
    val spValue = remember { mutableIntStateOf(defValue) }
    val stringValue = if (spValue.intValue != 0) spValue.intValue.toString() else emptyString
    val doOnInputConfirm: (String) -> Unit = { newString: String ->
        val oldValue = spValue.intValue
        val newValue = newString.toIntOrNull()
        if (newValue != null && (newValue in 0..2000) && oldValue != newValue) {
            spValue.intValue = newValue
            onValueChange?.let { it(newString, newValue) }
        }
    }
    BasicComponent(
        startAction = {
            icon?.let {
                PreferenceIconSlot(it)
            }
        },
        endActions = {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VerticalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Switch(
                    checked = checked.value,
                    onCheckedChange = { newValue ->
                        checked.value = newValue
                        onCheckedChange?.invoke()
                    }
                )
            }
        },
        onClick = {
            dialogVisibility.value = true
        }
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DurationBadge(text = stringValue, isSet = spValue.intValue != 0)
            summary?.let {
                Text(
                    text = it,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
    }
    EditTextDialog(
        visible = dialogVisibility.value,
        title = title,
        message = dialogMessage,
        initialText = spValue.intValue.toString(),
        keyboardType = KeyboardType.Number,
        onDismissRequest = { dialogVisibility.value = false },
        onConfirm = { newString ->
            doOnInputConfirm(newString)
        }
    )
}

/** 时长状态徽章：未设置=中性底，已设置=主题强调色底，放在 summary 包名前 */
@Composable
private fun DurationBadge(
    text: String,
    isSet: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .background(
                color = if (isSet) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
        fontSize = 11.sp,
        color = if (isSet) MiuixTheme.colorScheme.primary
        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

data class DurationAppInfo(
    val appName: String,
    val packageName: String,
    // 该 isChecked 用于存储应用是否被勾选, 0 为未勾选, 1 为勾选
    var isChecked: MutableState<Boolean>,
    var config: MutableState<String?>,
)
