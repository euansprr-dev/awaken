#!/usr/bin/env python3
"""The opening's run (SPEC §16.4): three acts, three routes each (rooftop, canal, street), designed on M1's painted city.

  python3 -I tools/opening/routes.py <city-dump.bin> [out-dir]

The city dump comes from the city_studio_dump GameTest (a city-dump.request file in run/, then
`-Ptests=city_studio_dump`). Writes src/main/resources/data/awaken/opening/routes.json: every route's points (plan x, feet
height, z, and the move that reaches each point), the stage blocks (the run's furniture, placed once into the standing
city by OpeningStage), and the plan's hash. With an out dir, also a top view of the run with the routes drawn.

How the routes are made:
- Rooftop: the runners' high road. A planner (planner.Builder) runs a rank-E Runner over the painted roofs and may lay a
  plank where no roof meets the line, at a price, so the city's own roofs carry most of it ("the rooftops are a road
  network of their own, with plank bridges, rope lines and worn bronze handholds"). Long bridges get a gap left open
  (the jumps), every bridge its posts and a bronze cap where it starts. Then the plain planner (no planks) must run the
  whole route on the city as built, or the script fails. The set pieces are laid by hand: the Roost's slide, the crane's
  trolley (hang under the arm, shimmy past it), the ladders.
- Canal: down the couriers' stair, the streets to the river, a ladder to the lower quay, south along the water, a swim
  across the canal's mouth and (Act 2, the Bell Bridge down) across the river, a ladder up the far side, the market.
- Street: the couriers' stair down the Roost, then M1's street route (Roost to the square by the Mill Bridge).
"""
import hashlib
import heapq
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, HERE)
from terrain import City, FULL, HALF, NONE, TALL, THIN, LADDER  # noqa: E402
from planner import Planner, Builder  # noqa: E402

PLAN = os.path.join(ROOT, 'src/main/resources/data/awaken/city/plan.json')
OUT = os.path.join(ROOT, 'src/main/resources/data/awaken/opening/routes.json')

SLAB = 'spruce_slab[type=bottom]'
PLANK = 'spruce_planks'
STAIR = 'spruce_stairs[facing=%s,half=bottom,shape=straight]'
POST = 'dark_oak_fence'
BRONZE = 'waxed_exposed_cut_copper'
LADDER_SPEC = 'ladder[facing=%s]'
TERRACE_FEET = 40.0


class Stage:
    """The blocks the routes add (plan coordinates), mirrored into the city model as they're laid."""

    def __init__(self, city):
        self.city = city
        self.blocks = {}

    def put(self, x, ry, z, spec, kind):
        self.blocks[(x, ry, z)] = spec
        self.city.place(x, ry, z, kind)

    def clear(self, x, ry, z):
        self.put(x, ry, z, 'air', NONE)

    def walk(self, x, z, feet):
        """A walking surface with its top at `feet` (half-block grid)."""
        if abs(feet * 2 - round(feet * 2)) > 1e-6:
            feet = round(feet * 2) / 2
        if abs(feet - math.floor(feet) - 0.5) < 1e-6:
            self.put(x, int(math.floor(feet)), z, SLAB, HALF)
        else:
            self.put(x, int(feet) - 1, z, PLANK, FULL)

    def stair(self, x, z, feet, facing):
        """A stair whose top is at `feet` (whole blocks), climbing toward `facing`."""
        self.put(x, int(feet) - 1, z, STAIR % facing, FULL)


# ---- set pieces -----------------------------------------------------------------------------------------------------

