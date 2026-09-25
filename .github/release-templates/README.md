# Release 文案规范

从 `v1.0.5` 之后，GitHub Release 统一使用本目录中的固定模板：

- Stable 正式版：`stable.md`
- Alpha 测试版：`alpha.md`

## 固定规则

1. 标题只使用版本号：Stable 为 `vX.Y.Z`，Alpha 为 `vX.Y.Z-alpha.N`。
2. 正文结构固定：居中标题区（英文 Emoji 标题 Stable 🧬 / Alpha 🧪 ＋斜体副标）、一句概述
   （Stable）或 WARNING 风险提示（Alpha）、自动变更记录、分隔线后的下载与反馈两小节、
   居中小字脚注；安装说明用 `> [!TIP]` 提示框承载。不写 README 中的功能介绍与项目信条，
   不堆砌兼容性、已知问题、验证结果等长章节。
3. “版本概述”只写 2～4 句，说明本版本解决了什么问题，不堆砌实现细节。
4. 每项更新以用户可感知的结果开头，必要时再补充技术原因。
5. Stable 必须包含版本概述与 APK SHA-256；兼容性、固定签名证书指纹与验证细节由附件
   `BUILD_INFO.txt`、`SHA256SUMS.txt` 和仓库文档承载。
6. Alpha 必须保留 WARNING 风险提示与回滚说明，并明确不会作为稳定更新推送。
7. Alpha Release 上传 APK、`BUILD_INFO.txt` 与 `SHA256SUMS.txt`；APK 文件名必须包含
   完整版本号和源码短 SHA。
8. 发布前删除模板中的填写提示，并确认没有残留 `{{TOKEN}}` 占位符。
9. Alpha/Stable 工作流通过 `generate_release_notes.py` 自动生成“更新内容”，详见下方“自动更新说明”。
10. Alpha 标签必须使用 `gradle.properties` 中稳定基础版本的下一个补丁前缀，例如
    `1.0.6` 对应 `v1.0.7-alpha.N`；工作流会把完整 Alpha 版本写入 APK，并在发布前反查
    APK 内部版本与构建源码 SHA。
11. Stable 只通过手动运行 `Build and publish Stable` 发布；标签必须与源码中的稳定
    `versionName` 完全一致，`versionCode` 必须在发布提交中完成递增，不允许由工作流临时覆盖。
12. Stable 的 `release_summary` 可以留空，由模型撰写 2～4 句概述；填写时优先使用手写内容。
13. Alpha/Stable 的发布资产只能来自受保护环境中的 `assembleRelease`，必须由同一个长期
    证书签名并通过 `verify_release_apk.py`；普通 `main` 推送的 Debug 构建不得作为候选发布资产。

## 推荐流程

1. 复制对应模板到临时文件。
2. 填写各章节；未涉及的分类写“无”。
3. 使用 `render_release_template.py` 替换版本、提交、日期和哈希占位符。
4. 通读一次安装、兼容性、已知问题和校验值。
5. 创建 Release，并确认 Stable 未勾选 Pre-release、Alpha 已勾选 Pre-release。

Alpha/Stable 工作流会通过 `generate_release_notes.py` 自动生成变更记录，再传入
`--changelog-file`，无需发布者另外生成整份说明。手动渲染 `alpha.md` 时，需要准备
Markdown 变更记录文件并增加：

```bash
  --changelog-file ALPHA_CHANGELOG.md
```

`generate_alpha_changelog.py` / `generate_stable_changelog.py` 仍保留，作为规则版兜底。
`stable.md` 同时要求 `--release-summary` 和 `--changelog-file`，缺少任一内容或残留占位符
都会终止发布。

渲染示例：

```bash
python3 .github/release-templates/render_release_template.py \
  --template .github/release-templates/stable.md \
  --output RELEASE_NOTES.md \
  --version v1.0.6 \
  --commit 0123456789abcdef \
  --release-date 2026-09-01 \
  --sha256 abcdef0123456789 \
  --apk-filename SplashScreenAdvanced-v1.0.6-01234567.apk \
  --release-summary "本版本完成近期功能与兼容性调整的稳定化收口。" \
  --changelog-file STABLE_CHANGELOG.md
```

