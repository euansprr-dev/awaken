"""The cave themes' sound (docs/SPEC.md §38.10): the Fungal Hollow, the Ember Mines, the Hive and the Overgrown Temple.
Three beds a theme, the touchstones, the events, the wonders' moments and an arrival sting each. Rebuild:

    python3 tools/art/specs/audio_gate_worlds_caves.py            render every file and write the JSON
    python3 tools/art/specs/audio_gate_worlds_caves.py --json     only the JSON

Recorded where there's a recording: the mines' bellows, anvils, carts and timbers are the city's own (sounds/city), the
caves' water, breath, drips, bubbles, vents and settling stone are the caves' loops (audio_caves.py), roots and wood
creak with the Elder Wood's creaks and Kenney's CC0 creaks and impacts, the jungle has the wild's crickets, frogs and
leaves (sounds/wild), and the glowing places chime with the Lumen's glass. What nothing records is made here: the hive's
buzz (softened saw voices beating against each other), its choir, a waterfall's roar and rain (shaped noise). Vanilla's
sounds are only referenced, never mixed in.

Written to src/main/resources/assets/awaken/sounds/gate_world/ and registered as gate.world.* in
src/main/merge/gates/assets/awaken/ (sounds.json and the lang file; other keys are kept). Loudness by
tools/art/audio_mix.py: beds amb (-28 +- 3 dBFS gated RMS, -6 dBTP), events sfx (-20 +- 4, -1 dBTP).
"""
import json
import os
import sys
from collections import OrderedDict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402
import audio_gate_worlds as G  # noqa: E402

OUT, MERGE = G.OUT, G.MERGE
BED = G.BED
at, trim, rate, ours, clip, wet, tile, scatter, wide, water, air, drips = (G.at, G.trim, G.rate, G.ours, G.clip, G.wet, G.tile,
                                                                         G.scatter, G.wide, G.water, G.air, G.drips)


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def pick(names, r0=1.0, r1=1.0, lp=None, fade=0.2):
    """A scatter picker: one of the named sounds of ours, resampled between r0 and r1, maybe low-passed (far away)."""
    def f(rnd):
        x = trim(rate(ours(names[rnd.integers(len(names))]), rnd.uniform(r0, r1)), 0, None, fade)
        return K.lowpass(x, lp, 2) if lp else x
    return f


def kick(names, r0=1.0, r1=1.0, lp=None):
    """A scatter picker from Kenney's packs: (pack, name) pairs."""
    def f(rnd):
        pack, name = names[rnd.integers(len(names))]
        x = trim(rate(clip(pack, name), rnd.uniform(r0, r1)), 0, None, 0.15)
        return K.lowpass(x, lp, 2) if lp else x
    return f


def loop_of(name, r=1.0, lp=None, seed=0):
    """One of our loops tiled to a bed's length, resampled, maybe low-passed."""
    x = rate(ours(name), r)
    y = tile(x, K.ns(BED), seed)
    return K.lowpass(y, lp, 2) if lp else y


def buzz(f0, n, seed, voices=4, bright=0.35, beat=3.0):
    """A hive's buzz: softened saw voices a few cents apart, each wavering, a slow swell through them. Mono."""
    r = K.rng(seed)
    out = np.zeros(n)
    for v in range(voices):
        f = f0 * 2 ** (r.uniform(-beat, beat) * 6 / 1200)
        amps = K.saw_amps(f * 1.05, bright=bright, tilt=1.3, cap=60, f0=f)
        vib = K.vibrato(n, r.uniform(9, 14), r.uniform(18, 30), 0.0, seed + 7 * v, drift=8.0)
        out += K.wt_voice(f, n, amps, vib, phase0=r.uniform(0, 1)) * (0.6 + 0.4 * K.smooth_noise(n, 0.4, seed + 3 * v + 1))
    return K.lowpass(out / voices ** 0.5, 2400, 2)


def roar(n, seed, lo=500, hi=2600):
    """A waterfall's roar: pink noise in a broad band that breathes a little, white spray over it."""
    body = norm(K.bandpass(K.noise(n, "pink", seed), (lo * hi) ** 0.5, 0.35))
    spray = norm(K.bandpass(K.noise(n, "white", seed + 1), 5200, 0.8))
    g = 0.82 + 0.18 * K.smooth_noise(n, 0.5, seed + 2)
    return (0.8 * body + 0.2 * spray) * g


