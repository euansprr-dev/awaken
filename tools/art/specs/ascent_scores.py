"""The movement's sounds as scores of Minecraft's own recordings (SPEC §62; docs/movement/PLAN.md §4.5).

Euan, 9 Nov 2026: built sounds "read fake"; start from Minecraft's own recordings, named in sounds.json. All 24 of the
Ascent's sounds were synthesised and eight of them rang. Here every one is a SCORE: two or three of the game's own
recordings, each at its own volume, pitch and delay. Nothing of Mojang's is in this repo: a layer is a sounds.json entry
that NAMES a vanilla sound event ("type": "event"), and the client plays the layers in turn (AscentScores.java).

    python3 tools/art/specs/ascent_scores.py build         the merge sounds.json and AscentScoreTable.java
    python3 tools/art/specs/ascent_scores.py listen [DIR]  a local page to hear Classic beside New (never hosted: it
                                                           copies recordings out of this machine's own game files)

A layer: (delay in ticks, vanilla sound event, volume 0..1, pitch). Edit the scores here, never the generated files.
The two rappel loops stay as they were (no vanilla recording loops as a rope through a hand does).
"""
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
MERGE = os.path.join(ROOT, "src/main/merge/ascent_scores/assets/awaken/sounds.json")
TABLE = os.path.join(ROOT, "src/main/java/dev/awaken/body/ascent/AscentScoreTable.java")
LANG = os.path.join(ROOT, "src/main/merge/ascent_scores/assets/awaken/lang/en_us.json")
CLASSIC = [os.path.join(ROOT, "src/main/merge", m, "assets/awaken/sounds.json") for m in ("ascent", "ascent_rappel")]

# The Ascent's own sounds (ascent.<name>), each replaced by its score while movementScores is on.
SCORES = {
    # the rope: a cast, a bite, the strain of a held line, the let-go
    "rope_fire": [(0, "entity.fishing_bobber.throw", 0.9, 1.15), (0, "item.crossbow.shoot", 0.5, 1.35), (1, "entity.arrow.shoot", 0.3, 0.8)],
    "rope_bite": [(0, "item.trident.hit_ground", 0.75, 1.1), (0, "block.chain.place", 0.6, 0.9)],
    "rope_strain": [(0, "item.crossbow.loading_middle", 0.7, 0.75), (2, "item.lead.tied", 0.35, 0.7)],
    "rope_release": [(0, "item.lead.untied", 0.8, 1.1), (0, "entity.fishing_bobber.retrieve", 0.5, 1.2)],
    "rope_swoop": [(0, "entity.phantom.swoop", 0.55, 1.25), (0, "entity.breeze.slide", 0.35, 1.1)],
    "rope_snap": [(0, "item.lead.break", 0.8, 1.2), (0, "item.crossbow.shoot", 0.4, 1.6)],
    "launch": [(0, "entity.breeze.jump", 0.8, 1.0), (0, "entity.wind_charge.wind_burst", 0.4, 1.35)],
    # the air
    "wind": [(0, "item.elytra.flying", 1.0, 1.0)],
    "wings_open": [(0, "item.armor.equip_elytra", 0.9, 0.9), (2, "entity.ender_dragon.flap", 0.7, 1.3), (3, "entity.phantom.flap", 0.4, 1.0)],
    "wings_fold": [(0, "item.armor.equip_elytra", 0.6, 1.2), (1, "block.wool.fall", 0.5, 0.9)],
    "flap": [(0, "entity.ender_dragon.flap", 0.7, 1.35), (0, "entity.phantom.flap", 0.6, 0.9)],
    "boom": [(0, "entity.warden.sonic_boom", 0.9, 1.0), (0, "item.mace.smash_air", 0.6, 0.8), (1, "entity.lightning_bolt.thunder", 0.7, 1.3)],
    "land": [(0, "entity.generic.small_fall", 0.7, 0.9), (0, "block.wool.fall", 0.6, 0.8)],
    # the scenes
    "remember": [(0, "block.beacon.activate", 0.8, 1.0), (6, "block.conduit.activate", 0.5, 1.2)],
    "updraft": [(0, "entity.breeze.idle_air", 0.7, 0.8), (0, "entity.breeze.inhale", 0.4, 0.7)],
    "vow_call": [(0, "entity.breeze.inhale", 0.8, 0.6), (5, "block.beacon.activate", 0.4, 1.4)],
    "vow_line": [(0, "entity.fishing_bobber.throw", 1.0, 0.8), (1, "item.trident.throw", 0.6, 1.0), (8, "item.trident.hit_ground", 0.7, 0.9)],
    # the rappel's moments (its two loops stay as they were)
    "rappel_catch": [(0, "item.lead.tied", 0.8, 0.9), (0, "item.crossbow.loading_end", 0.4, 1.1)],
    "rappel_kick": [(0, "entity.generic.small_fall", 0.6, 1.2), (0, "block.wool.hit", 0.6, 0.9)],
    "rappel_plant": [(0, "block.wool.fall", 0.6, 1.0), (0, "block.ladder.step", 0.5, 0.8)],
    "rappel_reel": [(0, "item.crossbow.quick_charge_1", 0.7, 1.1), (1, "item.spyglass.use", 0.4, 1.3)],
    "rappel_fit": [(0, "block.chain.place", 0.8, 1.1), (2, "item.armor.equip_leather", 0.6, 1.0)],
}

