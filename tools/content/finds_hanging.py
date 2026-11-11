#!/usr/bin/env python3
"""Leaves the world left hanging round a monument (SPEC §64.9): in each world-as-made dump (WORLD=1 tools/content/finds_census.sh),
the clusters of the land's living leaves that touch nothing but air and each other, with how far each is from the middle.
A crown whose trunk the monument took shows here; so does one a road took, and the few the world leaves by itself.

  python3 tools/content/finds_hanging.py run/finds-world/world*.txt
"""
import sys, collections, math
tot = 0
for f in sys.argv[1:]:
    leaves, other = set(), set(); reach = 0
    for line in open(f):
        p = line.split(' ', 4)
        if p[0] == 'K': reach = int(p[3])
        if p[0] != 'B': continue
        n = p[4].split('}')[0]
        pos = (int(p[1]), int(p[2]), int(p[3]))
        if n.endswith('leaves') and 'persistent=true' not in p[4]: leaves.add(pos)      # (kept leaves are a scene's own hedge or drift)
        elif n.endswith('leaves'): other.add(pos)
        elif not n.endswith(':air') and not n.endswith(':snow') and not n.endswith(':vine'): other.add(pos)
    N26 = [(dx, dy, dz) for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1) if (dx, dy, dz) != (0, 0, 0)]
    N6 = [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]
    seen = set(); hang = []
    W = reach + 20
    for s in leaves:
        if s in seen: continue
        comp = [s]; seen.add(s); q = collections.deque([s]); held = False
        while q:
            x, y, z = q.popleft()
            if any((x+dx, y+dy, z+dz) in other for dx, dy, dz in N6): held = True
            if max(abs(x), abs(z)) >= W - 1: held = True      # runs off the dump's edge: its trunk may be past it
            for dx, dy, dz in N26:
                n = (x+dx, y+dy, z+dz)
                if n in leaves and n not in seen: seen.add(n); comp.append(n); q.append(n)
        if not held: hang.append(comp)
    n = sum(len(c) for c in hang); tot += len(hang)
    d = sorted((int(math.hypot(c[0][0], c[0][2])), len(c), c[0]) for c in hang)
    print(f"{f.split('/')[-1][:-4]:28s} reach {reach:2d}: {len(hang):2d} clusters hang ({n} blocks)", [(a, b) for a, b, _ in d][:12], d[0][2] if d else '')
print('clusters in all:', tot)
