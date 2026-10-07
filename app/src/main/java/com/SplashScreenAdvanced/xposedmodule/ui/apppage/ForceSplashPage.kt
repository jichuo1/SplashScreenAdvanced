package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.appList
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi

fun NativePageUi.buildForceSplashPage(): View = appList(Preferences.AppList.FORCE_SHOW_SPLASH_SCREEN_LIST)
