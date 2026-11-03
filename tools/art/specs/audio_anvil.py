"""The War Anvil's own sounds (SPEC §46.10; plan docs/weapon-forge/PLAN.md §4.3, stage 5): steel that rings truer.

The armour forge's sound is a bell melody, a note per strike; a weapon's is the blade's own ring. Each clean strike rings
the blade a step nearer its style's true note (the game pitches `anvil.ring` there: AnvilSong.ringPitch), and the draw
from the stone rings it whole (`anvil.drawn`, at the true note). So the ring and the drawn blade are cut at one A
(880 Hz) and the game moves them; everything else is heard as rendered.

  - the hammer on each tier's metal, thinner and brighter than the armour's plate (a blade, not a breastplate): iron to D,
    gatewrought (C, hard and glassy with its diamond grit), veilwoven (B, a voice of glass in it), lordscale (A, dark,
    its scales settling after), sovereign (S, a high halo);
  - the blade's ring, the tongs turning it over, the plunge's roar and the lift out of the trough, the hilt's peening
    taps, the scrape rising as it's drawn from the stone and the blade coming free;
  - the ranks' acts (§46.9): a flake knapped off flint or bone, the cord wound round the grip, the grindstone on the
    edge, the hot bar twisting, the Deepglass horn's hum (the game pitches it truer as the blade lies level) and the
    starlight.
The fire and bellows, the pull from the coals, the count-in and a missed beat are the Forge's own (audio_forge.py).

Built like the forge's: a CC0 recording for the physical transient (Kenney's Impact Sounds and RPG Audio,
tools/art/sources/kenney, CC0 1.0), the audio kit for the steel's ring and the air round it; seeded variants. Mono,
mastered to the arms loudness (audio_mix.py). Written to src/main/resources/assets/awaken/sounds/anvil/ and registered in
src/main/merge/arms/assets/awaken/ (the anvil.* entries of sounds.json, subtitles in lang/en_us.json; the rest of both
files is audio_arms.py's and arms_weapons.py's and is kept).

    python3 tools/art/specs/audio_anvil.py          render everything and write the JSON
    python3 tools/art/specs/audio_anvil.py --json   only the JSON
    python3 tools/art/specs/audio_anvil.py strike_  only these (name prefixes)
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
import audio_arms as A  # noqa: E402  (rec, shift, ring, scrape, spark, sub, trim)
import audio_forge as F  # noqa: E402  (room, mix, unit, sizzle)
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
OUT = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/anvil")
MERGE = os.path.join(ROOT, "src/main/merge/arms/assets/awaken")
SR = K.SR
A5 = 880.0  # the ring's and the drawn blade's note; the game pitches them to each style's true note

room, mix, peak, sizzle = F.room, F.mix, F.unit, F.sizzle


def at(x, sec, n=None):
    """x laid sec seconds into a silence n samples long (as long as it needs if n is None)."""
    s = K.ns(sec)
    n = n or s + len(x)
    out = np.zeros(n)
    out[s:s + len(x)] += x[: max(0, n - s)]
    return out


def thud(dur, f0, f1, t60, seed):
    """The hot bar's own dull body under the hammer: soft steel doesn't ring, it gives."""
    n = K.ns(dur)
    body = peak(K.lowpass(K.noise(n, "pink", seed), 700, 2)) * K.perc(n, 0.001, t60 * 0.5)
    return mix((body, 0.5), (A.sub(dur, f0, f1, t60), 0.6))


# ---- the hammer on each tier's metal ------------------------------------------------------------------------------

METALS = ["iron", "gate", "veil", "lord", "star"]


def strike(metal, k):
    r = K.rng(4600 + 31 * k + METALS.index(metal) * 7)
    var = 1.0 + (k - 1) * 0.03
    if metal == "iron":
        t = A.shift(A.rec(A.pick(["impactMetal_medium_001", "impactMetal_medium_004", "impactMetal_light_001"], r)), 1.12 * var)
        face = A.ring(1720 * var, 0.45, 0.9, 4610 + k, bright=0.65)
        x = mix((t, 0.85), (face, 0.5), (thud(0.25, 130, 85, 0.12, 4620 + k), 0.25), (sizzle(0.3, 4630 + k), 0.2))
    elif metal == "gate":
        t = A.shift(A.rec(A.pick(["impactMetal_light_001", "impactMetal_light_004", "impactMetal_medium_001"], r)), 1.2 * var)
        grit = A.trim(A.shift(A.rec(A.pick(["impactGlass_heavy_000", "impactGlass_heavy_004"], r)), 1.45), 0.18)
        face = A.ring(2150 * var, 0.4, 0.8, 4640 + k, bright=0.85)
        x = mix((t, 0.8), (grit, 0.25), (face, 0.6), (thud(0.2, 140, 95, 0.1, 4650 + k), 0.22), (A.spark(0.3, 4660 + k), 0.25))
    elif metal == "veil":
        t = A.shift(A.rec(A.pick(["impactMetal_light_002", "impactMetal_light_001", "impactMetal_medium_004"], r)), 1.05 * var)
        n = K.ns(1.6)
        voice = R.pk(K.glass(990 * var, n, bright=0.45, t60=1.1, seed=4670 + k)) + R.pk(K.glass(992.5 * var, n, bright=0.35, t60=1.1, seed=4671 + k)) * 0.8
        breath = peak(K.bandpass(K.noise(K.ns(0.5), "white", 4672 + k), 3200, 0.8)) * K.perc(K.ns(0.5), 0.004, 0.35)
        x = mix((t, 0.75), (peak(voice), 0.35), (A.ring(1480 * var, 0.35, 0.7, 4673 + k, 0.5), 0.25), (breath, 0.15),
                (thud(0.22, 120, 80, 0.1, 4674 + k), 0.22))
    elif metal == "lord":
        t = A.shift(A.rec(A.pick(["impactMetal_heavy_000", "impactMetal_heavy_001", "impactMetal_heavy_003"], r)), 0.95 * var)
        scales = A.trim(A.shift(A.rec(A.pick(["impactPlate_medium_000", "impactPlate_medium_003"], r)), 1.35), 0.2)
        face = A.ring(760 * var, 0.8, 1.2, 4680 + k, bright=0.45)
        x = mix((t, 0.9), (at(scales, 0.028), 0.22), (at(scales * 0.6, 0.061), 0.14), (face, 0.4),
                (A.sub(0.4, 92, 52, 0.18, drive=1.8), 0.45), (sizzle(0.4, 4690 + k), 0.16))
    else:  # star
        t = A.shift(A.rec(A.pick(["impactMetal_light_002", "impactMetal_light_004", "impactMetal_medium_001"], r)), 1.0 * var)
        halo = R.pk(K.fm_bell(2640 * var, K.ns(2.0), t60=1.5, index=1.4, ratio=2.76, seed=4700 + k))
        face = A.ring(1540 * var, 0.7, 1.3, 4701 + k, bright=0.8)
        x = mix((t, 0.75), (face, 0.5), (halo, 0.22), (thud(0.25, 120, 80, 0.12, 4702 + k), 0.22), (sizzle(0.25, 4703 + k), 0.12))
    if metal != "lord":
        x = K.highpass(x, 90, 2)  # a blade, not a breastplate: lighter underneath than the armour's strikes (lordscale keeps its weight)
    return room(x, 0.16)


# ---- the blade ----------------------------------------------------------------------------------------------------

def ring(k):
    """The blade's own ring after a clean strike, at A5 (the game pitches it toward the style's true note)."""
    body = A.ring(A5, 1.5, 2.4, 4800 + k, bright=0.75)
    # a little of the long blade's lower mode, so a long blade pitched down still sounds long
    low = A.ring(A5 / 2.756, 1.1, 2.4, 4810 + k, bright=0.2)
    n = len(body)
    on = K.line(n, [(0, 0), (0.004, 1), (1, 1)])  # sits just behind the strike's transient
    return room(mix((body * on, 0.8), (low[:n] * on, 0.18)), 0.24)


def flip(k):
    """The tongs turning the blade over: the grip shifts, the blade lands on its other face and slides a little."""
    r = K.rng(4900 + k)
    grip = A.trim(A.shift(A.rec(A.pick(["impactMetal_light_001", "impactMetal_light_004"], r)), 1.55), 0.12)
    land = A.trim(A.shift(A.rec(A.pick(["impactMetal_light_002", "impactMetal_medium_004"], r)), 1.3), 0.25)
    slide = A.scrape(0.14, 4910 + k, fc=3000)
    return room(mix((grip, 0.4), (at(land, 0.075), 0.6), (at(slide, 0.09), 0.25), (at(A.ring(2350, 0.18, 0.35, 4920 + k, 0.5), 0.075), 0.2)), 0.12)


def quench(k):
    """Into the trough: a roar of steam bursting up, the water boiling round the blade, dying away."""
    dur = 3.0
    n = K.ns(dur)
    burst = peak(K.bandpass(K.noise(n, "white", 5000 + k), 2600, 0.6)) * K.perc(n, 0.006, 0.9)
    turb = 0.6 + 0.4 * (0.5 + 0.5 * K.smooth_noise(n, 18.0, 5001 + k))
    roar = peak(K.lowpass(K.noise(n, "pink", 5002 + k), 1900, 2)) * K.line(n, [(0, 0), (0.01, 1), (0.25, 0.75), (0.8, 0.2), (1, 0)]) * turb
    boil = peak(K.bandpass(K.crackle(n, 130.0, 5003 + k, 0.4, 0.5), 820, 1.2)) * K.line(n, [(0, 0), (0.03, 1), (0.5, 0.55), (1, 0)])
    rumble = peak(K.lowpass(K.noise(n, "pink", 5004 + k), 190, 2)) * K.perc(n, 0.02, 1.4)
    plunge = A.rec("impactSoft_medium_001" if k % 2 == 0 else "impactSoft_medium_003")
    return room(mix((burst, 0.75), (roar, 0.55), (boil, 0.4), (rumble, 0.35), (plunge, 0.45)), 0.1)


def lift(k):
    """Out of the water, steaming: a slosh, drops falling back into the trough, the last of the hiss."""
    r = K.rng(5100 + k)
    dur = 1.5
    n = K.ns(dur)
    out = np.zeros(n)
    slosh = A.shift(A.rec("impactSoft_medium_002" if k % 2 == 0 else "impactSoft_medium_000"), 1.3)
    out[:len(slosh)] += slosh[:n] * 0.3
    for i in range(7):
        s = K.ns(0.12 + 0.18 * i + float(r.uniform(-0.04, 0.04)))
        if s >= n:
            break
        drop = R.pk(K.glass(float(r.uniform(1300, 2600)), K.ns(0.09), bright=0.3, t60=0.06, seed=5110 + 10 * k + i))
        out[s:s + len(drop)] += drop[: n - s] * (0.35 - 0.035 * i)
    hiss = peak(K.bandpass(K.noise(n, "white", 5120 + k), 5200, 1.0)) * K.line(n, [(0, 0), (0.02, 0.55), (1, 0)])
    return room(mix((out, 1.0), (hiss, 0.3)), 0.12)


def tap(k):
    """The hilt's peening taps: a small hammer seating the guard, the grip, the pommel."""
    r = K.rng(5200 + k)
    t = A.trim(A.shift(A.rec(A.pick(["impactMetal_light_001", "impactMetal_light_002", "impactMetal_light_004"], r)), 1.6 + 0.04 * k), 0.14)
    knock = A.trim(A.shift(A.rec(A.pick(["impactWood_light_000", "impactWood_light_002"], r)), 1.3), 0.1)
    return room(mix((t, 0.7), (A.ring(2600 + 160 * k, 0.2, 0.4, 5210 + k, 0.55), 0.3), (knock, 0.25)), 0.1)


