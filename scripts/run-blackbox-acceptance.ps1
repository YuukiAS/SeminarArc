param(
    [string]$PackageName = "com.yuukias.seminararc.internal",
    [string]$Serial = "",
    [string]$ApkPath = "",
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [int]$AdbTimeoutSeconds = 45
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
$gradlew = Join-Path $repoRoot "gradlew.bat"
$runId = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$evidenceRoot = Join-Path $repoRoot "private\blackbox-acceptance\$runId"
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null

$script:StepCount = 0
$script:Results = [ordered]@{}
$script:SelectedSerial = $null

function Write-Status {
    param([string]$Message)
    $now = (Get-Date).ToString("HH:mm:ss")
    Write-Host "[$now] $Message"
}

function Join-ProcessArguments {
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

function Invoke-ProcessText {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [int]$TimeoutSeconds = 45,
        [switch]$IgnoreExitCode
    )

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $FilePath
    $psi.Arguments = Join-ProcessArguments $Arguments
    $psi.WorkingDirectory = $repoRoot
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($psi)
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        try { $process.Kill($true) } catch {}
        throw "Command timed out after ${TimeoutSeconds}s: $FilePath $($Arguments -join ' ')"
    }
    $stdout = $process.StandardOutput.ReadToEnd()
    $stderr = $process.StandardError.ReadToEnd()
    if ($process.ExitCode -ne 0 -and -not $IgnoreExitCode) {
        throw "Command failed ($($process.ExitCode)): $FilePath $($Arguments -join ' ')`n$stdout`n$stderr"
    }
    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Stdout = $stdout
        Stderr = $stderr
    }
}

function Invoke-ProcessBinary {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [string]$OutputPath,
        [int]$TimeoutSeconds = 45
    )

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $FilePath
    $psi.Arguments = Join-ProcessArguments $Arguments
    $psi.WorkingDirectory = $repoRoot
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($psi)
    $stream = [System.IO.File]::Open($OutputPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    try {
        $copyTask = $process.StandardOutput.BaseStream.CopyToAsync($stream)
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            try { $process.Kill($true) } catch {}
            throw "Command timed out after ${TimeoutSeconds}s: $FilePath $($Arguments -join ' ')"
        }
        $copyTask.GetAwaiter().GetResult()
        $stderr = $process.StandardError.ReadToEnd()
        if ($process.ExitCode -ne 0) {
            throw "Command failed ($($process.ExitCode)): $FilePath $($Arguments -join ' ')`n$stderr"
        }
    } finally {
        $stream.Dispose()
    }
}

function Invoke-AdbRaw {
    param(
        [string[]]$Arguments,
        [int]$TimeoutSeconds = $AdbTimeoutSeconds,
        [switch]$IgnoreExitCode
    )
    Invoke-ProcessText -FilePath $adb -Arguments $Arguments -TimeoutSeconds $TimeoutSeconds -IgnoreExitCode:$IgnoreExitCode
}

function Invoke-Adb {
    param(
        [string[]]$Arguments,
        [int]$TimeoutSeconds = $AdbTimeoutSeconds,
        [switch]$IgnoreExitCode
    )
    if ([string]::IsNullOrWhiteSpace($script:SelectedSerial)) {
        throw "No emulator serial has been selected."
    }
    Invoke-AdbRaw -Arguments (@("-s", $script:SelectedSerial) + $Arguments) -TimeoutSeconds $TimeoutSeconds -IgnoreExitCode:$IgnoreExitCode
}

function Invoke-AdbBinary {
    param(
        [string[]]$Arguments,
        [string]$OutputPath,
        [int]$TimeoutSeconds = $AdbTimeoutSeconds
    )
    if ([string]::IsNullOrWhiteSpace($script:SelectedSerial)) {
        throw "No emulator serial has been selected."
    }
    Invoke-ProcessBinary -FilePath $adb -Arguments (@("-s", $script:SelectedSerial) + $Arguments) -OutputPath $OutputPath -TimeoutSeconds $TimeoutSeconds
}

function Step {
    param([string]$Message)
    $script:StepCount += 1
    Write-Status ("STEP {0:000}: {1}" -f $script:StepCount, $Message)
}

