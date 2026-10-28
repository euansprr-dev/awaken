"""The Creature Lab (SPEC §34.5): one sheet per kit body to judge it by eye, before anyone sees it in game.

    python3 tools/art/specs/kit_bodies.py lab DIR [ids...]

A sheet has four views (three by day, one by night with only its light), the body beside a player on one ground line,
and every move as a strip: rest, the wind-up's peak, the hold, contact, the follow-through and the settle, each with the
move's hit capsules drawn over contact. The checks (kit.core.check) run in the build and stop it; the sheet repeats
them in red so a failing body is never only a log line.
"""
import math
import os

from kit.core import capsules, check, faces
import fauna_rig as R

BG_DAY = (156, 172, 190, 255)
BG_NIGHT = (14, 16, 24, 255)


def _lit(skin, glow):
    from PIL import Image
    return Image.alpha_composite(skin.image(), glow.image())


def _cam(yaw, pitch):
    ya, pa = math.radians(yaw), math.radians(pitch)
    return R._mm(R._rot(pa, 0, 0), R._rot(0, ya, 0))


def sheet(outdir, d, skin, glow):
    from PIL import Image, ImageDraw
    rig, mv = d["rig"], d["moves"]
    sk, gl = skin.image(), glow.image()
    lit = Image.alpha_composite(sk, gl)
    rest = rig.tree(rig.sample([("idle", 0.4, 1.0)]))
    cell = (320, 300)
    views = [R.render(rest, lit, yaw=35, pitch=14, size=cell), R.render(rest, lit, yaw=90, pitch=6, size=cell),
             R.render(rest, lit, yaw=-150, pitch=24, size=cell), R.render(rest, sk, yaw=25, pitch=10, size=cell, night=gl)]
    row1 = Image.new("RGBA", (cell[0] * 4, cell[1]), BG_DAY)
    for i, im in enumerate(views):
        bg = Image.new("RGBA", cell, BG_NIGHT if i == 3 else BG_DAY)
        bg.alpha_composite(im, (0, 0))
        row1.paste(bg, (i * cell[0], 0))
    # true size beside a player, same pixels per model pixel
    unit = 2.6
    body = R.render(rest, lit, yaw=60, pitch=6, px=unit)
    pl = R.render(R.player_tree(), R.steve(), yaw=60, pitch=6, px=unit)
    W2, H2 = max(cell[0] * 4, body.width + pl.width + 120), max(body.height, pl.height) + 40
    row2 = Image.new("RGBA", (W2, H2), (150, 170, 150, 255))
    d2 = ImageDraw.Draw(row2)
    ground = H2 - 24
    row2.alpha_composite(pl, (30, ground - pl.height + 8))
    row2.alpha_composite(body, (90 + pl.width, ground - body.height + 8))
    d2.line((0, ground, W2, ground), fill=(60, 80, 60, 255))
    blocks = (max(0, -min(q[1] for vs, _, _, _ in R.quads(rest) for q in vs)) + 24) / 16
    d2.text((8, 6), f"{rig.id}: {blocks:.1f} blocks tall, {len(rig.bones)} bones, {len(rig.cubes)} cubes, {faces(rig)} faces, "
                    f"tex {rig.tex[0]}x{rig.tex[1]} (used {rig.used} rows), {len(rig.clips)} clips", fill=(20, 24, 30, 255))
    # the moves
    strips = []
    for m in mv:
        strips.append(move_strip(rig, m, lit))
    problems = check(rig, mv)
    Wt = max(row1.width, row2.width, max((s.width for s in strips), default=0))
    Ht = row1.height + row2.height + sum(s.height for s in strips) + 30 + 14 * len(problems)
    out = Image.new("RGBA", (Wt, Ht), (24, 26, 34, 255))
    od = ImageDraw.Draw(out)
    y = 0
    out.paste(row1, (0, y))
    y += row1.height
    out.paste(row2, (0, y))
    y += row2.height
    for s in strips:
        out.paste(s, (0, y))
        y += s.height
    for p in problems:
        od.text((6, y + 4), "CHECK: " + p, fill=(255, 90, 90, 255))
        y += 14
    path = os.path.join(outdir, f"kit_{rig.id}.png")
    out.save(path)
    sk.resize((sk.width * 2, sk.height * 2), Image.NEAREST).save(os.path.join(outdir, f"kit_{rig.id}_tex.png"))
    return path


