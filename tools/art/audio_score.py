"""A small score-and-arranger layer over audiokit for the mod's music (tools/art/specs/audio_music.py).

A Score has a tempo, a key and a mode, a stereo mix with reverb sends, and helpers that think in bars and scale
degrees: pads that voice-lead (every chord voiced close to one centre pitch), bass roots, arpeggios, phrases written
as (degree, beats) pairs, sparse colour notes on the beat grid, and atmosphere beds. Instruments are audiokit voices
with musical envelopes; every note renders its own release, so chords cross-fade instead of cutting.

Degrees: an int is a scale degree of the score's mode (0 = tonic, 7 = an octave up in a 7-note mode); a string
"+n"/"-n" is n semitones from the key's root (for chromatic notes); None is a rest.
"""
import numpy as np

import audiokit as K

# ---- instruments: f0 (Hz), dur (s), vel 0..1, seed -> mono array (with its release) ----------------------------------


def _sus(n, a, r, s=0.85, d=0.2):
    return K.adsr(n, a, d, s, r)


def i_piano(f0, dur, vel, seed, felt=0.6, tail=3.5):
    n = K.ns(dur + tail)
    return K.piano(f0, n, vel, dur, seed, felt)


def i_glass(f0, dur, vel, seed, bright=0.5, t60=None, core=0.35):
    t60 = t60 or max(1.4, dur * 1.2)
    n = K.ns(min(dur + t60, t60 * 1.3))
    g = K.glass(f0, n, bright, t60, seed, hit=0.5 * vel)
    c = K.sine(f0, n, 0.25) * K.perc(n, 0.004, t60 * 1.25)
    return (g + core * c) * vel


def i_bell(f0, dur, vel, seed, t60=6.0, bright=0.55):
    n = K.ns(t60 * 1.1)
    return K.bell(f0, n, t60, bright, seed) * vel


def i_fmbell(f0, dur, vel, seed, t60=3.5, index=2.2, ratio=3.5):
    n = K.ns(t60 * 1.1)
    return K.fm_bell(f0, n, t60, index * (0.6 + 0.6 * vel), ratio, seed) * vel


def i_pluck(f0, dur, vel, seed, bright=0.5, metal=0.0, t60=2.2, pos=0.18):
    n = K.ns(t60 * 1.1)
    x = K.pluck(f0, n, bright * (0.7 + 0.5 * vel), t60, pos, seed, metal)
    if dur < t60:  # a damped note: fingers mute it
        k = K.ns(dur)
        damp = np.ones(n)
        damp[k:] = 10 ** (-3 * (np.arange(n - k) / K.SR) / 0.25)
        x = x * damp
    return x * vel


def i_koto(f0, dur, vel, seed, t60=2.6):
    return i_pluck(f0, dur, vel, seed, bright=0.5, metal=0.6, t60=t60, pos=0.14)


def i_marimba(f0, dur, vel, seed, t60=1.1):
    n = K.ns(t60 * 1.3)
    return K.bar(f0, n, t60, 0.5 + 0.4 * vel, seed, wood=True) * vel


def i_vibes(f0, dur, vel, seed, t60=3.0, trem=5.0):
    n = K.ns(t60 * 1.2)
    x = K.bar(f0, n, t60, 0.4 + 0.3 * vel, seed)
    return x * (1 - 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * trem * K.tvec(n)))) * vel


def i_musicbox(f0, dur, vel, seed, detune=0.0, t60=1.8):
    """A music-box tine; `detune` (cents, random sign) makes it sound like it has been left in the damp."""
    r = K.rng(seed)
    f = f0 * 2 ** (detune * r.uniform(-1, 1) / 1200)
    n = K.ns(t60 * 1.2)
    x = K.modal(f, [1.0, 2.0, 5.4, 8.9], [1.0, 0.18, 0.12, 0.05], [t60, t60 * 0.5, t60 * 0.15, t60 * 0.08], n, 0.001, seed)
    if detune:
        wob = 1 + 0.002 * np.sin(2 * np.pi * 0.8 * K.tvec(n) + r.uniform(0, 6))
        x = x * 0.6 + 0.4 * K.sine(f * wob, n) * K.perc(n, 0.001, t60)
    return x * vel


