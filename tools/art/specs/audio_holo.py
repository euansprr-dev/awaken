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
  The band's pages (SPEC §30.4):
  ui_holo_page        a page prints in behind the scan line: A5 then D6, light, with the air of the line running down
  ui_holo_spend       a stat point placed: one D5 struck; the band plays it higher for each point you place (E5, F#5,
                      A5, B5, D6: the pitch is set in code), so a run of points climbs the scale
  ui_holo_confirm     the points confirmed: D5 A5 D6 together with a little shimmer on top, a settled sound
  ui_holo_undo        a placed point taken back: B5 falling to F#5, soft
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


def page(seed):
    L = 0.6
    y = U.notes([(0.0, "A5", 0.22, 0.30, 0.6), (0.045, "D6", 0.30, 0.40, 0.65)], L, wet=0.18, spread=0.2)
    air = sweep(L, 0.03, 0.14, 7000, 2400, 0.020, q=3.0, seed=seed + 17)  # the scan line running down the panel
    return K.declick(y + air)


def spend(seed):
    L = 0.5
    return K.declick(U.notes([(0.0, "D5", 0.42, 0.30, 0.6)], L, wet=0.16, spread=0.0))


def confirm(seed):
    L = 1.1
    y = U.notes([(0.0, "D5", 0.36, 0.70, 0.55), (0.012, "A5", 0.34, 0.75, 0.6), (0.024, "D6", 0.34, 0.85, 0.7),
                 (0.090, "F#6", 0.10, 0.45, 0.8)], L, wet=0.22, spread=0.3)
    return K.declick(y + U.warm("D4", L, 0.0, 0.08, t60=0.6, attack=0.01))


def undo(seed):
    L = 0.55
    return K.declick(U.notes([(0.0, "B5", 0.26, 0.20, 0.5), (0.06, "F#5", 0.24, 0.28, 0.45)], L, wet=0.14, spread=0.15, lp=6000))


def sounds(Snd):
    return [
        Snd("ui_holo_page", page, rms=-26.5),
        Snd("ui_holo_spend", spend, rms=-26.5),
        Snd("ui_holo_confirm", confirm, rms=-25.0),
        Snd("ui_holo_undo", undo, rms=-26.5),
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
        Evt("holo.page", "Band page turns", files=["ui_holo_page"], folder="holo"),
        Evt("holo.spend", "Stat point placed", files=["ui_holo_spend"], folder="holo"),
        Evt("holo.confirm", "Stat points confirmed", files=["ui_holo_confirm"], folder="holo"),
        Evt("holo.undo", "Stat point taken back", files=["ui_holo_undo"], folder="holo"),
    ]
