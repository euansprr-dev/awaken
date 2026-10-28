import os
"""Lab-only prototype: the katana's level draw cut re-shaped (lower contact, shoulder-like pivot, blade more across,
visible set, short drive, bite, exit low and rise home). Writes nothing the game reads."""
import sys, math, json
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../tools/art"))
import numpy as np
import anim_lab as L
T = L.table()
f = 1/math.tan(math.radians(35)); ASP = 16/9
def ndc(P): return np.array([P[0]/-P[2]*f/ASP, P[1]/-P[2]*f])
H0 = np.array([L.HX, L.HY, L.HZ])

def proto(sid, mv, knobs):
    st = T[sid]; m = st["moves"][mv]; held = L.Held("gatewrought_"+sid)
    F, side = L.held_frame(held)
    form, u, _ = L.form_of(m)
    hit = L.hit_tick(m); total = m["windup"]+m["active"]+m["recovery"]
    pivot = np.array(knobs["pivot"]); target = np.array(knobs["target"])
    r_c = target - pivot; reach = float(np.linalg.norm(r_c)); r_c /= reach
    # the blade laid more across the cut: the plane's radial direction leans toward the travel
    tan_c = u - r_c*(u@r_c); tan_c /= np.linalg.norm(tan_c)
    arm = max(0.15, reach - L.MID*held.length)
    pre, post, stop = knobs["pre"], knobs["post"], knobs["stop"]
    roll0 = 35.0
    def at(theta, lag=0.0, roll=0.0, step=0.0, drop=0.0):
        th, tb = math.radians(theta), math.radians(theta-lag)
        r = math.cos(th)*r_c + math.sin(th)*tan_c
        rb = math.cos(tb)*r_c + math.sin(tb)*tan_c
        tan = -math.sin(tb)*r_c + math.cos(tb)*tan_c
        R = L.frame_to(rb, tan, side, F)
        if roll: R = L.axis_rot(rb, roll) @ R
        p = pivot + r_c*0.07*step + r*arm + np.array([0, -drop, 0])
        return p - H0 - R@held.grip, R
    rest = (np.zeros(3), np.eye(3))
    drive = knobs["drive"]; c = hit - drive
    reach_t = knobs["reach"]            # ticks to reach the cocked pose (the rest is the set)
    bite_end = hit + knobs["bite"]
    exit_t = bite_end + knobs["exit"]
    def pose(t):
        if t < reach_t:   # anticipation: along the arc, out of rest, eased out (a quick load, then the set)
            k = L.ease_out_cubic(t/reach_t)
            return L.lerp_pose(rest, at(-pre, 10, roll0*0.85), k)
        if t < c:         # the set: held, a hair further back (the spring loaded), on screen
            k = (t-reach_t)/max(1e-6, c-reach_t)
            return at(-pre-4*k, 10+6*k, roll0*(0.85+0.15*k))
        if t < hit:       # the drive: explosive, fastest on the contact
            x = (t-c)/drive; p = x**knobs["power"]
            return at((-pre-4)*(1-p), 16*(1-x)**1.5, roll0*(1-x)**1.6, x)
        if t < bite_end:  # the bite: the edge meets resistance, slows hard
            x = (t-hit)/knobs["bite"]
            return at(post*(1-(1-x)**3), 0, 0, 1)
        if t < exit_t:    # the follow-through: carried on and down, out of the frame low
            x = (t-bite_end)/knobs["exit"]
            return at(post+(stop-post)*L.ease_out_cubic(x), 0, 0, 1, knobs["exit_drop"]*L.ease_in_out_sine(x))
        rise = min(knobs["rise"], total-exit_t)          # home: up from below the frame, fast then settling
        x = min(1.0, (t-exit_t)/max(1e-6, rise))
        low = at(stop, 0, 0, 1, knobs["exit_drop"])
        lowered = (rest[0]+np.array([0.0,-0.75,0.0]), rest[1])
        if x < 0.35:
            return L.lerp_pose(low, lowered, L.ease_in_out_sine(x/0.35))
        return L.lerp_pose(lowered, rest, 1-(1-(x-0.35)/0.65)**4)
    return pose, held, dict(c=c, hit=hit, total=total, reach=reach_t, bite_end=bite_end, exit=exit_t)

def rows_of(pose, held, total):
    out=[]; prev=None
    for t in np.arange(0, total+1e-6, 0.25):
        d,R = pose(float(t))
        tip = R@held.tip + H0 + d; grip = R@held.grip + H0 + d; mid = grip+(tip-grip)*0.78
        ax=(tip-grip)/np.linalg.norm(tip-grip)
        sp = 0 if prev is None else math.degrees(math.acos(max(-1,min(1,float(ax@prev)))))/0.25
        prev=ax
        out.append((float(t), ndc(tip), ndc(mid), sp, tip[2], mid[2]))
    return out

def current_rows(sid, mv):
    S=L.load_swings(); m=T[sid]["moves"][mv]; held=L.Held("gatewrought_"+sid)
    total=m["windup"]+m["active"]+m["recovery"]
    return rows_of(lambda t: L.new_pose(sid, mv, t, S), held, total), L.hit_tick(m)

def metrics(rows, hit):
    sp=[r[3] for r in rows]; pk=max(sp)
    h=min(rows,key=lambda r:abs(r[0]-hit))
    fast=[r for r in rows if r[3]>0.25*pk]
    high=sum(1 for r in fast if r[1][1]>0)/len(fast)
    after=[r for r in rows if r[0]>hit]
    bite=next((r[0]-hit for r in after if r[3]<0.3*pk),None)
    onscreen=lambda r: abs(r[2][0])<1 and abs(r[2][1])<1 and r[5]<0
    drift=sum(0.25 for r in rows if r[0]>hit+1 and 0.5<r[3]<0.25*pk and onscreen(r))
    ramp=next((r[0] for r in rows if r[0]<=hit and r[3]>0.2*pk and r[0]>hit-3), None)
    return dict(contact_y=round(float(h[2][1]),2), tip_y=round(float(h[1][1]),2), high=round(high*100), peak=round(pk),
                at_contact=round(h[3]/pk,2), bite_ms=None if bite is None else bite*50, drift_ms=drift*50,
                drive_ms=None if ramp is None else (hit-ramp)*50)

KATANA = dict(pivot=[0.62,-0.52,-0.30], target=[0.0,-0.24,-1.15], pre=62, post=16, stop=58, drive=1.0, power=2.6,
              reach=1.25, bite=0.5, exit=1.5, exit_drop=0.35, rise=3.0)
if __name__=="__main__":
    cur,hit = current_rows("katana","strike_1")
    pose,held,ph = proto("katana","strike_1",KATANA)
    new = rows_of(pose, held, ph["total"])
    print("current ", metrics(cur, hit)); print("proto   ", metrics(new, ph["hit"]), ph)
    json.dump({"current":[[r[0],*map(float,r[1]),*map(float,r[2]),r[3]] for r in cur],
               "proto":[[r[0],*map(float,r[1]),*map(float,r[2]),r[3]] for r in new], "hit":hit},
              open(sys.argv[1] if len(sys.argv)>1 else "/dev/null","w"))
    if "-v" in sys.argv:
        for r in new: print(f"t{r[0]:5.2f} tip({r[1][0]:+.2f},{r[1][1]:+.2f}) cut({r[2][0]:+.2f},{r[2][1]:+.2f}) sp {r[3]:6.1f}")
