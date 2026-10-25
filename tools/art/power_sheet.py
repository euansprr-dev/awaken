"""Look sheets for the power icons (no game needed).

    python3 tools/art/power_sheet.py style <dir>      frames, samples at 1x/2x/4x, a HUD mock, the Power Core
    python3 tools/art/power_sheet.py contact <png>    all 63 icons grouped by family, with the Power Core crystals
"""
import importlib.util
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import powericons as K  # noqa: E402
import powerglyphs as GL  # noqa: E402

BG = (11, 15, 24, 255)
FAMILIES = ["kinetic", "velocity", "flight", "energy", "shadow", "mind", "architect", "tech", "spirit"]


def _spec():
    spec = importlib.util.spec_from_file_location("spec_powers", os.path.join(HERE, "specs", "powers.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def up(img, k):
    return img.resize((round(img.width * k), round(img.height * k)), Image.NEAREST)


def scene(w, h):
    """A dim dusk-world backdrop, like the HUD sits over."""
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        t = y / h
        c = (int(38 + 30 * t), int(52 + 40 * t), int(70 - 20 * t)) if t < 0.62 else (int(46 - 10 * t), int(70 - 20 * t), 38)
        d.line([(0, y), (w, y)], fill=c + (255,))
    return im


def hud(icons, gui):
    """The power bar as PowerHud draws it: 20-unit slots, 0xA0101622 fill, outline, icon at +2 (16 units)."""
    slot, gap = 20 * gui, 2 * gui
    w = 6 * slot + 5 * gap + 4 * gui + 24 * gui
    im = scene(w, slot + 16 * gui)
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    edges = [(58, 74, 96)] * 4 + [(154, 212, 255), (255, 207, 122)]
    for i, ic in enumerate(icons[:6]):
        x = 12 * gui + i * (slot + gap) + (4 * gui if i >= 4 else 0)
        y = 8 * gui
        d.rectangle([x, y, x + slot - 1, y + slot - 1], fill=(16, 22, 34, 160))
        d.rectangle([x, y, x + slot - 1, y + slot - 1], outline=edges[i] + (255,), width=gui)
        im = Image.alpha_composite(im, ov)
        ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(ov)
        big = up(ic, 16 * gui / ic.width)
        im.alpha_composite(big, (x + 2 * gui, y + 2 * gui))
    return im


def label(im, text, xy, col=(200, 210, 225)):
    ImageDraw.Draw(im).text(xy, text, fill=col + (255,))


def power_family(mod):
    return {p: fam for p, (fam, _g) in mod.POWERS.items()}


def new_icon(power, fam, mod=None):
    glow = mod.GLOW.get(power, fam in ("energy",)) if mod and hasattr(mod, "GLOW") else fam == "energy"
    return K.icon(fam, GL.GLYPHS[power], glow=glow).image()


def style(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    mod = _spec()
    fam_of = power_family(mod)
    # 1. the nine frames, colour and greyscale
    cell = 150
    im = Image.new("RGBA", (cell * 9, 330), BG)
    for i, fam in enumerate(FAMILIES):
        c, _f, _p = K.frame_canvas(fam)
        fr = c.image()
        im.alpha_composite(up(fr, 4), (i * cell + 11, 10))
        g = up(fr, 2).convert("LA").convert("RGBA")
        im.alpha_composite(g, (i * cell + 43, 160))
        im.alpha_composite(fr, (i * cell + 59, 240))
        label(im, fam, (i * cell + 11 + (128 - 6 * len(fam)) // 2, 290))
    im.save(os.path.join(out_dir, "frames.png"))
    # 2. samples: old (16px x4) | new 1x | 2x | 4x
    samples = list(GL.GLYPHS)
    old = {p: mod.icon(p, fam_of[p], mod.POWERS[p][1]).image() for p in samples}
    new = {p: new_icon(p, fam_of[p], mod) for p in samples}
    row = 150
    im = Image.new("RGBA", (5 * 160, row * ((len(samples) + 1) // 2)), BG)
    for i, p in enumerate(samples):
        ox, oy = (i % 2) * 400, (i // 2) * row
        im.alpha_composite(up(old[p], 4), (ox + 8, oy + 12))
        im.alpha_composite(new[p], (ox + 88, oy + 12))
        im.alpha_composite(up(new[p], 2), (ox + 88, oy + 54))
        im.alpha_composite(up(new[p], 4), (ox + 160, oy + 12))
        label(im, p.replace("_", " "), (ox + 8, oy + 128))
        label(im, "before", (ox + 18, oy + 80), (120, 130, 150))
    im.save(os.path.join(out_dir, "samples.png"))
    # 3. the HUD mock at GUI scale 2 and 4 (Retina Macs auto-pick 4 or more)
    pick = ["haymaker", "prism_lance", "storm_call", "railgun", "lightning_step", "meteor_dive"]
    hud([new[p] for p in pick], 2).save(os.path.join(out_dir, "hud_gui2.png"))
    hud([new[p] for p in pick], 4).save(os.path.join(out_dir, "hud_gui4.png"))
    hud([old[p] for p in pick], 4).save(os.path.join(out_dir, "hud_gui4_before.png"))
    # 4. Power Core crystals: item size (16 units) at GUI 2 and 4, plus the glow layer alone
    cores = ["haymaker", "prism_lance", "shadow_step", "storm_call"]
    im = Image.new("RGBA", (len(cores) * 210, 170), BG)
    for i, p in enumerate(cores):
        c = K.core(fam_of[p], GL.GLYPHS[p]).image()
        gl = K.core(fam_of[p], GL.GLYPHS[p], glow_only=True).image()
        im.alpha_composite(c, (i * 210 + 8, 10))
        im.alpha_composite(up(c, 4), (i * 210 + 48, 10))
        im.alpha_composite(up(gl, 2), (i * 210 + 8, 60))
        label(im, p.replace("_", " "), (i * 210 + 48, 146))
    im.save(os.path.join(out_dir, "cores.png"))
    print("style sheet ->", out_dir)


def contact(out_png):
    mod = _spec()
    by_fam = {f: [p for p, (fam, _g) in mod.POWERS.items() if fam == f] for f in FAMILIES}
    cols, cell = 7, 112
    rows = sum(1 + (len(v) + cols - 1) // cols for v in by_fam.values())
    im = Image.new("RGBA", (cols * cell * 2 + 24, rows * cell // 1 + 20), BG)
    y = 8
    for fam, powers in by_fam.items():
        label(im, fam.upper(), (12, y))
        y += 14
        for i, p in enumerate(powers):
            x = 12 + (i % cols) * cell * 2
            yy = y + (i // cols) * cell
            ic = new_icon(p, fam, mod)
            im.alpha_composite(up(ic, 2), (x, yy))
            im.alpha_composite(ic, (x + 70, yy))
            core = K.core(fam, GL.GLYPHS[p]).image()
            im.alpha_composite(up(core, 2), (x + 110, yy))
            im.alpha_composite(core, (x + 180, yy))
            label(im, p, (x, yy + 68))
        y += ((len(powers) + cols - 1) // cols) * cell - 10
    im = im.crop((0, 0, im.width, y + 10))
    im.save(out_png)
    print("contact sheet ->", out_png)


if __name__ == "__main__":
    {"style": style, "contact": contact}[sys.argv[1]](sys.argv[2])
