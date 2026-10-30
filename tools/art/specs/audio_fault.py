"""Faults (SPEC §43.8; JOURNEY part 5): the System's voice breaking up (tools/art/audio_build.py renders them).

  ui_fault_tell     the world's sound sucked out (a reversed breath closing to nothing) and one bell note pitched wrong,
                    its partials beating against each other
  ui_fault_tear     digital tearing: stuttered, bit-crushed bursts that slide in pitch, laced with a reversed bell
                    swelling into the cut
  ui_fault_voice    the System's voice as broken glass tones: three chopped glass notes, one a line typed
  ui_fault_drop     the sub drop on the cut to black: a click and a falling low boom
  ui_fault_mend     the mend: one clean sweep of air and a clear chord (D, F#, A, D), unhurried
  ui_fault_flicker  a flicker: a short crackle and a glass blink
"""
import numpy as np

import audiokit as K


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _env(n, length, points):
    return K.line(n, [(t / length, v) for t, v in points])


def _place(out, x, at):
    s = K.ns(at)
    e = min(len(out), s + len(x))
    out[s:e] += x[: e - s]


def _crush(x, bits, hold):
    """Bit depth and sample-rate reduction: the digital break."""
    q = 2 ** bits
    y = np.round(x * q) / q
    idx = (np.arange(len(y)) // hold) * hold
    return y[np.minimum(idx, len(y) - 1)]


def _wrong_bell(f0, n, seed):
    """A bell note pitched wrong: its fundamental flattened, a partial a quarter-tone off, beating."""
    a = K.bell(f0 * 2 ** (-0.55 / 12), n, t60=2.4, bright=0.45, seed=seed)
    b = K.bell(f0 * 2 ** (0.5 / 12) * 2.0, n, t60=1.6, bright=0.3, seed=seed + 1) * 0.35
    return _norm(a + b)


def tell(seed):
    L = 1.6
    n = K.ns(L)
    x = np.zeros(n)
    # the world's breath drawn out of it: a pink swell that closes to nothing, reversed
    breath = _norm(K.lowpass(K.noise(K.ns(0.7), "pink", seed), 1800, 2)) * _env(K.ns(0.7), 0.7, [(0, 0), (0.6, 1), (0.7, 0)])
    _place(x, breath[::-1] * 0.45, 0.0)
    # then nothing, and one bell note pitched wrong
    _place(x, _wrong_bell(K.hz(K.midi("A3")), K.ns(L - 0.42), seed + 2) * 0.7, 0.42)
    wet = K.convolve(x, K.reverb_ir(t60=2.2, predelay=0.03, bright=0.35, width=0.9, seed=seed + 3))[:n]
    return K.declick(K.stereo(x) + wet * 0.5)


def tear(seed):
    L = 2.6
    n = K.ns(L)
    r = K.rng(seed)
    x = np.zeros(n)
    t = 0.05
    # stuttered bursts: crushed noise and a sliding buzz, each chopped and repeated
    while t < L - 0.5:
        m = K.ns(r.uniform(0.04, 0.16))
        burst = _norm(K.bandpass(K.noise(m, "white", int(r.integers(1 << 30))), r.uniform(600, 4200), 1.4))
        f0, f1 = r.uniform(90, 400), r.uniform(60, 900)
        buzz = np.sign(np.sin(2 * np.pi * np.cumsum(K.glide(m, f0, f1)) / K.SR)) * 0.6
        g = _crush(burst * 0.7 + buzz * 0.4, int(r.integers(3, 6)), int(r.integers(4, 40)))
        g *= K.perc(m, 0.002, 0.2)
        for k in range(int(r.integers(1, 4))):
            _place(x, g * r.uniform(0.35, 0.8), t + k * len(g) / K.SR)
        t += r.uniform(0.05, 0.22)
    # a reversed bell swelling into the cut
    rb = K.bell(K.hz(K.midi("D3")), K.ns(1.8), t60=3.0, bright=0.5, seed=seed + 7)[::-1]
    _place(x, _norm(rb) * 0.55, L - 1.8)
    # a hair of delay on the right: the tear sits wide
    return K.declick(np.stack([x, np.roll(x, K.ns(0.011))], axis=1))


def voice(seed):
    L = 0.9
    n = K.ns(L)
    r = K.rng(seed)
    x = np.zeros(n)
    for k, m in enumerate(("E6", "B5", "F6")):
        note = K.glass(K.hz(K.midi(m)) * 2 ** (r.uniform(-0.4, 0.4) / 12), K.ns(0.5), bright=0.55, t60=0.4, seed=seed + k, hit=0.7)
        chop = np.ones(len(note))
        for _ in range(3):
            s = int(r.integers(0, len(note) - 400))
            chop[s:s + int(r.integers(120, 400))] = 0.0
        _place(x, note * chop * 0.5, k * 0.07)
    wet = K.convolve(x, K.reverb_ir(t60=0.9, predelay=0.01, bright=0.6, width=0.8, seed=seed + 9))[:n]
    return K.declick(K.stereo(x) + wet * 0.35)


def drop(seed):
    L = 2.2
    n = K.ns(L)
    x = np.zeros(n)
    click = _norm(K.highpass(K.noise(K.ns(0.02), "white", seed), 2000, 2)) * K.perc(K.ns(0.02), 0.0005, 0.01)
    _place(x, click * 0.4, 0.0)
    x += K.sub_boom(n, f0=62.0, f1=26.0, t60=1.6, seed=seed + 1) * 0.9
    return K.declick(K.stereo(x))


def mend(seed):
    L = 3.2
    n = K.ns(L)
    x = np.zeros(n)
    # a sweep of clean air, left to right
    air = _norm(K.bandpass(K.noise(K.ns(0.8), "pink", seed), 5200, 0.8)) * _env(K.ns(0.8), 0.8, [(0, 0), (0.35, 0.5), (0.8, 0)])
    sweep = np.zeros((n, 2))
    pan = np.linspace(-1, 1, len(air))
    sweep[: len(air), 0] = air * np.sqrt((1 - pan) / 2)
    sweep[: len(air), 1] = air * np.sqrt((1 + pan) / 2)
    # the clear chord, each note a breath after the last
    for k, (m, v) in enumerate((("D4", 0.5), ("F#4", 0.42), ("A4", 0.4), ("D5", 0.34))):
        _place(x, K.bell(K.hz(K.midi(m)), K.ns(L - 0.3 - k * 0.06), t60=2.8, bright=0.4, seed=seed + 10 + k) * v, 0.3 + k * 0.06)
    st = K.stereo(x) + sweep * 0.35
    wet = K.convolve(K.mono(st), K.reverb_ir(t60=2.4, predelay=0.02, bright=0.5, width=1.0, seed=seed + 20))[:n]
    return K.declick(st + wet * 0.45)


def flicker(seed):
    L = 0.45
    n = K.ns(L)
    x = K.crackle(n, rate=60.0, seed=seed, bright=0.8) * _env(n, L, [(0, 0), (0.02, 0.6), (0.2, 0.3), (L, 0)])
    _place(x, K.glass(K.hz(K.midi("C7")), K.ns(0.3), bright=0.6, t60=0.2, seed=seed + 1, hit=0.6) * 0.3, 0.05)
    return K.declick(K.stereo(x))


def sounds(Snd):
    return [
        Snd("ui_fault_tell", tell, rms=-22.0, seed=91),
        Snd("ui_fault_tear", tear, rms=-21.0, seed=92),
        Snd("ui_fault_voice", voice, rms=-25.0, seed=93),
        Snd("ui_fault_drop", drop, rms=-22.0, seed=94),
        Snd("ui_fault_mend", mend, rms=-23.0, seed=95),
        Snd("ui_fault_flicker", flicker, rms=-26.0, seed=96),
    ]


def events(Evt):
    return [
        Evt("fault.tell", "The world goes quiet", files=["ui_fault_tell"], folder="fault"),
        Evt("fault.tear", "The System breaks up", files=["ui_fault_tear"], folder="fault"),
        Evt("fault.voice", "The System's broken voice", files=["ui_fault_voice"], folder="fault"),
        Evt("fault.drop", "Everything cuts to black", files=["ui_fault_drop"], folder="fault"),
        Evt("fault.mend", "The System mends", files=["ui_fault_mend"], folder="fault"),
        Evt("fault.flicker", "The System flickers", files=["ui_fault_flicker"], folder="fault"),
    ]


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
