#!/usr/bin/env python3
"""Carillon's plan as data (docs/SPEC.md §16.2): the one designed city, the same in every world.

  python3 -I tools/city/plan.py            writes src/main/resources/data/awaken/city/plan.json and prints the stats
  python3 -I tools/city/plan.py pin        also pins the new hash in CityTests.PLAN_SHA256 (a deliberate plan change)
  python3 -I tools/city/plan.py preview    also writes build/city/plan-top.png (roofs by district) and
                                           build/city/plan-graph.png (street graph in white, roof graph in bronze)

Plan coordinates are blocks from the city's centre (the site CityBasin picks), x east, z south (north is -z).
Heights are relative to the street (ry 0 = the paving's top block, y STREET_Y = 70; feet stand at ry 1).
The river comes from CityBasin.RIVER (terrain owns that constant and carves it); everything else is decided here.
Nothing reads the world seed: the seed varies details (laundry, flower boxes, lit windows) in the painter only.

The painter (dev.awaken.city) reads the ground raster for every column and paints the pieces (plots, landmark parts,
bridges, stairs, trees, lamps), each at most 14x14 with a 2-block margin. The graphs are built here from the same
geometry and baked into the world's SavedData when the city is created.
"""
import base64
import hashlib
import json
import math
import os
import re
import sys
import zlib

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(REPO, 'src/main/resources/data/awaken/city/plan.json')
BASIN = os.path.join(REPO, 'src/main/java/dev/awaken/terrain/api/CityBasin.java')

SEED = 0xCA2111_0A7E
MASK = (1 << 64) - 1

# ---- ground kinds (one byte per cell; dev.awaken.city.CityPlan mirrors these) ------------------------------------
VOID, FREE, YARD, GARDEN, PLOT, AVENUE, STREET, LANE, ALLEY, SQUARE, PLAZA, QUAY, RIVER, CANAL, WALL, LANDMARK, \
    STAIR, WATERGATE, TRAINING, RACE, RING, LOWQUAY = range(22)
KIND_NAMES = ['void', 'free', 'yard', 'garden', 'plot', 'avenue', 'street', 'lane', 'alley', 'square', 'plaza', 'quay',
              'river', 'canal', 'wall', 'landmark', 'stair', 'watergate', 'training', 'race', 'ring', 'lowquay']
WALKABLE = {AVENUE, STREET, LANE, ALLEY, SQUARE, PLAZA, QUAY, STAIR, RING, TRAINING, LOWQUAY}
WATER = {RIVER, CANAL, WATERGATE, RACE}
RANK_KIND = {1: AVENUE, 2: STREET, 3: LANE, 4: ALLEY}

DISTRICTS = ['none', 'market', 'bellwatch', 'quay', 'terrace', 'works', 'garden', 'wallring']
D = {k: i for i, k in enumerate(DISTRICTS)}
DISTRICT_NAMES = {'market': 'Choir Square and the Market Ward', 'bellwatch': 'Bellwatch', 'quay': 'Lantern Quay',
                  'terrace': 'High Terrace', 'works': 'The Works', 'garden': 'Garden Ward', 'wallring': 'The Wallring'}

N_CELLS = 512
O = 256  # raster cell (i, j) is plan (x, z) = (i - O, j - O)

WALL_R = 240          # tower centres sit on this circle
WALL_HALF = 5         # the curtain is 10 thick
WALL_H = 40
TOWER_H = 58
TERRACE_SETBACK = 3   # the Terrace's houses stand back from its edge (a walk along the parapet)
TERRACE_LIFT = 12     # SPEC decision 22: the High Terrace stands 12 over the street (an even lift keeps the grid)
APOTHEM = WALL_R * math.cos(math.radians(7.5))

SQUARE_R = 36

# ---- the height plan (look bible §6; M4 B2) ------------------------------------------------------------------------
# Every lift a plot stands on is even, so neighbouring plots keep their lips on one grid (bible §3.10). Ground between
# levels is a retaining wall; streets climb in flights of single steps (one odd cell between two even landings).
KNOLL = (-150, -62)                       # Bellwatch's bell square: the old town's hill round it
KNOLL_TOP = 8
GARDEN_TOP = 4
# Choir Square's bowl: a level rim (the arcade's ground, B4), then three rings a step down each to the Bell Stone
BOWL = ((31.0, -1), (23.0, -2), (13.0, -3))
# the gatehouse yard outside the High Gate (SPEC decision 29): Living Wilds' quartermaster and bounty post stand there
GATE_YARD = (-16, -255, 15, -245)


def mix(z):
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9 & MASK
    z = (z ^ (z >> 27)) * 0x94D049BB133111EB & MASK
    return z ^ (z >> 31)


def h64(*vals):
    z = SEED
    for v in vals:
        z = mix((z ^ (int(v) & MASK)) * 0x9E3779B97F4A7C15 & MASK)
    return z


def rnd(*vals):
    """A stable number in [0, 1) for these values (never Python's random: the plan must not drift)."""
    return (h64(*vals) >> 11) / float(1 << 53)


def rint(lo, hi, *vals):
    return lo + int(rnd(*vals) * (hi - lo + 1))


# ---- the river (terrain's constant) and the water we add ---------------------------------------------------------

def read_river():
    txt = open(BASIN).read()
    m = re.search(r'RIVER = List\.of\((.*?)\);', txt, re.S)
    pts = [(int(a), int(b)) for a, b in re.findall(r'new int\[\]\{(-?\d+), (-?\d+)\}', m.group(1))]
    assert len(pts) >= 4, 'CityBasin.RIVER not found'
    return pts


RIVER_PTS = read_river()
RIVER_HALF = 9.0
QUAY_W = 7.0          # a lower quay 3 wide by the water (4 below the street), then the street-level promenade
LOW_QUAY_W = 3.0
CANAL_PTS = [(-73, -34), (-100, -38), (-120, -30), (-126, 0), (-118, 22), (-90, 26), (-68, 24)]
CANAL_HALF = 3.5
CANAL_LOW_W = 2.0     # the canal's lower quay (a towpath four below the street, three above the water), M4 B2
CANAL_QUAY_W = 3.0    # then the street-level quay
WASHERS = (-100.0, 25.0)  # the Washers' Bridge over the canal's south loop
RACE_Z = 118
RACE_PTS = [(-84, RACE_Z), (-28, RACE_Z)]
RACE_HALF = 2.5


def river_x(z):
    p = RIVER_PTS
    for (ax, az), (bx, bz) in zip(p, p[1:]):
        if az <= z <= bz:
            return ax + (bx - ax) * ((z - az) / (bz - az or 1))
    return p[0][0] if z < p[0][1] else p[-1][0]


def seg_dist(ax, az, bx, bz, X, Z):
    dx, dz = bx - ax, bz - az
    ll = dx * dx + dz * dz
    if ll == 0:
        return np.hypot(X - ax, Z - az)
    t = np.clip(((X - ax) * dx + (Z - az) * dz) / ll, 0.0, 1.0)
    return np.hypot(X - ax - t * dx, Z - az - t * dz)


def poly_dist(poly, X, Z):
    d = None
    for (ax, az), (bx, bz) in zip(poly, poly[1:]):
        s = seg_dist(ax, az, bx, bz, X, Z)
        d = s if d is None else np.minimum(d, s)
    return d


def pdist(poly, x, z):
    return float(poly_dist(poly, np.array([x], float), np.array([z], float))[0])


def wall_depth(X, Z):
    """How far inside the 24-sided wall line (negative outside); towers sit on its corners."""
    best = None
    for k in range(24):
        phi = math.radians(15 + 15 * k)
        s = X * math.cos(phi) + Z * math.sin(phi)
        best = s if best is None else np.maximum(best, s)
    return APOTHEM - best


def district_of(x, z):
    r = math.hypot(x, z)
    if r > 222:
        return 'wallring'
    rx = river_x(z)
    if z > -92 and r < 88 and x > rx + 16:
        return 'market'
    if abs(x - rx) < 30 or pdist(CANAL_PTS, x, z) < 14:
        return 'quay'
    if x < rx:
        return 'bellwatch'
    if z <= -92 and x <= 150:
        return 'terrace'
    a = math.degrees(math.atan2(z, x))
    if 30 < a < 150:
        return 'works'
    return 'garden'


