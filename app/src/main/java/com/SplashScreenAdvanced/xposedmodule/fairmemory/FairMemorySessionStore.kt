package com.SplashScreenAdvanced.xposedmodule.fairmemory

import android.content.Context
import androidx.navigation3.runtime.NavKey
import dev.lackluster.hyperx.navigation.HyperXRoute

/**
 * 查杀广播到达时把当前页 token 同步写进进程私有 SP。
 * 下次冷启动 [consumeRestoreBackStack] 读一次后清掉, 避免每次启动都跳回旧页。
 */
object FairMemorySessionStore {
    private const val PREFS = "fair_memory_session"
    private const val KEY_PENDING = "pending_restore"
    private const val KEY_TOKEN = "token"

    @Volatile
    private var currentToken: String = "Main"

    fun remember(key: NavKey?) {
        currentToken = key?.toFairMemoryToken() ?: "Main"
    }

    fun persistForKill(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_TOKEN, currentToken)
            .commit()
    }

    fun consumeRestoreBackStack(context: Context): List<NavKey> {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return listOf(HyperXRoute.Main)
        prefs.edit().putBoolean(KEY_PENDING, false).apply()
        return restoreBackStackFromToken(prefs.getString(KEY_TOKEN, null))
    }
}
