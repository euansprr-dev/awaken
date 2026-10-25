#!/usr/bin/env python3
"""Carillon's citizens as data (docs/SPEC.md §16, M3 "Alive", grown to ~460 in M4 B3a): who the citizens are, where they live and
work, and what they do at each hour. Everything is derived from the plan (plan.json), deterministically.

  python3 -I tools/city/citizens.py            writes src/main/resources/data/awaken/city/citizens.json, prints stats
  python3 -I tools/city/citizens.py check      runs every check without writing (exit code 1 on any failure)
  python3 -I tools/city/citizens.py preview    also writes build/city/citizens-top.png

Coordinates are plan coordinates (blocks from the city's centre, x east, z south). Place ids, route ids, household ids,
group ids and citizen ids are indices into the arrays of the output; node indices are the street graph's own.
Nothing here reads Python's global random: every random stream is `random.Random(SEED ^ salt)`.

Where the brief's templates could not hold as written (the brief's own checks are what rule), the choices are:
  * a leading `0 home` that repeats the day's last `home` is merged into it (the day wraps), so the walk to work gets
    the whole night instead of the first 600 ticks; lamplighters keep a real first `home` after their out round;
  * walks that still overrun their gap push the later start back in 50s (and stay within a few hundred ticks);
  * wanderers (errands, play) are reached at their nearest place and left from any of them; loops are left from any
    stop; a round's start is the stop nearest where the citizen comes from;
  * lamp rounds are timed at a run (RUN_PACE 0.135 blocks a tick, the game's), not the 0.088 stroll: 78 lamps cannot be
    walked by 8 lamplighters at the stroll inside the dusk and dawn windows (the best split has tours of 180-300 blocks);
    the light round of a long tour starts a little before 11200 so that it ends before 13600;
  * errand routes: the 7 per-district ones, plus local routes of the nearest eligible places where a district's is too far;
  * junction stands for patrols and couriers are the nearest standing cells to the junction that keep clear of the
    streets' centre lines; patrol loops that cannot close are walked out and back (a stop repeats in the list).

M4 B3a (street life): the roster grows to 460 and gathers where the plan's props are.
  * places come from the props: the 34 stalls (a keeper place of the two back cells, a front place of the four cells before the
    counter), benches (cap 2, on the bench), wells, fountain rims, the bowl's steps, notice boards, the tavern tables' bench
    rows (and a second lane place at every tavern); every street-level prop cell is an obstacle for the other places and for
    the keep-right lanes (edgeLane);
  * `market` routes (six sixths of the square: four stall fronts and the benches, rims, steps and board beside them) and
    `stroll` routes (the benches, wells and boards of a neighbourhood) are the walks of the crowd; a `dock` route (the porters'
    noon break at the crates); the porters' drops are one route of six stall fronts per dock (four docks, two to a crane);
  * residents: market-goers (home -> the square from ~1500 -> a sit at noon on a bench, rim or step, else lingering at the
    stalls -> errands -> a tavern 12000-15500), neighbours (a bench or well near home mornings and at dusk, the market at
    midday when it is within reach, an evening at the tavern on alternate days), elders (long sits, a short walk, home early);
    homes follow the bench or the market they go to and spread over the districts;
  * tavern hands stand at the table place 11500-17000; the lamps are cut into ceil(lamps / 10) tours balanced by time;
  * Kestrel (the Roost's master: a post at its street door by day, the Roost itself her home: household plot -1) and Bram (the
    Association's officer: inside by day with a spell at the forecourt board, a house near the Terrace) are named runner and clerk.
"""
import base64
import hashlib
import heapq
import json
import math
import os
import random
import sys
import zlib
from collections import Counter, defaultdict

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
PLAN = os.path.join(REPO, 'src/main/resources/data/awaken/city/plan.json')
OUT = os.path.join(REPO, 'src/main/resources/data/awaken/city/citizens.json')
BUILD = os.path.join(REPO, 'build', 'city')

SEED = 0xC171_2E45
N = 512
O = 256

# ---- ground kinds (the plan's raster) ---------------------------------------------------------------------------
VOID, FREE, YARD, GARDEN, PLOT, AVENUE, STREET, LANE, ALLEY, SQUARE, PLAZA, QUAY, RIVER, CANAL, WALL, LANDMARK, \
    STAIR, WATERGATE, TRAINING, RACE, RING, LOWQUAY = range(22)
WALKABLE = {AVENUE, STREET, LANE, ALLEY, SQUARE, PLAZA, QUAY, STAIR, RING, TRAINING, LOWQUAY}
WATER = {RIVER, CANAL, WATERGATE, RACE}
# (M4 Walls) the wall walk joined to the graph: its middle line, its row, the stair edges' move and climb, the Roost's tower place
WALL_R = 240
WALK_LINE_D = -0.5
WALK_RY = 39
MOVE_HIDDEN = 9
TOWER_CLIMB = 45.0
ROOST_TOWER = 14
SIDE_N, SIDE_E, SIDE_S, SIDE_W = 0, 1, 2, 3


def dominant(dx, dz):
    """The grid side a direction leans to most (0 N, 1 E, 2 S, 3 W), as CityWall.dominant."""
    if abs(dx) >= abs(dz):
        return SIDE_E if dx > 0 else SIDE_W
    return SIDE_S if dz > 0 else SIDE_N


WALK = 4.3            # blocks per second the plan's edge seconds were made with
STROLL = 0.088        # blocks per tick a citizen strolls at (speed 1.0)
RUN_PACE = 0.135      # blocks per tick lamplighters (and runners on their courier round) go at (speed 1.0)
DAY = 24000

JOBS = ['lamplighter', 'bellringer', 'porter', 'stallkeeper', 'baker', 'smith', 'watch', 'crier', 'boatman', 'washer',
        'mason', 'runner', 'shopkeeper', 'tavernkeeper', 'clerk', 'archivist', 'healer', 'miller', 'child', 'resident']
J = {n: i for i, n in enumerate(JOBS)}
ACTS = ['home', 'inside', 'stand', 'round', 'carry', 'wander']
A = {n: i for i, n in enumerate(ACTS)}
PLACE_KINDS = ['door', 'stand', 'lamp', 'stall_keeper', 'stall_front', 'bell', 'anvil', 'wash', 'line', 'pickup', 'post',
               'crier', 'sit', 'play', 'mill', 'landmark', 'tavern_out', 'shop_door', 'mooring', 'net']
PK = {n: i for i, n in enumerate(PLACE_KINDS)}
PLACE_FIELDS = ['kind', 'node', 'x', 'ry', 'z', 'yaw', 'cap', 'ref', 'district', 'approach', 'spots']
CITIZEN_FIELDS = ['id', 'given', 'family', 'job', 'household', 'home', 'work', 'group', 'tavern', 'build', 'age', 'scale',
                  'speed', 'look', 'named']
# M4 B3a: ~450 records (SPEC §16.5 asks 400-480). The two named records Kestrel (a runner) and Bram (a clerk) are on top of
# these counts, so the table's runner and clerk counts below include them. Stallkeepers: one opener per stall (34) and 14
# helpers on the square's stalls; tavernkeepers: 14 keepers and 14 hands.
JOB_COUNTS = {'lamplighter': 20, 'bellringer': 7, 'porter': 26, 'stallkeeper': 48, 'baker': 10, 'smith': 8, 'watch': 32,
              'crier': 7, 'boatman': 10, 'washer': 14, 'mason': 12, 'runner': 11, 'shopkeeper': 30, 'tavernkeeper': 28,
              'clerk': 11, 'archivist': 2, 'healer': 4, 'miller': 4, 'child': 36, 'resident': 140}
TOTAL = sum(JOB_COUNTS.values())
GOODS = ['bread', 'fish', 'produce', 'cloth', 'pottery', 'candles', 'flowers', 'spice', 'tools', 'cheese']
GROUP_KINDS = ['stall', 'smithy', 'bakery', 'watch', 'lamps', 'bells', 'porters', 'washers', 'masons', 'criers',
               'boatmen', 'runners', 'shop', 'tavern', 'association', 'mill', 'play', 'elders']

NAMES_A = ('Abel Alder Ambrose Anselm Aren Arno Bastian Bertil Brannoc Cato Cillian Clem Corin Dorran Edric Emrys '
           'Fenwick Finch Gideon Hale Hollis Ivo Jago Jory Kasimir Kit Lucan Mattis Nico Olek Orrin Pell Perrin Quill '
           'Rafe Roan Rook Silas Tam Tobin Ulric Wendel Wim Yorick Ansel Benedek Caspar Davin Eben Florian Garrick '
           'Hamish Ignas Joss Konrad Lorcan Marek Nils Osric Piet Radek Stellan Teague Vasco').split()
NAMES_B = ('Ada Agnes Alis Brisa Cass Dagny Della Edda Elin Elsabet Esme Fenna Greta Hanne Ida Ines Isolde Juna Leni '
           'Lotte Maren Marta Mavis Nell Noor Odile Ottilie Petra Rhosyn Romy Saoirse Selka Signe Tamsin Thea Una Vesna '
           'Wren Ysolde Zinnia Aino Bryony Calla Delphine Eira Freya Gisela Hester Ilse Jessamy Kerensa Liesl Morwen '
           'Nadia Ondine Philippa Rosalind Sabine Tilde Undine Veda').split()
FAMILIES = ('Ashgrove Bellamy Brassfield Callow Candlewick Chimewright Cobble Corder Dunmore Fairweather Fletcher Greave '
            'Hallam Hearn Holloway Inkwell Jessop Kettle Lantry Larkin Marrow Merriman Noll Oakes Quarry Reedman Rusk '
            'Saltmarsh Tolland Tollman Underbell Vane Wexley Whitlock Wick Brightwater Coldharbour Dimmock Eversley Flint '
            'Garrow Harrow Ivesley Lockwood Mallory Netherby Ormsby Penhallow Ridley Stannard Thorne Upwood '
            'Varley Weatherell Yarrow Ashby Bracken Cresswell Dunstan Elbury Fallow Grisedale Hartwell').split()
# The brief's list holds 64 surnames but about 91 households each need their own: these 30 extend it in the same vein.
FAMILIES_MORE = ('Ashworth Bannock Barley Beckett Birchall Blackwood Bowyer Bramley Brindle Broadbent Buckler Burdock Cartwright '
                 'Chalmers Cheeseman Clover Cobbold Copperfield Cordwell Cranmer Crowhurst Daventry Dobbin Drummond Eastwick '
                 'Elmore Faulkner Fenner Fernley Fitch Foxley Gantry Garland Gatehouse Glover Goodwin Grindle Hadley Halloran '
                 'Hargrave Hartnoll Haywood Hazelwood Hobday Hollis Hopcroft Hurlock Ingram Isherwood Jarrow Jennet Kersey '
                 'Kilbride Knoll Lambourne Latchford Lavender Leadbetter Linnet Lockyer Lowther Lumley Maddox Malthouse '
                 'Manning Marchbank Mayhew Melrose Milford Moorcroft Mossop Nailor Nesbitt Newsome Northcott Oakley Oldfield '
                 'Orrell Osgood Overton Packard Palliser Parslow Peverell Pilcher Plumtree Pollard Prowse Quillfeather Radley '
                 'Ramsden Rawlin Redfern Renshaw Rowntree Rudge Sandford Scarlett Sedgwick Shelley Skerry Slocombe Spindle '
                 'Stainer Stirling Stockdale Swallow Tanner Thackray Thistlewood Tidewell Tolliver Trevor Turnbull Tunstall '
                 'Ulverston Vickery Wadsworth Wallace Warbeck Wardle Wetherby Whitcombe Whitmore Willowby Winslow Wolsey '
                 'Woodcock Wrenfield Yelland Zouch Amberley Brackenbury Candover Dawlish Fenwick Greenhow Haddock Kimberley').split()
FAMILIES_EXTRA = ('Appleyard Barrow Cheswick Dewhurst Gladwin Hawkridge Ironside Kingsley Langdon Marlow Nettleton '
                  'Pemberton Quennell Selwyn Tenby Underhill Wyndham Yeoman Alcott Burnell Calloway Danvers Edgecombe '
                  'Farrow Galloway Heron Ludlow Mercer Orchard Pickering').split()

ERRAND_DWELL = 300
# (M3 allowed 14 for a spot the game must path to; two cells of the bowl, a fountain rim and a stall's back row, are 14.09 from
# the nearest junction, so the limit is 14.2)
PATH_REACH = 14.2
SQ_R = 40.0                    # the bowl: places this near the Bell Stone belong to the market routes
LAMP_MIN, LAMP_MAX = 5, 16      # lamps in a tour (the tours are balanced by the time they take, not their count)


def rng(salt):
    return random.Random(SEED ^ salt)


def yaw_of(dx, dz):
    """Minecraft yaw for facing the vector (dx, dz): 0 = south (+z), 90 = west (-x), 180 = north, -90 = east."""
    if dx == 0 and dz == 0:
        return 0
    y = math.degrees(math.atan2(-dx, dz))
    y = round(y)
    return 180 if y == -180 else y


def cc(v):
    """The block centre of the cell containing v."""
    return math.floor(v) + 0.5


def prop_cells(pr):
    """The ground cells a plan prop stands on: its whole box, or for a hang line only its pole cells (the end cell at x0/z0
    when its poles contain 'a', the end at x1/z1 when they contain 'b')."""
    if pr['kind'] == 'line':
        out = []
        if 'a' in pr['facing']:
            out.append((pr['x0'], pr['z0']))
        if 'b' in pr['facing']:
            out.append((pr['x1'], pr['z1']))
        return out
    return [(x, z) for x in range(pr['x0'], pr['x1'] + 1) for z in range(pr['z0'], pr['z1'] + 1)]


def jdump(obj):
    return json.dumps(obj, separators=(',', ':'), ensure_ascii=False)


# ======================================================================================================================
#  the city: raster, street graph, distances
# ======================================================================================================================
class City:
    def __init__(self):
        raw = open(PLAN, 'rb').read()
        self.plan_sha = hashlib.sha256(raw).hexdigest()
        P = self.P = json.loads(raw)
        dec = lambda s: np.frombuffer(zlib.decompress(base64.b64decode(s)), np.uint8).reshape(N, N)
        R = P['raster']
        self.kind, self.dist = dec(R['kind']), dec(R['district'])
        # (plan schema 2: the lift is signed, Choir Square's bowl stands below the street)
        self.lift = dec(R['lift']).view(np.int8).astype(np.int16) if P.get('schemaVersion', 1) >= 2 else dec(R['lift'])
        self.districts = {d['key']: d for d in P['districts']}
        self.dkey = {d['id']: d['key'] for d in P['districts']}
        # plots
        self.plots = {}
        self.plot_at = np.full((N, N), -1, np.int32)
        f = P['plotFields']
        for row in P['plots']:
            p = dict(zip(f, row))
            self.plots[p['id']] = p
            self.plot_at[p['z0'] + O:p['z1'] + O + 1, p['x0'] + O:p['x1'] + O + 1] = p['id']
        self.door_cells = {(p['doorX'], p['doorZ']): p['id'] for p in self.plots.values() if p['doorX'] is not None}
        # bridges and landmark footprints
        self.bridge_mask = np.zeros((N, N), bool)
        for b in P['bridges']:
            x0, z0, x1, z1 = b['box']
            self.bridge_mask[z0 + O:z1 + O + 1, x0 + O:x1 + O + 1] = True
        self.lm = {l['id']: l for l in P['landmarks']}
        self.block = np.zeros((N, N), bool)    # landmark footprints (painted over; never a standing place)
        for l in P['landmarks']:
            for part in l['parts']:
                if part['kind'] in ('stall', 'gate_passage', 'bridge'):
                    continue
                x0, z0, x1, z1 = part['box']
                self.block[z0 + O:z1 + O + 1, x0 + O:x1 + O + 1] = True
        # M4 B3a: the street-life props (plan.json "props"). Every street-level prop cell is an obstacle: no place's spot
        # stands in one (except the spots a prop defines itself), and the keep-right lanes keep clear of its centre.
        # A hang line has only its pole cells on the ground.
        self.props = [dict(zip(P['propFields'], row)) for row in P.get('props', [])]
        self.prop_mask = np.zeros((N, N), bool)
        self.stall_mask = np.zeros((N, N), bool)
        for pr in self.props:
            for (x, z) in prop_cells(pr):
                self.prop_mask[z + O, x + O] = True
                if pr['kind'] == 'stall':
                    self.stall_mask[z + O, x + O] = True
        # the lower quay is 4 below the street (ladders only): never a place to stand or walk to
        walk = np.isin(self.kind, list(WALKABLE - {LOWQUAY}))
        self.bridge_walk = self.bridge_mask & np.isin(self.kind, list(WATER | {LOWQUAY}))
        self.walk = walk | self.bridge_walk                     # a body can stand or cross here
        self.ground = walk & ~self.block & (self.plot_at < 0) & (self.kind != STAIR) & ~self.bridge_mask & ~self.prop_mask
        self.water = np.isin(self.kind, list(WATER)) & ~self.bridge_walk
        # street graph
        g = P['graphs']['street']
        self.nodes = [list(nd) for nd in g['nodes']]
        self.edges = [list(e) for e in g['edges']]
        self.n_plan, self.e_plan = len(self.nodes), len(self.edges)
        self.wall_nodes = set()
        self.wall_walk()
        n = len(self.nodes)
        self.adj = [[] for _ in range(n)]
        for a, b, m, sec, tag in self.edges:
            blocks = sec * WALK
            self.adj[a].append((b, blocks, m))
            self.adj[b].append((a, blocks, m))
        self.leaf = [nd[3] in ('door', 'landmark') for nd in self.nodes]
        self.main = self._main_component()
        self.in_main = np.zeros(n, bool)
        self.in_main[self.main] = True
        self.door_node = {}
        self.landmark_node = {}
        for i, nd in enumerate(self.nodes):
            if not self.in_main[i]:
                continue
            if nd[3] == 'door':
                self.door_node[int(nd[4])] = i
            elif nd[3] == 'landmark':
                self.landmark_node[nd[4]] = i
        self._rows = {}
        self._field = None
        self._edge_grid = None
        self._run = None
        self._street_grid = None
        self.wall_link()

    # -- the wall walk (M4 Walls) ------------------------------------------------------------------------------------------
    def wall_walk(self):
        """The wall walk joined to the street graph (citizens.json "wallWalk"; Java appends the same nodes and edges): a
        chain of nodes along the walk's middle line (depth -0.5, row 39) round the ring, broken at the Runners' Roost; for
        every tower a node at its door (on open ground, the door CityTowers paints) and a hidden edge from it up the tower's
        stair to the walk (move 9: a body never walks it, it goes in at the door and comes out on the walk). Nodes are kind
        'wall': no field seeds, no junction pools, never a place's nearest node."""
        ap = WALL_R * math.cos(math.radians(7.5))
        r_line = ap - WALK_LINE_D

        def side_of(x, z):
            best, bk = -1e9, 0
            for k in range(24):
                phi = math.radians(15 * (k + 1))
                v = x * math.cos(phi) + z * math.sin(phi)
                if v > best:
                    best, bk = v, k
            return bk

        def on_line(k, a):
            phi = math.radians(15 * (k + 1))
            return (r_line * math.cos(phi) - a * math.sin(phi), r_line * math.sin(phi) + a * math.cos(phi))

        pts = []      # (angle, x, z, tower or None)
        towers = []
        for lm in self.P['landmarks']:
            for part in lm['parts']:
                if part['kind'] in ('tower', 'gate_tower'):
                    towers.append(part)
        stairless = {id(p) for lm in self.P['landmarks'] if lm['id'].startswith('water_gate') for p in lm['parts']}
        half = WALL_R * math.sin(math.radians(7.5))
        for k in range(24):
            for t in range(-3, 4):
                a = t * half / 3.5
                x, z = on_line(k, a)
                pts.append((math.atan2(z, x), x, z, None))
        for j in range(24):
            if j == ROOST_TOWER:
                continue
            ang = math.radians(7.5 + 15 * j)
            rv = r_line / math.cos(math.radians(7.5))
            pts.append((ang, rv * math.cos(ang), rv * math.sin(ang), None))
        self.tower_doors = []
        for part in towers:
            x0, z0, x1, z1 = part['box']
            cx, cz = (x0 + x1 + 1) / 2.0, (z0 + z1 + 1) / 2.0
            k = side_of(cx, cz)
            phi = math.radians(15 * (k + 1))
            a = -cx * math.sin(phi) + cz * math.cos(phi)
            x, z = on_line(k, a)
            pts.append((math.atan2(z, x), x, z, part))
        roost = math.radians(7.5 + 15 * ROOST_TOWER)
        # angles measured from just past the Roost, so the chain runs round and stops short of it
        key = lambda p: (p[0] - roost) % (2 * math.pi)  # noqa: E731
        pts.sort(key=key)
        chain = []
        for p in pts:
            if key(p) < math.radians(5) or key(p) > 2 * math.pi - math.radians(5):
                continue      # (the Roost's own stretch: its tower is the guild's, not the Watch's)
            if chain and math.hypot(p[1] - chain[-1][1], p[2] - chain[-1][2]) < 2.5:
                if p[3] is not None and chain[-1][3] is None:
                    chain[-1] = p
                continue
            chain.append(p)
        base = len(self.nodes)
        for i, p in enumerate(chain):
            self.nodes.append([round(p[1], 1), WALK_RY, round(p[2], 1), 'wall', ''])
            self.wall_nodes.add(base + i)
            if i:
                a, b = base + i - 1, base + i
                d = math.hypot(self.nodes[a][0] - self.nodes[b][0], self.nodes[a][2] - self.nodes[b][2])
                self.edges.append([a, b, 0, round(d / WALK, 2), 'wall_walk'])
        for i, p in enumerate(chain):
            if p[3] is None or id(p[3]) in stairless:
                continue      # (the water gates' towers have no stair: the walk reaches them)
            part = p[3]
            door = self.tower_door(part)
            dn = len(self.nodes)
            self.nodes.append([door[0], self.lift_at(door[0], door[1]), door[1], 'wall', ''])
            self.wall_nodes.add(dn)
            w = base + i
            d = math.hypot(self.nodes[w][0] - door[0], self.nodes[w][2] - door[1])
            self.edges.append([dn, w, MOVE_HIDDEN, round((d + TOWER_CLIMB) / WALK, 2), 'tower_stair'])
            self.tower_doors.append(dn)

    def tower_door(self, part):
        """The cell just outside a tower's door (CityTowers.doorSide, ported exactly): the town side first, then the flanks,
        on the axis or up to five off it, where open street-level ground runs three out from the box."""
        x0, z0, x1, z1 = part['box']
        cx, cz = (x0 + x1 + 1) / 2.0, (z0 + z1 + 1) / 2.0
        ncx, ncz = math.floor(cx), math.floor(cz)
        out = dominant(cx, cz)
        to_city = (out + 2) & 3
        for sd in (to_city, (to_city + 1) & 3, (to_city + 3) & 3):
            for off in (0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5):
                ok = 0
                for o in range(1, 4):
                    x = x1 + o if sd == SIDE_E else x0 - o if sd == SIDE_W else ncx + off
                    z = z1 + o if sd == SIDE_S else z0 - o if sd == SIDE_N else ncz + off
                    k = self.kind_at(x + 0.5, z + 0.5)
                    if k in WALKABLE and k != LOWQUAY and k not in WATER and self.lift_at(x + 0.5, z + 0.5) <= 1:
                        ok += 1
                if ok == 3:
                    x = x1 + 1 if sd == SIDE_E else x0 - 1 if sd == SIDE_W else ncx + off
                    z = z1 + 1 if sd == SIDE_S else z0 - 1 if sd == SIDE_N else ncz + off
                    return (x + 0.5, z + 0.5)
        raise ValueError(f'no door for tower {part["box"]}')

    def wall_link(self):
        """Each tower's door node walks to its nearest street junction (called once the street field can be read)."""
        for dn in self.tower_doors:
            x, z = self.nodes[dn][0], self.nodes[dn][2]
            j, _ = self.nearest_node(x, z)
            d = math.hypot(self.nodes[j][0] - x, self.nodes[j][2] - z)
            self.edges.append([dn, j, 0, round(max(d, 0.1) / WALK, 2), 'tower_door'])
            sec = self.edges[-1][3] * WALK
            self.adj[dn].append((j, sec, 0))
            self.adj[j].append((dn, sec, 0))
        self.main = self._main_component()
        self.in_main = np.zeros(len(self.nodes), bool)
        self.in_main[self.main] = True

    def wall_extra(self):
        """The nodes and edges past the plan's, for citizens.json (indices absolute)."""
        return {'nodes': [[nd[0], nd[1], nd[2]] for nd in self.nodes[self.n_plan:]],
                'edges': [[e[0], e[1], e[2]] for e in self.edges[self.e_plan:]]}

    # -- raster helpers --------------------------------------------------------------------------------------------
    @staticmethod
    def cell(x, z):
        return int(math.floor(x)) + O, int(math.floor(z)) + O

    def kind_at(self, x, z):
        i, j = self.cell(x, z)
        return int(self.kind[j, i]) if 0 <= i < N and 0 <= j < N else VOID

    def lift_at(self, x, z):
        i, j = self.cell(x, z)
        return int(self.lift[j, i]) if 0 <= i < N and 0 <= j < N else 0

    def district_at(self, x, z):
        i, j = self.cell(x, z)
        return int(self.dist[j, i]) if 0 <= i < N and 0 <= j < N else 0

    def walkable(self, x, z):
        i, j = self.cell(x, z)
        return 0 <= i < N and 0 <= j < N and bool(self.walk[j, i])

    def is_ground(self, x, z):
        i, j = self.cell(x, z)
        return 0 <= i < N and 0 <= j < N and bool(self.ground[j, i]) and (i - O, j - O) not in self.door_cells \
            and (i - O, j - O) not in self.landmark_cells

    @property
    def landmark_cells(self):
        if not hasattr(self, '_lmc'):
            self._lmc = {(math.floor(self.nodes[n][0]), math.floor(self.nodes[n][2])) for n in self.landmark_node.values()}
        return self._lmc

    # -- the street graph ------------------------------------------------------------------------------------------
    def _main_component(self):
        """The street graph's main component: the junctions joined by edges (a junction may sit on a door edge), plus every
        door or landmark node whose edges all lead to junctions of it. A door hanging off another door, or off a junction on
        an island, is not in it (a body walks the graph through junctions only; door and landmark nodes are its ends)."""
        nodes = self.nodes
        leaf = lambda i: nodes[i][3] in ('door', 'landmark')
        start = next(v for v, _, _ in self.adj[self.landmark_node_raw('choir')] if not leaf(v))
        seen = {start}
        stack = [start]
        while stack:
            u = stack.pop()
            for v, _, m in self.adj[u]:
                if not leaf(v) and v not in seen:
                    seen.add(v)
                    stack.append(v)
        for i in range(len(nodes)):
            if leaf(i) and self.adj[i] and all(not leaf(v) and v in seen for v, _, _ in self.adj[i]):
                seen.add(i)
        return sorted(seen)

    def landmark_node_raw(self, lid):
        for i, nd in enumerate(self.nodes):
            if nd[3] == 'landmark' and nd[4] == lid:
                return i
        raise KeyError(lid)

    def row(self, src):
        """Shortest walking distances in blocks from graph node src to every node (inf where unreachable)."""
        r = self._rows.get(src)
        if r is None:
            n = len(self.nodes)
            d = [math.inf] * n
            d[src] = 0.0
            heap = [(0.0, src)]
            adj = self.adj
            leaf = self.leaf
            wall = self.wall_nodes
            root = src in wall
            while heap:
                du, u = heapq.heappop(heap)
                if du > d[u] or (u != src and leaf[u]):
                    continue    # a door or landmark node is where a walk ends, never a way through
                for v, w, _ in adj[u]:
                    # (M4 Walls) a walk changes between the street and the wall walk at most once: nobody crosses the
                    # city over the wall, the Watch climbs to its round and back (StreetNet.bake keeps the same rule)
                    if (u in wall) != root and (v in wall) == root:
                        continue
                    nd = du + w
                    if nd < d[v]:
                        d[v] = nd
                        heapq.heappush(heap, (nd, v))
            r = self._rows[src] = d
        return r

    # -- cell level walking: nearest junction node of every walkable cell --------------------------------------------
    def field(self):
        """(distance, node) per cell over walkable cells, multi-source from the main component's junction nodes."""
        if self._field is not None:
            return self._field
        dist = np.full((N, N), np.inf)
        who = np.full((N, N), -1, np.int32)
        heap = []
        for n in self.main:
            nd = self.nodes[n]
            if nd[3] != 'junction':
                continue
            i, j = self.cell(nd[0], nd[2])
            if not (0 <= i < N and 0 <= j < N):
                continue
            seeds = [(i, j)] if self.walk[j, i] else [(i + a, j + b) for a in (-1, 0, 1) for b in (-1, 0, 1)
                                                      if 0 <= i + a < N and 0 <= j + b < N and self.walk[j + b, i + a]]
            for (si, sj) in seeds:
                d0 = math.hypot(nd[0] - (si + 0.5 - O), nd[2] - (sj + 0.5 - O))
                if d0 < dist[sj, si]:
                    dist[sj, si] = d0
                    who[sj, si] = n
                    heapq.heappush(heap, (d0, sj, si))
        walk = self.walk
        R2 = math.sqrt(2.0)
        while heap:
            d0, j, i = heapq.heappop(heap)
            if d0 > dist[j, i]:
                continue
            nid = who[j, i]
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
                ni, nj = i + a, j + b
                if not (0 <= ni < N and 0 <= nj < N) or not walk[nj, ni]:
                    continue
                if a and b and not (walk[j, ni] and walk[nj, i]):
                    continue
                nd_ = d0 + (R2 if a and b else 1.0)
                if nd_ < dist[nj, ni]:
                    dist[nj, ni] = nd_
                    who[nj, ni] = nid
                    heapq.heappush(heap, (nd_, nj, ni))
        self._field = (dist, who)
        return self._field

    def nearest_node(self, x, z):
        dist, who = self.field()
        i, j = self.cell(x, z)
        if who[j, i] < 0:
            # (a prop's own cell may be yard or a well's footing, outside the walk field: use the nearest cell inside it)
            best = None
            for b in range(-8, 9):
                for a in range(-8, 9):
                    ni, nj = i + a, j + b
                    if 0 <= ni < N and 0 <= nj < N and who[nj, ni] >= 0:
                        d = math.hypot(a, b)
                        if best is None or d < best[0]:
                            best = (d, nj, ni)
            if best is None:
                raise ValueError(f'no node reaches ({x}, {z})')
            _, j, i = best
        return int(who[j, i]), float(dist[j, i])

    # -- geometry queries ------------------------------------------------------------------------------------------
    def runs(self):
        """Contiguous walkable run lengths through every cell along x and along z (for street widths)."""
        if self._run is None:
            w = self.walk & (self.plot_at < 0)
            rx = np.zeros((N, N), np.int16)
            rz = np.zeros((N, N), np.int16)
            for j in range(N):
                i = 0
                row = w[j]
                while i < N:
                    if row[i]:
                        k = i
                        while k < N and row[k]:
                            k += 1
                        rx[j, i:k] = k - i
                        i = k
                    else:
                        i += 1
            for i in range(N):
                j = 0
                col = w[:, i]
                while j < N:
                    if col[j]:
                        k = j
                        while k < N and col[k]:
                            k += 1
                        rz[j:k, i] = k - j
                        j = k
                    else:
                        j += 1
            self._run = (rx, rz)
        return self._run

    def narrow(self, x, z):
        """True on streets narrower than 6 (people standing there must stay off the walking lane): the plan's own street
        widths where the point lies on a street, else the open width across the cell."""
        i, j = self.cell(x, z)
        k = int(self.kind[j, i])
        if k in (SQUARE, PLAZA, TRAINING):
            return False
        if k in (LANE, ALLEY):
            return True
        if self._street_grid is None:
            g = defaultdict(list)
            for st in self.P['streets']:
                ax, az = st['a']
                bx, bz = st['b']
                for gx in range(int(math.floor(min(ax, bx) / 8)) - 1, int(math.floor(max(ax, bx) / 8)) + 2):
                    for gz in range(int(math.floor(min(az, bz) / 8)) - 1, int(math.floor(max(az, bz) / 8)) + 2):
                        g[(gx, gz)].append((ax, az, bx, bz, st['width']))
            self._street_grid = g
        for ax, az, bx, bz, w in self._street_grid.get((int(math.floor(x / 8)), int(math.floor(z / 8))), ()):
            if w < 6 and self.seg_dist((ax, az, bx, bz), x, z) <= w / 2.0 + 0.5:
                return True
        rx, rz = self.runs()
        return min(int(rx[j, i]), int(rz[j, i])) < 6

    def edge_grid(self):
        if self._edge_grid is None:
            g = defaultdict(list)
            for a, b, m, sec, tag in self.edges:
                if m == 3 or m == MOVE_HIDDEN or a in self.wall_nodes and b in self.wall_nodes or not (self.in_main[a] and self.in_main[b]):
                    continue
                ax, az = self.nodes[a][0], self.nodes[a][2]
                bx, bz = self.nodes[b][0], self.nodes[b][2]
                for gx in range(int(math.floor(min(ax, bx) / 8)) - 1, int(math.floor(max(ax, bx) / 8)) + 2):
                    for gz in range(int(math.floor(min(az, bz) / 8)) - 1, int(math.floor(max(az, bz) / 8)) + 2):
                        g[(gx, gz)].append((ax, az, bx, bz))
            self._edge_grid = g
        return self._edge_grid

    @staticmethod
    def seg_dist(seg, x, z):
        ax, az, bx, bz = seg
        dx, dz = bx - ax, bz - az
        ll = dx * dx + dz * dz
        t = 0.0 if ll == 0 else max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / ll))
        return math.hypot(x - ax - t * dx, z - az - t * dz)

    def edge_near(self, x, z):
        """(distance, segment) to the nearest street-graph walking edge's centre line (door edges excluded)."""
        best = (99.0, None)
        for seg in self.edge_grid().get((int(math.floor(x / 8)), int(math.floor(z / 8))), ()):
            d = self.seg_dist(seg, x, z)
            if d < best[0]:
                best = (d, seg)
        return best

    def edge_dist(self, x, z):
        return self.edge_near(x, z)[0]

    def lane_ok(self, x, z):
        """The keep-off-the-walking-lane rule: on narrow streets stay >= 1.0 from every edge's centre line."""
        return not self.narrow(x, z) or self.edge_dist(x, z) >= 0.999

    def approach(self, node, spot):
        """0 if the straight line from the node to the spot crosses only walkable cells (lift +-1 per cell), else 1."""
        nx, nz = self.nodes[node][0], self.nodes[node][2]
        sx, sz = spot[0], spot[1]
        L = math.hypot(sx - nx, sz - nz)
        steps = max(2, int(L * 4))
        last = self.cell(sx, sz)
        prev = None
        for k in range(steps + 1):
            x = nx + (sx - nx) * k / steps
            z = nz + (sz - nz) * k / steps
            c = self.cell(x, z)
            if not (0 <= c[0] < N and 0 <= c[1] < N):
                return 1
            if c == prev:
                continue
            if not self.walk[c[1], c[0]] and not (k == 0):
                return 1
            if self.prop_mask[c[1], c[0]] and not (k == 0) and c != last:
                return 1     # a prop stands in the way (the spot's own cell excepted): the game must path round it
            if prev is not None and abs(int(self.lift[c[1], c[0]]) - int(self.lift[prev[1], prev[0]])) > 1:
                return 1
            prev = c
        return 0


