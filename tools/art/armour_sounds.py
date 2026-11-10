#!/usr/bin/env python3
"""Armour's sounds (SPEC §44.12), every one from Minecraft's own recordings, named the way sounds.json names them
(Euan 2026-11-09: "built sounds read fake": start from the game's own recordings, audition locally in context).

One table, ROWS: each sound as three picks, a pick a recipe of recordings (an event of Minecraft's, a volume, a pitch,
and how many milliseconds in it starts). The first pick is the best guess and what the game plays until Euan has
listened; his marks choose another (tools/art/specs/armour_sound_picks.json, written by `apply`).

    python3 tools/art/armour_sounds.py build          writes armour's sounds.json entries and sound_layers.json
    python3 tools/art/armour_sounds.py check          fails if a recording a pick names does not exist in this game
    python3 tools/art/armour_sounds.py listen [port]  builds the listening page and serves it on 127.0.0.1 (8767)
    python3 tools/art/armour_sounds.py apply          takes his marks from the page into armour_sound_picks.json

A recipe's first recording is the event itself in sounds.json (a reference to Minecraft's event, so it follows the
game's own variants). Its other recordings are layered by the client (assets/awaken/armour/sound_layers.json,
ArmourSoundLayers), because sounds.json cannot play two at once. Nothing of Mojang's is copied: the page plays the
recordings from this machine's own copy of the game, lives in the gitignored run-autopilot/ and is never hosted.
"""
import glob
import http.server
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../.."))
OUT = os.path.join(ROOT, "run-autopilot/armour-listen")
ASSETS = os.environ.get("MC_ASSETS", os.path.join(os.environ.get("GRADLE_USER_HOME", "/Volumes/MEDIA/.gradle-home"),
                                                  "caches/neoformruntime/assets"))
OURS = os.path.join(ROOT, "src/main/resources/assets/awaken/sounds")
SOUNDS_JSON = os.path.join(ROOT, "src/main/merge/armour/assets/awaken/sounds.json")
LAYERS_JSON = os.path.join(ROOT, "src/main/resources/assets/awaken/armour/sound_layers.json")
BEFORE = os.path.join(HERE, "specs/armour_sounds_before.json")  # the 25 events as they were built on 29 Oct ("today" on the page)
PICKS = os.path.join(HERE, "specs/armour_sound_picks.json")     # row id -> 1, 2, 3 or "today": what the game plays
SEMITONE = 1.0594631
FAMILIES = ("hide", "cloth", "mail", "plate", "heavy", "crystal", "bone", "feather")


def v(event, vol=1.0, pitch=1.0, at=0):
    """One of Minecraft's sound events at a volume and pitch, `at` ms into the sound."""
    return {"e": "minecraft:" + event, "vol": vol, "pitch": pitch, "at": at}


def row(id, name, scene, picks, subtitle=None, new=False):
    return dict(id=id, name=name, scene=scene, picks=picks, subtitle=subtitle, new=new)


LAND, PLACE = "block.anvil.land", "block.anvil.place"

