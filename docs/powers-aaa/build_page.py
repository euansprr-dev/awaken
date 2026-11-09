#!/usr/bin/env python3
"""Builds plan.html (the approval page) and PLAN.md from powers_data.py, scenes.py and taster/levels.json.

Run: python3 docs/powers-aaa/build_page.py
"""
import base64
import html
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from powers_data import P, DUETS, NEW, STATES, RECS  # noqa: E402
import scenes  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ICONS = os.path.join(ROOT, "src/main/resources/assets/awaken/textures/gui/sprites/power")
TASTER = os.path.join(HERE, "taster")
PREVIEW = sys.argv[1] if len(sys.argv) > 1 else None

e = lambda s: html.escape(str(s), quote=False)
BY = {p["id"]: p for p in P}
STARTERS = ["haymaker", "shockwave", "charged_shot", "beam", "spirit_palm", "shadow_step"]
CHANNEL = ["beam", "charged_shot", "prism_lance", "telekinesis", "haymaker", "railgun"]
TOGGLE = ["hover", "flight"]
PASSIVE = ["danger_sense", "aura", "wall_run", "water_run"]
CHARGED = ["haymaker", "charged_shot", "railgun"]

FAM = {
    "Kinetic": ("--kin", "Stone and sub", "The waking body. Ordinary people who found out what they could move."),
    "Velocity": ("--vel", "Air and tape", "Time. People who got somewhere early and couldn't explain how."),
    "Flight": ("--fli", "Pressure and wind", "The Above. Ascendants, and people who simply stopped coming down."),
    "Energy": ("--ene", "Plasma, capacitor, shimmer", "The Halo and the sun. Light that learned to do as it's told."),
    "Shadow": ("--sha", "Reversed ink and breath", "The Deep. What comes back up after it has been forgotten."),
    "Mind": ("--min", "Glass harmonics and a voice", "Partly the Lucidium. People who could tell what you'd do next."),
    "Architect": ("--arc", "Wood and stone, ratchets", "The Sleepers, and one woman called Ida Rask who couldn't stop building."),
    "Tech": ("--tec", "Servo, relay, gauss", "The Above again. Things the Ascendants left running."),
    "Spirit": ("--spi", "Bowls, temple, breath", "The Veil. A catch-all family of 14, and the one that most needs sorting."),
}
VERDICT = {"KEEP": "Keep and polish", "GOOD": "Good, needs a hook", "FLAT": "Flat", "RETHINK": "Start again", "FOLD": "Fold into one"}
COUNT = {k: sum(1 for p in P if p["verdict"] == k) for k in VERDICT}

IF_NO = {
    "direction": "I'd build the small option instead: sounds, stance, dead buttons and the vanilla leftovers. Decent, but short of the bar you set.",
    "clean": "Powers stay one press and number 12 falls away. The rest still works, but you lose the main way of getting better at a power.",
    "stance": "Blades stay the only way to break a boss, and casters keep feeling second best there.",
    "dead": "Refusals stay as they are, except the seven that charge you for nothing, which I'd fix anyway.",
    "duets": "Powers go on working side by side, and the seven existing reactions stay.",
    "party": "Duets only work with your own two powers.",
    "ult": "R keeps its 60 second timer and stays empty until a Path or Rank C.",
    "move": "The eight stay as they are, next to the Ascent moves that overlap them.",
    "blueprint": "Architect keeps four of your six keys on building shapes.",
    "world": "Effects stay drawn on top of the world. Everything else in the plan still happens.",
    "sound": "Today's sounds stay. I'd still fix Gravity Well's silent three and a half seconds and split Haymaker from Railgun.",
    "notes": "Clean still rings, as one sound each time, with no rising phrase.",
    "readings": "Descriptions stay as they are today.",
    "yours": "Your starter's last reading is an ordinary one.",
    "cores": "Cores stay random by rank.",
    "way": "Level 5 stays exactly as it is today, which you already like.",
    "new": "Five fewer powers. Kinetic, Velocity and Mind players get offered Overload, Legion or Gravity Flip for their first ultimate.",
    "tasks": "Powers stay off the Tasks page, which breaks your standing rule, so I'd ask you to think again.",
    "proof": "I'd build straight through. It's faster, but it's much harder to stop cheaply if Clean turns out to feel wrong.",
}

TITLE = "Powers in Full Voice"


def b64(path):
    with open(path, "rb") as f:
        return base64.b64encode(f.read()).decode()


def ic(pid, title=True):
    t = ' title="%s"' % e(BY[pid]["name"]) if title else ""
    return '<i class="ic ic-%s"%s></i>' % (pid, t)


def table(head, rows, cls=""):
    out = ['<div class="scroll" tabindex="0"><table class="%s"><thead><tr>' % cls]
    out += ["<th>%s</th>" % h for h in head]
    out.append("</tr></thead><tbody>")
    for r in rows:
        out.append("<tr>" + "".join("<td>%s</td>" % c for c in r) + "</tr>")
    out.append("</tbody></table></div>")
    return "".join(out)


def h2(num, title, sid):
    return '<h2 id="%s"><span class="n">%s</span>%s</h2>' % (sid, num, title)


# ---- charts -----------------------------------------------------------------------------------------------------------------
def press_chart():
    one = [p["id"] for p in P if p["id"] not in CHANNEL + TOGGLE + PASSIVE]
    groups = [("One press, then a cooldown", one), ("Held", CHANNEL), ("Toggled on and off", TOGGLE), ("Always on", PASSIVE)]
    out = ['<figure class="census"><div class="groups">']
    for name, ids in groups:
        cells = "".join('<span class="%s">%s</span>' % ("charged" if i in CHARGED else "", ic(i)) for i in ids)
        out.append('<div class="group"><div class="ghead"><b>%d</b> %s</div><div class="icons">%s</div></div>' % (len(ids), name, cells))
    out.append('</div><figcaption>Each square is one power, using its real icon. The three with a gold edge are the only ones with a charged release you can time: Haymaker, Charged Shot and Railgun. Counted from the catalogue code.</figcaption></figure>')
    return "".join(out)


def sound_chart():
    uses = [("Whoosh", 17), ("Shadow", 8), ("Build", 8), ("Spirit", 7), ("Impact", 7), ("Gravity", 7), ("Thunder", 5), ("Rail", 4), ("Mind", 4), ("Charge-up", 4), ("Void", 3), ("Summon", 3), ("Slam", 3), ("Throw", 2)]
    top = uses[0][1]
    rows = "".join('<div class="br" title="%s: called %d times"><span class="bl">%s</span><span class="bt"><span class="bf" style="width:%.1f%%"></span></span><span class="bv">%d</span></div>' % (a, b, a, 100 * b / top, b) for a, b in uses)
    return '<figure class="bars"><div class="brs">%s</div><figcaption>How many times each shared sound is called from the power code, drawn to scale. A few moments have their own sets (Beam, the Shockwave stomp, Charged Shot). Almost everything else draws from these fourteen.</figcaption></figure>' % rows


def flow_svg():
    bw, bh, gap, x0 = 150, 66, 22, 30
    xs = [x0 + i * (bw + gap) for i in range(5)]

    def box(x, y, t, s, new=False, warn=False):
        col = "var(--keep)" if new else "currentColor"
        fill = "var(--panel2)"
        out = '<rect x="%d" y="%d" width="%d" height="%d" rx="5" fill="%s" stroke="%s" stroke-width="%s" stroke-opacity="%s"/>' % (x, y, bw, bh, fill, col, "2" if new else "1", "1" if new else ".45")
        out += '<text x="%d" y="%d" text-anchor="middle" font-size="14" font-weight="700" fill="currentColor">%s</text>' % (x + bw / 2, y + 27, t)
        out += '<text x="%d" y="%d" text-anchor="middle" font-size="11.5" fill="%s">%s</text>' % (x + bw / 2, y + 47, "var(--bad)" if warn else "var(--ink2)", s)
        return out

    def arr(x1, y1, x2, y2):
        return '<line x1="%d" y1="%d" x2="%d" y2="%d" stroke="currentColor" stroke-opacity=".6" stroke-width="1.5" marker-end="url(#ah)"/>' % (x1, y1, x2, y2)

    def lab(x, y, t, a="start"):
        return '<text x="%d" y="%d" font-size="11" text-anchor="%s" fill="var(--ink2)">%s</text>' % (x, y, a, t)

    o = ['<figure class="flow"><div class="scroll" tabindex="0"><svg viewBox="0 0 900 470" role="img" aria-label="Today a press runs straight to damage and mastery. After the plan the server checks whether the power can act before you pay, judges Clean on release, and a hit feeds stance, States, Duets and the R gauge.">']
    o.append('<defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="currentColor" fill-opacity=".7"/></marker></defs>')
    o.append('<text x="30" y="22" font-size="12" font-weight="700" letter-spacing="2" fill="var(--ink2)">TODAY</text>')
    y = 34
    o += [box(xs[0], y, "Press", "any of six keys"), box(xs[1], y, "Energy, cooldown", "checked first"), box(xs[2], y, "Power runs", "or refuses: red flash", warn=True), box(xs[3], y, "Hit", "damage only"), box(xs[4], y, "Mastery", "and stop")]
    for i in range(4):
        o.append(arr(xs[i] + bw, y + bh / 2, xs[i + 1], y + bh / 2))
    o.append('<text x="30" y="150" font-size="12" font-weight="700" letter-spacing="2" fill="var(--keep)">AFTER: GOLD BOXES ARE NEW</text>')
    y = 164
    o += [box(xs[0], y, "Press", "same keys, same feel"), box(xs[1], y, "Can it act?", "picks full or small version", True), box(xs[2], y, "Energy, cooldown", "paid only if it can"), box(xs[3], y, "Wind-up", "as today"), box(xs[4], y, "Release", "server judges Clean", True)]
    for i in range(4):
        o.append(arr(xs[i] + bw, y + bh / 2, xs[i + 1], y + bh / 2))
    o.append(lab(xs[1] + bw / 2, y + bh + 16, "no energy or no room: says why, costs nothing", "middle"))
    y2 = 306
    o += [box(xs[4], y2, "Hit", "damage plus stance", True), box(xs[3], y2, "State", "shows on the target", True), box(xs[2], y2, "Duet check", "did two powers meet?", True), box(xs[1], y2, "R gauge", "fills from fighting well", True), box(xs[0], y2, "Mastery", "plus counters")]
    o.append(arr(xs[4] + bw / 2, y + bh, xs[4] + bw / 2, y2))
    o.append(lab(xs[4] + bw / 2 + 8, y + bh + 36, "aimed at release"))
    for i in range(4, 0, -1):
        o.append(arr(xs[i], y2 + bh / 2, xs[i - 1] + bw, y2 + bh / 2))
    o.append(arr(xs[0] + bw / 2, y2 + bh, xs[0] + bw / 2, y2 + bh + 34))
    o.append('<text x="%d" y="%d" font-size="12" fill="var(--ink2)">Saved in Loadout schema 4: your Way, counters, Duets found. Shown on Tasks and in the Journal.</text>' % (xs[0], y2 + bh + 52))
    o.append('<text x="%d" y="%d" font-size="11" fill="var(--ink2)" text-anchor="end">States, Clean chains, ground pieces and sounds are never saved.</text>' % (xs[4] + bw, y2 + bh + 52))
    o.append('</svg></div><figcaption>The cast path today and after. Every gold box is added around the existing path, and with its setting off the path behaves as it does now.</figcaption></figure>')
    return "".join(o)


