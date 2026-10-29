"""Rig toolkit for the hero creatures (heroes.py): one source for the model, its texture and its keyframe animations.

A Rig is a bone tree (vanilla ModelPart semantics: pivots in model pixels, y down, ground at y=24, rotations applied
Z then Y then X), a set of keyframe clips (vanilla AnimationDefinition semantics: rotation keys in degrees, position
keys in posVec units where +y is up, scale keys as factors) and the ability names each clip answers to. From it:

  * UVs are packed automatically (vanilla cube layout, mirrored limbs can share one rect),
  * the texture is painted face by face with clean value masses (fauna_rig.Face helpers),
  * the Java side is written as plain data (mobs/client/anim/HeroData.java, parsed by HeroRig.java),
  * previews render the real geometry posed by the same keyframes (sampled exactly like vanilla's KeyframeAnimation).

This file has no build step of its own (heroes.py builds); build(out) is a no-op so build_all.py can import it.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
from pixelart import Canvas, value_noise  # noqa: E402

DEG = math.pi / 180.0


def build(out):  # noqa: D401 - build_all.py imports every spec; heroes.py does the work
    pass


# ---------------------------------------------------------------------------------------------------------------------
# Colours

def hexc(h, a=255):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a


def lerpc(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (a[3] if len(a) > 3 else 255,)


def mul(c, f):
    return tuple(max(0, min(255, int(round(c[i] * f)))) for i in range(3)) + (c[3] if len(c) > 3 else 255,)


class Ramp:
    """Dark to light colours; index arithmetic in steps (fractions round to the nearest step)."""

    def __init__(self, *hexes):
        self.c = [hexc(h) for h in hexes]

    def __len__(self):
        return len(self.c)

    def at(self, v):
        i = int(math.floor(v + 0.5))
        return self.c[max(0, min(len(self.c) - 1, i))]


# ---------------------------------------------------------------------------------------------------------------------
# Geometry

class Cube:
    def __init__(self, bone, name, x, y, z, w, h, d, mirror=False, inflate=0.0, share=None):
        for v in (w, h, d):
            if abs(v - round(v)) > 1e-6:
                raise ValueError(f"{name}: cube sizes must be whole pixels")
        self.bone, self.name = bone, name
        self.x, self.y, self.z, self.w, self.h, self.d = x, y, z, int(round(w)), int(round(h)), int(round(d))
        self.mirror, self.inflate, self.share = mirror, inflate, share
        self.u = self.v = None

    def footprint(self):
        return 2 * (self.d + self.w), self.d + self.h


class Bone:
    def __init__(self, rig, name, parent, pivot, rot):
        self.rig, self.name, self.parent = rig, name, parent
        self.pivot = tuple(float(p) for p in pivot)
        self.rot = tuple(float(r) for r in rot)  # degrees
        self.cubes = []
        self.children = []

    def cube(self, name, x, y, z, w, h, d, mirror=False, inflate=0.0, share=None):
        c = Cube(self, name, x, y, z, w, h, d, mirror, inflate, share)
        self.cubes.append(c)
        self.rig._cube(c)
        return c

    def bone(self, name, pivot, rot=(0, 0, 0)):
        return self.rig.bone(name, pivot, rot, parent=self)


class Clip:
    def __init__(self, name, length, loop):
        self.name, self.length, self.loop = name, float(length), loop
        self.tracks = {}  # (bone, target) -> [(t, (x,y,z), interp)]

    def _add(self, bone, target, keys, interp):
        lst = self.tracks.setdefault((bone, target), [])
        for k in keys:
            t, x, y, z = k[0], k[1], k[2], k[3]
            it = k[4] if len(k) > 4 else interp
            lst.append((float(t), (float(x), float(y), float(z)), it))
        lst.sort(key=lambda e: e[0])
        return self

    def rot(self, bone, keys, interp="cat"):
        return self._add(bone, "rot", keys, interp)

    def pos(self, bone, keys, interp="cat"):
        return self._add(bone, "pos", keys, interp)

    def scale(self, bone, keys, interp="cat"):
        return self._add(bone, "scale", keys, interp)


class Rig:
    def __init__(self, rid, tex=(128, 128), translucent=False):
        self.id = rid
        self.tex = tex
        self.translucent = translucent
        self.bones = []
        self.by_name = {}
        self.cubes = []
        self.clips = {}
        self.moves = {}  # ability name -> clip prefix
        self.look = []  # (bone, yaw weight, pitch weight)
        self.walk_stride = 2.0  # blocks travelled per walk clip cycle
        self.run_stride = 4.0
        self.run_speed = (0.16, 0.32)  # blocks/tick where the run blend starts and is complete
        self.shadow = 0.8
        self.notes = ""
        self.springs = []  # kit bodies: (bone, gain, hz, damping) secondary parts that trail the body (SPEC §35.10)

    def bone(self, name, pivot, rot=(0, 0, 0), parent=None):
        if name in self.by_name:
            raise ValueError(f"{self.id}: duplicate bone {name}")
        b = Bone(self, name, parent, pivot, rot)
        self.bones.append(b)
        self.by_name[name] = b
        if parent is not None:
            parent.children.append(b)
        return b

    def _cube(self, c):
        self.cubes.append(c)

    def clip(self, name, length, loop=False):
        c = Clip(name, length, loop)
        self.clips[name] = c
        return c

    def roots(self):
        return [b for b in self.bones if b.parent is None]

    # ---- UV packing ------------------------------------------------------------------------------------------------
    def pack(self):
        named = {c.name: c for c in self.cubes}
        own = [c for c in self.cubes if c.share is None]
        own.sort(key=lambda c: (-c.footprint()[1], -c.footprint()[0]))
        W, H = self.tex
        sky = [0] * W  # skyline packer: each rect goes where it sits lowest (leftmost on ties)
        for c in own:
            fw, fh = c.footprint()
            if fw > W:
                raise ValueError(f"{self.id}: {c.name} is wider ({fw}) than the texture")
            best = None
            for x in range(0, W - fw + 1):
                y = max(sky[x:x + fw])
                if best is None or y < best[1]:
                    best = (x, y)
            x, y = best
            if y + fh > H:
                raise ValueError(f"{self.id}: texture {W}x{H} too small at {c.name} ({fw}x{fh})")
            c.u, c.v = x, y
            for k in range(x, x + fw):
                sky[k] = y + fh
        y, row = max(sky), 0
        for c in self.cubes:
            if c.share is not None:
                s = named[c.share]
                if (s.w, s.h, s.d) != (c.w, c.h, c.d):
                    raise ValueError(f"{self.id}: {c.name} shares {s.name} but sizes differ")
                c.u, c.v = s.u, s.v
        self.used = y + row
        return self

    # ---- posing ----------------------------------------------------------------------------------------------------
    def tree(self, pose=None):
        """Box tree for the renderer. pose: {bone: {"pos": (x,y,z) model px, "rot": (rad...), "scale": (sx,sy,sz)}}."""
        pose = pose or {}

        def node(b):
            p = pose.get(b.name, {})
            dp = p.get("pos", (0, 0, 0))
            dr = p.get("rot", (0, 0, 0))
            ds = p.get("scale", (1, 1, 1))
            boxes = [{"u": c.u, "v": c.v, "x": c.x, "y": c.y, "z": c.z, "w": c.w, "h": c.h, "d": c.d, "mirror": c.mirror,
                      "grow": c.inflate, "name": c.name} for c in b.cubes]
            return {"pivot": (b.pivot[0] + dp[0], b.pivot[1] + dp[1], b.pivot[2] + dp[2]),
                    "rot": (b.rot[0] * DEG + dr[0], b.rot[1] * DEG + dr[1], b.rot[2] * DEG + dr[2]),
                    "scale": ds, "boxes": boxes, "children": [node(ch) for ch in b.children]}
        return {"pivot": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1), "boxes": [], "children": [node(b) for b in self.roots()]}

    def sample(self, layers):
        """layers: [(clip name, seconds, weight)] -> pose dict, summed like vanilla (offsets add, scale offsets add)."""
        pose = {}
        for name, t, w in layers:
            clip = self.clips.get(name)
            if clip is None or w <= 0:
                continue
            tt = (t % clip.length) if clip.loop and clip.length > 0 else t
            for (bone, target), keys in clip.tracks.items():
                v = _interp(keys, tt)
                e = pose.setdefault(bone, {"pos": [0, 0, 0], "rot": [0, 0, 0], "scale": [1, 1, 1]})
                if target == "rot":
                    for i in range(3):
                        e["rot"][i] += v[i] * DEG * w
                elif target == "pos":
                    e["pos"][0] += v[0] * w
                    e["pos"][1] -= v[1] * w
                    e["pos"][2] += v[2] * w
                else:
                    for i in range(3):
                        e["scale"][i] += (v[i] - 1.0) * w
        return {k: {"pos": tuple(v["pos"]), "rot": tuple(v["rot"]), "scale": tuple(v["scale"])} for k, v in pose.items()}

    # ---- Java data --------------------------------------------------------------------------------------------------
    def java_lines(self):
        f = _num
        out = [f"rig {self.id} {self.tex[0]} {self.tex[1]} {1 if self.translucent else 0} {f(self.walk_stride)} {f(self.run_stride)} "
               f"{f(self.run_speed[0])} {f(self.run_speed[1])} {f(self.shadow)}"]
        for b in self.bones:
            out.append(f"bone {b.name} {b.parent.name if b.parent else '-'} {f(b.pivot[0])} {f(b.pivot[1])} {f(b.pivot[2])} "
                       f"{f(b.rot[0] * DEG)} {f(b.rot[1] * DEG)} {f(b.rot[2] * DEG)}")
            for c in b.cubes:
                out.append(f"cube {c.u} {c.v} {f(c.x)} {f(c.y)} {f(c.z)} {c.w} {c.h} {c.d} {1 if c.mirror else 0} {f(c.inflate)}")
        for bn, yw, pw in self.look:
            out.append(f"look {bn} {f(yw)} {f(pw)}")
        for ability, prefix in self.moves.items():
            out.append(f"move {ability.replace(' ', '_')} {prefix}")
        for bn, gain, hz, damp in self.springs:
            out.append(f"spring {bn} {f(gain)} {f(hz)} {f(damp)}")
        for c in self.clips.values():
            out.append(f"clip {c.name} {f(c.length)} {1 if c.loop else 0}")
            for (bone, target), keys in c.tracks.items():
                ks = " ".join(f"{f(t)} {f(v[0])} {f(v[1])} {f(v[2])} {1 if it == 'cat' else 0}" for t, v, it in keys)
                out.append(f"track {bone} {target} {len(keys)} {ks}")
        return out

    def check(self):
        problems = []
        for c in self.clips.values():
            for (bone, target), keys in c.tracks.items():
                if bone not in self.by_name:
                    problems.append(f"{self.id}/{c.name}: no bone {bone}")
                for t, _, _ in keys:
                    if t < -1e-6 or t > c.length + 1e-6:
                        problems.append(f"{self.id}/{c.name}: key at {t} outside 0..{c.length}")
        for bn, _, _ in self.look:
            if bn not in self.by_name:
                problems.append(f"{self.id}: look bone {bn} missing")
        for need in ("idle", "hurt", "death"):
            if need not in self.clips:
                problems.append(f"{self.id}: no {need} clip")
        if "walk" not in self.clips and "fly" not in self.clips:
            problems.append(f"{self.id}: neither walk nor fly")
        for ability, mv in self.moves.items():
            if mv in ("roar", "warn"):
                if mv not in self.clips:
                    problems.append(f"{self.id}: move {ability} plays missing clip {mv}")
            elif mv + "_windup" not in self.clips or mv + "_strike" not in self.clips:
                problems.append(f"{self.id}: move {ability} lacks {mv}_windup/_strike")
        return problems


def _num(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def _catmull(a, p0, p1, p2, p3):
    return 0.5 * (2 * p1 + (p2 - p0) * a + (2 * p0 - 5 * p1 + 4 * p2 - p3) * a * a + (3 * p1 - p0 - 3 * p2 + p3) * a * a * a)


def _interp(keys, t):
    n = len(keys)
    idx = n
    for i, k in enumerate(keys):
        if t <= k[0]:
            idx = i
            break
    prev = max(0, idx - 1)
    nxt = min(n - 1, prev + 1)
    k0, k1 = keys[prev], keys[nxt]
    a = 0.0 if nxt == prev else max(0.0, min(1.0, (t - k0[0]) / (k1[0] - k0[0])))
    if k1[2] == "cat":
        p0 = keys[max(0, prev - 1)][1]
        p3 = keys[min(n - 1, nxt + 1)][1]
        return tuple(_catmull(a, p0[i], k0[1][i], k1[1][i], p3[i]) for i in range(3))
    return tuple(k0[1][i] + (k1[1][i] - k0[1][i]) * a for i in range(3))


# ---------------------------------------------------------------------------------------------------------------------
# Texture painting: faces in local coordinates, clean value masses

def face_rects(c):
    """Texture rects (x, y, w, h) of a cube's faces (vanilla layout; see fauna.faces)."""
    u, v, w, h, d = c.u, c.v, c.w, c.h, c.d
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "west": (u, v + d, d, h), "north": (u + d, v + d, w, h),
            "east": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}


