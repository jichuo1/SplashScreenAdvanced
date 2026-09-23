package com.SplashScreenAdvanced.xposedmodule.utils.enhance

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import com.SplashScreenAdvanced.xposedmodule.utils.XMLog
import java.time.Duration
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * GPU 重采样内核（AGSL RuntimeShader + 离屏 HardwareRenderer 管线）
 *
 * 与 CPU 内核 [Resampler] 实现同一套算法 —— 4-tap Mitchell 分离卷积 + 边缘感知
 * Unsharp Mask —— 但整条链跑在 GPU 上:
 *
 * ```
 * Drawable ──pass0──▶ 硬件位图 ──pass1──▶ H 卷积 ──pass2──▶ V 卷积 ──pass3──▶ 锐化 ──▶ 软件 Bitmap
 *   (栅格化)          (零拷贝传递: Bitmap.wrapHardwareBuffer → BitmapShader 作为下一趟输入)
 * ```
 *
 * alpha 处理: AGSL 全程工作在**预乘域** —— `shader.eval` 对预乘位图返回预乘色,
 * `main` 的输出也按预乘解释(官方文档语义), 因此 CPU 版的 premultiply/unpremultiply
 * 两趟全都不用做。
 *
 * ## 可靠性边界
 *
 * - 所有 GPU 对象 (`HardwareRenderer`/`RenderNode`) 绑定在一个带 Looper 的专用
 *   [HandlerThread] 上, 调用方线程阻塞等待结果(限时 [TASK_TIMEOUT_MS]);
 * - 首次调用先做一趟 2x2 探针渲染, 管线不可用(驱动/AGSL 编译失败)即永久回退;
 * - 运行期连续失败 [MAX_CONSECUTIVE_FAILS] 次后同样永久回退 —— 防住 SystemUI
 *   长生命周期里反复付失败的代价;
 * - 任何路径失败都返回 `null`, 由调用方退回 CPU 内核, 语义与 CPU 失败完全一致。
 */
internal object GpuResampler {

    private const val TASK_TIMEOUT_MS = 3000L
    private const val MAX_CONSECUTIVE_FAILS = 3

    /** GPU 工作线程: HardwareRenderer 要求 Looper 线程, 且 GL 状态必须串行访问 */
    private val worker = HandlerThread("SSA-GpuResample").apply {
        isDaemon = true
        start()
    }
    private val handler = Handler(worker.looper)

    private enum class State { UNKNOWN, OK, FAILED }

    @Volatile
    private var state = State.UNKNOWN

    /** 连续失败计数: 探针成功后偶发的单趟失败不立刻判死, 连续超限才永久回退 */
    private val consecutiveFails = AtomicInteger(0)

    // ---------------------------------------------------------------- AGSL 程序

    /**
     * Mitchell-Netravali 分离卷积（水平/垂直共用一个程序, 由 `axis` 选择方向）
     *
     * 4-tap 手写展开而非 for 循环: 常量界循环在 AGSL 里虽被允许, 展开写法对各家
     * ROM 的着色器编译器零风险, 且每趟只算一次权重。
     */
    private const val MITCHELL_SHADER = """
        uniform shader src;
        uniform float2 axis;
        uniform float srcLen;
        uniform float dstLen;

        float mitchell(float x) {
            float b = 1.0 / 3.0;
            float c = 1.0 / 3.0;
            float ax = abs(x);
            if (ax < 1.0) {
                return ((12.0 - 9.0*b - 6.0*c) * ax*ax*ax
                        + (-18.0 + 12.0*b + 6.0*c) * ax*ax
                        + (6.0 - 2.0*b)) / 6.0;
            }
            if (ax < 2.0) {
                return ((-b - 6.0*c) * ax*ax*ax
                        + (6.0*b + 30.0*c) * ax*ax
                        + (-12.0*b - 48.0*c) * ax
                        + (8.0*b + 24.0*c)) / 6.0;
            }
            return 0.0;
        }

        half4 main(float2 coord) {
            // coord 已是像素中心坐标(AGSL fragCoord 约定), 等价 CPU 版 (x + 0.5)
            float c0 = dot(coord, axis);
            float center = c0 * (srcLen / dstLen) - 0.5;
            float base = floor(center);
            float frac = center - base;
            float last = srcLen - 1.0;

            float s0 = clamp(base - 1.0, 0.0, last);
            float s1 = clamp(base,       0.0, last);
            float s2 = clamp(base + 1.0, 0.0, last);
            float s3 = clamp(base + 2.0, 0.0, last);
            float w0 = mitchell(-1.0 - frac);
            float w1 = mitchell(0.0 - frac);
            float w2 = mitchell(1.0 - frac);
            float w3 = mitchell(2.0 - frac);

            half4 acc =
                src.eval(coord + axis * (s0 + 0.5 - c0)) * half(w0) +
                src.eval(coord + axis * (s1 + 0.5 - c0)) * half(w1) +
                src.eval(coord + axis * (s2 + 0.5 - c0)) * half(w2) +
                src.eval(coord + axis * (s3 + 0.5 - c0)) * half(w3);
            // 权重归一化, 与 CPU LUT 行为一致, 防浮点误差带来的亮度漂移
            return acc / half(w0 + w1 + w2 + w3);
        }
    """

