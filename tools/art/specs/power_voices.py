#!/usr/bin/env python3
"""Power voices (SPEC §50.7, "Scores"; docs/powers-next/BUILD.md M2). Euan, 9 and 10 Nov 2026: built sounds read as fake, and
"the sounds are a big one". So a power's sound is a score: for each beat of its cast a few layers, and every layer is one of
Minecraft's own recordings, named the way sounds.json names it, at a volume, a pitch and a start tick. The game ships the files;
nothing of Mojang's is in the repo, only the names.

    python3 tools/art/specs/power_voices.py build      writes assets/awaken/power_voices.json and the voice.* events into
                                                       merge/powers/assets/awaken/sounds.json
    python3 tools/art/specs/power_voices.py check      the same checks, writes nothing; lists every recording the ring check flags

Edit PALETTE (which recordings an event may play) and the voice(...) rows. A layer is (event, tick, volume, pitch[, jitter[, cut
ticks]]); a beat is one list of layers, or sets(a, b) for two that take turns, or beat(layers, surface=True, duck=4, cut=True).
Rules the build enforces: every recording exists in this Minecraft's sound list; no tonal source (amethyst, note blocks, bells,
the level-up); at most 12 layers a set; volume at most 1, pitch 0.5 to 2. The ring check (ding_check.py) is a tripwire only:
real metal rings too, and Euan is the judge.
"""
import json
import os
import re
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "../../.."))
ASSETS = os.environ.get("MC_ASSETS", "/Volumes/MEDIA/.gradle-home/caches/neoformruntime/assets")
OUT_VOICES = os.path.join(ROOT, "src/main/resources/assets/awaken/power_voices.json")
OUT_SOUNDS = os.path.join(ROOT, "src/main/merge/powers/assets/awaken/sounds.json")
OUT_LANG = os.path.join(ROOT, "src/main/merge/powers_voice/assets/awaken/lang/en_us.json")
BEATS = ["gather", "peak", "release", "travel", "impact", "tail", "echo", "hold", "end", "level5", "extra_a", "extra_b"]
TONAL = ("block/amethyst", "note/", "block/bell", "random/levelup", "random/orb", "block/beacon/power", "item/goat_horn", "mob/allay",
         "block/respawn_anchor/charge", "block/vault", "records/", "music/", "block/copper_bulb", "entity/experience")

_INDEX = None


def index():
    global _INDEX
    if _INDEX is None:
        d = os.path.join(ASSETS, "indexes")
        f = sorted(x for x in os.listdir(d) if x.endswith(".json"))[-1]
        objs = json.load(open(os.path.join(d, f)))["objects"]
        _INDEX = {k[len("minecraft/sounds/"):-4]: v["hash"] for k, v in objs.items() if k.startswith("minecraft/sounds/") and k.endswith(".ogg")}
    return _INDEX


# ---- the palette: an event of ours -> the game's recordings it may play --------------------------------------------------
# "prefix#" takes every numbered take of that name; a plain name is one file; "-name" after it drops a take (one that rings).
PALETTE = OrderedDict()


def P(name, *sources, distance=28):
    PALETTE[name] = (list(sources), distance)


# the body: hands, breath, heart, cloth
P("heartbeat", "mob/warden/heartbeat_#")
P("breath", "random/breath")
P("leather", "item/armor/equip_leather#")
P("cloth", "dig/cloth#")
P("generic_equip", "item/armor/equip_generic#")
P("strong", "entity/player/attack/strong#")
P("weak", "entity/player/attack/weak#")
P("sweep", "entity/player/attack/sweep#")
P("crit", "entity/player/attack/crit#")
P("knockback", "entity/player/attack/knockback#")
P("slap", "mob/slime/attack#")
P("brush", "item/brush/brushing_generic#")
P("page", "item/book/open_flip#")
# weight: blows, ground, stone
P("mace_ground", "item/mace/smash_ground2", distance=40)
P("mace_air", "item/mace/smash_air3", distance=36)
P("mace_heavy", "item/mace/smash_ground_heavy", distance=44)
P("golem_hit", "mob/irongolem/hit#", distance=32)
P("golem_damage", "mob/irongolem/damage#", distance=32)
P("golem_throw", "mob/irongolem/throw")
P("golem_walk", "mob/irongolem/walk#")
P("ravager_step", "mob/ravager/step#", distance=36)
P("ravager_stun", "mob/ravager/stun#", distance=32)
P("ravager_roar", "mob/ravager/roar#", distance=40)
P("ravager_bite", "mob/ravager/bite#", distance=32)
P("warden_hit", "mob/warden/attack_impact_#", distance=36)
P("warden_boom", "mob/warden/sonic_boom#", "-mob/warden/sonic_boom4", distance=48)
P("warden_charge", "mob/warden/sonic_charge#", "-mob/warden/sonic_charge1", distance=36)
P("warden_dig", "mob/warden/dig", distance=36)
P("warden_emerge", "mob/warden/emerge", distance=40)
P("warden_step", "mob/warden/step_#", distance=32)
P("tendril", "mob/warden/tendril_clicks_#")
P("wood_hit", "mob/zombie/wood#", distance=32)
P("wood_break", "mob/zombie/woodbreak", distance=32)
P("shield", "item/shield/block#")
P("horse_land", "mob/horse/land")
P("horse_jump", "mob/horse/jump")
P("stone", "dig/stone#")
P("gravel", "dig/gravel#")
P("deepslate", "block/deepslate/break#")
P("basalt", "block/basalt/break#")
P("dripstone", "block/dripstone/break#")
P("explode", "random/explode#", distance=48)
P("piston_out", "tile/piston/out")
P("piston_in", "tile/piston/in")
P("iron_door_open", "block/iron_door/open#")
P("iron_door_close", "block/iron_door/close#")
P("door_open", "random/door_open")
P("door_close", "random/door_close")
P("click", "random/click")
# air: wind, wings, speed
P("breeze_inhale", "mob/breeze/inhale#")
P("breeze_burst", "mob/breeze/wind_burst#", "-mob/breeze/wind_burst2", distance=36)
P("breeze_shoot", "mob/breeze/shoot", distance=32)
P("breeze_charge", "mob/breeze/charge#")
P("breeze_slide", "mob/breeze/slide#")
P("breeze_jump", "mob/breeze/jump#")
P("breeze_land", "mob/breeze/land#")
P("breeze_deflect", "mob/breeze/deflect#")
P("breeze_air", "mob/breeze/idle_air#")
P("wind_burst", "entity/wind_charge/wind_burst#", distance=36)
P("elytra", "item/elytra/elytra_loop")
P("wings", "mob/enderdragon/wings#", distance=32)
P("flap", "mob/phantom/flap#")
P("trident_throw", "item/trident/throw2")
P("trident_ground", "item/trident/ground_impact#")
P("riptide", "item/trident/riptide#")
P("bow", "random/bow")
P("crossbow_start", "item/crossbow/loading_start")
P("crossbow_middle", "item/crossbow/loading_middle#")
P("crossbow_end", "item/crossbow/loading_end")
P("crossbow_shoot", "item/crossbow/shoot#")
P("leash", "entity/leashknot/leash#")
# light, heat and storm
P("thunder", "ambient/weather/thunder#", distance=64)
P("rain", "ambient/weather/rain#")
P("blast", "fireworks/blast1", distance=40)
P("blast_far", "fireworks/blast_far1", distance=48)
P("large_blast", "fireworks/largeblast1", distance=48)
P("large_blast_far", "fireworks/largeblast_far1", distance=56)
P("crackle", "fireworks/twinkle1", distance=32)
P("ignite", "fire/ignite")
P("fire", "fire/fire")
P("fizz", "random/fizz")
P("lava_pop", "liquid/lavapop")
P("blaze", "mob/blaze/breathe#", "-mob/blaze/breathe1", distance=32)
P("fireball", "mob/ghast/fireball4", distance=36)
# cold and glass
P("freeze", "entity/player/hurt/freeze_hurt#")
P("glass", "random/glass3")
P("creak_freeze", "mob/creaking/creaking_freeze#")
P("creak_unfreeze", "mob/creaking/creaking_unfreeze#")
P("creak_sway", "mob/creaking/creaking_sway#", "-mob/creaking/creaking_sway1")
# water and pressure
P("under_enter", "ambient/underwater/enter#")
P("under_exit", "ambient/underwater/exit#")
P("whirlpool", "block/bubble_column/whirlpool_inside")
P("bubble", "block/bubble_column/bubble#")
P("splash", "liquid/heavy_splash")
P("conduit_hum", "block/conduit/ambient")
P("conduit_on", "block/conduit/activate")
P("conduit_off", "block/conduit/deactivate", distance=36)
P("beacon_on", "block/beacon/activate")
P("beacon_off", "block/beacon/deactivate")
P("portal", "portal/portal")
P("portal_trigger", "portal/trigger")
# the strange: shadow, mind, machines
P("blink", "mob/endermen/portal2")
P("mirror", "mob/illusion_illager/mirror_move#")
P("evoker_cast", "mob/evocation_illager/cast#")
P("fangs", "mob/evocation_illager/fangs")
P("shulker_open", "entity/shulker/open#")
P("shulker_close", "entity/shulker/close#")
P("shulker_shoot", "entity/shulker/shoot#")
P("bullet_hit", "entity/shulker_bullet/hit#")
P("sculk_click", "block/sculk_sensor/sculk_clicking#")
P("sculk_charge", "block/sculk/charge#")
P("sculk_spread", "block/sculk/spread#")
P("spyglass", "item/spyglass/use")
P("bee", "mob/bee/loop#")
P("silverfish", "mob/silverfish/step#")
P("slime_big", "mob/slime/big#")
# the forge, the cold and green things (the earned powers, docs/powers-next)
P("crackle_fire", "block/furnace/fire_crackle#")
P("furnace_roar", "block/blastfurnace/blastfurnace#", distance=36)
P("fire_hurt", "entity/player/hurt/fire_hurt#")
P("ghast_charge", "mob/ghast/charge", distance=32)
P("dragon_growl", "mob/enderdragon/growl#", distance=48)
P("powder_snow", "block/powder_snow/break#")
P("snow_scoop", "item/bucket/fill_powder_snow#")
P("pierce", "item/trident/pierce#")
P("bowhit", "random/bowhit#")
P("swoop", "mob/phantom/swoop#")
P("leaves", "block/cherry_leaves/break#")
P("grass", "dig/grass#")
P("wood", "dig/wood#")
P("roots", "block/roots/break#")
P("rooted_dirt", "block/rooted_dirt/break#")
P("mangrove", "block/mangrove_roots/break#")
P("vine", "block/vine/break#")
P("moss", "block/moss/break#")

