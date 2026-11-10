#!/usr/bin/env python3
"""Powers, the next level: first-person storyboards (docs/powers-next/PLAN.md, "Hands"). A drawing tool for the approval page,
not the game: it projects boxes from the eye with the hand pass's own numbers (70 degrees, view space x right, y up, z toward
you), so a pose drawn here sits where the game would put it. "Today" rows read the real clips in fp_casts.json. "Proposed" rows
are drafts: a forearm that can point anywhere (a keyed elbow), a turn of the wrist, light that runs along the arm, the weapon
staying in the other hand.

    storyboard.py OUT_DIR      writes the boards as PNG
"""
import json
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
W, H = 480, 270
F = 1 / math.tan(math.radians(35))
SKIN, SKIN2 = (198, 152, 114), (178, 134, 100)
FAMILY = {"kinetic": (224, 143, 31), "velocity": (255, 74, 61), "flight": (167, 205, 227), "energy": (127, 195, 255), "shadow": (143, 107, 255),
          "mind": (208, 136, 194), "architect": (217, 186, 128), "tech": (71, 198, 176), "spirit": (134, 224, 160)}
EASE = {"linear": lambda t: t, "easeoutcubic": lambda t: 1 - (1 - t) ** 3, "easeinoutsine": lambda t: 0.5 - 0.5 * math.cos(math.pi * t),
        "easeoutquad": lambda t: 1 - (1 - t) ** 2, "easeoutsine": lambda t: math.sin(t * math.pi / 2), "easeincubic": lambda t: t ** 3,
        "easeoutback": lambda t: 1 + 2.70158 * (t - 1) ** 3 + 1.70158 * (t - 1) ** 2}


def font(size, bold=False):
    for p in ("/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf", "/System/Library/Fonts/Helvetica.ttc"):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def v_add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
def v_sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def v_mul(a, s): return (a[0] * s, a[1] * s, a[2] * s)
def v_dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def v_cross(a, b): return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def v_norm(a):
    l = math.sqrt(v_dot(a, a)) or 1.0
    return (a[0] / l, a[1] / l, a[2] / l)


