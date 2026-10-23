"""Spectrogram contact sheets for the mod's audio, to judge it by eye: aliasing (mirrored lines folding down),
clipping, clicks (full-height vertical lines), harsh highs, mud, and where the energy sits over time.

    python3 tools/art/audio_sheet.py out.png [glob ...]      default glob: every music_*.ogg
    python3 tools/art/audio_sheet.py out.png ui_ --cols 3     names starting with ui_

Each tile: name, length, gated RMS, LUFS and true peak; a log-frequency spectrogram (30 Hz-20 kHz, 0 to -90 dB);
under it the peak envelope (pale) and the 400 ms loudness (bright) on a dB scale; a red tick wherever |x| >= 0.999.
"""
import glob
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import audiokit as K  # noqa: E402

SOUNDS = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../src/main/resources/assets/awaken/sounds"))

STOPS = [(0.0, (8, 8, 18)), (0.25, (52, 16, 92)), (0.5, (160, 40, 110)), (0.72, (240, 110, 70)),
         (0.9, (252, 200, 110)), (1.0, (252, 252, 220))]


def cmap(v):
    v = np.clip(v, 0, 1)
    xs = [s[0] for s in STOPS]
    out = np.zeros(v.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(v, xs, [s[1][c] for s in STOPS])
    return out.astype(np.uint8)


def spectrogram(x, width, height, fmin=30.0, fmax=20000.0, floor=-90.0):
    m = K.mono(x)
    frame = 4096
    hop = max(64, (len(m) - frame) // max(1, width - 1)) if len(m) > frame else 64
    win = np.hanning(frame)
    cols = []
    for s in range(0, max(1, len(m) - frame + 1), hop):
        seg = m[s:s + frame]
        if len(seg) < frame:
            seg = K.pad_to(seg, frame)
        cols.append(np.abs(np.fft.rfft(seg * win)))
    if not cols:
        cols = [np.abs(np.fft.rfft(K.pad_to(m, frame) * win))]
    S = np.array(cols).T  # bins x frames
    S = 20 * np.log10(S / (frame / 4) + 1e-12)
    f = np.fft.rfftfreq(frame, 1 / K.SR)
    rows = np.geomspace(fmax, fmin, height)
    img = np.zeros((height, S.shape[1]))
    for i, fr in enumerate(rows):
        lo = fr / 2 ** (1 / 24)
        hi = fr * 2 ** (1 / 24)
        sel = (f >= lo) & (f <= hi)
        img[i] = S[sel].max(axis=0) if sel.any() else np.interp(fr, f, np.arange(len(f))) * 0 + S[np.argmin(np.abs(f - fr))]
    img = (img - floor) / -floor
    rgb = cmap(img)
    im = Image.fromarray(rgb, "RGB").resize((width, height), Image.BILINEAR)
    return im


def envelope_strip(x, width, height):
    st = K.stereo(x)
    n = len(st)
    im = Image.new("RGB", (width, height), (14, 14, 22))
    dr = ImageDraw.Draw(im)
    for db in (-6, -12, -24, -48):
        y = int(height * (-db) / 60)
        dr.line([(0, y), (width, y)], fill=(40, 40, 56))
    edges = np.linspace(0, n, width + 1).astype(int)
    blk = K.ns(0.4)
    prev = None
    for i in range(width):
        a, b = edges[i], max(edges[i] + 1, edges[i + 1])
        seg = st[a:b]
        pk = float(np.max(np.abs(seg))) if len(seg) else 0
        y = int(np.clip(height * (-K.ampdb(pk)) / 60, 0, height - 1))
        dr.line([(i, height - 1), (i, y)], fill=(70, 90, 120))
        c = (a + b) // 2
        w = st[max(0, c - blk // 2):c + blk // 2]
        rms = np.sqrt(np.mean(w ** 2)) if len(w) else 0
        yr = int(np.clip(height * (-K.ampdb(rms)) / 60, 0, height - 1))
        if prev is not None:
            dr.line([(i - 1, prev), (i, yr)], fill=(120, 220, 255))
        prev = yr
        if pk >= 0.999:
            dr.line([(i, 0), (i, 6)], fill=(255, 40, 40))
    return im


def tile(path, width=880, spec_h=210, env_h=46):
    x = K.read_audio(path)
    name = os.path.basename(path)[:-4]
    dur = len(x) / K.SR
    head = f"{name}   {dur:.1f} s   {'stereo' if x.ndim == 2 else 'mono'}   RMS {K.gated_rms(x):.1f} dBFS   " \
           f"{K.loudness(x):.1f} LUFS   TP {K.true_peak(x):.1f} dBFS"
    im = Image.new("RGB", (width, 18 + spec_h + env_h + 8), (24, 26, 34))
    dr = ImageDraw.Draw(im)
    dr.text((4, 3), head, fill=(220, 225, 235))
    im.paste(spectrogram(x, width, spec_h), (0, 18))
    # frequency guides: 100 Hz, 1 kHz, 10 kHz
    for fr, lab in ((100, "100"), (1000, "1k"), (10000, "10k")):
        y = 18 + int(spec_h * np.log(20000 / fr) / np.log(20000 / 30))
        dr.line([(0, y), (6, y)], fill=(200, 200, 200))
        dr.text((8, y - 6), lab, fill=(200, 200, 200))
    # seconds
    step = 1 if dur <= 12 else 5 if dur <= 60 else 15
    for s in np.arange(0, dur, step):
        xx = int(width * s / dur)
        dr.line([(xx, 18 + spec_h), (xx, 18 + spec_h + 4)], fill=(200, 200, 200))
    im.paste(envelope_strip(x, width, env_h), (0, 18 + spec_h + 4))
    return im


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    out = argv[0]
    cols = 1
    pats = []
    i = 1
    while i < len(argv):
        if argv[i] == "--cols":
            cols = int(argv[i + 1])
            i += 2
            continue
        pats.append(argv[i])
        i += 1
    if not pats:
        pats = ["music_"]
    files = []
    for p in pats:
        if any(ch in p for ch in "*?/"):
            files += glob.glob(p)
        else:
            files += glob.glob(os.path.join(SOUNDS, p + "*.ogg"))
    files = sorted(set(files))
    if not files:
        print("no files")
        return 1
    width = 880 if cols == 1 else 600
    tiles = [tile(f, width=width, spec_h=210 if cols == 1 else 150) for f in files]
    tw, th = tiles[0].size
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (tw + 8) + 8, rows * (th + 8) + 8), (10, 10, 14))
    for k, t in enumerate(tiles):
        sheet.paste(t, (8 + (k % cols) * (tw + 8), 8 + (k // cols) * (th + 8)))
    sheet.save(out)
    print(out, len(tiles), "sounds")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
