"""The family kits of the powers' sounds (docs/power-anims/PLAN.md section 5.6, phase P5): one authored sound for every
`power.*` event the catalog plays (they were vanilla remixes), built from a few shared voices in each family's timbre:
Kinetic is weight (recorded impacts, sub, cracked stone, rubble), Velocity air, Flight and gravity low pulses and falling tones,
Energy rising light and shimmer, Shadow ink and breath, Mind soft pads and pings, Architect set stone and a tick of chime, Tech
clicks, servos and lasers, Spirit bells and breath. audio_powers.py renders and masters them like the first five events.

Each event function takes a seed and returns mono audio. Voices live here; recipes are compositions of 2-4 voices on a bus.
"""
import numpy as np

import audiokit as K
import ritual_sounds as R
import audio_arms as AA


# ---- voices -------------------------------------------------------------------------------------------------------------

def whoosh(dur, lo, hi, peak, seed, q=1.6):
    """Air past the ear: a band of noise whose centre rises to the pass (at `peak` of the length) and falls, its level the same."""
    n = K.ns(dur)
    t = K.tvec(n)
    u = t / dur
    centre = lambda s: lo + (hi - lo) * np.exp(-((s / dur - peak) ** 2) / 0.08)
    env = np.exp(-((u - peak) ** 2) / 0.07)
    return R.unit(R.sweep(K.noise(n, "pink", seed), centre, q)) * env


def thump(dur, f0, f1, t60, drive=1.8):
    return AA.sub(dur, f0, f1, t60, drive)


def punch(seed, k=0.7, heavy=True, dur=0.4):
    r = K.rng(seed)
    nm = ["impactPunch_heavy_001", "impactPunch_heavy_002", "impactPunch_heavy_003", "impactPunch_heavy_004"] if heavy else \
        ["impactPunch_medium_000", "impactPunch_medium_001", "impactPunch_medium_003"]
    return AA.trim(AA.shift(AA.rec(AA.pick(nm, r)), k * r.uniform(0.95, 1.05)), dur)


def body(seed, k=0.65, fc=700, dur=0.4):
    r = K.rng(seed)
    nm = ["impactSoft_heavy_000", "impactSoft_heavy_002", "impactSoft_heavy_004"]
    return AA.trim(K.lowpass(AA.shift(AA.rec(AA.pick(nm, r)), k * r.uniform(0.95, 1.05)), fc, 2), dur)


def metal(seed, k=1.0, dur=0.5, kind="heavy"):
    r = K.rng(seed)
    nm = {"heavy": ["impactMetal_heavy_000", "impactMetal_heavy_001", "impactMetal_heavy_003"],
          "light": ["impactMetal_light_001", "impactMetal_light_002", "impactMetal_light_004"]}[kind]
    return AA.trim(AA.shift(AA.rec(AA.pick(nm, r)), k * r.uniform(0.95, 1.05)), dur)


def glassy(seed, k=1.0, dur=0.6):
    r = K.rng(seed)
    return AA.trim(AA.shift(AA.rec(AA.pick(["impactGlass_heavy_000", "impactGlass_heavy_001", "impactGlass_heavy_004"], r)), k), dur)


def rubble(seed, dur=0.5, rate=90.0):
    return R.unit(K.highpass(K.crackle(K.ns(dur), rate, seed, 0.9, 0.8), 900, 1)) * K.perc(K.ns(dur), 0.01, dur * 0.7)


def rumble(dur, fc, t60, seed):
    n = K.ns(dur)
    return R.unit(K.lowpass(K.noise(n, "pink", seed), fc, 2)) * K.perc(n, 0.02, t60)


def rise(f0, f1, dur, seed, harm=0.5, curve=1.3):
    n = K.ns(dur)
    t = K.tvec(n)
    f = f0 * np.exp(np.log(f1 / f0) * np.minimum(1.0, t / dur) ** curve)
    return R.unit(K.sine(f, n) + harm * K.sine(f * 2.01, n)) * (t / t[-1]) ** 1.2


def fall(f0, f1, dur, seed, harm=0.4, tau=0.18):
    n = K.ns(dur)
    t = K.tvec(n)
    f = f1 + (f0 - f1) * np.exp(-t / (dur * tau * 3))
    return R.unit(K.sine(f, n) + harm * K.sine(f * 2.0, n)) * K.perc(n, 0.004, dur * 0.6)


