#!/usr/bin/env python3
"""Builds docs/coop/page.html from page.src.html: every {{NAME}} is replaced by a drawing made here.

The drawings are mockups of game screens (nothing in them is built). They are drawn as fixed dark scenes, like a
screenshot would be, whatever the page's theme. Run: python3 docs/coop/build_page.py
"""
from pathlib import Path

HERE = Path(__file__).parent

# the game's own colours, as the mockups use them
HOLO = "#7fd4ff"        # the band's light, the fighter
ROSE = "#f0b79c"        # the thread's halo, the rider
IVORY = "#fbf0dc"       # the thread
GOLD = "#ffe9a8"        # a light blow's tell
AMBER = "#ffab3d"       # a heavy blow's tell
RED = "#ff5b5b"         # a perilous blow's tell
GREEN = "#7bd69a"
INK = "#e9edf3"
DIM = "#93a0b3"
FONT = "font-family=\"Inter, system-ui, sans-serif\""
MONO = "font-family=\"JetBrains Mono, ui-monospace, Menlo, monospace\""


def t(x, y, s, size=11, fill=INK, anchor="start", weight=500, mono=False):
    return (f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" text-anchor="{anchor}" font-weight="{weight}" '
            f'{MONO if mono else FONT}>{s}</text>')


def hall(w=640, h=360, tint="#141a24"):
    """An arena: a floor running to a far wall, two pillars."""
    return f'''<rect width="{w}" height="{h}" fill="#090c11"/>
<polygon points="0,{h} {w},{h} {w*0.72},{h*0.56} {w*0.28},{h*0.56}" fill="{tint}"/>
<rect x="{w*0.28}" y="{h*0.16}" width="{w*0.44}" height="{h*0.40}" fill="#0e131b"/>
<g stroke="#1e2633" stroke-width="1">
<line x1="{w*0.10}" y1="{h}" x2="{w*0.36}" y2="{h*0.56}"/><line x1="{w*0.30}" y1="{h}" x2="{w*0.43}" y2="{h*0.56}"/>
<line x1="{w*0.5}" y1="{h}" x2="{w*0.5}" y2="{h*0.56}"/><line x1="{w*0.70}" y1="{h}" x2="{w*0.57}" y2="{h*0.56}"/>
<line x1="{w*0.90}" y1="{h}" x2="{w*0.64}" y2="{h*0.56}"/>
<line x1="{w*0.17}" y1="{h*0.78}" x2="{w*0.83}" y2="{h*0.78}"/><line x1="{w*0.235}" y1="{h*0.66}" x2="{w*0.765}" y2="{h*0.66}"/>
</g>
<rect x="{w*0.20}" y="{h*0.08}" width="{w*0.05}" height="{h*0.55}" fill="#111722"/>
<rect x="{w*0.75}" y="{h*0.08}" width="{w*0.05}" height="{h*0.55}" fill="#111722"/>'''


def lord(cx, base, s=1.0, pose="stand", eye="#ffb45e"):
    """A gate's Lord as a silhouette: broad shoulders, a horned head, one long arm. pose: stand, raise, kneel."""
    if pose == "kneel":
        body = f'M{cx-40*s},{base} L{cx-46*s},{base-52*s} Q{cx-10*s},{base-96*s} {cx+34*s},{base-70*s} L{cx+50*s},{base-30*s} L{cx+44*s},{base} Z'
        head = (cx + 22 * s, base - 82 * s)
        arm = f'M{cx+30*s},{base-60*s} L{cx+70*s},{base-20*s} L{cx+62*s},{base-12*s} L{cx+22*s},{base-48*s} Z'
    else:
        body = f'M{cx-38*s},{base} L{cx-52*s},{base-96*s} Q{cx},{base-140*s} {cx+52*s},{base-96*s} L{cx+38*s},{base} Z'
        head = (cx, base - 128 * s)
        if pose == "raise":
            arm = f'M{cx+40*s},{base-104*s} L{cx+92*s},{base-176*s} L{cx+104*s},{base-168*s} L{cx+58*s},{base-90*s} Z'
        else:
            arm = f'M{cx+44*s},{base-98*s} L{cx+74*s},{base-30*s} L{cx+62*s},{base-26*s} L{cx+36*s},{base-88*s} Z'
    hx, hy = head
    return f'''<g>
<path d="{body}" fill="#333c4e"/><path d="{arm}" fill="#3a4458"/>
<path d="M{hx-18*s},{hy+14*s} L{hx-22*s},{hy-12*s} L{hx-34*s},{hy-34*s} L{hx-10*s},{hy-20*s} L{hx+10*s},{hy-20*s} L{hx+34*s},{hy-34*s} L{hx+22*s},{hy-12*s} L{hx+18*s},{hy+14*s} Z" fill="#3a4458"/>
<circle cx="{hx-8*s}" cy="{hy-2*s}" r="{2.6*s}" fill="{eye}"/><circle cx="{hx+8*s}" cy="{hy-2*s}" r="{2.6*s}" fill="{eye}"/>
</g>'''


