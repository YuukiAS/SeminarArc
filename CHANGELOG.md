# 更新日志

本项目的更新日志以后统一使用中文维护。

格式遵循“版本/日期 + 分类条目”的轻量约定；未正式发布的开发整理记录放在 `未发布` 下，发布或阶段收口时再移动到对应版本。

## 未发布

### 新增

- 完成 `0.2.x` Local Visual Reconstruction readiness gate：明确 Room v2->v3 schema 设计、bundled ML Kit Text Recognition v2 OCR 策略、Android 原生图像增强方案、provider/privacy/license 边界和 Research Reconstruction 工作区规格。
- 新增 `0.2.x` 数据基础：Room schema version 3、`SeminarAsset`、`ProcessingJob`、`OcrResult`、系统标签、asset-tag mapping、`MIGRATION_2_3` backfill 和 repository/JVM/migration test 覆盖。
- 新增 `0.2.x` 本地图像增强基础：`ImageEnhancementProvider`、Android Bitmap/Matrix/Canvas/ColorMatrix provider、原图保留的 enhanced derived asset 输出、processing job 状态写回和 use case JVM 测试。
- 新增 `0.2.x` 本地 OCR 基础：bundled ML Kit Latin/Chinese Text Recognition 依赖、`TextOcrProvider`、app-owned OCR block JSON、`RunTextOcrForAssetUseCase` 和 OCR job/result JVM 测试。
- 新增 `0.2.x` Reconstruction workspace ViewModel 基础：组合 photo assets、OCR results、processing jobs、KEY_SLIDE 标签、搜索 query 和 OCR 状态过滤，并暴露 enhance/OCR/edit/tag actions。
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

- 更新 README、架构和隐私说明，准确记录 `0.1.5-dev` 已具备本地 capture/timeline/clip/export headless 实现，并明确真机完整 E2E 验收仍需后续授权设备会话。
- 新增 `docs/notes/2026-08-08-remote-wsl-codex-handoff.md`，记录远端 WSL Codex 开发环境、Android Studio 安装判断、已完成线程上下文和后续开发入口。
- 明确 changelog 后续使用中文维护。
- 完善 README，说明当前仓库状态、文档入口、开发顺序和本地构建命令。
- 完善 AGENTS 规则，补充 changelog、计划文件、任务拆分和执行边界要求。
- 完善 `.gitignore`，补充 Android/Gradle、本地凭据、发布包和临时产物忽略规则。

### 修复

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
