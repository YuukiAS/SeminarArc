# Privacy

SeminarArc 默认把 seminar 材料保存在设备本地，不要求登录，也不会自动上传录音、PDF 或其他素材。

## 本地优先行为

- Seminar metadata 存储在本地 Room database。
- Imported PDFs 会复制到 app-private storage。
- 录音文件会写入 app-private seminar-owned storage，例如 `files/seminars/<seminar-id>/recordings/`。
- 数据库只保存 app-private storage 的相对路径，便于后续内部迁移。

## 麦克风与 foreground recording

- 麦克风只用于用户明确从可见应用界面启动的 seminar recording。
- `RECORD_AUDIO` 是开始真实 microphone recording 的硬前提；未授权时不会启动 recorder，也不会创建“正在录音”的 Room 状态。
- 录音通过 foreground service 持续运行，并显示 ongoing notification。
- `POST_NOTIFICATIONS` 被拒绝时，SeminarArc 不会把它当作 recorder failure；Android 仍要求 foreground service 创建 notification，系统如何展示由平台权限状态决定。

## 删除行为

- 删除 seminar 会删除该 seminar 的 Room 记录和 app-owned local media directory。
- 这包括该 seminar 下的 abstract PDF、recordings，以及后续阶段加入的 photos/clips。
- 一个录音失败不会删除同一 seminar 的其他 assets。

## 用户责任

用户应在录音前确认所在地法律、机构政策和现场活动规则允许录音。SeminarArc 的权限提示不替代用户获得必要许可。

## 当前不包含

- Cloud sync。
- Account login。
- Remote upload。
- AI transcription 或 summaries。
- Notion 或第三方 provider 上传。
