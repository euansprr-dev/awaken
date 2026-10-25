"""CARILLON's recorded sounds (M4 B3b, SPEC §16.0 decisions 28 and 30): the CC0 field recordings Euan approved on the
listening page (docs/audio/carillon-b3b/), cut, looped and levelled into the same files the synthesised soundscape
(audio_city_life.py) wrote, so nothing else changes: the beds' crowd, tavern and forge stems, the fountain, quay and
tavern loops, and the gulls, carts, anvils, pigeons and the market criers.

    python3 tools/art/specs/audio_city_cc0.py fetch            downloads each source through the Freesound API
                                                               (FREESOUND_TOKEN: its HQ preview; licence checked)
    python3 tools/art/specs/audio_city_cc0.py build --from DIR builds from files you downloaded from freesound.org
                                                               (their own names: <id>__<user>__<title>.<ext>)
    python3 tools/art/specs/audio_city_cc0.py build            builds from the cache the fetch filled
    python3 tools/art/specs/audio_city_cc0.py build --out DIR  writes the game files under DIR (a dry run)

Every source is pinned: its id, author, page and licence below, and the sha256 of the file it was built from in
audio_city_cc0.lock.json (a build with a different file stops). Downloads go to tools/art/.cc0-cache/<id>/ (one empty
folder each, gitignored) and are only decoded by ffmpeg. The credits are written beside the sounds
(assets/awaken/sounds/city/CREDITS.md). Segments are chosen by measurement, not by ear: the steadiest stretch for a bed or
a loop, the clearest calls and strikes for one-shots. The listening page is the check.
"""
import hashlib
import json
import os
import subprocess
import sys
import urllib.request

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
import audiokit as K  # noqa: E402
from audio_mix import TARGETS  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
CACHE = os.path.join(ROOT, "tools/art/.cc0-cache")
LOCK = os.path.join(HERE, "audio_city_cc0.lock.json")
MERGE = os.path.join(ROOT, "src/main/merge/citizens/assets/awaken")
CC0 = "http://creativecommons.org/publicdomain/zero/1.0/"
SR = K.SR
# no source is analysed beyond its first four minutes (a 19-minute file would hold a gigabyte of FFT)
LONGEST = 240.0

# what the recordings replace (audio_city_life.py leaves these alone once audio_city_cc0.lock.json exists)
RECORDED_STEMS = {"murmur_far", "murmur_near", "tavern_far", "works"}
RECORDED_LOOPS = {"amb_city_fountain", "amb_city_lap", "amb_city_tavern"}
RECORDED_EVENTS = {"city.gull.call", "city.pigeon.coo", "city.pigeon.flutter", "city.anvil", "city.cart.roll", "city.crier",
                   "city.fountain", "city.lap", "city.tavern"}


def recorded():
    """True once the recordings are in (the lock is written by a successful build)."""
    return os.path.exists(LOCK)

# id: (author, title, what it becomes)
SOURCES = {
    640254: ("gecop", "geroezemoes.wav", "bed: murmur_far"),
    625625: ("Erbsland-Music", "Solothurn - in Front of the St. Ursen Cathedral", "bed: murmur_far"),
    648483: ("nicoproson", "CROWD village square.wav", "bed: murmur_near"),
    522583: ("bushtobazaar", "Chefchaouen, Morocco (soundscape)", "bed: murmur_near"),
    200294: ("ikbenraar", "Spanish market with shouting market vendor.aif", "criers"),
    706254: ("timmydes", "Fish Market Siricusa Sicily Jan 2017.wav", "criers"),
    539811: ("denkmal", "Tavern_Greece_indoor.wav", "the tavern loop"),
    178449: ("vinjatovix", "130215 - AMB_INT - Peckham Pub.wav", "the tavern loop; bed: tavern_far"),
    53488: ("giddykipper", "horse_arches.wav", "cart roll"),
    831723: ("pnwheeler", "Road sweeper's rattling cart", "cart roll"),
    838025: ("bruno.auzet", "bubble lapping wave on concrete pier", "the quay's lapping loop"),
    442481: ("BonnyOrbit", "Fountain in small town.wav", "the fountains' loop"),
    640197: ("bruno.auzet", "traditionnal blacksmith", "anvils; bed: works"),
    468080: ("Kinoton", "Seagull Swarm at Port", "gull calls"),
    717784: ("Sandy-Ogilvie", "South Leith Parish Church Pigeons", "pigeon coos and flutters"),
}


def page(sid):
    return f"https://freesound.org/people/{SOURCES[sid][0]}/sounds/{sid}/"


# ---- getting the files ------------------------------------------------------------------------------------------------