# ---- touchstones and events -----------------------------------------------------------------------------------------

def puff(v):
    """A spore puffball struck: a soft thud and the spores' breath out of it, a glint of glass as they light."""
    n = K.ns(2.4)
    x = at(n, 0, trim(rate(clip("impact-sounds", "impactSoft_medium_00" + str(1 + v)), 1.25), 0, None, 0.2), 0.8)
    hiss = norm(K.bandpass(K.noise(K.ns(1.2), "pink", 31 + v), 2400, 0.7)) * K.perc(K.ns(1.2), 0.02, 0.7)
    x += at(n, 0.03, hiss, 0.45)
    x += at(n, 0.15, ours("amb_world_lumen_chime_" + str(1 + v)), 0.25)
    return wet(x, "small", 0.3)


def bellows(v):
    """The mine's bellows worked: the city's own bellows, a breath of coals answering."""
    n = K.ns(3.0)
    x = at(n, 0, trim(rate(ours("city/bellows" + str(1 + v)), 0.92), 0, None, 0.3))
    x += at(n, 0.25, K.crackle(K.ns(2.0), 40, 51 + v, 0.5) * K.perc(K.ns(2.0), 0.05, 1.4), 0.12)
    return wet(x, "crypt", 0.25)


def drum(v):
    """The wax drum struck: a deep soft hit, and the hive's hum answering it from the walls."""
    n = K.ns(3.0)
    x = at(n, 0, trim(rate(clip("impact-sounds", "impactPunch_heavy_00" + str(1 + v)), 0.55), 0, None, 0.4), 0.9)
    x += at(n, 0, trim(rate(clip("impact-sounds", "impactSoft_heavy_001"), 0.6), 0, None, 0.4), 0.6)
    h = buzz(175, K.ns(2.6), 61 + v, voices=5) * K.adsr(K.ns(2.6), 0.25, 0.4, 0.6, 1.2)
    x += at(n, 0.12, h, 0.35)
    return wet(x, "crypt", 0.35)


def brazier(v):
    """A brazier fed: flame catching in a stone bowl (the Rig's ignition), then the coals crackling."""
    n = K.ns(3.0)
    x = at(n, 0, trim(rate(ours("gate_rig_ignite"), 0.9 + 0.08 * v), 0, None, 0.4), 0.9)
    x += at(n, 0.2, K.crackle(K.ns(2.6), 55, 71 + v, 0.55) * K.adsr(K.ns(2.6), 0.2, 0.3, 0.7, 1.4), 0.2)
    return wet(x, "crypt", 0.3)


def tremor(v):
    """The mine shakes: a deep rumble, stone grinding, grit and small rocks coming down."""
    n = K.ns(5.0)
    r = K.lowpass(rate(ours("amb_rift_rumble"), 0.7), 400, 2)[:n]
    x = at(n, 0, r * np.minimum(1, np.linspace(0, 6, len(r))) * np.linspace(1, 0, len(r)) ** 0.6, 1.0)
    x += at(n, 0.2, K.sub_boom(K.ns(2.4), 46, 28, 2.0), 0.7)
    for i, (t, name) in enumerate(((0.4, "impactMining_000"), (1.1, "impactMining_003"), (1.8, "impactMining_001"))):
        x += at(n, t, trim(rate(clip("impact-sounds", name), 0.55 + 0.1 * i), 0, None, 0.3), 0.5)
    x += at(n, 1.0, ours("amb_cave_settle_" + str(1 + v)), 0.6)
    return wet(x, "shaft", 0.35)


def swarm(v):
    """The hive's swarm rising: many buzzing voices swelling and passing over."""
    n = K.ns(5.0)
    x = np.zeros(n)
    for i, f in enumerate((150, 196, 233, 262, 311)):
        x += buzz(f * (1 + 0.02 * v), n, 81 + 11 * i + v, voices=3) * K.swell(n, 0.35 + 0.08 * i, 1.6)
    return wet(x, "crypt", 0.3)


