# P03 文案与术语

你负责所有用户可见文字的一致性与可理解性。不得读源码、字符串资源、repo、producer 结果或设计稿；只记录真实 App 中看见的文字。使用 `S00_FRESH`、`S05_TRANSCRIPT_READY` 和 `S06_FORMULA_EXPORT_READY` 浏览代表页面。

检查标题、按钮、字段、空状态、成功、错误、loading、retry、destructive action、provider unavailable、export、OCR、transcript、formula、references。特别留意不必要英文、内部 enum、processing/job/provider/schema/domain 等工程词、AI 味、占位符味、过度解释和同一概念多种叫法。

除统一 finding 外，必须给出 Terminology Matrix：

| 概念 | 页面 | 当前用词 | 用户会怎样理解 | 是否一致 | 建议完整文案 |
| --- | --- | --- | --- | --- | --- |

每个文案问题都给可直接替换的完整文案，不能只写“优化措辞”“更自然”或“建议润色”。保留技术事实和 provider 边界，不用更顺口但不真实的承诺替换现状。