def rising(dur, seed, f0, f1, q=2.0):
    """Grainy steel-on-stone noise whose band climbs from f0 to f1 as the blade comes up."""
    x = K.noise(K.ns(dur), "white", seed)
    width = 1.0 / q

    def resp(t, f):
        fc = f0 * (f1 / f0) ** float(np.clip(t / dur, 0, 1))
        return np.exp(-0.5 * (np.log(np.maximum(f, 1.0) / fc) / width) ** 2)

    return peak(K.stft_filter(x, resp))


def scrape(k):
    """Drawn up out of the stone: steel grinding on stone, rising as it comes."""
    dur = 1.45
    n = K.ns(dur)
    grain = K.smooth_noise(n, 90.0, 5300 + k) * 0.5 + 0.5
    steel = rising(dur, 5301 + k, 1300, 3600) * grain * K.line(n, [(0, 0), (0.06, 0.45), (0.85, 1), (1, 0.3)])
    stone = peak(K.bandpass(K.crackle(n, 70.0, 5302 + k, 0.5, 0.6), 950, 1.4)) * K.line(n, [(0, 0), (0.05, 0.8), (1, 0.4)])
    start = A.trim(A.rec("impactMining_000" if k % 2 == 0 else "impactMining_003"), 0.2)
    return room(mix((steel, 0.6), (stone, 0.3), (start, 0.25)), 0.12)


