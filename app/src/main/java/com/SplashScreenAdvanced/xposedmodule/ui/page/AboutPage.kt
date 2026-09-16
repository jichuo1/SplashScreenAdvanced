package com.SplashScreenAdvanced.xposedmodule.ui.page

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.ui.LocalAppUiState
import com.SplashScreenAdvanced.xposedmodule.ui.component.TextPreference
import com.SplashScreenAdvanced.xposedmodule.utils.toast
import dev.lackluster.hyperx.ui.component.IconSize
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.layout.HyperXPage
import dev.lackluster.hyperx.ui.preference.ItemPosition
import dev.lackluster.hyperx.ui.preference.itemPreferenceGroup
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlin.math.min

/**
 * 关于页面
 */
@Composable
fun AboutPage() {
    HyperXPage(
        title = stringResource(R.string.about),
    ) {
        item(key = "header") {
            AdaptiveHeaderCard(
                colorCardContent = { HeaderBrandCard() },
                infoCardContent = { AppInfoCard() }
            )
        }
        itemPreferenceGroup(
            titleRes = R.string.open_source_license,
            position = ItemPosition.Last
        ) {
            val context = LocalContext.current
            for (project in OpenSourceReference.entries) {
                TextPreference(
                    title = "${project.author}/${project.name}",
                    summary = project.license
                ) {
                    with(context) {
                        toast(getString(R.string.thanks_to, project.author))
                        openExternalUrl(project.link)
                    }
                }
            }
        }
    }
}

/**
 * 关于页面的头部卡片
 */
@Composable
private fun HeaderBrandCard() {
    val context = LocalContext.current
    val repo = koinInject<GlobalPreferencesRepository>()
    val uiState = LocalAppUiState.current
    var count = 0
    var lastClickTime: Long = 0

    Box(
        modifier = Modifier.clickable {
            // 连点 5 次, 开启开发者设置
            val now = System.currentTimeMillis()
            if (now - lastClickTime < 500)
                count++
            else
                count = 1
            lastClickTime = now
            if (count != 5) return@clickable
            count = 0
            if (!repo.get(Preferences.Dev.ENABLE_DEV_SETTINGS)) {
                repo.update(Preferences.Dev.ENABLE_DEV_SETTINGS, true)
                uiState.syncDevMode()
                context.toast(R.string.enable_dev_settings)
            } else {
                context.toast(R.string.enable_dev_settings)
            }
        }
    ) {
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.08f)),
            alignment = Alignment.CenterStart,
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val density = LocalDensity.current
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White.copy(alpha = 0.7f),
                style = TextStyle(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.1f),
                        offset = with(density) { Offset(0f, 3.dp.toPx()) },
                        blurRadius = with(density) { 6.dp.toPx() }
                    )
                )
            )
            Spacer(modifier = Modifier.heightIn(min = 12.dp))
            Text(
                text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * 模块信息组 (本仓库及上游出处)
 *
 * 上游条目不是可选的装饰: 本项目以 AGPL-3.0 分发, 必须保留对原作品的出处说明
 */
@Composable
private fun AppInfoCard() {
    val context = LocalContext.current
    TextPreference(
        icon = ImageIcon(
            resId = R.drawable.img_github,
            size = IconSize.App
        ),
        title = "GitHub",
        summary = stringResource(R.string.open_source_repo)
    ) {
        context.openExternalUrl("https://github.com/jichuo1/SplashScreenAdvanced")
    }
    TextPreference(
        title = stringResource(R.string.upstream_project),
        summary = stringResource(R.string.upstream_project_summary)
    ) {
        context.openExternalUrl("https://github.com/GSWXXN/RestoreSplashScreen")
    }
}

/**
 * 根据屏幕宽度动态调整两个卡片的排列方式：
 * - 当屏幕宽度大于等于 768dp 时，两张卡片横向并排显示
 * - 当屏幕宽度小于 768dp 时，两张卡片纵向堆叠显示
 *
 * @param colorCardContent 用于定义颜色卡片（`colorCard`）内容的可组合项。
 * @param infoCardContent 用于定义信息卡片（`infoCard`）内容的可组合项。
 */
@Composable
private fun AdaptiveHeaderCard(
    colorCardContent: @Composable () -> Unit,
    infoCardContent: @Composable () -> Unit
) {
    Layout(
        content = {
            Card(
                colors = CardDefaults.defaultColors(Color("#21A2EE".toColorInt())),
                pressFeedbackType = PressFeedbackType.Sink
            ) {
                colorCardContent()
            }
            Card {
                infoCardContent()
            }
        }
    ) { measurables, constraints ->
        if (measurables.size != 2) {
            layout(0, 0) { }
        }
        if (constraints.maxWidth >= 768.dp.roundToPx()) {
            val cardWidthPx = (constraints.maxWidth - 48.dp.roundToPx()) / 2
            val infoCard = measurables[1].measure(
                constraints.copy(
                    minWidth = cardWidthPx, maxWidth = cardWidthPx
                )
            )
            val colorCard = measurables[0].measure(
                constraints.copy(
                    minWidth = cardWidthPx, maxWidth = cardWidthPx,
                    minHeight = infoCard.height, maxHeight = infoCard.height
                )
            )
            val layoutHeight = infoCard.height + 18.dp.roundToPx()
            layout(constraints.maxWidth, layoutHeight) {
                colorCard.place(12.dp.roundToPx(), 12.dp.roundToPx())
                infoCard.place(constraints.maxWidth - cardWidthPx - 12.dp.roundToPx(), 12.dp.roundToPx())
            }
        } else {
            val cardWidthPx = constraints.maxWidth - 24.dp.roundToPx()
            val infoCard = measurables[1].measure(
                constraints.copy(
                    minWidth = cardWidthPx, maxWidth = cardWidthPx
                )
            )
            val colorCardHeight = min(infoCard.height, cardWidthPx / 2)
            val colorCard = measurables[0].measure(
                constraints.copy(
                    minWidth = cardWidthPx, maxWidth = cardWidthPx,
                    minHeight = colorCardHeight, maxHeight = colorCardHeight
                )
            )
            val layoutHeight = colorCard.height + infoCard.height + 30.dp.roundToPx()
            layout(constraints.maxWidth, layoutHeight) {
                colorCard.place(12.dp.roundToPx(), 12.dp.roundToPx())
                infoCard.place(12.dp.roundToPx(), colorCard.height + 24.dp.roundToPx())
            }
        }
    }
}

/**
 * 定义项目中所引用的第三方开源库的相关信息
 */
enum class OpenSourceReference(val author: String, val license: String, val link: String) {
    MIUINativeNotifyIcon("fankes", "AGPL-3.0", "https://github.com/fankes/MIUINativeNotifyIcon"),
    `Hide-My-Applist`("Dr-TSNG", "AGPL-3.0", "https://github.com/Dr-TSNG/Hide-My-Applist"),
    YukiHookAPI("fankes", "Apache-2.0", "https://github.com/fankes/YukiHookAPI"),
    HyperCompose("HowieHChen", "Apache-2.0", "https://github.com/HowieHChen/hyperx-compose"),
    Miuix("miuix-kotlin-multiplatform", "Apache-2.0", "https://github.com/miuix-kotlin-multiplatform/miuix")
}

/**
 * 通过系统浏览器打开指定 URL
 */
private fun Context.openExternalUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: Exception) {
        // 原先是静默失败, 用户点了没反应也不知道为什么
        toast(R.string.no_browser)
    }
}
