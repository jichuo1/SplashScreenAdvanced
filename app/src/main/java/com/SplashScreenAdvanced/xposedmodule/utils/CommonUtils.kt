@file:JvmName("CommonUtils")

package com.SplashScreenAdvanced.xposedmodule.utils

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.StringRes
import java.io.DataOutputStream

/**
 * 显示 Toast
 *
 * @receiver 需要显示 Toast 应用的 Context
 * @param message 显示文本内容
 * @param duration 显示时长, 默认 [Toast.LENGTH_SHORT]
 * @return [Toast]
 */
fun Context.toast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT): Toast = Toast
    .makeText(this, message, duration)
    .apply { show() }

/**
 * 显示 Toast, 内容来自字符串资源 ID
 *
 * @receiver 需要显示 Toast 应用的 Context
 * @param stringID 字符串资源的 ID
 * @param duration 显示时长, 默认 [Toast.LENGTH_SHORT]
 * @return [Toast]
 */
fun Context.toast(@StringRes stringID: Int, duration: Int = Toast.LENGTH_SHORT) =
    toast(getString(stringID), duration)

/**
 * 执行 Shell 命令
 *
 * 阻塞直到 su 进程退出, 调用方应在后台线程调用。
 *
 * 原实现只关了 stdin 就返回: [Process] 从不 waitFor / destroy, 留下僵尸进程和三个未回收的
 * 管道 fd; stdout / stderr 也不排空, su 输出稍多就会把子进程堵死。
 *
 * 注意必须单流排空: stderr 合并进 stdout 后一次读完。若分流顺序读取, 子进程 stderr 写满
 * 管道缓冲会阻塞在写 stderr, 而父进程阻塞在读 stdout —— 双向死锁
 *
 * @param command Shell 命令
 * @return 退出码; 取不到 (如没有 su) 返回 null
 */
fun execShell(command: String): Int? {
    var process: Process? = null
    return try {
        process = ProcessBuilder("su").redirectErrorStream(true).start()
        DataOutputStream(process.outputStream).use { out ->
            // 补上换行与 exit: 部分 su 实现要读到换行才会执行该行
            out.writeBytes("$command\n")
            out.writeBytes("exit\n")
            out.flush()
        }
        // 排空输出, 避免管道缓冲区写满后子进程阻塞
        process.inputStream.use { it.readBytes() }
        process.waitFor()
    } catch (t: Throwable) {
        t.printStackTrace()
        null
    } finally {
        process?.destroy()
    }
}

/**
 * 将值为类似 <[String]_[String]> 的 Set 转换成 <[String], [String]> 的 Map
 */
fun Set<String>.toMap(): MutableMap<String, String> {
    val result = mutableMapOf<String, String>()
    forEach { item ->
        val separatorIndex = item.lastIndexOf("_")
        if (separatorIndex != -1 && separatorIndex < item.length - 1) {
            val packageName = item.substring(0, separatorIndex)
            val duration = item.substring(separatorIndex + 1)
            result[packageName] = duration
        }
    }
    return result
}

/**
 * 将类似 <[String], [String]> 的 Map 转换成值为类似 <[String]_[String]> 的 Set
 */
fun MutableMap<String, String>.toSet(): MutableSet<String> {
    val result = mutableSetOf<String>()
    forEach { (key, value) ->
        result += "${key}_${value}"
    }
    return result
}

/**
 * 比较两个 [Collection] 的内容是否相同
 */
infix fun Collection<*>.notEqualsTo(second: Collection<*>): Boolean = !(this equalTo second)

private infix fun Collection<*>.equalTo(second: Collection<*>): Boolean {
    if (size != second.size) return false
    forEach { if (it !in second) return false }
    return true
}

/**
 * 是否处于深色模式
 */
val Context.isDarkMode: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
