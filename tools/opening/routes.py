#!/usr/bin/env python3
"""The opening's run (SPEC §16.4): three acts, three routes each (rooftop, canal, street), designed on M1's painted city.

  python3 -I tools/opening/routes.py <city-dump.bin> [out-dir]

The rooftop routes are the runners' high road: chosen roofs joined by plank bridges and ramps where the grammar's roofs
don't meet ("the rooftops are a road network of their own, with plank bridges, rope lines and worn bronze handholds"),
with gaps left open on purpose (the jumps), a block to mantle, a slide, a wall to run. Each route is written as anchors
(plan x, feet height, z) and connectors between them; the stage blocks are generated here, laid into the city model, and
the whole route is then checked by the planner (planner.py): a Runner at rank E must get from its start to its end
without touching the street. The street routes follow M1's street route; the canal routes the quays and the water.

Writes src/main/resources/data/awaken/opening/routes.json (routes with their points and moves, the stage blocks, the
plan's hash) and, with an out dir, a top view and oblique views of every act with the routes drawn.
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, HERE)
from terrain import City, FULL, HALF, NONE, TALL  # noqa: E402
from planner import Planner  # noqa: E402

PLAN = os.path.join(ROOT, 'src/main/resources/data/awaken/city/plan.json')
OUT = os.path.join(ROOT, 'src/main/resources/data/awaken/opening/routes.json')

# materials (vanilla ids; the stage writes them as block specs)
SLAB = 'spruce_slab[type=bottom]'
PLANK = 'spruce_planks'
STAIR = 'spruce_stairs[facing=%s,half=bottom,shape=straight]'
POST = 'dark_oak_fence'
BRONZE = 'waxed_exposed_cut_copper'


class Stage:
    """The blocks the routes add, in plan coordinates, mirrored into the city model as they're laid."""

    def __init__(self, city):
        self.city = city
        self.blocks = {}

    def put(self, x, ry, z, spec, kind):
        self.blocks[(x, ry, z)] = spec
        self.city.place(x, ry, z, kind)

    def clear(self, x, ry, z):
        self.blocks[(x, ry, z)] = 'air'
        self.city.place(x, ry, z, NONE)

    def floor_at(self, x, z, feet, facing=None):
        """A walking surface with its top at `feet` (a half-block grid): a bottom slab or a full plank, or a stair."""
        fl = math.floor(feet * 2) / 2
        ry = int(math.floor(fl - 0.01))
        if facing:
            self.put(x, int(round(fl)) - 1, z, STAIR % facing, FULL)
        elif abs(fl - math.floor(fl) - 0.5) < 1e-6:
            self.put(x, ry, z, SLAB, HALF)
        else:
            self.put(x, ry, z, PLANK, FULL)


def facing_down(dx, dz):
    """The stair facing whose climb is toward where we came from (we walk down it along dx, dz)."""
    if abs(dx) >= abs(dz):
        return 'west' if dx > 0 else 'east'
    return 'north' if dz > 0 else 'south'


def line_cells(a, b, width=2):
    """Cells along a straight line from a to b (plan x, z), `width` wide, with their fraction along it."""
    ax, az = a
    bx, bz = b
    n = max(1, int(math.ceil(max(abs(bx - ax), abs(bz - az)))))
    dx, dz = (bx - ax) / n, (bz - az) / n
    L = math.hypot(dx, dz) or 1
    px, pz = -dz / L, dx / L
    out = {}
    for i in range(n + 1):
        t = i / n
        cx, cz = ax + dx * i, az + dz * i
        for w in range(width):
            off = w - (width - 1) / 2
            x, z = int(math.floor(cx + px * off + 0.5)), int(math.floor(cz + pz * off + 0.5))
            if (x, z) not in out:
                out[(x, z)] = t
    return out


def bridge(stage, a, b, gap=0, width=2, steps=False):
    """Planks from anchor a to anchor b ((x, feet, z)), the height interpolated; existing roofs at the right height carry
    the line themselves. gap: cells left open in the middle (a jump). steps: a steep fall laid as stairs (a slide)."""
    c = stage.city
    cells = line_cells((a[0], a[2]), (b[0], b[2]), width)
    n = max(1, max(abs(b[0] - a[0]), abs(b[2] - a[2])))
    lo, hi = 0.5 - gap / (2.0 * n), 0.5 + gap / (2.0 * n)
    for (x, z), t in sorted(cells.items(), key=lambda kv: kv[1]):
        if gap and lo < t < hi:
            continue
        feet = a[1] + (b[1] - a[1]) * t
        if steps:
            feet = math.floor(feet)
        else:
            feet = round(feet * 2) / 2
        # a roof already there at about this height carries us
        if any(abs(f - feet) <= 0.6 for f in c.floors(x, z, int(feet) - 3, int(feet) + 3)):
            continue
        if steps and t > 0:
            stage.floor_at(x, z, feet, facing_down(b[0] - a[0], b[2] - a[2]))
        else:
            stage.floor_at(x, z, feet)
        # head room over the walk: nothing of the city's in the way
        for ry in range(int(math.floor(feet)), int(math.floor(feet + 1.9)) + 1):
            if c.kind(x, ry, z) in (FULL, HALF, TALL) and (x, ry, z) not in stage.blocks:
                stage.clear(x, ry, z)