# ---- the scores ----------------------------------------------------------------------------------------------------------
VOICES = OrderedDict()


def beat(layers, surface=False, duck=0, cut=False, alt=None):
    return dict(sets=[list(layers)] + ([list(alt)] if alt else []), surface=surface, duck=duck, cut=cut)


def sets(a, b, **kw):
    return beat(a, alt=b, **kw)


MUTE = dict(sets=[], surface=False, duck=0, cut=False)


def voice(power, **beats):
    row = OrderedDict()
    for b in BEATS:
        if b in beats:
            v = beats[b]
            row[b] = v if isinstance(v, dict) else beat(v)
    extra = set(beats) - set(BEATS)
    assert not extra, f"{power}: unknown beats {extra}"
    VOICES[power] = row


S = 20  # ticks in a second, for cuts

# Kinetic: weight you feel in your feet
voice("ground_slam",
      gather=[("elytra", 0, 0.5, 1.3, 0.03, 18), ("sweep", 0, 0.8, 0.6)],
      release=[("breeze_jump", 0, 0.8, 0.7), ("flap", 0, 0.9, 0.7)],
      impact=beat([("mace_ground", 0, 1.0, 0.75, 0.03, 36), ("golem_hit", 0, 0.9, 0.6), ("explode", 0, 0.45, 0.55, 0.03, 40), ("ravager_step", 1, 0.8, 0.6)],
                  surface=True, duck=4),
      level5=[("warden_boom", 0, 0.6, 0.8, 0.03, 44)])
voice("leap",
      release=[("horse_jump", 0, 0.8, 0.8), ("breeze_jump", 0, 0.8, 0.9), ("leather", 0, 0.8, 0.8)],
      extra_a=[("sweep", 0, 0.9, 0.55), ("elytra", 0, 0.5, 1.5, 0.03, 12)],
      impact=beat([("horse_land", 0, 0.9, 0.7), ("ravager_step", 0, 0.8, 0.7), ("golem_walk", 1, 0.7, 0.7)], surface=True))
voice("haymaker",
      gather=beat([("crossbow_start", 0, 0.9, 0.6), ("leather", 0, 1.0, 0.7), ("leather", 7, 0.8, 0.8), ("leather", 13, 0.9, 0.9),
                   ("heartbeat", 2, 0.35, 1.0), ("heartbeat", 10, 0.5, 1.15), ("heartbeat", 16, 0.65, 1.3)], cut=True),
      peak=[("crossbow_end", 0, 1.0, 0.75), ("golem_throw", 0, 0.9, 0.8)],
      release=[("sweep", 0, 1.0, 0.7), ("wings", 0, 0.6, 1.2)],
      impact=sets([("crit", 0, 0.9, 0.7), ("golem_hit", 0, 1.0, 0.75), ("wood_hit", 0, 0.5, 0.6), ("mace_ground", 0, 0.45, 0.8, 0.02, 32)],
                  [("knockback", 0, 1.0, 0.8), ("warden_hit", 0, 0.7, 0.7), ("wood_break", 0, 0.5, 0.6), ("mace_ground", 0, 0.4, 0.75, 0.02, 32)],
                  surface=True, duck=3),
      tail=[("explode", 0, 0.22, 0.6, 0.02, 44), ("ravager_step", 2, 0.4, 0.7)],
      extra_a=beat([("wood_break", 0, 0.9, 0.55), ("deepslate", 0, 0.9, 0.7), ("golem_damage", 0, 0.6, 0.6), ("dripstone", 1, 0.8, 0.6)], surface=True),
      level5=[("warden_boom", 0, 0.55, 0.75, 0.03, 50)])
