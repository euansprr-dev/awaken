"""The gates' monsters on bodies of their own (SPEC §60; plan docs/gates-as-worlds/monsters/PLAN.md).

One file per theme in gm/ declares its monsters with gm/kit.py. This builds them all:

    python3 tools/art/build_all.py gate_monsters        textures, the rigs' Java data, motion, temperaments, voices
    python3 tools/art/specs/gate_monsters.py check      every rig against the rules below, without writing anything
    python3 tools/art/specs/gate_monsters.py preview DIR [theme or id ...]   sheets: three sides, its room's light, every clip

Written: textures/entity/monsters/<id>.png and <id>_glow.png (the mobs/ folder keeps the old bodies' skins, the fallback); mobs/client/anim/GateMonsterData.java (HeroRig's format, as
fauna3.py writes Fauna3Data); assets/awaken/life/monsters_motion.json and monsters_creatures.json (read beside
motion.json and creatures.json); src/main/merge/mobs_monsters (sounds.json: each voice is Minecraft's own recordings
by event name, re-pitched; lang: subtitles).

The rig's id is the monster's bestiary id, so nothing that names a monster changes.
"""
import importlib
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ART = os.path.abspath(os.path.join(HERE, ".."))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
sys.path.insert(0, HERE)
sys.path.insert(0, ART)
import fauna_rig as R  # noqa: E402

THEMES = ("bell", "hive", "fungal", "ember", "temple", "drowned", "clockwork", "glass", "lantern", "frost", "mirror", "precursor", "nightmare",
          "sky", "abyss")
JAVA_OUT = os.path.join(ROOT, "src/main/java/dev/awaken/mobs/client/anim/GateMonsterData.java")
LIFE = os.path.join(ROOT, "src/main/resources/assets/awaken/life")
MERGE = os.path.join(ROOT, "src/main/merge/mobs_monsters/assets/awaken")
KINDS = ("ambient", "hurt", "death", "step")
SOUND_EVENTS = "/Volumes/MEDIA/Awaken/.ref/src/net/minecraft/sounds/SoundEvents.java"
_events = None


def sound_event(name):
    """Whether Minecraft has a sound event of this id (read from the reference sources when they are on this machine)."""
    global _events
    if _events is None:
        _events = set()
        if os.path.exists(SOUND_EVENTS):
            import re
            _events = set(re.findall(r'"([a-z0-9_.]+)"', open(SOUND_EVENTS).read()))
    return not _events or name in _events
BONES = {"NORMAL": 40, "ELITE": 60, "WARDEN": 60}


def monsters(only=None):
    out = []
    for t in THEMES:
        try:
            mod = importlib.import_module("gm." + t)
        except ModuleNotFoundError as e:
            if e.name != "gm." + t:
                raise
            continue
        for m in mod.MONSTERS:
            m.theme = t
            if not only or t in only or m.id in only:
                out.append(m)
    return out


def painted(m, rig):
    P = R.Painter(rig, "monster|" + m.id)
    skin, glow = m.paint(rig, P)
    return skin, glow, P.blink()


# ---------------------------------------------------------------------------------------------------------------------
# Checks (offline: they need no game). The feet themselves are judged by the GameTest creature_motion_feet.

