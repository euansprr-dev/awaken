"""Every power, judged one at a time, for the Powers in Full Voice plan (docs/powers-aaa). build_page.py reads this.

Verdicts: KEEP (keep it and polish), GOOD (good, needs a hook of its own), FLAT (works, forgettable, needs a real change),
RETHINK (start again from nothing), FOLD (folded into another power).
Each row: id, name, family, slot, verdict, now, after, right, boss, sound, first.
`first` is the System's line when you learn it: who had it first. Plain speech, read it aloud.
"""

P = []


def add(id, name, family, slot, verdict, now, after, right, boss, sound, first):
    P.append(dict(id=id, name=name, family=family, slot=slot, verdict=verdict, now=now, after=after, right=right, boss=boss, sound=sound, first=first))


# ---- Kinetic -----------------------------------------------------------------------------------------------------------------
add("ground_slam", "Ground Slam", "Kinetic", "Active", "FLAT",
    "A ring of damage round you, 4.5 blocks. From the air you dive first, up to +14 damage for height. Shockwave and the Leaper's slam now do most of this, and nothing about it says ground.",
    "The real ground answers. The blocks round you kick up in a ring and roll outward, drawn from whatever you're standing on, so sand and stone look and sound different. From high up it leaves a crater for ten seconds. Whatever the crest catches is left Lifted.",
    "Land it within half a second of touching down, or right on top of something.",
    "Heavy stance hit, more the further you fell.",
    "A dropped wall: a sub thud first, then a drum roll of blocks travelling away from you. The surface changes the top layer: gravel hiss, sand sigh, stone crack.",
    "Tomas Reyne worked a pile driver for thirty years. When the Choir read him it found he'd stopped needing the machine.")
add("leap", "Leap", "Kinetic", "Movement", "FLAT",
    "A big forward bound with a small blast on landing. The Ascent now gives you double jumps, dives and slams, so this mostly repeats them.",
    "A hunting leap. Hold to crouch and a landing circle shows up to 24 blocks out. Let go and you arrive there, already striking. It counts as a link in your movement chain, so it joins the Ascent instead of competing with it.",
    "Let go in the half second the crouch is fully wound.",
    "Medium stance, doubled if you land in an opening.",
    "Knees creak, a ground thump as you leave, wind that rises and falls with the arc, then a landing body-thud.",
    "Nadia Orrin was late for a ferry and she made it. The ferry was already a quarter of a mile out.")
add("haymaker", "Haymaker", "Kinetic", "Active", "KEEP",
    "Hold to one second, let go, reach 4.5; a whiff throws a ring. The best idea in the family, because you can feel the wind-up. Its charge sound is the same file the Railgun uses.",
    "Keep it and polish it. A small bright window at full charge. Hit inside it and time hitches, the target folds, flies and cracks the wall it lands against. Hold too long and the shine fades into an ordinary full punch.",
    "Let go during the shine, about a fifth of a second.",
    "The biggest stance hit of any starter.",
    "In the taster. Knuckles and a straining shoulder climbing to a thump at the peak, torn air, meat and stone, a long sub drop.",
    "Dov Harlan threw one punch in his whole life that mattered. The Choir kept a copy of it. That's what you're holding.")
add("terrain_rip", "Terrain Rip", "Kinetic", "Active", "GOOD",
    "Tears up to nine real surface blocks out and throws them straight away, and the hole fills back in. A strong idea that's over in one quick press.",
    "You keep the torn-up chunk over your head while you aim, with a line showing where it lands, and the blocks grind against each other as you hold it. Press again in flight to burst it.",
    "Burst it in the last moment before it lands.",
    "Medium stance. A boulder can be caught with Telekinesis for a Duet.",
    "Roots tearing and stone grinding as it comes up, a groan while you hold it, a long throw, a dense wet thud.",
    "Subject 2, in the Precursors' own notes. They wrote that she lifted the test chamber. They didn't write that she was trying to get out of it.")
add("shockwave", "Shockwave", "Kinetic", "Active", "GOOD",
    "A stomp and a cone out to 9 blocks. A good starter with a proper stomp. The wave itself is only light running along the ground.",
    "The wave travels through the real surface. Blocks ripple and climb slopes as it passes, and you can watch it go. The crest at the far end hits hardest and throws foes up.",
    "Catch a foe on the crest, the last third of its range.",
    "Light stance. Small things are left Lifted.",
    "Keep the authored stomp. Add a low rolling rumble that moves away from you and knocks once for each foe it meets.",
    "Greta Moss stamped her foot at her brother in a kitchen in the old town. They found the kitchen in the next street.")
add("brace", "Brace", "Kinetic", "Active", "FLAT",
    "Three seconds at 60% less damage and no knockback, and melee attackers take a share back. It pays you to stand still, and the Arms guard now does that job better.",
    "A parry with your whole body. Press as a blow arrives and you stop it dead: the hit is soaked up and thrown back as a wave when you let go. Mistime it and you still get today's 60%.",
    "The first third of a second after you press.",
    "A timed Brace counts as a deflect: it opens a riposte and a Weave.",
    "A bronze door slamming shut inside your chest. A timed one rings like a struck gong.",
    "Ansel Tray held a door for nine hours so the people behind it could get out. Nobody ever told him he could stop.")
