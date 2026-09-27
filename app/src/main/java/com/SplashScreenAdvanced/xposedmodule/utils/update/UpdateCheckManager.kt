package com.SplashScreenAdvanced.xposedmodule.utils.update

import android.content.Context
import androidx.core.content.edit
import com.SplashScreenAdvanced.xposedmodule.BuildConfig
import com.SplashScreenAdvanced.xposedmodule.utils.update.GitHubReleaseChecker.UpdateChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 更新检查的运行时协调器（仅运行于模块应用进程，绝不进入 hook 目标进程）。
 *
 * 职责：
 * - 串行化检查请求，渠道切换后丢弃过期结果（竞态语义同参考实现的 UpdateCheckCoordinator）。
 * - 自动检查节流：每渠道距上次成功 ≥ 6h 才发起，且每个进程生命周期至多调度一次；
 *   检查结果若在飞行途中渠道被切换则直接丢弃。
 * - 手动检查不受节流限制，且手动请求会顶替排队项。
 *
 * 渠道与时间戳持久化：
 * - 渠道值存于模块远程偏好 [com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences.Module.UPDATE_CHANNEL]
 * - "上次成功检查时间"存本地私有 SP（运行态，不参与备份/远程同步）。
 */
object UpdateCheckManager {

    private const val PREF_FILE = "github_release_updates"
    private const val KEY_LAST_SUCCESS_PREFIX = "last_successful_check_ms_"
    private const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    /** 冷启动后等待的停留时长，避免秒开秒关也打一次网络请求。 */
    private const val AUTO_CHECK_DWELL_MS = 10_000L

    sealed interface UpdateCheckState {
        data object Idle : UpdateCheckState
        data class Checking(val manual: Boolean) : UpdateCheckState
        data class NewVersion(
            val release: GitHubReleaseChecker.ReleaseInfo,
            val channel: UpdateChannel
        ) : UpdateCheckState
        /** 已是最新——只对手动检查投递。 */
        data object Latest : UpdateCheckState
        /** 检查失败——只对手动检查投递。 */
        data object Failed : UpdateCheckState
    }

    data class Request(val channel: UpdateChannel, val manual: Boolean)

    private val _state = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val state: StateFlow<UpdateCheckState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coordinator = UpdateCheckCoordinator()
    private var autoCheckClaimed = false
    /** 手动请求与同渠道在飞请求合并时置位：结果按手动语义投递（Latest/Failed 也提示）。 */
    private var manualObserverWaiting = false
    private var channelProvider: (() -> UpdateChannel)? = null

    /** 绑定渠道读取入口（Compose 侧注入一次即可）。 */
    fun bindChannelProvider(provider: () -> UpdateChannel) {
        channelProvider = provider
    }

    /**
     * 冷启动自动检查：进程内至多调度一次；停留 10s 后若距上次成功 ≥ 6h 才真的发请求。
     * [enabled] 在延迟结束后才读取——用户刚关掉开关时不会补发请求。
     */
    fun maybeAutoCheck(
        context: Context,
        enabled: () -> Boolean,
        channelProvider: () -> UpdateChannel
    ) {
        bindChannelProvider(channelProvider)
        if (autoCheckClaimed || coordinator.isBusy()) return
        autoCheckClaimed = true
        val appContext = context.applicationContext
        scope.launch {
            delay(AUTO_CHECK_DWELL_MS)
            if (!enabled()) return@launch
            val channel = channelProvider()
            val lastSuccess = lastSuccessMs(appContext, channel)
            if (System.currentTimeMillis() - lastSuccess < AUTO_CHECK_INTERVAL_MS) return@launch
            check(appContext, channelProvider, manual = false)
        }
    }

    /** 手动检查：不节流；飞行中的同渠道请求直接复用。 */
    fun checkNow(context: Context, channelProvider: () -> UpdateChannel) {
        bindChannelProvider(channelProvider)
        check(context.applicationContext, channelProvider, manual = true)
    }

    private fun check(context: Context, channelProvider: () -> UpdateChannel, manual: Boolean) {
        val request = coordinator.submit(Request(channelProvider(), manual))
        if (request == null) {
            if (manual) manualObserverWaiting = true
            return
        }
        execute(context, request, channelProvider)
    }

