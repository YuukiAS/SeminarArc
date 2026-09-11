# SeminarArc PC Oral Defense Roadmap — STAT5060

Status: proposed roadmap, not yet implemented

Updated: 2026-09-11

## 1. 背景与目标

STAT5060 计划将 Final Project 的一部分改为每位学生约 10 分钟的 oral defense。课程不把“是否使用 AI”作为主要检测目标，而是通过答辩判断学生是否真正理解自己提交的分析、模型、代码、结果与局限。

SeminarArc 的 PC 版可为这一场景提供一个本地优先、可复核的答辩工作台。第一版不追求自动评分，也不要求学生操作电脑；核心目标是：

1. 让考官按统一流程展示每位学生预先准备的问题。
2. 允许不同问题使用不同节奏，不把答辩机械切成固定时长的小段。
3. 连续录音，并为每个主问题、follow-up 与关键事件写入时间标记，方便争议时精确回放。
4. 将学生报告、代码或结果作为只读证据材料绑定到具体问题，不让现场时间被文件导航消耗。
5. 在不牺牲灵活性的前提下，让评分、问题难度、录音与现场备注形成可复核的 assessment record。

本路线图属于 PC/assessment 扩展规划，不表示现有 Android 功能已具备这些能力。

## 2. 已确定的答辩设计

### 2.1 总体结构

每位学生 nominal session 为 10 分钟。默认结构为：

- 简短 introduction：约 60–90 秒，只要求说明研究问题、所用方法与最重要结论，不做 mini presentation。
- Q1：项目与结果理解。
- Q2：课程方法与统计理解。
- Q3：综合推理、模型改变、局限或“如果……会怎样”的 perturbation 问题。

不另外保留最后两分钟的“综合提问”。综合推理本身就是 Q3 的职责。三道主问题问完并完成必要 follow-up 后即可结束。

### 2.2 Follow-up 的位置

Follow-up 默认紧跟当前主问题，不在三道题结束后统一回问。

每道题可以预先准备：

- 1 个主问题；
- 1 个标准 follow-up；
- 0–1 个隐藏 probe，用于回答含糊或需要进一步确认理解时使用。

Follow-up 不是独立第四题，也不单独计分；它用于校准当前主问题的评分。考官可以完全跳过不必要的 follow-up。

### 2.3 时间不是硬切片

不得为 Q1/Q2/Q3 设置强制倒计时并自动切题。不同学生、不同项目和不同问题需要不同回答长度。

系统使用“推荐时间窗口”而不是硬限制。每道题可配置：

- `recommended_min`：低于该时间不代表扣分，只提示考官该题可能回答得过短；如果学生已经完整回答，可以直接进入下一题。
- `soft_max`：接近该值时只向考官显示轻量提醒，不中断学生、不自动切题。
- `warning_lead`：在 `soft_max` 前多少秒出现提醒。

STAT5060 初始模板可采用下列默认值，之后根据 dry run 调整：

| 阶段 | 推荐窗口 | 说明 |
| --- | --- | --- |
| Introduction | 60–90 秒 | 只做项目定位，不展开完整 presentation |
| Q1 | 90–150 秒 | 项目与结果理解 |
| Q2 | 90–150 秒 | 方法与统计理解 |
| Q3 | 120–180 秒 | 综合推理、局限、perturbation，可自然更长 |

这些窗口只用于 pacing。考官可以提前结束某题，也可以在必要时超过 `soft_max`。应用不得因为超时自动隐藏题目、停止录音或强制进入下一题。

整个 session 始终显示总计时器。接近 10 分钟时给考官全局提醒；达到 10 分钟后计时器进入明显的 overtime 状态，但仍由考官在自然语义边界结束，不由软件硬切麦克风。

## 3. 现场交互原则

### 3.1 考官控制，学生默认不操作电脑

默认由考官操作 SeminarArc。学生负责看题、看材料和口头回答，不承担 PDF 导航、找代码、切窗口等操作。

原因是 10 分钟非常短，现场电脑操作会把“统计理解”混入“文件导航熟练度”，还会放大学生之间不相关的操作差异。

只有某一道题明确需要学生指出报告或代码中的具体位置时，才临时允许极短的交互；这不是常规题型。

### 3.2 Examiner Console + Student View

PC 版优先设计两个逻辑视图：

**Examiner Console**

- 学生信息与项目标题；
- 总计时器；
- 当前主问题；
- follow-up / hidden probe；
- 评分锚点与现场备注；
- 录音状态与事件 marker；
- 推荐时间窗口及轻量 pacing 提醒；
- 下一题控制。

**Student View**

- 只显示当前已 reveal 的问题；
- 必要时显示只读证据材料；
- 不显示 rubric、隐藏 follow-up、评分、考官备注和下一题；
- 可在同一屏右侧区域呈现，也应为未来第二显示器/投影模式预留接口。

第一版即使只有单屏，也应保持“考官控制 + 学生只读”的信息边界。

