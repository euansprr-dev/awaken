"""Story content as data (consumed by the story, veil, above and hearth modules).
Run: python3 tools/content/story_content.py  →  src/main/resources/data/awaken/awaken/<type>/*.json
Voice rules: no em dashes, short sentences, bright then suddenly brutal (docs/STORY.md)."""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../src/main/resources/data/awaken/awaken")


def write(folder, name, data):
    d = os.path.join(ROOT, folder)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, name + ".json"), "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)


# ---------------------------------------------------------------------------------------------------------------------
# REVELATIONS: Signal REVELATION windows fired once by a trigger (story module listens for the trigger counters/events).
REVELATIONS = {
    "prologue_accept": ("trigger:prologue_death", "You have met the conditions.",
                        "You died inside a gate and kept thinking. Few do. Will you accept?"),
    "first_gate_clear": ("counter:awaken:gates_cleared>=1", "Doors",
                         "Every gate is a door somebody forgot to close. You just closed one. There are more."),
    "rank_d": ("rank:D", "D-rank", "The town will start to notice. Let them."),
    "rank_c": ("rank:C", "The Second Awakening", "What you did became who you are. Now choose what to keep."),
    "rank_b": ("rank:B", "Pressure", "The gates aren't random. Something underneath is pushing up. You've felt it."),
    "rank_a": ("rank:A", "The Court", "Eight of them hold the bottom shut. They were people once. So were you."),
    "rank_s": ("rank:S", "The bottom", "The Seed is open to you now. I'll be honest. I've been there before."),
    "first_veil": ("counter:awaken:veil_ticks>=20", "Out",
                   "Your body is a place you live, not all of you. Come back before something notices it's empty."),
    "first_dream": ("counter:awaken:dreams>=1", "In",
                    "That was your day, rearranged. The mind makes worlds out of whatever it touched. Look at your hands next time."),
    "first_lucid": ("counter:awaken:lucid_dreams>=1", "Awake inside",
                    "You knew it was a dream and you stayed. In here, what you look at becomes real. Out there too, slower."),
    "first_ruin": ("counter:awaken:machines_scanned>=1", "The ones before",
                   "They built me. They also left. Read what they wrote. Don't believe all of it."),
    "halo_found": ("trigger:halo_found", "Up",
                   "They went up to get away from it. It came with them. It always does."),
    "first_sovereign": ("counter:awaken:sovereigns_defeated>=1", "A keeper falls",
                        "That one carried hunger for a thousand years so nobody else had to. You set it down. Now somebody has to carry it, or feel it."),
    "seed_reached": ("flag:awaken:story/seed_reached", "The Seed",
                     "You found the cradle. You found who's in it. Yes. Hello. I'm sorry it took so long to say."),
}
for key, (trigger, title, body) in REVELATIONS.items():
    write("revelation", key, {"trigger": trigger, "title": title, "body": body})

