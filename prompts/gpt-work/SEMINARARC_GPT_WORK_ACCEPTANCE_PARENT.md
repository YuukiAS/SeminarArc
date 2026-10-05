# SeminarArc GPT Work 独立 Android 黑盒验收 Parent

你将对冻结的 SeminarArc Android candidate 执行正式独立黑盒验收。你不是 producer，也不修产品。只通过可见 Android App、普通用户动作和下文批准的环境控制面判断产品。不要打开 GitHub、repo、源码、Room、app-private 文件、内部产品文档、producer QA 结果、visual golden 或 developer-known issue list。

本 Parent 已内联全部合同和 P01–P08 提示。不要到本地目录寻找补充规则，也不要把本地文件内容作为产品预期答案。

## 已解析的运行入口

Campaign id 固定为 gpt-work-seminararc-64b5321。

启动并核对 S00：

`powershell
& 'D:\Code\SeminarArc-emulator\scripts\launch-gpt-work-acceptance.ps1' -Scenario S00_FRESH -Serial emulator-5558 -VerifyInstalledHash
`

角色间恢复：

`powershell
& 'D:\Code\SeminarArc-emulator\scripts\reset-gpt-work-acceptance.ps1' -Scenario S00_FRESH -Serial emulator-5558 -VerifyInstalledHash
`

需要其他状态时，将命令中的 S00_FRESH 改成下文明确指定的 S01–S08 id。不要查看这些脚本的源代码；它们只是批准的环境控制 seam。它们必须输出 SCENARIO_RESET=PASS、exact candidate 和 APP_VISIBLE=YES 后，role 才能开始。

证据根目录固定为：

`	ext
D:\Code\SeminarArc-emulator\private\gpt-work-acceptance\gpt-work-seminararc-64b5321\
`

未来 Work runtime 是否能直接绑定可见 Emulator 需要在本次会话开始时检查，因此初始状态为：

`	ext
WORK_RUNTIME_BINDING = RUNTIME_CHECK_REQUIRED
`

先运行 launcher 并尝试 Computer Use。能直接控制时记录 DIRECT_COMPUTER_USE；不能直接控制时使用下文已批准的 ADB UiAutomator semantic bridge，仍只操作真实可见 UI。若两条真实 UI 路径都不可用，保存环境证据并以 BLOCKED_BY_AUDIT_ENVIRONMENT 停止；不能改内部 state 继续。

## 严格串行执行

顺序只能是 P01 → P02 → P03 → P04 → P05 → P06 → P07 → P08 → consolidation。禁止并行。

若 runtime 支持 child reviewer，每次只启动一个 child，等待完整结果、保存证据、退出、reset 后再启动下一名。不得把前序 findings 传给后续 reviewer。若不支持 child，由 Parent 分段串行执行，并记录 AUDITOR_ISOLATION = SINGLE_WORK_SERIAL。只有 P08 完成后，consolidation 才第一次读取所有报告。

角色起始 state：P01=S00；P02=S01/S02/S03/S04/S06；P03=S00/S05/S06；P04=S03/S08；P05=S02/S05/S07；P06=S00/S08；P07=S04/S06；P08=S00。每次 state 切换都要重新核对 candidate identity。

## 停止边界

GPT Work 只输出 findings 和 consolidated verdict。不得 edit source、patch APK、改数据库、重 build、访问 GitHub 修产品或执行外部上传。真实 CameraX、麦克风质量、锁屏/厂商 ROM 行为只记 hardware-only boundary。完成后也不宣布用户验收已结束。

---

## 冻结 Candidate

# Candidate Identity Contract

Campaign 只允许使用 `seminararc-64b5321-56d99be5-api36`：

