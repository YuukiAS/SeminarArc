param(
    [string]$PackageName = "com.yuukias.seminararc.internal",
    [string]$Serial = "",
    [string[]]$Profiles = @("phone-normal", "phone-small", "phone-large-font", "phone-landscape", "tablet"),
    [string]$CandidateRoot = "",
    [string]$BaselineRoot = "",
    [switch]$CaptureOnly,
    [switch]$AcceptInitialBaseline,
    [switch]$UpdateBaseline,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptRoot
$androidHomeRoot = Join-Path $repoRoot ".android-user-home"
New-Item -ItemType Directory -Force -Path $androidHomeRoot | Out-Null
$env:USERPROFILE = $androidHomeRoot
$env:HOME = $androidHomeRoot
$env:ANDROID_SDK_HOME = $androidHomeRoot
. (Join-Path $scriptRoot "android-env.ps1")
$env:USERPROFILE = $androidHomeRoot
$env:HOME = $androidHomeRoot
$env:ANDROID_SDK_HOME = $androidHomeRoot

$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$blackbox = Join-Path $scriptRoot "run-blackbox-acceptance.ps1"
$comparator = Join-Path $scriptRoot "blackbox\Compare-VisualGolden.py"
$runId = "{0}-{1}" -f (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssfffZ"), $PID
if ([string]::IsNullOrWhiteSpace($CandidateRoot)) {
    $CandidateRoot = Join-Path $repoRoot "private\visual-regression\$runId"
} elseif (-not [System.IO.Path]::IsPathRooted($CandidateRoot)) {
    $CandidateRoot = Join-Path $repoRoot $CandidateRoot
}
if ([string]::IsNullOrWhiteSpace($BaselineRoot)) {
    $BaselineRoot = Join-Path $repoRoot "scripts\visual-baselines\0.5.x"
} elseif (-not [System.IO.Path]::IsPathRooted($BaselineRoot)) {
    $BaselineRoot = Join-Path $repoRoot $BaselineRoot
}

$profileCatalog = [ordered]@{
    "phone-normal" = [pscustomobject]@{
        size = "reset"; density = "reset"; fontScale = "1.0"; rotation = "0"
        scenarios = "B01,B02,B03,B04,B05,B06,B07,B08"
        captures = @(
            "B01/visual-empty-library", "B02/visual-new-seminar", "B02/visual-new-seminar-error",
            "B02/visual-seminar-detail", "B02/visual-populated-library",
            "B03/visual-active-session-empty", "B03/visual-active-session-populated",
            "B04/visual-reconstruction-processing", "B04/visual-reconstruction-long-ocr",
            "B05/visual-reference-review-empty",
            "B06/visual-transcript-review-empty", "B06/visual-transcript-review-populated",
            "B07/visual-formula-unavailable", "B07/visual-formula-ready", "B07/visual-export-document-picker",
            "B08/visual-delete-dialog"
        )
    }
    "phone-small" = [pscustomobject]@{
        size = "900x2000"; density = "420"; fontScale = "1.0"; rotation = "0"
        scenarios = "B01,B02,B04,B06,B07"
        captures = @(
            "B01/visual-empty-library", "B02/visual-new-seminar-error",
            "B04/visual-reconstruction-long-ocr", "B06/visual-transcript-review-populated",
            "B07/visual-formula-ready"
        )
    }
    "phone-large-font" = [pscustomobject]@{
        size = "reset"; density = "reset"; fontScale = "1.3"; rotation = "0"
        scenarios = "B01,B02,B04,B06,B07"
        captures = @(
            "B02/visual-new-seminar-error", "B04/visual-reconstruction-long-ocr",
            "B06/visual-transcript-review-populated", "B07/visual-formula-ready"
        )
    }
    "phone-landscape" = [pscustomobject]@{
        size = "reset"; density = "reset"; fontScale = "1.0"; rotation = "1"
        scenarios = "B01,B02,B04,B07"
        captures = @(
            "B02/visual-seminar-detail", "B04/visual-reconstruction-long-ocr",
            "B07/visual-formula-ready", "B07/visual-export-document-picker"
        )
    }
    "tablet" = [pscustomobject]@{
        size = "1600x2560"; density = "320"; fontScale = "1.0"; rotation = "0"
        scenarios = "B01,B02,B04,B06,B07"
        captures = @(
            "B02/visual-populated-library", "B04/visual-reconstruction-long-ocr",
            "B06/visual-transcript-review-populated", "B07/visual-formula-ready"
        )
    }
}

function Write-Status {
    param([string]$Message)
    Write-Host ("[{0}] {1}" -f (Get-Date).ToString("HH:mm:ss"), $Message)
}

function Invoke-Adb {
    param([string[]]$Arguments, [switch]$IgnoreExitCode)
    $output = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0 -and -not $IgnoreExitCode) {
        throw "adb command failed ($LASTEXITCODE): $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return @($output)
}

function Get-AdbValue {
    param([string[]]$Arguments)
    return ((Invoke-Adb -Arguments $Arguments) -join "`n").Trim()
}

function Get-DisplaySetting {
    param([string]$Kind)
    $raw = Get-AdbValue -Arguments @("shell", "wm", $Kind)
    $override = [regex]::Match($raw, "Override (?:size|density):\s*([^\r\n]+)")
    if ($override.Success) {
        return [pscustomobject]@{ mode = "value"; value = $override.Groups[1].Value.Trim() }
    }
    return [pscustomobject]@{ mode = "reset"; value = "reset" }
}

function Set-DisplayProfile {
    param($Profile)
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "accelerometer_rotation", "0") | Out-Null
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "user_rotation", $Profile.rotation) | Out-Null
    Invoke-Adb -Arguments @("shell", "wm", "size", $Profile.size) | Out-Null
    Invoke-Adb -Arguments @("shell", "wm", "density", $Profile.density) | Out-Null
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "font_scale", $Profile.fontScale) | Out-Null
    Start-Sleep -Seconds 3
}

