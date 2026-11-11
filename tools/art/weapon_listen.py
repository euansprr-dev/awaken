"""The weapon sounds' listening page (SPEC §25 "Weight in the Hand", stage 0; Euan 2026-11-09: "built sounds read fake",
start from Minecraft's own recordings named in sounds.json, audition locally in context).

Each of the fourteen weapon sounds that failed the audit, the five new draws and the clash (a light blow turned aside, a
heavy one, the one that breaks a stance, and on a bell), in a scene at the game's own timing:
today's sound beside three picks, every pick a recipe of recordings named the way sounds.json and the code would name them.
Euan marks one for each row; the marks land in run-autopilot/weapon-listen/picks.json.

It plays Mojang's recordings straight from this machine's asset cache, so nothing of theirs is copied, committed or hosted:
the page lives in the gitignored run-autopilot/ and is served on 127.0.0.1 only.

    python3 tools/art/weapon_listen.py build          writes the page; fails if a named recording doesn't exist
    python3 tools/art/weapon_listen.py serve [port]   builds, then serves it (default 8766): open http://127.0.0.1:8766
"""
import glob
import http.server
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
OUT = os.path.join(ROOT, "run-autopilot/weapon-listen")
ASSETS = os.environ.get("MC_ASSETS", os.path.join(os.environ.get("GRADLE_USER_HOME", "/Volumes/MEDIA/.gradle-home"),
                                                  "caches/neoformruntime/assets"))
OURS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
SEMITONE = 1.0594631


def v(event, vol=1.0, pitch=1.0, at=0):
    """One of Minecraft's sound events, as sounds.json would name it, at a volume and pitch, `at` ms into the sound."""
    return {"e": "minecraft:" + event, "vol": vol, "pitch": pitch, "at": at}


def a(event, vol=1.0, pitch=1.0, at=0):
    """One of the weapon kit's own events as it plays today."""
    return {"e": "awaken:arms." + event, "vol": vol, "pitch": pitch, "at": at}


def o(event, vol=1.0, pitch=1.0, at=0):
    """One of our events that names Minecraft's recordings at its own level and pitch (guard.struck, fight.bell...)."""
    return {"e": "awaken:" + event, "vol": vol, "pitch": pitch, "at": at}


STRONG, CRIT, KNOCK, SWEEP = "entity.player.attack.strong", "entity.player.attack.crit", "entity.player.attack.knockback", "entity.player.attack.sweep"
ZOMBIE = v("entity.zombie.hurt", 0.6)

# a scene: (ms, what) with "X" for the row's own sound and "X+n" for it n semitones up; the rest is what the game plays round it
SCENES = {
    "chain": [(0, a("swish_light")), (100, "X"), (100, ZOMBIE), (300, a("swish_light", pitch=SEMITONE)), (400, "X"), (400, ZOMBIE),
              (600, a("swish_light", pitch=SEMITONE ** 2)), (700, "X"), (700, ZOMBIE)],
    "chain_plain": [(0, a("swish_light")), (100, "X"), (300, a("swish_light", pitch=SEMITONE)), (400, "X"),
                    (600, a("swish_light", pitch=SEMITONE ** 2)), (700, "X")],
    "heavy": [(0, a("swish_heavy")), (150, "X"), (150, ZOMBIE)],
    "hurt": [(0, a("tell_light")), (250, "X"), (250, v("entity.player.hurt"))],
    "parry": [(0, a("tell_light")), (250, "X"), (600, a("swish_thrust")), (650, a("hit_flesh_heavy"))],
    "glance": [(0, v("entity.arrow.shoot", 0.6)), (350, "X")],
    "charge": [(0, "X"), (500, a("glint")), (750, a("swish_heavy")), (825, a("hit_flesh_heavy"))],
    "glint": [(0, a("charge")), (500, "X"), (750, a("swish_heavy")), (825, a("hit_flesh_heavy"))],
    "kill": [(0, a("swish_light")), (100, a("hit_flesh")), (100, "X"), (100, v("entity.zombie.death", 0.7))],
    "cleave": [(0, a("swish_heavy")), (150, a("hit_flesh")), (150, ZOMBIE), (150, "X+1"), (150, "X+2")],
    "execution": [(0, a("swish_heavy")), (100, "X")],
    "dodge": [(0, a("tell_heavy")), (450, a("dodge")), (500, "X")],
    "draw": [(0, "X"), (500, a("swish_light")), (600, a("hit_flesh"))],
    # the clash (SPEC §25 "The clash"): the boss's own whoosh (a heavy swish, lowered: a bigger arm) at the fight's rhythm
    "clash_light": [(0, a("tell_light")), (330, a("swish_heavy", 0.8, 0.75)), (400, "X"), (900, a("swish_heavy", 0.8, 0.8)), (970, "X")],
    "clash_heavy": [(0, a("tell_heavy")), (520, a("swish_heavy", 1.0, 0.65)), (620, "X")],
    "clash_break": [(0, a("tell_heavy")), (520, a("swish_heavy", 1.0, 0.65)), (620, "X"), (700, v("entity.ravager.stunned", 0.9))],
}
SCENE_SAYS = {
    "chain": "three strikes of a chain, each swish then the hit with a zombie's own grunt under it",
    "chain_plain": "three strikes of a chain, each swish then the hit",
    "heavy": "a heavy swish, then the hit with a zombie's grunt",
    "hurt": "a creature's tell, then its blow lands with Minecraft's own hurt sound",
    "parry": "a creature's tell, the parry, then your riposte",
    "glance": "a bow, then the arrow off your guard",
    "charge": "the draw back, the full-charge sound, the heavy and its hit",
    "glint": "the draw back, the full-charge sound, the heavy and its hit",
    "kill": "a swish, the hit, the kill over it and the zombie's death",
    "cleave": "a heavy swish, the hit, and two more bodies, each a semitone up",
    "execution": "a heavy swish, then the Execution",
    "dodge": "a heavy tell, your dodge, then the perfect dodge's own sound",
    "draw": "the draw, then a first strike",
    "clash_light": "a boss's tell, then two of its blows turned aside on the beat, each its whoosh then the clash",
    "clash_heavy": "a boss's heavy tell, its whoosh, the clash",
    "clash_break": "a heavy tell, its whoosh, the clash that breaks its stance, then its voice (in the game, the body's own)",
}

