"""The listening page for the War Anvil's own sounds (SPEC §46.10, stage 5): Euan's ear is the kit's last gate.

    python3 tools/art/anvil_listen.py [out]   default out: build/anvil-listening (index.html and its MP3s)
    python3 tools/art/anvil_listen.py [out] --page   only the page, from the MP3s already there

Two kinds of entry, each with keep / remake / cut and a note:
  - forgings: the sounds as the game plays them for the smith, at its own timing (the rhythms, the count-in, the flip
    after half the strikes, the quench's hold, the hilt's taps, the 30-tick draw; read from AnvilSong.java) and its own
    pitches (each clean strike's ring a step nearer the style's true note, the draw at it), so one listen answers "does
    a forging sound right?";
  - every sound alone: its variants one after another (the round-robin the game plays), with what it is for.
Built from the game's own files (sounds/anvil, and the Forge's sounds/forge for the bellows, pull and count-in), so
what is heard is what ships. The page is published as an Artifact (its MP3s alongside); the marks save in its store
(collection "verdicts", one document per entry: verdict, note).
"""
import html
import inspect
import json
import os
import re
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "specs"))
import audiokit as K  # noqa: E402
import audio_arms as A  # noqa: E402  (shift)
import audio_anvil as V  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
SND = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
SONG = os.path.join(ROOT, "src/main/java/dev/awaken/arms/anvil/AnvilSong.java")
TICK = 0.05
GAP = 0.45  # between an event's variants
NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]

_FILES = {}


def songs():
    """Each style's song as the game has it: (step, bar, strikes, note)."""
    src = open(SONG).read()
    out = {}
    for m in re.finditer(r'Map\.entry\("(\w+)", new Song\((\d+), "([x.]+)", (\d+), (-?\d+)\)\)', src):
        out[m.group(1)] = (int(m.group(2)), m.group(3), int(m.group(4)), int(m.group(5)))
    assert len(out) == 16, f"read {len(out)} songs from AnvilSong.java"
    return out


def flat():
    return float(re.search(r"public static final float FLAT = ([\d.]+)F", open(SONG).read()).group(1))


def note_name(semis):
    m = 81 + semis  # A5
    return f"{NAMES[m % 12]}{m // 12 - 1}"


def due(step, bar, k):
    """The game's Rhythm.due: the k-th strike's tick after the count-in bar."""
    xs = [i for i, c in enumerate(bar) if c == "x"]
    cycle, within = divmod(k, len(xs))
    return (len(bar) * (1 + cycle) + xs[within]) * step


def variants(event):
    if event in V.EVENTS:
        return V.EVENTS[event][1]
    return V.FORGE[event][1]


def sound(event, k):
    key = (event, k)
    if key not in _FILES:
        path = os.path.join(SND, "anvil", f"anvil_{event}_{k}.ogg") if event in V.EVENTS \
            else os.path.join(SND, "forge", f"forge_{V.FORGE[event][0]}_{k}.ogg")
        x = K.read_audio(path)
        _FILES[key] = K.mono(x) if x.ndim > 1 else x
    return _FILES[key]


class Scene:
    """Sounds on a timeline in ticks, each a variant in turn (the game's round-robin), at the game's volume and pitch."""

    def __init__(self):
        self.parts, self.turn = [], {}

    def at(self, tick, event, volume=1.0, pitch=1.0):
        k = self.turn.get(event, 0)
        self.turn[event] = (k + 1) % variants(event)
        x = sound(event, k)
        # Minecraft plays a pitch as a playback rate, clamped to 0.5-2.0
        p = min(2.0, max(0.5, pitch))
        self.parts.append((tick * TICK, A.shift(x, p) if p != 1.0 else x, volume))
        return self

    def mix(self, tail=0.4):
        n = max(K.ns(t) + len(x) for t, x, _ in self.parts) + K.ns(tail)
        y = np.zeros(n)
        for t, x, g in self.parts:
            i = K.ns(t)
            y[i:i + len(x)] += x * g
        pk = np.max(np.abs(y))
        return y / pk * 0.94 if pk > 0.94 else y