function Select-EmulatorSerial {
    Step "Read ADB device inventory"
    $inventory = Invoke-AdbRaw -Arguments @("devices", "-l") -TimeoutSeconds 30
    $inventory.Stdout | Set-Content -Encoding UTF8 -Path (Join-Path $evidenceRoot "adb-devices.txt")
    $devices = @()
    foreach ($line in ($inventory.Stdout -split "`r?`n")) {
        if ($line -match "^(\S+)\s+(\S+)\s+(.*)$" -and $Matches[1] -ne "List") {
            $devices += [pscustomobject]@{
                Serial = $Matches[1]
                State = $Matches[2]
                Detail = $Matches[3]
            }
        }
    }
    $emulators = @($devices | Where-Object { $_.Serial -like "emulator-*" -and $_.State -eq "device" })
    if ($Serial.Trim().Length -gt 0) {
        $match = @($emulators | Where-Object { $_.Serial -eq $Serial })
        if ($match.Count -ne 1) {
            throw "Requested emulator serial '$Serial' is not present as an emulator-* device."
        }
        $script:SelectedSerial = $Serial
    } else {
        if ($emulators.Count -ne 1) {
            throw "Expected exactly one emulator-* device, found $($emulators.Count). Pass -Serial explicitly."
        }
        $script:SelectedSerial = $emulators[0].Serial
    }
    $nonEmulators = @($devices | Where-Object { $_.Serial -notlike "emulator-*" })
    if ($nonEmulators.Count -gt 0) {
        Write-Status "Non-emulator devices are visible; harness will still target only $script:SelectedSerial."
    }
    Write-Status "Selected emulator serial: $script:SelectedSerial"
}

function Build-And-Install {
    if (-not $SkipBuild -and [string]::IsNullOrWhiteSpace($ApkPath)) {
        Step "Build internal APK"
        Invoke-ProcessText -FilePath $gradlew -Arguments @(":app:assembleInternal", "--console=plain") -TimeoutSeconds 900 | Out-Null
        $ApkPath = Join-Path $repoRoot "app\build\outputs\apk\internal\app-internal.apk"
    }
    if (-not $SkipInstall) {
        if ([string]::IsNullOrWhiteSpace($ApkPath)) {
            $ApkPath = Join-Path $repoRoot "app\build\outputs\apk\internal\app-internal.apk"
        }
        if (-not (Test-Path $ApkPath)) {
            throw "APK not found: $ApkPath"
        }
        Step "Install internal APK to $script:SelectedSerial"
        Invoke-Adb -Arguments @("install", "-r", $ApkPath) -TimeoutSeconds 180 | Out-Null
    }
}

function Get-ScenarioDir {
    param([string]$Scenario)
    $path = Join-Path $evidenceRoot $Scenario
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    return $path
}

function Save-Text {
    param([string]$Path, [string]$Text)
    $Text | Set-Content -Encoding UTF8 -Path $Path
}

function Get-UiXml {
    param([string]$Scenario, [string]$Label)
    $dir = Get-ScenarioDir $Scenario
    $lastError = ""
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            $remotePath = "/sdcard/seminararc-blackbox-window.xml"
            Invoke-Adb -Arguments @("shell", "uiautomator", "dump", $remotePath) -TimeoutSeconds 30 | Out-Null
            $path = Join-Path $dir "$Label-ui.xml"
            Invoke-Adb -Arguments @("pull", $remotePath, $path) -TimeoutSeconds 30 | Out-Null
            $raw = Get-Content -Raw -Path $path
            $start = $raw.IndexOf("<hierarchy")
            $end = $raw.LastIndexOf("</hierarchy>")
            if ($start -ge 0 -and $end -ge $start) {
                $xml = $raw.Substring($start, $end + "</hierarchy>".Length - $start).Trim()
                Save-Text -Path $path -Text $xml
                return [pscustomobject]@{
                    Xml = [xml]$xml
                    Path = $path
                }
            }
            $lastError = "UI tree did not contain hierarchy XML: $raw"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds $attempt
    }
    throw $lastError
}

function Save-Screenshot {
    param([string]$Scenario, [string]$Label)
    $dir = Get-ScenarioDir $Scenario
    $path = Join-Path $dir "$Label.png"
    Invoke-AdbBinary -Arguments @("exec-out", "screencap", "-p") -OutputPath $path -TimeoutSeconds 60
    return $path
}