# The new moves' own sounds (SPEC §62): no classic twin; with the scores off each falls back to an old sound in code.
MOVES = {
    "sling": [(0, "item.crossbow.shoot", 0.6, 0.9), (0, "entity.breeze.jump", 0.55, 1.2)],
    "spring": [(0, "entity.goat.long_jump", 0.5, 1.2), (0, "entity.wind_charge.wind_burst", 0.4, 1.5)],
    "flare": [(0, "entity.ender_dragon.flap", 0.6, 0.85), (1, "block.wool.fall", 0.5, 0.7), (3, "entity.phantom.flap", 0.3, 0.7)],
    "skim": [(0, "entity.breeze.slide", 0.6, 1.3), (0, "entity.phantom.swoop", 0.3, 1.5)],
    "takeoff": [(0, "item.armor.equip_elytra", 0.8, 1.1), (0, "entity.wind_charge.wind_burst", 0.7, 0.8), (2, "entity.ender_dragon.flap", 0.9, 1.0),
                (9, "entity.ender_dragon.flap", 0.6, 1.2)],
    "strike": [(0, "entity.player.attack.strong", 0.9, 0.85), (0, "item.mace.smash_air", 0.6, 1.2), (1, "entity.player.attack.knockback", 0.5, 0.9)],
    "beat": [(0, "entity.phantom.flap", 0.45, 1.1), (0, "block.wool.hit", 0.2, 0.7)],
    # the rope's hum through the let-go window: the air's own loop, high (a looping layer, driven by the swing)
    "hum": [(0, "item.elytra.flying", 0.6, 1.0)],
}
# Two other takes on the sounds the plan named, for the listening page only (docs/movement/PLAN.md §4.5: "first picks,
# each with two alternates"). To take one, copy its layers into SCORES above and build.
ALTERNATES = {
    "rope_fire": [[(0, "item.trident.throw", 0.8, 1.3), (0, "entity.fishing_bobber.throw", 0.6, 0.9)],
                  [(0, "entity.snowball.throw", 0.8, 0.8), (0, "item.crossbow.shoot", 0.7, 1.1), (1, "entity.wind_charge.throw", 0.4, 1.2)]],
    "rope_bite": [[(0, "block.chain.hit", 0.8, 0.8), (0, "item.trident.hit_ground", 0.5, 1.4)],
                  [(0, "block.tripwire.attach", 0.9, 0.8), (0, "block.chain.place", 0.7, 1.2)]],
    "rope_strain": [[(0, "item.crossbow.loading_end", 0.7, 0.7), (1, "block.ladder.step", 0.3, 0.6)],
                    [(0, "item.lead.tied", 0.8, 0.6), (2, "item.crossbow.loading_middle", 0.4, 1.0)]],
    "rope_release": [[(0, "block.tripwire.detach", 0.8, 1.0), (0, "entity.arrow.shoot", 0.4, 1.3)],
                     [(0, "item.lead.untied", 0.7, 1.4), (0, "entity.breeze.jump", 0.4, 1.4)]],
    "wings_open": [[(0, "entity.ender_dragon.flap", 0.9, 1.1), (1, "item.armor.equip_elytra", 0.6, 0.7)],
                   [(0, "item.armor.equip_elytra", 0.9, 1.0), (1, "entity.bat.takeoff", 0.6, 0.7), (3, "entity.phantom.flap", 0.6, 0.8)]],
    "flap": [[(0, "entity.phantom.flap", 0.9, 0.8), (0, "block.wool.hit", 0.3, 0.6)],
             [(0, "entity.ender_dragon.flap", 0.8, 1.6), (0, "entity.parrot.fly", 0.4, 0.7)]],
    "boom": [[(0, "entity.lightning_bolt.thunder", 0.9, 1.5), (0, "entity.wind_charge.wind_burst", 0.7, 0.6)],
             [(0, "item.mace.smash_air", 0.9, 0.7), (0, "entity.warden.sonic_boom", 0.6, 1.3), (2, "entity.lightning_bolt.thunder", 0.5, 1.0)]],
    "wind": [[(0, "item.elytra.flying", 1.0, 0.8)], [(0, "item.elytra.flying", 1.0, 1.25)]],
}

