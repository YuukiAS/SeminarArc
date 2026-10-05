Set-StrictMode -Version Latest

function Get-GptWorkRepoRoot {
    return (Split-Path -Parent $PSScriptRoot)
}

function Get-GptWorkHostUserProfile {
    return [Environment]::GetFolderPath([Environment+SpecialFolder]::UserProfile)
}

function Get-GptWorkSha256 {
    param([Parameter(Mandatory)][string]$Path)
    $stream = [IO.File]::OpenRead($Path)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $sha.ComputeHash($stream)
        return ([BitConverter]::ToString($bytes).Replace("-", ""))
    } finally {
        $sha.Dispose()
        $stream.Dispose()
    }
}

function Get-GptWorkCandidate {
    $repoRoot = Get-GptWorkRepoRoot
    return [ordered]@{
        candidateId = "seminararc-64b5321-56d99be5-api36"
        gitCommit = "64b5321893a8d79399c38cf85b0cf4256ffb0d63"
        apkPath = "private/gpt-work-acceptance/candidates/seminararc-64b5321-56d99be5-api36/SeminarArc-0.5.0-alpha.2-internal.apk"
        apkAbsolutePath = Join-Path $repoRoot "private\gpt-work-acceptance\candidates\seminararc-64b5321-56d99be5-api36\SeminarArc-0.5.0-alpha.2-internal.apk"
        apkSha256 = "56D99BE575766823E92BB84E86E2AD6A09F4F4124614423953C5BD9F2E1CE8E3"
        applicationId = "com.yuukias.seminararc.internal"
        versionName = "0.5.0-alpha.2"
        versionCode = 50002
        signerFingerprintSha256 = "7555BF92FB796C6209F353F66C156C6F65E0312F18EE918557B572014B2DB28A"
        buildTimeUtc = "2026-10-05T01:55:41.1861407Z"
        roomSchemaVersion = 7
        avdProfile = "SeminarArc_GPT_Work_API36"
        androidApi = 36
        emulatorSerial = "emulator-5558"
    }
}

function Initialize-GptWorkAndroidEnvironment {
    $repoRoot = Get-GptWorkRepoRoot
    $env:JAVA_HOME = "D:\Code\_jdks\jdk-17.0.20.1+1"
    $env:ANDROID_HOME = "D:\Android\Sdk"
    $env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
    $androidHome = Join-Path $repoRoot ".android-user-home"
    New-Item -ItemType Directory -Force -Path $androidHome | Out-Null
    $env:USERPROFILE = $androidHome
    $env:HOME = $androidHome
    $env:ANDROID_SDK_HOME = $androidHome
    Remove-Item Env:ANDROID_USER_HOME -ErrorAction SilentlyContinue
    $toolPaths = @(
        "$env:JAVA_HOME\bin",
        "$env:ANDROID_HOME\platform-tools",
        "$env:ANDROID_HOME\build-tools\36.0.0",
        "$env:ANDROID_HOME\emulator",
        "$env:ANDROID_HOME\cmdline-tools\latest\bin"
    )
    $env:Path = (($toolPaths + ($env:Path -split ";")) | Select-Object -Unique) -join ";"
}

function Join-GptWorkArguments {
    param([string[]]$Arguments)
    $quoted = foreach ($argument in $Arguments) {
        if ($argument -match '^[A-Za-z0-9_./:=@%+\-,\\]+$') {
            $argument
        } else {
            '"' + ($argument -replace '(\\*)"', '$1$1\"' -replace '(\\+)$', '$1$1') + '"'
        }
    }
    return ($quoted -join " ")
}

function Invoke-GptWorkProcess {
    param(
        [Parameter(Mandatory)][string]$FilePath,
        [string[]]$Arguments = @(),
        [int]$TimeoutSeconds = 60,
        [switch]$IgnoreExitCode,
        [string]$WorkingDirectory = ""
    )
    if ([string]::IsNullOrWhiteSpace($WorkingDirectory)) {
        $WorkingDirectory = Get-GptWorkRepoRoot
    }
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $FilePath
    $psi.Arguments = Join-GptWorkArguments $Arguments
    $psi.WorkingDirectory = $WorkingDirectory
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [Diagnostics.Process]::Start($psi)
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
    return [pscustomobject]@{ ExitCode = $process.ExitCode; Stdout = $stdout; Stderr = $stderr }
}

