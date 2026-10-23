"""Ambience beds (tools/art/audio_build.py renders them). All mono, so OpenAL can place them in the world; the loops
crossfade their own tail into their head, so they loop without a seam. The client loops them at their source
(core.audio.client.Ambience). The gate hum is the gates module's own and is not redone here.
"""
import numpy as np

import audiokit as K


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _grain(f, dur, attack, seed, wobble=0.003):
    n = K.ns(dur)
    vib = 1 + wobble * np.sin(2 * np.pi * K.rng(seed).uniform(3, 7) * K.tvec(n))
    return K.sine(f * vib, n) * K.perc(n, attack, dur * 0.8)


def sweep_band(x, centre, q):
    """Band-pass with a moving centre (centre: per-sample Hz)."""
    n = len(x)

    def resp(t, f):
        c = centre[min(n - 1, max(0, int(t * K.SR)))]
        return K.bp_resp(c, q)(f)
    return K.stft_filter(x, resp)


# ---- rift: crackle over a tearing drone ----------------------------------------------------------------------------

def rift(seed):
    L = 9.0
    n = K.ns(L)
    crack = K.crackle(n, rate=24, seed=seed + 1, bright=0.6, burstiness=0.8)
    d = K.drone(55.0, n, partials=(1, 2, 3, 4, 5, 7, 9), amps=(0.35, 0.6, 0.5, 0.35, 0.22, 0.12, 0.06), beat=0.35, seed=seed + 2)
    d *= 1 + 0.35 * K.smooth_noise(n, 9, seed + 3)  # the tear: a rough, uneven drone
    d = _norm(d)
    cen = 650 * 2 ** (1.0 * K.smooth_noise(n, 0.5, seed + 5))
    tear = _norm(sweep_band(K.noise(n, "pink", seed + 4), cen, 2.2)) * (0.6 + 0.4 * K.smooth_noise(n, 1.3, seed + 6))
    t = K.tvec(n)
    whine = K.sine(2637 * (1 + 0.004 * np.sin(2 * np.pi * 0.7 * t)), n) * (0.5 + 0.5 * K.smooth_noise(n, 0.4, seed + 7)) ** 2
    x = 0.36 * d + 0.42 * crack + 0.26 * tear + 0.025 * whine
    return K.loop_seamless(K.lowpass(x, 9000, 2), 1.0)


# ---- shimmer grove: glitter, breath, a held fifth ----------------------------------------------------------------

def grove(seed):
    L = 11.0
    n = K.ns(L)
    r = K.rng(seed)
    pent = [K.hz(K.midi(m)) for m in ("D6", "E6", "F#6", "A6", "B6", "D7", "E7", "F#7", "A7")]
    glitter = np.zeros(n)
    for k in range(80):
        dur = r.uniform(0.3, 1.1)
        t0 = r.uniform(0, L - dur)
        f = pent[int(r.integers(len(pent)))] * 2 ** (r.normal(0, 3) / 1200)
        g = _grain(f, dur, r.uniform(0.02, 0.12), seed + 10 + k) * r.uniform(0.15, 1.0) ** 2
        s = K.ns(t0)
        glitter[s:s + len(g)] += g[: n - s]
    breath = K.highpass(K.bandpass(K.noise(n, "pink", seed + 2), 4500, 0.9), 1500, 2)
    breath = _norm(breath) * (0.55 + 0.45 * K.smooth_noise(n, 0.3, seed + 3))
    t = K.tvec(n)
    tone = (np.sin(2 * np.pi * K.hz(K.midi("D6")) * t) + 0.7 * np.sin(2 * np.pi * K.hz(K.midi("A6")) * t + 1.0))
    tone *= 0.5 + 0.5 * K.smooth_noise(n, 0.25, seed + 4)
    x = 0.55 * _norm(glitter) + 0.22 * breath + 0.06 * tone
    return K.loop_seamless(x, 1.0)


# ---- the fallen star ---------------------------------------------------------------------------------------------

