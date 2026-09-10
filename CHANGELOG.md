# 更新日志

本项目的更新日志以后统一使用中文维护。

格式遵循“版本/日期 + 分类条目”的轻量约定；未正式发布的开发整理记录放在 `未发布` 下，发布或阶段收口时再移动到对应版本。

## 未发布

### 新增

- 完成 `0.5.0-alpha.2` 版本元数据：默认 `versionName = 0.5.0-alpha.2`、`versionCode = 50002`，用于替代远端 CI 未通过的 `v0.5.0-alpha.1` 候选。
- 完成 `0.5.0-alpha.1` 版本元数据：默认 `versionName = 0.5.0-alpha.1`、`versionCode = 50001`，并生成 `SeminarArc-0.5.0-alpha.1.apk` 与 SHA-256。
- 新增 mixed-inventory explicit-emulator 测试策略：当 Windows ADB 同时看到 protected physical serial 时，禁止 unscoped connected Gradle task，但允许 task 授权的 `adb -s <emulator>` app/test install 与 `am instrument`。
- 新增 `0.3.1-internal` dogfood 分发基础：internal build type、`com.yuukias.seminararc.internal` application identity、`0.3.1-internal.1` 版本元数据、repo 外 internal signing 配置读取、安装/更新文档和 secret ignore 规则。
- 新增 `0.4.x` transcript/schema foundation：Room v5、`transcripts`、`transcript_segments`、`summary_drafts`、`MIGRATION_4_5`、schema `5.json` 和未来 transcription/summary/Notion export processing job 类型边界。
- 新增 `0.4.x` provider contract foundation：`TranscriptionProvider`、`SummaryProvider`、timestamp granularity、selected transcript window、summary draft provenance、retryability 和 fake-provider JVM contract tests。
- 新增 `0.4.x` transcript repository/processing foundation：`TranscriptDao`、`TranscriptRepositoryImpl`、`RunTranscriptionForRecordingUseCase`、本地录音文件解析、`TRANSCRIPTION` job 状态写回和 timestamped segment 持久化测试。
- 新增 `0.4.x` summary draft use case foundation：基于用户选择的 transcript segments、已确认 references、key-slide captions 和 notes 生成 provider-independent summary request，并将成功/失败结果写入 `summary_drafts`，不覆盖人工 `SeminarBrief`。
- 新增 `0.4.x` transcript timeline windows foundation：基于 timeline/photo offset 生成可配置 transcript window，按 overlap 匹配 segments，关联 photo asset，并输出未来 UI/export 可复用 preview。
- 新增 `0.4.x` transcript review UI foundation：从 Reconstruction workspace 进入，展示 transcript 列表、timestamped segments、timeline/photo windows 和 summary draft 状态；真实 ASR/summary provider 入口保持未接线提示。
- 新增 `0.4.x` Markdown/ZIP transcript summary export foundation：导出 Markdown 现在包含 transcript review metadata、timestamped segments、timeline windows 和 editable generated summary drafts，ZIP 继续封装增强后的 `seminar.md` 和可读媒体。
- 新增 `0.4.x` transcription durable queue foundation：完成录音可创建/恢复/重试 durable `TRANSCRIPTION` WorkManager job；默认 `UnavailableTranscriptionProvider` 明确失败，避免上传音频或伪造转写。
- 新增 `0.4.x` transcript review queue entry foundation：Transcript Review 的转写动作会为最新完成录音排入 durable job，并通过 snackbar 标明仍需配置真实 provider。
- 新增 `0.4.x` summary durable queue foundation：Room v6 为 `processing_jobs` 增加 `inputPayloadJson`，Transcript Review 可把当前 transcript segments 排入 durable `SUMMARY_DRAFT` WorkManager job；默认 `UnavailableSummaryProvider` 明确失败，避免上传 transcript、references、notes 或伪造总结。
- 新增 `0.4.x` Notion-ready export contract foundation：`SeminarNotionReadyRenderer` 可从本地 export document 生成 block-like Notion-ready document 和 Markdown 预览；不接入 Notion OAuth、API client、token 或上传。
- 新增 `0.4.x` transcript processing queue UI foundation：Transcript Review 展示本地 `TRANSCRIPTION` / `SUMMARY_DRAFT` job 状态，并提供 retry/cancel 控制。
- 新增 `0.4.x` transcript segment edit foundation：Transcript Review 可本地编辑单条 timestamped segment 文本并持久化 `isEdited` / `updatedAt`，不触发 provider 或上传。
- 新增 `0.4.x` manual transcript import foundation：Transcript Review 可将粘贴的本地文本导入为 `MANUAL` transcript，并按非空行生成可编辑 coarse segments。
- 新增 `0.4.x` summary draft edit foundation：Transcript Review 可本地编辑并保存 generated summary draft 的各个 brief 字段，保存后标记为 `DRAFT` 并保留 provider provenance。
- 新增 `0.4.x` summary draft apply foundation：Transcript Review 可由用户显式将某个 summary draft 应用到人工 `SeminarBrief`，继续进入既有 confirmed references/key slides Markdown/ZIP export。
- 新增 `0.4.x` Notion-ready share entry foundation：Seminar Detail 可通过 Android share sheet 分享本地生成的 Notion-ready Markdown 预览文本，不接入 Notion OAuth/API/token 或上传。
- 新增 `0.4.x` Notion-ready save entry foundation：Seminar Detail 可通过 Android document picker 保存本地生成的 Notion-ready Markdown 预览文本，便于手动导入 Notion 或其他知识库。
- 完成 `0.5.x` Formula / Research Export readiness gate：明确 Mathpix 为后续 credential/backend 决策后的付费云候选，PaddleOCR/pix2tex 为需单独审计的自建候选，第一阶段优先实现本地公式区域、可编辑 LaTeX、provider contract 和 BibTeX/RIS deterministic export。
- 新增 `0.5.x` formula schema/domain foundation：Room version `7` 增加 `formula_regions`、`formula_results`、`FormulaDao`、公式 domain models 和安全占位 `FORMULA_OCR` processing job 类型。
- 新增 `0.5.x` formula provider contract foundation：`FormulaOcrProvider`、normalized crop/request/result models、`ManualFormulaProvider`、production 默认 `UnavailableFormulaOcrProvider` 和 JVM contract tests。
- 新增 `0.5.x` formula region UI foundation：Reconstruction workspace 可显示、创建和删除 seminar-owned photo 的本地公式区域记录，暂不启用 live OCR provider。
- 新增 `0.5.x` formula processing queue foundation：saved formula region 可通过 durable `FORMULA_OCR` WorkManager job 写回 manual LaTeX result，支持 payload-based retry/recovery，不启用云 provider。
- 新增 `0.5.x` research BibTeX/RIS export foundation：confirmed references 可生成 deterministic `references.bib` / `references.ris`，并随本地 ZIP export artifact 输出；pending/rejected candidates 不会导出为确定文献。
- 新增 `0.5.x` export polish：Seminar Detail 可单独保存/分享 BibTeX 和 RIS，本地无 confirmed references 时显示明确失败消息。
- 新增 `0.5.x` formula result export foundation：READY formula LaTeX results 进入 Markdown/Notion-ready export，保留 normalized crop、source photo、provider、confidence、edited 和 provenance。
- 新增 `0.5.x` formula UI overlay foundation：Reconstruction photo preview 会绘制已保存公式区域的本地半透明 overlay，并暴露无障碍描述；暂不加入手势写入。
- 新增 `0.5.x` formula region drag selection foundation：Reconstruction photo preview 可通过拖拽生成 normalized crop 草稿，并将草稿同步到显式保存表单；拖拽不直接写 Room、不触发 formula OCR provider 或上传。
- 新增 `0.5.x` formula region edit selection foundation：saved formula region 可通过 `Edit crop` 载入草稿并显式更新 label/crop，更新保留原 seminar/source asset 归属且不自动触发 provider。
- 新增 `0.5.x` formula provider settings boundary：`FormulaOcrProvider` 暴露 availability/capability/status，Reconstruction workspace 显示 live formula OCR 未配置和 Manual LaTeX 本地可用状态。
- 完成 `0.5.x` formula closeout：第一阶段本地安全 foundation、Windows explicit-emulator instrumentation、internal APK update-in-place smoke、签名检查、secret scan 和 release artifact 均已通过。
- 完成 `0.2.x` Local Visual Reconstruction readiness gate：明确 Room v2->v3 schema 设计、bundled ML Kit Text Recognition v2 OCR 策略、Android 原生图像增强方案、provider/privacy/license 边界和 Research Reconstruction 工作区规格。
- 新增 `0.2.x` 数据基础：Room schema version 3、`SeminarAsset`、`ProcessingJob`、`OcrResult`、系统标签、asset-tag mapping、`MIGRATION_2_3` backfill 和 repository/JVM/migration test 覆盖。
- 新增 `0.2.x` 本地图像增强基础：`ImageEnhancementProvider`、Android Bitmap/Matrix/Canvas/ColorMatrix provider、原图保留的 enhanced derived asset 输出、processing job 状态写回和 use case JVM 测试。
- 新增 `0.2.x` 本地 OCR 基础：bundled ML Kit Latin/Chinese Text Recognition 依赖、`TextOcrProvider`、app-owned OCR block JSON、`RunTextOcrForAssetUseCase` 和 OCR job/result JVM 测试。
- 新增 `0.2.x` Reconstruction workspace ViewModel 基础：组合 photo assets、OCR results、processing jobs、KEY_SLIDE 标签、搜索 query 和 OCR 状态过滤，并暴露 enhance/OCR/edit/tag actions。
- 新增 `0.2.x` Reconstruction workspace Compose UI：Seminar Detail 整理入口、照片预览、OCR 搜索/过滤、key-slide toggle、enhance/OCR 动作和 OCR 编辑保存路径。
- 完成 `0.2.x` processing queue 收口：新增 WorkManager-backed OCR/enhancement worker、durable retry/cancel/idempotency、process-start recovery，以及 Reconstruction workspace 真实 queued/running/succeeded/failed/cancelled 控件。
- 新增 Windows Emulator closeout 覆盖：`MIGRATION_2_3` connected、bundled ML Kit Latin/Chinese/mixed/empty OCR smoke、Android Bitmap enhancement smoke、WorkManager queue smoke、Reconstruction workspace UI regression 和既有 0.1.x connected regression。
- 完成 `0.3.x` Reference Candidate + Seminar Brief production closeout：新增 Room v4 reference/brief schema、Crossref/OpenAlex/DataCite keyless provider、deterministic evidence extraction、`reference-match-v1` ranking/dedup、Reference Candidate Review UI、可编辑 Seminar Brief 和 confirmed references/key slides Markdown/ZIP export。
- 完成 `0.1.2` 第一阶段 one-active-seminar invariant：新增 session start 结果语义、事务边界和 repository unit tests。
- 新增 `0.1.2` foreground recording service 基础：microphone foreground service、`MediaRecorder` 本地 `.m4a` backend、recording notification channel、ongoing notification、seminar-owned recording file 和 `RecordingEntity` durable lifecycle。
- 新增最小 recording start use case，让详情页可以在麦克风权限允许后通过 repository/session 语义启动 foreground service。
- 新增 `0.1.2` Active Session route 与现场录音闭环：详情页 start/resume 导航现场页、live recorder 与 durable recording recovery 区分、权限拒绝状态、notification 返回现场页、以及 End Seminar stop/finalize 后完成 seminar。
- 新增 `0.1.2` 完整录音回放：Seminar Detail 使用 Media3 页面内播放器播放已完成 `.m4a`，支持 Play/Pause/Seek、duration/position、缺失文件和失败录音状态。
- 新增 `0.1.3` Capture + Timeline：CameraX 幻灯片拍照、photos-only seminar session、MARK/PHOTO/QUESTION/NOTE 事件写入、统一 timeline review route、photo missing state、last-photo undo/retake 和 `Play from here` 回放入口。
- 新增 `0.1.4` clip 韧性基础：MARK 自动创建 `PENDING` clip、WorkManager 生成任务、Android `MediaExtractor`/`MediaMuxer` 本地 `.m4a` 裁剪、`READY` clip 播放入口、failed retry、完整录音 fallback 和 clip-owned 文件清理。
- 新增 `0.1.5` 本地导出闭环：UI-independent export document、Markdown renderer、ZIP writer、missing-media skip、`ACTION_CREATE_DOCUMENT` 保存和 Android share sheet。
- 新增 `0.1.x` 分阶段开发计划索引：`docs/plans/0.1.x-development-plan-index.md`。
- 新增 `0.1.1` 收口计划，覆盖当前基础批次的构建、CI、Room、UI、PDF 生命周期和文档校验。
- 新增 `0.1.2` 录音服务计划，覆盖 foreground service、active seminar invariant、恢复流程和完整录音回放。
- 新增 `0.1.3` 采集与时间线计划，覆盖 CameraX、photo-only、offset 记录、现场交互和统一 timeline。
- 新增 `0.1.4` clip 回放韧性计划，覆盖 clip 区间、WorkManager、状态、retry 和完整录音 fallback。
- 新增 `0.1.5` 本地 MVP 导出与验收计划，覆盖 Markdown/ZIP、本地验收、清理测试、文档和 CI 收口。
- 新增 `0.2.x` Research Reconstruction 前置计划，明确 OCR、provider、migration 和研究重建工作区进入后续版本线。

