param(
    [string]$Serial = "emulator-5558",
    [switch]$SkipDynamicProbe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "gpt-work-acceptance-common.ps1")
Initialize-GptWorkAndroidEnvironment

$repoRoot = Get-GptWorkRepoRoot
$candidate = Test-GptWorkCandidateFile
$catalog = Get-GptWorkScenarioCatalog
$operationsRoot = Join-Path $repoRoot "docs\operations\gpt-work-acceptance"
$promptRoot = Join-Path $repoRoot "prompts\gpt-work"
$parentPath = Join-Path $promptRoot "SEMINARARC_GPT_WORK_ACCEPTANCE_PARENT.md"
$fixtureRoot = Join-Path $repoRoot "private\gpt-work-acceptance\fixtures\geometric-neural-fields"
$verificationRoot = Join-Path $repoRoot ("private\gpt-work-acceptance\verification\" + (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssfffZ"))
New-Item -ItemType Directory -Force -Path $verificationRoot | Out-Null

function Assert-Condition {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Get-NodeCenter {
    param([System.Xml.XmlElement]$Node)
    $match = [regex]::Match($Node.GetAttribute("bounds"), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $match.Success) { throw "UI node has no usable bounds: $($Node.OuterXml)" }
    return [pscustomobject]@{
        X = [int]((($match.Groups[1].Value -as [int]) + ($match.Groups[3].Value -as [int])) / 2)
        Y = [int]((($match.Groups[2].Value -as [int]) + ($match.Groups[4].Value -as [int])) / 2)
    }
}

function Assert-ScriptSyntax {
    param([string]$Path)
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors)
    if ($errors.Count -gt 0) { throw "PowerShell syntax error in $Path`: $($errors[0].Message)" }
}

foreach ($script in @(
    "gpt-work-acceptance-common.ps1",
    "launch-gpt-work-acceptance.ps1",
    "prepare-gpt-work-acceptance.ps1",
    "reset-gpt-work-acceptance.ps1",
    "render-gpt-work-acceptance-parent.ps1",
    "verify-gpt-work-acceptance.ps1",
    "blackbox\New-GptWorkAcademicFixture.ps1"
)) {
    Assert-ScriptSyntax -Path (Join-Path $scriptRoot $script)
}

# Candidate gate: compare the single machine-readable contract with the immutable values
# used by the launcher/reset seam, then inspect the APK itself.
$candidateJsonPath = Join-Path $operationsRoot "CURRENT_CANDIDATE.json"
Assert-Condition (Test-Path -LiteralPath $candidateJsonPath) "CURRENT_CANDIDATE.json is missing."
$candidateJson = Get-Content -LiteralPath $candidateJsonPath -Raw | ConvertFrom-Json
foreach ($pair in @(
    @("candidate_id", $candidate.candidateId),
    @("git_commit", $candidate.gitCommit),
    @("apk_path", $candidate.apkPath),
    @("apk_sha256", $candidate.apkSha256),
    @("application_id", $candidate.applicationId),
    @("version_name", $candidate.versionName),
    @("version_code", $candidate.versionCode),
    @("signer_fingerprint_sha256", $candidate.signerFingerprintSha256),
    @("room_schema_version", $candidate.roomSchemaVersion),
    @("avd_profile", $candidate.avdProfile),
    @("android_api", $candidate.androidApi)
)) {
    Assert-Condition (($candidateJson.($pair[0])).ToString() -eq $pair[1].ToString()) "Candidate contract mismatch for $($pair[0])."
}

$aapt = Join-Path $env:ANDROID_HOME "build-tools\36.0.0\aapt.exe"
$badging = Invoke-GptWorkProcess -FilePath $aapt -Arguments @("dump", "badging", $candidate.apkAbsolutePath) -TimeoutSeconds 60
Assert-Condition ($badging.Stdout -match "package: name='$([regex]::Escape($candidate.applicationId))'") "APK package id mismatch."
Assert-Condition ($badging.Stdout -match "versionCode='$($candidate.versionCode)'") "APK versionCode mismatch."
Assert-Condition ($badging.Stdout -match "versionName='$([regex]::Escape($candidate.versionName))'") "APK versionName mismatch."
$apksignerJar = Join-Path $env:ANDROID_HOME "build-tools\36.0.0\lib\apksigner.jar"
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
$certs = Invoke-GptWorkProcess -FilePath $java -Arguments @("-jar", $apksignerJar, "verify", "--print-certs", $candidate.apkAbsolutePath) -TimeoutSeconds 90
$digest = [regex]::Match($certs.Stdout, 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)').Groups[1].Value.ToUpperInvariant()
Assert-Condition ($digest -eq $candidate.signerFingerprintSha256) "APK signer fingerprint mismatch."

