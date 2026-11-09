"""Forged picks' own sounds (SPEC §49.10): a pick you swing thousands of times, and the anvil's new acts.

The bar is vanilla's own tool sounds. So every one of these is built on a recorded blow (Kenney's Impact Sounds and RPG
Audio, tools/art/sources/kenney, CC0 1.0), kept as the loudest thing in it, in several takes, short and dry. Synthesis only
adds what a recording of this size can't: the faint steel ring that tunes a strike to the ore's note, air for a whoosh, the
fire. Vanilla's tool and dig sounds were measured (dur 0.3-1.2 s, spectral centroid 2-7 kHz, gated RMS -14 to -27 dB,
peaks -2 to -10 dB; only the numbers were read, nothing is copied), and the Kenney impacts centre near 1 kHz, so each is
pitched up and given back its top with a shelf. `python3 tools/art/specs/audio_picks.py --check` prints each sound's numbers.

  in the hand   pick.strike_delver / breaker / mattock / sounder  a bite into rock, each form's own; the ring is at A5 or a
                                                                   consonance of it, so the ore's note (the game pitches it
                                                                   by TuningForkItem.pitch) stays in tune with it
                pick.strike_earth / wood    the Mattock's broad end in soil and in timber
                pick.strike_blunt           a pick worn blunt: no ring, a dull knock
                pick.sounding_tap / hollow  the tap and the rock's answer, drummy and from inside
                pick.mattock_toss / catch   the haft leaving the hand and landing back in it
  at the anvil  anvil.eye, tip, hang, tend, thread, pour, colours

    python3 tools/art/specs/audio_picks.py           render everything and write the JSON
    python3 tools/art/specs/audio_picks.py --json    only the JSON
    python3 tools/art/specs/audio_picks.py --check   render, write, and print centroid, peak and RMS per sound
    python3 tools/art/specs/audio_picks.py strike_   only these (name prefixes)

Written to src/main/resources/assets/awaken/sounds/pick/ and registered in src/main/merge/delve_picks/assets/awaken/.
"""
import json
import os
import sys
from collections import OrderedDict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.dirname(HERE)
sys.path.insert(0, ART)
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
import ritual_sounds as R  # noqa: E402
import audio_arms as A  # noqa: E402
import audio_forge as F  # noqa: E402
import audio_anvil as V  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
OUT = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/pick")
MERGE = os.path.join(ROOT, "src/main/merge/delve_picks/assets/awaken")
SR = K.SR
A5 = 880.0

room, mix, peak = F.room, F.mix, F.unit
at = V.at

MINING = ["impactMining_000", "impactMining_001", "impactMining_003"]
LIGHT = ["impactMetal_light_001", "impactMetal_light_002", "impactMetal_light_004"]
HEAVY = ["impactMetal_heavy_000", "impactMetal_heavy_001", "impactMetal_heavy_003", "impactMetal_heavy_004"]
MEDIUM = ["impactMetal_medium_001", "impactMetal_medium_004"]
PUNCH = ["impactPunch_medium_000", "impactPunch_medium_001", "impactPunch_medium_003", "impactPunch_medium_004"]
WOOD = ["impactWood_light_000", "impactWood_light_002", "impactWood_light_004"]
GLASS = ["impactGlass_light_000", "impactGlass_light_002", "impactGlass_light_004"]


def rb(name, ratio=1.0, dur=None, top=0.0, fc=2200):
    """A recording: pitched, cut to length, its top given back so it sits where vanilla's tool sounds do."""
    x = A.shift(A.rec(name), ratio)
    if dur:
        x = A.trim(x, dur)
    return K.shelf(x, fc, top, True) if top else x


def ring(f, t60, seed, bright=0.5):
    """A steel ring that starts just behind the blow, so the transient stays the blow's."""
    y = A.ring(f, t60, min(1.2, t60 * 2.2 + 0.1), seed, bright=bright)
    return y * K.line(len(y), [(0, 0), (0.006, 1), (1, 1)])


def cave():
    if cave.ir is None:
        cave.ir = K.reverb_ir(t60=1.15, predelay=0.02, bright=0.22, width=0.0, early=0.45, size=0.7, seed=7290)
    return cave.ir


