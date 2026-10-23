"""The overworld creatures' own voices: ambient, hurt, death and step for each of the 34 (a flier's "step" is its
wingbeat), and a roar or cry for each named beast's signature move. Synthesised here (numpy), written as mono Ogg Vorbis
through ffmpeg (libvorbis), registered in src/main/merge/fauna/assets/awaken/sounds.json with subtitles in the fauna
lang file. Deterministic: the same table always gives the same files.

    python3 tools/art/specs/fauna_sounds.py build          every .ogg, sounds.json, subtitles
    python3 tools/art/specs/fauna_sounds.py sheet DIR      a spectrogram sheet of every sound (to judge them by eye)

Quiet by design (vanilla's quiet): small creatures' ambient calls are soft and short, their steps nearly silent; only
the beasts are loud, and their roars carry (attenuation 48). The Java side (mobs.MobSounds) registers the events
fauna.<id>.<kind>; FaunaMob plays them and spaces ambient calls out (a small creature speaks every 45 s or so).
build(out) is a no-op so build_all.py (textures) does not re-encode audio.
"""
import json
import math
import os
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds/fauna")
MERGE = os.path.join(ROOT, "src/main/merge/fauna/assets/awaken")
SR = 22050


def build(out):  # textures only in build_all; audio is built on demand
    pass


# ---------------------------------------------------------------------------------------------------------------------
# DSP primitives (all numpy, offline)

def T(dur):
    return np.arange(int(dur * SR)) / SR


def bp(dur, pts):
    """Breakpoint curve: pts [(t, v)] (t in seconds, may exceed dur), linear in between."""
    t = T(dur)
    xs, ys = zip(*pts)
    return np.interp(t, xs, ys)


def smooth_noise(n, rng, rate=8.0):
    """Slow random wobble (for jitter): noise sampled at `rate` Hz, interpolated."""
    k = max(2, int(n / SR * rate) + 2)
    pts = rng.standard_normal(k)
    return np.interp(np.arange(n), np.linspace(0, n, k), pts)


def harmonic(freq, tilt=1.2, odd=False, maxh=60, phase0=0.0):
    """Band-limited harmonic stack following a time-varying fundamental; amplitude 1/k^tilt."""
    ph = 2 * np.pi * np.cumsum(freq) / SR + phase0
    out = np.zeros_like(freq)
    for k in range(1, maxh + 1):
        if odd and k % 2 == 0:
            continue
        mask = (freq * k) < SR * 0.45
        if not mask.any():
            break
        out += np.where(mask, np.sin(k * ph), 0.0) / (k ** tilt)
    return out


def sine(freq, phase0=0.0):
    return np.sin(2 * np.pi * np.cumsum(freq) / SR + phase0)


def spectral(x, shape):
    """Filters x by a magnitude response shape(f) (FFT domain, zero phase)."""
    n = len(x)
    if n == 0:
        return x
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    return np.fft.irfft(X * shape(f), n)


def formants(x, fs, floor=0.05):
    """A bank of resonances [(centre Hz, bandwidth Hz, gain)] over a small floor."""
    def shape(f):
        h = np.full_like(f, floor)
        for fc, bw, g in fs:
            h += g * np.exp(-0.5 * ((f - fc) / bw) ** 2)
        return h
    return spectral(x, shape)


def lowpass(x, fc, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (f / fc) ** (2 * order)))


def highpass(x, fc, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** (2 * order)))


def bandpass(x, fc, bw):
    return spectral(x, lambda f: np.exp(-0.5 * ((f - fc) / bw) ** 2))


def drive(x, k):
    if k <= 0:
        return x
    return np.tanh(k * x) / np.tanh(k)


