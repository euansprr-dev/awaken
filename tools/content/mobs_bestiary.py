"""Reads the mobs bestiary straight from the Java sources so lang, loot tables, item models, textures and docs never
drift from the code. Each creature is declared on one line as
    m("id", "Name", Theme.X, Tier.Y, Model.Z, "signature")
followed by builder calls up to `.build());` (parts: P("id", "Name", "ramp"); abilities: a(KIND, "Name")).
Used by tools/content/mobs_content.py and tools/art/specs/mobs.py."""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
JAVA = os.path.abspath(os.path.join(HERE, "../../src/main/java/dev/awaken/mobs/def"))

M_RE = re.compile(r'm\("([a-z0-9_]+)",\s*"((?:[^"\\]|\\.)*)",\s*Theme\.(\w+),\s*Tier\.(\w+),\s*Model\.(\w+),\s*"((?:[^"\\]|\\.)*)"\)')
P_RE = re.compile(r'P\("([a-z0-9_]+)",\s*"((?:[^"\\]|\\.)*)",\s*"(\w+)"\)')
A_RE = re.compile(r'a\((\w+),\s*"((?:[^"\\]|\\.)*)"\)')
PHASE_RE = re.compile(r'\.phase\(([0-9.]+)F,\s*"((?:[^"\\]|\\.)*)"')
TRAITS_RE = re.compile(r'\.traits\(([^)]*)\)')
SCALE_RE = re.compile(r'\.scale\(([0-9.]+)F\)')
TINT_RE = re.compile(r'\.tint\(0x([0-9A-Fa-f]{6})\)')
GLOW_RE = re.compile(r'\.glow\(0x([0-9A-Fa-f]{6})\)')
THEME_RE = re.compile(r'^\s*(\w+)\("([^"]+)",\s*"([^"]+)",\s*Rank\.(\w+),\s*0x([0-9A-Fa-f]{6}),\s*0x([0-9A-Fa-f]{6}),\s*BoltKind\.(\w+)\)', re.M)


def unescape(s):
    return s.replace('\\"', '"').replace("\\\\", "\\")


def themes():
    src = open(os.path.join(JAVA, "Theme.java")).read()
    out = {}
    for name, tid, display, rank, tint, glow, bolt in THEME_RE.findall(src):
        out[name] = {"id": tid, "display": display, "rank": rank, "tint": "#" + tint.lower(), "glow": "#" + glow.lower(), "bolt": bolt}
    return out


def mobs():
    """All creatures in declaration order (= registration order)."""
    out = []
    for fname in ("GateBestiary.java", "DeepBestiary.java"):
        src = open(os.path.join(JAVA, fname)).read()
        starts = [m.start() for m in M_RE.finditer(src)]
        for i, st in enumerate(starts):
            end = src.find(".build());", st)
            block = src[st:end]
            mm = M_RE.match(block)
            mid, name, theme, tier, model, sig = mm.groups()
            phases = []
            for seg in re.split(r'\.(?:ab|phase)\(', block)[1:]:
                phases.append([unescape(n) for _, n in A_RE.findall(seg)])
            traits = []
            tm = TRAITS_RE.search(block)
            if tm:
                traits = [t.strip() for t in tm.group(1).split(",") if t.strip()]
            sc = SCALE_RE.search(block)
            tint = TINT_RE.search(block)
            glow = GLOW_RE.search(block)
            out.append({
                "id": mid, "name": unescape(name), "theme": theme, "tier": tier, "model": model, "sig": unescape(sig),
                "parts": [{"id": p, "name": unescape(n), "ramp": r} for p, n, r in P_RE.findall(block)],
                "abilities": [unescape(n) for _, n in A_RE.findall(block)],
                "phases": phases, "phase_lines": [unescape(l) for _, l in PHASE_RE.findall(block)],
                "traits": traits, "scale": float(sc.group(1)) if sc else 1.0,
                "tint": ("#" + tint.group(1).lower()) if tint else None,
                "glow": ("#" + glow.group(1).lower()) if glow else None,
                "file": fname,
            })
    ids = [m["id"] for m in out]
    assert len(ids) == len(set(ids)), "duplicate mob id"
    return out


BOSS_TIERS = {"LORD", "GUARDIAN", "SOVEREIGN", "FINAL", "NIGHTMARE"}

if __name__ == "__main__":
    ms = mobs()
    from collections import Counter
    print(len(ms), "creatures", Counter(m["tier"] for m in ms))
    print(len(themes()), "themes")
