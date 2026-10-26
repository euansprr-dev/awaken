"""World sound (docs/sound/PLAN.md, SPEC §13 "Plan v2 · world sound"): the air beds the Conductor mixes into its one
stream (core.audio.client.AirMixer). Each family's air is up to three mono layers of co-prime length (37, 41 and
53 seconds repeat together only after 22 hours), so the mix never repeats audibly; each layer loops seamlessly on its
own and holds nothing salient (no call, no knock you could notice coming round again): salient sounds are the
Conductor's one-shots. Mastered under the ambience rule; the soundscape files' gains set the balance.

  amb_world_greenwood_a   broadleaf hush: wind moving through a broadleaf canopy at a distance, slow swells
  amb_world_greenwood_b   the far wood: a low, slow roar of the forest beyond, deeper in the gusts
  amb_world_greenwood_c   open air: the faintest high air between the trees
  amb.world               the Conductor's stream event (sounds.json needs a streamed entry; the mixer supplies the audio)

AWAKEN's own places and moments (Euan, 2026-10-25: AmbientSounds plays the nature; we build only what is ours), in the
System's D major pentatonic where they are musical, never in its glass timbre's exact voice:

  amb_world_stillpoint_sea / _tone      the Stillpoint: wind over the cloud sea (stereo), a temple's held tone
  amb_world_halo_station / _deep        the Halo: the station's hum and ventilation, the deep quiet outside
  amb_world_dream_shimmer / _air        Lucidium: a soft shimmering chord (stereo), a breathing air
  amb_world_veil_choir                  the Veil (an overlay while you project): a far choir on D and A
  amb_world_elder_groan                 Elderwood: the colossi groaning in slow gusts, a golden glimmer high up
  amb_world_lumen_hum                   Lumen Forest: a low soft hum of two near tones
  world.chime, world.bell_far           the Stillpoint's chimes stirred by the wind, a far temple bell
  world.halo_hiss, _clunk, _chirp       the Halo: airlock hisses, a far metal clunk, data chirps
  world.dream_swell                     Lucidium: reversed glass swells
  world.elder_creak, world.elder_glint  Elderwood: a great limb creaking, a glint of gold light
  world.lumen_chime                     Lumen Forest: specks chiming, answering each other at night
  living.tell.*                         Living Wilds happenings heard from afar (the living module plays them):
                                        stampede rumble, murmuration wings, the Wild Hunt's horn, the Deep Tide's
                                        drone, a caravan's bells
"""
import numpy as np

import audiokit as K


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _swells(n, seed, rate, floor, power=1.6):
    """Slow gusting: a smooth envelope between floor and 1 that dwells low and rises in swells."""
    e = 0.5 + 0.5 * K.smooth_noise(n, rate, seed)
    return floor + (1 - floor) * np.clip(e, 0, 1) ** power


def greenwood_hush(seed):
    L = 40.0  # 37 s after the 3 s seam
    n = K.ns(L)
    body = _norm(K.bandpass(K.noise(n, "pink", seed + 1), 760, 0.55))
    leaves = _norm(K.bandpass(K.noise(n, "pink", seed + 2), 2600, 0.8))
    g = _swells(n, seed + 3, 0.09, 0.35)
    x = (0.72 * body + 0.28 * leaves * g) * g
    return K.loop_seamless(K.lowpass(x, 6500, 2), 3.0)


def greenwood_far(seed):
    L = 45.0  # 41 s after the 4 s seam
    n = K.ns(L)
    roar = _norm(K.lowpass(K.noise(n, "brown", seed + 1), 300, 2))
    cen = 180 * 2 ** (0.4 * K.smooth_noise(n, 0.06, seed + 2))
    t = np.arange(n) / K.SR

    def resp(tsec, f):
        return K.bp_resp(cen[min(n - 1, int(tsec * K.SR))], 1.2)(f)
    moan = _norm(K.stft_filter(K.noise(n, "pink", seed + 3), resp))
    g = _swells(n, seed + 4, 0.06, 0.45)
    x = (0.6 * roar + 0.4 * moan) * g * (0.92 + 0.08 * np.sin(2 * np.pi * t / L))
    return K.loop_seamless(K.highpass(x, 35, 2), 4.0)


