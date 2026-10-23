"""AWAKEN's music (tools/art/audio_build.py renders it): seven cues for moments that matter, a track for each gate
theme, one for the Descent.

The identity: Solo Leveling's tension, held back. Glass for the System (the same voice as the UI), felt piano and
soft strings for the player, a distant choir only when something is bigger than you, low drums for danger. Everything
leans on one motif, the Signal's sigh: tonic, fifth, the minor sixth falling back to the fifth (D A Bb A). It answers
upward (D A C D) when you win something, turns major (D A B A) when you rise, and inverts when a gate breaks.

Cues are 15-35 s and play through vanilla's music manager (a cue takes a song's place, then vanilla's long silence
follows). Tracks are about two minutes and sit in the biomes' music attribute with vanilla-length delays, so silence
stays the default. The Abyssal Shrine keeps "no sound at all".
"""
import numpy as np

import audiokit as K
import audio_score as A

M = K.midi


def riser(S, notes, end, length, room="cathedral", gain=0.5):
    """A reversed reverb of a struck chord swelling into `end` seconds (the strike lands on `end`)."""
    mix = K.Mix(length)
    for i, (inst, m, vel) in enumerate(notes):
        x = A.INSTR[inst](float(K.hz(M(m))), 1.0, vel, 900 + i)
        mix.add(x, 0.0, 1.0, 0.3 * np.sin(i * 1.7), "r", 0.85)
    wet = mix.render({"r": A.ir(room)})[::-1]
    k = K.ns(0.025)  # the reversed strike would stop dead: let it close in 25 ms, on the downbeat
    wet[-k:] *= K._rise(k)[::-1][:, None]
    S.add(wet * gain, max(0.0, end - length))


def at(S, inst, t, note, dur, vel, pan=0.0, send="hall", wet=0.35, **kw):
    S.play(inst, t, M(note), dur, vel, pan, send, wet, **kw)


def seq(S, inst, t0, notes, vel=0.5, pan=0.0, send="hall", wet=0.35, **kw):
    """notes: [(name or None, seconds[, vel multiplier])] played one after another from t0."""
    t = t0
    for item in notes:
        name, d = item[0], item[1]
        vm = item[2] if len(item) > 2 else 1.0
        if name:
            at(S, inst, t, name, d, vel * vm, pan, send, wet, **kw)
        t += d
    return t


def chord(S, inst, t, names, dur, vel, spread=0.5, send="hall", wet=0.4, roll=0.0, **kw):
    for i, n in enumerate(names):
        p = spread * (2 * i / max(1, len(names) - 1) - 1) if len(names) > 1 else 0.0
        at(S, inst, t + roll * i, n, dur, vel, p, send, wet, **kw)


# ---- cues ------------------------------------------------------------------------------------------------------------

def cue_awakening(seed):
    S = A.Score(60, 7, "D3", "aeolian", tail=6.0, seed=seed)
    # the System arrives: a reversed glass chord out of a sub swell, faint glints
    riser(S, [("glass", "D5", 0.6), ("glass", "A5", 0.55), ("glass", "E6", 0.5), ("bell", "D4", 0.4)], 4.0, 4.0, gain=0.55)
    at(S, "sub", 0.2, "D2", 26.0, 0.32, a=3.5, r=4.0)
    at(S, "sub", 0.2, "D1", 12.0, 0.25, a=3.5, r=3.0)
    for t, n in ((1.2, "A6"), (2.1, "E7"), (2.9, "D7"), (3.5, "A6")):
        at(S, "fmbell", t, n, 1.0, 0.07, pan=np.sin(t * 3) * 0.7, wet=0.7, t60=2.0)
    # the Signal's sigh: D A Bb A, glass doubled by the felt piano an octave down
    motif = [("D5", 1.0), ("A5", 1.0), ("Bb5", 1.5), ("A5", 2.5)]
    seq(S, "glass", 4.0, motif, 0.62, 0.1, wet=0.45, t60=3.0)
    seq(S, "piano", 4.0, [(n.replace("5", "4"), d) for n, d in motif], 0.38, -0.1, wet=0.35)
    chord(S, "strings", 4.0, ["D3", "F3", "A3"], 6.0, 0.22, a=2.0, r=2.0, bright=0.35)
    # the answer reaches up: D A C D, over Bb, C, then D major (the Picardy third)
    seq(S, "glass", 10.0, [("D5", 1.0), ("A5", 1.0), ("C6", 1.0), ("D6", 3.0)], 0.6, -0.1, wet=0.45, t60=3.2)
    seq(S, "piano", 10.0, [("D4", 1.0), ("A4", 1.0), ("C5", 1.0), ("D5", 3.0)], 0.32, 0.1, wet=0.35)
    chord(S, "strings", 10.0, ["Bb2", "F3", "D4"], 2.2, 0.24, a=0.8, r=1.2)
    chord(S, "strings", 12.0, ["C3", "G3", "E4"], 2.2, 0.26, a=0.6, r=1.2)
    chord(S, "choir", 10.0, ["D4", "A4"], 4.4, 0.14, vowel="a", a=2.5, r=2.0)
    # arrival
    chord(S, "strings", 14.0, ["D3", "A3", "F#4"], 10.0, 0.3, a=0.6, r=4.0, bright=0.5)
    chord(S, "choir", 14.0, ["D4", "F#4", "A4"], 10.0, 0.18, vowel="a", a=1.2, r=4.5)
    chord(S, "piano", 14.0, ["D2", "A2"], 6.0, 0.55, spread=0.0, wet=0.3)
    for i, n in enumerate(("F#6", "D6", "A5", "F#5", "D5")):
        at(S, "glass", 14.0 + 0.4 * i, n, 1.0, 0.36 - 0.03 * i, pan=0.5 - 0.25 * i, wet=0.5, t60=2.5)
    seq(S, "glass", 18.0, [("D5", 1.0), ("A5", 1.0), ("B5", 1.5), ("A5", 3.5)], 0.5, 0.0, wet=0.5, t60=3.5)
    seq(S, "piano", 18.0, [("F#4", 1.0), ("E4", 1.0), ("D4", 1.5), ("A3", 3.5)], 0.3, -0.2, wet=0.35)
    at(S, "glass", 24.0, "D6", 2.0, 0.34, pan=-0.3, wet=0.6, t60=5.0)
    at(S, "glass", 24.05, "A6", 2.0, 0.26, pan=0.3, wet=0.6, t60=5.0)
    return S.render(("hall", "cathedral"))


