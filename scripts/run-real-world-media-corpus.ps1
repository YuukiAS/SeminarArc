param(
    [string]$PackageName = "com.yuukias.seminararc.internal",
    [string]$Serial = "",
    [string]$FixtureRoot = "",
    [int]$MaxPhotos = 10,
    [int]$MaxPdfs = 2,
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

if ([string]::IsNullOrWhiteSpace($FixtureRoot)) {
    $FixtureRoot = Join-Path $repoRoot "private\blackbox-fixtures\real-world"
} elseif (-not [System.IO.Path]::IsPathRooted($FixtureRoot)) {
    $FixtureRoot = Join-Path $repoRoot $FixtureRoot
}

$adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$gradlew = Join-Path $repoRoot "gradlew.bat"
$runId = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$evidenceRoot = Join-Path $repoRoot "private\blackbox-acceptance\real-world-$runId"
$manifestPath = Join-Path $evidenceRoot "local-manifest.json"
$summaryPath = Join-Path $evidenceRoot "summary.json"
$summaryMdPath = Join-Path $evidenceRoot "summary.md"
$deviceFixtureDir = "/sdcard/Download/SeminarArcRealWorld/$runId"
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null

$script:StepCount = 0
$script:SelectedSerial = $null
$script:Limitations = New-Object System.Collections.Generic.List[string]
$script:RunStatus = "RUNNING"
$script:PhotoSamples = @()
$script:PdfSamples = @()
$script:FormulaSampleId = $null
$script:PdfLifecycle = [ordered]@{
    attempted = $false
    import = "NOT_RUN"
    persistence = "NOT_RUN"
    replace = "NOT_RUN"
    remove = "NOT_RUN"
}

function Write-Status {
    param([string]$Message)
    $now = (Get-Date).ToString("HH:mm:ss")
    Write-Host "[$now] $Message"
}

function Step {
    param([string]$Message)
    $script:StepCount += 1
    Write-Status ("STEP {0:000}: {1}" -f $script:StepCount, $Message)
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

function Save-Text {
    param([string]$Path, [string]$Text)
    $Text | Set-Content -Encoding UTF8 -Path $Path
}

function Get-ImageSize {
    param([string]$Path)
    try {
        Add-Type -AssemblyName System.Drawing -ErrorAction SilentlyContinue
        $image = [System.Drawing.Image]::FromFile($Path)
        try {
            return [ordered]@{ width = $image.Width; height = $image.Height }
        } finally {
            $image.Dispose()
        }
    } catch {
        return [ordered]@{ width = $null; height = $null; readError = $_.Exception.Message }
    }
}

function New-SampleInventory {
    Step "Scan private real-world fixtures and create local manifest"
    if (-not (Test-Path -LiteralPath $FixtureRoot)) {
        throw "Fixture root not found: $FixtureRoot"
    }
    $photoRoot = Join-Path $FixtureRoot "photos"
    $pdfRoot = Join-Path $FixtureRoot "pdfs"
    $photoExtensions = @(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif")
    $photos = @()
    if (Test-Path -LiteralPath $photoRoot) {
        $photos = @(Get-ChildItem -LiteralPath $photoRoot -Recurse -File | Where-Object {
            $photoExtensions -contains $_.Extension.ToLowerInvariant()
        } | Sort-Object FullName | Select-Object -First $MaxPhotos)
    }
    $pdfs = @()
    if (Test-Path -LiteralPath $pdfRoot) {
        $pdfs = @(Get-ChildItem -LiteralPath $pdfRoot -Recurse -File | Where-Object {
            $_.Extension.ToLowerInvariant() -eq ".pdf"
        } | Sort-Object FullName | Select-Object -First $MaxPdfs)
    }
    if ($photos.Count -lt 5) {
        $script:RunStatus = "NEEDS_PRIVATE_FIXTURES"
        $need = [ordered]@{
            status = $script:RunStatus
            fixtureRoot = $FixtureRoot
            photoCount = $photos.Count
            pdfCount = $pdfs.Count
            requiredPhotoCount = 5
        }
        ($need | ConvertTo-Json -Depth 8) | Set-Content -Encoding UTF8 -Path $summaryPath
        throw "NEEDS_PRIVATE_FIXTURES: found $($photos.Count) readable images; need at least 5."
    }

    $photoSamples = New-Object System.Collections.Generic.List[object]
    $index = 1
    foreach ($file in $photos) {
        $id = "RW-P{0:000}" -f $index
        $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName
        $size = Get-ImageSize -Path $file.FullName
        $deviceName = "$id$($file.Extension.ToLowerInvariant())"
        $photoSamples.Add([pscustomobject]@{
            id = $id
            kind = "photo"
            localPath = $file.FullName
            originalName = $file.Name
            sha256 = $hash.Hash
            bytes = $file.Length
            width = $size.width
            height = $size.height
            category = "unknown"
            deviceName = $deviceName
            devicePath = "$deviceFixtureDir/$deviceName"
            import = "NOT_RUN"
            enhancement = "NOT_RUN"
            enhancementLatencyMs = $null
            ocr = "NOT_RUN"
            ocrLatencyMs = $null
            ocrNonEmpty = $false
            ocrCharCount = 0
            ocrLineCount = 0
            clueCategory = "none"
            keySlide = "NOT_RUN"
            formula = "NOT_RUN"
            failureCategory = $null
        }) | Out-Null
        $index += 1
    }

    $pdfSamples = New-Object System.Collections.Generic.List[object]
    $index = 1
    foreach ($file in $pdfs) {
        $id = "RW-D{0:000}" -f $index
        $hash = Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName
        $deviceName = "$id.pdf"
        $pdfSamples.Add([pscustomobject]@{
            id = $id
            kind = "pdf"
            localPath = $file.FullName
            originalName = $file.Name
            sha256 = $hash.Hash
            bytes = $file.Length
            category = "abstract-pdf"
            deviceName = $deviceName
            devicePath = "$deviceFixtureDir/$deviceName"
        }) | Out-Null
        $index += 1
    }
    $script:PhotoSamples = @($photoSamples.ToArray())
    $script:PdfSamples = @($pdfSamples.ToArray())

    $manifest = [ordered]@{
        runId = $runId
        createdAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        fixtureRoot = $FixtureRoot
        evidenceRoot = $evidenceRoot
        privacy = "local-only ignored manifest; do not commit"
        photos = $script:PhotoSamples
        pdfs = $script:PdfSamples
    }
    ($manifest | ConvertTo-Json -Depth 12) | Set-Content -Encoding UTF8 -Path $manifestPath
    Write-Status "Selected $($script:PhotoSamples.Count) photo(s), $($script:PdfSamples.Count) PDF(s)."
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
    if (-not $SkipBuild) {
        Step "Build internal APK"
        Invoke-ProcessText -FilePath $gradlew -Arguments @(":app:assembleInternal", "--console=plain") -TimeoutSeconds 900 | Out-Null
    }
    if (-not $SkipInstall) {
        $apkPath = Join-Path $repoRoot "app\build\outputs\apk\internal\app-internal.apk"
        if (-not (Test-Path -LiteralPath $apkPath)) {
            throw "APK not found: $apkPath"
        }
        Step "Install internal APK to $script:SelectedSerial"
        Invoke-Adb -Arguments @("install", "-r", $apkPath) -TimeoutSeconds 180 | Out-Null
    }
}

function Get-EvidenceDir {
    param([string]$Name)
    $path = Join-Path $evidenceRoot $Name
    New-Item -ItemType Directory -Force -Path $path | Out-Null
    return $path
}

function Get-UiXml {
    param([string]$Area, [string]$Label)
    $dir = Get-EvidenceDir $Area
    $lastError = ""
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        try {
            $remotePath = "/sdcard/seminararc-realworld-window.xml"
            Invoke-Adb -Arguments @("shell", "uiautomator", "dump", $remotePath) -TimeoutSeconds 30 | Out-Null
            $path = Join-Path $dir "$Label-ui.xml"
            Invoke-Adb -Arguments @("pull", $remotePath, $path) -TimeoutSeconds 30 | Out-Null
            $raw = Get-Content -Raw -Path $path
            $start = $raw.IndexOf("<hierarchy")
            $end = $raw.LastIndexOf("</hierarchy>")
            if ($start -ge 0 -and $end -ge $start) {
                $xml = $raw.Substring($start, $end + "</hierarchy>".Length - $start).Trim()
                Save-Text -Path $path -Text $xml
                return [pscustomobject]@{ Xml = [xml]$xml; Path = $path }
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
    param([string]$Area, [string]$Label)
    $path = Join-Path (Get-EvidenceDir $Area) "$Label.png"
    Invoke-AdbBinary -Arguments @("exec-out", "screencap", "-p") -OutputPath $path -TimeoutSeconds 60
}

function Save-Logcat {
    param([string]$Area, [string]$Label)
    $dir = Get-EvidenceDir $Area
    $crash = Invoke-Adb -Arguments @("logcat", "-d", "-b", "crash", "-t", "500") -TimeoutSeconds 15 -IgnoreExitCode
    Save-Text -Path (Join-Path $dir "$Label-crash-logcat.txt") -Text ($crash.Stdout + $crash.Stderr)
    $pidResult = Invoke-Adb -Arguments @("shell", "pidof", "-s", $PackageName) -TimeoutSeconds 15 -IgnoreExitCode
    $appPid = $pidResult.Stdout.Trim()
    if ($appPid.Length -gt 0) {
        $appLog = Invoke-Adb -Arguments @("logcat", "-d", "-t", "500", "--pid", $appPid, "*:E") -TimeoutSeconds 15 -IgnoreExitCode
        Save-Text -Path (Join-Path $dir "$Label-app-error-logcat.txt") -Text ($appLog.Stdout + $appLog.Stderr)
    }
}

function Capture-Evidence {
    param([string]$Area, [string]$Label, [switch]$WithLogcat)
    try { Save-Screenshot -Area $Area -Label $Label | Out-Null } catch { Write-Status "Screenshot capture failed: $_" }
    try { Get-UiXml -Area $Area -Label $Label | Out-Null } catch { Write-Status "UI tree capture failed: $_" }
    if ($WithLogcat) {
        try { Save-Logcat -Area $Area -Label $Label } catch { Write-Status "Logcat capture failed: $_" }
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

function Tap-Point {
    param([int]$X, [int]$Y)
    Invoke-Adb -Arguments @("shell", "input", "tap", "$X", "$Y") -TimeoutSeconds 30 | Out-Null
}

function Tap-Node {
    param($Node)
    $center = Parse-BoundsCenter (Get-Attr $Node "bounds")
    Tap-Point -X $center.X -Y $center.Y
    Start-Sleep -Milliseconds 700
}

function Swipe-Up {
    Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "1750", "540", "650", "350") -TimeoutSeconds 30 | Out-Null
}

function Swipe-Down {
    Invoke-Adb -Arguments @("shell", "input", "swipe", "540", "650", "540", "1750", "350") -TimeoutSeconds 30 | Out-Null
}

function Press-Back {
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "4") -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 700
}

function Swipe-ToTop {
    param([int]$Attempts = 5)
    for ($i = 0; $i -lt $Attempts; $i++) {
        Swipe-Down
        Start-Sleep -Milliseconds 400
    }
}

function Enter-Text {
    param([string]$Value)
    $escaped = $Value.Replace("\", "\\").Replace(" ", "%s").Replace("&", "\&").Replace("(", "\(").Replace(")", "\)")
    Invoke-Adb -Arguments @("shell", "input", "text", $escaped) -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 400
}

function Tap-EditTextByIndex {
    param([string]$Area, [int]$Index = 0, [string]$Label = "edit-text")
    $tree = Get-UiXml -Area $Area -Label $Label
    $node = Find-Node -Xml $tree.Xml -ClassName "android.widget.EditText" -Index $Index
    if ($null -eq $node) {
        throw "EditText node not found at index $Index."
    }
    Tap-Node $node
}

function Enter-KeyEventText {
    param([string]$Value)
    $keyCodes = @{
        "0" = 7; "1" = 8; "2" = 9; "3" = 10; "4" = 11; "5" = 12; "6" = 13; "7" = 14; "8" = 15; "9" = 16
        "a" = 29; "b" = 30; "c" = 31; "d" = 32; "e" = 33; "f" = 34; "g" = 35; "h" = 36; "i" = 37; "j" = 38
        "k" = 39; "l" = 40; "m" = 41; "n" = 42; "o" = 43; "p" = 44; "q" = 45; "r" = 46; "s" = 47; "t" = 48
        "u" = 49; "v" = 50; "w" = 51; "x" = 52; "y" = 53; "z" = 54; "-" = 69
    }
    foreach ($character in $Value.ToCharArray()) {
        $key = $character.ToString().ToLowerInvariant()
        if (-not $keyCodes.ContainsKey($key)) {
            throw "Unsupported keyevent text character: $character"
        }
        Invoke-Adb -Arguments @("shell", "input", "keyevent", "$($keyCodes[$key])") -TimeoutSeconds 30 | Out-Null
        Start-Sleep -Milliseconds 60
    }
    Start-Sleep -Milliseconds 500
}

function Wait-ForNode {
    param(
        [string]$Area,
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
            $tree = Get-UiXml -Area $Area -Label $Label
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

function Get-PhotoCountFromTree {
    param([xml]$Xml)
    foreach ($node in $Xml.SelectNodes("//node")) {
        $text = Get-Attr $node "text"
        if ($text -match '^(\d+) of \1 photos$') {
            return [int]$Matches[1]
        }
    }
    return $null
}

function Get-VisiblePhotoCount {
    param([string]$Area, [string]$Label)
    $tree = Get-UiXml -Area $Area -Label $Label
    return Get-PhotoCountFromTree -Xml $tree.Xml
}

function Wait-ForPhotoImport {
    param(
        [string]$Area,
        [int]$PreviousCount,
        [int]$TimeoutSeconds = 35
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tree = Get-UiXml -Area $Area -Label "import-wait"
        $message = Find-Node -Xml $tree.Xml -Text "Slide image imported." -Contains
        $count = Get-PhotoCountFromTree -Xml $tree.Xml
        if (($null -ne $count -and $count -gt $PreviousCount) -or $null -ne $message) {
            return [pscustomobject]@{
                Count = $count
                Message = ($null -ne $message)
            }
        }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for slide image import confirmation."
}

function Wait-ForAnyText {
    param(
        [string]$Area,
        [string]$Label,
        [string[]]$Texts,
        [switch]$Contains,
        [int]$TimeoutSeconds = 20
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tree = Get-UiXml -Area $Area -Label $Label
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

function Tap-Text {
    param([string]$Area, [string]$Text, [switch]$Contains, [int]$ScrollAttempts = 0)
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Area $Area -Label ("find-" + ($Text -replace "[^A-Za-z0-9]", "-"))
        $node = Find-Node -Xml $tree.Xml -Text $Text -Contains:$Contains
        if ($null -ne $node) {
            Tap-Node $node
            return
        }
        if ($i -lt $ScrollAttempts) {
            Swipe-Up
            Start-Sleep -Milliseconds 700
        }
    }
    throw "Text node not found: $Text"
}

function Tap-TextClickableAncestor {
    param([string]$Area, [string]$Text, [switch]$Contains, [int]$ScrollAttempts = 0)
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Area $Area -Label ("find-clickable-" + ($Text -replace "[^A-Za-z0-9]", "-"))
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
    param([string]$Area, [string]$Description, [switch]$Contains, [int]$ScrollAttempts = 0)
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Area $Area -Label ("find-desc-" + ($Description -replace "[^A-Za-z0-9]", "-"))
        $node = Find-Node -Xml $tree.Xml -Description $Description -Contains:$Contains
        if ($null -ne $node) {
            Tap-Node $node
            return
        }
        if ($i -lt $ScrollAttempts) {
            Swipe-Up
            Start-Sleep -Milliseconds 700
        }
    }
    throw "Description node not found: $Description"
}

function Scroll-UntilTextVisible {
    param(
        [string]$Area,
        [string]$Text,
        [string]$Label,
        [switch]$Contains,
        [int]$ScrollAttempts = 8
    )
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Area $Area -Label ("$Label-$i")
        $node = Find-Node -Xml $tree.Xml -Text $Text -Contains:$Contains
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

function Scroll-UntilDescriptionVisible {
    param(
        [string]$Area,
        [string]$Description,
        [string]$Label,
        [switch]$Contains,
        [int]$ScrollAttempts = 8
    )
    for ($i = 0; $i -le $ScrollAttempts; $i++) {
        $tree = Get-UiXml -Area $Area -Label ("$Label-$i")
        $node = Find-Node -Xml $tree.Xml -Description $Description -Contains:$Contains
        if ($null -ne $node) {
            return $node
        }
        if ($i -lt $ScrollAttempts) {
            Swipe-Up
            Start-Sleep -Milliseconds 700
        }
    }
    throw "Description node not found after scrolling: $Description"
}

function Filter-ReconstructionToSample {
    param($Sample)
    Swipe-ToTop -Attempts 5
    try {
        $searchKey = if ($Sample.id -match '(\d{3})$') { $Matches[1] } else { $Sample.id }
        Tap-EditTextByIndex -Area $Sample.id -Index 0 -Label "search-field"
        Enter-Text $searchKey
        Press-Back
        Wait-ForNode -Area $Sample.id -Label "search-result" -Text $Sample.deviceName -Contains -TimeoutSeconds 20 | Out-Null
        return
    } catch {
        Write-Status "$($Sample.id) search filter fallback: $($_.Exception.Message)"
        Press-Back
        Swipe-ToTop -Attempts 5
        Scroll-UntilTextVisible -Area $Sample.id -Text $Sample.deviceName -Label "locate-sample" -Contains -ScrollAttempts 30 | Out-Null
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

function Get-VisibleOcrTextStats {
    param([string]$Area, [string]$Label)
    for ($i = 0; $i -le 6; $i++) {
        $tree = Get-UiXml -Area $Area -Label "$Label-$i"
        try {
            $node = Find-FirstEditTextBelowText -Xml $tree.Xml -AnchorText "OCR text"
            $text = Get-Attr $node "text"
            $lineCount = if ([string]::IsNullOrWhiteSpace($text)) { 0 } else { @($text -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 }).Count }
            $clue = "none"
            if ($text -match '(?i)\b10\.\d{4,9}/[-._;()/:A-Z0-9]+') {
                $clue = "doi"
            } elseif ($text -match '(?i)\b(references|bibliography|doi|abstract|introduction|conclusion)\b') {
                $clue = "reference-or-title-keyword"
            } elseif ($text.Length -ge 80) {
                $clue = "long-text"
            }
            return [ordered]@{
                nonEmpty = -not [string]::IsNullOrWhiteSpace($text)
                charCount = $text.Length
                lineCount = $lineCount
                clueCategory = $clue
            }
        } catch {
            if ($i -lt 6) {
                Swipe-Up
                Start-Sleep -Milliseconds 700
            }
        }
    }
    return [ordered]@{
        nonEmpty = $false
        charCount = 0
        lineCount = 0
        clueCategory = "none"
    }
}

function Drag-Description {
    param([string]$Area, [string]$Description, [switch]$Contains)
    $tree = Get-UiXml -Area $Area -Label ("drag-desc-" + ($Description -replace "[^A-Za-z0-9]", "-"))
    $node = Find-Node -Xml $tree.Xml -Description $Description -Contains:$Contains
    if ($null -eq $node) {
        throw "Description node not found for drag: $Description"
    }
    $bounds = Get-NodeBounds $node
    $startX = [int]($bounds.Left + (($bounds.Right - $bounds.Left) * 0.20))
    $startY = [int]($bounds.Top + (($bounds.Bottom - $bounds.Top) * 0.25))
    $endX = [int]($bounds.Left + (($bounds.Right - $bounds.Left) * 0.78))
    $endY = [int]($bounds.Top + (($bounds.Bottom - $bounds.Top) * 0.58))
    Invoke-Adb -Arguments @("shell", "input", "swipe", "$startX", "$startY", "$endX", "$endY", "500") -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Milliseconds 700
}

function Wait-ForPackageVisible {
    param(
        [string]$Area,
        [string]$Label,
        [string]$ExpectedPackage,
        [int]$TimeoutSeconds = 20
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tree = Get-UiXml -Area $Area -Label $Label
        foreach ($node in $tree.Xml.SelectNodes("//node")) {
            if ((Get-Attr $node "package") -eq $ExpectedPackage) {
                return
            }
        }
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for package '$ExpectedPackage'."
}

function Wait-ForJobTerminal {
    param(
        [string]$Area,
        [string]$JobPrefix,
        [int]$TimeoutSeconds = 120,
        [switch]$AllowFailure
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $tree = Get-UiXml -Area $Area -Label ("job-" + ($JobPrefix -replace "[^A-Za-z0-9]", "-"))
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

function Clear-App-State {
    Step "Clear internal package state"
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "pm", "clear", $PackageName) -TimeoutSeconds 60 | Out-Null
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
}

function Launch-App {
    Step "Launch app"
    Invoke-Adb -Arguments @("shell", "input", "keyevent", "224") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "wm", "dismiss-keyguard") -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @(
        "shell", "am", "start",
        "-S", "-W",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.LAUNCHER",
        "-f", "0x10008000",
        "-n", "$PackageName/com.yuukias.seminararc.MainActivity"
    ) -TimeoutSeconds 45 | Out-Null
    Wait-ForNode -Area "launch" -Label "launch-ready" -Text "SeminarArc" -TimeoutSeconds 25 | Out-Null
    Wait-ForAnyText -Area "launch" -Label "launch-content-ready" -Texts @("Your seminars", "Seminar detail", "Active session") -Contains -TimeoutSeconds 25 | Out-Null
}

function Push-Fixtures {
    Step "Push anonymized private fixtures to Emulator Downloads"
    Invoke-Adb -Arguments @("shell", "rm", "-rf", $deviceFixtureDir) -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
    Invoke-Adb -Arguments @("shell", "mkdir", "-p", $deviceFixtureDir) -TimeoutSeconds 30 | Out-Null
    foreach ($sample in @($script:PhotoSamples + $script:PdfSamples)) {
        Invoke-Adb -Arguments @("push", $sample.localPath, $sample.devicePath) -TimeoutSeconds 90 | Out-Null
    }
    Invoke-Adb -Arguments @(
        "shell", "am", "broadcast",
        "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d", "file://$deviceFixtureDir"
    ) -TimeoutSeconds 30 -IgnoreExitCode | Out-Null
}

function Select-DeviceFileInSystemPicker {
    param(
        [string]$Area,
        [string]$DeviceName,
        [string]$FallbackText = ""
    )
    $deadline = (Get-Date).AddSeconds(80)
    $attempt = 0
    do {
        $attempt += 1
        $tree = Get-UiXml -Area $Area -Label "picker-$attempt"
        $pickerVisible = $false
        foreach ($node in $tree.Xml.SelectNodes("//node")) {
            $package = Get-Attr $node "package"
            if (-not [string]::IsNullOrWhiteSpace($package) -and $package -ne $PackageName) {
                $pickerVisible = $true
                break
            }
        }
        if (-not $pickerVisible) {
            Start-Sleep -Milliseconds 700
            continue
        }
        foreach ($text in @($DeviceName, $FallbackText, $runId, "SeminarArcRealWorld", "Downloads", "Images", "Recent")) {
            if ([string]::IsNullOrWhiteSpace($text)) {
                continue
            }
            $node = Find-Node -Xml $tree.Xml -Text $text -Contains
            if ($null -eq $node) {
                $node = Find-Node -Xml $tree.Xml -Description $text -Contains
            }
            if ($null -ne $node) {
                Tap-Node $node
                try {
                    Wait-ForPackageVisible -Area $Area -Label "picker-return-$attempt" -ExpectedPackage $PackageName -TimeoutSeconds 8 | Out-Null
                    return
                } catch {
                    Start-Sleep -Seconds 1
                    break
                }
            }
        }
        Swipe-Up
        Start-Sleep -Milliseconds 700
    } while ((Get-Date) -lt $deadline)
    Capture-Evidence -Area $Area -Label "picker-file-not-found-$DeviceName" -WithLogcat
    throw "Timed out selecting $DeviceName in Android system picker."
}

function Create-CorpusSeminar {
    Step "Create real-world corpus seminar"
    Launch-App
    Tap-Text -Area "setup" -Text "Create seminar"
    Wait-ForNode -Area "setup" -Label "new-editor" -Text "New seminar" -TimeoutSeconds 20 | Out-Null
    $title = "realworldcorpus"
    Tap-EditTextByIndex -Area "setup" -Index 0 -Label "title-field"
    Enter-KeyEventText $title
    Press-Back

    if ($script:PdfSamples.Count -gt 0) {
        $script:PdfLifecycle.attempted = $true
        Step "Import first PDF through seminar editor picker"
        Tap-TextClickableAncestor -Area "pdf" -Text "Import PDF" -ScrollAttempts 5
        try {
            Select-DeviceFileInSystemPicker -Area "pdf" -DeviceName $script:PdfSamples[0].deviceName
            $script:PdfLifecycle.import = "PASS"
        } catch {
            $script:PdfLifecycle.import = "FAIL"
            $script:Limitations.Add("PDF import failed through system picker: $($_.Exception.Message)") | Out-Null
            Press-Back
        }
    }

    Tap-TextClickableAncestor -Area "setup" -Text "Save draft" -ScrollAttempts 5
    Wait-ForNode -Area "setup" -Label "detail-created" -Text "Seminar detail" -TimeoutSeconds 25 | Out-Null
    if ($script:PdfLifecycle.import -eq "PASS") {
        try {
            Wait-ForNode -Area "pdf" -Label "pdf-attached-detail" -Text $script:PdfSamples[0].deviceName -Contains -TimeoutSeconds 20 | Out-Null
            $script:PdfLifecycle.persistence = "PASS"
        } catch {
            $script:PdfLifecycle.persistence = "FAIL"
        }
    }
}

function Open-Reconstruction {
    Tap-TextClickableAncestor -Area "photos" -Text "Open reconstruction" -ScrollAttempts 6
    Wait-ForNode -Area "photos" -Label "reconstruction-open" -Text "Reconstruction" -TimeoutSeconds 25 | Out-Null
}

function Open-CorpusDetail {
    param([string]$Area = "detail")
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Seconds 1
    Launch-App
    Tap-Text -Area $Area -Text "realworldcorpus" -Contains -ScrollAttempts 4
    Wait-ForNode -Area $Area -Label "detail-open" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
}

function Open-CorpusReconstruction {
    param([string]$Area = "photos")
    Open-CorpusDetail -Area $Area
    Open-Reconstruction
}

function Import-PhotoSample {
    param($Sample)
    Step "Import $($Sample.id) through system picker"
    try {
        Open-CorpusReconstruction -Area $Sample.id
        Swipe-ToTop -Attempts 5
        $previousPhotoCount = Get-VisiblePhotoCount -Area $Sample.id -Label "pre-import-count"
        if ($null -eq $previousPhotoCount) { $previousPhotoCount = 0 }
        Tap-TextClickableAncestor -Area $Sample.id -Text "Import slide image" -ScrollAttempts 4
        Select-DeviceFileInSystemPicker -Area $Sample.id -DeviceName $Sample.deviceName
        Wait-ForPhotoImport -Area $Sample.id -PreviousCount $previousPhotoCount -TimeoutSeconds 35 | Out-Null
        $Sample.import = "PASS"
        Capture-Evidence -Area $Sample.id -Label "imported"
    } catch {
        $Sample.import = "FAIL"
        $Sample.failureCategory = "import"
        Capture-Evidence -Area $Sample.id -Label "import-failed" -WithLogcat
        Write-Status "$($Sample.id) import failed: $($_.Exception.Message)"
        try { Press-Back } catch {}
    }
}

function Process-PhotoSample {
    param($Sample, [switch]$DoFormula)
    if ($Sample.import -ne "PASS") {
        return
    }
    Step "Process $($Sample.id): enhancement/OCR/key-slide"
    try {
        Filter-ReconstructionToSample -Sample $Sample
        try {
            Tap-Description -Area $Sample.id -Description "Mark key slide" -Contains -ScrollAttempts 3
            $Sample.keySlide = "PASS"
        } catch {
            $Sample.keySlide = "FAIL"
            if ($null -eq $Sample.failureCategory) { $Sample.failureCategory = "key-slide" }
        }

        $start = Get-Date
        Tap-Text -Area $Sample.id -Text "Enhance" -ScrollAttempts 2
        $Sample.enhancement = Wait-ForJobTerminal -Area $Sample.id -JobPrefix "Enhancement" -TimeoutSeconds 120 -AllowFailure
        $Sample.enhancementLatencyMs = [int]((Get-Date) - $start).TotalMilliseconds

        $start = Get-Date
        Tap-Text -Area $Sample.id -Text "OCR" -ScrollAttempts 6
        $Sample.ocr = Wait-ForJobTerminal -Area $Sample.id -JobPrefix "OCR" -TimeoutSeconds 160 -AllowFailure
        $Sample.ocrLatencyMs = [int]((Get-Date) - $start).TotalMilliseconds

        $stats = Get-VisibleOcrTextStats -Area $Sample.id -Label "ocr-stats"
        $Sample.ocrNonEmpty = $stats.nonEmpty
        $Sample.ocrCharCount = $stats.charCount
        $Sample.ocrLineCount = $stats.lineCount
        $Sample.clueCategory = $stats.clueCategory

        if ($DoFormula) {
            Invoke-FormulaRepresentative -Sample $Sample
        }
        Capture-Evidence -Area $Sample.id -Label "processed" -WithLogcat
    } catch {
        if ($Sample.failureCategory -eq $null) {
            $Sample.failureCategory = "processing"
        }
        Capture-Evidence -Area $Sample.id -Label "processing-failed" -WithLogcat
        Write-Status "$($Sample.id) processing failed: $($_.Exception.Message)"
    }
}

function Invoke-FormulaRepresentative {
    param($Sample)
    Step "Run formula representative flow on $($Sample.id)"
    $script:FormulaSampleId = $Sample.id
    try {
        Scroll-UntilDescriptionVisible -Area $Sample.id -Description "Drag to draft formula region" -Label "formula-locate-region" -Contains -ScrollAttempts 8 | Out-Null
        Drag-Description -Area $Sample.id -Description "Drag to draft formula region" -Contains
        Tap-Text -Area $Sample.id -Text "Save formula region" -ScrollAttempts 5
        Wait-ForNode -Area $Sample.id -Label "formula-saved" -Text "Formula:" -Contains -TimeoutSeconds 20 | Out-Null
        Tap-Text -Area $Sample.id -Text "Edit crop" -ScrollAttempts 4
        Tap-Text -Area $Sample.id -Text "Formula label" -ScrollAttempts 2
        Enter-Text "RWFormula"
        Press-Back
        Tap-Text -Area $Sample.id -Text "Update formula region" -ScrollAttempts 3
        Wait-ForNode -Area $Sample.id -Label "formula-updated" -Text "RWFormula" -Contains -TimeoutSeconds 20 | Out-Null
        Tap-Text -Area $Sample.id -Text "LaTeX" -ScrollAttempts 5
        Enter-Text "x"
        Tap-Text -Area $Sample.id -Text "Queue LaTeX" -ScrollAttempts 2
        Wait-ForNode -Area $Sample.id -Label "formula-ready" -Text "Formula ready:" -Contains -TimeoutSeconds 45 | Out-Null
        $Sample.formula = "PASS"
    } catch {
        $Sample.formula = "FAIL"
        if ($null -eq $Sample.failureCategory) { $Sample.failureCategory = "formula" }
        throw
    }
}

function Verify-PostRelaunchPersistence {
    Step "Force-stop and verify imported media persistence"
    Invoke-Adb -Arguments @("shell", "am", "force-stop", $PackageName) -TimeoutSeconds 30 | Out-Null
    Start-Sleep -Seconds 1
    Launch-App
    Tap-Text -Area "persistence" -Text "realworldcorpus" -Contains -ScrollAttempts 4
    Wait-ForNode -Area "persistence" -Label "detail-reopened" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
    if ($script:PdfLifecycle.import -eq "PASS") {
        try {
            Wait-ForNode -Area "pdf" -Label "pdf-after-relaunch" -Text $script:PdfSamples[0].deviceName -Contains -TimeoutSeconds 20 | Out-Null
            $script:PdfLifecycle.persistence = "PASS"
        } catch {
            $script:PdfLifecycle.persistence = "FAIL"
        }
    }
    Open-Reconstruction
    foreach ($sample in $script:PhotoSamples | Where-Object { $_.import -eq "PASS" } | Select-Object -First 3) {
        try {
            Scroll-UntilTextVisible -Area "persistence" -Text $sample.deviceName -Label "photo-$($sample.id)-persisted" -Contains -ScrollAttempts 12 | Out-Null
        } catch {
            if ($null -eq $sample.failureCategory) { $sample.failureCategory = "persistence" }
        }
    }
    Capture-Evidence -Area "persistence" -Label "after-relaunch" -WithLogcat
}

function Exercise-PdfReplaceRemove {
    if ($script:PdfSamples.Count -eq 0 -or $script:PdfLifecycle.import -ne "PASS") {
        return
    }
    Step "Exercise PDF replace/remove lifecycle"
    try {
        Press-Back
        Wait-ForNode -Area "pdf" -Label "detail-before-edit" -Text "Seminar detail" -TimeoutSeconds 20 | Out-Null
        Tap-Description -Area "pdf" -Description "Edit" -Contains
        Wait-ForNode -Area "pdf" -Label "edit-screen" -Text "Edit seminar" -TimeoutSeconds 20 | Out-Null
        Tap-Text -Area "pdf" -Text "Import PDF" -ScrollAttempts 5
        Select-DeviceFileInSystemPicker -Area "pdf" -DeviceName $script:PdfSamples[0].deviceName -FallbackText "PDF"
        Tap-Text -Area "pdf" -Text "Save draft" -ScrollAttempts 5
        Wait-ForNode -Area "pdf" -Label "detail-after-replace" -Text "Seminar detail" -TimeoutSeconds 25 | Out-Null
        Wait-ForNode -Area "pdf" -Label "pdf-replaced" -Text $script:PdfSamples[0].deviceName -Contains -TimeoutSeconds 20 | Out-Null
        $script:PdfLifecycle.replace = "PASS"

        Tap-Description -Area "pdf" -Description "Edit" -Contains
        Wait-ForNode -Area "pdf" -Label "edit-screen-remove" -Text "Edit seminar" -TimeoutSeconds 20 | Out-Null
        Tap-Text -Area "pdf" -Text "Remove" -ScrollAttempts 5
        Tap-Text -Area "pdf" -Text "Save draft" -ScrollAttempts 5
        Wait-ForNode -Area "pdf" -Label "detail-after-remove" -Text "Seminar detail" -TimeoutSeconds 25 | Out-Null
        Wait-ForNode -Area "pdf" -Label "pdf-removed" -Text "No abstract PDF attached" -Contains -TimeoutSeconds 20 | Out-Null
        $script:PdfLifecycle.remove = "PASS"
    } catch {
        if ($script:PdfLifecycle.replace -ne "PASS") {
            $script:PdfLifecycle.replace = "FAIL"
        } else {
            $script:PdfLifecycle.remove = "FAIL"
        }
        $script:Limitations.Add("PDF replace/remove lifecycle failed: $($_.Exception.Message)") | Out-Null
        Capture-Evidence -Area "pdf" -Label "pdf-lifecycle-failed" -WithLogcat
    }
}

function Write-PrivateSummary {
    $photoStats = [ordered]@{
        total = $script:PhotoSamples.Count
        importPass = @($script:PhotoSamples | Where-Object { $_.import -eq "PASS" }).Count
        enhancementPass = @($script:PhotoSamples | Where-Object { $_.enhancement -eq "succeeded" }).Count
        ocrSucceeded = @($script:PhotoSamples | Where-Object { $_.ocr -eq "succeeded" }).Count
        ocrNonEmpty = @($script:PhotoSamples | Where-Object { $_.ocrNonEmpty }).Count
        clueUsable = @($script:PhotoSamples | Where-Object { $_.clueCategory -ne "none" }).Count
        keySlidePass = @($script:PhotoSamples | Where-Object { $_.keySlide -eq "PASS" }).Count
    }
    $latencies = [ordered]@{
        enhancementMs = @($script:PhotoSamples | Where-Object { $_.enhancementLatencyMs -ne $null } | ForEach-Object { $_.enhancementLatencyMs })
        ocrMs = @($script:PhotoSamples | Where-Object { $_.ocrLatencyMs -ne $null } | ForEach-Object { $_.ocrLatencyMs })
    }
    $summary = [ordered]@{
        status = $script:RunStatus
        runId = $runId
        evidenceRoot = $evidenceRoot
        package = $PackageName
        emulatorSerial = $script:SelectedSerial
        stepCount = $script:StepCount
        fixtureRoot = $FixtureRoot
        manifest = $manifestPath
        photoStats = $photoStats
        latencies = $latencies
        formulaSampleId = $script:FormulaSampleId
        pdfCount = $script:PdfSamples.Count
        pdfLifecycle = $script:PdfLifecycle
        limitations = @($script:Limitations)
        photos = $script:PhotoSamples
        pdfs = $script:PdfSamples
    }
    ($summary | ConvertTo-Json -Depth 14) | Set-Content -Encoding UTF8 -Path $summaryPath

    $lines = @(
        "# SeminarArc Real-world Media Corpus $runId",
        "",
        "- status: $script:RunStatus",
        "- package: $PackageName",
        "- emulator: $script:SelectedSerial",
        "- photos: $($script:PhotoSamples.Count)",
        "- pdfs: $($script:PdfSamples.Count)",
        "- steps: $script:StepCount",
        "- manifest: $manifestPath",
        "",
        "## Aggregate",
        "- import: $($photoStats.importPass)/$($photoStats.total)",
        "- enhancement: $($photoStats.enhancementPass)/$($photoStats.total)",
        "- OCR succeeded: $($photoStats.ocrSucceeded)/$($photoStats.total)",
        "- OCR non-empty: $($photoStats.ocrNonEmpty)/$($photoStats.total)",
        "- clue usable: $($photoStats.clueUsable)/$($photoStats.total)",
        "- key slide: $($photoStats.keySlidePass)/$($photoStats.total)",
        "- formula sample: $script:FormulaSampleId",
        "- PDF lifecycle: import=$($script:PdfLifecycle.import), persistence=$($script:PdfLifecycle.persistence), replace=$($script:PdfLifecycle.replace), remove=$($script:PdfLifecycle.remove)",
        "",
        "## Privacy",
        "This summary is private ignored evidence. It may include local paths and hashes. Do not commit."
    )
    $lines | Set-Content -Encoding UTF8 -Path $summaryMdPath
}

try {
    New-SampleInventory
    Select-EmulatorSerial
    Build-And-Install
    Push-Fixtures
    Clear-App-State
    Create-CorpusSeminar
    Open-Reconstruction

    $formulaDone = $false
    foreach ($sample in $script:PhotoSamples) {
        Import-PhotoSample -Sample $sample
        $doFormula = -not $formulaDone
        Process-PhotoSample -Sample $sample -DoFormula:$doFormula
        if ($sample.formula -eq "PASS") {
            $formulaDone = $true
        }
    }

    Verify-PostRelaunchPersistence
    Exercise-PdfReplaceRemove
    $script:RunStatus = "PASS"
} catch {
    if ($script:RunStatus -eq "RUNNING") {
        $script:RunStatus = "FAIL"
    }
    $script:Limitations.Add($_.Exception.Message) | Out-Null
    if (-not [string]::IsNullOrWhiteSpace($script:SelectedSerial)) {
        try { Capture-Evidence -Area "failure" -Label "final" -WithLogcat } catch {}
    }
    Write-PrivateSummary
    throw
} finally {
    Write-PrivateSummary
    Write-Status "Private summary: $summaryMdPath"
}
