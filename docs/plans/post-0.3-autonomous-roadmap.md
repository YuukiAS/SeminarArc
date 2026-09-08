# SeminarArc Post-0.3 Autonomous Roadmap

Status: active roadmap

Updated: 2026-09-08

## Goal

在用户无法频繁参与开发接力时，让 SeminarArc 在严格 gate、Emulator-first、安全边界和文档化 handoff 下，尽可能沿既定产品路线自动推进。

`origin/main` 是唯一源码事实来源。普通 compile/test/lint/Emulator bug 不应成为人工 blocker；只有产品选择、外部账号/secret、付费 provider、真实数据破坏风险、商店发布或真实物理设备操作需要人工参与。

## Current baseline

- `0.1.x` Local Capture MVP: COMPLETE.
- `0.2.x` Local Visual Reconstruction: COMPLETE.
- `0.3.x` Reference Candidate + Seminar Brief: COMPLETE.
- Current Room schema: v4.
- Default connected/instrumentation target: Windows `Pixel_8` API 36 Emulator.
- Protected GM1910 is not a daily test target.
- Repository visibility is currently public.

## Distribution principle

从 `0.3.1-internal` 起，用户下载 APK 的默认入口是 **GitHub Releases**，不是 GitHub Actions artifacts。

- build/test/sign 在受控 Windows 本地环境完成；`0.3.1-internal.1` 默认使用 `versionCode = 30101`，后续里程碑按版本号派生单调递增 code；
- accepted milestone 通过 test + Emulator + package/secret scan 后，本地生成签名 APK；
- 由本地 `gh release create/upload` 或 GitHub API 上传 APK + SHA-256 + release notes；
- release APK signing key 永远留在本地/repo 外，不上传 GitHub；
- 相同 internal application id + 相同 signer 才支持覆盖升级并保留数据。
- 当前 internal application id 为 `com.yuukias.seminararc.internal`，app label 为 `SeminarArc Internal`。

当前仓库为 public，因此 Release asset 也公开可下载；如果未来仓库转 private，再重新评估下载权限和 Actions 配额。

## CI principle

GitHub Actions 是**版本级远端验收**，不是每次 push 的持续构建器，也不是 APK 分发渠道。

当前策略见 `docs/CI_POLICY.md`：

- 普通代码 push 不自动跑；
- docs/task/result/review/README/TODO/design 更新不自动跑；
- push `v*` 版本 tag 时跑一次；
- 必要时可 `workflow_dispatch` 手动跑一次；
- 每个面向用户的 internal/alpha 版本至少有一次远端 version gate；
- 远端 gate 至少执行 `testDebugUnitTest assembleDebug lintDebug`；
- Windows Emulator connected/instrumentation 仍只在本地 Windows 运行。

版本发布顺序固定为：本地完整验收 -> commit/push -> 本地签名 APK -> push version tag -> GitHub Actions version gate -> CI PASS -> GitHub Release。

## Milestone A — 0.3.1-internal Dogfood Distribution

在大型 `0.4.x` production work 前完成。

Goal: 产出用户可以从仓库 `Releases` 直接下载并侧载的 internal APK，无需用户自行 build。

Required work:

- 修正 stale app version metadata，建立可重复版本约定；
- internal application id/build variant，例如 `.internal`；
- Windows 本地 assemble/sign；
- unit/build/lint + Windows Emulator regression；
- APK package/version/signature/secret scan；
- SHA-256；
- Git tag + version CI；
- CI PASS 后创建 GitHub prerelease + APK release asset；
- `docs/INTERNAL_DISTRIBUTION.md`；
- 不提交 keystore/private key/password。

### Signing

稳定 internal signer 保存在 Windows 本地、repo 外；Gradle 从环境变量或本地非版本化配置读取。GitHub 只收到已经签名的 APK，不需要 GitHub Actions Secrets。

若当前还没有稳定 signer，可先完成 disposable dogfood build/release foundation；真正 update-in-place 的稳定 signer 初始化可作为一次性 human approval，但不得阻塞其他 roadmap 开发。

### Distribution stages