# Realistic, synthetic fixture gate.
& (Join-Path $scriptRoot "blackbox\New-GptWorkAcademicFixture.ps1") -OutputRoot $fixtureRoot | Out-Null
$fixtureManifestPath = Join-Path $fixtureRoot "fixture-manifest.json"
$fixture = Get-Content -LiteralPath $fixtureManifestPath -Raw | ConvertFrom-Json
Assert-Condition ($fixture.classification -eq "non-sensitive synthetic academic seminar") "Fixture classification is not synthetic/non-sensitive."
Assert-Condition (@($fixture.slides).Count -eq 4) "Academic fixture must contain four slides."
Add-Type -AssemblyName System.Drawing
foreach ($slide in $fixture.slides) {
    $slidePath = Join-Path $fixtureRoot $slide.file
    Assert-Condition (Test-Path -LiteralPath $slidePath) "Missing fixture slide $($slide.file)."
    $image = [Drawing.Image]::FromFile($slidePath)
    try {
        Assert-Condition ($image.Width -eq [int]$slide.width -and $image.Height -eq [int]$slide.height) "Fixture dimensions mismatch for $($slide.file)."
    } finally { $image.Dispose() }
}
$pdfPath = Join-Path $fixtureRoot $fixture.abstractPdf
Assert-Condition (Test-Path -LiteralPath $pdfPath) "Synthetic abstract PDF is missing."
$pdfBytes = [IO.File]::ReadAllBytes($pdfPath)
Assert-Condition ($pdfBytes.Length -gt 5 -and [Text.Encoding]::ASCII.GetString($pdfBytes, 0, 5) -eq "%PDF-") "Synthetic abstract is not a PDF."
$visibleFixtureText = (($fixture.seminar | ConvertTo-Json -Compress) + " " + (($fixture.slides | ConvertTo-Json -Compress)))
Assert-Condition ($visibleFixtureText -notmatch '(?i)\b(lorem|debug|test data|fixture)\b') "Fixture exposes source/debug wording."

# Scenario gate: each state must have been produced by the real UI recipe and
# captured after the frozen APK was visible.
$resetRoot = Join-Path $repoRoot "private\gpt-work-acceptance\resets"
$stateFiles = @(Get-ChildItem -LiteralPath $resetRoot -Recurse -Filter "reset-state.json" -File -ErrorAction SilentlyContinue)
Assert-Condition ($catalog.Count -eq 9) "Scenario catalog must contain S00-S08."
foreach ($scenarioId in $catalog.Keys) {
    $matches = New-Object System.Collections.Generic.List[object]
    foreach ($stateFile in $stateFiles) {
        $state = Get-Content -LiteralPath $stateFile.FullName -Raw | ConvertFrom-Json
        if ($state.scenario -eq $scenarioId -and $state.candidateId -eq $candidate.candidateId -and $state.apkSha256 -eq $candidate.apkSha256) {
            $matches.Add([pscustomobject]@{ State = $state; File = $stateFile })
        }
    }
    Assert-Condition ($matches.Count -gt 0) "No successful real-UI reset evidence exists for $scenarioId."
    $latest = $matches | Sort-Object { $_.File.LastWriteTimeUtc } -Descending | Select-Object -First 1
    Assert-Condition (Test-Path -LiteralPath (Join-Path $latest.State.evidenceRoot "after-reset-screen.png")) "Scenario screenshot missing for $scenarioId."
    Assert-Condition (Test-Path -LiteralPath (Join-Path $latest.State.evidenceRoot "after-reset-ui.xml")) "Scenario UI hierarchy missing for $scenarioId."
    Assert-Condition ($latest.State.stateSource -eq $catalog[$scenarioId].stateSource) "Scenario source mismatch for $scenarioId."
}

