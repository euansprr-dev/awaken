"""The Inscribing Table's sounds (SPEC §45.10): the book's leather opening and its pages turning, the burin on metal and
on leather, a dull scratch when the pen leaves the line, each family's note as a stroke closes (wards a deep glass
bowl, banes a hard bright ping, spoils a gold chime, carrying a breathy flute, keeping a warm wooden bar), the piece
waking, a page learned, an old enchanting table's flare as it turns, the Study's drifting letters and a scrape.

Built like the Forge's (audio_forge.py): Kenney's CC0 recordings (tools/art/sources/kenney, CC0 1.0) for the physical
transients (metal ticks, leather creaks, cloth), the audio kit for paper, tone and air. Notes are pitched at C5; the
client raises each stroke's note a step (SPEC §45.10).

    python3 tools/art/specs/audio_enchant.py          render everything and write the JSON
    python3 tools/art/specs/audio_enchant.py --json   only the JSON
    python3 tools/art/specs/audio_enchant.py note_    only these (name prefixes)
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
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/enchant")
MERGE = os.path.join(ROOT, "src/main/merge/enchant/assets/awaken")
SR = K.SR
_ROOM = None


def room(x, wet=0.2):
    """A study: wood and books, short and soft."""
    global _ROOM
    if _ROOM is None:
        _ROOM = K.reverb_ir(t60=0.9, predelay=0.01, bright=0.35, width=0.0, early=0.55, size=0.5, seed=4501)
    w = K.convolve(x, _ROOM)
    w = K.mono(w) if w.ndim > 1 else w
    out = np.zeros(max(len(x), len(w)))
    out[:len(x)] += x
    out[:len(w)] += w * wet
    return out


def mix(*parts):
    n = max(len(p) for p, _ in parts)
    out = np.zeros(n)
    for p, g in parts:
        out[:len(p)] += p * g
    return out


def unit(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def at(x, sec, n):
    out = np.zeros(n)
    s = K.ns(sec)
    out[s:s + len(x)] += x[: max(0, n - s)]
    return out


def rustle(dur, seed, fc=3200, rate=60.0):
    """Paper moving: a band of noise broken into tiny crackles, swelling and settling."""
    n = K.ns(dur)
    body = unit(K.bandpass(K.noise(n, "pink", seed), fc, 0.9)) * (K.smooth_noise(n, rate, seed + 1) * 0.5 + 0.5)
    crack = unit(K.highpass(K.crackle(n, 140.0, seed + 2, 0.7, 0.8), 1800, 2)) * 0.5
    return (body + crack) * K.swell(n, 0.35, 1.6)


# ---- the book -------------------------------------------------------------------------------------------------------

def book_open(k):
    """The cover lifting on its leather hinge, the block of pages falling open with a soft thump and a breath of paper."""
    n = K.ns(1.1)
    creak = A.shift(A.rec(A.pick(["creak1", "creak2", "creak3"], K.rng(10 + k))), 1.6 + 0.1 * k)
    cloth = A.rec(A.pick(["cloth1", "cloth3"], K.rng(20 + k)))
    thump = A.shift(A.rec("impactSoft_medium_001"), 1.3)
    return room(mix((at(A.trim(creak, 0.45), 0.0, n), 0.45), (at(cloth, 0.12, n), 0.35), (at(thump, 0.38, n), 0.5),
                    (at(rustle(0.6, 30 + k, 2600), 0.34, n), 0.5)))


def page_turn(k):
    """A leaf lifted and laid over: a quick rustle that flutters and lands."""
    n = K.ns(0.55)
    flick = rustle(0.42, 100 + k, 3600 + 300 * k, 90.0)
    land = unit(K.bandpass(K.noise(K.ns(0.08), "white", 110 + k), 2200, 1.2)) * K.perc(K.ns(0.08), 0.002, 0.05)
    return room(mix((flick, 0.8), (at(land, 0.36, n), 0.4)), 0.12)


# ---- the burin ------------------------------------------------------------------------------------------------------

def burin_metal(k):
    """The point bites into metal: a bright tick, a short singing cut."""
    t = A.shift(A.rec(A.pick(["impactMetal_light_001", "impactMetal_light_002", "impactMetal_light_004"], K.rng(200 + k))), 1.9 + 0.1 * k)
    cut = A.scrape(0.28, 210 + k, fc=5200)
    sing = A.ring(2400 + 120 * k, 0.25, 0.4, 220 + k, bright=0.5)
    return room(mix((t, 0.6), (cut, 0.45), (sing, 0.22)), 0.1)


def burin_leather(k):
    """The point drawn through leather: a dry soft drag."""
    rub = A.rec(A.pick(["handleSmallLeather", "handleSmallLeather2"], K.rng(300 + k)))
    drag = unit(K.bandpass(K.noise(K.ns(0.3), "pink", 310 + k), 1400, 1.4)) * K.perc(K.ns(0.3), 0.01, 0.22)
    return room(mix((rub, 0.6), (drag, 0.4)), 0.08)


def scratch(k):
    """Off the line: a dull rough skid."""
    n = K.ns(0.22)
    skid = unit(K.bandpass(K.noise(n, "white", 400 + k), 900 + 200 * k, 2.5)) * (K.smooth_noise(n, 220.0, 401 + k) * 0.5 + 0.5)
    return room(skid * K.perc(n, 0.004, 0.14), 0.06)


def scrape(k):
    """An enchantment scraped off: the burin dragged hard across, the light going out of it with a low dull knock."""
    n = K.ns(1.1)
    long = A.scrape(0.75, 500 + k, fc=3000)
    knock = A.shift(A.rec("impactPlate_medium_003"), 0.75)
    fall = unit(K.lowpass(K.noise(n, "pink", 510 + k), 500, 2)) * K.line(n, [(0, 0.6), (0.6, 0.1), (1, 0)])
    return room(mix((long, 0.6), (at(knock, 0.62, n), 0.5), (fall, 0.2)))


# ---- each family's note (C5; the client pitches each stroke up a step) ----------------------------------------------

C5 = K.hz(72)


def note_ward(k):
    """A deep glass bowl struck softly: closed, round, long."""
    n = K.ns(2.2)
    bowl = R.pk(K.glass(C5 / 2, n, bright=0.35, t60=1.8, seed=600)) + R.pk(K.glass(C5 / 2 * 1.003, n, bright=0.3, t60=1.8, seed=601)) * 0.7
    return room(mix((unit(bowl), 0.8), (R.pk(K.bell(C5, n, t60=1.2, bright=0.3, seed=602)), 0.25)), 0.25)


def note_bane(k):
    """A hard bright ping with an edge: a point going home."""
    n = K.ns(1.4)
    ping = R.pk(K.fm_bell(C5, n, t60=0.9, index=3.2, ratio=3.01, seed=610))
    tick = A.shift(A.rec("impactMetal_light_002"), 2.2)
    return room(mix((ping, 0.75), (tick, 0.3)), 0.2)


def note_spoils(k):
    """A gold chime: bright, open, a little shimmer above."""
    n = K.ns(1.8)
    chime = R.pk(K.bell(C5, n, t60=1.5, bright=0.75, seed=620))
    halo = R.pk(K.glass(C5 * 2, n, bright=0.6, t60=1.0, seed=621))
    return room(mix((chime, 0.7), (halo, 0.3)), 0.25)


def note_carry(k):
    """A breathy flute: air moving, carrying."""
    n = K.ns(1.2)
    fl = K.flute(C5, n, breath=0.35, seed=630, vib_cents=8) * K.adsr(n, 0.03, 0.15, 0.55, 0.6)
    air = unit(K.bandpass(K.noise(n, "pink", 631), 2400, 0.8)) * K.swell(n, 0.25, 2.0)
    return room(mix((unit(fl), 0.75), (air, 0.2)), 0.3)


def note_keep(k):
    """A warm wooden bar: steady, plain, kept."""
    n = K.ns(1.3)
    b = R.pk(K.bar(C5, n, t60=0.9, bright=0.4, seed=640, wood=True))
    return room(mix((b, 0.85), (R.pk(K.bar(C5 * 2, n, t60=0.5, bright=0.3, seed=641, wood=True)), 0.15)), 0.2)


# ---- waking, learning, flaring ---------------------------------------------------------------------------------------

def wake(k):
    """The piece wakes: a breath drawn in, a bright rising shimmer, a lapis chord blooming, a soft low weight under it."""
    dur = 3.2
    n = K.ns(dur)
    breath = unit(K.bandpass(K.noise(n, "pink", 700), 1800, 0.8)) * K.line(n, [(0, 0), (0.18, 0.7), (0.24, 0), (1, 0)])
    rise = unit(K.bandpass(K.noise(n, "white", 701), 5200, 1.2)) * K.line(n, [(0, 0), (0.2, 0.5), (0.25, 0), (1, 0)])
    chord = np.zeros(n)
    s = K.ns(0.75)
    for m, g in ((67, 0.9), (74, 0.8), (79, 0.65), (83, 0.45), (91, 0.2)):
        chord[s:] += R.pk(K.glass(K.hz(m), n - s, bright=0.55, t60=2.2, seed=710 + m)) * g
    weight = np.zeros(n)
    bm = A.sub(1.2, 70, 40, 0.5, drive=1.5)
    weight[s:s + len(bm)] += bm[: n - s]
    return room(mix((breath, 0.3), (rise, 0.25), (unit(chord), 0.65), (weight, 0.35)), 0.3)


def learn(k):
    """A page learned: paper, then three soft rising chimes and a held glow."""
    n = K.ns(2.4)
    out = mix((rustle(0.5, 800, 3000), 0.4))
    out = np.pad(out, (0, n - len(out)))
    for i, m in enumerate((72, 76, 79, 84)):
        b = R.pk(K.bell(K.hz(m), n - K.ns(0.25 + 0.16 * i), t60=1.6, bright=0.5, seed=810 + i))
        out += at(b, 0.25 + 0.16 * i, n) * (0.55 - 0.08 * i)
    pad = K.pad(K.hz(60), n, voices=5, seed=820, bright=0.25) * K.swell(n, 0.5, 2.0)
    return room(out + unit(pad) * 0.18, 0.3)


def flare(k):
    """An old enchanting table turning: a rush of air, crackling sparks, a low bell going out."""
    n = K.ns(2.4)
    rush = unit(K.lowpass(K.noise(n, "pink", 900), 1500, 2)) * K.line(n, [(0, 0), (0.1, 1), (0.35, 0.3), (1, 0)])
    sparks = unit(K.highpass(K.crackle(n, 70.0, 901, 0.8, 0.8), 2200, 2)) * K.perc(n, 0.01, 1.0)
    bell = R.pk(K.bell(K.hz(55), n - K.ns(0.15), t60=1.8, bright=0.45, seed=902))
    return room(mix((rush, 0.5), (sparks, 0.35), (at(bell, 0.15, n), 0.55)), 0.3)


def drift(k):
    """The Study's letters drifting: a faint airy shimmer, barely there."""
    n = K.ns(3.0)
    air = unit(K.bandpass(K.noise(n, "pink", 1000 + k), 3800, 0.7)) * (0.5 + 0.5 * K.smooth_noise(n, 1.5, 1001 + k))
    glint = np.zeros(n)
    r = K.rng(1002 + k)
    for i in range(5):
        t0 = float(r.uniform(0.2, 2.4))
        g = R.pk(K.glass(K.hz(int(r.choice([84, 86, 88, 91, 93]))), K.ns(0.8), bright=0.5, t60=0.6, seed=1003 + i + k * 7))
        glint += at(g, t0, n) * 0.25
    return room((air * 0.4 + glint) * K.line(n, [(0, 0), (0.2, 1), (0.8, 1), (1, 0)]), 0.35)


