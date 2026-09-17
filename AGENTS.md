# AGENTS.md

## 语言与文档规则

- 面向用户、协作说明、计划、结果、review 和 changelog 默认使用简体中文。
- `CHANGELOG.md` 必须使用中文维护。
- 代码、API 名、Gradle 配置、Kotlin 类型、文件路径和英文模板字段保持原文。
- README、计划文档和 handoff 文档应优先说明真实仓库状态，不得把未来能力写成已完成能力。

## 设备测试安全摘要

- **后续 Android 自动化默认 Emulator-first。** JVM/headless 测试在 WSL
  canonical repo 运行；Compose/instrumentation/connected Android tests 优先在
  Windows Emulator 运行。远程物理真机不再承担日常 CI/connected test 角色。
- Protected physical device 只用于 Emulator 无法替代的少量硬件/系统 smoke；
  它不是 generic connected/instrumentation target。
- Agent 不得为了测试便利自动 reset/recover ADB、USB、usbipd、WSL 或任何
  transport。不得用 `adb kill-server`、transport reset、usbipd、`wsl
  --shutdown` 或真机操作获得“更干净”的 inventory。
- 任何 task 明确授权的 physical write 都必须使用现场复核过的 explicit
  serial，并在每个风险动作前后做 preflight/postflight；不得依赖历史 serial
  或默认设备。
- **`DEVICE_CHANNEL_BLOCKED` 不等于整个开发 task 被阻塞。** 物理设备异常只冻结
  physical-device 子流程；凡是仍可通过 WSL headless、Windows Emulator、文档或
  lint 完成的工作都应继续。
- PIN、密码、unlock secret、keystore/password/private key 等不得进入 repo、
  task、result、日志、截图说明、commit message 或 GitHub Release。
- 详细 device/environment/test mechanics、SDK/JDK/cache 路径、mixed-inventory
  fallback、命令禁令、historical incident evidence、harness 和 transport 规则见
  `docs/DEVICE_TESTING.md`；不要创建第三份 device/environment manual。

## SeminarArc Project Skills

Use the project-local skills in `.agents/skills/` for Android and Jetpack Compose work in this repository.

### Android Lead Skill

For Android app architecture, data layer, Room, WorkManager, foreground service, Media3, testing, build logic, modularity decisions, and product-quality UI review, follow:

`.agents/skills/android-lead/SKILL.md`

Load supporting references from:

`.agents/skills/android-lead/references/`

### Compose Expert Skill

For Jetpack Compose UI implementation, state management, modifier ordering, performance, navigation patterns, animation, Material 3 theming, and source-backed Compose guidance, follow:

`.agents/skills/compose-expert/SKILL.md`

Load supporting references from:

`.agents/skills/compose-expert/references/`

### Usage Rules

- Use both skills together for Compose-heavy Android features.
- Prefer `android-lead` for product architecture and app-level decisions.
- Prefer `compose-expert` for composable APIs, state/effect choices, navigation patterns, and performance-sensitive UI behavior.
- Before making non-trivial Compose decisions, consult the relevant reference files instead of relying on memory.

## Roadmap 与计划文件

- 产品级入口是 `TODO.md`。
- `docs/plans/0.1.x-mvp-implementation-plan.md` 是 `0.1.x` 本地采集核心的详细实现合同。
- `docs/plans/0.1.x-development-plan-index.md` 是后续分阶段开发计划索引。
- `docs/plans/0.1.x-mvp-execution-batch-01.md` 只记录已经完成的 `0.1.1` 基础批次，不定义未来完整产品范围。
- 新增阶段计划应放在 `docs/plans/`，并写清目标、范围、禁止事项、验证门槛和建议 task 拆分。
- 可执行任务仍必须写成 `prompts/tasks/<id>_task.md`，计划文件本身不是 Codex 默认执行入口。

## 0.1.x Scope Rules

- `0.1.x` 只做本地 seminar 管理、录音、拍照、时间线、clip、Markdown/ZIP 导出和本地 MVP 验收。
- 不要在 `0.1.x` 中加入 OCR、转写、AI 总结、Notion、云同步、登录、广告、订阅或支付。
- 每个素材都必须归属于明确的 `seminarId`。
- 同时最多只能有一场 `ACTIVE` seminar。
- 录音状态必须来自 foreground service 和 Room 持久状态，不能依赖 Activity 内存。
- Timeline event 必须记录可恢复的 offset。
- Clip 只有在真实生成并处于 `READY` 后才可播放；`PENDING`、`PROCESSING`、`FAILED` 必须有清晰 fallback。
- 删除 seminar 或 event 时，Room 记录和 app-owned 文件必须一致清理。

## Internal dogfood 自恢复授权

- `internal dogfood` signer、Windows Emulator、Gradle、build、lint、测试、release packaging、APK checksum 和本地分发管线中的可恢复工程问题，Codex 默认自行定位、修复、重试或重建，不反复询问用户。
- 该授权只覆盖 `com.yuukias.seminararc.internal` 的 internal/dogfood APK，不覆盖 Google Play production signing 或公开发布。
- 只有涉及 production signing、付费账号、私有 API credential、recurring-cost backend、真实用户数据破坏风险、Google Play Console、公开发布或 GM1910 真机写操作时才询问用户。
- 如果本地 internal signer 初始化失败且尚未用于发布，Codex 可以清理本轮失败尝试并重建；仍不得把 keystore、password、secret properties、私钥材料或用户数据写入仓库、Git、日志、result、commit message 或 GitHub Release。

## Android / Compose 实现约束

- 保持单模块 Android app，除非计划文档和 task 明确授权拆模块。
- UI 遵循 `design/` 的 `Academic Archive` 方向：列表优先、安静、专业、Material 3 token-backed。
- 不要用静态 Compose 页面、假按钮或内存假数据冒充 MVP 完成。
- ViewModel 使用 `StateFlow` 表示状态，one-shot event 使用 `SharedFlow(replay = 0)`。
- UI 通过 repository/use case 访问数据；composable 不直接读写 Room 或平台 recorder/camera API。
- 所有交互目标至少 48dp，并为图标按钮、时间线播放、删除、重试等动作提供清晰无障碍语义。

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
- `docs/wiki/`：长期研究知识库，用于沉淀论文、报告、概念、对比、gap 和综合讨论；不是默认任务入口。

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
- 有长期复用价值的论文摘要、报告摘要、概念、对比、gap 和综合讨论应写入 `docs/wiki/`，并让 task 显式引用相关 wiki 页面。
- ChatGPT 不应把 issue、PR description 或聊天正文当作 Codex 的唯一任务来源。
<!-- ai-bridge-kit:end -->
