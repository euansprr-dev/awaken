"""The C themes' sound (docs/SPEC.md §38.12): the Frost Monastery, the Mirror Palace, the Precursor Lab and the Nightmare.
Three beds a theme, the touchstones, the events, the wonders' moments, the signature creatures and an arrival sting each.
Rebuild:

    python3 tools/art/specs/audio_gate_worlds_c.py            render every file and write the JSON
    python3 tools/art/specs/audio_gate_worlds_c.py --json     only the JSON

Recorded where there's a recording: the monastery's wind is the Frost Spires' and the lands' high wind, its bells the
church bell and the handbell, its ice the Delve's cracks and the Rig's; the palace's glass is the world chimes, the Lumen's
glass and the Stillpoint's tone; the lab's hum, clunks and chirps are the Halo station's and the holo screens'; the house's
creaks are the elder wood's and Kenney's, its owl the wild's. What nothing records is made here: the monks' chant (the
choir, low, on an "o"), a whale's song (a slow sung glide), alarms and klaxons, digital glitches (crushed and stuttered),
and the music box (struck tines). Vanilla's sounds are only referenced, never mixed in.

Written to src/main/resources/assets/awaken/sounds/gate_world/ and registered as gate.world.* in
src/main/merge/gates/assets/awaken/ (sounds.json and the lang file; other keys are kept). Loudness by
tools/art/audio_mix.py: beds amb (-28 +- 3 dBFS gated RMS, -6 dBTP), events sfx (-20 +- 4, -1 dBTP).
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
import audio_gate_worlds as G  # noqa: E402
import audio_gate_worlds_caves as C  # noqa: E402
import audio_gate_worlds_d as D  # noqa: E402

BED = G.BED
at, trim, rate, ours, clip, wet, tile, scatter, wide, water, air, drips = (G.at, G.trim, G.rate, G.ours, G.clip, G.wet, G.tile,
                                                                         G.scatter, G.wide, G.water, G.air, G.drips)
norm, pick, kick, loop_of = C.norm, C.pick, C.kick, C.loop_of
wind, ticks = D.wind, D.ticks

CHIMES = D.CHIMES
GLINTS = D.GLINTS
CLUNKS = D.CLUNKS
HISSES = D.HISSES
BUBBLES = D.BUBBLES
CHIRPS = ["amb_world_halo_chirp_" + str(i) for i in range(1, 5)]
CREAKS = ["amb_world_elder_creak_" + str(i) for i in range(1, 5)]
CLOTH = [("rpg-audio", "cloth" + str(i)) for i in range(1, 5)]
# the lullaby every music box in the Nightmare plays (scale steps of C major, slow 3/4)
TUNE = [("E5", 1), ("G5", 1), ("C6", 2), ("B5", 1), ("G5", 1), ("A5", 2), ("G5", 1), ("E5", 1), ("D5", 2), ("C5", 3)]


def hz(m):
    return K.hz(K.midi(m))


def rswell(x, kind):
    """x's echo drawn in backwards, swelling into it (mono)."""
    return norm(K.mono(K.reverse_swell(x, G.room(kind))))


def chant(f0, n, seed, phrase=5.0, gap=1.4):
    """Monks chanting: low voices on an "o" with the fifth under them, in breaths of `phrase` seconds."""
    x = K.choir(f0, n, "o", voices=6, seed=seed, bright=0.18) + 0.6 * K.choir(f0 * 2 / 3, n, "u", voices=4, seed=seed + 1, bright=0.15)
    t = np.arange(n) / K.SR
    cyc = (t % (phrase + gap)) / phrase
    breath = np.where(cyc < 1, np.sin(np.pi * np.clip(cyc, 0, 1)) ** 0.6, 0.0)
    return K.lowpass(x * breath, 1600, 2)


def crack(seed, big=False):
    """Ice cracking: a sharp snap, and the sheet's ringing "pew" falling away under it."""
    r = K.rng(seed)
    n = K.ns(1.4 if big else 0.8)
    x = at(n, 0, rate(ours(("delve_vein_crack", "gate_rig_crack")[seed % 2]), r.uniform(0.7, 1.0)), 0.8)
    m = K.ns(r.uniform(0.35, 0.6) * (1.6 if big else 1.0))
    pew = K.sine(K.glide(m, r.uniform(2200, 3400), r.uniform(240, 420)), m) * K.perc(m, 0.002, 0.5)
    return x + at(n, 0.01, K.bandpass(pew, 1200, 0.5), 0.5)


def snow(n, seed, gain=1.0):
    """Snow blowing: a soft hiss, coming in gusts."""
    s = norm(K.bandpass(K.noise(n, "pink", seed), 2400, 0.7)) * (0.3 + 0.7 * np.clip(0.5 + K.smooth_noise(n, 0.4, seed + 1), 0, 1))
    return s * gain


def ding(f, seed):
    """A small bell: a prayer wheel's or a shrine's."""
    return K.bell(f, K.ns(2.6), t60=2.2, bright=0.55, seed=seed)


def tine(f, seed, t60=1.4):
    """One tine of a music box: a short bright FM ping settling to a pure note."""
    return K.fm_bell(f, K.ns(t60 + 0.4), t60=t60, index=1.6, ratio=4.0, seed=seed) * 0.5


