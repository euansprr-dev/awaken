# Powers in Full Voice

Source for the approval page `plan.html` (built by `build_page.py` from `powers_data.py` and `scenes.py`). Nothing here is built yet.

## Verdicts

- Keep and polish: 8
- Good, needs a hook: 17
- Flat: 24
- Start again: 10
- Fold into one: 4

## The ten changes

1. **Where it shines: no timing to learn**: My first draft gave every power a timing window. You said that sounds like a layer for the sake of it, and you're right, so it's gone. Each power instead has a situation where it's at its best, written by hand and listed on every card below: from high up, in a crowd, in water, on something already Charged. It happens by itself. There's no window, no cue and nothing to learn first. Six keep a moment you can time, because that's what they are: Haymaker, Charged Shot, Railgun, Brace, Dash and Barrier. Missing it still gives you the whole power.
2. **Powers count on bosses**: Every power fills stance by weight through the existing `Strikes.posture`: Light 3, Medium 6, Heavy 12, Massive 20. The same braced and opening rules apply.
3. **No dead buttons**: An aimed power with nothing to aim at does something smaller. Every power gets a boss version. Wind-up powers check before they charge you.
4. **States and Duets**: Six States show on a creature's body: Lifted, Charged, Burning, Chilled, Held, Read. On top of those, 24 named Duets: two specific powers that make something neither can do alone. You find them by trying things, and they're counted N / ?? like Weaves.
5. **Movement powers attack, and casts join the chain**: The eight movement powers become attacks that move you. A cast counts as a link in the Ascent's chain, and a long enough chain gives On the Move casts. Wings stay for travel and Flight is for fighting in the air.
6. **The R key fills from how you fight**: R becomes a gauge. Your hits, Duets, finishers and kills fill it, and it creeps up on its own so nobody is ever locked out. No energy cost. Your first ultimate is the reward for the D Proving, chosen from what you've actually done.
7. **The world answers**: The ground really comes up, in whatever you're standing on. Beam leaves a scorch. Frost freezes real water for a while. The Well pulls loose things and bends shots. Charge runs through water and metal. Flow slows every new sound through the game's own sound event. Every vanilla particle goes.
8. **A sound pack for every power**: Nothing gets to ring. Every attack is built from real recordings and air, and brightness comes from grit and wind, never from something struck that sings. A check fails any file with a high tone in its loud part, and all 83 get remade. On top of that each power gets a charge or wind, a release, an impact, a tail and a level 5 layer, two versions of each, and each family gets its own materials. A low Choir layer comes in at level 5. A mix budget keeps your own power and the biggest threat on top.
9. **Readings: every power has a person**: A Power Core is a recording the Choir took of a real person. The Deep is full of people who had them, which is why Lords drop Cores. Each power has a first bearer, one line when you learn it and one more per level. The Journal keeps them in The Choir's Record. At level 5 of your starter you find a second name on the reading.
10. **Progress you earn by playing**: Gate themes and Lords lean toward the families they've swallowed, with about a third staying random. At level 5 the System reads how you actually used the power and offers up to three ways to keep it, with the evidence. You pick one and can change it. New Powers tab through `Goals`.

## Every power

### Kinetic

**Ground Slam** (Active, Flat)

