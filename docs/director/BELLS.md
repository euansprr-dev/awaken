# Bells you can see: a fire and a bell (plan, 30 Oct 2026)

Euan, 2026-10-30, playing a fresh world of `build/awaken-v1` at 748dfae3: "the nook bell just rang on its own, I think
when I got my first Kin? That's weird. We need a physical bell system early on. idk how but the bell tower needs to be
built earlier and actually make sense."

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
  sleeping bells' wake ceremony, the beasts' reactions by count, the peals, the Foundry's casts, the Great Belfry, the
  Hundred Peal, the Deep's answer, the Wake, quiet ground.

**Strong result**: Euan sets the coal down, hangs Ilse's bell on a frame he made from planks and logs, and it rings
once, by his fire, for the first time in eleven years. Far off over the hill something answers it, muffled, and he
walks toward the sound until it's under his feet. That night the Lampwing comes out of a tree where it has been
sleeping in an old bell, and the bell is his once it moves in. At dusk he sees his two bells swing and hears the Wren
Bell, which he woke on the hill, answer from the north-east. When the Tenor is standing in his yard the next morning
he builds a tower to hang it in. **Weak**: the same flow with bells that look like vanilla bells and a tower he copies
from a plan. **Unacceptable**: any bell sound with no bell to see, a sound he can't follow or doesn't understand, a
sound on a clock, a bell that's "yours" but nowhere, a tower that builds itself, or a bell he can lose for good.

## 3. The rule, in two sentences

**A bell that still has its place, you wake where it is, and from then on it answers your song from there. A bell that
lost its place, you bring home in your hands and hang in your tower.**

Why two kinds: a woken great bell is the land's. It keeps the ground shut round it (quiet ground, §43.15), the land
turns back round it, and any player can come and ring it, so it has to stay. Everything else (bells swallowed by gates,
lost in the Deep, fallen from the Above, carried into dreams, made for the Provings, the home bells) has nowhere to go
back to, so it comes home with you. That's 29 bells that stay and 71 that come home (part 5 has every one).

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
- One line after it, so the hum means something before you ever hear it: "And if you're out there and you hear a low
  note that seems to come up out of the ground, that's a bell, asleep. Walk toward it till it's loud. You'll find it."
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
  won; a standing bell rings where it stands. The card says where it is: "Bell #58 · The Crypt Bell. It's yours. Take it
  home and hang it." The Book shows three states: asleep (a lead), yours (to hang), in your song.

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
- **Standing bells answer.** At its step, each standing bell in your song rings where it stands (swinging, if anyone is
  near it), and at home you hear it from where it is: within 256 blocks from its real place, further off from its
  bearing, fainter the further it is. At the same moment a thin pulse of light leaves the top of your tower toward it.
  So your song tells you, by ear and by eye, where your bells are.
- The beasts take it as now, by how many bells are in your song (hung here and answering): under 20 they bolt for their
  nooks, 20 to 49 hunker, 50 to 79 listen, 80 to 99 gather at the frame, 100 sing. Wild beasts within 48 of your tower
  run from a song with gaps (unchanged).
- Voices, the beasts, the peals, the Hundred Peal and the Book count **your song**: bells hung here plus standing bells
  that anyone living here has rung.

### 4.6 The Lampwing's old bell (the Nook Bell, #12)
- When the first fire's Lampwing comes, it comes out of a tree where it's been sleeping in an old bell: green with age,
  hung under a branch of the nearest tree within 24 of where it watches from (or on a dead post at the trees' edge if
  there's no tree). By day it sleeps in it, glowing faintly through the metal.
- While it roosts there, using the bell says: "[Something's asleep in it.]" Once it (or any beast) lives with you:
  "[The Lampwing's left its old bell in the tree. It's yours.]" You take it down and hang it.
- This is the moment Euan noticed, made into a thing you can see: your first kin brought a bell with it.

### 4.7 Sleeping bells: wake them, and they answer
- Unchanged: Heard and Seen, crossing bearings, keepers, digging, the toll, the Fault mend, the land turning back, the
  clapper that comes loose, the Chimewrens hearing it, quiet ground round it.
- Changed: the hum (4.10), the strike (4.10), and the card: "[You woke it. It'll answer your bells from here every dawn
  and dusk, and the ground round it stays shut.]" Nothing rings at your feet. Everyone within 48 who doesn't hold it
  gets it in their song, as now, and anyone can come later and strike it.