def rains(v):
    """The rains come: a roll of thunder far off and rain sweeping in over the leaves."""
    n = K.ns(6.0)
    rain = norm(K.bandpass(K.noise(n, "pink", 91 + v), 3200, 0.5)) * K.adsr(n, 1.5, 0.5, 0.8, 2.0)
    x = rain * 0.6 + at(n, 0, K.lowpass(trim(ours("amb_storm_roll_" + str(1 + v)), 0, 5.5, 1.5), 1800, 2), 0.7)
    x += scatter(n, 93 + v, pick(["amb_cave_drip_1", "amb_cave_drip_2", "amb_cave_drip_3"], 1.1, 1.4), 18, 0.25)[0][:n]
    return x


def wakening(v):
    """Something old wakes: stone grinds, a deep hum rises under it, and the lights catch one after another."""
    n = K.ns(6.0)
    x = at(n, 0, trim(rate(clip("impact-sounds", "impactMining_001"), 0.4), 0, None, 0.6), 0.7)
    hum = K.drone(55, n, seed=101) * K.adsr(n, 2.5, 0.5, 0.8, 2.0)
    x += K.lowpass(hum, 700, 2) * 0.35
    for i in range(4):
        x += at(n, 1.2 + i * 0.9, trim(rate(ours("gate_rig_ignite"), 1.1 + 0.1 * i), 0, 1.2, 0.4), 0.3)
    return wet(x, "shaft", 0.4)


def bloom(v):
    """The Bloom opens: the whole fungus breathing in, slow and huge, then out in a cloud of glowing spores."""
    n = K.ns(7.0)
    br = rate(ours("amb_cave_breath"), 0.5)[:n]
    x = at(n, 0, br * K.swell(len(br), 0.55, 1.8), 0.9)
    x += at(n, 0, K.lowpass(K.drone(41, n, seed=111), 500, 2) * K.swell(n, 0.6, 2.0), 0.4)
    for i in range(7):
        x += at(n, 3.6 + i * 0.22, ours("amb_world_lumen_chime_" + str(1 + i % 6)), 0.3)
    return wet(x, "shaft", 0.45)


def surface(v):
    """The Slag Wyrm surfaces: the magma heaves and bursts, a deep cry from under it."""
    n = K.ns(6.0)
    x = at(n, 0, K.sub_boom(K.ns(3.0), 40, 24, 2.4), 0.9)
    for i in range(6):
        x += at(n, 0.1 + i * 0.25, ours("amb_cave_sulfur_bubble_" + str(1 + i % 4)), 0.6)
    x += at(n, 0.3, trim(rate(clip("impact-sounds", "impactSoft_heavy_004"), 0.45), 0, None, 0.4), 0.8)
    x += at(n, 0.9, K.lowpass(trim(rate(ours("fauna/mycelord/roar1"), 0.55), 0, None, 0.8), 1600, 2), 0.7)
    return wet(x, "shaft", 0.4)


def choir(v):
    """The Queen's Choir: the swarm's buzz falling into a hummed chord, low voices under it."""
    n = K.ns(7.0)
    x = np.zeros(n)
    for i, f in enumerate((K.hz(K.midi("D3")), K.hz(K.midi("A3")), K.hz(K.midi("F#4")))):
        x += K.choir(f, n, "u", voices=5, seed=121 + i, bright=0.3) * K.swell(n, 0.5, 1.5) * (1 - 0.2 * i)
    x += buzz(196, n, 127, voices=4) * K.swell(n, 0.3, 1.8) * 0.5
    return wet(x, "shaft", 0.45)


def saint(v):
    """The Root Saint stirs: the whole tree groaning, roots cracking, the old wood settling around it."""
    n = K.ns(6.5)
    x = at(n, 0, K.lowpass(rate(ours("amb_world_elder_groan"), 0.8)[:n], 1600, 2) * K.swell(n, 0.45, 1.5), 1.0)
    for i, (t, name, r) in enumerate(((0.6, "impactWood_heavy_001", 0.5), (1.7, "impactWood_medium_002", 0.45),
                                       (2.5, "impactWood_heavy_001", 0.4))):
        x += at(n, t, trim(rate(clip("impact-sounds", name), r), 0, None, 0.4), 0.7)
    for i in range(3):
        x += at(n, 0.3 + i * 1.6, K.lowpass(trim(rate(ours("city/boat_creak" + str(1 + i)), 0.5), 0, None, 0.4), 1500, 2), 0.5)
    return wet(x, "shaft", 0.45)


