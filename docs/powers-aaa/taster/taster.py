#!/usr/bin/env python3
"""A disposable sound taster for the powers plan: four powers as the game plays them today, a second idea of their own pack
with no dings in it, and the layers that were taken out, on their own. Nothing here goes in the game. Run from the repo root:
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


# ---- a second idea of each one's own pack, after Euan's ear (9 Nov) -----------------------------------------------------------
# "the dings, the high-pitched sounds... the launch moment... sounds super synthetic". So: nothing here is a pitched struck layer
# or a sine above the sub. Every attack is a recording or noise. Brightness comes from air and grit, never from a thing that rings.
# Each recipe returns (the new sound, the layers that were taken out of the first idea, played where they used to be).

def low(name, ratio, fc, dur):
    """A recording taken down in pitch, with the ring above fc rolled off so only its body is left."""
    return R.unit(K.lowpass(AA.trim(AA.shift(AA.rec(name), ratio), dur), fc, 2))


_FIELD = {}


def field(tag, dur, ratio=1.0, hp=None, lp=None, start=None):
    """A stretch of one of the repo's CC0 field recordings (tools/art/sources/ritual). With no start it opens on the loudest attack."""
    import ding_check as D
    if tag not in _FIELD:
        _FIELD[tag] = D.decode(os.path.join(ROOT, "tools/art/sources/ritual", tag + ".flac"))
    x = _FIELD[tag]
    if start is None:
        w = K.ns(0.01)
        e = np.convolve(np.abs(x), np.ones(w) / w, "same")
        at = int(np.argmax(e))
        on = at - int(np.argmax(e[max(0, at - K.ns(0.25)):at + 1][::-1] < e[at] * 0.1)) if at else 0
        i = max(0, on - K.ns(0.002))
    else:
        i = K.ns(start)
    y = AA.shift(x[i:i + K.ns(dur * ratio * 1.05 + 0.1)], ratio)[:K.ns(dur)]
    if hp:
        y = K.highpass(y, hp, 2)
    if lp:
        y = K.lowpass(y, lp, 2)
    return R.unit(K.declick(y, 0.0004, min(0.05, dur * 0.2)))


def swell(n, u, seed, f0, f1, q, power):
    """Air being pulled in: a band of noise that climbs. It has no pitch to ring."""
    H = len(u) / K.SR
    return R.unit(R.sweep(K.noise(n, "pink", seed), lambda T: f0 + (f1 - f0) * (T / H) ** 2, q)) * u ** power


def pressure(n, u, seed, fc0, fc1, power):
    H = len(u) / K.SR
    return R.unit(R.sweep_lp(K.noise(n, "pink", seed), lambda T: fc0 + (fc1 - fc0) * (max(T, 0.0) / H) ** 1.5, 2)) * u ** power


def snap(dur, seed, hp, t60):
    n = K.ns(dur)
    return R.unit(K.highpass(K.noise(n, "white", seed), hp, 2)) * K.perc(n, 0.0005, t60)


def out_layers(dur, layers):
    b = np.zeros(K.ns(dur))
    for sig, at, g in layers:
        R.put(b, sig, at, g)
    return b


def haymaker():
    s = 241
    H = 1.0
    n = K.ns(H)
    u = K.tvec(n) / H
    sub = R.unit(K.sine(K.glide(n, 38, 66), n)) * u ** 2.0                    # below hearing as a note: you feel it climb
    layers = [(sub, 0, 0.5), (pressure(n, u, s, 70, 420, 1.8), 0, 0.5), (PK.cloth_rustle(s + 2, 0.5), 0.02, 0.4),
              (low("handleSmallLeather2", 0.42, 900, 0.5), 0.1, 0.34), (swell(n, u, s + 1, 240, 1500, 1.8, 2.8), 0, 0.2)]
    for i, at in enumerate((0.16, 0.33, 0.48)):                               # the fist closing: leather, not a tick
        layers.append((low("handleSmallLeather", 0.7 + 0.05 * i, 3000, 0.16), at, 0.5))
    layers.append((PK.thump(0.2, 86, 54, 0.08, 1.6), 0.94, 0.75))             # the top of the wind-up is a weight settling, nothing rings
    rel, hit = 1.02, 1.12
    layers += [(PK.whoosh(0.2, 380, 3000, 0.55, s + 9, q=1.0), rel, 0.8), (PK.punch(s + 10, 0.52), hit, 1.3), (PK.body(s + 11, 0.48, 620, 0.5), hit + 0.002, 1.1),
               (field("1022", 0.55, 0.62, 80, 3200), hit, 0.85), (snap(0.03, s + 12, 2400, 0.018), hit, 0.4), (R.crack(s + 13, 0.5, 0.3), hit + 0.004, 0.45), (field("3113", 1.5, 0.8, None, 260), hit + 0.03, 0.6),
               (PK.thump(0.85, 92, 26, 0.48, 2.5), hit, 1.3), (PK.rubble(s + 14, 0.75, 60.0), hit + 0.09, 0.28), (PK.rumble(1.2, 110, 0.6, s + 15), hit + 0.02, 0.42)]
    took = [(R.unit(K.bar(K.hz(45), K.ns(0.4), 0.32, 0.55, 49)), 0.93, 0.7)]
    took += [(PK.click(0.05, 44 + i, 1700 + 300 * i), at, 0.5) for i, at in enumerate((0.16, 0.31, 0.44))]
    return PK.mix(2.8, layers, "stone", 0.2, 2.0, 0.4), out_layers(2.8, took)


