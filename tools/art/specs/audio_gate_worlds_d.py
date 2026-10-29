"""The D themes' sound (docs/SPEC.md §38.11): the Drowned Archive, the Clockwork Foundry, the Glass Desert and the Lantern
Night Market. Three beds a theme, the touchstones, the events, the wonders' moments and an arrival sting each. Rebuild:

    python3 tools/art/specs/audio_gate_worlds_d.py            render every file and write the JSON
    python3 tools/art/specs/audio_gate_worlds_d.py --json     only the JSON

Recorded where there's a recording: the market's crowd, stalls, canal water, boats and cart bells are the city's own
(sounds/city and audio_city_life.py's loops), the foundry's hum, clunks and hiss are the Halo station's, the desert's wind is
the Glass Steppe's and the lands' high wind, its birds the wild's buzzards and the city's pigeons, the archive's tide is the
Tell's, and the glass sings with the world chimes and the Lumen's glass. What nothing records is made here: paper (cloth
and wings sped up and thinned), a clock's ticking and a machine's turning (struck metal on a grid), fireworks (a whistle,
a boom and a crackle) and the market's voices rising (the choir). Vanilla's sounds are only referenced, never mixed in.

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

BED = G.BED
at, trim, rate, ours, clip, wet, tile, scatter, wide, water, air, drips = (G.at, G.trim, G.rate, G.ours, G.clip, G.wet, G.tile,
                                                                         G.scatter, G.wide, G.water, G.air, G.drips)
norm, pick, kick, loop_of = C.norm, C.pick, C.kick, C.loop_of

CHIMES = ["amb_world_chime_" + str(i) for i in range(1, 7)]
GLINTS = ["amb_world_lumen_chime_" + str(i) for i in range(1, 7)]
CLUNKS = ["amb_world_halo_clunk_" + str(i) for i in range(1, 4)]
HISSES = ["amb_world_halo_hiss_" + str(i) for i in range(1, 4)]
CREAKS = ["city/boat_creak" + str(i) for i in range(1, 5)]
STALLS = ["city/stall" + str(i) for i in range(1, 7)]
BUBBLES = ["amb_cave_sulfur_bubble_" + str(i) for i in range(1, 5)]


def paper(seed, length=0.35, bright=1.0):
    """One page turning: a dry flick of thin noise, a little rise and a fall, the paper's rattle in it."""
    r = K.rng(seed)
    n = K.ns(length)
    x = norm(K.bandpass(K.noise(n, "white", seed), r.uniform(2600, 4200) * bright, 0.9))
    rattle = 0.6 + 0.4 * np.sign(np.sin(2 * np.pi * r.uniform(40, 70) * np.arange(n) / K.SR))
    return x * rattle * K.swell(n, r.uniform(0.25, 0.45), 1.4)


def pages_pick(rnd):
    return paper(int(rnd.integers(1 << 30)), rnd.uniform(0.25, 0.5))


def flutter(rnd):
    """A loose page blown past: the pigeons' wings, quicker and thinner."""
    x = trim(rate(ours("city/pigeon_flutter" + str(1 + rnd.integers(3))), rnd.uniform(1.3, 1.6)), 0, None, 0.15)
    return K.highpass(x, 1400, 2)


def tick(f, seed, t60=0.05):
    """A clock's tick: a small struck bar, very short."""
    return K.bar(f, K.ns(0.12), t60=t60, bright=0.8, seed=seed) * 0.6


def ticks(n, period, seed, f=2400, jitter=0.0, gain=1.0, accel=1.0):
    """A run of ticks every `period` seconds (tightening by `accel` each tick), tick and tock a little apart."""
    out = np.zeros(n)
    r = K.rng(seed)
    t, i = 0.05, 0
    while t < n / K.SR - 0.15:
        out += at(n, t, tick(f * (1.0 if i % 2 == 0 else 0.84), seed + i % 4), gain)
        period *= accel
        t += max(0.03, period + r.uniform(-jitter, jitter))
        i += 1
    return out


def flame(v, r=1.0):
    """A lantern catching: the Rig's ignition, softened, higher."""
    return K.lowpass(trim(rate(ours("gate_rig_ignite"), r), 0, 1.6, 0.4), 4200, 2)