ROWS = [
    # ---- the Forge Song: the hammer on each metal ------------------------------------------------------------------
    row("forge.strike_hide", "The mallet on hide (E sets, made by hand)", "strike", [
        ("Leather creaking under a wooden knock", [v("item.armor.equip_leather", 0.9, 0.85), v("block.wood.hit", 0.6, 0.8)]),
        ("A soft cloth thud with leather over it", [v("block.wool.hit", 1.0, 0.7), v("item.armor.equip_leather", 0.6, 1.1)]),
        ("A bundle stuffed, with a wooden knock", [v("item.bundle.insert", 0.9, 0.7), v("block.wood.hit", 0.5, 0.9)])]),
    row("forge.strike_copper", "The hammer on copper (E)", "strike", [
        ("Copper struck, with a quiet anvil ring", [v("block.copper.hit", 1.0, 0.9), v(PLACE, 0.3, 1.3)]),
        ("Copper set down, lowered", [v("block.copper.place", 1.0, 0.85), v(LAND, 0.2, 1.5)]),
        ("A copper footfall under a quiet anvil", [v("block.copper.step", 1.0, 0.8), v(LAND, 0.35, 1.5)])]),
    row("forge.strike_iron", "The hammer on iron (D)", "strike", [
        ("Minecraft's anvil clang, a little higher, over a dull metal knock", [v(LAND, 0.55, 1.2), v("block.netherite_block.hit", 0.6, 1.0)]),
        ("An anvil set down", [v(PLACE, 0.6, 1.1)]),
        ("A zombie on an iron door, with a quiet anvil", [v("entity.zombie.attack_iron_door", 0.7, 1.2), v(LAND, 0.3, 1.4)])]),
    row("forge.strike_diamond", "The hammer on diamond (C)", "strike", [
        ("The anvil clang raised, with amethyst struck", [v(LAND, 0.5, 1.45), v("block.amethyst_block.hit", 0.5, 0.9)]),
        ("An anvil set down, raised, with diamond armour", [v(PLACE, 0.6, 1.4), v("item.armor.equip_diamond", 0.7, 1.1)]),
        ("A trident striking stone, raised, with a quiet anvil", [v("item.trident.hit_ground", 0.7, 1.3), v(LAND, 0.35, 1.5)])]),
    row("forge.strike_deepglass", "The hammer on Deepglass (B)", "strike", [
        ("The anvil clang with an amethyst cluster struck: glass in the ring", [v(LAND, 0.45, 1.3), v("block.amethyst_cluster.hit", 0.8, 0.8)]),
        ("Glass knocked, over an anvil set down", [v("block.glass.hit", 0.9, 0.8), v(PLACE, 0.5, 1.25)]),
        ("An anvil set down with an amethyst chime, lowered", [v(PLACE, 0.5, 1.25), v("block.amethyst_block.chime", 0.6, 0.7)])]),
    row("forge.strike_netherite", "The hammer on netherite (A)", "strike", [
        ("The anvil clang lowered, over netherite struck: heavy", [v(LAND, 0.7, 0.85), v("block.netherite_block.hit", 0.9, 0.8)]),
        ("Netherite set down, lowered, with an anvil", [v("block.netherite_block.place", 1.0, 0.8), v(PLACE, 0.5, 0.9)]),
        ("The mace's blow through air, with the anvil lowered", [v("item.mace.smash_air", 0.7, 0.9), v(LAND, 0.5, 0.8)])]),
    row("forge.strike_starmetal", "The hammer on starmetal (S)", "strike", [
        ("The anvil clang with amethyst resonating under it", [v(LAND, 0.6, 1.0), v("block.amethyst_block.resonate", 0.5, 1.0)]),
        ("The anvil clang with a beacon's click, raised", [v(LAND, 0.6, 1.05), v("block.beacon.power_select", 0.3, 1.4)]),
        ("An anvil set down with a respawn anchor charging, quiet", [v(PLACE, 0.6, 1.0), v("block.respawn_anchor.charge", 0.3, 1.3)])]),
    # ---- the Forge Song: the rest ----------------------------------------------------------------------------------
    row("forge.bellows", "The fire and bellows under a heat", "alone", [
        ("Fire burning, lowered, with a breeze drawing breath", [v("block.fire.ambient", 0.9, 0.8), v("entity.breeze.inhale", 0.35, 0.7)]),
        ("A blast furnace crackling, with a slow wingbeat of air", [v("block.blastfurnace.fire_crackle", 1.0, 0.9), v("entity.phantom.flap", 0.5, 0.6)]),
        ("A furnace crackling, with a horse's breath", [v("block.furnace.fire_crackle", 1.0, 0.8), v("entity.horse.breathe", 0.6, 0.7)])]),
    row("forge.pull", "The bar pulled from the fire", "pull", [
        ("A bucket filled with lava, raised", [v("item.bucket.fill_lava", 0.7, 1.2)]),
        ("A chain set down, with a short hiss", [v("block.chain.place", 0.8, 0.8), v("block.fire.extinguish", 0.25, 1.4)]),
        ("Lava popping, with a touch of the grindstone", [v("block.lava.pop", 0.8, 1.0), v("block.grindstone.use", 0.3, 1.3)])]),
    row("forge.quench", "The quench", "alone", [
        ("Lava meeting water", [v("block.lava.extinguish", 0.9, 1.0)]),
        ("Fire put out, lowered, with a bucket emptied", [v("block.fire.extinguish", 0.9, 0.8), v("item.bucket.empty", 0.6, 0.9)]),
        ("Something burning put out, with bubbles rising", [v("entity.generic.extinguish_fire", 0.9, 0.9), v("block.bubble_column.upwards_inside", 0.4, 1.2)])]),
    row("forge.count", "The count-in tap", "count", [
        ("A note block's hat: a dry tick", [v("block.note_block.hat", 0.8, 1.0)]),
        ("Wood knocked, raised", [v("block.wood.hit", 0.9, 1.2)]),
        ("An anvil set down, very quiet and high", [v(PLACE, 0.25, 1.8)])]),
    row("forge.note", "The Forge Song's melody note (pitched to each set's tune; also a set's three notes when it becomes whole on you)", "motif", [
        ("A note block's iron xylophone: struck metal", [v("block.note_block.iron_xylophone", 0.9, 1.0)]),
        ("A note block's bell", [v("block.note_block.bell", 0.8, 1.0)]),
        ("A note block's chime", [v("block.note_block.chime", 0.8, 1.0)])]),
    row("forge.cut", "A cut in hide (by hand)", "alone", [
        ("Shears through wool", [v("entity.sheep.shear", 0.9, 0.9)]),
        ("An axe stripping bark, raised", [v("item.axe.strip", 0.8, 1.2)]),
        ("A stonecutter's cut", [v("ui.stonecutter.take_result", 0.9, 1.0)])]),
    row("forge.fit", "A piece set on the stand", "alone", [
        ("Iron armour going on, lowered, as wood is set down", [v("item.armor.equip_iron", 1.0, 0.9), v("block.wood.place", 0.6, 0.8)]),
        ("Plain armour going on, lowered", [v("item.armor.equip_generic", 1.0, 0.85)]),
        ("An armour stand set down, with chain mail", [v("entity.armor_stand.place", 0.9, 1.0), v("item.armor.equip_chain", 0.7, 1.0)])]),
    row("forge.wake", "A set waking (on the stand; quietly under the three notes on your body)", "alone", [
        ("Amethyst resonating, lowered, with an enchanting table's whisper", [v("block.amethyst_block.resonate", 0.9, 0.8), v("block.enchantment_table.use", 0.5, 0.9)]),
        ("A respawn anchor taking a charge", [v("block.respawn_anchor.charge", 0.6, 1.1)]),
        ("A beacon waking, quiet and raised", [v("block.beacon.activate", 0.5, 1.3)])]),
    row("forge.miss", "A missed beat", "alone", [
        ("A dull wooden thunk with a low anvil under it", [v("block.wood.hit", 0.8, 0.6), v(PLACE, 0.3, 0.6)]),
        ("A note block's bass drum, lowered", [v("block.note_block.basedrum", 0.8, 0.7)]),
        ("A dispenser with nothing in it", [v("block.dispenser.fail", 0.9, 0.9)])]),
    # ---- the set abilities -----------------------------------------------------------------------------------------
    row("armour.charged", "A set's charge filling", "alone", [
        ("Netherite armour settling, raised, with amethyst resonating", [v("item.armor.equip_netherite", 0.8, 1.2), v("block.amethyst_block.resonate", 0.5, 1.2)]),
        ("A respawn anchor taking a charge, raised", [v("block.respawn_anchor.charge", 0.6, 1.4)]),
        ("An amethyst chime with chain mail", [v("block.amethyst_block.chime", 0.9, 1.0), v("item.armor.equip_chain", 0.5, 1.3)])]),
    row("armour.bastion", "Bastion's burst", "alone", [
        ("The mace on the ground, with a breeze's burst of wind", [v("item.mace.smash_ground", 0.9, 0.9), v("entity.breeze.wind_burst", 0.6, 0.8)]),
        ("A wind charge bursting, lowered, over a shield block", [v("entity.wind_charge.wind_burst", 0.9, 0.7), v("item.shield.block", 0.8, 0.7)]),
        ("The mace's heaviest ground blow", [v("item.mace.smash_ground_heavy", 0.8, 1.0)])]),
    row("armour.flow", "Flow's afterimage taking a blow", "alone", [
        ("A phantom's swoop, raised, with a far, high teleport", [v("entity.phantom.swoop", 0.6, 1.3), v("entity.enderman.teleport", 0.3, 1.6)]),
        ("A trident's riptide, quiet and raised", [v("item.trident.riptide_1", 0.5, 1.4)]),
        ("A breeze leaping", [v("entity.breeze.jump", 0.8, 1.2)])]),
    row("armour.edge", "Edge's cutting wave", "alone", [
        ("Minecraft's sweep attack, lowered, with a trident thrown", [v("entity.player.attack.sweep", 1.0, 0.8), v("item.trident.throw", 0.7, 0.8)]),
        ("A breeze's shot with the sweep attack", [v("entity.breeze.shoot", 0.8, 1.1), v("entity.player.attack.sweep", 0.7, 1.0)]),
        ("A trident's riptide, raised, over the sweep lowered", [v("item.trident.riptide_2", 0.5, 1.3), v("entity.player.attack.sweep", 0.8, 0.7)])]),
    row("armour.rune", "A rune joining the circle", "alone", [
        ("An enchanting table's whisper, raised", [v("block.enchantment_table.use", 0.7, 1.3)]),
        ("An amethyst cluster set down, raised", [v("block.amethyst_cluster.place", 0.8, 1.2)]),
        ("A page turned, with a quiet amethyst chime", [v("item.book.page_turn", 0.9, 0.8), v("block.amethyst_block.chime", 0.4, 1.2)])]),
    row("armour.overchannel", "An overchannel: the runes fly out", "alone", [
        ("An evoker's spell, with a wind charge bursting", [v("entity.evoker.cast_spell", 0.9, 1.1), v("entity.wind_charge.wind_burst", 0.5, 1.2)]),
        ("A respawn anchor emptying, raised", [v("block.respawn_anchor.deplete", 0.7, 1.2)]),
        ("An illusioner's spell, lowered", [v("entity.illusioner.cast_spell", 0.9, 0.9)])]),
    row("armour.ring", "Bellguard's Ring: the armour rings like a bell", "alone", [
        ("Minecraft's bell, lowered, with its long resonance", [v("block.bell.use", 0.8, 0.9), v("block.bell.resonate", 0.5, 1.0)]),
        ("The bell, well lowered", [v("block.bell.use", 0.7, 0.7)]),
        ("The anvil clang with the bell's resonance", [v(LAND, 0.45, 1.1), v("block.bell.resonate", 0.7, 0.9)])]),
    row("armour.rooted", "Rootwarden's Rooted", "alone", [
        ("Rooted dirt and mangrove roots set down, lowered", [v("block.rooted_dirt.place", 1.0, 0.8), v("block.mangrove_roots.place", 0.9, 0.7)]),
        ("Roots breaking, lowered, over wood set down", [v("block.roots.break", 0.9, 0.7), v("block.wood.place", 0.6, 0.6)]),
        ("Moss set down, with a low wooden knock", [v("block.moss.place", 0.9, 0.7), v("block.wood.hit", 0.7, 0.6)])]),
    row("armour.gore", "Hornlord's Gore", "alone", [
        ("A goat's ram landing, lowered, with a ravager's bite", [v("entity.goat.ram_impact", 1.0, 0.8), v("entity.ravager.attack", 0.5, 1.1)]),
        ("A hoglin's attack with Minecraft's knockback hit", [v("entity.hoglin.attack", 0.9, 0.9), v("entity.player.attack.knockback", 0.8, 0.8)]),
        ("A goat's ram with a ravager stunned, quiet", [v("entity.goat.ram_impact", 0.9, 0.9), v("entity.ravager.stunned", 0.5, 1.2)])]),
    # ---- new: putting a piece on, by what the set is made of -------------------------------------------------------
    row("armour.equip_hide", "Putting on hide (Wildhide, Rootwarden, Nightcourt)", "alone", new=True, subtitle="Hide armour goes on", picks=[
        ("Leather armour going on, lowered", [v("item.armor.equip_leather", 1.0, 0.85)]),
        ("Wolf armour going on", [v("item.armor.equip_wolf", 0.9, 0.9)]),
        ("A saddle going on a horse", [v("entity.horse.saddle", 0.8, 1.0)])]),
    row("armour.equip_cloth", "Putting on cloth (Cleftrunner, Veilwoven, Sovereign Weave, Signal's Echo)", "alone", new=True, subtitle="Cloth armour goes on", picks=[
        ("Wool set down, with an elytra's cloth", [v("block.wool.place", 0.9, 1.1), v("item.armor.equip_elytra", 0.6, 1.1)]),
        ("An elytra going on, lowered", [v("item.armor.equip_elytra", 0.9, 0.9)]),
        ("A loom's cloth, with a soft wool tap", [v("ui.loom.take_result", 0.7, 1.0), v("block.wool.hit", 0.7, 1.2)])]),
    row("armour.equip_mail", "Putting on mail (Amberwing, Lanternlight, Tidebreaker, Riftwalker)", "alone", new=True, subtitle="Mail goes on", picks=[
        ("Chain mail going on", [v("item.armor.equip_chain", 1.0, 1.0)]),
        ("Chain mail, lowered, with a chain set down", [v("item.armor.equip_chain", 0.9, 0.85), v("block.chain.place", 0.4, 1.2)]),
        ("Horse armour going on, raised", [v("entity.horse.armor", 0.8, 1.2)])]),
    row("armour.equip_plate", "Putting on plate (Bellguard, Bladeward, Gatewrought, Sunward)", "alone", new=True, subtitle="Plate goes on", picks=[
        ("Iron armour going on", [v("item.armor.equip_iron", 1.0, 1.0)]),
        ("Iron armour, lowered, with mail settling a moment after", [v("item.armor.equip_iron", 0.9, 0.9), v("item.armor.equip_chain", 0.4, 1.1, 70)]),
        ("Copper armour going on, lowered", [v("item.armor.equip_copper", 0.9, 0.9)])]),
    row("armour.equip_heavy", "Putting on heavy plate (Hornlord, Vaultbreaker, Lordscale, Leviathan, Starfall)", "alone", new=True, subtitle="Heavy plate goes on", picks=[
        ("Netherite armour going on, lowered", [v("item.armor.equip_netherite", 1.0, 0.85)]),
        ("Netherite armour, lower still, with netherite set down under it", [v("item.armor.equip_netherite", 0.9, 0.8), v("block.netherite_block.place", 0.35, 0.8)]),
        ("Iron armour well lowered, with a very quiet anvil", [v("item.armor.equip_iron", 1.0, 0.7), v(PLACE, 0.15, 0.7)])]),
    row("armour.equip_crystal", "Putting on crystal (Frostwalker, Shardcrown, Halo Choir, Dreamsteel)", "alone", new=True, subtitle="Crystal armour goes on", picks=[
        ("Diamond armour going on, raised, with amethyst set down", [v("item.armor.equip_diamond", 1.0, 1.1), v("block.amethyst_block.place", 0.5, 1.0)]),
        ("Diamond armour going on", [v("item.armor.equip_diamond", 1.0, 1.0)]),
        ("An amethyst cluster set down, with mail", [v("block.amethyst_cluster.place", 0.9, 0.9), v("item.armor.equip_chain", 0.5, 1.0)])]),
    row("armour.equip_bone", "Putting on bone and shell (Beastbound, Deepcarapace, Hollowheart)", "alone", new=True, subtitle="Bone armour goes on", picks=[
        ("A turtle shell going on, with bone set down", [v("item.armor.equip_turtle", 0.9, 1.0), v("block.bone_block.place", 0.6, 1.1)]),
        ("A skeleton's step, with plain armour lowered", [v("entity.skeleton.step", 0.9, 0.9), v("item.armor.equip_generic", 0.7, 0.9)]),
        ("Bone knocked, with leather", [v("block.bone_block.hit", 0.9, 1.0), v("item.armor.equip_leather", 0.6, 1.0)])]),
    row("armour.equip_feather", "Putting on feathers (Cloudrunner, Galewing)", "alone", new=True, subtitle="Feathered armour goes on", picks=[
        ("An elytra going on, raised", [v("item.armor.equip_elytra", 1.0, 1.15)]),
        ("A parrot's wings, quiet, with leather raised", [v("entity.parrot.fly", 0.5, 1.2), v("item.armor.equip_leather", 0.6, 1.2)]),
        ("A phantom's wingbeat, quiet and raised, with plain armour", [v("entity.phantom.flap", 0.5, 1.4), v("item.armor.equip_generic", 0.6, 1.2)])]),
    # ---- new: the sound of a set as you move, one soft sound a stride ------------------------------------------------
    row("armour.move_hide", "Walking in hide", "walk", new=True, subtitle="Armour shifts", picks=[
        ("Leather creaking, quiet and raised", [v("item.armor.equip_leather", 0.25, 1.4)]),
        ("A soft step on wool, lowered", [v("block.wool.step", 0.5, 0.8)]),
        ("A saddle's creak, very quiet", [v("entity.horse.saddle", 0.2, 1.5)])]),
    row("armour.move_cloth", "Walking in cloth", "walk", new=True, subtitle="Armour shifts", picks=[
        ("A step on wool, raised: cloth brushing", [v("block.wool.step", 0.6, 1.3)]),
        ("An elytra's cloth, very quiet and raised", [v("item.armor.equip_elytra", 0.2, 1.5)]),
        ("A step on a moss carpet, raised", [v("block.moss_carpet.step", 0.5, 1.2)])]),
    row("armour.move_mail", "Walking in mail", "walk", new=True, subtitle="Armour shifts", picks=[
        ("A step on chain, raised: links shifting", [v("block.chain.step", 0.5, 1.3)]),
        ("Chain mail, very quiet and raised", [v("item.armor.equip_chain", 0.22, 1.5)]),
        ("A chain tapped, quiet and raised", [v("block.chain.hit", 0.3, 1.5)])]),
    row("armour.move_plate", "Walking in plate", "walk", new=True, subtitle="Armour shifts", picks=[
        ("Iron armour, very quiet and raised: a soft clink", [v("item.armor.equip_iron", 0.22, 1.5)]),
        ("A step on chain with plain armour under it, both quiet", [v("block.chain.step", 0.35, 1.0), v("item.armor.equip_generic", 0.15, 1.3)]),
        ("An iron golem's step, very quiet and well raised", [v("entity.iron_golem.step", 0.25, 1.8)])]),
    row("armour.move_heavy", "Walking in heavy plate", "walk", new=True, subtitle="Armour shifts", picks=[
        ("Netherite armour, quiet: a low clank", [v("item.armor.equip_netherite", 0.25, 1.1)]),
        ("An iron golem's step, quiet and raised", [v("entity.iron_golem.step", 0.35, 1.4)]),
        ("A step on netherite, with iron armour very quiet", [v("block.netherite_block.step", 0.6, 0.9), v("item.armor.equip_iron", 0.12, 1.1)])]),
    row("armour.move_crystal", "Walking in crystal", "walk", new=True, subtitle="Armour shifts", picks=[
        ("A step on amethyst: a small glassy ring", [v("block.amethyst_block.step", 0.6, 1.1)]),
        ("A step on an amethyst cluster, with iron armour very quiet", [v("block.amethyst_cluster.step", 0.5, 1.0), v("item.armor.equip_iron", 0.12, 1.5)]),
        ("Diamond armour, very quiet and raised", [v("item.armor.equip_diamond", 0.2, 1.5)])]),
    row("armour.move_bone", "Walking in bone and shell", "walk", new=True, subtitle="Armour shifts", picks=[
        ("A skeleton's step: bones knocking", [v("entity.skeleton.step", 0.5, 1.1)]),
        ("A step on bone, raised", [v("block.bone_block.step", 0.5, 1.2)]),
        ("A turtle shell, very quiet and raised", [v("item.armor.equip_turtle", 0.2, 1.4)])]),
    row("armour.move_feather", "Walking in feathers", "walk", new=True, subtitle="Armour shifts", picks=[
        ("An elytra's cloth, very quiet and well raised", [v("item.armor.equip_elytra", 0.2, 1.6)]),
        ("A parrot's step with a wool step, raised", [v("entity.parrot.step", 0.5, 1.0), v("block.wool.step", 0.4, 1.4)]),
        ("A chicken's step, raised", [v("entity.chicken.step", 0.4, 1.2)])]),
    # ---- new: a blow landing on your armour, under Minecraft's own hurt sound ---------------------------------------
    row("armour.struck_soft", "A blow on hide, cloth or feathers", "struck", new=True, subtitle="A blow lands on armour", picks=[
        ("A cloth thud with leather, both lowered", [v("block.wool.hit", 0.9, 0.8), v("item.armor.equip_leather", 0.5, 0.8)]),
        ("Minecraft's weak attack, lowered, with wool falling", [v("entity.player.attack.weak", 0.7, 0.8), v("block.wool.fall", 0.8, 0.8)]),
        ("A bundle stuffed, lowered", [v("item.bundle.insert", 0.8, 0.7)])]),
    row("armour.struck_plate", "A blow on mail or plate", "struck", new=True, subtitle="A blow lands on armour", picks=[
        ("A shield block, quiet and raised, with iron armour lowered", [v("item.shield.block", 0.6, 1.25), v("item.armor.equip_iron", 0.5, 0.8)]),
        ("An iron golem struck, quiet and raised", [v("entity.iron_golem.hurt", 0.5, 1.3)]),
        ("A zombie on an iron door, quiet and raised, with mail", [v("entity.zombie.attack_iron_door", 0.4, 1.3), v("item.armor.equip_chain", 0.5, 0.9)])]),
    row("armour.struck_crystal", "A blow on crystal", "struck", new=True, subtitle="A blow lands on armour", picks=[
        ("Amethyst struck, with a quiet shield block raised", [v("block.amethyst_block.hit", 0.9, 0.9), v("item.shield.block", 0.4, 1.4)]),
        ("An amethyst cluster breaking, quiet", [v("block.amethyst_cluster.break", 0.6, 1.0)]),
        ("Glass knocked, with diamond armour", [v("block.glass.hit", 0.8, 1.0), v("item.armor.equip_diamond", 0.5, 1.0)])]),
    row("armour.struck_bone", "A blow on bone and shell", "struck", new=True, subtitle="A blow lands on armour", picks=[
        ("Bone knocked, lowered, with a skeleton's rattle quiet", [v("block.bone_block.hit", 0.9, 0.9), v("entity.skeleton.hurt", 0.4, 1.1)]),
        ("A turtle's shell struck", [v("entity.turtle.hurt", 0.7, 1.0), v("block.bone_block.hit", 0.6, 1.0)]),
        ("An armadillo's shell taking a blow", [v("entity.armadillo.hurt_reduced", 0.9, 1.0)])]),
]
BY_ID = {r["id"]: r for r in ROWS}

