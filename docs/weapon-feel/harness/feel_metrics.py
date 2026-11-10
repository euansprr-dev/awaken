"""Disposable planning harness (weapon feel plan): what the eye gets at 60 fps from every baked first-person move,
played as the runtime plays a tap from rest (the wind-up at twice speed, ChargeDraw.TAP_RATE), read-only."""
import json, math, sys, os
ROOT = sys.argv[1]
sys.path.insert(0, os.path.join(ROOT, "tools/art"))
import numpy as np
import anim_lab as L
import swing_studio as S

swings = L.load_swings()
T = L.table()
FPS = 60.0
out = []
for sid in T:
    st = T[sid]
    chain = st.get("chain", []) + st.get("masteryChain", [])
    for mv, m in st["moves"].items():
        if mv not in swings.get(sid, {}).get("moves", {}):
            continue
        clip = swings[sid]["moves"][mv]
        rs = S.rows(sid, mv, swings)
        hit = clip.get("hit", L.hit_tick(m))
        w, a, r = m["windup"], m["active"], m["recovery"]
        tapped = mv in chain  # a chain strike waits under the press and launches on the tap
        launch = max(w / 2.0, 1.0) if tapped and w > 0 else w
        rate = (w / launch) if tapped and w > 0 else 1.0

        def shown_ms(t):  # clip tick -> ms after the tap as shown
            return (t / rate if t <= w else launch + (t - w)) * 50.0

        ts = np.array([x[0] for x in rs])
        mid = np.array([x[2] for x in rs])
        tip = np.array([x[1] for x in rs])
        ms = np.array([shown_ms(t) for t in ts])
        # resample the blade's middle on the screen at 60 fps of shown time
        grid = np.arange(0, ms[-1] + 1e-6, 1000.0 / FPS)
        gx = np.interp(grid, ms, mid[:, 0]); gy = np.interp(grid, ms, mid[:, 1])
        step = np.hypot(np.diff(gx), np.diff(gy))  # screen half-heights a frame
        hit_ms = shown_ms(hit)
        hi = int(round(hit_ms / (1000.0 / FPS)))
        pk = float(step.max()) if len(step) else 0.0
        # the blow: the run of frames around the hit moving faster than a quarter of the peak
        lo = hi
        while lo > 0 and step[min(lo, len(step)) - 1] > 0.25 * pk: lo -= 1
        up = hi
        while up < len(step) and step[up] > 0.25 * pk: up += 1
        blow_frames = up - lo
        blow_travel = float(step[lo:up].sum())
        # anticipation: frames from the first motion (over 0.004 a frame) to the blow's start, and how far it draws back
        first = next((i for i, s in enumerate(step) if s > 0.004), 0)
        antic_frames = max(0, lo - first)
        antic_travel = float(step[first:lo].sum())
        # the stops: near stills (under 6% of peak) between the first motion and the hit + 1.5 ticks
        end = min(len(step), hi + int(1.5 * 50 / (1000 / FPS)))
        stops = 0; still = False
        for s in step[first:end]:
            if s < 0.06 * pk and not still: stops += 1; still = True
            elif s >= 0.06 * pk: still = False
        # settle: from the hit to the last frame moving over 0.004
        last = max((i for i, s in enumerate(step) if s > 0.004), default=0)
        settle_ms = max(0.0, (last + 1) * 1000 / FPS - hit_ms)
        # where the blade's middle is at the hit, against the crosshair
        hm = np.array([np.interp(hit_ms, ms, mid[:, 0]), np.interp(hit_ms, ms, mid[:, 1])])
        # the way the middle travels at the hit, against the kick the game plays
        i0 = max(0, min(len(grid) - 2, hi - 1)); v = np.array([gx[i0 + 1] - gx[i0], gy[i0 + 1] - gy[i0]])
        sw = m.get("swing", {})
        kick = clip.get("kick")
        if kick: kv = np.array([kick[0], -kick[1]])
        elif sw.get("from") and sw.get("to"): kv = np.array([sw["to"][0] - sw["from"][0], sw["to"][1] - sw["from"][1]])
        else: kv = None
        kerr = None
        if kv is not None and np.linalg.norm(kv) > 1e-6 and np.linalg.norm(v) > 1e-6:
            c = float(v @ kv / (np.linalg.norm(v) * np.linalg.norm(kv)))
            kerr = round(math.degrees(math.acos(max(-1, min(1, c)))))
        cam = np.array([x[5] for x in rs]); lean_pk = float(np.abs(cam).max()) if len(cam) else 0.0
        on = np.mean([(abs(gx[i]) < 1 and abs(gy[i]) < 1) for i in range(lo, min(up + 1, len(gx)))]) if up > lo else 1.0
        hs = m.get("hitstop", 0)
        out.append(dict(style=sid, move=mv, form=clip.get("form", L.form_of(m)[0]), weight=S.weight_class(sid), keyed=bool(swings[sid].get("by") or clip.get("hit") is not None),
                        tapped=tapped, windup=w, active=a, recovery=r, press_to_hit_ms=round(hit_ms), antic_frames=int(antic_frames),
                        antic_travel=round(antic_travel, 2), blow_frames=int(blow_frames), blow_travel=round(blow_travel, 2), peak_step=round(pk, 2),
                        stops=int(stops), settle_ms=round(settle_ms), hit_x=round(float(hm[0]), 2), hit_y=round(float(hm[1]), 2), kick_err=kerr,
                        lean_pk=round(lean_pk, 1), hitstop=hs, kick_deg=round(min(2.4, hs / 40.0), 2), in_view=round(float(on), 2), total_ms=round(float(ms[-1]))))
json.dump(out, open(sys.argv[2], "w"))
cols = ["style", "move", "form", "press_to_hit_ms", "antic_frames", "antic_travel", "blow_frames", "blow_travel", "peak_step", "stops", "settle_ms", "hit_x", "hit_y", "kick_err", "lean_pk", "hitstop", "in_view"]
print(" ".join(f"{c[:11]:>11s}" for c in cols))
for r in out:
    if r["move"].startswith("strike") or r["move"] in ("heavy",):
        print(" ".join(f"{str(r[c])[:11]:>11s}" for c in cols))