def wind(n, seed, lp=2600, gusts=0.35):
    """Open wind: the lands' high wind tiled, gusting."""
    w = loop_of("amb_lands_wind_high", 1.0, lp, seed)[:n] if n == K.ns(BED) else K.lowpass(tile(ours("amb_lands_wind_high"), n, seed), lp, 2)
    return w * (1 - gusts + gusts * np.clip(0.5 + K.smooth_noise(n, 0.3, seed + 1), 0, 1))


def sand(n, seed, gain=1.0):
    """Sand on the move: fine high hiss, thickening in gusts."""
    s = norm(K.highpass(K.noise(n, "white", seed), 3800, 2)) * (0.4 + 0.6 * np.clip(0.5 + K.smooth_noise(n, 0.5, seed + 1), 0, 1))
    return s * gain


def bang(seed, far=False):
    """A firework: the whistle up, the bang and its crackle coming down."""
    r = K.rng(seed)
    up = K.ns(r.uniform(0.9, 1.3))
    f = K.glide(up, r.uniform(700, 900), r.uniform(1800, 2600))
    whistle = K.sine(f, up) * K.adsr(up, 0.05, 0.1, 0.8, 0.2) * 0.25
    whistle += norm(K.highpass(K.noise(up, "white", seed), 5000, 2)) * K.line(up, [(0, 0), (0.3, 0.15), (1, 0.05)])
    n = up + K.ns(3.2)
    x = at(n, 0, whistle)
    boom = K.sub_boom(K.ns(1.6), 90, 40, 0.9, seed) + norm(K.lowpass(K.noise(K.ns(1.6), "pink", seed + 1), 1800, 2)) * K.perc(K.ns(1.6), 0.002, 0.5)
    x += at(n, up / K.SR, boom, 1.0)
    x += at(n, up / K.SR + 0.25, K.crackle(K.ns(2.4), 60, seed + 2, 0.7) * K.perc(K.ns(2.4), 0.2, 2.0), 0.35)
    if far:
        x = K.lowpass(x, 1800, 2)
    return x


# ---- touchstones and events -----------------------------------------------------------------------------------------

def page(v):
    """The open book's page turned (and a paper bridge folding out): two pages turning, the archive's lamp answering in glass."""
    n = K.ns(2.2)
    x = at(n, 0, paper(11 + v, 0.42), 0.9) + at(n, 0.18, paper(13 + v, 0.35), 0.6)
    x += at(n, 0.0, K.lowpass(trim(clip("impact-sounds", "impactSoft_medium_00" + str(v)), 0, None, 0.2), 1600, 2), 0.35)
    x += at(n, 0.35, ours(GLINTS[2 + v]), 0.18)
    return wet(x, "crypt", 0.3)


def pages(v):
    """The page storm: a gust through the stacks, and every loose page in it flapping past."""
    n = K.ns(5.0)
    g = wind(n, 21 + v, 3200, 0.6) * K.swell(n, 0.4, 1.5)
    x = g * 0.7
    l, r = scatter(n, 23 + v, flutter, 14, 0.4)
    pl, pr = scatter(n, 25 + v, pages_pick, 18, 0.35)
    return wet(x + (l + r) * 0.5 + (pl + pr) * 0.5, "shaft", 0.3)


def tide(v):
    """The tide coming in through the stacks: a wash of water rising, shelves creaking as it lifts them."""
    n = K.ns(6.0)
    t = K.lowpass(trim(ours("amb_tell_tide"), 0, 6.0, 1.5), 2400, 2)
    x = at(n, 0, t * K.swell(len(t), 0.45, 1.3), 1.0)
    x += at(n, 1.4, K.lowpass(trim(rate(ours(CREAKS[v]), 0.7), 0, None, 0.4), 1400, 2), 0.4)
    x += at(n, 3.0, K.lowpass(trim(rate(ours(CREAKS[2 + v]), 0.65), 0, None, 0.4), 1400, 2), 0.3)
    return wet(x, "shaft", 0.35)


def bookworm(v):
    """The Bookworm rising: the ink heaving and gulping, paper tearing as something long pushes up through it, a low cry."""
    n = K.ns(6.5)
    x = at(n, 0, K.sub_boom(K.ns(3.0), 38, 24, 2.4), 0.8)
    for i in range(7):
        x += at(n, 0.1 + i * 0.3, K.lowpass(rate(ours(BUBBLES[i % 4]), 0.55), 1200, 2), 0.6)
    for i in range(3):
        rip = norm(K.bandpass(K.noise(K.ns(0.6), "white", 31 + i), 2200, 0.6)) * K.swell(K.ns(0.6), 0.2, 1.2)
        x += at(n, 1.2 + i * 0.5, rip * (0.7 + 0.3 * np.sign(np.sin(2 * np.pi * 90 * np.arange(K.ns(0.6)) / K.SR))), 0.35)
    x += at(n, 2.0, K.lowpass(trim(rate(ours("fauna/dune_wyrm/roar1"), 0.6), 0, None, 0.8), 1500, 2), 0.75)
    return wet(x, "shaft", 0.4)


