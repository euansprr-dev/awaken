# Bells you can see: a fire and a bell (plan, 30 Oct 2026)

Euan, 2026-10-30, playing a fresh world of `build/awaken-v1` at 748dfae3: "the nook bell just rang on its own, I think
when I got my first Kin? That's weird. We need a physical bell system early on. idk how but the bell tower needs to be
built earlier and actually make sense."

Later the same day, after reading the first version of this plan: "I don't think there should be that many bells ...
just around the world. I think they should mainly be found through gates or through different things ... through
puzzles inside lucid dreams. Having to find a bell out in the wild doesn't make sense. It doesn't even follow the
storyline well. Unless it's maybe deep underground or ... where you actually have a quest to follow ... I don't think
we should just randomly place a bell in the world ... that removes all of the mystery and the fun and the lore."
This version follows that: no bell lies in the open land without a story that leads to it (parts 3, 4.7, 4.8).

This is a plan, not built. Approval page: https://claude.ai/artifact/Fzj7qtDKBjVaonkgWCaeHa. SPEC changes (a new §43.16 and the edits listed in part 6)
go in with the first stage's commit.

---------------------------------------------------------------------------------------------------------------------

## 1. What rang, and why (read from the code at 748dfae3)

1. Setting Ilse's coal down founds the Hearthfire. Within 5 s `Bells.moments` (every 100 ticks per player) reaches
   `Bells.java:577`, `ring(p, "ember", ...)`.
2. That night `KinHome.lessonTick` (`KinHome.java:230`) sends the first fire's Lampwing. Three helps, it trusts you,
   follows you, and `KinHome.settle` (`KinHome.java:211`) puts it in `KinWorld`.
3. Within 5 s `Bells.java:578`: `if (!KinWorld.get(server).at(h.pos).isEmpty()) ring(p, "nook", ...)`.
4. `Bells.ring` plays the bell's note **at the player's own feet**: `Bells.java:91`,
   `p.level().playSound(null, p.blockPosition(), BellContent.note(d.no()), SoundSource.PLAYERS, 2F, 1F)`. Its subtitle
   is "Your tower's bell rings". The card says "Bell #12 · The Nook Bell. E-rank. It's gone home to your tower." whenever
   a home exists (`Bells.java:95`), tower or not.
5. Nothing physical exists. The first bell (the Ember, step 1) silently put a handbell and a Bell Frame item in the bag
   (`Bells.java:102-104`) with no line saying what the frame is for, and `BellTower.hang` filed both bells as hung at a
   home with no frame (`BellTower.java:95`).

So the bell Euan heard was the Nook Bell's note, played at him, by a rule that rings a bell for a state ("a beast lives
with you") rather than for anything in the world. The Ember Bell did the same thing a few minutes earlier.

Two more things of the same kind are waiting for him:
- **The bare fire sings.** At the first dawn or dusk with no frame set down, the song rings from the Hearthfire itself
  (`BellTower.java:297`, `: h.pos`): 20 s of two notes and 98 knocks out of a campfire.
- **Two different bell towers.** The bells module's tower is the frame, which grows a timber belfry by itself at 20
  bells and a stone tower at 50 (`BellTower.grow`). The Hearth's tier IV ("a bell tower and six rooms", B rank, about
  45 h) asks for something else: a *room* with a vanilla bell 8 up (`RoomType.BELL_TOWER`), which rings its own vanilla
  bell at dawn and dusk (`RoomEffects.bells`) and plays a vanilla bell at the fire to warn of breaks (`Breaks.java:119`).

Not the cause: the band's chime on a help (`KinWatch.chime`) is an amethyst chime at volume 0.5, and settling makes no
sound. Verified by reading; not reproduced in a running game (Euan is playing; the Mac stays light).

### The ding-ding that follows you, and the drum at the Hearth (Euan's second report, same day)

"Random ding-dings that just don't stop ... every few seconds ... no matter where I go, it follows me", and at the
Hearth "two or three bells that just went off, and now ... a drum beat ... as if something was happening".

- **Every bell sound is stereo.** All 20 `bell_*.ogg` files have two channels (checked in the file headers). The
  project's own audio rule is that only mono sounds are placed in the world (`tools/art/audio_build.py:104`, "OpenAL
  only places mono sources"), and `tools/art/specs/audio_bells.py` never asks for mono. So every bell sound plays with no
  direction and no fall-off: as loud 60 blocks away as next to it. That's "it follows me", and it's why a hum can't be
  followed by ear at all. Dream bells' "loud enough to follow" notes (`DreamBells.java:69`) have the same problem.
- **The ding-ding is the sleeping bell's hum.** `BellSites.java:266-272`: within 64 blocks of a sleeping bell you don't
  hold, an AMBIENT `bells.hum` at volume 4. `HUM_EVERY` is 160 ticks, but `BellSites.tick` only runs on each player's
  100-tick beat (`Bells.java:718-720`), so in practice it's every 10 s, on a clock, for as long as you're inside the
  circle. The sound is a 4 s swell of two partials a hair apart that beat against each other, pitched to the bell
  (`audio_bells.py`, `hum()`). The only line is the first one, "[A bell's note, faint, north-east. It's in your Bell
  Book.]", which says where but not what to do. Nothing else repeats like that: `fauna.chime_cricket` lives in caves
  only (`Habitat.java:123`), `kin.chime` plays only when you help a beast (`KinHelp.java:623`), `signal.chime` is the
  System card's own sound (`AudioUi.java:38`), the Chimewren's chime is its help tell, and `world.bell_far` is the
  Stillpoint's.
