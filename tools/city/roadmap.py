#!/usr/bin/env python3
"""The roads' studio map (SPEC §28.9): renders run/road-survey.json (written by the village_plan test when
run/road-survey.request exists) as a picture of the land with its hubs, settlements and roads.

Usage: python3 -I tools/city/roadmap.py run/road-survey.json out.png [scale] [x0 z0 x1 z1]
  scale: blocks per pixel (default 4). The optional box crops to a part of the map (world blocks).
"""
import json
import math
import sys

from PIL import Image, ImageDraw, ImageFont

FAMILY = {0: "temperate", 1: "frost", 2: "desert", 3: "savanna", 4: "mire", 5: "highland", 6: "elder", 7: "coast", 8: "jungle", 9: "cherry"}
KIND_COLOUR = {"HAMLET": (240, 220, 160), "VILLAGE": (250, 200, 90), "TOWN": (255, 120, 60), "MINING": (180, 180, 200), "WAYHOUSE": (120, 230, 140)}


def main():
    src, out = sys.argv[1], sys.argv[2]
    scale = float(sys.argv[3]) if len(sys.argv) > 3 else 4.0
    d = json.load(open(src))
    land = d["land"]
    step, n, x0 = land["step"], land["n"], land["x0"]
    z0 = land.get("z0", x0)
    top, wet = land["top"], land["wet"]
    box = [x0, z0, x0 + (n - 1) * step, z0 + (n - 1) * step]
    if len(sys.argv) > 7:
        box = [int(v) for v in sys.argv[4:8]]
    w, h = int((box[2] - box[0]) / scale), int((box[3] - box[1]) / scale)
    img = Image.new("RGB", (w, h))
    px = img.load()

    def sample(arr, x, z):
        fx, fz = (x - x0) / step, (z - z0) / step
        i, j = max(0, min(n - 2, int(fx))), max(0, min(n - 2, int(fz)))
        u, v = min(1, max(0, fx - i)), min(1, max(0, fz - j))
        a, b, c, e = arr[j * n + i], arr[j * n + i + 1], arr[(j + 1) * n + i], arr[(j + 1) * n + i + 1]
        return a * (1 - u) * (1 - v) + b * u * (1 - v) + c * (1 - u) * v + e * u * v

    for yy in range(h):
        for xx in range(w):
            x, z = box[0] + xx * scale, box[1] + yy * scale
            t = sample(top, x, z)
            if sample(wet, x, z) > 0.5:
                deep = min(1, sample(wet, x, z) / 12)
                px[xx, yy] = (int(40 - 20 * deep), int(90 - 30 * deep), int(150 + 40 * (1 - deep)))
                continue
            # hillshade from the north-west
            dx = sample(top, x + step, z) - sample(top, x - step, z)
            dz = sample(top, x, z + step) - sample(top, x, z - step)
            shade = max(0.35, min(1.25, 1 - (dx + dz) * 0.035))
            hgt = max(0, min(1, (t - 62) / 110))
            base = (int(90 + 100 * hgt), int(130 + 70 * hgt), int(70 + 110 * hgt))
            px[xx, yy] = tuple(max(0, min(255, int(c * shade))) for c in base)
    dr = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 13)
    except OSError:
        font = ImageFont.load_default()

    def P(x, z):
        return ((x - box[0]) / scale, (z - box[1]) / scale)

    # Carillon and the Commons
    cx, cz = d["centre"]
    r = 330 / scale
    dr.ellipse([P(cx, cz)[0] - r, P(cx, cz)[1] - r, P(cx, cz)[0] + r, P(cx, cz)[1] + r], outline=(255, 255, 255), width=2)
    r = 700 / scale
    dr.ellipse([P(cx, cz)[0] - r, P(cx, cz)[1] - r, P(cx, cz)[0] + r, P(cx, cz)[1] + r], outline=(200, 200, 255), width=1)
    dr.text(P(cx - 40, cz), "CARILLON", fill=(255, 255, 255), font=font)
    for road in d["roads"]:
        pts = road["pts"]
        width = 3 if road["cls"] == 0 else 2
        for a, b in zip(pts, pts[1:]):
            col = (235, 210, 150) if road["cls"] == 0 else (210, 180, 120)
            if a[3] & 1 and b[3] & 1:
                col = (230, 60, 60)
            elif a[3] & 2 and b[3] & 2:
                col = (60, 200, 90)
            elif a[3] & 4 and b[3] & 4:
                col = (90, 90, 90)
            dr.line([P(a[0], a[1]), P(b[0], b[1])], fill=col, width=width)
        for m in road["marks"]:
            q = P(m["x"], m["z"])
            c = (255, 255, 255) if not m["cairn"] else (255, 80, 255)
            dr.rectangle([q[0] - 2, q[1] - 2, q[0] + 2, q[1] + 2], fill=c)
    for hub in d["hubs"]:
        q = P(hub["hx"], hub["hz"])
        dr.line([q[0] - 4, q[1], q[0] + 4, q[1]], fill=(0, 0, 0))
        dr.line([q[0], q[1] - 4, q[0], q[1] + 4], fill=(0, 0, 0))
        q2 = P(hub["x"], hub["z"])
        if hub["kind"] in ("CROSSROADS",):
            dr.ellipse([q2[0] - 4, q2[1] - 4, q2[0] + 4, q2[1] + 4], outline=(0, 0, 0), width=2)
        label = hub["region"] + (" (first town)" if hub["first"] else " (town?)" if hub["town"] else "")
        dr.text((q[0] + 5, q[1] + 5), label, fill=(20, 20, 40), font=font)
    for s in d["settlements"]:
        if "x" not in s:
            q = P(s["ax"], s["az"])
            dr.line([q[0] - 3, q[1] - 3, q[0] + 3, q[1] + 3], fill=(120, 0, 0), width=2)
            dr.line([q[0] - 3, q[1] + 3, q[0] + 3, q[1] - 3], fill=(120, 0, 0), width=2)
            continue
        q = P(s["x"], s["z"])
        r = max(3, s["r"] / scale)
        col = KIND_COLOUR.get(s["kind"], (255, 255, 255))
        dr.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], outline=col, width=2)
        dr.text((q[0] + r + 2, q[1] - 7), "%s (%s, %d)" % (s["name"], s["kind"].lower(), s["people"]), fill=(255, 255, 255), font=font)
    dr.text((8, 8), "seed %s: %d roads, %d legs, %s" % (d["seed"], len(d["roads"]), d["legs"], d["stats"]), fill=(255, 255, 255), font=font)
    img.save(out)
    print("wrote", out, img.size)


if __name__ == "__main__":
    main()