def melody(notes, beat, seed, slow=1.0, drop=0.0):
    """The tune on music-box tines; `slow` > 1 drags each beat longer as it goes (winding down), `drop` sags the pitch."""
    total = sum(b for _, b in notes)
    n = K.ns(beat * total * max(slow, 1.0) + 1.8)
    x = np.zeros(n)
    t = 0.0
    for i, (m, b) in enumerate(notes):
        u = i / max(len(notes) - 1, 1)
        f = hz(m) * 2 ** (-drop * u / 12)
        x += at(n, t, tine(f, seed + i), 1.0)
        t += beat * b * (1 + (slow - 1) * u)
    return x


def winding(seed, clicks=6):
    """A music box being wound: little ratchet clicks."""
    n = K.ns(0.12 * clicks + 0.3)
    x = np.zeros(n)
    for i in range(clicks):
        x += at(n, 0.05 + i * 0.12, K.bar(3200 + 200 * (i % 2), K.ns(0.05), t60=0.03, bright=0.9, seed=seed + i), 0.4)
    return x


def crush(x, bits=4, hold=6):
    """A digital fault: fewer levels, and each sample held a while (a broken screen's sound)."""
    q = 2 ** (bits - 1)
    y = np.round(x * q) / q
    return np.repeat(y[::hold], hold)[:len(x)]


def stutter(x, seed, cuts=6):
    """A glitch: bits of x repeated and skipped, out of order."""
    r = K.rng(seed)
    out = []
    pos = 0
    while pos < len(x) and len(out) < cuts * 4:
        L = K.ns(r.uniform(0.03, 0.12))
        seg = x[pos:pos + L]
        for _ in range(int(r.integers(1, 4))):
            out.append(seg)
        pos += int(L * r.uniform(0.5, 2.5))
    return np.concatenate(out) if out else x


def klaxon(n, seed, f=(620, 460), period=0.9):
    """A two-tone alarm: hard tones swapping every half period, softened."""
    t = np.arange(n) / K.SR
    f0 = np.where((t % period) < period / 2, f[0], f[1])
    x = np.sign(K.sine(f0, n)) * 0.3 + K.sine(f0 * 2, n) * 0.2
    return K.lowpass(x, 2400, 2)


def beep(f, length=0.09):
    n = K.ns(length)
    return K.sine(np.full(n, float(f)), n) * K.adsr(n, 0.004, 0.02, 0.8, 0.02)


def whale_song(seed, length=6.0, lo=70.0, hi=150.0):
    """A whale singing a long way off: a slow sung glide with its overtones, the moan rising and falling."""
    n = K.ns(length)
    r = K.rng(seed)
    f = K.line(n, [(0, lo), (0.25, hi * r.uniform(0.85, 1.0)), (0.55, lo * 1.2), (0.8, hi * 0.8), (1, lo * 0.9)])
    vib = 1 + 0.01 * np.sin(2 * np.pi * 4.5 * np.arange(n) / K.SR)
    x = K.sine(f * vib, n) + 0.45 * K.sine(f * 2 * vib, n) + 0.2 * K.sine(f * 3.01 * vib, n)
    x += 0.15 * K.bandpass(K.noise(n, "pink", seed), 300, 1.0)
    return K.lowpass(x * K.swell(n, 0.35, 1.2), 900, 2)


def heart(n, bpm, seed, gain=1.0):
    """A slow heartbeat: two soft low thumps a beat."""
    x = np.zeros(n)
    t = 0.3
    while t < n / K.SR - 0.6:
        x += at(n, t, K.sub_boom(K.ns(0.4), 60, 38, 0.25, seed), gain)
        x += at(n, t + 0.24, K.sub_boom(K.ns(0.4), 52, 34, 0.25, seed + 1), gain * 0.7)
        t += 60.0 / bpm
    return x


def steps(n, t0, every, count, seed, r=0.8, gain=0.5, lp=2000):
    x = np.zeros(n)
    for i in range(count):
        s = trim(rate(clip("impact-sounds", "footstep_concrete_000"), r * (1 + 0.04 * ((i * 7 + seed) % 3 - 1))), 0, None, 0.1)
        x += at(n, t0 + i * every, K.lowpass(s, lp, 2), gain)
    return x


# ---- touchstones, events, moments and creatures ------------------------------------------------------------------------

def wheel(v):
    """A prayer wheel spun: the wooden drum creaking round on its pin, and its little bell striking as it turns."""
    n = K.ns(3.0)
    x = at(n, 0, K.lowpass(trim(rate(clip("rpg-audio", ("creak1", "creak2")[v]), 1.3), 0, None, 0.3), 2600, 2), 0.6)
    x += at(n, 0.4, ding(hz(("A5", "G5")[v]), 201 + v), 0.5) + at(n, 1.3, ding(hz(("A5", "G5")[v]), 203 + v), 0.3)
    return wet(x, "crypt", 0.3)


def bells(v):
    """The monastery bells ringing out over the valley: the big bell, then the small ones answering it, far and cold."""
    n = K.ns(8.0)
    x = at(n, 0, trim(rate(G.bell("3446"), 0.82), 0, 6.5, 1.5), 0.9)
    for i, r in enumerate((1.25, 1.5, 1.12)):
        x += at(n, 1.2 + 1.1 * i, trim(rate(G.bell("0292"), r), 0, 3.5, 1.0), 0.35)
    x += at(n, 4.0, trim(rate(G.bell("3446"), 0.82), 0, 3.5, 1.5), 0.5)
    return K.lowpass(wet(x, "field", 0.55), 3200, 2)


