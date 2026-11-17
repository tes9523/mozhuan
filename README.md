# 墨撰 Mozhuan

面向网文创作者的 Android AI 小说写作客户端。

以 OpenMinis 的成熟对话、人格与参数配置体系为基础，叠加自研书稿体系，
全部 AI 能力基于云端 API（OpenAI Chat Completions / Anthropic Messages），支持多服务商、多模型独立配置，专一、轻量、高可控。

## 下载

| 版本 | 更新内容 | 下载 |
|------|----------|------|
| v0.9.9 | 小说页只显示小说，过滤 skills 等非小说目录｜导入仅支持 TXT / DOCX / MD 并校验格式｜工具调用轮数上限提升到 100｜AI 可在挂载的外部文件夹（mounts/<名称>/）下创建新书与目录结构，挂载目录中的小说也会呈现在小说页｜技能编辑页系统返回键 / 手势也会弹出保存提示 | [mozhuan-v0.9.9.apk](https://github.com/tes9523/mozhuan/releases/download/v0.9.9/mozhuan-v0.9.9.apk) |
| v0.9.8 | 技能按内置 / 自定义分组，内置置顶｜书稿功能开启：我的小说空态、新建作品（封面 + 名称）、目录浏览与正文编辑｜支持导入 TXT / DOCX / MD 并自动识别书名与章节｜AI 对话写入的小说直接呈现在书稿，作品固定「小说资料（设定 / 大纲 / 章节摘要）+ 章节内容」结构 | [mozhuan-v0.9.8.apk](https://github.com/tes9523/mozhuan/releases/download/v0.9.8/mozhuan-v0.9.8.apk) |
| v0.9.7 | 技能详情去掉重复编辑入口、描述字号缩小｜模型组/记忆等内部页面返回逐级回退｜对话一键到顶 / 到底｜点击空白收起输入法｜长按菜单跟随气泡｜AI 正文可直接选中复制 | [mozhuan-v0.9.7.apk](https://github.com/tes9523/mozhuan/releases/download/v0.9.7/mozhuan-v0.9.7.apk) |

> 仅保留最新 3 个版本下载链接，历史版本请前往 [Releases](https://github.com/tes9523/mozhuan/releases) 页面。

## 功能

- **AI 对话**：左上角会话抽屉，多会话独立保存与切换、删除；顶栏模型切换与思考强度切换、紧凑 48dp 顶栏、空状态贴紧顶栏、助手 Markdown 正文无气泡 + 用户浅色气泡、圆形发送键、SSE 逐字流式、推理模型思考过程折叠、停止生成、复制、重新生成、长按消息操作（用户气泡复制 / 编辑 / 删除，编辑仅限最新一条）、输入框 @ 主动调用技能、清空二次确认、多行输入自动增高、点击空白收起键盘、输入法弹出时内容跟随上移、齿轮直达人格
- **多服务商**：对齐 OpenMinis 的类型 → 配置 → 模型流程；内置 OpenAI、Anthropic、Google Gemini、OpenRouter、xAI 与自定义 OpenAI 兼容类型；名称 / API Key / 自定义地址 / 自动追加 `/v1`；保存后可拉取 `/models` 补充模型，也可手动填写
- **多模型**：每个模型独立保存 max_tokens、上下文轮数、temperature、top_p 与 7 档思考强度；按服务商生成对应思考参数；max_tokens 留空即不发送、使用服务商默认值；列表内切换当前模型；上下文轮数 = 0 时只发送当前消息；测试连接使用当前模型并与对话共用流式请求，返回中文错误及 429 详情
- **模型组**：把多个模型组合为一个入口，支持故障转移（按顺序失败自动切换）与负载均衡（随机选择）；对话页可直接选择模型组，设置页管理名称、策略与成员
- **Token 用量**：当前会话输入、输出、总计；按服务商 + 模型累计输入输出 token；支持按服务商清空与全部清空；不包含金额与预算
- **技能**：每个技能是一个 `/var/minis/skills/<id>/SKILL.md`，YAML frontmatter 保存名称 / 描述 / 版本；列表按「内置 / 自定义」分组且内置置顶；导入完整保留 SKILL.md 原文与全部附件；详情页展示技能卡片与原始 Skill 内容，编辑改名为全屏编辑器（正文 / 引用文件两个 Tab，键盘不遮挡，references 可改）；内置 skill-creator 每次启动恢复为内置版本、只读、不可删除但可导出；自定义技能支持开关、导入导出；对话仅注入启用技能的元数据，AI 按需通过 read_file 加载正文与附件，输入框 @ 技能可直接注入正文
- **可用工具**：读取文件、写入书稿、读取网页、写入记忆四个开关；通过 OpenAI 兼容 function calling 调用，对话中显示工具状态，单次回复最多 4 轮
- **记忆**：GLOBAL.md 长期记忆 + 按天记录，支持编辑与删除；开启后自动注入系统提示词，也可由 AI 通过「写入记忆」工具追加
- **外观**：深色 / 浅色 / 跟随系统、字体大小、消息时间、紧凑气泡、自动滚动；象牙白分组卡片风格
- **共享文件夹**：卡片式列表 + 读写徽标；书稿（读写 book）、技能（只读 /var/minis/skills）、人格（只读 /var/minis/soul）；支持从手机导入文本、导出文件到手机
- **存储**：共享文件夹、挂载外部文件夹（SAF 持久授权、目录浏览与文本预览）、备份与恢复（服务商 / 模型 / 模型组 / 人格 / 技能整体导出导入）
- **其他**：HTTP / SOCKS5 代理（保存即生效）、生成完成与失败通知、后台保活（前台服务 + 电池优化白名单）、运行日志（请求与错误记录）
- **书稿**：底部「小说」入口；无作品时显示空态并可创建/导入；新建作品可填名称并选择封皮；进入作品固定「小说资料（设定 / 世界观、人物库；大纲；章节摘要）+ 章节内容」目录结构，支持逐级浏览与正文编辑；支持导入 TXT / DOCX / MD 并自动识别书名与章节；AI 对话写入 `book/` 的内容会直接显示在书稿中
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