function Save-Logcat {
    param([string]$Scenario, [string]$Label)
    $dir = Get-ScenarioDir $Scenario
    $crashPath = Join-Path $dir "$Label-crash-logcat.txt"
    $crash = Invoke-Adb -Arguments @("logcat", "-d", "-b", "crash") -TimeoutSeconds 60 -IgnoreExitCode
    Save-Text -Path $crashPath -Text ($crash.Stdout + $crash.Stderr)
    $pidResult = Invoke-Adb -Arguments @("shell", "pidof", "-s", $PackageName) -TimeoutSeconds 15 -IgnoreExitCode
    $appPid = $pidResult.Stdout.Trim()
    if ($appPid.Length -gt 0) {
        $appPath = Join-Path $dir "$Label-app-error-logcat.txt"
        $appLog = Invoke-Adb -Arguments @("logcat", "-d", "--pid", $appPid, "*:E") -TimeoutSeconds 60 -IgnoreExitCode
        Save-Text -Path $appPath -Text ($appLog.Stdout + $appLog.Stderr)
    }
}

function Capture-Evidence {
    param([string]$Scenario, [string]$Label, [switch]$WithLogcat)
    try { Save-Screenshot -Scenario $Scenario -Label $Label | Out-Null } catch { Write-Status "Screenshot capture failed: $_" }
    try { Get-UiXml -Scenario $Scenario -Label $Label | Out-Null } catch { Write-Status "UI tree capture failed: $_" }
    if ($WithLogcat) {
        try { Save-Logcat -Scenario $Scenario -Label $Label } catch { Write-Status "Logcat capture failed: $_" }
    }
}

function Parse-BoundsCenter {
    param([string]$Bounds)
    if ($Bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        throw "Cannot parse bounds: $Bounds"
    }
    return [pscustomobject]@{
        X = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        Y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    }
}

function Get-Attr {
    param($Node, [string]$Name)
    return $Node.GetAttribute($Name)
}

function Find-Node {
    param(
        [xml]$Xml,
        [string]$Text = "",
        [string]$Description = "",
        [switch]$Contains,
        [string]$ClassName = "",
        [int]$Index = 0
    )
    $matches = @()
    foreach ($node in $Xml.SelectNodes("//node")) {
        $nodeText = Get-Attr $node "text"
        $nodeDesc = Get-Attr $node "content-desc"
        $nodeClass = Get-Attr $node "class"
        $ok = $true
        if ($Text.Length -gt 0) {
            $ok = if ($Contains) { $nodeText -like "*$Text*" } else { $nodeText -eq $Text }
        }
        if ($ok -and $Description.Length -gt 0) {
            $ok = if ($Contains) { $nodeDesc -like "*$Description*" } else { $nodeDesc -eq $Description }
        }
        if ($ok -and $ClassName.Length -gt 0) {
            $ok = $nodeClass -eq $ClassName
        }
        if ($ok) {
            $matches += $node
        }
    }
    if ($matches.Count -le $Index) {
        return $null
    }
    return $matches[$Index]
}

function Tap-Point {
    param([int]$X, [int]$Y)
    Invoke-Adb -Arguments @("shell", "input", "tap", "$X", "$Y") -TimeoutSeconds 30 | Out-Null
}

function Tap-Node {
    param($Node)
    $center = Parse-BoundsCenter (Get-Attr $Node "bounds")
    Tap-Point -X $center.X -Y $center.Y
}

function Swipe-Up {
    Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "1750", "540", "650", "350") -TimeoutSeconds 30 | Out-Null
}

function Swipe-Down {
    Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "650", "540", "1750", "350") -TimeoutSeconds 30 | Out-Null
}

function Wait-ForNode {
    param(
        [string]$Scenario,
        [string]$Label,
        [string]$Text = "",
        [string]$Description = "",
        [switch]$Contains,
        [string]$ClassName = "",
        [int]$Index = 0,
        [int]$TimeoutSeconds = 15
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $tree = Get-UiXml -Scenario $Scenario -Label $Label
            $node = Find-Node -Xml $tree.Xml -Text $Text -Description $Description -Contains:$Contains -ClassName $ClassName -Index $Index
            if ($null -ne $node) {
                return $node
            }
        } catch {
            Write-Status "UI wait retry: $($_.Exception.Message)"
        }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for node text='$Text' desc='$Description' class='$ClassName'."
}

