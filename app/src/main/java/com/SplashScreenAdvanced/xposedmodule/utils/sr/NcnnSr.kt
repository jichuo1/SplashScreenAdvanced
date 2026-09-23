package com.SplashScreenAdvanced.xposedmodule.utils.sr

import android.content.Context
import android.graphics.Bitmap
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog

/**
 * ncnn 学习型超分（仅离线超分工厂使用）
 *
 * ## 边界
 * - 模型为 waifu2x `upconv_7_anime_style_art_rgb scale2.0x`（MIT, ~1.1MB）, 固定 2x 上采样;
 *   输入像素域 0-255 RGB, alpha 由 native 侧独立双三次放大并重新预乘;
 * - 后端优先 Vulkan, 无 Vulkan 设备自动落 ncnn CPU 多线程; 原生侧全程互斥串行;
 * - **不进 SystemUI**: 只有 [IconScanService] 会调 [init], 实时路径不加载此对象;
 * - 所有失败（库缺失 / Vulkan 初始化失败 / 模型损坏 / 推理异常）都归一为
 *   `null`/`false`, 调用方透明回退到 AGSL/CPU 重采样管线;
 * - JNI 桥为 `native/srncnn.cpp`, 由 `native/build-srncnn.cmd` 编译。
 */
internal object NcnnSr {

    private const val LIB_NAME = "srncnn"
    private const val PARAM_ASSET = "sr/sr_x2.param"
    private const val BIN_ASSET = "sr/sr_x2.bin"

    private external fun nativeInit(assetManager: android.content.res.AssetManager, paramPath: String, binPath: String): Int
    private external fun nativeEnhance(src: Bitmap, dst: Bitmap): Boolean
    private external fun nativeRelease()

    /**
     * 0 = 未初始化; 1 = Vulkan; 2 = 仅 CPU; <0 = 失败(不可重试——模型/库问题重试无意义,
     * 每轮扫描开始时由 [init] 统一尝试一次)
     */
    @Volatile
    private var state = 0

    val isReady: Boolean get() = state > 0

    /** 诊断用: "vulkan" / "cpu" / "unavailable" */
    val backendName: String
        get() = when (state) {
            1 -> "vulkan"
            2 -> "cpu"
            0 -> "uninit"
            else -> "unavailable"
        }

    /**
     * 加载 native 库并初始化模型; 重复调用幂等。
     * 必须在 [IconScanService] 的扫描线程上调用（模型加载 ~数十毫秒, 不进主线程）。
     */
    @Synchronized
    fun init(context: Context): Boolean {
        if (state != 0) return state > 0
        state = runCatching {
            System.loadLibrary(LIB_NAME)
            nativeInit(context.assets, PARAM_ASSET, BIN_ASSET)
        }.getOrElse {
            XMLog.e(t = it) { "NcnnSr: native init failed" }
            -1
        }
        XMLog.i { "NcnnSr: backend=$backendName" }
        return state > 0
    }

    /**
     * 单发 2x 超分: 输出边长恰为输入的 2 倍; 失败返回 `null`。
     * 互斥串行 —— 原生侧 ncnn::Net 非线程安全, 且扫描本就单线程顺序执行。
     */
    @Synchronized
    fun enhance(src: Bitmap): Bitmap? {
        if (state <= 0 || src.width <= 0 || src.height <= 0) return null
        val dst = Bitmap.createBitmap(src.width * 2, src.height * 2, Bitmap.Config.ARGB_8888)
        val ok = runCatching { nativeEnhance(src, dst) }.getOrElse {
            XMLog.e(t = it) { "NcnnSr: enhance failed" }
            false
        }
        return if (ok) dst else {
            dst.recycle()
            null
        }
    }
}