def i_flute(f0, dur, vel, seed, breath=0.22, vib=10):
    n = K.ns(dur + 0.35)
    return K.flute(f0, n, breath, seed, vib) * _sus(n, 0.07, 0.3, 0.85) * vel


def i_harmonica(f0, dur, vel, seed):
    """Glass harmonica: a rubbed glass, slow to speak, trembling."""
    n = K.ns(dur + 0.9)
    t = K.tvec(n)
    x = np.sin(2 * np.pi * f0 * t) + 0.22 * np.sin(2 * np.pi * 2 * f0 * t + 0.5) + 0.06 * np.sin(2 * np.pi * 3 * f0 * t)
    x *= 1 - 0.12 * (0.5 + 0.5 * np.sin(2 * np.pi * 5.5 * t))
    fr = K.bandnoise(n, min(f0 * 4, 12000), 3.0, seed) * 0.015
    return (x + fr) * _sus(n, 0.28, 0.85, 0.9) * vel


def i_pad(f0, dur, vel, seed, bright=0.3, a=1.5, r=2.5, voices=6):
    n = K.ns(dur + r)
    return K.pad(f0, n, voices, seed, bright) * _sus(n, a, r, 0.9) * vel


def i_strings(f0, dur, vel, seed, bright=0.45, a=1.2, r=2.0, trem=0.0):
    """`trem` (Hz): a bowed tremolo, the strings trembling."""
    n = K.ns(dur + r)
    x = K.strings(f0, n, 5, seed, bright) * _sus(n, a, r, 0.9) * vel
    if trem:
        x *= 0.6 + 0.4 * np.abs(np.sin(np.pi * trem * K.tvec(n) + K.rng(seed).uniform(0, 3)))
    return x


def i_choir(f0, dur, vel, seed, vowel="a", a=1.4, r=2.4, bright=0.35):
    n = K.ns(dur + r)
    return K.choir(f0, n, vowel, 6, seed, bright) * _sus(n, a, r, 0.9) * vel


def i_drone(f0, dur, vel, seed, a=3.0, r=4.0):
    n = K.ns(dur + r)
    return K.drone(f0, n, seed=seed) * _sus(n, a, r, 1.0) * vel * 0.5


def i_sub(f0, dur, vel, seed, a=0.8, r=2.0):
    n = K.ns(dur + r)
    return (K.sine(f0, n) + 0.15 * K.sine(2 * f0, n)) * _sus(n, a, r, 1.0) * vel


def i_brass(f0, dur, vel, seed, a=0.12, r=0.5):
    """A soft FM horn: the brass edge grows with the attack."""
    n = K.ns(dur + r)
    v = K.vibrato(n, 5.0, 6, 0.4, seed)
    ie = _sus(n, a * 1.4, r, 0.75) * (1.6 + 1.4 * vel)
    y = np.sin(2 * np.pi * K.phase(f0 * v, n) + ie * np.sin(2 * np.pi * K.phase(f0 * v, n)))
    return K.lowpass(y * _sus(n, a, r, 0.85), 1800 + 1500 * vel, 2) * vel


def i_drum(f0, dur, vel, seed, t60=0.7):
    """A soft low drum (taiko-ish): a falling sine body, a felt skin thud."""
    n = K.ns(t60 * 1.3)
    body = K.sine(K.glide(n, f0 * 1.6, f0), n) * K.perc(n, 0.003, t60)
    skin = K.bandnoise(n, 160 + f0, 1.2, seed) * K.perc(n, 0.001, 0.12)
    return (body + 0.35 * skin) * vel


def i_frame(f0, dur, vel, seed):
    n = K.ns(0.6)
    body = K.sine(K.glide(n, f0 * 1.3, f0), n) * K.perc(n, 0.002, 0.35)
    skin = K.bandnoise(n, 450, 1.0, seed) * K.perc(n, 0.001, 0.08)
    return (body + 0.5 * skin) * vel


def i_shaker(f0, dur, vel, seed):
    n = K.ns(0.12)
    return K.highpass(K.noise(n, "white", seed), 5000, 2) * K.perc(n, 0.008, 0.07) * vel * 0.4


