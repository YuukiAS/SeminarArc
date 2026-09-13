# SeminarArc

SeminarArc 是一个 **local-first 的原生 Android 学术 seminar 工作流应用**。它把会前准备、现场录音/拍幻灯片、时间线回顾、OCR、参考文献确认、Seminar Brief、公式整理和科研导出放在同一个 seminar-scoped 工作流中。

```text
Prepare -> Capture -> Reconstruct -> Research -> Export
```

## 当前状态

当前可安装版本：**`v0.5.0-alpha.2`**

- `0.1.x` Local Capture：COMPLETE
- `0.2.x` Local Visual Reconstruction：COMPLETE
- `0.3.x` Reference Candidate + Seminar Brief：COMPLETE
- `0.4.x` Transcription / Summary / Notion-ready local-safe foundation：COMPLETE
- `0.5.x` Formula / Research Export local-safe alpha：COMPLETE
- 当前 Room schema：**v7**
- 当前主动工程阶段：**0.5.x automated black-box QA hardening**
- `0.9.x` release readiness audit：`AUDIT_COMPLETE_NOT_READY_FOR_PRODUCTION_RELEASE`

这意味着当前版本适合 **个人 dogfood / 内测**，但还不是 Google Play production release。当前优先级不是继续扩展 provider 或新产品功能，而是把大部分“真实用户路径是否能走通”的验收自动化，减少用户逐页逐按钮手工测试。

## 下载与安装

最新 prerelease：