class Plan:
    def __init__(self):
        idx = np.arange(N_CELLS) - O
        self.X, self.Z = np.meshgrid(idx.astype(float) + 0.5, idx.astype(float) + 0.5)  # cell centres
        self.kind = np.zeros((N_CELLS, N_CELLS), np.uint8)
        self.dist = np.zeros((N_CELLS, N_CELLS), np.uint8)
        self.lift = np.zeros((N_CELLS, N_CELLS), np.int16)   # signed (the bowl); written as int8
        self.owner = np.full((N_CELLS, N_CELLS), -1, np.int32)  # the street segment that paved each street cell
        self.plot_id = np.full((N_CELLS, N_CELLS), -1, np.int32)
        self.streets = []      # dicts: a, b, w, rank, district (or None), name
        self.bridges = []
        self.stairs = []
        self.landmarks = []
        self.plots = []
        self.trees = []
        self.lamps = []
        self.towers = []
        self.gates = []
        self.notes = []

    # -- raster helpers ------------------------------------------------------------------------------------------
    def box_mask(self, x0, z0, x1, z1):
        m = np.zeros_like(self.kind, bool)
        m[max(0, z0 + O):min(N_CELLS, z1 + O + 1), max(0, x0 + O):min(N_CELLS, x1 + O + 1)] = True
        return m

    def put_box(self, x0, z0, x1, z1, kind, only=None):
        m = self.box_mask(x0, z0, x1, z1)
        if only is not None:
            m &= np.isin(self.kind, list(only))
        self.kind[m] = kind
        return m

    def cell(self, x, z):
        i, j = int(math.floor(x)) + O, int(math.floor(z)) + O
        if 0 <= i < N_CELLS and 0 <= j < N_CELLS:
            return int(self.kind[j, i])
        return VOID

    # -- 1. districts, water, the wall --------------------------------------------------------------------------
    def base(self):
        X, Z = self.X, self.Z
        depth = wall_depth(X, Z)
        self.depth = depth
        inside = depth > -WALL_HALF - 0.01
        self.kind[inside] = FREE
        # districts per cell (python loop once; 262k cells)
        dist = np.zeros_like(self.dist)
        for j in range(N_CELLS):
            z = j - O + 0.5
            for i in range(N_CELLS):
                if inside[j, i]:
                    dist[j, i] = D[district_of(i - O + 0.5, z)]
        self.dist = dist
        self.kind[(depth >= -WALL_HALF) & (depth < WALL_HALF)] = WALL
        self.kind[(depth >= WALL_HALF) & (depth < WALL_HALF + 6)] = RING
        rd = poly_dist(RIVER_PTS, X, Z)
        self.river_d = rd
        cd = poly_dist(CANAL_PTS, X, Z)
        self.canal_d = cd
        cityish = depth > -WALL_HALF - 0.01
        cw = CANAL_HALF + CANAL_LOW_W + CANAL_QUAY_W
        quay = cityish & (((rd >= RIVER_HALF) & (rd < RIVER_HALF + QUAY_W)) | ((cd >= CANAL_HALF) & (cd < cw)))
        self.kind[quay & (self.kind != WALL)] = QUAY
        self.kind[cityish & (cd < CANAL_HALF) & (depth >= WALL_HALF)] = CANAL
        self.kind[cityish & (rd >= RIVER_HALF) & (rd < RIVER_HALF + LOW_QUAY_W) & (self.kind == QUAY)] = LOWQUAY
        # the canal a level down too: a lower quay along both banks, ladders up to the street (the painter's); by the
        # Washers' Bridge the street-level edge stays (the washers' stretch, M3's washplace)
        wash = np.hypot(X - WASHERS[0], Z - WASHERS[1]) < 13
        self.kind[cityish & (cd >= CANAL_HALF) & (cd < CANAL_HALF + CANAL_LOW_W) & (self.kind == QUAY) & (depth >= WALL_HALF) & ~wash] = LOWQUAY
        river = cityish & (rd < RIVER_HALF)
        self.kind[river & (self.kind == WALL)] = WATERGATE
        self.kind[river & (self.kind != WATERGATE)] = RIVER
        race = cityish & (poly_dist(RACE_PTS, X, Z) < RACE_HALF) & (rd >= RIVER_HALF - 1)
        self.kind[race] = RACE
        self.kind[cityish & (poly_dist(RACE_PTS, X, Z) >= RACE_HALF) & (poly_dist(RACE_PTS, X, Z) < RACE_HALF + 2)
                  & np.isin(self.kind, [FREE])] = QUAY
        sq = np.hypot(X, Z) < SQUARE_R
        self.kind[sq] = SQUARE

    def walls(self):
        """24 tower places on the wall's corners: 21 towers, the Runners' Roost (NW), two water gates."""
        for k in range(24):
            a = 7.5 + 15 * k
            x, z = WALL_R * math.cos(math.radians(a)), WALL_R * math.sin(math.radians(a))
            cx, cz = int(round(x)), int(round(z))
            kind = 'tower'
            if k == 14:
                kind = 'roost'
            elif k in (8, 16):
                kind = 'watergate'
            self.towers.append({'x': cx, 'z': cz, 'angle': a, 'kind': kind})
        parts = []
        for t in self.towers:
            if t['kind'] != 'tower':
                continue
            box = [t['x'] - 7, t['z'] - 7, t['x'] + 6, t['z'] + 6]
            parts.append({'kind': 'tower', 'box': box, 'h': TOWER_H, 'style': rint(0, 2, 11, t['x'], t['z'])})
            self.put_box(*box, LANDMARK)
        self.landmarks.append({'id': 'wall_towers', 'name': 'The Wall', 'parts': parts})
        # water gates: towers either side of the river, the wall arches over the water (WATERGATE cells)
        for t, name in ((self.towers[16], 'North Water Gate'), (self.towers[8], 'South Water Gate')):
            a = math.radians(t['angle'])
            tx, tz = -math.sin(a), math.cos(a)  # along the wall
            # where the river centre crosses the wall line near this corner
            best = None
            for s in np.arange(-40, 40, 0.5):
                px, pz = t['x'] + tx * s, t['z'] + tz * s
                d = pdist(RIVER_PTS, px, pz)
                if best is None or d < best[0]:
                    best = (d, px, pz)
            _, px, pz = best
            wparts = []
            for side in (-1, 1):
                cx, cz = int(round(px + tx * side * 21)), int(round(pz + tz * side * 21))
                box = [cx - 6, cz - 6, cx + 6, cz + 6]
                wparts.append({'kind': 'tower', 'box': box, 'h': TOWER_H + 4, 'style': 3})
                self.put_box(*box, LANDMARK, only={FREE, WALL, RING, QUAY})
            self.landmarks.append({'id': 'water_gate_' + ('n' if t is self.towers[16] else 'sw'), 'name': name,
                                   'parts': wparts, 'at': [int(round(px)), int(round(pz))]})
        # land gates: mid-wall on the three axes
        for name, facing, (ux, uz), (vx, vz) in (('High Gate', 'N', (1, 0), (0, -1)), ('Dawn Gate', 'E', (0, 1), (1, 0)),
                                                 ('Mill Gate', 'S', (-1, 0), (0, 1))):
            c = APOTHEM
            cx, cz = int(round(vx * c)), int(round(vz * c))
            if facing == 'N':
                cz = -238
            elif facing == 'E':
                cx = 237
            else:
                cz = 237

            def box(u0, v0, u1, v1):
                xs = [cx + ux * u0 + vx * v0, cx + ux * u1 + vx * v1]
                zs = [cz + uz * u0 + vz * v0, cz + uz * u1 + vz * v1]
                return [min(xs), min(zs), max(xs), max(zs)]
            gparts = [{'kind': 'gate_tower', 'box': box(-17, -7, -6, 6), 'h': 50},
                      {'kind': 'gate_passage', 'box': box(-5, -6, 4, 5), 'h': 46, 'axis': 'z' if facing in 'NS' else 'x'},
                      {'kind': 'gate_tower', 'box': box(5, -7, 16, 6), 'h': 50}]
            for p in gparts:
                self.put_box(*p['box'], LANDMARK, only={FREE, WALL, RING, QUAY})
            # the passage floor is a street (the avenue runs through it)
            pb = gparts[1]['box']
            self.put_box(pb[0], pb[1], pb[2], pb[3], AVENUE)
            self.gates.append({'name': name, 'facing': facing, 'x': cx, 'z': cz})
            self.landmarks.append({'id': 'gate_' + facing.lower(), 'name': name, 'parts': gparts, 'at': [cx, cz]})
        # the gatehouse yard just outside the High Gate (SPEC decision 29): paved, the road through its middle, room on
        # either side for the Living Wilds' quartermaster and bounty post (that module places them on the named spots)
        self.put_box(*GATE_YARD, PLAZA, only={VOID})

    # -- 2. the landmarks inside the wall ------------------------------------------------------------------------
    def civic(self):
        L = self.landmarks
        # Choir: the spire on the square's north edge, a nave east-west, the apse behind
        choir = [{'kind': 'spire', 'box': [-7, -53, 6, -40], 'h': 140},
                 {'kind': 'nave', 'box': [-35, -51, -22, -42], 'h': 24, 'axis': 'x'},
                 {'kind': 'nave', 'box': [-21, -51, -8, -42], 'h': 26, 'axis': 'x'},
                 {'kind': 'nave', 'box': [7, -51, 20, -42], 'h': 26, 'axis': 'x'},
                 {'kind': 'nave', 'box': [21, -51, 34, -42], 'h': 24, 'axis': 'x'},
                 {'kind': 'apse', 'box': [-7, -67, 6, -54], 'h': 30}]
        L.append({'id': 'choir', 'name': 'The Choir', 'parts': choir, 'at': [0, -46]})
        for p in choir:
            self.put_box(*p['box'], LANDMARK)
        self.put_box(-10, -39, 9, -37, PLAZA, only={FREE, SQUARE})
        # the Association's market hall on the square's west edge (its balcony faces the square: act 3 of the run)
        mh = [{'kind': 'market_hall', 'box': [-50, -34, -37, -26], 'h': 20, 'face': 'E'},
              {'kind': 'market_hall', 'box': [-50, -25, -37, -17], 'h': 20, 'face': 'E'}]
        L.append({'id': 'market_hall', 'name': "The Association's Market Hall", 'parts': mh, 'at': [-43, -25]})
        for p in mh:
            self.put_box(*p['box'], LANDMARK)
        # (the square's stalls are street-life props since M4 B3a: tools/city/props.py, clusters on the bowl's -2 ring)
        # the High Terrace: the Hunter Association, raised with the terrace
        assoc = [{'kind': 'assoc_hall', 'box': [-21, -150, -8, -137], 'h': 26},
                 {'kind': 'assoc_centre', 'box': [-7, -150, 6, -137], 'h': 40},
                 {'kind': 'assoc_hall', 'box': [7, -150, 20, -137], 'h': 26},
                 {'kind': 'assoc_wing', 'box': [-21, -164, -8, -151], 'h': 22, 'name': 'Registry'},
                 {'kind': 'archive', 'box': [-7, -164, 6, -151], 'h': 44, 'name': 'Archive'},
                 {'kind': 'assoc_wing', 'box': [7, -164, 20, -151], 'h': 22, 'name': 'Infirmary'},
                 {'kind': 'pavilion', 'box': [-5, -194, 4, -188], 'h': 9}]
        L.append({'id': 'association', 'name': 'The Hunter Association', 'parts': assoc, 'at': [0, -150]})
        for p in assoc:
            self.put_box(*p['box'], LANDMARK)
        self.put_box(-26, -136, 25, -104, PLAZA)
        self.put_box(-24, -187, 23, -166, TRAINING)
        # the Runners' Roost: the NW tower rebuilt as the guild house, a hall against the wall's inner face
        t = self.towers[14]
        tx, tz = t['x'], t['z']
        roost = [{'kind': 'roost_tower', 'box': [tx - 7, tz - 7, tx + 6, tz + 6], 'h': 66},
                 {'kind': 'skybridge', 'box': [tx + 5, tz + 4, tx + 11, tz + 8], 'h': WALL_H, 'axis': 'x'},
                 {'kind': 'roost_hall', 'box': [tx + 10, tz + 7, tx + 23, tz + 19], 'h': WALL_H + 10}]
        L.append({'id': 'roost', 'name': "The Runners' Roost", 'parts': roost, 'at': [t['x'], t['z']]})
        for p in roost:
            if p['kind'] != 'skybridge':
                self.put_box(*p['box'], LANDMARK, only={FREE, WALL, YARD})
        self.roost = t
        # district bell towers (the Choir is the Market's), each in a small square
        bells = {'bellwatch': (-150, -62), 'quay': (-108, 64), 'terrace': (72, -128), 'garden': (152, 40),
                 'works': (40, 152)}
        self.bells = bells
        for key, (bx, bz) in bells.items():
            self.put_box(bx - 13, bz - 13, bx + 12, bz + 12, PLAZA, only={FREE})
            box = [bx - 5, bz - 5, bx + 4, bz + 4]
            L.append({'id': 'bell_' + key, 'name': 'Bell tower of ' + DISTRICT_NAMES[key],
                      'parts': [{'kind': 'belltower', 'box': box, 'h': {'bellwatch': 46, 'quay': 40, 'terrace': 48,
                                                                         'garden': 42, 'works': 44}[key],
                                 'style': key}], 'at': [bx, bz]})
            self.put_box(*box, LANDMARK)
        # crane towers on the Quay's east bank, booms over the river
        cranes = []
        for cz in (-26, 8):
            rx = int(round(river_x(cz)))
            qx = rx + int(RIVER_HALF + LOW_QUAY_W) + 2
            cranes.append({'kind': 'crane', 'box': [qx - 2, cz - 3, qx + 4, cz + 3], 'h': 24})
            cranes.append({'kind': 'boom', 'box': [rx - 6, cz - 1, qx - 3, cz + 1], 'h': 22})
            self.put_box(qx - 2, cz - 3, qx + 4, cz + 3, LANDMARK, only={FREE, QUAY})
        L.append({'id': 'cranes', 'name': 'Quay cranes', 'parts': cranes})
        # the Works mills on the mill race, wheels in the water
        mills = []
        for mx in (-70, -48):
            mills.append({'kind': 'mill', 'box': [mx, RACE_Z - 16, mx + 13, RACE_Z - 5], 'h': 18, 'face': 'S'})
            mills.append({'kind': 'wheel', 'box': [mx + 1, RACE_Z - 2, mx + 13, RACE_Z + 2], 'h': 13})
            self.put_box(mx, RACE_Z - 16, mx + 13, RACE_Z - 5, LANDMARK, only={FREE, QUAY})
        L.append({'id': 'mills', 'name': 'The Works mills', 'parts': mills, 'at': [-50, RACE_Z]})
        # under the old bell: a cellar room in the Knoll's fill below Bellwatch's bell tower, reached by steps down from
        # the bell square (SPEC decision 29; the Living Wilds' quest finds a melting room here; B4 dresses it)
        kx, kz = KNOLL
        L.append({'id': 'bellwatch_cellar', 'name': 'The cellar under the old bell',
                  'parts': [{'kind': 'cellar', 'box': [kx - 6, kz - 5, kx + 10, kz + 5], 'h': 5}], 'at': [kx - 1, kz]})

    # -- 3. streets ----------------------------------------------------------------------------------------------
    def street(self, a, b, w, rank, district=None, name=None):
        self.streets.append({'a': a, 'b': b, 'w': w, 'rank': rank, 'district': district, 'name': name,
                             'index': len(self.streets)})

    def avenues(self):
        S = self.street
        # the radial avenues from Choir Square
        S((0, -68), (0, -92), 10, 1, name='Choir Avenue')
        S((0, -100), (0, -104), 10, 1, name='Choir Avenue')
        S((-34, -104), (-34, -204), 8, 1, name='Registry Row')
        S((34, -104), (34, -204), 8, 1, name='Infirmary Row')
        S((-34, -204), (34, -204), 8, 1)
        S((0, -204), (0, -214), 10, 1, name='High Gate Road')
        S((0, -222), (0, -238), 10, 1, name='High Gate Road')
        S((36, 0), (238, 0), 10, 1, name='Dawn Avenue')
        S((0, 36), (0, 238), 10, 1, name='Mill Avenue')
        S((-36, -10), (-232, -10), 8, 1, name='Bell Street')
        S((-24, 26), (-40, 40), 8, 1, name='Mill Road')
        S((-40, 40), (-92, 40), 8, 1, name='Mill Road')
        S((-92, 40), (-176, 124), 8, 1, name='Mill Road')
        S((24, -24), (92, -92), 8, 1, name='Terrace Way')
        S((92, -100), (147, -155), 8, 1, name='Terrace Way')     # (ends on the Terrace: the Garden beyond is lower)
        S((24, 24), (160, 160), 8, 1, name='Kiln Way')
        # around the Choir: two lanes from the square to the avenue's start
        S((-42, -34), (-42, -70), 6, 2)
        S((42, -34), (42, -70), 6, 2)
        S((-42, -70), (42, -70), 6, 2)
        # the inner ring boulevard on the east bank
        ring = [(110 * math.cos(math.radians(a)), 110 * math.sin(math.radians(a))) for a in range(-56, 130, 6)]
        for p, q in zip(ring, ring[1:]):
            S(p, q, 7, 2, name='The Ring')
        # the quayside streets one block back from the water
        # market: spokes between the avenues and a ring
        for a in (-157.5, -22.5, 22.5, 67.5, 112.5, 157.5, -67.5, -112.5):
            r0, r1 = 36, 86
            p = (r0 * math.cos(math.radians(a)), r0 * math.sin(math.radians(a)))
            q = (r1 * math.cos(math.radians(a)), r1 * math.sin(math.radians(a)))
            S(p, q, 5, 2, district='market')     # the spokes are 5 wide (SPEC decision 22)
        ring2 = [(62 * math.cos(math.radians(a)), 62 * math.sin(math.radians(a))) for a in range(0, 361, 10)]
        for p, q in zip(ring2, ring2[1:]):
            S(p, q, 5, 2, district='market')
        # the terrace: a formal grid round the Association
        for x in (74, 112):
            S((x, -100), (x, -214), 6, 2, district='terrace')
        for z in (-130, -172):
            S((-60, z), (150, z), 6, 2, district='terrace')
        S((-60, -160), (-34, -160), 6, 2, district='terrace')
        # Bridges' streets
        S((-100, -160), (-34, -160), 6, 2, name='North Bridge')
        S((-130, 125), (-60, 125), 6, 2, name='Tannery Bridge')
        # garden ward: a crescent and spokes
        arc = [(168 * math.cos(math.radians(a)), 168 * math.sin(math.radians(a))) for a in range(-48, 34, 6)]
        for p, q in zip(arc, arc[1:]):
            S(p, q, 6, 2, district='garden', name='Orchard Crescent')
        for a in (-36, -12, 12, 26):
            p = (112 * math.cos(math.radians(a)), 112 * math.sin(math.radians(a)))
            q = (224 * math.cos(math.radians(a)), 224 * math.sin(math.radians(a)))
            S(p, q, 5, 3, district='garden')

    def lattice(self, district, spacing, jitter, keep, width, rank, salt, diag=0.0, ox=0, oz=0):
        n = int(260 / spacing) + 2
        V = {}
        for i in range(-n, n + 1):
            for k in range(-n, n + 1):
                jx = (rnd(salt, i, k, 1) - 0.5) * 2 * jitter
                jz = (rnd(salt, i, k, 2) - 0.5) * 2 * jitter
                V[(i, k)] = (i * spacing + ox + jx, k * spacing + oz + jz)
        for (i, k), p in V.items():
            for di, dk, prob in ((1, 0, keep), (0, 1, keep), (1, 1, diag), (1, -1, diag * 0.6)):
                q = V.get((i + di, k + dk))
                if q is None or prob <= 0:
                    continue
                mx, mz = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
                if math.hypot(mx, mz) > 226 or district_of(mx, mz) != district:
                    continue
                if rnd(salt, i, k, di, dk, 3) >= prob:
                    continue
                w = width if isinstance(width, (int, float)) else (width[0] if rnd(salt, i, k, di, dk, 4) < 0.5 else width[1])
                self.street(p, q, w, rank, district=district)

    def quay_lanes(self):
        """Lanes from the river inland on both banks, every ~26 blocks (the porters' lanes)."""
        for z in range(-210, 110, 26):
            if any(abs(z - bz) <= 4 for bz in (-160, 125)):
                continue  # (a bridge's street runs here already)
            rx = river_x(z)
            for side in (-1, 1):
                a = (rx + side * (RIVER_HALF + QUAY_W - 1), z)
                # (on the east bank under the High Terrace a lane ends at the foot of its wall)
                b = (rx + side * (28 if side > 0 and z <= -92 else 44), z)
                self.street(a, b, 4, 3, district=None, name=None)

    def rasterize(self):
        """Avenues first: a lower rank never overwrites a higher one; streets never cover water, wall or landmarks."""
        X, Z = self.X, self.Z
        for s in sorted(self.streets, key=lambda s: s['rank']):
            (ax, az), (bx, bz), w = s['a'], s['b'], s['w']
            x0, x1 = int(min(ax, bx) - w) + O, int(max(ax, bx) + w) + O + 1
            z0, z1 = int(min(az, bz) - w) + O, int(max(az, bz) + w) + O + 1
            x0, z0 = max(0, x0), max(0, z0)
            x1, z1 = min(N_CELLS, x1), min(N_CELLS, z1)
            sub = seg_dist(ax, az, bx, bz, X[z0:z1, x0:x1], Z[z0:z1, x0:x1])
            m = sub <= w / 2.0
            k = self.kind[z0:z1, x0:x1]
            ok = np.isin(k, [FREE, YARD]) | ((k > RANK_KIND[s['rank']]) & np.isin(k, [AVENUE, STREET, LANE, ALLEY]))
            if s['district'] is not None:
                ok &= self.dist[z0:z1, x0:x1] == D[s['district']]
            k[m & ok] = RANK_KIND[s['rank']]
            self.owner[z0:z1, x0:x1][m & ok] = s['index']

    # -- 4. the height plan (look bible §6, M4 B2) ---------------------------------------------------------------
    # Streets that climb: streets, lanes and alleys (rank 2-4). Avenues keep their zone's level (the Garden's are
    # cuttings between raised gardens), the square, plazas and quays are pinned to their ground.
    CLIMB_KINDS = (STREET, LANE, ALLEY)
    FLIGHT_GAP = 4      # landings between flights on a climbing street: a flight about every house front

    def field(self):
        """The height the ground would like, continuous (quantized by quant()): the Knoll in rings round Bellwatch's
        bell square (2 at r 46-37, 4 to 28, 6 to 19, 8 on the top), the Garden Ward stepping up toward the east wall
        (2 from r 172, 4 from r 196). Zero elsewhere."""
        X, Z = self.X, self.Z
        F = np.zeros(X.shape)
        cap = np.zeros(X.shape)
        bw = self.dist == D['bellwatch']
        rk = np.hypot(X - KNOLL[0], Z - KNOLL[1])
        F[bw] = (1 + 2 * (46 - rk) / 9)[bw]
        cap[bw] = KNOLL_TOP
        gd = self.dist == D['garden']
        r = np.hypot(X, Z)
        F[gd] = (1 + 2 * (r - 172) / 24)[gd]
        cap[gd] = GARDEN_TOP
        return F, cap

    @staticmethod
    def quant(f, cap):
        """Even levels only (bible §3.10: lifts in even numbers keep every plot's lips on one grid)."""
        return np.clip(2 * np.floor((f + 1) / 2), 0, cap)

    def prune(self):
        """Street cells cut off from the city's open ground (a lane's end beyond a landmark that took its middle) become
        yards: no house fronts a street that leads nowhere."""
        K = self.kind
        walk = np.isin(K, list(WALKABLE)) | (self.bridge_cells() & np.isin(K, list(WATER)))
        seen = np.zeros_like(walk)
        seen[O, O] = True
        while True:
            grow = seen.copy()
            grow[1:, :] |= seen[:-1, :]
            grow[:-1, :] |= seen[1:, :]
            grow[:, 1:] |= seen[:, :-1]
            grow[:, :-1] |= seen[:, 1:]
            grow &= walk
            if np.array_equal(grow, seen):
                break
            seen = grow
        cut = ~seen & np.isin(K, [STREET, LANE, ALLEY])
        K[cut] = YARD
        self.owner[cut] = -1
        self.pruned = int(np.count_nonzero(cut))

    def relief(self):
        """The city's ground (look bible §6): zones first (the Knoll, the Garden terraces, the High Terrace at 12, the
        square's bowl), the Terrace's five stairs, then every climbing street follows the ground in flights, and every
        walkable cell one above a walkable neighbour becomes a step."""
        K, Lf = self.kind, self.lift
        F, cap = self.field()
        G = self.quant(F, cap).astype(np.int16)
        ground = ~np.isin(K, list(WATER) + [WALL, RING, QUAY, LOWQUAY, VOID])
        Lf[ground] = G[ground]
        Lf[K == AVENUE] = 0
        t = (self.dist == D['terrace']) & ground
        Lf[t] = TERRACE_LIFT
        self.terrace_mask = t
        # the setback: the Terrace's houses stand back three from the edge of its retaining wall, leaving a walk of lawn
        # and paths along the parapet (the balcony over the city, bible §5.4); the long drops stay open to runners
        low = ~t & ~np.isin(K, list(WATER) + [VOID]) & (Lf <= TERRACE_LIFT - 4)
        near = low.copy()
        for _ in range(TERRACE_SETBACK):
            grow = near.copy()
            grow[1:, :] |= near[:-1, :]
            grow[:-1, :] |= near[1:, :]
            grow[:, 1:] |= near[:, :-1]
            grow[:, :-1] |= near[:, 1:]
            near = grow
        K[t & near & (K == FREE)] = YARD
        rr = np.hypot(self.X, self.Z)
        sq = K == SQUARE
        for rad, lv in BOWL:
            Lf[sq & (rr < rad)] = lv
        self.named_stairs()
        self.street_relief()
        self.stitch()
        self.steps()

    def named_stairs(self):
        """The High Terrace's five stairs, each as long as its rise (one step a cell), the top at the terrace's edge."""
        stairs = [('Grand Stair', [-7, -103, 6, -92], 's'),          # down south toward the Choir
                  ('Terrace Way stair', [86, -103, 97, -92], 's'),
                  ('High Gate stair', [-5, -225, 4, -214], 'n'),
                  ('Orchard stair', [143, -134, 150, -127], 'e'),     # down to the Garden's upper terrace (4)
                  ('Quay stair', [-50, -163, -39, -157], 'w')]
        for name, (x0, z0, x1, z1), down in stairs:
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    step = {'s': z - z0, 'n': z1 - z, 'e': x - x0, 'w': x1 - x}[down]
                    self.lift[z + O, x + O] = TERRACE_LIFT - step
                    self.kind[z + O, x + O] = STAIR
            self.stairs.append({'name': name, 'box': [x0, z0, x1, z1], 'down': down, 'lift': TERRACE_LIFT})

    def junctions(self):
        """Where streets meet: per street the points along it (t in [0, 1]) where it ends, crosses another, or another
        ends beside it (a T, within 3.5). Points within 2 blocks of each other are one junction."""
        segs = self.streets
        A = [np.array(s['a'], float) for s in segs]
        B = [np.array(s['b'], float) for s in segs]
        pts, parent = [], []

        def add(i, t, p):
            pts.append((i, float(t), float(p[0]), float(p[1])))
            parent.append(len(parent))
            return len(pts) - 1

        def find(a):
            while parent[a] != a:
                parent[a] = parent[parent[a]]
                a = parent[a]
            return a

        def union(a, b):
            ra, rb = find(a), find(b)
            if ra != rb:
                parent[max(ra, rb)] = min(ra, rb)

        for i in range(len(segs)):
            add(i, 0.0, A[i])
            add(i, 1.0, B[i])
        boxes = [(min(a[0], b[0]) - 4, min(a[1], b[1]) - 4, max(a[0], b[0]) + 4, max(a[1], b[1]) + 4) for a, b in zip(A, B)]
        for i in range(len(segs)):
            bi = boxes[i]
            for j in range(i + 1, len(segs)):
                bj = boxes[j]
                if bi[2] < bj[0] or bj[2] < bi[0] or bi[3] < bj[1] or bj[3] < bi[1]:
                    continue
                hit = intersect(A[i], B[i], A[j], B[j])
                if hit is not None:
                    p = A[i] + (B[i] - A[i]) * hit[0]
                    union(add(i, hit[0], p), add(j, hit[1], p))
                    continue
                for e_seg, e_end, e in ((j, 0, A[j]), (j, 1, B[j]), (i, 0, A[i]), (i, 1, B[i])):
                    other = i if e_seg == j else j
                    t, dist = project(e, A[other], B[other])
                    if dist <= 3.5 and 0.0 < t < 1.0:
                        q = A[other] + (B[other] - A[other]) * t
                        union(add(other, t, q), 2 * e_seg + e_end)
        grid = {}
        for k, (_, _, x, z) in enumerate(pts):
            key = (int(math.floor(x / 2)), int(math.floor(z / 2)))
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for m in grid.get((key[0] + dx, key[1] + dz), ()):
                        if math.hypot(pts[m][2] - x, pts[m][3] - z) <= 2.0:
                            union(k, m)
            grid.setdefault(key, []).append(k)
        clusters = {}
        for k in range(len(pts)):
            clusters.setdefault(find(k), []).append(k)
        cuts = [[] for _ in segs]
        for root, members in clusters.items():
            for k in members:
                cuts[pts[k][0]].append((pts[k][1], root))
        return pts, clusters, cuts

    def street_relief(self):
        """Every climbing street follows the ground: its junctions sit at the ground's level there (or the level of the
        square, plaza, quay or avenue they open onto), and between junctions it climbs in flights of single steps (one
        odd cell between two even landings), at the places the ground climbs, with landings between."""
        K, Lf = self.kind, self.lift
        base = Lf.copy()
        segs = self.streets
        rank = np.array([s['rank'] for s in segs] + [9])
        own = self.owner
        climb = np.isin(K, self.CLIMB_KINDS) & (own >= 0) & (rank[own] >= 2)
        walk = np.isin(K, list(WALKABLE))
        pts, clusters, cuts = self.junctions()
        level = {}
        for root, members in clusters.items():
            pinned, open_ = [], []
            for k in members:
                _, _, x, z = pts[k]
                i, j = int(math.floor(x)) + O, int(math.floor(z)) + O
                if 0 <= i < N_CELLS and 0 <= j < N_CELLS and walk[j, i]:
                    (open_ if climb[j, i] else pinned).append(int(base[j, i]))
            if pinned:
                level[root] = max(set(pinned), key=pinned.count)
            elif open_:
                # (the ground's level where the streets meet: at the centre if it is street, else the commonest)
                x = sum(pts[k][2] for k in members) / len(members)
                z = sum(pts[k][3] for k in members) / len(members)
                i, j = int(math.floor(x)) + O, int(math.floor(z)) + O
                level[root] = int(base[j, i]) if walk[j, i] else max(set(open_), key=open_.count)
            else:
                level[root] = None      # a street's end in nothing (beyond its district): no constraint
        widest = {root: max(segs[pts[k][0]]['w'] for k in members) for root, members in clusters.items()}
        self.relief_conflicts = []
        for s in segs:
            if s['rank'] < 2:
                continue
            idx = s['index']
            a, b = np.array(s['a'], float), np.array(s['b'], float)
            L = float(np.hypot(*(b - a)))
            if L < 0.5:
                continue
            u = (b - a) / L
            n = int(math.ceil(L)) + 1
            prof = [None] * n
            strong = [False] * n
            target = [0] * n
            for d in range(n):
                p = a + u * min(d, L)
                i, j = int(math.floor(p[0])) + O, int(math.floor(p[1])) + O
                target[d] = int(base[j, i])
                if walk[j, i] and not climb[j, i] and K[j, i] not in (LOWQUAY,):
                    prof[d] = int(base[j, i])
                    strong[d] = True
            owned = [False] * n
            opens = [False] * n
            for d in range(n):
                p = a + u * min(d, L)
                i, j = int(math.floor(p[0])) + O, int(math.floor(p[1])) + O
                owned[d] = own[j, i] == idx
                opens[d] = bool(walk[j, i])
            # (ground the street never reaches, beyond yards or houses at its end, doesn't hold it)
            d = 0
            while d < n:
                if not strong[d]:
                    d += 1
                    continue
                e = d
                while e + 1 < n and strong[e + 1]:
                    e += 1
                reach = False
                for lo, hi, step in ((d - 1, -1, -1), (e + 1, n, 1)):
                    k = lo
                    while (k >= 0 if step < 0 else k < n) and opens[k]:
                        if owned[k]:
                            reach = True
                            break
                        k += step
                if not reach:
                    for k in range(d, e + 1):
                        prof[k] = None
                        strong[k] = False
                d = e + 1
            for t, root in cuts[idx]:
                if level[root] is None:
                    continue
                dc = t * L
                m = max(2, int(math.ceil(widest[root] / 2)) + 1)
                for d in range(int(math.floor(dc - m)), int(math.ceil(dc + m)) + 1):
                    if 0 <= d < n and prof[d] is None:
                        prof[d] = level[root]
                        strong[d] = abs(d - dc) <= 1.0
            self.unpin(prof, strong)
            self.fill_profile(prof, target, s, owned)
            # the street's own cells take the level at their place along it
            x0 = int(min(a[0], b[0]) - s['w']) + O
            x1 = int(max(a[0], b[0]) + s['w']) + O + 1
            z0 = int(min(a[1], b[1]) - s['w']) + O
            z1 = int(max(a[1], b[1]) + s['w']) + O + 1
            x0, z0, x1, z1 = max(0, x0), max(0, z0), min(N_CELLS, x1), min(N_CELLS, z1)
            sub = (own[z0:z1, x0:x1] == idx) & climb[z0:z1, x0:x1]
            js, iis = np.nonzero(sub)
            if len(js) == 0:
                continue
            cx = iis + x0 - O + 0.5
            cz = js + z0 - O + 0.5
            d = np.clip((cx - a[0]) * u[0] + (cz - a[1]) * u[1], 0.0, L)
            di = np.clip(np.floor(d + 0.5).astype(int), 0, n - 1)
            Lf[js + z0, iis + x0] = np.array(prof, dtype=np.int16)[di]

    @staticmethod
    def unpin(prof, strong):
        """Where two fixed stretches of different levels meet with no room between (two junctions close together),
        frees the junctions' margins on both sides until the flight fits."""
        n = len(prof)
        d = 0
        while d < n - 1:
            a, b = prof[d], prof[d + 1]
            if a is None or b is None or abs(a - b) < 2:
                d += 1
                continue
            need = (abs(a - b) + 1) // 2
            l, r, freed = d, d + 1, 0
            while freed < need:
                moved = False
                if r < n and not strong[r] and prof[r] == b:
                    prof[r] = None
                    r += 1
                    freed += 1
                    moved = True
                if freed < need and l >= 0 and not strong[l] and prof[l] == a:
                    prof[l] = None
                    l -= 1
                    freed += 1
                    moved = True
                if not moved:
                    break
            d = r

    def fill_profile(self, prof, target, s, owned=None):
        """Fills the free stretches of a street's profile between fixed stretches: the level changes by single steps,
        each 2-step a flight through one odd cell, placed where the ground climbs and spaced by landings."""
        n = len(prof)
        fixed = [d for d in range(n) if prof[d] is not None]
        if not fixed:
            v = int(np.median(target))
            for d in range(n):
                prof[d] = v
            return
        for d in range(fixed[0]):
            prof[d] = prof[fixed[0]]
        for d in range(fixed[-1] + 1, n):
            prof[d] = prof[fixed[-1]]
        d = fixed[0]
        while d < n:
            if prof[d] is not None:
                d += 1
                continue
            d0 = d
            while d < n and prof[d] is None:
                d += 1
            d1 = d - 1
            A, Bv = prof[d0 - 1], prof[d1 + 1]
            levels = [A]
            while levels[-1] != Bv:
                diff = Bv - levels[-1]
                levels.append(levels[-1] + (2 if diff >= 2 else -2 if diff <= -2 else diff))
            k = len(levels) - 1
            span = d1 - d0 + 1
            if k == 0:
                for e in range(d0, d1 + 1):
                    prof[e] = A
                continue
            # (two flights one cell apart make one long flight of single steps: never closer, or a step doubles)
            gap = self.FLIGHT_GAP
            while gap > 2 and k + (k - 1) * (gap - 1) > span:
                gap -= 1
            if k + (k - 1) * (gap - 1) > span:
                # no room for the flight: the street keeps one level (its own paving's side) and ends at a wall
                if owned is None or any(owned[d0:d1 + 1]):
                    self.relief_conflicts.append((s['index'], s['a'], s['b'], A, Bv, span))
                keep = Bv if owned is None or owned[min(d1 + 1, n - 1)] or not owned[max(d0 - 1, 0)] else A
                for e in range(d0, d1 + 1):
                    prof[e] = keep
                continue
            pos = []
            for i in range(k):
                up = levels[i + 1] > levels[i]
                nat = next((e for e in range(d0, d1 + 1) if (target[e] >= levels[i + 1] if up else target[e] <= levels[i + 1])),
                           d0 + (i + 1) * span // (k + 1))
                lo = d0 if not pos else pos[-1] + gap
                hi = d1 - (k - 1 - i) * gap
                pos.append(max(lo, min(nat, hi)))
            for i in range(k - 2, -1, -1):
                pos[i] = min(pos[i], pos[i + 1] - gap)
            # (a flight on the street's own paving, not swallowed by a crossing street's)
            if owned is not None:
                for i in range(k):
                    if not owned[pos[i]]:
                        lo = d0 if i == 0 else pos[i - 1] + 1
                        hi = d1 if i == k - 1 else pos[i + 1] - 1
                        alt = [e for e in range(lo, hi + 1) if owned[e]]
                        if alt:
                            pos[i] = min(alt, key=lambda e: abs(e - pos[i]))
            for e in range(d0, d1 + 1):
                lv = levels[0]
                for i in range(k):
                    if e > pos[i]:
                        lv = levels[i + 1]
                    elif e == pos[i] and abs(levels[i + 1] - levels[i]) == 2:
                        lv = (levels[i] + levels[i + 1]) // 2
                    elif e == pos[i]:
                        lv = levels[i + 1]
                prof[e] = lv

    def stitch(self):
        """The safety net: where a walking line of the street graph still meets ground two or more higher from one cell
        to the next (a street ending at a plaza's retaining wall, a transition swallowed by a crossing street), the
        lower side's row is raised into a flight up to it."""
        K, Lf = self.kind, self.lift
        walk = np.isin(K, list(WALKABLE)) & (K != LOWQUAY)
        self.stitched = 0
        for _ in range(3):
            fixed = 0
            for (a, b, move, tag) in self.graph_lines():
                a, b = np.array(a, float), np.array(b, float)
                n = max(2, int(np.hypot(*(b - a)) * 3))
                prev = None
                for k in range(n + 1):
                    x, z = a + (b - a) * (k / n)
                    i, j = int(math.floor(x)) + O, int(math.floor(z)) + O
                    if not (1 <= i < N_CELLS - 1 and 1 <= j < N_CELLS - 1) or not walk[j, i]:
                        prev = None
                        continue
                    if prev is not None and (i, j) != prev:
                        pi, pj = prev
                        d = int(Lf[j, i]) - int(Lf[pj, pi])
                        if abs(d) >= 2:
                            lo, hi = ((pi, pj), (i, j)) if d > 0 else ((i, j), (pi, pj))
                            fixed += self.raise_row(lo, hi, walk)
                    prev = (i, j)
            self.stitched += fixed
            if not fixed:
                break

    def raise_row(self, lo, hi, walk):
        """Raises the low cell (and the cells beside it along the wall at its level, up to 3 each way) to one under the
        high side's next step, so the line climbs by single steps (repeated passes finish taller drops)."""
        K, Lf = self.kind, self.lift
        (li, lj), (hi_, hj) = lo, hi
        di, dj = hi_ - li, hj - lj
        if di != 0 and dj != 0:
            dj = 0     # a diagonal meeting: step along x
            if not walk[lj, li + di]:
                di, dj = 0, hj - lj
        a = int(Lf[lj, li])
        top = int(Lf[lj + dj, li + di]) if walk[lj + dj, li + di] else int(Lf[hj, hi_])
        if top - a < 2:
            return 0
        new = a + 1 if (top - a) % 2 == 0 or top - a > 2 else a + 1
        Lf[lj, li] = new
        n = 1
        for sgn in (1, -1):
            for k in range(1, 4):
                ci, cj = li + sgn * k * dj, lj + sgn * k * di
                if not walk[cj, ci] or int(Lf[cj, ci]) != a:
                    break
                ni, nj = ci + di, cj + dj
                if not walk[nj, ni] or int(Lf[nj, ni]) < a + 2:
                    break
                Lf[cj, ci] = new
                n += 1
        return n

    def steps(self):
        """A walkable cell one above a walkable neighbour is a step (the painter lays a stair facing up the slope)."""
        K, Lf = self.kind, self.lift
        walk = np.isin(K, list(WALKABLE)) & (K != LOWQUAY)
        stair = np.zeros_like(walk)
        for dz, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            nw = np.roll(np.roll(walk, -dz, axis=0), -dx, axis=1)
            nl = np.roll(np.roll(Lf, -dz, axis=0), -dx, axis=1)
            stair |= walk & nw & (nl == Lf - 1)
        K[stair] = STAIR

    # -- 5. bridges ----------------------------------------------------------------------------------------------
    def make_bridges(self):
        def bridge(bid, name, z, w, breakable=False, canal=False, x_at=None):
            if x_at is None:
                rx = river_x(z)
                half = RIVER_HALF + 2
                x0, x1 = int(math.floor(rx - half - 1)), int(math.ceil(rx + half + 1))
            else:
                x0, x1 = x_at
            z0, z1 = z - w // 2, z - w // 2 + w - 1
            parts = []
            at = x0
            while at <= x1:
                e = min(x1, at + 11)
                if x1 - e < 4:
                    e = x1
                parts.append({'kind': 'bridge', 'box': [at, z0, e, z1], 'axis': 'x', 'h': 0})
                at = e + 1
            self.bridges.append({'id': bid, 'name': name, 'box': [x0, z0, x1, z1], 'axis': 'x', 'breakable': breakable,
                                 'canal': canal})
            self.landmarks.append({'id': bid, 'name': name, 'parts': parts, 'breakable': breakable})
        bridge('bell_bridge', 'Bell Bridge', -10, 8, breakable=True)
        bridge('mill_bridge', 'Mill Bridge', 40, 8)
        bridge('north_bridge', 'North Bridge', -160, 6)
        bridge('tannery_bridge', 'Tannery Bridge', 125, 6)
        # over the canal: Bell Street's crossing and two footbridges
        cx = int(round(-124))
        bridge('canal_bridge_west', 'Lantern Bridge', -10, 8, canal=True, x_at=(cx - 6, cx + 5))
        # canal footbridges run north-south: modelled as x-axis spans of a z box, so give them as z boxes
        for bid, name, x, (z0, z1) in (('canal_bridge_north', 'Rope Bridge', -96, (-43, -30)),
                                       ('canal_bridge_south', 'Washers\' Bridge', -100, (18, 31)),
                                       ('race_bridge_quay', 'Race Bridge', -77, (114, 122)),
                                       ('race_bridge_works', 'Wheelwrights\' Bridge', -38, (114, 122))):
            self.bridges.append({'id': bid, 'name': name, 'box': [x - 2, z0, x + 2, z1], 'axis': 'z',
                                 'breakable': False, 'canal': True})
            self.landmarks.append({'id': bid, 'name': name, 'parts': [{'kind': 'bridge', 'box': [x - 2, z0, x + 2, z1],
                                                                      'axis': 'z', 'h': 0}]})
            self.street((x, z0 - 4), (x, z1 + 4), 4, 3)

    def bridge_cells(self):
        m = np.zeros_like(self.kind, bool)
        for b in self.bridges:
            m |= self.box_mask(*b['box'])
        return m

    # -- 6. plots ------------------------------------------------------------------------------------------------
    PARAMS = {
        'bellwatch': dict(w=(5, 9), d=(7, 13), st=(2, 5), gap=0.05, gapw=(1, 1), roofs=(('gable', 85), ('hip', 15))),
        'quay': dict(w=(8, 14), d=(9, 14), st=(3, 4), gap=0.04, gapw=(1, 2), roofs=(('gable', 90), ('hip', 10))),
        'market': dict(w=(6, 10), d=(8, 13), st=(3, 5), gap=0.03, gapw=(1, 1), roofs=(('hip', 45), ('mansard', 35), ('gable', 20))),
        'terrace': dict(w=(10, 14), d=(10, 14), st=(3, 4), gap=0.35, gapw=(2, 4), roofs=(('hip', 50), ('flat', 50)), front=2),
        'works': dict(w=(12, 14), d=(11, 14), st=(2, 3), gap=0.45, gapw=(3, 6), roofs=(('saw', 45), ('gable', 55))),
        'garden': dict(w=(8, 12), d=(8, 11), st=(2, 3), gap=1.0, gapw=(3, 7), roofs=(('hip', 60), ('gable', 40)), front=2),
        'wallring': dict(w=(6, 10), d=(6, 9), st=(2, 3), gap=0.55, gapw=(3, 6), roofs=(('gable', 100),)),
    }
    FRONT_RANK = {SQUARE: 0, PLAZA: 1, AVENUE: 1, STREET: 2, RING: 2, QUAY: 2, LANE: 3, ALLEY: 4, STAIR: 3, TRAINING: 5}
    # (the lower quay is no frontage: houses face the street-level promenade above it)

    def pack(self):
        """Plots grow from the street: every free cell beside public ground is a seed (avenues and the square first);
        each seed tries the district's plot sizes with the seed on the plot's front wall. Rows come out terraced in the
        dense districts and detached with gardens in the Garden Ward; whatever stays free becomes yards and courts."""
        K = self.kind
        free = (K == FREE) & (self.depth >= WALL_HALF + 6) & ~self.bridge_cells()
        K[(K == FREE) & ~free] = YARD
        dirs = {'N': (0, -1), 'S': (0, 1), 'W': (-1, 0), 'E': (1, 0)}
        public = list(self.FRONT_RANK.keys())
        seeds = []
        for side, (dx, dz) in dirs.items():
            sh = np.roll(np.roll(K, -dz, axis=0), -dx, axis=1)
            m = (K == FREE) & np.isin(sh, public)
            js, iis = np.nonzero(m)
            for j, i in zip(js.tolist(), iis.tolist()):
                seeds.append((self.FRONT_RANK[int(sh[j, i])], rnd(i, j, ord(side), 5), side, i, j))
        seeds.sort()
        self.public_mask = np.isin(K, public)
        pid = 0
        for rk, _, side, i, j in seeds:
            if K[j, i] != FREE:
                continue
            x, z = i - O, j - O
            dname = DISTRICTS[int(self.dist[j, i])]
            if dname == 'none':
                continue
            P = self.PARAMS[dname]
            if rk > P.get('front', 9):
                continue
            w0 = rint(P['w'][0], P['w'][1], i, j, 31)
            d0 = rint(P['d'][0], P['d'][1], i, j, 32)
            placed = None
            for w in range(w0, max(4, P['w'][0] - 2) - 1, -1):
                for dep in range(d0, max(5, P['d'][0] - 3) - 1, -1):
                    for off in (0, w - 1, w // 2):
                        rect = self.rect_at(side, x, z, w, dep, off)
                        if rect and self.all_free(rect) and self.frontage(rect, side) >= min(w, 2):
                            lv = self.front_lift(rect, side, i, j)
                            if lv is not None:
                                placed = rect
                                break
                    if placed:
                        break
                if placed:
                    break
            if not placed:
                continue
            x0_, z0_, x1_, z1_ = placed
            self.lift[z0_ + O:z1_ + O + 1, x0_ + O:x1_ + O + 1] = lv
            self.add_plot(pid, dname, placed, side, rk)
            pid += 1
            if rnd(pid, 7) < P['gap']:
                self.gap_beside(placed, side, rint(P['gapw'][0], P['gapw'][1], pid, 8))
        rest = K == FREE
        garden = rest & (self.dist == D['garden'])
        K[garden] = GARDEN
        K[rest & ~garden] = YARD

    def rect_at(self, side, x, z, w, dep, off):
        """The plot with the seed cell (x, z) on its front wall, `off` cells along it from the wall's start."""
        if side in 'NS':
            x0 = x - off
            x1 = x0 + w - 1
            if side == 'N':
                z0, z1 = z, z + dep - 1
            else:
                z0, z1 = z - dep + 1, z
        else:
            z0 = z - off
            z1 = z0 + w - 1
            if side == 'W':
                x0, x1 = x, x + dep - 1
            else:
                x0, x1 = x - dep + 1, x
        if x1 - x0 > 13 or z1 - z0 > 13:
            return None
        return (x0, z0, x1, z1)

    def frontage(self, r, side):
        x0, z0, x1, z1 = r
        pm = self.public_mask
        if side == 'N':
            row = pm[z0 - 1 + O, x0 + O:x1 + O + 1]
        elif side == 'S':
            row = pm[z1 + 1 + O, x0 + O:x1 + O + 1]
        elif side == 'W':
            row = pm[z0 + O:z1 + O + 1, x0 - 1 + O]
        else:
            row = pm[z0 + O:z1 + O + 1, x1 + 1 + O]
        return int(np.count_nonzero(row))

    def gap_beside(self, r, side, n):
        """Keeps a strip beside the plot free of buildings (a garden gap, a ginnel)."""
        x0, z0, x1, z1 = r
        if side in 'NS':
            box = (x1 + 1, z0, x1 + n, z1)
        else:
            box = (x0, z1 + 1, x1, z1 + n)
        m = self.box_mask(*box) & (self.kind == FREE)
        self.kind[m] = GARDEN if DISTRICTS[int(self.dist[z0 + O, x0 + O])] == 'garden' else YARD

    def free_at(self, side, line, at):
        if side in 'NS':
            return bool(self.kind[line, at] == FREE)
        return bool(self.kind[at, line] == FREE)

    def rect(self, side, line, at, w, depth):
        """The plot rectangle for a frontage cell run: x0, z0, x1, z1 in plan coordinates (inclusive)."""
        if side == 'N':
            x0, x1 = at - O, at - O + w - 1
            z0 = line - O
            z1 = z0 + depth - 1
        elif side == 'S':
            x0, x1 = at - O, at - O + w - 1
            z1 = line - O
            z0 = z1 - depth + 1
        elif side == 'W':
            z0, z1 = at - O, at - O + w - 1
            x0 = line - O
            x1 = x0 + depth - 1
        else:
            z0, z1 = at - O, at - O + w - 1
            x1 = line - O
            x0 = x1 - depth + 1
        if x1 - x0 > 13 or z1 - z0 > 13:
            return None
        return (x0, z0, x1, z1)

    def all_free(self, r):
        x0, z0, x1, z1 = r
        if x0 + O < 0 or z0 + O < 0 or x1 + O >= N_CELLS or z1 + O >= N_CELLS:
            return False
        sub = self.kind[z0 + O:z1 + O + 1, x0 + O:x1 + O + 1]
        if not np.all(sub == FREE):
            return False
        # a landmark stands two cells clear of the houses (its corbels, balconies and buttresses reach a cell out of its
        # box, and a house's facade needs its own outside plane)
        ring = self.kind[max(0, z0 + O - 2):z1 + O + 3, max(0, x0 + O - 2):x1 + O + 3]
        return not np.any(ring == LANDMARK)

    OUT = {'N': (0, -1), 'S': (0, 1), 'W': (-1, 0), 'E': (1, 0)}

    def front_lift(self, r, side, si, sj):
        """The level a plot stands at: the street's in front of its door (every row of the grid is relative to it, so the
        door opens onto the street and the courses meet the next house's). None where the door would open onto a step,
        where the street in front climbs more than one flight along the front, or where the ground under the plot is
        more than a flight away (a house never stands across a retaining wall)."""
        x0, z0, x1, z1 = r
        wx, wz = x1 - x0 + 1, z1 - z0 + 1
        if side in 'NS':
            dx_, dz_ = x0 + (wx - 1) // 2, (z0 if side == 'N' else z1)
        else:
            dx_, dz_ = (x0 if side == 'W' else x1), z0 + (wz - 1) // 2
        ox, oz = self.OUT[side]
        fi, fj = dx_ + ox + O, dz_ + oz + O
        if not self.public_mask[fj, fi]:
            fi, fj = si + ox, sj + oz
        if self.kind[fj, fi] == STAIR or self.lift[fj, fi] % 2 != 0:
            return None
        lv = int(self.lift[fj, fi])
        if side == 'N':
            row_k, row_l = self.kind[z0 - 1 + O, x0 + O:x1 + O + 1], self.lift[z0 - 1 + O, x0 + O:x1 + O + 1]
        elif side == 'S':
            row_k, row_l = self.kind[z1 + 1 + O, x0 + O:x1 + O + 1], self.lift[z1 + 1 + O, x0 + O:x1 + O + 1]
        elif side == 'W':
            row_k, row_l = self.kind[z0 + O:z1 + O + 1, x0 - 1 + O], self.lift[z0 + O:z1 + O + 1, x0 - 1 + O]
        else:
            row_k, row_l = self.kind[z0 + O:z1 + O + 1, x1 + 1 + O], self.lift[z0 + O:z1 + O + 1, x1 + 1 + O]
        pub = np.isin(row_k, list(self.FRONT_RANK.keys()))
        if np.any(np.abs(row_l[pub].astype(int) - lv) > 2):
            return None
        under = self.lift[z0 + O:z1 + O + 1, x0 + O:x1 + O + 1].astype(int)
        if np.max(np.abs(under - lv)) > 2:
            return None
        return lv

    def add_plot(self, pid, dname, rect, side, rank, door=True):
        x0, z0, x1, z1 = rect
        self.kind[z0 + O:z1 + O + 1, x0 + O:x1 + O + 1] = PLOT
        self.plot_id[z0 + O:z1 + O + 1, x0 + O:x1 + O + 1] = pid
        P = self.PARAMS[dname]
        st = self.target_storeys(pid, dname, rect, side, rank)
        roll = rnd(pid, 23) * 100
        roof = P['roofs'][-1][0]
        acc = 0
        for name, pct in P['roofs']:
            acc += pct
            if roll < acc:
                roof = name
                break
        wx, wz = x1 - x0 + 1, z1 - z0 + 1
        front_axis = 'x' if side in 'NS' else 'z'   # the front wall runs along this axis
        frontage = wx if side in 'NS' else wz
        if dname == 'bellwatch' and frontage <= 7 and roof == 'gable':
            ridge = 'z' if front_axis == 'x' else 'x'   # gable to the street
        elif roof == 'saw':
            ridge = front_axis
        else:
            ridge = 'x' if wx >= wz else 'z'
        pitch = {'bellwatch': 'steep', 'quay': 'normal', 'market': 'normal', 'terrace': 'normal', 'works': 'normal',
                 'garden': 'normal', 'wallring': 'steep'}[dname]
        eave = 5 + 4 * (st - 1)
        span = (wz if ridge == 'x' else wx)
        top = eave + 1 if roof == 'flat' else eave + rise(pitch, (span + 1) // 2) + 1
        # the door: middle of the front wall (the cell outside it is public ground)
        if side in 'NS':
            dx_ = x0 + (wx - 1) // 2
            dz_ = z0 if side == 'N' else z1
        else:
            dz_ = z0 + (wz - 1) // 2
            dx_ = x0 if side == 'W' else x1
        lift = int(self.lift[z0 + O, x0 + O])
        self.plots.append({'id': pid, 'district': dname, 'box': [x0, z0, x1, z1], 'front': side, 'storeys': st,
                           'roof': roof, 'ridge': ridge, 'pitch': pitch, 'eave': eave, 'top': top, 'lift': lift,
                           'door': [dx_, dz_] if door else None, 'rank': rank, 'use': 'house', 'enter': False,
                           'variant': rint(0, 255, pid, 29)})

    def target_storeys(self, pid, dname, rect, side, rank):
        """Storeys by the height plan (look bible §5, §6): the Market tallest on the square (5-6) stepping down to 4 at
        the spokes' ends; Bellwatch rising toward the Knoll (2-3 at the edges, 3-4 on its slopes, 4-5 round the bell
        square); the Quay a uniform wall of 4 along the water, 3 behind; the rest as their district's range."""
        P = self.PARAMS[dname]
        x0, z0, x1, z1 = rect
        cx, cz = (x0 + x1 + 1) / 2, (z0 + z1 + 1) / 2
        if dname == 'market':
            r = math.hypot(cx, cz)
            lo, hi = (5, 6) if rank == 0 else ((4, 5) if r < 62 or rank <= 1 else (4, 4))
            st = rint(lo, hi, pid, 21)
        elif dname == 'bellwatch':
            rk = math.hypot(cx - KNOLL[0], cz - KNOLL[1])
            lo, hi = (4, 5) if rk < 28 else ((3, 4) if rk < 46 else (2, 3))
            st = min(5, rint(lo, hi, pid, 21) + (1 if rank <= 1 else 0))
        elif dname == 'quay':
            ox, oz = self.OUT[side]
            if side in 'NS':
                fx, fz = (x0 + x1) // 2, (z0 if side == 'N' else z1)
            else:
                fx, fz = (x0 if side == 'W' else x1), (z0 + z1) // 2
            st = 4 if self.cell(fx + ox, fz + oz) == QUAY else 3
        else:
            st = rint(P['st'][0], P['st'][1], pid, 21)
            if rank <= 1:
                st += 1
            if rank == 0:
                st += 1
        return max(1, min(6, st))

    @staticmethod
    def set_storeys(p, st):
        """Storeys, the eave and the roof's top (relative to the plot's floor) from the storey count."""
        p['storeys'] = st
        p['eave'] = 5 + 4 * (st - 1)
        x0, z0, x1, z1 = p['box']
        span = (z1 - z0 + 1) if p['ridge'] == 'x' else (x1 - x0 + 1)
        p['top'] = p['eave'] + 1 if p['roof'] == 'flat' else p['eave'] + rise(p['pitch'], (span + 1) // 2) + 1

    MAX_STOREYS = {'market': 6, 'bellwatch': 5, 'quay': 4, 'terrace': 5, 'works': 4, 'garden': 4, 'wallring': 3}
    TOWERS = (-157.5, -67.5, 22.5, 112.5)

    def merchant_towers(self):
        """Merchant tower-houses (7 storeys, about 33 over the street) at four of the Market spokes' outer ends: the
        ward's edge read from afar (look bible §5.3, §6)."""
        for a in self.TOWERS:
            ex, ez = 84 * math.cos(math.radians(a)), 84 * math.sin(math.radians(a))
            best = None
            for p in self.plots:
                if p['district'] != 'market' or p['enter']:
                    continue
                x0, z0, x1, z1 = p['box']
                d = math.hypot((x0 + x1 + 1) / 2 - ex, (z0 + z1 + 1) / 2 - ez)
                if d < 14 and (best is None or d < best[0]):
                    best = (d, p)
            if best:
                self.set_storeys(best[1], 7)
                best[1]['tower'] = True

    def neighbours(self, gap):
        """Plots whose boxes face each other across at most `gap` (0: sharing a wall), by plot id."""
        cell = {}
        for p in self.plots:
            x0, z0, x1, z1 = p['box']
            for cx in range((x0 - gap - 1) // 16, (x1 + gap + 1) // 16 + 1):
                for cz in range((z0 - gap - 1) // 16, (z1 + gap + 1) // 16 + 1):
                    cell.setdefault((cx, cz), []).append(p)
        out = {p['id']: [] for p in self.plots}
        for p in self.plots:
            x0, z0, x1, z1 = p['box']
            seen = set()
            for cx in range((x0 - gap - 1) // 16, (x1 + gap + 1) // 16 + 1):
                for cz in range((z0 - gap - 1) // 16, (z1 + gap + 1) // 16 + 1):
                    for q in cell.get((cx, cz), ()):
                        if q['id'] == p['id'] or q['id'] in seen:
                            continue
                        seen.add(q['id'])
                        gx = max(q['box'][0] - x1 - 1, x0 - q['box'][2] - 1)
                        gz = max(q['box'][1] - z1 - 1, z0 - q['box'][3] - 1)
                        if gx >= 0 and gz >= 0:
                            continue  # diagonal only
                        if max(gx, gz) <= gap:
                            out[p['id']].append(q)
        return out

    def chain(self):
        """Roofs that chain (look bible §3.4): along a row of houses, neighbours stand within one storey (4) of each
        other, counting the ground they stand on; and every roof has some neighbour within a storey. The taller wins
        first: a neighbour more than a storey under a taller roof is raised as far as its district allows (towers get
        stepped shoulders), then whatever is still out of step is lowered."""
        side = self.neighbours(0)
        near = self.neighbours(4)

        def top(p):
            return p['lift'] + p['eave']

        def cap(p):
            return 7 if p.get('tower') else self.MAX_STOREYS[p['district']]

        def row(p, q):
            if p['front'] != q['front'] or abs(p['lift'] - q['lift']) > 2:
                return False
            ax = 'x' if p['front'] in 'NS' else 'z'
            if ax == 'x':
                return q['box'][0] == p['box'][2] + 1 or p['box'][0] == q['box'][2] + 1
            return q['box'][1] == p['box'][3] + 1 or p['box'][1] == q['box'][3] + 1

        rows = {p['id']: [q for q in side[p['id']] if row(p, q)] for p in self.plots}

        def reach(p, q):
            # from anywhere on p's roof (eave to ridge) onto q's: a step up of a storey at most, a drop of 6 at most
            return (q['lift'] + q['eave']) - (p['lift'] + p['top']) <= 4 and (p['lift'] + p['eave']) - (q['lift'] + q['top']) <= 6
        for _round in range(8):
            before = [q['storeys'] for q in self.plots]
            for _ in range(12):
                changed = False
                for p in sorted(self.plots, key=lambda p: (-top(p), p['id'])):
                    for q in rows[p['id']]:
                        d = top(p) - top(q)
                        if d > 4 and q['storeys'] < cap(q):
                            self.set_storeys(q, min(cap(q), q['storeys'] + (d - 1) // 4))
                            changed = True
                for p in sorted(self.plots, key=lambda p: (-top(p), p['id'])):
                    if p.get('tower'):
                        continue
                    for q in rows[p['id']]:
                        d = top(p) - top(q)
                        if d > 4 and p['storeys'] > 2:
                            self.set_storeys(p, max(2, p['storeys'] - (d - 1) // 4))
                            changed = True
                if not changed:
                    break
            # every roof within a storey of some neighbour (across a lane too)
            for _ in range(6):
                changed = False
                for p in sorted(self.plots, key=lambda p: p['id']):
                    ns = near[p['id']]
                    if not ns or any(reach(p, q) for q in ns) or self.landing(p):
                        continue
                    q = min(ns, key=lambda q: (abs(top(q) - top(p)), q['id']))
                    d = top(q) - top(p)
                    if d > 0 and p['storeys'] < cap(p):
                        self.set_storeys(p, min(cap(p), p['storeys'] + (d - 1) // 4))
                    elif d < 0 and q['storeys'] < cap(q):
                        self.set_storeys(q, min(cap(q), q['storeys'] + (-d - 1) // 4))
                    elif d < 0 and not p.get('tower') and p['storeys'] > 2:
                        self.set_storeys(p, max(2, p['storeys'] - (-d - 1) // 4))
                    else:
                        continue
                    changed = True
                if not changed:
                    break
            if [q['storeys'] for q in self.plots] == before:
                break
        self.chain_stats = {
            'row_out_of_step': sum(1 for p in self.plots for q in rows[p['id']] if abs(top(p) - top(q)) > 4) // 2,
            'alone': sum(1 for p in self.plots if not near[p['id']]),
            'no_neighbour_within_storey': [p['id'] for p in self.plots if near[p['id']]
                                           and not any(reach(p, q) for q in near[p['id']]) and not self.landing(p)]}

    def landing(self, p):
        """A soft way off a roof without a neighbour: open ground beside the house (raised ground, a terrace) at most 6
        under its eave, or water within 3."""
        x0, z0, x1, z1 = p['box']
        e = p['lift'] + p['eave']
        for x in range(x0 - 3, x1 + 4):
            for z in range(z0 - 3, z1 + 4):
                k = self.cell(x, z)
                if k in WATER:
                    return True
                if x0 - 1 <= x <= x1 + 1 and z0 - 1 <= z <= z1 + 1 and k in {YARD, GARDEN, FREE} | WALKABLE \
                        and e - int(self.lift[z + O, x + O]) <= 6:
                    return True
        return False

    def courts(self):
        """Leftover ground deep inside a block: one back building in the middle of each big court (not gardens)."""
        from collections import deque
        K = self.kind
        seen = np.zeros_like(K, bool)
        pid = len(self.plots)
        for j in range(N_CELLS):
            for i in range(N_CELLS):
                if K[j, i] != FREE or seen[j, i]:
                    continue
                comp = []
                dq = deque([(j, i)])
                seen[j, i] = True
                while dq:
                    a, b = dq.popleft()
                    comp.append((a, b))
                    for da, db in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        na, nb = a + da, b + db
                        if 0 <= na < N_CELLS and 0 <= nb < N_CELLS and not seen[na, nb] and K[na, nb] == FREE:
                            seen[na, nb] = True
                            dq.append((na, nb))
                dname = DISTRICTS[int(self.dist[j, i])]
                if len(comp) < 220 or dname in ('garden', 'none', 'terrace'):
                    continue
                cj = sum(c[0] for c in comp) // len(comp)
                ci = sum(c[1] for c in comp) // len(comp)
                for size in (12, 10, 8, 6):
                    r = (ci - O - size // 2, cj - O - size // 2, ci - O - size // 2 + size - 1, cj - O - size // 2 + size - 1)
                    grown = (r[0] - 2, r[1] - 2, r[2] + 2, r[3] + 2)
                    if self.all_free(grown):
                        self.add_plot(pid, dname, r, 'N', 5, door=False)
                        self.plots[-1]['use'] = 'workshop'
                        pid += 1
                        break

    # -- 7. trees, lamps, uses ----------------------------------------------------------------------------------
    def greenery(self):
        K = self.kind
        for j in range(2, N_CELLS - 2, 6):
            for i in range(2, N_CELLS - 2, 6):
                jj = j + rint(-1, 1, i, j, 41)
                ii = i + rint(-1, 1, i, j, 42)
                sub = K[jj - 2:jj + 3, ii - 2:ii + 3]
                dname = DISTRICTS[int(self.dist[jj, ii])]
                if np.all(sub == GARDEN) or (np.all(np.isin(sub, [YARD, GARDEN])) and dname in ('terrace', 'market', 'bellwatch')
                                             and rnd(i, j, 43) < 0.35):
                    kind = 'cherry' if dname == 'garden' and rnd(i, j, 44) < 0.25 else (
                        'birch' if rnd(i, j, 45) < 0.3 else 'oak')
                    self.trees.append([ii - O, jj - O, kind, rint(2, 3, i, j, 46)])
        # boulevard trees on the avenues of the Garden Ward and the Terrace (one cell in from the kerb)
        for s in self.streets:
            if s['rank'] != 1:
                continue
            (ax, az), (bx, bz) = s['a'], s['b']
            L = math.hypot(bx - ax, bz - az)
            if L < 12:
                continue
            ux, uz = (bx - ax) / L, (bz - az) / L
            nx, nz = -uz, ux
            for t in np.arange(6, L - 4, 12):
                for side in (-1, 1):
                    x = ax + ux * t + nx * side * (s['w'] / 2 - 1.5)
                    z = az + uz * t + nz * side * (s['w'] / 2 - 1.5)
                    dname = DISTRICTS[int(self.dist[int(math.floor(z)) + O, int(math.floor(x)) + O])] if abs(x) < O and abs(z) < O else 'none'
                    if dname in ('garden', 'terrace') and self.cell(x, z) == AVENUE:
                        self.trees.append([int(math.floor(x)), int(math.floor(z)), 'avenue', 1])
                    elif self.cell(x, z) in (AVENUE, STREET) and int(t) % 24 == 6:
                        self.lamps.append([int(math.floor(x)), int(math.floor(z))])
        for k in range(8):
            a = math.radians(k * 45 + 22.5)
            self.lamps.append([int(round(20 * math.cos(a))), int(round(20 * math.sin(a)))])

    def uses(self):
        """About 60-70 enterable buildings: the square's shops, taverns, and the street route of the opening."""
        by_id = {p['id']: p for p in self.plots}
        for p in self.plots:
            if p['rank'] == 0:
                p['use'], p['enter'] = 'shop', True
        # two taverns per district: the biggest plots on its best streets
        for dname in self.PARAMS:
            cands = sorted((p for p in self.plots if p['district'] == dname and p['door'] and p['rank'] <= 2),
                           key=lambda p: (-(p['box'][2] - p['box'][0] + 1) * (p['box'][3] - p['box'][1] + 1), p['id']))
            for p in cands[:2]:
                p['use'], p['enter'] = 'tavern', True
        # market shops along the first ring
        shops = [p for p in self.plots if p['district'] == 'market' and p['rank'] <= 2 and not p['enter']]
        shops.sort(key=lambda p: (math.hypot(*door_xz(p)), p['id']))
        # (thirty shops in all, the square's first: the citizens' roster keeps a shopkeeper for each)
        for p in shops[:max(0, 30 - sum(1 for q in self.plots if q['use'] == 'shop'))]:
            p['use'], p['enter'] = 'shop', True
        self.by_id = by_id

    # -- 7b. crossings over the avenues (look bible §3.5) --------------------------------------------------------
    CROSS_EVERY = 48     # a line across every avenue (and the Ring) at least this often, where roofs line it
    CROSS_BREAK = 20     # a stretch with nothing on either side this long breaks the rooftop line

    def avenue_lines(self):
        """The avenues and the Ring as ordered runs of segments (named groups of rank-1 streets, in the order made)."""
        groups = {}
        for s in self.streets:
            if s['rank'] == 1 or s['name'] == 'The Ring':
                groups.setdefault(s['name'] or 'avenue %d' % s['index'], []).append(s)
        return groups

    def facade_across(self, x, z, nx, nz, reach):
        """The first plot met walking from (x, z) along (nx, nz) over open street, and how far: (plot, steps) or None."""
        for k in range(1, int(reach * 2) + 1):
            px, pz = x + nx * k * 0.5, z + nz * k * 0.5
            c = self.cell(px, pz)
            if c == PLOT:
                return self.plots[int(self.plot_id[int(math.floor(pz)) + O, int(math.floor(px)) + O])], k * 0.5
            if c not in (AVENUE, STREET, LANE, ALLEY, RING, STAIR, PLAZA):
                return None
        return None

    def crossings(self):
        """A line across every avenue and the Ring at most every 48 blocks where houses line it (bible §3.5): an enclosed
        walkway between two facing upper floors where the avenue runs square and both houses stand 3+ storeys (built
        now, a landmark part), else a hang line between the facing first-floor courses at h 9 (its site marked for B3,
        who hangs the rope, bunting or lantern string). Each is a roof-graph edge."""
        self.crossing_sites = []
        parts = []
        by_id = {p['id']: p for p in self.plots}
        for name, segs in self.avenue_lines().items():
            samples = []      # chainage, x, z, ux, uz, width, segment
            ch = 0.0
            prev = None
            for s in segs:
                a, b = np.array(s['a'], float), np.array(s['b'], float)
                if prev is not None:
                    ch += float(np.hypot(*(a - prev)))
                L = float(np.hypot(*(b - a)))
                u = (b - a) / max(L, 1e-9)
                for k in range(int(L) + 1):
                    p = a + u * k
                    samples.append((ch + k, p[0], p[1], u[0], u[1], s['w'], s))
                ch += L
                prev = b
            lined = []
            for (c, x, z, ux, uz, w, s) in samples:
                left = self.facade_across(x, z, -uz, ux, w / 2 + 2)
                right = self.facade_across(x, z, uz, -ux, w / 2 + 2)
                lined.append((left, right))
            # stretches: lined samples, broken where nothing lines the avenue for 20 blocks or more
            stretches, cur, gapless = [], [], 0
            for k in range(len(samples)):
                if lined[k][0] or lined[k][1]:
                    cur.append(k)
                    gapless = 0
                else:
                    gapless += 1
                    if gapless >= self.CROSS_BREAK and cur:
                        stretches.append(cur)
                        cur = []
            if cur:
                stretches.append(cur)
            for st in stretches:
                last = samples[st[0]][0]
                end = samples[st[-1]][0]
                sites = {}
                while end - last > self.CROSS_EVERY:
                    window = [k for k in st if last < samples[k][0] <= last + self.CROSS_EVERY]
                    best = None
                    for k in window:
                        if k not in sites:
                            sites[k] = self.crossing_site(name, samples, lined, k)
                        site = sites[k]
                        if site is None:
                            continue
                        ahead = samples[k][0] - last
                        sc = site['score'] * (1.0 if ahead >= 30 else 0.25) + 0.6 * ahead / self.CROSS_EVERY
                        if best is None or sc > best[0]:
                            best = (sc, site)
                    if best is None:
                        break
                    self.crossing_sites.append(best[1])
                    if best[1]['kind'] == 'walkway':
                        parts.append(best[1]['part'])
                    last = best[1]['chainage']
        for cs in self.crossing_sites:
            cs.pop('part', None)
            cs.pop('sample', None)
            cs.pop('score', None)
        if parts:
            self.landmarks.append({'id': 'crossings', 'name': 'Walkways over the avenues', 'parts': parts})
        self.by_id = by_id

    def crossing_site(self, name, samples, lined, k):
        c, x, z, ux, uz, w, s = samples[k]
        left, right = lined[k]
        if not left and not right:
            return None
        square = abs(ux) < 1e-6 or abs(uz) < 1e-6
        if left and right and square and 1 <= k < len(samples) - 1:
            A, da = left
            B, db = right
            ok = all(lined[k + o][0] and lined[k + o][1] and lined[k + o][0][0] is A and lined[k + o][1][0] is B
                     and lined[k + o][0][1] == da and lined[k + o][1][1] == db for o in (-1, 1))
            ea, eb = A['lift'] + A['eave'], B['lift'] + B['eave']
            g = int(self.lift[int(math.floor(z)) + O, int(math.floor(x)) + O])
            deck = min(ea, eb)
            if ok and min(A['storeys'], B['storeys']) >= 3 and abs(ea - eb) <= 4 and deck - g >= 13:
                # the box: between the two fronts across the avenue, three wide along it
                nx, nz = -uz, ux
                ax, az = x + nx * (da - 0.5), z + nz * (da - 0.5)
                bx, bz = x - nx * (db - 0.5), z - nz * (db - 0.5)
                cx0 = int(math.floor(min(ax, bx) + 0.25)) if abs(nx) > 0.5 else int(math.floor(x)) - 1
                cx1 = int(math.floor(max(ax, bx) - 0.25)) if abs(nx) > 0.5 else int(math.floor(x)) + 1
                cz0 = int(math.floor(min(az, bz) + 0.25)) if abs(nz) > 0.5 else int(math.floor(z)) - 1
                cz1 = int(math.floor(max(az, bz) - 0.25)) if abs(nz) > 0.5 else int(math.floor(z)) + 1
                box = [cx0, cz0, cx1, cz1]
                cells = self.kind[cz0 + O:cz1 + O + 1, cx0 + O:cx1 + O + 1]
                if np.all(np.isin(cells, list(WALKABLE))) and max(cx1 - cx0, cz1 - cz0) <= 13:
                    axis = 'x' if abs(nx) > 0.5 else 'z'
                    style = A['district']
                    part = {'kind': 'walkway', 'box': box, 'h': deck, 'axis': axis, 'style': style}
                    return {'kind': 'walkway', 'street': name, 'at': [round(x, 1), round(z, 1)],
                            'a': [round(ax, 1), deck, round(az, 1)], 'b': [round(bx, 1), deck, round(bz, 1)],
                            'plots': [A['id'], B['id']], 'chainage': round(c, 1), 'sample': k, 'part': part,
                            'score': 3.0 - abs(ea - eb) * 0.1}
        # a hang line between the facing first-floor courses (h 9 over each house's floor), or to a post at the kerb
        g = int(self.lift[int(math.floor(z)) + O, int(math.floor(x)) + O])
        nx, nz = -uz, ux
        ends = []
        for side, hit, sgn in (('a', left, 1), ('b', right, -1)):
            if hit:
                P, d = hit
                ex, ez = x + sgn * nx * (d - 0.75), z + sgn * nz * (d - 0.75)
                ends.append(([round(ex, 1), P['lift'] + 9, round(ez, 1)], P['id']))
            else:
                ex, ez = x + sgn * nx * (w / 2 - 0.5), z + sgn * nz * (w / 2 - 0.5)
                gp = int(self.lift[int(math.floor(ez)) + O, int(math.floor(ex)) + O])
                ends.append(([round(ex, 1), gp + 9, round(ez, 1)], None))
        # (a line runs level: never across a flight or a terrace's edge)
        if abs(ends[0][0][1] - ends[1][0][1]) > 2:
            return None
        both = left and right
        return {'kind': 'line', 'street': name, 'at': [round(x, 1), round(z, 1)], 'a': ends[0][0], 'b': ends[1][0],
                'plots': [ends[0][1], ends[1][1]], 'anchor': 'facades' if both else 'post', 'chainage': round(c, 1),
                'sample': k, 'score': 1.0 if both else 0.2}

    # -- 8. graphs -----------------------------------------------------------------------------------------------
    def graphs(self):
        lines = self.graph_lines()
        self.graph_from(lines)

    def graph_lines(self):
        lines = []   # (a, b, move, tag)
        for s in self.streets:
            lines.append((tuple(s['a']), tuple(s['b']), 'walk', None))
        # the ring lane, the quays, the square
        rr = APOTHEM - (WALL_HALF + 3)
        ring = []
        for k in range(25):
            a = math.radians(7.5 + 15 * k)
            rad = rr / math.cos(math.radians(7.5))
            ring.append((rad * math.cos(a), rad * math.sin(a)))
        for p, q in zip(ring, ring[1:]):
            lines.append((p, q, 'walk', None))
        for off in (-(RIVER_HALF + LOW_QUAY_W + 2), RIVER_HALF + LOW_QUAY_W + 2):
            pts = offset_line(RIVER_PTS, off)
            for p, q in zip(pts, pts[1:]):
                lines.append((p, q, 'walk', None))
        for off in (-(CANAL_HALF + CANAL_LOW_W + 1.5), CANAL_HALF + CANAL_LOW_W + 1.5):
            pts = offset_line(CANAL_PTS, off)
            for p, q in zip(pts, pts[1:]):
                lines.append((p, q, 'walk', None))
        circ = [(30 * math.cos(math.radians(a)), 30 * math.sin(math.radians(a))) for a in range(0, 361, 15)]
        for p, q in zip(circ, circ[1:]):
            lines.append((p, q, 'walk', None))
        for a in range(0, 360, 45):
            lines.append(((0.0, 0.0), (36 * math.cos(math.radians(a)), 36 * math.sin(math.radians(a))), 'walk', None))
        # every street that starts at the square is joined to the ring path at its own bearing (M4 B3a: no longer to
        # the middle, so the eight winds stay the square's only aisles and its stalls stand between them)
        for st in self.streets:
            for end in (st['a'], st['b']):
                r = math.hypot(*end)
                if 30.5 < r < 42:
                    lines.append(((end[0] * 30.0 / r, end[1] * 30.0 / r), tuple(end), 'walk', None))
                elif r <= 30.5:
                    lines.append(((0.0, 0.0), tuple(end), 'walk', None))
        for key, (bx, bz) in self.bells.items():
            for (ax_, az_), (bx_, bz_) in (((bx - 16, bz - 9), (bx + 15, bz - 9)), ((bx - 16, bz + 8), (bx + 15, bz + 8)),
                                           ((bx - 9, bz - 16), (bx - 9, bz + 15)), ((bx + 8, bz - 16), (bx + 8, bz + 15))):
                lines.append(((ax_, az_), (bx_, bz_), 'walk', None))
        # (the Association's plaza: its south row too, where the Terrace's front houses open onto it)
        lines.append(((-25.5, -104.5), (24.5, -104.5), 'walk', None))
        for (ax_, az_), (bx_, bz_) in (((-29, -120), (28, -120)), ((0.5, -104), (0.5, -136)), ((-29, -104), (-29, -136)),
                                       ((28, -104), (28, -136)), ((-27, -176), (26, -176)), ((-37, -176), (-27, -176)),
                                       ((26, -176), (37, -176))):
            lines.append(((ax_, az_), (bx_, bz_), 'walk', None))
        # the gatehouse yard outside the High Gate, through the passage
        lines.append(((0.5, -230.0), (0.5, -252.0), 'walk', None))
        for b in self.bridges:
            x0, z0, x1, z1 = b['box']
            if b['axis'] == 'x':
                zc = (z0 + z1 + 1) / 2
                lines.append(((x0 - 3, zc), (x1 + 4, zc), 'bridge', b['id']))
            else:
                xc = (x0 + x1 + 1) / 2
                lines.append(((xc, z0 - 3), (xc, z1 + 4), 'bridge', b['id']))
        for st in self.stairs:
            x0, z0, x1, z1 = st['box']
            xc, zc = (x0 + x1 + 1) / 2, (z0 + z1 + 1) / 2
            if st['down'] in 'ns':
                lines.append(((xc, z0 - 3), (xc, z1 + 4), 'stairs', None))
            else:
                lines.append(((x0 - 3, zc), (x1 + 4, zc), 'stairs', None))
        return lines

    def graph_from(self, lines):
        bridge_mask = self.bridge_cells()
        g = Graph(self, bridge_mask)
        g.build(lines)
        # doors
        for p in self.plots:
            if not p['door']:
                continue
            dx_, dz_ = p['door']
            ox, oz = {'N': (0, -1), 'S': (0, 1), 'W': (-1, 0), 'E': (1, 0)}[p['front']]
            sx, sz = dx_ + ox + 0.5, dz_ + oz + 0.5
            p['street'] = g.attach(sx, sz, ('door', p['id']), dx_ + 0.5, dz_ + 0.5)
        # landmark doors: the Roost's hall (the start), the Association, the Choir, the market hall, gate passages
        t = self.roost
        hall = self.landmarks_by_id('roost')['parts'][2]['box']
        door = ((hall[0] + hall[2] + 1) / 2, hall[3] + 0.5)
        self.start_street = g.attach(door[0], door[1] + 2, ('landmark', 'roost'), door[0], door[1])
        for (x, z, lid) in ((0.5, -38.5, 'choir'), (0.5, -136.5, 'association'), (-36.5, -25.5, 'market_hall')):
            g.attach(x, z, ('landmark', lid), x, z)
        g.rejoin()
        self.street_graph = g
        self.roof_graph = roof_graph(self, g)

    def cliffs(self):
        """Places where a street-graph edge crosses walkable ground that changes level by more than one block between
        neighbouring cells (a retaining wall without its steps): there should be none."""
        sg = self.street_graph
        bad = []
        for (u, v), (move, tag) in sg.edges.items():
            if move == 'door':
                continue
            a, b = np.array(sg.nodes[u][:2], float), np.array(sg.nodes[v][:2], float)
            n = max(2, int(np.hypot(*(b - a)) * 3))
            prev = None
            for k in range(n + 1):
                x, z = a + (b - a) * (k / n)
                i, j = int(math.floor(x)) + O, int(math.floor(z)) + O
                kd = int(self.kind[j, i])
                if kd in WATER:
                    lv = 0
                elif kd in WALKABLE and kd != LOWQUAY:
                    lv = int(self.lift[j, i])
                else:
                    prev = None
                    continue
                if prev is not None and (i, j) != prev[1] and abs(lv - prev[0]) > 1:
                    bad.append(((round(x), round(z)), prev[0], lv))
                prev = (lv, (i, j))
        return bad

    def landmarks_by_id(self, lid):
        return next(l for l in self.landmarks if l['id'] == lid)

    # -- output --------------------------------------------------------------------------------------------------
    def spots(self):
        """Named spots other modules ask for through CityHooks.spot (SPEC §16.9, decision 29): plan x, z and ry, the
        block a thing stands in (feet), relative to the street."""
        kx, kz = KNOLL
        return {
            'high_gate_yard': [0, 1, -250],          # the yard's middle, on the road outside the High Gate
            'quartermaster': [10, 1, -249],          # east of the road: Quartermaster Hale (the living module)
            'bounty_post': [-10, 1, -249],           # west of the road: the bounty post (the living module)
            'association_board': [0, TERRACE_LIFT + 1, -149],   # the Association atrium's north wall, facing the great door
            'bellwatch_cellar': [kx - 1, 1, kz],     # the room under Bellwatch's bell tower, in the Knoll
            'hettie': [-18, -1, -4],                 # the market square (its floor is 2 below the street) by the cloth and produce stalls: Hettie (SPEC §43.1)
        }

    def to_json(self):
        def layer(a):
            return base64.b64encode(zlib.compress(a.astype(np.uint8).tobytes(), 9)).decode('ascii')
        plots = []
        fields = ['id', 'district', 'x0', 'z0', 'x1', 'z1', 'front', 'storeys', 'roof', 'ridge', 'pitch', 'eave', 'top',
                  'lift', 'doorX', 'doorZ', 'use', 'enter', 'variant', 'tower']
        for p in self.plots:
            d = p['door'] or [None, None]
            plots.append([p['id'], p['district'], *p['box'], p['front'], p['storeys'], p['roof'], p['ridge'], p['pitch'],
                          p['eave'], p['top'], p['lift'], d[0], d[1], p['use'], p['enter'], p['variant'], bool(p.get('tower'))])
        sg, rg = self.street_graph, self.roof_graph
        t = self.roost
        out = {
            'schemaVersion': 2,
            'name': 'Carillon',
            'generator': 'tools/city/plan.py',
            'note': 'Plan coordinates: blocks from the site centre, x east, z south; heights from the street (ry 0 = paving).',
            'streetY': 70,
            'raster': {'size': N_CELLS, 'origin': -O, 'encoding': 'zlib+base64, row-major by z then x, one byte a cell; lift signed (two\'s complement)',
                       'kinds': KIND_NAMES, 'kind': layer(self.kind), 'district': layer(self.dist),
                       'lift': layer(self.lift.astype(np.int8).view(np.uint8))},
            'relief': {'knoll': {'x': KNOLL[0], 'z': KNOLL[1], 'top': KNOLL_TOP, 'rings': [[46, 2], [37, 4], [28, 6], [19, 8]]},
                       'garden': {'top': GARDEN_TOP, 'rings': [[172, 2], [196, 4]]},
                       'bowl': [[r, lv] for r, lv in BOWL], 'terrace': TERRACE_LIFT,
                       'note': 'even lifts on every plot; streets climb in flights of single steps (look bible §6)'},
            'crossings': [{k: v for k, v in c.items()} for c in self.crossing_sites],
            'spots': self.spots(),
            'districts': [{'id': D[k], 'key': k, 'name': DISTRICT_NAMES[k], 'bell': list(self.bells.get(k, (0, -46)))}
                          for k in DISTRICTS[1:]],
            'square': {'x': 0, 'z': 0, 'r': SQUARE_R},
            'wall': {'radius': WALL_R, 'thickness': 2 * WALL_HALF, 'height': WALL_H, 'towerHeight': TOWER_H,
                     'towers': self.towers, 'gates': self.gates},
            'river': [list(p) for p in RIVER_PTS], 'riverHalfWidth': RIVER_HALF, 'quayWidth': QUAY_W,
            'canal': [list(p) for p in CANAL_PTS], 'canalHalfWidth': CANAL_HALF,
            'millRace': [list(p) for p in RACE_PTS], 'millRaceHalfWidth': RACE_HALF,
            'terraceLift': TERRACE_LIFT,
            'streets': [{'rank': s['rank'], 'width': s['w'], 'a': [round(s['a'][0], 1), round(s['a'][1], 1)],
                         'b': [round(s['b'][0], 1), round(s['b'][1], 1)], **({'name': s['name']} if s['name'] else {})}
                        for s in self.streets],
            'bridges': self.bridges,
            'stairs': self.stairs,
            'plotFields': fields,
            'plots': plots,
            'landmarks': self.landmarks,
            'outside': {'fieldsFrom': 252, 'fieldsTo': 318, 'roadWidth': 8,
                        'roads': [[g['x'], g['z'], int(g['x'] * 1.4), int(g['z'] * 1.4)] for g in self.gates]},
            'trees': self.trees,
            'lamps': self.lamps,
            'propFields': ['kind', 'x0', 'z0', 'x1', 'z1', 'ry', 'facing', 'v', 's'],
            'props': self.street_life.props,
            'start': {'x': self.landmarks_by_id('roost')['parts'][2]['box'][2] - 3, 'ry': WALL_H,
                      'z': self.landmarks_by_id('roost')['parts'][2]['box'][3] - 3, 'yaw': -45.0,
                      'note': "the Runners' Roost terrace on the NW wall, facing the spire"},
            'routes': {'street': self.street_route},
            'graphs': {
                'moves': MOVES,
                'street': {'nodes': sg.node_list(), 'edges': sg.edge_list()},
                'roof': {'nodes': rg['nodes'], 'edges': rg['edges']},
            },
        }
        return out


def rise(pitch, d):
    if pitch == 'steep':
        return 2 * d if d <= 3 else 6 + (d - 3)
    return d


def door_xz(p):
    return p['door'] if p['door'] else ((p['box'][0] + p['box'][2]) / 2, (p['box'][1] + p['box'][3]) / 2)


def offset_line(pts, off):
    out = []
    for i, (x, z) in enumerate(pts):
        a = pts[max(0, i - 1)]
        b = pts[min(len(pts) - 1, i + 1)]
        dx, dz = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dz) or 1
        out.append((x + (-dz / L) * off, z + (dx / L) * off))
    return out


MOVES = ['walk', 'stairs', 'bridge', 'door', 'jump', 'drop', 'mantle', 'hang_drop', 'climb_down', 'climb_up', 'wall_run',
         # M4 (look bible §9.1), appended so saved indices keep their meaning
         'line', 'shimmy', 'ledge_up', 'wall_jump', 'climb', 'bounce', 'walkway']
SPRINT = 5.6
WALK = 4.3


class Graph:
    """The street graph: centre lines split where they cross, kept where the ground under them is walkable."""

    def __init__(self, plan, bridge_mask):
        self.plan = plan
        self.bridge_mask = bridge_mask
        self.nodes = []      # [x, z, ry, kind, ref]
        self.edges = {}      # (a, b) -> (move, tag)
        self.grid = {}

    def node(self, x, z, kind='junction', ref=None, snap=1.6):
        key = (int(math.floor(x / 4)), int(math.floor(z / 4)))
        if kind == 'junction':
            for dk in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                for n in self.grid.get((key[0] + dk[0], key[1] + dk[1]), ()):
                    nx, nz = self.nodes[n][0], self.nodes[n][1]
                    if self.nodes[n][3] == 'junction' and math.hypot(nx - x, nz - z) <= snap:
                        return n
        n = len(self.nodes)
        self.nodes.append([x, z, self.ry(x, z), kind, ref])
        self.grid.setdefault(key, []).append(n)
        return n

    def ry(self, x, z):
        i, j = int(math.floor(x)) + O, int(math.floor(z)) + O
        if 0 <= i < N_CELLS and 0 <= j < N_CELLS:
            return int(self.plan.lift[j, i])
        return 0

    def walkable(self, x, z):
        i, j = int(math.floor(x)) + O, int(math.floor(z)) + O
        if not (0 <= i < N_CELLS and 0 <= j < N_CELLS):
            return False
        k = int(self.plan.kind[j, i])
        return k in WALKABLE or (self.bridge_mask[j, i] and k in WATER) or k == LANDMARK and self.gateway(i, j)

    def gateway(self, i, j):
        return False

    def build(self, lines):
        # split every line where another crosses it
        segs = [(np.array(a, float), np.array(b, float), m, t) for a, b, m, t in lines]
        cuts = [[0.0, 1.0] for _ in segs]
        joins = []
        boxes = [(min(a[0], b[0]) - 1, min(a[1], b[1]) - 1, max(a[0], b[0]) + 1, max(a[1], b[1]) + 1) for a, b, _, _ in segs]
        for i in range(len(segs)):
            a, b = segs[i][0], segs[i][1]
            bi = boxes[i]
            for j in range(i + 1, len(segs)):
                bj = boxes[j]
                if bi[2] < bj[0] or bj[2] < bi[0] or bi[3] < bj[1] or bj[3] < bi[1]:
                    continue
                c, d = segs[j][0], segs[j][1]
                hit = intersect(a, b, c, d)
                if hit is not None:
                    cuts[i].append(hit[0])
                    cuts[j].append(hit[1])
                else:
                    # T junctions: an end that stops beside the other line (within 3.5 blocks) is joined to it
                    for e, owner, other in ((c, j, i), (d, j, i), (a, i, j), (b, i, j)):
                        oa, ob = segs[other][0], segs[other][1]
                        t, dist = project(e, oa, ob)
                        if dist <= 3.5 and 0.0 < t < 1.0:
                            cuts[other].append(t)
                            if dist > 0.05:
                                joins.append((e.copy(), oa + (ob - oa) * t))
        for e, q in joins:
            segs.append((e, q, 'walk', None))
            cuts.append([0.0, 1.0])
        for (a, b, move, tag), cs in zip(segs, cuts):
            cs = sorted(set(round(c, 4) for c in cs))
            # keep a run of pieces only where the ground is walkable
            pts = [a + (b - a) * c for c in cs]
            for p, q in zip(pts, pts[1:]):
                L = float(np.hypot(*(q - p)))
                if L < 0.5:
                    continue
                n = max(2, int(L))
                ok = sum(1 for k in range(n + 1) if self.walkable(*(p + (q - p) * (k / n))))
                if ok / (n + 1) < 0.85:
                    continue
                over = self.bridge_over(p, q, n)
                u = self.node(p[0], p[1])
                v = self.node(q[0], q[1])
                if u != v:
                    self.edges[(min(u, v), max(u, v))] = ('bridge', over) if over else (move, tag)

    def bridge_over(self, p, q, n):
        """The id of the bridge whose water this piece crosses, or None."""
        for k in range(n + 1):
            x, z = p + (q - p) * (k / n)
            i, j = int(math.floor(x)) + O, int(math.floor(z)) + O
            if not (0 <= i < N_CELLS and 0 <= j < N_CELLS) or int(self.plan.kind[j, i]) not in WATER:
                continue
            for b in self.plan.bridges:
                x0, z0, x1, z1 = b['box']
                if x0 <= x < x1 + 1 and z0 <= z < z1 + 1:
                    return b['id']
        return None

    def attach(self, sx, sz, ref, px, pz, reach=14):
        """Links a door (or landmark entrance) at (px, pz) to the nearest street edge from (sx, sz)."""
        best = None
        for (u, v), (move, tag) in self.edges.items():
            if move in ('bridge', 'door'):
                continue  # never hang a door on a bridge (the Bell Bridge falls), nor on another house's door
            a = np.array(self.nodes[u][:2], float)
            b = np.array(self.nodes[v][:2], float)
            t, dist = project(np.array([sx, sz]), a, b)
            if dist <= reach and (best is None or dist < best[0]):
                best = (dist, u, v, t, move, tag)
        if best is None and reach < 24:
            return self.attach(sx, sz, ref, px, pz, reach=24)
        if best is None:
            return None
        dist, u, v, t, move, tag = best
        a = np.array(self.nodes[u][:2], float)
        b = np.array(self.nodes[v][:2], float)
        q = a + (b - a) * t
        if t <= 0.05 or np.hypot(*(q - a)) < 1.5:
            m = u
        elif t >= 0.95 or np.hypot(*(q - b)) < 1.5:
            m = v
        else:
            m = len(self.nodes)
            self.nodes.append([float(q[0]), float(q[1]), self.ry(q[0], q[1]), 'junction', None])
            del self.edges[(u, v)]
            self.edges[(min(u, m), max(u, m))] = (move, tag)
            self.edges[(min(v, m), max(v, m))] = (move, tag)
        d = len(self.nodes)
        self.nodes.append([px, pz, self.ry(px, pz), ref[0], ref[1]])
        self.edges[(min(m, d), max(m, d))] = ('door', None)
        return d

    def rejoin(self):
        """A door left on a scrap of street that reaches nothing is linked to the nearest node that does."""
        adj = self.adjacency()
        sq = min(range(len(self.nodes)), key=lambda n: math.hypot(*self.nodes[n][:2]))
        seen = {sq}
        st = [sq]
        while st:
            u = st.pop()
            for v, *_ in adj[u]:
                if v not in seen:
                    seen.add(v)
                    st.append(v)
        main = [n for n in seen if self.nodes[n][3] == 'junction']
        pts = np.array([self.nodes[n][:2] for n in main], float)
        for n, nd in enumerate(self.nodes):
            if n in seen or nd[3] not in ('door', 'landmark'):
                continue
            d = np.hypot(pts[:, 0] - nd[0], pts[:, 1] - nd[1])
            k = int(np.argmin(d))
            if d[k] <= 20:
                self.edges[(min(n, main[k]), max(n, main[k]))] = ('door', None)

    def adjacency(self):
        adj = [[] for _ in self.nodes]
        for (u, v), (move, tag) in self.edges.items():
            a, b = self.nodes[u], self.nodes[v]
            L = math.hypot(a[0] - b[0], a[1] - b[1]) + abs(a[2] - b[2])
            adj[u].append((v, L / WALK, move, tag))
            adj[v].append((u, L / WALK, move, tag))
        return adj

    def node_list(self):
        return [[round(n[0], 1), n[2], round(n[1], 1), n[3], n[4] if n[4] is not None else ''] for n in self.nodes]

    def edge_list(self):
        out = []
        for (u, v), (move, tag) in sorted(self.edges.items()):
            a, b = self.nodes[u], self.nodes[v]
            L = math.hypot(a[0] - b[0], a[1] - b[1]) + abs(a[2] - b[2])
            out.append([u, v, MOVES.index(move), round(L / WALK, 2), tag or ''])
        return out


def intersect(a, b, c, d):
    r, s = b - a, d - c
    den = r[0] * s[1] - r[1] * s[0]
    if abs(den) < 1e-9:
        return None
    t = ((c[0] - a[0]) * s[1] - (c[1] - a[1]) * s[0]) / den
    u = ((c[0] - a[0]) * r[1] - (c[1] - a[1]) * r[0]) / den
    if 0.0 <= t <= 1.0 and 0.0 <= u <= 1.0:
        return (t, u)
    return None


def project(p, a, b):
    ab = b - a
    ll = float(ab @ ab)
    t = 0.0 if ll == 0 else float(np.clip((p - a) @ ab / ll, 0.0, 1.0))
    q = a + ab * t
    return t, float(np.hypot(*(p - q)))


def roof_graph(plan, sg):
    """One node per roof top; edges between neighbouring roofs labelled with the move they need (SPEC §16.3),
    and a way down from each roof to its door's street node."""
    nodes, edges = [], []
    plots = plan.plots
    index = {}
    for p in plots:
        x0, z0, x1, z1 = p['box']
        nodes.append([round((x0 + x1 + 1) / 2, 1), p['lift'] + p['top'], round((z0 + z1 + 1) / 2, 1), 'roof', p['id']])
        index[p['id']] = len(nodes) - 1
        for cx in range((x0 - 6) // 16, (x1 + 6) // 16 + 1):
            for cz in range((z0 - 6) // 16, (z1 + 6) // 16 + 1):
                plan.__dict__.setdefault('_rc', {}).setdefault((cx, cz), []).append(p)
    seen = set()
    for p in plots:
        x0, z0, x1, z1 = p['box']
        cands = set()
        for cx in range((x0 - 6) // 16, (x1 + 6) // 16 + 1):
            for cz in range((z0 - 6) // 16, (z1 + 6) // 16 + 1):
                for q in plan._rc.get((cx, cz), ()):
                    if q['id'] != p['id']:
                        cands.add(q['id'])
        for qid in cands:
            if (min(p['id'], qid), max(p['id'], qid)) in seen:
                continue
            seen.add((min(p['id'], qid), max(p['id'], qid)))
            q = plan.by_id[qid]
            gx = max(q['box'][0] - x1 - 1, x0 - q['box'][2] - 1)
            gz = max(q['box'][1] - z1 - 1, z0 - q['box'][3] - 1)
            if gx >= 0 and gz >= 0:
                continue  # diagonal neighbours only: no shared side
            gap = max(gx, gz)
            if gap > 4:
                continue
            for a, b in ((p, q), (q, p)):
                ea, eb = a['lift'] + a['eave'], b['lift'] + b['eave']
                dh = eb - ea
                if gap == 0:
                    move = 'walk' if abs(dh) <= 1 else ('mantle' if 0 < dh <= 4 else ('drop' if -8 <= dh < 0 else
                                                                                     ('hang_drop' if dh >= -11 else None)))
                else:
                    move = 'jump' if dh <= 1 and gap <= (3 if dh == 1 else 4) and dh >= -8 else None
                if move is None:
                    continue
                ia, ib = index[a['id']], index[b['id']]
                L = math.hypot(nodes[ia][0] - nodes[ib][0], nodes[ia][2] - nodes[ib][2])
                cost = L / SPRINT + {'walk': 0, 'jump': 0.4, 'drop': 0.3, 'mantle': 1.0, 'hang_drop': 1.2}[move]
                edges.append([ia, ib, MOVES.index(move), round(cost, 2), ''])
    # the crossings over the avenues: a walkway's roof is a road between two roofs; a hang line is hand over hand
    for cs in getattr(plan, 'crossing_sites', []):
        pa, pb = cs['plots']
        if pa is None or pb is None:
            continue
        ia, ib = index[pa], index[pb]
        L = math.hypot(nodes[ia][0] - nodes[ib][0], nodes[ia][2] - nodes[ib][2])
        if cs['kind'] == 'walkway':
            edges.append([ia, ib, MOVES.index('walkway'), round(L / SPRINT + 0.5, 2), ''])
        else:
            span = math.hypot(cs['a'][0] - cs['b'][0], cs['a'][2] - cs['b'][2])
            edges.append([ia, ib, MOVES.index('line'), round(L / SPRINT + 2.0 + span / 1.6, 2), ''])
    # down to the street: a drainpipe on the front of every house of 3+ storeys, a hang-drop from lower eaves
    links = 0
    for p in plots:
        sn = p.get('street')
        if sn is None:
            continue
        i = index[p['id']]
        eave = p['eave']
        if p['storeys'] >= 3:
            move, cost = 'climb_down', eave / 3.0 + 1.5
        elif eave <= 9:
            move, cost = 'hang_drop', 1.5
        else:
            continue
        L = math.hypot(nodes[i][0] - sg.nodes[sn][0], nodes[i][2] - sg.nodes[sn][1])
        edges.append([i, -1 - sn, MOVES.index(move), round(cost + L / SPRINT, 2), ''])
        links += 1
    return {'nodes': nodes, 'edges': edges, 'links': links}


def shortest(adj, src, dst, avoid=None):
    import heapq
    dist = {src: 0.0}
    prev = {}
    pq = [(0.0, src)]
    while pq:
        d, u = heapq.heappop(pq)
        if u == dst:
            break
        if d > dist.get(u, 1e18):
            continue
        for v, c, move, tag in adj[u]:
            if avoid and tag == avoid:
                continue
            nd = d + c
            if nd < dist.get(v, 1e18):
                dist[v] = nd
                prev[v] = u
                heapq.heappush(pq, (nd, v))
    if dst not in dist:
        return None, None
    path = [dst]
    while path[-1] != src:
        path.append(prev[path[-1]])
    return path[::-1], dist[dst]


def build():
    p = Plan()
    p.base()
    p.walls()
    p.civic()
    p.make_bridges()
    p.avenues()
    p.lattice('bellwatch', 27, 7, 0.84, (3, 4), 3, 101, diag=0.10, ox=5, oz=3)
    p.lattice('bellwatch', 27, 7, 0.30, 2, 4, 102, ox=18, oz=16)
    p.quay_lanes()
    p.lattice('works', 34, 2, 0.85, (5, 6), 2, 104, ox=20, oz=20)
    p.lattice('garden', 46, 9, 0.45, 4, 3, 105, ox=8, oz=12)
    p.lattice('terrace', 38, 0, 0.0, 4, 3, 106)
    p.lattice('wallring', 30, 3, 0.0, 3, 3, 107)
    p.rasterize()
    p.prune()
    p.relief()
    p.pack()
    p.merchant_towers()
    p.chain()
    p.greenery()
    p.uses()
    p.crossings()
    p.graphs()
    # the street-life layer (look bible §7, M4 B3a): stalls, carts, wells, benches, lamps at every junction, lines
    sys.path.insert(0, HERE)
    import props as street_props
    p.street_life = street_props.build(p, globals())
    # the opening's street route: the Roost to the square, never over the Bell Bridge (it falls)
    adj = p.street_graph.adjacency()
    sq = min(range(len(p.street_graph.nodes)), key=lambda n: math.hypot(*p.street_graph.nodes[n][:2]))
    path, secs = shortest(adj, p.start_street, sq, avoid='bell_bridge')
    p.street_route = [[round(p.street_graph.nodes[n][0], 1), round(p.street_graph.nodes[n][1], 1)] for n in path] if path else []
    p.route_secs = secs
    # every building on that route can be entered
    if path:
        pts = np.array([p.street_graph.nodes[n][:2] for n in path], float)
        for q in p.plots:
            if q['enter'] or not q['door']:
                continue
            d = np.min(np.hypot(pts[:, 0] - q['door'][0], pts[:, 1] - q['door'][1]))
            if d <= 7 and sum(1 for r in p.plots if r['use'] == 'route') < 30:
                q['use'], q['enter'] = 'route', True
    return p


def stats(p):
    from collections import Counter
    c = Counter(q['district'] for q in p.plots)
    sg = p.street_graph
    adj = sg.adjacency()
    # connectivity from the square
    sq = min(range(len(sg.nodes)), key=lambda n: math.hypot(*sg.nodes[n][:2]))
    seen = {sq}
    st = [sq]
    while st:
        u = st.pop()
        for v, *_ in adj[u]:
            if v not in seen:
                seen.add(v)
                st.append(v)
    doors = [n for n, nd in enumerate(sg.nodes) if nd[3] == 'door']
    lost = [n for n in doors if n not in seen]
    print(f"plots {len(p.plots)}  by district {dict(sorted(c.items()))}")
    print(f"enterable {sum(1 for q in p.plots if q['enter'])}  uses "
          f"{dict(Counter(q['use'] for q in p.plots if q['enter']))}  no door {sum(1 for q in p.plots if not q['door'])}")
    print(f"landmarks {len(p.landmarks)} ({sum(len(l['parts']) for l in p.landmarks)} parts)  trees {len(p.trees)}  lamps {len(p.lamps)}")
    print('street life', p.street_life.summary())
    print(f"streets {len(p.streets)}  street graph {len(sg.nodes)} nodes {len(sg.edges)} edges; "
          f"reachable from the square {len(seen)}; doors {len(doors)}, unreachable {len(lost)}")
    print(f"roof graph {len(p.roof_graph['nodes'])} nodes {len(p.roof_graph['edges'])} edges ({p.roof_graph['links']} to the street)")
    print(f"street route Roost -> square: {len(p.street_route)} nodes, {p.route_secs and round(p.route_secs)} s walking")
    big = max(max(q['box'][2] - q['box'][0], q['box'][3] - q['box'][1]) + 1 for q in p.plots)
    print(f"largest plot side {big}")
    # the height plan
    lifts = Counter(int(q['lift']) for q in p.plots)
    print(f"plot lifts {dict(sorted(lifts.items()))}  odd: {sum(v for k, v in lifts.items() if k % 2)}")
    print(f"steps (STAIR cells) {int(np.count_nonzero(p.kind == STAIR))}  ground lifts {dict(sorted(Counter(p.lift.ravel().tolist()).items()))}")
    walk = np.isin(p.kind, list(WALKABLE)) & (p.kind != LOWQUAY)
    broken = 0
    for dz, dx in ((0, 1), (1, 0)):
        nw = np.roll(np.roll(walk, -dz, axis=0), -dx, axis=1)
        nl = np.roll(np.roll(p.lift, -dz, axis=0), -dx, axis=1)
        nk = np.roll(np.roll(p.kind, -dz, axis=0), -dx, axis=1)
        broken += int(np.count_nonzero(walk & nw & (np.abs(nl - p.lift) >= 2) & ((p.kind == STAIR) | (nk == STAIR))))
    print(f"steps meeting ground two or more off (a flight's side, or a broken one): {broken}")
    cl = p.cliffs()
    print(f"cliffs on street-graph edges: {len(cl)} {cl[:12]}")
    print(f"street profile conflicts (a flight longer than its stretch): {len(p.relief_conflicts)} {p.relief_conflicts[:6]}")
    cs = p.chain_stats
    print(f"roof chaining: rows out of step {cs['row_out_of_step']}, plots with no neighbour within 3: {cs['alone']}, "
          f"no neighbour within a storey: {len(cs['no_neighbour_within_storey'])} {cs['no_neighbour_within_storey'][:20]}")
    print(f"storeys {dict(sorted(Counter(q['storeys'] for q in p.plots).items()))}  towers {[q['id'] for q in p.plots if q.get('tower')]}")
    kinds = Counter(c['kind'] + ('/' + c['anchor'] if c['kind'] == 'line' else '') for c in p.crossing_sites)
    print(f"crossings {len(p.crossing_sites)} {dict(kinds)}")
    doors_off = 0
    for q in p.plots:
        if not q['door']:
            continue
        ox, oz = Plan.OUT[q['front']]
        fx, fz = q['door'][0] + ox, q['door'][1] + oz
        if p.cell(fx, fz) in WALKABLE and abs(int(p.lift[fz + O, fx + O]) - q['lift']) > 0:
            doors_off += 1
    print(f"doors not at their street's level: {doors_off}")
    return lost


ROOF = {'market': (185, 112, 70), 'bellwatch': (78, 86, 104), 'quay': (126, 92, 62), 'terrace': (196, 190, 176),
        'works': (150, 64, 50), 'garden': (78, 140, 112), 'wallring': (120, 120, 124)}


def preview(p, path_top, path_graph):
    from PIL import Image, ImageDraw
    S = 2
    img = Image.new('RGB', (N_CELLS * S, N_CELLS * S), (88, 112, 64))
    px = img.load()
    col = {VOID: (88, 112, 64), FREE: (150, 140, 120), YARD: (140, 132, 116), GARDEN: (92, 140, 70), AVENUE: (214, 210, 198),
           STREET: (192, 188, 178), LANE: (170, 164, 152), ALLEY: (150, 144, 134), SQUARE: (226, 220, 204),
           PLAZA: (210, 204, 190), QUAY: (176, 170, 160), RIVER: (54, 92, 140), CANAL: (64, 104, 150), WALL: (110, 110, 116),
           LANDMARK: (200, 160, 90), STAIR: (230, 226, 210), WATERGATE: (70, 90, 120), TRAINING: (196, 178, 140),
           RACE: (64, 104, 150), RING: (184, 180, 170), LOWQUAY: (150, 146, 138)}
    for j in range(N_CELLS):
        for i in range(N_CELLS):
            k = int(p.kind[j, i])
            c = col.get(k, (255, 0, 255))
            if k == PLOT:
                q = p.plots[int(p.plot_id[j, i])]
                base = ROOF[q['district']]
                f = 0.78 + 0.07 * q['storeys'] + ((q['variant'] % 7) - 3) * 0.02
                c = tuple(max(0, min(255, int(v * f))) for v in base)
            if p.lift[j, i] and k not in (PLOT,):
                c = tuple(min(255, v + 12) for v in c)
            for a in range(S):
                for b in range(S):
                    px[i * S + a, j * S + b] = c
    d = ImageDraw.Draw(img)
    for x, z, kind, size in p.trees:
        r = 2 * S if kind != 'avenue' else S + 1
        d.ellipse([(x + O) * S - r, (z + O) * S - r, (x + O) * S + r, (z + O) * S + r],
                  fill=(236, 170, 205) if kind == 'cherry' else (52, 96, 44))
    for lm in p.landmarks:
        for part in lm['parts']:
            x0, z0, x1, z1 = part['box']
            fill = {'spire': (196, 150, 70), 'tower': (96, 96, 102), 'gate_tower': (90, 90, 96), 'roost_tower': (200, 140, 60),
                    'belltower': (180, 130, 60), 'bridge': (150, 140, 128), 'boom': (110, 80, 50), 'stall': (190, 60, 60)}.get(part['kind'], (210, 200, 180))
            d.rectangle([(x0 + O) * S, (z0 + O) * S, (x1 + O + 1) * S - 1, (z1 + O + 1) * S - 1], fill=fill, outline=(40, 40, 40))
    img.save(path_top)
    g = img.copy()
    d = ImageDraw.Draw(g)
    sg = p.street_graph
    for (u, v), (move, tag) in sg.edges.items():
        a, b = sg.nodes[u], sg.nodes[v]
        colr = (255, 255, 255) if move != 'door' else (255, 230, 90)
        if tag == 'bell_bridge':
            colr = (255, 60, 60)
        d.line([(a[0] + O) * S, (a[1] + O) * S, (b[0] + O) * S, (b[1] + O) * S], fill=colr, width=1)
    rg = p.roof_graph
    for a, b, m, c, _ in rg['edges']:
        if b < 0:
            continue
        na, nb = rg['nodes'][a], rg['nodes'][b]
        d.line([(na[0] + O) * S, (na[2] + O) * S, (nb[0] + O) * S, (nb[2] + O) * S], fill=(205, 140, 60), width=1)
    if p.street_route:
        d.line([((x + O) * S, (z + O) * S) for x, z in p.street_route], fill=(60, 220, 255), width=3)
    g.save(path_graph)


def preview_lift(p, path):
    """The height plan: ground by level (blue below the street, then pale to deep warm as it rises), steps in red,
    plots outlined by their lift, crossings in white (walkways) and cyan (line sites)."""
    from PIL import Image, ImageDraw
    S = 2
    img = Image.new('RGB', (N_CELLS * S, N_CELLS * S), (60, 70, 60))
    px = img.load()
    for j in range(N_CELLS):
        for i in range(N_CELLS):
            k = int(p.kind[j, i])
            lv = int(p.lift[j, i])
            if k == VOID:
                c = (60, 70, 60)
            elif k in WATER:
                c = (50, 80, 130)
            elif k == STAIR:
                c = (210, 60, 50)
            elif lv < 0:
                c = (120 + 25 * lv, 150 + 20 * lv, 200)
            else:
                f = min(1.0, lv / 12.0)
                c = (int(205 - 60 * f), int(200 - 120 * f), int(180 - 140 * f))
                if k == PLOT:
                    c = tuple(int(v * 0.8) for v in c)
            for a in range(S):
                for b in range(S):
                    px[i * S + a, j * S + b] = c
    d = ImageDraw.Draw(img)
    for c in p.crossing_sites:
        col = (255, 255, 255) if c['kind'] == 'walkway' else ((80, 230, 240) if c['anchor'] == 'facades' else (60, 140, 160))
        d.line([(c['a'][0] + O) * S, (c['a'][2] + O) * S, (c['b'][0] + O) * S, (c['b'][2] + O) * S], fill=col, width=3)
    img.save(path)


def main():
    p = build()
    lost = stats(p)
    data = p.to_json()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    text = json.dumps(data, separators=(',', ':'), sort_keys=False)
    with open(OUT, 'w') as f:
        f.write(text)
    sha = hashlib.sha256(text.encode('utf-8')).hexdigest()
    print(f"wrote {os.path.relpath(OUT, REPO)} ({len(text) // 1024} KB) sha256 {sha}")
    if 'pin' in sys.argv[1:]:
        tests = os.path.join(REPO, 'src/main/java/dev/awaken/city/CityTests.java')
        src = open(tests).read()
        src2 = re.sub(r'static final String PLAN_SHA256 = "[^"]*";', f'static final String PLAN_SHA256 = "{sha}";', src)
        open(tests, 'w').write(src2)
        print('pinned in CityTests.PLAN_SHA256')
    if 'preview' in sys.argv[1:]:
        outdir = os.path.join(REPO, 'build', 'city')
        os.makedirs(outdir, exist_ok=True)
        preview(p, os.path.join(outdir, 'plan-top.png'), os.path.join(outdir, 'plan-graph.png'))
        preview_lift(p, os.path.join(outdir, 'plan-lift.png'))
        print('previews in build/city/')
    if lost:
        print(f"WARNING: {len(lost)} doors cannot reach the square")
        sg = p.street_graph
        for n in lost[:60]:
            print('   lost door', sg.nodes[n][4], [round(v) for v in sg.nodes[n][:2]], p.by_id[sg.nodes[n][4]]['district'])


if __name__ == '__main__':
    main()
