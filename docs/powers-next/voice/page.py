"""Writes the local listening page (voice_mock.py render): today beside two proposals for six powers, once and three times in
a row, all at one shared gain so nothing is flattered. Stays under run-autopilot/ (gitignored): it holds vanilla audio."""
import html
import json
import os

import numpy as np

import scores as S
import voice_mock as V

GAP = 1.1  # seconds between casts in the "three in a row" files


def three(score, seed):
    parts = [V.mix(score, seed + i) for i in range(3)]
    gap = np.zeros(int(GAP * V.SR))
    return np.concatenate([parts[0], gap, parts[1], gap, parts[2]])


def render():
    out = V.OUT
    os.makedirs(out, exist_ok=True)
    clips, facts = {}, {}
    for p in S.ORDER:
        for tag, src in (("today", S.TODAY[p]), ("a", S.A[p]["score"]), ("b", S.B[p]["score"])):
            clips[f"{p}_{tag}"] = V.mix(src, 11)
            clips[f"{p}_{tag}_x3"] = three(src, 21)
            facts[f"{p}_{tag}"] = V.describe(clips[f"{p}_{tag}"])
    peak = max(float(np.max(np.abs(x))) for x in clips.values())
    gain = 0.97 / peak
    for name, x in clips.items():
        V.encode(x * gain, os.path.join(out, name + ".mp3"))
    rows = []
    for p in S.ORDER:
        def col(tag, title, made):
            f = facts[f"{p}_{tag}"]
            li = "".join(f"<li>{html.escape(m)}</li>" for m in made)
            ring = "a held high tone in its loud part" if f["ding"] else "no held high tone"
            return (f'<div class="col {tag}"><h3>{html.escape(title)}</h3>'
                    f'<div class="btns"><button data-src="{p}_{tag}.mp3">Play</button><button data-src="{p}_{tag}_x3.mp3">Three in a row</button></div>'
                    f'<ul>{li}</ul><p class="facts">{f["secs"]:.1f} s · the checker finds {ring}</p></div>')
        today_made = ["what build plays now: the mod's own files, made by synthesis"]
        rows.append(f'<section><h2>{S.NAMES[p]}</h2><p class="what">{html.escape(S.WHAT[p])}</p><div class="cols">'
                    + col("today", "Today", today_made) + col("a", "New A: " + S.A[p]["title"], S.A[p]["made"]) + col("b", "New B: " + S.B[p]["title"], S.B[p]["made"])
                    + "</div></section>")
    page = PAGE.replace("{{ROWS}}", "\n".join(rows)).replace("{{GAIN}}", f"{20 * np.log10(gain):+.1f}")
    with open(os.path.join(out, "index.html"), "w") as fh:
        fh.write(page)
    json.dump({k: {kk: (float(vv) if not isinstance(vv, bool) else vv) for kk, vv in v.items()} for k, v in facts.items()},
              open(os.path.join(out, "facts.json"), "w"), indent=1)
    print(f"{len(clips)} clips and index.html -> {os.path.relpath(out, V.ROOT)} (shared gain {20 * np.log10(gain):+.1f} dB)")
    for k, f in facts.items():
        print(f"  {k:22s} {f['secs']:4.1f}s loud {f['rms']:6.1f} bright {f['centroid']:5.0f} Hz {'DING %.0f' % f['hz'] if f['ding'] else ''}")


PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Power sounds: today and new</title><style>
:root{--bg:#f6f4ef;--ink:#1d1b17;--mute:#6b665c;--card:#fff;--line:#ddd7ca;--acc:#b4541a;--today:#8a8478}
@media (prefers-color-scheme:dark){:root{--bg:#161512;--ink:#ece8df;--mute:#a09a8d;--card:#201e1a;--line:#35322b;--acc:#e08a4a;--today:#8a8478}}
body{background:var(--bg);color:var(--ink);font:16px/1.5 -apple-system,system-ui,sans-serif;margin:0;padding:24px 16px 80px}
main{max-width:1080px;margin:0 auto}h1{font-size:28px;margin:0 0 6px}h2{font-size:21px;margin:0}h3{font-size:15px;margin:0 0 8px}
.lede{color:var(--mute);max-width:70ch}section{border-top:1px solid var(--line);padding:22px 0}.what{color:var(--mute);margin:2px 0 12px}
.cols{display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:12px}.col{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px}
.col.today{border-left:4px solid var(--today)}.col.a,.col.b{border-left:4px solid var(--acc)}
.btns{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:8px}button{font:inherit;padding:8px 14px;border-radius:8px;border:1px solid var(--line);background:var(--bg);color:var(--ink);cursor:pointer}
button.on{background:var(--acc);color:#fff;border-color:var(--acc)}ul{margin:0;padding-left:18px;font-size:14px;color:var(--mute)}.facts{font-size:12px;color:var(--mute);margin:8px 0 0}
</style></head><body><main><h1>Power sounds: today and new</h1>
<p class="lede">Six powers. For each: what build plays today, then two new versions made only from Minecraft's own recordings, layered the way the game can layer them
(a name, a volume, a pitch and a start time: nothing else). I can't hear, so these are my best guesses and you're the judge. "Three in a row" plays three casts
one after another, so you can hear whether it gets boring. Every file is at the same gain ({{GAIN}} dB), so louder here means louder in game.
This page stays on your Mac because it plays Mojang's audio.</p>
{{ROWS}}
</main><script>
let cur=null,btn=null;document.querySelectorAll('button[data-src]').forEach(b=>b.addEventListener('click',()=>{
 if(cur){cur.pause();if(btn)btn.classList.remove('on');if(btn===b){cur=null;btn=null;return}}
 cur=new Audio(b.dataset.src);btn=b;b.classList.add('on');cur.onended=()=>{b.classList.remove('on');cur=null;btn=null};cur.play()}));
</script></body></html>"""
