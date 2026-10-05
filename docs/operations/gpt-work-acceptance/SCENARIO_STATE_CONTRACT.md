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