function Tap-Text {
    param([string]$Scenario, [string]$Text, [switch]$Contains, [int]$ScrollAttempts = 0)
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Scenario $Scenario -Label ("find-" + ($Text -replace "[^A-Za-z0-9]", "-"))
        $node = Find-Node -Xml $tree.Xml -Text $Text -Contains:$Contains
        if ($null -ne $node) {
            Tap-Node $node
            Start-Sleep -Milliseconds 700
            return
        }
        if ($i -lt $ScrollAttempts) {
            Swipe-Up
            Start-Sleep -Milliseconds 700
        }
    }
    throw "Text node not found: $Text"
}

function Tap-Description {
    param([string]$Scenario, [string]$Description, [switch]$Contains, [int]$ScrollAttempts = 0)
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Scenario $Scenario -Label ("find-desc-" + ($Description -replace "[^A-Za-z0-9]", "-"))
        $node = Find-Node -Xml $tree.Xml -Description $Description -Contains:$Contains
        if ($null -ne $node) {
            Tap-Node $node
            Start-Sleep -Milliseconds 700
            return
        }
        if ($i -lt $ScrollAttempts) {
            Swipe-Up
            Start-Sleep -Milliseconds 700
        }
    }
    throw "Description node not found: $Description"
}

function Enter-Text {
    param([string]$Value)
    $escaped = $Value.Replace("\", "\\").Replace(" ", "%s").Replace("&", "\&").Replace("(", "\(").Replace(")", "\)")
    Invoke-Adb -Arguments @("shell", "input", "text", $escaped) -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 400
}

function Press-Back {
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "4") -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 700
}

function Tap-FieldByLabel {
    param([string]$Scenario, [string]$Label, [int]$ScrollAttempts = 0)
    Tap-Text -Scenario $Scenario -Text $Label -ScrollAttempts $ScrollAttempts
}

function Input-Field {
    param([string]$Scenario, [string]$Label, [string]$Value, [int]$ScrollAttempts = 0)
    Tap-FieldByLabel -Scenario $Scenario -Label $Label -ScrollAttempts $ScrollAttempts
    Enter-Text $Value
}

function Clear-App-State {
    Step "Clear internal package state"
    Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) -TimeoutSeconds 60 | Out-Null
    foreach ($permission in @("android.permission.RECORD_AUDIO", "android.permission.CAMERA", "android.permission.POST_NOTIFICATIONS")) {
        Invoke-Adb -Arguments @("shell", "pm", "revoke", $PackageName, $permission) -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    }
}

function Launch-App {
    Step "Launch app"
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "224") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "wm", "dismiss-keyguard") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "start", "-n", "$PackageName/com.yuukias.seminararc.MainActivity") -TimeoutSeconds 45 | Out-Null
    Wait-ForNode -Scenario "launch" -Label "launch-ready" -Text "SeminarArc" -TimeoutSeconds 20 | Out-Null
}

function Force-Stop-App {
    param([string]$Reason)
    Step "Force-stop app: $Reason"
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Seconds 1
}

function Assert-NoCrash {
    param([string]$Scenario)
    Step "$Scenario crash buffer check"
    $crash = Invoke-Adb -Arguments @("logcat", "-d", "-b", "crash") -TimeoutSeconds 60 -IgnoreExitCode
    $path = Join-Path (Get-ScenarioDir $Scenario) "crash-buffer.txt"
    Save-Text -Path $path -Text ($crash.Stdout + $crash.Stderr)
    if (($crash.Stdout + $crash.Stderr) -match [regex]::Escape($PackageName)) {
        throw "$Scenario crash buffer contains $PackageName"
    }
}

function Start-Scenario {
    param([string]$Scenario)
    Write-Status "=== $Scenario START ==="
    Invoke-Adb -Arguments @("logcat", "-c") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
}

