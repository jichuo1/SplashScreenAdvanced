package com.SplashScreenAdvanced.xposedmodule.fairmemory

/**
 * 小米 / 金标联盟「公平运行内存」广播协议。
 *
 * 字段与数值对齐 HyperOS 开发者文档 pId=2304:
 * https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2304
 */
object FairMemoryProtocol {
    const val ACTION_TRIM = "itgsa.intent.action.TRIM"
    const val ACTION_KILL = "itgsa.intent.action.KILL"

    const val BUNDLE_COMMON = "common"
    const val BUNDLE_EXTRA = "extra"

    const val KEY_NOTIFY_TYPE = "notifyType"
    const val KEY_NOTIFY_ID = "notifyId"
    const val KEY_REASON = "reason"
    const val KEY_ACTION = "action"
    const val KEY_CALLBACK = "callback"

    const val KEY_HEAP_ALLOC = "heapAlloc"
    const val KEY_HEAP_SIZE = "heapSize"
    const val KEY_HEAP_CAPACITY = "heapCapacity"
    const val KEY_PSS = "pss"
    const val KEY_PSS_LIMIT = "pssLimit"

    const val KEY_REPLY = "reply"

    const val NOTIFY_PHYSICAL = 1000
    const val NOTIFY_JAVA_HEAP = 2000

    const val RESULT_OK = 0
    const val RESULT_FAILED = 1

    const val TRANSACTION_EXCEPTION_REPLY = android.os.IBinder.FIRST_CALL_TRANSACTION

    enum class Kind {
        Trim,
        Kill,
        Unknown,
    }

    /**
     * 同时看 Intent action 与 common.action。
     * 文档示例只注册了 TRIM, 但表格要求另听 KILL; common 里还有 trim/kill 字符串。
     */
    fun kindOf(intentAction: String?, requestedAction: String?): Kind {
        val intent = intentAction.orEmpty()
        val requested = requestedAction.orEmpty().lowercase()
        return when {
            intent == ACTION_KILL || requested == "kill" -> Kind.Kill
            intent == ACTION_TRIM || requested == "trim" -> Kind.Trim
            else -> Kind.Unknown
        }
    }
}
