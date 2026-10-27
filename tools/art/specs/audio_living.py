"""The Living Wilds' sounds (SPEC §21): the wrist band reading creatures, the glyph of a critical hit, XP arriving, the
level-up and the caption of a named region. Rendered by tools/art/audio_build.py like the Holo UI's and in the same one
voice: the System's struck glass with its warm core and faint glint (audio_ui.glass_note), in its small bright room (the
big one for the two long moments), every note from D major pentatonic (D E F# A B) so a living sound never clashes with
a toast, a card or a holo panel. UI loudness (-24 +- 3 dBFS RMS, true peak <= -3); the ones that repeat sit at the
quiet end of it.

  ui_living_scan          the band reads a creature (plays often, so the gentlest of them): one very soft glass tick (E6),
                          a faint B6 glint just behind it and a thread of air gliding up as the scan passes, ~0.25 s
  ui_living_crit          a critical hit's glyph: a bright crack of glass (a hard air tick and a short crackle) with F#6,
                          A6 and D7 breaking out of it in a quick rise, then a thin sparkle falling away, ~0.35 s
  ui_living_xp_tick       one XP mote arriving in the band: a tiny bright chime (A6) with a faint D7 above it, ~0.15 s;
                          it can sound several times in a second, so it is soft and dry
  ui_living_level_up      the level-up moment, ~2.4 s: D5 F#5 A5 D6 E6 climbing and growing, then swelling into a held
                          open fifth (D-A) under a soft shimmer of high glass and an air swell; it peaks around 0.75 s
                          and has gone by 2.4 s. Majestic and restrained: no rank-up bloom, no bass
  amb_living_dread        the bed under a land far above you (decision 11 of SPEC 21.10): 8 s, loops, low and cold, felt more than
                          heard; DreadFx plays it looping at the land's weight
  ui_living_region_enter  entering a named region (the place caption): a low, wide, calm two-note glass chime, D4 then A4
                          (one from each side), a faint low D3 bloom and a long soft tail, ~1.5 s

    python3 tools/art/audio_build.py ui_living        render these five files (and write their events)
"""
import os
import sys

import numpy as np

import audiokit as K

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import audio_ui as U  # noqa: E402  the System's glass voice: one note, its room, its air
from audio_holo import sweep  # noqa: E402  air through a band that glides: light moving


def at(n, t, seg):
    """Places a mono seg into a mono buffer of n samples at t seconds."""
    out = np.zeros(n)
    i = K.ns(t)
    if i < n:
        m = min(len(seg), n - i)
        out[i:i + m] = seg[:m]
    return out


def grain(n, t, fc, dur, level, q=1.2, seed=0, decay=0.4):
    """A tiny burst of band noise (a glass edge or a spark): `dur` seconds, its tail `decay` of that."""
    k = K.ns(dur)
    return at(n, t, K.bandnoise(k, fc, q, seed) * K.perc(k, 0.0004, dur * decay)) * level


# ---- the sounds --------------------------------------------------------------------------------------------------

def scan(seed):
    L = 0.25
    y = U.notes([(0.0, "E6", 0.34, 0.11, 0.65),   # the tick: soft, dry, gone almost at once
                 (0.045, "B6", 0.16, 0.10, 0.7)],  # a glint just behind it, as the scan passes
                L, wet=0.08, spread=0.0)
    air = sweep(L, 0.0, 0.19, 2200, 7000, 0.030, q=2.4, seed=seed + 21, pan=-0.1)  # a thread of air gliding up
    return K.declick(y + air, 0.002, 0.03)


