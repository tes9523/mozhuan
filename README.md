# 墨撰 Mozhuan

面向网文创作者的 Android 本地写作工具。

## 下载

| 版本 | 更新内容 | 下载 |
|------|----------|------|
| v0.3.0 | 阶段2 网络适配层 | [mozhuan-v0.3.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.3.0/mozhuan-v0.3.0.apk) |
| v0.1.0 | 阶段1 底部导航框架 | [mozhuan-v0.1.0.apk](https://github.com/tes9523/mozhuan/releases/download/v0.1.0/mozhuan-v0.1.0.apk) |

> 仅保留最新 3 个版本下载链接，历史版本请前往 [Releases](https://github.com/tes9523/mozhuan/releases) 页面。

## 功能

- **书稿管理**：SAF 自选目录存储，TXT / Markdown 导入自动分章
- **Markdown 编辑器**：基础渲染，编辑内容自动实时保存
- **导出**：单章节或全书导出为 TXT / Markdown
- **后台保活**：前台服务 + 常驻通知，展示编辑状态
- **主题**：亮色 / 暗色 / 跟随系统
- **导航**：底部三 Tab（首页 / 演示页 / 关于页）

## 技术栈

Kotlin · Jetpack Compose · Material3 · Navigation Compose · Room · SAF · Coroutines

## 构建

```bash
# 环境：JDK 17、Android SDK 35
./gradlew :app:assembleDebug          # Debug APK
./gradlew :app:assembleRelease        # Release APK（需配置签名）
./gradlew :app:testDebugUnitTest      # 单元测试
```

## CI

推送 `main` 分支自动触发签名打包，APK 上传至 Actions Artifacts。

签名 Secrets 配置：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`、`KEY_PASSWORD`。
