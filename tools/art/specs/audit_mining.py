"""The sound audit, part 2: mining (SPEC §66.4). Euan, 11 Nov 2026: "I hate the ding ding ding when mining ore, even
vanilla sounds better".

The dings were ours: every bite into an ore played that ore's synthesised note (iron, lapis, diamond and emerald were 80
to 95% one held tone, by docs/powers-aaa/taster/ding_check.py), and a hit on the seam added a chime that climbed a step
with each one in a row. All of it was audio_delve.py's synthesis. Now a bite is Minecraft's own stone and mineral
recordings, none of them a note (each was put through the same check: the game's copper, iron, chain and amethyst hits
do ring, so they are not used on a bite), quiet under the game's own digging sound; a hit on the seam is the game's
critical-hit crack. The Forged Pick's own voice (SPEC §49, already the game's recordings) is not touched.
"""
from audit_kit import E, F, group, pick, row

G = group("mining", "Mining",
          "What you hear with a pick in ore. Nothing here is a note any more: stone, grit and metal, the game's own.")

WAS = "ours, synthesised (tools/art/specs/audio_delve.py)"
BITE = ("delve.client.StrikeFeel.bite (client only): every fourth tick while you dig this ore, under the game's own digging sound, "
        "and louder on the blow that breaks it")
NOTHING = pick("Nothing: the game's own digging sound alone (a change in code)", [], code=True)


def ore(id, name, why, first, second):
    return row("delve.strike." + id, "delve", G, name, BITE, WAS + ": struck sine modes and noise", why, scene="dig",
               picks=[first, second, NOTHING])


