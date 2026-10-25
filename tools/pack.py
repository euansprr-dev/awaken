#!/usr/bin/env python3
"""The Awakened Pack: pin, install, remove and export the mods that ship around Awaken (docs/plan-pack.html).

  python3 -I tools/pack.py lock                          resolve pack/mods.json against Modrinth into pack/lock.json
  python3 -I tools/pack.py list                          what the lock pins, by profile
  python3 -I tools/pack.py install <game-dir> <profiles>  e.g. install run-autopilot perf,horizon  (all = every profile)
  python3 -I tools/pack.py install <game-dir> <profiles> --reset-configs   also overwrite the pack's config files
  python3 -I tools/pack.py remove <game-dir>             take out exactly what install put there
  python3 -I tools/pack.py status <game-dir>             what is installed, hashes, and jars the pack doesn't own
  python3 -I tools/pack.py export <out.mrpack> <profiles> --awaken-jar build/libs/awaken-x.jar
  python3 -I tools/pack.py dhconfig <game-dir> swift|cinematic|default   Distant Horizons settings for a look
  python3 -I tools/pack.py backend <game-dir> vulkan|opengl|default       the graphics API the game starts with

install writes the Swift Distant Horizons settings when the horizon profile goes into a folder that has none yet
(an existing DistantHorizons.toml is the player's and is left alone), and copies each chosen profile's config files
(mods.json "configs") the same way: only when missing, unless --reset-configs. install also makes Vulkan the graphics
API unless the player picked one (options.txt preferredGraphicsBackend left at "default" or missing); Vulkan measured
15-33% faster than OpenGL on Apple Silicon with every pack mod working (docs/perf/vulkan/REPORT.md). install only ever uses the lock (no network lookups, exact SHA-512), keeps a record in <game-dir>/mods/.awaken-pack.json,
never deletes a jar it didn't put there, and refuses to touch a game folder a client is running from.
Resource packs (a Modrinth project of type resourcepack, e.g. Fresh Animations) lock like mods, install into
<game-dir>/resourcepacks/ and are switched on in options.txt's resourcePacks list (added if missing, never taking
out the player's own packs); remove switches off and deletes only the ones install put there.
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODS = ROOT / "pack" / "mods.json"
LOCK = ROOT / "pack" / "lock.json"
CACHE = Path(os.environ.get("AWAKEN_PACK_CACHE", "/Volumes/MEDIA/.awaken-pack-cache"))
MARKER = ".awaken-pack.json"
API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "awaken-pack/1 (github.com/euansprr; modpack installer)"}
CHANNEL_RANK = {"release": 0, "beta": 1, "alpha": 2}


def get(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return json.load(r)
        except Exception as e:  # transient network trouble: back off, then give up loudly
            if attempt == 3:
                raise SystemExit(f"Modrinth request failed: {url}: {e}")
            time.sleep(1.5 * (attempt + 1))


def sha512(path):
    h = hashlib.sha512()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def load(path):
    with open(path) as f:
        return json.load(f)


def save(path, data):
    tmp = Path(str(path) + ".tmp")
    with open(tmp, "w") as f:
        json.dump(data, f, indent=1)
        f.write("\n")
    tmp.replace(path)  # never leave a half-written lock or marker behind


# ---------------------------------------------------------------------------------------------------------------- lock

def best_version(project, mc, loader, pin=None):
    if pin and not pin.startswith("~"):  # an exact Modrinth version id, as a dependency names it
        return get(f"{API}/version/{pin}")
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps([loader])})
    versions = get(f"{API}/project/{project}/version?{q}")
    if pin:  # "~<version_number>" in mods.json pins one release by its name
        versions = [v for v in versions if v["version_number"] == pin[1:]]
    if not versions:
        return None
    # newest release if there is one, else the newest beta, else alpha (Modrinth lists newest first)
    return sorted(versions, key=lambda v: CHANNEL_RANK.get(v["version_type"], 3))[0]


def cmd_lock():
    spec = load(MODS)
    mc, loader = spec["minecraft"], spec["loader"]
    entries, by_id, missing = {}, {}, []
    queue = [(m["slug"], set(m["profiles"]), None, m.get("why", ""), m.get("pin")) for m in spec["mods"]]
    while queue:
        ref, profiles, parent, why, pin = queue.pop(0)
        project = get(f"{API}/project/{ref}")
        slug = project["slug"]
        if slug in entries:  # already pinned: just widen its profiles and record who needs it
            e = entries[slug]
            e["profiles"] = sorted(set(e["profiles"]) | profiles)
            if parent and parent not in e["dependencyOf"]:
                e["dependencyOf"].append(parent)
            if pin and not pin.startswith("~") and pin != e["versionId"]:
                print(f"  warning: {parent} wants {slug} version {pin}, the lock has {e['version']} ({e['versionId']})")
            continue
        pack = project.get("project_type") == "resourcepack"
        v = best_version(project["id"], mc, "minecraft" if pack else loader, pin)  # Modrinth lists packs under "minecraft"
        if v is None:
            missing.append(f"{slug} (needed by {parent})" if parent else slug)
            continue
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        entries[slug] = {
            "slug": slug, "title": project["title"], "projectId": project["id"], "versionId": v["id"],
            "version": v["version_number"], "channel": v["version_type"], "published": v["date_published"][:10],
            "file": f["filename"], "url": f["url"], "size": f["size"],
            "sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"],
            "client": project.get("client_side"), "server": project.get("server_side"),
            "profiles": sorted(profiles), "dependencyOf": [parent] if parent else [], "why": why,
        }
        if pack:
            entries[slug]["kind"] = "resourcepack"
        by_id[project["id"]] = slug
        for d in v["dependencies"]:
            if d["dependency_type"] == "required" and d.get("project_id"):
                queue.append((d["project_id"], set(profiles), slug, "dependency", d.get("version_id")))
        time.sleep(0.1)
    if missing:
        raise SystemExit("No " + mc + " " + loader + " build for: " + ", ".join(missing) + ". Lock not written.")
    lock = {"schemaVersion": 1, "minecraft": mc, "loader": loader, "neoforge": spec.get("neoforge"),
            "generated": time.strftime("%Y-%m-%d %H:%M"), "files": sorted(entries.values(), key=lambda e: e["slug"])}
    save(LOCK, lock)
    print(f"locked {len(entries)} files into {LOCK.relative_to(ROOT)}")
    for e in lock["files"]:
        tag = "" if e["channel"] == "release" else f"  [{e['channel']}]"
        dep = f"  (for {', '.join(e['dependencyOf'])})" if e["dependencyOf"] else ""
        print(f"  {e['slug']:28} {e['version']:34} {','.join(e['profiles'])}{tag}{dep}")


def cmd_list():
    lock = load(LOCK)
    profiles = sorted({p for e in lock["files"] for p in e["profiles"]})
    for p in profiles:
        files = [e for e in lock["files"] if p in e["profiles"]]
        print(f"{p} ({len(files)}): " + ", ".join(f"{e['slug']} {e['version']}" for e in files))


# ------------------------------------------------------------------------------------------------- install and remove

def client_running_in(game_dir):
    """True if a dev client runs from this game folder (its cwd) or from the worktree that owns it."""
    game_dir = game_dir.resolve()
    tree = str(game_dir.parent) + "/"
    try:
        ps = subprocess.run(["ps", "-axo", "pid=,command="], capture_output=True, text=True, check=True).stdout
    except Exception:
        return True  # can't tell: be safe
    for line in ps.splitlines():
        parts = line.split(None, 2)
        # only real game JVMs: a shell whose command text mentions the same words is not a client
        if len(parts) < 3 or not parts[1].endswith("/java") or "devlaunch.Main" not in parts[2]:
            continue
        pid = parts[0]
        if tree in line:
            return True
        cwd = subprocess.run(["lsof", "-a", "-d", "cwd", "-p", pid, "-Fn"], capture_output=True, text=True).stdout
        if any(l[1:] == str(game_dir) for l in cwd.splitlines() if l.startswith("n")):
            return True
    return False


def select(lock, profiles):
    want = set(profiles.split(",")) if profiles != "all" else {p for e in lock["files"] for p in e["profiles"]}
    unknown = want - {p for e in lock["files"] for p in e["profiles"]}
    if unknown:
        raise SystemExit("unknown profile(s): " + ", ".join(sorted(unknown)))
    return [e for e in lock["files"] if want & set(e["profiles"])], sorted(want)


def fetch(e):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / f"{e['sha512'][:32]}-{e['file']}"
    if path.exists() and sha512(path) == e["sha512"]:
        return path
    tmp = Path(str(path) + ".part")
    with urllib.request.urlopen(urllib.request.Request(e["url"], headers=UA), timeout=120) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    if sha512(tmp) != e["sha512"]:
        tmp.unlink()
        raise SystemExit(f"hash mismatch for {e['file']} from {e['url']}: not installed")
    tmp.replace(path)
    return path


def place(game_dir, e):
    """Where a locked file lives in a game folder: resource packs in resourcepacks/, everything else in mods/."""
    return Path(game_dir) / ("resourcepacks" if e.get("kind") == "resourcepack" else "mods") / e["file"]


def enable_packs(game_dir, add, drop):
    """options.txt resourcePacks: switches on the pack files in add (if missing) and off the ones in drop, which install
    put there and has just deleted. The player's own packs and their order are left as they are."""
    if not add and not drop:
        return
    opts = Path(game_dir) / "options.txt"
    lines = opts.read_text().splitlines() if opts.exists() else []
    at = next((i for i, l in enumerate(lines) if l.startswith("resourcePacks:")), None)
    packs = json.loads(lines[at].split(":", 1)[1]) if at is not None else []
    before = list(packs)
    packs = [p for p in packs if p not in {f"file/{n}" for n in drop} | set(drop)]
    for n in add:
        if f"file/{n}" not in packs and n not in packs:
            packs.append(f"file/{n}")  # last in the list is the highest priority (below the mods' own resources)
    if packs == before:
        return
    line = "resourcePacks:" + json.dumps(packs, separators=(",", ":"))
    if at is None:
        lines.append(line)
    else:
        lines[at] = line
    opts.write_text("\n".join(lines) + "\n")
    print(f"  resource packs on in {opts}: {', '.join(packs) or 'none'}")