def greenwood_air(seed):
    L = 57.0  # 53 s after the 4 s seam
    n = K.ns(L)
    air = _norm(K.bandpass(K.noise(n, "pink", seed + 1), 4800, 0.7))
    g = _swells(n, seed + 2, 0.07, 0.5, 1.2)
    return K.loop_seamless(K.lowpass(air * g, 9000, 2), 4.0)


# ---- AWAKEN's places and moments -----------------------------------------------------------------------------------

PENTA = ("D", "E", "F#", "A", "B")


def _hz(name):
    return K.hz(K.midi(name))


def _room(x, t60, seed, wet=0.45, bright=0.5):
    """A synthesised space around a mono one-shot, folded back to mono (the game places it; Sound Physics adds the room)."""
    ir = K.reverb_ir(t60=t60, predelay=0.02, bright=bright, seed=seed)
    w = K.mono(K.convolve(x, ir))
    out = np.zeros(len(w))
    out[:len(x)] += (1 - wet) * x
    return out + wet * w / (np.max(np.abs(w)) + 1e-12) * np.max(np.abs(x))


def _loop(x, seam):
    return K.loop_seamless(x, seam)


def stillpoint_sea(seed):
    L = 40.0  # 37 s
    n = K.ns(L)
    chans = []
    for c in range(2):
        body = _norm(K.bandpass(K.noise(n, "pink", seed + 10 * c + 1), 520, 0.5))
        high = _norm(K.bandpass(K.noise(n, "pink", seed + 10 * c + 2), 2400, 0.9))
        g = _swells(n, seed + 10 * c + 3, 0.05, 0.4)
        chans.append((0.8 * body + 0.2 * high * g) * g)
    return _loop(K.lowpass(np.stack(chans, axis=1), 7000, 2), 3.0)


def stillpoint_tone(seed):
    L = 57.0  # 53 s (with its 37 s partner: 33 minutes before they meet again)
    n = K.ns(L)
    t = K.tvec(n)
    tone = K.drone(_hz("D2"), n, seed=seed) + 0.5 * K.pad(_hz("A3"), n, voices=5, seed=seed + 1, bright=0.15)
    tone *= 0.75 + 0.25 * np.sin(2 * np.pi * t / L) ** 2
    return _loop(K.lowpass(_norm(tone), 2200, 2), 4.0)


def halo_station(seed):
    L = 40.0  # 37 s
    n = K.ns(L)
    t = K.tvec(n)
    hum = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((50, 1.0), (100, 0.5), (150, 0.3), (250, 0.12)))
    vent = _norm(K.bandpass(K.noise(n, "pink", seed + 1), 620, 0.5)) * (0.85 + 0.15 * K.smooth_noise(n, 0.08, seed + 2))
    return _loop(0.45 * _norm(hum) + 0.55 * vent, 3.0)


def halo_deep(seed):
    L = 57.0  # 53 s (with its 37 s partner: 33 minutes before they meet again)
    n = K.ns(L)
    deep = _norm(K.lowpass(K.noise(n, "brown", seed + 1), 70, 2)) * _swells(n, seed + 2, 0.04, 0.6)
    return _loop(K.highpass(deep, 25, 2), 4.0)


def dream_shimmer(seed):
    """Pure tones only (no noise, no buzzy waves): D, A, E and F# as soft sines, each with a twin a hair apart so they
    beat slowly, a little octave above, breathing in and out (left and right drift apart)."""
    L = 40.0  # 37 s
    n = K.ns(L)
    t = K.tvec(n)
    r = K.rng(seed)
    chans = []
    for c in range(2):
        x = np.zeros(n)
        for i, (m, a) in enumerate((("D3", 1.0), ("A3", 0.8), ("E4", 0.55), ("F#4", 0.45), ("A4", 0.25), ("D5", 0.15))):
            f = _hz(m)
            beat = (1 + c) * (0.11 + 0.05 * i) / f  # a few cents, different each side
            breathe = 0.6 + 0.4 * (0.5 + 0.5 * np.sin(2 * np.pi * t / L * (1 + i % 3) + r.uniform(0, 6.28)))
            x += a * breathe * (np.sin(2 * np.pi * f * t) + 0.7 * np.sin(2 * np.pi * f * (1 + beat * 37) * t + 1.1))
        chans.append(_norm(x))
    return _loop(K.lowpass(np.stack(chans, axis=1), 3000, 2), 3.0)