def add(cx, base, s=1.0, bound=False):
    g = f'<path d="M{cx-12*s},{base} L{cx-14*s},{base-30*s} Q{cx},{base-44*s} {cx+14*s},{base-30*s} L{cx+12*s},{base} Z" fill="#2a3140"/>' \
        f'<circle cx="{cx-4*s}" cy="{base-30*s}" r="{1.6*s}" fill="#ff8f6b"/><circle cx="{cx+4*s}" cy="{base-30*s}" r="{1.6*s}" fill="#ff8f6b"/>'
    if bound:
        g += f'<ellipse cx="{cx}" cy="{base-4*s}" rx="{17*s}" ry="{5*s}" fill="none" stroke="{IVORY}" stroke-width="2"/>' \
             f'<ellipse cx="{cx}" cy="{base-10*s}" rx="{15*s}" ry="{4*s}" fill="none" stroke="{ROSE}" stroke-width="1.5"/>'
    return g


def hunter(cx, base, s=1.0, colour=HOLO, pose="stand", flip=False):
    """A player seen from outside. pose: stand, strike, kneel."""
    k = -1 if flip else 1
    if pose == "kneel":
        return f'''<g><path d="M{cx-9*s},{base} L{cx-10*s},{base-22*s} L{cx+8*s},{base-24*s} L{cx+14*s},{base} Z" fill="#3a4658"/>
<circle cx="{cx+2*s}" cy="{base-31*s}" r="{7*s}" fill="#3a4658"/>
<rect x="{cx-12*s}" y="{base-26*s}" width="{24*s}" height="{3*s}" fill="{colour}" opacity="0.85"/></g>'''
    sword = (f'<path d="M{cx+k*10*s},{base-34*s} L{cx+k*40*s},{base-62*s} L{cx+k*42*s},{base-58*s} L{cx+k*13*s},{base-30*s} Z" fill="#cfd8e6"/>'
             if pose == "strike" else
             f'<path d="M{cx+k*10*s},{base-26*s} L{cx+k*16*s},{base-58*s} L{cx+k*19*s},{base-57*s} L{cx+k*14*s},{base-25*s} Z" fill="#cfd8e6"/>')
    return f'''<g><path d="M{cx-8*s},{base} L{cx-10*s},{base-40*s} L{cx+10*s},{base-40*s} L{cx+8*s},{base} Z" fill="#3a4658"/>
<circle cx="{cx}" cy="{base-48*s}" r="{7*s}" fill="#3a4658"/>
<rect x="{cx-11*s}" y="{base-41*s}" width="{22*s}" height="{3*s}" fill="{colour}" opacity="0.85"/>{sword}</g>'''


def thread(x0, y0, x1, y1, sag=26, beads=0, colour=IVORY, halo=ROSE, dash=""):
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2 + sag
    d = f'M{x0},{y0} Q{mx},{my} {x1},{y1}'
    out = f'<path d="{d}" fill="none" stroke="{halo}" stroke-width="5" opacity="0.28" stroke-linecap="round"/>' \
          f'<path d="{d}" fill="none" stroke="{colour}" stroke-width="1.6" stroke-linecap="round" {dash}/>'
    for i in range(beads):
        u = (i + 1) / (beads + 1)
        bx = (1 - u) ** 2 * x0 + 2 * (1 - u) * u * mx + u * u * x1
        by = (1 - u) ** 2 * y0 + 2 * (1 - u) * u * my + u * u * y1
        out += f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="3.2" fill="{IVORY}"/><circle cx="{bx:.1f}" cy="{by:.1f}" r="6" fill="{ROSE}" opacity="0.35"/>'
    return out