def reverb(x, rt=0.35, mix=0.18, rng=None, bright=3000):
    """A short room/forest tail: convolution with decaying, darkened noise."""
    rng = rng or np.random.default_rng(1)
    n = int(rt * SR)
    ir = rng.standard_normal(n) * np.exp(-6.9 * np.arange(n) / n)
    ir = lowpass(ir, bright)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    y = np.convolve(x, ir)[: len(x) + n // 2]
    out = np.zeros(len(y))
    out[: len(x)] += x * (1 - mix)
    out += y * mix
    return out


def pad(x, before=0.0, after=0.0):
    return np.concatenate([np.zeros(int(before * SR)), x, np.zeros(int(after * SR))])


def mixdown(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def at(x, t, total=None):
    """x placed t seconds into silence."""
    y = pad(x, before=t)
    if total:
        y = pad(y, after=max(0.0, total - len(y) / SR))
    return y


def fade(x, a=0.004, r=0.03):
    n = len(x)
    e = np.ones(n)
    na, nr = min(n, int(a * SR)), min(n, int(r * SR))
    if na:
        e[:na] = np.linspace(0, 1, na)
    if nr:
        e[n - nr:] *= np.linspace(1, 0, nr)
    return x * e


# ---------------------------------------------------------------------------------------------------------------------
# Recipes

def voice(rng, dur, f0, amp, form, tilt=1.25, breath=0.12, growl=None, vib=None, jitter=0.012, drive_k=0.0, odd=False, nasal=0.0):
    """A creature's voice: a harmonic source on a pitch contour through formants, with breath, growl (AM roughness),
    vibrato and jitter. f0 and amp are breakpoint lists."""
    n = int(dur * SR)
    f = bp(dur, f0)
    f = f * (1 + jitter * smooth_noise(n, rng, 14))
    if vib:
        rate, depth = vib
        f = f * (1 + depth * np.sin(2 * np.pi * rate * T(dur) + rng.uniform(0, 6)))
    src = harmonic(f, tilt=tilt, odd=odd)
    if growl:
        rate, depth = growl
        wob = np.sin(2 * np.pi * np.cumsum(rate * (1 + 0.2 * smooth_noise(n, rng, 6))) / SR)
        src = src * (1 - depth * 0.5 + depth * 0.5 * wob) + depth * 0.25 * harmonic(f * 0.5, tilt=1.6)
    noise_ = rng.standard_normal(n) * 0.35
    x = formants(src, form) + breath * formants(noise_, [(fc, bw * 1.6, g) for fc, bw, g in form])
    if nasal:
        x += nasal * bandpass(src, 2400, 400)
    x = x * bp(dur, amp)
    if drive_k:
        x = drive(x / (np.max(np.abs(x)) + 1e-9), drive_k)
    return x


def chirp(rng, dur, f_from, f_to, amp=1.0, harm=0.25):
    f = np.geomspace(f_from, f_to, int(dur * SR))
    x = sine(f) + harm * sine(2 * f)
    e = np.sin(np.linspace(0, np.pi, len(f))) ** 1.5
    return x * e * amp


def chime(rng, dur, f, partials=((1, 1.0), (2.76, 0.45), (5.4, 0.25), (8.9, 0.1)), decay=6.0):
    t = T(dur)
    x = sum(g * np.sin(2 * np.pi * f * r * t + rng.uniform(0, 6)) * np.exp(-decay * r ** 0.5 * t) for r, g in partials)
    return fade(x, 0.002, 0.02)


def noise_burst(rng, dur, fc, bw, env_pts, lp=None):
    x = bandpass(rng.standard_normal(int(dur * SR)), fc, bw)
    if lp:
        x = lowpass(x, lp)
    return x * bp(dur, env_pts)


def thud(rng, dur=0.25, f_hi=90, f_lo=38, body=1.0, click=0.3, click_fc=1800, gravel=0.0, decay=14):
    t = T(dur)
    f = f_lo + (f_hi - f_lo) * np.exp(-t * 18)
    x = sine(f) * np.exp(-t * decay) * body
    x += click * noise_burst(rng, dur, click_fc, click_fc * 0.6, [(0, 1), (0.015, 0.4), (0.05, 0)])
    if gravel:
        g = rng.standard_normal(len(t)) * (rng.random(len(t)) < 0.02)
        x += gravel * lowpass(g, 900) * np.exp(-t * 6) * 6
    x += 0.4 * body * lowpass(rng.standard_normal(len(t)), 220) * np.exp(-t * 20)
    return fade(x, 0.001, 0.03)


def clop(rng, hard=1.0, f=170):
    a = noise_burst(rng, 0.09, 1600 * hard, 900, [(0, 1), (0.01, 0.5), (0.06, 0)])
    b = thud(rng, 0.14, f, f * 0.6, 0.6, 0.0, decay=30)
    return mixdown(a * hard, b)


def pad_step(rng, dur=0.12, fc=300):
    x = lowpass(rng.standard_normal(int(dur * SR)), fc) * bp(dur, [(0, 0), (0.01, 1), (dur, 0)]) * 3
    return fade(x, 0.002, 0.02)


def squelch(rng, dur=0.22, f_from=900, f_to=220, bubbles=2):
    n = int(dur * SR)
    fc = np.geomspace(f_from, f_to, n)
    raw = rng.standard_normal(n)
    # a swept resonance (frame by frame in 4 chunks keeps it cheap)
    out = np.zeros(n)
    chunks = 6
    for k in range(chunks):
        a, b = k * n // chunks, (k + 1) * n // chunks
        out[a:b] = bandpass(raw, fc[(a + b) // 2], fc[(a + b) // 2] * 0.35)[a:b]
    out *= bp(dur, [(0, 0), (0.02, 1), (dur, 0)])
    for k in range(bubbles):
        bt = rng.uniform(0.03, dur * 0.8)
        bf = rng.uniform(300, 700)
        bub = chirp(rng, 0.03, bf, bf * 1.8, 0.5)
        out = mixdown(out, at(bub, bt))[:n]
    return fade(out, 0.002, 0.03)


def wingbeat(rng, dur=0.18, fc=900, deep=0.0):
    x = noise_burst(rng, dur, fc, fc * 0.8, [(0, 0), (dur * 0.3, 1), (dur, 0)])
    if deep:
        x += deep * lowpass(rng.standard_normal(len(x)), 180) * bp(dur, [(0, 0), (dur * 0.35, 1), (dur, 0)]) * 4
    return fade(x, 0.004, 0.03)


def hiss(rng, dur, fc=4200, bw=1800, env_pts=None):
    return noise_burst(rng, dur, fc, bw, env_pts or [(0, 0), (0.05, 1), (dur * 0.7, 0.7), (dur, 0)])


def rattle(rng, dur, rate=34, fc=3200):
    x = hiss(rng, dur, fc, 1600, [(0, 0), (0.04, 1), (dur - 0.08, 0.8), (dur, 0)])
    gate = (np.sin(2 * np.pi * rate * T(dur) * (1 + 0.08 * smooth_noise(len(x), rng, 5))) > 0.1).astype(float)
    return x * (0.25 + 0.75 * lowpass(gate, 600))


def chitter(rng, dur, rate=18, fc=3000, n_clicks=None):
    out = np.zeros(int(dur * SR))
    t = rng.uniform(0, 0.02)
    while t < dur - 0.03:
        c = noise_burst(rng, 0.02, fc * rng.uniform(0.8, 1.25), fc * 0.4, [(0, 1), (0.004, 0.7), (0.02, 0)])
        out = mixdown(out, at(c * rng.uniform(0.5, 1.0), t))[: len(out)]
        t += rng.uniform(0.6, 1.4) / rate
    return out


def creak(rng, dur, f_from=18, f_to=40, form=((620, 120, 1.0), (1650, 220, 0.6), (3100, 400, 0.3))):
    """Wood under strain: a slow stick-slip pulse train through woody resonances."""
    rate = bp(dur, [(0, f_from), (dur * 0.5, f_to), (dur, f_from * 1.2)]) * (1 + 0.3 * smooth_noise(int(dur * SR), rng, 7))
    ph = np.cumsum(rate) / SR
    pulses = (np.diff(np.floor(ph), prepend=0) > 0).astype(float)
    x = formants(pulses * 4 + 0.02 * rng.standard_normal(len(pulses)), list(form), floor=0.02)
    return x * bp(dur, [(0, 0), (0.05, 1), (dur - 0.1, 0.8), (dur, 0)])


def rumble(rng, dur, fc=110, amp_pts=None):
    x = lowpass(rng.standard_normal(int(dur * SR)), fc, order=3) * 4
    x += 0.4 * sine(np.full(len(x), fc * 0.4))
    return x * bp(dur, amp_pts or [(0, 0), (0.1, 1), (dur - 0.2, 0.8), (dur, 0)])


def tumble(rng, dur, n=6, heavy=1.0):
    out = np.zeros(int(dur * SR))
    for k in range(n):
        t = rng.uniform(0, dur * 0.8)
        c = thud(rng, 0.2, 140 * rng.uniform(0.7, 1.4), 60, 0.5 * heavy, 0.6, click_fc=rng.uniform(900, 2400), gravel=0.4)
        out = mixdown(out, at(c * rng.uniform(0.4, 1.0), t))[: len(out)]
    return out


def buzz(rng, dur, f=190, amp_pts=None):
    fr = np.full(int(dur * SR), f) * (1 + 0.02 * smooth_noise(int(dur * SR), rng, 9))
    x = harmonic(fr, tilt=1.0) * (0.7 + 0.3 * np.sin(2 * np.pi * 9 * T(dur)))
    x = lowpass(x, 1600)
    return x * bp(dur, amp_pts or [(0, 0), (0.05, 1), (dur - 0.08, 1), (dur, 0)])


# vowel formants (rough, for creature voices: these are colours, not words)
AH = [(700, 130, 1.0), (1150, 160, 0.6), (2600, 260, 0.25)]
OH = [(450, 100, 1.0), (850, 140, 0.55), (2400, 260, 0.15)]
OO = [(320, 90, 1.0), (760, 140, 0.35), (2300, 260, 0.1)]
EH = [(550, 120, 1.0), (1800, 200, 0.5), (2600, 260, 0.25)]
EE = [(300, 90, 0.9), (2300, 260, 0.6), (3000, 300, 0.3)]
NG = [(260, 80, 1.0), (1100, 200, 0.2), (2500, 300, 0.1)]
BOAR = [(380, 120, 1.0), (1050, 200, 0.7), (2200, 300, 0.4), (3400, 400, 0.2)]
CROAK = [(280, 70, 1.0), (850, 160, 0.5), (1900, 260, 0.2)]


def scaled(form, k):
    return [(fc * k, bw * k, g) for fc, bw, g in form]


# ---------------------------------------------------------------------------------------------------------------------
# The creatures: kind -> [variant functions]. Each returns a float array; the writer normalises and encodes.

def S(fn, *a, **k):
    return lambda rng: fn(rng, *a, **k)


def grunt(rng, f=110, dur=0.4, form=BOAR, growl=(38, 0.6), drive_k=1.5, rise=1.0):
    return voice(rng, dur, [(0, f), (dur * 0.3, f * 1.15 * rise), (dur, f * 0.8)], [(0, 0), (0.03, 1), (dur * 0.5, 0.7), (dur, 0)],
                 form, growl=growl, drive_k=drive_k, breath=0.35)


def snort(rng, dur=0.35, fc=900):
    a = noise_burst(rng, dur, fc, fc * 0.8, [(0, 0), (0.02, 1), (0.08, 0.6), (dur, 0)])
    b = noise_burst(rng, 0.15, fc * 0.6, fc * 0.4, [(0, 0), (0.02, 0.7), (0.15, 0)])
    return mixdown(a, at(b, dur * 0.55))


def squeal(rng, f=700, dur=0.45, drive_k=2.0):
    return voice(rng, dur, [(0, f * 0.8), (0.06, f * 1.3), (dur * 0.6, f * 1.1), (dur, f * 0.6)], [(0, 0), (0.02, 1), (dur * 0.7, 0.8), (dur, 0)],
                 scaled(EE, 1.1), tilt=0.9, growl=(55, 0.4), drive_k=drive_k, breath=0.25)


def bleat(rng, f=320, dur=0.55, trem=11, form=EH):
    return voice(rng, dur, [(0, f), (0.08, f * 1.08), (dur, f * 0.9)], [(0, 0), (0.04, 1), (dur * 0.7, 0.8), (dur, 0)],
                 form, vib=(trem, 0.06), growl=(trem, 0.5), nasal=0.4, breath=0.15)


def croak(rng, f=95, dur=0.5, rate=24, big=1.0, form=CROAK):
    x = voice(rng, dur, [(0, f), (dur, f * 0.85)], [(0, 0), (0.03, 1), (dur * 0.8, 0.9), (dur, 0)], scaled(form, 1 / big ** 0.3),
              tilt=0.9, growl=(rate, 1.0), breath=0.1, jitter=0.02)
    return x


def hoot(rng, f=380, notes=2, gap=0.32):
    out = []
    for k in range(notes):
        d = 0.36 if k == notes - 1 else 0.22
        x = voice(rng, d, [(0, f * 1.03), (d, f * 0.94)], [(0, 0), (0.06, 1), (d - 0.08, 0.8), (d, 0)], OO, tilt=2.6, breath=0.22, jitter=0.004)
        out.append(at(x, k * gap))
    return reverb(mixdown(*out), 0.5, 0.25)


def howl(rng, f=420, dur=1.6):
    x = voice(rng, dur, [(0, f * 0.8), (0.3, f), (dur * 0.7, f * 1.05), (dur, f * 0.7)], [(0, 0), (0.2, 1), (dur - 0.3, 0.8), (dur, 0)],
              OO, tilt=2.2, vib=(5, 0.015), breath=0.3, jitter=0.006)
    return reverb(x, 0.9, 0.35)


def growl(rng, f=70, dur=0.9, form=OH, depth=0.8, drive_k=2.0):
    return voice(rng, dur, [(0, f), (dur * 0.5, f * 1.1), (dur, f * 0.9)], [(0, 0), (0.08, 1), (dur - 0.15, 0.9), (dur, 0)], form,
                 growl=(28, depth), drive_k=drive_k, breath=0.4, tilt=1.0)


def bugle(rng, base=140, top=1300, dur=1.8):
    """An elk's bugle: a groan that breaks into a rising whistle, holds, and falls away."""
    g = growl(rng, base, dur * 0.45, OH, 0.7, 1.6)
    w = voice(rng, dur * 0.75, [(0, top * 0.55), (0.25, top), (dur * 0.5, top * 1.04), (dur * 0.75, top * 0.7)],
              [(0, 0), (0.15, 1), (dur * 0.55, 0.9), (dur * 0.75, 0)], [(top, 300, 1.0), (top * 2, 500, 0.3)], tilt=2.5, vib=(6, 0.01),
              breath=0.3, jitter=0.005)
    return reverb(mixdown(g, at(w * 0.8, dur * 0.18)), 0.8, 0.3)


def whistle(rng, f_from, f_to, dur, vib=(9, 0.01)):
    f = bp(dur, [(0, f_from), (dur * 0.6, f_to), (dur, f_to * 0.97)]) * (1 + vib[1] * np.sin(2 * np.pi * vib[0] * T(dur)))
    return sine(f) * bp(dur, [(0, 0), (0.03, 1), (dur - 0.05, 0.9), (dur, 0)])


def choir(rng, f=220, dur=2.0, voices=4, form=AH):
    out = []
    for k in range(voices):
        det = 1 + rng.uniform(-0.012, 0.012) + (0.5 if k == voices - 1 else 0)
        out.append(voice(rng, dur, [(0, f * det * 0.96), (dur * 0.5, f * det * 1.02), (dur, f * det * 0.9)],
                         [(0, 0), (0.4, 1), (dur - 0.4, 0.8), (dur, 0)], form, tilt=2.0, vib=(5.5, 0.012), breath=0.1, jitter=0.004))
    return reverb(mixdown(*out), 1.2, 0.4)


def roar_layer(rng, f, dur, form, growl_rate=30, drive_k=3.0, sub=0.6):
    v = voice(rng, dur, [(0, f * 0.8), (0.15, f * 1.15), (dur * 0.6, f), (dur, f * 0.6)], [(0, 0), (0.06, 1), (dur * 0.65, 0.9), (dur, 0)],
              form, growl=(growl_rate, 0.9), drive_k=drive_k, breath=0.5, tilt=0.9)
    s_ = sine(bp(dur, [(0, f * 0.5), (dur, f * 0.35)])) * bp(dur, [(0, 0), (0.1, 1), (dur - 0.2, 0.8), (dur, 0)]) * sub
    return mixdown(v, s_)


def puff(rng, dur=0.3, fc=1200):
    return noise_burst(rng, dur, fc, fc, [(0, 0), (0.02, 1), (dur, 0)], lp=fc * 2)


CREATURES = {
    # ---- P1: Greenwild, Sunscorch, Frostwild -------------------------------------------------------------------
    "lumen_moth": dict(
        ambient=[S(lambda r: mixdown(wingbeat(r, 0.4, 1400), at(chime(r, 0.7, 1900, decay=8) * 0.5, 0.1))),
                 S(lambda r: mixdown(chime(r, 0.8, 2300, decay=7) * 0.6, at(chime(r, 0.6, 1700, decay=9) * 0.4, 0.18)))],
        hurt=[S(lambda r: chirp(r, 0.12, 3200, 2400)), S(lambda r: chirp(r, 0.1, 3600, 2800))],
        death=[S(lambda r: mixdown(chime(r, 1.0, 1500, decay=4), at(chime(r, 0.8, 1100, decay=5) * 0.7, 0.2)))],
        step=[S(wingbeat, 0.14, 1600), S(wingbeat, 0.12, 1900)]),
    "bloom_sprite": dict(
        ambient=[S(lambda r: mixdown(*[at(chime(r, 0.5, f, decay=9) * 0.5, k * 0.11) for k, f in enumerate((1568, 1976, 2349))])),
                 S(lambda r: mixdown(*[at(chime(r, 0.5, f, decay=9) * 0.5, k * 0.13) for k, f in enumerate((2093, 1760, 2637))]))],
        hurt=[S(lambda r: voice(r, 0.18, [(0, 900), (0.05, 1300), (0.18, 800)], [(0, 0), (0.02, 1), (0.18, 0)], EE, tilt=1.8))],
        death=[S(lambda r: mixdown(*[at(chime(r, 0.7, f, decay=6) * 0.5, k * 0.15) for k, f in enumerate((2349, 1976, 1568, 1175))]))],
        step=[S(wingbeat, 0.1, 2000)]),
    "glass_scarab": dict(
        ambient=[S(chitter, 0.35, 22, 4200), S(chitter, 0.25, 28, 5000)],
        hurt=[S(lambda r: mixdown(chime(r, 0.25, 3100, decay=18), noise_burst(r, 0.05, 5000, 2000, [(0, 1), (0.05, 0)])))],
        death=[S(lambda r: mixdown(*[at(chime(r, 0.4, r.uniform(2600, 4800), decay=14) * 0.5, r.uniform(0, 0.25)) for _ in range(6)]))],
        step=[S(chitter, 0.08, 40, 5500), S(chitter, 0.07, 45, 6000)]),
    "frostfinch": dict(
        ambient=[S(lambda r: mixdown(chirp(r, 0.07, 3400, 4600), at(chirp(r, 0.08, 3800, 5200), 0.11))),
                 S(lambda r: mixdown(chirp(r, 0.09, 4200, 3000), at(chirp(r, 0.06, 3600, 4400), 0.12), at(chirp(r, 0.06, 3600, 4400), 0.2)))],
        hurt=[S(lambda r: chirp(r, 0.1, 5000, 3600))],
        death=[S(lambda r: mixdown(chirp(r, 0.18, 4400, 2400), at(chirp(r, 0.2, 3600, 1800) * 0.6, 0.15)))],
        step=[S(pad_step, 0.05, 2400), S(pad_step, 0.04, 2800)]),
    "mossback": dict(
        ambient=[S(grunt, 70, 0.6, OH, (20, 0.5), 1.0), S(lambda r: mixdown(grunt(r, 62, 0.5, OO, (18, 0.5), 1.0), at(snort(r, 0.3, 500) * 0.5, 0.45)))],
        hurt=[S(grunt, 85, 0.35, OH, (30, 0.7), 1.8, 1.2)],
        death=[S(lambda r: voice(r, 1.3, [(0, 80), (1.3, 50)], [(0, 0), (0.1, 1), (1.3, 0)], OO, breath=0.5, growl=(14, 0.5)))],
        step=[S(thud, 0.3, 70, 35, 1.0, 0.15, 900), S(thud, 0.3, 65, 32, 1.0, 0.15, 800)]),
    "dune_ray": dict(
        ambient=[S(lambda r: reverb(voice(r, 1.0, [(0, 600), (0.5, 900), (1.0, 700)], [(0, 0), (0.2, 1), (1.0, 0)], OO, tilt=2.4, breath=0.4), 0.6, 0.3)),
                 S(lambda r: reverb(voice(r, 0.8, [(0, 1000), (0.8, 650)], [(0, 0), (0.15, 1), (0.8, 0)], OO, tilt=2.4, breath=0.4), 0.6, 0.3))],
        hurt=[S(lambda r: voice(r, 0.25, [(0, 1100), (0.06, 1500), (0.25, 900)], [(0, 0), (0.02, 1), (0.25, 0)], EE, tilt=1.5))],
        death=[S(lambda r: reverb(voice(r, 1.2, [(0, 900), (1.2, 380)], [(0, 0), (0.1, 1), (1.2, 0)], OO, tilt=2.2, breath=0.5), 0.8, 0.35))],
        step=[S(wingbeat, 0.35, 500, 0.6)]),
    "frost_stag": dict(
        ambient=[S(bleat, 300, 0.5, 9, OH), S(lambda r: voice(r, 0.7, [(0, 500), (0.3, 900), (0.7, 600)], [(0, 0), (0.1, 1), (0.7, 0)], OO, tilt=2.0, breath=0.35))],
        hurt=[S(bleat, 380, 0.3, 13, EH)],
        death=[S(lambda r: voice(r, 0.9, [(0, 420), (0.9, 220)], [(0, 0), (0.05, 1), (0.9, 0)], EH, nasal=0.3, growl=(11, 0.5), breath=0.3))],
        step=[S(clop, 0.7, 220), S(clop, 0.6, 200), S(clop, 0.65, 210)]),
    "gloomstalker": dict(
        ambient=[S(growl, 85, 0.9, OH, 0.7, 1.4), S(lambda r: voice(r, 0.6, [(0, 160), (0.6, 120)], [(0, 0), (0.1, 1), (0.6, 0)], NG, growl=(22, 0.6), breath=0.5))],
        hurt=[S(lambda r: voice(r, 0.3, [(0, 500), (0.05, 700), (0.3, 350)], [(0, 0), (0.02, 1), (0.3, 0)], AH, growl=(40, 0.6), drive_k=2.5))],
        death=[S(lambda r: voice(r, 1.0, [(0, 600), (1.0, 250)], [(0, 0), (0.05, 1), (1.0, 0)], EE, tilt=1.6, breath=0.4, vib=(7, 0.02)))],
        step=[S(pad_step, 0.1, 260), S(pad_step, 0.09, 300)]),
    "wisp_hound": dict(
        ambient=[S(howl, 440, 1.5), S(howl, 390, 1.3)],
        hurt=[S(lambda r: reverb(voice(r, 0.3, [(0, 700), (0.05, 1000), (0.3, 500)], [(0, 0), (0.02, 1), (0.3, 0)], EE, tilt=1.6), 0.4, 0.3))],
        death=[S(howl, 360, 1.8)],
        step=[S(pad_step, 0.1, 420), S(pad_step, 0.09, 480)]),
    "briar_sow": dict(
        ambient=[S(lambda r: mixdown(snort(r, 0.35, 800), at(grunt(r, 105, 0.45), 0.25))), S(lambda r: grunt(r, 95, 0.6)),
                 S(lambda r: mixdown(snort(r, 0.3, 700), at(snort(r, 0.3, 650) * 0.8, 0.35)))],
        hurt=[S(squeal, 620, 0.4), S(squeal, 560, 0.35)],
        death=[S(lambda r: mixdown(squeal(r, 600, 0.7), at(grunt(r, 70, 1.2, BOAR, (20, 0.8), 1.2, 0.8), 0.5)))],
        step=[S(thud, 0.28, 95, 40, 1.0, 0.35, 1400, 0.3), S(thud, 0.28, 90, 38, 1.0, 0.35, 1300, 0.3), S(thud, 0.28, 100, 42, 1.0, 0.3, 1500, 0.25)],
        roar=[S(lambda r: reverb(mixdown(roar_layer(r, 140, 1.6, BOAR, 34, 3.0), at(squeal(r, 680, 0.9, 3.0) * 0.6, 0.15)), 0.7, 0.25)),
              S(lambda r: reverb(mixdown(roar_layer(r, 125, 1.4, BOAR, 30, 3.0), at(squeal(r, 620, 0.7, 3.0) * 0.5, 0.25)), 0.7, 0.25))]),
    "dune_wyrm": dict(
        ambient=[S(lambda r: mixdown(rumble(r, 1.4, 90), hiss(r, 1.2, 2600, 1400) * 0.4)), S(lambda r: mixdown(rumble(r, 1.0, 120), hiss(r, 0.9, 3000, 1600) * 0.5))],
        hurt=[S(lambda r: mixdown(voice(r, 0.45, [(0, 260), (0.08, 360), (0.45, 200)], [(0, 0), (0.03, 1), (0.45, 0)], AH, growl=(45, 0.8), drive_k=3.0),
                                  chime(r, 0.4, 2400, decay=10) * 0.3))],
        death=[S(lambda r: reverb(mixdown(rumble(r, 2.2, 80, [(0, 0), (0.1, 1), (2.2, 0)]), roar_layer(r, 90, 1.6, OH, 22, 2.0) * 0.7,
                                          *[at(chime(r, 0.6, r.uniform(1800, 3600), decay=8) * 0.3, r.uniform(0.2, 1.6)) for _ in range(6)]), 1.0, 0.3))],
        step=[S(lambda r: hiss(r, 0.4, 2400, 1600, [(0, 0), (0.15, 1), (0.4, 0)]) + rumble(r, 0.4, 100) * 0.4)],
        roar=[S(lambda r: reverb(mixdown(roar_layer(r, 95, 1.9, AH, 26, 3.2, 0.9), rumble(r, 1.9, 70),
                                         *[at(chime(r, 0.7, r.uniform(1600, 3200), decay=6) * 0.35, r.uniform(0.3, 1.2)) for _ in range(5)]), 1.0, 0.3))]),
    "old_antler": dict(
        ambient=[S(lambda r: voice(r, 1.1, [(0, 120), (0.5, 135), (1.1, 100)], [(0, 0), (0.15, 1), (1.1, 0)], OH, growl=(16, 0.5), breath=0.5, tilt=1.4)),
                 S(lambda r: mixdown(snort(r, 0.4, 700), at(voice(r, 0.8, [(0, 110), (0.8, 95)], [(0, 0), (0.1, 1), (0.8, 0)], OO, breath=0.5), 0.3)))],
        hurt=[S(lambda r: voice(r, 0.4, [(0, 300), (0.07, 420), (0.4, 220)], [(0, 0), (0.02, 1), (0.4, 0)], EH, growl=(30, 0.6), drive_k=1.8, nasal=0.3))],
        death=[S(lambda r: reverb(mixdown(voice(r, 2.0, [(0, 160), (0.4, 190), (2.0, 80)], [(0, 0), (0.1, 1), (2.0, 0)], OH, growl=(14, 0.6), breath=0.5),
                                          at(whistle(r, 900, 500, 1.0) * 0.3, 0.2)), 1.0, 0.35))],
        step=[S(clop, 1.0, 120), S(clop, 0.9, 110), S(clop, 1.0, 130)],
        roar=[S(bugle, 140, 1300, 2.0), S(bugle, 150, 1150, 1.8)]),
    # ---- P2: the Mire, the Canopy, Duskwood, Blossomwood, the Crags, Sporewild -----------------------------------
    "bogwick": dict(
        ambient=[S(lambda r: mixdown(buzz(r, 0.6, 210, [(0, 0), (0.1, 0.4), (0.5, 0.4), (0.6, 0)]), at(chime(r, 0.5, 1760, decay=9) * 0.6, 0.15))),
                 S(lambda r: mixdown(chime(r, 0.6, 2093, decay=8) * 0.6, buzz(r, 0.4, 230, [(0, 0), (0.05, 0.3), (0.4, 0)])))],
        hurt=[S(lambda r: buzz(r, 0.2, 320, [(0, 0), (0.02, 1), (0.2, 0)]))],
        death=[S(lambda r: mixdown(buzz(r, 0.7, 200, [(0, 1), (0.7, 0)]), chime(r, 0.9, 1400, decay=4) * 0.5))],
        step=[S(lambda r: buzz(r, 0.25, 220, [(0, 0), (0.05, 0.5), (0.25, 0)]))]),
    "mudskipper": dict(
        ambient=[S(squelch, 0.25, 1100, 300, 2), S(lambda r: mixdown(chirp(r, 0.06, 1400, 2000) * 0.5, at(squelch(r, 0.2, 900, 250, 1), 0.08)))],
        hurt=[S(lambda r: chirp(r, 0.1, 2200, 1500))],
        death=[S(lambda r: mixdown(squelch(r, 0.4, 700, 160, 3), chirp(r, 0.2, 1600, 800) * 0.5))],
        step=[S(squelch, 0.12, 1400, 600, 0), S(squelch, 0.1, 1600, 700, 0)]),
    "bog_toad": dict(
        ambient=[S(croak, 110, 0.5, 26), S(lambda r: mixdown(croak(r, 100, 0.35, 24), at(croak(r, 95, 0.4, 22), 0.5)))],
        hurt=[S(lambda r: voice(r, 0.3, [(0, 220), (0.05, 300), (0.3, 160)], [(0, 0), (0.02, 1), (0.3, 0)], CROAK, growl=(35, 0.9), drive_k=1.5))],
        death=[S(lambda r: croak(r, 90, 1.0, 18) * bp(1.0, [(0, 1), (1.0, 0.2)]))],
        step=[S(squelch, 0.15, 800, 300, 0), S(squelch, 0.16, 700, 260, 1)]),
    "mire_mother": dict(
        ambient=[S(lambda r: reverb(croak(r, 62, 0.9, 16, 2.0), 0.6, 0.25)),
                 S(lambda r: reverb(mixdown(croak(r, 58, 0.6, 15, 2.0), at(croak(r, 55, 0.8, 14, 2.0), 0.75), at(squelch(r, 0.4, 500, 120, 4) * 0.4, 1.3)), 0.6, 0.25))],
        hurt=[S(lambda r: voice(r, 0.5, [(0, 140), (0.06, 200), (0.5, 100)], [(0, 0), (0.02, 1), (0.5, 0)], CROAK, growl=(30, 0.9), drive_k=2.0))],
        death=[S(lambda r: reverb(mixdown(croak(r, 60, 2.0, 12, 2.5) * bp(2.0, [(0, 1), (2.0, 0.1)]), at(squelch(r, 0.8, 400, 90, 6) * 0.5, 1.2)), 1.0, 0.3))],
        step=[S(lambda r: mixdown(thud(r, 0.35, 75, 32, 1.0, 0.0), squelch(r, 0.3, 600, 150, 1) * 0.7)),
              S(lambda r: mixdown(thud(r, 0.35, 70, 30, 1.0, 0.0), squelch(r, 0.3, 550, 140, 2) * 0.7))],
        roar=[S(lambda r: reverb(mixdown(*[at(croak(r, 58 - k * 3, 0.55, 15, 2.5) * (1 - 0.15 * k), k * 0.5) for k in range(3)],
                                         at(rumble(r, 1.6, 70) * 0.5, 0.0)), 0.9, 0.3))]),
    "dart_frog": dict(
        ambient=[S(lambda r: mixdown(*[at(chirp(r, 0.04, 2600, 3200) * 0.6, k * 0.06) for k in range(6)])),
                 S(lambda r: mixdown(*[at(chirp(r, 0.04, 2900, 3500) * 0.6, k * 0.07) for k in range(4)]))],
        hurt=[S(lambda r: chirp(r, 0.08, 3600, 2600))],
        death=[S(lambda r: mixdown(*[at(chirp(r, 0.05, 3000 - k * 300, 2600 - k * 300) * (0.7 - 0.1 * k), k * 0.09) for k in range(5)]))],
        step=[S(squelch, 0.06, 2400, 1200, 0)]),
    "crested_basilisk": dict(
        ambient=[S(chitter, 0.4, 16, 2600), S(lambda r: mixdown(hiss(r, 0.4, 3600, 1500) * 0.5, chitter(r, 0.3, 20, 2400)))],
        hurt=[S(lambda r: mixdown(hiss(r, 0.25, 4200, 1800), chirp(r, 0.12, 2200, 1600) * 0.6))],
        death=[S(lambda r: mixdown(hiss(r, 0.7, 3200, 1400, [(0, 1), (0.7, 0)]), chirp(r, 0.4, 1800, 900) * 0.5))],
        step=[S(pad_step, 0.05, 1800), S(pad_step, 0.05, 2200)]),
    "canopy_prowler": dict(
        ambient=[S(growl, 75, 1.0, OH, 0.8, 1.6), S(lambda r: mixdown(*[at(voice(r, 0.18, [(0, 130), (0.18, 110)], [(0, 0), (0.03, 1), (0.18, 0)], AH, breath=0.6, growl=(30, 0.5)), k * 0.3) for k in range(3)]))],
        hurt=[S(lambda r: voice(r, 0.35, [(0, 420), (0.05, 600), (0.35, 300)], [(0, 0), (0.02, 1), (0.35, 0)], AH, growl=(42, 0.7), drive_k=2.5))],
        death=[S(lambda r: voice(r, 1.2, [(0, 300), (1.2, 120)], [(0, 0), (0.05, 1), (1.2, 0)], OH, growl=(20, 0.7), breath=0.5))],
        step=[S(pad_step, 0.11, 240), S(pad_step, 0.1, 280)]),
    "plumed_coil": dict(
        ambient=[S(lambda r: mixdown(hiss(r, 0.9, 4400, 1600), rattle(r, 0.5, 30) * 0.3)), S(lambda r: rattle(r, 0.7, 34))],
        hurt=[S(lambda r: mixdown(hiss(r, 0.3, 5200, 2200), voice(r, 0.3, [(0, 900), (0.05, 1300), (0.3, 700)], [(0, 0), (0.02, 1), (0.3, 0)], EE, tilt=1.4) * 0.6))],
        death=[S(lambda r: mixdown(hiss(r, 1.4, 3800, 1600, [(0, 1), (1.4, 0)]), rattle(r, 1.0, 26) * 0.4))],
        step=[S(lambda r: mixdown(wingbeat(r, 0.25, 1200), rattle(r, 0.2, 40) * 0.2))],
        roar=[S(lambda r: reverb(mixdown(rattle(r, 1.4, 36, 3400), at(hiss(r, 1.0, 4600, 2000), 0.3),
                                         at(voice(r, 0.8, [(0, 700), (0.1, 1100), (0.8, 600)], [(0, 0), (0.05, 1), (0.8, 0)], EE, tilt=1.3, drive_k=2.0, growl=(40, 0.5)) * 0.6, 0.6)), 0.6, 0.25))]),
    "gloam_snail": dict(
        ambient=[S(lambda r: squelch(r, 0.5, 600, 200, 3) * 0.7), S(lambda r: mixdown(*[at(chirp(r, 0.04, 500, 900) * 0.4, k * 0.12) for k in range(3)]))],
        hurt=[S(squelch, 0.2, 900, 400, 1)],
        death=[S(squelch, 0.7, 700, 120, 5)],
        step=[S(lambda r: squelch(r, 0.3, 400, 200, 0) * 0.6)]),
    "hollow_owl": dict(
        ambient=[S(hoot, 380, 2, 0.34), S(hoot, 360, 3, 0.3)],
        hurt=[S(lambda r: voice(r, 0.3, [(0, 1400), (0.05, 1800), (0.3, 1100)], [(0, 0), (0.02, 1), (0.3, 0)], EE, tilt=1.2, breath=0.6, drive_k=1.5))],
        death=[S(lambda r: reverb(voice(r, 0.9, [(0, 420), (0.9, 260)], [(0, 0), (0.08, 1), (0.9, 0)], OO, tilt=2.4, breath=0.3), 0.6, 0.3))],
        step=[S(pad_step, 0.06, 900)]),
    "thornling": dict(
        ambient=[S(creak, 0.8, 14, 34), S(lambda r: mixdown(chitter(r, 0.4, 14, 1800), creak(r, 0.5, 20, 30) * 0.7))],
        hurt=[S(lambda r: mixdown(noise_burst(r, 0.08, 2600, 1400, [(0, 1), (0.08, 0)]), creak(r, 0.3, 30, 50)))],
        death=[S(lambda r: mixdown(creak(r, 1.0, 10, 40), *[at(noise_burst(r, 0.06, r.uniform(1500, 3500), 1200, [(0, 1), (0.06, 0)]) * 0.6, r.uniform(0, 0.8)) for _ in range(6)]))],
        step=[S(chitter, 0.08, 30, 2200), S(chitter, 0.07, 34, 2600)]),
    "weaver_queen": dict(
        ambient=[S(chitter, 0.7, 20, 2400), S(lambda r: mixdown(chitter(r, 0.5, 26, 2000), hiss(r, 0.6, 3200, 1400) * 0.4))],
        hurt=[S(lambda r: mixdown(chitter(r, 0.3, 40, 3200), hiss(r, 0.3, 4400, 1800) * 0.8))],
        death=[S(lambda r: reverb(mixdown(hiss(r, 1.6, 3000, 1500, [(0, 1), (1.6, 0)]), chitter(r, 1.4, 24, 1800) * 0.8), 0.6, 0.25))],
        step=[S(chitter, 0.12, 50, 2000), S(chitter, 0.1, 55, 2300), S(chitter, 0.12, 45, 1900)],
        roar=[S(lambda r: reverb(mixdown(hiss(r, 1.3, 3800, 2000, [(0, 0), (0.1, 1), (1.0, 0.8), (1.3, 0)]), chitter(r, 1.2, 34, 2600),
                                         at(voice(r, 0.9, [(0, 900), (0.1, 1500), (0.9, 800)], [(0, 0), (0.05, 1), (0.9, 0)], EE, tilt=1.1, drive_k=3.0, growl=(55, 0.6)) * 0.5, 0.15)), 0.7, 0.3))]),
    "petalwing": dict(
        ambient=[S(lambda r: mixdown(wingbeat(r, 0.3, 1700) * 0.6, at(chime(r, 0.5, 2637, decay=10) * 0.4, 0.1)))],
        hurt=[S(lambda r: chirp(r, 0.08, 3800, 3000))],
        death=[S(lambda r: chime(r, 0.9, 1976, decay=5))],
        step=[S(wingbeat, 0.1, 2100)]),
    "honey_bear": dict(
        ambient=[S(lambda r: mixdown(voice(r, 0.7, [(0, 90), (0.7, 80)], [(0, 0), (0.1, 1), (0.7, 0)], OH, growl=(18, 0.6), breath=0.6), at(snort(r, 0.25, 600) * 0.5, 0.6))),
                 S(lambda r: voice(r, 0.5, [(0, 110), (0.5, 95)], [(0, 0), (0.08, 1), (0.5, 0)], OO, breath=0.7, growl=(14, 0.4)))],
        hurt=[S(lambda r: voice(r, 0.4, [(0, 220), (0.06, 300), (0.4, 160)], [(0, 0), (0.02, 1), (0.4, 0)], AH, growl=(34, 0.8), drive_k=2.2))],
        death=[S(lambda r: voice(r, 1.3, [(0, 140), (1.3, 70)], [(0, 0), (0.08, 1), (1.3, 0)], OH, growl=(16, 0.7), breath=0.5))],
        step=[S(thud, 0.2, 80, 40, 0.8, 0.0), S(thud, 0.2, 75, 38, 0.8, 0.0)]),
    "empress_moth": dict(
        ambient=[S(lambda r: mixdown(choir(r, 196, 1.8, 3, OO) * 0.6, wingbeat(r, 0.6, 400, 0.8))),
                 S(lambda r: mixdown(wingbeat(r, 0.7, 350, 1.0), at(wingbeat(r, 0.7, 350, 1.0), 0.8)))],
        hurt=[S(lambda r: mixdown(wingbeat(r, 0.3, 1600), voice(r, 0.3, [(0, 900), (0.06, 1200), (0.3, 700)], [(0, 0), (0.02, 1), (0.3, 0)], EE, tilt=1.8) * 0.6))],
        death=[S(lambda r: choir(r, 220, 2.4, 4, AH) * bp(2.4 + 1.2, [(0, 1), (2.4, 0.3), (3.6, 0)])[: len(choir(r, 220, 2.4, 4, AH))])],
        step=[S(wingbeat, 0.5, 300, 1.2), S(wingbeat, 0.55, 280, 1.3)],
        roar=[S(lambda r: mixdown(choir(r, 262, 2.2, 5, AH), wingbeat(r, 0.9, 300, 1.4) * 0.8, at(wingbeat(r, 0.9, 300, 1.4) * 0.8, 1.0)))]),
    "scree_marmot": dict(
        ambient=[S(lambda r: whistle(r, 2200, 3000, 0.35) * 0.7), S(lambda r: mixdown(whistle(r, 2400, 3200, 0.25) * 0.6, at(whistle(r, 2400, 3200, 0.25) * 0.5, 0.3)))],
        hurt=[S(lambda r: chirp(r, 0.1, 3200, 2400))],
        death=[S(lambda r: whistle(r, 2600, 1200, 0.6) * 0.6)],
        step=[S(pad_step, 0.05, 1200), S(lambda r: mixdown(pad_step(r, 0.05, 1200), noise_burst(r, 0.05, 2500, 1500, [(0, 0.3), (0.05, 0)])))]),
    "peak_kite": dict(
        ambient=[S(lambda r: reverb(mixdown(whistle(r, 2600, 3200, 0.25, (0, 0)), at(whistle(r, 3000, 1900, 0.5, (14, 0.02)), 0.22)) * 0.7, 0.8, 0.35))],
        hurt=[S(lambda r: whistle(r, 3400, 2600, 0.15) * 0.7)],
        death=[S(lambda r: reverb(whistle(r, 3000, 1200, 0.9, (10, 0.02)) * 0.6, 0.8, 0.3))],
        step=[S(wingbeat, 0.3, 700, 0.3)]),
    "crag_ram": dict(
        ambient=[S(bleat, 260, 0.6, 10, AH), S(bleat, 240, 0.5, 9, EH)],
        hurt=[S(bleat, 330, 0.3, 14, EH)],
        death=[S(lambda r: voice(r, 1.0, [(0, 300), (1.0, 150)], [(0, 0), (0.05, 1), (1.0, 0)], AH, nasal=0.4, growl=(10, 0.6), breath=0.3))],
        step=[S(clop, 1.2, 180), S(clop, 1.1, 170), S(clop, 1.2, 190)]),
    "stonehorn": dict(
        ambient=[S(lambda r: voice(r, 1.0, [(0, 110), (0.3, 125), (1.0, 95)], [(0, 0), (0.08, 1), (1.0, 0)], AH, nasal=0.3, growl=(9, 0.6), breath=0.4)),
                 S(lambda r: mixdown(snort(r, 0.4, 600), at(tumble(r, 0.8, 3, 0.4) * 0.4, 0.3)))],
        hurt=[S(lambda r: mixdown(voice(r, 0.4, [(0, 180), (0.06, 240), (0.4, 140)], [(0, 0), (0.02, 1), (0.4, 0)], AH, growl=(28, 0.8), drive_k=2.0),
                                  noise_burst(r, 0.1, 1800, 1000, [(0, 1), (0.1, 0)]) * 0.5))],
        death=[S(lambda r: reverb(mixdown(voice(r, 1.8, [(0, 150), (1.8, 70)], [(0, 0), (0.1, 1), (1.8, 0)], AH, growl=(12, 0.7), breath=0.5), at(tumble(r, 1.6, 10, 1.0), 0.6)), 0.9, 0.3))],
        step=[S(thud, 0.4, 70, 30, 1.2, 0.4, 1100, 0.8), S(thud, 0.4, 66, 28, 1.2, 0.4, 1000, 0.8), S(thud, 0.4, 74, 32, 1.2, 0.35, 1200, 0.7)],
        roar=[S(lambda r: reverb(mixdown(roar_layer(r, 100, 2.0, AH, 22, 2.6, 0.9), at(rumble(r, 2.2, 90, [(0, 0), (0.6, 1), (2.2, 0)]), 0.3), at(tumble(r, 1.8, 12, 1.0) * 0.8, 0.5)), 1.1, 0.3))]),
    "shroomlet": dict(
        ambient=[S(lambda r: mixdown(puff(r, 0.25, 1500) * 0.6, at(chirp(r, 0.06, 1200, 1700) * 0.4, 0.15))), S(lambda r: puff(r, 0.3, 1100) * 0.7)],
        hurt=[S(lambda r: mixdown(puff(r, 0.2, 1800), chirp(r, 0.08, 1600, 2200) * 0.5))],
        death=[S(lambda r: mixdown(puff(r, 0.6, 900), chirp(r, 0.3, 1400, 600) * 0.4))],
        step=[S(pad_step, 0.06, 600)]),
    "sporeback_snail": dict(
        ambient=[S(lambda r: mixdown(squelch(r, 0.5, 500, 180, 2) * 0.6, at(puff(r, 0.3, 900) * 0.4, 0.3)))],
        hurt=[S(squelch, 0.25, 800, 300, 1)],
        death=[S(lambda r: mixdown(squelch(r, 0.8, 600, 110, 4), puff(r, 0.6, 700) * 0.5))],
        step=[S(lambda r: squelch(r, 0.3, 350, 160, 0) * 0.6)]),
    "mycelord": dict(
        ambient=[S(lambda r: reverb(mixdown(creak(r, 1.4, 8, 22), voice(r, 1.4, [(0, 62), (0.7, 70), (1.4, 58)], [(0, 0), (0.3, 1), (1.4, 0)], OO, breath=0.6, tilt=1.6) * 0.8), 0.9, 0.35)),
                 S(lambda r: mixdown(puff(r, 0.8, 600), creak(r, 0.9, 10, 26) * 0.6))],
        hurt=[S(lambda r: mixdown(creak(r, 0.4, 30, 60), noise_burst(r, 0.1, 1500, 800, [(0, 1), (0.1, 0)]) * 0.7, puff(r, 0.3, 1000) * 0.5))],
        death=[S(lambda r: reverb(mixdown(creak(r, 2.2, 6, 30), voice(r, 2.2, [(0, 70), (2.2, 40)], [(0, 0), (0.2, 1), (2.2, 0)], OO, breath=0.7, tilt=1.6),
                                          at(thud(r, 0.6, 60, 25, 1.5, 0.2, 700, 0.8), 1.6)), 1.2, 0.35))],
        step=[S(lambda r: mixdown(thud(r, 0.35, 80, 34, 1.0, 0.2, 700), creak(r, 0.3, 30, 40) * 0.3)),
              S(lambda r: mixdown(thud(r, 0.35, 75, 32, 1.0, 0.2, 650), creak(r, 0.3, 26, 36) * 0.3))],
        roar=[S(lambda r: reverb(mixdown(voice(r, 2.2, [(0, 55), (0.6, 75), (1.6, 70), (2.2, 45)], [(0, 0), (0.3, 1), (1.8, 0.9), (2.2, 0)], OO, breath=0.9, tilt=1.2,
                                                growl=(10, 0.5), drive_k=1.4), creak(r, 2.0, 8, 30) * 0.5, at(puff(r, 1.0, 700) * 0.8, 0.9)), 1.4, 0.4))]),
}

# what each kind is called in the subtitles (vanilla style: "Cow moos")
VERBS = {
    "lumen_moth": ("chimes", "squeaks", "fades"), "bloom_sprite": ("twinkles", "squeaks", "fades"), "glass_scarab": ("clicks", "cracks", "shatters"),
    "frostfinch": ("chirps", "squeaks", "dies"), "mossback": ("grumbles", "grunts", "dies"), "dune_ray": ("sings", "chirps", "dies"),
    "frost_stag": ("bleats", "bleats", "dies"), "gloomstalker": ("growls", "snarls", "dies"), "wisp_hound": ("howls", "yelps", "dies"),
    "briar_sow": ("snorts", "squeals", "dies"), "dune_wyrm": ("rumbles", "screeches", "dies"), "old_antler": ("groans", "snorts", "dies"),
    "bogwick": ("hums", "buzzes", "goes dark"), "mudskipper": ("burbles", "squeaks", "dies"), "bog_toad": ("croaks", "croaks", "dies"),
    "mire_mother": ("croaks", "gulps", "dies"), "dart_frog": ("trills", "peeps", "dies"), "crested_basilisk": ("chitters", "hisses", "dies"),
    "canopy_prowler": ("growls", "snarls", "dies"), "plumed_coil": ("rattles", "hisses", "dies"), "gloam_snail": ("squelches", "squishes", "dies"),
    "hollow_owl": ("hoots", "screeches", "dies"), "thornling": ("creaks", "cracks", "splinters"), "weaver_queen": ("chitters", "shrieks", "dies"),
    "petalwing": ("flutters", "squeaks", "fades"), "honey_bear": ("grumbles", "growls", "dies"), "empress_moth": ("hums", "flutters", "dies"),
    "scree_marmot": ("whistles", "squeaks", "dies"), "peak_kite": ("cries", "cries", "dies"), "crag_ram": ("bleats", "bleats", "dies"),
    "stonehorn": ("rumbles", "grunts", "dies"), "shroomlet": ("puffs", "squeaks", "deflates"), "sporeback_snail": ("squelches", "squishes", "dies"),
    "mycelord": ("creaks", "cracks", "topples"),
}
ROARS = {"briar_sow": "roars", "dune_wyrm": "bellows", "old_antler": "bugles", "mire_mother": "booms", "plumed_coil": "rattles its plumes",
         "weaver_queen": "shrieks", "empress_moth": "cries out", "stonehorn": "bellows", "mycelord": "moans"}
BEASTS = set(ROARS)
SMALL = {"lumen_moth", "bloom_sprite", "glass_scarab", "frostfinch", "bogwick", "mudskipper", "dart_frog", "gloam_snail", "petalwing",
         "scree_marmot", "peak_kite", "shroomlet", "sporeback_snail"}


def names():
    """Creature display names, read from the bestiaries (the lang the fauna specs write)."""
    sys.path.insert(0, HERE)
    import fauna as F1
    import fauna2 as F2
    return {m["id"]: m["name"] for m in F1.fauna() + F2.creatures()}


# ---------------------------------------------------------------------------------------------------------------------
# Writing

def encode(x, path):
    x = np.asarray(x, dtype=np.float64)
    x = x - np.mean(x)
    peak = np.max(np.abs(x)) + 1e-9
    x = fade(x / peak * 0.89, 0.002, 0.02)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    # synthesised at 22.05 kHz, stored at 44.1 kHz like every other mod sound (the audio tests require it)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "pipe:0",
                    "-ar", "44100", "-c:a", "libvorbis", "-q:a", "3", path], input=pcm, check=True)


def render_all():
    """{(creature, kind): [arrays]} with fixed seeds."""
    out = {}
    for cid, kinds in CREATURES.items():
        for kind, fns in kinds.items():
            arrs = []
            for k, fn in enumerate(fns):
                rng = np.random.default_rng(abs(hash_str(f"{cid}/{kind}/{k}")) % (2 ** 32))
                arrs.append(fn(rng))
            out[(cid, kind)] = arrs
    return out


def hash_str(s):
    h = 2166136261
    for ch in s:
        h = ((h ^ ord(ch)) * 16777619) & 0xFFFFFFFF
    return h


def volume(cid, kind):
    small = cid in SMALL
    beast = cid in BEASTS
    return {"ambient": 0.5 if small else 1.0 if beast else 0.8, "hurt": 0.6 if small else 1.0, "death": 0.7 if small else 1.0,
            "step": 0.25 if small else 0.7 if beast else 0.45, "roar": 1.0}[kind]


def build_sounds():
    nm = names()
    missing = [c for c in nm if c not in CREATURES]
    if missing:
        raise SystemExit("no voice for: " + ", ".join(missing))
    rendered = render_all()
    sounds = {}
    lang = {}
    count = 0
    for (cid, kind), arrs in sorted(rendered.items()):
        entries = []
        for k, x in enumerate(arrs):
            rel = f"fauna/{cid}/{kind}{k + 1}"
            encode(x, os.path.join(SND, cid, f"{kind}{k + 1}.ogg"))
            count += 1
            e = {"name": "awaken:" + rel, "volume": volume(cid, kind)}
            if kind == "roar":
                e["attenuation_distance"] = 48
            elif cid in BEASTS and kind in ("death", "ambient"):
                e["attenuation_distance"] = 24
            entries.append(e)
        key = f"fauna.{cid}.{kind}"
        ev = {"sounds": entries}
        if kind == "step":
            # one shared line, like vanilla's "Footsteps" for every mob
            ev["subtitle"] = "subtitles.awaken.fauna.step"
            lang["subtitles.awaken.fauna.step"] = "Footsteps"
        else:
            ev["subtitle"] = "subtitles.awaken." + key
            name = nm.get(cid, cid)
            if kind == "roar":
                lang["subtitles.awaken." + key] = f"{name} {ROARS[cid]}"
            else:
                verb = VERBS[cid][("ambient", "hurt", "death").index(kind)]
                lang["subtitles.awaken." + key] = f"{name} {verb}"
        sounds[key] = ev
    os.makedirs(MERGE, exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(dict(sorted(sounds.items())), f, indent=2)
        f.write("\n")
    path = os.path.join(MERGE, "lang/en_us.json")
    merged = json.load(open(path)) if os.path.exists(path) else {}
    merged.update(lang)
    with open(path, "w") as f:
        json.dump(dict(sorted(merged.items())), f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"fauna sounds: {len(sounds)} events, {count} files")


def sheet(outdir):
    """Spectrograms of every sound, one row per creature: to judge envelopes, pitch and noise at a glance."""
    from PIL import Image, ImageDraw
    os.makedirs(outdir, exist_ok=True)
    rendered = render_all()
    cids = list(CREATURES)
    kinds = ["ambient", "hurt", "death", "step", "roar"]
    cw, ch = 220, 90
    img = Image.new("RGB", (120 + cw * len(kinds), ch * len(cids)), (16, 18, 24))
    d = ImageDraw.Draw(img)
    for r_, cid in enumerate(cids):
        d.text((4, r_ * ch + 4), cid, fill=(220, 225, 235))
        for c_, kind in enumerate(kinds):
            arrs = rendered.get((cid, kind))
            if not arrs:
                continue
            x = arrs[0]
            x = x / (np.max(np.abs(x)) + 1e-9)
            nfft, hop = 512, 128
            frames = max(1, (len(x) - nfft) // hop)
            S_ = np.array([np.abs(np.fft.rfft(x[i * hop: i * hop + nfft] * np.hanning(nfft))) for i in range(frames)]) if len(x) > nfft else np.zeros((1, nfft // 2 + 1))
            S_ = 20 * np.log10(S_ + 1e-6)
            S_ = np.clip((S_ - S_.max() + 60) / 60, 0, 1)
            spec = Image.fromarray((S_.T[::-1] * 255).astype("uint8")).resize((cw - 6, ch - 6))
            img.paste(spec.convert("RGB"), (120 + c_ * cw, r_ * ch + 3))
            d.text((122 + c_ * cw, r_ * ch + 4), f"{kind} {len(x) / SR:.2f}s", fill=(255, 220, 120))
    p = os.path.join(outdir, "fauna_sounds.png")
    img.save(p)
    print(p)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "build":
        build_sounds()
    elif len(sys.argv) > 1 and sys.argv[1] == "sheet":
        sheet(sys.argv[2] if len(sys.argv) > 2 else "/tmp/fauna_sounds")
    else:
        print(__doc__)
