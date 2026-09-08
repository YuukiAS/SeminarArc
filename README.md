# SeminarArc

SeminarArc 是一个本地优先的原生 Android 应用，用于记录、回顾和整理线下学术 seminar。每一场 seminar 都是唯一的所有权容器；当前 `0.1.x` 的录音、幻灯片照片、问题、笔记、clip 和导出记录都必须归属于明确的 `seminarId`。

产品主循环：

```text
Prepare -> Capture -> Reconstruct -> Research -> Export
```

## 当前状态

仓库当前已完成 `0.3.x` Reference Candidate + Seminar Brief 收口，并正在建立 `0.3.1-internal` dogfood APK 分发链；`0.1.x` 本地采集、`0.2.x` Local Visual Reconstruction 和 `0.3.x` 人工复核式论文候选/brief/export 闭环均已落地。

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
- `0.3.x` Room version 4 reference/brief 数据基础：reference evidence、lookup attempts、canonical candidates、provider observations、Seminar Brief、confirmed references 和 key-slide join tables。
- `0.3.x` opt-in metadata lookup：Crossref、OpenAlex、DataCite keyless provider 边界，用户显式选择 evidence 后才发送最小 DOI/title/author/year clue，不发送照片、录音、完整 OCR corpus、timeline、导出包或 app-private 路径。
- `0.3.x` deterministic candidate ranking/dedup：`reference-match-v1` 记录 match reason、score、confidence band、normalized DOI/title/year/source provenance，并支持 confirm/reject/reopen。
- `0.3.x` Reference Candidate Review / Seminar Brief UI：从 Reconstruction workspace 进入，展示 evidence picker、query preview、provider plan、lookup status、候选复核、可编辑 brief 和 key-slide linking。
- `0.3.x` Markdown/ZIP export：导出 confirmed references、brief sections 和 key slides；未确认候选不会自动写入 brief。
- `0.3.1-internal` 分发基础：internal build identity 使用 `com.yuukias.seminararc.internal`，默认 `versionName = 0.3.1-internal.1`、`versionCode = 30101`，签名凭据只从 repo 外 Windows local secret store 或环境变量读取。用户 APK 下载入口见 `docs/INTERNAL_DISTRIBUTION.md`。
- `0.4.x` schema foundation：Room version 5 新增 transcript、timestamped transcript segment 和 summary draft 本地表，为后续 provider-independent 转写/总结/Notion export pipeline 提供持久化边界。
- `0.4.x` provider contract foundation：新增本地安全的 `TranscriptionProvider` 与 `SummaryProvider` domain contract，并用 fake-provider JVM tests 固定 timestamp、selected input、provenance 和 retryability 语义。
- `0.4.x` transcript repository foundation：新增 `TranscriptDao` / `TranscriptRepository` / `RunTranscriptionForRecordingUseCase`，可通过 fake/local provider 从已完成本地录音保存 timestamped transcript segments。
- `0.4.x` summary draft use case foundation：新增 `DraftSummaryForSeminarUseCase`，基于用户选择的 transcript segments、已确认 references、key-slide captions 和 notes 构造 provider-independent summary request，并把 provider 成功/失败结果写入 `summary_drafts`；不会覆盖人工 `SeminarBrief`。
- `0.4.x` transcript timeline windows foundation：新增 `BuildTranscriptTimelineWindowsUseCase`，可按 timeline/photo offset 生成 transcript segment windows、关联 photo asset，并输出未来 UI/export 可复用 preview。
- `0.4.x` transcript review UI foundation：从 Reconstruction workspace 进入，查看 transcript 列表、segments、timeline/photo windows 和 summary draft 状态；真实 provider 执行入口仍未接线。
- `0.4.x` Markdown/ZIP transcript summary export foundation：本地导出现在包含 transcript review metadata、timestamped segments、timeline windows 和 editable generated summary drafts；ZIP 继续写入增强后的 `seminar.md` 与可读媒体。
- `0.4.x` transcription durable queue foundation：`ProcessingWorkScheduler` 可为完成录音创建 durable `TRANSCRIPTION` job 并交给 WorkManager；默认 `UnavailableTranscriptionProvider` 只记录未配置失败，不上传音频或伪造转写。
- `0.4.x` transcript review queue entry foundation：Transcript Review 的转写动作会为最新完成录音排入 durable transcription job，并用 snackbar 明确提示仍需配置真实 provider 才会产生 segments。
- `0.4.x` summary durable queue foundation：Room version 6 为 `processing_jobs` 增加 `inputPayloadJson`，Transcript Review 可为当前 transcript segments 排入 durable `SUMMARY_DRAFT` WorkManager job；默认 `UnavailableSummaryProvider` 只记录未配置失败，不上传 transcript、references、notes 或伪造总结。
- `0.4.x` Notion-ready export contract foundation：新增本地 `SeminarNotionReadyRenderer`，可从现有 export document 生成 block-like Notion-ready document 和 Markdown 预览；不包含 Notion OAuth、API client、token 或上传行为。
- `0.4.x` transcript processing queue UI foundation：Transcript Review 现在展示本地 `TRANSCRIPTION` / `SUMMARY_DRAFT` job 状态，并支持对可重试/可取消 job 走 durable scheduler 的 retry/cancel。
- `0.4.x` transcript segment edit foundation：Transcript Review 可在本地编辑并保存单条 timestamped segment 文本，更新 `isEdited` 与 transcript activity 时间，不触发 provider 或上传。
- `0.4.x` manual transcript import foundation：Transcript Review 可把粘贴的本地文本导入为 `MANUAL` transcript，每个非空行成为可编辑 coarse segment，供 summary/export 后续使用。
- `0.4.x` summary draft edit foundation：Transcript Review 可在本地编辑并保存 generated summary draft 的各个 brief 字段，保存后作为 `DRAFT` 继续供 Markdown/ZIP 和 Notion-ready export 使用。
- `0.4.x` summary draft apply foundation：Transcript Review 可由用户显式把某个 summary draft 应用到人工 `SeminarBrief`，让 transcript/summary 整理结果进入既有 Reference Review brief/export 闭环。
- `0.4.x` Notion-ready share entry foundation：Seminar Detail 可通过 Android share sheet 分享本地生成的 Notion-ready Markdown 预览文本；该路径不包含 Notion OAuth、API client、token、backend 或上传行为。
- `0.4.x` Notion-ready save entry foundation：Seminar Detail 可通过 Android document picker 保存本地生成的 Notion-ready Markdown 预览文本，便于后续手动导入 Notion 或其他知识库；该路径仍不上传、不持有 token。
- `0.5.x` Formula / Research Export foundation：已形成 `docs/plans/0.5.x-formula-research-export-plan.md`；Room version `7` 已加入本地公式区域、公式结果、`FormulaDao`、domain model 和 `FORMULA_OCR` job 类型；`FormulaOcrProvider`、manual/unavailable providers、JVM contract tests、Reconstruction workspace 本地公式区域 UI、manual LaTeX durable queue，以及 confirmed references 的 deterministic `references.bib` / `references.ris` ZIP export artifact、单独保存和分享入口已就位。Mathpix live provider、PaddleOCR/pix2tex bundling 和任何 cloud upload 均需后续 credential/backend/license 决策。