### 文档

- 更新 README、TODO、架构、隐私、设备测试策略和 autonomous roadmap，记录 `0.5.x COMPLETE` 与 `0.9.x` release readiness boundary。
- 新增 `0.3.x` Reference Candidate + Seminar Brief readiness plan，明确 Crossref/OpenAlex/DataCite/Semantic Scholar 取舍、evidence extraction、candidate ranking/dedup、Room v4 设计、联网隐私边界和 Candidate Review / Brief UX。
- 更新 README、TODO、架构、隐私、设计说明和 `0.3.x` 计划，记录 reference lookup 已实现为 opt-in metadata lookup，并继续明确不上传照片、录音、完整 OCR corpus、timeline 或导出包。
- 更新 README、架构和隐私说明，准确记录 `0.1.5-dev` 已具备本地 capture/timeline/clip/export headless 实现，并明确真机完整 E2E 验收仍需后续授权设备会话。
- 新增 `docs/notes/2026-08-08-remote-wsl-codex-handoff.md`，记录远端 WSL Codex 开发环境、Android Studio 安装判断、已完成线程上下文和后续开发入口。
- 明确 changelog 后续使用中文维护。
- 完善 README，说明当前仓库状态、文档入口、开发顺序和本地构建命令。
- 完善 AGENTS 规则，补充 changelog、计划文件、任务拆分和执行边界要求。
- 完善 `.gitignore`，补充 Android/Gradle、本地凭据、发布包和临时产物忽略规则。

