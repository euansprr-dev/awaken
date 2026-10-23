"""Animation helpers for the hero rigs (heroes.py): sampled cycles for gaits and swims, and small key builders.

No build step (build(out) is a no-op so build_all.py can import it).
"""
import math


def build(out):
    pass


TAU = math.pi * 2


def cycle(clip, bone, target, fn, n=8, phase=0.0):
    """Samples fn(p) over one loop (p in 0..1, offset by phase) into n+1 Catmull-Rom keys; the last key equals the first."""
    keys = []
    for k in range(n + 1):
        p = (k / n + phase) % 1.0
        v = fn(p)
        keys.append((clip.length * k / n,) + tuple(v))
    # close the loop exactly
    keys[-1] = (clip.length,) + tuple(keys[0][1:])
    getattr(clip, target)(bone, keys)


def s(p, ph=0.0):
    return math.sin((p + ph) * TAU)


def c(p, ph=0.0):
    return math.cos((p + ph) * TAU)


def quad_gait(clip, legs, swing, knee=0.0, lift=0.0, n=8):
    """legs: [(upper, lower or None, phase)], swing degrees; the lower bone bends while the leg swings forward."""
    for upper, lower, ph in legs:
        cycle(clip, upper, "rot", lambda p, ph=ph: (swing * s(p, ph), 0, 0), n)
        if lower:
            # bend the knee/hock during the forward swing (when sin is rising)
            cycle(clip, lower, "rot", lambda p, ph=ph: (knee * max(0.0, c(p, ph)), 0, 0), n)
        if lift:
            cycle(clip, upper, "pos", lambda p, ph=ph: (0, lift * max(0.0, c(p, ph)), 0), n)


def body_bob(clip, bone, amp=0.6, pitch=0.0, roll=0.0, freq=2, n=8, ph=0.0):
    cycle(clip, bone, "pos", lambda p: (0, amp * c(p * freq, ph), 0), n)
    if pitch or roll:
        cycle(clip, bone, "rot", lambda p: (pitch * s(p * freq, ph), 0, roll * s(p, ph)), n)


def wave(clip, bones, axis, amp, lag=0.12, n=8, amps=None, ph0=0.0):
    """A travelling wave down a chain (serpent swim, tail sway): bone k lags k*lag of a cycle."""
    for k, b in enumerate(bones):
        a = amps[k] if amps else amp

        def fn(p, k=k, a=a):
            v = a * s(p, ph0 - k * lag)
            return (v, 0, 0) if axis == "x" else (0, v, 0) if axis == "y" else (0, 0, v)
        cycle(clip, b, "rot", fn, n)


def hold(t, *v):
    return (t,) + v


def ease_keys(points, interp="cat"):
    """[(t, x, y, z)] passthrough with an interpolation tag."""
    return [p + (interp,) for p in points]