# a scene: (ms, what) with "X" for the row's own sound and "X+n" for it n semitones up; ("row", id) plays another row's
# chosen pick; the rest is what Minecraft plays round it
SCENES = {
    "alone": [(0, "X")],
    "strike": [(0, ("row", "forge.count")), (450, ("row", "forge.count")), (900, "X"), (1350, "X"), (1800, "X"), (2250, "X")],
    "count": [(0, "X"), (450, "X"), (900, "X"), (1350, ("row", "forge.strike_iron"))],
    "pull": [(0, ("row", "forge.bellows")), (900, "X")],
    "motif": [(0, "X+0"), (150, "X+4"), (300, "X+7"), (0, ("row", "forge.wake", 0.35))],
    "walk": [(k * 340, v("block.stone.step", 0.6)) for k in range(6)] + [(k * 340 + 50, "X") for k in range(6)],
    "struck": [(0, v("entity.player.hurt", 0.9)), (0, "X")],
}
SCENE_SAYS = {
    "alone": "the sound by itself",
    "strike": "two taps of the count, then four strikes on the beat",
    "count": "three taps, then the first strike on iron",
    "pull": "the fire, then the bar comes out",
    "motif": "three notes rising, as when a set becomes whole on you, with the wake quiet under them",
    "walk": "six steps on stone, the armour's sound just after each footfall",
    "struck": "Minecraft's own hurt sound with the armour's under it",
}