def ring_pitch(note, clean, of):
    return 2 ** ((note - flat() * (1 - min(1.0, clean / max(1, of)))) / 12)


def true_pitch(note):
    return 2 ** (note / 12)


def jitter(i, spread):
    """The game's small per-play pitch wobble ((now % 10) * 0.01), as a fixed pattern."""
    return ((i * 7) % 10) / 10 * spread


def count_in(s, t0, step, bar, strikes):
    """The count-in bar out loud, then a soft tick on each beat still to strike."""
    last = due(step, bar, strikes - 1)
    for i in range(0, int(last / step) + 1):
        if bar[i % len(bar)] != "x":
            continue
        if i < len(bar):
            s.at(t0 + i * step, "count", 0.9, 1.25 if i == 0 else 1.0)
        elif i * step < last:
            s.at(t0 + i * step, "count", 0.22)


def draw_out(s, t0, song, strike, scores=None):
    """The draw out: count-in, each strike on its beat (a clean one rings a step truer), the flip after half."""
    step, bar, n, note = song
    scores = scores or [1.0] * n
    count_in(s, t0, step, bar, n)
    clean = 0
    for k, sc in enumerate(scores):
        t = t0 + due(step, bar, k)
        if sc <= 0:
            s.at(t, "miss", 0.6)
            continue
        s.at(t, strike, 1.0, 0.95 + jitter(k, 0.1))
        if sc >= 0.8:
            clean += 1
            s.at(t, "ring", 0.7, ring_pitch(note, clean, n))
        if k + 1 == n // 2:
            s.at(t + 1, "flip", 0.8)
    return t0 + due(step, bar, n - 1)


def heat(s, t0, bellows_every=46, pull_at=78):
    for t in range(0, pull_at, bellows_every):
        s.at(t0 + t, "bellows", 0.45, 0.95 + jitter(t, 0.07))
    s.at(t0 + pull_at, "pull", 1.0)
    return t0 + pull_at + 2


def quench(s, t0, hold=32):
    s.at(t0, "quench", 1.0)
    s.at(t0 + hold, "lift", 0.9)
    return t0 + hold


def hilt(s, t0):
    step, bar = 3, "x..x..x."
    count_in(s, t0, step, bar, 3)
    for k in range(3):
        s.at(t0 + due(step, bar, k), "tap", 0.8, 1.0 + jitter(k, 0.1))
    return t0 + due(step, bar, 2)


def draw(s, t0, note):
    s.at(t0, "scrape", 0.9)
    s.at(t0 + 30, "drawn", 1.0, true_pitch(note))
    return t0 + 30


# ---- the forgings -------------------------------------------------------------------------------------------------

def d_longsword():
    S = songs()
    s = Scene()
    t = heat(s, 0)
    t = draw_out(s, t, S["longsword"], "strike")
    t = quench(s, t + 24)
    t = hilt(s, t + 16)
    draw(s, t + 22, S["longsword"][3])
    return s


def truer():
    S = songs()
    s = Scene()
    t = draw_out(s, 0, S["longsword"], "strike")
    # the same song with two strikes off the mark (struck, not clean: no ring) and one beat let pass
    draw_out(s, t + 50, S["longsword"], "strike", [1, 1, 0.5, 1, 1, 0.6, 1, 0, 1, 1])
    return s


def true_notes():
    S = songs()
    s, t = Scene(), 0
    for note in sorted({v[3] for v in S.values()}):
        s.at(t, "strike", 0.8)
        s.at(t, "ring", 0.7, true_pitch(note))
        t += 22
    s.at(t + 10, "drawn", 1.0, true_pitch(S["maul"][3]))
    s.at(t + 70, "drawn", 1.0, true_pitch(S["daggers"][3]))
    return s


def metals():
    S = songs()
    s, t = Scene(), 0
    step, bar, _, note = S["longsword"]
    for ev in ("strike", "strike_gate", "strike_veil", "strike_lord", "strike_star"):
        for k in range(4):
            s.at(t + due(step, bar, k) - len(bar) * step, ev, 1.0, 0.95 + jitter(k, 0.1))
            s.at(t + due(step, bar, k) - len(bar) * step, "ring", 0.7, true_pitch(note))
        t += 70
    return s