- **Why it stopped at home.** Each home's own sleeping bell, the Wren Bell, lies 90 to 160 blocks from the fire
  (`BellSites.java:217`), and the hum reaches 64. At the Hearth he's outside the circle. His two bells are most likely
  the Ember (the coal, `Bells.java:577`) and the Nook (the Lampwing, `:578`), both rung by rules, not the Wren: waking
  it plays a 7 s toll and repaints the land round it. The frame and the handbell arrived with the Ember, the first bell
  ever rung (`Bells.java:101-104`), not in the first gate. (Read from code; I haven't opened his save while he plays.)
- **The drum is the dusk song.** `BellTower` starts it at 11,600 (and dawn at 23,600, `BellTower.java:42`), and
  `client/SongPlayer.java:54-56` plays 100 steps 4 ticks apart: a held bell's note at its step, else `bells.gap`, "a dull
  knock" (a 110 to 70 Hz thump and a click, `audio_bells.py`, `gap()`), at volume 6. With two bells that's two notes
  in the first 2.5 s, then 98 knocks, five a second, for 20 s, and in stereo, so it's the same loudness everywhere he
  walks. No line ever says what the song is.

## 2. The brief, as I understand it

- **The job**: the hundred bells are the game's goal from the first hour. Euan wants them to be things: a bell you hold,
  hang, see and hear at your own home, so the Bell Book's 0 of 100 feels like something you are building.
- **The pain**: bells ring from nowhere for abstract states; the "tower" is an item you're never told to use, then a
  structure that builds itself at 20 bells; the tier IV bell tower is a second, unrelated thing two days of play later.
  The sounds can't be placed or followed, repeat on a clock, and never say what they are: a hum that follows you
  everywhere, a song that is mostly a bad drum, and a frame and handbell in your bag that nothing explains.
- **The desired experience**: from the first night, a real bell by your fire. Every bell after it is somewhere you can
  go and get it, and you hang it yourself. The song comes from the bells you can see. The tower is something you build
  early because you need it, and the story says why.
- **Constraints**: the Hearth asks, beasts do the boring work, you build. No daily tasks. Events rare. Never
  pre-announce bosses. Plain speech. Every item and block has a recipe or a way in. Multiplayer-safe. The band pages,
  the task card and the Hearth page layout belong to the tasks-one-list session.
- **Keep**: the hundred (numbers, names, ranks, voices), the song's tune and its times (dawn and dusk), leads and Track, the
  the beasts' reactions by count, the peals, the Foundry's casts, the Great Belfry, the
  Hundred Peal, the Deep's answer, the Wake, quiet ground.

**Strong result**: Euan sets the coal down, hangs Ilse's bell on a frame he made from planks and logs, and it rings
once, by his fire, for the first time in eleven years. That night the Lampwing comes out of a tree where it has been
sleeping in an old bell, and the bell is his once it moves in. At dusk he sees his two bells swing as they ring. In his
first gate he hears a bell, muffled, somewhere inside, follows it to a shut room, works out how to open it, and carries
the Wren Bell out: so that's where they went. When the Tenor is standing in his yard the next morning he builds a tower
to hang it in. **Weak**: the same flow with bells that look like vanilla bells and a tower he copies
from a plan. **Unacceptable**: any bell sound with no bell to see, a sound he can't follow or doesn't understand, a
sound on a clock, a bell that's "yours" but nowhere, a tower that builds itself, or a bell he can lose for good.

## 3. The rule, in two sentences

**Nobody left a bell lying in the open. Every bell is somewhere a story puts it (a gate swallowed it, a village hid it,
the Deep took it, a dream keeps it, a Lord hoards it) and you win it there and carry it home to hang; only the six that
people still ring stay where they hang, and answer your song.**

Why: it's the story Ilse and Bram already tell (§43.15). The bells went quiet eleven years ago and the gates came and
swallowed many of them, so **the gates are where most of them are**: 31 of the hundred come out of gates. The villages
that walked to the city hid theirs before they left, and their people in Carillon remember where: those five are
quests. The rest are in the Deep, in dreams, in the Veil, in the Above, with the Court's Sovereigns, or are made for
you. The six still ringing are Carillon's four and the two holdfasts', rung by people to this day.

What goes: the random sleeping places across the land (`BellSites`' 640-block cells: 13 land bells, the 5 villages'
and the Veil's 5) and each home's own buried Wren Bell. A bell is never just out there in a field. Part 4.8 has where
every one of the hundred comes from.

Why a tower: bells have always hung high so they carry. A small bell can hang on a frame by the fire, but everything
after the first three has to hang high: **8 blocks or more above your fire**, and a Great Bell 12 or more. That's the
tower, and you build it when your fourth bell comes home (around hours 3 to 5), not at 20 bells.

Ilse's line becomes true in the plainest way: "A fire keeps you warm. A bell keeps the ground shut. That's all a home
is, out there."

## 4. What changes

### 4.1 Ilse gives you her bell (the Ember Bell, #07)
- Her coal line (`FirstScenes.ILSE[12]`) becomes: "Here. A coal from this fire, and this. It's the little bell from over
  my fire at home. I've carried it eleven years and never once rung it. Put the coal down wherever you want to live,
  and hang the bell beside it. A fire keeps you warm. A bell keeps the ground shut. That's all a home is, out there."
  She presses both into your hand. A player who already has a fire (her short scene) gets the bell with her last lines.
- One line after it, so a bell in a gate means something before you ever hear one: "The gates took a lot of them. If
  you're ever in one and you hear a bell, low, like it's under something, find it and bring it out."
- The Book's Remembered lead (§43.15) becomes: "Ilse gave you her bell. Hang it by your own fire and it'll ring there."
- Setting the coal down: "[A fire and a bell make a home. Hang Ilse's bell by your fire on a Bell Frame: planks and
  logs.]" The frame's recipe unlocks when you first hold any bell.

### 4.2 The Bell Frame: planks and logs, three hooks, by your fire
- Recipe: planks and logs only (no vanilla bell, which needs bell bronze, so it couldn't be made in the first hour).
  It is no longer handed out. Its recipe unlocks the first time you hold a bell, with the coal line above.
- **Where**: anywhere inside your own Hearth (as now; outside it won't set: "[Set it inside your home, by your
  fire.]"). While you hold a frame, or hold a bell with no frame set down, a faint outline of a frame stands beside
  your fire where one fits (level ground, 3 to 5 blocks off, facing the fire), so the world shows you where it goes.
  Its tooltip says what it is: "Set it by your fire and hang bells on it. It has three hooks. Your bells ring from it
  at dawn and dusk."
- **What happens when you set it**: the beam drops into place with a wooden knock and the three hooks catch the light.
  If you're holding a bell: "[Now hang Ilse's bell on it. Use the bell on the frame.]" Quiet ground now centres on it
  (100, unchanged). When you hang the first bell it swings and rings, clear, and something far off answers it (4.7).
- It has three hooks. Use a bell on it to hang it: any bell but a Great Bell. Each hook shows its bell, or hangs bare.
- It's where your song starts: your beasts gather round it, and the Great Belfry's two pieces are set into it (§43.14,
  unchanged).

### 4.3 A bell is a thing
- One item, `awaken:bell`, its number in a data component; it places as one block, `awaken:hung_bell`, whose block
  entity keeps the number and who hung it. Its look shows its rank by size (a house bell at E, up to a Great Bell drawn
  about two blocks tall from its hook) and where it's from by its metal (part 4.9). Its number is cast into the lip.
- Bells don't stack, don't burn, don't despawn, stay in your bag when you die, and can't be blown up. If one is lost
  anyway (the void, `/kill`), it turns up by your frame at the next dawn: "[Your Mere Bell turned up by your frame this
  morning.]" A bell you already hold can't be had twice.