# ---- which pick the game plays ---------------------------------------------------------------------------------------

def chosen():
    picks = json.load(open(PICKS)) if os.path.exists(PICKS) else {}
    return {r["id"]: picks.get(r["id"], 1) for r in ROWS}


def layers_of(r, pick):
    """A row's recipe for a pick (1-3); None for "today" (the event as it was built on 29 Oct)."""
    return None if pick == "today" else r["picks"][int(pick) - 1][1]


# ---- the game's files ------------------------------------------------------------------------------------------------

def build():
    before = json.load(open(BEFORE))
    sounds = json.load(open(SOUNDS_JSON))
    pick = chosen()
    extra = {}
    for r in ROWS:
        layers = layers_of(r, pick[r["id"]])
        if layers is None:
            sounds[r["id"]] = before[r["id"]]
            continue
        first = layers[0]
        assert first["at"] == 0, r["id"] + ": a recipe's first recording starts the sound"
        entry = {"sounds": [{"name": first["e"], "type": "event", "volume": first["vol"], "pitch": first["pitch"]}]}
        old = sounds.get(r["id"]) or before.get(r["id"]) or {}
        if r["subtitle"] or old.get("subtitle"):
            entry = {"subtitle": old.get("subtitle") or "subtitles.awaken." + r["id"], **entry}
        sounds[r["id"]] = entry
        if len(layers) > 1:
            extra["awaken:" + r["id"]] = [{"sound": l["e"], "volume": l["vol"], "pitch": l["pitch"], "delay": l["at"]} for l in layers[1:]]
    with open(SOUNDS_JSON, "w") as f:
        json.dump(sounds, f, indent=2)
        f.write("\n")
    os.makedirs(os.path.dirname(LAYERS_JSON), exist_ok=True)
    with open(LAYERS_JSON, "w") as f:
        json.dump(extra, f, indent=1, sort_keys=True)
        f.write("\n")
    today = [k for k, p in pick.items() if p == "today"]
    print(f"{len(ROWS)} sounds written ({len(extra)} layered by the client" + (f"; kept as built: {', '.join(today)}" if today else "") + ")")