def lerp(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def sample(track, tick):
    """track: [(tick, values, ease)] -> values at tick."""
    if tick <= track[0][0]:
        return track[0][1]
    for (t0, a, _), (t1, b, e) in zip(track, track[1:]):
        if tick <= t1:
            return lerp(a, b, EASE[e]((tick - t0) / max(1e-6, t1 - t0)))
    return track[-1][1]


def project(p, fov=1.0):
    z = min(-0.05, p[2])
    return (W / 2 + p[0] / -z * F * fov * H / 2, H / 2 - p[1] / -z * F * fov * H / 2)


class Scene:
    def __init__(self, fov=1.0, dip=0.0, roll=0.0, dusk=False):
        self.fov, self.dip, self.roll = fov, dip, roll
        self.quads, self.glows = [], []
        self.im = Image.new("RGB", (W, H))
        self.backdrop(dusk)

    def backdrop(self, dusk):
        dr = ImageDraw.Draw(self.im)
        hz = H * 0.46 + self.dip * H
        top, low = ((36, 48, 82), (120, 110, 130)) if dusk else ((92, 142, 214), (176, 208, 236))
        for y in range(H):
            t = min(1, max(0, y / max(1, hz)))
            dr.line([(0, y), (W, y)], fill=tuple(int(top[i] + (low[i] - top[i]) * t) for i in range(3)))
        g0, g1 = ((40, 62, 44), (58, 92, 52)) if dusk else ((74, 118, 58), (104, 152, 70))
        for y in range(int(hz), H):
            t = (y - hz) / max(1, H - hz)
            dr.line([(0, y), (W, y)], fill=tuple(int(g0[i] + (g1[i] - g0[i]) * t) for i in range(3)))
        # block seams on the ground, in perspective (eye 1.62 above it)
        for k in range(1, 14):
            y = hz + (H / 2) * F * self.fov * 1.62 / k
            if y < H:
                dr.line([(0, y), (W, y)], fill=tuple(int(c * 0.86) for c in g1), width=1)
        for x in range(-8, 9):
            a = project((x, -1.62, -1.0), self.fov)
            b = project((x, -1.62, -14.0), self.fov)
            dr.line([(a[0], a[1] + self.dip * H), (b[0], b[1] + self.dip * H)], fill=tuple(int(c * 0.9) for c in g0), width=1)

    def dummy(self, at=(0.0, -1.62, -3.6), hit=0.0, tint=None, lean=0.0):
        """A husk-sized target: legs, body, head. `hit` flashes it, `lean` tips it back."""
        base = (116, 132, 92) if tint is None else tint
        col = tuple(min(255, int(c + (255 - c) * hit)) for c in base)
        x, y, z = at
        z2 = z - lean
        self.box((x - 0.13, y, z), (x - 0.13, y + 0.75, z), (0, 0, 1), 0.12, 0.12, lambda *_: (tuple(int(c * 0.8) for c in col), 0))
        self.box((x + 0.13, y, z), (x + 0.13, y + 0.75, z), (0, 0, 1), 0.12, 0.12, lambda *_: (tuple(int(c * 0.8) for c in col), 0))
        self.box((x, y + 0.75, z), (x, y + 1.5, z2), (0, 0, 1), 0.25, 0.13, lambda *_: (col, 0))
        self.box((x, y + 1.5, z2), (x, y + 2.0, z2 - lean * 0.4), (0, 0, 1), 0.25, 0.25, lambda *_: (tuple(int(c * 1.08) for c in col), 0))

    def box(self, root, tip, front, hw, hd, tex, cells=(4, 12), roll=0.0):
        """A box from root to tip, its front turned to `front`, half width hw across and hd deep. tex(face, u, v) -> (rgb, glow)."""
        d = v_norm(v_sub(tip, root))
        f = v_sub(front, v_mul(d, v_dot(front, d)))
        if v_dot(f, f) < 1e-6:
            f = (0, 0, 1)
        f = v_norm(f)
        r = v_cross(d, f)
        if roll:
            c, s = math.cos(math.radians(roll)), math.sin(math.radians(roll))
            f, r = v_add(v_mul(f, c), v_mul(r, s)), v_add(v_mul(r, c), v_mul(f, -s))
        L = math.sqrt(v_dot(v_sub(tip, root), v_sub(tip, root)))
        nu, nv = cells

        def pt(u, v, w):  # u across (-1..1), v along (0..1), w depth (-1..1)
            return v_add(v_add(v_add(root, v_mul(d, v * L)), v_mul(r, u * hw)), v_mul(f, w * hd))
        faces = [("front", lambda a, b: pt(a * 2 - 1, b, 1), nu), ("back", lambda a, b: pt(1 - a * 2, b, -1), nu),
                 ("right", lambda a, b: pt(1, b, 1 - a * 2), nu), ("left", lambda a, b: pt(-1, b, a * 2 - 1), nu)]
        for name, fn, n in faces:
            for i in range(n):
                for j in range(nv):
                    q = [fn(i / n, j / nv), fn((i + 1) / n, j / nv), fn((i + 1) / n, (j + 1) / nv), fn(i / n, (j + 1) / nv)]
                    col, glow = tex(name, i, j)
                    self.quads.append((q, col, glow, name))
        cap = [pt(-1, 1, -1), pt(1, 1, -1), pt(1, 1, 1), pt(-1, 1, 1)]
        col, glow = tex("tip", 0, nv - 1)
        self.quads.append((cap, col, glow, "tip"))

    def glow(self, at, radius, colour, strength=1.0):
        self.glows.append((at, radius, colour, strength))

    def render(self):
        dr = ImageDraw.Draw(self.im)
        light = Image.new("RGB", (W, H))
        ld = ImageDraw.Draw(light)
        shade = {"front": 1.0, "tip": 1.06, "right": 0.82, "left": 0.78, "back": 0.6}
        self.quads.sort(key=lambda q: sum(p[2] for p in q[0]) / 4)
        for q, col, glow, name in self.quads:
            if any(p[2] > -0.08 for p in q):
                continue
            pts = [project(p, self.fov) for p in q]
            area = sum(pts[i][0] * pts[(i + 1) % 4][1] - pts[(i + 1) % 4][0] * pts[i][1] for i in range(4))
            if area > 0:  # facing away
                continue
            s = shade[name]
            c = tuple(min(255, int(v * (s + (1 - s) * glow))) for v in col)
            dr.polygon(pts, fill=c)
            if glow > 0:
                ld.polygon(pts, fill=tuple(int(v * glow) for v in col))
        for at, radius, colour, strength in self.glows:
            if at[2] > -0.08:
                continue
            x, y = project(at, self.fov)
            rr = radius / -at[2] * F * self.fov * H / 2
            for k in range(6, 0, -1):
                a = strength * (1 - k / 7) ** 1.5
                ld.ellipse([x - rr * k / 3, y - rr * k / 3, x + rr * k / 3, y + rr * k / 3], fill=tuple(int(min(255, v * a)) for v in colour))
        bloom = light.filter(ImageFilter.GaussianBlur(7))
        self.im = ImageChops.add(ImageChops.add(self.im, bloom), light.filter(ImageFilter.GaussianBlur(1.2)))
        if self.roll:
            self.im = self.im.rotate(self.roll, resample=Image.BICUBIC, center=(W / 2, H / 2)).resize((W, H)).crop((0, 0, W, H))
        return self.im


def cell_noise(i, j, k=0):
    return ((i * 7 + j * 13 + k * 5) % 7 - 3) * 0.012


def skin(base):
    def tex(face, u, v):
        s = 1 + cell_noise(u, v)
        if v >= 9:
            s *= 1.05  # the hand
        if v == 8:
            s *= 0.9   # the wrist line
        return (tuple(int(c * s) for c in base), 0)
    return tex


def dressed(base, family, charge=1.0, heat=1.0):
    """The arm showing its power: a pixel pattern per family that runs from the elbow to the fist as `charge` goes 0 to 1."""
    col = FAMILY[family]
    plain = skin(base)

    def lit(face, u, v):
        if v / 12 > charge + 1e-6:
            return False
        if family == "kinetic":     # cracked seams
            return (u + (v * 3 + (0 if face == "front" else 2)) % 5) % 4 == 1 or (v % 5 == 2 and u in (1, 2))
        if family == "velocity":    # two streaks, broken
            return u in (1, 2) and (v + u * 2) % 4 != 0
        if family == "flight":      # motes
            return (u * 5 + v * 3) % 7 == 0
        if family == "energy":      # a vein that zigzags
            return u == (1 + (v // 2) % 2) or (v % 4 == 1 and u == 2 - (v // 2) % 2)
        if family == "shadow":      # ink from the hand back
            return v >= 11 - int(charge * 6) + (u * 2 + (0 if face == "front" else 1)) % 3
        if family == "mind":        # rings at the wrist
            return v in (7, 9) or (v == 11 and charge > 0.8)
        if family == "architect":   # a drawn grid
            return v % 3 == 0 or u in (0,) and face == "front"
        if family == "tech":        # plates with seams
            return not (v % 4 == 3 or u == 2) and v >= 2
        if family == "spirit":      # tongues of flame from the hand
            return v >= 11 - (2, 5, 3, 6)[u % 4] * charge
        return False

    def tex(face, u, v):
        if face in ("front", "right", "left", "tip") and lit(face, u, v):
            if family == "shadow":
                return (tuple(int(c * 0.35) for c in col), 0.25 * heat)
            if family == "tech":
                return (tuple(int(c * 0.8) for c in col), 0.35 * heat)
            return (col, heat)
        return plain(face, u, v)
    return tex


ANCHOR = (0.48, -0.84, -0.53)


def arm_today(sc, fist, right, tex=None):
    """build's own drawing: one straight box from a fixed point under the corner to the fist (anims.client.WorldBody.viewArm)."""
    side = 1 if right else -1
    s = (side * ANCHOR[0], ANCHOR[1], ANCHOR[2])
    d = v_norm(v_sub(fist[:3], s))
    root = v_sub(fist[:3], v_mul(d, 0.6))
    sc.box(v_add(root, v_mul(d, -2 / 16)), v_add(root, v_mul(d, 10 / 16)), (0, 1, 0), 2 / 16, 2 / 16, tex or skin(SKIN if right else SKIN2))


def arm_new(sc, fist, elbow, right, tex=None, roll=0.0):
    """Proposed: the forearm runs from a keyed elbow to the fist, with a turn of the wrist; the upper arm goes off under the frame."""
    side = 1 if right else -1
    base = SKIN if right else SKIN2
    d = v_norm(v_sub(fist, elbow))
    sc.box(v_sub(fist, v_mul(d, 0.52)), v_add(fist, v_mul(d, 0.10)), (0, 1, 0.2), 2 / 16, 2 / 16, tex or skin(base), roll=roll)
    shoulder = (side * 0.52, -1.25, -0.35)
    sc.box(shoulder, v_sub(fist, v_mul(d, 0.50)), (0, 0.3, 1), 2.1 / 16, 2.1 / 16, lambda f, u, v: (tuple(int(c * 0.86 * (1 + cell_noise(u, v))) for c in base), 0), cells=(4, 8))


def sword(sc, grip, tip_dir, glow=None):
    """A plain blade in the main hand (the weapon stays): grip point, direction of the blade."""
    d = v_norm(tip_dir)
    steel = lambda f, u, v: ((226, 231, 236) if u in (1, 2) else (170, 178, 190), 0)
    sc.box(v_add(grip, v_mul(d, 0.12)), v_add(grip, v_mul(d, 0.92)), (0.25, 0, 1), 0.060, 0.012, steel, cells=(4, 10))
    sc.box(v_add(grip, v_mul(d, 0.07)), v_add(grip, v_mul(d, 0.13)), (0.25, 0, 1), 0.17, 0.03, lambda *_: ((150, 116, 60), 0), cells=(2, 1))
    sc.box(v_add(grip, v_mul(d, -0.16)), v_add(grip, v_mul(d, 0.08)), (0.25, 0, 1), 0.034, 0.034, lambda *_: ((92, 64, 44), 0), cells=(1, 2))


CAP = 46


def wrap(dr, text, f, width):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if dr.textlength(t, font=f) <= width:
            cur = t
        else:
            lines.append(cur)
            cur = w
    return lines + [cur]


def label(im, text, sub=None):
    """The frame with its name on a tab and its note underneath (never over the picture)."""
    out = Image.new("RGB", (W, H + CAP), (30, 30, 34))
    out.paste(im, (0, 0))
    dr = ImageDraw.Draw(out, "RGBA")
    tw = dr.textlength(text, font=font(13, True))
    dr.rectangle([0, 0, tw + 16, 22], fill=(0, 0, 0, 150))
    dr.text((8, 4), text, fill=(255, 255, 255), font=font(13, True))
    if sub:
        for i, line in enumerate(wrap(dr, sub, font(12), W - 16)[:3]):
            dr.text((8, H + 5 + i * 14), line, fill=(226, 224, 218), font=font(12))
    out.raw, out.name, out.note = im, text, sub or ""
    return out


MANIFEST = []


def row(frames, title, colour):
    if title:
        MANIFEST.append(dict(title=title, proposed=colour == NEW_COL, frames=[]))
    for fr in frames:
        MANIFEST[-1]["frames"].append(fr)
    pad, head = 6, 30
    im = Image.new("RGB", (len(frames) * (W + pad) + pad, H + CAP + head + pad), (18, 18, 20))
    dr = ImageDraw.Draw(im)
    dr.rectangle([0, 0, im.width, head - 4], fill=colour)
    dr.text((10, 5), title, fill=(255, 255, 255), font=font(15, True))
    for i, fr in enumerate(frames):
        im.paste(fr, (pad + i * (W + pad), head))
    return im


def stack(rows):
    w = max(r.width for r in rows)
    im = Image.new("RGB", (w, sum(r.height for r in rows)), (18, 18, 20))
    y = 0
    for r in rows:
        im.paste(r, (0, y))
        y += r.height
    return im


TODAY_COL, NEW_COL = (96, 92, 84), (180, 84, 26)
CLIPS = json.load(open(os.path.join(ROOT, "src/main/resources/assets/awaken/fp_clips/fp_casts.json")))["clips"]


def clip_track(name, side):
    return [(k[0], tuple(k[1:7]), k[7]) for k in CLIPS[name][side]]


def today_frames(clip, ticks, notes, dummy_hit=None, weapon_note=True):
    """Frames of a real clip from build, drawn the way build draws it: fists only, the weapon gone for the whole clip (stow)."""
    out = []
    c = CLIPS[clip]
    for t, note in zip(ticks, notes):
        sc = Scene()
        sc.dummy(hit=1.0 if dummy_hit is not None and abs(t - dummy_hit) < 0.6 else 0.0)
        m, o = sample(clip_track(clip, "main"), t), sample(clip_track(clip, "off"), t)
        if c.get("fists"):
            arm_today(sc, m, True)
            arm_today(sc, o, False)
        else:  # an offset clip moves vanilla's own resting arm; shown here at the rest fist plus the offset
            rest = (0.744, -0.455, -1.072)
            arm_today(sc, v_add(rest, m[:3]), True)
            if c.get("both"):
                arm_today(sc, v_add((-rest[0], rest[1], rest[2]), o[:3]), False)
        out.append(label(sc.render(), f"tick {t:g}", note))
    return out


def film(paths_notes, crop=None):
    """Real frames from the game (a CI tour's stills), scaled to the board's frame."""
    out = []
    for path, name, note in paths_notes:
        im = Image.open(path).convert("RGB")
        if crop:
            im = im.crop(crop)
        out.append(label(im.resize((W, H), Image.LANCZOS), name, note))
    return out


def board_palm(out, real=None):
    """Spirit Palm with a sword drawn."""
    today = real or today_frames("fp_p_spirit_palm", [0, 1, 3, 5, 9], [
        "The sword is hidden for the whole cast.", "Both arms pull back.", "Both arms are thrown forward in the same tick.", "Held.",
        "Back to rest, and the sword pops back into the hand."], dummy_hit=3)
    grip_rest, grip_low = (0.50, -0.50, -0.78), (0.62, -0.60, -0.70)
    up, low = (-0.22, 0.92, -0.30), (-0.62, 0.62, -0.46)
    keys = [  # tick, off fist, off elbow, roll, charge, heat, sword grip, sword dir, dip, fov, note
        (0, (-0.56, -0.62, -0.84), (-0.66, -1.05, -0.50), 0, 0.0, 0.0, grip_rest, up, 0, 1.0, "The press. Your sword is still in your hand."),
        (2, (-0.46, -0.36, -0.64), (-0.74, -0.74, -0.42), -30, 0.5, 0.7, grip_low, low, 0.004, 1.0, "The left hand draws back to the hip and light starts up the arm. The sword drops into a low guard."),
        (4, (-0.14, -0.16, -0.98), (-0.48, -0.50, -0.52), -80, 1.0, 1.0, grip_low, low, -0.006, 0.985, "The palm drives out along the aim, the wrist turned flat."),
        (5, (-0.05, -0.10, -1.16), (-0.38, -0.42, -0.66), -90, 1.0, 1.5, grip_low, low, -0.012, 0.97, "Contact. The light leaves the arm through the palm."),
        (8, (-0.12, -0.16, -1.02), (-0.46, -0.48, -0.56), -70, 0.3, 0.5, grip_low, low, 0.0, 1.0, "The hand recoils a little and holds while the echo travels through the target."),
        (13, (-0.56, -0.62, -0.84), (-0.66, -1.05, -0.50), 0, 0.0, 0.0, grip_rest, up, 0, 1.0, "Home. The sword is already back up, and you can swing."),
    ]
    new = []
    for t, fist, elbow, roll, charge, heat, grip, sdir, dip, fov, note in keys:
        sc = Scene(fov=fov, dip=dip)
        sc.dummy(hit=0.9 if t == 5 else 0.0, lean=0.25 if t == 8 else 0.0)
        sword(sc, grip, sdir)
        arm_today(sc, (grip[0] + 0.03, grip[1] - 0.10, grip[2] + 0.03), True)
        arm_new(sc, fist, elbow, False, dressed(SKIN2, "spirit", charge, min(1.0, heat)), roll=roll)
        if heat > 0.5:
            sc.glow(v_add(fist, (0.02, 0.03, -0.12)), 0.07 + 0.08 * (heat - 0.5), FAMILY["spirit"], min(0.8, heat * 0.5))
        if t == 5:
            sc.glow((0.0, -0.55, -3.4), 0.42, (210, 255, 225), 0.7)
        if t == 8:
            sc.glow((0.0, -0.55, -3.9), 0.30, FAMILY["spirit"], 0.5)
        new.append(label(sc.render(), f"tick {t:g}", note))
    stack([row(today, "TODAY: Spirit Palm with a sword in your hand" + (" (frames from the game)" if real else " (the real clip in build, hands only)"), TODAY_COL),
           row(new, "PROPOSED (a drawing, not the game): the left hand casts and the sword stays", NEW_COL)]).save(os.path.join(out, "board_palm.png"))


def board_nova(out, real=None):
    today = real or today_frames("fp_p_nova", [0, 2, 4, 5, 8, 12], ["Rest.", "Both fists go to the chest.", "", "Both are thrown wide in the same tick.", "Held.", "Back to rest."])
    E = "energy"
    keys = [  # tick, main fist, main elbow, off fist, off elbow, roll, charge, heat, fov, dip, ball, burst, note
        (0, (0.56, -0.62, -0.84), (0.66, -1.05, -0.50), (-0.56, -0.62, -0.84), (-0.66, -1.05, -0.50), 0, 0, 0, 1.0, 0, 0, 0, "The press."),
        (3, (-0.10, -0.10, -0.66), (0.60, -0.26, -0.56), (0.06, -0.26, -0.70), (-0.62, -0.38, -0.58), 40, 0.6, 0.7, 1.0, 0.006, 0.05, 0, "The forearms cross at the chest, laid right across your view. Light gathers where they meet."),
        (6, (-0.14, -0.12, -0.62), (0.56, -0.30, -0.54), (0.10, -0.28, -0.66), (-0.58, -0.42, -0.56), 55, 1.0, 1.0, 1.04, 0.010, 0.09, 0, "One beat held tight. The view pulls back a little and the world goes quiet."),
        (7, (0.64, 0.02, -0.90), (0.52, -0.56, -0.52), (-0.20, -0.20, -0.72), (-0.58, -0.56, -0.50), -20, 1.0, 1.3, 0.95, -0.014, 0, 0.45, "The right arm goes first."),
        (8, (0.74, 0.10, -0.94), (0.56, -0.50, -0.54), (-0.72, 0.06, -0.94), (-0.56, -0.52, -0.54), -30, 0.8, 1.1, 0.95, -0.008, 0, 0.8, "The left follows a tick later. Both swing past where they'll stop."),
        (12, (0.66, -0.04, -0.90), (0.56, -0.58, -0.52), (-0.64, -0.08, -0.90), (-0.56, -0.60, -0.52), -10, 0.35, 0.5, 1.0, 0, 0, 0.15, "They settle back, and the last of the light fades from the arms."),
    ]
    new = []
    for t, mf, me, of, oe, roll, charge, heat, fov, dip, ball, burst, note in keys:
        sc = Scene(fov=fov, dip=dip)
        sc.dummy(hit=0.8 if t in (7, 8) else 0.0, lean=0.3 if t >= 8 else 0.0)
        arm_new(sc, of, oe, False, dressed(SKIN2, E, charge, min(1.0, heat)), roll=-roll)
        arm_new(sc, mf, me, True, dressed(SKIN, E, charge, min(1.0, heat)), roll=roll)
        if ball:
            sc.glow((-0.02, -0.18, -0.74), ball, (200, 230, 255), 0.9)
        if burst:
            sc.glow((0.0, -0.9, -2.6), 0.9 + burst * 1.6, FAMILY[E], 0.42)
            sc.glow((0.0, -1.5, -2.2), 0.5 + burst, (235, 245, 255), 0.30)
        new.append(label(sc.render(), f"tick {t:g}", note))
    stack([row(today, "TODAY: Nova" + (" (frames from the game)" if real else " (the real clip in build, hands only)"), TODAY_COL),
           row(new, "PROPOSED (a drawing, not the game): Nova", NEW_COL)]).save(os.path.join(out, "board_nova.png"))


def board_arms(out):
    """The nine families on the arm at full charge."""
    lines = {"kinetic": "Cracks of ember light, like stone about to split.", "velocity": "Red streaks that run toward the fist.",
             "flight": "Pale motes lifting off the skin.", "energy": "A white-blue vein that forks.", "shadow": "Ink that creeps back from the hand.",
             "mind": "Rings that close round the wrist.", "architect": "A drawn gold grid.", "tech": "Plates that lock on with dark seams.",
             "spirit": "Tongues of jade flame from the hand."}
    frames = []
    for fam in FAMILY:
        sc = Scene(dusk=True)
        fist, elbow = (-0.02, 0.04, -0.80), (0.60, -0.46, -0.60)
        arm_new(sc, fist, elbow, True, dressed(SKIN, fam, 1.0, 1.0), roll=-20)
        sc.glow(v_add(fist, (-0.06, 0.06, -0.06)), 0.05, FAMILY[fam], 0.35)
        frames.append(label(sc.render(), fam.capitalize(), lines[fam]))
    rows = [row(frames[i:i + 3], "PROPOSED (a drawing, not the game): your arm shows the power you are casting" if i == 0 else "", NEW_COL if i == 0 else (18, 18, 20)) for i in (0, 3, 6)]
    stack(rows).save(os.path.join(out, "board_arms.png"))


def boot(sc, heel, toe, glow=0.0):
    sc.box(heel, toe, (0, 1, 0), 0.11, 0.09, lambda f, u, v: ((58, 50, 44) if v < 8 else (40, 36, 34), glow), cells=(4, 10))


def board_ways(out):
    """One power cast three ways, by what the body is doing: Shockwave."""
    frames = []
    K = "kinetic"
    sc = Scene()
    sc.dummy(at=(0.9, -1.62, -4.6))
    arm_new(sc, (0.60, -0.10, -0.86), (0.72, -0.62, -0.52), True, dressed(SKIN, K, 0.6, 0.7), roll=-15)
    arm_new(sc, (-0.60, -0.10, -0.86), (-0.72, -0.62, -0.52), False, dressed(SKIN2, K, 0.6, 0.7), roll=15)
    sc.box((0.02, -1.30, -0.50), (0.06, -0.46, -0.86), (0, 0.3, -1), 0.11, 0.11, lambda f, u, v: ((66, 84, 122) if v < 8 else (58, 50, 44), 0), cells=(4, 10))
    frames.append(label(sc.render(), "Standing", "The stomp, as today: the knee comes up into view and the boot goes down."))
    sc = Scene(dip=-0.30)
    sc.dummy(at=(1.5, -1.62, -3.0))
    arm_new(sc, (0.04, -0.52, -0.92), (0.52, -0.02, -0.56), True, dressed(SKIN, K, 1.0, 1.0), roll=-90)
    arm_new(sc, (-0.70, -0.10, -0.74), (-0.76, -0.66, -0.46), False, skin(SKIN2), roll=10)
    sc.glow((0.03, -0.80, -1.02), 0.20, FAMILY[K], 0.8)
    sc.glow((0.03, -0.95, -1.4), 0.55, (255, 200, 120), 0.25)
    frames.append(label(sc.render(), "In the air", "You come down fist first. The wave leaves from your knuckles as they hit the ground."))
    sc = Scene(dip=0.02, roll=5, fov=0.96)
    sc.dummy(at=(0.3, -1.62, -5.0))
    arm_new(sc, (0.72, -0.40, -0.72), (0.76, -0.92, -0.42), True, skin(SKIN), roll=-10)
    arm_new(sc, (-0.44, -0.56, -0.70), (-0.70, -0.98, -0.42), False, dressed(SKIN2, K, 0.8, 0.8), roll=30)
    boot(sc, (0.04, -0.78, -0.74), (0.14, -0.58, -1.36))
    sc.glow((0.16, -0.80, -1.9), 0.34, FAMILY[K], 0.7)
    frames.append(label(sc.render(), "At a sprint", "A slide kick. Your heel digs in and the wave runs out ahead of the slide."))
    stack([row(frames, "PROPOSED (a drawing, not the game): Shockwave fits what your body is doing", NEW_COL)]).save(os.path.join(out, "board_ways.png"))


FILM = os.path.join(ROOT, "run-autopilot/screenshots-ci/powers-next-before-42788742556")  # CI run 42788742556, build 6027401c


def real(power, notes):
    paths = [os.path.join(FILM, f"nb_{power}_{l}.png") for l in "abcd"]
    if not all(os.path.exists(p) for p in paths):
        return None
    return film([(p, f"still {i + 1}", n) for i, (p, n) in enumerate(zip(paths, notes))])


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "../boards")
    os.makedirs(out, exist_ok=True)
    board_palm(out, real("spirit_palm", [
        "Just after the press. The thing in your hand is still there and nothing else has moved.",
        "The strike. It is thrown with the weapon hand, so the weapon swings across the bottom of the view and an arm shows at the top right.",
        "A ring and a dome round the target. The weapon is back at the right.",
        "Over."]))
    board_nova(out, real("nova", [
        "The gather. Only the tips of the fists show, at the very bottom edge.",
        "The burst. The whole screen goes yellow and the target catches the game's own fire.",
        "The fists are back near the corners.",
        "The weapon pops back into view."]))
    board_arms(out)
    board_ways(out)
    # every frame on its own, with its words in a manifest: the approval page lays them out as text and pictures
    fdir = os.path.join(out, "frames")
    os.makedirs(fdir, exist_ok=True)
    doc = []
    for r, entry in enumerate(MANIFEST):
        items = []
        for i, fr in enumerate(entry["frames"]):
            name = f"r{r}_{i}.jpg"
            fr.raw.convert("RGB").save(os.path.join(fdir, name), quality=84)
            items.append(dict(file=name, name=fr.name, note=fr.note))
        doc.append(dict(title=entry["title"], proposed=entry["proposed"], frames=items))
    json.dump(doc, open(os.path.join(fdir, "manifest.json"), "w"), indent=1)
    print("boards ->", os.path.relpath(out, ROOT), f"({sum(len(d['frames']) for d in doc)} frames)")