- **The Wren Bell teaches the hum.** It's each home's own, buried 90 to 160 out. The moment you hang your first bell,
  the Wren answers it: one muffled note from where it lies, carried 320 blocks so it's clear at home, and a thin thread
  of grey light rises from the spot for ten seconds, tall enough to see over the trees. "[Something answered your bell,
  from over there. It sounded like a bell, asleep in the ground. Go and find it.]" The Book gets a lead with that
  bearing, and the Bells tab's next line points at it. On the way its hum gets louder and comes more often the closer
  you get (4.10), and at 24 the ground goes grey. When you wake it, it's the first to answer your song: that dusk its
  note comes in over the hill.

### 4.8 Every other source becomes a place (stage 3)
Nothing comes from a state any more. Each carried bell is found, given or set down somewhere:

| Bells | Today | Physical form |
|---|---|---|
| Ember 07 | rings 5 s after the coal | Ilse gives it with the coal (4.1) |
| Nook 12 | rings when a beast lives with you | the Lampwing's old roost in the tree (4.6) |
| Lintel 44 | rings for a Grand room | hangs over the door of the nearest empty settlement's hall; Seen when you pass within 24, and a stone person's letter can lead to it; else buried near home like the Wren |
| Founding 46 | rings at tier II | when your Hearth grows to a Steading, its new ground turns up an old bell that hums: the older home that stood here. Dig it up |
| Registry 03, Toll 30 | ring for your 1st and 25th gate | Bram hands them over at the Registry (you're back there for the D Proving at about 4 h); the System says he has it |
| Provings' six (Tenor, Second, Third, Fourth, Fifth, Bourdon) | ring with the rank | at the next dawn the Great Bell is standing in your yard (JOURNEY's Tenor). It needs a Great Beam |
| Gate worlds' sixteen | ring at the Lord | in the Lord's hoard, beside the Lord's Heart |
| The Court's eight | ring at the Sovereign | where it set down what it carried (as the words already say) |
| The Descent's ten | ring on reaching a stratum | set in the stratum's threshold, half grown over; you take it as you arrive |
| Crystal 62 | rings when Deepglass is in your bag | grown into the first Deepglass you mine |
| The Above's six | ring at a spire, platform, the Halo, a storm, the pod | in the spire or the platform's ruin; aboard the Halo; the storm and the Cloudcalf's pod drop theirs to you |
| Dreams' six | carried out of a dream | unchanged: you wake holding it |
| Night 50, Siege 38 | ring when a raid is held | at first light, lying where the breach was |
| Stampede 11, Flood 87, Eclipse 54 | ring when witnessed | the herd leader's bell falls in its wake; the Deep Tide leaves one at your feet; the dark sky drops one |
| Foundry six, Founders' 09 | rung for every member | cast and set down by the Crucible, one for the home |
| The Seed 00 | cast in the Great Belfry | unchanged |
| Spirit 88 | given by the first spirit | a ghost bell the spirit gives you; it hangs pale, and rings clear only to spirits |

The 29 **standing** bells: the land's thirteen sleeping places (Wren, Barrow, Mere, Wayside, Cairn, Spire, Reed,
Thorn, Crag, Fen, Thaw, Boulder, Leviathan), the five homes people left (Fenwick's, Mill, Shepherd's, Hill, Ford), the
holdfasts' two (Watch, Curfew), Carillon's four (Roost, Square, Market, Harbour) and the Veil's five (Ghost Lantern,
Whisper, Wake, Mourner's, Veil). They're rung as today, and answer your song from where they are. Carillon's answer
from the city, far off, at dusk.