# ---- the design ---------------------------------------------------------------------------------------------------

def act1_roof(stage):
    """Act 1 · Bellwatch roofs: off the Roost's terrace down the courier's slide onto the roofs, then south-east over
    Bellwatch on the high road to the canal. Returns the anchors (the route's skeleton)."""
    # the terrace's railing opens on the south, and the slide runs down onto the first roof
    for x in (-170, -169):
        stage.clear(x, 40, -127)
    a = [(-169.5, 40.0, -128.5), (-169.5, 40.0, -126.5), (-169.5, 28.0, -114.5)]
    bridge(stage, (a[1][0], a[1][1], a[1][2]), (a[2][0], a[2][1], a[2][2]), steps=True)
    return a


ROUTES = [
    # name, act, kind, design function, box for the planner
    ('act1_roof', 1, 'roof', act1_roof, (-185, -135, -115, -15)),
]


def plan_route(city, anchors, box, min_feet=5.0):
    """The planner's run through the anchors in order (each leg a search), never below min_feet (the street)."""
    P = Planner(city, box, avoid=None)
    path = []
    start = anchors[0]
    for nxt in anchors[1:]:
        s_nodes = P.nodes_at(int(math.floor(start[0])), int(math.floor(start[2])), start[1])[:1]
        tx, tz = int(math.floor(nxt[0])), int(math.floor(nxt[2]))
        goal = (lambda tx, tz, fy: (lambda n: abs(n[0] - tx) <= 1 and abs(n[1] - tz) <= 1 and abs(n[2] / 2 - fy) <= 1.6))(tx, tz, nxt[1])
        leg, cost, n = P.search(s_nodes, goal, (tx, tz), limit=120000)
        if leg is None:
            return None, start, nxt
        path.extend(leg if not path else leg[1:])
        start = (leg[-1][0][0] + 0.5, leg[-1][0][2] / 2, leg[-1][0][1] + 0.5)
    return path, None, None


def points(path):
    """The driver's points: every move that isn't a run, and a run's turning points."""
    out = []
    for i, (node, move) in enumerate(path):
        x, z, f2 = node
        p = [x + 0.5, f2 / 2.0, z + 0.5, move if move != 'start' else 'run']
        if i == 0 or i == len(path) - 1 or move != 'run':
            out.append(p)
            continue
        # keep a run point where the direction changes
        px, pz = path[i - 1][0][0], path[i - 1][0][1]
        nx, nz = path[i + 1][0][0], path[i + 1][0][1]
        if (x - px, z - pz) != (nx - x, nz - z) or path[i + 1][1] != 'run':
            out.append(p)
    return out


def main():
    dump = sys.argv[1]
    city = City(dump)
    stage = Stage(city)
    routes = []
    for name, act, kind, design, box in ROUTES:
        anchors = design(stage)
        path, at, to = plan_route(city, anchors, box)
        if path is None:
            print(f'{name}: no run from {at} to {to}')
            continue
        pts = points(path)
        moves = {}
        for p in pts:
            moves[p[3]] = moves.get(p[3], 0) + 1
        print(f'{name}: {len(pts)} points, moves {moves}')
        routes.append({'name': name, 'act': act, 'kind': kind, 'points': pts})
    with open(PLAN, 'rb') as f:
        import hashlib
        sha = hashlib.sha256(f.read()).hexdigest()
    stage_list = [[x, ry, z, spec] for (x, ry, z), spec in sorted(stage.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))]
    out = {'schemaVersion': 1, 'generator': 'tools/opening/routes.py', 'planSha256': sha, 'routes': routes, 'stage': stage_list}
    with open(OUT, 'w') as f:
        json.dump(out, f, separators=(',', ':'))
    print(f'wrote {OUT}: {len(routes)} routes, {len(stage_list)} stage blocks')


if __name__ == '__main__':
    main()