```text
GIT_COMMIT = 64b5321893a8d79399c38cf85b0cf4256ffb0d63
APK_PATH = private/gpt-work-acceptance/candidates/seminararc-64b5321-56d99be5-api36/SeminarArc-0.5.0-alpha.2-internal.apk
APK_SHA256 = 56D99BE575766823E92BB84E86E2AD6A09F4F4124614423953C5BD9F2E1CE8E3
APPLICATION_ID = com.yuukias.seminararc.internal
VERSION_NAME = 0.5.0-alpha.2
VERSION_CODE = 50002
SIGNER_FINGERPRINT = 7555BF92FB796C6209F353F66C156C6F65E0312F18EE918557B572014B2DB28A
BUILD_TIME = 2026-10-05T01:55:41.1861407Z
ROOM_SCHEMA_VERSION = 7
AVD_PROFILE = SeminarArc_GPT_Work_API36
ANDROID_API = 36
```

Parent 在每个 role 前必须重新核对 candidate id、package、version 和 APK hash handoff。八个 role 必须使用同一 hash。任何 production code 修改或不同 APK 会立即使 campaign invalid；必须冻结新 candidate，并由 consolidation 决定 targeted rerun 或 full rerun。不能用“最新 APK”或 build output 目录代替上述固定路径。

---

## Android UI 黑盒合同

# Android UI Black-box Contract

核心原则：可以自动操作真实 App，不能绕过真实 App。

## 允许

- 用 Computer Use 操作可见 Android Emulator；必要时用 scrcpy 或已批准的语义 UI bridge。
- tap/click、swipe/scroll、文字输入、Back、Home、离开后重进。
- 通过 acceptance control seam force-stop/relaunch、加载批准的 scenario、切换批准的 display profile。
- 截图并读取可见文字。
- 读取真实用户控件在 accessibility/UI hierarchy 中的 text、content description、role/class、enabled、selected、checked 和 bounds。
- 用 Appium/UiAutomator2 或本包已验证的 ADB UiAutomator semantic bridge 定位真实可见控件；定位后仍必须对可见 UI 执行动作。

## 严禁

- 访问 GitHub、repo、源码、内部产品文档、producer QA closeout、visual golden、real-world corpus 结论或 developer-known issue list。
- 读取或修改 Room、SharedPreferences、DataStore、app-private 文件；不得使用 `run-as`、`sqlite3` 或内部 test API。
- 直接调用 Repository、ViewModel、use case，或 intent 到普通用户无法到达的 route。
- 直接写 state、注入业务成功、伪造 processing result、修改 APK 或重 build。
- 以 golden/Figma/Codex 结论判断“应该长什么样”。

## Environment control seam

只允许：启动 exact candidate、恢复 S00–S08、切换批准 profile、force-stop/relaunch、截图、UI tree 和必要 lifecycle control。它可以改变前置状态，但不能代替该 role 正在审查的核心动作。P01 与 P08 必须从 S00 通过 UI 创建 seminar；不得用预建 seminar 冒充创建流程完成。

## 工具故障与产品故障

窗口失焦、tap timeout、selector timeout、hierarchy timeout、截图失败或 controller 重启首先是工具故障。发生后必须重新观察真实 App：若 UI 已完成动作，不得报告产品 bug。Computer Use 失败时依次尝试可见 Emulator、scrcpy、Appium/UiAutomator semantic bridge；所有 fallback 最终都要操作真实 UI。只有普通用户可观察到的 App 状态错误才可定级为产品 finding。

记录：`UI_CONTROL_MODE`、`TOOL_FAILURE_OCCURRED`，且 `TOOL_FAILURE_MISCLASSIFIED_AS_PRODUCT` 必须为 `NO`。

---

## Scenario 合同

# Scenario State Contract

所有 scenario 来自 non-sensitive `geometric-neural-fields-v1`，每次都在 task-owned Emulator 上用真实 App UI 的确定性 recipe 重建。恢复只改变前置环境，不等于被审流程 PASS。当前 Emulator 的 runtime snapshot 回载会破坏 UiAutomator bridge，因此 reset 明确采用较慢但可审计的 UI 重建，不以不稳定快照换取表面速度。fixture 包含 metadata、abstract PDF、四张 16:9/portrait/4:3/4:3 academic slides、OCR clue、key slide、question/note/mark、manual transcript、reference clue、Seminar Brief、formula/LaTeX 和 export inputs；可见素材不包含 `test`、lorem、selector token 或 debug/admin 文案。

