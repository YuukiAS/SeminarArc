$env:JAVA_HOME = "C:\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Android"
$env:ANDROID_SDK_ROOT = "C:\Android"
$env:GRADLE_USER_HOME = "C:\Android\.gradle"

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
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
