"""The Living Deep's underlife voices (SPEC §42.5): the cave cricket you hear long before you see it, and a roost of bats
bursting off the roof as you pass under; and the regions' own (SPEC §42.7): old roots creaking in the Rootways' roof, and
in the Ochre Deep the rock ticking as it settles, the chime crickets' chorus and a warm hum under it all; and the other
six's (SPEC §42.8): drips echoing into the Pale Halls' pools, the wind moaning through the Windcleft and pebbles falling
down its cracks, the Drowned Galleries' lakes lapping, the Glimmer Hollows' crystals ringing, a rumble deep in the Black
Vaults, and the Violet Deep's hum. Synthesised from scratch with tools/art/audiokit.py and the wild voices' helpers
(tools/art/specs/audio_wild.py); nothing is recorded or copied.

    python3 tools/art/specs/audio_underlife.py         render every one-shot, write the events and subtitles
    python3 tools/art/specs/audio_underlife.py json    only the events and subtitles

One-shots (sounds/caves/<name><n>.ogg, mono), played where the creature is; loops (sounds/caves/<name>.ogg), a region's
bed (the Ochre Deep's hum, the Windcleft's wind, the Drowned Galleries' lapping, the Violet Deep's hum), played round the
listener and seamless end to start. Unlike the wild's, which are dry because the
open air gives back nothing, each carries a little of the rock round it: a few hard early reflections and a short dark
tail, so a chirp reads as in a passage, not in a meadow. Mastered as the wild's (-28 dBFS gated RMS, <= -6 dBTP).
Event ids never start with `ambient.` (Sound Physics skips those).
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
import audio_wild as W  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MERGE = os.path.join(ROOT, "src/main/merge/caves/assets/awaken")
SR = K.SR


def build(out):  # rendered on demand (main), not by build_all
    pass


def rock(x, seed, wet=0.22):
    """The passage round the voice: three or four hard early reflections off near rock, then a short dark tail."""
    r = K.rng(seed)
    y = np.concatenate([x, np.zeros(K.ns(0.5))])
    out = y.copy()
    for k in range(int(r.integers(3, 5))):
        d = K.ns(r.uniform(0.007, 0.028) * (1 + k * 0.6))
        out[d:] += K.lowpass(y, 5200 - 700 * k, 2)[:len(y) - d] * r.uniform(0.18, 0.32) / (1 + k * 0.5)
    ir = K.reverb_ir(t60=0.75, predelay=0.012, bright=0.3, width=0.0, early=0.6, size=0.5, seed=seed)
    tail = K.mono(K.convolve(y, ir))[:len(y)]
    out = out + wet * tail / (np.max(np.abs(tail)) + 1e-9) * np.max(np.abs(y))
    b = W.Buf()
    b.x = out
    return b.out(floor_db=-66.0, tail=0.02)


# cave crickets (here a singing kind, not the silent camel cricket): slower and lower than the field's, two or three
# pulses a chirp and long gaps between, so one sounds alone in the dark: carrier Hz, chirps a second, pulses, pulse
# spacing s, pulse length s, length s
CRICKETS = [(3350, 1.1, 2, 0.034, 0.024, 2.6), (3620, 0.8, 3, 0.03, 0.021, 3.4), (3180, 1.4, 2, 0.038, 0.027, 2.2),
            (3480, 0.95, 3, 0.032, 0.022, 3.0)]


def cricket(v, seed):
    """A cave cricket: a few chirps of two or three pure pulses, the pitch slipping down through each, in a passage."""
    r = K.rng(seed)
    carrier, rate, pulses, per, plen, dur = CRICKETS[v]
    b = W.Buf()
    t = 0.006
    for c in range(int(np.ceil(dur * rate))):
        for p in range(pulses):
            L = K.ns(plen * r.uniform(0.95, 1.05))
            fc = carrier * (1 + 0.005 * r.standard_normal())
            f = W.curve(L, [(0, fc * 1.015), (1, fc * 0.98)], ms=0)
            u = np.arange(L) / (L - 1)
            amp = [0.8, 1.0, 0.9][p] * r.uniform(0.9, 1.05) * (0.85 if c % 3 == 2 else 1.0)
            b.add(W.tone(f, (1, 0.05, 0.01), W._sd(r), 0) * np.sin(np.pi * u) ** 0.8 * amp, t + p * per + 0.002 * r.standard_normal())
        t += 1 / rate * (1 + 0.08 * r.standard_normal())
    return rock(b.out(), seed + 7)


def _beat_leather(r, fc, per):
    """One bat's wingbeat: a membrane, not feathers, so a softer, lower flap with a papery edge."""
    L = K.ns(per * 1.5)
    tt = W.T(L)
    body = W._unit(K.lowpass(K.bandpass(r.standard_normal(L), fc, 0.9), 1800, 2))
    paper = W._unit(K.bandpass(r.standard_normal(L), fc * 3.2, 1.6)) * 0.22
    return (body + paper) * (1 - np.exp(-tt / (per * 0.1))) * np.exp(-tt / (per * 0.28))