## 4. 右侧只读 Evidence Reader

答辩界面保留只读 Evidence Reader，但不把它做成学生自由操作的 PDF 阅读器。

每道题可以预先绑定证据锚点，例如：

- 报告 PDF 的页码或页区间；
- 某张图或表；
- 某段公式；
- 代码文件与行范围；
- 输出结果或诊断图；
- 无证据材料，仅口头回答。

当考官 reveal 某题时，右侧 reader 自动跳到对应材料。考官可手动换页或缩放，学生默认不控制。

证据材料的作用是减少“你说的是哪张图/哪个 coefficient”的歧义，而不是让学生现场搜索答案。

第一版 reader 应优先可靠支持 PDF；代码片段、图片和纯文本可以作为第二优先级。不要为了复杂通用文档编辑功能拖慢 oral-defense 核心流程。

## 5. 连续录音与可复核回放

### 5.1 单次连续录音

每场答辩使用一条连续录音，不按题目开始/停止多个文件。

这样可以避免：

- 忘记按录音；
- follow-up 落在两个音频文件之间；
- 切题时产生缺失；
- 争议时需要手工拼接多段录音。

### 5.2 事件 marker

以下操作自动写入带时间戳的 marker：

- session started；
- introduction started；
- Q1/Q2/Q3 revealed；
- follow-up revealed；
- hidden probe used；
- examiner manual marker；
- session ended。

回放界面可直接跳到某一道题或 follow-up 的位置，而不是在完整 10 分钟音频中手动拖动。

评分记录应保存对应 question ID、question version、marker 时间和现场备注，使“问题—回答—分数”能够重新核对。

### 5.3 可靠性

录音属于 assessment evidence，应优先保证：

- crash 后尽量保留已录制部分；
- session 状态和 marker 定期落盘；
- 不因 PDF reader、计时器或 UI 卡顿中断录音；
- 结束时明确确认音频已 finalize；
- 可生成 session manifest，记录音频文件、marker、问题版本与必要校验信息。

第一版不需要复杂防篡改系统，但数据结构应允许后续加入文件 hash / manifest 校验。

## 6. STAT5060 Course Pack / Plugin

不要为 STAT5060 fork 一份 SeminarArc。课程特化应实现为可装载的 course pack / assessment profile，使 PC oral-defense 能力以后复用于其他课程。

一个 course pack 至少包含：

- course ID、academic year、assessment 名称；
- nominal session duration；
- rubric；
- question category 定义；
- timing default；
- student roster；
- 每位学生的 project title；
- report / code / evidence 路径；
- Q1/Q2/Q3；
- standard follow-up 与 hidden probe；
- evidence anchor；
- question difficulty / category 标签；
- question version。

STAT5060 的默认三类问题为：

1. `PROJECT_UNDERSTANDING`：项目设计、模型选择、结果解释。
2. `METHOD_UNDERSTANDING`：课程方法、假设、参数、估计、诊断。
3. `INTEGRATIVE_REASONING`：模型改变、反事实、鲁棒性、局限、下一步分析。

Course pack 可以由 AI 辅助生成候选问题，但正式答辩前必须由考官 approve。运行时不允许模型临场自动替换学生的正式主问题。

## 7. 评分模型

若 STAT5060 oral defense 最终占课程总评 15%，course pack 默认采用三道题各 5 分：

- Q1：5 分；
- Q2：5 分；
- Q3：5 分。

Follow-up 不额外加分，而是帮助判断该题应落在哪个档位。

建议的 5 分锚点：

- 5：理解完整，能独立处理 follow-up，并能进行合理推理；
- 4：核心理解正确，有小缺口，但不影响主要结论；
- 3：基本理解正确，但解释偏浅或对 follow-up 处理有限；
- 2：存在明显理解缺口，需要较多提示；
- 1：只能复述少量表面内容，无法解释核心工作；
- 0：没有实质回答。

UI 应允许 0–5 整数分，也可后续讨论是否开放 0.5 分。不要在第一版加入 AI 自动评分。

## 8. 公平性与 difficulty audit

为了避免“问题个性化”变成“难度不一致”，course pack 需要支持答辩前快速审查：

- 每位学生是否都有 Q1/Q2/Q3 三类问题；
- 每题是否标注预期难度；
- 同一类别是否存在明显离群难题；
- 是否有需要依赖报告中不存在信息才能回答的问题；
- follow-up 是否真正延伸主问题，而不是突然切换到额外知识点；
- 是否有题目只考术语背诵而几乎没有项目上下文。

建议保留可选的 calibration view：考前并排查看所有学生同一类别的问题，快速发现难度不平衡。

## 9. 录音、隐私与课程治理

录音涉及正式 assessment，应由课程负责人确认：

- 学生是否需要事先获得录音告知；
- 录音允许哪些人访问；
- 保存到哪个时间点；
- 成绩申诉期结束后的删除策略；
- 是否允许导出转写。

