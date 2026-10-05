# Android UI Black-box Contract

核心原则：可以自动操作真实 App，不能绕过真实 App。

## 允许

- 用 Computer Use 操作可见 Android Emulator；必要时用 scrcpy 或已批准的语义 UI bridge。
- tap/click、swipe/scroll、文字输入、Back、Home、离开后重进。
- 通过 acceptance control seam force-stop/relaunch、加载批准的 scenario、切换批准的 display profile。
- 截图并读取可见文字。
- 读取真实用户控件在 accessibility/UI hierarchy 中的 text、content description、role/class、enabled、selected、checked 和 bounds。
- 用 Appium/UiAutomator2 或本包已验证的 ADB UiAutomator semantic bridge 定位真实可见控件；定位后仍必须对可见 UI 执行动作。

## 严禁

- 访问 GitHub、repo、源码、内部产品文档、producer QA closeout、visual golden、real-world corpus 结论或 developer-known issue list。
- 读取或修改 Room、SharedPreferences、DataStore、app-private 文件；不得使用 `run-as`、`sqlite3` 或内部 test API。
- 直接调用 Repository、ViewModel、use case，或 intent 到普通用户无法到达的 route。
- 直接写 state、注入业务成功、伪造 processing result、修改 APK 或重 build。
- 以 golden/Figma/Codex 结论判断“应该长什么样”。

## Environment control seam

只允许：启动 exact candidate、恢复 S00–S08、切换批准 profile、force-stop/relaunch、截图、UI tree 和必要 lifecycle control。它可以改变前置状态，但不能代替该 role 正在审查的核心动作。P01 与 P08 必须从 S00 通过 UI 创建 seminar；不得用预建 seminar 冒充创建流程完成。

## 工具故障与产品故障

窗口失焦、tap timeout、selector timeout、hierarchy timeout、截图失败或 controller 重启首先是工具故障。发生后必须重新观察真实 App：若 UI 已完成动作，不得报告产品 bug。Computer Use 失败时依次尝试可见 Emulator、scrcpy、Appium/UiAutomator semantic bridge；所有 fallback 最终都要操作真实 UI。只有普通用户可观察到的 App 状态错误才可定级为产品 finding。

记录：`UI_CONTROL_MODE`、`TOOL_FAILURE_OCCURRED`，且 `TOOL_FAILURE_MISCLASSIFIED_AS_PRODUCT` 必须为 `NO`。