SIDES = ("west", "north", "east", "south")
# Light comes from above, a little from the front-left: these are added to a material's base step.
FACE_LIGHT = {"top": 1.0, "north": 0.25, "west": 0.0, "east": -0.35, "south": -0.25, "bottom": -1.6}


class Face:
    """One face of a cube. Local (i, j): i runs across the face as the texture does, j runs down (j = 0 is the top
    edge of a side face; on the top face j = 0 is the back edge)."""

    def __init__(self, canvas, cube, name):
        self.c = canvas
        self.cube = cube
        self.name = name
        self.x, self.y, self.w, self.h = face_rects(cube)[name]

    def set(self, i, j, col):
        if 0 <= i < self.w and 0 <= j < self.h and col is not None:
            self.c.set(self.x + i, self.y + j, col)

    def get(self, i, j):
        return self.c.get(self.x + i, self.y + j)

    def each(self):
        for j in range(self.h):
            for i in range(self.w):
                yield i, j


class Painter:
    def __init__(self, rig, seed):
        import random
        self.rig = rig
        self.skin = Canvas(*rig.tex)
        self.glow = Canvas(*rig.tex)
        self.rng = random.Random(seed)
        self.cubes = {c.name: c for c in rig.cubes}
        self.eye = EyeRec()

    # ---- eyes: the life layer's blink (SPEC §23) -----------------------------------------------------------------
    def eyes(self):
        """``with P.eyes(): <paint the eyes>`` (see EyeRec)."""
        return self.eye.eyes(self.skin, self.glow)

    def blink(self):
        """(skin, glow) canvases with the eyes shut, or None when no eyes were painted in an eyes() block."""
        return self.eye.shut(self.skin, self.glow)

    def face(self, cube, name, glow=False):
        c = self.cubes[cube] if isinstance(cube, str) else cube
        return Face(self.glow if glow else self.skin, c, name)

    def faces(self, cube, names=None, glow=False):
        return [self.face(cube, n, glow) for n in (names or ("top", "bottom") + SIDES)]

    def own(self, c):
        c = self.cubes[c] if isinstance(c, str) else c
        return c.share is None

    # ---- materials ---------------------------------------------------------------------------------------------------
    def paint(self, cube, ramp, base, pattern=None, grad=0.9, light=1.0, rim=0.6, ao=0.7, faces=None, noise=0.0, nscale=6):
        """Base value per face (FACE_LIGHT), a soft top-to-bottom gradient on sides, a lit rim on top edges and a dark
        foot (ambient occlusion) on bottom edges, then pattern(face, i, j) -> step offset (or None to skip)."""
        c = self.cubes[cube] if isinstance(cube, str) else cube
        if not self.own(c):
            return
        for fname in (faces or ("top", "bottom") + SIDES):
            f = Face(self.skin, c, fname)
            if f.w <= 0 or f.h <= 0:
                continue
            nz = value_noise(max(2, f.w), max(2, f.h), self.rng, scale=nscale, octaves=1) if noise else None
            for i, j in f.each():
                v = base + FACE_LIGHT[fname] * light
                if fname in SIDES:
                    t = j / max(1, f.h - 1)
                    v += grad * (0.5 - t)
                    if j == 0:
                        v += rim
                    if j == f.h - 1 and f.h > 2:
                        v -= ao
                elif fname == "top" and rim:
                    if i == 0 or i == f.w - 1 or j == 0 or j == f.h - 1:
                        v += rim * 0.3
                if nz is not None:
                    v += (nz[j][i] - 0.5) * noise
                if pattern is not None:
                    p = pattern(f, i, j)
                    if p is None:
                        continue
                    if isinstance(p, tuple):  # an explicit colour
                        f.set(i, j, p)
                        continue
                    v += p
                f.set(i, j, ramp.at(v))

    def paint_all(self, cubes, *a, **k):
        for c in cubes:
            self.paint(c, *a, **k)

    def glow_px(self, cube, fname, i, j, col):
        Face(self.glow, self.cubes[cube] if isinstance(cube, str) else cube, fname).set(i, j, col)

    def glow_face(self, cube, fname, fn):
        f = Face(self.glow, self.cubes[cube] if isinstance(cube, str) else cube, fname)
        for i, j in f.each():
            col = fn(f, i, j)
            if col is not None:
                f.set(i, j, col)