def blizzard(v):
    """A blizzard coming over the mountain: the wind climbing to a howl, snow driving sideways, a gust slamming past."""
    n = K.ns(7.0)
    w = wind(n, 211, 2600, 0.6) * K.swell(n, 0.55, 1.2)
    howl = norm(K.bandpass(K.noise(n, "pink", 213), 700, 4.0)) * K.swell(n, 0.6, 1.6)
    return w * 0.65 + howl * 0.3 + snow(n, 215, 0.4) * K.swell(n, 0.6, 1.0)


def icecrack(v):
    """Ice cracking in the thaw: a snap, the sheet singing, a second smaller crack after it."""
    n = K.ns(2.4)
    x = at(n, 0, crack(221 + v, True), 1.0) + at(n, 0.7 + 0.2 * v, crack(223 + v), 0.5)
    return wet(x, "crypt", 0.35)


def still(v):
    """The Still: the wind cut off mid-gust, a held high note in the cold, a heart beating slowly in the quiet."""
    n = K.ns(7.0)
    w = wind(n, 231, 2400, 0.3) * K.line(n, [(0, 0.8), (0.12, 0.8), (0.16, 0), (1, 0)])
    tone = trim(ours("amb_world_stillpoint_tone"), 8.0, 6.0, 1.5)
    x = w + at(n, 0.9, tone * K.swell(len(tone), 0.4, 1.2), 0.5)
    x += heart(n, 46, 233, 0.5) * K.line(n, [(0, 0), (0.2, 0), (0.3, 1), (1, 0.6)])
    return wet(x, "field", 0.4)


def monk(v):
    """The Snow Monk: one deep held chant swelling out of the snow, a gong under it, three slow steps crunching."""
    n = K.ns(7.0)
    x = chant(hz("A2"), n, 241, 5.5, 2.0) * K.swell(n, 0.45, 1.3) * 0.8
    x += at(n, 0.6, K.bell(hz("A1"), K.ns(5.0), t60=5.0, bright=0.35, seed=243), 0.5)
    for i in range(3):
        st = trim(rate(ours("fauna/frost_stag/step" + str(1 + i)), 0.7), 0, None, 0.2)
        x += at(n, 3.4 + 0.9 * i, K.lowpass(st, 2400, 2), 0.6)
    return wet(x, "field", 0.4)


def mirror(v):
    """A mirror touched: a clean glass ring, and the same ring coming back from inside the glass a beat later, backwards."""
    n = K.ns(3.6)
    f = hz(("E6", "C#6")[v])
    g = K.glass(f, K.ns(2.4), bright=0.7, t60=1.8, seed=251 + v)
    x = at(n, 0, g, 0.7)
    back = rswell(K.glass(f * 1.5, K.ns(1.2), bright=0.5, t60=0.8, seed=253 + v), "crypt")
    x += at(n, 0.9, back * K.line(len(back), [(0, 0), (0.9, 1), (1, 0)]), 0.3)
    return wet(x, "crypt", 0.3)


def shatter(v):
    """The mirrors shattering: one great pane going, then the others round it, glass raining down."""
    n = K.ns(5.0)
    x = at(n, 0, ours("gate_rig_shatter"), 0.9)
    for i, name in enumerate(("impactGlass_heavy_000", "impactGlass_heavy_001", "impactGlass_heavy_004")):
        x += at(n, 0.35 + 0.4 * i, trim(rate(clip("impact-sounds", name), 0.9 - 0.08 * i), 0, None, 0.3), 0.6)
    rain = scatter(n, 261, kick([("impact-sounds", "impactGlass_light_000"), ("impact-sounds", "impactGlass_light_002"),
                                ("impact-sounds", "impactGlass_light_004")], 1.1, 1.5), 16, 0.25)
    x += (rain[0] + rain[1]) * K.line(n, [(0, 0), (0.25, 0), (0.3, 1), (1, 0.2)])
    return wet(x, "crypt", 0.35)


def doubled(v):
    """Your steps answered: footsteps, then the same steps again from inside the glass, a little late and out of step."""
    n = K.ns(4.5)
    x = steps(n, 0.1, 0.5, 4, 271, 0.85, 0.6)
    echo = steps(n, 0.28, 0.53, 4, 272, 0.8, 0.4, 1600)
    whisper = norm(K.bandpass(K.noise(n, "pink", 273), 2600, 1.2)) * K.swell(n, 0.6, 2.0) * 0.08
    return wet(x + K.mono(K.chorus(echo, 8.0, 0.6, 2, 274, 0.6)) + whisper, "crypt", 0.4)


def whale(v):
    """The whale in the White: a vast low song from very far off, its call coming back across the empty air."""
    a = whale_song(281, 7.0)
    b = whale_song(283, 6.0, 85, 170)
    n = K.ns(10.0)
    x = at(n, 0, a, 0.9) + at(n, 3.6, b, 0.5)
    return wet(x, "field", 0.7)


def reflection(v):
    """Your Reflection stepping out: a breath sucked in backwards, glass cracking, your own voice sung back wrong."""
    n = K.ns(5.5)
    breath = norm(K.bandpass(K.noise(K.ns(1.6), "pink", 291), 900, 1.0)) * K.line(K.ns(1.6), [(0, 0), (0.95, 1), (1, 0)])
    x = at(n, 0, breath, 0.5)
    x += at(n, 1.55, crack(293, True), 0.7)
    voice = K.choir(hz("D4"), K.ns(3.0), "a", voices=3, seed=295, bright=0.3)[::-1] * K.swell(K.ns(3.0), 0.6, 1.4)
    x += at(n, 1.8, K.mono(K.chorus(voice, 10.0, 0.4, 2, 297, 0.5)), 0.35)
    return wet(x, "crypt", 0.45)


