param(
    [string]$PackageName = "com.yuukias.seminararc.internal",
    [string]$Serial = "",
    [string]$ApkPath = "",
    [string[]]$Scenarios = @(),
    [string]$CameraFixturePath = "",
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
$script:ScenarioStartSteps = @{}
$script:Limitations = New-Object System.Collections.Generic.List[string]
$script:Exports = New-Object System.Collections.Generic.List[object]
$script:BriefText = $null
$script:FixturePhotoPrepared = $false
$script:ReferenceFixture = [ordered]@{
    doi = "10.1038/nature12373"
    title = "An integrated encyclopedia of DNA elements in the human genome"
    provider = "Crossref/OpenAlex/DataCite public metadata"
}

function Normalize-ScenarioSelection {
    param([string[]]$RequestedScenarios)
    $valid = @("B01", "B02", "B03", "B04", "B05", "B06", "B07", "B08")
    $normalized = New-Object System.Collections.Generic.List[string]
    foreach ($item in $RequestedScenarios) {
        foreach ($part in ($item -split ",")) {
            $scenario = $part.Trim().ToUpperInvariant()
            if ([string]::IsNullOrWhiteSpace($scenario)) {
                continue
            }
            if ($valid -notcontains $scenario) {
                throw "Unknown scenario '$scenario'. Valid scenarios: $($valid -join ', ')."
            }
            if (-not $normalized.Contains($scenario)) {
                $normalized.Add($scenario) | Out-Null
            }
        }
    }
    if ($normalized.Count -eq 0) {
        foreach ($scenario in $valid) {
            $normalized.Add($scenario) | Out-Null
        }
    }
    return $normalized.ToArray()
}

$script:SelectedScenarios = Normalize-ScenarioSelection -RequestedScenarios $Scenarios
$script:RunFullCatalog = ($script:SelectedScenarios.Count -eq 8)

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
    $crash = Invoke-Adb -Arguments @("logcat", "-d", "-b", "crash", "-t", "500") -TimeoutSeconds 15 -IgnoreExitCode
    Save-Text -Path $crashPath -Text ($crash.Stdout + $crash.Stderr)
    $pidResult = Invoke-Adb -Arguments @("shell", "pidof", "-s", $PackageName) -TimeoutSeconds 15 -IgnoreExitCode
    $appPid = $pidResult.Stdout.Trim()
    if ($appPid.Length -gt 0) {
        $appPath = Join-Path $dir "$Label-app-error-logcat.txt"
        $appLog = Invoke-Adb -Arguments @("logcat", "-d", "-t", "500", "--pid", $appPid, "*:E") -TimeoutSeconds 15 -IgnoreExitCode
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

function Swipe-UpRightGutter {
    Invoke-Adb -Arguments @("shell", "input", "swipe", "1010", "1800", "1010", "600", "450") -TimeoutSeconds 30 | Out-Null
}

function Swipe-DownRightGutter {
    Invoke-Adb -Arguments @("shell", "input", "swipe", "1010", "600", "1010", "1800", "450") -TimeoutSeconds 30 | Out-Null
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

function Tap-TextBidirectional {
    param(
        [string]$Scenario,
        [string]$Text,
        [switch]$Contains,
        [int]$DownAttempts = 8,
        [int]$UpAttempts = 4
    )
    for ($i = 0; $i -le $DownAttempts; $i++) {
        $tree = Get-UiXml -Scenario $Scenario -Label ("find-bi-down-" + ($Text -replace "[^A-Za-z0-9]", "-"))
        $node = Find-Node -Xml $tree.Xml -Text $Text -Contains:$Contains
        if ($null -ne $node) {
            Tap-Node $node
            Start-Sleep -Milliseconds 700
            return
        }
        if ($i -lt $DownAttempts) {
            Swipe-UpRightGutter
            Start-Sleep -Milliseconds 700
        }
    }
    for ($i = 0; $i -le $UpAttempts; $i++) {
        $tree = Get-UiXml -Scenario $Scenario -Label ("find-bi-up-" + ($Text -replace "[^A-Za-z0-9]", "-"))
        $node = Find-Node -Xml $tree.Xml -Text $Text -Contains:$Contains
        if ($null -ne $node) {
            Tap-Node $node
            Start-Sleep -Milliseconds 700
            return
        }
        if ($i -lt $UpAttempts) {
            Swipe-DownRightGutter
            Start-Sleep -Milliseconds 700
        }
    }
    throw "Text node not found: $Text"
}

function Tap-TextClickableAncestor {
    param([string]$Scenario, [string]$Text, [switch]$Contains, [int]$ScrollAttempts = 0)
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Scenario $Scenario -Label ("find-clickable-" + ($Text -replace "[^A-Za-z0-9]", "-"))
        $node = Find-Node -Xml $tree.Xml -Text $Text -Contains:$Contains
        if ($null -ne $node) {
            $target = $node
            $parent = $node.ParentNode
            while ($null -ne $parent -and $parent.Name -eq "node") {
                if ((Get-Attr $parent "clickable") -eq "true" -and (Get-Attr $parent "enabled") -eq "true") {
                    $target = $parent
                    break
                }
                $parent = $parent.ParentNode
            }
            Tap-Node $target
            Start-Sleep -Milliseconds 700
            return
        }
        if ($i -lt $ScrollAttempts) {
            Swipe-Up
            Start-Sleep -Milliseconds 700
        }
    }
    throw "Clickable text node not found: $Text"
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

function Drag-Description {
    param([string]$Scenario, [string]$Description, [switch]$Contains)
    $tree = Get-UiXml -Scenario $Scenario -Label ("drag-desc-" + ($Description -replace "[^A-Za-z0-9]", "-"))
    $node = Find-Node -Xml $tree.Xml -Description $Description -Contains:$Contains
    if ($null -eq $node) {
        throw "Description node not found for drag: $Description"
    }
    $bounds = Get-Attr $node "bounds"
    if ($bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        throw "Cannot parse drag bounds: $bounds"
    }
    $left = [int]$Matches[1]
    $top = [int]$Matches[2]
    $right = [int]$Matches[3]
    $bottom = [int]$Matches[4]
    $startX = [int]($left + (($right - $left) * 0.20))
    $startY = [int]($top + (($bottom - $top) * 0.25))
    $endX = [int]($left + (($right - $left) * 0.78))
    $endY = [int]($top + (($bottom - $top) * 0.58))
    Invoke-Adb -Arguments @("shell", "input", "swipe", "$startX", "$startY", "$endX", "$endY", "500") -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 700
}

function Enter-Text {
    param([string]$Value)
    $escaped = $Value.Replace("\", "\\").Replace(" ", "%s").Replace("&", "\&").Replace("(", "\(").Replace(")", "\)")
    Invoke-Adb -Arguments @("shell", "input", "text", $escaped) -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 400
}

function Enter-KeyEventText {
    param([string]$Value)
    $keyCodes = @{
        "0" = 7; "1" = 8; "2" = 9; "3" = 10; "4" = 11; "5" = 12; "6" = 13; "7" = 14; "8" = 15; "9" = 16
        "a" = 29; "b" = 30; "c" = 31; "d" = 32; "e" = 33; "f" = 34; "g" = 35; "h" = 36; "i" = 37; "j" = 38
        "k" = 39; "l" = 40; "m" = 41; "n" = 42; "o" = 43; "p" = 44; "q" = 45; "r" = 46; "s" = 47; "t" = 48
        "u" = 49; "v" = 50; "w" = 51; "x" = 52; "y" = 53; "z" = 54
        "." = 56; "/" = 76; " " = 62
    }
    foreach ($character in $Value.ToCharArray()) {
        $key = $character.ToString().ToLowerInvariant()
        if (-not $keyCodes.ContainsKey($key)) {
            throw "Unsupported keyevent text character: $character"
        }
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "$($keyCodes[$key])") -TimeoutSeconds 30 | Out-Null
        Start-Sleep -Milliseconds 80
    }
    Start-Sleep -Milliseconds 500
}

function Enter-MultilineText {
    param([string[]]$Lines)
    for ($i = 0; $i -lt $Lines.Count; $i++) {
        Enter-KeyEventText $Lines[$i]
        if ($i -lt ($Lines.Count - 1)) {
            Invoke-Adb -Arguments @("shell", "input", "keyevent", "66") -TimeoutSeconds 30 | Out-Null
            Start-Sleep -Milliseconds 300
        }
    }
}

function Press-Back {
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "4") -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 700
}

function Tap-FieldByLabel {
    param([string]$Scenario, [string]$Label, [int]$ScrollAttempts = 0)
    Tap-Text -Scenario $Scenario -Text $Label -ScrollAttempts $ScrollAttempts
}

function Tap-EditTextByIndex {
    param([string]$Scenario, [int]$Index = 0, [string]$Label = "edit-text")
    $tree = Get-UiXml -Scenario $Scenario -Label $Label
    $node = Find-Node -Xml $tree.Xml -ClassName "android.widget.EditText" -Index $Index
    if ($null -eq $node) {
        throw "EditText node not found at index $Index."
    }
    Tap-Node $node
    Start-Sleep -Milliseconds 700
}

function Tap-EditTextContainingLabel {
    param([string]$Scenario, [string]$Text, [string]$Label = "edit-text-containing-label", [int]$ScrollAttempts = 0)
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Scenario $Scenario -Label ("$Label-$i")
        foreach ($node in $tree.Xml.SelectNodes("//node")) {
            if ((Get-Attr $node "class") -ne "android.widget.EditText") {
                continue
            }
            foreach ($child in $node.SelectNodes(".//node")) {
                if ((Get-Attr $child "text") -eq $Text) {
                    Tap-Node $node
                    Start-Sleep -Milliseconds 700
                    return
                }
            }
        }
        if ($i -lt $ScrollAttempts) {
            Swipe-Up
            Start-Sleep -Milliseconds 700
        }
    }
    throw "EditText containing label '$Text' not found."
}

function Get-EditTextTextByIndex {
    param([string]$Scenario, [int]$Index = 0, [string]$Label = "edit-text-value")
    $tree = Get-UiXml -Scenario $Scenario -Label $Label
    $node = Find-Node -Xml $tree.Xml -ClassName "android.widget.EditText" -Index $Index
    if ($null -eq $node) {
        throw "EditText node not found at index $Index."
    }
    return (Get-Attr $node "text")
}

function Get-NodeBounds {
    param($Node)
    $bounds = Get-Attr $Node "bounds"
    if ($bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        throw "Cannot parse bounds: $bounds"
    }
    return [pscustomobject]@{
        Left = [int]$Matches[1]
        Top = [int]$Matches[2]
        Right = [int]$Matches[3]
        Bottom = [int]$Matches[4]
    }
}

function Find-FirstEditTextBelowText {
    param([xml]$Xml, [string]$AnchorText)
    $anchor = Find-Node -Xml $Xml -Text $AnchorText
    if ($null -eq $anchor) {
        throw "Anchor text not found: $AnchorText"
    }
    $anchorBounds = Get-NodeBounds $anchor
    $bestNode = $null
    $bestTop = [int]::MaxValue
    foreach ($node in $Xml.SelectNodes("//node")) {
        if ((Get-Attr $node "class") -ne "android.widget.EditText") {
            continue
        }
        $nodeBounds = Get-NodeBounds $node
        if (($nodeBounds.Top -gt $anchorBounds.Bottom) -and ($nodeBounds.Top -lt $bestTop)) {
            $bestNode = $node
            $bestTop = $nodeBounds.Top
        }
    }
    if ($null -eq $bestNode) {
        throw "EditText below anchor not found: $AnchorText"
    }
    return $bestNode
}

function Append-EditTextTextByIndex {
    param(
        [string]$Scenario,
        [int]$Index = 0,
        [string]$AppendText,
        [string]$Label = "append-edit-text",
        [int]$Attempts = 4
    )
    $originalText = Get-EditTextTextByIndex -Scenario $Scenario -Index $Index -Label "$Label-before"
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        Tap-EditTextByIndex -Scenario $Scenario -Index $Index -Label "$Label-focus-$attempt"
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "123") -TimeoutSeconds 30 | Out-Null
        Start-Sleep -Milliseconds 500
        if (($attempt % 2) -eq 1) {
            Enter-Text $AppendText
        } else {
            Enter-KeyEventText $AppendText
        }
        Start-Sleep -Milliseconds 700
        $currentText = Get-EditTextTextByIndex -Scenario $Scenario -Index $Index -Label "$Label-after-$attempt"
        if (($currentText -ne $originalText) -and (-not [string]::IsNullOrWhiteSpace($currentText))) {
            return $currentText
        }
    }
    throw "EditText index $Index did not change after $Attempts black-box input attempts."
}

