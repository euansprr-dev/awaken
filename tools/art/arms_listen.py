"""The listening sheet for Arms' sounds (SPEC §24): the ear is the last gate the kit's checks can't be.

    python3 tools/art/arms_listen.py [date] [--page]   default: today; writes docs/combat/listening-<date>.html and
                                                        docs/combat/listening-<date>/*.mp3 next to it
                                                        --page: only the page, from the MP3s already there

Two kinds of entry, each with keep / remake / cut and a note (the same sheet as tools/art/audio_listen.py, its look
and its export):
  - scenes: the sounds in a fight, at the game's own timing (read from the timing table, the tells' lengths from
    mobs MobGoals.ToldMelee) and the game's own choices (the swish a semitone higher per rhythm step, the impact by
    what the blade meets, a heavy on a hard body a little lower), so one listen answers "does the fight sound right?";
  - every event alone: its variants one after another (the round-robin the game plays), with what it is for.
Built from the game's own files (src/main/resources/assets/awaken/sounds/arms), so what is heard is what ships.
"""
import datetime
import html
import inspect
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "specs"))
import audiokit as K  # noqa: E402
import audio_arms as A  # noqa: E402
from audio_listen import TEMPLATE as MUSIC_SHEET, mp3, verdict  # noqa: E402  (one look and one verdict for every sheet)

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
DOCS = os.path.join(ROOT, "docs/combat")
TABLE = os.path.join(ROOT, "src/main/resources/data/awaken/arms/archetypes.json")
TICK = 0.05
SEMITONE = 2 ** (1 / 12)
GAP = 0.45  # between an event's variants

_FILES = {}


def sound(event, k):
    """Variant k of an event as the game has it on disk, at its sounds.json volume."""
    key = (event, k)
    if key not in _FILES:
        x = K.read_audio(os.path.join(A.SND, f"arms_{event}_{k}.ogg"))
        _FILES[key] = (K.mono(x) if x.ndim > 1 else x) * A.EVENTS[event][7]
    return _FILES[key]


def variants(event):
    return A.EVENTS[event][1]


class Scene:
    """Sounds laid on a timeline (seconds), each a variant in turn, the way the game's round-robin picks."""

    def __init__(self):
        self.parts = []
        self.turn = {}

    def at(self, t, event, pitch=1.0, gain=1.0):
        k = self.turn.get(event, 0)
        self.turn[event] = (k + 1) % variants(event)
        x = sound(event, k)
        self.parts.append((t, A.shift(x, pitch) if pitch != 1.0 else x, gain))
        return self

    def mix(self, tail=0.6):
        n = max(K.ns(t) + len(x) for t, x, _ in self.parts) + K.ns(tail)
        y = np.zeros(n)
        for t, x, g in self.parts:
            i = K.ns(t)
            y[i:i + len(x)] += x * g
        peak = np.max(np.abs(y))
        return y / peak * 0.94 if peak > 0.94 else y  # several blows at once may sum past full scale: never clip


def table():
    d = json.load(open(TABLE))
    return {a["id"]: a for a in d["archetypes"]}["longsword"]


def moves():
    return table()["moves"]


A_SWISH = {"light": "swish_light", "heavy": "swish_heavy", "thrust": "swish_thrust"}  # a move's "swish" -> its event

# the tells' lengths in ticks (mobs MobGoals.ToldMelee.tellTicks): a gate creature, a boss, a heavy's +6
TELL, TELL_BOSS, TELL_HEAVY = 8, 10, 6


def chain(rhythm):
    """The three-strike chain into a zombie: each press in its rhythm window (or mashed early, buffered, no rhythm)."""
    m, s, t, step = moves(), Scene(), 0.0, 0
    for i, key in enumerate(table()["chain"]):
        mv = m[key]
        s.at(t, A_SWISH[mv.get("swish", "light")], SEMITONE ** step)
        s.at(t + mv["windup"] * TICK, "hit_flesh_heavy" if mv["motion"] >= 1.4 else "hit_flesh")
        if "cadence" not in mv:
            break
        # the next press lands on the window's first tick (counted from the move's start), or is mashed early and
        # buffered there: the same timing without the rhythm
        t += mv["cadence"][0] * TICK
        step = step + 1 if rhythm else 0
    return s