def valve(v):
    """The old valve turned: a stiff wheel squeaking round, then steam hissing out of the joint."""
    n = K.ns(3.0)
    x = at(n, 0, trim(rate(clip("impact-sounds", ("impactMetal_light_001", "impactMetal_light_004")[v]), 0.75), 0, None, 0.2), 0.6)
    sq = K.sine(K.glide(K.ns(0.5), 1300, 1700), K.ns(0.5)) * K.swell(K.ns(0.5), 0.3, 1.3)
    x += at(n, 0.05, K.bandpass(sq, 1500, 2) * 0.25, 1.0)
    x += at(n, 0.45, trim(ours(HISSES[v]), 0, 2.4, 0.8), 0.8)
    return wet(x, "crypt", 0.25)


def overdrive(v):
    """The machines running away: a hum climbing, clunks coming faster and faster, steam bursting out."""
    n = K.ns(6.0)
    f = K.glide(n, 48, 140)
    hum = K.lowpass(np.sign(K.sine(f, n)) * 0.3 + K.sine(f * 2, n) * 0.4, 900, 2) * K.adsr(n, 1.5, 0.5, 0.8, 1.0)
    x = hum * 0.5
    t, p = 0.2, 0.7
    while t < 5.2:
        x += at(n, t, K.lowpass(trim(ours(CLUNKS[int(t * 3) % 3]), 0, 0.9, 0.2), 2400, 2), 0.35)
        t += p
        p = max(0.12, p * 0.82)
    x += ticks(n, 0.25, 41 + v, 2200, 0.0, 0.25, 0.93)
    x += at(n, 3.8, trim(ours(HISSES[2]), 0, 2.0, 0.6), 0.6)
    return wet(x, "shaft", 0.3)


def powercut(v):
    """The power going: the hum falling away to nothing, a breaker's clunk, a last few sparks."""
    n = K.ns(4.5)
    f = K.glide(K.ns(2.5), 110, 30)
    hum = K.lowpass(np.sign(K.sine(f, K.ns(2.5))) * 0.3 + K.sine(f, K.ns(2.5)) * 0.5, 700, 2) * np.linspace(1, 0, K.ns(2.5)) ** 1.5
    x = at(n, 0.15, hum, 0.6)
    x += at(n, 0, trim(rate(clip("impact-sounds", "impactPlate_heavy_00" + str(v)), 0.7), 0, None, 0.3), 0.8)
    x += at(n, 0.4, K.crackle(K.ns(1.6), 45, 51 + v, 0.8) * K.perc(K.ns(1.6), 0.01, 0.8), 0.25)
    return wet(x, "shaft", 0.4)


def clockwork(v):
    """The Clock-Keeper coming: every clock starting at once out of time, catching each other up, then one deep chime."""
    n = K.ns(7.0)
    x = np.zeros(n)
    for i, (p, f) in enumerate(((0.5, 2400), (0.41, 1900), (0.33, 3000), (0.62, 1500))):
        x += ticks(n, p, 61 + i, f, 0.02, 0.3, 0.97) * np.clip(np.linspace(-0.2 * i, 1.6, n), 0, 1) * K.line(n, [(0, 1), (0.7, 1), (0.78, 0), (1, 0)])
    x += at(n, 5.0, K.bell(K.hz(K.midi("C3")), K.ns(2.0), t60=3.5, bright=0.5, seed=63), 0.8)
    x += at(n, 5.0, K.sub_boom(K.ns(1.5), 60, 40, 1.4), 0.4)
    return wet(x, "shaft", 0.4)


def chime(v):
    """The first mirror flashed (and a chime in the glass far off): a struck glass, bright, a second one answering."""
    n = K.ns(3.6)
    f0 = K.hz(K.midi(("E6", "B5")[v]))
    x = at(n, 0, K.glass(f0, K.ns(3.2), bright=0.8, t60=2.2, seed=71 + v), 0.8)
    x += at(n, 0.12, K.glass(f0 * 1.5, K.ns(2.6), bright=0.6, t60=1.6, seed=73 + v, hit=0.4), 0.35)
    return wet(x, "field", 0.3)


