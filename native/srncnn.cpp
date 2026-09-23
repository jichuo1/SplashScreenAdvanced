// ncnn 超分 JNI 桥
//
// 只服务离线超分工厂(IconScanService), 不进 SystemUI 进程。
// 模型: waifu2x upconv_7_anime_style_art_rgb scale2.0x (MIT), 输入 blob "Input1",
// 输出 blob "Eltwise4", 固定 2x 上采样, 输入像素域为 0-255 RGB(不归一化)。
//
// 编译: 见同目录 build-srncnn.cmd —— 直接调 NDK clang++, 不依赖 CMake/ndk-build。

#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <mutex>
#include <vector>

#include <ncnn/cpu.h>
#include <ncnn/gpu.h>
#include <ncnn/mat.h>
#include <ncnn/net.h>

#define LOG_TAG "NcnnSr"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_lock;
ncnn::Net* g_net = nullptr;
bool g_gpu_instance = false;
bool g_vulkan = false;

void releaseLocked() {
    delete g_net;
    g_net = nullptr;
    if (g_gpu_instance) {
        ncnn::destroy_gpu_instance();
        g_gpu_instance = false;
    }
    g_vulkan = false;
}

} // namespace

/**
 * 初始化: 创建 Vulkan 实例(可用时) → 加载 param/bin。
 *
 * @return 1 = Vulkan 就绪; 2 = 仅 CPU 可用; 负数 = 失败
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_SplashScreenAdvanced_xposedmodule_utils_sr_NcnnSr_nativeInit(
    JNIEnv* env, jclass, jobject assetManager, jstring paramPath, jstring binPath) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (g_net) return g_vulkan ? 1 : 2;

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) {
        LOGE("AAssetManager_fromJava failed");
        return -1;
    }
    const char* param = env->GetStringUTFChars(paramPath, nullptr);
    const char* bin = env->GetStringUTFChars(binPath, nullptr);
    if (!param || !bin) {
        if (param) env->ReleaseStringUTFChars(paramPath, param);
        if (bin) env->ReleaseStringUTFChars(binPath, bin);
        return -1;
    }

    int ret;
    bool vulkan = false;
    if (ncnn::create_gpu_instance() == 0) {
        g_gpu_instance = true;
        vulkan = ncnn::get_gpu_count() > 0;
    } else {
        LOGE("create_gpu_instance failed, cpu-only");
    }

    ncnn::Net* net = new ncnn::Net();
    net->opt.num_threads = ncnn::get_big_cpu_count();
    if (vulkan) {
        net->opt.use_vulkan_compute = 1;
        net->set_vulkan_device(ncnn::get_default_gpu_index());
    }

    if (net->load_param(mgr, param) != 0 || net->load_model(mgr, bin) != 0) {
        LOGE("model load failed: %s / %s", param, bin);
        delete net;
        releaseLocked();
        ret = -2;
    } else {
        g_net = net;
        g_vulkan = vulkan;
        LOGI("init ok, vulkan=%d", (int)vulkan);
        ret = vulkan ? 1 : 2;
    }

    env->ReleaseStringUTFChars(paramPath, param);
    env->ReleaseStringUTFChars(binPath, bin);
    return ret;
}

/**
 * 单发 2x 超分: src(任意尺寸) → dst(恰好 2w x 2h)。
 * 尺寸不符/推理失败返回 false, 调用方走重采样兜底。
 *
 * alpha 语义: Bitmap ARGB_8888 是预乘域, 网络训练域是 straight-alpha RGB,
 * 因此输入先解预乘, 输出合并时重新预乘; alpha 通道独立做双三次放大
 * (与 waifu2x-ncnn-vulkan 的 alpha 策略一致)。
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_SplashScreenAdvanced_xposedmodule_utils_sr_NcnnSr_nativeEnhance(
    JNIEnv* env, jclass, jobject srcBmp, jobject dstBmp) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (!g_net) return JNI_FALSE;

    AndroidBitmapInfo si, di;
    if (AndroidBitmap_getInfo(env, srcBmp, &si) != ANDROID_BITMAP_RESULT_SUCCESS ||
        si.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;
    if (AndroidBitmap_getInfo(env, dstBmp, &di) != ANDROID_BITMAP_RESULT_SUCCESS ||
        di.format != ANDROID_BITMAP_FORMAT_RGBA_8888)
        return JNI_FALSE;

    const int w = (int)si.width, h = (int)si.height;
    const int w2 = w * 2, h2 = h * 2;
    if (w <= 0 || h <= 0 || (int)di.width != w2 || (int)di.height != h2)
        return JNI_FALSE;

    void* srcPixels = nullptr;
    void* dstPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, srcBmp, &srcPixels) != ANDROID_BITMAP_RESULT_SUCCESS)
        return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, dstBmp, &dstPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, srcBmp);
        return JNI_FALSE;
    }

    const size_t npix = (size_t)w * (size_t)h;
    std::vector<unsigned char> rgba(npix * 4);
    for (int y = 0; y < h; y++)
        std::memcpy(rgba.data() + (size_t)y * w * 4,
                    (const unsigned char*)srcPixels + (size_t)y * si.stride, (size_t)w * 4);

    // 解预乘 → straight RGB + alpha 单通道浮点图
    std::vector<unsigned char> rgb(npix * 3);
    ncnn::Mat aMat(w, h, 1);
    {
        float* ap = aMat;
        for (size_t i = 0; i < npix; i++) {
            const unsigned char* s = rgba.data() + i * 4;
            unsigned char* d = rgb.data() + i * 3;
            const unsigned int a = s[3];
            ap[i] = (float)a;
            if (a == 0) {
                d[0] = d[1] = d[2] = 0;
            } else if (a == 255) {
                d[0] = s[0]; d[1] = s[1]; d[2] = s[2];
            } else {
                d[0] = (unsigned char)std::min(255u, (s[0] * 255u + a / 2) / a);
                d[1] = (unsigned char)std::min(255u, (s[1] * 255u + a / 2) / a);
                d[2] = (unsigned char)std::min(255u, (s[2] * 255u + a / 2) / a);
            }
        }
    }

    ncnn::Mat in = ncnn::Mat::from_pixels(rgb.data(), ncnn::Mat::PIXEL_RGB, w, h);
    ncnn::Mat out;
    bool ok = false;
    {
        ncnn::Extractor ex = g_net->create_extractor();
        if (ex.input("Input1", in) == 0 && ex.extract("Eltwise4", out) == 0)
            ok = true;
    }
    ok = ok && out.c == 3 && out.w == w2 && out.h == h2;

    if (ok) {
        ncnn::Mat a2;
        ncnn::resize_bicubic(aMat, a2, w2, h2);
        ok = a2.w == w2 && a2.h == h2;
        if (ok) {
            std::vector<unsigned char> outRgba((size_t)w2 * h2 * 4);
            out.to_pixels(outRgba.data(), ncnn::Mat::PIXEL_RGB2RGBA); // alpha 暂为 255
            const float* ap2 = a2;
            const size_t npix2 = (size_t)w2 * h2;
            for (size_t i = 0; i < npix2; i++) {
                float av = ap2[i];
                int a = av < 0.f ? 0 : (av > 255.f ? 255 : (int)lroundf(av));
                unsigned char* px = outRgba.data() + i * 4;
                if (a <= 0) {
                    px[0] = px[1] = px[2] = px[3] = 0;
                } else {
                    // 重新预乘回 Bitmap 域
                    px[0] = (unsigned char)((px[0] * a + 127) / 255);
                    px[1] = (unsigned char)((px[1] * a + 127) / 255);
                    px[2] = (unsigned char)((px[2] * a + 127) / 255);
                    px[3] = (unsigned char)a;
                }
            }
            for (int y = 0; y < h2; y++)
                std::memcpy((unsigned char*)dstPixels + (size_t)y * di.stride,
                            outRgba.data() + (size_t)y * w2 * 4, (size_t)w2 * 4);
        }
    }

    AndroidBitmap_unlockPixels(env, dstBmp);
    AndroidBitmap_unlockPixels(env, srcBmp);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_SplashScreenAdvanced_xposedmodule_utils_sr_NcnnSr_nativeRelease(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_lock);
    releaseLocked();
}
