"""Carillon's street-life layer (look bible §7; Milestone 4 step B3a): the props that make a street lived in, as plan
pieces the painter places per chunk and the citizens' roster reads as places.

Called from plan.py's build() once the street graph exists: `props.build(plan, globals())`. Everything is decided from
the plan alone (the same city in every world; the painter's world seed only varies small details such as laundry).

What it places, by district (bible §5, §7):
  * stalls: clusters on Choir Square's -2 ring (between the aisles on the eight winds) and on the district plazas; each
    4 along its counter x 2 deep (posts at the corners, the counter in front, the keeper behind) under a striped canopy;
  * the Market's shop displays under the awnings (produce, crates, tables of wares, shelves), carts, wells at the
    junctions, planters, benches; fountains either side of the Bell Stone;
  * Bellwatch's handcarts, crates and casks at the doors, wells, benches, and laundry across its lanes;
  * the Quay's porters' stacks, hay carts under the warehouses, nets drying, lantern strings across the canal;
  * the Works' heaps of coal, iron and timber, carts; the Terrace's benches, notice boards and a fountain;
  * a hay cart at the foot of every bell tower (the leap of faith, bible §3.6);
  * a lamp at every junction (within the light budget: at most 3 of the plan's lamps in a chunk);
  * hang lines (laundry, bunting, lanterns, rope) across lanes between facing courses (rows 9 and 13, on the grid) and on
    the avenue crossing sites B2 marked, made square to the facades.

Street-level props keep the citizens' walking lanes clear: a prop cell stays at least (lane offset + 0.9) from every
walking edge's centre line (the roster's keep-right lanes, citizens.py edge_lanes), and off every door's way in.
"""
import math
from collections import defaultdict

import numpy as np

SIDES = {'N': (0, -1), 'E': (1, 0), 'S': (0, 1), 'W': (-1, 0)}
OPP = {'N': 'S', 'S': 'N', 'E': 'W', 'W': 'E'}
CW = {'N': 'E', 'E': 'S', 'S': 'W', 'W': 'N'}
GOODS = ['bread', 'fish', 'produce', 'cloth', 'pottery', 'candles', 'flowers', 'spice', 'tools', 'cheese']
FIELDS = ['kind', 'x0', 'z0', 'x1', 'z1', 'ry', 'facing', 'v', 's']


def cardinal(ux, uz):
    if abs(ux) >= abs(uz):
        return 'E' if ux > 0 else 'W'
    return 'S' if uz > 0 else 'N'


