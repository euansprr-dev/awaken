"""The way each cut really travels on the screen at its hit (SPEC §25 "Weight in the Hand", stage 1): written into
data/awaken/arms/archetypes.json's swing rows as `blow` [x, y] (a unit; x to the right, y up), read from the blade the
baked first-person keys draw. The view's kick on a hit, the side a struck body is thrown to, the turn of its recoil and the
way a kill falls all follow this one number (ArmsRules.side, ArmsPredict.impact), so a blow answers the way the blade went.

Before this the kick and the knockback read the row's old `from`/`to` hand poses, which 17 of the 143 cuts no longer matched:
the Axe's fourth strike, the Maul's second and the War-staff's fourth threw a body the opposite way from the blade, and the
Twin Blades' rows still described the figure-eight their spins replaced.

A move struck by the off hand is mirrored on the screen, one struck by both is the two together; a hand-keyed move
(tools/art/arms_keys.py) keeps the direction its own bake worked out (its `kick`). A thrust has none: it pushes straight
away and nods the view.

    python3 tools/arms/blows.py            writes every cut's blow into the table
    python3 tools/arms/blows.py check      fails if a row's blow is more than 20 degrees off the baked blade (after any re-bake)
    python3 tools/arms/blows.py report     what the old from/to got wrong, without writing
"""
import json
import math
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/art"))
import numpy as np  # noqa: E402
import anim_lab as L  # noqa: E402
import swing_studio as S  # noqa: E402

TABLE = ROOT / "src/main/resources/data/awaken/arms/archetypes.json"
THRUSTS = ("stab", "jab", "bash")
NOD = 0.05  # a keyed kick with less turn than this is a nod: a thrust, or a pair cutting out both ways


def thrusts(m):
    sw = m.get("swing", {})
    return sw.get("form", "") in THRUSTS or m.get("swish") == "thrust"


def blade_way(sid, mv, m, clip, swings):
    """The blade's middle on the screen over the half tick round the hit, as the eye sees it for the hand that strikes."""
    rs = S.rows(sid, mv, swings)
    hit = clip.get("hit", L.hit_tick(m))
    ts = np.array([r[0] for r in rs])
    mid = np.array([[r[2][0] * S.ASP, r[2][1]] for r in rs])  # square units: a degree across is a degree up
    a = np.array([np.interp(hit - 0.25, ts, mid[:, 0]), np.interp(hit - 0.25, ts, mid[:, 1])])
    b = np.array([np.interp(hit + 0.25, ts, mid[:, 0]), np.interp(hit + 0.25, ts, mid[:, 1])])
    v = b - a
    hand = clip.get("hand", "main")
    if hand == "off":
        v[0] = -v[0]
    elif hand == "both":
        v[0] = 0.0
    return v


def blow_of(sid, mv, m, clip, swings):
    """[x, y] for a cut, None for a thrust or a move with no travel across the screen."""
    if thrusts(m):
        return None
    kick = clip.get("kick")
    if kick is not None:  # hand-keyed: its own bake's word (+yaw right, +pitch down)
        if abs(kick[0]) < NOD and abs(kick[1]) > 0:
            return None
        v = np.array([kick[0], -kick[1]])
    else:
        v = blade_way(sid, mv, m, clip, swings)
    n = float(np.linalg.norm(v))
    if n < 0.02:
        return None
    return [round(float(v[0] / n), 2), round(float(v[1] / n), 2)]


def side(x, y):
    return x / max(0.05, abs(x) + abs(y))


def angle(a, b):
    c = (a[0] * b[0] + a[1] * b[1]) / max(1e-9, math.hypot(*a) * math.hypot(*b))
    return math.degrees(math.acos(max(-1.0, min(1.0, c))))


def main(argv):
    mode = argv[0] if argv else "write"
    swings = L.load_swings()
    d = json.loads(TABLE.read_text())
    cuts = flips = offs = stale = 0
    for st in d["archetypes"]:
        sid = st["id"]
        for mv, m in st["moves"].items():
            clip = swings.get(sid, {}).get("moves", {}).get(mv)
            sw = m.get("swing")
            if not clip or not sw:
                continue
            blow = blow_of(sid, mv, m, clip, swings)
            if blow is None:
                if mode == "write":
                    sw.pop("blow", None)
                continue
            cuts += 1
            row = [sw["to"][0] - sw["from"][0], sw["to"][1] - sw["from"][1]]
            old_side, new_side = side(*row), side(*blow)
            flipped = old_side * new_side < -0.04
            off = angle(row, blow) if math.hypot(*row) > 1e-6 else 0.0
            flips += flipped
            offs += off > 45 or flipped
            if mode == "report" and (off > 45 or flipped):
                print(f"  {sid:11s} {mv:10s} {sw.get('form', ''):6s} old from/to {off:3.0f} deg off the blade; side {old_side:+.2f} -> {new_side:+.2f}"
                      f"{'  THROWN THE OTHER WAY' if flipped else ''}")
            if mode == "check":
                have = sw.get("blow")
                if have is None or angle(have, blow) > 20:
                    stale += 1
                    print(f"  {sid} {mv}: the table says {have}, the baked blade {blow}")
            if mode == "write":
                sw["blow"] = blow
    if mode == "write":
        TABLE.write_text(json.dumps(d, indent=2) + "\n")
        print(f"{cuts} cuts written")
    elif mode == "check":
        print(f"{cuts} cuts, {stale} stale")
        return 1 if stale else 0
    else:
        print(f"{cuts} cuts; the old from/to was over 45 degrees off or thrown the other way on {offs}, thrown the other way on {flips}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
