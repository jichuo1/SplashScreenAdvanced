package com.SplashScreenAdvanced.xposedmodule.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.SplashScreenAdvanced.xposedmodule.R
import com.SplashScreenAdvanced.xposedmodule.repository.GlobalPreferencesRepository
import com.SplashScreenAdvanced.xposedmodule.ui.MainActivity
import com.SplashScreenAdvanced.xposedmodule.utils.CommonUtils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import kotlin.system.exitProcess

/**
 * 改自 [MiuiHomeR](https://github.com/qqlittleice/MiuiHome_R/blob/9f3a298df6427b3a8ea6a47aaabfa0a56c4dd11e/app/src/main/java/com/yuk/miuiHomeR/utils/BackupUtils.kt#L47)
 * 用于备份和恢复数据
 *
 * 数据读写统一委托给 [GlobalPreferencesRepository] 的 import/export 逻辑（基于 [RemotePreferenceStore]）。
 */
object BackupUtils {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val repository: GlobalPreferencesRepository
        get() = KoinJavaComponent.get(GlobalPreferencesRepository::class.java)

    /**
     * 处理打开文件, 读取并导入数据
     *
     * @param context [Context]
     * @param data [Uri]
     */
    fun handleReadDocument(context: Context, data: Uri?) {
        val uri = data ?: return
        scope.launch {
            val result = repository.importBackup(uri)
            if (result.isSuccess) {
                context.toast(R.string.restore_successful)
                // 留一点时间让 Toast 显示出来, 再重启进程。
                // 这里在协程里, 用 delay 挂起即可, 不需要 Thread.sleep 占住一个线程
                delay(500)
                val intent =
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                exitProcess(0)
            } else {
                context.toast(R.string.restore_failed)
            }
        }
    }

    /**
     * 处理保存文件, 导出数据
     *
     * @param context [Context]
     * @param data [Uri]
     */
    fun handleCreateDocument(context: Context, data: Uri?) {
        val uri = data ?: return
        scope.launch {
            val result = repository.exportBackup(uri)
            if (result.isSuccess) {
                context.toast(context.getString(R.string.save_successful))
            } else {
                context.toast(R.string.save_failed)
            }
        }
    }
}
