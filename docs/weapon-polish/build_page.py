"""Builds the weapon polish approval page (the voxel version) from page.src.html into build/weapon-polish/page/
(index.html and its pictures), from what the draft harness has rendered:

    python3 tools/art/arms_polish.py changed                                   which weapons change (docs/weapon-polish/changed.json)
    python3 tools/art/arms_polish.py strips build/weapon-polish/vstrips        the four-view pictures
    python3 tools/art/arms_polish.py measure > build/weapon-polish/vmeasure.txt
    python3 docs/weapon-polish/page_tiles.py                                   the comparison tiles
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
CHANGED = set(json.load(open(os.path.join(HERE, "changed.json"))))

# kind -> (verdict class, verdict, blade today, proposed, the gallery's line)
KINDS = [
    ("longsword", "fat", "Fat", "5 × 3, a 3 × 3 post", "5 × 1 slab, a 1 × 3 ridge, a longer stepped point",
     "The square post goes. A slab one texel thick with a ridge one texel wide, and a point that steps 5, 3, 1 over eight "
     "texels. The Beastbound cleaver keeps its bronze back as its spine and thins under it."),
    ("katana", "ok", "Decent", "3 × 2, straight then a hook", "3 × 1, an even curve, a stepped point",
     "The bend is spread evenly from collar to point where today it runs straight and hooks at the tip. One texel thick, "
     "its last two pieces narrowing up to the back. Same length, same six turned boxes."),
    ("greatblade", "fat", "Fat", "9 to 11 × 3", "9 to 11 × 1 slab, a 3-wide spine",
     "Each keeps its own outline: the flint slab, the tusk, the gate's window, the flamberge, the fork, the crown. The "
     "plank becomes a slab with a spine three wide."),
    ("shieldblade", "fat", "Fattest", "6 × 4, plus a raised groove", "5 × 1 slab, the lit groove as its ridge",
     "The sword was the fattest blade in the game. It takes the Longsword's width and cut, and its lit groove becomes "
     "its ridge. Its stone comes down to the 5-deep rule. The shield is untouched."),
    ("daggers", "fat", "Chunky", "5 × 3; tusks 3 × 3", "5 × 1 slab and ridge; tusks 3 × 2",
     "The straight ones take the Longsword's cut. The tusk and the talon lose a texel of thickness. The flint knife "
     "keeps a thick back."),
    ("twinblades", "fat", "Fat", "5 × 3", "5 × 1 slab, a 1 × 3 ridge",
     "Both blades take the Longsword's cut. The Gatewrought pair already had it and only gains the finer point."),
    ("glaive", "fat", "A bent bar", "5 × 3", "5 × 1 flat, a back 3 thick",
     "The curved blade becomes a thin flat with a thick back along its spine, the same turned boxes."),
    ("scythe", "fat", "A bent bar", "5 × 3", "5 × 1 flat, a back 3 thick",
     "The crescent becomes a thin flat with a thick back along the outside of the curve."),
    ("spear", "fat", "Stubby head", "7 × 3", "7 × 1 slab, a 1 × 3 ridge",
     "The heads keep their shapes (leaf, lugs, wave, wings, spire) and take the slab and ridge. The Gatewrought fork "
     "is already slim and stays."),
    ("chainblade", "ok", "Good, blocky head", "5 to 7 × 3", "slab and ridge on the four straight heads",
     "Only the four straight heads change. The chain, the coil and the two tusk hooks stay."),
    ("rapier", "keep", "Good", "3 × 1 slab, a 1 × 3 ridge", "untouched: it already has the cut", ""),
    ("axe", "keep", "Good", "a stepped bit, 3 thick", "untouched", ""),
    ("maul", "keep", "The bar", "", "untouched", ""),
    ("warstaff", "keep", "Good", "", "untouched", ""),
    ("precursor", "keep", "Good", "its blade is light", "untouched", ""),
    ("gauntlets", "keep", "Good", "it is armour", "untouched", ""),
]


def measures():
    out, vanilla = {}, 0
    for line in open(os.path.join(WORK, "vmeasure.txt")):
        m = re.match(r"vanilla diamond sword:\s+(\d+)", line)
        if m:
            vanilla = int(m.group(1))
        m = re.match(r"(\w+)\s+today\s+(\d+) px\s+proposed\s+(\d+) px.*triangles (\d+) -> (\d+)", line)
        if m:
            out[m.group(1)] = (int(m.group(2)), int(m.group(3)), int(m.group(4)), int(m.group(5)))
    return out, vanilla


def grades():
    rows = []
    for kind, cls, verdict, today, prop, _ in KINDS:
        rows.append(f"        <tr><td><b>{AW.STYLES[kind][0]}</b></td><td><span class=\"v {cls}\">{verdict}</span></td>"
                    f"<td class=\"n\">{today}</td><td class=\"n\">{prop}</td></tr>")
    return "\n".join(rows)


def gallery():
    secs = []
    have = set(f[:-4] for f in os.listdir(os.path.join(OUT, "vstrips")))
    for kind, _cls, _v, _t, _p, note in KINDS:
        rows = [r for r in AW.CATALOG if r[8] == kind and r[0] not in AW.LORD_COLOUR and r[0] in have and r[0] in CHANGED]
        if not rows:
            continue
        first = next((r for r in rows if r[2] == "C"), rows[0])
        btns = "".join(f'<button type="button" aria-pressed="{"true" if r is first else "false"}" data-id="{r[0]}" '
                       f'data-name="{html.escape(r[1])}">{TIER_NAME[r[2]]} · {r[2]}</button>' for r in rows)
        secs.append(f'''  <section class="style" id="kind-{kind}">
    <h3>{AW.STYLES[kind][0]}</h3>
    <p>{note}</p>
    <div class="tiers" role="group" aria-label="Rank">{btns}</div>
    <div class="strip"><img src="vstrips/{first[0]}.jpg" width="960" height="740" loading="lazy" alt="{html.escape(first[1])}: today and proposed, in first person, third person, the Hall of Arms and as an icon"></div>
    <button type="button" class="zoomer">Enlarge</button>
  </section>''')
    lords = [r for r in AW.CATALOG if r[0] in AW.LORD_COLOUR and r[0] in have and r[0] in CHANGED]
    if lords:
        btns = "".join(f'<button type="button" aria-pressed="{"true" if i == 0 else "false"}" data-id="{r[0]}" '
                       f'data-name="{html.escape(r[1])}">{html.escape(r[1])}</button>' for i, r in enumerate(lords))
        secs.append(f'''  <section class="style" id="kind-lordforged">
    <h3>The Lord-forged</h3>
    <p>Six of the fifteen share these cuts. These drafts come from the same rules with no hand work yet. In the last stage each is checked by hand, and I show you each one before it lands. The other nine stay as they are.</p>
    <div class="tiers" role="group" aria-label="Weapon">{btns}</div>
    <div class="strip"><img src="vstrips/{lords[0][0]}.jpg" width="960" height="740" loading="lazy" alt="{html.escape(lords[0][1])}: today and proposed, in first person, third person, the Hall of Arms and as an icon"></div>
    <button type="button" class="zoomer">Enlarge</button>
  </section>''')
    return "\n".join(secs)


GAME_FP = [("fp_longsword", "Gatewrought Longsword"), ("fp_katana", "Gatewrought Katana"), ("fp_greatblade", "Sovereign Greatblade"),
           ("fp_shieldblade", "Gatewrought Shieldblade"), ("fp_daggers", "Beastbound Daggers"), ("fp_scythe", "Veilwoven Scythe"),
           ("fp_spear", "Lordscale Spear")]
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
    os.makedirs(os.path.join(OUT, "vgame"), exist_ok=True)
    rel = f"vgame/{name}_{tag}.jpg"
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
    os.makedirs(os.path.join(OUT, "vstrips"), exist_ok=True)
    for f in os.listdir(os.path.join(WORK, "vstrips")):
        if f.endswith(".jpg") and f[:-4] in CHANGED:
            shutil.copy2(os.path.join(WORK, "vstrips", f), os.path.join(OUT, "vstrips", f))
    M, vanilla = measures()
    page = open(os.path.join(HERE, "page.src.html")).read()
    ls = M["gatewrought_longsword"]
    page = page.replace("<!--LS_TODAY-->", str(ls[0])).replace("<!--VANILLA-->", str(vanilla))
    page = page.replace("<!--N_CHANGED-->", str(len(CHANGED))).replace("<!--N_KEPT-->", str(len(AW.CATALOG) - len(CHANGED)))
    page = page.replace("<!--GRADES-->", grades()).replace("<!--GALLERY-->", gallery())
    before, after = (argv + [None, None])[:2]
    ingame = before_fig = real = ""
    if before and os.path.isdir(before):
        three = [shot(before, n, "found", (0.42, 0.0, 1.0, 1.0), 560) for n in ("fp_longsword", "fp_greatblade", "fp_katana")]
        if all(three):
            cells = "".join(f'<div><img src="{t[0]}" width="{t[1][0]}" height="{t[1][1]}" loading="lazy" alt="{lab} in first person in the game today"><p>{lab}</p></div>'
                            for t, lab in zip(three, ("Longsword: three stacked planks", "Greatblade: a thick slab", "Katana: a bar, hooked at the tip")))
            before_fig = (f'  <figure><div class="dirs three">{cells}</div>'
                          f'<figcaption>The real game today, first person at rest (CI look tour on the build as it stands).</figcaption></figure>')
    if before and after and os.path.isdir(before) and os.path.isdir(after):
        fp, tp, hall = pairs(before, after, GAME_FP), pairs(before, after, GAME_TP), pairs(before, after, GAME_HALL)
        if fp:
            ingame = f'''<section>
  <span class="eyebrow">In the real game</span>
  <h2>The same tour, before and after</h2>
  <p>One look tour run twice on GitHub's machines: on the build before this pass, and with the re-cut weapons in the game. Same world, same camera, same script. These are the weapons as built.</p>
  <h3>First person, at rest</h3>
{fp}
  <h3>Third person, from the front</h3>
{tp}
  <h3>The Hall of Arms</h3>
{hall}
</section>'''
            real = ("<li><b>Real game:</b> the pictures under \"In the real game\" are screenshots from the game itself, from one look tour run on "
                    "today's build and again on a throwaway branch holding the voxel drafts.</li>")
    if not ingame:
        ingame = '''<section>
  <span class="eyebrow">In the real game</span>
  <h2>Waiting in the queue</h2>
  <p>The look tour of these voxel drafts in the real game (first person, third person and the Hall of Arms, beside today's) is waiting in GitHub's queue behind other sessions' tours. I add its shots here when it comes back. The first version's models already ran in the game through the same pipeline, and these are plainer: boxes only.</p>
</section>'''
    page = page.replace("<!--INGAME_BEFORE-->", before_fig).replace("<!--INGAME-->", ingame).replace("<!--REAL_GAME-->", real)
    with open(os.path.join(OUT, "index.html"), "w") as fh:
        fh.write(page)
    keep = ("vstrips", "vgame")
    files = sorted(os.path.relpath(os.path.join(d, f), OUT) for d, _, fs in os.walk(OUT) for f in fs
                   if f.endswith(".jpg") and (os.path.relpath(d, OUT) in keep or f.startswith(("vdir_", "vkat_"))))
    json.dump(files, open(os.path.join(WORK, "page_files.json"), "w"))
    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in files)
    print(f"{len(files)} pictures, {size / 1e6:.1f} MB, in-game section: {'yes' if ingame else 'no'}")


if __name__ == "__main__":
    main(sys.argv[1:])
