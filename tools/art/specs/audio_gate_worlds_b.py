"""The B themes' and the rare worlds' sound (docs/SPEC.md §38.13): the Sky Archipelago, the Abyssal Shrine and the 15 rare
worlds. Three beds a theme, the touchstones, the events, the wonders' moments, the signature creatures, an arrival sting
each, and the rare worlds' own beds and sounds. Rebuild:

    python3 tools/art/specs/audio_gate_worlds_b.py            render every file and write the JSON
    python3 tools/art/specs/audio_gate_worlds_b.py --json     only the JSON

Recorded where there's a recording: the islands' wind is the lands' high wind and the Frost Spires', their storms the
Tell's rolls, the Roc's cry the peak kite's slowed right down with the big wings; the shrine's void is the Veil's choir and
the falling stars' whistles; the quiet meadow's crickets and the campfire are the wild's and the Hearth's. What nothing
records is made here: wind chimes (pentatonic glass), a gong in the dark, rain falling upward (drops swelled in backwards),
knocking, the heart of the gates. Vanilla's sounds are only referenced, never mixed in.

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
import audio_gate_worlds_c as CC  # noqa: E402

BED = G.BED
at, trim, rate, ours, clip, wet, tile, scatter, wide, water, air, drips = (G.at, G.trim, G.rate, G.ours, G.clip, G.wet, G.tile,
                                                                         G.scatter, G.wide, G.water, G.air, G.drips)
norm, pick, kick, loop_of = C.norm, C.pick, C.kick, C.loop_of
wind, ticks = D.wind, D.ticks
hz, rswell, heart, ding, whale_song = CC.hz, CC.rswell, CC.heart, CC.ding, CC.whale_song

CLOTH = CC.CLOTH
CHIMES = D.CHIMES
# the islands' chimes: a pentatonic on D, high and glassy
CHIME_NOTES = ["D6", "E6", "F#6", "A6", "B6", "D7"]
CRICKETS = ["wild/cricket" + str(i) for i in range(1, 5)]
WINGS = ["wild/wings_big" + str(i) for i in range(1, 4)]


def chime(m, seed, t60=2.4):
    """One tube of a wind chime: a bright glass ring."""
    return K.glass(hz(m), K.ns(t60 + 0.6), bright=0.65, t60=t60, seed=seed, hit=0.5)


def chimes(n, seed, count, gain):
    """A wind chime moving in the breeze: tubes struck a few at a time, (L, R)."""
    return scatter(n, seed, lambda r: chime(CHIME_NOTES[r.integers(len(CHIME_NOTES))], int(r.integers(1 << 20))), count, gain)


def gong(f, seed, length=6.0):
    """A gong in the dark: a low bell, slow to bloom."""
    return K.bell(f, K.ns(length), t60=length * 0.8, bright=0.3, seed=seed) * K.swell(K.ns(length), 0.05, 0.6)


def _knock(seed, gain=1.0):
    return K.lowpass(trim(rate(clip("impact-sounds", "impactWood_heavy_001"), (0.85, 0.8)[seed % 2]), 0, None, 0.2),
                     2400, 2) * gain


def drop_up(seed):
    """A raindrop falling upward: its patter swelled in backwards."""
    r = K.rng(seed)
    m = K.ns(r.uniform(0.04, 0.08))
    d = norm(K.bandpass(K.noise(m, "white", seed), r.uniform(2500, 5000), 1.6)) * K.perc(m, 0.001, 0.03)
    return d[::-1]


def rain_up(n, seed, density=60):
    """Rain falling the wrong way: a soft hiss, and drops pulled in backwards all round you."""
    hiss = norm(K.bandpass(K.noise(n, "pink", seed), 3800, 0.6)) * 0.08
    drops = np.zeros(n)
    r = K.rng(seed + 1)
    for _ in range(int(density * n / K.SR)):
        t = r.uniform(0, n / K.SR - 0.2)
        drops += at(n, t, drop_up(int(r.integers(1 << 20))), r.uniform(0.05, 0.2))
    return hiss + drops


def murmur(n, seed, gain=1.0):
    """Voices talking low round a fire, no word clear: soft vowels coming and going."""
    x = np.zeros(n)
    r = K.rng(seed)
    t = 0.2
    while t < n / K.SR - 1.5:
        m = K.ns(r.uniform(0.6, 1.4))
        v = ("a", "o", "e", "u", "m")[r.integers(5)]
        x += at(n, t, K.choir(hz(("A2", "C3", "E3", "G2")[r.integers(4)]), m, v, voices=2, seed=int(r.integers(1 << 20)),
                              bright=0.2) * K.swell(m, 0.3, 0.8), r.uniform(0.3, 0.7))
        t += r.uniform(0.5, 1.6)
    return K.lowpass(x, 1800, 2) * gain


# ---- the Sky Archipelago --------------------------------------------------------------------------------------------

def skychime(v):
    """The wind chime touched: every tube struck at once, then ringing on as the wind takes them."""
    n = K.ns(4.5)
    x = np.zeros(n)
    notes = CHIME_NOTES if v == 0 else CHIME_NOTES[::-1]
    for i, m in enumerate(notes):
        x += at(n, 0.04 * i, chime(m, 701 + i + 7 * v, 3.0), 0.35)
    for i in range(5):
        x += at(n, 1.2 + 0.45 * i, chime(notes[(i * 2 + v) % len(notes)], 711 + i, 2.0), 0.18)
    return wet(x, "field", 0.35)


def gale(v):
    """A gale blowing across the islands: the wind climbing to a roar, pennants snapping, the chimes thrown about."""
    n = K.ns(7.0)
    w = wind(n, 721, 2400, 0.7) * K.swell(n, 0.45, 1.4)
    roar = norm(K.lowpass(K.noise(n, "brown", 723), 400, 2)) * K.swell(n, 0.5, 1.6) * 0.4
    flaps = scatter(n, 724, kick(CLOTH, 1.1, 1.3, 3800), 12, 0.25)
    ch = chimes(n, 725, 10, 0.12)
    return K.mono(wide(w * 0.7 + roar, np.roll(w, K.ns(0.3)) * 0.7 + roar, flaps, ch))


def sunburst(v):
    """The sun breaking through the clouds: the wind dropping, a warm chord opening, everything glittering."""
    n = K.ns(6.0)
    pad = (K.pad(hz("D4"), n, voices=5, seed=731, bright=0.25) + 0.7 * K.pad(hz("A4"), n, voices=4, seed=732, bright=0.2)
           + 0.5 * K.pad(hz("F#5"), n, voices=3, seed=733, bright=0.25)) * K.swell(n, 0.4, 1.0)
    w = wind(n, 734, 1800, 0.3) * K.line(n, [(0, 0.6), (0.3, 0.1), (1, 0.05)])
    glints = np.zeros(n)
    for i in range(8):
        glints += at(n, 1.4 + 0.35 * i, chime(CHIME_NOTES[(i * 3) % 6], 735 + i, 1.6), 0.12)
    return wet(pad * 0.4 + w * 0.5 + glints, "field", 0.4)


def great_wind(v):
    """The Great Wind: a deep rush rising from far below, a horn of air, every island creaking as it lifts."""
    n = K.ns(9.0)
    rise = wind(n, 741, 1200, 0.5) * K.line(n, [(0, 0), (0.6, 1), (0.85, 1), (1, 0.2)])
    horn = K.lowpass(K.choir(hz("D2"), n, "o", voices=6, seed=742, bright=0.3), 700, 2) * K.swell(n, 0.5, 1.5) * 0.4
    creaks = scatter(n, 743, pick(CC.CREAKS, 0.6, 0.8, 1200), 5, 0.3)
    return K.mono(wide(rise + horn, np.roll(rise, K.ns(0.2)) + horn, creaks))


def roc(v):
    """The Storm Roc: a vast cry out of the cloud, wingbeats like sails filling, thunder rolling after it."""
    n = K.ns(8.0)
    cry = trim(rate(ours("fauna/peak_kite/ambient1"), 0.42), 0, None, 0.5)
    x = at(n, 0.2, K.lowpass(cry, 2600, 2), 0.9)
    x += at(n, 0.2, K.lowpass(rate(cry, 0.5), 900, 2), 0.4)
    for i in range(3):
        wing = trim(rate(ours(WINGS[i % 3]), 0.55), 0, None, 0.3)
        x += at(n, 2.2 + 0.8 * i, K.lowpass(wing, 1400, 2), 0.7)
    x += at(n, 4.2, trim(ours("amb_storm_roll_" + str(1 + v % 3)), 0, 3.6, 1.0), 0.8)
    return wet(x, "field", 0.5)


def arrive_sky(v):
    """Arriving on the islands: a rush of air rising past you, the chimes answering, the wind settling round you."""
    n = K.ns(6.0)
    rush = wind(n, 751, 3000, 0.6) * K.line(n, [(0, 0), (0.25, 1), (0.5, 0.4), (1, 0.3)])
    x = rush * 0.6
    for i, m in enumerate(("D6", "A6", "F#6", "D7")):
        x += at(n, 1.8 + 0.22 * i, chime(m, 753 + i, 2.6), 0.3)
    return wet(x, "field", 0.45)


def bed_sky_wind(v):
    """The Isles and the Wind Stair: wind across open sky, pennants snapping, a chime somewhere, a waterfall far off."""
    n = K.ns(BED)
    hl, hr = loop_of("amb_lands_wind_high", 1.0, 4000, 801), loop_of("amb_lands_wind_high", 1.0, 4000, 802)
    w = wind(n, 803, 2000, 0.45)
    fall = norm(K.bandpass(K.noise(n, "pink", 804), 900, 0.6)) * 0.05
    return wide(hl * 0.55 + w * 0.35 + fall, hr * 0.55 + np.roll(w, K.ns(4.0)) * 0.35 + fall,
                scatter(n, 805, kick(CLOTH, 1.0, 1.2, 3600), 8, 0.12), chimes(n, 806, 6, 0.06))


def bed_sky_ruins(v):
    """The Sky Ruins: the wind in broken arches, stones settling, a gilded bell swinging somewhere above you."""
    n = K.ns(BED)
    w = wind(n, 811, 1600, 0.4)
    moan = norm(K.bandpass(K.noise(n, "pink", 812), 420, 6.0)) * (0.4 + 0.6 * np.clip(0.5 + K.smooth_noise(n, 0.15, 813), 0, 1)) * 0.12
    settle = scatter(n, 814, pick(["amb_cave_settle_" + str(i) for i in range(1, 5)], 0.8, 1.0, 2400), 5, 0.12)
    bell = scatter(n, 815, lambda r: K.lowpass(wet(ding(hz(("D5", "A4")[r.integers(2)]), int(r.integers(1 << 20))), "field", 0.6), 2400, 2), 3, 0.1)
    return wide(w * 0.45 + moan, np.roll(w, K.ns(3.5)) * 0.45 + moan, settle, bell, chimes(n, 816, 3, 0.04))


def bed_sky_high(v):
    """Above the Clouds and the Whale Road: thin air very high up, a soft endless rush below, something huge singing far off."""
    n = K.ns(BED)
    al, ar = K.highpass(air(821, 5000)[0], 900, 2), K.highpass(air(822, 5000)[1], 900, 2)
    under = norm(K.lowpass(K.noise(n, "brown", 823), 300, 2)) * 0.12
    songs = scatter(n, 824, lambda r: K.lowpass(wet(whale_song(int(r.integers(1 << 20)), 6.0, 80, 160), "field", 0.8), 800, 2), 2, 0.18)
    return wide(al * 0.5 + under, ar * 0.5 + under, songs, chimes(n, 825, 3, 0.04))


# ---- the Abyssal Shrine ----------------------------------------------------------------------------------------------

def offering(v):
    """An offering given: something small dropped in the bronze bowl, a gong blooming out of the dark, its echo coming back."""
    n = K.ns(6.0)
    x = at(n, 0, K.lowpass(trim(rate(clip("impact-sounds", ("impactMetal_light_001", "impactMetal_light_004")[v]), 1.2), 0, None, 0.1), 4000, 2), 0.5)
    x += at(n, 0.15, gong(hz(("D2", "C2")[v]), 901 + v), 0.8)
    return wet(x, "shaft", 0.55)


def hush(v):
    """The Hush: every small sound draining away at once, a thin high whine, your own breath loud in the quiet."""
    n = K.ns(7.0)
    al, ar = air(911, 3000)
    drain = al[:n] * K.line(n, [(0, 0.8), (0.2, 0.0), (1, 0)])
    whine = K.sine(np.full(n, 7200.0), n) * K.line(n, [(0, 0), (0.25, 0.04), (0.8, 0.04), (1, 0)])
    breath = norm(K.bandpass(K.noise(n, "pink", 913), 700, 1.0)) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.25 * np.arange(n) / K.SR)) ** 2
    return drain + whine + breath * K.line(n, [(0, 0), (0.3, 0), (0.4, 0.25), (1, 0.2)])


def hunger(v):
    """The Hunger: something far below the shrine growling, low enough to feel, the stones shifting round you."""
    n = K.ns(7.0)
    growl = K.lowpass(rate(ours("amb_world_elder_groan"), 0.45), 500, 2) * K.swell(len(rate(ours("amb_world_elder_groan"), 0.45)), 0.4, 1.4)
    x = at(n, 0.4, growl, 0.9)
    x += at(n, 0.2, K.sub_boom(K.ns(4.0), 42, 28, 1.2, 921), 0.6)
    x += scatter(n, 922, pick(["amb_cave_settle_" + str(i) for i in range(1, 5)], 0.6, 0.8, 1600), 4, 0.25)[0]
    return wet(x, "shaft", 0.5)


def starfall(v):
    """Starfall: stars coming down out of the dark, whistling as they fall, landing far off with a soft glassy hiss."""
    n = K.ns(8.0)
    x = np.zeros(n)
    r = K.rng(931 + v)
    for i in range(6):
        t = 0.2 + i * 1.1 + r.uniform(0, 0.4)
        x += at(n, t, rate(ours("amb_star_whistle"), r.uniform(0.8, 1.2)), r.uniform(0.3, 0.6))
        x += at(n, t + 2.2, K.lowpass(trim(ours("amb_star_cooling"), 0, 1.6, 0.6), 2400, 2), 0.25)
    return wet(x, "field", 0.5)


def the_knock(v):
    """The Knock: three knocks on the door of the room, then nothing, then one more, slower."""
    n = K.ns(8.0)
    x = np.zeros(n)
    for i in range(3):
        x += at(n, 0.3 + 0.45 * i, _knock(941 + i), 0.9)
    x += at(n, 5.6, _knock(944), 1.0)
    return wet(x, "crypt", 0.45)


def watcher(v):
    """The Watcher: a choir swelling up from the void, a slow wet blink, a heart beating somewhere very large."""
    n = K.ns(8.0)
    choir = trim(ours("amb_world_veil_choir"), 6.0, 7.0, 1.5) * K.swell(K.ns(7.0), 0.5, 1.5)
    x = at(n, 0, K.lowpass(choir, 1800, 2), 0.6)
    blink = norm(K.lowpass(K.noise(K.ns(0.5), "brown", 951), 600, 2)) * K.perc(K.ns(0.5), 0.05, 0.3)
    x += at(n, 3.0, blink, 0.5)
    x += heart(n, 34, 953, 0.7) * K.line(n, [(0, 0), (0.45, 0), (0.55, 1), (1, 1)])
    return wet(x, "field", 0.5)


def arrive_abyss(v):
    """Arriving at the Abyssal Shrine: a long drop into the dark, a candle catching, a gong far below."""
    n = K.ns(7.0)
    drop = rswell(norm(K.lowpass(K.noise(K.ns(0.6), "pink", 961), 900, 2)) * K.perc(K.ns(0.6), 0.005, 0.4), "shaft")
    x = at(n, 0, drop[-K.ns(2.0):], 0.5)
    x += at(n, 2.0, K.lowpass(trim(ours("sfx_hearth_fire"), 0, 1.2, 0.4), 3000, 2), 0.3)
    x += at(n, 2.6, gong(hz("A1"), 963, 4.4), 0.7)
    return wet(x, "shaft", 0.5)


def bed_abyss_steps(v):
    """The Dark Steps and the Last Room: dark still air, candles guttering, a low drone, a drip a long way down."""
    n = K.ns(BED)
    al, ar = air(1001, 1200)
    d = K.lowpass(K.drone(41, n, seed=1002), 300, 2) * 0.16
    gutter = scatter(n, 1003, lambda r: K.crackle(K.ns(0.8), 18, int(r.integers(1 << 20)), 0.5) * K.perc(K.ns(0.8), 0.01, 0.5), 9, 0.06)
    far = scatter(n, 1004, lambda r: K.lowpass(wet(drips(r), "shaft", 0.8), 1600, 2), 4, 0.1)
    return wide(al * 0.5 + d, ar * 0.5 + d, gutter, far)


def bed_abyss_lake(v):
    """The Black Mirror: still black water barely lapping, a tone held under it, a gong once in a long while."""
    n = K.ns(BED)
    wl, wr = water(1011, 900)
    tone = K.sine(np.full(n, hz("D3")), n) * (0.5 + 0.5 * np.clip(0.5 + K.smooth_noise(n, 0.08, 1012), 0, 1)) * 0.03
    g = scatter(n, 1013, lambda r: K.lowpass(gong(hz(("D2", "A1")[r.integers(2)]), int(r.integers(1 << 20)), 5.0), 900, 2), 1, 0.12)
    return wide(wl * 0.4 + tone, wr * 0.4 + tone, g)


def bed_abyss_void(v):
    """Starfall and the Eye: the void humming, a choir so far off it might be the wind, stars whistling past now and then."""
    n = K.ns(BED)
    cl, cr = loop_of("amb_world_veil_choir", 0.85, 1400, 1021), loop_of("amb_world_veil_choir", 0.85, 1400, 1022)
    hum = K.lowpass(K.drone(36, n, seed=1023), 220, 2) * 0.15
    stars = scatter(n, 1024, lambda r: rate(ours("amb_star_whistle"), r.uniform(0.7, 1.1)) * 0.6, 4, 0.12)
    return wide(cl * 0.35 + hum, cr * 0.35 + hum, stars)


# ---- the rare worlds ---------------------------------------------------------------------------------------------------

def arrive_rare(v):
    """Arriving somewhere rare: a chord drawn in backwards, opening out, and a single bright note left hanging."""
    n = K.ns(6.0)
    chord = (K.pad(hz("C4"), K.ns(2.0), voices=4, seed=1101, bright=0.3) + K.pad(hz("G4"), K.ns(2.0), voices=3, seed=1102, bright=0.25)
             + K.pad(hz("E5"), K.ns(2.0), voices=3, seed=1103, bright=0.3))
    sw = chord * K.line(K.ns(2.0), [(0, 0), (1, 1)])
    x = at(n, 0, sw, 0.6)
    x += at(n, 2.0, K.glass(hz("G6"), K.ns(3.4), bright=0.6, t60=2.6, seed=1104), 0.4)
    return wet(x, "field", 0.5)


def echoes(v):
    """The Last Campfire's figures talking: low voices, no word you can catch, one of them laughing softly."""
    n = K.ns(6.0)
    return wet(murmur(n, 1111 + v, 0.8), "small", 0.3)


