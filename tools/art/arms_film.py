#!/usr/bin/env python3
"""Reads an arms film (the autopilot's `arms film`, SPEC §25 "The arms film") and says what the frames can't.

    python3 tools/art/arms_film.py report <clip dir> [...]       timing, speed and jumps per take, as text
    python3 tools/art/arms_film.py svg <clip dir> <out.svg>      the blades' paths and speed over the take, events marked
    python3 tools/art/arms_film.py strip <clip dir> <out.png> <from gt> <to gt> [cols]   the pictures in that span
    python3 tools/art/arms_film.py video <clip dir> <out.mp4> [every] [fps]   the take stamped (time, move, press, impact)

A clip dir holds track.tsv (a row per frame drawn), events.tsv and the pictures with times.txt. Game time (gt) is the
client's ticks; at play's 20 a second a tick is three 60 fps frames, so speeds are given as the share of the screen a
blade's tip crosses in one 60 fps frame of play ("/f"), whatever /tick rate the film ran at. First person is projected
as the hands are drawn (70 degree fov), third person through the camera's own fov and turn.
"""
import math
import os
import sys

HAND_FOV = 70.0
FRAME = 1.0 / 3.0  # a 60 fps frame in ticks


def load(d):
    rows = []
    with open(os.path.join(d, 'track.tsv')) as f:
        head = f.readline().rstrip('\n').split('\t')
        for line in f:
            a = line.rstrip('\n').split('\t')
            if len(a) != len(head):
                continue
            r = dict(zip(head, a))
            for k in head:
                if k not in ('move',):
                    try:
                        r[k] = float(r[k])
                    except ValueError:
                        pass
            rows.append(r)
    events = []
    p = os.path.join(d, 'events.tsv')
    if os.path.exists(p):
        with open(p) as f:
            f.readline()
            for line in f:
                a = line.rstrip('\n').split('\t')
                if len(a) < 4:
                    continue
                events.append({'ms': float(a[0]), 'gt': float(a[1]) if a[1] else None, 'side': a[2], 'what': a[3]})
    # server rows carry only real time: placed on the film's clock by the frames around them
    for e in events:
        if e['gt'] is None:
            e['gt'] = gt_at(rows, e['ms'])
    events.sort(key=lambda e: e['gt'])
    return rows, events


def gt_at(rows, ms):
    if not rows:
        return 0.0
    if ms <= rows[0]['real_ms']:
        return rows[0]['gt']
    for a, b in zip(rows, rows[1:]):
        if a['real_ms'] <= ms <= b['real_ms']:
            u = 0 if b['real_ms'] == a['real_ms'] else (ms - a['real_ms']) / (b['real_ms'] - a['real_ms'])
            return a['gt'] + (b['gt'] - a['gt']) * u
    return rows[-1]['gt']


def project(r, pre):
    """A blade's tip and base on the screen (ndc, -1..1, y up), or None when it isn't drawn this frame."""
    if not r.get(pre + '_has'):
        return None
    aspect = r['w'] / max(1.0, r['h'])
    pts = []
    for k in ('t', 'b'):
        x, y, z = r[pre + '_' + k + 'x'], r[pre + '_' + k + 'y'], r[pre + '_' + k + 'z']
        if pre in ('m', 'o'):
            fov = HAND_FOV
            vx, vy, vz = x, y, -z
        else:
            fov = r['fov']
            yaw, pitch = math.radians(r['yaw']), math.radians(r['pitch'])
            fx, fy, fz = -math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)
            rx, ry, rz = -math.cos(yaw), 0.0, -math.sin(yaw)  # the camera's right
            ux, uy, uz = ry * fz - rz * fy, rz * fx - rx * fz, rx * fy - ry * fx
            vx, vy, vz = x * rx + y * ry + z * rz, x * ux + y * uy + z * uz, x * fx + y * fy + z * fz
        if vz <= 0.01:
            return None
        t = math.tan(math.radians(fov) / 2)
        pts.append((vx / (vz * t * aspect), vy / (vz * t)))
    return pts[0], pts[1]


