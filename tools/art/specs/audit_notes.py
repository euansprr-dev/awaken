"""The sound audit's verdicts for every event the tables (audit_*.py) do not replace (SPEC §66).

note(id, module, entry) -> dict(state, verdict[, what, where, was, fix, group]):
    state "bar"    at the bar: nothing to do
    state "left"   below the bar or unjudged, and left as it is, with the reason and the way to fix it
The tables' own rows are "fixed". Rules are tried in order; the first whose pattern matches the event id wins.
"""
import re

MODULE_TITLES = {
    "above": "The Halo (above)", "aim": "Lock-on", "anims": "Hands at work", "anomaly": "Rifts, groves and fallen stars",
    "armour": "Armour and the Forge Song", "arms": "Weapons and the War Anvil", "ascent": "Rope and wings (the Classic set)",
    "ascent_rappel": "Rappel", "ascent_scores": "Rope and wings (the scores)", "audio": "The System's cues, places and music",
    "bells": "The hundred bells", "binoculars": "Binoculars", "body": "The body's moves", "caves": "The Living Deep",
    "citizens": "Carillon", "core": "The System", "core_edge": "The edge hold", "delve": "Mining", "delve_caves": "Cave names",
    "delve_picks": "The Forged Pick", "delve_sense": "Cave senses", "descent": "The Descent", "enchant": "The Inscribing Table",
    "fault": "Faults", "fauna": "The wild's creatures", "gates": "Gates and gate worlds", "ground": "The wild's plants",
    "hearth": "The Hearth", "holo": "The Holo UI", "kin": "Kin and their works", "living": "The Living Wilds",
    "lucidium": "Dreams", "mobs": "Monsters' tells", "mobs_fights": "Carriers (what a creature sends at you)",
    "mobs_monsters": "Monsters of the gates", "powers": "Powers", "riding": "Riding", "shaping": "Shaping",
    "story": "The Ritual of the Bell", "terrain": "The land's wind", "veil": "The Veil", "village": "Holdfasts",
    "wildlife": "Wild life", "wilds": "The Precursor ward",
}

GENERATORS = [  # (event pattern, generator)
    (r"^ui\.(find|moment)|^amb\.rift_rumble", "audio_discovery.py, synthesised"),
    (r"^ui\.", "audio_ui.py, synthesised (the System's struck glass)"),
    (r"^(signal\.|level_up|rank_up)", "audio_ui.py, synthesised (the System's struck glass)"),
    (r"^amb\.(lands_|glass_steppe|shattered|frost_spires|mirror_marsh)", "audio_lands.py, synthesised wind beds"),
    (r"^amb\.(layers|world)$", "audio_layers.py / audio_world.py, synthesised stems"),
    (r"^amb\.", "audio_amb.py, synthesised beds"),
    (r"^music\.", "audio_music.py / audio_layers.py, a synthesised score"),
    (r"^world\.", "audio_world.py, synthesised"),
    (r"^city\.bell|^village\.alarm|^city\.handbell|^holdfast\.bell|^city\.carillon", "audio_city.py: CC0 recordings of real bells (BigSoundBank)"),
    (r"^city\.", "audio_city_life.py, synthesised"),
    (r"^caves\.", "audio_underlife.py, synthesised"),
    (r"^edge\.", "audio_edge_hold.py, synthesised wind"),
    (r"^delve\.cave\.name", "audio_caves.py, synthesised"),
    (r"^delve\.(band\.room|cave\.breath|cave\.settle|cave\.answers)", "audio_cave_recorded.py: CC0 cave recordings (BigSoundBank)"),
    (r"^delve\.(cave|deep)\.", "audio_caves.py, synthesised"),
    (r"^living\.(scan|crit|xp_tick|level_up|region_enter)", "audio_living.py, synthesised (the System's struck glass)"),
    (r"^living\.|^ambient\.storm", "audio_world.py, synthesised"),
    (r"^wild_plants\.", "audio_wild_plants.py, synthesised"),
    (r"^wilds\.", "audio_wilds.py, synthesised"),
    (r"^hearth\.", "audio_hearth.py, synthesised"),
    (r"^dream\.", "audio_dreams.py, synthesised"),
    (r"^anomaly\.", "audio_discovery.py, synthesised"),
    (r"^fault\.", "audio_fault.py, synthesised"),
    (r"^holo\.", "audio_holo.py, synthesised (the System's struck glass)"),
    (r"^aim\.", "aim_sounds.py, synthesised (the System's struck glass)"),
    (r"^binoculars\.", "binoculars_sounds.py, synthesised"),
    (r"^body\.", "audio_body.py, synthesised"),
    (r"^ascent\.rappel", "audio_rappel.py, synthesised"),
    (r"^ascent\.", "audio_ascent.py, synthesised"),
    (r"^mobs\.fights\.", "audio_fights.py, synthesised"),
    (r"^enchant\.", "audio_enchant.py, synthesised with CC0 leather and paper"),
    (r"^story\.ritual\.", "ritual_recordings.py (CC0 recordings) and ritual_sounds.py (synthesised)"),
    (r"^bells\.", "audio_bells.py, synthesised bells"),
    (r"^fauna\.", "fauna_sounds.py, synthesised"),
    (r"^gate\.rig\.", "audio_rig.py, synthesised"),
    (r"^gate\.world\.", "audio_gate_worlds*.py: synthesis over CC0 impacts and bells"),
    (r"^power\.", "audio_power_kits.py / audio_powers.py: synthesis over CC0 impacts"),
    (r"^arms\.", "audio_arms.py: CC0 impacts (Kenney) under synthesis"),
    (r"^anvil\.", "audio_anvil.py: CC0 impacts (Kenney) under synthesis"),
]


