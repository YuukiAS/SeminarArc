# SeminarArc Product and Engineering TODO v2

Status: canonical product roadmap

Last updated: 2026-07-19

## 0. 文档地位与旧计划关系

本文件是 SeminarArc 当前的产品方向、能力边界和版本路线图，是后续规划的产品级入口。它不直接替代具体 Codex task，也不表示所有条目已经获准执行。

仓库中已有材料继续保留，并按以下方式理解：

- `docs/plans/0.1.x-mvp-implementation-plan.md`：保留为本地采集核心的详细实现合同，尤其是录音、CameraX、时间线、音频片段、生命周期恢复和验收要求。
- `docs/plans/0.1.x-mvp-execution-batch-01.md`：记录已经完成的 `0.1.1` 基础批次，不作为未来产品范围的完整定义。
- `design/`：保留为 `Academic Archive` 视觉与交互基线，但本文件新增的现场模式、研究重建和导出需求在冲突时优先。设计稿不是不可修改的产品合同。
- `README.md`：描述当前代码状态，不承担完整路线图职责。
- `prompts/tasks/*_task.md`：仍是 Codex 唯一默认执行入口。实施本 TODO 中任何阶段前，应拆成小而明确的 task。

旧版 TODO 的核心工程约束继续有效：本地优先、每项素材归属于一个 seminar、最多一场 active seminar、录音时间偏移可恢复、不能伪造 clip、删除时数据库与文件一致清理。旧版将 OCR、转写、AI、Notion 等列为 MVP 非目标；这仍适用于 `0.1.x` 采集核心，但这些能力现在被正式纳入后续路线图。

## 1. 产品问题重新定义

SeminarArc 不是普通会议转写工具。它解决的首要场景是：线下学术 seminar 往往没有可下载的讲义或 PPT，活动页面只有标题、主讲人和简短 abstract；用户无法像 Zoom 一样截图，也无法事先在电脑上准备完整材料。报告中的背景、公式、图表、引用和关键结果如果没有现场拍照，结束后很难恢复。

因此产品核心不是单独完成“录音转文字”，而是把一次缺少讲义的线下 seminar 转化为可回顾、可检索、可继续研究的档案：

1. 会前保存题目、主讲人、单位、时间、地点、abstract 和附件。
2. 现场在尽量低干扰的情况下录音、拍摄关键幻灯片、标记重点、记录问题和速记。
3. 每张照片、问题、笔记和标记都与录音时间点对齐。
4. 会后从照片和录音中重建报告脉络，而不是只得到一份脱离幻灯片的文字稿。
5. 从幻灯片文字和用户线索中识别可能的论文、方法、数据集和作者，给出候选文献供人工确认。
6. 将关键图片、摘要、问题、参考文献和后续行动导出为 Markdown，后续可写入 Notion 或其他知识库。

产品主循环定义为：

`Prepare -> Capture -> Reconstruct -> Research -> Export`

## 2. 不可妥协的产品原则

### 2.1 本地优先且离线可用

创建 seminar、现场录音、拍照、打标、记录问题、时间线回放和基础导出必须在没有网络时工作。网络和云端处理只能增强产品，不能成为进入现场记录流程的前置条件。

### 2.2 Seminar 是唯一所有权容器

所有照片、录音、音频片段、OCR 结果、转写片段、文献候选、问题、笔记、标签、摘要和导出记录都必须归属于明确的 `seminarId`。不得建立脱离 seminar 的默认收件箱式素材流。

### 2.3 现场采集优先于 AI

AI 无法恢复从未采集到的幻灯片内容。第一优先级始终是可靠录音、快速拍照和时间对齐；OCR、公式识别、转写和总结都属于会后处理。

### 2.4 云处理必须显式选择

录音、照片或 abstract 上传第三方服务前，必须显示处理范围、服务商、可能费用和隐私提示，并由用户主动触发。默认状态不上传。

### 2.5 不绑定单一供应商

任何云端 OCR、公式识别、转写、总结或导出服务都必须通过 provider interface 接入。数据库不得只保存某一家供应商的私有响应格式，用户的原始素材和人工编辑结果不得因供应商不可用而失效。

### 2.6 研究候选必须可核对

论文、DOI、作者、方法名和公式识别结果只能显示为候选或已确认结果。低置信度输出不得伪装成事实。应用不应绕过出版商权限自动下载受限 PDF。