def dream_air(seed):
    """Slow bell-like tones in D major pentatonic swelling up and fading (long soft attacks, no strike, no noise):
    the dream drifting. Placed on whole cycles so the loop joins."""
    L = 57.0  # 53 s
    n = K.ns(L)
    t = K.tvec(n)
    r = K.rng(seed)
    out = np.zeros(n)
    for k in range(9):
        f = _hz(PENTA[int(r.integers(len(PENTA)))] + str(int(r.integers(4, 6))))
        c = r.uniform(4.0, L - 8.0)
        w = r.uniform(2.5, 4.5)
        env = np.exp(-0.5 * ((t - c) / w) ** 2)
        out += env * (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t + 0.6)) * r.uniform(0.4, 1.0)
    return _loop(K.lowpass(_norm(out), 4000, 2), 4.0)

def veil_choir(seed):
    L = 45.0  # 41 s
    n = K.ns(L)
    voice = K.choir(_hz("D3"), n, vowel="o", voices=6, seed=seed, bright=0.25) + 0.7 * K.choir(_hz("A3"), n, vowel="o", voices=5, seed=seed + 1, bright=0.2)
    return _loop(K.lowpass(_norm(voice) * _swells(n, seed + 2, 0.05, 0.55, 1.2), 3200, 2), 4.0)


def elder_groan(seed):
    L = 45.0  # 41 s
    n = K.ns(L)
    gust = _swells(n, seed + 1, 0.05, 0.15, 2.2)
    cen = 95 * 2 ** (0.5 * K.smooth_noise(n, 0.07, seed + 2))

    def resp(tsec, f):
        return K.bp_resp(cen[min(n - 1, int(tsec * K.SR))], 4.0)(f)
    groan = _norm(K.stft_filter(K.noise(n, "pink", seed + 3), resp)) * gust
    glimmer = sum(K.pad(_hz(m), n, voices=3, seed=seed + 4 + i, bright=0.1) for i, m in enumerate(("A5", "D6")))
    x = 0.82 * groan + 0.18 * _norm(glimmer) * (1 - gust) ** 2
    return _loop(K.lowpass(x, 4000, 2), 4.0)


def lumen_hum(seed):
    L = 40.0  # 37 s
    n = K.ns(L)
    t = K.tvec(n)
    f1, f2 = _hz("D2"), _hz("D2") + 1.0 / 37.0
    hum = np.sin(2 * np.pi * f1 * t) + 0.8 * np.sin(2 * np.pi * f2 * t) + 0.2 * np.sin(2 * np.pi * 3 * f1 * t + 0.4)
    air = _norm(K.lowpass(K.noise(n, "pink", seed + 1), 300, 2))
    return _loop(0.6 * _norm(hum) + 0.4 * air * _swells(n, seed + 2, 0.06, 0.5), 3.0)


def _glass_notes(seed, octave, count, t60, bright, gap=(0.12, 0.35)):
    r = K.rng(seed)
    n = K.ns(4.0)
    out = np.zeros(n)
    t0 = 0.05
    for k in range(count):
        f = _hz(PENTA[int(r.integers(len(PENTA)))] + str(octave + int(r.integers(0, 2))))
        g = K.glass(f, K.ns(2.5), bright=bright, t60=t60, seed=seed + 7 * k, hit=r.uniform(0.4, 1.0))
        s = K.ns(t0)
        out[s:s + len(g)] += g[: n - s] * r.uniform(0.5, 1.0)
        t0 += r.uniform(*gap)
    return out


def chime(seed):
    return _room(_glass_notes(seed, 5, 1 + seed % 3, 1.6, 0.35), 2.5, seed)


