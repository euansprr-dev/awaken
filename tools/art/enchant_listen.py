"""Enchanting's listening page (SPEC §45.18; Euan 2026-11-09: sounds start from Minecraft's own recordings named in
sounds.json, auditioned locally in context; 2026-11-11: no high ding at a sound's peak).

Ten of enchanting's beats, each in a small scene at the game's own timing: today's sound beside three picks, every pick a
recipe of Minecraft's recordings named the way sounds.json and the code would name them. Euan marks one for each row; the
marks land in run-autopilot/enchant-listen/picks.json. It is the weapons' page (tools/art/weapon_listen.py) with other
rows: the same player, the same rule that nothing of Mojang's is copied, committed or hosted (it plays the recordings
from this machine's asset cache, on 127.0.0.1 only, out of the gitignored run-autopilot/).

    python3 tools/art/enchant_listen.py build          writes the page; fails if a named recording doesn't exist
    python3 tools/art/enchant_listen.py serve [port]   builds, then serves it (default 8767): open http://127.0.0.1:8767
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import weapon_listen as WL  # noqa: E402

v = WL.v
S = WL.SEMITONE
SCRAPE, BRUSH = v("item.axe.scrape", 0.14, 1.35), v("item.brush.brushing.generic", 0.2, 1.3)

WL.OUT = os.path.join(WL.ROOT, "run-autopilot/enchant-listen")
WL.PAGE = (WL.PAGE.replace("Weapon sounds, by ear", "Enchanting sounds, by ear").replace("Twenty-three sounds.", "Ten of enchanting's sounds.")
           .replace("In a fight", "In its moment"))
WL.SCENES = {
    "alone": [(0, "X")],
    "page": [(0, v("entity.generic.death", 0.25, 0.7)), (600, "X")],
    "read": [(0, "X")],
    # four cuts of a glyph: the burin's grains under the hand, then the stroke seals, a step higher each time
    "strokes": [(0, SCRAPE), (110, SCRAPE), (220, SCRAPE), (330, SCRAPE), (440, "X"),
                (900, SCRAPE), (1010, SCRAPE), (1120, SCRAPE), (1230, "X+2"),
                (1700, SCRAPE), (1810, SCRAPE), (1920, SCRAPE), (2030, "X+4"),
                (2500, SCRAPE), (2610, SCRAPE), (2720, SCRAPE), (2830, "X+5")],
    "cut": [(0, "X"), (110, "X+1"), (220, "X"), (330, "X+2"), (440, "X+1"), (550, "X+3"), (660, "X+2"), (770, "X+4")],
    "wake": [(0, SCRAPE), (110, SCRAPE), (220, SCRAPE), (330, v("block.end_portal_frame.fill", 0.7, 0.9 * S ** 5)), (650, "X")],
    "ward": [(0, v("entity.blaze.shoot", 0.5)), (350, v("entity.player.hurt", 0.7)), (350, "X")],
    "bane": [(0, v("entity.player.attack.strong", 0.8)), (0, v("entity.zombie.hurt", 0.6)), (0, "X"),
             (600, v("entity.player.attack.strong", 0.8)), (600, v("entity.zombie.hurt", 0.6)), (600, "X")],
    "drop": [(0, v("entity.cow.death", 0.5)), (300, v("entity.item.pickup", 0.3)), (300, "X")],
    "dark": [(0, v("block.stone.step", 0.5)), (350, v("block.stone.step", 0.5)), (700, "X"), (1050, v("block.stone.step", 0.5))],
}
WL.SCENE_SAYS = {
    "alone": "the sound by itself",
    "page": "a Lord falls, then its page reaches your hand",
    "read": "you use the page: its glyph lifts off the paper and runs into your hand",
    "strokes": "four cuts of a glyph, the burin's grains under your hand, each cut sealing a step higher than the last",
    "cut": "the burin's grains as your hand runs a stroke, about nine a second",
    "wake": "the last cut seals, then the piece wakes",
    "ward": "a blaze's fire reaches you, with Minecraft's own hurt sound, and the ward takes its share",
    "bane": "two blows on a zombie with Minecraft's own attack and its grunt, the bane biting on each",
    "drop": "a cow falls, and the extra hide comes with a glint",
    "dark": "steps on stone, and the Lantern catches as you walk into the dark",
}
WL.ROWS = [
    dict(id="page", name="A page reaches your hand", scene="page", why="new: today nothing plays", today=[], picks=[
        ("A page turning, with a soft pickup just after", [v("item.book.page_turn", 0.9), v("entity.item.pickup", 0.35, 0.8, 120)]),
        ("A book set on a lectern", [v("item.book.put", 0.9)]),
        ("A map taken from the cartography table", [v("ui.cartography_table.take_result", 0.9)])]),
    dict(id="learn", name="Reading a page", scene="read", why="today it is the vanilla enchanting table's own sound", today=[v("block.enchantment_table.use", 0.6, 1.2)], picks=[
        ("A page turning, then a respawn anchor taking a charge, low", [v("item.book.page_turn", 0.9), v("block.respawn_anchor.charge", 0.5, 0.8, 250)]),
        ("A page turning, then a conduit waking", [v("item.book.page_turn", 0.9), v("block.conduit.activate", 0.45, 1.1, 250)]),
        ("A page turning, then a lodestone compass locking, low", [v("item.book.page_turn", 0.9), v("item.lodestone_compass.lock", 0.7, 0.8, 300)])]),
    dict(id="cut", name="The cut under your hand", scene="cut", why="today a one-shot (an axe's wax-off) is played as a loop", today=[v("item.axe.wax_off", 0.19, 1.3)], picks=[
        ("Grains of an axe scraping", [v("item.axe.scrape", 0.14, 1.35)]),
        ("Grains of a brush", [v("item.brush.brushing.generic", 0.22, 1.3)]),
        ("Grains of a grindstone, quiet and high", [v("block.grindstone.use", 0.1, 1.6)])]),
    dict(id="seal", name="A stroke sealed", scene="strokes", why="today an amethyst chime pitched up a step each stroke", today=[v("block.amethyst_block.resonate", 0.33, 0.59)], picks=[
        ("An eye set into an End portal frame", [v("block.end_portal_frame.fill", 0.7, 0.9)]),
        ("A key turned in a vault", [v("block.vault.insert_item", 0.8, 1.0)]),
        ("A lodestone compass locking", [v("item.lodestone_compass.lock", 0.7, 1.0)])]),
    dict(id="wake", name="The piece wakes", scene="wake", why="today it is the vanilla enchanting table's own sound", today=[v("block.enchantment_table.use", 0.8, 1.0)], picks=[
        ("A respawn anchor set, with a bell's long resonance low under it", [v("block.respawn_anchor.set_spawn", 0.7), v("block.bell.resonate", 0.35, 0.6)]),
        ("A beacon waking, with the bell low under it", [v("block.beacon.activate", 0.6, 1.1), v("block.bell.resonate", 0.3, 0.6)]),
        ("A conduit waking, with amethyst resonating low under it", [v("block.conduit.activate", 0.6), v("block.amethyst_block.resonate", 0.4, 0.5)])]),
    dict(id="ward", name="A ward takes a blow", scene="ward", why="today amethyst resonating", today=[v("block.amethyst_block.resonate", 0.4, 0.8)], picks=[
        ("A shield's block, low, with a beacon powering down", [v("item.shield.block", 0.5, 0.8), v("block.beacon.deactivate", 0.4, 1.2)]),
        ("A respawn anchor giving up a charge", [v("block.respawn_anchor.deplete", 0.5, 1.2)]),
        ("A shield's block, lower, with amethyst resonating low", [v("item.shield.block", 0.45, 0.7), v("block.amethyst_block.resonate", 0.3, 0.5)])]),
    dict(id="bane", name="A bane bites", scene="bane", why="today an amethyst hit pitched up to 1.8: the one most at risk of a ding", today=[v("block.amethyst_block.hit", 0.27, 1.82)], picks=[
        ("A trident going in", [v("item.trident.hit", 0.5, 0.9)]),
        ("Minecraft's critical hit, lowered", [v("entity.player.attack.crit", 0.5, 0.8)]),
        ("A sculk shrieker breaking", [v("block.sculk_shrieker.break", 0.4, 1.2)])]),
    dict(id="spoils", name="Gold on an extra drop", scene="drop", why="today an amethyst chime pitched up to 1.7", today=[v("block.amethyst_block.chime", 0.3, 1.68)], picks=[
        ("Gold armour put on", [v("item.armor.equip_gold", 0.5, 1.1)]),
        ("Something slipped into a bundle", [v("item.bundle.insert", 0.7)]),
        ("A chain set down, a little high", [v("block.chain.place", 0.4, 1.3)])]),
    dict(id="lantern", name="Lantern catches in the dark", scene="dark", why="new", today=[], picks=[
        ("A lantern set down, quiet", [v("block.lantern.place", 0.35, 1.25)]),
        ("A flint and steel struck, low", [v("item.flintandsteel.use", 0.3, 0.8)]),
        ("A fire charge lit, quiet and high", [v("item.firecharge.use", 0.2, 1.4)])]),
    dict(id="scrape", name="Scraping an enchantment off", scene="alone", why="check only: today a grindstone", today=[v("block.grindstone.use", 0.7)], picks=[
        ("An axe scraping, low, then grit falling", [v("item.axe.scrape", 0.6, 0.8), v("block.sand.break", 0.4, 1.0, 200)]),
        ("A brush, slow", [v("item.brush.brushing.generic", 0.6, 0.7)]),
        ("The grindstone, lower", [v("block.grindstone.use", 0.6, 0.8)])]),
]

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "serve":
        WL.serve(int(sys.argv[2]) if len(sys.argv) > 2 else 8767)
    elif len(sys.argv) > 1 and sys.argv[1] == "build":
        WL.build()
    else:
        raise SystemExit(__doc__)
