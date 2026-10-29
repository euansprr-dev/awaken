"""The level road's P3 steps, levels 14-49 (plan v2 §4.4): one JSON per step in data/awaken/awaken/level_road/.

Edit here, then run:  python3 tools/sim/road_content.py
Levels 1-12, 15, 20, 25, 30, 35, 40, 45 and 50 come from tools/content/progression_content.py and the four Hunter gear
steps (3, 13, 22, 42) from tools/art/specs/gear.py; this file writes only its own keys, so the three never collide.
The file name is the unlock id saved in the profile: never rename one.

Every step names a real unlock (a recipe, an item, a Power Core) and a goal: the concrete thing to do, shown on the
Now tracker until its condition holds (docs/modules/road.md). Text sizes are checked against the System v2 surfaces:
a toast shows the title and the first sentence of the text in 144 px; the tracker shows a goal in ~200 px.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../src/main/resources/data/awaken/awaken/level_road")

# Minecraft's default font advance widths (glyph + 1 px), close enough to keep text inside its box.
NARROW = {**{c: 2 for c in "i!.,:;'|"}, "l": 3, **{c: 4 for c in "tI[] "}, **{c: 5 for c in "fk<>(){}*\""}, "@": 7, "~": 7}


def px(s):
    return sum(NARROW.get(ch, 6) for ch in s)


def first_sentence(s):
    for i in range(len(s) - 1):
        if s[i] in ".!?" and s[i + 1] == " ":
            return s[:i + 1]
    return s


TOAST_TEXT_PX, TRACKER_PX = 144, 200


def c(counter, value=1):
    return {"type": "counter", "counter": "awaken:" + counter, "value": value}


def recipes(*ids):
    return {"type": "recipe", "ids": ["awaken:" + i for i in ids]}


def item(iid, count=1):
    return {"type": "item", "id": "awaken:" + iid, "count": count}


def core(count=1):
    return {"type": "power_core", "count": count}


def goal(now, done, toast, requires=(), priority=35):
    assert px(now) <= TRACKER_PX, f"tracker line too wide ({px(now)} px): {now}"
    assert px(toast) <= TOAST_TEXT_PX, f"goal toast too wide ({px(toast)} px): {toast}"
    g = {"now": now, "done": done, "toast": toast}
    if requires:
        g["requires"] = list(requires)
    if priority != 35:
        g["priority"] = priority
    return g


def step(level, title, text, grants, g):
    assert px(title) <= TOAST_TEXT_PX and len(title) <= 24, f"title too wide: {title}"
    assert px(first_sentence(text)) <= TOAST_TEXT_PX, f"first sentence too wide ({px(first_sentence(text))} px): {text}"
    return {"schema": 2, "level": level, "title": title, "text": text, "grants": grants, "quiet": False, "goal": g}


DESCENT = "unlock:awaken:descent"
DREAMS = "unlock:awaken:dreams"

STEPS = {
    # L11-20 · The Veil (11 meditation, 12 dreams, 13 gear D, 15 the Veil, 20 the Well exist; their teaching is in
    # veil/VeilLessons and lucidium/dream/DreamLessons). Mounts don't exist: the Above's machines are how you travel, so
    # 16-18 walk the Scanner -> Fabricator -> a machine of your own (a Waypoint Pylon carries you).
    "training_grounds": step(14, "Training Grounds", "Race your own ghost. Start Gate, Checkpoint Rings and Finish Gate are in the recipe book.",
                             [recipes("start_gate", "checkpoint_ring", "finish_gate")],
                             goal("Place Start, Rings, Finish: run a lap", c("training_laps"), "Your ghost races you next")),
    "precursor_scanner": step(16, "The Scanner", "Ruins hum back at it. Hold it up at the sky and sweep.",
                              [item("precursor_scanner"), recipes("precursor_scanner")],
                              goal("Hold the Scanner up: sweep for a ruin", c("ruins_found"), "Pylons can bring you back")),
    "fabricator": step(17, "The Fabricator", "Scan it, then build it. Broken machines in ruins hold blueprints.",
                       [recipes("fabricator", "precursor_circuit")],
                       goal("Scan a broken machine in a ruin", c("machines_scanned"), "Its blueprint is yours")),
    "machine_travel": step(18, "Machines, not mounts", "An essence cell for you. Waypoint Pylons and the Deep Lift carry you.",
                           [recipes("essence_cell", "precursor_void_glass"), item("essence_cell")],
                           goal("Build a machine at the Fabricator", c("machines_built"), "Feed a Reactor nearby")),
    "veil_scouting": step(19, "Scout from the Veil", "8 essence. As a spirit, a gate's mouth shows its Lord.",
                          [item("essence_shard", 8)],
                          goal("As a spirit, drift into a gate", c("gates_scouted"), "Its Lord is on the card",
                               requires=["unlock:awaken:veil"])),
    # L21-30 · C-rank (22 gear C, 25 Second Awakening, 30 The far towns exist): dream control, the town grows.
    "dream_bell": step(21, "The Dream Bell", "Lucid sooner. Arm it by your bed: you wake at midnight; sleep again.",
                       [recipes("dream_bell"), item("dream_residue")],
                       goal("Arm a Dream Bell by your bed; sleep", c("lucid_dreams", 2), "The dream answers you",
                            requires=[DREAMS])),
    "dream_shaping": step(23, "Shape the dream", "Hold H on a wall: a door. Lucid, a floor gives stairs and the air a bridge.",
                          [recipes("dream_glass", "dream_cloud", "shifting_stone"), item("dream_residue", 2)],
                          goal("In a lucid dream, hold H at a wall", c("dream_manifests"), "Air makes bridges too",
                               requires=[DREAMS])),
    "hanging_gardens": step(24, "The Hanging Gardens", "Stratum 2, 8 essence. Wind pushes you toward the drops: sneak.",
                            [item("essence_shard", 8)],
                            goal("Descend to stratum 2 of the Well", c("depth_record", 2), "Light its Waystone",
                                 requires=[DESCENT, "rank:D"])),
    "town_grows": step(26, "The town grows", "A Spire Board for home. C-rank brings Teodor, and Anchor tier III.",
                       [recipes("spire_board"), item("essence_shard", 10)],
                       goal("Hang a Lord's trophy in your Hall", c("trophies_placed"), "+1% damage per Lord",
                            requires=["hearth"])),
    "face_the_fear": step(27, "Face the nightmare", "Beat what hunts you. Set 'face my fear' in the Dream Journal.",
                          [item("dream_residue", 2)],
                          goal("Dream Journal: 'face my fear', then win", c("nightmares_beaten"), "That fear is gone",
                               requires=[DREAMS])),
    "twisted_gates": step(28, "Twisted gates", "A Power Core. A twist rewrites a gate's rules.",
                          [core()],
                          goal("Clear a gate with a twist", c("twists_seen"), "Scout to see twists")),
    "your_mentor": step(29, "Your mentor", "A Lord's Heart. Your Path's mentor waits in your dreams.",
                        [item("lord_heart")],
                        goal("Beat your mentor in a dream", c("mentor_duels_won"), "A fifth of a level a win",
                             requires=["path", DREAMS])),
    # L31-45 · Deep (35 Overload, 40 the deep calls, 42 gear B, 45 B trial exist): strata 3-6, named beasts everywhere.
    "drowned_choir": step(31, "The Drowned Choir", "Stratum 3, 12 scrap. The water rises every five minutes.",
                          [item("precursor_scrap", 12)],
                          goal("Descend to stratum 3, the Choir", c("depth_record", 3), "When it sings, climb",
                               requires=[DESCENT, "rank:D"])),
    "named_beasts": step(32, "Named beasts", "10 essence. The Old Antler, the Briar Sow and the Dune Wyrm roam.",
                         [item("essence_shard", 10)],
                         goal("Fell a named beast", c("beasts_felled"), "Half carry a Power Core")),
    "bone_orchard": step(33, "The Bone Orchard", "Stratum 4, 12 essence. It opens at C-rank: the dead rise once.",
                         [item("essence_shard", 12)],
                         goal("Descend to stratum 4 (C-rank)", c("depth_record", 4), "The dead rise once",
                              requires=[DESCENT, "rank:C"])),
    "deep_lift": step(34, "The Deep Lift", "12 Precursor scrap. Lit Waystones become Lift stops, with no Strain.",
                      [item("precursor_scrap", 12)],
                      goal("Light three Descent Waystones", c("waystones_lit", 3), "A Deep Lift rides there",
                           requires=[DESCENT])),
    "silent_halls": step(36, "The Silent Halls", "Stratum 5, 14 essence. Listeners hunt by ear.",
                         [item("essence_shard", 14)],
                         goal("Descend to stratum 5; stay quiet", c("depth_record", 5), "Listeners hunt by ear",
                              requires=[DESCENT, "rank:C"])),
    "precursor_frames": step(37, "Precursor Frames", "Masonry in the book. Frames with Jet Boots fly where mounts can't.",
                             [recipes("precursor_plating", "precursor_panel", "precursor_trim", "precursor_glow_panel",
                                      "precursor_warning_light", "precursor_membrane")],
                             goal("Forge a Frame at a Frame Forge", c("frames_forged"), "Jet Boots: hold jump")),
    "stratum_guardians": step(38, "Stratum guardians", "12 essence. A lair 14 blocks north of each pit.",
                              [item("essence_shard", 12)],
                              goal("Beat a stratum guardian, north of a pit", c("guardians_defeated"), "A core and essence",
                                   requires=[DESCENT])),
    "beasts_everywhere": step(39, "Beasts everywhere", "A Power Core. Every biome family has its named beast.",
                              [core()],
                              goal("Fell three named beasts", c("beasts_felled", 3), "The wilds know you")),
    "hold_the_line": step(41, "Hold the line", "A Lord's Heart. A gate that breaks near home sends its Lord.",
                          [item("lord_heart")],
                          goal("Defend your Hearth from a gate break", c("breaks_held_at_hearth"), "The town is safe",
                               requires=["hearth"])),
    "push_deeper": step(43, "Push past comfort", "16 essence. Deeper floors fill the Satchel faster.",
                        [item("essence_shard", 16)],
                        goal("Clear a floor deeper than usual", c("floors_pushed_past_comfort"), "Deeper floors pay more")),
    "what_was_left": step(44, "What was left", "12 essence. A key from the stone people still opens what someone left.",
                          [item("essence_shard", 12)],
                          goal("Open a chest someone left behind", c("left_chests_opened"), "Somebody meant it for you")),
    # L46-50 · B-rank (45 B trial, 50 the Hollow Court exist): the B strata and the Ascension hints (the Halo).
    "the_foundry": step(46, "The Foundry", "Stratum 6, a Power Core. Light five conduits to open its pit.",
                        [core()],
                        goal("Descend to stratum 6 (B-rank)", c("depth_record", 6), "Light all five conduits",
                             requires=[DESCENT, "rank:B"])),
    "the_observatory": step(47, "The Observatory", "A Lord's Heart. At B-rank an Observatory shows the Halo.",
                            [item("lord_heart")],
                            goal("Find the Halo with an Observatory", c("story/halo_seen"), "Something above the sky",
                                 requires=["rank:B"])),
    "inverted_sea": step(48, "The Inverted Sea", "Stratum 7, 20 essence. Its tides lift you: land before the ebb.",
                         [item("essence_shard", 20)],
                         goal("Descend to stratum 7 (B-rank)", c("depth_record", 7), "Land before the ebb",
                              requires=[DESCENT, "rank:B"])),
    "launch_ring": step(49, "The Launch Ring", "Ring frames in the book. 16 round a core and 2,000 essence: the Halo.",
                        [recipes("launch_ring_frame", "precursor_alloy")],
                        goal("Ride a Launch Ring to the Halo", c("story/halo_reached"), "Read the archive",
                             requires=["rank:B"])),
}

if __name__ == "__main__":
    os.makedirs(ROOT, exist_ok=True)
    for key, data in STEPS.items():
        with open(os.path.join(ROOT, key + ".json"), "w") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print(len(STEPS), "steps:", sorted(d["level"] for d in STEPS.values()))