class EyeRec:
    """The eyes of one creature's texture, for the life layer's blink (SPEC §23). Every pixel set inside
    ``with rec.eyes(skin, glow):`` is an eye pixel; shut() gives the texture with the eyes closed (those pixels as they
    were before the eyes went on, so the lid is the skin around them, with a darker lash line along their bottom edge)
    and its glow with the eyes' light out. The build writes them as <id>_blink.png and <id>_glow_blink.png. Recording
    changes nothing else: the RNG and every other pixel stay as they were."""

    def __init__(self):
        self.skin, self.glow = {}, {}  # pixel -> what it was before the eyes went on

    def clear(self):
        self.skin.clear()
        self.glow.clear()

    def eyes(self, skin, glow):
        return _Eyes(self, skin, glow)

    def shut(self, skin_c, glow_c):
        if not self.skin and not self.glow:
            return None
        skin, glow = skin_c.copy(), glow_c.copy()
        for (x, y), before in self.skin.items():
            col = before
            if col[3] == 0:  # painted onto bare texture: borrow the nearest skin that isn't eye
                for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1), (0, -2)):
                    n = (x + dx, y + dy)
                    if n not in self.skin and skin_c.get(*n)[3] > 0:
                        col = skin_c.get(*n)
                        break
            if (x, y + 1) not in self.skin and col[3] > 0:
                col = mul(col, 0.62)  # the lash line where the lids meet
            skin.set(x, y, col)
        for (x, y), before in self.glow.items():
            glow.set(x, y, before)
        return skin, glow


