#!/usr/bin/env python3
"""The facing tours and their contact sheets (Euan 2026-11-11, the Bell Warden's Toll: "facing the wrong way and hitting
with the wrong end, let's check all special weapons for this"; SPEC §25 "One-faced heads").

Every weapon at the hit in first person (the pose just before the hit and on it, so the way it travels shows) and
mid-combo in third person (strike 1 into strike 2, filmed at a tenth of play's speed, from behind its right shoulder),
so a sheet shows the part that meets the husk: a blade's edge, an axe's bit, a hammer's face, a point. Each style's
forged (C) weapon and the Toll play every move in first person. The forged picks dig (a pick's hold is one per form,
every tier the same). Pictures are filmed small (arms film, a third of the frame) so five tours fit one fetch.

    python3 tools/art/facing_tour.py                      writes tools/autopilot/arms-facing-{a..e}.txt
    tools/tour-ci.sh tools/autopilot/arms-facing-a.txt 3600
    gh run download <run> -R euansprr-dev/awaken -n tour-arms-facing-a-clips -D run-autopilot/clips-ci/facing-a
    python3 tools/art/facing_tour.py sheet OUT_DIR run-autopilot/clips-ci/facing-a ...
"""
import csv
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path[:0] = [HERE, os.path.join(HERE, "specs")]
ROOT = os.path.dirname(os.path.dirname(HERE))
TOURS = os.path.join(ROOT, "tools", "autopilot")
SWINGS = os.path.join(ROOT, "src/main/resources/data/awaken/arms/fp_swings.json")

GROUPS = {
    "a": ["longsword", "axe", "maul", "spear", "hunter", "vanilla"],
    "b": ["greatblade", "rapier", "daggers", "katana"],
    "c": ["twinblades", "glaive", "scythe", "gauntlets"],
    "d": ["chainblade", "shieldblade", "warstaff", "precursor"],
    "e": ["picks"],
}
HUNTER = [t + "_blade" for t in ("wildhide", "beastbound", "gatewrought", "veilwoven", "lordscale", "sovereign")]
VANILLA = {"minecraft:iron_sword": "longsword", "minecraft:iron_axe": "axe", "minecraft:mace": "maul"}
PICKS = ["delver", "breaker", "mattock", "sounder"]
ALL_MOVES = ["strike_1", "strike_2", "strike_3", "strike_4", "heavy", "riposte", "slipcut", "undercut", "art", "plunge",
             "execution"]
MAUL_MOVES = ["strike_1", "strike_2", "strike_4", "riposte", "slipcut", "undercut"]

HEAD = """# {title}
# Written by tools/art/facing_tour.py (SPEC §25 "One-faced heads"): {what}
# ci knobs: PACK=perf,feel,comfort,world
world armsfacing{g} creative 778
waitworld
wait 60
cmd awaken opening skip
wait 40
cmd awaken powers starter 1
wait 10
screen close
gui 0
cmd gamerule send_command_feedback false
cmd gamerule spawn_mobs false
cmd gamerule awaken:fauna_spawning false
cmd gamerule advance_time false
cmd weather clear 1000000
cmd time set 6000
window 1280 720
hud on
cmd gamemode creative
wait 60
screen close
"""


def styles():
    import arms_weapons as AW
    by = {}
    for r in AW.CATALOG:
        by.setdefault(r[8], []).append(r[0])
    return by


def timing():
    """{style: {move: (hit tick, total ticks)}} as the game's hands play them: a hand-keyed move's own hit (the clip
    tick its blow lands), else the lab's (the first active tick)."""
    import anim_lab as L
    baked = json.load(open(SWINGS))["styles"]
    out = {}
    for sid, st in L.table().items():
        out[sid] = {}
        for mv, m in st["moves"].items():
            if "swing" not in m:
                continue
            total = m["windup"] + m["active"] + m["recovery"]
            hit = baked.get(sid, {}).get("moves", {}).get(mv, {}).get("hit", L.hit_tick(m))
            out[sid][mv] = (float(hit), float(total), m.get("cadence"))
    return out


def fp_lines(item, name, sid, moves, tim, turns=None):
    """First person: the weapon posed a tick before each move's hit and on it, each a one-picture film."""
    out = [f"cmd item replace entity @s weapon.mainhand with {item}", "wait 30"]
    for mv in moves:
        if mv not in tim[sid]:
            continue
        hit, total, _ = tim[sid][mv]
        marks = [("a", max(0.0, hit - 1.0)), ("b", hit)]
        if turns and mv in turns:  # a one-faced head half way through its turn into the blow
            marks.insert(0, ("t", turns[mv]))
        for tag, t in marks:
            out += [f"arms pose {mv} {t / total:.4f}", "wait 4", f"arms film fa_{name}_{mv}_{tag} 1 1 3", "wait 2"]
    out.append("arms pose off")
    return out


