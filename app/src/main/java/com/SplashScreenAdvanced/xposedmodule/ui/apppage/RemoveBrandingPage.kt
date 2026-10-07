package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.appList
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi

fun NativePageUi.buildRemoveBrandingPage(): View = appList(Preferences.AppList.REMOVE_BRANDING_IMAGE_LIST, extra = {
        switch(R.string.exception_mode, Preferences.Scope.IS_REMOVE_BRANDING_IMAGE_EXCEPTION_MODE).also { view ->
            val row = view as com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeRow
            bind {
                row.summaryView.visibility = View.VISIBLE
                row.summaryView.text = string(R.string.exception_mode_message, string(
                    if (repo.get(Preferences.Scope.IS_REMOVE_BRANDING_IMAGE_EXCEPTION_MODE)) R.string.not_chosen
                    else R.string.chosen))
            }
        }
    })