def console(v):
    """The console woken: a key pressed, the screen coming on with a soft hum, three beeps as it logs you in."""
    n = K.ns(2.6)
    x = at(n, 0, K.lowpass(trim(rate(clip("impact-sounds", "impactGeneric_light_002"), 1.6), 0, None, 0.1), 3000, 2), 0.4)
    x += at(n, 0.1, ours(("ui_holo_open", "ui_holo_confirm")[v]), 0.5)
    for i, f in enumerate((1320, 1760, 2640) if v == 0 else (1760, 1320, 1980)):
        x += at(n, 0.7 + 0.14 * i, beep(f), 0.25)
    return wet(x, "small", 0.3)


def lockdown(v):
    """Lockdown: the klaxon starting, doors slamming shut down the corridors one after another, the air going still."""
    n = K.ns(7.0)
    x = klaxon(n, 301) * K.line(n, [(0, 0), (0.03, 1), (0.85, 1), (1, 0)]) * 0.35
    for i in range(4):
        door = trim(rate(clip("impact-sounds", "impactPlate_heavy_00" + str(i % 4)), 0.7), 0, None, 0.4)
        x += at(n, 0.9 + 1.2 * i, K.lowpass(door, 3000 - 500 * i, 2), 0.8 - 0.12 * i)
        x += at(n, 0.95 + 1.2 * i, trim(ours(HISSES[i % 3]), 0, 1.0, 0.4), 0.2)
    return wet(x, "shaft", 0.35)


def breach(v):
    """A breach: a tank's glass giving way, the green pouring out over the floor, something big getting up out of it."""
    n = K.ns(6.0)
    x = at(n, 0, trim(rate(clip("impact-sounds", "impactGlass_heavy_001"), 0.7), 0, None, 0.4), 0.9)
    gush = norm(K.lowpass(K.noise(K.ns(3.0), "pink", 311), 1400, 2)) * K.swell(K.ns(3.0), 0.15, 1.2)
    x += at(n, 0.15, gush, 0.6)
    for i in range(4):
        x += at(n, 0.6 + 0.35 * i, K.lowpass(rate(ours(BUBBLES[i]), 0.5), 900, 2), 0.4)
    x += at(n, 2.6, K.lowpass(trim(rate(ours("fauna/gloomstalker/ambient1"), 0.6), 0, None, 0.6), 1400, 2), 0.7)
    x += klaxon(n, 313, (700, 520), 0.6) * K.line(n, [(0, 0), (0.5, 0), (0.6, 0.25), (1, 0.15)])
    return wet(x, "shaft", 0.35)


def glitch(v):
    """The Simulation glitching (and the Glitch itself): a sound torn up and repeated, a screen's buzz, a dropout."""
    n = K.ns(2.8 + v)
    src = np.concatenate([ours(CHIRPS[v]), ours("ui_holo_hover_1"), beep(880 + 220 * v, 0.4)])
    x = at(n, 0, crush(stutter(src, 321 + v, 7), 3, 5), 0.6)
    buzz = np.sign(K.sine(np.full(K.ns(0.8), 120.0), K.ns(0.8))) * 0.15 * K.perc(K.ns(0.8), 0.005, 0.6)
    x += at(n, 1.0, crush(buzz + norm(K.noise(K.ns(0.8), "white", 323)) * 0.1, 3, 9), 0.6)
    if v == 1:
        x += at(n, 2.1, crush(K.choir(hz("C4"), K.ns(1.4), "e", voices=2, seed=325, bright=0.4)[::-1], 4, 4), 0.25)
    return wet(x, "small", 0.25)


def draw(v):
    """The Simulation drawing itself in round you: a soft pencil scratch, a tiny bright ping as the block fills."""
    n = K.ns(1.2)
    r = K.rng(331 + v)
    x = np.zeros(n)
    for i in range(3):
        m = K.ns(r.uniform(0.08, 0.16))
        stroke = norm(K.bandpass(K.noise(m, "white", 333 + i + 7 * v), r.uniform(3500, 5200), 1.4)) * K.swell(m, 0.3, 1.0)
        x += at(n, 0.03 + 0.12 * i, stroke, 0.35)
    x += at(n, 0.4, K.glass(hz(("G6", "D7")[v]), K.ns(0.7), bright=0.7, t60=0.4, seed=335 + v), 0.25)
    return wet(x, "small", 0.2)


def musicbox(v):
    """The music box opened (and one playing somewhere in the house): it winds, and the lullaby's first bars play."""
    pre = winding(341 + v, 4)
    tune = melody(TUNE[:6] if v == 0 else TUNE[4:], 0.42, 343 + 11 * v)
    n = len(pre) + len(tune)
    x = at(n, 0, pre, 1.0) + at(n, len(pre) / K.SR, tune, 1.0)
    return wet(x, "crypt", 0.3)


def lullaby(v):
    """The lullaby: the whole tune on the music box, slow, with soft voices humming it under the tines."""
    tune = melody(TUNE, 0.55, 351, 1.15)
    n = len(tune) + K.ns(1.0)
    x = at(n, 0, tune, 1.0)
    hum = np.zeros(n)
    t = 0.0
    for i, (m, b) in enumerate(TUNE):
        seg = K.choir(hz(m) / 2, K.ns(0.55 * b + 0.3), "u", voices=4, seed=353 + i, bright=0.15) * K.swell(K.ns(0.55 * b + 0.3), 0.4, 1.0)
        hum += at(n, t, seg, 0.25)
        t += 0.55 * b * (1 + 0.15 * i / (len(TUNE) - 1))
    return wet(x + hum, "crypt", 0.45)