def sandstorm(v):
    """A sandstorm blowing in: the wind rising to a howl, sand hissing along the ground and against stone."""
    n = K.ns(7.0)
    w = wind(n, 81 + v, 3000, 0.5) * K.swell(n, 0.55, 1.2)
    howl = K.bandpass(K.noise(n, "pink", 83 + v), 520, 3.0) * K.swell(n, 0.6, 1.6)
    x = w * 0.7 + norm(howl) * 0.3 + sand(n, 85 + v, 0.35) * K.swell(n, 0.6, 1.0)
    return x


def shimmer(v):
    """The mirage: the dream's shimmer, high glass voices drifting in and out of tune."""
    n = K.ns(6.0)
    s = trim(ours("amb_world_dream_shimmer"), 4.0 + 6 * v, 6.0, 1.5)
    x = at(n, 0, s * K.swell(len(s), 0.5, 1.4), 0.8)
    for i, m in enumerate(("E5", "B5", "G#5")):
        x += at(n, 0.6 + 0.7 * i, K.glass(K.hz(K.midi(m)), K.ns(4.0), bright=0.4, t60=3.0, seed=91 + i, hit=0.0), 0.2)
    return K.chorus(wet(x, "field", 0.5), 9.0, 0.3, 2, 93, 0.5)


def walker(v):
    """The Sand Walker standing up: sand pouring upwards, three heavy soft steps, glass ringing in its chest."""
    n = K.ns(6.5)
    pour = norm(K.highpass(K.noise(K.ns(3.0), "pink", 101), 1200, 2))
    sweep = K.bandpass(pour, 2600, 0.6) * K.swell(K.ns(3.0), 0.7, 1.2)
    x = at(n, 0, sweep, 0.7)
    for i in range(3):
        x += at(n, 2.6 + i * 0.9, trim(rate(clip("impact-sounds", "impactSoft_heavy_00" + str(i + 1)), 0.5), 0, None, 0.4), 0.9)
        x += at(n, 2.6 + i * 0.9, sand(K.ns(0.7), 103 + i, 0.4) * K.perc(K.ns(0.7), 0.01, 0.5), 1.0)
    x += at(n, 1.8, K.glass(K.hz(K.midi("A4")), K.ns(4.0), bright=0.5, t60=3.0, seed=105), 0.35)
    return wet(x, "field", 0.35)


def wish(v):
    """A wish lantern lit: the paper catching with a soft breath of flame, a small bell, the glow settling."""
    n = K.ns(3.0)
    x = at(n, 0, flame(v, 1.2 + 0.1 * v), 0.6)
    x += at(n, 0.25, trim(rate(ours("city/handbell"), 1.6 + 0.1 * v), 0, 1.8, 0.8), 0.35)
    x += at(n, 0.4, ours(GLINTS[v]), 0.2)
    return wet(x, "field", 0.3)


def fireworks(v):
    """Fireworks over the market: one goes up and bursts, another a moment behind it, further off."""
    a = bang(111 + v)
    b = bang(113 + v, far=True)
    n = len(a) + K.ns(1.0)
    x = at(n, 0, a, 1.0) + at(n, 0.7, b, 0.4)
    return wet(x, "field", 0.35)


def release(v):
    """Every lantern let go: the crowd sighing, flames catching all round, and the lanterns lifting on the night air."""
    n = K.ns(7.0)
    sigh = K.choir(K.hz(K.midi("A3")), n, "a", voices=8, seed=121, bright=0.25) * K.swell(n, 0.35, 1.6)
    x = sigh * 0.35
    for i in range(10):
        x += at(n, 0.3 + i * 0.35, flame(i, 1.1 + 0.05 * (i % 4)), 0.18)
    x += wind(n, 123, 1800, 0.3) * K.swell(n, 0.7, 1.2) * 0.3
    return wet(x, "field", 0.45)