def motes(points, colour=IVORY):
    return "".join(f'<circle cx="{x}" cy="{y}" r="2.4" fill="{colour}"/><circle cx="{x}" cy="{y}" r="5.5" fill="{ROSE}" opacity="0.28"/>'
                   for x, y in points)


def plate(cx, y, name, health, stance, w=250, broken=False):
    x = cx - w / 2
    return f'''{t(cx, y, name, 10, INK, "middle", 600, True)}
<rect x="{x}" y="{y+7}" width="{w}" height="7" rx="1.5" fill="#1b212c"/><rect x="{x}" y="{y+7}" width="{w*health}" height="7" rx="1.5" fill="#d9584f"/>
<rect x="{x}" y="{y+17}" width="{w}" height="3" rx="1" fill="#1b212c"/><rect x="{x}" y="{y+17}" width="{w*stance}" height="3" rx="1" fill="{GOLD if not broken else '#ffffff'}"/>'''


def strip(x, y, rows):
    """The party strip: rows of (name, colour, health 0..1, state)."""
    out = ""
    for i, (name, colour, hp, state) in enumerate(rows):
        yy = y + i * 30
        out += f'''<rect x="{x}" y="{yy}" width="168" height="25" rx="4" fill="#0d1219" opacity="0.86" stroke="#243042"/>
<circle cx="{x+12}" cy="{yy+12.5}" r="4" fill="{colour}"/>{t(x+22, yy+11, name, 10.5, INK, weight=600)}
<rect x="{x+22}" y="{yy+15}" width="74" height="4" rx="1" fill="#1b212c"/><rect x="{x+22}" y="{yy+15}" width="{74*hp}" height="4" rx="1" fill="{GREEN if hp > 0.35 else RED}"/>
{t(x+162, yy+16, state, 9, DIM, "end")}'''
    return out


def powers(cx, y, keys, light=None, light_max=30):
    """A power bar: keys is a list of (key, name, ready)."""
    n, bw, gap = len(keys), 46, 6
    x0 = cx - (n * bw + (n - 1) * gap) / 2
    out = ""
    for i, (key, name, ready) in enumerate(keys):
        x = x0 + i * (bw + gap)
        out += f'''<rect x="{x}" y="{y}" width="{bw}" height="30" rx="4" fill="#0d1219" opacity="0.9" stroke="{'#3b4a60' if ready else '#222a36'}"/>
{t(x+6, y+12, key, 9, HOLO if ready else DIM, weight=700, mono=True)}{t(x+bw/2, y+24, name, 9, INK if ready else DIM, "middle")}'''
    if light is not None:
        lx = x0 - 96
        out += f'''<rect x="{lx}" y="{y}" width="84" height="30" rx="4" fill="#0d1219" opacity="0.9" stroke="#3b3027"/>
{t(lx+8, y+12, "LIGHT", 8, ROSE, weight=700, mono=True)}{t(lx+76, y+12, f"{light} / {light_max}", 9, IVORY, "end", mono=True)}
<rect x="{lx+8}" y="{y+18}" width="68" height="5" rx="1.5" fill="#241d19"/><rect x="{lx+8}" y="{y+18}" width="{68*light/light_max:.1f}" height="5" rx="1.5" fill="{IVORY}"/>'''
    return out


def ring(cx, cy, r, colour, tell=None, thin=False):
    out = f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{colour}" stroke-width="{1 if thin else 1.6}" opacity="{0.5 if thin else 0.9}"/>'
    if tell:
        out += f'<circle cx="{cx}" cy="{cy}" r="{r*1.55}" fill="none" stroke="{tell}" stroke-width="{1.2 if thin else 3}" opacity="{0.55 if thin else 1}" {"stroke-dasharray=\"3 5\"" if thin else ""}/>'
    return out


def edge_arrow(x, y, name, dist, colour, side="right"):
    k = 1 if side == "right" else -1
    return f'''<polygon points="{x},{y} {x-k*12},{y-7} {x-k*12},{y+7}" fill="{colour}"/>
{t(x-k*17, y-1, name, 10, INK, "end" if side == "right" else "start", 600)}{t(x-k*17, y+11, dist, 9, DIM, "end" if side == "right" else "start", mono=True)}'''