function Get-GptWorkAdbPath {
    return (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
}

function Invoke-GptWorkAdbRaw {
    param([string[]]$Arguments, [int]$TimeoutSeconds = 60, [switch]$IgnoreExitCode)
    return Invoke-GptWorkProcess -FilePath (Get-GptWorkAdbPath) -Arguments $Arguments -TimeoutSeconds $TimeoutSeconds -IgnoreExitCode:$IgnoreExitCode
}

function Invoke-GptWorkAdb {
    param(
        [Parameter(Mandatory)][string]$Serial,
        [string[]]$Arguments,
        [int]$TimeoutSeconds = 60,
        [switch]$IgnoreExitCode
    )
    if ($Serial -notlike "emulator-*") {
        throw "Refusing non-emulator target '$Serial'."
    }
    return Invoke-GptWorkAdbRaw -Arguments (@("-s", $Serial) + $Arguments) -TimeoutSeconds $TimeoutSeconds -IgnoreExitCode:$IgnoreExitCode
}

function Get-GptWorkDeviceInventory {
    $result = Invoke-GptWorkAdbRaw -Arguments @("devices", "-l") -TimeoutSeconds 30
    $devices = New-Object System.Collections.Generic.List[object]
    foreach ($line in ($result.Stdout -split "`r?`n")) {
        if ($line -match '^(\S+)\s+(\S+)(?:\s+(.*))?$' -and $Matches[1] -ne "List") {
            $devices.Add([pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2]; Detail = $Matches[3] })
        }
    }
    return [pscustomobject]@{ Raw = $result.Stdout.Trim(); Devices = $devices.ToArray() }
}

function Assert-GptWorkEmulator {
    param([Parameter(Mandatory)][string]$Serial)
    if ($Serial -notlike "emulator-*") { throw "Only emulator-* targets are permitted." }
    $inventory = Get-GptWorkDeviceInventory
    $match = @($inventory.Devices | Where-Object { $_.Serial -eq $Serial -and $_.State -eq "device" })
    if ($match.Count -ne 1) { throw "Approved emulator '$Serial' is not present in device state." }
    return $inventory
}

function Wait-GptWorkBoot {
    param([Parameter(Mandatory)][string]$Serial, [int]$TimeoutSeconds = 180)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $state = Invoke-GptWorkAdbRaw -Arguments @("-s", $Serial, "get-state") -TimeoutSeconds 10 -IgnoreExitCode
        if ($state.ExitCode -eq 0 -and $state.Stdout.Trim() -eq "device") {
            $boot = Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "getprop", "sys.boot_completed") -TimeoutSeconds 10 -IgnoreExitCode
            if ($boot.ExitCode -eq 0 -and $boot.Stdout.Trim() -eq "1") { return }
        }
        Start-Sleep -Seconds 3
    } while ((Get-Date) -lt $deadline)
    throw "Emulator '$Serial' did not complete boot within ${TimeoutSeconds}s."
}

function Test-GptWorkCandidateFile {
    $candidate = Get-GptWorkCandidate
    if (-not (Test-Path -LiteralPath $candidate.apkAbsolutePath)) {
        throw "Frozen APK is missing: $($candidate.apkAbsolutePath)"
    }
    $actualHash = (Get-GptWorkSha256 -Path $candidate.apkAbsolutePath).ToUpperInvariant()
    if ($actualHash -ne $candidate.apkSha256) {
        throw "Frozen APK hash mismatch. Expected $($candidate.apkSha256), got $actualHash."
    }
    $git = Invoke-GptWorkProcess -FilePath "git.exe" -Arguments @("cat-file", "-e", "$($candidate.gitCommit)^{commit}") -TimeoutSeconds 30 -IgnoreExitCode
    if ($git.ExitCode -ne 0) { throw "Candidate commit does not exist locally: $($candidate.gitCommit)" }
    return $candidate
}

function Install-GptWorkCandidate {
    param([Parameter(Mandatory)][string]$Serial)
    $candidate = Test-GptWorkCandidateFile
    Assert-GptWorkEmulator -Serial $Serial | Out-Null
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("install", "-r", $candidate.apkAbsolutePath) -TimeoutSeconds 180 | Out-Null
}

function Get-GptWorkInstalledIdentity {
    param([Parameter(Mandatory)][string]$Serial, [switch]$VerifyHash)
    $candidate = Get-GptWorkCandidate
    $dump = Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "dumpsys", "package", $candidate.applicationId) -TimeoutSeconds 45
    $versionNameMatch = [regex]::Match($dump.Stdout, 'versionName=([^\r\n]+)')
    $versionCodeMatch = [regex]::Match($dump.Stdout, 'versionCode=(\d+)')
    if (-not $versionNameMatch.Success -or -not $versionCodeMatch.Success) {
        throw "Installed package identity could not be read for $($candidate.applicationId)."
    }
    $installedHash = "NOT_CHECKED"
    if ($VerifyHash) {
        $pathResult = Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "pm", "path", $candidate.applicationId) -TimeoutSeconds 30
        $remotePath = ($pathResult.Stdout -split "`r?`n" | Where-Object { $_ -like "package:*base.apk*" } | Select-Object -First 1).Substring(8).Trim()
        $hashResult = Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "sha256sum", $remotePath) -TimeoutSeconds 180
        $hashMatch = [regex]::Match($hashResult.Stdout, '^([0-9a-fA-F]{64})\s')
        if (-not $hashMatch.Success) { throw "Installed APK SHA-256 could not be parsed." }
        $installedHash = $hashMatch.Groups[1].Value.ToUpperInvariant()
    }
    return [pscustomobject]@{
        package = $candidate.applicationId
        versionName = $versionNameMatch.Groups[1].Value.Trim()
        versionCode = [int]$versionCodeMatch.Groups[1].Value
        installedApkSha256 = $installedHash
    }
}