## 自动更新说明

`generate_release_notes.py` 负责两个渠道的“更新内容”（Stable 还包括版本概述）：

1. **收集证据**（`release_context.py`）：上一标签到本次提交的提交记录、用户可见文案
   （`values-zh-rCN/strings.xml`）的净 diff、净文件变化、按改动量截取的源码摘录；
   Stable 还会读取本周期已发布的 Alpha 正文和上一 Stable 正文。净 diff 代表最终状态，
   用来合并阶段性提交、剔除已被推翻的中间实现。收集范围与禁用术语见 `release_notes_config.json`。
2. **按槽位请求模型**（`llm_providers.py`）：槽位 1 → 2 → 3 依次尝试，模型只输出 JSON。
3. **本地校验**：提交引用必须属于本次范围、每个提交都要被归类、Stable 概述为 2～4 句、
   条目长度、不得含 Markdown/链接/内部术语、不得重复。不通过时把错误反馈给同一模型重试一次，
   仍失败就换下一槽位。
4. **本地渲染**：章节、图标、分组、提交链接、完整提交记录与差异链接都由脚本生成，
   模型文本中的 HTML 和 `@` 会被转义。
5. **兜底**：未配置密钥或所有槽位失败时回退规则版（按提交标题分类），并在 Job Summary
   与 Actions 注解中给出警告。

### 配置槽位

在仓库 **Settings → Secrets and variables → Actions** 中配置，必须是仓库级（不要放在
Environment 里）。`<N>` 为 1、2、3，至少配置一个槽位；未配置 API Key 的槽位会被跳过。

| 名称 | 类型 | 说明 |
| --- | --- | --- |
| `RELEASE_NOTES_LLM_<N>_API_KEY` | Secret | 必填 |
| `RELEASE_NOTES_LLM_<N>_PROTOCOL` | Variable | `anthropic`（默认）或 `openai`（任意 OpenAI 兼容接口） |
| `RELEASE_NOTES_LLM_<N>_BASE_URL` | Secret 或 Variable | 可选；默认 `https://api.anthropic.com` 或 `https://api.openai.com/v1`。同名 Secret 优先；**私有代理地址必须放 Secret** |
| `RELEASE_NOTES_LLM_<N>_MODEL` | Variable | `anthropic` 默认 `claude-opus-5`；`openai` 必填 |

仓库公开，运行日志与 Job Summary 人人可见：Variable 会以明文出现在日志的 env 块中，只有 Secret 会被遮蔽。模型调用失败的错误信息在写入日志前会抹掉密钥、`sk-` 开头的密钥片段、Bearer/api_key 字段值与非官方端点地址；工作流契约测试保证密钥只从 Secret 读取。建议为发布说明单独创建带消费上限的密钥。

直连官方 Anthropic API 时会启用服务端拒答回退（`fallbacks: "default"`）；经代理时不附加该参数。
接口不支持结构化输出参数时，脚本会自动降级为纯提示词约束的 JSON。

### 手写覆盖

在 `.github/release-notes/<标签>.md` 放置文件（例如 `.github/release-notes/v1.2.0.md`），
工作流会原样使用它作为“更新内容”，不再调用模型。Stable 使用覆盖文件时必须填写
`release_summary`。

### 本地预览

```bash
python3 .github/release-templates/generate_release_notes.py   --channel stable --repo-root .   --repository-url https://github.com/jichuo1/SplashScreenAdvanced   --release-tag v1.2.0 --commit HEAD   --changelog-output /tmp/CHANGELOG.md --summary-output /tmp/SUMMARY.md   --report-output /tmp/REPORT.md
```

在本地设置同名环境变量即可调用模型；加 `--no-llm` 只查看规则版结果。