def crit(seed):
    L = 0.35
    n = K.ns(L)
    # the crack: a hard air tick, then a few shards of crackle as the glass lets go
    tick = grain(n, 0.0, 7200, 0.014, 0.55, q=1.0, seed=seed + 31, decay=0.35)
    shards = at(n, 0.003, K.crackle(K.ns(0.07), rate=1400, seed=seed + 33, bright=0.95, burstiness=0.5)
                * K.perc(K.ns(0.07), 0.0008, 0.05)) * 0.30
    crack = K.pan(tick + shards, 0.05)
    # F#6, A6 and D7 breaking out of it in a quick rise (bright glass, short tails), then the sparkle falling away
    y = U.notes([(0.000, "F#6", 0.50, 0.26, 0.85), (0.014, "A6", 0.46, 0.26, 0.85), (0.030, "D7", 0.40, 0.24, 0.9),
                 (0.095, "A6", 0.17, 0.16, 0.9), (0.150, "D7", 0.13, 0.14, 0.9), (0.205, "F#6", 0.09, 0.13, 0.9)],
                L, wet=0.16, spread=0.55)
    knock = at(n, 0.0, K.sine(K.glide(K.ns(0.05), 1500, 620), K.ns(0.05)) * K.perc(K.ns(0.05), 0.0006, 0.03)) * 0.16
    return K.declick(y + crack + K.pan(knock, 0.0), 0.0008, 0.06)


def xp_tick(seed):
    L = 0.17
    y = U.notes([(0.0, "A6", 0.36, 0.13, 0.7),     # the mote lands
                 (0.006, "D7", 0.12, 0.10, 0.75)],  # and rings once above itself
                L, wet=0.07, spread=0.0)
    return K.declick(y, 0.001, 0.05)


def level_up(seed):
    L = 2.4
    n = K.ns(L)
    big = U.room("big")
    mix = K.Mix(L)
    # the climb: D5 F#5 A5 D6 E6, each a little louder and longer-lived than the last
    climb = [(0.00, "D5", 0.36, 1.2), (0.13, "F#5", 0.42, 1.3), (0.26, "A5", 0.48, 1.4), (0.39, "D6", 0.54, 1.6, 0.6),
             (0.52, "E6", 0.58, 1.8, 0.65)]
    for i, (t, m, vel, t60, *b) in enumerate(climb):
        x = U.glass_note(m, L - t, vel, b[0] if b else 0.55, t60, seed=seed + 70 + i)
        mix.add(x, t, 1.0, 0.28 * np.sin(1.4 * i + 0.3), "big", 0.26)
    # the fifth it opens into, held: glass D4 and A4 struck as the climb tops out, D5 and A5 swelling in as pure tones
    for i, (t, m, vel, t60) in enumerate(((0.60, "D4", 0.50, 2.4), (0.62, "A4", 0.44, 2.3), (0.64, "D5", 0.36, 2.0))):
        x = U.glass_note(m, L - t, vel, 0.4, t60, seed=seed + 80 + i)
        mix.add(x, t, 1.0, (-0.3, 0.3, 0.0)[i], "big", 0.34)
    pure = np.zeros(n)
    for m, lvl in (("D4", 1.0), ("A4", 0.8), ("D5", 0.55), ("A5", 0.35)):
        f = float(K.hz(K.midi(m)))
        pure += (K.sine(f, n) + 0.18 * K.sine(2 * f, n)) * lvl
    swell = K.line(n, [(0.0, 0.0), (0.16, 0.0), (0.31, 1.0), (0.42, 0.75), (0.75, 0.22), (1.0, 0.0)])  # peaks ~0.75 s
    mix.add(pure * swell * 0.11, 0.0, 1.0, 0.0, "big", 0.30)
    # the shimmer: high glass, scattered and thin, drifting off above the chord
    for i, (t, m, vel) in enumerate(((0.74, "D7", 0.13), (0.86, "A6", 0.11), (1.02, "F#7", 0.07), (1.18, "D7", 0.08),
                                     (1.42, "A6", 0.05), (1.68, "D7", 0.035))):
        x = U.glass_note(m, L - t, vel, 0.7, 0.9, seed=seed + 90 + i)
        mix.add(x, t, 1.0, 0.6 * np.sin(2.1 * i + 0.5), "big", 0.4)
    y = mix.render({"big": big})
    air = U.air(L, 0.80, 0.62, fc=5600, level=0.05, seed=seed + 95)  # a breath swelling in under the peak
    return K.declick(y + air, 0.002, 0.45)


