# SeminarArc

SeminarArc 是一个本地优先的原生 Android 应用，用于记录、回顾和整理线下学术 seminar。每一场 seminar 都是唯一的所有权容器，后续录音、幻灯片照片、问题、笔记、clip、OCR、参考文献候选和导出记录都必须归属于明确的 `seminarId`。

产品主循环：

```text
Prepare -> Capture -> Reconstruct -> Research -> Export
```

## 当前状态

仓库当前处于 `0.1.2-dev` 录音基础设施批次。

已经具备：

- Android/Compose 单模块应用基线。
- Material 3 theme 和 SeminarArc 设计令牌映射。
- Room-backed seminar 容器模型。
- Seminar list、editor、detail 基础流程。
- Abstract PDF import / replace / remove 的本地文件生命周期基础。
- One-active-seminar invariant 与 session start 语义。
- Microphone foreground service、本地 `.m4a` recording backend、ongoing notification 和 `RecordingEntity` durable lifecycle。
- Active Session route、录音状态恢复 UI、权限拒绝状态、notification 返回现场页、以及 End Seminar 的 stop/finalize 后完成 seminar 流程。
- `0.1.x` 到 `0.2.x` 的分阶段计划文档。

尚未声明完成：

- CameraX slide capture。
- 录音中 timeline event 写入。
- Clip generation、retry 和 full-recording fallback。
- Full recording playback UI。
- Markdown/ZIP 导出和最终本地 MVP 验收。

## 文档入口

- 产品级路线图：`TODO.md`
- `0.1.x` 总体实现合同：`docs/plans/0.1.x-mvp-implementation-plan.md`
- 后续分阶段计划索引：`docs/plans/0.1.x-development-plan-index.md`
- 当前已完成基础批次记录：`docs/plans/0.1.x-mvp-execution-batch-01.md`
- 架构说明：`docs/ARCHITECTURE.md`
- 隐私说明：`docs/PRIVACY.md`
- 设计交付：`design/`
- 变更记录：`CHANGELOG.md`

## 推荐开发顺序

后续不要直接执行整个 `TODO.md`。默认按以下顺序把计划拆成小 task：

1. `0.1.1` closeout：构建、CI、Room、UI、PDF 生命周期和文档收口。
2. `0.1.2` recording：foreground service、active seminar invariant、恢复和完整录音回放。
3. `0.1.3` capture/timeline：CameraX、photo-only、offset、现场交互和统一 timeline。
4. `0.1.4` clip：WorkManager、clip 状态、retry 和 full-recording fallback。
5. `0.1.5` local MVP：Markdown/ZIP 导出、数据清理、验收、README/隐私/CI 收口。
6. `0.2.x` readiness：OCR、provider、Room migration 和 Research Reconstruction 设计审查。

实际执行时必须先写入 `prompts/tasks/<id>_task.md`，再由 Codex 按任务单执行并回写 `prompts/tasks/<id>_result.md`。

## 本地构建

在 Java、Android SDK 和 Gradle wrapper 前置条件可用后，预期命令为：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Windows PowerShell 可使用：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

## 权限规划

- 麦克风：`0.1.2` 起用于 seminar 录音。
- 通知：`0.1.2` 起用于 foreground recording service。
- 相机：`0.1.3` 起用于 slide capture。

权限拒绝必须有真实状态和恢复路径；不能用静态 Compose 页面冒充可用功能。

## Active Session 与恢复

`0.1.2` 现在把详情页的 `Start seminar` / `Resume seminar` 正常流程导航到 Active Session，而不是只在详情页显示临时消息。Active Session 从 Room 中的 durable seminar/recording facts 和当前进程 runtime recorder state 推导 UI：

- 当前进程持有 live recorder 时显示 `Recording` 与本地 elapsed timer。
- Room 中遗留 `RECORDING` row 但当前进程没有 live recorder 时显示 recovery，而不是伪装正在录音。
- process-start recovery 会先捕获启动前 stale recording IDs，再只标记这些 rows 为 `FAILED`，避免误杀同一进程中新创建的录音。
- `End Seminar` 会先停止并 finalize recorder，再条件化完成 seminar 并写 `sessionEndedAt`；stop/finalize 失败时不会把 seminar 宣称为 completed。

计时器使用 `RecordingSession.startedAt` 或 seminar `sessionStartedAt` 作为事实来源，UI 本地 tick 只负责显示，不会每秒写 Room。

## 当前明确不做

在本地采集闭环完成前，不做：

- 强制登录或云同步。
- 自动上传全部录音或全部照片。
- OCR、转写、AI 总结、Notion 或 provider API 接入。
- 广告、订阅、支付。
- iOS 或 web 客户端。

这些能力可以保留在 roadmap 中，但不能在 `0.1.x` UI 中伪装成已完成。
