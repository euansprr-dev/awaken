"""Builds the Rope and Wings approval page: docs/movement/page.src.html -> docs/movement/page.html, with the two live
models (src/wings.js, src/swing.js) inlined and the tour's pictures cut down into docs/movement/shots/.

    python3 docs/movement/build_page.py [run-autopilot/screenshots-ci/movement-now-<run> [.../movement-wood-<run>]]

Without a tour folder the pictures already in shots/ are used. Publish page.html with shots/ as its files."""
import html
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

# (file on the page, the tour's shot, crop as fractions (x0, y0, x1, y1) or None, caption)
ROPE = [
    ("rope_wood_a", "a_wood_tp_3", None, "<b>A real forest, rope held.</b> @@WOOD_A@@"),
    ("rope_wood_b", "a_wood_tp_8", None, "<b>The same run, a few seconds on.</b> @@WOOD_B@@"),
    ("rope_lane", "b_lane_tp_05", None, "<b>Giant trees, 26 to 38 blocks tall.</b> Here the rope works: a fitted swing past the trunk."),
    ("rope_street", "c_street_tp_05", None, "<b>A street of towers.</b> The rope's best case, and the one it was tested in."),
]
WING = [
    ("wing_back", "d_glide_back_04", None, "<b>Gliding, from behind.</b> One see-through sheet a side."),
    ("wing_front", "d_glide_front_02", None, "<b>From the front.</b> No layers, no thickness."),
    ("wing_beat", "e_soar_back_03", None, "<b>A beat in flight.</b> @@BEAT@@"),
    ("wing_hover", "e_soar_back_14", None, "<b>Hovering.</b> The same sheet, upright."),
    ("wing_fp", "e_soar_fp_02", None, "<b>Through your own eyes.</b> The outer half of the sheet, faded."),
    ("wing_sovereign", "g_sovereign_back_02", None, "<b>The Sovereign.</b> The same wing, tinted gold."),
]
# what the second tour showed, in the page's words (filled in by hand after reading its log and pictures)
TEXT = {
    "WOOD_A": "",
    "WOOD_B": "",
    "BEAT": "",
    "ROPE3_EV": "",
    "ROPE4_HEAD": "",
    "ROPE4_EV": "",
    "WING2_HEAD": "",
    "WING2_EV": "",
}


def load_text():
    p = os.path.join(HERE, "tour_text.py")
    if os.path.exists(p):
        ns = {}
        exec(open(p).read(), ns)
        TEXT.update(ns.get("TEXT", {}))
        for key in ("ROPE", "WING"):
            if key in ns:
                globals()[key] = ns[key]


def cut(tour, name, shot, crop):
    from PIL import Image
    if shot.startswith("WOOD:"):
        if len(sys.argv) < 3:
            return
        tour, shot = sys.argv[2], shot[5:]
    src = os.path.join(tour, shot + ".png")
    if not os.path.exists(src):
        print("missing", src)
        return
    im = Image.open(src).convert("RGB")
    if crop:
        w, h = im.size
        im = im.crop((int(crop[0] * w), int(crop[1] * h), int(crop[2] * w), int(crop[3] * h)))
    w, h = im.size
    if w > 1280:
        im = im.resize((1280, round(h * 1280 / w)), Image.LANCZOS)
    os.makedirs(os.path.join(HERE, "shots"), exist_ok=True)
    im.save(os.path.join(HERE, "shots", name + ".jpg"), quality=82, optimize=True)


def figures(rows):
    out = []
    for name, _, _, cap in rows:
        for k, v in TEXT.items():
            cap = cap.replace("@@" + k + "@@", v)
        if not os.path.exists(os.path.join(HERE, "shots", name + ".jpg")):
            continue
        alt = html.escape(cap.replace("<b>", "").replace("</b>", ""))
        out.append(f'<figure><img src="shots/{name}.jpg" alt="{alt}"><figcaption>{cap}</figcaption></figure>')
    return "\n    ".join(out)


def main():
    load_text()
    if len(sys.argv) > 1:
        for name, shot, crop, _ in ROPE + WING:
            cut(sys.argv[1], name, shot, crop)
    s = open(os.path.join(HERE, "page.src.html")).read()
    s = s.replace("@@ROPE_SHOTS@@", figures(ROPE)).replace("@@WING_SHOTS@@", figures(WING))
    for k, v in TEXT.items():
        s = s.replace("@@" + k + "@@", v)
    for tag, path in (("WING_TODAY_JS", "src/wing_today.js"), ("WINGS_JS", "src/wings.js"), ("SWING_JS", "src/swing.js")):
        s = s.replace("@@" + tag + "@@", open(os.path.join(HERE, path)).read().replace("</script", "<\\/script"))
    left = [w for w in s.split("@@")[1::2] if w.isupper()]
    if left:
        print("unfilled:", left)
    open(os.path.join(HERE, "page.html"), "w").write(s)
    print("page.html", len(s) // 1024, "KB;", len(os.listdir(os.path.join(HERE, "shots"))) if os.path.isdir(os.path.join(HERE, "shots")) else 0, "pictures")


if __name__ == "__main__":
    main()