voice("terrain_rip",
      release=beat([("warden_dig", 0, 0.8, 1.2, 0.03, 24), ("gravel", 0, 0.9, 0.7), ("gravel", 2, 0.8, 0.6), ("basalt", 1, 0.8, 0.6), ("creak_sway", 0, 0.7, 0.5)],
                   surface=True),
      impact=beat([("mace_ground", 0, 0.9, 0.7, 0.03, 34), ("deepslate", 0, 0.9, 0.6), ("explode", 0, 0.35, 0.6, 0.03, 30), ("gravel", 1, 0.8, 0.6)], surface=True),
      extra_a=[("gravel", 0, 0.8, 0.8), ("basalt", 0, 0.8, 0.7)])
voice("shockwave",
      release=beat([("ravager_step", 0, 1.0, 0.6), ("mace_ground", 0, 0.55, 0.9, 0.03, 30), ("golem_walk", 0, 0.8, 0.6), ("warden_step", 1, 0.7, 0.7)],
                   surface=True, duck=3),
      impact=sets([("knockback", 0, 0.9, 0.75), ("golem_hit", 0, 0.6, 0.8)], [("strong", 0, 0.9, 0.7), ("wood_hit", 0, 0.5, 0.7)]),
      extra_a=beat([("deepslate", 0, 0.9, 0.7), ("wood_hit", 0, 0.7, 0.6)], surface=True),
      level5=[("warden_boom", 0, 0.5, 0.9, 0.03, 40)])
voice("brace",
      release=[("iron_door_close", 0, 0.9, 0.6), ("leather", 0, 0.9, 0.6), ("golem_walk", 0, 0.7, 0.6), ("shield", 1, 0.6, 0.6)],
      impact=[("shield", 0, 1.0, 0.7), ("golem_hit", 0, 0.8, 0.7), ("breeze_deflect", 0, 0.6, 0.7)])
voice("thunderclap",
      release=beat([("crit", 0, 1.0, 0.9), ("strong", 0, 0.9, 0.7), ("thunder", 1, 0.5, 1.25, 0.03, 60), ("wind_burst", 0, 0.6, 1.1)], duck=3),
      impact=[("blast", 0, 0.7, 0.7), ("thunder", 0, 0.3, 1.4, 0.03, 40)],
      extra_a=[("blast", 0, 0.6, 0.9), ("crackle", 0, 0.4, 0.8)])
# Velocity: air torn by something already gone
voice("dash",
      release=sets([("breeze_slide", 0, 0.9, 1.2), ("sweep", 0, 0.6, 1.1), ("flap", 0, 0.9, 1.3)],
                   [("breeze_jump", 0, 0.9, 1.3), ("sweep", 0, 0.6, 1.2), ("bow", 0, 0.5, 0.7)]))
voice("flow",
      release=[("under_enter", 0, 0.9, 0.6), ("beacon_off", 0, 0.7, 0.9, 0.02, 50), ("heartbeat", 4, 0.5, 0.7), ("heartbeat", 22, 0.6, 0.6), ("heartbeat", 44, 0.6, 0.55)],
      end=[("under_exit", 0, 0.9, 0.9), ("breeze_burst", 0, 0.6, 1.2), ("wind_burst", 1, 0.4, 1.3)],
      extra_a=[("crit", 0, 0.9, 0.8), ("knockback", 0, 0.8, 0.7)])
voice("afterimage",
      release=[("breeze_slide", 0, 0.9, 1.0), ("mirror", 0, 0.5, 1.3, 0.03, 14), ("flap", 0, 0.9, 1.1)],
      extra_a=[("blink", 0, 0.5, 0.8), ("sweep", 0, 0.8, 1.2)],
      extra_b=MUTE)
voice("lightning_step",
      release=[("blast", 0, 0.6, 1.4), ("breeze_shoot", 0, 0.8, 1.3), ("trident_throw", 0, 0.7, 1.3)],
      impact=[("thunder", 0, 0.45, 1.5, 0.03, 50), ("blast", 0, 0.7, 0.9), ("wind_burst", 0, 0.5, 1.2)])
# Flight and gravity: pressure, and the air giving way
voice("hover",
      release=[("breeze_air", 0, 0.9, 0.8), ("flap", 0, 0.9, 0.7), ("elytra", 0, 0.4, 0.6, 0.02, 24)],
      end=[("breeze_land", 0, 0.8, 0.9), ("leather", 0, 0.7, 0.8)])
voice("flight",
      release=[("wings", 0, 0.9, 0.8), ("breeze_jump", 0, 0.8, 0.8), ("elytra", 0, 0.5, 0.8, 0.02, 30)],
      end=[("breeze_land", 0, 0.8, 0.8), ("flap", 0, 0.8, 0.8)])
voice("gravity_well",
      release=[("under_enter", 0, 0.9, 0.6), ("warden_charge", 0, 0.4, 0.6, 0.02, 44), ("whirlpool", 2, 0.8, 0.5, 0.0), ("whirlpool", 50, 0.8, 0.52, 0.0, 38),
               ("conduit_hum", 4, 0.9, 0.7, 0.0, 84), ("heartbeat", 20, 0.4, 0.8), ("heartbeat", 40, 0.45, 0.8), ("heartbeat", 60, 0.5, 0.8)],
      end=beat([("conduit_off", 0, 0.6, 0.8, 0.0, 48), ("warden_hit", 0, 0.6, 0.6)], duck=3),
      extra_a=[("bubble", 0, 0.8, 0.8), ("bullet_hit", 0, 0.5, 0.6)],
      level5=[("warden_boom", 0, 0.6, 0.7, 0.03, 50)])
voice("telekinesis",
      release=[("breeze_inhale", 0, 0.9, 0.7), ("evoker_cast", 0, 0.5, 0.7, 0.03, 20), ("leather", 0, 0.8, 0.6)],
      end=[("breeze_shoot", 0, 0.9, 0.8), ("sweep", 0, 0.8, 0.6), ("trident_throw", 0, 0.7, 0.8)],
      impact=beat([("golem_hit", 0, 0.9, 0.7), ("wood_hit", 0, 0.7, 0.6), ("mace_ground", 0, 0.4, 0.9, 0.02, 24)], surface=True),
      extra_a=beat([("piston_out", 0, 0.9, 0.6), ("deepslate", 0, 0.9, 0.6), ("golem_damage", 0, 0.6, 0.6)], surface=True))
voice("meteor_dive",
      gather=[("breeze_jump", 0, 0.9, 0.7), ("wings", 0, 0.8, 0.7), ("ignite", 0, 0.7, 0.6)],
      release=[("elytra", 0, 0.8, 1.6, 0.02, 24), ("blaze", 0, 0.5, 0.7, 0.03, 24), ("fireball", 0, 0.8, 0.6)],
      impact=beat([("explode", 0, 0.9, 0.7), ("mace_ground", 0, 0.9, 0.7, 0.02, 36), ("large_blast", 0, 0.6, 0.6), ("lava_pop", 2, 0.6, 0.6), ("fizz", 4, 0.5, 0.7)],
                  surface=True, duck=5),
      extra_a=[("fireball", 0, 0.8, 0.7), ("blast_far", 0, 0.6, 0.8)])
