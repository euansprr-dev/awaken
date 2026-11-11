"""The sound audit, part 6: a gate room's mechanisms, and the hundred bells (SPEC §66.8).

The Gate Rig (audio_rig.py) was synthesis: noise cracks, sine thuds and hums. Its sounds are stone, pistons, glass and
fire, all of which the game has. The bells (audio_bells.py) are synthesised too, but they are the game's tuned song,
fifteen notes from D3 to B5 that no one recording of the game's reaches in tune; a wrong guess would put the home's song
out of tune. So the bells stay as built (keep=True) and the page offers what they could become, for Euan's ear first.
"""
from audit_kit import E, group, row

G = group("gates", "Gate rooms and the bells", "A gate room's moving floor, mirrors and presses; and the bells, kept as built with alternatives to hear.")
RIG = "gates.run (the Gate Rig): "
WAS = "ours, synthesised (tools/art/specs/audio_rig.py)"


def g(id, name, where, why, *picks, scene="alone"):
    return row("gate.rig." + id, "gates", G, name, RIG + where, WAS, why, list(picks), scene=scene)


ROWS = [
    g("crack", "A floor tile cracks", "a tile about to fall", "Synthetic crack (noise)",
      ("Deepslate tiles knocked", [E("block.deepslate_tiles.hit", 0.8, 1.0)]), ("Stone knocked, raised", [E("block.stone.hit", 0.8, 1.3)])),
    g("fall", "The floor falls away", "a tile dropping", "Synthetic",
      ("Stone breaking, lowered, and grit after", [E("block.stone.break", 0.8, 0.7), E("block.gravel.fall", 0.5, 0.8, at=100)]), ("Deepslate tiles breaking", [E("block.deepslate_tiles.break", 0.8, 0.8)])),
    g("knit", "The floor knits back", "a tile returning", "Synthetic",
      ("Stone set down, then deepslate bricks", [E("block.stone.place", 0.8, 0.9), E("block.deepslate_bricks.place", 0.6, 1.1, at=100)]), ("Stone set down", [E("block.stone.place", 0.8, 0.9)])),
    g("run", "The floor shudders", "a run of tiles about to go", "Synthetic",
      ("Deepslate knocked three times, quickly", [E("block.deepslate.hit", 0.6, 1.2), E("block.deepslate.hit", 0.6, 1.1, at=100, spread=0.05), E("block.deepslate.hit", 0.5, 1.2, at=200, spread=0.05)]),
      ("Gravel shifting", [E("block.gravel.hit", 0.8, 0.9)])),
    g("pit", "The pit throws you back", "falling into the pit", "Synthetic",
      ("A wind charge bursting, lowered", [E("entity.wind_charge.wind_burst", 0.7, 0.8)]), ("A breeze leaping", [E("entity.breeze.jump", 0.7, 0.9)])),
    g("turn", "A mirror turns", "turning a mirror", "Synthetic",
      ("A grindstone turning, slowed, with a piston", [E("block.grindstone.use", 0.5, 0.5), E("block.piston.extend", 0.4, 0.7)]), ("An iron trapdoor", [E("block.iron_trapdoor.open", 0.6, 0.7)])),
    g("hum", "A lens hums", "a lit lens", "Synthetic hum (sine)",
      ("A beacon's hum, a little lowered", [E("block.beacon.ambient", 0.4, 0.8)]), ("A conduit's short hum", [E("block.conduit.ambient.short", 0.4, 0.9)]), scene="loop"),
    g("burn", "Light burns", "the beam on a seal", "Synthetic (noise)",
      ("Fire burning", [E("block.fire.ambient", 0.9, 0.9)]), ("A blast furnace crackling", [E("block.blastfurnace.fire_crackle", 0.9, 1.0)]), scene="loop"),
    g("ignite", "A seal catches the light", "a seal lit", "Synthetic",
      ("A fire catching", [E("item.firecharge.use", 0.7, 0.9)]), ("Flint and steel", [E("item.flintandsteel.use", 0.8, 0.9)])),
    g("shatter", "A mirror shatters", "a mirror broken", "Synthetic shatter (noise and sines)",
      ("Glass breaking, a little lowered", [E("block.glass.break", 0.9, 0.8)]), ("Glass and an amethyst cluster breaking", [E("block.glass.break", 0.9, 0.8), E("block.amethyst_cluster.break", 0.5, 0.8)])),
    g("flip_up", "Gravity turns over", "the room flipping", "Synthetic",
      ("A piston pushing, with a breeze leaping", [E("block.piston.extend", 0.6, 0.9), E("entity.breeze.jump", 0.5, 0.8)]), ("A piston pushing", [E("block.piston.extend", 0.6, 0.9)])),
    g("flip_down", "Gravity rights itself", "the room flipping back", "Synthetic",
      ("A piston drawing back, with a breeze landing", [E("block.piston.contract", 0.6, 0.9), E("entity.breeze.land", 0.5, 0.8)]), ("A piston drawing back", [E("block.piston.contract", 0.6, 0.9)])),
    g("surge", "The room draws breath", "before the room moves", "Synthetic",
      ("A breeze drawing breath, lowered", [E("entity.breeze.inhale", 0.8, 0.7)]), ("A respawn anchor taking a charge, lowered", [E("block.respawn_anchor.charge", 0.6, 0.8)])),
    g("cave", "The ceiling creaks", "before a ceiling falls", "Synthetic",
      ("Stone grinding (a piston, slowed) and grit trickling", [E("block.piston.extend", 0.5, 0.5), E("block.gravel.fall", 0.8, 0.8, at=250)]), ("Grit trickling", [E("block.gravel.fall", 0.9, 0.8)])),
    g("rubble", "Rubble lands", "a ceiling's fall landing", "Synthetic",
      ("Gravel and stone breaking", [E("block.gravel.break", 0.9, 0.8), E("block.stone.break", 0.7, 0.7, at=50)]), ("Gravel falling", [E("block.gravel.fall", 0.9, 0.8)])),
    g("windup", "A press winds up", "a press about to fall", "Synthetic",
      ("A crossbow drawn, well lowered", [E("item.crossbow.loading_start", 0.7, 0.6)]), ("A piston drawing back, slowed", [E("block.piston.contract", 0.6, 0.6)])),
    g("press", "A press slams down", "a press landing", "Synthetic thud",
      ("A piston pushing, slowed, into an anvil landing far lowered", [E("block.piston.extend", 0.8, 0.6), E("block.anvil.land", 0.25, 0.5, at=100)]), ("The mace's ground blow", [E("item.mace.smash_ground", 0.7, 0.8)])),
    g("lift", "A press lifts", "a press rising", "Synthetic",
      ("A piston drawing back, slowed", [E("block.piston.contract", 0.7, 0.7)]), ("An iron door opening, slowed", [E("block.iron_door.open", 0.6, 0.7)])),
    g("ring", "A checkpoint", "passing a checkpoint ring", "A synthetic ding",
      ("Amethyst chiming", [E("block.amethyst_block.chime", 0.6, 1.1)]), ("XP arriving", [E("entity.experience_orb.pickup", 0.4, 1.0)])),
    g("medal", "A course run", "finishing a room's course", "A synthetic ding",
      ("The game's own level-up, quiet, with an amethyst chime", [E("entity.player.levelup", 0.5, 1.2), E("block.amethyst_block.chime", 0.4, 1.0)]), ("The level-up alone", [E("entity.player.levelup", 0.5, 1.2)])),

    # ---- the bells: two plain ones remade, the tuned song kept as built with alternatives to hear ---------------------------
    row("bells.gap", "bells", G, "A gap in the bells' song: a dull knock", "bells: where a bell is missing from the tower's song",
        "ours, synthesised (tools/art/specs/audio_bells.py)", "A synthetic knock; a knock of wood is something the game has", [
            ("Wood knocked, lowered", [E("block.wood.hit", 0.9, 0.7)]), ("A note block's bass drum, lowered", [E("block.note_block.basedrum", 0.7, 0.7)])]),
    row("bells.hang", "bells", G, "A bell is hung on its hook", "bells.Hanging: hanging a found bell in the tower",
        "ours, synthesised (tools/art/specs/audio_bells.py)", "Synthetic: sine partials for iron on iron", [
            ("A chain set down, an anvil far quieter, then the beam taking the weight (wood knocked)",
             [E("block.chain.place", 0.8, 0.8), E("block.anvil.place", 0.3, 1.2, at=100), E("block.wood.hit", 0.7, 0.7, at=250)]),
            ("A chain set down, then wood knocked", [E("block.chain.place", 0.8, 0.8), E("block.wood.hit", 0.7, 0.7, at=200)])]),
    row("bells.note_7", "bells", G, "One of the tower's fifteen bells (F sharp, the middle of the song)", "bells: every note of the home's song, a found bell rung, a dream bell",
        "ours, synthesised (tools/art/specs/audio_bells.py: a bell's partials as sines)",
        "Synthetic, but kept: the fifteen notes run D3 to B5, wider than any one of the game's recordings reaches in tune. All 45 note sounds change together or not at all",
        keep=True, scene="twice", picks=[
            ("A note block's iron xylophone at this note: struck metal, in tune by the game's own tuning", [E("block.note_block.iron_xylophone", 0.9, 1.0)]),
            ("A note block's bell at this note, an octave up", [E("block.note_block.bell", 0.8, 0.5)]),
            ("The game's own bell, untuned, with its hum", [E("block.bell.use", 0.8, 1.0), E("block.bell.resonate", 0.5, 1.0)])]),
    row("bells.toll", "bells", G, "A great bell tolls as it wakes", "bells: a bell waking, heard over the land",
        "ours, synthesised (tools/art/specs/audio_bells.py)", "Synthetic, kept with the notes: it shares their voice", keep=True, picks=[
            ("The game's own bell, slowed as far as it goes, with its hum", [E("block.bell.use", 1.0, 0.5), E("block.bell.resonate", 0.8, 0.5)]),
            ("The bell's hum alone, slowed", [E("block.bell.resonate", 1.0, 0.5)])]),
    row("bells.handbell", "bells", G, "Your handbell rings", "bells: the band's handbell, shaken twice",
        "ours, synthesised (tools/art/specs/audio_bells.py)", "Synthetic, kept with the notes", keep=True, picks=[
            ("The game's own bell, raised, twice", [E("block.bell.use", 0.5, 1.8), E("block.bell.use", 0.45, 1.85, at=250)]),
            ("A note block's bell, twice", [E("block.note_block.bell", 0.6, 0.9), E("block.note_block.bell", 0.55, 0.9, at=250)])]),
]
