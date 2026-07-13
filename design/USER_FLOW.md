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

## 7. 异常与边界

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

## 8. 设计意图摘要

- 首页负责“找到哪一场 seminar”
- 详情页负责“确认这场 seminar 有什么”
- 现场页负责“尽快记录，不犯错”
- 时间线负责“回放同一场 seminar 的完整上下文”
