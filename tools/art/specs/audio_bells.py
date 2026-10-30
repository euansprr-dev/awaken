"""The hundred bells (SPEC §43.7, §43.16; JOURNEY part 4): the home's song, the handbell and a bell's toll
(tools/art/audio_build.py renders them). Every one is mono: each is played in the world from its bell, so it has a
direction and fades with distance (OpenAL only places mono sources).

  bell_note_0..14  the tower's fifteen tuned bells, D major pentatonic from D3 to B5: each note of the song is one of these
  bell_gap         a gap in the song: a dull knock of wood where a bell should be
  bell_handbell    the band's handbell: a bright little bell shaken twice
  bell_toll        a bell waking: one great low strike that rolls out over the land
  bell_hum         a hidden bell's hum under the world's sound: low, beating, with no strike at all
  bell_wake        the place waking round it: a warm rising shimmer
"""
import numpy as np

import audiokit as K

NOTES = ("D3", "E3", "F#3", "A3", "B3", "D4", "E4", "F#4", "A4", "B4", "D5", "E5", "F#5", "A5", "B5")


def _norm(x):
    return x / (np.max(np.abs(x)) + 1e-12)


def _place(out, x, at):
    s = K.ns(at)
    e = min(len(out), s + len(x))
    out[s:e] += x[: e - s]


def note(k):
    def render(seed):
        L = 3.4
        n = K.ns(L)
        f = K.hz(K.midi(NOTES[k]))
        # lower bells ring longer and darker; the top ones are brighter and shorter
        t60 = 3.2 - k * 0.1
        x = _norm(K.bell(f, n, t60=t60, bright=0.35 + k * 0.015, seed=seed))
        wet = K.convolve(x, K.reverb_ir(t60=2.6, predelay=0.04, bright=0.4, width=1.0, seed=seed + 50))[:n]
        return K.declick(K.stereo(x) + wet * 0.45)
    return render


def gap(seed):
    L = 0.5
    n = K.ns(L)
    knock = _norm(K.lowpass(K.noise(K.ns(0.06), "white", seed), 900, 2)) * K.perc(K.ns(0.06), 0.001, 0.03)
    thump = K.sub_boom(K.ns(0.3), f0=110.0, f1=70.0, t60=0.15, seed=seed + 1) * 0.5
    x = np.zeros(n)
    _place(x, knock * 0.7, 0.0)
    _place(x, thump, 0.0)
    return K.declick(K.stereo(x))


def handbell(seed):
    L = 2.2
    n = K.ns(L)
    x = np.zeros(n)
    for k, (at, v) in enumerate(((0.0, 0.8), (0.24, 0.6))):
        _place(x, K.bell(K.hz(K.midi("A5")), K.ns(L - at), t60=1.4, bright=0.6, seed=seed + k) * v, at)
    wet = K.convolve(x, K.reverb_ir(t60=1.2, predelay=0.01, bright=0.6, width=0.8, seed=seed + 9))[:n]
    return K.declick(K.stereo(_norm(x)) + wet * 0.3)


def toll(seed):
    L = 7.0
    n = K.ns(L)
    x = _norm(K.bell(K.hz(K.midi("D2")), n, t60=6.0, bright=0.4, seed=seed))
    x += K.sub_boom(n, f0=60.0, f1=48.0, t60=2.5, seed=seed + 1) * 0.35
    wet = K.convolve(x, K.reverb_ir(t60=5.0, predelay=0.08, bright=0.35, width=1.0, seed=seed + 2))[:n]
    return K.declick(K.stereo(_norm(x)) + wet * 0.6)


def hum(seed):
    L = 4.0
    n = K.ns(L)
    t = np.arange(n) / K.SR
    f = K.hz(K.midi("D3"))
    # two partials a hair apart, beating slowly, and a breath of low air under them
    x = np.sin(2 * np.pi * f * t) + 0.7 * np.sin(2 * np.pi * (f * 1.004) * t) + 0.25 * np.sin(2 * np.pi * f * 2.01 * t)
    x += _norm(K.lowpass(K.noise(n, "pink", seed), 300, 2)) * 0.25
    env = K.line(n, [(0, 0), (0.35, 1), (0.7, 1), (1.0, 0)])
    return K.declick(K.stereo(_norm(x) * env))


def wake(seed):
    L = 4.0
    n = K.ns(L)
    x = np.zeros(n)
    for k, m in enumerate(("D5", "F#5", "A5", "D6", "E6", "A6")):
        _place(x, K.glass(K.hz(K.midi(m)), K.ns(2.4), bright=0.5, t60=1.6, seed=seed + k, hit=0.4) * (0.5 - k * 0.04), 0.15 + k * 0.22)
    air = _norm(K.bandpass(K.noise(n, "pink", seed + 20), 3000, 0.7)) * K.line(n, [(0, 0), (0.5, 0.4), (1.0, 0)])
    st = K.stereo(x) + K.stereo(air) * 0.25
    wet = K.convolve(K.mono(st), K.reverb_ir(t60=2.8, predelay=0.03, bright=0.55, width=1.0, seed=seed + 30))[:n]
    return K.declick(st + wet * 0.5)


def sounds(Snd):
    out = [Snd("bell_note_%d" % k, note(k), rms=-21.0, seed=200 + k, mono=True) for k in range(len(NOTES))]
    out += [
        Snd("bell_gap", gap, rms=-24.0, seed=220, mono=True),
        Snd("bell_handbell", handbell, rms=-22.0, seed=221, mono=True),
        Snd("bell_toll", toll, rms=-19.0, seed=222, mono=True),
        Snd("bell_hum", hum, rms=-24.0, seed=223, mono=True),
        Snd("bell_wake", wake, rms=-23.0, seed=224, mono=True),
    ]
    return out


def events(Evt):
    out = [Evt("bells.note_%d" % k, "A bell rings", files=["bell_note_%d" % k], folder="bells") for k in range(len(NOTES))]
    out += [
        Evt("bells.gap", "A dull knock", files=["bell_gap"], folder="bells"),
        Evt("bells.handbell", "Your handbell rings", files=["bell_handbell"], folder="bells"),
        Evt("bells.toll", "A great bell tolls", files=["bell_toll"], folder="bells"),
        Evt("bells.hum", "A hidden bell hums", files=["bell_hum"], folder="bells"),
        Evt("bells.wake", "The place wakes", files=["bell_wake"], folder="bells"),
    ]
    return out


def build(out):
    """tools/art/build_all.py builds textures; audio is rendered by tools/art/audio_build.py."""
