"""The parts library for premium kit bodies (docs/bosses/REWORK.md R1): the shapes a body is built from, so a boss reads
as carved and layered instead of a stack of plain boxes. No round shapes: every taper steps, every curve is a chain of
short angled segments.

All sizes are model pixels on a fauna_rig bone (y down, -z the front). Each part names its cubes `<name><k>` so a
spec can paint them by prefix (cubes_of).
"""


def taper(bone, name, cx, y0, cz, sizes, up=True, mirror=False):
    """A stepped taper: cubes (w, h, d) stacked from y0, each centred on (cx, cz), going up (-y) or down. The step from
    one size to the next is what reads as a carved form."""
    out, y = [], y0
    for k, (w, h, d) in enumerate(sizes):
        top = y - h if up else y
        out.append(bone.cube(f"{name}{k}", cx - w / 2, top, cz - d / 2, w, h, d, mirror=mirror))
        y = top if up else y + h
    return out


def plate(bone, name, pivot, rot, box, mirror=False):
    """A plate on its own bone, so it can sit at an angle (a cube can't turn on its own) and move as a secondary part."""
    b = bone.bone(name, pivot, rot)
    b.cube(name, *box, mirror=mirror)
    return b


def segments(bone, name, pivot, rot, segs, bend, along="up", mirror=False):
    """A horn, a spike or a tail as a chain of bones: segs [(w, length)], each a square section extending along the
    bone's up (-y) or down; every next segment turns by bend (rx, ry, rz), so the chain curves in steps."""
    b = bone.bone(f"{name}0", pivot, rot)
    out = [b]
    for k, (w, ln) in enumerate(segs):
        y = -ln if along == "up" else 0
        b.cube(f"{name}{k}", -w / 2, y, -w / 2, w, ln, w, mirror=mirror)
        if k + 1 < len(segs):
            nb = b.bone(f"{name}{k + 1}", (0, -ln + 1 if along == "up" else ln - 1, 0), bend)
            out.append(nb)
            b = nb
    return out


def chain(bone, name, pivot, n, link=(3, 6, 3)):
    """Hanging links, each on its own bone (a secondary chain for the motion kit): alternate links turn a quarter."""
    w, h, d = link
    b = bone.bone(f"{name}0", pivot)
    out = [b]
    for k in range(n):
        if k % 2:
            b.cube(f"{name}{k}", -d / 2, 0, -w / 2, d, h, w)
        else:
            b.cube(f"{name}{k}", -w / 2, 0, -d / 2, w, h, d)
        if k + 1 < n:
            b = b.bone(f"{name}{k + 1}", (0, h - 1, 0))
            out.append(b)
    return out


def bars(bone, name, x, y, z, w, n, gap, h=2, d=3):
    """Horizontal bars stacked down from y (a ribcage, a grille): light shows between them."""
    return [bone.cube(f"{name}{k}", x, y + k * (h + gap), z, w, h, d) for k in range(n)]


def cubes_of(rig, *prefixes):
    """The names of a rig's cubes that start with any of the prefixes."""
    return [c.name for c in rig.cubes if any(c.name.startswith(p) for p in prefixes)]