def alarm(v):
    """The alarm clock going off in the dream: the bells hammering, too loud, then cut dead."""
    n = K.ns(4.5)
    ring = trim(rate(ours("city/bell_alarm"), 1.9), 0, 3.0, 0.05)
    x = at(n, 0.1, ring * K.line(len(ring), [(0, 1), (0.97, 1), (1, 0)]), 0.9)
    x += at(n, 0.0, K.lowpass(trim(rate(clip("impact-sounds", "impactWood_light_002"), 1.2), 0, None, 0.1), 3000, 2), 0.5)
    return wet(x, "crypt", 0.35)


def stirs(v):
    """The Sleeper stirring: a huge slow breath in and out, the bed groaning under it, a low murmur that isn't a word."""
    n = K.ns(7.0)
    b = norm(K.lowpass(K.noise(n, "pink", 361), 700, 2))
    x = b * K.line(n, [(0, 0), (0.3, 0.8), (0.4, 0.6), (0.75, 0.9), (1, 0)]) * 0.5
    x += at(n, 1.6, K.lowpass(rate(ours("amb_world_elder_groan"), 0.6), 900, 2), 0.6)
    x += at(n, 3.4, K.lowpass(trim(rate(clip("rpg-audio", "creak3"), 0.55), 0, None, 0.4), 1400, 2), 0.5)
    murmur = K.choir(hz("A2"), K.ns(2.5), "m", voices=3, seed=363, bright=0.2) * K.swell(K.ns(2.5), 0.4, 1.4)
    x += at(n, 4.2, murmur, 0.35)
    return wet(x, "shaft", 0.45)


def sleepwalker(v):
    """The Sleepwalker: slow dragging steps, a long yawning moan sinking down, a music box running down as it walks."""
    n = K.ns(8.0)
    x = np.zeros(n)
    for i in range(4):
        st = trim(rate(clip("impact-sounds", "impactSoft_medium_00" + str(i % 4)), 0.6), 0, None, 0.3)
        drag = norm(K.bandpass(K.noise(K.ns(0.5), "pink", 371 + i), 900, 0.8)) * K.swell(K.ns(0.5), 0.2, 1.0)
        x += at(n, 0.2 + 1.3 * i, st, 0.6) + at(n, 0.3 + 1.3 * i, drag, 0.15)
    for i, note in enumerate(("E3", "C3", "A2")):
        m = K.ns(1.6)
        x += at(n, 1.0 + 0.9 * i, K.choir(hz(note), m, "o", voices=3, seed=373 + i, bright=0.25) * K.swell(m, 0.4, 1.2), 0.4 - 0.08 * i)
    x += at(n, 3.0, melody(TUNE[:5], 0.4, 375, 2.2, 3.0), 0.6)
    return wet(x, "crypt", 0.45)


def fold(v):
    """The hall folding back on itself: a rush pulled in backwards, a soft thump, a door clicking shut behind you."""
    n = K.ns(2.4)
    rush = rswell(norm(K.bandpass(K.noise(K.ns(0.5), "pink", 381), 1200, 0.8)) * K.perc(K.ns(0.5), 0.005, 0.3), "crypt")
    x = at(n, 0, rush[-K.ns(1.2):], 0.4)
    x += at(n, 1.15, trim(rate(clip("impact-sounds", "impactSoft_heavy_002"), 0.8), 0, None, 0.2), 0.5)
    x += at(n, 1.35, K.lowpass(trim(rate(clip("impact-sounds", "impactWood_light_000"), 1.3), 0, None, 0.1), 3200, 2), 0.35)
    return wet(x, "crypt", 0.35)


# ---- arrivals ---------------------------------------------------------------------------------------------------------

def arrive_frost(v):
    """Arriving at the Frost Monastery: the mountain wind rising, the big bell struck once far below, the monks' chant under it."""
    n = K.ns(6.0)
    x = wind(n, 391, 2400, 0.4) * K.swell(n, 0.5, 1.3) * 0.6
    x += at(n, 0.8, K.lowpass(trim(rate(G.bell("3446"), 0.78), 0, 5.0, 1.5), 1800, 2), 0.6)
    x += chant(hz("D3"), n, 393, 4.2, 0.6) * K.swell(n, 0.6, 1.5) * 0.35
    return wet(x, "field", 0.4)


def arrive_palace(v):
    """Arriving in the Mirror Palace: a glass ring drawn in backwards, then every mirror answering it, a clock ticking."""
    n = K.ns(6.0)
    sw = rswell(K.glass(hz("E6"), K.ns(1.0), bright=0.6, t60=0.8, seed=401), "field")
    x = at(n, 0, sw[-K.ns(2.0):], 0.5)
    for i, m in enumerate(("E6", "B5", "G#6", "E5")):
        x += at(n, 2.0 + 0.18 * i, K.glass(hz(m), K.ns(3.6), bright=0.6, t60=2.6, seed=403 + i, hit=0.6), 0.3)
    x += ticks(n, 0.5, 407, 2600, 0.0, 0.25) * K.line(n, [(0, 0), (0.4, 0), (0.6, 1), (1, 1)])
    return wet(x, "crypt", 0.45)


