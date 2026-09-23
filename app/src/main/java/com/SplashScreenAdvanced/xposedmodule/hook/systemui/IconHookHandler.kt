package com.SplashScreenAdvanced.xposedmodule.hook.systemui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import com.SplashScreenAdvanced.xposedmodule.data.preference.Preferences
import com.SplashScreenAdvanced.xposedmodule.hook.SystemUIHooker
import com.SplashScreenAdvanced.xposedmodule.hook.base.BaseHookHandler
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.GenerateHookHandler.currentActivityInfo
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.GenerateHookHandler.currentApplicationInfo
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.GenerateHookHandler.currentComponentName
import com.SplashScreenAdvanced.xposedmodule.hook.systemui.GenerateHookHandler.currentPackageName
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.getDevPrefs
import com.SplashScreenAdvanced.xposedmodule.hook.utils.HookExt.printLog
import com.SplashScreenAdvanced.xposedmodule.hook.utils.RemotePreferences.observe
import com.SplashScreenAdvanced.xposedmodule.hook.utils.ReflectCache
import com.SplashScreenAdvanced.xposedmodule.hook.utils.getValueFrom
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.BGColorModes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ChangeBGColorTypes
import com.SplashScreenAdvanced.xposedmodule.ui.page.data.ShrinkIconType
import com.SplashScreenAdvanced.xposedmodule.utils.DeviceUtils.isHyperOS
import com.SplashScreenAdvanced.xposedmodule.utils.IconPackManager
import com.SplashScreenAdvanced.xposedmodule.utils.convertToSquareDrawable
import com.SplashScreenAdvanced.xposedmodule.utils.drawable2Bitmap
import com.SplashScreenAdvanced.xposedmodule.utils.drawableDominantColor
import com.SplashScreenAdvanced.xposedmodule.utils.enhance.IconCacheClient
import com.SplashScreenAdvanced.xposedmodule.utils.enhance.IconEnhanceEngine
import com.SplashScreenAdvanced.xposedmodule.utils.isDarkMode
import com.SplashScreenAdvanced.xposedmodule.utils.XiaomiIconsHelper
import com.SplashScreenAdvanced.xposedmodule.wrapper.NoStrokeAdaptiveIconDrawable
import com.SplashScreenAdvanced.xposedmodule.wrapper.TransparentAdaptiveIconDrawable
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.toClass
import java.util.Collections
import java.util.WeakHashMap

/**
 * 此对象用于处理图标 Hook
 */
object IconHookHandler : BaseHookHandler() {
    var currentIconDominantColor: Int? = null
    private var currentIsNeedShrinkIcon = false

    /**
     * currentUseBigHyperOSLagerIcon 有三种状态:
     *
     * null: 当前没有使用小米大图标
     * true: 当前使用 1x2 或 2x1 或 2x2 的图标
     * false: 当前使用 1x1 的图标
     *
     */
    private var currentUseBigHyperOSLagerIcon: Boolean? = null
    private var currentIconDrawable: Drawable? = null

    /** 当前线程是否正处于 makeSplashScreenContentView 的同步执行区间内 */
    private val isInMakeSplashScreenContentView = ThreadLocal<Boolean>()

    /**
     * 待增强图标的归属信息（Drawable 实例 -> `包名|组件|sourceDir`）
     *
     * 增强实际发生在 `preDrawIcon`，它被宿主 post 到后台线程执行，那时
     * [GenerateHookHandler] 的进程级静态（`currentPackageName` 等）可能已被下一次启动覆盖，
     * 直接读会**串包**（把 A 应用的图标画成 B 的）。这里在构造器阶段（仍在本次启动的同步区间
     * 内）按 drawable 实例记下归属，后台线程用同一个实例取回。
     *
     * 用 `WeakHashMap`：key 是宿主持有的 Drawable，图标被回收时条目自动消失，无需手工清理。
     */
    private val pendingEnhanceTargets =
        Collections.synchronizedMap(WeakHashMap<Drawable, String>())

    /**
     * com.android.internal.R.dimen.starting_surface_icon_size 资源 id (进程内恒定, 解析一次)
     *
     * 解析失败返回 null 而不是抛出: lazy 不缓存异常, 若直接 !! 则每次取尺寸都重抛 NPE;
     * 返回 null 后由 [startingSurfaceIconSizePx] 回退到 AOSP 缺省 160dp
     */
    private val startingSurfaceIconSizeResId by lazy {
        runCatching {
            $$"com.android.internal.R$dimen".toClass(loader = appClassLoader).resolve()
                .firstField { name = "starting_surface_icon_size" }.getValueFrom<Any, Int>(null)
        }.getOrNull()
    }

