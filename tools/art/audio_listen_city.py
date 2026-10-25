"""The listening page for Carillon's soundscape (M4 B3b, SPEC §16.0 decision 28: "the gate is Euan's ears").

    python3 tools/art/audio_listen_city.py [--page]   writes docs/audio/carillon-b3b/index.html and its MP3s
                                                      --page: only the page, from the MP3s already there

What it renders, offline, from the shipped files (nothing recorded, nothing of Mojang's):
  - every district at morning (06:00, the dawn carillon), noon (the noon carillon, the market's peak) and night (22:00):
    "after" is the district's bed at the level the server would pick (citizens.Soundscape: the roster's people outdoors
    in that district at that hour against its busiest hour; the city's night bed after dark) under the places and
    one-shots a listener standing there hears (citizens.client.CitySound's hours, rates and volumes; Minecraft's linear
    falloff over each event's attenuation_distance; equal-power panning by direction). "before" is the same spot before
    B3b: the Choir striking the hour (M3) and otherwise silence;
  - every sound alone: each bed stem, each place loop, each one-shot event with its variants, the carillon's tunes;
  - keep / remake / cut and a note on each, kept in the browser, exported as JSON or text.
Approximations (said on the page): no Sound Physics reverb, no head-related panning, vanilla's voices the animals use
(cats, dogs, hooves) are left out because they can't be copied into a public repo.
"""
import html
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
from audio_listen import TEMPLATE as GATE_SHEET, excerpt, mp3, verdict  # noqa: E402  (the gate sheet's look and helpers)

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
MANIFESTS = os.path.join(ROOT, "src/main/resources/assets/awaken/music_layers/city")
EVENTS = os.path.join(ROOT, "src/main/merge/citizens/assets/awaken/sounds.json")
OUT = os.path.join(ROOT, "docs/audio/carillon-b3b")
FOLDER = "carillon-b3b"

SCENE_SECONDS = 36.0
LEVELS = ["calm", "tension", "combat", "lord"]
LEVEL_WORDS = {"calm": "quiet", "tension": "light", "combat": "busy", "lord": "packed"}

# citizens.Soundscape: PEAK (outdoors at the busiest hour) and the roster's outdoor counts at 06:00 and 12:00 (B3a report)
PEAK = {"market": 169, "bellwatch": 42, "quay": 55, "terrace": 21, "works": 41, "garden": 20, "wallring": 21}
OUTDOORS = {"morning": {"market": 9, "bellwatch": 10, "quay": 4, "terrace": 5, "works": 7, "garden": 6, "wallring": 2},
            "noon": {"market": 169, "bellwatch": 25, "quay": 55, "terrace": 21, "works": 41, "garden": 16, "wallring": 21}}
# the spire's distance from where each district's listener stands (the plan: the Choir is in Choir Square)
SPIRE = {"market": 45, "bellwatch": 150, "quay": 140, "terrace": 175, "works": 205, "garden": 215, "wallring": 290}


def level_for(district, time):
    """citizens.Soundscape.level / nightLevel: the bed's level for a district at a time."""
    if time == "night":
        return "tension"  # 22:00 (tod 16000): the taverns' last hours
    f = OUTDOORS[time][district] / max(1, PEAK[district])
    return "calm" if f < 0.15 else "tension" if f < 0.45 else "combat" if f < 0.8 else "lord"