SeminarArc 第一版应坚持本地优先：

- 不自动上传答辩录音；
- 不自动调用云端转写；
- 不因没有网络而阻塞答辩；
- 删除 session 时应明确区分 assessment record 与原始音频。

未来如加入转写，只作为检索和复核辅助，不作为正式评分依据。

## 10. PC 版架构原则

当前 SeminarArc 的主体是 Android 本地采集应用，因此 PC oral-defense 不应通过强行把 CameraX、Android MediaRecorder、Room/UI 全部跨平台化来实现。

第一阶段先做 architecture spike，比较：

1. Compose Multiplatform / Desktop，复用 Kotlin domain model 与部分 Compose 设计；
2. 独立 desktop shell，仅共享 course-pack/session schema 与导出格式；
3. 其他轻量本地桌面方案。

选择标准优先级：

- Windows 现场录音可靠性；
- PDF reader 与双视图实现成本；
- 本地文件恢复能力；
- 与现有 Android repo 的维护复杂度；
- 后续 macOS 扩展可能性。

技术选型在 spike 前保持开放，不为了“代码复用率”牺牲答辩现场可靠性。

## 11. 实施阶段

### Phase A — Assessment domain 与文件格式

- 定义 course pack、student、question、follow-up、evidence anchor、rubric、session、marker、score、note schema。
- 明确 question version 与 assessment record 的关联。
- 定义本地目录布局与 export manifest。
- 写最小 schema fixture，覆盖一名 STAT5060 学生。

验收：无需 GUI 即可装载 course pack，并生成/读取完整 session record。

### Phase B — PC shell 与答辩运行界面

- student/session selector；
- examiner console；
- student view；
- 总计时器；
- 每题推荐时间窗口；
- `soft_max` 前轻量提醒；
- reveal 主问题 / follow-up / hidden probe；
- 不自动切题。

验收：可以完整模拟 10 分钟、3 道主问题的答辩，不依赖录音和 PDF。

### Phase C — Evidence Reader

- PDF 只读展示；
- page anchor；
- 自动随题定位；
- 手动翻页/缩放由考官控制；
- Student View 不暴露隐藏评分信息。

验收：切换 Q1/Q2/Q3 时能可靠跳到对应报告位置。

### Phase D — Audio + Marker

- 连续录音；
- session/question/follow-up marker；
- crash-safe state flush；
- finalize 检查；
- marker-based replay。

验收：完成多轮 mock defense 后，任意问题都可以一键跳回对应录音区域；UI 操作不导致录音中断。

### Phase E — Scoring / Audit / Export

- 每题 0–5 分；
- rubric anchor；
- examiner note；
- difficulty audit；
- session manifest；
- CSV/JSON/Markdown assessment export；
- 单学生与全班汇总视图。

验收：从 course pack 到最终 15 分 oral score 有完整可复核链路。

### Phase F — STAT5060 dry run

用若干 synthetic student/project 做至少两轮模拟：

- concise but strong answer；
- verbose answer；
- Q1 很快、Q3 较长；
- follow-up 全部跳过；
- hidden probe 被使用；
- PDF anchor 缺失；
- 录音设备不可用；
- 应用异常退出后恢复；
- session 超过 nominal 10 分钟。

根据 dry run 调整 timing default、提醒强度、rubric 文案和 question-pack 结构。

只有 dry run 稳定后，才进入真实课程现场。

## 12. 第一版明确不做

为控制现场风险，第一版不做：

- AI 自动评分；
- AI 在答辩进行中动态生成正式主问题；
- 自动语音情绪/流利度评价；
- 学生自由浏览整份报告或代码库；
- 强制按题自动切换；
- 自动云同步录音；
- 用语速、停顿时长等代理指标决定成绩；
- 复杂在线 proctoring。

## 13. 后续可扩展方向

如果 STAT5060 验证有效，可将该模式泛化为 SeminarArc 的通用 `Oral Defense / Viva` 能力：

- 多课程 course pack；
- 双考官独立评分与 reconcile；
- 第二显示器 student-facing mode；
- 本地转写与搜索；
- 题库 calibration；
- rubric 版本管理；
- 匿名化问题难度复盘；
- 与课程 LMS 的成绩导出接口。

这些属于后续产品化方向，不应阻塞 STAT5060 的最小可靠版本。

## 14. 当前建议的产品判断

本场景的核心不是“把 10 分钟切得非常精确”，而是确保三道问题都能被公平、完整地问到，并让考官有足够自由根据学生回答深入或提前结束。

因此 PC 版应遵循四条主原则：

1. **总时长统一，单题时长柔性。**
2. **follow-up 紧跟主问题，服务于同一道题的评分。**
3. **学生主要负责思考和表达，电脑主要负责展示、记录和复核。**
4. **录音与 marker 是 assessment evidence；AI 只能辅助准备和复核，不能替代考官判断。**