def i_tick(f0, dur, vel, seed, wood=False):
    n = K.ns(0.12)
    x = K.bar(f0, n, 0.04 if not wood else 0.06, 0.8, seed, wood=wood)
    x[:K.ns(0.004)] += K.highpass(K.noise(K.ns(0.004), "white", seed + 1), 3000, 2) * 0.3
    return x * vel


def i_clank(f0, dur, vel, seed):
    """A struck metal beam far away (anvil, girder)."""
    n = K.ns(2.0)
    x = K.modal(f0, [1, 2.76, 5.40, 6.3, 8.93], [1, 0.7, 0.45, 0.3, 0.2], [1.4, 0.9, 0.55, 0.4, 0.3], n, 0.001, seed, 4, 0.5)
    x[:K.ns(0.01)] += K.bandnoise(K.ns(0.01), 3000, 1.0, seed + 1) * 0.4
    return K.lowpass(x, 5000, 1) * vel


def i_drip(f0, dur, vel, seed):
    n = K.ns(0.25)
    return K.sine(K.glide(n, f0 * 1.35, f0), n) * K.perc(n, 0.001, 0.09) * vel


def i_bubble(f0, dur, vel, seed):
    n = K.ns(0.18)
    return K.sine(K.glide(n, f0, f0 * 2.6), n) * K.perc(n, 0.004, 0.11) * vel


def i_chirp(f0, dur, vel, seed):
    n = K.ns(0.16)
    f = K.glide(n, f0, f0 * 1.25)
    return K.sine(f * (1 + 0.02 * np.sin(2 * np.pi * 38 * K.tvec(n))), n) * K.adsr(n, 0.01, 0.03, 0.6, 0.06) * vel


def i_whisper(f0, dur, vel, seed, vowel="u"):
    n = K.ns(dur)
    src = K.noise(n, "white", seed)
    form = K.VOWELS[vowel]

    def r(f):
        g = np.zeros_like(f)
        for fc, bw, gdb in form:
            g += K.dbamp(gdb) * np.exp(-0.5 * ((f - fc * 1.2) / (bw * 1.5)) ** 2)
        return g + 0.05
    x = K.fft_filter(src, r)
    return x / (np.std(x) + 1e-9) * K.swell(n, 0.35, 1.2) * vel * 0.3


def i_fmbass(f0, dur, vel, seed, index=1.6, t60=0.6):
    """A round FM bass for driving ostinatos: the bite (index) falls away fast, the body stays."""
    n = K.ns(dur + 0.25)
    ie = index * (0.35 + 0.65 * K.perc(n, 0.002, t60 * 0.5)) * (0.7 + 0.6 * vel)
    ph = K.phase(f0, n)
    y = np.sin(2 * np.pi * ph + ie * np.sin(2 * np.pi * ph))
    env = K.adsr(n, 0.004, 0.08, 0.75, 0.2) * K.perc(n, 0.002, max(t60 * 2.5, dur + 0.3))
    return K.lowpass(y * env, 900 + 900 * vel, 2) * vel


def i_pulse(f0, dur, vel, seed, t60=0.4, bright=0.3):
    """A soft synth pulse (sine with a little 2nd and 3rd): the Precursor Lab's machines."""
    n = K.ns(min(dur, t60 * 1.5) + 0.05)
    t = K.tvec(n)
    x = np.sin(2 * np.pi * f0 * t) + bright * 0.5 * np.sin(4 * np.pi * f0 * t) + bright * 0.25 * np.sin(6 * np.pi * f0 * t)
    return x * K.perc(n, 0.006, t60) * vel


def i_staccato(f0, dur, vel, seed, bright=0.5):
    """Short bowed strings (spiccato), an ostinato voice."""
    n = K.ns(dur + 0.18)
    return K.strings(f0, n, 4, seed, bright) * K.adsr(n, 0.012, 0.06, 0.65, 0.14) * vel


