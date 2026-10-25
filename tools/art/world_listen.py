#!/usr/bin/env python3
"""World sound listening page (docs/sound/PLAN.md §9): AWAKEN's own synthesised places and moments, rendered from the
shipped files and the soundscape data the game reads, so Euan can check the quality bar quickly.

    python3 tools/art/world_listen.py [date]     -> docs/sound/listening-<date>/*.mp3 and docs/sound/listening-<date>.html

Each place gets a 30 s mix as the Conductor layers it (its air beds at their gains and the hour they suit, its one-shots
placed left and right at their distances; one-shots come more often than in game so a short clip holds several);
the Veil and the Living Wilds happenings get short scenes; every file is also there alone. Clips are levelled for
listening (about -20 dBFS); in game they sit lower, under the music, on the Ambient slider.
"""
import html
import json
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
SOUNDS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
SCAPES = os.path.join(ROOT, "src/main/resources/assets/awaken/soundscape")
MERGE = os.path.join(ROOT, "src/main/merge")
DATE = sys.argv[1] if len(sys.argv) > 1 else "2026-10-25"
OUT = os.path.join(ROOT, "docs/sound", f"listening-{DATE}")
PAGE = os.path.join(ROOT, "docs/sound", f"listening-{DATE}.html")
RNG = np.random.default_rng(7)
_cache = {}


def events():
    ev = {}
    for m in sorted(os.listdir(MERGE)):
        p = os.path.join(MERGE, m, "assets/awaken/sounds.json")
        if os.path.exists(p):
            ev.update(json.load(open(p)))
    return ev


EVENTS = events()


def audio(stem):
    if stem not in _cache:
        x = K.read_audio(os.path.join(SOUNDS, stem + ".ogg"))
        _cache[stem] = K.mono(x) if x.ndim == 2 else x
    return _cache[stem]


def stereo_audio(stem):
    x = K.read_audio(os.path.join(SOUNDS, stem + ".ogg"))
    return x if x.ndim == 2 else np.stack([x, x], axis=1)


def files_of(event):
    """An awaken: event's files (other events it names are followed; vanilla ones are skipped)."""
    ns, path = event.split(":", 1)
    if ns != "awaken" or path not in EVENTS:
        return []
    out = []
    for s in EVENTS[path]["sounds"]:
        name = s if isinstance(s, str) else s["name"]
        if isinstance(s, dict) and s.get("type") == "event":
            out += files_of(name)
        else:
            out.append(name.split(":", 1)[1])
    return out


def curve(when, tick):
    """DayCurve.at (core.audio.world.DayCurve): six anchors eased with a cosine."""
    when = when or {}
    day, night = when.get("day", 1.0), when.get("night", 1.0)
    v = [when.get("dawn", 1.0), when.get("morning", day), day, when.get("dusk", 1.0), night, when.get("late", night)]
    at = [-500, 2000, 6000, 12500, 15000, 21000, 23500]
    t = (tick + 500) % 24000 - 500
    i = 0
    while i < len(at) - 2 and t >= at[i + 1]:
        i += 1
    k = (t - at[i]) / (at[i + 1] - at[i])
    e = 0.5 - 0.5 * math.cos(math.pi * k)
    a, b = v[i], v[(i + 1) % 6] if i + 1 < 6 else v[0]
    return a + (b - a) * e


def looped(x, n):
    reps = int(np.ceil(n / len(x))) + 1
    return np.concatenate([x] * reps)[:n] if x.ndim == 1 else np.concatenate([x] * reps, axis=0)[:n]


def place_at(mix, x, t, pan, gain):
    s = K.ns(t)
    if s >= len(mix):
        return
    st = K.pan(x * gain, pan)
    e = min(len(mix), s + len(st))
    mix[s:e] += st[: e - s]


def pitched(x, ratio):
    if abs(ratio - 1) < 1e-3:
        return x
    idx = np.arange(0, len(x) - 1, ratio)
    return np.interp(idx, np.arange(len(x)), x)


