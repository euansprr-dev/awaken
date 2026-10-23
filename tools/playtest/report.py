"""Turns <world>/awaken/playtest.jsonl into a short session report (markdown).
Usage: python3 tools/playtest/report.py <path to playtest.jsonl> [out.md]

Measures from docs/plan.html section 6: next-step clarity, fun, surprises, unfair deaths, time between new things."""
import json, sys, statistics
from datetime import datetime

path = sys.argv[1]
out = sys.argv[2] if len(sys.argv) > 2 else None
events = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
sessions, cur = [], None
for e in events:
    if e["type"] == "session_start":
        cur = [e]; sessions.append(cur)
    elif cur is not None:
        cur.append(e)
    if e["type"] == "session_end":
        cur = None

def ts(e): return datetime.fromisoformat(e["t"].replace("Z", "+00:00"))

lines = ["# AWAKEN playtest report", ""]
for i, s in enumerate(sessions, 1):
    dur = (ts(s[-1]) - ts(s[0])).total_seconds() / 60
    by = lambda t: [e for e in s if e["type"] == t]
    prompts, nexts, funs = by("prompt"), by("next"), by("fun")
    new_things = [ts(e) for e in by("new_thing")]
    gaps = [(b - a).total_seconds() / 60 for a, b in zip(new_things, new_things[1:])]
    lines += [f"## Session {i} · {dur:.0f} min · {s[0]['player']}", ""]
    lines.append(f"- Prompts answered with a next step: {len(nexts)} / {len(prompts)}"
                 + (f" ({100*len(nexts)/len(prompts):.0f}%)" if prompts else ""))
    if funs:
        lines.append(f"- Fun: {', '.join(e['score'] for e in funs)} (mean {statistics.mean(int(e['score']) for e in funs):.1f})")
    lines.append(f"- Level-ups: {len(by('level_up'))} · deaths: {len(by('death'))} · flagged unfair: {len(by('unfair_death'))}")
    lines.append(f"- New things: {len(new_things)}" + (f" · median gap {statistics.median(gaps):.0f} min, worst {max(gaps):.0f} min" if gaps else ""))
    lines.append(f"- Surprises noted: {len(by('surprise'))}")
    if nexts:
        lines += ["", "What you said you'd do next:"] + [f"  - {e['answer']}" for e in nexts]
    lines.append("")
text = "\n".join(lines)
if out:
    open(out, "w").write(text)
print(text)
