#!/usr/bin/env python3
"""The sound audit (SPEC §66; Euan, 11 Nov 2026: "double check basically all the custom sounds we have and do a full
audit and make sure they're all on par with the best sounds we have").

The tables (tools/art/specs/audit_*.py, built with specs/audit_kit.py) give every sound that fell short up to three
picks, each a recipe of recordings the game already has, named the way sounds.json names them. Nothing of Mojang's is
copied: the game plays its own files. The first pick is the best guess and what the game plays until Euan has listened;
his marks choose another (specs/sound_audit_picks.json, written by `apply`).

    python3 tools/art/sound_audit.py build          writes the rows into each module's sounds.json and sound_layers/
    python3 tools/art/sound_audit.py check          fails if a recording a pick names is not in this game, or the
                                                    game's files have drifted from the tables
    python3 tools/art/sound_audit.py audit          writes docs/sound-audit/AUDIT.md: one row per sound event of ours
    python3 tools/art/sound_audit.py listen [port]  builds the listening page and serves it on 127.0.0.1 (8768)
    python3 tools/art/sound_audit.py apply          takes his marks from the page into sound_audit_picks.json
    python3 tools/art/sound_audit.py prune [go]     lists (go: deletes) our .ogg files nothing plays any more
    python3 tools/art/sound_audit.py snapshot       records the rows' events as they were before the audit (once)

A recipe's first recording is the event itself in sounds.json. Its other recordings are layered by the client
(assets/awaken/sound_layers/<module>.json, core.audio.client.SoundLayers), because sounds.json plays one recording an
event. Run `build` after any audio generator that rewrites a module's sounds.json: it puts the audit's rows back.
The page plays the recordings from this machine's own copy of the game (and of AmbientSounds, when the play copy has
it), lives in the gitignored run-autopilot/ and is never hosted.
"""
import glob
import http.server
import json
import os
import re
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
sys.path.insert(0, os.path.join(HERE, "specs"))
import audit_kit  # noqa: E402

SPECS = ["audit_birds", "audit_mining", "audit_world", "audit_fauna", "audit_rest"]
OUT = os.path.join(ROOT, "run-autopilot/sound-audit-listen")
ASSETS = os.environ.get("MC_ASSETS", os.path.join(os.environ.get("GRADLE_USER_HOME", "/Volumes/MEDIA/.gradle-home"),
                                                  "caches/neoformruntime/assets"))
AS_JAR = os.environ.get("AS_JAR", "")  # AmbientSounds' jar, for the page's real crows, gulls and beds
OURS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
LAYERS_DIR = os.path.join(ROOT, "src/main/resources/assets/awaken/sound_layers")
BEFORE = os.path.join(HERE, "specs/sound_audit_before.json")   # {"base": commit, "events": {id: entry}}: as they were
PICKS = os.path.join(HERE, "specs/sound_audit_picks.json")     # row id -> 1, 2, 3 or "before": what the game plays
AUDIT_MD = os.path.join(ROOT, "docs/sound-audit/AUDIT.md")


def merge_json(module):
    return os.path.join(ROOT, "src/main/merge", module, "assets/awaken/sounds.json")


def load_rows():
    rows = []
    for name in SPECS:
        if os.path.exists(os.path.join(HERE, "specs", name + ".py")):
            rows += __import__(name).ROWS
    seen = set()
    for r in rows:
        assert r["id"] not in seen, "two rows for " + r["id"]
        seen.add(r["id"])
    return rows


ROWS = load_rows()
BY_ID = {r["id"]: r for r in ROWS}


def chosen():
    picks = json.load(open(PICKS)) if os.path.exists(PICKS) else {}
    return {r["id"]: ("before" if r["keep"] else picks.get(r["id"], 1)) for r in ROWS}


def before():
    return json.load(open(BEFORE)) if os.path.exists(BEFORE) else {"base": "", "events": {}}


# ---- a row as the game's files ---------------------------------------------------------------------------------------