## 3. 通义听悟的角色与依赖决策

### 3.1 最终决策

通义听悟不是 SeminarArc 的必需依赖，也不是产品核心。它可以作为一个可选的云端 `TranscriptionProvider` / `SummaryProvider`，为愿意上传音频或视频的用户提供转写、说话人区分、章节、摘要和问答提取。

SeminarArc 必须在完全不接入通义听悟的情况下完成核心价值：现场照片、录音时间对齐、问题记录、时间线回放、Markdown 导出和人工整理。

### 3.2 通义听悟适合承担的能力

- 云端实时或离线语音转写。
- 中英等多语种识别和说话人相关处理。
- 章节、全文摘要、问答、关键词和自定义 Prompt。
- 当用户确实拥有完整且清晰的视频时，尝试视频 PPT 抽取及逐页讲解摘要。

### 3.3 通义听悟不应承担的核心职责

- 现场关键幻灯片的快速拍照。
- 照片与录音时间点的绑定。
- 用户未说出口的个人问题和研究想法。
- 照片级重点标记、方法/结果/引用标签。
- 论文候选检索、DOI 确认和 Zotero/Notion 归档。
- 离线浏览和本地数据所有权。

其视频 PPT 抽取要求用户录制完整视频并让 PPT 成为主要画面，和 SeminarArc 的“后台录音 + 只拍关键页”主流程并不一致。因此该能力只能作为补充导入方式，不能决定产品架构。

### 3.4 可替代能力矩阵

#### 普通文字 OCR

默认优先 Android 端 ML Kit Text Recognition，用于中英文标题、作者、引用、方法名、数据集名和普通段落。它可以在设备端运行，适合第一层低成本 OCR。

需要更强版面解析、表格、复杂文档或自建服务器时，可以评估 PaddleOCR / PP-Structure。引入任何模型前必须单独核对代码、模型权重和传递依赖的商业许可证。

#### 幻灯片矫正与增强

透视矫正、裁边、旋转、对比度和去眩光应通过本地图像处理管线实现，可评估 OpenCV。原始照片必须保留，增强图作为派生资产，不覆盖原图。

#### 公式识别

Mathpix 可以作为高准确度付费 `FormulaOcrProvider`，但不是硬依赖。其密钥不能写入 APK，应通过用户自带密钥或后端代理调用。

开源替代需要谨慎区分“代码许可证”和“模型权重许可证”。例如 pix2tex 代码为 MIT，但公开权重带非商业限制，不适合直接用于带广告或付费的 Google Play 版本。PaddleOCR 的公式识别管线可作为后续自建候选，但上线前必须完成模型、依赖和移动端/服务器部署审计。

公式 OCR 在第一版中不是自动处理所有照片的默认步骤。用户应先框选公式或把照片标记为“公式”，再按需处理，避免成本和错误扩散。

#### 语音转写

可选路径包括：

- 本地或自建服务：Whisper、whisper.cpp 或兼容实现。
- 第三方付费服务：通义听悟或其他云端转写 API。
- 用户不启用转写：仍可通过照片时间点和完整录音回放使用应用。

Whisper 的代码和模型权重采用 MIT 许可证，可作为不依赖通义听悟的基础方案。Android 端完整离线转写仍需评估模型体积、内存、耗电、发热和长音频处理时间；默认可设计为 seminar 结束后、充电时或用户手动触发的后台任务。

#### 摘要与研究重建

摘要不应绑定听悟专有格式。统一输入应由 SeminarArc 自己构造：seminar 元数据、用户选中的关键照片 OCR、转写片段、个人问题和已确认文献。之后可交给任意 `SummaryProvider` 或完全由用户手工编辑。

#### 论文和 DOI 检索

普通文献候选检索优先使用开放学术元数据接口，例如 Crossref；后续可以评估 OpenAlex、Semantic Scholar 等。识别流程应先抽取标题、作者、年份、期刊或 DOI，再返回候选和匹配理由，由用户确认。获取元数据不等于拥有 PDF 下载权。

#### Markdown 与 Notion

Markdown、JSON 和媒体 ZIP 导出应在本地完成，不依赖任何云服务。