def roost(stage):
    """The Roost's two ways down: the courier's slide (south, onto the roofs) and the couriers' stair (to the street)."""
    c = stage.city
    # the terrace's railing opens in two places on its south side
    for x in (-170, -169, -177, -176):
        stage.clear(x, 40, -127)
    # the slide: a steep timber run from the terrace down onto the first roof's ridge (Act 1's first move)
    for k in range(13):
        z = -126 + k
        for x in (-170, -169):
            if k == 0:
                stage.walk(x, z, TERRACE_FEET)
            else:
                stage.stair(x, z, TERRACE_FEET - k, 'north')
    for x in (-170, -169):
        stage.walk(x, -113, 28.0)
    # the couriers' stair: five flights down the Roost's south face, two lanes, to the street by its door
    lanes = {0: (-126, -125), 1: (-124, -123)}
    feet = TERRACE_FEET
    x_hi, x_lo = -171, -178
    flight = 0
    while feet > 1.0:
        lane = lanes[flight % 2]
        going_west = flight % 2 == 0
        xs = list(range(x_hi, x_lo - 1, -1)) if going_west else list(range(x_lo, x_hi + 1))
        for x in xs:
            if feet <= 1.0:
                break
            feet -= 1.0
            for z in lane:
                stage.stair(x, z, feet, 'east' if going_west else 'west')
                for ry in range(int(feet), int(feet) + 3):
                    if c.kind(x, ry, z) != NONE and (x, ry, z) not in stage.blocks:
                        stage.clear(x, ry, z)
        # a landing across both lanes at the turn
        end = xs[-1] + (-1 if going_west else 1)
        for z in lanes[0] + lanes[1]:
            stage.walk(end, z, feet)
        flight += 1
    # from the terrace's south-west opening onto the first flight
    for x in (-177, -176):
        stage.walk(x, -126, TERRACE_FEET)
    return [(-176.5, TERRACE_FEET, -128.5), (-176.5, TERRACE_FEET, -126.5)]


def crane(stage):
    """The north crane's arm over the river: a trolley stands on it (you can't run past it on top), its side rails go
    where you hang, so you drop to the arm's south face, shimmy past the trolley and climb back up."""
    # rails off the south side where the hands go
    for x in range(-79, -61):
        stage.clear(x, 23, -25)
    # the trolley: a dark block with its hook frame, two blocks long, on top of the arm
    for x in (-71, -70):
        stage.put(x, 23, -26, 'stripped_dark_oak_log[axis=x]', FULL)
        stage.put(x, 24, -26, 'dark_oak_trapdoor[facing=north,half=bottom,open=false]', THIN)
    # the old rope post on the arm's tower end goes (one obstacle on the arm: the trolley)
    stage.clear(-65, 23, -26)
    # a ladder up the cab's west face, from the arm onto the cab's roof
    ladder(stage, -62, -26, 23, 24, 'west')
    # bronze handholds mark where to drop and where to climb
    stage.put(-74, 22, -25, 'waxed_exposed_cut_copper_slab[type=top]', FULL)
    stage.put(-67, 22, -25, 'waxed_exposed_cut_copper_slab[type=top]', FULL)


def ladder(stage, x, z, ry0, ry1, facing):
    for ry in range(ry0, ry1 + 1):
        stage.put(x, ry, z, LADDER_SPEC % facing, LADDER)


def decorate(stage, planks):
    """Posts under the plank bridges (down to whatever is below, every fourth plank), a bronze cap where a bridge
    starts: the high road reads as the runners' own carpentry."""
    c = stage.city
    for i, (x, feet, z) in enumerate(planks):
        if i % 4 != 1:
            continue
        top = int(math.floor(feet - 0.01)) - 1
        ry = top
        while ry > -8 and c.kind(x, ry, z) == NONE and (x, ry, z) not in stage.blocks:
            ry -= 1
        if top - ry >= 2:
            for y in range(ry + 1, top + 1):
                stage.put(x, y, z, POST, TALL)


# ---- planning -------------------------------------------------------------------------------------------------------

class Route:
    def __init__(self, name, act, kind):
        self.name, self.act, self.kind = name, act, kind
        self.points = []

    def add(self, x, feet, z, move):
        self.points.append([round(x, 2), round(feet, 2), round(z, 2), move])

    def end(self):
        p = self.points[-1]
        return (p[0], p[1], p[2])