def _extras(l):
    out = {}
    if l["spread"]:
        out["spread"] = l["spread"]
    if l["jitter"]:
        out["jitter"] = l["jitter"]
    if l["chance"] != 1.0:
        out["chance"] = l["chance"]
    return out


def compile_row(r, layers):
    """(the event's sounds.json entry without its subtitle, helper events {id: entry}, client layers [..])."""
    assert layers, r["id"] + ": an empty recipe can't be written (mark the pick code=True)"
    first = layers[0]
    assert first["at"] == 0 and not first["spread"] and first["chance"] == 1.0, r["id"] + ": a recipe's first recording starts the sound"
    assert not first["e"].startswith("ambientsounds:") if "e" in first else True, r["id"] + ": AmbientSounds' recordings only play from code"
    if "f" in first:
        sounds = []
        for f in first["f"]:
            s = {"name": "minecraft:" + f, "volume": first["vol"], "pitch": first["pitch"]}
            if first["dist"]:
                s["attenuation_distance"] = first["dist"]
            sounds.append(s)
    else:
        sounds = [{"name": first["e"], "type": "event", "volume": first["vol"], "pitch": first["pitch"]}]
    helpers, parts, extra = {}, {}, []
    for l in layers[1:]:
        if "f" in l:
            key = (tuple(l["f"]), l["dist"])
            if key not in parts:
                parts[key] = f"{r['id']}.part{len(parts) + 1}"
                helpers[parts[key]] = {"sounds": [dict({"name": "minecraft:" + f}, **({"attenuation_distance": l["dist"]} if l["dist"] else {})) for f in l["f"]]}
            sound = "awaken:" + parts[key]
        else:
            assert not l["e"].startswith("ambientsounds:"), r["id"] + ": AmbientSounds' recordings only play from code"
            sound = l["e"]
        extra.append(dict({"sound": sound, "volume": l["vol"], "pitch": l["pitch"], "delay": l["at"]}, **_extras(l)))
    return {"sounds": sounds}, helpers, extra


def plan():
    """What build writes: {module: (sounds.json as it should be, layers file)}."""
    was = before()["events"]
    pick = chosen()
    out = {}
    for module in sorted({r["module"] for r in ROWS}):
        path = merge_json(module)
        sounds = json.load(open(path))
        layers_file = {}
        for r in (r for r in ROWS if r["module"] == module):
            stale = [k for k in sounds if re.fullmatch(re.escape(r["id"]) + r"\.part\d+", k)]
            for k in stale:
                del sounds[k]
            p = pick[r["id"]]
            if p == "before":
                if r["id"] in was:
                    sounds[r["id"]] = was[r["id"]]
                continue
            recipe = r["picks"][int(p) - 1]
            if recipe["code"]:
                # played from code: the event keeps the first written recipe (what plays without the code's source);
                # a row with none has no event any more (the code plays another mod's recording, or nothing)
                recipe = next((k for k in r["picks"] if not k["code"]), None)
                if recipe is None:
                    sounds.pop(r["id"], None)
                    continue
            entry, helpers, extra = compile_row(r, recipe["layers"])
            old = sounds.get(r["id"]) or was.get(r["id"]) or {}
            if old.get("subtitle"):
                entry = {"subtitle": old["subtitle"], **entry}
            sounds[r["id"]] = entry
            sounds.update(helpers)
            if extra:
                layers_file["awaken:" + r["id"]] = extra
        out[module] = (sounds, layers_file)
    return out


def build():
    n = layered = 0
    for module, (sounds, layers_file) in plan().items():
        with open(merge_json(module), "w") as f:
            json.dump(sounds, f, indent=2)
            f.write("\n")
        path = os.path.join(LAYERS_DIR, module + ".json")
        if layers_file:
            os.makedirs(LAYERS_DIR, exist_ok=True)
            with open(path, "w") as f:
                json.dump(layers_file, f, indent=1, sort_keys=True)
                f.write("\n")
        elif os.path.exists(path):
            os.remove(path)
        n += sum(1 for r in ROWS if r["module"] == module)
        layered += len(layers_file)
    kept = [k for k, p in chosen().items() if p == "before"]
    print(f"{n} sounds written ({layered} layered by the client; {len(kept)} kept as built)")


