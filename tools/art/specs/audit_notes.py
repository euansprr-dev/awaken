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

    # ---- left, each with its reason ----------------------------------------------------------------------------------------
    (r"^bells\.", dict(state="left", verdict="Synthetic bells (sine partials), kept for now: they are the home's tuned song, fifteen notes from D3 to B5, wider "
                                              "than any one of the game's recordings reaches in tune. The family changes together or not at all",
                       fix="Left. `bells.note_7`, `bells.toll` and `bells.handbell` are on the listening page with alternatives; his pick decides the family",
                       group="gates")),
    (r"^story\.ritual\.", dict(state="left", verdict="Not changed: the opening ritual is one staged scene, rebuilt on 2 Nov mostly from recordings of the real thing "
                                                       "(stone, thunder, wind, a welding arc, a church bell: CC0, tools/art/sources/ritual), unheard in game. "
                                                       "The System's and the void's tones in it are still synthesised",
                               fix="Left for his ear as a whole scene (tools/art/audio_listen_ritual.py). Remaking single cues blind would break its mix")),
    (r"^gate\.world\.", dict(state="left", verdict="Below the bar: synthesis over CC0 impacts and bells (beds, events and stings of the nineteen gate-world themes). "
                                                     "Not remade here: the gates are being rebuilt as worlds, whose rule is already 'every sound one of Minecraft's "
                                                     "own recordings by name' (SPEC §50), and remaking 145 theme sounds twice would be waste",
                              fix="Left with the gates' rebuild. `tools/art/sound_audit.py` takes a table for them (audit_gates.py) when their worlds are settled")),
    (r"^(arms|anvil)\.", dict(state="left", verdict="Partly remade already: on 10 Nov the blows, parries and draws became mixes of the game's own recordings "
                                                     "(`fight.*`, `guard.*`, SPEC §25 stage 2) over this kit, which is CC0 impact recordings (Kenney) under synthesis. "
                                                     "That work has its own listening page and waits for his marks",
                              fix="Left until he has marked the weapons' page (run-autopilot/weapon-listen): changing the kit under it now would change what he is asked to judge")),
    (r"^amb\.world$", dict(state="left", verdict="The air mixer's stream (the places' air beds, see 'Beds that are not events' below)", fix="See below")),
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


# Beds and stems that play from files, not from sound events (the audit's last table).
STEMS = [
    ("`amb_world_stillpoint_*`, `halo_*`, `dream_*`, `elder_groan`, `lumen_hum`, `veil_choir` (10 files)",
     "The air of the special places and the Veil: `core.audio.client.AirMixer`, named in `assets/awaken/soundscape/place|state/*.json`",
     "audio_world.py, synthesised drones",
     "Below the bar where the place is of this world (the Elderwood's groan, the Lumen Forest's hum); unreal places (the Stillpoint, the Halo, the dream, the Veil) have no real thing to record",
     "Left. The mixer decodes 44.1 kHz files only and the game's own loops are 48 kHz, so naming them needs a resampler in `OggLoop` first: a follow-up"),
    ("`amb_world_greenwood_*`, `amb_world_hollows_*` (6 files)",
     "The Conductor's own nature air, only without AmbientSounds (with the pack, its recordings play and these are off)",
     "audio_world.py, synthesised", "Below the bar, and not heard with the pack", "Left with the same follow-up"),
    ("`layers/layer_city_*` (city air, murmur, bustle, water, birds, crickets)",
     "Carillon's district beds: `LayerPlayer.AMBIENCE`, named in `assets/awaken/music_layers/city|village/**.json`. With AmbientSounds the server already leaves the birds and crickets stems out",
     "audio_city_life.py, synthesised (a murmur of voices from formants, noise for water and air)",
     "Below the bar: the synthetic crowd most of all", "Left: the same 44.1 kHz limit, and a crowd needs recordings the game does not have. A follow-up with its own plan"),
    ("`layers/layer_<gate theme>_*` and `music_*`", "Gate and cue music stems (`LayerPlayer.MUSIC`)", "audio_layers.py / audio_music.py, a synthesised score",
     "Not judged by this bar: music", "Left"),
]