def marker_of(mods_dir):
    m = mods_dir / MARKER
    return load(m) if m.exists() else {"files": []}


def profile_configs(names):
    """The config files mods.json gives the chosen profiles: (source under the repo, target under the game folder)."""
    spec = load(MODS)
    return [(c["source"], c["target"]) for c in spec.get("configs", []) if c["profile"] in names]


def install_configs(game_dir, names, reset):
    for source, target in profile_configs(names):
        src, dst = ROOT / source, Path(game_dir) / target
        if not src.exists():
            print(f"  config {source} not in this checkout yet: skipped")
            continue
        if dst.exists() and not reset:
            continue  # the player's (a mod may also have rewritten it with comments): leave it
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        print(f"  config {target} from {source}")


def cmd_install(game_dir, profiles, reset_configs=False):
    game_dir = Path(game_dir)
    if client_running_in(game_dir):
        raise SystemExit(f"a client is running from {game_dir}: close it first")
    lock = load(LOCK)
    chosen, names = select(lock, profiles)
    mods_dir = game_dir / "mods"
    old = marker_of(mods_dir)
    # terrain mods reshape every chunk generated from now on: an old world would get seams where new land meets old,
    # so the world profile only goes into a folder whose worlds were all made with it (or that has none yet)
    saves = game_dir / "saves"
    has_worlds = saves.is_dir() and any(p.is_dir() for p in saves.iterdir())
    if "world" in names and has_worlds and "world" not in old.get("profiles", []):
        raise SystemExit(f"{game_dir} has worlds made without the world profile; terrain mods would seam them. "
                         "Use a fresh game folder (tools/play.sh --pack uses run-pack).")
    mods_dir.mkdir(parents=True, exist_ok=True)
    keep = {e["file"] for e in chosen}
    dropped = []
    for f in old["files"]:  # take out what an earlier install put here and this selection no longer wants
        p = place(game_dir, f)
        if f["file"] not in keep and p.exists() and sha512(p) == f["sha512"]:
            p.unlink()
            print(f"  removed {f['file']}")
            if f.get("kind") == "resourcepack":
                dropped.append(f["file"])
    for e in chosen:
        src = fetch(e)
        dst = place(game_dir, e)
        dst.parent.mkdir(parents=True, exist_ok=True)
        if not (dst.exists() and sha512(dst) == e["sha512"]):
            shutil.copyfile(src, dst)
            print(f"  installed {e['file']}")
    enable_packs(game_dir, [e["file"] for e in chosen if e.get("kind") == "resourcepack"], dropped)
    if any(e["slug"] == "distanthorizons" for e in chosen):
        cmd_dhconfig(game_dir, "swift", only_if_missing=True)
    install_configs(game_dir, names, reset_configs)
    cmd_backend(game_dir, "vulkan", only_if_unset=True)
    mac_windowing(game_dir)
    culling_exemptions(game_dir)
    save(mods_dir / MARKER, {"schemaVersion": 1, "profiles": names, "installed": time.strftime("%Y-%m-%d %H:%M"),
                             "lockGenerated": lock["generated"],
                             "files": [{"slug": e["slug"], "file": e["file"], "sha512": e["sha512"],
                                        **({"kind": e["kind"]} if e.get("kind") else {})} for e in chosen]})
    foreign = sorted(p.name for p in mods_dir.glob("*.jar") if p.name not in keep)
    print(f"{len(chosen)} pack files in {mods_dir} ({', '.join(names)})" + (f"; not ours: {', '.join(foreign)}" if foreign else ""))


