---
id: post_0.3_autonomous_roadmap_master
status: ready
owner: codex
created: 2026-09-08
allow_code_change: true
allow_shell_command: true
allow_network: true
allow_external_upload: false
requires_human_approval: false
---

# SeminarArc Post-0.3 Autonomous Roadmap Master

## Objective

在用户无法频繁参与接力时，按仓库 roadmap 尽可能自动推进 SeminarArc，从已完成的 `0.3.x` 开始，优先完成 internal dogfood 分发，然后依次推进 `0.4.x` 与 `0.5.x` 的 readiness -> implementation -> Emulator closeout。

不要自动进入 Google Play/public release。`0.9.x` 只允许准备 readiness/audit，不允许执行生产签名、Play Console、公开发布或 ads/payment。

## Source of truth

- GitHub `origin/main` 是唯一源码事实来源。
- 开始时必须同步最新 `main`。
- 读取并遵守：
  - `AGENTS.md`
  - `prompts/AGENT_RULES.md`
  - `prompts/CHATGPT_RULES.md`
  - `docs/plans/post-0.3-autonomous-roadmap.md`
  - `README.md`
  - `TODO.md`
  - `CHANGELOG.md`
  - `docs/ARCHITECTURE.md`
  - `docs/PRIVACY.md`
  - `docs/DEVICE_TESTING.md`
  - 当前 design/plans/task/results

## Execution environment

需要 connected/instrumentation/packaging 时优先使用 Windows native checkout：

`D:\Code\SeminarArc-emulator`

Windows SDK：

`D:\Android\Sdk`

已验证 JDK 17：

`D:\Code\_jdks\jdk-17.0.20.1+1`

Windows Emulator 是默认 connected target。GM1910 不参与日常自动开发/CI。

如果当前运行环境是 WSL 且某个 Windows-only gate 暂时无法执行：

- 继续所有 headless/research/docs/code-safe 工作；
- 把 Windows connected/packaging gate留到下一次 Windows native阶段；
- 不得通过修改 usbipd、重启 WSL 或触碰 protected physical device 来绕过。

## Phase A — 0.3.1 Internal Dogfood

先执行现有 task：

`prompts/tasks/0.3.1_internal_dogfood_distribution_task.md`

目标是让用户可以从 GitHub 下载并侧载 SeminarArc Internal APK。

如果稳定 internal signing secrets 尚未提供：

- 完成所有可自动完成的 build variant/workflow/docs/artifact工作；
- 明确一项一次性 human signing-secret action；
- 不把 secret 缺失变成后续 `0.4.x`/`0.5.x` 开发 blocker。

## Phase B — 0.4.x Readiness

在 `0.3.1` 可自动部分完成后，创建并执行一个明确的 `0.4.x` readiness task/result/plan。

必须先研究，不能直接照 TODO 粗略描述写 production code。

至少覆盖：

1. transcription options：
   - on-device/local/open-source/self-hosted/cloud；
   - Android可行性、语言、timestamp、资源需求；
   - license/commercial use；
   - cost/privacy；
   - cancel/retry/idempotency/offline；
2. Room migration from current v4：transcript segment / provider job / provenance；
3. transcript segment 与 recording offset/photo/timeline window 的关系；
4. SummaryProvider boundary：输入选择、draft/provenance/edit semantics、禁止覆盖用户 Brief；
5. Notion：Markdown-first、official API/OAuth、file upload、credential/backend边界；
6. app-owned secret/backend requirements；
7. privacy/network contract；
8. UI/design flow；
9. test/Emulator strategy；
10. explicit scope and out-of-scope。

使用会变化的 provider/API/license事实时必须联网查官方文档。

Readiness result 必须是 `READINESS_PASS` 或 `READINESS_BLOCKED`。

如果只有单个 provider/account 子路径 blocked，但 provider-independent/local-safe core 可以继续，则不要把整个 0.4.x 标记 blocked；应收缩实现 scope 并继续。

## Phase C — 0.4.x Production

如果 readiness gate PASS 且没有真正需要用户产品决策的冲突，自动创建 production master 并连续执行到 `0.4.x COMPLETE`。

原则：

