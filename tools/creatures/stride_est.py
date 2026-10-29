# python3 -I tools/creatures/stride_est.py
# A rough estimate of how long a stride a one-bone rigid leg can plant (SPEC 23.1, "One-bone legs"): from a hip, the
# rest leg vector (model px, y down) and the crouch, the longest planted sweep before the hips must drop more than the
# vault (a quarter leg), and the strides that allows walking and running. Edit the body(...) lines at the bottom.
# It treats every leg as pointing straight out: a leg splayed fore or aft reaches far less along its own direction
# (the glass scarab's front legs: estimate 2.1 px for the middle pair, 0.85 for the front). creature_motion_feet is
# the judge; this only gets you near.
import math
# rigid one-bone leg: hip (model px), rest leg vector g*L (model, y down); lift k = shoulder*L; no room (fore=aft=drop=0)
def sink(hip, g, L, k, tgt):
    vx,vy,vz = hip[0]-g[0]*k, hip[1]-g[1]*k, hip[2]-g[2]*k
    ex,ey,ez = tgt[0]-vx, tgt[1]-vy, tgt[2]-vz
    el = math.sqrt(ex*ex+ey*ey+ez*ez)
    py = hip[1] + (ey/el - g[1])*k
    pz = hip[2]
    dx, dz, r = tgt[0]-hip[0], tgt[2]-pz, 0.97*L
    flat = r*r-dx*dx-dz*dz
    return tgt[1]-py-(math.sqrt(flat) if flat>0 else 0)
def body(name, hip, legvec, crouch, shoulder=0.5, scale=1.0, spd=0.3, mod=0.7, duty=(0.7,0.42), lead=0.2):
    L = math.sqrt(sum(c*c for c in legvec)); g=[c/L for c in legvec]
    foot = [hip[i]+legvec[i] for i in range(3)]
    home=(foot[0],24.0,foot[2])
    vault=0.25*L
    hmax=0
    for h10 in range(0,400):
        h=h10/20
        ok=True
        for sg in (-1,1):
            s=sink(hip,g,L,shoulder*L,(home[0],home[1],home[2]+sg*h))-crouch
            if s>vault: ok=False
        if not ok: break
        hmax=h
    v = 1.596*(spd*mod)**2*20  # blocks/s world at wander
    vm = v/scale
    sw = hmax/((0.5+lead)*duty[0]*16)
    sr = hmax/((0.5+lead)*duty[1]*16)
    print(f"{name:10s} L={L:5.2f} vault={vault:4.2f} restdist={math.dist(hip,(home[0],24-crouch,home[2])):.2f} half_max={hmax:5.2f}px strideW<={sw:.2f} strideR<={sr:.2f} wander={v:.2f}b/s -> {vm/ max(sw,0.2/scale):.1f}Hz at max stride")
body('stag',(2.5,12,-6),(0,12,0),0.4,spd=0.3,mod=0.7)
body('tortoise',(5,19,-5.5),(0,5,0),0.3,scale=1.5,spd=0.13,mod=0.7)
body('lizard',(3,21,-4),(3.30,2.26,0),1.0,scale=1.3,spd=0.3,mod=0.7)
body('mudskip',(3,21,-4),(3.30,2.26,0),1.0,scale=0.75,spd=0.25,mod=0.9)
body('listener',(2,8,0),(0,16,0),0.5,spd=0.3,mod=1.0,duty=(0.6,0.4))
body('scarab',(2.5,22,0),(1.73,2.5,0),0.3,scale=1.4,spd=0.3,mod=0.9)
body('marmot',(2,22,-2),(0,2,0),0.1,spd=0.26,mod=0.9)
body('mushroom',(1.5,21,0),(0,3,0),0.1,scale=0.9,spd=0.22,mod=0.9,duty=(0.6,0.4))