def air_burst(dur, fc, seed, q=1.2):
    n = K.ns(dur)
    return R.unit(K.bandpass(K.noise(n, "white", seed), fc, q)) * K.perc(n, 0.003, dur * 0.5)


def shimmer(f0, dur, seed, t60=1.2, bright=0.8):
    return R.unit(K.glass(f0, K.ns(dur), bright, t60, seed, 0.9))


def bell(f0, dur, seed, t60=1.6, bright=0.6):
    return R.unit(K.bell(f0, K.ns(dur), t60, bright, seed))


def zap(dur, seed, hp=2200):
    n = K.ns(dur)
    t = K.tvec(n)
    buzz = R.unit(K.sine(60 + 40 * np.sin(t * 180), n) * np.sign(np.sin(2 * np.pi * 110 * t)))
    return (R.unit(K.highpass(K.crackle(n, 260.0, seed, 0.95, 0.85), hp, 2)) * 0.8 + 0.25 * buzz) * K.perc(n, 0.0008, dur * 0.55)


def ink(dur, seed, fc=600):
    n = K.ns(dur)
    t = K.tvec(n)
    return R.unit(K.lowpass(K.noise(n, "pink", seed), fc, 2)) * np.sin(np.pi * np.minimum(1.0, t / dur)) ** 1.5


def pad(f0, dur, seed, detune=0.006, attack=0.25):
    n = K.ns(dur)
    t = K.tvec(n)
    x = np.zeros(n)
    for m, a in ((1, 1.0), (2, 0.5), (3, 0.3)):
        for d in (-detune, 0, detune):
            x += a * K.sine(f0 * m * (1 + d), n)
    return R.unit(x) * np.minimum(1.0, t / attack) * np.minimum(1.0, (t[-1] - t) / (dur * 0.5))


def click(dur, seed, fc=3200):
    n = K.ns(dur)
    return R.unit(K.bandpass(K.noise(n, "white", seed), fc, 3)) * K.perc(n, 0.0004, dur * 0.4)


def servo(dur, f0, f1, seed):
    n = K.ns(dur)
    t = K.tvec(n)
    f = f0 + (f1 - f0) * t / t[-1]
    return R.unit(K.sine(f, n) * np.sign(np.sin(2 * np.pi * f * 0.5 * t)) * 0.5 + K.sine(f * 2, n) * 0.3) * np.minimum(1.0, t / 0.03) * np.minimum(1.0, (t[-1] - t) / 0.05)


def cloth_rustle(seed, dur=0.3):
    r = K.rng(seed)
    return AA.cloth(dur, seed, r)


def mix(dur, layers, room="field", wet=0.15, drive=1.6, tail=0.2):
    """Stacks (signal, at seconds, gain) glued together on one bus in a room (audio_arms._blow's job for non-blows)."""
    hit = np.zeros(K.ns(dur))
    for sig, t, g in layers:
        R.put(hit, sig, t, g)
    b = R.Bus(dur + tail)
    b.add(R.glue(hit, drive), 0.0, 1.0, room, wet, hp=90)
    return R.fade_out(b.render(), min(0.2, dur * 0.2))


# ---- events: (function, variants, rms, attenuation blocks, crest) ----------------------------------------------------------------

def ev_slam(s):
    return mix(1.3, [(punch(s, 0.62), 0, 1.0), (body(s + 1, 0.6), 0.002, 0.9), (R.crack(s + 3, 0.55, 0.3), 0.003, 0.5), (thump(0.55, 80, 32, 0.3, 2.0), 0, 1.0),
                     (rubble(s + 7, 0.5), 0.07, 0.3), (rumble(0.9, 130, 0.5, s + 9), 0.02, 0.35)], "stone", 0.22, 2.0, 0.35)


def ev_whoosh(s):
    return mix(0.5, [(whoosh(0.45, 500, 2600, 0.5, s), 0, 0.9), (air_burst(0.12, 3800, s + 1), 0.18, 0.25)], "field", 0.1, 1.2)


def ev_punch(s):
    return mix(0.5, [(punch(s, 0.8, True, 0.35), 0, 1.0), (whoosh(0.18, 700, 2400, 0.8, s + 2), 0, 0.4), (thump(0.2, 110, 50, 0.1, 1.6), 0, 0.7)], "field", 0.14, 1.7)