def cue_beast(seed):
    S = A.Score(60, 4, "D3", "phrygian", tail=3.0, seed=seed)
    for k in range(6):
        v = 0.75 * (1 - 0.1 * k)
        at(S, "drum", 1.6 * k, "D1", 0.2, v, send="cave", wet=0.35)
        at(S, "drum", 1.6 * k + 0.32, "D1", 0.2, v * 0.6, send="cave", wet=0.35)
    chord(S, "strings", 0.6, ["D2", "Eb2", "A2"], 10.0, 0.3, spread=0.4, send="cave", wet=0.4, a=3.0, r=4.0, bright=0.25)
    at(S, "brass", 4.0, "A2", 2.4, 0.5, -0.2, send="cave", wet=0.35)
    at(S, "brass", 6.4, "Bb2", 3.0, 0.55, -0.2, send="cave", wet=0.35)
    at(S, "brass", 9.6, "D3", 1.8, 0.45, 0.2, send="cave", wet=0.4)
    at(S, "brass", 11.4, "Eb3", 3.2, 0.5, 0.2, send="cave", wet=0.4)
    at(S, "sub", 8.0, "D1", 7.0, 0.3, a=2.0, r=3.0)
    at(S, "glass", 12.6, "Bb5", 2.0, 0.3, 0.3, send="cathedral", wet=0.7, t60=4.0)
    S.bed(A.wind(K.ns(S.length), seed + 5, centre=260, q=1.2, gust=0.1), 0.0, None, 0.4, 3.0, 5.0)
    return S.render(("cave", "cathedral"))


def cue_gate_forming(seed):
    S = A.Score(60, 4, "E3", "phrygian", tail=3.0, seed=seed)
    riser(S, [("bell", "E3", 0.5), ("glass", "E5", 0.5), ("glass", "B5", 0.45), ("glass", "F6", 0.4)], 3.0, 3.0, gain=0.5)
    chord(S, "strings", 3.0, ["E3", "B3", "F4"], 10.0, 0.26, a=0.8, r=3.5, trem=6.5, bright=0.5)
    seq(S, "glass", 3.0, [("E5", 1.0), ("B5", 1.0), ("C6", 1.5), ("B5", 2.5)], 0.52, 0.15, wet=0.45, t60=3.0)
    for k in range(5):
        at(S, "drum", 3.0 + 2.0 * k, "E1", 0.2, 0.45 * (1 - 0.12 * k), send="cave", wet=0.4)
    seq(S, "glass", 9.0, [("E5", 1.0), ("B5", 1.0), ("F5", 3.0)], 0.4, -0.2, wet=0.5, t60=3.5)
    at(S, "sub", 2.5, "E1", 11.0, 0.28, a=1.5, r=3.0)
    at(S, "fmbell", 13.5, "B6", 1.0, 0.1, 0.4, send="cathedral", wet=0.7, t60=3.0)
    return S.render(("hall", "cave", "cathedral"))


def cue_gate_broke(seed):
    S = A.Score(60, 5, "D3", "aeolian", tail=3.0, seed=seed)
    n = K.ns(4.0)
    S.add(K.sub_boom(n, 58, 26, 3.0) * 0.9, 0.0, send="cave", wet=0.3)
    crack = K.lowpass(K.noise(K.ns(1.2), "white", seed), 1200, 2) * K.perc(K.ns(1.2), 0.002, 0.5)
    S.add(crack * 0.35, 0.0, send="cave", wet=0.5)
    at(S, "clank", 0.0, "D3", 1.0, 0.4, -0.3, send="cave", wet=0.5)
    at(S, "clank", 0.01, "Ab3", 1.0, 0.32, 0.3, send="cave", wet=0.5)
    at(S, "drum", 0.0, "D1", 0.2, 0.9, send="cave", wet=0.4)
    # the lament falls: Dm, Am/C, Gm/Bb, A (left open)
    for t, ch, d in ((0.6, ["D3", "F3", "A3"], 2.6), (3.1, ["C3", "E3", "A3"], 2.6), (5.6, ["Bb2", "D3", "G3"], 2.6),
                     (8.1, ["A2", "C#3", "E3"], 5.5)):
        chord(S, "strings", t, ch, d, 0.28, a=0.9 if t > 1 else 1.4, r=2.5, bright=0.4, send="hall", wet=0.4)
    chord(S, "choir", 2.0, ["D3", "A3"], 11.0, 0.16, vowel="o", a=3.0, r=4.0)
    seq(S, "glass", 3.0, [("D6", 1.0), ("A5", 1.0), ("G#5", 1.5), ("A5", 3.0)], 0.5, 0.2, send="cathedral", wet=0.5, t60=3.0)
    for k, t in enumerate((4.0, 6.0, 8.0, 10.0)):
        at(S, "drum", t, "D1", 0.2, 0.6 * (1 - 0.18 * k), send="cave", wet=0.4)
    at(S, "drone", 11.0, "D1", 7.0, 0.5, a=2.0, r=4.0)
    S.bed(A.murk(K.ns(10.0), seed + 3, 300), 11.0, 21.0, 0.6, 3.0, 4.0)
    at(S, "clank", 15.0, "D3", 1.0, 0.12, 0.5, send="cave", wet=0.8)
    return S.render(("hall", "cave", "cathedral"))


def cue_rank_up(seed):
    S = A.Score(60, 6, "D3", "ionian", tail=4.0, seed=seed)
    for i, n in enumerate(("D2", "A2", "D3", "F#3", "A3", "D4")):
        at(S, "piano", 0.12 * i, n, 4.0, 0.45, -0.3 + 0.12 * i, wet=0.3)
    seq(S, "glass", 1.5, [("D5", 0.94), ("A5", 0.94), ("B5", 1.4), ("A5", 2.4)], 0.55, 0.1, wet=0.45, t60=3.0)
    for t, ch, d in ((0.0, ["D3", "F#3", "A3"], 5.2), (5.2, ["G2", "D3", "B3"], 3.8), (9.0, ["A2", "E3", "C#4"], 3.2),
                     (12.2, ["D3", "A3", "F#4"], 8.0)):
        chord(S, "strings", t, ch, d, 0.24, a=1.2 if t == 0 else 0.7, r=2.5, bright=0.45)
    seq(S, "glass", 6.6, [("D6", 0.94), ("C#6", 0.47), ("B5", 0.47), ("A5", 2.0)], 0.48, -0.15, wet=0.45, t60=2.5)
    seq(S, "piano", 6.6, [("B4", 0.94), ("A4", 0.94), ("G4", 1.9)], 0.3, 0.2, wet=0.3)
    seq(S, "glass", 10.6, [("A5", 0.47), ("B5", 0.47), ("C#6", 0.66), ("D6", 3.5)], 0.55, 0.0, wet=0.5, t60=3.5)
    chord(S, "choir", 9.0, ["A3", "E4"], 3.2, 0.12, vowel="a", a=1.5, r=1.5)
    chord(S, "choir", 12.2, ["D4", "F#4", "A4"], 7.0, 0.16, vowel="a", a=1.0, r=4.0)
    chord(S, "piano", 12.2, ["D2", "D3"], 5.0, 0.5, spread=0.0, wet=0.3)
    at(S, "sub", 12.0, "D2", 7.0, 0.25, a=0.5, r=3.0)
    for i, n in enumerate(("D5", "F#5", "A5", "D6", "F#6")):
        at(S, "glass", 12.3 + 0.15 * i, n, 1.0, 0.3, -0.5 + 0.25 * i, wet=0.5, t60=2.8)
    at(S, "bell", 12.2, "D5", 2.0, 0.12, wet=0.5, t60=6.0, bright=0.4)
    at(S, "glass", 16.5, "A6", 1.5, 0.2, 0.4, wet=0.6, t60=4.0)
    at(S, "glass", 16.6, "D7", 1.5, 0.15, -0.4, wet=0.6, t60=4.0)
    return S.render(("hall",))