def ping(x, y, who, line, colour):
    return f'''<path d="M{x},{y} L{x-7},{y-16} L{x+7},{y-16} Z" fill="{colour}"/><circle cx="{x}" cy="{y-24}" r="9" fill="none" stroke="{colour}" stroke-width="2"/>
<circle cx="{x}" cy="{y}" r="14" fill="none" stroke="{colour}" stroke-width="1" opacity="0.45"/>
{t(x, y-44, who, 9.5, colour, "middle", 700)}{t(x, y+30, line, 10.5, INK, "middle", 500)}'''


def frame(body, w=640, h=360, label=None, minw=560):
    lab = ""
    if label:
        lab = f'<rect x="{w-162}" y="{h-22}" width="154" height="15" rx="3" fill="#0d1219" opacity="0.8"/>' + \
              t(w - 85, h - 11, label, 8.5, DIM, "middle", mono=True)
    return (f'<div class="shot"><svg viewBox="0 0 {w} {h}" role="img" style="min-width:{minw}px" xmlns="http://www.w3.org/2000/svg">'
            f'{body}{lab}</svg></div>')


# ---- the two big views of one moment ---------------------------------------------------------------------------------

def m1_fighter():
    b = hall()
    b += lord(322, 250, 1.18, "raise")
    b += ring(322, 150, 44, HOLO, AMBER)
    b += plate(320, 22, "LORD OF THE AMBER REACH", 0.62, 0.74)
    # your own blade, bottom right
    b += '<path d="M470,360 L400,236 L408,231 L500,360 Z" fill="#cfd8e6"/><path d="M462,360 L452,340 L488,326 L506,360 Z" fill="#5a4630"/>'
    b += thread(150, 360, 640, 262, sag=30, beads=3)
    b += edge_arrow(632, 244, "Ana", "9 blocks", ROSE)
    b += strip(12, 44, [("Ana", ROSE, 0.9, "riding along")])
    b += t(322, 232, "amber: dodge to the side", 10, AMBER, "middle", 600)
    b += powers(320, 318, [("Z", "Haymaker", True), ("X", "Dash", True), ("C", "Brace", False), ("V", "Shot", True), ("R", "Barrier", True)])
    b += motes([(250, 300), (382, 286), (300, 270)])
    return frame(b, label="MOCKUP · THE FIGHTER'S SCREEN")


def m4_rider():
    b = hall()
    b += lord(398, 236, 0.92, "raise")
    b += ring(398, 158, 30, HOLO, AMBER, thin=True)
    b += hunter(300, 262, 1.25, HOLO, "strike")
    b += plate(320, 22, "LORD OF THE AMBER REACH", 0.62, 0.74)
    b += motes([(350, 268), (420, 280), (250, 290), (466, 262), (330, 310), (204, 322), (510, 300)])
    # your hand with the thread, bottom right
    b += '<path d="M640,330 L560,322 L536,306 L544,296 L572,306 L640,300 Z" fill="#c9a58a"/>'
    b += thread(544, 300, 312, 222, sag=34, beads=3)
    b += strip(12, 44, [("Euan", HOLO, 0.55, "this blow is his")])
    b += t(452, 196, "thin ring: not your blow", 9.5, DIM, "start")
    # the mark over the Lord
    b += f'<path d="M398,84 l-7,-11 h14 z" fill="{ROSE}"/>' + t(398, 68, "your mark", 9, ROSE, "middle", 600)
    b += powers(336, 318, [("Z", "Mark", True), ("X", "Mend", True), ("C", "Bind", True), ("V", "Haul", False), ("R", "Ward", True)], light=18)
    b += t(110, 300, "3 motes in reach", 9.5, IVORY, "middle")
    return frame(b, label="MOCKUP · THE RIDER'S SCREEN")


# ---- pings ----------------------------------------------------------------------------------------------------------