def fetch():
    """Each source's HQ preview through the official API (needs FREESOUND_TOKEN), into a folder of its own."""
    token = os.environ.get("FREESOUND_TOKEN")
    if not token:
        sys.exit("FREESOUND_TOKEN is not set (an API key from https://freesound.org/apiv2/apply/)")
    for sid in SOURCES:
        req = urllib.request.Request(f"https://freesound.org/apiv2/sounds/{sid}/?fields=id,username,license,previews",
                                     headers={"Authorization": "Token " + token})
        meta = json.load(urllib.request.urlopen(req, timeout=60))
        if meta["license"].rstrip("/") != CC0.rstrip("/"):
            sys.exit(f"{sid}: licence is {meta['license']}, not CC0: stopping")
        url = meta["previews"]["preview-hq-ogg"]
        folder = os.path.join(CACHE, str(sid))
        os.makedirs(folder, exist_ok=True)
        dest = os.path.join(folder, f"{sid}__{meta['username']}__preview-hq.ogg")
        if not os.path.exists(dest):
            with urllib.request.urlopen(urllib.request.Request(url, headers={"Authorization": "Token " + token}), timeout=300) as r, \
                    open(dest, "wb") as f:
                f.write(r.read())
        print(f"  {sid} {meta['username']}: {os.path.getsize(dest) / 1e6:.1f} MB")


def find(sid, src):
    for d in ([src] if src else [os.path.join(CACHE, str(sid))]):
        if not os.path.isdir(d):
            continue
        for f in sorted(os.listdir(d)):
            if f.startswith(f"{sid}__") or f.startswith(f"{sid}."):
                return os.path.join(d, f)
    return None


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def load_all(src):
    lock = json.load(open(LOCK)) if os.path.exists(LOCK) else {}
    audio, missing = {}, []
    for sid in SOURCES:
        path = find(sid, src)
        if path is None:
            missing.append(f"{sid} ({SOURCES[sid][0]}: {page(sid)})")
            continue
        digest = sha256(path)
        pinned = lock.get(str(sid), {}).get("sha256")
        if pinned and pinned != digest:
            sys.exit(f"{sid}: {os.path.basename(path)} is not the file this was built from (sha256 {digest[:12]}, pinned "
                     f"{pinned[:12]}); delete its lock entry to rebuild from it on purpose")
        lock[str(sid)] = {"author": SOURCES[sid][0], "title": SOURCES[sid][1], "page": page(sid), "license": "CC0 1.0",
                          "file": os.path.basename(path), "sha256": digest}
        x = K.stereo(K.read_audio(path))
        audio[sid] = x[: K.ns(LONGEST)]
    if missing:
        sys.exit("missing sources (download each from its page, keep the file name):\n  " + "\n  ".join(missing))
    return audio, lock


# ---- measuring ----------------------------------------------------------------------------------------------------

def env_db(x, win=0.1):
    """Short-term level (dB) in windows of `win` seconds."""
    m = K.mono(x) if x.ndim > 1 else x
    k = K.ns(win)
    n = len(m) // k
    e = np.sqrt(np.mean(m[: n * k].reshape(n, k) ** 2, axis=1) + 1e-12)
    return 20 * np.log10(e)


def steadiest(x, seconds, margin=2.0):
    """The start (samples) of the `seconds`-long stretch with the evenest level and no loud jolts (cars, coughs, bumps)."""
    e = env_db(x, 0.1)
    w = int(seconds * 10)
    lo, hi = int(margin * 10), len(e) - w - int(margin * 10)
    if hi <= lo:
        return 0
    best, at = None, lo
    for s in range(lo, hi, 5):
        seg = e[s:s + w]
        med = np.median(seg)
        cost = np.std(seg) + 2.0 * max(0.0, np.percentile(seg, 99.5) - med - 9) + 0.5 * max(0.0, med - np.median(e) - 6)
        if best is None or cost < best:
            best, at = cost, s
    return at * K.ns(0.1)


def take(x, seconds, margin=2.0):
    s = steadiest(x, seconds, margin)
    return x[s:s + K.ns(seconds)]