# ---- arrivals --------------------------------------------------------------------------------------------------------

def arrive_fungal(v):
    """Arriving in the Fungal Hollow: the cave breathing out, and the glass of the glowcaps answering in a rising run."""
    n = K.ns(6.0)
    br = rate(ours("amb_cave_breath"), 0.7)[:n]
    x = at(n, 0, K.lowpass(br, 1800, 2) * K.swell(len(br), 0.4, 1.6), 0.8)
    for i in range(5):
        x += at(n, 0.5 + i * 0.3, ours("amb_world_lumen_chime_" + str(1 + i)), 0.4 - 0.04 * i)
    return wet(x, "field", 0.4)


def arrive_ember(v):
    """Arriving in the Ember Mines: an anvil struck somewhere far down, the bellows breathing, the stone settling."""
    n = K.ns(6.0)
    x = at(n, 0, K.lowpass(trim(rate(ours("city/anvil1"), 0.8), 0, None, 1.0), 2400, 2), 0.8)
    x += at(n, 0.9, trim(rate(ours("city/bellows1"), 0.85), 0, None, 0.5), 0.5)
    x += at(n, 1.6, ours("amb_cave_settle_2"), 0.5)
    return wet(x, "shaft", 0.5)


def arrive_hive(v):
    """Arriving in the Hive: the buzz swelling all round you, and one deep beat of the wax drum."""
    n = K.ns(6.0)
    x = buzz(185, n, 141, voices=6) * K.swell(n, 0.4, 1.6) * 0.8
    x += at(n, 1.4, trim(rate(clip("impact-sounds", "impactPunch_heavy_001"), 0.5), 0, None, 0.5), 0.8)
    return wet(x, "crypt", 0.4)


def arrive_temple(v):
    """Arriving in the Overgrown Temple: leaves stirring, a far brazier catching, birds in the canopy above."""
    n = K.ns(6.0)
    x = at(n, 0, trim(ours("amb_lands_rustle"), 0, 5.5, 1.5), 0.6)
    x += at(n, 0.8, K.lowpass(trim(rate(ours("gate_rig_ignite"), 0.85), 0, None, 0.5), 2600, 2), 0.6)
    for i, (t, name) in enumerate(((1.6, "wild/wren1"), (2.5, "wild/chip2"), (3.6, "wild/wren3"))):
        x += at(n, t, K.lowpass(ours(name), 3600, 2), 0.25)
    return wet(x, "field", 0.35)


# ---- beds -------------------------------------------------------------------------------------------------------------

CHIMES = ["amb_world_lumen_chime_" + str(i) for i in range(1, 7)]
CREAKS = ["amb_world_elder_creak_" + str(i) for i in range(1, 5)]
SETTLE = ["amb_cave_settle_" + str(i) for i in range(1, 5)]
BUBBLES = ["amb_cave_sulfur_bubble_" + str(i) for i in range(1, 5)]
VENTS = ["amb_cave_sulfur_vent_" + str(i) for i in range(1, 4)]


def bed_hollow_glow(v):
    """The Glowing Deep and the Canopy: still water lapping, the glowcaps' low hum, their glass chiming now and then."""
    n = K.ns(BED)
    wl, wr = water(201, 1400)
    hum = loop_of("amb_world_lumen_hum", 1.0, 900, 202) * 0.35
    return wide(wl * 0.8 + hum, wr * 0.8 + hum, scatter(n, 203, pick(CHIMES), 6, 0.12), scatter(n, 204, drips, 7, 0.3))


def bed_hollow_roots(v):
    """The Root Warrens: close still air, roots creaking all round you, earth settling, a drip."""
    n = K.ns(BED)
    al, ar = air(211, 1200)
    return wide(al * 0.7, ar * 0.7, scatter(n, 212, pick(CREAKS, 0.8, 1.0), 8, 0.3), scatter(n, 213, pick(SETTLE, 0.9, 1.1), 3, 0.2),
                scatter(n, 214, drips, 4, 0.22))


