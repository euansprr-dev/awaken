"""Gate Worlds: the air's own sprites, shared by every world (SPEC §38.9 "Air").

Tinted by the client per world: a soft wisp (mist on water, smoke off candles, spore clouds, steam), a falling streak
(rain, and with a short tail sparks or ash), a flake (snow, ash, petals, paper scraps, sand), a glowing orb with a halo
(ghost lights, fireflies, lantern spirits, glowing spores) and a soft flash (lightning through a window).

    python3 tools/art/build_all.py gate_worlds_air
    python3 tools/art/specs/gate_worlds_air.py content
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")


def _soft(P, S, edge, seed, lumpy=0.0, core=1.0):
    """A soft round puff: white, alpha falling off to the edge, a little lumpy so it isn't a perfect disc."""
    n = P.value_noise(S, S, P.rng_for("air", seed), scale=3, octaves=2)
    c = P.Canvas(S, S)
    h = (S - 1) / 2
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - h, y - h) / h
            d += (n[y][x] - 0.5) * lumpy
            a = max(0.0, 1 - d) ** edge * core
            if a > 0.02:
                c.set(x, y, (255, 255, 255, int(255 * min(1, a))))
    return c


def sprites(P):
    s = {}
    s["gate_air_wisp_0"] = _soft(P, 16, 1.6, "wisp0", lumpy=0.5, core=0.8)
    s["gate_air_wisp_1"] = _soft(P, 16, 1.4, "wisp1", lumpy=0.7, core=0.75)
    s["gate_air_wisp_2"] = _soft(P, 16, 2.0, "wisp2", lumpy=0.4, core=0.85)
    # a thin streak, brightest at its head
    c = P.Canvas(16, 16)
    for y in range(16):
        a = int(70 + 150 * y / 15)
        c.set(7, y, (255, 255, 255, a))
        c.set(8, y, (255, 255, 255, a // 2))
    s["gate_air_streak"] = c
    # flakes: a soft dot, a curled scrap, a speck
    s["gate_air_flake_0"] = _soft(P, 8, 1.0, "flake0", core=1.0)
    c = P.Canvas(8, 8)
    for x, y in ((2, 3), (3, 2), (4, 2), (5, 3), (5, 4), (4, 5), (3, 4)):
        c.set(x, y, (255, 255, 255, 230))
    c.set(4, 3, (255, 255, 255, 140))
    s["gate_air_flake_1"] = c
    c = P.Canvas(8, 8)
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)):
        c.set(x, y, (255, 255, 255, 255))
    s["gate_air_flake_2"] = c
    # an orb: a bright core, a halo, a faint ring
    c = P.Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            a = 255 if d < 1.6 else int(255 * max(0.0, 1 - (d - 1.6) / 6.0) ** 2.2)
            if 5.6 < d < 6.6:
                a = max(a, 40)
            if a > 4:
                c.set(x, y, (255, 255, 255, a))
    s["gate_air_orb"] = c
    s["gate_air_flash"] = _soft(P, 16, 1.2, "flash", core=1.0)
    return s


PARTICLES = {
    "gate_air_wisp": ["gate_air_wisp_0", "gate_air_wisp_1", "gate_air_wisp_2"],
    "gate_air_streak": ["gate_air_streak"],
    "gate_air_flake": ["gate_air_flake_0", "gate_air_flake_1", "gate_air_flake_2"],
    "gate_air_orb": ["gate_air_orb"],
    "gate_air_flash": ["gate_air_flash"],
}


def build(out):
    import pixelart as P
    for name, c in sprites(P).items():
        out.particle(name, c)


def content():
    for name, tex in PARTICLES.items():
        path = os.path.join(ASSETS, f"particles/{name}.json")
        with open(path, "w") as f:
            json.dump({"textures": [f"awaken:{t}" for t in tex]}, f, indent=2)
            f.write("\n")
    print("gate_worlds_air: content written for", len(PARTICLES), "particles")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
    else:
        print(__doc__)
