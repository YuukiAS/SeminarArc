# Design Tokens

## 1. 令牌原则

- 所有令牌面向 `Jetpack Compose MaterialTheme` 映射
- 页面内禁止硬编码颜色、字号、圆角和随意间距
- 颜色先映射到 Material 3 角色，再补充 SeminarArc 扩展语义色

## 2. Material 3 颜色角色

### Light Scheme

```text
primary: #0D2A5C
onPrimary: #FFFFFF
primaryContainer: #D8E6FF
onPrimaryContainer: #081B3D

secondary: #1698C7
onSecondary: #FFFFFF
secondaryContainer: #D4F2FB
onSecondaryContainer: #08384A

tertiary: #8F6A2A
onTertiary: #FFFFFF
tertiaryContainer: #F7E3BF
onTertiaryContainer: #3A2710

background: #F7F9FC
onBackground: #162033
surface: #FCFDFF
onSurface: #162033
surfaceVariant: #EAF0F7
onSurfaceVariant: #526176

error: #BA1A1A
onError: #FFFFFF
errorContainer: #FFDAD6
onErrorContainer: #410002

outline: #738397
outlineVariant: #C3CEDA
inverseSurface: #2A3140
inverseOnSurface: #EEF3FB
surfaceTint: #0D2A5C
scrim: #000000
```

### Dark Scheme

```text
primary: #AFC7FF
onPrimary: #002655
primaryContainer: #003A7D
onPrimaryContainer: #D8E6FF

secondary: #7FD8F2
onSecondary: #003545
secondaryContainer: #004D62
onSecondaryContainer: #D4F2FB

tertiary: #E7C78C
onTertiary: #4F3800
tertiaryContainer: #6D4F16
onTertiaryContainer: #F7E3BF

background: #0E1520
onBackground: #E8EDF6
surface: #101926
onSurface: #E8EDF6
surfaceVariant: #223043
onSurfaceVariant: #BCC8D8

error: #FFB4AB
onError: #690005
errorContainer: #93000A
onErrorContainer: #FFDAD6

outline: #8D9AAF
outlineVariant: #3B485A
inverseSurface: #E8EDF6
inverseOnSurface: #1E2633
surfaceTint: #AFC7FF
scrim: #000000
```

## 3. SeminarArc 品牌色

```text
brandNavy: #0D2A5C
brandNavyDeep: #081B3D
brandCyan: #1AA7D8
brandCyanSoft: #B8ECFB
brandMist: #F2F7FB
brandWave: #3BB8E6
```

品牌使用规则：
- `brandNavy` 用于顶部结构、主按钮、关键标题锚点
- `brandCyan` 用于关键强调、时间线焦点、活跃筛选、品牌微装饰
- 弧线和波形只允许使用 `brandCyan` 的浅深变化，不加入第三套高饱和色

## 4. Typography 层级

```text
displayLarge: 40 / 48, SemiBold
headlineLarge: 32 / 40, SemiBold
headlineMedium: 28 / 36, SemiBold
titleLarge: 22 / 28, Medium
titleMedium: 18 / 24, Medium
bodyLarge: 16 / 24, Regular
bodyMedium: 14 / 20, Regular
bodySmall: 12 / 18, Regular
labelLarge: 14 / 20, Medium
labelMedium: 12 / 16, Medium
labelSmall: 11 / 16, Medium
```

使用规则：
- seminar 标题优先 `headlineMedium` 或 `titleLarge`
- speaker / 时间 / 地点优先 `bodyMedium`
- 状态 pill、计数和按钮标签使用 `labelLarge` 或 `labelMedium`
- 时间戳不可低于 `bodySmall`

## 5. Shape 层级

```text
extraSmall: 8dp
small: 12dp
medium: 16dp
large: 20dp
extraLarge: 28dp
fab: 18dp
pill: 999dp
```

使用规则：
- 列表行与轻分组 surface：`medium`
- 主操作按钮：`large`
- 状态 pill / filter chip：`pill`

## 6. 间距体系

```text
space1: 4dp
space2: 8dp
space3: 12dp
space4: 16dp
space5: 20dp
space6: 24dp
space8: 32dp
space10: 40dp
space12: 48dp
```

推荐用法：
- 页面水平边距：`space5`
- 区块上下分隔：`space6`
- 行内图标与文本间距：`space2` 或 `space3`
- 主要按钮区上下留白：`space6`

## 7. 图标和插图规则

- 图标默认使用单色描边或双色轻填充
- 不使用复杂拟物插图
- 空状态插图可使用文档、波形、弧线、讲台、照片等语义元素
- 事件图标固定映射，不随页面随意变化

## 8. 语义颜色

```text
recordingActive: #D92D20
recordingPaused: #D98C1F
markMoment: #0D2A5C
slidePhoto: #1698C7
question: #A56A12
quickNote: #3C7A5E
processing: #3A78D0
success: #1F7A4D
warning: #A56A12
errorStrong: #BA1A1A
```

使用规则：
- 录音中必须同时显示红点和文本 `Recording`
- 暂停使用暖橙并配合 `Paused`
- `Question` 可以使用低饱和暖色，帮助与 photo / mark 区分
- `Quick Note` 使用沉稳绿色，不与成功态混淆
- `PROCESSING` 必须有文本，不仅靠蓝色

## 9. Compose Theme 映射建议

- `MaterialTheme.colorScheme`：承载全部 M3 角色
- `SeminarArcExtendedColors`：补充 `recordingActive / recordingPaused / mark / photo / question / note / processing`
- `SeminarArcSpacing`：统一 4dp 栅格
- `SeminarArcShapes`：统一圆角层级

## 10. 实现阶段禁止事项

- 不得在 composable 内直接写十六进制颜色
- 不得用 `Color.White` / `Color.Black` 替代语义色
- 不得为“做得更炫”加入大面积紫色、荧光色或强渐变
- 不得因为深色模式而改变核心层级和操作语义