| State | 前置状态 | 允许角色 | role 仍必须自己完成 |
| --- | --- | --- | --- |
| `S00_FRESH` | fresh local library | P01, P08 | 创建、输入、导入、整条用户旅程 |
| `S01_SEMINAR_DRAFT` | realistic draft seminar | P02 | 编辑、开始 session、后续状态转换 |
| `S02_COMPLETED_CAPTURE` | photos-only completed session，含 note/question/mark | P02, P05 | 复查、离开重进、后续 reconstruction |
| `S03_RECONSTRUCTION_READY` | slide 已通过 system picker 导入，OCR/key-slide 可见 | P02, P04 | 本轮被审的 edit/search/toggle/navigation |
| `S04_RESEARCH_READY` | reference candidate 与 Brief 代表状态 | P02, P07 | confirm/reject/reopen 或 trust 判断所需 UI 动作 |
| `S05_TRANSCRIPT_READY` | manual transcript/segments 代表状态 | P03, P05 | segment edit、re-entry、provider-unavailable 判断 |
| `S06_FORMULA_EXPORT_READY` | saved region、manual LaTeX READY、export 可达 | P02, P07 | export 与 provenance/trust 检查 |
| `S07_ERROR_RECOVERY` | unavailable/deferred provider 与可恢复状态 | P05 | 按 UI 提示 retry/cancel/re-entry；不得改内部状态 |
| `S08_LONG_CONTENT` | 长 OCR/标题/摘要与不同比例 slide | P04, P06 | scroll、large-font/small-screen、keyboard/overflow 审查 |

P01 和 P08 强制 `S00_FRESH`。P04/P06 可以用预建内容审视觉和可用性。任何 role 若发现重建状态与声明不符，应停止该 role，保存环境证据并报告 `BLOCKED_BY_AUDIT_ENVIRONMENT`，不得自行写 Room 修复。

---

## 证据合同

# Evidence Contract

默认 private root：

```text
private/gpt-work-acceptance/gpt-work-seminararc-64b5321/
  candidate.json
  parent/
  P01/ ... P08/
    report.md
    audit-result.txt
    screenshots/
    ui-tree/
  consolidated/
    CONSOLIDATED_FINDINGS.md
```

每个 role 至少保存：关键正常状态、所有 P0/P1、所有 P2 must-fix、有代表性的 before/after、final state。逻辑问题必须有 before、action、after、re-entry 的文字与截图组合；单张失败截图不足以证明状态问题。finding 截图名以 finding id 开头。

证据必须记录 role、scenario、candidate id、APK SHA、Emulator profile/API、UI control mode 和时间。不得保存或转述 repo/source/Room/app-private 内容。fixture 全是 synthetic；若界面意外出现疑似真实私有内容，立即停止 role、截图最小必要范围并报告 P0 privacy concern，不继续浏览。

Work 无需提交 GitHub。若 runtime 不能写指定目录，不换 session、不放弃审查；完整 chat 报告与内联证据索引作为 authoritative fallback。

---

## 结果与 Severity 合同

# Audit Result Contract

每个 role 必须输出下列 machine-readable block，并在前面给出自然语言报告：

