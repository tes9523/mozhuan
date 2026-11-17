package com.mozhuan.data.repository

/**
 * 随应用内置的技能原文。
 * skill-creator 每次扫描都会按这里的 canonical 内容恢复，用户不可修改或删除。
 * 正文中的工具名已对齐本应用实际提供的 read_file / write_file。
 */
internal object BundledSkills {

    const val SKILL_CREATOR_VERSION = "3.0.0"

    val SKILL_CREATOR_MD: String = """
---
name: skill-creator
description: 创建新的技能(Skill)、修改和改进现有技能。当用户提到"创建技能"、"写个技能"、"做个助手"、"帮我做一个XX"、"我想让AI学会XX"、"把流程变成技能"时使用此技能，即使用户没有明确说出"技能"二字也应触发。也可用于编辑、优化已有技能。
version: 3.0.0
---

# 技能创建助手

帮助用户创建和管理技能(Skill)，让 AI 能够更好地完成特定任务。

## 什么是技能？

技能是一组指导 AI 完成特定任务的指令集合，存储为一个目录：

```
skill-name/
├── SKILL.md          (必需 — YAML frontmatter + Markdown 指令)
├── scripts/          (可选 — 可执行脚本)
├── references/       (可选 — 按需读取的文档和规则)
└── assets/           (可选 — 输出使用的模板、图标等)
```

## 技能的三级加载机制

技能采用渐进式加载，避免撑爆上下文：

1. **元数据**（name + description）——始终在系统提示中，约 100 字
2. **SKILL.md 正文**——调用 `read_file` 读取 `skills/<name>/SKILL.md` 后加载（建议控制在 500 行以内）
3. **配套资源**（scripts/、references/、assets/）——AI 用 `read_file` 按需读取

配套资源**不会自动内联**。AI 只能看到文件列表（文件名），看不到内容。因此 SKILL.md 中必须明确写出每个资源文件的路径和使用时机，AI 才知道去读。

## 创建技能的流程

### Step 1: 了解用户需求

如果用户说"把这个流程变成技能"，先从对话历史中提取步骤、工具使用和修正点。

向用户了解以下信息：
1. 这个技能要帮 AI 完成什么任务？
2. 什么情况下应该触发？（用户会说什么话、提到什么关键词）
3. 期望的输出格式是什么？
4. 有没有特殊的规则或约束？

### Step 2: 规划技能结构

根据需求复杂度，决定内容分布：

- **SKILL.md** — 核心工作流程和指令（控制在 500 行以内）
- **scripts/** — 可执行脚本，用于确定性、重复性任务
- **references/** — AI 按需读取的规则、标准、领域资料
- **assets/** — 输出时使用的模板、图标等文件

**领域变体模式**：当技能支持多个领域时，按变体组织：
```
cloud-deploy/
├── SKILL.md          (工作流 + 领域选择)
└── references/
    ├── aws.md
    ├── gcp.md
    └── azure.md
```
AI 只需读取相关的 reference 文件，不用全部加载。

### Step 3: 编写 SKILL.md

基本结构：

```markdown
---
name: skill-name
description: 技能描述，说明何时使用。要覆盖触发场景和关键词，宁滥勿缺。
---

# 技能标题

说明这个技能做什么、为什么这样工作。

## 工作流程

### Step 1: [步骤名称]
[说明这一步做什么，解释为什么重要]

### Step 2: [步骤名称]
...

## 输出格式
[明确的输出模板]

## 重要规则
- [规则和原因]

## References
- **[references/xxx.md](references/xxx.md)** — 说明什么时候需要读这个文件
```

**编写要点：**

- **名称**：英文小写 + 连字符，如 `novel-reviewer`、`chapter-outliner`
- **描述要贪婪**：AI 容易不触发技能（欠触发），描述要主动覆盖相关场景和同义词。好例子：`当用户想要生成周报、写工作汇报、整理本周进度、总结项目进展时使用此技能，即使用户只说"帮我写个报告"也应触发。`
- **解释原因**：告诉 AI 为什么要这样做，比堆砌 MUST/ALWAYS/NEVER 更有效。比如"检查格式是因为下游系统会解析，格式错误会导致解析失败"，而不是"必须检查格式"
- **给示例**：示例比抽象描述更有帮助
- **用祈使句**：直接说"读取文件"、"输出结果"

**配套资源引用规范（重要）：**

在 SKILL.md 正文中，**必须明确写出每个配套文件的路径和使用时机**：
```markdown
审核时，读取 `references/审核规则.md` 检查输出是否符合标准。
需要批量转换时，执行 `scripts/helper.py`。
生成报告时，使用 `assets/report-template.html` 作为模板。
```

并在末尾加 **References 索引区块**：
```markdown
## References
- **[references/审核规则.md](references/审核规则.md)** — 输出审核标准，在生成最终内容前读取
- **[scripts/helper.py](scripts/helper.py)** — 批量格式转换脚本
```

配套文件较多时，简要说明每个文件的用途和读取时机。

### Step 4: 自我验证

写完技能后，在脑中模拟执行，逐项检查：

1. **触发检查**：选一个最典型的用户说法，技能描述能匹配上吗？
2. **流程完整性**：按技能步骤走一遍，输出符合预期吗？有没有遗漏的步骤？
3. **配套资源引用检查**：每个 references/ 和 scripts/ 下的文件，在 SKILL.md 正文中是否都写出了路径和使用时机？末尾有没有 References 索引？
4. **边缘场景**：选一个用户没明确提"技能"的场景，技能能触发吗？

发现问题先修正，再交给用户确认。

### Step 5: 保存技能

先向用户展示技能概要并确认：
- 技能名称和描述
- SKILL.md 的核心流程
- 包含的配套文件列表

用户确认后，用 `write_file` 逐个保存文件：

```
写入 skills/<name>/SKILL.md                → 完整的 SKILL.md（以 --- 开头的 frontmatter + 正文）
写入 skills/<name>/references/<file>.md    → 参考文档
写入 skills/<name>/scripts/<file>          → 脚本
写入 skills/<name>/assets/<file>           → 模板或素材
```

注意：
- 路径必须以 `skills/` 开头，并保留 `references/`、`scripts/`、`assets/` 前缀
- 内置技能 skill-creator 只读，无法覆盖；修改内置技能时应先另存为新名称
- 写完后提示用户到「设置 → 技能」中启用新技能

## 修改现有技能

当用户要修改现有技能时：
1. 用 `read_file` 读取 `skills/<name>/SKILL.md` 的完整内容
2. 了解用户想改什么
3. 提出修改建议，用户确认后用 `write_file` 覆盖整个 SKILL.md
4. 只改某个配套文件时，直接 `write_file` 写入对应路径

内置技能修改时会自动保留原版，不会被覆盖。

## References

- **[references/templates.md](references/templates.md)** — SKILL.md 模板和示例，创建新技能时参考
""".trimIndent()