def ev_charge_up(s):
    return mix(0.95, [(rise(140, 900, 0.9, s, 0.6), 0, 0.7), (R.unit(R.sweep(K.noise(K.ns(0.9), "pink", s + 1), lambda t: 250 + 2800 * min(1, t / 0.9), 2.0)) * np.linspace(0, 1, K.ns(0.9)) ** 1.5, 0, 0.5)], "field", 0.12, 1.2)


def ev_rip(s):
    return mix(0.9, [(rubble(s, 0.7, 70), 0, 0.8), (R.crack(s + 2, 0.6, 0.3), 0.01, 0.6), (rumble(0.8, 160, 0.4, s + 3), 0, 0.6), (body(s + 4, 0.7, 500, 0.4), 0.02, 0.6)], "stone", 0.18, 1.8)


def ev_impact(s):
    return mix(0.7, [(punch(s, 0.8, True, 0.4), 0, 1.0), (body(s + 1, 0.8, 800, 0.3), 0.002, 0.7), (R.crack(s + 2, 0.5, 0.2), 0.004, 0.3), (thump(0.3, 110, 45, 0.15, 1.8), 0, 0.8)], "field", 0.16, 1.8)


def ev_shockwave(s):
    return mix(0.9, [(whoosh(0.5, 250, 1500, 0.35, s), 0, 0.6), (thump(0.5, 70, 32, 0.28, 2.0), 0.05, 1.0), (rumble(0.7, 120, 0.4, s + 2), 0.05, 0.5), (rubble(s + 3, 0.4), 0.12, 0.3)], "stone", 0.2, 1.9)


def ev_brace(s):
    return mix(0.8, [(metal(s, 0.9, 0.5), 0, 0.9), (thump(0.3, 95, 45, 0.15, 1.6), 0, 0.7), (cloth_rustle(s + 2, 0.25), 0, 0.4)], "field", 0.12, 1.6)


def ev_thunder(s):
    return mix(1.8, [(R.crack(s, 1.0, 0.4), 0, 1.0), (punch(s + 1, 0.6), 0.002, 0.8), (thump(0.8, 70, 28, 0.4, 2.0), 0, 1.0), (rumble(1.6, 110, 0.8, s + 3), 0.05, 0.6), (zap(0.25, s + 4), 0, 0.5)], "stone", 0.3, 2.0, 0.5)


def ev_zap(s):
    return mix(0.5, [(zap(0.35, s), 0, 1.0), (air_burst(0.1, 5000, s + 1), 0, 0.3), (thump(0.15, 120, 60, 0.06, 1.5), 0, 0.4)], "field", 0.1, 1.5)


def ev_flow_start(s):
    return mix(1.1, [(fall(900, 120, 0.9, s), 0, 0.7), (thump(0.6, 70, 40, 0.3, 1.4), 0, 0.7), (shimmer(1700, 0.9, s + 2, 1.0), 0.05, 0.4), (whoosh(0.8, 200, 900, 0.2, s + 3), 0, 0.4)], "field", 0.2, 1.3)


def ev_flow_end(s):
    return mix(0.8, [(rise(150, 1100, 0.45, s, 0.4), 0, 0.5), (whoosh(0.5, 400, 2500, 0.7, s + 1), 0.1, 0.7), (air_burst(0.1, 4000, s + 2), 0.45, 0.3)], "field", 0.12, 1.3)


def ev_hover(s):
    return mix(0.7, [(whoosh(0.6, 200, 700, 0.5, s), 0, 0.6), (pad(110, 0.6, s + 1, 0.004, 0.1), 0, 0.4)], "field", 0.1, 1.2)


def ev_gravity(s):
    return mix(1.0, [(thump(0.7, 62, 32, 0.4, 1.8), 0, 1.0), (fall(380, 90, 0.8, s), 0, 0.5), (rumble(0.9, 100, 0.5, s + 2), 0, 0.4)], "field", 0.2, 1.5)


def ev_throw(s):
    return mix(0.6, [(whoosh(0.4, 400, 2200, 0.3, s), 0, 0.9), (thump(0.2, 100, 50, 0.1, 1.5), 0.02, 0.6)], "field", 0.1, 1.4)


def ev_beam(s):
    return mix(0.62, [(pad(98, 0.62, s, 0.004, 0.07), 0, 0.7), (air_burst(0.5, 1800, s + 1, 1.2), 0, 0.3), (zap(0.3, s + 2, 3200), 0.1, 0.2)], "field", 0.1, 1.2)


