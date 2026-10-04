#!/usr/bin/env python3
"""Compare one Android screenshot against a reviewed visual golden."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageEnhance


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--diff", required=True)
    parser.add_argument("--changed-ratio-limit", type=float, default=0.025)
    parser.add_argument("--mean-absolute-error-limit", type=float, default=4.0)
    parser.add_argument("--pixel-threshold", type=int, default=32)
    parser.add_argument("--mask-top-ratio", type=float, default=0.045)
    parser.add_argument("--mask-bottom-ratio", type=float, default=0.055)
    args = parser.parse_args()

    baseline_path = Path(args.baseline)
    candidate_path = Path(args.candidate)
    diff_path = Path(args.diff)
    baseline = Image.open(baseline_path).convert("RGB")
    candidate = Image.open(candidate_path).convert("RGB")

    result: dict[str, object] = {
        "baseline": str(baseline_path),
        "candidate": str(candidate_path),
        "baselineSize": list(baseline.size),
        "candidateSize": list(candidate.size),
        "status": "FAIL",
    }
    if baseline.size != candidate.size:
        result["failure"] = "SIZE_MISMATCH"
        print(json.dumps(result, ensure_ascii=False))
        return 1

    width, height = baseline.size
    top = max(0, min(height, round(height * args.mask_top_ratio)))
    bottom = max(top, min(height, height - round(height * args.mask_bottom_ratio)))
    baseline_crop = baseline.crop((0, top, width, bottom))
    candidate_crop = candidate.crop((0, top, width, bottom))
    diff = ImageChops.difference(baseline_crop, candidate_crop)
    pixel_data = (
        diff.get_flattened_data()
        if hasattr(diff, "get_flattened_data")
        else diff.getdata()
    )
    pixels = list(pixel_data)
    channel_total = max(1, len(pixels) * 3)
    absolute_total = sum(sum(pixel) for pixel in pixels)
    mean_absolute_error = absolute_total / channel_total
    changed_pixels = sum(1 for pixel in pixels if max(pixel) >= args.pixel_threshold)
    changed_ratio = changed_pixels / max(1, len(pixels))

    diff_path.parent.mkdir(parents=True, exist_ok=True)
    visual = Image.new("RGB", baseline.size, "black")
    visual.paste(ImageEnhance.Contrast(diff).enhance(3.0), (0, top))
    visual.save(diff_path)

    passed = (
        changed_ratio <= args.changed_ratio_limit
        and mean_absolute_error <= args.mean_absolute_error_limit
    )
    result.update(
        {
            "status": "PASS" if passed else "FAIL",
            "changedPixelRatio": round(changed_ratio, 8),
            "meanAbsoluteError": round(mean_absolute_error, 5),
            "pixelThreshold": args.pixel_threshold,
            "changedRatioLimit": args.changed_ratio_limit,
            "meanAbsoluteErrorLimit": args.mean_absolute_error_limit,
            "maskedRows": {"top": top, "bottomStart": bottom},
            "diff": str(diff_path),
        }
    )
    print(json.dumps(result, ensure_ascii=False))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
