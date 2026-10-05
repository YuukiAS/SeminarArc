# SeminarArc GPT Work Android 黑盒验收

本目录定义独立 reviewer 使用的 Android 黑盒验收子系统。它与 producer-side JVM、instrumentation、visual golden、stress 和 real-world corpus 证据分离；这些已有结果不会作为 GPT Work 的预期答案。

当前冻结 candidate 见 `CURRENT_CANDIDATE.json`。一键入口：

```powershell
.\scripts\launch-gpt-work-acceptance.ps1 -Scenario S00_FRESH -VerifyInstalledHash
```

角色间恢复：

```powershell
.\scripts\reset-gpt-work-acceptance.ps1 -Scenario S04_RESEARCH_READY
```

producer delivery gate：

```powershell
.\scripts\verify-gpt-work-acceptance.ps1
```

正式 campaign 只能读取最终 Parent 中内联的合同、candidate handoff 和 role prompt；不得读取本仓库、源码、Room、app-private 文件、producer QA 结果或 golden。当前包只建设环境，不执行正式 GPT Work，也不改变 `READY_FOR_USER_REVIEW = NO`。

## 控制面

- 可见目标：task-owned `SeminarArc_GPT_Work_API36`，默认 serial `emulator-5558`。
- UI 路径：Computer Use 优先；语义 fallback 为 ADB `uiautomator dump` + 对真实可见节点执行显式 tap/input/swipe。
- scenario：在 task-owned Emulator 上通过确定性 UI recipe 重建，只改变前置环境；review 中的核心用户动作仍必须通过真实 UI 完成。运行时 snapshot 会让当前 Emulator 的 UiAutomator bridge 失效，因此本包明确不依赖 snapshot 快照冒充可重复恢复。
- fixture：`geometric-neural-fields-v1`，完全 synthetic、无真实 seminar 内容，包含 abstract PDF 和四张不同比例的学术 slide。
- 证据：`private/gpt-work-acceptance/<campaign-id>/`，不进入 Git。
- Work runtime 是否能直接绑定本机 Emulator 必须在实际 Work 会话中检查，因此 Parent 保留 `WORK_RUNTIME_BINDING = RUNTIME_CHECK_REQUIRED`；这不影响已验证的本机 UI 控制 fallback。

## 文件职责

- `ANDROID_UI_BLACKBOX_CONTRACT.md`：真实 UI 与禁止绕过边界。
- `AUDIT_RESULT_CONTRACT.md`：severity、finding 和 role 输出结构。
- `CAMPAIGN_ORCHESTRATION.md`：严格串行、隔离与 consolidation。
- `CANDIDATE_IDENTITY_CONTRACT.md`：exact APK 冻结规则。
- `SCENARIO_STATE_CONTRACT.md`：S00–S08 来源、角色映射和限制。
- `EVIDENCE_CONTRACT.md`：截图/UI tree/过程证据。
- `PARENT_DELIVERY_GATE.md`：交付前的静态与动态门禁。