def cue_shrine(seed):
    S = A.Score(60, 5, "D3", "dorian", tail=4.0, seed=seed)
    at(S, "bell", 0.0, "D4", 2.0, 0.42, 0.0, send="cathedral", wet=0.45, t60=7.0, bright=0.4)
    for t, lh, d in ((1.0, ["D3", "A3"], 4.0), (5.0, ["G2", "D3"], 4.0), (9.0, ["F2", "C3"], 4.0), (13.0, ["D3", "A3"], 5.0)):
        chord(S, "piano", t, lh, d, 0.34, spread=0.2, wet=0.35, felt=0.75)
    seq(S, "piano", 1.5, [("A4", 1.0), ("C5", 0.5), ("D5", 2.0)], 0.4, 0.15, wet=0.35, felt=0.75)
    seq(S, "piano", 5.0, [("B4", 1.0), ("D5", 1.0), ("E5", 2.0)], 0.38, 0.15, wet=0.35, felt=0.75)
    seq(S, "piano", 9.0, [("C5", 1.0), ("A4", 1.0), ("F5", 2.0)], 0.38, 0.15, wet=0.35, felt=0.75)
    seq(S, "piano", 13.0, [("E5", 1.0), ("D5", 1.0), ("A4", 3.0)], 0.34, 0.15, wet=0.4, felt=0.75)
    for t, ch, d in ((1.0, ["D3", "F3", "A3", "E4"], 4.0), (5.0, ["G3", "B3", "D4"], 4.0), (9.0, ["F3", "A3", "C4"], 4.0),
                     (13.0, ["D3", "A3", "E4"], 5.0)):
        chord(S, "pad", t, ch, d, 0.12, a=1.5, r=3.0, bright=0.25)
    at(S, "glass", 13.0, "D6", 2.0, 0.22, 0.3, send="cathedral", wet=0.6, t60=4.0)
    return S.render(("hall", "cathedral"))


def cue_lair(seed):
    S = A.Score(60, 4, "D3", [0, 2, 3, 7, 8], tail=3.0, seed=seed)
    at(S, "drone", 0.0, "D2", 13.0, 0.5, a=3.0, r=4.0)
    at(S, "drone", 0.0, "A2", 13.0, 0.35, a=4.0, r=4.0)
    seq(S, "koto", 0.5, [("D4", 1.0), ("A4", 1.0), ("Bb4", 1.5), ("A4", 2.0)], 0.55, -0.2, wet=0.4, t60=2.4)
    seq(S, "flute", 5.0, [("A4", 2.0), ("Bb4", 1.5), ("A4", 3.0)], 0.4, 0.2, wet=0.45, breath=0.3)
    for k, n in enumerate(("D3", "A3", "D3", "F3", "D3", "A3")):
        at(S, "koto", 6.0 + 1.0 * k, n, 0.9, 0.28, -0.4, wet=0.3, t60=1.6)
    for t in (6.0, 8.0, 10.0, 12.0):
        at(S, "frame", t, "D2", 0.2, 0.3, 0.1, send="room", wet=0.3)
    at(S, "koto", 12.5, "D5", 3.0, 0.32, 0.3, wet=0.6, t60=3.5)
    at(S, "koto", 12.52, "A5", 3.0, 0.18, -0.3, wet=0.6, t60=3.5)
    S.bed(A.wind(K.ns(S.length), seed + 7, centre=380, q=1.1, gust=0.15), 0.0, None, 0.22, 3.0, 4.0)
    return S.render(("hall", "room"))


CUES = {
    "awakening": (cue_awakening, "Arise", "A rising theme answers you"),
    "beast": (cue_beast, "Something Holds This Ground", "Low drums warn of a beast"),
    "gate_forming": (cue_gate_forming, "A Gate Forms", "Strings tremble as a gate forms"),
    "gate_broke": (cue_gate_broke, "The Break", "A deep boom, then a dark theme"),
    "rank_up": (cue_rank_up, "Reassessed", "A bright theme rises"),
    "shrine": (cue_shrine, "Rest", "A temple bell and quiet piano"),
    "lair": (cue_lair, "Den", "A koto and a flute, wary"),
}

# ---- tracks ----------------------------------------------------------------------------------------------------------
# Every track has the same breathing shape: an intro on the tonic, the theme (A), a breath where the melody rests (B),
# the theme again with something added (A2), and an outro that thins to nothing. Phrases are (degree, beats).

def form(intro=2, a=8, b=8, a2=8, outro=3):
    secs, bar = {}, 0
    for name, n in (("intro", intro), ("A", a), ("B", b), ("A2", a2), ("outro", outro)):
        secs[name] = (bar, n)
        bar += n
    return secs, bar


def breathe(S, secs, b_db=-7.0, a_db=-3.0, a2_db=0.0):
    """The piece's dynamics: rise out of the intro, hold the theme, sink for the breath, open up for A2, fade."""
    t = lambda name, frac=0.0: S.t(secs[name][0] + frac * secs[name][1])
    S.shape = [(0.0, -9.0), (t("A"), a_db), (t("B", 0.25), a_db - 1.0), (t("B", 0.5), b_db), (t("A2"), a2_db - 1.0),
               (t("A2", 0.6), a2_db), (t("outro"), a2_db - 2.0), (S.length, -12.0)]


def span(secs, prog, prog_b, on=("intro", "A", "B", "A2", "outro"), home=0):
    """The whole piece's chords; sections not in `on` are rests."""
    out = []
    for name, (bar, n) in secs.items():
        seg = [(home, n)] if name in ("intro", "outro") else prog_b if name == "B" else prog
        if name not in on:
            seg = [(None, sum(b for _, b in seg))]
        out += seg
    return out


def melody(S, inst, secs, pa, pb, octave, vel, a2_octave=None, a2_inst=None, gap=4, **kw):
    """Phrase A then B in the theme, again (maybe higher, maybe another voice) in A2."""
    a0, b0 = secs["A"][0], secs["A2"][0]
    S.phrase(inst, pa, a0, octave=octave, vel=vel, **kw)
    S.phrase(inst, pb, a0 + gap, octave=octave, vel=vel, **kw)
    S.phrase(a2_inst or inst, pa, b0, octave=a2_octave if a2_octave is not None else octave, vel=vel, **kw)
    S.phrase(a2_inst or inst, pb, b0 + gap, octave=a2_octave if a2_octave is not None else octave, vel=vel * 0.95, **kw)