# ---- what a listener hears at each district (CitySound's hours, rates, volumes; distances from the plan) ------------
# shot: (event, distance m, bearing deg (0 ahead, 90 right), volume, (min s, max s) between, pitch spread)
# loop: (event, distance, bearing, level)
# once: (event, at s, distance, bearing, volume)
DAY_SHOTS = {
    "market": [("city.stall", 7, -40, 0.55, (2.0, 5.5), 0.25), ("city.stall", 12, 55, 0.55, (3.0, 7.0), 0.25),
               ("city.pigeon.coo", 5, 150, 0.35, (4.0, 9.0), 0.25), ("city.gull.call", 34, 20, 0.7, (6.0, 16.0), 0.3),
               ("city.cart.roll", 11, 80, 0.5, (9.0, 14.0), 0.2), ("city.songbird", 26, -110, 0.4, (2.5, 7.5), 0.3)],
    "bellwatch": [("city.pigeon.coo", 14, 30, 0.35, (3.0, 8.0), 0.25), ("city.songbird", 18, -70, 0.4, (2.5, 7.5), 0.3),
                  ("city.songbird", 24, 120, 0.4, (3.0, 8.0), 0.3), ("city.gull.call", 40, -20, 0.7, (8.0, 18.0), 0.3)],
    "quay": [("city.boat", 10, 60, 0.5, (4.5, 13.0), 0.3), ("city.boat", 19, -50, 0.5, (5.0, 13.0), 0.3),
             ("city.gull.call", 22, 10, 0.7, (4.0, 11.0), 0.3), ("city.gull.call", 30, -100, 0.6, (5.0, 13.0), 0.3),
             ("city.cart.roll", 14, 160, 0.5, (10.0, 16.0), 0.2)],
    "terrace": [("city.songbird", 14, -30, 0.4, (2.5, 7.5), 0.3), ("city.songbird", 22, 100, 0.4, (3.0, 8.0), 0.3),
                ("city.pigeon.coo", 16, 60, 0.35, (5.0, 10.0), 0.25)],
    "works": [("city.anvil", 17, 35, 0.85, (2.5, 6.0), 0.1), ("city.bellows", 17, 35, 0.6, (9.0, 16.0), 0.2),
              ("city.cart.roll", 12, -80, 0.5, (8.0, 14.0), 0.2), ("city.anvil", 34, -120, 0.85, (4.0, 8.0), 0.1)],
    "garden": [("city.songbird", 8, -20, 0.4, (1.2, 4.0), 0.3), ("city.songbird", 15, 70, 0.4, (1.5, 4.5), 0.3),
               ("city.songbird", 24, -130, 0.4, (2.0, 6.0), 0.3), ("city.pigeon.coo", 12, 150, 0.35, (5.0, 10.0), 0.25)],
    "wallring": [("city.pigeon.coo", 20, -60, 0.35, (4.0, 10.0), 0.25), ("city.gull.call", 42, 40, 0.7, (8.0, 18.0), 0.3),
                 ("city.songbird", 30, 110, 0.4, (3.0, 9.0), 0.3)],
}
# CitySound's clocks: anvils 900-10800, stalls 1000-11500, carts before 12600; at 06:00 (tod 0) the forges and stalls
# are still shut, and the dawn chorus (before tod 1500) sings three times as often
MORNING_SHUT = {"city.anvil", "city.bellows", "city.stall"}
LOOPS = {
    "market": [("city.fountain", 9, -75, 0.8)],
    "quay": [("city.lap", 6, 90, 0.8), ("city.lap", 15, -90, 0.8)],
    "works": [("city.mill", 21, 120, 0.9)],
}
NIGHT = {
    "market": dict(loops=[("city.fountain", 9, -75, 0.8), ("city.tavern", 11, 40, 0.9)],
                   shots=[("city.boat", 60, 0, 0.5, (8.0, 13.0), 0.3)], once=[("city.handbell", 14.0, 16, 120, 0.25)]),
    "bellwatch": dict(loops=[("city.tavern", 8, -50, 0.9)], shots=[], once=[("city.handbell", 9.0, 12, 80, 0.25)]),
    "quay": dict(loops=[("city.lap", 6, 90, 0.8), ("city.lap", 15, -90, 0.8), ("city.tavern", 16, 160, 0.9)],
                 shots=[("city.boat", 10, 60, 0.5, (4.5, 13.0), 0.3)], once=[]),
    "terrace": dict(loops=[], shots=[], once=[("city.handbell", 20.0, 22, -40, 0.25)]),
    "works": dict(loops=[("city.mill", 21, 120, 0.9), ("city.tavern", 13, -30, 0.9)], shots=[], once=[]),
    "garden": dict(loops=[], shots=[], once=[]),
    "wallring": dict(loops=[], shots=[], once=[("city.handbell", 12.0, 18, 30, 0.25)]),
}
CARILLON_AT = 3.0  # the tune starts a few seconds in
# the Choir strikes the hour (M3; citizens.CitizenWork.ringHour): a stroke every 46 ticks at pitch 0.84, volume 6; since
# B3b it waits AFTER_TUNE (300 ticks) at dawn, noon and dusk while the carillon plays
STROKES = {"morning": 6, "noon": 12, "night": 10}
STROKE_GAP, STROKE_PITCH, AFTER_TUNE = 46 / 20, 0.84, 300 / 20