def heartbeat(v):
    """The heart of the gates beating once: a slow double thump you feel more than hear, the air ringing after it."""
    n = K.ns(4.0)
    x = at(n, 0.1, K.sub_boom(K.ns(1.0), 48, 26, 0.6, 1121), 1.0) + at(n, 0.48, K.sub_boom(K.ns(1.0), 40, 24, 0.6, 1122), 0.7)
    x += at(n, 0.2, K.glass(hz("A5"), K.ns(2.6), bright=0.4, t60=2.0, seed=1123), 0.15)
    return wet(x, "field", 0.4)


def doorway(v):
    """A door in the Library opened onto somewhere else: the latch, a gust of other air, the door shutting behind you."""
    n = K.ns(3.2)
    x = at(n, 0, K.lowpass(trim(rate(clip("impact-sounds", "impactWood_light_000"), 1.4), 0, None, 0.1), 3200, 2), 0.4)
    gust = norm(K.bandpass(K.noise(K.ns(1.4), "pink", 1131 + v), 1100, 0.7)) * K.swell(K.ns(1.4), 0.2, 0.8)
    x += at(n, 0.3, gust, 0.5)
    x += at(n, 1.8, _knock(1132 + v, 0.7), 0.6)
    return wet(x, "crypt", 0.35)


