# CUHK Workstation WSL Codex 远端开发交接

date: 2026-08-08
type: handoff-note
status: reference

## 背景

本 note 记录本轮 Codex 线程已经完成的仓库整理、roadmap 明确、远端 WSL 环境配置和后续开发入口。它用于让 `/home/yuukias/Code/SeminarArc` 这份远端 clone 在后续开发时能直接看到上下文。

这篇 note 不是执行入口。需要开始实现时，应先从 `docs/plans/` 选定阶段，再创建或读取对应的 `prompts/tasks/<id>_task.md`。

## Android Studio 判断

当前远端 `CUHK_Workstation_WSL_Codex` 不需要安装 Android Studio。

理由：

- 后续如果只在 WSL 内使用 Codex CLI、Git、Gradle、Android SDK command-line tools、`adb`、单测和 headless build，Android Studio 不是必需依赖。
- Android Studio 主要用于 GUI IDE、Layout Inspector、可视化调试、Windows 桌面 emulator 管理等交互式工作。
- 远端 WSL 已经安装用户目录 JDK 和 Android SDK command-line tools，并通过 `./gradlew assembleDebug testDebugUnitTest` 验证。
- WSL 侧执行 Windows `cmd.exe` / `.exe` interop 曾返回 `Invalid argument`，因此后续稳定路径应避免依赖 WSL 调 Windows 程序。

如果将来确实需要 GUI IDE 或 emulator，可在 workstation 的 Windows 侧安装 Android Studio；不建议把 WSL GUI Android Studio 作为默认开发路径。

## 当前仓库进展

本轮已完成并推送到 `origin/main` 的主要内容：

- 明确 `TODO.md` 是产品级 roadmap 入口。
- 新增 `docs/plans/0.1.x-development-plan-index.md`，作为后续分阶段开发计划索引。
- 新增 `0.1.1` 到 `0.1.5` 的阶段计划，以及 `0.2.x` reconstruction readiness 计划。
- 新增中文 `CHANGELOG.md`，并约定后续 changelog 使用中文维护。
- 改写中文 `README.md`，说明当前真实仓库状态、文档入口、构建命令和非目标。
- 更新 `AGENTS.md`，明确中文输出、计划文档、handoff、`0.1.x` scope、Android/Compose 实现约束。
- 完善 `.gitignore`，覆盖 Android/Gradle、本地凭据、发布包和临时产物。
- 修复 `gradlew` Linux/WSL 可执行位，避免远端 `./gradlew: Permission denied`。

关键提交：

- `fe08e5d docs: add phased roadmap plans`
- `e406061 chore: make gradle wrapper executable`

## 远端环境状态

远端开发入口：

```bash
ssh CUHK_Workstation_WSL_Codex
cd /home/yuukias/Code/SeminarArc
```

远端已配置：

- repo：`/home/yuukias/Code/SeminarArc`
- JDK：`/home/yuukias/opt/jdk-17`
- Android SDK：`/home/yuukias/Android/Sdk`
- `local.properties`：`sdk.dir=/home/yuukias/Android/Sdk`
- `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT` 和 PATH 已写入 `~/.bashrc`
- Android SDK packages：`platform-tools`、`platforms;android-36`、`build-tools;36.0.0`、`cmdline-tools`

远端验证命令：

```bash
export JAVA_HOME="$HOME/opt/jdk-17"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
cd "$HOME/Code/SeminarArc"
./gradlew assembleDebug testDebugUnitTest
```

最近一次验证结果：

- `git status --short --branch`：`## main...origin/main`
- `java -version`：Temurin OpenJDK 17
- `adb version`：Android Debug Bridge 37.0.1
- `./gradlew --version`：Gradle 8.10.2，JVM 17
- `./gradlew assembleDebug testDebugUnitTest`：BUILD SUCCESSFUL

## 已知非阻断 warning

后续 `0.1.1 closeout` 可以优先处理：

- `Icons.Outlined.ArrowBack` deprecated，应迁移到 AutoMirrored 图标。
- `SeminarLibraryViewModel` 需要显式处理 `ExperimentalCoroutinesApi` opt-in。
- Android SDK XML version warning 非阻断，来自较新的 SDK 与当前 Gradle/工具链解析版本差异。
- `stripDebugDebugSymbols` 对部分 native libs 无法 strip，debug 包可接受。

## 后续开发入口

建议下一步从 `0.1.1` 收口开始：

- 计划文件：`docs/plans/0.1.1-closeout-plan.md`
- 推荐先创建 task：`prompts/tasks/0.1.1_closeout_audit_task.md`
- 验收重点：构建/单测、warning 收口、Room/data invariant、现有 UI 不伪装未实现能力、README/CHANGELOG 与真实能力一致。

进入任何 Android/Compose 实现前，先读取：

- `AGENTS.md`
- `prompts/AGENT_RULES.md`
- 指定的 `prompts/tasks/<id>_task.md`
- `.agents/skills/android-lead/SKILL.md`
- `.agents/skills/compose-expert/SKILL.md`

## 稳定性注意事项

- 后续 headless Android 开发默认使用 WSL，不依赖 PowerShell 或 Windows GUI。
- PowerShell/Windows 侧保留给 Android Studio GUI、emulator 或桌面工具。
- 不要让 WSL 工作流依赖 Windows interop `.exe`。
- 本地 Windows checkout 运行 Git 时仍建议使用 `git -c safe.directory=C:/Code/SeminarArc ...`。
- 如果需要远端拉取最新代码，优先使用：

```powershell
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -Command "ssh CUHK_Workstation_WSL_Codex 'cd /home/yuukias/Code/SeminarArc && git pull --ff-only && git status --short --branch'"
```

## 相关材料

- `TODO.md`：产品级 roadmap。
- `docs/plans/0.1.x-mvp-implementation-plan.md`：`0.1.x` 本地 MVP 详细合同。
- `docs/plans/0.1.x-development-plan-index.md`：分阶段开发索引。
- `docs/plans/0.1.1-closeout-plan.md`：下一步建议入口。
- `CHANGELOG.md`：中文更新日志。
- `AGENTS.md`：仓库协作与执行规则。
