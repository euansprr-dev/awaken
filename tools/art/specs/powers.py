"""Powers module textures: 63 power icons, the Power Core crystals, constructs, Stunned.

Icons are 32x32 GUI sprites at textures/gui/sprites/power/<id>.png, made with the kit in tools/art/powericons.py
(a frame per family and one lighting pass) and the glyphs in tools/art/powerglyphs.py. Every power also gets its
Power Core crystal: item/power_core/<id> plus the emissive item/power_core/<id>_glow, picked in-game by the
awaken:core_power select (powers/client/CorePowerSelect). Look sheets: python3 tools/art/power_sheet.py contact <png>
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pixelart import *  # noqa: E402,F403
from pixelart import _outline  # noqa: E402
import powericons as K  # noqa: E402
import powerglyphs as GL  # noqa: E402

# power id -> family
POWERS = {
    # kinetic
    "ground_slam": "kinetic", "leap": "kinetic", "haymaker": "kinetic", "terrain_rip": "kinetic",
    "shockwave": "kinetic", "brace": "kinetic", "thunderclap": "kinetic",
    # velocity
    "dash": "velocity", "flow": "velocity", "afterimage": "velocity", "wall_run": "velocity",
    "water_run": "velocity", "lightning_step": "velocity",
    # flight
    "hover": "flight", "flight": "flight", "gravity_well": "flight", "telekinesis": "flight",
    "meteor_dive": "flight", "gravity_flip": "flight",
    # energy
    "beam": "energy", "nova": "energy", "barrier": "energy", "charged_shot": "energy",
    "overload": "energy", "prism_lance": "energy",
    # shadow
    "shadow_step": "shadow", "cloak": "shadow", "echo_swap": "shadow", "legion": "shadow",
    # mind
    "danger_sense": "mind", "dominate": "mind", "read_intent": "mind", "decoy": "mind",
    "foresight": "mind",
    # architect
    "wall": "architect", "ramp": "architect", "floor": "architect", "cone": "architect",
    "rampart": "architect", "spire": "architect", "collapse": "architect", "citadel": "architect",
    # tech
    "grapple": "tech", "jet_burst": "tech", "drone": "tech", "turret": "tech",
    "railgun": "tech", "nanite_skin": "tech", "drone_swarm": "tech",
    # spirit
    "aura": "spirit", "spirit_palm": "spirit", "mend": "spirit", "third_eye": "spirit",
    "cord_lash": "spirit", "bulwark": "spirit", "hundred_palms": "spirit", "void_maw": "spirit",
    "waking_dream": "spirit", "storm_call": "spirit", "ember_step": "spirit", "frost_nova": "spirit",
    "ghost_step": "spirit", "twin_strike": "spirit",
}

# energy-like powers: their glyph glows (a halo instead of a drop shadow, a lit face behind it)
GLOW = {p for p, f in POWERS.items() if f == "energy"} | {
    "lightning_step", "thunderclap", "aura", "mend", "third_eye", "ember_step", "frost_nova", "storm_call", "foresight",
}


def construct(ramp_name, age, seed):
    """Hard light: a translucent fill with a bright frame. Cracks spread and the fill thins as it ages."""
    rp = ramp(ramp_name)
    c = Canvas()
    rnd = rng_for("construct", ramp_name, seed)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            inner = x in (1, 14) or y in (1, 14)
            if edge:
                r, g, b, _ = rp[4]
                c.set(x, y, (r, g, b, 235 - age * 20))
            elif inner:
                r, g, b, _ = rp[3]
                c.set(x, y, (r, g, b, 150 - age * 15))
            else:
                r, g, b, _ = rp[3]
                shimmer = 24 if (x + y + seed) % 7 == 0 else 0
                c.set(x, y, (r, g, b, 105 - age * 12 + shimmer))
    # corner glints
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        r, g, b, _ = rp[5]
        c.set(x, y, (r, g, b, 230))
    # cracks grow with age
    for k in range(age * 2):
        x, y = rnd.randint(3, 12), rnd.randint(3, 12)
        for _ in range(rnd.randint(2, 4)):
            r, g, b, _ = rp[5]
            c.set(x, y, (r, g, b, 200))
            x += rnd.choice((-1, 0, 1))
            y += rnd.choice((-1, 1))
    return c


def stunned():
    c = Canvas(18, 18)
    rp = ramp("signal")
    am = ramp("amber")
    for k in range(3):
        a = k * 2 * math.pi / 3
        cx, cy = 9 + math.cos(a) * 5, 9 + math.sin(a) * 5
        for x in range(18):
            for y in range(18):
                if math.hypot(x - cx, y - cy) <= 2.2:
                    c.set(x, y, am[4] if k == 0 else rp[4])
    for x in range(4, 14):
        c.set(x, 9, rp[5])
    return _outline(c)


def _write_json(out, rel, data):
    path = os.path.join(out.root, "assets/awaken", rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def _core_model(tex):
    return {"parent": "minecraft:item/generated", "textures": {
        "layer0": "awaken:item/" + tex,
        "layer1": {"sprite": "awaken:item/" + tex + "_glow", "neoforge_data": {"light_emission": 15}}}}


def build(out):
    for power, fam in POWERS.items():
        draw = GL.GLYPHS[power]
        out.gui("sprites/power/" + power, K.icon(fam, draw, glow=power in GLOW))
        out.item("power_core/" + power, K.core(fam, draw))
        out.item("power_core/" + power + "_glow", K.core(fam, draw, glow_only=True))
        _write_json(out, f"models/item/power_core/{power}.json", _core_model("power_core/" + power))
    # a core with no power (or one added later without a crystal) shows a blank Energy crystal
    out.item("power_core", K.core("energy"))
    out.item("power_core_glow", K.core("energy", glow_only=True))
    _write_json(out, "models/item/power_core.json", _core_model("power_core"))
    _write_json(out, "items/power_core.json", {"model": {
        "type": "minecraft:select", "property": "awaken:core_power",
        "cases": [{"when": p, "model": {"type": "minecraft:model", "model": "awaken:item/power_core/" + p}} for p in POWERS],
        "fallback": {"type": "minecraft:model", "model": "awaken:item/power_core"},
    }})
    for age in range(4):
        out.block(f"construct_{age}", construct("signal", age, 3))
        out.block(f"dream_construct_{age}", construct("dream", age, 5))
    out._save("mob_effect/stunned", stunned())
