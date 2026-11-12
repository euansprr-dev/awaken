#!/usr/bin/env python3
"""The pick hold tour and its sheets (Euan 2026-11-12: "the custom picks are being held the wrong way, the animation
isn't even hitting, it's just wobbling, and overall it needs to match vanilla animations and angle"; SPEC §49.8).

Vanilla's iron pickaxe, then each forged form (and the Lord-forged Delver), held at rest and digging stone (the Mattock
earth too, adze first), in first person and from behind the right shoulder. Each is filmed at rest, then through the
strike demo's first blows at a picture every half tick, so the sheet shows the hold beside vanilla's and the head coming
down onto the block.

    python3 tools/art/pick_hold_tour.py                       writes tools/autopilot/pick-hold.txt
    tools/tour-ci.sh tools/autopilot/pick-hold.txt 2400
    python3 tools/art/pick_hold_tour.py sheet OUT_DIR run-autopilot/clips-ci/pick-hold
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path[:0] = [HERE]
import facing_tour as FT  # noqa: E402

TOUR = os.path.join(FT.TOURS, "pick-hold.txt")
# (shot name, item, ground)
ITEMS = [("vanilla", "minecraft:iron_pickaxe", "stone"),
         ("delver", "awaken:gatewrought_pick_delver", "stone"),
         ("breaker", "awaken:gatewrought_pick_breaker", "stone"),
         ("mattock", "awaken:gatewrought_pick_mattock", "stone"),
         ("mattock_dirt", "awaken:gatewrought_pick_mattock", "dirt"),
         ("sounder", "awaken:gatewrought_pick_sounder", "stone"),
         ("lord_delver", "awaken:lordscale_pick_delver", "stone")]
STRIKE = 12  # ticks of the demo filmed: its first three blows
GAP = 0.5


def write():
    head = FT.HEAD.replace("world armsfacing{g}", "world pickhold").replace("SPEC §25 \"One-faced heads\"", "SPEC §49.8")
    lines = head.format(title="Pick hold: vanilla's iron pickaxe beside the forged picks", g="",
                        what="each held at rest and digging, first person and from behind the right shoulder").splitlines()
    lines = [l.replace("facing_tour.py", "pick_hold_tour.py") for l in lines]
    # the arena: a stone wall, stone and dirt faces in front of it. The runner's server runs far behind while the land
    # round spawn is made (35 s on 12 Nov), so every command is waited for on its ticks (arms waitserver), and the
    # client, which draws fills slowly, gets its own wait after
    lines += ["cmd tp @s 0.5 201 0.5 0 0", "cmd forceload add -16 -16 16 16", "arms waitserver 100", "wait 100",
              "cmd fill -8 200 -6 8 200 9 minecraft:polished_deepslate", "cmd fill -8 201 -6 8 206 8 minecraft:air",
              "cmd fill -8 201 9 8 205 9 minecraft:stone", "cmd fill 2 201 8 7 204 8 minecraft:dirt",
              "cmd fill -7 201 8 -2 204 8 minecraft:stone", "arms waitserver 40", "wait 200"]
    for cam in ("first", "back"):
        lines += [f"camera {cam}", f"cmd awaken fx sidecam {0 if cam == 'first' else 35}", f"opt fov {70 if cam == 'first' else 50}", "wait 4"]
        v = "1p" if cam == "first" else "3p"
        for name, item, ground in ITEMS:
            x = 4.5 if ground == "dirt" else -4.5
            n = f"ph_{name}_{v}"
            # the demo's blows start when the server's answer lands, a tick or two after it has run the command
            lines += [f"cmd tp @s {x} 201 6.5 0 12", f"cmd item replace entity @s weapon.mainhand with {item}", "arms waitserver 10",
                      "wait 40", f"arms film {n}_rest {FT.FILM} 2 3", f"wait {FT.FILM + 1}",
                      "cmd awaken delve demo strike", "arms waitserver 2", f"arms film {n} {STRIKE} {GAP} 3", "wait 24"]
    lines += ["cmd awaken fx sidecam 0", "opt fov 70", "quit"]
    with open(TOUR, "w") as f:
        f.write("\n".join(lines) + "\n")
    return TOUR


def _times(clips, n):
    p = os.path.join(clips, n, "times.txt")
    if not os.path.exists(p):
        return []
    with open(p) as f:
        return [l.split()[-1] for l in f if l.strip()]


def sheet(out_dir, clip_dirs, cols=12):
    """Per view a sheet: a row an item, its rest picture then the strike film's first pictures, each with its time."""
    from PIL import Image, ImageDraw
    os.makedirs(out_dir, exist_ok=True)
    have = {}
    for d in clip_dirs:
        for n in os.listdir(d):
            have[n] = d
    W, H = FT.CELL
    made = []
    for v, crop in (("1p", None), ("3p", FT.CROP3)):
        img = Image.new("RGB", (220 + W * (cols + 1), 70 + H * len(ITEMS)), (24, 24, 28))
        dr = ImageDraw.Draw(img)
        dr.text((10, 10), f"Pick hold, {'first person' if v == '1p' else 'third person'}: rest, then the dig (a picture every {GAP} tick)", fill=(240, 240, 240))
        for i, (name, item, ground) in enumerate(ITEMS):
            y = 70 + i * H
            dr.text((10, y + H // 2), f"{name}\n{item.split(':')[1]}\n{ground}", fill=(230, 230, 230))
            n = f"ph_{name}_{v}"
            rest = FT._pic(have[n + "_rest"], n + "_rest", 0, crop) if n + "_rest" in have else None
            if rest is not None:
                img.paste(rest, (220, y))
                dr.text((226, y + 6), "rest", fill=(255, 255, 0))
            if n not in have:
                continue
            ts = _times(have[n], n)
            for j in range(cols):
                c = FT._pic(have[n], n, j, crop)
                if c is None:
                    break
                img.paste(c, (220 + W * (j + 1), y))
                if j < len(ts):
                    dr.text((226 + W * (j + 1), y + 6), ts[j], fill=(255, 255, 0))
        path = os.path.join(out_dir, f"pick_hold_{v}.png")
        img.save(path)
        made.append(path)
    return made


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "sheet":
        for p in sheet(sys.argv[2], sys.argv[3:]):
            print(p)
    else:
        print(write())
