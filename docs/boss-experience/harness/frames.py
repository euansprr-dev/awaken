#!/usr/bin/env python3
"""Cuts a CI tour's shots into the page's frame strips (planning harness).

  python3 docs/boss-experience/harness/frames.py <group> <run folder> <crop x0,y0,x1,y1 | full> <width> <note> <name=caption> ...

Each named shot (without .png) is cropped, scaled to <width> and saved as img/<group>_<name>.jpg; img/frames.json gains
the group ({"note", "frames": [{"file", "alt", "caption"}]}), which build_page.py puts where the page says
<!--FRAMES:group-->.
"""
import json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(HERE, "..", "img")


def main():
    group, folder, crop, width, note = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4]), sys.argv[5]
    frames = []
    for arg in sys.argv[6:]:
        name, caption = arg.split("=", 1)
        im = Image.open(os.path.join(folder, name + ".png")).convert("RGB")
        if crop != "full":
            im = im.crop(tuple(int(v) for v in crop.split(",")))
        im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
        out = "%s_%s.jpg" % (group, name)
        im.save(os.path.join(IMG, out), quality=72, optimize=True)
        frames.append({"file": out, "alt": caption, "caption": caption})
    path = os.path.join(IMG, "frames.json")
    data = json.load(open(path)) if os.path.exists(path) else {}
    data[group] = {"note": note, "frames": frames}
    json.dump(data, open(path, "w"), indent=1)
    print(group, len(frames), "frames")


if __name__ == "__main__":
    main()
