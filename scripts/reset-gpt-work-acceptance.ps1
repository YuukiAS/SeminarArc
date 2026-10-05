param(
    [ValidateSet(
        "S00_FRESH", "S01_SEMINAR_DRAFT", "S02_COMPLETED_CAPTURE",
        "S03_RECONSTRUCTION_READY", "S04_RESEARCH_READY", "S05_TRANSCRIPT_READY",
        "S06_FORMULA_EXPORT_READY", "S07_ERROR_RECOVERY", "S08_LONG_CONTENT"
    )]
    [string]$Scenario = "S00_FRESH",
    [string]$Serial = "emulator-5558",
    [Alias("PrepareSnapshot")]
    [switch]$PrepareScenarioState,
    [switch]$VerifyInstalledHash
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "gpt-work-acceptance-common.ps1")
Initialize-GptWorkAndroidEnvironment

$repoRoot = Get-GptWorkRepoRoot
$candidate = Test-GptWorkCandidateFile
$catalog = Get-GptWorkScenarioCatalog
$definition = $catalog[$Scenario]
if ($null -eq $definition) { throw "Unknown scenario: $Scenario" }

Assert-GptWorkEmulator -Serial $Serial | Out-Null
Wait-GptWorkBoot -Serial $Serial -TimeoutSeconds 120

$fixtureRoot = Join-Path $repoRoot "private\gpt-work-acceptance\fixtures\geometric-neural-fields"
$fixtureGenerator = Join-Path $scriptRoot "blackbox\New-GptWorkAcademicFixture.ps1"
& $fixtureGenerator -OutputRoot $fixtureRoot | Out-Null

Install-GptWorkCandidate -Serial $Serial
$primarySlide = Join-Path $fixtureRoot "slide-02-method-portrait.png"
$harness = Join-Path $scriptRoot "run-blackbox-acceptance.ps1"
$scenarioArgs = @(
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $harness,
    "-Serial", $Serial,
    "-ApkPath", $candidate.apkAbsolutePath,
    "-SkipBuild", "-SkipInstall",
    "-UseAcademicAcceptanceFixture",
    "-PhotoFixturePath", $primarySlide,
    "-Scenarios", ($definition.builderScenarios -join ",")
)
$result = Invoke-GptWorkProcess -FilePath "powershell.exe" -Arguments $scenarioArgs -TimeoutSeconds 1800 -IgnoreExitCode
if ($result.ExitCode -ne 0 -or $result.Stdout -notmatch "Overall: PASS") {
    throw "Scenario builder failed for $Scenario.`n$($result.Stdout)`n$($result.Stderr)"
}

$deviceFixtureRoot = "/sdcard/Download/SeminarArc-Acceptance"
Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "mkdir", "-p", $deviceFixtureRoot) -TimeoutSeconds 30 | Out-Null
foreach ($fixture in (Get-ChildItem -LiteralPath $fixtureRoot -File)) {
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("push", $fixture.FullName, "$deviceFixtureRoot/$($fixture.Name)") -TimeoutSeconds 90 | Out-Null
}
Start-GptWorkApp -Serial $Serial
if ($PrepareScenarioState) { Write-Output "SCENARIO_PREPARED=$Scenario" }

$identity = Get-GptWorkInstalledIdentity -Serial $Serial -VerifyHash:$VerifyInstalledHash
if ($identity.package -ne $candidate.applicationId) { throw "Installed package mismatch." }
if ($identity.versionName -ne $candidate.versionName -or $identity.versionCode -ne $candidate.versionCode) {
    throw "Installed version mismatch: $($identity.versionName) / $($identity.versionCode)"
}
if ($VerifyInstalledHash -and $identity.installedApkSha256 -ne $candidate.apkSha256) {
    throw "Installed APK hash mismatch: $($identity.installedApkSha256)"
}

$runId = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssfffZ")
$evidenceRoot = Join-Path $repoRoot "private\gpt-work-acceptance\resets\$runId-$Scenario"
$evidence = Save-GptWorkUiEvidence -Serial $Serial -OutputRoot $evidenceRoot -Label "after-reset"
[xml]$ui = Get-Content -LiteralPath $evidence.UiXml
$packageNodes = @($ui.SelectNodes("//node[@package='$($candidate.applicationId)']"))
if ($packageNodes.Count -eq 0) { throw "App is not visible after reset to $Scenario." }
$uiText = $ui.OuterXml
if ($uiText -notmatch [regex]::Escape($definition.expectedText)) {
    throw "Scenario $Scenario did not expose expected visible text '$($definition.expectedText)' after reset."
}

$state = [ordered]@{
    scenario = $Scenario
    stateSource = $definition.stateSource
    candidateId = $candidate.candidateId
    gitCommit = $candidate.gitCommit
    apkSha256 = $candidate.apkSha256
    applicationId = $identity.package
    versionName = $identity.versionName
    versionCode = $identity.versionCode
    installedApkSha256 = $identity.installedApkSha256
    emulatorSerial = $Serial
    expectedText = $definition.expectedText
    evidenceRoot = $evidenceRoot
    resetAtUtc = (Get-Date).ToUniversalTime().ToString("o")
}
$state | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 -Path (Join-Path $evidenceRoot "reset-state.json")

Write-Output "SCENARIO_RESET=PASS"
Write-Output "SCENARIO=$Scenario"
Write-Output "CANDIDATE_ID=$($candidate.candidateId)"
Write-Output "APP_VISIBLE=YES"
Write-Output "EVIDENCE_ROOT=$evidenceRoot"
