# 墨撰 Mozhuan

面向网文创作者的 Android AI 小说写作客户端。

以 OpenMinis 的成熟对话、人格与参数配置体系为基础，叠加自研书稿体系，
全部 AI 能力基于云端 API（OpenAI Chat Completions / Anthropic Messages），支持多服务商、多模型独立配置，专一、轻量、高可控。

## 下载

| 版本 | 更新内容 | 下载 |
|------|----------|------|
| v0.10.4 | 修复更换封面闪退：改为流式读取尺寸与解码，不再把大图整体读入内存导致 OOM；同时拦截无系统图库选择器的机型｜「共享文件夹 → 书稿」真正可用：可浏览 book 目录、进入子文件夹、预览/编辑并保存文本、从手机导入 TXT/MD | [mozhuan-v0.10.4.apk](https://github.com/tes9523/mozhuan/releases/download/v0.10.4/mozhuan-v0.10.4.apk) |
| v0.10.3 | AI 对话支持 Markdown 表格完整渲染（不再折叠成一串竖线）｜修复小说页更换封面闪退（封面解码采样防 OOM、图片处理移出主线程、封面选择改为横向滚动而非嵌套懒加载）｜系统提示词始终注入已挂载的外部文件夹，AI 不再误判「没有挂载文件夹」｜明确区分「内置书稿文件夹 book/」与「记忆 memory/」：小说设定写入 book/<书名>/小说资料/，记忆只存偏好；写入书稿工具默认开启 | [mozhuan-v0.10.3.apk](https://github.com/tes9523/mozhuan/releases/download/v0.10.3/mozhuan-v0.10.3.apk) |
| v0.10.2 | 修复 AI 创建的小说删除失败（挂载目录作品递归删除、本地作品移入回收站失败时兜底清除）｜导入小说书名改为优先使用文件名，不再被正文里的其它书名覆盖｜离开再回到 AI 对话页时恢复原来的滚动位置，不再每次都跳到同一处 | [mozhuan-v0.10.2.apk](https://github.com/tes9523/mozhuan/releases/download/v0.10.2/mozhuan-v0.10.2.apk) |

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
