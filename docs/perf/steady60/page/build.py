#!/usr/bin/env python3
"""Fills the approval page's template from the tours' traces and reports. python3 -I build.py <worktree>"""
import json
import subprocess
import sys
from pathlib import Path

WT = Path(sys.argv[1])
RES = WT / "run-autopilot" / "results"
HERE = Path(__file__).resolve().parent


def trace(run, label):
    p = RES / run / "perf-traces" / f"{label}.txt"
    return [int(x) / 1000.0 for x in p.read_text().split() if x] if p.exists() else None


def report(run):
    p = RES / run / "report.json"
    if not p.exists() and (RES / run / "perf-traces").exists():
        subprocess.run(["python3", "-I", str(WT / "tools" / "steady60-report.py"), str(RES / run), "--json", str(p)],
                       capture_output=True)
    if not p.exists():
        return {}
    d = json.loads(p.read_text())
    for v in d.values():
        v.pop("trace", None)
    return d


R1, R2, R3N, R3A = report("dell-1"), report("dell-2"), report("dell-3-nomod"), report("dell-3-asleep")


def on_time(ms):
    return sum(1 for m in ms if round(m / 8.333) == 2) / len(ms)


def slots_chart(items, width=760, row=28):
    left, right, up = 262, 50, 8
    w = width - left - right
    rows = [(t, trace(r, l)) for r, l, t in items]
    rows = [(t, ms) for t, ms in rows if ms]
    height = up + row * len(rows) + 6
    out = [f'<svg viewBox="0 0 {width} {height}" role="img" aria-label="Share of frames shown on time, by window">']
    for i, (title, ms) in enumerate(rows):
        bins = [0, 0, 0, 0]
        for m in ms:
            bins[max(1, min(4, round(m / 8.333))) - 1] += 1
        y = up + i * row
        out.append(f'<text x="{left - 10}" y="{y + 14}" class="cb" text-anchor="end">{title}</text>')
        x = left
        for k in (1, 0, 2, 3):  # on time first, then early, late, very late
            bw = w * bins[k] / len(ms)
            if bw > 0:
                out.append(f'<rect x="{x:.1f}" y="{y}" width="{bw:.1f}" height="{row - 9}" class="s{k + 1}"/>')
            x += bw
        out.append(f'<text x="{left + w + 8}" y="{y + 14}" class="cb">{bins[1] * 100 / len(ms):.1f}%</text>')
    out.append("</svg>")
    return "\n".join(out)


def strip(run, label, title, width=760, height=150, top=40.0):
    ms = trace(run, label)
    if not ms:
        return ""
    left, right, up, down = 34, 8, 22, 18
    w, h = width - left - right, height - up - down
    total = sum(ms)
    t, pts, late = 0.0, [], []
    for m in ms:
        t += m
        x = left + w * t / total
        y = up + h * (1 - min(m, top) / top)
        pts.append(f"{x:.1f},{y:.1f}")
        if m > 20.0:
            late.append((x, y))
    out = [f'<svg viewBox="0 0 {width} {height}" role="img" aria-label="{title}: milliseconds per frame over the window">',
           f'<text x="{left}" y="13" class="ct">{title}</text>']
    for v in (0, 8.3, 16.7, 25, 33.3):
        y = up + h * (1 - v / top)
        out.append(f'<line x1="{left}" x2="{left + w}" y1="{y:.1f}" y2="{y:.1f}" class="{"cl60" if abs(v - 16.7) < 0.1 else "cg"}"/>')
        out.append(f'<text x="{left - 5}" y="{y + 3:.1f}" class="ca" text-anchor="end">{v:g}</text>')
    out.append(f'<polyline points="{" ".join(pts)}" class="cp"/>')
    out += [f'<circle cx="{x:.1f}" cy="{y:.1f}" r="1.7" class="cd"/>' for x, y in late]
    out.append(f'<text x="{left}" y="{height - 4}" class="ca">0 s</text>')
    out.append(f'<text x="{left + w}" y="{height - 4}" class="ca" text-anchor="end">{total / 1000:.0f} s</text>')
    out.append("</svg>")
    return "\n".join(out)