cave.ir = None


def in_cave(x, wet):
    w = K.convolve(x, cave())
    w = K.mono(w) if w.ndim > 1 else w
    out = np.zeros(max(len(x), len(w)))
    out[:len(x)] += x
    out[:len(w)] += w * wet
    return out


def whoosh(dur, seed, f0=650, top=2600, f1=850, q=1.6):
    """Air: a band of noise that rises and falls with the speed of what's moving through it."""
    x = K.noise(K.ns(dur), "white", seed)
    width = 1.0 / q

    def resp(t, f):
        u = float(np.clip(t / dur, 0, 1))
        fc = f0 * (top / f0) ** (u / 0.5) if u < 0.5 else top * (f1 / top) ** ((u - 0.5) / 0.5)
        return np.exp(-0.5 * (np.log(np.maximum(f, 1.0) / fc) / width) ** 2)

    return peak(K.stft_filter(x, resp)) * K.swell(K.ns(dur), 0.5, 1.6)


# ---- the bite ------------------------------------------------------------------------------------------------------

def strike_delver(k):
    r = K.rng(7000 + k)
    rock = rb(MINING[k % 3], 1.7 + 0.05 * k, 0.2, 10)
    steel = rb(A.pick(LIGHT, r), 1.4 + 0.05 * k, 0.12, 5)
    return room(mix((rock, 0.78), (steel, 0.5), (ring(A5, 0.2, 7010 + k), 0.11)), 0.05)


def strike_breaker(k):
    r = K.rng(7020 + k)
    rock = rb(MINING[k % 3], 1.22 + 0.04 * k, 0.26, 7)
    steel = rb(A.pick(HEAVY, r), 1.05 + 0.04 * k, 0.15, 6)
    return room(mix((rock, 0.75), (steel, 0.62), (ring(A5 / 2, 0.26, 7030 + k, 0.4), 0.12),
                    (A.sub(0.2, 120, 70, 0.1), 0.2)), 0.06)


def strike_mattock(k):
    r = K.rng(7040 + k)
    rock = rb(MINING[k % 3], 1.44 + 0.05 * k, 0.22, 7)
    steel = rb(A.pick(MEDIUM, r), 1.5 + 0.06 * k, 0.12, 6)
    return room(mix((rock, 0.78), (steel, 0.5), (ring(A5 * 2 / 3, 0.2, 7050 + k, 0.45), 0.11)), 0.05)


def strike_sounder(k):
    r = K.rng(7060 + k)
    rock = rb(MINING[k % 3], 1.95 + 0.06 * k, 0.15, 6)
    steel = rb(A.pick(LIGHT, r), 1.72 + 0.06 * k, 0.1, 3)
    return room(mix((rock, 0.66), (steel, 0.55), (ring(A5 * 2, 0.34, 7070 + k, 0.7), 0.13)), 0.06)


def strike_earth(k):
    """The broad end into soil: a soft chunk and the crumbs falling off it."""
    n = K.ns(0.22)
    body = rb(PUNCH[k % 4], 1.6 + 0.05 * k, 0.16, 7)
    crumbs = peak(K.highpass(K.crackle(n, 200.0, 7080 + k, 0.55, 0.6), 1600, 2)) * K.perc(n, 0.004, 0.14)
    return room(mix((body, 0.85), (at(crumbs, 0.012), 0.34)), 0.03)


def strike_wood(k):
    """Into timber: the chop, with the haft's knock behind it."""
    body = A.shift(A.rec("chop"), 0.92 + 0.07 * k)
    knock = rb(WOOD[k % 3], 1.3 + 0.05 * k, 0.1, 5)
    return room(mix((body, 0.8), (knock, 0.4)), 0.04)


def strike_blunt(k):
    """Worn blunt: nothing rings, the steel just knocks."""
    r = K.rng(7090 + k)
    rock = K.highpass(K.lowpass(A.trim(A.shift(A.rec(MINING[k % 3]), 1.25 + 0.04 * k), 0.2), 3200, 2), 220, 2)
    steel = K.highpass(K.lowpass(A.trim(A.shift(A.rec(A.pick(HEAVY, r)), 1.0), 0.1), 2000, 2), 260, 2)
    return room(mix((rock, 0.85), (steel, 0.55)), 0.03)