# ---- demonstrations ---------------------------------------------------------------------------------------------------------
def storyboards():
    out = []
    for b in scenes.boards():
        out.append('<div class="board" id="%s" style="--fc:%s"><div class="bhead"><h4>%s</h4><span>%s</span></div><div class="panels">' % (b["id"], b["color"], e(b["title"]), e(b["kind"])))
        for i, (pn, cap) in enumerate(zip(b["panels"], b["caps"])):
            out.append('<figure>%s<figcaption><b>%d</b> %s</figcaption></figure>' % (pn.svg(html.escape(cap)), i + 1, e(cap)))
        out.append("</div></div>")
    return "".join(out)


def taster():
    with open(os.path.join(TASTER, "levels.json")) as f:
        lv = json.load(f)
    stats = {
        "haymaker": dict(today=(1.4, 27, 4.9), new=(1.5, 72, 5.0), clean=(2.0, 85, 2.5)),
        "railgun": dict(today=(1.8, 7, 0.9), new=(1.8, 36, 12.7), clean=(1.8, 29, 9.7)),
        "gravity_well": dict(today=(0.5, 63, 0.0), new=(4.8, 79, 0.9), clean=(4.8, 82, 0.8)),
        "shadow_step": dict(today=(0.6, 27, 0.3), new=(0.9, 40, 3.6), clean=(0.8, 19, 2.2)),
    }
    notes = {
        "haymaker": "Today shares its rising charge with Railgun.",
        "railgun": "Today's has almost no low end at all, and its charge is the Haymaker's.",
        "gravity_well": "Today makes sound for half a second of a four second field. The new one keeps going for as long as the field is there.",
        "shadow_step": "The new one takes a breath, cuts it off, leaves two frames of silence, then the slice.",
    }
    labels = (("today", "Today"), ("new", "New idea"), ("clean", "New, cast Clean"))
    out = []
    for pid, d in lv.items():
        p = BY[pid]
        longest = max(d[k]["seconds"] for k, _ in labels)
        out.append('<div class="tast" style="--fc:var(%s)"><div class="thead">%s<h4>%s</h4><p>%s</p></div><div class="clips">' % (FAM[p["family"]][0], ic(pid), e(p["name"]), e(notes[pid])))
        for k, lab in labels:
            c = d[k]
            top = max(max(d[x]["env"]) for x, _ in labels) or 1
            n = len(c["env"])
            up = ["%.1f,%.1f" % (i * 110.0 / (n - 1), 22 - 20 * (v / top) ** .6) for i, v in enumerate(c["env"])]
            dn = ["%.1f,%.1f" % (i * 110.0 / (n - 1), 22 + 20 * (v / top) ** .6) for i, v in reversed(list(enumerate(c["env"])))]
            a, lo, hi = stats[pid][k]
            f = "%s_%s.mp3" % (pid, k)
            out.append(
                '<div class="clip" data-src="data:audio/mpeg;base64,%s" data-file="taster/%s">'
                '<button class="play" id="play-%s-%s" aria-pressed="false" aria-label="Play %s, %s">&#9654;</button>'
                '<div class="cbody"><div class="clab">%s <span>%.1f s</span></div>'
                '<div class="wave" style="width:%.1f%%"><svg viewBox="0 0 110 44" preserveAspectRatio="none" aria-hidden="true"><polygon points="%s"/></svg><i class="ph"></i></div>'
                '<div class="cstat">sounding for %.1f s &middot; %d%% low &middot; %.1f%% high</div></div></div>' % (
                    b64(os.path.join(TASTER, f)), f, pid, k, e(p["name"]), lab, lab, c["seconds"], 100 * c["seconds"] / longest, " ".join(up + dn), a, lo, hi))
        qid = "taster_" + pid
        out.append('</div><fieldset class="ask" id="%s" data-doc="%s"><legend>Which is closer to right?</legend>' % (qid, qid))
        for v, lab in (("new", "The new idea"), ("today", "Today's"), ("neither", "Neither")):
            out.append('<label><input type="radio" name="%s" id="%s-%s" value="%s"> %s</label>' % (qid, qid, v, v, lab))
        out.append("</fieldset></div>")
    return "".join(out)


def cards():
    fams = list(FAM)
    out = []
    for f in fams:
        var, instr, src = FAM[f]
        ps = [p for p in P if p["family"] == f]
        out.append('<section class="fam" id="fam-%s" style="--fc:var(%s)"><div class="famhead"><h4>%s</h4><span class="c">%d powers</span><p><b>Sounds like:</b> %s. <b>Where the readings come from:</b> %s</p></div><div class="cards">' % (f.lower(), var, f, len(ps), e(instr).lower(), e(src)))
        for p in ps:
            pid = p["id"]
            slot = e(p["slot"]) + (" &middot; Starter" if pid in STARTERS else "")
            out.append(
                '<article class="card" id="p-%s" data-fam="%s" data-v="%s">'
                '<header>%s<div class="nm"><h5>%s</h5><span class="slot">%s</span></div><span class="v v-%s">%s</span></header>'
                '<div class="ba"><div><b>Today</b><p>%s</p></div><div class="aft"><b>After</b><p>%s</p></div></div>'
                '<dl><dt>Right way</dt><dd>%s</dd><dt>On a boss</dt><dd>%s</dd><dt>Sound</dt><dd>%s</dd></dl>'
                '<blockquote class="sys">[%s]</blockquote>'
                '<div class="flag"><label><input type="checkbox" id="flag-%s" data-flag="%s"> Not this one</label><input type="text" class="note" id="flagnote-%s" data-flagnote="%s" placeholder="What would you change?" hidden></div>'
                '</article>' % (pid, f, p["verdict"], ic(pid, False), e(p["name"]), slot, p["verdict"], VERDICT[p["verdict"]],
                                e(p["now"]), e(p["after"]), e(p["right"]), e(p["boss"]), e(p["sound"]), e(p["first"]), pid, pid, pid, pid))
        out.append("</div></section>")
    return "".join(out)


def duets():
    out = ['<div class="duets">']
    for a, b, name, what in DUETS:
        out.append('<div class="duet"><div class="dp">%s <span>+</span> %s</div><h5>%s</h5><p>%s</p></div>' % (e(a), e(b), e(name), e(what)))
    out.append("</div>")
    return "".join(out)


def recs():
    out = []
    for i, (key, text) in enumerate(RECS, 1):
        qid = "rec_" + key
        out.append(
            '<fieldset class="rec" id="%s" data-doc="%s"><div class="rn">%d</div><div class="rb"><legend>%s</legend><p class="ifno"><b>If no:</b> %s</p>'
            '<div class="yn"><label><input type="radio" name="%s" id="%s-yes" value="yes"> Yes</label><label><input type="radio" name="%s" id="%s-no" value="no"> No</label>'
            '<input type="text" class="note" id="%s-note" data-note="%s" placeholder="Anything to add? (optional)"></div></div></fieldset>' % (qid, qid, i, e(text), e(IF_NO[key]), qid, qid, qid, qid, qid, qid))
    return "".join(out)


# ---- page copy ----------------------------------------------------------------------------------------------------------------
HEAD = """<title>Powers in Full Voice</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Chakra+Petch:wght@500;600;700&family=Atkinson+Hyperlegible:ital,wght@0,400;0,700;1,400&family=IBM+Plex+Mono:wght@400;500&display=swap">
"""