function Restore-DisplaySettings {
    param($Original)
    Invoke-Adb -Arguments @("shell", "wm", "size", $Original.size.value) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "wm", "density", $Original.density.value) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "font_scale", $Original.fontScale) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "user_rotation", $Original.rotation) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "accelerometer_rotation", $Original.accelerometerRotation) -IgnoreExitCode | Out-Null
}

function Copy-ReviewedBaseline {
    $candidateManifestPath = Join-Path $CandidateRoot "manifest.json"
    if (-not (Test-Path -LiteralPath $candidateManifestPath)) {
        throw "Candidate manifest not found: $candidateManifestPath"
    }
    $candidateManifest = Get-Content -LiteralPath $candidateManifestPath -Raw | ConvertFrom-Json
    $existing = @(Get-ChildItem -LiteralPath $BaselineRoot -Recurse -File -Filter "*.png" -ErrorAction SilentlyContinue)
    if ($AcceptInitialBaseline -and $existing.Count -gt 0) {
        throw "Initial baseline acceptance refused because PNG baselines already exist."
    }
    if (-not $AcceptInitialBaseline -and -not $UpdateBaseline) {
        throw "Baseline writes require AcceptInitialBaseline or UpdateBaseline."
    }
    foreach ($capture in $candidateManifest.captures) {
        $source = Join-Path $CandidateRoot $capture.relativePath
        if (-not (Test-Path -LiteralPath $source)) {
            throw "Reviewed candidate is missing: $source"
        }
        $destination = Join-Path $BaselineRoot $capture.relativePath
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
        Copy-Item -LiteralPath $source -Destination $destination -Force
    }
    $baselineProfiles = @($candidateManifest.profileRuns | ForEach-Object {
        [ordered]@{
            profile = $_.profile
            scenarios = $_.scenarios
            size = $_.size
            density = $_.density
            fontScale = $_.fontScale
            rotation = $_.rotation
        }
    })
    $baselineCaptures = @($candidateManifest.captures | ForEach-Object {
        [ordered]@{
            profile = $_.profile
            scenario = $_.scenario
            label = $_.label
            relativePath = $_.relativePath
            width = $_.width
            height = $_.height
        }
    })
    $baselineManifest = [ordered]@{
        version = 1
        acceptedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        sourceRunId = $candidateManifest.runId
        initial = [bool]$AcceptInitialBaseline
        profiles = $baselineProfiles
        captures = $baselineCaptures
    }
    New-Item -ItemType Directory -Force -Path $BaselineRoot | Out-Null
    $baselineManifest | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -Path (Join-Path $BaselineRoot "manifest.json")
    Write-Status "Baseline accepted at $BaselineRoot"
}