# ---- Sounding and the turn -----------------------------------------------------------------------------------------

def sounding_tap(k):
    r = K.rng(7100 + k)
    rock = rb(MINING[k % 3], 1.9 + 0.05 * k, 0.2, 11)
    tick = rb(A.pick(LIGHT, r), 1.65, 0.08, 6)
    return in_cave(mix((rock, 0.75), (tick, 0.55)), 0.1)


def sounding_hollow(k):
    """Drummy rock: a dull knock from inside it, dark, with the hollow's own small room behind."""
    body = K.lowpass(A.trim(A.shift(A.rec(["impactWood_heavy_001", "impactWood_medium_000", "impactWood_medium_002", "impactPunch_heavy_002"][k]), 1.05 + 0.05 * k), 0.34), 2400, 2)
    grit = K.lowpass(A.trim(A.shift(A.rec(MINING[k % 3]), 0.66 + 0.04 * k), 0.34), 1500, 2)
    # laptop speakers lose everything under 200 Hz: the knock has to live above that, and still be dark
    return K.highpass(in_cave(mix((peak(body), 0.85), (peak(grit), 0.5)), 0.42), 190, 2)


def mattock_toss(k):
    """It leaves the hand: the haft sliding out of the grip, then air round a heavy head turning over."""
    n = K.ns(0.34)
    air = whoosh(0.32, 7120 + k, 520 + 40 * k, 1900 + 200 * k, 700)
    cloth = rb(["cloth1", "cloth2", "cloth3"][k], 1.3, 0.3, 4)
    creak = rb("creak3", 1.35 + 0.1 * k, 0.1, 3)
    return mix((air[:n], 0.6), (peak(cloth), 0.34), (peak(creak), 0.12))


def mattock_catch(k):
    """Back in the hand: a palm on the haft, the wood's knock, and the head's small rattle a moment after."""
    r = K.rng(7130 + k)
    palm = rb(PUNCH[k % 4], 2.0 + 0.06 * k, 0.12, 8)
    haft = rb(WOOD[k % 3], 1.5 + 0.08 * k, 0.1, 6)
    head = rb(A.pick(LIGHT, r), 1.5, 0.09, 4)
    leather = rb("handleSmallLeather" if k % 2 == 0 else "handleSmallLeather2", 1.15, 0.2)
    return room(mix((palm, 0.85), (haft, 0.6), (at(head, 0.016), 0.2), (at(peak(leather), 0.03), 0.18)), 0.03)


# ---- the anvil's acts ----------------------------------------------------------------------------------------------

def eye(k):
    """A drift driven through hot steel: the hammer's ring on the drift, the dull give of the bar under it."""
    r = K.rng(7200 + k)
    hammer = rb(A.pick(HEAVY, r), 1.3 + 0.05 * k, 0.16, 11)
    drift = rb(A.pick(MEDIUM, r), 1.8 + 0.04 * k, 0.1, 8)
    give = V.thud(0.3, 150, 90, 0.14, 7210 + k)
    return room(mix((hammer, 0.85), (at(drift, 0.002), 0.45), (give, 0.2), (ring(1320, 0.22, 7220 + k, 0.5), 0.16),
                    (F.sizzle(0.26, 7230 + k), 0.12)), 0.14)


def tip(k):
    """Steeling a point: small, hard, bright, a little grit of diamond in it."""
    r = K.rng(7240 + k)
    hammer = rb(A.pick(LIGHT, r), 1.45 + 0.05 * k, 0.14, 5)
    grit = rb(GLASS[k % 3], 1.75, 0.1, 3)
    return room(mix((hammer, 0.85), (grit, 0.2), (ring(2640, 0.2, 7250 + k, 0.7), 0.14), (A.spark(0.16, 7260 + k), 0.12)), 0.12)