def star_whistle(seed):
    L = 2.6
    n = K.ns(L)
    u = np.arange(n) / n
    f = 2400 * (520 / 2400) ** (u ** 1.4)  # the drop speeds up as it comes in
    vib = 1 + 0.006 * np.sin(2 * np.pi * 9 * K.tvec(n))
    tone = K.sine(f * vib, n) + 0.22 * K.sine(2 * f * vib, n)
    env = (0.12 + 0.88 * u ** 1.3) * K.adsr(n, 0.25, 0.0, 1.0, 0.08)
    rush = _norm(sweep_band(K.noise(n, "white", seed + 1), f * 1.4, 1.4)) * (0.2 + 0.8 * u ** 2)
    rumble = _norm(K.lowpass(K.noise(n, "brown", seed + 2), 180, 2)) * u ** 2.5
    x = 0.45 * tone * env + 0.35 * rush * K.adsr(n, 0.3, 0, 1, 0.08) + 0.4 * rumble * K.adsr(n, 0.5, 0, 1, 0.1)
    return K.declick(x, 0.01, 0.06)


def star_cooling(seed):
    L = 7.0
    n = K.ns(L)
    r = K.rng(seed)
    hiss = K.lowpass(K.highpass(K.noise(n, "white", seed + 1), 2600, 2), 11000, 2)
    hiss = _norm(hiss) * (0.6 + 0.4 * K.smooth_noise(n, 3.0, seed + 2)) * (0.8 + 0.2 * K.smooth_noise(n, 0.3, seed + 3))
    ticks = np.zeros(n)
    for k in range(16):
        f = r.uniform(1800, 5200)
        L2 = K.ns(0.4)
        b = K.bar(f, L2, t60=r.uniform(0.06, 0.22), bright=0.8, seed=seed + 20 + k) * r.uniform(0.2, 1.0)
        s = K.ns(r.uniform(0, L - 0.4))
        ticks[s:s + L2] += b
    creak = np.zeros(n)
    for k in range(2):
        f = r.uniform(230, 380)
        L2 = K.ns(0.9)
        m = K.modal(f, [1, 2.31, 3.9], [1, 0.5, 0.25], [0.45, 0.25, 0.12], L2, seed=seed + 40 + k)
        s = K.ns(r.uniform(0.5, L - 1.2))
        creak[s:s + L2] += m
    sizzle = K.crackle(n, rate=55, seed=seed + 5, bright=0.95, burstiness=0.3)
    x = 0.28 * hiss + 0.32 * _norm(ticks) + 0.2 * _norm(creak) + 0.12 * sizzle
    return K.loop_seamless(x, 1.0)


def star_cooled(seed):
    L = 2.6
    n = K.ns(L)
    ring = K.modal(880, [1, 1.5, 2.756, 4.1], [1, 0.6, 0.3, 0.12], [1.8, 1.4, 0.7, 0.35], n, seed=seed, detune_cents=3, pair=0.5)
    steam = K.bandnoise(n, 4200, 0.8, seed + 1) * K.perc(n, 0.01, 0.9)
    thump = K.sub_boom(n, 70, 45, 0.35, seed + 2)
    x = 0.35 * _norm(ring) + 0.3 * steam / 3 + 0.3 * thump
    return K.declick(x)


# ---- the Well and the ruins ----------------------------------------------------------------------------------------