def bats(v, seed):
    """A roost bursting off the roof: a dozen bats' wingbeats overlapping, quick and leathery, thinning as they spread
    down the passage, and a few of their high thin squeaks."""
    r = K.rng(seed)
    b = W.Buf()
    many = [6, 9, 12][v]
    for i in range(many):
        t = 0.005 + r.uniform(0.0, 0.22) * (i / many) ** 0.7
        rate = r.uniform(11, 16)
        k = int(r.uniform(0.45, 0.9) * rate)
        fc = r.uniform(520, 820)
        loud = r.uniform(0.5, 1.0)
        for j in range(k):
            u = j / k
            amp = loud * min(1.0, 0.6 + 0.25 * j) * np.exp(-2.2 * u) * r.uniform(0.8, 1.0)
            b.add(_beat_leather(r, fc * (1 - 0.2 * u), 1 / rate), t, amp)
            t += (1 / rate) * (1 + 0.05 * r.standard_normal())
    for s in range(int(r.integers(2, 5))):
        f0 = r.uniform(7200, 9400)
        b.add(W.note(r.uniform(0.012, 0.024), [(0, f0), (0.4, f0 * 1.06), (1, f0 * 0.8)], 0.22, a=0.001, rel=0.004,
                     amps=(1, 0.05), seed=W._sd(r), ms=0.5), r.uniform(0.02, 0.45))
    return rock(b.out(), seed + 11, wet=0.3)


def root_creak(v, seed):
    """Old roots shifting in the rock overhead: a slow stick-slip creak (a train of tiny slips, forty to a hundred a
    second, speeding and slowing as the root bends) ringing the wood's few resonances, once or twice with a pause between,
    dulled by the earth it comes through."""
    r = K.rng(seed)
    b = W.Buf()
    t = 0.01
    for k in range(1 + v % 2 + int(r.random() < 0.3)):
        dur = r.uniform(0.6, 1.3)
        L = K.ns(dur)
        f0 = r.uniform(38, 66)
        f = W.curve(L, [(0, f0), (0.3, f0 * r.uniform(1.3, 1.8)), (0.7, f0 * r.uniform(0.9, 1.4)), (1, f0 * 0.8)], ms=20)
        slips = np.diff(np.floor(np.cumsum(f) / SR), prepend=0) > 0
        x = slips * (0.55 + 0.45 * r.random(L)) + 0.08 * r.standard_normal(L)
        body = (K.bandpass(x, r.uniform(260, 340), 4) + 0.6 * K.bandpass(x, r.uniform(640, 760), 5)
                + 0.3 * K.bandpass(x, r.uniform(1300, 1600), 6))
        u = np.arange(L) / (L - 1)
        env = np.sin(np.pi * u) ** 0.6 * (1 + 0.3 * K.smooth_noise(L, 6, seed + k))
        b.add(K.lowpass(body * env, 2400, 2), t)
        t += dur + r.uniform(0.25, 0.7)
    return rock(b.out(), seed + 3, wet=0.3)


