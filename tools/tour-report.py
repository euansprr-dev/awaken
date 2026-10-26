#!/usr/bin/env python3
"""Reports one autopilot tour run on a GitHub runner (.github/workflows/tours.yml) and decides whether it passed.

  python3 -I tools/tour-report.py <out-dir>

Copies the shots, the client's log and any crash report into <out-dir> (the run's artifact), writes summary.md (the run
page), summary.txt (what tools/tour-ci.sh prints) and tour.json, and sets the step output verdict=pass|fail. A tour
fails when Gradle or the client failed, the watchdog stopped it, the world never loaded, the game crashed, or it took
no shots. Reads the env the workflow sets: SCRIPT, KNOBS, TOUR_NAME, TOUR_RUNNER, BUILD_SECONDS, TOUR_SECONDS.
"""
import json
import os
import re
import shutil
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GAME = ROOT / "run-autopilot"


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def mins(s):
    try:
        s = int(s)
    except (TypeError, ValueError):
        return "?"
    return f"{s // 60}m{s % 60:02d}s"


def script_shots(script):
    """The names the script asks for (shot/shotnow lines), in order."""
    try:
        lines = (ROOT / script).read_text().splitlines()
    except OSError:
        return []
    return [l.split(None, 1)[1].strip() for l in lines if re.match(r"^\s*(shot|shotnow)\s+\S", l)]


