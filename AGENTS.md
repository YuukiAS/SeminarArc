# AGENTS.md

## 语言与文档规则

- 面向用户、协作说明、计划、结果、review 和 changelog 默认使用简体中文。
- `CHANGELOG.md` 必须使用中文维护。
- 代码、API 名、Gradle 配置、Kotlin 类型、文件路径和英文模板字段保持原文。
- README、计划文档和 handoff 文档应优先说明真实仓库状态，不得把未来能力写成已完成能力。

## 远端 WSL 环境记录

- 远端 repo 固定位置：`/home/yuukias/code/SeminarArc`，注意 `code` 为小写；不要使用旧的 `/home/yuukias/Code/SeminarArc`。
- JDK 位置：`/home/yuukias/opt/jdk-17`；当前 `JAVA_HOME=/home/yuukias/opt/jdk-17`。
- Android SDK 位置：`/home/yuukias/Android/Sdk`；当前 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 都指向该目录。
- `local.properties` 应保持 `sdk.dir=/home/yuukias/Android/Sdk`。
- `~/.bashrc` 已写入 JDK、Android SDK 和 scrcpy PATH：`$JAVA_HOME/bin`、`$ANDROID_HOME/cmdline-tools/latest/bin`、`$ANDROID_HOME/platform-tools`、`$SCRCPY_HOME`。
- Gradle 使用仓库内 wrapper：`./gradlew`；当前 wrapper 为 Gradle `8.10.2`，不要假设系统级 `gradle` 已安装。
- ADB 位置：`/home/yuukias/Android/Sdk/platform-tools/adb`。
- Android command-line tools 已在 `PATH`：`sdkmanager` 和 `avdmanager` 位于 `/home/yuukias/Android/Sdk/cmdline-tools/latest/bin/`。
- scrcpy 位置：`/home/yuukias/Android/scrcpy-linux-x86_64-v4.1`；当前 `SCRCPY_HOME=/home/yuukias/Android/scrcpy-linux-x86_64-v4.1`，版本为 `scrcpy 4.1`。
- 当前 WSL 未安装或未暴露到 `PATH`：Android emulator。需要 GUI emulator 或 Layout Inspector 时，应先确认 Windows 侧工具或再单独安装；本项目日常 headless 开发不需要 Android Studio GUI。
- 当前 Android SDK 已安装 `platforms;android-36`、`build-tools;36.0.0`、`platform-tools 37.0.1`，与项目 `compileSdk = 36` 对齐。

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