def rock_tick(v, seed):
    """The Ochre Deep's rock settling, the way warm stone ticks as it cools: one to three small dry clicks, each ringing a
    few milliseconds in the rock, spaced unevenly."""
    r = K.rng(seed)
    b = W.Buf()
    t = 0.005
    for k in range(1 + v % 3):
        base = r.uniform(1800, 3200)
        y = W.knock(r, base, [(1, 1, 0.025), (1.71, 0.5, 0.018), (2.63, 0.3, 0.012)], hard=r.uniform(0.5, 1.0), click=0.8,
                    click_fc=7000)
        b.add(y, t, r.uniform(0.5, 1.0))
        t += r.uniform(0.08, 0.6)
    return rock(b.out(), seed + 5, wet=0.35)


def chime_cricket(v, seed):
    """The Ochre Deep's chime crickets: three or four in a chorus, each chirp a tiny bell-like ping rather than a buzz
    (pure partials, not harmonic: 1, 2.32, 4.1, dying in a few hundredths of a second), in trills of three to five, the
    voices a little apart in pitch so the chorus shimmers."""
    r = K.rng(seed)
    b = W.Buf()
    voices = 3 + v % 2
    for i in range(voices):
        f0 = r.uniform(2400, 3100)
        t = r.uniform(0.0, 0.6)
        rate = r.uniform(0.7, 1.2)
        while t < 3.0:
            for p in range(int(r.integers(3, 6))):
                y = K.modal(f0 * (1 + 0.003 * r.standard_normal()), (1, 2.32, 4.1), (1, 0.35, 0.12), (0.06, 0.03, 0.015), K.ns(0.09),
                            attack=0.002, seed=W._sd(r))
                b.add(y, t + p * r.uniform(0.045, 0.06), r.uniform(0.5, 1.0) / voices ** 0.5)
            t += 1 / rate * r.uniform(0.8, 1.3)
    return rock(b.out(), seed + 9, wet=0.32)


def warm_hum(seed):
    """The Ochre Deep's bed: a low warm hum of pure partials on 52 Hz beating slowly, over a brown rumble; eight seconds
    that loop without a seam (the last half second crossfaded into the first)."""
    n, fade = K.ns(8.0), K.ns(0.5)
    x = K.drone(52.0, n + fade, partials=(1, 2, 3, 5), amps=(1, 0.45, 0.2, 0.06), beat=0.12, seed=seed)
    x = x / (np.max(np.abs(x)) + 1e-9) + 0.5 * W._unit(K.lowpass(K.noise(n + fade, "brown", seed + 1), 180, 2))
    w = np.sin(0.5 * np.pi * np.arange(fade) / fade) ** 2
    y = x[:n].copy()
    y[:fade] = x[:fade] * w + x[n:n + fade] * (1 - w)
    return y


def hall(x, seed, t60=2.4, wet=0.45):
    """A big still hall round the voice (the Pale Halls, the Black Vaults): a few far reflections and a long dark tail."""
    y = np.concatenate([x, np.zeros(K.ns(t60))])
    ir = K.reverb_ir(t60=t60, predelay=0.03, bright=0.25, width=0.0, early=0.4, size=1.0, seed=seed)
    tail = K.mono(K.convolve(y, ir))[:len(y)]
    out = y + wet * tail / (np.max(np.abs(tail)) + 1e-9) * np.max(np.abs(y))
    b = W.Buf()
    b.x = out
    return b.out(floor_db=-60.0, tail=0.05)


def pool_drip(v, seed):
    """A drop falling into a still pool in a pale hall: the tick of the drop meeting the water, the small bubble it
    traps ringing up in pitch over a few hundredths of a second, sometimes a second smaller drop after; then the hall
    gives it back, long and soft."""
    r = K.rng(seed)
    b = W.Buf()
    t = 0.01
    for k in range(1 + int(r.random() < 0.35)):
        L = K.ns(0.09)
        f0 = r.uniform(900, 1500) * (1.25 if k else 1.0)
        f = W.curve(L, [(0, f0), (0.4, f0 * r.uniform(1.6, 2.1)), (1, f0 * 2.3)], ms=2)
        ph = 2 * np.pi * np.cumsum(f) / SR
        u = np.arange(L) / SR
        bubble = np.sin(ph) * np.exp(-u / r.uniform(0.012, 0.02))
        tick = K.highpass(K.noise(K.ns(0.004), "white", seed + k), 3000, 2) * np.hanning(K.ns(0.004))
        b.add(tick, t, 0.35)
        b.add(bubble, t + 0.002, 1.0 if not k else 0.5)
        t += r.uniform(0.12, 0.3)
    return hall(b.out(), seed + 1, t60=2.6, wet=0.55)