class _Eyes:
    def __init__(self, rec, skin, glow):
        self.rec, self.skin_c, self.glow_c = rec, skin, glow

    def __enter__(self):
        self.skin = [row[:] for row in self.skin_c.px]
        self.glow = [row[:] for row in self.glow_c.px]
        return self.rec

    def __exit__(self, *exc):
        for y in range(self.skin_c.h):
            rs, rg, s0, g0 = self.skin_c.px[y], self.glow_c.px[y], self.skin[y], self.glow[y]
            for x in range(self.skin_c.w):
                if rs[x] != s0[x]:
                    self.rec.skin.setdefault((x, y), s0[x])
                if rg[x] != g0[x]:
                    self.rec.glow.setdefault((x, y), g0[x])
        return False




# ---- patterns: return a step offset for (face, i, j) --------------------------------------------------------------

def p_fur(depth=1.0, every=3, seed=0, length=4):
    """Fur locks: darker parting lines every few pixels, staggered, with lighter tips. Structured, not noisy."""
    def f(face, i, j):
        if face.name in ("top", "bottom"):
            return -0.5 * depth if (i + (j // 2) * 2 + seed) % (every + 1) == 0 else 0.0
        col = (i + seed) % every
        lock = (j + ((i + seed) // every) * 2) % length
        v = 0.0
        if col == 0:
            v -= 0.9 * depth
        if lock == 0 and col != 0:
            v += 0.5 * depth
        return v
    return f


def p_scales(w=3, h=2, depth=1.0):
    """Offset rows of scales: a bright upper edge and a dark lower edge per scale."""
    def f(face, i, j):
        row = j // h
        off = (row % 2) * (w // 2 + 1)
        ci = (i + off) % w
        cj = j % h
        v = 0.0
        if cj == 0:
            v += 0.6 * depth
        if cj == h - 1:
            v -= 0.5 * depth
        if ci == w - 1:
            v -= 0.6 * depth
        return v
    return f


def p_plates(pw=6, ph=4, depth=1.0, stagger=True):
    def f(face, i, j):
        row = j // ph
        off = (row % 2) * (pw // 2) if stagger else 0
        ci, cj = (i + off) % pw, j % ph
        if ci == pw - 1 or cj == ph - 1:
            return -1.2 * depth
        if cj == 0 or ci == 0:
            return 0.6 * depth
        return 0.0
    return f


def p_bands(every=3, depth=1.0, vertical=False, offset=0):
    def f(face, i, j):
        k = (i if vertical else j) + offset
        return -0.8 * depth if k % every == every - 1 else 0.0
    return f


def p_feathers(fw=3, fh=3, depth=1.0):
    """Overlapping feather tips: V-shaped dark edges, rows offset, light shafts."""
    def f(face, i, j):
        row = j // fh
        off = (row % 2) * (fw // 2 + 1)
        ci = (i + off) % fw
        cj = j % fh
        mid = fw // 2
        if cj == fh - 1 and ci != mid:
            return -0.9 * depth
        if cj == fh - 1 and ci == mid:
            return -0.4 * depth
        if ci == mid and cj < fh - 1:
            return 0.5 * depth
        return 0.0
    return f


def p_stone(rng, depth=1.0, cell=4):
    """Blocky cracked stone: a few big cells with dark seams and lit upper-left edges."""
    cache = {}

    def f(face, i, j):
        key = (face.cube.name, face.name)
        if key not in cache:
            w, h = face.w, face.h
            seeds = [(rng.randrange(max(1, w)), rng.randrange(max(1, h))) for _ in range(max(1, (w * h) // (cell * cell)))]
            grid = [[min(range(len(seeds)), key=lambda k: (seeds[k][0] - x) ** 2 + (seeds[k][1] - y) ** 2 * 1.3) for x in range(w)] for y in range(h)]
            shade_ = [rng.choice((-0.4, 0.0, 0.0, 0.3)) for _ in seeds]
            cache[key] = (grid, shade_)
        grid, shade_ = cache[key]
        g = grid[j][i]
        right = grid[j][i + 1] if i + 1 < face.w else g
        down = grid[j + 1][i] if j + 1 < face.h else g
        left = grid[j][i - 1] if i > 0 else g
        up = grid[j - 1][i] if j > 0 else g
        if right != g or down != g:
            return -1.3 * depth
        if left != g or up != g:
            return 0.6 * depth + shade_[g]
        return shade_[g]
    return f


def p_none(face, i, j):
    return 0.0


def _h(*a):
    """A small deterministic hash (stable across runs, unlike hash())."""
    x = 2166136261
    for v in a:
        for ch in str(v):
            x = ((x ^ ord(ch)) * 16777619) & 0xFFFFFFFF
        x = ((x ^ 0x9E) * 16777619) & 0xFFFFFFFF
    return x


def p_strokes(cell=3, length=3, dark=0.7, light=0.45, density=0.7, seed=0, tops=True):
    """Fur or bristles as short strokes on a staggered grid: each cell may hold one stroke (a darker line `length`
    long with a lighter tip pixel above it). Sparse and structured, so the form shading stays readable."""
    def f(face, i, j):
        if face.name in ("top", "bottom") and not tops:
            return 0.0
        ci = i // cell
        jj = j + (ci % 2) * (length // 2 + 1)
        cj = jj // (length + 1)
        hv = _h(face.cube.name, face.name, ci, cj, seed)
        if (hv % 100) >= density * 100:
            return 0.0
        col = ci * cell + (hv // 100) % cell
        if i != col:
            return 0.0
        k = jj % (length + 1)
        if k == 0:
            return light
        return -dark if k < length else -dark * 0.4
    return f


def p_blotch(rng_seed, scale=6, amount=0.6, levels=2):
    """Large soft value masses (2-3 levels), not per-pixel noise: patches of lighter or darker hide."""
    cache = {}

    def f(face, i, j):
        key = (face.cube.name, face.name)
        if key not in cache:
            import random
            r = random.Random(_h(rng_seed, *key))
            cache[key] = value_noise(max(2, face.w), max(2, face.h), r, scale=scale, octaves=1)
        v = cache[key][j][i]
        q = round((v - 0.5) * levels) / levels
        return q * amount * 2
    return f


def combine(*ps):
    def f(face, i, j):
        tot = 0.0
        for p in ps:
            v = p(face, i, j)
            if v is None:
                return None
            if isinstance(v, tuple):
                return v
            tot += v
        return tot
    return f


# ---------------------------------------------------------------------------------------------------------------------
# Rendering (the fauna.py renderer, with bone scale)

def _rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    rxm = ((1, 0, 0), (0, cx, -sx), (0, sx, cx))
    rym = ((cy, 0, sy), (0, 1, 0), (-sy, 0, cy))
    rzm = ((cz, -sz, 0), (sz, cz, 0), (0, 0, 1))
    return _mm(rzm, _mm(rym, rxm))


def _mm(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def _mv(m, v):
    return tuple(m[i][0] * v[0] + m[i][1] * v[1] + m[i][2] * v[2] for i in range(3))


def quads(tree):
    out = []

    def walk(p, M, T):
        # ModelPart.translateAndRotate: translate(pivot), rotate ZYX, scale
        T2 = tuple(T[i] + _mv(M, p["pivot"])[i] for i in range(3))
        s = p.get("scale", (1, 1, 1))
        M2 = _mm(_mm(M, _rot(*p["rot"])), ((s[0], 0, 0), (0, s[1], 0), (0, 0, s[2])))

        def tf(pt):
            q = _mv(M2, pt)
            return (q[0] + T2[0], q[1] + T2[1], q[2] + T2[2])
        for b in p["boxes"]:
            g = b["grow"]
            x0, y0, z0 = b["x"] - g, b["y"] - g, b["z"] - g
            x1, y1, z1 = b["x"] + b["w"] + g, b["y"] + b["h"] + g, b["z"] + b["d"] + g
            if b["mirror"]:
                x0, x1 = x1, x0
            t0, t1, t2, t3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
            l0, l1, l2, l3 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
            u, v, w, h, d = b["u"], b["v"], b["w"], b["h"], b["d"]
            U0, U1, U2, U22, U3, U4 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
            V0, V1, V2 = v, v + d, v + d + h
            plane = w == 0 or h == 0 or d == 0
            centre = tf(((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2))
            for verts, rect in (((l1, l0, t0, t1), (U1, V0, U2, V1)), ((t2, t3, l3, l2), (U2, V1, U22, V0)),
                                ((t0, l0, l3, t3), (U0, V1, U1, V2)), ((t1, t0, t3, t2), (U1, V1, U2, V2)),
                                ((l1, t1, t2, l2), (U2, V1, U3, V2)), ((l0, l1, l2, l3), (U3, V1, U4, V2))):
                if rect[0] == rect[2] or rect[1] == rect[3]:
                    continue
                vs = [tf(q) for q in verts]
                fc = tuple(sum(q[i] for q in vs) / 4 for i in range(3))
                n = tuple(fc[i] - centre[i] for i in range(3))
                out.append((vs, rect, n, plane))
        for ch in p["children"]:
            walk(ch, M2, T2)
    walk(tree, ((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0, 0, 0))
    return out


def render(tree, tex, yaw=35.0, pitch=22.0, px=8.0, size=None, light=1.0, night=None, bounds=None, origin=None):
    """Renders a posed tree. bounds: fixed (minx, maxx, miny, maxy) in camera space so poses line up in a strip.
    origin: (W, H, offx, offy) to place several renders in one frame."""
    from PIL import Image, ImageDraw, ImageFilter
    ya, pa = math.radians(yaw), math.radians(pitch)
    cam = _mm(_rot(pa, 0, 0), _rot(0, ya, 0))
    lightdir = (-0.45, -0.75, -0.5)
    ln = math.sqrt(sum(c * c for c in lightdir))
    lightdir = tuple(c / ln for c in lightdir)
    tw, th = tex.size
    texpx = tex.load()
    glowpx = night.load() if night is not None else None
    polys = []
    for vs, (ua, va, ub, vb), n, plane in quads(tree):
        cv = [_mv(cam, q) for q in vs]
        nv = _mv(cam, n)
        if not plane and nv[2] > 1e-6:
            continue
        nl = math.sqrt(sum(c * c for c in n)) or 1.0
        lit = 0.62 + 0.38 * max(0.0, -sum(n[i] / nl * lightdir[i] for i in range(3)))
        iu, iv = int(round(abs(ub - ua))), int(round(abs(vb - va)))
        for j in range(iv):
            for i in range(iu):
                tu = min(ua, ub) + i
                tv = min(va, vb) + j
                if not (0 <= tu < tw and 0 <= tv < th):
                    continue
                col = texpx[int(tu), int(tv)]
                gcol = glowpx[int(tu), int(tv)] if glowpx is not None else (0, 0, 0, 0)
                if col[3] < 16 and gcol[3] < 16:
                    continue
                corners = []
                for (a, b) in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    s = (tu + a - ua) / (ub - ua)
                    t = (tv + b - va) / (vb - va)
                    p = tuple((1 - s) * (1 - t) * cv[1][k] + s * (1 - t) * cv[0][k] + (1 - s) * t * cv[2][k] + s * t * cv[3][k] for k in range(3))
                    corners.append(p)
                depth = sum(c[2] for c in corners) / 4
                if night is not None:
                    base = mul(col, 0.22 * lit) if col[3] >= 16 else None
                    polys.append((depth, corners, base, gcol if gcol[3] >= 16 else None, col[3]))
                else:
                    polys.append((depth, corners, mul(col, lit * light) if col[3] >= 16 else None, None, col[3]))
    if not polys:
        return Image.new("RGBA", (8, 8))
    xs = [c[0] for p in polys for c in p[1]]
    ys = [c[1] for p in polys for c in p[1]]
    minx, maxx, miny, maxy = bounds if bounds else (min(xs), max(xs), min(ys), max(ys))
    if size is not None and bounds is None:
        px = min((size[0] - 16) / max(1e-6, maxx - minx), (size[1] - 16) / max(1e-6, maxy - miny))
    if origin is not None:
        W, H, offx, offy = origin
    else:
        W = int((maxx - minx) * px) + 16 if size is None else size[0]
        H = int((maxy - miny) * px) + 16 if size is None else size[1]
        offx = 8 - minx * px if size is None else (W - (maxx - minx) * px) / 2 - minx * px
        offy = 8 - miny * px if size is None else (H - (maxy - miny) * px) - 8 - miny * px
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    glow_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gdr = ImageDraw.Draw(glow_layer)
    for depth, corners, col, gcol, alpha in sorted(polys, key=lambda p: -p[0]):
        pts = [(c[0] * px + offx, c[1] * px + offy) for c in corners]
        if col is not None:
            if alpha < 250:
                # translucent texels (wings, sacs): blend over what is behind
                over = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                ImageDraw.Draw(over).polygon(pts, fill=col[:3] + (alpha,))
                img = Image.alpha_composite(img, over)
                dr = ImageDraw.Draw(img)
            else:
                dr.polygon(pts, fill=col[:3] + (255,))
        if gcol is not None:
            a = gcol[3] / 255
            under = col[:3] if col is not None else (0, 0, 0)
            dr.polygon(pts, fill=tuple(int(under[k] * (1 - a) + gcol[k] * a) for k in range(3)) + (255,))
            gdr.polygon(pts, fill=gcol[:3] + (min(255, gcol[3]),))
    if night is not None:
        halo = glow_layer.filter(ImageFilter.GaussianBlur(max(2, px * 0.6)))
        img = Image.alpha_composite(halo, img)
    return img


def bounds_of(trees, yaw, pitch):
    """Camera-space bounds of several posed trees (to frame a strip of poses identically)."""
    ya, pa = math.radians(yaw), math.radians(pitch)
    cam = _mm(_rot(pa, 0, 0), _rot(0, ya, 0))
    xs, ys = [], []
    for t in trees:
        for vs, _, _, _ in quads(t):
            for q in vs:
                c = _mv(cam, q)
                xs.append(c[0])
                ys.append(c[1])
    return min(xs), max(xs), min(ys), max(ys)


# ---------------------------------------------------------------------------------------------------------------------
# A player for scale (vanilla player model, wide arms, the steve texture read from the reference assets)

STEVE = "/Volumes/MEDIA/Awaken/.ref/assets/assets/minecraft/textures/entity/player/wide/steve.png"


def player_tree():
    def b(u, v, x, y, z, w, h, d):
        return {"u": u, "v": v, "x": x, "y": y, "z": z, "w": w, "h": h, "d": d, "mirror": False, "grow": 0.0}

    def p(pivot, boxes, rot=(0, 0, 0)):
        return {"pivot": pivot, "rot": rot, "scale": (1, 1, 1), "boxes": boxes, "children": []}
    # Vanilla player model: legs end on y = 24 like every entity model, so it stands on the same ground.
    return {"pivot": (0, 0, 0), "rot": (0, 0, 0), "scale": (1, 1, 1), "boxes": [], "children": [
        p((0, 0, 0), [b(0, 0, -4, -8, -4, 8, 8, 8)]),
        p((0, 0, 0), [b(16, 16, -4, 0, -2, 8, 12, 4)]),
        p((-5, 2, 0), [b(40, 16, -3, -2, -2, 4, 12, 4)], (0.15, 0, 0)),
        p((5, 2, 0), [b(32, 48, -1, -2, -2, 4, 12, 4)], (-0.15, 0, 0)),
        p((-1.9, 12, 0), [b(0, 16, -2, 0, -2, 4, 12, 4)]),
        p((1.9, 12, 0), [b(16, 48, -2, 0, -2, 4, 12, 4)])]}


def steve():
    from PIL import Image
    return Image.open(STEVE).convert("RGBA")