尚未声明完成：

- 非破坏性真机完整 E2E 验收：创建 seminar、录音、拍照、timeline、clip、重启后持久化、离线导出和删除清理仍需在用户授权的设备会话中执行。
- 真实 ASR provider 接入、AI 总结 provider 运行时、Notion live OAuth/upload、cloud sync、live 公式 OCR provider、广告或支付。

## 文档入口

- 产品级路线图：`TODO.md`
- `0.1.x` 总体实现合同：`docs/plans/0.1.x-mvp-implementation-plan.md`
- 后续分阶段计划索引：`docs/plans/0.1.x-development-plan-index.md`
- 当前已完成基础批次记录：`docs/plans/0.1.x-mvp-execution-batch-01.md`
- 架构说明：`docs/ARCHITECTURE.md`
- 隐私说明：`docs/PRIVACY.md`
- 设计交付：`design/`
- 变更记录：`CHANGELOG.md`
- Internal APK 安装与更新说明：`docs/INTERNAL_DISTRIBUTION.md`

## 推荐开发顺序

后续不要直接执行整个 `TODO.md`。默认按以下顺序把计划拆成小 task：

1. `0.1.1` closeout：构建、CI、Room、UI、PDF 生命周期和文档收口。
2. `0.1.2` recording validation：真机/设备录音、notification、完成后 `.m4a` 播放和文档收口验收。
3. `0.1.3` capture/timeline：CameraX、photo-only、offset、现场交互和统一 timeline。
4. `0.1.4` clip：WorkManager、clip 状态、retry 和 full-recording fallback。
5. `0.1.5` local MVP：Markdown/ZIP 导出、数据清理、验收、README/隐私/CI 收口。
6. `0.2.x` implementation：asset/job/OCR/tag data foundation、WorkManager processing queue、local image enhancement、local OCR、搜索、标签和 Research Reconstruction workspace UI。
7. `0.3.x` implementation：reference schema/domain、Crossref/OpenAlex/DataCite provider、evidence extraction、candidate ranking/dedup、Reference Review、Seminar Brief 和 brief export。

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

Internal dogfood APK 使用：

```powershell
.\gradlew.bat assembleInternal
```

稳定 internal signing key 不在仓库内。Gradle 通过 `SEMINARARC_INTERNAL_SIGNING_PROPERTIES` 指向 repo 外 properties 文件；若未设置，则默认检查 `D:\Code\_secrets\SeminarArc\internal-signing.properties`。

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
- formula OCR、转写、AI 总结、Notion 或 cloud upload。
- 广告、订阅、支付。
- iOS 或 web 客户端。

普通本地 OCR、图像增强和 Reconstruction workspace 已在 `0.2.x` 实现；opt-in reference lookup、候选复核和 Seminar Brief 已在 `0.3.x` 实现。其余上述能力可以保留在 roadmap 中，但不能在 UI 中伪装成已完成。
