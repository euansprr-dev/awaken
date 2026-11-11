#!/usr/bin/env python3
"""Reads the pose log a tour wrote (the pilot's "handslog on", SPEC 65.2) and says whether the hands held still.

    python3 tools/anims/handslog.py run-autopilot/screenshots-ci/hands-smooth-<run>/tour.log

For every stretch where the pointer was left on a slot ("hover" mark to the next mark, less a settle): how far the fist,
the arm's line, its stretch, the eye and the look each moved, in the hand pass's own units (a fist is 0.25 across).
For every sweep: how many frames, the fist's speed frame to frame, how far the arm's line turned in one frame and how
often the fist went back on itself. For every touch (a chest's lid): how long a hand was in the view and how high.
Exits 1 if a held hand moved more than a thousandth of a fist, 2 if no held stretch was found to measure.
"""
import math
import re
import sys

SETTLE_MS = 500


def read(path):
    rows = []
    for line in open(path, errors='replace'):
        m = re.search(r'\[handslog\] mark ms=(\d+) (.*)', line)
        if m:
            rows.append(dict(ms=int(m.group(1)), mark=m.group(2).strip()))
            continue
        m = re.search(r'\[handslog\] ms=(\d+) scene=(\S+) eye=([-\d.,]+) fov=([\d.]+) look=([-\d.,]+)(.*)', line)
        if not m:
            continue
        arms = {}
        for a in re.finditer(r'\| ([RL]) view=([-\d.,]+) line=([-\d.,]+) stretch=([\d.]+) far=([\d.]+)(?: world=([-\d.,]+) out=([\d.]+))?', m.group(6)):
            arms[a.group(1)] = dict(view=tuple(map(float, a.group(2).split(','))), line=tuple(map(float, a.group(3).split(','))),
                                    stretch=float(a.group(4)), far=float(a.group(5)), out=float(a.group(7)) if a.group(7) else None)
        rows.append(dict(ms=int(m.group(1)), scene=m.group(2), eye=tuple(map(float, m.group(3).split(','))), fov=float(m.group(4)),
                         look=tuple(map(float, m.group(5).split(','))), arms=arms))
    return rows


def dist(a, b):
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))


def main(path):
    rows = read(path)
    frames = [r for r in rows if 'arms' in r]
    marks = [r for r in rows if 'mark' in r]
    print(f'{len(frames)} frames with an arm drawn, {len(marks)} marks')
    worst, held = 0.0, 0
    print('held still (after each hover, to the next mark):')
    for i, mk in enumerate(marks):
        if not mk['mark'].startswith('hover'):
            continue
        end = marks[i + 1]['ms'] if i + 1 < len(marks) else mk['ms'] + 10_000
        w = [f for f in frames if mk['ms'] + SETTLE_MS <= f['ms'] < end and len(f['arms']) == 1 and all(a['out'] is None or a['out'] >= 0.9999 for a in f['arms'].values())]
        if len(w) < 6:
            continue
        k = next(iter(w[0]['arms']))
        w = [f for f in w if k in f['arms']]
        a0 = w[0]['arms'][k]
        dv = max(dist(f['arms'][k]['view'], a0['view']) for f in w)
        dl = max(dist(f['arms'][k]['line'], a0['line']) for f in w)
        ds = max(abs(f['arms'][k]['stretch'] - a0['stretch']) for f in w)
        de = max(dist(f['eye'], w[0]['eye']) for f in w)
        dk = math.degrees(max(dist(f['look'], w[0]['look']) for f in w))
        worst = max(worst, dv)
        held += 1
        print(f"  {w[0]['scene']:8s} {mk['mark']:9s} fov {w[0]['fov']:5.1f}: {len(w):3d} frames over {(w[-1]['ms'] - w[0]['ms']) / 1000:4.1f} s: fist moved {dv:.5f}, line {dl:.5f}, "
              f"stretch {ds:.4f}; eye {de:.4f}, look {dk:.3f} deg  (stretch {a0['stretch']:.2f})")
    print('moving (each sweep):')
    for mk in marks:
        if not mk['mark'].startswith('sweep'):
            continue
        ticks = int(mk['mark'].split()[3])
        w = [f for f in frames if mk['ms'] <= f['ms'] <= mk['ms'] + ticks * 50 + 100 and f['arms']]
        if len(w) < 4:
            continue
        k = next(iter(w[0]['arms']))
        w = [f for f in w if k in f['arms']]
        speeds, turns, back, prev = [], [], 0, None
        for a, b in zip(w, w[1:]):
            d = [y - x for x, y in zip(a['arms'][k]['view'], b['arms'][k]['view'])]
            n = math.sqrt(sum(x * x for x in d))
            dt = max(1, b['ms'] - a['ms']) / 1000
            speeds.append(n / dt)
            turns.append(math.degrees(math.acos(max(-1, min(1, sum(x * y for x, y in zip(a['arms'][k]['line'], b['arms'][k]['line'])))))))
            if prev is not None and n > 1e-4 and sum(x * y for x, y in zip(d, prev)) < 0:
                back += 1
            if n > 1e-4:
                prev = d
        sp = sorted(speeds)
        print(f"  {w[0]['scene']:8s} {mk['mark']:14s}: {len(w):3d} frames; fist speed median {sp[len(sp) // 2]:.2f}/s, most {sp[-1]:.2f}/s; "
              f"the line turned at most {max(turns):.2f} deg in a frame; went back on itself {back} times; stretch {min(f['arms'][k]['stretch'] for f in w):.2f} to {max(f['arms'][k]['stretch'] for f in w):.2f}")
    print('touches (a hand to a thing):')
    run = []
    for f in frames + [None]:
        if f is not None and f['scene'].startswith('fp_') and (not run or f['ms'] - run[-1]['ms'] < 700):
            run.append(f)
            continue
        if run:
            ys = [a['view'][1] for g in run for a in g['arms'].values()]
            print(f"  {run[0]['scene']:8s}: {len(run):2d} frames, a hand in the view for {(run[-1]['ms'] - run[0]['ms'])} ms; its fist from {min(ys):+.2f} to {max(ys):+.2f} of the view's height from the middle "
                  f"(hand pass units at 0.75 out: +0.5 is the top edge)")
        run = [f] if f is not None and f['scene'].startswith('fp_') else []
    if held == 0:
        print('NOTHING MEASURED: no stretch with the pointer left on a slot (was the log switched on, and are there hover marks?)')
        return 2
    if worst > 0.00025:
        print(f'FAIL: a held hand moved {worst:.5f} (a fist is 0.25)')
        return 1
    print('ok: every held hand was still')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
