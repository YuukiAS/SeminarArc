param(
    [string]$Serial = "emulator-5558",
    [string[]]$Scenarios = @(),
    [Alias("SkipSnapshotBuild")]
    [switch]$SkipScenarioValidation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "gpt-work-acceptance-common.ps1")
Initialize-GptWorkAndroidEnvironment

$repoRoot = Get-GptWorkRepoRoot
$candidate = Test-GptWorkCandidateFile
$catalog = Get-GptWorkScenarioCatalog

$fixtureGenerator = Join-Path $scriptRoot "blackbox\New-GptWorkAcademicFixture.ps1"
$fixtureRoot = Join-Path $repoRoot "private\gpt-work-acceptance\fixtures\geometric-neural-fields"
& $fixtureGenerator -OutputRoot $fixtureRoot | Out-Null
& (Join-Path $scriptRoot "render-gpt-work-acceptance-parent.ps1") | Out-Null

$requested = New-Object System.Collections.Generic.List[string]
foreach ($value in $Scenarios) {
    foreach ($part in ($value -split ",")) {
        $name = $part.Trim().ToUpperInvariant()
        if ($name.Length -gt 0) { $requested.Add($name) }
    }
}
if ($requested.Count -eq 0) {
    foreach ($name in $catalog.Keys) { $requested.Add($name) }
}
foreach ($name in $requested) {
    if (-not $catalog.Contains($name)) { throw "Unknown scenario '$name'." }
}

$hostProfile = Get-GptWorkHostUserProfile
$acceptanceAndroidHome = $env:USERPROFILE
$env:USERPROFILE = $hostProfile
$env:HOME = $hostProfile
Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue
try {
    $emulator = Join-Path $env:ANDROID_HOME "emulator\emulator.exe"
    $avdList = Invoke-GptWorkProcess -FilePath $emulator -Arguments @("-list-avds") -TimeoutSeconds 45
    if (($avdList.Stdout -split "`r?`n") -notcontains $candidate.avdProfile) {
        $avdPath = Join-Path $repoRoot "private\gpt-work-acceptance\avd\$($candidate.avdProfile).avd"
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $avdPath) | Out-Null
        $avdManager = Join-Path $env:ANDROID_HOME "cmdline-tools\latest\bin\avdmanager.bat"
        $create = "no`n" | & $avdManager create avd --force --name $candidate.avdProfile --path $avdPath --package "system-images;android-36;google_apis;x86_64" --device "pixel_8" 2>&1
        if ($LASTEXITCODE -ne 0) { throw "AVD creation failed: $($create -join "`n")" }
    }

    $inventory = Get-GptWorkDeviceInventory
    $ready = @($inventory.Devices | Where-Object { $_.Serial -eq $Serial -and $_.State -eq "device" })
    if ($ready.Count -ne 1) {
        $arguments = @(
            "-avd", $candidate.avdProfile,
            "-port", $Serial.Substring($Serial.IndexOf("-") + 1),
            "-no-boot-anim", "-no-audio", "-no-snapshot-load", "-gpu", "auto",
            "-netdelay", "none", "-netspeed", "full"
        )
        Start-Process -FilePath $emulator -ArgumentList $arguments | Out-Null
    }
} finally {
    $env:USERPROFILE = $acceptanceAndroidHome
    $env:HOME = $acceptanceAndroidHome
    $env:ANDROID_SDK_HOME = $acceptanceAndroidHome
}

Wait-GptWorkBoot -Serial $Serial -TimeoutSeconds 240
$inventory = Assert-GptWorkEmulator -Serial $Serial
$avdName = (Invoke-GptWorkAdb -Serial $Serial -Arguments @("emu", "avd", "name") -TimeoutSeconds 30).Stdout -replace "OK", ""
if ($avdName.Trim() -ne $candidate.avdProfile) {
    throw "Serial $Serial is running AVD '$($avdName.Trim())', expected '$($candidate.avdProfile)'."
}

$preparationRoot = Join-Path $repoRoot ("private\gpt-work-acceptance\preparation\" + (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssfffZ"))
New-Item -ItemType Directory -Force -Path $preparationRoot | Out-Null
$prepared = New-Object System.Collections.Generic.List[object]

if (-not $SkipScenarioValidation) {
    foreach ($scenario in $requested) {
        Write-Host "Preparing $scenario ..."
        $resetScript = Join-Path $scriptRoot "reset-gpt-work-acceptance.ps1"
        $result = Invoke-GptWorkProcess -FilePath "powershell.exe" -Arguments @(
            "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $resetScript,
            "-Scenario", $scenario, "-Serial", $Serial, "-PrepareSnapshot"
        ) -TimeoutSeconds 2100 -IgnoreExitCode
        $logPath = Join-Path $preparationRoot "$scenario.log"
        ($result.Stdout + "`n" + $result.Stderr) | Set-Content -Encoding UTF8 -Path $logPath
        if ($result.ExitCode -ne 0 -or $result.Stdout -notmatch "SCENARIO_RESET=PASS") {
            throw "Failed to prepare $scenario. See $logPath"
        }
        $prepared.Add([ordered]@{ scenario = $scenario; stateSource = $catalog[$scenario].stateSource; log = $logPath })
    }
}

$manifest = [ordered]@{
    preparedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    candidateId = $candidate.candidateId
    candidateCommit = $candidate.gitCommit
    apkSha256 = $candidate.apkSha256
    avdProfile = $candidate.avdProfile
    emulatorSerial = $Serial
    androidApi = $candidate.androidApi
    fixtureManifest = Join-Path $fixtureRoot "fixture-manifest.json"
    deviceInventory = $inventory.Raw
    physicalDeviceTargeted = $false
    scenarios = @($catalog.GetEnumerator() | ForEach-Object {
        [ordered]@{
            id = $_.Key
            stateSource = $_.Value.stateSource
            roles = $_.Value.roles
            expectedText = $_.Value.expectedText
            builderScenarios = $_.Value.builderScenarios
        }
    })
    prepared = $prepared.ToArray()
}
$manifestPath = Join-Path $preparationRoot "preparation-manifest.json"
$manifest | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -Path $manifestPath

Write-Output "ACCEPTANCE_PREPARATION=PASS"
Write-Output "CANDIDATE_ID=$($candidate.candidateId)"
Write-Output "SCENARIO_COUNT=$($requested.Count)"
Write-Output "AVD_PROFILE=$($candidate.avdProfile)"
Write-Output "PREPARATION_MANIFEST=$manifestPath"
