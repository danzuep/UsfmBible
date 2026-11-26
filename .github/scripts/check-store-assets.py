#!/usr/bin/env python3
"""Validate the Fastlane store listing against Google Play and F-Droid limits.

Usage: check-store-assets.py [--fix] [--root fastlane/metadata/android]

--fix pads screenshots that are too tall (Google Play allows at most 2:1) with
black bars on the sides, in place, so the original content is not cropped.
Needs Pillow (pip install pillow).
"""
import argparse
import math
import re
import sys
from pathlib import Path

from PIL import Image

MAX_RATIO = 2.0
PAD_RATIO = 1.95  # a little under the limit so rounding never tips it over
LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}


def pad_screenshot(path: Path) -> None:
    im = Image.open(path).convert("RGB")
    w, h = im.size
    new_w = math.ceil(h / PAD_RATIO)
    canvas = Image.new("RGB", (new_w, h), (0, 0, 0))
    canvas.paste(im, ((new_w - w) // 2, 0))
    canvas.save(path, quality=95)


def check_locale(loc: Path, fix: bool) -> list[str]:
    errors = []
    for name, limit in LIMITS.items():
        f = loc / name
        if not f.is_file():
            errors.append(f"{f}: missing")
            continue
        text = f.read_text(encoding="utf-8")
        if len(text.strip()) > limit:
            errors.append(f"{f}: {len(text.strip())} characters, limit {limit}")
        if name == "full_description.txt" and re.search(r"</?(ul|ol|li|p|div)\b", text):
            errors.append(f"{f}: Google Play does not render HTML lists, use '• ' lines")
    for f in sorted((loc / "changelogs").glob("*.txt")):
        if len(f.read_text(encoding="utf-8").strip()) > 500:
            errors.append(f"{f}: over 500 characters")
    images = loc / "images"
    icon = images / "icon.png"
    if not icon.is_file():
        errors.append(f"{icon}: missing")
    else:
        if Image.open(icon).size != (512, 512):
            errors.append(f"{icon}: must be 512x512")
        if icon.stat().st_size > 1024 * 1024:
            errors.append(f"{icon}: over 1 MB")
    feature = images / "featureGraphic.png"
    if not feature.is_file():
        errors.append(f"{feature}: missing")
    elif Image.open(feature).size != (1024, 500):
        errors.append(f"{feature}: must be 1024x500")
    shots = sorted(p for p in (images / "phoneScreenshots").glob("*") if p.suffix.lower() in (".jpg", ".jpeg", ".png"))
    if not 2 <= len(shots) <= 8:
        errors.append(f"{images / 'phoneScreenshots'}: {len(shots)} screenshots, Google Play needs 2 to 8")
    for p in shots:
        w, h = Image.open(p).size
        if max(w, h) > 2 * min(w, h):
            if fix:
                pad_screenshot(p)
                print(f"padded {p}")
                w, h = Image.open(p).size
            else:
                errors.append(f"{p}: {w}x{h} is taller than 2:1, run with --fix")
                continue
        if not 320 <= min(w, h) or max(w, h) > 3840:
            errors.append(f"{p}: {w}x{h}, sides must be 320 to 3840 pixels")
    return errors


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--fix", action="store_true")
    ap.add_argument("--root", default="fastlane/metadata/android")
    args = ap.parse_args()
    locales = [p for p in Path(args.root).iterdir() if p.is_dir()]
    errors = [e for loc in locales for e in check_locale(loc, args.fix)]
    for e in errors:
        print(f"::error::{e}")
    print("store assets OK" if not errors else f"{len(errors)} problem(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
