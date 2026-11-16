# 墨撰 Mozhuan

面向网文创作者的 Android AI 小说写作客户端。

以 OpenMinis 的成熟对话、人格与参数配置体系为基础，叠加 OpenWrite 的书稿体系框架（当前仅保留入口），
全部 AI 能力共用同一套 OpenAI 兼容后端配置，专一、轻量、高可控。

## 下载

| 版本 | 更新内容 | 下载 |
|------|----------|------|
| v0.5.0 | 最终定稿｜OpenMinis 对话 + 设置 + 人格体系 | [mozhuan-v0.5.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.5.0/mozhuan-v0.5.0.apk) |
| v0.4.0 | 全新底部导航｜统一单后端 + 书稿+技能底座 | [mozhuan-v0.4.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.4.0/mozhuan-v0.4.0.apk) |
| v0.3.0 | 阶段2 网络适配层 | [mozhuan-v0.3.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.3.0/mozhuan-v0.3.0.apk) |

> 仅保留最新 3 个版本下载链接，历史版本请前往 [Releases](https://github.com/tes9523/mozhuan/releases) 页面。

## 功能

- **AI 对话**：标准双色气泡、SSE 逐字流式输出、停止生成、清空对话、复制 AI 回复、自动滚动到底部
- **人格系统**：可编辑 SOUL.md 全局人格 Prompt，永久本地保存，所有对话自动携带
- **统一后端**：全局仅一套 OpenAI 兼容 API 配置，对话与后续写作能力共用
- **参数可控**：接口地址 / API Key / 模型 / max_tokens / temperature / top_p / 上下文轮数，支持保存、重置、测试连接
- **外观**：深色 / 浅色 / 跟随系统，字体大小调节
- **书稿**：体系入口已预留，功能开发中
- **设置**：模型组、Token 用量、记忆、备份恢复、代理、后台通知、日志等入口预留

## 技术栈

Kotlin · Jetpack Compose · Material3 · Navigation Compose · OkHttp SSE · Coroutines

## 构建

```bash
# 环境：JDK 17、Android SDK 35
./gradlew :app:assembleDebug          # Debug APK
./gradlew :app:assembleRelease        # Release APK（需配置签名）
```

## CI

推送 `main` 分支自动触发签名打包，APK 上传至 Actions Artifacts。

签名 Secrets 配置：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`、`KEY_PASSWORD`。