def e_knap_bind():
    S = songs()
    s, t = Scene(), 0
    for k in range(8):
        s.at(t, "knap", 0.8, 0.95 + jitter(k, 0.1))
        t += 13 + (k % 3) * 2
    t += 20
    for turn in range(1, 5):
        s.at(t, "cord", 0.6, 1.0 + turn * 0.06)
        t += 22
    draw(s, t + 20, S["daggers"][3])
    return s


def c_grind():
    s = Scene()
    for k in range(20):
        if (k + 1) % 2 == 0:
            s.at(k * 5, "wheel", 0.45, 0.95 + jitter(k, 0.08))
    return s


def a_twist():
    S = songs()
    s, t = Scene(), 0
    for turn in range(1, 4):
        s.at(t, "twist", 0.7, 0.9 + turn * 0.06)
        t += 16
    step, bar, n, note = S["longsword"]
    draw_out(s, t + 20, (step, bar, 4, note), "strike_lord")
    return s


def b_balance():
    S = songs()
    note = S["longsword"][3]
    s, t = Scene(), 0
    for fl in (1.0, 0.6, 0.2, 0.0):
        s.at(t, "hum", 0.55, true_pitch(note) * (1 - 0.06 * fl))
        t += 50
    s.at(t - 30, "ring", 0.8, true_pitch(note))
    return s


def s_star():
    S = songs()
    s = Scene()
    note = S["longsword"][3]
    s.at(0, "strike_star", 1.0)
    s.at(0, "ring", 0.7, true_pitch(note))
    s.at(40, "star", 1.0)
    return s


def scenes():
    S = songs()
    ladder = sorted({v[3] for v in S.values()})
    by_note = {}
    for style, v in sorted(S.items(), key=lambda kv: kv[1][3]):
        by_note.setdefault(v[3], []).append(style)
    return [
        ("d_longsword", "A Longsword at D, start to finish", d_longsword,
         "The heat with the Forge's bellows and the pull in the gold, the anvil counting in, ten clean strikes in the "
         "Longsword's rhythm (each ring a step nearer its true note, A) with the flip after the fifth, the plunge and the "
         "lift, the hilt's three taps, then the blade drawn from the stone.", S["longsword"][1]),
        ("truer", "A step truer with each clean strike", truer,
         "The Longsword's ten strikes all clean, the ring climbing a minor third to A; then the same song with two strikes "
         "off the mark (no ring) and one beat missed, so the last ring ends a little flat.", S["longsword"][1]),
        ("true_notes", "Every style's true note", true_notes,
         "The ring at each true note from the lowest to the highest, then a Maul and a pair of Daggers drawn from the "
         "stone. " + "; ".join(f"{note_name(n)}: {', '.join(s.title() for s in by_note[n])}" for n in ladder) + ".", None),
        ("metals", "The hammer on each tier's metal", metals,
         "Four strikes each, in order: iron (up to D), gatewrought (C), veilwoven (B), lordscale (A), sovereign (S). "
         "Each should sound like its own metal, and thinner and brighter than the armour's plate.", None),
        ("e_knap_bind", "E: knap and bind", e_knap_bind,
         "A Wildhide weapon by hand: eight flakes struck off the edge at your own pace, four turns of cord round the "
         "grip, then a pair of Daggers drawn (their true note, G6).", None),
        ("c_grind", "C: the grind", c_grind,
         "Twenty touches of the edge on the grindstone, one every five ticks, the wheel heard on every second.", None),
        ("a_twist", "A: the twist", a_twist,
         "Three turns of the hot bar in the tongs, each a little higher, then the first strikes on lordscale.", None),
        ("b_balance", "B: the balance on the Deepglass horn", b_balance,
         "The horn hums under the blade every two and a half seconds, flat while it tips and truer as it levels; level, "
         "the blade rings true.", None),
        ("s_star", "S: the starlight", s_star,
         "The last strike on sovereign steel, then the blade held up into the starlight.", None),
    ], ladder, by_note