def drawn(k):
    """Free of the stone: a last bright chirp and the whole blade ringing (the game pitches it to its true note)."""
    n = K.ns(3.6)
    chirp = rising(0.16, 5400 + k, 3000, 5200) * K.perc(K.ns(0.16), 0.002, 0.12)
    whole = A.ring(A5, 2.6, 3.4, 5410 + k, bright=0.85)
    low = A.ring(A5 / 2.756, 1.8, 3.0, 5411 + k, bright=0.25)
    air = peak(K.bandpass(K.noise(K.ns(0.45), "pink", 5412 + k), 1500, 0.7)) * K.swell(K.ns(0.45), 0.3, 1.5)
    lift_ = np.zeros(n)
    bm = A.sub(0.7, 70, 55, 0.4)
    lift_[K.ns(0.08):K.ns(0.08) + len(bm)] += bm
    return room(mix((chirp, 0.35), (at(whole, 0.06, n), 0.75), (at(low, 0.06, n), 0.2), (air, 0.25), (lift_, 0.25)), 0.26)


# ---- the ranks' acts (§46.9) --------------------------------------------------------------------------------------

def knap(k):
    """E: a flake struck off a flint or bone edge: a hard glassy crack, chips skittering, a little dust."""
    r = K.rng(5500 + k)
    crack = A.trim(A.shift(A.rec(A.pick(["impactGlass_light_000", "impactGlass_light_002", "impactGlass_light_004"], r)), 1.6 + 0.05 * k), 0.08)
    stone = A.trim(A.shift(A.rec(A.pick(["impactMining_000", "impactMining_001", "impactMining_003"], r)), 1.45), 0.1)
    n = K.ns(0.35)
    chips = np.zeros(n)
    for i in range(3):
        s = K.ns(0.018 + 0.03 * i + float(r.uniform(0, 0.012)))
        c = R.pk(K.glass(float(r.uniform(3200, 5200)), K.ns(0.05), bright=0.6, t60=0.035, seed=5510 + 10 * k + i))
        chips[s:s + len(c)] += c[: n - s] * (0.4 - 0.1 * i)
    dust = peak(K.highpass(K.noise(K.ns(0.12), "white", 5520 + k), 4000, 2)) * K.perc(K.ns(0.12), 0.002, 0.08)
    return room(mix((crack, 0.75), (stone, 0.45), (chips, 0.35), (dust, 0.12)), 0.06)


