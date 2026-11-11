# Awa and Dubu at the Hearth: the plan

Status: **waiting for Euan's approval**. Page (private, it shows his photos): https://claude.ai/artifact/PFB5JdXQaZDCV8MLrmvdYN,
published 11 Nov 2026. Nothing below is built into play.
Branch `claude/hearth-pets-plan-472c8d`. What exists on it is the preview only: the two bodies, their coats and poses
(`tools/art/specs/hearth_family.py`), two entity types that only `/awaken admin family` can place, and the look tour
`tools/autopilot/family.txt`. Nothing spawns them and nothing changes in play.

Euan's words (11 Nov 2026): "when you place your hearth for the first time, they should wander up to the house and be one
of the kins, but they don't really do anything. They just kind of walk around and like they're part of the environment
there... if they can come like join you on adventures too and follow you around in a very like natural and awesome way...
And you can also just make them stay... And also make them immortal. It should create like a render of these... Before,
doing anything more, so like I can actually approve."

The photos are private and never enter this repo. The page that shows them is a private artifact.

## 1. What he is asking for

- **The job:** his real cat and dog live at his home in the game. They are company, not workers.
- **The desired experience:** they turn up by themselves at the first Hearth, they are simply around, they can come
  along on a trip and it feels like a real cat and a real dog are with you, they can be told to stay, and he never has to
  worry about them.
- **Hard constraints:** immortal; they do no work; staying is always possible; pictures before anything is built.
- **Rules from earlier decisions that bind this:** nothing fights for you (SPEC §43.4); kin do only boring work, and
  these two do none; taming is discovered, and these two are a gift, not a taming; motion at the Fresh Animations bar;
  plain words; answered through the world, not a screen; safe for a server with many players.
- **A strong result:** he looks at Awa in the game and says "that's her". She loafs where the real one loafs. Dubu meets
  him at the yard's edge with his giraffe. Out in the wild they keep up without ever popping in beside him, and a fight
  never involves them.
- **A weak result:** two vanilla-looking animals with their colours, standing about.
- **An unacceptable result:** one of them can be lost, killed, left behind, used in a fight, or gets in the way of one.

## 2. What the code has today (checked on build/awaken-v1 at 69fee63de)

| Thing | What it does now | Consequence | Keep / extend / new |
|---|---|---|---|
| `kin.KinKind`, `KinBeast` | Sixteen kinds, one entity type each, a synced act that picks a clip | Adding two kinds would give them rumours, wants, Kinship steps and jobs | Keep as is; **do not** add them as kinds |
| `KinWorld.homes`, `KinHome.NOOKS` (2, 4, 8, 12, 18, 24) | Who lives at each Hearth, counted against nooks | A pet in this list would take a nook | New, separate record |
| `KinGoals`, trail token `beasts:N`, the Nook bell (`bells`) | Count `KinWorld.at(anchor)` | They would ring the Nook bell and tick beast goals unearned | Not counted |
| `KinHome.lessonTick` | Once, on a night at a new home, a Lampwing comes to the first fire | A proven pattern for "it comes by itself, once" | Reuse the pattern |
| `KinBeast.hurtServer` | A befriended beast is left at one heart and hides | Still dies to `/kill`, is gone if its entity is lost, goes wild if the Hearth goes | Stronger rule needed |
| `KinBeast.FollowGoal` | Follows you home once, gives up past 48 blocks | No keeping up with rope, wings, boats | New following |
| `hearth.Hearth` | `home(p)`, `atHome(p)`, owner and members; Hearthfire radius 24, Anchor 48 to 96 | The home's area and household are already defined | Reuse |
| `Embers.found`, `HearthService.onAnchorPlaced` | The two moments a first home begins | Where arrival hooks | Reuse |
| `powers.Combat.isAlly`, `ArmsServer.foe`, `aim.client.Lock.foe` | An `OwnableEntity` owned by a player is never hurt by powers or sweeps; lock-on takes only hostiles | Owned pets are already safe from the player's own attacks | Reuse: they implement `OwnableEntity` |
| Kin plate (SPEC §43.17) | Names a pet "yours", has the naming card | Their plate and renaming come for free | Reuse |
| Hero rigs, life layer (`mobs.client.life`), `tools/art/specs` | Our own bodies with breath, blinks, springy ears and tails | The same family of art as the sixteen kin | Reuse |
| EMF and Fresh Animations | Animate only vanilla model layers, only where the pack is installed | FA motion is free only on vanilla bodies | See section 3 |
| The Far Landing (branch `claude/far-landing-ae982f`, not merged) | `TheLull.landing(server)`: the landing, or null on old worlds | Where they come from on a new world | Read it if present |