CSS = """<style>
/* One deliberate dark look: a night sky with the nine real family colours from the game's code as the only accents. */
:root {
  color-scheme: dark;
  --bg:#0f1019; --bg2:#141625; --panel:#1a1c2c; --panel2:#22253a; --line:#30334c;
  --ink:#f0ebe1; --ink2:#c0bcca; --ink3:#9794a6;
  --keep:#F2C14E; --bad:#ff9b8f; --ok:#86E0A0;
  --kin:#E08F1F; --vel:#FF6A5E; --fli:#A7CDE3; --ene:#7FC3FF; --sha:#A98FFF; --min:#D88FCA; --arc:#D9BA80; --tec:#47C6B0; --spi:#86E0A0;
  --disp:"Chakra Petch","Arial Narrow",system-ui,sans-serif;
  --body:"Atkinson Hyperlegible",system-ui,-apple-system,"Segoe UI",sans-serif;
  --mono:"IBM Plex Mono",ui-monospace,Menlo,monospace;
}
*{box-sizing:border-box}
html{scroll-behavior:smooth;-webkit-text-size-adjust:100%}
@media (prefers-reduced-motion:reduce){html{scroll-behavior:auto}}
body{margin:0;background:var(--bg);color:var(--ink);font:17px/1.56 var(--body)}
a{color:var(--keep)}
:focus-visible{outline:2px solid var(--keep);outline-offset:2px}
h1,h2,h3,h4,h5{font-family:var(--disp);line-height:1.15;margin:0;font-weight:700}
p{margin:0 0 .85em}
.wrap{display:grid;grid-template-columns:200px minmax(0,1fr);gap:44px;max-width:1280px;margin:0 auto;padding-block:32px 96px;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px))}
nav.toc{position:sticky;top:20px;align-self:start;font-size:14px;max-height:calc(100vh - 40px);overflow:auto}
nav.toc b{font-family:var(--disp);font-size:13px;letter-spacing:.12em;color:var(--ink3);display:block;margin-bottom:8px}
nav.toc a{display:block;color:var(--ink2);text-decoration:none;padding:3px 0}
nav.toc a:hover{color:var(--ink)}
nav.toc a.sub{padding-left:14px;font-size:13px;color:var(--ink3)}
.tocm{display:none}
main{min-width:0}
.hero{padding:26px 0 8px}
.eyebrow{font:500 13px var(--mono);color:var(--ink3);letter-spacing:.04em}
h1{font-size:clamp(44px,8.2vw,88px);margin:12px 0 16px;letter-spacing:-.01em}
h1 em{font-style:normal;color:var(--keep)}
.lede{font-size:21px;line-height:1.45;max-width:36em}
.honest{border-left:3px solid var(--keep);padding:2px 0 2px 14px;color:var(--ink2);max-width:40em;margin-top:18px}
.tiles{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px;margin:28px 0}
.tile{background:var(--panel);border:1px solid var(--line);padding:12px 12px 10px;border-radius:6px}
.tile b{font:700 38px/1 var(--disp);display:block}
.tile span{font-size:14px;color:var(--ink2)}
.jump{display:flex;gap:10px;flex-wrap:wrap;margin-top:6px}
.btn{display:inline-block;background:var(--keep);color:#1a1405;font:700 15px var(--disp);padding:10px 16px;border-radius:4px;text-decoration:none;border:0;cursor:pointer}
.btn.alt{background:transparent;color:var(--ink);border:1px solid var(--line)}
section.top{margin-top:84px}
h2{font-size:34px;margin-bottom:20px;display:flex;gap:14px;align-items:baseline;scroll-margin-top:16px}
h2 .n{color:var(--keep);font-size:22px}
h3{font-size:22px;margin:40px 0 12px;scroll-margin-top:16px}
h4{font-size:19px}
h5{font-size:17px}
.prose{max-width:44em}
.tag{font:500 11px var(--mono);letter-spacing:.05em;border:1px solid var(--line);border-radius:3px;padding:1px 5px;color:var(--ink2);white-space:nowrap}
.tag.obs{color:var(--ok);border-color:#3f6a52}.tag.res{color:var(--ene);border-color:#3b5a7a}.tag.hyp{color:#f2b36e;border-color:#7a5a30}.tag.pro{color:var(--keep);border-color:#7a6128}
.scroll{overflow-x:auto;margin:14px 0}
table{border-collapse:collapse;font-size:15px;width:100%;min-width:640px}
th{font:500 12px var(--mono);letter-spacing:.04em;color:var(--ink3);text-align:left;padding:8px 10px;border-bottom:1px solid var(--line);vertical-align:bottom}
td{padding:9px 10px;border-bottom:1px solid #24263a;vertical-align:top}
td:first-child{font-weight:700;color:var(--ink)}
code{font:13.5px var(--mono);color:var(--ink2);overflow-wrap:anywhere}
ul.plain{margin:.3em 0 1em;padding-left:1.15em}
ul.plain li{margin-bottom:.35em}
.twoc{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:22px}
.ic{display:inline-block;width:32px;height:32px;background-size:100% 100%;image-rendering:pixelated;flex:none}
figure{margin:18px 0}
figcaption{font-size:14px;color:var(--ink2);margin-top:8px;max-width:50em}
.groups{display:flex;flex-direction:column;gap:14px}
.ghead{font-size:15px;color:var(--ink2);margin-bottom:6px}
.ghead b{font:700 24px var(--disp);color:var(--ink);margin-right:4px}
.icons{display:flex;flex-wrap:wrap;gap:3px}
.icons span{display:block;padding:1px;line-height:0;border:1px solid transparent}
.icons span.charged{border-color:var(--keep);background:#3a2f12}
.brs{display:grid;gap:4px;max-width:620px}
.br{display:grid;grid-template-columns:86px minmax(0,1fr) 28px;align-items:center;gap:8px;font-size:14.5px}
.bl{color:var(--ink2)}
.bt{display:block;height:12px}
.bf{display:block;height:12px;background:var(--ene);border-radius:0 3px 3px 0;min-width:4px}
.bv{font-family:var(--mono);font-size:13px;text-align:right}
.alts{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.alt{background:var(--panel);border:1px solid var(--line);border-radius:6px;padding:14px}
.alt.pick{border-color:var(--keep);box-shadow:inset 0 0 0 1px var(--keep)}
.alt h4{margin-bottom:6px}
.alt .pk{font:700 12px var(--mono);color:var(--keep);letter-spacing:.06em;display:block;margin-bottom:4px}
.alt .bh{font-size:14.5px;color:var(--ink2)}
.sys-block{background:var(--panel);border:1px solid var(--line);border-radius:6px;padding:16px;margin:12px 0}
.sys-block h4{display:flex;gap:10px;align-items:baseline;margin-bottom:10px}
.sys-block h4 span{color:var(--keep)}
.sg{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px 18px;font-size:15px}
.sg b{display:block;font:500 11.5px var(--mono);letter-spacing:.05em;color:var(--ink3);margin-bottom:2px}
.sg p{margin:0}
.board{margin:26px 0;--fc:#fff}
.bhead{display:flex;justify-content:space-between;align-items:baseline;gap:10px;border-bottom:2px solid var(--fc);padding-bottom:5px}
.bhead span{font:500 12px var(--mono);color:var(--ink3)}
.panels{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}
.panels figure{margin:8px 0 0}
.panels svg{display:block;width:100%;height:auto;border-radius:3px}
.panels figcaption{font-size:14px;margin-top:6px}
.panels figcaption b{font-family:var(--disp);color:var(--fc);margin-right:2px}
.mock{background:#0a0b13;border:1px solid var(--line);border-radius:8px;padding:18px;margin:14px 0}
.mockl{font:500 12px var(--mono);color:var(--ink3);letter-spacing:.04em;margin-bottom:10px}
.hud{display:flex;flex-direction:column;align-items:center;gap:14px}
.notes{display:flex;gap:7px;align-items:flex-end;height:40px}
.notes i{display:block;width:12px;background:var(--spi);border-radius:2px;opacity:.9}
.slots{display:flex;gap:6px;flex-wrap:wrap;justify-content:center}
.sl{position:relative;width:60px;height:60px;background:#191b2a;border:1px solid #3a3d58;border-radius:5px;display:grid;place-items:center}
.sl .ic{width:40px;height:40px}
.sl kbd{position:absolute;right:3px;bottom:1px;font:700 11px var(--disp);color:var(--ink2)}
.sl.cl{border-color:var(--spi);box-shadow:0 0 14px var(--spi)}
.sl.ult{margin-left:12px;border:0;background:conic-gradient(var(--keep) 72%,#2a2d44 0);padding:3px}
.sl.ult>div{width:100%;height:100%;background:#191b2a;border-radius:3px;display:grid;place-items:center}
.sl.mv{margin-left:12px}
.xh{position:relative;width:64px;height:64px}
.call{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:12px;font-size:14.5px;color:var(--ink2)}
.call b{color:var(--ink);display:block;font-family:var(--disp);font-size:15px}
.journal{display:grid;grid-template-columns:230px minmax(0,1fr);gap:18px}
.jlist{font-size:15px;border-right:1px solid var(--line);padding-right:12px}
.jlist h5{font-size:13px;letter-spacing:.08em;color:var(--ink3);margin:10px 0 4px}
.jlist div{padding:3px 6px;border-radius:3px;display:flex;justify-content:space-between;gap:6px}
.jlist div.on{background:var(--panel2);color:var(--keep)}
.jlist div.un{color:var(--ink3)}
.jlist small{font:12px var(--mono);color:var(--ink3)}
.lad{display:grid;gap:9px;margin-top:8px}
.lad div{display:grid;grid-template-columns:62px minmax(0,1fr);gap:8px;font:14.5px/1.5 var(--mono);color:#ffe2b8}
.lad div b{font:700 13px var(--disp);color:var(--ink3);padding-top:2px}
.lad div.last{color:var(--keep)}
.ways{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:10px}
.way{background:var(--panel);border:1px solid var(--line);border-radius:6px;padding:12px;font-size:15px}
.way h5{margin-bottom:4px}
.way .ev{font:13px/1.45 var(--mono);color:var(--ene);margin-top:8px}
.tast{background:var(--panel);border:1px solid var(--line);border-left:3px solid var(--fc);border-radius:6px;padding:14px;margin:12px 0}
.thead{display:grid;grid-template-columns:auto auto minmax(0,1fr);gap:4px 10px;align-items:center;margin-bottom:10px}
.thead p{margin:0;font-size:15px;color:var(--ink2)}
.clips{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.clip{display:grid;grid-template-columns:40px minmax(0,1fr);gap:8px;--p:0}
.play{width:40px;height:40px;border-radius:50%;border:1px solid var(--fc);background:transparent;color:var(--fc);cursor:pointer;font-size:14px}
.clip.on .play{background:var(--fc);color:#10121c}
.clab{font:700 14.5px var(--disp)}
.clab span{font:12px var(--mono);color:var(--ink3);margin-left:4px}
.wave{position:relative;height:44px;margin:3px 0}
.wave svg{display:block;width:100%;height:44px}
.wave polygon{fill:var(--fc);opacity:.8}
.ph{position:absolute;top:0;bottom:0;left:calc(var(--p)*100%);width:2px;background:#fff;opacity:0}
.clip.on .ph{opacity:1}
.cstat{font:12px var(--mono);color:var(--ink3)}
.err{color:var(--bad);font-size:14px}
.ask{border:0;padding:0;margin:12px 0 0;display:flex;gap:6px 16px;flex-wrap:wrap;align-items:center;font-size:15px}
.ask legend{float:left;margin-right:10px;padding:0;color:var(--ink2)}
.filters{position:sticky;top:0;z-index:5;background:var(--bg);padding:10px 0;border-bottom:1px solid var(--line);display:grid;gap:6px}
.frow{display:flex;gap:5px;flex-wrap:wrap;align-items:center}
.chip{background:transparent;color:var(--ink2);border:1px solid var(--line);border-radius:14px;padding:3px 11px;font:500 14px var(--disp);cursor:pointer}
.chip[aria-pressed=true]{background:var(--ink);color:#12131e;border-color:var(--ink)}
#filter-count{font:13px var(--mono);color:var(--ink3);margin-left:auto}
.fam{margin:26px 0;--fc:#fff}
.famhead{border-left:4px solid var(--fc);padding-left:12px;margin-bottom:10px;display:grid;grid-template-columns:auto 1fr;gap:0 10px;align-items:baseline}
.famhead h4{font-size:24px;color:var(--fc)}
.famhead .c{font:13px var(--mono);color:var(--ink3)}
.famhead p{grid-column:1/-1;margin:2px 0 0;font-size:15px;color:var(--ink2)}
.famhead b{color:var(--ink);font-weight:700}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,430px),1fr));gap:10px}
.card{background:var(--panel);border:1px solid var(--line);border-top:2px solid var(--fc);border-radius:5px;padding:12px;font-size:15px;display:flex;flex-direction:column;gap:8px}
.card[hidden],.fam[hidden]{display:none}
.card header{display:flex;align-items:center;gap:10px}
.nm{flex:1;min-width:0}
.slot{font:12px var(--mono);color:var(--ink3)}
.v{font:700 11.5px var(--disp);letter-spacing:.03em;padding:3px 7px;border-radius:3px;white-space:nowrap}
.v-KEEP{background:var(--keep);color:#1a1405}.v-GOOD{border:1px solid var(--spi);color:var(--spi)}.v-FLAT{border:1px solid #6d6a7c;color:var(--ink2)}.v-RETHINK{border:1px solid var(--bad);color:var(--bad)}.v-FOLD{border:1px solid var(--arc);color:var(--arc)}
.ba{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.25fr);gap:12px}
.ba b{font:500 11.5px var(--mono);letter-spacing:.05em;color:var(--ink3)}
.ba .aft b{color:var(--fc)}
.ba p{margin:2px 0 0}
.ba>div:first-child p{color:var(--ink2)}
dl{display:grid;grid-template-columns:72px minmax(0,1fr);gap:4px 8px;margin:0}
dt{font:500 11.5px/1.9 var(--mono);color:var(--ink3)}
dd{margin:0}
.sys{font:13.5px/1.5 var(--mono);margin:2px 0 0;padding:7px 9px;background:#10121d;border-left:2px solid var(--fc);color:#efe3cc}
.flag{display:flex;gap:8px;align-items:center;font-size:13.5px;color:var(--ink3);margin-top:auto;flex-wrap:wrap}
.note{background:#10121d;border:1px solid var(--line);color:var(--ink);border-radius:3px;padding:6px 8px;font:15px var(--body);flex:1;min-width:150px}
.duets{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,270px),1fr));gap:8px}
.duet{background:var(--panel);border:1px solid var(--line);border-radius:5px;padding:10px 12px;font-size:15px}
.dp{font:12.5px var(--mono);color:var(--ink3)}
.dp span{color:var(--keep)}
.duet h5{margin:3px 0 3px;color:var(--keep)}
.duet p{margin:0}
.states{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,220px),1fr));gap:8px;margin:10px 0}
.states div{background:var(--panel);border:1px solid var(--line);border-radius:5px;padding:9px 11px;font-size:14.5px}
.states b{font-family:var(--disp);font-size:16px}
.states small{display:block;font:12px var(--mono);color:var(--ink3);margin-bottom:3px}
.newp{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,300px),1fr));gap:8px}
.newp div{background:var(--panel);border:1px solid var(--line);border-radius:5px;padding:11px 13px;font-size:15px}
.newp small{font:12px var(--mono);color:var(--keep);display:block;margin-bottom:4px}
.newp p:last-child{margin:0;color:var(--ink2);font-size:14px}
.rec{display:grid;grid-template-columns:42px minmax(0,1fr);gap:10px;border:0;border-bottom:1px solid var(--line);padding:14px 0;margin:0}
.rn{font:700 28px/1 var(--disp);color:var(--keep)}
.rec legend{padding:0;font-size:18px;font-weight:700;float:left;width:100%;margin-bottom:4px}
.ifno{clear:both;font-size:14.5px;color:var(--ink2);margin:0 0 8px}
.ifno b{color:var(--ink2)}
.yn{display:flex;gap:6px 18px;align-items:center;flex-wrap:wrap}
.yn label,.ask label{display:inline-flex;gap:6px;align-items:center;cursor:pointer}
input[type=radio],input[type=checkbox]{accent-color:var(--keep);width:17px;height:17px;margin:0}
.savebar{position:sticky;bottom:0;background:var(--bg2);border-top:1px solid var(--line);padding:10px 0;display:flex;gap:12px;align-items:center;flex-wrap:wrap;z-index:6}
#save-state{font:13.5px var(--mono);color:var(--ink2)}
#answered{font:700 16px var(--disp)}
@media (max-width:1020px){
  .wrap{grid-template-columns:minmax(0,1fr);gap:0}
  nav.toc{display:none}
  .tocm{display:block;background:var(--panel);border:1px solid var(--line);border-radius:5px;padding:9px 12px;margin:14px 0;font-size:15px}
  .tocm a{display:block;padding:4px 0;color:var(--ink2);text-decoration:none}
}
@media (max-width:760px){
  body{font-size:16.5px}
  .tiles{grid-template-columns:repeat(2,minmax(0,1fr))}
  .twoc,.alts,.sg,.clips,.call,.ways,.journal{grid-template-columns:minmax(0,1fr)}
  .jlist{border-right:0;border-bottom:1px solid var(--line);padding:0 0 10px}
  .panels{grid-template-columns:repeat(2,minmax(0,1fr))}
  .ba{grid-template-columns:minmax(0,1fr)}
  h2{font-size:28px}
  .filters{position:static}
  .sl{width:48px;height:48px}.sl .ic{width:32px;height:32px}
  .sl.ult,.sl.mv{margin-left:0}
}
</style>
"""