    private val startingSurfaceIconSizePx by lazy {
        val res = appResources
        val resId = startingSurfaceIconSizeResId
        if (res != null && resId != null) res.getDimensionPixelSize(resId)
        else ((160f * (res?.displayMetrics?.density ?: 3f)) + 0.5f).toInt()
    }

    /**
     * 图标包管理器
     *
     * 图标包包名在 [IconPackManager] 构造时就固定了, 所以**不能**用 `by lazy` 一次性固化:
     * 那样用户在设置里换了图标包也要等 SystemUI 重启才生效。这里改为可失效的缓存,
     * 由 [onHook] 中对 `ICON_PACK_PACKAGE_NAME` 的 observe 负责置空重建
     */
    @Volatile
    private var iconPackManagerCache: IconPackManager? = null

    /**
     * 图标包管理器（惰性构造）
     *
     * @Synchronized: 后台预热线程与启动遮罩路径可能并发进入, 构造本身虽廉价, 但允许重复
     * 实例会让先构造的一方白跑一次 appfilter 解析然后被丢弃
     */
    private val iconPackManager: IconPackManager?
        @Synchronized get() = iconPackManagerCache ?: appContext?.let { ctx ->
            IconPackManager(ctx, prefs.get(Preferences.Icon.ICON_PACK_PACKAGE_NAME))
                .also { iconPackManagerCache = it }
        }

    private val miuiIcons by lazy { XiaomiIconsHelper(appContext!!, appClassLoader) }

    private val iconAnimateListenerClass by lazy {
        $$"android.window.SplashScreenView$IconAnimateListener".toClass(loader = appClassLoader)
    }