def cmd_remove(game_dir):
    game_dir = Path(game_dir)
    if client_running_in(game_dir):
        raise SystemExit(f"a client is running from {game_dir}: close it first")
    mods_dir = game_dir / "mods"
    old = marker_of(mods_dir)
    dropped = []
    for f in old["files"]:
        p = place(game_dir, f)
        if p.exists() and sha512(p) == f["sha512"]:
            p.unlink()
            print(f"  removed {f['file']}")
            if f.get("kind") == "resourcepack":
                dropped.append(f["file"])
    enable_packs(game_dir, [], dropped)
    if (mods_dir / MARKER).exists():
        (mods_dir / MARKER).unlink()
    print(f"pack removed from {mods_dir}")


def cmd_status(game_dir):
    mods_dir = Path(game_dir) / "mods"
    old = marker_of(mods_dir)
    print(f"profiles: {', '.join(old.get('profiles', [])) or 'none'}  installed: {old.get('installed', '-')}")
    ours = set()
    for f in old["files"]:
        p = place(game_dir, f)
        state = "ok" if p.exists() and sha512(p) == f["sha512"] else ("changed" if p.exists() else "missing")
        ours.add(f["file"])
        print(f"  {state:8} {f['file']}")
    for p in sorted(mods_dir.glob("*.jar")):
        if p.name not in ours:
            print(f"  {'not ours':8} {p.name}")


