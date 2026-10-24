"""The listening sheet for the owner's session on the layered gate music (Awakened Pack stage 4, "The Sound").

    python3 tools/art/audio_listen.py [date]      default: today; writes docs/audio/listening-<date>.html and
                                                   docs/audio/listening-<date>/*.mp3 next to it

For every gate theme (from its manifest, assets/awaken/music_layers/gate/<theme>.json):
  - a "journey" (about 70 s): the stems mixed exactly as the game mixes them (LayerPlayer + StemMixer: each level's
    gains, each stem's attack / duck / release, a Lord's fall letting go in 3 s), walking calm -> tension -> combat ->
    Lord -> the Lord falls -> calm, so one listen answers "does this theme's music rise and fall right?";
  - each stem alone (20 s from the loop's start), to judge a layer by itself.
Every entry has keep / remake / cut and a note field; choices stay in the browser (localStorage) and export as JSON
or as text to paste back. The page works from file:// with plain <audio> elements (no server, no Web Audio fetches).
MP3 (not the game's Ogg Vorbis) so any browser plays it, Safari included.
"""
import datetime
import html
import json
import os
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "specs"))
import audiokit as K  # noqa: E402
import audio_layers as L  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets")
DOCS = os.path.join(ROOT, "docs/audio")

# the journey: (seconds, level, label); "fell" = the Lord falls (the server's victory flag, level drops to tension)
JOURNEY = [(0.0, "calm", "calm (the bed fades in)"), (12.0, "tension", "a monster hunts you"), (24.0, "combat", "blows traded"),
           (38.0, "lord", "the Lord"), (52.0, "fell", "the Lord falls"), (60.0, "calm", "calm again")]
JOURNEY_END = 70.0
VICTORY = 3.0
STEM_EXCERPT = 20.0
LEVELS = ["calm", "tension", "combat", "lord"]


def stem_audio(man):
    out = []
    for s in man["stems"]:
        ns, path = s["file"].split(":", 1)
        out.append(K.stereo(K.read_audio(os.path.join(ASSETS, ns, "sounds", path + ".ogg"))))
    return out


def journey(man, stems):
    """The game's mixer, offline: per-sample linear ramps toward each level's gains at each stem's own speed."""
    n = K.ns(JOURNEY_END)
    loop = len(stems[0])
    reps = n // loop + 1
    tiled = [np.tile(s, (reps, 1))[:n] for s in stems]
    gains = {lv["name"]: lv["gains"] for lv in man["levels"]}
    fades = [(s["attack"], s["duck"], s["release"]) for s in man["stems"]]
    k = len(stems)
    cur = np.zeros(k)
    tgt = np.zeros(k)
    step = np.ones(k)
    env = np.zeros((n, k))
    events = [(K.ns(t), lv) for t, lv, _ in JOURNEY]
    prev = -1
    ei = 0
    block = 64
    for i0 in range(0, n, block):
        while ei < len(events) and events[ei][0] <= i0:
            lv = events[ei][1]
            victory = lv == "fell"
            level = LEVELS.index("tension" if victory else lv)
            g = gains[LEVELS[level]]
            for j in range(k):
                t = g[j]
                a, d, r = fades[j]
                secs = a if t > tgt[j] else (d if prev >= 0 and level > prev else r)
                if victory and t < tgt[j]:
                    secs = min(secs, VICTORY)
                tgt[j] = t
                step[j] = 1.0 / (secs * K.SR)
            prev = level
            ei += 1
        m = min(block, n - i0)
        for j in range(k):
            ramp = cur[j] + np.sign(tgt[j] - cur[j]) * step[j] * np.arange(1, m + 1)
            ramp = np.minimum(ramp, tgt[j]) if tgt[j] >= cur[j] else np.maximum(ramp, tgt[j])
            env[i0:i0 + m, j] = ramp
            cur[j] = ramp[-1]
    y = np.zeros((n, 2))
    for j in range(k):
        y += tiled[j] * env[:, j:j + 1]
    fo = K.ns(3.0)
    y[-fo:] *= np.linspace(1, 0, fo)[:, None]
    return y


