# SeminarArc Device Testing Policy

Status: active
Updated: 2026-08-30

## 1. 目标

SeminarArc 后续采用 **Emulator-first + protected physical-device smoke** 的测试策略。

原因不是减少真机测试价值，而是把不同测试职责分开：自动化测试应运行在可重建、可反复安装的 Emulator 上；远程无人值守真机只用于 Emulator 无法替代的真实硬件和系统行为验收。

## 2. 环境职责

### WSL canonical development

- canonical repo：`/home/yuukias/code/SeminarArc`
- WSL Android SDK：`/home/yuukias/Android/Sdk`
- 负责：JVM tests、build、lint、代码开发、Git、文档。
- WSL repo 不改指向 Windows SDK。

### Windows Emulator

- Windows Android SDK：`D:\Android\Sdk`
- Android Studio / Android Emulator 运行在 Windows。
- 负责：Compose instrumentation、Room migration instrumentation、connected Android tests、自动安装测试 APK、可重复 UI regression。
- Windows Emulator 的 AVD 名、API、serial 由 onboarding task 现场确认后再写入仓库事实记录。
- 2026-08-30 onboarding 尝试时，当前 Codex WSL session 无法执行 Windows interop：`powershell.exe` 不在 PATH，直接调用 `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe` 和 `/mnt/c/Windows/System32/cmd.exe` 均返回 `Invalid argument`；同时 `/mnt/d` 为只读挂载。因此本轮无法验证 `D:\Android\Sdk`、AVD、Windows ADB 或建立 `D:\Code\SeminarArc-emulator`。

### Protected physical device

- 当前保护真机：`8cc54656` / `GM1910` / OnePlus 7 Pro / Android 10 API 29。
- 通过 usbipd attach 给 WSL。
- 只用于少量真实硬件/系统 smoke：真实麦克风、CameraX、厂商 ROM 后台/锁屏、notification、真实媒体链路等。
- 不再作为日常 connected/instrumentation target。

## 3. 默认测试矩阵

| 测试类型 | 默认环境 | 真机是否需要 |
| --- | --- | --- |
| Kotlin/JVM unit tests | WSL | 否 |
| Repository / ViewModel / use case | WSL | 否 |
| Room JVM tests | WSL | 否 |
| Build / lint | WSL | 否 |
| Compose instrumentation | Windows Emulator | 否 |
| Room migration instrumentation | Windows Emulator | 否 |
| connected Android tests | Windows Emulator | 否 |
| CameraX 基本流程 | Windows Emulator 优先 | 最终专项 smoke 才需要 |
| 麦克风真实录音 | Emulator 可做流程回归 | 阶段性真实硬件验收需要 |
| 厂商 ROM 后台/锁屏 | 不可靠 | 真机专项 smoke |
| 最终 release/device acceptance | Emulator + 少量真机 | 需要时人工明确授权 |

## 4. Windows test mirror

为了避免 WSL canonical repo 与 Windows Android SDK/Emulator 混用平台二进制，建议建立独立 NTFS 测试镜像：

`D:\Code\SeminarArc-emulator`

规则：

- 该目录只用于同步 `origin/main` 和运行 Windows Gradle/connected tests。
- canonical source of truth 始终是 WSL repo。
- 不在 Windows mirror 中开发独立功能或产生未同步 commit。
- 每次 connected test 前先确认 mirror 对应目标 commit。
- Windows Gradle/JDK/SDK 缓存允许与 WSL 重复，隔离优先于节省磁盘。
- 如果 `/mnt/d` 在 WSL 中是只读挂载，或 Windows interop 无法执行 `powershell.exe` / `cmd.exe`，Codex 不能自动创建该 mirror。此时应记录环境状态，先保留 WSL canonical headless gate，通过用户恢复 Windows interop 或在 Windows 侧手动准备 mirror 后再重跑 onboarding。

## 5. Emulator connected-test safety

运行 Windows connected/instrumentation test 前必须确认：

1. Windows SDK 路径实际存在。
2. Emulator 已启动。
3. Windows `adb.exe devices -l` 只看到预期的 `emulator-*`。
4. Windows ADB 绝不能看到 physical serial `8cc54656`。
5. 如果 Windows ADB 看见物理真机，停止 connected test，不尝试自行修改 usbipd/ADB transport。

只有满足以上条件，才允许 Windows mirror 运行 connected/instrumentation 自动安装链。

## 6. Physical-device protection

完整物理真机约束以 `AGENTS.md` 为最高优先级。

特别强调：

- 禁止 physical device 上的 `connected*AndroidTest` / instrumentation 自动安装链。
- 禁止 agent 自行执行 ADB/usbipd transport 恢复。
- 物理设备异常只冻结 physical-device channel；只要 WSL headless 或 Emulator 仍能工作，开发任务继续。
- 真机专项 smoke 必须由 task 明确授权，且使用显式 serial、单步动作和 preflight/postflight。

## 7. 2026-08-30 连接事故

此前一次 `connectedDebugAndroidTest` 在 instrumentation APK 安装阶段后，GM1910 从 WSL ADB 消失，usbipd 状态退回 `Shared (forced)`。用户随后在 Windows PowerShell 手动执行 attach 才恢复。

因此后续原则是：

> 自动化测试可以失败，Emulator 可以重建；远程无人值守真机 transport 不应成为自动化测试的风险承担者。

## 8. 当前迁移策略

从 2026-08-30 起：

1. 先验证 Windows Emulator 环境。
2. 将 existing instrumentation / migration / connected regression 迁移到 Emulator。
3. 只做一次只读检查确认 GM1910 仍在线，随后默认不再操作真机。
4. 原先等待真机完整 E2E 的 stalled task 不再作为日常开发 blocker；物理验收延期到未来明确的 hardware acceptance checkpoint。
