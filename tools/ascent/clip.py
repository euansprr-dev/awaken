"""Turns a recorded tour clip (run-autopilot/clips/<name>/f*.png + times.txt, from `/awaken ascent pilot rec`) into
a real-time video and a contact sheet.

    python3 -I tools/ascent/clip.py <clip dir> [--fps 30] [--keep] [--sheet-every 0.25]

Writes <clip dir>.mp4 (H.264, even frame rate: each frame held for its real game time) and <clip dir>_sheet.png
(frames at even game-time steps, with their tick, for reviewing a move's anticipation, action and follow-through
without a video player). Deletes the PNG frames afterwards unless --keep.
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw


def main(argv):
    d = argv[0].rstrip("/")
    fps = 30
    keep = "--keep" in argv
    every = 0.25  # seconds of game time between sheet frames
    if "--fps" in argv:
        fps = int(argv[argv.index("--fps") + 1])
    if "--sheet-every" in argv:
        every = float(argv[argv.index("--sheet-every") + 1])
    times = []
    with open(os.path.join(d, "times.txt")) as f:
        for line in f:
            i, t = line.split()
            p = os.path.join(d, f"f{int(i):05d}.png")
            if os.path.exists(p):
                times.append((p, float(t)))
    if len(times) < 2:
        print("no frames in", d)
        return 1
    listing = os.path.join(d, "frames.txt")
    with open(listing, "w") as f:
        for k, (p, t) in enumerate(times):
            dur = (times[k + 1][1] - t) / 20.0 if k + 1 < len(times) else 1.0 / fps
            f.write(f"file '{os.path.abspath(p)}'\nduration {max(dur, 0.001):.5f}\n")
        f.write(f"file '{os.path.abspath(times[-1][0])}'\n")
    mp4 = d + ".mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", listing,
                    "-vf", f"fps={fps},scale=trunc(iw/2)*2:trunc(ih/2)*2", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", mp4],
                   check=True)
    # contact sheet: frames at even game-time steps
    t0 = times[0][1]
    picks, nxt = [], t0
    for p, t in times:
        if t >= nxt:
            picks.append((p, t))
            nxt = t + every * 20
    picks = picks[:40]
    thumbs = []
    for p, t in picks:
        im = Image.open(p).convert("RGB")
        im.thumbnail((480, 300))
        dr = ImageDraw.Draw(im)
        dr.rectangle((0, 0, 92, 14), fill=(0, 0, 0))
        dr.text((3, 1), f"t+{(t - t0) / 20:5.2f}s", fill=(255, 255, 255))
        thumbs.append(im)
    cols = 5
    w, h = thumbs[0].size
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * w, rows * h), (0, 0, 0))
    for k, im in enumerate(thumbs):
        sheet.paste(im, ((k % cols) * w, (k // cols) * h))
    sheet.save(d + "_sheet.png")
    span = (times[-1][1] - t0) / 20
    print(f"{mp4}: {len(times)} frames over {span:.1f} s ({len(times) / max(span, 1e-6):.0f} fps captured); sheet {d}_sheet.png")
    if not keep:
        for p, _ in times:
            os.remove(p)
        os.remove(listing)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