function Finish-Scenario {
    param(
        [string]$Scenario,
        [string]$Status,
        [string]$Details,
        [datetime]$StartedAt,
        [string]$FailedStep = ""
    )
    $finishedAt = Get-Date
    $script:Results[$Scenario] = [ordered]@{
        scenario = $Scenario
        status = $Status
        details = $Details
        failedStep = $FailedStep
        startedAt = $StartedAt.ToUniversalTime().ToString("o")
        finishedAt = $finishedAt.ToUniversalTime().ToString("o")
        elapsedSeconds = [math]::Round(($finishedAt - $StartedAt).TotalSeconds, 2)
        package = $PackageName
        emulatorSerial = $script:SelectedSerial
    }
    $scenarioPath = Join-Path (Get-ScenarioDir $Scenario) "summary.json"
    $script:Results[$Scenario] | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 -Path $scenarioPath
    Write-Status "=== $Scenario $Status ==="
}

function Run-Scenario {
    param([string]$Scenario, [scriptblock]$Body)
    $startedAt = Get-Date
    Start-Scenario $Scenario
    try {
        & $Body
        Finish-Scenario -Scenario $Scenario -Status "PASS" -Details "Completed" -StartedAt $startedAt
    } catch {
        $message = $_.Exception.Message
        Capture-Evidence -Scenario $Scenario -Label "failure" -WithLogcat
        Finish-Scenario -Scenario $Scenario -Status "FAIL" -Details $message -StartedAt $startedAt -FailedStep $message
        throw
    }
}

function Run-B01 {
    Run-Scenario "B01" {
        Clear-App-State
        Launch-App
        Step "Verify empty library state"
        Wait-ForNode -Scenario "B01" -Label "empty-library" -Text "Your seminar library is empty" -TimeoutSeconds 20 | Out-Null
        Step "Verify create seminar entry is reachable"
        Wait-ForNode -Scenario "B01" -Label "create-entry" -Text "Create seminar" -TimeoutSeconds 15 | Out-Null
        Capture-Evidence -Scenario "B01" -Label "final"
        Assert-NoCrash "B01"
    }
}

function Run-B02 {
    Run-Scenario "B02" {
        Step "Open new seminar editor"
        Tap-Text -Scenario "B02" -Text "Create seminar"
        Wait-ForNode -Scenario "B02" -Label "new-editor" -Text "New seminar" -TimeoutSeconds 20 | Out-Null

        Step "Validate empty title"
        Tap-Text -Scenario "B02" -Text "Save draft" -ScrollAttempts 2
        Wait-ForNode -Scenario "B02" -Label "title-required" -Text "Title is required." -TimeoutSeconds 10 | Out-Null

        Step "Fill seminar editor fields"
        Input-Field -Scenario "B02" -Label "Title *" -Value "BlackboxAcceptanceSeminar"
        Input-Field -Scenario "B02" -Label "Speaker" -Value "TestSpeaker"
        Input-Field -Scenario "B02" -Label "Affiliation" -Value "SeminarArcQA"
        Swipe-Up
        Input-Field -Scenario "B02" -Label "Location" -Value "EmulatorRoom" -ScrollAttempts 1
        Input-Field -Scenario "B02" -Label "Abstract" -Value "SyntheticAbstractForBlackboxAcceptancePersistenceVerification" -ScrollAttempts 1
        Press-Back

        Step "Save seminar"
        Tap-Text -Scenario "B02" -Text "Save draft" -ScrollAttempts 2
        Wait-ForNode -Scenario "B02" -Label "detail-created" -Text "BlackboxAcceptanceSeminar" -TimeoutSeconds 20 | Out-Null

        Step "Return to library and search seminar"
        Tap-Description -Scenario "B02" -Description "Back"
        Wait-ForNode -Scenario "B02" -Label "library-after-create" -Text "Your seminars" -TimeoutSeconds 20 | Out-Null
        Input-Field -Scenario "B02" -Label "Search by title or speaker" -Value "B"
        Wait-ForNode -Scenario "B02" -Label "search-result" -Text "BlackboxAcceptanceSeminar" -TimeoutSeconds 15 | Out-Null

        Step "Reopen and edit title"
        Tap-Text -Scenario "B02" -Text "BlackboxAcceptanceSeminar"
        Wait-ForNode -Scenario "B02" -Label "detail-reopened" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
        Tap-Description -Scenario "B02" -Description "Edit"
        Wait-ForNode -Scenario "B02" -Label "edit-editor" -Text "Edit seminar" -TimeoutSeconds 20 | Out-Null
        Tap-FieldByLabel -Scenario "B02" -Label "BlackboxAcceptanceSeminar"
        Enter-Text "Updated"
        Press-Back
        Tap-Text -Scenario "B02" -Text "Save draft" -ScrollAttempts 2
        Wait-ForNode -Scenario "B02" -Label "updated-detail" -Text "Updated" -Contains -TimeoutSeconds 20 | Out-Null

        Step "Force-stop and verify persistence"
        Tap-Description -Scenario "B02" -Description "Back"
        Force-Stop-App "B02 persistence"
        Launch-App
        Input-Field -Scenario "B02" -Label "Search by title or speaker" -Value "U"
        Wait-ForNode -Scenario "B02" -Label "persistence-result" -Text "Updated" -Contains -TimeoutSeconds 20 | Out-Null
        Capture-Evidence -Scenario "B02" -Label "final"
        Assert-NoCrash "B02"
    }
}