- **Hanging one**: use it on a free hook (the frame, a Bell Beam, a Great Beam) inside your own Hearth. It rings once on
  its own, its note and a slow swell, and the card says what it does: "Bell #12 · The Nook Bell. It's hanging by your
  fire. Its note is the thirteenth in your song. While it hangs, beasts warm to you sooner when you help them." The
  first bell you ever hang brings the Signal's line six seconds later (unchanged words).
- Sneak-use a hung bell to take it down (only whoever hung it, or the home's owner). Breaking it drops it. Its hole
  comes back in the song until it hangs again.
- **When a bell becomes yours** nothing plays at your feet. A carried bell is in your hands or set down where it was
  won; one of the six still rung by people rings where it hangs. The card says where it is: "Bell #58 · The Crypt Bell. It's yours. Take it
  home and hang it." The Book shows three states: a lead, yours (to hang), in your song.

### 4.4 The tower you build: hang them high
- **Bell Beam** (three logs and two iron nuggets): four hooks, any bell but a Great Bell. Its bells ring in your song
  only if it hangs **8 or more above your fire**, inside your Hearth (flat distance, so a tall tower counts). Lower down:
  "[A bell hung this low won't carry. Hang it higher: 8 blocks or more above your fire.]" and it stays in your hand.
- **Great Beam** (three logs, two chains and an iron ingot): one hook for a Great Bell, **12 or more** above your fire.
- Build it any shape you like. When the frame is full and a bell is waiting, the System says: "[Your frame's full. Hang
  the next bell high, on a Bell Beam 8 or more above your fire. Build up over the frame, or anywhere at home.]"
- **Or your Thatchbacks build one** (from D, beasts doing the boring work): two new Work Plan choices, **Belfry**
  (timber, 5 by 5, posts, a roof, four Bell Beams at 9 up: sixteen hooks) and **Bell Tower** (stone, 5 by 5, 15 high,
  six Bell Beams and two Great Beams). They're laid round your frame from the timber, stone and iron in the chests
  within eight, like every other work. Today's self-building belfry and tower at 20 and 50 bells are retired; their
  shapes become these plans.
- **The Hearth's "Bell tower" is your tower.** The old room's dawn and dusk vanilla bell goes (the song is the bells
  now). Its two gifts, a gate near home warned two play-days before it breaks and calm Deep Tide nights, come from your
  tower once a bell hangs 8 up, and the warning rings from your own bells. Tier II asks for "a bell tower: a bell
  hanging 8 or more above your fire" (moved from IV, so the Hearth asks for it at D, when you'll want it anyway); tier
  IV asks for "six rooms, and thirty bells in your song".

### 4.5 The song comes from the bells
- At dawn and dusk (times unchanged), each bell rings **from where it hangs, and swings** as it rings. Missing bells
  are silence, never a knock: the knock leaves the song for good. How it's played grows with your bells (4.10): one
  bell tolls, a few ring rounds, ten or more ring the tune. Nothing at all rings until a bell hangs: the bare fire never
  sings again.
- **The far six answer.** Each of the six still rung by people (Carillon's four, the holdfasts' two) that's in your
  song takes its turn from where it hangs, and at home you hear it from where it is: within 256 blocks from its real place, further off from its
  bearing, fainter the further it is. At the same moment a thin pulse of light leaves the top of your tower toward it.
  So your song tells you, by ear and by eye, where they are.
- The beasts take it as now, by how many bells are in your song (hung here and answering): under 20 they bolt for their
  nooks, 20 to 49 hunker, 50 to 79 listen, 80 to 99 gather at the frame, 100 sing. Wild beasts within 48 of your tower
  run from a song with gaps (unchanged).
- Voices, the beasts, the peals, the Hundred Peal and the Book count **your song**: bells hung here plus those of the
  far six that anyone living here has heard rung.