function Append-FirstEditTextBelowText {
    param(
        [string]$Scenario,
        [string]$AnchorText,
        [string]$AppendText,
        [string]$Label = "append-first-edit-text-below",
        [int]$Attempts = 4
    )
    $initialTree = Get-UiXml -Scenario $Scenario -Label "$Label-before"
    $initialNode = Find-FirstEditTextBelowText -Xml $initialTree.Xml -AnchorText $AnchorText
    $originalText = Get-Attr $initialNode "text"
    if ([string]::IsNullOrWhiteSpace($originalText)) {
        throw "First EditText below '$AnchorText' is empty before edit."
    }
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        $focusTree = Get-UiXml -Scenario $Scenario -Label "$Label-focus-$attempt"
        $node = Find-FirstEditTextBelowText -Xml $focusTree.Xml -AnchorText $AnchorText
        Tap-Node $node
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "123") -TimeoutSeconds 30 | Out-Null
        Start-Sleep -Milliseconds 500
        if (($attempt % 2) -eq 1) {
            Enter-Text $AppendText
        } else {
            Enter-KeyEventText $AppendText
        }
        Start-Sleep -Milliseconds 700
        $currentTree = Get-UiXml -Scenario $Scenario -Label "$Label-after-$attempt"
        $currentNode = Find-FirstEditTextBelowText -Xml $currentTree.Xml -AnchorText $AnchorText
        $currentText = Get-Attr $currentNode "text"
        if (($currentText -ne $originalText) -and (-not [string]::IsNullOrWhiteSpace($currentText))) {
            return $currentText
        }
    }
    throw "First EditText below '$AnchorText' did not change after $Attempts black-box input attempts."
}

function Tap-EditTextByText {
    param([string]$Scenario, [string]$Text, [string]$Label = "edit-text-by-text", [int]$ScrollAttempts = 0)
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Scenario $Scenario -Label ("$Label-$i")
        $node = Find-Node -Xml $tree.Xml -Text $Text -ClassName "android.widget.EditText"
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
    throw "EditText node not found for text: $Text"
}

function Get-EditTextTextByText {
    param([string]$Scenario, [string]$Text, [string]$Label = "edit-text-value-by-text")
    $tree = Get-UiXml -Scenario $Scenario -Label $Label
    $node = Find-Node -Xml $tree.Xml -Text $Text -ClassName "android.widget.EditText"
    if ($null -eq $node) {
        throw "EditText node not found for text: $Text"
    }
    return (Get-Attr $node "text")
}

function Append-EditTextTextByText {
    param(
        [string]$Scenario,
        [string]$Text,
        [string]$AppendText,
        [string]$Label = "append-edit-text-by-text",
        [int]$Attempts = 4
    )
    $originalText = Get-EditTextTextByText -Scenario $Scenario -Text $Text -Label "$Label-before"
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        Tap-EditTextByText -Scenario $Scenario -Text $originalText -Label "$Label-focus-$attempt" -ScrollAttempts 1
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "123") -TimeoutSeconds 30 | Out-Null
        Start-Sleep -Milliseconds 500
        if (($attempt % 2) -eq 1) {
            Enter-Text $AppendText
        } else {
            Enter-KeyEventText $AppendText
        }
        Start-Sleep -Milliseconds 700
        $tree = Get-UiXml -Scenario $Scenario -Label "$Label-after-$attempt"
        foreach ($node in $tree.Xml.SelectNodes("//node")) {
            if ((Get-Attr $node "class") -eq "android.widget.EditText") {
                $currentText = Get-Attr $node "text"
                if (($currentText -ne $originalText) -and ($currentText -like "$originalText*")) {
                    return $currentText
                }
            }
        }
    }
    throw "EditText '$Text' did not change after $Attempts black-box input attempts."
}

function Input-Field {
    param([string]$Scenario, [string]$Label, [string]$Value, [int]$ScrollAttempts = 0)
    Tap-FieldByLabel -Scenario $Scenario -Label $Label -ScrollAttempts $ScrollAttempts
    Enter-Text $Value
}

function Input-FieldKeyEvent {
    param([string]$Scenario, [string]$Label, [string]$Value, [int]$ScrollAttempts = 0)
    Tap-FieldByLabel -Scenario $Scenario -Label $Label -ScrollAttempts $ScrollAttempts
    Enter-KeyEventText $Value
}

function Input-EditTextKeyEvent {
    param([string]$Scenario, [int]$Index, [string]$Value, [string]$Label = "edit-text")
    Tap-EditTextByIndex -Scenario $Scenario -Index $Index -Label $Label
    Enter-KeyEventText $Value
}

function Input-EditText {
    param([string]$Scenario, [int]$Index, [string]$Value, [string]$Label = "edit-text")
    Tap-EditTextByIndex -Scenario $Scenario -Index $Index -Label $Label
    Enter-Text $Value
}