    val SKILL_CREATOR_TEMPLATES_MD: String = """
# SKILL.md 模板参考

## 基础模板

```markdown
---
name: my-skill
description: 简要说明技能做什么、何时触发。覆盖触发关键词和场景。
---

# 技能名称

说明这个技能的目的和工作方式。

## 工作流程

### Step 1: [步骤名称]
[说明这一步做什么，解释为什么重要]

### Step 2: [步骤名称]
[说明这一步做什么]

## 输出格式
[明确的输出格式和模板]

## 重要规则
- [规则和原因]

## References
- **[references/xxx.md](references/xxx.md)** — 说明何时读取此文件
```

## 示例：带多个配套资源的技能

```markdown
---
name: code-reviewer
description: 代码审查助手。当用户要求审查代码、检查PR、review代码、代码质量检查时使用。
---

# 代码审查助手

审查代码变更，检查代码质量、安全问题和最佳实践。

## 工作流程

### Step 1: 了解审查范围
确定要审查的文件和变更范围。

### Step 2: 加载审查标准
读取 `references/审查规则.md` 了解详细的审查标准和检查项。

### Step 3: 执行审查
逐文件检查，对照审查标准标记问题。

### Step 4: 生成报告
使用 `assets/review-report.md` 模板格式，生成审查报告。

## References
- **[references/审查规则.md](references/审查规则.md)** — 代码审查标准，开始审查前必须读取
- **[assets/review-report.md](assets/review-report.md)** — 审查报告输出模板
```

## 关键原则

1. **渐进式加载**：元数据 → SKILL.md 正文 → 配套资源，不让上下文一次膨胀
2. **配套资源不自动内联**：每个 references/ 文件的使用时机必须在 SKILL.md 中明确写出
3. **末尾加 References 索引**：让 AI 一眼看到有哪些配套文件可用
4. **解释为什么**：告诉 AI 原因，比命令式 MUST/ALWAYS 更有效
5. **名称不用中文**：避免路径编码问题，用英文小写 + 连字符
""".trimIndent()
}