def series(rows, pre):
    """(gt, tip, base) for every frame the blade is drawn."""
    out = []
    for r in rows:
        p = project(r, pre)
        if p:
            out.append((r['gt'], p[0], p[1]))
    return out


def speeds(s):
    """The tip's speed per 60 fps frame of play (share of the screen: ndc / 2), at the middle of each step."""
    out = []
    for (t0, a, _), (t1, b, _) in zip(s, s[1:]):
        dt = t1 - t0
        if dt <= 1e-4:
            continue
        d = math.hypot(b[0] - a[0], b[1] - a[1]) / 2
        out.append(((t0 + t1) / 2, d / dt * FRAME, dt))
    return out


def strikes(events):
    """Each press with what followed it on both sides, until the next press."""
    out = []
    cur = None
    for e in events:
        w = e['what']
        if e['side'] == 'c' and w.startswith('press '):
            cur = {'press': e['gt'], 'items': []}
            out.append(cur)
        if cur is not None:
            cur['items'].append(e)
    return out


_ROWS = {}


def windup(d, move):
    """The move's wind-up in ticks (archetypes.json), the style read from the clip's name (daggers_1p_slow...)."""
    style = os.path.basename(d.rstrip('/')).split('_')[0]
    if style not in _ROWS:
        here = os.path.dirname(os.path.abspath(__file__))
        import json
        with open(os.path.join(here, '..', '..', 'src/main/resources/data/awaken/arms/archetypes.json')) as f:
            for a in json.load(f)['archetypes']:
                _ROWS[a['id']] = a['moves']
    m = _ROWS.get(style, {}).get(move)
    return None if m is None else m['windup']


def timeline(d, rows, events):
    """Each press by its number: the strike it began (here and on the server), the release, the blade reaching the
    target on screen (the first frame the hands' clock passes the move's wind-up), the server's hit and the impact."""
    seqs = []
    by = {}
    for e in events:
        w = e['what'].split()
        if e['side'] == 'c' and w[0] == 'press':
            n = int(w[2])
            by[n] = {'seq': n, 'press': e['gt'], 'begin': None, 'move': None, 'waits': False, 'tap': None, 'sbegin': None,
                     'shit': None, 'impact': None, 'contact': None, 'refused': False}
            seqs.append(n)
        elif w[0] == 'begin' and 'seq' in w:
            n = int(w[w.index('seq') + 1])
            if n in by:
                x = by[n]
                if e['side'] == 'c' and x['begin'] is None:
                    x['begin'], x['move'], x['waits'] = e['gt'], w[1], 'waits' in w
                elif e['side'] == 's' and x['sbegin'] is None:
                    x['sbegin'] = e['gt']
        elif e['side'] == 'c' and w[0] == 'refused':
            n = int(w[2])
            if n in by:
                by[n]['refused'] = True
    # a release belongs to the latest press before it; a server hit to the latest strike the server began before it with
    # that move; an impact to the first hit not yet shown
    order = [by[n] for n in seqs]
    for e in events:
        w = e['what'].split()
        if e['side'] == 'c' and w[0] == 'tap':
            last = [x for x in order if x['press'] <= e['gt'] + 1e-6]
            if last and last[-1]['tap'] is None:
                last[-1]['tap'] = e['gt']
        elif e['side'] == 's' and w[0] == 'hit':
            cands = [x for x in order if x['sbegin'] is not None and x['sbegin'] <= e['gt'] + 1e-6 and x['move'] == w[1]]
            if cands and cands[-1]['shit'] is None:
                cands[-1]['shit'] = e['gt']
    shown = [e for e in events if e['side'] == 'c' and e['what'].startswith('impact ')]
    hits = sorted((x for x in order if x['shit'] is not None), key=lambda x: x['shit'])
    for x, e in zip(hits, shown):
        x['impact'] = e['gt']
    for x in order:
        wu = windup(d, x['move']) if x['move'] else None
        if x['begin'] is not None and wu is not None:
            for r in rows:
                if r['gt'] >= x['begin'] and r['move'] == x['move'] and r['view'] >= wu:
                    x['contact'] = r['gt']
                    break
        f = lambda v: '-' if v is None else f'+{(v - x["press"]) * 50:.0f}'
        x['text'] = (f'press {x["seq"]} @{x["press"]:.2f}: {x["move"] or "(none)":9s} begun {f(x["begin"])}'
                     f'{" waits" if x["waits"] else ""} released {f(x["tap"])} blade there {f(x["contact"])} | server begun '
                     f'{f(x["sbegin"])} hit {f(x["shit"])} | impact shown {f(x["impact"])} (ms of play)' + (' REFUSED' if x['refused'] else ''))
    return order


