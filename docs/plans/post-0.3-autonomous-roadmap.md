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

## Milestone A — 0.3.1-internal Dogfood Distribution

Do this before starting large `0.4.x` production work.

Goal: produce an installable internal APK that the user can download from GitHub and sideload on a personal Android phone without rebuilding locally.

Required work:

- Correct stale app version metadata and define a repeatable versioning convention.
- Add an `internal` distribution variant/build type that does not collide with the eventual production package when practical.
- Add GitHub Actions packaging and artifact upload.
- Run unit/build/lint and Emulator regression before publishing an internal artifact.
- Add APK secret scan/basic package inspection.
- Add `docs/INTERNAL_DISTRIBUTION.md` with download/install/update/uninstall/data-retention behavior.
- Do not commit keystore/private key/password.

### Signing

For disposable one-off smoke, an ordinary debug APK can be sideloaded, but ephemeral CI debug signing is not a durable update channel.

For repeatable dogfood updates, use a stable internal signing key stored outside the repository and injected through GitHub Actions secrets. Prefer a distinct internal application id (for example an `.internal` suffix) so later Play production signing does not force a destructive transition.

Preparing the workflow/configuration is automatic. Provisioning a new private signing key or changing GitHub secrets is a human-approval boundary unless the user explicitly authorizes a trusted local Codex session to do it without exposing the secret.

### Distribution stages

- `0.3.1-internal`: first downloadable dogfood APK.
- `0.4.x-internal`: continued APK artifacts for dogfood after each accepted milestone.
- `0.5.x-alpha`: optionally start GitHub prerelease APKs once formula/research export line is stable.
- `0.9.x`: switch primary tester distribution to signed AAB / Google Play internal testing; APK may remain a diagnostic side artifact.

## Milestone B — 0.4.x Advanced Processing

Do not treat the existing broad TODO text as an implementation contract. Start with a readiness gate.

### Readiness topics

- Transcription provider architecture: local/open-source/self-hosted/cloud options, Android feasibility, timestamp quality, language support, cost, license, cancellation/retry and privacy.
- Transcript storage model and Room migration from v4.
- Mapping transcript segments to recording offsets and nearby slide/timeline events.
- SummaryProvider boundary: user-selected inputs only, draft semantics, provenance and editing; no overwrite of manual Brief.
- Notion integration: Markdown-first export path, official API/OAuth feasibility, attachment/file limits, credential storage and whether public OAuth/backend is required.
- Secrets/backend decision: identify anything that cannot safely ship in the APK.

### Production target after readiness

If readiness passes without unresolved product/security conflict:

1. transcript schema/provider boundary;
2. at least one approved transcription path;
3. timestamped transcript review linked to timeline/photo windows;
4. cancellable/retryable processing;
5. SummaryProvider as editable draft only;
6. Notion export/integration only to the extent credentials/privacy architecture is safe;
7. Markdown/ZIP compatibility;
8. Windows Emulator closeout and internal APK artifact.

If a provider requires app-owned secret/backend and no approved secure path exists, implement provider-independent contracts/fakes and continue other local-safe work; stop only the blocked provider subpath.

## Milestone C — 0.5.x Formula + Research Export

Start with another readiness/license gate.

Targets:

- formula region selection;
- formula OCR provider boundary;
- evaluate Mathpix and licensable open/self-hosted alternatives;
- LaTeX correction/review with confidence/provenance;
- BibTeX/RIS export from confirmed references;
- research-oriented export polish;
- no automatic paywalled PDF retrieval;
- Windows Emulator regression and internal/pre-release APK.

Paid API keys and commercial provider credentials are human-approval boundaries and must never be committed to the repository.

## Milestone D — 0.9.x Release Preparation

Do not auto-enter external/public release merely because `0.5.x` completes.

The autonomous development loop may prepare an audit/readiness report, but the following require explicit user approval:

- production signing key creation/change;
- Google Play Console account/actions;
- privacy-policy publication;
- Data safety submission;
- release-track rollout;
- ads/payment SDK integration;
- public release.

Before `0.9.x` release work, require:

- versioning/signing audit;
- release APK/AAB secret scan;
- backup/upgrade/migration tests from installed internal versions;
- no-network/weak-network/low-storage/long-session tests;
- hardware checkpoint plan;
- permission/FGS/background-policy review;
- privacy and third-party SDK inventory.

## Autonomous execution policy

At each version line:

1. research/readiness task;
2. ChatGPT/Codex-readable plan/result;
3. production master if gate passes;
4. JVM/build/lint continuously;
5. Windows Emulator connected closeout;
6. docs/README/TODO/CHANGELOG/architecture/privacy/design update;
7. internal APK artifact after accepted milestones;
8. proceed to the next planned version if no hard blocker.

Codex may create internal task/result files under an already-authorized master task and continue between phases without waiting for the user.

Hard stop / human approval conditions:

- new paid API/provider account or secret;
- backend/service deployment that creates recurring cost;
- external cloud upload of seminar content;
- destructive migration/data-loss risk;
- production signing/Play Console/public release;
- physical-device write/install/instrumentation unless separately authorized;
- major product-scope choice not resolved by the roadmap.

## User installation target

Once `0.3.1-internal` distribution is complete, the expected user flow is:

1. Open the SeminarArc GitHub Actions run (or later prerelease page).
2. Download the named SeminarArc internal APK artifact.
3. Extract the artifact ZIP if GitHub supplies a ZIP wrapper.
4. On Android, open the APK and allow the browser/files app to install unknown apps when prompted.
5. Install SeminarArc Internal.
6. Future internal builds signed with the same internal key and application id should update in place and retain app data.

The production Play build should use a separate release identity/signing plan; internal dogfood must not silently become the production signing contract.