ROWS = [
    dict(id="hit_flesh", name="A light hit on flesh", scene="chain", why="84% of today's sits below 300 Hz", today=[a("hit_flesh")], picks=[
        ("Minecraft's strong attack, over today's low thump at under half", [v(STRONG), a("hit_flesh", 0.45)]),
        ("Minecraft's critical hit, a little higher, over the thump", [v(CRIT, 0.8, 1.1), a("hit_flesh", 0.45)]),
        ("Minecraft's knockback hit with its strong attack under it", [v(KNOCK, 0.9), v(STRONG, 0.5, 1.15)])]),
    dict(id="hit_flesh_heavy", name="A heavy hit on flesh (finishers, heavies, ripostes)", scene="heavy", why="96% below 300 Hz", today=[a("hit_flesh_heavy")], picks=[
        ("The critical hit and the knockback hit, both lowered, over today's thump", [v(CRIT, 1.0, 0.85), v(KNOCK, 0.7, 0.8), a("hit_flesh_heavy", 0.5)]),
        ("The strong attack pitched well down, with the knockback hit", [v(STRONG, 1.0, 0.7), v(KNOCK, 0.8, 0.9)]),
        ("A trident going in, lowered, over today's thump", [v("item.trident.hit", 0.9, 0.8), a("hit_flesh_heavy", 0.5)])]),
    dict(id="hit_blunt", name="The Maul's blunt hit", scene="heavy", why="98% below 300 Hz", today=[a("hit_blunt")], picks=[
        ("The mace's own blow, over today's thump", [v("item.mace.smash_air", 0.8), a("hit_blunt", 0.5)]),
        ("A heavy core set down, lowered, with the knockback hit", [v("block.heavy_core.place", 1.0, 0.8), v(KNOCK, 0.8, 0.7)]),
        ("A goat's ram landing, lowered, over today's thump", [v("entity.goat.ram_impact", 1.0, 0.8), a("hit_blunt", 0.5)])]),
    dict(id="hit_armor", name="A hit on armour", scene="chain_plain", why="80% below 300 Hz; three of its four files carry the thin tone", today=[a("hit_armor")], picks=[
        ("An iron golem struck, raised, with the strong attack", [v("entity.iron_golem.hurt", 0.9, 1.2), v(STRONG, 0.6)]),
        ("A shield block, raised, with the strong attack", [v("item.shield.block", 0.8, 1.3), v(STRONG, 0.6)]),
        ("A zombie on an iron door, raised, with the strong attack", [v("entity.zombie.attack_iron_door", 0.7, 1.2), v(STRONG, 0.5)])]),
    dict(id="hit_spirit", name="A hit on a spirit", scene="chain_plain", why="all three files carry the thin tone", today=[a("hit_spirit")], picks=[
        ("A breeze hurt, with a sweep of air", [v("entity.breeze.hurt", 0.8), v(SWEEP, 0.6, 1.2)]),
        ("A phantom hurt, with a sweep of air", [v("entity.phantom.hurt", 0.8, 1.1), v(SWEEP, 0.5, 1.3)]),
        ("A vex hurt, lowered, over a soft cloth thud", [v("entity.vex.hurt", 0.7, 0.9), v("block.wool.hit", 0.8, 0.8)])]),
    dict(id="hurt", name="A blow landing on you", scene="hurt", why="96% below 300 Hz", today=[a("hurt")], picks=[
        ("The knockback hit, lowered, over today's thump", [v(KNOCK, 0.9, 0.8), a("hurt", 0.5)]),
        ("An iron golem's throw, raised and quiet, over today's thump", [v("entity.iron_golem.attack", 0.6, 1.3), a("hurt", 0.5)]),
        ("The strong attack, lowered, over a cloth thud", [v(STRONG, 0.9, 0.75), v("block.wool.hit", 0.8, 0.7)])]),
    dict(id="deflect", name="The parry", scene="parry", why="all four files carry the thin tone", today=[a("deflect")], picks=[
        ("An anvil landing, quiet and raised, over a shield block", [v("block.anvil.land", 0.45, 1.6), v("item.shield.block", 1.0, 1.1)]),
        ("A zombie on an iron door, raised, over a shield block", [v("entity.zombie.attack_iron_door", 0.9, 1.4), v("item.shield.block", 0.8, 1.2)]),
        ("A chain breaking, over an iron golem struck and raised", [v("block.chain.break", 1.0, 0.9), v("entity.iron_golem.hurt", 0.7, 1.5)])]),
    # the clash (SPEC §25 "The clash"; Euan 2026-11-11 of the parry above: "a very small ding, and the screen barely moves"):
    # each pick is our steel by the blow's size over what it met (a sword here, a bell on the last row); no layer above 1.2
    dict(id="clash_light", name="The clash: a light blow turned aside", scene="clash_light", why="today's parry is an anvil at 1.6 over a shield: a small ding",
         today=[v("block.anvil.land", 0.45, 1.6), o("guard.struck", 1.0, 1.1)], picks=[
        ("A trident striking the ground and an anvil landing, over the shield and an iron golem struck (in the game now)",
         [v("item.trident.hit_ground", 1.0, 1.0), v("block.anvil.land", 0.8, 1.15), o("guard.struck", 0.8, 1.0), o("guard.steel", 1.0, 1.0)]),
        ("The mace through the air, an iron golem cracking, over the shield",
         [v("item.mace.smash_air", 0.8, 1.0), v("entity.iron_golem.damage", 0.8, 1.1), o("guard.struck", 0.8, 1.0)]),
        ("A chain and copper struck, an anvil landing, over the shield",
         [v("block.chain.hit", 1.0, 1.0), v("block.copper.hit", 0.9, 1.1), v("block.anvil.land", 0.7, 1.2), o("guard.struck", 0.7, 1.0)])]),
    dict(id="clash_heavy", name="The clash: a heavy blow turned aside", scene="clash_heavy", why="new: today a heavy's parry sounds like a light's",
         today=[v("block.anvil.land", 0.45, 1.6), o("guard.struck", 1.0, 1.1)], picks=[
        ("An anvil landing low and the mace on the ground, over the shield and an iron golem struck (in the game now)",
         [v("block.anvil.land", 1.0, 0.9), v("item.mace.smash_ground", 0.8, 1.0), o("guard.struck", 0.9, 0.95), o("guard.steel", 1.0, 0.9)]),
        ("A heavy core struck, an iron golem cracking and a trident in the ground, all lowered",
         [v("block.heavy_core.hit", 1.0, 0.9), v("entity.iron_golem.damage", 0.9, 0.9), v("item.trident.hit_ground", 0.9, 0.9), o("guard.struck", 0.9, 0.9)]),
        ("Netherite struck, the mace through the air and a chain breaking, lowered",
         [v("block.netherite_block.hit", 1.0, 0.8), v("item.mace.smash_air", 0.9, 0.85), v("block.chain.break", 0.8, 0.9), o("guard.struck", 0.9, 0.9)])]),
    dict(id="clash_break", name="The clash that breaks its stance", scene="clash_break", why="new: the biggest parry, with the body's own voice after it",
         today=[v("block.anvil.land", 0.45, 1.6), o("guard.struck", 1.0, 1.1)], picks=[
        ("An anvil landing lower and the mace's heaviest ground blow, over the shield and an iron golem struck (in the game now)",
         [v("block.anvil.land", 1.0, 0.8), v("item.mace.smash_ground_heavy", 0.9, 1.0), o("guard.struck", 0.9, 0.85), o("guard.steel", 1.0, 0.85)]),
        ("The mace's heaviest blow, an iron golem cracking and a heavy core struck, all low",
         [v("item.mace.smash_ground_heavy", 0.9, 0.9), v("entity.iron_golem.damage", 1.0, 0.8), v("block.heavy_core.hit", 1.0, 0.8), o("guard.struck", 0.9, 0.85)]),
        ("An anvil landing lowest, a chain breaking and a vault struck",
         [v("block.anvil.land", 1.0, 0.75), v("block.chain.break", 1.0, 0.8), v("block.vault.hit", 0.9, 0.8), o("guard.struck", 0.9, 0.8)])]),
    dict(id="clash_bell", name="The clash on a bell (the Ringer, the Bell Warden, the Toller)", scene="clash_light", why="new: what met the blade was a bell",
         today=[v("block.anvil.land", 0.45, 1.6), o("guard.struck", 1.0, 1.1)], picks=[
        ("The light clash with a bell struck over it (in the game now)",
         [v("item.trident.hit_ground", 1.0, 1.0), v("block.anvil.land", 0.8, 1.15), o("guard.struck", 0.8, 1.0), o("fight.bell", 1.0, 1.0)]),
        ("A trident in the ground and the shield, with the bell lower",
         [v("item.trident.hit_ground", 1.0, 1.0), o("guard.struck", 0.8, 1.0), o("fight.bell", 1.0, 0.85)]),
        ("An anvil landing and a chain, with the bell quieter and a little higher",
         [v("block.anvil.land", 0.9, 1.0), v("block.chain.hit", 0.9, 1.0), o("guard.struck", 0.8, 1.0), o("fight.bell", 0.7, 1.1)])]),
    dict(id="glance", name="A shot glancing off the guard", scene="glance", why="all four files carry the thin tone", today=[a("glance")], picks=[
        ("A breeze turning a shot away", [v("entity.breeze.deflect", 0.9)]),
        ("A shield block, raised, with an arrow striking", [v("item.shield.block", 0.8, 1.6), v("entity.arrow.hit", 0.6, 1.2)]),
        ("A trident striking the ground, raised", [v("item.trident.hit_ground", 0.8, 1.5)])]),
    dict(id="charge", name="Drawing back for a heavy", scene="charge", why="both files carry the thin tone", today=[a("charge")], picks=[
        ("A crossbow being drawn, lowered", [v("item.crossbow.loading_middle", 0.8, 0.8)]),
        ("Leather tightening, lowered, then the crossbow's first pull", [v("item.armor.equip_leather", 0.9, 0.7), v("item.crossbow.loading_start", 0.5, 0.8, 120)]),
        ("A fishing line reeled in, lowered", [v("entity.fishing_bobber.retrieve", 0.8, 0.6)])]),
    dict(id="glint", name="Full charge (the yellow ting)", scene="glint", why="both files carry the thin tone", today=[a("glint")], picks=[
        ("A crossbow locking, raised: a wooden click", [v("item.crossbow.loading_end", 0.9, 1.2)]),
        ("Chain mail settling, raised", [v("item.armor.equip_chain", 0.9, 1.3)]),
        ("An iron trapdoor shutting, raised", [v("block.iron_trapdoor.close", 0.7, 1.5)])]),
    dict(id="kill", name="The kill, over its hit", scene="kill", why="93% below 300 Hz", today=[a("kill")], picks=[
        ("The critical hit, lowered, over today's body drop", [v(CRIT, 1.0, 0.8), a("kill", 0.5)]),
        ("The knockback hit, lowered, with an axe biting wood", [v(KNOCK, 1.0, 0.7), v("item.axe.strip", 0.5, 0.8)]),
        ("A hard fall, over today's body drop", [v("entity.generic.big_fall", 0.7, 0.9), a("kill", 0.4)])]),
    dict(id="cleave", name="Each extra body in a sweep", scene="cleave", why="all three files carry the thin tone", today=[a("cleave")], picks=[
        ("Minecraft's own sweep attack", [v(SWEEP)]),
        ("The strong attack, raised", [v(STRONG, 0.8, 1.2)]),
        ("A trident going in, raised", [v("item.trident.hit", 0.7, 1.1)])]),
    dict(id="execution", name="The Execution", scene="execution", why="97% below 300 Hz", today=[a("execution")], picks=[
        ("The mace's heaviest ground blow, with the critical hit lowered", [v("item.mace.smash_ground_heavy", 0.9), v(CRIT, 0.9, 0.7)]),
        ("The mace's ground blow, with the knockback hit lowered", [v("item.mace.smash_ground", 0.9, 0.9), v(KNOCK, 0.9, 0.7)]),
        ("An iron golem cracking, with the critical hit, over today's", [v("entity.iron_golem.damage", 0.9, 0.7), v(CRIT, 0.9, 0.75), a("execution", 0.5)])]),
    dict(id="perfect_dodge", name="The perfect dodge", scene="dodge", why="both files carry the thin tone", today=[a("perfect_dodge")], picks=[
        ("A dragon's wingbeat, quiet and raised: a rush of air", [v("entity.ender_dragon.flap", 0.6, 1.7)]),
        ("A trident's riptide, quiet and raised", [v("item.trident.riptide_1", 0.5, 1.5)]),
        ("A phantom's swoop, raised", [v("entity.phantom.swoop", 0.6, 1.4)])]),
    dict(id="draw_blade", name="Drawing a blade (Longsword, Katana, Greatblade, Rapier, Daggers, Twin Blades, Shieldblade)", scene="draw", why="new: today a weapon change is silent", today=[], picks=[
        ("Iron armour going on, raised: a short steel ring", [v("item.armor.equip_iron", 0.8, 1.3)]),
        ("Netherite armour going on, raised", [v("item.armor.equip_netherite", 0.8, 1.2)]),
        ("Chain mail, with a touch of the grindstone", [v("item.armor.equip_chain", 0.7), v("block.grindstone.use", 0.25, 1.6)])]),
    dict(id="draw_haft", name="Drawing a hafted weapon (Axe, Maul, Spear, Glaive, Scythe, War-staff)", scene="draw", why="new", today=[], picks=[
        ("An axe biting wood, lowered", [v("item.axe.strip", 0.8, 0.8)]),
        ("Wood knocked, lowered, then leather", [v("block.wood.hit", 0.9, 0.8), v("item.armor.equip_leather", 0.6, 0.9, 60)]),
        ("A wooden spear taken up", [v("item.spear_wood.use", 0.8)])]),
    dict(id="draw_chain", name="Drawing the Chain-blade", scene="draw", why="new", today=[], picks=[
        ("A chain set down", [v("block.chain.place", 0.9)]),
        ("Chain mail going on, lowered", [v("item.armor.equip_chain", 0.9, 0.9)]),
        ("Two links knocking", [v("block.chain.hit", 0.9), v("block.chain.hit", 0.8, 1.1, 90)])]),
    dict(id="draw_gauntlets", name="Putting up the Gauntlets", scene="draw", why="new", today=[], picks=[
        ("Leather armour going on, lowered: a creak", [v("item.armor.equip_leather", 0.9, 0.8)]),
        ("Plain armour going on, lowered", [v("item.armor.equip_generic", 0.9, 0.8)]),
        ("A bundle stuffed, lowered", [v("item.bundle.insert", 0.8, 0.7)])]),
    dict(id="draw_precursor", name="Waking the Precursor Arm", scene="draw", why="new", today=[], picks=[
        ("A conduit waking, quiet and raised", [v("block.conduit.activate", 0.4, 1.8)]),
        ("A beacon waking, quiet and raised", [v("block.beacon.activate", 0.5, 1.6)]),
        ("A sculk sensor clicking, raised", [v("block.sculk_sensor.clicking", 0.8, 1.2)])]),
]


