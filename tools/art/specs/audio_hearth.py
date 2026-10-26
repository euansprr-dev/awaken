"""The carried flame (SPEC §28.2, W1): the Hearth's own sounds (tools/art/audio_build.py renders them).

  sfx_hearth_take   the coal takes in its ring of stones: a soft settling thud and the click of stones, then the
                    world's sound opens into a low hearth tone (a warm drone on D with a slow swell and a faint bell
                    chord), a breath of crackle under it; mono, heard to 32 blocks
  sfx_hearth_fire   the Hearthfire burning: a low roar with wood-fire pops (no electric crackle); mono, short, played
                    now and then by the block itself
  sfx_hearth_lift   the coal lifted out of the ring again: a rising breath, embers ticking, one small bright ping; mono
  ui_hearth_room    a room named (the quiet caption) or your land's name: two warm wooden-bell notes a third apart
"""
import numpy as np

import audiokit as K


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _env(n, length, points):
    """A piecewise-linear envelope through (seconds, value) points."""
    return K.line(n, [(t / length, v) for t, v in points])


def _place(out, x, at):
    s = K.ns(at)
    e = min(len(out), s + len(x))
    out[s:e] += x[: e - s]


def _pops(n, rate, seed, bright=0.45):
    """Wood-fire pops: sparse short broadband snaps, a few bigger ones, softer than electric crackle."""
    r = K.rng(seed)
    out = np.zeros(n)
    t = 0.0
    L = n / K.SR
    while True:
        t += r.exponential(1.0 / rate)
        if t >= L:
            break
        size = r.uniform(0.15, 1.0) if r.random() < 0.85 else r.uniform(1.0, 1.8)
        m = int(r.integers(30, 220 if size < 1 else 600))
        s = K.ns(t)
        m = min(m, n - s)
        if m <= 4:
            continue
        seg = r.standard_normal(m) * np.exp(-np.arange(m) / (m * r.uniform(0.12, 0.3)))
        out[s:s + m] += seg * size
    out = K.highpass(out, 300 + 900 * bright, 1)
    return _norm(K.lowpass(out, 3500 + 6000 * bright, 2))


def _roar(n, seed, fc=380.0):
    """A fire's low breath: pink noise under a lowpass, its strength wandering slowly."""
    x = K.lowpass(K.noise(n, "pink", seed), fc, 2)
    wob = 0.75 + 0.25 * K.smooth_noise(n, 1.6, seed + 1)
    return _norm(x) * wob


def take(seed):
    L = 3.8
    n = K.ns(L)
    x = np.zeros(n)
    # the coal settles into the stones: a soft low thud and a breath of air
    thud = K.sub_boom(K.ns(0.9), f0=78.0, f1=46.0, t60=0.55, seed=seed + 1) * 0.55
    _place(x, thud, 0.0)
    whuff = _norm(K.lowpass(K.noise(K.ns(0.7), "pink", seed + 2), 700, 2)) * K.perc(K.ns(0.7), 0.04, 0.5) * 0.35
    _place(x, whuff, 0.0)
    # stone on stone as it beds in
    r = K.rng(seed + 3)
    for k, at in enumerate((0.06, 0.13, 0.19)):
        click = K.bar(K.hz(K.midi(("G6", "C7", "E6")[k])), K.ns(0.12), t60=0.06, bright=0.7, seed=seed + 10 + k, wood=False)
        _place(x, click * r.uniform(0.08, 0.14), at)
    # the hearth tone: a low warm drone on D with its fifth, swelling in as the warmth runs out over the land
    drone = K.drone(K.hz(K.midi("D2")), n, partials=(1, 2, 3, 4, 6), amps=(1, 0.55, 0.3, 0.16, 0.06), beat=0.12, seed=seed + 20)
    fifth = K.drone(K.hz(K.midi("A2")), n, partials=(1, 2, 3), amps=(1, 0.4, 0.15), beat=0.1, seed=seed + 21)
    tone = K.lowpass(_norm(drone + 0.5 * fifth), 1400, 2)
    x += tone * _env(n, L, [(0, 0), (0.25, 0), (1.2, 0.42), (2.6, 0.3), (L, 0)])
    # a faint bell chord inside it: home
    for at, m, v in ((0.5, "D4", 0.11), (0.62, "A4", 0.08), (0.8, "F#5", 0.06)):
        _place(x, K.bell(K.hz(K.midi(m)), K.ns(L - at), t60=2.6, bright=0.35, seed=seed + int(at * 100)) * v, at)
    # the fire under it all, fading as the tone takes over
    x += _pops(n, 9.0, seed + 30) * _env(n, L, [(0, 0.0), (0.1, 0.16), (1.5, 0.08), (L, 0)])
    x += _roar(n, seed + 31) * _env(n, L, [(0, 0), (0.15, 0.18), (1.2, 0.1), (L, 0)])
    wet = K.convolve(x, K.reverb_ir(t60=1.6, predelay=0.015, bright=0.4, width=0.6, seed=seed + 40))[:n]
    return K.declick(x + K.mono(wet) * 0.35)