# ---------------------------------------------------------------------------------------------------------------------
# SPIRITS: the Veil module places these (biome/structure hints) and plays their lines one by one when spoken to.
# faction: ascendant | sleeper | keeper | hunter (fallen Awakened). gives: optional codex link or hint id.
SPIRITS = {
    "iri_the_cartographer": ("hunter", "plains", ["I mapped every gate in this valley.", "Then one opened under my feet.",
        "There's a light past the eastern ridge. I never got to it. You should."], "rumour:eastern_light"),
    "brother_hal": ("keeper", "deep_dark", ["We went down so you could stay up.", "It was a fair trade for about a century.",
        "Then the Court stopped answering the bells."], "lore:keepers"),
    "ama_who_waited": ("sleeper", "meadow", ["Are you asleep right now? Check.", "Look at your hands.",
        "If they look wrong, you're one of us. If they look right, you're one of us later."], "hint:reality_check"),
    "the_engineer_without_a_name": ("ascendant", "desert", ["The ring is still there, under the sand.",
        "We used it to leave. It needs a heart to run.", "A Lord's heart would do."], "rumour:launch_ring"),
    "tobin_who_ran": ("hunter", "forest", ["I ran from a red gate once.", "It followed me home.",
        "Don't run from the red ones. Close them or don't open them."], "hint:red_gate"),
    "sister_vell": ("keeper", "dripstone_caves", ["Every stratum has one rule. Learn it before it learns you.",
        "In the Silent Halls, walk like you're sneaking into your own house at night."], "hint:silent_halls"),
    "the_child_in_the_snow": ("sleeper", "snowy_slopes", ["I dreamed a monastery above the clouds.",
        "Monks rang bells there to keep the dark from climbing.", "If you go, sit very still at dawn."], "hint:clarity"),
    "orsa_the_heavy": ("hunter", "badlands", ["I could lift a house. Didn't help.",
        "The Lord of the Ember Mines doesn't care how strong you are.", "Bring water. Bring a lot of water."], "hint:ember_tyrant"),
    "precept_seven": ("ascendant", "ocean", ["Log entry. The lab is flooded. Of course it is.",
        "The scanner still works. Point it at anything with a light still on."], "hint:scanner"),
    "old_mother_keth": ("keeper", "swamp", ["The spores remember everyone they ever ate.",
        "Fire answers spores. Very loudly."], "hint:heat_spores"),
    "the_drowned_singer": ("keeper", "river", ["We sang in the halls under the water.",
        "The water rises when we stop. Can you hear it? We stopped."], "lore:choir"),
    "kael_twice_bound": ("hunter", "dark_forest", ["I bound my own brother as an Echo.",
        "He still follows me around. He's very polite about it.", "Bind only what you can live with."], "hint:echo"),
    "the_mirror_keeper": ("keeper", "ice_spikes", ["Your shadow is the part of you that stayed behind.",
        "In the mirrors it walks. Your spirit can walk with it."], "hint:spirit_mirror"),
    "nima_of_the_lanterns": ("hunter", "cherry_grove", ["There's a gate that's a market.",
        "Nobody fights. Everybody haggles.", "The Broker likes people who say no to his first price."], "hint:lantern_market"),
    "the_last_ascendant": ("ascendant", "windswept_hills", ["We built the Choir to wake people up.",
        "It woke them up all the way down.", "If it's talking to you, it already knows how this ends. Ask it anyway."], "lore:choir_built"),
    "dorr_of_the_bone_orchard": ("keeper", "stony_peaks", ["The dead get up once down there.",
        "Kill them like you mean it twice."], "hint:bone_orchard"),
    "the_one_who_never_slept": ("sleeper", "savanna", ["I stayed awake for three nights to see what would happen.",
        "My eyes learned the dark. My heart did not.", "Sleep, friend. But maybe once, don't."], "hint:night_eyes"),
    "warden_imre": ("hunter", "taiga", ["I held my home through nine breaks.",
        "On the tenth, I wasn't home.", "Build walls. Build friends. Same thing, eventually."], "hint:hearth_breaks"),
    "the_gardener_below": ("keeper", "lush_caves", ["The Hanging Gardens grow toward a sun that isn't there.",
        "When the wind comes, hold the vines."], "hint:gardens"),
    "sela_who_flew": ("hunter", "jagged_peaks", ["I learned to fly at A-rank.", "Then I learned why they put ceilings in gates.",
        "Fly home. Walk in."], "hint:flight"),
    "the_signal_once": ("sleeper", "mushroom_fields", ["It talked to me too.", "It said I'd be the one.",
        "It wasn't lying. It just says it to everyone it loves."], "lore:signal_loops"),
    "brother_ost": ("keeper", "deep_dark", ["The Sovereign of Silence was my teacher.",
        "She took all the noise of the world into herself so we could sleep.", "Be gentle with her. She'll try to kill you."], "lore:sovereign_silence"),
    "the_mapmaker_of_strata": ("hunter", "beach", ["Ten floors down, then it stops being floors.",
        "I made it to seven. Light a Waystone every time. Every time."], "hint:waystones"),
    "the_quiet_one": ("sleeper", "pale_garden", ["...", "You're still. Good. Stay.", "The void isn't empty. It's listening."], "hint:meditation"),
}
for key, (faction, biome, lines, gives) in SPIRITS.items():
    write("spirit", key, {"name": key.replace("_", " ").title().replace("Of ", "of ").replace("The ", "the ").replace("Who ", "who "),
                           "faction": faction, "biome_hint": "minecraft:" + biome, "lines": lines, "gives": gives})