# ---- the recordings on this machine ----------------------------------------------------------------------------------

def vanilla():
    idx = max(glob.glob(ASSETS + "/indexes/*.json"), key=lambda p: int(os.path.basename(p)[:-5]) if os.path.basename(p)[:-5].isdigit() else -1)
    objects = json.load(open(idx))["objects"]
    h = objects["minecraft/sounds.json"]["hash"]
    return objects, json.load(open(f"{ASSETS}/objects/{h[:2]}/{h}"))


def as_jar():
    if AS_JAR and os.path.exists(AS_JAR):
        return AS_JAR
    for pat in ("/Volumes/MEDIA/Awaken-play/run-pack/mods/AmbientSounds*.jar", os.path.join(ROOT, "run-pack/mods/AmbientSounds*.jar")):
        hits = sorted(glob.glob(pat))
        if hits:
            return hits[-1]
    return ""


def as_sounds(jar):
    if not jar:
        return {}
    with zipfile.ZipFile(jar) as z:
        return json.loads(z.read("assets/ambientsounds/sounds.json"))


def all_layers():
    for r in ROWS:
        for p in r["picks"]:
            for l in p["layers"]:
                yield r, l


def check():
    objects, vsj = vanilla()
    jar = as_jar()
    asj = as_sounds(jar)
    bad, warn = [], []
    for r, l in all_layers():
        if "f" in l:
            for f in l["f"]:
                if f"minecraft/sounds/{f}.ogg" not in objects:
                    bad.append(f"{r['id']}: no file {f}")
        elif l["e"].startswith("minecraft:"):
            if l["e"][10:] not in vsj:
                bad.append(f"{r['id']}: no event {l['e']}")
        elif l["e"].startswith("ambientsounds:"):
            if jar and l["e"][14:] not in asj:
                bad.append(f"{r['id']}: no AmbientSounds event {l['e']}")
        if not 0.5 <= l["pitch"] <= 2.0:
            warn.append(f"{r['id']}: pitch {l['pitch']} leaves 0.5..2.0 (the game clamps it)")
    for module, (sounds, layers_file) in plan().items():
        if json.load(open(merge_json(module))) != sounds:
            bad.append(f"{module}: sounds.json is not what the tables say (run build)")
        path = os.path.join(LAYERS_DIR, module + ".json")
        have = json.load(open(path)) if os.path.exists(path) else {}
        if have != layers_file:
            bad.append(f"{module}: sound_layers/{module}.json is not what the tables say (run build)")
    for w in warn:
        print("note:", w)
    if bad:
        raise SystemExit("the audit's tables and the game disagree:\n  " + "\n  ".join(bad))
    print(f"{len(ROWS)} rows, every recording here" + ("" if jar else " (AmbientSounds' jar not found: its names unchecked)"))


# ---- before the audit ------------------------------------------------------------------------------------------------

def git(*args, binary=False):
    out = subprocess.run(["git", "-C", ROOT, *args], capture_output=True, check=True).stdout
    return out if binary else out.decode()


def snapshot():
    """Records each row's event as it is at the merge base with the build branch: what "Before" plays on the page."""
    had = before()
    base = had["base"] or git("merge-base", "HEAD", "origin/build/awaken-v1").strip()
    events = dict(had["events"])
    cache = {}
    for r in ROWS:
        if r["id"] in events:
            continue
        m = r["module"]
        if m not in cache:
            try:
                cache[m] = json.loads(git("show", f"{base}:src/main/merge/{m}/assets/awaken/sounds.json"))
            except subprocess.CalledProcessError:
                cache[m] = {}
        if r["id"] in cache[m]:
            events[r["id"]] = cache[m][r["id"]]
    with open(BEFORE, "w") as f:
        json.dump({"base": base, "events": events}, f, indent=1, sort_keys=True)
        f.write("\n")
    print(f"{len(events)} events as they were at {base[:9]}")