def events(x, band, min_len, max_len, count, gap=1.0, rise=10.0):
    """The `count` clearest events: where the level in `band` (Hz) rises `rise` dB over its running floor, cut from just
    before the rise to where it falls back, `min_len`..`max_len` seconds, at least `gap` apart; loudest over floor first."""
    b = K.bandpass(K.mono(x), (band[0] * band[1]) ** 0.5, (band[0] * band[1]) ** 0.5 / max(1.0, band[1] - band[0]))
    e = env_db(b, 0.02)
    floor = np.array([np.percentile(e[max(0, i - 150):i + 150], 30) for i in range(0, len(e), 25)])
    floor = np.repeat(floor, 25)[: len(e)]
    over = e - floor
    found, i = [], 0
    while i < len(e):
        if over[i] > rise:
            j = i
            while j < len(e) and over[j] > rise * 0.4:
                j += 1
            length = (j - i) * 0.02
            if min_len <= length + 0.25 <= max_len:
                found.append((float(np.max(over[i:j])), i, j))
            i = j + 1
        else:
            i += 1
    found.sort(reverse=True)
    picked = []
    for score, i, j in found:
        if all(abs(i - p[1]) * 0.02 > gap for p in picked):
            picked.append((score, i, j))
        if len(picked) == count:
            break
    out = []
    for _, i, j in sorted(picked, key=lambda p: p[1]):
        a, z = max(0, K.ns(i * 0.02 - 0.06)), min(len(x), K.ns(j * 0.02 + 0.25))
        out.append(fades(K.mono(x[a:z]), 0.01, 0.12))
    return out


def busiest(x, seconds, count, band=(1500, 6000)):
    """`count` separate `seconds`-long stretches with the most strikes (sharp rises in `band`): a smith at work."""
    b = K.bandpass(K.mono(x), (band[0] * band[1]) ** 0.5, 1.0)
    e = env_db(b, 0.02)
    hits = np.zeros(len(e))
    hits[3:] = (e[3:] - e[:-3] > 9).astype(float)
    w = int(seconds / 0.02)
    score = np.convolve(hits, np.ones(w), "valid")
    out, taken = [], []
    for i in np.argsort(-score):
        if all(abs(i - t) > w * 1.5 for t in taken):
            taken.append(i)
            a = K.ns(i * 0.02)
            out.append(fades(K.mono(x[a:a + K.ns(seconds)]), 0.01, 0.3))
        if len(out) == count:
            break
    return out


def fades(x, fi, fo):
    y = x.copy()
    a, b = K.ns(fi), K.ns(fo)
    if x.ndim == 1:
        y[:a] *= np.linspace(0, 1, a)
        y[-b:] *= np.linspace(1, 0, b)
    else:
        y[:a] *= np.linspace(0, 1, a)[:, None]
        y[-b:] *= np.linspace(1, 0, b)[:, None]
    return y


def exact(x, n):
    """Folds or pads a loop to exactly n samples (the beds' stems share one length)."""
    if len(x) >= n:
        y = x[:n].copy()
        rest = x[n:]
        y[: len(rest)] += rest[:n]
        return y
    return np.pad(x, ((0, n - len(x)),) + ((0, 0),) * (x.ndim - 1))


def loop(x, seconds, xfade):
    return K.loop_seamless(x[: K.ns(seconds + xfade)], xfade)


def distance(x, lp, room=0.0, seed=0):
    """Pushed back: low-passed, a touch of the city's air (a short stone reverb), the dry kept under it."""
    y = K.highpass(K.lowpass(x, lp, 2), 70, 2)
    if room > 0:
        wet = K.convolve(y, K.reverb_ir(t60=1.6, predelay=0.03, bright=0.35, width=1.0, seed=seed))
        y = y + wet[: len(y)] * room
    return y


# ---- building -----------------------------------------------------------------------------------------------------

def build(out):  # build_all.py imports every spec: this one runs on demand (main), never from there
    pass


