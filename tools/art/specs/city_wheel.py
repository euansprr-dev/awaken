"""The Works mills' turning water wheels (Milestone 4 step B3a, "movement for free": SPEC decision 27). The wheel's
middle plane is drawn as one picture (13 x 15 blocks at 16 px a block) and cut into cells; each cell is a cutout plane
with an animated texture, so the spokes and the paddles turn while nothing ticks. The wheel has twelve spokes and twelve
paddles, so eight frames turn it through 30 degrees and the loop is seamless; the outer rim planes (in front and behind)
stay painted blocks and are rings, so they need not turn.

    python3 tools/art/build_all.py city_wheel             textures (wheel_face_<cell>.png + .mcmeta)
    python3 tools/art/specs/city_wheel.py content         blockstate and models (awaken:wheel_face, cell 0..194)

Cell index = (dx + 6) + (dy + 7) * 13, dx east of the axle (-6..6), dy up from it (-7..7); the painter
(CityLandmarks.wheel) places the cells whose middle lies within 6.9 of the axle.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from pixelart import Canvas, rng_for  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/awaken")

W, H = 13, 15            # cells across, cells up
PX = 16
CX, CY = 6 * PX + 8, 7 * PX + 8   # the axle, in image pixels (y down)
FRAMES = 8
STEP = math.radians(30.0) / FRAMES
FRAMETIME = 3            # 24 ticks a twelfth of a turn: a slow mill wheel, twelve seconds a turn

# dark oak in vanilla's register (dark_oak_planks averages about (66, 43, 20)), wet and darker at the rim
OAK = [(34, 22, 11), (48, 31, 15), (62, 41, 20), (78, 53, 27), (97, 67, 35)]
IRON = [(38, 38, 42), (56, 56, 61), (78, 78, 84), (104, 104, 110)]
MOSS = (58, 74, 38)

R_HUB, R_HUB_RING = 15.0, 19.0
R_RIM0, R_RIM1 = 74.0, 104.0      # the rim's band (4.6 to 6.5 blocks; the outer planes' ring covers it past 5.4)
SPOKE_W = 2.6                     # half width of a spoke, px
PADDLE_W = 3.0                    # half width of a paddle, px
N = 12


def ang_dist(a, b):
    d = (a - b) % (2 * math.pi)
    return min(d, 2 * math.pi - d)


def sample(x, y, rot, grain):
    """The wheel's colour at image pixel (x, y) (centre of the pixel), turned by rot; None where open."""
    dx, dy = x + 0.5 - CX, -(y + 0.5 - CY)
    r = math.hypot(dx, dy)
    if r > R_RIM1 + 0.5:
        return None
    th = math.atan2(dy, dx) - rot
    # the hub: an iron boss with a ring
    if r < R_HUB:
        light = 0.5 + 0.5 * (-(dx + dy) / max(r, 1.0)) * min(1.0, r / R_HUB)
        i = 1 + int(light * 2.2)
        return IRON[max(0, min(3, i))]
    if r < R_HUB_RING:
        return IRON[0]
    # paddles across the rim (between the spokes), boards with a dark edge
    for k in range(N):
        a = (k + 0.5) * 2 * math.pi / N
        lat = r * math.sin(ang_dist(th, a))
        if ang_dist(th, a) < math.pi / 2 and lat < PADDLE_W and R_RIM0 - 10 <= r <= R_RIM1:
            edge = lat > PADDLE_W - 1.0
            g = grain[(int(r) * 7 + k * 13) % len(grain)]
            return OAK[0] if edge else OAK[max(1, min(4, 2 + g))]
    # the rim: two felloes of planks with joints every 30 degrees, a wet lower half
    if R_RIM0 <= r <= R_RIM1:
        t = (r - R_RIM0) / (R_RIM1 - R_RIM0)
        joint = min(ang_dist(th, (k + 0.25) * 2 * math.pi / N) for k in range(N)) * r < 0.8
        if joint or t < 0.08 or t > 0.92:
            c = OAK[0]
        else:
            g = grain[(int(th * 40) % 17 + int(r)) % len(grain)]
            c = OAK[max(1, min(4, 2 + g + (1 if t < 0.35 else 0)))]
        # the water's side: darker, a little weed
        world_down = dy < -R_RIM0 * 0.55
        if world_down:
            c = tuple(max(0, int(v * 0.78)) for v in c)
            if grain[(int(th * 90) + int(r)) % len(grain)] > 1:
                c = MOSS
        return c
    # the spokes: square-cut timbers from the hub to the rim, lit on the side toward the light
    for k in range(N):
        a = k * 2 * math.pi / N
        if ang_dist(th, a) > math.pi / 2:
            continue
        lat = r * math.sin(th - a)
        if abs(lat) < SPOKE_W:
            return OAK[4] if lat > 1.4 else OAK[3] if lat > 0.0 else OAK[2] if lat > -1.4 else OAK[0]
    return None


