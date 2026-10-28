import os
import sys, math, json
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../tools/art"))
import numpy as np
import anim_lab as L
T = L.table(); S = L.load_swings()
f = 1/math.tan(math.radians(35)); ASP = 16/9
def ndc(P):
    return np.array([P[0]/-P[2]*f/ASP, P[1]/-P[2]*f])
def measure(sid, mv, wid=None):
    st = T[sid]; m = st["moves"][mv]
    held = L.Held(wid or "gatewrought_"+sid)
    wt = L.WEIGHTS[L.WEIGHT_OF.get(sid,"blade")]
    c, hit, ea, fe, total = L.phases(m, wt)
    rows=[]
    prev=None
    for t in np.arange(0, total+1e-6, 0.25):
        d,R = L.new_pose(sid, mv, float(t), S)
        H0=np.array([L.HX,L.HY,L.HZ])
        tip = R@held.tip + H0 + d
        grip = R@held.grip + H0 + d
        mid = grip + (tip-grip)*0.78
        ax = (tip-grip)/np.linalg.norm(tip-grip)
        sp = 0 if prev is None else math.degrees(math.acos(max(-1,min(1,float(ax@prev)))))/0.25
        prev=ax
        cam = S[sid]["moves"][mv]["keys"][int(round(t/0.25))][8:11]
        rows.append((float(t), ndc(tip), ndc(mid), ndc(grip), sp, cam))
    return dict(c=c,hit=hit,ea=ea,fe=fe,total=total,rows=rows, m=m, wt=wt)
def summary(sid, mv, wid=None, verbose=False):
    r = measure(sid, mv, wid); rows=r["rows"]
    hit=r["hit"]; c=r["c"]; ea=r["ea"]
    hi = min(rows, key=lambda x: abs(x[0]-hit))
    blow=[x for x in rows if c<=x[0]<=ea+0.01]
    sp=[x[4] for x in rows]; pk=max(sp); tpk=rows[sp.index(pk)][0]
    # deceleration: ticks from peak until speed < 20% of peak
    after=[x for x in rows if x[0]>tpk]
    stop=next((x[0] for x in after if x[4]<0.2*pk), None)
    # moving (speed>10% pk) after the fe
    tail=[x for x in rows if x[0]>r["fe"]]
    tipys=[x[1][1] for x in blow]
    above=sum(1 for x in rows if x[1][1]>0.0 and x[4]>0.25*pk)/max(1,sum(1 for x in rows if x[4]>0.25*pk))
    m=r["m"]
    print(f"{sid:10s} {mv:9s} {m['swing'].get('form','?'):6s} w{m['windup']} a{m['active']} r{m['recovery']} | hit@{hit:.2f} "
          f"tip@hit ({hi[1][0]:+.2f},{hi[1][1]:+.2f}) cut@hit ({hi[2][0]:+.2f},{hi[2][1]:+.2f}) | blow tip y {min(tipys):+.2f}..{max(tipys):+.2f} "
          f"fast&above-eye {above*100:3.0f}% | peak {pk:5.0f}deg/t @{tpk:.2f} stop20%@{stop} home {r['total']-r['fe']:.2f}t ({(r['total']-r['fe'])/r['total']*100:.0f}%)")
    if verbose:
        for x in rows:
            print(f"   t{x[0]:5.2f} tip({x[1][0]:+.2f},{x[1][1]:+.2f}) cut({x[2][0]:+.2f},{x[2][1]:+.2f}) speed {x[4]:6.1f} cam {x[5]}")
if __name__=="__main__":
    args=sys.argv[1:]
    if args and args[0]=="v":
        summary(args[1], args[2], None, True)
    else:
        for sid in ["katana","longsword","greatblade","axe","maul","daggers","spear","glaive","scythe","twinblades","rapier","chainblade","shieldblade","warstaff","precursor","gauntlets"]:
            for mv in T[sid]["chain"]+T[sid].get("masteryChain",[])+["heavy"]:
                try: summary(sid,mv)
                except Exception as e: print(sid,mv,"ERR",e)