def lumen_chime(seed):
    return _room(_glass_notes(seed, 6, 1 + seed % 2, 1.1, 0.25), 1.8, seed, wet=0.5)


def bell_far(seed):
    b = K.bell(_hz("D3"), K.ns(5.0), t60=5.0, bright=0.35, seed=seed)
    return K.lowpass(_room(b, 4.0, seed, wet=0.6, bright=0.3), 2600, 2)


def halo_hiss(seed):
    r = K.rng(seed)
    n = K.ns(1.6)
    env = K.adsr(n, 0.03, 0.2, 0.55, 0.9)
    hiss = _norm(K.bandpass(K.noise(n, "white", seed), r.uniform(2400, 3800), 0.9)) * env
    thunk = K.bandnoise(K.ns(0.12), 140, 2.0, seed + 1) * K.perc(K.ns(0.12), 0.001, 0.08)
    out = 0.8 * hiss
    out[:len(thunk)] += 0.6 * _norm(thunk)
    return _room(out, 1.6, seed, wet=0.3)


def halo_clunk(seed):
    r = K.rng(seed)
    x = K.modal(r.uniform(70, 120), (1.0, 2.76, 5.4, 8.9), (1.0, 0.6, 0.35, 0.2), (1.4, 0.9, 0.5, 0.3), K.ns(2.0), seed=seed)
    return K.lowpass(_room(_norm(x), 2.2, seed, wet=0.55), 3000, 2)


def halo_chirp(seed):
    r = K.rng(seed)
    n = K.ns(0.9)
    out = np.zeros(n)
    t0 = 0.0
    for k in range(int(r.integers(2, 5))):
        f = _hz(PENTA[int(r.integers(len(PENTA)))] + "6")
        m = K.ns(0.07)
        tone = K.fm(f, 2.0, 1.2, m, amp_env=K.perc(m, 0.002, 0.05), seed=seed + k)
        s = K.ns(t0)
        out[s:s + m] += tone[: n - s]
        t0 += r.uniform(0.08, 0.14)
    return _room(out, 1.2, seed, wet=0.35)


def dream_swell(seed):
    """Two glass notes played backwards (their decay becomes a swell that stops on the strike), with a little of the
    note heard forwards after it: no reverb tail, so no noise in a dream."""
    g = _glass_notes(seed, 5, 2, 2.2, 0.3, gap=(0.02, 0.06))[: K.ns(3.2)]
    g = g * np.exp(-np.arange(len(g)) / K.ns(1.4))
    rev = g[::-1]
    tail = 0.25 * g[: K.ns(0.6)] * np.linspace(1, 0, K.ns(0.6))
    return _norm(np.concatenate([rev, tail]))

def elder_creak(seed):
    r = K.rng(seed)
    L = r.uniform(1.0, 1.8)
    n = K.ns(L)
    rate = 26 + 22 * K.smooth_noise(n, 1.5, seed + 1)
    phase = np.cumsum(rate) / K.SR
    stick = (np.diff(np.floor(phase), prepend=0) > 0).astype(float)  # stick-slip: a grain each cycle
    grains = K.lowpass(stick, 900, 2)
    body = K.bandpass(grains, r.uniform(160, 260), 3.0)
    return _room(_norm(body) * K.swell(n, 0.4, 1.5), 2.0, seed, wet=0.35)


def elder_glint(seed):
    return _room(_glass_notes(seed, 6, 2, 0.8, 0.2, gap=(0.05, 0.12)), 2.0, seed, wet=0.55)