add("thunderclap", "Thunderclap", "Kinetic", "Active", "GOOD",
    "Radius 7 lightning and a stun, one press. At this range it plays like a recoloured Ground Slam.",
    "A held clap. Your hands pull apart while you hold; let go and the air breaks in a forward wedge. Everything Charged nearby goes off with it. In rain, or standing in water, it reaches twice as far.",
    "Clap into a blow that's winding up.",
    "Heavy stance when clapped into a wind-up.",
    "A dry hand-crack right at your ears, then real thunder rolling off across the land for four seconds.",
    "Yara Venn clapped once to get a room's attention. It worked. They rebuilt the room.")

# ---- Velocity ----------------------------------------------------------------------------------------------------------------
add("dash", "Dash", "Velocity", "Movement", "GOOD",
    "A fast steered dash on a 0.7 second cooldown. Reliable, and the Ascent's air dash and the Arms dodge now sit right next to it.",
    "The move that goes through things. Through a foe you leave a delayed cut. Through a blow, time hitches for one beat and it counts as a perfect dodge.",
    "Go through a blow in its last third of a second.",
    "Counts as a perfect dodge: 10 on its stance, and it opens a Weave.",
    "Tape ripped fast, a doppler past your ear, and a small air pop when you arrive.",
    "Lio Sandoval ran messages across Carillon for eleven years and was never once late. He died running one. It got there.")
add("flow", "Flow", "Velocity", "Active", "KEEP",
    "A time bubble, radius 18, a quarter speed for four seconds. The headline power. Mobs slow down but the world's sound doesn't.",
    "Slow everything. Rain hangs, arrows hang, and every sound that starts while it runs plays lower and duller. The marks you leave all go off together when it ends.",
    "Start it just before a blow lands on you and it lasts a second longer.",
    "Bosses run at half speed instead of a quarter, and their tells stretch with them.",
    "The world's pitch falls away, your heartbeat comes up close, and time comes back in a rising rush.",
    "When Sera Quill was nine she fell off a roof, and she said everything went slow on the way down. She spent the rest of her life trying to get back to that second.")
add("afterimage", "Afterimage", "Velocity", "Active", "FLAT",
    "Leaves a body that bursts. Close to Decoy and easily forgotten.",
    "It remembers your last two seconds. Your ghost replays them where you did them, at half strength, blows and casts included.",
    "Cast it straight after a big hit, so the ghost repeats the big hit.",
    "The same stance as whatever it repeats, halved.",
    "Your own last sound again, thin and a little backwards.",
    "Pim Arlen left a room so fast that part of him stayed in it. His friends used to say goodnight to it.")
add("wall_run", "Wall Stride", "Velocity", "Passive", "GOOD",
    "A passive that lengthens the Ascent's wall-run. It does its job and belongs with the body's moves.",
    "Keeps that job. While you're on a wall a cast counts as a moving cast, and kicking off a wall into a strike adds the run's speed to it.",
    "Cast in the instant you leave the wall.",
    "Nothing of its own; it feeds your next hit.",
    "A quick patter of soles on whatever the wall is made of, climbing in pitch with the length of the run.",
    "Nobody told Ines Cardo that walls were for stopping you. She was four, and they never managed to explain it to her afterwards.")
add("water_run", "Water Run", "Velocity", "Passive", "FLAT",
    "Run on water while sprinting. It still uses vanilla splash particles, which breaks your look rule.",
    "A real wake that fans out behind you and lasts. Skip across at speed, leaving rings where your feet touched.",
    "Hit the water at a sprint out of a jump without breaking stride.",
    "Not a fight power. Foes you soak count as wet for Charge.",
    "Slapping sheets of water that climb to a steady hiss as you speed up.",
    "Jory Wake's boat sank a mile out. He says he didn't want to get his boots wet, and nobody can tell if he's joking.")
add("lightning_step", "Lightning Step", "Velocity", "Movement", "GOOD",
    "A 14-block flash that shocks the line. Good, and the fourth dash in the game.",
    "The line stays live. For a second and a half the path you flashed down is a rail of current, and pressing again runs you back along it. Anything on it is left Charged.",
    "Run back along it while something is standing on the line.",
    "Light stance each pass. Once it's Charged your other hits arc off it.",
    "A capacitor snap, a bright crack on arrival, and the line sizzling away behind you.",
    "Kasia Thorne was struck on a hill at sixteen and ran home before the thunder got there. She was grounded for it.")

# ---- Flight ------------------------------------------------------------------------------------------------------------------
add("hover", "Hover", "Flight", "Active", "FLAT",
    "Hang in the air for five seconds. Pleasant at E rank, then the Ascent gives you far better control in the air.",
    "An air stance. While you hover every cast has a third more range and counts as from above. You can drift and aim, and a second press drops you like a stone.",
    "Start it at the very top of a jump and the first second is free.",
    "Nothing of its own; it sets up the next cast.",
    "A soft pressure hum with a slow wobble. It thins and shakes as your energy runs out.",
    "Old Mirren sat down on nothing one afternoon, about a foot up, because her knees hurt. That's all this was at first.")
add("flight", "Flight", "Flight", "Movement", "FLAT",
    "Free flight for 10 energy plus 6 a second, unlocked at rank A. It overlaps Wings and Skyborn and has no job of its own.",
    "Flight for fighting. Wings take you far. This one stops dead, strafes and turns on a coin, so you can hang in a boss room and keep moving. At top speed a boom hits what's under you.",
    "Reach top speed, then turn hard. The boom goes off in the turn.",
    "The boom is a light stance hit.",
    "Pressure, not engines: wind at your ears that follows your speed, and one clean boom.",
    "Cael Aster was the first one who went up on purpose. He came down once, to say it was worth it.")
