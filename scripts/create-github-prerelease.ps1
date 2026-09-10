param(
    [string]$Repository = "YuukiAS/SeminarArc",
    [string]$Tag = "v0.5.0-alpha.2",
    [string]$TargetCommitish = "122dfb8ce5203e3960aacd6c1c3a73cfdd851a15",
    [string]$Title = "SeminarArc v0.5.0-alpha.2",
    [string]$ReleaseNotesPath = "docs/releases/v0.5.0-alpha.2.md",
    [string[]]$AssetPaths = @(
        "release/SeminarArc-0.5.0-alpha.2.apk",
        "release/SeminarArc-0.5.0-alpha.2.apk.sha256"
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

function Resolve-RepoPath {
    param([string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }

    return Join-Path $repoRoot $Path
}

$token = $env:GH_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    $token = $env:GITHUB_TOKEN
}

if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Set GH_TOKEN or GITHUB_TOKEN with GitHub release write permission before running this script."
}

$releaseNotesFullPath = Resolve-RepoPath $ReleaseNotesPath
if (-not (Test-Path -LiteralPath $releaseNotesFullPath -PathType Leaf)) {
    throw "Release notes file not found: $releaseNotesFullPath"
}

$assetFullPaths = foreach ($assetPath in $AssetPaths) {
    $fullPath = Resolve-RepoPath $assetPath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "Release asset not found: $fullPath"
    }
    $fullPath
}

$headers = @{
    Accept = "application/vnd.github+json"
    Authorization = "Bearer $token"
    "X-GitHub-Api-Version" = "2022-11-28"
}

$releaseUri = "https://api.github.com/repos/$Repository/releases"
$existingReleaseUri = "$releaseUri/tags/$Tag"

try {
    $release = Invoke-RestMethod -Method Get -Uri $existingReleaseUri -Headers $headers
    Write-Host "Using existing release: $($release.html_url)"
} catch {
    $statusCode = $null
    if ($_.Exception.Response -ne $null) {
        $statusCode = [int]$_.Exception.Response.StatusCode
    }

    if ($statusCode -ne 404) {
        throw
    }

    $body = @{
        tag_name = $Tag
        target_commitish = $TargetCommitish
        name = $Title
        body = Get-Content -LiteralPath $releaseNotesFullPath -Raw
        draft = $false
        prerelease = $true
        make_latest = "false"
    } | ConvertTo-Json -Depth 5

    $release = Invoke-RestMethod -Method Post -Uri $releaseUri -Headers $headers -ContentType "application/json" -Body $body
    Write-Host "Created prerelease: $($release.html_url)"
}

$uploadedNames = @{}
foreach ($asset in @($release.assets)) {
    $uploadedNames[$asset.name] = $true
}

foreach ($assetPath in $assetFullPaths) {
    $assetName = Split-Path -Leaf $assetPath
    if ($uploadedNames.ContainsKey($assetName)) {
        Write-Host "Skipping already uploaded asset: $assetName"
        continue
    }

    $uploadUri = $release.upload_url -replace "\{\?name,label\}$", "?name=$([uri]::EscapeDataString($assetName))"
    $contentType = "application/octet-stream"
    if ($assetName.EndsWith(".sha256", [System.StringComparison]::OrdinalIgnoreCase)) {
        $contentType = "text/plain"
    }

    Invoke-RestMethod -Method Post -Uri $uploadUri -Headers $headers -ContentType $contentType -InFile $assetPath | Out-Null
    Write-Host "Uploaded asset: $assetName"
}

Write-Host "Release URL: $($release.html_url)"
