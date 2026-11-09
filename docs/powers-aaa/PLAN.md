# Powers in Full Voice

Source for the approval page `plan.html` (built by `build_page.py` from `powers_data.py` and `scenes.py`). Nothing here is built yet.

## Verdicts

- Keep and polish: 8
- Good, needs a hook: 19
- Flat: 26
- Start again: 6
- Fold into one: 4

## The ten changes

1. **Clean casts: every press has a right way**: Each power gets its own right way, written by hand: a moment, an aim, a position or a move into it. It's listed on every card below. Do it and the cast lands Clean: about 25% more damage, more stance, about 40% of the energy back, and more mastery. The cue is at your crosshair. The server judges it, with two ticks of grace.
2. **Powers count on bosses**: Every power fills stance by weight through the existing `Strikes.posture`: Light 3, Medium 6, Heavy 12, Massive 20, Clean half as much again. The same braced and opening rules apply.
3. **No dead buttons**: An aimed power with nothing to aim at does something smaller. Every power gets a boss version. Wind-up powers check before they charge you.
4. **States and Duets**: Six States show on a creature's body: Lifted, Charged, Burning, Chilled, Held, Read. On top of those, 24 named Duets: two specific powers that make something neither can do alone. You find them by trying things, and they're counted N / ?? like Weaves.
5. **Movement powers attack, and casts join the chain**: The eight movement powers become attacks that move you. A cast counts as a link in the Ascent's chain, and a long enough chain gives On the Move casts. Wings stay for travel and Flight is for fighting in the air.
6. **The R key fills from how you fight**: R becomes a gauge. Clean casts, Duets, finishers and kills fill it, and it creeps up on its own so nobody is ever locked out. No energy cost. Your first ultimate is the reward for the D Proving, chosen from what you've actually done.
7. **The world answers**: The ground really comes up, in whatever you're standing on. Beam leaves a scorch. Frost freezes real water for a while. The Well pulls loose things and bends shots. Charge runs through water and metal. Flow slows every new sound through the game's own sound event. Every vanilla particle goes.
8. **A sound pack for every power**: Each power gets a charge or wind, a release, an impact, a tail, a Clean layer and a level 5 layer, two versions of each. Each family gets its own instruments. A sung Choir layer comes in at level 5. A mix budget keeps your own power and the biggest threat on top.
9. **Readings: every power has a person**: A Power Core is a recording the Choir took of a real person. The Deep is full of people who had them, which is why Lords drop Cores. Each power has a first bearer, one line when you learn it and one more per level. The Journal keeps them in The Choir's Record. At level 5 of your starter you find a second name on the reading.
10. **Progress you earn by playing**: Gate themes and Lords lean toward the families they've swallowed, with about a third staying random. At level 5 the System reads how you actually used the power and offers up to three ways to keep it, with the evidence. You pick one and can change it. New Powers tab through `Goals`.

## Every power

### Kinetic

**Ground Slam** (Active, Flat)