add("gravity_well", "Gravity Well", "Flight", "Active", "GOOD",
    "A vortex, radius 6, four seconds. Good and useful. It makes one sound at the start and nothing for the other three and a half seconds.",
    "It bends what you see. Light curves round it (with Lens on) and real loose things fly in: drops, arrows, sand, leaves. Shots aimed through it get pulled off course.",
    "Set it where something is about to land.",
    "Can't pull a boss. It pulls the adds, eats the shots, and fills stance while the boss stands in it.",
    "In the taster. A throb you feel more than hear, noise sucking inward in pulses, debris rattling round, then a swallowed thud.",
    "Ruth Lenner lost a son, and after that everything around her kept falling toward the place she was sitting. The Choir read it and called it a talent.")
add("telekinesis", "Telekinesis", "Flight", "Active", "KEEP",
    "Lift and throw one creature. The fantasy is strong. On a boss it refuses with \"too heavy\".",
    "It catches things out of the air. Any shot coming your way stops dead while you hold, and you send it back where you look. Creatures and torn-up ground work as they do now.",
    "Catch the shot in the last moment before it hits.",
    "Never refuses. You grab a limb for a beat: its next blow is held a moment longer, plus a stance hit.",
    "A strained hum that climbs with the weight, small cracks while you hold, a snap when you throw.",
    "Hanno Vik wanted the salt and didn't want to get up. He's the reason the Choir started taking laziness seriously.")
add("meteor_dive", "Meteor Dive", "Flight", "Active", "FLAT",
    "Launches you up, then brings you down hard, more for height. The Leaper's dive and Ground Slam's air version already do this.",
    "A strike from anywhere in the air. A circle shows on the ground up to 40 blocks away and you go there on a burning slant. The impact melts a shallow bowl that cools over ten seconds.",
    "Go at the instant the circle locks.",
    "Heavy stance, and a way across a big room.",
    "A re-entry roar that climbs, then a deep boom with a glassy crackle as the ground cools.",
    "Tess Roan jumped off the Halo. She'd been told you couldn't.")
add("gravity_flip", "Gravity Flip", "Flight", "Ultimate (new slot)", "RETHINK",
    "A hidden power that throws things up and drops them. It skips anything heavy, has no variants and is rarely seen.",
    "Becomes Flight's ultimate. Everything nearby lets go of the ground and falls upward, drops and loose blocks too, for two seconds. Press again and it all comes down at once.",
    "Press again just as it reaches the top.",
    "The boss stays down. It takes the fall of everything above it as stance.",
    "A backwards thump, floating quiet with faint whistles, then a rain of impacts.",
    "Nobody knows who this was. The reading came up from under the Inverted Sea with the name scratched off.")

# ---- Energy ------------------------------------------------------------------------------------------------------------------
add("beam", "Beam", "Energy", "Active", "KEEP",
    "A held beam with a proper gather, loop and end. One of the best powers in the game.",
    "Keep it. It marks the world more: a glowing scorch line that lasts, and leaves catch. Held on one target it climbs to a white peak, and letting go there ends in a pulse.",
    "Let go at the white peak, about two seconds on one target.",
    "Light stance, steady.",
    "Keep the authored set. Add a heat creak that grows with time on target, and a layer per level.",
    "Ilan Soto looked at something and wanted it gone, and then it was. He spent the rest of his life being careful where he looked.")
add("nova", "Nova", "Energy", "Active", "FLAT",
    "A burst round you. The third point-blank burst in the game, behind Ground Slam and Frost Nova.",
    "A burst you build while you keep moving. Press and you swell with light for half a second, free to run, then press again to let it go. It's the power that pairs with everything.",
    "Press again as the swelling peaks.",
    "Medium stance.",
    "An in-breath of rising light, then a soft huge foom and a high ring.",
    "Maren Ash held her breath as a child until she went bright. The other kids dared her. They stopped.")
add("barrier", "Barrier", "Energy", "Active", "FLAT",
    "An absorption shield for ten seconds and a shock when it breaks. Fine, and forgotten once it's up.",
    "A plate you aim. It curves in front of you and turns with your look. Shots it takes are stored, and a second press throws them back as one blast.",
    "Raise it in the moment a shot arrives and it's reflected.",
    "Blocks ranged hits. The stored blast is a medium stance hit.",
    "A glassy hum. Every hit it takes rings one note higher, so you can hear it filling. It breaks with a shatter and a drop.",
    "A schoolteacher called Wen Hadley stood in front of thirty children and said no. It was a very good no.")
add("charged_shot", "Charged Shot", "Energy", "Active", "KEEP",
    "Hold, let go. Rebuilt on 30 October with its own hands and six sounds. You haven't heard it yet.",
    "Keep it. A window at the peak makes it pierce. Hold past that and it grows unstable and hurts you a little, for a bigger shot.",
    "Let go on the ping.",
    "Medium stance.",
    "Already its own. First on your listening page.",
    "Piet Rennick spent six years on a single shot and missed. The second one took four seconds.")
add("overload", "Overload", "Energy", "Ultimate", "GOOD",
    "A one-second gather, a blast radius 9, then +25% and half cost for eight seconds. Strong. 60 energy on a 60 second timer.",
    "Fills from how you fight instead of a timer. During the gather everything you've left Charged lights up and goes off with it.",
    "Let it go when the most things are Charged.",
    "Massive stance.",
    "A swelling choir with circuits crackling inside it, a beat of silence, then a sub you feel in your chest.",
    "Dagny Holt's heart stopped and started forty times on the way down. The last time, it took the whole street with it.")