def festival(v):
    """The great lantern going up: a drum beat, the crowd's cheer swelling, cart bells and far fireworks over it."""
    n = K.ns(8.0)
    x = np.zeros(n)
    for i in range(6):
        x += at(n, 0.2 + i * 0.5, K.lowpass(trim(rate(clip("impact-sounds", "impactPunch_heavy_00" + str(1 + i % 3)), 0.5), 0, None, 0.3), 900, 2), 0.6)
    cheer = K.choir(K.hz(K.midi("D4")), n, "a", voices=10, seed=131, bright=0.35) * K.swell(n, 0.55, 1.4)
    x += cheer * 0.35
    x += at(n, 2.5, trim(ours("city/cart_bells1"), 0, None, 0.8), 0.35)
    x += at(n, 4.0, bang(133, far=True), 0.4) + at(n, 5.2, bang(135, far=True), 0.3)
    return wet(x, "field", 0.4)


def spirits(v):
    """The lantern spirits: soft high voices on an "oo", glass chimes drifting with them, nothing quite solid."""
    n = K.ns(7.0)
    x = np.zeros(n)
    for i, m in enumerate(("E5", "B5", "G#5")):
        x += K.choir(K.hz(K.midi(m)), n, "u", voices=4, seed=141 + i, bright=0.2) * K.swell(n, 0.4 + 0.1 * i, 1.8) * 0.3
    for i in range(5):
        x += at(n, 0.8 + i * 1.0, ours(GLINTS[i]), 0.2)
    return K.chorus(wet(x, "field", 0.55), 12.0, 0.2, 2, 143, 0.5)


# ---- arrivals --------------------------------------------------------------------------------------------------------

def arrive_archive(v):
    """Arriving in the Drowned Archive: a deep hum under the water, a drip falling a long way into a pool, a page turning."""
    n = K.ns(6.0)
    x = K.lowpass(K.drone(49, n, seed=151), 500, 2) * K.swell(n, 0.4, 1.5) * 0.4
    x += at(n, 0.7, ours("amb_cave_drip_pool_2"), 0.7)
    x += at(n, 1.9, paper(153, 0.5), 0.6)
    x += at(n, 2.4, ours(GLINTS[3]), 0.2)
    return wet(x, "shaft", 0.5)


def arrive_foundry(v):
    """Arriving in the Clockwork Foundry: a great clunk as something engages, steam letting go, gears starting to tick."""
    n = K.ns(6.0)
    x = at(n, 0, trim(rate(clip("impact-sounds", "impactPlate_heavy_003"), 0.6), 0, None, 0.4), 0.9)
    x += at(n, 0, K.sub_boom(K.ns(1.6), 70, 40, 1.2), 0.5)
    x += at(n, 0.6, trim(ours(HISSES[0]), 0, 2.4, 0.8), 0.6)
    x += ticks(n, 0.5, 161, 2200, 0.0, 0.3, 0.98) * np.clip(np.linspace(-0.8, 1.2, n), 0, 1)
    return wet(x, "shaft", 0.4)


def arrive_desert(v):
    """Arriving in the Glass Desert: the wind rising over the dunes, and a glass ringing somewhere in the sand."""
    n = K.ns(6.0)
    x = wind(n, 171, 2400, 0.4) * K.swell(n, 0.5, 1.3) * 0.7 + sand(n, 173, 0.15) * K.swell(n, 0.5, 1.3)
    x += at(n, 1.4, K.glass(K.hz(K.midi("E6")), K.ns(4.0), bright=0.7, t60=3.0, seed=175), 0.5)
    x += at(n, 1.9, K.glass(K.hz(K.midi("B6")), K.ns(3.5), bright=0.5, t60=2.4, seed=177, hit=0.3), 0.25)
    return wet(x, "field", 0.35)


def arrive_market(v):
    """Arriving at the Lantern Night Market: the crowd's chatter rising, a stall's coins, a little bell over the door."""
    n = K.ns(6.0)
    c = K.lowpass(trim(ours("amb_city_tavern"), 2.0, 6.0, 1.5), 3000, 2)
    x = at(n, 0, c * K.swell(len(c), 0.5, 1.2), 0.8)
    x += at(n, 0.9, trim(ours("city/stall2"), 0, None, 0.3), 0.5)
    x += at(n, 0.3, trim(rate(ours("city/handbell"), 1.4), 0, 2.0, 0.8), 0.45)
    return wet(x, "field", 0.3)


# ---- beds -------------------------------------------------------------------------------------------------------------

def bed_archive_stacks(v):
    """The Stacks: still air between the shelves, water dripping into pools, a page turning somewhere, shelves creaking."""
    n = K.ns(BED)
    al, ar = air(401, 1400)
    creak = scatter(n, 402, pick(CREAKS, 0.55, 0.7, 1300), 5, 0.18)
    pg = scatter(n, 403, pages_pick, 6, 0.12)
    pools = scatter(n, 404, pick(["amb_cave_drip_pool_1", "amb_cave_drip_pool_2", "amb_cave_drip_pool_3"], 0.9, 1.0), 6, 0.25)
    return wide(al * 0.7, ar * 0.7, creak, pg, pools)