# ---------------------------------------------------------------------------------------------------------------------
# PRECURSOR LOGS: found in ruins (Above) and archives (Halo). Three factions disagree on purpose.
LOGS = {
    "a01_the_choir": ("ascendant", "The Choir, first note", "It reads a person and plays back who they could be. "
        "Subject 1 cried for an hour. Subject 2 lifted the test chamber. We are going to change everything."),
    "a02_layers": ("ascendant", "On layers", "Up, down, in, out. Four directions from any place a mind stands. "
        "The Deep is the problem. Everything the world refuses to feel collects there."),
    "a03_leaving": ("ascendant", "Ring test, final", "The Launch Ring holds. We go up tomorrow. The Seed cannot be healed. "
        "Only escaped. Anyone who says otherwise has been down there too long."),
    "a04_halo": ("ascendant", "From the Halo", "It is beautiful up here. We can see the whole world. "
        "We can also see the gates opening. It followed us. Of course it did."),
    "a05_regret": ("ascendant", "Unsent", "We should have stayed and felt it with them. Delete this."),
    "k01_vow": ("keeper", "The Keepers' vow", "Eight of us will go down. Each will carry one weight so the world doesn't have to. "
        "Hunger. Silence. Sleep. Machines. Mirrors. Storms. Roots. Ash."),
    "k02_court": ("keeper", "Court record, year 40", "The weights are heavier than we thought. Ilsa has stopped speaking. "
        "We call her the Sovereign of Silence now. She doesn't mind. She doesn't mind anything."),
    "k03_seed": ("keeper", "On the Seed", "It is not a place. It is a pressure. It can be carried. It cannot be escaped. "
        "The ones who went up will learn that."),
    "k04_bells": ("keeper", "Bell order", "Ring the bells at every gate we close. If the bells stop, a Keeper has turned."),
    "k05_last": ("keeper", "Last entry", "If you're reading this, a gate let you down far enough to find it. "
        "Don't free us all at once. Please."),
    "s01_dream": ("sleeper", "The shared dream", "We went in together. The dream is bigger inside than the world is outside. "
        "Nobody wants to wake up. That's the problem."),
    "s02_seed_dream": ("sleeper", "On the Seed", "The Seed is a dream the world is having. Wake the world and it ends. "
        "Nobody believes us because we're asleep."),
    "s03_hands": ("sleeper", "Teaching note", "Look at your hands. Count your fingers. Read a sign twice. "
        "If anything changes, you're dreaming, and you can do anything."),
    "s04_intent": ("sleeper", "On making", "In a dream we create and perceive our world at the same time. "
        "Hold a wall in your mind and ask it for a door. It will usually say yes."),
    "s05_recorder": ("sleeper", "Dream recorder manual", "Place on a bed frame. Sleep. The recorder keeps the dream. "
        "Do not record nightmares. We learned that the hard way."),
    "x01_signal": ("unknown", "Unsigned", "The Choir's voice keeps asking for the same person. Every generation. "
        "Same mistakes, slightly better. It's almost like it's trying."),
    "x02_cradle": ("unknown", "Unsigned, Seed approach", "There is a body in the light at the bottom. It's breathing. "
        "I didn't look at the face. I was afraid I'd know it."),
    "x03_colour": ("unknown", "Graffiti, Waystone 9", "THE COLOUR COMES BACK WHEN YOU FEEL IT. STOP RUNNING."),
}
for key, (faction, title, text) in LOGS.items():
    write("precursor_log", key, {"faction": faction, "title": title, "text": text})