def well(seed):
    L = 13.5
    n = K.ns(L)
    d = K.drone(36.71, n, partials=(1, 1.5, 2, 3, 4), amps=(0.6, 0.7, 0.8, 0.35, 0.18), beat=0.12, seed=seed)
    # wind through a long shaft: noise through the shaft's resonances (harmonics of ~41 Hz), gusting
    f0 = 41.2

    def shaft(f):
        g = np.zeros_like(f)
        for k in range(1, 24):
            g += (1.0 / k ** 0.6) * np.exp(-0.5 * ((f - k * f0) / (3.0 + 0.4 * k)) ** 2)
        return g
    wind = _norm(K.lowpass(K.fft_filter(K.noise(n, "pink", seed + 1), shaft), 1100, 2))
    wind *= 0.55 + 0.45 * K.smooth_noise(n, 0.15, seed + 2)
    # a far-off groan, once
    gL = K.ns(3.2)
    gf = K.glide(gL, 92, 68)
    groan = K.wt_voice(1.0, gL, K.saw_amps(92, 0.1, 1.3, 40, formants=K.VOWELS["o"], f0=80), vib=gf)
    groan = K.lowpass(groan, 500, 2) * K.swell(gL, 0.4, 1.5)
    gx = np.zeros(n)
    s = K.ns(5.5)
    gx[s:s + gL] = groan
    x = 0.45 * _norm(d) + 0.5 * wind + 0.12 * _norm(gx)
    return K.loop_seamless(K.lowpass(x, 1400, 2), 1.5)


def ruin_night(seed):
    L = 11.0
    n = K.ns(L)
    t = K.tvec(n)
    hum = sum(a * np.sin(2 * np.pi * 50.0 * h * t + h) for h, a in ((1, 0.5), (2, 1.0), (3, 0.55), (4, 0.3), (6, 0.12), (8, 0.05)))
    hum *= 1 + 0.22 * np.sin(2 * np.pi * t / (L - 1.0) * 3)  # breathing (whole cycles over the loop)
    sig = np.sin(2 * np.pi * K.hz(K.midi("A6")) * t)
    gate = np.clip(np.sin(2 * np.pi * t / 5.0) * 1.5 - 0.6, 0, 1) ** 2  # a slow beacon pulse
    sig *= gate
    r = K.rng(seed)
    chirps = np.zeros(n)
    for k, m in enumerate(("A5", "D6", "E6", "D6")):
        cL = K.ns(r.uniform(0.07, 0.14))
        f = float(K.hz(K.midi(m)))
        c = K.fm(f, 2.0, 1.2, cL, index_env=K.perc(cL, 0.002, 0.1), amp_env=K.adsr(cL, 0.005, 0.02, 0.7, 0.03))
        s = K.ns(r.uniform(0.5, L - 1.5))
        chirps[s:s + cL] += c * r.uniform(0.4, 1.0)
    buzz = _norm(K.bandpass(K.noise(n, "white", seed + 3), 200, 3.0)) * 0.5
    x = 0.55 * _norm(hum) + 0.07 * sig + 0.12 * chirps + 0.06 * buzz
    return K.loop_seamless(K.lowpass(x, 6000, 2), 1.0)


def sounds(Snd):
    return [
        Snd("amb_rift", rift, rms=-27.0, seed=11),
        Snd("amb_grove", grove, rms=-29.0, seed=12),
        Snd("amb_star_whistle", star_whistle, rms=-26.0, seed=13),
        Snd("amb_star_cooling", star_cooling, rms=-29.0, seed=14),
        Snd("amb_star_cooled", star_cooled, rms=-27.0, seed=15),
        Snd("amb_well", well, rms=-27.0, seed=16),
        Snd("amb_ruin_night", ruin_night, rms=-29.0, seed=17),
    ]


def events(Evt):
    return [
        Evt("amb.rift", "Rift crackles", files=["amb_rift"], attenuation=32),
        Evt("amb.grove", "The air shimmers", files=["amb_grove"], attenuation=24),
        Evt("amb.star_whistle", "Falling star whistles", files=["amb_star_whistle"], attenuation=96),
        Evt("amb.star_cooling", "Fallen star hisses as it cools", files=["amb_star_cooling"], attenuation=20),
        Evt("amb.star_cooled", "Fallen star settles", files=["amb_star_cooled"], attenuation=24),
        Evt("amb.well", "The Well hums below", files=["amb_well"], attenuation=40),
        Evt("amb.ruin_night", "Old machines hum", files=["amb_ruin_night"], attenuation=32),
    ]


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