def railgun():
    s = 271
    H = 1.2
    n = K.ns(H)
    u = K.tvec(n) / H
    hum = R.unit(K.lowpass(np.sign(np.sin(2 * np.pi * 88 * K.tvec(n))), 320, 2)) * (0.25 + 0.75 * u) * (0.85 + 0.15 * K.rng(s).normal(0, 1, n).clip(-1, 1))
    arc = K.bandpass(field("0950", H, 0.85, start=3.0), 1100, 0.5)
    layers = [(R.unit(arc) * u ** 1.6, 0, 0.42), (swell(n, u, s + 1, 130, 2400, 2.2, 2.0), 0, 0.2), (pressure(n, u, s + 2, 60, 300, 1.6), 0, 0.42), (hum, 0, 0.2)]
    for i, at in enumerate((0.1, 0.32, 0.52, 0.7, 0.86, 0.99, 1.09)):         # the coils: heavy relays closing, dull and getting quicker
        layers.append((low(("impactPlate_medium_000", "impactPlate_medium_003", "impactPlate_medium_004")[i % 3], 0.52 + 0.03 * i, 780, 0.12), at, 0.5 + 0.06 * i))
    fire = 1.26                                                               # the charge stops dead a hair before: the gap is the warning
    layers += [(snap(0.05, s + 23, 1100, 0.03), fire, 1.25), (PK.air_burst(0.32, 1500, s + 24, q=0.7), fire, 0.8), (low("impactPlate_heavy_001", 0.46, 3200, 0.45), fire, 0.7), (field("1023", 1.6, 1.0, 60, 5200), fire, 1.1),
               (PK.thump(0.5, 130, 34, 0.26, 2.4), fire, 1.25)]
    v = K.ns(0.5)
    wake = R.unit(K.bandpass(K.noise(v, "pink", s + 25), 700, 0.7)) * K.perc(v, 0.004, 0.35)
    tail = K.echo(wake, time=0.16, feedback=0.42, taps=4, damp=1500.0, pingpong=False)
    tail = K.mono(tail) if getattr(tail, "ndim", 1) > 1 else tail
    layers += [(tail, fire + 0.02, 0.5), (PK.rumble(1.5, 95, 0.9, s + 26), fire + 0.02, 0.5)]
    f = K.glide(n, 170, 6300)
    whine = R.unit(K.sine(f, n) + 0.4 * K.sine(f * 1.007, n) + 0.12 * K.sine(f * 2.01, n)) * u ** 2.0
    took = [(whine, 0, 0.3), (R.unit(K.glass(K.hz(96), K.ns(0.2), 0.85, 0.16, 93, 1.0)), 1.15, 0.5)]
    for i, at in enumerate((0.1, 0.32, 0.52, 0.7, 0.86, 0.99, 1.09)):
        took += [(PK.click(0.04, 73 + i, 1100 + 420 * i), at, 0.62), (PK.metal(83 + i, 1.3 + 0.1 * i, 0.09, "light"), at + 0.002, 0.2)]
    took.append((PK.metal(96, 0.82, 0.4), 1.21, 0.3))
    # the gap: everything that was charging is cut 60 ms before the shot
    hit = np.zeros(K.ns(3.3))
    for sig, t, g in layers:
        if t < fire - 0.001:
            sig = np.array(sig[:max(0, K.ns(fire - 0.06 - t))], copy=True)
            if len(sig) > 200:
                sig[-200:] *= np.linspace(1, 0, 200)
        R.put(hit, sig, t, g)
    b = R.Bus(3.3 + 0.5)
    b.add(R.glue(hit, 1.7), 0.0, 1.0, "field", 0.22, hp=70)
    return R.fade_out(b.render(), 0.2), out_layers(3.3, took)


