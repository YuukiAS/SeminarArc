# AGENTS.md

## 语言与文档规则

- 面向用户、协作说明、计划、结果、review 和 changelog 默认使用简体中文。
- `CHANGELOG.md` 必须使用中文维护。
- 代码、API 名、Gradle 配置、Kotlin 类型、文件路径和英文模板字段保持原文。
- README、计划文档和 handoff 文档应优先说明真实仓库状态，不得把未来能力写成已完成能力。

## 远端 WSL 环境记录

- 远端 repo 固定位置：`/home/yuukias/code/SeminarArc`，注意 `code` 为小写；不要使用旧的 `/home/yuukias/Code/SeminarArc`。
- JDK 位置：`/home/yuukias/opt/jdk-17`；当前 `JAVA_HOME=/home/yuukias/opt/jdk-17`。
- WSL Android SDK 位置：`/home/yuukias/Android/Sdk`；当前 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 都指向该目录。
- Windows Android SDK 当前由用户安装在 `D:\Android\Sdk`；该 SDK 主要服务 Windows Android Studio / Emulator。使用前仍必须实际检查，不要把路径视为永远不变。
- `local.properties` 应保持 WSL 构建路径 `sdk.dir=/home/yuukias/Android/Sdk`；不要把 canonical WSL 工作区改指向 Windows SDK。
- `~/.bashrc` 已写入 JDK、Android SDK 和 scrcpy PATH：`$JAVA_HOME/bin`、`$ANDROID_HOME/cmdline-tools/latest/bin`、`$ANDROID_HOME/platform-tools`、`$SCRCPY_HOME`。
- Gradle 使用仓库内 wrapper：`./gradlew`；当前 wrapper 为 Gradle `8.10.2`，不要假设系统级 `gradle` 已安装。
- WSL ADB 位置：`/home/yuukias/Android/Sdk/platform-tools/adb`。
- Android command-line tools 已在 `PATH`：`sdkmanager` 和 `avdmanager` 位于 `/home/yuukias/Android/Sdk/cmdline-tools/latest/bin/`。
- scrcpy 位置：`/home/yuukias/Android/scrcpy-linux-x86_64-v4.1`；当前 `SCRCPY_HOME=/home/yuukias/Android/scrcpy-linux-x86_64-v4.1`，版本为 `scrcpy 4.1`。
- WSL 本身不承担 GUI Android Emulator；Windows Android Studio / Windows Emulator 是后续默认 connected/instrumentation 测试环境。
- 当前 WSL Android SDK 已安装 `platforms;android-36`、`build-tools;36.0.0`、`platform-tools 37.0.1`，与项目 `compileSdk = 36` 对齐。

## Emulator-first 测试策略

- **后续 Android 自动化默认 Emulator-first。** JVM unit tests、Room/repository/ViewModel 测试继续在 WSL canonical repo 运行；Compose/instrumentation/Room migration/connected Android tests 优先在 Windows Emulator 运行。远程物理真机不再承担日常 CI/connected test 角色。
- Windows Android Studio / Emulator 使用独立 Windows SDK `D:\Android\Sdk`。WSL 与 Windows SDK 可以重复下载 platform/build-tools/Gradle 缓存；为了隔离平台二进制和降低真机风险，不要强行共享 SDK 或 ADB server。
- 后续可维护一个 Windows/NTFS 上的**测试镜像 checkout**（建议 `D:\Code\SeminarArc-emulator`），只用于从 `origin/main` 同步代码并在 Windows Gradle/SDK/Emulator 上跑 connected/instrumentation tests。canonical 开发工作区仍是 `/home/yuukias/code/SeminarArc`。测试镜像不得反向成为源码事实来源。
- Windows Emulator 的实际 AVD 名称、serial（通常 `emulator-*`）、API 和 system image 必须由 setup task 现场检查后记录；不要提前假设 `emulator-5554` 永远固定。
- 运行任何 Windows connected/instrumentation test 前，必须先用 `D:\Android\Sdk\platform-tools\adb.exe devices -l` 确认 Windows ADB **只看到预期 `emulator-*` 设备，绝不能看到物理真机 serial `8cc54656`**。如果 Windows ADB 看见物理真机，立即停止 connected test，视为 transport isolation 异常。
- connected/instrumentation 自动安装链只允许对 Emulator 使用。只要目标设备不是明确的 `emulator-*`，默认禁止运行 connected test。
- 物理真机只保留给 Emulator 无法替代的少量硬件/系统 smoke，例如真实麦克风、真实 CameraX、厂商 ROM 后台/锁屏行为、通知与真实媒体链路。除非用户明确要求阶段性真机验收，否则不要主动碰真机。
- **`DEVICE_CHANNEL_BLOCKED` 不等于整个开发 task 被阻塞。** 物理真机不可见或进入保护状态时，只冻结 physical-device 子流程；凡是仍可通过 WSL headless 或 Windows Emulator 完成的开发、测试、lint、文档和修复都应继续。只有当前唯一剩余验收条件确实必须依赖物理设备时，task 才能标记 `BLOCKED_PHYSICAL_DEVICE`。
- Windows Emulator 相关配置不得通过 `wsl --shutdown`、usbipd 重绑、物理设备 transport 切换等方式获得便利。Emulator 环境必须与 GM1910 的 usbipd→WSL 链路隔离。
- 详细的 Emulator/真机职责与安全矩阵见 `docs/DEVICE_TESTING.md`。涉及 Android 设备测试的 task 应同时读取本节和该文档。