    /**
     * 边缘感知 Unsharp Mask（与 CPU 版同式: `dst = src + amount × mask × (src − blur)`）
     *
     * CPU 版的盒模糊是分趟实现的; GPU 上单趟 9-tap 采样代价可忽略, 直接内联 3x3。
     * 边缘采样依赖 BitmapShader CLAMP 边缘复制, 与 CPU 的边界处理语义一致。
     */
    private const val UNSHARP_SHADER = """
        uniform shader src;
        uniform float amount;
        uniform float tau;

        half4 main(float2 coord) {
            half4 p = src.eval(coord);
            if (p.a <= 0.0) return half4(0.0);

            half3 blur =
                src.eval(coord + float2(-1.0, -1.0)).rgb +
                src.eval(coord + float2( 0.0, -1.0)).rgb +
                src.eval(coord + float2( 1.0, -1.0)).rgb +
                src.eval(coord + float2(-1.0,  0.0)).rgb +
                src.eval(coord).rgb +
                src.eval(coord + float2( 1.0,  0.0)).rgb +
                src.eval(coord + float2(-1.0,  1.0)).rgb +
                src.eval(coord + float2( 0.0,  1.0)).rgb +
                src.eval(coord + float2( 1.0,  1.0)).rgb;
            blur /= 9.0;

            half3 diff = p.rgb - blur;
            float mag = (abs(diff.r) + abs(diff.g) + abs(diff.b)) / 3.0;
            float k = amount * min(1.0, mag / tau);
            return half4(p.rgb + diff * half(k), p.a);
        }
    """

    /**
     * AGSL 编译产物全程只建一次 —— RuntimeShader 构造即触发 AGSL→SkSL 编译,
     * 逐趟重建会让每次增强白付 2~3 次编译; 所有访问都串行在 worker 线程上, 无并发问题
     */
    private val mitchellShader by lazy { RuntimeShader(MITCHELL_SHADER) }
    private val unsharpShader by lazy { RuntimeShader(UNSHARP_SHADER) }

    // ---------------------------------------------------------------- 对外入口

