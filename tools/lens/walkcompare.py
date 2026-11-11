#!/usr/bin/env python3
"""Measurement only (Steady 60, 12 Nov): before and after pictures of a still scene, against the scene's own noise.
For each set <prefix>_ref_<n>.png and <prefix>_new_<n>.png in a results folder: the per-pixel change (8-bit steps, all
three channels, the whole frame) between consecutive frames of the same code (the noise a still scene has from frame
to frame), and between the before and after frames taken nearest in time. A change that gives the same picture shows
no more across than within.
Shots taken A B A B A (ref_1, new_1, ref_2, new_2, ref_3, each a fixed time after the last) are read as such: the
noise is each frame against the next of the same code, two steps apart; the change is each frame against its neighbour
of the other code, one step apart. A change that gives the same picture shows no more across than within, though the
pairs across are nearer in time.
Also counts the twin check's shots (t_*.png, tools/lens/twincheck.py): magenta where two composites of one frame differ.
Also counts the walk check's shots (k_*.png, the light buffer under `lensdebug 5` with lens_light_walkcheck): red
where the two walks differ, green where a pixel walked.
tools/lens/walkcompare.py run-autopilot/results/<name>"""
import glob, os, re, sys
from PIL import Image, ImageChops

def diff_stats(a, b):
    d = ImageChops.difference(a, b)
    hist = [0] * 256
    for ch in d.split():
        for i, c in enumerate(ch.histogram()):
            hist[i] += c
    n = sum(hist)
    mean = sum(i * c for i, c in enumerate(hist)) / n
    acc, p99, p999 = 0, None, None
    for i, c in enumerate(hist):
        acc += c
        if p99 is None and acc >= n * 0.99:
            p99 = i
        if p999 is None and acc >= n * 0.999:
            p999 = i
    over = sum(c for i, c in enumerate(hist) if i >= 6) / n * 100
    top = max(i for i, c in enumerate(hist) if c)
    return mean, p99, p999, over, top

def row(name, s):
    print("%-34s mean %5.2f  p99 %3d  p99.9 %3d  over5 %6.3f%%  max %3d" % ((name,) + s))

root = sys.argv[1]
sets = {}
for f in sorted(glob.glob(os.path.join(root, "*_ref_*.png")) + glob.glob(os.path.join(root, "*_new_*.png"))):
    m = re.match(r"(.+)_(ref|new)_(\d+)\.png", os.path.basename(f))
    if m:
        sets.setdefault(m.group(1), {}).setdefault(m.group(2), []).append(f)
for name, s in sorted(sets.items()):
    ref = [Image.open(f).convert("RGB") for f in sorted(s.get("ref", []))]
    new = [Image.open(f).convert("RGB") for f in sorted(s.get("new", []))]
    print(name)
    for i in range(len(ref) - 1):
        row("  ref %d vs ref %d (noise)" % (i + 1, i + 2), diff_stats(ref[i], ref[i + 1]))
    for i in range(len(new) - 1):
        row("  new %d vs new %d (noise)" % (i + 1, i + 2), diff_stats(new[i], new[i + 1]))
    if ref and len(ref) == len(new) + 1:
        # A B A B A: the sequence in the order taken, each against its neighbour (the other code, one step apart)
        seq = [x for pair in zip(ref, new) for x in pair] + [ref[-1]]
        names = [n for i in range(len(new)) for n in ("ref %d" % (i + 1), "new %d" % (i + 1))] + ["ref %d" % len(ref)]
        for i in range(len(seq) - 1):
            row("  %s vs %s (across, one step)" % (names[i], names[i + 1]), diff_stats(seq[i], seq[i + 1]))
    elif ref and new:
        row("  ref %d vs new 1 (across)" % len(ref), diff_stats(ref[-1], new[0]))
        for i in range(min(len(ref), len(new))):
            row("  ref %d vs new %d (across)" % (i + 1, i + 1), diff_stats(ref[i], new[i]))

checks = sorted(glob.glob(os.path.join(root, "k_*.png")))
if checks:
    print("walk check (red: the walks differ; green: walked), pixels of the shot")
for f in checks:
    im = Image.open(f).convert("RGB")
    r, g, b = im.split()
    red = sum(c for i, c in enumerate(r.point(lambda v: 255 if v > 100 else 0).histogram()) if i == 255)
    green = sum(c for i, c in enumerate(g.point(lambda v: 255 if v > 100 else 0).histogram()) if i == 255)
    n = im.size[0] * im.size[1]
    print("  %-22s differ %8d (%.4f%%)  walked %8d (%.1f%%)" % (os.path.basename(f), red, red / n * 100, green, green / n * 100))

twins = sorted(glob.glob(os.path.join(root, "t_*.png")))
if twins:
    print("twin check (where two composites of one frame differ, by the most in any channel), pixels of the shot")
def hot(ch, on):
    return ch.point(lambda v: 255 if v > 200 else 0) if on else ch.point(lambda v: 255 if v < 60 else 0)
for f in twins:
    im = Image.open(f).convert("RGB")
    r, g, b = im.split()
    n = im.size[0] * im.size[1]
    # the marks are pure colours over the picture at half brightness (never over 128), so none is the picture's
    counts = []
    for want in ((1, 0, 1), (1, 1, 0), (0, 1, 1)):
        m = ImageChops.multiply(ImageChops.multiply(hot(r, want[0]), hot(g, want[1])), hot(b, want[2]))
        counts.append(m.histogram()[255])
    print("  %-26s one step %8d   2-4 steps %8d   more %8d   (%.4f%% of %dx%d)" % ((os.path.basename(f),) + tuple(counts) + (sum(counts) / n * 100, im.size[0], im.size[1])))
