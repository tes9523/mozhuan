# 墨撰 Mozhuan

面向网文创作者的 Android AI 小说写作客户端。

以 OpenMinis 的成熟对话、人格与参数配置体系为基础，叠加 OpenWrite 的书稿体系框架（当前仅保留入口），
全部 AI 能力基于云端 API（OpenAI Chat Completions / Anthropic Messages），支持多服务商、多模型独立配置，专一、轻量、高可控。

## 下载

| 版本 | 更新内容 | 下载 |
|------|----------|------|
| v0.9.3 | 输入法留白修复｜内置技能只读可导出｜技能按需加载｜逐模型思考强度｜连接测试修复｜Markdown 与消息长按操作 | [mozhuan-v0.9.3.apk](https://github.com/tes9523/mozhuan/releases/download/v0.9.3/mozhuan-v0.9.3.apk) |
| v0.9.2 | 多会话｜内置 skill-creator｜zip 技能导入｜后台保活｜可用工具与记忆｜输入法内容跟随 | [mozhuan-v0.9.2.apk](https://github.com/tes9523/mozhuan/releases/download/v0.9.2/mozhuan-v0.9.2.apk) |
| v0.9.1 | 模型组｜外部文件夹 / 备份恢复 / 代理 / 通知 / 运行日志｜参数默认值与输入法留白修复 | [mozhuan-v0.9.1.apk](https://github.com/tes9523/mozhuan/releases/download/v0.9.1/mozhuan-v0.9.1.apk) |

> 仅保留最新 3 个版本下载链接，历史版本请前往 [Releases](https://github.com/tes9523/mozhuan/releases) 页面。

## 功能

- **AI 对话**：左上角会话抽屉，多会话独立保存与切换、删除；顶栏模型切换、紧凑 48dp 顶栏、空状态贴紧顶栏、助手 Markdown 正文无气泡 + 用户浅色气泡、圆形发送键、SSE 逐字流式、推理模型思考过程折叠、停止生成、复制、重新生成、长按消息重试 / 编辑重发 / 分支删除、清空二次确认、多行输入自动增高、点击空白收起键盘、输入法弹出时内容跟随上移、齿轮直达人格
- **多服务商**：对齐 OpenMinis 的类型 → 配置 → 模型流程；内置 OpenAI、Anthropic、Google Gemini、OpenRouter、xAI 与自定义 OpenAI 兼容类型；名称 / API Key / 自定义地址 / 自动追加 `/v1`；保存后可拉取 `/models` 补充模型，也可手动填写
- **多模型**：每个模型独立保存 max_tokens、上下文轮数、temperature、top_p 与 7 档思考强度；按服务商生成对应思考参数；max_tokens 留空即不发送、使用服务商默认值；列表内切换当前模型；上下文轮数 = 0 时只发送当前消息；测试连接使用当前模型并与对话共用流式请求，返回中文错误及 429 详情
- **模型组**：把多个模型组合为一个入口，支持故障转移（按顺序失败自动切换）与负载均衡（随机选择）；对话页可直接选择模型组，设置页管理名称、策略与成员
- **Token 用量**：当前会话输入、输出、总计；按服务商 + 模型累计输入输出 token；支持按服务商清空与全部清空；不包含金额与预算
- **技能**：每个技能是一个 `/var/minis/skills/<id>/SKILL.md`，YAML frontmatter 保存名称 / 描述 / 版本；内置原创 skill-creator 只读、不可删除但可导出；自定义技能支持开关、编辑、导入导出；导入支持 zip，自动解压 references / assets / scripts 等附件；对话仅注入启用技能的元数据，AI 按需通过 read_file 加载正文与附件
- **可用工具**：读取文件、写入书稿、读取网页、写入记忆四个开关；通过 OpenAI 兼容 function calling 调用，对话中显示工具状态，单次回复最多 4 轮
- **记忆**：GLOBAL.md 长期记忆 + 按天记录，支持编辑与删除；开启后自动注入系统提示词，也可由 AI 通过「写入记忆」工具追加
- **外观**：深色 / 浅色 / 跟随系统、字体大小、消息时间、紧凑气泡、自动滚动；象牙白分组卡片风格
- **共享文件夹**：卡片式列表 + 读写徽标；书稿（读写 /var/minis/book 占位）、技能（只读 /var/minis/skills）、人格（只读 /var/minis/soul）；支持从手机导入文本、导出文件到手机
- **存储**：共享文件夹、挂载外部文件夹（SAF 持久授权、目录浏览与文本预览）、备份与恢复（服务商 / 模型 / 模型组 / 人格 / 技能整体导出导入）
- **其他**：HTTP / SOCKS5 代理（保存即生效）、生成完成与失败通知、后台保活（前台服务 + 电池优化白名单）、运行日志（请求与错误记录）
- **书稿**：仅保留入口，本轮未开发
- **占位**：反馈

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