def old_file(name, base):
    """A path to one of our .ogg files as it was: the working tree's, else taken out of git into the page's folder."""
    path = os.path.join(OURS, name + ".ogg")
    if os.path.exists(path):
        return path
    cached = os.path.join(OUT, "before", name + ".ogg")
    if not os.path.exists(cached):
        os.makedirs(os.path.dirname(cached), exist_ok=True)
        try:
            data = git("show", f"{base}:src/main/resources/assets/awaken/sounds/{name}.ogg", binary=True)
        except subprocess.CalledProcessError:
            return ""
        with open(cached, "wb") as f:
            f.write(data)
    return cached


# ---- the audit document ----------------------------------------------------------------------------------------------

def inventory():
    """Every sound event of ours: {id: (module, entry)} in merge order."""
    out = {}
    for path in sorted(glob.glob(os.path.join(ROOT, "src/main/merge/*/assets/awaken/sounds.json"))):
        module = path.split(os.sep)[-4]
        for k, v in json.load(open(path)).items():
            out[k] = (module, v)
    return out


def subtitles():
    out = {}
    for path in glob.glob(os.path.join(ROOT, "src/main/merge/*/assets/awaken/lang/en_us.json")):
        try:
            for k, v in json.load(open(path)).items():
                if k.startswith("subtitles."):
                    out[k] = v
        except ValueError:
            pass
    return out


def code_refs(ids):
    """Where each event id is written out in the Java sources: {id: [short paths]}."""
    want = {i: [] for i in ids}
    rx = re.compile(r'"([a-z0-9_]+(?:\.[a-z0-9_]+)+)"')
    base = os.path.join(ROOT, "src/main/java/dev/awaken")
    for d, _, files in os.walk(base):
        for name in files:
            if not name.endswith(".java") or name.endswith("Tests.java"):
                continue
            p = os.path.join(d, name)
            short = os.path.relpath(p, base)[:-5]
            for m in set(rx.findall(open(p, encoding="utf-8").read())):
                if m in want and len(want[m]) < 3:
                    want[m].append(short)
    return want


def names_of(entry):
    return [(s if isinstance(s, str) else s["name"]) for s in entry.get("sounds", [])]


def recipe_words(layers):
    out = []
    for l in layers:
        what = l["e"].split(":", 1)[1] if "e" in l else (l["f"][0] + (" .." if len(l["f"]) > 1 else ""))
        ns = "AmbientSounds " if "e" in l and l["e"].startswith("ambientsounds:") else ""
        out.append(f"{ns}`{what}` ×{l['vol']:g} @{l['pitch']:g}" + (f" +{l['at']}ms" if l["at"] else ""))
    return "; ".join(out)