def deflect_riposte():
    m = moves()["riposte"]
    s = Scene().at(0.0, "tell_light")
    hit = TELL * TICK
    s.at(hit, "deflect")
    press = hit + 0.2  # a riposte pressed 4 ticks after the deflect (its window is 12)
    s.at(press, A_SWISH[m.get("swish", "light")]).at(press + m["windup"] * TICK, "hit_flesh_heavy")
    return s


def heavy_glint():
    m = moves()["heavy"]
    c = m["charge"]
    s = Scene().at(c["min"] * TICK, "charge")  # the draw starts once the key is held into a heavy
    s.at(c["glint"] * TICK, "glint")
    release = (c["glint"] + 1) * TICK
    s.at(release, A_SWISH[m.get("swish", "light")]).at(release + TICK, "hit_flesh_heavy")
    return s


def perilous_dodge():
    m = moves()["slipcut"]
    s = Scene().at(0.0, "tell_perilous")
    blow = TELL_BOSS * TICK
    s.at(blow - 0.05, "perfect_dodge")  # the dodge pressed a tick before the blow: it lands in the perfect ticks
    press = blow + 0.15
    s.at(press, A_SWISH[m.get("swish", "light")]).at(press + m["windup"] * TICK, "hit_flesh")
    return s


def guard_broken():
    s = Scene().at(0.0, "tell_heavy")
    blow = (TELL_BOSS + TELL_HEAVY) * TICK
    return s.at(blow, "guard_break").at(blow + 0.03, "hurt")


def break_execution():
    """Blows on a stone body until its stance breaks, then the Execution."""
    m, s, t = moves(), Scene(), 0.0
    for key in ("strike_1", "strike_2", "strike_3"):
        mv = m[key]
        heavy = mv["motion"] >= 1.4
        s.at(t, A_SWISH[mv.get("swish", "light")]).at(t + mv["windup"] * TICK, "hit_stone", 0.9 if heavy else 1.0)
        t += mv["cadence"][0] * TICK if "cadence" in mv else 0
    brk = t + m["strike_3"]["windup"] * TICK + 0.02
    s.at(brk, "break")
    ex = moves()["execution"]
    press = brk + 0.6
    return s.at(press, A_SWISH[ex.get("swish", "light")]).at(press + ex["windup"] * TICK, "execution")


def art():
    """The heavy held through with the meter full: the draw, the glint passing, the Art let go at full charge."""
    m = moves()
    c = m["heavy"]["charge"]
    s = Scene().at(c["min"] * TICK, "charge").at(c["glint"] * TICK, "glint")
    release = c["max"] * TICK
    s.at(release, "art")
    blade = release + m["art"]["windup"] * TICK
    for i, at in enumerate((0.05, 0.15, 0.25)):
        s.at(blade + at, "hit_flesh_heavy" if i == 0 else "hit_flesh", 0.85 if i else 1.0)  # the wave reaching three foes in turn
    return s


def materials():
    s, t = Scene(), 0.0
    for event in ("hit_flesh", "hit_armor", "hit_bone", "hit_wood", "hit_stone", "hit_spirit", "hit_slime"):
        s.at(t, "swish_light").at(t + 0.1, event)
        t += 0.9
    return s


SCENES = [
    ("chain_rhythm", "The chain, in rhythm", chain, (True,),
     "Strike 1, 2, 3 into a zombie, each press on its rhythm window: the swish climbs a semitone a step, the finisher lands heavy."),
    ("chain_mashed", "The chain, mashed", chain, (False,),
     "The same chain pressed early every time (buffered to the window, no rhythm): no climb. Should feel flatter, not wrong."),
    ("deflect", "Deflect, then riposte", deflect_riposte, (),
     "A creature tells a light blow (8 ticks), the guard rises in time: the steel sings, then the riposte's thrust lands heavy."),
    ("heavy", "A heavy on the glint", heavy_glint, (),
     "The strike key held: the draw creaks, the glint pings at its mark, released on it: the rising cut lands heavy."),
    ("perilous", "Perilous: dodged", perilous_dodge, (),
     "A boss readies a perilous blow (a shriek nothing else makes), a perfect dodge (the world's breath held), a Slipcut."),
    ("guard_break", "A heavy breaks the guard", guard_broken, (),
     "An amber heavy tell, blocked instead of deflected: the guard is crushed aside and the blow gets through."),
    ("execution", "Break, then Execution", break_execution, (),
     "Three blows on a golem (stone), its stance gives, the Execution: the biggest blow there is."),
    ("art", "The Art", art, (),
     "A heavy held through to full charge with the meter full: the draw, the glint passing, the Art let go, its wave reaching three foes in turn."),
    ("materials", "What the blade meets", materials, (),
     "One cut into each body: flesh, armour, bone, wood, stone, a spirit, slime."),
]


