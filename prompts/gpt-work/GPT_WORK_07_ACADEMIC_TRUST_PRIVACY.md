# P07 学术工作流、可信度与隐私

以研究人员视角审查界面如何表达来源、候选状态、人工编辑、自动处理和数据流向。只看真实 App，不读隐私文档、源码、producer 结果或 provider 实现。使用 `S04_RESEARCH_READY` 与 `S06_FORMULA_EXPORT_READY`。

判断 seminar 数据是否始终归属清楚；录音、照片、OCR、公式和 reference provenance 是否可理解；人工输入、自动生成、candidate、confirmed、draft、unavailable provider 是否清楚区分；本地与可能离开设备的数据边界是否可见；confidence/heuristic 是否被冒充概率真值；privacy wording 是否过度承诺；export 是否说明内容范围。

重点检查：reference candidate 被误认为已确认论文、manual LaTeX 被误认成自动 OCR、manual transcript 被误认成 ASR、summary draft 被误认成正式 Seminar Brief，以及界面暗示“AI 已确认事实”。寻找会损害研究判断和信任的问题，不只找 crash。保留 hardware-only 与未接 provider 的真实边界。