def ev_nova(s):
    return mix(1.4, [(rise(200, 1300, 0.3, s, 0.5), 0, 0.5), (thump(0.8, 85, 32, 0.4, 2.0), 0.25, 1.0), (shimmer(1500, 1.0, s + 2, 1.2), 0.25, 0.6), (whoosh(0.8, 300, 3000, 0.3, s + 3), 0.25, 0.7),
                     (rumble(1.0, 140, 0.5, s + 4), 0.25, 0.4)], "stone", 0.2, 1.8)


def ev_barrier(s):
    return mix(0.9, [(shimmer(1100, 0.8, s, 1.0), 0, 0.6), (shimmer(1650, 0.7, s + 1, 0.9), 0.04, 0.4), (whoosh(0.4, 400, 1500, 0.4, s + 2), 0, 0.5), (thump(0.25, 140, 70, 0.12, 1.4), 0, 0.5)], "field", 0.18, 1.3)


def ev_barrier_break(s):
    return mix(0.9, [(glassy(s, 1.0, 0.7), 0, 1.0), (shimmer(2200, 0.7, s + 1, 0.8), 0.02, 0.5), (rubble(s + 2, 0.4, 160), 0.03, 0.3)], "field", 0.15, 1.5)


def ev_shot(s):
    return mix(0.6, [(zap(0.3, s), 0, 0.9), (whoosh(0.35, 700, 3200, 0.3, s + 1), 0, 0.7), (rise(300, 1800, 0.15, s + 2, 0.3), 0, 0.3)], "field", 0.1, 1.5)


def ev_overload(s):
    return mix(1.9, [(rise(90, 700, 0.7, s, 0.7, 1.6), 0, 0.7), (zap(0.7, s + 1, 1800), 0.1, 0.5), (thump(1.0, 70, 26, 0.5, 2.2), 0.7, 1.0), (shimmer(900, 1.1, s + 2, 1.5), 0.7, 0.6),
                     (rumble(1.1, 120, 0.6, s + 3), 0.7, 0.5), (whoosh(0.9, 200, 3500, 0.3, s + 4), 0.7, 0.6)], "stone", 0.25, 2.0, 0.4)


def ev_shadow(s):
    return mix(0.7, [(ink(0.65, s, 700), 0, 0.8), (whoosh(0.4, 200, 800, 0.4, s + 1), 0, 0.5), (cloth_rustle(s + 2, 0.3), 0.02, 0.3)], "field", 0.14, 1.2)


def ev_cloak(s):
    return mix(0.8, [(cloth_rustle(s, 0.35), 0, 0.7), (fall(500, 110, 0.6, s + 1, 0.2), 0.05, 0.4), (ink(0.6, s + 2, 500), 0.05, 0.6)], "field", 0.12, 1.2)


def ev_summon(s):
    return mix(1.4, [(pad(73, 1.3, s, 0.008, 0.5), 0, 0.7), (ink(1.2, s + 1, 450), 0, 0.6), (thump(0.6, 60, 30, 0.35, 1.5), 0.3, 0.6), (shimmer(520, 1.0, s + 2, 1.4, 0.5), 0.4, 0.3)], "stone", 0.25, 1.4, 0.4)


def ev_mind(s):
    return mix(0.9, [(pad(330, 0.8, s, 0.005, 0.12), 0, 0.5), (shimmer(1760, 0.7, s + 1, 1.0), 0.06, 0.5), (bell(660, 0.7, s + 2, 1.1, 0.4), 0.0, 0.3)], "field", 0.2, 1.2)


def ev_pop(s):
    return mix(0.4, [(air_burst(0.1, 1800, s), 0, 0.8), (shimmer(2400, 0.3, s + 1, 0.4), 0.0, 0.4), (thump(0.12, 160, 80, 0.05, 1.3), 0, 0.5)], "field", 0.08, 1.4)


def ev_build(s):
    return mix(0.7, [(body(s, 1.0, 1500, 0.3), 0, 0.8), (click(0.04, s + 1, 2400), 0, 0.5), (bell(1320, 0.5, s + 2, 0.6, 0.5), 0.02, 0.4), (thump(0.2, 130, 70, 0.1, 1.4), 0, 0.5)], "field", 0.14, 1.5)


def ev_collapse(s):
    return mix(1.6, [(punch(s, 0.55), 0, 1.0), (rubble(s + 1, 1.0, 120), 0.04, 0.7), (thump(0.8, 70, 28, 0.45, 2.0), 0, 1.0), (rumble(1.4, 120, 0.7, s + 2), 0, 0.6), (R.crack(s + 3, 0.7, 0.3), 0.01, 0.5)], "stone", 0.3, 2.0, 0.4)