def hang(k):
    """The head driven down the haft: a knock of wood on the block, then the iron settling on it."""
    r = K.rng(7270 + k)
    knock = K.highpass(rb("impactPlank_medium_001", 1.7 + 0.07 * k, 0.16, 11), 240, 2)
    sharp = rb(WOOD[k % 3], 2.0 + 0.05 * k, 0.1, 12)
    iron = rb(A.pick(MEDIUM, r), 1.25, 0.1, 3)
    return room(mix((peak(knock), 0.85), (sharp, 0.5), (at(iron, 0.02), 0.2)), 0.1)


def tend(k):
    """A dresser's stroke: a short file along the edge with a tap at the end of it."""
    r = K.rng(7290 + k)
    stroke = rb(["knifeSlice", "knifeSlice2"][k % 2], 0.74 + 0.05 * k, 0.26, 0)
    tick = rb(A.pick(LIGHT, r), 1.7, 0.07, 3)
    return room(mix((peak(stroke), 0.8), (at(tick, 0.2), 0.3)), 0.06)


def thread(k):
    """Deepglass run along a groove: a thin bright draw of glass on steel, with the glass's voice in it."""
    n = K.ns(0.5)
    draw = K.lowpass(rb(["knifeSlice2", "knifeSlice"][k % 2], 1.0 + 0.05 * k, 0.42, 0), 9000, 2)
    chip = rb(GLASS[k % 3], 2.1, 0.07, 3)
    voice = R.pk(K.glass(1320 + 60 * k, n, bright=0.35, t60=0.35, seed=7300 + k, hit=0.25))
    return room(mix((peak(draw), 0.72), (chip, 0.16), (voice, 0.1)), 0.12)


def pour(k):
    """A thick pour: low, glugging, with pops breaking at its surface and a hiss where it meets cold steel."""
    dur = 2.1
    n = K.ns(dur)
    r = K.rng(7320 + k)
    # the weight of it, breathing with the glugs
    glug = 0.55 + 0.45 * (0.5 + 0.5 * K.smooth_noise(n, 9.0, 7321 + k))
    body = peak(K.lowpass(K.noise(n, "pink", 7322 + k), 800, 2)) * glug * K.line(n, [(0, 0), (0.05, 1), (0.8, 0.8), (1, 0)])
    hiss = peak(K.bandpass(K.noise(n, "white", 7323 + k), 5600, 1.0)) * K.line(n, [(0, 0), (0.1, 0.4), (0.7, 0.3), (1, 0)]) * (0.6 + 0.4 * K.smooth_noise(n, 22.0, 7324 + k))
    pops = np.zeros(n)
    t = 0.08
    while t < dur - 0.25:
        p = rb("impactSoft_medium_00" + str(int(r.integers(0, 4))), 2.4 + float(r.random()) * 1.6, 0.07, 9)
        s = K.ns(t)
        m = min(len(p), n - s)
        pops[s:s + m] += p[:m] * (0.45 + 0.55 * float(r.random()))
        t += 0.06 + float(r.random()) * 0.16
    return room(mix((body, 0.62), (hiss, 0.14), (peak(pops), 0.5)), 0.08)


def colours(k):
    """Warming the steel: a low breath of flame, the iron ticking as it creeps, brighter the longer it runs."""
    dur = 2.7
    n = K.ns(dur)
    r = K.rng(7340 + k)
    flame = peak(K.lowpass(K.noise(n, "pink", 7341 + k), 1400, 2)) * (0.7 + 0.3 * K.smooth_noise(n, 7.0, 7342 + k))
    flame *= K.line(n, [(0, 0), (0.08, 0.7), (0.9, 1.0), (1, 0)])
    air = peak(K.bandpass(K.noise(n, "white", 7343 + k), 3600, 0.8)) * K.line(n, [(0, 0), (0.15, 0.25), (0.92, 0.6), (1, 0)]) * (0.6 + 0.4 * K.smooth_noise(n, 14.0, 7344 + k))
    ticks = np.zeros(n)
    t = 0.3
    while t < dur - 0.2:
        p = rb(A.pick(LIGHT, r), 2.3 + float(r.random()) * 0.9, 0.05, 3)
        s = K.ns(t)
        m = min(len(p), n - s)
        ticks[s:s + m] += p[:m] * (0.4 + 0.6 * float(r.random()))
        t += 0.14 + float(r.random()) * 0.46
    return mix((flame, 0.75), (air, 0.08), (peak(ticks), 0.3))


