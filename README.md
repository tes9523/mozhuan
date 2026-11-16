# 墨撰 Mozhuan

面向网文创作者的 Android AI 小说写作客户端。

以 OpenMinis 的成熟对话、人格与参数配置体系为基础，叠加 OpenWrite 的书稿体系框架（当前仅保留入口），
全部 AI 能力基于云端 API（OpenAI Chat Completions / Anthropic Messages），支持多服务商、多模型独立配置，专一、轻量、高可控。

## 下载

| 版本 | 更新内容 | 下载 |
|------|----------|------|
| v0.9.0 | 推翻重写服务商配置 / 对话 / 技能：对齐 OpenMinis 的预设、模型与 SKILL.md | [mozhuan-v0.9.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.9.0/mozhuan-v0.9.0.apk) |
| v0.8.0 | UI 对齐 OpenMinis｜分组卡片、圆形发送键、无气泡正文 | [mozhuan-v0.8.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.8.0/mozhuan-v0.8.0.apk) |
| v0.7.0 | 两页式服务商配置｜模型列表自动拉取 + 上下文=0 边界修复 | [mozhuan-v0.7.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.7.0/mozhuan-v0.7.0.apk) |

> 仅保留最新 3 个版本下载链接，历史版本请前往 [Releases](https://github.com/tes9523/mozhuan/releases) 页面。

## 功能

- **AI 对话**：顶栏模型切换、紧凑 48dp 顶栏、空状态贴紧顶栏、助手正文无气泡 + 用户浅色气泡、圆形发送键、SSE 逐字流式、推理模型思考过程折叠、停止生成、复制、重新生成、清空二次确认、多行输入自动增高、点击空白收起键盘、齿轮直达人格
- **多服务商**：对齐 OpenMinis 的类型 → 配置 → 模型流程；内置 OpenAI、Anthropic、Google Gemini、OpenRouter、xAI 与自定义 OpenAI 兼容类型；名称 / API Key / 自定义地址 / 自动追加 `/v1`；保存后可拉取 `/models` 补充模型，也可手动填写
- **多模型**：每个模型独立保存 max_tokens、上下文轮数、temperature、top_p；列表内切换当前模型；上下文轮数 = 0 时只发送当前消息；测试连接返回中文错误并区分 401 / 403 / 404 / 429 / 超时
- **Token 用量**：当前会话输入、输出、总计；按服务商 + 模型累计输入输出 token；支持按服务商清空与全部清空；不包含金额与预算
- **技能**：每个技能是一个 `/var/minis/skills/<id>/SKILL.md`，YAML frontmatter 保存名称 / 描述 / 版本；支持开关、编辑、导入导出；启用后与人格一起合并进系统提示词
- **外观**：深色 / 浅色 / 跟随系统、字体大小、消息时间、紧凑气泡、自动滚动；象牙白分组卡片风格
- **共享文件夹**：卡片式列表 + 读写徽标；书稿（读写 /var/minis/book 占位）、技能（只读 /var/minis/skills）、人格（只读 /var/minis/soul）；支持从手机导入文本、导出文件到手机
- **书稿**：仅保留入口，本轮未开发
- **占位**：模型组、可用工具、记忆、挂载外部文件夹、备份与恢复、代理设置、后台通知、运行日志

## 技术栈

Kotlin · Jetpack Compose · Material3 · Navigation Compose · OkHttp SSE · Coroutines · OpenAI Chat Completions / Anthropic Messages

## 构建

```bash
# 环境：JDK 17、Android SDK 35
./gradlew :app:assembleDebug          # Debug APK
./gradlew :app:assembleRelease        # Release APK（需配置签名）
```

## CI

推送 `main` 分支自动触发签名打包，APK 上传至 Actions Artifacts。

签名 Secrets 配置：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`、`KEY_PASSWORD`。
