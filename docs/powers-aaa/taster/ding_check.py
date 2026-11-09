#!/usr/bin/env python3
"""Finds the ding: a narrow steady tone above 800 Hz sounding during the loud part of a sound. Euan, 9 Nov: "the dings, the
high-pitched sounds... the launch moment... sounds super synthetic". Nobody here can hear, so this measures it.
Recordings and noise have energy spread across many frequencies; a struck-glass or sine layer puts it in a few thin lines
that hold still. Usage: ding_check.py file.ogg|mp3 ... prints the worst tone in each file."""
import os, subprocess, sys
import numpy as np

SR = 44100
N, HOP = 4096, 512          # 93 ms window, 11.6 ms step
FMIN, FMAX = 800.0, 12000.0
LINE_DB = 14.0              # how far a line must stand above the spectrum around it
HOLD = 0.045                # how long it must hold its frequency (s) to be a tone, not a click
LOUD_DB = 14.0              # only look at frames within this much of the loudest frame


def decode(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<f4").astype(float)


def spec(y):
    y = np.concatenate([y, np.zeros(N)])
    w = np.hanning(N)
    count = max(1, (len(y) - N) // HOP)
    return np.array([np.abs(np.fft.rfft(y[i * HOP:i * HOP + N] * w)) for i in range(count)])


def lines(mag):
    """dB above the local spectrum for every bin of one frame, using the median of about a third of an octave around it."""
    db = 20 * np.log10(mag + 1e-9)
    out = np.zeros_like(db)
    hzbin = SR / N
    for k in range(int(FMIN / hzbin), min(len(db) - 2, int(FMAX / hzbin))):
        if db[k] < db[k - 1] or db[k] < db[k + 1]:
            continue
        span = max(6, int(k * 0.12))
        lo, hi = max(1, k - span), min(len(db), k + span + 1)
        out[k] = db[k] - np.median(db[lo:hi])
    return out


def check(y):
    """Returns dict(ding, hz, at, stand, share): the strongest held tone in the loud part."""
    if len(y) < N // 2:
        y = np.concatenate([y, np.zeros(N)])
    S = spec(y)
    energy = np.sqrt(np.sum(S ** 2, axis=1))
    top = np.max(energy) + 1e-12
    loud = 20 * np.log10(energy / top + 1e-9) > -LOUD_DB
    need = max(2, int(HOLD * SR / HOP))
    tracks = {}
    best = dict(ding=False, hz=0.0, at=0.0, stand=0.0, share=0.0, secs=0.0)
    active = {}
    for f, m in enumerate(S):
        if not loud[f]:
            active = {}
            continue
        L = lines(m)
        now = {}
        for k in np.nonzero(L > LINE_DB)[0]:
            prev = next((active[j] for j in (k, k - 1, k + 1, k - 2, k + 2) if j in active), None)
            t = prev or dict(start=f, stand=[], power=[])
            t["stand"].append(L[k])
            band = np.sum(m[max(0, k - 2):k + 3] ** 2)
            t["power"].append(band / (np.sum(m ** 2) + 1e-12))
            t["k"] = k
            t["end"] = f
            now[k] = t
        active = now
        for t in now.values():
            n = len(t["stand"])
            if n >= need:
                stand, share = float(np.median(t["stand"])), float(np.median(t["power"]))
                if share > best["share"]:
                    best = dict(ding=True, hz=round(t["k"] * SR / N), at=round(t["start"] * HOP / SR, 2), stand=round(stand, 1), share=round(share, 4), secs=round(n * HOP / SR, 3))
    # a ding has to carry a real share of the frame, not be a faint line under a loud hit
    best["ding"] = best["ding"] and best["share"] >= 0.02
    return best


if __name__ == "__main__":
    for p in sys.argv[1:]:
        r = check(decode(p))
        print("%-46s %s" % (os.path.basename(p), ("DING %5d Hz at %.2fs, %.1f%% of the sound then, held %.2fs" % (r["hz"], r["at"], 100 * r["share"], r["secs"])) if r["ding"] else "clean"))
