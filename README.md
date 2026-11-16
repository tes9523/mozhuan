# 墨撰 Mozhuan

面向网文创作者的 Android AI 小说写作客户端。

以 OpenMinis 的成熟对话、人格与参数配置体系为基础，叠加 OpenWrite 的书稿体系框架（当前仅保留入口），
全部 AI 能力基于云端 OpenAI 兼容 API（chat/completions），支持多服务商独立配置，专一、轻量、高可控。

## 下载

| 版本 | 更新内容 | 下载 |
|------|----------|------|
| v0.6.0 | 多服务商｜Token 统计 + 技能与共享文件夹 | [mozhuan-v0.6.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.6.0/mozhuan-v0.6.0.apk) |
| v0.5.0 | 最终定稿｜OpenMinis 对话 + 设置 + 人格体系 | [mozhuan-v0.5.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.5.0/mozhuan-v0.5.0.apk) |
| v0.4.0 | 全新底部导航｜统一单后端 + 书稿+技能底座 | [mozhuan-v0.4.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.4.0/mozhuan-v0.4.0.apk) |

> 仅保留最新 3 个版本下载链接，历史版本请前往 [Releases](https://github.com/tes9523/mozhuan/releases) 页面。

## 功能

- **AI 对话**：双色气泡、SSE 逐字流式输出、停止生成、复制、重新生成、清空二次确认、多行输入自动增高、点击空白收起键盘
- **多服务商**：服务商增删改与设默认，独立保存名称 / API Key / 端点 / 模型 / max_tokens / temperature / top_p / 上下文轮数；端点原样发送；模型支持下拉与手动输入；测试连接返回中文错误并区分 401 / 404 / 超时
- **Token 用量**：当前会话输入、输出、总计，按服务商 + 模型累计统计，支持分别清空；不包含金额与预算
- **技能与人格**：静态提示词技能增删改、启停、单条 JSON 导入导出，启用后与 SOUL.md 人格合并发送
- **外观**：深色 / 浅色 / 跟随系统、字体大小、消息时间、紧凑气泡、自动滚动
- **共享文件夹**：预览技能与人格目录文本，支持从手机导入文本、导出文件到手机
- **书稿**：仅保留入口，本轮未开发
- **占位**：模型组、可用工具、子代理任务、记忆、MCP 集成、环境变量、挂载外部文件夹、备份与恢复

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
