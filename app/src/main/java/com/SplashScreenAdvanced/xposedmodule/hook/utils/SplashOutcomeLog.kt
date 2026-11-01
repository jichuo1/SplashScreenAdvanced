package com.SplashScreenAdvanced.xposedmodule.hook.utils

import com.SplashScreenAdvanced.xposedmodule.utils.XMLog

/** 有界的「首次出现」记录，超出容量时淘汰最早的键 */
internal class OnceKeys(private val capacity: Int = 256) {
    private val keys = LinkedHashSet<String>()

    @Synchronized
    fun firstTime(key: String): Boolean {
        if (!keys.add(key)) return false
        if (keys.size > capacity) keys.iterator().apply { next(); remove() }
        return true
    }
}

/**
 * 遮罩处理结果的非门控日志: 同一 (包, 结果) 每进程只写一条
 *
 * 作用域外 / 圆角被跳过这类「没有发生」的事，不开调试日志就无从得知原因；
 * 按 (包, 结果) 去重后日志量与应用数成正比而不是与启动次数成正比
 */
internal object SplashOutcomeLog {
    private val once = OnceKeys()

    fun note(packageName: String, outcome: String) {
        if (once.firstTime("$packageName|$outcome")) {
            XMLog.i { "[Splash] $packageName: $outcome" }
        }
    }
}