function Deny-Permission-IfVisible {
    param([string]$Scenario)
    $tree = Get-UiXml -Scenario $Scenario -Label "permission-dialog"
    foreach ($node in $tree.Xml.SelectNodes("//node")) {
        if ((Get-Attr $node "resource-id") -eq "com.android.permissioncontroller:id/permission_deny_button") {
            Tap-Node $node
            Start-Sleep -Seconds 1
            return $true
        }
    }
    foreach ($label in @("Don" + [char]0x2019 + "t allow", "Don't allow", "Deny")) {
        $node = Find-Node -Xml $tree.Xml -Text $label -Contains
        if ($null -ne $node) {
            Tap-Node $node
            Start-Sleep -Seconds 1
            return $true
        }
    }
    return $false
}

function Run-B03 {
    Run-Scenario "B03" {
        Step "Open persisted seminar detail"
        Tap-Text -Scenario "B03" -Text "Updated" -Contains
        Wait-ForNode -Scenario "B03" -Label "detail-open" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null

        Step "Exercise microphone permission denied path"
        Invoke-Adb -Arguments @("shell", "pm", "revoke", $PackageName, "android.permission.RECORD_AUDIO") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
        Tap-Text -Scenario "B03" -Text "Start seminar" -ScrollAttempts 2
        Start-Sleep -Seconds 1
        [void](Deny-Permission-IfVisible -Scenario "B03")
        Wait-ForNode -Scenario "B03" -Label "mic-denied" -Text "Microphone permission is required before recording can start." -TimeoutSeconds 15 | Out-Null

        Step "Start photos-only active session"
        Tap-Text -Scenario "B03" -Text "Start photos only" -ScrollAttempts 1
        Wait-ForNode -Scenario "B03" -Label "active-session" -Text "Active session" -TimeoutSeconds 20 | Out-Null
        Wait-ForNode -Scenario "B03" -Label "photos-only" -Text "PHOTOS ONLY" -Contains -TimeoutSeconds 20 | Out-Null

        Step "Add note event"
        Tap-Text -Scenario "B03" -Text "Quick Note" -ScrollAttempts 2
        Wait-ForNode -Scenario "B03" -Label "note-dialog" -Text "Quick note" -TimeoutSeconds 15 | Out-Null
        Input-Field -Scenario "B03" -Label "Note" -Value "BlackboxNoteEvent"
        Tap-Text -Scenario "B03" -Text "Save"
        Wait-ForNode -Scenario "B03" -Label "note-saved" -Text "1 timeline events" -Contains -TimeoutSeconds 15 | Out-Null

        Step "Add question event"
        Tap-Text -Scenario "B03" -Text "Add Question" -ScrollAttempts 2
        Wait-ForNode -Scenario "B03" -Label "question-dialog" -Text "Add question" -TimeoutSeconds 15 | Out-Null
        Input-Field -Scenario "B03" -Label "Question" -Value "BlackboxQuestionEvent"
        Tap-Text -Scenario "B03" -Text "Save"
        Wait-ForNode -Scenario "B03" -Label "question-saved" -Text "2 timeline events" -Contains -TimeoutSeconds 15 | Out-Null

        Step "Add mark event"
        Tap-Text -Scenario "B03" -Text "Mark Moment" -ScrollAttempts 2
        Wait-ForNode -Scenario "B03" -Label "mark-saved" -Text "3 timeline events" -Contains -TimeoutSeconds 15 | Out-Null

        Step "End seminar"
        Tap-Text -Scenario "B03" -Text "End Seminar" -ScrollAttempts 3
        Wait-ForNode -Scenario "B03" -Label "end-dialog" -Text "End this seminar?" -TimeoutSeconds 15 | Out-Null
        Tap-Text -Scenario "B03" -Text "Stop and end"
        Wait-ForNode -Scenario "B03" -Label "completed-detail" -Text "This seminar is completed." -Contains -TimeoutSeconds 25 | Out-Null

        Step "Force-stop and verify completed detail remains reachable"
        Force-Stop-App "B03 completed persistence"
        Launch-App
        Input-Field -Scenario "B03" -Label "Search by title or speaker" -Value "U"
        Tap-Text -Scenario "B03" -Text "Updated" -Contains
        Wait-ForNode -Scenario "B03" -Label "completed-after-relaunch" -Text "This seminar is completed." -Contains -TimeoutSeconds 25 | Out-Null
        Capture-Evidence -Scenario "B03" -Label "final"
        Assert-NoCrash "B03"
    }
}

