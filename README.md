# SeminarArc

SeminarArc 是一个本地优先的原生 Android 应用，用于记录、回顾和整理线下学术 seminar。每一场 seminar 都是唯一的所有权容器；当前 `0.1.x` 的录音、幻灯片照片、问题、笔记、clip 和导出记录都必须归属于明确的 `seminarId`。

产品主循环：

```text
Prepare -> Capture -> Reconstruct -> Research -> Export
```

## 当前状态

仓库当前已完成 `0.2.x` Local Visual Reconstruction 收口，可进入 `0.3.x` planning。

已经具备：

- Android/Compose 单模块应用基线。
- Material 3 theme 和 SeminarArc 设计令牌映射。
- Room-backed seminar 容器模型。
- Seminar list、editor、detail 基础流程。
- Abstract PDF import / replace / remove 的本地文件生命周期基础。
- One-active-seminar invariant 与 session start 语义。
- Microphone foreground service、本地 `.m4a` recording backend、ongoing notification 和 `RecordingEntity` durable lifecycle。
- Active Session route、录音状态恢复 UI、权限拒绝状态、notification 返回现场页、以及 End Seminar 的 stop/finalize 后完成 seminar 流程。
- Seminar Detail 的完整录音回放：Media3 页面内播放器、Play/Pause/Seek、duration/position、文件缺失和失败录音状态。
- CameraX slide capture、photo-only seminar session、MARK/PHOTO/QUESTION/NOTE timeline 写入、统一 timeline review route、last-photo undo/retake 和 `Play from here` 回放入口。
- Clip generation 韧性基础：MARK 自动创建 `PENDING` clip、WorkManager 本地 `.m4a` 裁剪、`READY` clip 播放入口、failed retry 和完整录音 fallback。
- 单 seminar Markdown/ZIP 本地导出：包含 metadata、abstract、recording summary、timeline、relative media links、missing media skip 记录，并通过 `ACTION_CREATE_DOCUMENT` 与 Android share sheet 暴露。
- `0.1.x` 到 `0.2.x` 的分阶段计划文档。
- `0.2.x` readiness gate：Room v2->v3 migration 设计、bundled ML Kit Text Recognition v2 OCR 决策、Android 原生图像增强决策、provider/privacy/license 边界和 Research Reconstruction 工作区规格。
- `0.2.x` Room version 3 数据基础：`SeminarAsset`、`ProcessingJob`、`OcrResult`、tags/key-slide mapping、schema `3.json` 和 v2->v3 backfill migration。
- `0.2.x` 本地图像增强基础：Android Bitmap/Matrix/Canvas/ColorMatrix provider、rotate/crop/perspective/readability options、原图保留的 enhanced derived asset 输出，以及 job success/failure/idempotency 单元测试。
- `0.2.x` 本地 OCR 基础：bundled ML Kit Text Recognition Latin/Chinese 依赖、`TextOcrProvider`、app-owned OCR block JSON、`RunTextOcrForAssetUseCase` 和 OCR job/result JVM 测试。
- `0.2.x` Reconstruction workspace 基础：照片 asset、OCR result、processing job、key-slide tag、搜索 query 和 OCR 状态过滤组合成可渲染 UI state，并从 Seminar Detail 进入 Compose 工作区进行照片预览、增强、OCR、OCR 编辑和 key-slide 标记。
- `0.2.x` WorkManager-backed processing queue：OCR 和 image enhancement 支持 durable `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`，并具备 duplicate idempotency、retry、cancel 和 process-start recovery。
- Windows Emulator closeout：`MIGRATION_2_3`、bundled ML Kit Latin/Chinese/mixed/empty OCR、Android Bitmap enhancement、Reconstruction workspace UI 和 0.1.x regression 已在 `Pixel_8` API 36 Emulator 上通过 connected 验证。

尚未声明完成：

- 非破坏性真机完整 E2E 验收：创建 seminar、录音、拍照、timeline、clip、重启后持久化、离线导出和删除清理仍需在用户授权的设备会话中执行。
- 转写、AI 总结、Notion、cloud sync、Reference lookup、公式 OCR、广告或支付。

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
2. `0.1.2` recording validation：真机/设备录音、notification、完成后 `.m4a` 播放和文档收口验收。
3. `0.1.3` capture/timeline：CameraX、photo-only、offset、现场交互和统一 timeline。
4. `0.1.4` clip：WorkManager、clip 状态、retry 和 full-recording fallback。
5. `0.1.5` local MVP：Markdown/ZIP 导出、数据清理、验收、README/隐私/CI 收口。
6. `0.2.x` implementation：asset/job/OCR/tag data foundation、WorkManager processing queue、local image enhancement、local OCR、搜索、标签和 Research Reconstruction workspace UI。

实际执行时必须先写入 `prompts/tasks/<id>_task.md`，再由 Codex 按任务单执行并回写 `prompts/tasks/<id>_result.md`。

## 本地构建

在 Java、Android SDK 和 Gradle wrapper 前置条件可用后，预期命令为：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
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

当前明确不做：

- 强制登录或云同步。
- 自动上传全部录音或全部照片。
- formula OCR、转写、AI 总结、Reference lookup、Notion 或 cloud provider API 接入。
- 广告、订阅、支付。
- iOS 或 web 客户端。

普通本地 OCR、图像增强和 Reconstruction workspace 已在 `0.2.x` 实现；上述能力可以保留在 roadmap 中，但不能在 UI 中伪装成已完成。