def main(out):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    script = os.environ.get("SCRIPT", "?")
    logs = sorted((ROOT / "logs").glob("gradle-*-runAutopilot.log"), key=lambda p: p.stat().st_mtime)
    log = logs[-1] if logs else None
    text = log.read_text(errors="replace") if log else ""
    lines = text.splitlines()

    problems, warnings = [], []
    m = re.findall(r"^EXIT (\d+)$", text, re.M)
    exit_code = int(m[-1]) if m else None
    if log is None:
        problems.append("no client log: the tour never started (see the Tour step)")
    elif "WATCHDOG" in text:
        problems.append(f"stopped by the watchdog after {os.environ.get('TOUR_TIMEOUT', '?')} s: the script needs a final"
                        " quit, or a longer timeout")
    elif exit_code is None:
        problems.append("Gradle never finished")
    elif exit_code != 0:
        problems.append(f"Gradle exited {exit_code} (a build failure or the client crashed)")
    if "[autopilot] world never became ready" in text:
        problems.append("the world never became ready")
    # the process died under the game (the GPU driver, the JVM): the line that says why
    for l in lines:
        if re.search(r"\*\*\* Terminating app|A fatal error has been detected", l):
            problems.append(l.strip()[:300])
            break
    for l in lines:
        if "Failed to create backend" in l:
            warnings.append(re.sub(r"^\[[0-9:]+\] ", "", l).strip()[:300])

    crashes = sorted((GAME / "crash-reports").glob("*.txt")) if (GAME / "crash-reports").is_dir() else []
    if crashes:
        problems.append(f"the game crashed: {crashes[-1].name}")
        (out / "crash-reports").mkdir(exist_ok=True)
        for c in crashes:
            shutil.copy2(c, out / "crash-reports" / c.name)

    shots = []
    for p in sorted((GAME / "screenshots").glob("*.png")):
        shutil.copy2(p, out / p.name)
        size = png_size(p)
        shots.append({"name": p.name, "size": f"{size[0]}x{size[1]}" if size else "?"})
    if not shots:
        problems.append("no screenshots")
    got = {s["name"][:-4] for s in shots}
    missing = [n for n in script_shots(script) if n not in got]
    if missing:
        warnings.append("asked for but not taken: " + ", ".join(missing))

    backend = re.findall(r"Using graphics backend (\S+), using drivers: (.*)$", text, re.M)
    device = re.findall(r"Using graphics device: (.*)$", text, re.M)
    want = dict(kv.split("=", 1) for kv in os.environ.get("KNOBS", "").split() if "=" in kv).get("BACKEND", "vulkan")
    if backend and want in ("vulkan", "opengl") and backend[-1][0].lower() != want:
        warnings.append(f"asked for {want}, ran on {backend[-1][0]} (the game fell back)")
    info = [l.split("[autopilot] info ", 1)[1] for l in lines if "[autopilot] info " in l]
    perf = [l.split("[autopilot] perf ", 1)[1] for l in lines if "[autopilot] perf " in l]
    sound = "off (no audio device)" if "Error starting SoundSystem" in text else (
        "started" if "Sound engine started" in text else "?")
    for l in lines:
        if re.search(r"/WARN\] \[[^]]*\]: \[autopilot\]", l):
            warnings.append(l.split("[autopilot] ", 1)[1].strip())
    errors = []
    for l in lines:
        if "/ERROR]" in l or "/FATAL]" in l:
            msg = re.sub(r"^\[[0-9:]+\] ", "", l).strip()
            if msg not in errors:
                errors.append(msg)
    for c in [log, GAME / "logs" / "latest.log", GAME / "options.txt", GAME / "config" / "awaken-client.toml"]:
        if c and c.exists():
            shutil.copy2(c, out / ("tour.log" if c == log else c.name))

    verdict = "fail" if problems else "pass"
    report = {
        "schemaVersion": 1, "verdict": verdict, "script": script, "knobs": os.environ.get("KNOBS", ""),
        "runner": os.environ.get("TOUR_RUNNER", "?"), "buildSeconds": os.environ.get("BUILD_SECONDS"),
        "tourSeconds": os.environ.get("TOUR_SECONDS"), "backend": " ".join(backend[-1]) if backend else None,
        "device": device[-1] if device else None, "sound": sound, "info": info, "perf": perf, "shots": shots,
        "problems": problems, "warnings": warnings, "errors": errors[:40],
    }
    (out / "tour.json").write_text(json.dumps(report, indent=1) + "\n")

    name = os.environ.get("TOUR_NAME", Path(script).stem)
    head = (f"{verdict.upper()}: {name} on {report['runner']}, {len(shots)} shots; tour {mins(report['tourSeconds'])},"
            f" build {mins(report['buildSeconds'])}")
    rows = [("script", script), ("knobs", report["knobs"] or "none (Awaken alone, defaults)"),
            ("backend", report["backend"] or "?"), ("device", report["device"] or "?"), ("sound", sound)]
    txt = [head] + [f"  {k}: {v}" for k, v in rows]
    txt += [f"  info: {i}" for i in info]
    txt += [f"  perf (the runner's VM, not a Mac): {p}" for p in perf]
    txt += [f"  PROBLEM: {p}" for p in problems] + [f"  warning: {w}" for w in warnings]
    if errors:
        txt.append(f"  {len(errors)} distinct ERROR lines in tour.log; first: {errors[0][:200]}")
    txt.append("  shots: " + ", ".join(f"{s['name']} {s['size']}" for s in shots))
    (out / "summary.txt").write_text("\n".join(txt) + "\n")

    md = [f"### {head}", "", "| | |", "|---|---|"] + [f"| {k} | `{v}` |" for k, v in rows]
    md += [f"| info | `{i}` |" for i in info]
    md += [f"| perf (runner VM) | `{p}` |" for p in perf]
    md += [""] + [f"- **Problem:** {p}" for p in problems] + [f"- warning: {w}" for w in warnings]
    md += ["", f"**Shots** ({len(shots)}): " + ", ".join(f"`{s['name']}` {s['size']}" for s in shots)]
    if errors:
        md += ["", f"<details><summary>{len(errors)} distinct ERROR lines</summary>", "", "```"]
        md += [e[:300] for e in errors[:40]] + ["```", "</details>"]
    md.append("")
    (out / "summary.md").write_text("\n".join(md))
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write("\n".join(md) + "\n")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            f.write(f"verdict={verdict}\n")
    print("\n".join(txt))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