### 4.6 The Lampwing's old bell (the Nook Bell, #12)
- When the first fire's Lampwing comes, it comes out of a tree where it's been sleeping in an old bell: green with age,
  hung under a branch of the nearest tree within 24 of where it watches from (or on a dead post at the trees' edge if
  there's no tree). By day it sleeps in it, glowing faintly through the metal.
- While it roosts there, using the bell says: "[Something's asleep in it.]" Once it (or any beast) lives with you:
  "[The Lampwing's left its old bell in the tree. It's yours.]" You take it down and hang it.
- This is the moment Euan noticed, made into a thing you can see: your first kin brought a bell with it.

### 4.7 No bell lies in the open
- **Retired**: the land's sleeping places (`BellSites`: one per 640-block cell past the ridge, rank by distance, the Veil's
  on their own 960 grid) and every home's buried Wren Bell 90 to 160 out. Nothing is placed in open country for you to
  stumble on, and nothing in the open hums. Their machinery lives on where a story puts a bell: the bell room in a gate,
  a village's hiding place, a dream.
- **Gates hold the swallowed bells.** It's what Ilse says happened, so it's where you look. A gate whose rank has a
  swallowed bell you haven't found may hold one in a **bell room**: a room off the gate's main way (the gate rooms of
  §38 and gate depth), its door shut by something you have to work out (a lever that's somewhere else in the gate, a
  floor that has to be dug through, water to drain, or the gate's own creatures sitting round it). From the moment you
  step into the gate you can hear it, faint, its own note muffled, from wherever it lies (4.10). Nothing outside the gate
  says it's there: the Registry's board doesn't, the band doesn't. **Your first gate always holds one** (the Wren
  Bell), so the first hour teaches it; after that about one gate in four of a rank with bells still to find holds one,
  never two in a row. When you lift it, it rings once in your hands and the card says where it hung before: "Bell #17 ·
  The Wren Bell. It hung over the green at Wrenford until the gate took it. It's yours now. Take it home and hang it."
- **The villages hid theirs, and their people remember.** Five villages (Fenwick, the Mill, the Shepherds, the Hill, the
  Ford) hid their bells before they walked to the city, so the gates couldn't have them. Each is a quest that starts with
  someone in Carillon (the rumours they already tell, §43.13), goes to a real empty village chosen for it, where a letter
  on a table or a stone person's keepsake says exactly where ("We sank it off the end of the jetty. Mam cried."), and
  ends at the hiding place: sunk, buried under a floor, carried up the hill, or with something sitting on it. Only there,
  within 32 of the spot the letter names, do you hear it. Bring it home, and next time you pass the person who told you,
  they say so in their own words ("You found Fenwick's bell? My gran was married under that."). Never a task, never a
  reward screen.
- **Dreams keep theirs behind a puzzle.** Each of the six dream bells sits inside its dream's own small puzzle (the
  Lucidium designs them; a sketch each: the Falling Bell at the foot of a fall that only ends when you stop looking down;
  the Daydream Bell somewhere else each time you look away; the Lullaby Bell in a room where you mustn't wake what's
  asleep; the Nightmare's Bell carried by the thing that chases you; the Lucid Bell only there once you've noticed what's
  wrong with the dream; the Dreaming Bell, the first, simply waiting, so the first one teaches). You wake holding it.
- **The Veil's are kept by spirits.** The Spirit Bell comes from the first spirit you speak to, as now. Each of the
  other five is held by a spirit you can only meet in spirit; its words lead to what it stayed for, and it lets the
  bell go when that's done. It comes home a pale ghost bell that rings clear only to
  spirits and as a soft note to everyone else.
- **Quiet ground** (§43.15) is now round your frame, Carillon and the holdfasts; there are no woken places in the land.

### 4.8 Where every one of the hundred comes from (stages 4 to 6)
Nothing comes from a state any more, and nothing lies in the open. Each bell is given, won or found somewhere with a
story, and all but six come home:

| Bells (count) | Today | After |
|---|---|---|
| Ember 07 (1) | rings 5 s after the coal | Ilse gives it with the coal (4.1) |
| Nook 12 (1) | rings when a beast lives with you | the Lampwing's old roost in a tree by your fire (4.6) |
| Registry 03, Toll 30 (2) | ring for your 1st and 25th gate | Bram hands them over at the Registry; the System says he has it |
| Founders' 09, Founding 46 (2) | the Foundry's first cast; tier II | the Foundry casts them, the first with its first cast, the Founding Bell when your Hearth becomes a Steading; set down by the Crucible |
| The Provings' six Great Bells | ring with the rank | the Registry has kept them in its cellar for eleven years; its wagons bring yours to your yard by the next morning. Each needs a Great Beam |
| Swallowed by gates (14): Wren, Barrow, Mere, Wayside (E); Cairn, Spire, Reed, Lintel (D); Thorn, Crag, Fen (C); Thaw (B); Boulder, Leviathan's (A) | sleeping places in the land; Lintel for a Grand room | in a gate's bell room, heard from inside (4.7); the first gate always holds the Wren |
| Gate worlds' fifteen | ring at the Lord | in the Lord's hoard, beside the Lord's Heart |
| The villages' five: Fenwick's, Mill, Shepherd's, Hill, Ford | sleeping places, rumoured in Carillon | a quest from a Carillon rumour to an empty village to the hiding place (4.7) |
| The Descent's ten | ring on reaching a stratum | set in the stratum's threshold, half grown over, deep underground; Crystal 62 grown into the first Deepglass you mine |
| The Foundry's six sung bells | cast | cast and set down by the Crucible, one for the home (unchanged) |
| Dreams' six | carried out of a dream | behind each dream's puzzle (4.7) |
| The Veil's six | Spirit given; five sleeping in the land | kept by spirits (4.7); Spirit 88 from the first spirit, as now |
| The Above's six | ring at a spire, platform, the Halo, a storm, the pod | in the spire or the platform's ruin; aboard the Halo; the storm and the Cloudcalf's pod drop theirs to you |
| The Court's eight | ring at the Sovereign | where it set down what it carried (as the words already say) |
| Night 50, Siege 38 | ring when a raid is held | at first light, lying where the breach was |
| Stampede 11, Flood 87, Eclipse 54 | ring when witnessed | the herd leader's bell falls in its wake; the Deep Tide leaves one at your feet; the dark sky drops one |
| **Still rung by people (6)**: Roost, Square, Market, Harbour; Watch, Curfew | rung for you in Carillon; at a holdfast | stay where they hang and answer your song: Carillon's from the city, far off; the holdfasts' from their towers |
| The Seed 00 | cast in the Great Belfry | unchanged |

