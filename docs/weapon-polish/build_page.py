"""Builds the weapon polish approval page from page.src.html into build/weapon-polish/page/ (index.html and its
pictures), from what the draft harness has rendered:

    python3 tools/art/arms_polish.py strips build/weapon-polish/strips        the four-view pictures
    python3 tools/art/arms_polish.py measure > build/weapon-polish/measure.txt
    python3 tools/art/arms_polish.py directions build/weapon-polish/page
    python3 docs/weapon-polish/build_page.py [before-tour-dir after-tour-dir]  (run-autopilot/screenshots-ci/arms-polish-<run>)
"""
import html
import json
import os
import re
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
for p in (os.path.join(ROOT, "tools/art"), os.path.join(ROOT, "tools/art/specs")):
    sys.path.insert(0, p)
import arms_weapons as AW  # noqa: E402

WORK = os.path.join(ROOT, "build/weapon-polish")
OUT = os.path.join(WORK, "page")
TIER_NAME = {t: n for t, _, n, *_ in AW.TIERS}

# kind -> (verdict class, verdict, blade today, proposed, what changes: the gallery's line)
KINDS = [
    ("longsword", "fat", "Fat", "5 × 3, a 3 × 3 post", "5 × 2 at the middle, 0 at the edge",
     "The square post down the middle goes. A flat one texel wide, a bevel each side to a knife edge, and a point drawn over the last "
     "eight texels. The Veilwoven's flame wave becomes a smooth serpentine. The Beastbound cleaver is ground from its bronze back to one edge."),
    ("katana", "ok", "Decent", "3 × 2, a bent bar", "3 × 1.5, one bevel",
     "Today it runs straight and hooks back at the tip, in six jointed pieces. It becomes one even curve from collar to point: a real "
     "back, one bevel down to the edge with the temper line beside it, and a point that sweeps up to the back. Same length."),
    ("greatblade", "fat", "Fat", "9 to 11 × 3", "9 to 11 × 2.5, 0 at the edge",
     "Each keeps its own outline: the flint slab, the tusk, the gate's window, the flamberge, the fork, the crown. Each loses its plank "
     "edges. The flamberge's ripple becomes a real wave."),
    ("shieldblade", "fat", "Fattest", "6 × 4, plus a raised groove", "5 × 2, groove painted in",
     "The sword was the fattest blade in the game. It takes the Longsword's cut, 5 wide, with its lit groove painted into the flat. Its "
     "guard top and stone come down to the 5-deep rule. The shield is untouched."),
    ("daggers", "fat", "Chunky", "5 × 3; tusks 3 × 3", "5 × 2; fangs 3 × 2",
     "The tusk and the talon were chains of blocks. They become curved fangs ground to their inside edge. The straight ones take the "
     "Longsword's cut."),
    ("twinblades", "fat", "Fat", "5 × 3", "5 × 2",
     "Both blades ground and pointed. The tusk pair becomes one smooth S."),
    ("rapier", "ok", "Good, a little thick", "3 wide, a ridge 3 deep", "3 × 2, a diamond",
     "The cross-shaped needle becomes a diamond needle with a long point. The narwhal tusk stays as it is."),
    ("axe", "ok", "Good head, stepped bit", "bit 3 thick, in steps", "a wedge, 3 to 0",
     "The bit was a staircase. It becomes a wedge: thick at the eye, ground to its edge, its top and bottom straight lines out to the "
     "horns. Eyes, polls, spikes and hafts are untouched."),
    ("spear", "fat", "Stubby head", "7 × 3, stacked", "7 × 2, a real leaf",
     "The heads were stacked boxes. They become real leaves, forks and spires that come to a point."),
    ("glaive", "fat", "A bent bar", "5 × 3", "5 × 2, tapering",
     "The blade becomes one curved, tapering blade, its edge sweeping up to a point at the back."),
    ("scythe", "fat", "A bent bar", "5 × 3", "5 × 2, tapering",
     "The crescent was six bars. It becomes one tapering crescent with its edge along the inside."),
    ("chainblade", "ok", "Good, blocky head", "5 to 7 × 3", "5 to 7 × 2",
     "Only the head changes: ground and pointed like the others. The chain and its coil stay."),
    ("maul", "keep", "The bar", "", "untouched", ""),
    ("warstaff", "keep", "Good", "", "untouched", ""),
    ("precursor", "keep", "Good", "its blade is light", "untouched", ""),
    ("gauntlets", "keep", "Good", "it is armour", "untouched", ""),
]
SCREEN = ("longsword", "katana", "greatblade", "shieldblade", "daggers", "rapier")  # held edge ahead: the width on screen is the thickness you see
UNCHANGED = ("maul", "warstaff", "precursor", "gauntlets")


def measures():
    out, vanilla = {}, 0
    for line in open(os.path.join(WORK, "measure.txt")):
        m = re.match(r"vanilla diamond sword:\s+(\d+)", line)
        if m:
            vanilla = int(m.group(1))
        m = re.match(r"(\w+)\s+today\s+(\d+) px\s+proposed\s+(\d+) px.*triangles (\d+) -> (\d+)", line)
        if m:
            out[m.group(1)] = (int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5)))
    return out, vanilla