A party: every member in reach gets their own carried bell, as today. A shared home hangs one of each; a second copy
says "[Your home's song already has this bell.]" and is yours to keep.

The Great Belfry (§43.14) keeps its rules and its look. When it rises, your beasts carry your Great Bells up into it and
the Seed is cast in its middle; the rest stay where you hung them. "Only a Great Belfry holds all hundred" becomes "Only
a Great Belfry can lead the Hundred Peal": every bell in your song, at home and across the land, rings at once.

### 4.9 How they look and sound (stage 4, Euan judges)
- A style sheet first (the art pipeline: `tools/art/specs/bells.py`, preview, studio tour). Six sizes by rank. Metals by
  family: the land's bronze gone green, the Deep's dark iron with an ember glow in the lip, the Above's pale silver, the
  dream bells' pearl, the Veil's cold glass, the Court's black and gold, the Great Bells huge, with a band of old words.
  The frame, the beams and the hooks in the same hand. Asleep bells keep today's grey-green.
- Sounds (`tools/art/specs/audio_bells.py`): the hang (iron on iron, then the bell), the first ring's swell, far
  answers (the same notes, darker and further), the muffled hum (4.10). Nobody will have heard them until Euan plays.

### 4.10 Sounds you can follow (stage 1, first, on its own)
The rule: **every bell sound comes from a bell you can find, from where it is, and you can tell what it means.**

