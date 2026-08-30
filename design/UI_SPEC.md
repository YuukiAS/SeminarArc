# UI Specification

## 1. 产品视觉原则

- `Academic Archive` 是本次选定方向：强调学术归档、清晰层级、长期回顾。
- 主基调是专业、安静、克制，不做消费级喧闹反馈。
- 所有素材必须显式从属于某一场 seminar，不能出现“脱离 seminar 的照片/录音”视觉印象。
- 页面结构优先用分组、列表、间距和字体层级建立秩序，避免大量无意义卡片。
- 现场页虽然属于同一视觉体系，但操作优先级高于装饰性。

## 2. 页面清单

### 2.1 Seminar 列表首页

页面目的：
统一管理 seminar，支持检索、筛选、快速识别状态与进入单场 seminar。

主操作：
- 创建 seminar
- 打开 seminar
- 搜索
- 按 `All / Draft / Completed / Favorites` 筛选

次要操作：
- 收藏
- 查看照片数与 clip 数

页面结构：
- 顶部栏：品牌、搜索、筛选入口
- 筛选行：分段筛选或 filter chip
- 列表主体：按时间分组的 seminar 行
- 底部创建动作：FAB

组件映射：
- `TopAppBar`
- `SegmentedButtonRow` 或 `FilterChip`
- `LazyColumn`
- 自定义 `ListItem` 风格 seminar row
- `FloatingActionButton`

### 2.2 创建或编辑 Seminar

页面目的：
编辑 seminar 基础信息并管理 abstract PDF。

主操作：
- 保存
- 保存为草稿
- 导入 abstract PDF

次要操作：
- 替换 PDF
- 移除 PDF

页面结构：
- 顶部栏：返回、溢出菜单
- 表单区：标题、speaker、affiliation、时间、地点
- abstract 文本区
- PDF 附件区
- 底部保存动作

组件映射：
- `TopAppBar`
- `OutlinedTextField`
- 日期/时间选择触发器
- 只读附件行 + 行内动作
- `Button` / `TextButton`

### 2.3 Seminar 详情页

页面目的：
作为单场 seminar 的总入口，展示元信息、附件、状态、收藏、评分、录音汇总与时间线预览。

主操作：
- 开始或恢复 seminar
- 打开 abstract PDF
- 进入完整时间线

次要操作：
- 编辑
- 删除
- 收藏
- 评分

页面结构：
- 顶部栏：返回、编辑、溢出操作
- seminar 标题与元信息头部
- 状态与评分区
- abstract 区
- 附件区
- recording summary 区
- timeline preview 区
- 底部主操作区

组件映射：
- `TopAppBar`
- `AssistChip` / 状态 pill
- 评分 icon row
- 附件行
- `Surface` 分组
- `Button`

### 2.4 现场记录页

页面目的：
在 seminar 进行中完成录音、标记、拍照、提问、速记和结束动作。

主操作：
- `Mark Moment`
- `Capture Slide`
- `End Seminar`

次要操作：
- `Add Question`
- `Quick Note`
- 暂停/恢复录音

页面结构：
- 顶部 seminar 身份区
- 录音状态与计时区
- 波形区
- 主操作双按钮区
- 次操作按钮区
- 结束 seminar 区

组件映射：
- `TopAppBar`
- 信息头 `Surface`
- 大型主按钮 `FilledTonalButton` / 自定义大尺寸 surface button
- 次操作 `OutlinedButton`
- 危险动作确认入口

### 2.5 Seminar 时间线回顾页

页面目的：
按 recording offset 统一回看所有事件，并从任一点播放。

主操作：
- `Play from here`
- 播放 clip
- 播放完整录音

次要操作：
- 打开图片
- 编辑 note/question
- 重试失败 clip

页面结构：
- 顶部栏
- seminar 身份概览
- abstract 快捷入口
- 时间线主体
- 底部录音播放器

组件映射：
- `TopAppBar`
- `TabRow` 或轻量 section switch
- `LazyColumn`
- 自定义 timeline spine + event rows
- 固定底部 player bar

### 2.6 Research Reconstruction 工作区

页面目的：
让 completed seminar 的照片从“可回看”变成“可增强、可 OCR、可搜索、可筛选关键幻灯片、可继续整理”的本地会后工作区。

入口：
- `Seminar Detail` 在 seminar `COMPLETED` 时显示 `Research Reconstruction` 主操作。
- Timeline 的 photo event 可进入同一 workspace 并预选对应照片。

主操作：
- 选择照片或关键幻灯片
- 运行本地 OCR
- 编辑 OCR 文本
- 标记 / 取消关键幻灯片
- 搜索 OCR 文本
- 重试或取消处理任务

次要操作：
- 原图 / 增强图切换
- rotate / crop / perspective / readability enhancement
- 添加系统标签：`KEY_SLIDE`、`BACKGROUND`、`METHOD`、`RESULT`、`REFERENCE`、`FORMULA`、`FOLLOW_UP`
- 添加用户自定义标签
- 返回 Timeline 对应 offset

页面结构：
- 顶部栏：返回、seminar title、处理状态入口
- Seminar identity strip：speaker、date、photo count、OCR count
- Search/filter row：搜索框、key slides filter、OCR status filter
- Photo rail：缩略图列表，显示 key-slide、tag、OCR status
- Selected slide workspace：大图预览、原图/增强图切换、增强操作
- OCR review panel：状态、识别文本、编辑入口、保存状态
- Processing queue：queued/running/failed/cancelled jobs 和 retry/cancel

