"""loop_seams.py [--against REV]: reads every hero rig the game ships (the generated *Data.java text blocks and the kit
rigs under assets/awaken/kit/rigs) and says whether each looping track is written with no seam: one key before its
start and one after its end, the loop carried on a step either way (fauna_rig.sealed, SPEC §60.9). A track with nothing
to seal is counted by its reason. It also measures how far each sealed loop now moves from the same loop played with
its ends clamped (what the game did before), so the size of the change can be read without a film.

--against REV (a git revision, say HEAD) proves the seal changed nothing else: every line that is not a track is the
same as at REV, every track that does not loop is the same, and every looping track is the old keys, untouched, with
at most the two guard keys round them.

A looping track with two keys at one instant is a different fault, older than the seal (two calls keyed one bone in one
clip): it is counted and named (--twice lists every one), never sealed, and must be as it was.

Run by hand from the repository root: python3 tools/art/loop_seams.py --against HEAD. Standalone: no imports of the specs."""
import os
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
EPS = 2e-4      # (the data is written to four decimals)
# the rig format's lines (HeroRig.parse). The Java text blocks round them are cut by size, so a longer track line moves
# a block's end: the wrapper's own lines are not compared.
DATA = ("rig", "bone", "cube", "look", "move", "spring", "clip", "track")
TWICE = "two keys at one instant (left as it was)"


def files():
    out = subprocess.run(["git", "-C", ROOT, "grep", "-l", "-E", "^ *track [A-Za-z0-9_]+ (rot|pos|scale) ", "--", "src/main"],
                         capture_output=True, text=True).stdout.split("\n")
    return sorted(f for f in out if f)


def parse(text):
    """[(kind, payload)] in file order, the rig data only. kind 'track': (rig, clip, length, loop, bone, target,
    [(t, x, y, z, cat)], its tokens); any other line of the format: the line as written."""
    rows, rig, clip = [], "?", ("?", 0.0, False)
    for raw in text.split("\n"):
        line = raw.strip()
        a = line.split(" ")
        if a[0] == "rig" and len(a) > 3:
            rig = a[1]
        if a[0] == "clip" and len(a) == 4:
            clip = (a[1], float(a[2]), a[3] == "1")
        if a[0] == "track" and len(a) > 3 and a[2] in ("rot", "pos", "scale"):
            n = int(a[3])
            v = a[4:4 + 5 * n]
            keys = [(float(v[5 * i]), float(v[5 * i + 1]), float(v[5 * i + 2]), float(v[5 * i + 3]), v[5 * i + 4] == "1") for i in range(n)]
            rows.append(("track", (rig, clip[0], clip[1], clip[2], a[1], a[2], keys, v)))
        elif a[0] in DATA:
            rows.append(("line", line))
    return rows


def state(length, keys):
    """How a looping track stands: 'sealed', a reason it needs no seal, or 'SEAM' (it should have been sealed)."""
    n = len(keys)
    if any(keys[i + 1][0] - keys[i][0] < EPS for i in range(n - 1)):
        return TWICE
    if n >= 4 and keys[0][0] < -EPS and keys[-1][0] > length + EPS:
        inner = keys[1:-1]
        turn = [inner[-1][i] - inner[0][i] for i in (1, 2, 3)]
        (tb, ta) = (inner[-2], inner[1])
        want = ((tb[0] - length, [tb[i] - turn[i - 1] for i in (1, 2, 3)]), (ta[0] + length, [ta[i] + turn[i - 1] for i in (1, 2, 3)]))
        for got, (t, v) in zip((keys[0], keys[-1]), want):
            if abs(got[0] - t) > EPS or any(abs(got[i + 1] - v[i]) > EPS for i in range(3)):
                return "SEAM (its guard keys are not the loop carried on)"
        return "sealed"
    if n < 2:
        return "one key"
    if abs(keys[0][0]) > EPS or abs(keys[-1][0] - length) > EPS:
        return "does not span the loop"
    if n == 2 and all(abs(keys[0][i] - keys[1][i]) <= EPS for i in (1, 2, 3)):
        return "held still"
    if n == 2 and not keys[1][4]:
        return "a straight line"
    return "SEAM"


def closes(keys, target):
    """How a sealed track's loop ends against its start: 'closed', 'spins' (a rotation a whole number of turns on, a
    wheel) or 'open' (it ends somewhere else: right for a part with symmetry that turns part of the way, a pop otherwise)."""
    inner = keys[1:-1]
    turn = [inner[-1][i] - inner[0][i] for i in (1, 2, 3)]
    if all(abs(d) <= 2 * EPS for d in turn):
        return "closed"
    if target == "rot" and all(abs(d / 360.0 - round(d / 360.0)) <= 1e-3 for d in turn):
        return "spins"
    return "open"


def catmull(a, p0, p1, p2, p3):
    return 0.5 * (2 * p1 + (p2 - p0) * a + (2 * p0 - 5 * p1 + 4 * p2 - p3) * a * a + (3 * p1 - p0 - 3 * p2 + p3) * a * a * a)