# ---- events --------------------------------------------------------------------------------------------------------

# event -> (make(k), takes, target gated RMS dB, attenuation, subtitle)
PICK = OrderedDict([
    ("strike_delver", (strike_delver, 4, -18.0, 16, "Pick bites rock")),
    ("strike_breaker", (strike_breaker, 4, -16.5, 18, "Heavy pick bites rock")),
    ("strike_mattock", (strike_mattock, 4, -18.0, 16, "Mattock bites rock")),
    ("strike_sounder", (strike_sounder, 4, -19.0, 16, "Fine pick bites rock")),
    ("strike_earth", (strike_earth, 4, -20.0, 12, "Mattock cuts earth")),
    ("strike_wood", (strike_wood, 3, -18.0, 14, "Mattock cuts wood")),
    ("strike_blunt", (strike_blunt, 3, -21.0, 12, "Blunt pick knocks")),
    ("sounding_tap", (sounding_tap, 3, -18.0, 16, "Pick taps rock")),
    ("sounding_hollow", (sounding_hollow, 4, -20.0, 24, "Rock sounds hollow")),
    ("mattock_toss", (mattock_toss, 3, -25.0, 8, "Mattock turns over")),
    ("mattock_catch", (mattock_catch, 3, -19.0, 8, "Mattock caught")),
])

ANVIL = OrderedDict([
    ("eye", (eye, 3, -14.5, 20, "Drift punches through hot steel")),
    ("tip", (tip, 3, -15.5, 18, "Point steeled")),
    ("hang", (hang, 3, -16.0, 16, "Head seats on the haft")),
    ("tend", (tend, 4, -19.0, 12, "File runs along an edge")),
    ("thread", (thread, 3, -22.0, 12, "Glass threads the steel")),
    ("pour", (pour, 2, -21.0, 16, "Molten glass pours")),
    ("colours", (colours, 1, -27.0, 12, "Steel warms")),
])


LENGTH = {"sounding_tap": 0.8, "sounding_hollow": 1.0, "mattock_toss": 0.34, "mattock_catch": 0.42, "eye": 0.7, "tip": 0.6, "hang": 0.5, "tend": 0.5,
          "thread": 0.6, "pour": 2.6, "colours": 2.7, "strike_breaker": 0.65}


def render(kind, event, k):
    make, _, rms, _, _ = (PICK if kind == "pick" else ANVIL)[event]
    y = make(k)
    y = A.trim(y, LENGTH.get(event, 0.6))  # a blow's room dies well inside this; vanilla's digs are half a second
    y, _ = R.master(y, rms, -2.0, 8.0)
    return y


def write_json():
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    sounds, lang = OrderedDict(), OrderedDict()
    for kind, table in (("pick", PICK), ("anvil", ANVIL)):
        for event, (_, takes, _, att, subtitle) in table.items():
            sounds[f"{kind}.{event}"] = OrderedDict(subtitle=f"subtitles.awaken.{kind}.{event}", sounds=[
                OrderedDict(name=f"awaken:pick/{kind}_{event}_{i}", attenuation_distance=att) for i in range(takes)])
            lang[f"subtitles.awaken.{kind}.{event}"] = subtitle
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(os.path.join(MERGE, "lang/en_us.json"), "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")


def centroid(x):
    f = np.abs(np.fft.rfft(x * np.hanning(len(x))))
    fr = np.fft.rfftfreq(len(x), 1 / SR)
    return int((f * fr).sum() / (f.sum() + 1e-9))


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    if "--json" not in argv:
        os.makedirs(OUT, exist_ok=True)
        for kind, table in (("pick", PICK), ("anvil", ANVIL)):
            for event, (_, takes, *_) in table.items():
                if only and not any(event.startswith(p) for p in only):
                    continue
                for k in range(takes):
                    y = render(kind, event, k)
                    path = os.path.join(OUT, f"{kind}_{event}_{k}.ogg")
                    K.write_ogg(path, y, 5, None)
                    if "--check" in argv:
                        print(f"  {kind}_{event}_{k:<1}  {len(y) / SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  centroid {centroid(y):5d}  {os.path.getsize(path) // 1024} KB")
    write_json()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
