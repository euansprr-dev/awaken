"""The sound audit, part 4: the wild's creatures (SPEC §66.6). Every voice was fauna_sounds.py's synthesis: a buzz
through formant filters for a growl or a bleat, noise bursts for a hiss, sine thuds for a step. Each creature now
speaks with the nearest real animal the game has recorded, re-pitched to its size: a ram with the goat's voice, the
honey bear with the polar bear's, the toads with the frog's, a thing of living wood with the Creaking's. Where the
game has no near animal (an owl, a marmot, a ray) the row says so and waits for Euan's ear.

One line a creature: what it is, then its idle voice, hurt, death and step (a flier's step is its wingbeat), a beast's
roar, and sometimes a second idle to choose from. Each recipe is a list of E(...) layers.
"""
from audit_kit import E, F, group, row

G = group("fauna", "The wild's creatures",
          "Idle voices, hurt, death, steps and the beasts' roars. Each creature takes the nearest real animal the game has.")

WHERE = {"ambient": "its idle voice (mobs.MobSounds, the mob's ambient sound)", "hurt": "when it is hurt", "death": "when it dies",
         "step": "each step (a flier: each wingbeat)", "roar": "a named beast's signature move (mobs.entity.FaunaMob)"}
SAY = {"ambient": "idles", "hurt": "is hurt", "death": "dies", "step": "steps", "roar": "roars"}


def creature(id, title, what, ambient, hurt, death, step, roar=None, alt=None, extra=()):
    out = []
    parts = [("ambient", ambient), ("hurt", hurt), ("death", death), ("step", step)] + ([("roar", roar)] if roar else []) + list(extra)
    for verb, recipe in parts:
        if isinstance(recipe, tuple):  # (say, layers) for an extra
            say, layers = recipe
        else:
            say, layers = what, recipe
        picks = [(say, layers)]
        if verb == "ambient" and alt:
            picks.append(alt)
        out.append(row(f"fauna.{id}.{verb}", "fauna", G, f"{title} {SAY.get(verb, verb)}",
                       "mobs: " + WHERE.get(verb, "the Seep Heart's fight (mobs.entity.SeepHeart)"), "ours, synthesised (tools/art/specs/fauna_sounds.py)",
                       "Synthetic voice: a buzz through filters, noise and sine thuds", picks, scene="twice" if verb in ("ambient", "roar") else "steps" if verb == "step" else "alone"))
    return out


def mob(m, pitch, vol=1.0, verbs=("ambient", "hurt", "death")):
    """A creature that takes one of the game's animals whole, re-pitched."""
    return [[E(f"entity.{m}.{v}", vol, pitch)] for v in verbs]