add("prism_lance", "Prism Lance", "Energy", "Ultimate", "FLAT",
    "The Prism path's ultimate: a lance that splits into four seeking beams at the first thing it hits. It reads as Beam at a higher tier.",
    "A fan of five colours you sweep like an open hand, each one a different Force. Hold and they close into one.",
    "Close them on an opening.",
    "Heavy stance when closed.",
    "Five detuned voices in a wide chord that resolves into a single note as they close.",
    "Ottilie Graves taught glassblowers. She could make sunlight do as it was told, and she was very strict with it.")

# ---- Shadow ------------------------------------------------------------------------------------------------------------------
add("shadow_step", "Shadow Step", "Shadow", "Active", "KEEP",
    "Teleports behind a target within 16 blocks, next hit x1.6. A starter and a really good one. With nothing aimed it just blinks 8 blocks ahead.",
    "Keep it. With nothing in sight it steps you to the nearest shadow instead of straight ahead. Stepping behind something mid-swing is the big one: the blow goes through where you were.",
    "Step while it's swinging.",
    "Your next hit counts as an opening.",
    "In the taster. A breath taken in and cut off, two frames of nothing, then a low slice.",
    "Nell Aubry used to sneak up on her sister. Then she stopped needing the floor in between.")
add("cloak", "Cloak", "Shadow", "Active", "FLAT",
    "Six seconds unseen, drops aggro, first strike x1.8. A plain stealth button.",
    "Light matters now. In the dark it lasts twice as long and in full sun it flickers. Sound drops away for you too, so you feel hidden.",
    "Cloak while nothing is looking at you.",
    "Bosses keep tracking you by sound. It only breaks their lock for a beat.",
    "A cloth sweep, then everything you hear loses its top end until you come out.",
    "Emeric Dole wasn't invisible. People just stopped being able to care that he was there, and he says that's worse.")
add("echo_swap", "Echo Swap", "Shadow", "Active", "RETHINK",
    "Swaps you with your summon or decoy. Without one it refuses, which is the worst thing a power can do.",
    "Swap with anything you've hit in the last ten seconds, or with the ghost you leave behind every time you cast. Swap a foe into your own Well.",
    "Swap as a blow lands, and it hits whatever is standing there now.",
    "You can't move a boss, but you can swap with your own ghost, so it's never dead.",
    "A two-note whip from one ear to the other.",
    "Odile Marsh stood in for her twin at a wedding and nobody could tell. Eventually neither could they.")
add("legion", "Legion", "Shadow", "Ultimate", "GOOD",
    "The Shadowbinder's ultimate: four or more shadow soldiers for 20 seconds.",
    "They stand up in the shape of the last things you killed, as ink, for twenty seconds, then they're gone. You keep nothing, so the no-allies rule stands.",
    "Call it straight after a kill and they arrive already hitting.",
    "Draws its attention once. Medium stance if they land together.",
    "A low chant with marching ink. Every soldier's step lands on the beat.",
    "Corvin Hask went into a gate with forty people and came out with their shadows. He says they came along on their own.")

# ---- Mind --------------------------------------------------------------------------------------------------------------------
add("danger_sense", "Danger Sense", "Mind", "Passive", "GOOD",
    "Anything about to hit you glows red a moment before it does. Useful and quiet.",
    "Keep it. It gains a note: a small rising sting in the ear the blow is coming from, before you see it.",
    "Nothing to press. You learn to trust it.",
    "Reads the boss's tells early.",
    "A thin rising tone placed exactly where the danger is, a different one for Light, Heavy and Perilous.",
    "Hester Pike flinched eleven seconds before the bridge fell. Her whole family crossed it on a different day because of that.")
add("dominate", "Dominate", "Mind", "Active", "RETHINK",
    "Takes over a creature for eight seconds. On a boss it refuses with \"too strong\", right when you want it most.",
    "Give one order. Look at a creature, then look where you want it to go or what you want it to hit. Puppet King at level 5 gives three orders.",
    "Give the order while it's winding up and the blow goes where you say.",
    "It shakes you off, but it loses a beat. Its next blow is held longer.",
    "A soft two-tone pulse that gets into your head, and a low hum like someone about to speak.",
    "Lucan Ferro only ever said please. Nobody has ever been able to explain why that was so frightening.")
add("read_intent", "Read Intent", "Mind", "Active", "FLAT",
    "Ten seconds of seeing who's hunting you, and they take 15% more and deal 15% less. In practice a stat buff you press and forget.",
    "One creature, read. Its next blow shows on the ground before it starts, and your next cast on it lands Clean. The bonus is only against that one.",
    "Dodge a blow it showed you and the read lasts longer.",
    "Works in full. It reads the real move the boss has queued.",
    "A clock ticking in your head, speeding up before each blow.",
    "Aya Lindqvist played cards for money until nobody would sit down with her.")
add("decoy", "Decoy", "Mind", "Active", "FLAT",
    "A copy that draws attention. Afterimage's cousin.",
    "It walks, runs and looks like you. Cast it as you dodge and you vanish while it takes over. It can carry a trap.",
    "Cast it in the moment of a dodge.",
    "Draws one attack from a boss.",
    "Your own footsteps, very slightly out of time.",
    "Rafe Colby got out of every fight he was ever in by being somewhere else. He'd be the first to tell you so, if you could find him.")
add("foresight", "Foresight", "Mind", "Active", "GOOD",
    "A tenfold slow and the first blow misses. Flow's cousin.",
    "See the next second. A gold ghost of the boss plays its next blow on the real path it will take, and you hear the hit backwards before it lands.",
    "Step out of the ghost's blow at the last moment and your counter lands Clean.",
    "This is what it's for.",
    "A gold shimmer and a reversed swell of the hit that's coming.",
    "Joan Thessaly told the Choir what it was going to ask her, answered it, and left.")