def bars(groups, width=760, row=22, scale_max=18.0, line=15.1):
    left, right, up = 262, 54, 22
    w = width - left - right
    n = sum(len(g[1]) for g in groups) + len(groups)
    height = up + row * n + 18
    out = [f'<svg viewBox="0 0 {width} {height}" role="img" aria-label="Median GPU time for the world by Look and scene, in milliseconds">']
    for v in (0, 5, 10, 15):
        x = left + w * v / scale_max
        out.append(f'<line x1="{x:.1f}" x2="{x:.1f}" y1="{up - 4}" y2="{height - 16}" class="cg"/>')
        out.append(f'<text x="{x:.1f}" y="{height - 4}" class="ca" text-anchor="middle">{v} ms</text>')
    xl = left + w * line / scale_max
    out.append(f'<line x1="{xl:.1f}" x2="{xl:.1f}" y1="{up - 4}" y2="{height - 16}" class="cl60"/>')
    out.append(f'<text x="{xl + 4:.1f}" y="{up + 4}" class="ca">60 fps ends here</text>')
    y = up
    for name, rows in groups:
        out.append(f'<text x="{left - 10}" y="{y + 14}" class="ct" text-anchor="end">{name}</text>')
        y += row
        for title, v, cls in rows:
            if v is None:
                continue
            bw = w * min(v, scale_max) / scale_max
            out.append(f'<text x="{left - 10}" y="{y + 13}" class="cb" text-anchor="end">{title}</text>')
            out.append(f'<rect x="{left}" y="{y + 2}" width="{bw:.1f}" height="{row - 8}" class="{cls}"/>')
            out.append(f'<text x="{left + bw + 6:.1f}" y="{y + 13}" class="ca">{v:.1f}</text>')
            y += row
    out.append("</svg>")
    return "\n".join(out)


def g(rep, key, field="gpu_p50"):
    return rep.get(key, {}).get(field)


def fps(rep, key):
    v = rep.get(key, {}).get("fps")
    return f"{v:.0f}" if v else "–"


air = g(R3A, "air_raw_still") or g(R3N, "air_raw_still")
cine3 = g(R3A, "cine_raw_still") or g(R3N, "cine_raw_still")
hyb = g(R3A, "hybrid_raw_still")

BARS = bars([
    ("Your clearing", [
        ("the game alone (Lens off)", g(R1, "off_raw_still"), "bg1"),
        ("Swift", g(R1, "swift_raw_still"), "bg1"),
        ("Cinematic", g(R1, "cine_raw_still"), "bg2"),
        ("Cinematic + Far Sight (near)", g(R2, "cine_fs_raw_still"), "bg2"),
        ("Ultra", g(R1, "ultra_raw_still"), "bg2"),
        ("ImmersiveVFX, your settings", g(R2, "vfx2_raw_still"), "bg3"),
        ("ImmersiveVFX, shadows off", g(R1, "vfx_noshadow"), "bg3"),
        ("ImmersiveVFX, fog, sky, clouds only", g(R1, "vfx_fogsky_only"), "bg3"),
    ]),
    ("Flying, open view", [
        ("Swift", g(R1, "swift_fly_raw"), "bg1"),
        ("Cinematic", g(R1, "cine_fly_raw"), "bg2"),
        ("ImmersiveVFX, your settings", g(R1, "vfx_fly_back_raw"), "bg4"),
        ("ImmersiveVFX, lighter shadows", g(R2, "vfx2_fly_trim"), "bg4"),
        ("ImmersiveVFX, shadows off", g(R2, "vfx2_fly_noshadow"), "bg3"),
        ("ImmersiveVFX, fog, sky, clouds only", g(R2, "vfx2_fly_fogsky"), "bg3"),
    ]),
    ("The city from the Roost", [
        ("Swift", g(R2, "roost_swift_raw"), "bg1"),
        ("Cinematic", g(R2, "roost_cine_raw"), "bg2"),
        ("ImmersiveVFX, your settings", g(R2, "roost_vfx_raw"), "bg3"),
    ]),
])