    private fun execute(
        context: Context,
        request: Request,
        channelProvider: () -> UpdateChannel
    ) {
        scope.launch {
            _state.value = UpdateCheckState.Checking(request.manual)
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    GitHubReleaseChecker.fetchLatestRelease(request.channel)
                }
            }
            val completion = coordinator.complete(request.channel, channelProvider())
            if (completion.shouldDeliverResult) {
                val deliverManualFeedback = request.manual || manualObserverWaiting
                manualObserverWaiting = false
                outcome.fold(
                    onSuccess = { release ->
                        recordSuccess(context, request.channel)
                        _state.value = when {
                            GitHubReleaseChecker.isNewerVersion(
                                release.tagName,
                                BuildConfig.VERSION_NAME
                            ) -> UpdateCheckState.NewVersion(release, request.channel)
                            deliverManualFeedback -> UpdateCheckState.Latest
                            else -> UpdateCheckState.Idle
                        }
                    },
                    onFailure = {
                        _state.value = if (deliverManualFeedback) {
                            UpdateCheckState.Failed
                        } else {
                            UpdateCheckState.Idle
                        }
                    }
                )
            }
            completion.nextRequest?.let { execute(context, it, channelProvider) }
        }
    }

    /** UI 消费完结果（dialog 关闭/toast 已显示）后复位。 */
    fun dismiss() {
        _state.value = UpdateCheckState.Idle
    }

    fun isChecking(): Boolean = _state.value is UpdateCheckState.Checking

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private fun lastSuccessMs(context: Context, channel: UpdateChannel): Long =
        prefs(context).getLong(KEY_LAST_SUCCESS_PREFIX + channel.storageValue, 0L)

    private fun recordSuccess(context: Context, channel: UpdateChannel) {
        prefs(context).edit {
            putLong(KEY_LAST_SUCCESS_PREFIX + channel.storageValue, System.currentTimeMillis())
        }
    }
}

/**
 * 串行化更新检查请求，并在用户切换渠道时只保留最后一次手动请求。
 *
 * 网络请求仍由调用方执行；该类只管理当前请求、待执行请求和结果是否仍属于当前渠道，
 * 因而可以在普通 JVM 单元测试中完整覆盖切换竞态。
 */
internal class UpdateCheckCoordinator {

    data class Completion(
        val shouldDeliverResult: Boolean,
        val nextRequest: UpdateCheckManager.Request?
    )

    private var activeRequest: UpdateCheckManager.Request? = null
    private var pendingRequest: UpdateCheckManager.Request? = null

    /** Read-only UI scheduling gate; does not change update request/channel semantics. */
    @Synchronized fun isBusy(): Boolean = activeRequest != null || pendingRequest != null

    /**
     * 返回非 null 表示调用方应立即启动该请求；已有请求运行时，手动请求会覆盖排队项。
     */
    @Synchronized
    fun submit(request: UpdateCheckManager.Request): UpdateCheckManager.Request? {
        if (activeRequest == null) {
            activeRequest = request
            return request
        }
        if (request.manual) {
            pendingRequest = if (activeRequest?.channel == request.channel) null else request
        }
        return null
    }

    /**
     * 完成当前请求。只有完成渠道仍是用户当前选择时才允许展示结果；排队请求也必须与
     * 当前选择一致，否则直接丢弃，避免快速往返切换后启动过期检查。
     */
    @Synchronized
    fun complete(
        completedChannel: UpdateChannel,
        selectedChannel: UpdateChannel
    ): Completion {
        val completedCurrentRequest = activeRequest?.channel == completedChannel
        val shouldDeliver = completedCurrentRequest && completedChannel == selectedChannel
        if (completedCurrentRequest) activeRequest = null

        var next: UpdateCheckManager.Request? = null
        if (activeRequest == null) {
            val pending = pendingRequest
            pendingRequest = null
            if (pending?.channel == selectedChannel) {
                activeRequest = pending
                next = pending
            }
        }
        return Completion(shouldDeliver, next)
    }
}
