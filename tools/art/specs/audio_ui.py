"""The System's UI family (tools/art/audio_build.py renders it).

One voice for everything the System says: a struck glass (few slightly split, inharmonic modes and a short bright
tick), a warm sine core under it and a faint FM glint, all in one small bright room. Every note comes from D major
pentatonic (D E F# A B), so two System sounds overlapping never clash; only the warning leaves it (a minor second, the
one sound that should feel wrong). Short, soft, readable at a glance: the toast is a two-note "tink", a decision rises
and stays open, a level climbs, a rank blooms.
"""
import numpy as np

import audiokit as K

_IR = {}


def room(kind="small"):
    if kind not in _IR:
        if kind == "small":
            _IR[kind] = K.reverb_ir(t60=0.95, predelay=0.008, bright=0.7, width=0.9, early=0.6, size=0.5, seed=31)
        else:
            _IR[kind] = K.reverb_ir(t60=2.3, predelay=0.02, bright=0.6, width=1.0, early=0.4, size=1.0, seed=32)
    return _IR[kind]


def glass_note(m, dur, vel=0.6, bright=0.55, t60=1.2, seed=0, core=0.35, glint=0.08):
    """One System note: glass + sine core + FM glint."""
    f = float(K.hz(K.midi(m)))
    n = K.ns(dur)
    g = K.glass(f, n, bright, t60, seed, hit=0.6 * vel)
    c = K.sine(f, n, 0.25) * K.perc(n, 0.004, t60 * 1.25)
    s = K.fm(2 * f, 1.5, 0.9, n, index_env=K.perc(n, 0.001, 0.12), amp_env=K.perc(n, 0.001, t60 * 0.35))
    return (g + core * c + glint * bright * s) * vel


def notes(seq, length, wet=0.22, ir="small", lp=None, spread=0.25):
    """seq: (time, pitch, vel, t60[, bright]) -> stereo with room."""
    mix = K.Mix(length)
    for i, item in enumerate(seq):
        t, m, vel, t60 = item[:4]
        bright = item[4] if len(item) > 4 else 0.55
        x = glass_note(m, length - t, vel, bright, t60, seed=int(K.midi(m)) * 31 + i)
        p = spread * np.sin(1.7 * i + 0.4)
        mix.add(x, t, 1.0, p, "room", wet)
    y = mix.render({"room": room(ir)}, tail=0.0)
    if lp:
        y = K.lowpass(y, lp, 2)
    return y


def air(length, at, rise, fc=5200, level=0.04, seed=5):
    """A breath of air swelling into `at` (a window appearing)."""
    n = K.ns(length)
    x = np.zeros(n)
    k = K.ns(rise)
    s = K.ns(at) - k
    if s < 0:
        k += s
        s = 0
    seg = K.bandnoise(k + K.ns(0.12), fc, 0.8, seed)
    env = np.concatenate([K._rise(k) ** 2, np.exp(-np.arange(K.ns(0.12)) / K.ns(0.03))])
    x[s:s + len(seg)] = (seg * env)[: n - s]
    return K.pan(x * level, 0.0)


def warm(m, length, at, level, t60=1.6, attack=0.02):
    """A soft low sine bloom (warmth under a bright figure)."""
    n = K.ns(length)
    f = float(K.hz(K.midi(m)))
    x = np.zeros(n)
    s = K.ns(at)
    seg = (K.sine(f, n - s) + 0.25 * K.sine(2 * f, n - s)) * K.perc(n - s, attack, t60)
    x[s:] = seg
    return K.pan(x * level, 0.0)


# ---- the sounds --------------------------------------------------------------------------------------------------

def toast(v):
    a, b = (("A5", "D6"), ("F#5", "A5"))[v]
    return lambda seed: K.declick(notes([(0.0, a, 0.32, 0.6), (0.06, b, 0.6, 0.95)], 1.05))


def card(seed):
    y = notes([(0.0, "D5", 0.42, 0.8), (0.085, "A5", 0.5, 0.9), (0.17, "E6", 0.62, 1.35, 0.6)], 1.5)
    return y + air(1.5, 0.17, 0.22, level=0.05)


