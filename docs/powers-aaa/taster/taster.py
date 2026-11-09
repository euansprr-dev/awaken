#!/usr/bin/env python3
"""A disposable sound taster for the powers plan: four powers as the game plays them today, and a first idea of their own
pack, each with an ordinary release and a Clean one. Nothing here goes in the game. Run from the repo root:
    python3 docs/powers-aaa/taster/taster.py
Writes MP3s and levels.json next to this file. Original synthesis plus the repo's own CC0 Kenney hits."""
import json, os, subprocess, sys
import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
sys.path[:0] = [os.path.join(ROOT, "tools/art"), os.path.join(ROOT, "tools/art/specs")]
import audiokit as K
import ritual_sounds as R
import audio_arms as AA
import audio_power_kits as PK

OUT = os.path.dirname(os.path.abspath(__file__))
SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/powers")
TARGET = -17.0


def ogg(name):
    y = K.read_audio(os.path.join(SND, name + ".ogg"))
    return K.mono(y) if y.ndim > 1 else y


def play(buf, name, at, vol, pitch):
    """As Minecraft plays one: pitch by rate, gain clamped to 1."""
    R.put(buf, AA.shift(ogg(name), max(0.5, min(2.0, pitch))), at, min(vol, 1.0))


# ---- today, built from the real files at the real moments in the code -----------------------------------------------------

def today_haymaker():
    b = np.zeros(K.ns(2.4))
    play(b, "power_kit_charge_up_0", 0.0, 0.7, 0.8)      # KineticPowers: press
    play(b, "power_kit_charge_up_1", 1.0, 0.6, 1.6)      # full charge
    play(b, "power_kit_whoosh_0", 1.05, 0.8, 1.3)        # release
    play(b, "power_kit_punch_0", 1.12, 1.2, 0.8)         # the hit
    return b


def today_railgun():
    b = np.zeros(K.ns(2.6))
    play(b, "power_kit_charge_up_0", 0.0, 0.8, 0.6)      # TechPowers: the same charge file as the Haymaker
    play(b, "power_kit_charge_up_1", 1.15, 0.6, 2.0)
    play(b, "power_kit_rail_0", 1.2, 1.0, 1.3)
    return b


def today_well():
    b = np.zeros(K.ns(4.4))
    play(b, "power_kit_gravity_0", 0.0, 1.2, 0.7)        # FlightPowers: one sound at the start of a four-second field
    return b


def today_step():
    b = np.zeros(K.ns(1.4))
    play(b, "power_kit_shadow_0", 0.0, 0.9, 1.0)         # ShadowPowers: leaving
    play(b, "power_kit_shadow_1", 0.05, 0.9, 1.3)        # arriving
    return b


# ---- a first idea of each one's own pack ------------------------------------------------------------------------------------

def haymaker(clean):
    s = 41
    H = 1.0
    n = K.ns(H)
    u = K.tvec(n) / H
    f = K.glide(n, 42, 98)
    groan = R.unit(K.sine(f, n) + 0.5 * K.sine(f * 2.03, n) + 0.22 * K.sine(f * 3.1, n)) * u ** 1.7
    creak = R.unit(K.bandpass(K.crackle(n, 26.0, s, 0.5, 0.9), 1500, 1.4)) * (0.15 + 0.85 * u)
    air = R.unit(R.sweep(K.noise(n, "pink", s + 1), lambda T: 170 + 1500 * (T / H) ** 2, 2.3)) * u ** 2.4
    layers = [(groan, 0, 0.55), (creak, 0, 0.13), (air, 0, 0.22), (PK.cloth_rustle(s + 2, 0.45), 0.04, 0.3)]
    for i, at in enumerate((0.16, 0.31, 0.44)):                  # knuckles closing
        layers.append((PK.click(0.05, s + 3 + i, 1700 + 300 * i), at, 0.5))
    layers.append((PK.thump(0.22, 96, 58, 0.09, 1.7), 0.93, 0.8))  # the peak: you feel it arrive
    layers.append((R.unit(K.bar(K.hz(45), K.ns(0.4), 0.32, 0.55, s + 8)), 0.93, 0.42 if not clean else 0.7))
    rel = 1.02
    hit = rel + (0.1 if not clean else 0.13)                     # a Clean one hangs for a heartbeat longer
    layers += [(PK.whoosh(0.2, 420, 3900, 0.55, s + 9, q=1.1), rel, 0.75),
               (PK.punch(s + 10, 0.55), hit, 1.25), (PK.body(s + 11, 0.5, 620, 0.5), hit + 0.002, 1.05),
               (R.crack(s + 12, 0.55, 0.3), hit + 0.003, 0.65), (PK.thump(0.8, 96, 27, 0.46, 2.5), hit, 1.25),
               (PK.rubble(s + 13, 0.7, 70.0), hit + 0.08, 0.32), (PK.rumble(1.1, 120, 0.55, s + 14), hit + 0.02, 0.36)]
    if clean:
        layers += [(R.unit(K.bell(K.hz(33), K.ns(1.3), 2.0, 0.42, s + 15)), hit, 0.42), (PK.metal(s + 16, 0.62, 0.5), hit, 0.45),
                   (PK.thump(1.0, 62, 22, 0.75, 2.8), hit + 0.04, 0.9)]
    return PK.mix(2.8, layers, "stone", 0.2, 2.1, 0.4)