That's 31 through gates (the swallowed fourteen, the Lords' fifteen, Bram's two), 5 quests, 16 from the Deep and the
Foundry, 6 dreams, 6 spirits, 6 from the Above, 8 from the Court, 5 from the land's rare big days, 6 Great Bells, 4 from
home and people, 6 still ringing, and the Seed. The count stays a hundred: it's the number Ilse and Bram already say,
and now each has a reason to be where it is (decision 4).

A party: every member in reach gets their own bell, as today. A shared home hangs one of each; a second copy says
"[Your home's song already has this bell.]" and is yours to keep.

The Great Belfry (§43.14) keeps its rules and its look. When it rises, your beasts carry your Great Bells up into it and
the Seed is cast in its middle; the rest stay where you hung them. "Only a Great Belfry holds all hundred" becomes "Only
a Great Belfry can lead the Hundred Peal": every bell in your song, at home and the six far off, rings at once.

### 4.9 How they look and sound (the last stage, Euan judges)
- A style sheet first (the art pipeline: `tools/art/specs/bells.py`, preview, studio tour). Six sizes by rank. Metals by
  family: the land's bronze gone green, the Deep's dark iron with an ember glow in the lip, the Above's pale silver, the
  dream bells' pearl, the Veil's cold glass, the Court's black and gold, the Great Bells huge, with a band of old words.
  The frame, the beams and the hooks in the same hand. A swallowed bell in a gate is dulled by the Deep, and comes
  clean the day it hangs.
- Sounds (`tools/art/specs/audio_bells.py`): the hang (iron on iron, then the bell), the first ring's swell, far
  answers (the same notes, darker and further), the muffled hum (4.10). Nobody will have heard them until Euan plays.

### 4.10 Sounds you can follow (stage 1, first, on its own)
The rule: **every bell sound comes from a bell you can find, from where it is, and you can tell what it means.**

- **Placed, not stereo.** Every bell sound that plays in the world is rendered mono (`Snd(..., mono=True)` in
  `audio_bells.py`, the project's own rule), so it comes from its direction, gets louder as you get near, and fades with
  distance. That includes the dream bells. Only the Book's "Play your song", which plays at you on purpose, stays as is.
- **Nothing hums in the open.** A bell you haven't found is heard only where a story has already brought you: inside a
  gate that holds one, within 32 of the spot a village's letter names, and in a dream. Out in the land, nothing.
- **What a lost bell sounds like.** Its own note, muffled, as if something brushed it through stone: a soft damped
  strike, then a low hum that swells and fades over about four seconds. It's the note it rings when it hangs, so when you
  lift it, the note you followed comes out clear.
- **How often, and from where.** No clock. Inside a gate (or in a quest's last stretch):
  - one hum as you step in (or reach the spot), so you know at once there's a bell here;
  - then one each time you're 8 blocks closer than at its last hum, at most every 10 s, so walking toward it, it comes
    louder and more often, like hot and cold;
  - standing still or walking away, one every 45 s, so it doesn't vanish while you fight;
  - none once it's yours.
- **Learning what a hum means, with nobody explaining.** Ilse says it once (4.1). In your first gate you hear it as you
  step in, and the first time ever: "[That low note is a bell. The gate swallowed it, and it's in here somewhere. Follow
  the sound.]" Bram's handbell answers you there (4.11). At its room the line says what stands between you and it, by
  kind: "[It's under the floor. Dig.]", "[It's behind that door. Something in here opens it.]", "[It's under the water.
  Drain it or dive.]", "[Something's sitting on it. You'll have to deal with that first.]". After the first, each gate
  that holds one says only: "[There's a bell in this gate.]"
- **Lifting one: a moment you know you caused.** You take it from where it lies. The muffled note you followed rings
  out clear at the same pitch, in your hands; the room's dust lifts off it; the gate goes quiet for a breath, as if it
  noticed. If your home is within 256 (a quest bell), your own hung bells answer once each, faint, from home's
  direction. Then the card, once, plain: "Bell #17 · The Wren Bell. It hung over the green at Wrenford until the gate
  took it. It's yours now. Take it home and hang it. 3 of 100." Nothing at your feet.
- **The home song with few bells, growing to a hundred.** Silence, never knocks. By how many bells are in your song:
  - **One**: it tolls three times, slowly, three seconds apart, each stroke left to ring out: a village's one bell
    calling people home. About 9 s.
  - **Two to nine**: rounds, the way bell-ringers ring them: each bell once, highest to lowest, three times over, about
    0.6 s apart, then the deepest once more, alone. 6 to 20 s.
  - **Ten or more**: the tune. The song's hundred-step tune plays with the notes you have; missing notes are rests, and
    a run of rests shortens to one beat so the tune never stalls. It's sparse at ten, you can hum it by sixty, and at a
    hundred the whole thing plays. It ends with all your hung bells struck together once. 10 to 25 s.
  - The six still-ringing bells take their turn from where they hang, far off, with a pulse of light from your tower
    toward them. Anyone within 256 hears your song from your tower, quieter with distance; inside your Hearth it's full.