    /**
     * 尝试在 GPU 上完成 栅格化 → Mitchell 重采样 → 锐化 全链
     *
     * @param src           源 Drawable（在 GPU 工作线程上栅格化, bounds 会还原）
     * @param rasterSize    栅格化边长（源原生尺寸与目标尺寸的较小者, 由调用方算好）
     * @param dstSize       目标边长
     * @param sharpenAmount 锐化强度; <=0 时跳过锐化趟（调用方保证仅放大时传非 0）
     * @param sharpenTau    边缘掩码阈值, 0..1 域（CPU 版 SHARPEN_TAU/255）
     * @return 目标尺寸的软件 Bitmap; `null` = 不可用或失败, 调用方回退 CPU
     */
    fun enhance(
        src: Drawable,
        rasterSize: Int,
        dstSize: Int,
        sharpenAmount: Float,
        sharpenTau: Float,
    ): Bitmap? {
        if (state == State.FAILED) return null
        return try {
            val task = FutureTask {
                doEnhance(src, rasterSize, dstSize, sharpenAmount, sharpenTau)
            }
            handler.post(task)
            task.get(TASK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            noteFailure()
            XMLog.e(t = t) { "GpuResampler: task failed" }
            null
        }
    }

    // ---------------------------------------------------------------- 管线内部
    // 以下全部运行在 worker 线程上（FutureTask 内）

    private fun doEnhance(
        src: Drawable, rasterSize: Int, dstSize: Int, sharpen: Float, tau: Float,
    ): Bitmap? {
        if (state == State.UNKNOWN && !probe()) {
            state = State.FAILED
            return null
        }

        val renderer = HardwareRenderer()
        try {
            // pass0: 栅格化 —— Drawable 直接画进录制 Canvas, 省掉软件位图与 IntArray
            var cur = renderPass(renderer, rasterSize, rasterSize) { canvas ->
                val original = Rect(src.bounds)
                src.setBounds(0, 0, rasterSize, rasterSize)
                src.draw(canvas)
                src.bounds = original
            } ?: return fail()

            // pass1/2: Mitchell 分离卷积（仅确实需要放大时, 与 CPU 版同条件）
            if (rasterSize < dstSize) {
                cur = convolve(renderer, cur, rasterSize, dstSize, horizontal = true)
                    ?: return fail()
                cur = convolve(renderer, cur, rasterSize, dstSize, horizontal = false)
                    ?: return fail()
            }

            // pass3: 边缘感知锐化
            if (sharpen > 0f) {
                cur = sharpenPass(renderer, cur, dstSize, sharpen, tau) ?: return fail()
            }

            // 唯一一次回读: 硬件位图 → 软件 Bitmap (Bitmap 内部存储本就是预乘域)
            val result = cur.copy(Bitmap.Config.ARGB_8888, false)
            cur.recycle()
            return if (result != null) {
                noteSuccess()
                result
            } else fail()
        } finally {
            renderer.destroy()
        }
    }

    /**
     * 渲染一趟离屏 pass 并把结果包装成硬件 Bitmap
     *
     * `wrapHardwareBuffer` 产出的 Bitmap 持有 buffer 引用 —— 故**不主动 close**
     * [HardwareBuffer], 由 Bitmap 回收时随 GC 释放; 每趟新建 ImageReader/HardwareRenderer
     * 表面换来的是零拷贝链路, 值得。
     */
    private fun renderPass(
        renderer: HardwareRenderer, w: Int, h: Int, record: (Canvas) -> Unit,
    ): Bitmap? {
        val reader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        try {
            val node = RenderNode("SSA-Resample")
            node.setPosition(0, 0, w, h)
            record(node.beginRecording(w, h))
            node.endRecording()
            renderer.setContentRoot(node)
            renderer.setSurface(reader.surface)
            renderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()

            val image = reader.acquireLatestImage() ?: return null
            try {
                // syncAndDraw(setWaitForPresent=true) 已等待呈现完成, 这里只是兜底;
                // fence 正常已就绪, await 立即返回
                runCatching { image.fence.await(Duration.ofMillis(500)) }
                val hw = image.hardwareBuffer ?: return null
                return Bitmap.wrapHardwareBuffer(hw, null)
            } finally {
                image.close()
            }
        } finally {
            reader.close()
        }
    }

    /** Mitchell 分离卷积的一趟（axis 选择采样方向） */
    private fun convolve(
        renderer: HardwareRenderer,
        srcBitmap: Bitmap,
        srcLen: Int,
        dstLen: Int,
        horizontal: Boolean,
    ): Bitmap? {
        val shader = mitchellShader.apply {
            setInputShader(
                "src",
                BitmapShader(srcBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
            setFloatUniform("axis", if (horizontal) 1f else 0f, if (horizontal) 0f else 1f)
            setFloatUniform("srcLen", srcLen.toFloat())
            setFloatUniform("dstLen", dstLen.toFloat())
        }
        val dstW = if (horizontal) dstLen else srcBitmap.width
        val dstH = if (horizontal) srcBitmap.height else dstLen
        return renderPass(renderer, dstW, dstH) { canvas ->
            canvas.drawRect(
                0f, 0f, dstW.toFloat(), dstH.toFloat(),
                Paint().apply { this.shader = shader },
            )
        }.also { srcBitmap.recycle() }
    }

    /** 边缘感知锐化趟 */
    private fun sharpenPass(
        renderer: HardwareRenderer,
        srcBitmap: Bitmap,
        size: Int,
        amount: Float,
        tau: Float,
    ): Bitmap? {
        val shader = unsharpShader.apply {
            setInputShader(
                "src",
                BitmapShader(srcBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
            setFloatUniform("amount", amount)
            setFloatUniform("tau", tau)
        }
        return renderPass(renderer, size, size) { canvas ->
            canvas.drawRect(
                0f, 0f, size.toFloat(), size.toFloat(),
                Paint().apply { this.shader = shader },
            )
        }.also { srcBitmap.recycle() }
    }

    /** 探针: 强制编译两个 AGSL 程序 + 一趟 2x2 渲染, 管线不可用在这里暴露 */
    private fun probe(): Boolean = runCatching {
        // 触发 lazy 编译 —— 着色器编译器异常应在探针期就判死, 而不是留到首个真实任务
        mitchellShader; unsharpShader
        val renderer = HardwareRenderer()
        try {
            renderPass(renderer, 2, 2) { it.drawColor(Color.TRANSPARENT) } != null
        } finally {
            renderer.destroy()
        }
    }.getOrDefault(false)

    // ---------------------------------------------------------------- 失败记账

    private fun fail(): Bitmap? {
        noteFailure()
        return null
    }

    private fun noteFailure() {
        if (consecutiveFails.incrementAndGet() >= MAX_CONSECUTIVE_FAILS) {
            state = State.FAILED
            XMLog.e { "GpuResampler: $MAX_CONSECUTIVE_FAILS consecutive failures, falling back to CPU permanently" }
        }
    }

    /** 成功清零（在 [doEnhance] 返回值处调用方已处理 —— 见 enhance 的 FutureTask 包装） */
    private fun noteSuccess() = consecutiveFails.set(0)
}