# Prompt/Parent gate. Render from its authoritative component files and require
# that the delivered artifact is self-contained and fully resolved.
for ($i = 1; $i -le 8; $i++) {
    $prompt = Get-ChildItem -LiteralPath $promptRoot -Filter ("GPT_WORK_{0:D2}_*.md" -f $i) -File
    Assert-Condition (@($prompt).Count -eq 1) "Expected exactly one P$('{0:D2}' -f $i) prompt."
}
& (Join-Path $scriptRoot "render-gpt-work-acceptance-parent.ps1") -OutputPath $parentPath | Out-Null
$parent = Get-Content -LiteralPath $parentPath -Raw
Assert-Condition ($parent.Contains($candidate.candidateId)) "Parent does not contain exact candidate id."
Assert-Condition ($parent.Contains($candidate.gitCommit)) "Parent does not contain exact candidate commit."
Assert-Condition ($parent.Contains($candidate.apkSha256)) "Parent does not contain exact APK SHA-256."
$serialCursor = -1
foreach ($step in @("P01", "P02", "P03", "P04", "P05", "P06", "P07", "P08", "consolidation")) {
    $serialCursor = $parent.IndexOf($step, $serialCursor + 1, [StringComparison]::Ordinal)
    Assert-Condition ($serialCursor -ge 0) "Strict serial order is missing step $step."
}
Assert-Condition ($parent.Contains("ANDROID UI BLACK-BOX CONTRACT") -or $parent.Contains("Android UI 黑盒合同")) "Black-box contract is not inline."
Assert-Condition ($parent.Contains("AUDIT_RESULT_CONTRACT") -or $parent.Contains("Severity")) "Audit result contract is not inline."
Assert-Condition ($parent -notmatch '\{\{[^}]+\}\}|\$\{[^}]+\}|<[^>]+>|(?im)\b(TODO|TBD|PLACEHOLDER)\b') "Parent contains an unresolved placeholder."
Assert-Condition ($parent -notmatch '(?i)\b9/9\b|33\s+goldens|3,?750|24\s+tests|producer\s+QA\s+(passed|pass)|visual\s+regression\s+(passed|pass)|real-world\s+corpus\s+(passed|pass)') "Parent contains producer result contamination."
Assert-Condition ($parent -notmatch [regex]::Escape((Get-GptWorkHostUserProfile))) "Parent exposes the host user profile."
Assert-Condition ($parent -notmatch '(?i)(password\s*=|private[_ -]?key\s*=|api[_ -]?key\s*=|token\s*=)') "Parent appears to contain a secret assignment."
Assert-Condition ($parent.Contains("不得") -and $parent.Contains("GitHub") -and $parent.Contains("源码")) "Parent does not prohibit source/GitHub access."
Assert-Condition ($parent.Contains("WORK_RUNTIME_BINDING = RUNTIME_CHECK_REQUIRED")) "Work runtime binding preflight is unresolved incorrectly."
Assert-Condition ($parent.Contains("D:\Code\SeminarArc-emulator\private\gpt-work-acceptance\gpt-work-seminararc-64b5321\")) "Parent output path is not resolved."

$inventory = Assert-GptWorkEmulator -Serial $Serial
$avdName = (Invoke-GptWorkAdb -Serial $Serial -Arguments @("emu", "avd", "name") -TimeoutSeconds 30).Stdout -replace "OK", ""
Assert-Condition ($avdName.Trim() -eq $candidate.avdProfile) "Wrong AVD is attached to $Serial."

if (-not $SkipDynamicProbe) {
    $launcher = Join-Path $scriptRoot "launch-gpt-work-acceptance.ps1"
    $launch = Invoke-GptWorkProcess -FilePath "powershell.exe" -Arguments @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $launcher,
        "-Scenario", "S00_FRESH", "-Serial", $Serial, "-VerifyInstalledHash"
    ) -TimeoutSeconds 2100 -IgnoreExitCode
    ($launch.Stdout + "`n" + $launch.Stderr) | Set-Content -Encoding UTF8 -Path (Join-Path $verificationRoot "launcher.log")
    Assert-Condition ($launch.ExitCode -eq 0 -and $launch.Stdout -match "ACCEPTANCE_ENVIRONMENT=READY" -and $launch.Stdout -match "APP_VISIBLE=YES") "Launcher dry-run failed."

    $before = Save-GptWorkUiEvidence -Serial $Serial -OutputRoot $verificationRoot -Label "probe-before"
    [xml]$beforeUi = Get-Content -LiteralPath $before.UiXml
    $createNode = $beforeUi.SelectSingleNode("//node[@content-desc='Create seminar']")
    Assert-Condition ($null -ne $createNode) "Semantic probe could not locate Create seminar."
    $createCenter = Get-NodeCenter -Node $createNode
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "input", "tap", $createCenter.X, $createCenter.Y) -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Seconds 1

    $editor = Save-GptWorkUiEvidence -Serial $Serial -OutputRoot $verificationRoot -Label "probe-editor"
    [xml]$editorUi = Get-Content -LiteralPath $editor.UiXml
    $editNode = $editorUi.SelectSingleNode("//node[@class='android.widget.EditText']")
    Assert-Condition ($null -ne $editNode) "Semantic probe could not locate a real EditText."
    $editCenter = Get-NodeCenter -Node $editNode
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "input", "tap", $editCenter.X, $editCenter.Y) -TimeoutSeconds 30 | Out-Null
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "input", "text", "academic") -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 700
    $typed = Save-GptWorkUiEvidence -Serial $Serial -OutputRoot $verificationRoot -Label "probe-typed"
    [xml]$typedUi = Get-Content -LiteralPath $typed.UiXml
    Assert-Condition ($typedUi.OuterXml -match "academic") "Semantic probe input was not visible in UI hierarchy."

    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "input", "keyevent", "4") -TimeoutSeconds 30 | Out-Null
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "am", "force-stop", $candidate.applicationId) -TimeoutSeconds 30 | Out-Null
    Start-GptWorkApp -Serial $Serial
    $relaunched = Save-GptWorkUiEvidence -Serial $Serial -OutputRoot $verificationRoot -Label "probe-relaunch"
    [xml]$relaunchedUi = Get-Content -LiteralPath $relaunched.UiXml
    Assert-Condition (@($relaunchedUi.SelectNodes("//node[@package='$($candidate.applicationId)']")).Count -gt 0) "App did not relaunch visibly."

    $cleanup = Invoke-GptWorkProcess -FilePath "powershell.exe" -Arguments @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $scriptRoot "reset-gpt-work-acceptance.ps1"),
        "-Scenario", "S00_FRESH", "-Serial", $Serial, "-VerifyInstalledHash"
    ) -TimeoutSeconds 2100 -IgnoreExitCode
    ($cleanup.Stdout + "`n" + $cleanup.Stderr) | Set-Content -Encoding UTF8 -Path (Join-Path $verificationRoot "cleanup.log")
    Assert-Condition ($cleanup.ExitCode -eq 0 -and $cleanup.Stdout -match "SCENARIO_RESET=PASS") "Sacrificial probe cleanup/reset failed."
}