- **Knowing what just happened when it rings.** Every bell sound has something to see: a hung bell swings as it rings,
  a far bell's answer comes with a pulse of light toward it, a lost bell in a gate answers your handbell with a thread of
  grey light through the walls. The first of each, once ever, gets one plain line: the first song ("[Your bells,
  ringing for dusk. They ring at dawn and dusk, and every bell you hang joins in.]"), the first far answer ("[Hear that?
  Carillon's Roost bell, answering yours from the city.]"). Subtitles say what each is: "Your bells ring for dusk",
  "A lost bell hums", "A bell answers your song", "A bell is hung", "Your handbell rings". The Bells tab keeps "Last
  song: dusk, 3 bells" and the next one.

### 4.11 The handbell: every hunter carries one
- **What it is, said when you get it**: Bram gives it to you at the Registry before your first gate: "Take this. Every
  hunter carries one. If a gate's holding a bell, ring this inside and it'll answer you." A spare is a stick over two
  copper ingots. Its tooltip says the same, plus "Sneak and use it to choose a peal" once you have one.
- **Ringing it** (plain ring, 10 s apart): inside a gate, a bell held there answers clearly from where it lies, with a
  thread of grey light rising through the walls and floors over it for six seconds, so you can see which way. At a
  quest's spot, the hidden bell does the same. Nothing to answer: "[Nothing answered. There's no bell in here.]" Out in
  the land it still scares wild beasts outside your Hearth and calls Chimewrens, as now.
- **Peals**: unchanged, and the System says so the first time three bells of a set make one: "[You can ring a peal on
  your handbell now. Sneak and use it to choose.]"
- Bell #03, the Registry Handbell, is shown as **the Registry Bell** so nobody mixes it up with this one. It hangs like
  any other.

## 5. A first evening, before and after (same world, same moves)

| What Euan does | Today | After |
|---|---|---|
| Registers with Bram | (nothing about bells he can hold) | Bram gives him a handbell: "Every hunter carries one. If a gate's holding a bell, ring this inside and it'll answer you." |
| Takes Ilse's coal | the Book opens at 0 of 100 | she gives him the coal and her bell, and says the gates took a lot of them; the Book opens at 0 of 100, the bell "yours, to hang" |
| Sets the coal down | 5 s later a bell note at his feet, "It's gone home to your tower"; a handbell and a frame appear in his bag | "[A fire and a bell make a home. Hang Ilse's bell by your fire on a Bell Frame: planks and logs.]" |
| Makes and sets the frame, hangs the bell | (the frame does nothing he can see) | an outline by the fire shows where; the bell rings once, by the fire; 1 of 100; the Signal's line |
| Walks the land | a hum every 10 s within 64 blocks of any sleeping bell, the same loudness wherever he stands | nothing hums out here; the land is quiet |
| The first night, the Lampwing | it comes out of the trees | it comes out of an old bell hanging in a tree, glowing through it |
| It trusts him and settles | the Nook Bell's note at his feet, from nowhere | "[The Lampwing's left its old bell in the tree. It's yours.]" He takes it down and hangs it: 2 of 100 |
| The first dusk | 2 notes, then 98 knocks, five a second, for 20 s, from his campfire (if no frame) or the frame, equally loud wherever he walks | his two bells ring rounds from the frame, swinging, about 8 s: "[Your bells, ringing for dusk. They ring at dawn and dusk, and every bell you hang joins in.]"; the Lampwing ducks into its nook |
| Steps into his first gate | (nothing) | a low muffled note from somewhere inside: "[That low note is a bell. The gate swallowed it, and it's in here somewhere. Follow the sound.]" It comes louder as he closes in; his handbell makes it answer with a thread of grey light through the walls |
| Finds its room | (nothing) | "[It's behind that door. Something in here opens it.]" He finds the lever, takes the bell, and it rings clear in his hands: "The Wren Bell. It hung over the green at Wrenford until the gate took it ... 3 of 100." |
| Back at the Registry | the Registry Handbell at his feet when the gate clears | Bram gives him the Registry Bell for his first gate, and hears about the Wren: "So that's where they went." 4 of 100: his frame's full, and the next one needs a Bell Beam |
| The Tenor (D) | a note at his feet | the Registry's wagon leaves it in his yard by morning: "[It needs a Great Beam, 12 or more above your fire.]" He builds his first tower, or sets a Bell Tower plan for his Thatchbacks |

A failure case: he loses the Nook Bell in lava. Today there's nothing to lose. After: it can't burn; if it's gone
anyway it's by his frame at dawn. A missing case: no tree near the fire for the Lampwing's bell: it hangs on a dead post
at the trees' edge.

## 6. Data, saves and old worlds

- `bell_book` schema 4: per bell, `state` (lead, yours, in song) beside today's fields; carried bells held but not hung
  are "yours". `bells` world data schema 3: each home's hung bells by place (`BlockPos` → number, who hung it), its
  frame, and the far six in its song; `hung` becomes derived. `awaken:hung_bell` block entity schema 1.
- **Old saves** (Euan's world included): every carried bell a player holds that isn't hanging anywhere comes to them as
  an item the first time they're home, with "[Your bells are waiting by your frame to be hung.]" (into the bag, the rest
  in a chest by the frame). The far six go straight into the song. A frame already set down keeps its place and
  takes three. A belfry or tower that grew by itself stays, and its bell spots become Bell Beams that count wherever they
  are. Nobody loses a bell, a hook or a block. Euan's world: the Ember and the Nook come to him as two bells, and since
  he already holds a frame and a handbell, the first time he's home after the update: "[You've got a Bell Frame and a
  handbell. Set the frame by your fire and hang your bells on it. Ring the handbell inside a gate, and if there's a bell
  in there it'll answer you.]"