# ---- Architect ---------------------------------------------------------------------------------------------------------------
add("wall", "Wall", "Architect", "Active", "FOLD",
    "Builds a wall on a 2 second cooldown. Wall, Ramp, Floor and Cone take four of your six keys, so nobody can play Architect and anything else.",
    "All four fold into one power, Blueprint, in one slot. Press it and Z X C V become wall, ramp, floor and cone for a few seconds, snapped to a grid. You keep every level you've earned.",
    "Place a wall as a shot arrives on that side and it sets twice as hard.",
    "A boss charging into a fresh wall stops dead and takes a stance hit.",
    "A ratchet tick and a set-stone knock.",
    "Ida Rask could build a wall faster than you could throw a rock at her. Her brothers checked.")
add("ramp", "Ramp", "Architect", "Active", "FOLD",
    "A ramp on a 2 second cooldown. Its level 2 step is called Blueprint, like four other powers'.",
    "A shape inside Blueprint, with its Rush, Rails and Launch Ramp steps at its own level.",
    "Lay one under a moving foe's feet.",
    "Fair footing in a boss room.",
    "A stone block sliding home up a slope.",
    "Ida's little brother Tobin couldn't climb the walls she built, so she made him a way up.")
add("floor", "Floor", "Architect", "Active", "FOLD",
    "A floor on a 2 second cooldown.",
    "A shape inside Blueprint, with Spring, Hold and Sky Bridge at its own level.",
    "Catch yourself with one in the last moment of a fall.",
    "Footing over a drop.",
    "A flat heavy slap of a slab landing.",
    "Ida's floor went out over the river and stopped a yard short of the far bank. She said that was all the stone she had.")
add("cone", "Cone", "Architect", "Active", "FOLD",
    "A small roof shape on a 2 second cooldown.",
    "A shape inside Blueprint, with Lantern, Shatterproof and Pagoda at its own level.",
    "Drop one over something and it's trapped for a beat.",
    "Shelter from what falls.",
    "A small roof clink that settles.",
    "Ida built the little roofs last, over people who had no time to get indoors.")
add("rampart", "Rampart", "Architect", "Active", "GOOD",
    "A small fort in one press.",
    "You draw it. Hold and sweep your look across the ground and the wall rises along the line you drew, gatehouse and all.",
    "Close the line back on itself and it rises twice as fast.",
    "Absorbs one charge.",
    "Stone courses grinding up one after another and a portcullis clang at the end.",
    "Bastien Cole walled in a whole village by himself in a night, and then he forgot to leave a gate.")
add("spire", "Spire", "Architect", "Active", "FLAT",
    "A pillar under your own feet that throws you up, along with whatever's next to you. Forgettable.",
    "It rises under what you're looking at and takes it up. Stand on one yourself for height, and a turret on top gets twice the range.",
    "Put it under feet in the moment they leave the ground.",
    "A small stance knock.",
    "A screech of rock rising, stopped dead with a stone clap.",
    "Vera Sund was tired of being looked down on, and it turns out that was a thing you could fix.")
add("collapse", "Collapse", "Architect", "Active", "RETHINK",
    "Brings down your constructs. With none it refuses.",
    "Brings down anything overhead: your builds, or ceilings and overhangs, which put themselves back afterwards.",
    "Let it go when something is directly underneath.",
    "Heavy stance when something falls on it.",
    "A long groan, then stone slamming in a rolling cadence.",
    "Everything Ida Rask built came down the day she died. That's what this is.")
add("citadel", "Citadel", "Architect", "Ultimate", "GOOD",
    "The Architect path's ultimate: a dome of hard light you heal and shelter inside.",
    "Fills from how you fight. The dome becomes a real fortress that folds up round you in sections you can hear, with slits and rails for your other powers.",
    "Call it under attack and the first hits feed it.",
    "Blocks one big blow, then cracks.",
    "A huge mechanism folding, ending on a single bell.",
    "They found Ida's last building at the bottom of a gate. It's still standing and nobody has worked out how to get in.")

# ---- Tech --------------------------------------------------------------------------------------------------------------------
add("grapple", "Snap Cable", "Tech", "Movement", "GOOD",
    "A quick pull. It overlaps the Tether, which is better for getting around.",
    "A weapon first. On a light target it drags it to you, on a heavy one it drags you to it, and letting go at the closest point slings you past.",
    "Let go at the closest point.",
    "Drags you in, and counts as a charge-in blow.",
    "A winch whine, a high taut ping and a fast reel.",
    "Tam Okoro fixed cable cars above the Deep Lift for thirty years. He fell four times and the cable always caught him.")
add("jet_burst", "Jet Burst", "Tech", "Movement", "FLAT",
    "A short boot thrust. One more way to go up.",
    "A thrust you aim and chain. It leaves a line of heat that burns what crosses it, and it cancels any recovery.",
    "Use it at the very top of a jump and it's free.",
    "Light stance through the burn line.",
    "An ignition snap and a crackling afterburner that tails off.",
    "Someone in the Above strapped an engine to a test pilot's boots. She wrote down \"again\" and nothing else.")
add("drone", "Drone", "Tech", "Active", "FLAT",
    "A drone that orbits you for twenty seconds and zaps whatever you're fighting. It runs itself.",
    "You aim it. Hit something with a power and your drone fires on that. It can catch a Beam and send it on.",
    "Land a hit right before it fires.",
    "Steady light stance.",
    "A tiny motor whine, servo ticks and quick high shots.",
    "Precursor maintenance unit 14 followed a woman home in the last year of the Halo. The notes don't say why she kept it.")