if ($AcceptInitialBaseline -or $UpdateBaseline) {
    Copy-ReviewedBaseline
    return
}

$inventory = & $adb devices -l 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read ADB inventory."
}
$inventoryText = $inventory -join "`n"
if ([string]::IsNullOrWhiteSpace($Serial)) {
    $emulators = @($inventory | Where-Object { $_ -match '^(emulator-\d+)\s+device\b' } | ForEach-Object { $Matches[1] })
    if ($emulators.Count -ne 1) {
        throw "Expected exactly one online emulator; pass -Serial explicitly when multiple are online."
    }
    $Serial = $emulators[0]
}
if ($Serial -notmatch '^emulator-\d+$' -or $inventoryText -notmatch "(?m)^$([regex]::Escape($Serial))\s+device\b") {
    throw "Selected serial is not an online emulator: $Serial"
}

foreach ($profileName in $Profiles) {
    if (-not $profileCatalog.Contains($profileName)) {
        throw "Unknown profile '$profileName'. Valid profiles: $($profileCatalog.Keys -join ', ')."
    }
}

$original = [pscustomobject]@{
    size = Get-DisplaySetting -Kind "size"
    density = Get-DisplaySetting -Kind "density"
    fontScale = Get-AdbValue -Arguments @("shell", "settings", "get", "system", "font_scale")
    rotation = Get-AdbValue -Arguments @("shell", "settings", "get", "system", "user_rotation")
    accelerometerRotation = Get-AdbValue -Arguments @("shell", "settings", "get", "system", "accelerometer_rotation")
}

New-Item -ItemType Directory -Force -Path $CandidateRoot | Out-Null
$captures = New-Object System.Collections.Generic.List[object]
$profileRuns = New-Object System.Collections.Generic.List[object]
$comparisonFailures = 0