def region_enter(seed):
    L = 1.5
    n = K.ns(L)
    mix = K.Mix(L)
    # D4 from the left, A4 from the right, a fifth apart: calm, spacious, nothing in a hurry
    mix.add(U.glass_note("D4", L, 0.58, 0.38, 1.9, seed=seed + 101, core=0.45), 0.00, 1.0, -0.42, "big", 0.38)
    mix.add(U.glass_note("A4", L - 0.34, 0.52, 0.38, 2.1, seed=seed + 102, core=0.45), 0.34, 1.0, 0.42, "big", 0.38)
    # a faint low bloom under the pair, and a breath of air as the second note opens the space
    bloom = U.warm("D3", L, 0.04, 0.10, t60=1.3, attack=0.07)
    y = mix.render({"big": U.room("big")})
    air = U.air(L, 0.40, 0.30, fc=4200, level=0.016, seed=seed + 103)
    return K.declick(y + bloom + air, 0.003, 0.35)


def dread(seed):
    """The bed under a land far above you (SPEC 21.10), 8 s that loops without a seam: every tone makes a whole number of cycles in
    the loop, the air is filtered noise made over the whole loop (so it is periodic too) and its movement is slow sines of the same
    period. A low D (73.4 Hz) and a twin a cycle sharper beating once a loop, a fifth above and a cold octave over that, a wide dark
    breath of air through them. No melody, no bell: it is felt more than heard, and its volume is the land's weight."""
    L = 8.0
    n = K.ns(L)
    t = np.arange(n) / K.SR
    rng = np.random.default_rng(seed + 401)

    def cyc(c, ph=0.0):
        return np.sin(2 * np.pi * (c * t / L) + ph)

    tone = cyc(587) + 0.8 * cyc(588, 1.3) + 0.45 * cyc(880, 0.4) + 0.25 * cyc(1174, 2.0)
    swell = 0.78 + 0.22 * np.sin(2 * np.pi * 2 * t / L + 0.5)
    spec = np.fft.rfft(rng.standard_normal((2, n)), axis=1)
    f = np.fft.rfftfreq(n, 1 / K.SR)
    shape = (1 / (1 + (f / 650.0) ** 4)) * (f ** 2 / (f ** 2 + 70.0 ** 2))  # a dark band: low-passed at 650 Hz, high-passed at 70
    air = np.fft.irfft(spec * shape, n, axis=1)
    air /= np.std(air) + 1e-12
    move = 0.72 + 0.28 * np.sin(2 * np.pi * 3 * t / L + 0.7)
    left = tone * 0.42 * swell + air[0] * 0.60 * move
    right = tone * 0.42 * np.roll(swell, K.ns(0.37)) + air[1] * 0.60 * np.roll(move, K.ns(0.61))
    return (left + right) * 0.5  # mono: a sound played in the world is mono (the audio test holds every such file to it)


def sounds(Snd):
    return [
        Snd("ui_living_scan", scan, rms=-26.5),
        Snd("ui_living_crit", crit, rms=-24.5),
        Snd("ui_living_xp_tick", xp_tick, rms=-26.0),
        Snd("ui_living_level_up", level_up, rms=-24.0),
        Snd("ui_living_region_enter", region_enter, rms=-25.0),
        Snd("amb_living_dread", dread, rms=-29.0, mono=True),
    ]


def events(Evt):
    return [
        Evt("living.scan", "Band reads a creature", files=["ui_living_scan"], folder="living"),
        Evt("living.crit", "Critical hit", files=["ui_living_crit"], folder="living"),
        Evt("living.xp_tick", "XP gathers", files=["ui_living_xp_tick"], folder="living"),
        Evt("living.level_up", "Level up", files=["ui_living_level_up"], folder="living"),
        Evt("living.region_enter", "Region entered", files=["ui_living_region_enter"], folder="living"),
        Evt("living.dread", "A heavy land", files=["amb_living_dread"], folder="living"),
    ]