def frames():
    """The whole wheel, frame by frame (lists of rows of RGBA or None)."""
    rng = rng_for("city_wheel", "grain")
    grain = [rng.choice([-1, 0, 0, 0, 1]) for _ in range(97)]
    out = []
    for f in range(FRAMES):
        rot = f * STEP
        img = [[sample(x, y, rot, grain) for x in range(W * PX)] for y in range(H * PX)]
        out.append(img)
    return out


def cells():
    """{cell index: [Canvas per frame]} for every cell with anything in it in any frame."""
    fr = frames()
    out = {}
    for cy in range(H):
        for cx in range(W):
            canv = []
            any_px = False
            for img in fr:
                c = Canvas()
                for y in range(PX):
                    for x in range(PX):
                        p = img[cy * PX + y][cx * PX + x]
                        if p is not None:
                            c.set(x, y, (*p, 255))
                            any_px = True
                canv.append(c)
            if any_px:
                dx, dy = cx - 6, 6 - cy + 1   # image row 0 is the top (dy +7)
                out[(dx + 6) + (dy + 7) * W] = canv
    return out


def build(out):
    for idx, fr in cells().items():
        name = f"wheel_face_{idx}"
        out.block(name, fr, FRAMETIME)
        path = os.path.join(ASSETS, "textures/block", name + ".png.mcmeta")
        with open(path, "w") as m:
            json.dump({"animation": {"frametime": FRAMETIME, "interpolate": False}}, m)


def content():
    have = set(cells().keys())
    variants = {}
    os.makedirs(os.path.join(ASSETS, "models/block"), exist_ok=True)
    empty = {"parent": "minecraft:block/block", "textures": {"particle": "minecraft:block/dark_oak_planks"}, "elements": []}
    with open(os.path.join(ASSETS, "models/block/wheel_face_empty.json"), "w") as f:
        json.dump(empty, f, indent=2)
    for idx in range(W * H):
        if idx in have:
            tex = f"awaken:block/wheel_face_{idx}"
            m = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                 "textures": {"particle": tex, "face": tex},
                 "elements": [{"from": [0, 0, 8], "to": [16, 16, 8],
                               "faces": {"north": {"uv": [16, 0, 0, 16], "texture": "#face"},
                                         "south": {"uv": [0, 0, 16, 16], "texture": "#face"}}}]}
            with open(os.path.join(ASSETS, f"models/block/wheel_face_{idx}.json"), "w") as f:
                json.dump(m, f, indent=2)
            variants[f"cell={idx}"] = {"model": f"awaken:block/wheel_face_{idx}"}
        else:
            variants[f"cell={idx}"] = {"model": "awaken:block/wheel_face_empty"}
    with open(os.path.join(ASSETS, "blockstates/wheel_face.json"), "w") as f:
        json.dump({"variants": variants}, f, indent=2)
    print(f"wheel_face: {len(have)} cells with art of {W * H}")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "content":
        content()