def vanilla():
    idx = max(glob.glob(ASSETS + "/indexes/*.json"), key=lambda p: int(os.path.basename(p)[:-5]) if os.path.basename(p)[:-5].isdigit() else -1)
    objects = json.load(open(idx))["objects"]
    h = objects["minecraft/sounds.json"]["hash"]
    return objects, json.load(open(f"{ASSETS}/objects/{h[:2]}/{h}"))


def manifest():
    """Every event the page names: its recordings as urls with each one's own volume and pitch. Fails on a missing one."""
    objects, vsj = vanilla()
    ours = json.load(open(os.path.join(ROOT, "src/main/merge/arms/assets/awaken/sounds.json")))
    used = set()
    for row in ROWS:
        for lay in row["today"]:
            used.add(lay["e"])
        for _, layers in row["picks"]:
            for lay in layers:
                used.add(lay["e"])
    for scene in SCENES.values():
        for _, what in scene:
            if isinstance(what, dict):
                used.add(what["e"])
    events, files, missing = {}, {}, []
    for e in sorted(used):
        ns, name = e.split(":", 1)
        entry = (vsj if ns == "minecraft" else ours).get(name)
        if entry is None:
            missing.append(e)
            continue
        out = []
        for s in entry["sounds"]:
            s = {"name": s} if isinstance(s, str) else s
            if s.get("type") == "event":
                continue
            n = s["name"]
            if ns == "minecraft" or n.startswith("minecraft:"):  # ours may name Minecraft's recordings directly (guard.struck)
                n = n.split(":", 1)[-1]
                o = objects.get(f"minecraft/sounds/{n}.ogg")
                if o is None:
                    missing.append(e + " -> " + n)
                    continue
                url, path = "/v/" + n + ".ogg", f"{ASSETS}/objects/{o['hash'][:2]}/{o['hash']}"
            else:
                n = n.split(":", 1)[-1]
                url, path = "/a/" + n + ".ogg", os.path.join(OURS, n + ".ogg")
            if not os.path.exists(path):
                missing.append(e + " -> " + path)
                continue
            files[url] = path
            out.append({"url": url, "vol": s.get("volume", 1.0), "pitch": s.get("pitch", 1.0)})
        if not out:
            missing.append(e + " (no recordings)")
        events[e] = out
    if missing:
        raise SystemExit("recordings not found:\n  " + "\n  ".join(missing))
    return events, files


