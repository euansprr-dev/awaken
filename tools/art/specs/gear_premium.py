"""The premium test's hero set (docs/armour/PREMIUM-TEST.md; branch pack/armour-premium-test, never merged): Veilwoven's clean
proof set (docs/armour/proof/proof_sets.py) written as a model spec the game builds and an atlas with a material half.

    python3 tools/art/specs/gear_premium.py            writes the spec, the atlas, the glow layer and a preview
    python3 tools/art/specs/gear_premium.py check      only the checks (the proof's overlap, face, rooted and connection checks on what is written)

What it writes (src/main/resources/assets/awaken/):
    gear_premium/veilwoven.json                      the spec: parts with box, grow, pose, uv, mirror and the slot that draws them
    textures/entity/equipment/premium/veilwoven.png  256x256: the albedo in the left half (u < 128), the material map in the right
                                                     half (r metal, g gloss, b emissive, a flow), the way the weapons' atlases are
    textures/entity/equipment/premium/veilwoven_glow.png  the glowing texels alone, for Level 1's emissive pass

Boxes are made whole here: the game lays a cube's faces out by its float sizes, so a 6.4-wide box would sample the wrong texels. Each
dimension is rounded and the difference goes into the cube's own (anisotropic) grow, which does not move the texture, so the geometry is
exactly the proof's and the faces land on whole texels.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, os.path.join(ROOT, "docs/armour/proof"))
sys.path.insert(0, os.path.join(ROOT, "tools/art"))
sys.path.insert(0, HERE)
import armour_proof as A  # noqa: E402
import audit as U  # noqa: E402
import proof_sets as S  # noqa: E402
from PIL import Image  # noqa: E402

RES = os.path.join(ROOT, "src/main/resources/assets/awaken")
SPEC = os.path.join(RES, "gear_premium/veilwoven.json")
TEX = os.path.join(RES, "textures/entity/equipment/premium")
HALF = (128, 256)       # the albedo's half of the atlas
ATLAS = (256, 256)

FEET = ("boot_cuff", "toe", "sh_boot")


def hero():
    """The proof's Veilwoven with the leg shell cut in two (the leggings take the upper leg, the boots the lower), so the two pieces
    never draw the same plane."""
    parts = S.veilwoven()
    out = []
    for p in parts:
        if p["name"] in ("sh_leg_r", "sh_leg_l"):
            continue
        out.append(p)
    base = [q for q in parts if q["name"] == "sh_leg_r"][0]
    legs = A.part("sh_leg_r", "right_leg", (-2.5, -0.5, -2.5, 4.4, 9.7, 5), "silk", **base["o"])
    boot = A.part("sh_boot_r", "right_leg", (-2.5, 9.2, -2.5, 4.4, 3.7, 5), "silk", **dict(base["o"], fold=0))
    rename = {"sh_leg_r": "sh_leg_l", "sh_boot_r": "sh_boot_l", "right_leg": "left_leg"}
    out += [legs, boot] + A.mirrored([legs, boot], rename)
    return out


def slot_of(p, byname):
    q = p
    while q["parent"] in byname:
        q = byname[q["parent"]]
    root = q["parent"]
    if root == "head":
        return "head"
    if root in ("body", "right_arm", "left_arm"):
        return "chest"
    return "feet" if any(p["name"].startswith(f) for f in FEET) else "legs"


def whole(p):
    """Whole-texel box sizes with the difference as anisotropic grow: the geometry is unchanged, the UV layout is exact."""
    x, y, z, w, h, d = p["box"]
    g = p["grow"]
    wi, hi, di = max(1, round(w)), max(1, round(h)), max(1, round(d))
    p["box"] = [x + (w - wi) / 2, y + (h - hi) / 2, z + (d - di) / 2, float(wi), float(hi), float(di)]
    p["grow3"] = [g + (w - wi) / 2, g + (h - hi) / 2, g + (d - di) / 2]


def material_map(alb, glw):
    """r metal, g gloss, b emissive, a flow per texel, from what the texel is (the painters know the part's kind; the texel's
    colour tells silver from silk from glass, which is enough for a test)."""
    def ramp(name):
        return {tuple(int(h.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)) for h in A.R[name]}
    silver, glass, rose = ramp("silver"), ramp("glass"), ramp("rose")
    a, g = alb.load(), glw.load()
    out = Image.new("RGBA", alb.size, (0, 0, 0, 0))
    o = out.load()
    for y in range(alb.height):
        for x in range(alb.width):
            r, gg, b, al = a[x, y]
            if al < 128:
                continue
            rgb = (r, gg, b)
            metal, gloss, emit, flow = 0, 40, 0, 0
            if rgb in silver:
                metal, gloss = 150, 140
            elif rgb in glass or rgb in rose:
                metal, gloss = 60, 230
            elif r + gg + b > 0:
                gloss = 70                      # silk: a soft sheen, no metal
            if g[x, y][3] >= 128:
                emit, gloss = 255, 200
                flow = 40 + ((x * 5 + y * 9) % 20) * 10      # the thread and the gems light in turn
            o[x, y] = (metal, gloss, emit, flow)
    return out


def build():
    parts = hero()
    byname = {p["name"]: p for p in parts}
    for p in parts:
        whole(p)
    parts, alb, glw = A.prepare(parts, HALF, 0)
    byname = {p["name"]: p for p in parts}
    spec = {"schema": 1, "texture": list(ATLAS), "parts": []}
    for p in parts:
        spec["parts"].append({
            "name": p["name"], "parent": p["parent"], "slot": slot_of(p, byname),
            "box": [round(v, 4) for v in p["box"]], "grow": [round(v, 4) for v in p["grow3"]],
            "pose": [round(v, 4) for v in p["pose"]], "uv": list(p["uv"]), "mirror": bool(p["twin"])})
    atlas = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    atlas.paste(alb, (0, 0))
    atlas.paste(material_map(alb, glw), (128, 0))
    glow = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    g, gl = glw.load(), glow.load()
    for y in range(glw.height):
        for x in range(glw.width):
            if g[x, y][3] >= 128:
                gl[x, y] = g[x, y]
    return parts, spec, atlas, glow, alb


def write():
    parts, spec, atlas, glow, alb = build()
    os.makedirs(os.path.dirname(SPEC), exist_ok=True)
    os.makedirs(TEX, exist_ok=True)
    with open(SPEC, "w") as f:
        json.dump(spec, f, indent=1)
        f.write("\n")
    atlas.save(os.path.join(TEX, "veilwoven.png"))
    glow.save(os.path.join(TEX, "veilwoven_glow.png"))
    counts = {s: sum(1 for p in spec["parts"] if p["slot"] == s) for s in ("head", "chest", "legs", "feet")}
    print("wrote", len(spec["parts"]), "parts", counts)
    return parts, alb


def check(parts, alb):
    """The proof's checks: the geometry on the set as designed (the whole-texel rounding keeps it exact), the face on what is written."""
    pre, _, _ = A.prepare(hero(), (256, 256))
    found = U.audit(pre) + U.rooted(pre) + U.connection(pre) + U.face_check(parts, alb)
    for f in found:
        print("  finding:", f[:3])
    print("checks:", len(found), "findings")
    return found


if __name__ == "__main__":
    if "check" in sys.argv:
        ps, _, _, _, al = build()
    else:
        ps, al = write()
    sys.exit(1 if check(ps, al) else 0)