def pebble_fall(v, seed):
    """Pebbles let go somewhere up a crack: a first knock, then three to seven more bouncing down, quicker and smaller
    as they go, ringing in the tall narrow rock."""
    r = K.rng(seed)
    b = W.Buf()
    t = 0.01
    gap = r.uniform(0.18, 0.3)
    for k in range(int(r.integers(4, 9))):
        base = r.uniform(1100, 2400) * (1 + 0.08 * k)
        y = W.knock(r, base, [(1, 1, 0.03), (1.83, 0.45, 0.02), (3.1, 0.25, 0.012)], hard=r.uniform(0.6, 1.0), click=0.9,
                    click_fc=6500)
        b.add(y, t, r.uniform(0.6, 1.0) * 0.82 ** k)
        t += gap * r.uniform(0.7, 1.1)
        gap *= 0.8
    return rock(b.out(), seed + 2, wet=0.42)


def crystal_ring(v, seed):
    """Crystals ringing as you brush past: two to four strikes a breath apart, each a cluster of glassy partials (not
    harmonic: 1, 2.76, 5.4, 8.9) that rings for a second or two and beats slowly, the cluster a little apart in pitch."""
    r = K.rng(seed)
    b = W.Buf()
    t = 0.01
    f0 = r.uniform(1250, 1800)
    for k in range(2 + v % 3):
        f = f0 * r.choice([1.0, 1.122, 1.26, 1.335, 1.5])
        y = K.modal(f, (1, 2.76, 5.4, 8.9), (1, 0.45, 0.2, 0.08), (1.6, 0.9, 0.45, 0.2), K.ns(2.2), attack=0.004,
                    seed=W._sd(r), detune_cents=4.0, pair=0.5)
        b.add(y, t, r.uniform(0.5, 1.0))
        t += r.uniform(0.09, 0.28)
    return hall(b.out(), seed + 4, t60=1.6, wet=0.35)


def vault_rumble(v, seed):
    """Something heavy shifting far off under the Black Vaults: a low rumble swelling over two to four seconds and
    dying away, with a few deep knocks inside it, heard through a great deal of rock."""
    r = K.rng(seed)
    n = K.ns(r.uniform(2.5, 4.0))
    u = np.arange(n) / (n - 1)
    env = np.sin(np.pi * u ** 0.7) ** 1.5 * (1 + 0.35 * K.smooth_noise(n, 3, seed))
    x = W._unit(K.lowpass(K.noise(n, "brown", seed), 110, 2)) * env
    b = W.Buf()
    b.add(x, 0.0)
    for k in range(int(r.integers(2, 5))):
        y = W.knock(r, r.uniform(60, 110), [(1, 1, 0.25), (1.6, 0.5, 0.15)], hard=0.3, dull=2.0, click=0.0)
        b.add(K.lowpass(y, 300, 2), r.uniform(0.3, n / SR - 0.6), r.uniform(0.4, 0.8))
    return hall(K.lowpass(b.out(), 400, 2), seed + 6, t60=3.0, wet=0.5)


def _seam(x, n, fade):
    """The last `fade` samples crossfaded into the first, so the loop has no seam."""
    w = np.sin(0.5 * np.pi * np.arange(fade) / fade) ** 2
    y = x[:n].copy()
    y[:fade] = x[:fade] * w + x[n:n + fade] * (1 - w)
    return y