voice("gravity_flip",
      release=[("under_enter", 0, 0.9, 0.7), ("warden_charge", 0, 0.4, 0.7, 0.02, 30), ("breeze_inhale", 0, 0.9, 0.6)],
      extra_a=[("breeze_air", 0, 0.8, 0.6), ("flap", 0, 0.7, 0.6)],
      impact=beat([("mace_ground", 0, 0.9, 0.7, 0.02, 36), ("explode", 0, 0.5, 0.6, 0.03, 30), ("golem_hit", 0, 0.8, 0.6), ("gravel", 1, 0.8, 0.6)], surface=True, duck=4))
# Energy: air drawn in, then the sky
voice("beam",
      gather=[("breeze_inhale", 0, 0.8, 1.1), ("ignite", 0, 0.6, 0.8), ("conduit_on", 0, 0.5, 1.3, 0.02, 20)],
      hold=[("blaze", 0, 0.35, 1.4, 0.04, 13), ("fire", 0, 0.5, 1.2, 0.04, 13), ("conduit_hum", 0, 0.5, 1.5, 0.02, 13)],
      end=[("fizz", 0, 0.6, 0.8), ("beacon_off", 0, 0.5, 1.4, 0.02, 20)])
voice("nova",
      gather=[("breeze_inhale", 0, 1.0, 0.8), ("ignite", 0, 0.5, 0.6)],
      peak=[("crossbow_end", 0, 0.5, 1.2)],
      release=sets([("large_blast", 0, 1.0, 0.7), ("explode", 0, 0.5, 1.3, 0.04, 36), ("wind_burst", 0, 0.8, 0.8), ("large_blast_far", 4, 0.7, 0.6),
                    ("thunder", 3, 0.2, 1.2, 0.03, 70)],
                   [("warden_boom", 0, 0.7, 1.2, 0.03, 44), ("fireball", 0, 0.8, 0.6), ("breeze_burst", 0, 0.7, 0.9), ("large_blast_far", 3, 0.6, 0.7)], duck=4))
voice("barrier",
      release=[("shulker_close", 0, 0.9, 0.7), ("conduit_on", 0, 0.6, 1.0, 0.02, 30), ("golem_walk", 0, 0.5, 0.8), ("breeze_deflect", 0, 0.6, 0.7)],
      end=[("glass", 0, 0.7, 0.6), ("bullet_hit", 0, 0.8, 0.6), ("breeze_deflect", 0, 0.8, 0.6), ("deepslate", 0, 0.5, 0.8)],
      extra_a=[("shulker_close", 0, 0.6, 0.9), ("leather", 0, 0.8, 0.7)])
voice("charged_shot",
      gather=beat([("fire", 0, 0.6, 1.5, 0.02, 30), ("breeze_charge", 0, 0.6, 0.8), ("breeze_charge", 10, 0.7, 1.0), ("breeze_charge", 20, 0.8, 1.2),
                   ("crackle", 6, 0.35, 0.7, 0.04, 14), ("crackle", 16, 0.5, 0.85, 0.04, 14), ("heartbeat", 4, 0.3, 1.2), ("heartbeat", 18, 0.4, 1.4)], cut=True),
      peak=[("crossbow_end", 0, 0.9, 1.1), ("blast_far", 0, 0.4, 1.3)],
      release=[("blast", 0, 0.8, 1.2), ("trident_throw", 0, 0.8, 1.1), ("breeze_shoot", 0, 0.7, 1.1), ("crossbow_shoot", 0, 0.7, 0.8)],
      tail=[("thunder", 0, 0.5, 1.3, 0.03, 60), ("large_blast_far", 0, 0.6, 0.8)],
      extra_b=[("thunder", 0, 0.7, 1.1, 0.03, 80), ("large_blast", 0, 0.8, 0.8)])
voice("overload",
      gather=[("breeze_inhale", 0, 1.0, 0.6), ("portal_trigger", 0, 0.5, 0.6, 0.02, 30), ("heartbeat", 0, 0.5, 1.0), ("heartbeat", 8, 0.6, 1.2), ("heartbeat", 14, 0.7, 1.4)],
      peak=[("beacon_off", 0, 0.5, 1.5, 0.02, 16), ("crossbow_end", 0, 0.8, 0.8)],
      extra_a=[("under_enter", 0, 0.7, 0.5)],
      release=beat([("explode", 0, 1.0, 0.6), ("large_blast", 0, 0.9, 0.6), ("warden_boom", 0, 0.7, 1.0, 0.03, 50), ("thunder", 2, 0.5, 1.0, 0.03, 80),
                    ("wind_burst", 0, 0.8, 0.7)], duck=6))
voice("prism_lance",
      release=[("breeze_shoot", 0, 0.9, 1.2), ("blast", 0, 0.6, 1.3), ("trident_throw", 0, 0.8, 1.2), ("glass", 0, 0.3, 1.2)],
      hold=[("blaze", 0, 0.3, 1.5, 0.04, 14), ("fire", 0, 0.4, 1.4, 0.04, 14)])
# Shadow: what isn't there
voice("shadow_step",
      release=[("breath", 0, 0.9, 1.25, 0.03, 7), ("blink", 0, 0.45, 0.6)],
      impact=[("sweep", 3, 0.9, 0.6), ("wings", 3, 0.45, 0.7)],
      extra_a=[("lava_pop", 0, 0.7, 0.5), ("sculk_spread", 0, 0.9, 0.7)])
voice("cloak",
      release=[("flap", 0, 1.0, 0.6), ("cloth", 0, 0.9, 0.6), ("under_enter", 0, 0.5, 0.8, 0.02, 16)])
voice("echo_swap",
      release=[("blink", 0, 0.5, 0.7), ("breeze_deflect", 0, 0.7, 1.2)],
      impact=[("blink", 2, 0.5, 0.9), ("sweep", 2, 0.8, 0.8)],
      extra_a=[("blink", 0, 0.5, 1.1), ("flap", 0, 0.8, 1.0)])
voice("legion",
      release=beat([("warden_emerge", 0, 0.8, 1.0, 0.02, 50), ("sculk_charge", 4, 0.9, 0.7), ("ravager_step", 10, 0.7, 0.6), ("ravager_step", 16, 0.7, 0.6),
                    ("ravager_step", 22, 0.8, 0.6), ("breath", 2, 0.8, 0.6)], duck=5))
# Mind: close to the ear
voice("dominate",
      release=[("evoker_cast", 0, 0.7, 0.6, 0.03, 24), ("breath", 0, 0.8, 0.8), ("tendril", 0, 0.7, 0.7), ("heartbeat", 3, 0.5, 0.9)])
voice("read_intent",
      release=[("tendril", 0, 0.9, 0.9), ("page", 0, 0.8, 0.8), ("heartbeat", 0, 0.4, 1.1), ("spyglass", 0, 0.8, 0.7)])