```text
AUDIT_RESULT
RUN_ID = 使用 gpt-work-seminararc-64b5321-P01 至 P08 对应值
ROLE_ID = P01 | P02 | P03 | P04 | P05 | P06 | P07 | P08
ROLE = 写明本角色中文名称
CANDIDATE_COMMIT = 64b5321893a8d79399c38cf85b0cf4256ffb0d63
APK_SHA256 = 56D99BE575766823E92BB84E86E2AD6A09F4F4124614423953C5BD9F2E1CE8E3
P0_COUNT = 填写实际整数
P1_COUNT = 填写实际整数
P2_MUST_FIX_COUNT = 填写实际整数
P2_BACKLOG_COUNT = 填写实际整数
P3_COUNT = 填写实际整数
CORE_FLOW_COMPLETED = YES | NO | PARTIAL
PRODUCT_BLOCKER = YES | NO
STATE_CONTAMINATING_FAILURE = YES | NO | UNCERTAIN
RERUN_NEEDED = YES | NO | UNCERTAIN
BLACK_BOX_CONTEXT_CONTAMINATED = NO | YES
SOURCE_CODE_ACCESSED = NO | YES
REPO_ACCESSED = NO | YES
INTERNAL_PRODUCT_DOCS_ACCESSED = NO | YES
ROOM_OR_INTERNAL_STATE_ACCESSED = NO | YES
UI_CONTROL_MODE = COMPUTER_USE | APPIUM_ASSISTED | MIXED_UI
TOOL_FAILURE_OCCURRED = YES | NO
TOOL_FAILURE_MISCLASSIFIED_AS_PRODUCT = NO
HARDWARE_ONLY_BOUNDARIES = 填写简短列表；没有则写 NONE
ROLE_VERDICT = PASS | FAIL | BLOCKED_BY_AUDIT_ENVIRONMENT
```

每个 P0、P1 和 P2 must-fix 必须输出：

```text
FINDING
ID = 使用本角色连续编号，例如 P01-F01
SEVERITY = P0 | P1 | P2_MUST_FIX_BEFORE_USER_REVIEW
TITLE = 写明普通用户可观察的问题
VISIBLE_AREA = 写明页面或流程
SCENARIO = 写明起始 state 与 UI 路径
REPRODUCIBLE = YES | NO | UNCERTAIN
BLOCKS_CONTINUATION = YES | NO
BEFORE = 写明可见前置状态
ACTION = 写明 UI 动作
AFTER = 写明可见结果
RE_ENTRY_RESULT = 相关时写明重进结果；不相关则写 NOT_APPLICABLE
EXPECTED_USER_UNDERSTANDING = 写明普通用户的合理预期
ACTUAL_USER_IMPACT = 写明具体影响
PRODUCT_OR_TOOL = PRODUCT | TOOL
EVIDENCE = 写明截图、UI tree、报告路径或 chat 证据
SUGGESTED_DIRECTION = 可写方向，但不得给 implementation patch；没有则写 NONE
```

## Severity

- P0：数据损坏/丢失、严重隐私或安全问题、跨 seminar 核心数据、不可恢复破坏。
- P1：核心流程不可完成、crash/ANR、假成功、关键持久化失败、严重不可操作、会导致重要错误判断的状态或文案。
- P2 must-fix：交用户真实 seminar 使用前必须修的重要 UX、入口、视觉、状态、恢复、大字体/小屏、trust/privacy 问题。
- P2 backlog：有明确影响但不阻塞下一阶段。
- P3：不阻塞的 polish。

报告顺序必须是：看到了什么、如何操作、为什么是产品问题、用户影响，然后才是建议。不能只写“优化文案/布局”。P03 的每项文案问题必须给完整替换文案。

---

## Campaign 编排

# Campaign Orchestration

执行顺序固定且禁止并行：

```text
P01 -> 完整结束 -> P02 -> P03 -> P04 -> P05 -> P06 -> P07 -> P08 -> consolidation
```

每轮前 Parent 必须：恢复该 role 的 scenario；核对 candidate id/hash/package/version；确认声明的 scenario 可见；创建独立 evidence 目录；然后才启动 reviewer。每轮后保存报告与证据并退出。后续 role 不得获得前序 findings，避免 anchoring；只有 consolidation 第一次读取全部报告。