def report(d):
    rows, events = load(d)
    name = os.path.basename(d.rstrip('/'))
    if not rows:
        print(name, 'no frames')
        return
    span = rows[-1]['gt'] - rows[0]['gt']
    pics = sum(1 for r in rows if r['pic'] >= 0)
    real = (rows[-1]['real_ms'] - rows[0]['real_ms']) / 1000
    print(f'== {name}: {len(rows)} frames over {span:.1f} ticks ({len(rows) / max(span, 1e-3):.1f} a tick; {len(rows) / max(real, 1e-3):.1f} fps '
          f'real), {pics} pictures')
    third = rows[0]['third'] > 0
    for pre, label in (('m3', 'main 3p'), ('o3', 'twin 3p')) if third else (('m', 'main'), ('o', 'twin')):
        s = series(rows, pre)
        if len(s) < 3:
            continue
        sp = speeds(s)
        top = sorted(sp, key=lambda x: -x[1])[:6]
        print(f'  {label}: drawn {len(s)} frames; fastest tip /f: ' + ', '.join(f'{v:.2f}@{t:.2f}' for t, v, _ in top))
        # a jump: a step at least 2.5x both neighbours and over 0.15 of the screen a frame
        jumps = []
        for i in range(1, len(sp) - 1):
            if sp[i][1] > 0.15 and sp[i][1] > 2.5 * max(sp[i - 1][1], sp[i + 1][1], 0.02):
                jumps.append(sp[i])
        if jumps:
            print(f'  {label} spikes (a frame much faster than both neighbours): ' + ', '.join(f'{v:.2f}@{t:.2f}' for t, v, _ in jumps))
    for line in timeline(d, rows, events):
        print('  ' + line['text'])
    tl = timeline(d, rows, events)
    begun = [x for x in tl if x['contact'] is not None]
    if begun:
        pc = [x['contact'] - x['press'] for x in begun]
        print(f'  presses {len(tl)}, strikes begun {sum(1 for x in tl if x["begin"] is not None)}, waited at the set {sum(1 for x in tl if x["waits"])}; '
              f'press to contact on screen: mean {sum(pc) / len(pc) * 50:.0f} ms of play, worst {max(pc) * 50:.0f}')
    lag = [x['impact'] - x['contact'] for x in tl if x['impact'] is not None and x['contact'] is not None]
    if lag:
        print(f'  impacts {len(lag)}: shown after the blade reached the target by mean {sum(lag) / len(lag) * 50:.0f} ms of play, '
              f'worst {max(lag) * 50:.0f}, least {min(lag) * 50:.0f}')
    srcs = [e for e in events if e['what'].startswith('source ')]
    print(f'  source changes: {len(srcs)}; ' + ', '.join(f'{e["what"].split()[1]}@{e["gt"]:.2f}' for e in srcs[:24]))