class Layer:
    def __init__(self, p, G):
        self.p, self.G = p, G
        self.O, self.N = G['O'], G['N_CELLS']
        self.K, self.H, self.DI, self.PID = p.kind, p.lift, p.dist, p.plot_id
        g = G
        self.WALK = set(g['WALKABLE']) - {g['LOWQUAY'], g['STAIR']}
        self.rnd = g['rnd']
        self.dname = lambda i, j: g['DISTRICTS'][int(self.DI[j, i])]
        self.taken = np.zeros(self.K.shape, bool)
        self.props = []
        self.lines = []
        self.stats = defaultdict(int)
        O = self.O
        blocked = np.zeros(self.K.shape, bool)
        blocked |= self.K == g['STAIR']
        blocked |= self.K == g['LANDMARK']
        blocked |= p.bridge_cells()
        for t in p.trees:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    self._mark(blocked, t[0] + dx, t[1] + dz)
        for x, z in p.lamps:
            self._mark(blocked, x, z)
        self.blocked = blocked
        self.bridges = p.bridge_cells()
        near_tree = np.zeros(self.K.shape, bool)
        for t in p.trees:
            # (clipped avenue trees keep their crowns under 7; the big ones reach 10 or so and spread 4)
            r = 0 if t[2] == 'avenue' else 4
            for dx in range(-r, r + 1):
                for dz in range(-r, r + 1):
                    self._mark(near_tree, t[0] + dx, t[1] + dz)
        self.near_tree = near_tree
        # doors and the cell in front of each (the way in stays clear)
        self.doors = set()
        for q in p.plots:
            if q['door']:
                dx, dz = SIDES[q['front']]
                x, z = q['door']
                for k in range(0, 3):
                    self.doors.add((x + dx * k, z + dz * k))
                for s in (-1, 1):
                    tx, tz = (1, 0) if dz else (0, 1)
                    self.doors.add((x + dx + tx * s, z + dz + tz * s))
        # the street graph's walking edges, their keep-right lane offsets, and the doors' ways in
        sg = p.street_graph
        self.nodes = sg.nodes
        self.walk_edges, self.door_edges = [], []
        for (u, v), (move, tag) in sg.edges.items():
            a, b = sg.nodes[u], sg.nodes[v]
            seg = (a[0], a[1], b[0], b[1])
            if move == 'door':
                self.door_edges.append(seg)
            else:
                self.walk_edges.append((seg, self.lane_of(seg)))
        self.grid = defaultdict(list)
        for k, (seg, lane) in enumerate(self.walk_edges):
            for key in self._keys(seg):
                self.grid[key].append(k)
        self.dgrid = defaultdict(list)
        for k, seg in enumerate(self.door_edges):
            for key in self._keys(seg):
                self.dgrid[key].append(k)
        self.degree = defaultdict(int)
        for (u, v), (move, tag) in sg.edges.items():
            if move != 'door':
                self.degree[u] += 1
                self.degree[v] += 1
        self.plot_by_id = {q['id']: q for q in p.plots}

    # -- helpers ------------------------------------------------------------------------------------------------------
    def _mark(self, m, x, z):
        i, j = x + self.O, z + self.O
        if 0 <= i < self.N and 0 <= j < self.N:
            m[j, i] = True

    @staticmethod
    def _keys(seg):
        ax, az, bx, bz = seg
        for gx in range(int(math.floor(min(ax, bx) / 8)) - 1, int(math.floor(max(ax, bx) / 8)) + 2):
            for gz in range(int(math.floor(min(az, bz) / 8)) - 1, int(math.floor(max(az, bz) / 8)) + 2):
                yield gx, gz

    @staticmethod
    def seg_dist(seg, x, z):
        ax, az, bx, bz = seg
        dx, dz = bx - ax, bz - az
        ll = dx * dx + dz * dz
        t = 0.0 if ll == 0 else max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / ll))
        return math.hypot(x - ax - t * dx, z - az - t * dz)

    def walkable_xz(self, x, z):
        # (as the roster's: the lower quay is no walking ground)
        i, j = int(math.floor(x)) + self.O, int(math.floor(z)) + self.O
        if not (0 <= i < self.N and 0 <= j < self.N):
            return False
        k = int(self.K[j, i])
        return k in self.G['WALKABLE'] and k != self.G['LOWQUAY']

    def lane_of(self, seg):
        """The roster's keep-right offset for this edge (citizens.py edge_lanes, before obstacles)."""
        ax, az, bx, bz = seg
        L = math.hypot(bx - ax, bz - az)
        if L < 0.01:
            return 0.0
        dx, dz = (bx - ax) / L, (bz - az) / L
        px, pz = -dz, dx
        half = 8.0
        for f in (0.25, 0.5, 0.75):
            x, z = ax + (bx - ax) * f, az + (bz - az) * f
            for sx, sz in ((px, pz), (-px, -pz)):
                d = 0.0
                while d < 8.0:
                    d += 0.25
                    if not self.walkable_xz(x + sx * d, z + sz * d):
                        d -= 0.25
                        break
                half = min(half, d)
        if half < 1.5:
            return 0.0
        return round(10 * max(0.0, min(1.5, 0.45 * half))) / 10.0

    def kind(self, x, z):
        i, j = x + self.O, z + self.O
        if 0 <= i < self.N and 0 <= j < self.N:
            return int(self.K[j, i])
        return self.G['VOID']

    def lift(self, x, z):
        return int(self.H[z + self.O, x + self.O])

    def dist_of(self, x, z):
        return self.G['DISTRICTS'][int(self.DI[z + self.O, x + self.O])]

    def plot_at(self, x, z):
        i, j = x + self.O, z + self.O
        if 0 <= i < self.N and 0 <= j < self.N:
            return int(self.PID[j, i])
        return -1

    def lane_clear(self, x, z, extra=0.0, pole=False):
        """Off the walking lanes: (lane offset + 0.9) from every walking edge's centre line. In Choir Square, whose
        aisles are the eight winds, 1.4 (the lanes there narrow beside a stall); a thin pole only keeps off the line."""
        cx, cz = x + 0.5, z + 0.5
        key = (int(math.floor(cx / 8)), int(math.floor(cz / 8)))
        square = self.kind(x, z) == self.G['SQUARE']
        for k in self.grid.get(key, ()):
            seg, lane = self.walk_edges[k]
            need = 0.9 if pole else (1.4 if square else lane + 0.9) + extra
            if self.seg_dist(seg, cx, cz) < need:
                return False
        for k in self.dgrid.get(key, ()):
            if self.seg_dist(self.door_edges[k], cx, cz) < 1.3:
                return False
        return True

    def free(self, x, z, kinds=None, ry=None):
        i, j = x + self.O, z + self.O
        if not (0 <= i < self.N and 0 <= j < self.N):
            return False
        k = int(self.K[j, i])
        if kinds is not None:
            if k not in kinds:
                return False
        elif k not in self.WALK:
            return False
        if self.taken[j, i] or self.blocked[j, i] or (x, z) in self.doors:
            return False
        if ry is not None and int(self.H[j, i]) != ry:
            return False
        return True

    def cells_ok(self, cells, kinds=None, lanes=True, extra=0.0):
        if not cells:
            return False
        ry = self.lift(*cells[0])
        for x, z in cells:
            if not self.free(x, z, kinds, ry):
                return False
            if lanes and not self.lane_clear(x, z, extra):
                return False
        return True

    def take(self, cells):
        for x, z in cells:
            self._mark(self.taken, x, z)

    def add(self, kind, cells, facing='', v=0, s='', ry=None, take=True):
        xs = [c[0] for c in cells]
        zs = [c[1] for c in cells]
        if ry is None:
            ry = self.lift(cells[0][0], cells[0][1])
        self.props.append([kind, min(xs), min(zs), max(xs), max(zs), int(ry), facing, int(v), s])
        if take:
            self.take(cells)
        self.stats[kind] += 1

    def wall_side(self, x, z):
        """The side (N/E/S/W) of the first plot or landmark wall touching this cell, or None."""
        for sd, (dx, dz) in SIDES.items():
            k = self.kind(x + dx, z + dz)
            if k == self.G['PLOT'] or k == self.G['LANDMARK']:
                return sd
        return None

    def open_width(self, x, z, sd):
        """Open walkable cells from (x, z) away from the wall on side sd (the street's width there)."""
        dx, dz = SIDES[OPP[sd]]
        n = 0
        while n < 12 and self.kind(x + dx * n, z + dz * n) in self.G['WALKABLE']:
            n += 1
        return n

    def chunk_lamps(self):
        c = defaultdict(int)
        for x, z in self.p.lamps:
            c[(x >> 4, z >> 4)] += 1
        return c

    # -- the run ------------------------------------------------------------------------------------------------------
    def run(self):
        self.fountains()
        self.square_stalls()
        self.lamps()
        self.wells()
        self.plaza_stalls()
        self.taverns()
        self.hay_carts()
        self.market_displays()
        self.benches()
        self.carts()
        self.door_clutter()
        self.planters()
        self.notice_boards()
        self.works_heaps()
        self.quay_nets()
        self.crossing_lines()
        self.lane_lines()
        self.canal_lines()

    # -- the square ----------------------------------------------------------------------------------------------------
    def fountains(self):
        """A fountain either side of the Bell Stone (bible §5.3), one in the Association's forecourt, one in the Garden."""
        G = self.G
        sites = [((-7, 3), {G['SQUARE']}), ((7, 3), {G['SQUARE']}), ((0, -121), {G['PLAZA']}), ((152, 49), {G['PLAZA']}),
                 ((-150, -52), {G['PLAZA']})]
        for (cx, cz), kinds in sites:
            done = False
            for r in range(0, 5):
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if done or max(abs(dx), abs(dz)) != r:
                            continue
                        x0, z0 = cx + dx - 2, cz + dz - 2
                        cells = [(x, z) for x in range(x0, x0 + 5) for z in range(z0, z0 + 5)]
                        if any(self.wall_side(cx, cz) is not None for cx, cz in cells):
                            continue
                        if self.cells_ok(cells, kinds, lanes=True, extra=-0.2):
                            self.add('fountain', cells, v=len([q for q in self.props if q[0] == 'fountain']))
                            done = True
            self.stats['fountain_sites'] += 1

    def stall_cells(self, cx, cz, facing):
        """A stall of 4 along its counter x 2 deep with its front row (customers), the box anchored at (cx, cz)."""
        fx, fz = SIDES[facing]
        tx, tz = (1, 0) if fx == 0 else (0, 1)
        box, front = [], []
        for a in range(4):
            for d in range(2):
                box.append((cx + tx * a - fx * d, cz + tz * a - fz * d))
            front.append((cx + tx * a + fx, cz + tz * a + fz))
        return box, front

    def try_stall(self, cx, cz, facing, kinds, good, colour, lanes=True):
        box, front = self.stall_cells(cx, cz, facing)
        ry = self.lift(cx, cz)
        if not self.cells_ok(box, kinds, lanes=lanes):
            return False
        for x, z in front:
            if not self.free(x, z, kinds, ry):
                return False
        # a cell's gap to every other stall (hop between at 1-2)
        for x, z in box:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if self.taken[z + dz + self.O, x + dx + self.O] and (x + dx, z + dz) not in box:
                        return False
        self.add('stall', box, facing=facing, v=colour, s=good)
        self.take(front)  # the customers stand there (no other prop)
        return True

    def square_stalls(self):
        """Clusters of three stalls on the bowl's -2 ring, between the aisles on the eight winds, facing out."""
        G = self.G
        k = 0
        for sector in range(8):
            centre = math.radians(sector * 45 + 22.5)
            placed = 0
            cands = []
            for x in range(-24, 25):
                for z in range(-24, 25):
                    for facing in 'NESW':
                        box, front = self.stall_cells(x, z, facing)
                        mx = sum(c[0] for c in box) / 8.0 + 0.5
                        mz = sum(c[1] for c in box) / 8.0 + 0.5
                        r = math.hypot(mx, mz)
                        if not (15.5 <= r <= 20.5):
                            continue
                        a = math.atan2(mz, mx)
                        da = abs((a - centre + math.pi) % (2 * math.pi) - math.pi)
                        if da > math.radians(17):
                            continue
                        # facing out: the front away from the Bell Stone
                        fx, fz = SIDES[facing]
                        if fx * mx + fz * mz < 0.5 * r:
                            continue
                        cands.append((da + abs(r - 18) * 0.02, x, z, facing))
            cands.sort()
            for _, x, z, facing in cands:
                if placed == 3:
                    break
                if self.try_stall(x, z, facing, {G['SQUARE']}, GOODS[k % len(GOODS)], k % 6):
                    placed += 1
                    k += 1
            self.stats['square_sectors_short'] += 3 - placed

    def plaza_stalls(self):
        """A few stalls round each district's bell tower: fish on the Quay, produce and bread elsewhere."""
        G = self.G
        goods = {'bellwatch': ['bread', 'produce', 'candles'], 'quay': ['fish', 'fish', 'tools'],
                 'works': ['tools', 'bread'], 'garden': ['flowers', 'produce', 'cheese'], 'terrace': [],
                 'wallring': []}
        k = 0
        for key, (bx, bz) in self.p.bells.items():
            want = goods.get(key, [])
            placed = 0
            cands = []
            for x in range(bx - 13, bx + 13):
                for z in range(bz - 13, bz + 13):
                    for facing in 'NESW':
                        box, front = self.stall_cells(x, z, facing)
                        mx = sum(c[0] for c in box) / 8.0 + 0.5 - bx
                        mz = sum(c[1] for c in box) / 8.0 + 0.5 - bz
                        fx, fz = SIDES[facing]
                        # facing the tower across the plaza
                        if fx * mx + fz * mz > -0.5 * math.hypot(mx, mz):
                            continue
                        r = math.hypot(mx, mz)
                        cands.append((abs(r - 10.0) + 0.01 * (x + z), x, z, facing))
            cands.sort()
            for _, x, z, facing in cands:
                if placed == len(want):
                    break
                if self.try_stall(x, z, facing, {G['PLAZA']}, want[placed], (k * 5 + 3) % 6):
                    placed += 1
                    k += 1
            self.stats['plaza_stalls_short'] += len(want) - placed

    # -- lamps, wells --------------------------------------------------------------------------------------------------
    def lamps(self):
        """A lamp at every junction (bible §7) where none stands within 14, at a corner off the walking lanes, never a
        fourth of the plan's lamps in one chunk (the light budget: at most 4-5 sources a chunk)."""
        per = self.chunk_lamps()
        lamps = list(self.p.lamps)
        nodes = sorted((n for n, d in self.degree.items() if d >= 3 and self.nodes[n][3] == 'junction'),
                       key=lambda n: (round(self.nodes[n][0]), round(self.nodes[n][1])))
        added = 0
        for n in nodes:
            nx, nz = self.nodes[n][0], self.nodes[n][1]
            if math.hypot(nx, nz) < 14:
                continue   # the Bell Stone's floor stays clear (the gate opens there)
            if any(math.hypot(lx + 0.5 - nx, lz + 0.5 - nz) < 14 for lx, lz in lamps):
                continue
            best = None
            for r in (2, 3, 4):
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if max(abs(dx), abs(dz)) != r:
                            continue
                        x, z = int(math.floor(nx)) + dx, int(math.floor(nz)) + dz
                        if not self.free(x, z) or not self.lane_clear(x, z, 0.1):
                            continue
                        if per[(x >> 4, z >> 4)] >= 3:
                            continue
                        sc = abs(math.hypot(x + 0.5 - nx, z + 0.5 - nz) - 2.5) + 0.001 * (dx * 7 + dz)
                        if best is None or sc < best[0]:
                            best = (sc, x, z)
                if best:
                    break
            if best is None:
                self.stats['junction_no_lamp'] += 1
                continue
            _, x, z = best
            lamps.append([x, z])
            per[(x >> 4, z >> 4)] += 1
            self._mark(self.blocked, x, z)
            added += 1
        self.p.lamps[:] = lamps
        self.stats['lamps_added'] = added

    def wells(self):
        """A well at each Market junction off the square, in Bellwatch's squares and one or two in every other district."""
        G = self.G
        quota = {'market': 8, 'bellwatch': 5, 'quay': 2, 'works': 2, 'wallring': 3, 'garden': 2, 'terrace': 0}
        got = defaultdict(int)
        placed = []
        nodes = sorted((n for n, d in self.degree.items() if d >= 3), key=lambda n: (round(self.nodes[n][0]), round(self.nodes[n][1])))
        order = sorted(nodes, key=lambda n: self.rnd(n, 9301))
        for n in order:
            nx, nz = self.nodes[n][0], self.nodes[n][1]
            i, j = int(math.floor(nx)) + self.O, int(math.floor(nz)) + self.O
            if not (0 <= i < self.N and 0 <= j < self.N):
                continue
            dn = self.G['DISTRICTS'][int(self.DI[j, i])]
            if got[dn] >= quota.get(dn, 0) or math.hypot(nx, nz) < 40:
                continue
            if any(math.hypot(nx - a, nz - b) < 30 for a, b in placed):
                continue
            done = False
            for r in range(2, 6):
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if done or max(abs(dx), abs(dz)) != r:
                            continue
                        x0, z0 = int(math.floor(nx)) + dx - 1, int(math.floor(nz)) + dz - 1
                        cells = [(x, z) for x in range(x0, x0 + 3) for z in range(z0, z0 + 3)]
                        if any(self.wall_side(cx, cz) is not None for cx, cz in cells):
                            continue
                        if self.cells_ok(cells, self.WALK | {G['YARD']}, extra=0.2):
                            self.add('well', cells, v=int(self.rnd(x0, z0, 9302) * 4))
                            got[dn] += 1
                            placed.append((nx, nz))
                            done = True
        self.stats['wells_by_district'] = dict(got)

    # -- taverns, hay carts ----------------------------------------------------------------------------------------------
    def taverns(self):
        """A table with benches each side outside every tavern (the evening spills into the lane), casks by the door."""
        G = self.G
        for q in self.p.plots:
            if q['use'] != 'tavern' or not q['door']:
                continue
            dx, dz = SIDES[q['front']]
            tx, tz = (1, 0) if dz else (0, 1)
            x, z = q['door']
            done = False
            for along in (3, -3, 4, -4, 5, -5, 6, -6):
                if done:
                    break
                for out in (1, 2):
                    # table cells along the wall, a bench each side (along the wall's normal)
                    bx, bz = x + tx * along + dx * (out + 1), z + tz * along + dz * (out + 1)
                    table = [(bx, bz), (bx + tx, bz + tz)]
                    near = [(bx - dx, bz - dz), (bx + tx - dx, bz + tz - dz)]
                    far = [(bx + dx, bz + dz), (bx + tx + dx, bz + tz + dz)]
                    cells = table + near + far
                    if self.cells_ok(cells, self.WALK | {G['YARD']}):
                        self.add('tavern_table', cells, facing=q['front'], v=q['id'] % 4, s=str(q['id']))
                        done = True
                        break
            if not done:
                # no room for a table: a bench against the wall beside the door
                for along in (2, -3, 3, -4):
                    cells = [(x + tx * along + dx, z + tz * along + dz), (x + tx * (along + 1) + dx, z + tz * (along + 1) + dz)]
                    if self.cells_ok(cells, self.WALK | {G['YARD']}, extra=-0.2):
                        self.add('bench', cells, facing=q['front'], v=1, s='tavern:' + str(q['id']))
                        break
            # casks by the door
            for s in (2, -2):
                cx, cz = x + tx * s + dx, z + tz * s + dz
                if self.cells_ok([(cx, cz)], self.WALK | {G['YARD']}):
                    self.add('stack', [(cx, cz)], facing=q['front'], v=2, s='casks')
                    break
            self.stats['tavern_tables' if done else 'tavern_no_table'] += 1

    def cart_cells(self, x, z, along):
        """A cart's two cells: the body at (x, z), the shafts one step along."""
        dx, dz = SIDES[along]
        return [(x, z), (x + dx, z + dz)]

    def hay_carts(self):
        """A hay cart at the foot of every bell tower and the Choir, and under the Quay's warehouse hoists."""
        G = self.G
        towers = []
        for lm in self.p.landmarks:
            for part in lm['parts']:
                if part['kind'] in ('belltower', 'spire'):
                    towers.append(part['box'])
        for x0, z0, x1, z1 in towers:
            done = False
            for sd in 'SENW':
                if done:
                    break
                dx, dz = SIDES[sd]
                along = CW[sd]
                ax, az = SIDES[along]
                # the strip of cells against the tower's face on side sd
                if sd in 'NS':
                    line = [(x, z1 + 1 if sd == 'S' else z0 - 1) for x in range(x0, x1 + 1)]
                else:
                    line = [(x1 + 1 if sd == 'E' else x0 - 1, z) for z in range(z0, z1 + 1)]
                for x, z in line[1:-1]:
                    cells = self.cart_cells(x, z, along)
                    if self.cells_ok(cells, self.WALK | {G['YARD'], G['PLAZA']}):
                        self.add('hay_cart', cells, facing=along)
                        done = True
                        break
            self.stats['hay_tower' if done else 'hay_tower_missed'] += 1
        # the Quay: at the gable-front warehouses (3+ storeys) along the promenade, about one in five
        n = 0
        for q in sorted(self.p.plots, key=lambda q: q['id']):
            if q['district'] != 'quay' or q['storeys'] < 3 or not q['door'] or self.rnd(q['id'], 9311) > 0.3:
                continue
            dx, dz = SIDES[q['front']]
            tx, tz = (1, 0) if dz else (0, 1)
            x, z = q['door']
            for s in (2, -3, 3, -4):
                cx, cz = x + tx * s + dx, z + tz * s + dz
                along = cardinal(tx, tz) if s > 0 else cardinal(-tx, -tz)
                cells = self.cart_cells(cx, cz, along)
                if self.cells_ok(cells):
                    self.add('hay_cart', cells, facing=along)
                    n += 1
                    break
        self.stats['hay_quay'] = n

    # -- the Market's shopfronts -----------------------------------------------------------------------------------------
    def front_cells(self, q):
        """The outside-plane cells along a plot's front (1 out), with t along the face; corners skipped."""
        x0, z0, x1, z1 = q['box']
        sd = q['front']
        dx, dz = SIDES[sd]
        if sd in 'NS':
            row = z0 - 1 if sd == 'N' else z1 + 1
            return [(x - x0, x, row) for x in range(x0 + 1, x1)]
        col = x0 - 1 if sd == 'W' else x1 + 1
        return [(z - z0, col, z) for z in range(z0 + 1, z1)]

    def market_displays(self):
        """Goods under the awnings: produce, crates, tables of wares and shelves along every Market shopfront."""
        kinds = ['produce', 'produce', 'crate', 'table', 'table', 'shelf']
        for q in sorted(self.p.plots, key=lambda q: q['id']):
            if q['district'] != 'market' or q['rank'] > 2 or not q['door']:
                continue
            dx, dz = SIDES[q['front']]
            doorx, doorz = q['door']
            cells = [(t, x, z) for t, x, z in self.front_cells(q)
                     if abs((x - dx) - doorx) + abs((z - dz) - doorz) >= 2]
            if len(cells) < 2:
                continue
            want = min(4, len(cells) - (1 if self.rnd(q['id'], 9321) < 0.3 else 0))
            n = 0
            for t, x, z in sorted(cells, key=lambda c: self.rnd(q['id'], c[0], 9322)):
                if n >= want:
                    break
                if not self.cells_ok([(x, z)], extra=-0.25):
                    continue
                kind = kinds[int(self.rnd(q['id'], t, 9323) * len(kinds))]
                good = GOODS[int(self.rnd(q['id'], 9324) * len(GOODS))]
                v = int(self.rnd(q['id'], t, 9325) * 6)
                self.add('display', [(x, z)], facing=q['front'], v=v, s=kind + ':' + good)
                n += 1
            self.stats['display_plots'] += 1 if n else 0

    # -- benches, carts, door clutter ------------------------------------------------------------------------------------
    def wall_runs(self, length, kinds, district=None):
        """Runs of `length` free cells against a wall (plot or landmark), along it: (cells, wall side, plot id)."""
        G = self.G
        out = []
        idx = np.argwhere(np.isin(self.K, list(kinds)))
        for j, i in idx:
            x, z = int(i) - self.O, int(j) - self.O
            if district and self.G['DISTRICTS'][int(self.DI[j, i])] not in district:
                continue
            sd = self.wall_side(x, z)
            if sd is None:
                continue
            wx, wz = SIDES[sd]
            tx, tz = (1, 0) if wz else (0, 1)
            cells = [(x + tx * k, z + tz * k) for k in range(length)]
            ok = all(self.kind(cx + wx, cz + wz) in (G['PLOT'], G['LANDMARK']) for cx, cz in cells)
            if ok:
                out.append((cells, sd, self.plot_at(x + wx, z + wz)))
        return out

    def benches(self):
        """Benches against walls on the squares, plazas and wide streets (two places to sit each), and on the bowl's
        outer ring facing the Choir."""
        G = self.G
        quota = {'market': 10, 'bellwatch': 8, 'quay': 6, 'terrace': 10, 'garden': 8, 'works': 4, 'wallring': 4}
        got = defaultdict(int)
        placed = []
        runs = self.wall_runs(2, {G['PLAZA'], G['STREET'], G['AVENUE'], G['QUAY'], G['RING']})
        runs.sort(key=lambda r: self.rnd(r[0][0][0], r[0][0][1], 9331))
        for cells, sd, pid in runs:
            dn = self.dist_of(*cells[0])
            if got[dn] >= quota.get(dn, 0):
                continue
            if any(math.hypot(cells[0][0] - a, cells[0][1] - b) < 14 for a, b in placed):
                continue
            if self.open_width(cells[0][0], cells[0][1], sd) < 5 or not self.cells_ok(cells, extra=0.1):
                continue
            self.add('bench', cells, facing=OPP[sd], v=int(self.rnd(cells[0][0], cells[0][1], 9332) * 2))
            got[dn] += 1
            placed.append(cells[0])
        # the bowl: benches on the -1 ring in the south half, looking up at the Choir
        n = 0
        for x in range(-26, 27, 6):
            for z in (25, 27):
                cells = [(x, z), (x + 1, z)]
                r = math.hypot(x + 1, z + 0.5)
                if r < 24 or r > 30:
                    continue
                if self.cells_ok(cells, {G['SQUARE']}, extra=0.1):
                    self.add('bench', cells, facing='N', v=0)
                    n += 1
                    break
        got['square'] = n
        self.stats['benches_by_district'] = dict(got)

    def carts(self):
        """Handcarts and market carts parked against the walls, along them, spread out."""
        quota = {'market': 12, 'bellwatch': 14, 'quay': 6, 'works': 10, 'wallring': 6, 'garden': 3, 'terrace': 2}
        loads = {'market': ['produce', 'crates', 'produce', 'sacks'], 'bellwatch': ['sacks', 'empty', 'casks', 'crates'],
                 'quay': ['crates', 'casks', 'crates'], 'works': ['crates', 'casks', 'empty'], 'wallring': ['sacks', 'empty'],
                 'garden': ['produce', 'empty'], 'terrace': ['crates']}
        G = self.G
        got = defaultdict(int)
        placed = []
        runs = self.wall_runs(2, {G['STREET'], G['LANE'], G['AVENUE'], G['PLAZA'], G['QUAY'], G['RING']})
        runs.sort(key=lambda r: self.rnd(r[0][0][0], r[0][0][1], 9341))
        for cells, sd, pid in runs:
            dn = self.dist_of(*cells[0])
            if got[dn] >= quota.get(dn, 0):
                continue
            if any(math.hypot(cells[0][0] - a, cells[0][1] - b) < 18 for a, b in placed):
                continue
            if self.open_width(cells[0][0], cells[0][1], sd) < 4 or not self.cells_ok(cells):
                continue
            along = cardinal(cells[1][0] - cells[0][0], cells[1][1] - cells[0][1])
            if self.rnd(cells[0][0], cells[0][1], 9342) < 0.5:
                cells = [cells[1], cells[0]]
                along = OPP[along]
            ld = loads.get(dn, ['empty'])
            self.add('cart', cells, facing=along, s=ld[int(self.rnd(cells[0][0], cells[0][1], 9343) * len(ld))])
            got[dn] += 1
            placed.append(cells[0])
        self.stats['carts_by_district'] = dict(got)

    def door_clutter(self):
        """Crates, sacks and casks beside the doors (Bellwatch 40%, the Quay's porters' stacks, the Works' yards...)."""
        pct = {'bellwatch': 0.40, 'quay': 0.65, 'works': 0.5, 'wallring': 0.4, 'garden': 0.15, 'market': 0.35, 'terrace': 0.0}
        stacks = {'bellwatch': ['crate', 'casks', 'sacks', 'crate_sack', 'crates2'],
                  'quay': ['crates2', 'crates2', 'casks', 'sacks', 'crate_sack'], 'works': ['crates2', 'casks', 'crate'],
                  'wallring': ['crate', 'sacks', 'casks'], 'garden': ['produce'], 'market': ['crate_sack', 'sacks', 'crates2']}
        n = 0
        for q in sorted(self.p.plots, key=lambda q: q['id']):
            dn = q['district']
            if not q['door'] or self.rnd(q['id'], 9351) >= pct.get(dn, 0):
                continue
            dx, dz = SIDES[q['front']]
            tx, tz = (1, 0) if dz else (0, 1)
            x, z = q['door']
            for s in ((2, -2) if self.rnd(q['id'], 9352) < 0.5 else (-2, 2)):
                cx, cz = x + tx * s + dx, z + tz * s + dz
                if self.cells_ok([(cx, cz)]):
                    opts = stacks.get(dn, ['crate'])
                    self.add('stack', [(cx, cz)], facing=q['front'], v=int(self.rnd(q['id'], 9353) * 4),
                             s=opts[int(self.rnd(q['id'], 9354) * len(opts))])
                    n += 1
                    # the Quay's porters stack two side by side
                    if dn == 'quay':
                        c2 = (cx + tx * (1 if s > 0 else -1), cz + tz * (1 if s > 0 else -1))
                        if self.cells_ok([c2]):
                            self.add('stack', [c2], facing=q['front'], v=int(self.rnd(q['id'], 9355) * 4), s='crates2')
                    break
        self.stats['door_stacks'] = n

    def planters(self):
        """Planters at the Market's party walls and along the Terrace's parapet walk; flower beds by Garden doors."""
        quota = {'market': 40, 'terrace': 24, 'garden': 16, 'bellwatch': 6}
        G = self.G
        got = defaultdict(int)
        placed = []
        runs = self.wall_runs(1, {G['STREET'], G['AVENUE'], G['PLAZA'], G['LANE'], G['RING']})
        runs.sort(key=lambda r: self.rnd(r[0][0][0], r[0][0][1], 9361))
        for cells, sd, pid in runs:
            dn = self.dist_of(*cells[0])
            if got[dn] >= quota.get(dn, 0):
                continue
            x, z = cells[0]
            if any(abs(x - a) + abs(z - b) < 7 for a, b in placed):
                continue
            # at a party wall (the next cell along is another plot's front) in the Market; anywhere else
            if dn == 'market':
                wx, wz = SIDES[sd]
                tx, tz = (1, 0) if wz else (0, 1)
                here = self.plot_at(x + wx, z + wz)
                if self.plot_at(x + wx + tx, z + wz + tz) == here and self.plot_at(x + wx - tx, z + wz - tz) == here:
                    continue
            if self.open_width(x, z, sd) < 4 or not self.cells_ok(cells):
                continue
            style = 'stone' if dn in ('terrace', 'market') and self.rnd(x, z, 9362) < 0.6 else 'wood'
            bloom = int(self.rnd(x, z, 9363) * 7)
            self.add('planter', cells, facing=OPP[sd], v=bloom, s=style)
            got[dn] += 1
            placed.append((x, z))
        self.stats['planters_by_district'] = dict(got)

    def notice_boards(self):
        """The Association's notices in its forecourt, and one on each district plaza."""
        G = self.G
        sites = [(-12, -128), (12, -128)] + [(bx + 9, bz) for bx, bz in self.p.bells.values()] + [(-30, 10)]
        for cx, cz in sites:
            done = False
            for r in range(0, 6):
                for dx in range(-r, r + 1):
                    for dz in range(-r, r + 1):
                        if done or max(abs(dx), abs(dz)) != r:
                            continue
                        x, z = cx + dx, cz + dz
                        for facing in 'SNEW':
                            fx, fz = SIDES[facing]
                            if done:
                                break
                            # the board, and a cell in front of it to read from
                            if self.cells_ok([(x, z)], extra=0.2) and self.free(x + fx, z + fz):
                                self.add('notice', [(x, z)], facing=facing)
                                done = True
            self.stats['notice' if done else 'notice_missed'] += 1

    def works_heaps(self):
        """The Works' yards: coal and iron heaps, timber stacks, a grindstone."""
        G = self.G
        n = 0
        placed = []
        idx = np.argwhere((self.K == G['YARD']) & (self.DI == G['D']['works']))
        cands = sorted(((int(i) - self.O, int(j) - self.O) for j, i in idx), key=lambda c: self.rnd(c[0], c[1], 9371))
        for x, z in cands:
            if n >= 30:
                break
            if any(abs(x - a) + abs(z - b) < 9 for a, b in placed):
                continue
            cells = [(x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)]
            # (two high: never in a facade's outside plane, where a hold may stand only in an odd row)
            if any(self.wall_side(cx, cz) is not None for cx, cz in cells):
                continue
            if not any(self.wall_side(cx + 2 * ex, cz + 2 * ez) for cx, cz in cells for ex, ez in SIDES.values()):
                continue
            if not self.cells_ok(cells, {G['YARD']}, lanes=False):
                continue
            kind = ['coal', 'iron', 'timber', 'timber', 'coal'][int(self.rnd(x, z, 9372) * 5)]
            self.add('heap', cells, v=int(self.rnd(x, z, 9373) * 4), s=kind)
            placed.append((x, z))
            n += 1
        self.stats['heaps'] = n

    def quay_nets(self):
        """Nets drying on frames along the quays (a pole each end), parallel to the water's edge."""
        G = self.G
        n = 0
        placed = []
        idx = np.argwhere(self.K == G['QUAY'])
        cands = sorted(((int(i) - self.O, int(j) - self.O) for j, i in idx), key=lambda c: self.rnd(c[0], c[1], 9381))
        for x, z in cands:
            if n >= 10:
                break
            if any(abs(x - a) + abs(z - b) < 24 for a, b in placed):
                continue
            # next to the drop to the lower quay or the water, running along it
            edge = None
            for sd, (dx, dz) in SIDES.items():
                k = self.kind(x + dx, z + dz)
                if k == G['LOWQUAY'] or k in G['WATER']:
                    edge = sd
            if edge is None:
                continue
            ex, ez = SIDES[edge]
            tx, tz = (1, 0) if ez else (0, 1)
            cells = [(x + tx * k, z + tz * k) for k in range(4)]
            if not all(self.kind(cx + ex, cz + ez) in (G['LOWQUAY'],) or self.kind(cx + ex, cz + ez) in G['WATER'] for cx, cz in cells):
                continue
            if not self.cells_ok(cells):
                continue
            self.add('net', cells, facing=edge)
            placed.append((x, z))
            n += 1
        self.stats['nets'] = n

    # -- lines across the streets ----------------------------------------------------------------------------------------
    def add_line(self, cells, ry, hang, v, poles='', axis='x'):
        """A hang line over these cells (a straight run along x or z) at row ry; poles at its a (low) or b (high) end.
        The variant packs the set (0-3) and the axis (+4 along z), so a one-cell line still knows its way."""
        xs = [c[0] for c in cells]
        zs = [c[1] for c in cells]
        self.props.append(['line', min(xs), min(zs), max(xs), max(zs), int(ry), poles, int(v) + (4 if axis == 'z' else 0), hang])
        for c in ((cells[0],) if 'a' in poles else ()) + ((cells[-1],) if 'b' in poles else ()):
            self._mark(self.taken, *c)
        self.lines.append((cells, ry))
        self.stats['line_' + hang] += 1

    def line_room(self, cells, ry):
        """A line's cells are open sky over walkable ground (or water), well clear of the ground under them."""
        G = self.G
        for x, z in cells:
            k = self.kind(x, z)
            if not (k in G['WALKABLE'] or k in G['WATER'] or k in (G['LOWQUAY'], G['GARDEN'], G['YARD'], G['FREE'])):
                return False
            i, j = x + self.O, z + self.O
            # trees (their crowns) and bridges stand in the way; lamps (heads at 4) are low enough
            if self.near_tree[j, i] or self.bridges[j, i]:
                return False
            if int(self.H[j, i]) > ry - 4:
                return False
            for c2, r2 in self.lines:
                if abs(r2 - ry) <= 1 and any(abs(a - x) + abs(b - z) <= 1 for a, b in c2):
                    return False
        return True

    def crossing_lines(self):
        """The avenue crossing sites B2 marked: hung square to the facade at the site's end a (or b), across the avenue
        to the facing course or to a pole at the far kerb."""
        G = self.G
        hung = 0
        for ci, cs in enumerate(self.p.crossing_sites):
            if cs['kind'] != 'line':
                continue
            best = None
            for end, other in (('a', 'b'), ('b', 'a')):
                pid = cs['plots'][0 if end == 'a' else 1]
                ex, ery, ez = cs[end]
                ox, ory, oz = cs[other]
                # march square to the axis that runs most across the avenue
                ux, uz = ox - ex, oz - ez
                second = cardinal(0, uz) if abs(ux) >= abs(uz) else cardinal(ux, 0)
                for sd, off in [(cardinal(ux, uz), o) for o in (0, 1, -1, 2, -2, 3, -3, 4, -4)] + \
                               [(second, o) for o in (0, 1, -1, 2, -2, 3, -3)]:
                    dx, dz = SIDES[sd]
                    tx, tz = (1, 0) if dz else (0, 1)
                    x, z = int(math.floor(ex)) + tx * off, int(math.floor(ez)) + tz * off
                    # step back to the facade (a plot cell) and forward out of it
                    k = 0
                    while k < 3 and self.kind(x, z) == G['PLOT']:
                        x, z = x + dx, z + dz
                        k += 1
                    k = 0
                    while k < 4 and self.kind(x, z) not in (G['PLOT'],) and self.kind(x - dx, z - dz) != G['PLOT']:
                        x, z = x - dx, z - dz
                        k += 1
                    if self.kind(x - dx, z - dz) != G['PLOT']:
                        continue
                    a_plot = self.plot_at(x - dx, z - dz)
                    # a front garden between the facade and the avenue: the line starts at the facade
                    lead = []
                    while self.kind(x, z) in (G['GARDEN'], G['YARD']) and len(lead) < 4:
                        lead.append((x, z))
                        x, z = x + dx, z + dz
                    cells = []
                    cx, cz = x, z
                    hit = None
                    while len(cells) < 18:
                        kk = self.kind(cx, cz)
                        if kk == G['PLOT']:
                            hit = self.plot_at(cx, cz)
                            break
                        if not (kk in G['WALKABLE'] or kk in G['WATER'] or kk == G['LOWQUAY']):
                            break
                        cells.append((cx, cz))
                        cx, cz = cx + dx, cz + dz
                    cells = lead + cells
                    if len(cells) < 3:
                        continue
                    pa = self.plot_by_id.get(a_plot)
                    if pa is None:
                        continue
                    if hit is not None:
                        pb = self.plot_by_id[hit]
                        if pb['lift'] != pa['lift']:
                            continue
                        line, poles = cells[1:-1], ''
                    else:
                        # a pole at the far kerb: the last open cell (free ground, off the walking lanes)
                        pb = pa
                        line, poles = cells[1:], ('b' if sd in 'ES' else 'a')
                        px_, pz_ = cells[-1]
                        if not (self.free(px_, pz_, self.WALK | {G['GARDEN'], G['YARD']}) and self.lane_clear(px_, pz_, pole=True)):
                            continue
                    if len(line) < 2:
                        continue
                    run = sorted(line)
                    # the first-floor course (row 9), else the next course up where the ground climbs under the line
                    for r in (9, 13, 17):
                        ry = pa['lift'] + r
                        if r > min(pa['eave'], pb['eave']) or not self.line_room(run, ry):
                            continue
                        sc = abs(off) + (0 if hit is not None else 2) + (0 if sd == cardinal(ux, uz) else 3) + (r - 9) / 2
                        if best is None or sc < best[0]:
                            best = (sc, run, ry, poles, 'x' if dx else 'z')
                        break
                if best is not None and best[0] == 0:
                    break
            if best is None:
                best = self.pole_to_pole(cs)
            if best is None:
                self.stats['crossing_unhung'] += 1
                cs['hung'] = None
                continue
            _, run, ry, poles, axis = best
            hang = ['bunting', 'lanterns', 'bunting', 'laundry'][ci % 4]
            self.add_line(run, ry, hang, ci % 4, poles, axis)
            cs['hung'] = [run[0][0], run[0][1], run[-1][0], run[-1][1], ry]
            hung += 1
        self.stats['crossings_hung'] = hung

    def pole_to_pole(self, cs):
        """The fallback for a crossing site with no facade square to it: a line from a pole at one kerb to a pole at the
        other, across the avenue near the site, nine over the higher kerb."""
        G = self.G
        ax, az = cs['at']
        ux, uz = cs['b'][0] - cs['a'][0], cs['b'][2] - cs['a'][2]
        best = None
        for sd in (cardinal(ux, uz), cardinal(0, uz) if abs(ux) >= abs(uz) else cardinal(ux, 0)):
            dx, dz = SIDES[sd]
            tx, tz = (1, 0) if dz else (0, 1)
            for off in (0, 2, -2, 4, -4, 6, -6):
                x, z = int(math.floor(ax)) + tx * off, int(math.floor(az)) + tz * off
                if not self.walkable_xz(x + 0.5, z + 0.5):
                    continue
                while self.walkable_xz(x - dx + 0.5, z - dz + 0.5):
                    x, z = x - dx, z - dz
                cells = []
                while self.walkable_xz(x + 0.5, z + 0.5) and len(cells) < 18:
                    cells.append((x, z))
                    x, z = x + dx, z + dz
                if len(cells) < 4 or len(cells) >= 18:
                    continue
                a, b = cells[0], cells[-1]
                kinds = self.WALK | {G['GARDEN'], G['YARD']}
                if not all(self.free(*c, kinds) and self.lane_clear(*c, pole=True) for c in (a, b)):
                    continue
                ry = max(self.lift(*a), self.lift(*b)) + 9
                run = sorted(cells)
                if not self.line_room(run[1:-1], ry):
                    continue
                sc = abs(off) + 10
                if best is None or sc < best[0]:
                    best = (sc, run, ry, 'ab', 'x' if dx else 'z')
        return best

    def lane_lines(self):
        """Lines across lanes and streets between facing facades, on the grid (rows 9 and 13, both courses): laundry
        webs in Bellwatch and the Wallring, bunting along the Market's spokes, a few lantern strings."""
        G = self.G
        style = {'bellwatch': (['laundry'] * 6 + ['rope', 'bunting'], 5, 0.8), 'wallring': (['laundry'] * 5 + ['rope'], 6, 0.7),
                 'market': (['bunting'] * 4 + ['lanterns', 'laundry'], 8, 0.9), 'quay': (['laundry', 'laundry', 'rope', 'lanterns'], 8, 0.6),
                 'works': (['rope', 'laundry'], 12, 0.3), 'garden': (['bunting', 'lanterns'], 12, 0.35),
                 'terrace': (['lanterns'], 14, 0.3)}
        cands = []
        streets = {G['STREET'], G['LANE'], G['ALLEY'], G['AVENUE'], G['RING'], G['PLAZA'], G['QUAY']}
        for q in self.p.plots:
            x0, z0, x1, z1 = q['box']
            for sd, (dx, dz) in SIDES.items():
                if sd in 'NS':
                    span = [(x, z0 - 1 if sd == 'N' else z1 + 1) for x in range(x0 + 1, x1)]
                else:
                    span = [(x0 - 1 if sd == 'W' else x1 + 1, z) for z in range(z0 + 1, z1)]
                for t, (x, z) in enumerate(span):
                    if self.kind(x, z) not in streets:
                        continue
                    cells = []
                    cx, cz = x, z
                    hit = None
                    while len(cells) < 14:
                        kk = self.kind(cx, cz)
                        if kk == G['PLOT']:
                            hit = self.plot_at(cx, cz)
                            break
                        if kk not in streets and kk != G['STAIR']:
                            break
                        cells.append((cx, cz))
                        cx, cz = cx + dx, cz + dz
                    if hit is None or hit == q['id'] or len(cells) < 3:
                        continue
                    o = self.plot_by_id[hit]
                    if abs(o['lift'] - q['lift']) > 4 or q['id'] > hit:
                        continue   # each pair once, from the lower id; lifts are even, so the rows stay odd on both
                    run = sorted(cells[1:-1])
                    if not run:
                        continue
                    dn = q['district']
                    if dn not in style:
                        continue
                    low = min(q['lift'], o['lift'])
                    for r in (9, 13):
                        ry = low + r
                        # a course or a box row (odd, 7+) on both facades, under both eaves
                        if any(ry - h['lift'] < 7 or ry - h['lift'] > h['eave'] for h in (q, o)):
                            continue
                        mx, mz = (run[0][0] + run[-1][0]) / 2.0, (run[0][1] + run[-1][1]) / 2.0
                        cands.append((self.rnd(int(mx * 2), int(mz * 2), r, 9391), run, ry, dn, (mx, mz), 'x' if dx else 'z'))
        cands.sort()
        placed = defaultdict(list)
        for key, run, ry, dn, (mx, mz), axis in cands:
            hangs, spacing, keep = style[dn]
            if key > keep:
                continue
            if any(math.hypot(mx - a, mz - b) < spacing and abs(rr - ry) < 3 for a, b, rr in placed[dn]):
                continue
            if not self.line_room(run, ry):
                continue
            hang = hangs[int(self.rnd(int(mx), int(mz), 9392) * len(hangs))]
            self.add_line(run, ry, hang, int(self.rnd(int(mx), int(mz), 9393) * 4), '', axis)
            placed[dn].append((mx, mz, ry))
        self.stats['lane_lines_by_district'] = {k: len(v) for k, v in placed.items()}

    def canal_lines(self):
        """Lantern strings across the canal from pole to pole at the quay's edge, row 7 (they double in the water)."""
        G = self.G
        n = 0
        placed = []
        idx = np.argwhere(self.K == G['QUAY'])
        cands = sorted(((int(i) - self.O, int(j) - self.O) for j, i in idx), key=lambda c: self.rnd(c[0], c[1], 9401))
        for x, z in cands:
            if n >= 10:
                break
            if self.canal_d_at(x, z) > 12:
                continue
            if any(math.hypot(x - a, z - b) < 18 for a, b in placed):
                continue
            for sd, (dx, dz) in SIDES.items():
                k1 = self.kind(x + dx, z + dz)
                if k1 != G['LOWQUAY'] and k1 not in G['WATER']:
                    continue
                cells = [(x, z)]
                cx, cz = x + dx, z + dz
                ok = False
                while len(cells) < 20:
                    kk = self.kind(cx, cz)
                    cells.append((cx, cz))
                    if kk == G['QUAY']:
                        ok = True
                        break
                    if kk != G['LOWQUAY'] and kk not in G['WATER']:
                        break
                    cx, cz = cx + dx, cz + dz
                if not ok or len(cells) < 6:
                    continue
                if self.lift(*cells[0]) != 0 or self.lift(*cells[-1]) != 0:
                    continue
                if not (self.free(*cells[0]) and self.free(*cells[-1]) and self.lane_clear(*cells[0], pole=True)
                        and self.lane_clear(*cells[-1], pole=True)):
                    continue
                run = sorted(cells)
                if not self.line_room(run[1:-1], 7):
                    continue
                self.add_line(run, 7, 'lanterns', n % 4, 'ab', 'x' if dx else 'z')
                placed.append((x, z))
                n += 1
                break
        self.stats['canal_lines'] = n

    def canal_d_at(self, x, z):
        return float(self.p.canal_d[z + self.O, x + self.O])

    # -- output -----------------------------------------------------------------------------------------------------------
    def summary(self):
        out = dict(self.stats)
        out['props'] = len(self.props)
        return out


def build(p, G):
    layer = Layer(p, G)
    layer.run()
    return layer