def m2_pings():
    w, h = 640, 220
    b = f'<rect width="{w}" height="{h}" fill="#090c11"/>'
    # three small scenes side by side
    for i in range(3):
        x = 8 + i * 211
        b += f'<rect x="{x}" y="8" width="202" height="204" rx="6" fill="#0e131b" stroke="#1e2633"/>'
        b += f'<polygon points="{x},212 {x+202},212 {x+150},130 {x+52},130" fill="#141a24"/>'
    # 1: a place on the ground
    b += ping(110, 168, "ANA", "Over here · 24 blocks", ROSE)
    b += t(110, 28, "middle click on the ground", 9, DIM, "middle", mono=True)
    # 2: a gate door
    b += '<rect x="290" y="62" width="62" height="88" rx="30" fill="#17202e" stroke="#3a5a86" stroke-width="2"/><ellipse cx="321" cy="106" rx="20" ry="34" fill="#22406a" opacity="0.7"/>'
    b += ping(321, 150, "EUAN", "Let's go in · D-rank gate", HOLO)
    b += t(321, 28, "middle click on a gate", 9, DIM, "middle", mono=True)
    # 3: shared focus
    b += add(532, 166, 1.5)
    b += ring(532, 126, 26, HOLO)
    b += f'<path d="M532,86 l-6,-10 h12 z" fill="{HOLO}"/>' + t(532, 70, "EUAN'S TARGET", 9, HOLO, "middle", 700)
    b += t(532, 198, "a lock is shared, no extra press", 10, INK, "middle")
    b += t(532, 28, "middle click on a monster", 9, DIM, "middle", mono=True)
    return frame(b, w, h, minw=560)


# ---- cards -----------------------------------------------------------------------------------------------------------

def card(x, y, w, h, title, kicker):
    return f'''<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="#0d141d" opacity="0.96" stroke="#2c4763"/>
<rect x="{x}" y="{y}" width="{w}" height="26" rx="6" fill="#132233"/>{t(x+12, y+17, kicker, 9, HOLO, weight=700, mono=True)}
{t(x+12, y+48, title, 15, INK, weight=600)}'''


def button(x, y, w, label, sub, colour, primary=False):
    return f'''<rect x="{x}" y="{y}" width="{w}" height="58" rx="5" fill="{'#152232' if primary else '#10161f'}" stroke="{colour}" stroke-width="{1.6 if primary else 1}"/>
{t(x+12, y+22, label, 12, colour, weight=700)}{t(x+12, y+40, sub[0], 9.5, INK)}{t(x+12, y+52, sub[1], 9.5, DIM)}'''


def m3_door():
    w, h = 640, 300
    b = hall(w, h, "#131a25")
    b += '<rect x="278" y="52" width="84" height="118" rx="40" fill="#17202e" stroke="#3a5a86" stroke-width="2"/><ellipse cx="320" cy="112" rx="28" ry="46" fill="#22406a" opacity="0.7"/>'
    b += card(110, 120, 420, 164, "Go in together, which way?", "D-RANK GATE · WITH ANA")
    b += button(124, 180, 192, "Fight side by side", ("Both of you fight. The gate is", "a little harder. Full pay each."), HOLO, True)
    b += button(324, 180, 192, "Ana rides along", ("She can't go down near you and", "works the thread. Half to 3/4 pay."), ROSE)
    b += t(320, 262, "Ana sees the same card. It needs a yes from both. Runs out in 30 s and chooses nothing.", 9.5, DIM, "middle")
    b += t(320, 276, "Y accept the lit one  ·  ← → the other way  ·  N go in alone", 9, DIM, "middle", mono=True)
    return frame(b, w, h, label="MOCKUP · AT A GATE'S DOOR")