FLY = "entity.parrot.fly"
ROWS = []
for c in [
    # ---- Greenwild --------------------------------------------------------------------------------------------------------
    creature("lumen_moth", "A Lumen Moth", "A small wingbeat (a parrot's, raised) and a bat's small voice, raised and quiet",
             [E(FLY, 1.0, 1.5)], [E("entity.bat.hurt", 0.15, 1.4)], [E("entity.bat.death", 0.15, 1.4)], [E(FLY, 0.9, 1.7)]),
    creature("bloom_sprite", "A Bloom Sprite", "The allay's own voice, a little raised", [E("entity.allay.ambient_without_item", 0.5, 1.3)],
             [E("entity.allay.hurt", 0.6, 1.3)], [E("entity.allay.death", 0.6, 1.3)], [E(FLY, 0.8, 1.8)]),
    creature("mossback", "A Mossback", "The turtle's voice, lowered", [E("entity.turtle.ambient_land", 1.0, 0.8)], [E("entity.turtle.hurt", 1.0, 0.8)],
             [E("entity.turtle.death", 1.0, 0.8)], [E("entity.turtle.shamble", 0.8, 0.8)]),
    creature("gloomstalker", "A Gloomstalker", "A fox's growl, lowered", [E("entity.fox.aggro", 0.5, 0.8)], [E("entity.fox.hurt", 0.7, 0.8)],
             [E("entity.fox.death", 0.7, 0.8)], [E("entity.wolf.step", 0.4, 1.0)], alt=("A wolf's growl", [E("entity.wolf.growl", 0.6, 0.9)])),
    creature("briar_sow", "The Briar Sow", "The hoglin's voice: a great boar", [E("entity.hoglin.ambient", 0.8, 0.9)], [E("entity.hoglin.hurt", 0.9, 0.9)],
             [E("entity.hoglin.death", 0.9, 0.9)], [E("entity.hoglin.step", 0.8, 0.9)],
             roar=[E("entity.hoglin.angry", 1.0, 0.8), E("entity.ravager.roar", 0.3, 0.9)]),
    # ---- Sunscorch --------------------------------------------------------------------------------------------------------
    creature("glass_scarab", "A Glass Scarab", "A silverfish's dry chitter, raised", [E("entity.silverfish.ambient", 0.4, 1.3)], [E("entity.silverfish.hurt", 0.5, 1.3)],
             [E("entity.silverfish.death", 0.5, 1.3)], [E("entity.silverfish.step", 0.5, 1.2)]),
    creature("dune_ray", "A Dune Ray", "An axolotl's soft chirp, lowered (the game has no ray)", [E("entity.axolotl.idle_air", 0.6, 0.8)], [E("entity.axolotl.hurt", 0.7, 0.8)],
             [E("entity.axolotl.death", 0.7, 0.8)], [E("block.sand.step", 0.4, 1.1)], alt=("A dolphin's whistle, lowered", [E("entity.dolphin.ambient", 0.4, 0.8)])),
    creature("dune_wyrm", "The Dune Wyrm", "The dragon's growl, raised to a lesser wyrm", [E("entity.ender_dragon.growl", 0.4, 1.2)], [E("entity.ender_dragon.hurt", 0.5, 1.2)],
             [E("entity.ravager.death", 0.9, 0.7)], [E("block.sand.break", 0.6, 0.7)], roar=[E("entity.ender_dragon.growl", 0.8, 0.9)]),
    # ---- Frostwild --------------------------------------------------------------------------------------------------------
    creature("frostfinch", "A Frostfinch", "A parrot's chirp and cry, raised", [E("entity.parrot.ambient", 0.5, 1.6)], [E("entity.parrot.hurt", 0.5, 1.3)],
             [E("entity.parrot.death", 0.5, 1.3)], [E(FLY, 1.0, 1.5)]),
    creature("frost_stag", "A Frost Stag", "A goat's bleat, lowered", [E("entity.goat.ambient", 0.8, 0.75)], [E("entity.goat.hurt", 0.9, 0.75)],
             [E("entity.goat.death", 0.9, 0.75)], [E("entity.goat.step", 0.8, 0.9)], alt=("A camel's groan, raised", [E("entity.camel.ambient", 0.7, 1.2)])),
    creature("wisp_hound", "A Wisp Hound", "The big wolf's voice", [E("entity.wolf_big.ambient", 0.8, 0.9)], [E("entity.wolf.hurt", 0.9, 0.9)],
             [E("entity.wolf.death", 0.9, 0.9)], [E("entity.wolf.step", 0.5, 1.0)], alt=("A wolf's whine, lowered", [E("entity.wolf.whine", 0.8, 0.8)])),
    creature("old_antler", "Old Antler", "A camel's groan, lowered: an old stag", [E("entity.camel.ambient", 0.9, 0.8)], [E("entity.camel.hurt", 0.9, 0.8)],
             [E("entity.camel.death", 0.9, 0.8)], [E("entity.camel.step", 0.8, 0.9)], roar=[E("entity.goat.screaming.ambient", 0.9, 0.7)]),
    # ---- Mire -------------------------------------------------------------------------------------------------------------
    creature("bogwick", "A Bogwick", "A bee at a flower, raised and quiet: a small hum", [E("entity.bee.pollinate", 0.35, 1.4)], [E("entity.bee.hurt", 0.4, 1.4)],
             [E("block.candle.extinguish", 0.8, 1.0)], [E("entity.bee.pollinate", 0.25, 1.6)], alt=("The game's own night insects", [E("block.firefly_bush.idle", 2.0, 1.0)])),
    creature("mudskipper", "A Mudskipper", "An axolotl's chirp, raised", [E("entity.axolotl.idle_air", 0.6, 1.2)], [E("entity.axolotl.hurt", 0.7, 1.2)],
             [E("entity.axolotl.death", 0.7, 1.2)], [E("block.mud.step", 0.5, 1.3)]),
    creature("bog_toad", "A Bog Toad", "The frog's voice, lowered", [E("entity.frog.ambient", 1.0, 0.7)], [E("entity.frog.hurt", 1.0, 0.7)],
             [E("entity.frog.death", 1.0, 0.7)], [E("entity.frog.step", 0.9, 0.8)]),
    creature("mire_mother", "The Mire Mother", "The frog's voice as low as the game goes: a toad the size of a house", [E("entity.frog.ambient", 1.0, 0.56)],
             [E("entity.frog.hurt", 1.0, 0.56)], [E("entity.frog.death", 1.0, 0.56)], [E("block.mud.step", 1.0, 0.6)],
             roar=[E("entity.frog.ambient", 1.0, 0.56), E("entity.ravager.roar", 0.35, 0.6)]),
    creature("dart_frog", "A Dart Frog", "The frog's voice, raised", [E("entity.frog.ambient", 0.6, 1.5)], [E("entity.frog.hurt", 0.7, 1.5)],
             [E("entity.frog.death", 0.7, 1.5)], [E("entity.frog.step", 0.8, 1.4)]),
    # ---- Canopy -----------------------------------------------------------------------------------------------------------
    creature("crested_basilisk", "A Crested Basilisk", "A spider's chitter raised, and a cat's hiss when hurt", [E("entity.spider.ambient", 0.5, 1.3)],
             [E("entity.cat.hiss", 0.5, 0.9)], [E("entity.spider.death", 0.6, 1.2)], [E("entity.armadillo.step", 0.6, 1.2)]),
    creature("canopy_prowler", "A Canopy Prowler", "The ocelot's voice, lowered: a bigger cat", [E("entity.ocelot.ambient", 0.7, 0.6)], [E("entity.ocelot.hurt", 0.8, 0.7)],
             [E("entity.ocelot.death", 0.8, 0.7)], [E("entity.wolf.step", 0.4, 0.9)], alt=("A panda's growl", [E("entity.panda.aggressive_ambient", 0.7, 0.9)])),
    creature("plumed_coil", "The Plumed Coil", "Bones rattling (a skeleton's), raised: its plumes; a cat's hiss slowed when hurt", [E("entity.skeleton.ambient", 0.5, 1.3)],
             [E("entity.cat.hiss", 0.6, 0.6)], [E("entity.spider.death", 0.8, 0.7)], [E("block.gravel.step", 0.5, 0.8)],
             roar=[E("entity.skeleton.ambient", 0.8, 1.5), E("entity.skeleton.ambient", 0.8, 1.6, at=100), E("entity.cat.hiss", 0.6, 0.55, at=150), E("entity.skeleton.ambient", 0.7, 1.5, at=250)]),
    creature("gloam_snail", "A Gloam Snail", "A small slime's squelch", [E("entity.slime.squish_small", 0.5, 0.8)], [E("entity.slime.hurt_small", 0.6, 0.8)],
             [E("entity.slime.death_small", 0.6, 0.8)], [E("block.honey_block.step", 0.3, 1.2)]),
    # ---- Duskwood, Blossom ------------------------------------------------------------------------------------------------
    creature("hollow_owl", "A Hollow Owl", "A frog's deep croak, lowered: a soft hoot of a kind (the game has no owl); a parrot's cry lowered when hurt",
             [F(["mob/frog/idle5", "mob/frog/idle6", "mob/frog/idle7"], 1.0, 0.8)], [E("entity.parrot.hurt", 0.6, 0.75)], [E("entity.parrot.death", 0.6, 0.65)], [E(FLY, 1.0, 0.7)],
             alt=("A parrot's whistle, well lowered", [F(["mob/parrot/idle3", "mob/parrot/idle6"], 0.5, 0.55)])),
    creature("thornling", "A Thornling", "The Creaking's own voice, raised: living wood", [E("entity.creaking.ambient", 0.5, 1.3)], [E("block.wood.break", 0.6, 1.3)],
             [E("entity.creaking.death", 0.7, 1.3)], [E("entity.creaking.step", 0.4, 1.4)]),
    creature("weaver_queen", "The Weaver Queen", "The spider's voice, lowered: a great one", [E("entity.spider.ambient", 0.8, 0.7)], [E("entity.spider.hurt", 0.9, 0.7)],
             [E("entity.spider.death", 0.9, 0.6)], [E("entity.spider.step", 0.8, 0.7)], roar=[E("entity.spider.hurt", 1.0, 0.5), E("entity.fox.screech", 0.5, 0.8)]),
    creature("petalwing", "A Petalwing", "A small wingbeat (a parrot's, raised)", [E(FLY, 0.8, 1.6)], [E("entity.bat.hurt", 0.12, 1.6)], [E("entity.bat.death", 0.12, 1.6)], [E(FLY, 0.7, 1.8)]),
    creature("honey_bear", "A Honey Bear", "The polar bear's voice, a little raised", [E("entity.polar_bear.ambient", 0.9, 1.1)], [E("entity.polar_bear.hurt", 0.9, 1.1)],
             [E("entity.polar_bear.death", 0.9, 1.1)], [E("entity.polar_bear.step", 0.8, 1.0)]),
    creature("empress_moth", "The Empress Moth", "A phantom's wingbeat, raised: great soft wings", [E("entity.phantom.flap", 0.6, 1.6)], [E("entity.phantom.hurt", 0.5, 1.4)],
             [E("entity.phantom.death", 0.6, 1.3)], [E("entity.phantom.flap", 0.5, 1.8)], roar=[E("entity.phantom.ambient", 0.7, 1.3), E("entity.phantom.flap", 0.6, 1.4)]),
    # ---- Crag, Spore ------------------------------------------------------------------------------------------------------
    creature("crag_ram", "A Crag Ram", "The goat's own voice", [E("entity.goat.ambient", 0.8, 0.9)], [E("entity.goat.hurt", 0.9, 0.9)],
             [E("entity.goat.death", 0.9, 0.9)], [E("entity.goat.step", 0.8, 1.0)]),
    creature("scree_marmot", "A Scree Marmot", "A parrot's whistle (a marmot whistles; the game has none)", [F(["mob/parrot/idle4"], 0.5, 1.3)], [E("entity.fox.hurt", 0.4, 1.6)],
             [E("entity.fox.death", 0.4, 1.6)], [E("entity.rabbit.jump", 0.8, 1.0)], alt=("A fox's yip, raised", [E("entity.fox.ambient", 0.4, 1.6)])),
    creature("peak_kite", "A Peak Kite", "A parrot's cry, lowered: a bird of prey (the game has none)", [E("entity.parrot.ambient", 0.6, 0.7)], [E("entity.parrot.hurt", 0.6, 0.7)],
             [E("entity.parrot.death", 0.6, 0.65)], [E(FLY, 1.0, 0.75)], alt=("A fox's scream, raised and quiet", [E("entity.fox.screech", 0.25, 1.5)])),
    creature("stonehorn", "The Stonehorn", "The goat's voice dropped very low: a ram the size of a boulder", [E("entity.goat.ambient", 0.9, 0.6)], [E("entity.goat.hurt", 1.0, 0.6)],
             [E("entity.goat.death", 1.0, 0.6)], [E("entity.ravager.step", 0.6, 0.9)], roar=[E("entity.goat.screaming.prepare_ram", 1.0, 0.55), E("entity.ravager.roar", 0.4, 0.8)]),
    creature("shroomlet", "A Shroomlet", "A puffer fish puffing up, raised: a puff of spores", [E("entity.puffer_fish.blow_up", 0.4, 1.3)], [E("entity.axolotl.hurt", 0.5, 1.4)],
             [E("entity.puffer_fish.blow_out", 0.5, 1.2)], [E("block.fungus.step", 0.5, 1.3)]),
    creature("sporeback_snail", "A Sporeback Snail", "A small slime's squelch, lowered", [E("entity.slime.squish_small", 0.5, 0.7)], [E("entity.slime.hurt_small", 0.6, 0.7)],
             [E("entity.slime.death_small", 0.6, 0.7)], [E("block.honey_block.step", 0.3, 1.0)]),
    creature("mycelord", "The Mycelord", "The Creaking's voice dropped very low: a mushroom as tall as a tree", [E("entity.creaking.ambient", 0.8, 0.6)],
             [E("block.wood.break", 0.8, 0.6), E("entity.creaking.sway", 0.6, 0.6)], [E("entity.creaking.death", 1.0, 0.6)], [E("entity.creaking.step", 0.8, 0.6)],
             roar=[E("entity.creaking.activate", 0.9, 0.55), E("block.wood.break", 0.6, 0.5, at=200)]),
    # ---- water ------------------------------------------------------------------------------------------------------------
    creature("glimmerfin", "A Glimmerfin", "A fish's flick in the water", [E("entity.fish.swim", 0.3, 1.2)], [E("entity.cod.hurt", 0.6, 1.2)], [E("entity.cod.death", 0.6, 1.2)], [E("entity.fish.swim", 0.2, 1.3)]),
    creature("breathbell", "A Breathbell Jelly", "A bubble popping", [E("block.bubble_column.bubble_pop", 0.4, 1.2)], [E("entity.squid.hurt", 0.5, 1.3)], [E("entity.squid.death", 0.5, 1.3)],
             [E("block.bubble_column.bubble_pop", 0.3, 1.4)]),
    creature("brook_otter", "A Brook Otter", "An axolotl's chirp (an otter chirps; the game has none)", [E("entity.axolotl.idle_air", 0.6, 1.0)], [E("entity.fox.hurt", 0.5, 1.4)],
             [E("entity.fox.death", 0.5, 1.4)], [E("entity.wolf.step", 0.3, 1.3)], alt=("A fox's yip, raised", [E("entity.fox.ambient", 0.5, 1.4)])),
    creature("tidesteed", "A Tidesteed", "A dolphin's voice under water", [E("entity.dolphin.ambient_water", 0.6, 0.9)], [E("entity.dolphin.hurt", 0.7, 0.9)],
             [E("entity.dolphin.death", 0.7, 0.9)], [E("entity.dolphin.swim", 0.4, 1.0)]),
    creature("lantern_maw", "The Lantern Maw", "The elder guardian's voice, lowered: an angler as big as a boat", [E("entity.elder_guardian.ambient", 0.6, 0.7)],
             [E("entity.elder_guardian.hurt", 0.8, 0.8)], [E("entity.elder_guardian.death", 0.8, 0.8)], [E("entity.generic.swim", 0.4, 0.6)],
             roar=[E("entity.elder_guardian.ambient", 1.0, 0.55), E("entity.ravager.roar", 0.3, 0.6)]),
    # ---- caves ------------------------------------------------------------------------------------------------------------
    creature("wickling", "A Wickling", "An axolotl's chirp: a cave salamander", [E("entity.axolotl.idle_air", 0.6, 1.1)], [E("entity.axolotl.hurt", 0.7, 1.1)],
             [E("entity.axolotl.death", 0.7, 1.1), E("block.candle.extinguish", 0.6, 1.0, at=100)], [E("entity.frog.step", 0.4, 1.2)]),
    creature("drip_lurker", "A Drip Lurker", "The game's own drip, then dripstone knocked when hurt", [E("block.pointed_dripstone.drip_water", 2.0, 0.8)],
             [E("block.dripstone_block.hit", 1.0, 0.8), E("entity.silverfish.hurt", 0.4, 0.7)], [E("block.dripstone_block.break", 1.0, 0.8)], [E("block.dripstone_block.step", 0.5, 1.0)]),
    creature("hushling", "A Hushling", "The warden's tendrils clicking, raised: it listens", [E("entity.warden.tendril_clicks", 0.5, 1.4)], [E("entity.bat.hurt", 0.2, 0.6)],
             [E("entity.bat.death", 0.25, 0.6)], [E("block.sculk.step", 0.5, 1.0)]),
    creature("chime_cricket", "A Chime Cricket", "Amethyst chiming, raised and quiet, over the night insects", [E("block.amethyst_block.chime", 0.3, 1.8), E("block.firefly_bush.idle", 2.0, 1.2)],
             [E("entity.silverfish.hurt", 0.4, 1.5)], [E("entity.silverfish.death", 0.4, 1.5)], [E("entity.silverfish.step", 0.4, 1.5)]),
    creature("thousandfoot", "The Thousandfoot", "The spider's voice dropped low, over a skitter of many feet", [E("entity.spider.ambient", 0.8, 0.6), E("entity.silverfish.step", 0.5, 0.7)],
             [E("entity.spider.hurt", 0.9, 0.6)], [E("entity.spider.death", 1.0, 0.55)], [E("entity.silverfish.step", 0.6, 0.7), E("block.amethyst_block.step", 0.3, 0.8)],
             roar=[E("entity.spider.ambient", 1.0, 0.5), E("entity.silverfish.ambient", 0.6, 0.5, at=100)]),
    creature("seep_heart", "The Seep Heart", "A conduit's hum, slowed: Deepglass grown through the rock", [E("block.conduit.ambient.short", 0.5, 0.6)],
             [E("block.amethyst_block.break", 0.7, 0.6), E("block.stone.hit", 0.8, 0.7)],
             [E("block.glass.break", 1.0, 0.5), E("block.conduit.deactivate", 0.8, 0.6), E("entity.warden.death", 0.5, 0.8, at=150)], [E("block.sculk.step", 0.8, 0.8)],
             roar=[E("entity.warden.roar", 0.7, 0.9)],
             extra=[("beat", ("The warden's heartbeat", [E("entity.warden.heartbeat", 0.9, 0.9)])), ("rumble", ("Thunder, slowed and far", [E("entity.lightning_bolt.thunder", 0.3, 0.5)])),
                    ("roots", ("Roots breaking, slowed, as sculk spreads", [E("block.roots.break", 0.9, 0.6), E("block.sculk.spread", 0.7, 0.8, at=100)])),
                    ("shards", ("Glass and an amethyst cluster breaking", [E("block.glass.break", 0.8, 0.8), E("block.amethyst_cluster.break", 0.7, 0.7)])),
                    ("pulse", ("The warden's sonic boom, quieter and lower", [E("entity.warden.sonic_boom", 0.5, 0.8)])),
                    ("call", ("A sculk shrieker, lowered", [E("block.sculk_shrieker.shriek", 0.5, 0.7)]))]),
]:
    ROWS += c

ROWS.append(row("fauna.mount_whistle", "fauna", G, "You whistle for your mount", "world.fauna.Mounts: calling a bonded mount",
                "ours, synthesised (tools/art/specs/fauna_sounds.py)", "A synthetic whistle (a sine glide)", [
                    ("A parrot's whistle, twice, the second higher", [F(["mob/parrot/idle4"], 0.6, 1.0), F(["mob/parrot/idle4"], 0.6, 1.25, at=200)]),
                    ("One parrot whistle", [F(["mob/parrot/idle4"], 0.6, 1.1)]),
                    ("A note block's flute, two notes", [E("block.note_block.flute", 0.6, 1.2), E("block.note_block.flute", 0.6, 1.6, at=200)])]))
