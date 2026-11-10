"""Disposable: reads a take of the baseline film (tools/autopilot/arms-feel-baseline.txt; ArmsFilm's track.tsv) and
prints what the page needs: the frames at each part of a strike, how fast the guard rises and drops, the swap's dip."""
import csv, math, os, sys

def load(d):
    rows = list(csv.DictReader(open(os.path.join(d, "track.tsv")), delimiter="\t"))
    for r in rows:
        for k, v in r.items():
            if k != "move":
                try: r[k] = float(v)
                except (TypeError, ValueError): pass
    return rows

def tip(r):  # the main blade's tip on the screen (view space to ndc at the hands' 70 degrees, 16:9)
    z = -r["m_tz"]
    if not r["m_has"] or z <= 1e-3: return None
    f = 1 / math.tan(math.radians(35))
    return (r["m_tx"] / z * f / (16 / 9), r["m_ty"] / z * f)

def strikes(rows):
    """Each move's frames by part: its first frame, the first frozen frame, the last frozen frame, the first still one after."""
    out, cur = [], None
    for i, r in enumerate(rows):
        key = (r["move"], r["step"])
        if r["move"] != "-" and (cur is None or cur["key"] != key or r["raw"] < cur["last_raw"] - 0.5):
            cur = dict(key=key, first=i, hold0=None, hold1=None, last_raw=r["raw"])
            out.append(cur)
        if cur is not None and r["move"] != "-":
            cur["last_raw"] = r["raw"]
            if r["hold"] and cur["hold0"] is None: cur["hold0"] = i
            if r["hold"]: cur["hold1"] = i
            cur["end"] = i
    return out

def pic_near(rows, i):
    best = None
    for j in range(max(0, i - 40), min(len(rows), i + 40)):
        if rows[j]["pic"] >= 0 and (best is None or abs(j - i) < abs(best - i)): best = j
    return None if best is None else (int(rows[best]["pic"]), best)

if __name__ == "__main__":
    d = sys.argv[1]
    rows = load(d)
    print(os.path.basename(d), len(rows), "frames", "pictures", int(max(r["pic"] for r in rows)) + 1, "ticks %.1f to %.1f" % (rows[0]["gt"], rows[-1]["gt"]))
    for s in strikes(rows):
        a = rows[s["first"]]
        line = "  %s step %d: from tick %.2f (frame %d, pic %s)" % (s["key"][0], s["key"][1], a["gt"], s["first"], pic_near(rows, s["first"]))
        if s["hold0"] is not None:
            h0, h1 = rows[s["hold0"]], rows[s["hold1"]]
            line += " | freeze frames %d-%d, move clock %.2f, %.0f ms real, pics %s %s" % (s["hold0"], s["hold1"], h0["view"], h1["real_ms"] - h0["real_ms"], pic_near(rows, s["hold0"]), pic_near(rows, s["hold1"]))
        print(line)
    if "--tips" in sys.argv:
        last = None
        for r in rows:
            t = tip(r)
            if r["pic"] >= 0:
                step = "" if last is None or t is None else " step %.3f" % math.hypot(t[0] - last[0], t[1] - last[1])
                print("   pic %3d tick %7.2f move %-9s clock %5.2f hold %d key %d equip %.2f swap %.2f tip %s%s" % (r["pic"], r["gt"], r["move"], r["view"], r["hold"], r["key"], r["equip"], r["swap"], "-" if t is None else "(%+.2f,%+.2f)" % t, step))
            if t is not None: last = t