def audit():
    import audit_notes  # the verdicts for every event the tables do not replace
    inv = inventory()
    subs = subtitles()
    refs = code_refs(inv.keys())
    pick = chosen()
    was = before()["events"]
    groups, counts = {}, {"bar": 0, "fixed": 0, "left": 0}
    order = []
    for k, (module, entry) in inv.items():
        if re.search(r"\.part\d+$", k) and k.rsplit(".part", 1)[0] in BY_ID:
            continue  # a recipe's own helper
        r = BY_ID.get(k)
        where_auto = ", ".join(f"`{p}`" for p in refs.get(k, []))
        what = subs.get(entry.get("subtitle", ""), "")
        if r and not r["keep"]:
            p = pick[k]
            recipe = r["picks"][0] if p == "before" else r["picks"][int(p) - 1]
            written = next((q for q in r["picks"] if not q["code"]), recipe) if recipe["code"] else recipe
            fix = recipe["say"] + ". " + recipe_words(written["layers"]) + (". " + r["note"] if r["note"] else "")
            row = (k, r["name"], r["where"], r["was"], "Below the bar. " + r["why"], fix)
            section, verdict = r["group"], "fixed"
        else:
            n = audit_notes.note(k, module, entry)
            ours = any(x.startswith("awaken:") for x in names_of(entry))
            src = n.get("was") or ("ours: " + audit_notes.generator(k, module, entry) if ours else "Minecraft's own, by name")
            where = n.get("where") or where_auto or "`" + module + "`"
            row = (k, n.get("what") or what or k, where, src, n["verdict"], n.get("fix", "None needed" if n["state"] == "bar" else ""))
            section, verdict = n.get("group") or ("m:" + module), n["state"]
        counts[verdict] += 1
        if section not in groups:
            groups[section] = []
            order.append(section)
        groups[section].append(row)
    for r in ROWS:
        if r["id"] in inv:
            continue  # an event the audit took out of the game: its row still says why
        fix = r["picks"][0]["say"] + ". The event is gone from sounds.json" + (". " + r["note"] if r["note"] else "")
        if r["group"] not in groups:
            groups[r["group"]] = []
            order.append(r["group"])
        groups[r["group"]].append((r["id"], r["name"], r["where"], r["was"], "Below the bar. " + r["why"], fix))
        counts["fixed"] += 1
    titles = {g[0]: (g[1], g[2]) for g in audit_kit.GROUPS}
    order.sort(key=lambda s: ([g[0] for g in audit_kit.GROUPS].index(s) if s in titles else len(titles), s))
    lines = [open(os.path.join(HERE, "specs/audit_head.md")).read().rstrip().format(
        total=sum(counts.values()), bar=counts["bar"], fixed=counts["fixed"], left=counts["left"]), ""]
    for s in order:
        title, lead = titles.get(s, (audit_notes.MODULE_TITLES.get(s[2:], s[2:]) if s.startswith("m:") else s, ""))
        lines += ["", f"## {title}", ""]
        if lead:
            lines += [lead, ""]
        lines += ["| Event | What it is | Where and when it plays | Source before | Verdict | Fix, as built |", "|---|---|---|---|---|---|"]
        for k, what, where, src, verdict, fix in groups[s]:
            lines.append("| `" + k + "` | " + " | ".join(str(x).replace("|", "/").replace("\n", " ") for x in (what, where, src, verdict, fix)) + " |")
    os.makedirs(os.path.dirname(AUDIT_MD), exist_ok=True)
    with open(AUDIT_MD, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{sum(counts.values())} events: {counts['bar']} at the bar, {counts['fixed']} fixed, {counts['left']} left as they are -> {AUDIT_MD}")


# ---- our files nothing plays any more --------------------------------------------------------------------------------

def unplayed():
    """Our .ogg files that only the audit's replaced events played: named by a row's event before, by nothing now.
    (Other leftovers are not this tool's to judge: armour's old files still play on its own listening page.)"""
    used = set()
    for path in glob.glob(os.path.join(ROOT, "src/main/merge/*/assets/awaken/sounds.json")):
        for v in json.load(open(path)).values():
            for n in names_of(v):
                if n.startswith("awaken:"):
                    used.add(n[7:])
    was = set()
    for r in ROWS:
        for n in names_of(before()["events"].get(r["id"], {})):
            if n.startswith("awaken:"):
                was.add(n[7:])
    # a stem or bed can be named outside sounds.json: any json or java that spells the whole path keeps the file
    blob = []
    for base, ext in ((os.path.join(ROOT, "src/main/resources"), ".json"), (os.path.join(ROOT, "src/main/java"), ".java"),
                      (os.path.join(ROOT, "src/main/merge"), ".json")):
        for d, _, files in os.walk(base):
            for name in files:
                if name.endswith(ext) and name != "sounds.json":
                    try:
                        blob.append(open(os.path.join(d, name), encoding="utf-8").read())
                    except (UnicodeDecodeError, OSError):
                        pass
    blob = "\n".join(blob)
    def named(f):  # as a sound: "awaken:<path>", "sounds/<path>", or a path with a folder (a bare word may be a packet's id)
        return "awaken:" + f in blob or "sounds/" + f in blob or ("/" in f and f in blob)

    return sorted(f for f in was - used if os.path.exists(os.path.join(OURS, f + ".ogg")) and not named(f))


def prune(go):
    files = unplayed()
    size = sum(os.path.getsize(os.path.join(OURS, f + ".ogg")) for f in files)
    for f in files:
        print(("removed " if go else "unplayed ") + f)
        if go:
            os.remove(os.path.join(OURS, f + ".ogg"))
    print(f"{len(files)} files, {size / 1e6:.1f} MB" + ("" if go else " (prune go deletes them)"))


# ---- the listening page ----------------------------------------------------------------------------------------------

def V(event, vol=1.0, pitch=1.0):
    """One of Minecraft's events as a scene's own step (what the game plays round our sound)."""
    return {"e": "minecraft:" + event, "vol": vol, "pitch": pitch, "at": 0, "spread": 0, "chance": 1, "jitter": 0}


SCENES = {
    "alone": dict(says="the sound by itself", steps=[(0, "X")]),
    "twice": dict(says="twice, a breath apart", steps=[(0, "X"), (1600, "X")]),
    "flush": dict(says="a wood's birdsong, then the bird bursts off, and another a moment later",
                  steps=[(0, ("bed", "ambientsounds:animals.bird-ambience3", 0.3)), (1000, "X"), (2600, "X")], len=5000),
    "bigwings": dict(says="waves on a beach, then the big bird takes off", steps=[(0, ("bed", "ambientsounds:water.beach", 0.5)), (1000, "X")], len=4500),
    "flock": dict(says="the flock goes up, twice", steps=[(0, "X"), (2600, "X")]),
    "song": dict(says="a wood's birdsong under it, the bird sings, then again",
                 steps=[(0, ("bed", "ambientsounds:animals.bird-ambience3", 0.25)), (800, "X"), (3800, "X")], len=7000),
    "caw": dict(says="wind in the leaves, the call, then again", steps=[(0, ("bed", "ambientsounds:wind.wind-in-leaves", 0.3)), (800, "X"), (4200, "X")], len=7500),
    "beach": dict(says="waves on a beach, a gull's cry, a small bird flushed, the cry again",
                  steps=[(0, ("bed", "ambientsounds:water.beach", 0.5)), (800, "X"), (3200, ("row", "wild.flush")), (5200, "X")], len=9000),
    "far": dict(says="from far off, twice", steps=[(0, "X"), (3500, "X")]),
    "night": dict(says="three voices in the grass, starting one after another", steps=[(0, "X"), (2200, "X"), (4700, "X")]),
    "steps": dict(says="six in a row at a walk", steps=[(k * 420, "X") for k in range(6)]),
    # the game's own digging under ours: a stone hit every fourth tick at its own level and pitch, then the block breaks
    "dig": dict(says="six bites into ore with Minecraft's own digging sound under them, then the block breaks",
                steps=[s for k in range(6) for s in ((k * 200, V("block.stone.hit", 0.25, 0.5)), (k * 200, ("X", 0.55)))]
                + [(1200, V("block.stone.break", 1.0, 0.8)), (1200, ("X", 0.85))]),
    "loop": dict(says="looped for ten seconds", steps=[(0, ("loop", "X"))], len=10000),
}


def manifest():
    """Every event the page plays: its recordings as urls, each with its own volume and pitch."""
    objects, vsj = vanilla()
    jar = as_jar()
    asj = as_sounds(jar)
    was = before()
    events, files, missing = {}, {}, []

    def recordings(ns, name, depth=0):
        entry = vsj.get(name) if ns == "minecraft" else asj.get(name) if ns == "ambientsounds" else was["events"].get(name)
        if entry is None:
            return None
        out = []
        for s in entry["sounds"]:
            s = {"name": s} if isinstance(s, str) else s
            n = s["name"]
            sns, sn = n.split(":", 1) if ":" in n else ("minecraft", n)
            vol, pitch = s.get("volume", 1.0), s.get("pitch", 1.0)
            if s.get("type") == "event":
                for t in (recordings(sns, sn, depth + 1) or []) if depth < 4 else []:
                    out.append({"url": t["url"], "vol": t["vol"] * vol, "pitch": t["pitch"] * pitch})
                continue
            url = add_file(sns, sn)
            if url:
                out.append({"url": url, "vol": vol, "pitch": pitch})
        return out

    def add_file(ns, name):
        if ns == "minecraft":
            o = objects.get(f"minecraft/sounds/{name}.ogg")
            if not o:
                return None
            files["/v/" + name + ".ogg"] = f"{ASSETS}/objects/{o['hash'][:2]}/{o['hash']}"
            return "/v/" + name + ".ogg"
        if ns == "ambientsounds":
            files["/as/" + name + ".ogg"] = ("zip", jar, f"assets/ambientsounds/sounds/{name}.ogg")
            return "/as/" + name + ".ogg"
        path = old_file(name, was["base"])
        if not path:
            return None
        files["/a/" + name + ".ogg"] = path
        return "/a/" + name + ".ogg"

    used = set()
    for r, l in all_layers():
        if "e" in l:
            used.add(l["e"])
        else:
            for f in l["f"]:
                url = add_file("minecraft", f)
                if not url:
                    missing.append(f"{r['id']} -> {f}")
    for r in ROWS:
        if r["id"] in was["events"]:
            used.add("awaken:" + r["id"])
    for sc in SCENES.values():
        for _, what in sc["steps"]:
            if isinstance(what, tuple) and what[0] == "bed":
                used.add(what[1])
            elif isinstance(what, dict):
                used.add(what["e"])
    for e in sorted(used):
        ns, name = e.split(":", 1)
        if ns == "ambientsounds" and not jar:
            continue  # the page says so and leaves these out
        rec = recordings(ns, name)
        if not rec:
            missing.append(e)
            continue
        events[e] = rec
    if missing:
        raise SystemExit("recordings not found:\n  " + "\n  ".join(missing))
    return events, files, bool(jar)


def page():
    events, files, has_as = manifest()
    was = before()["events"]
    pick = chosen()

    def layers_js(layers):
        out = []
        for l in layers:
            if "f" in l:
                out.append({"urls": ["/v/" + f + ".ogg" for f in l["f"]], "vol": l["vol"], "pitch": l["pitch"], "at": l["at"],
                            "spread": l["spread"], "chance": l["chance"], "jitter": l["jitter"]})
            else:
                out.append({k: l[k] for k in ("e", "vol", "pitch", "at", "spread", "chance", "jitter")})
        return out

    def old(r):
        return [{"e": "awaken:" + r["id"], "vol": 1.0, "pitch": 1.0, "at": 0, "spread": 0, "chance": 1, "jitter": 0}] if "awaken:" + r["id"] in events else None

    def now(r):
        p = pick[r["id"]]
        return old(r) if p == "before" else layers_js(r["picks"][int(p) - 1]["layers"])

    def step(what):
        if isinstance(what, tuple):
            if what[0] == "bed":
                return {"bed": what[1], "gain": what[2]}
            if what[0] == "loop":
                return {"loop": True}
            if what[0] == "X":
                return {"x": what[1]}
            return {"row": what[1], "gain": what[2] if len(what) > 2 else 1}
        return what

    data = {"events": events, "hasAS": has_as,
            "groups": [{"key": g[0], "title": g[1], "lead": g[2]} for g in audit_kit.GROUPS],
            "scenes": {k: {"says": s["says"], "len": s.get("len", 0), "steps": [[ms, step(w)] for ms, w in s["steps"]]} for k, s in SCENES.items()},
            "now": {r["id"]: now(r) for r in ROWS},
            "rows": [{"id": r["id"], "name": r["name"], "where": re.sub(r"`", "", r["where"]), "why": r["why"], "scene": r["scene"], "group": r["group"],
                      "before": old(r), "inGame": "before" if pick[r["id"]] == "before" else int(pick[r["id"]]),
                      "picks": [{"say": p["say"], "code": p["code"], "layers": layers_js(p["layers"])} for p in r["picks"]]} for r in ROWS]}
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "index.html"), "w") as f:
        f.write(open(os.path.join(HERE, "specs/audit_page.html")).read().replace("/*DATA*/", json.dumps(data)))
    print(f"{len(ROWS)} rows, {len(events)} events, {len(files)} recordings -> {os.path.join(OUT, 'index.html')}" + ("" if has_as else " (no AmbientSounds jar: its recordings are left out)"))
    return files