DISTRICTS = [
    ("market", "Choir Square and the Market", "on the square's floor by the stalls, a fountain to your left"),
    ("bellwatch", "Bellwatch", "a stepped lane under the Knoll, its bell tower up the hill"),
    ("quay", "Lantern Quay", "on the quay's edge, water lapping below, boats moored along the wall"),
    ("terrace", "the High Terrace", "on the Terrace's formal walk, gardens behind the houses"),
    ("works", "the Works", "in the yard between two smithies, the mill race behind"),
    ("garden", "Garden Ward", "under the cherry trees"),
    ("wallring", "the Wallring", "inside the ring wall, a tower's pigeons above"),
]
TIMES = [("morning", "06:00", "the dawn carillon, then six strokes; the forges and stalls are still shut"),
         ("noon", "12:00", "the noon carillon, then twelve strokes; the busiest hour outdoors"),
         ("night", "22:00", "ten strokes; the night bed, taverns, the Watch's handbell")]


# ---- audio ------------------------------------------------------------------------------------------------------------

_cache = {}


def ogg(path):
    if path not in _cache:
        _cache[path] = K.read_audio(os.path.join(SOUNDS, path + ".ogg"))
    return _cache[path]


def event(eid):
    """An event's variants (mono or stereo arrays) and its attenuation distance."""
    ev = json.load(open(EVENTS))[eid]
    files = [s["name"].split(":", 1)[1] for s in ev["sounds"]]
    att = ev["sounds"][0].get("attenuation_distance", 16)
    return [ogg(f) for f in files], att


def pitched(x, p):
    if abs(p - 1) < 1e-3:
        return x
    n = int(len(x) / p)
    t = np.arange(n) * p
    if x.ndim == 1:
        return np.interp(t, np.arange(len(x)), x)
    return np.stack([np.interp(t, np.arange(len(x)), x[:, c]) for c in range(x.shape[1])], axis=1)


def place(y, x, at, dist, bearing, vol, att):
    """A sound at a place: Minecraft's volume clamp and linear falloff, equal-power pan by bearing; stereo stays as is."""
    g = min(1.0, vol) * max(0.0, 1.0 - dist / att)
    if g <= 0:
        return
    i = K.ns(at)
    if i >= len(y):
        return
    if x.ndim == 1:
        q = (np.sin(np.deg2rad(bearing)) + 1) * np.pi / 4  # left 0 .. right pi/2
        s = np.stack([x * np.cos(q), x * np.sin(q)], axis=1)
    else:
        s = x
    n = min(len(s), len(y) - i)
    y[i:i + n] += s[:n] * g