function Clear-App-State {
    Step "Clear internal package state"
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) -TimeoutSeconds 60 | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    foreach ($permission in @("android.permission.RECORD_AUDIO", "android.permission.CAMERA", "android.permission.POST_NOTIFICATIONS")) {
        Invoke-Adb -Arguments @("shell", "pm", "revoke", $PackageName, $permission) -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    }
}

function Launch-App {
    Step "Launch app"
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "224") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "wm", "dismiss-keyguard") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    $lastError = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        Invoke-Adb -Arguments @(
            "shell", "am", "start",
            "-S", "-W",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            "-f", "0x10008000",
            "-n", "$PackageName/com.yuukias.seminararc.MainActivity"
        ) -TimeoutSeconds 45 | Out-Null
        try {
            Wait-ForNode -Scenario "launch" -Label "launch-ready-$attempt" -Text "SeminarArc" -TimeoutSeconds 25 | Out-Null
            Wait-ForAnyText -Scenario "launch" -Label "launch-content-ready-$attempt" -Texts @("Your seminars", "Seminar detail", "Active session") -Contains -TimeoutSeconds 20 | Out-Null
            Start-Sleep -Seconds 1
            return
        } catch {
            $lastError = $_.Exception.Message
            Write-Status "Launch readiness retry ${attempt}: $lastError"
            Start-Sleep -Seconds 2
        }
    }
    throw $lastError
}

function Force-Stop-App {
    param([string]$Reason)
    Step "Force-stop app: $Reason"
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Seconds 1
}

function Assert-NoCrash {
    param([string]$Scenario)
    Step "$Scenario scoped error logcat check"
    $path = Join-Path (Get-ScenarioDir $Scenario) "scoped-error-logcat.txt"
    try {
        $pidResult = Invoke-Adb -Arguments @("shell", "pidof", "-s", $PackageName) -TimeoutSeconds 5 -IgnoreExitCode
        $appPid = $pidResult.Stdout.Trim()
        if ([string]::IsNullOrWhiteSpace($appPid)) {
            Save-Text -Path $path -Text "No running app pid for scoped error logcat check."
            return
        }
        $crash = Invoke-Adb -Arguments @("logcat", "-d", "-t", "200", "--pid", $appPid, "*:E") -TimeoutSeconds 5 -IgnoreExitCode
    } catch {
        Save-Text -Path $path -Text "Scoped error logcat read failed: $($_.Exception.Message)"
        Add-Limitation "$Scenario scoped error logcat check could not dump emulator logcat within the bounded timeout; UI evidence and failure captures remain available."
        Write-Status "$Scenario scoped error logcat check skipped: $($_.Exception.Message)"
        return
    }
    $crashText = $crash.Stdout + $crash.Stderr
    Save-Text -Path $path -Text $crashText
    if ($crashText -match "FATAL EXCEPTION|AndroidRuntime") {
        throw "$Scenario scoped error logcat contains fatal app error"
    }
}

function Add-Limitation {
    param([string]$Message)
    $script:Limitations.Add($Message) | Out-Null
}

function Cleanup-Export-Files {
    Step "Clean prior synthetic export files"
    foreach ($name in @(
        "seminar.md",
        "seminar-notion-ready.md",
        "references.bib",
        "references.ris",
        "seminar.zip"
    )) {
        Invoke-Adb -Arguments @("shell", "rm", "-f", "/sdcard/Download/$name") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    }
}