def runner_path(city, box, start, goal, centre, min_feet=None, build=False, plank_cost=0.45, limit=400000, max_drop=None, avoid=None):
    P = Builder(city, box, min_feet=-99.0 if min_feet is None else min_feet, plank_cost=plank_cost, avoid=avoid) if build else Planner(city, box, avoid=avoid)
    if max_drop is not None:
        P.max_drop = max_drop
    if min_feet is not None and not build:
        base = P.moves

        def moves(node, base=base):
            for v, cost, m in base(node):
                if v[2] / 2.0 >= min_feet:
                    yield v, cost, m
        P.moves = moves
    sx, sz = int(math.floor(start[0])), int(math.floor(start[2]))
    s = P.nodes_at(sx, sz, start[1])[:1]
    if not s:
        raise SystemExit(f'no floor at the start {start}')
    path, cost, n = P.search(s, goal, centre, limit=limit)
    return path, cost, n


def run_built(city, box, start, goal_node, name):
    """The plain runner (no planks) from start to the Builder's goal over the city as built: the route's real moves."""
    gx, gz, gf2 = goal_node
    goal = lambda n: abs(n[0] - gx) <= 1 and abs(n[1] - gz) <= 1 and abs(n[2] - gf2) <= 2
    path, cost, n = runner_path(city, box, start, goal, (gx, gz), min_feet=None, limit=400000)
    if path is None:
        raise SystemExit(f'{name}: the road as built cannot be run (expanded {n})')
    return path


def lay_planks(stage, path, gap_every=7):
    """The planks a Builder path asked for; long bridges keep a two-block gap in the middle (a jump)."""
    runs, cur = [], []
    for node, move in path:
        if move == 'plank':
            cur.append(node)
        elif cur:
            runs.append(cur)
            cur = []
    if cur:
        runs.append(cur)
    laid = []
    for run in runs:
        skip = set()
        if len(run) >= gap_every:
            mid = len(run) // 2
            # a two-block gap across a straight bridge; one block on a diagonal (it is as wide again)
            ddx, ddz = run[mid][0] - run[mid - 1][0], run[mid][1] - run[mid - 1][1]
            skip = {mid} if ddx != 0 and ddz != 0 else {mid - 1, mid}
        for i, (x, z, f2) in enumerate(run):
            if i in skip:
                continue
            stage.walk(x, z, f2 / 2.0)
            laid.append((x, f2 / 2.0, z))
            # a diagonal step: fill the corner too, so the bridge is a ribbon, never blocks touching at their corners
            if i + 1 < len(run) and i + 1 not in skip:
                nx, nz, nf2 = run[i + 1]
                if nx != x and nz != z:
                    for cx, cz in ((nx, z), (x, nz)):
                        c = stage.city
                        if (cx, int(math.floor(f2 / 2.0 - 0.01)), cz) not in stage.blocks and not c.floors(cx, cz, int(f2 / 2) - 1, int(f2 / 2) + 1) \
                                and all(c.kind(cx, ry, cz) == NONE for ry in range(int(f2 / 2), int(f2 / 2) + 2)):
                            stage.walk(cx, cz, f2 / 2.0)
    return laid


def to_points(route, path, skip_first=False):
    """A path as the driver's points: every move that isn't a run, and the turns of a run."""
    for i, (node, move) in enumerate(path):
        if skip_first and i == 0:
            continue
        x, z, f2 = node
        mv = 'run' if move in ('start', 'plank') else move
        last = i == len(path) - 1
        if mv == 'run' and not last and 0 < i:
            px, pz = path[i - 1][0][0], path[i - 1][0][1]
            nx, nz = path[i + 1][0][0], path[i + 1][0][1]
            nmv = path[i + 1][1]
            if (x - px, z - pz) == (nx - x, nz - z) and nmv in ('run', 'plank', 'start'):
                continue
        route.add(x + 0.5, f2 / 2.0, z + 0.5, mv)


def check(city, route, box):
    """The plain runner (no planks) runs the built route leg by leg between its planner-made points."""
    pts = route.points
    bad = []
    for a, b in zip(pts, pts[1:]):
        if b[3] in ('swim', 'climb', 'hang', 'shimmy', 'ledge_up', 'wallrun_l', 'wallrun_r'):
            continue  # set pieces the planner doesn't model: the tours drive them
        tx, tz = int(math.floor(b[0])), int(math.floor(b[2]))
        goal = (lambda tx, tz, fy: (lambda n: abs(n[0] - tx) <= 1 and abs(n[1] - tz) <= 1 and abs(n[2] / 2 - fy) <= 1.1))(tx, tz, b[1])
        path, cost, n = runner_path(city, box, (a[0], a[1], a[2]), goal, (tx, tz), limit=40000)
        if path is None:
            bad.append((a, b))
    return bad