def level(x, rms=-20.0):
    r = K.gated_rms(x)
    y = x * K.dbamp(rms - r)
    peak = np.max(np.abs(y))
    return y / peak * 0.97 if peak > 0.97 else y


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".mp3")
    ch = 1 if x.ndim == 1 else 2
    import subprocess
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(K.SR), "-ac", str(ch), "-i", "pipe:0",
                    "-map_metadata", "-1", "-c:a", "libmp3lame", "-b:a", "128k", path],
                   input=np.clip(x, -1, 1).astype("<f4").tobytes(), check=True)
    return os.path.basename(path)


def scape_mix(path, tick, seconds=30.0, density=3.0, extra=None):
    sc = json.load(open(os.path.join(SCAPES, path + ".json")))
    n = K.ns(seconds)
    mix = np.zeros((n, 2))
    for a in sc.get("air", []):
        stem = a["file"].split(":", 1)[1]
        x = stereo_audio(stem)
        g = a.get("gain", 1.0) * curve(a.get("when"), tick) * (0.7 if a.get("drive") == "wind" else 1.0)
        mix += looped(x, n) * g
    # fade the bed in and out so the clip starts and ends gently
    ramp = np.minimum(1, np.minimum(np.arange(n) / K.ns(2.0), (n - np.arange(n)) / K.ns(2.0)))
    mix *= ramp[:, None]
    for e in sc.get("events", []):
        fl = files_of(e["sound"])
        if not fl:
            continue
        rate = e["per_minute"] * curve(e.get("when"), tick) * density / 60.0
        if rate <= 0:
            continue
        count = max(2, RNG.poisson(rate * seconds))
        vol = sum(e.get("volume", [0.6, 1.0])) / 2
        dist = sum(e.get("distance", [8, 32])) / 2
        pitch = sum(e.get("pitch", [1.0, 1.0])) / 2
        for _ in range(count):
            t = RNG.uniform(1.0, seconds - 3.0)
            pan = RNG.uniform(-0.8, 0.8)
            x = pitched(audio(fl[RNG.integers(len(fl))]), pitch)
            place_at(mix, x, t, pan, vol * 12 / (12 + dist) * 2.2)
            if e.get("answer", 0) > 0 and RNG.random() < e["answer"]:
                d = RNG.uniform(*e.get("answer_delay", [1, 3]))
                place_at(mix, pitched(audio(fl[RNG.integers(len(fl))]), pitch), t + d, -pan * 0.8, vol * 12 / (12 + dist) * 1.8)
    if extra:
        extra(mix)
    return level(mix)


def pieces(stems, gap=0.5, cap=20.0):
    parts = []
    for s in stems:
        x = audio(s)[: K.ns(cap)]
        parts += [x, np.zeros(K.ns(gap))]
    return level(np.concatenate(parts))


def approach(stem, seconds=16.0, far=0.06):
    """A happening heard as you come near it: faint and to one side, then closer and centred."""
    n = K.ns(seconds)
    x = looped(audio(stem), n)
    g = far + (1 - far) * (np.arange(n) / n) ** 2
    pan = 0.75 * (1 - np.arange(n) / n)
    a = (pan + 1) * np.pi / 4
    st = np.stack([x * g * np.cos(a), x * g * np.sin(a)], axis=1)
    ramp = np.minimum(1, (n - np.arange(n)) / K.ns(1.0))
    return level(st * ramp[:, None])


def hunt(seconds=24.0):
    n = K.ns(seconds)
    mix = np.zeros((n, 2))
    horns, bays = files_of("awaken:living.tell.horn"), files_of("awaken:fauna.wisp_hound.ambient")
    for t, g in ((1.0, 0.5), (12.0, 0.9)):
        place_at(mix, audio(horns[int(RNG.integers(len(horns)))]), t, -0.4, g)
    for t in np.arange(4.0, seconds - 2, 2.2):
        place_at(mix, pitched(audio(bays[int(RNG.integers(len(bays)))]), 0.7), t + RNG.uniform(0, 0.8), RNG.uniform(-0.7, 0.2), 0.25 + 0.5 * t / seconds)
    return level(mix)


def calls(event, seconds=16.0, pitch=1.0, every=(2.0, 3.5), up=0.0):
    n = K.ns(seconds)
    mix = np.zeros((n, 2))
    fl = files_of(event)
    t = 0.8
    while t < seconds - 2:
        place_at(mix, pitched(audio(fl[int(RNG.integers(len(fl)))]), pitch), t, RNG.uniform(-0.6, 0.6), 0.6)
        t += RNG.uniform(*every)
    return level(mix)