def render(src=None, out=None):
    import audio_city_life as L
    audio, lock = load_all(src)
    sounds = out or os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
    n = K.ns(L.STEM_SECONDS)
    rms = {name: r for name, _, r in L.STEMS}

    def stem(name, y):
        y = K.master(exact(K.loop_seamless(y, 2.0), n), rms[name], -3.0)
        K.write_ogg(os.path.join(sounds, L.stem_file(name) + ".ogg"), exact(y, n), 3, "AWAKEN - Carillon (" + name + ", recorded)")
        print(f"  stem {name:11s} RMS {K.gated_rms(y):6.1f}  TP {K.true_peak(y):5.1f}")

    sec = L.STEM_SECONDS + 2.0
    # the crowd: two squares far off, darkened by distance; two near, never close enough for words
    stem("murmur_far", distance(take(audio[640254], sec), 2400, 0.35, 1) * 0.8 + distance(take(audio[625625], sec), 2000, 0.4, 2) * 0.6)
    stem("murmur_near", distance(take(audio[648483], sec), 5200, 0.15, 3) * 0.75 + distance(take(audio[522583], sec), 4200, 0.2, 4) * 0.6)
    # taverns across the city at night, through walls
    stem("tavern_far", distance(take(audio[178449], sec), 900, 0.6, 5))
    # the Works' yards: the forge far off
    stem("works", distance(take(audio[640197], sec), 3000, 0.5, 6))

    at, _, aceil, aq = TARGETS["amb"]

    def place(name, y, seconds):
        y = K.master(K.mono(loop(y, seconds, 1.5)), at, aceil)
        K.write_ogg(os.path.join(sounds, name + ".ogg"), y, aq, None)
        print(f"  {name:20s} {len(y) / SR:5.1f} s  RMS {K.gated_rms(y):6.1f}")

    place("amb_city_fountain", take(audio[442481], 14.0), 12.0)
    place("amb_city_lap", take(audio[838025], 16.0), 14.0)
    tav = take(audio[539811], 16.0) * 0.8 + take(audio[178449], 16.0) * 0.6
    place("amb_city_tavern", distance(tav, 1100, 0.3, 7), 14.0)

    _, _, ceil, q = TARGETS["sfx"]
    files = {}

    def shots(eid, name, cuts):
        if len(cuts) < 2:
            sys.exit(f"{eid}: only {len(cuts)} usable cut(s) in its recording; pick other material for it")
        for k, y in enumerate(cuts):
            # one-shots are places' sounds: mono, so the game puts them where they happen
            y = K.master(K.highpass(K.mono(y) if y.ndim > 1 else y, 90, 2), -22.0, ceil - 0.7)
            K.write_ogg(os.path.join(sounds, f"city/{name}{k + 1}.ogg"), y, q, None)
        files[eid] = [f"awaken:city/{name}{k + 1}" for k in range(len(cuts))]
        print(f"  {name:15s} x{len(cuts)}")

    shots("city.gull.call", "gull_call", events(audio[468080], (900, 5000), 0.5, 2.4, 6))
    shots("city.pigeon.coo", "pigeon_coo", events(audio[717784], (250, 900), 0.5, 1.8, 5))
    shots("city.pigeon.flutter", "pigeon_flutter", events(audio[717784], (1500, 8000), 0.6, 2.0, 4, rise=14))
    shots("city.anvil", "anvil", busiest(audio[640197], 3.0, 4))
    shots("city.cart.roll", "cart_roll", [take(audio[53488], 2.8, 0.5), take(audio[53488][K.ns(8):], 2.8, 0.5),
                                          take(audio[831723], 2.8, 0.3)])
    shots("city.crier", "crier", events(audio[200294], (250, 3500), 0.6, 2.6, 5, gap=3.0)
          + events(audio[706254], (250, 3500), 0.6, 2.6, 4, gap=3.0))
    if out is None:
        write_events(files)
        with open(LOCK, "w") as f:
            json.dump(lock, f, indent=2)
            f.write("\n")
        credits(sounds, lock)


def write_events(files):
    path = os.path.join(MERGE, "sounds.json")
    ev = json.load(open(path))
    lang_path = os.path.join(MERGE, "lang/en_us.json")
    lang = json.load(open(lang_path))
    att = {"city.crier": 26}
    for eid, names in files.items():
        old = ev.get(eid, {"subtitle": "subtitles.awaken." + eid, "sounds": [{"attenuation_distance": att.get(eid, 16)}]})
        a = old["sounds"][0].get("attenuation_distance", att.get(eid, 16))
        ev[eid] = {"subtitle": old["subtitle"], "sounds": [{"name": n, "attenuation_distance": a} for n in names]}
    lang["subtitles.awaken.city.crier"] = "A stallholder calls"
    for p, d in ((path, ev), (lang_path, lang)):
        with open(p, "w") as f:
            json.dump(d, f, indent=2, ensure_ascii=False)
            f.write("\n")


def credits(sounds, lock):
    lines = ["# Carillon's recorded sounds", "",
             "Field recordings from freesound.org, each dedicated to the public domain (Creative Commons 0: no credit",
             "required; given anyway). Cut, looped and levelled by tools/art/specs/audio_city_cc0.py, which pins the exact",
             "file each was built from (audio_city_cc0.lock.json). Everything else in this folder is synthesised",
             "(tools/art/specs/audio_city_life.py).", "",
             "| Recording | Author | Licence | Used for |", "|---|---|---|---|"]
    for sid, (author, title, use) in SOURCES.items():
        lines.append(f"| [{title}]({page(sid)}) | {author} | CC0 1.0 | {use} |")
    with open(os.path.join(sounds, "city/CREDITS.md"), "w") as f:
        f.write("\n".join(lines) + "\n")


def main(argv):
    if not argv or argv[0] not in ("fetch", "build"):
        print(__doc__)
        return 1
    if argv[0] == "fetch":
        fetch()
        return 0
    src = argv[argv.index("--from") + 1] if "--from" in argv else None
    out = argv[argv.index("--out") + 1] if "--out" in argv else None
    render(src, out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
