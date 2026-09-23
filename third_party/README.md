# Vendored third-party components

## ncnn (Tencent) — Apache-2.0

- Source: https://github.com/Tencent/ncnn , release `20260526`, asset `ncnn-20260526-android-vulkan-shared.zip`
- Used: `ncnn/arm64-v8a/libncnn.so` (Vulkan-enabled shared build) → copied to `app/src/main/jniLibs/arm64-v8a/`
- Used: `ncnn/arm64-v8a/include/` (public headers, compile-time only)
- License: Apache License 2.0 (see ncnn repo `LICENSE.txt`)

## SR model — waifu2x upconv_7 (anime_style_art_rgb, scale2.0x) — MIT

- Source: https://github.com/nihui/waifu2x-ncnn-vulkan , `models/models-upconv_7_anime_style_art_rgb/scale2.0x_model.{param,bin}`
- Weights originally from https://github.com/nagadomi/waifu2x (MIT)
- Vendored as `app/src/main/assets/sr/sr_x2.param` + `sr_x2.bin`
- License: MIT (see waifu2x / waifu2x-ncnn-vulkan `LICENSE`)

## Android NDK r27d — Android SDK license (build tool only, not shipped)

- `android-ndk-r27d/` extracted from https://dl.google.com/android/repository/android-ndk-r27d-windows.zip
- Only `toolchains/llvm` is extracted; used solely to compile `native/srncnn.cpp` → `libsrncnn.so`
- Not shipped in the APK
