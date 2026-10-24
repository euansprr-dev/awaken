"""Adaptive gate music (Awakened Pack stage 4, "The Sound"): four phase-aligned stems for every gate theme that the
client layers live (dev.awaken.core.audio.client.LayerPlayer), so the music follows what happens in the gate.

  bed      calm, exploring: the theme's room, its harmony, a fragment of its tune, its air
  tension  something hunts you or the gate is waking: a pulse and a worry (a minor second, a raised fourth)
  combat   a fight: the theme's drums, a driving bass, stabs
  lord     a Lord or boss: the theme's tune stated big, a choir, low hits

Every stem of a theme is an exact loop of the same number of samples (whole bars at the theme's tempo), rendered
circularly: whatever rings past the end (release tails, reverb) is folded back onto the start, so the loop has no
seam and the four stems stay sample-aligned for ever. Each theme keeps the mode, tempo and voices of its track
(audio_music.py), so a frost gate and a fire gate never sound alike.

Loudness (SPEC "Plan v2 · audio", music -19 +-2 dBFS RMS / -1 dBTP) is a rule on what the player hears, i.e. on the
mix at each level, not on a stem alone: the stems of a theme are mastered together (one gain per stem, then one
linked true-peak limiter over every level's mix) so that calm sits at -20.5, tension -19.5, combat -18.5 and a Lord
fight -17.5 dBFS gated RMS, every mix under -1 dBTP after encoding. A stem alone is quieter (it is a part).

Files: assets/awaken/sounds/layers/layer_<theme>_<stem>.ogg; the context manifest the game reads (stems, exact length,
the gains per level, fade rates, intent notes) is assets/awaken/music_layers/gate/<theme>.json.
"""
import json
import os

import numpy as np

import audiokit as K
import audio_score as A

M = K.midi
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MANIFESTS = os.path.join(ROOT, "src/main/resources/assets/awaken/music_layers")

STEMS = ["bed", "tension", "combat", "lord"]
# level name, gain per stem (bed, tension, combat, lord), gated RMS target of that mix (dBFS)
LEVELS = [
    ("calm", (1.0, 0.0, 0.0, 0.0), -20.5),
    ("tension", (0.85, 1.0, 0.0, 0.0), -19.5),
    ("combat", (0.6, 0.8, 1.0, 0.0), -18.5),
    ("lord", (0.45, 0.6, 0.9, 1.0), -17.5),
]
# seconds for a stem's gain to travel 0 -> 1: attack (rising), duck (falling because the music got more intense),
# release (falling because it calmed down). Combat hits at once; everything leaves slowly.
FADES = {
    "bed": (6.0, 2.0, 10.0),
    "tension": (3.0, 1.5, 8.0),
    "combat": (0.6, 1.5, 6.0),
    "lord": (1.5, 1.5, 4.0),
}
TP_CEILING = -1.0          # every level's mix and every stem alone, after encoding
TP_MASTER = TP_CEILING - 0.8  # before encoding: room for the encoder's overshoot
QUALITY = 1                # Vorbis q1 (~80 kbps stereo): 56 loops of a minute
TAIL = 12.0                # rendered past the loop's end, then folded back onto its start


# ---- loops -------------------------------------------------------------------------------------------------------

def loop_seconds(c):
    return c["bars"] * c["beats"] * 60.0 / c["bpm"]


def loop_frames(c):
    return K.ns(loop_seconds(c))


def fold(y, L):
    """Circular wrap: everything past L is added back from the start (tails ring into the next lap)."""
    out = y[:L].copy()
    k = L
    while k < len(y):
        seg = y[k:k + L]
        out[:len(seg)] += seg
        k += L
    return out


def cfilter(x, resp):
    """Circular zero-phase filter (the loop's end meets its start, so the filter wraps too)."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / K.SR)
    H = resp(f)
    if x.ndim == 1:
        return np.fft.irfft(np.fft.rfft(x) * H, n)
    return np.stack([np.fft.irfft(np.fft.rfft(x[:, c]) * H, n) for c in range(x.shape[1])], axis=1)


def loop_tone(L, note, partials=(1, 2, 3, 4, 6), amps=(1, 0.5, 0.25, 0.15, 0.06), seed=0):
    """A drone that loops by construction: every partial and every slow beat makes a whole number of cycles per loop."""
    sec = L / K.SR
    f0 = float(K.hz(M(note)))
    r = K.rng(seed)
    t = np.arange(L) / K.SR
    y = np.zeros(L)
    for p, a in zip(partials, amps):
        cyc = max(1, round(f0 * p * sec))
        beat = max(1, round(0.12 * p * sec))  # a slow shimmer, ~0.1 Hz times the partial
        ph = r.uniform(0, 6.28)
        y += a * np.sin(2 * np.pi * cyc * t / sec + ph) * (0.8 + 0.2 * np.sin(2 * np.pi * beat * t / sec + ph * 1.7))
    swell = 1 + 0.12 * np.sin(2 * np.pi * max(1, round(sec / 20)) * t / sec)
    return K.lowpass(y * swell, 1600, 1) / (np.sum(amps) + 1e-9)


def looped(L, make, xfade=3.0):
    """A texture that wasn't made to loop (wind, noise): rendered a little long, its tail crossfaded into its start."""
    k = K.ns(xfade)
    x = make(L + k)
    return K.loop_seamless(x, xfade)


def atmos(L, kind, seed, o):
    """The theme's air, looped: murk (stone, water above), wind, hiss, embers, steam, wings (hive), mains hum."""
    if kind == "murk":
        def make(n):
            y = A.murk(n, seed, o.get("cutoff", 400))
            if "wind" in o:
                cen, q, gust, mix = o["wind"]
                y = y + mix * A.wind(n, seed + 1, centre=cen, q=q, gust=gust)
            if "hiss" in o:
                lo, hi, mix = o["hiss"]
                y = y + mix * A.hiss(n, seed + 2, lo, hi)
            return y
    elif kind == "wind":
        def make(n):
            y = A.wind(n, seed, centre=o["centre"], q=o["q"], gust=o["gust"])
            if "hiss" in o:
                lo, hi, mix = o["hiss"]
                y = y + mix * A.hiss(n, seed + 2, lo, hi)
            return y
    elif kind == "hiss":
        def make(n):
            return A.hiss(n, seed, o["lo"], o["hi"], o.get("flutter", 0.4))
    elif kind == "embers":
        def make(n):
            em = np.stack([K.crackle(n, 6, seed + 3, 0.5, 0.6), K.crackle(n, 6, seed + 4, 0.5, 0.6)], axis=1) * 0.08
            return A.murk(n, seed, o.get("cutoff", 260)) * 1.2 + em
    elif kind == "steam":
        def make(n):
            st = A.hiss(n, seed + 3, 2500, 9000, 0.8) * (0.4 + 0.6 * np.clip(K.smooth_noise(n, 0.12, seed + 4), 0, 1))[:, None]
            return A.murk(n, seed, o.get("cutoff", 300)) * 0.8 + st * 0.6
    elif kind == "buzz":
        def make(n):
            a, b = o["notes"]
            fl = o.get("flutter", 17)
            return np.stack([_buzz(n, float(K.hz(M(a))), seed, fl), _buzz(n, float(K.hz(M(b))), seed + 7, fl)], axis=1)
    elif kind == "hum":
        # the Precursor Lab's mains hum (~100 and 150 Hz, whole cycles per loop) and a faint electrical hiss
        hum = loop_tone(L, 43.35, partials=(1, 1.5), amps=(1.0, 0.4), seed=seed)
        return np.stack([hum, hum], axis=1) * 0.12 + looped(L, lambda n: A.hiss(n, seed + 1, 5000, 11000, 0.3)) * 0.3
    else:
        raise ValueError(kind)
    return looped(L, make)


def _buzz(n, f0, seed, flutter=17):
    """Wings: a soft saw drone with a fast flutter."""
    amps = K.saw_amps(f0 * 1.01, bright=0.15, tilt=1.2, cap=60, f0=f0)
    x = K.wt_voice(f0, n, amps, 2 ** (K.smooth_noise(n, 0.5, seed) * 8 / 1200))
    fl = 0.65 + 0.35 * np.sin(2 * np.pi * (flutter + 2 * K.smooth_noise(n, 0.3, seed + 1)) * K.tvec(n))
    return K.lowpass(x * fl, 1300, 2)