- **Sleeping places already built in an old world** (the grey patches round a bell, Euan's included): at a player's
  next visit the bell is gone from them and the land there looks like land again; the bell joins the gates' pool (or
  its village's quest). A bell already woken from one stays yours and comes to you as an item to hang; its place stops
  counting as quiet ground. Sleeping places planned but not yet built are dropped. Leads to them in the Book become "[It
  wasn't there after all. The gates may have it.]" and close.
- SPEC: a new §43.16 with these rules; edits to §43.6 (tier II and IV needs), §43.7 (moments, the frame, the song, the
  tower stages, the sleeping places retired), §43.13 (the land's bells, the villages', the Veil's), §43.14 (the Great
  Belfry's line), §43.15 (Ilse's coal line, the lead, quiet ground without woken places), §38 (a gate's bell room,
  agreed with the gates module's owner); JOURNEY parts 1 and 4 (bells no longer asleep in the open); LEDGER rows for the bell, the
  hung bell, the frame's recipe, the Bell Beam, the Great Beam, the two plans and the roost bell, before code.

## 7. Failure register (the ones that matter)

| Case | What happens |
|---|---|
| Bell lost (void, `/kill`, despawn) | can't burn or despawn; if removed anyway, by the frame at the next dawn |
| Death | bells stay in the bag |
| Duplicate (party, shared home) | one per number per player; one per number per home's song; the spare is a keepsake |
| Hung below 8 / outside your Hearth / in someone else's | refused with a line, stays in hand |
| Frame broken | its bells drop as items, the holes come back |
| No frame yet, bells waiting | no song; the Tasks page and the frame's recipe say what to do |
| A Great Bell and no Great Beam | it waits in the yard; the line says what it needs |
| No tree for the roost bell | a dead post at the trees' edge |
| Lesson Lampwing never befriended | the bell is free once any beast lives with you |
| A far bell's place unloaded at song time | its note still plays from its bearing (the song packet carries places) |
| A member leaves a shared home | bells stay hung; whoever hung them can take them down |
| Old save | part 6 |
| The first gate can't fit a bell room | the bell lies in the gate's last room instead, still heard from the door |
| A gate with a bell closes or breaks before you find it | the bell goes back to the pool; a later gate of that rank holds it |
| A party in a gate with a bell | everyone in it hears it; each who doesn't have it gets their own when one lifts it, as today |
| All of a rank's swallowed bells found | that rank's gates hold none; nothing says so |
| A quest's village is gone (built over, a city) | the next empty village of the same kind along the rumour's bearing is chosen instead |
| Handbell rung with nothing to answer | "[Nothing answered. There's no bell in here.]" |
| Song while you're in a gate or the Deep | nothing reaches you there (as today, overworld only) |
| Restart mid-song, Peaceful, other dimensions | as today (overworld homes only) |

## 8. Tests, looks, stages

- **Tests** (CI, one per mechanism): `bells_nothing_from_nowhere` (no bell sound at a player when a bell becomes theirs;
  the bare fire never sings; a carried bell is an item, a still-ringing one stays), `bells_none_in_the_open` (no sleeping
  place is planned or built in the land; an old world's built ones are cleared on the next visit), `bells_gate_holds`
  (the first gate holds the Wren in a reachable room; a later gate of a rank with bells to find holds one at the set
  odds; the hum plays only inside), `bells_village_quest` (the rumour, the letter, the hum within 32 of the spot, the
  bell), `bells_hang_rules` (three on the frame,
  not a Great Bell; a beam 8 up takes four, 7 up refuses; a Great Beam 12 up; taking down brings the hole back),
  `bells_song_from_bells` (each hung bell's place and each far bell's place in the song; voices count the song),
  `bells_roost_bell`, `bells_old_save`, `bells_catalog_ring` extended (each of the hundred yields an item or is one of
  the six still ringing), `journey_ledger` (every new item and block has a way in). For the sounds: `bells_sounds_placed`
  (every bell sound played in the world is mono, read from the files as `AudioLayerTests` already does),
  `bells_hum_cadence` (in a gate with a bell: one hum on entry, then none for 45 s standing still; 8 closer: the next
  after 10 s; none for a bell you hold; none in the open land), `bells_song_arrangement` (1 bell: three
  strokes; 4: three rounds; 30: the tune with rests shortened; never `bells.gap`; never over 25 s),
  `bells_handbell_answers` (a gate's bell answers with its thread; "nothing answered" in an empty gate).
- **One look tour per stage** on CI (`tools/autopilot/bells-home-<n>.txt`): the frame with Ilse's bell at dusk, the
  roost bell in a tree at night with the Lampwing, a gate's bell room before and after its door opens, the grey thread
  through a gate's walls, a hand-built tower's beams with empty hooks, the Tenor in the yard, both Work Plans built.

| Stage | What | Rough size |
|---|---|---|
| 1 | **Quiet land and sounds you can follow** (4.7's retirement, 4.10, 4.11): no sleeping place planned or built in the land and old ones cleared (the ding-ding stops), every bell sound mono, the song with silence (toll, rounds, tune), nothing at your feet, a true card ("It'll hang by your fire once you set a Bell Frame there"), plain lines and subtitles, the frame and handbell explained to old saves | 2 to 3 h |
| 2 | The bell as a thing, the frame (recipe, outline, three hooks), hanging and the first ring, the song from the bells, Ilse's bell and lines, the roost bell, the still-ringing six answer, the Bells tab's data, old saves | 3 to 4 h |
| 3 | Bell Beam and Great Beam, the 8 and 12 rules, the Belfry and Bell Tower plans, the Hearth's bell tower folded in, tier II and IV needs, the Tenor by wagon | 2 to 3 h |
| 4 | **Bells in gates**: the bell room (with the gates module), the first gate's Wren, the swallowed fourteen's odds, the hum and the handbell inside, Bram's handbell and lines, the Lords' hoards | 4 to 5 h |
| 5 | **Quests and puzzles**: the five villages' quests (rumour, letter, hiding place, the person's reaction), the six dream puzzles (with the Lucidium), the Veil's five spirits, and every other source as a place (4.8) | 5 to 7 h |
| 6 | Looks and sounds: style sheet, bells by rank and metal, the frame and beams, the bell room, the pulse and the grey thread, the new sounds | 2 to 3 h, then Euan's eye |

Each stage merges on green CI and a reviewed tour. A stage can stop without breaking the last: stage 1 changes only how
bells sound, what the lines say and that nothing new is placed in the land, so it can go to Euan's play copy on its own
the same day; until stage 4 lands, each of the swallowed fourteen is yours when you clear a gate of its rank (a
stand-in, with no sound at your feet), and the villages' five wait for stage 5 (their rumours pause); stage 2 works
with today's self-building belfry still in place. Stage 1's sounds are checked by spectrogram
and level only; nobody hears them before Euan.

What could change the size: drawing 70 bells' block entities at once at a Great Belfry (if it costs, bells far from
the camera bake to a still model); the 26.3 item model chosen by a data component (to verify in `.ref`); the Work Plan
builder laying hanging blocks after the roof they hang from.

## 9. What bells need from the band pages (the tasks-one-list session owns them)

Bells will register their lines through `core.api.Shown` and goals through `Destinations.set(p, target, goal)` with
`doneWhen`, so each line goes the moment it's done:
- **Tasks**: "Hang Ilse's bell by your fire" (with "a Bell Frame: planks and logs"); "A bell to hang: the Nook Bell"
  (grouped as "3 bells to hang" past one; Track points at your frame when you're away); "Your frame's full: hang the
  next bell on a Bell Beam, 8 or more above your fire"; "The Tenor is in your yard: it needs a Great Beam, 12 or more
  above your fire"; "The Lampwing's old bell is in the tree"; "There's a bell in this gate" (while you're in one that
  holds one); a village quest's step ("Someone in Carillon remembers Fenwick's bell, by the lake to the west", then the
  letter's words); bell leads as today. Goals `bell/frame`, `bell/hang/<n>`, `bell/beam`, `bell/great_beam`,
  `bell/roost`, `bell/gate`, `bell/quest/<id>`.
- **Hearth page**, a "Your bells" part, from a new `BellHooks.home(player)`: bells in your song (hung here and the far six)
  of 100, bells waiting to be hung, free hooks, the next song, how your beasts take it now, voices on with their counts,
  and the Great Belfry pin (today's). Tier II and IV's need lines come from `TierRules` as now.
- **Journal**: Ilse's bell, the Lampwing's bell, the first bell found in a gate, each bell and where it came from, each
  village's bell brought home and what its person said, the first far bell that answers at dusk; once each.
- **The top-right card**: Track to your frame for a bell waiting to be hung; leads as today.
- **The Tasks page's Bells tab** (theirs to lay out), from one call, `BellHooks.tab(player)`, top to bottom:
  1. **Next**: the one most useful thing now, in this order: "Make a Bell Frame (planks and logs) and set it by your
     fire. Then hang Ilse's bell on it."; "Hang the Nook Bell: use it on your frame."; "Your frame's full. Build a Bell
     Beam (three logs, two iron nuggets) 8 or more above your fire."; "The Tenor is in your yard. It needs a Great Beam,
     12 or more above your fire."; in a gate that holds one, "There's a bell in this gate. Follow the sound, or ring
     your handbell."; a village quest's next step ("Fenwick's bell: they sank it off the end of the jetty."); else the
     nearest lead. Never "go and look for bells" with nothing behind it. Each with Track.
  2. **Your song**: "3 of 100". The bells hung at home, by hook (frame 2 of 3, beams), and the far six that answer once
     you've heard them, each with its direction. "Next: dusk, in about 4 minutes. Last: dawn, 3 bells." The Book's "Play your song".
  3. **To hang**: bells in your bag or yard, each with where it can go (frame, Bell Beam, Great Beam).
  4. **Leads**: a village's quest by its step, a dream or spirit's words, a holdfast heard far off; "a bell" until you
     know its name; how you know; direction and rough distance where there is one; Track.
  5. **Your handbell**: what it does, in one line, and the peals you can ring now.
  No timers, nothing daily; every line goes the moment it's done.

## 10. Decisions for Euan

1. Ilse gives you her bell with the coal, and one line says the gates took a lot of them (two lines of her scene
   change). **Recommend yes.**
2. The Nook Bell is the Lampwing's old roost in a tree by your fire: it comes with the story of your first beast, not
   lying in the open. **Recommend yes.**
3. No bell lies in the open: the land's random sleeping places and each home's Wren go. Every bell is given, won or
   found somewhere a story puts it, and all but the six people still ring (Carillon's four, the holdfasts' two) come
   home to hang. **Recommend yes.**
4. Where they come from (4.8): 31 through gates (the swallowed fourteen in bell rooms, your first gate always holding
   the Wren; the Lords' fifteen; Bram's two), the villages' five as quests from Carillon, the dreams' six behind
   puzzles, the Veil's six from spirits, the Deep's sixteen, the Above's six, the Court's eight, the Great Bells by the
   Registry's wagon. The count stays a hundred, because that's the number Ilse and Bram already say. If you'd rather
   have fewer, say how many and I'll cut from the gates' fourteen first. **Recommend this list, and a hundred.**
5. Bells hang high (8 above your fire, Great Bells 12) on beams you place in a tower you build, or your Thatchbacks
   build a standard one; the self-building belfry and tower at 20 and 50 go. **Recommend yes.**
6. Bram gives every hunter a handbell before the first gate; inside a gate it makes a held bell answer with a thread of
   light through the walls (a spare is a stick over two copper ingots). **Recommend yes.**
7. First, on its own, straight to your play copy: stage 1 stops anything being placed in the open land (the ding-ding
   ends), makes every bell sound placed, plays the song with silence instead of knocks, and says things plainly.
   **Recommend yes.**
8. The home song grows with your bells: one bell tolls, two to nine ring rounds, ten or more ring the tune, and missing
   bells are silence. **Recommend yes.**