def mp3(path, y, kbps=112):
    data = np.clip(K.stereo(y), -1, 1).astype("<f4").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(K.SR), "-ac", "2", "-i", "pipe:0",
                    "-map_metadata", "-1", "-c:a", "libmp3lame", "-b:a", f"{kbps}k", path], input=data, check=True)


def excerpt(s):
    n = min(len(s), K.ns(STEM_EXCERPT))
    y = s[:n].copy()
    fi, fo = K.ns(0.3), K.ns(1.5)
    y[:fi] *= np.linspace(0, 1, fi)[:, None]
    y[-fo:] *= np.linspace(1, 0, fo)[:, None]
    return y


def voices(c):
    """A one-line description of a theme: key, mode, tempo, metre."""
    return f"{c['key'][:-1]} {c['mode'].capitalize()}, {c['bpm']} bpm in {c['beats']}, a {c['bars']}-bar loop ({L.loop_seconds(c):.0f} s)"


def build(date):
    folder = f"listening-{date}"
    out_dir = os.path.join(DOCS, folder)
    os.makedirs(out_dir, exist_ok=True)
    cards = []
    for tid, c in L.THEMES.items():
        path = os.path.join(ASSETS, "awaken/music_layers/gate", tid + ".json")
        man = json.load(open(path))
        stems = stem_audio(man)
        jy = journey(man, stems)
        jfile = f"{tid}_journey.mp3"
        mp3(os.path.join(out_dir, jfile), jy)
        sfiles = []
        for s, a in zip(man["stems"], stems):
            f = f"{tid}_{s['name']}.mp3"
            mp3(os.path.join(out_dir, f), excerpt(a), kbps=96)
            sfiles.append(f)
        meas = man.get("measured", {})
        cards.append(dict(id=tid, title=c["title"], line=voices(c), room=c["room"], journey=f"{folder}/{jfile}",
                          stems=[dict(name=s["name"], file=f"{folder}/{f}", intent=s["intent"],
                                      fades=f"in {s['attack']:g} s · duck {s['duck']:g} s · out {s['release']:g} s")
                                 for s, f in zip(man["stems"], sfiles)],
                          loud=" · ".join(f"{k} {v['rms']:.1f} dB" for k, v in meas.items())))
        print(f"  {tid}: journey + {len(sfiles)} stems")
    page = render(date, cards)
    with open(os.path.join(DOCS, f"listening-{date}.html"), "w") as f:
        f.write(page)
    size = sum(os.path.getsize(os.path.join(out_dir, x)) for x in os.listdir(out_dir)) / 1e6
    print(f"wrote docs/audio/listening-{date}.html and {len(os.listdir(out_dir))} files ({size:.1f} MB)")


def render(date, cards):
    e = html.escape
    marks = "".join(f'<li><b>{e(t)}</b> {e(lbl)}</li>' for (s, lv, lbl) in JOURNEY
                    for t in [f"{int(s) // 60}:{int(s) % 60:02d}"])
    body = []
    for i, c in enumerate(cards):
        stems = "".join(f"""
        <div class="row" data-id="{e(c['id'])}.{e(s['name'])}">
          <div class="what"><span class="tag">{e(s['name'])}</span><span class="intent">{e(s['intent'])}</span>
            <span class="meta">{e(s['fades'])}</span></div>
          <audio controls preload="none" src="{e(s['file'])}"></audio>
          {verdict()}
        </div>""" for s in c["stems"])
        body.append(f"""
    <section class="card" id="{e(c['id'])}">
      <header><span class="n">{i + 1:02d}</span><h2>{e(c['title'])}</h2><p class="line">{e(c['line'])} · room: {e(c['room'])}</p></header>
      <div class="row journey" data-id="{e(c['id'])}.mix">
        <div class="what"><span class="tag strong">the mix</span><span class="intent">One minute through every level, faded as the game fades it.
          Does it rise with the danger and settle after?</span><span class="meta">{e(c['loud'])} (gated RMS)</span></div>
        <audio controls preload="none" src="{e(c['journey'])}"></audio>
        {verdict()}
      </div>
      <details><summary>each layer alone (20 s)</summary>{stems}</details>
    </section>""")
    return TEMPLATE.replace("%DATE%", e(date)).replace("%MARKS%", marks).replace("%CARDS%", "".join(body)) \
        .replace("%KEY%", f"awaken-listening-{date}").replace("%COUNT%", str(len(cards) * 5))