def wind_moan(seed):
    """The Windcleft's bed: wind through the cracks, a breathy band of noise whose centre wanders slowly, and over it a
    hollow moan (two narrow resonances, a fifth apart) that swells and sinks; ten seconds, looping without a seam."""
    n, fade = K.ns(10.0), K.ns(1.0)
    m = n + fade
    gust = 0.6 + 0.4 * K.smooth_noise(m, 0.4, seed)
    breath = np.zeros(m)
    src = K.noise(m, "pink", seed + 1)
    # the band moves through a few overlapping bands, faded by a slow curve each
    for i, fc in enumerate((260, 380, 540, 760)):
        g = np.clip(0.5 + 0.6 * K.smooth_noise(m, 0.25, seed + 10 + i), 0, 1)
        breath += K.bandpass(src, fc, 1.6) * g
    moan = (K.bandpass(K.noise(m, "white", seed + 2), 330, 28) + 0.6 * K.bandpass(K.noise(m, "white", seed + 3), 495, 30))
    swell = np.clip(0.3 + 0.7 * K.smooth_noise(m, 0.15, seed + 4), 0, 1) ** 2
    x = W._unit(breath) * gust + 0.8 * W._unit(moan) * swell
    return _seam(x, n, fade)


def lake_lap(seed):
    """The Drowned Galleries' bed: still water lapping at rock and silt, soft swells every second or so with the small
    gurgles of water in hollows; ten seconds, looping without a seam."""
    n, fade = K.ns(10.0), K.ns(1.0)
    m = n + fade
    r = K.rng(seed)
    b = np.zeros(m)
    t = 0
    while t < m:
        L = int(K.ns(r.uniform(0.5, 1.1)))
        u = np.arange(L) / L
        env = np.sin(np.pi * u) ** 2.5
        sw = K.lowpass(K.noise(L, "pink", W._sd(r)), r.uniform(500, 900), 2) * env * r.uniform(0.5, 1.0)
        e = min(m, t + L)
        b[t:e] += sw[:e - t]
        t += int(K.ns(r.uniform(0.6, 1.3)))
    for k in range(int(r.integers(5, 9))):
        L = K.ns(r.uniform(0.04, 0.09))
        g = K.bandpass(K.noise(L, "white", W._sd(r)), r.uniform(350, 900), 9) * np.hanning(L)
        i = int(r.integers(0, m - L))
        b[i:i + L] += 0.5 * g / (np.max(np.abs(g)) + 1e-9) * np.max(np.abs(b) + 1e-9)
    x = W._unit(b) + 0.15 * W._unit(K.lowpass(K.noise(m, "brown", seed + 5), 150, 2))
    return _seam(x, n, fade)


def violet_hum(seed):
    """The Violet Deep's bed, the seep's second voice: a low hum on 41 Hz shaped through two vowel-like resonances, so
    it sounds almost sung, wavering slowly in pitch and beating against itself; eight seconds, looping without a seam."""
    n, fade = K.ns(8.0), K.ns(0.5)
    m = n + fade
    x = K.drone(41.0, m, partials=(1, 2, 3, 4, 5, 6, 8, 10, 12), amps=(1, 0.7, 0.55, 0.45, 0.35, 0.3, 0.2, 0.14, 0.1),
                beat=0.2, seed=seed)
    x = K.bandpass(x, 480, 3.0) + 0.8 * K.bandpass(x, 820, 4.0) + 0.5 * K.lowpass(x, 120, 2)
    wob = 1 + 0.2 * K.smooth_noise(m, 0.3, seed + 1)
    return _seam(W._unit(x) * wob, n, fade)


