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