def subtitles():
    """The new events' subtitle lines (merged into armour's en_us.json by hand once: see SPEC §44.12)."""
    return {"subtitles.awaken." + r["id"]: r["subtitle"] for r in ROWS if r["new"] and r["subtitle"]}


# ---- Minecraft's own recordings on this machine ----------------------------------------------------------------------

def vanilla():
    idx = max(glob.glob(ASSETS + "/indexes/*.json"), key=lambda p: int(os.path.basename(p)[:-5]) if os.path.basename(p)[:-5].isdigit() else -1)
    objects = json.load(open(idx))["objects"]
    h = objects["minecraft/sounds.json"]["hash"]
    return objects, json.load(open(f"{ASSETS}/objects/{h[:2]}/{h}"))


def manifest():
    """Every event the page plays: its recordings as urls, each with its own volume and pitch. Fails on a missing one."""
    objects, vsj = vanilla()
    before = json.load(open(BEFORE))
    used = set()
    for r in ROWS:
        for _, layers in r["picks"]:
            used.update(l["e"] for l in layers)
        if r["id"] in before:
            used.add("awaken:" + r["id"])
    for scene in SCENES.values():
        used.update(what["e"] for _, what in scene if isinstance(what, dict))
    events, files, missing = {}, {}, []
    for e in sorted(used):
        ns, name = e.split(":", 1)
        entry = vsj.get(name) if ns == "minecraft" else before.get(name)
        if entry is None:
            missing.append(e)
            continue
        out, todo = [], list(entry["sounds"])
        while todo:
            s = todo.pop(0)
            s = {"name": s} if isinstance(s, str) else s
            n = s["name"].split(":", 1)[-1]
            if s.get("type") == "event":  # one of Minecraft's events naming another: its recordings, scaled
                for t in vsj.get(n, {}).get("sounds", []):
                    t = dict({"name": t} if isinstance(t, str) else t)
                    t["volume"], t["pitch"] = t.get("volume", 1.0) * s.get("volume", 1.0), t.get("pitch", 1.0) * s.get("pitch", 1.0)
                    todo.append(t)
                continue
            if ns == "minecraft":
                o = objects.get(f"minecraft/sounds/{n}.ogg")
                url, path = "/v/" + n + ".ogg", f"{ASSETS}/objects/{o['hash'][:2]}/{o['hash']}" if o else ""
            else:
                url, path = "/a/" + n + ".ogg", os.path.join(OURS, n + ".ogg")
            if not path or not os.path.exists(path):
                missing.append(e + " -> " + n)
                continue
            files[url] = path
            out.append({"url": url, "vol": s.get("volume", 1.0), "pitch": s.get("pitch", 1.0)})
        if not out:
            missing.append(e + " (no recordings)")
        events[e] = out
    if missing:
        raise SystemExit("recordings not found:\n  " + "\n  ".join(missing))
    return events, files