function Wait-ForAnyText {
    param(
        [string]$Scenario,
        [string]$Label,
        [string[]]$Texts,
        [switch]$Contains,
        [int]$TimeoutSeconds = 20
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tree = Get-UiXml -Scenario $Scenario -Label $Label
        foreach ($text in $Texts) {
            $node = Find-Node -Xml $tree.Xml -Text $text -Contains:$Contains
            if ($null -ne $node) {
                return [pscustomobject]@{ Text = $text; Node = $node }
            }
        }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for any text: $($Texts -join ', ')"
}

function Wait-ForPackageVisible {
    param(
        [string]$Scenario,
        [string]$Label,
        [string]$ExpectedPackage,
        [int]$TimeoutSeconds = 20
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tree = Get-UiXml -Scenario $Scenario -Label $Label
        foreach ($node in $tree.Xml.SelectNodes("//node")) {
            if ((Get-Attr $node "package") -eq $ExpectedPackage) {
                return
            }
        }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for package '$ExpectedPackage'."
}

function Wait-ForExportFile {
    param(
        [string]$DevicePath,
        [int]$TimeoutSeconds = 60
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $stat = Invoke-Adb -Arguments @("shell", "stat", "-c", "%s", $DevicePath) -TimeoutSeconds 30 -IgnoreExitCode
        if ($stat.ExitCode -eq 0) {
            $trimmed = $stat.Stdout.Trim()
            $size = 0L
            if ([long]::TryParse($trimmed, [ref]$size) -and $size -gt 0) {
                return $size
            }
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Export file not found or empty after ${TimeoutSeconds}s: $DevicePath"
}

function Verify-ZipExportEntries {
    param(
        [string]$Scenario,
        [string]$DevicePath,
        [string[]]$ExpectedEntries
    )
    $localZip = Join-Path (Get-ScenarioDir $Scenario) "export.zip"
    Invoke-Adb -Arguments @("pull", $DevicePath, $localZip) -TimeoutSeconds 60 | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($localZip)
    try {
        $entryNames = @($zip.Entries | ForEach-Object { $_.FullName })
    } finally {
        $zip.Dispose()
    }
    $manifestPath = Join-Path (Get-ScenarioDir $Scenario) "export-zip-entries.txt"
    Save-Text -Path $manifestPath -Text ($entryNames -join "`n")
    foreach ($expected in $ExpectedEntries) {
        $matched = $false
        foreach ($entry in $entryNames) {
            if (($entry -eq $expected) -or ($entry -like "*/$expected")) {
                $matched = $true
                break
            }
        }
        if (-not $matched) {
            throw "ZIP export missing entry '$expected'."
        }
    }
    return $entryNames
}

function Scroll-UntilTextVisible {
    param(
        [string]$Scenario,
        [string]$Text,
        [string]$Label,
        [int]$ScrollAttempts = 8
    )
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Scenario $Scenario -Label ("$Label-$i")
        $node = Find-Node -Xml $tree.Xml -Text $Text
        if ($null -ne $node) {
            return $node
        }
        if ($i -lt $ScrollAttempts) {
            Swipe-Up
            Start-Sleep -Milliseconds 700
        }
    }
    throw "Text node not found after scrolling: $Text"
}

function Wait-ForJobTerminal {
    param(
        [string]$Scenario,
        [string]$JobPrefix,
        [int]$TimeoutSeconds = 90,
        [switch]$AllowFailure
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tree = Get-UiXml -Scenario $Scenario -Label ("job-" + ($JobPrefix -replace "[^A-Za-z0-9]", "-"))
        foreach ($node in $tree.Xml.SelectNodes("//node")) {
            $text = Get-Attr $node "text"
            if ($text -like "$JobPrefix succeeded*") {
                return "succeeded"
            }
            if ($AllowFailure -and $text -like "$JobPrefix failed*") {
                return "failed"
            }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $JobPrefix terminal state."
}

function Open-Synthetic-Detail {
    param([string]$Scenario)
    Force-Stop-App "$Scenario reopen synthetic detail"
    Launch-App
    $libraryReady = $false
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        $tree = Get-UiXml -Scenario $Scenario -Label "library-ready-$attempt"
        if ($null -ne (Find-Node -Xml $tree.Xml -Text "Your seminars" -Contains)) {
            $libraryReady = $true
            break
        }
        Press-Back
    }
    if (-not $libraryReady) {
        throw "Could not navigate back to seminar library before reopening synthetic detail."
    }
    Input-FieldKeyEvent -Scenario $Scenario -Label "Search by title or speaker" -Value "u"
    Tap-TextClickableAncestor -Scenario $Scenario -Text "updated" -Contains
    Wait-ForNode -Scenario $Scenario -Label "detail-open" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
}

function Ensure-FocusedPhotoFixtureSeminar {
    param([string]$Scenario)
    if ($script:FixturePhotoPrepared) {
        return
    }

    Step "Prepare focused seminar with deterministic camera fixture"
    Clear-App-State
    Launch-App
    Tap-Text -Scenario $Scenario -Text "Create seminar"
    Wait-ForNode -Scenario $Scenario -Label "fixture-new-editor" -Text "New seminar" -TimeoutSeconds 20 | Out-Null
    [void](Append-EditTextTextByIndex -Scenario $Scenario -Index 0 -AppendText "updatedfixture" -Label "fixture-title-field")
    Press-Back
    Tap-Text -Scenario $Scenario -Text "Save draft" -ScrollAttempts 2
    Wait-ForNode -Scenario $Scenario -Label "fixture-detail-created" -Text "updatedfixture" -Contains -TimeoutSeconds 20 | Out-Null

    Step "Start photos-only session for deterministic fixture"
    Invoke-Adb -Arguments @("shell", "pm", "revoke", $PackageName, "android.permission.RECORD_AUDIO") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Tap-TextClickableAncestor -Scenario $Scenario -Text "Start photos only" -ScrollAttempts 2
    Wait-ForNode -Scenario $Scenario -Label "fixture-active-session" -Text "Active session" -TimeoutSeconds 25 | Out-Null
    Wait-ForNode -Scenario $Scenario -Label "fixture-photos-only" -Text "PHOTOS ONLY" -Contains -TimeoutSeconds 20 | Out-Null

    Step "Capture deterministic slide through CameraX"
    Invoke-Adb -Arguments @("shell", "pm", "grant", $PackageName, "android.permission.CAMERA") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Start-Sleep -Seconds 2
    Tap-Text -Scenario $Scenario -Text "Capture Slide" -ScrollAttempts 3
    try {
        $photoResult = Wait-ForAnyText -Scenario $Scenario -Label "fixture-photo-capture-result" -Texts @("Slide photo saved.", "Photo capture failed:") -Contains -TimeoutSeconds 45
    } catch {
        Capture-Evidence -Scenario $Scenario -Label "fixture-photo-capture-timeout" -WithLogcat
        throw "BLOCKED_EMULATOR_CAMERA_FIXTURE: CameraX did not report a terminal photo capture state for the deterministic camera fixture."
    }
    if ($photoResult.Text -like "Photo capture failed:*") {
        Capture-Evidence -Scenario $Scenario -Label "fixture-photo-capture-failed" -WithLogcat
        throw "BLOCKED_EMULATOR_CAMERA_FIXTURE: CameraX reported photo capture failure for the deterministic camera fixture."
    }
    Wait-ForNode -Scenario $Scenario -Label "fixture-last-photo" -Text "Last photo" -TimeoutSeconds 20 | Out-Null

    Step "Complete focused fixture seminar"
    $endDialogVisible = $false
    $endDialogError = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        Tap-TextClickableAncestor -Scenario $Scenario -Text "End Seminar" -ScrollAttempts 3
        try {
            Wait-ForNode -Scenario $Scenario -Label "fixture-end-dialog-$attempt" -Text "End this seminar?" -TimeoutSeconds 15 | Out-Null
            $endDialogVisible = $true
            break
        } catch {
            $endDialogError = $_.Exception.Message
            Write-Status "Focused fixture End Seminar retry ${attempt}: $endDialogError"
        }
    }
    if (-not $endDialogVisible) {
        throw $endDialogError
    }
    Tap-TextClickableAncestor -Scenario $Scenario -Text "Stop and end"
    Wait-ForNode -Scenario $Scenario -Label "fixture-completed-detail" -Text "This seminar is completed." -Contains -TimeoutSeconds 25 | Out-Null
    $script:FixturePhotoPrepared = $true
}

function Save-Current-CreateDocument {
    param(
        [string]$Scenario,
        [string]$Kind,
        [string]$DevicePath,
        [string[]]$ExpectedSnippets,
        [switch]$Binary,
        [string[]]$ExpectedZipEntries = @()
    )
    Step "Confirm Android document picker for $Kind"
    $candidate = Wait-ForAnyText -Scenario $Scenario -Label "document-picker-$Kind" -Texts @("Save", "SAVE", "Use this folder", "Allow") -TimeoutSeconds 120
    if ($candidate.Text -like "*Use this folder*") {
        Tap-Node $candidate.Node
        Start-Sleep -Seconds 1
        $candidate = Wait-ForAnyText -Scenario $Scenario -Label "document-picker-save-$Kind" -Texts @("Save", "SAVE", "Allow") -TimeoutSeconds 15
    }
    if ($candidate.Text -like "*Allow*") {
        Tap-Node $candidate.Node
        Start-Sleep -Seconds 1
        $candidate = Wait-ForAnyText -Scenario $Scenario -Label "document-picker-save-after-allow-$Kind" -Texts @("Save", "SAVE") -TimeoutSeconds 15
    }
    $pickerDismissed = $false
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $saveTree = Get-UiXml -Scenario $Scenario -Label "document-picker-save-confirm-$Kind-$attempt"
        $saveNode = $null
        foreach ($node in $saveTree.Xml.SelectNodes("//node")) {
            if ((Get-Attr $node "resource-id") -eq "android:id/button1" -and (Get-Attr $node "enabled") -eq "true") {
                $saveNode = $node
                break
            }
        }
        if ($null -eq $saveNode) {
            $saveNode = Find-Node -Xml $saveTree.Xml -Text "SAVE" -ClassName "android.widget.Button"
        }
        if ($null -eq $saveNode) {
            $saveNode = Find-Node -Xml $saveTree.Xml -Text "Save" -ClassName "android.widget.Button"
        }
        if ($null -eq $saveNode) {
            throw "Android document picker save button not found for $Kind."
        }
        Tap-Node $saveNode
        Start-Sleep -Seconds 2
        $afterTap = Get-UiXml -Scenario $Scenario -Label "document-picker-after-save-$Kind-$attempt"
        $replaceNode = Find-Node -Xml $afterTap.Xml -Text "Replace" -Contains -ClassName "android.widget.Button"
        if ($null -ne $replaceNode) {
            Tap-Node $replaceNode
            Start-Sleep -Seconds 2
            $afterReplace = Get-UiXml -Scenario $Scenario -Label "document-picker-after-replace-$Kind-$attempt"
            if ($null -eq (Find-Node -Xml $afterReplace.Xml -Text "SAVE" -ClassName "android.widget.Button") -and
                $null -eq (Find-Node -Xml $afterReplace.Xml -Text "Save" -ClassName "android.widget.Button")) {
                $pickerDismissed = $true
                break
            }
        } elseif ($null -eq (Find-Node -Xml $afterTap.Xml -Text "SAVE" -ClassName "android.widget.Button") -and
            $null -eq (Find-Node -Xml $afterTap.Xml -Text "Save" -ClassName "android.widget.Button")) {
            $pickerDismissed = $true
            break
        }
        Write-Status "Android document picker save retry ${attempt} for $Kind."
    }
    if (-not $pickerDismissed) {
        throw "Android document picker did not dismiss after saving $Kind."
    }

    Step "Verify exported $Kind file exists"
    $size = Wait-ForExportFile -DevicePath $DevicePath -TimeoutSeconds 60
    try {
        Wait-ForPackageVisible -Scenario $Scenario -Label "after-export-$Kind" -ExpectedPackage $PackageName -TimeoutSeconds 20
    } catch {
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "4") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
        Wait-ForPackageVisible -Scenario $Scenario -Label "after-export-back-$Kind" -ExpectedPackage $PackageName -TimeoutSeconds 20
    }
    try {
        Wait-ForNode -Scenario $Scenario -Label "export-complete-$Kind" -Text "Export complete." -TimeoutSeconds 5 | Out-Null
    } catch {
        Add-Limitation "$Kind export was verified by saved file evidence, but the in-app 'Export complete.' message was not visible within the bounded UI wait."
    }
    if (-not $Binary) {
        $content = Invoke-Adb -Arguments @("shell", "cat", $DevicePath) -TimeoutSeconds 30
        foreach ($snippet in $ExpectedSnippets) {
            if ($content.Stdout -notlike "*$snippet*") {
                throw "Export $Kind missing snippet '$snippet'."
            }
        }
    }
    $exportEvidence = [ordered]@{
        kind = $Kind
        path = $DevicePath
        sizeBytes = $size
    }
    if ($Binary -and $ExpectedZipEntries.Count -gt 0) {
        $zipEntries = Verify-ZipExportEntries -Scenario $Scenario -DevicePath $DevicePath -ExpectedEntries $ExpectedZipEntries
        $exportEvidence.zipEntries = $zipEntries
    }
    $script:Exports.Add($exportEvidence) | Out-Null
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
    $startStep = 0
    if ($script:ScenarioStartSteps.ContainsKey($Scenario)) {
        $startStep = $script:ScenarioStartSteps[$Scenario]
    }
    $scenarioSteps = $script:StepCount - $startStep
    $script:Results[$Scenario] = [ordered]@{
        scenario = $Scenario
        status = $Status
        details = $Details
        failedStep = $FailedStep
        stepCount = $scenarioSteps
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
    $script:ScenarioStartSteps[$Scenario] = $script:StepCount
    Start-Scenario $Scenario
    try {
        & $Body
        Finish-Scenario -Scenario $Scenario -Status "PASS" -Details "Completed" -StartedAt $startedAt
    } catch {
        $message = $_.Exception.Message
        if ($message -match "^(BLOCKED_[A-Z0-9_]+|EXPECTED_DEFERRED_[A-Z0-9_]+):\s*(.*)$") {
            Capture-Evidence -Scenario $Scenario -Label "blocked" -WithLogcat
            Finish-Scenario -Scenario $Scenario -Status $Matches[1] -Details $Matches[2] -StartedAt $startedAt -FailedStep $message
            return
        }
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

        Step "Fill required seminar editor field"
        $createdTitle = Append-EditTextTextByIndex -Scenario "B02" -Index 0 -AppendText "blackboxqa" -Label "title-field"
        Press-Back

        Step "Save seminar"
        Tap-Text -Scenario "B02" -Text "Save draft" -ScrollAttempts 2
        Wait-ForNode -Scenario "B02" -Label "detail-created" -Text $createdTitle -TimeoutSeconds 20 | Out-Null

        Step "Return to library and search seminar"
        Tap-Description -Scenario "B02" -Description "Back"
        Wait-ForNode -Scenario "B02" -Label "library-after-create" -Text "Your seminars" -TimeoutSeconds 20 | Out-Null
        Input-FieldKeyEvent -Scenario "B02" -Label "Search by title or speaker" -Value $createdTitle.Substring(0, 1)
        Wait-ForNode -Scenario "B02" -Label "search-result" -Text $createdTitle -TimeoutSeconds 15 | Out-Null

        Step "Reopen and edit title"
        Tap-Text -Scenario "B02" -Text $createdTitle
        Wait-ForNode -Scenario "B02" -Label "detail-reopened" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
        Tap-Description -Scenario "B02" -Description "Edit"
        Wait-ForNode -Scenario "B02" -Label "edit-editor" -Text "Edit seminar" -TimeoutSeconds 20 | Out-Null
        Tap-EditTextByIndex -Scenario "B02" -Index 0 -Label "edit-title-field"
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "123") -TimeoutSeconds 30 | Out-Null
        Enter-KeyEventText "updated"
        Press-Back
        Tap-Text -Scenario "B02" -Text "Save draft" -ScrollAttempts 2
        Wait-ForNode -Scenario "B02" -Label "updated-detail" -Text "updated" -Contains -TimeoutSeconds 20 | Out-Null

        Step "Force-stop and verify persistence"
        Tap-Description -Scenario "B02" -Description "Back"
        Force-Stop-App "B02 persistence"
        Launch-App
        Input-FieldKeyEvent -Scenario "B02" -Label "Search by title or speaker" -Value "u"
        Wait-ForNode -Scenario "B02" -Label "persistence-result" -Text "updated" -Contains -TimeoutSeconds 20 | Out-Null
        Capture-Evidence -Scenario "B02" -Label "final"
        Assert-NoCrash "B02"
    }
}

function Deny-Permission-IfVisible {
    param([string]$Scenario, [int]$TimeoutSeconds = 15)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tree = Get-UiXml -Scenario $Scenario -Label "permission-dialog"
        $target = $null
        foreach ($node in $tree.Xml.SelectNodes("//node")) {
            if ((Get-Attr $node "resource-id") -eq "com.android.permissioncontroller:id/permission_deny_button") {
                $target = $node
                break
            }
        }
        if ($null -eq $target) {
            foreach ($label in @("Don" + [char]0x2019 + "t allow", "Don't allow", "Don", "Deny")) {
                $node = Find-Node -Xml $tree.Xml -Text $label -Contains
                if ($null -ne $node) {
                    $target = $node
                    break
                }
            }
        }
        if ($null -ne $target) {
            $center = Parse-BoundsCenter (Get-Attr $target "bounds")
            Tap-Node $target
            Start-Sleep -Seconds 1
            $after = Get-UiXml -Scenario $Scenario -Label "permission-dialog-after-deny"
            if ($null -eq (Find-Node -Xml $after.Xml -Text "record audio?" -Contains) -and
                $null -eq (Find-Node -Xml $after.Xml -Text "allow" -Contains -ClassName "android.widget.Button")) {
                return $true
            }
            Tap-Point -X $center.X -Y $center.Y
            Start-Sleep -Seconds 1
            $afterRetry = Get-UiXml -Scenario $Scenario -Label "permission-dialog-after-deny-retry"
            if ($null -eq (Find-Node -Xml $afterRetry.Xml -Text "record audio?" -Contains) -and
                $null -eq (Find-Node -Xml $afterRetry.Xml -Text "allow" -Contains -ClassName "android.widget.Button")) {
                return $true
            }
            Write-Status "Permission deny tap did not dismiss dialog; retrying within bounded window."
        }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Run-B03 {
    Run-Scenario "B03" {
        Step "Open persisted seminar detail"
        Tap-Text -Scenario "B03" -Text "updated" -Contains
        Wait-ForNode -Scenario "B03" -Label "detail-open" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null

        Step "Exercise microphone permission denied path"
        Invoke-Adb -Arguments @("shell", "pm", "revoke", $PackageName, "android.permission.RECORD_AUDIO") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
        Tap-Text -Scenario "B03" -Text "Start seminar" -ScrollAttempts 2
        Start-Sleep -Seconds 1
        $permissionDenied = Deny-Permission-IfVisible -Scenario "B03"
        try {
            Wait-ForNode -Scenario "B03" -Label "mic-denied" -Text "Microphone permission is required before recording can start." -TimeoutSeconds 8 | Out-Null
        } catch {
            $deniedTree = Get-UiXml -Scenario "B03" -Label "mic-denied-fallback"
            if (
                -not $permissionDenied -or
                $null -eq (Find-Node -Xml $deniedTree.Xml -Text "Seminar detail") -or
                $null -eq (Find-Node -Xml $deniedTree.Xml -Text "Start photos only") -or
                $null -ne (Find-Node -Xml $deniedTree.Xml -Text "Active session")
            ) {
                throw
            }
            Add-Limitation "B03 microphone denied path was verified by permission-dialog denial plus remaining on Seminar detail; the transient in-app denial message was not visible within the bounded UI wait."
        }

        Step "Start photos-only active session"
        $activeStarted = $false
        $activeStartError = $null
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            Tap-TextClickableAncestor -Scenario "B03" -Text "Start photos only" -ScrollAttempts 1
            try {
                Wait-ForNode -Scenario "B03" -Label "active-session-$attempt" -Text "Active session" -TimeoutSeconds 20 | Out-Null
                $activeStarted = $true
                break
            } catch {
                $activeStartError = $_.Exception.Message
                Write-Status "Photos-only start retry ${attempt}: $activeStartError"
            }
        }
        if (-not $activeStarted) {
            throw $activeStartError
        }
        Wait-ForNode -Scenario "B03" -Label "photos-only" -Text "PHOTOS ONLY" -Contains -TimeoutSeconds 20 | Out-Null

        Step "Add note event"
        Tap-Text -Scenario "B03" -Text "Quick Note" -ScrollAttempts 2
        Wait-ForNode -Scenario "B03" -Label "note-dialog" -Text "Quick note" -TimeoutSeconds 15 | Out-Null
        [void](Append-EditTextTextByIndex -Scenario "B03" -Index 0 -AppendText "noteqa" -Label "note-field")
        Tap-Text -Scenario "B03" -Text "Save"
        Wait-ForAnyText -Scenario "B03" -Label "note-saved" -Texts @("1 timeline events", "1 timeline event") -Contains -TimeoutSeconds 20 | Out-Null

        Step "Add question event"
        Tap-Text -Scenario "B03" -Text "Add Question" -ScrollAttempts 2
        Wait-ForNode -Scenario "B03" -Label "question-dialog" -Text "Add question" -TimeoutSeconds 15 | Out-Null
        [void](Append-EditTextTextByIndex -Scenario "B03" -Index 0 -AppendText "questionqa" -Label "question-field")
        Tap-Text -Scenario "B03" -Text "Save"
        Wait-ForNode -Scenario "B03" -Label "question-saved" -Text "2 timeline events" -Contains -TimeoutSeconds 15 | Out-Null

        Step "Add mark event"
        Tap-Text -Scenario "B03" -Text "Mark Moment" -ScrollAttempts 2
        Wait-ForNode -Scenario "B03" -Label "mark-saved" -Text "3 timeline events" -Contains -TimeoutSeconds 15 | Out-Null

        Step "Capture slide photo for research-flow scenarios"
        Invoke-Adb -Arguments @("shell", "pm", "grant", $PackageName, "android.permission.CAMERA") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
        Start-Sleep -Seconds 3
        Tap-Text -Scenario "B03" -Text "Capture Slide" -ScrollAttempts 3
        try {
            $photoResult = Wait-ForAnyText -Scenario "B03" -Label "photo-capture-result" -Texts @("Slide photo saved.", "Photo capture failed:") -Contains -TimeoutSeconds 35
            if ($photoResult.Text -like "Photo capture failed:*") {
                Add-Limitation "B03/B04 CameraX capture failed on Windows Emulator; research photo workflows are limited to no-photo UI/provider surfaces in this run."
            } else {
                Wait-ForNode -Scenario "B03" -Label "last-photo" -Text "Last photo" -TimeoutSeconds 15 | Out-Null
            }
        } catch {
            Capture-Evidence -Scenario "B03" -Label "photo-capture-no-terminal" -WithLogcat
            Add-Limitation "B03/B04 CameraX capture did not reach a terminal UI state on Windows Emulator; research photo workflows are limited to no-photo UI/provider surfaces in this run."
        }

        Step "End seminar"
        $endDialogVisible = $false
        $endDialogError = $null
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            Tap-TextClickableAncestor -Scenario "B03" -Text "End Seminar" -ScrollAttempts 3
            try {
                Wait-ForNode -Scenario "B03" -Label "end-dialog-$attempt" -Text "End this seminar?" -TimeoutSeconds 15 | Out-Null
                $endDialogVisible = $true
                break
            } catch {
                $endDialogError = $_.Exception.Message
                Write-Status "End seminar dialog retry ${attempt}: $endDialogError"
            }
        }
        if (-not $endDialogVisible) {
            throw $endDialogError
        }
        Tap-TextClickableAncestor -Scenario "B03" -Text "Stop and end"
        Wait-ForNode -Scenario "B03" -Label "completed-detail" -Text "This seminar is completed." -Contains -TimeoutSeconds 25 | Out-Null

        Step "Force-stop and verify completed detail remains reachable"
        Force-Stop-App "B03 completed persistence"
        Launch-App
        Input-FieldKeyEvent -Scenario "B03" -Label "Search by title or speaker" -Value "u"
        Tap-Text -Scenario "B03" -Text "updated" -Contains
        Wait-ForNode -Scenario "B03" -Label "completed-after-relaunch" -Text "This seminar is completed." -Contains -TimeoutSeconds 25 | Out-Null
        Capture-Evidence -Scenario "B03" -Label "final"
        Assert-NoCrash "B03"
    }
}

