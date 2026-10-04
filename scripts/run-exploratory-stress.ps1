param(
    [string]$PackageName = "com.yuukias.seminararc.internal",
    [string]$Serial = "",
    [int[]]$Seeds = @(505001, 505002, 505003),
    [ValidateRange(1, 1000)]
    [int]$EventCount = 750,
    [ValidateRange(0, 1000)]
    [int]$ThrottleMs = 35,
    [ValidateSet("Normal", "Small")]
    [string]$DisplayProfile = "Normal",
    [string]$EvidenceRoot = "",
    [string]$ApkPath = "",
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
if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $runId = "stress-{0}-{1}" -f (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssfffZ"), $PID
    $EvidenceRoot = Join-Path $repoRoot "private\exploratory-stress\$runId"
} elseif (-not [System.IO.Path]::IsPathRooted($EvidenceRoot)) {
    $EvidenceRoot = Join-Path $repoRoot $EvidenceRoot
}
New-Item -ItemType Directory -Force -Path $EvidenceRoot | Out-Null

function Write-Status {
    param([string]$Message)
    Write-Host ("[{0}] {1}" -f (Get-Date).ToString("HH:mm:ss"), $Message)
}

function Invoke-ProcessText {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [int]$TimeoutSeconds = 120,
        [switch]$IgnoreExitCode
    )
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $FilePath
    $psi.WorkingDirectory = $repoRoot
    foreach ($argument in $Arguments) {
        [void]$psi.ArgumentList.Add($argument)
    }
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($psi)
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        try { $process.Kill($true) } catch {}
        throw "Command timed out after ${TimeoutSeconds}s: $FilePath $($Arguments -join ' ')"
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0 -and -not $IgnoreExitCode) {
        throw "Command failed ($($process.ExitCode)): $FilePath $($Arguments -join ' ')`n$stdout`n$stderr"
    }
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Stdout = $stdout
        Stderr = $stderr
    }
}

function Invoke-Adb {
    param(
        [string[]]$Arguments,
        [int]$TimeoutSeconds = 120,
        [switch]$IgnoreExitCode
    )
    return Invoke-ProcessText -FilePath $adb -Arguments (@("-s", $Serial) + $Arguments) -TimeoutSeconds $TimeoutSeconds -IgnoreExitCode:$IgnoreExitCode
}

function Get-AdbValue {
    param([string[]]$Arguments)
    return (Invoke-Adb -Arguments $Arguments).Stdout.Trim()
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

function Restore-DisplaySettings {
    param($Original)
    Invoke-Adb -Arguments @("shell", "wm", "size", $Original.size.value) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "wm", "density", $Original.density.value) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "font_scale", $Original.fontScale) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "user_rotation", $Original.rotation) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "settings", "put", "system", "accelerometer_rotation", $Original.accelerometerRotation) -IgnoreExitCode | Out-Null
}

function Save-TextEvidence {
    param([string]$Name, [string]$Content)
    Set-Content -LiteralPath (Join-Path $EvidenceRoot $Name) -Value $Content -Encoding UTF8
}

function Capture-Screenshot {
    param([int]$Seed)
    $devicePath = "/sdcard/Download/seminararc-stress-$Seed.png"
    $localPath = Join-Path $EvidenceRoot "seed-$Seed-recovery.png"
    Invoke-Adb -Arguments @("shell", "screencap", "-p", $devicePath) | Out-Null
    Invoke-Adb -Arguments @("pull", $devicePath, $localPath) | Out-Null
    Invoke-Adb -Arguments @("shell", "rm", "-f", $devicePath) | Out-Null
}

function Assert-SelectedEmulator {
    $inventory = Invoke-ProcessText -FilePath $adb -Arguments @("devices", "-l") -TimeoutSeconds 30
    Save-TextEvidence -Name "device-inventory.txt" -Content $inventory.Stdout
    $inventoryLines = @($inventory.Stdout -split "`r?`n")
    if ([string]::IsNullOrWhiteSpace($Serial)) {
        $onlineEmulators = @($inventoryLines | Where-Object { $_ -match '^(emulator-\d+)\s+device\b' } | ForEach-Object { $Matches[1] })
        if ($onlineEmulators.Count -ne 1) {
            throw "Expected exactly one online emulator when Serial is omitted."
        }
        $script:Serial = $onlineEmulators[0]
    }
    if ($Serial -notmatch '^emulator-\d+$') {
        throw "Stress target must be an emulator serial: $Serial"
    }
    if ($inventory.Stdout -notmatch "(?m)^$([regex]::Escape($Serial))\s+device\b") {
        throw "Selected emulator is not online: $Serial"
    }
    Write-Status "Selected emulator serial: $Serial"
}

