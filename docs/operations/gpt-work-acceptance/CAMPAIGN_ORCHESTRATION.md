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