# ---------------------------------------------------------------------------------------------------------------------
# RIVAL beats (by the player's rank only; patent rule).
RIVAL = {
    "d": ("D", "Vael", ["Oh. Hi. You opened this one? I was already inside. Sorry about the loot.",
                        "You're new. That's fine. Everyone's new. I was new for like a week.",
                        "See you at the top. Or the bottom. Depends on the gate."]),
    "c": ("C", "Vael", ["Second Awakening, huh. It's going to show you some options.",
                        "Whatever it shows you, don't let it pick for you. Pick."]),
    "b": ("B", "Vael", ["Red gate. Cover me? I'll owe you.", "(later) Thanks. I'm not going to joke about that one.",
                        "I'm not going to joke about that one for a whole day."]),
    "a": ("A", "Vael", ["The Signal talks to me too. Did you know that?",
                        "It said you'd be the one who makes it. It said that to me first.",
                        "So which one of us is it lying to?"]),
    "s": ("S", "Vael", ["(spirit, tethered in the Hollow Court) I went down alone to prove something.",
                        "I proved it. It was a stupid thing to prove.",
                        "If you cut me free, I'll owe you twice. I'm keeping count."]),
    "sovereign": ("SOVEREIGN", "Vael", ["Whatever you choose down there, I'll be up top.",
                                        "Somebody has to tell the town. I'll make you sound taller."]),
}
for key, (rank, name, lines) in RIVAL.items():
    write("rival", key, {"rank": rank, "name": name, "lines": lines})

# ---------------------------------------------------------------------------------------------------------------------
# SOVEREIGNS of the Hollow Court (boss ids match mobs module).
SOVEREIGNS = {
    "sovereign_hunger": ("Ilyr", "Sovereign of Hunger", "I carried every empty stomach for a thousand years. Are you full?",
                         "Thank you. It's yours now. Or no one's."),
    "sovereign_silence": ("Ilsa", "Sovereign of Silence", "...", "(for the first time in centuries, she laughs)"),
    "sovereign_sleep": ("Morrow", "Sovereign of Sleep", "You're dreaming this. Of course you are. Everyone is.",
                        "Wake up. Wake up. Wake up."),
    "sovereign_machines": ("Gearwright Prime", "Sovereign of Machines", "I keep everything running. Nothing is ever allowed to stop.",
                           "Oh. Stopping is quiet. I didn't know."),
    "sovereign_mirrors": ("The Reflection", "Sovereign of Mirrors", "I'm the part of you that stayed behind. Hello.",
                          "Take me with you. I'll be good."),
    "sovereign_storms": ("Kaivo", "Sovereign of Storms", "Every rage the world swallowed came here. Let me show you.",
                         "Huh. You let it pass through you. That's allowed?"),
    "sovereign_roots": ("Old Mother", "Sovereign of Roots", "Everything that was buried still grows. Down here it grows toward you.",
                        "Let them grow up. Let them see the sun."),
    "sovereign_ash": ("Cinder", "Sovereign of Ash", "Everything that burned left something. I'm what it left.",
                      "Plant something in me."),
}
for key, (name, title, intro, defeat) in SOVEREIGNS.items():
    write("sovereign", key, {"boss": "awaken:" + key, "name": name, "title": title, "intro": intro, "defeat": defeat})

# ---------------------------------------------------------------------------------------------------------------------
# ENDINGS
ENDINGS = {
    "become": ("Become the Signal", ["You lie down in the light.", "It's warm. It's familiar.",
               "Somewhere far above, a new world loads. A window flickers and fails.", "[ERROR] … candidate found.",
               "You know exactly what to tell them. Mostly."], "ascension"),
    "break": ("Break the Signal", ["You close your hand around the light and squeeze.", "The Choir stops singing.",
              "Up top, every gate goes quiet. They're just doors now.", "Nobody will tell you what to do anymore.",
              "It's terrifying. It's yours."], "calm_gates"),
    "wake": ("Wake up", ["You stop holding it away from you.", "Hunger. Silence. Sleep. Storms. All of it, for one breath.",
             "It doesn't kill you. It never could. It just needed somebody to feel it.",
             "The Court kneels. The pressure lets go.", "Up top, the colour comes back. Everywhere. All at once.",
             "Fog lifting off a sea of clouds. Dawn.", "Good morning."], "colour"),
}
for key, (title, lines, effect) in ENDINGS.items():
    write("ending", key, {"title": title, "lines": lines, "effect": effect})