JS = r"""<script>
(function(){
  var $=function(s,r){return (r||document).querySelector(s)},$$=function(s,r){return Array.prototype.slice.call((r||document).querySelectorAll(s))};
  /* filters over the 63 cards */
  var fam='all',ver='all';
  function applyFilter(){
    var n=0;
    $$('.card').forEach(function(c){var ok=(fam==='all'||c.dataset.fam===fam)&&(ver==='all'||c.dataset.v===ver);c.hidden=!ok;if(ok)n++;});
    $$('.fam').forEach(function(s){s.hidden=!$('.card:not([hidden])',s);});
    $('#filter-count').textContent=n+' of 63 showing';
  }
  function chipRow(sel,set){$$(sel).forEach(function(b){b.addEventListener('click',function(){$$(sel).forEach(function(x){x.setAttribute('aria-pressed','false');});b.setAttribute('aria-pressed','true');set(b.dataset.val);applyFilter();});});}
  chipRow('.chip-fam',function(v){fam=v;});chipRow('.chip-ver',function(v){ver=v;});
  /* one player for every clip; sound starts only from a click */
  var audio=new Audio(),current=null,raf=0;audio.preload='none';
  function stop(){if(current){current.classList.remove('on');current.style.setProperty('--p',0);$('.play',current).setAttribute('aria-pressed','false');$('.play',current).innerHTML='&#9654;';}current=null;cancelAnimationFrame(raf);}
  function tick(){if(current&&audio.duration){current.style.setProperty('--p',audio.currentTime/audio.duration);raf=requestAnimationFrame(tick);}}
  function fail(){var c=current;if(!c)return;if(!c.dataset.tried){c.dataset.tried='1';audio.src=c.dataset.file;audio.play().then(tick).catch(fail);return;}stop();var m=$('.err',c);if(!m){m=document.createElement('div');m.className='err';m.textContent='This clip would not play here.';$('.cbody',c).appendChild(m);}}
  audio.addEventListener('ended',stop);audio.addEventListener('error',function(){if(current)fail();});
  $$('.clip').forEach(function(c){var b=$('.play',c);b.addEventListener('click',function(){
    if(current===c){audio.pause();stop();return;}
    audio.pause();stop();current=c;c.classList.add('on');b.setAttribute('aria-pressed','true');b.innerHTML='&#10073;&#10073;';delete c.dataset.tried;
    audio.src=c.dataset.src;audio.play().then(tick).catch(function(e){if(e&&e.name==='AbortError')return;fail();});
  });});
  /* answers: one document per question in the answers collection */
  var docs={},queues={},db=null,canWrite=true,ready=false,offline=false,recIds=$$('.rec').map(function(f){return f.dataset.doc;});
  function say(t,bad){var s=$('#save-state');s.textContent=t;s.style.color=bad?'var(--bad)':'';}
  function refresh(){
    $$('fieldset[data-doc]').forEach(function(f){var d=docs[f.dataset.doc]||{};$$('input[type=radio]',f).forEach(function(r){r.checked=(d.answer===r.value);});});
    $$('[data-note]').forEach(function(i){if(document.activeElement!==i){var d=docs[i.dataset.note]||{};i.value=d.note||'';}});
    $$('[data-flag]').forEach(function(c){var d=docs['power_'+c.dataset.flag]||{};c.checked=!!d.flag;var n=$('[data-flagnote="'+c.dataset.flag+'"]');n.hidden=!c.checked;if(document.activeElement!==n)n.value=d.note||'';});
    var g=$('#general');if(g&&document.activeElement!==g)g.value=(docs.general||{}).note||'';
    var yes=0,no=0;recIds.forEach(function(id){var a=(docs[id]||{}).answer;if(a==='yes')yes++;if(a==='no')no++;});
    $('#answered').textContent=(yes+no)+' of '+recIds.length+' answered';
  }
  function put(id,patch){
    docs[id]=Object.assign({},docs[id]||{},patch);refresh();
    if(!db||!canWrite)return;
    queues[id]=(queues[id]||Promise.resolve()).then(function(){say('Saving…');return db.doc('answers/'+id).set(Object.assign({},docs[id],{at:Date.now()}));}).then(function(){say('Saved.');},function(e){say('That one did not save: '+((e&&e.message)||'unknown error'),true);});
  }
  var timers={};
  function later(id,fn){clearTimeout(timers[id]);timers[id]=setTimeout(fn,700);}
  $$('fieldset[data-doc]').forEach(function(f){$$('input[type=radio]',f).forEach(function(r){r.addEventListener('change',function(){if(r.checked)put(f.dataset.doc,{answer:r.value});});});});
  $$('[data-note]').forEach(function(i){i.addEventListener('input',function(){later(i.id,function(){put(i.dataset.note,{note:i.value});});});});
  $$('[data-flag]').forEach(function(c){c.addEventListener('change',function(){put('power_'+c.dataset.flag,{flag:c.checked});});});
  $$('[data-flagnote]').forEach(function(i){i.addEventListener('input',function(){later(i.id,function(){put('power_'+i.dataset.flagnote,{note:i.value});});});});
  var gen=$('#general');gen.addEventListener('input',function(){later('general',function(){put('general',{note:gen.value});});});
  $('#yes-all').addEventListener('click',function(){recIds.forEach(function(id){if(!(docs[id]||{}).answer)put(id,{answer:'yes'});});});
  function noSave(){offline=true;say('Answers can’t be saved in this view. Open the page in Claude to answer.',true);}
  (async function(){
    var t=setTimeout(function(){if(!ready)noSave();},10000);
    try{
      if(!window.claude||!window.claude.use){clearTimeout(t);noSave();return;}
      db=await window.claude.use('db');
      var user=await window.claude.use('user');
      if(!db){clearTimeout(t);noSave();return;}
      if(user){var c=await user.can('data.write');if(c===false){canWrite=false;say('You can read this plan, but only Euan can answer it.');}}
      db.collection('answers').onSnapshot(function(snap){
        ready=true;clearTimeout(t);var next={};snap.docs.forEach(function(d){next[d.id]=d.data();});docs=next;refresh();
        if(canWrite&&$('#save-state').textContent==='Loading your answers…')say(snap.empty?'Ready. Nothing answered yet.':'Your earlier answers are loaded.');
      },function(e){clearTimeout(t);say('Could not load answers: '+((e&&e.message)||'unknown error'),true);});
    }catch(e){clearTimeout(t);noSave();}
  })();
  applyFilter();refresh();
})();
</script>
"""


def icons_css():
    return "<style>" + "".join(".ic-%s{background-image:url(data:image/png;base64,%s)}" % (p["id"], b64(os.path.join(ICONS, p["id"] + ".png"))) for p in P) + "</style>\n"


TOC = [("short", "The short version", 0), ("s1", "1 What you asked for", 0), ("s2", "2 What's there today", 0), ("s3", "3 Three ways to do it", 0), ("s4", "4 How it would work", 0), ("systems", "The ten changes", 1), ("contract", "Rules it must keep", 1), ("failures", "What could go wrong", 1),
       ("s5", "5 See it", 0), ("boards", "Storyboards", 1), ("mocks", "On screen", 1), ("taster", "Listen", 1), ("walk", "Before and after", 1), ("every", "Every power", 1), ("duets", "The 24 Duets", 1), ("newp", "Five new powers", 1),
       ("s6", "6 Checks and order", 0), ("s7", "7 Your decisions", 0)]


def tag(kind):
    return {"obs": '<span class="tag obs">SEEN IN CODE</span>', "res": '<span class="tag res">OTHER GAMES</span>', "hyp": '<span class="tag hyp">MY GUESS</span>', "pro": '<span class="tag pro">PROPOSAL</span>'}[kind]