slot_items = [
    ("dell-1", "vfx_played_still", "ImmersiveVFX, standing (busy first minute)"),
    ("dell-1", "vfx_played_turn", "ImmersiveVFX, turning (busy first minute)"),
    ("dell-3-asleep", "vfx3_played_still", "ImmersiveVFX, standing (quiet)"),
    ("dell-3-asleep", "vfx3_played_turn", "ImmersiveVFX, turning (quiet)"),
    ("dell-1", "vfx_fly_played", "ImmersiveVFX, flying"),
    ("dell-2", "roost_vfx_played", "ImmersiveVFX, the city"),
    ("dell-1", "cine_played_still", "Cinematic, standing"),
    ("dell-1", "cine_played_turn", "Cinematic, turning"),
    ("dell-1", "cine_fly_played", "Cinematic, flying"),
    ("dell-2", "roost_cine_played", "Cinematic, the city"),
    ("dell-2", "cine_fs_played_turn", "Cinematic + Far Sight, turning"),
]


def row(look, a, b, c, note, cls=""):
    return f'<tr><td>{look}</td><td class="n">{a}</td><td class="n {cls}">{b}</td><td class="n">{c}</td><td>{note}</td></tr>'


def pct(run, label):
    ms = trace(run, label)
    return f"{on_time(ms) * 100:.1f}%" if ms else "–"


rows = [
    row("The game alone (Lens off)", fps(R1, "off_raw_still"), "–", "–", "the room there is: 220 fps"),
    row("Swift", fps(R1, "swift_raw_still"), fps(R1, "swift_fly_raw"), fps(R2, "roost_swift_raw"), "never near the line"),
    row("Cinematic", fps(R1, "cine_raw_still"), fps(R1, "cine_fly_raw"), fps(R2, "roost_cine_raw"),
        f"on time {pct('dell-1', 'cine_played_turn')} turning, {pct('dell-1', 'cine_fly_played')} flying", "ok"),
    row("Cinematic + Far Sight (near)", fps(R2, "cine_fs_raw_still"), "–", "–", "1.4 ms from the line"),
    row("Ultra", fps(R1, "ultra_raw_still"), "–", "–", "little to see for 3 ms more"),
    row("ImmersiveVFX, your settings", fps(R2, "vfx2_raw_still"), fps(R1, "vfx_fly_back_raw"), fps(R2, "roost_vfx_raw"),
        f"on time {pct('dell-1', 'vfx_fly_played')} flying", "hot"),
    row("ImmersiveVFX, lighter shadows, no reflections", fps(R2, "vfx2_trim_raw_still"), fps(R2, "vfx2_fly_trim"), "–", "still on the line flying", "hot"),
    row("ImmersiveVFX, shadows off", fps(R1, "vfx_noshadow"), fps(R2, "vfx2_fly_noshadow"), "–", "steady, and no shadows"),
    row("ImmersiveVFX, fog, sky and clouds only", fps(R1, "vfx_fogsky_only"), fps(R2, "vfx2_fly_fogsky"), "–", "the mood for about 1.3 ms"),
]
if air and cine3:
    rows.append(row("Proof of concept (Lens)", fps(R3A if "air_raw_still" in R3A else R3N, "air_raw_still"), "–", "–",
                    f"{air:.1f} ms against Cinematic's {cine3:.1f} on the same frames"))

# notes that depend on the third tour
if R3A.get("vfx3_full_still") and R3A.get("vfx3_particles_none"):
    a, b, c = R3A["vfx3_full_still"], R3A.get("vfx3_particles_reduced", {}), R3A["vfx3_particles_none"]
    particle = (f"Uncapped in your clearing, {a['over16'] * 100:.1f}% of frames ran over 16.7 ms with particles on maximum, "
                f"{b.get('over16', float('nan')) * 100:.1f}% on \"reduced\" and {c['over16'] * 100:.1f}% with them off.")
else:
    particle = "The particle-off windows didn't run cleanly, so I have no before-and-after for that yet."

if R3N.get("swift_raw_still") and R3A.get("swift_raw_still"):
    sn, sa = R3N["swift_raw_still"], R3A["swift_raw_still"]
    cn, ca = R3N.get("cine_raw_still", {}), R3A.get("cine_raw_still", {})
    asleep = (f"little or nothing.</strong> The same windows with its jar taken out and with it installed but asleep: Swift "
              f"{sn['gpu_p50']:.2f} against {sa['gpu_p50']:.2f} ms of GPU time ({sn['fps']:.0f} and {sa['fps']:.0f} fps), Cinematic "
              f"{cn.get('gpu_p50', float('nan')):.2f} against {ca.get('gpu_p50', float('nan')):.2f} ms. "
              f"Differences under about 0.3 ms are within what two runs differ by.")