# ------------------------------------------------------------------------------------------------- graphics API

def cmd_backend(game_dir, backend, only_if_unset=False):
    """Sets options.txt preferredGraphicsBackend. only_if_unset leaves a player's explicit choice (opengl or vulkan)."""
    game_dir = Path(game_dir)
    if not only_if_unset and client_running_in(game_dir):
        raise SystemExit(f"a client is running from {game_dir}: close it first")
    opts = game_dir / "options.txt"
    lines = opts.read_text().splitlines() if opts.exists() else []
    current = next((l.split(":", 1)[1].strip().strip('"') for l in lines if l.startswith("preferredGraphicsBackend:")), None)
    keep = only_if_unset and current not in (None, "default")
    if not keep and current != backend:
        lines = [l for l in lines if not l.startswith("preferredGraphicsBackend:")] + [f'preferredGraphicsBackend:"{backend}"']
        opts.write_text("\n".join(lines) + "\n")
        print(f"  graphics API: {backend} (was {current or 'unset'}) in {opts}; takes effect on the next launch")
    if (current if keep else backend) == "vulkan":
        early_window_off(game_dir)


# Awaken block entities whose effect is far bigger than their block and sits on an invisible anchor inside other
# blocks (the gate vortex, doorway vortices, checkpoint rings): EntityCulling traces to the block itself, finds it
# covered, and skips drawing it, so the gate showed its pad and ring with no vortex.
CULLING_EXEMPT = ["awaken:gate_vortex", "awaken:gate_portal", "awaken:checkpoint_ring"]