Notion 集成可使用官方 API 创建页面、追加 block，并在需要时上传照片或 PDF。公开应用的 Notion OAuth 和应用自有 token 不能只靠客户端安全实现，正式多用户接入应通过后端完成；在此之前先提供 Markdown 导出和 Android share sheet，必要时再支持高级用户自带 integration token。

## 4. Provider 架构要求

后续云能力不得直接散落在 ViewModel 或 Worker 中。建议建立以下接口，名称可按代码风格调整：

- `TextOcrProvider`
- `FormulaOcrProvider`
- `TranscriptionProvider`
- `SummaryProvider`
- `ReferenceLookupProvider`
- `ExportProvider`
- `CloudUploadPolicy`

每个 provider 结果至少记录：

- provider ID 和版本。
- 本地任务 ID。
- 输入资产 ID，而不是只记录文件路径。
- 状态：`QUEUED | RUNNING | SUCCEEDED | FAILED | CANCELLED`。
- 创建、开始和完成时间。
- 可读错误信息和可重试性。
- 原始结果的可选本地缓存位置。
- 用户是否已经人工确认或编辑。

应用自有的商业 API 密钥不得嵌入 APK。正式方案只能是：

1. 不需要密钥的本地能力。
2. 用户自带密钥，并使用 Android Keystore 加密保存，同时明确风险和退出方式。
3. SeminarArc 后端代理，负责密钥、配额、速率限制、账单和滥用防护。

## 5. 数据模型演进

### 5.1 `0.1.x` 保持当前捕获模型

当前 `SeminarEntity`、`RecordingEntity`、`TimelineEventEntity` 和 `AudioClipEntity` 足以完成录音、照片、问题、笔记、标记和时间线 MVP。不要为了未来 AI 提前阻塞 `0.1.2` 和 `0.1.3`。

### 5.2 在 `0.2.x` 前规划 Room migration

后续建议增加以下概念，具体拆表以实现审查为准：

- `SeminarAssetEntity`：统一标识原始和派生资产，类型包括 abstract、recording、photo、enhanced photo、clip 和 export。
- `TranscriptSegmentEntity`：记录 `recordingId`、起止时间、speaker label、文本、语言和 provider。
- `OcrResultEntity`：记录照片资产、普通文本、版面块、置信度、provider 和人工修订状态。
- `ReferenceCandidateEntity`：记录标题、作者、年份、期刊、DOI、来源、匹配分数和确认状态。
- `TagEntity` 与关联表：支持 `KEY_SLIDE`、`BACKGROUND`、`METHOD`、`RESULT`、`REFERENCE`、`FORMULA`、`FOLLOW_UP` 等系统标签和用户自定义标签。
- `ProcessingJobEntity`：统一追踪 OCR、转写、摘要、文献匹配和导出任务。
- `ExportRecordEntity`：记录导出格式、时间、目标和是否包含媒体。

所有 schema 变化必须提供 Room migration 和回滚/失败保护，不得依赖 destructive migration 处理真实用户数据。

## 6. 设计评估与必须修改的方向

### 6.1 可以保留的设计基础

`Academic Archive` 的整体方向适合本产品：安静、专业、列表优先、本地归档、单场 seminar 身份持续可见。现有 `Seminar List -> Editor -> Detail -> Live Capture -> Timeline` 主导航也合理。

必须继续保留：

- 首页以 seminar 列表为主，不做统计 dashboard。
- 现场页始终显示当前 seminar。
- 统一时间线，不把照片、问题和录音拆成互不关联的页面。
- 深色模式、大字体、权限拒绝、处理中和失败状态。

### 6.2 现场页需要调整

当前设计把 `Mark Moment` 和 `Capture Slide` 都作为主操作，这是正确的，但产品重心应进一步偏向幻灯片采集。

需要新增或修改：

- 支持 `Record + Photos`、`Photos only` 两种明确模式。录音不被允许时，应用仍然必须可用。
- `Capture Slide` 必须单手可达，并允许连续快速拍摄。
- 不应每次拍照都强制进入长时间 `Keep / Retake` 阻塞流程。默认建议立即保存，短暂显示缩略图并提供 `Undo / Retake`；用户可在设置中选择严格确认模式。
- 拍照后可选快速标签：重点、背景、方法、结果、引用、公式、待研究。标签操作不能成为保存照片的前置步骤。
- 显示最近一张照片缩略图和成功保存反馈，避免用户反复拍同一页。
- 现场页不展示 OCR、转写或广告，不运行会抢占相机/录音资源的重处理。
- 录音开始前显示简洁的录制许可提醒；该提醒不替代用户遵守现场规定。