# ---- the street graph (M1's) ----------------------------------------------------------------------------------------

class Streets:
    def __init__(self, plan):
        g = plan['graphs']['street']
        self.nodes = g['nodes']
        self.adj = {}
        for a, b, m, c, t in g['edges']:
            self.adj.setdefault(a, []).append((b, c, t))
            self.adj.setdefault(b, []).append((a, c, t))

    def near(self, x, z):
        return min(range(len(self.nodes)), key=lambda i: math.hypot(self.nodes[i][0] - x, self.nodes[i][2] - z))

    def path(self, a, b, avoid=None):
        dist, prev, pq = {a: 0.0}, {}, [(0.0, a)]
        while pq:
            d, u = heapq.heappop(pq)
            if u == b:
                break
            if d > dist[u]:
                continue
            for v, c, t in self.adj.get(u, []):
                if avoid and t == avoid:
                    continue
                if d + c < dist.get(v, 1e18):
                    dist[v] = d + c
                    prev[v] = u
                    heapq.heappush(pq, (d + c, v))
        out = [b]
        while out[-1] != a:
            out.append(prev[out[-1]])
        return [(self.nodes[i][0], self.nodes[i][2]) for i in reversed(out)]


def ground_feet(city, x, z, near=1.0):
    fl = city.floors(int(math.floor(x)), int(math.floor(z)), -8, 12)
    if not fl:
        return near
    return min(fl, key=lambda f: abs(f - near))


STALLS = []


def off_stalls(x, z):
    return any(x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1 for x0, z0, x1, z1 in STALLS)


def street_points(route, city, pts, move='run', near=1.0):
    """Ground waypoints joined by the planner's runs (round a stall, onto a bridge's deck): what a runner on the street does."""
    for x, z in pts:
        if not route.points:
            route.add(x, ground_feet(city, x, z, near), z, move)
            continue
        a = route.end()
        tx, tz = int(math.floor(x)), int(math.floor(z))
        if (int(math.floor(a[0])), int(math.floor(a[2]))) == (tx, tz):
            continue
        goal = (lambda tx, tz: (lambda n: abs(n[0] - tx) <= 1 and abs(n[1] - tz) <= 1 and n[2] <= 2 * 3))(tx, tz)
        path, cost, n = runner_path(city, BOX, a, goal, (tx, tz), min_feet=None, limit=60000, avoid=off_stalls)
        if path is None:
            # an unreachable waypoint (inside a stall or a doorway): skip it, the next one carries the line
            print(f'   {route.name}: skips waypoint ({x:.1f}, {z:.1f})')
            continue
        to_points(route, path, skip_first=True)


# ---- the design ---------------------------------------------------------------------------------------------------------

BOX = (-200, -170, 10, 60)
SQUARE_R = 32.0  # inside Choir Square (the arrival counts at r - 2 = 34; a little inside it)


def in_square(n):
    return math.hypot(n[0] + 0.5, n[1] + 0.5) <= SQUARE_R and n[2] <= 2 * 2