def generator(id, module, entry):
    for pat, gen in GENERATORS:
        if re.search(pat, id):
            return gen
    return "tools/art (" + module + ")"


BAR = "At the bar: Minecraft's own recordings, by name"

RULES = [
    # ---- already Minecraft's own, by name ---------------------------------------------------------------------------------
    (r"^monster\.", dict(state="bar", verdict=BAR + " (SPEC §60, 10 Nov)", where="`mobs`: a gate monster's voice (ambient, hurt, death, step, wind-up, blow)")),
    (r"^kin\.|^works\.", dict(state="bar", verdict=BAR, where="`kin`: a kin's call, tell and worry; a work finished")),
    (r"^ascent\.score\.", dict(state="bar", verdict=BAR + " (SPEC §62, 10 Nov): the New movement scores", where="`body.ascent.client.AscentSounds.move`: each move's layers")),
    (r"^(forge|armour)\.", dict(state="bar", verdict=BAR + " (SPEC §44.12): the bar itself; his marks on its page still choose between picks", where="`armour`: the Forge Song, the set abilities, putting on, walking, being struck")),
    (r"^pick\.|^anvil\.(colours|eye|hang|pour|tend|thread|tip)$", dict(state="bar", verdict=BAR + " (SPEC §49): Euan, 'WAYYYY better'", where="`delve.forged`: the Forged Pick's voice and the head's making")),
    (r"^(guard|fight)\.", dict(state="bar", verdict=BAR + " (SPEC §25, weapon feel stage 2, 10 Nov)", where="`arms.ArmsSounds.mix`: layers of a blow, a parry, a draw")),
    (r"^voice\.", dict(state="bar", verdict=BAR + " (SPEC §50): a layer of a power's score", where="`powers.client.fx.PowerVoice`: one recording of a power's score")),
    (r"^(descent|above|veil|riding|shaping|station|touch|mobs\.(telegraph|boss|rise|cord|listener))", dict(state="bar", verdict=BAR)),
    (r"^realm\.", dict(state="bar", verdict=BAR, where="`gates.realms`: a realm's air")),

    # ---- real recordings of ours ------------------------------------------------------------------------------------------
    (r"^city\.bell|^village\.alarm|^city\.handbell|^holdfast\.bell|^city\.carillon", dict(
        state="bar", verdict="At the bar: recordings of real bells (CC0), their strike and decay kept, no synthetic layer")),
    (r"^delve\.(band\.room|cave\.breath|cave\.settle|cave\.answers)", dict(
        state="bar", verdict="At the bar: recordings of real caves and falling stone (CC0), SPEC §34.5")),

    # ---- the older synthesised sets that a score of Minecraft's recordings has replaced ----------------------------------
    (r"^power\.", dict(state="left", verdict="Superseded, not heard by default: a power's sound is now a score of Minecraft's own recordings (SPEC §50). "
                                              "This is the Classic set, heard only with Power sounds set to Classic or on a beat with no score",
                       fix="Left as the Classic option. If Classic should go, the option and these files go together")),
    (r"^ascent\.(?!rappel)", dict(state="left", verdict="Superseded, not heard by default: every movement sound is now a score of Minecraft's own recordings "
                                                        "(SPEC §62). This is the Classic set the scores are keyed off",
                                  fix="Left as the Classic option")),

    # ---- music --------------------------------------------------------------------------------------------------------------
    (r"^music\.|^amb\.layers$", dict(state="left", verdict="Not judged by this bar: a synthesised score, not a sound of the world. Nothing in the game or the repo's "
                                                           "recordings can replace music", fix="Left. A music pass needs its own plan (real instruments or the game's own music by name)")),
]


def note(id, module, entry):
    for pat, n in RULES:
        if re.search(pat, id):
            return dict(n)
    ours = any((s if isinstance(s, str) else s["name"]).startswith("awaken:") for s in entry.get("sounds", []))
    if not ours:
        return dict(state="bar", verdict=BAR)
    return dict(state="left", verdict="Below the bar: synthesised. Not yet remade", fix="Open: to be remade from the game's recordings")