def serve(port):
    files = page()
    picks = os.path.join(OUT, "picks.json")

    class Handler(http.server.BaseHTTPRequestHandler):
        def send(self, code, kind, body):
            self.send_response(code)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            path = self.path.split("?", 1)[0]
            if path in ("/", "/index.html"):
                self.send(200, "text/html; charset=utf-8", open(os.path.join(OUT, "index.html"), "rb").read())
            elif path == "/picks":
                self.send(200, "application/json", open(picks, "rb").read() if os.path.exists(picks) else b"{}")
            elif path in files:  # only the recordings the page names
                src = files[path]
                if isinstance(src, tuple):
                    with zipfile.ZipFile(src[1]) as z:
                        self.send(200, "audio/ogg", z.read(src[2]))
                else:
                    self.send(200, "audio/ogg", open(src, "rb").read())
            else:
                self.send(404, "text/plain", b"not here")

        def do_POST(self):
            if self.path != "/picks":
                self.send(404, "text/plain", b"not here")
                return
            body = self.rfile.read(min(int(self.headers.get("Content-Length", "0")), 2_000_000))
            try:
                marks = json.loads(body)
            except ValueError:
                self.send(400, "text/plain", b"not json")
                return
            with open(picks, "w") as f:
                json.dump(marks, f, indent=1)
            self.send(200, "application/json", b"{}")

        def log_message(self, fmt, *args):
            pass

    print(f"listening page on http://127.0.0.1:{port} (marks -> {picks})", flush=True)
    http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()


