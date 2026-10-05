# P02 核心流程与产品逻辑

你以独立产品审阅者身份检查 SeminarArc 的核心状态机。不得访问 repo、源码、Room、app-private 文件、producer QA 结果或 golden；所有业务动作必须经过真实可见 UI。

以 `S01_SEMINAR_DRAFT`、`S02_COMPLETED_CAPTURE`、`S03_RECONSTRUCTION_READY`、`S04_RESEARCH_READY` 和 `S06_FORMULA_EXPORT_READY` 为入口，逐段检查 Prepare → Capture/session → timeline → Reconstruction → OCR/key slide → Research/reference candidate → Seminar Brief → Transcript → Formula → Export。每换一个 state 都先核对 candidate，然后只执行该段需要的普通用户动作。

重点寻找：下一步不可发现、动作后状态错误、假成功、非法状态转换、enabled/disabled 错误、页面间状态矛盾、删除/编辑/重试语义错误、跨 seminar 泄漏、导出与确认状态不一致、pending/rejected reference 被当作 confirmed、失败公式被当作 READY、summary draft 被当作正式 Brief、unavailable provider 被表现成成功。

Emulator 不能代表真实 CameraX 和麦克风质量；只记录为 hardware-only boundary，不能写 PASS。每段保存 before/after/re-entry 证据，按统一合同输出；不提供 implementation patch。