- provider-independent domain first；
- fake/provider contract tests before live integration；
- local/self-hosted path优先于把 secret 塞 APK；
- external cloud processing必须 opt-in；
- Summary只产生 editable draft；
- Notion若需要公共 OAuth/backend而当前不存在安全架构，可保留 Markdown-friendly export/provider boundary，并将真实 public integration延后；
- 不为完成 roadmap 而嵌入私人 token/key；
- 不上传用户 seminar 内容做 live smoke；
- Windows Emulator closeout + 0.1/0.2/0.3 regression；
- accepted milestone 后生成 internal APK artifact（若分发链可用）。

## Phase D — 0.5.x Readiness

完成 0.4.x 后自动进入 `0.5.x` readiness。

至少研究：

- formula region selection UX；
- Mathpix terms/cost/commercial/privacy；
- licensable local/open/self-hosted formula OCR alternatives；
- formula provider boundary；
- LaTeX correction/provenance/confidence；
- confirmed references -> BibTeX/RIS deterministic export；
- Room/data model changes；
- privacy/secrets/backend；
- Emulator testing；
- explicit out-of-scope（Zotero account sync、paywalled scraping等）。

Paid API credential/account remains human-approval boundary. A blocked Mathpix account must not block local region-selection, provider contracts, BibTeX/RIS, or other safe work.

## Phase E — 0.5.x Production

If readiness passes, create and execute production master to `0.5.x COMPLETE` within the approved safe scope.

Closeout must include:

- JVM/build/lint；
- Windows Emulator connected regression；
- old Room migrations reopen current；
- 0.1/0.2/0.3/0.4 regression；
- export golden tests；
- secret scan；
- docs/design/privacy/roadmap sync；
- internal/pre-release APK artifact if signing/distribution channel is available。

## Phase F — Stop before external release

After 0.5.x completion, create a `0.9.x_release_readiness_report` only.

Do not automatically:

- create/change production signing key；
- manipulate Google Play Console；
- publish privacy policy externally；
- submit Data safety；
- upload AAB to a public/testing track；
- integrate ads/payment；
- make public release。

End master by reporting what one-time user approvals/accounts/secrets are needed for 0.9.x.

## APK artifact policy

Starting at 0.3.1:

- each accepted internal milestone should produce a downloadable APK artifact when the signing/distribution setup permits；
- artifact generation must be gated by build/tests/lint/secret scan appropriate to that stage；
- transient live provider API failures must not prevent local APK production if code/tests are otherwise valid；
- stable signer/application id is required for update-in-place；
- never commit keystore/private key/password。

## Autonomous behavior

Within this master, Codex is explicitly authorized to create the needed subtask/result files for each roadmap phase and continue to the next phase without waiting for the user, provided no hard-stop condition occurs.

For each macro stage:

research/readiness -> plan -> implementation -> tests -> Emulator closeout -> docs -> result -> commit -> push main -> continue.

Do not stop for ordinary:

- compile failure；
- unit/instrumentation failure；
- lint failure；
- parser/provider mock bug；
- Emulator UI bug；
- documentation drift；
- temporary external provider failure when fixtures/fakes can verify contracts。

Fix, retest, document and continue.

## Hard stops / human approval

Stop only the affected path, or the entire master if unavoidable, when one of these occurs:

- new paid provider/account/subscription is required for the only viable implementation；
- app-owned backend or recurring cloud cost must be deployed；
- new private API secret/credential must be provisioned；
- destructive migration or credible user-data loss risk cannot be safely resolved；
- production signing/Google Play/public release action；
- physical-device write/install/instrumentation is the only remaining validation and has not been authorized；
- major product scope/UX choice cannot be inferred from roadmap/design；
- provider terms materially conflict with intended commercial/privacy model。

When a subpath is blocked but the rest is safe, continue the rest and report the blocked subpath rather than stalling the whole roadmap.

## Final output

Create:

`prompts/tasks/post_0.3_autonomous_roadmap_master_result.md`

Summarize:

- completed version lines；
- current Room version；
- APK distribution status and download path；
- stable signing status；
- 0.4.x decisions/implementation；
- 0.5.x decisions/implementation；
- regression/test status；
- known deferred hardware/provider work；
- exact human approvals needed before 0.9.x；
- final commit SHA。