def ev_construct_fade(s):
    return mix(0.8, [(shimmer(1900, 0.7, s, 0.8), 0, 0.6), (whoosh(0.5, 900, 3000, 0.7, s + 1), 0, 0.4)], "field", 0.15, 1.2)


def ev_grapple(s):
    return mix(0.6, [(whoosh(0.25, 800, 3500, 0.4, s), 0, 0.8), (metal(s + 1, 1.3, 0.3, "light"), 0.12, 0.7), (click(0.03, s + 2, 4200), 0, 0.5)], "field", 0.1, 1.5)


def ev_jet(s):
    return mix(0.8, [(whoosh(0.7, 400, 2200, 0.25, s, 1.0), 0, 1.0), (rumble(0.6, 400, 0.4, s + 1), 0, 0.5), (zap(0.15, s + 2, 4000), 0, 0.3)], "field", 0.1, 1.4)


def ev_drone_shot(s):
    return mix(0.45, [(zap(0.2, s, 3400), 0, 0.8), (rise(900, 2600, 0.12, s + 1, 0.2), 0, 0.4), (click(0.02, s + 2, 5000), 0, 0.4)], "field", 0.08, 1.4)


def ev_rail(s):
    return mix(1.5, [(R.crack(s, 1.2, 0.35), 0, 1.0), (thump(0.9, 90, 28, 0.5, 2.2), 0, 1.0), (zap(0.3, s + 1, 1800), 0, 0.7), (metal(s + 2, 0.8, 0.8), 0.01, 0.6), (bell(880, 1.2, s + 3, 1.4, 0.5), 0.02, 0.3),
                     (rumble(1.0, 140, 0.5, s + 4), 0, 0.4)], "stone", 0.2, 2.0, 0.4)


def ev_nanite(s):
    return mix(0.9, [(R.unit(K.highpass(K.crackle(K.ns(0.8), 140.0, s, 0.9, 0.9), 2000, 1)) * K.perc(K.ns(0.8), 0.02, 0.6), 0, 0.7), (shimmer(2600, 0.7, s + 1, 0.9), 0.05, 0.4),
                     (metal(s + 2, 1.5, 0.3, "light"), 0, 0.3)], "field", 0.1, 1.3)


def ev_spirit(s):
    return mix(1.1, [(bell(528, 1.0, s, 1.4, 0.5), 0, 0.5), (bell(792, 0.9, s + 1, 1.2, 0.4), 0.06, 0.4), (ink(0.9, s + 2, 1500), 0, 0.3), (shimmer(2100, 0.8, s + 3, 1.0), 0.1, 0.3)], "field", 0.22, 1.2)


def ev_heal(s):
    return mix(1.2, [(bell(660, 1.0, s, 1.5, 0.5), 0, 0.5), (bell(990, 1.0, s + 1, 1.4, 0.4), 0.12, 0.45), (bell(1320, 0.9, s + 2, 1.3, 0.4), 0.24, 0.4), (pad(220, 1.0, s + 3, 0.004, 0.3), 0, 0.3)], "field", 0.22, 1.2)


def ev_eye(s):
    return mix(0.9, [(pad(293, 0.8, s, 0.005, 0.2), 0, 0.5), (bell(1174, 0.7, s + 1, 1.0, 0.4), 0.15, 0.5), (air_burst(0.15, 2500, s + 2), 0.05, 0.2)], "field", 0.2, 1.2)


def ev_lash(s):
    return mix(0.5, [(R.crack(s, 0.35, 0.15), 0.12, 0.9), (whoosh(0.25, 800, 3800, 0.5, s + 1), 0, 0.8), (bell(1580, 0.3, s + 2, 0.4, 0.5), 0.12, 0.25)], "field", 0.1, 1.6)


def ev_ward(s):
    return mix(1.1, [(pad(196, 1.0, s, 0.006, 0.2), 0, 0.6), (bell(392, 1.0, s + 1, 1.5, 0.5), 0.02, 0.45), (thump(0.5, 70, 38, 0.3, 1.4), 0, 0.7), (shimmer(1568, 0.8, s + 2, 1.0), 0.05, 0.3)], "stone", 0.22, 1.3, 0.3)