add("turret", "Turret", "Tech", "Active", "FLAT",
    "Set it and forget it.",
    "Where you put it matters. High up it sees further, and on a Spire twice as far. It overheats if it never stops firing, so you point it away.",
    "Put it on high ground.",
    "Steady light stance.",
    "A hydraulic unfold, a slow deep autocannon and a falling cool-down tick.",
    "The Ascendants left these guarding a door on the Halo. They're still guarding it. There's nothing behind it any more.")
add("railgun", "Railgun", "Tech", "Active", "KEEP",
    "Hold 1.2 seconds for a 48-block pierce. Its charge sound is the Haymaker's.",
    "Keep the idea. Holding zooms in a little, firing shoves you back, and the shot leaves a line of light. Level 5 punches a real tunnel that fills itself back in.",
    "Fire on the ready tick.",
    "The most stance you can do from range.",
    "In the taster. Coils tripping faster, a rising whine, a ready tick, a supersonic crack, an echo down the line.",
    "Imre Kallo built this to send a message to the Halo. It got there. It's still in the wall.")
add("nanite_skin", "Nanite Skin", "Tech", "Active", "FLAT",
    "Armour for a while. The third self-guard after Brace and Barrier.",
    "It flows to where you're being hit. The side taking the blows thickens and shows its scales.",
    "Raise it when you're below half and it also repairs.",
    "Takes a third of one big hit.",
    "Millions of tiny clicks moving round your body, like a swarm settling.",
    "Beatrix Lund had a body that repaired itself and a mind that couldn't keep up. She kept asking whose hand it was.")
add("drone_swarm", "Drone Swarm", "Tech", "Ultimate", "GOOD",
    "The Engineer path's ultimate: a swarm of drones.",
    "Fills from how you fight. It follows what you hit, and at level 5 forms one lance.",
    "Call it after a Clean hit and they all start there.",
    "Medium stance once they form up.",
    "A wall of small motors panned round you, rising as they form.",
    "Unit 14 had children, more or less. Nobody asked it to.")

# ---- Spirit ------------------------------------------------------------------------------------------------------------------
add("aura", "Aura", "Spirit", "Passive", "FLAT",
    "1.2 damage every two seconds to what's near you. You forget it's there.",
    "It shows your state. It takes your Path's colour and after a fight it's big and slow. Standing still for a second makes the next pulse stronger.",
    "Stand still for a second.",
    "Light.",
    "A low temple drone that only comes up near enemies.",
    "Everyone has this. Most people just call it being in a room with someone.")
add("spirit_palm", "Spirit Palm", "Spirit", "Active", "GOOD",
    "Reach 4.5, and an echo hits a second later. It refuses on a whiff.",
    "A whiff still throws a small palm. Right at arm's length, the echo comes back doubled.",
    "Land it at the very end of your reach.",
    "Medium stance from the echo.",
    "A bare-hand slap, then a deep singing bowl a second later.",
    "Brother Anselm's students used to bow to the wall he had just hit, because a second later it would fall over.")
add("mend", "Mend", "Spirit", "Active", "FLAT",
    "A heal. Press and wait.",
    "Breathe in. Hold, and let go at the top of the breath for a bigger heal. Getting hit breaks it. Players near you still share it.",
    "Let go at the top of the breath.",
    "It's a drink to a boss: under the new fights it will notice.",
    "A long in-breath, then a warm bowl note.",
    "Subject 1 cried for an hour. The notes leave out that everyone else in the room felt better afterwards.")
add("third_eye", "Third Eye", "Spirit", "Active", "FLAT",
    "Eight seconds of seeing every hostile through walls. Very like Read Intent.",
    "It sees the world, not the fight: sleeping bells, hidden kin, secret doors, cave mouths. The tool for everything we've built since.",
    "Hold still to widen it.",
    "Shows a weak side for a beat.",
    "A high glass ring as it opens, then tiny pings for each thing it finds, placed where they are.",
    "Zofia Harker could always find your keys, and she also knew what you had done with the money.")
add("cord_lash", "Cord Lash", "Spirit", "Active", "GOOD",
    "A whip with a pull. It overlaps Snap Cable.",
    "It ties two things together. Lash one, lash another, and they're pulled together and share hits.",
    "Second lash within a second.",
    "Ties a boss to one of its adds, and they share damage.",
    "A leather crack, then a taut string hum while they're bound.",
    "When Marek Sayle's sister left her body, he pulled her back with a rope that wasn't there.")
add("bulwark", "Bulwark", "Spirit", "Active", "GOOD",
    "A dome for eight seconds: allies inside take half damage, arrows dissolve and enemies get pushed out. The Warden's signature.",
    "It arrives with force: a ring throws everything out the moment you raise it. Party members inside count as guarded.",
    "Raise it just before the blow.",
    "Blocks one big blow.",
    "A huge bell bloom, felt more than heard.",
    "Hild Aran stood on the last bridge at Carillon and told the Deep it was closed. It took the Deep eleven days to argue.")
add("hundred_palms", "Hundred Palms", "Spirit", "Ultimate", "KEEP",
    "Two seconds of palms too fast to see, ending in one that launches. On paper the most exciting ultimate you have.",
    "Keep it and add a rhythm. Press along with the beat and you get more palms and a bigger last one.",
    "Press in time with the palms.",
    "Heavy stance, more on the beat.",
    "Slaps building to a blur and ending on one giant bowl.",
    "Master Oyelaran was asked how many hands he had, and he said he'd lost count. He had two.")