function Run-B08 {
    Run-Scenario "B08" {
        Step "Force-stop and relaunch before cleanup"
        Force-Stop-App "B08 recovery start"
        Launch-App
        Input-Field -Scenario "B08" -Label "Search by title or speaker" -Value "U"
        Wait-ForNode -Scenario "B08" -Label "seminar-before-delete" -Text "Updated" -Contains -TimeoutSeconds 20 | Out-Null

        Step "Delete synthetic seminar through UI"
        Tap-Text -Scenario "B08" -Text "Updated" -Contains
        Wait-ForNode -Scenario "B08" -Label "detail-before-delete" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
        Tap-Text -Scenario "B08" -Text "Delete seminar" -ScrollAttempts 6
        Wait-ForNode -Scenario "B08" -Label "delete-dialog" -Text "Delete this seminar?" -TimeoutSeconds 15 | Out-Null
        Tap-Text -Scenario "B08" -Text "Delete"
        Wait-ForNode -Scenario "B08" -Label "library-after-delete" -Text "Your seminars" -TimeoutSeconds 20 | Out-Null

        Step "Verify deletion persists after relaunch"
        Force-Stop-App "B08 deletion persistence"
        Launch-App
        Wait-ForNode -Scenario "B08" -Label "empty-after-delete" -Text "Your seminar library is empty" -TimeoutSeconds 20 | Out-Null
        Capture-Evidence -Scenario "B08" -Label "final"
        Assert-NoCrash "B08"
    }
}

try {
    Write-Status "Evidence root: $evidenceRoot"
    Select-EmulatorSerial
    Build-And-Install
    Run-B01
    Run-B02
    Run-B03
    Run-B08
    $overall = "PASS"
} catch {
    $overall = "FAIL"
    Write-Status "HARNESS FAILED: $($_.Exception.Message)"
} finally {
    $summary = [ordered]@{
        runId = $runId
        status = $overall
        package = $PackageName
        emulatorSerial = $script:SelectedSerial
        stepCount = $script:StepCount
        evidenceRoot = $evidenceRoot
        scenarios = $script:Results
    }
    $summaryJsonPath = Join-Path $evidenceRoot "summary.json"
    $summary | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -Path $summaryJsonPath
    $summaryMarkdownPath = Join-Path $evidenceRoot "summary.md"
    $lines = @(
        "# SeminarArc Black-box Acceptance $runId",
        "",
        "- status: $overall",
        "- package: $PackageName",
        "- emulator: $script:SelectedSerial",
        "- steps: $script:StepCount",
        ""
    )
    foreach ($key in $script:Results.Keys) {
        $item = $script:Results[$key]
        $lines += ("- {0}: {1} ({2}s)" -f $key, $item.status, $item.elapsedSeconds)
    }
    $lines | Set-Content -Encoding UTF8 -Path $summaryMarkdownPath
    Write-Status "Summary: $summaryMarkdownPath"
    Write-Status "Steps executed: $script:StepCount"
    Write-Status "Overall: $overall"
    if ($overall -ne "PASS") {
        exit 1
    }
}