def ev_void(s):
    return mix(1.6, [(fall(520, 40, 1.4, s, 0.4, 0.3), 0, 0.8), (thump(1.0, 55, 24, 0.6, 2.0), 0.2, 1.0), (whoosh(1.2, 100, 600, 0.15, s + 1), 0, 0.6), (ink(1.2, s + 2, 300), 0, 0.5)], "stone", 0.25, 1.8, 0.4)


def ev_dream(s):
    return mix(1.1, [(pad(262, 1.0, s, 0.007, 0.3), 0, 0.5), (shimmer(1568, 0.9, s + 1, 1.3), 0.05, 0.4), (shimmer(2093, 0.8, s + 2, 1.2), 0.2, 0.3), (whoosh(0.8, 300, 1400, 0.6, s + 3), 0, 0.3)], "field", 0.25, 1.1)


def ev_frost(s):
    return mix(1.0, [(glassy(s, 1.2, 0.6), 0, 0.8), (shimmer(2800, 0.9, s + 1, 1.2), 0.02, 0.5), (rubble(s + 2, 0.4, 200), 0.03, 0.3), (thump(0.3, 90, 50, 0.15, 1.4), 0, 0.5), (whoosh(0.5, 900, 3500, 0.4, s + 3), 0, 0.3)], "field", 0.18, 1.5)


def ev_ember(s):
    return mix(0.8, [(whoosh(0.5, 300, 1800, 0.35, s), 0, 0.7), (R.unit(K.highpass(K.crackle(K.ns(0.7), 110.0, s + 1, 0.8, 0.8), 1500, 1)) * K.perc(K.ns(0.7), 0.01, 0.5), 0, 0.6), (thump(0.2, 100, 50, 0.1, 1.4), 0, 0.5)], "field", 0.1, 1.4)


def ev_blast(s):
    return mix(1.5, [(thump(0.9, 80, 28, 0.45, 2.2), 0, 1.0), (punch(s, 0.6), 0, 0.8), (whoosh(0.7, 200, 3000, 0.2, s + 1), 0, 0.7), (rubble(s + 2, 0.7, 150), 0.03, 0.4), (rumble(1.2, 120, 0.6, s + 3), 0, 0.5)], "stone", 0.25, 2.0, 0.4)


def ev_stun(s):
    return mix(0.5, [(zap(0.3, s, 2800), 0, 0.9), (bell(1250, 0.3, s + 1, 0.5, 0.6), 0, 0.3), (thump(0.15, 120, 60, 0.07, 1.4), 0, 0.5)], "field", 0.1, 1.5)


def ev_shatter(s):
    return mix(0.8, [(glassy(s, 1.1, 0.6), 0, 1.0), (shimmer(3000, 0.6, s + 1, 0.7), 0.01, 0.4)], "field", 0.12, 1.5)


def ev_steam(s):
    return mix(0.9, [(air_burst(0.8, 3800, s, 0.8), 0, 0.8), (whoosh(0.7, 800, 3200, 0.2, s + 1), 0, 0.4)], "field", 0.1, 1.1)


# ---- Charged Shot's lightning (SPEC 32): one sound for each moment of the cast -------------------------------------------------

def _ramp(n, at, to, curve=1.0):
    """0 before `at` seconds, rising to 1 at `to`, held after."""
    t = K.tvec(n)
    return np.clip((t - at) / max(1e-6, to - at), 0, 1) ** curve


def soft(x, crest=10.0):
    """A sparse layer (a crackle, a crack) at unit RMS with its spikes rounded off at `crest` dB, so one spark cannot set the
    whole sound's level."""
    c = 10 ** (crest / 20)
    return c * np.tanh(R.unit(x) / c)


def arc_buzz(dur, f0, f1, seed, curve=1.6):
    """An arc's mains buzz: a gliding square-ish tone (odd harmonics), wobbling, as a held spark does."""
    n = K.ns(dur)
    t = K.tvec(n)
    f = f0 * np.exp(np.log(f1 / f0) * np.minimum(1.0, t / dur) ** curve) * (1 + 0.012 * K.smooth_noise(n, 9.0, seed))
    x = sum(K.sine(f * m, n) / m for m in (1, 3, 5, 7, 9))
    return R.unit(K.lowpass(x, 3200, 2))