def move_strip(rig, m, lit):
    from PIL import Image, ImageDraw
    from kit.core import clip_name
    c = clip_name(m)
    W, A, R = m.seconds("windup"), m.seconds("active"), m.seconds("recover")
    frames = [("rest", [(c, 0, 1.0)]), ("gather", [(c, W * 0.35, 1.0)]), ("peak", [(c, W, 1.0)]),
              ("contact", [(c, W + A * 0.5, 1.0)]), ("through", [(c, W + A + R * 0.25, 1.0)]), ("settle", [(c, W + A + R * 0.82, 1.0)])]
    trees = [rig.tree(rig.sample(l)) for _, l in frames]
    yaw, pitch = 28, 10
    b = R.bounds_of(trees, yaw, pitch)
    pc = (210, 200)
    px = min((pc[0] - 16) / (b[1] - b[0]), (pc[1] - 24) / (b[3] - b[2]))
    strip = Image.new("RGBA", (pc[0] * len(frames) + 260, pc[1] + 18), (40, 44, 54, 255))
    sd = ImageDraw.Draw(strip)
    cam = _cam(yaw, pitch)
    org = (pc[0], pc[1], (pc[0] - (b[1] - b[0]) * px) / 2 - b[0] * px, pc[1] - 8 - b[3] * px)
    caps = capsules(rig, m) if m.hit else []
    for k, ((label, _), t) in enumerate(zip(frames, trees)):
        im = R.render(t, lit, yaw=yaw, pitch=pitch, px=px, bounds=b, origin=org)
        bg = Image.new("RGBA", pc, BG_DAY)
        bg.alpha_composite(im)
        if label == "contact" and caps:
            dd = ImageDraw.Draw(bg)
            for c in caps[len(caps) // 2]:
                pa = _proj(cam, c[0:3], px, org)
                pb = _proj(cam, c[3:6], px, org)
                rr = c[6] * 16 * px
                dd.line((pa, pb), fill=(255, 40, 40, 255), width=max(1, int(rr * 0.25)))
                dd.ellipse((pb[0] - rr, pb[1] - rr, pb[0] + rr, pb[1] + rr), outline=(255, 40, 40, 255))
        strip.paste(bg, (k * pc[0], 0))
        sd.text((k * pc[0] + 4, pc[1] + 3), label, fill=(220, 225, 235, 255))
    x = pc[0] * len(frames) + 8
    info = [f"{m.id}  {m.title}", f"{m.cls}  {m.windup}+{m.hold}h / {m.active} / {m.recover} ticks",
            f"range {m.range[0]:g}-{m.range[1]:g}  track {m.track:g}/t  lock {m.lock}", f"kinds {', '.join(m.kinds) or '-'}",
            f"advance {m.advance:g}  rise {m.rise:g}", f"ability {m.ability or '-'}", f"{'SIGNATURE' if m.signature else ''}"]
    for i, s in enumerate(info):
        sd.text((x, 10 + i * 16), s, fill=(230, 210, 150, 255) if i == 0 else (200, 205, 215, 255))
    return strip


def _proj(cam, body_pt, px, org):
    # body space (blocks: +x left, +y up, +z forward) back to model pixels, then the renderer's camera
    p = (body_pt[0] * 16, 24 - body_pt[1] * 16, -body_pt[2] * 16)
    c = R._mv(cam, p)
    return (c[0] * px + org[2], c[1] * px + org[3])
