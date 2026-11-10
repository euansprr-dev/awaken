"""Builds The Way Up's approval page: docs/movement-unlocks/page.src.html -> page.html, with the tours' pictures cut down
into docs/movement-unlocks/shots/.

    python3 docs/movement-unlocks/build_page.py [cut]

"cut" re-cuts the pictures from the tour folders named in shots.py (TOURS); without it the pictures in shots/ are used.
Publish page.html with shots/ as its files."""
import html
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))


def load():
    ns = {}
    exec(open(os.path.join(HERE, "shots.py")).read(), ns)
    return ns


def cut(src, name, crop):
    from PIL import Image
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
    im.save(os.path.join(HERE, "shots", name + ".jpg"), quality=80, optimize=True)


def have(name):
    return os.path.exists(os.path.join(HERE, "shots", name + ".jpg"))


def figure(name, cap):
    alt = html.escape(cap.replace("<b>", "").replace("</b>", ""))
    return f'<figure><img src="shots/{name}.jpg" alt="{alt}" loading="lazy"><figcaption>{cap}</figcaption></figure>'


def main():
    ns = load()
    if len(sys.argv) > 1 and sys.argv[1] == "cut":
        for name, (tour, shot, crop) in ns["CUTS"].items():
            cut(os.path.join(ROOT, ns["TOURS"][tour], shot + ".png"), name, crop)
    s = open(os.path.join(HERE, "page.src.html")).read()
    # one picture with its caption
    for key, (name, cap) in ns["FIGS"].items():
        s = s.replace("@@FIG:" + key + "@@", figure(name, cap) if have(name) else "")
    # a strip of pictures under a rung (several real frames of one moment)
    for key, rows in ns["STRIPS"].items():
        rows = [(n, c) for n, c in rows if have(n)]
        strip = ""
        if rows:
            strip = '<div class="three">' + "".join(figure(n, c) for n, c in rows) + "</div>"
        s = s.replace("@@FIG:" + key + "@@", strip)
    # a mock-up's background
    for key, name in ns["IMGS"].items():
        s = s.replace("@@IMG:" + key + "@@", f'<img src="shots/{name}.jpg" alt="" loading="lazy">' if have(name) else "")
    for k, v in ns["TEXT"].items():
        s = s.replace("@@" + k + "@@", v)
    left = sorted({w for w in s.split("@@")[1::2] if w.replace(":", "").replace("_", "").isalnum() and w.upper() == w or w.startswith(("FIG:", "IMG:"))})
    if left:
        print("unfilled:", left)
    open(os.path.join(HERE, "page.html"), "w").write(s)
    n = len(os.listdir(os.path.join(HERE, "shots"))) if os.path.isdir(os.path.join(HERE, "shots")) else 0
    print("page.html", len(s) // 1024, "KB;", n, "pictures")


if __name__ == "__main__":
    main()