def sysblock(n, title, ev, ch, ben, keeps, cost, test):
    return ('<div class="sys-block" id="sys-%d"><h4><span>%d</span>%s</h4><div class="sg">'
            '<div><b>THE PROBLEM</b><p>%s</p></div><div><b>THE CHANGE</b><p>%s</p></div><div><b>WHAT YOU GET</b><p>%s</p></div>'
            '<div><b>WHAT IT KEEPS</b><p>%s</p></div><div><b>THE COST</b><p>%s</p></div><div><b>HOW WE TEST IT</b><p>%s</p></div></div></div>') % (n, n, title, ev, ch, ben, keeps, cost, test)


SYSTEMS = [
    ("Clean casts: every press has a right way",
     "51 of 63 powers are one press and a cooldown. Only three have a release you can time. There's nothing to get better at.",
     "Each power gets its own right way, written by hand: a moment, an aim, a position or a move into it. It's listed on every card below. Do it and the cast lands Clean: about 25% more damage, more stance, about 40% of the energy back, and more mastery. The cue is at your crosshair. The server judges it, with two ticks of grace.",
     "After a hundred casts you're visibly better with a power, and you can hear it.",
     "A cast that isn't Clean does exactly what today's does. Nothing is punished, and a new player can ignore all of it.",
     "63 timings to write and balance. The risk is that it feels like a quick-time event.",
     "You play four powers in Stage 0 and tell me. The simulator must show no more than about 12% average gain at a 50% Clean rate."),
    ("Powers count on bosses",
     "The powers code has zero calls to the stance system. Blades are the only thing that breaks a boss. The one link is an attuned weapon's touch, worth 6.",
     "Every power fills stance by weight through the existing <code>Strikes.posture</code>: Light 3, Medium 6, Heavy 12, Massive 20, Clean half as much again. The same braced and opening rules apply.",
     "Being a caster finally counts in the fights that matter.",
     "A blade stays the main way. A longsword swung non-stop is about 8 stance a second. A Clean Haymaker every time it's ready is about 3.",
     "One more stance source for the boss fights session to allow for. Ranged stance could be abused.",
     "<code>combat_sim</code>: breaks per minute no more than 20% above today, tuned on two bosses and checked on bosses it wasn't tuned on."),
    ("No dead buttons",
     "32 places in the catalogue refuse with a red flash: no target 13, blocked 13, and the rest. Dominate and Telekinesis refuse a boss outright, and Gravity Flip skips anything heavy. Worse, a refused cast is normally free, but 14 powers with a wind-up pay first and ignore a later refusal, and at least seven can refuse at that point (Spire, Collapse, Terrain Rip, Thunderclap, Legion, Storm Call, Drone Swarm).",
     "An aimed power with nothing to aim at does something smaller. Every power gets a boss version. Wind-up powers check before they charge you.",
     "A press always answers you.",
     "Real limits stay: energy, cooldown, no room. Those say why and cost nothing.",
     "About 30 small behaviours to write.",
     "One GameTest casts all 63 at a boss and at nothing. Zero unexplained refusals."),
    ("States and Duets",
     "Seven force reactions exist, mostly against enemy tags, drawn with vanilla particles. Powers barely know each other.",
     "Six States show on a creature's body: Lifted, Charged, Burning, Chilled, Held, Read. On top of those, 24 named Duets: two specific powers that make something neither can do alone. You find them by trying things, and they're counted N / ?? like Weaves.",
     "The stories you tell someone else. Your Well, then your Nova, and the room goes white.",
     "The Weave format, the 20% boss cap, and your freedom to ignore all of it.",
     "Stacking is the danger, so every bonus together is capped at about 1.6 times. 24 looks and sounds to write.",
     "One test per mechanism. The simulator checks the cap holds in the worst combination."),
    ("Movement powers attack, and casts join the chain",
     "Dash, Leap, Flight, Jet Burst, Snap Cable and others now sit next to the Ascent moves that overlap them. A cast is not a chain link, so the two halves of movement don't know each other.",
     "The eight movement powers become attacks that move you. A cast counts as a link in the Ascent's chain, and a long enough chain gives On the Move casts. Wings stay for travel and Flight is for fighting in the air.",
     "Moving and casting become one thing.",
     "The Ascent's tiers stay the Ascent's. Nothing on its ladder moves.",
     "Eight reworks. Chains will reach further, so air fights need a balance check.",
     "A test that a cast links through <code>AscentServer.link</code>, and you judge the feel."),
    ("The R key fills from how you fight",
     "Ultimates sit on a 60 second timer. There are seven, six only come from a Path, and four families have none, so R is empty for hours.",
     "R becomes a gauge. Clean casts, Duets, finishers and kills fill it, and it creeps up on its own so nobody is ever locked out. No energy cost. Your first ultimate is the reward for the D Proving, chosen from what you've actually done.",
     "The big moment comes when you've earned it, and it comes early enough to matter.",
     "Path signatures still come from Paths.",
     "Every ultimate needs rebalancing.",
     "Simulator: about 90 seconds of good fighting fills it, about three minutes of sloppy fighting still does."),
    ("The world answers",
     "Almost everything is drawn on top of the world. The tools for more already exist (<code>FxDebris</code> reads real blocks, <code>TerrainLedger</code> restores them, <code>TimeSlow</code> zones) and go mostly unused. Some powers still use vanilla particles, which breaks your look rule.",
     "The ground really comes up, in whatever you're standing on. Beam leaves a scorch. Frost freezes real water for a while. The Well pulls loose things and bends shots. Charge runs through water and metal. Flow slows every new sound through the game's own sound event. Every vanilla particle goes.",
     "It stops looking like an add-on.",
     "Your frame budget. Ground pieces stay inside the existing debris cap (160 full, 64 reduced, 0 minimal). Everything put back afterwards.",
     "The hardest look work in the plan. Bending light round a Well would be new Lens work and is only a guess until tried.",
     "Stage 0 proves the ground wave first, with <code>FxStats</code> on the Mac."),
    ("A sound pack for every power",
     "172 sound files, and 17 calls to one whoosh. Haymaker and Railgun share a charge. Gravity Well is silent for most of its life. You haven't heard most of them in game.",
     "Each power gets a charge or wind, a release, an impact, a tail, a Clean layer and a level 5 layer, two versions of each. Each family gets its own instruments. A sung Choir layer comes in at level 5. A mix budget keeps your own power and the biggest threat on top.",
     "You could close your eyes and name the power.",
     "Today's sounds. Old files stay in place until you've passed the new one.",
     "About 340 sound events and 700 short files, about 10 MB. I can't hear any of them, so your ears are the gate.",
     "A listening page per family: Today, New, New in the mix, Keep or Redo on each. Loudness measured to within 1.5 dB."),
    ("Readings: every power has a person",
     "The descriptions say things like a path's signature or a latent gift. The story already has the answer: the Choir reads people and plays back who they could be. Nothing connects that to your powers.",
     "A Power Core is a recording the Choir took of a real person. The Deep is full of people who had them, which is why Lords drop Cores. Each power has a first bearer, one line when you learn it and one more per level. The Journal keeps them in The Choir's Record. At level 5 of your starter you find a second name on the reading.",
     "Levelling up a power turns into finding out who they were, and then who you are.",
     "The ladder. No Echoes and no allies. The System's plain bracketed voice.",
     "About 300 short lines to write and read aloud.",
     "You read them all in one file before they ship."),
    ("Progress you earn by playing",
     "Cores come at random by rank, so there's nothing to go looking for, and powers have no tab on the Tasks page. The ladder itself is good, so it stays exactly as it is.",
     "Gate themes and Lords lean toward the families they've swallowed, with about a third staying random. At level 5 the System reads how you actually used the power and offers up to three ways to keep it, with the evidence. You pick one and can change it. New Powers tab through <code>Goals</code>.",
     "You can hunt a power, and level 5 is about you.",
     "All four levels, the 7,000 / 13,000 / 32,000 / 50,000 mastery, and every level already earned.",
     "One more choice at level 5, though it's optional and reversible.",
     "Ledger and Goals tests. Your Way only uses real counters, so a new player sees no made-up evidence."),
]

STORY_SAMPLE = [
    ("Learned", "[Dov Harlan threw one punch in his whole life that mattered. The Choir kept a copy of it. That's what you're holding.]"),
    ("Level 2", "[He was a dock worker. The man he hit was the foreman who'd been shorting everyone's pay.]"),
    ("Level 3", "[The foreman lived. Dov went to prison for it, and the docks paid everyone what they were owed the next week.]"),
    ("Level 4", "[He never threw another punch. When the Choir asked him why, he said he hadn't needed to.]"),
]