def verdict():
    return """<div class="verdict"><div class="choices" role="group">
            <button data-v="keep">keep</button><button data-v="remake">remake</button><button data-v="cut">cut</button></div>
            <input class="note" type="text" placeholder="note (what's wrong, what it should be)"></div>"""


TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>The Sound: listening</title>
<style>
:root { --bg:#f6f4ef; --card:#fffdf8; --ink:#1d1c1a; --soft:#6b665d; --line:#e4dfd4; --accent:#2e6bd8; --keep:#2f8a4e;
  --remake:#b7791f; --cut:#b83a3a; --chip:#efe9dd; }
@media (prefers-color-scheme: dark) { :root { --bg:#14161a; --card:#1c1f25; --ink:#ece9e2; --soft:#a29d93; --line:#2c3038;
  --accent:#7fb0ff; --keep:#5cc27f; --remake:#e0a548; --cut:#ef6b6b; --chip:#262a32; } }
* { box-sizing:border-box; }
body { margin:0; background:var(--bg); color:var(--ink); font:15px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
main { max-width:980px; margin:0 auto; padding:28px 16px 80px; }
h1 { font-size:26px; margin:0 0 4px; letter-spacing:-0.01em; }
.sub { color:var(--soft); margin:0 0 20px; }
.intro { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:16px 18px; margin-bottom:18px; }
.intro p { margin:6px 0; }
.intro ol { margin:6px 0 6px 20px; padding:0; }
.marks { display:flex; flex-wrap:wrap; gap:6px 14px; list-style:none; padding:0; margin:8px 0 0; color:var(--soft); font-size:13px; }
.marks b { color:var(--ink); font-variant-numeric:tabular-nums; }
.bar { position:sticky; top:0; z-index:5; background:var(--bg); padding:10px 0; display:flex; gap:10px; align-items:center;
  flex-wrap:wrap; border-bottom:1px solid var(--line); margin-bottom:16px; }
.bar .progress { font-variant-numeric:tabular-nums; color:var(--soft); margin-right:auto; }
button { font:inherit; border:1px solid var(--line); background:var(--card); color:var(--ink); border-radius:8px; padding:6px 12px;
  cursor:pointer; }
button:hover { border-color:var(--accent); }
.card { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:16px 18px; margin-bottom:14px; }
.card header { display:flex; align-items:baseline; gap:10px; flex-wrap:wrap; }
.card h2 { font-size:19px; margin:0; }
.card .n { color:var(--soft); font-variant-numeric:tabular-nums; }
.card .line { margin:0; color:var(--soft); font-size:13px; flex-basis:100%; }
.row { display:grid; grid-template-columns: 1fr; gap:8px; padding:12px 0; border-top:1px solid var(--line); }
.row:first-of-type { border-top:none; }
.what { display:flex; flex-wrap:wrap; gap:4px 10px; align-items:baseline; }
.tag { background:var(--chip); border-radius:6px; padding:1px 8px; font-size:13px; }
.tag.strong { background:var(--accent); color:var(--bg); }
.intent { flex:1 1 300px; }
.meta { color:var(--soft); font-size:12px; flex-basis:100%; }
audio { width:100%; height:36px; }
.verdict { display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
.choices { display:flex; gap:6px; }
.choices button[aria-pressed=true][data-v=keep] { background:var(--keep); border-color:var(--keep); color:#fff; }
.choices button[aria-pressed=true][data-v=remake] { background:var(--remake); border-color:var(--remake); color:#fff; }
.choices button[aria-pressed=true][data-v=cut] { background:var(--cut); border-color:var(--cut); color:#fff; }
.note { flex:1 1 220px; min-width:0; font:inherit; padding:6px 10px; border-radius:8px; border:1px solid var(--line);
  background:var(--bg); color:var(--ink); }
details summary { cursor:pointer; color:var(--accent); padding:8px 0 2px; }
textarea { width:100%; min-height:160px; font:13px/1.4 ui-monospace, Menlo, monospace; background:var(--card); color:var(--ink);
  border:1px solid var(--line); border-radius:8px; padding:10px; }
@media (min-width:760px) { .row { grid-template-columns: 1.1fr 1fr; align-items:center; } .row .verdict { grid-column: 1 / -1; } }
</style>
</head>
<body>
<main>
<h1>The Sound: listening</h1>
<p class="sub">AWAKEN · Awakened Pack stage 4 · %DATE% · layered gate music, 14 themes</p>
<div class="intro">
  <p><b>Fifteen minutes.</b> For each gate theme, play <b>the mix</b>: about a minute that walks through every level the way
  the game does, with the game's own fades. Mark it <b>keep</b>, <b>remake</b> or <b>cut</b>, and say why in the note when it
  isn't a keep. The layers alone are under each card if one part bothers you (optional).</p>
  <ul class="marks">%MARKS%</ul>
  <p>In the game: inside a gate the music becomes four stems played together. The bed while you explore; a tension layer
  when something hunts you or the gate's pressure rises; drums and bass in a fight; the theme's tune stated big for a Lord.
  It follows the Music slider, never plays over vanilla's music, rests after two and a half minutes of calm, and fades on
  the way out and on death. The System's voice, the UI, the music and the Ritual's voices stay dry under Sound Physics
  Remastered; everything else gets the room it's in (that part is heard in game, not here).</p>
  <p>Your choices stay in this browser. When you're done, <b>Export</b> and send me the file (or the text).</p>
</div>
<div class="bar"><span class="progress" id="progress">0 of %COUNT% marked</span>
  <button id="export">Export choices (.json)</button><button id="text">Show as text</button></div>
%CARDS%
<textarea id="out" hidden readonly></textarea>
</main>
<script>
(function () {
  var KEY = "%KEY%";
  var state = {};
  try { state = JSON.parse(localStorage.getItem(KEY) || "{}") || {}; } catch (e) { state = {}; }
  function save() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {} update(); }
  function update() {
    var rows = document.querySelectorAll(".row[data-id]"), done = 0, total = rows.length;
    rows.forEach(function (r) { if ((state[r.dataset.id] || {}).v) done++; });
    document.getElementById("progress").textContent = done + " of " + total + " marked";
  }
  document.querySelectorAll(".row[data-id]").forEach(function (row) {
    var id = row.dataset.id, s = state[id] || {};
    row.querySelectorAll(".choices button").forEach(function (b) {
      b.setAttribute("aria-pressed", s.v === b.dataset.v ? "true" : "false");
      b.addEventListener("click", function () {
        var cur = state[id] || {};
        cur.v = cur.v === b.dataset.v ? "" : b.dataset.v;
        state[id] = cur;
        row.querySelectorAll(".choices button").forEach(function (o) { o.setAttribute("aria-pressed", o.dataset.v === cur.v ? "true" : "false"); });
        save();
      });
    });
    var note = row.querySelector(".note");
    note.value = s.note || "";
    note.addEventListener("input", function () { var cur = state[id] || {}; cur.note = note.value; state[id] = cur; save(); });
  });
  // one sound at a time
  document.addEventListener("play", function (ev) {
    document.querySelectorAll("audio").forEach(function (a) { if (a !== ev.target) a.pause(); });
  }, true);
  function rows() {
    var out = [];
    document.querySelectorAll(".row[data-id]").forEach(function (r) {
      var s = state[r.dataset.id] || {};
      out.push({ id: r.dataset.id, verdict: s.v || "", note: s.note || "" });
    });
    return out;
  }
  document.getElementById("export").addEventListener("click", function () {
    var blob = new Blob([JSON.stringify({ sheet: KEY, exported: new Date().toISOString(), entries: rows() }, null, 2)], { type: "application/json" });
    var a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = KEY + ".json";
    document.body.appendChild(a); a.click(); a.remove();
  });
  document.getElementById("text").addEventListener("click", function () {
    var t = document.getElementById("out");
    t.value = rows().filter(function (r) { return r.verdict || r.note; })
      .map(function (r) { return r.id + ": " + (r.verdict || "-") + (r.note ? " - " + r.note : ""); }).join("\\n") || "(nothing marked yet)";
    t.hidden = false; t.focus(); t.select();
  });
  update();
})();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else datetime.date.today().isoformat())