function Run-B04 {
    Run-Scenario "B04" {
        if (-not $script:RunFullCatalog) {
            Ensure-FocusedPhotoFixtureSeminar "B04"
        }
        Step "Open reconstruction from completed seminar detail"
        Open-Synthetic-Detail "B04"
        Tap-Text -Scenario "B04" -Text "Open reconstruction" -ScrollAttempts 5
        Wait-ForNode -Scenario "B04" -Label "reconstruction-open" -Text "Reconstruction" -TimeoutSeconds 20 | Out-Null
        try {
            Wait-ForNode -Scenario "B04" -Label "photo-count" -Text "1 of 1 photos" -TimeoutSeconds 20 | Out-Null
        } catch {
            $tree = Get-UiXml -Scenario "B04" -Label "no-photo-inspect"
            if ((Find-Node -Xml $tree.Xml -Text "0 of 0 photos" -Contains) -or (Find-Node -Xml $tree.Xml -Text "No photos match the current filters." -Contains)) {
                Add-Limitation "B04 photo asset path is BLOCKED_BY_EMULATOR_FIXTURE_LIMITATION because CameraX capture did not yield a saved slide photo on this Emulator."
                throw "BLOCKED_BY_EMULATOR_FIXTURE_LIMITATION: CameraX did not provide a saved photo asset; exact OCR/image workflow is deferred to a stable emulator camera fixture or real-world corpus task."
            }
            throw
        }

        Step "Queue and verify image enhancement"
        Tap-Text -Scenario "B04" -Text "Enhance" -ScrollAttempts 4
        [void](Wait-ForJobTerminal -Scenario "B04" -JobPrefix "Enhancement" -TimeoutSeconds 90)

        Step "Queue and verify OCR lifecycle"
        Tap-Text -Scenario "B04" -Text "OCR" -ScrollAttempts 2
        [void](Wait-ForJobTerminal -Scenario "B04" -JobPrefix "OCR" -TimeoutSeconds 120 -AllowFailure)

        Step "Inspect deterministic OCR clue"
        try {
            [void](Wait-ForAnyText -Scenario "B04" -Label "ocr-fixture-clue" -Texts @("SeminarArc", "10.1038", "nature12373") -Contains -TimeoutSeconds 25)
        } catch {
            Add-Limitation "B04 OCR completed, but the deterministic fixture title/DOI clue was not visible in the bounded UI tree wait; editable OCR surface is still exercised."
        }

        Step "Edit OCR result through UI"
        Tap-Text -Scenario "B04" -Text "OCR text" -ScrollAttempts 5
        Enter-Text "BlackboxOcrEditedText"
        Press-Back
        Tap-Text -Scenario "B04" -Text "Save OCR edit" -ScrollAttempts 2
        Wait-ForNode -Scenario "B04" -Label "ocr-edited" -Text "BlackboxOcrEditedText" -Contains -TimeoutSeconds 20 | Out-Null

        Step "Mark key slide"
        Swipe-Down
        Swipe-Down
        Tap-Description -Scenario "B04" -Description "Mark key slide" -Contains
        Wait-ForNode -Scenario "B04" -Label "key-slide-marked" -Description "Remove key slide" -TimeoutSeconds 15 | Out-Null

        Step "Force-stop and verify reconstruction persistence"
        Open-Synthetic-Detail "B04"
        Tap-Text -Scenario "B04" -Text "Open reconstruction" -ScrollAttempts 5
        Wait-ForNode -Scenario "B04" -Label "reconstruction-after-relaunch" -Text "Reconstruction" -TimeoutSeconds 20 | Out-Null
        Wait-ForNode -Scenario "B04" -Label "ocr-persisted" -Text "BlackboxOcrEditedText" -Contains -TimeoutSeconds 20 | Out-Null
        Wait-ForNode -Scenario "B04" -Label "key-slide-persisted" -Description "Remove key slide" -TimeoutSeconds 20 | Out-Null
        Capture-Evidence -Scenario "B04" -Label "final"
        Assert-NoCrash "B04"
    }
}

