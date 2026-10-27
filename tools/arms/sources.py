#!/usr/bin/env python3
"""Writes data/awaken/arms/sources.json: where every ingredient of Hunter gear and Arms weapons comes from.

docs/arms-progression/PLAN.md §3.5 ("Source lines are data, and a test enforces them"). Read from the sources the game
reads, so it can't drift:
  - the recipes (data/awaken/recipe, groups awaken_hunter_* and awaken_arms_*) for the ingredients, tags resolved
    through every tag file (src/main/resources and src/main/merge/*/);
  - the creature loot tables (data/awaken/loot_table/entities) for which creatures drop each item.
Hand-written lines for the rest live in the lang files under awaken.source.<namespace>.<path>; the GameTest
`arms_sources` fails when an ingredient has neither a creature or a line.

    python3 tools/arms/sources.py            write the file
    python3 tools/arms/sources.py --check    exit 1 if the file on disk is out of date
"""
import glob
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "src", "main", "resources", "data", "awaken")
OUT = os.path.join(RES, "arms", "sources.json")
GROUPS = ("awaken_hunter_", "awaken_arms_")
MAX_CREATURES = 5


def tag_files(name):
    out = []
    for base in [os.path.join(RES, "tags", "item")] + glob.glob(os.path.join(ROOT, "src", "main", "merge", "*", "data", "awaken", "tags", "item")):
        p = os.path.join(base, name + ".json")
        if os.path.exists(p):
            out.append(p)
    return out


def resolve_tag(tag, seen=None):
    """Item ids in an awaken: item tag (nested #tags followed; other namespaces' tags are kept as '#id')."""
    seen = seen or set()
    ns, _, name = tag.partition(":")
    if tag in seen:
        return []
    seen.add(tag)
    if ns != "awaken":
        return ["#" + tag]
    items = []
    for p in tag_files(name):
        for v in json.load(open(p)).get("values", []):
            v = v["id"] if isinstance(v, dict) else v
            if v.startswith("#"):
                items.extend(resolve_tag(v[1:], seen))
            else:
                items.append(v)
    return items


def kind_of(token):
    """What a recipe slot is called to a player: part, binding, hide, metal, essence, or None."""
    if token.startswith("#awaken:hunter_core_"):
        return "part"
    if token.startswith("#awaken:hunter_bind_"):
        return "binding"
    if token.startswith("#awaken:hunter_hide_"):
        return "hide"
    if token.startswith("#awaken:tier_metal_"):
        return "metal"
    if token == "awaken:essence_shard":
        return "essence"
    return None


def main():
    ingredients = set()
    blades = {}
    for f in sorted(glob.glob(os.path.join(RES, "recipe", "*.json"))):
        r = json.load(open(f))
        if not str(r.get("group", "")).startswith(GROUPS) or "key" not in r:
            continue
        rid = os.path.basename(f)[:-5]
        counts = {}
        for row in r["pattern"]:
            for ch in row:
                if ch == " ":
                    continue
                token = r["key"][ch]
                token = token if isinstance(token, str) else token.get("item") or token.get("tag", "")
                k = kind_of(token)
                if k:
                    counts[k] = counts.get(k, 0) + 1
        for token in r["key"].values():
            token = token if isinstance(token, str) else token.get("item") or token.get("tag", "")
            if token.startswith("#"):
                ingredients.update(i for i in resolve_tag(token[1:]) if not i.startswith("#"))
            else:
                ingredients.add(token)
        if r["group"] == "awaken_hunter_blade":
            blades["awaken:" + rid] = {k: counts[k] for k in sorted(counts)}

    drops = {}
    for f in sorted(glob.glob(os.path.join(RES, "loot_table", "entities", "*.json"))):
        creature = os.path.basename(f)[:-5]
        for pool in json.load(open(f)).get("pools", []):
            for e in pool.get("entries", []):
                name = e.get("name")
                if name in ingredients:
                    drops.setdefault(name, [])
                    if "awaken:" + creature not in drops[name]:
                        drops[name].append("awaken:" + creature)
    for k in drops:
        drops[k] = drops[k][:MAX_CREATURES]

    data = {
        "schemaVersion": 1,
        "ingredients": sorted(ingredients),
        "drops": {k: drops[k] for k in sorted(drops)},
        "blades": {k: blades[k] for k in sorted(blades)},
    }
    text = json.dumps(data, indent=1) + "\n"
    if "--check" in sys.argv:
        ok = os.path.exists(OUT) and open(OUT).read() == text
        print("sources.json is", "current" if ok else "OUT OF DATE")
        sys.exit(0 if ok else 1)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    open(OUT, "w").write(text)
    undropped = [i for i in data["ingredients"] if i not in drops]
    print(f"{len(data['ingredients'])} ingredients, {len(drops)} with creature drops, {len(blades)} blades -> {os.path.relpath(OUT, ROOT)}")
    print("hand-written lines needed for:", ", ".join(undropped))


if __name__ == "__main__":
    main()