- **Placed, not stereo.** Every bell sound that plays in the world is rendered mono (`Snd(..., mono=True)` in
  `audio_bells.py`, the project's own rule), so it comes from its direction, gets louder as you get near, and fades with
  distance. That includes the dream bells. Only the Book's "Play your song", which plays at you on purpose, stays as is.
- **A sleeping bell's hum: what it sounds like.** The bell's own note, muffled, as if something brushed it under the
  ground: a soft damped strike, then a low hum that swells and fades over about four seconds. It's the same note the bell
  rings when it wakes, so when you strike it, the note you followed comes out clear.
- **How often, and from where.** No clock. Within 64 blocks of the nearest sleeping bell you don't hold:
  - one hum the moment you come within 64 (again only after you've been away five minutes);
  - then one each time you're 12 blocks closer than at its last hum, at most every 15 s, so walking toward it, it comes
    louder and more often, like hot and cold;
  - standing still or walking away, one every 90 s, so it doesn't vanish while you look round;
  - only the nearest sleeping bell hums for you at a time; none inside your own Hearth; none once it's yours or awake.
- **Learning what a hum means, with nobody explaining.** Ilse says it once (4.1); the Wren answers your first bell and
  you walk to it with the hum growing (4.7); at 24 the place looks asleep and the line says what to do by its kind:
  buried "[It's under your feet. Dig down and strike it.]", sunk "[It's under the water here. Dive for it.]", high
  "[It's up there somewhere. Climb.]", kept "[Something's keeping it. You'll have to deal with that first.]". If a hum
  ever reaches you before the Wren has answered (you went exploring first), the first one says it outright: "[That low
  note is a bell, asleep somewhere near. Walk toward it till it's loud.]" After that each new bell's Heard line is
  plain: "[A bell, asleep, somewhere to the north-east. Walk toward the sound.]"
- **Waking one: a moment you know you caused.** You strike it. The muffled note you followed rings out clear at the same
  pitch, and a beat later the great toll rolls out (today's). The grey lifts off the ground in a ring that sweeps out
  from the bell over five seconds, and birds go up. If your home is within 256, your own hung bells answer once each,
  faint, from home's direction: they heard it. Then the card, once, plain: "Bell #17 · The Wren Bell. You woke it. It'll
  answer your bells from here every dawn and dusk, and the ground round it stays shut. 3 of 100." Nothing at your feet.
  Lifting a carried bell from where it lay rings it once in your hands, its own note, small.
- **The home song with few bells, growing to a hundred.** Silence, never knocks. By how many bells are in your song:
  - **One**: it tolls three times, slowly, three seconds apart, each stroke left to ring out: a village's one bell
    calling people home. About 9 s.
  - **Two to nine**: rounds, the way bell-ringers ring them: each bell once, highest to lowest, three times over, about
    0.6 s apart, then the deepest once more, alone. 6 to 20 s. Standing bells take their turn from where they stand.
  - **Ten or more**: the tune. The song's hundred-step tune plays with the notes you have; missing notes are rests, and
    a run of rests shortens to one beat so the tune never stalls. It's sparse at ten, you can hum it by sixty, and at a
    hundred the whole thing plays. It ends with all your hung bells struck together once. 10 to 25 s.
  - Anyone within 256 hears it from your tower, quieter with distance; inside your Hearth it's full.
- **Knowing what just happened when it rings.** Every bell sound has something to see: a hung bell swings as it rings,
  a standing bell's answer comes with a pulse of light from your tower toward it, a sleeping bell's answer with its grey
  thread of light. The first of each, once ever, gets one plain line: the first song ("[Your bells, ringing for dusk.
  They ring at dawn and dusk, and every bell you hang joins in.]"), the first standing bell to answer ("[Hear that? The
  Wren Bell, answering your bells from where you woke it.]"). Subtitles say what each is: "Your bells ring for dusk",
  "A sleeping bell hums", "A bell answers your song", "A bell wakes", "A bell is hung", "Your handbell rings". The
  Bells tab keeps "Last song: dusk, 3 bells" and the next one.

### 4.11 The handbell: ask the sleeping bells to answer
- **What it is, said when you get it**: you find it at the Wren Bell when you wake it, lying in the grass where someone
  last rang it: "[Someone left a handbell here, the last time anyone rang this bell. Ring it anywhere out in the land and
  any bell asleep near you will answer.]" A spare is a stick over two copper ingots. Its tooltip says the same, plus
  "Sneak and use it to choose a peal" once you have one.
- **Ringing it** (plain ring, 10 s apart): every sleeping bell within 160 answers in turn, nearest first, each from
  where it lies, muffled, with its grey thread of light for six seconds, and each gives you a Heard lead with that
  bearing. None near: "[Nothing answered. No bell sleeps within earshot of here.]" It still scares wild beasts outside
  your Hearth and calls Chimewrens, as now.
- **Peals**: unchanged, and the System says so the first time three bells of a set make one: "[You can ring a peal on
  your handbell now. Sneak and use it to choose.]"
- Bell #03, the Registry Handbell, is shown as **the Registry Bell** so nobody mixes it up with this one. It hangs like
  any other.

## 5. A first evening, before and after (same world, same moves)

| What Euan does | Today | After |
|---|---|---|
| Takes Ilse's coal | the Book opens at 0 of 100 | she gives him the coal and her bell; the Book opens at 0 of 100, the bell "yours, to hang" |
| Sets the coal down | 5 s later a bell note at his feet, "It's gone home to your tower"; a handbell and a frame appear in his bag | "[A fire and a bell make a home. Hang Ilse's bell by your fire on a Bell Frame: planks and logs.]" |
| Makes and sets the frame, hangs the bell | (the frame does nothing he can see) | an outline by the fire shows where; the bell rings once, by the fire; 1 of 100; the Signal's line |
| A moment later | (nothing) | something answers from over the hill, muffled, with a thread of grey light: "[Something answered your bell ... Go and find it.]" |
| Walks out toward it | a hum every 10 s inside 64 blocks, the same loudness wherever he stands | the hum comes from the bell, louder and more often as he gets closer; at 24 the ground goes grey: "[It's under your feet. Dig down and strike it.]" |
| Strikes it | a toll, and a bell note at his feet | the note he followed rings clear, the toll rolls out, the grey sweeps away; his bells at home answer; a handbell lies in the grass: "[... Ring it anywhere out in the land and any bell asleep near you will answer.]" |
| The first night, the Lampwing | it comes out of the trees | it comes out of an old bell hanging in a tree, glowing through it |
| It trusts him and settles | the Nook Bell's note at his feet, from nowhere | "[The Lampwing's left its old bell in the tree. It's yours.]" He takes it down and hangs it: 2 of 100 |
| The first dusk | 2 notes, then 98 knocks, five a second, for 20 s, from his campfire (if no frame) or the frame, equally loud wherever he walks | his bells ring rounds from the frame, swinging, about 8 s, and the Wren answers from the north-east with a pulse of light from his frame toward it; "[Your bells, ringing for dusk ...]"; the Lampwing ducks into its nook |
| Clears his first gate | the Registry Handbell at his feet | "[Bram has the Registry's bell for you.]"; he picks it up at the D Proving: 3 hooks full |
| The Tenor (D) | a note at his feet | it's standing in his yard at dawn. "[It needs a Great Beam, 12 or more above your fire.]" He builds his first tower, or sets a Bell Tower plan for his Thatchbacks |

A failure case: he loses the Nook Bell in lava. Today there's nothing to lose. After: it can't burn; if it's gone
anyway it's by his frame at dawn. A missing case: no tree near the fire for the Lampwing's bell: it hangs on a dead post
at the trees' edge.

## 6. Data, saves and old worlds

- `bell_book` schema 4: per bell, `state` (lead, yours, in song) beside today's fields; carried bells held but not hung
  are "yours". `bells` world data schema 3: each home's hung bells by place (`BlockPos` → number, who hung it), its
  frame, and its standing bells; `hung` becomes derived. `awaken:hung_bell` block entity schema 1.
- **Old saves** (Euan's world included): every carried bell a player holds that isn't hanging anywhere comes to them as
  an item the first time they're home, with "[Your bells are waiting by your frame to be hung.]" (into the bag, the rest
  in a chest by the frame). Standing bells go straight into the song. A frame already set down keeps its place and
  takes three. A belfry or tower that grew by itself stays, and its bell spots become Bell Beams that count wherever they
  are. Nobody loses a bell, a hook or a block. Euan's world: the Ember and the Nook come to him as two bells, and since
  he already holds a frame and a handbell, the first time he's home after the update: "[You've got a Bell Frame and a
  handbell. Set the frame by your fire and hang your bells on it. Ring the handbell out in the land and any bell asleep
  near you will answer.]" If the Wren still sleeps, it answers his first hung bell as in 4.7.