def tp_lines(item, name, sid, tim):
    """Third person: strike 1 into strike 2 (the chain's window a tick in), filmed at the world's tenth."""
    hit2 = tim[sid]["strike_2"][0]
    cad = tim[sid]["strike_1"][2] or [6, 9]
    gap = int(cad[0]) + 1
    ticks = 2 + gap + int(hit2) + 8
    return [f"cmd item replace entity @s weapon.mainhand with {item}", "wait 30",
            "cmd tp @e[type=minecraft:husk] 0.5 200 3.5 180 0", "arms waitserver 10", "arms rate 2", "wait 2",
            f"arms film fb_{name} {ticks} 0.5 3", "wait 1", "arms strike 2", f"wait {gap}", "arms strike 2",
            f"wait {ticks - gap}", "arms rate 20", "wait 10"]


def turn_marks():
    """The Toll's turned moves: the tick its turn in the fist is half way (first person's turn curve)."""
    baked = json.load(open(SWINGS))["styles"]["maul"]["moves"]
    out = {}
    for mv, row in baked.items():
        tn = row.get("turn")
        if tn:
            whole = next(k for k, v in enumerate(tn) if abs(v) > 0.999)
            i = min(range(whole), key=lambda k: abs(abs(tn[k]) - 0.5))
            out[mv] = row["keys"][i][0]
    return out


def weapons(group, by):
    """[(item, name, style, first-person moves, turns)] for a group's styles."""
    out = []
    turns = turn_marks()
    for sid in GROUPS[group]:
        if sid == "hunter":
            out += [(f"awaken:{w}", w, "longsword", ["strike_1", "strike_2"], None) for w in HUNTER]
            continue
        if sid == "vanilla":
            out += [(i, i.split(":")[1], s, MAUL_MOVES if s == "maul" else ["strike_1", "strike_2"], None) for i, s in VANILLA.items()]
            continue
        for w in by[sid]:
            if w == "bell_warden_maul":
                moves, t = ALL_MOVES, turns
            elif w.startswith("gatewrought_"):
                moves, t = ALL_MOVES, None
            elif sid == "maul":
                moves, t = MAUL_MOVES, None
            else:
                moves, t = ["strike_1", "strike_2"], None
            out.append((f"awaken:{w}", w, sid, moves, t))
    return out


def picks_tour():
    lines = HEAD.format(title="Facing, e: the forged picks dig", g="e",
                        what="each pick's form held and digging stone (the Mattock earth too), first person and from behind").splitlines()
    lines += ["cmd tp @s 0.5 201 0.5 0 0", "cmd forceload add -16 -16 16 16", "wait 300",
              "cmd fill -8 200 -6 8 200 9 minecraft:polished_deepslate", "cmd fill -8 201 -6 8 206 8 minecraft:air",
              "cmd fill -8 201 9 8 205 9 minecraft:stone", "cmd fill 2 201 8 7 204 8 minecraft:dirt",
              "cmd fill -7 201 8 -2 204 8 minecraft:stone", "wait 20"]
    for cam in ("first", "back"):
        lines += [f"camera {cam}", "wait 4"]
        for form in PICKS:
            for ground, x in (("stone", -4.5), ("dirt", 4.5)):
                if ground == "dirt" and form != "mattock":
                    continue
                name = f"fp_{form}_{ground}_{'1p' if cam == 'first' else '3p'}"
                lines += [f"cmd tp @s {x} 201 6.5 0 12", f"cmd item replace entity @s weapon.mainhand with awaken:gatewrought_pick_{form}",
                          "wait 30", f"arms film {name}_rest 1 1 3", "wait 3", f"arms film {name} 16 0.5 3", "wait 1",
                          "cmd awaken delve demo strike", "wait 17"]
    lines.append("quit")
    return lines


def write():
    by = styles()
    tim = timing()
    out = []
    for g in GROUPS:
        if g == "e":
            lines = picks_tour()
        else:
            ws = weapons(g, by)
            first = ws[0][0]
            lines = HEAD.format(title=f"Facing, {g}: " + ", ".join(GROUPS[g]), g=g,
                                what="every weapon a tick before the hit and on it in first person, then strike 1 into"
                                     " strike 2 in third person from behind its right shoulder").splitlines()
            lines += ["arms waitserver 40", f"arms studio {first}", "arms waitserver 100", "camera first", "look 0 6", "wait 20"]
            for item, name, sid, moves, turns in ws:
                lines += fp_lines(item, name, sid, moves, tim, turns)
            lines += ["camera back", "cmd awaken fx sidecam 35", "look 0 6", "wait 20"]
            for item, name, sid, moves, turns in ws:
                lines += tp_lines(item, name, sid, tim)
            lines += ["cmd awaken fx sidecam 0", "quit"]
        path = os.path.join(TOURS, f"arms-facing-{g}.txt")
        with open(path, "w") as f:
            f.write("\n".join(lines) + "\n")
        out.append(path)
    return out


# ---- the sheets ------------------------------------------------------------------------------------------------------

CELL = (400, 225)


def _pic(clips, name, i=0):
    from PIL import Image
    p = os.path.join(clips, name, f"f{i:05d}.png")
    return Image.open(p).convert("RGB").resize(CELL) if os.path.exists(p) else None