def design(city, plan):
    stage = Stage(city)
    STALLS[:] = [tuple(p['box']) for l in plan['landmarks'] if l['id'] == 'stalls' for p in l['parts']]
    stair_top = roost(stage)
    crane(stage)
    # the far bank: a ladder from the east lower quay up the quay wall (south of the Bell Bridge, where the swim lands)
    ladder(stage, -60, -4, -3, 0, 'west')
    # the canal's mouth: a ladder out of the water onto the lower quay's south side
    ladder(stage, -84, -33, -7, -4, 'north')
    streets = Streets(plan)
    routes = []

    # ---------------- Act 1 · Bellwatch roofs: the slide, then the high road over Bellwatch to the canal
    r = Route('act1_roof', 1, 'roof')
    r.add(-169.5, TERRACE_FEET, -129.5, 'run')
    r.add(-169.5, TERRACE_FEET, -126.5, 'run')
    r.add(-169.5, 28.0, -113.5, 'run')
    path, cost, n = runner_path(city, (-185, -135, -110, -15), (-169.5, 28.0, -112.5),
                                lambda n: n[0] >= -134 and -38 <= n[1] <= -24 and n[2] >= 2 * 6, (-131, -31),
                                min_feet=6.0, build=True, plank_cost=0.45)
    if path is None:
        raise SystemExit('act1_roof: no high road')
    planks = lay_planks(stage, path)
    decorate(stage, planks)
    # the route is what a runner does on the road as built (the gaps are leaps now)
    a1_goal = path[-1][0]
    path = run_built(city, (-185, -135, -110, -15), (-169.5, 28.0, -112.5), a1_goal, 'act1_roof')
    to_points(r, path)
    routes.append(r)
    a1_end = r.end()

    # ---------------- Act 2 · the Quay, rooftop: over the canal's mouth and the warehouses to the crane's arm
    r = Route('act2_roof', 2, 'roof')
    r.add(*a1_end, 'run')
    boom_w = (-78.5, 23.0, -25.5)
    path, cost, n = runner_path(city, (-140, -60, -76, 0), a1_end,
                                lambda n: n[0] in (-79, -78) and n[1] == -26 and abs(n[2] / 2 - 23.0) <= 0.6, (-78, -26),
                                min_feet=6.0, build=True, plank_cost=0.4)
    if path is None:
        raise SystemExit('act2_roof: no way to the crane')
    planks = lay_planks(stage, path)
    decorate(stage, planks)
    path = run_built(city, (-140, -60, -76, 0), a1_end, path[-1][0], 'act2_roof')
    to_points(r, path, skip_first=True)
    # the arm: run to the trolley, drop to a hang on its south face, shimmy past, climb back up, on to the tower
    r.add(-73.5, 23.0, -25.5, 'run')
    r.add(-73.5, 21.45, -24.5, 'hang')
    r.add(-67.5, 21.45, -24.5, 'shimmy')
    r.add(-67.5, 23.0, -25.5, 'ledge_up')
    r.add(-62.5, 23.0, -25.5, 'run')
    routes.append(r)
    a2_end = r.end()

    # ---------------- Act 3 · the market, rooftop: up onto the crane's cab, over the Market Hall, a drop into the square
    r = Route('act3_roof', 3, 'roof')
    r.add(*a2_end, 'run')
    # up the crane's cab by its ladder, onto its roof
    r.add(-62.5, 25.0, -25.5, 'climb')
    cab = (-60.5, 25.0, -25.5)
    r.add(*cab, 'run')
    path, cost, n = runner_path(city, (-64, -60, 0, 0), cab, in_square, (0, 0), min_feet=None, build=True, plank_cost=0.5)
    if path is None:
        raise SystemExit('act3_roof: no way into the square')
    planks = lay_planks(stage, path, gap_every=9)
    decorate(stage, planks)
    path = run_built(city, (-64, -60, 0, 0), cab, path[-1][0], 'act3_roof')
    to_points(r, path, skip_first=True)
    routes.append(r)

    # ---------------- the street routes: the couriers' stair, then M1's street route (Roost to the square)
    stair_bottom = (-177.5, 1.0, -124.5)
    sr = plan['routes']['street']
    # where the acts change along it: the canal's Lantern Bridge (the Quay) and the Mill Bridge (the market)
    def cut(pts, x, z):
        k = min(range(len(pts)), key=lambda i: math.hypot(pts[i][0] - x, pts[i][1] - z))
        return k
    k1 = cut(sr, -125, -10)
    k2 = cut(sr, -66, 40)
    r = Route('act1_street', 1, 'street')
    r.add(*stair_top[0], 'run')
    r.add(*stair_top[1], 'run')
    for k in range(5):
        pass
    # the flights (the driver runs them as runs: stairs carry the body)
    r.add(-178.5, 32.0, -125.0, 'run')
    r.add(-178.5, 32.0, -123.5, 'run')
    r.add(-170.5, 24.0, -123.5, 'run')
    r.add(-170.5, 24.0, -125.5, 'run')
    r.add(-178.5, 16.0, -125.5, 'run')
    r.add(-178.5, 16.0, -123.5, 'run')
    r.add(-170.5, 8.0, -123.5, 'run')
    r.add(-170.5, 8.0, -125.5, 'run')
    r.add(*stair_bottom, 'run')
    first = next(k for k, (x, z) in enumerate(sr) if not (-179 <= x <= -169 and -127 <= z <= -122))
    r.add(-177.5, 1.0, -121.0, 'run')
    street_points(r, city, sr[first:k1 + 1])
    routes.append(r)
    r = Route('act2_street', 2, 'street')
    street_points(r, city, sr[k1:k2 + 1])
    routes.append(r)
    r = Route('act3_street', 3, 'street')
    street_points(r, city, [p for p in sr[k2:] if math.hypot(p[0], p[1]) > 26] + [(-17.0, 15.0)])
    routes.append(r)

    # ---------------- the canal routes: down the stair, the streets to the river, the lower quay, the water
    r = Route('act1_canal', 1, 'canal')
    r.add(*stair_bottom, 'run')
    r.add(-177.5, 1.0, -121.0, 'run')
    a = streets.near(-177.5, -119.0)
    b = streets.near(-90, -142)
    street_points(r, city, streets.path(a, b))
    r.add(-88.5, 1.0, -142.5, 'run')
    r.add(-86.5, 1.0, -142.5, 'run')
    r.add(-85.5, -3.0, -142.5, 'climb')     # down the ladder to the lower quay
    r.add(-85.5, -3.0, -141.5, 'run')
    r.add(-85.5, -3.0, -41.5, 'run')        # south along the water
    routes.append(r)
    r = Route('act2_canal', 2, 'canal')
    r.add(-85.5, -3.0, -41.5, 'run')
    r.add(-84.5, -3.0, -41.5, 'run')
    r.add(-84.5, -3.0, -40.5, 'run')
    r.add(-84.5, -6.4, -37.5, 'swim')       # off the lower quay into the canal's mouth
    r.add(-84.5, -6.4, -33.6, 'swim')
    r.add(-84.5, -3.0, -33.5, 'climb')      # up the mouth's ladder
    r.add(-84.5, -3.0, -31.5, 'run')
    street_points(r, city, [(-82.5, -6.5), (-82.5, -4.5)], near=-3.0)
    r.add(-79.5, -6.4, -4.5, 'swim')        # the Bell Bridge is in the river beside you: across
    r.add(-63.5, -6.4, -4.5, 'swim')
    r.add(-63.5, -3.0, -4.5, 'climb')       # the far bank's water ladder
    r.add(-61.5, -3.0, -3.5, 'run')
    r.add(-60.5, -3.0, -3.5, 'run')
    r.add(-60.5, 1.0, -3.5, 'climb')        # and the quay wall's
    r.add(-58.5, 1.0, -3.5, 'run')
    routes.append(r)
    r = Route('act3_canal', 3, 'canal')
    r.add(-58.5, 1.0, -4.5, 'run')
    a = streets.near(-58, -6)
    b = streets.near(-28, -8)
    street_points(r, city, streets.path(a, b) + [(-22.0, -6.0)])
    routes.append(r)
    return stage, routes