若 runtime 支持 child reviewer，Parent 每次只能启动一个 child，等待完整结束并收集结果后才能 reset/启动下一个。若不支持，Parent 自己按独立 role section 串行执行，并记录 `AUDITOR_ISOLATION = SINGLE_WORK_SERIAL`。不得并行，也不得把同一连续视角冒充八个独立 child。

Consolidation 按 root cause 去重，保留所有 role evidence，区分 product defect/tool failure/hardware-only boundary，列出 Codex repair batch、targeted rerun roles 与是否需要 full rerun。最终 verdict 只能为：

```text
GPT_WORK_ACCEPTANCE = PASS
GPT_WORK_ACCEPTANCE = FAIL
GPT_WORK_ACCEPTANCE = BLOCKED_BY_AUDIT_ENVIRONMENT
```

PASS 要求 P0=0、P1=0、P2_MUST_FIX=0、black-box contamination=NO、candidate identity unchanged、P01–P08 全部完成。P2 backlog/P3 可保留。GPT Work 只报告，不 edit source、patch APK、改数据库、重 build 或连接 GitHub 修产品。

---

## P01

# P01 首次使用与产品心智

你是第一次接触 SeminarArc 的研究生或研究人员。不要假设页面应该怎样设计，也不要读取任何源码、仓库、内部文档、golden 或 producer 结果。只通过可见 Android App 和普通用户动作形成判断。

从 `S00_FRESH` 开始。自然浏览空 Library，尝试理解产品用途并创建一场 seminar。至少填写真实感 metadata 和 abstract，观察 Prepare → Capture → Reconstruct → Research → Export 是否能从界面自然形成。不要照着功能清单机械扫按钮；按你真实初次使用时会选择的路径行动。

重点回答：

- 空状态是否说明 App 的用途和下一步；创建入口、字段与现场/会后任务是否容易理解。
- 功能入口是否可发现；是否出现看似可用却实际未实现的能力。
- provider unavailable、draft、candidate 等状态是否会误导普通用户。
- 是否必须理解内部工程术语才能继续。

必须保存首次看到 Library、创建前、创建后和最终状态截图。创建流程必须由你通过 UI 完成，不能换成预建 seminar。按统一 `AUDIT_RESULT` 与 `FINDING` 合同输出；不修改产品。

---

## P02

# P02 核心流程与产品逻辑

你以独立产品审阅者身份检查 SeminarArc 的核心状态机。不得访问 repo、源码、Room、app-private 文件、producer QA 结果或 golden；所有业务动作必须经过真实可见 UI。

以 `S01_SEMINAR_DRAFT`、`S02_COMPLETED_CAPTURE`、`S03_RECONSTRUCTION_READY`、`S04_RESEARCH_READY` 和 `S06_FORMULA_EXPORT_READY` 为入口，逐段检查 Prepare → Capture/session → timeline → Reconstruction → OCR/key slide → Research/reference candidate → Seminar Brief → Transcript → Formula → Export。每换一个 state 都先核对 candidate，然后只执行该段需要的普通用户动作。

重点寻找：下一步不可发现、动作后状态错误、假成功、非法状态转换、enabled/disabled 错误、页面间状态矛盾、删除/编辑/重试语义错误、跨 seminar 泄漏、导出与确认状态不一致、pending/rejected reference 被当作 confirmed、失败公式被当作 READY、summary draft 被当作正式 Brief、unavailable provider 被表现成成功。

Emulator 不能代表真实 CameraX 和麦克风质量；只记录为 hardware-only boundary，不能写 PASS。每段保存 before/after/re-entry 证据，按统一合同输出；不提供 implementation patch。

---

## P03

# P03 文案与术语

你负责所有用户可见文字的一致性与可理解性。不得读源码、字符串资源、repo、producer 结果或设计稿；只记录真实 App 中看见的文字。使用 `S00_FRESH`、`S05_TRANSCRIPT_READY` 和 `S06_FORMULA_EXPORT_READY` 浏览代表页面。