def _near(clips, name, move, t):
    """The picture of a film nearest a move's clock reading t (track.tsv), and that reading."""
    path = os.path.join(clips, name, "track.tsv")
    if not os.path.exists(path):
        return None, None
    best = None
    with open(path) as f:
        for row in csv.DictReader(f, delimiter="\t"):
            if row["pic"] == "-1" or row["move"] != move:
                continue
            d = abs(float(row["view"]) - t)
            if best is None or d < best[0]:
                best = (d, int(row["pic"]), float(row["view"]))
    return (None, None) if best is None else (_pic(clips, name, best[1]), best[2])


def sheet(out_dir, clip_dirs):
    """Contact sheets: one a style (each weapon a row: first person strike 1 and 2 before and on the hit, third person
    the same), one for each C weapon and the Toll (every move), one for the picks."""
    from PIL import Image, ImageDraw
    by = styles()
    tim = timing()
    os.makedirs(out_dir, exist_ok=True)
    have = {}
    for d in clip_dirs:
        for n in os.listdir(d):
            have[n] = d
    made = []

    def board(rows, heads, title, path):
        W, H, LW = CELL[0], CELL[1], 260
        img = Image.new("RGB", (LW + W * len(heads), 40 + 30 + H * len(rows)), (24, 24, 28))
        dr = ImageDraw.Draw(img)
        dr.text((10, 10), title, fill=(240, 240, 240))
        for j, h in enumerate(heads):
            dr.text((LW + j * W + 8, 48), h, fill=(200, 200, 210))
        for i, (label, cells) in enumerate(rows):
            y = 70 + i * H
            dr.text((10, y + H // 2), label, fill=(230, 230, 230))
            for j, c in enumerate(cells):
                if c is not None:
                    img.paste(c[0] if isinstance(c, tuple) else c, (LW + j * W, y))
                    if isinstance(c, tuple) and c[1] is not None:
                        dr.text((LW + j * W + 6, y + 6), f"t {c[1]:.2f}", fill=(255, 255, 0))
        img.save(path)
        made.append(path)

    # one sheet a style, the Hunter blades and vanilla's with the Longsword's, Axe's and Maul's
    rows_by = {}
    for g in GROUPS:
        if g == "e":
            continue
        for item, name, sid, moves, turns in weapons(g, by):
            rows_by.setdefault(sid, []).append((item, name, sid, moves, turns))
    heads = ["1P s1 hit-1", "1P s1 hit", "1P s2 hit-1", "1P s2 hit", "3P s1 hit-.5", "3P s1 hit+.5", "3P s2 hit-.5", "3P s2 hit+.5"]
    for sid, ws in rows_by.items():
        rows = []
        for item, name, _, moves, turns in ws:
            cells = []
            for mv in ("strike_1", "strike_2"):
                for tag in ("a", "b"):
                    n = f"fa_{name}_{mv}_{tag}"
                    cells.append(_pic(have[n], n) if n in have else None)
            n = f"fb_{name}"
            for mv in ("strike_1", "strike_2"):
                hit = tim[sid][mv][0]
                for dt in (-0.5, 0.5):
                    cells.append(_near(have[n], n, mv, hit + dt) if n in have else None)
            rows.append((name, cells))
        board(rows, heads, f"{sid}: first person posed, third person filmed (strike 1 into 2)", os.path.join(out_dir, f"facing_{sid}.png"))
    # every move of each C weapon, the Toll's with its turn half way
    for sid, ws in rows_by.items():
        for item, name, _, moves, turns in ws:
            if len(moves) <= 2:
                continue
            rows = []
            for mv in moves:
                tags = (["t"] if turns and mv in turns else []) + ["a", "b"]
                cells = [_pic(have[f"fa_{name}_{mv}_{t}"], f"fa_{name}_{mv}_{t}") if f"fa_{name}_{mv}_{t}" in have else None
                         for t in tags]
                if len(tags) == 2:
                    cells = [None] + cells
                rows.append((mv, cells))
            board(rows, ["turn half way", "hit-1", "hit"], f"{name}: every move in first person", os.path.join(out_dir, f"moves_{name}.png"))
    # the picks
    rows = []
    for form in PICKS:
        for ground in ("stone", "dirt"):
            if ground == "dirt" and form != "mattock":
                continue
            for v in ("1p", "3p"):
                n = f"fp_{form}_{ground}_{v}"
                cells = [_pic(have[n + "_rest"], n + "_rest") if n + "_rest" in have else None]
                if n in have:
                    k = len([f for f in os.listdir(os.path.join(have[n], n)) if f.endswith(".png")])
                    cells += [_pic(have[n], n, i) for i in range(0, min(k, 24), 4)]
                rows.append((f"{form} {ground} {v}", cells))
    if any(c for _, cs in rows for c in cs):
        board(rows, ["rest"] + [f"dig {i / 2:.0f}" for i in range(0, 24, 4)], "the forged picks (gatewrought; every tier holds alike)",
              os.path.join(out_dir, "facing_picks.png"))
    return made


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "sheet":
        for p in sheet(sys.argv[2], sys.argv[3:]):
            print(p)
    else:
        for p in write():
            print(p)