组件映射：
- `TopAppBar`
- `SearchBar` 或 `OutlinedTextField`
- `FilterChip`
- `SegmentedButton`
- `LazyRow` / `LazyColumn`
- 轻量 `Surface` 分组
- `TextField` for OCR editing
- `AssistChip` for tags/status

状态要求：
- `PENDING`：可运行 OCR / enhancement
- `RUNNING`：显示任务类型、不能重复排队同一 input
- `SUCCEEDED`：显示结果和时间
- `FAILED`：显示 readable error 与 retry
- `CANCELLED`：显示可重新运行
- empty / no photo：解释没有照片仍可使用 0.1.x timeline/export

禁止事项：
- 不在 `0.2.x` 显示真实 Crossref/OpenAlex、DOI matching、transcription、Whisper、AI summary、Notion、formula OCR 或 cloud upload 功能。
- Future capability 只能 disabled / coming later，不能伪装成已完成。

## 3. 导航规则

- 启动进入 `Seminar List`
- `Seminar List -> Seminar Editor`
- `Seminar List -> Seminar Detail`
- `Seminar Detail -> Live Capture`
- `Seminar Detail -> Seminar Timeline`
- `Live Capture -> End Seminar Confirm -> Seminar Timeline`
- `Seminar Detail (COMPLETED) -> Research Reconstruction`
- `Seminar Timeline photo event -> Research Reconstruction(photoId)`
- 当已有 `ACTIVE` seminar 时，从列表或详情的“开始”动作必须回到该 active seminar，而不是创建第二场 session

## 4. 顶部栏、底部导航、FAB、按钮、卡片、列表、底部弹层

顶部栏规则：
- 使用单层 `TopAppBar`
- 首页以品牌识别为主
- 子页面优先返回路径和单场 seminar 身份

底部导航规则：
- 当前设计只在首页级信息架构上使用底部导航语义
- 实现阶段可根据信息架构缩减为单 FAB + 顶部导航，不得破坏主流程

FAB 规则：
- 首页仅允许一个主创建 FAB
- 不允许页面上同时出现多个抢主视觉的悬浮动作

按钮规则：
- 主操作用深蓝实体或青色高强调按钮
- 次操作用浅色描边或低强调 surface
- 危险操作只在需要确认时使用红色语义

卡片规则：
- 不把每个模块都做成重卡片
- 详情页和状态页允许用轻度 `Surface` 分组
- 时间线事件更像“行项目”而不是“卡片墙”

列表规则：
- 列表以清晰行高、时间分组和计数信息为主
- 每行必须能快速读出：标题、speaker、时间/地点、状态、照片数、clip 数

底部弹层规则：
- 仅用于确认、补充上下文或次级动作
- 不承载主流程关键内容

## 5. 颜色、排版、形状、间距、elevation

颜色：
- 主色深蓝负责结构和高优先级动作
- 青色负责关键强调和品牌联结
- 背景使用暖白和浅灰蓝，减少生硬纯白
- 错误只在真正异常或删除时使用

排版：
- 标题层级清楚，`seminar title` 是页面最大文本之一
- 元信息与说明文字使用低一层的中性文本
- 时间戳必须稳定可读，不使用过浅文本

形状：
- 统一中等圆角
- 主操作按钮圆角略大于普通行项

间距：
- 严格使用 4dp 栅格
- 页面水平主边距建议 20dp
- 分区上下间距建议 24dp

elevation：
- 常态分组以低 tonal elevation 为主
- 不依赖大阴影制造层级

## 6. 系统状态栏、导航栏、edge-to-edge

- 所有页面按 edge-to-edge 设计
- 深色顶栏页面：状态栏图标使用浅色
- 浅色内容区保持与导航栏背景协调，不留生硬色块
- 底部播放器或主操作区要考虑手势导航安全区

## 7. 触摸目标与无障碍

- 所有交互目标最小 48dp
- 图标按钮必须有清晰内容描述
- 时间线事件的“播放”“打开图片”“更多操作”都必须可被 TalkBack 独立聚焦
- 大字体下禁止文本溢出遮挡主操作
- 颜色不是唯一状态信号：录音、暂停、处理中、失败都要有文本标签

## 8. 动效和状态切换

- 页面转场保持克制，以淡入、轻滑动、低幅度共享感为主
- Live Capture 的录音状态可有轻微波形/脉冲，但不能分散操作注意力
- Timeline 事件展开、播放态切换和状态 pill 更新应有简短过渡
- Research Reconstruction 中 photo rail selection、原图/增强图切换和 OCR 状态变更使用克制过渡，不引入会影响阅读的动效
- 删除确认、权限请求、错误恢复优先清晰，不做花哨动画

## 9. 不允许实现阶段自行改变的关键视觉决策

- `Academic Archive` 的整体基调不能被改成强 dashboard 风格
- 首页必须保持“列表优先”，不能堆叠统计卡片替代 seminar 行
- Live Capture 必须保留两个高强调主操作：`Mark Moment` 与 `Capture Slide`
- Timeline 必须维持单条时间序列逻辑，不能拆散成多个互不相关的 tab 墙
- Research Reconstruction 必须保持 seminar-scoped，不做跨 seminar 全局资料库
- 录音、照片、问题、笔记都必须持续显示其属于当前 seminar
- 深色模式不能改成高饱和霓虹风