def well():
    s = 311
    D = 3.9
    n = K.ns(D)
    t = K.tvec(n)
    u = t / D
    body = R.unit(K.lowpass(K.sine(32 + 3 * np.sin(t * 1.3), n) + 0.4 * K.sine(64.4, n), 150, 2))
    throb = 0.5 + 0.5 * np.sin(2 * np.pi * 2.0 * t - 1.2) ** 2
    inward = R.unit(R.sweep(K.noise(n, "pink", s), lambda T: 2400 * 0.07 ** (((T * 2.0) % 1.0) ** 1.2) + 80, 1.1)) * (0.25 + 0.75 * u) * (0.55 + 0.45 * throb)
    pull = R.unit(K.lowpass(field("0595", D, 0.7, start=2.0)[::-1], 1400, 2)) * np.sin(np.pi * np.minimum(u * 1.02, 1.0)) ** 0.8    # real wind, played backwards
    debris = R.unit(K.bandpass(K.crackle(n, 44.0, s + 1, 0.7, 0.65), 900, 0.8)) * (0.25 + 0.5 * (0.5 + 0.5 * np.sin(t * 6.3)))
    open_n = K.ns(0.5)
    rev = R.unit(K.lowpass(K.noise(open_n, "pink", s + 2), 800, 2)) * (K.tvec(open_n) / 0.5) ** 2.8
    end = K.ns(0.55)
    whump = R.unit(R.sweep_lp(K.noise(end, "pink", s + 3), lambda T: 90 + 900 * (T / 0.55) ** 3, 2)) * (K.tvec(end) / 0.55) ** 2.2
    layers = [(rev, 0, 0.6), (PK.thump(0.55, 66, 24, 0.36, 2.4), 0.5, 1.05), (body * throb, 0.45, 0.6), (inward, 0.45, 0.32), (pull, 0.45, 0.34), (debris, 0.5, 0.18),
              (whump, D - 0.05, 0.8), (field("1022", 0.7, 0.45, 40, 1800), D + 0.48, 1.1), (PK.thump(0.75, 58, 20, 0.5, 2.8), D + 0.48, 1.3), (field("3113", 1.1, 0.7, None, 200), D + 0.5, 0.6), (PK.rubble(s + 4, 0.9, 50.0), D + 0.55, 0.22)]
    fall = K.glide(n, 330, 48)
    tone = R.unit(K.sine(fall, n) + K.sine(fall * 1.012, n) + 0.5 * K.sine(fall * 1.5, n)) * np.sin(np.pi * np.minimum(u * 1.02, 1.0)) ** 0.8
    took = [(tone, 0.45, 0.3), (PK.fall(300, 24, 0.6, 114, 0.5, 0.3), D + 0.3, 0.9), (R.unit(K.bell(K.hz(28), K.ns(1.4), 1.8, 0.35, 115)), 0.5, 0.42)]
    return PK.mix(5.2, layers, "cavern" if "cavern" in R.ROOMS else "hall", 0.18, 1.4, 0.3), out_layers(5.2, took)


def step():
    s = 351
    B = 0.24
    n = K.ns(B)
    u = K.tvec(n) / B
    breath = R.unit(K.bandpass(K.noise(n, "pink", s), 1200, 0.8)) * u ** 2.6
    at = B + 0.05
    tear = R.unit(K.highpass(K.lowpass(AA.trim(AA.shift(AA.rec("cloth2"), 0.82), 0.22), 5200, 2), 500, 2))
    c = K.ns(0.8)
    exhale = R.unit(K.bandpass(K.noise(c, "pink", s + 1), 380, 1.0)) * K.perc(c, 0.006, 0.5)
    gust = R.unit(K.bandpass(field("0595", 0.3, 1.3, start=4.0), 1100, 0.7)) * K.perc(K.ns(0.3), 0.002, 0.18)
    layers = [(breath, 0, 0.62), (gust, at, 0.75), (tear, at, 0.8), (snap(0.03, s + 2, 900, 0.02), at, 0.55), (PK.thump(0.3, 104, 42, 0.15, 2.0), at, 0.85), (exhale, at + 0.01, 0.42), (PK.ink(0.6, s + 3, 800), at + 0.03, 0.34)]
    v = K.ns(0.9)
    voice = R.unit(K.lowpass(K.choir(K.hz(45), v, "o", 5, 152, 0.2, 8, 7.0, 0.03), 1300, 2)) * K.perc(v, 0.004, 0.55)
    took = [(voice, at, 0.26), (R.unit(K.glass(K.hz(90), K.ns(0.5), 0.8, 0.4, 154, 1.0)), at, 0.5)]
    return PK.mix(1.7, layers, "small", 0.12, 1.5, 0.3), out_layers(1.7, took)