def m5_together():
    w, h = 640, 380
    b = f'<rect width="{w}" height="{h}" fill="#090c11"/>'
    b += f'<rect x="20" y="16" width="600" height="330" rx="8" fill="#0d141d" stroke="#2c4763"/>'
    b += t(40, 44, "STATUS", 9, DIM, weight=700, mono=True) + t(100, 44, "TOGETHER", 9, HOLO, weight=700, mono=True)
    b += f'<line x1="100" y1="50" x2="158" y2="50" stroke="{HOLO}" stroke-width="2"/><line x1="40" y1="58" x2="600" y2="58" stroke="#1e2a3a"/>'
    rows = [
        ("Ana", ROSE, "Level 7 · E rank", "In your party · shares your home · 12 blocks away",
         [("Ride along", ROSE), ("Bring along", HOLO), ("Leave party", DIM)]),
        ("Tomas", "#b9d47a", "Level 22 · D rank", "48 blocks away · not in your party",
         [("Travel together", HOLO)]),
    ]
    y = 74
    for name, colour, lvl, where, acts in rows:
        b += f'<rect x="36" y="{y}" width="568" height="82" rx="6" fill="#10161f" stroke="#1e2a3a"/>'
        b += f'<circle cx="60" cy="{y+26}" r="10" fill="{colour}" opacity="0.9"/>' + t(80, y + 24, name, 14, INK, weight=600) + t(80, y + 40, lvl, 10, DIM, mono=True)
        b += t(596, y + 24, where, 10, DIM, "end")
        x = 80
        for label, c in acts:
            bw = 12 + len(label) * 6.4
            b += f'<rect x="{x}" y="{y+52}" width="{bw}" height="22" rx="4" fill="#0d141d" stroke="{c}"/>' + t(x + bw / 2, y + 67, label, 10, c, "middle", 600)
            x += bw + 8
        y += 92
    b += f'<rect x="36" y="{y}" width="568" height="52" rx="6" fill="#10161f" stroke="#1e2a3a"/>'
    b += t(52, y + 21, "Your home", 11, INK, weight=600) + t(52, y + 38, "Kept by you and Ana. Tomas isn't a member.", 10, DIM)
    b += f'<rect x="452" y="{y+15}" width="136" height="22" rx="4" fill="#0d141d" stroke="{HOLO}"/>' + t(520, y + 30, "Share with Tomas", 10, HOLO, "middle", 600)
    b += t(320, 332, "Each button sends the other person a card. Nothing here is a typed command.", 9.5, DIM, "middle")
    return frame(b, w, h, label="MOCKUP · THE BAND, STATUS PAGE")


# ---- the storyboard: one fight, six beats, both screens -------------------------------------------------------------