def page():
    c = []
    c.append(HEAD)
    c.append(CSS)
    c.append(icons_css())
    c.append('<div class="wrap"><nav class="toc" aria-label="Contents"><b>CONTENTS</b>')
    for sid, t, sub in TOC:
        c.append('<a href="#%s"%s>%s</a>' % (sid, ' class="sub"' if sub else "", t))
    c.append("</nav><main>")

    # hero and short version
    c.append('<header class="hero"><div class="eyebrow">AWAKEN &middot; for your approval &middot; 9 November 2026</div><h1>Powers in <em>Full Voice</em></h1>')
    c.append('<p class="lede">A plan to make using powers the best thing in the game. All 63 judged one at a time: what each does today, what it would do, how it would sound and who had it first.</p>')
    c.append('<p class="honest">Nothing here is built. I read the code and measured the sounds, but I haven\'t played a power or heard a clip. Every verdict is mine from reading, so overrule any of them. The tags say which claims come from the code, which from other games and which are my guesses.</p></header>')
    c.append('<details class="tocm"><summary>Contents</summary>' + "".join('<a href="#%s">%s</a>' % (s, t) for s, t, sb in TOC if not sb) + "</details>")
    c.append('<section id="short"><div class="tiles">')
    for k in ("KEEP", "GOOD", "FLAT", "RETHINK", "FOLD"):
        c.append('<div class="tile"><b>%d</b><span>%s</span></div>' % (COUNT[k], VERDICT[k]))
    c.append('</div><div class="prose"><p>You were right about the levels. The ladder stays exactly as it is. Three other things hold the powers back:</p>')
    c.append('<ul class="plain"><li><b>There\'s nothing to get good at.</b> 51 of the 63 are one press and a cooldown.</li><li><b>They don\'t know about the game built since.</b> A power has never filled a boss\'s stance, and casting isn\'t part of the Ascent\'s chain.</li><li><b>They sound alike and nobody is behind them.</b> 17 calls to one whoosh, and descriptions that name a path instead of a person.</li></ul>')
    c.append('<p>So the plan keeps the 63 and the ladder and adds ten connected changes: a right way to press every power, powers that count on bosses, combinations you find by experimenting, an R key that fills from how you fight, a ground that really moves, a sound pack per power, and a real person behind each one, ending with your own name on your starter.</p>')
    c.append('<p>It starts with a playable proof of four powers, and I stop there until you\'ve played it.</p></div><div class="jump"><a class="btn" href="#s7">Go to your 19 decisions</a><a class="btn alt" href="#taster">Listen to the taster</a><a class="btn alt" href="#every">See every power</a></div></section>')

    # 1
    c.append('<section class="top" id="s1">' + h2(1, "What you asked for", "s1h"))
    c.append('<div class="prose"><p>Here\'s how I read it. Tell me if I\'ve got this wrong, because everything below follows from it.</p></div>')
    c.append(table(["", "What I took from it"], [
        ["The job", "Make using powers extremely fun and satisfying, at every level, for someone who has played a lot."],
        ["What's wrong", "The first powers were built before most of the game existed and were never revisited. Some are boring. They could be more creative and more technical."],
        ["What you want", "\"Holy shit, this is extremely fun and satisfying to use.\" More progression, more lore."],
        ["What you suggested", "A sound pack per power, then the powers themselves. I checked whether sound was the main problem. It's one of three."],
        ["What must stay", "How they level up. Your saves. Your frame rate. Fair fights."],
        ["What I can't check", "How they feel. Only you can do that, so the plan stops early for you to play."],
    ]))
    c.append('<h3>What the bar means, in things you\'d notice</h3>')
    c.append(table(["Strong", "Weak", "Unacceptable"], [[
        "You tell someone about a thing you did with two powers. You could name Railgun with your eyes shut. After a hundred casts you're visibly better and you can hear it.",
        "Bigger numbers, more particles and new sounds laid over the same one press.",
        "A power turns into a chore or a quick-time event. Levels you earned vanish. A boss melts in seconds. The frame rate drops."]]))
    c.append('<h3>The success contract</h3>')
    c.append(table(["", "What has to be true"], [
        ["Fun", "Every power has a better way to press it, and you can tell when you found it."],
        ["Truth", "A power's text says what it does. Numbers come from the simulator, not from me."],
        ["Effort", "Nothing to learn before it works. No new menu to manage. A plain cast is today's cast."],
        ["Response", "A press answers on the same frame it does today. The Clean ring lands within one server tick, 50 ms."],
        ["Control", "You choose your bar and your Way. The notes can be turned off. Nothing changes your loadout for you."],
        ["When it can't", "A power with nothing to do does something smaller, or says why and costs nothing."],
        ["Cost", "About 10 MB of sound. No more frame time than the level 5 limit we already test against."],
    ]))
    c.append('<div class="twoc"><div><h3>Not part of this</h3><ul class="plain"><li>Replacing the six-key bar or the 63 powers</li><li>Permanent allies or followers</li><li>A new currency, daily lists or announcing bosses</li><li>Any new block or item</li><li>The general frame rate pass, which stays parked</li></ul></div>')
    c.append('<div><h3>Must not get worse</h3><ul class="plain"><li>The four named levels and the Awakened form</li><li>Every level, variant and mastery point already earned</li><li>The Ascent\'s movement ladder and Weaves</li><li>Boss fights: the 20% hit cap and fair damage</li><li>Cinematic frame time</li></ul></div></div></section>')

    # 2
    c.append('<section class="top" id="s2">' + h2(2, "What's there today", "s2h"))
    c.append('<div class="prose"><p>Everything in this section comes from the code at commit <code>1f9bab956</code>, unless it\'s tagged otherwise.</p></div>')
    c.append("<h3>How the 63 are pressed " + tag("obs") + "</h3>" + press_chart())
    c.append("<h3>What they sound like " + tag("obs") + "</h3>" + sound_chart())
    c.append("<h3>What's been built since, and whether powers know about it " + tag("obs") + "</h3>")
    c.append(table(["Built since", "What it does", "Do powers use it?"], [
        ["Stance (SPEC 47)", "Blows fill a boss's stance until it breaks", "<b>No.</b> Zero calls from the powers code. One exception: an attuned weapon's touch, worth 6."],
        ["The Ascent (17)", "Rope, boom, dive, wall-run, ledge moves chain together", "<b>No.</b> A cast isn't a link. <code>MoveTwists</code> refunds a little energy."],
        ["Fights That Read You (48)", "Bosses answer what you've started", "<b>Barely.</b> A cast is reported. Nothing knows which power it was."],
        ["Lock-on (40)", "Bends your look toward a target", "<b>Halfway.</b> The aim bends, but 13 places in the code can still refuse with no target."],
        ["Weaves (25)", "36 named weapon and power fusions", "<b>Yes.</b> The one real link, and the model for Duets."],
        ["Armour (44)", "Channeler sets and runes", "<b>Yes,</b> as stats."],
        ["Tasks page", "Six tabs of goals", "<b>No.</b> There's no Powers tab."],
        ["Bells and the Deep (43)", "The Deep can't stand bell sound", "<b>No.</b> No power uses this."],
    ]))
    c.append("<h3>The full map " + tag("obs") + "</h3>")
    c.append(table(["Part", "What it really does", "Evidence", "What that means", "Call"], [
        ["Levels (36)", "Four named steps, riders, Awakened form at 5", "<code>ladders.json</code>, 63 powers", "The part that works", "Keep"],
        ["Cast poses (32)", "Wind-ups, bodies, release frames", "54 profiles in <code>casts.json</code>", "Bodies already move well", "Keep, extend"],
        ["The press", "Fires once, starts a cooldown", "51 of 63 instant", "Nothing to get better at", "Extend"],
        ["R key", "60 s timer; 7 ultimates, 6 from Paths", "Catalogue", "Empty for hours", "Replace the timer"],
        ["Refusals", "32 fail sites, red flash", "<code>Casting.java</code>", "Dead buttons; 7 charge you anyway", "Replace"],
        ["Force reactions", "7 cases, vanilla particles", "<code>ForceInteractions</code>", "Powers barely combine", "Replace with States"],
        ["Movement powers", "8 powers that overlap the Ascent", "Dash next to air dash, and so on", "Two systems for one job", "Rework"],
        ["Architect", "Wall, Ramp, Floor and Cone take four slots", "Catalogue; five steps all named Blueprint", "You can't play anything else", "Fold"],
        ["Sound", "172 files, mostly shared", "Call counts above", "Powers blur together", "Replace per power"],
        ["Effects kit", "Own renderer, real-block debris, ledger", "<code>FxDebris</code>, <code>TerrainLedger</code>", "A strong kit, barely used on the world", "Extend"],
        ["Cores", "Random by rank", "<code>PowerCoreHooks.roll</code>", "Nothing to go looking for", "Extend"],
        ["Lore", "One line, often a path's name", "Descriptions; <code>STORY.md</code>", "Nobody behind them", "Extend"],
        ["Icons", "Frame by family, real art", "<code>powericons.py</code>", "Good. Six new ones needed", "Keep"],
    ]))
    c.append("<h3>Six cases that show it</h3>")
    c.append(table(["Kind", "Case", "What happens, step by step", "Where it goes wrong"], [
        ["Disappointing", "Telekinesis on a boss", "Press, aim, <code>too_heavy</code>, red flash", "The moment you most want it"],
        ["Disappointing", "Storm Call with nobody near", "Press, 45 energy and a 30 second cooldown spent, wind-up, <code>no_target</code> ignored", "You pay for nothing"],
        ["Strong", "Haymaker", "Hold, peak, release on the real frame", "Nothing. This is the model"],
        ["Ordinary", "Ground Slam at E rank", "Press, ring, cooldown", "Nothing to do better next time"],
        ["Unclear", "Flow", "Looks great, but only mobs slow down. The world's sound doesn't", "Good already, could be astonishing"],
        ["Missing data", "Echo Swap with no summon", "Press, <code>no_summon</code>, nothing", "Needs another power to function at all"],
    ]))
    c.append('<h3>Why it feels this way</h3>')
    c.append(table(["What you notice", "Likely cause", "For", "Against", "Smallest test", "If fixed"], [
        ["Some are boring", "No skill in the press", "51 of 63 are one press", "Haymaker is held and could still be dull if it looked bad", "Stage 0, four powers", "The biggest single gain " + tag("hyp")],
        ["Powers feel left behind", "No link to stance or chains", "Zero calls in the code", "Casters may be fine in ordinary fights", "Simulator and Stage 0", "Bosses become a place for powers"],
        ["They blur together", "Shared sounds", "Call counts", "You haven't really heard them yet, so it could be fine in play", "The taster below", "Each power has a voice"],
        ["They look like an add-on", "Effects drawn over the world", "Code; your own rule", "I haven't seen them in game", "Stage 0 ground wave", "The big impression"],
        ["Levelling is a menu", "Random Cores", "<code>roll()</code>", "You like how they level, so this may matter least", "Stage 7", "Something to hunt"],
    ]))
    c.append("<h3>What other games teach " + tag("res") + "</h3>")
    c.append('<div class="prose"><p>Only for questions that change the design. I read reporting and guides, not the original talks, so treat these as pointers.</p></div>')
    c.append(table(["Question", "What I found", "How far it applies", "What it changes here"], [
        ["Would a timing reward feel good or like homework?", 'Gears of War made each reload a small timing game with a prize. <a href="https://gmtk.substack.com/p/how-game-designers-solved-these-11">GMTK</a> notes beginners often ignored it. Testing for the 2026 game moved the bar to mid-screen because that\'s where people used it most (<a href="https://gamingbolt.com/?p=646835">GamingBolt</a>). Its designer later called the multiplayer damage bonus a mistake (<a href="https://www.destructoid.com/cliff-bleszinski-devs-should-feel-free-to-use-gears-of-wars-active-reload/">Destructoid headline</a>, the rest of the piece was cut off for me).', "One repeated action in a shooter. We have 63 different ones.", "The cue goes at the crosshair. A plain cast stays fully fine. The damage bonus doesn't apply against other players."],
        ["How many pairings are worth finding?", 'One guide counts 28 Duo Boons in Hades (see <a href="https://screenrant.com/hades-duo-boons-guide/">Screen Rant</a> and <a href="https://primagames.com/tips/hades-what-are-duo-boons-how-get-duo-boons">Prima</a>), and Hades II has 37 (<a href="https://rogueranker.com/hades-2-duo-boons/">Rogue Ranker</a>). Each needs something from both gods.', "They're rolled per run. You try ours on purpose.", "24 Duets, each needing only the two powers on your bar."],
        ["Rules or a thousand special cases?", 'Breath of the Wild runs a rule-based calculator of states between elements and materials (<a href="https://www.thumbsticks.com/gdc-17-breath-of-the-wild-science-lies">report on the GDC 2017 talk</a>).', "An open-world sandbox, and I only read coverage.", "Six States are general rules. Duets are the named special cases on top."],
        ["Can you tell things apart by ear in a busy fight?", 'Overwatch found "the loudest sound wins" and built a system that lifts the biggest threat in the mix, and gave every character its own footsteps (<a href="https://pcgamer.com/how-overwatch-uses-sound-to-pinpoint-threats-you-cant-see">PC Gamer on the GDC 2016 talk</a>).', "A competitive shooter. I found nothing on how each ability was handled.", "A mix budget, with your own power and the biggest threat on top."],
        ["Timer or meter for the big one?", 'Overwatch fills a meter from damage and healing, with slow passive charge and no timer (<a href="https://overwatch.fandom.com/wiki/Ultimate_ability">wiki</a>, <a href="https://www.dexerto.com/overwatch/overwatch-how-to-quickly-build-ultimate-charge-560754">Dexerto</a>).', "Team PvP. The numbers are community figures.", "R becomes a gauge, and a slow creep means nobody is locked out."],
    ]))
    c.append('<h3>What already exists to build on</h3><div class="prose"><p><code>Strikes.posture</code> for stance, the Weaves\' found-not-told pattern, <code>FxDebris</code> and <code>TerrainLedger</code> for the ground, <code>TimeSlow</code> for time, <code>PlaySoundEvent</code> for bending sounds in Flow, <code>audiokit.py</code> and the listening pages for sound, CC0 sources already in the repo so no new download is needed, <code>combat_sim.py</code> for balance, <code>FxStats</code> for frame cost, and <code>Goals</code> for the Tasks page.</p></div></section>')

    # 3
    c.append('<section class="top" id="s3">' + h2(3, "Three ways to do it", "s3h"))
    c.append('<div class="alts">')
    c.append('<div class="alt"><h4>A. Repair</h4><p>New sound pack, powers fill stance, end the dead buttons, remove vanilla leftovers.</p><p class="bh">About 12 hours. Low risk. Decent to good, but the powers are still one press. It fixes what\'s wrong without reaching what you asked for.</p></div>')
    c.append('<div class="alt pick"><span class="pk">RECOMMENDED</span><h4>B. Build underneath</h4><p>Keep the 63 and the ladder. Add the ten changes, then rework each power by hand on top of them.</p><p class="bh">About 56 hours. Each piece can be switched off. It\'s the only option that reaches the bar and keeps the part you like.</p></div>')
    c.append('<div class="alt"><h4>C. Start over</h4><p>Cut to about 30 deep powers with a new key model.</p><p class="bh">120 hours or more. Breaks the ladder and everyone\'s saves. Rejected, though its best idea survives: folding the four Architect shapes into one.</p></div></div>')
    c.append('<div class="prose"><p><b>What would change my mind:</b> if Stage 0 feels just as good without Clean casts, I\'d drop them and move toward A plus the world and sound work. If the ground wave can\'t hold your frame rate, I\'d cut it back to Swift-style dust and keep the rest.</p></div></section>')

    # 4
    c.append('<section class="top" id="s4">' + h2(4, "How it would work", "s4h"))
    c.append(flow_svg())
    c.append('<h3 id="systems">The ten changes ' + tag("pro") + "</h3>")
    for i, s in enumerate(SYSTEMS, 1):
        c.append(sysblock(i, *s))
    c.append('<h3>The six States</h3><div class="states">')
    for name, src, what in STATES:
        c.append("<div><b>%s</b><small>from %s</small>%s</div>" % (e(name), e(src), e(what)))
    c.append("</div>")
    c.append('<h3 id="contract">The rules it must keep</h3>')
    c.append(table(["Step", "What happens"], [
        ["Trigger", "A key press on one of the six slots, exactly as today."],
        ["Reads", "The power, its level and variant, your aim, what's under your aim, your movement, what's already on the target, your energy and cooldown."],
        ["Does", "Second press? Can it act, and which version? Pay. Wind up. Release and judge Clean. Hit, stance, State. Duet check. Gauge, mastery, counters."],
        ["You see", "The effect, the Clean ring, the State on the creature, and the first time you find a Duet, a toast."],
        ["Allowed", "Damage and stance inside the caps, temporary terrain through the ledger, short-lived summons."],
        ["Saved", "Loadout schema 4 only. Nothing mid-fight is saved."],
        ["When it can't", "No energy, on cooldown, no room: it says why and costs nothing."],
    ]))
    c.append('<div class="twoc"><div><h3>These are always true</h3><ul class="plain"><li>A cast that isn\'t Clean equals today\'s cast</li><li>The server decides damage and Clean, never the client</li><li>The 20% boss hit cap stands, and all bonuses together stop at about 1.6 times</li><li>Every terrain change puts itself back unless it\'s something you built</li><li>No permanent allies, only short-lived summons and decoys</li><li>No power moves or controls another player</li><li>With a change\'s setting off, the game behaves as it did without it</li></ul></div>')
    c.append('<div><h3>When two things clash</h3><ul class="plain"><li>Boss cap beats the 1.6 cap, which beats any single bonus</li><li>Two time effects: the stronger one wins, they never stack</li><li>A Duet needs both powers from you or your party</li><li>Clean is judged at release, so the wind-up can\'t be gamed by a late aim</li><li>The Ascent\'s ladder always wins over a power that overlaps it</li></ul></div></div>')
    c.append("<h3>Saves and data</h3>")
    c.append(table(["What", "Where", "Old saves"], [
        ["Owned powers, levels, variants", "<code>Loadout</code>, schema 3 becomes 4", "Load unchanged. Wall, Ramp, Floor and Cone become Blueprint, each shape keeping its own level and variant, and the freed slots open up."],
        ["Your Way, per power", "Loadout 4", "Empty. Offered the next time you qualify."],
        ["Clean and Duet counts, Duets found", "Loadout 4", "Start at zero, so Your Way waits for real evidence."],
        ["R gauge", "Loadout 4", "Starts empty. Ultimates' old cooldowns are ignored."],
        ["Readings you've seen", "Worked out from your level, not saved", "Earlier lines appear in the Journal straight away."],
        ["States, Clean chains, ground pieces", "Memory only", "Never saved. A relog clears them."],
        ["Terrain changes", "The existing <code>TerrainLedger</code>", "Put back on expiry, or on restart."],
        ["New items or blocks", "None", "The <code>journey_ledger</code> test stays green. New powers arrive through the existing Core rolls."],
    ]))
    c.append('<div class="prose"><p><b>AI:</b> not applicable. There\'s no AI in this feature.</p></div>')
    c.append('<h3 id="failures">What could go wrong</h3>')
    c.append(table(["Failure", "Guard", "What you\'d see", "Test"], [
        ["Clean feels like a quick-time event", "It's optional, a plain cast is fine, one tip only", "Nothing nagging you", "You, at Stage 0"],
        ["Bonuses stack into a boss-melter", "1.6 cap and the 20% hit cap", "Big numbers that stop", "Simulator's worst case"],
        ["Sniping stance from far away", "Ranged stance falls off and Light hits are small", "Bosses come to you", "Simulator with a ranged build"],
        ["Ground wave costs too much", "Existing debris cap, simpler on Swift, none at Minimal", "Fewer pieces", "<code>FxStats</code>, stage gate"],
        ["Ear fatigue, or sounds cutting each other off", "Mix budget, at most three layers a cast, no preloading", "A clean mix", "Listening pass in a busy fight"],
        ["Sound memory", "Buffers load the first time a sound plays, so only what you use", "No startup cost", "Checked in <code>SoundBufferLibrary</code>"],
        ["Blueprint migration loses a level", "Each shape keeps its own level", "All four shapes at their old level", "A test loads a schema 3 save"],
        ["Time stop traps or freezes a player", "Stopped Clock leaves players unaffected, and a hard time limit", "It ends on time", "Two-player test"],
        ["Titan stuck in a cave", "Refused under a low ceiling, shrinks if one closes in", "A \"no room\" message", "GameTest with a closing roof"],
        ["Lag makes Clean feel unfair", "Two ticks of grace, judged at the moment you pressed", "It counts when it looks like it should", "Simulated lag test"],
        ["Clash with the boss fights session", "Only calls <code>Strikes.posture</code>, numbers agreed first", "Nothing", "CI on both branches"],
        ["Too many systems at once", "All silent until you run into them", "No new tutorial", "You, in a fresh world"],
    ]))
    c.append('<div class="twoc"><div><h3>Multiplayer and boundaries</h3><ul class="plain"><li>The server is in charge, and looks and sound stay on the client</li><li>Duets work across a party</li><li>Dominate, Echo Swap and time stop never touch players</li><li>The existing limit on aim-bending against people stays</li><li>Clean\'s damage bonus doesn\'t apply against players</li><li>No new data leaves the game</li></ul></div>')
    c.append('<div><h3>Frame rate and cost</h3><ul class="plain"><li>Limit we already test against: level 5 costs at most 0.5 ms typical and 1 ms worst over level 1</li><li>Ground pieces stay inside the existing cap</li><li>About 10 MB of sound on top of the current 2.2 MB</li><li>A command prints each power\'s Clean rate, Duets found and stance dealt</li><li>Frame tests run on your Mac when it\'s quiet, under <code>slot.sh</code></li></ul></div></div></section>')

    # 5
    c.append('<section class="top" id="s5">' + h2(5, "See it", "s5h"))
    c.append('<h3 id="boards">The best moments, as storyboards</h3><div class="prose"><p>Drawn to show the idea. None of this is game footage.</p></div>')
    c.append(storyboards())
    c.append('<h3 id="mocks">On screen</h3><div class="prose"><p>Mockups. The icons are real, and the numbers are made up to show the format.</p></div>')
    c.append('<div class="mock"><div class="mockl">THE POWER BAR, MID-FIGHT</div><div class="hud"><div class="notes" aria-hidden="true"><i style="height:12px"></i><i style="height:19px"></i><i style="height:26px"></i><i style="height:36px"></i></div>')
    c.append('<svg class="xh" viewBox="0 0 64 64" aria-hidden="true"><circle cx="32" cy="32" r="23" fill="none" stroke="#3a3d58" stroke-width="2"/><path d="M32 9A23 23 0 1 1 9 32" fill="none" stroke="#86E0A0" stroke-width="3"/><path d="M32 26v12M26 32h12" stroke="#fff" stroke-width="2"/></svg>')
    c.append('<div class="slots">' + "".join('<div class="sl%s">%s<kbd>%s</kbd></div>' % (cl, ic(i, False), k) for i, k, cl in (("shockwave", "Z", ""), ("spirit_palm", "X", " cl"), ("gravity_well", "C", ""), ("beam", "V", ""))))
    c.append('<div class="sl ult"><div>%s</div><kbd>R</kbd></div><div class="sl mv">%s<kbd>Alt</kbd></div></div></div>' % (ic("overload", False), ic("dash", False)))
    c.append('<div class="call"><div><b>The ring at your crosshair</b>The window closes as a ring, right where you\'re already looking.</div><div><b>The Clean flash and rising notes</b>Spirit Palm just landed Clean. Four in a row, so the note has climbed four steps.</div><div><b>R fills around its icon</b>72% full, from fighting well. No timer.</div></div></div>')
    c.append('<div class="mock"><div class="mockl">THE JOURNAL: THE CHOIR\'S RECORD</div><div class="journal"><div class="jlist"><h5>KINETIC</h5><div class="on">Dov Harlan <small>Haymaker</small></div><div>Greta Moss <small>Shockwave</small></div><div class="un">?? <small>unread</small></div><h5>SHADOW</h5><div>Nell Aubry <small>Shadow Step</small></div><div class="un">?? <small>unread</small></div><h5>READINGS FOUND</h5><div>7 / ??</div></div>')
    c.append('<div><h4>Dov Harlan</h4><div class="lad">' + "".join("<div><b>%s</b><span>%s</span></div>" % (a, e(b)) for a, b in STORY_SAMPLE))
    c.append('<div class="last"><b>Level 5</b><span>[I looked at this reading again. There are two names on it. His, and under that, yours. You picked this one last time too.]</span></div></div></div></div></div>')
    c.append('<div class="prose"><p>That last line appears only on the power you chose as your starter. It agrees with what the story already says: the Signal is you, from an earlier loop.</p></div>')
    c.append('<div class="mock"><div class="mockl">LEVEL 5: YOUR WAY, FOR A HAYMAKER</div><p class="sys" style="--fc:var(--kin)">[I\'ve been watching how you use this. There are three ways it could go from here. You can change your mind later.]</p><div class="ways">')
    c.append('<div class="way"><h5>Skybreaker</h5>Thrown from the air, it drives them into the ground and leaves a crater.<div class="ev">You threw 38% of them from the air.</div></div><div class="way"><h5>Counterpunch</h5>Thrown into a blow that\'s landing, it hits twice as hard and stops the blow.<div class="ev">71% of yours landed Clean.</div></div><div class="way"><h5>Single Combat</h5>With only one enemy near you, the charge is a third faster.<div class="ev">64% were on one target.</div></div></div></div>')

    c.append('<h3 id="taster">Listen</h3><div class="prose"><p><b>I can\'t hear these.</b> I only measured them, so you\'re the judge. Today is rebuilt from the real files, at the moments the code plays them. New and Clean are my first ideas, made from synthesis and CC0 sources already in the repo. All of them are evened out to the same loudness, so you\'re comparing the sound and not the volume. Each wave is drawn to scale. Headphones if you can.</p></div>')
    c.append(taster())

    c.append('<h3 id="walk">Before and after, same situation</h3>')
    c.append(table(["Situation", "Today", "After", "Why it's better"], [
        ["Ordinary: E rank, Shockwave, a few mobs in a field", "Press, a cone of light, they fall over, wait", "The real ground ripples out. You time it so the far ones are on the crest and they go up in the air. Your next hit slams one down.", "Same keys, but something to do and something to see"],
        ["Disappointing: Telekinesis user fighting a boss", "\"Too heavy.\" You swap it out or stop pressing it", "You catch the boss's shot and send it back, or hold its limb for a beat. It takes stance.", "Your power works where it matters"],
        ["Difficult: a low cave, two players, four foes", "Terrain Rip has little to tear, and you each fight alone", "Your partner's Well pulls the pack together and your Nova makes a Collapsing Star. Titan is refused here, and tells you why.", "Combos reward working together, and limits are clear"],
        ["Missing data: an old save with Wall 3 and Floor 1", "Four separate slots", "One Blueprint slot, Wall still level 3, Floor still level 1, and three keys back. Your Way says it hasn't seen enough of you yet.", "Nothing lost, and no invented evidence"],
    ]))

    c.append('<h3 id="every">Every power, one at a time</h3><div class="prose"><p>Tick <b>Not this one</b> on anything you\'d change. The verdicts come from reading the code, so overrule me freely. The line in the box is what the System says when you learn the power.</p></div>')
    c.append('<div class="filters"><div class="frow"><button class="chip chip-fam" data-val="all" aria-pressed="true">All</button>' + "".join('<button class="chip chip-fam" data-val="%s" aria-pressed="false">%s</button>' % (f, f) for f in FAM) + '</div>')
    c.append('<div class="frow"><button class="chip chip-ver" data-val="all" aria-pressed="true">Any verdict</button>' + "".join('<button class="chip chip-ver" data-val="%s" aria-pressed="false">%s (%d)</button>' % (k, v, COUNT[k]) for k, v in VERDICT.items()) + '<span id="filter-count"></span></div></div>')
    c.append(cards())

    c.append('<h3 id="duets">The 24 Duets</h3><div class="prose"><p>You\'d never see this list in the game. You find them by trying things, and the Journal counts them as N / ??. Here they are so you can veto any.</p></div>')
    c.append(duets())
    c.append('<h3 id="newp">Five new powers, only if you want them</h3><div class="newp">')
    for name, slot, what, why in NEW:
        c.append("<div><small>%s</small><h5>%s</h5><p>%s</p><p>%s</p></div>" % (e(slot), e(name), e(what), e(why)))
    c.append("</div></section>")

    # 6
    c.append('<section class="top" id="s6">' + h2(6, "Checks and order", "s6h"))
    c.append("<h3>What we measure against</h3>")
    c.append(table(["What", "Today", "Must become"], [
        ["Dead presses on a boss", "32 refusal sites in the code", "Zero unexplained, in one GameTest"],
        ["Stance from powers", "0", "Breaks per minute no more than 20% above today"],
        ["Clean's overall effect", "None", "No more than about 12% at a 50% Clean rate"],
        ["Level 5 power", "About 1.5 times level 1", "Stays on that curve"],
        ["Powers with their own sound", "A few", "All, each passed by you on a listening page"],
        ["Loudness", "-10 to -21 LUFS across the four I measured", "Within 1.5 dB of each power's target"],
        ["Frame time", "Your 4K Mac: 40-55 fps on Cinematic", "Level 5 at most 0.5 ms typical and 1 ms worst over level 1, and level 1 no worse than today"],
        ["Old saves", "Schema 3", "Load through schema 4, tested"],
    ]))
    c.append('<div class="prose"><p>Tuning uses the Hive Regent and Gearwright, then the numbers are checked on bosses they weren\'t tuned on. The fun itself can\'t be measured, so that gate is you playing it. As usual, one test per mechanism, and CI runs the full suite.</p></div>')
    c.append("<h3>The order</h3>")
    c.append(table(["Stage", "What gets built", "Hours", "Stop and check", "If it fails"], [
        ["0 Proof", "Clean, stance and the ground wave on Haymaker, Shockwave, Gravity Well and Railgun, with full sound packs", "4", "<b>You play it. Nothing continues until you say so.</b>", "The branch is dropped and nothing was merged"],
        ["1 Foundations", "Clean, States, stance, R gauge, chain links, no dead buttons, save schema 4, Powers tab, Readings engine, HUD", "6", "Full suite green, old saves load", "Settings off, and schema 4 reads back to defaults"],
        ["2 Starters, Kinetic, Energy", "Rework and sound for each", "8", "Listening page, Keep or Redo", "A power's sound goes back with one line"],
        ["3 Velocity, Flight, Shadow", "Rework and sound for each", "7", "Listening page", "Same"],
        ["4 Mind, Architect, Tech", "Rework, sound, Blueprint", "9", "Listening page; migration test", "Same; Blueprint can stay off"],
        ["5 Spirit and latents", "Rework and sound for each", "6", "Listening page", "Same"],
        ["6 Duets and party", "All 24, across players", "6", "Two-player test", "Duets switch off"],
        ["7 Lore and earning", "Readings, Journal, Cores from places, Your Way", "5", "You read every line", "Text only, easy to change"],
        ["8 New powers", "Whichever of the five you choose", "5", "You pick", "Skip them"],
    ]))
    c.append('<div class="prose"><p><b>About 56 hours in total, one builder, no sub-agents, based on how fast the ladders and casts were really built. Could be 40 to 80.</b> What could move it most: Sound rounds that come back Redo, whether the ground wave holds up on your Mac, and how Clean tunes.</p><p><b>Depends on:</b> the boss fights session, so stance numbers are agreed with it first. <b>Stops the rollout:</b> Stage 0 not feeling better, any frame gate failed, or an old save that won\'t load.</p></div>')
    c.append('<h3>Where this plan could be wrong</h3>')
    c.append(table(["Question", "Honest answer"], [
        ["Is this your real frustration?", "I read \"boring\" as nothing to get good at. If what bothers you is mostly how they look and sound, Clean is the part to cut. Stage 0 will show it."],
        ["Does it just add effort?", "It adds things to learn: Clean, six States, Duets. All are silent until you run into them, and ignoring them leaves you with today's powers."],
        ["Could something simpler do most of it?", "Option A gets the sound and bosses. It doesn't touch the one press. Nothing simpler changes that."],
        ["What would prove me wrong?", "You play the proof and prefer today's Haymaker. I'd stop there."],
        ["Is 56 hours too big a bet?", "Each stage stands on its own, and you can stop after any of them."],
    ]))
    c.append("</section>")

    # 7
    c.append('<section class="top" id="s7">' + h2(7, "Your decisions", "s7h"))
    c.append('<div class="prose"><p>Answer yes or no. My recommendation is yes to all of them. You can also flag any power above, or answer the four taster questions.</p></div>')
    c.append(recs())
    c.append('<h3>Anything else?</h3><textarea class="note" id="general" rows="4" style="width:100%" placeholder="Anything I missed, or got wrong"></textarea>')
    c.append('<div class="savebar"><span id="answered">0 answered</span><button class="btn alt" id="yes-all" type="button">Yes to everything unanswered</button><span id="save-state">Loading your answers…</span></div></section>')
    c.append("</main></div>")
    c.append(JS)
    return "".join(c)