voice("decoy",
      release=[("mirror", 0, 0.6, 1.1, 0.03, 16), ("leather", 0, 0.8, 1.0), ("blink", 0, 0.3, 1.3)])
voice("foresight",
      release=[("under_enter", 0, 0.7, 0.9), ("tendril", 0, 0.8, 0.6), ("heartbeat", 2, 0.6, 0.8), ("spyglass", 0, 0.8, 0.6)],
      extra_b=MUTE,
      end=[("under_exit", 0, 0.7, 1.1), ("breeze_burst", 0, 0.3, 1.4)])
# Architect: stone set down by a careful hand
voice("wall", release=[("stone", 0, 1.0, 0.7), ("piston_out", 0, 0.8, 0.8), ("deepslate", 1, 0.6, 0.9)])
voice("ramp", release=[("piston_out", 0, 0.8, 1.0), ("stone", 0, 0.9, 0.9), ("gravel", 1, 0.4, 1.1)], extra_a=[("breeze_slide", 0, 0.8, 1.1)])
voice("floor", release=[("stone", 0, 1.0, 0.6), ("horse_land", 0, 0.5, 0.6), ("piston_in", 0, 0.7, 0.8)], extra_a=[("piston_out", 0, 0.8, 1.4), ("slime_big", 0, 0.5, 1.0)])
voice("cone", release=[("stone", 0, 0.9, 1.1), ("dripstone", 0, 0.8, 1.0), ("click", 1, 0.5, 0.8)])
voice("rampart",
      release=[("stone", 0, 1.0, 0.6), ("stone", 3, 0.9, 0.7), ("stone", 6, 0.9, 0.8), ("piston_out", 2, 0.8, 0.6), ("deepslate", 8, 0.7, 0.7), ("iron_door_close", 10, 0.8, 0.5)],
      extra_a=[("crossbow_shoot", 0, 0.8, 0.8), ("click", 0, 0.5, 0.7)],
      end=[("gravel", 0, 0.8, 0.9), ("basalt", 0, 0.7, 0.9)],
      extra_b=[("iron_door_open", 0, 0.8, 0.7)])
voice("spire",
      release=[("warden_dig", 0, 0.7, 1.3, 0.03, 20), ("gravel", 0, 0.8, 0.7)],
      impact=beat([("piston_out", 0, 1.0, 0.5), ("deepslate", 0, 0.9, 0.6), ("dripstone", 0, 0.8, 0.6), ("stone", 1, 0.9, 0.6)], surface=True),
      tail=[("breeze_jump", 0, 0.7, 0.8), ("gravel", 0, 0.5, 0.9)])
voice("collapse",
      release=beat([("creak_sway", 0, 0.9, 0.5), ("deepslate", 0, 1.0, 0.6), ("deepslate", 3, 0.9, 0.6), ("gravel", 5, 0.9, 0.6), ("explode", 2, 0.4, 0.6, 0.03, 36),
                    ("basalt", 7, 0.8, 0.6)], duck=4))
voice("citadel",
      release=beat([("piston_out", 0, 1.0, 0.5), ("iron_door_close", 4, 0.9, 0.5), ("stone", 0, 1.0, 0.6), ("stone", 5, 0.9, 0.6), ("stone", 10, 0.9, 0.6),
                    ("deepslate", 2, 0.8, 0.6), ("warden_dig", 0, 0.6, 1.0, 0.02, 30)], duck=4),
      hold=[("piston_out", 0, 0.7, 0.6), ("stone", 0, 0.8, 0.7), ("iron_door_close", 1, 0.6, 0.6)],
      extra_a=[("crossbow_shoot", 0, 0.8, 0.7), ("blast", 0, 0.4, 1.2)])
# Tech: springs, latches and small motors
voice("grapple",
      release=[("crossbow_shoot", 0, 0.9, 0.9), ("trident_throw", 0, 0.6, 1.2), ("bow", 0, 0.7, 0.8)],
      impact=[("trident_ground", 0, 0.9, 0.9), ("crossbow_middle", 0, 0.8, 1.2), ("leash", 0, 0.8, 0.8)],
      extra_a=[("crit", 0, 0.9, 0.9), ("knockback", 0, 0.8, 0.8)])
voice("jet_burst",
      release=[("fireball", 0, 0.8, 0.8), ("blaze", 0, 0.5, 1.2, 0.03, 16), ("ignite", 0, 0.8, 0.7), ("breeze_jump", 0, 0.7, 1.0)])
voice("drone",
      release=[("click", 0, 0.7, 0.8), ("shulker_open", 0, 0.8, 1.2), ("bee", 0, 0.6, 1.3, 0.03, 30), ("piston_out", 0, 0.5, 1.5)])
voice("turret",
      release=[("piston_out", 0, 0.9, 0.8), ("iron_door_close", 0, 0.8, 0.8), ("shulker_open", 2, 0.8, 0.8), ("crossbow_end", 6, 0.9, 0.7)],
      extra_a=[("crossbow_shoot", 0, 0.8, 0.7), ("blast", 0, 0.4, 1.3)])
voice("railgun",
      gather=beat([("crossbow_start", 0, 0.9, 0.5), ("crossbow_middle", 6, 0.9, 0.6), ("crossbow_middle", 12, 0.9, 0.7), ("crossbow_middle", 18, 1.0, 0.8),
                   ("breeze_charge", 2, 0.6, 0.7), ("breeze_charge", 10, 0.7, 0.9), ("breeze_charge", 18, 0.8, 1.1), ("beacon_on", 0, 0.5, 1.2, 0.02, 24)], cut=True),
      peak=[("crossbow_end", 0, 1.0, 0.6), ("piston_in", 0, 0.8, 0.9)],
      release=beat([("large_blast", 0, 0.9, 1.1), ("crossbow_shoot", 0, 0.9, 0.6), ("trident_throw", 0, 0.8, 0.7), ("breeze_shoot", 0, 0.8, 0.7),
                    ("explode", 0, 0.4, 1.4, 0.03, 20)], duck=3),
      impact=beat([("mace_ground", 0, 0.7, 0.9, 0.02, 30), ("golem_damage", 0, 0.8, 0.6), ("deepslate", 0, 0.9, 0.6)], surface=True),
      extra_b=[("warden_boom", 0, 0.6, 1.1, 0.03, 44), ("warden_dig", 0, 0.6, 1.2, 0.03, 30)])
voice("nanite_skin",
      release=[("sculk_click", 0, 0.9, 1.2), ("sculk_spread", 0, 0.9, 1.0), ("sculk_charge", 1, 0.8, 1.2), ("silverfish", 0, 0.6, 1.3), ("silverfish", 3, 0.6, 1.4),
               ("leather", 0, 0.7, 0.9)])
voice("drone_swarm",
      release=[("bee", 0, 0.7, 1.2, 0.03, 40), ("bee", 4, 0.6, 1.5, 0.03, 36), ("shulker_open", 0, 0.8, 1.1), ("piston_out", 0, 0.5, 1.3), ("click", 2, 0.5, 0.9)],
      extra_a=[("crossbow_shoot", 0, 0.7, 1.0), ("blast", 0, 0.3, 1.4)])