def warning(seed):
    # two low pulses, each a minor second (the only dissonance in the family), the second a step lower
    y = notes([(0.0, "A4", 0.55, 0.55, 0.35), (0.004, "Bb4", 0.5, 0.55, 0.35),
               (0.21, "G4", 0.55, 0.65, 0.35), (0.214, "Ab4", 0.5, 0.65, 0.35)], 1.1, wet=0.18, lp=4200)
    thump = warm("D3", 1.1, 0.0, 0.35, t60=0.25, attack=0.004) + warm("C3", 1.1, 0.21, 0.3, t60=0.3, attack=0.004)
    return y + thump


def answer_yes(seed):
    return notes([(0.0, "A5", 0.45, 0.6), (0.018, "D6", 0.6, 0.75, 0.6)], 0.85)


def answer_no(seed):
    return notes([(0.0, "F#5", 0.45, 0.45, 0.4), (0.07, "D5", 0.42, 0.55, 0.35)], 0.75, lp=3200)


def story(seed):
    """The Signal's voice arrives: a reversed glass swell that opens into a pure fifth."""
    L = 1.6
    src = K.Mix(1.2)
    for i, m in enumerate(("D6", "A6")):
        src.add(glass_note(m, 1.2, 0.5, 0.5, 0.9, seed=i), 0.0, 1.0, 0.0, "room", 0.6)
    sw = src.render({"room": room("small")})[::-1]
    sw = K.pad_to(sw, K.ns(L)) * 0.6
    n = K.ns(L)
    bloom = np.zeros((n, 2))
    s = K.ns(1.2)
    for m, p in (("D6", -0.3), ("A6", 0.3)):
        f = float(K.hz(K.midi(m)))
        seg = K.sine(f, n - s) * K.perc(n - s, 0.01, 0.5)
        bloom[s:] += K.pan(seg, p) * 0.18
    return sw + bloom + air(L, 1.2, 0.9, fc=6500, level=0.025, seed=9)


def now(seed):
    return notes([(0.0, "E6", 0.4, 0.45, 0.5)], 0.55, wet=0.15)


def level_up(seed):
    seq = [(0.0, "D5", 0.42, 1.2), (0.055, "F#5", 0.5, 1.2), (0.11, "A5", 0.58, 1.3), (0.165, "D6", 0.68, 1.7, 0.6),
           (0.32, "A6", 0.14, 0.6, 0.7), (0.4, "D7", 0.11, 0.5, 0.7), (0.47, "F#7", 0.08, 0.45, 0.7)]
    y = notes(seq, 2.3, wet=0.26)
    return y + warm("D3", 2.3, 0.15, 0.22, t60=1.6, attack=0.05) + warm("A3", 2.3, 0.17, 0.12, t60=1.4, attack=0.05)


def rank_up(seed):
    L = 4.3
    big = room("big")
    # a reversed chord swells in...
    src = K.Mix(1.0)
    for i, m in enumerate(("D5", "A5", "E6")):
        src.add(glass_note(m, 1.0, 0.5, 0.5, 0.8, seed=40 + i), 0, 1, 0, "r", 0.7)
    rise = K.pad_to(src.render({"r": big})[::-1][-K.ns(0.6):], K.ns(L)) * 0.5
    # ...into a bloom, then a climbing glint
    T = 0.6
    chord = [(T + 0.000, "D4", 0.55, 2.8, 0.4), (T + 0.012, "A4", 0.5, 2.6, 0.45), (T + 0.024, "D5", 0.55, 2.6),
             (T + 0.036, "A5", 0.55, 2.4), (T + 0.048, "E6", 0.5, 2.2, 0.6), (T + 0.06, "F#6", 0.42, 2.0, 0.6)]
    climb = [(T + 0.16 + 0.07 * i, m, 0.32 - 0.03 * i, 0.9, 0.7) for i, m in enumerate(("A5", "D6", "E6", "A6", "D7"))]
    mix = K.Mix(L)
    for i, (t, m, vel, t60, *b) in enumerate(chord + climb):
        x = glass_note(m, L - t, vel, b[0] if b else 0.55, t60, seed=50 + i)
        mix.add(x, t, 1.0, 0.35 * np.sin(1.3 * i), "big", 0.3)
    n = K.ns(L)
    ch = np.zeros(n)
    s = K.ns(T)
    for m in ("D4", "A4"):
        seg = K.choir(float(K.hz(K.midi(m))), n - s, "a", voices=5, seed=K.midi(m), bright=0.35)
        ch[s:] += seg * K.adsr(n - s, 0.35, 0.4, 0.7, 2.2)
    mix.add(ch * 0.05, 0, 1.0, 0.0, "big", 0.5)
    y = mix.render({"big": big})
    sub = warm("D2", L, T, 0.32, t60=2.4, attack=0.06)
    return rise + y + sub