### 6.3 详情页需要从“录音汇总”升级为工作入口

详情页后续应根据状态显示不同主操作：

- `DRAFT`：准备 seminar / 开始现场记录。
- `ACTIVE`：返回当前现场记录。
- `COMPLETED`：继续整理 / 查看研究重建结果。

详情内容应逐步增加：

- Materials：abstract、照片、录音和附件。
- Reconstruction status：OCR、转写、摘要和待处理任务。
- Questions：现场问题和会后问题。
- References：候选、已确认和待查文献。
- Exports：最近的 Markdown、ZIP 或 Notion 导出。

实现时避免把每个区块都做成重卡片。现有代码中的详情页 Card 密度应在功能进入后重新审查。

### 6.4 时间线需要成为照片与讲解的联合回顾界面

每个照片事件至少应支持：

- 大缩略图或可读预览。
- 时间偏移。
- `Play from here`。
- 前后音频上下文。
- 标签、OCR 状态和个人备注。
- 标记为关键幻灯片。

有转写后，照片附近只显示与该时间窗相关的转写摘要，不要把整份 transcript 塞进时间线。

### 6.5 新增会后 Research Reconstruction 工作区

在不破坏单一时间线的前提下，新增一个会后整理流程：

1. 选择或确认关键幻灯片。
2. 批量或逐张运行普通 OCR。
3. 对选中的公式区域运行公式识别。
4. 识别论文/DOI/作者候选并人工确认。
5. 填写核心收获、仍未解决的问题和下一步动作。
6. 生成可编辑的 Seminar Brief。
7. 导出 Markdown/ZIP，后续可发送到 Notion。

这不是新的独立知识库，而是单场 seminar 的整理视图。

### 6.6 设计文件维护问题

`design/README.md` 当前包含 `C:/Code/SeminarArc/...` 绝对路径，应改为仓库相对链接。后续新增设计稿时必须同步更新 `UI_SPEC.md`、`USER_FLOW.md` 和状态图，不能只生成主屏图片。

## 7. 版本路线图

版本号代表能力门槛，不代表固定发布日期。每一阶段必须从干净 checkout 构建并通过相应测试，才可进入下一阶段。

### `0.1.1` 当前基础：校验和收口

当前仓库已经包含 Android/Compose 基线、Room 模型、seminar CRUD、详情/编辑/列表和 abstract PDF 生命周期。

待完成：

- 在可用 Android 环境运行 `assembleDebug`、unit tests 和 instrumentation smoke tests。
- 确认 CI workflow 不是空壳并能产出可诊断日志。
- 修复明显 placeholder、导航和文件生命周期问题。
- 不在此阶段加入 AI 或云服务。

### `0.1.2` 可靠录音骨架

目标：完成真正可用的 foreground recording session。

必须完成：

- 前台录音服务和持久通知。
- 锁屏、后台、旋转、Activity 重建和异常停止恢复。
- 最多一场 active seminar。
- 完整录音保存和 Media3 回放。
- 低存储、权限拒绝和录音失败状态。
- physical device 长时录音验证。

### `0.1.3` 快速拍照与统一时间线

目标：完成产品最核心的线下采集闭环。

必须完成：

- CameraX 快速拍照。
- 录音模式和 photo-only 模式。
- 照片、mark、question 和 note 的 offset 记录。
- 最近照片反馈、撤销/重拍和可选快速标签。
- 统一时间线及任意事件 `Play from here`。
- 原始照片方向和文件所有权正确。

### `0.1.4` 音频片段与回放韧性

目标：完成旧版计划中的 marked clip 合同。

必须完成：

- 片段区间计算和 WorkManager 处理。
- pending、processing、ready、failed 状态。
- 失败时回退到完整录音正确时间范围。
- 不破坏原始录音。

### `0.1.5` 本地 MVP 收口与 Markdown 导出

目标：形成不依赖 AI、可以实际长期使用的本地版本。

必须完成：