def intent(event):
    fn = V.EVENTS[event][0]
    if fn.__name__ == "<lambda>":
        fn = V.strike
    return " ".join((inspect.getdoc(fn) or "").split())


EVENT_LINES = {
    "strike": "The hammer on iron, up to D.", "strike_gate": "The hammer on gatewrought steel (C): hard and glassy, its grit.",
    "strike_veil": "The hammer on veilwoven steel (B): a glass voice beating in it.",
    "strike_lord": "The hammer on lordscale (A): dark and heavy, its scales settling after.",
    "strike_star": "The hammer on sovereign steel (S): a high halo.",
}
GROUPS = [("The hammer", ["strike", "strike_gate", "strike_veil", "strike_lord", "strike_star"]),
          ("The blade", ["ring", "flip", "quench", "lift", "tap", "scrape", "drawn"]),
          ("The ranks' acts", ["knap", "cord", "wheel", "twist", "hum", "star"])]


def mp3(path, y, kbps=128):
    data = np.clip(K.stereo(y), -1, 1).astype("<f4").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(K.SR), "-ac", "2", "-i", "pipe:0",
                    "-map_metadata", "-1", "-c:a", "libmp3lame", "-b:a", f"{kbps}k", path], input=data, check=True)


def build(out, audio=True):
    os.makedirs(os.path.join(out, "audio"), exist_ok=True)
    sc, ladder, by_note = scenes()
    forgings, groups = [], []
    for sid, title, fn, line, rhythm in sc:
        f = f"audio/forging_{sid}.mp3"
        y = fn().mix()
        if audio:
            mp3(os.path.join(out, f), y)
        dur = len(y) / K.SR
        forgings.append(dict(id=f"forging-{sid}", title=title, line=line, file=f, rhythm=rhythm, secs=dur))
    for gname, events in GROUPS:
        rows = []
        for event in events:
            f = f"audio/sound_{event}.mp3"
            n = variants(event)
            if audio:
                s, t = Scene(), 0.0
                for k in range(n):
                    s.at(t / TICK, event)
                    t += len(sound(event, k)) / K.SR + GAP
                mp3(os.path.join(out, f), s.mix(0.2))
            _, _, att, subtitle = V.EVENTS[event]
            rows.append(dict(id=f"sound-{event}", title=f"anvil.{event}", line=EVENT_LINES.get(event, intent(event)), file=f,
                             meta=[f"{n} variant{'s' if n > 1 else ''}", f"subtitle “{subtitle}”", f"heard to {att} blocks"]))
        groups.append((gname, rows))
    with open(os.path.join(out, "index.html"), "w") as fh:
        fh.write(render(forgings, groups, ladder, by_note))
    size = sum(os.path.getsize(os.path.join(out, "audio", x)) for x in os.listdir(os.path.join(out, "audio"))) / 1e6
    print(f"wrote {os.path.relpath(out, ROOT)}/index.html: {len(forgings)} forgings, {sum(len(r) for _, r in groups)} sounds ({size:.1f} MB)")


def clock(s):
    return f"{int(s // 60)}:{int(round(s % 60)):02d}"


def verdict(eid):
    e = html.escape(eid)
    return f"""<div class="mark" role="group" aria-label="Your mark">
          <button type="button" class="v keep" data-id="{e}" data-v="keep" aria-pressed="false">Keep</button>
          <button type="button" class="v remake" data-id="{e}" data-v="remake" aria-pressed="false">Remake</button>
          <button type="button" class="v cut" data-id="{e}" data-v="cut" aria-pressed="false">Cut</button>
        </div>
        <textarea class="note" id="note-{e}" data-id="{e}" rows="1" placeholder="A note on what to change (optional)"></textarea>"""


