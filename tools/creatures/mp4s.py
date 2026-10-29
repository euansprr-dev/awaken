# python3 -I tools/creatures/mp4s.py <out dir> single <clip dir> <name> <label>
# python3 -I tools/creatures/mp4s.py <out dir> pair <left clip dir> <left label> <right clip dir> <right label> <name>
# Encodes autopilot clip frames (f00000.png..., times.txt = "<frame> <game tick>") at in-game speed.
import os
import subprocess
import sys

FONT = "/System/Library/Fonts/Supplemental/Arial.ttf"


def fps(d):
    ts = []
    with open(os.path.join(d, "times.txt")) as f:
        for ln in f:
            p = ln.split()
            if len(p) >= 2:
                ts.append(float(p[1]))
    if len(ts) < 2 or ts[-1] <= ts[0]:
        return 20.0
    # the mean rate, so the whole clip lasts exactly its game time (frames come unevenly on a slow runner)
    return max(4.0, min(60.0, (len(ts) - 1) / ((ts[-1] - ts[0]) / 20.0)))


def label(text):
    t = text.replace(":", r"\:").replace("'", "")
    return (f"drawtext=fontfile={FONT}:text='{t}':x=12:y=10:fontsize=22:fontcolor=white"
            f":box=1:boxcolor=black@0.55:boxborderw=6")


def single(out, d, name, text):
    r = fps(d)
    dst = os.path.join(out, name + ".mp4")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", f"{r:.3f}", "-i", os.path.join(d, "f%05d.png"),
                    "-vf", "scale=-2:480:flags=neighbor," + label(f"{text}  ({r:.0f} fps, game speed)"), "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    "-crf", "20", dst], check=True)
    print(dst, f"{r:.1f}")


def pair(out, a, la, b, lb, name):
    ra, rb = fps(a), fps(b)
    dst = os.path.join(out, name + ".mp4")
    # both resampled to 30 fps at their own game speed, side by side, the shorter one holds its last frame
    fc = (f"[0:v]fps=30,scale=-2:480:flags=neighbor,{label(la)}[l];[1:v]fps=30,scale=-2:480:flags=neighbor,{label(lb)}[r];"
          f"[l][r]hstack=inputs=2:shortest=0[v]")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error",
                    "-framerate", f"{ra:.3f}", "-i", os.path.join(a, "f%05d.png"),
                    "-framerate", f"{rb:.3f}", "-i", os.path.join(b, "f%05d.png"),
                    "-filter_complex", fc, "-map", "[v]", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "21", dst],
                   check=True)
    print(dst, f"{ra:.1f}/{rb:.1f}")


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    if sys.argv[2] == "single":
        single(out, sys.argv[3], sys.argv[4], sys.argv[5])
    else:
        pair(out, sys.argv[3], sys.argv[4], sys.argv[5], sys.argv[6], sys.argv[7])