def quest_done(seed):
    y = notes([(0.0, "A5", 0.45, 0.8), (0.11, "B5", 0.5, 0.8), (0.22, "D6", 0.66, 1.9, 0.6), (0.225, "D5", 0.25, 1.9, 0.4)],
              2.0, wet=0.25)
    return y + warm("D4", 2.0, 0.22, 0.12, t60=1.5, attack=0.03)


def request_delivered(seed):
    L = 2.4
    mix = K.Mix(L)
    for i, (t, m) in enumerate(((0.0, "D5"), (0.08, "F#5"), (0.16, "A5"))):
        f = float(K.hz(K.midi(m)))
        x = K.bar(f, K.ns(L - t), t60=0.7, bright=0.6, seed=60 + i, wood=True) * (0.45 + 0.05 * i)
        mix.add(x, t, 1.0, -0.25 + 0.25 * i, "room", 0.2)
    mix.add(glass_note("D6", L - 0.3, 0.55, 0.45, 2.0, seed=64), 0.3, 1.0, 0.1, "room", 0.3)
    mix.add(K.bell(float(K.hz(K.midi("D5"))), K.ns(L - 0.3), t60=2.2, bright=0.35, seed=65) * 0.12, 0.3, 1.0, 0.0, "room", 0.3)
    y = mix.render({"room": room("small")})
    return K.lowpass(y, 7500, 1) + warm("D3", L, 0.3, 0.12, t60=1.6, attack=0.02)


def sounds(Snd):
    return [
        Snd("ui_toast_1", toast(0), rms=-25.5), Snd("ui_toast_2", toast(1), rms=-25.5),
        Snd("ui_card", card, rms=-24.5),
        Snd("ui_warning", warning, rms=-23.0, mono=True),
        Snd("ui_answer_yes", answer_yes, rms=-25.0), Snd("ui_answer_no", answer_no, rms=-26.0),
        Snd("ui_story", story, rms=-26.5),
        Snd("ui_now", now, rms=-26.5),
        Snd("ui_level_up", level_up, rms=-23.0, mono=True),
        Snd("ui_rank_up", rank_up, rms=-22.0, mono=True),
        Snd("ui_quest_done", quest_done, rms=-23.5),
        Snd("ui_request_delivered", request_delivered, rms=-23.5),
    ]


def events(Evt):
    return [
        # the five System events core already registers: same ids and subtitle keys, new sounds
        Evt("signal.chime", "", files=["ui_toast_1", "ui_toast_2"], folder="core"),
        Evt("signal.open", "", files=["ui_card"], folder="core"),
        Evt("signal.warning", "", files=["ui_warning"], folder="core"),
        Evt("level_up", "", files=["ui_level_up"], folder="core"),
        Evt("rank_up", "", files=["ui_rank_up"], folder="core"),
        # new ones
        Evt("ui.answer_yes", "System answer accepted", files=["ui_answer_yes"]),
        Evt("ui.answer_no", "System answer declined", files=["ui_answer_no"]),
        Evt("ui.story", "The Signal speaks", files=["ui_story"]),
        Evt("ui.now", "Now tracker updates", files=["ui_now"]),
        Evt("ui.quest_done", "Quest complete", files=["ui_quest_done"]),
        Evt("ui.request_delivered", "Request delivered", files=["ui_request_delivered"]),
    ]


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