def cord(k):
    """E: a turn of cord pulled tight round the grip: rawhide rubbing, a creak as it bites."""
    r = K.rng(5600 + k)
    dur = 0.6
    n = K.ns(dur)
    rub = peak(K.bandpass(K.noise(n, "white", 5601 + k), 1600, 1.5)) * (K.smooth_noise(n, 60.0, 5602 + k) * 0.4 + 0.6) * K.swell(n, 0.55, 1.6)
    creak = A.rec(A.pick(["handleSmallLeather", "handleSmallLeather2"], r))
    cloth = A.rec(A.pick(["cloth1", "cloth2", "cloth3", "cloth4"], r))
    return room(mix((rub, 0.45), (at(creak, 0.22), 0.5), (cloth, 0.2)), 0.06)


def wheel(k):
    """C: the grindstone turning against the edge: a hissing grind that swells with each turn, sparks thrown off."""
    dur = 0.85
    n = K.ns(dur)
    t = np.arange(n) / SR
    turn = 0.65 + 0.35 * np.sin(2 * np.pi * (3.1 + 0.3 * k) * t + k)
    grind = peak(K.bandpass(K.noise(n, "white", 5700 + k), 3800, 1.0)) * (K.smooth_noise(n, 200.0, 5701 + k) * 0.35 + 0.65) * turn
    stone = peak(K.lowpass(K.noise(n, "pink", 5702 + k), 260, 2))
    env = K.line(n, [(0, 0), (0.05, 1), (0.75, 0.9), (1, 0)])
    return room(mix((grind * env, 0.6), (A.spark(dur, 5703 + k) * env, 0.4), (stone * env, 0.25)), 0.1)


