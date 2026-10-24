"""The Holo UI's sounds (docs/modules/holo.md "Sounds"), rendered by tools/art/audio_build.py like the rest of the
System's UI family and in its one voice: the struck glass with its warm core and faint glint (audio_ui.glass_note),
in the same small bright room, every note from D major pentatonic (D E F# A B), so a holo sound under a toast or a
card never clashes. What is new is the projection: a breath of filtered air that sweeps as light leaves the wrist and
folds back into it.

  ui_holo_open        the K menu unfolds out of the wrist, timed to its choreography (docs/modules/holo.md):
                      air swells with the arm (0-120 ms), a glint as the band flares (140), the panel's arpeggio
                      D5 F#5 A5 D6 as it grows and unfolds (160-310), a faint B6 as the scan line passes, D-A left open
  ui_holo_open_quick  the inventory: the same gesture at its 2.5x speed (open in 180 ms), lighter, it opens often
  ui_holo_close       folding back into the band: D6 A5 F#5 D5 falling, the air drawn in after it, a soft low tick
  ui_holo_hover_1/2   the cursor comes onto something: one tiny glass tick (A6 / D7, picked at random by the event)
  ui_holo_card        a System card projected in (the projected look's voice for signal.open): ui_card's own rising
                      D5 A5 E6, with the scan sweeping down through the card and a small fizz at its edge
"""
import os
import sys

import numpy as np

import audiokit as K

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import audio_ui as U  # noqa: E402  the System's glass voice: one note, its room, its air


def sweep(length, at, dur, f0, f1, level, q=2.2, seed=0, pan=0.0):
    """Air through a band that glides from f0 to f1 over `dur` seconds from `at`: light moving."""
    n = K.ns(length)
    s = K.ns(at)
    k = min(K.ns(dur), n - s)
    if k <= 8:
        return np.zeros((n, 2))
    tail = K.ns(0.08)
    seg = K.stft_filter(K.noise(k + tail, "white", seed),
                        lambda t, f: K.bp_resp(f0 * (f1 / f0) ** min(1.0, max(0.0, t / dur)), q)(f), frame=1024, hop=256)
    seg = K.highpass(seg, 700, 2)  # air, not rumble
    seg = seg / (np.std(seg) + 1e-12)
    env = np.concatenate([K._rise(k) ** 1.5, np.exp(-np.arange(tail) / K.ns(0.02))])
    x = np.zeros(n)
    m = min(len(seg), n - s)
    x[s:s + m] = (seg * env)[:m]
    return K.pan(x * level, pan)


# ---- the sounds --------------------------------------------------------------------------------------------------

def open_k(seed):
    L = 1.25
    y = U.notes([(0.140, "A6", 0.16, 0.25, 0.75),  # the band flares
                 (0.160, "D5", 0.36, 0.75), (0.210, "F#5", 0.40, 0.75), (0.260, "A5", 0.44, 0.85),
                 (0.310, "D6", 0.50, 1.10, 0.6),  # the arpeggio as it grows and unfolds
                 (0.330, "B6", 0.10, 0.35, 0.7),  # the scan line
                 (0.360, "A5", 0.18, 0.95, 0.45)], L, wet=0.24, spread=0.3)
    air = sweep(L, 0.0, 0.40, 1800, 6400, 0.045, seed=seed + 3, pan=-0.25)  # out of the wrist (lower left)
    return K.declick(y + air + U.warm("D4", L, 0.16, 0.10, t60=0.9, attack=0.03))


def open_quick(seed):
    L = 0.75
    y = U.notes([(0.056, "A6", 0.12, 0.18, 0.75),
                 (0.064, "D5", 0.30, 0.45), (0.088, "A5", 0.34, 0.50), (0.120, "D6", 0.38, 0.65, 0.6)], L, wet=0.20, spread=0.25)
    air = sweep(L, 0.0, 0.16, 2200, 6400, 0.030, seed=seed + 5, pan=-0.2)
    return K.declick(y + air)


def close(seed):
    L = 0.8
    y = U.notes([(0.0, "D6", 0.30, 0.30, 0.6), (0.035, "A5", 0.30, 0.30), (0.070, "F#5", 0.28, 0.32), (0.105, "D5", 0.26, 0.40, 0.45)],
                L, wet=0.18, spread=0.25, lp=6000)
    air = sweep(L, 0.0, 0.17, 6000, 1600, 0.030, seed=seed + 7, pan=-0.25)  # drawn back into the wrist
    return K.declick(y + air + U.warm("D4", L, 0.165, 0.09, t60=0.10, attack=0.003))  # the panel meets the band


def hover(m):
    return lambda seed: K.declick(U.notes([(0.0, m, 0.42, 0.12, 0.75)], 0.32, wet=0.10, spread=0.0))


def card(seed):
    L = 1.5
    y = U.card(seed)  # the System's card motif, as signal.open has always sounded
    scan = sweep(L, 0.02, 0.20, 7000, 2400, 0.022, q=3.0, seed=seed + 11)  # the scan line runs down through the card
    n = K.ns(L)
    fizz = np.zeros(n)
    k = K.ns(0.12)
    fizz[:k] = K.crackle(k, rate=260, seed=seed + 13, bright=0.8) * K.perc(k, 0.002, 0.10) * 0.035  # its projecting edge
    return K.declick(y + scan + K.pan(fizz, 0.35))


def sounds(Snd):
    return [
        Snd("ui_holo_open", open_k, rms=-25.0),
        Snd("ui_holo_open_quick", open_quick, rms=-26.5),
        Snd("ui_holo_close", close, rms=-26.0),
        Snd("ui_holo_hover_1", hover("A6"), rms=-26.5),
        Snd("ui_holo_hover_2", hover("D7"), rms=-26.5),
        Snd("ui_holo_card", card, rms=-24.5),
    ]


def events(Evt):
    return [
        Evt("holo.open", "Holo panel unfolds", files=["ui_holo_open"], folder="holo"),
        Evt("holo.open_quick", "Holo inventory unfolds", files=["ui_holo_open_quick"], folder="holo"),
        Evt("holo.close", "Holo panel folds away", files=["ui_holo_close"], folder="holo"),
        Evt("holo.hover", "Holo panel ticks", files=["ui_holo_hover_1", "ui_holo_hover_2"], folder="holo"),
        Evt("holo.card", "System card projected", files=["ui_holo_card"], folder="holo"),
    ]
