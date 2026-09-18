package com.SplashScreenAdvanced.xposedmodule.fairmemory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import com.SplashScreenAdvanced.xposedmodule.fairmemory.FairMemoryProtocol.Kind
import com.SplashScreenAdvanced.xposedmodule.ui.component.clearAppIconCache
import com.SplashScreenAdvanced.xposedmodule.ui.component.clearInstalledAppsCache
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog

/**
 * 接入公平运行内存: 听 TRIM / KILL, 3 秒内释放或备份, 再经 callback Binder 回执。
 *
 * 只装在模块 App 进程。SystemUI 里的 Hook 不属于本应用的高优进程集合, 不能在宿主里注册这些广播。
 */
object FairMemoryController {
    private const val TAG = "FairMemory"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        synchronized(this) {
            if (installed) return
            val app = context.applicationContext
            val thread = HandlerThread(TAG).apply { start() }
            val handler = Handler(thread.looper)
            val filter = IntentFilter().apply {
                addAction(FairMemoryProtocol.ACTION_TRIM)
                addAction(FairMemoryProtocol.ACTION_KILL)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter, null, handler)
            }
            installed = true
            XMLog.i(TAG) { "fair-memory receiver installed" }
        }
    }

    fun onAndroidTrimMemory(level: Int) {
        // UI_HIDDEN=20, BACKGROUND+=40。10/15 是仍可能下发的 running low/critical。
        if (level >= 20 || level == 10 || level == 15) {
            trimLocalCaches("android:$level")
        }
    }

    fun trimLocalCaches(reason: String) {
        clearAppIconCache()
        clearInstalledAppsCache()
        XMLog.i(TAG) { "trimmed local caches ($reason)" }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras ?: run {
                XMLog.w(TAG) { "fair-memory intent has no extras, action=${intent.action}" }
                return
            }
            val common = extras.getBundle(FairMemoryProtocol.BUNDLE_COMMON) ?: run {
                XMLog.w(TAG) { "fair-memory missing common bundle" }
                return
            }
            val notifyType = common.getInt(FairMemoryProtocol.KEY_NOTIFY_TYPE)
            val notifyId = common.getInt(FairMemoryProtocol.KEY_NOTIFY_ID)
            val reason = common.getString(FairMemoryProtocol.KEY_REASON)
            val requestedAction = common.getString(FairMemoryProtocol.KEY_ACTION)
            val callback = common.getBinder(FairMemoryProtocol.KEY_CALLBACK)
            val extra = extras.getBundle(FairMemoryProtocol.BUNDLE_EXTRA)
            val kind = FairMemoryProtocol.kindOf(intent.action, requestedAction)

            XMLog.i(TAG) {
                buildString {
                    append("received kind=$kind notifyType=$notifyType notifyId=$notifyId")
                    append(" reason=$reason action=$requestedAction")
                    extra?.let {
                        val heap = it.getInt(FairMemoryProtocol.KEY_HEAP_ALLOC, it.getInt(FairMemoryProtocol.KEY_HEAP_SIZE))
                        append(" heapAlloc=$heap")
                        append(" heapCapacity=${it.getInt(FairMemoryProtocol.KEY_HEAP_CAPACITY)}")
                        append(" pss=${it.getInt(FairMemoryProtocol.KEY_PSS)}")
                        append(" pssLimit=${it.getInt(FairMemoryProtocol.KEY_PSS_LIMIT)}")
                    }
                }
            }

            val result = runCatching {
                when (kind) {
                    Kind.Trim -> {
                        trimLocalCaches(reason ?: "trim")
                        FairMemoryProtocol.RESULT_OK
                    }
                    Kind.Kill -> {
                        trimLocalCaches(reason ?: "kill")
                        FairMemorySessionStore.persistForKill(context)
                        FairMemoryProtocol.RESULT_OK
                    }
                    Kind.Unknown -> FairMemoryProtocol.RESULT_FAILED
                }
            }.getOrElse { t ->
                XMLog.e(TAG, t) { "fair-memory handle failed" }
                FairMemoryProtocol.RESULT_FAILED
            }

            if (callback == null) {
                XMLog.w(TAG) { "fair-memory callback binder missing, skipped reply" }
                return
            }
            reply(
                callback = callback,
                notifyType = notifyType,
                notifyId = notifyId,
                result = result,
                extra = Bundle().apply {
                    putString(
                        FairMemoryProtocol.KEY_REPLY,
                        if (result == FairMemoryProtocol.RESULT_OK) kind.name.lowercase() else "failed"
                    )
                }
            )
        }
    }

    private fun reply(
        callback: IBinder,
        notifyType: Int,
        notifyId: Int,
        result: Int,
        extra: Bundle,
    ) {
        val data = Parcel.obtain()
        val replyParcel = Parcel.obtain()
        try {
            data.writeInt(notifyType)
            data.writeInt(notifyId)
            data.writeInt(result)
            data.writeBundle(extra)
            callback.transact(
                FairMemoryProtocol.TRANSACTION_EXCEPTION_REPLY,
                data,
                replyParcel,
                IBinder.FLAG_ONEWAY
            )
            replyParcel.readException()
        } catch (t: Throwable) {
            XMLog.e(TAG, t) { "fair-memory reply failed notifyId=$notifyId" }
        } finally {
            replyParcel.recycle()
            data.recycle()
        }
    }
}
