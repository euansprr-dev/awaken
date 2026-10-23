"""Generates the mobs module's JSON: loot tables, spawn egg and boss part item models, lang, sounds.json, and the
bestiary tables in docs/modules/mobs.md. Reads the bestiary from the Java sources (mobs_bestiary.py).
Run: python3 tools/content/mobs_content.py"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import mobs_bestiary as B  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "../.."))
RES = os.path.join(ROOT, "src/main/resources")
MERGE = os.path.join(ROOT, "src/main/merge/mobs/assets/awaken")
DOC = os.path.join(ROOT, "docs/modules/mobs.md")

# Themed vanilla drops: (common item, common max count, rare item, rare chance)
THEME_DROPS = {
    "BELL": ("minecraft:bone", 2, "minecraft:candle", 0.08),
    "FUNGAL": ("minecraft:brown_mushroom", 2, "minecraft:red_mushroom", 0.25),
    "EMBER": ("minecraft:coal", 2, "minecraft:raw_iron", 0.10),
    "DROWNED": ("minecraft:ink_sac", 1, "minecraft:paper", 0.30),
    "CLOCKWORK": ("minecraft:copper_ingot", 2, "minecraft:redstone", 0.25),
    "GLASS": ("minecraft:quartz", 2, "minecraft:glass_pane", 0.25),
    "HIVE": ("minecraft:honeycomb", 1, "minecraft:honey_bottle", 0.08),
    "TEMPLE": ("minecraft:vine", 2, "minecraft:moss_block", 0.15),
    "LANTERN": ("minecraft:gold_nugget", 3, "minecraft:candle", 0.20),
    "FROST": ("minecraft:snowball", 3, "minecraft:packed_ice", 0.10),
    "MIRROR": ("minecraft:amethyst_shard", 2, "minecraft:quartz", 0.25),
    "PRECURSOR": ("minecraft:redstone", 2, "minecraft:copper_ingot", 0.25),
    "NIGHTMARE": ("minecraft:phantom_membrane", 1, "minecraft:ender_pearl", 0.08),
    "SKY": ("minecraft:feather", 2, "minecraft:wind_charge", 0.20),
    "ABYSS": ("minecraft:ender_pearl", 1, "minecraft:echo_shard", 0.05),
    "RIM": ("minecraft:glow_berries", 2, "minecraft:moss_block", 0.15),
    "GARDENS": ("minecraft:vine", 2, "minecraft:spore_blossom", 0.05),
    "CHOIR": ("minecraft:prismarine_shard", 2, "minecraft:nautilus_shell", 0.04),
    "ORCHARD": ("minecraft:bone", 3, "minecraft:bone_block", 0.10),
    "SILENT": ("minecraft:sculk", 1, "minecraft:echo_shard", 0.05),
    "FOUNDRY": ("minecraft:copper_ingot", 2, "minecraft:redstone", 0.30),
    "INVERTED": ("minecraft:prismarine_crystals", 2, "minecraft:heart_of_the_sea", 0.01),
    "DREAMING": ("minecraft:phantom_membrane", 1, "minecraft:amethyst_shard", 0.25),
    "COURT": ("minecraft:gold_ingot", 1, "minecraft:obsidian", 0.20),
    "SEED": ("minecraft:glowstone_dust", 2, "minecraft:amethyst_shard", 0.25),
    "VEIL": ("minecraft:phantom_membrane", 1, "minecraft:echo_shard", 0.03),
    "DREAM": ("minecraft:phantom_membrane", 1, "minecraft:amethyst_shard", 0.10),
    "FEAR": ("minecraft:phantom_membrane", 2, "minecraft:ender_pearl", 0.20),
}

# Essence shards by tier: count = floor(base + per_level * contentLevel + random), at most max.
ESSENCE = {
    "NORMAL": (0.35, 0.035, 16),
    "ELITE": (1.5, 0.08, 32),
    "WARDEN": (3.0, 0.15, 48),
    "LORD": (6.0, 0.4, 64),
    "GUARDIAN": (7.0, 0.45, 64),
    "SOVEREIGN": (10.0, 0.5, 64),
    "FINAL": (20.0, 0.6, 64),
    "NIGHTMARE": (1.0, 0.06, 16),
}
TIER_DROP_MULT = {"NORMAL": 1, "ELITE": 2, "WARDEN": 3, "LORD": 4, "GUARDIAN": 4, "SOVEREIGN": 5, "FINAL": 6, "NIGHTMARE": 1}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def looting(minv=0.0, maxv=1.0):
    return {"type": "minecraft:enchanted_count_increase", "count": {"type": "minecraft:uniform", "min": minv, "max": maxv},
            "enchantment": "minecraft:looting"}


def loot_table(m):
    tier = m["tier"]
    base, per, mx = ESSENCE[tier]
    pools = [{
        "rolls": 1,
        "condition": {"type": "minecraft:killed_by_player"},
        "entries": [{"type": "minecraft:item", "name": "awaken:essence_shard",
                     "modifier": [{"type": "awaken:mob_level_count", "base": base, "per_level": per, "max": mx}]}],
    }]
    common, cmax, rare, rchance = THEME_DROPS[m["theme"]]
    mult = TIER_DROP_MULT[tier]
    pools.append({
        "rolls": 1,
        "entries": [{"type": "minecraft:item", "name": common,
                     "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 0, "max": cmax * mult}}, looting()]}],
    })
    pools.append({
        "rolls": 1,
        "condition": {"type": "minecraft:all_of", "terms": [
            {"type": "minecraft:killed_by_player"},
            {"type": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
             "unenchanted_chance": min(1.0, rchance * mult),
             "enchanted_chance": {"type": "minecraft:linear", "base": min(1.0, rchance * mult + 0.02), "per_level_above_first": 0.02}}]},
        "entries": [{"type": "minecraft:item", "name": rare}],
    })
    for i, part in enumerate(m["parts"]):
        chance = 1.0 if i == 0 else (0.6 if tier == "LORD" else 1.0)
        pool = {
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "awaken:" + part["id"],
                         "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2 if i == 0 else 1}}]}],
        }
        cond = [{"type": "minecraft:killed_by_player"}]
        if chance < 1.0:
            cond.append({"type": "minecraft:random_chance", "chance": chance})
        pool["condition"] = cond[0] if len(cond) == 1 else {"type": "minecraft:all_of", "terms": cond}
        pools.append(pool)
    return {"type": "minecraft:entity", "pools": pools, "random_sequence": "awaken:entities/" + m["id"]}


def item_model(name):
    write_json(os.path.join(RES, "assets/awaken/items", name + ".json"), {"model": {"type": "minecraft:model", "model": "awaken:item/" + name}})
    write_json(os.path.join(RES, "assets/awaken/models/item", name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "awaken:item/" + name}})


SOUNDS = {
    "mobs.telegraph": [("minecraft:block/respawn_anchor/charge1", 1.4, 0.6), ("minecraft:block/respawn_anchor/charge2", 1.4, 0.6)],
    "mobs.telegraph_heavy": [("minecraft:mob/warden/sonic_charge1", 1.1, 0.9), ("minecraft:mob/warden/sonic_charge2", 1.1, 0.9)],
    "mobs.boss_intro": [("minecraft:block/bell/resonate", 0.6, 1.0)],
    "mobs.boss_phase": [("minecraft:mob/ravager/roar1", 0.7, 1.0), ("minecraft:mob/ravager/roar2", 0.7, 1.0)],
    "mobs.boss_toll": [("minecraft:block/bell/bell_use01", 0.5, 1.0), ("minecraft:block/bell/bell_use02", 0.5, 1.0)],
    "mobs.boss_defeated": [("minecraft:block/beacon/deactivate", 0.6, 1.0)],
    "mobs.rise": [("minecraft:block/sculk_shrieker/shriek1", 1.6, 0.6), ("minecraft:block/sculk_shrieker/shriek2", 1.6, 0.6)],
    "mobs.cord_bite": [("minecraft:block/chain/break1", 0.7, 0.9), ("minecraft:block/chain/break2", 0.7, 0.9)],
    "mobs.listener_hear": [("minecraft:mob/warden/listening_1", 1.3, 0.8), ("minecraft:mob/warden/listening_2", 1.3, 0.8)],
}
SUBTITLES = {
    "mobs.telegraph": "Something gathers itself",
    "mobs.telegraph_heavy": "A heavy attack winds up",
    "mobs.boss_intro": "A great bell resonates",
    "mobs.boss_phase": "A boss roars",
    "mobs.boss_toll": "A toll rings out",
    "mobs.boss_defeated": "A boss falls",
    "mobs.rise": "The dead get up",
    "mobs.cord_bite": "Something bites the cord",
    "mobs.listener_hear": "A Listener hears you",
}

MESSAGES = {
    "awaken.mobs.pickpocket.stole": "A pickpocket snatched your essence shards! Catch it.",
    "awaken.mobs.pickpocket.empty": "A pickpocket rummaged through your pockets and found nothing.",
    "awaken.mobs.cord_bitten": "Something is chewing on your cord.",
    "awaken.mobs.broker.ask": "The Broker steps back. Sneak and ask if you want a duel.",
    "awaken.mobs.broker.duel_title": "A duel with the Lantern Broker",
    "awaken.mobs.broker.duel_body": "Lanterns down, one challenger, no help. Win and the market's Lord is yours to claim.",
    "awaken.mobs.broker.duel_stakes": "Stakes",
    "awaken.mobs.broker.duel_stakes_value": "Lord Heart, parts, a chance at a Power Core",
    "awaken.mobs.broker.duel_footer": "Y to accept · N to decline",
    "awaken.mobs.broker.duel_chat": "The Lantern Broker offers a duel. Press Y, or type /awaken mobs duel within 30 seconds.",
    "awaken.mobs.broker.no_offer": "No duel is on offer.",
    "awaken.mobs.broker.duel_over": "The duel is over. The Broker relights its lanterns.",
    "entity.awaken.mob_bolt": "Creature Bolt",
}


def main():
    ms = B.mobs()
    th = B.themes()
    lang = dict(MESSAGES)
    for m in ms:
        write_json(os.path.join(RES, "data/awaken/loot_table/entities", m["id"] + ".json"), loot_table(m))
        egg = m["id"] + "_spawn_egg"
        item_model(egg)
        lang["entity.awaken." + m["id"]] = m["name"]
        lang["item.awaken." + egg] = m["name"] + " Spawn Egg"
        for part in m["parts"]:
            item_model(part["id"])
            lang["item.awaken." + part["id"]] = part["name"]
    for k, v in SUBTITLES.items():
        lang["subtitles.awaken." + k] = v
    write_json(os.path.join(MERGE, "lang/en_us.json"), dict(sorted(lang.items())))
    sounds = {k: {"subtitle": "subtitles.awaken." + k, "sounds": [{"name": n, "pitch": p, "volume": v} for n, p, v in vals]}
              for k, vals in SOUNDS.items()}
    write_json(os.path.join(MERGE, "sounds.json"), sounds)
    write_doc(ms, th)
    print(f"mobs: {len(ms)} creatures, {sum(len(m['parts']) for m in ms)} parts")


TIER_LABEL = {"NORMAL": "normal", "ELITE": "elite", "WARDEN": "Warden", "LORD": "Gate Lord", "GUARDIAN": "stratum guardian",
              "SOVEREIGN": "Sovereign", "FINAL": "final boss", "NIGHTMARE": "nightmare"}


def write_doc(ms, th):
    """Rewrites the generated bestiary section of docs/modules/mobs.md (between the markers)."""
    start, end = "<!-- bestiary:start -->", "<!-- bestiary:end -->"
    lines = [start, ""]
    order = list(th.keys())
    for tname in order:
        group = [m for m in ms if m["theme"] == tname]
        if not group:
            continue
        t = th[tname]
        theme_id = t["id"] if tname != "FEAR" else "awaken:nightmare/<family>"
        lines.append(f"### {t['display']} (`{theme_id}`)")
        lines.append("")
        lines.append("| id | name | role | model | signature | abilities |")
        lines.append("|---|---|---|---|---|---|")
        for m in group:
            abil = ", ".join(dict.fromkeys(m["abilities"])) or "(passive)"
            role = TIER_LABEL[m["tier"]]
            if m["parts"]:
                role += "; parts: " + ", ".join(f"`awaken:{p['id']}`" for p in m["parts"])
            lines.append(f"| `awaken:{m['id']}` | {m['name']} | {role} | {m['model'].lower()} | {m['sig']} | {abil} |")
        lines.append("")
    lines.append(end)
    block = "\n".join(lines)
    text = open(DOC).read() if os.path.exists(DOC) else start + "\n" + end + "\n"
    if start in text and end in text:
        text = text[:text.index(start)] + block + text[text.index(end) + len(end):]
    else:
        text = text + "\n" + block + "\n"
    os.makedirs(os.path.dirname(DOC), exist_ok=True)
    with open(DOC, "w") as f:
        f.write(text)


if __name__ == "__main__":
    main()