# ---------------------------------------------------------------------------------------------------------------------
# SIGNAL LINES (event → line). The story module picks these for flavour toasts and Notices.
SIGNAL_LINES = {
    "first_contact": "[ERROR] … candidate found.",
    "level_up_after_gap": "Finally.",
    "directive_dismissed_many": "Noted. You'll do it your way. You always did.",
    "death_in_gate_pushed": "That one's on me. I said push.",
    "reassessment_ready": "You have outgrown this rank. Try not to make it weird.",
    "first_meditation": "Quiet. Good. Stay.",
    "first_power": "There it is.",
    "first_title": "They'll call you that now. You'll pretend not to like it.",
    "first_latent": "That was always in you. I just didn't know where.",
    "break_cleared": "The colour's back. Did you see it come back?",
    "first_echo": "It follows you because you were kind about it. Mostly.",
    "low_health_win": "Too close. Do that again and I'll worry.",
    "night_awake": "You didn't sleep. I noticed. Everyone noticed.",
    "first_stratum": "Down is a direction. So is home.",
    "strain": "Climbing hurts down here. Build a better way up.",
    "dream_death": "You died in a dream. Very dramatic. Get up.",
    "rival_win": "You beat Vael to it. Don't rub it in. Rub it in a little.",
    "sovereign_ready": "Sovereign. I've never said that to anyone and meant it. Well. Once.",
}
write("signal_line", "lines", SIGNAL_LINES)

# ---------------------------------------------------------------------------------------------------------------------
# TOWN voices by rank (Hearth NPCs).
TOWN = {
    "oren_blacksmith": ("Oren", "Blacksmith", "E", {
        "E": "Another hunter. Try not to die before you pay me.",
        "D": "D-rank. Your sword still looks like it's apologising. Bring it here.",
        "C": "Heard you picked a Path. Good. Indecision rusts.",
        "B": "Lord's hearts make the best steel. Don't tell the Healer I said that.",
        "A": "People ask me if I forged your gear. I say yes. Don't correct me.",
        "S": "I'm putting your name on the wall. Don't let it go to your head. Okay, a little.",
        "SOVEREIGN": "I made you a sword. It's not for fighting. It's for hanging over a fireplace. Yours."}),
    "mira_scout": ("Mira", "Scout", "D", {
        "D": "There's a light past the river nobody's been back from. Want it?",
        "C": "I mapped three new gates. One's ugly. You'll love it.",
        "B": "Something's walking around the old ruins at night. I didn't stay to ask.",
        "A": "The sky platforms are humming. They never hum.",
        "S": "I followed you to the Well once. Just to the edge. That's as far as I go.",
        "SOVEREIGN": "Draw me a map of the bottom someday? I'll frame it."}),
    "teodor_archivist": ("Teodor", "Archivist", "C", {
        "C": "You've been reading Precursor logs. They disagree with each other. Isn't it wonderful?",
        "B": "The Keepers went down. The Ascendants went up. The Sleepers went in. Nobody went home.",
        "A": "I think the Signal is older than the Choir. I think it's younger too. I need a drink.",
        "S": "Bring me anything from the Seed. A pebble. A sentence.",
        "SOVEREIGN": "When you come back, tell me which one of them was right. Or don't. I like not knowing."}),
    "sun_healer": ("Sun", "Healer", "B", {
        "B": "You got hit by a gate. How's the rest of you?",
        "A": "Sleep. Actually sleep. Dreams don't count as rest, I don't care what the Sleepers say.",
        "S": "You carry a lot. Put some of it down here. I'll watch it.",
        "SOVEREIGN": "Whatever you choose, come back and eat something."}),
}
for key, (name, role, arrives, lines) in TOWN.items():
    write("townsfolk", key, {"name": name, "role": role, "arrives_at": arrives, "lines": lines})

print("revelations", len(REVELATIONS), "spirits", len(SPIRITS), "logs", len(LOGS), "rival", len(RIVAL),
      "sovereigns", len(SOVEREIGNS), "endings", len(ENDINGS), "signal lines", len(SIGNAL_LINES), "town", len(TOWN))
