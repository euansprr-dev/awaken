#!/usr/bin/env python3
"""What Carillon's people say (docs/carillon/briefs/M3-alive.md §6): short authored lines, picked in game per job,
tier and hour, with a seeded choice. No language model, nothing at runtime but this table.

  python3 -I tools/city/lines.py        writes src/main/resources/data/awaken/city/citizen_lines.json

Tiers (SPEC §16.6, first step): stranger (they don't know you), familiar (they've seen you about), known (they know
your name and say it). A line may start with a time tag: [m] morning (06-11), [d] day (11-17), [e] evening (17-21),
[n] night (21-06); untagged lines suit any hour. Placeholders: {name} (the player), {district}, {gate}, {hour}.
Keep lines short (one breath), in the town's voice: dry, warm, a little proud of its bells, never quest-giver speech.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
OUT = os.path.join(REPO, 'src/main/resources/data/awaken/city/citizen_lines.json')

# Greetings by job and tier; "any" is everyone's fallback and is always mixed in.
GREET = {
    "any": {
        "stranger": ["[m]Morning.", "[d]Afternoon.", "[e]Evening.", "[n]Late to be out.", "Mind yourself.",
                     "Mind the cobbles, they bite.", "Not from round here, are you.", "Hm.", "Lost? Follow the spire."],
        "familiar": ["You again.", "[m]Morning to you.", "[e]Evening. Not such a stranger now.", "Seen you about.",
                     "Keeping well?", "Back in the city, then.", "[n]Still up? Me too.", "You're the one with the... yes. Hello."],
        "known": ["[m]Morning, {name}.", "[d]Afternoon, {name}.", "[e]Evening, {name}.", "[n]Out late, {name}?",
                  "{name}! Good to see you.", "There's {name}. The whole ward's been talking.", "Bless the bells, it's {name}.",
                  "Mind how you go, {name}.", "{name}. You look like you walked through a gate. Again."],
    },
    "lamplighter": {
        "stranger": ["Out from under the pole, if you please.", "[e]Dark's coming. Lamps first, talk after."],
        "familiar": ["[e]Evening. Light's on its way.", "[m]Long night. All lit, all out.", "Stand under a lamp after dark. Trust me."],
        "known": ["[e]Evening, {name}. I'll keep your street bright.", "[m]Morning, {name}. Not one lamp failed. Well. One.",
                  "{name}! Under a lit lamp you're safe. Mostly."],
    },
    "bellringer": {
        "stranger": ["Every hour, on the hour. Since before your grandmother.", "Hands off the rope."],
        "familiar": ["Hear that? That's me.", "Count the strokes and you'll never be late."],
        "known": ["{name}! I rang an extra stroke for you. Nobody noticed.", "The bell knows your name now, {name}. So do I."],
    },
    "porter": {
        "stranger": ["Coming through!", "Mind the crate.", "Heavy one, this."],
        "familiar": ["Can't stop, the Quay's backed up.", "You again. Grab a crate, then."],
        "known": ["{name}! Carry this to the square? No? Worth asking.", "Morning, {name}. Fish, flour or bell metal. Guess the heaviest."],
    },
    "stallkeeper": {
        "stranger": ["[m]Fresh this morning. Looking or buying?", "Don't squeeze the produce."],
        "familiar": ["Back for more? Good taste.", "I'll keep the best one aside. Maybe."],
        "known": ["{name}! I saved you one. Don't tell the others.", "For you, {name}, the honest price."],
    },
    "baker": {
        "stranger": ["Still warm. Smell that?", "[m]First loaves are gone. Second batch soon."],
        "familiar": ["[m]Up before the bells, me.", "You've flour on your boots from standing too near."],
        "known": ["{name}, take a heel of bread. You look hungry.", "[m]Morning, {name}. The ovens remember you."],
    },
    "smith": {
        "stranger": ["Don't stand in the sparks.", "Hot iron. Stay back."],
        "familiar": ["Your blade's chipped. I can hear it from here.", "Mm. You again."],
        "known": ["{name}. Bring me that sword before it apologises again.", "Oren says you're good for it, {name}. We'll see."],
    },
    "watch": {
        "stranger": ["Move along.", "Keep it sheathed inside the walls.", "[n]Business at this hour?"],
        "familiar": ["Quiet day. Keep it that way.", "[e]Evening. Stay out of trouble."],
        "known": ["{name}. The Watch owes you one. Don't spend it all at once.", "[n]All quiet, {name}. Thanks to you, partly.",
                  "Safe walking, {name}."],
    },
    "crier": {
        "stranger": ["News at the hour! Stay and hear it."],
        "familiar": ["Back for the news? It's better than yesterday's."],
        "known": ["{name}! You're in tomorrow's news. Try to deserve it."],
    },
    "boatman": {
        "stranger": ["River's high today.", "Not a ferry. Not yet."],
        "familiar": ["Quay smells of rain.", "You again. Fancy the water?"],
        "known": ["{name}! When the boats run, you ride free.", "The river talks about you too, {name}. Mostly complaints."],
    },
    "washer": {
        "stranger": ["Mind the washing.", "Don't you dare splash."],
        "familiar": ["Good drying weather.", "Your coat could do with a wash. Just saying."],
        "known": ["{name}! Bring me that coat. Gate muck doesn't wash itself.", "[m]Morning, {name}. Lines full, good day."],
    },
    "mason": {
        "stranger": ["Mind the mortar.", "This wall's older than both of us."],
        "familiar": ["Every stone here's been put back twice.", "You break it, I mend it. That's the arrangement."],
        "known": ["{name}. If you knock anything over, tell me first.", "I mended a wall you leaned on, {name}. Lean gentler."],
    },
    "runner": {
        "stranger": ["Coming through!", "Can't stop!"],
        "familiar": ["Race you to the Roost. Some day.", "Fast feet. Not as fast as mine."],
        "known": ["{name}! The Guild still talks about your last run!", "Catch me on the roofs some day, {name}."],
    },
    "shopkeeper": {
        "stranger": ["Come in, come in. Or don't.", "[e]We close at dusk."],
        "familiar": ["Ah, the browser.", "Something caught your eye?"],
        "known": ["{name}! I dusted the good shelf for you.", "For you, {name}, I'll pretend the prices are fair."],
    },
    "tavernkeeper": {
        "stranger": ["Boots off the table.", "[e]Fire's lit inside."],
        "familiar": ["Your corner's free.", "[e]You look like you need a pint."],
        "known": ["{name}! Your seat by the fire's waiting.", "[e]First one's on the house, {name}. Second's on you."],
    },
    "clerk": {
        "stranger": ["Registry's that way. Bring your papers."],
        "familiar": ["Your file's getting thicker."],
        "known": ["{name}. Three forms with your name on. Good forms."],
    },
    "archivist": {
        "stranger": ["Quiet, please. The pages are sleeping."],
        "familiar": ["You read things. I can tell."],
        "known": ["{name}, the archive keeps a shelf for you now. A small shelf."],
    },
    "healer": {
        "stranger": ["If you're bleeding, the infirmary's on the Terrace."],
        "familiar": ["You're limping less. Good."],
        "known": ["{name}. Sit. Drink water. You never drink water."],
    },
    "miller": {
        "stranger": ["Flour gets everywhere. Sorry."],
        "familiar": ["The wheel's singing today."],
        "known": ["{name}! The wheel turns better when you're about. Superstition, but still."],
    },
    "child": {
        "stranger": ["Are you a hunter?", "Mum says don't talk to hunters.", "Is that a real sword?"],
        "familiar": ["It's you again!", "Can you do the jumpy thing?"],
        "known": ["{name}! I'm being you today!", "{name}, {name}! Watch me run!", "When I'm big I'm going to be {name}."],
    },
    "resident": {
        "stranger": ["In my day the bells were louder.", "Hmph."],
        "familiar": ["You've a kind face. For a hunter.", "Walk slower. You'll see more."],
        "known": ["Ah, {name}. My grandson won't stop talking about you.", "{name}! Come here, let me look at you."],
    },
}

# Oren, Mira, Teodor and Sun keep their Hearth voices (data/awaken/awaken/townsfolk/*.json).
NAMED = {
    "oren": {"stranger": ["Another hunter. Try not to die before you pay me."],
             "familiar": ["Your sword still looks like it's apologising. Bring it here."],
             "known": ["{name}. People ask if I forged your gear. I say yes. Don't correct me."]},
    "mira": {"stranger": ["Eyes up. There's always something past the river."],
             "familiar": ["Saw you on the wall walk. Not bad."],
             "known": ["{name}! There's a light past the river nobody's been back from. Want it?"]},
    "teodor": {"stranger": ["Mind the stacks. Some of them bite."],
               "familiar": ["You've been reading. Your eyes do the squint."],
               "known": ["{name}, the Precursor logs disagree with each other. Isn't it wonderful?"]},
    "sun": {"stranger": ["You're bleeding a little. Everyone is, here."],
            "familiar": ["Eat something. Then come back."],
            "known": ["{name}. How's the rest of you?"]},
}

# Said aloud to whoever is near (no name): the city's own noise.
CALLS = {
    "stallkeeper": {
        "bread": ["Bread! Still warm!", "Loaves, rolls, the good crusts!"],
        "fish": ["Fresh off the Quay!", "Eels, perch, river trout!"],
        "produce": ["Apples from the Garden Ward!", "Greens, roots, the lot!"],
        "cloth": ["Wool for the cold nights!", "Linen, fine as a bell's ring!"],
        "pottery": ["Jugs and pots, nothing chipped!", "Bowls for every table!"],
        "candles": ["Candles for the long nights!", "Beeswax, no smoke, no smell!"],
        "flowers": ["Flowers for the Choir steps!", "A posy for your sweetheart!"],
        "spice": ["Pepper, salt, a pinch of the far south!", "Spices! Smell before you buy!"],
        "tools": ["Nails, hinges, honest iron!", "A lantern for the dark streets!"],
        "cheese": ["Cheese! Old, older, oldest!", "Rounds from the High Terrace dairies!"],
    },
    "watch_night": ["[n]{hour} o'clock, and all's well!", "[n]{hour} o'clock! Lamps lit, gates shut!"],
    "porter": ["Make way!", "Crate coming through!"],
    "child": ["Can't catch me!", "I'm the Runner! I'm the Runner!"],
    "smith": ["Steady now.", "There. Good iron."],
    "baker": ["[m]First batch out!", "[m]Mind, it's hot!"],
}

# The crier's news: read at the posts. "city" lines always fit; the rest are chosen when the city's state says so.
NEWS = {
    "city": ["Hear ye! The bells of Carillon ring true today!", "Hear ye! The river's high, mind the lower quay!",
             "Hear ye! The Association seeks hunters of every rank!", "Hear ye! Market day, every day, in Choir Square!",
             "Hear ye! Lamps are lit at dusk. Stay in their light!", "Hear ye! The Runners' Guild wants fast feet!"],
    "burglary": ["Hear ye! A thief in {district} last night! Bar your shutters!", "Hear ye! Burglars about in {district}! The Watch asks for eyes!"],
    "burglary_caught": ["Hear ye! The {district} burglar is caught! Sleep easy!"],
    "pickpocket": ["Hear ye! Pickpockets in the market! Hands on your purses!"],
    "fire": ["Hear ye! Fire in the Works! The masons ask for patience!", "Hear ye! The Works burned last night. Help where you can!"],
    "cart": ["Hear ye! A cart ran wild on the Quay! Mind the crates!"],
    "tremor": ["Hear ye! A gate broke past the {gate}! Hunters, to the walls!"],
    "lamp_broken": ["Hear ye! A lamp is out on the avenue! Walk in pairs after dark!"],
    "about": ["Hear ye! {name} caught a thief! Carillon thanks you!", "Hear ye! {name} fought the fire in the Works!",
              "Hear ye! {name}, hunter, walks among us! Mind your manners!"],
}

# Events: who shouts what.
EVENT = {
    "burglary_wake": ["Thief! Stop, thief!", "Watch! WATCH!", "He's got the box!"],
    "burglary_thanks": ["You brought it back? Bless you!", "{name}, I'll not forget this. None of us will."],
    "watch_chase": ["Stop in the name of the Watch!", "After him!", "Got you!"],
    "thief": ["Out of my way!", "Move!"],
    "thief_caught": ["All right, all right!", "I give up!"],
    "pickpocket_cry": ["My purse! That one, there!", "Pickpocket!", "Stop him!"],
    "pickpocket_thanks": ["My purse! Oh, thank you!", "Here, take something for your trouble."],
    "fire_cry": ["Fire! Fire in the Works!", "Buckets! Get the buckets!", "Water! Pass it down!"],
    "fire_trapped": ["Help! I can't get out!", "The smoke! I can't see!", "Someone! Please!"],
    "fire_out": ["It's out. Thank the bells, it's out.", "We saved the walls, at least."],
    "fire_rescued": ["You came in for me. You came in!", "{name}... I owe you my life."],
    "cart_cry": ["Look out!", "The brake's gone!", "Mind your legs!"],
    "cart_thanks": ["Cheers, friend. Saved us an hour.", "{name}, you're welcome on the Quay any day."],
    "tremor_cry": ["A gate's broken past the {gate}! Hunters wanted!", "Stay inside the walls!"],
    "tremor_held": ["The Watch held the gate. Lost a field doing it.", "We held it. Barely."],
    "tremor_cleared": ["The gate's closed! {name} closed it!", "Thank you, hunter. The farm's safe."],
    "hit": ["Oi!", "What was that for?", "Watch! Watch!"],
    "hurt_walk": ["Just a bruise. I'm going to Sun.", "Ow. Infirmary, then."],
}


def main():
    doc = {"schemaVersion": 1, "generator": "tools/city/lines.py", "greet": GREET, "named": NAMED, "calls": CALLS,
           "news": NEWS, "event": EVENT}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    n = sum(len(v) for t in GREET.values() for v in t.values()) + sum(len(v) for t in NAMED.values() for v in t.values())
    n += sum(len(v) if isinstance(v, list) else sum(len(x) for x in v.values()) for v in CALLS.values())
    n += sum(len(v) for v in NEWS.values()) + sum(len(v) for v in EVENT.values())
    longest = max((l for t in GREET.values() for v in t.values() for l in v), key=len)
    print(f"{n} lines -> {os.path.relpath(OUT, REPO)} (longest greeting {len(longest)} chars)")


if __name__ == '__main__':
    main()