def railgun(clean):
    s = 71
    H = 1.2
    n = K.ns(H)
    u = K.tvec(n) / H
    f = K.glide(n, 170, 6300)
    whine = R.unit(K.sine(f, n) + 0.4 * K.sine(f * 1.007, n) + 0.12 * K.sine(f * 2.01, n)) * u ** 2.0
    hum = np.sign(np.sin(2 * np.pi * 96 * K.tvec(n)))
    hum = R.unit(K.lowpass(hum, 420, 2)) * (0.3 + 0.7 * u)
    layers = [(whine, 0, 0.3), (hum, 0, 0.2), (PK.servo(0.35, 60, 180, s), 0.0, 0.24)]
    for i, at in enumerate((0.1, 0.32, 0.52, 0.7, 0.86, 0.99, 1.09)):  # the coils tripping, faster and higher
        layers.append((PK.click(0.04, s + 2 + i, 1100 + 420 * i), at, 0.62))
        layers.append((PK.metal(s + 12 + i, 1.3 + 0.1 * i, 0.09, "light"), at + 0.002, 0.2))
    layers.append((R.unit(K.glass(K.hz(96), K.ns(0.2), 0.85, 0.16, s + 22, 1.0)), 1.15, 0.42 if not clean else 0.75))
    fire = 1.2
    x = K.ns(0.012)
    crack = R.unit(K.highpass(K.noise(x, "white", s + 23), 1300, 2)) * K.perc(x, 0.0002, 0.008)
    layers += [(crack, fire, 1.5), (PK.air_burst(0.28, 2300, s + 24, q=0.9), fire, 0.65), (PK.thump(0.4, 140, 38, 0.22, 2.3), fire, 1.0)]
    tail = K.echo(R.unit(PK.metal(s + 25, 0.82, 0.4)), time=0.14, feedback=0.46, taps=5, damp=2300.0, pingpong=False)
    tail = K.mono(tail) if getattr(tail, "ndim", 1) > 1 else tail
    layers += [(tail, fire + 0.01, 0.26), (PK.rumble(1.4, 100, 0.8, s + 26), fire + 0.02, 0.36)]
    if clean:
        v = K.ns(0.34)
        vac = R.unit(K.bandpass(K.noise(v, "pink", s + 27), 900, 0.8)) * (K.tvec(v) / 0.34) ** 3
        layers += [(crack, fire + 0.06, 0.9), (vac, fire + 0.06, 0.35), (PK.thump(0.5, 72, 28, 0.32, 2.4), fire + 0.4, 0.95)]
    return PK.mix(3.2, layers, "field", 0.22, 1.7, 0.5)


