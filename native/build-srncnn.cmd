@echo off
rem build libsrncnn.so via NDK clang++ directly, no CMake/ndk-build needed.
rem
rem usage: build-srncnn.cmd [NDK_ROOT]
rem   default: ANDROID_NDK_HOME env, else ..\third_party\android-ndk-r27d
rem
rem output: ..\app\src\main\jniLibs\arm64-v8a\libsrncnn.so
rem deps:   ..\app\src\main\jniLibs\arm64-v8a\libncnn.so (vendored, see third_party\README.md)
rem         ..\third_party\ncnn\arm64-v8a\include (ncnn headers)

setlocal
set ROOT=%~dp0..
set NDK=%~1
if "%NDK%"=="" set NDK=%ANDROID_NDK_HOME%
if "%NDK%"=="" set NDK=%ROOT%\third_party\android-ndk-r27d

set CLANG=%NDK%\toolchains\llvm\prebuilt\windows-x86_64\bin\clang++.exe
set SYSROOT=%NDK%\toolchains\llvm\prebuilt\windows-x86_64\sysroot
set ABI=arm64-v8a
set OUT=%ROOT%\app\src\main\jniLibs\%ABI%
set NCNN_INC=%ROOT%\third_party\ncnn\%ABI%\include

if not exist "%CLANG%" (
    echo [x] clang++ not found: %CLANG%
    echo     pass NDK root as arg or set ANDROID_NDK_HOME
    exit /b 1
)
if not exist "%OUT%\libncnn.so" (
    echo [x] libncnn.so missing in %OUT%
    echo     copy it from third_party\ncnn\%ABI%\libncnn.so
    exit /b 1
)

if not exist "%OUT%" mkdir "%OUT%"

"%CLANG%" -shared -fPIC -O2 -fvisibility=hidden ^
    --target=aarch64-linux-android24 ^
    --sysroot="%SYSROOT%" ^
    -I"%NCNN_INC%" ^
    -DANDROID ^
    "%~dp0srncnn.cpp" ^
    -L"%OUT%" -lncnn -ljnigraphics -llog -landroid ^
    -static-libstdc++ ^
    -Wl,-soname,libsrncnn.so ^
    -Wl,-z,max-page-size=16384 ^
    -o "%OUT%\libsrncnn.so"

if errorlevel 1 (
    echo [x] build failed
    exit /b 1
)
echo [v] %OUT%\libsrncnn.so
endlocal
