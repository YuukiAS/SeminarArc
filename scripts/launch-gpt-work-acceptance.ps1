param(
    [ValidateSet(
        "S00_FRESH", "S01_SEMINAR_DRAFT", "S02_COMPLETED_CAPTURE",
        "S03_RECONSTRUCTION_READY", "S04_RESEARCH_READY", "S05_TRANSCRIPT_READY",
        "S06_FORMULA_EXPORT_READY", "S07_ERROR_RECOVERY", "S08_LONG_CONTENT"
    )]
    [string]$Scenario = "S00_FRESH",
    [string]$Serial = "emulator-5558",
    [switch]$VerifyInstalledHash
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "gpt-work-acceptance-common.ps1")
Initialize-GptWorkAndroidEnvironment

$repoRoot = Get-GptWorkRepoRoot
$candidate = Test-GptWorkCandidateFile
$inventory = Get-GptWorkDeviceInventory
$ready = @($inventory.Devices | Where-Object { $_.Serial -eq $Serial -and $_.State -eq "device" })

if ($ready.Count -ne 1) {
    if ($Serial -ne $candidate.emulatorSerial) {
        throw "Only the approved acceptance serial $($candidate.emulatorSerial) can be auto-started."
    }
    $emulator = Join-Path $env:ANDROID_HOME "emulator\emulator.exe"
    $arguments = @(
        "-avd", $candidate.avdProfile,
        "-port", $Serial.Substring($Serial.IndexOf("-") + 1),
        "-no-boot-anim", "-no-audio", "-no-snapshot-load", "-gpu", "auto",
        "-netdelay", "none", "-netspeed", "full"
    )
    $acceptanceAndroidHome = $env:USERPROFILE
    $hostUserProfile = Get-GptWorkHostUserProfile
    $env:USERPROFILE = $hostUserProfile
    $env:HOME = $hostUserProfile
    Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue
    try {
        Start-Process -FilePath $emulator -ArgumentList $arguments | Out-Null
    } finally {
        $env:USERPROFILE = $acceptanceAndroidHome
        $env:HOME = $acceptanceAndroidHome
        $env:ANDROID_SDK_HOME = $acceptanceAndroidHome
    }
    Wait-GptWorkBoot -Serial $Serial -TimeoutSeconds 240
}

$inventory = Assert-GptWorkEmulator -Serial $Serial
$nonEmulators = @($inventory.Devices | Where-Object { $_.Serial -notlike "emulator-*" })
$inventoryRoot = Join-Path $repoRoot "private\gpt-work-acceptance\inventory"
New-Item -ItemType Directory -Force -Path $inventoryRoot | Out-Null
$inventory.Raw | Set-Content -Encoding UTF8 -Path (Join-Path $inventoryRoot "latest-adb-devices.txt")

$resetScript = Join-Path $scriptRoot "reset-gpt-work-acceptance.ps1"
$resetArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $resetScript, "-Scenario", $Scenario, "-Serial", $Serial)
if ($VerifyInstalledHash) { $resetArgs += "-VerifyInstalledHash" }
$reset = Invoke-GptWorkProcess -FilePath "powershell.exe" -Arguments $resetArgs -TimeoutSeconds 300 -IgnoreExitCode
if ($reset.ExitCode -ne 0 -or $reset.Stdout -notmatch "SCENARIO_RESET=PASS") {
    throw "Acceptance reset failed.`n$($reset.Stdout)`n$($reset.Stderr)"
}

$identity = Get-GptWorkInstalledIdentity -Serial $Serial -VerifyHash:$VerifyInstalledHash
$runId = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssfffZ")
$outputRoot = Join-Path $repoRoot "private\gpt-work-acceptance\campaigns\$runId"
$parentRoot = Join-Path $outputRoot "parent"
New-Item -ItemType Directory -Force -Path $parentRoot | Out-Null
$handoffEvidence = Save-GptWorkUiEvidence -Serial $Serial -OutputRoot $parentRoot -Label "launch"
[xml]$ui = Get-Content -LiteralPath $handoffEvidence.UiXml
if (@($ui.SelectNodes("//node[@package='$($candidate.applicationId)']")).Count -eq 0) {
    throw "Candidate App is not visible after launch."
}

$handoff = [ordered]@{
    acceptanceEnvironment = "READY"
    candidateId = $candidate.candidateId
    candidateCommit = $candidate.gitCommit
    apkSha256 = $candidate.apkSha256
    installedApkSha256 = $identity.installedApkSha256
    emulatorProfile = $candidate.avdProfile
    emulatorSerial = $Serial
    androidApi = $candidate.androidApi
    appVisible = "YES"
    computerUseTarget = "Visible Android Emulator window: $($candidate.avdProfile) ($Serial)"
    appiumOrUiBridge = "ADB_UIAUTOMATOR_SEMANTIC_BRIDGE"
    resetControl = "$resetScript -Scenario S00_FRESH -Serial $Serial (replace S00_FRESH with an approved S01-S08 id)"
    outputRoot = $outputRoot
    protectedPhysicalDeviceTargeted = "NO"
    nonEmulatorInventoryCount = $nonEmulators.Count
    workRuntimeBinding = "RUNTIME_CHECK_REQUIRED"
}
$handoff | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -Path (Join-Path $parentRoot "handoff.json")

Write-Output "ACCEPTANCE_ENVIRONMENT=READY"
Write-Output "CANDIDATE_ID=$($candidate.candidateId)"
Write-Output "EMULATOR_PROFILE=$($candidate.avdProfile)"
Write-Output "APP_VISIBLE=YES"
Write-Output "COMPUTER_USE_TARGET=Visible Android Emulator window $($candidate.avdProfile) ($Serial)"
Write-Output "APPIUM_OR_UI_BRIDGE=ADB_UIAUTOMATOR_SEMANTIC_BRIDGE"
Write-Output "RESET_CONTROL=$resetScript -Scenario S00_FRESH -Serial $Serial"
Write-Output "OUTPUT_ROOT=$outputRoot"
Write-Output "WORK_RUNTIME_BINDING=RUNTIME_CHECK_REQUIRED"
