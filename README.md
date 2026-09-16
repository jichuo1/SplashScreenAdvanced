# SplashScreenAdvanced

为 Android 的 Splash Screen（启动遮罩）提供自定义选项的 Xposed 模块。

[![License](https://img.shields.io/github/license/jichuo1/SplashScreenAdvanced)](LICENSE)
[![Xposed](https://img.shields.io/badge/-Xposed-green?style=flat&logo=Android&logoColor=white)](https://github.com/libxposed)

> [!NOTE]
> 本项目是 [GSWXXN/RestoreSplashScreen](https://github.com/GSWXXN/RestoreSplashScreen) 的修改版，
> 以 AGPL-3.0 继续分发。详见下方[出处与许可](#出处与许可)。

## 功能

- 为所有应用显示原生 Splash Screen 界面
- 对主动适配 Splash Screen 的应用改用默认静态图标
- 替换图标获取方式，使 Splash Screen 图标与桌面图标一致（可配合图标包与主题）
- 从图标取色、莫奈取色或自定义颜色来替换背景
- 逐应用单独配置背景颜色、最小持续时长、是否移除底部 Branding Image
- 彻底关闭 Splash Screen

## 要求

| 项 | 要求 |
|---|---|
| Android | 14 及以上（`minSdk 34`） |
| Xposed 框架 | 实现 [libxposed](https://github.com/libxposed) API 102 的框架，如较新版本的 LSPosed |
| 适配重点 | MIUI / HyperOS 与 ColorOS 有专门分支，其余系统走 AOSP 通用路径 |

## 使用

1. 在 Xposed 管理器中激活模块
2. 作用域勾选 **系统界面**（`com.android.systemui`）；若要使用「强制显示遮罩」「彻底关闭 Splash Screen」「热启动遮罩」，还需勾选 **系统框架**（`android`）
3. 重启系统界面；勾选了系统框架的需要重启手机

模块支持热重载，多数设置改完立即生效，无需重启。

## 构建

```bash
git clone https://github.com/jichuo1/SplashScreenAdvanced.git
cd SplashScreenAdvanced
./gradlew :app:assembleDebug
```

需要 JDK 21 与 Android SDK Platform 37。签名发布走 CI，见 `.github/workflows/`。

## 反馈

提交 [Issue](https://github.com/jichuo1/SplashScreenAdvanced/issues) 时请附上：

- Android 版本、ROM 及版本号、Xposed 框架及版本
- 复现步骤
- 模块日志（设置内开启「启用日志」后复现，日志 24 小时自动关闭）与 Xposed 框架日志

## 出处与许可

本项目基于 [GSWXXN/RestoreSplashScreen](https://github.com/GSWXXN/RestoreSplashScreen)（作者 GSWXXN）修改而来，
原作品以 GNU Affero General Public License v3.0 授权。

本仓库同样以 [AGPL-3.0](LICENSE) 授权，并已对原作品作出修改，包括但不限于：

- 更换模块包名与项目身份为 `com.SplashScreenAdvanced.xposedmodule`
- Hook 层的异常隔离、反射缓存、内存泄漏与线程安全修复
- 模块设置界面的重组开销与图标加载优化
- 替换发布流水线

原作者的个人品牌素材、社区链接与捐赠信息已移除，因为它们指向的是原作者本人而非本项目。

## 致谢

- [GSWXXN/RestoreSplashScreen](https://github.com/GSWXXN/RestoreSplashScreen) —— 本项目的上游
- [libxposed](https://github.com/libxposed) —— 模块 API 与服务
- [KavaRef](https://github.com/HighCapable/KavaRef) —— 运行期反射定位
- [miuix](https://github.com/miuix-kotlin-multiplatform/miuix) 与 [hyperx-compose](https://github.com/YuKongA/hyperx-compose) —— 设置界面
- [Koin](https://github.com/InsertKoinIO/koin) —— 依赖注入
- 获取应用列表的思路参考 [Hide My Applist](https://github.com/Dr-TSNG/Hide-My-Applist)
- 备份恢复逻辑改自 [MiuiHome_R](https://github.com/qqlittleice/MiuiHome_R)
