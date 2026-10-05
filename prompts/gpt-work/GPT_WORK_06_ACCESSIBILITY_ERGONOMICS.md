# P06 可访问性与设备可用性

本轮不是 WCAG certification，而是检查普通用户是否能在代表性 Android 环境完成操作。不得根据 producer golden 或 instrumentation 结论推断 PASS。

使用 `S00_FRESH` 与 `S08_LONG_CONTENT`。按批准控制面依次观察 normal phone、small phone、font scale 1.3 和合理的 landscape；每次改变 profile 后重新确认 App 可见，结束时恢复 normal。必要时读取 accessibility hierarchy 的 role、content description、enabled、selected 和 bounds，但动作仍须发生在可见 UI。

检查 touch target、focus、content description、icon-only action、large-font clipping、keyboard obstruction、scrollability、horizontal overflow、dialog、button crowding、只靠颜色表达状态、disabled/enabled、error location、明显低对比，以及长 OCR/title/abstract。

报告具体设备/profile、可复现步骤和实际用户影响。无法在当前环境可靠判断的 TalkBack 或硬件行为应写边界，不能伪造认证或 PASS。