- 完整端到端验收和数据清理测试。
- 从 seminar 生成 Markdown。
- 可选生成带 `media/` 相对路径的 ZIP：Markdown、关键照片、abstract 和用户选择的音频片段。
- Android share sheet 和 `ACTION_CREATE_DOCUMENT` 导出。
- 隐私、权限、备份和恢复文档。
- README 截图与真实功能一致。

### `0.2.x` 本地视觉重建

目标：让照片从“可回看”变成“可搜索、可整理”。

当前 readiness gate 已通过，见 `docs/plans/0.2.x-reconstruction-readiness-plan.md` 和 `prompts/tasks/0.2.x_*_result.md`。

范围：

- 图像旋转、裁边、透视矫正和增强，始终保留原图。
- ML Kit 普通 OCR。
- OCR 文本搜索。
- 照片标签和关键幻灯片筛选。
- ProcessingJob 状态与重试。
- Room migration，为 transcript、OCR 和 references 建立稳定数据层。

### `0.3.x` 论文候选与 Seminar Brief

目标：把现场材料转化为研究线索。

范围：

- 从 OCR 和用户输入抽取标题、作者、年份、期刊、DOI、方法和数据集线索。
- Crossref 等开放元数据检索。
- 候选匹配、人工确认和拒绝。
- 会后 Research Reconstruction 工作区。
- 可编辑 Seminar Brief：背景、方法、结果、关键幻灯片、问题、参考文献、后续动作。
- 不自动下载无合法访问权的 PDF。

### `0.4.x` 可插拔转写、总结和 Notion

目标：增加云端或本地高级处理，但保持供应商独立。

范围：

- `TranscriptionProvider`：至少实现一种开源/自建路径；通义听悟可作为可选 provider。
- 时间戳 transcript segment 与照片窗口关联。
- `SummaryProvider` 基于用户选定材料生成草稿，不覆盖人工内容。
- Notion：先完成 Markdown 友好模板；再实现官方 API 页面、block 和文件上传。
- 公开 Notion OAuth 和应用自有付费 API 需要后端后再开放给普通用户。
- provider 费用、配额、取消和删除说明。

### `0.5.x` 公式与高级科研导出

范围：

- 框选公式区域。
- Mathpix 付费 provider。
- 经过商业许可审查的开源/自建公式识别 provider。
- LaTeX 人工校正和置信度提示。
- 可选 Zotero/引用格式导出研究；优先支持 BibTeX/RIS 文件，不先绑定复杂账号同步。

### `0.9.x` Google Play 内测与发布准备

必须完成：

- Android App Bundle、签名、版本升级和 release build。
- target SDK、前台服务、通知、媒体权限和后台行为复核。
- 隐私政策公开 URL、Google Play Data safety 表、录音/照片/云处理披露。
- 崩溃和性能监测方案，默认不得上传录音或照片。
- internal testing -> closed testing -> open testing 的逐级验收。
- 无网络、弱网、低存储、长 seminar、多语言和不同相机设备测试。

仓库当前 `targetSdk = 36`，符合 2026-08-31 起 Google Play 对新应用和更新的 Android 16 / API 36 要求，但发布前仍需重新核对最新政策。

### `1.0` 公开发布与克制变现

免费核心应包括：

- 本地 seminar 管理。
- 录音、拍照、时间线、问题和笔记。
- 基础回放和 Markdown 导出。

变现原则：

- active recording、相机、时间线精读和音频播放期间禁止广告。
- 不允许广告遮挡 `Capture Slide`、`Mark Moment` 或 `End Seminar`。
- 可评估冷启动后每次进程生命周期至多一次 app-open ad，但必须在恢复 active seminar 时跳过。
- 高级云转写、公式识别、批量处理或跨设备服务可按额度或订阅计费。
- 提供去广告选项。
- 引入 AdMob 时同步完成 consent/CMP、Data safety、隐私政策和第三方 SDK 数据披露。

广告和支付不应进入 `0.1.x` 到 `0.4.x` 的核心开发路径。

## 8. 跨版本验收场景

最终产品至少要通过以下真实场景：

