# 墨撰 Mozhuan

面向网文创作者的 Android AI 写作工具：融合 OpenWrite 书稿 / 章节体系与 OpenMinis 技能调度系统，
全部 AI 能力共用同一套 API 后端，依靠技能模板区分续写、对话任务。

## 下载

| 版本 | 更新内容 | 下载 |
|------|----------|------|
| v0.4.0 | 全新底部导航｜统一单后端 + 书稿+技能底座 | [mozhuan-v0.4.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.4.0/mozhuan-v0.4.0.apk) |
| v0.3.0 | 阶段2 网络适配层 | [mozhuan-v0.3.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.3.0/mozhuan-v0.3.0.apk) |
| v0.1.0 | 阶段1 底部导航框架 | [mozhuan-v0.1.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.1.0/mozhuan-v0.1.0.apk) |

> 仅保留最新 3 个版本下载链接，历史版本请前往 [Releases](https://github.com/tes9523/mozhuan/releases) 页面。

## 功能

- **AI 对话**：全局自由对话；从书稿详情进入绑定书稿的专属对话，自动注入世界观与人物设定，SSE 流式多轮
- **书稿体系**：新建书稿、章节管理、独立世界观 / 人物设定、书稿专属技能绑定
- **章节续写**：编辑章节时一键续写，自动加载全书上下文与续写技能，流式生成正文
- **技能系统**：内置【剧情对话打磨】【网文章节续写】，支持新增 / 编辑自定义全局技能
- **统一后端**：全局仅一套 API 配置（地址 / Key / 模型），对话与续写共用
- **后台保活**：编辑期间前台服务 + 常驻通知
- **主题**：亮色 / 暗色 / 跟随系统

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