def apply():
    """His marks into the committed picks file: "pick n" -> n, "before" -> "before"; the rest are listed."""
    marks = json.load(open(os.path.join(OUT, "picks.json")))["rows"]
    picks = json.load(open(PICKS)) if os.path.exists(PICKS) else {}
    left = []
    for r in ROWS:
        m = (marks.get(r["id"]) or {}).get("pick", "")
        note = (marks.get(r["id"]) or {}).get("note", "")
        if m.startswith("pick "):
            n = int(m[5:])
            if r["picks"][n - 1]["code"] and n != 1:
                left.append(f"{r['id']}: pick {n} is played from code, a session has to switch it" + (f" ({note})" if note else ""))
            else:
                picks[r["id"]] = n
        elif m == "before":
            picks[r["id"]] = "before"
        elif m == "none":
            left.append(f"{r['id']}: none of these" + (f" ({note})" if note else ""))
        elif note:
            left.append(f"{r['id']}: not marked ({note})")
    with open(PICKS, "w") as f:
        json.dump(picks, f, indent=1, sort_keys=True)
        f.write("\n")
    print(f"{len(picks)} rows chosen; run build." + ("\nStill open:\n  " + "\n  ".join(left) if left else ""))


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "build":
        build()
    elif cmd == "check":
        check()
    elif cmd == "audit":
        audit()
    elif cmd == "listen":
        serve(int(sys.argv[2]) if len(sys.argv) > 2 else 8768)
    elif cmd == "page":
        page()
    elif cmd == "apply":
        apply()
    elif cmd == "prune":
        prune(len(sys.argv) > 2 and sys.argv[2] == "go")
    elif cmd == "snapshot":
        snapshot()
    else:
        raise SystemExit(__doc__)
