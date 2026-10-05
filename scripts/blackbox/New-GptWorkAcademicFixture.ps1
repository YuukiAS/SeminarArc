param(
    [string]$OutputRoot = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptRoot)
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot "private\gpt-work-acceptance\fixtures\geometric-neural-fields"
} elseif (-not [System.IO.Path]::IsPathRooted($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot $OutputRoot
}
New-Item -ItemType Directory -Force -Path $OutputRoot | Out-Null

Add-Type -AssemblyName System.Drawing

function New-AcademicSlide {
    param(
        [string]$Path,
        [int]$Width,
        [int]$Height,
        [string]$Kicker,
        [string]$Title,
        [string[]]$Body,
        [string]$Formula = "",
        [ValidateSet("Title", "Method", "Results", "References")]
        [string]$Kind = "Title"
    )

    $bitmap = [System.Drawing.Bitmap]::new($Width, $Height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $brushes = New-Object System.Collections.Generic.List[System.IDisposable]
    $pens = New-Object System.Collections.Generic.List[System.IDisposable]
    $fonts = New-Object System.Collections.Generic.List[System.IDisposable]
    try {
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
        $paper = [System.Drawing.Color]::FromArgb(255, 248, 247, 243)
        $ink = [System.Drawing.Color]::FromArgb(255, 29, 39, 49)
        $muted = [System.Drawing.Color]::FromArgb(255, 84, 94, 104)
        $blue = [System.Drawing.Color]::FromArgb(255, 48, 84, 132)
        $teal = [System.Drawing.Color]::FromArgb(255, 47, 118, 111)
        $ochre = [System.Drawing.Color]::FromArgb(255, 194, 137, 48)
        $panel = [System.Drawing.Color]::FromArgb(255, 230, 235, 239)
        $graphics.Clear($paper)

        $brushes.Add(($inkBrush = [System.Drawing.SolidBrush]::new($ink)))
        $brushes.Add(($mutedBrush = [System.Drawing.SolidBrush]::new($muted)))
        $brushes.Add(($blueBrush = [System.Drawing.SolidBrush]::new($blue)))
        $brushes.Add(($tealBrush = [System.Drawing.SolidBrush]::new($teal)))
        $brushes.Add(($ochreBrush = [System.Drawing.SolidBrush]::new($ochre)))
        $brushes.Add(($panelBrush = [System.Drawing.SolidBrush]::new($panel)))
        $pens.Add(($inkPen = [System.Drawing.Pen]::new($ink, [Math]::Max(2, $Width / 700))))
        $pens.Add(($bluePen = [System.Drawing.Pen]::new($blue, [Math]::Max(3, $Width / 500))))
        $pens.Add(($tealPen = [System.Drawing.Pen]::new($teal, [Math]::Max(3, $Width / 500))))

        $scale = [Math]::Min($Width / 1920.0, $Height / 1080.0)
        $fonts.Add(($kickerFont = [System.Drawing.Font]::new("Segoe UI", [Math]::Max(16, 26 * $scale), [System.Drawing.FontStyle]::Bold)))
        $fonts.Add(($titleFont = [System.Drawing.Font]::new("Segoe UI", [Math]::Max(30, 58 * $scale), [System.Drawing.FontStyle]::Bold)))
        $fonts.Add(($bodyFont = [System.Drawing.Font]::new("Segoe UI", [Math]::Max(18, 31 * $scale), [System.Drawing.FontStyle]::Regular)))
        $fonts.Add(($smallFont = [System.Drawing.Font]::new("Segoe UI", [Math]::Max(14, 22 * $scale), [System.Drawing.FontStyle]::Regular)))
        $fonts.Add(($monoFont = [System.Drawing.Font]::new("Cambria Math", [Math]::Max(22, 42 * $scale), [System.Drawing.FontStyle]::Italic)))

        $margin = [int]($Width * 0.055)
        $graphics.FillRectangle($blueBrush, 0, 0, $Width, [int]($Height * 0.035))
        $graphics.DrawString($Kicker.ToUpperInvariant(), $kickerFont, $blueBrush, $margin, [int]($Height * 0.075))
        $titleRect = [System.Drawing.RectangleF]::new($margin, [int]($Height * 0.13), $Width - (2 * $margin), [int]($Height * 0.22))
        $graphics.DrawString($Title, $titleFont, $inkBrush, $titleRect)

        $bodyTop = [int]($Height * 0.36)
        $lineHeight = [int]($Height * 0.075)
        for ($i = 0; $i -lt $Body.Count; $i++) {
            $bulletY = $bodyTop + ($i * $lineHeight)
            $graphics.FillEllipse($(if (($i % 2) -eq 0) { $tealBrush } else { $ochreBrush }), $margin, $bulletY + 12, [Math]::Max(10, [int]($Width * 0.009)), [Math]::Max(10, [int]($Width * 0.009)))
            $bodyRect = [System.Drawing.RectangleF]::new($margin + [int]($Width * 0.025), $bulletY, $Width - (2 * $margin) - [int]($Width * 0.03), $lineHeight * 1.25)
            $graphics.DrawString($Body[$i], $bodyFont, $inkBrush, $bodyRect)
        }

        if (-not [string]::IsNullOrWhiteSpace($Formula)) {
            $formulaRect = [System.Drawing.Rectangle]::new($margin, [int]($Height * 0.72), $Width - (2 * $margin), [int]($Height * 0.16))
            $graphics.FillRectangle($panelBrush, $formulaRect)
            $graphics.DrawRectangle($bluePen, $formulaRect)
            $graphics.DrawString($Formula, $monoFont, $tealBrush, $formulaRect.X + 30, $formulaRect.Y + 28)
        }

        if ($Kind -eq "Method") {
            $y = [int]($Height * 0.90)
            $graphics.DrawEllipse($bluePen, $margin, $y - 36, 180 * $scale, 70 * $scale)
            $graphics.DrawLine($tealPen, $margin + [int](180 * $scale), $y, $margin + [int](420 * $scale), $y)
            $graphics.DrawEllipse($tealPen, $margin + [int](420 * $scale), $y - 36, 210 * $scale, 70 * $scale)
        } elseif ($Kind -eq "Results") {
            $chartLeft = [int]($Width * 0.62)
            $chartTop = [int]($Height * 0.62)
            $graphics.DrawLine($inkPen, $chartLeft, $chartTop, $chartLeft, $chartTop + [int]($Height * 0.25))
            $graphics.DrawLine($inkPen, $chartLeft, $chartTop + [int]($Height * 0.25), [int]($Width * 0.93), $chartTop + [int]($Height * 0.25))
            [System.Drawing.Point[]]$points = @(
                [System.Drawing.Point]::new($chartLeft + 20, $chartTop + 190),
                [System.Drawing.Point]::new($chartLeft + 120, $chartTop + 145),
                [System.Drawing.Point]::new($chartLeft + 220, $chartTop + 112),
                [System.Drawing.Point]::new($chartLeft + 320, $chartTop + 62)
            )
            $graphics.DrawLines($tealPen, $points)
        }

        $footer = "Northbridge Institute | Applied Geometry Seminar | October 2026"
        $graphics.DrawString($footer, $smallFont, $mutedBrush, $margin, $Height - [int]($Height * 0.065))
        $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        foreach ($font in $fonts) { $font.Dispose() }
        foreach ($pen in $pens) { $pen.Dispose() }
        foreach ($brush in $brushes) { $brush.Dispose() }
        $graphics.Dispose()
        $bitmap.Dispose()
    }
}

function Escape-PdfText {
    param([string]$Value)
    return $Value.Replace("\", "\\").Replace("(", "\(").Replace(")", "\)")
}

function New-AbstractPdf {
    param([string]$Path)
    $lines = @(
        "Geometric Priors for Sparse Neural Fields",
        "Dr. Mira Chen - Northbridge Institute",
        "",
        "Abstract",
        "Neural field models can reconstruct continuous signals from sparse observations,",
        "but their behavior depends strongly on implicit geometric assumptions. This seminar",
        "introduces a local regularization principle that separates sampling noise from manifold",
        "curvature, derives a stability bound, and evaluates the method on three reconstruction",
        "tasks. The discussion emphasizes provenance, uncertainty, and reproducible comparison.",
        "",
        "Reference clue: 10.1038/s41586-023-06004-9"
    )
    $content = New-Object System.Text.StringBuilder
    [void]$content.AppendLine("BT /F1 18 Tf 72 730 Td")
    foreach ($line in $lines) {
        [void]$content.AppendLine("($(Escape-PdfText $line)) Tj")
        [void]$content.AppendLine("0 -28 Td")
    }
    [void]$content.AppendLine("ET")
    $stream = $content.ToString()
    $objects = @(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
        "<< /Length $([Text.Encoding]::ASCII.GetByteCount($stream)) >>`nstream`n$stream`nendstream",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
    )
    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append("%PDF-1.4`n")
    $offsets = New-Object System.Collections.Generic.List[int]
    for ($i = 0; $i -lt $objects.Count; $i++) {
        $offsets.Add([Text.Encoding]::ASCII.GetByteCount($builder.ToString()))
        [void]$builder.Append("$($i + 1) 0 obj`n$($objects[$i])`nendobj`n")
    }
    $xref = [Text.Encoding]::ASCII.GetByteCount($builder.ToString())
    [void]$builder.Append("xref`n0 $($objects.Count + 1)`n0000000000 65535 f `n")
    foreach ($offset in $offsets) { [void]$builder.Append(('{0:D10} 00000 n ' -f $offset) + "`n") }
    [void]$builder.Append("trailer`n<< /Size $($objects.Count + 1) /Root 1 0 R >>`nstartxref`n$xref`n%%EOF`n")
    [IO.File]::WriteAllText($Path, $builder.ToString(), [Text.Encoding]::ASCII)
}

$slides = @(
    [ordered]@{
        file = "slide-01-overview.png"; width = 1920; height = 1080; kind = "Title";
        kicker = "Departmental Seminar"; title = "Geometric Priors for Sparse Neural Fields";
        body = @("Mira Chen, Northbridge Institute", "Why sparse observations need explicit geometric assumptions", "Roadmap: model, stability bound, reconstruction study")
        formula = "Objective: data fidelity + lambda times geometric regularization"
    },
    [ordered]@{
        file = "slide-02-method-portrait.png"; width = 1200; height = 1600; kind = "Method";
        kicker = "Method"; title = "A local chart regularizer";
        body = @("Estimate tangent neighborhoods without constructing a global mesh", "Separate observation noise from curvature-driven bias", "Optimize with a bounded alternating update")
        formula = "L(theta) = sum_i ||f_theta(x_i) - y_i||^2 + lambda R_geo(theta)"
    },
    [ordered]@{
        file = "slide-03-results-4x3.png"; width = 1600; height = 1200; kind = "Results";
        kicker = "Results"; title = "Stability improves under incomplete observations";
        body = @("Lower reconstruction error at 10 percent sampling density", "Confidence intervals remain separated across three fixed seeds", "Ablation: removing the geometric term increases boundary artifacts")
        formula = "Relative error: 0.18 to 0.11 | boundary artifacts: minus 37 percent"
    },
    [ordered]@{
        file = "slide-04-references.png"; width = 2048; height = 1536; kind = "References";
        kicker = "Discussion and references"; title = "What remains uncertain?";
        body = @("How robust is the bound when neighborhood estimates are biased?", "Can provenance be retained through interactive reconstruction?", "Reference clue: DOI 10.1038/s41586-023-06004-9", "Follow-up: compare against neural operators on irregular domains")
        formula = "Key question: geometry prior or data-induced shortcut?"
    }
)

foreach ($slide in $slides) {
    New-AcademicSlide -Path (Join-Path $OutputRoot $slide.file) -Width $slide.width -Height $slide.height -Kicker $slide.kicker -Title $slide.title -Body $slide.body -Formula $slide.formula -Kind $slide.kind
}
New-AbstractPdf -Path (Join-Path $OutputRoot "seminar-abstract.pdf")

$manifest = [ordered]@{
    fixtureId = "geometric-neural-fields-v1"
    classification = "non-sensitive synthetic academic seminar"
    seminar = [ordered]@{
        title = "Geometric Priors for Sparse Neural Fields"
        speaker = "Dr. Mira Chen"
        affiliation = "Northbridge Institute"
        location = "Research Commons 3.12"
        abstract = "A synthetic seminar about geometric regularization, stability, and sparse neural field reconstruction."
    }
    slides = @($slides | ForEach-Object { [ordered]@{ file = $_.file; width = $_.width; height = $_.height; role = $_.kind } })
    abstractPdf = "seminar-abstract.pdf"
    manualTranscript = @(
        "The first result separates geometric bias from sampling noise.",
        "The discussion then connects the stability bound to practical reconstruction."
    )
    referenceClue = "10.1038/s41586-023-06004-9"
    seminarBrief = [ordered]@{
        methods = "Local chart regularization with sparse neural field reconstruction."
        findings = "The synthetic evaluation reports lower reconstruction error and fewer boundary artifacts."
        followUp = "Compare robustness under biased neighborhood estimates."
    }
    formula = "L(theta) = data fidelity + lambda R_geo(theta)"
}
$manifestPath = Join-Path $OutputRoot "fixture-manifest.json"
$manifest | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 -Path $manifestPath

Write-Output $manifestPath