def ladder_svg(ladder, by_note):
    lo, hi = min(ladder), max(ladder)
    cols = []
    for n in ladder:
        h = 18 + 62 * (n - lo) / (hi - lo)
        styles = ", ".join(s.title() for s in by_note[n])
        cols.append(f"""<div class="rung" title="{html.escape(styles)}">
            <span class="bar" style="height:{h:.0f}px"></span>
            <span class="nn">{note_name(n)}</span>
            <span class="who">{html.escape(styles)}</span>
          </div>""")
    return f'<div class="ladder" aria-label="Each style\'s true note, low to high">{"".join(cols)}</div>'


def render(forgings, groups, ladder, by_note):
    e = html.escape
    f_rows = []
    for x in forgings:
        extra = ""
        if x["rhythm"]:
            extra = f'<code class="rhythm" title="The Longsword\'s bar: x a strike, . a step of 4 ticks">{e(x["rhythm"])}</code>'
        if x["id"] == "forging-true_notes":
            extra = ladder_svg(ladder, by_note)
        f_rows.append(f"""
      <article class="row" data-id="{e(x['id'])}">
        <header class="what">
          <h3>{e(x['title'])}</h3><span class="len">{clock(x['secs'])}</span>
        </header>
        <p class="line">{e(x['line'])}</p>
        {extra}
        <audio controls preload="none" src="{e(x['file'])}"></audio>
        {verdict(x['id'])}
      </article>""")
    g_html = []
    for gname, rows in groups:
        items = []
        for x in rows:
            meta = "".join(f"<li>{e(m)}</li>" for m in x["meta"])
            items.append(f"""
        <article class="row compact" data-id="{e(x['id'])}">
          <header class="what"><h3><code>{e(x['title'])}</code></h3></header>
          <p class="line">{e(x['line'])}</p>
          <ul class="meta">{meta}</ul>
          <audio controls preload="none" src="{e(x['file'])}"></audio>
          {verdict(x['id'])}
        </article>""")
        g_html.append(f'<section class="group"><h3 class="gname">{e(gname)}</h3>{"".join(items)}</section>')
    ids = [x["id"] for x in forgings] + [x["id"] for _, rows in groups for x in rows]
    return PAGE.replace("%FORGINGS%", "".join(f_rows)).replace("%SOUNDS%", "".join(g_html)) \
        .replace("%IDS%", json.dumps(ids)).replace("%NF%", str(len(forgings))).replace("%NS%", str(len(ids) - len(forgings)))