def twist(k):
    """A: the hot bar turned in the tongs: steel groaning low as it gives, a hiss of scale."""
    r = K.rng(5800 + k)
    dur = 1.0
    n = K.ns(dur)
    groan = A.shift(A.rec(A.pick(["creak1", "creak2", "creak3"], r)), 0.55 + 0.04 * k)
    metal = peak(K.bandpass(K.crackle(n, 45.0, 5801 + k, 0.4, 0.8), 430, 3.0)) * K.line(n, [(0, 0), (0.15, 1), (0.7, 0.6), (1, 0)])
    bend = A.ring(260 + 30 * k, 0.5, dur, 5802 + k, bright=0.3) * K.line(n, [(0, 0), (0.3, 1), (1, 0.4)])
    return room(mix((groan, 0.5), (metal, 0.4), (bend, 0.2), (sizzle(0.6, 5803 + k), 0.1)), 0.12)


def hum(k):
    """B: the Deepglass horn humming under the blade as it balances: a pure glass tone with a slow beat (at A4; the
    game pitches it to the style's true note, and a little flat until the blade lies level)."""
    dur = 3.2
    n = K.ns(dur)
    tone = K.drone(A5 / 2, n, partials=(1, 2, 3), amps=(1.0, 0.45, 0.12), beat=0.7, seed=5900 + k)
    glassy = R.pk(K.glass(A5, n, bright=0.35, t60=2.4, seed=5901 + k, hit=0.0))
    breath = peak(K.bandpass(K.noise(n, "pink", 5902 + k), A5 / 2, 9.0))
    env = K.adsr(n, a=0.45, d=0.4, s=0.8, r=1.1)
    return room(mix((peak(tone) * env, 0.55), (glassy * env, 0.25), (breath * env, 0.12)), 0.3)


def star(k):
    """S: starlight on the blade: an airy rise, then a high chord blooming, motes of light chiming in it."""
    r = K.rng(6000 + k)
    dur = 3.6
    n = K.ns(dur)
    rise = rising(0.6, 6001 + k, 1500, 7000, q=1.4) * K.line(K.ns(0.6), [(0, 0), (0.8, 0.8), (1, 0)])
    bloom = np.zeros(n)
    s = K.ns(0.5)
    for m, g in ((93, 1.0), (97, 0.75), (100, 0.6), (105, 0.3)):  # A6, C#7, E7, A7
        bloom[s:] += R.pk(K.fm_bell(K.hz(m), n - s, t60=2.2, index=1.2, ratio=2.0, seed=6010 + m)) * g
    motes = np.zeros(n)
    for i in range(12):
        ms = K.ns(0.55 + float(r.uniform(0, 2.2)))
        c = R.pk(K.glass(float(r.uniform(3000, 6200)), K.ns(0.3), bright=0.7, t60=0.25, seed=6020 + 10 * k + i))
        motes[ms:ms + len(c)] += c[: n - ms] * float(r.uniform(0.15, 0.35))
    under = K.sine(220.0, n) * K.line(n, [(0, 0), (0.15, 0), (0.3, 0.6), (1, 0)])
    return room(mix((rise, 0.35), (peak(bloom), 0.55), (motes, 0.3), (under, 0.2)), 0.35)