# What the subtitles say for the new moves' sounds (every layer of a score carries its score's line; the game shows one).
SUBTITLES = {"sling": "A line slings someone on", "spring": "Someone springs off a ledge", "flare": "Wings cup the air", "skim": "Wings skim the ground",
             "takeoff": "Someone takes off", "strike": "A rope strike lands", "beat": "Wings beat", "hum": "A taut line sings"}
RANGE = {"boom": 96, "wings_open": 32, "flap": 28, "takeoff": 32, "strike": 24, "wind": 16, "hum": 16}


def classic():
    out = {}
    for path in CLASSIC:
        with open(path) as fh:
            out.update(json.load(fh))
    return out


def entries():
    old = classic()
    doc = {}
    for name, layers in {**SCORES, **MOVES}.items():
        was = old.get("ascent." + name, {})
        dist = RANGE.get(name)
        if dist is None:
            dist = (was.get("sounds") or [{}])[0].get("attenuation_distance", 20) if was else 20
        for i, (_, event, volume, pitch) in enumerate(layers):
            doc[f"ascent.score.{name}.{i}"] = {
                "subtitle": was.get("subtitle") or "subtitles.awaken.ascent." + name,
                "sounds": [{"name": "minecraft:" + event, "type": "event", "volume": volume, "pitch": pitch, "attenuation_distance": dist}]}
    return doc


def table():
    names = list(SCORES) + list(MOVES)
    rows = ["package dev.awaken.body.ascent;", "",
            "/** GENERATED by tools/art/specs/ascent_scores.py (edit the scores there): each score's name and its layers' delays in ticks. */",
            "public final class AscentScoreTable {", "    private AscentScoreTable() {}", "",
            "    public static final String[] NAMES = {" + ", ".join(f'"{n}"' for n in names) + "};",
            "    public static final int[][] DELAYS = {" + ", ".join("{" + ", ".join(str(l[0]) for l in ({**SCORES, **MOVES})[n]) + "}" for n in names) + "};",
            "}", ""]
    return "\n".join(rows)


def build():
    os.makedirs(os.path.dirname(MERGE), exist_ok=True)
    with open(MERGE, "w") as fh:
        json.dump(entries(), fh, indent=2)
        fh.write("\n")
    with open(TABLE, "w") as fh:
        fh.write(table())
    os.makedirs(os.path.dirname(LANG), exist_ok=True)
    with open(LANG, "w") as fh:
        json.dump({"subtitles.awaken.ascent." + k: v for k, v in SUBTITLES.items()}, fh, indent=2)
        fh.write("\n")
    print(f"{len(SCORES)} scores, {len(MOVES)} moves, {sum(len(v) for v in {**SCORES, **MOVES}.values())} layers")


# ---- the listening page (local only) ---------------------------------------------------------------------------------

def game_sounds():
    """The game's own sound files on this machine: {event: [paths]}, from the NeoForm assets cache."""
    home = os.environ.get("GRADLE_USER_HOME", "/Volumes/MEDIA/.gradle-home")
    base = os.path.join(home, "caches/neoformruntime/assets")
    idx_dir = os.path.join(base, "indexes")
    idx = sorted(os.listdir(idx_dir), key=lambda n: os.path.getmtime(os.path.join(idx_dir, n)))[-1]
    with open(os.path.join(idx_dir, idx)) as fh:
        objects = json.load(fh)["objects"]

    def path(key):
        h = objects[key]["hash"]
        return os.path.join(base, "objects", h[:2], h)

    with open(path("minecraft/sounds.json")) as fh:
        events = json.load(fh)

    def files(event, depth=0):
        out = []
        for s in events.get(event, {}).get("sounds", []):
            name = s if isinstance(s, str) else s["name"]
            if not isinstance(s, str) and s.get("type") == "event":
                if depth < 3:
                    out += files(name.replace("minecraft:", ""), depth + 1)
                continue
            key = "minecraft/sounds/" + name.replace("minecraft:", "") + ".ogg"
            if key in objects:
                out.append(path(key))
        return out

    return files


