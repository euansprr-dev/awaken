"""Renders the whole proof (docs/armour/PLAN.md §5 and Part C/D) and runs the overlap checker on every set first.

    python3 docs/armour/proof/render_all.py OUTDIR

Writes: sheet_B/C (today beside proposed), closeup_*, night_*, walk_*, houses_D, garments, materials, lower_<set> (the hips, legs and
boots at 3x, front and back, at rest and mid-stride: the view Euan's crops came from), audit.txt (the checker's result per set).
Fails (exit 1) if the checker finds anything: the same rule the real kit's `check()` would have.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import armour_proof as A  # noqa: E402
import audit as U  # noqa: E402
import proof_sets as S  # noqa: E402
from PIL import Image  # noqa: E402

W, H, CTR = A.W, A.H, A.CTR
SIZE = (256, 256)
KW = dict(scale=8, size=(W, H), centre=CTR, pitch=-0.2)
KN = {k: v for k, v in KW.items() if k != "scale"}
BIG = dict(scale=20, size=(520, 560), centre=(260, 360), pitch=-0.15)
LOW = dict(scale=17, size=(340, 330), centre=(170, -150), pitch=-0.12)     # the hips down to the boots


def today(t, yaw):
    return A.G.render_set(t, yaw, scale=8, size=(W, H), centre=CTR, pitch=-0.2)


def lower(parts, alb, tag, out):
    """Hips to boots, front and back, at rest and mid-stride, the way the crops in Euan's message were taken."""
    views = [A.render(parts, alb, 0.0, 0.0, **LOW), A.render(parts, alb, 0.6, 1.0, **LOW), A.render(parts, alb, math.pi, 0.0, **LOW),
             A.render(parts, alb, math.pi - 0.6, -1.0, **LOW)]
    A.grid([views], w=340, h=330).save(os.path.join(out, "lower_%s.png" % tag))


def main(out):
    os.makedirs(out, exist_ok=True)
    report = []
    bad = 0
    built = {}
    for name, fn in S.SETS.items():
        parts, alb, glw = A.prepare(fn(), SIZE)
        found = U.audit(parts)
        bad += len(found)
        cubes = len(parts)
        report.append("%-16s %2d cubes %4d triangles  %d findings" % (name, cubes, cubes * 12, len(found)))
        built[name] = (parts, alb, glw)
    lk = []
    for name, parts in S.looks():
        ps, alb, glw = A.prepare(parts, SIZE)
        found = U.audit(ps)
        bad += len(found)
        report.append("%-16s %2d cubes %4d triangles  %d findings" % ("look: " + name, len(ps), len(ps) * 12, len(found)))
        lk.append((name, ps, alb, glw))
    open(os.path.join(out, "audit.txt"), "w").write("\n".join(report) + "\n")
    print("\n".join(report))

    for tag, name, tier in (("B", "B Veilwoven", "B"), ("C", "C Gatewrought", "C")):
        parts, alb, glw = built[name]
        A.grid([[today(tier, y) for y in (0.0, 0.7, math.pi / 2, math.pi)],
                [A.render(parts, alb, y, 0.0, **KW) for y in (0.0, 0.7, math.pi / 2, math.pi)]]).save(os.path.join(out, "sheet_%s.png" % tag))
        A.grid([[A.render(parts, alb, y, w, **KW) for y, w in ((0.7, 0.9), (math.pi / 2, 1.0), (math.pi - 0.6, 0.9))]]).save(
            os.path.join(out, "walk_%s.png" % tag))
        A.grid([[today(tier, y) for y in (0.0, 0.7)] + [A.render_night(parts, alb, glw, y, **KN) for y in (0.0, 0.7)]]).save(
            os.path.join(out, "night_%s.png" % tag))
        A.grid([[A.render(parts, alb, 0.55, 0.0, **BIG), A.render_night(parts, alb, glw, 0.55, **BIG)]], w=520, h=560).save(
            os.path.join(out, "closeup_%s.png" % tag))
        lower(parts, alb, tag, out)

    rows = [[today("D", 0.0), today("D", 0.7), today("D", math.pi), today("D", 0.7)]]
    for name in ("Briarguard", "Wyrmscale", "Antlerhold"):
        parts, alb, glw = built[name]
        rows.append([A.render(parts, alb, 0.0, 0.0, **KW), A.render(parts, alb, 0.7, 0.0, **KW), A.render(parts, alb, math.pi, 0.0, **KW),
                     A.render_night(parts, alb, glw, 0.7, **KN)])
        A.grid([[A.render(parts, alb, 0.55, 0.0, **BIG), A.render_night(parts, alb, glw, 0.55, **BIG)]], w=520, h=560).save(
            os.path.join(out, "closeup_%s.png" % name))
        lower(parts, alb, name, out)
    A.grid(rows).save(os.path.join(out, "houses_D.png"))

    top, back = [], []
    for name, ps, alb, glw in lk:
        top.append(A.render(ps, alb, 0.45, 0.15, **KW))
        back.append(A.render(ps, alb, math.pi - 0.45, 0.3, **KW))
    A.grid([top, back]).save(os.path.join(out, "garments.png"))
    A.materials(out)
    if bad:
        raise SystemExit("the checker found %d overlaps" % bad)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "renders"))