# name: (render(k), variants, attenuation, subtitle); files sounds/anvil/anvil_<name>_<k>.ogg, events anvil.<name>
EVENTS = OrderedDict([
    ("strike", (lambda k: strike("iron", k), 3, 20, "Hammer rings on steel")),
    ("strike_gate", (lambda k: strike("gate", k), 3, 20, "Hammer rings on gatewrought steel")),
    ("strike_veil", (lambda k: strike("veil", k), 3, 20, "Hammer rings on veilwoven steel")),
    ("strike_lord", (lambda k: strike("lord", k), 3, 24, "Hammer rings on lordscale")),
    ("strike_star", (lambda k: strike("star", k), 3, 24, "Hammer rings on sovereign steel")),
    ("ring", (ring, 2, 16, "Blade rings true")),
    ("flip", (flip, 2, 12, "Tongs turn the blade")),
    ("quench", (quench, 2, 16, "Blade roars in the trough")),
    ("lift", (lift, 2, 12, "Blade lifted, steaming")),
    ("tap", (tap, 3, 12, "Hilt tapped home")),
    ("scrape", (scrape, 2, 12, "Steel scrapes on stone")),
    ("drawn", (drawn, 1, 24, "Blade drawn from the stone")),
    ("knap", (knap, 3, 10, "A flake knapped off")),
    ("cord", (cord, 2, 8, "Cord wound tight")),
    ("wheel", (wheel, 2, 12, "Grindstone on the edge")),
    ("twist", (twist, 2, 14, "Hot steel twists")),
    ("hum", (hum, 1, 12, "Deepglass horn hums")),
    ("star", (star, 1, 20, "Starlight on the blade")),
])
QUIET = ("cord", "lift", "hum", "tap")
# the Forge's own, kept (audio_forge.py's files): event -> (forge file stem, variants, attenuation, subtitle)
FORGE = OrderedDict([
    ("bellows", ("bellows", 2, 16, "Bellows breathe")),
    ("pull", ("pull", 2, 12, "Hot metal pulled from the fire")),
    ("miss", ("miss", 2, 10, "Hammer misses the beat")),
    ("count", ("count", 2, 10, "Anvil counts in")),
])


def write_json():
    path = os.path.join(MERGE, "sounds.json")
    cur = json.load(open(path), object_pairs_hook=OrderedDict) if os.path.exists(path) else OrderedDict()
    sounds = OrderedDict((k, v) for k, v in cur.items() if not k.startswith("anvil."))
    lang = OrderedDict()
    for event, (stem, variants, att, subtitle) in FORGE.items():
        sounds[f"anvil.{event}"] = OrderedDict(subtitle=f"subtitles.awaken.anvil.{event}", sounds=[
            OrderedDict(name=f"awaken:forge/forge_{stem}_{k}", attenuation_distance=att) for k in range(variants)])
        lang[f"subtitles.awaken.anvil.{event}"] = subtitle
    for event, (_, variants, att, subtitle) in EVENTS.items():
        sounds[f"anvil.{event}"] = OrderedDict(subtitle=f"subtitles.awaken.anvil.{event}", sounds=[
            OrderedDict(name=f"awaken:anvil/anvil_{event}_{k}", attenuation_distance=att) for k in range(variants)])
        lang[f"subtitles.awaken.anvil.{event}"] = subtitle
    with open(path, "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    lpath = os.path.join(MERGE, "lang/en_us.json")
    cur = json.load(open(lpath), object_pairs_hook=OrderedDict)
    cur = OrderedDict((k, v) for k, v in cur.items() if not k.startswith("subtitles.awaken.anvil."))
    cur.update(lang)
    with open(lpath, "w") as f:
        json.dump(cur, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"anvil events: {len(FORGE) + len(EVENTS)} in merge/arms")


def build(out):  # build_all.py makes textures; audio is rendered on demand (main)
    pass


def render(event, k):
    """One variant mastered as it ships."""
    rms, _, ceil, _ = TARGETS["arms"]
    y = EVENTS[event][0](k)
    y, _ = R.master(y, rms - (4.0 if event in QUIET else 0.0), ceil, 6.0)
    return y


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    q = TARGETS["arms"][3]
    if "--json" not in argv:
        os.makedirs(OUT, exist_ok=True)
        for event, (_, variants, *_) in EVENTS.items():
            if only and not any(event.startswith(p) for p in only):
                continue
            for k in range(variants):
                y = render(event, k)
                path = os.path.join(OUT, f"anvil_{event}_{k}.ogg")
                K.write_ogg(path, y, q, None)
                print(f"  anvil_{event}_{k}  {len(y) / SR:5.2f} s  RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}  {os.path.getsize(path) // 1024} KB")
    write_json()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