def play(keys, t):
    """The game's sampler (vanilla KeyframeAnimation): the first key at or after t ends the segment; ends clamp."""
    n = len(keys)
    idx = n
    for i, k in enumerate(keys):
        if t <= k[0]:
            idx = i
            break
    prev = max(0, idx - 1)
    nxt = min(n - 1, prev + 1)
    k0, k1 = keys[prev], keys[nxt]
    a = 0.0 if nxt == prev else max(0.0, min(1.0, (t - k0[0]) / (k1[0] - k0[0])))
    if k1[4]:
        p0, p3 = keys[max(0, prev - 1)], keys[min(n - 1, nxt + 1)]
        return [catmull(a, p0[i], k0[i], k1[i], p3[i]) for i in (1, 2, 3)]
    return [k0[i] + (k1[i] - k0[i]) * a for i in (1, 2, 3)]


def moved(length, keys):
    """The furthest the sealed loop is from the same keys played with clamped ends, over the whole loop."""
    inner = keys[1:-1]
    worst = 0.0
    steps = 200
    for s in range(steps + 1):
        t = length * s / steps
        a, b = play(keys, t), play(inner, t)
        worst = max(worst, max(abs(a[i] - b[i]) for i in range(3)))
    return worst


def main(argv):
    against = argv[argv.index("--against") + 1] if "--against" in argv else None
    tally, wrong, biggest, twice, spins, opened = {}, [], {"rot": [], "pos": [], "scale": []}, [], 0, []
    loops = changed = same = 0
    for f in files():
        new = parse(open(os.path.join(ROOT, f)).read())
        old = None
        if against:
            got = subprocess.run(["git", "-C", ROOT, "show", f"{against}:{f}"], capture_output=True, text=True)
            if got.returncode == 0:
                old = parse(got.stdout)
                if len(old) != len(new):
                    wrong.append(f"{f}: {len(old)} lines at {against}, {len(new)} now")
                    old = None
        for i, (kind, row) in enumerate(new):
            was = old[i] if old else None
            if kind == "line":
                if was and (was[0] != "line" or was[1] != row):
                    wrong.append(f"{f}: line {i + 1} changed: {row.strip()[:80]}")
                continue
            rig, clip, length, loop, bone, target, keys, toks = row
            name = f"{rig}/{clip}/{bone}.{target}"
            if was:
                if was[0] != "track" or was[1][:6] != row[:6]:
                    wrong.append(f"{f}: {name}: not the track it was")
                else:
                    otoks = was[1][7]
                    if otoks == toks:
                        same += 1
                    elif loop and toks[5:-5] == otoks:
                        changed += 1
                    else:
                        wrong.append(f"{f}: {name}: its own keys changed")
            if not loop:
                continue
            loops += 1
            st = state(length, keys)
            tally[st] = tally.get(st, 0) + 1
            if st.startswith("SEAM"):
                wrong.append(f"{f}: {name}: {st}")
            elif st == TWICE:
                twice.append(name)
            elif st == "sealed":
                biggest[target].append((moved(length, keys), name))
                end = closes(keys, target)
                spins += end == "spins"
                if end == "open":
                    opened.append(name)
    print(f"{loops} looping tracks in {len(files())} files")
    for st in sorted(tally, key=lambda k: -tally[k]):
        print(f"  {tally[st]:6d}  {st}")
    for target, unit in (("rot", "degrees"), ("pos", "pixels"), ("scale", "of its size")):
        top = sorted(biggest[target], reverse=True)[:5]
        if top:
            print(f"furthest a sealed {target} track moves from its old curve ({unit}): " + ", ".join(f"{name} {d:.2f}" for d, name in top))
    print(f"{spins} sealed tracks are parts that spin (a whole number of turns a loop: they keep turning across the seam)")
    if opened:
        print(f"{len(opened)} sealed tracks end somewhere other than where they start (a part with symmetry that turns part of the "
              f"way a loop, a four-toothed cog a quarter turn, is right; anything else pops as the loop comes round, as it did "
              f"before): " + ", ".join(opened[:12]) + (" ..." if len(opened) > 12 else ""))
    if twice:
        rigs = sorted({n.split("/")[0] for n in twice})
        print(f"{len(twice)} looping tracks have two keys at one instant, in {len(rigs)} rigs (a fault of their own, older than the seal: "
              f"two calls keyed one bone in one clip, so the part jumps between the two at every key): " + ", ".join(rigs))
        if "--twice" in argv:
            for n in twice:
                print("  ", n)
    if against:
        print(f"against {against}: {changed} looping tracks gained their two guard keys, {same} tracks are as they were")
    for w in wrong[:60]:
        print("WRONG", w)
    if len(wrong) > 60:
        print(f"... and {len(wrong) - 60} more")
    print("nothing wrong" if not wrong else f"{len(wrong)} wrong")
    return 1 if wrong else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