def beat(view, n):
    """One small panel of the storyboard. view: 'f' or 'r'. Big marks, few words."""
    w, h = 320, 190
    b = hall(w, h)
    L = lambda pose="stand": lord(196 if view == "r" else 160, 132, 0.62 if view == "r" else 0.72, pose)
    H = lambda pose="stand": hunter(136, 142, 0.9, HOLO, pose)
    hand = '<path d="M320,176 L276,172 L262,163 L267,157 L282,163 L320,160 Z" fill="#c9a58a"/>'
    blade = '<path d="M240,190 L206,128 L211,125 L256,190 Z" fill="#cfd8e6"/>'
    if view == "f":
        cx, cy = 160, 70
        if n == 1:
            b += L() + ring(cx, cy, 26, HOLO) + blade + thread(70, 190, 320, 150, 14, 0)
            b += f'<path d="M160,22 l-6,-10 h12 z" fill="{ROSE}"/>' + cap("Ana's mark lights it", ROSE)
        elif n == 2:
            b += L("raise") + ring(cx, cy, 26, HOLO) + blade + thread(70, 190, 320, 150, 14, 0) + motes([(120, 150), (200, 146), (150, 164)])
            b += cap("your blows shed light", IVORY)
        elif n == 3:
            b += L() + ring(cx, cy, 26, HOLO) + blade + thread(70, 190, 320, 150, 14, 3)
            b += '<rect x="218" y="10" width="92" height="9" rx="2" fill="#1b212c"/><rect x="218" y="10" width="38" height="9" rx="2" fill="#7bd69a"/>'
            b += cap("beads reach you: a heart a second", GREEN)
        elif n == 4:
            b += L() + add(64, 150, 1.1, bound=True) + ring(cx, cy, 26, HOLO) + blade + thread(70, 190, 320, 150, 14, 0)
            b += cap("the one at your back is tied", IVORY)
        elif n == 5:
            b += L("raise") + ring(cx, cy, 26, HOLO, RED) + blade + thread(70, 190, 320, 150, 14, 0)
            b += f'<rect x="3" y="3" width="{w-6}" height="{h-6}" rx="10" fill="none" stroke="{IVORY}" stroke-width="3" opacity="0.8"/>'
            b += cap("red is coming: the ward is on you", IVORY)
        else:
            b += L("kneel") + blade + thread(70, 190, 320, 150, 14, 0)
            b += '<rect x="0" y="0" width="320" height="16" fill="#000"/><rect x="0" y="174" width="320" height="16" fill="#000"/>'
            b += t(160, 168, "the finishing blow: both of you in the shot", 10.5, GOLD, "middle", 600)
    else:
        if n == 1:
            b += L() + H() + hand + thread(266, 160, 144, 108, 16, 0)
            b += f'<path d="M196,36 l-6,-10 h12 z" fill="{ROSE}"/>' + cap("look at it, press Mark", ROSE)
        elif n == 2:
            b += L("raise") + H("strike") + hand + thread(266, 160, 144, 108, 16, 0) + motes([(180, 150), (222, 142), (100, 158), (60, 170), (250, 152)])
            b += '<path d="M250,176 q-40,-10 -66,-26" fill="none" stroke="#fbf0dc" stroke-width="1.2" stroke-dasharray="3 4"/>'
            b += cap("run the floor and gather it", IVORY)
        elif n == 3:
            b += L() + H() + hand + thread(266, 160, 144, 108, 16, 3)
            b += cap("hold Mend: light runs down the thread", GREEN)
        elif n == 4:
            b += L() + H("strike") + add(84, 150, 1.0, bound=True) + hand + thread(266, 160, 144, 108, 16, 0)
            b += cap("Bind the one behind him", IVORY)
        elif n == 5:
            b += L("raise") + ring(196, 76, 20, HOLO, RED, thin=True) + H() + hand + thread(266, 160, 144, 108, 16, 0)
            b += f'<ellipse cx="136" cy="118" rx="24" ry="34" fill="none" stroke="{IVORY}" stroke-width="2.5"/><ellipse cx="136" cy="118" rx="28" ry="38" fill="none" stroke="{ROSE}" stroke-width="1" opacity="0.6"/>'
            b += cap("red shows: Ward, or Haul him out", IVORY)
        else:
            b += L("kneel") + H("strike") + hunter(104, 146, 0.8, ROSE, "stand", flip=True) + thread(108, 114, 140, 108, 6, 0)
            b += '<rect x="0" y="0" width="320" height="16" fill="#000"/><rect x="0" y="174" width="320" height="16" fill="#000"/>'
            b += t(160, 168, "you're in the film, thread in hand", 10.5, GOLD, "middle", 600)
    return f'<svg viewBox="0 0 {w} {h}" role="img" xmlns="http://www.w3.org/2000/svg">{b}</svg>'


def cap(s, colour, y=180):
    """A beat's one line, on a band so it never fights the drawing."""
    return f'<rect x="0" y="{y-14}" width="320" height="24" fill="#090c11" opacity="0.82"/>' + t(160, y + 1, s, 11, colour, "middle", 600)


BEATS = [
    ("0:00", "The Lord wakes", "Ana marks it. Monsters go for Euan first, so she stands off to the side."),
    ("0:10", "Blows land", "Every blow Euan lands sheds a mote. Ana runs the floor for them: 14 light."),
    ("0:25", "He takes a hit", "Ana holds Mend. It pauses each time he's struck, so it can't outrun the fight."),
    ("0:40", "It calls two more", "Ana ties the one running at his back for four seconds."),
    ("1:05", "Red", "Nothing blocks a red blow. Ana wards him (the hit is halved) or hauls him to her side."),
    ("1:30", "It kneels", "Euan takes the finishing blow. The film frames both of them."),
]


def s1_storyboard():
    rows = []
    for i, (at, title, line) in enumerate(BEATS, 1):
        rows.append(f'''<div class="beat"><div class="beat-head"><span class="num">{at}</span><h4>{title}</h4><p>{line}</p></div>
<figure class="pane f"><span class="who f">Euan fights</span>{beat("f", i)}</figure>
<figure class="pane r"><span class="who r">Ana rides</span>{beat("r", i)}</figure></div>''')
    return '<div class="board">' + "".join(rows) + '</div>'


# ---- a Reassessment with one riding ---------------------------------------------------------------------------------