add("void_maw", "Void Maw", "Spirit", "Ultimate", "GOOD",
    "A vortex close to Gravity Well that feeds you, with an execute under 15% that skips bosses.",
    "It bites. Press again and the jaws shut on whatever's inside. It eats shots too.",
    "Shut the jaws when most are inside.",
    "No execute. It takes stance and heals you.",
    "A wet cavern groan and a final clack of teeth.",
    "Something came up out of the Deep and ate Sorrel Vance, and then for some reason it started doing what she wanted.")
add("waking_dream", "Waking Dream", "Spirit", "Active", "RETHINK",
    "A temporary door, bridge or stairs. A lovely idea with little use.",
    "What you look at becomes real for a while. Hold your look on a wall and a door opens that really leads through, for a minute.",
    "Hold still on the spot until it settles.",
    "A door in a boss room only lasts a blink.",
    "A slowed piano chord played backwards, and the room tone changing as you pass through.",
    "One of the Sleepers walked out of the dream for an afternoon and left a door behind. We still don't know where it goes.")
add("storm_call", "Storm Call", "Spirit", "Active", "FLAT",
    "Calls lightning down on up to five enemies near you, using a vanilla bolt. Stronger in the rain. With nobody near, it refuses.",
    "A real storm cell overhead. Rain starts, and bolts land where you look. Wet foes and metal carry it.",
    "Call it where there's water.",
    "Medium stance per bolt.",
    "Thunder walking closer, rain coming across the ground, one crack right overhead.",
    "Anneke Dorr was a fisherman's widow who stood on the beach and shouted at the sky, and it shouted back.")
add("ember_step", "Ember Step", "Spirit", "Movement", "FLAT",
    "A fiery dash, from walking through lava.",
    "You leave burning footprints and lava is safe to cross while it lasts.",
    "Go through something Chilled and it bursts into steam.",
    "A burn line it has to step across.",
    "A flare and an ember crackle that follows your feet.",
    "Bruno Cassar walked out of the foundry fire carrying three people. He left footprints in the iron.")
add("frost_nova", "Frost Nova", "Spirit", "Active", "FLAT",
    "A cold burst, and the only frost power in the game.",
    "Freezes the real ground and water. Cross a river on it, and it thaws by itself. Frozen foes shatter.",
    "Shatter a frozen foe with a Kinetic hit.",
    "Leaves it Chilled: slower wind-ups for a moment.",
    "Ice creaking and glassy crystals growing outward.",
    "Little Signe Aalto got lost in the snow when she was six and came home a week later perfectly warm.")
add("ghost_step", "Ghost Step", "Spirit", "Movement", "FLAT",
    "A dash through walls. The fifth dash in the game.",
    "It shows you the room on the other side for a moment before you go.",
    "Stop for a beat inside the wall and come out unseen.",
    "Passes through a blow.",
    "Everything muffles as if under water, you hear the room beyond, then a breath.",
    "Nobody's sure Lorne Pascoe ever really was in the room. He left a wife and two kids, and he's still expected for dinner.")
add("twin_strike", "Twin Strike", "Spirit", "Active", "RETHINK",
    "A hidden power that repeats your hits for a while.",
    "Your second self stands next to you for eight seconds and does everything you do, a beat late.",
    "Cast something big straight after.",
    "Its hits count as yours.",
    "Every sound you make comes again, just late.",
    "This one came up out of the Double Gate with two names on it. Both of them are yours. I'd like to know more about that.")

# ---- Five to add, only if you want them --------------------------------------------------------------------------------------
NEW = [
    ("Titan", "Kinetic ultimate", "You grow to three times your size for ten seconds. Your steps shake the ground and your swings sweep a room. Refused under a low ceiling, and you shrink if one closes in.",
     "Kinetic has no ultimate today. The game's size attribute is verified in 26.3, so this is cheap and it's the kind of moment people show their friends."),
    ("Stopped Clock", "Velocity ultimate", "Everything but you stops for three seconds. Bosses drag at a quarter speed instead of stopping, so a fight stays a fight.",
     "Velocity has no ultimate. Flow's time bubble already does the hard part."),
    ("Nobody Home", "Mind ultimate", "For six seconds nothing near you remembers you're there, and every one of them can be hit from the blind side.",
     "Mind has no ultimate and today's Mind powers are buffs. This one is pure mischief."),
    ("Toll", "Earned from the bells", "You strike the air like a bell. The Deep's creatures reel, and any sleeping bell within earshot answers faintly.",
     "The story says the Deep can't stand a bell's sound. Nothing you can do today uses that."),
    ("Second Try", "Rank A, Velocity", "You go back to where you stood four seconds ago, with the health you had then.",
     "The Signal is you from an earlier loop. This is the one power that says so with your hands."),
]