- SPEC: a new §43.16 with these rules; edits to §43.6 (tier II and IV needs), §43.7 (moments, the frame, the song, the
  tower stages), §43.14 (the Great Belfry's line), §43.15 (Ilse's coal line and the lead); LEDGER rows for the bell, the
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
| A standing bell's place unloaded at song time | its note still plays from its bearing (the song packet carries places) |
| A member leaves a shared home | bells stay hung; whoever hung them can take them down |
| Old save | part 6 |
| Two sleeping bells in reach | only the nearest hums; the other starts once the first is yours or you're nearer to it |
| Wren unbuildable (city, water, another home) | today it's GONE; then the next sleeping bell within 320 answers the first hang instead, and if there's none, the first line still says what a hum means when one comes |
| Handbell rung with nothing in 160 | "[Nothing answered ...]", no lead |
| Several players near one sleeping bell | each hears its hum on their own cadence (the packet is per player, as today) |
| Song while you're in a gate or the Deep | nothing reaches you there (as today, overworld only) |
| Restart mid-song, Peaceful, other dimensions | as today (overworld homes only) |

## 8. Tests, looks, stages

- **Tests** (CI, one per mechanism): `bells_nothing_from_nowhere` (no bell sound at a player when a bell becomes theirs;
  the bare fire never sings; a carried bell is an item, a standing one stays), `bells_hang_rules` (three on the frame,
  not a Great Bell; a beam 8 up takes four, 7 up refuses; a Great Beam 12 up; taking down brings the hole back),
  `bells_song_from_bells` (each hung bell's place and each standing bell's place in the song; voices count the song),
  `bells_roost_bell`, `bells_old_save`, `bells_catalog_ring` extended (each of the hundred yields an item, a placed bell
  or a woken place), `journey_ledger` (every new item and block has a way in). For the sounds: `bells_sounds_placed`
  (every bell sound played in the world is mono, read from the files as `AudioLayerTests` already does),
  `bells_hum_cadence` (standing still within 64: one hum, then none for 90 s; 12 closer: the next after 15 s; none
  inside your Hearth; none for a bell you hold; only the nearest hums), `bells_song_arrangement` (1 bell: three
  strokes; 4: three rounds; 30: the tune with rests shortened; never `bells.gap`; never over 25 s),
  `bells_handbell_answers` (sleeping bells within 160 answer nearest first and each gives a Heard lead).
