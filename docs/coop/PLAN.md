# Together, All the Way

Co-op from the first minute to the last bell, and a second way to play gates for someone who doesn't enjoy fighting.
Plan only. Nothing in production has changed. Approval page source: `docs/coop/page.html`. SPEC gets its section (next
free number) in the first commit after approval.

Labels used below. **Verified**: I read it in the code or the SPEC of `build/awaken-v1` at f47c3cff2. **Not run**: nobody
has seen it in a game with two people. **Hypothesis**: likely from the code, to be measured in stage 0. **Proposal**: a
design choice, open to change.

## 1. Understanding and success contract

### 1.1 Euan's brief (11 Nov 2026, dictated)
Everything from A to Z works, and shines, with two or more players: the story, the first boss scene, every gate, rank
assessments, levels, discovery. A gate and a Reassessment can each be played two ways: both fight, or one tags along,
dies less, earns less, and still has "a ton of things to do during the fight". And whatever is missing that would make
co-op "20 times better" for a couple or for friends, "extremely smooth".

### 1.2 What "again" points at
He has asked for the tag-along before. **Riding Along** (SPEC §55, approved 9 Nov as Stage A of Made by Hand, built
the same day, merged into build, never seen in a game): two players tie a thread of light; the rider can't go down
within 48 blocks of the other; their hits count for 40%; monsters pick the fighter first; the gate isn't made harder
for them; they earn half the XP and no rank credit; **Reassessments can't be ridden**. His words then: a partner "who
doesn't try hard at games and isn't good at fighting", "effectively can't die, must stay balanced".

So a safe tag-along exists, bought with penalties. What today's brief adds, as he explained it the same day (1.2a):
- she fights **her own, simpler version of the same fight**, with her own weapon and powers, and none of the penalties;
- a **Reassessment has that way too** (today a tied pair is left standing at the bell);
- the same care for **two fighters on Full**, and for everything around the fights.

### 1.2a His ruling on the first page (11 Nov, the same day)
The first version of this plan gave the tag-along player a support job on the thread (gather light, mark, mend, bind,
haul, ward). He said no: "I really don't like this. I don't think they should just become a healer. I think they
should still have their weapons, their powers, and all the things like that. I don't think it should be connected by a
thread either. Or maybe ... if it looks really good and it's not distracting." And what he does want: "it doesn't need
to be so fair for the person that is riding along ... it's kind of two people having a different difficulty mode, in a
sense, while still being interesting for the person and still being revertible ... I love very difficult fights. But
she might ... want something a bit more simpler and co-op, while still obviously being action-based and fun."