def listen(out_dir):
    files = game_sounds()
    os.makedirs(os.path.join(out_dir, "rec"), exist_ok=True)
    old = classic()
    rows = []
    for name, layers in {**SCORES, **MOVES}.items():
        new = []
        for delay, event, volume, pitch in layers:
            fs = files(event)
            if not fs:
                print("no recording on this machine for", event)
                continue
            dst = os.path.join(out_dir, "rec", event + ".ogg")
            if not os.path.exists(dst):
                shutil.copyfile(fs[0], dst)
            new.append({"t": delay / 20.0, "src": "rec/" + event + ".ogg", "v": volume, "p": pitch, "event": event})
        alts = []
        for take in ALTERNATES.get(name, []):
            row = []
            for delay, event, volume, pitch in take:
                fs = files(event)
                if not fs:
                    continue
                dst = os.path.join(out_dir, "rec", event + ".ogg")
                if not os.path.exists(dst):
                    shutil.copyfile(fs[0], dst)
                row.append({"t": delay / 20.0, "src": "rec/" + event + ".ogg", "v": volume, "p": pitch, "event": event})
            alts.append(row)
        was = None
        if "ascent." + name in old:
            src = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds", old["ascent." + name]["sounds"][0]["name"].split(":")[1] + ".ogg")
            if os.path.exists(src):
                dst = os.path.join(out_dir, "rec", "classic_" + name + ".ogg")
                shutil.copyfile(src, dst)
                was = "rec/classic_" + name + ".ogg"
        rows.append({"name": name, "new": new, "classic": was, "alts": alts})
    page = """<!doctype html><meta charset="utf-8"><title>Movement sounds</title>
<style>body{font:15px/1.5 system-ui;margin:24px;max-width:860px;background:#14181d;color:#e8edf2}
h1{font-size:20px}table{border-collapse:collapse;width:100%}td{padding:8px 10px;border-bottom:1px solid #2a323c;vertical-align:top}
button{font:inherit;padding:6px 14px;border-radius:6px;border:1px solid #46586b;background:#1f2933;color:inherit;cursor:pointer}
button:hover{background:#2b3947}small{color:#93a3b3}</style>
<h1>Movement sounds: as they were, and as scores of the game's own recordings</h1>
<p><small>Local page. The recordings were copied from this machine's own game files and are not in the repo.</small></p>
<table id="t"></table>
<script>
const ROWS = __ROWS__;
// plain audio elements (no fetch), so the page works opened straight from the disk
function play(layers){ for (const l of layers){ setTimeout(() => { const a = new Audio(l.src); a.preservesPitch = false; a.mozPreservesPitch = false; a.webkitPreservesPitch = false;
    a.playbackRate = l.p; a.volume = Math.min(1, l.v); a.play(); }, l.t * 1000); } }
const t = document.getElementById('t');
for (const r of ROWS){ const tr = t.insertRow();
  tr.insertCell().textContent = r.name;
  const a = tr.insertCell(); if (r.classic){ const b = document.createElement('button'); b.textContent = 'As it was'; b.onclick = () => play([{t:0,src:r.classic,v:1,p:1}]); a.append(b); }
  const n = tr.insertCell(); const b = document.createElement('button'); b.textContent = 'New'; b.onclick = () => play(r.new); n.append(b);
  r.alts.forEach((alt, i) => { const c = document.createElement('button'); c.textContent = 'Other ' + (i + 1); c.style.marginLeft = '6px'; c.title = alt.map(l => l.event).join(' + ');
    c.onclick = () => play(alt); n.append(c); });
  const d = tr.insertCell(); d.innerHTML = '<small>' + r.new.map(l => l.event + ' ×' + l.v + ' @' + l.p + (l.t ? ' +' + Math.round(l.t*20) + 't' : '')).join('<br>') + '</small>';
}
</script>
""".replace("__ROWS__", json.dumps(rows))
    with open(os.path.join(out_dir, "index.html"), "w") as fh:
        fh.write(page)
    print("listen: open", os.path.join(out_dir, "index.html"))


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd == "build":
        build()
        return 0
    if cmd == "listen":
        listen(argv[1] if len(argv) > 1 else os.path.join(ROOT, "run-autopilot/listen/movement"))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
