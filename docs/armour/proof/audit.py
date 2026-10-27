"""The armour kit's overlap checker (the armour's `check()`, as the weapons have one: voxel.py fails on two faces sharing a plane).
Disposable like the rest of the proof; the real kit would carry this as `gear_parts.py check` and a GameTest.

For a part list it poses the figure at rest and at the extremes of a stride, and reports three kinds of finding:

    zfight   two parts have faces on one plane (within 0.08 px), facing the same way, over a patch of more than 1 px^2: the two
             textures flicker against each other (the vertical stripes in Euan's crops)
    cross    two solid boxes pass through each other (penetration over 0.35 px) and neither sits inside the other: a surface
             pokes out through another
    buried   a part sits wholly inside another (never seen: wasted cubes, and a sign two parts were meant to be one)

Vanilla's own shell boxes overlap at the neck, so shell against shell is skipped. A part and its pivot's parent may touch by
up to 0.35 px (a hanging cloth's top edge sitting on what it hangs from).

    from audit import audit; audit(parts) -> list of findings
"""
import itertools
import math

import numpy as np

import armour_proof as A

TOUCH = 0.35        # how far a part may sink into another before it counts as passing through
HINGE = 1.0         # a cloth chain's joint, or a part on its own pivot, may overlap its neighbour this much when it bends
PLANE = 0.08        # two faces closer than this, facing alike, are one plane
POSES = (0.0, 1.0, -1.0)   # rest and a stride each way (0.55 rad); a full sprint swings the legs to 1.0 rad and is looked at by eye


def box_of(p, pose):
    x, y, z, w, h, d = p["box"]
    g = p["grow"]
    lo = np.array([x - g, y - g, z - g], float)
    hi = np.array([x + w + g, y + h + g, z + d + g], float)
    M = np.array(pose.m, float)
    t = np.array(pose.t, float)
    half = (hi - lo) / 2
    c = M @ ((lo + hi) / 2) + t
    return dict(name=p["name"], parent=p["parent"], c=c, M=M, half=half, lo=lo, hi=hi, t=t, shell=p["name"].startswith("sh_"))


def corners(b):
    out = []
    for sx, sy, sz in itertools.product((-1, 1), repeat=3):
        out.append(b["c"] + b["M"] @ (np.array([sx, sy, sz]) * b["half"]))
    return np.array(out)


def local(b, pts):
    return (np.asarray(pts) - b["c"]) @ b["M"]          # coordinates in b's own axes, centred


def inside(b, pts, eps=0.02):
    q = local(b, pts)
    return np.all(np.abs(q) <= b["half"] + eps, axis=-1)


def penetration(a, b):
    """The SAT depth of two oriented boxes: how far one must move to separate them (0 or less: apart)."""
    axes = [a["M"][:, i] for i in range(3)] + [b["M"][:, i] for i in range(3)]
    for i in range(3):
        for j in range(3):
            n = np.cross(a["M"][:, i], b["M"][:, j])
            if np.linalg.norm(n) > 1e-6:
                axes.append(n / np.linalg.norm(n))
    best = 1e9
    d = b["c"] - a["c"]
    for n in axes:
        ra = np.sum(a["half"] * np.abs(a["M"].T @ n))
        rb = np.sum(b["half"] * np.abs(b["M"].T @ n))
        depth = ra + rb - abs(d @ n)
        if depth <= 0:
            return 0.0
        best = min(best, depth)
    return best


def faces(b):
    for k in range(3):
        for s in (1, -1):
            n = s * b["M"][:, k]
            fc = b["c"] + n * b["half"][k]
            u, v = [i for i in range(3) if i != k]
            yield n, fc, b["M"][:, u], b["M"][:, v], b["half"][u], b["half"][v]


def zfight_faces(a, b):
    out = []
    for ia, (na, fa, ua, va, hu, hv) in enumerate(faces(a)):
        for ib, (nb, fb, ub, vb, hu2, hv2) in enumerate(faces(b)):
            if na @ nb >= 0.995 and abs((fa - fb) @ na) <= PLANE:
                out.append((ia, ib, np.round(na, 1).tolist()))
    return out


