package com.SplashScreenAdvanced.xposedmodule.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.Route
import com.SplashScreenAdvanced.xposedmodule.provider.AppPreferenceActions
import com.SplashScreenAdvanced.xposedmodule.state.GlobalUIViewModel
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.BackgroundExceptPage
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.BgIndividualPage
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.CustomScopePage
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.ForceSplashPage
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.HideIconPage
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.IgnoreAppIconPage
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.MinDurationPage
import com.SplashScreenAdvanced.xposedmodule.ui.apppage.RemoveBrandingPage
import com.SplashScreenAdvanced.xposedmodule.ui.component.ColorPickerPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.AboutPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.BackgroundPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.BasicPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.BottomPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.DevPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.DisplayPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.IconPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.MainPage
import com.SplashScreenAdvanced.xposedmodule.ui.page.ScopePage
import dev.lackluster.hyperx.core.HyperXActivity
import dev.lackluster.hyperx.ui.layout.HyperXAppLayout
import dev.lackluster.hyperx.ui.preference.core.LocalPreferenceActions
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : HyperXActivity() {
    private val uiViewModel: GlobalUIViewModel by viewModel()

    override fun onResume() {
        super.onResume()
        uiViewModel.refreshRestartState()
    }

    @Composable
    override fun AppContent() {
        val globalUiVm: GlobalUIViewModel = koinViewModel()
        val uiConfig by globalUiVm.configFlow.collectAsState()

        val appPreferenceActions: AppPreferenceActions = koinInject()
        val primaryContent = remember<@Composable () -> Unit> { { MainPage() } }
        val emptyContent = remember<@Composable () -> Unit> {
            {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        modifier = Modifier.size(256.dp),
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.secondary)
                    )
                }
            }
        }
        val customEntryProvider = remember {
            { key: NavKey ->
                NavEntry(key) {
                    when (key) {
                        is Route.About -> AboutPage()
                        is Route.Basic -> BasicPage()
                        is Route.Scope -> ScopePage()
                        is Route.Icon -> IconPage()
                        is Route.Bottom -> BottomPage()
                        is Route.Background -> BackgroundPage()
                        is Route.Display -> DisplayPage()
                        is Route.Developer -> DevPage()
                        is Route.CustomScope -> CustomScopePage()
                        is Route.IgnoreAppIcon -> IgnoreAppIconPage()
                        is Route.HideIcon -> HideIconPage()
                        is Route.RemoveBranding -> RemoveBrandingPage()
                        is Route.BackgroundExcept -> BackgroundExceptPage()
                        is Route.BgIndividual -> BgIndividualPage()
                        is Route.MinDuration -> MinDurationPage()
                        is Route.ForceSplash -> ForceSplashPage()
                        is Route.ColorPicker -> ColorPickerPage(key.pkgName)
                        else -> {}
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalPreferenceActions provides appPreferenceActions,
            LocalAppUiState provides globalUiVm,
        ) {
            HyperXAppLayout(
                config = uiConfig,
                primaryContent = primaryContent,
                emptyContent = emptyContent,
                customEntryProvider = customEntryProvider,
            )
        }
    }
}
