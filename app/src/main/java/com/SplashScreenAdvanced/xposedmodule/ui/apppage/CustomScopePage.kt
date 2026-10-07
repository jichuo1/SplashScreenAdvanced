package com.SplashScreenAdvanced.xposedmodule.ui.apppage

import android.view.View
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.ui.component.appList
import com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativePageUi

fun NativePageUi.buildCustomScopePage(): View = appList(Preferences.AppList.CUSTOM_SCOPE_LIST, extra = {
        switch(R.string.exception_mode, Preferences.Scope.IS_CUSTOM_SCOPE_EXCEPTION_MODE).also { view ->
            val row = view as com.SplashScreenAdvanced.xposedmodule.ui.nativeview.NativeRow
            bind {
                row.summaryView.visibility = View.VISIBLE
                row.summaryView.text = string(R.string.custom_scope_exception_mode_message, string(
                    if (repo.get(Preferences.Scope.IS_CUSTOM_SCOPE_EXCEPTION_MODE)) R.string.will_not
                    else R.string.will_only))
            }
        }
    })