### 修复

- 修复 GitHub Actions Linux runner 解析默认 Windows internal signing properties 路径时抛出的 `URISyntaxException`；非 Windows 环境默认不再解析 `D:\Code\_secrets\...`，Windows 本地 internal signer 行为保持不变。
- 修复 `0.2.x` closeout 期间暴露的 instrumentation 问题：旧 migration test 未在当前 v3 database builder 上注册 `MIGRATION_2_3`、mixed OCR fixture 断言过窄、workspace queue UI 只显示同类最新 job 导致 cancelled state 不可见。
- 修复 `0.3.x` closeout 期间暴露的问题：Crossref DOI endpoint 携带 `select` 导致 400、Compose instrumentation 缺少稳定 debug host Activity、emulator screen-off 导致 UI hierarchy 不可见、低置信 DOI OCR repair 误用风险，以及 rejected/reopened candidate 与 brief reference 关系未及时收敛。
- 修复 `gradlew` 在 Linux/WSL 远程开发环境中的可执行位，避免 `./gradlew: Permission denied`。
- 修复 process-start recovery 竞态：启动恢复先捕获 stale recording IDs，再只标记这些 rows 为 `FAILED`，避免误杀当前进程中新建的 recording。
- 修复 durable `RECORDING` row 被误当成 live recorder 的语义；当前进程没有 runtime recorder 时改为 recovery state。
- 修复 0.1.4 schema 变更仍停留在 Room version 1 的问题：新增 `MIGRATION_1_2`，为旧 `audio_clips` 表补 `retryCount` 默认值，并恢复 schema history，避免保留旧数据库的真机启动崩溃。

## 0.1.1-dev

### 已有基础

- 建立 Android/Compose 单模块应用基线。
- 建立 Material 3 主题和 SeminarArc 设计令牌映射。
- 建立 Room-backed seminar 容器模型。
- 建立 seminar 列表、编辑、详情和 abstract PDF 生命周期基础流程。

### 已知限制

- 当前阶段尚未实现真实录音、CameraX 拍照、录音中 timeline 事件写入、clip 生成或最终 MVP 验收。