function Start-GptWorkApp {
    param([Parameter(Mandatory)][string]$Serial)
    $candidate = Get-GptWorkCandidate
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "input", "keyevent", "224") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "wm", "dismiss-keyguard") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "monkey", "-p", $candidate.applicationId, "-c", "android.intent.category.LAUNCHER", "1") -TimeoutSeconds 45 | Out-Null
    Start-Sleep -Seconds 2
}

function Save-GptWorkUiEvidence {
    param(
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$OutputRoot,
        [string]$Label = "current"
    )
    New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null
    $deviceXml = "/sdcard/gpt-work-$Label.xml"
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("shell", "uiautomator", "dump", $deviceXml) -TimeoutSeconds 45 | Out-Null
    $xmlPath = Join-Path $OutputRoot "$Label-ui.xml"
    Invoke-GptWorkAdb -Serial $Serial -Arguments @("pull", $deviceXml, $xmlPath) -TimeoutSeconds 45 | Out-Null
    $pngPath = Join-Path $OutputRoot "$Label-screen.png"
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = Get-GptWorkAdbPath
    $psi.Arguments = Join-GptWorkArguments @("-s", $Serial, "exec-out", "screencap", "-p")
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [Diagnostics.Process]::Start($psi)
    $stream = [IO.File]::Open($pngPath, [IO.FileMode]::Create, [IO.FileAccess]::Write)
    try {
        $process.StandardOutput.BaseStream.CopyTo($stream)
        if (-not $process.WaitForExit(45000)) { try { $process.Kill($true) } catch {}; throw "Screenshot timed out." }
        if ($process.ExitCode -ne 0) { throw "Screenshot failed: $($process.StandardError.ReadToEnd())" }
    } finally { $stream.Dispose() }
    return [pscustomobject]@{ UiXml = $xmlPath; Screenshot = $pngPath }
}

function Get-GptWorkScenarioCatalog {
    return [ordered]@{
        S00_FRESH = [ordered]@{ stateSource = "UI_RECIPE_B01"; roles = @("P01", "P08"); expectedText = "Your seminar library is empty"; builderScenarios = @("B01") }
        S01_SEMINAR_DRAFT = [ordered]@{ stateSource = "UI_RECIPE_B01_B02"; roles = @("P02"); expectedText = "Geometry"; builderScenarios = @("B01", "B02") }
        S02_COMPLETED_CAPTURE = [ordered]@{ stateSource = "UI_RECIPE_B01_B02_B03"; roles = @("P02", "P05"); expectedText = "This seminar is completed"; builderScenarios = @("B01", "B02", "B03") }
        S03_RECONSTRUCTION_READY = [ordered]@{ stateSource = "UI_RECIPE_B04"; roles = @("P02", "P04"); expectedText = "Reconstruction"; builderScenarios = @("B04") }
        S04_RESEARCH_READY = [ordered]@{ stateSource = "UI_RECIPE_B04_B05"; roles = @("P02", "P07"); expectedText = "References and Brief"; builderScenarios = @("B04", "B05") }
        S05_TRANSCRIPT_READY = [ordered]@{ stateSource = "UI_RECIPE_B04_B06"; roles = @("P03", "P05"); expectedText = "Transcripts"; builderScenarios = @("B04", "B06") }
        S06_FORMULA_EXPORT_READY = [ordered]@{ stateSource = "UI_RECIPE_B04_B07"; roles = @("P02", "P07"); expectedText = "Seminar detail"; builderScenarios = @("B04", "B07") }
        S07_ERROR_RECOVERY = [ordered]@{ stateSource = "UI_RECIPE_B04_B06_ERROR"; roles = @("P05"); expectedText = "Transcripts"; builderScenarios = @("B04", "B06") }
        S08_LONG_CONTENT = [ordered]@{ stateSource = "UI_RECIPE_B04_LONG"; roles = @("P04", "P06"); expectedText = "Reconstruction"; builderScenarios = @("B04") }
    }
}