def bed_hollow_bloom(v):
    """Inside the Bloom and the Mind: the fungus breathing, slow and deep, a hum through its gills, spores puffing."""
    n = K.ns(BED)
    br = loop_of("amb_cave_breath", 0.55, 1400, 221)
    breath = br * (0.55 + 0.45 * np.sin(2 * np.pi * np.arange(n) / K.ns(10.0)) ** 2)
    hum = loop_of("amb_world_lumen_hum", 0.8, 700, 222) * 0.4
    puffs = scatter(n, 223, kick([("impact-sounds", "impactSoft_medium_001"), ("impact-sounds", "impactSoft_medium_002")],
                                 1.2, 1.5, 2400), 5, 0.12)
    return wide(breath * 0.8 + hum, breath * 0.75 + hum, puffs, scatter(n, 224, pick(CHIMES, 0.8, 0.9), 4, 0.1))


def bed_mine_works(v):
    """The Working Mine: cold air in the galleries, timbers creaking, a cart rolling somewhere and an anvil far below."""
    n = K.ns(BED)
    al, ar = air(231, 1600)
    carts = scatter(n, 232, pick(["city/cart_roll1", "city/cart_roll2"], 0.8, 0.9, 1200), 2, 0.2)
    anvils = scatter(n, 233, pick(["city/anvil1", "city/anvil2", "city/anvil3", "city/anvil4"], 0.8, 0.95, 1800), 4, 0.12)
    timbers = scatter(n, 234, pick(["city/boat_creak1", "city/boat_creak2", "city/boat_creak3"], 0.6, 0.75, 1400), 4, 0.18)
    return wide(al * 0.8, ar * 0.8, carts, anvils, timbers, scatter(n, 235, drips, 3, 0.18))


def bed_mine_chasm(v):
    """The Chasm and the Forge: wind up the chasm, lava bubbling far below, vents sighing, stone ticking as it cools."""
    n = K.ns(BED)
    al, ar = air(241, 2200)
    deep = loop_of("amb_rift_rumble", 0.8, 300, 242) * 0.25
    return wide(al + deep, ar + deep, scatter(n, 243, pick(BUBBLES, 0.7, 0.9, 1800), 14, 0.3),
                scatter(n, 244, pick(VENTS, 0.8, 1.0), 3, 0.25),
                scatter(n, 245, kick([("impact-sounds", "impactMining_000"), ("impact-sounds", "impactMining_003")], 1.6, 2.0, 3000), 4, 0.05))


def bed_mine_sea(v):
    """The Cooling Sea and the Heart Seam: a whole sea of magma bubbling and sighing, the crust cracking, a deep pulse."""
    n = K.ns(BED)
    deep = loop_of("amb_rift_rumble", 0.65, 260, 251) * 0.4
    pulse = deep * (0.7 + 0.3 * np.sin(2 * np.pi * np.arange(n) / K.ns(5.0)))
    return wide(pulse, pulse * 0.9, scatter(n, 252, pick(BUBBLES, 0.6, 0.85, 1600), 26, 0.3),
                scatter(n, 253, pick(VENTS, 0.7, 0.9), 5, 0.3), scatter(n, 254, pick(SETTLE, 0.7, 0.9), 3, 0.2))


def bed_hive_comb(v):
    """The Comb: the hive's hum close in the walls, honey dripping thick and slow, wax ticking."""
    n = K.ns(BED)
    hl, hr = buzz(165, n, 261, voices=5) * 0.5, buzz(168, n, 262, voices=5) * 0.5
    sw = 0.6 + 0.4 * K.smooth_noise(n, 0.08, 263)
    honey = scatter(n, 264, pick(["amb_cave_drip_pool_1", "amb_cave_drip_pool_2", "amb_cave_drip_pool_3"], 0.6, 0.75), 9, 0.3)
    return wide(hl * sw, hr * sw[::-1], honey)


def bed_hive_brood(v):
    """The Brood Hall and the Swarm: a huge hum all round, swarms passing far above, the comb creaking under the weight."""
    n = K.ns(BED)
    lo = buzz(130, n, 271, voices=6) * 0.45
    hi = buzz(220, n, 272, voices=4) * (0.3 + 0.7 * np.clip(K.smooth_noise(n, 0.1, 273), 0, 1)) * 0.35
    creak = scatter(n, 274, pick(CREAKS, 1.0, 1.2), 4, 0.15)
    return wide(lo + hi, lo * 0.9 + hi * 1.1, creak, scatter(n, 275, drips, 3, 0.15))