def svg(d, out):
    rows, events = load(d)
    third = rows[0]['third'] > 0
    pres = (('m3', '#3b82f6'), ('o3', '#f97316')) if third else (('m', '#3b82f6'), ('o', '#f97316'))
    t0, t1 = rows[0]['gt'], rows[-1]['gt']
    W, H, L = 1100, 520, 60
    X = lambda t: L + (t - t0) / max(1e-3, t1 - t0) * (W - L - 10)
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" font-family="system-ui" font-size="11">',
             f'<rect width="{W}" height="{H}" fill="#111"/>']
    bands = [('tip x on screen', 10, 150, lambda p: p[1][0]), ('tip y on screen', 175, 150, lambda p: p[1][1])]
    for title, y0, h, f in bands:
        parts.append(f'<text x="4" y="{y0 + 12}" fill="#aaa">{title}</text>')
        parts.append(f'<line x1="{L}" x2="{W - 10}" y1="{y0 + h / 2}" y2="{y0 + h / 2}" stroke="#333"/>')
        for pre, col in pres:
            s = series(rows, pre)
            pts = ' '.join(f'{X(t):.1f},{y0 + h / 2 - max(-1.4, min(1.4, f((t, tip, base)))) * h / 2.8:.1f}' for t, tip, base in s)
            parts.append(f'<polyline points="{pts}" fill="none" stroke="{col}" stroke-width="1.5"/>')
    y0, h = 345, 150
    parts.append(f'<text x="4" y="{y0 + 12}" fill="#aaa">tip speed /f</text>')
    for v in (0.1, 0.2, 0.3):
        yy = y0 + h - v / 0.4 * h
        parts.append(f'<line x1="{L}" x2="{W - 10}" y1="{yy:.1f}" y2="{yy:.1f}" stroke="#2a2a2a"/><text x="{L - 28}" y="{yy + 4:.1f}" fill="#666">{v}</text>')
    for pre, col in pres:
        sp = speeds(series(rows, pre))
        pts = ' '.join(f'{X(t):.1f},{y0 + h - min(0.4, v) / 0.4 * h:.1f}' for t, v, _ in sp)
        parts.append(f'<polyline points="{pts}" fill="none" stroke="{col}" stroke-width="1.2"/>')
    colours = {'press': '#e5e7eb', 'begin': '#22c55e', 'impact': '#ef4444', 'hit': '#facc15', 'source': '#a855f7', 'tap': '#94a3b8'}
    for e in events:
        k = e['what'].split()[0]
        if k not in colours or (k == 'begin' and e['side'] == 's'):
            continue
        if k == 'hit' and e['side'] != 's':
            continue
        x = X(e['gt'])
        parts.append(f'<line x1="{x:.1f}" x2="{x:.1f}" y1="0" y2="{H}" stroke="{colours[k]}" stroke-opacity="0.45" stroke-dasharray="{"2,3" if k == "source" else "none"}"/>')
    for t in range(int(math.ceil(t0)), int(t1) + 1, 2):
        parts.append(f'<text x="{X(t):.1f}" y="{H - 4}" fill="#666" text-anchor="middle">{t - int(t0)}</text>')
    parts.append('</svg>')
    with open(out, 'w') as f:
        f.write('\n'.join(parts))
    print(out)


def strip(d, out, a, b, cols=8, width=0):
    from PIL import Image, ImageDraw
    times = {}
    with open(os.path.join(d, 'times.txt')) as f:
        for line in f:
            i, t = line.split()
            times[int(i)] = float(t)
    keep = [(i, t) for i, t in sorted(times.items()) if a <= t <= b and os.path.exists(os.path.join(d, f'f{i:05d}.png'))]
    if not keep:
        print('no pictures in', a, b)
        return
    _, events = load(d)
    ims = [Image.open(os.path.join(d, f'f{i:05d}.png')).convert('RGB') for i, _ in keep]
    if width:
        ims = [im.resize((width, round(im.height * width / im.width))) for im in ims]
    w, h = ims[0].size
    rows = (len(ims) + cols - 1) // cols
    S = Image.new('RGB', (cols * w, rows * (h + 16)), (16, 16, 16))
    dr = ImageDraw.Draw(S)
    prev = None
    for n, ((i, t), im) in enumerate(zip(keep, ims)):
        x, y = n % cols * w, n // cols * (h + 16)
        S.paste(im, (x, y + 16))
        lo = t - 0.2 if prev is None else prev
        tags = []
        for e in events:
            if lo < e['gt'] <= t + 1e-6 and e['side'] == 'c':
                k = e['what'].split()[0]
                if k in ('press', 'impact', 'begin', 'source'):
                    tags.append(k if k != 'begin' else e['what'].split()[1])
        dr.text((x + 4, y + 2), f'{t - a:+.2f}t ' + ' '.join(tags), fill=(255, 90, 80) if tags else (255, 230, 80))
        if any(k in ('press', 'impact') for k in tags):
            dr.rectangle((x, y + 16, x + w - 1, y + 16 + h - 1), outline=(230, 50, 40), width=3)
        prev = t
    S.save(out)
    print(out, len(ims))