def over_water(city, p, reach=2):
    """Water under this point or a step from it (a runner lets go into the river)."""
    from terrain import WATER
    x0, z0 = int(math.floor(p[0])), int(math.floor(p[2]))
    for dx in range(-reach, reach + 1):
        for dz in range(-reach, reach + 1):
            if any(city.kind(x0 + dx, ry, z0 + dz) == WATER for ry in range(-8, -4)) and not city.floors(x0 + dx, z0 + dz, -3, int(p[1])):
                return True
    return False


def draw(city, routes, stage, out):
    import numpy as np
    from PIL import Image, ImageDraw
    sys.path.insert(0, os.path.join(ROOT, 'tools', 'city'))
    S = 4
    x0, z0, x1, z1 = -200, -170, 10, 60
    img = Image.new('RGB', ((x1 - x0) * S, (z1 - z0) * S), (40, 44, 50))
    d = ImageDraw.Draw(img)
    for x in range(x0, x1):
        for z in range(z0, z1):
            g = city.ground(x, z)
            if g is None:
                continue
            v = int(max(0, min(255, 40 + g * 6)))
            d.rectangle([(x - x0) * S, (z - z0) * S, (x - x0 + 1) * S - 1, (z - z0 + 1) * S - 1], fill=(v, v, v))
    for (x, ry, z), spec in stage.blocks.items():
        if spec != 'air' and x0 <= x < x1 and z0 <= z < z1:
            d.rectangle([(x - x0) * S, (z - z0) * S, (x - x0 + 1) * S - 1, (z - z0 + 1) * S - 1], fill=(200, 140, 70))
    colours = {'roof': (255, 80, 60), 'canal': (60, 160, 255), 'street': (90, 230, 120)}
    for r in routes:
        pts = [((p[0] - x0) * S, (p[2] - z0) * S) for p in r.points]
        if len(pts) > 1:
            d.line(pts, fill=colours[r.kind], width=2)
    img.save(out)


