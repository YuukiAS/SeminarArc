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
- `0.9.x` release readiness audit：`AUDIT_COMPLETE_NOT_READY_FOR_PRODUCTION_RELEASE`

这意味着当前版本适合 **个人 dogfood / 内测**，但还不是 Google Play production release。

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

## 你现在应该怎么验收

当前最有价值的下一步不是继续加功能，而是 **在你自己的 Android 手机上做 personal dogfood**。

建议先做一次 15–30 分钟的低风险 smoke seminar：

1. 从 Releases 下载并安装 `SeminarArc-0.5.0-alpha.2.apk`。
2. 新建一个测试 seminar，填标题/abstract。
3. 开始现场流程：授权麦克风/通知/相机，录音 2–5 分钟，拍几张测试幻灯片。
4. 添加一个 MARK、QUESTION、NOTE，然后 End Seminar。
5. 完全退出 App 后重新打开，确认 seminar、录音、照片和 timeline 仍在。
6. 从 timeline 播放录音，检查 clip / fallback。
7. 进入 Reconstruction：跑一次英文或中文 OCR，编辑文字，标记 key slide。
8. 用公开的论文标题/DOI 测试一次 Reference Candidate lookup，并 Confirm 一个候选。
9. 编辑 Seminar Brief。
10. 选一张测试幻灯片，画一个公式区域并手动填一段 LaTeX。
11. 导出 Markdown / ZIP / BibTeX / RIS，检查文件能正常打开。
12. 删除这个**测试 seminar**，确认 app 内对应记录不再出现。

如果这轮基本稳定，再拿它参加一次低风险的真实 seminar。真实硬件上的麦克风、CameraX、锁屏/后台、文件选择器和厂商 ROM 行为，才是 Emulator 无法替代的下一批证据。

发现问题时，优先记录：

- 操作步骤；
- 实际结果 vs 预期结果；
- 是否可稳定复现；
- Android 版本/机型；
- 是否涉及录音、相机、后台/锁屏、导出或联网 lookup。

不要在 bug report 中上传真实 seminar 的敏感录音、照片或完整 OCR 文本。

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

## Production boundary

当前 `0.9.x` audit 结论仍然是：

```text
AUDIT_COMPLETE_NOT_READY_FOR_PRODUCTION_RELEASE
```

在真正进入 Google Play / production release 前，还需要单独处理：production signing ownership、AAB、Play Console、Data safety、公开隐私政策、权限/FGS/background policy、真机 release acceptance，以及是否需要 crash/performance telemetry。