So the tag-along is **her own difficulty**, not a role. That also corrects a reading in the brief I was given: "isn't
as good as fighting" was dictation for "isn't as good **at** fighting". Nothing in his words asks for her to earn less.
Everything about the support job is withdrawn (it stays in this branch's history at 35ef1c7e2).

### 1.3 The job, the pain, the wanted experience
- **The job.** Play the whole game with one other person, most often a partner, sometimes one to three friends,
  without either of them managing the game instead of playing it.
- **The pain (section 2 has the evidence).** The rules for sharing exist; the experience of being together doesn't.
  You can't see your partner on the screen or the map, you can't point at anything, sharing a home is a typed command,
  a death in a gate sends one of you home alone, a Reassessment takes one and leaves the other, a boss's warning ring
  closes for the wrong person, and the game has one difficulty for two people who want different ones.
- **The wanted experience.** Two people who always know where the other is and what they mean, who are never split up
  by a rule, who fight the same fight at the difficulty each of them enjoys, and who end the game with one home and
  one story they made together.

### 1.4 Hard constraints (his rulings, all still binding)
- Single player plays exactly as it does today. Every pair feature is inert with one player.
- A support player is a person. Nothing fights for you; no AI helper; no settlers ([[beasts-not-people]]).
- No new key and none taken over; power keys keep their meaning (Riding Along's condition, Made by Hand's too).
- No timing layer on everything: reward the right thing at the right moment, not button timing.
- Direction through the world and the System; no daily list; bosses are never announced.
- Plain words for every line a player reads. No synthetic dings; sounds are the game's own recordings by name.
- One side decides anything predicted; a server reply never changes what's on screen ([[animation-glitch-rules]]).
- Keep it safe for a bigger server later; build no server infrastructure now.
- The Mac runs one Java at a time; two-player checks happen on CI.

### 1.5 Words made observable
| His word | Strong | Weak | Unacceptable |
|---|---|---|---|
| "works in co-op" | Every step of the journey has a two-player test and a two-player picture | It runs, but one player waits or watches | A step that can't be finished by two, or that one finishes for both by accident |
| "dies less" | On Gentle she is fully down no more than once in ten gate floors; a pair is split by death less than once a gate | Safe but pointless | Can't fail at all, or his fight gets easier because she came |
| "action-based and fun", "simpler" | She fights the whole fight with her own weapon and powers; blows come at her about one phrase in four, each one she can read and answer with a held guard or a step | A fight that never looks at her | A job that isn't fighting; or his fight, just weaker |
| "doesn't need to be so fair" | She keeps full hits, full XP, loot and rank; only hard-feat titles ask for Full | Penalties that make her feel like a passenger | A setting that punishes her for using it |
| "revertible" | Her own switch, changed any time out of a fight | Changed only at a gate door | Chosen once |
| "extremely smooth" | No moment where the two screens disagree; nothing that only one of them is told | Works with visible hitches | A glitch either player would call a bug |
| "20 times better" | A couple can play an evening without typing a command, opening chat, or asking "where are you?" | Some of that | Today |

### 1.6 Non-goals
Guilds, matchmaking, voice chat, a server browser, trading, PvP, more than four in a party, any support or healer role,
a companion that isn't a person, any change to solo balance, any change to the look of the fights themselves.

### 1.7 Unknowns I could not settle by reading
- Whether two real clients can run on one CI runner (stage 0 probes it; nothing depends on the answer).
- How fast a boss's stance breaks under two fighters (stage 0 measures it).
- Whether power reactions (frost and steam, fire and oil) already cross between two players' casts.
- What the pack ships for inviting a friend over the internet (the old audit names e4mc; I didn't open the pack list).
- Answers still owed by three sibling sessions (section 8).

## 2. Verified audit and diagnosis

### 2.1 What co-op already has (keep all of it)
| Piece | What it does | Evidence | Verdict |
|---|---|---|---|
| Party hook | `PartyHooks.members/sameParty`; the installed rule is: everyone in your gate run, everyone you share a Hearth with, and your thread partner | `core/api/PartyHooks.java`, `gates/GateParty.java` | Keep the hook, replace the rule (4.1) |
| Shared rewards | Party within 32 blocks shares kill XP (each through their own budget) and gets their own loot rolls | `core/api/Parties.java`, `living/combat/KillXp.java`, SPEC "Together" | Keep |
| Gates in a party | Arena doors bring the party through; +35% monsters and +50% Lord health per extra fighter; hoard one per player | `gates/run/Rooms.java:63-86,174` | Keep, add stance (4.3) |
| Provings in a party | Party within 24 blocks comes; +50% Warden health each; a fall wakes you at the door; all down resets the Warden | `hearth/trial/Provings.java:58,167,285,342` | Extend to Gentle (4.5) |
| Shared Hearth | Owner and members, sister homes from tier III | SPEC "Together", `hearth/Household.java` | Keep, add a second keeper (4.1) |
| The opening | The square holds for the group; stragglers are pulled after 5 minutes; Bram's walk follows the first player | `opening/OpeningDirector.java:50-55`, test `opening_party_wait` | Keep |
| The first boss scene | The Ritual waits for every unawakened player (90 s), kills all at once, each answers "Will you accept?" alone | `docs/proposals/the-ritual.md`, `story/RitualTests.java:133-167` | Keep |
| Bells | A book per player; bells hang at the home and ring for every member; a Lord's bell goes to each player in the run | SPEC §43.14-43.16, test `bells_lord_room` | Keep |
| Monuments and finds | Everyone near when it opens is paid, once per player per site | `finds/Reward.java:39-50` | Keep, add "shown the way" (4.7) |
| The Atlas | Waypoints you share show for your party; "Share with party" sends what you've explored | `atlas/Waypoints.java`, `atlas/Discovery.java:357` | Keep, add live dots (4.2) |
| Dreams | Sleepers in one room share the host's dream | `lucidium/dream/Sleep.java:171-200`, test `dreams_shared` | Keep; check the guest's prize |
| Powers | No power ever hurts another player | `powers/Combat.java:42` | Keep |
| Riding Along | The thread, the catch, 40% hits, half XP, no aggro, no scaling | SPEC §55, `riding/*`, 13 `bond_` tests | Three mechanisms kept, its rules retired (4.4) |
| Far Landing (branch) | Everyone on the deck flies together; the skiff runs both ways; one landing per world | `claude/far-landing-ae982f` SPEC §67.5 | Keep; three small rules (4.7) |
| Tests | 23 `together_` tests, 13 `bond_`, a real survival test player whose packets can be read | `core/test/AwakenTests.java:319-347` | The base of the pair kit (6) |

### 2.2 What is wrong or missing with two players
| # | Finding | Evidence | Consequence | Label |
|---|---|---|---|---|
| F1 | No party you choose. Your party is a side effect of a Hearth, a gate or a thread | `GateParty.members` | Two friends walking the wilds on day one share nothing until one types a command | Verified |
| F2 | Sharing a home is `/awaken hearth invite`, or using the other's Anchor and waiting for a card | `hearth/HearthCommands.java:42-52` | A couple's first shared act is a typed command | Verified |
| F3 | Nothing on screen shows your partner: no health, no direction, no dot on the Atlas, no way to point | no party HUD or ping class anywhere; the Atlas client draws no other players | "Where are you?" is the whole evening | Verified |
| F4 | A fighter who dies in a gate loses the Satchel, takes a Fracture, leaves the run and wakes at the Hearth | `gates/run/Runs.java:651-704` | One of you is home alone, perhaps kilometres away, while the other is still inside | Verified |
| F5 | A Reassessment takes only party members at the level, and skips a tied pair entirely | `Provings.java:167-171` (no word is said to the one left out) | The rider, or a friend a few levels short, stands at the bell while their partner vanishes into the game's best fight | Verified |
| F6 | A boss's warning carries no target, and the closing ring is drawn for whoever is locked on | `mobs/entity/FightRunner.java:864,935` (`setTell`), `aim/client/Reticle.java:157-177` | The player the blow is NOT for sees the ring close. With the boss plan's "Too early / Too late" they would be told off for a blow that wasn't theirs | Verified |
| F7 | Bosses retarget on every hit (`HurtByTargetGoal` at priority 1), and the boss's read of you starts over on a new target | `mobs/entity/AwakenMob.java:201`, SPEC §48.1 "A new target starts the read again" | Two fighters make a boss flick between them; "Fights That Read You" reads nobody | Verified in code, not run |
| F8 | A party raises health but not stance | `arms/ArmsRules.java:250` (stance by kind and role only) against `Rooms.java:174` | Two fighters break a boss's stance about twice as often per point of health | Hypothesis |
| F9 | The fights have one difficulty. The world's `difficulty` setting only decides what a death costs | `core/AwakenConfig.java:9-16` (STORY, HUNTER, HARDLINE, HARDCORE) and its nine uses | Two people who want different fights can't have them. Riding Along's answer was to make her safe by making her weak (40% hits, half XP, no rank), which is fairness he no longer asks for | Verified |
| F10 | Half XP on this curve pulls a pair apart | `docs/coop/harness/rider_pace.py` | At the hour he is ready for S (level 100) she would be about level 40, so they could never take a Reassessment the same day | Computed |
| F11 | A late friend plays the whole opening, the Registry, Ilse and the flight alone, then walks | `OpeningDirector`, §67.5 | 30 to 40 minutes before they see the person they joined | Verified in code, not run |
| F12 | A friend who wasn't there can never earn a solved monument | `Reward.pay` pays who is near when it opens | Late friends find the world already emptied near home | Verified |
| F13 | Levels apart have no rule except the thread | region levels by place (§21, §67.3); `KillXp.diff` | A stronger friend one-shots everything in the weaker one's land; a weaker fighter is one-shot in the stronger one's | Verified |
| F14 | The finishing blow (boss plan, not built) films only the striker | `claude/boss-experience-plan-3bdfa8` PLAN 4.4 "With others" | The partner who helped watches a body from the side | Verified (plan) |
| F15 | Nothing has been seen with two real players. Every tour is one client | `tools/autopilot/*` (544 scripts), `.github/workflows/tours.yml` | Thread, fold, party doors: all unseen | Verified |

### 2.3 The journey, step by step
| Step | Today with two | What changes |
|---|---|---|
| The sky, the run, the square | Built for a group; tested with four | Nothing. Those who start together become a party without asking (4.1) |
| The Ritual (the first boss scene) | Waits for everyone, kills all at once; each accepts alone | Nothing in the scene. The Together section says who is still choosing |
| Infirmary and Registry | Each wakes on their own clock; Bram walks the first player | The one who wakes later sees the other waiting; the party strip appears when the band boots (4.2) |
| Ilse's fire | Each gets the coal | When one places the Ember near the other: "Make this your home too?" (4.1) |
| The skiff | Everyone on the deck flies together, coal or not, and each sees the whole film (confirmed, section 8) | A passenger who has flown may take the helm home |
| First gate | Both walk in; the door can offer the thread | Both walk in, each on their own setting; no card, no thread (4.4) |
| Gate floors | Shared credit, sealing, scaling | See each other; point; stand back up; targets; her own difficulty |
| The Lord | +50% health each, hoard each, bell each | Target hold, the right ring, stance, a shared finishing film |
| Reassessment | Fighters at the level only; a tied pair is skipped | Each fights it on their own setting and takes the rank (4.5) |
| Home, kin, bells | Per home, per member | Second keeper; pets by the pets plan |
| Monuments, finds, movement lessons | Paid to all near | A partner's solo finds become leads; "shown the way" |
| Dreams | Shared by room | Check the guest's prize in the remade dreams |
| The Hundred Peal, the Deep's answer | Per home; every member | Nothing |
| The Seed, the ending | One ending per world; later arrivals are told it | Nothing |
| The homecoming | Takes "you and anyone beside you" | Nothing |

### 2.4 Root causes
1. Co-op was built as rules (24 Oct) before most of the game existed (the Journey, the Provings, Fights That Read
   You, Gates as Worlds, the Far Landing came after). Each later plan added a line about members; none owned the pair.
2. There is no object called "the people I'm playing with". Everything asks `PartyHooks`, and the rule behind it was
   an interim one that was never replaced.
3. Riding Along bought safety with fairness: a weaker, poorer fighter on a tether. What he wants is a second
   difficulty in the same fight, which nothing in the game has.
4. No test or tour has two players who behave like players, so nothing that only shows on the second screen was seen.

## 3. Alternatives and recommendation

### 3.1 The tag-along: four directions
| | A. Riding Along as built | B. A support job on the thread (the first page) | C. Her own difficulty in the same fight (recommended) | D. One easier setting for the whole world |
|---|---|---|---|---|
| What she does | Fights at 40% | Gathers, mends, binds | Fights, with her own weapon and powers, at full strength | Fights |
| What he gets | His fight | His fight, made survivable | His fight, unchanged where it's aimed at him | An easier fight he doesn't want |
| Safety | The catch, on a tether | The catch, on a tether | Half damage, second winds, no tether | Softer for both |
| Verdict | Safe but weak | **Rejected by Euan, 11 Nov** ("just become a healer") | Reuses the catch, the target rule and the scaling hook of A; the boss already decides per target (SPEC §48) | Fails "I love very difficult fights" |

C wins on his words. Its cost is one new idea in the code: a boss chooses what to throw by who it's aimed at.
Gears of War has let each co-op player pick their own difficulty since its second game (section 7).

### 3.2 Being together: two directions
| | Patch each system | One small module that owns the pair (recommended) |
|---|---|---|
| Party | Keep the interim rule | A party you form with a card, saved, behind the same `PartyHooks` |
| Seeing and pointing | A HUD here, a map dot there | One strip, one ping, one state synced once |
| Risk | Six half-features | One module to get right; everything else already asks the hook |

### 3.3 Things I looked at and left out
- **A shared screen camera or split screen**: not Minecraft.
- **Shared XP pool or one shared level**: removes each person's own progress; the budgets already stop boosting.
- **Text or voice chat features**: vanilla chat exists; a couple in one room needs neither; pings cover the rest.
- **Scaling the land to the weaker player**: world-moods owns the land's danger; the party protects people instead.
- **A third step above Full** for the one who loves hard fights: not asked for; the setting is data, so a step can be
  added later without new code paths.

## 4. Behaviour and architecture

New module `dev.awaken.together` (the SPEC section it extends is already called "Together"). It owns the party, the
party strip, pings and the pair's shared state, and installs the real `PartyHooks.Access`. `dev.awaken.riding` shrinks
to the three mechanisms Gentle keeps (4.4). Other modules change only where the audit names a line.

### 4.1 The party (who you're playing with)
- **What it is.** Up to four players who chose each other. Saved per world (`together`, schema 1): members, who
  asked, when. `PartyHooks.members` returns the party, plus, as today, anyone inside your gate run.
  A shared Hearth no longer makes a party by itself; a party usually shares one.
- **Forming it, never by command.** The band's Status page gets a TOGETHER section (it replaces RIDING ALONG): every player within 48 blocks or sharing your home, each with what you can do: `Travel together`,
  `Share my home`, `Leave`, and your own **My fights** switch (4.4). Each is a 30-second card for the other. Looking at a player
  and pressing the band's key opens the section on them. Commands stay for servers and tests.
- **Automatic.** Players who walk the opening together are a party from the square onward. Nothing to accept.
- **A home for two.** When a party member places the Ember within 24 blocks of another member with no home: "Make
  this your home too?" One yes and they are **keepers** together. A Hearth has one owner (as saved today) and may have
  one second keeper with the owner's rights except ending the household. Members stay as they are.
- **Leaving.** Either side, any time, out of a fight. A party of one is no party. Logging off keeps it.
- **Failure.** A card that runs out chooses nothing. A member who leaves mid-gate keeps this run's party (the gate
  rule), so nothing changes under their feet.

### 4.2 Seeing each other and pointing (mockups M1, M2, M5)
- **The party strip.** One row per party member, top left under the band's own line: name, health as a bar, a
  small state mark (in a menu, in a gate with its floor, on Gentle, on a knee, down, far), and when they are off screen an arrow on
  the screen's edge with the distance. Hidden when you have no party or they are in another dimension with nothing
  to say. Drawn in the band's look; off with the HUD; a setting to keep it small. The Tab glance lists the same people
  with level, rank and place ("Amber Reach, floor 2").
- **The Atlas.** Party members in your dimension are live dots in their colour, with their home and, faintly, the
  destination they've set. `Go to <name>` is a destination that follows them.
- **Pings, on the lock key.** Middle click on a monster locks on as today, and your party sees a small mark on
  what you've locked: shared focus with no extra press. Middle click on anything else points at it: a marker in world
  light for 8 seconds, on the edge when off screen, on the Atlas, with one plain line under the pinger's name chosen
  by what it is: "Over here", "Ore here", "Let's go in" (a gate door), "Look at this" (a find, a beast, a bell), "Careful
  here" (double click), "On my way" (on a partner or their ping), "Help" (while down). With binoculars up, the mark you
  already make is shared. Four pings in two seconds at most (`core.Throttle`). A setting silences others' ping sounds.
- **Sync.** One small message per member on change only: health in twentieths, state, floor; position only when the
  client isn't already tracking that player, twice a second. Budget in section 4.9.

### 4.3 Two fighters (side by side)
- **Whose blow it is.** A tell carries its target's id. The full closing ring, and any "Too early / Too late", show
  only for that player. Their partner sees a thin ring in the blow's colour with no verdict. A blow that sweeps wide
  enough to reach both is everyone's. This is a change to `Strikes.Tell` and to whichever ring ships (today's or the
  boss plan's); section 8 has the question to that session.
- **A boss keeps its mind.** A boss holds its target for a whole phrase and changes only at an opening, to whoever
  did the most to it since the last change, or whoever is in front if that's close. It shows the change with the
  notice it already has (the head turn, §48.2). `FightMind` keeps a read per player instead of starting over.
  Packs already take turns (§48); nothing changes there. Riding Along's rule (fighters first) stays on top.
- **Its back is turned.** A blow that lands on a monster while it is in the middle of a blow at someone else does a
  quarter more damage and stance. For anyone, on any setting. It gives two people a reason to split up around a
  boss, and the one it isn't looking at a job that is still fighting.
- **Stance with two fighters.** If stage 0 confirms F8, stance grows with fighters as health does (+50% each), so a
  break means the same thing with two as with one.
- **Standing back up (gates only).** A fighter who would die in a gate while another of the party still stands
  on the floor goes **down** instead: kneeling, untargetable, for 20 seconds. A partner who reaches them and holds
  Use for 2 seconds lifts them at 30% health with 2 seconds of grace. Once per fighter per floor (twice on Story).
  Nobody comes, or everyone is down: today's death, unchanged. Cheat Death still goes first. Minecraft Dungeons
  works this way (section 7).
- **After a real death.** While your party is still inside, you wake at the gate's door in the overworld, not at
  home, and the door lets you back in at the last shrine once they're out of a fight. The Satchel and the Fracture are
  the price, as today.
- **The finishing blow** (when the boss plan is built). Either fighter may take the last stand. Everyone in the
  fight sees the same film, and when a partner is within 12 blocks the shot frames both. Through-your-own-eyes stays
  each player's own setting. On either setting.
- **Provings.** Already forgiving. Add what is said: "Ana is back up" and "Both of you are down. It starts over."

### 4.4 Her own difficulty: Full and Gentle (mockups M1, M4, M5, storyboard S1)
One setting per player, **My fights: Full or Gentle**. Full is the game as it is. Gentle is the same fight asked more
simply. She keeps her weapon, her powers, her loot and her progress. Nothing ties the two players together.

**What Gentle changes, for that player only** (all decided on the server, by who a thing is aimed at):
| Part | Full (today) | Gentle |
|---|---|---|
| Blows a boss aims at you | White-gold, amber, red, grabs; strings up to four; feints and answers to what you do | White-gold and amber only; strings of two at most; no feints, no answers to your drinking or casting |
| Warning | The move's own | The same move holds 8 ticks longer before it comes |
| A held guard | Stops white-gold; amber breaks it | Stops both. A well-timed deflect still earns its reward |
| The opening after it attacks you | The move's own | Half as long again |
| How often it turns to you | By what you do to it | With a Full fighter in sight, about one phrase in four; alone, as normal |
| Small monsters | As many as reach you | Two on you at a time; the rest hold back or go for a Full fighter |
| Damage you take | Full | Half, after armour, from everything |
| A killing blow | Down (4.3), or death | A **second wind**: thrown back, two seconds on a knee where nothing can touch you, up at half health. Three a floor, refilled at a shrine. After those, down as anyone |
| A fall into a pit in a gate | Death | Set back on the last safe ground, two hearts lighter |
| What a death costs | The world's setting | Story's rules: half the Satchel kept, no Fracture |
| Your hits, your stance damage, your powers | Full | Full |
| XP, loot, boss pieces, bells, rank | Yours | The same |
| Titles and records for hard feats (solo clears, untouched fights, Hardline's) | Earned | Not earned on Gentle |

- **His fight stays his.** Nothing above touches a blow aimed at a Full player. To keep her full-strength hits from
  shortening his fight, a Gentle player counts as **half a fighter** when a room or a boss is sized: a Lord or Warden
  gains 25% health and stance for her (50% for a Full partner), a room a sixth more monsters, no extra elite.
- **Still a fight for her.** The boss does turn to her. What it throws can always be read, and holding guard is
  always an answer. She breaks stance, lands heavies, casts, takes the finishing blow if she's the one there.
  With "its back is turned" (4.3) she is usually the one hitting from behind while it's busy with him.
- **Chosen and changed.** Her own switch on the band's Together section. It changes at once out of a fight; in a gate
  it takes hold at the next room. Her party sees a small mark by her name on the strip. It is hers everywhere, alone
  or together, so the world doesn't turn on her when her partner logs off.
- **Not a tether.** No thread, no reach, no both-must-agree, no card at a gate's door. The thread's look that Riding
  Along built stays in the code, unused; a decision asks whether to keep it as a faint look between two partners.
- **What happens to Riding Along (SPEC §55).** Its rules are retired: the bond, the 40% hits, half XP, no rank
  credit, the fold's four seconds through the partner's eyes, the refusal at Reassessments. Three of its mechanisms
  are kept under the new rule: the target preference (`LivingChangeTargetEvent` sweep), the cancelled killing blow
  (now the second wind), and the scaling hook in `Rooms.partyOnFloor` (now half a fighter). Its 13 `bond_` tests are
  rewritten as `gentle_` tests. It was never seen in a game, so nothing a player knows changes.
- **Numbers are first guesses.** Every value in the table is data (`awaken-together.toml`), to be tuned after the two
  of them have played, not before.

### 4.5 A Reassessment, two ways
- **Both on Full** (today, kept): each fighter at the level and ready takes the rank; a ranked friend comes as
  practice; +50% Warden health and stance per extra fighter.
- **One on Gentle** (new). She comes as a fighter like anyone (today a tied pair is skipped). The Warden treats her
  by Gentle's rules and gains 25% for her. If she's at the level and ready she takes the rank, the same rank, with
  nothing written on it. Same XP means a pair who play together reach the level together, so one fight ranks both.
- **A friend short of the level**, on either setting, may come along and fight. They're told plainly that this one
  doesn't count for them yet. Nobody is skipped in silence.
- **The gift.** Whatever the Warden leaves (the Ringer's chain is the Tether) goes to everyone in the hall, as today.
- **Lose.** As today: no fail day, wake at the door, go again.

### 4.6 Levels apart
- **Gentle is the first answer**: the weaker player takes half damage and has second winds in the stronger one's
  land. It softens a gap; it doesn't erase one (ten levels under, a Full blow that would kill twice still hurts).
- **Match my friend** (a switch in TOGETHER, off by default): within 32 blocks of a party member ten or more
  levels under you, in land at their level, your hits are evened down to their level (the mechanism the story's
  Ascension tiers already use, `story/Ascension`), and you earn at your own level's rate. For the friend who wants
  to play the early game with a newcomer rather than clear it for them. Guild Wars 2 does the same by zone (7).
  It changes hits only: the land's dread look and sound stay on each player's real level, so the weaker player's
  warning never fades because a strong friend is near (world-moods' request, section 8).
- **Pay above your rank**: a fighter in a gate above their rank is paid what a gate of their own rank would pay
  (the cap Riding Along wrote as `Runs.riderReward`, kept for everyone).

### 4.7 The journey's joins
- **Joining late.** The newcomer plays the opening (it is the game's first impression and it is theirs), and the
  host gets one line: "Ana has arrived in Carillon." After the Registry they are offered the party. The skiff takes
  them out. From their first landing on, the **Answering Peal** works.
- **The Answering Peal.** A partner rings their handbell for you; you hear it from its bearing wherever you are;
  ring yours back, stand still 5 seconds, and you're beside them. Both in the overworld, neither in a fight, a gate, a
  trial or a dream; every 10 minutes. It is Homeward's rule pointed at a person. No menu.
- **The skiff.** It already does two of the three things a pair needs (section 8): anyone on the deck flies, coal or
  not, and each sees the whole film; and the skiff is never away on the server, so a late friend always finds it.
  One gap to close with that plan: a passenger without the coal can't fly home alone (the helm turns them away). The
  rule: anyone who has flown once may take the helm **home**; flying **out** as the pilot still needs Ilse's coal.
- **Shown the way.** A monument your partner solved without you is a lead in your Journal ("Ana opened the Tether
  Spire east of home"). Go there with them and touch the cache: you're paid a first-timer's share, once.
- **Found together.** A find, a region, a beast one of you discovers alone becomes a lead for the other, never a
  completion. Two people explore in different directions and bring each other something.
- **Ours.** One page in the Journal: the firsts you did together (first gate, first Lord, first peal, the day the
  home was founded), each with its play-day. A record, never an order.
- **Tasks.** A row both of you have open carries a small two-heads mark. One optional row appears only when it's
  true: "Ana is ready for her Reassessment." Nothing else is added to the list.
- **Pets, dreams, the second Hearth.** By their own plans; questions in section 8.

### 4.8 Data and lifecycle
| State | Where | Schema | Notes |
|---|---|---|---|
| Party | saved data `awaken:together` | 1 | members, formed day; old worlds load with none |
| Second keeper | `HearthRecord` household | +1 | optional field; old Hearths load with none |
| Down | in memory, per run | none | never saved; a logout while down is a death as today, a crash gets up |
| My fights (Full or Gentle), second winds left | attachment `awaken:fights` on the player | 1 | the setting is kept through death and logout; second winds are not saved (a relog refills them) |
| Tell target | synced entity data on the boss | none | cleared with the tell |
| Pings | in memory, 8 s | none | never saved |
| Shown-the-way leads, Ours | the Journal's own book | +1 | optional lists |
All new fields are optional with defaults, so `together_old_saves_load_the_same` holds and gains a twin,
`alone_plays_the_same`: one player, every switch on, the same packets and the same numbers as today.

### 4.9 Performance and the wire
- Stage 0 measures the baseline with the pair kit: packets and bytes per player per second, alone and as two, in a
  gate fight (the test player's channel already records every packet, `AwakenTests.Live.sent`).
- Budget for everything in this plan: under 12 messages a second per player in a fight, under 2 at rest; no
  allocation per tick on the server for the strip (state diffed in place); pings rate-limited.
- Server tick: the spec's own budget (p95 at or under 25 ms) with two players in a Lord fight on the CI runner.

### 4.10 Failure register
| Case | What happens | Test |
|---|---|---|
| Partner logs off mid-gate | The run goes on for the other; scaling drops at the next room | `pair_logout_mid_gate` |
| Partner logs off while you're down | Down ends as a death (nobody can come) | `pair_down_partner_leaves` |
| Both down the same tick | Death for both, as today | `pair_both_down` |
| She switches to Full (or back) in the middle of a boss fight | The switch waits for the fight to end and says so | `gentle_switch_waits` |
| A red blow aimed at him reaches her too | It is his blow (never filtered); she takes half | `gentle_caught_in_his_blow` |
| Two Gentle players and nobody on Full | The boss shares its phrases between them, all by Gentle's rules; sized as one fighter | `gentle_two_alone` |
| He dies and she is still standing | He goes down and she can lift him, as any pair (4.3); second winds are hers only | `pair_down_lift` |
| Ping spam | Throttle; the fifth in two seconds is dropped silently | `pair_ping_throttle` |
| A boss's target logs off mid-phrase | Target passes at once, with the notice | `pair_target_leaves` |
| Party member in another dimension | Strip shows where ("in a gate", "asleep"); no arrow | `pair_strip_far` |
| A script whose phrase has only red blows and grabs, aimed at a Gentle player | It picks from the body's plain blows instead; never nothing | `gentle_always_a_blow` |
| Answering Peal into unloaded or unsafe ground | Lands on the partner's last safe standing spot | `pair_peal_safe` |
| Both of a couple have pealed and one accepts the homecoming | One fetch takes both; both are thanked in Carillon; no second card is left waiting for the other | `pair_homecoming_together` |
| A pet is out with one of you when the other rings the Answering Peal | The pet's own catch-up rule brings it (pets plan); nothing here moves it | `pair_peal_safe` |
| Old world, one player, all switches on | Exactly today | `alone_plays_the_same` |

## 5. Concrete demonstrations
The page carries them as drawings (they are mockups, not screenshots; nothing here is built):
- **M1** his screen on Full: a red blow is his; the party strip and an edge arrow say where she is.
- **M4** her screen on Gentle, the same second: she is behind the Lord, its back turned; the ring is thin, not hers.
- **M2** pings: a place, a gate door, shared focus.
- **M5** the Together section of the band, with the My fights switch.
- **S1** a storyboard of ninety seconds of a Lord fight, both screens side by side, six beats.
- **S2** a Reassessment with one on Gentle, four beats.
- Before and after for four cases: an ordinary gate with two on Full; the case he asked about (a partner who wants a
  simpler fight); a hard case (levels 8 and 35); a failure (her second winds are spent and she goes down).

## 6. Evaluation and delivery

### 6.1 How co-op gets tested
- **The pair kit** (`core.test.Pair`, stage 0): two real survival test players in a party, at a site, with each
  one's packets readable. Every test below uses it. Naming: `pair_` (two fighters and the party), `gentle_` (her
  own difficulty), beside the existing `together_`; the 13 `bond_` tests are rewritten as `gentle_`.
- **One test per mechanism** (about 40): party forms by card and by the opening; second keeper; strip state on
  change only; ping kinds and throttle; shared focus; tell target and the ring's owner; target hold and its notice;
  per-player reads; stance with two; its back is turned; down, lift, both down, logout while down; the door's respawn
  and way back in; the setting saved and switched, waiting out a fight; half damage; no red and no grab aimed at a
  Gentle target, and never nothing; the longer warning; a held guard stops amber; the longer opening; one phrase in
  four with a Full partner, all of them alone; two small monsters at a time; second winds, their count and refill; the
  caught fall; Story's death cost; full hits; half a fighter in a room's size and a boss's health; a blow aimed at a
  Full player is byte for byte today's; XP, loot and rank the same; hard-feat counters refuse Gentle; a Proving with
  one on Gentle; a friend short of the level told, not skipped; Match my friend; the Answering Peal's refusals; shown
  the way; leads from a partner; `alone_plays_the_same`.
- **The journey as a pair** (five longer tests): opening to Registry with two; Ilse to the landing with two; a whole
  E gate, one on Full and one on Gentle; a D Proving the same way; a home founded by two and a bell rung for both.
- **Tours with a partner on CI.** One client films while the server plays a second player from a script (a
  **tour partner**: the same real test player the GameTests use, with a short list of moves: go, look, strike,
  cast, use, fall). The client is on Full in one tour and on Gentle in the next, so both screens are
  photographed: strip, pings, a Lord fight from each side, a second wind, a lift, a Proving, the Atlas. Same runners, same script language, no second client.
- **Two real clients, once.** A stage 0 probe on a Linux CI runner (16 GB): one dedicated server in offline mode,
  two clients under a virtual display, five scripted minutes, then the logs are read for kicks, errors and
  disagreements. If the clients won't draw there, the probe is dropped and nothing else changes: the tour partner
  covers the pictures and the last check covers the feel. Never two clients on the Mac.
- **The wire.** Packet and byte counts per player from the pair kit, alone against two, before and after each stage.
- **The real test.** Euan and his partner play: the opening, a gate with her on Gentle, a Reassessment the same way. Both
  play logs are copied at once (the black box is overwritten by the next session).

### 6.2 Acceptance gates
- Every row of 2.3 has a passing pair test or a tour picture.
- `alone_plays_the_same` and `together_old_saves_load_the_same` pass; the full suite is green (the manager runs it once).
- In the scripted Lord fight with a Gentle partner, the blows aimed at the Full player are the same moves with the
  same timings as alone (compared from the fight recorder), at least three phrases in four are his, and the fight's
  length with her striking at a third of his rate is within 15% of his fight alone.
- Wire budget of 4.9 holds.
- No picture shows text cut off or a strip over the boss plate.

### 6.3 Stages (each behind a switch in `awaken-together.toml`; off is exactly today)
| Stage | What | Depends on | Builder hours (AI pace) |
|---|---|---|---|
| 0 | Pair kit, tour partner, wire baseline, stance measurement, two-client probe. No behaviour changes | nothing | 2 to 3 |
| 1 | See and say: the party, the TOGETHER section, home for two, party strip, Atlas dots, pings, shared focus | 0 | 5 to 7 |
| 2 | Two fighters: tell targets and the ring's owner, target hold, per-player reads, stance, its back is turned, standing back up, the door respawn | 0; the boss plan's ring if it lands first | 5 to 7 |
| 3 | Gentle I: the setting and its switch, half damage, second winds, caught falls, Story's death cost, half a fighter, the strip's mark; Riding Along's rules retired | 1 | 4 to 6 |
| 4 | Gentle II: a boss chooses by who a blow is for (no red, no grab, strings of two, the longer warning and opening), the held guard, one phrase in four, two monsters at a time, Reassessments on Gentle | 2, 3 | 5 to 7 |
| 5 | The joins: late friends, the Answering Peal, skiff rules, shown the way, leads, Ours, Tasks marks, Match my friend, the shared finishing film | 1; Far Landing merged; boss plan built | 5 to 8 |
| 6 | The pair plays; fixes from two play logs | all | as found |
About 26 to 38 builder hours. One builder, in order, coded whole and tested at the end as his rule says; the tour
partner's pictures at each stage are the look check.

### 6.4 Rollback
Each stage's switch off returns that stage to today. Saved fields are optional, so a world that used them loads on a
build without them (parties and the fights setting are ignored, never corrupt).

### 6.5 Risks
- **Gentle is too soft or not soft enough** (the thing most likely to need their play). Every number is data; the
  first play is the tuning session, with both play logs.
- **Gentle bleeds into his fight.** The recorder test: a blow aimed at a Full player is the same move with the same
  timings as today.
- **A boss with nothing plain to throw.** Every kit body has plain blows; the test `gentle_always_a_blow` walks all
  47 fight scripts.
- **The strip crowds the screen.** It is one line per person, hides when alone, and M1 shows it against the boss plate.
- **Pings on the lock key misfire.** Lock always wins on a monster; a ping never fires while a lock is held.
- **An easy mode leaks into solo play.** Gentle is a player's own choice, default Full; a decision asks whether it
  may be used alone.
- **Three other plans move the same ground** (boss ring, Far Landing, pets). Section 8; no file of theirs is edited
  before they merge.

## 7. Research
| Claim | Source | What it means here |
|---|---|---|
| Mario Galaxy's second player collects star bits and stuns enemies with a pointer and can't be hurt; guides call the first game's role thin ("stop enemies or gather star bits, but little else"), and Galaxy 2 widened it | mariowiki.com/Co-Star_Luma; imore.com (Co-Star Mode guide) | A helper role beside the real game reads as thin. He ruled the same way: she fights |
| Apex Legends' one-button, context-aware ping lets a team play with no microphone; Respawn tested with voice banned | gamedeveloper.com (Respawn played with muted mics); pcgamer.com | One input that says the right thing by what you look at |
| Minecraft Dungeons: a downed player is revived by a teammate standing near; the team only loses a life when all are down; enemies scale with players | minecraftdungeons.wiki.fextralife.com/Multiplayer; gamewith.net | Standing back up is what Minecraft's own co-op game does |
| Guild Wars 2 lowers a stronger character to the area's level so friends can play early zones together | wiki.guildwars2.com/wiki/Dynamic_level_adjustment | Match my friend, as a choice |
| Gears of War lets each co-op player choose their own difficulty ("one person could be playing on Hardcore while the other ... on Casual") | co-optimus.com (Gears of War 2 co-op info; Gears of War 3 co-op review and FAQ) | Two difficulties in one fight is a known, liked shape; theirs is damage and aim, ours adds what the boss chooses to throw |
None of these was measured in AWAKEN; they pick the direction, the tests and his play decide.

## 8. Other plans in flight
Asked by message on 11 Nov; answers are folded in below as they arrive.
- **Far Landing** (`spires-and-journey`): passengers without the coal, the film for everyone on deck, a helm used
  while the skiff is away, anything keyed to one player, a way straight to a friend.
  **Answered 11 Nov** (read from its code at 2abda1c36, not merged):
  - Only the one at the helm is checked for the coal. Everyone else on the deck flies and gets their own film.
    The gap: a passenger without the coal can't take the skiff home alone (taken into 4.7).
  - Both moorings belong to the world. The skiff's blocks never leave; a second flight can start while one is in
    the air. Only the pictures can overlap for a watcher within 160 blocks who then takes the helm.
  - Everything else is per player: the marks, the letters (16 each), the hold's errands, the invitations, the
    homecoming. In a shared home each member who has pealed gets their own homecoming; the fetch carries anyone within
    5 blocks but changes only its own player's marks. So a couple needs a test that one fetch serves both (4.10).
  - Nothing goes straight to a friend. The Answering Peal (4.7) is the only such thing in this plan or that one.
- **The Whole Boss Fight** (`boss-experience-plan`): whether the new ring knows who a blow is for; the target hold;
  more than one client filming one finishing blow.
  **Answered 11 Nov** (its SPEC §68, merged into build the same day):
  - Whose blow: its ring times the blow on its own clock and takes "whose" from `Strikes.Tell.aimedAt`: the full ring,
    the band, the white "now", the glint and the threat arc show only for the target; anyone else gets the thin ring.
    It remembers what the tell last said until the blow is over, because the tell clears a tick before the blow lands.
    "Too early / Too late" come from the server's reply to your own guard, so they were always per player.
  - The target hold doesn't clash: nothing of its reads or sets the target except the last stand, which clears it.
  - The finishing blow: the server already sends one `mobs_finish` (boss, striker, ticks) to everyone within 64 blocks
    and every client plays the blow's light and the body's ending; only the striker's client gets the freeze, the bars
    and the camera. A shared film is a client-side change in `mobs.client.finish.FinishClient` (let party members
    through the "mine" test and frame from the striker's position). Either fighter can already take it.
- **Aya and Dubu** (`hearth-pets-plan`): who may ask a pet along in a shared home; pets and the skiff and gates.
  **Answered 11 Nov** (SPEC §69 on its branch, approved, not yet played):
  - The pair belongs to the home and is shared by its household; a sister home gets no second pair. Owner or member
    may ask one along; each pet answers to one person at a time, and the second to ask is told who it's with. So a
    couple can take one each. Only the owner renames them: with two keepers (4.1) the second keeper should be able to
    as well, a one-line change for that plan once both are merged.
  - Nothing can target or hurt them and they never fight; in a fight they back off 11 to 13 blocks. They wait where
    they stand when their person enters a gate or a dream and go home after five minutes. After a skiff flight their
    own catch-up rule brings them. Nothing in this plan needs to move or protect a pet.
- **Danger and calm** (`world-moods-plan`): mixed levels; whether its late second Hearth is the sister home.
  **Answered 11 Nov** (its page isn't published; nothing of it is approved):
  - No clash on mixed levels. Land and creature levels come from the place, never from who is there. The land's mood
    is the same for both players; the dread look stays per player by that player's own gap, so two friends side by
    side see slightly different pictures, as today.
  - Its one request: Match my friend must leave the dread gap on the real level (taken into 4.6).
  - It adds no second Hearth. Sister homes (SPEC "Together") stay the one rule to describe to Euan. Its Hearth
    proposals: a moved Hearth keeps tier, forge tier, name, bells and kin; a newly placed Hearth takes a day and a night
    before its calm, waking point and Homeward work; an Anchor takes root only in land up to 5 levels above whoever
    places it. For this plan that means: a home founded by two is measured on the one who places the Ember (so the
    weaker of a pair should not be the one to place it far out, and the card says so if it's refused), a sister
    home's Anchor obeys the same rules, and the Answering Peal is unaffected (it goes to a person, not a home).

## 9. Decisions for Euan
On the page, each with a recommendation.
