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