PAGE = r"""<title>War Anvil Sounds</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Big+Shoulders+Display:wght@700;800&family=JetBrains+Mono:wght@400;500&family=Public+Sans:wght@400;600&display=swap">
<style>
/* A smith's listening bench: one column of rows, each a sound to hear and mark; soot and steel, the ember for what's yours to press. */
:root {
  --bg: #eceff1; --panel: #f9fafb; --ink: #1b2126; --muted: #56616b; --line: #cfd6db;
  --ember: #b4501b; --ember-soft: #f3dccd;
  --keep: #2c7a4b; --remake: #9a6400; --cut: #b23a2f;
  --display: "Big Shoulders Display", "Arial Narrow", Impact, sans-serif;
  --body: "Public Sans", system-ui, -apple-system, "Segoe UI", sans-serif;
  --mono: "JetBrains Mono", ui-monospace, Menlo, Consolas, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #121619; --panel: #1a2025; --ink: #e5e9ec; --muted: #98a3ac; --line: #2b343b;
  --ember: #f08a4b; --ember-soft: #3a2519;
  --keep: #5cc98b; --remake: #e1aa3d; --cut: #ef6d5f; color-scheme: dark; } }
:root[data-theme="dark"] {
  --bg: #121619; --panel: #1a2025; --ink: #e5e9ec; --muted: #98a3ac; --line: #2b343b;
  --ember: #f08a4b; --ember-soft: #3a2519;
  --keep: #5cc98b; --remake: #e1aa3d; --cut: #ef6d5f; color-scheme: dark; }
body { background: var(--bg); color: var(--ink); font: 16px/1.55 var(--body); }
body::before { content: ""; position: fixed; inset: 0 0 auto; height: env(safe-area-inset-top, 0px); z-index: 10; background: var(--bg); }
.wrap { max-width: 46rem; margin: 0 auto; padding-left: max(16px, env(safe-area-inset-left, 0px));
  padding-right: max(16px, env(safe-area-inset-right, 0px)); padding-block: 2.5rem 4rem; display: grid; gap: 2.5rem; }
.eyebrow { font: 500 0.75rem/1 var(--mono); letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted); margin: 0 0 0.75rem; }
h1 { font: 800 clamp(2.6rem, 9vw, 4rem)/0.92 var(--display); letter-spacing: 0.01em; text-transform: uppercase; margin: 0; text-wrap: balance; }
h1 span { color: var(--ember); }
.lede { max-width: 38rem; margin: 1rem 0 0; color: var(--ink); }
.lede + .lede { color: var(--muted); font-size: 0.94rem; margin-top: 0.6rem; }
.tally { position: sticky; top: env(safe-area-inset-top, 0px); z-index: 5; background: var(--bg); border-bottom: 1px solid var(--line);
  display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem 1.25rem; padding-block: 0.7rem; font: 500 0.82rem/1.2 var(--mono); }
.tally .n { font-variant-numeric: tabular-nums; }
.tally .k { color: var(--keep); } .tally .r { color: var(--remake); } .tally .c { color: var(--cut); } .tally .u { color: var(--muted); }
.tally .state { color: var(--muted); font-family: var(--body); font-weight: 400; flex: 1 1 12rem; min-width: 0; }
.tally button { margin-left: auto; }
h2 { font: 700 1.9rem/1 var(--display); text-transform: uppercase; letter-spacing: 0.02em; margin: 0 0 0.35rem; }
.sub { color: var(--muted); margin: 0 0 1.25rem; max-width: 38rem; font-size: 0.94rem; }
.rows, .group { display: grid; gap: 1rem; }
.gname { font: 500 0.78rem/1 var(--mono); letter-spacing: 0.08em; text-transform: uppercase; color: var(--ember); margin: 1rem 0 0; }
.row { background: var(--panel); border: 1px solid var(--line); border-radius: 6px; padding: 1.1rem 1.1rem 1rem; display: grid; gap: 0.7rem; min-width: 0; }
.row[data-v="keep"] { box-shadow: inset 4px 0 0 var(--keep); }
.row[data-v="remake"] { box-shadow: inset 4px 0 0 var(--remake); }
.row[data-v="cut"] { box-shadow: inset 4px 0 0 var(--cut); }
.what { display: flex; align-items: baseline; justify-content: space-between; gap: 1rem; }
.what h3 { font: 600 1.05rem/1.3 var(--body); margin: 0; min-width: 0; }
.what code { font: 500 0.95rem var(--mono); }
.len { font: 400 0.8rem var(--mono); color: var(--muted); font-variant-numeric: tabular-nums; }
.line { margin: 0; color: var(--ink); font-size: 0.95rem; max-width: 40rem; }
.meta { list-style: none; display: flex; flex-wrap: wrap; gap: 0.25rem 1rem; margin: 0; padding: 0; font: 400 0.78rem var(--mono); color: var(--muted); }
.rhythm { justify-self: start; font: 500 0.95rem var(--mono); letter-spacing: 0.18em; color: var(--ember); background: var(--ember-soft);
  padding: 0.2rem 0.5rem; border-radius: 3px; max-width: 100%; overflow-x: auto; }
audio { width: 100%; height: 40px; }
.mark { display: flex; flex-wrap: wrap; gap: 0.5rem; }
button { font: 600 0.88rem/1 var(--body); padding: 0.6rem 1rem; border-radius: 4px; border: 1px solid var(--line); background: var(--bg);
  color: var(--ink); cursor: pointer; min-height: 40px; }
button:hover { border-color: var(--muted); }
button:focus-visible, textarea:focus-visible { outline: 2px solid var(--ember); outline-offset: 2px; }
.v.keep[aria-pressed="true"] { background: var(--keep); border-color: var(--keep); color: var(--panel); }
.v.remake[aria-pressed="true"] { background: var(--remake); border-color: var(--remake); color: var(--panel); }
.v.cut[aria-pressed="true"] { background: var(--cut); border-color: var(--cut); color: var(--panel); }
textarea { font: 400 0.92rem/1.4 var(--body); color: var(--ink); background: var(--bg); border: 1px solid var(--line); border-radius: 4px;
  padding: 0.55rem 0.7rem; resize: vertical; width: 100%; box-sizing: border-box; }
.ladder { display: grid; grid-template-columns: repeat(9, minmax(0, 1fr)); gap: 0.35rem; align-items: end; padding-top: 0.4rem; }
.rung { display: grid; justify-items: center; gap: 0.25rem; min-width: 0; text-align: center; }
.rung .bar { width: 100%; max-width: 2.2rem; background: linear-gradient(to top, var(--ember), var(--ember-soft)); border-radius: 2px 2px 0 0; }
.rung .nn { font: 500 0.78rem var(--mono); }
.rung .who { font-size: 0.68rem; line-height: 1.25; color: var(--muted); overflow-wrap: anywhere; }
.copied { margin: 0.5rem 0 0; width: 100%; font: 400 0.8rem/1.4 var(--mono); }
footer { color: var(--muted); font-size: 0.88rem; border-top: 1px solid var(--line); padding-top: 1rem; }
@media (max-width: 30rem) { .rung .who { display: none; } .row { padding: 0.9rem; } }
@media (prefers-reduced-motion: reduce) { * { transition: none !important; } }
</style>

<div class="wrap">
  <header>
    <p class="eyebrow">Awaken &middot; War Anvil &middot; stage 5</p>
    <h1>War Anvil <span>Sounds</span></h1>
    <p class="lede">The anvil's own sound kit, laid out the way the game plays it. Listen to the forgings first, then each
      sound on its own, and mark every one Keep, Remake or Cut, with a note on anything you'd change. Your marks save as
      you go, and Claude reads them back.</p>
    <p class="lede">Every clean strike rings the blade a little nearer its style's true note, so a Maul's forging sings low
      and a pair of Daggers high. The Forge's bellows, the pull from the coals and the count-in are the armour forge's
      sounds and play in the forgings as they do in the game.</p>
  </header>

  <div class="tally" aria-live="polite">
    <span class="n k" id="t-keep">0 keep</span><span class="n r" id="t-remake">0 remake</span>
    <span class="n c" id="t-cut">0 cut</span><span class="n u" id="t-left">%NF% + %NS% to hear</span>
    <span class="state" id="state">Loading your marks&hellip;</span>
    <button type="button" id="copy">Copy marks as text</button>
    <textarea class="copied" id="copied" rows="6" readonly hidden></textarea>
  </div>

  <section>
    <h2>Forgings</h2>
    <p class="sub">Each at the game's own timing: the rhythms, the count-in, the quench's hold, the 30-tick draw.</p>
    <div class="rows">%FORGINGS%
    </div>
  </section>

  <section>
    <h2>Every sound alone</h2>
    <p class="sub">Each sound's variants one after another, as the game takes them in turn. The ring and the drawn blade
      are cut at A; the game moves them to each style's note.</p>
    <div class="rows">%SOUNDS%
    </div>
  </section>

  <footer>Built from the files the game ships (tools/art/specs/audio_anvil.py, page tools/art/anvil_listen.py). Nothing
    here was heard before you: these sounds have only been checked by meter and spectrogram.</footer>
</div>

<script>
(() => {
  const IDS = %IDS%;
  const marks = {};            // id -> {verdict, note}
  const chains = {};           // id -> the write in flight, so one document is written one at a time
  let db = null, writable = true;
  const $ = (s) => document.querySelector(s);
  const state = $("#state");
  const say = (t) => { state.textContent = t; };

  function paint(id) {
    const m = marks[id] || {};
    const row = document.querySelector(`.row[data-id="${id}"]`);
    if (!row) return;
    if (m.verdict) row.dataset.v = m.verdict; else delete row.dataset.v;
    row.querySelectorAll(".v").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.v === m.verdict)));
    const note = row.querySelector(".note");
    if (note && document.activeElement !== note) note.value = m.note || "";
  }
  function tally() {
    const n = { keep: 0, remake: 0, cut: 0 };
    let marked = 0;
    for (const id of IDS) { const v = marks[id] && marks[id].verdict; if (v) { n[v]++; marked++; } }
    $("#t-keep").textContent = `${n.keep} keep`;
    $("#t-remake").textContent = `${n.remake} remake`;
    $("#t-cut").textContent = `${n.cut} cut`;
    $("#t-left").textContent = marked === IDS.length ? "all marked" : `${IDS.length - marked} unmarked`;
  }
  function save(id) {
    const body = { verdict: (marks[id] && marks[id].verdict) || "", note: (marks[id] && marks[id].note) || "", at: Date.now() };
    try { localStorage.setItem("mark:" + id, JSON.stringify(body)); } catch (e) {}
    if (!db || !writable) return;
    const prev = chains[id] || Promise.resolve();
    chains[id] = prev.then(() => db.collection("verdicts").doc(id).set(body)).then(
      () => say("Saved."),
      (err) => {
        if (err && err.code === "invalid_argument") { writable = false; say("Your marks can't be saved from this view. Use Copy marks as text."); }
        else say("Couldn't save just now; it will try again with your next mark.");
      });
  }

  document.addEventListener("click", (ev) => {
    const b = ev.target.closest(".v");
    if (!b) return;
    const id = b.dataset.id;
    const m = marks[id] || (marks[id] = {});
    m.verdict = m.verdict === b.dataset.v ? "" : b.dataset.v;
    paint(id); tally(); save(id);
  });
  const timers = {};
  document.addEventListener("input", (ev) => {
    const t = ev.target.closest(".note");
    if (!t) return;
    const id = t.dataset.id;
    (marks[id] || (marks[id] = {})).note = t.value;
    clearTimeout(timers[id]);
    timers[id] = setTimeout(() => save(id), 900);
  });
  document.addEventListener("focusout", (ev) => {
    const t = ev.target.closest && ev.target.closest(".note");
    if (t && timers[t.dataset.id]) { clearTimeout(timers[t.dataset.id]); delete timers[t.dataset.id]; save(t.dataset.id); }
  });

  $("#copy").addEventListener("click", () => {
    const lines = IDS.map((id) => {
      const m = marks[id] || {};
      return `${(m.verdict || "unmarked").padEnd(8)} ${id}${m.note ? "  " + m.note.replace(/\s+/g, " ") : ""}`;
    });
    const text = lines.join("\n");
    const box = $("#copied");
    const show = () => { box.value = text; box.hidden = false; box.focus(); box.select(); say("Selected: copy it with your keyboard."); };
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).then(() => say("Copied."), show);
    else show();
  });

  // this browser's own copy first, so the page is right even before (or without) the store
  for (const id of IDS) {
    try { const s = localStorage.getItem("mark:" + id); if (s) marks[id] = JSON.parse(s); } catch (e) {}
    paint(id);
  }
  tally();

  (async () => {
    try { db = window.claude ? await window.claude.use("db") : null; } catch (e) { db = null; }
    if (!db) { say("Marks stay in this browser only here. Use Copy marks as text to send them."); return; }
    const slow = setTimeout(() => say("Your saved marks can't be reached yet; new marks still save."), 10000);
    db.collection("verdicts").onSnapshot((snap) => {
      if (!snap.metadata.fromCache) { clearTimeout(slow); say(snap.empty ? "No marks yet." : "Your marks are loaded."); }
      for (const d of snap.docs) {
        const body = d.data() || {};
        if (document.activeElement && document.activeElement.dataset && document.activeElement.dataset.id === d.id) continue;
        marks[d.id] = { verdict: body.verdict || "", note: body.note || "" };
        paint(d.id);
      }
      tally();
    }, () => { clearTimeout(slow); say("Your saved marks can't be read here; new marks stay in this browser."); });
  })();
})();
</script>
"""


def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    out = os.path.abspath(args[0]) if args else os.path.join(ROOT, "build/anvil-listening")
    build(out, audio="--page" not in argv)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