def s2_panel(n):
    w, h = 320, 190
    b = hall(w, h, "#15161f")
    if n == 1:
        b += '<rect x="146" y="70" width="28" height="60" fill="#232a36"/><path d="M140,70 q20,-34 40,0 z" fill="#3a3222"/><circle cx="160" cy="62" r="4" fill="#ffe9a8"/>'
        b += hunter(118, 150, 0.95, HOLO) + hunter(206, 150, 0.9, ROSE, flip=True) + thread(196, 114, 128, 112, 12, 0)
        b += cap("tied, at the Reading Bell", INK)
    elif n == 2:
        b += lord(190, 132, 0.66, "raise", "#b0c8ff") + hunter(112, 144, 0.9, HOLO, "strike") + '<path d="M320,176 L276,172 L262,163 L267,157 L282,163 L320,160 Z" fill="#c9a58a"/>'
        b += thread(266, 160, 120, 110, 16, 2)
        b += '<rect x="196" y="8" width="116" height="26" rx="4" fill="#0d1219" opacity="0.9" stroke="#3b3027"/>'
        b += t(204, 19, "THE THREAD'S TASK", 7.5, ROSE, weight=700, mono=True)
        for i in range(12):
            b += f'<circle cx="{206+i*9}" cy="27" r="3" fill="{IVORY if i < 7 else "#2a241f"}"/>'
        b += cap("the Warden is his; the beads are hers", INK)
    elif n == 3:
        b += lord(190, 132, 0.66, "kneel", "#b0c8ff") + hunter(120, 144, 0.9, HOLO, "strike") + hunter(92, 148, 0.8, ROSE, flip=True) + thread(96, 116, 124, 110, 6, 0)
        b += cap("it's beaten", GOLD)
    else:
        for i, (c, lab, sub) in enumerate([(HOLO, "D", "Euan"), (ROSE, "D", "Ana")]):
            x = 62 + i * 110
            b += f'<rect x="{x}" y="40" width="88" height="112" rx="5" fill="#ede3cc"/><rect x="{x}" y="40" width="88" height="16" rx="5" fill="#cbbd9b"/>'
            b += t(x + 44, 52, "HUNTER'S LICENCE", 6.5, "#5c5037", "middle", 700, True)
            b += f'<circle cx="{x+44}" cy="96" r="22" fill="none" stroke="#3d6fa8" stroke-width="2.5"/>' + t(x + 44, 106, lab, 26, "#3d6fa8", "middle", 700)
            if i == 1:
                b += f'<path d="M{x+14},100 Q{x+44},84 {x+74},100" fill="none" stroke="#b8643c" stroke-width="2"/><circle cx="{x+44}" cy="92" r="2.6" fill="#b8643c"/>'
            b += t(x + 44, 140, sub, 9.5, "#3a3324", "middle", 600)
        b += cap("the same rank; hers shows the thread", INK)
    return f'<svg viewBox="0 0 {w} {h}" role="img" xmlns="http://www.w3.org/2000/svg">{b}</svg>'


def s2_proving():
    caps = [
        ("You go in tied", "Both of you wake in the hall. The Warden gets no extra health for her, and never picks her while you stand."),
        ("Two tests in one fight", "Yours is the Warden. Hers is the thread's task: keep the thread whole, go down no more than twice, do twelve deeds."),
        ("You win", "Whatever the Warden leaves goes to both of you. The Ringer's chain is the rope, and she gets hers too."),
        ("Both licences", "You take D. With her task done, she takes D by the thread. If it wasn't, she's told what was short and can go again."),
    ]
    out = "".join(f'<figure class="step">{s2_panel(i)}<figcaption><strong>{a}.</strong> {c}</figcaption></figure>' for i, (a, c) in enumerate(caps, 1))
    return f'<div class="steps">{out}</div>'


def main():
    src = (HERE / "page.src.html").read_text()
    parts = {"M1": m1_fighter(), "M4": m4_rider(), "M2": m2_pings(), "M3": m3_door(), "M5": m5_together(),
             "S1": s1_storyboard(), "S2": s2_proving()}
    for k, v in parts.items():
        assert "{{" + k + "}}" in src, k
        src = src.replace("{{" + k + "}}", v)
    assert "{{" not in src, "an unfilled marker is left"
    (HERE / "page.html").write_text(src)
    print("page.html", len(src) // 1024, "KB")


if __name__ == "__main__":
    main()