# ======================================================================================================================
#  places
# ======================================================================================================================
FRONT = {'N': (0, -1), 'S': (0, 1), 'W': (-1, 0), 'E': (1, 0)}
LANDMARK_YAW = {'roost': 180, 'choir': 180, 'association': 180, 'market_hall': 90}


class Builder:
    def __init__(self, city):
        self.c = city
        self.places = []          # dicts: kind (str), node, x, ry, z, yaw, cap, ref, district, approach, spots
        self.used = set()         # cells already claimed by some place's spot
        self.door_place = {}      # plot id -> place id
        self.landmark_place = {}  # landmark id -> place id
        self.routes = []          # {id, kind, places}

    # -- the place store ----------------------------------------------------------------------------------------------
    def add(self, kind, spots, ref=-1, node=None, claim=True):
        c = self.c
        spots = [(float(x), float(z), int(y)) for x, z, y in spots]
        cx = sum(s[0] for s in spots) / len(spots)
        cz = sum(s[1] for s in spots) / len(spots)
        mid = min(spots, key=lambda s: (math.hypot(s[0] - cx, s[1] - cz), spots.index(s)))
        if node is None:
            node, _ = c.nearest_node(mid[0], mid[1])
            if not self.reach_ok(node, spots):
                # the nearest node is too far from a spot for the game to path the last blocks: the nearest that is not
                for alt in sorted((n for n in c.main if c.nodes[n][3] == 'junction'),
                                  key=lambda n: (math.hypot(c.nodes[n][0] - mid[0], c.nodes[n][2] - mid[1]), n))[:60]:
                    if self.reach_ok(alt, spots):
                        node = alt
                        break
        approach = max(c.approach(node, s) for s in spots)
        pid = len(self.places)
        self.places.append({'kind': kind, 'node': node, 'x': mid[0], 'z': mid[1], 'ry': c.lift_at(mid[0], mid[1]),
                            'yaw': mid[2], 'cap': len(spots), 'ref': ref, 'district': c.district_at(mid[0], mid[1]),
                            'approach': approach, 'spots': [list(s) for s in spots]})
        if claim:
            for s in spots:
                self.used.add(c.cell(s[0], s[1]))
        return pid

    def reach_ok(self, node, spots):
        """A straight way from the node to every spot may be up to 36 blocks long; one the game must path, PATH_REACH."""
        nd = self.c.nodes[node]
        far = max(math.hypot(s[0] - nd[0], s[1] - nd[2]) for s in spots)
        return far <= (36.0 if all(self.c.approach(node, s) == 0 for s in spots) else PATH_REACH)

    def free(self, x, z):
        return self.c.cell(x, z) not in self.used

    def spot_ok(self, x, z):
        return self.c.is_ground(x, z) and self.free(x, z) and self.c.lane_ok(x, z)

    def prop_cell_ok(self, x, z):
        """A cell of a prop itself (a bench's, a table's bench row) that a body may stand on: walkable, outside plots, water,
        doors and landmark footprints, clear of a narrow street's walking lane. (Props on yard ground are not standing
        places: the walk raster excludes yards, like everywhere else in this file.)"""
        c = self.c
        i, j = c.cell(x, z)
        return bool(c.walk[j, i]) and c.plot_at[j, i] < 0 and not c.water[j, i] and not c.block[j, i] \
            and int(c.kind[j, i]) != LOWQUAY and (i - O, j - O) not in c.door_cells and (i - O, j - O) not in c.landmark_cells \
            and c.lane_ok(x, z)

    def step_ok(self, x, z):
        """A free step (stair cell) to stand on, clear of the walking lane."""
        c = self.c
        i, j = c.cell(x, z)
        return int(c.kind[j, i]) == STAIR and bool(c.walk[j, i]) and not c.prop_mask[j, i] and c.plot_at[j, i] < 0 \
            and (i - O, j - O) not in c.door_cells and self.free(x, z) and c.lane_ok(x, z)

    def cells_around(self, cx, cz, rmax, pred=None):
        """Standing cells (centres) within rmax of (cx, cz), nearest first: [(d, x, z)]."""
        out = []
        r = int(math.ceil(rmax)) + 1
        for j in range(int(math.floor(cz)) - r, int(math.floor(cz)) + r + 1):
            for i in range(int(math.floor(cx)) - r, int(math.floor(cx)) + r + 1):
                x, z = i + 0.5, j + 0.5
                d = math.hypot(x - cx, z - cz)
                if d <= rmax and self.spot_ok(x, z) and (pred is None or pred(x, z)):
                    out.append((d, x, z))
        out.sort()
        return out

    def pick_apart(self, cands, n, sep=0.9, taken=()):
        """The first n candidates (x, z) that are at least `sep` from each other and from `taken`."""
        got = []
        for x, z in cands:
            if all(math.hypot(x - a, z - b) >= sep for a, b in list(got) + list(taken)):
                got.append((x, z))
                if len(got) == n:
                    break
        return got

    # -- doors --------------------------------------------------------------------------------------------------------
    def door(self, pid):
        if pid not in self.door_place:
            p = self.c.plots[pid]
            ox, oz = FRONT[p['front']]
            sp = (p['doorX'] + 0.5, p['doorZ'] + 0.5, yaw_of(-ox, -oz))
            self.door_place[pid] = self.add('door', [sp], ref=pid, node=self.c.door_node[pid], claim=False)
        return self.door_place[pid]

    def landmark(self, lid):
        if lid not in self.landmark_place:
            n = self.c.landmark_node[lid]
            nd = self.c.nodes[n]
            sp = (cc(nd[0]), cc(nd[2]), LANDMARK_YAW[lid])
            self.landmark_place[lid] = self.add('landmark', [sp], ref=lid, node=n, claim=False)
        return self.landmark_place[lid]

    def front_cell(self, pid):
        """The street cell straight out from a plot's door, its outward vector and the tangent along the wall."""
        p = self.c.plots[pid]
        ox, oz = FRONT[p['front']]
        tx, tz = (1, 0) if oz else (0, 1)
        return (p['doorX'] + ox, p['doorZ'] + oz), (ox, oz), (tx, tz)

    def door_seg(self, pid):
        """The door's own graph edge (door cell -> street point) as a segment, or None."""
        dn = self.c.door_node[pid]
        for v, _, m in self.c.adj[dn]:
            if m == 3:
                a, b = self.c.nodes[dn], self.c.nodes[v]
                return (a[0], a[2], b[0], b[2])
        return None

    def beside_door(self, pid, count, face_out=True, rmax=9.0):
        """`count` ground cells in the street outside a plot's door, beside it (never on the way in), nearest the wall
        first. Yaw faces the door (face_out False) or out into the street."""
        (fx, fz), (ox, oz), (tx, tz) = self.front_cell(pid)
        seg = self.door_seg(pid)
        cx, cz = fx + 0.5, fz + 0.5

        def clear(x, z):
            return seg is None or self.c.seg_dist(seg, x, z) >= 0.999

        cells = self.cells_around(cx, cz, rmax, clear)
        keyed = sorted(((d + 0.9 * abs((x - cx) * ox + (z - cz) * oz), x, z) for d, x, z in cells))
        got = self.pick_apart([(x, z) for _, x, z in keyed], count)
        px, pz = self.c.plots[pid]['doorX'] + 0.5, self.c.plots[pid]['doorZ'] + 0.5
        return [(x, z, yaw_of(ox, oz) if face_out else yaw_of(px - x, pz - z)) for x, z in got]

    # -- lamps ----------------------------------------------------------------------------------------------------------
    def build_lamps(self):
        c = self.c
        lamps = c.P['lamps']
        posts = {(x, z) for x, z in lamps}
        out = []
        for k, (x, z) in enumerate(lamps):
            px, pz = x + 0.5, z + 0.5
            sq = c.kind_at(px, pz) in (SQUARE, PLAZA)
            seg = c.edge_near(px, pz)[1]
            best = None
            for step in (False, True):
                # (a lamp hard by a flight of steps, on a lane too narrow to stand beside: the lamplighter takes the step)
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
                    sx, sz = px + dx, pz + dz
                    if (math.floor(sx), math.floor(sz)) in posts or not (self.step_ok(sx, sz) if step else self.spot_ok(sx, sz)):
                        continue
                    if sq or seg is None:
                        score = math.hypot(sx, sz)
                    else:
                        score = c.seg_dist(seg, sx, sz)
                    key = (abs(dx) + abs(dz) > 1, round(score, 3), -dx, -dz)
                    if best is None or key < best[0]:
                        best = (key, sx, sz, dx, dz)
                if best:
                    break
            assert best, f'no spot for lamp {k}'
            _, sx, sz, dx, dz = best
            out.append(self.add('lamp', [(sx, sz, yaw_of(-dx, -dz))], ref=k))
        self.lamp_places = out

    # -- stalls --------------------------------------------------------------------------------------------------------
    def build_stalls(self):
        """The plan's stall props (M4 B3a: 4 along the counter x 2 deep, `facing` its customers; 24 on Choir Square's -2
        ring, the rest on the district plazas), in plan order. The front row (depth 0, the `facing` side) has posts at 0 and 3
        and the counter at 1 and 2; the back row has posts at 0 and 3 and the keeper's two cells at 1 and 2. The keeper
        place is those two cells (yaw toward the counter), the front place the four cells just outside the front row."""
        self.stalls = []
        self.square_stalls = []
        for part, pr in enumerate(self.c.props):
            if pr['kind'] != 'stall':
                continue
            x0, z0, x1, z1 = pr['x0'], pr['z0'], pr['x1'], pr['z1']
            fx, fz = FRONT[pr['facing']]
            if fx:    # counter along z; the front row is the column on the facing side
                fcol, bcol = (x1, x0) if fx > 0 else (x0, x1)
                back = [(bcol, z0 + k) for k in range(4)]
                front = [(fcol, z0 + k) for k in range(4)]
            else:
                frow, brow = (z1, z0) if fz > 0 else (z0, z1)
                back = [(x0 + k, brow) for k in range(4)]
                front = [(x0 + k, frow) for k in range(4)]
            k = len(self.stalls)
            keeper = self.add('stall_keeper', [(x + .5, z + .5, yaw_of(fx, fz)) for x, z in back[1:3]], ref=k)
            fr = self.add('stall_front', [(x + fx + .5, z + fz + .5, yaw_of(-fx, -fz)) for x, z in front], ref=k)
            self.stalls.append({'part': part, 'box': [x0, z0, x1, z1], 'facing': pr['facing'], 'colour': pr['v'],
                                'keeper': keeper, 'front': fr, 'good': pr['s']})
            if pr['ry'] < 0:
                self.square_stalls.append(k)     # on the bowl's -2 ring
        assert len(self.stalls) >= 30 and len(self.square_stalls) >= 20, (len(self.stalls), len(self.square_stalls))

    # -- bells ---------------------------------------------------------------------------------------------------------
    def build_bells(self):
        c = self.c
        self.bells = []
        order = [('bell_bellwatch', 'belltower'), ('bell_quay', 'belltower'), ('bell_terrace', 'belltower'),
                 ('bell_garden', 'belltower'), ('bell_works', 'belltower'), ('choir', 'spire')]
        keyd = {'bell_bellwatch': 2, 'bell_quay': 3, 'bell_terrace': 4, 'bell_garden': 6, 'bell_works': 5, 'choir': 1}
        for lid, pk in order:
            box = next(p['box'] for p in c.lm[lid]['parts'] if p['kind'] == pk)
            x0, z0, x1, z1 = box
            cap = 2 if lid == 'choir' else 1
            mx, mz = (x0 + x1 + 1) / 2, (z0 + z1 + 1) / 2
            door = c.landmark_cells if lid == 'choir' else set()

            def boxdist(x, z):
                dx = max(x0 - x, 0, x - (x1 + 1))
                dz = max(z0 - z, 0, z - (z1 + 1))
                return math.hypot(dx, dz)
            cands = []
            for j in range(z0 - 4 + O, z1 + 5 + O):
                for i in range(x0 - 4 + O, x1 + 5 + O):
                    x, z = i - O + 0.5, j - O + 0.5
                    if not self.spot_ok(x, z) or (i - O, j - O) in door:
                        continue
                    if lid == 'choir' and abs(x - 0.5) < 1.6:
                        continue
                    ap = c.approach(c.nearest_node(x, z)[0], (x, z))
                    cands.append((round(boxdist(x, z), 2), ap, abs(x - mx) + 0.001 * (z1 - z) if lid != 'choir' else abs(x - 0.5), x, z))
            cands.sort()
            first = cands[0][0]
            near = [(a[3], a[4]) for a in cands if a[0] <= first + 0.01 and a[1] == 0] or [(a[3], a[4]) for a in cands]
            got = self.pick_apart(near, cap, sep=0.9)
            spots = []
            for x, z in got:
                nx = min(max(x, x0), x1 + 1)
                nz = min(max(z, z0), z1 + 1)
                spots.append((x, z, yaw_of(nx - x, nz - z) if (nx - x or nz - z) else yaw_of(mx - x, mz - z)))
            pid = self.add('bell', spots, ref=lid)
            self.bells.append({'landmark': lid, 'place': pid, 'box': [x0, z0, x1, z1], 'district': keyd[lid]})

    # -- taverns, shops, bakeries, smithies ------------------------------------------------------------------------------
    def build_taverns_shops(self):
        c = self.c
        self.taverns, self.shops = [], []
        for pid in sorted(c.plots):
            p = c.plots[pid]
            if p['use'] == 'tavern' and pid in c.door_node:
                d = self.door(pid)
                table = next((pr for pr in c.props if pr['kind'] == 'tavern_table' and pr['s'] == str(pid)), None)
                if table is None:
                    spots = self.beside_door(pid, 5, face_out=False)
                    assert len(spots) == 5, f'tavern {pid} front has {len(spots)} cells'
                else:
                    # M4 B3a: the table's four bench cells (the rows either side of the table, yaw toward it) and the two
                    # cells beside the door: the evening spills into the lane
                    ux, uz = FRONT[table['facing']]
                    cells = prop_cells(table)
                    key = lambda q: q[0] * ux + q[1] * uz   # noqa: E731
                    lo, hi = min(key(q) for q in cells), max(key(q) for q in cells)
                    spots = self.beside_door(pid, 2, face_out=False)
                    assert len(spots) == 2, f'tavern {pid} has no cells beside the door'
                    for q in cells:
                        if not self.prop_cell_ok(q[0] + .5, q[1] + .5):
                            continue
                        if key(q) == lo:
                            spots.append((q[0] + .5, q[1] + .5, yaw_of(ux, uz)))
                        elif key(q) == hi:
                            spots.append((q[0] + .5, q[1] + .5, yaw_of(-ux, -uz)))
                    assert len(spots) >= 2, f'tavern {pid} table {table}'
                out = self.add('tavern_out', spots, ref=pid)
                self.taverns.append({'plot': pid, 'door': d, 'out': out, 'district': c.district_at(p['doorX'] + .5, p['doorZ'] + .5),
                                     'table': table is not None})
        for pid in sorted(c.plots):
            p = c.plots[pid]
            if p['use'] == 'shop' and pid in c.door_node:
                d = self.door(pid)
                spots = self.beside_door(pid, 1, face_out=True)
                assert spots, f'shop {pid} has no spot beside the door'
                sd = self.add('shop_door', spots, ref=pid)
                self.shops.append({'plot': pid, 'door': d, 'stand': sd})
        assert len(self.taverns) == 14 and len(self.shops) == 30, (len(self.taverns), len(self.shops))

    # -- the props' places: benches, wells, fountains, notice boards, the square's steps, the taverns' overflow -----------------
    def spread(self, cands, n, cx, cz, first=None):
        """n of the (x, z) candidates spread round (cx, cz): the first nearest `first` (or the first listed), then each next
        the one farthest from those taken (ties by position, so the choice is stable)."""
        cands = sorted(set(cands))
        if len(cands) <= n:
            return list(cands)
        if first is None:
            got = [cands[0]]
        else:
            got = [min(cands, key=lambda q: (math.hypot(q[0] - first[0], q[1] - first[1]), q))]
        while len(got) < n:
            got.append(max((q for q in cands if q not in got),
                           key=lambda q: (min(math.hypot(q[0] - g[0], q[1] - g[1]) for g in got), -q[0], -q[1])))
        return got

    def build_civic(self):
        c = self.c
        self.bench_places, self.well_places, self.fountain_places, self.notice_places, self.step_places = [], [], [], [], []
        for k, pr in enumerate(c.props):
            kind = pr['kind']
            x0, z0, x1, z1 = pr['x0'], pr['z0'], pr['x1'], pr['z1']
            if kind == 'bench':
                yaw = yaw_of(*FRONT[pr['facing']])
                cells = [(x + .5, z + .5, yaw) for x, z in prop_cells(pr) if self.prop_cell_ok(x + .5, z + .5)]
                if cells:     # (a bench on yard ground is scenery only)
                    self.bench_places.append(self.add('sit', cells))
            elif kind == 'well':
                cx, cz = (x0 + x1 + 1) / 2.0, (z0 + z1 + 1) / 2.0
                ring = [(x + .5, z + .5) for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2)
                        if not (x0 <= x <= x1 and z0 <= z <= z1) and self.spot_ok(x + .5, z + .5)]
                near = [q for q in ring if not (math.floor(q[0]) in (x0 - 1, x1 + 1) and math.floor(q[1]) in (z0 - 1, z1 + 1))] or ring   # not the corners
                nd = c.nodes[c.nearest_node(cx, cz)[0]]
                got = self.spread(near, 3, cx, cz, first=(nd[0], nd[2]))
                if got:       # (a well in a yard has only the lane cells round it; one in the corner of a yard none)
                    self.well_places.append(self.add('stand', [(x, z, yaw_of(cx - x, cz - z)) for x, z in got]))
            elif kind == 'fountain':
                cx, cz = (x0 + x1 + 1) / 2.0, (z0 + z1 + 1) / 2.0
                ring = [(x + .5, z + .5) for x in range(x0 - 1, x1 + 2) for z in range(z0 - 1, z1 + 2)
                        if not (x0 <= x <= x1 and z0 <= z <= z1) and self.spot_ok(x + .5, z + .5)]
                clear = [q for q in ring if c.edge_dist(*q) >= 1.2] or ring        # off the aisles
                got = self.spread(clear, 4, cx, cz)
                assert len(got) >= 2, f'fountain {k} has {len(got)} cells round it'
                self.fountain_places.append(self.add('sit', [(x, z, yaw_of(cx - x, cz - z)) for x, z in got]))
            elif kind == 'notice':
                fx, fz = FRONT[pr['facing']]
                tx, tz = (1, 0) if fz else (0, 1)
                bx, bz = x0 + .5, z0 + .5
                fcell = (x0 + fx + .5, z0 + fz + .5)
                spots = []
                if self.spot_ok(*fcell):
                    spots.append(fcell)
                    for off in (1, -1, 2, -2):
                        q = (fcell[0] + tx * off, fcell[1] + tz * off)
                        if self.spot_ok(*q) and c.kind_at(*q) == c.kind_at(*fcell):
                            spots.append(q)
                            break
                else:     # (the front is a landmark's footprint: the nearest free cells instead)
                    got = self.pick_apart([(x, z) for _, x, z in self.cells_around(bx, bz, 4.0)], 2)
                    spots = [(x, z) for x, z in got]
                assert spots, f'notice board {k}: no free cell near it'
                self.notice_places.append(self.add('stand', [(x, z, yaw_of(bx - x, bz - z)) for x, z in spots]))

    def build_steps(self):
        """The bowl's ring steps in the south half (the stair cells between the -2 ring and the floor, and between the -1 and -2
        rings): four sit places of 3 side by side, facing the Bell Stone, so a dozen sit in the bowl at noon."""
        c = self.c
        step_cells = {}
        for lift in (-1, -2):
            cl_ = []
            for j in range(O + 6, O + 36):
                for i in range(O - 34, O + 35):
                    x, z = i - O + .5, j - O + .5
                    if int(c.kind[j, i]) == STAIR and int(c.lift[j, i]) == lift and c.walk[j, i] and not c.prop_mask[j, i] \
                            and self.free(x, z) and c.edge_dist(x, z) >= 1.0 and 12 <= math.hypot(x, z) <= 28:
                        cl_.append((x, z))
            step_cells[lift] = cl_
        sites = [(-1, 35), (-1, -35), (-2, 38), (-2, -38)]     # (the step's lift, bearing off due south)
        for lift, bearing in sites:
            tx, tz = math.sin(math.radians(bearing)), math.cos(math.radians(bearing))
            cl_ = sorted(step_cells[lift], key=lambda q: (math.hypot(q[0] / (math.hypot(*q)) - tx, q[1] / (math.hypot(*q)) - tz), q))
            run = [cl_[0]]
            for q in cl_[1:]:
                if len(run) == 3:
                    break
                if all(math.hypot(q[0] - a, q[1] - b) >= 0.9 for a, b in run) and min(math.hypot(q[0] - a, q[1] - b) for a, b in run) <= 1.5:
                    run.append(q)
            assert len(run) == 3, f'step site {lift}/{bearing} has {len(run)} cells'
            self.step_places.append(self.add('sit', [(x, z, yaw_of(-x, -z)) for x, z in run]))

    def build_tavern_overflow(self):
        """More cells in the lane outside every tavern (the same plot, kind tavern_out): the evening crowd's second place."""
        for t in self.taverns:
            got = self.beside_door(t['plot'], 6, face_out=False, rmax=10.0)
            t['more'] = self.add('tavern_out', got, ref=t['plot']) if len(got) >= 3 else -1

    def house_candidates(self):
        c = self.c
        return [pid for pid in sorted(c.plots) if c.plots[pid]['use'] == 'house' and pid in c.door_node]

    def door_front_kind(self, pid):
        (fx, fz), _, _ = self.front_cell(pid)
        return self.c.kind_at(fx + 0.5, fz + 0.5)

    def build_bakeries(self):
        c = self.c
        stall_fronts = [self.places[s['front']] for s in self.stalls]
        want = ['market', 'bellwatch', 'quay', 'garden', 'works']
        self.bakeries = []
        self.reserved = set()
        for dk in want:
            best = None
            for pid in self.house_candidates():
                p = c.plots[pid]
                if p['district'] != dk or pid in self.reserved:
                    continue
                fk = self.door_front_kind(pid)
                if fk not in (STREET, LANE):
                    continue
                spots = self.beside_door(pid, 1, face_out=True)
                if not spots:
                    continue
                dn = c.door_node[pid]
                row = c.row(dn)
                near = sorted(row[self.places[s['front']]['node']] for s in self.stalls)[:2]
                score = near[1] + 0.3 * near[0] + (0 if p['storeys'] >= 3 else 25) + (0 if fk == STREET else 6)
                if best is None or (score, pid) < best:
                    best = (score, pid)
            assert best, f'no bakery plot in {dk}'
            pid = best[1]
            self.reserved.add(pid)
            d = self.door(pid)
            front = self.add('stand', self.beside_door(pid, 1, face_out=True), ref=pid)
            self.bakeries.append({'plot': pid, 'door': d, 'front': front, 'district': dk})
        # each bakery's drops: its two nearest stall fronts
        for b in self.bakeries:
            dn = self.places[b['door']]['node']
            row = c.row(dn)
            order = sorted(range(len(self.stalls)), key=lambda k: (row[self.places[self.stalls[k]['front']]['node']], k))
            b['drops'] = [self.stalls[k]['front'] for k in order[:2]]

    def smithy_spots(self, pid):
        """(block cell, anvil spot) for a plot, or None: block beside the door against the front wall, anvil in front."""
        (fx, fz), (ox, oz), (tx, tz) = self.front_cell(pid)
        for k in (1, -1, 2, -2):
            bx, bz = fx + tx * k, fz + tz * k
            ax, az = bx + ox, bz + oz
            if self.c.is_ground(bx + .5, bz + .5) and self.free(bx + .5, bz + .5) and self.spot_ok(ax + .5, az + .5):
                return (bx, bz), (ax + .5, az + .5, yaw_of(-ox, -oz))
        return None

    def build_smithies(self):
        c = self.c
        mb = next(b for b in c.P['bridges'] if b['id'] == 'mill_bridge')
        mbx, mbz = (mb['box'][0] + mb['box'][2] + 1) / 2, (mb['box'][1] + mb['box'][3] + 1) / 2
        anchors = [('works', (mbx, mbz)), ('works', (-52, 118)), ('works', (60, 150)), ('quay', (-90, 60))]
        self.smithies = []
        chosen = []
        for dk, (ax, az) in anchors:
            best = None
            for pid in self.house_candidates():
                p = c.plots[pid]
                if p['district'] != dk or pid in self.reserved or self.door_front_kind(pid) not in (STREET, LANE):
                    continue
                d = (p['doorX'] + .5 - ax, p['doorZ'] + .5 - az)
                dd = math.hypot(*d)
                if any(math.hypot(p['doorX'] - q[0], p['doorZ'] - q[1]) < 30 for q in chosen):
                    continue
                sp = self.smithy_spots(pid)
                if not sp:
                    continue
                if best is None or (dd, pid) < best[:2]:
                    best = (dd, pid, sp)
            assert best, f'no smithy plot near {ax},{az}'
            _, pid, (block, anvil_spot) = best
            p = c.plots[pid]
            chosen.append((p['doorX'], p['doorZ']))
            self.reserved.add(pid)
            d = self.door(pid)
            anvil = self.add('anvil', [anvil_spot], ref=pid)
            self.used.add((block[0] + O, block[1] + O))
            self.smithies.append({'plot': pid, 'door': d, 'anvil': anvil, 'block': [block[0], c.lift_at(block[0] + .5, block[1] + .5), block[1]]})

    # -- washplaces, laundry lines, the quay -------------------------------------------------------------------------------
    def water_side(self, x, z):
        """(dx, dz) of a 4-neighbour that is open water (not under a bridge), or None."""
        c = self.c
        i, j = c.cell(x, z)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ii, jj = i + dx, j + dz
            if 0 <= ii < N and 0 <= jj < N and c.water[jj, ii] and int(c.kind[jj, ii]) in (CANAL, RACE, RIVER):
                return dx, dz
        return None

    def build_washplaces(self):
        c = self.c
        wb = next(b for b in c.P['bridges'] if b['id'] == 'canal_bridge_south')
        centres = [((wb['box'][0] + wb['box'][2] + 1) / 2, (wb['box'][1] + wb['box'][3] + 1) / 2 + 3.0, None),
                   (-44.0, 121.0, (0, -1))]   # the race: its south bank only (the north bank is cut off by the mills)
        self.washplaces = []
        for k, (cx, cz, side) in enumerate(centres):
            cands = []
            for d, x, z in self.cells_around(cx, cz, 16, None):
                w = self.water_side(x, z)
                if w and (side is None or w == side):
                    cands.append((d, x, z, w))
            spots = []
            first_node = None
            for d, x, z, w in cands:
                if all(math.hypot(x - a, z - b) >= 2.0 for a, b, _ in spots):
                    node, _ = c.nearest_node(x, z)
                    if first_node is None:
                        first_node = node
                    elif c.row(first_node)[node] > 45:
                        continue   # a bank cell the street graph reaches only by a long way round
                    spots.append((x, z, yaw_of(*w)))
                if len(spots) == 6:
                    break
            assert len(spots) >= 4, f'washplace {k} found {len(spots)} cells'
            places = [self.add('wash', [sp], ref=k) for sp in spots]
            # the tub: a free walkable cell next to as many of them as possible
            best = None
            for i in range(int(min(s[0] for s in spots)) - 3, int(max(s[0] for s in spots)) + 4):
                for j in range(int(min(s[1] for s in spots)) - 3, int(max(s[1] for s in spots)) + 4):
                    x, z = i + 0.5, j + 0.5
                    if not (c.is_ground(x, z) and self.free(x, z)):
                        continue
                    near = sum(1 for s in spots if max(abs(s[0] - x), abs(s[1] - z)) <= 1.01)
                    if near and (best is None or (-near, math.hypot(x - cx, z - cz)) < best[0]):
                        best = ((-near, math.hypot(x - cx, z - cz)), i, j)
            assert best, 'no tub cell'
            self.used.add((best[1] + O, best[2] + O))
            self.washplaces.append({'places': places, 'tub': [best[1], c.lift_at(best[1] + .5, best[2] + .5), best[2]],
                                    'centre': (cx, cz)})
            if k == 1:
                assert len(places) >= 5, 'race wash places'

    def build_lines(self):
        c = self.c
        cands = []
        for j in range(N):
            for i in range(N):
                if c.kind[j, i] not in (LANE, ALLEY, STREET):
                    continue
                # a run along x starting here, plot cells at both ends
                for axis in (0, 1):
                    di, dj = (1, 0) if axis == 0 else (0, 1)
                    pi, pj = i - di, j - dj
                    if not (0 <= pi < N and 0 <= pj < N) or c.plot_at[pj, pi] < 0:
                        continue
                    n = 0
                    while n <= 8:
                        ii, jj = i + di * n, j + dj * n
                        if not (0 <= ii < N and 0 <= jj < N) or c.kind[jj, ii] not in (LANE, ALLEY, STREET) or not c.ground[jj, ii]:
                            break
                        n += 1
                    ei, ej = i + di * n, j + dj * n
                    if 3 <= n <= 7 and 0 <= ei < N and 0 <= ej < N and c.plot_at[ej, ei] >= 0:
                        a = (i - O + 0.5, j - O + 0.5)
                        b = (i + di * (n - 1) - O + 0.5, j + dj * (n - 1) - O + 0.5)
                        if self.spot_ok(*a) and self.spot_ok(*b) and (a[0], a[1], b[0], b[1]) not in cands:
                            cands.append((a[0], a[1], b[0], b[1], n))
        self.lines = []
        chosen = []
        for k, wp in enumerate(self.washplaces):
            cx, cz = wp['centre']
            scored = []
            for a0, a1, b0, b1, n in cands:
                mx, mz = (a0 + b0) / 2, (a1 + b1) / 2
                if min(math.hypot(mx - self.places[p]['x'], mz - self.places[p]['z']) for p in wp['places']) > 40:
                    continue
                node, d0 = c.nearest_node(mx, mz)
                best = min(c.row(self.places[p]['node'])[node] + self.cost(p) + d0 for p in wp['places'])
                if best <= 110:
                    scored.append((best, a0, a1, b0, b1, n))
            scored.sort()
            got = 0
            for walk, a0, a1, b0, b1, n in scored:
                mx, mz = (a0 + b0) / 2, (a1 + b1) / 2
                if any(math.hypot(mx - q[0], mz - q[1]) < 8 for q in chosen):
                    continue
                if any((ci, cj) in self.used for ci in range(int(a0) + O - 2, int(a0) + O + 3) for cj in range(int(a1) + O - 2, int(a1) + O + 3)) \
                        or any((ci, cj) in self.used for ci in range(int(b0) + O - 2, int(b0) + O + 3) for cj in range(int(b1) + O - 2, int(b1) + O + 3)):
                    continue
                chosen.append((mx, mz))
                pid = self.add('line', [(a0, a1, yaw_of(b0 - a0, b1 - a1)), (b0, b1, yaw_of(a0 - b0, a1 - b1))], ref=len(self.lines))
                self.lines.append({'a': [a0 - 0.5, a1 - 0.5], 'b': [b0 - 0.5, b1 - 0.5], 'ry': c.lift_at(a0, a1), 'place': pid,
                                   'cells': n, 'washplace': k})
                got += 1
                if got == (2 if k == 0 else 3):
                    break
        assert 4 <= len(self.lines) <= 6, f'{len(self.lines)} laundry lines'

    def build_quay(self):
        c = self.c
        cranes = [p['box'] for p in c.lm['cranes']['parts'] if p['kind'] == 'crane']
        self.quay_pickups = []
        crates = []
        for box in cranes:
            x0, z0, x1, z1 = box
            mx, mz = (x0 + x1 + 1) / 2, (z0 + z1 + 1) / 2

            def boxdist(x, z):
                return math.hypot(max(x0 - x, 0, x - (x1 + 1)), max(z0 - z, 0, z - (z1 + 1)))
            cells = [(boxdist(x, z) + 0.01 * d, x, z) for d, x, z in self.cells_around(mx, mz, 26, None)
                     if c.kind_at(x, z) == QUAY and boxdist(x, z) <= 18]
            cells.sort()
            # two clusters of four spots (2x2) nearest the base, side by side (M4 B3a: ten boatmen on four docks)
            groups = []
            for _, x, z in cells:
                # (a 2x2 block, else four in a row: the quay is a strip a few cells wide)
                # (M4 B3b: spots two apart, so a crew at a crane stands to its work, not in a knot)
                for shape, bias in (([(0, 0), (2, 0), (0, 2), (2, 2)], 0.0), ([(0, 0), (2, 0), (4, 0), (6, 0)], 2.0),
                                    ([(0, 0), (0, 2), (0, 4), (0, 6)], 2.0), ([(0, 0), (1, 0), (0, 1), (1, 1)], 6.0)):
                    grp = [(x + dx, z + dz) for dx, dz in shape]
                    if all(self.spot_ok(a, b) and c.kind_at(a, b) == QUAY for a, b in grp):
                        groups.append((sum(boxdist(a, b) for a, b in grp) + bias, x, z, grp))
            groups.sort()
            assert groups, 'no pickup cluster'
            first = groups[0]
            spots = [(a, b, yaw_of(min(max(a, x0), x1 + 1) - a, min(max(b, z0), z1 + 1) - b)) for a, b in first[3]]
            self.quay_pickups.append(self.add('pickup', spots, ref=len(self.quay_pickups)))
            node = self.places[self.quay_pickups[-1]]['node']
            nx, nz = c.nodes[node][0], c.nodes[node][2]
            # crates: against the crane base or a plot, off the walking lines
            chosen = []
            for d, x, z in self.cells_around(mx, mz, 12, None):
                if len(chosen) == 4:
                    break
                if c.kind_at(x, z) != QUAY or boxdist(x, z) < 0.4:
                    continue
                i, j = c.cell(x, z)
                touch = any((0 <= i + a < N and 0 <= j + b < N and (c.plot_at[j + b, i + a] >= 0 or c.block[j + b, i + a]))
                            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if not touch or c.edge_dist(x, z) < 1.4:
                    continue
                if any(City.seg_dist((nx, nz, s[0], s[1]), x, z) < 1.2 for s in spots):
                    continue
                if any(math.hypot(x - s[0], z - s[1]) < 1.5 for s in spots):
                    continue
                if any(math.hypot(x - a, z - b) < 1.5 for a, b in chosen + crates):
                    continue
                chosen.append((x, z))
            # the second dock of the crane: the next block that keeps clear of the first and of the crates
            taken = list(first[3]) + chosen + crates
            def joined(g):
                # (on the same walk as the first dock: a block whose nearest junction is a long way round would strand its crew)
                mx_, mz_ = sum(a for a, _ in g[3]) / 4, sum(b for _, b in g[3]) / 4
                return c.row(node)[c.nearest_node(mx_, mz_)[0]] <= 45
            second = next((g for g in groups[1:] if all(math.hypot(a - a2, b - b2) >= 0.9 for a, b in g[3] for a2, b2 in taken)
                           and joined(g)), None)
            assert second, f'no second pickup cluster ({len(groups)} groups, {len(cells)} cells)'
            sp2 = [(a, b, yaw_of(min(max(a, x0), x1 + 1) - a, min(max(b, z0), z1 + 1) - b)) for a, b in second[3]]
            self.quay_pickups.append(self.add('pickup', sp2, ref=len(self.quay_pickups)))
            crates += chosen
        assert 6 <= len(crates) <= 10, f'{len(crates)} crate cells'
        for x, z in crates:
            self.used.add(c.cell(x, z))
        self.crates = [[int(math.floor(x)), c.lift_at(x, z), int(math.floor(z))] for x, z in crates]

    def build_moorings(self):
        """M4 B3b: a berth every ~22 blocks along both quays of the river inside the wall (none by a bridge): a boatman's
        place on the quay's edge, looking down at his boat, and where the boat lies (on the river, near the lower quay,
        along the stream). The client moors its boats at exactly these (citizens.json "moorings"). Then a place by each of
        the nets (the boatmen mend them in the slack of the day)."""
        c = self.c
        river = c.P['river']
        half = float(c.P.get('riverHalfWidth', 9))
        inner = c.P['wall']['radius'] - 14
        segs, acc = [], 0.0
        for k in range(len(river) - 1):
            ax, az = river[k]
            bx, bz = river[k + 1]
            ln = math.hypot(bx - ax, bz - az)
            segs.append((ax, az, bx, bz, ln, acc))
            acc += ln

        def project(x, z):
            best = None
            for ax, az, bx, bz, ln, a0 in segs:
                t = max(0.0, min(1.0, ((x - ax) * (bx - ax) + (z - az) * (bz - az)) / (ln * ln)))
                px, pz = ax + (bx - ax) * t, az + (bz - az) * t
                d = math.hypot(x - px, z - pz)
                if best is None or d < best[0]:
                    side = 1 if (bx - ax) * (z - az) - (bz - az) * (x - ax) > 0 else -1
                    best = (d, a0 + ln * t, side, (bx - ax) / ln, (bz - az) / ln, px, pz)
            return best
        cand = []
        for j in range(N):
            for i in range(N):
                if int(c.kind[j, i]) != QUAY:
                    continue
                x, z = i - O + 0.5, j - O + 0.5
                if math.hypot(x, z) > inner or not self.spot_ok(x, z):
                    continue
                # on the quay's edge: the lower quay or the water right beside it
                drop = None
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    ii, jj = i + dx, j + dz
                    if 0 <= ii < N and 0 <= jj < N and (int(c.kind[jj, ii]) == LOWQUAY or (c.water[jj, ii] and int(c.kind[jj, ii]) == RIVER)):
                        drop = (dx, dz)
                        break
                if drop is None or c.bridge_mask[max(0, j - 5):j + 6, max(0, i - 5):i + 6].any():
                    continue
                pr = project(x, z)
                if pr is None or pr[0] > half + 6:
                    continue
                cand.append((pr[1], pr[2], x, z, drop, pr))
        cand.sort()
        self.moorings = []
        last = {1: -1e9, -1: -1e9}
        for along, side, x, z, drop, pr in cand:
            if along - last[side] < 22:
                continue
            last[side] = along
            _, _, sd, tx, tz, px, pz = pr
            nx, nz = -tz * sd, tx * sd          # toward this bank
            moor = half - 1.8
            boat = [round(px + nx * moor, 2), round(pz + nz * moor, 2)]
            heading = yaw_of(tx, tz)
            pid = self.add('mooring', [(x, z, yaw_of(*drop))], ref=len(self.moorings))
            self.moorings.append({'place': pid, 'boat': boat, 'yaw': heading, 'side': side})
        assert len(self.moorings) >= 10, f'{len(self.moorings)} moorings'
        self.net_places = []
        self.nets_taken = set()
        for pr in c.props:
            if pr['kind'] != 'net':
                continue
            mx, mz = (pr['x0'] + pr['x1'] + 1) / 2, (pr['z0'] + pr['z1'] + 1) / 2
            near = self.cells_around(mx, mz, 3.5)
            if not near:
                continue
            _, x, z = near[0]
            self.net_places.append(self.add('net', [(x, z, yaw_of(mx - x, mz - z))], ref=len(self.net_places)))

    # -- generic finders ------------------------------------------------------------------------------------------------
    def near_door_cell(self, x, z, r=2.5):
        """True if a door cell (plot door or landmark door) is within r of the point."""
        c = self.c
        for (dx, dz) in list(c.door_cells) + list(c.landmark_cells):
            if abs(dx + 0.5 - x) <= r and abs(dz + 0.5 - z) <= r:
                return True
        return False

    def wall_row(self, cx, cz, n, rmax, kinds, door_gap=1.5, apart_from=(), min_d=0.0):
        """n consecutive standing cells along a wall (plot or landmark) near (cx, cz): [(x, z, yaw)] facing away from the
        wall, or None. Cells are of the given kinds, free, and clear of doors."""
        c = self.c
        best = None
        r = int(rmax) + 1
        for j in range(int(math.floor(cz)) - r, int(math.floor(cz)) + r + 1):
            for i in range(int(math.floor(cx)) - r, int(math.floor(cx)) + r + 1):
                for (tx, tz) in ((1, 0), (0, 1)):
                    for (wx, wz) in ((tz, tx), (-tz, -tx)):
                        cells = [(i + tx * k, j + tz * k) for k in range(n)]
                        mid = (cells[0][0] + cells[-1][0]) / 2 + 0.5, (cells[0][1] + cells[-1][1]) / 2 + 0.5
                        d = math.hypot(mid[0] - cx, mid[1] - cz)
                        if d > rmax or d < min_d or (best is not None and d >= best[0]):
                            continue
                        ok = True
                        for (ci, cj) in cells:
                            x, z = ci + 0.5, cj + 0.5
                            ni, nj = ci + wx, cj + wz
                            if not (self.spot_ok(x, z) and c.kind_at(x, z) in kinds):
                                ok = False
                                break
                            wi, wj = ni + O, nj + O
                            if not (c.plot_at[wj, wi] >= 0 or c.block[wj, wi] or c.stall_mask[wj, wi]) or self.near_door_cell(x, z, door_gap):
                                ok = False
                                break
                            if any(math.hypot(x - a, z - b) < 3.0 for a, b in apart_from):
                                ok = False
                                break
                        if ok:
                            best = (d, [(ci + 0.5, cj + 0.5, yaw_of(-wx, -wz)) for ci, cj in cells])
        return best[1] if best else None

    def junction_stand(self, node, cap=2, r=3.6):
        c = self.c
        nx, nz = c.nodes[node][0], c.nodes[node][2]
        cells = self.cells_around(nx, nz, r, None)
        got = self.pick_apart([(x, z) for _, x, z in cells], cap, sep=1.5)
        if len(got) < cap:
            return None
        return [(x, z, yaw_of(nx - x, nz - z)) for x, z in got]

    def junctions(self, pred):
        """Main-component junction nodes (>= 3 edges, on walkable ground) whose position satisfies pred(x, z)."""
        c = self.c
        out = []
        for n in c.main:
            nd = c.nodes[n]
            if nd[3] != 'junction' or sum(1 for v, _, _ in c.adj[n] if v not in c.wall_nodes) < 3:
                continue
            i, j = c.cell(nd[0], nd[2])
            if c.ground[j, i] and pred(nd[0], nd[2], int(c.dist[j, i])):
                out.append(n)
        return out

    # -- more places ------------------------------------------------------------------------------------------------------
    def build_misc(self):
        c = self.c
        bells = {b['landmark']: b for b in self.bells}
        centre = {d['key']: d['bell'] for d in c.P['districts']}
        dist_of = {d['key']: d['id'] for d in c.P['districts']}
        # mills: the walkable cells nearest each mill's box on its water side
        self.mill_places = []
        for part in [p for p in c.lm['mills']['parts'] if p['kind'] == 'mill']:
            x0, z0, x1, z1 = part['box']
            mx = (x0 + x1 + 1) / 2
            cells = [(abs(x - mx) + 3.0 * abs(z - (z1 + 1.5)), x, z) for d, x, z in self.cells_around(mx, z1 + 2.0, 6, None)]
            cells.sort()
            got = self.pick_apart([(x, z) for _, x, z in cells], 2)
            assert len(got) == 2, 'mill spots'
            self.mill_places.append(self.add('mill', [(x, z, 180) for x, z in got], ref=len(self.mill_places)))
        # gate posts: the inner mouth of each passage, one post of 2 per gate
        self.post_places = []
        for gid, (ux, uz) in (('gate_n', (0, 1)), ('gate_e', (-1, 0)), ('gate_s', (0, -1))):
            box = next(p['box'] for p in c.lm[gid]['parts'] if p['kind'] == 'gate_passage')
            x0, z0, x1, z1 = box
            if ux == 0:
                row = z1 if uz > 0 else z0
                spots = [(x0 + 0.5, row + 0.5), (x1 + 0.5, row + 0.5)]
            else:
                col = x0 if ux < 0 else x1
                spots = [(col + 0.5, z0 + 0.5), (col + 0.5, z1 + 0.5)]
            self.post_places.append(self.add('post', [(x, z, yaw_of(-ux, -uz)) for x, z in spots], ref=gid))
        # M3's benches for residents: cap 3 in a row against a wall of a plaza. M4 B3a keeps one only where no bench prop stands
        # within 20 (the benches, the fountains' rims and the square's steps are the sit places now)
        self.wall_sits = []
        bench_xy = [((pr['x0'] + pr['x1'] + 1) / 2.0, (pr['z0'] + pr['z1'] + 1) / 2.0) for pr in c.props if pr['kind'] == 'bench']
        sites = [(dk, None) for dk in ('bellwatch', 'bellwatch', 'quay', 'quay', 'terrace', 'terrace', 'garden', 'garden',
                                        'works', 'works')]
        taken = [(p['x'], p['z']) for b in self.bells for p in [self.places[b['place']]]]
        for k, (dk, at) in enumerate(sites):
            bx, bz = centre[dk]
            ang = math.radians(35 + 180 * (k % 2) + 20 * (k // 2))
            at = (bx + 9 * math.cos(ang), bz + 9 * math.sin(ang))
            row = None
            for rr in (6, 10, 16, 24):
                row = self.wall_row(at[0], at[1], 3, rr, (SQUARE, PLAZA), apart_from=taken)
                if row:
                    break
            if not row or min(math.hypot(row[1][0] - a, row[1][1] - b) for a, b in bench_xy) < 20:
                continue
            taken.append((row[1][0], row[1][1]))
            self.wall_sits.append(self.add('sit', row, ref=-1))
        # mason yards: cap 4 along a wall in the Works
        self.yard_places = []
        for at in ((26, 150), (60, 160), (-20, 125)):
            row = None
            for rr in (8, 14, 22):
                row = self.wall_row(at[0], at[1], 4, rr, (SQUARE, PLAZA, STREET, AVENUE, QUAY), apart_from=taken)
                if row:
                    break
            assert row, f'no yard near {at}'
            taken.append((row[1][0], row[1][1]))
            self.yard_places.append(self.add('stand', row, ref=-1))
        # the Association's terrace: two stands of 2 in front of the hall, off the doorway
        self.terrace_places = []
        for at in ((-12, -134), (12, -134)):
            row = self.wall_row(at[0], at[1], 2, 8, (SQUARE, PLAZA, AVENUE, STREET), apart_from=taken)
            assert row, 'terrace stand'
            taken.append((row[0][0], row[0][1]))
            self.terrace_places.append(self.add('stand', row, ref=-1))
        # criers: five posts near the Choir's door, then two per district plaza
        self.crier_first = []
        dn = c.landmark_node['choir']
        dx0, dz0 = c.nodes[dn][0], c.nodes[dn][2]
        cells = self.cells_around(dx0, dz0 + 4, 9, lambda x, z: abs(x - dx0) >= 3.0 and not self.near_door_cell(x, z, 1.5))
        got = []
        for ang in (-70, -35, 0, 35, 70):
            best = min(cells, key=lambda t: (abs(math.degrees(math.atan2(t[2] - dz0, t[1] - dx0)) - 90 - ang) + 0.1 * t[0]) if t[0] > 3.5 else 1e9)
            got.append((best[1], best[2]))
        got = self.pick_apart(sorted(set(got)), 5, sep=3.0)
        assert len(got) == 5, f'{len(got)} crier posts at the Choir'
        for x, z in got:
            self.crier_first.append(self.add('crier', [(x, z, 0)], ref=-1))
        self.crier_plaza = {}
        for dk in ('bellwatch', 'quay', 'terrace', 'garden', 'works'):
            bx, bz = centre[dk]
            ps = []
            for ang in (45, 225):
                cands = [(abs(math.degrees(math.atan2(z - bz, x - bx)) - ang + 540) % 360 - 180, d, x, z)
                         for d, x, z in self.cells_around(bx, bz, 17, lambda x, z: c.kind_at(x, z) == PLAZA)
                         if 8.5 <= d]
                cands.sort(key=lambda t: (abs(t[0]) // 15, t[1]))
                x, z = cands[0][2], cands[0][3]
                ps.append(self.add('crier', [(x, z, yaw_of(bx - x, bz - z))], ref=-1))
            self.crier_plaza[dk] = ps

    def build_named(self):
        """Kestrel's post (one standing cell beside the Roost's street door) and the notice board in the Association's forecourt
        that Bram takes his spell at (the nearest board place)."""
        c = self.c
        nd = c.nodes[c.landmark_node['roost']]
        cells = self.cells_around(nd[0], nd[2], 10.0, lambda x, z: not self.near_door_cell(x, z, 1.0) and c.edge_dist(x, z) >= 1.0)
        assert cells, 'no cell beside the Roost door'
        _, x, z = cells[0]
        self.roost_stand = self.add('stand', [(x, z, yaw_of(nd[0] - x, nd[2] - z))], ref=-1)
        an = c.nodes[c.landmark_node['association']]
        self.assoc_notice = min(self.notice_places, key=lambda p: (math.hypot(self.places[p]['x'] - an[0], self.places[p]['z'] - an[2]), p))
        pl = self.places[self.assoc_notice]
        assert math.hypot(pl['x'] - an[0], pl['z'] - an[2]) <= 30, 'no notice board near the Association'

    # -- the watch's junction stands, patrol loops, the scout, the couriers ---------------------------------------------------
    def stand_at_node(self, node):
        if not hasattr(self, '_jstand'):
            self._jstand = {}
        if node not in self._jstand:
            sp = self.junction_stand(node)
            self._jstand[node] = self.add('stand', sp, ref=-1) if sp else None
        return self._jstand[node]

    def build_patrols(self):
        c = self.c
        rr = rng(0xA7201)
        plan = [('market', 2), ('bellwatch', 2), ('quay', 1), ('terrace', 2), ('works', 2), ('garden', 2), ('wallring', 1)]
        self.patrols = []
        did = {d['key']: d['id'] for d in c.P['districts']}
        for dk, count in plan:
            pool = self.junctions(lambda x, z, d: d == did[dk] and self.junction_stand(c.nearest_node(x, z)[0]) is not None)
            pool = [n for n in pool if self.junction_stand(n) is not None]
            made = []
            for _ in range(count):
                loop = self.make_loop(pool, rr, [n for r in made for n in r], dk == 'wallring')
                assert loop, f'no patrol loop in {dk}'
                made.append(loop)
                places = [self.stand_at_node(n) for n in loop]
                assert all(p is not None for p in places)
                self.patrols.append({'district': dk, 'nodes': loop, 'places': places})

    def snode(self, n):
        """The street-graph node a stand at junction n would get (nearest to its middle spot)."""
        if not hasattr(self, '_snode'):
            self._snode = {}
        if n not in self._snode:
            sp = self.junction_stand(n)
            c = self.c
            cx = sum(q[0] for q in sp) / len(sp)
            cz = sum(q[1] for q in sp) / len(sp)
            mid = min(sp, key=lambda q: (math.hypot(q[0] - cx, q[1] - cz), sp.index(q)))
            self._snode[n] = c.nearest_node(mid[0], mid[1])[0]
        return self._snode[n]

    def gd(self, a, b):
        """Graph distance between two junction stands (their own nodes)."""
        return self.c.row(self.snode(a))[self.snode(b)]

    def make_loop(self, pool, rr, avoid, chain):
        c = self.c
        best = None
        for attempt in range(400):
            cn = rr.choice(pool)
            if any(self.gd(cn, a) < 40 for a in avoid) and attempt < 300:
                continue
            R = rr.uniform(26, 38)
            ring = [n for n in pool if abs(self.gd(cn, n) - R) < 9]
            if len(ring) < 5:
                continue
            ring.sort(key=lambda n: math.atan2(c.nodes[n][2] - c.nodes[cn][2], c.nodes[n][0] - c.nodes[cn][0]))
            k = rr.randint(5, 8)
            if len(ring) < k:
                k = len(ring)
            step = len(ring) / k
            stops = [ring[int(i * step + rr.random() * 0.5 * step) % len(ring)] for i in range(k)]
            stops = list(dict.fromkeys(stops))
            if len(stops) < 5:
                continue
            ok = True
            tot = 0
            for a, b in zip(stops, stops[1:] + stops[:1]):
                d = self.gd(a, b)
                tot += d
                if not (25.5 <= d <= 59.5):
                    ok = False
                    break
            if ok and (best is None or tot > best[0]):
                best = (tot, stops)
        if best:
            return best[1]
        return self.make_chain(pool, rr, avoid)

    def make_chain(self, pool, rr, avoid):
        """Out and back along a street: s0..s4 then s3, s2, s1 (the loop closes s1 -> s0)."""
        c = self.c
        for attempt in range(2000):
            a = rr.choice(pool)
            if avoid and attempt < 1500 and any(self.gd(a, x) < 30 for x in avoid):
                continue
            chain = [a]
            for _ in range(4):
                nxt = [n for n in pool if 25.5 <= self.gd(chain[-1], n) <= 40 and n not in chain
                       and all(self.gd(n, m) >= 22 for m in chain)]
                if not nxt:
                    break
                prev = chain[-2] if len(chain) > 1 else None
                if prev is not None:
                    d0 = (c.nodes[chain[-1]][0] - c.nodes[prev][0], c.nodes[chain[-1]][2] - c.nodes[prev][2])
                    nxt = [n for n in nxt if (c.nodes[n][0] - c.nodes[chain[-1]][0]) * d0[0] + (c.nodes[n][2] - c.nodes[chain[-1]][2]) * d0[1] > 0] or nxt
                chain.append(rr.choice(nxt))
            if len(chain) == 5:
                return chain + chain[3:0:-1]
        return None

    def build_errand_corners(self):
        """Places to run errands to: wherever fewer than 5 eligible places lie within 40 blocks' walk of a workplace, add
        stands (cap 2) at the nearest street corners until there are."""
        anchors = [s_['anvil'] for s_ in self.smithies] + list(self.mill_places) + list(self.yard_places) + list(self.quay_pickups) \
            + [w['places'][0] for w in self.washplaces] + [ln['place'] for ln in self.lines] \
            + [self.landmark('association'), self.landmark('choir')] + [s_['keeper'] for s_ in self.stalls] \
            + [b['place'] for b in self.bells] + [sh['stand'] for sh in self.shops[::6]]
        for a in anchors:
            self.ensure_corners(a, 40.0, 5)

    def ensure_corners(self, a, radius, need):
        """At least `need` eligible places within `radius` blocks' walk of place a (a place id or a house door): corner
        stands (cap 2), 12 apart, are added at the nearest street corners if there are fewer."""
        c = self.c
        if not hasattr(self, '_corner_pool'):
            self._corner_pool = [n for n in self.junctions(lambda x, z, d: True) if self.junction_stand(n) is not None]
        row = c.row(self.pn(a))
        ca = self.cost(a)
        stands = [p for p in self._jstand.values() if p is not None]
        have = sum(1 for p, pl in enumerate(self.places) if (pl['kind'] in ('tavern_out', 'sit', 'shop_door', 'stall_front') or p in stands)
                   and self.wd(p, a) <= radius)
        if have >= need:
            return
        for n in sorted((n for n in self._corner_pool if n not in self._jstand), key=lambda n: (row[self.snode(n)], n)):
            if have >= need or row[self.snode(n)] + ca > radius - 2:
                break
            # keep new corners 12 apart from the ones already there
            if any(self.gd(n, m) < 12 for m in self._jstand if self._jstand[m] is not None and abs(c.nodes[m][0] - c.nodes[n][0]) < 14 and abs(c.nodes[m][2] - c.nodes[n][2]) < 14):
                continue
            self.stand_at_node(n)
            have += 1

    def build_scout_courier(self):
        c = self.c
        rr = rng(0x5C007)
        # the scout: gate posts and four Wallring points, by angle around the wall
        ring_pool = self.junctions(lambda x, z, d: d == 7 and self.junction_stand(c.nearest_node(x, z)[0]) is not None)
        ring_pool = [n for n in ring_pool if self.junction_stand(n) is not None]
        pts = []
        for ang in (-45, 45, 135, 225):
            a = math.radians(ang)
            tx, tz = 222 * math.cos(a), 222 * math.sin(a)
            n = min(ring_pool, key=lambda q: math.hypot(c.nodes[q][0] - tx, c.nodes[q][2] - tz))
            pts.append((ang, self.stand_at_node(n)))
        gates = [(-90, self.post_places[0]), (0, self.post_places[1]), (90, self.post_places[2])]
        order = sorted(gates + pts, key=lambda t: t[0] % 360 if t[0] != -90 else 270)
        self.scout = [p for _, p in sorted(gates + pts, key=lambda t: (t[0] + 90) % 360)]
        # couriers: Roost, Association, then 3-4 junction stands, loop
        roost, assoc = self.landmark('roost'), self.landmark('association')
        specs = [[(-138, -108), (-95, -100), (-40, -105)], [(-120, -140), (-90, -130), (-60, -160)], [(-110, -80), (-95, -160), (-50, -140)]]
        self.courier = []
        pool = self.junctions(lambda x, z, d: self.junction_stand(c.nearest_node(x, z)[0]) is not None)
        pool = [n for n in pool if self.junction_stand(n) is not None]
        for spec in specs:
            stops = [roost, assoc]
            for (tx, tz) in spec:
                n = min(pool, key=lambda q: math.hypot(c.nodes[q][0] - tx, c.nodes[q][2] - tz))
                stops.append(self.stand_at_node(n))
            self.courier.append(stops)

    # -- distances between places ----------------------------------------------------------------------------------------
    def pn(self, p):
        return self.c.door_node[p[1]] if isinstance(p, tuple) else self.places[p]['node']

    def cost(self, p):
        """Straight walk from a place's node to its first spot (the last blocks, not on the graph)."""
        if isinstance(p, tuple):
            return 0.0
        if not hasattr(self, '_cost'):
            self._cost = {}
        co = self._cost.get(p)
        if co is None:
            pl = self.places[p]
            nd = self.c.nodes[pl['node']]
            sp = pl['spots'][0]
            co = self._cost[p] = math.hypot(sp[0] - nd[0], sp[1] - nd[2])
        return co

    def wd(self, a, b):
        """Walking distance in blocks between two places (a place id, or ('h', plot) for a house door)."""
        if a == b:
            return 0.0
        return self.c.row(self.pn(a))[self.pn(b)] + self.cost(a) + self.cost(b)

    # -- routes ------------------------------------------------------------------------------------------------------------
    def add_route(self, kind, places):
        rid = len(self.routes)
        self.routes.append({'id': rid, 'kind': kind, 'places': list(places)})
        return rid

    def build_lamp_routes(self):
        """Cut one short walk through all the lamps into ceil(lamps / 10) tours (the cuts balance the time of the longest)."""
        L = self.lamp_places
        n = len(L)
        K = -(-n // 10)
        D = [[self.wd(a, b) for b in L] for a in L]

        def two_opt(path):
            improved = True
            while improved:
                improved = False
                for i in range(0, len(path) - 1):
                    for j in range(i + 1, len(path)):
                        a = path[i - 1] if i > 0 else None
                        b, c_ = path[i], path[j]
                        d = path[j + 1] if j + 1 < len(path) else None
                        before = (D[a][b] if a is not None else 0) + (D[c_][d] if d is not None else 0)
                        after = (D[a][c_] if a is not None else 0) + (D[b][d] if d is not None else 0)
                        if after + 1e-9 < before:
                            path[i:j + 1] = reversed(path[i:j + 1])
                            improved = True
            return path
        best = None
        px = [self.places[p]['x'] for p in L]
        pz = [self.places[p]['z'] for p in L]
        starts = {min(range(n), key=lambda q: (v(q), q)) for v in (lambda q: px[q], lambda q: pz[q], lambda q: px[q] + pz[q], lambda q: px[q] - pz[q])}
        starts |= {max(range(n), key=lambda q: (v(q), -q)) for v in (lambda q: px[q], lambda q: pz[q], lambda q: px[q] + pz[q], lambda q: px[q] - pz[q])}
        starts |= set(range(0, n, max(1, n // 8)))
        for st in sorted(starts):
            path = [st]
            rest = set(range(n)) - {st}
            while rest:
                nx = min(rest, key=lambda q: (D[path[-1]][q], q))
                path.append(nx)
                rest.remove(nx)
            path = two_opt(path)
            tot = sum(D[a][b] for a, b in zip(path, path[1:]))
            if best is None or tot < best[0]:
                best = (tot, path[:])
        path = best[1]

        def seglen(i, j):
            return sum(D[path[k]][path[k + 1]] for k in range(i, j - 1))
        memo = {}

        def f(i, r):
            if r == 0:
                return (0.0 if i == n else 1e9, ())
            if (i, r) in memo:
                return memo[(i, r)]
            bst = (1e9, ())
            for sz in range(LAMP_MIN, LAMP_MAX + 1):
                j = i + sz
                if j > n:
                    break
                cost = seglen(i, j) / (RUN_PACE * 1.07) + 40 * sz     # ticks of a light round
                sub = f(j, r - 1)
                val = max(cost, sub[0])
                if val < bst[0] - 1e-9:
                    bst = (val, ((i, j),) + sub[1])
            memo[(i, r)] = bst
            return bst
        _, segs = f(0, K)
        assert segs, f'{n} lamps cannot be cut into {K} tours of {LAMP_MIN}-{LAMP_MAX}'
        self.lamp_clusters = []
        for (i, j) in segs:
            members = [path[k] for k in range(i, j)]
            self.lamp_clusters.append({'members': members, 'route': self.add_route('lamps', [L[m] for m in members])})

    def order_tour(self, rid, start_lamp_place):
        """A lamp route is a path: it starts at the end the lamplighter lives nearer to (the other end is the last)."""
        r = self.routes[rid]
        pl = r['places']
        if pl[-1] == start_lamp_place or (pl[0] != start_lamp_place and self.wd(start_lamp_place, pl[-1]) < self.wd(start_lamp_place, pl[0])):
            pl.reverse()

    def route_len(self, rid):
        pl = self.routes[rid]['places']
        return sum(self.wd(a, b) for a, b in zip(pl, pl[1:]))

    def eligible(self):
        """Places a wandering citizen may stop at on an errand."""
        out = []
        for pid, pl in enumerate(self.places):
            if pl['kind'] in ('tavern_out', 'sit', 'shop_door', 'stall_front'):
                out.append(pid)
            elif pl['kind'] == 'stand' and pl['ref'] == -1 and pl['cap'] == 2 and pid in self._jstand_ids:
                out.append(pid)
        return out

    def build_routes(self):
        c = self.c
        self._jstand_ids = {p for p in self._jstand.values() if p is not None}
        # patrols, the scout, criers, couriers
        self.patrol_routes = [self.add_route('patrol', p['places']) for p in self.patrols]
        self.scout_route = self.add_route('scout', self.scout)
        rcentre = {d['key']: d['id'] for d in c.P['districts']}
        order = ['bellwatch', 'quay', 'works', 'garden', 'terrace']   # a ring, so each loop visits two neighbouring plazas
        self.crier_routes = []
        for k in range(5):
            a, b = order[k], order[(k + 1) % 5]
            self.crier_routes.append(self.add_route('crier', [self.crier_first[k], self.crier_plaza[a][0], self.crier_plaza[b][1]]))
        self.courier_routes = [self.add_route('courier', stops) for stops in self.courier]
        # the porters' drops: the square's 24 stall fronts, 6 to each of the four docks (each crane serves the half of the ring
        # nearer it, its two docks one side of that half each, so a carry never crosses the whole square); one per bakery: its two
        # nearest fronts (the plazas' stalls)
        pk = self.quay_pickups
        fr = [self.stalls[k]['front'] for k in self.square_stalls]
        per_crane = [[], []]
        for f in sorted(fr, key=lambda p: (self.wd(pk[0], p) - self.wd(pk[2], p), p)):
            per_crane[0 if len(per_crane[0]) < len(fr) // 2 else 1].append(f)
        self.drops_routes = []
        for cr in range(2):
            half = sorted(per_crane[cr], key=lambda p: math.atan2(self.places[p]['z'], self.places[p]['x']))
            self.drops_routes += [self.add_route('drops', half[:len(half) // 2]), self.add_route('drops', half[len(half) // 2:])]
        fronts = fr
        for b in self.bakeries:
            b['drops_route'] = self.add_route('drops', b['drops'])
        # benches; the docks (the porters' noon break: they linger where the crates are)
        self.sit_route = self.add_route('sit', self.sit_places)
        self.dock_route = self.add_route('dock', self.quay_pickups)
        # one errand route per district (stall fronts for the market): the nearest sit places and tavern doors, then corner stands
        self.district_errand = {}
        tav_outs = {t['out'] for t in self.taverns}
        for dk, did in rcentre.items():
            bx, bz = next(d['bell'] for d in c.P['districts'] if d['key'] == dk)
            near = lambda p: (math.hypot(self.places[p]['x'] - bx, self.places[p]['z'] - bz), p)   # noqa: E731
            pl = sorted((p for p in range(len(self.places)) if self.places[p]['district'] == did
                         and (self.places[p]['kind'] == 'sit' or p in tav_outs)), key=near)[:6]
            if dk == 'market':
                fr = sorted(fronts, key=lambda p: (self.places[p]['x'] ** 2 + self.places[p]['z'] ** 2, p))
                pl = pl[:4] + fr[:5]
            extra = sorted((p for p in self._jstand_ids if self.places[p]['district'] == did), key=near)
            for p in extra:
                if len(pl) >= 6:
                    break
                pl.append(p)
            assert 4 <= len(pl) <= 10, f'errand route of {dk} has {len(pl)} places'
            self.district_errand[dk] = self.add_route('errand', pl)
        self.build_life_routes()
        self._errand_cache = {tuple(sorted(r['places'])): r['id'] for r in self.routes if r['kind'] == 'errand'}

    def build_wall_rounds(self):
        """(M4 Walls) The Watch's rounds of the wall walk: the chain cut into four stretches, each a round of eight stands
        on the walk about fifty apart, walked out and back by a night watchman (route kind 'wall')."""
        c = self.c
        chain = sorted(n for n in c.wall_nodes if c.nodes[n][1] == WALK_RY)
        cum = [0.0]
        for a, b in zip(chain, chain[1:]):
            cum.append(cum[-1] + math.hypot(c.nodes[a][0] - c.nodes[b][0], c.nodes[a][2] - c.nodes[b][2]))
        total = cum[-1]
        self.wall_routes = []
        for q in range(4):
            lo, hi = q * total / 4 + 12, (q + 1) * total / 4 - 12
            stops = []
            for i in range(8):
                want = lo + (hi - lo) * i / 7
                n = chain[min(range(len(chain)), key=lambda k: (abs(cum[k] - want), k))]
                nd = c.nodes[n]
                x, z = math.floor(nd[0]) + 0.5, math.floor(nd[2]) + 0.5
                ix, iz = (1, 0) if dominant(-nd[0], -nd[2]) == SIDE_E else (-1, 0) if dominant(-nd[0], -nd[2]) == SIDE_W \
                    else (0, 1) if dominant(-nd[0], -nd[2]) == SIDE_S else (0, -1)
                yaw = yaw_of(ix, iz)
                pid = len(self.places)
                self.places.append({'kind': 'stand', 'node': n, 'x': x, 'z': z, 'ry': WALK_RY, 'yaw': yaw, 'cap': 2, 'ref': -1,
                                    'district': c.district_at(x, z), 'approach': 0,
                                    'spots': [[x, z, yaw], [x + ix, z + iz, yaw]]})
                stops.append(pid)
            self.wall_routes.append(self.add_route('wall', stops))

    def build_life_routes(self):
        """M4 B3a: where people gather. Six `market` routes, one per sixth of the square: its four stall fronts and the benches,
        fountain rims, steps and board of the bowl on that side. `stroll` routes everywhere else: the benches, wells and boards of
        a district in groups of at most six within 70 blocks of each other. Every prop place is on one of them."""
        c = self.c
        P = self.places
        near_sq = lambda p: math.hypot(P[p]['x'], P[p]['z']) < SQ_R and P[p]['district'] == 1   # noqa: E731
        self.square_places = [p for p in self.bench_places + self.fountain_places + self.step_places + self.well_places + self.notice_places
                              if near_sq(p)]
        ang = lambda p: math.atan2(P[p]['z'], P[p]['x'])    # noqa: E731
        sq = sorted((self.stalls[k]['front'] for k in self.square_stalls), key=ang)
        sectors = [sq[i * len(sq) // 6:(i + 1) * len(sq) // 6] for i in range(6)]
        self.market_routes = []
        for grp in sectors:
            # the square's other places go to the quarter whose fronts are nearest them
            self.market_routes.append(grp)
        for p in self.square_places:
            best = min(range(6), key=lambda i: (min(math.hypot(P[p]['x'] - P[f]['x'], P[p]['z'] - P[f]['z']) for f in sectors[i]), i))
            self.market_routes[best] = self.market_routes[best] + [p]
        for i, pl in enumerate(self.market_routes):
            cx = sum(P[p]['x'] for p in pl) / len(pl)
            cz = sum(P[p]['z'] for p in pl) / len(pl)
            self.market_routes[i] = self.add_route('market', sorted(pl, key=lambda p: math.atan2(P[p]['z'] - cz, P[p]['x'] - cx)))
        # strolls: everywhere else
        pool = [p for p in self.bench_places + self.well_places + self.notice_places + self.wall_sits if p not in set(self.square_places)]
        self.stroll_routes = []
        left = sorted(pool, key=lambda p: (P[p]['district'], P[p]['x'], P[p]['z']))
        while left:
            seed = left.pop(0)
            grp = [seed]
            for p in sorted(left, key=lambda p: (math.hypot(P[p]['x'] - P[seed]['x'], P[p]['z'] - P[seed]['z']), p)):
                if len(grp) == 6 or P[p]['district'] != P[seed]['district']:
                    continue
                if math.hypot(P[p]['x'] - P[seed]['x'], P[p]['z'] - P[seed]['z']) > 70:
                    break
                grp.append(p)
            left = [p for p in left if p not in grp]
            gx = sum(P[p]['x'] for p in grp) / len(grp)
            gz = sum(P[p]['z'] for p in grp) / len(grp)
            grp.sort(key=lambda p: math.atan2(P[p]['z'] - gz, P[p]['x'] - gx))
            self.stroll_routes.append(self.add_route('stroll', grp))

    def errand_route(self, all_a, each_a):
        """An errand route (a district's, else a local one of the nearest eligible places) with every place within the
        limit of every anchor in all_a [(place, blocks)] (the walk out of it) and, for every anchor in each_a, at least one
        place within its limit (the walk in is to the nearest place). None if there is no such route."""
        def fits(pl):
            if not all(self.wd(p, a) <= lim for p in pl for a, lim in all_a):
                return False
            return all(any(self.wd(p, a) <= lim for p in pl) for a, lim in each_a)
        for rid in [r_['id'] for r_ in self.routes if r_['kind'] == 'errand']:
            if fits(self.routes[rid]['places']):
                return rid
        el = self.eligible()
        ok = [p for p in el if all(self.wd(p, a) <= lim for a, lim in all_a)]
        if len(ok) < 4:
            return None
        pick = []
        for a, lim in each_a:
            if any(self.wd(p, a) <= lim for p in pick):
                continue
            near = sorted((p for p in ok if self.wd(p, a) <= lim), key=lambda p: (self.wd(p, a), p))
            if not near:
                return None
            pick.append(near[0])
        anchors = list(all_a) + list(each_a)
        rest = sorted((p for p in ok if p not in pick), key=lambda p: (sum(self.wd(p, a) for a, _ in anchors), p))
        pick += rest[:7 - len(pick)]
        cx = sum(self.places[p]['x'] for p in pick) / len(pick)
        cz = sum(self.places[p]['z'] for p in pick) / len(pick)
        key = tuple(sorted(pick))
        if key in self._errand_cache:
            return self._errand_cache[key]
        pick.sort(key=lambda p: math.atan2(self.places[p]['z'] - cz, self.places[p]['x'] - cx))
        rid = self.add_route('errand', pick)
        self._errand_cache[key] = rid
        return rid


# ======================================================================================================================
#  the people
# ======================================================================================================================
PENDING_TAGS = ('tav', 'sit', 'loc', 'msit')    # stands whose place is chosen last, among those with room
AGE_WEIGHTS = {  # [child, young, adult, old]
    'lamplighter': (0, .2, .6, .2), 'bellringer': (0, 0, .5, .5), 'porter': (0, .5, .5, 0), 'stallkeeper': (0, .25, .5, .25),
    'baker': (0, .2, .6, .2), 'smith': (0, .3, .7, 0), 'watch': (0, .5, .5, 0), 'crier': (0, 0, .5, .5),
    'boatman': (0, .4, .6, 0), 'washer': (0, 0, .6, .4), 'mason': (0, .3, .5, .2), 'runner': (0, 1, 0, 0),
    'shopkeeper': (0, .2, .6, .2), 'tavernkeeper': (0, 0, .8, .2), 'clerk': (0, .4, .6, 0), 'archivist': (0, 0, .5, .5),
    'healer': (0, 0, .7, .3), 'miller': (0, 0, .7, .3), 'child': (1, 0, 0, 0), 'resident': (0, 0, .3, .7)}
# (job, index within the job) -> (name, age class). Kestrel is the 11th runner and Bram the 11th clerk (M4 B3a).
NAMED = {('smith', 0): ('oren', 2), ('watch', 0): ('mira', 1), ('archivist', 0): ('teodor', 3), ('healer', 0): ('sun', 2),
         ('runner', 10): ('kestrel', 2), ('clerk', 10): ('bram', 2)}
NAMED_GIVEN = {'oren': 'Oren', 'mira': 'Mira', 'teodor': 'Teodor', 'sun': 'Sun', 'kestrel': 'Kestrel', 'bram': 'Bram'}
# residents by kind: market-goers, neighbours, elders (age weights child, young, adult, old)
RESIDENT_AGES = {'market': (0, .3, .5, .2), 'neighbour': (0, .1, .6, .3), 'elder': (0, 0, .1, .9)}


def S(start, act, target=-1, alt=-1, dwell=0, flags=0, tag='', fix=False):
    """One segment. `fix`: its start may not be pushed later (the lamp rounds are timed by dusk and dawn)."""
    return {'s': start, 'a': act, 't': target, 'alt': alt, 'd': dwell, 'f': flags, 'tag': tag, 'fix': fix}


class Population:
    def __init__(self, B):
        self.B = B
        self.c = B.c
        self.cits = []
        self._tav_rank = {}

    # -- the roster ------------------------------------------------------------------------------------------------------
    def make_roster(self):
        B = self.B
        r = rng(0xC1715)
        cits = self.cits

        def new(job, **kw):
            idx_in_job = sum(1 for q in cits if q['job'] == job)
            named, age = NAMED.get((job, idx_in_job), ('', None))
            cit = {'id': len(cits), 'job': job, 'named': named, 'k': idx_in_job, **kw}
            w = RESIDENT_AGES[kw['kind']] if job == 'resident' else AGE_WEIGHTS[job]
            if age is None:
                age = r.choices(range(4), weights=w)[0]
            cit['age'] = age
            cit['build'] = r.randrange(2)
            if age == 0:
                cit['scale'] = round(r.uniform(0.62, 0.70), 2)
                cit['speed'] = round(r.uniform(1.05, 1.15), 2)
            elif age == 3:
                cit['scale'] = round(r.uniform(0.93, 0.99), 2)
                cit['speed'] = round(r.uniform(0.85, 0.95), 2)
            else:
                cit['scale'] = round(r.uniform(0.96, 1.05), 2)
                cit['speed'] = round(r.uniform(0.92, 1.08), 2)
            if job == 'runner':
                cit['speed'] = 1.0
            if job == 'lamplighter':
                cit['speed'] = round(r.uniform(1.05, 1.08), 2)
            if named:
                cit['build'] = 0      # (the named wear a wide townsfolk skin)
            cits.append(cit)
            return cit
        for i in range(len(B.lamp_clusters)):
            new('lamplighter', cluster=i)
        for i in range(7):
            new('bellringer', bell=min(i, 5))
        npick = len(B.quay_pickups)
        for i in range(26):
            new('porter', pickup=i % npick)
        for k in range(len(B.stalls)):      # one opener per stall
            new('stallkeeper', stall=k, role=0)
        sq = B.square_stalls
        for i in range(14):                 # helpers on the square's stalls, spread round it
            new('stallkeeper', stall=sq[(i * len(sq)) // 14], role=1)
        for i in range(10):
            new('baker', bakery=i % 5, rank=i // 5)
        for i in range(8):
            new('smith', smithy=i // 2, rank=i % 2)
        for i in range(32):
            if i == 0:
                new('watch', mode='scout')
            elif i <= 6:
                new('watch', mode='gate', post=(i - 1) // 2)
            elif i <= 15:
                new('watch', mode='patrol', slot=i - 7)
            else:
                new('watch', mode='night', slot=i - 16)
        for i in range(7):
            new('crier', route=i % 5)
        nm = len(B.moorings)
        for i in range(10):
            new('boatman', mooring=(i * nm) // 10)
        # washers: 7 at each washplace; six hold a wash place (the ones nearest a line hang the laundry), the 7th tends
        for wp in range(2):
            lines = [ln for ln in B.lines if ln['washplace'] == wp]
            places = B.washplaces[wp]['places']
            order = sorted(range(len(places)), key=lambda q: (min(B.wd(places[q], ln['place']) for ln in lines), q))
            hang = set(order[:2 * len(lines)])
            hang_order = [q for q in order if q in hang]
            for slot in range(6):
                q = slot
                if q in hang:
                    h = hang_order.index(q)
                    new('washer', washplace=wp, slot=q, role='hanger', line=h // 2)
                else:
                    new('washer', washplace=wp, slot=q, role='plain', line=-1)
            new('washer', washplace=wp, slot=hang_order[0], role='extra', line=0)
        for i in range(12):
            new('mason', yard=i % 3)
        for i in range(10):
            new('runner', route=i % 3, slot=i // 3, mode='courier')
        new('runner', mode='roost')                      # Kestrel
        for i in range(30):
            new('shopkeeper', shop=i)
        for i in range(14):
            new('tavernkeeper', tavern=i, role='keeper')
        for i in range(14):
            new('tavernkeeper', tavern=i, role='hand')
        for i in range(10):
            new('clerk', mode='clerk')
        new('clerk', mode='bram')                        # Bram
        for i in range(2):
            new('archivist')
        for i in range(4):
            new('healer', terrace=i // 2)
        for i in range(4):
            new('miller', mill=i // 2)
        for i in range(36):
            new('child')
        # residents are the crowd: about half go to the market, a third are neighbours, the rest elders
        for i in range(140):
            kind = 'market' if i < 60 else ('neighbour' if i < 110 else 'elder')
            new('resident', kind=kind, rk=i if i < 60 else (i - 60 if i < 110 else i - 110))
        assert len(cits) == TOTAL, (len(cits), TOTAL)
        for cit in cits:
            cit['speed'] = round(cit['speed'], 2)
        for cit in cits:
            if cit['named'] == 'bram':
                cit['scale'] = 1.04           # (tall)
            elif cit['named'] == 'kestrel':
                cit['scale'] = 0.97           # (wiry)

    # -- places a citizen is tied to ----------------------------------------------------------------------------------------
    def tavern_rank(self, H):
        """Tavern indices nearest first for a home (place id or ('h', plot))."""
        key = H if isinstance(H, tuple) else ('p', H)
        r = self._tav_rank.get(key)
        if r is None:
            B = self.B
            r = self._tav_rank[key] = sorted(range(len(B.taverns)), key=lambda t: (B.wd(B.taverns[t]['out'], H), t))
        return r

    def tav(self, H, which=0):
        return self.B.taverns[self.tavern_rank(H)[which]]['out']

    def segments(self, cit, H, err=None):
        """The citizen's day before jitter and cap resolution: a list of segments sorted by start. `err` is the errand
        route to use (None leaves the errand segments as placeholders)."""
        B = self.B
        job = cit['job']
        T = self.tav(H)
        E = err
        sg = []
        if job == 'lamplighter':
            r = B.lamp_clusters[cit['cluster']]['route']
            # the light round starts at 11200 unless the tour is too long to end before 13600 (150 ticks to spare for jitter)
            light = min(11200, int((13600 - 150 - B.route_len(r) / (RUN_PACE * cit['speed']) - 40 * len(B.routes[r]['places'])) // 50) * 50)
            sg = [S(2300, 'home', H), S(7000, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(9000, 'home', H),
                  S(light, 'round', r, -2, 40, 2, fix=True), S(14800, 'stand', T, tag='tav'), S(16800, 'home', H),
                  S(23100, 'round', r, -2, 30, 0, fix=True)]
        elif job == 'bellringer':
            bell = B.bells[cit['bell']]['place']
            sg = [S(7800, 'stand', bell), S(16300, 'home', H), S(23500, 'stand', bell)]
            if cit['bell'] == 5:
                sg.insert(0, S(7000, 'wander', E, -1, ERRAND_DWELL, 0, 'err'))
        elif job == 'porter':
            P = B.quay_pickups[cit['pickup']]
            D = B.drops_routes[cit['pickup']]
            sg = [S(0, 'home', H), S(600, 'carry', P, D, 60), S(5000, 'wander', B.dock_route, -1, 700, 0), S(6500, 'carry', P, D, 60),
                  S(9800, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(11800, 'stand', T, tag='tav'), S(14000, 'home', H)]
        elif job == 'stallkeeper':
            S_ = B.stalls[cit['stall']]['keeper']
            if cit['role'] == 0:
                sg = [S(0, 'home', H), S(600, 'stand', S_), S(12100, 'stand', T, tag='tav'), S(14500, 'home', H)]
            else:
                sg = [S(0, 'home', H), S(800, 'stand', S_), S(5600, 'wander', E, -1, ERRAND_DWELL, 0, 'err'),
                      S(6600, 'stand', S_), S(11400, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(12400, 'home', H)]
        elif job == 'baker':
            b = B.bakeries[cit['bakery']]
            d = b['door']
            spot = b['front'] if cit['rank'] == 0 else d
            sg = [S(1000, 'carry', d, b['drops_route'], 80), S(2400, 'stand' if cit['rank'] == 0 else 'inside', spot),
                  S(6200, 'home', H), S(10000, 'stand', T, tag='tav'), S(12000, 'home', H), S(21400, 'inside', d)]
        elif job == 'smith':
            sm = B.smithies[cit['smithy']]
            first = ('stand', sm['anvil']) if cit['rank'] == 0 else ('inside', sm['door'])
            second = ('inside', sm['door']) if cit['rank'] == 0 else ('stand', sm['anvil'])
            sg = [S(0, 'home', H), S(1000, first[0], first[1]), S(5600, 'wander', E, -1, ERRAND_DWELL, 0, 'err'),
                  S(6300, second[0], second[1]), S(10600, 'stand', T, tag='tav'), S(13800, 'home', H)]
        elif job == 'watch':
            m = cit['mode']
            if m == 'gate':
                sg = [S(0, 'home', H), S(600, 'stand', B.post_places[cit['post']]), S(12400, 'stand', T, tag='tav'), S(14200, 'home', H)]
            elif m == 'patrol':
                R = cit['route']
                sg = [S(0, 'home', H), S(600, 'round', R, -2, 160, 1), S(12400, 'stand', T, tag='tav'), S(14200, 'home', H)]
            elif m == 'scout':
                sg = [S(0, 'home', H), S(600, 'round', B.scout_route, -2, 200, 1), S(11000, 'stand', T, tag='tav'), S(13000, 'home', H)]
            else:
                R = cit['route']
                sg = [S(0, 'round', R, -3, 160, 1), S(1000, 'home', H), S(9000, 'wander', E, -1, ERRAND_DWELL, 0, 'err'),
                      S(10800, 'home', H), S(12500, 'round', R, -2, 160, 1)]
        elif job == 'crier':
            K = B.crier_routes[cit['route']]
            sg = [S(0, 'home', H), S(1200, 'round', K, 0, 1200, 1), S(6000, 'wander', E, -1, ERRAND_DWELL, 0, 'err'),
                  S(7000, 'round', K, 0, 1200, 1), S(11000, 'stand', T, tag='tav'), S(13500, 'home', H)]
        elif job == 'boatman':
            # (M4 B3b) his own berth: lines, bailing, loading and fending off there (the client cycles the work), the nets
            # mended in the slack of the morning and the afternoon when one is near
            M = B.moorings[cit['mooring']]['place']
            mp = B.places[M]
            nets = sorted((q for q in B.net_places if q not in B.nets_taken),
                          key=lambda q: (math.hypot(B.places[q]['x'] - mp['x'], B.places[q]['z'] - mp['z']), q))
            N_ = nets[0] if nets and math.hypot(B.places[nets[0]]['x'] - mp['x'], B.places[nets[0]]['z'] - mp['z']) < 70 else None
            if N_ is not None:
                B.nets_taken.add(N_)  # a net each: one mender at a time
            if N_ is not None:
                sg = [S(0, 'home', H), S(800, 'stand', M), S(3000, 'stand', N_), S(4400, 'stand', M),
                      S(5600, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(6400, 'stand', M), S(8600, 'stand', N_), S(9800, 'stand', M),
                      S(10800, 'stand', T, tag='tav'), S(13500, 'home', H)]
            else:
                sg = [S(0, 'home', H), S(800, 'stand', M), S(5600, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(6400, 'stand', M),
                      S(10800, 'stand', T, tag='tav'), S(13500, 'home', H)]
        elif job == 'washer':
            wp = B.washplaces[cit['washplace']]
            role = cit['role']
            lines = [ln for ln in B.lines if ln['washplace'] == cit['washplace']]
            if role == 'extra':
                w0 = wp['places'][cit['slot']]
                ln = lines[0]['place']
                sg = [S(0, 'home', H), S(600, 'stand', ln), S(2800, 'stand', w0), S(3600, 'stand', ln), S(8200, 'stand', w0),
                      S(9000, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(10500, 'home', H)]
            else:
                W = wp['places'][cit['slot']]
                if role == 'hanger':
                    ln = lines[cit['line']]['place']
                    sg = [S(0, 'home', H), S(600, 'stand', W), S(2800, 'stand', ln), S(3600, 'stand', W), S(8200, 'stand', ln),
                          S(9000, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(10500, 'home', H)]
                else:
                    sg = [S(0, 'home', H), S(600, 'stand', W), S(9000, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(10500, 'home', H)]
        elif job == 'mason':
            Y = B.yard_places[cit['yard']]
            sg = [S(0, 'home', H), S(1000, 'stand', Y), S(5600, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(6400, 'stand', Y),
                  S(10600, 'stand', T, tag='tav'), S(13600, 'home', H)]
        elif job == 'runner' and cit['mode'] == 'roost':
            # Kestrel lives and works at the Roost: by day at her post by its street door, inside it otherwise
            sg = [S(600, 'stand', B.roost_stand), S(12500, 'home', H), S(22800, 'inside', H)]
        elif job == 'runner':
            C_ = B.courier_routes[cit['route']]
            sg = [S(0, 'home', H), S(1000, 'round', C_, -2, 60, 1), S(5800, 'stand', T, tag='tav'),
                  S(6600, 'round', C_, -2, 60, 1), S(10400, 'home', H)]
        elif job == 'shopkeeper':
            sh = B.shops[cit['shop']]
            sg = [S(0, 'home', H), S(1000, 'stand', sh['stand']), S(2600, 'inside', sh['door']), S(5000, 'stand', sh['stand']),
                  S(7000, 'inside', sh['door']), S(10000, 'stand', sh['stand']), S(12000, 'home', H)]
        elif job == 'tavernkeeper' and cit['role'] == 'hand':
            # a hand serves the lane outside: at the tavern's table place from 11500 to 17000
            t = B.taverns[cit['tavern']]
            sg = [S(0, 'home', H), S(10600, 'inside', t['door']), S(11500, 'stand', t['out']), S(17000, 'inside', t['door']),
                  S(18400, 'home', H)]
        elif job == 'tavernkeeper':
            t = B.taverns[cit['tavern']]
            sg = [S(0, 'home', H), S(4000, 'inside', t['door']), S(9500, 'stand', t['out']), S(11500, 'inside', t['door']), S(20000, 'home', H)]
        elif job == 'clerk' and cit['mode'] == 'bram':
            # Bram works inside the Association, with a spell at the forecourt notice board
            A_ = B.landmark('association')
            sg = [S(0, 'home', H), S(1300, 'inside', A_), S(4200, 'stand', B.assoc_notice), S(5400, 'inside', A_), S(10800, 'home', H)]
        elif job in ('clerk', 'archivist', 'healer'):
            A_ = B.landmark('association')
            terr = B.terrace_places
            if job == 'archivist' and cit['named'] == 'teodor':
                TR = terr[0]
                sg = [S(0, 'home', H), S(1500, 'stand', TR), S(3200, 'inside', A_), S(8800, 'stand', TR), S(10500, 'stand', T, tag='tav'), S(13000, 'home', H)]
            elif job == 'healer':
                TR = terr[cit['terrace']]
                sh = cit.get('shift', 0)
                sg = [S(0, 'home', H), S(1000 + sh, 'inside', A_), S(4000 + sh, 'stand', TR), S(5000 + sh, 'inside', A_), S(11000 + sh, 'home', H)]
            else:
                sg = [S(0, 'home', H), S(1500, 'inside', A_), S(5600, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(6600, 'inside', A_), S(10500, 'home', H)]
        elif job == 'miller':
            M = B.mill_places[cit['mill']]
            sg = [S(0, 'home', H), S(800, 'stand', M), S(6000, 'wander', E, -1, ERRAND_DWELL, 0, 'err'), S(6800, 'stand', M),
                  S(10800, 'stand', T, tag='tav'), S(13000, 'home', H)]
        elif job == 'child':
            PL = cit.get('play')
            sg = [S(0, 'home', H), S(1600, 'wander', PL, -1, 400, 0, 'play'), S(5600, 'home', H), S(6600, 'wander', PL, -1, 400, 0, 'play'),
                  S(11300, 'home', H)]
        elif job == 'resident':
            k = cit['rk']
            if cit['kind'] == 'market':
                # home -> the stall fronts, benches and fountains of the square -> a sit at noon -> errands -> a tavern -> home
                far = min(B.wd(p, H) for p in B.routes[cit['mk']]['places']) > 85      # (a home far from the square: straight home after noon)
                sg = [S(0, 'home', H), S(1500 + (k % 4) * 300, 'wander', cit['mk'], -1, 350, 0, 'mk'),
                      S(5000 + (k % 3) * 200, 'stand', cit['msit'], tag='msit'),
                      S(7600 + (k % 3) * 150, 'home', H) if far else S(7600 + (k % 3) * 150, 'wander', E, -1, ERRAND_DWELL, 0, 'err')]
                if not far:
                    sg.append(S(10300, 'home', H))
                sg += [S(12000 + (k % 3) * 300, 'stand', T, tag='tav'), S(15500 - (k % 2) * 300, 'home', H)]
            elif cit['kind'] == 'neighbour':
                # a well or bench near home mornings and at dusk, the market at midday (when it is near enough), some evenings a tavern
                L = cit['loc']
                trip = min(B.wd(p, L) for p in B.routes[cit['mkr']]['places']) <= 150 and not cit.get('walks')
                mid = S(4600 + (k % 3) * 200, 'wander', cit['mkr'], -1, 350, 0, 'mk') if trip else \
                    S(4600, 'wander', cit['stroll'], -1, 600, 0, 'stroll')
                sg = [S(0, 'home', H), S(2200 + (k % 3) * 200, 'stand', L, tag='loc'), mid, S(7900, 'home', H),
                      S(10500 + (k % 3) * 200, 'stand', L, tag='loc')]
                if k % 2 == 0:
                    sg += [S(12700, 'stand', T, tag='tav'), S(15000, 'home', H)]
                else:
                    sg += [S(12600, 'home', H)]
            else:
                # long sits on a bench near home or in the square, a short walk, home early
                L = cit['loc']
                sg = [S(0, 'home', H), S(1800 + (k % 4) * 150, 'stand', L, tag='loc'), S(5300, 'wander', cit['stroll'], -1, 700, 0, 'stroll'),
                      S(7000, 'home', H), S(9800, 'stand', L, tag='loc'), S(11800, 'home', H)]
        sg.sort(key=lambda q: q['s'])
        # a leading `home` that repeats the day's last segment is the same stay (the day wraps): merge it
        if len(sg) > 1 and sg[0]['a'] == 'home' and sg[0]['s'] == 0 and sg[-1]['a'] == 'home' and sg[-1]['t'] == sg[0]['t']:
            sg.pop(0)
        return sg

    # -- feasibility -------------------------------------------------------------------------------------------------------
    def ends(self, sg):
        B = self.B
        a = sg['a']
        if a in ('home', 'inside', 'stand'):
            return [sg['t']]
        if a == 'round' or a == 'wander':
            return None if sg['t'] is None else B.routes[sg['t']]['places']
        if a == 'carry':
            return [sg['t']] + B.routes[sg['alt']]['places']
        raise ValueError(a)

    def starts(self, sg):
        B = self.B
        a = sg['a']
        if a in ('home', 'inside', 'stand', 'carry'):
            return [sg['t']]
        if a == 'round':
            pl = B.routes[sg['t']]['places']
            if sg['alt'] in (-2, -3):
                return [pl[0], pl[-1]] if B.routes[sg['t']]['kind'] == 'lamps' else list(pl)
            return [pl[sg['alt']]]
        if a == 'wander':
            return None if sg['t'] is None else B.routes[sg['t']]['places']
        raise ValueError(a)

    def xz(self, p):
        if isinstance(p, tuple):
            pl = self.c.plots[p[1]]
            return pl['doorX'] + 0.5, pl['doorZ'] + 0.5
        return self.B.places[p]['x'], self.B.places[p]['z']

    def near_place(self, e, places):
        """The place of a route nearest (straight line) to where a citizen is: a wander's first stop."""
        x, z = self.xz(e)
        return min(places, key=lambda q: (math.hypot(self.B.places[q]['x'] - x, self.B.places[q]['z'] - z), q))

    def walk_ticks(self, ends, nxt, speed):
        """Ticks to stroll from the farthest of `ends` to the next segment's start (the nearest place for a wander, the
        nearest stop for a round whose start is still free). None while an errand is a placeholder."""
        B = self.B
        s_ = self.starts(nxt)
        if ends is None or s_ is None:
            return None
        if nxt['a'] == 'wander':
            w = max(B.wd(e, self.near_place(e, s_)) for e in ends)
        elif nxt['a'] == 'round' and nxt['alt'] in (-2, -3):
            w = min(max(B.wd(x, y) for x in ends) for y in s_)
        else:
            w = max(B.wd(x, y) for x in ends for y in s_)
        return w / (STROLL * speed)

    def wander_ticks(self, ends, wand, nxt, speed):
        """The walk in to a wander's first stop plus the walk on from it to the next start, in ticks, worst over where the
        citizen starts from (None while the route is a placeholder)."""
        B = self.B
        places = self.starts(wand)
        s_ = self.starts(nxt)
        if ends is None or places is None or s_ is None:
            return None
        worst = 0.0
        for e in ends:
            p = self.near_place(e, places)
            if nxt['a'] == 'round' and nxt['alt'] in (-2, -3):
                out = min(B.wd(p, y) for y in s_)
            else:
                out = max(B.wd(p, y) for y in s_)
            worst = max(worst, B.wd(e, p) + out)
        return worst / (STROLL * speed)

    def margins(self, segs, speed, starts=None, kinds=False):
        """[(margin_in_ticks, index)] over the day, errand placeholders skipped. Per pair of consecutive segments (index =
        the earlier): the walk from the earlier's last place to the next start must fit the earlier's duration (less 100,
        except after a round/carry/wander, which can be anywhere on its route: then the walk is from the farthest stop).
        Per run (round/carry/wander, then a stay at a place, then whatever follows; index = the stay): the farthest stop's
        walk to the stay, 200 ticks standing and the walk on must fit the stay's duration. starts: the (jittered) start
        times to use, default the segments' own."""
        n = len(segs)
        out = []
        st = starts if starts is not None else [q['s'] for q in segs]
        for i in range(n):
            a, b = segs[i], segs[(i + 1) % n]
            walk = self.walk_ticks(self.ends(a), b, speed)
            if walk is None:
                if a['a'] == 'wander' and b['a'] in ('home', 'inside', 'stand') and n > 2:
                    # an errand still to be chosen: at the very least the stay after it must fit its own walk on
                    w2 = self.walk_ticks(self.ends(b), segs[(i + 2) % n], speed)
                    if w2 is not None:
                        m2 = ((st[(i + 2) % n] - st[(i + 1) % n]) % DAY or DAY) - w2 - 200 - 900   # 900: a nearby errand's return
                        out.append((m2, (i + 1) % n, 'stay') if kinds else (m2, (i + 1) % n))
                continue
            gap = (st[(i + 1) % n] - st[i]) % DAY
            allow = gap - (0 if a['a'] in ('round', 'carry', 'wander') else 100)
            out.append((allow - walk, i, 'walk') if kinds else (allow - walk, i))
            if b['a'] == 'wander' and n > 2:
                # into the first stop, 100 ticks there and on to the next target must fit the wander
                ww = self.wander_ticks(self.ends(a), b, segs[(i + 2) % n], speed)
                if ww is not None:
                    m2 = ((st[(i + 2) % n] - st[(i + 1) % n]) % DAY or DAY) - ww - 100
                    out.append((m2, (i + 1) % n, 'stay') if kinds else (m2, (i + 1) % n))
            elif a['a'] in ('round', 'carry', 'wander') and b['a'] in ('home', 'inside', 'stand') and n > 2:
                c_ = segs[(i + 2) % n]
                w2 = self.walk_ticks(self.ends(b), c_, speed)
                if w2 is not None:
                    dur = (st[(i + 2) % n] - st[(i + 1) % n]) % DAY or DAY
                    m2 = dur - w2 - walk - 200
                    out.append((m2, (i + 1) % n, 'stay') if kinds else (m2, (i + 1) % n))
        return out

    def deficit(self, segs, speed):
        """Total ticks by which the day's walks overrun their gaps (errand placeholders skipped)."""
        return sum(-m for m, _ in self.margins(segs, speed) if m < 0)

    def push(self, segs, speed, limit=1500, slack=0):
        """Moves later starts back (in 50s, taking later starts along) until every walk fits its gap (with `slack` ticks to
        spare); returns the segments (copied) or None. Stands for people who live farther away setting out earlier or
        arriving a little later."""
        segs = [dict(q) for q in segs]
        n = len(segs)
        moved = 0
        seen = set()
        for _ in range(300):
            mg = [t for t in self.margins(segs, speed, kinds=True) if t[0] < slack]
            if not mg:
                return segs
            m, i, kind = min(mg, key=lambda t: (t[1], t[2]))
            shift = int(math.ceil((slack - m) / 50.0)) * 50
            # (an earlier start for one stay can undo the later start another needed: when a state comes round again,
            # push the next start instead)
            state = tuple(q['s'] for q in segs)
            again = state in seen
            seen.add(state)
            if kind == 'stay' and not again:
                # a stay too short for the walks round it: first try setting out for it earlier (the route before it has
                # time to spare), else the segment after it starts later
                walk_m = next((t[0] for t in self.margins(segs, speed, kinds=True) if t[1] == (i - 1) % n and t[2] == 'walk'), 0)
                prev_s = segs[(i - 1) % n]['s']
                room = min(walk_m, segs[i]['s'] - prev_s - 200) if i > 0 else 0
                early = int(min(shift, room) // 50) * 50
                if early >= 50:
                    segs[i]['s'] -= early
                    moved += early
                    if moved > limit:
                        return None
                    continue
            j = (i + 1) % n
            if segs[j].get('fix'):
                # a fixed start: the stay before it begins earlier instead, if the day allows
                if i > 0 and segs[i]['s'] - shift >= segs[i - 1]['s'] + 200 and kind == 'walk':
                    segs[i]['s'] -= shift
                    moved += shift
                    continue
                return None
            moved += shift
            if moved > limit:
                return None
            segs[j]['s'] += shift
            k = j
            while True:
                nk = (k + 1) % n
                if nk == 0 or nk == j:
                    break
                if segs[nk]['s'] < segs[k]['s'] + 200:
                    segs[nk]['s'] = segs[k]['s'] + 200
                    k = nk
                else:
                    break
            if segs[n - 1]['s'] > DAY - 100 or (j == 0 and n > 1 and segs[0]['s'] > segs[1]['s'] - 200):
                return None
        return None

    # -- work assignments that need the routes ----------------------------------------------------------------------------------
    def assign_work(self):
        B = self.B
        r = rng(0xA551)
        day_routes = [10, 11, 1, 3, 5, 7, 9]
        for cit in self.cits:
            if cit['job'] == 'watch':
                if cit['mode'] == 'patrol':
                    cit['route'] = B.patrol_routes[day_routes[cit['slot'] % len(day_routes)]]
                elif cit['mode'] == 'night':
                    # (M4 Walls) the first four take the rounds of the wall walk, the rest the streets' loops
                    s = cit['slot']
                    cit['route'] = B.wall_routes[s] if s < len(B.wall_routes) else B.patrol_routes[(s - len(B.wall_routes)) % len(B.patrol_routes)]
            if cit['job'] == 'healer':
                cit['shift'] = 0 if cit['named'] == 'sun' else r.randint(-6, 6) * 100
        # residents: market-goers get one of the market routes, neighbours and elders a bench or well on a stroll route
        P = B.places
        mk = B.market_routes
        msits = [p for p in B.square_places if P[p]['kind'] == 'sit']      # (the bowl's benches, fountain rims and steps)
        self.msits = msits
        in_route = {p: rid for rid in B.stroll_routes for p in B.routes[rid]['places']}
        # the neighbours and elders (not those of the square) take their bench or well from slots: every stroll route gets one
        # first (so that every bench, well and board is on a walk), the rest by district in proportion to the square root of its
        # houses (a quiet district with few benches still has its people), the places of a district by turns
        n_stroll = sum(1 for x in self.cits if x['job'] == 'resident' and x['kind'] != 'market' and not (x['kind'] == 'elder' and x['rk'] % 3 == 0))
        dist_of = lambda p: P[p]['district']      # noqa: E731
        houses = Counter(self.c.plots[pl]['district'] for pl in self.c.plots if self.c.plots[pl]['use'] == 'house')
        did = {d['key']: d['id'] for d in self.c.P['districts']}
        weight = {did[k]: math.sqrt(v) for k, v in houses.items()}
        by_d = defaultdict(list)
        for p in sorted(in_route):
            by_d[dist_of(p)].append(p)
        slots = [B.routes[rid]['places'][0] for rid in B.stroll_routes]
        left = n_stroll - len(slots)
        have = Counter(dist_of(p) for p in slots)
        tot = sum(weight[d] for d in by_d)
        want = {d: weight[d] / tot * n_stroll for d in by_d}
        for _ in range(left):
            d = max(by_d, key=lambda d: (want[d] - have[d], -d))
            places = sorted(by_d[d], key=lambda p: (slots.count(p), p))
            slots.append(places[0])
            have[d] += 1
        r.shuffle(slots)
        si = 0
        msit_i = 0
        walked = set()
        for q in (x for x in self.cits if x['job'] == 'resident'):
            k = q['rk']
            if q['kind'] == 'market':
                q['mk'] = mk[k % len(mk)]
                cx = sum(P[p]['x'] for p in B.routes[q['mk']]['places']) / len(B.routes[q['mk']]['places'])
                cz = sum(P[p]['z'] for p in B.routes[q['mk']]['places']) / len(B.routes[q['mk']]['places'])
                q['msit'] = min(msits, key=lambda p: (math.hypot(P[p]['x'] - cx, P[p]['z'] - cz), p))
            elif q['kind'] == 'elder' and k % 3 == 0:
                # an elder of the square: a bench of the market and its route
                q['loc'] = msits[(k // 3 * 5) % len(msits)]
                q['stroll'] = mk[(k // 3) % len(mk)]
                q['sq'] = True
            else:
                q['loc'] = slots[si]
                q['stroll'] = in_route[q['loc']]
                si += 1
                # the first on each stroll route walks it every day (a market trip would leave its benches unwalked)
                q['walks'] = q['stroll'] not in walked
                walked.add(q['stroll'])
            if q['kind'] == 'neighbour':
                q['mkr'] = mk[k % len(mk)]
        self.stroll_users = si

    def anchors(self, cit):
        """Places the citizen's day starts from or comes back to (the first targets): used to pre-filter homes."""
        B = self.B
        job = cit['job']
        if job == 'lamplighter':
            return B.routes[B.lamp_clusters[cit['cluster']]['route']]['places']
        if job == 'bellringer':
            return [B.bells[cit['bell']]['place']]
        if job == 'porter':
            return [B.quay_pickups[cit['pickup']]]
        if job == 'boatman':
            return [B.moorings[cit['mooring']]['place']]
        if job == 'stallkeeper':
            return [B.stalls[cit['stall']]['keeper']]
        if job == 'baker':
            return [B.bakeries[cit['bakery']]['door']]
        if job == 'smith':
            return [B.smithies[cit['smithy']]['anvil']]
        if job == 'watch':
            m = cit['mode']
            if m == 'gate':
                return [B.post_places[cit['post']]]
            if m == 'scout':
                return B.routes[B.scout_route]['places']
            return B.routes[cit['route']]['places']
        if job == 'crier':
            return [B.routes[B.crier_routes[cit['route']]]['places'][0]]
        if job == 'washer':
            return [B.washplaces[cit['washplace']]['places'][0]]
        if job == 'mason':
            return [B.yard_places[cit['yard']]]
        if job == 'runner':
            return B.routes[B.courier_routes[cit['route']]]['places'] if cit['mode'] == 'courier' else [B.roost_stand]
        if job == 'shopkeeper':
            return [B.shops[cit['shop']]['stand']]
        if job == 'tavernkeeper':
            return [B.taverns[cit['tavern']]['door']]
        if job in ('clerk', 'archivist', 'healer'):
            return [B.landmark('association')]
        if job == 'miller':
            return [B.mill_places[cit['mill']]]
        if job == 'resident':
            return B.routes[cit['mk']]['places'] if cit['kind'] == 'market' else [cit['loc']]
        return []

    def commute(self, cit, plot):
        an = self.anchors(cit)
        if not an:
            return 0.0
        return min(self.B.wd(a, ('h', plot)) for a in an)

    def allowed_homes(self, cit, pool, extra=300, pre=200.0, commute_cap=125.0):
        """Plots at which the citizen's day fits after pushing starts back by at most (the least any home needs) + extra
        ticks: [(deficit, commute, plot)] best first. Errand placeholders are skipped."""
        sp = cit['speed']
        rows = []
        lamp_pl = self.B.routes[self.B.lamp_clusters[cit['cluster']]['route']]['places'] if cit['job'] == 'lamplighter' else None
        for plot in pool:
            com = self.commute(cit, plot)
            if com > pre:
                continue
            if lamp_pl is not None:
                # the tour starts at the lamp nearest home, which must be one end of the route
                dd = [self.B.wd(p, ('h', plot)) for p in lamp_pl]
                if min(dd[0], dd[-1]) > min(dd) + 0.05:
                    continue
            sg = self.segments(cit, ('h', plot))
            if lamp_pl is not None and any(m < 0 and sg[(i + 1) % len(sg)].get('fix') for m, i, _ in self.margins(sg, sp, kinds=True)):
                continue
            rows.append((self.deficit(sg, sp), com, plot))
        if not rows:
            return []
        capped = [r for r in rows if r[1] <= commute_cap]
        if len(capped) >= 8:
            rows = capped
        base = min(r[0] for r in rows)
        out = [r for r in rows if r[0] <= base + extra]
        out.sort()
        return out

    # -- households -----------------------------------------------------------------------------------------------------------
    def household_sizes(self, total, r):
        """Sizes (1-5) of the adult households, summing to `total` (M3's mix: mostly 2s and 3s, 91 households per 220 adults)."""
        count = max(1, round(total * 91 / 220))
        base = []
        for size, w in ((1, 17), (2, 34), (3, 28), (4, 9), (5, 3)):
            base += [size] * round(w * count / 91)
        while len(base) < count:
            base.append(2)
        base = base[:count]
        sizes = list(base)
        r.shuffle(sizes)
        while sum(sizes) != total:
            i = r.randrange(count)
            if sum(sizes) < total and sizes[i] < 5:
                sizes[i] += 1
            elif sum(sizes) > total and sizes[i] > 1:
                sizes[i] -= 1
        return sizes

    def form_households(self):
        B = self.B
        c = B.c
        r = rng(0x40D5E)
        cits = self.cits
        pool = [p for p in B.house_candidates() if p not in B.reserved]
        self.pool = pool
        for cit in cits:
            if cit['job'] == 'child' or cit['named'] == 'kestrel':
                continue
            if cit['job'] == 'resident':
                # (the crowd: home near the bench or the market they go to, not a day's walk)
                cit['allowed'] = self.allowed_homes(cit, pool, pre=185.0 if cit['kind'] == 'market' else 80.0, commute_cap=999.0)
            elif cit['named'] == 'bram':
                # a house near the Terrace, the nearest to the Association
                terr = [pl for pl in pool if self.c.plots[pl]['district'] == 'terrace']
                terr.sort(key=lambda pl: (self.commute(cit, pl), pl))
                cit['allowed'] = [(0, self.commute(cit, pl), pl) for pl in terr[:12]]
            else:
                cit['allowed'] = self.allowed_homes(cit, pool)
            cit['aset'] = {pl for _, _, pl in cit['allowed']}
        used = set()
        hh = []   # {'plot', 'members': [ids], 'size': target}

        dload = Counter()      # people housed per district so far; the crowd's homes are spread by it
        in_district = Counter(c.plots[p]['district'] for p in pool)

        def open_hh(plot, members):
            used.add(plot)
            hh.append({'plot': plot, 'members': list(members)})
            if plot >= 0:
                dload[c.plots[plot]['district']] += len(members)
        # the named: a home each, nearest their work (Kestrel's is the Roost itself)
        for cit in cits:
            if cit['named'] == 'kestrel':
                open_hh(-1, [cit['id']])
            elif cit['named']:
                al = [t for t in cit['allowed'] if t[2] not in used]
                open_hh(al[0][2], [cit['id']])
        adults = sorted((q for q in cits if q['job'] != 'child' and not q['named']), key=lambda q: (len(q['allowed']), q['id']))
        sizes = self.household_sizes(len(adults), r)
        # sizes are for adults only here; children join afterwards, so the final sizes come out a little larger
        r.shuffle(sizes)
        unplaced = list(adults)
        while unplaced:
            c0 = unplaced.pop(0)
            s_ = sizes.pop() if sizes else r.choice((1, 2, 2, 3))
            homes = [t for t in c0['allowed'] if t[2] not in used]
            if not homes:
                homes = [t for t in self.allowed_homes(c0, [p for p in pool if p not in used], extra=2000, pre=300, commute_cap=250)]
            assert homes, f'no home left for citizen {c0["id"]} ({c0["job"]})'
            others = unplaced
            best = None
            crowd = c0['job'] == 'resident'
            for k, (d, com, plot) in enumerate(homes[:200 if crowd else 60]):
                fill = sum(1 for u in others if plot in u['aset'])
                if crowd:
                    # (the crowd spreads over the districts: the fuller a district, the less it is wanted)
                    dk_ = c.plots[plot]['district']
                    key = (-min(fill, s_ - 1), d // 150, com * 0.25 + r.random() * 25 + 400.0 * dload[dk_] / (2.0 * in_district[dk_] + 1))
                else:
                    key = (-min(fill, s_ - 1), d // 150, com + r.random() * 12)
                if best is None or key < best[0]:
                    best = (key, plot)
            plot = best[1]
            members = [c0['id']]
            cand = [u for u in others if plot in u['aset']]
            cand.sort(key=lambda u: (len(u['allowed']), self.commute(u, plot), u['id']))
            for u in cand[:s_ - 1]:
                members.append(u['id'])
            take = set(members)
            unplaced = [u for u in unplaced if u['id'] not in take]
            open_hh(plot, members)
            hh[-1]['plan'] = s_
        self.hh = hh
        self.used_homes = used

    def build_play(self):
        """Choose three open places for the children's play routes where households are, make the play places (cap 4,
        open ground) and routes, and move the children in with households nearby (at most two a home)."""
        B = self.B
        c = B.c
        cits = self.cits
        by_id = {q['id']: q for q in cits}
        homes = [h for h in self.hh if len(h['members']) < 5 and any(by_id[m]['job'] != 'child' for m in h['members'])
                 and not any(by_id[m]['named'] for m in h['members'])]
        # candidate centres: open ground on a coarse grid
        cands = []
        for j in range(-215, 215, 5):
            for i in range(-215, 215, 5):
                x, z = i + 0.5, j + 0.5
                if c.kind_at(x, z) not in (SQUARE, PLAZA, TRAINING) or not B.spot_ok(x, z):
                    continue
                try:
                    node, cost = c.nearest_node(x, z)
                except ValueError:
                    continue
                row = c.row(node)
                near = {h['plot'] for h in homes if row[c.door_node[h['plot']]] + cost <= 55}
                if len(near) >= 3:
                    cands.append((x, z, near))
        chosen = []
        covered = set()
        for _ in range(5):
            best = None
            for x, z, near in cands:
                if any(math.hypot(x - a, z - b) < 60 for a, b, _ in chosen):
                    continue
                gain = len(near - covered)
                key = (-gain, x, z)
                if best is None or key < best[0]:
                    best = (key, x, z, near)
            assert best, 'no play site'
            chosen.append((best[1], best[2], best[3]))
            covered |= best[3]
        self.play_sites = [(x, z) for x, z, _ in chosen]
        self.play_routes = []
        P_ = B.places
        for (sx, sz, _) in chosen:
            places = self.play_cluster(sx, sz, 5)
            # (children play at the fountains and wells too: the nearest of them join the route)
            extra = sorted((p for p in B.fountain_places + B.well_places + B.step_places if math.hypot(P_[p]['x'] - sx, P_[p]['z'] - sz) <= 30),
                           key=lambda p: (math.hypot(P_[p]['x'] - sx, P_[p]['z'] - sz), p))[:3]
            self.play_routes.append(B.add_route('play', places + extra))
        kids = [q for q in cits if q['job'] == 'child']
        kid_count = Counter()
        site_rows = [(c.row(c.nearest_node(sx, sz)[0]), c.nearest_node(sx, sz)[1]) for sx, sz, _ in chosen]
        for kid in kids:
            best = None
            for h in homes:
                if len(h['members']) >= 5 or kid_count[h['plot']] >= 2:
                    continue
                for si, (row, cost) in enumerate(site_rows):
                    d = row[c.door_node[h['plot']]] + cost
                    if d > 55:
                        continue
                    load = sum(1 for k2 in kids if k2.get('site') == si)
                    key = (d + 14 * len(h['members']) + 10 * kid_count[h['plot']] + 9 * load, h['plot'], si)
                    if best is None or key < best[0]:
                        best = (key, h, si)
            assert best, 'no household for a child'
            h, si = best[1], best[2]
            h['members'].append(kid['id'])
            kid_count[h['plot']] += 1
            kid['play'] = self.play_routes[si]
            kid['site'] = si

    def play_cluster(self, sx, sz, n):
        """n play places (2x2 open cells) around a site, at least 6 apart."""
        B = self.B
        c = B.c
        got = []
        spots = []
        for rmax in (12, 18, 26):
            for d, x, z in B.cells_around(sx, sz, rmax, None):
                if len(got) == n:
                    break
                if c.kind_at(x, z) not in (SQUARE, PLAZA, TRAINING):
                    continue
                block = [(x + dx, z + dz) for dx in (0, 1) for dz in (0, 1)]
                if not all(B.spot_ok(a, b) and c.kind_at(a, b) in (SQUARE, PLAZA, TRAINING) for a, b in block):
                    continue
                if not B.reach_ok(c.nearest_node(x, z)[0], [(a, b, 0) for a, b in block]):
                    continue
                # open: no walls within two cells
                openish = all(c.walkable(x + dx, z + dz) and c.plot_at[c.cell(x + dx, z + dz)[1], c.cell(x + dx, z + dz)[0]] < 0
                              and not c.block[c.cell(x + dx, z + dz)[1], c.cell(x + dx, z + dz)[0]]
                              for dx in (-2, -1, 0, 1, 2, 3) for dz in (-2, -1, 0, 1, 2, 3))
                if not openish:
                    continue
                if any(math.hypot(x - a, z - b) < 6.0 for a, b in got):
                    continue
                if any(c.edge_dist(a, b) < 0.0 for a, b in block):
                    continue
                got.append((x, z))
                spots.append([(a, b, 0) for a, b in block])
            if len(got) == n:
                break
        assert len(got) == n, f'play cluster at {sx},{sz}: {len(got)}'
        return [B.add('play', sp, ref=-1) for sp in spots]

    # -- names, looks, groups ----------------------------------------------------------------------------------------------
    def finish_households(self):
        """Household ids, home door places, family names, given names, looks, groups."""
        B = self.B
        c = B.c
        cits = self.cits
        by_id = {q['id']: q for q in cits}
        hh = sorted(self.hh, key=lambda h: min(h['members']))
        for hid, h in enumerate(hh):
            h['id'] = hid
            h['members'].sort()
            h['home'] = B.landmark('roost') if h['plot'] < 0 else B.door(h['plot'])
            for m in h['members']:
                by_id[m]['household'] = hid
                by_id[m]['home'] = h['home']
        self.hh = hh
        r = rng(0xFA417)
        named_h = {h['id'] for h in hh if any(by_id[m]['named'] for m in h['members'])}
        free = [h for h in hh if h['id'] not in named_h]
        # ten related pairs: households whose homes are close
        pairs = []
        order = list(free)
        r.shuffle(order)
        used_h = set()
        for h in order:
            if len(pairs) == 10:
                break
            if h['id'] in used_h:
                continue
            near = [g for g in free if g['id'] not in used_h and g['id'] != h['id']
                    and math.hypot(B.places[g['home']]['x'] - B.places[h['home']]['x'], B.places[g['home']]['z'] - B.places[h['home']]['z']) < 45]
            if near:
                g = r.choice(near)
                pairs.append((h, g))
                used_h |= {h['id'], g['id']}
        fams = list(dict.fromkeys(list(FAMILIES) + list(FAMILIES_EXTRA) + FAMILIES_MORE))
        r.shuffle(fams)
        fi = 0
        for h in hh:
            h['family'] = ''
        pair_of = {}
        for a, b in pairs:
            pair_of[a['id']] = b['id']
        done = {}
        for h in free:
            if h['id'] in done:
                continue
            h['family'] = fams[fi]
            fi += 1
            done[h['id']] = h['family']
            if h['id'] in pair_of:
                hb = next(g for g in hh if g['id'] == pair_of[h['id']])
                hb['family'] = h['family']
                done[hb['id']] = h['family']
        assert fi <= len(fams)
        # given names
        taken = set()
        r2 = rng(0x91FE)
        for h in hh:
            fam = h['family']
            adult_names = set()
            mem = sorted(h['members'], key=lambda m: (by_id[m]['job'] == 'child', m))
            for m in mem:
                cit = by_id[m]
                cit['family'] = fam
                if cit['named']:
                    cit['given'] = NAMED_GIVEN[cit['named']]
                    cit['family'] = ''
                    continue
                listA, listB = NAMES_A, NAMES_B
                lst = listA if cit['build'] == 0 else listB
                if r2.random() < 0.10:
                    lst = listB if lst is listA else listA
                for _ in range(500):
                    g = r2.choice(lst)
                    if (g, fam) in taken or g in adult_names and cit['job'] == 'child':
                        continue
                    if cit['job'] == 'child' and g in adult_names:
                        continue
                    break
                cit['given'] = g
                taken.add((g, fam))
                if cit['job'] != 'child':
                    adult_names.add(g)
        # looks: a household shares a base skin tone and hair colour, but two people of the same job in different households
        # differ in at least `need` of tone, hair style, hair colour, outfit and accent (the skins are checked pixel by pixel)
        seen = defaultdict(list)       # job -> [(household, look)]
        combos = {}                    # job -> the (outfit, accent) pairs not yet worn, so that a job's clothes are used evenly
        for h in hh:
            rr = rng(0x10000 + h['id'])
            base_tone = rr.randrange(6)
            base_hair = rr.randrange(6)
            for m in h['members']:
                cit = by_id[m]
                need = 2 if cit['job'] == 'resident' else 3
                pool_ = combos.setdefault(cit['job'], [])
                if not pool_:
                    pool_.extend((o_, a_) for o_ in range(4) for a_ in range(4))
                    rng(0x5EED + J[cit['job']]).shuffle(pool_)
                outfit, accent = pool_.pop()
                best = None
                for attempt in range(60):
                    rm = rng(0x20000 + cit['id'] + 7919 * attempt)
                    tone = base_tone if rm.random() < 0.7 else rm.randrange(6)
                    if cit['age'] == 3:
                        hair = rm.choice((6, 7)) if rm.random() < 0.85 else rm.randrange(6)
                    elif cit['age'] == 0:
                        hair = base_hair if rm.random() < 0.5 else rm.randrange(6)
                    else:
                        hair = base_hair if rm.random() < 0.5 else rm.randrange(6)
                    look = [tone, rm.randrange(12), hair, outfit, accent, rm.randrange(8)]
                    near = min((sum(a != b for a, b in zip(look[:5], o[:5])) for hid, o in seen[cit['job']] if hid != h['id']), default=9)
                    if best is None or near > best[0]:
                        best = (near, look)
                    if near >= need:
                        break
                cit['look'] = best[1]
                seen[cit['job']].append((h['id'], best[1]))

    def make_groups(self):
        B = self.B
        cits = self.cits
        by_job = defaultdict(list)
        for q in cits:
            by_job[q['job']].append(q)
        groups = []

        def add(kind, members):
            groups.append({'id': len(groups), 'kind': kind, 'members': sorted(q['id'] for q in members)})

        def home_xy(q):
            pl = B.places[q['home']]
            return pl['x'], pl['z']

        def chunks(lst, size_target):
            lst = sorted(lst, key=lambda q: math.atan2(home_xy(q)[1], home_xy(q)[0]))
            n = max(1, round(len(lst) / size_target))
            per = [len(lst) // n + (1 if i < len(lst) % n else 0) for i in range(n)]
            out, i = [], 0
            for k in per:
                out.append(lst[i:i + k])
                i += k
            return out
        for k in range(len(B.stalls)):
            add('stall', [q for q in by_job['stallkeeper'] if q['stall'] == k])
        for k in range(4):
            add('smithy', [q for q in by_job['smith'] if q['smithy'] == k])
        for k in range(5):
            add('bakery', [q for q in by_job['baker'] if q['bakery'] == k])
        add('watch', [q for q in by_job['watch'] if q['mode'] != 'night'])
        add('watch', [q for q in by_job['watch'] if q['mode'] == 'night'])
        add('lamps', by_job['lamplighter'])
        add('bells', by_job['bellringer'])
        for k in range(len(B.quay_pickups)):
            add('porters', [q for q in by_job['porter'] if q['pickup'] == k])
        for k in range(2):
            add('washers', [q for q in by_job['washer'] if q['washplace'] == k])
        for k in range(3):
            add('masons', [q for q in by_job['mason'] if q['yard'] == k])
        add('criers', by_job['crier'])
        add('boatmen', by_job['boatman'])
        add('runners', by_job['runner'])
        for ch in chunks(by_job['shopkeeper'], 10):
            add('shop', ch)
        for ch in chunks(by_job['tavernkeeper'], 7):
            add('tavern', ch)
        add('association', by_job['clerk'] + by_job['archivist'] + by_job['healer'])
        add('mill', by_job['miller'])
        for k in range(len(self.play_routes)):
            add('play', [q for q in by_job['child'] if q.get('site') == k])
        for ch in chunks(by_job['resident'], 5):
            add('elders', ch)
        for g in groups:
            for m in g['members']:
                cits[m]['group'] = g['id']
        assert all('group' in q for q in cits)
        assert sum(len(g['members']) for g in groups) == len(cits)
        self.groups = groups

    def work_of(self, cit):
        B = self.B
        job = cit['job']
        if job == 'lamplighter':
            return B.lamp_clusters[cit['cluster']]['route']
        if job == 'bellringer':
            return B.bells[cit['bell']]['place']
        if job == 'porter':
            return B.drops_routes[cit['pickup']]
        if job == 'stallkeeper':
            return B.stalls[cit['stall']]['keeper']
        if job == 'baker':
            return B.bakeries[cit['bakery']]['door']
        if job == 'smith':
            return B.smithies[cit['smithy']]['anvil']
        if job == 'watch':
            m = cit['mode']
            if m == 'gate':
                return B.post_places[cit['post']]
            if m == 'scout':
                return B.scout_route
            return cit['route']
        if job == 'crier':
            return B.crier_routes[cit['route']]
        if job == 'boatman':
            return B.moorings[cit['mooring']]['place']
        if job == 'washer':
            return B.washplaces[cit['washplace']]['places'][cit['slot']]
        if job == 'mason':
            return B.yard_places[cit['yard']]
        if job == 'runner':
            return B.courier_routes[cit['route']] if cit['mode'] == 'courier' else B.landmark('roost')
        if job == 'shopkeeper':
            return B.shops[cit['shop']]['door']
        if job == 'tavernkeeper':
            return B.taverns[cit['tavern']]['door']
        if job in ('clerk', 'archivist', 'healer'):
            return B.landmark('association')
        if job == 'miller':
            return B.mill_places[cit['mill']]
        return -1

    # -- the final days: errands, round starts, pushes, jitter, caps ---------------------------------------------------------------
    def errand_anchors(self, sg, i, speed, factor, stay=True):
        """(all_a, any_group): limits every errand place must meet (the walk out to the next target) and the limits the
        nearest place must meet together (the walk in from the previous one)."""
        n = len(sg)
        prev, nxt = sg[(i - 1) % n], sg[(i + 1) % n]
        gap_prev = (sg[i]['s'] - prev['s']) % DAY
        gap_next = (nxt['s'] - sg[i]['s']) % DAY
        allow_prev = gap_prev - (0 if prev['a'] in ('round', 'carry', 'wander') else 100)
        all_a, each_a = [], []
        pe = self.ends(prev)
        if pe is not None:
            each_a = [(e, max(1.0, allow_prev) * STROLL * speed * factor) for e in pe]
        ns = self.starts(nxt)
        if ns is not None:
            room = gap_next
            if stay and nxt['a'] in ('home', 'inside', 'stand'):
                # the stay after an errand must also fit the walk in, 200 ticks standing and the walk on
                nn = sg[(i + 2) % n]
                w2 = self.walk_ticks(self.ends(nxt), nn, speed)
                if w2 is not None:
                    room = min(room, ((nn['s'] - nxt['s']) % DAY or DAY) - 200 - w2)
            all_a += [(s_, max(1.0, room) * STROLL * speed * factor) for s_ in ns]
        return all_a, each_a

    def assign_errands(self, cit, sg):
        B = self.B
        for i, q in enumerate(sg):
            if q['tag'] != 'err' or q['t'] is not None:
                continue
            rid = None
            # a route that also leaves the stay after it long enough; else any that fits the gap (the stay is then pushed)
            for attempt in range(2):
                for factor in (0.9, 1.0):
                    rid = B.errand_route(*self.errand_anchors(sg, i, cit['speed'], factor, True))
                    if rid is not None:
                        break
                if rid is not None or attempt:
                    break
                # none near enough: make corner stands near the closest anchor (the home, usually) and look again
                anchors = self.errand_anchors(sg, i, cit['speed'], 0.9, True)[0]
                if anchors:
                    a_, lim = min(anchors, key=lambda t: t[1])
                    B.ensure_corners(a_, min(lim, 45.0), 5)
            if rid is None:
                for factor, stay in ((0.9, False), (1.0, False), (1.6, False), (3.0, False)):
                    rid = B.errand_route(*self.errand_anchors(sg, i, cit['speed'], factor, stay))
                    if rid is not None:
                        break
            assert rid is not None, f'no errand route for citizen {cit["id"]} ({cit["job"]})'
            q['t'] = rid
        return sg

    def assign_round_starts(self, cit, sg):
        B = self.B
        n = len(sg)
        # lamp tours begin at the end nearer home (alt 0); other rounds at the stop nearest where the citizen comes from
        chosen = {}
        for i, q in enumerate(sg):
            if q['a'] != 'round' or q['alt'] not in (-2, -3):
                continue
            pl = B.routes[q['t']]['places']
            if B.routes[q['t']]['kind'] == 'lamps':
                q['alt'] = 0
                continue
            if q['alt'] == -3:
                continue
            prev = sg[(i - 1) % n]
            e = self.ends(prev)
            idx = min(range(len(pl)), key=lambda k: (max(B.wd(x, pl[k]) for x in e), k))
            q['alt'] = idx
            chosen[q['t']] = idx
        for q in sg:
            if q['a'] == 'round' and q['alt'] == -3:
                q['alt'] = chosen.get(q['t'], 0)
        return sg

    def jittered(self, cit, sg, r):
        """Seeded jitter on every start (+-300, bellringers +-150, lamplighters' rounds +-100) that keeps order and every
        walk feasible; falls back to smaller jitter, then none."""
        n = len(sg)
        base = [q['s'] for q in sg]
        for scale in (1.0, 1.0, 0.6, 0.3, 0.0):
            for attempt in range(40 if scale > 0 else 1):
                st = []
                for i, q in enumerate(sg):
                    J = 150 if cit['job'] == 'bellringer' else (100 if cit['job'] == 'lamplighter' and q['a'] == 'round' else 300)
                    if cit['named'] == 'kestrel':
                        J = 0      # (her hours are the brief's: 600 to 12500 at her post)
                    J = int(J * scale)
                    off = r.randint(-J, J) if J else 0
                    v = base[i] + off
                    if base[i] == 0:
                        v = abs(off)
                    st.append(v)
                ok = all(0 <= st[i] < DAY for i in range(n)) and all(st[i + 1] - st[i] >= 150 for i in range(n - 1))
                if not ok:
                    continue
                mg = self.margins(sg, cit['speed'], starts=st)
                if all(m >= 0 for m, _ in mg):
                    return st
        return base

    def finalize(self):
        B = self.B
        cits = self.cits
        r = rng(0x717E5)
        by_id = {q['id']: q for q in cits}
        # lamp tours begin at the end nearest the lamplighter's home
        for cit in cits:
            if cit['job'] == 'lamplighter':
                rid = B.lamp_clusters[cit['cluster']]['route']
                pl = B.routes[rid]['places']
                near = min((pl[0], pl[-1]), key=lambda p: (B.wd(p, cit['home']), p))
                B.order_tour(rid, near)
        self.days = {}
        for cit in cits:
            H = cit['home']
            sg = self.segments(cit, H)
            sg = self.assign_errands(cit, sg)
            sg = self.assign_round_starts(cit, sg)
            pushed = self.push(sg, cit['speed'])
            if pushed is None:
                pushed = self.push(sg, cit['speed'], limit=8000)
            assert pushed is not None, f'citizen {cit["id"]} ({cit["job"]}) cannot make their day: ' + \
                ', '.join(f'{m:.0f} at {i} ({k})' for m, i, k in self.margins(sg, cit['speed'], kinds=True) if m < 0) + \
                ' in ' + ' '.join(f"{q['s']}:{q['a']}:{q['t']}" for q in sg)
            cit['pushed'] = max(q2['s'] - q1['s'] for q1, q2 in zip(sg, pushed))
            sg = pushed
            rj = rng(0x3000 + cit['id'])
            st = self.jittered(cit, sg, rj)
            for q, s_ in zip(sg, st):
                q['s'] = s_
            cit['segs'] = sg
        self.resolve_caps()

    def occ_slots(self, start, end):
        """Sample indexes (every 100 ticks) covered by [start, end) on a wrapping day."""
        s = start % DAY
        e = end % DAY
        k0 = int(math.ceil(s / 100.0))
        k1 = int(math.ceil(e / 100.0))
        if s < e or (s == e and start == end):
            return list(range(k0, k1))
        return list(range(k0, 240)) + list(range(0, k1))

    def delay_start(self, cit, i, new_s):
        """Moves segment i's start later (cascading over any starts it would reach), then repairs walks by pushing."""
        sg = cit['segs']
        n = len(sg)
        sg[i]['s'] = new_s
        j = i
        while True:
            nx = (j + 1) % n
            if nx == i or nx == 0:
                break
            if sg[nx]['s'] - sg[j]['s'] < 150:
                sg[nx]['s'] = sg[j]['s'] + 150
                j = nx
            else:
                break
        if sg[-1]['s'] >= DAY - 50:
            return False
        fixed = self.push(sg, cit['speed'], limit=3000)
        if fixed is None:
            return False
        for a_, b_ in zip(sg, fixed):
            a_['s'] = b_['s']
        return True

    def fix_overlaps(self):
        """Delay whoever arrives second where a place would be over cap (swaps, shared stands, benches)."""
        B = self.B
        cits = self.cits
        for it in range(200):
            occ = defaultdict(list)   # place -> [(cit, seg index)]
            for cit in cits:
                for i, q in enumerate(cit['segs']):
                    if q['a'] == 'stand' and q['tag'] not in PENDING_TAGS:
                        occ[q['t']].append((cit, i))
            fixed_any = False
            for place in sorted(occ):
                cap = B.places[place]['cap']
                items = occ[place]
                if len(items) <= cap:
                    continue
                cnt = [[] for _ in range(240)]
                for cit, i in items:
                    n = len(cit['segs'])
                    s_ = cit['segs'][i]['s']
                    e_ = s_ + ((cit['segs'][(i + 1) % n]['s'] - s_) % DAY or DAY)
                    for k in self.occ_slots(s_, e_):
                        cnt[k].append((cit, i, s_, e_))
                for k in range(240):
                    if len(cnt[k]) > cap:
                        t0 = k * 100
                        # the latest arrival (cyclic) waits for the earliest departure
                        lst = cnt[k]
                        victim = min(lst, key=lambda v: ((t0 - v[2]) % DAY, v[0]['id']))
                        others = [v for v in lst if v is not victim]
                        wait = min((v[3] - victim[2]) % DAY if (v[3] - victim[2]) % DAY else DAY for v in others)
                        new_s = victim[2] + wait + 100
                        if new_s >= DAY - 100 or not self.delay_start(victim[0], victim[1], new_s):
                            raise AssertionError(f'cannot fix overlap at place {place} citizen {victim[0]["id"]}')
                        fixed_any = True
                        break
                if fixed_any:
                    break
            if not fixed_any:
                return
        raise AssertionError('overlaps do not settle')

    def resolve_caps(self):
        """Who stands where: fixed targets first, then taverns and benches into whatever has room."""
        B = self.B
        cits = self.cits
        self.fix_overlaps()
        occ = defaultdict(lambda: [0] * 240)

        def seg_interval(cit, i):
            sg = cit['segs']
            n = len(sg)
            return sg[i]['s'], sg[i]['s'] + ((sg[(i + 1) % n]['s'] - sg[i]['s']) % DAY or DAY)

        def add(place, cit, i, sign=1):
            s_, e_ = seg_interval(cit, i)
            for k in self.occ_slots(s_, e_):
                occ[place][k] += sign
        pending = []
        for cit in cits:
            for i, q in enumerate(cit['segs']):
                if q['a'] != 'stand':
                    continue
                if q['tag'] in PENDING_TAGS:
                    pending.append((cit, i))
                else:
                    add(q['t'], cit, i)
        # fixed segments must already fit
        for place, arr in occ.items():
            cap = B.places[place]['cap']
            bad = [k for k, v in enumerate(arr) if v > cap]
            assert not bad, f'place {place} ({B.places[place]["kind"]}) over cap {cap} at samples {bad[:5]}'

        def room(place, s_, e_):
            cap = B.places[place]['cap']
            return all(occ[place][k] < cap for k in self.occ_slots(s_, e_))

        def ok_margin(cit):
            mg = self.margins(cit['segs'], cit['speed'])
            return all(m >= 0 for m, _ in mg)
        P = B.places
        tav_places = {t: [B.taverns[t]['out']] + ([B.taverns[t]['more']] if B.taverns[t].get('more', -1) >= 0 else []) for t in range(len(B.taverns))}
        loc_pool = [p for p in B.sit_places + B.well_places + B.notice_places]
        # the evening first (the taverns), then the noon sits of the square, then the benches and wells near home
        order = {'tav': 0, 'msit': 1, 'loc': 2, 'sit': 3}
        for cit, i in sorted(pending, key=lambda t: (order[t[0]['segs'][t[1]]['tag']], t[0]['id'], t[1])):
            q = cit['segs'][i]
            s_, e_ = seg_interval(cit, i)
            H = cit['home']
            tag = q['tag']
            if tag == 'tav':
                rank = self.tavern_rank(H)
                d0 = B.wd(B.taverns[rank[0]]['out'], H)
                cands = []
                for t in rank[:3]:
                    if B.wd(B.taverns[t]['out'], H) <= d0 + 60:
                        cands += tav_places[t]
            elif tag == 'msit':
                here = q['t']
                cands = sorted(self.msits, key=lambda p: (B.wd(p, here), p))
            elif tag == 'loc':
                here = q['t']
                cands = [here] + sorted((p for p in loc_pool if p != here and B.wd(p, here) <= 60), key=lambda p: (B.wd(p, here), p))[:10]
            else:
                cands = sorted(B.sit_places, key=lambda p: (B.wd(p, H), p))
            done = False
            for place in cands:
                if not room(place, s_, e_):
                    continue
                old = q['t']
                q['t'] = place
                if ok_margin(cit):
                    add(place, cit, i)
                    done = True
                    break
                q['t'] = old
            if done:
                continue
            if tag == 'tav':
                # taverns hold anyone inside: the usual tavern's door
                tr = self.tavern_rank(H)[0]
                q['a'] = 'inside'
                q['t'] = B.taverns[tr]['door']
            elif tag == 'msit':
                # no bench left in the square: they linger among the stalls instead
                q['a'] = 'wander'
                q['t'] = cit['mk']
                q['d'] = 700
            elif tag == 'loc':
                # the bench and everything near it taken: stay at home a little longer
                q['a'] = 'home'
                q['t'] = H
            else:
                raise AssertionError(f'no bench for citizen {cit["id"]}')
            q['tag'] = ''
            if not ok_margin(cit):
                pushed = self.push(cit['segs'], cit['speed'], limit=3000)
                assert pushed is not None, f'citizen {cit["id"]} cannot reach their {tag} place'
                for a_, b_ in zip(cit['segs'], pushed):
                    a_['s'] = b_['s']
        self.occ = occ


# ======================================================================================================================
#  the street lanes
# ======================================================================================================================
def edge_lanes(c, lamps_xz, trees):
    """Keep-right lane offset (tenths of a block) for every street-graph edge."""
    # (M4 B3a: every street-level prop cell is an obstacle like a lamp post)
    obstacles = [(x + 0.5, z + 0.5) for x, z in lamps_xz] + [(t[0] + 0.5, t[1] + 0.5) for t in trees] \
        + [(x + 0.5, z + 0.5) for pr in c.props for (x, z) in prop_cells(pr)]
    grid = defaultdict(list)
    for ox, oz in obstacles:
        grid[(int(math.floor(ox / 8)), int(math.floor(oz / 8)))].append((ox, oz))

    def clear_to(x, z, nx, nz):
        """Distance from (x, z) along (nx, nz) to the first unwalkable sample (max 8)."""
        d = 0.0
        while d < 8.0:
            d += 0.25
            if not c.walkable(x + nx * d, z + nz * d):
                return d - 0.25
        return 8.0
    out = []
    for a, b, move, sec, tag in c.edges:
        if move == 3 or move == MOVE_HIDDEN or a in c.wall_nodes and b in c.wall_nodes:
            out.append(0)
            continue
        na, nb = c.nodes[a], c.nodes[b]
        ax, az, bx, bz = na[0], na[2], nb[0], nb[2]
        L = math.hypot(bx - ax, bz - az)
        if L < 0.01:
            out.append(0)
            continue
        dx, dz = (bx - ax) / L, (bz - az) / L
        px, pz = -dz, dx
        half = 8.0
        for f in (0.25, 0.5, 0.75):
            x, z = ax + (bx - ax) * f, az + (bz - az) * f
            half = min(half, clear_to(x, z, px, pz), clear_to(x, z, -px, -pz))
        if half < 1.5:
            out.append(0)
            continue
        lane = int(round(10 * max(0.0, min(1.5, 0.45 * half))))
        # nothing (lamp post, tree) within 0.8 of either offset line
        near = []
        for gx in range(int(math.floor(min(ax, bx) / 8)) - 1, int(math.floor(max(ax, bx) / 8)) + 2):
            for gz in range(int(math.floor(min(az, bz) / 8)) - 1, int(math.floor(max(az, bz) / 8)) + 2):
                near += grid.get((gx, gz), [])
        while lane > 0:
            off = lane / 10.0
            blocked = False
            for sgn in (1, -1):
                seg = (ax + px * off * sgn, az + pz * off * sgn, bx + px * off * sgn, bz + pz * off * sgn)
                if any(City.seg_dist(seg, ox, oz) <= 0.8 for ox, oz in near):
                    blocked = True
                    break
            if not blocked:
                break
            lane -= 1
        out.append(lane)
    return out


# ======================================================================================================================
#  the output
# ======================================================================================================================
REF_KINDS = {'door', 'landmark', 'lamp', 'stall_keeper', 'stall_front', 'bell', 'anvil', 'tavern_out', 'shop_door', 'post'}


def num(v):
    return int(v) if float(v).is_integer() else round(float(v), 2)


def assemble(C, B, P):
    cits = P.cits
    places_rows = []
    for pl in B.places:
        ref = pl['ref'] if (pl['kind'] in REF_KINDS or (pl['kind'] == 'stand' and isinstance(pl['ref'], int) and pl['ref'] >= 0)) else -1
        places_rows.append([PK[pl['kind']], pl['node'], num(pl['x']), pl['ry'], num(pl['z']), pl['yaw'], pl['cap'], ref,
                            pl['district'], pl['approach'], [[num(a), num(b), y] for a, b, y in pl['spots']]])
    routes = [{'id': r['id'], 'kind': r['kind'], 'places': r['places']} for r in B.routes]
    lanes = edge_lanes(C, C.P['lamps'], C.P['trees'])
    route_of_lamp = {}
    for cl in B.lamp_clusters:
        for p_ in B.routes[cl['route']]['places']:
            route_of_lamp[p_] = cl['route']
    lamps = [{'place': p_, 'post': list(C.P['lamps'][k]), 'route': route_of_lamp[p_]} for k, p_ in enumerate(B.lamp_places)]
    stalls = [dict(s_) for s_ in B.stalls]
    bakeries = [{'plot': b['plot'], 'door': b['door'], 'front': b['front']} for b in B.bakeries]
    smithies = [{'plot': s_['plot'], 'door': s_['door'], 'anvil': s_['anvil'], 'block': s_['block']} for s_ in B.smithies]
    washplaces = [{'places': w['places'], 'tub': w['tub']} for w in B.washplaces]
    lines = [{'a': [int(l['a'][0]), int(l['a'][1])], 'b': [int(l['b'][0]), int(l['b'][1])], 'ry': l['ry'], 'place': l['place'],
              'cells': l['cells']} for l in B.lines]
    quay = {'pickup': B.quay_pickups, 'crates': B.crates}
    moorings = [{'place': m['place'], 'boat': m['boat'], 'yaw': m['yaw']} for m in B.moorings]
    taverns = [dict(t) for t in B.taverns]
    households = [{'id': h['id'], 'home': h['home'], 'plot': h['plot'], 'family': h['family'], 'members': h['members']} for h in P.hh]
    groups = [{'id': g['id'], 'kind': g['kind'], 'members': g['members']} for g in P.groups]
    rows = []
    for q in cits:
        tav = -1
        if q['job'] != 'child':
            tav = B.taverns[q['tavern']]['out'] if q['job'] == 'tavernkeeper' else B.taverns[P.tavern_rank(q['home'])[0]]['out']
        rows.append([q['id'], q['given'], q['family'], J[q['job']], q['household'], q['home'], P.work_of(q), q['group'], tav,
                     q['build'], q['age'], q['scale'], q['speed'], q['look'], q['named']])
    schedules = []
    for q in cits:
        sg = []
        for g in q['segs']:
            sg.append([int(g['s']), A[g['a']], g['t'], g['alt'] if g['a'] in ('round', 'carry') else -1, g['d'], g['f']])
        schedules.append(sg)
    re_ = rng(0xE87A5)
    extras = []
    for k in range(8):
        extras.append({'id': len(cits) + k, 'role': 'thief', 'look': [re_.randrange(6), re_.randrange(12), re_.randrange(8), re_.randrange(4),
                                                                    re_.randrange(4), re_.randrange(8)],
                       'build': re_.randrange(2), 'scale': round(re_.uniform(0.95, 1.05), 2)})
    out = {'schemaVersion': 1, 'generator': 'tools/city/citizens.py', 'planSha256': C.plan_sha, 'jobs': JOBS, 'acts': ACTS,
           'placeKinds': PLACE_KINDS, 'places': {'fields': PLACE_FIELDS, 'rows': places_rows}, 'routes': routes, 'edgeLane': lanes,
           'lamps': lamps, 'stalls': stalls, 'bells': B.bells, 'bakeries': bakeries, 'smithies': smithies,
           'washplaces': washplaces, 'lines': lines, 'quay': quay, 'moorings': moorings, 'taverns': taverns, 'households': households,
           'groups': groups, 'citizens': {'fields': CITIZEN_FIELDS, 'rows': rows}, 'schedules': schedules, 'extras': extras,
           'wallWalk': C.wall_extra()}
    return out


def generate(verbose=False):
    C = City()
    B = Builder(C)
    B.build_lamps()
    B.build_stalls()
    B.build_bells()
    B.build_taverns_shops()
    B.build_civic()
    B.build_steps()
    B.build_tavern_overflow()
    B.build_bakeries()
    B.build_smithies()
    B.build_washplaces()
    B.build_lines()
    B.build_quay()
    B.build_moorings()
    B.build_misc()
    B.build_named()
    B.sit_places = B.bench_places + B.fountain_places + B.step_places + B.wall_sits
    B.build_patrols()
    B.build_scout_courier()
    B.build_errand_corners()
    B.build_lamp_routes()
    B.build_routes()
    B.build_wall_rounds()
    for lid in ('roost', 'choir', 'association', 'market_hall'):
        B.landmark(lid)
    P = Population(B)
    P.make_roster()
    P.assign_work()
    P.form_households()
    P.build_play()
    P.finish_households()
    P.make_groups()
    P.finalize()
    out = assemble(C, B, P)
    text = jdump(out)
    return text, out, (C, B, P)


# ======================================================================================================================
#  checks, stats, preview
# ======================================================================================================================


class Checker:
    """Re-derives every property from the serialised output and the plan."""

    def __init__(self, out, C):
        self.o = out
        self.C = C
        f = out['places']['fields']
        self.pl = [dict(zip(f, r)) for r in out['places']['rows']]
        f = out['citizens']['fields']
        self.ct = [dict(zip(f, r)) for r in out['citizens']['rows']]
        self.routes = out['routes']
        self.results = []

    def cost(self, p):
        pl = self.pl[p]
        nd = self.C.nodes[pl['node']]
        sp = pl['spots'][0]
        return math.hypot(sp[0] - nd[0], sp[1] - nd[2])

    def wd(self, a, b):
        if a == b:
            return 0.0
        return self.C.row(self.pl[a]['node'])[self.pl[b]['node']] + self.cost(a) + self.cost(b)

    quiet = False

    def rec(self, name, ok, detail):
        self.results.append((name, ok, detail))
        if not self.quiet:
            print(f"{'ok  ' if ok else 'FAIL'} {name}: {detail}")
        return ok

    # 1 ----------------------------------------------------------------------------------------------------------------
    def counts(self):
        o, ct = self.o, self.ct
        errs = []
        jc = Counter(JOBS[q['job']] for q in ct)
        for j, n in JOB_COUNTS.items():
            if jc[j] != n:
                errs.append(f'{j} {jc[j]} != {n}')
        if len(ct) != TOTAL:
            errs.append(f'{len(ct)} citizens')
        named = Counter(q['named'] for q in ct if q['named'])
        if sorted(named) != ['bram', 'kestrel', 'mira', 'oren', 'sun', 'teodor'] or any(v != 1 for v in named.values()):
            errs.append(f'named {dict(named)}')
        hm = Counter()
        for h in o['households']:
            for m in h['members']:
                hm[m] += 1
            if not 1 <= len(h['members']) <= 5:
                errs.append(f'household {h["id"]} has {len(h["members"])}')
        if sorted(hm) != list(range(TOTAL)) or any(v != 1 for v in hm.values()):
            errs.append('a citizen is not in exactly one household')
        gm = Counter()
        for g in o['groups']:
            if g['kind'] not in GROUP_KINDS:
                errs.append(f'group kind {g["kind"]}')
            for m in g['members']:
                gm[m] += 1
        if sorted(gm) != list(range(TOTAL)) or any(v != 1 for v in gm.values()):
            errs.append('a citizen is not in exactly one group')
        for q in ct:
            if q['household'] != next(h['id'] for h in o['households'] if q['id'] in h['members']):
                errs.append(f'citizen {q["id"]} household field')
                break
        if len({h['plot'] for h in o['households']}) != len(o['households']):
            errs.append('two households share a plot')
        names = Counter((q['given'], q['family']) for q in ct)
        if any(v > 1 for v in names.values()):
            errs.append('duplicate given+family')
        for h in o['households']:
            mem = [ct[m] for m in h['members']]
            if any(q['age'] == 0 for q in mem) and all(q['age'] == 0 for q in mem):
                errs.append(f'household {h["id"]} has only children')
            ad = {q['given'] for q in mem if q['age'] != 0}
            if any(q['age'] == 0 and q['given'] in ad for q in mem):
                errs.append(f'child shares an adult name in household {h["id"]}')
        for q in ct:
            h = o['households'][q['household']]
            if bool(q['named']) != (q['family'] == '') or (q['named'] and len(h['members']) != 1):
                errs.append(f'citizen {q["id"]} family name / named household')
        for h in o['households']:
            if h['plot'] == -1:     # Kestrel lives in the Roost itself
                if [ct[m]['named'] for m in h['members']] != ['kestrel'] or self.pl[h['home']]['kind'] != PK['landmark'] or self.pl[h['home']]['ref'] != 'roost':
                    errs.append(f'household {h["id"]} (no plot) is not Kestrel at the Roost')
                continue
            p = self.C.plots.get(h['plot'])
            if p is None or p['use'] != 'house' or p['enter'] or self.pl[h['home']]['kind'] != PK['door'] or self.pl[h['home']]['ref'] != h['plot']:
                errs.append(f'household {h["id"]} home is not a house plot door')
        sizes = Counter(len(h['members']) for h in o['households'])
        return self.rec('1 counts', not errs,
                        f'{TOTAL} citizens, jobs ok, 6 named, {len(o["households"])} households {dict(sorted(sizes.items()))}, '
                        f'{len(o["groups"])} groups' + ('; ' + '; '.join(errs[:6]) if errs else ''))

    # 2 ----------------------------------------------------------------------------------------------------------------
    def places(self):
        C = self.C
        errs = []
        door_cells = C.door_cells
        main = set(C.main)
        n_sp = 0
        for pid, pl in enumerate(self.pl):
            kind = PLACE_KINDS[pl['kind']]
            if pl['node'] not in main:
                errs.append(f'place {pid} node not in the main component')
            if pl['node'] in C.wall_nodes:
                # (a stand on the wall walk: its spot on the walk beside its node; the plan's raster says wall there)
                for (x, z, yaw) in pl['spots']:
                    n_sp += 1
                    nd = C.nodes[pl['node']]
                    if (x - math.floor(x)) != 0.5 or (z - math.floor(z)) != 0.5 or math.hypot(x - nd[0], z - nd[2]) > 2.5:
                        errs.append(f'wall place {pid} spot ({x},{z}) not a block centre beside its node')
                continue
            if len(pl['spots']) != pl['cap'] or pl['cap'] < 1:
                errs.append(f'place {pid} cap/spots')
            for (x, z, yaw) in pl['spots']:
                n_sp += 1
                if (x - math.floor(x)) != 0.5 or (z - math.floor(z)) != 0.5:
                    errs.append(f'place {pid} spot not a block centre')
                cx, cz = math.floor(x), math.floor(z)
                i, j = cx + O, cz + O
                if kind == 'door':
                    if door_cells.get((cx, cz)) != pl['ref']:
                        errs.append(f'door place {pid} is not on its door cell')
                    continue
                if kind == 'landmark':
                    nd = C.nodes[pl['node']]
                    if (math.floor(nd[0]), math.floor(nd[2])) != (cx, cz):
                        errs.append(f'landmark place {pid} spot is not the node cell')
                    continue
                if not C.walk[j, i]:
                    errs.append(f'place {pid} ({kind}) spot ({x},{z}) not walkable')
                if C.plot_at[j, i] >= 0:
                    errs.append(f'place {pid} ({kind}) spot in a plot')
                if C.water[j, i] or int(C.kind[j, i]) == LOWQUAY:
                    errs.append(f'place {pid} ({kind}) spot in water')
                if (cx, cz) in door_cells or (cx, cz) in C.landmark_cells:
                    errs.append(f'place {pid} ({kind}) spot on a door cell')
                if kind not in ('stall_keeper', 'stall_front') and C.block[j, i]:
                    errs.append(f'place {pid} ({kind}) spot on a landmark footprint')
                if not C.lane_ok(x, z):
                    errs.append(f'place {pid} ({kind}) spot ({x},{z}) within 1.0 of a street line on a narrow street')
            sp = pl['spots']
            for a in range(len(sp)):
                for b in range(a + 1, len(sp)):
                    if math.hypot(sp[a][0] - sp[b][0], sp[a][1] - sp[b][1]) < 0.9:
                        errs.append(f'place {pid} spots closer than 0.9')
            ap = max(C.approach(pl['node'], s_) for s_ in pl['spots'])
            nd = C.nodes[pl['node']]
            far = max(math.hypot(s_[0] - nd[0], s_[1] - nd[2]) for s_ in pl['spots'])
            if far > (36.0 if ap == 0 else PATH_REACH):
                errs.append(f'place {pid} ({kind}) spots are {far:.1f} from the node with approach {ap}')
            if kind not in ('door', 'landmark') and C.nearest_node(pl['x'], pl['z'])[0] != pl['node']:
                nn = C.nearest_node(pl['x'], pl['z'])[0]
                nnd = C.nodes[nn]
                nfar = max(math.hypot(s_[0] - nnd[0], s_[1] - nnd[2]) for s_ in pl['spots'])
                nap = max(C.approach(nn, s_) for s_ in pl['spots'])
                if nfar <= (36.0 if nap == 0 else PATH_REACH):
                    errs.append(f'place {pid} ({kind}) node is not the nearest by walking')
            if ap != pl['approach']:
                errs.append(f'place {pid} approach flag')
        ap1 = sum(1 for p in self.pl if p['approach'])
        kinds = Counter(PLACE_KINDS[p['kind']] for p in self.pl)
        return self.rec('2 places', not errs, f'{len(self.pl)} places, {n_sp} spots, {ap1} with approach 1' +
                        ('; ' + '; '.join(errs[:6]) + (f' (+{len(errs) - 6})' if len(errs) > 6 else '') if errs else ''))

    # shared schedule helpers ------------------------------------------------------------------------------------------------
    def ends(self, seg):
        a, t, alt = ACTS[seg[1]], seg[2], seg[3]
        if a in ('home', 'inside', 'stand'):
            return [t]
        if a in ('round', 'wander'):
            return self.routes[t]['places']
        return [t] + self.routes[alt]['places']

    def starts(self, seg):
        a, t, alt = ACTS[seg[1]], seg[2], seg[3]
        if a in ('home', 'inside', 'stand', 'carry'):
            return [t]
        if a == 'round':
            return [self.routes[t]['places'][alt]]
        return self.routes[t]['places']

    def near_place(self, e, places):
        x, z = self.pl[e]['x'], self.pl[e]['z']
        return min(places, key=lambda q: (math.hypot(self.pl[q]['x'] - x, self.pl[q]['z'] - z), q))

    def starts_to(self, p, nxt):
        """Walk from place p to the next segment's start (nearest stop of a round, any for the rest), blocks."""
        s_ = self.starts(nxt)
        return max(self.wd(p, y) for y in s_)

    def walk_ticks(self, e, nxt, speed):
        s_ = self.starts(nxt)
        if ACTS[nxt[1]] == 'wander':
            w = max(self.wd(x, self.near_place(x, s_)) for x in e)
        else:
            w = max(self.wd(x, y) for x in e for y in s_)
        return w / (STROLL * speed)

    def margins(self, q):
        sg = self.o['schedules'][q['id']]
        n = len(sg)
        out = []
        moving = ('round', 'carry', 'wander')
        for i in range(n):
            a, b = sg[i], sg[(i + 1) % n]
            gap = (b[0] - a[0]) % DAY
            walk = self.walk_ticks(self.ends(a), b, q['speed'])
            allow = gap - (0 if ACTS[a[1]] in moving else 100)
            out.append((allow - walk, i, 'walk'))
            c_ = sg[(i + 2) % n]
            dur = (c_[0] - b[0]) % DAY or DAY
            if ACTS[b[1]] == 'wander' and n > 2:
                places = self.starts(b)
                worst = max(self.wd(e, self.near_place(e, places)) + self.starts_to(self.near_place(e, places), c_) for e in self.ends(a))
                out.append((dur - worst / (STROLL * q['speed']) - 100, (i + 1) % n, 'stay'))
            elif ACTS[a[1]] in moving and ACTS[b[1]] in ('home', 'inside', 'stand') and n > 2:
                w2 = self.walk_ticks(self.ends(b), c_, q['speed'])
                out.append((dur - w2 - walk - 200, (i + 1) % n, 'stay'))
        return out

    # 3 ----------------------------------------------------------------------------------------------------------------
    def reach(self):
        C = self.C
        errs = []
        # components over the whole street graph, door and landmark nodes as dead ends (a walk never passes through one):
        # every place's node, and every target of a schedule or route, must be in the biggest
        nodes = C.nodes
        isleaf = [nd[3] in ('door', 'landmark') for nd in nodes]
        nbr = [[] for _ in nodes]
        for a, b, *_ in C.edges:
            nbr[a].append(b)
            nbr[b].append(a)
        comp = {}
        for s0 in range(len(nodes)):
            if isleaf[s0] or s0 in comp:
                continue
            comp[s0] = s0
            stack = [s0]
            while stack:
                u = stack.pop()
                for v in nbr[u]:
                    if not isleaf[v] and v not in comp:
                        comp[v] = s0
                        stack.append(v)
        sizes = Counter(comp.values())
        big = sizes.most_common(1)[0][0]

        def usable(n):
            if isleaf[n]:
                return bool(nbr[n]) and all(not isleaf[v] and comp.get(v) == big for v in nbr[n])
            return comp.get(n) == big
        for pid, pl in enumerate(self.pl):
            if not usable(pl['node']):
                errs.append(f'place {pid} ({PLACE_KINDS[pl["kind"]]}) is on node {pl["node"]}, not in the main component')
        for q in self.ct:
            row = C.row(self.pl[q['home']]['node'])
            for seg in self.o['schedules'][q['id']]:
                a = ACTS[seg[1]]
                targets = []
                if a in ('home', 'inside', 'stand'):
                    targets = [seg[2]]
                elif a in ('round', 'wander'):
                    targets = self.routes[seg[2]]['places']
                else:
                    targets = [seg[2]] + self.routes[seg[3]]['places']
                for t in targets:
                    if not usable(self.pl[t]['node']):
                        errs.append(f'citizen {q["id"]} targets place {t} on node {self.pl[t]["node"]}, which is not in the main component')
                    if not math.isfinite(row[self.pl[t]['node']]):
                        errs.append(f'citizen {q["id"]} cannot reach place {t}')
        for r in self.routes:
            pl = r['places']
            for t in pl:
                if not usable(self.pl[t]['node']):
                    errs.append(f'route {r["id"]} has place {t} on node {self.pl[t]["node"]}, not in the main component')
            for a, b in zip(pl, pl[1:] + (pl[:1] if r['kind'] in ('patrol', 'scout', 'crier', 'courier') else [])):
                if not math.isfinite(self.wd(a, b)):
                    errs.append(f'route {r["id"]} breaks between {a} and {b}')
            if not pl:
                errs.append(f'route {r["id"]} empty')
        return self.rec('3 reachability', not errs, f'{len(self.ct)} citizens x their targets, {len(self.routes)} routes' +
                        ('; ' + '; '.join(errs[:5]) if errs else ''))

    # 4 ----------------------------------------------------------------------------------------------------------------
    def feasible(self):
        worst = []
        errs = []
        for q in self.ct:
            sg = self.o['schedules'][q['id']]
            if not 3 <= len(sg) <= 9:
                errs.append(f'citizen {q["id"]} has {len(sg)} segments')
            if [s_[0] for s_ in sg] != sorted(s_[0] for s_ in sg) or len({s_[0] for s_ in sg}) != len(sg):
                errs.append(f'citizen {q["id"]} schedule not strictly sorted')
            if not all(0 <= s_[0] < DAY for s_ in sg):
                errs.append(f'citizen {q["id"]} start out of the day')
            for m, i, kind in self.margins(q):
                worst.append((m, q['id'], JOBS[q['job']], i))
                if m < 0:
                    errs.append(f'citizen {q["id"]} ({JOBS[q["job"]]}) segment {i}: {m:.0f} ticks')
        worst.sort()
        self.worst = worst[:10]
        txt = ', '.join(f'{m:.0f}t {j}#{c}' for m, c, j, i in worst[:10])
        return self.rec('4 feasibility', not errs, f'{len(worst)} transitions, worst margins: {txt}' +
                        ('; ' + '; '.join(errs[:5]) if errs else ''))

    # 5 ----------------------------------------------------------------------------------------------------------------
    def caps(self):
        occ = defaultdict(lambda: [0] * 240)
        for q in self.ct:
            sg = self.o['schedules'][q['id']]
            n = len(sg)
            for i, seg in enumerate(sg):
                if ACTS[seg[1]] != 'stand':
                    continue
                s_ = seg[0]
                e_ = s_ + ((sg[(i + 1) % n][0] - s_) % DAY or DAY)
                for k in range(240):
                    t = k * 100
                    if s_ <= t < e_ or s_ <= t + DAY < e_:
                        occ[seg[2]][k] += 1
        errs = []
        peak = 0
        for place, arr in occ.items():
            cap = self.pl[place]['cap']
            peak = max(peak, max(arr))
            for k, v in enumerate(arr):
                if v > cap:
                    errs.append(f'place {place} ({PLACE_KINDS[self.pl[place]["kind"]]}) {v} > cap {cap} at tick {k * 100}')
                    break
        return self.rec('5 caps', not errs, f'{len(occ)} stand places, peak occupancy {peak}' + ('; ' + '; '.join(errs[:5]) if errs else ''))

    # 6 ----------------------------------------------------------------------------------------------------------------
    def lamps(self):
        o = self.o
        errs = []
        seen = Counter()
        lamp_routes = [r for r in self.routes if r['kind'] == 'lamps']
        n_lamps = len(self.C.P['lamps'])
        n_tours = -(-n_lamps // 10)
        if len(lamp_routes) != n_tours:
            errs.append(f'{len(lamp_routes)} lamp routes')
        for r in lamp_routes:
            for p in r['places']:
                seen[p] += 1
        if len(o['lamps']) != n_lamps or len(seen) != n_lamps or any(v != 1 for v in seen.values()) or \
                {lp['place'] for lp in o['lamps']} != set(seen):
            errs.append('lamps are not each in exactly one lamp route')
        lamplighters = [q for q in self.ct if JOBS[q['job']] == 'lamplighter']
        slow = min(q['speed'] for q in lamplighters)
        worst_l, worst_o = 1e9, 1e9
        for q in lamplighters:
            sg = o['schedules'][q['id']]
            for seg in sg:
                if ACTS[seg[1]] != 'round':
                    continue
                pl = self.routes[seg[2]]['places']
                walk = sum(self.wd(a, b) for a, b in zip(pl, pl[1:])) / (RUN_PACE * slow)
                dwell = seg[4] * len(pl)
                finish = seg[0] + walk + dwell
                if seg[5] & 2:
                    worst_l = min(worst_l, 13600 - finish)
                    if finish >= 13600:
                        errs.append(f'light round of citizen {q["id"]} ends at {finish:.0f}')
                else:
                    worst_o = min(worst_o, DAY + 1800 - finish)
                    if finish >= DAY + 1800:
                        errs.append(f'out round of citizen {q["id"]} ends at {finish - DAY:.0f}')
            # a route's first lamp is the one nearest its lamplighter's home
            rid = next(seg[2] for seg in sg if ACTS[seg[1]] == 'round')
            pl = self.routes[rid]['places']
            dd = [self.wd(p, q['home']) for p in pl]
            if dd[0] > min(dd) + 0.05:
                errs.append(f'lamplighter {q["id"]}: first lamp is not the nearest to home')
        longest = max(sum(self.wd(a, b) for a, b in zip(r['places'], r['places'][1:])) for r in lamp_routes)
        return self.rec('6 lamps', not errs, f'{n_lamps} lamps in {n_tours} routes (longest tour {longest:.0f} blocks), at the run {RUN_PACE}/tick x slowest speed {slow}: '
                        f'light round ends {worst_l:.0f} ticks before 13600, out round {worst_o:.0f} before 1800' +
                        ('; ' + '; '.join(errs[:5]) if errs else ''))


    # 9 ----------------------------------------------------------------------------------------------------------------
    def structure(self):
        C, o = self.C, self.o
        errs = []
        by = defaultdict(list)
        for r in self.routes:
            by[r['kind']].append(r)
        n_tours = -(-len(C.P['lamps']) // 10)
        want = {'lamps': (n_tours, n_tours), 'dock': (1, 1), 'scout': (1, 1), 'crier': (5, 5), 'courier': (3, 3), 'play': (4, 6), 'sit': (1, 1), 'drops': (9, 9),
                'market': (6, 6)}
        for k, (lo, hi) in want.items():
            if not lo <= len(by[k]) <= hi:
                errs.append(f'{len(by[k])} {k} routes')
        if not 7 <= len(by['patrol']) <= 14:
            errs.append(f'{len(by["patrol"])} patrol routes')
        if len(by['errand']) < 7:
            errs.append('fewer than 7 errand routes')
        if len(by.get('wall', [])) != 4:
            errs.append(f'{len(by.get("wall", []))} wall rounds')
        for r in by.get('wall', []):
            pl = r['places']
            if not 5 <= len(pl) <= 8:
                errs.append(f'wall round {r["id"]} has {len(pl)} stops')
            for a, b in zip(pl, pl[1:]):
                d = self.C.row(self.pl[a]['node'])[self.pl[b]['node']]
                if not 20 <= d <= 80:
                    errs.append(f'wall round {r["id"]} stops {a}->{b} are {d:.0f} apart')
        for r in by['patrol']:
            pl = r['places']
            if not 5 <= len(pl) <= 8:
                errs.append(f'patrol {r["id"]} has {len(pl)} stops')
            for a, b in zip(pl, pl[1:] + pl[:1]):
                d = self.C.row(self.pl[a]['node'])[self.pl[b]['node']]
                if not 24.5 <= d <= 60.5:
                    errs.append(f'patrol {r["id"]} stops {a}->{b} are {d:.0f} apart')
            if any(self.pl[p]['cap'] != 2 for p in pl):
                errs.append(f'patrol {r["id"]} stop cap')
        for r in by['errand']:
            if not 4 <= len(r['places']) <= 10:
                errs.append(f'errand {r["id"]} has {len(r["places"])} places')
        for r in by['play']:
            # (the play places, cap 4, and the fountains and wells beside them)
            plays = [p for p in r['places'] if PLACE_KINDS[self.pl[p]['kind']] == 'play']
            if not 5 <= len(r['places']) <= 8 or len(plays) < 4 or any(self.pl[p]['cap'] != 4 for p in plays):
                errs.append(f'play route {r["id"]}')
        for r in by['market'] + by['stroll']:
            if not 1 <= len(r['places']) <= 9 or any(PLACE_KINDS[self.pl[p]['kind']] not in ('sit', 'stand', 'stall_front') for p in r['places']):
                errs.append(f'{r["kind"]} route {r["id"]}')
        for r in by['crier']:
            if len(r['places']) != 3 or any(PLACE_KINDS[self.pl[p]['kind']] != 'crier' or self.pl[p]['cap'] != 1 for p in r['places']):
                errs.append(f'crier route {r["id"]}')
        for r in by['courier']:
            if not 5 <= len(r['places']) <= 6 or PLACE_KINDS[self.pl[r['places'][0]]['kind']] != 'landmark':
                errs.append(f'courier route {r["id"]}')
        n_sq = sum(1 for pr in C.props if pr['kind'] == 'stall' and pr['ry'] < 0)
        if sorted(len(r['places']) for r in by['drops']) != [2] * 5 + [n_sq // 4] * 4:
            errs.append('drops routes')
        # washplaces and lines
        wps = o['washplaces']
        if len(wps) != 2:
            errs.append('washplaces')
        for w in wps:
            sp = [self.pl[p]['spots'][0] for p in w['places']]
            if not 4 <= len(sp) <= 6 or any(self.pl[p]['cap'] != 1 or PLACE_KINDS[self.pl[p]['kind']] != 'wash' for p in w['places']):
                errs.append('wash places')
            for i in range(len(sp)):
                x, z = sp[i][0], sp[i][1]
                ci, cj = C.cell(x, z)
                touches = any(C.water[cj + b, ci + a] for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                if not touches:
                    errs.append(f'wash spot ({x},{z}) does not touch water')
                for j in range(i + 1, len(sp)):
                    if math.hypot(x - sp[j][0], z - sp[j][1]) < 2.0 - 1e-9:
                        errs.append('wash spots closer than 2')
            tx, ry, tz = w['tub']
            if not C.ground[tz + O, tx + O]:
                errs.append('tub cell not walkable')
        ls = o['lines']
        if not 4 <= len(ls) <= 6:
            errs.append(f'{len(ls)} laundry lines')
        for i, ln in enumerate(ls):
            ax, az = ln['a']
            bx, bz = ln['b']
            n = max(abs(bx - ax), abs(bz - az)) + 1
            if n != ln['cells'] or not 3 <= n <= 7 or (ax != bx and az != bz):
                errs.append(f'line {i} cells')
                continue
            dx, dz = (1 if bx > ax else (-1 if bx < ax else 0)), (1 if bz > az else (-1 if bz < az else 0))
            if C.plot_at[az - dz + O, ax - dx + O] < 0 or C.plot_at[bz + dz + O, bx + dx + O] < 0:
                errs.append(f'line {i} is not between two walls')
            if not all(C.ground[az + dz * k + O, ax + dx * k + O] for k in range(n)):
                errs.append(f'line {i} crosses unwalkable ground')
            mx, mz = (ax + bx) / 2 + 0.5, (az + bz) / 2 + 0.5
            if min(math.hypot(mx - self.pl[p]['x'], mz - self.pl[p]['z']) for w in wps for p in w['places']) > 40.0:
                errs.append(f'line {i} is farther than 40 from a washplace')
            for j in range(i + 1, len(ls)):
                m2x, m2z = (ls[j]['a'][0] + ls[j]['b'][0]) / 2 + 0.5, (ls[j]['a'][1] + ls[j]['b'][1]) / 2 + 0.5
                if math.hypot(mx - m2x, mz - m2z) < 8:
                    errs.append(f'lines {i} and {j} closer than 8')
        # smithies, bakeries, quay, taverns
        sm = o['smithies']
        if len(sm) != 4:
            errs.append('smithies')
        for i, a in enumerate(sm):
            pa = C.plots[a['plot']]
            for b in sm[i + 1:]:
                pb = C.plots[b['plot']]
                if math.hypot(pa['doorX'] - pb['doorX'], pa['doorZ'] - pb['doorZ']) < 30:
                    errs.append('smithies closer than 30')
            if int(C.kind[pa['doorZ'] + {'N': -1, 'S': 1, 'E': 0, 'W': 0}[pa['front']] + O,
                          pa['doorX'] + {'N': 0, 'S': 0, 'E': 1, 'W': -1}[pa['front']] + O]) not in (STREET, LANE):
                errs.append('smithy door not on a street or lane')
            bx, ry, bz = a['block']
            if not C.ground[bz + O, bx + O]:
                errs.append('anvil block cell not walkable')
        if [C.plots[a['plot']]['district'] for a in sm] != ['works', 'works', 'works', 'quay']:
            errs.append('smithy districts')
        bk = o['bakeries']
        if [C.plots[b['plot']]['district'] for b in bk] != ['market', 'bellwatch', 'quay', 'garden', 'works']:
            errs.append('bakery districts')
        for b in bk:
            p = C.plots[b['plot']]
            if p['enter'] or p['use'] != 'house' or PLACE_KINDS[self.pl[b['front']]['kind']] != 'stand' or self.pl[b['front']]['cap'] != 1:
                errs.append('bakery plot or front')
        q = o['quay']
        if len(q['pickup']) != 4 or not 6 <= len(q['crates']) <= 10:
            errs.append('quay')
        for p in q['pickup']:
            if any(C.kind_at(s_[0], s_[1]) != QUAY for s_ in self.pl[p]['spots']) or self.pl[p]['cap'] != 4:
                errs.append('pickup places are not on QUAY cells')
        for x, ry, z in q['crates']:
            if not C.ground[z + O, x + O]:
                errs.append('crate cell not walkable')
        # (M4 B3b) every boatman has a berth of his own on a quay's edge
        mo = o['moorings']
        if len(mo) < 10 or any(PLACE_KINDS[self.pl[m['place']]['kind']] != 'mooring' or self.pl[m['place']]['cap'] != 1 for m in mo):
            errs.append('moorings')
        mine = [P_.work_of(q_) for q_ in P_.cits if q_['job'] == 'boatman'] if (P_ := getattr(self, 'P', None)) else []
        if mine and len(set(mine)) != len(mine):
            errs.append('two boatmen share a berth')
        n_stalls = sum(1 for pr in C.props if pr['kind'] == 'stall')
        if len(o['taverns']) != 14 or len(o['bells']) != 6 or len(o['stalls']) != n_stalls or len(o['lamps']) != len(C.P['lamps']):
            errs.append('taverns/bells/stalls/lamps counts')
        if len(o['edgeLane']) != len(C.edges) or any(not 0 <= v <= 15 for v in o['edgeLane']):
            errs.append('edgeLane')
        if len(o['extras']) != 8:
            errs.append('extras')
        return self.rec('9 structure (more of the brief)', not errs, f'routes {dict(Counter(r["kind"] for r in self.routes))}; 2 washplaces, {len(ls)} lines, 4 smithies, 5 bakeries, '
                        f'quay {len(q["crates"])} crates' + ('; ' + '; '.join(errs[:6]) + (f' (+{len(errs) - 6})' if len(errs) > 6 else '') if errs else ''))


    # 10 ----------------------------------------------------------------------------------------------------------------
    def seg_at(self, q, tod):
        """The citizen's segment at a time of day (the last one started, wrapping)."""
        sg = self.o['schedules'][q['id']]
        best = None
        for g in sg:
            if g[0] <= tod:
                best = g
        return best if best is not None else sg[-1]

    def where(self, q, tod):
        """(outdoors, [(place, weight)]) for a citizen at a time of day: a stand is at its place, a round, carry or wander
        spread evenly over the places of its route (the ledger picks them in turn); at home or inside is not outdoors."""
        g = self.seg_at(q, tod)
        a = ACTS[g[1]]
        if a in ('home', 'inside'):
            return False, [(g[2], 1.0)]
        if a == 'stand':
            return True, [(g[2], 1.0)]
        pl = [g[2]] + self.routes[g[3]]['places'] if a == 'carry' else self.routes[g[2]]['places']
        return True, [(p, 1.0 / len(pl)) for p in pl]

    def life(self, B=None):
        """The street-life checks: every stall has its opener, every sit/stand place the props define is used by someone, the
        market fills at noon and the taverns at 20:00, no standing place in a prop's cell but the ones props define."""
        o, C = self.o, self.C
        errs = []
        # every stall has its opener: a stallkeeper standing at its keeper place from the morning into the afternoon
        for k, st in enumerate(o['stalls']):
            ok = any(JOBS[q['job']] == 'stallkeeper' and (g := self.seg_at(q, 1100)) and ACTS[g[1]] == 'stand' and g[2] == st['keeper']
                     and ACTS[self.seg_at(q, 6000)[1]] == 'stand' and self.seg_at(q, 6000)[2] == st['keeper'] for q in self.ct)
            if not ok:
                errs.append(f'stall {k} has no opener')
        # every sit/stand place from props is used by someone (a stand, or a stop on a route someone walks)
        used = set()
        for q in self.ct:
            for g in self.o['schedules'][q['id']]:
                a = ACTS[g[1]]
                if a in ('home', 'inside', 'stand'):
                    used.add(g[2])
                elif a == 'carry':
                    used.add(g[2])
                    used.update(self.routes[g[3]]['places'])
                else:
                    used.update(self.routes[g[2]]['places'])
        if B is not None:
            prop_places = set(B.bench_places + B.well_places + B.fountain_places + B.notice_places + B.step_places)
            prop_places |= {t['out'] for t in B.taverns if t['table']}
            unused = sorted(p for p in prop_places if p not in used)
            if unused:
                errs.append(f'{len(unused)} prop places nobody uses: {unused[:8]}')
        else:
            prop_places = set()
        # noon in the Market district (outdoors), 20:00 at the taverns
        noon = 0.0
        noon_in = 0
        for q in self.ct:
            out_, pl = self.where(q, 6000)
            w = sum(wt for p, wt in pl if self.pl[p]['district'] == 1)
            if out_:
                noon += w
            else:
                noon_in += w
        tav = sum(1 for q in self.ct if ACTS[self.seg_at(q, 14000)[1]] == 'stand' and PLACE_KINDS[self.pl[self.seg_at(q, 14000)[2]]['kind']] == 'tavern_out')
        tav_in = sum(1 for q in self.ct if ACTS[self.seg_at(q, 14000)[1]] == 'inside' and PLACE_KINDS[self.pl[self.seg_at(q, 14000)[2]]['kind']] == 'door'
                     and self.pl[self.seg_at(q, 14000)[2]]['ref'] in {t['plot'] for t in o['taverns']})
        self.noon_market, self.tavern_2000 = noon, tav
        if noon < 120:
            errs.append(f'only {noon:.0f} outdoors in the Market district at noon (120 wanted)')
        if tav < 70:
            errs.append(f'only {tav} at tavern places at 20:00 (70 wanted)')
        return self.rec('10 street life', not errs,
                        f'{len(o["stalls"])} stalls each opened; {len(prop_places)} prop places each used; noon: {noon:.0f} outdoors in the Market district '
                        f'(+{noon_in:.0f} indoors there); 20:00: {tav} standing at tavern places ({tav_in} inside the taverns)' +
                        ('; ' + '; '.join(errs[:6]) if errs else ''))

    # 11 ----------------------------------------------------------------------------------------------------------------
    def props(self):
        """No spot stands in a street-level prop's cell except the spots the props define (a bench's cells, a tavern table's bench
        rows, a stall's back row), and no keep-right lane line passes within 0.8 of a prop cell's centre."""
        C, o = self.C, self.o
        errs = []
        owner = {}
        for pr in C.props:
            for cell in prop_cells(pr):
                owner[cell] = pr['kind']
        allowed = {'bench': ('sit',), 'tavern_table': ('tavern_out',), 'stall': ('stall_keeper', 'stall_front')}
        n = 0
        for pid, pl in enumerate(self.pl):
            kind = PLACE_KINDS[pl['kind']]
            if kind in ('door', 'landmark'):
                continue
            for (x, z, yaw) in pl['spots']:
                pk = owner.get((math.floor(x), math.floor(z)))
                if pk is not None:
                    n += 1
                    if kind not in allowed.get(pk, ()):
                        errs.append(f'place {pid} ({kind}) spot ({x},{z}) is in a {pk}')
        # keep-right lanes
        grid = defaultdict(list)
        for (x, z), pk in owner.items():
            grid[(x // 8, z // 8)].append((x + 0.5, z + 0.5))
        bad = 0
        for e, (a, b, m, sec, tag) in enumerate(C.edges):
            lane = o['edgeLane'][e]
            if m == 3:
                continue
            na, nb = C.nodes[a], C.nodes[b]
            ax, az, bx, bz = na[0], na[2], nb[0], nb[2]
            L = math.hypot(bx - ax, bz - az)
            if L < 0.01:
                continue
            px, pz = -(bz - az) / L, (bx - ax) / L
            near = []
            for gx in range(int(math.floor(min(ax, bx) / 8)) - 1, int(math.floor(max(ax, bx) / 8)) + 2):
                for gz in range(int(math.floor(min(az, bz) / 8)) - 1, int(math.floor(max(az, bz) / 8)) + 2):
                    near += grid.get((gx, gz), [])
            off = lane / 10.0
            for sgn in (1, -1):
                seg = (ax + px * off * sgn, az + pz * off * sgn, bx + px * off * sgn, bz + pz * off * sgn)
                if any(City.seg_dist(seg, ox, oz) <= 0.8 - 1e-9 for ox, oz in near):
                    bad += 1
                    errs.append(f'edge {e} lane {lane} passes within 0.8 of a prop cell')
                    break
        return self.rec('11 props', not errs, f'{len(owner)} prop cells; {n} spots inside the cells props define; lanes clear of every prop cell' +
                        ('; ' + '; '.join(errs[:6]) + (f' (+{len(errs) - 6})' if len(errs) > 6 else '') if errs else ''))


def stats(out, C, P=None):
    if P is not None:
        pushed = sorted(q['pushed'] for q in P.cits if q['pushed'] > 0)
        print(f'-- starts pushed back to fit a walk: {len(pushed)} citizens, the most {pushed[-1] if pushed else 0} ticks, '
              f'median {pushed[len(pushed) // 2] if pushed else 0}')
    ct = [dict(zip(out['citizens']['fields'], r)) for r in out['citizens']['rows']]
    pl = [dict(zip(out['places']['fields'], r)) for r in out['places']['rows']]
    ck = Checker(out, C)
    if P is not None:
        ck.quiet = True
        ck.life(P.B)
        ck.quiet = False
    print('-- per job: count, mean / max home -> work walking distance (blocks)')
    by = defaultdict(list)
    for q in ct:
        by[JOBS[q['job']]].append(q)
    for j in JOBS:
        ds = []
        for q in by[j]:
            w = q['work']
            if w < 0:
                continue
            sched = out['schedules'][q['id']]
            if j == 'porter':
                ds.append(ck.wd(q['home'], next(g[2] for g in sched if ACTS[g[1]] == 'carry')))
            elif any(ACTS[g[1]] == 'round' for g in sched):       # a route: the nearest of its places
                ds.append(min(ck.wd(q['home'], p) for p in out['routes'][w]['places']))
            else:
                ds.append(ck.wd(q['home'], w))
        if ds:
            print(f'   {j:13s} {len(by[j]):3d}   mean {sum(ds) / len(ds):6.1f}   max {max(ds):6.1f}')
        else:
            print(f'   {j:13s} {len(by[j]):3d}   (no workplace)')
    sizes = Counter(len(h['members']) for h in out['households'])
    print('-- households by size:', ', '.join(f'{k}: {v}' for k, v in sorted(sizes.items())), f'(total {len(out["households"])})')
    kinds = Counter(PLACE_KINDS[p['kind']] for p in pl)
    print('-- places by kind:', ', '.join(f'{k} {v}' for k, v in kinds.items()), f'(total {len(pl)})')
    rk = Counter(r['kind'] for r in out['routes'])
    print('-- routes by kind:', ', '.join(f'{k} {v}' for k, v in rk.items()), f'(total {len(out["routes"])})')
    segs = Counter(len(s_) for s_ in out['schedules'])
    print('-- schedule segments per citizen:', dict(sorted(segs.items())))
    # M4 B3a: where the crowd is
    dn = {d['id']: d['key'] for d in C.P['districts']}
    order = [dn[i] for i in sorted(dn)]
    homes = Counter(dn[pl[q['home']]['district']] for q in ct)
    print('-- homes by district:', ', '.join(f'{k} {homes[k]}' for k in order), f'(households on a plot: {sum(1 for h in out["households"] if h["plot"] >= 0)})')
    print('-- the three kinds of resident: ' + ', '.join(f'{k} {n}' for k, n in Counter(q.get("kind") for q in P.cits if q["job"] == "resident").items()) if P is not None else '')
    ck.pl = pl
    for tod, label in ((0, '06:00'), (6000, '12:00'), (12000, '18:00'), (14000, '20:00'), (16000, '22:00')):
        outd, ins = Counter(), 0.0
        for q in ct:
            o_, wp = ck.where(q, tod)
            for p_, wt in wp:
                if o_:
                    outd[dn[pl[p_]['district']]] += wt
                else:
                    ins += wt
        print(f'-- {label} (tod {tod}) outdoors by district: ' + ', '.join(f'{k} {outd[k]:.0f}' for k in order) + f'; outdoors {sum(outd.values()):.0f}, indoors {ins:.0f}')
    if P is not None:
        B = P.B
        print('-- noon in the Market district (outdoors):', f'{ck.noon_market:.0f}' if hasattr(ck, 'noon_market') else '?',
              '| 20:00 standing at tavern places:', ck.tavern_2000 if hasattr(ck, 'tavern_2000') else '?')
        print('-- places from the street life: stalls', len(B.stalls), f'({len(B.square_stalls)} on the square), benches', len(B.bench_places),
              'wells', len(B.well_places), 'fountain rims', len(B.fountain_places), 'steps', len(B.step_places), 'notice boards', len(B.notice_places),
              'tavern table places', sum(1 for t in B.taverns if t['table']), 'tavern lane overflow', sum(1 for t in B.taverns if t.get('more', -1) >= 0),
              'docks', len(B.quay_pickups), '| routes: market', len(B.market_routes), 'stroll', len(B.stroll_routes), 'dock 1')


def run_checks(text, out, C, B=None):
    ck = Checker(out, C)
    ck.counts()
    ck.places()
    ck.reach()
    ck.feasible()
    ck.caps()
    ck.lamps()
    text2, _, _ = generate()
    stale = ''
    ok7 = text2 == text
    if os.path.exists(OUT) and open(OUT, encoding='utf-8').read() != text:
        stale = '; the file on disk differs (run without arguments to rewrite it)'
        ok7 = False
    ck.rec('7 determinism', ok7, f'two generations {"identical" if text2 == text else "DIFFER"} ({len(text)} bytes){stale}')
    size = len(text.encode('utf-8'))
    ck.rec('8 size', size < 400 * 1024, f'{size / 1024:.1f} KB (limit 400 KB)')
    ck.structure()
    ck.life(B)
    ck.props()
    return all(ok for _, ok, _ in ck.results)


def preview(out, C):
    from PIL import Image, ImageDraw
    os.makedirs(BUILD, exist_ok=True)
    S = 3
    img = Image.new('RGB', (N * S, N * S), (60, 64, 60))
    px = img.load()
    cols = {PLOT: (98, 98, 98), AVENUE: (170, 170, 170), STREET: (150, 150, 150), LANE: (132, 132, 132), ALLEY: (118, 118, 118),
            SQUARE: (180, 180, 176), PLAZA: (170, 170, 166), QUAY: (140, 142, 146), RIVER: (40, 56, 80), CANAL: (44, 62, 88),
            WALL: (84, 84, 88), LANDMARK: (110, 104, 92), STAIR: (190, 190, 190), TRAINING: (140, 136, 120), RING: (142, 142, 142),
            LOWQUAY: (100, 104, 110), RACE: (44, 62, 88), WATERGATE: (44, 62, 88), YARD: (74, 78, 72), GARDEN: (62, 82, 62)}
    for j in range(N):
        for i in range(N):
            cl = cols.get(int(C.kind[j, i]))
            if cl:
                for a in range(S):
                    for b in range(S):
                        px[i * S + a, j * S + b] = cl
    d = ImageDraw.Draw(img)
    pl = [dict(zip(out['places']['fields'], r)) for r in out['places']['rows']]
    ct = [dict(zip(out['citizens']['fields'], r)) for r in out['citizens']['rows']]

    def P(x, z):
        return ((x + O) * S, (z + O) * S)
    rcol = {'lamps': (255, 220, 60), 'patrol': (255, 90, 90), 'scout': (255, 140, 40), 'crier': (210, 90, 255), 'courier': (90, 220, 255),
            'errand': (90, 255, 140), 'play': (255, 150, 220), 'drops': (255, 255, 255), 'sit': (160, 255, 160),
            'market': (255, 120, 40), 'stroll': (120, 200, 255), 'dock': (255, 255, 120)}
    for r in out['routes']:
        if r['kind'] in ('errand', 'sit', 'drops', 'stroll'):
            continue
        pts = [P(pl[p]['x'], pl[p]['z']) for p in r['places']]
        if len(pts) > 1:
            d.line(pts + ([pts[0]] if r['kind'] in ('patrol', 'scout', 'crier', 'courier') else []), fill=rcol[r['kind']], width=1)
    jcol = [(255, 220, 60), (230, 170, 60), (90, 160, 255), (255, 120, 120), (250, 240, 200), (200, 120, 60), (255, 70, 70),
            (210, 90, 255), (60, 200, 220), (120, 180, 255), (170, 150, 120), (90, 220, 255), (240, 200, 120), (255, 160, 60),
            (140, 255, 160), (180, 255, 220), (255, 255, 255), (220, 220, 120), (255, 150, 220), (170, 170, 170)]
    for q in ct:
        x, z = pl[q['home']]['x'], pl[q['home']]['z']
        cx, cz = P(x, z)
        d.ellipse([cx - 2, cz - 2, cx + 2, cz + 2], fill=jcol[q['job']])
    skip = {'door', 'landmark', 'lamp'}
    for p in pl:
        k = PLACE_KINDS[p['kind']]
        if k == 'lamp':
            cx, cz = P(p['x'], p['z'])
            d.ellipse([cx - 1, cz - 1, cx + 1, cz + 1], fill=(255, 255, 0))
        elif k not in skip:
            cx, cz = P(p['x'], p['z'])
            d.rectangle([cx - 3, cz - 3, cx + 3, cz + 3], outline=(255, 255, 255), fill=None)
    path = os.path.join(BUILD, 'citizens-top.png')
    img.save(path)
    return path


def main(argv):
    cmd = argv[1] if len(argv) > 1 else 'write'
    text, out, (C, B, P) = generate()
    if cmd == 'check':
        ok = run_checks(text, out, C, B)
        stats(out, C, P)
        print('all checks pass' if ok else 'CHECKS FAILED')
        return 0 if ok else 1
    if cmd not in ('write', 'preview'):
        print(f'unknown command {cmd}')
        return 2
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        f.write(text)
    print(f'wrote {os.path.relpath(OUT, REPO)} ({len(text.encode("utf-8")) / 1024:.1f} KB)')
    stats(out, C, P)
    if cmd == 'preview':
        print('preview', os.path.relpath(preview(out, C), REPO))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
