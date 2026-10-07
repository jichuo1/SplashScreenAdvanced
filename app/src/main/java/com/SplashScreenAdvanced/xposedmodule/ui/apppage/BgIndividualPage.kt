package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.ui.component.appList
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi

fun NativePageUi.buildBgIndividualPage(): View = appList(colorBrowsing = true)
