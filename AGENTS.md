# AGENTS.md

## SeminarArc Project Skills

Use the project-local skills in `.agents/skills/` for Android and Jetpack Compose work in this repository.

### Android Lead Skill

For Android app architecture, data layer, Room, WorkManager, foreground service, Media3, testing, build logic, modularity decisions, and product-quality UI review, follow:

`.agents/skills/android-lead/SKILL.md`

Load supporting references from:

`.agents/skills/android-lead/references/`

### Compose Expert Skill

For Jetpack Compose UI implementation, state management, modifier ordering, performance, navigation patterns, animation, Material 3, and source-backed Compose guidance, follow:

`.agents/skills/compose-expert/SKILL.md`

Load supporting references from:

`.agents/skills/compose-expert/references/`

### Usage Rules

- Use both skills together for Compose-heavy Android features.
- Prefer `android-lead` for product architecture and app-level decisions.
- Prefer `compose-expert` for composable APIs, state/effect choices, navigation patterns, and performance-sensitive UI behavior.
- Before making non-trivial Compose decisions, consult the relevant reference files instead of relying on memory.

<!-- ai-bridge-kit:start -->
# Handoff Protocol

本项目采用 `prompts/` handoff 协议，用于 ChatGPT 和 Codex 之间的文件化交接。

## 默认入口

- `prompts/AGENT_RULES.md`：长期执行规则。
- `prompts/CHATGPT_RULES.md`：ChatGPT 通过 GitHub MCP 或仓库工具写 task、note、review 时应读取的规则。
- `prompts/tasks/*_task.md`：唯一默认任务入口。
- `prompts/tasks/*_result.md`：Codex 的结果回写位置。
- `prompts/tasks/*_review.md`：ChatGPT 的复盘位置。
- `docs/notes/`：参考笔记目录，不是默认任务入口。
- `docs/wiki/`：长期研究知识库，不是默认任务入口。

## Codex 行为规则

- Codex 开始任务前应读取 `prompts/AGENT_RULES.md` 和指定的 `prompts/tasks/<id>_task.md`。
- Codex 必须遵守 task frontmatter、允许动作、禁止动作和停止条件。
- Codex 完成后必须写 `prompts/tasks/<id>_result.md`。
- Codex 不应主动执行 `docs/notes/` 或 `docs/wiki/` 中的内容，除非任务单显式引用某篇 note 或 wiki 页面作为背景材料。
- 如果任务需要联网、上传、删除数据、运行昂贵命令或修改高风险配置，但 task 没有授权，Codex 必须停止并在 result 中请求人工批准。

## ChatGPT / GitHub MCP 行为规则

- ChatGPT 通过 GitHub MCP 处理本仓库时，应先读取 `AGENTS.md` 和 `prompts/CHATGPT_RULES.md`。
- 需要 Codex 执行的内容必须写成 `prompts/tasks/<id>_task.md`。
- 只作参考的研究分析、方案比较、会议记录和复盘应写到 `docs/notes/`。
- 有长期复用价值的论文摘要、报告摘要、概念、对比、gap 和综合讨论应写到 `docs/wiki/`。
- ChatGPT 不应把 issue、PR description 或聊天正文当作 Codex 的唯一任务来源。
<!-- ai-bridge-kit:end -->