def fire(seed):
    L = 2.4
    n = K.ns(L)
    x = _roar(n, seed, fc=320.0) * 0.5 + _pops(n, 14.0, seed + 1) * 0.55
    x *= _env(n, L, [(0, 0), (0.12, 1), (L - 0.4, 1), (L, 0)])
    return K.declick(x)


def lift(seed):
    L = 1.5
    n = K.ns(L)
    # a breath rising: the low band first, then the high, so it seems to lift
    base = _norm(K.noise(n, "pink", seed))
    low, high = _norm(K.bandpass(base, 700, 0.9)), _norm(K.bandpass(base, 2600, 0.9))
    x = low * _env(n, L, [(0, 0), (0.3, 0.4), (0.7, 0.1), (L, 0)]) + high * _env(n, L, [(0, 0), (0.35, 0.05), (0.75, 0.35), (1.1, 0), (L, 0)])
    x += _pops(n, 18.0, seed + 2, bright=0.7) * _env(n, L, [(0, 0.05), (0.4, 0.22), (L, 0)])
    _place(x, K.glass(K.hz(K.midi("A6")), K.ns(0.9), bright=0.5, t60=0.6, seed=seed + 3, hit=0.5) * 0.18, 0.55)
    wet = K.convolve(x, K.reverb_ir(t60=1.0, predelay=0.01, bright=0.5, width=0.6, seed=seed + 4))[:n]
    return K.declick(x + K.mono(wet) * 0.3)


def room(seed):
    L = 1.8
    n = K.ns(L)
    x = np.zeros(n)
    for at, m, v in ((0.0, "A4", 0.55), (0.14, "C#5", 0.45)):
        note = K.bar(K.hz(K.midi(m)), K.ns(L - at), t60=0.9, bright=0.35, seed=seed + int(at * 100), wood=True) * v
        note += K.bell(K.hz(K.midi(m)), K.ns(L - at), t60=1.4, bright=0.25, seed=seed + 7 + int(at * 100)) * v * 0.35
        _place(x, note, at)
    wet = K.convolve(x, K.reverb_ir(t60=1.5, predelay=0.02, bright=0.45, width=0.9, seed=seed + 9))[:n]
    return K.declick(K.stereo(x) + wet * 0.45)


def sounds(Snd):
    return [
        Snd("sfx_hearth_take", take, rms=-21.0, seed=61, mono=True),
        Snd("sfx_hearth_fire", fire, rms=-23.0, seed=62, mono=True),
        Snd("sfx_hearth_lift", lift, rms=-22.0, seed=63, mono=True),
        Snd("ui_hearth_room", room, rms=-26.0, seed=64),
    ]


def events(Evt):
    return [
        Evt("hearth.take", "A coal takes", files=["sfx_hearth_take"], attenuation=32, folder="hearth"),
        Evt("hearth.fire", "Hearthfire crackles", files=["sfx_hearth_fire"], attenuation=12, folder="hearth"),
        Evt("hearth.lift", "A coal is lifted", files=["sfx_hearth_lift"], attenuation=16, folder="hearth"),
        Evt("hearth.room", "The Hearth names a room", files=["ui_hearth_room"], folder="hearth"),
    ]


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
