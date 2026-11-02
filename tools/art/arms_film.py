#!/usr/bin/env python3
"""Reads an arms film (the autopilot's `arms film`, SPEC §25 "The arms film") and says what the frames can't.

    python3 tools/art/arms_film.py report <clip dir> [...]       timing, speed and jumps per take, as text
    python3 tools/art/arms_film.py svg <clip dir> <out.svg>      the blades' paths and speed over the take, events marked
    python3 tools/art/arms_film.py strip <clip dir> <out.png> <from gt> <to gt> [cols]   the pictures in that span
    python3 tools/art/arms_film.py video <clip dir> <out.mp4> [speed] [from] [to] [crop] [width]   the take stamped, at a pace

A clip dir holds track.tsv (a row per frame drawn), events.tsv and the pictures with times.txt. Game time (gt) is the
client's ticks; at play's 20 a second a tick is three 60 fps frames, so speeds are given as the share of the screen a
blade's tip crosses in one 60 fps frame of play ("/f"), whatever /tick rate the film ran at. Both views are logged in the
camera's place with the world's axes: projected through the camera's turn, first person with the hands' 70 degrees,
third person with the camera's fov.
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
    # every event placed on the film's clock by its real time and the frames around it (the server's rows carry only
    # real time, and the client's own game time is stale inside its tick, up to a tick behind the frames)
    for e in events:
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
        # both passes draw in the camera's place with the world's axes (checked on a frame: the marks sit on the tips);
        # the hands through their own 70 degrees, the world through the camera's fov
        fov = HAND_FOV if pre in ('m', 'o') else r['fov']
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
    """(gt, tip, base, real_ms) for every frame the blade is drawn."""
    out = []
    for r in rows:
        p = project(r, pre)
        if p:
            out.append((r['gt'], p[0], p[1], r['real_ms']))
    return out


def ms_per_tick(rows):
    """Real ms per game tick over the take (500 at the films' 2 ticks a second)."""
    span = rows[-1]['gt'] - rows[0]['gt'] if rows else 0
    return (rows[-1]['real_ms'] - rows[0]['real_ms']) / span if span > 0 else 50.0


def speeds(s, mpt):
    """The tip's speed per 60 fps frame of play (share of the screen: ndc / 2), at the middle of each step. Timed by
    real ms, as the hands' clock runs (ArmsFeel), not by the frame's game time: inside a runner's hitch the client's
    ticks bunch up and its game time lags the hands, which made false spikes."""
    out = []
    for (t0, a, _, m0), (t1, b, _, m1) in zip(s, s[1:]):
        dt = (m1 - m0) / mpt
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
    """Each strike in order: the press that began it here, the blade reaching the target on screen (the first frame the
    hands' clock passes the move's wind-up), the server's begin and hit (the server may take a different press of a
    mashed chain for the same strike: both are given) and the impact shown with its freeze. Presses that began nothing
    are listed too."""
    presses = {}
    for e in events:
        w = e['what'].split()
        if e['side'] == 'c' and w[0] == 'press':
            presses[int(w[2])] = e['gt']
    taps = [e['gt'] for e in events if e['side'] == 'c' and e['what'].startswith('tap ')]
    cl, sv = [], []
    for e in events:
        w = e['what'].split()
        if w[0] != 'begin' or 'seq' not in w:
            continue
        seq = int(w[w.index('seq') + 1])
        x = {'seq': seq, 'move': w[1], 'at': e['gt'], 'waits': 'waits' in w}
        (cl if e['side'] == 'c' else sv).append(x)
    for i, x in enumerate(sv):
        end = sv[i + 1]['at'] if i + 1 < len(sv) else 1e18
        x['hit'] = next((e['gt'] for e in events if e['side'] == 's' and e['what'].startswith('hit ' + x['move'] + ' ')
                         and x['at'] - 1e-6 <= e['gt'] < end), None)
    shown = []
    for e in events:
        w = e['what'].split()
        if e['side'] == 'c' and w[0] == 'impact':
            shown.append({'gt': e['gt'], 'move': w[1].lower(), 'ms': int(w[3]), 'on': w[5], 'at': float(w[7])})
    # the server's strikes paired with ours in order, by move
    j = 0
    out = []
    for x in cl:
        k = j
        while k < len(sv) and sv[k]['move'] != x['move']:
            k += 1
        s_ = sv[k] if k < len(sv) and k - j <= 2 else None
        if s_ is not None:
            j = k + 1
        out.append({'c': x, 's': s_})
    used = set()
    for o in out:
        s_ = o['s']
        o['impact'] = None
        if s_ is None or s_['hit'] is None:
            continue
        for n, im in enumerate(shown):
            if n not in used and im['move'] == o['c']['move'] and im['gt'] >= s_['hit'] - 1e-6:
                used.add(n)
                o['impact'] = im
                break
    for o in out:
        x = o['c']
        p0 = presses.get(x['seq'], x['at'])
        o['press'], o['move'] = p0, x['move']
        o['tap'] = next((t for t in taps if t >= p0 - 1e-6), None)
        wu = windup(d, x['move'])
        o['contact'] = None
        if wu is not None:
            for r in rows:
                if r['gt'] >= x['at'] and r['move'] == x['move'] and r['view'] >= wu:
                    o['contact'] = r['gt']
                    break
        o['waits'] = x['waits']
        # the runner's own hitches (a frame drawn over 250 ms of real time after the last, half a tick at 2 a second): the
        # hands' clock runs on real time, so a strike seen through one jumps as no 60 fps screen would show it
        end = o['contact'] if o['contact'] is not None else x['at'] + 2
        o['hitch'] = any(rows[i + 1]['real_ms'] - rows[i]['real_ms'] > 250 and rows[i + 1]['gt'] >= p0 - 0.05 and rows[i]['gt'] <= end
                         for i in range(len(rows) - 1))
        f = lambda v: '-' if v is None else f'{(v - p0) * 50:+.0f}'
        s_, im = o['s'], o['impact']
        frz = ''
        if im is not None:
            frz = f' freeze {im["ms"]} ms ' + (f'held at {im["at"]:.2f} of the move (blow at {wu})' if im['on'] == x['move']
                                              else f'SKIPPED (the hands were on {im["on"]})')
        same = '' if s_ is None or s_['seq'] == x['seq'] else f' (the server took press {s_["seq"]})'
        o['text'] = (f'press {x["seq"]} @{p0:.2f}: {x["move"]:9s} begun {f(x["at"])}{" waits" if x["waits"] else ""} blade there '
                     f'{f(o["contact"])} | server begun {f(s_["at"] if s_ else None)}{same} hit {f(s_["hit"] if s_ else None)}'
                     f' | impact shown {f(im["gt"] if im else None)}{frz}  (ms of play from the press)'
                     + ('  [HITCH: the runner stalled here, not judged]' if o['hitch'] else ''))
        o['impact'] = None if im is None else im['gt']
    began = {o['c']['seq'] for o in out}
    idle = [n for n in presses if n not in began]
    if idle:
        out.append({'text': f'presses that began nothing: {len(idle)} of {len(presses)} ({", ".join(str(n) for n in sorted(idle))})',
                    'press': None})
    return out


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
        sp = speeds(s, ms_per_tick(rows))
        top = sorted(sp, key=lambda x: -x[1])[:6]
        print(f'  {label}: drawn {len(s)} frames; fastest tip /f: ' + ', '.join(f'{v:.2f}@{t:.2f}' for t, v, _ in top))
        # a jump: a step at least 2.5x both neighbours and over 0.15 of the screen a frame
        jumps = []
        for i in range(1, len(sp) - 1):
            if sp[i][1] > 0.15 and sp[i][1] > 2.5 * max(sp[i - 1][1], sp[i + 1][1], 0.02):
                jumps.append(sp[i])
        if jumps:
            print(f'  {label} spikes (a frame much faster than both neighbours): ' + ', '.join(f'{v:.2f}@{t:.2f}' for t, v, _ in jumps))
    tl = timeline(d, rows, events)
    for line in tl:
        print('  ' + line['text'])
    st = [x for x in tl if x.get('press') is not None and not x['hitch']]
    print(f'  (judged: {len(st)} strikes; {sum(1 for x in tl if x.get("hitch"))} seen through the runner\'s hitches left out)')
    pc = [x['contact'] - x['press'] for x in st if x['contact'] is not None]
    if pc:
        print(f'  strikes {len(st)}, waited at the set {sum(1 for x in st if x["waits"])}; press to the blade on the target: mean '
              f'{sum(pc) / len(pc) * 50:.0f} ms of play, least {min(pc) * 50:.0f}, worst {max(pc) * 50:.0f}')
    lag = [(x['impact'] - x['contact']) * 50 for x in st if x['impact'] is not None and x['contact'] is not None]
    if lag:
        print(f'  impacts {len(lag)}: after the blade reached the target by ' + ', '.join(f'{v:+.0f}' for v in lag) + ' ms of play')
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
            pts = ' '.join(f'{X(t):.1f},{y0 + h / 2 - max(-1.4, min(1.4, f((t, tip, base)))) * h / 2.8:.1f}' for t, tip, base, _ in s)
            parts.append(f'<polyline points="{pts}" fill="none" stroke="{col}" stroke-width="1.5"/>')
    y0, h = 345, 150
    parts.append(f'<text x="4" y="{y0 + 12}" fill="#aaa">tip speed /f</text>')
    for v in (0.1, 0.2, 0.3):
        yy = y0 + h - v / 0.4 * h
        parts.append(f'<line x1="{L}" x2="{W - 10}" y1="{yy:.1f}" y2="{yy:.1f}" stroke="#2a2a2a"/><text x="{L - 28}" y="{yy + 4:.1f}" fill="#666">{v}</text>')
    for pre, col in pres:
        sp = speeds(series(rows, pre), ms_per_tick(rows))
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


def video(d, out, speed=0.25, fps=30, width=960, a=None, b=None, crop=None):
    """The take as a video at `speed` times play's pace (0.25: a quarter speed), timed by the film's own clock: each frame
    of the video shows the latest picture taken by then, so a stretch the runner drew slowly holds instead of rushing.
    Stamped with the time, the move, a press and an impact; `a`..`b` in game ticks, `crop` (x0, y0, x1, y1 fractions)."""
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
    a = times[0][1] if a is None else a
    b = times[-1][1] if b is None else b
    step = speed * 20.0 / fps  # game ticks a video frame
    tmp = tempfile.mkdtemp()
    n, k, t, prev = 0, 0, a, a
    cache = {}
    while t <= b:
        while k + 1 < len(times) and times[k + 1][1] <= t:
            k += 1
        i = times[k][0]
        if i not in cache:
            p = os.path.join(d, f'f{i:05d}.png')
            im = Image.open(p).convert('RGB')
            if crop:
                W, H = im.size
                im = im.crop((int(crop[0] * W), int(crop[1] * H), int(crop[2] * W), int(crop[3] * H)))
            cache.clear()
            cache[i] = im.resize((width, round(im.height * width / im.width) // 2 * 2))
        im = cache[i].copy()
        dr = ImageDraw.Draw(im)
        row = min(rows, key=lambda r: abs(r['gt'] - t))
        dr.rectangle((0, 0, width, 22), fill=(0, 0, 0))
        dr.text((8, 5), f'{(t - a) * 50:5.0f} ms of play  x{speed:g}   {row["move"]:<10s} {"WAITING AT SET" if row.get("wait") == 1 else ""}',
                fill=(255, 210, 90))
        # a mark stays up for 4 video frames so it can be seen
        if any(prev - 3 * step < x <= t for x in presses):
            dr.rectangle((width - 124, 0, width - 66, 22), fill=(200, 40, 40))
            dr.text((width - 118, 5), 'PRESS', fill=(255, 255, 255))
        if any(prev - 3 * step < x <= t for x in imps):
            dr.rectangle((width - 62, 0, width, 22), fill=(240, 240, 240))
            dr.text((width - 56, 5), 'IMPACT', fill=(0, 0, 0))
        im.save(os.path.join(tmp, f'v{n:05d}.png'))
        n += 1
        prev = t
        t += step
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(fps), '-i', os.path.join(tmp, 'v%05d.png'),
                    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '27', '-movflags', '+faststart', out], check=True)
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
        # video <dir> <out.mp4> [speed] [from gt] [to gt] [crop x0,y0,x1,y1] [width]
        g = sys.argv
        video(g[2], g[3], float(g[4]) if len(g) > 4 else 0.25, 30, int(g[8]) if len(g) > 8 else 960,
              float(g[5]) if len(g) > 5 and g[5] != '-' else None, float(g[6]) if len(g) > 6 and g[6] != '-' else None,
              tuple(map(float, g[7].split(','))) if len(g) > 7 and g[7] != '-' else None)