def tell_rumble(seed):
    """Ten animals galloping: each a three-beat gallop (two quick hooves and a heavier one) at its own pace and phase,
    hooves a low thud with a little dirt on it, so together they roll into one steady thunder; a ground rumble under
    it. Distant: no click survives the low-pass."""
    L = 11.0  # 10 s
    n = K.ns(L)
    r = K.rng(seed)
    hooves = np.zeros(n)
    m = K.ns(0.09)
    tt = np.arange(m) / K.SR
    for animal in range(10):
        stride = r.uniform(0.36, 0.44)  # seconds per stride
        t0 = r.uniform(0, stride)
        heavy = r.uniform(0.7, 1.0)
        while t0 < L:
            for off, g in ((0.0, 0.55), (0.07, 0.6), (0.16, 1.0)):
                h = K.ns(t0 + off + r.normal(0, 0.006))
                if 0 <= h < n - m:
                    f = r.uniform(55, 85)
                    thud = np.sin(2 * np.pi * f * tt * (1 - 0.6 * tt / tt[-1])) * np.exp(-tt / 0.025)
                    dirt = r.standard_normal(m) * np.exp(-tt / 0.012) * 0.25
                    hooves[h:h + m] += (thud + dirt) * g * heavy
            t0 += stride * r.uniform(0.97, 1.03)
    hooves = K.lowpass(hooves, 450, 2)
    rumble = _norm(K.lowpass(K.noise(n, "brown", seed + 1), 70, 2)) * 0.6
    return _loop(0.75 * _norm(hooves) + 0.25 * rumble, 1.0)

def tell_wings(seed):
    L = 11.0  # 10 s
    n = K.ns(L)
    t = K.tvec(n)
    turns = 0.25 + 0.75 * (0.5 + 0.5 * np.sin(2 * np.pi * t / 2.5 + 2 * K.smooth_noise(n, 0.3, seed))) ** 2
    flap = 0.7 + 0.3 * np.sin(2 * np.pi * 9 * t + 3 * K.smooth_noise(n, 2.0, seed + 1))
    return _loop(_norm(K.bandpass(K.noise(n, "pink", seed + 2), 900, 0.6)) * turns * flap, 1.0)


def tell_horn(seed):
    n = K.ns(3.6)
    f = _hz("D3") * 2 ** (np.clip(K.tvec(n) - 1.6, 0, 0.6) / 0.6 * 7 / 12)  # D rising to A
    amps = K.saw_amps(400.0, bright=0.35, tilt=1.3)
    horn = K.wt_voice(f, n, amps) * K.adsr(n, 0.35, 0.3, 0.8, 0.9)
    return K.lowpass(_room(_norm(horn), 3.5, seed, wet=0.6, bright=0.3), 2400, 2)


def tell_tide(seed):
    L = 13.0  # 12 s
    n = K.ns(L)
    t = K.tvec(n)
    f = _hz("D1")
    drone = np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * (f + 1 / 12.0) * t) + 0.3 * np.sin(2 * np.pi * 2 * f * t)
    wash = _norm(K.lowpass(K.noise(n, "pink", seed + 1), 400, 2)) * _swells(n, seed + 2, 0.15, 0.4)
    return _loop(0.6 * _norm(drone) + 0.4 * wash, 1.0)


def tell_storm(seed):
    """The Rolling Storm heard from afar: its rain a broad roar (the hiss of a curtain of drops a few hundred blocks off,
    its highs gone with the distance), the wind ahead of it gusting under that, and a low growl that never stops."""
    L = 13.0  # 12 s
    n = K.ns(L)
    roar = _norm(K.lowpass(K.bandpass(K.noise(n, "pink", seed), 1100, 0.5), 2600, 2)) * _swells(n, seed + 1, 0.12, 0.65, 1.2)
    wind = _norm(K.lowpass(K.noise(n, "brown", seed + 2), 320, 2)) * _swells(n, seed + 3, 0.22, 0.25, 1.8)
    growl = _norm(K.lowpass(K.noise(n, "brown", seed + 4), 60, 2)) * _swells(n, seed + 5, 0.08, 0.5)
    return _loop(0.55 * roar + 0.3 * wind + 0.15 * growl, 1.0)