    /**
     * 图标主色 LRU 缓存: 主色计算需渲染位图 + Palette 取色, 是启动链路上最贵的纯计算。
     *
     * key 含 sourceDir(应用更新后自然失效); 图标来源相关开关变更时在 [onHook] 注册的 observe 中整体清空
     */
    private val dominantColorCache = object : LinkedHashMap<String, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>): Boolean = size > 128
    }

    private fun clearDominantColorCache() = synchronized(dominantColorCache) { dominantColorCache.clear() }

    /**
     * 重置当前应用的属性
     */
    fun resetCache() {
        currentIconDominantColor = null
        currentIsNeedShrinkIcon = false
        currentUseBigHyperOSLagerIcon = null
        currentIconDrawable = null
    }

    /** 开始 Hook */
    override fun onHook() {
        // 图标来源相关配置变更时清空主色缓存
        Preferences.Icon.ICON_PACK_PACKAGE_NAME.observe(fireImmediately = false) {
            clearDominantColorCache()
            iconPackManagerCache = null  // 图标包换了, 下次取图标时按新包名重建
        }
        Preferences.Icon.ENABLE_USE_MIUI_LARGE_ICON.observe(fireImmediately = false) { clearDominantColorCache() }
        Preferences.Icon.ENABLE_REPLACE_ICON.observe(fireImmediately = false) { clearDominantColorCache() }
        // 档位变更时清增强缓存: key 虽含 lv 不会错配, 但旧档位条目会白占 LRU
        Preferences.Icon.ENHANCE_LEVEL.observe(fireImmediately = false) { IconEnhanceEngine.clearCache() }

        SystemUIHooker.Members.getWindowAttrs.addAfterHook {

            //忽略应用主动设置的图标
            val isDefaultStyle = prefs.get(Preferences.Icon.ENABLE_DEFAULT_STYLE) &&
                    if (prefs.get(Preferences.Scope.IS_DEFAULT_STYLE_LIST_EXCEPTION_MODE))
                        currentPackageName !in prefs.get(Preferences.AppList.DEFAULT_STYLE_LIST)
                    else
                        currentPackageName in prefs.get(Preferences.AppList.DEFAULT_STYLE_LIST)
            if (isDefaultStyle) {
                val attrs = args[1]!!
                ReflectCache.setField(attrs, "mSplashScreenIcon", null)
            }
            printLog { "getWindowAttrs():${if (isDefaultStyle) "" else " not"} ignore set icon" }
        }

        // 处理 Drawable 图标
        SystemUIHooker.Members.getIcon_IconProvider.addAfterHook {
            printLog { "getIcon_IconProvider(): current method is getIcon" }
            result = processIconDrawable(result as Drawable)
        }

        // 执行缩小图标
        SystemUIHooker.Members.createIconDrawable.addBeforeHook {
            if (currentUseBigHyperOSLagerIcon == true) {
                val size = ReflectCache.getField<Int>(instance!!, "mFinalIconSize") ?: 0
                ReflectCache.setField(instance!!, "mFinalIconSize", (size * 1.35).toInt())
                printLog { "createIconDrawable(): execute enlarge icon" }
            } else if (currentIsNeedShrinkIcon) {
                val size = ReflectCache.getField<Int>(instance!!, "mFinalIconSize") ?: 0
                ReflectCache.setField(instance!!, "mFinalIconSize", (size / 1.5).toInt())
                printLog { "createIconDrawable(): execute shrink icon" }
            }
        }

        // 模糊背景 + 圆角
        SystemUIHooker.Members.build_SplashScreenViewBuilder.addAfterHook {
            // 先判开关: 两项功能都不生效时, 不做 mIconView 反射读
            val needBlurBg = prefs.get(Preferences.Icon.SHRINK_ICON) != ShrinkIconType.NotShrinkIcon.ordinal &&
                    prefs.get(Preferences.Icon.ENABLE_ADD_ICON_BLUR_BG) &&
                    currentIsNeedShrinkIcon &&
                    currentUseBigHyperOSLagerIcon != true
            // 不为小米大图标绘制圆角
            val needRoundCorner = prefs.get(Preferences.Display.ENABLE_DRAW_ROUND_CORNER) &&
                    currentUseBigHyperOSLagerIcon != true
            if (!needBlurBg && !needRoundCorner) return@addAfterHook

            val splashScreenView = result as FrameLayout
            val iconView = ReflectCache.getField<ImageView>(splashScreenView, "mIconView")
                ?: return@addAfterHook

            // 创建模糊背景 View —— 纯 View 层实现, 全部走 GPU:
            // 放大图标经圆角轮廓裁剪后由 RenderEffect 模糊向外溢出, 不再预烘软件位图
            // (原 createShadowedIcon 每次启动付一次 ~3MB 位图分配 + saveLayerAlpha/clipPath 软件绘制)
            if (needBlurBg) {
                val blurIconSize = (startingSurfaceIconSizePx / 1.5).toInt()
                // 视图尺寸与原方案一致; 图标经 padding 限定绘制在中央 blurIconSize*2 区域,
                // 四周留白供模糊溢出 (与原 shadowBitmap 中 scaledSize+shadowSize 布局等价)
                val bgIconSize = blurIconSize * 4
                val iconInset = blurIconSize
                val cornerRadius = blurIconSize *
                        getDevPrefs(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE) / 100f

                // 同一 Drawable 实例不能挂到两个 View (bounds 会互相覆盖),
                // 经 constantState 复制一份; 复制失败(罕见)时跳过模糊背景, 不影响遮罩本身
                val blurDrawable = currentIconDrawable?.constantState?.newDrawable()?.mutate()
                if (blurDrawable != null) {
                    val iconBlurBGView = ImageView(appContext).apply {
                        setImageDrawable(blurDrawable)
                        // padding 限定内容区 = 中央 blurIconSize*2 —— FIT_XY 下图标完整
                        // 显示在该区域内 (若直接铺满视图再 clip, 看到的会是图标的中央裁切而非整体)
                        setPadding(iconInset, iconInset, iconInset, iconInset)
                        scaleType = ImageView.ScaleType.FIT_XY
                        alpha = 90f / 255f   // ≈ 原 saveLayerAlpha(90)
                        outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                outline.setRoundRect(
                                    iconInset, iconInset,
                                    view.width - iconInset, view.height - iconInset,
                                    cornerRadius
                                )
                            }
                        }
                        clipToOutline = true
                        setRenderEffect(
                            RenderEffect.createBlurEffect(
                                bgIconSize.toFloat() / 10,
                                bgIconSize.toFloat() / 10,
                                Shader.TileMode.DECAL
                            )
                        )
                        z = -1f
                    }
                    splashScreenView.addView(
                        iconBlurBGView,
                        FrameLayout.LayoutParams(bgIconSize, bgIconSize).apply { gravity = Gravity.CENTER }
                    )
                    iconView.alpha = 0.9f
                    printLog { "build_SplashScreenViewBuilder(): add icon blur bg" }
                }
            }

            // 绘制图标圆角
            if (needRoundCorner) {
                val iconDrawable = ReflectCache.getField<Drawable>(instance!!, "mIconDrawable")
                when {
                    // 没有图标时不绘制圆角
                    iconDrawable == null ->
                        printLog { "build_SplashScreenViewBuilder(): skip round corner, no icon drawable" }
                    // 不为动态图标绘制圆角
                    iconAnimateListenerClass in iconDrawable.javaClass.interfaces ->
                        printLog { "build_SplashScreenViewBuilder(): skip round corner for animated icon" }

                    else -> {
                        // 通过 View 轮廓剪裁绘制圆角
                        val cornerRate = getDevPrefs(Preferences.Dev.DEV_ICON_ROUND_CORNER_RATE)
                        iconView.outlineProvider = object : ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: Outline) {
                                val size = minOf(view.width, view.height)
                                if (size <= 0) return
                                outline.setRoundRect(0, 0, view.width, view.height, size * cornerRate / 100f)
                            }
                        }
                        iconView.clipToOutline = true
                        printLog { "build_SplashScreenViewBuilder(): draw icon round corner" }
                    }
                }
            }
        }

        // 标记 makeSplashScreenContentView 的同步执行区间
        SystemUIHooker.Members.makeSplashScreenContentView.addBeforeHook({ true }) {
            isInMakeSplashScreenContentView.set(true)
        }
        SystemUIHooker.Members.makeSplashScreenContentView.addAfterHook({ true }) {
            isInMakeSplashScreenContentView.remove()
        }

        // 不使用自带的图标缩放, 防止在 HyperOS 上出现图标白边及图标错位
        SystemUIHooker.Members.normalizeAndWrapToAdaptiveIcon.addBeforeHook {
            if (isInMakeSplashScreenContentView.get() == true) {
                printLog { "normalizeAndWrapToAdaptiveIcon(): avoid shrink icon by system ui" }
                val boolShrinkNonAdaptiveIconsIndex = args.indexOfFirst { it is Boolean }
                if (boolShrinkNonAdaptiveIconsIndex != -1) {
                    args(boolShrinkNonAdaptiveIconsIndex).set(false)
                } else {
                    // 经 ReflectCache 解析 (按运行时类缓存, 含父类查找), 避免每次启动重复扫描
                    val normalizer = ReflectCache.invokeMethod<Any>(instance!!, "getNormalizer")
                    val scale = normalizer?.let {
                        ReflectCache.invokeMethod<Float>(
                            it,
                            "getScale",
                            args.first { arg -> arg is Drawable },
                            args.first { arg -> arg is RectF },
                            null,
                            null
                        )
                    } ?: 0.92f
                    (args(args.indexOfFirst { it is FloatArray }).any() as FloatArray)[0] = scale
                    val oriDrawable = args.first { it is Drawable } as Drawable
                    val returnType = SystemUIHooker.Members.normalizeAndWrapToAdaptiveIcon.returnType
                    result = if (returnType == classOf<AdaptiveIconDrawable>())
                        TransparentAdaptiveIconDrawable(oriDrawable)
                    else
                        oriDrawable
                }
            }
        }

        SystemUIHooker.Members.createIconBitmap_BaseIconFactory.addBeforeHook {
            // 必须和 normalizeAndWrapToAdaptiveIcon 一样用 ThreadLocal 精确圈定作用域。
            // 默认条件 isHooking 从 makeSplashScreenContentView 一直持续到 removeStartingWindow
            // (开了最小持续时长可达数百毫秒), 这段时间内 SystemUI 里通知图标 / Recents / QS
            // 对 createIconBitmap 的调用也会被一并替换掉——而本替换忽略了入参 scale,
            // 尺寸也强制用 starting_surface_icon_size, 会污染这些无关路径
            if (isInMakeSplashScreenContentView.get() != true) return@addBeforeHook

            (args(0).any() as? Drawable)?.let { drawable ->
                printLog { "createIconBitmap_BaseIconFactory(): avoid shrink icon by system ui" }
                result = drawable.drawable2Bitmap(getIconSize(drawable))
            }
        }

        // 强制使图标背景被判断为复杂, 以防止安卓抹去简单的图标背景
        SystemUIHooker.Members.iconColor_constructor.addAfterHook {
            ReflectCache.setField(instance!!, "mIsBgComplex", true)
        }

        // 图标画质增强之一: 地基修复
        //
        // 宿主默认把图标栅格化成 starting_surface_default_icon_size(108dp)、再放大到
        // starting_surface_icon_size(160dp) 绘制(1.48x 位图放大), 而高密度设备上 loadInDetail
        // 恒为 false。强制走"按目标尺寸栅格化"分支即可消除这次放大: 矢量图标是无损重渲染,
        // 位图图标也少一次重采样。
        //
        // 这里只改一个布尔入参, 开销为零, 可以安全地留在主线程。
        SystemUIHooker.Members.immobileIconDrawableConstructor.addBeforeHook({
            prefs.get(Preferences.Icon.ENHANCE_LEVEL) > 0
        }) {
            // 构造签名: (Drawable, int srcIconSize, int iconSize, boolean loadInDetail, Handler)
            if (args.size < 4) return@addBeforeHook
            if (args[3] == false) args(3).set(true)

            // 记下这次栅格化的归属, 供后台线程上的 preDrawIcon 取用
            (args[0] as? Drawable)?.let { pendingEnhanceTargets[it] = currentEnhanceTarget() }
        }

        // 图标画质增强之二: 位图重采样
        //
        // 挂在 preDrawIcon 而不是构造器: 宿主把栅格化 post 到了 preDrawHandler(非主线程), 构造器
        // 却运行在 makeSplashScreenContentView 的同步区间里 —— 在那里做 12~40ms 的重采样会直接
        // 推迟启动遮罩的出现。preDrawIcon 既不在主线程, 拿到的 size 也正是最终绘制尺寸。
        //
        // 缓存未命中且引擎返回 null 时保留原 Drawable, 即只享受地基修复; 开关关闭时两者都不挂。
        SystemUIHooker.Members.preDrawIcon_ImmobileIconDrawable.addBeforeHook({
            prefs.get(Preferences.Icon.ENHANCE_LEVEL) > 0
        }) {
            // 签名: preDrawIcon(Drawable, int size)
            if (args.size < 2) return@addBeforeHook
            val src = args[0] as? Drawable ?: return@addBeforeHook
            val targetSize = args[1] as? Int ?: return@addBeforeHook
            if (targetSize <= 0) return@addBeforeHook

            // 取不到归属说明这次栅格化不是本次启动遮罩产生的, 直接放行
            val target = pendingEnhanceTargets.remove(src) ?: return@addBeforeHook
            val pkg = target.substringBefore("|")
            // target 格式 "pkg|component|sourceDir", sourceDir 随机段随应用更新而变,
            // 并入缓存键后"更新 + 重扫"产出的新文件不会再被旧位图命中; 不读 currentApplicationInfo
            // 是因为此刻它可能已被下一次启动覆盖(见 pendingEnhanceTargets 注释)
            val sourceDir = target.substringAfterLast("|").takeIf { it.isNotEmpty() }

            // 优先取离线超分缓存(命中时比实时路径更快), 未命中再走实时引擎
            val enhanced = appContext?.let { ctx -> IconCacheClient.fetch(ctx, pkg, targetSize, sourceDir) }
                ?: IconEnhanceEngine.enhance(src = src, targetSize = targetSize, cacheKey = target)

            enhanced?.let {
                args(0).set(it)
                printLog { "IconEnhanceEngine(): enhanced $pkg @ ${targetSize}px" }
            }
        }

        // 后台预热: 图标包 appfilter.xml 解析 (典型 10~50ms) + 离线缓存 Provider 探活。
        // 前者避免首次启动遮罩在热路径上付解析成本(传空串必然 miss, 只触发 load());
        // 后者避免 openFileDescriptor 在首启遮罩上同步冷启动模块进程(可达 ~0.5-1s)
        val needIconPackWarmup = prefs.get(Preferences.Icon.ICON_PACK_PACKAGE_NAME) != "None"
        val needProviderPrewarm = prefs.get(Preferences.Icon.ENHANCE_LEVEL) > 0
        if (needIconPackWarmup || needProviderPrewarm) {
            Thread({
                runCatching {
                    if (needIconPackWarmup) iconPackManager?.getIconByComponentName("")
                    if (needProviderPrewarm) appContext?.let { IconCacheClient.prewarm(it) }
                }
            }, "SSA-IconWarmup").apply { isDaemon = true }.start()
        }
    }

    /** 本次启动遮罩的归属标识（包名|组件|sourceDir） */
    private fun currentEnhanceTarget(): String =
        "$currentPackageName|$currentComponentName|${currentApplicationInfo?.sourceDir}"

    /**
     * 处理图标 Drawable 的方法
     *
     * 实现功能:
     * - 替换获取图标方式
     * - 不显示 Splash Screen 图标
     * - 使用图标包
     * - 绘制图标圆角
     *
     * @param oriDrawable 原始 Drawable 对象
     * @return 处理后的 Drawable 对象
     */
    fun processIconDrawable(oriDrawable: Drawable): Drawable {
        val shrinkIconType = prefs.get(Preferences.Icon.SHRINK_ICON)

        val isHideSplashScreenIcon = prefs.get(Preferences.Icon.ENABLE_HIDE_SPLASH_SCREEN_ICON) &&
                if (prefs.get(Preferences.Scope.IS_HIDE_SPLASH_SCREEN_ICON_EXCEPTION_MODE))
                    currentPackageName !in prefs.get(Preferences.AppList.HIDE_SPLASH_SCREEN_ICON_LIST)
                else
                    currentPackageName in prefs.get(Preferences.AppList.HIDE_SPLASH_SCREEN_ICON_LIST)

        val iconSize = getIconSize(oriDrawable)

        // 不显示 Splash Screen 图标
        if (isHideSplashScreenIcon) {
            printLog { "getIcon(): draw TRANSPARENT icon" }
            return Color.TRANSPARENT.toDrawable()
        }

        // 检索图标优先级: 使用小米大图标 -> 使用图标包 -> 替换获取图标方式 -> 原始图标
        val iconDrawable = getHyperOSLargeIcon() ?: getIconFromIconPack() ?: replaceWayOfGetIcons() ?: oriDrawable

        // 判断是否需要缩小图标
        when (shrinkIconType) {
            ShrinkIconType.NotShrinkIcon.ordinal -> currentIsNeedShrinkIcon = false
            ShrinkIconType.ShrinkLowResolutionIcon.ordinal -> currentIsNeedShrinkIcon =
                if (iconDrawable !is AdaptiveIconDrawable) iconDrawable.intrinsicWidth < iconSize / 1.5 else false

            ShrinkIconType.ShrinkAllIcon.ordinal -> currentIsNeedShrinkIcon = true
        }
        printLog { "getIcon(): currentIsNeedShrinkIcon: $currentIsNeedShrinkIcon" }

        // 获取图标颜色: 仅当背景颜色取自图标时进行
        if (prefs.get(Preferences.Background.CHANG_BG_COLOR_TYPE) == ChangeBGColorTypes.FromIcon.ordinal) {
            val isLight = when (prefs.get(Preferences.Background.BG_COLOR_MODE)) {
                BGColorModes.DarkColor.ordinal -> false
                BGColorModes.FollowSystem.ordinal -> !appContext!!.isDarkMode
                else -> true
            }
            val cacheKey = "$currentPackageName|$currentComponentName|${currentApplicationInfo?.sourceDir}|$isLight"
            currentIconDominantColor = synchronized(dominantColorCache) { dominantColorCache[cacheKey] }
                ?: iconDrawable.drawableDominantColor(isLight)
                    .also { synchronized(dominantColorCache) { dominantColorCache[cacheKey] = it } }
        }

        // 移除 HyperOS 为自适应图标强制添加的边缘描边
        val finalIconDrawable =
            if (isHyperOS && iconDrawable is AdaptiveIconDrawable && prefs.get(Preferences.Icon.ENABLE_REMOVE_ICON_STROKE)) {
                printLog { "getIcon(): remove icon stroke" }
                NoStrokeAdaptiveIconDrawable.from(iconDrawable)
            } else {
                iconDrawable
            }
        currentIconDrawable = finalIconDrawable
        return finalIconDrawable
    }

    /**
     * 获取 SplashScreen 图标的大小
     *
     * @param drawable 要获取大小的 Drawable 对象
     * @return 图标的大小
     */
    private fun getIconSize(drawable: Drawable): Int {
        val mIconSize = startingSurfaceIconSizePx

        return if (drawable is AdaptiveIconDrawable) (mIconSize * 1.2 + 0.5).toInt()
        else mIconSize
    }

    /** 使用小米大图标 */
    private fun getHyperOSLargeIcon(): Drawable? {
        if (isHyperOS && prefs.get(Preferences.Icon.ENABLE_USE_MIUI_LARGE_ICON) && miuiIcons.hasLargeIcon(currentPackageName)) {
            printLog { "getIcon(): use MIUI Large Icon" }
            return miuiIcons.getLargeIconDrawable(currentPackageName)?.let {
                val largeIconSize = miuiIcons.getLargeIconSize(currentPackageName)
                printLog { "getIcon(): large icon size: $largeIconSize" }
                currentUseBigHyperOSLagerIcon = when (largeIconSize) {
                    "1x2", "2x1", "2x2" -> true
                    "1x1" -> false
                    else -> null
                }

                // 转换成正方形图标
                if (largeIconSize == "1x2" || largeIconSize == "2x1") {
                    it.convertToSquareDrawable()
                } else {
                    it
                }
            }
        }
        return null
    }

    /** 使用图标包 */
    private fun getIconFromIconPack(): Drawable? {
        if (prefs.get(Preferences.Icon.ICON_PACK_PACKAGE_NAME) == "None") return null
        val manager = iconPackManager ?: return null

        printLog { "getIcon(): use Icon Pack" }
        return when {
            currentPackageName == "com.android.contacts" && currentComponentName.isNotEmpty() ->
                manager.getIconByComponentName("ComponentInfo{com.android.contacts/$currentComponentName}")

            currentComponentName.isNotEmpty() ->
                manager.getIconByComponentName("ComponentInfo{$currentPackageName/$currentComponentName}")
                    ?: manager.getIconByPackageName(currentPackageName)

            else -> manager.getIconByPackageName(currentPackageName)
        }
    }

    /**
     * 替换获取图标方式
     *
     * 使用 Context.packageManager.getApplicationIcon() 的方式获取图标
     */
    private fun replaceWayOfGetIcons(): Drawable? {
        if (!prefs.get(Preferences.Icon.ENABLE_REPLACE_ICON)) return null

        printLog { "getIcon(): replace way of getting icon" }
        val pm = appContext!!.packageManager

        // 应用内通过 activity-alias + setComponentEnabledSetting 主动更换图标
        val launchedInfo = currentActivityInfo
        val isSwitchedAliasIcon = launchedInfo != null &&
                launchedInfo.targetActivity != null &&                   // 是 activity-alias
                launchedInfo.icon != 0 &&                                // alias 自带图标
                launchedInfo.icon != (currentApplicationInfo?.icon ?: 0) // 不同于默认应用图标

        return when {
            // 0、应用内主动更换的图标
            isSwitchedAliasIcon -> runCatching { launchedInfo.loadIcon(pm) }.getOrNull() ?: getActivityIconOrApp(pm)

            // 1、处理电话拨号界面（正常情况下已被阶段 0 覆盖）
            currentPackageName == "com.android.contacts" && currentComponentName.isNotEmpty() -> getActivityIconOrApp(pm)

            // 2、在 HyperOS 上尝试获取完美图标
            isHyperOS && miuiIcons.isSupportMIUIModeIcon -> miuiIcons.getFancyIconDrawable(
                currentPackageName,
                appUserId,
                currentApplicationInfo
            )

            // 3、优先使用 ComponentName 获取 Activity 图标, 失败时回退到 Application 图标
            else -> getActivityIconOrApp(pm)
        }
    }

    /**
     * 使用 ComponentName 获取 Activity 图标
     *
     * 失败时回退到 Application 图标
     * */
    fun getActivityIconOrApp(pm: PackageManager): Drawable {
        if (currentComponentName.isNotEmpty()) {
            try {
                return pm.getActivityIcon(ComponentName(currentPackageName, currentComponentName))
            } catch (_: Exception) {
                // 回退到 Application 图标
            }
        }
        return pm.getApplicationIcon(currentPackageName)
    }
}
