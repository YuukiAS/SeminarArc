# SeminarArc Device Testing Policy

Status: active
Updated: 2026-09-10

## 1. 目标

SeminarArc 后续采用 **Emulator-first + protected physical-device smoke** 的测试策略。

原因不是减少真机测试价值，而是把不同测试职责分开：自动化测试应运行在可重建、可反复安装的 Emulator 上；远程无人值守真机只用于 Emulator 无法替代的真实硬件和系统行为验收。

## 2. 环境职责

### WSL canonical development

- canonical repo：`/home/yuukias/code/SeminarArc`
- 不要使用旧的 `/home/yuukias/Code/SeminarArc`。
- JDK 位置：`/home/yuukias/opt/jdk-17`；当前 `JAVA_HOME=/home/yuukias/opt/jdk-17`。
- WSL Android SDK：`/home/yuukias/Android/Sdk`
- 当前 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 都指向该目录。
- 负责：JVM tests、build、lint、代码开发、Git、文档。
- WSL repo 不改指向 Windows SDK。
- `local.properties` 应保持 WSL 构建路径 `sdk.dir=/home/yuukias/Android/Sdk`。
- `~/.bashrc` 已写入 JDK、Android SDK 和 scrcpy PATH：`$JAVA_HOME/bin`、
  `$ANDROID_HOME/cmdline-tools/latest/bin`、`$ANDROID_HOME/platform-tools`、
  `$SCRCPY_HOME`。
- Gradle 使用仓库内 wrapper：`./gradlew`；当前 wrapper 为 Gradle `8.10.2`。
- WSL ADB 位置：`/home/yuukias/Android/Sdk/platform-tools/adb`。
- Android command-line tools 已在 `PATH`：`sdkmanager` 和 `avdmanager` 位于
  `/home/yuukias/Android/Sdk/cmdline-tools/latest/bin/`。
- scrcpy 位置：`/home/yuukias/Android/scrcpy-linux-x86_64-v4.1`；当前
  `SCRCPY_HOME=/home/yuukias/Android/scrcpy-linux-x86_64-v4.1`，版本为
  `scrcpy 4.1`。
- 当前 WSL Android SDK 已安装 `platforms;android-36`、`build-tools;36.0.0`、
  `platform-tools 37.0.1`，与项目 `compileSdk = 36` 对齐。