def ev_lightning_charge(s):
    """The ball builds between the hands (1.5 s to full, then held): a hum that climbs and swells, a whine rising over it, and
    the crackle going from a few sparks to a storm. HoldSound plays it from the hands and cuts it on the let-go."""
    dur = 1.8
    n = K.ns(dur)
    t = K.tvec(n)
    grow = np.minimum(1.0, t / 1.5)
    hum = arc_buzz(dur, 62, 128, s, 1.4) * (0.25 + 0.75 * grow ** 1.4)
    whine = R.unit(K.sine(900 * np.exp(np.log(3.4) * grow ** 1.6), n) + 0.3 * K.sine(1810 * np.exp(np.log(3.4) * grow ** 1.6), n)) * grow ** 2.2
    sparks = soft(K.crackle(n, 22.0, s + 1, 0.85, 0.9), 9.0) * (0.5 + 0.5 * grow)
    storm = soft(K.crackle(n, 260.0, s + 2, 0.9, 0.6), 9.0) * _ramp(n, 0.5, 1.5, 1.8)
    air = R.unit(R.sweep(K.noise(n, "pink", s + 3), lambda u: 300 + 2600 * min(1.0, max(0.0, u) / 1.5) ** 1.5, 2.2)) * grow ** 1.5
    sub = AA.sub(dur, 40, 58, dur, 1.3) * _ramp(n, 0.2, 1.5, 1.2)
    x = hum * 0.8 + whine * 0.16 + sparks * 0.45 + storm * 0.55 + air * 0.3 + sub * 0.5
    x *= np.minimum(1.0, t / 0.04)
    return R.fade_out(x, 0.12)


def ev_lightning_full(s):
    """Full: a bright ring with a snap in it and the hum lifting under it, so you know without looking."""
    return mix(0.75, [(shimmer(2350, 0.7, s, 0.6, 1.0), 0.0, 0.7), (bell(1175, 0.7, s + 1, 0.9, 0.7), 0.0, 0.4),
                      (zap(0.18, s + 2, 3000), 0.0, 0.8), (rise(160, 420, 0.25, s + 3, 0.5), 0.0, 0.35),
                      (thump(0.25, 90, 55, 0.12, 1.4), 0.0, 0.35)], "field", 0.18, 1.4, 0.3)


def ev_lightning_release(s):
    """The let-go: the crack of the bolt leaving (a split-second snap, the air torn), the hands' push in a thump, a zap off the
    ball and the bolt's hiss going away."""
    return mix(0.9, [(R.crack(s, 1.1, 0.25), 0.0, 1.0), (air_burst(0.12, 5200, s + 1, 0.9), 0.0, 0.8), (zap(0.3, s + 2, 1800), 0.004, 0.7),
                     (punch(s + 3, 0.95, False, 0.25), 0.0, 0.45), (thump(0.3, 120, 45, 0.14, 1.8), 0.0, 0.8),
                     (whoosh(0.6, 900, 4200, 0.12, s + 4, 1.2), 0.01, 0.55)], "field", 0.14, 2.0, 0.35)


def ev_lightning_boom(s):
    """Under a strong release: the boom the air makes when it closes behind the bolt, felt more than heard."""
    return mix(1.6, [(thump(1.0, 64, 24, 0.55, 2.2), 0.0, 1.0), (rumble(1.5, 140, 0.9, s), 0.02, 0.7),
                     (rumble(1.2, 420, 0.4, s + 1), 0.0, 0.25), (R.crack(s + 2, 0.7, 0.2), 0.0, 0.3)], "stone", 0.32, 2.2, 0.5)


def ev_lightning_sizzle(s):
    """The bolt going by: a frying hiss and a buzz, close and gone (the bolt plays one every 3 ticks along its flight)."""
    n = K.ns(0.32)
    t = K.tvec(n)
    hiss = R.unit(K.bandpass(K.noise(n, "white", s), 4200, 0.8)) * (0.6 + 0.4 * K.smooth_noise(n, 40.0, s + 1))
    fry = R.unit(K.crackle(n, 420.0, s + 2, 0.95, 0.4))
    buzz = arc_buzz(0.32, 150, 120, s + 3, 1.0)
    env = np.sin(np.pi * np.minimum(1.0, t / 0.32)) ** 0.8
    return (hiss * 0.5 + fry * 0.6 + buzz * 0.35) * env


def ev_lightning_thunder(s):
    """Where it lands: the strike's crack, then thunder rolling away in two or three swells."""
    r = K.rng(s)
    layers = [(R.crack(s, 1.2, 0.35), 0.0, 1.0), (air_burst(0.15, 4500, s + 1), 0.0, 0.4), (punch(s + 2, 0.55, True, 0.4), 0.004, 0.7),
              (thump(0.9, 62, 26, 0.45, 2.2), 0.0, 1.0)]
    at = 0.12
    for k in range(int(r.integers(2, 4))):
        layers.append((rumble(1.4 - 0.25 * k, 160 - 30 * k, 0.6, s + 3 + k), at, 0.75 - 0.18 * k))
        at += float(r.uniform(0.25, 0.45))
    return mix(2.4, layers, "stone", 0.35, 2.0, 0.5)


