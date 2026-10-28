"""Quiet, reproducible sound design from the licensed lossless recordings in sources/ritual.
No downloads, playback or pure-tone layer. Real rock, friction, electrical arc and air pressure
supply the texture; filtering, resampling, envelopes and sparse room reflections shape the scene.
"""
from pathlib import Path
import hashlib
import json
import sys
import numpy as np
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import audiokit as K

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / 'tools/art/sources/ritual'
PROVENANCE = json.loads((SOURCE / 'sources.json').read_text())
CACHE = {}
NAMES = {'bell','doors','flames_out','eyes','gaze_charge','gaze_fire','gaze_travel','gaze_impact',
         'statue_rise','swing','impact','pit','rumble','close','footstep1','footstep2','footstep3','hall'}


def source(id):
    if id not in CACHE:
        row = PROVENANCE[id]
        p = SOURCE / row['file']
        assert hashlib.sha256(p.read_bytes()).hexdigest() == row['sha256'], p
        CACHE[id] = K.mono(K.read_audio(str(p)))
    return CACHE[id]


def take(id, start, seconds, rate=1):
    raw = source(id)
    x = raw[K.ns(start):min(len(raw), K.ns(start + seconds * rate))].copy()
    if rate != 1:
        x = np.interp(np.arange(0, len(x) - 1, rate), np.arange(len(x)), x)
    x = np.pad(x[:K.ns(seconds)], (0, max(0, K.ns(seconds) - len(x))))
    x -= np.mean(x)
    return x / (max(np.max(abs(x)), 1e-12))


def env(x, points):
    t, g = zip(*points)
    assert all(a <= b for a, b in zip(t, t[1:])), points
    return x * np.interp(np.arange(len(x)) / K.SR, t, g)


def add(dst, x, when=0, gain=1):
    i = K.ns(when)
    n = min(len(x), len(dst) - i)
    if n > 0: dst[i:i+n] += x[:n] * gain


def room(x, taps=((.17,.16),(.31,.10),(.53,.05))):
    out = x.copy()
    reflected = K.lowpass(x, 2100, 2)
    for delay, gain in taps: add(out, reflected, delay, gain)
    return out


def rubble(seconds, variant=0):
    # Separate physical stone impacts, aligned just before each recording's main contact.
    start = (1.67, 11.65, 16.51)[variant % 3]
    x = take('1022', start, seconds, .68 + .05 * (variant % 3))
    return env(x, [(0,0),(.008,1),(.12,.9),(min(.55,seconds*.75),.23),(seconds,0)])


def pressure(seconds):
    return env(K.lowpass(take('1023', 0, seconds, .55), 180, 3), [(0,0),(.009,1),(.20,.5),(seconds,0)])


def air(seconds, rise=False):
    x = K.highpass(K.lowpass(take('0595',26.6,seconds),5200,2),160,2)
    return env(x, [(0,0),(.7*seconds,1),(seconds,0)] if rise else [(0,0),(.06,1),(seconds,0)])


def render(name):
    assert name in NAMES
    peak = -9.5
    if name == 'bell':
        x = take('3446',0,8.5,.9);peak=-9
    elif name.startswith('footstep'):
        v = int(name[-1])-1
        x = np.zeros(K.ns(1.45))
        add(x,rubble(1.35,v),gain=.8)
        add(x,pressure(1.2),gain=.65)
        add(x,K.lowpass(take('0580',8+.3*v,.8,.7),650,2),.03,.12)
        x = room(x, ((.12,.10),(.26,.055)))
        peak=-12
    elif name in ('impact','gaze_impact','pit'):
        duration = 3.6 if name != 'pit' else 4.2
        x=np.zeros(K.ns(duration))
        add(x,rubble(2.4,1),gain=.85)
        add(x,pressure(2),gain=.85)
        tail=K.lowpass(take('3113',2.15,duration-.15,.6),260,3)
        add(x,env(tail,[(0,0),(.15,.7),(duration-.15,0)]),.15,.20)
        if name == 'gaze_impact': add(x,K.highpass(take('0950',.35,.55,.8),1200,2),.02,.12)
        x=room(x);peak=-8.5
    elif name == 'doors':
        x=np.zeros(K.ns(4.8))
        grind=take('0580',7.8,1.4,.62)
        add(x,env(grind,[(0,0),(.12,.4),(1.25,.85),(1.4,0)]),gain=.55)
        add(x,rubble(2.9,0),1.4,.9)
        add(x,pressure(2.5),1.4,.75)
        x=room(x);peak=-9
    elif name == 'statue_rise':
        x=env(take('0580',10.8,3.5,.65),[(0,0),(.2,.35),(1.6,.85),(2.3,.4),(3.5,0)])
        add(x,K.lowpass(take('3113',2.15,2.9,.7),220,3),.2,.2)
        add(x,rubble(1,2),2.3,.35)
        x=room(x);peak=-12
    elif name == 'gaze_charge':
        # Exact 45-tick warning: the energy grows into the release, not over it.
        x=air(2.25,True)*.35
        arc=K.highpass(K.lowpass(take('0950',.32,2.25,.85),3600,2),350,2)
        add(x,env(arc,[(0,0),(.4,.04),(1.8,.8),(2.15,1),(2.25,0)]),gain=.4)
        peak=-15
    elif name == 'gaze_fire':
        x=np.zeros(K.ns(2.5))
        add(x,pressure(1.4),gain=.9)
        add(x,air(1.3),gain=.4)
        arc=K.highpass(take('0950',.35,.85,.65),650,2)
        add(x,env(arc,[(0,0),(.02,.7),(.12,.9),(.85,0)]),gain=.23)
        x=room(x);peak=-8.5
    elif name == 'gaze_travel':
        x=air(.6)*.5
        add(x,K.highpass(take('0950',4,.6,.8),1500,2),gain=.06)
        peak=-18
    elif name == 'eyes':
        x=air(4,True)*.45
        add(x,env(K.lowpass(take('3113',2.1,4,.65),320,3),[(0,0),(2.75,.8),(2.9,1),(4,0)]),gain=.55)
        x=room(x);peak=-12
    elif name == 'swing':
        x=air(.72)
        add(x,K.highpass(take('0580',13.6,.55,.8),350,2),gain=.15)
        x=env(x,[(0,0),(.12,.3),(.28,1),(.72,0)]);peak=-15
    elif name == 'rumble':
        x=env(K.lowpass(take('3113',2.1,3.4,.65),200,3),[(0,0),(.25,.7),(1.1,1),(3.4,0)]);peak=-17
    elif name == 'close':
        x=np.zeros(K.ns(.5))
        add(x,pressure(.4),.06,.9)
        add(x,rubble(.35,2),.06,.7)
        x=env(x,[(0,0),(.06,1),(.3,.8),(.46,0),(.5,0)]);peak=-7.5
    elif name == 'flames_out':
        x=air(1.4);peak=-17
    elif name == 'hall':
        x=K.lowpass(take('0595',28,24),350,3)
        x+=K.lowpass(take('0580',0,24),130,3)*.035
        x=K.loop_seamless(np.stack([x,np.roll(x,K.ns(.023))],axis=1),1)
        peak=-29
    x=K.highpass(x,30,2)
    x=K.declick(x,.003,.06) if name != 'hall' else x
    x *= 10**((peak-K.true_peak(x))/20)
    return x
