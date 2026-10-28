import os
import sys, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import curves as C
import numpy as np
L=C.L; T=C.T
PASS={"light":97,"heavy":145,"thrust":80,None:97,"art":None}
pops=[]; offs=[]
for sid in T:
    st=T[sid]; chain=st["chain"]+st.get("masteryChain",[])
    for i,mv in enumerate(chain):
        m=st["moves"][mv]; r=C.measure(sid,mv); rows=r["rows"]
        rest=rows[0][1]
        cad=m.get("cadence")
        if cad and i+1<len(chain):
            t=cad[0]; row=min(rows,key=lambda x:abs(x[0]-t))
            tm=(cad[0]+cad[1])/2; rowm=min(rows,key=lambda x:abs(x[0]-tm))
            pops.append((sid,mv,chain[i+1],t,float(np.linalg.norm(row[1]-rest)),float(np.linalg.norm(rowm[1]-rest))))
        sw=m.get("swish") or "light"
        if PASS.get(sw): offs.append((sid,mv,sw,r["hit"]*50,PASS[sw]))
for p in pops: print(f"POP {p[0]:10s} {p[1]}->{p[2]} at t{p[3]}: tip jump {p[4]:.2f} NDC (mid-window {p[5]:.2f})")
print()
bad=[o for o in offs if abs(o[3]-o[4])>40]
for o in offs: print(f"SND {o[0]:10s} {o[1]:9s} {o[2]:6s} hit {o[3]:4.0f}ms swish pass ~{o[4]}ms  off {o[4]-o[3]:+.0f}ms")