def i_taiko(f0, dur, vel, seed, t60=1.1):
    """A big low drum: a falling body, the stick's thud and a long low bloom (danger, held back)."""
    n = K.ns(t60 * 1.4)
    body = K.sine(K.glide(n, f0 * 1.9, f0), n) * K.perc(n, 0.002, t60)
    bloom = K.sine(f0 * 0.5, n) * K.perc(n, 0.02, t60 * 1.2) * 0.4
    skin = K.bandnoise(n, 120 + f0 * 1.5, 1.0, seed) * K.perc(n, 0.001, 0.09)
    return (body + bloom + 0.45 * skin) * vel


INSTR = {
    "piano": i_piano, "glass": i_glass, "bell": i_bell, "fmbell": i_fmbell, "pluck": i_pluck, "koto": i_koto,
    "marimba": i_marimba, "vibes": i_vibes, "musicbox": i_musicbox, "flute": i_flute, "harmonica": i_harmonica,
    "pad": i_pad, "strings": i_strings, "choir": i_choir, "drone": i_drone, "sub": i_sub, "brass": i_brass,
    "drum": i_drum, "frame": i_frame, "shaker": i_shaker, "tick": i_tick, "clank": i_clank, "drip": i_drip,
    "bubble": i_bubble, "chirp": i_chirp, "whisper": i_whisper, "fmbass": i_fmbass, "pulse": i_pulse,
    "staccato": i_staccato, "taiko": i_taiko,
}

_IRS = {}
_PARAMS = {}


def _params(fn):
    """The keyword options an instrument takes (a phrase may pass options meant for another voice)."""
    if fn not in _PARAMS:
        import inspect
        _PARAMS[fn] = set(inspect.signature(fn).parameters)
    return _PARAMS[fn]


def ir(kind):
    """Shared rooms: 'room' (1.2 s), 'hall' (3.2 s), 'cathedral' (5.5 s), 'cave' (4 s, dark), 'air' (2.5 s, bright)."""
    if kind not in _IRS:
        p = {"room": dict(t60=1.2, predelay=0.012, bright=0.55, early=0.6, size=0.6, seed=71),
             "hall": dict(t60=3.2, predelay=0.03, bright=0.5, early=0.45, size=1.0, seed=72),
             "cathedral": dict(t60=5.5, predelay=0.045, bright=0.4, early=0.35, size=1.6, seed=73, low_mult=1.35),
             "cave": dict(t60=4.0, predelay=0.035, bright=0.25, early=0.6, size=1.3, seed=74, low_mult=1.4),
             "air": dict(t60=2.6, predelay=0.02, bright=0.8, early=0.2, size=0.9, seed=75)}[kind]
        _IRS[kind] = K.reverb_ir(**p)
    return _IRS[kind]