def plan_md():
    o = ["# Powers in Full Voice", "", "Source for the approval page `plan.html` (built by `build_page.py` from `powers_data.py` and `scenes.py`). Nothing here is built yet.", ""]
    o += ["## Verdicts", ""] + ["- %s: %d" % (VERDICT[k], COUNT[k]) for k in VERDICT] + [""]
    o += ["## The ten changes", ""]
    for i, s in enumerate(SYSTEMS, 1):
        o.append("%d. **%s**: %s" % (i, s[0], s[2].replace("<code>", "`").replace("</code>", "`")))
    o += ["", "## Every power", ""]
    for f in FAM:
        o += ["### " + f, ""]
        for p in P:
            if p["family"] != f:
                continue
            o += ["**%s** (%s, %s)" % (p["name"], p["slot"], VERDICT[p["verdict"]]), "", "- Today: " + p["now"], "- After: " + p["after"], "- Right way: " + p["right"], "- Boss: " + p["boss"], "- Sound: " + p["sound"], "- Reading: [" + p["first"] + "]", ""]
    o += ["## Duets", ""] + ["- %s + %s = **%s**: %s" % d for d in DUETS] + [""]
    o += ["## New powers (optional)", ""] + ["- **%s** (%s): %s" % (a, b, c) for a, b, c, _ in NEW] + [""]
    o += ["## Recommendations", ""] + ["%d. %s (If no: %s)" % (i, t, IF_NO[k]) for i, (k, t) in enumerate(RECS, 1)] + [""]
    return "\n".join(o)


if __name__ == "__main__":
    body = page()
    with open(os.path.join(HERE, "plan.html"), "w") as f:
        f.write(body)
    with open(os.path.join(HERE, "PLAN.md"), "w") as f:
        f.write(plan_md())
    if PREVIEW:
        with open(PREVIEW, "w") as f:
            f.write('<!doctype html><html><head><meta charset=utf8><meta name=viewport content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body>' + body + "</body></html>")
    print("plan.html", len(body) // 1024, "KB")
