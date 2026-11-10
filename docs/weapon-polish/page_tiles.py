"""The approval page's comparison tiles: one Longsword in the voxel cuts tried, and the Katana's cuts, each a tile of
first person over the upright weapon (face, edge). Writes build/weapon-polish/page/vdir_*.jpg and vkat_*.jpg."""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
for p in (os.path.join(ROOT, "tools/art"), os.path.join(ROOT, "tools/art/specs")):
    sys.path.insert(0, p)
import arms_polish as AP  # noqa: E402
import arms_weapons as AW  # noqa: E402

OUT = os.path.join(ROOT, "build/weapon-polish/page")


def tiles(row, shots, prefix):
    fulls = [AP.fp_full(row, bl, lk) for _, (bl, lk) in shots]
    hit = np.any([h for _, h in fulls], axis=0)
    ys, xs = np.nonzero(hit)
    H, W = hit.shape
    w = 500
    x0 = int(min(max((xs.min() + xs.max()) / 2 - w / 2, 0), W - w))
    for (name, (bl, lk)), (im, _) in zip(shots, fulls):
        tile = Image.new("RGB", (500, 720 + 300), (17, 17, 23))
        tile.paste(im.crop((x0, 0, x0 + w, H)), (0, 0))
        tile.paste(AP.hall_shot(bl, lk, (250, 300)), (0, 720))
        tile.paste(AP.hall_shot(bl, lk, (250, 300), yaw=84, pitch=6), (250, 720))
        tile.resize((375, 765), Image.LANCZOS).save(os.path.join(OUT, f"{prefix}_{name}.jpg"), quality=88)
        print(prefix, name, len(bl.pos) // 3, "triangles")


def main():
    os.makedirs(OUT, exist_ok=True)
    row = AP.ROWS["gatewrought_longsword"]
    base = AW.design_of(row)

    def variant(fn):
        def design():
            saved = AW.blade
            AW.blade = fn
            try:
                return base()
            finally:
                AW.blade = saved
        return design

    def thin(segs, core, edge, **kw):
        kw.pop("core_d", None)
        return AP.ORIG["blade"](segs, core, edge, core_d=2, **kw)

    tiles(row, [("today", AW.make(row)), ("thin", AP.make2(row, variant(thin))), ("ridge", AP.make2(row, variant(AP.vblade))),
                ("flat", AP.make2(row, variant(lambda *a, **k: AP.vblade(*a, ridge_d=1, **k))))], "vdir")
    row = AP.ROWS["gatewrought_katana"]
    shots = [("today", AW.make(row))]
    for name, cut in (("curve", (2, 0)), ("flat", (1, 0))):
        AP.KAT_CUT = cut
        with AP.Voxel():
            shots.append((name, AP.make2(row, AW.design_of(row))))
    AP.KAT_CUT = (1, 0)
    tiles(row, shots, "vkat")


if __name__ == "__main__":
    main()
