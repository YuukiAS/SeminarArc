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