def echo_scene():
    """Mirror Marsh: your own sound, and the marsh answering from across the water."""
    sc = json.load(open(os.path.join(SCAPES, "place/mirror_marsh.json")))["echo"]
    n = K.ns(14.0)
    mix = np.zeros((n, 2))
    mix += looped(stereo_audio("amb_mirror_marsh"), n) * 0.9
    steps = files_of("awaken:body.wallstep") or files_of("awaken:body.grab")
    for t in (1.5, 6.0, 10.0):
        x = audio(steps[int(RNG.integers(len(steps)))])
        place_at(mix, x, t, 0.0, 0.8)
        d = RNG.uniform(sc["delay"][0], sc["delay"][1])
        place_at(mix, K.lowpass(pitched(x, sc["pitch"]), 4000, 2), t + d, 0.7, 0.8 * sc["volume"])
    return level(mix)


def main():
    clips = []  # (section, name, title, what, file)

    def add(section, name, title, what, x):
        clips.append((section, name, title, what, write(name, x)))
        print(f"  {name}")

    places = [
        ("stillpoint", 6000, "The Stillpoint", "Wind over the cloud sea, a held temple tone, chimes stirred by the wind, a far bell."),
        ("halo_orbit", 6000, "The Halo", "The station's hum and ventilation, airlock hisses, far metal knocks, data chirps."),
        ("lucidium_dream", 6000, "A dream (Lucidium), fully lucid", "A soft shimmering chord, breathing air, reversed glass swells."),
        ("elderwood", 4000, "Elderwood, a breezy morning", "The giant trees groaning in the gusts, great limbs creaking, gold glints. AmbientSounds' forest plays under this in game."),
        ("lumen_forest", 18000, "Lumen Forest at night", "A low hum, and the specks chiming and answering each other. AmbientSounds' night plays under this in game."),
    ]
    for path, tick, title, what in places:
        add("Places", "place_" + path, title, what, scape_mix("place/" + path, tick))

    def muffled(mix):
        mix[:] = K.lowpass(mix, 900, 2)
    add("Places", "place_lucidium_dream_low", "The same dream at lucidity 0", "The same place, muffled: it clears as you become lucid.",
        scape_mix("place/lucidium_dream", 6000, seconds=20.0, extra=muffled))
    add("Moments", "veil", "The Veil, while you project", "A far choir on D and A and faint glints, the world muffled behind it.",
        scape_mix("state/veil", 6000, seconds=24.0))
    add("Moments", "mirror_marsh", "Mirror Marsh answers", "Your own sound, then the marsh sending it back from across the water.", echo_scene())
    add("Happenings", "tell_stampede", "A stampede, as you come near", "Hooves and a ground rumble, far off to one side, then close.", approach("amb_tell_stampede"))
    add("Happenings", "tell_wings", "A murmuration", "A great flock turning overhead.", approach("amb_tell_wings"))
    add("Happenings", "tell_tide", "The Deep Tide", "A low drone rolling in with the fog (in game the world falls quiet under it).", approach("amb_tell_tide", far=0.15))
    add("Happenings", "tell_bells", "A caravan", "Small bells at a walking pace.", approach("amb_tell_bells"))
    add("Happenings", "tell_hunt", "The Wild Hunt", "Its horn far off, the hounds baying closer (in game everything else falls silent).", hunt())
    add("Happenings", "tell_kites", "A carrion circle", "Kites crying overhead.", calls("awaken:fauna.peak_kite.ambient"))

    singles = [
        ("Air beds alone (first 20 s)", [("amb_world_stillpoint_sea", "Stillpoint: the cloud sea"), ("amb_world_stillpoint_tone", "Stillpoint: the temple tone"),
                                          ("amb_world_halo_station", "Halo: the station"), ("amb_world_halo_deep", "Halo: the deep quiet"),
                                          ("amb_world_dream_shimmer", "Dream: the shimmer"), ("amb_world_dream_air", "Dream: the air"),
                                          ("amb_world_veil_choir", "Veil: the choir"), ("amb_world_elder_groan", "Elderwood: the groan"),
                                          ("amb_world_lumen_hum", "Lumen Forest: the hum")]),
    ]
    for section, items in singles:
        for stem, title in items:
            add(section, "air_" + stem[len("amb_world_"):], title, "", pieces([stem], cap=20.0))
    shots = [("world.chime", "Stillpoint chimes"), ("world.bell_far", "The far bell"), ("world.halo_hiss", "Halo airlocks"),
             ("world.halo_clunk", "Halo metal"), ("world.halo_chirp", "Halo data chirps"), ("world.dream_swell", "Dream swells"),
             ("world.elder_creak", "Elderwood creaks"), ("world.elder_glint", "Elderwood glints"), ("world.lumen_chime", "Lumen chimes"),
             ("living.tell.horn", "The Hunt's horn")]
    for ev, title in shots:
        fl = files_of("awaken:" + ev)
        add("One-off sounds, every variant in a row", "shot_" + ev.split(".", 1)[1].replace(".", "_"), title, f"{len(fl)} variants", pieces(fl, gap=0.4, cap=7.0))
    page(clips)