def video(d, out, every=1, fps=30, width=960):
    """The take as a video: every `every`th picture at `fps`, each stamped with its time, the move and a mark on a press.
    Pictures 0.2 ticks apart at 30 fps play at a third of play's speed; every 2nd at 50 fps plays at play's speed."""
    import shutil
    import subprocess
    import tempfile
    from PIL import Image, ImageDraw
    rows, events = load(d)
    times = []
    with open(os.path.join(d, 'times.txt')) as f:
        for line in f:
            i, t = line.split()
            times.append((int(i), float(t)))
    presses = [e['gt'] for e in events if e['side'] == 'c' and e['what'].startswith('press ')]
    imps = [e['gt'] for e in events if e['side'] == 'c' and e['what'].startswith('impact ')]
    t0 = times[0][1]
    tmp = tempfile.mkdtemp()
    n = 0
    prev = None
    for k, (i, t) in enumerate(times):
        if k % every:
            continue
        p = os.path.join(d, f'f{i:05d}.png')
        if not os.path.exists(p):
            continue
        im = Image.open(p).convert('RGB')
        im = im.resize((width, round(im.height * width / im.width)))
        dr = ImageDraw.Draw(im)
        row = min(rows, key=lambda r: abs(r['gt'] - t))
        lo = t if prev is None else prev
        mark = [x for x in presses if lo < x <= t + 1e-6] if prev is not None else []
        hit = [x for x in imps if lo < x <= t + 1e-6] if prev is not None else []
        dr.rectangle((0, 0, width, 22), fill=(0, 0, 0))
        dr.text((8, 5), f'tick {t - t0:6.2f}   {row["move"]:<10s} {"WAITING AT SET" if row.get("wait") == 1 else ""}', fill=(255, 210, 90))
        if mark:
            dr.rectangle((width - 120, 0, width - 64, 22), fill=(200, 40, 40))
            dr.text((width - 114, 5), 'PRESS', fill=(255, 255, 255))
        if hit:
            dr.rectangle((width - 60, 0, width, 22), fill=(240, 240, 240))
            dr.text((width - 54, 5), 'IMPACT', fill=(0, 0, 0))
        im.save(os.path.join(tmp, f'v{n:05d}.png'))
        n += 1
        prev = t
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(fps), '-i', os.path.join(tmp, 'v%05d.png'),
                    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '26', '-movflags', '+faststart', out], check=True)
    shutil.rmtree(tmp)
    print(out, n, 'frames')


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'report':
        for d in sys.argv[2:]:
            report(d)
    elif cmd == 'svg':
        svg(sys.argv[2], sys.argv[3])
    elif cmd == 'strip':
        strip(sys.argv[2], sys.argv[3], float(sys.argv[4]), float(sys.argv[5]), int(sys.argv[6]) if len(sys.argv) > 6 else 8,
              int(sys.argv[7]) if len(sys.argv) > 7 else 0)
    elif cmd == 'video':
        video(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 1, int(sys.argv[5]) if len(sys.argv) > 5 else 30)
