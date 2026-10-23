"""Checks every registered AWAKEN item/block/entity has its assets. Run after runGameTestServer:
    python3 tools/qa/check_assets.py            (reads run/awaken-registry.json or the newest game-test run dir)
Exit code 1 when anything is missing."""
import glob, json, os, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
RES = os.path.join(ROOT, "src/main/resources")
GEN = os.path.join(ROOT, "build/generated/merged")
cands = glob.glob(os.path.join(ROOT, "run*/awaken-registry.json")) + glob.glob(os.path.join(ROOT, "**/awaken-registry.json"), recursive=True)
if not cands:
    print("No awaken-registry.json found. Run tools/gradle.sh 1200 runGameTestServer first."); sys.exit(2)
dump = json.load(open(max(cands, key=os.path.getmtime)))
lang = {}
for p in glob.glob(os.path.join(GEN, "assets/awaken/lang/en_us.json")):
    lang.update(json.load(open(p)))
problems = []

def has(path):
    return os.path.exists(os.path.join(RES, path)) or os.path.exists(os.path.join(GEN, path))

for it in dump["items"]:
    if not has(f"assets/awaken/items/{it}.json"):
        problems.append(f"item {it}: missing assets/awaken/items/{it}.json")
    key_i, key_b = f"item.awaken.{it}", f"block.awaken.{it}"
    if key_i not in lang and key_b not in lang:
        problems.append(f"item {it}: missing lang key {key_i}")
for b in dump["blocks"]:
    if not has(f"assets/awaken/blockstates/{b}.json"):
        problems.append(f"block {b}: missing blockstate")
    if f"block.awaken.{b}" not in lang:
        problems.append(f"block {b}: missing lang key block.awaken.{b}")
    if b in dump["block_items"] and not has(f"data/awaken/loot_table/blocks/{b}.json"):
        problems.append(f"block {b}: missing loot table (data/awaken/loot_table/blocks/{b}.json)")
for e in dump["entities"]:
    if f"entity.awaken.{e}" not in lang:
        problems.append(f"entity {e}: missing lang key entity.awaken.{e}")
# texture references from item models
for mp in glob.glob(os.path.join(RES, "assets/awaken/models/**/*.json"), recursive=True):
    try:
        m = json.load(open(mp))
    except Exception as ex:
        problems.append(f"bad json {mp}: {ex}"); continue
    for t in (m.get("textures") or {}).values():
        if isinstance(t, str) and t.startswith("awaken:"):
            path = t.split(":", 1)[1]
            if not has(f"assets/awaken/textures/{path}.png"):
                problems.append(f"model {os.path.relpath(mp, RES)}: missing texture {t}")
print(f"{len(dump['items'])} items, {len(dump['blocks'])} blocks, {len(dump['entities'])} entities checked")
for p in problems:
    print(" -", p)
print("OK" if not problems else f"{len(problems)} problems")
sys.exit(1 if problems else 0)