try {
    $profileIndex = 0
    foreach ($profileName in $Profiles) {
        $profile = $profileCatalog[$profileName]
        Write-Status "Apply visual profile $profileName"
        Set-DisplayProfile -Profile $profile
        $startedAt = Get-Date
        $blackboxParameters = @{
            PackageName = $PackageName
            Serial = $Serial
            Scenarios = @($profile.scenarios)
            PhotoAcquisition = "Import"
            CaptureVisualStates = $true
            ReferenceVisualOnly = $true
            SkipBuild = [bool]($SkipBuild -or $profileIndex -gt 0)
            SkipInstall = [bool]($SkipInstall -or $profileIndex -gt 0)
        }
        & $blackbox @blackboxParameters
        if ($LASTEXITCODE -ne 0) {
            throw "Black-box visual source run failed for profile $profileName."
        }
        $sourceRun = Get-ChildItem (Join-Path $repoRoot "private\blackbox-acceptance") -Directory |
            Where-Object { $_.LastWriteTime -ge $startedAt.AddSeconds(-2) -and (Test-Path (Join-Path $_.FullName "summary.json")) } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -eq $sourceRun) {
            throw "Unable to locate black-box evidence for profile $profileName."
        }
        $sourceSummary = Get-Content (Join-Path $sourceRun.FullName "summary.json") -Raw | ConvertFrom-Json
        if ($sourceSummary.status -ne "PASS") {
            throw "Black-box source status for $profileName was $($sourceSummary.status)."
        }
        $profileRuns.Add([ordered]@{
            profile = $profileName
            evidenceRoot = $sourceRun.FullName
            scenarios = $profile.scenarios
            size = $profile.size
            density = $profile.density
            fontScale = $profile.fontScale
            rotation = $profile.rotation
        }) | Out-Null
        foreach ($captureSpec in $profile.captures) {
            $parts = $captureSpec -split '/', 2
            $scenario = $parts[0]
            $label = $parts[1]
            $sourcePng = Join-Path $sourceRun.FullName "$scenario\$label.png"
            $sourceXml = Join-Path $sourceRun.FullName "$scenario\$label-ui.xml"
            if (-not (Test-Path -LiteralPath $sourcePng) -or -not (Test-Path -LiteralPath $sourceXml)) {
                throw "Visual capture missing for $profileName/$captureSpec."
            }
            $relativePng = "$profileName\$scenario-$label.png"
            $relativeXml = "$profileName\$scenario-$label-ui.xml"
            $candidatePng = Join-Path $CandidateRoot $relativePng
            $candidateXml = Join-Path $CandidateRoot $relativeXml
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $candidatePng) | Out-Null
            Copy-Item -LiteralPath $sourcePng -Destination $candidatePng -Force
            Copy-Item -LiteralPath $sourceXml -Destination $candidateXml -Force
            $image = [System.Drawing.Image]::FromFile($candidatePng)
            try {
                $width = $image.Width
                $height = $image.Height
            } finally {
                $image.Dispose()
            }
            $captureRecord = [ordered]@{
                profile = $profileName
                scenario = $scenario
                label = $label
                relativePath = $relativePng
                uiTreeRelativePath = $relativeXml
                width = $width
                height = $height
                sourceEvidenceRoot = $sourceRun.FullName
                comparison = $null
            }
            $captures.Add($captureRecord) | Out-Null

            $baseline = Join-Path $BaselineRoot $relativePng
            if (-not $CaptureOnly -and (Test-Path -LiteralPath $baseline)) {
                $diff = Join-Path $CandidateRoot "diff\$relativePng"
                $json = & python $comparator --baseline $baseline --candidate $candidatePng --diff $diff
                $comparison = $json | ConvertFrom-Json
                $captureRecord.comparison = $comparison
                if ($LASTEXITCODE -ne 0) {
                    $comparisonFailures += 1
                }
            } elseif (-not $CaptureOnly) {
                $captureRecord.comparison = [ordered]@{ status = "MISSING_BASELINE" }
                $comparisonFailures += 1
            }
        }
        $profileIndex += 1
    }
} finally {
    Write-Status "Restore original Emulator display settings"
    Restore-DisplaySettings -Original $original
}

$manifest = [ordered]@{
    version = 1
    runId = $runId
    status = if ($comparisonFailures -eq 0) { if ($CaptureOnly) { "CAPTURED" } else { "PASS" } } else { "FAIL" }
    serial = $Serial
    package = $PackageName
    baselineRoot = $BaselineRoot
    candidateRoot = $CandidateRoot
    captureOnly = [bool]$CaptureOnly
    originalDisplay = $original
    profileRuns = @($profileRuns.ToArray())
    captures = @($captures.ToArray())
    comparisonFailures = $comparisonFailures
}
$manifest | ConvertTo-Json -Depth 12 | Set-Content -Encoding UTF8 -Path (Join-Path $CandidateRoot "manifest.json")
Write-Status "Visual manifest: $(Join-Path $CandidateRoot 'manifest.json')"
if ($comparisonFailures -gt 0 -and -not $CaptureOnly) {
    throw "$comparisonFailures visual comparison(s) failed or lacked a baseline."
}