def bed(context, level, n):
    m = json.load(open(os.path.join(MANIFESTS, context + ".json")))
    g = dict((lv["name"], lv["gains"]) for lv in m["levels"])[level]
    y = np.zeros((n, 2))
    for s, gain in zip(m["stems"], g):
        if gain <= 0:
            continue
        x = K.stereo(ogg(s["file"].split(":", 1)[1]))
        y += np.tile(x, (n // len(x) + 1, 1))[:n] * gain
    return y


def loop(y, eid, dist, bearing, lv):
    xs, att = event(eid)
    x = xs[0]
    x = np.tile(x, (len(y) // len(x) + 1,) + (1,) * (x.ndim - 1))[:len(y)]
    place(y, x, 0, dist, bearing, lv, att)


def shots(y, spec, rng, rate=1.0):
    eid, dist, bearing, vol, (lo, hi), spread = spec
    xs, att = event(eid)
    t = rng.uniform(0.3, lo)
    while t < SCENE_SECONDS - 1:
        x = pitched(xs[rng.integers(len(xs))], 1 + rng.uniform(-0.5, 0.5) * spread)
        place(y, x, t, dist + rng.uniform(-2, 2), bearing + rng.uniform(-15, 15), vol, att)
        t += rng.uniform(lo, hi) / rate


def fade(y):
    fi, fo = K.ns(1.5), K.ns(2.5)
    y[:fi] *= np.linspace(0, 1, fi)[:, None]
    y[-fo:] *= np.linspace(1, 0, fo)[:, None]
    return y


def scene(district, time, after=True):
    n = K.ns(SCENE_SECONDS)
    rng = np.random.default_rng(sum(map(ord, district + time)))
    y = bed("night" if time == "night" else district, level_for(district, time), n) if after else np.zeros((n, 2))
    if after:
        if time == "night":
            spec = NIGHT[district]
            for lp in spec["loops"]:
                loop(y, *lp)
            for sh in spec["shots"]:
                shots(y, sh, rng)
            for eid, at, dist, bearing, vol in spec["once"]:
                xs, att = event(eid)
                place(y, xs[0], at, dist, bearing, vol, att)
        else:
            for lp in LOOPS.get(district, []):
                loop(y, *lp)
            for sh in DAY_SHOTS[district]:
                if time == "morning" and sh[0] in MORNING_SHUT:
                    continue
                chorus = time == "morning" and sh[0] == "city.songbird"
                shots(y, sh, rng, 3.0 if chorus else 1.0)
            xs, att = event("city.carillon.dawn" if time == "morning" else "city.carillon.noon")
            place(y, xs[0], CARILLON_AT, SPIRE[district], 0, 4.0, att)
    xs, att = event("city.bell.hour")
    stroke = pitched(xs[0], STROKE_PITCH)
    t0 = CARILLON_AT + (AFTER_TUNE if after and time != "night" else 0)
    for k in range(STROKES[time]):
        place(y, stroke, t0 + k * STROKE_GAP, SPIRE[district], 0, 6.0, att)
    return fade(y)


def alone(eid, gap=0.6):
    """Every variant of an event one after another, as heard 4 blocks away."""
    xs, att = event(eid)
    parts = []
    for x in xs:
        s = K.stereo(x) * max(0.0, 1 - 4 / att)
        parts += [s, np.zeros((K.ns(gap), 2))]
    y = np.concatenate(parts)
    if len(y) > K.ns(24):
        y = excerpt(y)
    return y


# ---- the page ---------------------------------------------------------------------------------------------------------

STEM_LINES = {
    "air": "the city's own air: wind over roofs and in the lanes, far off and soft",
    "murmur_far": "a crowd heard across a square: many formant voices, no words, darkened by distance",
    "murmur_near": "people a few metres off: the same voices nearer, still never words",
    "bustle": "footsteps, doors, a barrow, a dropped crate: the work of a street",
    "water": "the river and the canals under everything near the water",
    "works": "the Works' yards: distant hammering, a saw, the bellows' breath",
    "birds": "sparrows and swifts over the roofs by day",
    "crickets": "the night's insects in the gardens and the wall's grass",
    "night_far": "the city asleep: a dog far off, a shutter, the wind in the towers",
    "tavern_far": "taverns heard across the city at night: laughter and a fiddle through walls",
}
EVENT_LINES = {
    "city.fountain": "the two fountains in Choir Square (a loop at the fountain, heard within 18 blocks)",
    "city.lap": "water lapping along both quays (a point every 16 blocks, the nearest three play)",
    "city.mill": "the mill wheels on the race",
    "city.tavern": "a tavern's evening through its walls (11000-18500, muffled)",
    "city.anvil": "the four smithies' hammers, 07:00-16:48",
    "city.bellows": "a forge's bellows now and then",
    "city.stall": "a stall's goods handled, a coin, a crate set down (07:00-17:30)",
    "city.boat": "boats creaking at their moorings along the river",
    "city.songbird": "songbirds in the gardens and the Terrace (a dawn chorus till 07:30)",
    "city.pigeon.coo": "a flock on the square, a well, a bell tower",
    "city.pigeon.flutter": "a flock bursting up as you run past (and when its tower's bell rings)",
    "city.gull.call": "gulls over the river and on the nets",
    "city.cart.roll": "a cart's wheels on the stones (vanilla's hooves go with it in game)",
    "city.cart.bells": "a carthorse's harness bells when it tosses its head",
    "city.handbell": "the Watch's handbell on its rounds at night",
    "city.carillon.dawn": "the Choir's carillon at dawn, from the spire",
    "city.carillon.noon": "the Choir's carillon at noon",
    "city.carillon.dusk": "the Choir's carillon at dusk",
}


def build(audio=True):
    os.makedirs(OUT, exist_ok=True)
    files = {}

    def write(name, fn, kbps=112):
        path = os.path.join(OUT, name)
        if audio:
            mp3(path, fn(), kbps)
        elif not os.path.exists(path):
            sys.exit(f"no {name} (run without --page)")
        files[name] = f"{FOLDER}/{name}"
        return files[name]

    # before B3b only the Choir struck the hour (nearer or farther by district); otherwise the city was silent
    cards = []
    for key, title, spot in DISTRICTS:
        times = []
        for t, clock, what in TIMES:
            f = write(f"{key}_{t}.mp3", lambda key=key, t=t: scene(key, t))
            before = write(f"{key}_{t}_before.mp3", lambda key=key, t=t: scene(key, t, after=False), 80)
            times.append(dict(t=t, clock=clock, what=what, file=f, before=before,
                              level=level_for(key, t), bed="night" if t == "night" else key))
        cards.append(dict(id=key, title=title, spot=spot, times=times))
        print(f"  {key}: 3 scenes")
    stems = []
    for name, line in STEM_LINES.items():
        f = write(f"stem_{name}.mp3", lambda name=name: excerpt(K.stereo(ogg("layers/layer_city_" + name))), 96)
        stems.append(dict(id="stem." + name, name=name, line=line, file=f))
    evs = []
    for eid, line in EVENT_LINES.items():
        f = write(f"{eid.replace('.', '_')}.mp3", lambda eid=eid: alone(eid), 96)
        evs.append(dict(id=eid, name=eid.replace("city.", ""), line=line, file=f))
    page = render(cards, stems, evs)
    with open(os.path.join(OUT, "index.html"), "w") as f:
        f.write(page)
    size = sum(os.path.getsize(os.path.join(OUT, x)) for x in os.listdir(OUT)) / 1e6
    print(f"wrote docs/audio/{FOLDER}/index.html and {len(os.listdir(OUT))} files ({size:.1f} MB)")


def row(rid, tag, line, src, meta=""):
    e = html.escape
    return f"""
        <div class="row" data-id="{e(rid)}">
          <div class="what"><span class="tag">{e(tag)}</span><span class="intent">{e(line)}</span>
            {f'<span class="meta">{e(meta)}</span>' if meta else ''}</div>
          <audio controls preload="none" src="{e(src)}"></audio>
          {verdict()}
        </div>"""


def render(cards, stems, evs):
    e = html.escape
    body = []
    for i, c in enumerate(cards):
        rows = []
        for t in c["times"]:
            rows.append(f"""
        <div class="row scene" data-id="{e(c['id'])}.{t['t']}">
          <div class="what"><span class="tag strong">{e(t['t'])} · {e(t['clock'])}</span><span class="intent">{e(t['what'])}</span>
            <span class="meta">bed awaken:city/{e(t['bed'])} at "{LEVEL_WORDS[t['level']]}" ({e(t['level'])})</span></div>
          <div class="ab"><label>after</label><audio controls preload="none" src="{e(t['file'])}"></audio>
            <label>before</label><audio controls preload="none" src="{e(t['before'])}"></audio></div>
          {verdict()}
        </div>""")
        body.append(f"""
    <section class="card" id="{e(c['id'])}">
      <header><span class="n">{i + 1:02d}</span><h2>{e(c['title'])}</h2><p class="line">where you stand: {e(c['spot'])}</p></header>
      {''.join(rows)}
    </section>""")
    alone_rows = "".join(row(s["id"], s["name"], s["line"], s["file"], "a bed stem alone, 20 s") for s in stems)
    ev_rows = "".join(row(v["id"], v["name"], v["line"], v["file"], "every variant, 4 blocks away") for v in evs)
    body.append(f"""
    <section class="card" id="alone">
      <header><h2>Every sound alone</h2><p class="line">mark one here only when it bothers you in a scene</p></header>
      <details><summary>the beds' {len(stems)} stems</summary>{alone_rows}</details>
      <details><summary>the {len(evs)} places and one-shots</summary>{ev_rows}</details>
    </section>""")
    style = GATE_SHEET[GATE_SHEET.index("<style>"):GATE_SHEET.index("</style>") + len("</style>")]
    style = style.replace("</style>", EXTRA_STYLE + "</style>")
    scenes = len(cards) * len(TIMES)
    page = PAGE.replace("%STYLE%", style).replace("%CARDS%", "".join(body)).replace("%SCENES%", str(scenes)) \
        .replace("%SOUNDS%", str(len(stems) + len(evs))).replace("%MINUTES%", str(round(scenes * SCENE_SECONDS / 60)))
    for k, v in SECTIONS.items():
        page = page.replace("%" + k + "%", open(os.path.join(HERE, "specs", "listen_city", v)).read())
    return page


EXTRA_STYLE = """
.intro a, .notes a { color:var(--accent); }
.ab { display:grid; grid-template-columns:auto 1fr; gap:4px 10px; align-items:center; }
.ab label { color:var(--soft); font-size:12px; }
.notes h3 { font-size:15px; margin:14px 0 4px; }
.notes table { border-collapse:collapse; width:100%; font-size:13px; display:block; overflow-x:auto; }
.notes th, .notes td { text-align:left; border-top:1px solid var(--line); padding:6px 8px; vertical-align:top; }
.notes td a { color:var(--accent); overflow-wrap:anywhere; }
.notes li { margin:3px 0; }
"""
SECTIONS = {"RECOMMEND": "recommendations.html", "NATURE": "nature.html"}

PAGE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Carillon soundscape listening</title>
%STYLE%
</head>
<body>
<main>
<h1>Carillon: the city's sound</h1>
<p class="sub">AWAKEN · Carillon M4 B3b · %SCENES% scenes (seven districts at morning, noon and night), %SOUNDS% sounds alone</p>
<div class="intro">
  <p><b>About %MINUTES% minutes.</b> Each district has three moments. Play <b>after</b>: what you'd hear standing there
  with this build. <b>Before</b> is the same spot before it: only the Choir striking the hour, otherwise silence.
  Mark each moment <b>keep</b>, <b>remake</b> or <b>cut</b>, and say why in the note when it isn't a keep.</p>
  <p>In the game every sound comes from a place: a quiet bed for the district (it follows how many people are outdoors
  there now, and turns into the night bed after dark), with fountains, stalls, forges, boats, birds, the Watch's
  handbell and the carillon at their own places and hours. Rendered here offline from the shipped files, so three
  things differ from the game: no Sound Physics echo down the lanes, plain stereo panning, and the animals' vanilla
  voices (cats, dogs, hooves) are left out because they can't be copied into the public repo. Headphones help.</p>
  <p>The city's own sounds are synthesised; nature comes from AmbientSounds when you have it (<a href="#nature">below</a>),
  and those recordings are heard in the game, not here. The weak spot is the crowd.</p>
  <p>Your choices stay in this browser. When you're done, <b>Export</b> and send me the file (or the text).</p>
</div>
<div class="bar"><span class="progress" id="progress"></span>
  <button id="export">Export choices (.json)</button><button id="text">Show as text</button></div>
<section class="card notes" id="recommend">%RECOMMEND%</section>
%CARDS%
<section class="card notes" id="nature">%NATURE%</section>
<textarea id="out" hidden readonly></textarea>
</main>
<script>
(function () {
  var KEY = "awaken-listening-carillon-b3b";
  var state = {};
  try { state = JSON.parse(localStorage.getItem(KEY) || "{}") || {}; } catch (e) { state = {}; }
  function save() { try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {} update(); }
  function update() {
    var sc = 0, ns = 0, so = 0, nso = 0;
    document.querySelectorAll(".row[data-id]").forEach(function (r) {
      var id = r.dataset.id, v = (state[id] || {}).v || "";
      if (r.classList.contains("scene")) { ns++; if (v) sc++; } else { nso++; if (v) so++; }
      r.querySelectorAll(".choices button").forEach(function (b) { b.setAttribute("aria-pressed", v === b.dataset.v ? "true" : "false"); });
    });
    document.getElementById("progress").textContent = "scenes " + sc + " of " + ns + " · sounds alone " + so + " of " + nso;
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
  function rows() {
    var out = [];
    document.querySelectorAll(".row[data-id]").forEach(function (r) {
      var id = r.dataset.id;
      out.push({ id: id, verdict: (state[id] || {}).v || "", note: (state[id] || {}).note || "" });
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
    var lines = rows().filter(function (r) { return r.verdict || r.note; })
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
    build(audio="--page" not in sys.argv)
