param(
    [string]$OutputPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $repoRoot "prompts\gpt-work\SEMINARARC_GPT_WORK_ACCEPTANCE_PARENT.md"
} elseif (-not [IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath = Join-Path $repoRoot $OutputPath
}

$operationsRoot = Join-Path $repoRoot "docs\operations\gpt-work-acceptance"
$promptRoot = Join-Path $repoRoot "prompts\gpt-work"
$launcher = Join-Path $repoRoot "scripts\launch-gpt-work-acceptance.ps1"
$reset = Join-Path $repoRoot "scripts\reset-gpt-work-acceptance.ps1"

$header = @"
# SeminarArc GPT Work 独立 Android 黑盒验收 Parent

你将对冻结的 SeminarArc Android candidate 执行正式独立黑盒验收。你不是 producer，也不修产品。只通过可见 Android App、普通用户动作和下文批准的环境控制面判断产品。不要打开 GitHub、repo、源码、Room、app-private 文件、内部产品文档、producer QA 结果、visual golden 或 developer-known issue list。

本 Parent 已内联全部合同和 P01–P08 提示。不要到本地目录寻找补充规则，也不要把本地文件内容作为产品预期答案。

## 已解析的运行入口

Campaign id 固定为 `gpt-work-seminararc-64b5321`。

启动并核对 S00：

```powershell
& '$launcher' -Scenario S00_FRESH -Serial emulator-5558 -VerifyInstalledHash
```

角色间恢复：

```powershell
& '$reset' -Scenario S00_FRESH -Serial emulator-5558 -VerifyInstalledHash
```

需要其他状态时，将命令中的 `S00_FRESH` 改成下文明确指定的 S01–S08 id。不要查看这些脚本的源代码；它们只是批准的环境控制 seam。它们必须输出 `SCENARIO_RESET=PASS`、exact candidate 和 `APP_VISIBLE=YES` 后，role 才能开始。

证据根目录固定为：

```text
D:\Code\SeminarArc-emulator\private\gpt-work-acceptance\gpt-work-seminararc-64b5321\
```

未来 Work runtime 是否能直接绑定可见 Emulator 需要在本次会话开始时检查，因此初始状态为：

```text
WORK_RUNTIME_BINDING = RUNTIME_CHECK_REQUIRED
```

先运行 launcher 并尝试 Computer Use。能直接控制时记录 `DIRECT_COMPUTER_USE`；不能直接控制时使用下文已批准的 ADB UiAutomator semantic bridge，仍只操作真实可见 UI。若两条真实 UI 路径都不可用，保存环境证据并以 `BLOCKED_BY_AUDIT_ENVIRONMENT` 停止；不能改内部 state 继续。

## 严格串行执行

顺序只能是 P01 → P02 → P03 → P04 → P05 → P06 → P07 → P08 → consolidation。禁止并行。

若 runtime 支持 child reviewer，每次只启动一个 child，等待完整结果、保存证据、退出、reset 后再启动下一名。不得把前序 findings 传给后续 reviewer。若不支持 child，由 Parent 分段串行执行，并记录 `AUDITOR_ISOLATION = SINGLE_WORK_SERIAL`。只有 P08 完成后，consolidation 才第一次读取所有报告。

角色起始 state：P01=S00；P02=S01/S02/S03/S04/S06；P03=S00/S05/S06；P04=S03/S08；P05=S02/S05/S07；P06=S00/S08；P07=S04/S06；P08=S00。每次 state 切换都要重新核对 candidate identity。

## 停止边界

GPT Work 只输出 findings 和 consolidated verdict。不得 edit source、patch APK、改数据库、重 build、访问 GitHub 修产品或执行外部上传。真实 CameraX、麦克风质量、锁屏/厂商 ROM 行为只记 hardware-only boundary。完成后也不宣布用户验收已结束。
"@

$sections = @(
    [ordered]@{ title = "冻结 Candidate"; path = Join-Path $operationsRoot "CANDIDATE_IDENTITY_CONTRACT.md" },
    [ordered]@{ title = "Android UI 黑盒合同"; path = Join-Path $operationsRoot "ANDROID_UI_BLACKBOX_CONTRACT.md" },
    [ordered]@{ title = "Scenario 合同"; path = Join-Path $operationsRoot "SCENARIO_STATE_CONTRACT.md" },
    [ordered]@{ title = "证据合同"; path = Join-Path $operationsRoot "EVIDENCE_CONTRACT.md" },
    [ordered]@{ title = "结果与 Severity 合同"; path = Join-Path $operationsRoot "AUDIT_RESULT_CONTRACT.md" },
    [ordered]@{ title = "Campaign 编排"; path = Join-Path $operationsRoot "CAMPAIGN_ORCHESTRATION.md" },
    [ordered]@{ title = "P01"; path = Join-Path $promptRoot "GPT_WORK_01_FIRST_TIME_MENTAL_MODEL.md" },
    [ordered]@{ title = "P02"; path = Join-Path $promptRoot "GPT_WORK_02_CORE_WORKFLOW_LOGIC.md" },
    [ordered]@{ title = "P03"; path = Join-Path $promptRoot "GPT_WORK_03_LANGUAGE_TERMINOLOGY.md" },
    [ordered]@{ title = "P04"; path = Join-Path $promptRoot "GPT_WORK_04_VISUAL_UI.md" },
    [ordered]@{ title = "P05"; path = Join-Path $promptRoot "GPT_WORK_05_ROBUSTNESS_STATE.md" },
    [ordered]@{ title = "P06"; path = Join-Path $promptRoot "GPT_WORK_06_ACCESSIBILITY_ERGONOMICS.md" },
    [ordered]@{ title = "P07"; path = Join-Path $promptRoot "GPT_WORK_07_ACADEMIC_TRUST_PRIVACY.md" },
    [ordered]@{ title = "P08"; path = Join-Path $promptRoot "GPT_WORK_08_INTEGRATED_FINAL_JOURNEY.md" }
)

$builder = [Text.StringBuilder]::new()
[void]$builder.AppendLine($header.TrimEnd())
foreach ($section in $sections) {
    if (-not (Test-Path -LiteralPath $section.path)) { throw "Missing Parent source: $($section.path)" }
    [void]$builder.AppendLine()
    [void]$builder.AppendLine("---")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine("## $($section.title)")
    [void]$builder.AppendLine()
    [void]$builder.AppendLine((Get-Content -LiteralPath $section.path -Raw).Trim())
}

$footer = @"

---

## Consolidation 输出

P08 完成后读取 P01–P08 报告，按 root cause 去重并保留 role evidence。输出 `CONSOLIDATED_FINDINGS.md`，区分 product defect、tool failure、hardware-only boundary，列出建议的 Codex repair batch、targeted rerun roles 和是否需要 full rerun。

最终 verdict 只能是 `GPT_WORK_ACCEPTANCE = PASS`、`FAIL` 或 `BLOCKED_BY_AUDIT_ENVIRONMENT`。PASS 要求 P0=0、P1=0、P2_MUST_FIX=0、black-box contamination=NO、candidate identity unchanged、P01–P08 完整。P2 backlog/P3 可保留。

最后同时输出：

```text
CANDIDATE_ID = seminararc-64b5321-56d99be5-api36
APK_SHA256 = 56D99BE575766823E92BB84E86E2AD6A09F4F4124614423953C5BD9F2E1CE8E3
AUDITOR_ISOLATION = CHILD_SERIAL 或 SINGLE_WORK_SERIAL
WORK_RUNTIME_BINDING = DIRECT_COMPUTER_USE 或 SEMANTIC_UI_FALLBACK 或 FAILED
GPT_WORK_ACCEPTANCE = PASS 或 FAIL 或 BLOCKED_BY_AUDIT_ENVIRONMENT
READY_FOR_USER_REVIEW = YES 或 NO
```

只有 `GPT_WORK_ACCEPTANCE = PASS` 才能把 `READY_FOR_USER_REVIEW` 写成 `YES`；本 Parent 不执行任何用户真机验收。
"@
[void]$builder.AppendLine($footer.TrimEnd())

$outputDirectory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
($builder.ToString().TrimEnd() + [Environment]::NewLine) | Set-Content -Encoding UTF8 -NoNewline -Path $OutputPath
Write-Output $OutputPath