def bed_rare_quiet(v):
    """The Quiet and the Lantern Orchard: a meadow at night with nothing in it to fear, crickets, a soft breeze in the grass."""
    n = K.ns(BED)
    rl, rr = loop_of("amb_lands_rustle", 0.9, 3000, 1201), loop_of("amb_lands_rustle", 0.9, 3000, 1202)
    crick = scatter(n, 1203, pick(CRICKETS, 0.95, 1.05, 6000), 22, 0.1)
    owls = scatter(n, 1204, pick(["wild/owl1", "wild/owl2"], 0.95, 1.0, 2600), 1, 0.05)
    return wide(rl * 0.35, rr * 0.35, crick, owls)


def bed_rare_rain(v):
    """Upward Rain: rain falling up all round you out of wet grass, the drops pulled in backwards, a thunder roll far off."""
    n = K.ns(BED)
    a, b = rain_up(n, 1211), rain_up(n, 1212)
    thunder = scatter(n, 1213, lambda r: K.lowpass(ours("amb_storm_roll_" + str(1 + r.integers(3))), 900, 2)[::-1], 2, 0.15)
    return wide(a, b, thunder)


def bed_rare_aurora(v):
    """The Aurora Steppe and the Glass Sea: cold wind over grass, and the aurora's faint shimmering hum high above."""
    n = K.ns(BED)
    sl, sr = loop_of("amb_glass_steppe", 1.0, 3000, 1221), loop_of("amb_glass_steppe", 1.0, 3000, 1222)
    shimmer = loop_of("amb_world_dream_shimmer", 1.0, 6000, 1223) * 0.12
    pad = K.pad(hz("E4"), n, voices=5, seed=1224, bright=0.2) * (0.5 + 0.5 * np.clip(0.5 + K.smooth_noise(n, 0.05, 1225), 0, 1)) * 0.05
    return wide(sl * 0.45 + shimmer + pad, sr * 0.45 + np.roll(shimmer, K.ns(2.0)) + pad)


