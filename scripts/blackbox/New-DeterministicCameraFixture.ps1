param(
    [string]$OutputPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptRoot)
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $repoRoot "private\blackbox-fixtures\seminararc-camera-fixture.png"
} elseif (-not [System.IO.Path]::IsPathRooted($OutputPath)) {
    $OutputPath = Join-Path $repoRoot $OutputPath
}

$outputDir = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

Add-Type -AssemblyName System.Drawing

$width = 1920
$height = 1080
$bitmap = [System.Drawing.Bitmap]::new($width, $height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

$brushes = @()
$pens = @()
$fonts = @()
$chineseText = [System.Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String("5Lit5paH57q/57Si77ya6L+Z5piv5LiA5byg5ZCI5oiQ56CU6K6o5Lya5bm754Gv54mH77yM55So5LqO55u45py644CB6YeN5bu65ZKM5YWs5byP5Yy65Z+f5rWL6K+V44CC")
)

try {
    $white = [System.Drawing.Color]::FromArgb(255, 252, 252, 248)
    $ink = [System.Drawing.Color]::FromArgb(255, 24, 34, 42)
    $muted = [System.Drawing.Color]::FromArgb(255, 78, 88, 96)
    $blue = [System.Drawing.Color]::FromArgb(255, 42, 94, 142)
    $green = [System.Drawing.Color]::FromArgb(255, 66, 132, 96)
    $gold = [System.Drawing.Color]::FromArgb(255, 204, 151, 56)
    $red = [System.Drawing.Color]::FromArgb(255, 184, 76, 76)

    $graphics.Clear($white)
    $brushes += $titleBrush = [System.Drawing.SolidBrush]::new($ink)
    $brushes += $mutedBrush = [System.Drawing.SolidBrush]::new($muted)
    $brushes += $blueBrush = [System.Drawing.SolidBrush]::new($blue)
    $brushes += $greenBrush = [System.Drawing.SolidBrush]::new($green)
    $brushes += $goldBrush = [System.Drawing.SolidBrush]::new($gold)
    $brushes += $redBrush = [System.Drawing.SolidBrush]::new($red)
    $brushes += $lightBlueBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 226, 238, 248))
    $brushes += $lightGreenBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 229, 242, 235))
    $pens += $inkPen = [System.Drawing.Pen]::new($ink, 4)
    $pens += $bluePen = [System.Drawing.Pen]::new($blue, 5)
    $pens += $gridPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 194, 202, 210), 2)
    $pens += $greenPen = [System.Drawing.Pen]::new($green, 5)

    $fonts += $titleFont = [System.Drawing.Font]::new("Segoe UI", 72, [System.Drawing.FontStyle]::Bold)
    $fonts += $subtitleFont = [System.Drawing.Font]::new("Segoe UI", 36, [System.Drawing.FontStyle]::Regular)
    $fonts += $bodyFont = [System.Drawing.Font]::new("Segoe UI", 34, [System.Drawing.FontStyle]::Regular)
    $fonts += $monoFont = [System.Drawing.Font]::new("Consolas", 40, [System.Drawing.FontStyle]::Bold)
    $fonts += $chineseFont = [System.Drawing.Font]::new("Microsoft YaHei", 36, [System.Drawing.FontStyle]::Regular)
    $fonts += $labelFont = [System.Drawing.Font]::new("Segoe UI", 26, [System.Drawing.FontStyle]::Bold)

    $graphics.FillRectangle($lightBlueBrush, 0, 0, $width, 150)
    $graphics.DrawString("SeminarArc Camera Fixture", $titleFont, $titleBrush, 80, 32)
    $graphics.DrawString("Deterministic synthetic slide for black-box CameraX QA", $subtitleFont, $mutedBrush, 86, 165)

    $graphics.DrawString("English clue: local reconstruction, OCR, reference review, and formula flow.", $bodyFont, $titleBrush, 90, 260)
    $graphics.DrawString($chineseText, $chineseFont, $titleBrush, 90, 330)
    $graphics.DrawString("DOI clue: 10.1038/nature12373", $monoFont, $blueBrush, 90, 420)
    $graphics.DrawString("Formula: E = mc^2", $monoFont, $greenBrush, 90, 505)

    $graphics.FillRectangle($lightGreenBrush, 92, 625, 760, 310)
    $graphics.DrawRectangle($inkPen, 92, 625, 760, 310)
    $graphics.DrawString("Simple chart region", $labelFont, $titleBrush, 122, 650)
    for ($i = 0; $i -lt 6; $i++) {
        $x = 155 + ($i * 105)
        $graphics.DrawLine($gridPen, $x, 710, $x, 895)
    }
    for ($i = 0; $i -lt 4; $i++) {
        $y = 730 + ($i * 45)
        $graphics.DrawLine($gridPen, 140, $y, 790, $y)
    }
    $graphics.FillRectangle($blueBrush, 175, 825, 80, 70)
    $graphics.FillRectangle($greenBrush, 330, 775, 80, 120)
    $graphics.FillRectangle($goldBrush, 485, 735, 80, 160)
    $graphics.FillRectangle($redBrush, 640, 795, 80, 100)
    $graphics.DrawString("A", $labelFont, $titleBrush, 197, 902)
    $graphics.DrawString("B", $labelFont, $titleBrush, 352, 902)
    $graphics.DrawString("C", $labelFont, $titleBrush, 507, 902)
    $graphics.DrawString("D", $labelFont, $titleBrush, 662, 902)

    $formulaBox = [System.Drawing.Rectangle]::new(1020, 625, 720, 310)
    $graphics.FillRectangle([System.Drawing.Brushes]::White, $formulaBox)
    $graphics.DrawRectangle($inkPen, $formulaBox)
    $graphics.DrawString("Formula crop target", $labelFont, $titleBrush, 1050, 650)
    $graphics.DrawString("y = X beta + epsilon", $monoFont, $greenBrush, 1070, 755)
    $graphics.DrawEllipse($bluePen, 1160, 835, 160, 70)
    $graphics.DrawLine($greenPen, 1320, 870, 1540, 800)
    $graphics.DrawString("crop here", $labelFont, $mutedBrush, 1380, 865)

    $graphics.DrawString("Fixture version: 2026-09-13 / repo-safe synthetic content", $labelFont, $mutedBrush, 90, 1000)
    $bitmap.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    foreach ($font in $fonts) { if ($null -ne $font) { $font.Dispose() } }
    foreach ($pen in $pens) { if ($null -ne $pen) { $pen.Dispose() } }
    foreach ($brush in $brushes) { if ($null -ne $brush) { $brush.Dispose() } }
    $graphics.Dispose()
    $bitmap.Dispose()
}

Write-Output $OutputPath