- `0.3.1-internal.*`: first downloadable dogfood APK/releases.
- `0.4.x-internal.*`: accepted 0.4.x milestones publish new GitHub prerelease APKs.
- `0.5.x-alpha.*`: formula/research export line稳定后继续 GitHub prerelease APKs.
- `0.9.x`: primary tester distribution 迁到 signed AAB / Google Play internal testing；APK 可保留诊断副产物。

## Milestone B — 0.4.x Advanced Processing

不要直接执行 TODO 粗略范围；先做 readiness gate。

### Readiness topics

- transcription provider architecture：local/open-source/self-hosted/cloud、Android 可行性、语言、timestamp、资源、license/cost/privacy、cancel/retry/idempotency/offline；
- Room migration from v4；
- transcript segment 与 recording offset / slide / timeline window；
- SummaryProvider：用户选定输入、draft/provenance/edit，不覆盖 manual Brief；
- Notion：Markdown-first、official API/OAuth、file upload、credential/backend 边界；
- secrets/backend decision；
- privacy/network contract；
- UI/design/test/Emulator strategy。

### Production target after readiness

如果 readiness PASS 且无真正产品/安全冲突：

1. transcript schema/provider boundary；
2. 至少一个批准的 transcription path；
3. timestamped transcript review 与 timeline/photo window 联动；
4. cancellable/retryable processing；
5. SummaryProvider 只产生 editable draft；
6. Notion 只实现安全 credential/privacy 架构允许的部分；
7. Markdown/ZIP compatibility；
8. Windows Emulator closeout；
9. accepted milestone 后本地 build/sign；
10. push version tag 跑一次远端 CI；
11. CI PASS 后发布 GitHub Release APK。

如果某 provider 需要 app-owned secret/backend 且没有安全路径，只冻结该子路径，继续其他 local-safe work。

## Milestone C — 0.5.x Formula + Research Export

先做 readiness/license gate。

Targets:

- formula region selection；
- formula OCR provider boundary；
- Mathpix 与 licensable local/open/self-hosted alternatives；
- LaTeX correction/review + confidence/provenance；
- BibTeX/RIS export；
- research export polish；
- no automatic paywalled PDF retrieval；
- Windows Emulator regression；
- accepted milestone 本地构建/sign；
- version tag remote CI；
- CI PASS 后发布 GitHub prerelease APK。

Paid API keys/commercial credentials 是 human-approval boundary，绝不提交 repo。

## Milestone D — 0.9.x Release Preparation

`0.5.x` 完成后不要自动公开发布，只做 0.9.x readiness/audit。

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
- hardware checkpoint plan；
- permission/FGS/background-policy review；
- privacy + third-party SDK inventory。

## Autonomous execution policy

At each version line:

1. research/readiness；
2. plan/result；
3. production master if gate passes；
4. JVM/build/lint continuously；
5. Windows Emulator connected closeout；
6. docs/README/TODO/CHANGELOG/architecture/privacy/design update；
7. accepted milestone 本地生成 signed internal APK；
8. 创建并 push version tag；
9. 等待 GitHub Actions version gate PASS；
10. 创建 GitHub prerelease，上传 APK + SHA-256 + notes；
11. 无 hard blocker 则继续下一版本。

Codex 可以在已授权 master 下创建内部 task/result 并连续推进，无需每阶段等用户。

Hard stop / human approval：

- new paid API/provider account or secret；
- recurring-cost backend/service deployment；
- external cloud upload of seminar content；
- destructive migration/data-loss risk；
- production signing/Play Console/public release；
- physical-device write/install/instrumentation unless separately authorized；
- major product-scope choice not resolved by roadmap。

## User installation target

从 `0.3.1-internal` 起：

1. 打开 SeminarArc 仓库的 `Releases` 页面；
2. 打开最新 internal/prerelease；
3. 直接下载 `.apk` asset（不是 Actions artifact，不应需要解 ZIP）；
4. Android 打开 APK；
5. 只给当前浏览器/文件管理器“安装未知应用”权限；
6. 安装 `SeminarArc Internal`；
7. 后续同 applicationId + 同 signing key 的 Release APK 可以直接覆盖升级并保留数据。

production Play build 使用独立 release identity/signing 计划；internal dogfood 不自动成为 production signing contract。
