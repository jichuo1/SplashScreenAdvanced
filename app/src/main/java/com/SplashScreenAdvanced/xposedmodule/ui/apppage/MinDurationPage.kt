package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.appList
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi

fun NativePageUi.buildMinDurationPage(): View = appList(Preferences.AppList.MIN_DURATION_LIST, durationEditing = true)