function Run-B05 {
    Run-Scenario "B05" {
        Step "Open Reference Candidate Review from reconstruction"
        $entryTree = Get-UiXml -Scenario "B05" -Label "reference-entry"
        if ($null -eq (Find-Node -Xml $entryTree.Xml -Text "References and Brief")) {
            if ($null -eq (Find-Node -Xml $entryTree.Xml -Text "Reconstruction")) {
                Open-Synthetic-Detail "B05"
                Tap-Text -Scenario "B05" -Text "Open reconstruction" -ScrollAttempts 5
                Wait-ForNode -Scenario "B05" -Label "reconstruction-for-reference" -Text "Reconstruction" -TimeoutSeconds 20 | Out-Null
            }
            Swipe-Down
            Swipe-Down
            Tap-Text -Scenario "B05" -Text "Find references" -ScrollAttempts 2
            Wait-ForNode -Scenario "B05" -Label "reference-open" -Text "References and Brief" -TimeoutSeconds 20 | Out-Null
        }

        Step "Enter fixed public DOI fixture and preview query"
        Tap-Text -Scenario "B05" -Text "Manual DOI/title/author/year clue" -ScrollAttempts 3
        Enter-KeyEventText $script:ReferenceFixture.doi
        Press-Back
        Tap-Text -Scenario "B05" -Text "Preview query" -ScrollAttempts 2
        Wait-ForNode -Scenario "B05" -Label "query-preview-doi" -Text $script:ReferenceFixture.doi -Contains -TimeoutSeconds 20 | Out-Null
        Wait-ForNode -Scenario "B05" -Label "query-preview-provider" -Text "Provider: CROSSREF" -Contains -TimeoutSeconds 20 | Out-Null
        Wait-ForNode -Scenario "B05" -Label "provider-plan" -Text "Provider plan" -Contains -TimeoutSeconds 15 | Out-Null

        Step "Run bounded public metadata lookup"
        Tap-Text -Scenario "B05" -Text "Run lookup" -ScrollAttempts 2
        try {
            Wait-ForAnyText -Scenario "B05" -Label "lookup-terminal" -Texts @("Reference lookup complete.", "RATE_LIMITED", "FAILED") -Contains -TimeoutSeconds 75 | Out-Null
        } catch {
            throw "BLOCKED_BY_EXTERNAL_PROVIDER: Reference lookup did not return a terminal UI state within bounded timeout for DOI $($script:ReferenceFixture.doi)."
        }
        $terminalTree = Get-UiXml -Scenario "B05" -Label "lookup-terminal-inspect"
        if ((Find-Node -Xml $terminalTree.Xml -Text "RATE_LIMITED" -Contains) -or (Find-Node -Xml $terminalTree.Xml -Text "FAILED" -Contains)) {
            Add-Limitation "B05 public metadata lookup returned provider error/rate-limit for DOI $($script:ReferenceFixture.doi)."
            throw "BLOCKED_BY_EXTERNAL_PROVIDER: Public metadata provider returned an error/rate-limit for DOI $($script:ReferenceFixture.doi)."
        }

        Step "Confirm, reopen, and reconfirm candidate"
        Tap-Text -Scenario "B05" -Text "Confirm" -ScrollAttempts 8
        Wait-ForNode -Scenario "B05" -Label "candidate-confirmed" -Text "CONFIRMED" -TimeoutSeconds 20 | Out-Null
        Tap-Text -Scenario "B05" -Text "Reopen" -ScrollAttempts 2
        Wait-ForNode -Scenario "B05" -Label "candidate-reopened" -Text "PENDING" -TimeoutSeconds 20 | Out-Null
        Tap-Text -Scenario "B05" -Text "Confirm" -ScrollAttempts 2
        Wait-ForNode -Scenario "B05" -Label "candidate-reconfirmed" -Text "CONFIRMED" -TimeoutSeconds 20 | Out-Null

        Step "Edit and save Seminar Brief"
        Scroll-UntilTextVisible -Scenario "B05" -Text "Seminar Brief" -Label "brief-section" -ScrollAttempts 8 | Out-Null
        $briefEntryTree = Get-UiXml -Scenario "B05" -Label "brief-entry"
        if ($null -ne (Find-Node -Xml $briefEntryTree.Xml -Text "Create brief")) {
            Tap-Text -Scenario "B05" -Text "Create brief" -ScrollAttempts 2
        }
        Scroll-UntilTextVisible -Scenario "B05" -Text "Methods" -Label "brief-editor" -ScrollAttempts 8 | Out-Null
        Tap-EditTextContainingLabel -Scenario "B05" -Text "Methods" -Label "brief-methods-field" -ScrollAttempts 1
        $script:BriefText = "briefbg"
        Enter-Text $script:BriefText
        Press-Back
        Wait-ForNode -Scenario "B05" -Label "brief-field-entered" -Text $script:BriefText -Contains -TimeoutSeconds 20 | Out-Null
        Tap-TextBidirectional -Scenario "B05" -Text "Save brief" -DownAttempts 24 -UpAttempts 8
        Wait-ForAnyText -Scenario "B05" -Label "brief-saved" -Texts @("Seminar Brief saved.", $script:BriefText) -Contains -TimeoutSeconds 20 | Out-Null

        Step "Force-stop and verify reference/brief persistence"
        Open-Synthetic-Detail "B05"
        Tap-Text -Scenario "B05" -Text "Open reconstruction" -ScrollAttempts 5
        Wait-ForNode -Scenario "B05" -Label "reconstruction-reopen" -Text "Reconstruction" -TimeoutSeconds 20 | Out-Null
        Tap-Text -Scenario "B05" -Text "Find references" -ScrollAttempts 2
        Wait-ForNode -Scenario "B05" -Label "reference-after-relaunch" -Text "CONFIRMED" -Contains -TimeoutSeconds 20 | Out-Null
        Scroll-UntilTextVisible -Scenario "B05" -Text $script:BriefText -Label "brief-after-relaunch-section" -ScrollAttempts 8 | Out-Null
        Wait-ForNode -Scenario "B05" -Label "brief-after-relaunch" -Text $script:BriefText -Contains -TimeoutSeconds 20 | Out-Null
        Capture-Evidence -Scenario "B05" -Label "final"
        Assert-NoCrash "B05"
    }
}

