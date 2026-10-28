import json
d=json.load(open("katana_s1.json"))
W,H,ML,MT=640,360,34,14
def X(x): return ML+(x+1)/2*W
def Y(y): return MT+(1-(y+1)/2)*H
def path(rows, ix, iy):
    pts=[(r[0],r[ix],r[iy]) for r in rows]
    return " ".join(f"{X(max(-1.6,min(1.6,x))):.1f},{Y(max(-1.6,min(1.6,y))):.1f}" for _,x,y in pts), pts
out=[]
out.append(f'<svg viewBox="0 0 {W+ML+20} {H+MT+40}" role="img" aria-label="Katana strike 1, the blade tip on screen, today and the lab prototype">')
out.append(f'<defs><clipPath id="fr"><rect x="{ML}" y="{MT}" width="{W}" height="{H}"/></clipPath></defs>')
out.append(f'<rect x="{ML}" y="{MT}" width="{W}" height="{H}" class="frame"/>')
for gy in (0.5,-0.5):
    out.append(f'<line x1="{ML}" x2="{ML+W}" y1="{Y(gy):.1f}" y2="{Y(gy):.1f}" class="grid"/>')
out.append(f'<line x1="{ML}" x2="{ML+W}" y1="{Y(0):.1f}" y2="{Y(0):.1f}" class="eye"/>')
out.append(f'<text x="{ML+W-6}" y="{Y(0)-6:.1f}" class="lab" text-anchor="end">eye line</text>')
cx,cy=X(0),Y(0)
out.append(f'<path d="M{cx-9},{cy}h18M{cx},{cy-9}v18" class="cross"/>')
hit=d["hit"]
for key,cls in (("current","today"),("proto","proto")):
    rows=d[key]
    pts_s,pts=path(rows,1,2)
    out.append(f'<g clip-path="url(#fr)"><polyline points="{pts_s}" class="p {cls}"/>')
    for t,x,y in pts:
        if abs(x)<1.6 and abs(y)<1.6:
            r=3.2 if abs(t-hit)<1e-6 else 1.8
            out.append(f'<circle cx="{X(x):.1f}" cy="{Y(y):.1f}" r="{r}" class="dot {cls}"/>')
    out.append('</g>')
    h=[r for r in rows if abs(r[0]-hit)<1e-6][0]
    out.append(f'<circle cx="{X(h[3]):.1f}" cy="{Y(h[4]):.1f}" r="6" class="hit {cls}"/>')
# labels
out.append(f'<text x="{ML}" y="{MT+H+18}" class="lab">left edge of the screen</text>')
out.append(f'<text x="{ML+W}" y="{MT+H+18}" class="lab" text-anchor="end">right edge</text>')
out.append('</svg>')
open("frame.svg","w").write("\n".join(out))
# speed chart
W2,H2,ML2,MT2=640,180,40,12
tmax=10; vmax=160
def TX(t): return ML2+t/tmax*W2
def VY(v): return MT2+H2-(min(v,vmax))/vmax*H2
o=[f'<svg viewBox="0 0 {W2+ML2+16} {H2+MT2+34}" role="img" aria-label="How fast the blade turns over the move, today and the prototype">']
for v in (0,50,100,150):
    o.append(f'<line x1="{ML2}" x2="{ML2+W2}" y1="{VY(v):.1f}" y2="{VY(v):.1f}" class="grid"/><text x="{ML2-6}" y="{VY(v)+4:.1f}" class="lab" text-anchor="end">{v}</text>')
for t in range(0,11):
    o.append(f'<text x="{TX(t):.1f}" y="{MT2+H2+16}" class="lab" text-anchor="middle">{t*50}</text>')
off=[r[0] for r in d['proto'] if r[4]<-1.0]
o.append(f'<rect x="{TX(min(off)):.1f}" y="{MT2}" width="{TX(max(off))-TX(min(off)):.1f}" height="{H2}" class="band"/><text x="{(TX(min(off))+TX(max(off)))/2:.1f}" y="{MT2+26}" class="lab" text-anchor="middle">prototype: below the frame</text>')
o.append(f'<line x1="{TX(hit):.1f}" x2="{TX(hit):.1f}" y1="{MT2}" y2="{MT2+H2}" class="hitline"/><text x="{TX(hit)+5:.1f}" y="{MT2+12}" class="lab">the hit</text>')
o.append(f'<text x="{ML2+W2}" y="{MT2+H2+31}" class="lab" text-anchor="end">ms into the move</text>')
for key,cls in (("current","today"),("proto","proto")):
    rows=d[key]
    pts=" ".join(f"{TX(r[0]):.1f},{VY(r[5]):.1f}" for r in rows)
    o.append(f'<polyline points="{pts}" class="p {cls}"/>')
o.append('</svg>')
open("speed.svg","w").write("\n".join(o))
print(len(open("frame.svg").read()), len(open("speed.svg").read()))