Not found anywhere: a creature that cannot be lost; catching up with fast movement; a place on the Hearth page for
anyone who is not a working beast.

## 3. Their bodies: two directions, one recommendation

- **A. The game's own cat and wolf bodies, wearing their coats.** Fresh Animations would move them for free in Euan's
  pack. But the vanilla cat's face is 5 by 4 pixels, so Awa's blaze, wedge and black cheek cannot be drawn and she reads
  as any tuxedo cat; and Dubu is a small, long, low dog with drop ears and a plume, while the wolf is tall, prick-eared
  and straight-tailed. Their own poses (the loaf, belly up, chin on paws, the giraffe) have no FA clip, and FA's files
  are not ours to extend. Without the pack they would move like vanilla.
- **B. Bodies of their own on our hero rigs (recommended).** About twice the pixel density of a vanilla cat, so the
  markings fit; their real proportions; any pose from the photos; the life layer on every install. The cost is that all
  motion is ours to make, to the FA bar, and that is the larger part of the build.
- A third (the vanilla layer on our own entity) has A's look and B's work, so it is not offered.

The page shows A and B side by side at the same real size.

Numbers of the drawn bodies:

| | Awa | Dubu |
|---|---|---|
| Rig | `family_awa`, 13 bones, texture 128 x 64 | `family_dubu`, 15 bones, texture 128 x 64 |
| Drawn scale | 0.45 (36 texels a block) | 0.5 (32 texels a block) |
| Height at the back | 0.39 blocks | 0.38 blocks |
| Nose to rump | 0.68 blocks | 0.9 blocks |
| Hitbox | 0.45 x 0.5 | 0.5 x 0.55 |
| Clips drawn | idle, move, run, sit, loaf, sleep, roll, nudge, stretch, hide | idle, move, carry, run, sit, lie, sleep, nudge, hide |

## 4. How it sits with the kin

They are family, not kin you tamed. They are their own small type beside the kin (`dev.awaken.kin.family`), not two
more `KinKind`s. So:

- They take no nook and have no job, no want, no rumour and no Kinship ladder.
- They do not count for `beasts:N`, the Nook bell, or any beast step on Tasks.
- They are on the Hearth page under Your beasts, first, as two cards: name, where they are, what they are doing.
- They wear the kin plate: "Awa" and "yours", then what she is doing.
- The Hundred Peal, the Kinsong, the Gnawed and raids' thefts never touch them. In a raid they hide with the beasts
  and come out at dawn.

## 5. Behaviour

### 5.1 Arriving