def page(clips):
    sections = []
    order = []
    for c in clips:
        if c[0] not in order:
            order.append(c[0])
    for sec in order:
        rows = []
        for _, name, title, what, f in [c for c in clips if c[0] == sec]:
            rows.append(f"""<div class="clip" data-id="{name}">
  <div class="head"><p class="t">{html.escape(title)}</p>{f'<p class="w">{html.escape(what)}</p>' if what else ''}</div>
  <audio controls preload="none" src="listening-{DATE}/{f}"></audio>
  <div class="marks"><button type="button" data-mark="good" aria-pressed="false">Good</button><button type="button" data-mark="not" aria-pressed="false">Not yet</button></div>
</div>""")
        sections.append(f'<section><h2>{html.escape(sec)}</h2><div class="list">{"".join(rows)}</div></section>')
    body = TEMPLATE.replace("__SECTIONS__", "\n".join(sections)).replace("__DATE__", DATE)
    with open(PAGE, "w") as fh:
        fh.write(body)
    print(f"page: {os.path.relpath(PAGE, ROOT)} ({len(clips)} clips)")


TEMPLATE = """<title>World Sound Listening</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Chakra+Petch:wght@600;700&family=IBM+Plex+Sans:wght@400;500;600&family=IBM+Plex+Mono:wght@400&family=Silkscreen&display=swap">
<style>
/* Layout: one column of night glass; one row per clip: what it is, the player, two marks. */
:root {
  --bg: #0a1019; --glass: #0f1724; --line: #22344d; --fg: #e4f1ff; --muted: #8ea5bf; --accent: #7fc3ff;
  --good: #8fdc9a; --not: #ffcf7a; --ink: #0a1019;
  --display: "Chakra Petch", "Avenir Next Condensed", "Arial Narrow", sans-serif;
  --body: "IBM Plex Sans", "Helvetica Neue", Arial, sans-serif;
  --pixel: "Silkscreen", "IBM Plex Mono", ui-monospace, monospace;
  color-scheme: dark;
}
@media (prefers-color-scheme: light) {
  :root:not([data-theme="dark"]) { --bg: #eef3f8; --glass: #ffffff; --line: #cfdbe8; --fg: #0e1a2b; --muted: #4b5f76;
    --accent: #1f6fb5; --good: #23884f; --not: #9a6100; --ink: #ffffff; color-scheme: light; }
}
:root[data-theme="light"] { --bg: #eef3f8; --glass: #ffffff; --line: #cfdbe8; --fg: #0e1a2b; --muted: #4b5f76;
  --accent: #1f6fb5; --good: #23884f; --not: #9a6100; --ink: #ffffff; color-scheme: light; }
* { box-sizing: border-box; }
body { background: var(--bg); color: var(--fg); font: 400 15px/1.55 var(--body);
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); padding-block: 0 80px; }
.wrap { max-width: 860px; margin: 0 auto; display: grid; gap: 32px; }
h1, h2 { font-family: var(--display); margin: 0; line-height: 1.15; }
h1 { font-size: clamp(2rem, 6vw, 3rem); }
h2 { font-size: 1.3rem; }
p { margin: 0; }
.eyebrow { font-family: var(--pixel); font-size: 0.7rem; letter-spacing: 0.12em; text-transform: uppercase; color: var(--accent); }
header { padding-top: 44px; display: grid; gap: 10px; max-width: 66ch; }
.muted { color: var(--muted); }
section { display: grid; gap: 10px; }
.list { border-top: 1px solid var(--line); }
.clip { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 300px) auto; gap: 8px 16px; align-items: center; padding: 12px 0; border-bottom: 1px solid var(--line); }
.clip .t { font-weight: 600; }
.clip .w { color: var(--muted); font-size: 0.88rem; }
audio { width: 100%; min-width: 0; }
.marks { display: flex; gap: 6px; }
.marks button { font: 0.66rem var(--pixel); letter-spacing: 0.08em; text-transform: uppercase; padding: 8px 10px; border: 1px solid var(--line);
  background: var(--glass); color: var(--fg); cursor: pointer; }
.marks button[aria-pressed="true"][data-mark="good"] { background: var(--good); border-color: var(--good); color: var(--ink); }
.marks button[aria-pressed="true"][data-mark="not"] { background: var(--not); border-color: var(--not); color: var(--ink); }
button:focus-visible, audio:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.bar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.bar button { font: 600 0.85rem var(--body); padding: 7px 12px; border: 1px solid var(--accent); background: transparent; color: var(--accent); cursor: pointer; }
#out { width: 100%; min-height: 6em; font: 0.8rem ui-monospace, Menlo, monospace; background: var(--glass); color: var(--fg); border: 1px solid var(--line); padding: 8px; }
@media (max-width: 640px) { .clip { grid-template-columns: 1fr; } }
</style>
<div class="wrap">
<header>
  <p class="eyebrow">AWAKEN · world sound · listening · __DATE__</p>
  <h1>World Sound Listening</h1>
  <p>AWAKEN's own synthesised places and moments, rendered from the game's files and data. Nature is AmbientSounds and
    isn't here. Mark what holds the bar and what doesn't; nothing is required.</p>
  <p class="muted">Clips are levelled for listening; in game they sit lower, under the music. In the place mixes the
    one-off sounds come about three times as often as in game so 30 seconds holds several.</p>
</header>
__SECTIONS__
<section><h2>Your marks</h2><div class="bar"><button type="button" id="copy">Copy marks</button><span class="muted" id="count"></span></div>
<textarea id="out" readonly aria-label="Your marks"></textarea></section>
</div>
<script>
const KEY = "awaken-world-listening-__DATE__";
let marks = {};
try { marks = JSON.parse(localStorage.getItem(KEY) || "{}"); } catch (e) { marks = {}; }
function render() {
  let g = 0, n = 0;
  document.querySelectorAll(".clip").forEach(c => {
    const m = marks[c.dataset.id];
    c.querySelectorAll("button").forEach(b => b.setAttribute("aria-pressed", String(m === b.dataset.mark)));
    if (m === "good") g++; else if (m === "not") n++;
  });
  document.getElementById("count").textContent = g + " good · " + n + " not yet";
  const lines = Object.entries(marks).filter(([, v]) => v).map(([k, v]) => k + ": " + v);
  document.getElementById("out").value = lines.join("\\n");
}
document.querySelectorAll(".clip").forEach(c => c.querySelectorAll("button").forEach(b => b.addEventListener("click", () => {
  marks[c.dataset.id] = marks[c.dataset.id] === b.dataset.mark ? undefined : b.dataset.mark;
  try { localStorage.setItem(KEY, JSON.stringify(marks)); } catch (e) {}
  render();
})));
document.querySelectorAll("audio").forEach(a => a.addEventListener("play", () => {
  document.querySelectorAll("audio").forEach(o => { if (o !== a) o.pause(); });
}));
document.getElementById("copy").addEventListener("click", () => {
  const t = document.getElementById("out");
  navigator.clipboard.writeText(t.value).catch(() => { t.focus(); t.select(); });
});
render();
</script>
"""


if __name__ == "__main__":
    main()