def bed_rare_campfire(v):
    """The Last Campfire: a fire crackling, the night wide and empty round it, the figures talking low on the far side."""
    n = K.ns(BED)
    fire = tile(ours("sfx_hearth_fire"), n, 1231, 0.6) * 0.35
    cr = K.crackle(n, 7, 1232, 0.4) * 0.12
    crick = scatter(n, 1233, pick(CRICKETS, 0.9, 1.0, 5000), 10, 0.06)
    talk = scatter(n, 1234, lambda r: murmur(K.ns(4.0), int(r.integers(1 << 20)), 0.4), 4, 0.25)
    return wide(fire + cr, np.roll(fire, K.ns(1.0)) + cr, crick, talk)


def bed_rare_heart(v):
    """The Gates' Heart: a vast heart beating slowly at the middle of everything, a choir holding one long chord."""
    n = K.ns(BED)
    h = heart(n, 30, 1241, 0.35)
    chord = (K.pad(hz("C3"), n, voices=6, seed=1242, bright=0.2) + K.pad(hz("G3"), n, voices=5, seed=1243, bright=0.18)) * 0.06
    cl, cr = loop_of("amb_world_veil_choir", 1.0, 2000, 1244), loop_of("amb_world_veil_choir", 1.0, 2000, 1245)
    return wide(h + chord + cl * 0.2, h + chord + cr * 0.2)