def world_points(rig, pose=None):
    """Every box corner of a posed rig in model space: [(bone, cube, (x, y, z))]."""
    import gm.kit as K
    pts = []

    def walk(p, M, T):
        T2 = tuple(T[i] + R._mv(M, p["pivot"])[i] for i in range(3))
        sc = p.get("scale", (1, 1, 1))
        M2 = R._mm(R._mm(M, R._rot(*p["rot"])), ((sc[0], 0, 0), (0, sc[1], 0), (0, 0, sc[2])))
        for b in p["boxes"]:
            for x in (b["x"], b["x"] + b["w"]):
                for y in (b["y"], b["y"] + b["h"]):
                    for z in (b["z"], b["z"] + b["d"]):
                        q = R._mv(M2, (x, y, z))
                        pts.append((b.get("name", ""), (q[0] + T2[0], q[1] + T2[1], q[2] + T2[2])))
        for ch in p["children"]:
            walk(ch, M2, T2)
    walk(K.tree(rig, pose), ((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0, 0, 0))
    return pts


def sampled(rig, clip, t):
    return rig.tree(rig.sample([(clip, t, 1.0)]))


def lowest(rig, clip, t):
    low, name = -1e9, ""

    def walk(p, M, T):
        nonlocal low, name
        T2 = tuple(T[i] + R._mv(M, p["pivot"])[i] for i in range(3))
        sc = p.get("scale", (1, 1, 1))
        if min(abs(v) for v in sc) < 0.05:
            return  # a part scaled away (a spent rib, a snuffed flame) is not there
        M2 = R._mm(R._mm(M, R._rot(*p["rot"])), ((sc[0], 0, 0), (0, sc[1], 0), (0, 0, sc[2])))
        for b in p["boxes"]:
            for x in (b["x"], b["x"] + b["w"]):
                for y in (b["y"], b["y"] + b["h"]):
                    for z in (b["z"], b["z"] + b["d"]):
                        q = R._mv(M2, (x, y, z))
                        if q[1] + T2[1] > low:
                            low, name = q[1] + T2[1], b.get("name", "")
        for ch in p["children"]:
            walk(ch, M2, T2)
    walk(sampled(rig, clip, t), ((1, 0, 0), (0, 1, 0), (0, 0, 1)), (0, 0, 0))
    return low, name


def bestiary():
    """{id: {"spd": speed attribute, "scale": entity scale, "tier": tier}} read from GateBestiary.java."""
    import re
    src = open(os.path.join(ROOT, "src/main/java/dev/awaken/mobs/def/GateBestiary.java")).read()
    out = {}
    for mo in re.finditer(r'm\("([a-z0-9_]+)",\s*"(?:[^"\\]|\\.)*",\s*Theme\.\w+,\s*Tier\.(\w+),[^\n]*', src):
        line = mo.group(0)
        spd = re.search(r'\.spd\(([0-9.]+)\)', line)
        sc = re.search(r'\.scale\(([0-9.]+)F\)', line)
        whole = re.search(r'm\("' + mo.group(1) + r'",.*?\.build\(\)\);', src, re.S)
        block = whole.group(0) if whole else line
        if "\n        out.add(" in block:      # ran on into the next monster: this one's own text ends where that begins
            block = block.split("\n        out.add(")[0]
        op, mi = re.search(r'\.open\((\d+)\)', line), re.search(r'\.miss\((\d+)\)', line)
        out[mo.group(1)] = {"spd": float(spd.group(1)) if spd else 0.25, "scale": float(sc.group(1)) if sc else 1.0, "tier": mo.group(2),
                            "rig": ".rig()" in line, "line": line, "abilities": re.findall(r'a\((\w+), "([^"]+)"\)', block),
                            "open": int(op.group(1)) if op else 0, "miss": int(mi.group(1)) if mi else 0}
    return out


#: Ability kinds that never leave a body open (GateMonster.opens): a call or a guard, not a move it commits itself to.
NO_OPENING = {"SUMMON", "HEAL", "GUARD", "DECOY", "FRENZY", "BLINK"}


def moves_match(m, rig, d):
    """A body acts every ability the bestiary gives its monster, and no others: each has its move, the move its wind-up
    and strike, and (where the server will leave it open after it, or stuck after a lunge that missed) its open clip."""
    out = []
    for kind, name in d["abilities"]:
        mv = rig.moves.get(name)
        if not mv:
            out.append(f"{m.id}: the bestiary gives it {name} ({kind}) and its body has no move for it")
            continue
        for suf in ("_windup", "_strike"):
            if mv + suf not in rig.clips:
                out.append(f"{m.id}: {name}: no {mv}{suf} clip")
        lunges = kind in ("CHARGE", "LEAP")
        if ((lunges and d["miss"]) or (d["open"] and kind not in NO_OPENING)) and mv + "_open" not in rig.clips:
            out.append(f"{m.id}: {name} ({kind}) leaves it open or stuck and its body has no {mv}_open clip to show it")
    for name in set(rig.moves) - {n for _, n in d["abilities"]}:
        out.append(f"{m.id}: its body acts {name}, which the bestiary does not give it")
    return out


def cadence(m, defs):
    """Strides a second at its full chase (a mob's ground pace goes as 1.6 x speed squared blocks a tick), or None."""
    g = m.build()[1].get("gait")
    d = defs.get(m.id)
    if not g or not d:
        return None
    return 1.6 * d["spd"] ** 2 * 20.0 / (g["stride"][1] * d["scale"])


def knees(m, rig):
    """A four-legged body's joints at rest: a foreleg's knee stands ahead of the line from its hip to its foot and a hind
    leg's hock behind it, as the motion layer bends them (MotionProfile.Knees) and as an animal's do. Both folding in
    under the belly reads as legs put on backwards. A knuckle-walker is the other way round (elbows back, knees
    forward): Monster(arms=True). One row of legs (a biped) and a crawler's legs out to the side read either way."""
    import gm.kit as K
    if m.arms:
        return []
    fr = K.fk(rig)
    rows = []
    for hip, knee, tip in K.legs_of(rig):
        h, k, t = K.point(rig, hip, frames=fr), K.point(rig, knee, frames=fr), K.point(rig, tip[0], tip[1], frames=fr)
        span = t[1] - h[1]
        if span < 1e-3:
            continue
        u = (k[1] - h[1]) / span
        off_z = k[2] - (h[2] + (t[2] - h[2]) * u)  # the joint behind (+) or ahead (-) of the hip-to-foot line
        off_x = k[0] - (h[0] + (t[0] - h[0]) * u)
        if abs(off_z) > abs(off_x):
            rows.append((hip, h[2], off_z))
    zs = [z for _, z, _ in rows]
    if not rows or max(zs) - min(zs) < 4.0:
        return []
    mid = (max(zs) + min(zs)) / 2
    out = []
    for hip, z, off in rows:
        if z < mid and off > -0.5:
            out.append(f"{m.id}: {hip} is a foreleg whose knee folds back under the belly (a foreleg's knee points forward)")
        if z >= mid and off < 0.5:
            out.append(f"{m.id}: {hip} is a hind leg whose joint folds forward under the belly (a hock points back)")
    return out


def lies_down(m, rig):
    """A death ends with the body on the floor: its trunk (its biggest box) no more than 12 px over it. Further up and
    something is holding it in the air like a prop: an arm or a leg left pointing down (kit.lying lays it flat)."""
    import gm.kit as K
    from gm.kit import R
    clip = rig.clips.get("death")
    if clip is None:
        return []
    pose = rig.sample([("death", clip.length, 1.0)])
    pose = {bn: {"rot": tuple(a / K.DEG for a in v["rot"]), "pos": (v["pos"][0], -v["pos"][1], v["pos"][2]), "scale": v["scale"]} for bn, v in pose.items()}
    cube, tb = K._trunk(rig)
    M, T = K.fk(rig, pose)[tb.name]
    if min(abs(M[0][0]) + abs(M[1][0]) + abs(M[2][0]), abs(M[0][1]) + abs(M[1][1]) + abs(M[2][1])) < 0.2:
        return []       # it shrinks away to nothing, or goes flat: nothing left to lie
    low = max(R._mv(M, (x, y, z))[1] + T[1] for x in (cube.x, cube.x + cube.w) for y in (cube.y, cube.y + cube.h) for z in (cube.z, cube.z + cube.d))
    if 24.0 - low > 12.0:
        return [f"{m.id}/death: it ends with its {cube.name} {24.0 - low:.1f} px over the floor, held up by {K.lowest(rig, pose)[1]}"]
    return []


def check(ms, tiers=None):
    problems = []
    defs = bestiary()
    tiers = tiers or {k: v["tier"] for k, v in defs.items()}
    seen = set()
    for m in ms:
        if m.id in seen:
            problems.append(f"{m.id}: declared twice")
        seen.add(m.id)
        try:
            rig = m.rig()
        except Exception as e:  # noqa: BLE001 - report every broken body, not the first
            problems.append(f"{m.id}: {type(e).__name__}: {e}")
            continue
        problems += rig.check()
        tier = (tiers or {}).get(m.id, "NORMAL")
        if len(rig.bones) > BONES.get(tier, 60):
            problems.append(f"{m.id}: {len(rig.bones)} bones (a {tier.lower()} monster has at most {BONES.get(tier, 60)})")
        for need in ("windup", "strike"):
            if need not in rig.clips:
                problems.append(f"{m.id}: no {need} clip (its plain blow)")
        if m.kind == "floater" and "fly" not in rig.clips:
            problems.append(f"{m.id}: a floater with no fly clip")
        if m.kind != "floater" and "walk" not in rig.clips:
            problems.append(f"{m.id}: no walk clip")
        mo = m.build()[1]
        if m.kind in ("strider", "runner", "crawler") and "gait" not in mo:
            problems.append(f"{m.id}: a {m.kind} with no gait")
        if m.kind == "floater" and "fly" not in mo:
            problems.append(f"{m.id}: a floater with no flight profile")
        for bad in ("lid", "bell"):
            for b in rig.bones:
                if b.name.split("_")[0].rstrip("0123456789") == bad:
                    problems.append(f"{m.id}: bone {b.name} would bind as {'an eyelid' if bad == 'lid' else 'a second body'}")
        problems += knees(m, rig)
        if m.kind != "floater" or m.grounds:
            problems += lies_down(m, rig)
        if m.id in defs:
            problems += moves_match(m, rig, defs[m.id])
        # at rest it stands on the ground (a floater hangs over it), and no clip drives it into the floor. Walking and
        # running, the game plants the feet itself, so a limb's boxes are left out there; a floater is only ever on
        # the floor where a miss left it.
        floor = 24.0
        limbs = set()
        for b in rig.bones:
            if b.name.startswith(("leg", "shin", "foot")):
                limbs.update(cb.name for cb in b.cubes)
        import gm.kit as K
        for name, clip in rig.clips.items():
            if m.kind == "floater" and not (name.endswith("_open") and m.grounds):
                continue
            every = [cb.name for cb in rig.cubes]
            skip = {n for n in every if n.startswith(tuple(m.sunk))} if m.sunk else set()
            skip |= limbs if name in ("walk", "run") else set()
            n = max(2, int(clip.length * 20) + 1)
            worst, part = -1e9, ""
            for k in range(n):
                pose = rig.sample([(name, clip.length * k / (n - 1), 1.0)])
                pose = {bn: {"rot": tuple(a / K.DEG for a in v["rot"]), "pos": (v["pos"][0], -v["pos"][1], v["pos"][2]), "scale": v["scale"]} for bn, v in pose.items()}
                low, nm = K.lowest(rig, pose, skip)
                if low > worst:
                    worst, part = low, nm
            if worst > floor + 2.0:
                problems.append(f"{m.id}/{name}: {part} goes {worst - floor:.1f} px into the floor")
            if name == "idle" and m.kind != "floater":
                stands = K.lowest(rig, None)[0]
                if stands < floor - 1.0:
                    problems.append(f"{m.id}: it stands {floor - stands:.1f} px off the ground at rest")
        # a strike begins where its wind-up ends
        for ab, mv in list(rig.moves.items()) + [("", "")]:
            w, s_ = (mv + "_windup", mv + "_strike") if mv else ("windup", "strike")
            if w in rig.clips and s_ in rig.clips:
                a = rig.sample([(w, rig.clips[w].length, 1.0)])
                b = rig.sample([(s_, 0.0, 1.0)])
                for bone in set(a) | set(b):
                    ra = a.get(bone, {"rot": (0, 0, 0)})["rot"]
                    rb = b.get(bone, {"rot": (0, 0, 0)})["rot"]
                    if max(abs(ra[i] - rb[i]) for i in range(3)) > 0.02:
                        problems.append(f"{m.id}: {s_} doesn't begin where {w} ends ({bone})")
                        break
        for kind in KINDS:
            for name, vol, pitch in m.voice.get(kind, []):
                if not sound_event(name):
                    problems.append(f"{m.id}: voice {kind} names no Minecraft sound {name}")
            if not m.voice.get(kind) and kind != "step":
                problems.append(f"{m.id}: no {kind} voice")
        if not m.box:
            problems.append(f"{m.id}: no box")
        if m.id not in defs:
            problems.append(f"{m.id}: no such monster in GateBestiary.java")
        cad = cadence(m, defs)
        if cad is not None and not (0.6 <= cad <= 3.6):
            problems.append(f"{m.id}: {cad:.1f} strides a second at full chase (its run stride is {m.build()[1]['gait']['stride'][1]} blocks); a body reads between 0.6 and 3.6")
    return problems


# ---------------------------------------------------------------------------------------------------------------------
# Writing

def java(built):
    chunks = []
    for m, rig in built:
        block, size, parts = [], 0, []
        for ln in rig.java_lines():
            if size + len(ln) > 24000 and block:
                parts.append(block)
                block, size = [], 0
            block.append(ln)
            size += len(ln) + 1
        if block:
            parts.append(block)
        chunks.append((m.id, parts))
    src = ["package dev.awaken.mobs.client.anim;", "", "import java.util.List;", "", "/**",
           " * GENERATED by tools/art/specs/gate_monsters.py: do not edit by hand. The gates' monsters on bodies of their own",
           " * (SPEC §60) as plain data, in the hero rigs' format ({@link HeroRig#parse}); a rig's id is its monster's bestiary",
           " * id; textures in textures/entity/monsters/{id}.png.", " */", "public final class GateMonsterData {",
           "    private GateMonsterData() {}", "", "    /** Every rig's source text, in order. */",
           "    public static final List<String[]> SOURCES = List.<String[]>of("]
    for k, (rid, parts) in enumerate(chunks):
        src.append(f"            // {rid}")
        src.append("            new String[] {")
        for pi, block in enumerate(parts):
            src.append('                """')
            for ln in block:
                src.append("                " + ln)
            src.append('                """' + ("," if pi < len(parts) - 1 else ""))
        src.append("            }" + ("," if k < len(chunks) - 1 else ""))
    src.append("    );")
    src.append("}")
    with open(JAVA_OUT, "w") as f:
        f.write("\n".join(src) + "\n")


def _dump(path, about, entries):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = ["{", '  "schemaVersion": 1,', '  "about": ' + json.dumps(about) + ",", '  "creatures": {']
    items = list(entries.items())
    for k, (key, val) in enumerate(items):
        lines.append(f"    {json.dumps(key)}: {json.dumps(val)}" + ("," if k < len(items) - 1 else ""))
    lines += ["  }", "}"]
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")


def write_life(ms):
    _dump(os.path.join(LIFE, "monsters_motion.json"),
          "The gates' monsters' motion (SPEC 60), in motion.json's shape and read beside it. GENERATED by tools/art/specs/gate_monsters.py "
          "from the theme files in tools/art/specs/gm: edit those.", {m.id: m.build()[1] for m in ms if m.build()[1]})
    _dump(os.path.join(LIFE, "monsters_creatures.json"),
          "The gates' monsters' temperaments (SPEC 60), in creatures.json's shape and read beside it. GENERATED by "
          "tools/art/specs/gate_monsters.py from the theme files in tools/art/specs/gm: edit those.", {m.id: m.life for m in ms})


SUBTITLE = {"ambient": "{} stirs", "hurt": "{} is hurt", "death": "{} falls", "step": "{} moves"}


def write_voices(ms):
    sounds, lang = {}, {}
    for m in ms:
        for kind in KINDS:
            layers = m.voice.get(kind)
            if not layers:
                continue
            key = f"monster.{m.id}.{kind}"
            sounds[key] = {"sounds": [{"name": "minecraft:" + n, "type": "event", "volume": v, "pitch": p} for n, v, p in layers],
                           "subtitle": "subtitles.awaken." + key}
            said = getattr(m, "says", {}).get(kind)
            lang["subtitles.awaken." + key] = said if said else SUBTITLE[kind].format(m.name)
    os.makedirs(os.path.join(MERGE, "lang"), exist_ok=True)
    with open(os.path.join(MERGE, "sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    with open(os.path.join(MERGE, "lang/en_us.json"), "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")


MOBS_LANG = os.path.join(ROOT, "src/main/merge/mobs/assets/awaken/lang/en_us.json")


def write_names(ms):
    """The names players see, where the mobs module keeps them (tools/content/mobs_content.py writes the same lines
    from the bestiary; this keeps the two in step without rewriting the rest of its output)."""
    import re
    src = open(MOBS_LANG).read()
    for m in ms:
        for key, val in (("entity.awaken." + m.id, m.name), (f"item.awaken.{m.id}_spawn_egg", m.name + " Spawn Egg")):
            pat = re.compile(r'("' + re.escape(key) + r'":\s*)"(?:[^"\\]|\\.)*"')
            if not pat.search(src):
                raise SystemExit(f"{MOBS_LANG}: no {key}")
            src = pat.sub(lambda mo: mo.group(1) + json.dumps(val, ensure_ascii=False), src, count=1)
    with open(MOBS_LANG, "w") as f:
        f.write(src)


def build(out):
    ms = monsters()
    problems = check(ms)
    if problems:
        raise SystemExit("gate monsters:\n  " + "\n  ".join(problems))
    built = []
    for m in ms:
        rig = m.rig()
        skin, glow, blink = painted(m, rig)
        out.entity("monsters/" + m.id, skin)
        out.entity("monsters/" + m.id + "_glow", glow)
        if blink is not None:
            out.entity("monsters/" + m.id + "_blink", blink[0])
            out.entity("monsters/" + m.id + "_glow_blink", blink[1])
        built.append((m, rig))
    java(built)
    write_life(ms)
    write_voices(ms)
    write_names(ms)
    size = os.path.getsize(JAVA_OUT)
    print(f"gate monsters: {len(built)} rigs, {size // 1024} KB of rig data")


# ---------------------------------------------------------------------------------------------------------------------
# Previews

ROOM = {  # each theme's room: the background a monster is seen against, and how its light falls on a skin (dim, warmth)
    "bell": ((22, 26, 44), 0.55, (1.0, 0.86, 0.72)), "hive": ((52, 24, 16), 0.6, (1.0, 0.82, 0.6)), "fungal": ((30, 20, 44), 0.55, (0.9, 0.86, 1.0)),
    "ember": ((26, 16, 14), 0.55, (1.0, 0.78, 0.6)), "temple": ((24, 40, 30), 0.65, (0.92, 1.0, 0.84)), "drowned": ((16, 44, 50), 0.6, (0.82, 0.96, 1.0)),
    "clockwork": ((30, 26, 24), 0.6, (1.0, 0.9, 0.74)), "glass": ((224, 204, 160), 1.0, (1.0, 1.0, 1.0)), "lantern": ((20, 18, 34), 0.6, (1.0, 0.84, 0.66)),
    "frost": ((206, 218, 232), 1.0, (0.96, 0.98, 1.0)), "mirror": ((196, 202, 216), 1.0, (1.0, 1.0, 1.0)), "precursor": ((20, 30, 42), 0.6, (0.84, 0.92, 1.0)),
    "nightmare": ((40, 22, 48), 0.55, (1.0, 0.84, 0.96)), "sky": ((150, 186, 226), 1.0, (1.0, 1.0, 1.0)), "abyss": ((14, 10, 24), 0.45, (0.86, 0.8, 1.0))}
DAY = (96, 122, 138)


def _textures(m, rig):
    from PIL import Image
    skin, glow, _ = painted(m, rig)
    sk, gl = skin.image(), glow.image()
    bg, dim, warm = ROOM[m.theme]
    base, mask = sk.copy(), sk.copy()
    bp, mp, gp = base.load(), mask.load(), gl.load()
    for y in range(base.height):
        for x in range(base.width):
            r_, g_, b_, a_ = bp[x, y]
            bp[x, y] = (int(r_ * dim * warm[0]), int(g_ * dim * warm[1]), int(b_ * dim * warm[2]), a_)
            ga = gp[x, y]
            mp[x, y] = (ga[0], ga[1], ga[2], 255) if ga[3] >= 16 else (0, 0, 0, a_)
    return Image.alpha_composite(sk, gl), Image.alpha_composite(base, gl), mask


def _place(canvas, tree, tex, mask, yaw, pitch, px, at_, halo=True):
    from PIL import ImageChops, ImageFilter
    W, H = canvas.size
    g = R._mv(R._mm(R._rot(math.radians(pitch), 0, 0), R._rot(0, math.radians(yaw), 0)), (0, 24, 0))
    origin = (W, H, at_[0] - g[0] * px, at_[1] - g[1] * px)
    im = R.render(tree, tex, yaw=yaw, pitch=pitch, px=px, bounds=(0, 1, 0, 1), origin=origin)
    canvas.paste(im, (0, 0), im)
    if halo and mask is not None:
        mk = R.render(tree, mask, yaw=yaw, pitch=pitch, px=px, bounds=(0, 1, 0, 1), origin=origin).convert("RGB")
        soft = mk.filter(ImageFilter.GaussianBlur(px * 1.6)).point(lambda v: min(255, int(v * 0.85)))
        canvas.paste(ImageChops.add(canvas, soft))


def sheet(m, outdir, cell=(210, 210), fps=8):
    """One monster: three sides in plain light and one in its room's, then every clip as a strip of frames."""
    from PIL import Image, ImageDraw
    rig = m.rig()
    lit_, dim, mask = _textures(m, rig)
    bg = ROOM[m.theme][0]
    rest = rig.tree(rig.sample([("idle", 0.0, 1.0)]))
    rest["pivot"] = (0, -m.lift, 0)
    names = [n for n in rig.clips]
    order = ["idle", "walk", "run", "fly", "angry", "windup", "strike", "heavy_windup", "heavy_strike"]
    names.sort(key=lambda n: (order.index(n) if n in order else 50 if n not in ("hurt", "death") else 90, n))
    rows = []
    trees_all = [rest]
    for n in names:
        clip = rig.clips[n]
        k = 6 if clip.loop else max(3, min(8, int(clip.length * fps) + 1))
        ts = [clip.length * i / k for i in range(k)] if clip.loop else [clip.length * i / (k - 1) for i in range(k)]
        base = [] if n in ("death",) or n.endswith(("_strike", "_open", "_windup")) or n in ("windup", "strike", "hurt", "walk", "run") else []
        trs = []
        for t in ts:
            tr = rig.tree(rig.sample(base + [(n, t, 1.0)]))
            tr["pivot"] = (0, -m.lift, 0)
            trs.append(tr)
        rows.append((n, clip, ts, trs))
        trees_all += trs
    yaw, pitch = 46, 12
    bd = R.bounds_of(trees_all, yaw, pitch)
    px = min((cell[0] - 14) / max(1e-6, bd[1] - bd[0]), (cell[1] - 20) / max(1e-6, bd[3] - bd[2]))
    g = R._mv(R._mm(R._rot(math.radians(pitch), 0, 0), R._rot(0, math.radians(yaw), 0)), (0, 24, 0))
    foot = (cell[0] / 2 - ((bd[0] + bd[1]) / 2 - g[0]) * px, cell[1] - 8 - (bd[3] - g[1]) * px)
    cols = 8
    W = cell[0] * cols
    H = cell[1] * (1 + len(rows)) + 18 * (1 + len(rows))
    out = Image.new("RGB", (W, H), (18, 20, 28))
    d = ImageDraw.Draw(out)
    for k, (yw, pt, room) in enumerate(((32, 14, False), (118, 8, False), (-150, 16, False), (32, 14, True), (62, 6, None))):
        c = Image.new("RGB", cell, bg if room else (58, 66, 96) if room is None else DAY)
        b2 = R.bounds_of([rest], yw, pt)
        p2 = min((cell[0] - 20) / max(1e-6, b2[1] - b2[0]), (cell[1] - 24) / max(1e-6, b2[3] - b2[2]))
        g2 = R._mv(R._mm(R._rot(math.radians(pt), 0, 0), R._rot(0, math.radians(yw), 0)), (0, 24, 0))
        f2 = (cell[0] / 2 - ((b2[0] + b2[1]) / 2 - g2[0]) * p2, cell[1] - 10 - (b2[3] - g2[1]) * p2)
        if room is None:  # the outline alone
            tmp = Image.new("RGB", cell, (58, 66, 96))
            im = R.render(rest, lit_, yaw=yw, pitch=pt, px=p2, bounds=(0, 1, 0, 1), origin=(cell[0], cell[1], f2[0] - g2[0] * p2, f2[1] - g2[1] * p2))
            tmp.paste(Image.new("RGB", cell, (8, 9, 14)), (0, 0), im.split()[3].point(lambda v: 255 if v > 40 else 0))
            c = tmp
        else:
            _place(c, rest, dim if room else lit_, mask if room else None, yw, pt, p2, f2, halo=bool(room))
        out.paste(c, (k * cell[0], 0))
    d.text((cell[0] * 5 + 8, 8), f"{m.name}  ({m.id})", fill=(236, 238, 246))
    d.text((cell[0] * 5 + 8, 26), f"{m.kind}, {len(rig.bones)} bones, {len(rig.cubes)} boxes, {len(rig.clips)} clips", fill=(170, 176, 196))
    d.text((cell[0] * 5 + 8, 44), "moves: " + ", ".join(f"{a} -> {v}" for a, v in rig.moves.items()), fill=(170, 176, 196))
    y = cell[1] + 18
    for n, clip, ts, trs in rows:
        d.text((4, y - 15), f"{n}  {clip.length:.2f}s{' loop' if clip.loop else ''}", fill=(206, 210, 224))
        for k, (t, tr) in enumerate(zip(ts, trs)):
            c = Image.new("RGB", cell, bg)
            ImageDraw.Draw(c).line((0, foot[1], cell[0], foot[1]), fill=tuple(min(255, v + 18) for v in bg))
            _place(c, tr, dim, mask, yaw, pitch, px, foot)
            ImageDraw.Draw(c).text((4, 4), f"{t:.2f}", fill=(150, 156, 176))
            out.paste(c, (k * cell[0], y))
        y += cell[1] + 18
    path = os.path.join(outdir, f"{m.theme}_{m.id}.png")
    out.save(path)
    return path


def keyframes(ms, path, cell=(190, 200)):
    """A theme at a glance, one row a monster: at rest in plain light, in its room, its outline, then the telling frames of
    every move (the wind-up's end, the hit, the open pose) and how its fall ends."""
    from PIL import Image, ImageDraw
    rows = []
    for m in ms:
        rig = m.rig()
        lit_, dim, mask = _textures(m, rig)
        frames = [("rest", [("idle", 0.0, 1.0)], "day"), ("in its room", [("idle", 0.0, 1.0)], "room"), ("outline", [("idle", 0.0, 1.0)], "black")]
        if "walk" in rig.clips:
            frames.append(("walk", [("walk", rig.clips["walk"].length * 0.25, 1.0)], "room"))
        if "angry" in rig.clips:
            frames.append(("angry", [("angry", rig.clips["angry"].length * 0.25, 1.0)], "room"))
        for mv in ["heavy_"] + sorted(set(rig.moves.values())):
            w, s_, o = (mv + "_windup", mv + "_strike", mv + "_open") if mv != "heavy_" else ("heavy_windup", "heavy_strike", "")
            if w not in rig.clips:
                w, s_ = "windup", "strike"
            frames.append((w, [(w, rig.clips[w].length, 1.0)], "room"))
            frames.append((s_ + " hit", [(s_, min(0.12, rig.clips[s_].length), 1.0)], "room"))
            if o in rig.clips:
                frames.append((o, [(o, 0.0, 1.0)], "room"))
        frames.append(("hurt", [("hurt", 0.05, 1.0)], "room"))
        frames.append(("death end", [("death", rig.clips["death"].length, 1.0)], "room"))
        trees = []
        for _, layers, _ in frames:
            t = rig.tree(rig.sample(layers))
            t["pivot"] = (0, -m.lift, 0)
            trees.append(t)
        rows.append((m, rig, lit_, dim, mask, frames, trees))
    cols = max(len(r_[5]) for r_ in rows)
    out = Image.new("RGB", (cell[0] * cols, (cell[1] + 16) * len(rows)), (18, 20, 28))
    d = ImageDraw.Draw(out)
    yaw, pitch = 44, 12
    for ri, (m, rig, lit_, dim, mask, frames, trees) in enumerate(rows):
        bg = ROOM[m.theme][0]
        bd = R.bounds_of(trees, yaw, pitch)
        px = min((cell[0] - 12) / max(1e-6, bd[1] - bd[0]), (cell[1] - 16) / max(1e-6, bd[3] - bd[2]))
        g = R._mv(R._mm(R._rot(math.radians(pitch), 0, 0), R._rot(0, math.radians(yaw), 0)), (0, 24, 0))
        foot = (cell[0] / 2 - ((bd[0] + bd[1]) / 2 - g[0]) * px, cell[1] - 6 - (bd[3] - g[1]) * px)
        y0 = ri * (cell[1] + 16)
        for k, ((label, _, mode), tr) in enumerate(zip(frames, trees)):
            c = Image.new("RGB", cell, DAY if mode == "day" else (58, 66, 96) if mode == "black" else bg)
            if mode == "black":
                im = R.render(tr, lit_, yaw=yaw, pitch=pitch, px=px, bounds=(0, 1, 0, 1), origin=(cell[0], cell[1], foot[0] - g[0] * px, foot[1] - g[1] * px))
                c.paste(Image.new("RGB", cell, (8, 9, 14)), (0, 0), im.split()[3].point(lambda v: 255 if v > 40 else 0))
            else:
                ImageDraw.Draw(c).line((0, foot[1], cell[0], foot[1]), fill=tuple(min(255, v + 16) for v in (DAY if mode == "day" else bg)))
                _place(c, tr, lit_ if mode == "day" else dim, None if mode == "day" else mask, yaw, pitch, px, foot, halo=mode != "day")
            out.paste(c, (k * cell[0], y0))
            d.text((k * cell[0] + 3, y0 + cell[1] + 2), (m.name + ": " if k == 0 else "") + label, fill=(214, 218, 232))
    out.save(path)
    return path


def preview(outdir, only=None):
    os.makedirs(outdir, exist_ok=True)
    ms = monsters(only)
    if only and len(ms) <= 3:
        for m in ms:
            print(sheet(m, outdir))
        return
    by = {}
    for m in ms:
        by.setdefault(m.theme, []).append(m)
    for t, group in by.items():
        print(keyframes(group, os.path.join(outdir, f"{t}_keys.png")))


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "check":
        ps = check(monsters(args[1:] or None))
        print("\n".join(ps) if ps else f"{len(monsters(args[1:] or None))} monsters: nothing wrong")
        sys.exit(1 if ps else 0)
    elif args and args[0] == "preview":
        preview(args[1], args[2:] or None)
    else:
        print(__doc__)