- **When:** the first time a Hearth exists for a household (an Ember's Hearthfire or an Anchor), once per Hearth. Half a
  minute after the fire is lit, by day or night, never during a raid or a Fault.
- **How:** Dubu first. He runs in from the way you came (the landing's mooring on a Far Landing world, else the bearing
  of the Last Fire, else from behind you), from 24 to 40 blocks off and out of sight, straight to you, and greets you.
  Awa comes ten to fifteen seconds behind at a walk, stops a few blocks off, sits and looks, then comes over and rubs
  your shin.
- **Words** (the System's bracket lines, once): "[That's Dubu. He must have followed you the whole way.]" then
  "[And Awa, taking her time. They live here now.]"
- **A world that already has a Hearth** (Euan's): the same scene the next time its owner is home and outdoors.
- **If no path can be found:** each is placed on open ground 12 to 20 blocks off, out of your sight, and walks the rest.
- **A Hearth that moves:** they come to the new one the same way. With no Hearth at all they stay with its owner.

### 5.2 At home

They do nothing useful. A day is made of small things, each picked from what is really at the home:

| | Awa | Dubu |
|---|---|---|
| Rests | The loaf, on something raised and in the light: a sill, a slab, a stair, a barrel, your bed | Chin on his paws near where you are |
| Sleeps | Curled, on a bed, a carpet or by the fire; at the foot of your bed when you sleep | On his side by the fire, his giraffe under his chin |
| In the sun | Rolls belly up on warm ground | Naps in it |
| When you work | Sits near the bench you are using and watches | Lies at your feet |
| When you come home | Finishes what she is doing, then finds you, tail up | Runs out to the yard's edge to meet you, with his giraffe |
| When you leave | Watches from where she is | Walks you to the yard's edge and sits |
| The bells' song | Ears back, tail flicking | Howls along |
| Each other | She ignores his play-bow, or swats the air | Play-bows at her; they end up by the same fire |

- They keep inside the Hearth's radius. They never block: they step off a block you aim to use, they do not sit on a
  chest's lid or a bench's face, and you cannot be pushed by them.
- Petting (use): she purrs and leans in; he wags and wriggles. No hearts, no food, no breeding, no leash.

### 5.3 Coming with you, or staying

Three states each, shown on the plate: **At home**, **With you**, **Waiting here**.

- **Sneak and use** on one at home: it comes with you. Its plate says "Coming with you".
- **Sneak and use** on one that is with you: at home it stays home; away from home it waits where it is.
- **Use** always pets, in any state.
- One that is waiting goes home by itself if you go more than 64 blocks off, are gone five minutes, or leave the
  dimension. It is at home when you next get there.
- Coming home with one ends the trip: inside the Hearth it goes back to its own day.
- The first time you walk out of the yard after they arrive, Dubu follows to the edge and sits, and the System says once:
  "[Dubu wants to come. Crouch and call him and he will.]" That is the whole lesson.

### 5.4 Out with you

- **Dubu** ranges. He trots a few blocks ahead, stops and looks back until you catch up, sniffs at things, heels close
  when you sneak, and runs flat out beside you when you sprint. He swims.
- **Awa** trails. A few blocks behind or to one side, tail up, along walls and fences where there are any. She will not
  swim: she goes round, or catches up.
- **When you outrun them** (rope, wings, a mount, a boat with no free seat, Homeward): they are never left in unloaded
  land and never appear beside you. Each is placed out of your sight 12 to 20 blocks back along the way you came and runs
  in. In a boat with a free seat, one hops in. On the skiff's deck they fly with you.
- **Camp:** when you sleep away from home they sleep by your bed.

### 5.5 Danger

- Nothing can take either as a target, hurt them, or be distracted by them. They never attack, never block a foe, and
  never warn of anything you cannot see.
- When a fight starts within 16 blocks of you, each backs off 10 to 14 blocks, to cover or height if there is any, and
  watches in its wary pose. They come back a few seconds after it ends: he runs in circles, she strolls.
- Your own swings and powers pass through them (they are yours, as tamed pets already are).

### 5.6 Where they will not go

Gates, dreams, the Nether and the End. One that is with you sits at the threshold when you cross, waits, and goes home
if you are gone five minutes or come back somewhere else.

### 5.7 Immortal, in practice

| Case | What happens |
|---|---|
| Any damage: mobs, falls, fire, lava, drowning, cactus, explosions, your own blows | None taken. No hurt flash, no knockback |
| The void, or stuck anywhere for 10 seconds | Placed back out of sight behind you, or at home |
| Unloaded chunks | They exist as saved data first. Home ones sleep with the chunk. One with you is never left behind |
| `/kill`, a lost entity, a crash mid-save | The body is made again at home from the saved record |
| Two copies after a bad load | The record keeps one; the other is removed |
| Raids and Faults | They hide with the beasts and come out at dawn. Nothing is taken from them |
| Peaceful, game rules, `spawn_mobs` off | No effect on them |
| The Hearth is broken or moved | They stay with its owner until a home exists again |

### 5.8 With other players

- One Awa and one Dubu per Hearth, shared by its household. A sister home gets no second pair.
- Any member can ask one along. It answers to one person at a time and goes home when that person logs out.
- Other players can pet them. They cannot take them, push them or hurt them.
- The Hearth's owner can rename them with the naming card that kin already have. Their coats stay.
- Two bodies per Hearth: nothing a busy server would notice.

### 5.9 Data

- Saved world data `family`, schema 1, keyed by the Hearth's owner: for each of the two, its entity id, name, state
  (home, with, waiting), who it is with, where it waits, where it was last seen, and whether it has arrived.
- The entity carries its owner and which of the two it is. On load and every 10 seconds the record and the bodies are
  reconciled: none missing, none twice.
- Nothing is saved on the player.

### 5.10 Tasks, the Hearth page, the Journal

- Hearth page: their two cards, first under Your beasts.
- Tasks, Hearth tab: one row, only until it has been done once: "Awa and Dubu will come with you if you ask."
- No Journal entry: they are not a discovery.

### 5.11 Sounds

From the game's own recordings, by name, pitched for them: the cat's purr, purreow and ambient for Awa; the wolf's
whine, pant, shake and a high, short bark for Dubu. No synthetic sounds. A listening page comes with stage 1.

### 5.12 Motion: the bar for the build

The preview's clips are a first pass. The build takes them to the Fresh Animations bar: strides driven by distance so
feet stay planted, the head leading a turn, steps on a turn in place, real clips for sitting down, getting up, lying
down and the flop (not crossfades), three idles each, and the life layer. Checked close up on CI film, at leg height.

### 5.13 Budgets

Two entities per Hearth. Thinking every 10 ticks. Finding a perch or a spot in the sun reads at most 64 blocks a second,
spread over ticks, and remembers what it found. No allocation per tick.

## 6. Failure and recovery

| What goes wrong | Seen as | What the game does | Test |
|---|---|---|---|
| No path to you at arrival | Would never arrive | Placed out of sight nearer, then walks | `family_arrives_without_a_path` |
| Body lost or killed | Would be gone | Remade at home from the record | `family_never_lost` |
| You fly off | Would be left behind | Catch-up from out of sight | `family_catches_up` |
| A fight starts | Would get in the way | Back off, untargetable | `family_keeps_out_of_fights` |
| You cross a gate | Would follow into it | Wait, then home | `family_waits_at_a_gate` |
| The person they are with logs out | Would stand in the wild | Go home | `family_goes_home_on_logout` |
| A second player asks | Two masters | The first keeps it; the plate says whose | `family_one_person_at_a_time` |
| Old save with a Hearth | Would never arrive | Arrive next time the owner is home | `family_arrives_at_an_old_home` |

## 7. Stages

- **Stage 0 (done here):** the bodies, coats and first-pass poses; pictures offline and in game.
- **Stage 1, they live with you:** the real entity in place of the preview, arrival (new and old worlds), the record
  that makes them impossible to lose, home life in its plain form (wander, sit, rest, sleep, greet, petting), the Hearth
  page cards, plates, sounds.
- **Stage 2, out with you:** ask along, stay, wait; following with each one's own manner; catch-up; boats and the skiff;
  danger; thresholds; the rules for several players; the Tasks row.
- **Stage 3, themselves:** her perches, sills, beds and sun; his giraffe, chin rests and yard's edge; the bells; the two
  of them together; every transition as a real clip; motion to the FA bar.
- **Later, each its own decision:** fetch with Dubu; Awa on your shoulder at speed; a basket to craft.

At AI pace: stage 1 about three to four hours, stage 2 the same, stage 3 four to five, most of it motion. Built in one
go and tested once at the end, film-checked on CI.

Rollback: a game rule `awaken:hearth_family` (on by default). Off, the bodies are removed and the record is kept. No
other system depends on them.

SPEC: one new section when it is built (the next free number), from sections 4 to 6 above. The preview adds nothing to
SPEC because it decides nothing about play.

## 8. Decisions for Euan

1. Her name is spelled **Awa**. (Recommended: yes.)
2. Awa's look, as drawn. (Yes, or say what is off.)
3. Dubu's look, as drawn. (Yes, or say what is off.)
4. Bodies of their own, not the vanilla cat and wolf. (Yes.)
5. They arrive half a minute after the first fire, Dubu first; on your current world, the next time you are home. (Yes.)
6. Family, not kin: no nook, no job, not counted for beast goals or the Nook bell. (Yes.)
7. Sneak and use asks one along or tells it to stay; use pets. (Yes.)
8. Out with you they keep up by catching up from out of sight, never by appearing beside you. (Yes.)
9. In a fight they back off and watch; nothing can target or hurt them; they never help. (Yes.)
10. They do not enter gates, dreams, the Nether or the End; they wait, then go home. (Yes.)
11. On a server: one pair per Hearth, shared by its household; the owner may rename them. (Yes.)
12. Sounds from the game's own cat and wolf recordings, pitched for them. (Yes.)
13. One Tasks row until you have first asked one along. (Yes.)
14. Later extras: (a) fetch with Dubu, yes; (b) Awa rides your shoulder when you sprint, glide or ride, yes;
    (c) a basket to craft, no.
15. Build stages 1 to 3 in one go, tested once at the end. (Yes.)

## 9. Not verified

- The pictures of their motion come from the rig's own keys. The life layer shows only in the game.
- No sound has been chosen or heard.
- The Far Landing's `TheLull.landing` is read from its branch, which is not merged.
- Nothing here has been played.