def bed_hive_lake(v):
    """The Honey Lake and the Garden: thick honey lapping slowly, the hum far off across it, now and then a drip."""
    n = K.ns(BED)
    wl, wr = water(281, 900)
    slow = 0.75
    far = buzz(175, n, 282, voices=4) * 0.18
    return wide(wl * slow + far, wr * slow + far, scatter(n, 283, pick(["amb_cave_drip_pool_1", "amb_cave_drip_pool_4"], 0.6, 0.7), 5, 0.22))


def bed_temple_courts(v):
    """The Courts and the Great Tree: leaves in a warm wind, crickets, a frog plopping into a pool, birds high up."""
    n = K.ns(BED)
    rl, rr = loop_of("amb_lands_rustle", 1.0, 5000, 291), loop_of("amb_lands_rustle", 1.0, 5000, 292)
    crickets = scatter(n, 293, pick(["wild/cricket1", "wild/cricket2", "wild/cricket3", "wild/cricket4"]), 10, 0.12)
    birds = scatter(n, 294, pick(["wild/wren1", "wild/wren2", "wild/chip1", "wild/chip3", "wild/finch2"], 0.95, 1.05, 4200), 6, 0.1)
    frogs = scatter(n, 295, pick(["wild/frog_plop1", "wild/frog_plop2", "wild/frog_plop3"]), 3, 0.18)
    return wide(rl * 0.55, rr * 0.55, crickets, birds, frogs)


def bed_temple_halls(v):
    """The Root Halls and the Idol: still stone air, roots creaking in the walls, water dripping, an owl somewhere far."""
    n = K.ns(BED)
    al, ar = air(301, 1300)
    owl = scatter(n, 302, pick(["wild/owl1", "wild/owl2", "wild/owl3"], 0.95, 1.0, 1800), 2, 0.1)
    return wide(al * 0.7, ar * 0.7, scatter(n, 303, pick(CREAKS, 0.75, 0.9), 6, 0.25), scatter(n, 304, drips, 7, 0.3), owl)


def bed_temple_falls(v):
    """The Waterfall Temple: the fall's roar, spray hissing, the pool churning under it, birds over the noise."""
    n = K.ns(BED)
    a, b = roar(n, 311), roar(n, 312)
    wl, wr = water(313, 1100)
    birds = scatter(n, 314, pick(["wild/wren2", "wild/chip2", "wild/finch1"], 1.0, 1.05, 4500), 4, 0.07)
    return wide(a * 0.6 + wl * 0.4, b * 0.6 + wr * 0.4, birds)