def bed_archive_atrium(v):
    """The Atrium and the Sunken Shelves: water lapping under the dome, a low glassy hum, drips ringing off the glass."""
    n = K.ns(BED)
    wl, wr = water(411, 1500)
    hum = loop_of("amb_world_lumen_hum", 0.8, 800, 412) * 0.3
    return wide(wl * 0.8 + hum, wr * 0.8 + hum, scatter(n, 413, drips, 9, 0.28), scatter(n, 414, pick(GLINTS, 0.7, 0.8), 4, 0.08))


def bed_archive_ink(v):
    """The Inkwell and the Last Reader's Room: thick dark water, the ink gulping, pages flapping far off, a deep hum."""
    n = K.ns(BED)
    wl, wr = water(421, 700)
    deep = loop_of("amb_rift_rumble", 0.7, 220, 422) * 0.2
    gulps = scatter(n, 423, pick(BUBBLES, 0.5, 0.65, 900), 8, 0.22)
    far = scatter(n, 424, lambda r: K.lowpass(flutter(r), 1800, 2), 6, 0.1)
    return wide(wl * 0.7 + deep, wr * 0.7 + deep, gulps, far)


def bed_foundry_works(v):
    """The Works Floor: the machines' hum, a clunk now and then, steam letting go, gears ticking over in the pits."""
    n = K.ns(BED)
    hum = loop_of("amb_world_halo_station", 0.85, 1600, 431)
    hl, hr = hum * 0.6, np.roll(hum, K.ns(3.0)) * 0.6
    t = ticks(n, 0.36, 432, 1800, 0.0, 0.08)
    clunks = scatter(n, 433, pick(CLUNKS, 0.8, 0.95, 2400), 8, 0.2)
    hiss = scatter(n, 434, pick(HISSES, 0.9, 1.0), 4, 0.12)
    return wide(hl + t, hr + np.roll(t, K.ns(0.11)), clunks, hiss)


def bed_foundry_pipes(v):
    """The Pipe Maze: steam hissing close, pipes knocking as the pressure moves through them, drips off the joints."""
    n = K.ns(BED)
    hum = loop_of("amb_world_halo_station", 0.7, 900, 441) * 0.35
    hiss = scatter(n, 442, pick(HISSES, 0.85, 1.1), 10, 0.22)
    knocks = scatter(n, 443, kick([("impact-sounds", "impactMetal_medium_001"), ("impact-sounds", "impactMetal_medium_004")], 0.6, 0.8, 1400), 9, 0.16)
    return wide(hum, hum * 0.9, hiss, knocks, scatter(n, 444, drips, 5, 0.18))


def bed_foundry_engine(v):
    """The Great Engine, the Stopped Hour and the Orrery: one huge slow beat, a ratchet turning, a deep drone under it."""
    n = K.ns(BED)
    d = K.lowpass(K.drone(41, n, seed=451), 400, 2) * 0.3
    beat = np.zeros(n)
    t = 0.5
    while t < BED - 2:
        beat += at(n, t, K.sub_boom(K.ns(1.4), 62, 44, 1.0), 0.45)
        beat += at(n, t + 0.05, K.lowpass(trim(rate(ours(CLUNKS[int(t) % 3]), 0.6), 0, 1.0, 0.3), 1200, 2), 0.18)
        t += 2.5
    ratchet = ticks(n, 0.625, 452, 1300, 0.0, 0.07)
    return wide(d + beat + ratchet, d + beat * 0.95 + np.roll(ratchet, K.ns(0.2)), scatter(n, 453, pick(CHIMES, 0.5, 0.6, 1800), 2, 0.06))


def bed_desert_wind(v):
    """The Colossus and the Mirage: wind over the dunes, sand hissing, a buzzard high up, the steppe's glass far away."""
    n = K.ns(BED)
    sl, sr = loop_of("amb_glass_steppe", 1.0, 5000, 461), loop_of("amb_glass_steppe", 1.0, 5000, 462)
    w = wind(n, 463, 2200, 0.4)
    birds = scatter(n, 464, pick(["wild/buzzard1", "wild/buzzard2", "wild/buzzard3"], 0.95, 1.0, 3800), 3, 0.08)
    return wide(sl * 0.5 + w * 0.4 + sand(n, 465, 0.04), sr * 0.5 + np.roll(w, K.ns(4.0)) * 0.4 + sand(n, 466, 0.04), birds)


