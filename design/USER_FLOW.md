# User Flow

## 1. 创建前

1. 用户进入 `Seminar List`
2. 用户点击 `Create Seminar`
3. 用户填写标题、speaker、时间、地点、abstract 文本
4. 用户可选导入 abstract PDF
5. 用户保存为 `Draft`
6. 用户从列表或详情重新打开该 seminar

## 2. 现场开始

1. 用户在详情页点击 `Start / Resume`
2. 若无权限，进入对应权限解释状态
3. 录音启动后进入 `Live Capture`
4. 顶部持续显示 seminar 身份，避免归属混淆

## 3. 现场捕获

1. 用户点击 `Mark Moment`
2. 系统立即创建 `MARK` 事件与待生成 clip
3. 用户点击 `Capture Slide`
4. 照片保存并以 recording offset 进入时间线
5. 用户点击 `Add Question`
6. 问题与 offset 写入当前 seminar
7. 用户点击 `Quick Note`
8. 备注与 offset 写入当前 seminar

## 4. 现场中断与恢复

1. 若录音暂停，进入 `Paused` 状态
2. 页面保留当前 seminar 身份与次操作
3. `Resume Recording` 成为唯一主操作
4. 若麦克风权限被拒绝，用户可去设置修复，或返回浏览现有 seminar 内容

## 5. 结束 seminar

1. 用户点击 `End Seminar`
2. 系统展示删除外的最严肃确认语义
3. 录音结束并持久化
4. 用户进入 `Seminar Timeline`

## 6. 结束后回顾

1. 用户在时间线按时间查看照片、MARK、QUESTION、NOTE
2. 用户可从任一事件 `Play from here`
3. 若 clip 已完成，直接播放 clip
4. 若 clip 处理中或失败，清晰提示并回退到完整录音对应时间点
5. 用户之后可从详情页再次回到时间线

## 7. 会后视觉重建

1. 用户打开 `COMPLETED` seminar detail
2. 用户点击 `Research Reconstruction`
3. 系统显示本 seminar 的照片列表、key slide filter、OCR status filter 和搜索入口
4. 用户选择一张照片
5. 用户可在原图和增强图之间切换；尚无增强图时可执行 rotate、crop、perspective 或 readability enhancement
6. 系统将增强图保存为 derived asset，不覆盖原图
7. 用户对当前照片或 key slides 运行本地 OCR
8. OCR job 显示 `PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED`
9. 用户查看并编辑 OCR 文本；人工编辑后显示 edited state
10. 用户为照片添加标签，或标记为 key slide
11. 用户搜索 OCR 文本，结果限定在当前 seminar
12. 用户可回到 Timeline 对应 offset，或继续导出 0.1.x Markdown/ZIP

0.2.x 不出现真实 reference lookup、transcription、AI summary、Notion 或 cloud upload 流程。

## 8. 论文候选与 Seminar Brief

1. 用户在 `Research Reconstruction` 中选择 `REFERENCE` / `KEY_SLIDE` 照片、OCR edited text、question/note 或手工 DOI/title/author/year clue
2. 用户点击 `Find references`
3. 系统展示 query preview，明确 provider、将发送字段和不会发送的内容
4. 用户确认 lookup
5. 系统优先执行 exact DOI lookup；没有 DOI 时使用 title/author/year bibliographic query
6. 系统按 normalized DOI 或 normalized title/year/author overlap 合并候选
7. 用户查看 candidate list，每个 candidate 显示 match reason 和来源 provider
8. 用户打开 candidate detail，核对 OCR evidence 与 provider metadata
9. 用户将候选标记为 `CONFIRMED` 或 `REJECTED`
10. confirmed references 出现在 `Seminar Brief` 的 reference section
11. 用户编辑 Brief 的 background、core question、methods、main results、takeaways、unresolved questions、follow-up actions 和 user notes
12. 用户从 Brief 进入现有 Markdown/ZIP export，导出 confirmed references、key slides 和手工编辑内容

0.3.x 第一版不自动确认参考文献，不自动下载 PDF，不生成 AI brief，不接入 transcription、Notion、formula OCR 或 cloud upload。

## 9. 异常与边界

### 首页空状态

1. 首次进入没有 seminar
2. 页面解释产品价值
3. 用户从主按钮开始创建第一场 seminar

### 没有 abstract

1. seminar 已存在，但没有 PDF
2. 详情页明确说明“无 abstract 仍是有效 seminar”
3. 用户可稍后补充 PDF

### 文件缺失

1. 用户打开附件或媒体失败
2. 页面保持 seminar 其他上下文可见
3. 用户选择重试、定位文件或从完整录音回放

### 删除 seminar

1. 用户在详情页选择删除
2. 确认页必须再次点名 seminar 标题
3. 明确列出将被删除的资产种类
4. 用户完成二次确认后才真正删除

### OCR 或增强失败

1. 任务失败后保留原图和已有 OCR 文本
2. 页面显示 readable error 和 retry
3. 用户取消任务时不删除原图或已确认标签
4. 重复点击同一照片的 OCR 不产生无限重复 job

### Reference lookup 失败

1. 离线时保留 selected evidence，不发请求，允许稍后重试
2. provider rate limit 时显示 provider、backoff 状态和本地 Reconstruction 仍可继续
3. 没有候选时允许用户修改 query 或保存手工 reference note
4. 低置信候选必须停留在 `PENDING`，不能自动写入 confirmed references
5. 删除 seminar 时清理 candidates、lookup evidence、lookup attempts 和 Brief

## 10. 设计意图摘要

- 首页负责“找到哪一场 seminar”
- 详情页负责“确认这场 seminar 有什么”
- 现场页负责“尽快记录，不犯错”
- 时间线负责“回放同一场 seminar 的完整上下文”
- Research Reconstruction 负责“把照片整理成可搜索、可修订、可导出的研究材料”
- Reference Candidate Review 负责“把用户选择的线索变成可核对的候选文献”
- Seminar Brief 负责“把人工确认的 references、key slides、问题和笔记整理成可导出的研究摘要”
