# SeminarArc Design Delivery

本目录包含 `SeminarArc` 选定视觉方向 `A / Academic Archive` 的完整设计交付，用于后续原生 Android 实现，不包含业务代码变更。

## 目录结构

```text
design/
├── README.md
├── UI_SPEC.md
├── USER_FLOW.md
├── DESIGN_TOKENS.md
├── screens/
│   ├── seminar-list.png
│   ├── seminar-editor.png
│   ├── seminar-detail.png
│   ├── live-capture.png
│   └── seminar-timeline.png
└── states/
    ├── dark-mode.png
    ├── empty-and-no-abstract.png
    ├── large-text.png
    ├── permissions-denied.png
    ├── processing-error-delete.png
    └── recording-and-paused.png
```

## 设计目标

- 保持 `SeminarArc` 的核心定位：本地优先、学术场景、强归属、低负担回顾。
- 用安静、专业、克制的 Material 3 Android 语言承载复杂信息，而不是做成网页仪表盘。
- 让用户始终明确“当前素材属于哪一场 seminar”。
- 让现场记录页在单手竖屏下优先成立，再服务长期归档与回顾。

## 核心页面

- `screens/seminar-list.png`
  目的：展示 seminar 目录、状态筛选、检索入口和创建入口。
- `screens/seminar-editor.png`
  目的：创建/编辑 seminar，并管理 abstract PDF。
- `screens/seminar-detail.png`
  目的：展示 seminar 元信息、附件、录音汇总与时间线预览。
- `screens/live-capture.png`
  目的：现场记录，执行标记、拍照、提问、速记和结束 seminar。
- `screens/seminar-timeline.png`
  目的：按时间统一回看照片、音频标记、问题和笔记。
- `Research Reconstruction`（0.2.x 文字规格，尚未生成高保真图）
  目的：completed seminar 的会后视觉重建工作区，用于选择关键幻灯片、查看原图/增强图、运行和编辑本地 OCR、添加标签、搜索 OCR 文本、重试或取消处理任务。该工作区不替代统一时间线，也不展示已完成的 transcript/reference/AI 能力。
- `Reference Candidate Review / Seminar Brief`（0.3.x 文字规格，尚未生成高保真图）
  目的：在用户显式选择 evidence 后执行开放 metadata lookup，展示透明 match reason、确认/拒绝候选，并把 confirmed references、key slides、问题和用户笔记组织进可编辑 Seminar Brief。该流程不自动上传全量 OCR、照片或录音，也不自动下载 PDF。

## 状态页面

- `states/empty-and-no-abstract.png`
  覆盖：首页空状态、没有 abstract 的 seminar。
- `states/recording-and-paused.png`
  覆盖：正在录音、录音暂停。
- `states/permissions-denied.png`
  覆盖：麦克风权限拒绝、相机权限拒绝。
- `states/processing-error-delete.png`
  覆盖：音频片段处理中、文件缺失或读取失败、删除 seminar 确认。
- `states/dark-mode.png`
  覆盖：深色模式下的首页与时间线。
- `states/large-text.png`
  覆盖：大字体下的首页与现场记录页。

## 使用方式

- 先读 [`UI_SPEC.md`](UI_SPEC.md) 确认页面目的、导航关系和不可擅改项。
- 再读 [`DESIGN_TOKENS.md`](DESIGN_TOKENS.md) 映射为 Compose Theme 与扩展令牌。
- 实现阶段必须把状态图和主图一起看，不能只照主界面实现。
- `0.2.x` Research Reconstruction 与 `0.3.x` Reference Candidate / Seminar Brief 先以 `UI_SPEC.md` 和 `USER_FLOW.md` 的结构化规格为准；没有高保真 PNG 不阻塞生产实现，但不得偏离 `Academic Archive` 的列表优先、安静、专业方向。

## 交付边界

- 本交付面向 `Jetpack Compose + Material 3` 原生实现。
- 设计图不是背景图，不允许在实现阶段以图片贴图替代真实 UI。
- 未在本目录明确授权的关键视觉决策，不应在实现阶段自由更改。