- **One look tour per stage** on CI (`tools/autopilot/bells-home-<n>.txt`): the frame with Ilse's bell at dusk, the
  roost bell in a tree at night with the Lampwing, a hand-built tower's beams with empty hooks, the Tenor in the yard,
  both Work Plans built, the pulse toward an answering bell.

| Stage | What | Rough size |
|---|---|---|
| 1 | **Sounds you can follow** (4.10, 4.11): every bell sound mono, the muffled hum and its cadence, the song with silence (toll, rounds, tune), the strike, the handbell's answers (still given with the first bell until stage 2), the plain lines and subtitles, nothing at your feet, a true card ("It'll hang by your fire once you set a Bell Frame there"), the frame and handbell explained to old saves | 2 to 3 h |
| 2 | The bell as a thing, the frame (recipe, outline, three hooks), hanging, the first ring and the Wren's answer, the song from the bells, Ilse's bell and line, the roost bell, standing bells answer, the Bells tab's data, old saves | 3 to 4 h |
| 3 | Bell Beam and Great Beam, the 8 and 12 rules, the Belfry and Bell Tower plans, the Hearth's bell tower folded in, tier II and IV needs, the Tenor in the yard | 2 to 3 h |
| 4 | Every other source becomes a place (4.8), the Great Belfry's line | 3 to 4 h |
| 5 | Looks and sounds: style sheet, bells by rank and metal, the frame and beams, the pulse and the grey thread, the new sounds | 2 to 3 h, then Euan's eye |

Each stage merges on green CI and a reviewed tour. A stage can stop without breaking the last: stage 1 changes only how
bells sound and what the lines say, on today's systems, so it can go to Euan's play copy on its own the same day;
stage 2 works with today's self-building belfry still in place; stage 4's sources fall back to today's rings until each
is moved. Stage 1's sounds are checked by spectrogram and level only; nobody hears them before Euan.

What could change the size: drawing 70 bells' block entities at once at a Great Belfry (if it costs, bells far from
the camera bake to a still model); the 26.3 item model chosen by a data component (to verify in `.ref`); the Work Plan
builder laying hanging blocks after the roof they hang from.

## 9. What bells need from the band pages (the tasks-one-list session owns them)