def arrive_lab(v):
    """Arriving in the Precursor Lab: the power coming up with a rising hum, the lights clicking on row by row, a boot chime."""
    n = K.ns(6.0)
    f = K.glide(n, 30, 60)
    hum = K.lowpass(np.sign(K.sine(f, n)) * 0.25 + K.sine(f * 2, n) * 0.4, 800, 2) * K.line(n, [(0, 0), (0.4, 1), (1, 0.7)])
    x = hum * 0.5
    for i in range(6):
        x += at(n, 1.0 + 0.32 * i, K.lowpass(trim(rate(clip("impact-sounds", "impactMetal_light_002"), 2.2), 0, None, 0.05), 4000, 2), 0.25)
    for i, m in enumerate(("C5", "G5", "E6")):
        x += at(n, 3.3 + 0.12 * i, K.sine(np.full(K.ns(1.6), hz(m)), K.ns(1.6)) * K.perc(K.ns(1.6), 0.005, 1.2), 0.25)
    return wet(x, "shaft", 0.35)


def arrive_nightmare(v):
    """Arriving in the Nightmare: a music box running down in the dark, a door creaking open, a slow heart."""
    n = K.ns(7.0)
    x = at(n, 0, melody(TUNE[:6], 0.45, 411, 2.0, 2.0), 0.7)
    x += at(n, 3.4, K.lowpass(trim(rate(clip("rpg-audio", "creak2"), 0.7), 0, None, 0.4), 1800, 2), 0.6)
    x += heart(n, 52, 413, 0.6) * K.line(n, [(0, 0), (0.55, 0), (0.7, 1), (1, 1)])
    return wet(x, "crypt", 0.45)


# ---- beds -------------------------------------------------------------------------------------------------------------

def bed_frost_chant(v):
    """The Cloisters and the Frozen Choir: a soft cold wind, the monks chanting a long way off, a prayer wheel's bell."""
    n = K.ns(BED)
    w = wind(n, 501, 1600, 0.3)
    ch = K.lowpass(chant(hz("D3"), n, 502, 6.0, 3.0), 900, 2) * 0.25
    wheels = scatter(n, 503, lambda r: ding(hz(("A5", "G5", "E5")[r.integers(3)]), int(r.integers(1 << 20))), 6, 0.08)
    flaps = scatter(n, 504, kick(CLOTH, 0.8, 0.95, 2400), 6, 0.1)
    return wide(w * 0.45 + ch, np.roll(w, K.ns(4.0)) * 0.45 + np.roll(ch, K.ns(0.03)), wheels, flaps)


def bed_frost_ice(v):
    """The Ice Caves: still cold air, the ice ticking and cracking as it settles, drips ringing, a low hum in the ice."""
    n = K.ns(BED)
    al, ar = air(511, 1500)
    hum = loop_of("amb_world_lumen_hum", 0.6, 500, 512) * 0.25
    cracks = scatter(n, 513, lambda r: crack(int(r.integers(1 << 20))), 7, 0.18)
    glints = scatter(n, 514, pick(CHIMES, 0.9, 1.05), 5, 0.08)
    return wide(al * 0.7 + hum, ar * 0.7 + hum, cracks, glints, scatter(n, 515, drips, 6, 0.2))


def bed_frost_wind(v):
    """The Cliff Stair and the Still Snow: high mountain wind, snow hissing past, prayer flags snapping, a bell far below."""
    n = K.ns(BED)
    sl, sr = loop_of("amb_frost_spires", 1.0, 4000, 521), loop_of("amb_frost_spires", 1.0, 4000, 522)
    w = wind(n, 523, 2200, 0.45)
    flags = scatter(n, 524, kick(CLOTH, 1.0, 1.2, 3600), 10, 0.14)
    bell = scatter(n, 525, G.far_bell, 1, 0.12)
    return wide(sl * 0.5 + w * 0.4 + snow(n, 526, 0.05), sr * 0.5 + np.roll(w, K.ns(5.0)) * 0.4 + snow(n, 527, 0.05), flags, bell)


def bed_palace_halls(v):
    """The Gallery and the Mirror Maze: a vast quiet room, candles guttering, a clock ticking, the mirrors singing faintly."""
    n = K.ns(BED)
    tone = loop_of("amb_world_stillpoint_tone", 1.0, 2400, 531) * 0.12
    pad = K.pad(hz("E3"), n, voices=5, seed=532, bright=0.15) * 0.08
    t = ticks(n, 1.0, 533, 2600, 0.0, 0.1)
    glints = scatter(n, 534, pick(GLINTS, 0.95, 1.05), 7, 0.08)
    gutter = scatter(n, 535, lambda r: K.crackle(K.ns(0.8), 20, int(r.integers(1 << 20)), 0.5) * K.perc(K.ns(0.8), 0.01, 0.5), 8, 0.05)
    return wide(tone + pad + t, np.roll(tone, K.ns(2.0)) + pad + np.roll(t, K.ns(0.3)), glints, gutter)


def bed_palace_under(v):
    """The Inverted Palace and the Room Behind the Glass: the palace's own sounds coming back backwards and muffled."""
    n = K.ns(BED)
    tone = loop_of("amb_world_stillpoint_tone", 0.85, 1200, 541) * 0.15
    pad = K.pad(hz("C3"), n, voices=5, seed=542, bright=0.12) * 0.08
    back = scatter(n, 543, lambda r: rswell(K.glass(hz(("E6", "B5", "G#5")[r.integers(3)]), K.ns(0.8), bright=0.5, t60=0.6,
                                                         seed=int(r.integers(1 << 20))), "crypt")[-K.ns(2.5):], 6, 0.1)
    t = ticks(n, 1.0, 544, 1800, 0.0, 0.06)[::-1]
    return wide(tone + pad + t, np.roll(tone, K.ns(3.0)) + pad + t, back)


