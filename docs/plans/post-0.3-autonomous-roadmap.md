# SeminarArc Post-0.3 Autonomous Roadmap

Status: active roadmap

Updated: 2026-09-13

## Goal

在用户无法频繁参与开发接力时，让 SeminarArc 在严格 gate、Emulator-first、安全边界和文档化 handoff 下自动推进；同时避免把“功能已实现”误当成“真实用户流程已充分验收”。

`origin/main` 是唯一源码事实来源。普通 compile/test/lint/Emulator bug 不应成为人工 blocker；只有产品选择、外部账号/secret、付费 provider、真实数据破坏风险、商店发布或真实物理设备操作需要人工参与。

## Current baseline

- `0.1.x` Local Capture MVP: COMPLETE.
- `0.2.x` Local Visual Reconstruction: COMPLETE.
- `0.3.x` Reference Candidate + Seminar Brief: COMPLETE.
- `0.4.x` Transcription/Summary/Notion-ready local-safe foundation: COMPLETE.
- `0.5.x` Formula + Research Export local-safe alpha: COMPLETE.
- Current Room schema: v7.
- Current installable prerelease: `v0.5.0-alpha.2`.
- Current active engineering phase: **0.5.x automated black-box QA hardening**.
- `0.9.x` audit: `AUDIT_COMPLETE_NOT_READY_FOR_PRODUCTION_RELEASE`.
- Default connected/instrumentation target: Windows Pixel 8 API 36 Emulator.
- Protected GM1910 is not a daily automated test target.
- Repository visibility is currently public.

## Distribution / CI principle

GitHub Releases 是用户 APK 下载入口；GitHub Actions 是版本级远端验收，不是每次 push 的持续构建器，也不是 APK 分发渠道。

当前策略：

- 普通 code/docs/task/result/review push 不自动跑 GitHub Actions；
- push `v*` version tag 时跑一次 version gate；
- 必要时可 `workflow_dispatch` 手动跑一次；
- Windows 本地 build/sign；
- Windows Emulator 完成 connected/instrumentation；
- package/version/signature/secret scan 后生成 signed internal APK；
- version tag -> GitHub Actions PASS -> GitHub Release；
- stable internal signer 始终留在 repo 外。

当前 internal identity：

- application id: `com.yuukias.seminararc.internal`
- current version: `0.5.0-alpha.2`
- versionCode: `50002`

## Milestone A — 0.3.1 Internal Dogfood Distribution

Status: COMPLETE.

已建立：

- 独立 internal application id；
- repo 外稳定 signer；
- Windows local build/sign；
- Emulator validation；
- GitHub version CI；
- GitHub Release APK + SHA-256；
- update-in-place 路径。

## Milestone B — 0.4.x Advanced Processing Foundation

Status: COMPLETE for approved local-safe scope.

已完成：

- transcript / timestamped segment schema 与 repository；
- provider-independent `TranscriptionProvider` / `SummaryProvider` boundary；
- manual transcript import / segment edit；
- durable transcription / summary job foundation；
- transcript timeline windows；
- editable summary draft 与显式 Apply to Seminar Brief；
- Notion-ready local Markdown/save/share boundary；
- Markdown/ZIP compatibility。

Deferred：

- live ASR provider；
- online AI summary runtime；
- Notion OAuth/upload/backend。

这些 deferred provider 不应阻塞本地核心和 QA hardening。

## Milestone C — 0.5.x Formula + Research Export

Status: COMPLETE for approved local-safe alpha scope.

已完成：

- formula region create / drag / edit；
- Room v7 formula domain；
- manual LaTeX durable queue；
- provider capability/status boundary；
- READY formula result export；
- deterministic BibTeX / RIS；
- Windows explicit-emulator regression；
- `v0.5.0-alpha.2` Release。

Deferred：

- live Mathpix；
- PaddleOCR / pix2tex bundling；
- cloud formula OCR。

## Milestone D — 0.5.x Automated Black-box QA Hardening

Status: ACTIVE.

Plan:

`docs/plans/0.5.x-blackbox-acceptance-plan.md`

目标：把验收模式从“用户逐项手工点击”升级为“自动 package-level black-box acceptance + 少量真人真机验收”。

### D1 — Harness foundation

Task:

`prompts/tasks/0.5.x_blackbox_acceptance_harness_task.md`

建立长期入口：

`scripts/run-blackbox-acceptance.ps1`

第一阶段自动覆盖：