- Today: A ring of damage round you, 4.5 blocks. From the air you dive first, up to +14 damage for height. Shockwave and the Leaper's slam now do most of this, and nothing about it says ground.
- After: The real ground answers. The blocks round you kick up in a ring and roll outward, drawn from whatever you're standing on, so sand and stone look and sound different. From high up it leaves a crater for ten seconds. Whatever the crest catches is left Lifted.
- Where it shines: Come down on something from high up. The higher you start, the bigger the crater and the further it throws them.
- Boss: Heavy stance hit, more the further you fell.
- Sound: A dropped wall: a sub thud first, then a drum roll of blocks travelling away from you. The surface changes the top layer: gravel hiss, sand sigh, stone crack.
- Reading: [Tomas Reyne worked a pile driver for thirty years. When the Choir read him it found he'd stopped needing the machine.]

**Leap** (Movement, Flat)

- Today: A big forward bound with a small blast on landing. The Ascent now gives you double jumps, dives and slams, so this mostly repeats them.
- After: A hunting leap. Hold to crouch and a landing circle shows up to 24 blocks out. Let go and you arrive there, already striking. It counts as a link in your movement chain, so it joins the Ascent instead of competing with it.
- Where it shines: Leap at something from a long way off. The further the jump, the harder you land on it.
- Boss: Medium stance, doubled if you land in an opening.
- Sound: Knees creak, a ground thump as you leave, wind that rises and falls with the arc, then a landing body-thud.
- Reading: [Nadia Orrin was late for a ferry and she made it. The ferry was already a quarter of a mile out.]

**Haymaker** (Active, Keep and polish)

- Today: Hold to one second, let go, reach 4.5; a whiff throws a ring. The best idea in the family, because you can feel the wind-up. Its charge sound is the same file the Railgun uses.
- After: Keep it and polish it. Hold to build and let go whenever you like. At full charge time hitches, the target folds, flies and cracks the wall it lands against.
- Where it shines: Let go as it reaches full charge and time hitches, the target folds and it cracks the wall behind. Hold for as long as you like: a late punch is still a full punch.
- Boss: The biggest stance hit of any starter.
- Sound: In the taster. Knuckles and a straining shoulder climbing to a thump at the peak, torn air, meat and stone, a long sub drop.
- Reading: [Dov Harlan threw one punch in his whole life that mattered. The Choir kept a copy of it. That's what you're holding.]

**Terrain Rip** (Active, Good, needs a hook)

- Today: Tears up to nine real surface blocks out and throws them straight away, and the hole fills back in. A strong idea that's over in one quick press.
- After: You keep the torn-up chunk over your head while you aim, with a line showing where it lands, and the blocks grind against each other as you hold it. Press again in flight to burst it.
- Where it shines: Rip it up in a corridor or a cave, where the chunk has nowhere to go but through them.
- Boss: Medium stance. A boulder can be caught with Telekinesis for a Duet.
- Sound: Roots tearing and stone grinding as it comes up, a groan while you hold it, a long throw, a dense wet thud.
- Reading: [Subject 2, in the Precursors' own notes. They wrote that she lifted the test chamber. They didn't write that she was trying to get out of it.]

**Shockwave** (Active, Good, needs a hook)

- Today: A stomp and a cone out to 9 blocks. A good starter with a proper stomp. The wave itself is only light running along the ground.
- After: The wave travels through the real surface. Blocks ripple and climb slopes as it passes, and you can watch it go. The crest at the far end hits hardest and throws foes up.
- Where it shines: Use it on a slope or in a tight group. Whatever the far edge of the wave catches goes up in the air, and anything thrown into a wall takes a second hit.
- Boss: Light stance. Small things are left Lifted.
- Sound: Keep the authored stomp. Add a low rolling rumble that moves away from you and knocks once for each foe it meets.
- Reading: [Greta Moss stamped her foot at her brother in a kitchen in the old town. They found the kitchen in the next street.]

**Brace** (Active, Flat)

- Today: Three seconds at 60% less damage and no knockback, and melee attackers take a share back. It pays you to stand still, and the Arms guard now does that job better.
- After: A parry with your whole body. Press as a blow arrives and you stop it dead: the hit is soaked up and thrown back as a wave when you let go. Mistime it and you still get today's 60%.
- Where it shines: Press it as a blow arrives and you stop it dead, the same as a parry with a blade. Miss and you still get the 60% it gives today.
- Boss: A timed Brace counts as a deflect: it opens a riposte and a Weave.
- Sound: A bronze door slamming shut inside your chest. Stop a blow with it and the door booms and shakes the floor.
- Reading: [Ansel Tray held a door for nine hours so the people behind it could get out. Nobody ever told him he could stop.]

**Thunderclap** (Active, Good, needs a hook)

- Today: Radius 7 lightning and a stun, one press. At this range it plays like a recoloured Ground Slam.
- After: A held clap. Your hands pull apart while you hold; let go and the air breaks in a forward wedge. Everything Charged nearby goes off with it. In rain, or standing in water, it reaches twice as far.
- Where it shines: Set it off with several things Charged nearby, or stand in rain or water. All of them go at once and it reaches twice as far.
- Boss: Heavy stance when clapped into a wind-up.
- Sound: A dry hand-crack right at your ears, then real thunder rolling off across the land for four seconds.
- Reading: [Yara Venn clapped once to get a room's attention. It worked. They rebuilt the room.]

### Velocity

**Dash** (Movement, Good, needs a hook)

- Today: A fast steered dash on a 0.7 second cooldown. Reliable, and the Ascent's air dash and the Arms dodge now sit right next to it.
- After: The move that goes through things. Through a foe you leave a delayed cut. Through a blow, time hitches for one beat and it counts as a perfect dodge.
- Where it shines: Go through a blow rather than round it and it counts as a dodge, the same one your weapons use.
- Boss: Counts as a perfect dodge: 10 on its stance, and it opens a Weave.
- Sound: Tape ripped fast, a doppler past your ear, and a small air pop when you arrive.
- Reading: [Lio Sandoval ran messages across Carillon for eleven years and was never once late. He died running one. It got there.]

**Flow** (Active, Keep and polish)

- Today: A time bubble, radius 18, a quarter speed for four seconds. The headline power. Mobs slow down but the world's sound doesn't.
- After: Slow everything. Rain hangs, arrows hang, and every sound that starts while it runs plays lower and duller. The marks you leave all go off together when it ends.
- Where it shines: Use it with a lot of things in the air: arrows, thrown rocks, your own shots. They all hang, and it all lands together when it ends.
- Boss: Bosses run at half speed instead of a quarter, and their tells stretch with them.
- Sound: The world's pitch falls away, your heartbeat comes up close, and time comes back in a rising rush.
- Reading: [When Sera Quill was nine she fell off a roof, and she said everything went slow on the way down. She spent the rest of her life trying to get back to that second.]

**Afterimage** (Active, Flat)

- Today: Leaves a body that bursts. Close to Decoy and easily forgotten.
- After: It remembers your last two seconds. Your ghost replays them where you did them, at half strength, blows and casts included.
- Where it shines: Do something big just before it. Your ghost plays back whatever you did in those two seconds, so a big hit gets repeated.
- Boss: The same stance as whatever it repeats, halved.
- Sound: Your own last sound again, thin and a little backwards.
- Reading: [Pim Arlen left a room so fast that part of him stayed in it. His friends used to say goodnight to it.]

**Wall Stride** (Passive, Start again)

- Today: A passive that lengthens the Ascent's wall-run. It does its job and belongs with the body's moves.
- After: Reinvented, see Always On. Your hand drags sparks along walls, your footprints burn, the run turns red when only the power is holding you up, and all its speed goes into your next hit.
- Where it shines: Hit something straight off the end of a run. It carries all the speed of the run.
- Boss: Nothing of its own; it feeds your next hit.
- Sound: A quick patter of soles on whatever the wall is made of, climbing in pitch with the length of the run.
- Reading: [Nobody told Ines Cardo that walls were for stopping you. She was four, and they never managed to explain it to her afterwards.]

**Water Run** (Passive, Start again)

- Today: Run on water while sprinting. It still uses vanilla splash particles, which breaks your look rule.
- After: Reinvented, see Always On. A real wake that lasts, sheets of spray, a hard stop that breaks a wave forward, and at level 5 you run up waterfalls.
- Where it shines: Run at someone across water and stop hard in front of them.
- Boss: Not a fight power. Foes you soak count as wet for Charge.
- Sound: Slapping sheets of water that climb to a steady hiss as you speed up.
- Reading: [Jory Wake's boat sank a mile out. He says he didn't want to get his boots wet, and nobody can tell if he's joking.]

**Lightning Step** (Movement, Good, needs a hook)

- Today: A 14-block flash that shocks the line. Good, and the fourth dash in the game.
- After: The line stays live. For a second and a half the path you flashed down is a rail of current, and pressing again runs you back along it. Anything on it is left Charged.
- Where it shines: Flash through a crowd, then run back along the line. Everything standing on it gets hit twice and is left Charged.
- Boss: Light stance each pass. Once it's Charged your other hits arc off it.
- Sound: A capacitor snap, a bright crack on arrival, and the line sizzling away behind you.
- Reading: [Kasia Thorne was struck on a hill at sixteen and ran home before the thunder got there. She was grounded for it.]

### Flight

**Hover** (Active, Flat)

- Today: Hang in the air for five seconds. Pleasant at E rank, then the Ascent gives you far better control in the air.
- After: An air stance. While you hover every cast has a third more range and counts as from above. You can drift and aim, and a second press drops you like a stone.
- Where it shines: Hang over a fight. Every cast has a third more range from up there and counts as from above.
- Boss: Nothing of its own; it sets up the next cast.
- Sound: A soft pressure hum with a slow wobble. It thins and shakes as your energy runs out.
- Reading: [Old Mirren sat down on nothing one afternoon, about a foot up, because her knees hurt. That's all this was at first.]

**Flight** (Movement, Flat)

- Today: Free flight for 10 energy plus 6 a second, unlocked at rank A. It overlaps Wings and Skyborn and has no job of its own.
- After: Flight for fighting. Wings take you far. This one stops dead, strafes and turns on a coin, so you can hang in a boss room and keep moving. At top speed a boom hits what's under you.
- Where it shines: Fly low and fast over a group. The boom hits everything underneath.
- Boss: The boom is a light stance hit.
- Sound: Pressure, not engines: wind at your ears that follows your speed, and one clean boom.
- Reading: [Cael Aster was the first one who went up on purpose. He came down once, to say it was worth it.]

**Gravity Well** (Active, Good, needs a hook)

- Today: A vortex, radius 6, four seconds. Good and useful. It makes one sound at the start and nothing for the other three and a half seconds.
- After: It bends what you see. Light curves round it (with Lens on) and real loose things fly in: drops, arrows, sand, leaves. Shots aimed through it get pulled off course.
- Where it shines: Drop it where they're already bunched up, or put it between you and something shooting at you.
- Boss: Can't pull a boss. It pulls the adds, eats the shots, and fills stance while the boss stands in it.
- Sound: In the taster. A throb you feel more than hear, noise sucking inward in pulses, debris rattling round, then a swallowed thud.
- Reading: [Ruth Lenner lost a son, and after that everything around her kept falling toward the place she was sitting. The Choir read it and called it a talent.]

**Telekinesis** (Active, Keep and polish)

- Today: Lift and throw one creature. The fantasy is strong. On a boss it refuses with "too heavy".
- After: It catches things out of the air. Any shot coming your way stops dead while you hold, and you send it back where you look. Creatures and torn-up ground work as they do now.
- Where it shines: Hold it up when things are shooting at you. Anything you catch can go straight back to whoever sent it.
- Boss: Never refuses. You grab a limb for a beat: its next blow is held a moment longer, plus a stance hit.
- Sound: A strained hum that climbs with the weight, small cracks while you hold, a snap when you throw.
- Reading: [Hanno Vik wanted the salt and didn't want to get up. He's the reason the Choir started taking laziness seriously.]

**Meteor Dive** (Active, Flat)

- Today: Launches you up, then brings you down hard, more for height. The Leaper's dive and Ground Slam's air version already do this.
- After: A strike from anywhere in the air. A circle shows on the ground up to 40 blocks away and you go there on a burning slant. The impact melts a shallow bowl that cools over ten seconds.
- Where it shines: Dive from as high as you can get. The longer the fall, the bigger the bowl it melts.
- Boss: Heavy stance, and a way across a big room.
- Sound: A re-entry roar that climbs, then a deep boom with a glassy crackle as the ground cools.
- Reading: [Tess Roan jumped off the Halo. She'd been told you couldn't.]

**Gravity Flip** (Ultimate (new slot), Start again)

- Today: A hidden power that throws things up and drops them. It skips anything heavy, has no variants and is rarely seen.
- After: Becomes Flight's ultimate. Everything nearby lets go of the ground and falls upward, drops and loose blocks too, for two seconds. Press again and it all comes down at once.
- Where it shines: Use it in a room with a ceiling. Everything falls up into it, then comes down again when you press.
- Boss: The boss stays down. It takes the fall of everything above it as stance.
- Sound: A backwards thump, floating quiet with faint whistles, then a rain of impacts.
- Reading: [Nobody knows who this was. The reading came up from under the Inverted Sea with the name scratched off.]

### Energy

**Beam** (Active, Keep and polish)

- Today: A held beam with a proper gather, loop and end. One of the best powers in the game.
- After: Keep it. It marks the world more: a glowing scorch line that lasts, and leaves catch. Held on one target it climbs to a white peak, and letting go there ends in a pulse.
- Where it shines: Keep it on one target. It climbs to a white peak by itself and ends in a pulse when you let go.
- Boss: Light stance, steady.
- Sound: Keep the authored set. Add a heat creak that grows with time on target, and a layer per level.
- Reading: [Ilan Soto looked at something and wanted it gone, and then it was. He spent the rest of his life being careful where he looked.]

**Nova** (Active, Flat)

- Today: A burst round you. The third point-blank burst in the game, behind Ground Slam and Frost Nova.
- After: A burst you build while you keep moving. Press and you swell with light for half a second, free to run, then press again to let it go. It's the power that pairs with everything.
- Where it shines: Run into the middle of them while it swells. It goes off wherever you've got to.
- Boss: Medium stance.
- Sound: A long pull of air going inward, then a huge soft foom that you feel in your stomach and a roll of thunder going away.
- Reading: [Maren Ash held her breath as a child until she went bright. The other kids dared her. They stopped.]

**Barrier** (Active, Flat)

- Today: An absorption shield for ten seconds and a shock when it breaks. Fine, and forgotten once it's up.
- After: A plate you aim. It curves in front of you and turns with your look. Shots it takes are stored, and a second press throws them back as one blast.
- Where it shines: Raise it as a shot arrives and that shot is thrown straight back.
- Boss: Blocks ranged hits. The stored blast is a medium stance hit.
- Sound: A heavy hum of pressure. Every hit it takes lands as a dull thud, each one tighter than the last, so you can hear it filling. It breaks with a slam and a rush of air.
- Reading: [A schoolteacher called Wen Hadley stood in front of thirty children and said no. It was a very good no.]

**Charged Shot** (Active, Keep and polish)

- Today: Hold, let go. Rebuilt on 30 October with its own hands and six sounds. You haven't heard it yet.
- After: Keep it. A window at the peak makes it pierce. Hold past that and it grows unstable and hurts you a little, for a bigger shot.
- Where it shines: Let go at full charge and it goes through whatever it hits. Hold past that for a bigger, unstable shot that hurts you a little.
- Boss: Medium stance.
- Sound: Already its own. First on your listening page.
- Reading: [Piet Rennick spent six years on a single shot and missed. The second one took four seconds.]

**Overload** (Ultimate, Good, needs a hook)

- Today: A one-second gather, a blast radius 9, then +25% and half cost for eight seconds. Strong. 60 energy on a 60 second timer.
- After: Fills from how you fight instead of a timer. During the gather everything you've left Charged lights up and goes off with it.
- Where it shines: Use it once plenty of things are Charged. Each one goes off with it.
- Boss: Massive stance.
- Sound: A swelling choir with circuits crackling inside it, a beat of silence, then a sub you feel in your chest.
- Reading: [Dagny Holt's heart stopped and started forty times on the way down. The last time, it took the whole street with it.]

**Prism Lance** (Ultimate, Flat)

- Today: The Prism path's ultimate: a lance that splits into four seeking beams at the first thing it hits. It reads as Beam at a higher tier.
- After: A fan of five colours you sweep like an open hand, each one a different Force. Hold and they close into one.
- Where it shines: Sweep the open fan across a crowd, and close it on the big one.
- Boss: Heavy stance when closed.
- Sound: Five roaring streams of air, wide apart, that pull in together into one solid blast as they close.
- Reading: [Ottilie Graves taught glassblowers. She could make sunlight do as it was told, and she was very strict with it.]

### Shadow

**Shadow Step** (Active, Keep and polish)

- Today: Teleports behind a target within 16 blocks, next hit x1.6. A starter and a really good one. With nothing aimed it just blinks 8 blocks ahead.
- After: Keep it. With nothing in sight it steps you to the nearest shadow instead of straight ahead. Stepping behind something mid-swing is the big one: the blow goes through where you were.
- Where it shines: Step while something is mid-swing. The blow goes through where you were and you're behind it.
- Boss: Your next hit counts as an opening.
- Sound: In the taster. A breath taken in and cut off, two frames of nothing, then a low slice.
- Reading: [Nell Aubry used to sneak up on her sister. Then she stopped needing the floor in between.]

**Cloak** (Active, Flat)

- Today: Six seconds unseen, drops aggro, first strike x1.8. A plain stealth button.
- After: Light matters now. In the dark it lasts twice as long and in full sun it flickers. Sound drops away for you too, so you feel hidden.
- Where it shines: Use it in the dark, where it lasts twice as long, or before anything has noticed you.
- Boss: Bosses keep tracking you by sound. It only breaks their lock for a beat.
- Sound: A cloth sweep, then everything you hear loses its top end until you come out.
- Reading: [Emeric Dole wasn't invisible. People just stopped being able to care that he was there, and he says that's worse.]

**Echo Swap** (Active, Start again)

- Today: Swaps you with your summon or decoy. Without one it refuses, which is the worst thing a power can do.
- After: Swap with anything you've hit in the last ten seconds, or with the ghost you leave behind every time you cast. Swap a foe into your own Well.
- Where it shines: Swap a creature into something bad: your Well, a drop, the path of a charging boss.
- Boss: You can't move a boss, but you can swap with your own ghost, so it's never dead.
- Sound: Two quick rushes of air, one in each ear, as you and it trade places.
- Reading: [Odile Marsh stood in for her twin at a wedding and nobody could tell. Eventually neither could they.]

**Legion** (Ultimate, Good, needs a hook)

- Today: The Shadowbinder's ultimate: four or more shadow soldiers for 20 seconds.
- After: They stand up in the shape of the last things you killed, as ink, for twenty seconds, then they're gone. You keep nothing, so the no-allies rule stands.
- Where it shines: Call it just after a big fight. They stand up in the shapes of what you just killed.
- Boss: Draws its attention once. Medium stance if they land together.
- Sound: A low chant with marching ink. Every soldier's step lands on the beat.
- Reading: [Corvin Hask went into a gate with forty people and came out with their shadows. He says they came along on their own.]

### Mind

**Danger Sense** (Passive, Start again)

- Today: Anything about to hit you glows red a moment before it does. Useful and quiet.
- After: Reinvented, see Always On. The blow draws its path in the air before it lands, a note climbs in the ear it's coming from, and a late dodge pays you back.
- Where it shines: Move as late as you dare. It's the one passive you can get better at, and a dodge you nearly missed pays the most.
- Boss: Reads the boss's tells early.
- Sound: A thin rising tone placed exactly where the danger is, a different one for Light, Heavy and Perilous.
- Reading: [Hester Pike flinched eleven seconds before the bridge fell. Her whole family crossed it on a different day because of that.]

**Dominate** (Active, Start again)

- Today: Takes over a creature for eight seconds. On a boss it refuses with "too strong", right when you want it most.
- After: Give one order. Look at a creature, then look where you want it to go or what you want it to hit. Puppet King at level 5 gives three orders.
- Where it shines: Order the biggest thing in the room to hit the second biggest.
- Boss: It shakes you off, but it loses a beat. Its next blow is held longer.
- Sound: A low pressure that gets into your head, and a hum like someone about to speak.
- Reading: [Lucan Ferro only ever said please. Nobody has ever been able to explain why that was so frightening.]

**Read Intent** (Active, Flat)

- Today: Ten seconds of seeing who's hunting you, and they take 15% more and deal 15% less. In practice a stat buff you press and forget.
- After: One creature, read. Its next blow shows on the ground before it starts, and your next cast on it hits harder and always lands. The bonus is only against that one.
- Where it shines: Read the thing that's about to be a problem. You see its next blow on the ground, and your next cast on it hits harder.
- Boss: Works in full. It reads the real move the boss has queued.
- Sound: A clock ticking in your head, speeding up before each blow.
- Reading: [Aya Lindqvist played cards for money until nobody would sit down with her.]

**Decoy** (Active, Flat)

- Today: A copy that draws attention. Afterimage's cousin.
- After: It walks, runs and looks like you. Cast it as you dodge and you vanish while it takes over. It can carry a trap.
- Where it shines: Drop it when you're being chased. It keeps running and they keep following it.
- Boss: Draws one attack from a boss.
- Sound: Your own footsteps, very slightly out of time.
- Reading: [Rafe Colby got out of every fight he was ever in by being somewhere else. He'd be the first to tell you so, if you could find him.]

**Foresight** (Active, Good, needs a hook)

- Today: A tenfold slow and the first blow misses. Flow's cousin.
- After: See the next second. A gold ghost of the boss plays its next blow on the real path it will take, and you hear the hit backwards before it lands.
- Where it shines: Use it on a boss you've never fought. You see each big blow coming along its real path.
- Boss: This is what it's for.
- Sound: The hit that's coming, played backwards and muffled, so you feel it pull at you before it lands.
- Reading: [Joan Thessaly told the Choir what it was going to ask her, answered it, and left.]

### Architect

**Wall** (Active, Fold into one)

- Today: Builds a wall on a 2 second cooldown. Wall, Ramp, Floor and Cone take four of your six keys, so nobody can play Architect and anything else.
- After: All four fold into one power, Blueprint, in one slot. Press it and Z X C V become wall, ramp, floor and cone for a few seconds, snapped to a grid. You keep every level you've earned.
- Where it shines: Put it between you and something shooting at you.
- Boss: A boss charging into a fresh wall stops dead and takes a stance hit.
- Sound: A ratchet tick and a set-stone knock.
- Reading: [Ida Rask could build a wall faster than you could throw a rock at her. Her brothers checked.]

**Ramp** (Active, Fold into one)

- Today: A ramp on a 2 second cooldown. Its level 2 step is called Blueprint, like four other powers'.
- After: A shape inside Blueprint, with its Rush, Rails and Launch Ramp steps at its own level.
- Where it shines: Lay it in front of something charging at you and watch it leave the ground.
- Boss: Fair footing in a boss room.
- Sound: A stone block sliding home up a slope.
- Reading: [Ida's little brother Tobin couldn't climb the walls she built, so she made him a way up.]

**Floor** (Active, Fold into one)

- Today: A floor on a 2 second cooldown.
- After: A shape inside Blueprint, with Spring, Hold and Sky Bridge at its own level.
- Where it shines: Lay it under yourself over a drop, or across a gap for someone else.
- Boss: Footing over a drop.
- Sound: A flat heavy slap of a slab landing.
- Reading: [Ida's floor went out over the river and stopped a yard short of the far bank. She said that was all the stone she had.]

**Cone** (Active, Fold into one)

- Today: A small roof shape on a 2 second cooldown.
- After: A shape inside Blueprint, with Lantern, Shatterproof and Pagoda at its own level.
- Where it shines: Drop it over something small and it's stuck inside for a moment.
- Boss: Shelter from what falls.
- Sound: A small roof clink that settles.
- Reading: [Ida built the little roofs last, over people who had no time to get indoors.]

**Rampart** (Active, Good, needs a hook)

- Today: A small fort in one press.
- After: You draw it. Hold and sweep your look across the ground and the wall rises along the line you drew, gatehouse and all.
- Where it shines: Close the line back on itself and you've made a fort. A closed ring rises twice as fast.
- Boss: Absorbs one charge.
- Sound: Stone courses grinding up one after another and a portcullis clang at the end.
- Reading: [Bastien Cole walled in a whole village by himself in a night, and then he forgot to leave a gate.]

**Spire** (Active, Flat)

- Today: A pillar under your own feet that throws you up, along with whatever's next to you. Forgettable.
- After: It rises under what you're looking at and takes it up. Stand on one yourself for height, and a turret on top gets twice the range.
- Where it shines: Put it under something heavy. The higher it goes, the further it falls.
- Boss: A small stance knock.
- Sound: A screech of rock rising, stopped dead with a stone clap.
- Reading: [Vera Sund was tired of being looked down on, and it turns out that was a thing you could fix.]

**Collapse** (Active, Start again)

- Today: Brings down your constructs. With none it refuses.
- After: Brings down anything overhead: your builds, or ceilings and overhangs, which put themselves back afterwards.
- Where it shines: Lure them under a ceiling or an overhang, then bring it down.
- Boss: Heavy stance when something falls on it.
- Sound: A long groan, then stone slamming in a rolling cadence.
- Reading: [Everything Ida Rask built came down the day she died. That's what this is.]

**Citadel** (Ultimate, Good, needs a hook)

- Today: The Architect path's ultimate: a dome of hard light you heal and shelter inside.
- After: Fills from how you fight. The dome becomes a real fortress that folds up round you in sections you can hear, with slits and rails for your other powers.
- Where it shines: Call it when you're surrounded. The sections knock back whatever's standing where they land.
- Boss: Blocks one big blow, then cracks.
- Sound: A huge mechanism folding, stone on stone, ending in one heavy gate shutting.
- Reading: [They found Ida's last building at the bottom of a gate. It's still standing and nobody has worked out how to get in.]

### Tech

**Snap Cable** (Movement, Good, needs a hook)

- Today: A quick pull. It overlaps the Tether, which is better for getting around.
- After: A weapon first. On a light target it drags it to you, on a heavy one it drags you to it, and letting go at the closest point slings you past.
- Where it shines: On something heavy, keep hold until you're close and you sling past it.
- Boss: Drags you in, and counts as a charge-in blow.
- Sound: A winch dragging, the line snapping tight with a thud, and a fast reel.
- Reading: [Tam Okoro fixed cable cars above the Deep Lift for thirty years. He fell four times and the cable always caught him.]

**Jet Burst** (Movement, Flat)

- Today: A short boot thrust. One more way to go up.
- After: A thrust you aim and chain. It leaves a line of heat that burns what crosses it, and it cancels any recovery.
- Where it shines: Chain them across a crowd. Each line of heat burns what crosses it.
- Boss: Light stance through the burn line.
- Sound: An ignition snap and a crackling afterburner that tails off.
- Reading: [Someone in the Above strapped an engine to a test pilot's boots. She wrote down "again" and nothing else.]

**Drone** (Active, Flat)

- Today: A drone that orbits you for twenty seconds and zaps whatever you're fighting. It runs itself.
- After: You aim it. Hit something with a power and your drone fires on that. It can catch a Beam and send it on.
- Where it shines: Hit things with your powers. The drone fires on whatever you just hit.
- Boss: Steady light stance.
- Sound: A small motor, servo clicks and quick dull shots.
- Reading: [Precursor maintenance unit 14 followed a woman home in the last year of the Halo. The notes don't say why she kept it.]

**Turret** (Active, Flat)

- Today: Set it and forget it.
- After: Where you put it matters. High up it sees further, and on a Spire twice as far. It overheats if it never stops firing, so you point it away.
- Where it shines: Put it on high ground, or on a Spire, where it can see twice as far.
- Boss: Steady light stance.
- Sound: A hydraulic unfold, a slow deep autocannon and a falling cool-down tick.
- Reading: [The Ascendants left these guarding a door on the Halo. They're still guarding it. There's nothing behind it any more.]

**Railgun** (Active, Keep and polish)

- Today: Hold 1.2 seconds for a 48-block pierce. Its charge sound is the Haymaker's.
- After: Keep the idea. Holding zooms in a little, firing shoves you back, and the shot leaves a line of light. Level 5 punches a real tunnel that fills itself back in.
- Where it shines: Hold to full charge and fire. It goes through walls at level 5, and a longer hold always means a bigger shot.
- Boss: The most stance you can do from range.
- Sound: In the taster. A real electric arc building, heavy relays closing faster, a split second of nothing, then a crack and a boom going off down the line.
- Reading: [Imre Kallo built this to send a message to the Halo. It got there. It's still in the wall.]

**Nanite Skin** (Active, Flat)

- Today: Armour for a while. The third self-guard after Brace and Barrier.
- After: It flows to where you're being hit. The side taking the blows thickens and shows its scales.
- Where it shines: Raise it when you're already hurt. Below half health it repairs you too.
- Boss: Takes a third of one big hit.
- Sound: Millions of tiny clicks moving round your body, like a swarm settling.
- Reading: [Beatrix Lund had a body that repaired itself and a mind that couldn't keep up. She kept asking whose hand it was.]

**Drone Swarm** (Ultimate, Good, needs a hook)

- Today: The Engineer path's ultimate: a swarm of drones.
- After: Fills from how you fight. It follows what you hit, and at level 5 forms one lance.
- Where it shines: Call it on the thing you're already hitting. They all start there.
- Boss: Medium stance once they form up.
- Sound: A wall of small motors panned round you, rising as they form.
- Reading: [Unit 14 had children, more or less. Nobody asked it to.]

### Spirit

**Aura** (Passive, Start again)

- Today: 1.2 damage every two seconds to what's near you. You forget it's there.
- After: Reinvented, see Always On. A living mantle of fire round your real body. A tongue of it goes out, bites and comes back, and it shows how the fight is going.
- Where it shines: Stand your ground for a moment in a fight. The next strike goes out as a whole arm of light and hits twice as hard.
- Boss: Light.
- Sound: A low temple drone that only comes up near enemies.
- Reading: [Everyone has this. Most people just call it being in a room with someone.]

**Spirit Palm** (Active, Good, needs a hook)

- Today: Reach 4.5, and an echo hits a second later. It refuses on a whiff.
- After: A whiff still throws a small palm. Right at arm's length, the echo comes back doubled.
- Where it shines: Hit from the very end of your reach. The echo comes back doubled.
- Boss: Medium stance from the echo.
- Sound: A bare-hand slap, then a deep thud from inside whatever you hit, a moment later.
- Reading: [Brother Anselm's students used to bow to the wall he had just hit, because a second later it would fall over.]

**Mend** (Active, Flat)

- Today: A heal. Press and wait.
- After: Breathe in. Hold, and let go at the top of the breath for a bigger heal. Getting hit breaks it. Players near you still share it.
- Where it shines: Hold it for longer when you've got room to breathe. The longer the breath, the bigger the heal.
- Boss: It's a drink to a boss: under the new fights it will notice.
- Sound: A long in-breath, then a warm low swell as it lands.
- Reading: [Subject 1 cried for an hour. The notes leave out that everyone else in the room felt better afterwards.]

**Third Eye** (Active, Flat)

- Today: Eight seconds of seeing every hostile through walls. Very like Read Intent.
- After: It sees the world, not the fight: sleeping bells, hidden kin, secret doors, cave mouths. The tool for everything we've built since.
- Where it shines: Open it when you're somewhere new. It shows what's hidden in the world, not just what's in the fight.
- Boss: Shows a weak side for a beat.
- Sound: A low intake as it opens, and soft thuds placed where things are, as if something knocked from behind the wall.
- Reading: [Zofia Harker could always find your keys, and she also knew what you had done with the money.]

**Cord Lash** (Active, Good, needs a hook)

- Today: A whip with a pull. It overlaps Snap Cable.
- After: It ties two things together. Lash one, lash another, and they're pulled together and share hits.
- Where it shines: Tie the weak one to the strong one. They share hits.
- Boss: Ties a boss to one of its adds, and they share damage.
- Sound: A leather crack, then a taut string hum while they're bound.
- Reading: [When Marek Sayle's sister left her body, he pulled her back with a rope that wasn't there.]

**Bulwark** (Active, Good, needs a hook)

- Today: A dome for eight seconds: allies inside take half damage, arrows dissolve and enemies get pushed out. The Warden's signature.
- After: It arrives with force: a ring throws everything out the moment you raise it. Party members inside count as guarded.
- Where it shines: Raise it in the middle of a crowd. It throws everything off you as it comes up.
- Boss: Blocks one big blow.
- Sound: A huge slam of pressure, felt more than heard.
- Reading: [Hild Aran stood on the last bridge at Carillon and told the Deep it was closed. It took the Deep eleven days to argue.]

**Hundred Palms** (Ultimate, Keep and polish)

- Today: Two seconds of palms too fast to see, ending in one that launches. On paper the most exciting ultimate you have.
- After: Keep it. It's already the most fun button on the bar, so it gets a bigger last palm and a sound that builds to it. Nothing to press in time.
- Where it shines: Save it for something that can't get away. Held, stunned or cornered, it takes every palm.
- Boss: Heavy stance, more on the beat.
- Sound: Slaps building to a blur and ending on one huge drum.
- Reading: [Master Oyelaran was asked how many hands he had, and he said he'd lost count. He had two.]

**Void Maw** (Ultimate, Good, needs a hook)

- Today: A vortex close to Gravity Well that feeds you, with an execute under 15% that skips bosses.
- After: It bites. Press again and the jaws shut on whatever's inside. It eats shots too.
- Where it shines: Wait until most of them are inside before you shut the jaws.
- Boss: No execute. It takes stance and heals you.
- Sound: A wet cavern groan and a final clack of teeth.
- Reading: [Something came up out of the Deep and ate Sorrel Vance, and then for some reason it started doing what she wanted.]

**Waking Dream** (Active, Start again)

- Today: A temporary door, bridge or stairs. A lovely idea with little use.
- After: What you look at becomes real for a while. Hold your look on a wall and a door opens that really leads through, for a minute.
- Where it shines: Use it on a dead end. What you look at becomes real for a while.
- Boss: A door in a boss room only lasts a blink.
- Sound: A slowed recording played backwards, and the room tone changing as you pass through.
- Reading: [One of the Sleepers walked out of the dream for an afternoon and left a door behind. We still don't know where it goes.]

**Storm Call** (Active, Flat)

- Today: Calls lightning down on up to five enemies near you, using a vanilla bolt. Stronger in the rain. With nobody near, it refuses.
- After: A real storm cell overhead. Rain starts, and bolts land where you look. Wet foes and metal carry it.
- Where it shines: Call it over water or on things wearing metal.
- Boss: Medium stance per bolt.
- Sound: Thunder walking closer, rain coming across the ground, one crack right overhead.
- Reading: [Anneke Dorr was a fisherman's widow who stood on the beach and shouted at the sky, and it shouted back.]

**Ember Step** (Movement, Flat)

- Today: A fiery dash, from walking through lava.
- After: You leave burning footprints and lava is safe to cross while it lasts.
- Where it shines: Go through something Chilled and it bursts into steam.
- Boss: A burn line it has to step across.
- Sound: A flare and an ember crackle that follows your feet.
- Reading: [Bruno Cassar walked out of the foundry fire carrying three people. He left footprints in the iron.]

**Frost Nova** (Active, Flat)

- Today: A cold burst, and the only frost power in the game.
- After: Freezes the real ground and water. Cross a river on it, and it thaws by itself. Frozen foes shatter.
- Where it shines: Freeze them, then hit them with something heavy. They shatter.
- Boss: Leaves it Chilled: slower wind-ups for a moment.
- Sound: Ice creaking and glassy crystals growing outward.
- Reading: [Little Signe Aalto got lost in the snow when she was six and came home a week later perfectly warm.]

**Ghost Step** (Movement, Flat)

- Today: A dash through walls. The fifth dash in the game.
- After: It shows you the room on the other side for a moment before you go.
- Where it shines: Use it on the wall you can't get round. You see the other side before you go.
- Boss: Passes through a blow.
- Sound: Everything muffles as if under water, you hear the room beyond, then a breath.
- Reading: [Nobody's sure Lorne Pascoe ever really was in the room. He left a wife and two kids, and he's still expected for dinner.]

**Twin Strike** (Active, Start again)

- Today: A hidden power that repeats your hits for a while.
- After: Your second self stands next to you for eight seconds and does everything you do, a beat late.
- Where it shines: Cast something big straight after. Your second self casts it too.
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

## The whole system

11. **Get: having a power should be an event**: Hold use for a second and the Core cracks in your hand. The first Reading plays and the power goes on a free key if there is one. Your first press is free and can't be refused, so you feel it on the spot. A Core of a mastered power can be poured into another power of the same family as mastery.
12. **Equip: your bar should be something you build**: Three saved sets that you name, swapped from the K page or a held key once you're out of a fight. The card gains damage (`PowerPreviews` already works it out for Cores), where it shines, the Duets you've found, the Reading and a Try button that shuts the menu and gives you one free press. Sort by family, level or what a power does.
13. **Cast: stop throwing presses away**: A press buffer: in the last 0.15 seconds of a cooldown or during another power's wind-up, your press is kept and fires the moment it can. Hold the key on the dozen or so powers that land somewhere to see where, with a ring in the power's own look, and let go to cast. A tap still casts at once. The reason moves to your crosshair, once a second, with a low thud that differs for no energy, not ready and no room.
14. **Combine: help you play the Duets you've found**: When a creature has a State that one of your keys can use in a Duet you've already found, that key shows a small glint. Duets you haven't found never show. The first time you find one, the moment slows for a quarter of a second and its name goes up once, large. States sit on the creature's body and on its lock-on panel, and a party member's count too.
15. **Level: leave it alone and add a little**: Everything stays. The window gains a Try It press: your next cast of that power is free and shows the new step. When you're a few uses from the next level, the slot's frame draws a thin line round itself. The window also carries the next line of the Reading.
16. **Read: you should be able to fight without looking down**: A slot that comes back gives a bright glint and a quiet tick pitched to its family, and a full R gets a bigger one. The cooldown becomes a bright sweep you can catch from the corner of your eye. Family colours on every edge. A ghost on the energy bar shows what your last press cost. Two thin arcs at the crosshair for energy and R, only during a fight, with a setting to turn them off.
17. **Always On: passives you can see, read and switch**: Their own tab on the K page, one row of icons on the Tab glance, and Full, Quiet or Off on each. Every passive shows and sounds from level 1, always tells you it was you, and the System names it the first time it acts. Section 6 has all of it.

## Play styles

### Kinetic: Up close and heavy

You want to walk up to it and hit it until it's somewhere else.

- A fight: You start close and stay close. Shockwave or Ground Slam throws them in the air, Haymaker sends the biggest one through a wall, and Brace stops whatever comes back.
- Together: Most of these leave things Lifted, and the rest hit Lifted things hard, so every power sets up the next one.
- Goes with: Velocity, for the run-up: a Shockwave at the end of a Dash is Sonic Stamp. Shadow, for the surprise: charge a Haymaker, Shadow Step, and that's Knock Knock.
- Style bonus, Heavy Hands: Things you knock into the air stay up half a second longer.

### Velocity: Never where it lands

You want to be gone by the time the blow gets there.

- A fight: You keep moving. Dash through the hit, leave an Afterimage carrying on where you were, and when it gets bad, Flow slows the whole room down.
- Together: These all pay you for what you do on the way past. Standing still wastes them.
- Goes with: Kinetic, for something to land: Sonic Stamp again. Energy, for what you do in slow time: Flow and Beam make Slow Burn.
- Style bonus, Never Still: While you're moving fast, your powers come back 10% sooner.

### Flight: Above it

You want the fight to be happening underneath you.

- A fight: You get up high and stay there. Gravity Well pulls them together, Telekinesis picks up the one that matters, and Meteor Dive finishes it.
- Together: Gravity powers gather things up and air powers get you over them. Anything pulled together is waiting for something big to land on it.
- Goes with: Energy, for the big finish: a Nova inside a Well is Collapsing Star. Tech, for the angle: a Railgun through a Well is Slingshot.
- Style bonus, Good Air: Powers you cast in the air cost 15% less energy.

### Energy: Stand back and light it up

You want to stay well back and make the room very bright.

- A fight: You keep your distance and spend big. Beam wears things down, Charged Shot picks off what's coming, Barrier holds the line and Nova clears whatever gets close.
- Together: Energy powers leave things Charged, and Charged things set each other off. It all adds up to Overload.
- Goes with: Flight, to bunch them up: Collapsing Star. Mind, for a nasty surprise: a Decoy that goes off as a Nova is Surprise Party.
- Style bonus, Full Tank: Your energy refills 15% faster when you haven't been hit for three seconds.

### Shadow: Get there first

You want the fight to be over before it knows it started.

- A fight: You pick the moment. Cloak to get close, Shadow Step behind the one that matters, and Echo Swap out when it turns round.
- Together: Every Shadow power is about being somewhere they didn't expect, and your first hit is always your biggest.
- Goes with: Kinetic, for one enormous hit: Knock Knock. Flight, for a dirty trick: swap something into your own Well and that's Switcheroo.
- Style bonus, First Blow: Your first hit on something that hasn't noticed you does 25% more.

### Mind: Knows what you'll do

You want to know what it's going to do, and then decide what it does instead.

- A fight: You watch first. Read Intent shows you the next blow, Foresight shows you a boss's, Dominate turns the big one on the others and Decoy gives them something else to hit.
- Together: Mind powers leave things Read, and everything else you've got hits a Read target harder.
- Goes with: Velocity, to use what you saw: Dash through a blow you read and that's Already Gone. Energy, for Surprise Party.
- Style bonus, Two Steps Ahead: Anything you've Read stays Read for two seconds longer.

### Architect: Bring your own ground

You want to decide what the place looks like before the fight starts.

- A fight: You get there early and change it. Blueprint for cover, Rampart for a fort, Spire to throw things up and Collapse to bring the roof down on them.
- Together: Everything you build is something your other powers can use: to stand on, hide behind or drop on someone.
- Goes with: Tech, to man the walls: a Turret on a Spire is Crow's Nest. Energy, to use them: a Beam through your own wall is Prism Wall.
- Style bonus, Home Ground: Within six blocks of something you built, you take 10% less damage.

### Tech: Let the machines work

You want things that keep working while you do something else.

- A fight: You set up, then you aim. A Turret on high ground, a Drone after whatever you just hit, the Railgun for the big one, and Grapple for when it goes wrong.
- Together: Your machines fire on what you hit, so every cast counts twice.
- Goes with: Architect, for somewhere to put them: Crow's Nest. Velocity, for a second shot: your Afterimage firing the Railgun again is Two Shots.
- Style bonus, Well Maintained: Your drones and turrets last 25% longer.

### Spirit: An answer for everything

You want to be hard to kill and to have something for every situation.

- A fight: You carry a bit of everything. Spirit Palm up close, Cord Lash to tie them together, Bulwark when it gets bad, Mend when it's over, and fire, frost or a storm for whatever's in the way.
- Together: It's the widest family: body and soul, heat, cold and storms. Those set each other off, so Ember Step through something Chilled makes steam.
- Goes with: Kinetic, for the one-two: Spirit Palm inside a Haymaker is One-Two. Energy, to protect people: a Mend into your Barrier is Sanctuary.
- Style bonus, Still Standing: Your heals and shields are 15% stronger.

## Always On

### Aura (Spirit, about 5 hours)

Your soul doesn't fit inside you any more.

A living mantle of jade fire wraps your actual body. It isn't particles floating near you. It follows your shoulders, your arms and the line of your head, it streams behind you when you run and it flattens when you land. Every two seconds a tongue of it peels away from you, goes for something in reach, bites, and comes back carrying a bright bead that sinks into your chest.

- Today: 1.2 damage every two seconds to whatever hostile is within 3 blocks. A small quiet flash on up to three of them and a few faint motes. No sound of its own. Wisps only arrive at level 2. You had it on and things were taking damage from nowhere.
- At rest: Low and slow, like breathing. A thin line of light round your body and a lick or two at the shoulders.
- Roused: Something hostile within about 10 blocks and it stands up. It gets taller and leans toward the nearest threat like a flame in a draught, so it tells you where to look.
- When it acts: The tongue. One for each target, up to three drawn, a beat apart so they read as separate strikes.
- First person: Never in the middle of your view. Licks rise past the bottom corners and curl round your hands. A tongue starts beside your hand and thins as it leaves, so it's gone before it reaches your crosshair.
- Others see: The whole figure. It's your Path's colour once you have one. It frays and gutters when you're nearly dead and burns tall when your R is full, so anyone who looks at you can read how the fight is going.
- Sound: A low temple drone that only comes up when something's near. Each tongue is a soft indrawn breath and a knock when it lands. The bead coming back is one soft low pulse in your chest. There's nothing high or bright in it, and all of it sits under everything else in the mix.
- How you know: A line of light from you to the thing it hit, every time. The damage number is jade with a small Aura mark on it. The first time it ever strikes, the System says what that was, once.
- Where it shines: Stand still for one second in a fight. The mantle gathers up, and the next strike goes out as a whole arm of light that hits twice as hard.
- Boss: Light hits that take a little stance. A tongue on a boss stays attached for a moment, so you can see it's doing something. The 20% cap still applies.
- Quiet and Off: Quiet keeps the tongues and a thin edge of light and drops the mantle. Off stops it: no damage, no healing, nothing to see.
- Fair: Crouch and it pulls in tight and stops striking, so it can't start a fight you were sneaking past. Today it can: it hits any hostile in reach, and each hit puts you in combat. It never touches pets, villagers or other players. Damage at level 1 is what it is today.
- Steps: Wisps: Wisps break off the mantle and circle you in a fight. Its reach grows by a block.; Calm: Out of a fight the mantle goes bright and still, and you heal twice as often.; Drag: A tongue holds on for a moment before it lets go, and whatever it held is slowed.; Soul Guard: Hit you up close and the whole mantle turns on them at once.
- Reading: [Everyone has this. Most people just call it being in a room with someone.]

### Danger Sense (Mind, about 3 hours)

You flinch before it happens.

The blow draws itself in the air before it lands. A thin line, the exact path of the claw, the blade or the arrow, shows up the moment the creature commits to it. A rush of air swells in the ear it's coming from. Get out of the way at the last moment and the line snaps, the world's sound drops out for half a beat, and the thing that missed is left open.

- Today: Anything about to hit you glows red, with a quiet ping. The arc at the edge of your screen only comes at level 2. It's useful and it's the best of the four, but it's still an outline on a creature.
- At rest: Nothing at all. No glow and no sound. That's the point: when this wakes up, it means something.
- Roused: When something commits to you, the edges of your view cool by a shade.
- When it acts: The path of the blow, the note in the right ear and, if you dodge, the miss.
- First person: The line is in the world, so it's exactly where the blow will be. Only the line and the note. Nothing new sits on your crosshair.
- Others see: Your eyes light pale gold for a moment before the hit lands. From outside it looks as if you knew.
- Sound: A rush of air that swells in the ear it's coming from, deeper for a Heavy and with a low growl under it for a Perilous. There's no beep. The miss is a soft thud with everything else ducked under it.
- How you know: The line only exists because of this power, and a gold ripple comes off you when a dodge pays off.
- Where it shines: Move as late as you dare. The later you step out, the more energy you get back and the longer the thing that missed stays open.
- Boss: Reads a boss's tells, never sooner than the boss's own wind-up starts. It tells you where. The fight still decides when.
- Quiet and Off: Quiet keeps the note and the arc and drops the line. Off is nothing at all.
- Fair: Only blows aimed at you, and at most the nearest two lines at once. Anything with no move data gets today's red glow, so nothing is lost.
- Steps: Edge Arc: An arc at the edge of your screen points to threats you can't see.; Sooner: The line and the note come 0.2 seconds earlier.; Read the Shot: A drawn bow shows you the whole flight of its arrow, in gold, before it's loosed.; Sixth Sense: A dodged blow slows whatever threw it for a second, and the world takes a full beat.
- Reading: [Hester Pike flinched eleven seconds before the bridge fell. Her whole family crossed it on a different day because of that.]

### Wall Stride (Velocity, about 3 hours)

Nobody told you walls were for stopping.

Walls become road. Sprint beside one and your hand goes out and drags along it, throwing sparks off your fingers. On the wall your feet leave prints that burn red and fade. The run stays white for as long as anyone could have held it, then turns red at the moment only Wall Stride is keeping you up, so you can see exactly what it's giving you. Come off the end and all of that speed goes into whatever you do next.

- Today: Your wall-runs last four seconds. At level 1 nothing shows that at all: its look only starts at level 2.
- At rest: Sprint within arm's reach of a wall and your free hand trails along it.
- Roused: On the wall: burning footprints, dust shaken loose behind you.
- When it acts: The wake turns red past the normal length. Leaving the wall throws a red ring behind you and carries the run's speed into your next hit or cast.
- First person: Your hand at the edge of the view, sparks running back past you and the horizon leaning over. Nothing near the crosshair.
- Others see: Someone running flat along a wall with a red line burning behind them.
- Sound: A quick patter of soles on whatever the wall is made of, climbing in pitch with the length of the run.
- How you know: The moment the wake goes red, it's the power holding you up.
- Where it shines: Cast or strike as you come off the wall. You get the whole run's speed, and the hit knocks them the way you were running.
- Boss: Nothing of its own. It feeds your next hit, and a boss room with pillars becomes somewhere to build speed.
- Quiet and Off: Quiet keeps the red at the end of the run and drops the prints. Off puts your wall-runs back to their ordinary length.
- Fair: The Ascent's wall-run stays the rule, and this only extends it.
- Steps: Spark Steps: Sparks kick off each step, and you get half a second longer on the wall.; Kick-off: Leaving the wall gives you a burst of speed.; Steady: A thin red line marks your path along the wall, and you don't sink at all.; Street Runner: A red ribbon trails you, you flip off the end, and each new wall within a second of the last adds a second.
- Reading: [Nobody told Ines Cardo that walls were for stopping you. She was four, and they never managed to explain it to her afterwards.]

### Water Run (Velocity, about 4 hours)

The sea is just another road.

A real wake. It fans out behind you, rolls outward and lasts for several seconds, so you can look back and see where you've been. At full speed sheets of spray stand up on either side of you. Stop dead and the whole wake catches up with you and breaks forward past your feet in a low wave that knocks over and soaks whatever's in front. And at level 5, run straight at a waterfall and you keep going, up it.

- Today: Sprint and you run on the water. At level 1 it throws the game's own splash particles, which breaks your rule about vanilla looks.
- At rest: Stand at the water's edge and the surface leans toward your feet in small ripples.
- Roused: The first step onto the water rings out from your foot.
- When it acts: The wake, the spray and the hard stop.
- First person: The horizon rocks a little with your stride and spray comes in low at the edges of your view.
- Others see: A long white V across a lake with somebody at the point of it.
- Sound: Slapping sheets of water that climb to a steady hiss as you speed up. The hard stop is one heavy slap and a long wash running out.
- How you know: You're standing on a lake.
- Where it shines: Hit the water at a sprint out of a jump without breaking stride. Then stop hard with your enemy in front of you.
- Boss: Not a fight power. Anything the wave soaks counts as wet for Charge.
- Quiet and Off: Quiet keeps the running and a thin white line. Off means sprinting swims again, which today you can only get by holding crouch.
- Fair: The wave pushes and barely hurts. The waterfall only works on real falling water with room above it.
- Steps: Wake: No slowdown as you step onto water, and the wake widens.; Skip: A leap off the water keeps your speed, and each stride leaves a ring of light.; Still Water: You can stand on water for two seconds, and it stills in a circle under you.; Tidewalker: You run up waterfalls, and at full sprint you throw a bow wave.
- Reading: [Jory Wake's boat sank a mile out. He says he didn't want to get his boots wet, and nobody can tell if he's joking.]

### Second Shadow (Shadow, about 5 hours)

Your shadow stopped copying you.

Your shadow has its own ideas. It's a beat behind you when you move. It's a little too long. When something hostile is near, it turns its head before you do. Stand somewhere dark for two seconds and you go unlit: the shadow comes up round your feet, your steps stop making a sound and creatures have to be much closer to notice you. The first blow you land from the dark, a black arm comes up out of the ground a moment ahead of your own.

- Today: Doesn't exist. Cloak is the only stealth power, and it's a press.
- At rest: In daylight, just your shadow being slightly wrong.
- Roused: Near something hostile it turns to look at it. In the dark, that's sometimes the only way you'll know where they are.
- When it acts: Going unlit, then the arm.
- First person: You see it on the ground ahead of you when the light's behind you. When you go unlit the edges of your view sink a shade and your own footsteps fall away.
- Others see: A shadow that doesn't match. In the dark, someone they can't quite make out.
- Sound: Your footsteps fade out over half a second when you go unlit. The arm is a very low wet thud and a breath you can't place.
- How you know: The black arm comes up out of your own shadow.
- Where it shines: Stay unlit until they're within arm's reach. The closer the first blow, the harder the arm hits.
- Boss: Once a boss is fighting you it can't lose you. You get one arm at the start, if you arranged to start from the dark.
- Quiet and Off: Quiet makes your shadow behave and keeps going unlit and the arm. Off is a normal shadow and normal footsteps.
- Fair: Only real darkness counts, and a torch ends it. Anything already chasing you keeps chasing you. You're never invisible, and other players see and hear you as usual.
- Earn: Land five first blows from the dark, with no light in your hands. Or find its Core in any gate that leans Shadow.
- Steps: Ink: Going unlit takes one second instead of two.; Hush: Creatures that lose sight of you in the dark forget you twice as fast.; Long Reach: The arm can come up out of any shadow within 6 blocks of you.; Both of You: For three seconds after the arm, your shadow stands up and fights beside you.
- Reading: [Marta Voss kept a lighthouse alone for thirty-one years. She always laid two places for dinner, and the relief keeper says both plates were clean.]

### Stormborn (Energy, about 3 hours)

Lightning likes you now.

In a thunderstorm the lightning starts finding you. A bolt comes straight down on your head and you stand there lit up and unhurt, with a full energy bar and your hands crackling. Your next cast goes out overcharged. Hit something while you're crackling and the sky finishes the job. Storms become the thing you go outside for.

- Today: Doesn't exist. No passive cares what the weather is doing.
- At rest: In clear weather, a tick of static off your fingers when you open an iron door.
- Roused: In rain the drops turn to sparks just before they reach you, and you feel thunder in your hands before you hear it.
- When it acts: The strike, every twenty to forty seconds in a storm, always with half a second of warning: a hum, and a line of light on the ground at your feet.
- First person: Your hands crackle at the bottom of your view. The strike whites out the edges, never the middle.
- Others see: A person being hit by lightning on purpose.
- Sound: A rising hum through the floor, the crack right on top of you, and a long fizz that fades as the charge does.
- How you know: You've just been struck by lightning and your energy bar is full.
- Where it shines: Stand still under the warning with an empty hand. Move and it lands beside you and you only get half.
- Boss: Only in a storm, so it rarely matters in a gate. Outdoors in a storm, the sky can land on a Lord, under the usual cap.
- Quiet and Off: Quiet keeps the strike and drops the sparks in the rain. Off and lightning goes back to ignoring you.
- Fair: The bolts are drawn, not real, so they never start fires, touch your builds or turn a creeper. Other players are never struck by it.
- Earn: Stand outside in a thunderstorm with an Energy power on your bar. The sky picks you out within a minute or two, and that first bolt gives it to you. Or find its Core in any gate that leans Energy.
- Steps: Grounded: A bolt can take your energy over its maximum for ten seconds.; Long Fuse: You stay charged twice as long.; Forked: The bolt forks into whatever's near you.; Eye of It: For as long as the storm lasts, every hit you land while charged brings a bolt.
- Reading: [Arlo Finch was struck nine times and kept all the hospital receipts. After the fourth he started going out in storms. His wife says he liked being noticed.]

## Passive ideas cut

- **A hero landing (Kinetic)**: Every fall ends in a three-point crouch and a crack in the ground. The Ascent's slam already owns landing.
- **Wind at your back (Flight)**: The air around you moves with you and pushes you along. There's no wind to read, and it's the Ascent's job again.
- **Ground that rises to your foot (Architect)**: Stones lift to meet your step on a slope. Road Ahead does this properly.
- **A drone on your shoulder (Tech)**: A small machine that follows you and helps out. That's a permanent ally, and you've said no to those.
- **Full hands (Energy)**: Your hands glow brighter the fuller your bar is. It's a nice touch but not a power. It's folded into how the bar reads.


## Eight new powers

### Titan (Your size, Ultimate, on R, wave 1, about 3 hours)

The R key is full. You grow, as big as the place allows, up to five times your height: nine blocks on open ground. For twelve seconds your swing sweeps a whole room, Z is a stomp that sends the ground out in a wave, and X picks up anything smaller than you and throws it.

- Thrill: The fight changes scale in a second. The thing you were dodging is at your waist. You can feel your weight: every step rocks the camera and throws up real dirt, and the trees you walk through go down.
- Only it: Nothing else in the catalogue changes your size. It's also the only power that gives your other keys new jobs while it lasts.
- Look: You stay yourself, armour and all, only huge, with hot orange seams at the joints. The growth comes in three heaves. Your shadow falls across the ground ahead of you and mobs look up.
- Sound: A creak like old timber taking load as you grow. Sub-bass footsteps you feel more than hear. Your own breath and your own power sounds drop about an octave while you're big, and come back up when you shrink.
- Where it shines: Start growing in the middle of a group. The first stomp lands on all of them at once.
- Boss: A boss can't be picked up. X shoves it instead and takes a big bite of stance. Every hit still stops at the 20% cap.
- Fair: Other players are never grabbed. In a cave or a gate you only grow as far as the roof allows, and if a roof closes in you shrink to fit, so you can't get stuck.
- Earned: Offered at the D Proving if most of what you've done with powers so far is Kinetic. Anyone else: a Core from any gate that leans Kinetic, and a Kinetic Core is guaranteed after three gates of that kind.
- Reading: [Bram Okoye was five foot two and lifted a bus off a child outside Carillon station. He weighs a lot more in the Choir's copy than he did in life. So will you.]
- Levels: Longer Stride: You cover ground at the speed your legs look like they should.; Uprooted: X can tear up a tree or a boulder and throw that.; Walking Earthquake: Each step ripples the ground and trips small things near your feet.; The Whole Height: Awakened. Seven times your height on open ground, and the first stomp cracks the land out to 30 blocks.
- Engine: The game's size attribute goes up to 16 times and we already use it on mobs (`MobScaling`). Step height, reach and jump are attributes too.
- Unknown: How the camera and first-person arms behave at five times size. One short test.

### Stopped Clock (Time, Ultimate, on R, wave 1, about 4 hours)

One tick of a clock, then nothing. Every creature near you stops dead, the colour drains out of the world and only you and your own powers keep theirs. For three seconds you walk around a fight that's holding its breath. Arrows hang where they were. Everything you do is held: your shots leave your hand and stop a metre out, and your hits pile up on whatever you struck. Then the clock starts again and all of it lands in the same instant.

- Thrill: It's three seconds of planning inside a fight, and then one enormous payoff that you built. You walk round a frozen boss laying shots around it like a firing squad. The better you compose those three seconds, the bigger the one frame at the end.
- Only it: Flow and Foresight slow things down. This is the only power where what you do waits, so you're stacking up a single moment instead of hitting things in turn.
- Look: On your screen every creature freezes mid-step, animation included. Held shots stay lit and slowly turn. A thin white mark appears for each hit waiting on a target. On release: one frame of white, then full colour comes back all at once.
- Sound: The tick. Then almost nothing: the whole mix drops to a low hum and your footsteps are the only clear thing. Each held hit adds one quiet note to a rising stack. On release the stack and every impact play as a single hit with a long tail.
- Where it shines: Stop the clock with a lot of things around you. Everything you do in those three seconds lands together, so a crowd is where it's biggest.
- Boss: Bosses don't stop. They drag at a quarter speed so a fight stays a fight. All your held hits on one boss count as a single hit for the 20% cap, and the release is worth Massive stance.
- Fair: Other players never stop. A held hit is dropped if its target has left the area. If you die or log out, nothing is held any more and the world starts again.
- Earned: Offered at the D Proving if you mostly fight with Velocity powers. Anyone else: take Flow or Foresight to level 3. You've spent long enough in slow time that one day it stops.
- Reading: [Yusra Demir mended clocks. When the Choir asked her what she wanted, she said one more minute with the shop open. She's still in it. It's still that minute.]
- Levels: Another Second: Four seconds, and you can hold one more shot.; Pushed Aside: Slap a hanging arrow or bolt and it goes where you sent it when the clock restarts.; Everything At Once: Held hits also set off any State on the target, all together.; Noon: Awakened. Six seconds. Rain stops in the air, and for a second after the release nothing near you can cast.
- Engine: `TimeSlow` already cancels creatures' ticks near you. The client has its own freeze switch (`TickRateManager`), and the renderer already honours it for every creature, so only you see the full stop.
- Unknown: Vanilla particles and rain aren't covered by that switch. Our own effects can hang. The rest needs trying.

### Road Ahead (The road, Movement, on Left Alt, wave 2, about 4 hours)

Hold Left Alt and start running. A strip of hard light lays itself down in front of your feet, tile by tile, wherever you're looking. Run off a cliff and it carries on. Look up and it climbs. Lean into a turn and it banks. It stays behind you for ten seconds and then falls away in pieces.

- Thrill: It's a line you draw across the sky with your own body. There's speed to build on the way down and speed to lose on the way up, so a long run is a real piece of driving. You can jump off it, drop, and land back on your own road further along.
- Only it: Every other way of moving is a burst or a hover. This one is continuous and you steer it, and it leaves something behind that's still there when you look back. Other players can run on it too.
- Look: Warm blueprint gold. The tiles snap into place with a small overlap, so it reads as something being built very fast. The edges glow where you've just been and cool as they age. At the end of its life each tile drops and rings off the next.
- Sound: A ratchet that clicks once per tile and speeds up with you until it blurs into one rising note. Creaks on the tight bends. A wooden boom as you pass twice sprint speed.
- Where it shines: Come off the end of the road with speed and land on a ledge or a creature. The speed carries into the Ascent's next move.
- Boss: Lay it round a boss and you can stay out of reach, but a hit needs a landing. Coming off the end onto a boss is a drop attack that counts your speed.
- Fair: It costs energy for every second you're climbing, less on the flat and none going downhill. Run out on the way up and the tiles ahead flicker for a second before you drop, so you always get a warning.
- Earned: Reach the top of an Elder Colossus or a Lumen Spire, any way you can. The Core waits in the cache beside the crown.
- Reading: [Pia Lund carried the post on foot between the hill towns. In the last winter she got tired of waiting for roads. There's a line of frost across the pass that still hasn't melted.]
- Levels: Banking: No speed lost in the turns.; Two Lanes: Wide enough to turn round on, and a friend can keep up beside you.; Kerb: Small creatures that touch it get tripped.; The Long Road: Awakened. It stays for thirty seconds, and going downhill pays energy back.
- Engine: Water Run already holds you up with client physics, and the Runner's hang already passes the game's floating check. A road is a list of points plus a drawn strip, with no block changes at all.
- Unknown: Whether it feels like driving or like sliding on glass. That's the whole power, so it gets its own test.

### Say It Back (Their moves, Active, on Z X C or V, wave 2, about 5 hours)

When a boss or Lord throws a big move at you and you dodge it, deflect it or read it cleanly, this key takes a copy for fifteen seconds. Its icon changes to show what you've got. Press it and you do that move: its shape, its reach, its colours and its own sound, from your own hands, aimed where you're looking.

- Thrill: The biggest thing in the room just tried to kill you with it, and now it's yours. Every boss gives you something different, so this power plays differently in every fight you'll ever have.
- Only it: It's the one power whose content comes from the enemy: 497 moves across 47 bosses, 47 of them signatures. It's also the answer to Fights That Read You. They read what you've started. This lets you read them back.
- Look: The copy is made of glass in the boss's own colours. A blow comes out as a huge glass limb the size of the boss's own, swinging from your shoulder through the real arc of the move. A ring of bolts is the boss's own bolts. A slam is its own crater, lit from inside.
- Sound: The move's own sound, taken up three semitones and dried out. You recognise it instantly, and it sounds like a bad impression of the thing that scared you.
- Where it shines: Say it back while the boss is still recovering from the original. It staggers harder.
- Boss: The damage is yours, worked out from your own level and never the boss's. A boss hit by its own signature takes double stance and can't use that move again for 20 seconds.
- Fair: Moves that make no sense in your hands get a translation: a summon gives you one short-lived copy of yourself, a guard gives you a perfect guard for a second, a heal heals you a little. It never copies another player.
- Earned: The first time you dodge or block a Lord's big move, at any rank, the System offers it.
- Reading: [Ines Okafor could repeat anything she'd heard once, in the voice she heard it in. Mostly she used it to win arguments with her mother. The Choir read her for eleven minutes, and she read it back.]
- Levels: Long Memory: It holds a copy for thirty seconds.; Two at Once: It holds two moves on one key, and each press spends the oldest.; Word for Word: Signature moves come out at the boss's full size.; Say It First: Awakened. If a boss starts a move you've said back before, pressing now beats it to the punch, cancels its move and takes a huge bite of stance.
- Engine: Every boss move is data (`KitMoves`): hit shapes, timing, sounds and what kind of ability it is. The 25 ability kinds are a fixed list (`Ability.Kind`), so one set of player versions covers every boss.
- Unknown: More than half the moves (265 of 497) are body blows, so the glass limb has to look right. One boss in a test will show it.

### Toll (Sound, Active, on Z X C or V, wave 2, about 4 hours)

You strike the air with your open hand. Every sound in the world drops away. Half a second later a bell goes off that's bigger than anything you've heard in this game, and you can see the sound leave: a thin gold ring going out across the land, through hills, out past where you can see. The Deep's creatures reel. And any bell you haven't found that's within earshot answers, faintly, from where it is.

- Thrill: The silence first. Then the size of it. You stand on a hill and watch a ring cross the valley, and then from somewhere out there you hear one small note come back.
- Only it: It's the only power that's as much for the map as for the fight, and the only one where the sound is the weapon. It also ties straight into the game's main goal.
- Look: A hair-thin gold ring that bends the view as it passes, shakes dust off every surface it crosses and lifts the birds. An answering bell shows as a small ring on the horizon in the right direction.
- Sound: Built from the real recorded bells already in the game, low and heavy. The silence is the existing muffle system taken nearly all the way down, so the first thing you hear is your own hand landing.
- Where it shines: Strike near a real bell. Yours takes its sound and goes out twice as far.
- Boss: Deep and Hollow bosses take Massive stance and lose their next move. Everything else takes a Medium hit to stance. The Deep can't stand it, and the rest just don't like it.
- Fair: Reach runs from 60 blocks at 50 bells up to 200 at 100. A setting keeps the silence from being total for anyone who finds it uncomfortable, and subtitles give the direction of an answering bell.
- Earned: Ring 15 of the Hundred. It comes from the bells themselves, and its reach grows with every one you ring after that.
- Reading: [Marta Selby rang the noon bell in Carillon for forty years and was late exactly once. She was on the stairs when the bells stopped. She's still on the stairs.]
- Levels: Carrying: The answering bell is louder, and you can tell how far away it is.; Held Note: Hold it and the silence lasts three seconds. Nothing caught in it can cast.; Second Stroke: Press again as the ring fades to send a second one.; Full Peal: Awakened. Three strokes, and every bell you've rung within reach sounds along with them.
- Engine: `Bells.count` gives your number. `Soundscape.muffle` already takes the world's sound down for dreams. A screen-bending pass already exists for power impacts (`FxScreen`).
- Unknown: How far out the ring still reads. Far Sight's distant land may hide it.

### The Long Cut (The horizon, Ultimate, on R, wave 2, about 5 hours)

Hold R. A hair-thin white line draws itself across the ground from your feet to as far as you can see, following the land over hills and across valleys. You can sweep it left and right. Let go and for half a second nothing happens. Then the land opens along that line, from your feet to the horizon, in one travelling wave. Anything on it is cut. Trees drop in. Over the next half minute it closes again, from the far end back toward you.

- Thrill: You changed the view. From a hilltop you can see the whole of what you just did, a straight line through everything for 200 blocks. The half second of nothing between letting go and the land opening is the best part.
- Only it: The longest reach any power has today is 48 blocks. This one reaches the horizon, and its effect is the landscape.
- Look: A cut three blocks wide and up to twenty deep. The inside walls glow like cooling steel and steam comes off any water that falls in. Near you the blocks are really gone. Far away it's drawn on the land as a dark seam with hot edges, which costs almost nothing.
- Sound: A rising rush of air that stops dead. Half a second of nothing. Then a tearing sound that starts at your feet and travels away from you, and the far end reaches you late, the way thunder does.
- Where it shines: Let go when the line has reached full length. It goes as far as you can see.
- Boss: One Massive hit and Massive stance, only if the line crosses it, inside the 20% cap. Worth aiming.
- Fair: It never touches anything anyone has built, any village, or any block that can't be put back. In a gate the floor holds and you get the line of damage with no canyon. Everything comes back on its own, even after a crash.
- Earned: Finish any Lord at rank B or above with an Energy power on your bar, and it hands you its Core.
- Reading: [Tomasz Ilyin was told to draw a line on the map to show where the evacuation would stop. He had a Halo gun and a bad sense of humour. Nobody crossed that line for nine years.]
- Levels: Steady Hand: A wider sweep while you hold.; Second Line: Press again to cross it with another.; Deep Cut: Creatures caught in it are stuck for two seconds.; Both Ways: Awakened. It goes out in front of you and behind you at once.
- Engine: `TerrainLedger` takes blocks out and puts them back, even after a crash, and refuses protected ground. Real blocks only within about 40 of you, spread over a second. `Soundscape.muffle` does the silence.
- Unknown: The frame cost of even that many block changes on your Mac. And whole trees: you told me nothing should float, so a cut tree has to go down in one piece.

### Borrowed Body (Someone else's body, Ultimate, on R, wave 3, about 8 hours)

Press R looking at a creature. Your body kneels where it stands with its eyes shut, and you're inside the creature. You walk on its legs at its height. Left click swings what it swings, and Z, X, C and V are its own moves. Press R at another creature and you jump across, and the one you left falls over.

- Thrill: You get to be the monster. A ten-block beast rampaging through its own friends, then you hop to the thing on the roof that was about to shoot you. Chaining hops through a whole room is the skill, with your real body waiting, helpless, somewhere behind you.
- Only it: Everything else changes what your character does. This changes who your character is. No other power hands you another creature's height, speed and moves.
- Look: A thin pink thread runs from your kneeling body to the creature's head, tighter and brighter the further you go. The camera drops or rises to its real eye height. The creature you're wearing has a pink light where its eyes are.
- Sound: Your own breath, small and far behind you, under the creature's heartbeat and all its real sounds right up close. When your body is hit you hear it from the wrong direction.
- Where it shines: Hop into the biggest thing there. When the one you're in dies, you keep the time you had left, plus two seconds.
- Boss: You can't get into a boss. Press R at one and you reach in for a moment: it drops its next move and stumbles a step.
- Fair: Twelve seconds at most. Your real body can be hit, and one hit pulls you back. You can't go further than 48 blocks from it. Never other players, and never anyone who lives at your Hearth. Kills count for half the mastery.
- Earned: Give 25 orders with Dominate. You've been telling creatures what to do for long enough that one day you go in yourself.
- Reading: [Ren Takeda looked after other people's dogs. He could tell you exactly what one was about to do. The Choir found out why, and then nobody could find Ren. Keep your own body somewhere safe.]
- Levels: Better Fit: Sixteen seconds.; Quick Hands: A hop costs no time at all.; Someone Watching: Your kneeling body gets a dome that takes the first hit.; Full Occupancy: Awakened. Whatever you were last inside keeps fighting for you for ten seconds after you leave.
- Engine: Handing the camera to another entity is already done for the opening, the Forge and the map (`setCameraEntity`). The moves of every boss-grade creature are data. Dominate already takes creatures over through `Allies`.
- Unknown: This is the riskiest of the eight. Driving a creature by hand instead of by its brain hasn't been tried here. It gets its own test, and if that's poor I'll say so and drop it.

### Last Time Round (Your own past, Ultimate, on R, wave 3, about 5 hours)

Press R. The last eight seconds of your fight are pulled out of you. A white figure with gold seams and no face stands up where you were eight seconds ago and does exactly what you did: the same steps, the same powers, at the same moments, at 60% strength. Meanwhile you're free to do something else.

- Thrill: You get to fight beside yourself, and you can plan for it. Set up a Well, throw a punch, press R, and spend the next eight seconds doing what your past self couldn't. When your old Well meets your new Nova, that's a Duet, and you played both halves.
- Only it: It's the only power whose effect is whatever you just did. Play well and it's devastating. Play badly and you get to watch yourself do it again. It can't be the same twice.
- Look: The same body as the Last Loop, at your size. Just before it stands, the path it's about to walk lights up from the far end as a thin gold line, exactly as the boss did it to you. Every power in its hands is white and gold instead of its family's colour, so you never mistake it for yourself.
- Sound: Your own sounds, half a step lower and with a long tail, arriving very slightly late. A low tick counts off the eight seconds.
- Where it shines: Use it at the end of your best eight seconds. The figure does exactly what you just did, so the better the eight seconds, the better the figure.
- Boss: Real damage and real stance at 60%, inside every cap. Bosses can't read it, because it isn't deciding anything.
- Fair: It repeats what your powers did. It never builds, never summons and never moves land. Only one at a time until level 5, and it can't record itself.
- Earned: Beat the A Proving. It kneels, lays its blade down and leaves you this. The Last Loop, at the very end, gives it its last step.
- Reading: [That was you. You got further than this once, and everything you've got, you brought back with you. There's no other name on this one.]
- Levels: Ten Seconds: Ten seconds of you instead of eight.; Step Back: Press R again while it's out and you swap places with it, wherever it's got to.; Word for Word: The figure hits at full strength.; Every Time Round: Awakened. Two figures: the last eight seconds, and the eight before.
- Engine: The Last Loop boss already keeps your path, a sample every two ticks. `EchoGhost` already walks a recorded path wearing your skin. Power hits run through one place (`Combat.hurt`) and effects through another (`Fx`), so the figure repeats the results and never has to re-run 63 powers' code.
- Unknown: Whether repeating results looks right when the target has moved. A test with four powers will show it.

## Ideas cut

- **Nobody Home**: In my first draft. For six seconds nothing near you remembers you're there. Cloak already does nearly this. It's a good trick, and it isn't a new kind of thing.
- **Second Try**: In my first draft. Go back to where you stood four seconds ago with the health you had then. Last Time Round says the same thing better, and its Step Back level gives you the best part. Rewinding health sits badly with fair damage.
- **Under**: Swim through solid ground and come up beneath someone. The game resets a player's collision every tick, and a camera inside stone is a rendering job of its own. Lovely, and too much risk for now.
- **Orbit**: Lock on to a boss and circle it like a moon, faster and faster, then fling off. A great feel, and better as a level 5 Way for Gravity Well than as its own power.
- **Two Doors**: Place two linked doors and send anything through. Without seeing through them it's half the idea, and a live view through a door is too much for your frame rate.
- **Down Is Over There**: Pick any wall and make it your floor. The game's physics knows up and down and nothing else. It would mean rebuilding how the player moves.
- **Eclipse**: Cover the sun and fight at midnight in the middle of the day. Looks wonderful, but underneath it's a colour grade and a damage buff. Better as Legion's Awakened look.
- **Thousand Cuts**: The world stops and you flash through everyone with camera cuts. Hundred Palms and Lightning Step already own this.
- **Comet**: Go up out of sight and come down as a meteor. The Ascent's Judgement Fall is this, and the rule is that the Ascent's moves stay the Ascent's.
- **Second Wind**: At zero health you get six seconds to earn it back. It muddies fair damage and the way dying already works in gates and dreams.

## Recommendations

1. Build the middle option: keep the ladder, add the systems underneath, and rework the weak powers one at a time. (If no: I'd build the small option instead: sounds, stance, dead buttons and the vanilla leftovers. Decent, but short of the bar you set.)
2. Drop Clean casts. No timing on most powers: each one gets a situation where it shines, which happens by itself. Only six keep a moment you can time, because that's what they are. (If no: Powers do the same thing wherever you use them. You'd still have the reworks, States and Duets.)
3. Let powers fill a boss's stance, under the same braced and opening rules as blades, with the numbers set so a blade stays the main way to break it. (If no: Blades stay the only way to break a boss, and casters keep feeling second best there.)
4. End every dead button: Dominate, Telekinesis, Echo Swap, Collapse and Gravity Flip all do something on a boss, and an aimed power with nothing to aim at does something smaller. (If no: Refusals stay as they are, except the seven that charge you for nothing, which I'd fix anyway.)
5. Add six States you can see on a creature, and 24 named Duets you find by trying things, recorded as N / ?? like the Weaves. (If no: Powers go on working side by side, and the seven existing reactions stay.)
6. Let Duets work between two players in a party. Your Well, their Nova. (If no: Duets only work with your own two powers.)
7. Let the R key fill from how you fight, with no energy cost and no timer, and give your first ultimate as the reward for the D Proving. (If no: R keeps its 60 second timer and stays empty until a Path or Rank C.)
8. Make the eight movement powers attacks that move you, and count every cast as a link in the Ascent's chain. (If no: The eight stay as they are, next to the Ascent moves that overlap them.)
9. Fold Wall, Ramp, Floor and Cone into one slot, Blueprint, keeping every level already earned. (If no: Architect keeps four of your six keys on building shapes.)
10. Build the showpieces: ground that moves, light that bends round a Well, shots you catch, a real door in a real wall, a gold ghost of the boss's next blow. (If no: Effects stay drawn on top of the world. Everything else in the plan still happens.)
11. No more synthetic dings: every sound is built from recordings and air, a check fails any file with a high ringing tone at its loud moment, and all 83 of today's that fail get remade. Give every power its own sound pack (about 340 new events in about 700 short files, roughly 10 MB), with a listening page per family where you pass or redo each one. (If no: Today's sounds stay, dings included. I'd still fix Gravity Well's silent three and a half seconds and split Haymaker from Railgun.)
12. Give every power a person: a Reading in the System's voice when you learn it, and one more line each time it levels. (If no: Descriptions stay as they are today.)
13. At level 5 of your starter, tell the player the last reading has their own name on it. "You picked this one last time too." (If no: Your starter's last reading is an ordinary one.)
14. Give Power Cores a source: each gate theme and Lord leans toward the families it has swallowed, so a player can go looking for one. (If no: Cores stay random by rank.)
15. At level 5 the System reads how you actually used the power and offers up to three ways to keep it. You pick one, and can change it later. (If no: Level 5 stays exactly as it is today, which you already like.)
16. Build new powers at all, in three waves with a stop after each so you can end it there. The last eight answers say which ones. (If no: Just the reworked 63. Kinetic, Velocity and Mind players get offered Overload, Legion or Gravity Flip for their first ultimate.)
17. Add a Powers tab to the K menu's Tasks page and a Choir's Record page to the Journal. (If no: Powers stay off the Tasks page, which breaks your standing rule, so I'd ask you to think again.)
18. Build a playable proof first (Haymaker, Shockwave, Gravity Well, Railgun, with stance and their sound packs) and stop for you to play it before anything else. (If no: I'd build straight through. It's faster, but it's much harder to stop cheaply if the reworks turn out to feel wrong.)
19. Make getting a power an event: hold to crack the Core, your first press is free, and a Core is never wasted. (If no: A Core keeps working as it does today: right-click and it's done. A Core of a mastered power stays useless.)
20. Three saved sets of six keys, and a power card that shows damage, where it shines, the Duets you've found and a Try button. (If no: One set of six keys, and the card stays as it is.)
21. A press kept for 0.15 seconds so a press just before a cooldown ends still fires, hold to aim for powers that land somewhere, and refusals said at your crosshair with a sound. (If no: A press made a moment early keeps getting thrown away, and you keep finding out where a big power lands by casting it.)
22. A small glint on a key when a Duet you've already found is on offer, and a big moment the first time you find one. (If no: You'd have to remember your Duets yourself. The list in the Journal is still there.)
23. Keep the level-up exactly as it is, and add a free Try It press, the next Reading line, and a thin line on the slot when the next level is close. (If no: Level-ups stay exactly as they are today.)
24. Remake the bar for fighting: a glint and a tick when a power comes back, a bright cooldown sweep, family colours on every edge, and two small arcs at the crosshair in a fight. (If no: Today's bar stays, and a power coming back stays silent and unmarked.)
25. Make the nine play styles clear in the game: on the starter choice, at the top of each family on the K page, and a Works With line on every card. You can still mix them however you like. (If no: Families stay a colour and a word on the card, and you work out what they're for by playing.)
26. Give three keys of one family a small Style bonus, so leaning in pays. With six keys you can hold two Styles at once. (If no: The play styles are still explained, but nothing extra happens for leaning into one. Duets stay the reward for mixing.)
27. Make every power easy enough to get. All the new ones can be had by about the middle of the game, nothing gets harder to find than it is today, and each has a second way in. (If no: The new powers stay where I first put them, some of them only at S rank or at the very end of the game.)
28. Give passives their own Always On tab on the K powers page, showing each one, what it's doing and its level, plus one row of icons on the Tab glance. Every passive shows and sounds from level 1, and you can always tell the hit came from you. (If no: Passives stay at the end of the power list, and you still can't tell when one has done something.)
29. Let you set each passive to Full, Quiet or Off on that tab, only when you're out of a fight. (If no: Passives stay on all the time, and the only way to quiet Aura is not to own it.)
30. Aura: a living mantle of fire round your real body, and a tongue of it goes out, bites and comes back. (If no: Aura keeps today's small flash and faint motes, with a sound and a line added so you can tell it was you.)
31. Danger Sense: the blow draws its path in the air before it lands, and a dodge you nearly missed pays the most. (If no: Danger Sense stays a red glow and a quiet ping, which is still useful.)
32. Wall Stride: your hand drags sparks along walls, your footprints burn, and the run turns red when only the power is holding you up. (If no: Wall Stride just lasts longer, with its look starting at level 1 instead of level 2.)
33. Water Run: a real wake that lasts, a hard stop that breaks a wave forward, and at level 5 you run up waterfalls. (If no: Water Run gets a wake of its own but no hard stop and no waterfalls.)
34. New passive, Second Shadow: your shadow acts on its own, and in the dark you go unlit and strike with it. (If no: Cloak stays the only stealth in the game, and it's a press.)
35. New passive, Stormborn: lightning finds you in a storm, fills your energy and finishes your hits. (If no: Storms stay weather, and Storm Call stays the only power that cares about them.)
36. Titan: you grow as big as the place allows, up to nine blocks, and pick things up. (If no: Kinetic players choose another ultimate at the D Proving.)
37. Stopped Clock: three seconds of frozen fight, and everything you did lands in one instant. (If no: Velocity players choose another ultimate at the D Proving, and Flow stays the top of the time powers.)
38. Road Ahead: run and a road of light lays itself down wherever you look. (If no: Architect still has no movement power, and the Ascent stays the only way to travel at speed.)
39. Say It Back: answer a boss's big move cleanly and for fifteen seconds it's yours. (If no: Mind keeps its five reworked powers, and a boss's moves stay the boss's.)
40. Toll: silence, then a bell you can see, and every sleeping bell in earshot answers. (If no: Bells stay out of your powers, which leaves the Deep's one stated weakness unused.)
41. The Long Cut: draw a line to the horizon, let go, and the land opens along it. (If no: Energy's biggest moment stays the Beam, and nothing reaches past 48 blocks.)
42. Borrowed Body: leave your body kneeling and walk around in a monster's. Only if its test comes out well. (If no: Dominate stays the top of that line, with its rework.)
43. Last Time Round: the last eight seconds of your fight stand up and do it all again beside you. (If no: The Last Loop's reward stays as it is, and the story's best idea stays out of your hands.)