Assert-SelectedEmulator

$originalDisplay = [pscustomobject]@{
    size = Get-DisplaySetting -Kind "size"
    density = Get-DisplaySetting -Kind "density"
    fontScale = Get-AdbValue -Arguments @("shell", "settings", "get", "system", "font_scale")
    rotation = Get-AdbValue -Arguments @("shell", "settings", "get", "system", "user_rotation")
    accelerometerRotation = Get-AdbValue -Arguments @("shell", "settings", "get", "system", "accelerometer_rotation")
}

if ([string]::IsNullOrWhiteSpace($ApkPath)) {
    $ApkPath = Join-Path $repoRoot "app\build\outputs\apk\internal\app-internal.apk"
} elseif (-not [System.IO.Path]::IsPathRooted($ApkPath)) {
    $ApkPath = Join-Path $repoRoot $ApkPath
}

$seedResults = New-Object System.Collections.Generic.List[object]
$overallStatus = "PASS"
$startedAt = Get-Date

try {
    if ($DisplayProfile -eq "Small") {
        Write-Status "Apply bounded small-screen profile"
        Invoke-Adb -Arguments @("shell", "settings", "put", "system", "accelerometer_rotation", "0") | Out-Null
        Invoke-Adb -Arguments @("shell", "settings", "put", "system", "user_rotation", "0") | Out-Null
        Invoke-Adb -Arguments @("shell", "wm", "size", "900x2000") | Out-Null
        Invoke-Adb -Arguments @("shell", "wm", "density", "420") | Out-Null
        Invoke-Adb -Arguments @("shell", "settings", "put", "system", "font_scale", "1.0") | Out-Null
        Start-Sleep -Seconds 3
    }

    if (-not $SkipBuild) {
        Write-Status "Build internal APK"
        Invoke-ProcessText -FilePath (Join-Path $repoRoot "gradlew.bat") -Arguments @(":app:assembleInternal", "--console=plain") -TimeoutSeconds 1200 | Out-Null
    }
    if (-not (Test-Path -LiteralPath $ApkPath)) {
        throw "Internal APK not found: $ApkPath"
    }
    if (-not $SkipInstall) {
        Write-Status "Install internal APK on $Serial"
        Invoke-Adb -Arguments @("install", "-r", $ApkPath) -TimeoutSeconds 180 | Out-Null
    }

    foreach ($seed in $Seeds) {
        $seedStartedAt = Get-Date
        Write-Status "Seed ${seed}: reset synthetic state"
        Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) | Out-Null
        Invoke-Adb -Arguments @("logcat", "-b", "all", "-c") | Out-Null

        $monkeyArguments = @(
            "shell", "monkey",
            "-p", $PackageName,
            "-s", "$seed",
            "--throttle", "$ThrottleMs",
            "--pct-syskeys", "0",
            "--pct-appswitch", "0",
            "--pct-anyevent", "0",
            "-v", "$EventCount"
        )
        $replayCommand = "$adb -s $Serial $($monkeyArguments -join ' ')"
        Write-Status "Seed ${seed}: inject $EventCount package-scoped events"
        $monkey = Invoke-Adb -Arguments $monkeyArguments -TimeoutSeconds ([math]::Max(300, [int](($EventCount * ($ThrottleMs + 300)) / 1000))) -IgnoreExitCode
        Save-TextEvidence -Name "seed-$seed-monkey.txt" -Content ($monkey.Stdout + "`n" + $monkey.Stderr)

        $crashLog = Invoke-Adb -Arguments @("logcat", "-d", "-b", "crash", "-v", "threadtime") -IgnoreExitCode
        Save-TextEvidence -Name "seed-$seed-crash-logcat.txt" -Content ($crashLog.Stdout + "`n" + $crashLog.Stderr)
        $allLog = Invoke-Adb -Arguments @("logcat", "-d", "-b", "main", "-b", "system", "-b", "events", "-v", "threadtime") -TimeoutSeconds 120 -IgnoreExitCode
        Save-TextEvidence -Name "seed-$seed-logcat.txt" -Content ($allLog.Stdout + "`n" + $allLog.Stderr)

        $combinedEvidence = $monkey.Stdout + "`n" + $monkey.Stderr + "`n" + $crashLog.Stdout + "`n" + $allLog.Stdout
        $packagePattern = [regex]::Escape($PackageName)
        $hasCrash = $combinedEvidence -match "(?is)(FATAL EXCEPTION.*$packagePattern|Process:\s*$packagePattern|CRASH:\s*$packagePattern|ANR in\s+$packagePattern|am_anr.*$packagePattern|Monkey aborted due to error)"
        $eventsInjected = [regex]::Match($monkey.Stdout, "Events injected:\s*(\d+)")
        $injectedCount = if ($eventsInjected.Success) { [int]$eventsInjected.Groups[1].Value } else { 0 }

        Write-Status "Seed ${seed}: force-stop and verify recovery"
        Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) | Out-Null
        # Monkey can leave an Android-owned picker/resolver in front of the app.
        # Dismiss that external activity, then return to HOME before relaunch so
        # recovery is evaluated against the package under test.
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "4") | Out-Null
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "3") | Out-Null
        Start-Sleep -Seconds 1
        $launch = Invoke-Adb -Arguments @("shell", "monkey", "-p", $PackageName, "-c", "android.intent.category.LAUNCHER", "1") -IgnoreExitCode
        Start-Sleep -Seconds 5
        $appProcessId = Get-AdbValue -Arguments @("shell", "pidof", $PackageName)
        $uiTree = Invoke-Adb -Arguments @("exec-out", "uiautomator", "dump", "/dev/tty") -TimeoutSeconds 60 -IgnoreExitCode
        Save-TextEvidence -Name "seed-$seed-recovery-ui.xml" -Content $uiTree.Stdout
        Capture-Screenshot -Seed $seed
        $recovered = $launch.ExitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($appProcessId) -and $uiTree.Stdout -match $packagePattern

        $status = if ($monkey.ExitCode -eq 0 -and -not $hasCrash -and $injectedCount -eq $EventCount -and $recovered) { "PASS" } else { "FAIL" }
        if ($status -ne "PASS") {
            $overallStatus = "FAIL"
        }
        $seedResults.Add([ordered]@{
            seed = $seed
            status = $status
            eventLimit = $EventCount
            eventsInjected = $injectedCount
            throttleMs = $ThrottleMs
            monkeyExitCode = $monkey.ExitCode
            crashOrAnrDetected = $hasCrash
            recoveredAfterForceStop = $recovered
            elapsedSeconds = [math]::Round(((Get-Date) - $seedStartedAt).TotalSeconds, 2)
            replayCommand = $replayCommand
        }) | Out-Null

        Write-Status "Seed ${seed}: clean app-owned synthetic state"
        Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) | Out-Null
    }
} finally {
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) -IgnoreExitCode | Out-Null
    if ($DisplayProfile -eq "Small") {
        Write-Status "Restore original display settings"
        Restore-DisplaySettings -Original $originalDisplay
    }
    $postInventory = Invoke-ProcessText -FilePath $adb -Arguments @("devices", "-l") -TimeoutSeconds 30 -IgnoreExitCode
    Save-TextEvidence -Name "device-inventory-postflight.txt" -Content $postInventory.Stdout
}

$summary = [ordered]@{
    version = 1
    status = $overallStatus
    package = $PackageName
    emulatorSerial = $Serial
    displayProfile = $DisplayProfile
    eventLimitPerSeed = $EventCount
    totalEventLimit = $EventCount * $Seeds.Count
    throttleMs = $ThrottleMs
    evidenceRoot = $EvidenceRoot
    elapsedSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 2)
    seedResults = @($seedResults.ToArray())
}
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $EvidenceRoot "summary.json") -Encoding UTF8
Write-Status "Summary: $(Join-Path $EvidenceRoot 'summary.json')"
Write-Status "Overall: $overallStatus"
if ($overallStatus -ne "PASS") {
    throw "One or more fixed-seed stress runs failed."
}