# ---- out --------------------------------------------------------------------------------------------------------------------

def limit(y, ceil=0.94, attack=0.0012, release=0.09):
    """A brickwall limiter with lookahead, so one hard crack doesn't pull the whole sound down and make it seem quiet."""
    want = np.minimum(1.0, ceil / (np.abs(y) + 1e-9))
    half = K.ns(attack)
    held = np.lib.stride_tricks.sliding_window_view(np.pad(want, half, mode="edge"), half * 2 + 1).min(axis=1)
    g = np.empty_like(held)
    r = np.exp(-1.0 / (release * K.SR))
    cur = 1.0
    for i, v in enumerate(held):
        cur = v if v < cur else v + (cur - v) * r
        g[i] = cur
    n = K.ns(attack) * 2 + 1
    g = np.convolve(g, np.ones(n) / n, "same")
    return y * g


def norm(y, lufs=TARGET):
    """To one loudness, limited rather than scaled down, so every clip is heard at the same level."""
    g = K.dbamp(lufs - K.loudness(K.stereo(y)))
    out = y
    for _ in range(5):
        out = limit(y * g)
        miss = lufs - K.loudness(K.stereo(out))
        if abs(miss) < 0.15 or g > 40:
            break
        g *= K.dbamp(miss)
    return out


def mp3(path, y):
    data = np.clip(y, -1, 1).astype("<f4").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(K.SR), "-ac", "1", "-i", "-", "-b:a", "112k", path], input=data, check=True)


def envelope(y, points=110):
    n = max(1, len(y) // points)
    return [round(float(np.sqrt(np.mean(y[i * n:(i + 1) * n] ** 2))), 4) for i in range(points)]


def stats(y):
    """Seconds sounding (within 30 dB of the loudest moment), and shares of energy below 200 Hz and above 5 kHz."""
    hop = K.ns(0.02)
    rms = np.array([np.sqrt(np.mean(y[i:i + hop] ** 2)) for i in range(0, max(1, len(y) - hop), hop)])
    secs = float(np.sum(rms > np.max(rms) * 10 ** (-30 / 20)) * 0.02)
    P2 = np.abs(np.fft.rfft(y)) ** 2
    f = np.fft.rfftfreq(len(y), 1.0 / K.SR)
    tot = np.sum(P2) + 1e-12
    return round(secs, 1), int(round(100 * np.sum(P2[f < 200]) / tot)), round(float(100 * np.sum(P2[f > 5000]) / tot), 1)


def main():
    import ding_check as D
    sets = {"haymaker": (today_haymaker, haymaker), "railgun": (today_railgun, railgun), "gravity_well": (today_well, well), "shadow_step": (today_step, step)}
    out = {}
    for name, (old, new) in sets.items():
        before = old()
        native = float(K.loudness(K.stereo(before)))
        made, took = new()
        rows = {"today": norm(before), "new": norm(made), "out": norm(took)}
        out[name] = {"today_native_lufs": round(native, 1)}
        for k, y in rows.items():
            path = os.path.join(OUT, f"{name}_{k}.mp3")
            mp3(path, y)
            d = D.check(D.decode(path))
            out[name][k] = {"seconds": round(len(y) / K.SR, 2), "lufs": round(float(K.loudness(K.stereo(y))), 1), "peak": round(float(np.max(np.abs(y))), 3), "env": envelope(y), "stats": stats(y), "ding": d}
        print(name, native, {k: (out[name][k]["lufs"], out[name][k]["stats"], out[name][k]["ding"]["ding"] and out[name][k]["ding"]["hz"]) for k in rows})
    json.dump(out, open(os.path.join(OUT, "levels.json"), "w"))


if __name__ == "__main__":
    main()