def zfight(a, b):
    """The area (px^2, sampled) where a face of a and a face of b lie on one plane and face alike."""
    area = 0.0
    for na, fa, ua, va, hu, hv in faces(a):
        for nb, fb, ub, vb, hu2, hv2 in faces(b):
            if na @ nb < 0.995 or abs((fa - fb) @ na) > PLANE:
                continue
            us = np.arange(-hu + 0.25, hu, 0.5)
            vs = np.arange(-hv + 0.25, hv, 0.5)
            if len(us) == 0 or len(vs) == 0:
                continue
            P = fa + us[:, None, None] * ua + vs[None, :, None] * va
            r = P - fb
            ok = (np.abs(r @ ub) <= hu2) & (np.abs(r @ vb) <= hv2)
            area += ok.sum() * 0.25
    return area


PLANT = 0.30        # a part with less than this share of its volume inside another is planted in it (a spike in a plate)


def share_inside(a, b):
    """The share of a's volume that lies inside b (sampled every half texel)."""
    g = [np.arange(-h + 0.25, h, 0.5) for h in a["half"]]
    if any(len(x) == 0 for x in g):
        return 0.0
    X, Y, Z = np.meshgrid(*g, indexing="ij")
    pts = a["c"] + (np.stack([X, Y, Z], -1).reshape(-1, 3) @ a["M"].T)
    return float(inside(b, pts, eps=0.0).mean())


def audit(parts, poses=POSES, verbose=False):
    findings = {}
    names = [p["name"] for p in parts]
    for walk in poses:
        pose = A.build_poses(walk, parts)
        boxes = [box_of(p, pose[p["name"]]) for p in parts]
        for i, j in itertools.combinations(range(len(boxes)), 2):
            a, b = boxes[i], boxes[j]
            if a["shell"] and b["shell"]:
                continue
            if np.linalg.norm(a["c"] - b["c"]) > np.linalg.norm(a["half"]) + np.linalg.norm(b["half"]) + 1:
                continue
            pair = (a["name"], b["name"])
            near_kin = a["parent"] == b["name"] or b["parent"] == a["name"]
            z = zfight(a, b)
            if z > 1.0 and not near_kin:
                findings.setdefault(("zfight",) + pair, []).append((walk, round(z, 1)))
            depth = penetration(a, b)
            if depth > (HINGE if near_kin else 0.12):
                ca, cb = corners(a), corners(b)
                a_in_b = inside(b, ca).all()
                b_in_a = inside(a, cb).all()
                if a_in_b or b_in_a:
                    if not (a["shell"] or b["shell"]):
                        findings.setdefault(("buried",) + pair, []).append((walk, round(depth, 2)))
                elif depth > TOUCH and not (a["shell"] or b["shell"]):   # wrapping a shell (a cuff, a collar) is the design
                    small, big = (a, b) if np.prod(a["half"]) <= np.prod(b["half"]) else (b, a)
                    if share_inside(small, big) < PLANT and depth < 2.5:
                        continue
                    findings.setdefault(("cross",) + pair, []).append((walk, round(depth, 2)))
    out = [(k[0], k[1], k[2], v) for k, v in sorted(findings.items())]
    if verbose:
        for kind, a, b, v in out:
            print("%-7s %-18s %-18s %s" % (kind, a, b, [(w, float(d)) for w, d in v[:3]]))
        print("%d findings (%d zfight, %d cross, %d buried)" % (len(out), sum(f[0] == "zfight" for f in out), sum(f[0] == "cross" for f in out),
                                                             sum(f[0] == "buried" for f in out)))
    return out


if __name__ == "__main__":
    import proof_sets as S
    bad = 0
    for n, fn in S.SETS.items():
        parts, _, _ = A.prepare(fn(), (256, 256))
        print("==", n, len(parts), "parts")
        bad += len(audit(parts, verbose=True))
    for n, parts in S.looks():
        ps, _, _ = A.prepare(parts, (256, 256))
        print("==", n, len(ps), "parts")
        bad += len(audit(ps, verbose=True))
    raise SystemExit(1 if bad else 0)
