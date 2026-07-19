# 更新日志

本项目的更新日志以后统一使用中文维护。

格式遵循“版本/日期 + 分类条目”的轻量约定；未正式发布的开发整理记录放在 `未发布` 下，发布或阶段收口时再移动到对应版本。

## 未发布

### 新增

- 新增 `0.1.x` 分阶段开发计划索引：`docs/plans/0.1.x-development-plan-index.md`。
- 新增 `0.1.1` 收口计划，覆盖当前基础批次的构建、CI、Room、UI、PDF 生命周期和文档校验。
- 新增 `0.1.2` 录音服务计划，覆盖 foreground service、active seminar invariant、恢复流程和完整录音回放。
- 新增 `0.1.3` 采集与时间线计划，覆盖 CameraX、photo-only、offset 记录、现场交互和统一 timeline。
- 新增 `0.1.4` clip 回放韧性计划，覆盖 clip 区间、WorkManager、状态、retry 和完整录音 fallback。
- 新增 `0.1.5` 本地 MVP 导出与验收计划，覆盖 Markdown/ZIP、本地验收、清理测试、文档和 CI 收口。
- 新增 `0.2.x` Research Reconstruction 前置计划，明确 OCR、provider、migration 和研究重建工作区进入后续版本线。

### 文档

- 明确 changelog 后续使用中文维护。
- 完善 README，说明当前仓库状态、文档入口、开发顺序和本地构建命令。
- 完善 AGENTS 规则，补充 changelog、计划文件、任务拆分和执行边界要求。
- 完善 `.gitignore`，补充 Android/Gradle、本地凭据、发布包和临时产物忽略规则。

## 0.1.1-dev

### 已有基础

- 建立 Android/Compose 单模块应用基线。
- 建立 Material 3 主题和 SeminarArc 设计令牌映射。
- 建立 Room-backed seminar 容器模型。
- 建立 seminar 列表、编辑、详情和 abstract PDF 生命周期基础流程。

### 已知限制

- 当前阶段尚未实现真实录音、CameraX 拍照、录音中 timeline 事件写入、clip 生成或最终 MVP 验收。
