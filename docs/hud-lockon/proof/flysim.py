# Replays MobGoals.FlyHunt + FlyerMoveControl + LivingEntity.travelInAir (no gravity) against a still player.
import math,random,statistics as st,sys
# usage: python3 -I flysim.py [step]   (no step: today's rules; a step: the proposed lock rules,
# reach + step to the creature you mean and no height band for it)
STEP=float(sys.argv[1]) if len(sys.argv)>1 else None
def run(spd,w,h,reach_move,seed,ticks=20*120,rng=None):
    R=random.Random(seed)
    P=(0.0,0.0,0.0); eye=(0,1.62,0); pw=0.6
    x,y,z=6.0,3.0,0.0; vx=vy=vz=0.0
    angle=R.random()*2*math.pi; retarget=0; cd=0; tell=-1; dive=0
    wanted=None; mod=1.0; op='WAIT'
    hits=0; hittable=0; windows=[]; cur=0; angs=[]; prev=None; reachable_now=[]
    reach_mob=(w+pw)*0.5+1.3
    for t in range(ticks):
        # goal tick
        if cd>0: cd-=1
        did=False
        if cd<=0:
            if tell<0: tell=8
            if tell>0:
                tell-=1
                if tell==0: dive=30
                wanted=(x,y,z);mod=0.6;op='MOVE'; did=True
            else:
                ex,ey,ez=0,1.62-1.8*0.3,0
                wanted=(ex,ey,ez);mod=1.2;op='MOVE'
                d2=(x)**2+(y+h/2-0.9)**2+z**2  # distanceToSqr uses positions (feet); approx
                d2=x*x+(y-0)**2+z*z
                hit=d2<reach_mob**2
                if hit: hits+=1
                dive-=1
                if hit or dive<=0:
                    cd=30+R.randint(0,19); tell=-1; retarget=0
                did=True
        if not did:
            retarget-=1
            if retarget<=0:
                retarget=12; angle+=0.6+R.random()*0.4
                r=3.5+w; yy=2.0+R.random()*2.0+(1.5 if h>2 else 0)
                wanted=(math.cos(angle)*r,yy,math.sin(angle)*r); mod=1.0; op='MOVE'
        # move control
        if op=='MOVE' and wanted:
            dx,dy,dz=wanted[0]-x,wanted[1]-y,wanted[2]-z; L=math.sqrt(dx*dx+dy*dy+dz*dz)
            if L<max(0.5,max(w,h)*0.5):
                op='WAIT'; vx*=0.5;vy*=0.5;vz*=0.5
            else:
                a=spd*mod*0.16/L; vx+=dx*a;vy+=dy*a;vz+=dz*a
        else:
            vx*=0.8;vy*=0.8;vz*=0.8
        x+=vx;y+=vy;z+=vz
        vx*=0.91;vz*=0.91;vy*=0.98
        # hittable by the katana's sweep? closest point of box to eye within reach, vertical band
        bx=(x-w/2,x+w/2);by=(y,y+h);bz=(z-w/2,z+w/2)
        cx=min(max(0,bx[0]),bx[1]);cy=min(max(1.62,by[0]),by[1]);cz=min(max(0,bz[0]),bz[1])
        dist=math.sqrt(cx*cx+(cy-1.62)**2+cz*cz)
        ok= (dist<=reach_move+0.3 and not (by[1]<0-1.0 or by[0]>1.62+1.5)) if STEP is None else dist<=reach_move+0.3+STEP
        if ok: hittable+=1; cur+=1
        elif cur: windows.append(cur); cur=0
        # angular speed of the centre seen from the eye
        c=(x,y+h/2-1.62,z); n=math.sqrt(sum(v*v for v in c))
        u=tuple(v/n for v in c)
        if prev: angs.append(math.degrees(math.acos(max(-1,min(1,sum(a*b for a,b in zip(u,prev))))))*20)
        prev=u
    return hits,hittable/ticks,windows,angs
for name,spd,w,h in [('bell_wisp (vex body, spd 0.5)',0.5,0.4,0.8),('gale_harpy (phantom body, 0.45)',0.45,0.9,0.5),('ember_imp (blaze, 0.3)',0.3,0.6,1.8)]:
    H=[];F=[];W=[];A=[]
    for s in range(20):
        h_,f,wn,an=run(spd,w,h,3.1,s); H.append(h_);F.append(f);W+=wn;A+=an
    A.sort()
    print(f"{name}: dive hits/min {st.mean(H)/2:.1f} | time in katana reach {100*st.mean(F):.0f}% | median window {st.median(W)/20*1000 if W else 0:.0f} ms (p90 {sorted(W)[int(len(W)*.9)]/20*1000 if W else 0:.0f}) | angular speed median {A[len(A)//2]:.0f} deg/s p90 {A[int(len(A)*.9)]:.0f}")
