# Parent Delivery Gate

只有以下门禁全部通过才可写 `READY_TO_PASTE_PARENT = YES`。

## Candidate

- commit 存在；frozen APK 存在且 SHA-256 一致。
- package、versionCode/versionName、signer、Room schema、AVD/API 已解析并冻结。
- installed base APK hash 与 frozen APK 一致。

## Environment

- inventory 明确记录；所有写操作只针对 `emulator-*`。
- `SeminarArc_GPT_Work_API36` 能 boot；UI recipe reset、install、launch、visible package、screenshot、UI hierarchy 可用。
- 牺牲性 probe 实际完成控件定位、click、input、screenshot、Back、force-stop/relaunch；最后恢复 S00。

## Scenario

- S00–S08 UI recipe 均存在并完成实际重建；重复 reset 不依赖 app-private 写入或 runtime snapshot。
- fixture manifest 无 private 内容，四张 slide 尺寸/比例有效，abstract PDF 有效。
- scenario 页面不含 debug/selector/placeholder 文案；核心被审动作未被 control seam 代做。

## Prompt

- P01–P08 全部存在；顺序严格串行；黑盒/result/evidence/severity/candidate/reset/tool-failure/stop contract 已内联。
- Parent 不含 unresolved placeholder、secret、private identity path、producer QA 结论、repo/source access 指令或矛盾规则。
- output root 和 candidate identity 已解析。

## Required terminal output

```text
ACCEPTANCE_INFRASTRUCTURE=PASS
CANDIDATE_IDENTITY_GATE=PASS
EMULATOR_CONTROL_GATE=PASS
ANDROID_UI_AUTOMATION_GATE=PASS
SCENARIO_RESET_GATE=PASS
EVIDENCE_PIPELINE_GATE=PASS
P01_PROMPT=PASS ... P08_PROMPT=PASS
STRICT_SERIAL_ORDER=PASS
BLACK_BOX_CONTRACT_INLINE=PASS
AUDIT_RESULT_CONTRACT_INLINE=PASS
NO_UNRESOLVED_PLACEHOLDERS=PASS
NO_PRODUCER_RESULT_CONTAMINATION=PASS
PARENT_DELIVERY_GATE=PASS
READY_TO_PASTE_PARENT=YES
GPT_WORK_EXECUTED=NO
READY_FOR_USER_REVIEW=NO
USER_ACTION_REQUIRED_NOW=NO
```

`WORK_RUNTIME_BINDING = RUNTIME_CHECK_REQUIRED` 只表示未来 GPT Work 会话需确认能否直接绑定本机可见 Emulator；已验证的 launcher 与 semantic UI fallback 仍必须完整可用。