## 真机解锁与设备操作安全

- 当前常用本地测试机：serial `8cc54656`，型号 `GM1910` / OnePlus 7 Pro，Android 10 / API 29；每次真机验收前仍必须用 `adb devices -l` 和 `adb shell getprop` 复核，不要把这些信息当成永久不变。
- **该真机长期位于远程工位且经常处于无人值守状态。保持 USB / usbipd / WSL / ADB 链路连续可用是最高优先级安全约束，高于完成测试、收集 coverage、运行 instrumentation 或追求一次性验收完整度。只要某个命令是否可能影响连接存在合理不确定性，默认停止并请求用户确认，不得“先试一下”。**
- **严禁以任何直接或间接方式导致真机从 Windows、usbipd、WSL 或 ADB 中断开、重枚举、切换连接形态或失去当前可访问状态。禁止范围不仅包括显式 disconnect，也包括可能重置 USB/ADB transport、重启设备/服务、切换 USB mode、重新绑定 passthrough、重启 WSL 或触发高风险 instrumentation 安装链的操作。**
- **2026-08-30 已发生一次真实连接安全事件：在尝试 `connectedDebugAndroidTest` 的 instrumentation APK 安装阶段后，serial `8cc54656` 从 WSL ADB 消失；Windows `usbipd list` 仍能识别 `GM1910`，但状态退回 `Shared (forced)`，需要用户亲自在 Windows PowerShell 执行 `usbipd attach --wsl --busid 1-4` 才恢复 `Attached`。此事件证明 connected/instrumentation 自动安装链可能间接破坏远程 usbipd passthrough。以后绝不能把“掉线后还能重新 attach”视为可接受兜底，也不得由 agent 自行执行恢复。**
- 用户不在工位旁时，自动进入“远程无人值守设备保护模式”。该模式下，默认只允许必要的只读检查和 task 明确授权的单步设备动作；任何测试覆盖率收益都不足以抵消失去远程真机连接的风险。
- 除 `adb devices -l` 这种枚举命令外，所有针对真机的 ADB 命令必须显式指定 `-s 8cc54656`，不得依赖“只有一台设备”而使用隐式默认 target。执行前仍需先确认当前实际 serial，没有核对时不得盲用历史 serial。
- 当设备已经通过 usbipd `Attached` 给 WSL 时，不要再启动或使用 Windows 侧 ADB 去争用/探测同一手机；真机 ADB 操作统一使用 WSL 内 `/home/yuukias/Android/Sdk/platform-tools/adb`。不要在 Windows ADB 与 WSL ADB 之间来回切换 transport ownership。
- 不得把真机 PIN、密码或任何解锁 secret 写入本仓库、task、result、日志、截图说明、commit message 或 `AGENTS.md`。PIN 只能保存在用户本机私有 secret store、受权限保护的本地配置或专门 harness 的加密 secret 文件中。
- 如需自动短暂解锁，优先复用 EchoSelect 的 WSL 本地 harness 流程：`/home/yuukias/code/EchoSelect/scripts/device_test_harness/wsl_device_harness.py`。默认 PIN secret 文件为 `~/.config/echoselect/device-secrets/8cc54656.pin.wsl`，该文件不是仓库内容，不得复制进 SeminarArc。
- 可用的只读锁屏检查命令：`python /home/yuukias/code/EchoSelect/scripts/device_test_harness/wsl_device_harness.py check-lock-state --serial 8cc54656`。
- 可用的短暂解锁命令：`python /home/yuukias/code/EchoSelect/scripts/device_test_harness/wsl_device_harness.py unlock --serial 8cc54656 --evidence-root /tmp/seminararc-device-evidence`。该流程只允许 wake/swipe/text/keyevent 这类解锁输入，并通过 `deviceLocked=0` 验证成功；输出证据不得包含 PIN。
- 真机验收期间禁止执行会断开、重置或改变连接形态的命令，包括但不限于 `adb disconnect`、`adb reconnect`、`adb kill-server`、`adb reboot`、`adb tcpip`、`adb usb`、`adb pair`、`adb connect`、`svc usb`、修改 `sys.usb.config` / USB 模式、USB detach/unbind、`usbipd attach/detach/bind/unbind`、重启 usbipd 服务、`wsl --shutdown` 或任何等价操作。即使目的是“恢复连接”，也必须先取得用户明确授权；用户不在工位时默认由用户本人执行恢复动作。
- **远程无人值守真机默认禁止作为通用 connected/instrumentation CI 目标。未经用户对该次执行明确授权，不得运行 `connectedDebugAndroidTest`、`connectedAndroidTest`、任何 `connected*AndroidTest` / `device*AndroidTest` Gradle task、会自动安装 instrumentation APK 的 connected test、批量 device test、测试 runner 安装链、Gradle Managed Device 对真机的等价流程或其他可能触发 package/transport 重置的自动化设备测试。优先使用 JVM tests、静态检查或 emulator。**
- `adb install -r` 只允许在 task 明确需要更新 app 且当前设备链路已确认稳定时使用；必须使用显式 serial，例如 `/home/yuukias/Android/Sdk/platform-tools/adb -s 8cc54656 install -r ...`。执行前后都必须重新运行 `adb devices -l` 核对同一 serial 仍为 `device`。禁止把 `adb install -r` 扩展成 uninstall/reinstall/clear-data 流程。若安装过程中或安装后设备从 ADB 消失，立即停止所有设备命令，不得自动重连、重启 server、切换 USB、重绑 usbipd 或重复安装。
- 任何会写入、安装、启动 instrumentation、改变 package 状态、向 UI 注入输入或长时间占用设备的命令，在执行前必须先做设备 preflight：至少确认 `adb devices -l` 中目标 serial 唯一且状态为 `device`；执行后立即做 postflight。每一个风险动作单独执行，动作之间重新检查设备状态；不得把多个真机写操作串成无检查的长 shell 链。多个会访问真机的 Gradle/ADB/scrcpy 流程不得并行运行。
- `scrcpy` 只能作为单一、受控的观察/交互通道使用；禁止 `--tcpip` 或任何改变 transport 的选项。启动前后做 ADB 状态检查，且不得与 Gradle connected test、另一个 scrcpy 实例或其他真机自动化并行。
- 如果 `adb devices -l` 为空、设备状态不是 `device`、serial 改变，或 Windows/WSL 对设备可见性出现任何异常：**立即把真机视为连接安全事件并停止 physical-device 子流程。不得为了继续任务自行尝试恢复连接。** 只记录最后一个已知安全命令、设备消失发生在哪一步以及只读证据；如果仍有 headless/Emulator 工作则继续这些工作，只有唯一剩余要求必须依赖真机时才把整个 task 标为 `BLOCKED_PHYSICAL_DEVICE`。
- 如果设备从 `Attached` 退回 `Shared (forced)` 或其他非 Attached 状态，agent 不得自动运行 `usbipd attach`；即使历史上用户曾成功恢复过，也必须停下并让用户本人确认/执行。一次成功恢复不意味着未来掉线一定可恢复。
- 真机验收期间禁止卸载 app、清空 app data、删除或移动设备上的用户文件/媒体/数据库，除非用户对该具体动作给出明确授权。本项目调试产生的 app-owned 测试数据可以保留到用户手动清理。
- `scrcpy -S` 或黑屏只算显示隐私措施，不等于安全锁屏；如果某项验收要求锁屏状态，必须用 `dumpsys trust` / `dumpsys window policy` 或 harness 的 `check-lock-state` 明确验证。
- 每次 task 只要涉及 physical device、ADB、scrcpy、usbipd、instrumentation 或安装 APK，Codex 必须在执行任何设备命令前重新阅读本节；不得仅凭之前线程中“设备一直可用”的状态继续操作。

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