def M_name(m):
    names = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
    return names[m % 12] + str(m // 12 - 1)


# ---- the score language: each stem is a list of ops over the theme's progression ------------------------------------

def apply(S, c, op, L):
    kind = op[0]
    room = c["room"]
    prog = c["prog"]
    if kind == "pads":
        _, inst, centre, vel, o = op
        o = dict(o)
        S.pads(inst, prog, 0, M(centre), stack=o.pop("stack", (0, 2, 4)), vel=vel, send=o.pop("send", room),
               wet=o.pop("wet", 0.45), overlap=o.pop("overlap", 1.5), pan=o.pop("pan", 0.35), **o)
    elif kind == "bass":
        _, inst, octave, vel, o = op
        o = dict(o)
        S.bass(inst, prog, 0, octave=octave, vel=vel, send=o.pop("send", room), wet=o.pop("wet", 0.2), **o)
    elif kind == "arp":
        _, inst, centre, pattern, step, vel, o = op
        o = dict(o)
        S.arp(inst, prog, 0, M(centre), pattern, step=step, stack=o.pop("stack", (0, 2, 4)), vel=vel,
              accent=o.pop("accent", None), pan=o.pop("pan", 0.0), send=o.pop("send", room), wet=o.pop("wet", 0.3), **o)
    elif kind == "beat":
        _, inst, pitch, hits, vel, o = op
        o = dict(o)
        every, dur, pan = o.pop("every", 1), o.pop("dur", 0.2), o.pop("pan", 0.0)
        send, wet = o.pop("send", room), o.pop("wet", 0.3)
        p = M(pitch) if isinstance(pitch, str) else pitch
        for b in range(0, c["bars"], every):
            for beat, vm in hits:
                S.play(inst, S.t(b, beat), p, dur, vel * vm * (1 + S.r.normal(0, 0.05)), pan, send, wet, **o)
    elif kind == "drums":
        # a bar pattern, with a fuller fill on the last bar of every four (the phrase breathes in)
        _, inst, pitch, main, fill, vel, o = op
        o = dict(o)
        send, wet, pan = o.pop("send", room), o.pop("wet", 0.3), o.pop("pan", 0.0)
        for b in range(c["bars"]):
            hits = fill if b % 4 == 3 else main
            for beat, vm in hits:
                S.play(inst, S.t(b, beat), M(pitch), 0.2, vel * vm * (1 + S.r.normal(0, 0.05)), pan, send, wet, **o)
    elif kind == "stabs":
        # chord stabs at fixed beats of every chord in the progression
        _, inst, centre, beats, dur, vel, o = op
        o = dict(o)
        stack, send, wet = o.pop("stack", (0, 2, 4)), o.pop("send", room), o.pop("wet", 0.35)
        bar = 0
        for root, bars in prog:
            tones = S.voiced(root, stack, M(centre))
            for beat in beats:
                if beat >= bars * c["beats"]:
                    continue
                for i, m in enumerate(tones):
                    pan = 0.4 * (2 * i / max(1, len(tones) - 1) - 1) if len(tones) > 1 else 0.0
                    S.play(inst, S.t(bar, beat), m, dur, vel, pan, send, wet, **o)
            bar += bars
    elif kind == "phrase":
        _, inst, notes, bars, octave, vel, o = op
        o = dict(o)
        beat, pan, send, wet = o.pop("beat", 0.0), o.pop("pan", 0.0), o.pop("send", room), o.pop("wet", 0.4)
        for b in bars:
            S.phrase(inst, notes, b, beat=beat, octave=octave, vel=vel, pan=pan, send=send, wet=wet, **o)
    elif kind == "colour":
        _, inst, degrees, per_bar, octave, vel, o = op
        o = dict(o)
        S.colour(inst, degrees, c["bars"], 0, per_bar=per_bar, octave=octave, vel=vel, grid=o.pop("grid", 1.0),
                 send=o.pop("send", room), wet=o.pop("wet", 0.6), **o)
    elif kind == "scatter":
        _, inst, count, lo, hi, vel, o = op
        o = dict(o)
        A.scatter(S, inst, count, 0.0, L / K.SR, lo, hi, vel=vel, send=o.pop("send", room), wet=o.pop("wet", 0.4),
                  spread=o.pop("spread", 0.8), **o)
    elif kind == "tone":
        _, note, gain, o = op
        S.add(np.stack([loop_tone(L, note, seed=S.seed + 31)] * 2, axis=1) * gain, 0.0, send=o.get("send", room),
              wet=o.get("wet", 0.2))
    elif kind == "atmos":
        _, name, gain, o = op
        S.add(atmos(L, name, S.seed + 41, o) * gain, 0.0)
    elif kind == "riser":
        # a reversed reverb of a struck chord sucking into each listed bar line
        _, notes, bars, length, gain, o = op
        rroom = o.get("room", room)
        for b in bars:
            mix = K.Mix(length)
            for i, (inst, deg, octv, v) in enumerate(notes):
                x = A.INSTR[inst](float(K.hz(S.deg(deg, octv))), 1.0, v, S.seed * 97 + b * 7 + i)
                mix.add(x, 0.0, 1.0, 0.3 * np.sin(i * 1.7), "r", 0.85)
            wet = mix.render({"r": A.ir(rroom)})[::-1]
            k = K.ns(0.025)
            wet[-k:] *= K._rise(k)[::-1][:, None]
            end = S.t(b)
            start = end - length
            if start < 0:
                start += L / K.SR  # lands on bar 0: rendered at the end, folded round
            S.add(wet * gain, start)
    else:
        raise ValueError(kind)


# ---- the themes --------------------------------------------------------------------------------------------------
# prog: (scale degree, bars), summing to the loop's bars. Phrases: (degree or "+semitones" or None, beats).

SIGH = [(0, 2), (4, 2), (5, 1.5), (4, 2.5)]  # the Signal's sigh (tonic, fifth, sixth, fifth) in the theme's mode
LULLABY = [(0, 1), (2, 1), (4, 1), (2, 1), (0, 1), (2, 1), ("+6", 1), (2, 1)]  # the Nightmare's, one note wrong
MIRROR_A = [(0, 1), (2, 1), (3, 1), (4, 2), (3, 1), (2, 1), (0, 1)]
MIRROR_B = [(4, 1), (6, 1), (7, 2), (6, 1), (4, 1), (2, 2)]
TICKS8 = [(b * 0.5, 1.0 if b % 2 == 0 else 0.55) for b in range(8)]
EIGHTHS = [(b * 0.5, 1.0 if b % 2 else 0.55) for b in range(8)]
SIXTEENTHS = [(b * 0.25, 1.0 if b % 2 else 0.5) for b in range(16)]
ROLL16 = [(b * 0.25, 0.45 + 0.55 * (b % 4 == 0)) for b in range(16)]
TAIKO_A = [(0, 1.0), (1.5, 0.6), (2, 0.85), (3, 0.7), (3.5, 0.5)]
TAIKO_FILL = [(0, 1.0), (1, 0.7), (2, 0.8), (2.5, 0.6), (3, 0.9), (3.25, 0.6), (3.5, 0.8), (3.75, 0.9)]
STRAIGHT_FILL = [(0, 1.0), (1, 0.7), (2, 0.9), (2.5, 0.6), (3, 0.8), (3.5, 0.9)]

THEMES = {
    "bell_crypt": dict(
        title="Bell Crypt", bpm=54, beats=4, bars=12, key="D3", mode="phrygian", room="cathedral",
        aim="Dread under stone: the crypt tolls for you, growing heavier rather than faster; the Bell Warden is the great bell waking.",
        prog=[(0, 2), (1, 2), (3, 2), (0, 2), (6, 2), (0, 2)],
        intent={"bed": "A buried drone, monks humming 'o', the sigh tolled on bells; the crypt breathing.",
                "tension": "A low bell every bar, strings trembling on D against E-flat, a slow heartbeat.",
                "combat": "Taiko under the vault, iron clanks, an eighth-note string ostinato, brass stabs.",
                "lord": "The choir opens to 'a', the sigh in slow brass, the great bell every two bars."},
        bed=[("tone", "D1", 0.30, {}),
             ("pads", "choir", "A3", 0.12, dict(vowel="o", a=2.5, r=3.0, wet=0.55)),
             ("phrase", "bell", SIGH, [1], 1, 0.34, dict(t60=6.0, bright=0.45, wet=0.45)),
             ("phrase", "bell", [(2, 2), (1, 2), (0, 4)], [7], 1, 0.3, dict(t60=6.0, bright=0.45, wet=0.45)),
             ("colour", "fmbell", [0, 4, 5, 7], 0.25, 2, 0.08, dict(t60=3.0, wet=0.7)),
             ("atmos", "murk", 0.45, dict(cutoff=380, wind=(300, 1.5, 0.08, 0.5)))],
        tension=[("beat", "bell", "D2", [(0, 1.0)], 0.26, dict(t60=5.0, bright=0.3, wet=0.5)),
                 ("pads", "strings", "E3", 0.09, dict(stack=(0, 1), trem=6.0, bright=0.35, a=1.5, r=2.0, wet=0.45)),
                 ("beat", "drum", "D1", [(0, 1.0), (0.35, 0.55)], 0.22, dict(every=2, wet=0.3)),
                 ("colour", "glass", [1, 5], 0.3, 2, 0.1, dict(t60=3.0, wet=0.7))],
        combat=[("drums", "taiko", "D1", TAIKO_A, TAIKO_FILL, 0.5, dict(wet=0.3)),
                ("beat", "clank", "D3", [(1, 0.6), (3, 0.8)], 0.12, dict(wet=0.5, pan=0.3)),
                ("arp", "staccato", "D3", [0, 0, 1, 0, 0, 1, 0, 2], 0.5, 0.22, dict(stack=(0, 4), wet=0.3, bright=0.45)),
                ("stabs", "brass", "A3", [0, 6], 0.45, 0.24, dict(stack=(0, 4, 7)))],
        lord=[("pads", "choir", "D4", 0.18, dict(vowel="a", stack=(0, 4, 7), a=1.0, r=2.5, wet=0.55)),
              ("phrase", "brass", [(0, 4), (4, 4), (5, 6), (4, 10)], [0], 0, 0.3, dict(wet=0.45)),
              ("phrase", "brass", [(2, 4), (1, 4), (0, 16)], [6], 0, 0.28, dict(wet=0.45)),
              ("beat", "bell", "D2", [(0, 1.0)], 0.3, dict(every=2, t60=7.0, bright=0.4, wet=0.55)),
              ("beat", "sub", "D1", [(0, 1.0)], 0.3, dict(every=2, dur=1.5, a=0.02, r=1.5)),
              ("drums", "taiko", "D1", [(0, 1.0), (2, 0.7)], [(0, 1.0), (2, 0.8), (3, 0.6), (3.5, 0.8)], 0.42, dict(wet=0.35))],
    ),
    "fungal_hollow": dict(
        title="Fungal Hollow", bpm=66, beats=3, bars=16, key="F3", mode="lydian", room="hall",
        aim="Strange, gentle wonder in three: a glowing hollow that breathes; the Mother of Spores swells it into a towering Lydian choir.",
        prog=[(0, 2), (1, 2), (0, 2), (1, 2), (5, 2), (1, 2), (2, 2), (1, 2)],
        intent={"bed": "A warm pad, a breathy flute floating on the raised fourth, marimba drops, spores popping.",
                "tension": "Marimba circling in 6/8, a plucked B under F that doesn't settle, vibes on the fourth.",
                "combat": "Frame drums in three, a low drum, a bouncing plucked bass, spores bursting.",
                "lord": "Mother of Spores: a choir in Lydian, the flute tune high, strings and a drum every bar."},
        bed=[("pads", "pad", "A3", 0.14, dict(stack=(0, 2, 4, 6), bright=0.22, a=2.0, r=3.0, wet=0.45)),
             ("bass", "sub", -1, 0.16, dict(wet=0.2)),
             ("phrase", "flute", [(4, 1.5), (3, 0.5), (4, 1), (6, 3)], [2], 1, 0.36, dict(breath=0.28, wet=0.4, pan=-0.15)),
             ("phrase", "flute", [(5, 1), (4, 1), (3, 1), (2, 3)], [10], 1, 0.34, dict(breath=0.28, wet=0.4, pan=-0.15)),
             ("colour", "marimba", [0, 2, 3, 4, 6], 0.5, 1, 0.12, dict(grid=0.5, wet=0.4, t60=1.0)),
             ("scatter", "bubble", 16, 64, 80, 0.07, dict(wet=0.4)),
             ("atmos", "murk", 0.32, dict(cutoff=600, hiss=(4000, 9000, 0.3)))],
        tension=[("arp", "marimba", "C5", [0, 1, 2, 3, 2, 1], 0.5, 0.17, dict(accent=[1, 0.6, 0.8, 0.6, 0.8, 0.6], wet=0.35, pan=0.25)),
                 ("beat", "pluck", "B2", [(0, 1.0)], 0.22, dict(bright=0.3, t60=1.5, wet=0.3)),
                 ("colour", "vibes", [3, 6], 0.4, 2, 0.1, dict(t60=2.0, wet=0.6))],
        combat=[("drums", "frame", "F2", [(0, 1.0), (1, 0.6), (1.5, 0.5), (2, 0.8), (2.5, 0.5)],
                 [(0, 1.0), (0.5, 0.6), (1, 0.8), (1.5, 0.7), (2, 0.9), (2.5, 1.0)], 0.32, dict(wet=0.25)),
                ("drums", "taiko", "F1", [(0, 1.0)], [(0, 1.0), (2, 0.7)], 0.4, dict(wet=0.3)),
                ("arp", "pluck", "F2", [0, None, 1, 0, 2, None], 0.5, 0.3, dict(stack=(0, 4), bright=0.4, t60=0.8, wet=0.2)),
                ("scatter", "bubble", 40, 70, 88, 0.09, dict(wet=0.3)),
                ("stabs", "staccato", "C4", [0, 1.5], 0.3, 0.16, dict(stack=(0, 2, 4)))],
        lord=[("pads", "choir", "C4", 0.17, dict(vowel="a", stack=(0, 2, 4, 6), wet=0.5)),
              ("phrase", "flute", [(4, 1.5), (3, 0.5), (4, 1), (6, 3), (7, 6)], [0, 8], 2, 0.34, dict(breath=0.2, wet=0.4)),
              ("pads", "strings", "F4", 0.1, dict(stack=(0, 4), bright=0.5, wet=0.45)),
              ("beat", "taiko", "F1", [(0, 1.0)], 0.38, dict(wet=0.3)),
              ("beat", "sub", "F1", [(0, 1.0)], 0.25, dict(every=2, dur=1.5, a=0.02, r=1.5))],
    ),
    "ember_mines": dict(
        title="Ember Mines", bpm=60, beats=4, bars=16, key="E3", mode="hijaz", room="cave",
        aim="Heat and labour deep in the rock: the oud's Hijaz line over far anvils, hammered into a march when the Ember Tyrant comes.",
        prog=[(0, 4), (1, 2), (0, 2), (3, 4), (1, 2), (0, 2)],
        intent={"bed": "Two drones in the rock, dark strings, the oud tune (E F G# A) now and then, embers ticking.",
                "tension": "The oud worrying E against F in eighths, far anvils, strings trembling.",
                "combat": "Taiko and anvils, an FM bass on E and F, brass stabs on the Hijaz chord.",
                "lord": "The Ember Tyrant: the horn tune stated, a choir on the fifth, an anvil every two bars."},
        bed=[("tone", "E1", 0.28, {}), ("tone", "B1", 0.2, {}),
             ("pads", "strings", "B3", 0.12, dict(bright=0.28, a=2.0, r=2.5, wet=0.45)),
             ("phrase", "pluck", [(0, 1), (1, 1), (2, 1), (3, 1), (2, 2), (1, 1), (0, 1)], [2, 10], 1, 0.36,
              dict(bright=0.45, metal=0.35, t60=1.8, wet=0.35, pan=0.2)),
             ("atmos", "embers", 0.45, dict(cutoff=260))],
        tension=[("arp", "pluck", "E2", [0, None, 0, 1, 0, None, 0, None], 0.5, 0.26,
                  dict(stack=(0, 1), bright=0.35, metal=0.3, t60=0.9, wet=0.25)),
                 ("beat", "clank", "E3", [(1.5, 1.0)], 0.12, dict(every=2, wet=0.6, pan=-0.4)),
                 ("pads", "strings", "E3", 0.07, dict(stack=(0, 1), trem=6.5, bright=0.3, wet=0.45))],
        combat=[("drums", "taiko", "E1", [(0, 1.0), (0.75, 0.5), (1.5, 0.7), (2, 0.9), (3, 0.6), (3.5, 0.7)],
                 [(0, 1.0), (1, 0.8), (1.5, 0.6), (2, 0.9), (2.5, 0.7), (3, 1.0), (3.5, 0.9)], 0.5, dict(wet=0.3)),
                ("beat", "clank", "E3", [(1, 0.8), (3, 1.0), (3.5, 0.5)], 0.16, dict(wet=0.5, pan=0.3)),
                ("arp", "fmbass", "E2", [0, 0, 1, 0, 0, 2, 0, 1], 0.5, 0.3, dict(stack=(0, 1), wet=0.15)),
                ("stabs", "brass", "G#3", [0, 3], 0.5, 0.22, dict(stack=(0, 2, 4)))],
        lord=[("phrase", "brass", [(0, 4), (1, 2), (0, 2), (2, 4), (3, 2), (2, 2)], [0, 8], 0, 0.34, dict(wet=0.4)),
              ("phrase", "brass", [(4, 4), (5, 2), (4, 2), (2, 4), (1, 4)], [4, 12], 0, 0.3, dict(wet=0.4)),
              ("pads", "choir", "B3", 0.16, dict(vowel="a", stack=(0, 4), wet=0.5)),
              ("beat", "clank", "E2", [(0, 1.0)], 0.22, dict(every=2, wet=0.6)),
              ("beat", "sub", "E1", [(0, 1.0)], 0.28, dict(every=2, dur=1.5, a=0.02, r=1.5))],
    ),
    "drowned_archive": dict(
        title="Drowned Archive", bpm=58, beats=4, bars=16, key="A2", mode="aeolian", room="cave",
        aim="A library under water: damped, slow and sinking, even a fight heard as if through the tide; the Archivist rises out of it.",
        prog=[(0, 2), (5, 2), (2, 2), (6, 2), (3, 2), (5, 2), (0, 2), (4, 2)],
        intent={"bed": "Damped felt piano breaking the chords, low strings, drips and bubbles, water overhead.",
                "tension": "A low piano ostinato, a cluster trembling in the strings, glass notes sinking.",
                "combat": "Drums heard through water, sub pulses in eighths, spiccato strings.",
                "lord": "The Drowned Archivist: a choir on 'u', the sinking tune in glass, strings above."},
        bed=[("bass", "sub", 0, 0.16, dict(wet=0.2)),
             ("arp", "piano", "E4", [0, 2, 1, 3, None, None, None, None], 1.0, 0.2, dict(accent=[1, 0.7, 0.8, 0.7], felt=0.92, wet=0.45)),
             ("pads", "strings", "C4", 0.07, dict(bright=0.25, a=2.5, r=3.0, wet=0.5)),
             ("scatter", "drip", 26, 82, 96, 0.09, dict(wet=0.5)),
             ("scatter", "bubble", 8, 55, 70, 0.05, dict(wet=0.4)),
             ("atmos", "murk", 0.5, dict(cutoff=340, wind=(220, 1.0, 0.06, 0.6)))],
        tension=[("arp", "piano", "A2", [0, None, 0, None, 1, None, 0, None], 0.5, 0.26, dict(stack=(0, 4), felt=0.8, wet=0.4)),
                 ("pads", "strings", "E3", 0.07, dict(stack=(0, 1, 2), trem=5.5, bright=0.25, wet=0.5)),
                 ("phrase", "glass", [(4, 1), (3, 1), (2, 2), (1, 4)], [3, 11], 2, 0.2, dict(t60=3.0, wet=0.6))],
        combat=[("drums", "drum", "A1", [(0, 1.0), (1.5, 0.6), (2, 0.8), (3, 0.6), (3.5, 0.5)], STRAIGHT_FILL, 0.48,
                 dict(wet=0.4, t60=0.9)),
                ("arp", "sub", "A1", [0, 0, 0, 0, 0, 0, 0, 0], 0.5, 0.2, dict(stack=(0,), a=0.01, r=0.2, wet=0.1)),
                ("arp", "staccato", "A3", [0, 2, 1, 2, 0, 2, 1, 2], 0.5, 0.15, dict(wet=0.35)),
                ("stabs", "strings", "E4", [0], 1.2, 0.12, dict(stack=(0, 2, 4), a=0.05, r=0.6))],
        lord=[("pads", "choir", "A3", 0.17, dict(vowel="u", stack=(0, 2, 4), wet=0.55)),
              ("phrase", "glass", [(4, 1), (3, 1), (2, 2), (1, 1), (2, 3), (2, 1), (1, 1), (0, 2), (-1, 1), (0, 3)], [0, 8], 2, 0.3,
               dict(t60=3.0, wet=0.5)),
              ("pads", "strings", "A4", 0.1, dict(stack=(0, 4), bright=0.4, wet=0.45)),
              ("beat", "taiko", "A1", [(0, 1.0), (2.5, 0.6)], 0.36, dict(wet=0.4)),
              ("beat", "sub", "A1", [(0, 1.0)], 0.26, dict(every=2, dur=1.5, a=0.02, r=1.5))],
    ),
    "clockwork_foundry": dict(
        title="Clockwork Foundry", bpm=72, beats=4, bars=16, key="C3", mode="dorian", room="room",
        aim="A machine room that keeps time: ticking, precise, a little sinister; the Gearwright Colossus is the whole works at full drive.",
        prog=[(0, 2), (3, 2), (0, 2), (6, 2), (4, 2), (3, 2), (2, 2), (3, 2)],
        intent={"bed": "An escapement ticking, a music box turning over, strings, steam in the pipes.",
                "tension": "The ticks double, a pizzicato bass stalks, strings tremble on a second.",
                "combat": "Drums and girder clanks, a running plucked bass, brass stabs on the off-beat.",
                "lord": "The Gearwright Colossus: C G A G hammered in the brass every two bars, bells, a choir."},
        bed=[("beat", "tick", "G6", TICKS8, 0.07, dict(wet=0.25, pan=0.3)),
             ("beat", "tick", "C6", [(0.75, 0.8), (2.75, 0.8)], 0.06, dict(wet=0.25, pan=-0.3, wood=True)),
             ("arp", "musicbox", "C5", [0, 1, 2, 3, None, None, None, None], 0.5, 0.12, dict(accent=[1, 0.7, 0.8, 0.7], wet=0.35, pan=-0.2)),
             ("pads", "strings", "Eb4", 0.08, dict(bright=0.3, wet=0.4)),
             ("atmos", "steam", 0.38, dict(cutoff=300))],
        tension=[("beat", "tick", "D7", [(b * 0.25, 1.0 if b % 4 == 0 else 0.5) for b in range(16)], 0.05, dict(wet=0.2, pan=0.4)),
                 ("arp", "pluck", "C2", [0, None, None, None, 0, None, 2, None], 0.5, 0.34, dict(stack=(0, 4), bright=0.35, t60=1.0, wet=0.2)),
                 ("pads", "strings", "G3", 0.06, dict(stack=(0, 1), trem=7.0, wet=0.4))],
        combat=[("drums", "drum", "C1", [(0, 1.0), (1, 0.6), (2, 0.9), (3, 0.6), (3.5, 0.5)],
                 [(0, 1.0), (0.5, 0.6), (1, 0.8), (1.5, 0.6), (2, 0.9), (2.5, 0.7), (3, 1.0), (3.5, 0.9)], 0.45, dict(wet=0.25)),
                ("beat", "clank", "C3", [(1, 0.8), (3, 1.0)], 0.16, dict(wet=0.4, pan=0.25)),
                ("arp", "pluck", "C2", [0, 0, 2, 0, 1, 0, 2, 1], 0.5, 0.32, dict(stack=(0, 4), bright=0.4, t60=0.6, wet=0.15)),
                ("stabs", "brass", "G3", [0, 2.5], 0.35, 0.22, dict(stack=(0, 2, 4)))],
        lord=[("phrase", "brass", [(0, 2), (4, 2), (5, 1), (4, 3)], [0, 2, 4, 6, 8, 10, 12, 14], 0, 0.3, dict(wet=0.4)),
              ("phrase", "brass", [(0, 2), (4, 2), (5, 1), (4, 3)], [0, 4, 8, 12], 1, 0.22, dict(wet=0.4)),
              ("pads", "choir", "G3", 0.14, dict(vowel="o", stack=(0, 4), wet=0.45)),
              ("beat", "bell", "C3", [(0, 1.0)], 0.22, dict(every=4, t60=5.0, wet=0.5)),
              ("beat", "clank", "C2", [(0, 1.0), (2, 0.7)], 0.2, dict(wet=0.5))],
    ),
    "glass_desert": dict(
        title="Glass Desert", bpm=50, beats=4, bars=12, key="E3", mode="phrygian", room="air",
        aim="Vast, empty heat and ringing glass, slow as a mirage; the Glass Serpent's fight should glitter and cut, never turn muddy.",
        prog=[(0, 4), (1, 2), (0, 2), (6, 2), (5, 1), (0, 1)],
        intent={"bed": "Glass harmonica fifths, ringing glass bowls, sand wind; the tune once, slow.",
                "tension": "The oud ostinato under it, a low E, the F bowl ringing a half step above.",
                "combat": "Frame drums and a low drum, a metallic plucked bass, a shaker in the wind.",
                "lord": "The Glass Serpent: a choir, the harmonica tune high, a gong bell and sub every two bars."},
        bed=[("pads", "harmonica", "B4", 0.16, dict(stack=(0, 4), wet=0.5)),
             ("bass", "sub", -1, 0.13, dict(wet=0.1)),
             ("colour", "glass", [0, 4, 1, 7], 0.35, 2, 0.14, dict(t60=5.0, bright=0.6, wet=0.7)),
             ("phrase", "harmonica", [(0, 2), (4, 2), (5, 2), (4, 4)], [2], 1, 0.28, dict(wet=0.5)),
             ("atmos", "wind", 0.45, dict(centre=1100, q=0.6, gust=0.1, hiss=(5000, 12000, 0.3)))],
        tension=[("arp", "pluck", "E3", [0, None, 1, None, 0, None, None, None], 0.5, 0.28,
                  dict(stack=(0, 4), bright=0.4, metal=0.35, t60=2.0, wet=0.3, pan=-0.2)),
                 ("tone", "E2", 0.14, {}),
                 ("beat", "glass", "F4", [(0, 1.0)], 0.12, dict(every=2, t60=4.0, wet=0.6))],
        combat=[("drums", "frame", "E2", [(0, 1.0), (0.5, 0.4), (1, 0.7), (2, 0.9), (2.5, 0.5), (3, 0.7), (3.5, 0.5)],
                 [(b * 0.5, 0.6 + 0.4 * (b % 2 == 0)) for b in range(8)], 0.32, dict(wet=0.3)),
                ("drums", "taiko", "E1", [(0, 1.0), (2.5, 0.7)], [(0, 1.0), (1.5, 0.7), (2, 0.8), (3, 0.9)], 0.42, dict(wet=0.25)),
                ("arp", "pluck", "E2", [0, 0, 1, 0, 2, 0, 1, 0], 0.5, 0.28, dict(stack=(0, 1), bright=0.45, metal=0.4, t60=0.7, wet=0.15)),
                ("beat", "shaker", 60, EIGHTHS, 0.2, dict(wet=0.15, pan=0.35))],
        lord=[("pads", "choir", "B3", 0.16, dict(vowel="a", stack=(0, 4), wet=0.5)),
              ("phrase", "harmonica", [(0, 2), (4, 2), (5, 2), (4, 4), (7, 2), (6, 1), (5, 1), (4, 4)], [0, 6], 2, 0.32, dict(wet=0.5)),
              ("beat", "bell", "E2", [(0, 1.0)], 0.26, dict(every=2, t60=8.0, bright=0.35, wet=0.6)),
              ("beat", "sub", "E1", [(0, 1.0)], 0.26, dict(every=2, dur=2.0, a=0.02, r=1.5)),
              ("beat", "taiko", "E1", [(0, 1.0), (2, 0.6)], 0.32, dict(wet=0.3))],
    ),
    "hive": dict(
        title="Hive", bpm=76, beats=4, bars=16, key="F2", mode="aeolian", room="room",
        aim="Warm, busy and faintly wrong: wings everywhere, a second swarm a half step off; the Hive Regent brings the swarm's full weight.",
        prog=[(0, 2), (5, 2), (6, 2), (0, 2), (3, 2), (5, 2), (4, 2), (6, 2)],
        intent={"bed": "Wings as a fluttering drone, strings, the marimba tune, honey dripping.",
                "tension": "The hexagonal pizzicato pattern, and a second swarm a half step up: agitated.",
                "combat": "Drums in sixteenths like a swarm, spiccato strings, brass stabs.",
                "lord": "The Hive Regent: a choir on 'e', the tune in brass, marimba running, a taiko."},
        bed=[("atmos", "buzz", 0.12, dict(notes=("F2", "C3"))),
             ("pads", "strings", "Ab3", 0.11, dict(bright=0.35, wet=0.4)),
             ("bass", "sub", 0, 0.14, dict(wet=0.1)),
             ("phrase", "marimba", [(4, 1), (5, 1), (4, 1), (2, 1), (0, 4)], [2, 10], 2, 0.3, dict(wet=0.35, pan=-0.2)),
             ("scatter", "drip", 14, 66, 76, 0.08, dict(wet=0.4))],
        tension=[("arp", "pluck", "C4", [0, 1, 2, 3, 2, 1], 0.5, 0.18,
                  dict(accent=[1, 0.6, 0.75, 0.9, 0.7, 0.6], bright=0.55, t60=0.9, wet=0.3, pan=0.3)),
                 ("atmos", "buzz", 0.1, dict(notes=("F#2", "C#3"), flutter=23))],
        combat=[("drums", "drum", "F1", [(0, 1.0), (0.5, 0.4), (1, 0.6), (2, 0.9), (2.75, 0.5), (3, 0.7)], ROLL16, 0.42, dict(wet=0.25)),
                ("arp", "staccato", "F3", [0, 1, 2, 1, 0, 1, 2, 1], 0.25, 0.14, dict(stack=(0, 2, 4), wet=0.3)),
                ("stabs", "brass", "C4", [0, 3.5], 0.3, 0.2, dict(stack=(0, 2, 4)))],
        lord=[("pads", "choir", "C4", 0.15, dict(vowel="e", stack=(0, 2, 4), wet=0.45)),
              ("phrase", "brass", [(4, 2), (5, 2), (4, 2), (2, 2), (0, 8)], [0, 8], 0, 0.3, dict(wet=0.4)),
              ("arp", "marimba", "F4", [0, 1, 2, 3, 4, 3, 2, 1], 0.25, 0.14, dict(stack=(0, 2, 4), wet=0.3)),
              ("beat", "taiko", "F1", [(0, 1.0), (2, 0.8)], 0.36, dict(wet=0.3))],
    ),
    "overgrown_temple": dict(
        title="Overgrown Temple", bpm=60, beats=4, bars=16, key="G2", mode="dorian", room="hall",
        aim="Old, green and calm, a holy place the forest took back; the Root Saint's fight is solemn and rooted, not frantic.",
        prog=[(0, 2), (3, 2), (0, 2), (6, 2), (2, 2), (3, 2), (4, 2), (3, 2)],
        intent={"bed": "A warm pad, the wooden flute's tune twice, fireflies chirping, forest air.",
                "tension": "Kalimba circling, a hand drum, strings trembling on the fifth.",
                "combat": "Frame drums running in sixteenths, taiko, a plucked bass.",
                "lord": "The Root Saint: a choir, the brass and the flute trading the tune, a drum on every bar."},
        bed=[("pads", "pad", "D4", 0.14, dict(bright=0.25, wet=0.45)),
             ("bass", "sub", 0, 0.16, dict(wet=0.15)),
             ("phrase", "flute", [(4, 2), (5, 1), (4, 1), (3, 2), (2, 2)], [2], 2, 0.34, dict(breath=0.3, wet=0.4, pan=-0.2)),
             ("phrase", "flute", [(0, 1), (2, 1), (4, 2), (5, 2), (4, 2)], [10], 2, 0.32, dict(breath=0.3, wet=0.4, pan=-0.2)),
             ("scatter", "chirp", 16, 96, 104, 0.045, dict(wet=0.5)),
             ("atmos", "wind", 0.32, dict(centre=520, q=0.8, gust=0.1))],
        tension=[("arp", "marimba", "G4", [0, 2, 1, 3, None, 2, 1, None], 0.5, 0.15, dict(wet=0.35, pan=0.3, t60=0.8)),
                 ("beat", "frame", "G2", [(0, 1.0), (2.5, 0.6)], 0.16, dict(wet=0.3)),
                 ("pads", "strings", "D3", 0.06, dict(stack=(0, 4), trem=5.0, wet=0.4))],
        combat=[("drums", "frame", "G2", [(0, 1.0), (0.5, 0.5), (1, 0.7), (1.75, 0.5), (2, 0.9), (2.5, 0.6), (3, 0.8), (3.5, 0.6)],
                 [(b * 0.25, 0.5 + 0.5 * (b % 2 == 0)) for b in range(16)], 0.3, dict(wet=0.25)),
                ("drums", "taiko", "G1", [(0, 1.0), (2, 0.7)], [(0, 1.0), (1.5, 0.7), (2, 0.9), (3, 0.8), (3.5, 0.9)], 0.42, dict(wet=0.3)),
                ("arp", "pluck", "G2", [0, None, 0, 1, 0, None, 2, 1], 0.5, 0.28, dict(stack=(0, 4), bright=0.4, t60=0.8, wet=0.15)),
                ("stabs", "staccato", "D4", [0, 2.5], 0.3, 0.14, dict(stack=(0, 2, 4)))],
        lord=[("pads", "choir", "D4", 0.16, dict(vowel="a", stack=(0, 2, 4), wet=0.5)),
              ("phrase", "brass", [(0, 4), (2, 2), (4, 2), (5, 4), (4, 4)], [0, 8], 0, 0.3, dict(wet=0.4)),
              ("phrase", "flute", [(4, 2), (5, 1), (4, 1), (3, 2), (2, 2)], [4, 12], 2, 0.3, dict(breath=0.2, wet=0.4)),
              ("beat", "taiko", "G1", [(0, 1.0)], 0.34, dict(wet=0.3))],
    ),
    "lantern_night_market": dict(
        title="Lantern Night Market", bpm=84, beats=4, bars=16, key="D3", mode="ionian", room="room",
        aim="The friendly one: a night market you'd want to linger in; a fight here stays playful, the Lantern Broker a showman.",
        prog=[(0, 2), (5, 2), (3, 2), (4, 2), (0, 2), (5, 2), (3, 2), (4, 2)],
        intent={"bed": "The friendly one: felt piano in quarters, the koto tune and its answer.",
                "tension": "A shaker, a plucked bass, bells: the market gets busy (rarely a threat here).",
                "combat": "A frame drum, a quick shaker, the koto running, a bouncing bass.",
                "lord": "The Lantern Broker: the tune on a horn, a choir, a drum and a bell; still a little playful."},
        bed=[("arp", "piano", "A3", [0, 2, 1, 2], 1.0, 0.18, dict(accent=[1, 0.7, 0.85, 0.7], felt=0.7, wet=0.3)),
             ("phrase", "koto", [(4, 1), (5, 0.5), (4, 0.5), (2, 1), (1, 1), (0, 2), (None, 2)], [4], 1, 0.4,
              dict(t60=2.0, wet=0.35, pan=0.15)),
             ("phrase", "koto", [(2, 1), (4, 1), (5, 1), (7, 1), (5, 1.5), (4, 0.5), (2, 2)], [12], 1, 0.38,
              dict(t60=2.0, wet=0.35, pan=0.15)),
             ("bass", "sub", -1, 0.12, dict(wet=0.1))],
        tension=[("beat", "shaker", 60, EIGHTHS, 0.18, dict(wet=0.2, pan=0.35)),
                 ("arp", "pluck", "D2", [0, None, None, None, 2, None, None, None], 0.5, 0.32, dict(stack=(0, 4), bright=0.35, t60=1.2, wet=0.2)),
                 ("colour", "fmbell", [0, 2, 4, 5], 0.5, 2, 0.08, dict(grid=0.5, t60=2.0, wet=0.5))],
        combat=[("drums", "frame", "D2", [(0, 1.0), (2, 0.6), (3.5, 0.4)], [(0, 1.0), (1, 0.6), (2, 0.8), (2.5, 0.6), (3, 0.9), (3.5, 0.7)],
                 0.3, dict(wet=0.25)),
                ("beat", "shaker", 60, SIXTEENTHS, 0.16, dict(wet=0.15, pan=-0.3)),
                ("arp", "koto", "D4", [0, 1, 2, 1, 0, 1, 2, 3], 0.5, 0.2, dict(stack=(0, 2, 4), t60=1.0, wet=0.25)),
                ("arp", "pluck", "D2", [0, None, 2, None, 0, 1, 2, None], 0.5, 0.3, dict(stack=(0, 4), t60=0.8, wet=0.15))],
        lord=[("phrase", "brass", [(4, 1), (5, 0.5), (4, 0.5), (2, 1), (1, 1), (0, 4)], [0, 4, 8, 12], 0, 0.28, dict(wet=0.35)),
              ("pads", "choir", "A3", 0.13, dict(vowel="a", wet=0.4)),
              ("beat", "drum", "D1", [(0, 1.0), (2, 0.8)], 0.34, dict(wet=0.25)),
              ("beat", "bell", "D4", [(0, 1.0)], 0.14, dict(every=4, t60=4.0, wet=0.5))],
    ),
    "frost_monastery": dict(
        title="Frost Monastery", bpm=50, beats=4, bars=12, key="B2", mode="aeolian", room="cathedral",
        aim="Cold, vast and devout: a far chant in a stone hall with snow outside; the Abbot of Frost answers with the full choir.",
        prog=[(0, 4), (5, 2), (0, 2), (3, 2), (4, 1), (0, 1)],
        intent={"bed": "A hummed fifth in a huge stone room, ice bells, cold wind; the chant once, far off.",
                "tension": "Low sung pulses on 'u', ice bells circling high, a soft drum on every bar.",
                "combat": "Taiko under the vaults, spiccato strings on B and F#, small bells on the off-beats.",
                "lord": "The Abbot of Frost: the chant in unison and in octaves, the great bell, a sub."},
        bed=[("pads", "choir", "B2", 0.11, dict(vowel="m", stack=(0, 4), a=4.0, r=5.0, wet=0.5, overlap=3.0)),
             ("colour", "fmbell", [0, 2, 4, 7], 0.35, 3, 0.07, dict(t60=3.0, ratio=3.5, wet=0.7)),
             ("phrase", "choir", [(0, 2), (2, 2), (3, 2), (2, 2)], [4], 1, 0.16, dict(vowel="o", a=0.35, r=1.2, wet=0.5)),
             ("atmos", "wind", 0.35, dict(centre=2200, q=0.5, gust=0.08, hiss=(6000, 13000, 0.25)))],
        tension=[("beat", "choir", "B2", [(0, 1.0), (2, 0.7)], 0.1, dict(vowel="u", a=0.08, r=0.6, dur=0.9, wet=0.5)),
                 ("arp", "fmbell", "F#6", [0, 1, 2, 1], 0.5, 0.06, dict(stack=(0, 2, 4), t60=1.2, ratio=3.5, wet=0.6)),
                 ("beat", "drum", "B1", [(0, 1.0)], 0.2, dict(wet=0.4))],
        combat=[("drums", "taiko", "B1", [(0, 1.0), (1.5, 0.6), (2, 0.8), (3, 0.6)],
                 [(0, 1.0), (1, 0.7), (1.5, 0.6), (2, 0.9), (3, 0.8), (3.5, 0.9)], 0.48, dict(wet=0.4)),
                ("arp", "staccato", "B2", [0, 0, 1, 0, 0, 1, 0, 2], 0.5, 0.2, dict(stack=(0, 4), wet=0.35)),
                ("beat", "bell", "F#4", [(1, 0.6), (3, 0.8)], 0.08, dict(t60=2.5, wet=0.6))],
        lord=[("phrase", "choir", [(0, 2), (2, 2), (3, 2), (2, 2), (0, 1), (2, 1), (4, 2), (3, 2), (2, 1), (0, 1)], [0, 4, 8], 1, 0.26,
               dict(vowel="o", a=0.3, r=1.0, wet=0.5)),
              ("phrase", "choir", [(0, 2), (2, 2), (3, 2), (2, 2), (0, 1), (2, 1), (4, 2), (3, 2), (2, 1), (0, 1)], [0, 4, 8], 0, 0.18,
               dict(vowel="o", a=0.3, r=1.0, wet=0.5)),
              ("beat", "bell", "B2", [(0, 1.0)], 0.26, dict(every=2, t60=8.0, wet=0.6)),
              ("beat", "sub", "B1", [(0, 1.0)], 0.26, dict(every=2, dur=2.0, a=0.02, r=1.5))],
    ),
    "mirror_palace": dict(
        title="Mirror Palace", bpm=64, beats=4, bars=16, key="C3", mode="lydian", room="air",
        aim="Elegant and disorienting: every phrase mirrored, nothing quite where you hear it; the Mirror Knight duels in reflections.",
        prog=[(0, 2), (1, 2), (0, 2), (1, 2), (5, 2), (1, 2), (4, 2), (1, 2)],
        intent={"bed": "A glass harmonica chord, a palindrome in glass with its reflection an octave up, half a beat late.",
                "tension": "Reversed swells into every other bar line, a glass ostinato.",
                "combat": "Taiko, glass struck on two and four (the raised fourth), a plucked bass.",
                "lord": "The Mirror Knight: a choir, the palindrome and its reflection stated, a bell, a sub."},
        bed=[("pads", "harmonica", "G4", 0.11, dict(stack=(0, 2, 4, 6), wet=0.5)),
             ("bass", "sub", 0, 0.12, dict(wet=0.1)),
             ("phrase", "glass", MIRROR_A, [2], 2, 0.32, dict(t60=2.5, wet=0.5, pan=-0.5)),
             ("phrase", "glass", MIRROR_A, [2], 3, 0.15, dict(beat=0.5, t60=2.0, wet=0.6, pan=0.5)),
             ("phrase", "glass", MIRROR_B, [10], 2, 0.3, dict(t60=2.5, wet=0.5, pan=0.5)),
             ("phrase", "glass", MIRROR_B, [10], 3, 0.14, dict(beat=0.5, t60=2.0, wet=0.6, pan=-0.5)),
             ("atmos", "hiss", 0.12, dict(lo=7000, hi=14000, flutter=0.6))],
        tension=[("riser", [("glass", 1, 2, 0.4), ("glass", 5, 2, 0.35)], [2, 4, 6, 8, 10, 12, 14, 16], 2.0, 0.22, dict(room="air")),
                 ("arp", "glass", "C5", [0, None, 2, None, 1, None, 3, None], 0.5, 0.12, dict(stack=(0, 2, 4), t60=1.5, wet=0.5, pan=0.3))],
        combat=[("drums", "taiko", "C1", [(0, 1.0), (1.5, 0.6), (2, 0.8), (3, 0.6)], STRAIGHT_FILL, 0.42, dict(wet=0.3)),
                ("beat", "glass", "F#5", [(1, 0.8), (3, 1.0)], 0.16, dict(t60=1.2, bright=0.8, wet=0.4)),
                ("arp", "pluck", "C2", [0, 0, 1, 0, 2, 0, 1, 0], 0.5, 0.28, dict(stack=(0, 4), bright=0.5, t60=0.7, wet=0.15))],
        lord=[("pads", "choir", "E4", 0.15, dict(vowel="a", stack=(0, 2, 4, 6), wet=0.5)),
              ("phrase", "glass", MIRROR_A, [0, 4, 8, 12], 2, 0.34, dict(t60=2.5, wet=0.5, pan=-0.4)),
              ("phrase", "glass", MIRROR_A, [0, 4, 8, 12], 3, 0.17, dict(beat=0.5, t60=2.0, wet=0.6, pan=0.4)),
              ("beat", "bell", "C3", [(0, 1.0)], 0.22, dict(every=4, t60=7.0, wet=0.6)),
              ("beat", "sub", "C1", [(0, 1.0)], 0.26, dict(every=2, dur=2.0, a=0.02, r=1.5))],
    ),
    "precursor_lab": dict(
        title="Precursor Lab", bpm=90, beats=4, bars=16, key="F#2", mode="aeolian", room="room",
        aim="Clinical and ancient: a machine still counting in the dark; Unit Zero is its countdown at full power.",
        prog=[(0, 2), (5, 2), (2, 2), (6, 2), (3, 2), (5, 2), (0, 2), (4, 2)],
        intent={"bed": "A soft FM arpeggiator, a pad, the mains hum, the sigh in signal-blue glass.",
                "tension": "A sine pulse in eighths, a low drum, glass on the minor second.",
                "combat": "Drums with a sixteenth-note hat of ticks, an FM bass, brass stabs.",
                "lord": "Unit Zero: the sigh repeated in glass like a countdown, brass under it, a choir on 'e'."},
        bed=[("arp", "fmbell", "C#5", [0, 1, 2, 3, 2, 1, 2, 3], 0.5, 0.07,
              dict(accent=[1, 0.6, 0.8, 0.6], t60=0.45, index=0.9, ratio=2.0, wet=0.25, pan=0.3)),
             ("pads", "pad", "C#4", 0.12, dict(bright=0.35, wet=0.4)),
             ("atmos", "hum", 0.5, {}),
             ("phrase", "glass", [(0, 1), (4, 1), (5, 1.5), (4, 2.5)], [4, 12], 3, 0.3, dict(t60=2.0, wet=0.4, pan=-0.2)),
             ("scatter", "chirp", 12, 88, 100, 0.035, dict(wet=0.3))],
        tension=[("arp", "pulse", "F#2", [0, 0, 0, 0, 0, 0, 0, 0], 0.5, 0.25, dict(stack=(0,), t60=0.3, bright=0.4, wet=0.15)),
                 ("beat", "drum", "F#1", [(0, 1.0), (2, 0.7)], 0.22, dict(wet=0.15)),
                 ("colour", "glass", [1, 5, 6], 0.3, 3, 0.08, dict(t60=2.0, wet=0.5))],
        combat=[("drums", "drum", "F#1", [(0, 1.0), (0.75, 0.5), (1.5, 0.6), (2, 0.9), (3, 0.7), (3.5, 0.6)],
                 [(b * 0.25, 0.4 + 0.6 * (b % 2 == 0)) for b in range(16)], 0.42, dict(wet=0.15)),
                ("arp", "fmbass", "F#2", [0, 0, 1, 0, 0, 2, 0, 1], 0.5, 0.3, dict(stack=(0, 4), index=2.2, wet=0.1)),
                ("beat", "tick", "C#7", [(b * 0.25, 1.0 if b % 2 == 0 else 0.6) for b in range(16)], 0.05, dict(wet=0.15, pan=0.3)),
                ("stabs", "brass", "C#4", [0, 3], 0.3, 0.18, dict(stack=(0, 2, 4)))],
        lord=[("phrase", "glass", [(0, 1), (4, 1), (5, 1.5), (4, 2.5)], [0, 2, 4, 6, 8, 10, 12, 14], 2, 0.34, dict(t60=2.0, wet=0.4)),
              ("phrase", "brass", [(0, 2), (4, 2), (5, 3), (4, 5)], [0, 4, 8, 12], 0, 0.28, dict(wet=0.35)),
              ("pads", "choir", "C#4", 0.13, dict(vowel="e", stack=(0, 4), wet=0.45)),
              ("beat", "sub", "F#1", [(0, 1.0)], 0.26, dict(every=2, dur=1.5, a=0.02, r=1.5))],
    ),
    "nightmare": dict(
        title="Nightmare", bpm=52, beats=4, bars=12, key="C#3", mode="aeolian", room="cave",
        aim="The gate meant to frighten: a lullaby with one wrong note and a limping heart; the Sleeper is the dream coming apart.",
        prog=[(0, 2), (5, 2), (0, 2), (1, 2), (3, 2), (1, 2)],
        intent={"bed": "Cluster strings, a buried drone, the damp music box's lullaby with its one wrong note, whispers.",
                "tension": "A heartbeat on every bar, reversed piano sucking into the bar lines.",
                "combat": "Taiko that keeps the heartbeat's limp, cluster stabs, a low piano hammering.",
                "lord": "The Sleeper: a choir in clusters, the lullaby high and more out of tune, the heart racing."},
        bed=[("pads", "strings", "E3", 0.08, dict(stack=(0, 1, 2), bright=0.2, a=3.0, r=3.5, wet=0.5)),
             ("tone", "C#1", 0.26, {}),
             ("phrase", "musicbox", LULLABY, [2], 2, 0.34, dict(detune=28, wet=0.45, pan=0.2)),
             ("phrase", "musicbox", [(1, 1), (0, 1), (-1, 1), (0, 3)], [8], 2, 0.32, dict(detune=28, wet=0.45, pan=0.2)),
             ("scatter", "whisper", 6, 40, 50, 0.45, dict(wet=0.5, spread=0.9)),
             ("atmos", "murk", 0.5, dict(cutoff=300))],
        tension=[("beat", "drum", "C#1", [(0, 1.0), (0.32, 0.6)], 0.26, dict(wet=0.35)),
                 ("riser", [("piano", 0, 1, 0.5), ("piano", 4, 1, 0.4)], [2, 6, 10], 2.5, 0.28, dict(room="cave")),
                 ("pads", "strings", "C#3", 0.05, dict(stack=(0, 1), trem=4.5, wet=0.5))],
        combat=[("drums", "taiko", "C#1", [(0, 1.0), (0.32, 0.6), (2, 0.8), (2.32, 0.5), (3, 0.5)],
                 [(0, 1.0), (0.32, 0.6), (1, 0.7), (1.5, 0.6), (2, 0.9), (2.32, 0.6), (3, 0.8), (3.5, 0.9)], 0.48, dict(wet=0.4)),
                ("stabs", "strings", "E3", [0, 1.5, 3], 0.4, 0.12, dict(stack=(0, 1, 2), a=0.02, r=0.3)),
                ("arp", "piano", "C#2", [0, None, 0, None, 1, None, 0, None], 0.5, 0.3, dict(stack=(0, 1), felt=0.5, wet=0.3))],
        lord=[("pads", "choir", "E3", 0.16, dict(vowel="u", stack=(0, 1, 2), wet=0.55)),
              ("phrase", "musicbox", LULLABY, [0, 4, 8], 3, 0.4, dict(detune=40, wet=0.45)),
              ("beat", "drum", "C#1", [(0, 1.0), (0.3, 0.6), (1, 0.9), (1.3, 0.6), (2, 1.0), (2.3, 0.6), (3, 0.9), (3.3, 0.6)], 0.3,
               dict(wet=0.35)),
              ("beat", "sub", "C#1", [(0, 1.0)], 0.26, dict(every=2, dur=2.0, a=0.02, r=1.5))],
    ),
    "sky_archipelago": dict(
        title="Sky Archipelago", bpm=66, beats=4, bars=16, key="D3", mode="lydian", room="air",
        aim="Open air and lift, the most hopeful gate; the Sky Leviathan is huge and soaring, never grim.",
        prog=[(0, 2), (1, 2), (0, 2), (1, 2), (5, 2), (4, 2), (1, 2), (0, 2)],
        intent={"bed": "Airy strings and fifths, the high flute's tune and its answer, wind.",
                "tension": "A glass arpeggio, a soft pulse, strings trembling on the raised fourth.",
                "combat": "Taiko, brass stabs, a plucked bass running in eighths.",
                "lord": "The Sky Leviathan: a choir, the tune in the brass, high strings."},
        bed=[("pads", "strings", "A4", 0.1, dict(bright=0.55, a=2.0, r=3.0, wet=0.5)),
             ("pads", "pad", "D4", 0.1, dict(stack=(0, 4), bright=0.3, wet=0.45)),
             ("bass", "sub", -1, 0.12, dict(wet=0.1)),
             ("phrase", "flute", [(4, 2), (3, 1), (4, 1), (6, 4)], [2], 2, 0.34, dict(breath=0.24, wet=0.45, pan=0.2)),
             ("phrase", "flute", [(7, 2), (6, 1), (4, 1), (5, 2), (4, 2)], [10], 2, 0.32, dict(breath=0.24, wet=0.45, pan=0.2)),
             ("atmos", "wind", 0.5, dict(centre=850, q=0.6, gust=0.12))],
        tension=[("arp", "glass", "A5", [0, None, 2, None, 1, None, 3, None], 0.5, 0.12, dict(t60=1.8, wet=0.5, pan=-0.3)),
                 ("arp", "pulse", "D2", [0, None, 0, None, 0, None, 0, 0], 0.5, 0.18, dict(stack=(0,), t60=0.35, wet=0.15)),
                 ("pads", "strings", "D4", 0.06, dict(stack=(0, 3), trem=6.0, bright=0.5, wet=0.45))],
        combat=[("drums", "taiko", "D1", [(0, 1.0), (1.5, 0.6), (2, 0.8), (3, 0.6), (3.5, 0.5)], STRAIGHT_FILL, 0.42, dict(wet=0.3)),
                ("stabs", "brass", "A3", [0, 2.5], 0.4, 0.22, dict(stack=(0, 2, 4))),
                ("arp", "pluck", "D2", [0, 0, 1, 0, 2, 0, 1, 0], 0.5, 0.28, dict(stack=(0, 4), bright=0.45, t60=0.7, wet=0.15))],
        lord=[("pads", "choir", "A3", 0.15, dict(vowel="a", stack=(0, 2, 4), wet=0.5)),
              ("phrase", "brass", [(4, 2), (3, 1), (4, 1), (6, 4), (7, 2), (6, 1), (4, 1), (5, 2), (4, 2)], [0, 8], 0, 0.3, dict(wet=0.4)),
              ("pads", "strings", "D5", 0.09, dict(stack=(0, 4), bright=0.55, wet=0.5)),
              ("beat", "taiko", "D1", [(0, 1.0), (2, 0.7)], 0.34, dict(wet=0.3))],
    ),
}


# ---- rendering and group mastering -------------------------------------------------------------------------------

def render_stem(tid, stem, seed):
    c = THEMES[tid]
    L = loop_frames(c)
    S = A.Score(c["bpm"], c["bars"], c["key"], c["mode"], beats=c["beats"], tail=TAIL, seed=seed)
    for op in c[stem]:
        apply(S, c, op, L)
    rooms = set(S.mix.sends) | {c["room"]}
    y = S.mix.render({r: A.ir(r) for r in rooms}, tail=8.0)
    y = fold(y, L)
    return cfilter(y, K.hp_resp(25.0, 2))  # no DC or rumble (circular, like everything in a loop)


def mix(stems, gains, scale):
    out = np.zeros_like(stems[0])
    for s, g, a in zip(stems, gains, scale):
        if g:
            out += s * (g * a)
    return out


def _peaks_os(x, block=32, over=4):
    """Per-block peak of |x| oversampled x4 (circular), max over channels: the true peak, block by block."""
    n = len(x)
    nb = n // block + (1 if n % block else 0)
    out = np.zeros(nb)
    for c in range(x.shape[1]):
        up = np.abs(np.fft.irfft(np.fft.rfft(x[:, c]), n * over) * over)
        pad = np.zeros(nb * block * over)
        pad[:len(up)] = up
        out = np.maximum(out, pad.reshape(nb, block * over).max(axis=1))
    return out


def _solve_gain(stems, gains, scale, k, target):
    """The scalar for stem k that puts this level's mix at the target gated RMS (bisection in dB)."""
    lo, hi = -40.0, 30.0
    for _ in range(40):
        mid = (lo + hi) / 2
        sc = list(scale)
        sc[k] = K.dbamp(mid)
        r = K.gated_rms(mix(stems, gains, sc))
        if r < target:
            lo = mid
        else:
            hi = mid
    return K.dbamp((lo + hi) / 2)


def master_group(stems, ceiling_db=TP_MASTER):
    """One gain per stem so every level's mix meets its RMS target, then one gain envelope over all stems (a linked
    look-ahead limiter, circular) so no level's mix passes the true-peak ceiling. Returns the processed stems."""
    stems = [s.copy() for s in stems]
    L = len(stems[0])
    block = 32
    for _ in range(5):
        scale = [1.0] * len(stems)
        for li, (name, gains, target) in enumerate(LEVELS):
            k = max(i for i, g in enumerate(gains) if g)  # the stem this level brings in
            scale[k] = _solve_gain(stems, gains, scale, k, target)
        stems = [s * a for s, a in zip(stems, scale)]
        need = np.ones(L // block + (1 if L % block else 0))
        ceil = K.dbamp(ceiling_db)
        checks = [gains for name, gains, target in LEVELS] + [tuple(float(i == k) for i in range(len(stems))) for k in range(len(stems))]
        for gains in checks:  # every level's mix, and every stem alone (each may be heard by itself mid-crossfade)
            pk = _peaks_os(mix(stems, gains, [1.0] * len(stems)), block)
            need = np.minimum(need, np.minimum(1.0, ceil / np.maximum(pk, 1e-12)))
        if need.min() > 0.999:
            break
        # look-ahead (8 blocks ~ 6 ms) and a 150 ms release, run twice round the loop so the seam is smooth too
        nb = len(need)
        ahead = need.copy()
        for k in range(1, 9):
            ahead = np.minimum(ahead, np.roll(need, -k))
        rel = np.exp(-block / (0.15 * K.SR))
        g = np.empty(nb)
        cur = 1.0
        for lap in range(2):
            for i in range(nb):
                cur = ahead[i] if ahead[i] < cur else ahead[i] + (cur - ahead[i]) * rel
                g[i] = cur
        g = np.minimum(g, ahead)
        gs = np.interp(np.arange(L), np.arange(nb) * block + block / 2, g, period=nb * block)
        stems = [s * gs[:, None] for s in stems]
    return stems


def measure_levels(stems):
    """{level: (gated RMS, LUFS, true peak)} of each level's mix."""
    out = {}
    for name, gains, target in LEVELS:
        m = mix(stems, gains, [1.0] * len(stems))
        out[name] = (K.gated_rms(m), K.loudness(m), K.true_peak(m))
    return out


def file_name(tid, stem):
    return "layers/layer_%s_%s" % (tid, stem)


class Group:
    """One theme: four files mastered together, and the manifest the game reads."""

    def __init__(self, tid, index):
        self.tid, self.index = tid, index
        self.c = THEMES[tid]
        self.files = [file_name(tid, s) for s in STEMS]

    def build(self, write_ogg, log=print):
        import time
        t0 = time.time()
        raw = [render_stem(self.tid, s, 1000 + 10 * self.index + k) for k, s in enumerate(STEMS)]
        stems = master_group(raw)
        for attempt in range(3):
            for f, y in zip(self.files, stems):
                write_ogg(os.path.join(SOUNDS, f + ".ogg"), y, QUALITY, "AWAKEN - %s (%s)" % (self.c["title"], f.rsplit("_", 1)[1]))
            dec = [K.read_audio(os.path.join(SOUNDS, f + ".ogg")) for f in self.files]
            assert all(len(d) == loop_frames(self.c) for d in dec), f"{self.tid}: decoded length differs from the loop"
            lv = measure_levels(dec)
            worst = max(max(v[2] for v in lv.values()), max(K.true_peak(d) for d in dec))
            if worst <= TP_CEILING - 0.05:
                break
            stems = [s * K.dbamp(TP_CEILING - 0.15 - worst) for s in stems]  # the encoder overshot: trim and encode again
        self.measured = lv
        self.stem_rms = [K.gated_rms(d) for d in dec]
        self.stem_tp = [K.true_peak(d) for d in dec]
        log(f"  {self.tid:22s} loop {loop_seconds(self.c):5.1f} s  " + "  ".join(
            f"{n} {r:5.1f}/{tp:4.1f}" for n, (r, lu, tp) in lv.items()) + f"  ({time.time() - t0:.0f} s)")
        self.write_manifest()

    def manifest(self):
        c = self.c
        out = {
            "schemaVersion": 1,
            "id": "awaken:gate/" + self.tid,
            "source": "music",
            "title": c["title"],
            "bpm": c["bpm"], "beats": c["beats"], "bars": c["bars"], "key": c["key"][:-1], "mode": c["mode"],
            "sampleRate": K.SR,
            "frames": loop_frames(c),
            "stems": [{"name": s, "file": "awaken:" + f, "attack": FADES[s][0], "duck": FADES[s][1], "release": FADES[s][2],
                       "intent": c["intent"][s]} for s, f in zip(STEMS, self.files)],
            "levels": [{"name": n, "gains": list(g), "rms": t} for n, g, t in LEVELS],
        }
        m = getattr(self, "measured", None)
        if m:
            out["measured"] = {n: {"rms": round(r, 2), "lufs": round(lu, 2), "truePeak": round(tp, 2)} for n, (r, lu, tp) in m.items()}
            out["measuredStems"] = {s: {"rms": round(r, 2), "truePeak": round(tp, 2)}
                                    for s, r, tp in zip(STEMS, self.stem_rms, self.stem_tp)}
        return out

    def write_manifest(self):
        path = os.path.join(MANIFESTS, "gate", self.tid + ".json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        old = {}
        if os.path.exists(path) and not getattr(self, "measured", None):
            with open(path) as f:
                old = json.load(f)  # a JSON-only run keeps the last measured numbers
        data = self.manifest()
        for k in ("measured", "measuredStems"):
            if k in old and k not in data:
                data[k] = old[k]
        with open(path, "w") as f:
            json.dump(data, f, indent=2)
            f.write("\n")


def groups():
    return [Group(tid, i) for i, tid in enumerate(THEMES)]


def sounds(Snd):
    return []


def events(Evt):
    # one event for every layered music context: the client streams the context's stems itself (LayeredSound),
    # the event only has to resolve (and names a real file so a plain /playsound of it is harmless)
    return [Evt("music.layers", "Music shifts", files=[file_name("bell_crypt", "bed")], stream=True),
            Evt("amb.layers", "Ambience shifts", files=["amb_well"], stream=True)]


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