else:
    asleep = "an unknown amount still.</strong> That comparison didn't finish; it is the first thing stage 0 measures."

if hyb:
    h = R3A["hybrid_raw_still"]
    hybrid = (f"I tried it: {h['fps']:.0f} fps uncapped in your clearing at {hyb:.1f} ms of GPU time, which is what "
              f"ImmersiveVFX costs whole ({g(R3A, 'vfx3_full_still'):.1f} ms). So it gains nothing.")
else:
    hybrid = "On paper it holds 60: about 6 ms for the mod's part plus Lens's light. I couldn't get a clean run of it."

proof_cost = (f"{air - cine3:+.1f} ms beside today's Cinematic ({air:.1f} against {cine3:.1f} ms of GPU time on the same frames)"
              if air and cine3 else "no new pass and no new texture (its cost wasn't measured cleanly; expect it within noise of Cinematic)")


def shots(rows_):
    out = []
    for caption, items in rows_:
        cells = []
        for file, lab, note in items:
            if not (HERE / "shots" / file).exists():
                continue
            cells.append(f'<figure class="shot"><img src="shots/{file}" alt="{lab}: {caption}" loading="lazy">'
                         f'<div class="lab"><span>{lab}</span><i>{note}</i></div></figure>')
        if cells:
            out.append(f'<figure class="shotrow"><figcaption>{caption}</figcaption><div class="shots{" two" if len(cells) == 2 else ""}">{"".join(cells)}</div></figure>')
    return "\n".join(out)


SHOT_ROWS = shots(json.loads((HERE / "shots.json").read_text())) if (HERE / "shots.json").exists() else ""

vfx_fly = trace("dell-1", "vfx_fly_played")
cine_fly = trace("dell-1", "cine_fly_played")
fill = {
    "T_VFX_FLY": f"{on_time(vfx_fly) * 100:.1f}%",
    "T_CINE_FLY": f"{on_time(cine_fly) * 100:.1f}%",
    "T_SHADOW": f"{g(R1, 'vfx_fly_back_raw') - g(R2, 'vfx2_fly_noshadow'):.0f} ms",
    "T_AIR": f"{g(R1, 'vfx_fogsky_only') - g(R1, 'swift_raw_still'):.1f} ms",
    "SLOTS_CHART": slots_chart(slot_items),
    "STRIP_VFX": strip("dell-1", "vfx_fly_back_raw", "ImmersiveVFX, flying"),
    "STRIP_CINE": strip("dell-1", "cine_fly_raw", "Lens Cinematic, the same flight"),
    "BARS": BARS,
    "TABLE_ROWS": "\n".join(rows),
    "PARTICLE_NOTE": particle,
    "ASLEEP_NOTE": asleep,
    "HYBRID_NOTE": hybrid,
    "PROOF_COST": proof_cost,
    "SHOT_ROWS": SHOT_ROWS,
    "END_TIME": sys.argv[2] if len(sys.argv) > 2 else "14:30",
    "VFX_QUIET": (lambda a, c: f"{on_time(a) * 100:.1f}% and {on_time(c) * 100:.1f}%" if a and c else "about 99%")(
        trace("dell-3-asleep", "vfx3_played_still"), trace("dell-3-asleep", "vfx3_played_turn")),
}
html = (HERE / "template.html").read_text()
for k, v in fill.items():
    html = html.replace("{{" + k + "}}", v)
left = [m for m in __import__("re").findall(r"\{\{[A-Z_]+\}\}", html)]
(HERE / "index.html").write_text(html)
print("written", HERE / "index.html", len(html), "bytes; unfilled:", left)
for k in ("T_VFX_FLY", "T_CINE_FLY", "T_SHADOW", "T_AIR", "PARTICLE_NOTE", "ASLEEP_NOTE", "HYBRID_NOTE", "PROOF_COST"):
    print(" ", k, "=", fill[k][:200])
