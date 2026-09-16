# 发布签名

本模块的发布签名身份**长期固定**：`verify_release_apk.py` 会把 APK 的签名证书指纹与
`ANDROID_SIGNING_CERT_SHA256` 逐字节比对，不一致就拒绝发布。指纹一旦变更，已安装的用户
将无法覆盖升级，只能卸载重装并丢失全部模块配置。

因此：**密钥库生成一次，之后只复用，永不重新生成。**

## 首次配置

```powershell
pwsh .github/release-signing/setup-release-signing.ps1
```

前置条件：

- PowerShell 7+
- `keytool`（随 JDK 提供，需在 PATH 中）
- `gh` 已登录且对本仓库有写权限（若没有，见下方"手工填写"）

脚本会：

1. 交互式读取密码（两次确认，不回显、不落盘、不进命令历史）
2. 在**仓库目录之外**生成 PKCS12 密钥库，默认路径
   `%USERPROFILE%\Documents\AndroidSigning\SplashScreenAdvanced\splash-screen-release.p12`
3. 回读校验密码与别名确实可用
4. 导出证书并计算 SHA-256
5. 把 5 个 Secret 写入 `alpha-release` 与 `stable-release` 两个 GitHub Environment

密钥库已存在时脚本**不会覆盖**，只会重新校验并刷新 Secret。

## 手工填写

`gh` 不可用时：

```powershell
pwsh .github/release-signing/setup-release-signing.ps1 -SkipGitHubSecrets
```

脚本只生成并校验密钥库，然后打印除密码外的所有值，并把 base64 写到临时文件
（几千个字符打到终端会残留在回滚缓冲区里）。复制进 GitHub 后请立即删除该文件。

## 需要的 Secret

两个 Environment（`alpha-release`、`stable-release`）各需以下 5 项：

| Secret | 内容 |
|---|---|
| `ANDROID_SIGNING_KEY_BASE64` | 密钥库文件的 base64 |
| `ANDROID_SIGNING_STORE_PASSWORD` | 密钥库密码 |
| `ANDROID_SIGNING_KEY_ALIAS` | 密钥别名，默认 `splash_screen_release` |
| `ANDROID_SIGNING_KEY_PASSWORD` | 密钥密码（脚本生成时与库密码相同） |
| `ANDROID_SIGNING_CERT_SHA256` | 证书 SHA-256，64 位小写十六进制 |

用 Environment 而非仓库级 Secret，是为了能对发布环境单独加审批与分支限制。

## 备份

密钥库与密码分别存放：

- 密钥库文件 → 离线介质，至少两份异地
- 密码 → 密码管理器

两者任一丢失，本模块就再也发不出能覆盖升级的版本了。这不是可以事后补救的事情。

## 本地构建

日常 `assembleDebug` 不需要任何签名配置。只有 `assembleRelease` 这类真正产包的任务才要求
完整签名身份，缺失时 `app/build.gradle.kts` 里的守卫会直接失败，而不是悄悄产出未签名包。
本地确实需要打签名包时，通过环境变量提供：

```
SPLASH_SIGNING_STORE_FILE
SPLASH_SIGNING_STORE_PASSWORD
SPLASH_SIGNING_KEY_ALIAS
SPLASH_SIGNING_KEY_PASSWORD
```

或用对应的 `splashScreen.signing.*` Gradle 属性。两者都不要写进仓库内的任何文件。
