"""World sound (docs/sound/PLAN.md, SPEC §13 "Plan v2 · world sound"): the air beds the Conductor mixes into its one
stream (core.audio.client.AirMixer). Each family's air is up to three mono layers of co-prime length (37, 41 and
53 seconds repeat together only after 22 hours), so the mix never repeats audibly; each layer loops seamlessly on its
own and holds nothing salient (no call, no knock you could notice coming round again): salient sounds are the
Conductor's one-shots. Mastered under the ambience rule; the soundscape files' gains set the balance.

  amb_world_greenwood_a   broadleaf hush: wind moving through a broadleaf canopy at a distance, slow swells
  amb_world_greenwood_b   the far wood: a low, slow roar of the forest beyond, deeper in the gusts
  amb_world_greenwood_c   open air: the faintest high air between the trees
  amb.world               the Conductor's stream event (sounds.json needs a streamed entry; the mixer supplies the audio)
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


def sounds(Snd):
    return [
        Snd("amb_world_greenwood_a", greenwood_hush, rms=-29.0, seed=301, mono=True),
        Snd("amb_world_greenwood_b", greenwood_far, rms=-29.0, seed=302, mono=True),
        Snd("amb_world_greenwood_c", greenwood_air, rms=-30.0, seed=303, mono=True),
    ]


def events(Evt):
    return [
        Evt("amb.world", "The world around you", files=["amb_world_greenwood_a"], stream=True),
    ]