# file, render, variants, rms, event, subtitle, attenuation (events) or 0 (a bed, streamed)
SPEC = [
    ("puff", puff, 2, -22.0, "gate.world.puff", "A spore puffball bursts", 16),
    ("bellows", bellows, 2, -21.0, "gate.world.bellows", "Bellows breathe", 16),
    ("drum", drum, 2, -20.0, "gate.world.drum", "A wax drum booms", 24),
    ("brazier", brazier, 2, -21.0, "gate.world.brazier", "A brazier catches fire", 16),
    ("tremor", tremor, 2, -19.0, "gate.world.tremor", "The ground shakes", 48),
    ("swarm", swarm, 1, -21.0, "gate.world.swarm", "A swarm rises", 48),
    ("rains", rains, 2, -22.0, "gate.world.rains", "Rain sweeps in", 48),
    ("wakening", wakening, 1, -20.0, "gate.world.wakening", "Something old wakes up", 48),
    ("bloom", bloom, 1, -20.0, "gate.world.bloom", "The bloom opens", 64),
    ("surface", surface, 1, -19.0, "gate.world.surface", "Something huge surfaces from the magma", 64),
    ("choir", choir, 1, -21.0, "gate.world.choir", "The hive sings", 64),
    ("saint", saint, 1, -20.0, "gate.world.saint", "The great tree groans", 64),
    ("arrive_fungal_hollow", arrive_fungal, 1, -22.0, "gate.world.arrive_fungal_hollow", "The cave breathes and glass chimes", 16),
    ("arrive_ember_mines", arrive_ember, 1, -22.0, "gate.world.arrive_ember_mines", "An anvil rings far below", 16),
    ("arrive_hive", arrive_hive, 1, -22.0, "gate.world.arrive_hive", "The hive hums all around", 16),
    ("arrive_overgrown_temple", arrive_temple, 1, -22.0, "gate.world.arrive_overgrown_temple", "Leaves stir and a fire catches", 16),
    ("amb_hollow_glow", bed_hollow_glow, 1, -28.0, "gate.world.hollow_glow", "Water laps under glowing mushrooms", 0),
    ("amb_hollow_roots", bed_hollow_roots, 1, -29.0, "gate.world.hollow_roots", "Roots creak in the walls", 0),
    ("amb_hollow_bloom", bed_hollow_bloom, 1, -28.5, "gate.world.hollow_bloom", "The fungus breathes", 0),
    ("amb_mine_works", bed_mine_works, 1, -28.5, "gate.world.mine_works", "Timbers creak and an anvil rings far off", 0),
    ("amb_mine_chasm", bed_mine_chasm, 1, -28.0, "gate.world.mine_chasm", "Lava bubbles far below", 0),
    ("amb_mine_sea", bed_mine_sea, 1, -27.5, "gate.world.mine_sea", "A sea of magma bubbles", 0),
    ("amb_hive_comb", bed_hive_comb, 1, -28.5, "gate.world.hive_comb", "The hive hums in the walls", 0),
    ("amb_hive_brood", bed_hive_brood, 1, -28.0, "gate.world.hive_brood", "A huge hive hums all around", 0),
    ("amb_hive_lake", bed_hive_lake, 1, -29.0, "gate.world.hive_lake", "Honey laps slowly", 0),
    ("amb_temple_courts", bed_temple_courts, 1, -28.5, "gate.world.temple_courts", "Leaves rustle and crickets chirp", 0),
    ("amb_temple_halls", bed_temple_halls, 1, -29.0, "gate.world.temple_halls", "Roots creak in old stone halls", 0),
    ("amb_temple_falls", bed_temple_falls, 1, -27.5, "gate.world.temple_falls", "A waterfall roars", 0),
]


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    total = 0
    if "--json" not in argv:
        for name, fn, variants, rms, event, _, att in SPEC:
            cat = "amb" if name.startswith("amb_") else "sfx"
            tgt, tol, ceil, q = TARGETS[cat]
            assert abs(rms - tgt) <= tol, name
            for v in range(variants):
                x = fn(v)
                if cat == "sfx":
                    x = K.mono(x)
                y = K.master(x, rms, ceil - 0.7)
                if cat == "amb":
                    y = K.loop_seamless(y, 2.0)
                file = name + ("" if variants == 1 else "_" + str(v + 1))
                path = os.path.join(OUT, file + ".ogg")
                K.write_ogg(path, y, q, None)
                total += os.path.getsize(path)
                print(f"  {file:26s} {len(y) / K.SR:5.1f} s  {1 if y.ndim == 1 else y.shape[1]} ch  RMS {K.gated_rms(y):6.1f}"
                      f"  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
        print(f"  {total / 1e6:.2f} MB for the cave themes")
    sounds_path, lang_path = os.path.join(MERGE, "sounds.json"), os.path.join(MERGE, "lang/en_us.json")
    sounds = json.load(open(sounds_path), object_pairs_hook=OrderedDict)
    lang = json.load(open(lang_path), object_pairs_hook=OrderedDict)
    for name, _, variants, _, event, subtitle, att in SPEC:
        entries = []
        for v in range(variants):
            e = OrderedDict(name="awaken:gate_world/" + name + ("" if variants == 1 else "_" + str(v + 1)))
            if att:
                e["attenuation_distance"] = att
            else:
                e["stream"] = True
            entries.append(e)
        assert subtitle, event + " needs a subtitle (audio_events_resolve)"
        sounds[event] = OrderedDict(subtitle="subtitles.awaken." + event, sounds=entries)
        lang["subtitles.awaken." + event] = subtitle
    with open(sounds_path, "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(lang_path, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"events: {len(SPEC)} gate.world.* for the cave themes in merge/gates")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