PAGE = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Weapon sounds, by ear</title>
<style>
:root { --bg: #f2f3f5; --panel: #fff; --ink: #15181d; --muted: #596170; --line: #d7dbe2; --soft: #e9ebef; --forge: #a9560f; --ok: #2c7a4b }
@media (prefers-color-scheme: dark) { :root { --bg: #0f1216; --panel: #171b21; --ink: #e7e9ed; --muted: #9aa2af; --line: #2a303a; --soft: #1e232b; --forge: #eda155; --ok: #74c493; color-scheme: dark } }
body { margin: 0; background: var(--bg); color: var(--ink); font: 16px/1.55 system-ui, -apple-system, "Segoe UI", sans-serif; padding: 24px 16px 80px }
main { max-width: 60rem; margin: 0 auto; display: flex; flex-direction: column; gap: 22px }
h1 { font-size: 2rem; margin: 0; letter-spacing: -0.01em } h2 { font-size: 1.1rem; margin: 0 }
p { margin: 0; max-width: 68ch } .muted { color: var(--muted) } .small { font-size: 0.88rem }
.row { background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 16px 18px; display: flex; flex-direction: column; gap: 12px }
.opts { display: grid; grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr)); gap: 10px }
.opt { border: 1px solid var(--line); border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 8px; min-width: 0 }
.opt.on { border-color: var(--forge); box-shadow: inset 0 0 0 1px var(--forge) }
.opt b { font-size: 0.78rem; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted) }
.opt .say { font-size: 0.9rem; flex: 1 }
.btns { display: flex; gap: 6px; flex-wrap: wrap }
button { font: inherit; font-size: 0.86rem; color: var(--ink); background: var(--soft); border: 1px solid var(--line); border-radius: 5px; padding: 6px 10px; cursor: pointer }
button.use[aria-pressed="true"] { background: var(--forge); border-color: var(--forge); color: var(--bg) }
button:focus-visible, input:focus-visible, textarea:focus-visible { outline: 2px solid var(--forge); outline-offset: 2px }
input[type=text], textarea { font: inherit; font-size: 0.9rem; width: 100%; box-sizing: border-box; background: var(--bg); color: var(--ink); border: 1px solid var(--line); border-radius: 5px; padding: 7px 9px }
.bar { position: sticky; top: 0; background: var(--bg); padding: 8px 0; display: flex; gap: 14px; align-items: center; flex-wrap: wrap; border-bottom: 1px solid var(--line); z-index: 2 }
#state { color: var(--ok); font-size: 0.9rem }
label { font-size: 0.9rem }
</style></head><body><main>
<h1>Weapon sounds, by ear</h1>
<p>Twenty-three sounds. For each one: today's, and three picks made from Minecraft's own recordings. <strong>In a fight</strong> plays the sound where the game plays it, with the sounds round it. <strong>Alone</strong> plays just the sound. Press <strong>Use this</strong> on the one you'd keep, or <strong>None of these</strong>, and add a word if something is close but off. Your marks save as you go.</p>
<p class="muted small">Every pick is a guess until you have heard it. The levels are first values. This page plays the recordings from this Mac's own copy of the game; it is not hosted anywhere.</p>
<div class="bar"><label>Volume <input type="range" id="vol" min="0" max="1" step="0.01" value="0.8"></label><button id="stop">Stop</button><span id="state"></span><span class="muted small" id="count"></span></div>
<div id="rows"></div>
<div class="row"><h2>Anything else</h2><textarea id="notes" rows="3" placeholder="Anything about the sounds as a whole"></textarea>
<p class="muted small">If saving ever fails, copy this and paste it to Claude:</p><textarea id="dump" rows="6" readonly></textarea><div class="btns"><button id="copy">Copy my marks</button></div></div>
</main>
<script>
var DATA = /*DATA*/;
(function () {
  "use strict";
  var ctx = null, master = null, cache = {}, live = [], marks = { rows: {}, notes: "" };
  function audio() {
    if (!ctx) { ctx = new (window.AudioContext || window.webkitAudioContext)(); master = ctx.createGain(); master.connect(ctx.destination); master.gain.value = parseFloat(document.getElementById("vol").value); }
    if (ctx.state === "suspended") ctx.resume();
    return ctx;
  }
  function buffer(url) {
    if (!cache[url]) cache[url] = fetch(url).then(function (r) { if (!r.ok) throw new Error(url); return r.arrayBuffer(); }).then(function (b) { return audio().decodeAudioData(b); });
    return cache[url];
  }
  function stop() { live.forEach(function (s) { try { s.stop(); } catch (e) {} }); live = []; }
  // layers: [{e, vol, pitch, at}], each at `ms` into the play; a variant of each event is drawn as the game draws one
  function gather(layers, ms, up, into) {
    layers.forEach(function (l) {
      var vs = DATA.events[l.e], pick = vs[Math.floor(Math.random() * vs.length)];
      into.push({ url: pick.url, vol: l.vol * pick.vol, pitch: l.pitch * pick.pitch * Math.pow(DATA.semitone, up), at: ms + (l.at || 0) });
    });
  }
  function play(plan) {
    stop();
    var c = audio();
    Promise.all(plan.map(function (p) { return buffer(p.url); })).then(function (bufs) {
      var t0 = c.currentTime + 0.06;
      plan.forEach(function (p, i) {
        var s = c.createBufferSource(), g = c.createGain();
        s.buffer = bufs[i]; s.playbackRate.value = p.pitch; g.gain.value = p.vol; s.connect(g); g.connect(master); s.start(t0 + p.at / 1000); live.push(s);
      });
    }).catch(function (e) { say("Couldn't load " + e.message, true); });
  }
  function alone(layers) { var plan = []; gather(layers, 0, 0, plan); play(plan); }
  function scene(row, layers) {
    var plan = [];
    DATA.scenes[row.scene].forEach(function (step) {
      var ms = step[0], what = step[1];
      if (typeof what === "string") gather(layers, ms, what.length > 1 ? parseInt(what.slice(2), 10) : 0, plan);
      else gather([what], ms, 0, plan);
    });
    play(plan);
  }
  function say(text, bad) { var el = document.getElementById("state"); el.textContent = text; el.style.color = bad ? "#c0392b" : ""; }
  function dump() {
    var lines = DATA.rows.map(function (r) { var m = marks.rows[r.id] || {}; return r.id + ": " + (m.pick || "-") + (m.note ? " (" + m.note + ")" : ""); });
    if (marks.notes) lines.push("notes: " + marks.notes);
    document.getElementById("dump").value = lines.join("\n");
    var done = DATA.rows.filter(function (r) { return (marks.rows[r.id] || {}).pick; }).length;
    document.getElementById("count").textContent = done + " of " + DATA.rows.length + " marked";
  }
  var timer = null;
  function save() {
    dump();
    clearTimeout(timer);
    timer = setTimeout(function () {
      marks.at = new Date().toISOString();
      fetch("/picks", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(marks) })
        .then(function (r) { say(r.ok ? "Saved" : "Couldn't save: copy the marks below", !r.ok); })
        .catch(function () { say("Couldn't save: copy the marks below", true); });
    }, 300);
  }
  function build() {
    var host = document.getElementById("rows");
    host.innerHTML = "";
    DATA.rows.forEach(function (r) {
      var m = marks.rows[r.id] || (marks.rows[r.id] = {});
      var card = document.createElement("div"); card.className = "row";
      var h = document.createElement("h2"); h.textContent = r.name; card.appendChild(h);
      var p = document.createElement("p"); p.className = "muted small"; p.textContent = "Why it is here: " + r.why + ". In a fight: " + DATA.sceneSays[r.scene] + "."; card.appendChild(p);
      var opts = document.createElement("div"); opts.className = "opts";
      var all = [{ key: "today", title: "Today", say: r.today.length ? "The sound as it is now" : "No sound", layers: r.today }]
        .concat(r.picks.map(function (k, i) { return { key: "pick " + (i + 1), title: "Pick " + (i + 1), say: k.say, layers: k.layers }; }))
        .concat([{ key: "none", title: "None of these", say: "Try again with other recordings", layers: null }]);
      all.forEach(function (o) {
        var box = document.createElement("div"); box.className = "opt" + (m.pick === o.key ? " on" : "");
        var b = document.createElement("b"); b.textContent = o.title; box.appendChild(b);
        var s = document.createElement("div"); s.className = "say"; s.textContent = o.say; box.appendChild(s);
        var btns = document.createElement("div"); btns.className = "btns";
        if (o.layers) {
          var f = document.createElement("button"); f.type = "button"; f.textContent = "In a fight"; f.onclick = function () { scene(r, o.layers); }; btns.appendChild(f);
          if (o.layers.length) { var a = document.createElement("button"); a.type = "button"; a.textContent = "Alone"; a.onclick = function () { alone(o.layers); }; btns.appendChild(a); }
        }
        var u = document.createElement("button"); u.type = "button"; u.className = "use"; u.textContent = o.layers ? "Use this" : "None of these"; u.setAttribute("aria-pressed", m.pick === o.key ? "true" : "false");
        u.onclick = function () { m.pick = m.pick === o.key ? "" : o.key; save(); build(); };
        btns.appendChild(u); box.appendChild(btns); opts.appendChild(box);
      });
      card.appendChild(opts);
      var note = document.createElement("input"); note.type = "text"; note.id = "note-" + r.id; note.placeholder = "A word on this one (optional)"; note.value = m.note || "";
      note.oninput = function () { m.note = note.value; save(); };
      card.appendChild(note);
      host.appendChild(card);
    });
    dump();
  }
  document.getElementById("vol").oninput = function () { if (master) master.gain.value = parseFloat(this.value); };
  document.getElementById("stop").onclick = stop;
  document.getElementById("notes").oninput = function () { marks.notes = this.value; save(); };
  document.getElementById("copy").onclick = function () { var d = document.getElementById("dump"); d.select(); if (navigator.clipboard) navigator.clipboard.writeText(d.value).then(function () { say("Copied"); }, function () {}); };
  fetch("/picks").then(function (r) { return r.ok ? r.json() : null; }).then(function (j) {
    if (j && j.rows) { marks = j; document.getElementById("notes").value = j.notes || ""; }
    build();
  }).catch(build);
})();
</script></body></html>
"""


def build():
    events, files = manifest()
    data = {"semitone": SEMITONE, "events": events, "sceneSays": SCENE_SAYS,
            "scenes": {k: [[ms, what] for ms, what in s] for k, s in SCENES.items()},
            "rows": [{"id": r["id"], "name": r["name"], "scene": r["scene"], "why": r["why"], "today": r["today"],
                      "picks": [{"say": say, "layers": layers} for say, layers in r["picks"]]} for r in ROWS]}
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "index.html"), "w") as f:
        f.write(PAGE.replace("/*DATA*/", json.dumps(data)))
    json.dump(files, open(os.path.join(OUT, "files.json"), "w"))
    print(f"{len(ROWS)} rows, {len(events)} events, {len(files)} recordings -> {os.path.join(OUT, 'index.html')}")
    return files


def serve(port):
    files = build()
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
                self.send(200, "audio/ogg", open(files[path], "rb").read())
            else:
                self.send(404, "text/plain", b"not here")

        def do_POST(self):
            if self.path != "/picks":
                self.send(404, "text/plain", b"not here")
                return
            body = self.rfile.read(min(int(self.headers.get("Content-Length", "0")), 200_000))
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


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "serve":
        serve(int(sys.argv[2]) if len(sys.argv) > 2 else 8766)
    elif len(sys.argv) > 1 and sys.argv[1] == "build":
        build()
    else:
        raise SystemExit(__doc__)