# Spirit: breath, a bare hand, weather
voice("spirit_palm",
      release=[("strong", 0, 0.9, 0.8), ("sweep", 0, 0.5, 0.9)],
      impact=sets([("strong", 0, 1.0, 0.8), ("slap", 0, 0.7, 0.7), ("leather", 0, 0.8, 1.0)], [("strong", 0, 1.0, 0.9), ("knockback", 0, 0.6, 1.1), ("leather", 0, 0.8, 0.9)]),
      echo=sets([("heartbeat", 0, 1.0, 0.7), ("golem_hit", 0, 0.7, 0.6), ("knockback", 0, 0.5, 0.6)], [("ravager_stun", 0, 0.8, 0.9, 0.03, 24), ("wood_hit", 0, 0.45, 0.5)]))
voice("mend",
      release=[("breath", 0, 0.9, 0.7), ("under_exit", 0, 0.5, 0.7), ("brush", 0, 0.6, 0.7), ("beacon_on", 0, 0.5, 0.8, 0.02, 40), ("heartbeat", 6, 0.4, 0.6),
               ("heartbeat", 22, 0.3, 0.6)])
voice("third_eye",
      release=[("tendril", 0, 0.9, 0.7), ("under_enter", 0, 0.5, 1.0), ("spyglass", 0, 0.8, 0.6), ("heartbeat", 0, 0.4, 0.7)])
voice("cord_lash",
      release=[("bow", 0, 0.8, 0.6), ("leash", 0, 0.9, 0.7), ("sweep", 0, 0.8, 1.2), ("crossbow_shoot", 0, 0.5, 1.2)],
      impact=[("strong", 0, 0.9, 0.7), ("leather", 0, 0.9, 0.6), ("crossbow_middle", 0, 0.7, 0.8)])
voice("bulwark",
      release=beat([("warden_hit", 0, 0.8, 0.6), ("shulker_close", 0, 0.9, 0.6), ("breeze_burst", 0, 0.7, 0.7), ("conduit_on", 0, 0.5, 0.8, 0.02, 30),
                    ("golem_walk", 0, 0.7, 0.6)], duck=4))
voice("hundred_palms",
      release=[("breath", 0, 0.9, 1.0), ("strong", 0, 0.9, 0.9), ("sweep", 0, 0.7, 1.0)],
      hold=[("strong", 0, 0.6, 1.1), ("strong", 2, 0.5, 1.3), ("weak", 4, 0.5, 1.2), ("strong", 6, 0.5, 1.2)],
      impact=beat([("crit", 0, 1.0, 0.6), ("golem_hit", 0, 0.9, 0.6), ("mace_ground", 0, 0.6, 0.7, 0.02, 36), ("knockback", 0, 0.8, 0.6),
                   ("explode", 0, 0.3, 0.6, 0.03, 30)], surface=True, duck=4))
voice("void_maw",
      release=[("ravager_roar", 0, 0.7, 0.5, 0.03, 36), ("under_enter", 0, 0.9, 0.5), ("whirlpool", 3, 0.7, 0.5), ("conduit_hum", 0, 0.8, 0.6, 0.0, 70)],
      hold=[("whirlpool", 0, 0.6, 0.5), ("ravager_bite", 0, 0.5, 0.6), ("heartbeat", 0, 0.5, 0.6)],
      end=[("ravager_bite", 0, 1.0, 0.5), ("fangs", 0, 0.8, 0.6), ("golem_hit", 0, 0.6, 0.5)])
voice("waking_dream",
      release=[("blink", 0, 0.4, 0.5), ("under_enter", 0, 0.5, 1.2), ("door_open", 0, 0.9, 0.6), ("brush", 0, 0.7, 0.6)],
      end=[("door_close", 0, 0.9, 0.6), ("under_exit", 0, 0.4, 1.2)])
voice("storm_call",
      release=beat([("thunder", 0, 0.9, 0.8, 0.03, 100), ("breeze_inhale", 0, 0.9, 0.6), ("wind_burst", 3, 0.6, 0.6), ("rain", 4, 0.5, 1.0, 0.02, 60)], duck=5))
voice("ember_step",
      release=[("ignite", 0, 0.9, 0.7), ("blaze", 0, 0.5, 1.1, 0.03, 14), ("fireball", 0, 0.5, 0.9), ("breeze_slide", 0, 0.6, 1.0)])
voice("frost_nova",
      release=sets([("mace_ground", 0, 0.4, 1.2, 0.02, 20), ("freeze", 0, 1.0, 0.7), ("creak_freeze", 0, 1.0, 0.6), ("glass", 1, 0.4, 0.5), ("dripstone", 2, 0.8, 0.7),
                    ("dripstone", 4, 0.8, 0.8), ("dripstone", 7, 0.7, 0.9), ("creak_unfreeze", 5, 0.8, 0.5), ("wind_burst", 1, 0.3, 0.6)],
                   [("heartbeat", 0, 0.8, 0.9), ("under_exit", 0, 0.5, 1.3, 0.03, 20), ("freeze", 0, 1.0, 0.6), ("freeze", 4, 0.8, 0.8), ("creak_freeze", 2, 1.0, 0.5),
                    ("dripstone", 3, 0.8, 0.7)], surface=True))
voice("ghost_step",
      release=[("breath", 0, 0.9, 1.1, 0.03, 8), ("under_enter", 0, 0.6, 1.1, 0.02, 14)],
      impact=[("under_exit", 0, 0.6, 1.1), ("flap", 0, 0.9, 1.0)],
      extra_a=[("door_open", 0, 0.8, 0.5), ("blink", 0, 0.3, 0.6)])
voice("twin_strike",
      release=[("mirror", 0, 0.6, 0.8, 0.03, 20), ("sweep", 0, 0.9, 0.8), ("sweep", 3, 0.8, 0.8)])

# ---- Earned (SPEC 50, "Earned powers") ---------------------------------------------------------------------------------------
# Brand: a hand on a body and the hiss of it (impact); the fire coming out when your blade lights it (release); the leap (echo).
voice("brand",
      impact=sets([("strong", 0, 0.8, 0.8), ("leather", 0, 0.7, 0.9), ("fizz", 1, 0.7, 0.7), ("ignite", 1, 0.8, 0.6), ("crackle_fire", 3, 0.8, 0.8), ("crackle_fire", 9, 0.6, 0.9)],
                  [("slap", 0, 0.6, 0.7), ("strong", 0, 0.8, 0.9), ("fizz", 1, 0.8, 0.6), ("fire_hurt", 1, 0.5, 0.6), ("crackle_fire", 4, 0.8, 0.7)]),
      release=beat([("fireball", 0, 0.9, 0.7), ("blast", 0, 0.7, 0.6), ("ignite", 0, 0.9, 0.5), ("mace_air", 0, 0.5, 0.8), ("blaze", 1, 0.7, 0.8, 0.03, 16), ("crackle_fire", 3, 0.9, 0.7),
                    ("crackle_fire", 8, 0.7, 0.8), ("lava_pop", 5, 0.6, 0.7), ("fizz", 10, 0.4, 0.6)], surface=True, duck=3,
                   alt=[("fireball", 0, 0.9, 0.8), ("large_blast", 0, 0.5, 0.7), ("fire_hurt", 0, 0.7, 0.5), ("mace_ground", 0, 0.5, 0.9), ("blaze", 1, 0.7, 0.7, 0.03, 16),
                        ("crackle_fire", 2, 0.9, 0.8), ("lava_pop", 6, 0.6, 0.8)]),
      echo=[("ignite", 0, 0.7, 0.9), ("fizz", 0, 0.5, 0.9), ("crackle_fire", 2, 0.6, 1.0)])
