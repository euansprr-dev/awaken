"""Fingerprints of the approved voxel drafts (tools/art/arms_polish.py, MODE "voxel"), and the check that the weapon
spec, once the re-cut is ported into it, builds the very same weapons.

    python3 docs/weapon-polish/fingerprint.py draft    writes build/weapon-polish/voxel_fp.json from the draft harness
    python3 docs/weapon-polish/fingerprint.py check    builds every weapon from arms_weapons.py alone and compares
"""
import hashlib
import json
import os
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
for p in (os.path.join(ROOT, "tools/art"), os.path.join(ROOT, "tools/art/specs")):
    sys.path.insert(0, p)
import arms_weapons as AW  # noqa: E402

OUT = os.path.join(ROOT, "build/weapon-polish/voxel_fp.json")


def geo(built):
    """The weapon's surface whatever order its boxes came in: every triangle's corners, sorted."""
    tri = np.round(built.pos.reshape(-1, 9), 3)
    tri = tri[np.lexsort(tri.T[::-1])]
    return hashlib.sha1(tri.tobytes()).hexdigest()


def icon_of(built_clean, look):
    return hashlib.sha1(np.asarray(AW.icon(built_clean, look)).tobytes()).hexdigest()


def draft():
    import arms_polish as AP
    out = {}
    for row in AW.CATALOG:
        with AP.Proposed():
            d = AP.after_design(row)
            b, look = AP.make2(row, d)
            c, _ = AP.make2(row, d, clean=True)
            fp = {"geo": geo(b), "icon": icon_of(c, look), "tris": len(b.pos) // 3}
            if row[8] == "shieldblade" and row[0] not in AW.LORD_COLOUR:
                boxes, _a, _l = AP.vsword(row[2])
                fp["blade"] = geo(AP.build2(boxes, AP.painter2(False), AW.to_item))
        out[row[0]] = fp
    json.dump(out, open(OUT, "w"), indent=0)
    print(len(out), "fingerprints written")


def check():
    import voxel as VX
    want = json.load(open(OUT))
    bad = []
    for row in AW.CATALOG:
        b, look = AW.make(row)
        c, _ = AW.make(row, clean=True)
        fp = want[row[0]]
        if geo(b) != fp["geo"]:
            bad.append((row[0], "shape", len(b.pos) // 3, fp["tris"]))
        elif icon_of(c, look) != fp["icon"]:
            bad.append((row[0], "paint (icon)"))
        if "blade" in fp:
            boxes, _a, _l = AW.shieldblade_blade(row[2])
            if geo(VX.build(boxes, AW.painter(False), AW.to_item, width=64)) != fp["blade"]:
                bad.append((row[0], "the sword in the hand"))
    for b_ in bad:
        print("DIFFERS", *b_)
    print(len(AW.CATALOG) - len({b_[0] for b_ in bad}), "of", len(AW.CATALOG), "weapons build exactly as the approved drafts")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(draft() if sys.argv[1:] == ["draft"] else check())