# event: (file stem, voice, variants, reach in blocks, gated RMS dBFS, subtitle)
SHOTS = {
    "caves.cricket": ("cricket", cricket, 4, 24, -28.0, "Cave cricket chirps"),
    "caves.bats": ("bats", bats, 3, 24, -27.0, "Bats scatter"),
    "caves.root_creak": ("root_creak", root_creak, 3, 20, -30.0, "Roots creak overhead"),
    "caves.rock_tick": ("rock_tick", rock_tick, 4, 16, -30.0, "Rock ticks"),
    "caves.chime_cricket": ("chime_cricket", chime_cricket, 4, 24, -29.0, "Chime crickets ring"),
    "caves.pool_drip": ("pool_drip", pool_drip, 4, 24, -31.0, "Water drips into a pool"),
    "caves.pebble_fall": ("pebble_fall", pebble_fall, 3, 28, -30.0, "Pebbles fall"),
    "caves.crystal_ring": ("crystal_ring", crystal_ring, 4, 16, -31.0, "Crystals ring"),
    "caves.vault_rumble": ("vault_rumble", vault_rumble, 3, 48, -29.0, "Deep rumble"),
}
# loops: (file stem, maker, gated RMS dBFS, subtitle)
LOOPS = {"caves.warm_hum": ("warm_hum", warm_hum, -30.0, "Warm hum in the rock"),
         "caves.wind_moan": ("wind_moan", wind_moan, -31.0, "Wind moans in the cracks"),
         "caves.lake_lap": ("lake_lap", lake_lap, -32.0, "Water laps"),
         "caves.violet_hum": ("violet_hum", violet_hum, -31.0, "Low hum in the rock")}


def path(name, k):
    return os.path.join(SOUNDS, f"caves/{name}{k + 1}.ogg")


def write_events():
    import json
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    spath = os.path.join(MERGE, "sounds.json")
    ev = json.load(open(spath)) if os.path.exists(spath) else {}
    lpath = os.path.join(MERGE, "lang/en_us.json")
    lang = json.load(open(lpath)) if os.path.exists(lpath) else {}
    for eid, (name, _, n, att, _, sub) in SHOTS.items():
        ev[eid] = {"subtitle": "subtitles.awaken." + eid,
                   "sounds": [{"name": f"awaken:caves/{name}{k + 1}", "attenuation_distance": att} for k in range(n)]}
        lang["subtitles.awaken." + eid] = sub
    for eid, (name, _, _, sub) in LOOPS.items():
        ev[eid] = {"subtitle": "subtitles.awaken." + eid, "sounds": [{"name": f"awaken:caves/{name}"}]}
        lang["subtitles.awaken." + eid] = sub
    with open(spath, "w") as f:
        json.dump(dict(sorted(ev.items())), f, indent=2)
        f.write("\n")
    with open(lpath, "w") as f:
        json.dump(dict(sorted(lang.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")


def render_shots():
    q = TARGETS["sfx"][3]
    os.makedirs(os.path.join(SOUNDS, "caves"), exist_ok=True)
    for eid, (name, fn, n, _, rms, _) in SHOTS.items():
        for k in range(n):
            y = W.master(K.mono(fn(k, sum(map(ord, name)) * 17 + k * 131)), rms)
            p = path(name, k)
            K.write_ogg(p, y, q, None)
            d = K.read_audio(p)
            tp = K.true_peak(d)
            if tp > W.CEIL:
                y = y * K.dbamp(W.CEIL - tp - 0.15)
                K.write_ogg(p, y, q, None)
                d = K.read_audio(p)
            print(f"  caves/{name}{k + 1:<3d} {len(d) / SR:5.2f}s  RMS {K.gated_rms(d):6.1f}  TP {K.true_peak(d):5.1f}  "
                  f"{os.path.getsize(p) / 1024:4.0f} kB  (target {rms:.0f})")


def render_loops():
    q = TARGETS["sfx"][3]
    for eid, (name, fn, rms, _) in LOOPS.items():
        y = fn(sum(map(ord, name)) * 17)
        # filtered as a loop (three copies, the middle kept), so its ends still meet
        y = K.highpass(np.concatenate([y, y, y]), 40, 2)[len(y):2 * len(y)]
        y = y * K.dbamp(rms - K.gated_rms(y))      # no fades: the ends must meet
        if K.true_peak(y) > W.CEIL:
            y = y * K.dbamp(W.CEIL - K.true_peak(y) - 0.15)
        p = os.path.join(SOUNDS, f"caves/{name}.ogg")
        K.write_ogg(p, y, q, None)
        d = K.read_audio(p)
        print(f"  caves/{name:<5s} {len(d) / SR:5.2f}s  RMS {K.gated_rms(d):6.1f}  TP {K.true_peak(d):5.1f}  loop")


def main(argv):
    if not argv or argv[0] != "json":
        render_shots()
        render_loops()
    write_events()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