[SeminarArc v0.5.0-alpha.2](https://github.com/YuukiAS/SeminarArc/releases/tag/v0.5.0-alpha.2)

下载：

- `SeminarArc-0.5.0-alpha.2.apk`
- 可选：`SeminarArc-0.5.0-alpha.2.apk.sha256`

当前 APK：

- application id：`com.yuukias.seminararc.internal`
- versionName：`0.5.0-alpha.2`
- versionCode：`50002`
- APK 大小：约 **60.6 MB**
- SHA-256：

```text
5E382F3237A346B60B924716A9D53C3001D467C41517C85A13319117724E49FF
```

在 Android 上打开 APK，并仅给当前浏览器或文件管理器授予“安装未知应用”权限即可侧载。

更完整的安装、升级、签名和数据保留说明见：

[`docs/INTERNAL_DISTRIBUTION.md`](docs/INTERNAL_DISTRIBUTION.md)

> `SeminarArc Internal` 使用独立 internal application id 和 repo 外稳定 signer。后续使用相同 application id、相同 signer、且更高 versionCode 的 APK 可以原地升级并保留 app data。

## 当前能做什么

### 1. Capture

- 创建和管理 seminar。
- 导入/替换/移除 abstract PDF。
- 前台录音并保存本地 `.m4a`。
- CameraX 连续拍摄幻灯片。
- 记录 MARK / PHOTO / QUESTION / NOTE 时间线事件。
- 从时间线回看现场内容并从对应 offset 播放录音。
- 从 MARK 生成本地音频 clip，并提供 retry / full-recording fallback。

### 2. Reconstruct

- 本地图像旋转、裁边、透视矫正和可读性增强；原图始终保留。
- 可从 Reconstruction workspace 通过 Android system picker 导入已有 slide/photo；导入文件会复制到 app-private seminar media storage，并登记为 `PHOTO_ORIGINAL`，不伪造 timeline event 或 recording offset。
- bundled ML Kit Latin / Chinese OCR。
- OCR 文本编辑、搜索、过滤。
- key slide / tags。
- OCR 与图像增强使用 durable WorkManager queue，支持 retry、cancel、idempotency 和恢复。

### 3. Research

- 用户显式选择 OCR / note / DOI / bibliographic clue 后再发起 reference lookup。
- Crossref primary、OpenAlex fallback/cross-confirmation、DataCite targeted fallback。
- `reference-match-v1` deterministic matching / dedup；UI 显示 confidence band 和 match reasons，不把 heuristic score 冒充概率。
- Reference Candidate Review：Confirm / Reject / Reopen。
- 可编辑 Seminar Brief，并关联 confirmed references 和 key slides。

### 4. Transcript / Summary local-safe foundation

- timestamped transcript / segment 数据模型与本地 review UI。
- manual transcript import，并可编辑 segment。
- durable `TRANSCRIPTION` / `SUMMARY_DRAFT` processing job boundary。
- editable summary draft，可由用户显式应用到 Seminar Brief。
- Notion-ready Markdown 预览、保存和 share entry。

当前 **没有接入真实 ASR provider、在线 AI summary provider 或 Notion OAuth/upload**；默认 unavailable provider 不上传录音、transcript 或 brief，也不会伪造结果。

### 5. Formula / Research Export

- 在幻灯片照片上 drag-to-draft 公式区域。
- 已保存公式区域可重新编辑 crop / label。
- local manual LaTeX durable queue。
- READY formula results 可进入 Markdown / Notion-ready export。
- confirmed references 可确定性导出：
  - `references.bib`
  - `references.ris`
- Seminar Markdown / ZIP export，包括 brief、confirmed references、key slides 和支持的本地研究材料。

当前 **没有接入 live Mathpix、PaddleOCR/pix2tex 或其他 cloud formula OCR**。

## Alpha 验证状态

`v0.5.0-alpha.2` 已通过：

- Windows local Gradle build / unit / lint gate。
- Windows Pixel 8 API 36 Emulator 显式 instrumentation：**24 tests PASS**。
- Room migration / current schema regression。
- OCR、图像增强、processing queue、Reconstruction、Reference Review、Formula UI 等 connected regression。
- internal APK install / launch smoke。
- update-in-place smoke。
- package / version / signer continuity inspection。
- release-relevant secret scan。
- GitHub Actions version gate：PASS。
- GitHub prerelease APK 远端 SHA-256 与本地一致。

Windows ADB 即使同时能看到受保护真机，所有非 inventory ADB 操作也只允许显式 targeting Emulator；远程真机不承担日常 connected/instrumentation 测试。

## 当前 QA 阶段：先自动验收，再让用户做少量真机验收

当前主动阶段是 **0.5.x automated black-box QA hardening**。目标是把已安装的 `SeminarArc Internal` 当成真实用户看到的黑盒 App，通过 Windows Emulator 自动完成 CRUD、navigation、persistence、session lifecycle、cleanup，以及后续 Reconstruction / Reference / Transcript / Formula / export 用户流。

长期计划：[`docs/plans/0.5.x-blackbox-acceptance-plan.md`](docs/plans/0.5.x-blackbox-acceptance-plan.md)

当前第一张执行任务：[`prompts/tasks/0.5.x_blackbox_acceptance_harness_task.md`](prompts/tasks/0.5.x_blackbox_acceptance_harness_task.md)

第一阶段先建立 `scripts/run-blackbox-acceptance.ps1`，并自动覆盖：

- B01：fresh install / empty library；
- B02：Seminar CRUD / force-stop 后 persistence；
- B03：Emulator-safe session lifecycle；
- B08：recovery / synthetic seminar cleanup。

当前 deterministic research-flow closeout 已通过 imported-photo path 自动覆盖：

- B04：Reconstruction import、enhancement、OCR、OCR edit、key slide 和 force-stop 后 persistence；
- B07：formula drag selection、crop/label edit、manual LaTeX READY result、force-stop 后 persistence 和本地 Markdown export 文件验证。

CameraX 在 Windows Emulator 上的 deterministic camera fixture 仍单独记录为 Emulator/hardware acquisition limitation；它没有被改写为 CameraX PASS。真实相机质量、方向和速度仍保留给后续真实硬件 acceptance。

后续再依次扩展：

1. real-world media corpus robustness；
2. screenshot visual regression（小屏、大字体、长文本、error/empty/loading）；
3. fixed-seed Monkey / multi-device；
4. major alpha / `0.9.x` 再评估 Firebase Test Lab Robo / 小型真实设备矩阵。

每个自动 scenario 应输出 PASS/FAIL、screenshot、UI tree、scoped logcat 和耗时证据。普通 navigation、layout、accessibility semantics、persistence、cleanup bug 由 Codex 自动修复并复测，不要求用户逐项证明按钮是否工作。

### 最终仍需要用户亲自验证的内容

自动 QA 稳定后，人工验收应压缩到以下真实硬件/主观项目：

- 真手机麦克风录音质量；
- CameraX 拍真实投影/PPT 的质量、方向和速度；
- 锁屏/后台/厂商 ROM 对长时间录音的影响；
- 一次低风险真实 seminar 的连续操作手感；
- 主观 UI/UX 判断。

因此当前 **不要求用户手工把所有页面和按钮逐项点完**。现有 APK 可以安装留在手机上，但大范围 dogfood 应等第一轮 black-box harness 把基础路径跑过后再做。

发现真实硬件问题时，优先记录：操作步骤、实际结果 vs 预期、是否可复现、Android 版本/机型，以及是否涉及录音、相机、后台/锁屏、导出或联网 lookup。不要在 bug report 中上传真实 seminar 的敏感录音、照片或完整 OCR 文本。

## 当前明确未完成

- 真实 ASR provider。
- 在线 AI summary provider runtime。
- Notion live OAuth / upload。
- live formula OCR provider。
- cloud sync / account system。
- Google Play production signing / AAB / Data safety / rollout。
- 广告、订阅、支付。
- 系统化真机长时录音、锁屏/后台、弱网/低存储和多厂商 ROM release acceptance。

这些不会为了“把 roadmap 打勾”而以假按钮、硬编码 token 或不安全上传方式实现。

## 开发与文档入口

- 产品级路线：[`TODO.md`](TODO.md)
- 当前 QA hardening plan：[`docs/plans/0.5.x-blackbox-acceptance-plan.md`](docs/plans/0.5.x-blackbox-acceptance-plan.md)
- 架构：[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- 隐私：[`docs/PRIVACY.md`](docs/PRIVACY.md)
- 设备测试策略：[`docs/DEVICE_TESTING.md`](docs/DEVICE_TESTING.md)
- CI 策略：[`docs/CI_POLICY.md`](docs/CI_POLICY.md)
- Internal APK 分发：[`docs/INTERNAL_DISTRIBUTION.md`](docs/INTERNAL_DISTRIBUTION.md)
- 0.4.x plan：[`docs/plans/0.4.x-transcription-summary-notion-plan.md`](docs/plans/0.4.x-transcription-summary-notion-plan.md)
- 0.5.x plan：[`docs/plans/0.5.x-formula-research-export-plan.md`](docs/plans/0.5.x-formula-research-export-plan.md)
- Post-0.3 autonomous roadmap：[`docs/plans/post-0.3-autonomous-roadmap.md`](docs/plans/post-0.3-autonomous-roadmap.md)
- 0.9.x readiness audit：[`prompts/tasks/0.9.x_release_readiness_audit_result.md`](prompts/tasks/0.9.x_release_readiness_audit_result.md)

## CI / Release 约定

普通开发和文档 push 不自动跑 GitHub Actions。

GitHub Actions 仅在：

- `v*` 版本 tag；或
- 手动 `workflow_dispatch`

时作为版本级远端验收运行。

Internal APK 在受控 Windows 本地环境 build/sign，随后通过 GitHub Release 发布；GitHub Actions 不是 APK 分发渠道。

Black-box QA / docs / harness 的普通提交不会为了留痕创建无意义版本 tag。只有修复了值得用户重新安装验证的 P0/P1 或明显 dogfood blocker，才准备新的 `0.5.1-alpha.*` Release。

## Production boundary

当前 `0.9.x` audit 结论仍然是：

```text
AUDIT_COMPLETE_NOT_READY_FOR_PRODUCTION_RELEASE
```

在真正进入 Google Play / production release 前，还需要单独处理：production signing ownership、AAB、Play Console、Data safety、公开隐私政策、权限/FGS/background policy、真机 release acceptance，以及是否需要 crash/performance telemetry。