$summary = [ordered]@{
    verifiedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    candidateId = $candidate.candidateId
    gitCommit = $candidate.gitCommit
    apkSha256 = $candidate.apkSha256
    signerFingerprintSha256 = $candidate.signerFingerprintSha256
    avdProfile = $candidate.avdProfile
    emulatorSerial = $Serial
    inventory = $inventory.Raw
    scenarioCount = $catalog.Count
    dynamicProbe = $(if ($SkipDynamicProbe) { "SKIPPED" } else { "PASS" })
    protectedPhysicalDeviceTargeted = $false
    gptWorkExecuted = $false
    parentPath = $parentPath
}
$summary | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -Path (Join-Path $verificationRoot "verification-summary.json")

Write-Output "ACCEPTANCE_INFRASTRUCTURE = PASS"
Write-Output "CANDIDATE_IDENTITY_GATE = PASS"
Write-Output "EMULATOR_CONTROL_GATE = PASS"
Write-Output "SCENARIO_RESET_GATE = PASS"
Write-Output "EVIDENCE_PIPELINE_GATE = PASS"
for ($i = 1; $i -le 8; $i++) { Write-Output ("P{0:D2}_PROMPT = PASS" -f $i) }
Write-Output "STRICT_SERIAL_ORDER = PASS"
Write-Output "BLACK_BOX_CONTRACT_INLINE = PASS"
Write-Output "AUDIT_RESULT_CONTRACT_INLINE = PASS"
Write-Output "NO_UNRESOLVED_PLACEHOLDERS = PASS"
Write-Output "NO_PRODUCER_RESULT_CONTAMINATION = PASS"
if ($SkipDynamicProbe) {
    Write-Output "ANDROID_UI_AUTOMATION_GATE = NOT_EVALUATED"
    Write-Output "PARENT_DELIVERY_GATE = NOT_EVALUATED"
    Write-Output "READY_TO_PASTE_PARENT = NO"
    Write-Output "DYNAMIC_DRY_RUN = SKIPPED"
    Write-Output "GPT_WORK_EXECUTED = NO"
    Write-Output "READY_FOR_USER_REVIEW = NO"
    Write-Output "USER_ACTION_REQUIRED_NOW = NO"
    Write-Output "VERIFICATION_EVIDENCE = $verificationRoot"
    exit 0
}
Write-Output "ANDROID_UI_AUTOMATION_GATE = PASS"
Write-Output "PARENT_DELIVERY_GATE = PASS"
Write-Output "READY_TO_PASTE_PARENT = YES"
Write-Output "WORK_RUNTIME_BINDING = RUNTIME_CHECK_REQUIRED"
Write-Output "GPT_WORK_EXECUTED = NO"
Write-Output "READY_FOR_USER_REVIEW = NO"
Write-Output "USER_ACTION_REQUIRED_NOW = NO"
Write-Output "GPT_WORK_PARENT_PATH = $parentPath"
Write-Output "VERIFICATION_EVIDENCE = $verificationRoot"