def grades(M):
    rows = []
    for kind, cls, verdict, today, prop, _ in KINDS:
        name = AW.STYLES[kind][0]
        if kind in SCREEN:
            ids = [r[0] for r in AW.CATALOG if r[8] == kind and r[0] not in AW.LORD_COLOUR and r[0] != "beastbound_rapier"]
            thin = [round((1 - M[i][1] / M[i][0]) * 100) for i in ids if i in M and M[i][0]]
            c = M.get("gatewrought_" + kind)
            lo, hi = min(thin), max(thin)
            rng = f"up to {hi}% thinner" if lo <= 1 else (f"{hi}% thinner" if lo == hi else f"{lo}% to {hi}% thinner")
            screen = f"{c[0]} → {c[1]} px<br><span class=\"muted\">{rng} across the six ranks</span>"
        elif kind in UNCHANGED:
            screen = "no change"
        else:
            screen = "<span class=\"muted\">about the same width: the edge and the point change</span>"
        rows.append(f"        <tr><td><b>{name}</b></td><td><span class=\"v {cls}\">{verdict}</span></td><td class=\"n\">{today}</td>"
                    f"<td class=\"n\">{prop}</td><td class=\"n\">{screen}</td></tr>")
    return "\n".join(rows)


def gallery():
    secs = []
    have = set(f[:-4] for f in os.listdir(os.path.join(OUT, "strips")))
    for kind, _cls, _v, _t, _p, note in KINDS:
        if kind in UNCHANGED:
            continue
        rows = [r for r in AW.CATALOG if r[8] == kind and r[0] not in AW.LORD_COLOUR and r[0] in have]
        if not rows:
            continue
        first = next((r for r in rows if r[2] == "C"), rows[0])
        btns = "".join(f'<button type="button" aria-pressed="{"true" if r is first else "false"}" data-id="{r[0]}" '
                       f'data-name="{html.escape(r[1])}">{TIER_NAME[r[2]]} · {r[2]}</button>' for r in rows)
        secs.append(f'''  <section class="style" id="kind-{kind}">
    <h3>{AW.STYLES[kind][0]}</h3>
    <p>{note}</p>
    <div class="tiers" role="group" aria-label="Rank">{btns}</div>
    <div class="strip"><img src="strips/{first[0]}.jpg" width="960" height="740" loading="lazy" alt="{html.escape(first[1])}: today and proposed, in first person, third person, the Hall of Arms and as an icon"></div>
    <button type="button" class="zoomer">Enlarge</button>
  </section>''')
    lords = [r for r in AW.CATALOG if r[0] in AW.LORD_COLOUR and r[0] in have]
    if lords:
        btns = "".join(f'<button type="button" aria-pressed="{"true" if i == 0 else "false"}" data-id="{r[0]}" '
                       f'data-name="{html.escape(r[1])}">{html.escape(r[1])}</button>' for i, r in enumerate(lords))
        secs.append(f'''  <section class="style" id="kind-lordforged">
    <h3>The Lord-forged</h3>
    <p>Eleven of the fifteen have blades. These drafts are an automatic first pass and rougher than the rest. In the last stage each is re-cut by hand, and I show you each one before it lands. The Bell Warden's Toll, the Root Saint's Reliquary, the Gearwright's Grip and Unit Zero's Countdown have no blade and stay as they are.</p>
    <div class="tiers" role="group" aria-label="Weapon">{btns}</div>
    <div class="strip"><img src="strips/{lords[0][0]}.jpg" width="960" height="740" loading="lazy" alt="{html.escape(lords[0][1])}: today and proposed, in first person, third person, the Hall of Arms and as an icon"></div>
    <button type="button" class="zoomer">Enlarge</button>
  </section>''')
    return "\n".join(secs)


GAME_FP = [("fp_longsword", "Gatewrought Longsword"), ("fp_katana", "Gatewrought Katana"), ("fp_greatblade", "Sovereign Greatblade"),
           ("fp_shieldblade", "Gatewrought Shieldblade"), ("fp_daggers", "Beastbound Daggers"), ("fp_scythe", "Veilwoven Scythe"),
           ("fp_axe", "Gatewrought Axe"), ("fp_spear", "Lordscale Spear")]
GAME_TP = [("tp_longsword", "Gatewrought Longsword"), ("tp_katana", "Gatewrought Katana"), ("tp_greatblade", "Sovereign Greatblade"),
           ("tp_scythe", "Veilwoven Scythe")]
GAME_HALL = [("hall_wall", "The wall"), ("hall_longsword", "Gatewrought Longsword"), ("hall_katana", "Gatewrought Katana"),
             ("hall_greatblade", "Sovereign Greatblade"), ("hall_scythe", "Veilwoven Scythe")]