def bed_desert_city(v):
    """The Sunken City and the Hourglass: a softer wind between the roofs, sand trickling down, pigeons, a far bell."""
    n = K.ns(BED)
    w = wind(n, 471, 1600, 0.3)
    trickle = scatter(n, 472, lambda r: sand(K.ns(1.8), int(r.integers(1 << 20)), 0.6) * K.swell(K.ns(1.8), 0.3, 1.0), 8, 0.12)
    coos = scatter(n, 473, pick(["city/pigeon_coo1", "city/pigeon_coo2", "city/pigeon_coo3"], 0.95, 1.05), 5, 0.12)
    bell = scatter(n, 474, pick(["city/bell_district"], 0.8, 0.85, 1400), 1, 0.12)
    return wide(w * 0.5, np.roll(w, K.ns(5.0)) * 0.5, trickle, coos, bell)


def bed_desert_glass(v):
    """The Glass Caves: cave air, glass chiming as the light warms it, sand sliding down the walls, a drip."""
    n = K.ns(BED)
    al, ar = air(481, 1600)
    glassy = scatter(n, 482, pick(CHIMES, 0.95, 1.1), 8, 0.12)
    slides = scatter(n, 483, lambda r: sand(K.ns(1.5), int(r.integers(1 << 20)), 0.5) * K.swell(K.ns(1.5), 0.3, 1.0), 6, 0.12)
    return wide(al * 0.7, ar * 0.7, glassy, slides, scatter(n, 484, drips, 4, 0.18))


def bed_market_street(v):
    """The Lantern Street and the Rooftops: the crowd's chatter, stalls at work, canal water, a cart's bells, crickets."""
    n = K.ns(BED)
    cl, cr = loop_of("amb_city_tavern", 1.0, 2800, 491), loop_of("amb_city_tavern", 1.0, 2800, 492)
    ll, lr = loop_of("amb_city_lap", 1.0, 1800, 493), loop_of("amb_city_lap", 1.0, 1800, 494)
    stalls = scatter(n, 495, pick(STALLS, 0.95, 1.05), 9, 0.14)
    carts = scatter(n, 496, pick(["city/cart_bells1", "city/cart_bells2"], 0.95, 1.0, 3200), 2, 0.1)
    crickets = scatter(n, 497, pick(["wild/cricket1", "wild/cricket2", "wild/cricket3"]), 6, 0.06)
    return wide(cl * 0.55 + ll * 0.3, cr * 0.55 + lr * 0.3, stalls, carts, crickets)


def bed_market_water(v):
    """The Floating Market: water lapping round the boats, hulls creaking against each other, the crowd across the water."""
    n = K.ns(BED)
    ll, lr = loop_of("amb_city_lap", 1.0, 2200, 501), loop_of("amb_city_lap", 1.0, 2200, 502)
    crowd = loop_of("amb_city_tavern", 1.0, 1400, 503) * 0.3
    creaks = scatter(n, 504, pick(CREAKS, 0.9, 1.05), 8, 0.2)
    return wide(ll * 0.7 + crowd, lr * 0.7 + crowd, creaks, scatter(n, 505, pick(STALLS, 0.95, 1.05, 2400), 4, 0.08))


def bed_market_festival(v):
    """The Festival and the Broker's Vault: a bigger crowd, a drum somewhere, bells, fireworks popping far off."""
    n = K.ns(BED)
    cl, cr = loop_of("amb_city_tavern", 1.05, 3200, 511), loop_of("amb_city_tavern", 0.95, 3200, 512)
    drum = np.zeros(n)
    t = 1.0
    while t < BED - 3:
        for k in (0, 0.5, 0.75):
            drum += at(n, t + k, K.lowpass(trim(rate(clip("impact-sounds", "impactPunch_heavy_002"), 0.5), 0, None, 0.3), 700, 2), 0.12)
        t += 2.0
    pops = scatter(n, 513, lambda r: bang(int(r.integers(1 << 20)), far=True), 4, 0.1)
    bells = scatter(n, 514, pick(["city/cart_bells1", "city/cart_bells2"], 1.0, 1.1, 3600), 3, 0.1)
    return wide(cl * 0.6 + drum, cr * 0.6 + np.roll(drum, K.ns(0.02)), pops, bells)