def intent(event):
    """What the event is for: its render function's own docstring (audio_arms.py), first sentence onward."""
    fn = A.EVENTS[event][0]
    return " ".join((inspect.getdoc(fn) or "").split())


def build(date, audio=True):
    folder = f"listening-{date}"
    out_dir = os.path.join(DOCS, folder)
    os.makedirs(out_dir, exist_ok=True)
    scenes, events = [], []
    for sid, title, fn, args, line in SCENES:
        f = f"scene_{sid}.mp3"
        if audio:
            mp3(os.path.join(out_dir, f), fn(*args).mix(), kbps=128)
        scenes.append(dict(id=f"scene.{sid}", title=title, line=line, file=f"{folder}/{f}"))
    for event, spec in A.EVENTS.items():
        f = f"{event}.mp3"
        if audio:
            s, t = Scene(), 0.0
            for k in range(spec[1]):
                s.at(t, event)
                t += len(sound(event, k)) / K.SR + GAP
            mp3(os.path.join(out_dir, f), s.mix(0.2), kbps=128)
        events.append(dict(id=f"event.{event}", title=event.replace("_", " "), line=intent(event), file=f"{folder}/{f}",
                           meta=f"{spec[1]} variants · \"{spec[4]}\" · heard to {spec[3]} blocks · {spec[2]:g} dBFS"))
    missing = [x["file"] for x in scenes + events if not os.path.exists(os.path.join(DOCS, x["file"]))]
    if missing:
        sys.exit(f"missing {', '.join(missing)} (run without --page)")
    with open(os.path.join(DOCS, f"listening-{date}.html"), "w") as fh:
        fh.write(render(date, scenes, events))
    size = sum(os.path.getsize(os.path.join(out_dir, x)) for x in os.listdir(out_dir)) / 1e6
    print(f"wrote docs/combat/listening-{date}.html: {len(scenes)} scenes, {len(events)} events ({size:.1f} MB)")


def rows(entries, strong):
    e = html.escape
    out = []
    for x in entries:
        meta = f'<span class="meta">{e(x["meta"])}</span>' if x.get("meta") else ""
        tag = f'<span class="tag strong">{e(x["title"])}</span>' if strong else f'<span class="tag">{e(x["title"])}</span>'
        out.append(f"""
      <div class="row" data-id="{e(x['id'])}">
        <div class="what">{tag}<span class="intent">{e(x['line'])}</span>{meta}</div>
        <audio controls preload="none" src="{e(x['file'])}"></audio>
        {verdict()}
      </div>""")
    return "".join(out)


def render(date, scenes, events):
    style = MUSIC_SHEET[MUSIC_SHEET.index("<style>"):MUSIC_SHEET.index("</style>") + len("</style>")]
    return PAGE.replace("%STYLE%", style).replace("%DATE%", html.escape(date)).replace("%KEY%", f"awaken-arms-listening-{date}") \
        .replace("%SCENES%", rows(scenes, True)).replace("%EVENTS%", rows(events, False)) \
        .replace("%NS%", str(len(scenes))).replace("%NE%", str(len(events)))


PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Arms: listening</title>
%STYLE%
</head>
<body>
<main>
<h1>Arms: listening</h1>
<p class="sub">AWAKEN · weapons and combat · %DATE% · %NS% scenes, %NE% sounds</p>
<div class="intro">
  <p><b>About five minutes.</b> Every sound a fight makes in Arms, built from CC0 recordings (Kenney) and the kit's own
  synthesis, mastered to sit with vanilla's combat sounds. The checks already passed (loudness, true peak, a blow's
  transient in its first 6 ms, no clicks, variants that differ, no harshness): they catch a broken sound, not a dull
  one. That part is yours.</p>
  <ol>
    <li><b>The scenes first</b>: each plays a moment of a fight at the game's own timing. Does it feel like steel, like
    weight, like the moment it is? Mark it <b>keep</b>, <b>remake</b> or <b>cut</b>, and say why in the note.</li>
    <li><b>Then any sound alone</b> that bothered you in a scene: its variants play one after another (the game takes
    them in turn so a fight never repeats one sample).</li>
  </ol>
  <p>Headphones or good speakers, at the volume you play at. Your choices stay in this browser; <b>Export</b> and send
  me the file (or the text).</p>
</div>
<div class="bar"><span class="progress" id="progress"></span>
  <button id="export">Export choices (.json)</button><button id="text">Show as text</button></div>
<section class="card"><header><span class="n">01</span><h2>In a fight</h2>
  <p class="line">the sounds together, at the timing of the game's rows</p></header>%SCENES%
</section>
<section class="card"><header><span class="n">02</span><h2>Every sound alone</h2>
  <p class="line">what it is for, and its variants in turn</p></header>
  <details><summary>%NE% sounds</summary>%EVENTS%</details>
</section>
<textarea id="out" hidden readonly></textarea>
</main>
<script>
(function () {
  var KEY = "%KEY%";
  var state = {};
  try { state = JSON.parse(localStorage.getItem(KEY) || "{}") || {}; } catch (e) { state = {}; }
  function save() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {} update(); }
  function update() {
    var scenes = 0, ns = 0, events = 0, ne = 0;
    document.querySelectorAll(".row[data-id]").forEach(function (r) {
      var id = r.dataset.id, v = (state[id] || {}).v || "";
      if (id.indexOf("scene.") === 0) { ns++; if (v) scenes++; } else { ne++; if (v) events++; }
      r.querySelectorAll(".choices button").forEach(function (b) {
        b.setAttribute("aria-pressed", v === b.dataset.v ? "true" : "false");
      });
    });
    document.getElementById("progress").textContent = "scenes " + scenes + " of " + ns + " · sounds " + events + " of " + ne;
  }
  document.querySelectorAll(".row[data-id]").forEach(function (row) {
    var id = row.dataset.id;
    row.querySelectorAll(".choices button").forEach(function (b) {
      b.addEventListener("click", function () {
        var cur = state[id] || {};
        cur.v = cur.v === b.dataset.v ? "" : b.dataset.v;
        state[id] = cur;
        save();
      });
    });
    var note = row.querySelector(".note");
    note.value = (state[id] || {}).note || "";
    note.addEventListener("input", function () { var cur = state[id] || {}; cur.note = note.value; state[id] = cur; save(); });
  });
  document.addEventListener("play", function (ev) {
    document.querySelectorAll("audio").forEach(function (a) { if (a !== ev.target) a.pause(); });
  }, true);
  function entries() {
    var out = [];
    document.querySelectorAll(".row[data-id]").forEach(function (r) {
      var id = r.dataset.id, s = state[id] || {};
      out.push({ id: id, verdict: s.v || "", note: s.note || "" });
    });
    return out;
  }
  document.getElementById("export").addEventListener("click", function () {
    var blob = new Blob([JSON.stringify({ sheet: KEY, exported: new Date().toISOString(), entries: entries() }, null, 2)], { type: "application/json" });
    var a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = KEY + ".json";
    document.body.appendChild(a); a.click(); a.remove();
  });
  document.getElementById("text").addEventListener("click", function () {
    var t = document.getElementById("out");
    var lines = entries().filter(function (r) { return r.verdict || r.note; })
      .map(function (r) { return r.id + ": " + (r.verdict || "-") + (r.note ? " - " + r.note : ""); });
    t.value = lines.length ? lines.join("\\n") : "(nothing marked yet)";
    t.hidden = false; t.focus(); t.select();
  });
  update();
})();
</script>
</body>
</html>
"""

if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--page"]
    build(args[0] if args else datetime.date.today().isoformat(), audio="--page" not in sys.argv)