def shot(src_dir, name, tag, crop=None, width=960):
    src = os.path.join(src_dir, name + ".png")
    if not os.path.exists(src):
        return None
    im = Image.open(src).convert("RGB")
    if crop:
        w, h = im.size
        im = im.crop((int(crop[0] * w), int(crop[1] * h), int(crop[2] * w), int(crop[3] * h)))
    im = im.resize((width, round(im.size[1] * width / im.size[0])), Image.LANCZOS)
    os.makedirs(os.path.join(OUT, "game"), exist_ok=True)
    rel = f"game/{name}_{tag}.jpg"
    im.save(os.path.join(OUT, rel), quality=86)
    return rel, im.size


def pairs(before, after, items, crop=None, width=960):
    out = []
    for name, label in items:
        a, b = shot(before, name, "today", crop, width), shot(after, name, "proposed", crop, width)
        if not a or not b:
            continue
        out.append(f'''    <figure><div class="pair">
      <div><span class="tag steel">Today</span><img src="{a[0]}" width="{a[1][0]}" height="{a[1][1]}" loading="lazy" alt="{html.escape(label)} in the game today"></div>
      <div><span class="tag gold">Proposed</span><img src="{b[0]}" width="{b[1][0]}" height="{b[1][1]}" loading="lazy" alt="{html.escape(label)} in the game, proposed"></div>
    </div><figcaption>{html.escape(label)}</figcaption></figure>''')
    return "\n".join(out)


def main(argv):
    os.makedirs(os.path.join(OUT, "strips"), exist_ok=True)
    for f in os.listdir(os.path.join(WORK, "strips")):
        if f.endswith(".jpg"):
            shutil.copy2(os.path.join(WORK, "strips", f), os.path.join(OUT, "strips", f))
    M, vanilla = measures()
    page = open(os.path.join(HERE, "page.src.html")).read()
    ls = M["gatewrought_longsword"]
    page = page.replace("<!--LS_PCT-->", f"{round((1 - ls[1] / ls[0]) * 100)}%")
    page = page.replace("<!--LS_TODAY-->", str(ls[0])).replace("<!--VANILLA-->", str(vanilla))
    page = page.replace("<!--GRADES-->", grades(M)).replace("<!--GALLERY-->", gallery())
    before, after = (argv + [None, None])[:2]
    early = ('\n  <p class="muted">These shots are of an earlier cut of the drafts: its points run one texel longer than the plan\'s, its Katana two, '
             'and its Sovereign Greatblade has a longer point. The four-view pictures further down are the plan as proposed. The tour of '
             'the final drafts is waiting in GitHub\'s queue and I will swap its shots in here when it comes back.</p>') if "early" in argv else ""
    ingame = before_fig = real = ""
    if before and os.path.isdir(before):
        three = [shot(before, n, "found", (0.42, 0.0, 1.0, 1.0), 560) for n in ("fp_longsword", "fp_greatblade", "fp_katana")]
        if all(three):
            cells = "".join(f'<div><img src="{t[0]}" width="{t[1][0]}" height="{t[1][1]}" loading="lazy" alt="{lab} in first person in the game today"><p>{lab}</p></div>'
                            for t, lab in zip(three, ("Longsword: three stacked planks", "Greatblade: a slab with a stair for a point", "Katana: a bent bar, three steps at the tip")))
            before_fig = (f'  <figure><div class="dirs" style="grid-template-columns:repeat(3,1fr)">{cells}</div>'
                          f'<figcaption>The real game today, first person at rest (CI look tour on the build as it stands).</figcaption></figure>')
    if before and after and os.path.isdir(before) and os.path.isdir(after):
        fp, tp, hall = pairs(before, after, GAME_FP), pairs(before, after, GAME_TP), pairs(before, after, GAME_HALL)
        if fp:
            ingame = f'''<section>
  <span class="eyebrow">In the real game</span>
  <h2>The same tour, today and with the drafts</h2>
  <p>I ran one look tour twice on GitHub's machines: once on the build as it stands, once on a throwaway branch with the draft models written over the game's. Same world, same camera, same script. The throwaway branch is never merged.</p>{early}
  <h3>First person, at rest</h3>
{fp}
  <h3>Third person, from the front</h3>
{tp}
  <h3>The Hall of Arms</h3>
{hall}
</section>'''
            real = ("<li><b>Real game:</b> the pictures under \"In the real game\" are screenshots from the game itself, from one look tour run on "
                    "today's build and again on a throwaway branch holding the drafts.</li>")
    page = page.replace("<!--INGAME_BEFORE-->", before_fig).replace("<!--INGAME-->", ingame).replace("<!--REAL_GAME-->", real)
    page = page.replace("/*GALLERY_DATA*/{}", json.dumps({}))
    with open(os.path.join(OUT, "index.html"), "w") as fh:
        fh.write(page)
    files = sorted(os.path.relpath(os.path.join(d, f), OUT) for d, _, fs in os.walk(OUT) for f in fs if f.endswith(".jpg") and not f.startswith("_"))
    json.dump(files, open(os.path.join(WORK, "page_files.json"), "w"))
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in files)
    print(f"{len(files)} pictures, {size / 1e6:.1f} MB, in-game section: {'yes' if ingame else 'no'}")


if __name__ == "__main__":
    main(sys.argv[1:])