def bed_palace_white(v):
    """The White: almost nothing, a soft high air, a pure note on the edge of hearing, and a whale calling a long way off."""
    n = K.ns(BED)
    al, ar = K.highpass(air(551, 6000)[0], 1500, 2), K.highpass(air(552, 6000)[1], 1500, 2)
    tone = K.sine(np.full(n, hz("E6")), n) * (0.4 + 0.6 * np.clip(0.5 + K.smooth_noise(n, 0.1, 553), 0, 1)) * 0.03
    songs = scatter(n, 554, lambda r: K.lowpass(wet(whale_song(int(r.integers(1 << 20)), 6.0), "field", 0.8), 700, 2), 2, 0.25)
    return wide(al * 0.4 + tone, ar * 0.4 + tone, songs)


def bed_lab_hum(v):
    """The Facility and Containment: the air plant's hum, the strip lights buzzing, the tanks bubbling, a beep now and then."""
    n = K.ns(BED)
    hum = loop_of("amb_world_halo_station", 0.8, 1400, 561) * 0.5
    t = np.arange(n) / K.SR
    buzz = (np.sin(2 * np.pi * 120 * t) * 0.5 + np.sin(2 * np.pi * 240 * t) * 0.3 + np.sin(2 * np.pi * 360 * t) * 0.15) * 0.04
    bub = scatter(n, 562, pick(BUBBLES, 0.7, 0.9, 1400), 9, 0.12)
    beeps = scatter(n, 563, lambda r: beep(float(r.choice([1320, 1760, 2093])), 0.08), 6, 0.05)
    chirps = scatter(n, 564, pick(CHIRPS, 0.95, 1.05), 3, 0.06)
    return wide(hum + buzz, np.roll(hum, K.ns(2.5)) + buzz, bub, beeps, chirps)


def bed_lab_servers(v):
    """The Server Abyss: fans roaring softly in the dark, drives clicking, the deep hum of the stack, lights chirping."""
    n = K.ns(BED)
    fl = norm(K.bandpass(K.noise(n, "pink", 571), 900, 0.5)) * 0.18
    fr = norm(K.bandpass(K.noise(n, "pink", 572), 950, 0.5)) * 0.18
    deep = loop_of("amb_world_halo_deep", 1.0, 400, 573) * 0.35
    clicks = scatter(n, 574, lambda r: ticks(K.ns(0.6), r.uniform(0.04, 0.09), int(r.integers(1 << 20)), r.uniform(3500, 5000), 0.02, 0.5), 18, 0.08)
    chirps = scatter(n, 575, pick(CHIRPS, 1.1, 1.3), 5, 0.05)
    return wide(fl + deep, fr + deep, clicks, chirps)


def bed_lab_sim(v):
    """The Simulation and the Garden Dome: a calm synthetic pad, made birdsong a little too perfect, soft bleeps."""
    n = K.ns(BED)
    pad = K.pad(hz("A3"), n, voices=6, seed=581, bright=0.2) * 0.1 + K.pad(hz("E4"), n, voices=4, seed=582, bright=0.15) * 0.06
    birds = scatter(n, 583, lambda r: crush(trim(ours("city/songbird" + str(1 + r.integers(5))), 0, None, 0.3), 6, 2), 7, 0.1)
    bleeps = scatter(n, 584, lambda r: K.glass(hz(str(r.choice(["A5", "C#6", "E6", "A6"]))), K.ns(1.2), bright=0.4, t60=0.9,
                                                seed=int(r.integers(1 << 20))), 9, 0.06)
    return wide(pad, np.roll(pad, K.ns(1.5)), birds, bleeps)


def bed_dream_house(v):
    """The House and the Nursery: an old house at night, a clock, the boards creaking, wind at the window, an owl outside."""
    n = K.ns(BED)
    w = wind(n, 591, 900, 0.5) * 0.25
    t = ticks(n, 1.0, 592, 1800, 0.0, 0.08)
    creaks = scatter(n, 593, pick(CREAKS, 0.8, 0.95, 1800), 6, 0.14)
    owls = scatter(n, 594, pick(["wild/owl1", "wild/owl2", "wild/owl3"], 0.95, 1.0, 2600), 2, 0.08)
    box = scatter(n, 595, lambda r: K.lowpass(melody(TUNE[:4], 0.5, int(r.integers(1 << 20))), 2200, 2), 1, 0.06)
    return wide(w + t, np.roll(w, K.ns(3.0)) + np.roll(t, K.ns(0.2)), creaks, owls, box)


def bed_dream_hall(v):
    """The Endless Hall: a long low drone, steps somewhere down the hall that stop when you listen, a far door closing."""
    n = K.ns(BED)
    d = K.lowpass(K.drone(55, n, seed=601), 500, 2) * 0.18
    al, ar = air(602, 1200)
    far = scatter(n, 603, lambda r: K.lowpass(wet(steps(K.ns(2.4), 0.0, 0.55, 4, int(r.integers(1 << 20)), 0.8, 0.8), "shaft", 0.6), 1400, 2), 4, 0.12)
    doors = scatter(n, 604, kick([("impact-sounds", "impactWood_heavy_001"), ("impact-sounds", "impactWood_medium_002")], 0.7, 0.8, 1000), 3, 0.12)
    return wide(d + al * 0.5, d + ar * 0.5, far, doors)


