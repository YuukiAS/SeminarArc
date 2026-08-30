# Privacy

SeminarArc 默认把 seminar 材料保存在设备本地，不要求登录，也不会自动上传录音、PDF 或其他素材。

## 本地优先行为

- Seminar metadata 存储在本地 Room database。
- Imported PDFs 会复制到 app-private storage。
- 录音文件会写入 app-private seminar-owned storage，例如 `files/seminars/<seminar-id>/recordings/`。
- 幻灯片照片和本地生成的 clips 会写入同一 seminar-owned app-private storage。
- 数据库只保存 app-private storage 的相对路径，便于后续内部迁移。

## 麦克风与 foreground recording

- 麦克风只用于用户明确从可见应用界面启动的 seminar recording。
- `RECORD_AUDIO` 是开始真实 microphone recording 的硬前提；未授权时不会启动 recorder，也不会创建“正在录音”的 Room 状态。
- 录音通过 foreground service 持续运行，并显示 ongoing notification。
- `POST_NOTIFICATIONS` 被拒绝时，SeminarArc 不会把它当作 recorder failure；Android 仍要求 foreground service 创建 notification，系统如何展示由平台权限状态决定。
- 点击 recording notification 会返回对应 seminar 的 Active Session 页面，不会创建新的 seminar session。
- 如果进程重启或 service 异常销毁导致录音中断，SeminarArc 会保留已有 app-private recording 文件和 Room 记录，并把不可继续的 recording row 标记为 `FAILED`，不会自动上传或删除其他 seminar assets。
- 用户点击 `End Seminar` 后，应用会先停止并 finalize 本地 recorder，再把 seminar 标记为 `COMPLETED` 并写入 `sessionEndedAt`。

## 删除行为

- 删除 seminar 会删除该 seminar 的 Room 记录和 app-owned local media directory。
- 这包括该 seminar 下的 abstract PDF、recordings、photos 和 clips。
- 一个录音失败不会删除同一 seminar 的其他 assets。

## 本地导出

- Markdown/ZIP 导出由用户显式触发，可保存到用户通过 Android system picker 选择的位置，也可交给 Android share sheet。
- ZIP 内只包含当前可读的 app-owned abstract/photos/ready clips；缺失媒体会记录为 skipped media，不会自动上传或补传。
- 导出文件是外部副本。删除 SeminarArc 内的 seminar 只清理 app-owned Room 记录和 app-private media，不会删除用户另存或分享出去的 Markdown/ZIP 副本。
- SeminarArc 不会默认把导出内容发送到云端或第三方服务；真正发送到哪里取决于用户在系统 share sheet 中选择的目标应用。

## 本地 OCR 与图像增强

- `0.2.x` 已接入 bundled ML Kit Text Recognition v2 作为本地普通 OCR provider。SeminarArc 不会把 seminar 照片或 OCR 文本上传给 Google servers。
- ML Kit SDK 仍可能向 Google 发送 diagnostics / usage metrics，例如 device/app information、performance metrics、API configuration、feature input/output size、event type 和 error codes。正式发布前需要在 Google Play Data safety 与隐私政策中披露。
- 图像 rotate、crop、perspective correction 和 basic readability enhancement 已使用 Android 本地 Bitmap/Matrix/Canvas/ColorMatrix 管线实现基础 provider，输出为 app-private derived asset。
- 原始照片始终保留；增强失败、OCR 失败、取消或重试都不得删除原图。

## Provider 与云上传边界

- `0.2.x` 不实现 cloud OCR、transcription、AI summary、reference lookup 或 Notion upload。
- 未来任何 cloud provider 都必须由用户主动触发，并在上传前显示 provider、资产范围、费用/配额和删除语义。
- 应用自有 commercial API secret 不得嵌入 APK。

## 用户责任

用户应在录音前确认所在地法律、机构政策和现场活动规则允许录音。SeminarArc 的权限提示不替代用户获得必要许可。

## 当前不包含

- Cloud sync。
- Account login。
- Remote upload。
- AI transcription 或 summaries。
- Notion 或第三方 provider 上传。
- Cloud OCR、formula OCR、transcription、AI summary、reference lookup。