def culling_exemptions(game_dir):
    """Adds CULLING_EXEMPT to EntityCulling's block entity whitelist (it writes its file on first launch)."""
    cfg = game_dir / "config" / "entityculling.json"
    if not cfg.exists():
        return
    data = json.loads(cfg.read_text())
    wl = data.setdefault("blockEntityWhitelist", [])
    added = [i for i in CULLING_EXEMPT if i not in wl]
    if not added:
        return
    wl.extend(added)
    cfg.write_text(json.dumps(data, indent=2) + "\n")
    print(f"  EntityCulling never culls {', '.join(added)} ({cfg})")


def mac_windowing(game_dir):
    """On a Mac, exclusive fullscreen (Sodium turns it on by default) always takes the built-in display, so with an
    external monitor the game goes fullscreen on a screen you may not be looking at. Borderless follows the window."""
    if sys.platform != "darwin":
        return
    opts = game_dir / "options.txt"
    lines = opts.read_text().splitlines() if opts.exists() else []
    if "exclusiveFullscreen:false" in lines:
        return
    lines = [l for l in lines if not l.startswith("exclusiveFullscreen:")] + ["exclusiveFullscreen:false"]
    opts.write_text("\n".join(lines) + "\n")
    print(f"  exclusive fullscreen off in {opts} (on a Mac it always picks the built-in display)")


def early_window_off(game_dir):
    """NeoForge's early loading screen can't hand over to Minecraft on Vulkan (MoltenVK): the game freezes at "loading
    mods" with "the background loading screen renderer seems stuck". Vulkan game folders run without it."""
    fml = game_dir / "config" / "fml.toml"
    text = fml.read_text() if fml.exists() else ""
    if "earlyWindowControl = false" in text:
        return
    if "earlyWindowControl" in text:
        text = "\n".join("earlyWindowControl = false" if l.startswith("earlyWindowControl") else l for l in text.splitlines()) + "\n"
    else:
        text = text + ("" if text.endswith("\n") or not text else "\n") + "earlyWindowControl = false\n"
    fml.parent.mkdir(parents=True, exist_ok=True)
    fml.write_text(text)
    print(f"  early loading screen off in {fml} (it freezes on Vulkan)")


# ---------------------------------------------------------------------------------------------- Distant Horizons looks

