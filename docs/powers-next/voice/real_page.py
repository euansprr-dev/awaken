"""The local listening page for the scores as built (tools/art/specs/power_voices.py): every scored power as one cast, its beats
laid on a timeline the way the game plays them, each set of each beat, all at one shared gain so nothing is flattered.

    python3 docs/powers-next/voice/real_page.py            run-autopilot/powers-next/listen-real/index.html and the clips
    python3 docs/powers-next/voice/real_page.py brand nova  only these powers

Stays under run-autopilot/ (gitignored): the clips are mixed from Minecraft's own recordings, so they are never committed and
never hosted. Each row has Pass and Redo; the marks are kept in the browser and "Copy my marks" puts them on the clipboard.
"""
import html
import json
import os
import random
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(ROOT, "tools/art/specs"))
import voice_mock as V  # noqa: E402
import power_voices as PV  # noqa: E402
import power_ladder_table as LT  # noqa: E402

OUT = os.path.join(ROOT, "run-autopilot/powers-next/listen-real")
CASTS = json.load(open(os.path.join(ROOT, "src/main/resources/data/awaken/powers/casts.json")))["profiles"]
PROFILE = {p["power"]: p for p in CASTS}
NAME = {p["id"]: p["name"] for p in LT.L}
FAMILY = {p["id"]: p["fam"] for p in LT.L}
WORDS = {"gather": "gathering", "peak": "full", "release": "the release", "travel": "in flight", "impact": "the hit", "tail": "after", "echo": "the echo",
         "hold": "held", "end": "the end", "level5": "level 5, under the hit", "extra_a": "its own extra", "extra_b": "its second extra"}


def when(power):
    """The tick each beat sounds on, for one ordinary cast of this power."""
    p = PROFILE.get(power)
    channel = bool(p and p["channel"])
    rel = 20 if channel else (p["release"] if p else 0)
    return {"gather": 0, "peak": max(0, rel - 2) if channel else max(0, rel - 1), "release": rel, "travel": rel + 1, "impact": rel + 2, "tail": rel + 2,
            "echo": rel + 22, "hold": rel + 6, "end": rel + 40, "level5": rel + 2, "extra_a": rel + 30, "extra_b": rel + 36}


def layers(power, which, rng):
    """One cast as voice_mock layers: each beat's set (the first, or the second where a beat has two)."""
    at = when(power)
    out, said = [], []
    for beat, sc in PV.VOICES[power].items():
        if beat == "level5" or not sc["sets"]:
            continue
        st = sc["sets"][min(which, len(sc["sets"]) - 1)]
        said.append(WORDS[beat])
        for layer in st:
            name, tick, vol, pitch = layer[:4]
            jitter = layer[4] if len(layer) > 4 else 0.04
            cut = layer[5] * V.TICK if len(layer) > 5 and layer[5] else None
            files = PV.resolve(PV.PALETTE[name][0])
            f = "minecraft:" + files[rng.randrange(len(files))]
            out.append((f, at[beat] + tick, vol, pitch, jitter) + ((cut,) if cut else ()))
    return out, said


def render(only):
    os.makedirs(OUT, exist_ok=True)
    powers = [p for p in PV.VOICES if not only or p in only]
    clips, facts, said = {}, {}, {}
    for power in powers:
        two = any(len(sc["sets"]) > 1 for sc in PV.VOICES[power].values())
        for which in range(2 if two else 1):
            rng = random.Random(hash((power, which)) & 0xFFFFFF)
            sc, words = layers(power, which, rng)
            if not sc:
                continue
            key = f"{power}_{'ab'[which]}"
            clips[key] = V.mix(sc, 7 + which)
            facts[key] = V.describe(clips[key])
            said[key] = words
    peak = max(float(np.max(np.abs(x))) for x in clips.values())
    gain = 0.97 / peak
    for name, x in clips.items():
        V.encode(x * gain, os.path.join(OUT, name + ".mp3"))
    rows, fam = [], None
    for power in sorted(powers, key=lambda p: (LT.FAMS.index(FAMILY.get(p, "Spirit")), list(PV.VOICES).index(p))):
        if FAMILY.get(power) != fam:
            fam = FAMILY.get(power)
            rows.append(f"<h2>{html.escape(fam or '')}</h2>")
        cols = []
        for which in "ab":
            key = f"{power}_{which}"
            if key not in clips:
                continue
            f = facts[key]
            ring = "a held high tone in its loud part" if f["ding"] else "no held high tone"
            cols.append(f'<div class="take"><button data-src="{key}.mp3">Play {"first" if which == "a" else "second"} set</button>'
                        f'<span class="facts">{f["secs"]:.1f} s · the checker finds {ring}</span></div>')
        beats = ", ".join(said.get(f"{power}_a", []))
        rows.append(f'<section data-power="{power}"><h3>{html.escape(NAME.get(power, power))}</h3><p class="what">{html.escape(beats)}</p>'
                    f'<div class="takes">{"".join(cols)}</div>'
                    f'<div class="marks"><label><input type="radio" name="{power}" value="pass"> Pass</label>'
                    f'<label><input type="radio" name="{power}" value="redo"> Redo</label>'
                    f'<input type="text" placeholder="what is wrong with it" data-note="{power}"></div></section>')
    page = PAGE.replace("{{ROWS}}", "\n".join(rows)).replace("{{GAIN}}", f"{20 * np.log10(gain):+.1f}").replace("{{N}}", str(len(powers)))
    with open(os.path.join(OUT, "index.html"), "w") as fh:
        fh.write(page)
    dings = [k for k, f in facts.items() if f["ding"]]
    print(f"{len(clips)} clips for {len(powers)} powers and index.html -> {os.path.relpath(OUT, ROOT)} (shared gain {20 * np.log10(gain):+.1f} dB)")
    print(f"the ring check flags {len(dings)}: {' '.join(dings)}")


PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Power voices, as built</title><style>
:root{--bg:#f6f4ef;--ink:#1d1b17;--mute:#6b665c;--card:#fff;--line:#ddd7ca;--acc:#b4541a}
@media (prefers-color-scheme:dark){:root{--bg:#161512;--ink:#ece8df;--mute:#a09a8d;--card:#201e1a;--line:#35322b;--acc:#e08a4a}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
main{max-width:860px;margin:0 auto;padding:28px 16px 80px}h1{font-size:28px;margin:0 0 6px}h2{margin:34px 0 8px;font-size:20px;color:var(--acc)}
h3{margin:0;font-size:17px}.lead{color:var(--mute);margin:0 0 18px}section{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px 16px;margin:10px 0}
.what{color:var(--mute);margin:2px 0 10px;font-size:14px}.takes{display:flex;flex-wrap:wrap;gap:10px 18px}.take{display:flex;align-items:center;gap:10px}
button{font:inherit;padding:7px 14px;border-radius:8px;border:1px solid var(--line);background:var(--bg);color:var(--ink);cursor:pointer}
button.on{border-color:var(--acc);color:var(--acc)}.facts{color:var(--mute);font-size:13px}.marks{display:flex;flex-wrap:wrap;gap:8px 16px;align-items:center;margin-top:10px}
.marks input[type=text]{flex:1;min-width:180px;font:inherit;padding:6px 8px;border-radius:8px;border:1px solid var(--line);background:var(--bg);color:var(--ink)}
.bar{position:sticky;bottom:0;background:var(--bg);border-top:1px solid var(--line);padding:10px 16px;display:flex;gap:12px;align-items:center;justify-content:center}
</style></head><body><main>
<h1>Power voices, as built</h1>
<p class="lead">{{N}} powers. Each clip is one ordinary cast, its beats on the ticks the game plays them, mixed here from Minecraft's own recordings
the way the game will layer them. One shared gain for everything ({{GAIN}} dB), so loud is loud. In the game a hit also adds the sound of the
block it lands on, and no two casts pick the same takes. Mark each one, then copy your marks and paste them to me.</p>
{{ROWS}}
</main><div class="bar"><button id="copy">Copy my marks</button><span id="count" class="facts"></span></div>
<script>
const a=new Audio();let on=null;
document.querySelectorAll('button[data-src]').forEach(b=>b.addEventListener('click',()=>{if(on)on.classList.remove('on');if(on===b&&!a.paused){a.pause();on=null;return}
a.src=b.dataset.src;a.currentTime=0;a.play();on=b;b.classList.add('on')}));a.addEventListener('ended',()=>{if(on)on.classList.remove('on');on=null});
const KEY='awaken-power-voices';let m={};try{m=JSON.parse(localStorage.getItem(KEY)||'{}')}catch(e){}
function save(){try{localStorage.setItem(KEY,JSON.stringify(m))}catch(e){}count()}
function count(){const v=Object.values(m);document.getElementById('count').textContent=v.filter(x=>x.mark==='pass').length+' pass, '+v.filter(x=>x.mark==='redo').length+' redo'}
document.querySelectorAll('section[data-power]').forEach(s=>{const p=s.dataset.power;const r=m[p]||{};
s.querySelectorAll('input[type=radio]').forEach(i=>{i.checked=r.mark===i.value;i.addEventListener('change',()=>{m[p]=Object.assign(m[p]||{},{mark:i.value});save()})});
const t=s.querySelector('input[type=text]');t.value=r.note||'';t.addEventListener('input',()=>{m[p]=Object.assign(m[p]||{},{note:t.value});save()})});
document.getElementById('copy').addEventListener('click',()=>{const lines=Object.entries(m).filter(([,v])=>v.mark||v.note).map(([p,v])=>p+': '+(v.mark||'')+(v.note?' ('+v.note+')':''));
navigator.clipboard.writeText(lines.join('\\n')).then(()=>{document.getElementById('copy').textContent='Copied'})});count();
</script></body></html>
"""

if __name__ == "__main__":
    render(set(sys.argv[1:]))