- B01 fresh install / empty library；
- B02 seminar CRUD / persistence；
- B03 Emulator-safe session lifecycle；
- B08 force-stop recovery / synthetic cleanup。

Acceptance 必须从安装后的 App 外部驱动，通过 UI tree / UI Automator / adb input / screenshot / scoped logcat 完成，禁止直接写 Room 或调用 repository 来伪造用户路径。

### D2 — Research-flow black-box

Harness foundation 稳定后再覆盖：

- B04 Reconstruction；
- B05 Reference lookup / Candidate Review / Brief；
- B06 Transcript / Summary local-safe flow；
- B07 Formula / Markdown / ZIP / BibTeX / RIS / Notion-ready local export。

未配置的 live ASR / AI / Notion / formula providers 只验证 unavailable 状态正确，不要求用户手工证明未实现功能。

### D3 — Visual regression

核心 E2E 稳定后建立 screenshot/golden：

- empty/populated library；
- New Seminar；
- Detail / Active Session；
- Reconstruction；
- Reference Review；
- Transcript Review；
- Formula UI；
- normal/small screen；
- normal/enlarged font；
- long text / empty / error / loading。

Golden 更新必须显式批准，测试失败不能自动接受新截图。

### D4 — Exploratory / device matrix

在核心 E2E 稳定后：

- package-scoped Monkey + fixed seed；
- current API 36 phone；
- smaller-screen Emulator；
- 一个仍在支持范围内的旧 API profile；
- major alpha / 0.9 再评估 Firebase Test Lab Robo / 小型真实设备矩阵。

没有现成 Firebase 配置时，不自动创建付费资源。

### Human-only acceptance after automation

自动 QA 稳定后，用户人工验收应只剩：

1. 真手机麦克风录音质量；
2. CameraX 拍真实投影/PPT 的质量和方向；
3. 锁屏/后台/厂商 ROM 对长录音的影响；
4. 一次低风险真实 seminar 的连续使用手感；
5. 主观 UI/UX 判断。

用户不再负责逐项证明普通 CRUD、navigation、export、persistence 按钮是否工作。

### Release rule during QA hardening

Black-box harness / docs / tests 的普通提交不创建版本 tag。

只有当 QA 发现并修复 P0/P1 或明显 dogfood blocker、值得用户重新安装验证时，才准备 `0.5.1-alpha.*`；否则继续完善 harness，不制造无意义 Release。

## Milestone E — 0.9.x Release Preparation

当前仅允许 readiness/audit，不自动进入 production release。

`0.5.x` QA hardening 与必要的真实手机 dogfood 证据完成后，再决定是否启动 production-readiness work。

以下必须用户明确批准：

- production signing key creation/change；
- Google Play Console；
- privacy-policy publication；
- Data safety submission；
- release-track rollout；
- ads/payment；
- public release。

Before release work require：

- versioning/signing audit；
- release APK/AAB secret scan；
- backup/upgrade/migration tests from installed internal versions；
- no-network/weak-network/low-storage/long-session tests；
- automated black-box acceptance baseline；
- hardware checkpoint plan；
- permission/FGS/background-policy review；
- privacy + third-party SDK inventory。

## Autonomous execution policy

Current default sequence：

1. execute current black-box task；
2. review result；
3. continue research-flow black-box if foundation is stable；
4. visual regression；
5. exploratory/multi-device；
6. user performs only remaining human-only hardware/usability checks；
7. if serious bug fixes justify it, publish a new internal alpha；
8. only then revisit `0.9.x` production-readiness decisions。

Codex may automatically fix ordinary navigation/layout/accessibility/persistence/export/testability bugs discovered by QA and add regression coverage without repeatedly asking the user.

Hard stop / human approval：

- paid API/provider account or secret；
- recurring-cost backend/service deployment；
- external cloud upload of seminar content；
- destructive migration/data-loss risk；
- production signing/Play Console/public release；
- physical-device write/install/instrumentation unless separately authorized；
- major product-scope choice not resolved by roadmap。

## User installation target

Current dogfood build:

`v0.5.0-alpha.2`

用户可从 GitHub Releases 直接下载 APK。现阶段可以保留安装在手机上，但不要求用户在 automated black-box foundation 完成前手工逐项走完整功能清单。

后续同 applicationId + 同 signing key + 更高 versionCode 的 Release APK 可以直接覆盖升级并保留数据。production Play build 使用独立 release identity/signing 计划。