# Hoarfrost Lances: ice growing out of the air by your ear (release); a thrown spear that breaks like ice (impact); the crown's ring.
voice("hoarfrost_lances",
      release=[("snow_scoop", 0, 0.8, 0.7), ("creak_freeze", 0, 0.9, 0.7), ("freeze", 2, 0.7, 0.9), ("dripstone", 3, 0.6, 1.1), ("dripstone", 5, 0.6, 1.2), ("dripstone", 7, 0.6, 1.3),
               ("breeze_air", 0, 0.4, 1.1, 0.03, 20)],
      impact=sets([("trident_throw", 0, 0.7, 1.2), ("pierce", 1, 0.9, 0.9), ("glass", 1, 0.45, 0.7), ("powder_snow", 1, 0.8, 0.8), ("freeze", 2, 0.6, 0.8)],
                  [("bow", 0, 0.6, 0.8), ("bowhit", 1, 0.8, 0.7), ("dripstone", 1, 0.8, 0.8), ("powder_snow", 1, 0.8, 0.9), ("creak_freeze", 2, 0.5, 0.9)]),
      extra_a=[("freeze", 0, 1.0, 0.6), ("creak_unfreeze", 0, 0.9, 0.5), ("glass", 0, 0.5, 0.5), ("dripstone", 1, 0.8, 0.7), ("dripstone", 3, 0.8, 0.8), ("wind_burst", 0, 0.4, 0.6)])
# Updraft: the air drawn in, then let go upward, and it keeps going for as long as the column stands; the gate's throw.
voice("updraft",
      release=[("breeze_inhale", 0, 0.8, 0.8), ("breeze_jump", 2, 0.9, 0.7), ("wind_burst", 3, 0.6, 0.7), ("swoop", 3, 0.6, 0.6), ("leaves", 4, 0.6, 0.8), ("grass", 5, 0.5, 0.8),
               ("elytra", 4, 0.5, 0.7, 0.02, 110), ("breeze_air", 30, 0.5, 0.8), ("breeze_air", 70, 0.45, 0.85)],
      extra_a=[("breeze_shoot", 0, 0.8, 0.9), ("wings", 0, 0.7, 1.0), ("swoop", 0, 0.7, 0.9)])
# Backdraft: the fires rushing in to you (gather), then one long breath of a furnace (release); a dragon under it at level 5.
voice("backdraft",
      gather=[("breath", 0, 0.9, 0.6), ("fire", 0, 0.7, 1.4, 0.03, 8), ("fizz", 0, 0.6, 0.8), ("swoop", 0, 0.5, 0.7), ("ghast_charge", 0, 0.35, 0.7, 0.02, 10)],
      release=beat([("fireball", 0, 1.0, 0.6), ("blaze", 0, 0.9, 0.6, 0.03, 22), ("furnace_roar", 0, 0.9, 0.7, 0.03, 30), ("large_blast", 0, 0.5, 0.6), ("breeze_shoot", 0, 0.5, 0.6),
                    ("crackle_fire", 4, 0.9, 0.7), ("crackle_fire", 10, 0.8, 0.8), ("crackle_fire", 18, 0.6, 0.9), ("fire", 2, 0.7, 0.8, 0.0, 26)], duck=4,
                   alt=[("fireball", 0, 1.0, 0.7), ("blaze", 0, 0.9, 0.7, 0.03, 22), ("furnace_roar", 1, 0.9, 0.6, 0.03, 30), ("blast", 0, 0.6, 0.5), ("ravager_roar", 0, 0.3, 0.6, 0.03, 18),
                        ("crackle_fire", 5, 0.9, 0.8), ("crackle_fire", 12, 0.7, 0.9), ("fire", 2, 0.7, 0.7, 0.0, 26)]),
      level5=[("dragon_growl", 0, 0.6, 0.7), ("furnace_roar", 0, 0.8, 0.5, 0.02, 34)])
# Thornwake: the ground turning over (release); wood and vine closing on a body (impact); the great root (extra).
voice("thornwake",
      release=beat([("mace_ground", 0, 0.45, 0.8, 0.02, 16), ("rooted_dirt", 0, 0.9, 0.7), ("roots", 1, 0.9, 0.6), ("creak_sway", 0, 0.8, 0.6), ("grass", 2, 0.7, 0.7), ("leaves", 3, 0.6, 0.8),
                    ("gravel", 2, 0.6, 0.7)], surface=True,
                   alt=[("warden_dig", 0, 0.35, 1.2, 0.02, 18), ("rooted_dirt", 0, 0.9, 0.8), ("mangrove", 1, 0.9, 0.6), ("creak_sway", 1, 0.8, 0.5), ("moss", 2, 0.7, 0.7), ("leaves", 3, 0.6, 0.9)]),
      impact=sets([("mangrove", 0, 0.9, 0.7), ("vine", 0, 0.8, 0.7), ("wood", 0, 0.7, 0.6), ("creak_freeze", 1, 0.6, 0.8), ("leaves", 1, 0.5, 1.0)],
                  [("roots", 0, 0.9, 0.7), ("wood_hit", 0, 0.4, 0.6), ("vine", 0, 0.8, 0.8), ("creak_sway", 1, 0.6, 0.9), ("leash", 0, 0.6, 0.6)]),
      extra_a=[("warden_emerge", 0, 0.4, 1.1, 0.02, 30), ("wood_break", 0, 0.7, 0.5), ("mangrove", 0, 1.0, 0.5), ("mangrove", 3, 0.9, 0.6), ("creak_unfreeze", 2, 0.9, 0.5), ("rooted_dirt", 1, 0.9, 0.6),
               ("leaves", 6, 0.7, 0.8), ("leaves", 12, 0.5, 0.9)])


# ---- building ------------------------------------------------------------------------------------------------------------

def resolve(sources):
    """A palette entry's sources -> the game's file names (sorted), dropping the "-name" takes."""
    idx = index()
    out, drop = [], set()
    for s in sources:
        if s.startswith("-"):
            drop.add(s[1:])
        elif s.endswith("#"):
            found = sorted(k for k in idx if re.fullmatch(re.escape(s[:-1]) + r"_?\d+", k))
            if not found:
                raise SystemExit(f"no recordings named {s}")
            out += found
        else:
            if s not in idx:
                raise SystemExit(f"no recording named {s}")
            out.append(s)
    out = [o for o in out if o not in drop]
    for o in out:
        if any(o.startswith(t) for t in TONAL):
            raise SystemExit(f"{o} is a tonal source: not for power voices")
    if not out:
        raise SystemExit(f"{sources}: nothing left")
    return out