# file, render, variants, rms, event, subtitle, attenuation (events) or 0 (a bed, streamed)
SPEC = [
    ("skychime", skychime, 2, -22.0, "gate.world.skychime", "A wind chime rings", 24),
    ("gale", gale, 1, -20.0, "gate.world.gale", "A gale blows across the sky", 64),
    ("sunburst", sunburst, 1, -23.0, "gate.world.sunburst", "The sun breaks through", 48),
    ("great_wind", great_wind, 1, -20.0, "gate.world.great_wind", "A great wind rises from below", 96),
    ("roc", roc, 1, -19.0, "gate.world.roc", "A huge bird cries in the storm", 96),
    ("arrive_sky_archipelago", arrive_sky, 1, -22.0, "gate.world.arrive_sky_archipelago", "Wind rushes past", 16),
    ("offering", offering, 2, -21.0, "gate.world.offering", "A gong sounds in the dark", 32),
    ("hush", hush, 1, -24.0, "gate.world.hush", "Everything goes quiet", 48),
    ("hunger", hunger, 1, -19.0, "gate.world.hunger", "Something growls far below", 64),
    ("starfall", starfall, 1, -21.0, "gate.world.starfall", "Stars whistle as they fall", 96),
    ("knock", the_knock, 1, -20.0, "gate.world.knock", "Someone knocks", 32),
    ("watcher", watcher, 1, -20.0, "gate.world.watcher", "Something vast opens its eye", 96),
    ("arrive_abyssal_shrine", arrive_abyss, 1, -22.0, "gate.world.arrive_abyssal_shrine", "A gong far below", 16),
    ("arrive_rare", arrive_rare, 1, -22.0, "gate.world.arrive_rare", "Somewhere you've never been", 16),
    ("echoes", echoes, 2, -23.0, "gate.world.echoes", "Voices talk low", 16),
    ("heartbeat", heartbeat, 1, -20.0, "gate.world.heartbeat", "A great heart beats", 96),
    ("doorway", doorway, 2, -22.0, "gate.world.doorway", "A door opens onto somewhere else", 16),
    ("amb_sky_wind", bed_sky_wind, 1, -28.0, "gate.world.sky_wind", "Wind across open sky", 0),
    ("amb_sky_ruins", bed_sky_ruins, 1, -28.5, "gate.world.sky_ruins", "Wind moans in broken arches", 0),
    ("amb_sky_high", bed_sky_high, 1, -29.5, "gate.world.sky_high", "Thin air, and something singing far off", 0),
    ("amb_abyss_steps", bed_abyss_steps, 1, -29.5, "gate.world.abyss_steps", "Candles gutter in the dark", 0),
    ("amb_abyss_lake", bed_abyss_lake, 1, -30.0, "gate.world.abyss_lake", "Still black water", 0),
    ("amb_abyss_void", bed_abyss_void, 1, -29.5, "gate.world.abyss_void", "The void hums", 0),
    ("amb_rare_quiet", bed_rare_quiet, 1, -29.0, "gate.world.rare_quiet", "Crickets in a quiet meadow", 0),
    ("amb_rare_rain", bed_rare_rain, 1, -28.5, "gate.world.rare_rain", "Rain falls upward", 0),
    ("amb_rare_aurora", bed_rare_aurora, 1, -29.0, "gate.world.rare_aurora", "Cold wind and a shimmering hum", 0),
    ("amb_rare_campfire", bed_rare_campfire, 1, -28.5, "gate.world.rare_campfire", "A fire crackles, voices talk low", 0),
    ("amb_rare_heart", bed_rare_heart, 1, -29.0, "gate.world.rare_heart", "A vast heart beats slowly", 0),
]


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def main(argv):
    return C.main(argv, SPEC, "the B themes and the rare worlds")


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