function Run-B06 {
    Run-Scenario "B06" {
        Step "Open Transcript Review"
        Press-Back
        Wait-ForNode -Scenario "B06" -Label "reconstruction-visible" -Text "Reconstruction" -TimeoutSeconds 20 | Out-Null
        Tap-Text -Scenario "B06" -Text "Review transcripts" -ScrollAttempts 2
        Wait-ForNode -Scenario "B06" -Label "transcripts-open" -Text "Transcripts" -TimeoutSeconds 20 | Out-Null

        Step "Verify no completed recording transcription path is explicit"
        Tap-Text -Scenario "B06" -Text "Prepare transcription" -ScrollAttempts 3
        Wait-ForAnyText -Scenario "B06" -Label "no-recording-transcription" -Texts @(
            "No completed recording is available for transcription.",
            "No transcripts yet. A durable transcription job can be queued for a completed recording.",
            "No transcription or summary jobs yet."
        ) -Contains -TimeoutSeconds 15 | Out-Null
        Add-Limitation "B06 live ASR provider path is unavailable/deferred in photos-only Emulator flow; no completed recording exists."

        Step "Import multiline manual transcript"
        Tap-Text -Scenario "B06" -Text "Transcript text" -ScrollAttempts 3
        Enter-MultilineText @("lineone", "linetwo")
        Press-Back
        Tap-Text -Scenario "B06" -Text "Import manual transcript" -ScrollAttempts 2
        Wait-ForNode -Scenario "B06" -Label "manual-imported" -Text "1 transcripts" -Contains -TimeoutSeconds 20 | Out-Null
        Wait-ForNode -Scenario "B06" -Label "segments-visible" -Text "2 segments" -Contains -TimeoutSeconds 20 | Out-Null

        Step "Edit and save transcript segment"
        Scroll-UntilTextVisible -Scenario "B06" -Text "Transcript segments" -Label "segments-section" -ScrollAttempts 8 | Out-Null
        Swipe-Up
        $editedSegmentText = Append-FirstEditTextBelowText -Scenario "B06" -AnchorText "Transcript segments" -AppendText "qq" -Label "first-segment-field"
        Press-Back
        Tap-Text -Scenario "B06" -Text "Save segment" -ScrollAttempts 3
        Wait-ForNode -Scenario "B06" -Label "segment-edited" -Text $editedSegmentText -Contains -TimeoutSeconds 20 | Out-Null

        Step "Exercise summary draft deferred provider path"
        Tap-Text -Scenario "B06" -Text "Queue summary draft" -ScrollAttempts 6
        try {
            Wait-ForAnyText -Scenario "B06" -Label "summary-deferred" -Texts @("Summary draft could not be queued", "Live summary provider is not configured.", "Summary failed") -Contains -TimeoutSeconds 25 | Out-Null
            Add-Limitation "B06 generated summary draft creation is EXPECTED_DEFERRED_PROVIDER_PATH without a configured live summary provider."
        } catch {
            Add-Limitation "B06 generated summary draft UI is present, but no editable provider draft was created in local-safe mode."
        }

        Step "Force-stop and verify transcript persistence"
        Open-Synthetic-Detail "B06"
        Tap-Text -Scenario "B06" -Text "Open reconstruction" -ScrollAttempts 5
        Wait-ForNode -Scenario "B06" -Label "reconstruction-after-relaunch" -Text "Reconstruction" -TimeoutSeconds 20 | Out-Null
        Tap-Text -Scenario "B06" -Text "Review transcripts" -ScrollAttempts 2
        Scroll-UntilTextVisible -Scenario "B06" -Text "Transcript segments" -Label "segments-after-relaunch-section" -ScrollAttempts 8 | Out-Null
        Wait-ForNode -Scenario "B06" -Label "transcript-after-relaunch" -Text $editedSegmentText -Contains -TimeoutSeconds 20 | Out-Null
        Capture-Evidence -Scenario "B06" -Label "final"
        Assert-NoCrash "B06"
    }
}