def check_scores():
    used = set()
    for power, row in VOICES.items():
        for b, sc in row.items():
            for st in sc["sets"]:
                if len(st) > 12:
                    raise SystemExit(f"{power}.{b}: {len(st)} layers (12 at most)")
                for layer in st:
                    name, tick, vol, pitch = layer[:4]
                    if name not in PALETTE:
                        raise SystemExit(f"{power}.{b}: no palette event {name}")
                    if not (0 < vol <= 1.0) or not (0.5 <= pitch <= 2.0) or tick < 0:
                        raise SystemExit(f"{power}.{b}: {layer} out of range")
                    used.add(name)
    return used


def document():
    out = OrderedDict(schemaVersion=1, __comment="GENERATED by tools/art/specs/power_voices.py: edit the scores there, not this file.", voices=OrderedDict())
    for power, row in VOICES.items():
        r = OrderedDict()
        for b, sc in row.items():
            entry = OrderedDict(sets=[[["voice." + l[0], int(l[1]), round(float(l[2]), 3), round(float(l[3]), 3), round(float(l[4]) if len(l) > 4 else 0.04, 3),
                                        int(l[5]) if len(l) > 5 else 0] for l in st] for st in sc["sets"]])
            if sc["surface"]:
                entry["surface"] = True
            if sc["duck"]:
                entry["duck"] = int(sc["duck"])
            if sc["cut"]:
                entry["cut"] = True
            r[b] = entry
        out["voices"][power] = r
    return out


# What the subtitles say when a power sounds (one line a power: a score is many recordings, and none of their own subtitles
# would be true). A power not listed says "<Name> is cast".
SUBTITLES = {
    "ground_slam": "Ground Slam lands", "leap": "Leap", "haymaker": "Haymaker lands", "terrain_rip": "Ground is ripped up", "shockwave": "Shockwave rolls out",
    "brace": "Brace sets", "thunderclap": "Thunderclap", "dash": "Dash", "flow": "Time slows", "afterimage": "Afterimage splits off",
    "lightning_step": "Lightning Step cracks", "hover": "Hover lifts", "flight": "Flight takes off", "gravity_well": "Gravity Well pulls", "telekinesis": "Telekinesis grips",
    "meteor_dive": "Meteor Dive lands", "gravity_flip": "Gravity flips", "beam": "Beam burns", "nova": "Nova bursts", "barrier": "Barrier rises",
    "charged_shot": "Charged Shot fires", "overload": "Overload bursts", "prism_lance": "Prism Lance burns", "shadow_step": "Shadow Step", "cloak": "Cloak closes",
    "echo_swap": "Echo Swap", "legion": "Legion rises", "dominate": "Dominate takes hold", "read_intent": "Read Intent listens", "decoy": "Decoy is thrown",
    "foresight": "Foresight opens", "wall": "Wall is set", "ramp": "Ramp is set", "floor": "Floor is set", "cone": "Cone is set", "rampart": "Rampart rises",
    "spire": "Spire erupts", "collapse": "Collapse falls", "citadel": "Citadel rises", "grapple": "Grapple fires", "jet_burst": "Jets fire", "drone": "Drone launches",
    "turret": "Turret deploys", "railgun": "Railgun fires", "nanite_skin": "Nanite Skin forms", "drone_swarm": "Drone Swarm launches", "spirit_palm": "Spirit Palm strikes",
    "mend": "Mend heals", "third_eye": "Third Eye opens", "cord_lash": "Cord Lash cracks", "bulwark": "Bulwark rises", "hundred_palms": "Hundred Palms strike",
    "void_maw": "Void Maw opens", "waking_dream": "Waking Dream shapes", "storm_call": "Storm Call strikes", "ember_step": "Ember Step burns", "frost_nova": "Frost Nova freezes",
    "ghost_step": "Ghost Step", "twin_strike": "Twin Strike echoes", "brand": "Brand burns", "hoarfrost_lances": "Hoarfrost Lances fly", "updraft": "Updraft rises",
    "backdraft": "Backdraft roars", "thornwake": "Roots wake",
}


def names():
    import power_ladder_table as LT
    return {p["id"]: p["name"] for p in LT.L}


def subtitle_lang():
    nm = names()
    out = OrderedDict()
    for power in VOICES:
        out["subtitles.awaken.voice." + power] = SUBTITLES.get(power) or (nm.get(power, power.replace("_", " ").title()) + " is cast")
    return out


def sound_events(used):
    ev = OrderedDict()
    # the subtitle's carrier: one event a power, played too quietly to hear when subtitles are on (powers.client.fx.PowerVoice)
    for power in VOICES:
        ev["voice.say." + power] = OrderedDict(subtitle="subtitles.awaken.voice." + power,
                                                sounds=[OrderedDict(name="minecraft:random/click", volume=0.05, attenuation_distance=24)])
    for name in sorted(used):
        sources, distance = PALETTE[name]
        ev["voice." + name] = OrderedDict(sounds=[OrderedDict(name="minecraft:" + f, attenuation_distance=distance) for f in resolve(sources)])
    return ev


def write():
    used = check_scores()
    events = sound_events(used)
    os.makedirs(os.path.dirname(OUT_VOICES), exist_ok=True)
    with open(OUT_VOICES, "w") as fh:
        json.dump(document(), fh, separators=(",", ":"))
        fh.write("\n")
    sounds = json.load(open(OUT_SOUNDS), object_pairs_hook=OrderedDict)
    for k in [k for k in sounds if k.startswith("voice.")]:
        del sounds[k]
    sounds.update(events)
    with open(OUT_SOUNDS, "w") as fh:
        json.dump(sounds, fh, indent=2)
        fh.write("\n")
    os.makedirs(os.path.dirname(OUT_LANG), exist_ok=True)
    with open(OUT_LANG, "w") as fh:
        json.dump(subtitle_lang(), fh, indent=1, ensure_ascii=False)
        fh.write("\n")
    return used, events


def build(out):
    """build_all.py entry: the outputs are committed, and the asset index is only on a machine that has run the game."""
    if os.path.isdir(os.path.join(ASSETS, "indexes")):
        write()


def main(argv):
    cmd = argv[0] if argv else "build"
    if cmd == "build":
        used, events = write()
        layers = sum(len(st) for row in VOICES.values() for sc in row.values() for st in sc["sets"])
        files = sum(len(e["sounds"]) for e in events.values())
        print(f"{len(VOICES)} powers, {sum(len(r) for r in VOICES.values())} beats, {layers} layers, {len(events)} events over {files} recordings")
        return 0
    if cmd == "check":
        used = check_scores()
        sound_events(used)
        sys.path.insert(0, HERE)
        import ding_check as D
        idx = index()
        for name in sorted(used):
            for f in resolve(PALETTE[name][0]):
                h = idx[f]
                r = D.check(D.decode(os.path.join(ASSETS, "objects", h[:2], h)))
                if r["ding"]:
                    print(f"  ring? {name:16s} {f}  {r['hz']:.0f} Hz")
        print("unused palette events:", sorted(set(PALETTE) - used))
        return 0
    raise SystemExit(__doc__)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