1. 用户只拿到 seminar 标题和简短 abstract，没有 PPT。
2. 用户创建 seminar 并进入现场。
3. 用户选择录音加拍照，锁屏后录音继续。
4. 用户连续拍摄多张幻灯片，不被强制确认页打断。
5. 用户为其中一张标记“引用”，为另一张标记“公式”，并记录一个未说出口的问题。
6. 用户结束 seminar，按时间线打开照片并播放当时讲解。
7. 在完全离线状态下，所有原始资料仍可浏览和导出 Markdown。
8. 联网后，用户主动对选中照片运行 OCR，并得到可编辑文本。
9. 应用根据引用线索返回多个论文候选，用户确认正确 DOI。
10. 用户选择一种转写 provider，能够取消、重试，并看到费用/隐私提示。
11. 用户生成 Seminar Brief，人工修改后导出 Markdown/ZIP。
12. 用户可选将确认后的内容和图片写入 Notion。
13. 用户删除 seminar 后，本地原始资产、派生结果和任务记录一致清理；已经导出的外部副本不被伪装成已删除。

## 9. 测试和质量门槛

除旧版测试要求外，后续必须增加：

- physical device 上一小时以上录音和多次拍照压力测试。
- 录音过程中来电、音频焦点变化、相机启动、锁屏、进程回收和存储不足。
- photo-only 模式无麦克风权限仍可完成全流程。
- OCR 和转写 job 的取消、重试、幂等和进程重启恢复。
- Room migration 保留真实媒体引用。
- Markdown/ZIP golden tests，确认相对链接和 Unicode/LaTeX 不损坏。
- provider contract tests，不依赖真实付费调用完成大部分测试。
- release APK/AAB secret scan，禁止包含生产 API secret。
- 广告和 consent 流程不能阻塞 active seminar 恢复。

## 10. 隐私、安全与合规

- 录音前提醒用户遵守主办方、讲者和所在地法律/政策。
- 应用不能以技术可录制为理由暗示现场允许录制。
- 所有云上传均为 opt-in，并显示具体资产类型。
- provider 返回内容本地缓存时，删除语义必须明确。
- API key 使用 Android Keystore；日志、Crash report 和 analytics 不得记录 token、转写全文或 OCR 原文。
- 第三方 SDK 必须维护清单，记录用途、数据访问、网络域名和关闭方式。
- Google Play 发布时必须提交 Data safety 信息并提供公开隐私政策。

## 11. 下一步拆解顺序

后续不要直接让 Codex“完成整个 TODO”。应依次生成并执行小任务：

1. 审计并构建当前 `0.1.1`，修复 CI 和真实编译问题。
2. 实现 `0.1.2` 录音服务、恢复和完整回放。
3. 实现 `0.1.3` CameraX、photo-only、时间偏移和现场交互。
4. 实现统一时间线与照片音频上下文。
5. 实现 `0.1.4` clip 和 fallback。
6. 实现 `0.1.5` Markdown/ZIP 导出和本地 MVP 验收。
7. 重新设计并审阅 Research Reconstruction 页面，再进入 `0.2.x` OCR。
8. 在供应商和许可证审计后选择首个转写、公式和 Notion provider。

任何任务开始前必须读取本文件、相关 `docs/plans/`、`design/`、`AGENTS.md` 和项目本地 Android/Compose skills，并按 `prompts/CHATGPT_RULES.md` 写入独立 task。

## 12. 当前明确不做

在本地采集闭环完成前，不做：

- 强制登录和云同步。
- 自动上传全部录音或全部照片。
- 把通义听悟写成唯一后端。
- 全自动批量公式识别。
- 未经确认的论文自动写入正式参考文献。
- 自动抓取受限论文 PDF。
- 社交、公开分享社区或多人协作。
- iOS 和 web 客户端。
- 广告、订阅和支付实现。

## 13. 参考接口与实现入口

以下链接用于后续 task 再核对，不构成永久版本锁定：

- 通义听悟 OpenAPI 与功能：https://help.aliyun.com/zh/tingwu/
- Whisper：https://github.com/openai/whisper
- ML Kit Text Recognition：https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- PaddleOCR：https://github.com/PaddlePaddle/PaddleOCR
- Mathpix Convert API：https://website.mathpix.com/docs
- Crossref REST API：https://www.crossref.org/documentation/retrieve-metadata/rest-api/
- Notion API：https://developers.notion.com/
- Google Play target API：https://developer.android.com/google/play/requirements/target-sdk
- Google Play Data safety：https://support.google.com/googleplay/android-developer/answer/10787469