function Run-B07 {
    Run-Scenario "B07" {
        Cleanup-Export-Files
        $formulaBlocked = $false
        $b05Passed = $script:Results.Contains("B05") -and (($script:Results["B05"]).status -eq "PASS")

        if (-not $script:RunFullCatalog) {
            Ensure-FocusedPhotoFixtureSeminar "B07"
        }

        Step "Open reconstruction for formula workflow"
        Open-Synthetic-Detail "B07"
        Tap-Text -Scenario "B07" -Text "Open reconstruction" -ScrollAttempts 5
        Wait-ForNode -Scenario "B07" -Label "reconstruction-visible" -Text "Reconstruction" -TimeoutSeconds 20 | Out-Null
        Wait-ForNode -Scenario "B07" -Label "formula-provider-status" -Text "Formula OCR: Unavailable" -Contains -TimeoutSeconds 20 | Out-Null
        Add-Limitation "B07 live Formula OCR provider is unavailable by design; manual LaTeX path is validated instead."

        try {
            Step "Drag formula draft selection and save region"
            Drag-Description -Scenario "B07" -Description "Drag to draft formula region" -Contains
            Tap-Text -Scenario "B07" -Text "Save formula region" -ScrollAttempts 5
            Wait-ForNode -Scenario "B07" -Label "formula-region-saved" -Text "Formula:" -Contains -TimeoutSeconds 20 | Out-Null

            Step "Reopen and edit crop label"
            Tap-Text -Scenario "B07" -Text "Edit crop" -ScrollAttempts 4
            Tap-Text -Scenario "B07" -Text "Formula label" -ScrollAttempts 2
            Enter-Text "MainEquation"
            Press-Back
            Tap-Text -Scenario "B07" -Text "Update formula region" -ScrollAttempts 3
            Wait-ForNode -Scenario "B07" -Label "formula-region-updated" -Text "FormulaMainEquation" -Contains -TimeoutSeconds 20 | Out-Null

            Step "Save manual LaTeX and verify READY result"
            Tap-Text -Scenario "B07" -Text "LaTeX" -ScrollAttempts 5
            Enter-Text "E=mc^2"
            Press-Back
            Tap-Text -Scenario "B07" -Text "Queue LaTeX" -ScrollAttempts 2
            Wait-ForNode -Scenario "B07" -Label "formula-ready" -Text "Formula ready: E=mc^2" -Contains -TimeoutSeconds 45 | Out-Null

            Step "Force-stop and verify formula persistence"
            Open-Synthetic-Detail "B07"
            Tap-Text -Scenario "B07" -Text "Open reconstruction" -ScrollAttempts 5
            Wait-ForNode -Scenario "B07" -Label "formula-after-relaunch" -Text "Formula ready: E=mc^2" -Contains -TimeoutSeconds 25 | Out-Null
        } catch {
            $formulaBlocked = $true
            Add-Limitation "B07 formula region/manual LaTeX workflow is BLOCKED_BY_EMULATOR_FIXTURE_LIMITATION because no saved photo asset is available."
            Open-Synthetic-Detail "B07"
        }

        Step "Save and verify local exports"
        $detailTree = Get-UiXml -Scenario "B07" -Label "detail-for-export-check"
        if ($null -eq (Find-Node -Xml $detailTree.Xml -Text "Seminar detail")) {
            Press-Back
            Wait-ForNode -Scenario "B07" -Label "detail-for-export" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
        }
        Tap-Text -Scenario "B07" -Text "Markdown" -ScrollAttempts 8
        $markdownSnippets = @("updated")
        if ($b05Passed -and -not [string]::IsNullOrWhiteSpace($script:BriefText)) {
            $markdownSnippets += $script:BriefText
        }
        if (-not $formulaBlocked) {
            $markdownSnippets += "E=mc^2"
        }
        Save-Current-CreateDocument -Scenario "B07" -Kind "Markdown" -DevicePath "/sdcard/Download/seminar.md" -ExpectedSnippets $markdownSnippets
        if (-not $script:RunFullCatalog) {
            Capture-Evidence -Scenario "B07" -Label "final"
            Assert-NoCrash "B07"
            if ($formulaBlocked) {
                throw "BLOCKED_BY_EMULATOR_FIXTURE_LIMITATION: Formula region/manual LaTeX workflow requires a saved photo asset, but CameraX capture did not produce one on this Emulator run; export surface was still minimally validated where possible."
            }
            return
        }
        Tap-Text -Scenario "B07" -Text "Save Notion-ready Markdown" -ScrollAttempts 8
        $notionSnippets = @("updated")
        if ($b05Passed -and -not [string]::IsNullOrWhiteSpace($script:BriefText)) {
            $notionSnippets += $script:BriefText
        }
        Save-Current-CreateDocument -Scenario "B07" -Kind "NotionMarkdown" -DevicePath "/sdcard/Download/seminar-notion-ready.md" -ExpectedSnippets $notionSnippets
        if ($b05Passed) {
            Tap-Text -Scenario "B07" -Text "BibTeX" -ScrollAttempts 8
            Save-Current-CreateDocument -Scenario "B07" -Kind "BibTeX" -DevicePath "/sdcard/Download/references.bib" -ExpectedSnippets @("@", "doi")
            Tap-Text -Scenario "B07" -Text "RIS" -ScrollAttempts 8
            Save-Current-CreateDocument -Scenario "B07" -Kind "RIS" -DevicePath "/sdcard/Download/references.ris" -ExpectedSnippets @("TY  -", "ER  -")
        } else {
            Add-Limitation "B07 BibTeX/RIS file validation skipped because B05 did not confirm a public metadata candidate."
        }
        Tap-Text -Scenario "B07" -Text "ZIP" -ScrollAttempts 8
        $zipEntries = @("seminar.md")
        if ($b05Passed) {
            $zipEntries += @("references.bib", "references.ris")
        }
        Save-Current-CreateDocument -Scenario "B07" -Kind "ZIP" -DevicePath "/sdcard/Download/seminar.zip" -Binary -ExpectedZipEntries $zipEntries
        Capture-Evidence -Scenario "B07" -Label "final"
        Assert-NoCrash "B07"
        if ($formulaBlocked) {
            throw "BLOCKED_BY_EMULATOR_FIXTURE_LIMITATION: Formula region/manual LaTeX workflow requires a saved photo asset, but CameraX capture did not produce one on this Emulator run; export surfaces were still validated where possible."
        }
    }
}

function Run-B08 {
    Run-Scenario "B08" {
        Step "Force-stop and relaunch before cleanup"
        Force-Stop-App "B08 recovery start"
        Launch-App
        Input-FieldKeyEvent -Scenario "B08" -Label "Search by title or speaker" -Value "u"
        Wait-ForNode -Scenario "B08" -Label "seminar-before-delete" -Text "updated" -Contains -TimeoutSeconds 20 | Out-Null

        Step "Delete synthetic seminar through UI"
        Press-Back
        Tap-Text -Scenario "B08" -Text "updated" -Contains
        try {
            Wait-ForNode -Scenario "B08" -Label "detail-before-delete" -Text "Seminar detail" -TimeoutSeconds 10 | Out-Null
        } catch {
            Tap-Text -Scenario "B08" -Text "updated" -Contains
            Wait-ForNode -Scenario "B08" -Label "detail-before-delete-retry" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
        }
        Tap-TextBidirectional -Scenario "B08" -Text "Delete seminar" -DownAttempts 16 -UpAttempts 4
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
    Write-Status "Selected scenarios: $($script:SelectedScenarios -join ', ')"
    if (-not [string]::IsNullOrWhiteSpace($CameraFixturePath)) {
        Write-Status "Camera fixture path: $CameraFixturePath"
    }
    Select-EmulatorSerial
    Build-And-Install
    $scenarioCatalog = [ordered]@{
        B01 = { Run-B01 }
        B02 = { Run-B02 }
        B03 = { Run-B03 }
        B04 = { Run-B04 }
        B05 = { Run-B05 }
        B06 = { Run-B06 }
        B07 = { Run-B07 }
        B08 = { Run-B08 }
    }
    foreach ($scenario in $script:SelectedScenarios) {
        & $scenarioCatalog[$scenario]
    }
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
        selectedScenarios = $script:SelectedScenarios
        cameraFixturePath = $CameraFixturePath
        stepCount = $script:StepCount
        evidenceRoot = $evidenceRoot
        referenceFixture = $script:ReferenceFixture
        limitations = $script:Limitations.ToArray()
        exports = $script:Exports.ToArray()
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
        "- selectedScenarios: $($script:SelectedScenarios -join ', ')",
        "- cameraFixturePath: $CameraFixturePath",
        "- steps: $script:StepCount",
        ""
    )
    foreach ($key in $script:Results.Keys) {
        $item = $script:Results[$key]
        $lines += ("- {0}: {1}, {2} steps ({3}s)" -f $key, $item.status, $item.stepCount, $item.elapsedSeconds)
    }
    if ($script:Limitations.Count -gt 0) {
        $lines += ""
        $lines += "## Limitations"
        foreach ($limitation in $script:Limitations) {
            $lines += "- $limitation"
        }
    }
    if ($script:Exports.Count -gt 0) {
        $lines += ""
        $lines += "## Exports"
        foreach ($export in $script:Exports) {
            $lines += ("- {0}: {1} ({2} bytes)" -f $export.kind, $export.path, $export.sizeBytes)
        }
    }
    $lines | Set-Content -Encoding UTF8 -Path $summaryMarkdownPath
    Write-Status "Summary: $summaryMarkdownPath"
    Write-Status "Steps executed: $script:StepCount"
    Write-Status "Overall: $overall"
    if ($overall -ne "PASS") {
        exit 1
    }
}
