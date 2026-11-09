#!/usr/bin/env python3
"""Every picture a preview drew on one contact sheet, each with its name."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

out = Path(sys.argv[1])
shots = sorted(p for p in out.glob("*.png") if p.name != "sheet.png")
cols, w = 4, 480
rows = (len(shots) + cols - 1) // cols
h = w * 9 // 16
sheet = Image.new("RGB", (cols * w, rows * h), (20, 16, 12))
draw = ImageDraw.Draw(sheet)
for i, p in enumerate(shots):
    x, y = i % cols * w, i // cols * h
    sheet.paste(Image.open(p).convert("RGB").resize((w, h), Image.LANCZOS), (x, y))
    draw.text((x + 5, y + 4), p.stem, fill=(255, 240, 80))
sheet.save(out / "sheet.png")