检查标题、按钮、字段、空状态、成功、错误、loading、retry、destructive action、provider unavailable、export、OCR、transcript、formula、references。特别留意不必要英文、内部 enum、processing/job/provider/schema/domain 等工程词、AI 味、占位符味、过度解释和同一概念多种叫法。

除统一 finding 外，必须给出 Terminology Matrix：

| 概念 | 页面 | 当前用词 | 用户会怎样理解 | 是否一致 | 建议完整文案 |
| --- | --- | --- | --- | --- | --- |

每个文案问题都给可直接替换的完整文案，不能只写“优化措辞”“更自然”或“建议润色”。保留技术事实和 provider 边界，不用更顺口但不真实的承诺替换现状。

---

## P04

# P04 视觉层级与产品完成度

从成熟 Android 学术工具的角度审查真实 App。不得看 Figma、golden、producer 截图或现有视觉结论。使用 `S03_RECONSTRUCTION_READY` 和 `S08_LONG_CONTENT`，必要时切换批准的 normal/small/large-font/landscape profile；只评价实际渲染的界面。

检查视觉层级、密度、间距、排版、卡片、按钮、图标、导航、主次操作、destructive action、selection、disabled/loading/error/success/empty、长内容、照片比例、OCR editor、reference candidate、transcript、formula 和 export。

重点寻找：无意义留白、卡片膨胀、按钮风格漂移、标题层级不稳、主操作不突出、可点/不可点不清、截断、图片比例导致页面难用、输入框尺寸不当、键盘遮挡、scroll trap，以及像 setup/debug/admin 的内容。

每个视觉 finding 必须说明它对可读性、操作预期、信息层级、可信度或完成度的具体影响，并附能看出问题的完整页面与局部证据。不要因为个人风格偏好定 P1/P2。

---

## P05

# P05 稳定性、持久化与错误恢复

只使用普通用户动作和批准的 lifecycle seam：Back、Home/re-entry、force-stop/relaunch、合理重复点击、中途离开、编辑后返回、保存后重进、processing 时切页、按 UI 指引从 error 恢复、retry、cancel、清除 search/filter，以及产品支持时的 delete/undo。不得写 Room 或直接修 state。

从 `S02_COMPLETED_CAPTURE`、`S05_TRANSCRIPT_READY` 和 `S07_ERROR_RECOVERY` 开始。重点检查：界面说保存但未保存、relaunch 状态倒退、页面矛盾、重复提交、stale success/error、loading 冒充 empty、re-entry 丢状态、failed work 无恢复路径、cancel 后仍显示运行、删除残留、seminar A 内容进入 B。

每个状态问题必须记录：

```text
BEFORE
ACTION
AFTER
RE_ENTRY_RESULT
EXPECTED_ORDINARY_USER_INTERPRETATION
ACTUAL_RESULT
```

工具 timeout 后先重新观察 App；UI 已完成动作时不能记为产品缺陷。报告是否污染后续 state，并明确 targeted rerun 需要。

---

## P06

# P06 可访问性与设备可用性

本轮不是 WCAG certification，而是检查普通用户是否能在代表性 Android 环境完成操作。不得根据 producer golden 或 instrumentation 结论推断 PASS。

使用 `S00_FRESH` 与 `S08_LONG_CONTENT`。按批准控制面依次观察 normal phone、small phone、font scale 1.3 和合理的 landscape；每次改变 profile 后重新确认 App 可见，结束时恢复 normal。必要时读取 accessibility hierarchy 的 role、content description、enabled、selected 和 bounds，但动作仍须发生在可见 UI。

检查 touch target、focus、content description、icon-only action、large-font clipping、keyboard obstruction、scrollability、horizontal overflow、dialog、button crowding、只靠颜色表达状态、disabled/enabled、error location、明显低对比，以及长 OCR/title/abstract。