# ---- Duets: two powers that make something neither does alone ------------------------------------------------------------------
DUETS = [
    ("Gravity Well", "Nova", "Collapsing Star", "A Nova set off inside a Well is crushed down to a point for half a second, then goes off twice as wide."),
    ("Telekinesis", "Terrain Rip", "Orbit", "The rocks you tear up circle you. Press again and they go out like a shotgun."),
    ("Railgun", "Gravity Well", "Slingshot", "The slug bends round the Well and hits things behind cover."),
    ("Beam", "Blueprint", "Prism Wall", "A Beam through your own wall splits into a fan of five."),
    ("Frost Nova", "Ground Slam", "Shatterfield", "The frozen ground bursts up in a ring of ice."),
    ("Storm Call", "Telekinesis", "Lightning Rod", "Whatever you're holding takes every bolt in the storm."),
    ("Shadow Step", "Haymaker", "Knock Knock", "Charge first and step. You arrive behind them with the punch already there."),
    ("Flow", "Beam", "Slow Burn", "The Beam keeps its real speed in slowed time. One sweep tags everything, and it all burns when Flow ends."),
    ("Dash", "Shockwave", "Sonic Stamp", "Stomp at the end of a Dash and the wave carries your speed twice as far."),
    ("Decoy", "Nova", "Surprise Party", "Your decoy goes off as a Nova, wearing your face."),
    ("Afterimage", "Railgun", "Two Shots", "Your ghost fires it again from where you stood."),
    ("Drone", "Beam", "Relay", "The drone catches your Beam and turns it on a second target."),
    ("Turret", "Spire", "Crow's Nest", "A turret on a Spire sees twice as far and fires down on everything."),
    ("Snap Cable", "Haymaker", "Flying Fist", "All the speed of the reel goes into the punch."),
    ("Meteor Dive", "Gravity Well", "Deep Impact", "Land in the middle of your Well and the crater pulls everything in with you."),
    ("Cord Lash", "Ground Slam", "Reel and Stamp", "Everything you've tied together is hauled under your feet as you land."),
    ("Thunderclap", "Telekinesis", "Live Round", "Clap on something you're holding, then throw it. It goes off when it lands."),
    ("Barrier", "Mend", "Sanctuary", "Your heal fills the plate, and it heals whoever stands behind it."),
    ("Third Eye", "Railgun", "Sight Line", "See through the wall, shoot through the wall."),
    ("Read Intent", "Dash", "Already Gone", "Dash through a blow you read and your next hit is a guaranteed opening."),
    ("Echo Swap", "Gravity Well", "Switcheroo", "Swap a foe into the middle of the Well."),
    ("Spirit Palm", "Haymaker", "One-Two", "Body then soul: the echo lands inside the punch and they both come through."),
    ("Void Maw", "Gravity Well", "Swallow", "The Maw eats the Well and spits everything in it at what you're looking at."),
    ("Ember Step", "Frost Nova", "Fog Bank", "A wall of steam that hides you and scalds what walks through it."),
]

STATES = [
    ("Lifted", "Kinetic, Gravity", "In the air and helpless. Any hit slams it down hard for extra stance."),
    ("Charged", "Charge powers", "The next hit arcs to the nearest thing. Water and metal carry it further."),
    ("Burning", "Heat powers", "A cold hit makes steam. A heavy hit spreads the fire."),
    ("Chilled", "Frost powers", "Three layers freeze it. A heavy hit shatters what's frozen."),
    ("Held", "Tethers, grips, orders", "It can't get away. Your next hit lands from the blind side."),
    ("Read", "Mind powers", "You can see its next blow. Your next cast on it lands Clean."),
]

RECS = [
    ("direction", "Build the middle option: keep the ladder, add the systems underneath, and rework the weak powers one at a time."),
    ("clean", "Give every power a right way to press it (a Clean cast), judged by the server and heard as a ring."),
    ("stance", "Let powers fill a boss's stance, under the same braced and opening rules as blades, with the numbers set so a blade stays the main way to break it."),
    ("dead", "End every dead button: Dominate, Telekinesis, Echo Swap, Collapse and Gravity Flip all do something on a boss, and an aimed power with nothing to aim at does something smaller."),
    ("duets", "Add six States you can see on a creature, and 24 named Duets you find by trying things, recorded as N / ?? like the Weaves."),
    ("party", "Let Duets work between two players in a party. Your Well, their Nova."),
    ("ult", "Let the R key fill from how you fight, with no energy cost and no timer, and give your first ultimate as the reward for the D Proving."),
    ("move", "Make the eight movement powers attacks that move you, and count every cast as a link in the Ascent's chain."),
    ("blueprint", "Fold Wall, Ramp, Floor and Cone into one slot, Blueprint, keeping every level already earned."),
    ("world", "Build the showpieces: ground that moves, light that bends round a Well, shots you catch, a real door in a real wall, a gold ghost of the boss's next blow."),
    ("sound", "Give every power its own sound pack (about 340 new events in about 700 short files, roughly 10 MB), with a listening page per family where you pass or redo each one."),
    ("notes", "Let Clean casts ring a note tuned to the family, so a good run of play builds into a phrase. Can be turned off."),
    ("readings", "Give every power a person: a Reading in the System's voice when you learn it, and one more line each time it levels."),
    ("yours", "At level 5 of your starter, tell the player the last reading has their own name on it. \"You picked this one last time too.\""),
    ("cores", "Give Power Cores a source: each gate theme and Lord leans toward the families it has swallowed, so a player can go looking for one."),
    ("way", "At level 5 the System reads how you actually used the power and offers up to three ways to keep it. You pick one, and can change it later."),
    ("new", "Add the five new powers: Titan, Stopped Clock, Nobody Home, Toll and Second Try. They come last and can be skipped."),
    ("tasks", "Add a Powers tab to the K menu's Tasks page and a Choir's Record page to the Journal."),
    ("proof", "Build a playable proof first (Haymaker, Shockwave, Gravity Well, Railgun, with Clean, stance and their sound packs) and stop for you to play it before anything else."),
]

if __name__ == "__main__":
    from collections import Counter
    print(len(P), Counter(p["verdict"] for p in P), Counter(p["family"] for p in P))
    print(len(DUETS), "duets", len(RECS), "recs")