- 当前 `gradle.properties` 固定
  `kotlin.compiler.execution.strategy=in-process`，用于避免 Kotlin daemon 在
  `C:\Users\<user>\AppData\Local\kotlin\daemon` 创建 marker/cache 文件；不要为了
  提速随手改回 daemon，除非同时把 Kotlin daemon home 可靠迁到 `D:\` 并验证不触碰
  C 盘。

### Windows Emulator

- Windows Android SDK：`D:\Android\Sdk`
- Android Studio / Android Emulator 运行在 Windows。
- 负责：Compose instrumentation、Room migration instrumentation、connected Android tests、自动安装测试 APK、可重复 UI regression。
- 2026-08-30 Windows 原生 Codex 已确认：AVD `Pixel_8`，serial `emulator-5554`，model `sdk_gphone64_x86_64`，API `36`，ABI `x86_64`。
- Emulator version：`37.1.11.0 (build_id 15917651)`；acceleration：`WHPX(10.0.26100) is installed and usable`。
- Windows ADB preferred gate 是仅看到 `emulator-*`。2026-09-10 已新增并验证 mixed-inventory fallback：当 Windows ADB 同时看到 protected physical serial `8cc54656 unauthorized` 与 `emulator-5554 device` 时，禁止 unscoped connected Gradle task，但 task-authorized `adb -s emulator-5554 install` + `adb -s emulator-5554 shell am instrument` 可完成 Emulator-only instrumentation。
- 2026-08-30 Windows 原生 onboarding/regression 结果：`EMULATOR_REGRESSION_PASS`。
- 历史 WSL onboarding 尝试曾因 WSL session 无法执行 Windows interop 且 `/mnt/d` 只读而记录 `EMULATOR_INFRA_NEEDS_FIX`；该 blocker 仅适用于当时的 WSL interop 场景，不适用于 Windows 原生 Codex 执行。
- Android Studio JBR 实际位于 `C:\Android\Android Studio\jbr`；该环境当前为
  OpenJDK `25.0.2`，与本仓库 Gradle/Kotlin DSL 不兼容。Windows Gradle 验证可使用
  本地 JDK 17：`D:\Code\_jdks\jdk-17.0.20.1+1`，不要修改系统级 JAVA_HOME。
- Windows 原生 Codex 执行 Gradle/Android 构建时，大型缓存、wrapper 下载、
  Gradle user home、Android preferences 和构建环境应显式落在 `D:\`。默认不要让
  Gradle 写入 `C:\.gradle`、`C:\.android`、`C:\Users\<user>\.gradle` 或其他 C 盘
  缓存；当前实测组合是
  `GRADLE_USER_HOME=D:\Code\SeminarArc-emulator\.gradle-user-home`、
  `ANDROID_PREFS_ROOT=D:\Code\SeminarArc-emulator\.android-user-home`，并给 Gradle
  JVM 传入 `-Duser.home=D:\Code\SeminarArc-emulator\.gradle-user-home`。
- Android preference 位置只能设置一种入口；不要同时设置 `ANDROID_USER_HOME`、
  `ANDROID_PREFS_ROOT` 或 deprecated `ANDROID_SDK_HOME`，否则 AGP 会报 location
  conflict。
- 2026-08-30 本 WSL session 中 `/mnt/c` 与 `/mnt/d` 以 `ro` 挂载，且直接执行
  `/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe` 或
  `/mnt/c/Windows/System32/cmd.exe` 返回 `Invalid argument`。这表示该 Codex/WSL
  进程暂不能通过 Windows interop 操作 Windows SDK/Emulator，也不能从 WSL 写入
  `D:\Code\SeminarArc-emulator`；恢复能力需要用户在 Windows/WSL 环境层处理，不得用
  `wsl --shutdown`、usbipd 或真机 transport 操作绕过。

### Protected physical device

- 当前保护真机：`8cc54656` / `GM1910` / OnePlus 7 Pro / Android 10 API 29。
- 通过 usbipd attach 给 WSL。
- 只用于少量真实硬件/系统 smoke：真实麦克风、CameraX、厂商 ROM 后台/锁屏、notification、真实媒体链路等。
- 不再作为日常 connected/instrumentation target。
- 每次真机验收前仍必须用 `adb devices -l` 和 `adb shell getprop` 复核，不要把这些
  信息当成永久不变。

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
- 2026-08-30 已建立并验证 `D:\Code\SeminarArc-emulator`。
- Android Studio JBR 当前位于 `C:\Android\Android Studio\jbr`，但该环境中为 OpenJDK `25.0.2`，与当前 Gradle/Kotlin DSL 不兼容；本轮 Windows Gradle 使用本地 JDK 17：`D:\Code\_jdks\jdk-17.0.20.1+1`，未修改系统级 JAVA_HOME。
- 如果 `/mnt/d` 在 WSL 中是只读挂载，或 Windows interop 无法执行 `powershell.exe` / `cmd.exe`，Codex 不能自动创建该 mirror。此时应记录环境状态，先保留 WSL canonical headless gate，通过用户恢复 Windows interop 或在 Windows 侧手动准备 mirror 后再重跑 onboarding。

## 5. Emulator connected-test safety

运行 Windows connected/instrumentation test 前必须确认：

1. Windows SDK 路径实际存在。
2. Emulator 已启动，且预期 `emulator-*` 处于 `device`。
3. 先运行只读 Windows `adb.exe devices -l` inventory。
4. Preferred fast path：inventory 只看到预期 `emulator-*` 时，可运行普通 connected Gradle task。
5. Mixed-inventory fallback：inventory 同时看到 protected physical serial `8cc54656` 时，禁止 `connectedDebugAndroidTest`、`connectedAndroidTest`、任何 `connected*AndroidTest` 或 `device*AndroidTest`；只能在 task 明确授权时由 Gradle 构建 app APK/androidTest APK，再用 `adb -s <emulator>` 显式 install 和 `am instrument`。
6. 不得为了让 `8cc54656` 消失而执行 ADB/USB/usbipd/WSL transport 修改。

无论哪条路径，physical serial 永远不得接收 install、shell、input、instrumentation、scrcpy 或 transport 命令。

2026-09-10 Windows 原生 Codex 已验证 mixed-inventory explicit-emulator lane：
sandbox 内直接运行 `D:\Android\Sdk\platform-tools\adb.exe devices -l` 仍可能失败于
`Cannot mkdir '\.android': Permission denied`；非 sandbox 且临时设置
`USERPROFILE`、`HOME`、`ANDROID_SDK_HOME` 到
`D:\Code\SeminarArc-emulator\.android-user-home` 后，ADB 可枚举设备。

## 6. Physical-device protection

根 `AGENTS.md` 负责醒目的不可妥协安全摘要和 locator。本文件负责详细
device/environment/test mechanics、命令限制、易变 inventory、mixed-inventory
流程和历史事故证据。不要创建第三份 device/environment manual。

特别强调：

- 禁止 physical device 上的 `connected*AndroidTest` / instrumentation 自动安装链。
- 禁止 agent 自行执行 ADB/usbipd transport 恢复。
- 物理设备异常只冻结 physical-device channel；只要 WSL headless 或 Emulator 仍能工作，开发任务继续。
- 真机专项 smoke 必须由 task 明确授权，且使用显式 serial、单步动作和 preflight/postflight。
- 远程无人值守真机默认禁止作为通用 connected/instrumentation CI 目标。未经用户对
  该次执行明确授权，不得运行 `connectedDebugAndroidTest`、`connectedAndroidTest`、
  任何 `connected*AndroidTest` / `device*AndroidTest` Gradle task、会自动安装
  instrumentation APK 的 connected test、批量 device test、测试 runner 安装链、
  Gradle Managed Device 对真机的等价流程或其他可能触发 package/transport 重置的
  自动化设备测试。优先使用 JVM tests、静态检查或 emulator。
- 除 `adb devices -l` 这种枚举命令外，所有针对真机的 ADB 命令必须显式指定
  `-s 8cc54656`，不得依赖“只有一台设备”而使用隐式默认 target。执行前仍需先确认
  当前实际 serial，没有核对时不得盲用历史 serial。
- 当设备已经通过 usbipd `Attached` 给 WSL 时，不要再启动或使用 Windows 侧 ADB 去
  争用/探测同一手机；真机 ADB 操作统一使用 WSL 内
  `/home/yuukias/Android/Sdk/platform-tools/adb`。
- 如需自动短暂解锁，优先复用 EchoSelect 的 WSL 本地 harness：
  `/home/yuukias/code/EchoSelect/scripts/device_test_harness/wsl_device_harness.py`。
  默认 PIN secret 文件为 `~/.config/echoselect/device-secrets/8cc54656.pin.wsl`，
  该文件不是仓库内容，不得复制进 SeminarArc。
- 可用只读锁屏检查：
  `python /home/yuukias/code/EchoSelect/scripts/device_test_harness/wsl_device_harness.py check-lock-state --serial 8cc54656`。
- 可用短暂解锁：
  `python /home/yuukias/code/EchoSelect/scripts/device_test_harness/wsl_device_harness.py unlock --serial 8cc54656 --evidence-root /tmp/seminararc-device-evidence`。
  该流程只允许 wake/swipe/text/keyevent 这类解锁输入，并通过 `deviceLocked=0`
  验证成功；输出证据不得包含 PIN。
- 真机验收期间禁止执行会断开、重置或改变连接形态的命令，包括但不限于
  `adb disconnect`、`adb reconnect`、`adb kill-server`、`adb reboot`、`adb tcpip`、
  `adb usb`、`adb pair`、`adb connect`、`svc usb`、修改 `sys.usb.config` / USB 模式、
  USB detach/unbind、`usbipd attach/detach/bind/unbind`、重启 usbipd 服务、
  `wsl --shutdown` 或任何等价操作。即使目的是“恢复连接”，也必须先取得用户明确授权。
- `adb install -r` 只允许在 task 明确需要更新 app 且当前设备链路已确认稳定时使用；
  必须使用显式 serial，执行前后都必须重新运行 `adb devices -l` 核对同一 serial 仍为
  `device`。禁止把 `adb install -r` 扩展成 uninstall/reinstall/clear-data 流程。
- 任何会写入、安装、启动 instrumentation、改变 package 状态、向 UI 注入输入或长时间
  占用设备的命令，在执行前必须先做设备 preflight，执行后立即做 postflight；不得把
  多个真机写操作串成无检查的长 shell 链。
- `scrcpy` 只能作为单一、受控的观察/交互通道使用；禁止 `--tcpip` 或任何改变
  transport 的选项。启动前后做 ADB 状态检查，且不得与 Gradle connected test、
  另一个 scrcpy 实例或其他真机自动化并行。
- 如果 `adb devices -l` 为空、设备状态不是 `device`、serial 改变，或 Windows/WSL 对
  设备可见性出现任何异常，立即把真机视为连接安全事件并停止 physical-device 子流程；
  不得为了继续任务自行尝试恢复连接。
- 如果设备从 `Attached` 退回 `Shared (forced)` 或其他非 Attached 状态，agent 不得
  自动运行 `usbipd attach`。
- 真机验收期间禁止卸载 app、清空 app data、删除或移动设备上的用户文件/媒体/数据库，
  除非用户对该具体动作给出明确授权。
- `scrcpy -S` 或黑屏只算显示隐私措施，不等于安全锁屏；如果某项验收要求锁屏状态，
  必须用 `dumpsys trust` / `dumpsys window policy` 或 harness 的 `check-lock-state`
  明确验证。

## 7. 2026-08-30 连接事故

此前一次 `connectedDebugAndroidTest` 在 instrumentation APK 安装阶段后，GM1910 从 WSL ADB 消失，usbipd 状态退回 `Shared (forced)`。用户随后在 Windows PowerShell 手动执行 attach 才恢复。

因此后续原则是：

> 自动化测试可以失败，Emulator 可以重建；远程无人值守真机 transport 不应成为自动化测试的风险承担者。

## 8. 当前迁移策略

从 2026-08-30 起：

1. Windows Emulator 环境已验证，当前结果为 `EMULATOR_REGRESSION_PASS`。
2. existing instrumentation / migration / connected regression 已可在 Windows Emulator 执行。
3. 每次涉及 GM1910 的 task 只做 task 明确授权的只读/单步检查，默认不操作真机。
4. 原先等待真机完整 E2E 的 stalled task 不再作为日常开发 blocker；物理验收延期到未来明确的 hardware acceptance checkpoint。
5. 2026-09-10 `v0.5.0-alpha.1` closeout 在 mixed-inventory fallback 下通过：Windows ADB inventory 为 `8cc54656 unauthorized` + `emulator-5554 device`，所有非 inventory ADB 命令均显式使用 `-s emulator-5554`，完整 instrumentation suite `OK (24 tests)`。