def t_bell_crypt(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(54, bars, "D3", "phrygian", tail=8.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (1, 2), (3, 2), (0, 2)], [(3, 2), (1, 2), (6, 2), (0, 2)]
    S.play("drone", S.t(0), M("D1"), S.t(bars) - 2, 0.3, send="cathedral", wet=0.2)
    S.bass("sub", span(secs, prog, prog_b, ("A", "A2")), 0, octave=-1, vel=0.14, send="cathedral", wet=0.2)
    S.pads("choir", span(secs, prog, prog_b, ("intro", "A", "A2", "outro")), 0, M("A3"), vel=0.15, send="cathedral", wet=0.55, vowel="o", a=2.5, r=3.0)
    S.pads("strings", span(secs, prog, prog_b, ("A", "A2")), 0, M("D4"), vel=0.1, send="cathedral", wet=0.5, bright=0.3)
    pa = [(0, 2), (4, 2), (5, 1.5), (4, 2.5)]          # D A Bb A: the Signal's sigh, tolled
    pb = [(2, 2), (1, 2), (0, 4)]                        # F Eb D
    S.phrase("bell", pa, secs["A"][0], octave=1, vel=0.42, send="cathedral", wet=0.45, t60=6.0, bright=0.45)
    S.phrase("bell", pb, secs["A"][0] + 4, octave=1, vel=0.4, send="cathedral", wet=0.45, t60=6.0, bright=0.45)
    S.phrase("glass", pa, secs["A2"][0], octave=2, vel=0.42, send="cathedral", wet=0.5, t60=4.0)
    S.phrase("glass", pb, secs["A2"][0] + 4, octave=2, vel=0.4, send="cathedral", wet=0.5, t60=4.0)
    for bar in range(secs["A2"][0], secs["A2"][0] + 8, 2):
        S.play("bell", S.t(bar), M("D2"), 1.0, 0.3, -0.2, "cathedral", 0.5, t60=7.0, bright=0.35)
    S.colour("fmbell", [0, 4, 5, 7], 16, secs["B"][0], per_bar=0.35, octave=2, vel=0.12, send="cathedral", wet=0.7, t60=3.0)
    S.phrase("bell", [(0, 4)], secs["outro"][0], octave=0, vel=0.35, send="cathedral", wet=0.5, t60=7.0)
    n = K.ns(S.length)
    S.bed(A.murk(n, seed + 1, 380) + 0.5 * A.wind(n, seed + 2, centre=300, q=1.5, gust=0.08), gain=0.5, fade_in=6, fade_out=8)
    return S.render(("cathedral",))


def t_fungal_hollow(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(66, bars, "F3", "lydian", beats=3, tail=7.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (1, 2), (0, 2), (1, 2)], [(5, 2), (1, 2), (2, 2), (1, 2)]
    S.bass("sub", span(secs, prog, prog_b), 0, octave=-1, vel=0.2, wet=0.2)
    S.pads("pad", span(secs, prog, prog_b), 0, M("A3"), stack=(0, 2, 4, 6), vel=0.17, wet=0.45, bright=0.22, a=2.0, r=3.0)
    S.arp("marimba", span(secs, prog, prog_b, ("A", "A2")), 0, M("C5"), [0, 1, 2, 3, 2, 1], step=0.5, vel=0.24,
          accent=[1, 0.6, 0.8, 0.6, 0.8, 0.6], wet=0.35, pan=0.25)
    pa = [(4, 1.5), (3, 0.5), (4, 1), (6, 3)]           # C B C E: the raised fourth floats
    pb = [(5, 1), (4, 1), (3, 1), (2, 3)]
    melody(S, "flute", secs, pa, pb, 1, 0.42, a2_octave=1, gap=4, wet=0.4, pan=-0.15, breath=0.28)
    S.colour("vibes", [0, 2, 3, 4, 6], 8, secs["B"][0], per_bar=0.6, octave=2, vel=0.12, grid=0.5, wet=0.6, t60=2.5)
    A.scatter(S, "bubble", 22, S.t(1), S.t(bars) - 2, 64, 80, vel=0.08, wet=0.4)
    n = K.ns(S.length)
    S.bed(A.murk(n, seed + 1, 600) + 0.3 * A.hiss(n, seed + 2, 4000, 9000), gain=0.35)
    return S.render(("hall",))


def t_ember_mines(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(60, bars, "E3", "hijaz", tail=7.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 4), (1, 2), (0, 2)], [(3, 4), (1, 2), (0, 2)]
    S.play("drone", S.t(0), M("E1"), S.t(bars) - 2, 0.32, send="cave", wet=0.2)
    S.play("drone", S.t(2), M("B1"), S.t(bars) - 4, 0.25, send="cave", wet=0.2)
    S.pads("strings", span(secs, prog, prog_b), 0, M("B3"), vel=0.15, send="cave", wet=0.45, bright=0.28, a=2.0, r=2.5)
    pa = [(0, 1), (1, 1), (2, 1), (3, 1), (2, 2), (1, 1), (0, 1)]   # E F G# A G# F E
    pb = [(4, 2), (5, 1), (4, 1), (2, 2), (1, 2)]                   # B C B G# F
    melody(S, "pluck", secs, pa, pb, 1, 0.5, a2_octave=1, send="cave", wet=0.35, pan=0.2, bright=0.45, metal=0.35, t60=1.8)
    S.phrase("brass", [(0, 4), (1, 2), (0, 2)], secs["A2"][0] + 4, octave=0, vel=0.32, send="cave", wet=0.4)
    S.beats_at("clank", [(0, 1.0), (0.5, 0.7)], 8, secs["B"][0], M("E3"), vel=0.16, send="cave", wet=0.6, every=2)
    S.beats_at("clank", [(0, 1.0), (0.5, 0.7)], 8, secs["A2"][0], M("E3"), vel=0.12, send="cave", wet=0.6, every=2)
    S.beats_at("drum", [(0, 1.0)], 8, secs["A2"][0], M("E1"), vel=0.3, send="cave", wet=0.35)
    n = K.ns(S.length)
    embers = np.stack([K.crackle(n, 6, seed + 3, 0.5, 0.6), K.crackle(n, 6, seed + 4, 0.5, 0.6)], axis=1) * 0.08
    S.bed(A.murk(n, seed + 1, 260) * 1.2 + embers, gain=0.5)
    return S.render(("cave",))


def t_drowned_archive(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(58, bars, "A2", "aeolian", tail=8.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (5, 2), (2, 2), (6, 2)], [(3, 2), (5, 2), (0, 2), (4, 2)]
    S.bass("sub", span(secs, prog, prog_b), 0, octave=0, vel=0.2, send="cave", wet=0.2)
    S.arp("piano", span(secs, prog, prog_b, ("A", "B", "A2")), 0, M("E4"), [0, 2, 1, 3, 2, 1, None, None], step=0.5,
          vel=0.24, accent=[1, 0.7, 0.8, 0.7, 0.9, 0.6, 1, 1], send="cave", wet=0.45, felt=0.92)
    S.pads("strings", span(secs, prog, prog_b), 0, M("C4"), vel=0.09, send="cave", wet=0.5, bright=0.25, a=2.5, r=3.0)
    pa = [(4, 1), (3, 1), (2, 2), (1, 1), (2, 3)]        # E D C B C, sinking
    pb = [(2, 1), (1, 1), (0, 2), (-1, 1), (0, 3)]       # C B A G A
    melody(S, "piano", secs, pa, pb, 2, 0.42, a2_octave=2, a2_inst="glass", send="cave", wet=0.45, felt=0.85)
    A.scatter(S, "drip", 30, S.t(1), S.t(bars) - 2, 82, 96, vel=0.1, send="cave", wet=0.5)
    A.scatter(S, "bubble", 10, S.t(2), S.t(bars) - 4, 55, 70, vel=0.06, send="cave", wet=0.4)
    n = K.ns(S.length)
    S.bed(A.murk(n, seed + 1, 340) + 0.6 * A.wind(n, seed + 2, centre=220, q=1.0, gust=0.06), gain=0.55)
    return S.render(("cave",))


def t_clockwork_foundry(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(72, bars, "C3", "dorian", tail=6.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (3, 2), (0, 2), (6, 2)], [(4, 2), (3, 2), (2, 2), (3, 2)]
    ticks = [(b * 0.5, (1.0 if b % 2 == 0 else 0.55) * (1.2 if b == 0 else 1.0)) for b in range(8)]
    S.beats_at("tick", ticks, bars - 1, 0, M("G6"), vel=0.1, send="room", wet=0.25, pan=0.3)
    S.beats_at("tick", [(0.75, 0.8), (2.75, 0.8)], bars - 1, 0, M("C6"), vel=0.08, send="room", wet=0.25, pan=-0.3, wood=True)
    S.arp("pluck", span(secs, prog, prog_b, ("A", "B", "A2")), 0, M("C2"), [0, None, None, None, 0, None, 2, None],
          step=0.5, vel=0.4, send="room", wet=0.2, bright=0.35, t60=1.0)
    S.arp("musicbox", span(secs, prog, prog_b, ("A", "A2")), 0, M("C5"), [0, 1, 2, 3, 2, 1, 0, 1], step=0.5, vel=0.16,
          accent=[1, 0.7, 0.8, 0.7], wet=0.35, pan=-0.2)
    S.pads("brass", span(secs, prog, prog_b, ("B", "A2")), 0, M("G3"), vel=0.12, wet=0.4, a=0.6, r=1.2)
    S.pads("strings", span(secs, prog, prog_b), 0, M("Eb4"), vel=0.08, wet=0.4, bright=0.3)
    pa = [(0, 2), (4, 2), (5, 1), (4, 3)]                 # C G A G: the sigh with Dorian's bright sixth
    pb = [(2, 2), (3, 1), (2, 1), (1, 2), (0, 2)]         # Eb F Eb D C
    melody(S, "brass", secs, pa, pb, 1, 0.34, a2_octave=1, wet=0.4, pan=0.1)
    S.colour("clank", [0, 4], 8, secs["B"][0], per_bar=0.3, octave=0, vel=0.08, wet=0.6)
    n = K.ns(S.length)
    steam = A.hiss(n, seed + 3, 2500, 9000, 0.8) * (0.4 + 0.6 * np.clip(K.smooth_noise(n, 0.12, seed + 4), 0, 1))[:, None]
    S.bed(A.murk(n, seed + 1, 300) * 0.8 + steam * 0.6, gain=0.4)
    return S.render(("room", "hall"))


def t_glass_desert(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(50, bars, "E3", "phrygian", tail=8.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 4), (1, 2), (0, 2)], [(6, 4), (5, 2), (0, 2)]
    S.pads("harmonica", span(secs, prog, prog_b), 0, M("B4"), stack=(0, 4), vel=0.2, send="air", wet=0.5)
    S.arp("pluck", span(secs, prog, prog_b, ("A", "B", "A2")), 0, M("E3"), [0, None, 1, None, 0, None, None, None],
          step=0.5, vel=0.32, stack=(0, 4), send="air", wet=0.3, bright=0.4, metal=0.35, t60=2.0, pan=-0.2)
    S.bass("sub", span(secs, prog, prog_b), 0, octave=-1, vel=0.15, send="air", wet=0.1)
    pa = [(0, 2), (4, 2), (5, 2), (4, 4)]                 # E B C B
    pb = [(7, 2), (6, 1), (5, 1), (4, 4)]                 # E D C B
    melody(S, "harmonica", secs, pa, pb, 1, 0.34, a2_octave=2, send="air", wet=0.5, pan=0.15)
    S.colour("glass", [0, 4, 1, 7], 24, secs["A"][0], per_bar=0.3, octave=2, vel=0.16, send="air", wet=0.7, t60=5.0, bright=0.6)
    n = K.ns(S.length)
    S.bed(A.wind(n, seed + 1, centre=1100, q=0.6, gust=0.1) * 0.9 + 0.3 * A.hiss(n, seed + 2, 5000, 12000), gain=0.45)
    return S.render(("air",))


def _buzz(n, f0, seed):
    """Wings: a soft saw drone with a fast flutter."""
    amps = K.saw_amps(f0 * 1.01, bright=0.15, tilt=1.2, cap=60, f0=f0)
    x = K.wt_voice(f0, n, amps, 2 ** (K.smooth_noise(n, 0.5, seed) * 8 / 1200))
    flutter = 0.65 + 0.35 * np.sin(2 * np.pi * (17 + 2 * K.smooth_noise(n, 0.3, seed + 1)) * K.tvec(n))
    return K.lowpass(x * flutter, 1300, 2)


def t_hive(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(76, bars, "F2", "aeolian", tail=6.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (5, 2), (6, 2), (0, 2)], [(3, 2), (5, 2), (4, 2), (6, 2)]
    n = K.ns(S.length)
    S.bed(np.stack([_buzz(n, float(K.hz(M("F2"))), seed), _buzz(n, float(K.hz(M("C3"))), seed + 7)], axis=1),
          gain=0.12, fade_in=6.0, fade_out=6.0, send="room", wet=0.3)
    S.pads("strings", span(secs, prog, prog_b), 0, M("Ab3"), vel=0.13, wet=0.4, bright=0.35)
    S.arp("pluck", span(secs, prog, prog_b, ("A", "B", "A2")), 0, M("C4"), [0, 1, 2, 3, 2, 1], step=0.5, vel=0.2,
          accent=[1, 0.6, 0.75, 0.9, 0.7, 0.6], wet=0.3, pan=0.3, bright=0.55, t60=0.9)
    S.bass("sub", span(secs, prog, prog_b), 0, octave=0, vel=0.18, wet=0.1)
    pa = [(4, 1), (5, 1), (4, 1), (2, 1), (0, 4)]          # C Db C Ab F
    pb = [(2, 1), (3, 1), (4, 2), (3, 1), (2, 1), (1, 2)]  # Ab Bb C Bb Ab G
    melody(S, "marimba", secs, pa, pb, 2, 0.4, a2_octave=2, wet=0.35, pan=-0.2)
    A.scatter(S, "drip", 16, S.t(1), S.t(bars) - 2, 66, 76, vel=0.09, send="room", wet=0.4)
    return S.render(("hall", "room"))


def t_overgrown_temple(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(60, bars, "G2", "dorian", tail=7.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (3, 2), (0, 2), (6, 2)], [(2, 2), (3, 2), (4, 2), (3, 2)]
    S.pads("pad", span(secs, prog, prog_b), 0, M("D4"), vel=0.16, wet=0.45, bright=0.25)
    S.bass("sub", span(secs, prog, prog_b), 0, octave=0, vel=0.2, wet=0.15)
    S.arp("marimba", span(secs, prog, prog_b, ("A2",)), 0, M("G4"), [0, 2, 1, 3, None, 2, 1, None], step=0.5,
          vel=0.18, wet=0.35, pan=0.3, t60=0.8)
    pa = [(4, 2), (5, 1), (4, 1), (3, 2), (2, 2)]         # D E D C Bb
    pb = [(0, 1), (2, 1), (4, 2), (5, 2), (4, 2)]         # G Bb D E D
    melody(S, "flute", secs, pa, pb, 2, 0.4, a2_octave=2, wet=0.4, pan=-0.2, breath=0.3)
    S.beats_at("frame", [(0, 1.0), (2.5, 0.6)], 8, secs["A2"][0], M("G2"), vel=0.2, send="room", wet=0.3)
    A.scatter(S, "chirp", 18, S.t(1), S.t(bars) - 2, 96, 104, vel=0.05, wet=0.5)
    n = K.ns(S.length)
    S.bed(A.wind(n, seed + 1, centre=520, q=0.8, gust=0.1), gain=0.35)
    return S.render(("hall", "room"))


def t_lantern_night_market(seed):
    secs, bars = form(2, 16, 8, 16, 3)
    S = A.Score(84, bars, "D3", "ionian", tail=6.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (5, 2), (3, 2), (4, 2)] * 2, [(3, 2), (4, 2), (2, 2), (5, 2)]
    full = span(secs, prog, prog_b)
    S.arp("piano", full, 0, M("A3"), [0, 2, 1, 2], step=1.0, vel=0.2, accent=[1, 0.7, 0.85, 0.7], wet=0.3, felt=0.7)
    S.arp("pluck", span(secs, prog, prog_b, ("A", "B", "A2")), 0, M("D2"), [0, None, None, None, 2, None, None, None],
          step=0.5, vel=0.38, send="room", wet=0.2, bright=0.35, t60=1.2)
    S.beats_at("frame", [(0, 1.0), (2, 0.6), (3.5, 0.4)], 8, secs["A2"][0], M("D2"), vel=0.22, send="room", wet=0.25)
    S.beats_at("shaker", [(b * 0.5, 1.0 if b % 2 else 0.6) for b in range(8)], 8, secs["A2"][0], 60, vel=0.25,
               send="room", wet=0.2, pan=0.35)
    pa = [(4, 1), (5, 0.5), (4, 0.5), (2, 1), (1, 1), (0, 2), (None, 2)]   # A B A F# E D
    pb = [(2, 1), (4, 1), (5, 1), (7, 1), (5, 1.5), (4, 0.5), (2, 2)]     # F# A B D B A F#
    melody(S, "koto", secs, pa, pb, 1, 0.48, a2_octave=1, gap=4, wet=0.35, pan=0.15, t60=2.0)
    for b in (secs["A"][0] + 8, secs["A2"][0] + 8):   # the second half of each long theme: the tune, answered higher
        S.phrase("koto", pb, b, octave=1, vel=0.44, wet=0.35, pan=0.15, t60=2.0)
        S.phrase("koto", pa, b + 4, octave=2, vel=0.32, wet=0.4, pan=-0.3, t60=1.8)
    S.colour("fmbell", [0, 2, 4, 5], 8, secs["B"][0], per_bar=0.6, octave=2, vel=0.1, grid=0.5, wet=0.5, t60=2.0)
    S.phrase("koto", [(0, 4)], secs["outro"][0], octave=1, vel=0.35, wet=0.5)
    return S.render(("room", "hall"))


def t_frost_monastery(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(50, bars, "B2", "aeolian", tail=9.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 4), (5, 2), (0, 2)], [(3, 4), (4, 2), (0, 2)]
    S.play("choir", S.t(0), M("B2"), S.t(bars) - 3, 0.12, send="cathedral", wet=0.5, vowel="m", a=4.0, r=5.0)
    S.play("choir", S.t(1), M("F#3"), S.t(bars) - 5, 0.08, send="cathedral", wet=0.5, vowel="m", a=4.0, r=5.0)
    S.pads("strings", span(secs, prog, prog_b, ("A2", "outro")), 0, M("F#5"), vel=0.05, send="cathedral", wet=0.6,
           bright=0.5, a=3.0, r=4.0)
    pa = [(0, 2), (2, 2), (3, 2), (2, 2)]                 # B D E D: a chant
    pb = [(0, 1), (2, 1), (4, 2), (3, 2), (2, 1), (0, 1)] # B D F# E D B
    melody(S, "choir", secs, pa, pb, 1, 0.22, a2_octave=1, send="cathedral", wet=0.5, vowel="o", a=0.35, r=1.2)
    S.phrase("choir", pa, secs["A2"][0], octave=0, vel=0.14, send="cathedral", wet=0.5, vowel="o", a=0.35, r=1.2)
    S.colour("fmbell", [0, 2, 4, 7], 24, secs["A"][0], per_bar=0.35, octave=3, vel=0.08, send="cathedral", wet=0.7,
             t60=3.0, ratio=3.5)
    n = K.ns(S.length)
    S.bed(A.wind(n, seed + 1, centre=2200, q=0.5, gust=0.08) * 0.7 + 0.25 * A.hiss(n, seed + 2, 6000, 13000), gain=0.35)
    return S.render(("cathedral",))


def t_mirror_palace(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(64, bars, "C3", "lydian", tail=8.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (1, 2), (0, 2), (1, 2)], [(5, 2), (1, 2), (4, 2), (1, 2)]
    S.pads("harmonica", span(secs, prog, prog_b), 0, M("G4"), stack=(0, 2, 4, 6), vel=0.12, send="air", wet=0.5)
    S.bass("sub", span(secs, prog, prog_b), 0, octave=0, vel=0.14, send="air", wet=0.1)
    pa = [(0, 1), (2, 1), (3, 1), (4, 2), (3, 1), (2, 1), (0, 1)]   # C E F# G F# E C: the same both ways
    pb = [(4, 1), (6, 1), (7, 2), (6, 1), (4, 1), (2, 2)]
    for sec, octv in (("A", 2), ("A2", 2)):
        b0 = secs[sec][0]
        S.phrase("glass", pa, b0, octave=octv, vel=0.4, send="air", wet=0.5, pan=-0.5, t60=2.5)
        S.phrase("glass", pa, b0, beat=0.5, octave=octv + 1, vel=0.18, send="air", wet=0.6, pan=0.5, t60=2.0)  # the reflection
        S.phrase("glass", pb, b0 + 4, octave=octv, vel=0.38, send="air", wet=0.5, pan=0.5, t60=2.5)
        S.phrase("glass", pb, b0 + 4, beat=0.5, octave=octv + 1, vel=0.17, send="air", wet=0.6, pan=-0.5, t60=2.0)
    for bar in range(secs["A2"][0] + 2, secs["A2"][0] + 8, 2):
        riser(S, [("glass", M_name(S.deg(1, 2)), 0.4), ("glass", M_name(S.deg(5, 2)), 0.35)], S.t(bar), 2.0, room="air", gain=0.25)
    S.colour("fmbell", [0, 3, 4, 6], 8, secs["B"][0], per_bar=0.5, octave=3, vel=0.08, wet=0.7, t60=2.5)
    n = K.ns(S.length)
    S.bed(A.hiss(n, seed + 1, 7000, 14000, 0.6), gain=0.12)
    return S.render(("air",))


def M_name(m):
    names = ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
    return names[m % 12] + str(m // 12 - 1)


def t_precursor_lab(seed):
    secs, bars = form(2, 16, 8, 16, 3)
    S = A.Score(90, bars, "F#2", "aeolian", tail=6.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (5, 2), (2, 2), (6, 2)] * 2, [(3, 2), (5, 2), (0, 2), (4, 2)]
    S.arp("fmbell", span(secs, prog, prog_b, ("A", "B", "A2")), 0, M("C#5"), [0, 1, 2, 3, 2, 1, 2, 3], step=0.25,
          vel=0.1, accent=[1, 0.6, 0.8, 0.6], wet=0.25, pan=0.3, t60=0.45, index=0.9, ratio=2.0, send="room")
    S.beats_at("drum", [(0, 1.0), (2, 0.7)], bars - 3, 2, M("F#1"), vel=0.28, send="room", wet=0.15)
    S.pads("pad", span(secs, prog, prog_b), 0, M("C#4"), vel=0.14, wet=0.4, bright=0.35)
    S.bass("sub", span(secs, prog, prog_b), 0, octave=0, vel=0.2, wet=0.1)
    pa = [(0, 1), (4, 1), (5, 1.5), (4, 2.5)]              # F# C# D C#: the sigh, in signal blue
    pb = [(2, 1), (4, 1), (6, 1), (7, 1), (6, 2), (4, 2)]  # A C# E F# E C#
    melody(S, "glass", secs, pa, pb, 3, 0.42, a2_octave=3, wet=0.4, pan=-0.2, t60=2.0)
    for b in (secs["A"][0] + 8, secs["A2"][0] + 8):
        S.phrase("glass", pa, b, octave=2, vel=0.34, wet=0.45, pan=0.25, t60=2.0)
        S.phrase("piano", pb, b + 4, octave=2, vel=0.3, wet=0.35, pan=-0.1, felt=0.5)
    A.scatter(S, "chirp", 14, S.t(2), S.t(bars) - 2, 88, 100, vel=0.04, send="room", wet=0.3)
    n = K.ns(S.length)
    t = K.tvec(n)
    hum = (np.sin(2 * np.pi * 100 * t) + 0.4 * np.sin(2 * np.pi * 150 * t)) * 0.04
    S.bed(np.stack([hum, hum], axis=1) + A.hiss(n, seed + 1, 5000, 11000, 0.3) * 0.3, gain=0.5)
    return S.render(("room", "hall"))


def t_nightmare(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(52, bars, "C#3", "aeolian", tail=8.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (5, 2), (0, 2), (1, 2)], [(3, 2), (1, 2), (5, 2), (1, 2)]
    S.pads("strings", span(secs, prog, prog_b), 0, M("E3"), stack=(0, 1, 2), vel=0.1, send="cave", wet=0.5, bright=0.2,
           a=3.0, r=3.5)
    S.play("drone", S.t(0), M("C#1"), S.t(bars) - 2, 0.28, send="cave", wet=0.3)
    pa = [(0, 1), (2, 1), (4, 1), (2, 1), (0, 1), (2, 1), ("+6", 1), (2, 1)]   # a lullaby, one note wrong
    pb = [(1, 1), (0, 1), (-1, 1), (0, 3)]
    melody(S, "musicbox", secs, pa, pb, 2, 0.4, a2_octave=2, send="cave", wet=0.45, pan=0.2, detune=28)
    for bar in range(secs["A2"][0], secs["A2"][0] + 8, 2):
        riser(S, [("piano", M_name(S.deg(0, 1)), 0.5), ("piano", M_name(S.deg(4, 1)), 0.4)], S.t(bar), 2.5, room="cave", gain=0.3)
    for k in range(0, bars - 2, 2):
        S.play("drum", S.t(k), M("C#1"), 0.2, 0.25, 0.0, "cave", 0.4)
        S.play("drum", S.t(k) + 0.3, M("C#1"), 0.2, 0.15, 0.0, "cave", 0.4)
    A.scatter(S, "whisper", 9, S.t(2), S.t(bars) - 4, 40, 50, vel=0.5, send="cave", wet=0.5, spread=0.9)
    n = K.ns(S.length)
    S.bed(A.murk(n, seed + 1, 300), gain=0.5)
    return S.render(("cave",))


def t_sky_archipelago(seed):
    secs, bars = form(2, 8, 8, 8, 3)
    S = A.Score(66, bars, "D3", "lydian", tail=8.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (1, 2), (0, 2), (1, 2)], [(5, 2), (4, 2), (1, 2), (0, 2)]
    S.pads("strings", span(secs, prog, prog_b), 0, M("A4"), vel=0.12, send="air", wet=0.5, bright=0.55, a=2.0, r=3.0)
    S.pads("pad", span(secs, prog, prog_b), 0, M("D4"), stack=(0, 4), vel=0.12, send="air", wet=0.45, bright=0.3)
    S.bass("sub", span(secs, prog, prog_b), 0, octave=-1, vel=0.14, send="air", wet=0.1)
    pa = [(4, 2), (3, 1), (4, 1), (6, 4)]                 # A G# A C#
    pb = [(7, 2), (6, 1), (4, 1), (5, 2), (4, 2)]         # D C# A B A
    melody(S, "flute", secs, pa, pb, 2, 0.4, a2_octave=2, send="air", wet=0.45, pan=0.2, breath=0.24)
    S.arp("glass", span(secs, prog, prog_b, ("A2",)), 0, M("A5"), [0, None, 2, None, 1, None, 3, None], step=0.5,
          vel=0.14, send="air", wet=0.5, pan=-0.3, t60=1.8)
    S.colour("glass", [0, 1, 4, 7], 8, secs["B"][0], per_bar=0.5, octave=3, vel=0.1, send="air", wet=0.7, t60=2.5)
    n = K.ns(S.length)
    S.bed(A.wind(n, seed + 1, centre=850, q=0.6, gust=0.12), gain=0.55)
    return S.render(("air",))


def t_descent(seed):
    secs, bars = form(3, 8, 8, 8, 4)
    S = A.Score(60, bars, "A2", "dorian", tail=9.0, seed=seed)
    breathe(S, secs)
    prog, prog_b = [(0, 2), (3, 2), (5, 2), (0, 2)], [(2, 2), (6, 2), (3, 2), (4, 2)]
    S.play("sub", S.t(0), M("A1"), S.t(bars) - 2, 0.09, send="cave", wet=0.2, a=4.0, r=5.0)
    S.pads("piano", span(secs, prog, prog_b, ("intro", "A", "A2", "outro")), 0, M("E3"), stack=(0, 4), vel=0.22,
           send="cave", wet=0.45, overlap=0.0, felt=0.8)
    S.pads("strings", span(secs, prog, prog_b, ("B", "A2")), 0, M("C4"), vel=0.06, send="cave", wet=0.55, bright=0.25,
           a=3.0, r=4.0)
    pa = [(4, 1), (3, 1), (2, 2), (None, 4)]              # E D C, then nothing
    pb = [(1, 1), (2, 1), (5, 2), (4, 4)]                 # B C F# E: Dorian's sixth, hopeful
    pc = [(2, 1), ("+6", 1), (4, 3)]                      # C D# E: the tritone leans in, then lets go
    S.phrase("piano", pa, secs["A"][0], octave=2, vel=0.4, send="cave", wet=0.45, felt=0.75)
    S.phrase("piano", pb, secs["A"][0] + 4, octave=2, vel=0.38, send="cave", wet=0.45, felt=0.75)
    S.phrase("piano", [(4, 4)], secs["B"][0] + 3, octave=3, vel=0.18, send="cathedral", wet=0.6, felt=0.75)
    S.phrase("piano", pa, secs["A2"][0], octave=2, vel=0.38, send="cave", wet=0.45, felt=0.75)
    S.phrase("piano", pc, secs["A2"][0] + 4, octave=2, vel=0.36, send="cave", wet=0.45, felt=0.75)
    for bar in range(secs["A"][0] + 1, secs["outro"][0], 4):
        S.play("glass", S.t(bar, 2), S.deg(7, 2), 1.0, 0.08, 0.4, "cathedral", 0.8, t60=4.0)
    S.phrase("piano", [(0, 4)], secs["outro"][0] + 1, octave=1, vel=0.3, send="cave", wet=0.5, felt=0.8)
    n = K.ns(S.length)
    S.bed(A.murk(n, seed + 1, 320), gain=0.45, fade_in=8.0, fade_out=8.0)
    return S.render(("cave", "cathedral"))


# id: (render, title, subtitle, the vanilla music the biome had (kept as a rarer alternative))
TRACKS = {
    "gate_bell_crypt": (t_bell_crypt, "Bell Crypt", "minecraft:music.overworld.deep_dark"),
    "gate_fungal_hollow": (t_fungal_hollow, "Fungal Hollow", "minecraft:music.overworld.lush_caves"),
    "gate_ember_mines": (t_ember_mines, "Ember Mines", "minecraft:music.nether.basalt_deltas"),
    "gate_drowned_archive": (t_drowned_archive, "Drowned Archive", "minecraft:music.under_water"),
    "gate_clockwork_foundry": (t_clockwork_foundry, "Clockwork Foundry", "minecraft:music.overworld.dripstone_caves"),
    "gate_glass_desert": (t_glass_desert, "Glass Desert", "minecraft:music.overworld.desert"),
    "gate_hive": (t_hive, "Hive", "minecraft:music.overworld.flower_forest"),
    "gate_overgrown_temple": (t_overgrown_temple, "Overgrown Temple", "minecraft:music.overworld.jungle"),
    "gate_lantern_night_market": (t_lantern_night_market, "Lantern Night Market", "minecraft:music.overworld.cherry_grove"),
    "gate_frost_monastery": (t_frost_monastery, "Frost Monastery", "minecraft:music.overworld.frozen_peaks"),
    "gate_mirror_palace": (t_mirror_palace, "Mirror Palace", "minecraft:music.end"),
    "gate_precursor_lab": (t_precursor_lab, "Precursor Lab", "minecraft:music.end"),
    "gate_nightmare": (t_nightmare, "Nightmare", "minecraft:music.overworld.deep_dark"),
    "gate_sky_archipelago": (t_sky_archipelago, "Sky Archipelago", "minecraft:music.overworld.meadow"),
    "descent": (t_descent, "The Long Way Down", None),
}

# each stratum keeps its old vanilla music as the rarer alternative to the Descent track
STRATA_VANILLA = {1: "minecraft:music.overworld.lush_caves", 2: "minecraft:music.overworld.flower_forest",
                  3: "minecraft:music.under_water", 4: "minecraft:music.nether.soul_sand_valley",
                  5: "minecraft:music.overworld.deep_dark", 6: "minecraft:music.nether.basalt_deltas",
                  7: "minecraft:music.under_water", 8: "minecraft:music.overworld.cherry_grove",
                  9: "minecraft:music.nether.crimson_forest", 10: "minecraft:music.end"}

# gates: a track every 3-10 minutes (vanilla's own is 10-20); the Descent keeps its 5-15
GATE_DELAY = (3600, 12000)
DESCENT_DELAY = (6000, 18000)

LANG = {}
BIOME_MUSIC = {}
for _tid in TRACKS:
    if _tid.startswith("gate_"):
        BIOME_MUSIC[_tid] = {"default": {"sound": "awaken:music." + _tid.replace("_", ".", 1),
                                         "min_delay": GATE_DELAY[0], "max_delay": GATE_DELAY[1]}}
for _s in STRATA_VANILLA:
    BIOME_MUSIC["stratum_%d" % _s] = {"default": {"sound": "awaken:music.descent.%d" % _s,
                                                  "min_delay": DESCENT_DELAY[0], "max_delay": DESCENT_DELAY[1]}}


def sounds(Snd):
    out = []
    for i, (cid, (fn, title, _)) in enumerate(CUES.items()):
        out.append(Snd("music_cue_" + cid, fn, title="AWAKEN - " + title, seed=100 + i))
    for i, (tid, (fn, title, _)) in enumerate(TRACKS.items()):
        out.append(Snd("music_" + tid, fn, title="AWAKEN - " + title, seed=200 + i, quality=2))
    return out


def events(Evt):
    out = []
    for cid, (fn, title, sub) in CUES.items():
        out.append(Evt("music.cue." + cid, sub, files=["music_cue_" + cid], stream=True))
        LANG["awaken.music_cue_" + cid] = "AWAKEN - " + title
    for tid, (fn, title, vanilla) in TRACKS.items():
        LANG["awaken.music_" + tid] = "AWAKEN - " + title
        if tid.startswith("gate_"):
            out.append(Evt("music." + tid.replace("_", ".", 1), title + " music plays", files=[("music_" + tid, 3)],
                           refs=[(vanilla, 1)], stream=True))
    for s, vanilla in STRATA_VANILLA.items():
        out.append(Evt("music.descent.%d" % s, "Music of the Descent plays", files=[("music_descent", 2)],
                       refs=[(vanilla, 1)], stream=True))
    return out


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
