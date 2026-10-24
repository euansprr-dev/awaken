#!/usr/bin/env python3
"""A runner's path through the painted city (tools/opening): A* over the floors of a City (terrain.py) with the moves a
Runner has at rank E (SPEC §16.3, §17.2) and nothing more: run (up a stair roof, down a step), hop up a block, mantle
(up to 1.6), jump and grab a ledge then climb out (up to 3.4), drop (a fall that costs time, more over 3.5 blocks), and
sprint-jump a gap (about four blocks level, a little more falling). Conservative on purpose: a path it finds is one the
route driver can run. Costs are seconds.
"""
import heapq
import math

SPRINT = 5.6        # blocks a second, sprinting
JUMP_UP = 1.25      # a standing jump's rise
MANTLE = 1.6        # BodyRules.MANTLE_MAX
CLIMB = 3.4         # a jump's rise and a ledge hang (RunnerRules.HANG_HI) then climb out
SAFE_DROP = 3.0     # no damage
MAX_DROP = 7.0


class Planner:
    def __init__(self, city, box, avoid=None):
        """box: (x0, z0, x1, z1) plan coordinates the search stays inside; avoid(x, z) -> True to keep out."""
        self.c = city
        self.box = box
        self.avoid = avoid
        self._floors = {}

    def floors(self, x, z):
        k = (x, z)
        f = self._floors.get(k)
        if f is None:
            x0, z0, x1, z1 = self.box
            if not (x0 <= x <= x1 and z0 <= z <= z1) or (self.avoid and self.avoid(x, z)):
                f = ()
            else:
                f = tuple(self.c.floors(x, z, -8, 120))
            self._floors[k] = f
        return f

    def clear(self, x, z, lo, hi):
        """Nothing in this column blocks a body between heights lo and hi."""
        for ry in range(int(math.floor(lo + 1e-6)), int(math.floor(hi - 1e-6)) + 1):
            if self.c.blocks(x, ry, z):
                # a bottom slab whose top is at or under lo is a floor below us, not in the way
                if self.c.top(x, ry, z) is not None and self.c.top(x, ry, z) <= lo + 1e-6:
                    continue
                return False
        return True

    # ---- moves --------------------------------------------------------------------------------------------------

    def moves(self, node):
        x, z, f2 = node
        f = f2 / 2.0
        # run: the eight neighbours, up a half step or a stair, down a step
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                nx, nz = x + dx, z + dz
                if dx != 0 and dz != 0:
                    # diagonals: both sides open at body height (no cutting corners through walls)
                    if not (self.clear(x + dx, z, f + 0.6, f + 1.8) and self.clear(x, z + dz, f + 0.6, f + 1.8)):
                        continue
                best = None
                for g in self.floors(nx, nz):
                    if f - 1.01 <= g <= f + 0.61 and (best is None or abs(g - f) < abs(best - f)):
                        best = g
                if best is not None:
                    yield (nx, nz, int(round(best * 2))), math.hypot(dx, dz) / SPRINT, 'run'
        # up: hop, mantle, climb (the four sides)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for g in self.floors(nx, nz):
                dy = g - f
                if dy <= 0.61:
                    continue
                if dy <= JUMP_UP and self.clear(x, z, f, f + 1.8 + JUMP_UP):
                    yield (nx, nz, int(round(g * 2))), 0.45, 'jump'
                elif dy <= MANTLE and self.clear(x, z, f, f + 1.8 + 0.5):
                    yield (nx, nz, int(round(g * 2))), 0.8, 'mantle'
                elif dy <= CLIMB and self.clear(x, z, f, g + 1.8):
                    yield (nx, nz, int(round(g * 2))), 1.9, 'hang'
        # drop: off an edge onto something lower, up to two cells out
        for dx in (-2, -1, 0, 1, 2):
            for dz in (-2, -1, 0, 1, 2):
                if dx == 0 and dz == 0:
                    continue
                if abs(dx) == 2 and abs(dz) == 1 or abs(dx) == 1 and abs(dz) == 2:
                    continue
                nx, nz = x + dx, z + dz
                for g in self.floors(nx, nz):
                    dy = g - f
                    if not (-MAX_DROP <= dy < -1.01):
                        continue
                    # the column we step over (one out) must be open from our feet down to the landing's head room
                    sx, sz = x + (dx > 0) - (dx < 0), z + (dz > 0) - (dz < 0)
                    if (sx, sz) != (nx, nz) and not self.clear(sx, sz, g, f + 1.8):
                        continue
                    if not self.clear(nx, nz, g, f + 1.8):
                        continue
                    cost = 0.35 + 0.03 * -dy + (2.0 if -dy > SAFE_DROP + 0.5 else 0)
                    yield (nx, nz, int(round(g * 2))), cost, 'drop'
        # sprint-jump a gap: out to five blocks, landing up to one higher or six lower
        for dx in range(-5, 6):
            for dz in range(-5, 6):
                h = math.hypot(dx, dz)
                if h < 1.9 or h > 5.2:
                    continue
                nx, nz = x + dx, z + dz
                lands = self.floors(nx, nz)
                if not lands:
                    continue
                # a run-up: the cell behind us at our height
                bx, bz = x - round(dx / h), z - round(dz / h)
                if not any(abs(g - f) <= 0.6 for g in self.floors(bx, bz)):
                    continue
                for g in lands:
                    dy = g - f
                    reach = 4.0 + max(0.0, -dy) * 0.3
                    if dy > 1.01 or dy < -6 or h > min(5.2, reach) or (dy > 0.5 and h > 3.6):
                        continue
                    if self.arc_clear(x, z, f, nx, nz, g, h):
                        cost = h / SPRINT + 0.3 + (1.5 if -dy > SAFE_DROP + 0.5 else 0)
                        yield (nx, nz, int(round(g * 2))), cost, 'leap'

    def arc_clear(self, x, z, f, nx, nz, g, h):
        """The cells under the jump are a gap (lower than the line), and the arc has head room all the way."""
        n = max(2, int(math.ceil(h * 2)))
        gap = False
        seen = set()
        for i in range(1, n):
            t = i / n
            cx, cz = int(math.floor(x + 0.5 + (nx - x) * t)), int(math.floor(z + 0.5 + (nz - z) * t))
            if (cx, cz) in ((x, z), (nx, nz)) or (cx, cz) in seen:
                continue
            seen.add((cx, cz))
            line = f + (g - f) * t
            apex = line + 1.25 * 4 * t * (1 - t)
            # nothing to stand on (or trip over) above the line minus a step
            for fl in self.floors(cx, cz):
                if fl > line - 0.9:
                    return False
            if not self.clear(cx, cz, line - 0.2, apex + 1.8):
                return False
            gap = True
        return gap

    # ---- search -------------------------------------------------------------------------------------------------

    def nodes_at(self, x, z, near=None):
        fl = self.floors(x, z)
        if not fl:
            return []
        if near is not None:
            fl = sorted(fl, key=lambda g: abs(g - near))
        return [(x, z, int(round(g * 2))) for g in fl]

    def search(self, starts, goal, heuristic_to, limit=400000):
        """starts: nodes; goal(node) -> bool; heuristic_to: (x, z) the goal's centre. Returns [(node, move)] or None."""
        gx, gz = heuristic_to
        dist, prev = {}, {}
        pq = []
        for s in starts:
            dist[s] = 0.0
            heapq.heappush(pq, (math.hypot(s[0] - gx, s[1] - gz) / SPRINT, 0.0, s))
        n = 0
        while pq:
            _, d, u = heapq.heappop(pq)
            if d > dist.get(u, 1e18):
                continue
            if goal(u):
                out = [(u, prev[u][1] if u in prev else 'start')]
                while out[-1][0] in prev:
                    p, _m = prev[out[-1][0]]
                    out.append((p, prev[p][1] if p in prev else 'start'))
                return list(reversed(out)), d, n
            n += 1
            if n > limit:
                break
            for v, c, m in self.moves(u):
                nd = d + c
                if nd < dist.get(v, 1e18):
                    dist[v] = nd
                    prev[v] = (u, m)
                    heapq.heappush(pq, (nd + math.hypot(v[0] - gx, v[1] - gz) / SPRINT, nd, v))
        return None, None, n