class Score:
    def __init__(self, bpm, bars, key="D3", mode="aeolian", beats=4, tail=7.0, seed=0, lead_in=0.0):
        self.spb = 60.0 / bpm
        self.beats = beats
        self.key = K.midi(key)
        self.mode = mode
        self.lead = lead_in
        self.length = lead_in + bars * beats * self.spb + tail
        self.mix = K.Mix(self.length)
        self.seed = seed
        self._n = 0
        self.r = K.rng(seed)
        self.shape = None  # [(seconds, dB)]: the mix's level over time (the piece breathes)

    # time and pitch
    def t(self, bar, beat=0.0):
        return self.lead + (bar * self.beats + beat) * self.spb

    def deg(self, d, octave=0):
        if isinstance(d, str):
            return self.key + 12 * octave + int(d)
        return K.degree(self.key + 12 * octave, self.mode, d)

    # notes
    def play(self, inst, at, pitch, dur, vel=0.6, pan=0.0, send="hall", wet=0.3, gain=1.0, **kw):
        self._n += 1
        f0 = float(K.hz(pitch))
        fn = INSTR[inst]
        known = _params(fn)
        x = fn(f0, dur, vel, self.seed * 1000 + self._n, **{k: v for k, v in kw.items() if k in known})
        self.mix.add(x, at, gain, pan, send, wet)

    def add(self, sig, at=0.0, gain=1.0, pan=0.0, send=None, wet=0.0):
        self.mix.add(sig, at, gain, pan, send, wet)

    def render(self, rooms=("hall",), fade=2.0):
        """The mix with its reverbs; the last `fade` seconds ease out so no tail is cut off."""
        y = self.mix.render({k: ir(k) for k in set(rooms) | set(self.mix.sends)})
        if self.shape:
            ts, dbs = zip(*sorted(self.shape))
            g = K.dbamp(np.interp(np.arange(len(y)) / K.SR, ts, dbs))
            y = y * g[:, None]
        k = min(K.ns(fade), len(y) // 3)
        y[len(y) - k:] *= K._rise(k)[::-1][:, None] ** 1.5
        return y

    # arranging helpers
    def voiced(self, root, stack=(0, 2, 4), centre=None, octave=0):
        """Chord tones of the degree `root`, each moved by octaves to sit within a fifth of `centre` (MIDI)."""
        out = []
        for s in stack:
            m = self.deg(root + s if isinstance(root, int) else root, octave)
            if centre is not None:
                while m < centre - 7:
                    m += 12
                while m > centre + 6:
                    m -= 12
            out.append(m)
        return sorted(set(out))

    def pads(self, inst, prog, start_bar, centre, stack=(0, 2, 4), vel=0.3, pan=0.35, send="hall", wet=0.4,
             overlap=0.6, **kw):
        """Sustained chords; prog: [(degree, bars)]. Returns the bar after the last chord."""
        bar = start_bar
        for root, bars in prog:
            if root is None:
                bar += bars
                continue
            tones = self.voiced(root, stack, centre)
            for i, m in enumerate(tones):
                p = pan * (2 * i / max(1, len(tones) - 1) - 1) if len(tones) > 1 else 0.0
                self.play(inst, self.t(bar), m, bars * self.beats * self.spb + overlap, vel, p, send, wet, **kw)
            bar += bars
        return bar

    def bass(self, inst, prog, start_bar, octave=-1, vel=0.4, send="hall", wet=0.2, overlap=0.4, **kw):
        bar = start_bar
        for root, bars in prog:
            if root is not None:
                self.play(inst, self.t(bar), self.deg(root, octave), bars * self.beats * self.spb + overlap, vel, 0.0, send, wet, **kw)
            bar += bars
        return bar

    def arp(self, inst, prog, start_bar, centre, pattern, step=0.5, stack=(0, 2, 4), vel=0.35, accent=None,
            pan=0.0, send="hall", wet=0.3, note=None, swing=0.0, human=0.008, **kw):
        """Broken chords: pattern indexes the chord's tones (an index past the end climbs an octave); None rests."""
        bar = start_bar
        for root, bars in prog:
            if root is None:
                bar += bars
                continue
            tones = self.voiced(root, stack, centre)
            steps = int(round(bars * self.beats / step))
            for k in range(steps):
                idx = pattern[k % len(pattern)]
                if idx is None:
                    continue
                o, i = divmod(idx, len(tones))
                m = tones[i] + 12 * o
                beat = k * step + (swing * step if k % 2 else 0.0)
                v = vel * (accent[k % len(accent)] if accent else 1.0) * (1 + self.r.normal(0, 0.06))
                at = self.t(bar, beat) + self.r.normal(0, human)
                self.play(inst, max(0.0, at), m, note or step * self.spb * 0.95, v, pan, send, wet, **kw)
            bar += bars
        return bar

    def phrase(self, inst, notes, bar, beat=0.0, octave=0, vel=0.55, pan=0.0, send="hall", wet=0.35, legato=1.0,
               human=0.01, **kw):
        """notes: [(degree or None, beats[, vel multiplier])]. Returns the beat count used."""
        t = self.t(bar, beat)
        total = 0.0
        for item in notes:
            d, beats = item[0], item[1]
            vm = item[2] if len(item) > 2 else 1.0
            if d is not None:
                self.play(inst, max(0.0, t + self.r.normal(0, human)), self.deg(d, octave), beats * self.spb * legato,
                          vel * vm, pan, send, wet, **kw)
            t += beats * self.spb
            total += beats
        return total

    def colour(self, inst, degrees, bars, start_bar, per_bar=0.5, octave=1, vel=0.2, grid=1.0, send="hall", wet=0.5,
               spread=0.6, **kw):
        """Sparse notes from `degrees` on the beat grid, about `per_bar` per bar."""
        for b in range(bars):
            k = self.r.poisson(per_bar)
            for _ in range(k):
                beat = grid * int(self.r.integers(0, int(self.beats / grid)))
                d = degrees[int(self.r.integers(len(degrees)))]
                self.play(inst, self.t(start_bar + b, beat), self.deg(d, octave), grid * self.spb,
                          vel * self.r.uniform(0.6, 1.0), self.r.uniform(-spread, spread), send, wet, **kw)

    def beats_at(self, inst, hits, bars, start_bar, pitch, vel=0.4, send="room", wet=0.2, pan=0.0, every=1, **kw):
        """A rhythm: hits = [(beat, vel multiplier)] repeated each bar (every n bars)."""
        for b in range(0, bars, every):
            for beat, vm in hits:
                self.play(inst, self.t(start_bar + b, beat), pitch, 0.2, vel * vm * (1 + self.r.normal(0, 0.05)), pan,
                          send, wet, **kw)

    def bed(self, x, start=0.0, end=None, gain=1.0, fade_in=4.0, fade_out=6.0, send=None, wet=0.0):
        """An atmosphere bed placed from start to end (seconds), faded."""
        end = self.length if end is None else end
        n = K.ns(end - start)
        y = K.pad_to(x, n) if x.ndim == 1 else K.pad_to(x, n)
        e = np.ones(n)
        fi, fo = min(K.ns(fade_in), n // 2), min(K.ns(fade_out), n // 2)
        e[:fi] = K._rise(fi)
        e[n - fo:] *= K._rise(fo)[::-1]
        y = y * (e if y.ndim == 1 else e[:, None])
        self.add(y, start, gain, 0.0, send, wet)


# ---- atmosphere beds (mono or stereo, n samples) ---------------------------------------------------------------------

def _st(a, b):
    return np.stack([a, b], axis=1)


def wind(n, seed, centre=600, q=0.7, gust=0.12, bright=0.0):
    """Stereo wind: band noise whose centre and level drift with the gusts."""
    out = []
    for c in range(2):
        cen = centre * 2 ** (0.7 * K.smooth_noise(n, gust * 1.5, seed + 10 * c))
        lvl = 0.45 + 0.55 * (0.5 + 0.5 * K.smooth_noise(n, gust, seed + 10 * c + 1)) ** 1.5
        x = K.noise(n, "pink", seed + 10 * c + 2)

        def resp(t, f, cen=cen):
            return K.bp_resp(cen[min(n - 1, int(t * K.SR))], q)(f) + bright * K.hp_resp(5000)(f) * 0.2
        y = K.stft_filter(x, resp) * lvl
        out.append(y / (np.std(y) + 1e-9))
    return _st(*out) * 0.3


def murk(n, seed, cutoff=500):
    """Low, dark room tone (stone, water above you)."""
    a = K.lowpass(K.noise(n, "brown", seed), cutoff, 2)
    b = K.lowpass(K.noise(n, "brown", seed + 1), cutoff, 2)
    a, b = a / (np.std(a) + 1e-9), b / (np.std(b) + 1e-9)
    return _st(a, b) * 0.25


def hiss(n, seed, lo=3000, hi=12000, flutter=0.4):
    a = K.lowpass(K.highpass(K.noise(n, "white", seed), lo, 2), hi, 2)
    b = K.lowpass(K.highpass(K.noise(n, "white", seed + 1), lo, 2), hi, 2)
    m = 1 - flutter * (0.5 + 0.5 * K.smooth_noise(n, 2.5, seed + 2))
    return _st(a * m, b * m) / (np.std(a) + 1e-9) * 0.2


def scatter(score, inst, n_events, start, end, pitch_lo, pitch_hi, vel=0.2, send="hall", wet=0.4, spread=0.8, **kw):
    """Random one-shots (drips, bubbles, chirps) between start and end seconds."""
    r = score.r
    for _ in range(n_events):
        at = r.uniform(start, end)
        p = r.uniform(pitch_lo, pitch_hi)
        score.play(inst, at, p, 0.2, vel * r.uniform(0.4, 1.0), r.uniform(-spread, spread), send, wet, **kw)
