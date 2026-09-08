$repoRoot = Split-Path -Parent $PSScriptRoot

$env:JAVA_HOME = "D:\Code\_jdks\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "D:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
Remove-Item Env:ANDROID_USER_HOME -ErrorAction SilentlyContinue
$env:ANDROID_PREFS_ROOT = Join-Path $repoRoot ".android-user-home"
$env:GRADLE_USER_HOME = Join-Path $repoRoot ".gradle-user-home"
$env:GRADLE_OPTS = "-Duser.home=$env:GRADLE_USER_HOME"

$androidTools = @(
    "$env:JAVA_HOME\bin",
    "$env:ANDROID_HOME\platform-tools",
    "$env:ANDROID_HOME\cmdline-tools\latest\bin"
)

$currentPath = [System.Collections.Generic.List[string]]::new()
$env:PATH -split ";" | Where-Object { $_ -ne "" } | ForEach-Object {
    [void]$currentPath.Add($_)
}

$orderedAndroidTools = $androidTools.Clone()
[array]::Reverse($orderedAndroidTools)

foreach ($toolPath in $orderedAndroidTools) {
    if ($currentPath -notcontains $toolPath) {
        $currentPath.Insert(0, $toolPath)
    }
}

$env:PATH = $currentPath -join ";"

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "ANDROID_SDK_ROOT=$env:ANDROID_SDK_ROOT"
Write-Host "ANDROID_PREFS_ROOT=$env:ANDROID_PREFS_ROOT"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
Write-Host "GRADLE_OPTS=$env:GRADLE_OPTS"
