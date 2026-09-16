package com.SplashScreenAdvanced.xposedmodule.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.ui.MainActivity
import com.SplashScreenAdvanced.xposedmodule.utils.CommonUtils.toast
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.preference.TextPreference as HyperXTextPreference

/**
 * TextPreference 在用户点击后先判断模块是否激活, 如未激活则发出 toast
 */
@Composable
fun TextPreference(
    icon: ImageIcon? = null,
    title: String,
    summary: String? = null,
    value: String? = null,
    ignoreModuleActiveStatus: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    HyperXTextPreference(
        title = title,
        icon = icon,
        summary = summary,
        value = value,
        onClick = {
            if (!MainActivity.moduleActive.value && !ignoreModuleActiveStatus) {
                context.toast(R.string.make_sure_active)
            } else {
                onClick?.invoke()
            }
        }
    )
}