# event: (function, variants, rms dBFS, attenuation blocks, crest dB)
KIT = {
    "slam": (ev_slam, 3, -13.0, 32, 12.0), "whoosh": (ev_whoosh, 3, -19.0, 18, 12.0), "punch": (ev_punch, 3, -15.0, 22, 12.0),
    "charge_up": (ev_charge_up, 2, -19.0, 20, 12.0), "rip": (ev_rip, 2, -20.5, 24, 12.0), "impact": (ev_impact, 3, -15.0, 24, 12.0),
    "shockwave": (ev_shockwave, 2, -15.0, 28, 12.0), "brace": (ev_brace, 2, -16.0, 22, 12.0), "thunder": (ev_thunder, 2, -15.5, 48, 12.0),
    "zap": (ev_zap, 3, -17.5, 28, 12.0), "flow_start": (ev_flow_start, 2, -18.0, 24, 12.0), "flow_end": (ev_flow_end, 2, -19.0, 24, 12.0),
    "hover": (ev_hover, 2, -21.0, 18, 12.0), "gravity": (ev_gravity, 2, -17.0, 26, 12.0), "throw": (ev_throw, 2, -18.0, 22, 12.0),
    "beam": (ev_beam, 2, -19.0, 22, 14.0), "nova": (ev_nova, 2, -14.0, 36, 12.0), "barrier": (ev_barrier, 2, -18.0, 22, 12.0),
    "barrier_break": (ev_barrier_break, 2, -15.0, 26, 12.0), "shot": (ev_shot, 3, -16.0, 26, 12.0), "overload": (ev_overload, 2, -13.0, 44, 12.0),
    "shadow": (ev_shadow, 2, -20.0, 20, 12.0), "cloak": (ev_cloak, 2, -21.0, 18, 12.0), "summon": (ev_summon, 2, -17.0, 26, 12.0),
    "mind": (ev_mind, 2, -21.0, 18, 12.0), "pop": (ev_pop, 2, -19.0, 18, 12.0), "build": (ev_build, 3, -17.0, 22, 12.0),
    "collapse": (ev_collapse, 2, -16.0, 36, 12.0), "construct_fade": (ev_construct_fade, 2, -20.0, 20, 12.0), "grapple": (ev_grapple, 2, -17.0, 24, 12.0),
    "jet": (ev_jet, 2, -17.0, 24, 12.0), "drone_shot": (ev_drone_shot, 3, -18.0, 24, 12.0), "rail": (ev_rail, 2, -12.0, 56, 12.0),
    "nanite": (ev_nanite, 2, -20.0, 20, 12.0), "spirit": (ev_spirit, 2, -19.0, 22, 12.0), "heal": (ev_heal, 2, -19.0, 22, 12.0),
    "eye": (ev_eye, 2, -20.0, 20, 12.0), "lash": (ev_lash, 2, -17.0, 24, 12.0), "ward": (ev_ward, 2, -18.0, 24, 12.0),
    "void": (ev_void, 2, -14.0, 36, 12.0), "dream": (ev_dream, 2, -21.0, 20, 12.0), "frost": (ev_frost, 2, -16.0, 26, 12.0),
    "ember": (ev_ember, 2, -18.0, 22, 12.0), "blast": (ev_blast, 2, -15.5, 40, 12.0), "stun": (ev_stun, 2, -17.0, 22, 12.0),
    "shatter": (ev_shatter, 2, -16.0, 24, 12.0), "steam": (ev_steam, 2, -20.0, 22, 12.0),
    "lightning_charge": (ev_lightning_charge, 2, -20.0, 20, 16.0), "lightning_full": (ev_lightning_full, 2, -17.0, 20, 12.0),
    "lightning_release": (ev_lightning_release, 3, -15.5, 32, 12.0), "lightning_boom": (ev_lightning_boom, 2, -14.0, 40, 12.0),
    "lightning_sizzle": (ev_lightning_sizzle, 3, -20.0, 16, 12.0), "lightning_thunder": (ev_lightning_thunder, 3, -15.5, 56, 12.0),
}