ROWS = [
    ore("coal", "The pick bites coal", "Synthetic thock",
        ("Tuff knocked, lowered: soft and crumbly", [E("block.tuff.hit", 0.6, 0.85)]),
        ("Basalt knocked, lowered", [E("block.basalt.hit", 0.4, 0.8)])),
    ore("copper", "The pick bites copper", "Synthetic, with a held tone at 950 Hz",
        ("A dense metal knock (the heavy core's), a little raised: dull, no ring", [E("block.heavy_core.hit", 0.6, 1.15)]),
        ("Dripstone knocked", [E("block.dripstone_block.hit", 0.5, 1.0)])),
    ore("iron", "The pick bites iron", "A ding: 92% of the sound one tone at 1,174 Hz",
        ("The same dense metal knock, lower and heavier", [E("block.heavy_core.hit", 0.7, 0.9)]),
        ("Ancient debris knocked: heavy stone", [E("block.ancient_debris.hit", 0.4, 0.9)])),
    ore("gold", "The pick bites gold", "Synthetic, soft sine thump",
        ("Minecraft's own gold in rock (nether gold ore struck), quiet", [E("block.nether_gold_ore.hit", 0.3, 1.0)]),
        ("Tuff knocked, raised: soft", [E("block.tuff.hit", 0.6, 1.2)])),
    ore("redstone", "The pick bites redstone", "Synthetic fizz of noise",
        ("Cinnabar knocked: a dry red mineral's tick", [E("block.cinnabar.hit", 0.7, 1.0)]),
        ("Sulfur knocked: drier, softer", [E("block.sulfur.hit", 1.0, 1.0)])),
    ore("lapis", "The pick bites lapis", "A ding: 94% one tone at 1,314 Hz",
        ("Calcite knocked: a clean mineral tick", [E("block.calcite.hit", 0.6, 1.1)]),
        ("Dripstone knocked, raised", [E("block.dripstone_block.hit", 0.5, 1.3)])),
    ore("emerald", "The pick bites emerald", "A ding: 80% one tone at 1,766 Hz",
        ("Calcite knocked, raised: a finer tick", [E("block.calcite.hit", 0.6, 1.4)]),
        ("Cinnabar knocked, raised", [E("block.cinnabar.hit", 0.6, 1.3)])),
    ore("diamond", "The pick bites diamond", "A ding: 95% one tone at 2,218 Hz",
        ("Deepslate knocked, raised: the hardest, driest tick", [E("block.deepslate.hit", 0.5, 1.5)]),
        ("Calcite knocked, well raised", [E("block.calcite.hit", 0.55, 1.7)])),
    ore("deepglass", "The pick bites Deepglass", "A ding: a tone at 883 Hz from the first instant",
        ("Dripstone knocked, lowered, with a small crack of glass now and then",
         [E("block.dripstone_block.hit", 0.6, 0.8), E("block.glass.break", 0.12, 1.5, chance=0.35, spread=0.08)]),
        ("Calcite knocked, lowered", [E("block.calcite.hit", 0.6, 0.75)])),
    row("delve.strike_last", "delve", G, "The last blow: the rock lets go of the ore",
        "delve.client.StrikeFeel.bite: with the blow that breaks an ore block, on top of the block's own breaking sound",
        WAS + ": a noise crack", "Synthetic crack", scene="dig", picks=[
            ("Deepslate splitting, with a little rubble after", [E("block.deepslate.break", 0.55, 0.9), E("block.gravel.hit", 0.5, 1.0, at=100, chance=0.8)]),
            ("Minecraft's own ore breaking (nether quartz ore)", [E("block.nether_ore.break", 0.5, 0.9)]),
            ("Tuff breaking: softer", [E("block.tuff.break", 0.7, 0.8)])]),
    row("delve.true_strike", "delve", G, "A true strike: the pick lands on the seam",
        "delve.client.StrikeFeel.trueStrike: when a bite lands on the ore's seam; a little brighter with each one in a row (the note it "
        "used to climb is gone: StrikeFeel now asks for a small rise only)",
        WAS + ": struck sine modes at the ore's note", "The ding itself: 89% one tone, climbing two semitones with every hit in a row",
        scene="dig", picks=[
            ("Minecraft's critical-hit crack, quiet, with a split of stone: a clean hit, no note",
             [E("entity.player.attack.crit", 0.45, 1.0), E("block.deepslate.break", 0.3, 1.25)]),
            ("Minecraft's strong-attack thwack with the split of stone", [E("entity.player.attack.strong", 0.5, 1.0), E("block.deepslate.break", 0.3, 1.25)]),
            ("The split of stone alone", [E("block.deepslate.break", 0.45, 1.3)])]),
    row("delve.vein_gives", "delve", G, "A lode's heart breaks: the vein gives",
        "delve.Veins (server, at the heart, heard far): when the heart of a lode is broken", WAS + ": a sub thump, cracks and sine modes ringing",
        "Synthetic, with a tone at 1,755 Hz held a quarter second (the 'whole vein ringing')", picks=[
            ("Deepslate splitting deep, the mace's ground blow under it, then the ore crumbling and gravel settling",
             [E("block.deepslate.break", 1.0, 0.6), E("item.mace.smash_ground", 0.5, 0.7), E("block.nether_ore.break", 0.6, 0.8, at=150),
              E("block.gravel.break", 0.6, 0.7, at=300), E("block.gravel.fall", 0.5, 0.8, at=550, chance=0.8)]),
            ("The same without the mace: lighter", [E("block.deepslate.break", 1.0, 0.6), E("block.nether_ore.break", 0.6, 0.8, at=150), E("block.gravel.break", 0.6, 0.7, at=300)]),
            ("The mace's heaviest ground blow, lowered, and gravel", [E("item.mace.smash_ground_heavy", 0.7, 0.7), E("block.gravel.break", 0.7, 0.7, at=200)])]),
    row("delve.vein_crack", "delve", G, "Cracks run along the vein",
        "delve.client.VeinFx.front: up to twelve small cracks travelling outward from a broken heart", WAS + ": noise crackle",
        "Synthetic crackle with a 5 kHz edge", scene="steps", picks=[
            ("Tuff breaking, raised: a small crunch each", [E("block.tuff.break", 0.5, 1.3)]),
            ("Dripstone breaking, raised", [E("block.dripstone_block.break", 0.45, 1.3)]),
            ("Calcite breaking, raised", [E("block.calcite.break", 0.45, 1.3)])]),
    row("delve.heavy_charge", "delve", G, "A heavy strike drawn back",
        "delve.client.HeavyCharge: as you hold the heavy strike, once the draw passes its least", WAS + ": a low sine strain",
        "Synthetic: a low sine swell", picks=[
            ("A crossbow drawn, lowered: wood and cord under strain", [E("item.crossbow.loading_start", 0.7, 0.6), E("item.crossbow.loading_middle", 0.6, 0.6, at=600)]),
            ("Leather creaking as it tightens", [E("item.armor.equip_leather", 0.8, 0.6), E("item.armor.equip_leather", 0.7, 0.55, at=450)]),
            ("A bundle being stuffed, lowered", [E("item.bundle.insert", 0.8, 0.6)])]),
    row("delve.heavy_glint", "delve", G, "The heavy strike is ready",
        "delve.client.HeavyCharge: the instant the draw is full", WAS + ": a struck sine", "A ding: 80% one tone at 2,358 Hz", picks=[
            ("A crossbow's catch clicking home", [E("item.crossbow.loading_end", 0.8, 1.1)]),
            ("A lever thrown", [E("block.lever.click", 0.6, 1.2)]),
            ("A lodestone compass locking", [E("item.lodestone_compass.lock", 0.6, 1.3)])]),
    row("delve.heavy_strike", "delve", G, "A heavy strike bores the rock",
        "delve.HeavyStrikes (server, at the hole): the heavy strike landing", WAS + ": sub thump and noise", "Synthetic", picks=[
            ("The mace's ground blow, with deepslate splitting", [E("item.mace.smash_ground", 0.8, 0.9), E("block.deepslate.break", 0.8, 0.7)]),
            ("The mace's heaviest ground blow", [E("item.mace.smash_ground_heavy", 0.7, 0.9)]),
            ("Deepslate splitting, low, with stone breaking", [E("block.deepslate.break", 1.0, 0.6), E("block.stone.break", 0.8, 0.7)])]),
    row("delve.cave_creak", "delve", G, "The roof creaks before a cave-in",
        "delve.CaveIns and delve.client.CaveInFx: the warning before a weakened roof comes down", WAS + ": filtered noise and sine creaks", "Synthetic", picks=[
            ("Stone grinding (a piston, lowered) and grit trickling after", [E("block.piston.extend", 0.5, 0.5), E("block.gravel.fall", 0.8, 0.8, at=250), E("block.gravel.hit", 0.6, 0.7, at=600, chance=0.7)]),
            ("A grindstone turning, low, and grit", [E("block.grindstone.use", 0.5, 0.5), E("block.gravel.fall", 0.8, 0.8, at=300)]),
            ("Grit alone, three trickles", [E("block.gravel.fall", 0.9, 0.8), E("block.gravel.hit", 0.7, 0.7, at=300), E("block.gravel.fall", 0.6, 0.9, at=700)])]),
    row("delve.cave_fall", "delve", G, "The roof comes down",
        "delve.CaveIns (server, at each fall): the blocks of a cave-in landing", WAS + ": sub thump, noise and rubble grains", "Synthetic", picks=[
            ("The mace's heaviest ground blow, lowered, deepslate splitting, gravel breaking after",
             [E("item.mace.smash_ground_heavy", 0.9, 0.7), E("block.deepslate.break", 1.0, 0.6), E("block.gravel.break", 0.8, 0.7, at=150), E("block.gravel.break", 0.6, 0.6, at=350, chance=0.8)]),
            ("An anvil landing, lowered and quiet, with gravel", [E("block.anvil.land", 0.3, 0.5), E("block.gravel.break", 0.9, 0.7, at=50), E("block.stone.break", 0.8, 0.6, at=150)]),
            ("Stone and gravel breaking, no blow", [E("block.stone.break", 1.0, 0.6), E("block.gravel.break", 0.9, 0.7, at=100), E("block.deepslate.break", 0.8, 0.6, at=200)])]),
    row("delve.piton_set", "delve", G, "A piton hammered into rock",
        "delve.PitonBlock: placing a piton", WAS + ": a struck sine tink", "Synthetic tink", picks=[
            ("A chain set down on stone, with the stone knocked", [E("block.chain.place", 0.7, 0.9), E("block.stone.hit", 0.8, 0.8)]),
            ("A lantern set down, with the stone knocked", [E("block.lantern.place", 0.7, 1.0), E("block.stone.hit", 0.8, 0.8)]),
            ("An anvil set down, very quiet and high, with the stone knocked", [E("block.anvil.place", 0.25, 1.6), E("block.stone.hit", 0.8, 0.8)])]),
    row("delve.rope_pay", "delve", G, "Rope pays out from a piton",
        "delve.PitonBlock: using a piton to pay out its rope", WAS + ": band noise whir", "Synthetic whir", picks=[
            ("A fishing line reeled, lowered, with a lead let go", [E("entity.fishing_bobber.retrieve", 0.8, 0.7), E("item.lead.untied", 0.6, 0.9)]),
            ("A lead tied", [E("item.lead.tied", 0.8, 0.8)]),
            ("Wool brushed, twice", [E("block.wool.step", 0.9, 0.8), E("block.wool.step", 0.8, 0.9, at=150)])]),
    row("delve.deepglass_hum", "delve", G, "Deepglass hums",
        "delve.DeepglassBlock (client, a random tick of the block near you)", WAS + ": sine partials", "Synthetic hum", picks=[
            ("A beacon's hum, raised and quiet", [E("block.beacon.ambient", 0.35, 1.4)]),
            ("Amethyst resonating, lowered", [E("block.amethyst_block.resonate", 0.5, 0.6)]),
            ("A conduit's short hum", [E("block.conduit.ambient.short", 0.4, 1.2)])]),
    row("delve.deep_ear_cave", "delve", G, "Deep Ear: a cave breathes behind the rock",
        "delve.client (the Deep Ear's tell when a cave lies behind the wall you face)", WAS + ": filtered noise", "Synthetic breath of noise", picks=[
            ("A breeze drawing breath, lowered", [E("entity.breeze.inhale", 0.5, 0.6)]),
            ("A horse's breath, lowered", [E("entity.horse.breathe", 0.8, 0.6)]),
            ("Minecraft's cave air (one of its cave sounds), quiet", [E("ambient.cave", 0.3, 1.0)])]),
    row("delve.deep_ear_glass", "delve", G, "Deep Ear: glass hums behind the rock",
        "delve.client (the Deep Ear's tell when Deepglass lies behind the wall you face)", WAS + ": sine partials", "Synthetic hum", picks=[
            ("Amethyst resonating, well lowered: a hum through stone", [E("block.amethyst_block.resonate", 0.5, 0.55)]),
            ("A beacon's hum, raised", [E("block.beacon.ambient", 0.3, 1.5)]),
            ("A respawn anchor's glow", [E("block.respawn_anchor.ambient", 0.4, 1.3)])]),

    # ---- the Tuning Fork: a tone by its nature, now the game's own ring -----------------------------------------------------
    row("delve.fork_tune", "delve", G, "The Tuning Fork takes an ore's note",
        "delve.TuningForkItem: touching the fork to an ore", WAS + ": a pure sine", "A pure synthetic tone. A fork is a tone by nature, so it takes the game's own ring", picks=[
            ("Amethyst chiming", [E("block.amethyst_block.chime", 0.9, 1.0)]),
            ("A note block's chime", [E("block.note_block.chime", 0.7, 1.0)]),
            ("Amethyst struck", [E("block.amethyst_block.hit", 0.9, 1.0)])]),
    row("delve.fork_strike", "delve", G, "The Tuning Fork struck on stone",
        "delve.TuningForkItem: striking the tuned fork on stone", WAS + ": a pure sine with a click", "A pure synthetic tone", picks=[
            ("Stone knocked, and amethyst struck", [E("block.stone.hit", 0.8, 1.2), E("block.amethyst_block.hit", 0.9, 1.0)]),
            ("Stone knocked, and a note block's bell", [E("block.stone.hit", 0.8, 1.2), E("block.note_block.bell", 0.6, 1.0)]),
            ("Stone knocked, and a chain's ring", [E("block.stone.hit", 0.8, 1.2), E("block.chain.hit", 0.6, 1.0)])]),
    row("delve.fork_ring", "delve", G, "The Tuning Fork rings toward a lode",
        "delve.client.ForkSong: a ring that lasts a few seconds after the strike and climbs in pitch as you face the lode (the game bends it)",
        WAS + ": a pure sine loop", "A pure synthetic tone, looped", scene="loop", picks=[
            ("Amethyst resonating: the game's own long ring, struck again as it fades", [E("block.amethyst_block.resonate", 1.0, 1.0)]),
            ("A bell's hum after the strike", [E("block.bell.resonate", 0.8, 1.2)]),
            ("A beacon's hum", [E("block.beacon.ambient", 0.6, 1.4)])]),
]