def cmd_dhconfig(game_dir, preset, only_if_missing=False):
    """Writes config/DistantHorizons.toml from DH's own default file with one look's keys changed."""
    import re
    target = Path(game_dir) / "config" / "DistantHorizons.toml"
    if only_if_missing and target.exists():
        return
    text = (ROOT / "pack" / "dh" / "default.toml").read_text()
    if preset != "default":
        changes = {k: v for k, v in load(ROOT / "pack" / "dh" / f"{preset}.json").items() if not k.startswith("_")}
        for key, value in changes.items():
            text, n = re.subn(rf"(?m)^(\s*){re.escape(key)} = .*$", lambda m: f"{m.group(1)}{key} = {value}", text)
            if n != 1:
                raise SystemExit(f"DistantHorizons.toml: expected one '{key}', found {n}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)
    print(f"  Distant Horizons: {preset} settings in {target}")


# ------------------------------------------------------------------------------------------------------------- export

def env(e):
    side = lambda v: "required" if v == "required" else ("optional" if v == "optional" else "unsupported")
    client, server = side(e["client"]), side(e["server"])
    # single player runs the server inside the client: a launcher must never skip a mod the server runs
    # (Lithostitched is "client unsupported" on Modrinth, yet Tectonic needs it in every single-player world)
    if client == "unsupported" and server != "unsupported":
        client = server
    return {"client": client, "server": server}


def cmd_export(out, profiles, awaken_jar):
    lock = load(LOCK)
    chosen, names = select(lock, profiles)
    jar = Path(awaken_jar)
    if not jar.exists():
        raise SystemExit(f"no Awaken jar at {jar}: run tools/gradle.sh 900 build first")
    index = {
        "formatVersion": 1, "game": "minecraft", "versionId": time.strftime("%Y.%m.%d"),
        "name": "The Awakened Pack", "summary": "Awaken and the mods chosen to frame it: " + ", ".join(names),
        "files": [{"path": f"{'resourcepacks' if e.get('kind') == 'resourcepack' else 'mods'}/{e['file']}",
                   "hashes": {"sha1": e["sha1"], "sha512": e["sha512"]},
                   "env": env(e), "downloads": [e["url"]], "fileSize": e["size"]} for e in chosen],
        "dependencies": {"minecraft": lock["minecraft"], "neoforge": lock["neoforge"]},
    }
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=1))
        z.write(jar, f"overrides/mods/{jar.name}")  # Awaken isn't on Modrinth: it travels inside the pack
        for cfg in sorted((ROOT / "pack" / "config").glob("**/*")) if (ROOT / "pack" / "config").exists() else []:
            if cfg.is_file():
                z.write(cfg, "overrides/config/" + str(cfg.relative_to(ROOT / "pack" / "config")))
        for source, target in profile_configs(names):  # e.g. the Awakened Tectonic preset as config/tectonic.json
            if (ROOT / source).exists():
                z.write(ROOT / source, "overrides/" + target)
    print(f"wrote {out}: {len(chosen)} mods + {jar.name} ({', '.join(names)})")


def main(argv):
    if len(argv) < 2:
        raise SystemExit(__doc__)
    c = argv[1]
    if c == "lock":
        cmd_lock()
    elif c == "list":
        cmd_list()
    elif c == "install" and len(argv) in (4, 5) and (len(argv) == 4 or argv[4] == "--reset-configs"):
        cmd_install(argv[2], argv[3], reset_configs=len(argv) == 5)
    elif c == "remove" and len(argv) == 3:
        cmd_remove(argv[2])
    elif c == "status" and len(argv) == 3:
        cmd_status(argv[2])
    elif c == "dhconfig" and len(argv) == 4 and argv[3] in ("swift", "cinematic", "default"):
        cmd_dhconfig(argv[2], argv[3])
    elif c == "backend" and len(argv) == 4 and argv[3] in ("vulkan", "opengl", "default"):
        cmd_backend(argv[2], argv[3])
    elif c == "export" and len(argv) == 6 and argv[4] == "--awaken-jar":
        cmd_export(argv[2], argv[3], argv[5])
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main(sys.argv)