# name: (render(k), variants, attenuation, subtitle)
EVENTS = OrderedDict([
    ("book_open", (book_open, 2, 12, "A book opens")),
    ("page_turn", (page_turn, 3, 8, "A page turns")),
    ("burin_metal", (burin_metal, 3, 8, "A burin cuts metal")),
    ("burin_leather", (burin_leather, 2, 8, "A burin cuts leather")),
    ("scratch", (scratch, 3, 6, "A burin scratches")),
    ("note_ward", (note_ward, 1, 12, "A ward's note")),
    ("note_bane", (note_bane, 1, 12, "A bane's note")),
    ("note_spoils", (note_spoils, 1, 12, "A chime of spoils")),
    ("note_carry", (note_carry, 1, 12, "A carrying note")),
    ("note_keep", (note_keep, 1, 12, "A keeping note")),
    ("wake", (wake, 1, 24, "An enchantment wakes")),
    ("learn", (learn, 1, 16, "An enchantment learned")),
    ("flare", (flare, 1, 24, "An old table flares")),
    ("drift", (drift, 2, 8, "Letters drift")),
    ("scrape", (scrape, 2, 12, "An enchantment scraped off")),
])


def write_json():
    sounds, lang = OrderedDict(), OrderedDict()
    for event, (_, variants, att, subtitle) in EVENTS.items():
        sounds[f"enchant.{event}"] = OrderedDict(subtitle=f"subtitles.awaken.enchant.{event}", sounds=[
            OrderedDict(name=f"awaken:enchant/enchant_{event}_{k}", attenuation_distance=att) for k in range(variants)])
        lang[f"subtitles.awaken.enchant.{event}"] = subtitle
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    lpath = os.path.join(MERGE, "lang/en_us.json")
    cur = json.load(open(lpath)) if os.path.exists(lpath) else {}
    cur = OrderedDict((k, v) for k, v in cur.items() if not k.startswith("subtitles.awaken.enchant."))
    cur.update(lang)
    with open(lpath, "w") as f:
        json.dump(cur, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(sounds)} in merge/enchant")


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    rms, _, ceil, q = TARGETS["sfx"]
    quiet = ("page_turn", "burin_leather", "scratch", "drift")
    if "--json" not in argv:
        os.makedirs(SOUNDS, exist_ok=True)
        for event, (render, variants, *_) in EVENTS.items():
            if only and not any(event.startswith(p) for p in only):
                continue
            for k in range(variants):
                y = render(k)
                y, took = R.master(y, rms - (5.0 if event in quiet else 0.0), ceil, 6.0)
                path = os.path.join(SOUNDS, f"enchant_{event}_{k}.ogg")
                K.write_ogg(path, y, q, None)
                print(f"  enchant_{event}_{k}  {len(y) / SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    write_json()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