def main():
    dump = sys.argv[1]
    city = City(dump)
    with open(PLAN, 'rb') as f:
        raw = f.read()
    plan = json.loads(raw)
    stage, routes = design(city, plan)
    for r in routes:
        moves = {}
        for p in r.points:
            moves[p[3]] = moves.get(p[3], 0) + 1
        length = sum(math.dist((a[0], a[1], a[2]), (b[0], b[1], b[2])) for a, b in zip(r.points, r.points[1:]))
        print(f'{r.name:12s} {len(r.points):3d} points, {length:6.0f} blocks, moves {moves}')
        if r.kind == 'roof':
            bad = check(city, r, BOX)
            for a, b in bad:
                print(f'   cannot run {a} -> {b}')
    # SPEC §16.4: the street is never more than 30 s away: from any point of a high route a Runner gets down to street
    # level within 30 s at a sprint (over the river the water is down: you let go)
    worst = 0.0
    for r in routes:
        if r.kind == 'street':
            continue
        for p in r.points[::2]:
            if p[1] <= 2.0 or p[3] in ('hang', 'shimmy', 'ledge_up', 'swim', 'climb'):
                continue
            if over_water(city, p):
                continue  # let go: the river is down
            goal = lambda n: n[2] <= 2 * 2.5
            # getting down at any cost: a hang from the eaves and a long fall (no death in the opening) count
            path, cost, n = runner_path(city, BOX, (p[0], p[1], p[2]), goal, (p[0], p[2]), min_feet=None, limit=60000, max_drop=18.0)
            c = cost if path is not None else 99.0
            worst = max(worst, c)
            if c > 30:
                print(f'   {r.name}: the street route is {c:.0f} s from {p[:3]}')
    print(f'the street: at most {worst:.1f} s down from any point of a high route (a Runner at a sprint)')
    stage_list = [[x, ry, z, spec] for (x, ry, z), spec in sorted(stage.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))]
    out = {'schemaVersion': 1, 'generator': 'tools/opening/routes.py', 'planSha256': hashlib.sha256(raw).hexdigest(),
           'routes': [{'name': r.name, 'act': r.act, 'kind': r.kind, 'points': r.points} for r in routes],
           'stage': stage_list}
    with open(OUT, 'w') as f:
        json.dump(out, f, separators=(',', ':'))
    print(f'wrote {OUT}: {len(routes)} routes, {len(stage_list)} stage blocks')
    if len(sys.argv) > 2:
        os.makedirs(sys.argv[2], exist_ok=True)
        draw(city, routes, stage, os.path.join(sys.argv[2], 'routes-top.png'))
        print('wrote', os.path.join(sys.argv[2], 'routes-top.png'))


if __name__ == '__main__':
    main()