- Today: A ring of damage round you, 4.5 blocks. From the air you dive first, up to +14 damage for height. Shockwave and the Leaper's slam now do most of this, and nothing about it says ground.
- After: The real ground answers. The blocks round you kick up in a ring and roll outward, drawn from whatever you're standing on, so sand and stone look and sound different. From high up it leaves a crater for ten seconds. Whatever the crest catches is left Lifted.
- Right way: Land it within half a second of touching down, or right on top of something.
- Boss: Heavy stance hit, more the further you fell.
- Sound: A dropped wall: a sub thud first, then a drum roll of blocks travelling away from you. The surface changes the top layer: gravel hiss, sand sigh, stone crack.
- Reading: [Tomas Reyne worked a pile driver for thirty years. When the Choir read him it found he'd stopped needing the machine.]

**Leap** (Movement, Flat)

- Today: A big forward bound with a small blast on landing. The Ascent now gives you double jumps, dives and slams, so this mostly repeats them.
- After: A hunting leap. Hold to crouch and a landing circle shows up to 24 blocks out. Let go and you arrive there, already striking. It counts as a link in your movement chain, so it joins the Ascent instead of competing with it.
- Right way: Let go in the half second the crouch is fully wound.
- Boss: Medium stance, doubled if you land in an opening.
- Sound: Knees creak, a ground thump as you leave, wind that rises and falls with the arc, then a landing body-thud.
- Reading: [Nadia Orrin was late for a ferry and she made it. The ferry was already a quarter of a mile out.]

**Haymaker** (Active, Keep and polish)

- Today: Hold to one second, let go, reach 4.5; a whiff throws a ring. The best idea in the family, because you can feel the wind-up. Its charge sound is the same file the Railgun uses.
- After: Keep it and polish it. A small bright window at full charge. Hit inside it and time hitches, the target folds, flies and cracks the wall it lands against. Hold too long and the shine fades into an ordinary full punch.
- Right way: Let go during the shine, about a fifth of a second.
- Boss: The biggest stance hit of any starter.
- Sound: In the taster. Knuckles and a straining shoulder climbing to a thump at the peak, torn air, meat and stone, a long sub drop.
- Reading: [Dov Harlan threw one punch in his whole life that mattered. The Choir kept a copy of it. That's what you're holding.]

**Terrain Rip** (Active, Good, needs a hook)

- Today: Tears up to nine real surface blocks out and throws them straight away, and the hole fills back in. A strong idea that's over in one quick press.
- After: You keep the torn-up chunk over your head while you aim, with a line showing where it lands, and the blocks grind against each other as you hold it. Press again in flight to burst it.
- Right way: Burst it in the last moment before it lands.
- Boss: Medium stance. A boulder can be caught with Telekinesis for a Duet.
- Sound: Roots tearing and stone grinding as it comes up, a groan while you hold it, a long throw, a dense wet thud.
- Reading: [Subject 2, in the Precursors' own notes. They wrote that she lifted the test chamber. They didn't write that she was trying to get out of it.]

**Shockwave** (Active, Good, needs a hook)

- Today: A stomp and a cone out to 9 blocks. A good starter with a proper stomp. The wave itself is only light running along the ground.
- After: The wave travels through the real surface. Blocks ripple and climb slopes as it passes, and you can watch it go. The crest at the far end hits hardest and throws foes up.
- Right way: Catch a foe on the crest, the last third of its range.
- Boss: Light stance. Small things are left Lifted.
- Sound: Keep the authored stomp. Add a low rolling rumble that moves away from you and knocks once for each foe it meets.
- Reading: [Greta Moss stamped her foot at her brother in a kitchen in the old town. They found the kitchen in the next street.]

**Brace** (Active, Flat)

- Today: Three seconds at 60% less damage and no knockback, and melee attackers take a share back. It pays you to stand still, and the Arms guard now does that job better.
- After: A parry with your whole body. Press as a blow arrives and you stop it dead: the hit is soaked up and thrown back as a wave when you let go. Mistime it and you still get today's 60%.
- Right way: The first third of a second after you press.
- Boss: A timed Brace counts as a deflect: it opens a riposte and a Weave.
- Sound: A bronze door slamming shut inside your chest. A timed one rings like a struck gong.
- Reading: [Ansel Tray held a door for nine hours so the people behind it could get out. Nobody ever told him he could stop.]

**Thunderclap** (Active, Good, needs a hook)

- Today: Radius 7 lightning and a stun, one press. At this range it plays like a recoloured Ground Slam.
- After: A held clap. Your hands pull apart while you hold; let go and the air breaks in a forward wedge. Everything Charged nearby goes off with it. In rain, or standing in water, it reaches twice as far.
- Right way: Clap into a blow that's winding up.
- Boss: Heavy stance when clapped into a wind-up.
- Sound: A dry hand-crack right at your ears, then real thunder rolling off across the land for four seconds.
- Reading: [Yara Venn clapped once to get a room's attention. It worked. They rebuilt the room.]

### Velocity

**Dash** (Movement, Good, needs a hook)

- Today: A fast steered dash on a 0.7 second cooldown. Reliable, and the Ascent's air dash and the Arms dodge now sit right next to it.
- After: The move that goes through things. Through a foe you leave a delayed cut. Through a blow, time hitches for one beat and it counts as a perfect dodge.
- Right way: Go through a blow in its last third of a second.
- Boss: Counts as a perfect dodge: 10 on its stance, and it opens a Weave.
- Sound: Tape ripped fast, a doppler past your ear, and a small air pop when you arrive.
- Reading: [Lio Sandoval ran messages across Carillon for eleven years and was never once late. He died running one. It got there.]

**Flow** (Active, Keep and polish)

- Today: A time bubble, radius 18, a quarter speed for four seconds. The headline power. Mobs slow down but the world's sound doesn't.
- After: Slow everything. Rain hangs, arrows hang, and every sound that starts while it runs plays lower and duller. The marks you leave all go off together when it ends.
- Right way: Start it just before a blow lands on you and it lasts a second longer.
- Boss: Bosses run at half speed instead of a quarter, and their tells stretch with them.
- Sound: The world's pitch falls away, your heartbeat comes up close, and time comes back in a rising rush.
- Reading: [When Sera Quill was nine she fell off a roof, and she said everything went slow on the way down. She spent the rest of her life trying to get back to that second.]

**Afterimage** (Active, Flat)

- Today: Leaves a body that bursts. Close to Decoy and easily forgotten.
- After: It remembers your last two seconds. Your ghost replays them where you did them, at half strength, blows and casts included.
- Right way: Cast it straight after a big hit, so the ghost repeats the big hit.
- Boss: The same stance as whatever it repeats, halved.
- Sound: Your own last sound again, thin and a little backwards.
- Reading: [Pim Arlen left a room so fast that part of him stayed in it. His friends used to say goodnight to it.]

**Wall Stride** (Passive, Good, needs a hook)

- Today: A passive that lengthens the Ascent's wall-run. It does its job and belongs with the body's moves.
- After: Keeps that job. While you're on a wall a cast counts as a moving cast, and kicking off a wall into a strike adds the run's speed to it.
- Right way: Cast in the instant you leave the wall.
- Boss: Nothing of its own; it feeds your next hit.
- Sound: A quick patter of soles on whatever the wall is made of, climbing in pitch with the length of the run.
- Reading: [Nobody told Ines Cardo that walls were for stopping you. She was four, and they never managed to explain it to her afterwards.]

**Water Run** (Passive, Flat)

- Today: Run on water while sprinting. It still uses vanilla splash particles, which breaks your look rule.
- After: A real wake that fans out behind you and lasts. Skip across at speed, leaving rings where your feet touched.
- Right way: Hit the water at a sprint out of a jump without breaking stride.
- Boss: Not a fight power. Foes you soak count as wet for Charge.
- Sound: Slapping sheets of water that climb to a steady hiss as you speed up.
- Reading: [Jory Wake's boat sank a mile out. He says he didn't want to get his boots wet, and nobody can tell if he's joking.]

**Lightning Step** (Movement, Good, needs a hook)

- Today: A 14-block flash that shocks the line. Good, and the fourth dash in the game.
- After: The line stays live. For a second and a half the path you flashed down is a rail of current, and pressing again runs you back along it. Anything on it is left Charged.
- Right way: Run back along it while something is standing on the line.
- Boss: Light stance each pass. Once it's Charged your other hits arc off it.
- Sound: A capacitor snap, a bright crack on arrival, and the line sizzling away behind you.
- Reading: [Kasia Thorne was struck on a hill at sixteen and ran home before the thunder got there. She was grounded for it.]

### Flight

**Hover** (Active, Flat)

- Today: Hang in the air for five seconds. Pleasant at E rank, then the Ascent gives you far better control in the air.
- After: An air stance. While you hover every cast has a third more range and counts as from above. You can drift and aim, and a second press drops you like a stone.
- Right way: Start it at the very top of a jump and the first second is free.
- Boss: Nothing of its own; it sets up the next cast.
- Sound: A soft pressure hum with a slow wobble. It thins and shakes as your energy runs out.
- Reading: [Old Mirren sat down on nothing one afternoon, about a foot up, because her knees hurt. That's all this was at first.]

**Flight** (Movement, Flat)

- Today: Free flight for 10 energy plus 6 a second, unlocked at rank A. It overlaps Wings and Skyborn and has no job of its own.
- After: Flight for fighting. Wings take you far. This one stops dead, strafes and turns on a coin, so you can hang in a boss room and keep moving. At top speed a boom hits what's under you.
- Right way: Reach top speed, then turn hard. The boom goes off in the turn.
- Boss: The boom is a light stance hit.
- Sound: Pressure, not engines: wind at your ears that follows your speed, and one clean boom.
- Reading: [Cael Aster was the first one who went up on purpose. He came down once, to say it was worth it.]

**Gravity Well** (Active, Good, needs a hook)

- Today: A vortex, radius 6, four seconds. Good and useful. It makes one sound at the start and nothing for the other three and a half seconds.
- After: It bends what you see. Light curves round it (with Lens on) and real loose things fly in: drops, arrows, sand, leaves. Shots aimed through it get pulled off course.
- Right way: Set it where something is about to land.
- Boss: Can't pull a boss. It pulls the adds, eats the shots, and fills stance while the boss stands in it.
- Sound: In the taster. A throb you feel more than hear, noise sucking inward in pulses, debris rattling round, then a swallowed thud.
- Reading: [Ruth Lenner lost a son, and after that everything around her kept falling toward the place she was sitting. The Choir read it and called it a talent.]

**Telekinesis** (Active, Keep and polish)

- Today: Lift and throw one creature. The fantasy is strong. On a boss it refuses with "too heavy".
- After: It catches things out of the air. Any shot coming your way stops dead while you hold, and you send it back where you look. Creatures and torn-up ground work as they do now.
- Right way: Catch the shot in the last moment before it hits.
- Boss: Never refuses. You grab a limb for a beat: its next blow is held a moment longer, plus a stance hit.
- Sound: A strained hum that climbs with the weight, small cracks while you hold, a snap when you throw.
- Reading: [Hanno Vik wanted the salt and didn't want to get up. He's the reason the Choir started taking laziness seriously.]

**Meteor Dive** (Active, Flat)

- Today: Launches you up, then brings you down hard, more for height. The Leaper's dive and Ground Slam's air version already do this.
- After: A strike from anywhere in the air. A circle shows on the ground up to 40 blocks away and you go there on a burning slant. The impact melts a shallow bowl that cools over ten seconds.
- Right way: Go at the instant the circle locks.
- Boss: Heavy stance, and a way across a big room.
- Sound: A re-entry roar that climbs, then a deep boom with a glassy crackle as the ground cools.
- Reading: [Tess Roan jumped off the Halo. She'd been told you couldn't.]

**Gravity Flip** (Ultimate (new slot), Start again)

- Today: A hidden power that throws things up and drops them. It skips anything heavy, has no variants and is rarely seen.
- After: Becomes Flight's ultimate. Everything nearby lets go of the ground and falls upward, drops and loose blocks too, for two seconds. Press again and it all comes down at once.
- Right way: Press again just as it reaches the top.
- Boss: The boss stays down. It takes the fall of everything above it as stance.
- Sound: A backwards thump, floating quiet with faint whistles, then a rain of impacts.
- Reading: [Nobody knows who this was. The reading came up from under the Inverted Sea with the name scratched off.]

### Energy

**Beam** (Active, Keep and polish)

- Today: A held beam with a proper gather, loop and end. One of the best powers in the game.
- After: Keep it. It marks the world more: a glowing scorch line that lasts, and leaves catch. Held on one target it climbs to a white peak, and letting go there ends in a pulse.
- Right way: Let go at the white peak, about two seconds on one target.
- Boss: Light stance, steady.
- Sound: Keep the authored set. Add a heat creak that grows with time on target, and a layer per level.
- Reading: [Ilan Soto looked at something and wanted it gone, and then it was. He spent the rest of his life being careful where he looked.]

**Nova** (Active, Flat)

- Today: A burst round you. The third point-blank burst in the game, behind Ground Slam and Frost Nova.
- After: A burst you build while you keep moving. Press and you swell with light for half a second, free to run, then press again to let it go. It's the power that pairs with everything.
- Right way: Press again as the swelling peaks.
- Boss: Medium stance.
- Sound: An in-breath of rising light, then a soft huge foom and a high ring.
- Reading: [Maren Ash held her breath as a child until she went bright. The other kids dared her. They stopped.]

**Barrier** (Active, Flat)

- Today: An absorption shield for ten seconds and a shock when it breaks. Fine, and forgotten once it's up.
- After: A plate you aim. It curves in front of you and turns with your look. Shots it takes are stored, and a second press throws them back as one blast.
- Right way: Raise it in the moment a shot arrives and it's reflected.
- Boss: Blocks ranged hits. The stored blast is a medium stance hit.
- Sound: A glassy hum. Every hit it takes rings one note higher, so you can hear it filling. It breaks with a shatter and a drop.
- Reading: [A schoolteacher called Wen Hadley stood in front of thirty children and said no. It was a very good no.]

**Charged Shot** (Active, Keep and polish)

- Today: Hold, let go. Rebuilt on 30 October with its own hands and six sounds. You haven't heard it yet.
- After: Keep it. A window at the peak makes it pierce. Hold past that and it grows unstable and hurts you a little, for a bigger shot.
- Right way: Let go on the ping.
- Boss: Medium stance.
- Sound: Already its own. First on your listening page.
- Reading: [Piet Rennick spent six years on a single shot and missed. The second one took four seconds.]

**Overload** (Ultimate, Good, needs a hook)

- Today: A one-second gather, a blast radius 9, then +25% and half cost for eight seconds. Strong. 60 energy on a 60 second timer.
- After: Fills from how you fight instead of a timer. During the gather everything you've left Charged lights up and goes off with it.
- Right way: Let it go when the most things are Charged.
- Boss: Massive stance.
- Sound: A swelling choir with circuits crackling inside it, a beat of silence, then a sub you feel in your chest.
- Reading: [Dagny Holt's heart stopped and started forty times on the way down. The last time, it took the whole street with it.]

**Prism Lance** (Ultimate, Flat)

- Today: The Prism path's ultimate: a lance that splits into four seeking beams at the first thing it hits. It reads as Beam at a higher tier.
- After: A fan of five colours you sweep like an open hand, each one a different Force. Hold and they close into one.
- Right way: Close them on an opening.
- Boss: Heavy stance when closed.
- Sound: Five detuned voices in a wide chord that resolves into a single note as they close.
- Reading: [Ottilie Graves taught glassblowers. She could make sunlight do as it was told, and she was very strict with it.]

### Shadow

**Shadow Step** (Active, Keep and polish)

- Today: Teleports behind a target within 16 blocks, next hit x1.6. A starter and a really good one. With nothing aimed it just blinks 8 blocks ahead.
- After: Keep it. With nothing in sight it steps you to the nearest shadow instead of straight ahead. Stepping behind something mid-swing is the big one: the blow goes through where you were.
- Right way: Step while it's swinging.
- Boss: Your next hit counts as an opening.
- Sound: In the taster. A breath taken in and cut off, two frames of nothing, then a low slice.
- Reading: [Nell Aubry used to sneak up on her sister. Then she stopped needing the floor in between.]

**Cloak** (Active, Flat)

- Today: Six seconds unseen, drops aggro, first strike x1.8. A plain stealth button.
- After: Light matters now. In the dark it lasts twice as long and in full sun it flickers. Sound drops away for you too, so you feel hidden.
- Right way: Cloak while nothing is looking at you.
- Boss: Bosses keep tracking you by sound. It only breaks their lock for a beat.
- Sound: A cloth sweep, then everything you hear loses its top end until you come out.
- Reading: [Emeric Dole wasn't invisible. People just stopped being able to care that he was there, and he says that's worse.]

**Echo Swap** (Active, Start again)

- Today: Swaps you with your summon or decoy. Without one it refuses, which is the worst thing a power can do.
- After: Swap with anything you've hit in the last ten seconds, or with the ghost you leave behind every time you cast. Swap a foe into your own Well.
- Right way: Swap as a blow lands, and it hits whatever is standing there now.
- Boss: You can't move a boss, but you can swap with your own ghost, so it's never dead.
- Sound: A two-note whip from one ear to the other.
- Reading: [Odile Marsh stood in for her twin at a wedding and nobody could tell. Eventually neither could they.]

**Legion** (Ultimate, Good, needs a hook)

- Today: The Shadowbinder's ultimate: four or more shadow soldiers for 20 seconds.
- After: They stand up in the shape of the last things you killed, as ink, for twenty seconds, then they're gone. You keep nothing, so the no-allies rule stands.
- Right way: Call it straight after a kill and they arrive already hitting.
- Boss: Draws its attention once. Medium stance if they land together.
- Sound: A low chant with marching ink. Every soldier's step lands on the beat.
- Reading: [Corvin Hask went into a gate with forty people and came out with their shadows. He says they came along on their own.]

### Mind

**Danger Sense** (Passive, Good, needs a hook)

- Today: Anything about to hit you glows red a moment before it does. Useful and quiet.
- After: Keep it. It gains a note: a small rising sting in the ear the blow is coming from, before you see it.
- Right way: Nothing to press. You learn to trust it.
- Boss: Reads the boss's tells early.
- Sound: A thin rising tone placed exactly where the danger is, a different one for Light, Heavy and Perilous.
- Reading: [Hester Pike flinched eleven seconds before the bridge fell. Her whole family crossed it on a different day because of that.]

**Dominate** (Active, Start again)

- Today: Takes over a creature for eight seconds. On a boss it refuses with "too strong", right when you want it most.
- After: Give one order. Look at a creature, then look where you want it to go or what you want it to hit. Puppet King at level 5 gives three orders.
- Right way: Give the order while it's winding up and the blow goes where you say.
- Boss: It shakes you off, but it loses a beat. Its next blow is held longer.
- Sound: A soft two-tone pulse that gets into your head, and a low hum like someone about to speak.
- Reading: [Lucan Ferro only ever said please. Nobody has ever been able to explain why that was so frightening.]

**Read Intent** (Active, Flat)

- Today: Ten seconds of seeing who's hunting you, and they take 15% more and deal 15% less. In practice a stat buff you press and forget.
- After: One creature, read. Its next blow shows on the ground before it starts, and your next cast on it lands Clean. The bonus is only against that one.
- Right way: Dodge a blow it showed you and the read lasts longer.
- Boss: Works in full. It reads the real move the boss has queued.
- Sound: A clock ticking in your head, speeding up before each blow.
- Reading: [Aya Lindqvist played cards for money until nobody would sit down with her.]

**Decoy** (Active, Flat)

- Today: A copy that draws attention. Afterimage's cousin.
- After: It walks, runs and looks like you. Cast it as you dodge and you vanish while it takes over. It can carry a trap.
- Right way: Cast it in the moment of a dodge.
- Boss: Draws one attack from a boss.
- Sound: Your own footsteps, very slightly out of time.
- Reading: [Rafe Colby got out of every fight he was ever in by being somewhere else. He'd be the first to tell you so, if you could find him.]

**Foresight** (Active, Good, needs a hook)

- Today: A tenfold slow and the first blow misses. Flow's cousin.
- After: See the next second. A gold ghost of the boss plays its next blow on the real path it will take, and you hear the hit backwards before it lands.
- Right way: Step out of the ghost's blow at the last moment and your counter lands Clean.
- Boss: This is what it's for.
- Sound: A gold shimmer and a reversed swell of the hit that's coming.
- Reading: [Joan Thessaly told the Choir what it was going to ask her, answered it, and left.]

### Architect

**Wall** (Active, Fold into one)

- Today: Builds a wall on a 2 second cooldown. Wall, Ramp, Floor and Cone take four of your six keys, so nobody can play Architect and anything else.
- After: All four fold into one power, Blueprint, in one slot. Press it and Z X C V become wall, ramp, floor and cone for a few seconds, snapped to a grid. You keep every level you've earned.
- Right way: Place a wall as a shot arrives on that side and it sets twice as hard.
- Boss: A boss charging into a fresh wall stops dead and takes a stance hit.
- Sound: A ratchet tick and a set-stone knock.
- Reading: [Ida Rask could build a wall faster than you could throw a rock at her. Her brothers checked.]

**Ramp** (Active, Fold into one)

- Today: A ramp on a 2 second cooldown. Its level 2 step is called Blueprint, like four other powers'.
- After: A shape inside Blueprint, with its Rush, Rails and Launch Ramp steps at its own level.
- Right way: Lay one under a moving foe's feet.
- Boss: Fair footing in a boss room.
- Sound: A stone block sliding home up a slope.
- Reading: [Ida's little brother Tobin couldn't climb the walls she built, so she made him a way up.]

**Floor** (Active, Fold into one)

- Today: A floor on a 2 second cooldown.
- After: A shape inside Blueprint, with Spring, Hold and Sky Bridge at its own level.
- Right way: Catch yourself with one in the last moment of a fall.
- Boss: Footing over a drop.
- Sound: A flat heavy slap of a slab landing.
- Reading: [Ida's floor went out over the river and stopped a yard short of the far bank. She said that was all the stone she had.]

**Cone** (Active, Fold into one)

- Today: A small roof shape on a 2 second cooldown.
- After: A shape inside Blueprint, with Lantern, Shatterproof and Pagoda at its own level.
- Right way: Drop one over something and it's trapped for a beat.
- Boss: Shelter from what falls.
- Sound: A small roof clink that settles.
- Reading: [Ida built the little roofs last, over people who had no time to get indoors.]

**Rampart** (Active, Good, needs a hook)

- Today: A small fort in one press.
- After: You draw it. Hold and sweep your look across the ground and the wall rises along the line you drew, gatehouse and all.
- Right way: Close the line back on itself and it rises twice as fast.
- Boss: Absorbs one charge.
- Sound: Stone courses grinding up one after another and a portcullis clang at the end.
- Reading: [Bastien Cole walled in a whole village by himself in a night, and then he forgot to leave a gate.]

**Spire** (Active, Flat)

- Today: A pillar under your own feet that throws you up, along with whatever's next to you. Forgettable.
- After: It rises under what you're looking at and takes it up. Stand on one yourself for height, and a turret on top gets twice the range.
- Right way: Put it under feet in the moment they leave the ground.
- Boss: A small stance knock.
- Sound: A screech of rock rising, stopped dead with a stone clap.
- Reading: [Vera Sund was tired of being looked down on, and it turns out that was a thing you could fix.]

**Collapse** (Active, Start again)

- Today: Brings down your constructs. With none it refuses.
- After: Brings down anything overhead: your builds, or ceilings and overhangs, which put themselves back afterwards.
- Right way: Let it go when something is directly underneath.
- Boss: Heavy stance when something falls on it.
- Sound: A long groan, then stone slamming in a rolling cadence.
- Reading: [Everything Ida Rask built came down the day she died. That's what this is.]

**Citadel** (Ultimate, Good, needs a hook)

- Today: The Architect path's ultimate: a dome of hard light you heal and shelter inside.
- After: Fills from how you fight. The dome becomes a real fortress that folds up round you in sections you can hear, with slits and rails for your other powers.
- Right way: Call it under attack and the first hits feed it.
- Boss: Blocks one big blow, then cracks.
- Sound: A huge mechanism folding, ending on a single bell.
- Reading: [They found Ida's last building at the bottom of a gate. It's still standing and nobody has worked out how to get in.]

### Tech

**Snap Cable** (Movement, Good, needs a hook)

- Today: A quick pull. It overlaps the Tether, which is better for getting around.
- After: A weapon first. On a light target it drags it to you, on a heavy one it drags you to it, and letting go at the closest point slings you past.
- Right way: Let go at the closest point.
- Boss: Drags you in, and counts as a charge-in blow.
- Sound: A winch whine, a high taut ping and a fast reel.
- Reading: [Tam Okoro fixed cable cars above the Deep Lift for thirty years. He fell four times and the cable always caught him.]

**Jet Burst** (Movement, Flat)

- Today: A short boot thrust. One more way to go up.
- After: A thrust you aim and chain. It leaves a line of heat that burns what crosses it, and it cancels any recovery.
- Right way: Use it at the very top of a jump and it's free.
- Boss: Light stance through the burn line.
- Sound: An ignition snap and a crackling afterburner that tails off.
- Reading: [Someone in the Above strapped an engine to a test pilot's boots. She wrote down "again" and nothing else.]

**Drone** (Active, Flat)

- Today: A drone that orbits you for twenty seconds and zaps whatever you're fighting. It runs itself.
- After: You aim it. Hit something with a power and your drone fires on that. It can catch a Beam and send it on.
- Right way: Land a hit right before it fires.
- Boss: Steady light stance.
- Sound: A tiny motor whine, servo ticks and quick high shots.
- Reading: [Precursor maintenance unit 14 followed a woman home in the last year of the Halo. The notes don't say why she kept it.]

**Turret** (Active, Flat)

- Today: Set it and forget it.
- After: Where you put it matters. High up it sees further, and on a Spire twice as far. It overheats if it never stops firing, so you point it away.
- Right way: Put it on high ground.
- Boss: Steady light stance.
- Sound: A hydraulic unfold, a slow deep autocannon and a falling cool-down tick.
- Reading: [The Ascendants left these guarding a door on the Halo. They're still guarding it. There's nothing behind it any more.]

**Railgun** (Active, Keep and polish)

- Today: Hold 1.2 seconds for a 48-block pierce. Its charge sound is the Haymaker's.
- After: Keep the idea. Holding zooms in a little, firing shoves you back, and the shot leaves a line of light. Level 5 punches a real tunnel that fills itself back in.
- Right way: Fire on the ready tick.
- Boss: The most stance you can do from range.
- Sound: In the taster. Coils tripping faster, a rising whine, a ready tick, a supersonic crack, an echo down the line.
- Reading: [Imre Kallo built this to send a message to the Halo. It got there. It's still in the wall.]

**Nanite Skin** (Active, Flat)

- Today: Armour for a while. The third self-guard after Brace and Barrier.
- After: It flows to where you're being hit. The side taking the blows thickens and shows its scales.
- Right way: Raise it when you're below half and it also repairs.
- Boss: Takes a third of one big hit.
- Sound: Millions of tiny clicks moving round your body, like a swarm settling.
- Reading: [Beatrix Lund had a body that repaired itself and a mind that couldn't keep up. She kept asking whose hand it was.]

**Drone Swarm** (Ultimate, Good, needs a hook)

- Today: The Engineer path's ultimate: a swarm of drones.
- After: Fills from how you fight. It follows what you hit, and at level 5 forms one lance.
- Right way: Call it after a Clean hit and they all start there.
- Boss: Medium stance once they form up.
- Sound: A wall of small motors panned round you, rising as they form.
- Reading: [Unit 14 had children, more or less. Nobody asked it to.]

### Spirit

**Aura** (Passive, Flat)

- Today: 1.2 damage every two seconds to what's near you. You forget it's there.
- After: It shows your state. It takes your Path's colour and after a fight it's big and slow. Standing still for a second makes the next pulse stronger.
- Right way: Stand still for a second.
- Boss: Light.
- Sound: A low temple drone that only comes up near enemies.
- Reading: [Everyone has this. Most people just call it being in a room with someone.]

**Spirit Palm** (Active, Good, needs a hook)

- Today: Reach 4.5, and an echo hits a second later. It refuses on a whiff.
- After: A whiff still throws a small palm. Right at arm's length, the echo comes back doubled.
- Right way: Land it at the very end of your reach.
- Boss: Medium stance from the echo.
- Sound: A bare-hand slap, then a deep singing bowl a second later.
- Reading: [Brother Anselm's students used to bow to the wall he had just hit, because a second later it would fall over.]

**Mend** (Active, Flat)

- Today: A heal. Press and wait.
- After: Breathe in. Hold, and let go at the top of the breath for a bigger heal. Getting hit breaks it. Players near you still share it.
- Right way: Let go at the top of the breath.
- Boss: It's a drink to a boss: under the new fights it will notice.
- Sound: A long in-breath, then a warm bowl note.
- Reading: [Subject 1 cried for an hour. The notes leave out that everyone else in the room felt better afterwards.]

**Third Eye** (Active, Flat)

- Today: Eight seconds of seeing every hostile through walls. Very like Read Intent.
- After: It sees the world, not the fight: sleeping bells, hidden kin, secret doors, cave mouths. The tool for everything we've built since.
- Right way: Hold still to widen it.
- Boss: Shows a weak side for a beat.
- Sound: A high glass ring as it opens, then tiny pings for each thing it finds, placed where they are.
- Reading: [Zofia Harker could always find your keys, and she also knew what you had done with the money.]

**Cord Lash** (Active, Good, needs a hook)

- Today: A whip with a pull. It overlaps Snap Cable.
- After: It ties two things together. Lash one, lash another, and they're pulled together and share hits.
- Right way: Second lash within a second.
- Boss: Ties a boss to one of its adds, and they share damage.
- Sound: A leather crack, then a taut string hum while they're bound.
- Reading: [When Marek Sayle's sister left her body, he pulled her back with a rope that wasn't there.]

**Bulwark** (Active, Good, needs a hook)

- Today: A dome for eight seconds: allies inside take half damage, arrows dissolve and enemies get pushed out. The Warden's signature.
- After: It arrives with force: a ring throws everything out the moment you raise it. Party members inside count as guarded.
- Right way: Raise it just before the blow.
- Boss: Blocks one big blow.
- Sound: A huge bell bloom, felt more than heard.
- Reading: [Hild Aran stood on the last bridge at Carillon and told the Deep it was closed. It took the Deep eleven days to argue.]

**Hundred Palms** (Ultimate, Keep and polish)

- Today: Two seconds of palms too fast to see, ending in one that launches. On paper the most exciting ultimate you have.
- After: Keep it and add a rhythm. Press along with the beat and you get more palms and a bigger last one.
- Right way: Press in time with the palms.
- Boss: Heavy stance, more on the beat.
- Sound: Slaps building to a blur and ending on one giant bowl.
- Reading: [Master Oyelaran was asked how many hands he had, and he said he'd lost count. He had two.]

**Void Maw** (Ultimate, Good, needs a hook)

- Today: A vortex close to Gravity Well that feeds you, with an execute under 15% that skips bosses.
- After: It bites. Press again and the jaws shut on whatever's inside. It eats shots too.
- Right way: Shut the jaws when most are inside.
- Boss: No execute. It takes stance and heals you.
- Sound: A wet cavern groan and a final clack of teeth.
- Reading: [Something came up out of the Deep and ate Sorrel Vance, and then for some reason it started doing what she wanted.]

**Waking Dream** (Active, Start again)

- Today: A temporary door, bridge or stairs. A lovely idea with little use.
- After: What you look at becomes real for a while. Hold your look on a wall and a door opens that really leads through, for a minute.
- Right way: Hold still on the spot until it settles.
- Boss: A door in a boss room only lasts a blink.
- Sound: A slowed piano chord played backwards, and the room tone changing as you pass through.
- Reading: [One of the Sleepers walked out of the dream for an afternoon and left a door behind. We still don't know where it goes.]

**Storm Call** (Active, Flat)

- Today: Calls lightning down on up to five enemies near you, using a vanilla bolt. Stronger in the rain. With nobody near, it refuses.
- After: A real storm cell overhead. Rain starts, and bolts land where you look. Wet foes and metal carry it.
- Right way: Call it where there's water.
- Boss: Medium stance per bolt.
- Sound: Thunder walking closer, rain coming across the ground, one crack right overhead.
- Reading: [Anneke Dorr was a fisherman's widow who stood on the beach and shouted at the sky, and it shouted back.]

**Ember Step** (Movement, Flat)

- Today: A fiery dash, from walking through lava.
- After: You leave burning footprints and lava is safe to cross while it lasts.
- Right way: Go through something Chilled and it bursts into steam.
- Boss: A burn line it has to step across.
- Sound: A flare and an ember crackle that follows your feet.
- Reading: [Bruno Cassar walked out of the foundry fire carrying three people. He left footprints in the iron.]

**Frost Nova** (Active, Flat)

- Today: A cold burst, and the only frost power in the game.
- After: Freezes the real ground and water. Cross a river on it, and it thaws by itself. Frozen foes shatter.
- Right way: Shatter a frozen foe with a Kinetic hit.
- Boss: Leaves it Chilled: slower wind-ups for a moment.
- Sound: Ice creaking and glassy crystals growing outward.
- Reading: [Little Signe Aalto got lost in the snow when she was six and came home a week later perfectly warm.]

**Ghost Step** (Movement, Flat)

- Today: A dash through walls. The fifth dash in the game.
- After: It shows you the room on the other side for a moment before you go.
- Right way: Stop for a beat inside the wall and come out unseen.
- Boss: Passes through a blow.
- Sound: Everything muffles as if under water, you hear the room beyond, then a breath.
- Reading: [Nobody's sure Lorne Pascoe ever really was in the room. He left a wife and two kids, and he's still expected for dinner.]

**Twin Strike** (Active, Start again)

- Today: A hidden power that repeats your hits for a while.
- After: Your second self stands next to you for eight seconds and does everything you do, a beat late.
- Right way: Cast something big straight after.
- Boss: Its hits count as yours.
- Sound: Every sound you make comes again, just late.
- Reading: [This one came up out of the Double Gate with two names on it. Both of them are yours. I'd like to know more about that.]

## Duets

- Gravity Well + Nova = **Collapsing Star**: A Nova set off inside a Well is crushed down to a point for half a second, then goes off twice as wide.
- Telekinesis + Terrain Rip = **Orbit**: The rocks you tear up circle you. Press again and they go out like a shotgun.
- Railgun + Gravity Well = **Slingshot**: The slug bends round the Well and hits things behind cover.
- Beam + Blueprint = **Prism Wall**: A Beam through your own wall splits into a fan of five.
- Frost Nova + Ground Slam = **Shatterfield**: The frozen ground bursts up in a ring of ice.
- Storm Call + Telekinesis = **Lightning Rod**: Whatever you're holding takes every bolt in the storm.
- Shadow Step + Haymaker = **Knock Knock**: Charge first and step. You arrive behind them with the punch already there.
- Flow + Beam = **Slow Burn**: The Beam keeps its real speed in slowed time. One sweep tags everything, and it all burns when Flow ends.
- Dash + Shockwave = **Sonic Stamp**: Stomp at the end of a Dash and the wave carries your speed twice as far.
- Decoy + Nova = **Surprise Party**: Your decoy goes off as a Nova, wearing your face.
- Afterimage + Railgun = **Two Shots**: Your ghost fires it again from where you stood.
- Drone + Beam = **Relay**: The drone catches your Beam and turns it on a second target.
- Turret + Spire = **Crow's Nest**: A turret on a Spire sees twice as far and fires down on everything.
- Snap Cable + Haymaker = **Flying Fist**: All the speed of the reel goes into the punch.
- Meteor Dive + Gravity Well = **Deep Impact**: Land in the middle of your Well and the crater pulls everything in with you.
- Cord Lash + Ground Slam = **Reel and Stamp**: Everything you've tied together is hauled under your feet as you land.
- Thunderclap + Telekinesis = **Live Round**: Clap on something you're holding, then throw it. It goes off when it lands.
- Barrier + Mend = **Sanctuary**: Your heal fills the plate, and it heals whoever stands behind it.
- Third Eye + Railgun = **Sight Line**: See through the wall, shoot through the wall.
- Read Intent + Dash = **Already Gone**: Dash through a blow you read and your next hit is a guaranteed opening.
- Echo Swap + Gravity Well = **Switcheroo**: Swap a foe into the middle of the Well.
- Spirit Palm + Haymaker = **One-Two**: Body then soul: the echo lands inside the punch and they both come through.
- Void Maw + Gravity Well = **Swallow**: The Maw eats the Well and spits everything in it at what you're looking at.
- Ember Step + Frost Nova = **Fog Bank**: A wall of steam that hides you and scalds what walks through it.

## New powers (optional)

- **Titan** (Kinetic ultimate): You grow to three times your size for ten seconds. Your steps shake the ground and your swings sweep a room. Refused under a low ceiling, and you shrink if one closes in.
- **Stopped Clock** (Velocity ultimate): Everything but you stops for three seconds. Bosses drag at a quarter speed instead of stopping, so a fight stays a fight.
- **Nobody Home** (Mind ultimate): For six seconds nothing near you remembers you're there, and every one of them can be hit from the blind side.
- **Toll** (Earned from the bells): You strike the air like a bell. The Deep's creatures reel, and any sleeping bell within earshot answers faintly.
- **Second Try** (Rank A, Velocity): You go back to where you stood four seconds ago, with the health you had then.

## Recommendations

1. Build the middle option: keep the ladder, add the systems underneath, and rework the weak powers one at a time. (If no: I'd build the small option instead: sounds, stance, dead buttons and the vanilla leftovers. Decent, but short of the bar you set.)
2. Give every power a right way to press it (a Clean cast), judged by the server and heard as a ring. (If no: Powers stay one press and number 12 falls away. The rest still works, but you lose the main way of getting better at a power.)
3. Let powers fill a boss's stance, under the same braced and opening rules as blades, with the numbers set so a blade stays the main way to break it. (If no: Blades stay the only way to break a boss, and casters keep feeling second best there.)
4. End every dead button: Dominate, Telekinesis, Echo Swap, Collapse and Gravity Flip all do something on a boss, and an aimed power with nothing to aim at does something smaller. (If no: Refusals stay as they are, except the seven that charge you for nothing, which I'd fix anyway.)
5. Add six States you can see on a creature, and 24 named Duets you find by trying things, recorded as N / ?? like the Weaves. (If no: Powers go on working side by side, and the seven existing reactions stay.)
6. Let Duets work between two players in a party. Your Well, their Nova. (If no: Duets only work with your own two powers.)
7. Let the R key fill from how you fight, with no energy cost and no timer, and give your first ultimate as the reward for the D Proving. (If no: R keeps its 60 second timer and stays empty until a Path or Rank C.)
8. Make the eight movement powers attacks that move you, and count every cast as a link in the Ascent's chain. (If no: The eight stay as they are, next to the Ascent moves that overlap them.)
9. Fold Wall, Ramp, Floor and Cone into one slot, Blueprint, keeping every level already earned. (If no: Architect keeps four of your six keys on building shapes.)
10. Build the showpieces: ground that moves, light that bends round a Well, shots you catch, a real door in a real wall, a gold ghost of the boss's next blow. (If no: Effects stay drawn on top of the world. Everything else in the plan still happens.)
11. Give every power its own sound pack (about 340 new events in about 700 short files, roughly 10 MB), with a listening page per family where you pass or redo each one. (If no: Today's sounds stay. I'd still fix Gravity Well's silent three and a half seconds and split Haymaker from Railgun.)
12. Let Clean casts ring a note tuned to the family, so a good run of play builds into a phrase. Can be turned off. (If no: Clean still rings, as one sound each time, with no rising phrase.)
13. Give every power a person: a Reading in the System's voice when you learn it, and one more line each time it levels. (If no: Descriptions stay as they are today.)
14. At level 5 of your starter, tell the player the last reading has their own name on it. "You picked this one last time too." (If no: Your starter's last reading is an ordinary one.)
15. Give Power Cores a source: each gate theme and Lord leans toward the families it has swallowed, so a player can go looking for one. (If no: Cores stay random by rank.)
16. At level 5 the System reads how you actually used the power and offers up to three ways to keep it. You pick one, and can change it later. (If no: Level 5 stays exactly as it is today, which you already like.)
17. Add the five new powers: Titan, Stopped Clock, Nobody Home, Toll and Second Try. They come last and can be skipped. (If no: Five fewer powers. Kinetic, Velocity and Mind players get offered Overload, Legion or Gravity Flip for their first ultimate.)
18. Add a Powers tab to the K menu's Tasks page and a Choir's Record page to the Journal. (If no: Powers stay off the Tasks page, which breaks your standing rule, so I'd ask you to think again.)
19. Build a playable proof first (Haymaker, Shockwave, Gravity Well, Railgun, with Clean, stance and their sound packs) and stop for you to play it before anything else. (If no: I'd build straight through. It's faster, but it's much harder to stop cheaply if Clean turns out to feel wrong.)