def storm_roll(seed):
    """Far thunder: no crack, only the roll: the channel's near end arrives first and its far reaches keep coming for
    seconds, so a soft onset, then a run of uneven swells that tail off; low, the highs gone with the distance."""
    L = 8.5
    n = K.ns(L)
    t = K.tvec(n)
    r = K.rng(seed)
    env = np.zeros(n)
    at = r.uniform(0.15, 0.35)
    for k in range(r.integers(6, 10)):
        g = (0.85 ** k) * r.uniform(0.55, 1.0)
        rise, fall = r.uniform(0.05, 0.18), r.uniform(0.5, 1.4)
        x = np.clip(t - at, 0, None)
        env += g * (1 - np.exp(-x / rise)) * np.exp(-x / fall) * (t >= at)
        at += r.uniform(0.25, 0.9)
    env = env / (np.max(env) + 1e-12)
    body = _norm(K.lowpass(K.noise(n, "brown", seed + 1), 160, 2))
    grit = _norm(K.lowpass(K.bandpass(K.noise(n, "pink", seed + 2), 320, 0.7), 700, 2)) * 0.35
    x = (body + grit) * env * np.clip(t / 0.08, 0, 1) * np.clip((L - t) / 0.6, 0, 1)
    return _room(_norm(x), 2.5, seed, wet=0.35, bright=0.2)


def tell_bells(seed):
    """Pack-animal bells on a caravan: three bells of different sizes (hammered metal: clashing, inharmonic overtones,
    not one clean note), each swinging with its animal's walk so its clapper strikes twice a stride, unevenly, plus a
    soft rattle of the clapper against the rim between strikes."""
    L = 10.0  # 9 s
    n = K.ns(L)
    r = K.rng(seed)
    out = np.zeros(n)
    ratios = (1.0, 1.59, 2.14, 2.83, 3.51, 4.62)
    for b, (f0, size) in enumerate(((620.0, 1.0), (860.0, 0.8), (1180.0, 0.6))):
        stride = r.uniform(0.85, 1.05)  # a walking pace
        t0 = r.uniform(0, stride)
        while t0 < L - 0.2:
            for half in (0.0, 0.5):
                at = t0 + stride * half + r.normal(0, 0.03)
                h = K.ns(at)
                if not 0 <= h < n:
                    continue
                m = min(K.ns(1.2), n - h)
                f = f0 * 2 ** (r.normal(0, 8) / 1200)
                amps = np.array([1.0, 0.7, 0.55, 0.4, 0.3, 0.2]) * r.uniform(0.6, 1.0, 6)
                t60s = [0.9 * size, 0.6 * size, 0.5 * size, 0.35 * size, 0.25 * size, 0.18 * size]
                ring = K.modal(f, ratios, amps, t60s, m, attack=0.001, seed=seed + b * 1000 + h % 997, detune_cents=7, pair=0.5)
                clank = K.bandnoise(min(m, K.ns(0.02)), 2600, 1.5, seed + h % 991) * K.perc(min(m, K.ns(0.02)), 0.0005, 0.012)
                ring[:len(clank)] += 0.3 * clank
                out[h:h + m] += ring * r.uniform(0.5, 1.0) * size
            t0 += stride * r.uniform(0.95, 1.05)
    rattle = K.crackle(n, rate=9.0, seed=seed + 5, bright=0.5, burstiness=0.5) * 0.05
    return _loop(K.lowpass(_norm(out) + rattle, 7000, 2), 1.0)

def _variants(name, fn, count, seed, rms=-27.0):
    return [Snd_(f"{name}_{k + 1}", (lambda f: (lambda s: f(s)))(fn), rms=rms, seed=seed + k, mono=True) for k in range(count)]


Snd_ = None  # set by sounds(): the build's file record