def bed_dream_deep(v):
    """Upside Down and the Sleeper's Dream: the dream's air, slow swells, a sleeping heart, the lullaby backwards and far."""
    n = K.ns(BED)
    dl, dr = loop_of("amb_world_dream_air", 0.9, 2000, 611), loop_of("amb_world_dream_air", 0.9, 2000, 612)
    h = heart(n, 40, 613, 0.12)
    swells = scatter(n, 614, pick(["amb_world_dream_swell_" + str(i) for i in range(1, 5)], 0.75, 0.9), 6, 0.15)
    back = scatter(n, 615, lambda r: K.lowpass(melody(TUNE[2:7], 0.5, int(r.integers(1 << 20)))[::-1], 1600, 2), 2, 0.07)
    return wide(dl * 0.6 + h, dr * 0.6 + h, swells, back)


# file, render, variants, rms, event, subtitle, attenuation (events) or 0 (a bed, streamed)
SPEC = [
    ("wheel", wheel, 2, -22.0, "gate.world.wheel", "A prayer wheel turns", 16),
    ("bells", bells, 1, -20.0, "gate.world.bells", "Monastery bells ring out", 64),
    ("blizzard", blizzard, 1, -21.0, "gate.world.blizzard", "A blizzard blows in", 48),
    ("icecrack", icecrack, 2, -21.0, "gate.world.icecrack", "Ice cracks", 32),
    ("still", still, 1, -22.0, "gate.world.still", "The wind stops dead", 64),
    ("monk", monk, 1, -20.0, "gate.world.monk", "A deep chant rises out of the snow", 64),
    ("mirror", mirror, 2, -22.0, "gate.world.mirror", "A mirror rings", 24),
    ("shatter", shatter, 1, -19.0, "gate.world.shatter", "Mirrors shatter", 48),
    ("doubled", doubled, 1, -22.0, "gate.world.doubled", "Your footsteps echo out of step", 32),
    ("whale", whale, 1, -21.0, "gate.world.whale", "A whale sings far away", 96),
    ("reflection", reflection, 1, -20.0, "gate.world.reflection", "Your reflection steps out of the glass", 48),
    ("console", console, 2, -22.0, "gate.world.console", "A console beeps", 16),
    ("lockdown", lockdown, 1, -19.0, "gate.world.lockdown", "Alarms sound and doors slam shut", 64),
    ("breach", breach, 1, -19.0, "gate.world.breach", "A tank bursts", 48),
    ("glitch", glitch, 2, -21.0, "gate.world.glitch", "Something glitches", 24),
    ("draw", draw, 2, -24.0, "gate.world.draw", "The world draws itself in", 12),
    ("musicbox", musicbox, 2, -22.0, "gate.world.musicbox", "A music box plays", 24),
    ("lullaby", lullaby, 1, -21.0, "gate.world.lullaby", "A lullaby plays", 48),
    ("alarm", alarm, 1, -19.0, "gate.world.alarm", "An alarm clock rings", 48),
    ("stirs", stirs, 1, -20.0, "gate.world.stirs", "Something huge breathes in its sleep", 64),
    ("sleepwalker", sleepwalker, 1, -20.0, "gate.world.sleepwalker", "Slow footsteps drag closer", 48),
    ("fold", fold, 1, -23.0, "gate.world.fold", "The hall folds back on itself", 16),
    ("arrive_frost_monastery", arrive_frost, 1, -22.0, "gate.world.arrive_frost_monastery", "Wind and a far bell", 16),
    ("arrive_mirror_palace", arrive_palace, 1, -22.0, "gate.world.arrive_mirror_palace", "Glass rings all around", 16),
    ("arrive_precursor_lab", arrive_lab, 1, -22.0, "gate.world.arrive_precursor_lab", "The power comes on", 16),
    ("arrive_nightmare", arrive_nightmare, 1, -22.0, "gate.world.arrive_nightmare", "A music box winds down", 16),
    ("amb_frost_chant", bed_frost_chant, 1, -28.5, "gate.world.frost_chant", "Monks chant far off", 0),
    ("amb_frost_ice", bed_frost_ice, 1, -29.0, "gate.world.frost_ice", "Ice ticks and cracks", 0),
    ("amb_frost_wind", bed_frost_wind, 1, -28.0, "gate.world.frost_wind", "Mountain wind and snapping flags", 0),
    ("amb_palace_halls", bed_palace_halls, 1, -29.5, "gate.world.palace_halls", "A clock ticks in a vast quiet room", 0),
    ("amb_palace_under", bed_palace_under, 1, -29.5, "gate.world.palace_under", "Sounds come back backwards", 0),
    ("amb_palace_white", bed_palace_white, 1, -30.0, "gate.world.palace_white", "A pure note in the white", 0),
    ("amb_lab_hum", bed_lab_hum, 1, -28.5, "gate.world.lab_hum", "Machines hum and lights buzz", 0),
    ("amb_lab_servers", bed_lab_servers, 1, -28.5, "gate.world.lab_servers", "Server fans whir and drives click", 0),
    ("amb_lab_sim", bed_lab_sim, 1, -29.0, "gate.world.lab_sim", "Birdsong that sounds too perfect", 0),
    ("amb_dream_house", bed_dream_house, 1, -29.0, "gate.world.dream_house", "An old house creaks at night", 0),
    ("amb_dream_hall", bed_dream_hall, 1, -29.0, "gate.world.dream_hall", "Footsteps far down a hall", 0),
    ("amb_dream_deep", bed_dream_deep, 1, -29.0, "gate.world.dream_deep", "A slow heart beats in the dark", 0),
]


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    return C.main(argv, SPEC, "the C themes")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