# file, render, variants, rms, event, subtitle, attenuation (events) or 0 (a bed, streamed)
SPEC = [
    ("page", page, 2, -22.0, "gate.world.page", "A page turns", 16),
    ("pages", pages, 1, -21.0, "gate.world.pages", "Pages fly in a gust", 48),
    ("tide", tide, 1, -21.0, "gate.world.tide", "The tide comes in", 48),
    ("bookworm", bookworm, 1, -19.0, "gate.world.bookworm", "Something huge rises out of the ink", 64),
    ("valve", valve, 2, -21.0, "gate.world.valve", "A valve turns and steam hisses", 16),
    ("overdrive", overdrive, 1, -20.0, "gate.world.overdrive", "The machines speed up", 48),
    ("powercut", powercut, 1, -21.0, "gate.world.powercut", "The power goes out", 48),
    ("clockwork", clockwork, 1, -20.0, "gate.world.clockwork", "Every clock starts at once", 64),
    ("chime", chime, 2, -22.0, "gate.world.chime", "Glass chimes", 24),
    ("sandstorm", sandstorm, 1, -21.0, "gate.world.sandstorm", "A sandstorm blows in", 48),
    ("shimmer", shimmer, 1, -22.0, "gate.world.shimmer", "The air shimmers", 32),
    ("walker", walker, 1, -20.0, "gate.world.walker", "The sand stands up and walks", 64),
    ("wish", wish, 2, -22.0, "gate.world.wish", "A wish lantern is lit", 16),
    ("fireworks", fireworks, 2, -19.0, "gate.world.fireworks", "Fireworks burst", 64),
    ("release", release, 1, -21.0, "gate.world.release", "Lanterns drift up into the night", 48),
    ("festival", festival, 1, -20.0, "gate.world.festival", "The crowd cheers as the great lantern rises", 64),
    ("spirits", spirits, 1, -22.0, "gate.world.spirits", "Lantern spirits sing", 48),
    ("arrive_drowned_archive", arrive_archive, 1, -22.0, "gate.world.arrive_drowned_archive", "Water drips into a deep pool", 16),
    ("arrive_clockwork_foundry", arrive_foundry, 1, -22.0, "gate.world.arrive_clockwork_foundry", "A great machine clanks into motion", 16),
    ("arrive_glass_desert", arrive_desert, 1, -22.0, "gate.world.arrive_glass_desert", "Wind rises and glass rings", 16),
    ("arrive_lantern_night_market", arrive_market, 1, -22.0, "gate.world.arrive_lantern_night_market", "A market's chatter rises", 16),
    ("amb_archive_stacks", bed_archive_stacks, 1, -29.0, "gate.world.archive_stacks", "Shelves creak and water drips", 0),
    ("amb_archive_atrium", bed_archive_atrium, 1, -28.5, "gate.world.archive_atrium", "Water laps under a glass dome", 0),
    ("amb_archive_ink", bed_archive_ink, 1, -28.5, "gate.world.archive_ink", "Dark water gulps", 0),
    ("amb_foundry_works", bed_foundry_works, 1, -28.0, "gate.world.foundry_works", "Machines hum and clank", 0),
    ("amb_foundry_pipes", bed_foundry_pipes, 1, -28.5, "gate.world.foundry_pipes", "Steam hisses through pipes", 0),
    ("amb_foundry_engine", bed_foundry_engine, 1, -27.5, "gate.world.foundry_engine", "A huge engine turns over", 0),
    ("amb_desert_wind", bed_desert_wind, 1, -28.0, "gate.world.desert_wind", "Wind blows over the dunes", 0),
    ("amb_desert_city", bed_desert_city, 1, -28.5, "gate.world.desert_city", "Sand trickles between old roofs", 0),
    ("amb_desert_glass", bed_desert_glass, 1, -29.0, "gate.world.desert_glass", "Glass chimes in the caves", 0),
    ("amb_market_street", bed_market_street, 1, -27.5, "gate.world.market_street", "A busy night market", 0),
    ("amb_market_water", bed_market_water, 1, -28.0, "gate.world.market_water", "Water laps round moored boats", 0),
    ("amb_market_festival", bed_market_festival, 1, -27.5, "gate.world.market_festival", "A festival crowd and drums", 0),
]


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    return C.main(argv, SPEC, "the D themes")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