def sounds(Snd):
    global Snd_
    Snd_ = Snd
    out = [
        Snd("amb_world_greenwood_a", greenwood_hush, rms=-29.0, seed=301, mono=True),
        Snd("amb_world_greenwood_b", greenwood_far, rms=-29.0, seed=302, mono=True),
        Snd("amb_world_greenwood_c", greenwood_air, rms=-30.0, seed=303, mono=True),
        Snd("amb_world_stillpoint_sea", stillpoint_sea, rms=-29.0, seed=311),
        Snd("amb_world_stillpoint_tone", stillpoint_tone, rms=-30.0, seed=312, mono=True),
        Snd("amb_world_halo_station", halo_station, rms=-29.0, seed=321, mono=True),
        Snd("amb_world_halo_deep", halo_deep, rms=-30.0, seed=322, mono=True),
        Snd("amb_world_dream_shimmer", dream_shimmer, rms=-29.0, seed=331),
        Snd("amb_world_dream_air", dream_air, rms=-30.0, seed=332, mono=True),
        Snd("amb_world_veil_choir", veil_choir, rms=-29.0, seed=341, mono=True),
        Snd("amb_world_elder_groan", elder_groan, rms=-29.0, seed=351, mono=True),
        Snd("amb_world_lumen_hum", lumen_hum, rms=-30.0, seed=361, mono=True),
        Snd("amb_tell_stampede", tell_rumble, rms=-27.0, seed=401, mono=True),
        Snd("amb_tell_wings", tell_wings, rms=-28.0, seed=402, mono=True),
        Snd("amb_tell_tide", tell_tide, rms=-28.0, seed=404, mono=True),
        Snd("amb_tell_bells", tell_bells, rms=-28.0, seed=405, mono=True),
        Snd("amb_tell_storm", tell_storm, rms=-28.0, seed=406, mono=True),
    ]
    for name, fn, count, seed in (("amb_world_chime", chime, 6, 500), ("amb_world_bell_far", bell_far, 2, 510),
                                  ("amb_world_halo_hiss", halo_hiss, 3, 520), ("amb_world_halo_clunk", halo_clunk, 3, 530),
                                  ("amb_world_halo_chirp", halo_chirp, 4, 540), ("amb_world_dream_swell", dream_swell, 4, 550),
                                  ("amb_world_elder_creak", elder_creak, 4, 560), ("amb_world_elder_glint", elder_glint, 4, 570),
                                  ("amb_world_lumen_chime", lumen_chime, 6, 580), ("amb_tell_horn", tell_horn, 2, 590),
                                  ("amb_storm_roll", storm_roll, 3, 600)):
        out += _variants(name, fn, count, seed)
    return out


# the one-shots' events: (event, subtitle, file stem, variants, merge folder)
ONE_SHOTS = (
    ("world.chime", "Chimes stir in the wind", "amb_world_chime", 6, "audio"),
    ("world.bell_far", "A far bell tolls", "amb_world_bell_far", 2, "audio"),
    ("world.halo_hiss", "An airlock hisses", "amb_world_halo_hiss", 3, "audio"),
    ("world.halo_clunk", "Metal knocks somewhere in the station", "amb_world_halo_clunk", 3, "audio"),
    ("world.halo_chirp", "Data chirps", "amb_world_halo_chirp", 4, "audio"),
    ("world.dream_swell", "Glass swells in reverse", "amb_world_dream_swell", 4, "audio"),
    ("world.elder_creak", "A great limb creaks", "amb_world_elder_creak", 4, "audio"),
    ("world.elder_glint", "Gold light glints", "amb_world_elder_glint", 4, "audio"),
    ("world.lumen_chime", "Lumen specks chime", "amb_world_lumen_chime", 6, "audio"),
    ("living.tell.horn", "A hunting horn, far off", "amb_tell_horn", 2, "living"),
    ("living.storm.roll", "Thunder rolls in the distance", "amb_storm_roll", 3, "living"),
)
LOOPS = (
    ("living.tell.stampede", "A stampede rumbles", "amb_tell_stampede"),
    ("living.tell.wings", "A great flock turns", "amb_tell_wings"),
    ("living.tell.tide", "The Deep Tide rolls in", "amb_tell_tide"),
    ("living.tell.bells", "Caravan bells", "amb_tell_bells"),
    ("living.tell.storm", "A storm's rain roars", "amb_tell_storm"),
)


def events(Evt):
    out = [Evt("amb.world", "The world around you", files=["amb_world_greenwood_a"], stream=True)]
    for ev, sub, stem, count, folder in ONE_SHOTS:
        out.append(Evt(ev, sub, files=[f"{stem}_{k + 1}" for k in range(count)], folder=folder))
    for ev, sub, stem in LOOPS:
        out.append(Evt(ev, sub, files=[stem], folder="living"))
    return out