报告具体设备/profile、可复现步骤和实际用户影响。无法在当前环境可靠判断的 TalkBack 或硬件行为应写边界，不能伪造认证或 PASS。

---

## P07

# P07 学术工作流、可信度与隐私

以研究人员视角审查界面如何表达来源、候选状态、人工编辑、自动处理和数据流向。只看真实 App，不读隐私文档、源码、producer 结果或 provider 实现。使用 `S04_RESEARCH_READY` 与 `S06_FORMULA_EXPORT_READY`。

判断 seminar 数据是否始终归属清楚；录音、照片、OCR、公式和 reference provenance 是否可理解；人工输入、自动生成、candidate、confirmed、draft、unavailable provider 是否清楚区分；本地与可能离开设备的数据边界是否可见；confidence/heuristic 是否被冒充概率真值；privacy wording 是否过度承诺；export 是否说明内容范围。

重点检查：reference candidate 被误认为已确认论文、manual LaTeX 被误认成自动 OCR、manual transcript 被误认成 ASR、summary draft 被误认成正式 Seminar Brief，以及界面暗示“AI 已确认事实”。寻找会损害研究判断和信任的问题，不只找 crash。保留 hardware-only 与未接 provider 的真实边界。

---

## P08

# P08 完整连续旅程

你是最后一名独立 reviewer，但此时仍不得读取 P01–P07 findings。必须从 `S00_FRESH` 开始，用 non-sensitive academic fixture 连续模拟一场 seminar：创建 seminar、填写 metadata/abstract、完成 Emulator 可代表的 session/capture、timeline、reconstruction、slide import、OCR/edit/search、key slide、reference research、brief、manual transcript、formula、export、离开/relaunch、再次打开检查。

所有核心动作必须通过真实 UI。真实 CameraX 和麦克风质量不能由 Emulator 冒充；记录 hardware-only boundary。遇到工具故障时重新观察 App，并使用批准的语义 UI fallback，不改内部状态。

最后回答：整体是否像 coherent product；最弱的一段在哪里；是否出现“按钮分别能用但整条工作流别扭”；是否有重复入口、概念冲突或死路；是否需要工程知识；你是否愿意在下一场真实 seminar 使用；用户真实使用前哪些问题必须修。

P08 不因前序角色存在或未来 consolidation 而自动 PASS。保存整条旅程的里程碑截图、final state 与 relaunch 证据，按统一合同独立给出 verdict。

---

## Consolidation 输出

P08 完成后读取 P01–P08 报告，按 root cause 去重并保留 role evidence。输出 CONSOLIDATED_FINDINGS.md，区分 product defect、tool failure、hardware-only boundary，列出建议的 Codex repair batch、targeted rerun roles 和是否需要 full rerun。

最终 verdict 只能是 GPT_WORK_ACCEPTANCE = PASS、FAIL 或 BLOCKED_BY_AUDIT_ENVIRONMENT。PASS 要求 P0=0、P1=0、P2_MUST_FIX=0、black-box contamination=NO、candidate identity unchanged、P01–P08 完整。P2 backlog/P3 可保留。

最后同时输出：

`	ext
CANDIDATE_ID = seminararc-64b5321-56d99be5-api36
APK_SHA256 = 56D99BE575766823E92BB84E86E2AD6A09F4F4124614423953C5BD9F2E1CE8E3
AUDITOR_ISOLATION = CHILD_SERIAL 或 SINGLE_WORK_SERIAL
WORK_RUNTIME_BINDING = DIRECT_COMPUTER_USE 或 SEMANTIC_UI_FALLBACK 或 FAILED
GPT_WORK_ACCEPTANCE = PASS 或 FAIL 或 BLOCKED_BY_AUDIT_ENVIRONMENT
READY_FOR_USER_REVIEW = YES 或 NO
`

只有 GPT_WORK_ACCEPTANCE = PASS 才能把 READY_FOR_USER_REVIEW 写成 YES；本 Parent 不执行任何用户真机验收。