def well(clean):
    s = 111
    D = 3.9
    n = K.ns(D)
    t = K.tvec(n)
    u = t / D
    body = R.unit(K.lowpass(K.sine(34 + 3 * np.sin(t * 1.3), n) + 0.5 * K.sine(68.4, n), 180, 2))
    throb = 0.5 + 0.5 * np.sin(2 * np.pi * 2.0 * t - 1.2) ** 2
    inward = R.unit(R.sweep(K.noise(n, "pink", s), lambda T: 3100 * 0.08 ** (((T * 2.0) % 1.0) ** 1.2) + 90, 1.3))
    inward *= (0.25 + 0.75 * u) * (0.55 + 0.45 * throb)
    orbit = R.unit(K.bandpass(K.crackle(n, 44.0, s + 1, 0.7, 0.65), 1200, 0.9)) * (0.25 + 0.5 * (0.5 + 0.5 * np.sin(t * 6.3)))
    fall = K.glide(n, 330, 48)
    tone = R.unit(K.sine(fall, n) + K.sine(fall * 1.012, n) + 0.5 * K.sine(fall * 1.5, n)) * np.sin(np.pi * np.minimum(u * 1.02, 1.0)) ** 0.8
    open_n = K.ns(0.5)
    rev = R.unit(K.lowpass(K.noise(open_n, "pink", s + 2), 900, 2)) * (K.tvec(open_n) / 0.5) ** 2.8
    layers = [(rev, 0, 0.6), (PK.thump(0.55, 70, 24, 0.36, 2.4), 0.5, 1.05), (body * throb, 0.45, 0.6), (inward, 0.45, 0.3), (orbit, 0.5, 0.15),
              (tone, 0.45, 0.2), (PK.fall(300, 24, 0.6, s + 3, 0.5, 0.3), D + 0.3, 0.9), (PK.thump(0.7, 62, 20, 0.5, 2.8), D + 0.48, 1.25)]
    if clean:
        layers += [(R.unit(K.bell(K.hz(28), K.ns(1.4), 1.8, 0.35, s + 4)), 0.5, 0.42), (PK.fall(800, 38, 0.8, s + 5, 0.3, 0.5), 0.5, 0.42)]
    return PK.mix(5.2, layers, "cavern" if "cavern" in R.ROOMS else "hall", 0.18, 1.4, 0.3)


def step(clean):
    s = 151
    B = 0.24
    n = K.ns(B)
    u = K.tvec(n) / B
    breath = R.unit(K.bandpass(K.noise(n, "pink", s), 1500, 0.9)) * u ** 2.6       # a breath taken in, and cut off
    gap = 0.05
    at = B + gap
    cut = AA.trim(AA.shift(AA.rec("knifeSlice"), 0.62), 0.28)
    c = K.ns(0.9)
    voice = R.unit(K.lowpass(K.choir(K.hz(45), c, "o", 5, s + 1, 0.2, 8, 7.0, 0.03), 1300, 2)) * K.perc(c, 0.004, 0.55)
    layers = [(breath, 0, 0.62), (R.pk(cut), at, 1.0), (PK.thump(0.28, 110, 44, 0.14, 2.0), at, 0.75), (voice, at, 0.26),
              (PK.ink(0.6, s + 2, 900), at + 0.03, 0.32)]
    if clean:
        layers += [(R.unit(K.glass(K.hz(90), K.ns(0.5), 0.8, 0.4, s + 3, 1.0)), at, 0.5), (PK.thump(0.34, 62, 30, 0.2, 2.3), at + 0.22, 0.85),
                   (PK.thump(0.3, 62, 30, 0.2, 2.3), at + 0.42, 0.55)]
    return PK.mix(1.7, layers, "small", 0.12, 1.5, 0.3)


# ---- out --------------------------------------------------------------------------------------------------------------------

def norm(y, lufs=TARGET):
    g = K.dbamp(lufs - K.loudness(K.stereo(y)))
    y = y * g
    p = np.max(np.abs(y))
    return y * (0.94 / p) if p > 0.94 else y


def mp3(path, y):
    data = np.clip(y, -1, 1).astype("<f4").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(K.SR), "-ac", "1", "-i", "-", "-b:a", "112k", path], input=data, check=True)


def envelope(y, points=110):
    n = max(1, len(y) // points)
    return [round(float(np.sqrt(np.mean(y[i * n:(i + 1) * n] ** 2))), 4) for i in range(points)]


def main():
    sets = {"haymaker": (today_haymaker, haymaker), "railgun": (today_railgun, railgun), "gravity_well": (today_well, well), "shadow_step": (today_step, step)}
    out = {}
    for name, (old, new) in sets.items():
        before = old()
        native = float(K.loudness(K.stereo(before)))
        rows = {"today": norm(before), "new": norm(new(False)), "clean": norm(new(True))}
        out[name] = {"today_native_lufs": round(native, 1)}
        for k, y in rows.items():
            mp3(os.path.join(OUT, f"{name}_{k}.mp3"), y)
            out[name][k] = {"seconds": round(len(y) / K.SR, 2), "lufs": round(float(K.loudness(K.stereo(y))), 1),
                            "peak": round(float(np.max(np.abs(y))), 3), "env": envelope(y)}
        print(name, out[name]["today_native_lufs"], {k: out[name][k]["lufs"] for k in rows})
    json.dump(out, open(os.path.join(OUT, "levels.json"), "w"))


if __name__ == "__main__":
    main()