# ---- the listening page ----------------------------------------------------------------------------------------------

PAGE = r"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Armour sounds, by ear</title>
<style>
:root { --bg: #f2f3f5; --panel: #fff; --ink: #15181d; --muted: #596170; --line: #d7dbe2; --soft: #e9ebef; --forge: #a9560f; --ok: #2c7a4b }
@media (prefers-color-scheme: dark) { :root { --bg: #0f1216; --panel: #171b21; --ink: #e7e9ed; --muted: #9aa2af; --line: #2a303a; --soft: #1e232b; --forge: #eda155; --ok: #74c493; color-scheme: dark } }
body { margin: 0; background: var(--bg); color: var(--ink); font: 16px/1.55 system-ui, -apple-system, "Segoe UI", sans-serif; padding: 24px 16px 80px }
main { max-width: 60rem; margin: 0 auto; display: flex; flex-direction: column; gap: 22px }
h1 { font-size: 2rem; margin: 0; letter-spacing: -0.01em } h2 { font-size: 1.1rem; margin: 0 } h3 { font-size: 0.8rem; letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted); margin: 14px 0 0 }
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
<h1>Armour sounds, by ear</h1>
<p>Forty-five sounds: the Forge Song, the set abilities, and the new ones (putting a piece on, walking in a set, a blow landing on it). Each has three picks made from Minecraft's own recordings. <strong>Pick 1 is what the game plays now.</strong> The old ones also have <strong>As built</strong>, the sound made on 29 October that nobody has heard. <strong>In its moment</strong> plays the sound where the game plays it. <strong>Alone</strong> plays just the sound. Press <strong>Use this</strong> on the one you want, or <strong>None of these</strong> and say a word.</p>
<p class="muted small">Every pick is a guess until you have heard it. The levels are first values. This page plays the recordings from this Mac's own copy of the game; it is not hosted anywhere. Your marks save as you go.</p>
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
  function gather(layers, ms, up, gain, into) {
    layers.forEach(function (l) {
      var vs = DATA.events[l.e], pick = vs[Math.floor(Math.random() * vs.length)];
      into.push({ url: pick.url, vol: l.vol * pick.vol * gain, pitch: l.pitch * pick.pitch * Math.pow(DATA.semitone, up), at: ms + (l.at || 0) });
    });
  }
  function play(plan) {
    stop();
    var c = audio();
    Promise.all(plan.map(function (p) { return buffer(p.url); })).then(function (bufs) {
      var t0 = c.currentTime + 0.06;
      plan.forEach(function (p, i) {
        var s = c.createBufferSource(), g = c.createGain();
        s.buffer = bufs[i]; s.playbackRate.value = p.pitch; g.gain.value = Math.min(1.5, p.vol); s.connect(g); g.connect(master); s.start(t0 + p.at / 1000); live.push(s);
      });
    }).catch(function (e) { say("Couldn't load " + e.message, true); });
  }
  function alone(layers) { var plan = []; gather(layers, 0, 0, 1, plan); play(plan); }
  function scene(row, layers) {
    var plan = [];
    DATA.scenes[row.scene].forEach(function (step) {
      var ms = step[0], what = step[1];
      if (typeof what === "string") gather(layers, ms, what.length > 1 ? parseInt(what.slice(2), 10) : 0, 1, plan);
      else if (what.row) gather(DATA.now[what.row], ms, 0, what.gain || 1, plan);
      else gather([what], ms, 0, 1, plan);
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
    var host = document.getElementById("rows"), group = "";
    host.innerHTML = "";
    DATA.rows.forEach(function (r) {
      if (r.group !== group) { group = r.group; var g = document.createElement("h3"); g.textContent = group; host.appendChild(g); }
      var m = marks.rows[r.id] || (marks.rows[r.id] = {});
      var card = document.createElement("div"); card.className = "row";
      var h = document.createElement("h2"); h.textContent = r.name; card.appendChild(h);
      var p = document.createElement("p"); p.className = "muted small"; p.textContent = (r.isNew ? "New: silent today. " : "") + "In its moment: " + DATA.sceneSays[r.scene] + "."; card.appendChild(p);
      var opts = document.createElement("div"); opts.className = "opts";
      var all = r.picks.map(function (k, i) { return { key: "pick " + (i + 1), title: "Pick " + (i + 1) + (i === 0 ? " (in the game now)" : ""), say: k.say, layers: k.layers }; });
      if (r.today) all.push({ key: "today", title: "As built", say: "The sound made on 29 October", layers: r.today });
      all.push({ key: "none", title: "None of these", say: "Try again with other recordings", layers: null });
      all.forEach(function (o) {
        var box = document.createElement("div"); box.className = "opt" + (m.pick === o.key ? " on" : "");
        var b = document.createElement("b"); b.textContent = o.title; box.appendChild(b);
        var s = document.createElement("div"); s.className = "say"; s.textContent = o.say; box.appendChild(s);
        var btns = document.createElement("div"); btns.className = "btns";
        if (o.layers) {
          if (r.scene !== "alone") { var f = document.createElement("button"); f.type = "button"; f.textContent = "In its moment"; f.onclick = function () { scene(r, o.layers); }; btns.appendChild(f); }
          var a = document.createElement("button"); a.type = "button"; a.textContent = "Alone"; a.onclick = function () { alone(o.layers); }; btns.appendChild(a);
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


def group_of(r):
    i = r["id"]
    return ("The Forge Song" if i.startswith("forge.") else "Putting a piece on" if ".equip_" in i else "Walking in a set" if ".move_" in i
            else "A blow landing on you" if ".struck_" in i else "The set abilities")


def page():
    events, files = manifest()
    before = json.load(open(BEFORE))
    pick = chosen()

    def step(what):
        if isinstance(what, tuple):
            return {"row": what[1], "gain": what[2] if len(what) > 2 else 1}
        return what

    def today(r):
        return [{"e": "awaken:" + r["id"], "vol": 1.0, "pitch": 1.0, "at": 0}] if r["id"] in before else None

    data = {"semitone": SEMITONE, "events": events, "sceneSays": SCENE_SAYS,
            "scenes": {k: [[ms, step(what)] for ms, what in s] for k, s in SCENES.items()},
            "now": {r["id"]: layers_of(r, pick[r["id"]]) or today(r) for r in ROWS},
            "rows": [{"id": r["id"], "name": r["name"], "scene": r["scene"], "isNew": r["new"], "group": group_of(r), "today": today(r),
                      "picks": [{"say": say, "layers": layers} for say, layers in r["picks"]]} for r in ROWS]}
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "index.html"), "w") as f:
        f.write(PAGE.replace("/*DATA*/", json.dumps(data)))
    print(f"{len(ROWS)} rows, {len(events)} events, {len(files)} recordings -> {os.path.join(OUT, 'index.html')}")
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


def apply():
    """His marks into the committed picks file: "pick n" -> n, "today" -> "today"; "none" stays as it is and is listed."""
    marks = json.load(open(os.path.join(OUT, "picks.json")))["rows"]
    picks = json.load(open(PICKS)) if os.path.exists(PICKS) else {}
    left = []
    for r in ROWS:
        m = (marks.get(r["id"]) or {}).get("pick", "")
        if m.startswith("pick "):
            picks[r["id"]] = int(m[5:])
        elif m == "today":
            picks[r["id"]] = "today"
        else:
            left.append(r["id"] + (" (none of these: " + (marks.get(r["id"]) or {}).get("note", "") + ")" if m == "none" else " (not marked)"))
    with open(PICKS, "w") as f:
        json.dump(picks, f, indent=1, sort_keys=True)
        f.write("\n")
    print(f"{len(picks)} rows chosen; run build. Still open:\n  " + "\n  ".join(left) if left else f"{len(picks)} rows chosen; run build.")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "build":
        build()
    elif cmd == "check":
        events, files = manifest()
        print(f"{len(events)} events, {len(files)} recordings: all here")
    elif cmd == "listen":
        serve(int(sys.argv[2]) if len(sys.argv) > 2 else 8767)
    elif cmd == "page":
        page()
    elif cmd == "apply":
        apply()
    else:
        raise SystemExit(__doc__)
