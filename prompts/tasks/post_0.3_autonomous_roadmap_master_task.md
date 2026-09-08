---
id: post_0.3_autonomous_roadmap_master
status: ready
owner: codex
created: 2026-09-08
allow_code_change: true
allow_shell_command: true
allow_network: true
allow_external_upload: true
requires_human_approval: false
---

# SeminarArc Post-0.3 Autonomous Roadmap Master

## Objective

在用户无法频繁参与接力时，按仓库 roadmap 尽可能自动推进 SeminarArc：先完成 `0.3.1-internal` 可下载安装分发，再依次推进 `0.4.x`、`0.5.x` 的 readiness -> implementation -> Emulator closeout -> GitHub Release APK。

不要自动进入 Google Play/public release。`0.9.x` 只允许 readiness/audit，不允许生产签名、Play Console、公开发布或 ads/payment。

## Source of truth

- GitHub `origin/main` 是唯一源码事实来源。
- 开始时同步最新 `main`。
- 必须读取：`AGENTS.md`、`prompts/AGENT_RULES.md`、`prompts/CHATGPT_RULES.md`、`docs/plans/post-0.3-autonomous-roadmap.md`、README/TODO/CHANGELOG/ARCHITECTURE/PRIVACY/DEVICE_TESTING 与当前 design/plans/tasks/results。

## Execution environment

Windows native checkout：`D:\Code\SeminarArc-emulator`

Windows SDK：`D:\Android\Sdk`

JDK 17：`D:\Code\_jdks\jdk-17.0.20.1+1`

Windows Emulator 是默认 connected/instrumentation target。GM1910 不参与日常开发/CI。

如果当前是 WSL 且 Windows-only gate 暂时不可用：继续 research/docs/headless/code-safe 工作，把 connected/release gate 留给后续 Windows native 阶段；不得通过 usbipd/WSL transport/physical device 绕过。

## Release distribution policy

**GitHub Releases 是用户 APK 下载入口；GitHub Actions 不是 APK 分发依赖。**

Accepted milestone 的 APK 流程：

1. Windows 本地 build/test/lint；
2. Windows Emulator connected closeout；
3. package/version/signature/secret scan；
4. 使用稳定 repo 外 internal signer 签名；
5. 计算 SHA-256；
6. commit + push main；
7. 创建 version tag；
8. 本地通过 `gh release create/upload` 或等价 GitHub API 创建 prerelease；
9. 上传已签名 APK + `.sha256` + release notes。

用户已明确授权：可以把 SeminarArc internal/pre-release APK、checksum 和 release notes 上传到本仓库 GitHub Releases。禁止上传用户数据、测试媒体、keystore/private key/password/token。

GitHub Actions 仅保留 ordinary CI。不要为了 APK 发布新增每次 push 都运行的 packaging workflow。

## Phase A — 0.3.1 Internal Dogfood

执行：

`prompts/tasks/0.3.1_internal_dogfood_distribution_task.md`

目标：用户从 GitHub `Releases` 直接下载 APK 并侧载。

需要：

- versioning；
- `.internal` identity；
- Windows local build/sign；
- Emulator regression；
- release asset；
- install/update docs。

如果稳定 internal signer 尚不存在：完成所有其余工作，并把 signer 初始化列为一次性 human action；不要让它阻塞后续 roadmap。

## Phase B — 0.4.x Readiness

在 0.3.1 可自动部分完成后创建并执行 `0.4.x` readiness task/result/plan。

至少研究：

1. transcription options：on-device/local/open-source/self-hosted/cloud、Android 可行性、语言/timestamp/资源/license/cost/privacy/cancel/retry/idempotency/offline；
2. Room migration from v4；
3. transcript segment 与 recording offset/photo/timeline window；
4. SummaryProvider：selected input、draft/provenance/edit、不覆盖 manual Brief；
5. Notion：Markdown-first、official API/OAuth/file upload/credential/backend；
6. secrets/backend requirements；
7. privacy/network；
8. UI/design；
9. test/Emulator；
10. scope/out-of-scope。

变化中的 provider/API/license 必须查官方文档。

结果必须 `READINESS_PASS` / `READINESS_BLOCKED`。单个 provider/account 被阻塞时只冻结该子路径，不要拖死整个 0.4.x。

## Phase C — 0.4.x Production

Readiness PASS 后自动创建 production master 并连续执行到 `0.4.x COMPLETE`。

原则：provider-independent domain first；fake/contract tests before live；local/self-hosted 优先；cloud processing opt-in；Summary 仅 editable draft；Notion 没有安全 OAuth/backend 时允许只做 Markdown-friendly/export boundary；不嵌入私人 key；不使用真实 seminar 内容做 live smoke。

Closeout：Windows Emulator + 0.1/0.2/0.3 regression。通过后按 Release policy 发布 `0.4.x-internal.*` APK。

## Phase D — 0.5.x Readiness

完成 0.4.x 后自动进入 readiness，研究：

- formula region selection UX；
- Mathpix terms/cost/commercial/privacy；
- local/open/self-hosted formula OCR alternatives；
- formula provider boundary；
- LaTeX correction/provenance/confidence；
- confirmed references -> BibTeX/RIS deterministic export；
- Room/data model；
- privacy/secrets/backend；
- Emulator tests；
- explicit out-of-scope。

Paid credential/account 是 human boundary；Mathpix blocked 不得阻止 region-selection/contracts/BibTeX/RIS 等 safe work。

## Phase E — 0.5.x Production

Readiness PASS 后连续执行到 `0.5.x COMPLETE`。

Closeout 至少：JVM/build/lint、Windows Emulator connected、old Room migrations reopen current、0.1/0.2/0.3/0.4 regression、export golden tests、secret scan、docs/design/privacy/roadmap sync。

通过后按 Release policy 发布 `0.5.x-alpha.*` APK。

## Phase F — Stop before external release

0.5.x 完成后只创建 `0.9.x_release_readiness_report`。

禁止自动：

- create/change production signing key；
- Google Play Console；
- publish privacy policy externally；
- Data safety submission；
- AAB track upload；
- ads/payment；
- public release。

## Autonomous behavior

Within this master，Codex 可创建所需 subtask/result 并在无 hard blocker 时连续进入下一阶段，不必等待用户。

每个宏阶段：

research/readiness -> plan -> implementation -> tests -> Emulator closeout -> docs/result -> commit/push -> local APK build/sign -> GitHub prerelease -> continue。

普通 compile/unit/instrumentation/lint/parser/mock/Emulator/docs drift/temporary provider failure 均不是人工 blocker，应修复后继续。

## Hard stops / human approval

只在以下情况停止受影响路径或整个 master：

- 唯一可行路径需要新 paid account/subscription；
- 必须部署 recurring-cost backend；
- 必须 provision 新 private API credential；
- destructive migration / credible data-loss risk 无法安全解决；
- production signing / Google Play / public release；
- physical-device write/install/instrumentation 是唯一验证且未授权；
- major product scope/UX conflict；
- provider terms 与 commercial/privacy model 实质冲突。

稳定 internal signing key 的一次性初始化可以请求用户批准，但若只是影响 update-in-place/release signing，不应阻塞 0.4/0.5 代码开发。

## Final output

创建：`prompts/tasks/post_0.3_autonomous_roadmap_master_result.md`

总结：completed versions、Room version、GitHub Release tags/assets、signing status、0.4/0.5 decisions/implementation、regression、deferred hardware/provider work、0.9.x 前所需 human approvals、final commit SHA。