Bells will register their lines through `core.api.Shown` and goals through `Destinations.set(p, target, goal)` with
`doneWhen`, so each line goes the moment it's done:
- **Tasks**: "Hang Ilse's bell by your fire" (with "a Bell Frame: planks and logs"); "A bell to hang: the Nook Bell"
  (grouped as "3 bells to hang" past one; Track points at your frame when you're away); "Your frame's full: hang the
  next bell on a Bell Beam, 8 or more above your fire"; "The Tenor is in your yard: it needs a Great Beam, 12 or more
  above your fire"; "The Lampwing's old bell is in the tree"; bell leads as today. Goals `bell/frame`, `bell/hang/<n>`,
  `bell/beam`, `bell/great_beam`, `bell/roost`.
- **Hearth page**, a "Your bells" part, from a new `BellHooks.home(player)`: bells in your song (hung here and answering)
  of 100, bells waiting to be hung, free hooks, the next song, how your beasts take it now, voices on with their counts,
  and the Great Belfry pin (today's). Tier II and IV's need lines come from `TierRules` as now.
- **Journal**: Ilse's bell, the Lampwing's bell, each bell woken (where), the first bell that answers at dusk; once each.
- **The top-right card**: Track to your frame for a bell waiting to be hung; leads as today.
- **The Tasks page's Bells tab** (theirs to lay out), from one call, `BellHooks.tab(player)`, top to bottom:
  1. **Next**: the one most useful thing now, in this order: "Make a Bell Frame (planks and logs) and set it by your
     fire. Then hang Ilse's bell on it."; "Hang the Nook Bell: use it on your frame."; "Your frame's full. Build a Bell
     Beam (three logs, two iron nuggets) 8 or more above your fire."; "The Tenor is in your yard. It needs a Great Beam,
     12 or more above your fire."; "Something answered your bell from the north-east, about 120 blocks out. Follow the
     sound."; else "Ring your handbell out in the land. Any bell asleep near you will answer." Each with Track.
  2. **Your song**: "3 of 100". The bells hung at home, by hook (frame 2 of 3, beams), and the standing bells that
     answer, each with its direction. "Next: dusk, in about 4 minutes. Last: dawn, 3 bells." The Book's "Play your song".
  3. **To hang**: bells in your bag or yard, each with where it can go (frame, Bell Beam, Great Beam).
  4. **Leads**: nearest first; "a bell" until you know its name; how you know (heard, seen, answered your handbell,
     told); direction and rough distance; Track.
  5. **Your handbell**: what it does, in one line, and the peals you can ring now.
  No timers, nothing daily; every line goes the moment it's done.

## 10. Decisions for Euan

1. Ilse gives you her bell with the coal (one line of her scene changes). **Recommend yes.**
2. The Nook Bell is the Lampwing's old roost in a tree by your fire. **Recommend yes.**
3. Two kinds: standing bells stay and answer your song; the rest come home in your hands. The other choice is that
   every bell comes home (one rule, but a woken great bell leaves the land it just woke, and two players can't both
   have it). **Recommend two kinds.**
4. Bells hang high (8 above your fire, Great Bells 12) on beams you place in a tower you build, or your Thatchbacks
   build a standard one; the self-building belfry and tower at 20 and 50 go. **Recommend yes.**
5. The re-sourced bells in 4.8 (Lintel to an empty settlement's hall door, Founding from your home's new ground, the
   Registry's two from Bram, the Great Bells in your yard, Spirit as a ghost bell). **Recommend yes.**
6. The handbell is found at the Wren Bell when you wake it (a spare is a stick over two copper ingots), instead of
   appearing with the first bell, and ringing it makes sleeping bells within 160 answer. **Recommend yes.**
7. Sounds first: stage 1 (every bell sound placed, the hum on approach instead of a clock, the song with silence
   instead of knocks, plain lines) goes on its own, straight to your play copy, ahead of the rest. It replaces the small
   same-day fix offered before. **Recommend yes.**
8. The home song grows with your bells: one bell tolls, two to nine ring rounds, ten or more ring the tune, and missing
   bells are silence. **Recommend yes.**
9. The Wren Bell answers the first bell you hang, and Ilse says one line about the hum, so you learn to follow a hum
   without anyone telling you. **Recommend yes.**
