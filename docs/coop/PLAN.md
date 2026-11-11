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

So the safe half of the tag-along exists. What today's brief adds:
- the rider has **things of their own to do** (today they are a fighter at 40%, with nothing that is theirs);
- a **Reassessment has a tag-along way too** (today the rider is left standing at the bell);
- the same care for **two fighters**, and for everything around the fights.

### 1.3 The job, the pain, the wanted experience
- **The job.** Play the whole game with one other person, most often a partner, sometimes one to three friends,
  without either of them managing the game instead of playing it.
- **The pain (section 2 has the evidence).** The rules for sharing exist; the experience of being together doesn't.
  You can't see your partner on the screen or the map, you can't point at anything, sharing a home is a typed command,
  a death in a gate sends one of you home alone, a Reassessment takes one and leaves the other, a boss's warning ring
  closes for the wrong person, and the tag-along player has no job.
- **The wanted experience.** Two people who always know where the other is and what they mean, who are never split up
  by a rule, who each have a full evening's worth of things to do in the same fight even if only one of them likes
  fighting, and who end the game with one home and one story they made together.

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
| "dies less" | The rider goes down no more than once in ten gate floors; a pair of fighters is split by death less than once a gate | Safe but pointless | Can't fail at all, or the fight is easier for the fighter because someone tagged along |
| "a ton of things to do" | In a boss fight the rider acts on purpose at least every 5 seconds, with four or more different verbs | One button to hold | Waiting for the fight to end |
| "isn't as good as fighting" | The rider earns half to three-quarters of a fighter's XP and a rank marked as won by the thread | Same pay as fighting | No progress at all |
| "extremely smooth" | No moment where the two screens disagree; nothing that only one of them is told | Works with visible hitches | A glitch either player would call a bug |
| "20 times better" | A couple can play an evening without typing a command, opening chat, or asking "where are you?" | Some of that | Today |

### 1.6 Non-goals
Guilds, matchmaking, voice chat, a server browser, trading, PvP, more than four in a party, a second support class,
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
| Provings in a party | Party within 24 blocks comes; +50% Warden health each; a fall wakes you at the door; all down resets the Warden | `hearth/trial/Provings.java:58,167,285,342` | Extend to riders (4.5) |
| Shared Hearth | Owner and members, sister homes from tier III | SPEC "Together", `hearth/Household.java` | Keep, add a second keeper (4.1) |
| The opening | The square holds for the group; stragglers are pulled after 5 minutes; Bram's walk follows the first player | `opening/OpeningDirector.java:50-55`, test `opening_party_wait` | Keep |
| The first boss scene | The Ritual waits for every unawakened player (90 s), kills all at once, each answers "Will you accept?" alone | `docs/proposals/the-ritual.md`, `story/RitualTests.java:133-167` | Keep |
| Bells | A book per player; bells hang at the home and ring for every member; a Lord's bell goes to each player in the run | SPEC §43.14-43.16, test `bells_lord_room` | Keep |
| Monuments and finds | Everyone near when it opens is paid, once per player per site | `finds/Reward.java:39-50` | Keep, add "shown the way" (4.7) |
| The Atlas | Waypoints you share show for your party; "Share with party" sends what you've explored | `atlas/Waypoints.java`, `atlas/Discovery.java:357` | Keep, add live dots (4.2) |
| Dreams | Sleepers in one room share the host's dream | `lucidium/dream/Sleep.java:171-200`, test `dreams_shared` | Keep; check the guest's prize |
| Powers | No power ever hurts another player | `powers/Combat.java:42` | Keep |
| Riding Along | The thread, the catch, 40% hits, half XP, no aggro, no scaling | SPEC §55, `riding/*`, 13 `bond_` tests | The base of the support way (4.4) |
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
| F9 | The rider has no verb of their own | SPEC §55 "Keeps every power and weapon. Hits count for 40%" | The tag-along is a weak fighter. Mario Galaxy's first co-star mode was called thin for the same reason (7) | Verified |
| F10 | The rider earns half XP on a steep curve | `docs/coop/harness/rider_pace.py` | At the hour the fighter is ready for S (level 100) the rider is about level 40 | Computed |
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
| The skiff | Everyone on the deck flies together | A passenger without the coal may still fly; the film plays for all (to confirm, section 8) |
| First gate | Both walk in; the door can offer the thread | The door's card becomes a clear two-way choice (4.4, mockup M3) |
| Gate floors | Shared credit, sealing, scaling | See each other; point; stand back up; targets; the rider's kit |
| The Lord | +50% health each, hoard each, bell each | Target hold, the right ring, stance, a shared finishing film |
| Reassessment | Fighters at the level only | The rider comes; a friend short of the level may ride; rank by the thread (4.5) |
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
3. Riding Along solved safety and fairness and stopped there; it was scoped as one stage of a building plan.
4. No test or tour has two players who behave like players, so nothing that only shows on the second screen was seen.

## 3. Alternatives and recommendation

### 3.1 The support way: three directions
| | A. Leave Riding Along as it is | B. Give the rider a kit on the thread (recommended) | C. A separate support class |
|---|---|---|---|
| What the partner does | Fights at 40% | Gathers, marks, mends, binds, hauls, wards; fights at 40% if they feel like it | Picks a healer at the start and keeps it |
| Safety | The catch | The catch | Its own rules |
| Chosen and changed | At any gate door | At any gate door | Once |
| Cost | None | One new power family, a HUD strip, pay and rank rules | A second progression tree |
| Why not | Fails "a ton of things to do" | | Locks a person into a role; doubles balance work; breaks "you can always switch" |

B wins: it reuses the bond, the catch, the pay rule, the tests, the K menu section and the power system, and adds
only what is missing, which is a job.

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
- **Two riders on one fighter**: later, if friends ask; one rider keeps the balance work small.

## 4. Behaviour and architecture

New module `dev.awaken.together` (the SPEC section it extends is already called "Together"). It owns the party, the
party strip, pings and the pair's shared state, and installs the real `PartyHooks.Access`. `dev.awaken.riding` keeps
the bond and grows the rider's kit. Other modules change only where the audit names a line.

### 4.1 The party (who you're playing with)
- **What it is.** Up to four players who chose each other. Saved per world (`together`, schema 1): members, who
  asked, when. `PartyHooks.members` returns the party, plus, as today, anyone inside your gate run.
  A shared Hearth no longer makes a party by itself; a party usually shares one.
- **Forming it, never by command.** The band's Status page gets a TOGETHER section (it replaces RIDING ALONG and
  holds it): every player within 48 blocks or sharing your home, each with what you can do: `Travel together`,
  `Share my home`, `Ride along`, `Bring along`, `Leave`. Each is a 30-second card for the other. Looking at a player
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
  small state mark (in a menu, in a gate with its floor, folded, down, far), and when they are off screen an arrow on
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
  each player's own setting. A rider can't take it; their thread is in the shot.
- **Provings.** Already forgiving. Add what is said: "Ana is back up" and "Both of you are down. It starts over."

### 4.4 The rider's way (the support role; mockups M3, M4, storyboard S1)
The bond, the catch, the reach and the refusals stay exactly as §55. What's new is the job.

**The idea.** The fighter makes the fight shed light; the rider turns that light into help. Everything the rider does
is a decision about where to be and whom to help, never a reflex. Their own danger is small but real: blasts still
hurt, a fold costs them their light, and stepping out of reach takes the protection away.

**Always on, no input.**
- **Gather.** A monster struck by your partner sheds motes of light (one a blow, three on a stance break, six on a
  kill). They hang for 12 seconds and drift to a rider within 3 blocks. Each is 1 light, held up to 30 (60 at the top).
  Getting to them is movement, the part of the game a non-fighter already enjoys: run, vault, wall-run, rope.
- **What's left over pays.** Light unspent when a floor is cleared becomes a small bonus roll in both Satchels.
- **Second sight.** The rider sees each monster's stance and health as plain bars, and **seams**: caches behind thin
  walls that shimmer only for them (placed by rule in any room, one to three a floor). Opening one pays both.

**Thread powers.** A ninth power family in the existing system, castable only while you ride, equipped in your own
power slots beside or instead of your own powers (which still work, at 40%). Same keys, same bar, same cast system,
same ladders (§36). None needs timing; each has a window of seconds.

| Power | What it does | Cost and wait | The right moment |
|---|---|---|---|
| **Mark** | What you look at glows for your partner for 8 s; their hits on it shed double motes and the first one adds a quarter more stance damage | free, 3 s | The one about to cast, the one behind them |
| **Mend** (hold) | Beads run down the thread: a heart a second to your partner, washing off fire and poison; it pauses 1.5 s whenever they're struck | 1 light a half-heart | After a mistake, between phrases |
| **Bind** | Ties a monster's feet for 4 s (2 for an elite). A boss can't be held, but a Bind on a boss whose stance is broken keeps the opening 1 s longer, once per opening | 6 light, 10 s | The add running at your partner's back; the opening |
| **Haul** | Pulls your partner to your side along the thread. With Sneak, pulls you to theirs | 8 light, 20 s | Out of a red blow; over a gap you can't jump |
| **Ward** | Wraps your partner for 4 s: the next blow is halved and doesn't stagger | 10 light, 15 s | When red shows |
| **Hush** | Monsters that aren't bosses within 8 blocks of you lose interest for 5 s | 12 light, 30 s | When the room is too much |
| **Twin** | Ties two monsters: what one takes, the other feels at 30%, for 8 s | 10 light, 20 s | Two elites |
| **The last thread** | Once a floor, your partner's killing blow leaves them down instead, even alone with you; lift them as in 4.3 | no cost | It decides itself; you still have to reach them |

- **Fairness to the fight.** Mend can't outrun a boss (a heart a second against blows of 4 to 8). Ward halves one
  blow in 15 seconds. Bind never holds a boss. Nothing here adds damage of the rider's own. The fighter's fight
  stays "still challenging"; the rider makes mistakes survivable, which is what a partner is for.
- **The fighter's say.** A setting: "My partner may haul me" (on), or "Ask first" (a half-second thread flash, any
  move cancels it). A haul never interrupts a strike or cast that has begun; it waits up to half a second.
- **The fold.** As §55, and a fold spills the rider's light on the ground where they fell, to be gathered again.
- **Growing across the ranks.** The thread has six **strands**, earned only by riding:
  I at the first ride (Gather, Mark, Mend); II after the first Lord ridden (Bind, seams); III with the D Proving's
  thread task (Haul); IV with C (Ward); V with B (Hush, Twin); VI with A (the last thread, 60 light). Each strand
  also thickens the thread's look. Numbers above are strand I to III; later strands ease waits by a fifth.
- **Pay.** Half a fighter's XP as today, rising to three-quarters with the thread's work on that floor (light
  spent on your partner, marks that were struck, saves). The floor's card says "Your thread's share: 68%". Loot,
  boss pieces and bells stay their own full rolls, as §55. `rider_pace.py`: at half, a rider is level 40 when the
  fighter reaches 100; at three-quarters, 60.
- **Chosen and changed.** At a gate's door: one card, two ways (M3). In the TOGETHER section any time out of a
  fight. Per gate, never for good: tonight's rider can fight tomorrow.
- **Scaling.** A rider still adds no monsters, no elite and no health (§55). With two fighters and a rider, the
  gate scales for two.

### 4.5 A Reassessment, two ways
- **Both fight** (today, kept): each fighter at the level and ready takes the rank; a ranked friend comes as
  practice; +50% Warden health and stance per extra fighter.
- **One rides** (new; reverses §55's refusal). The pair touches the Reading Bell tied. Both wake in the hall. The
  Warden is the fighter's: no extra health, and it never picks the rider while the fighter stands.
  - **The thread's task.** The rider's own test, the same shape for every Warden: keep the thread whole (out of
    reach for no more than 5 seconds in all), go down no more than twice, and do the thread's work for the rank
    (deeds: hearts mended, marks struck, binds, wards that took a blow, hauls; 12 deeds at D, 4 more each rank).
    Shown as a small row of beads filling on the rider's strip. No clock.
  - **Win.** The fighter takes the rank as today. The rider, with the task done, takes the same rank **by the
    thread**: their licence and plate show the letter with a thread through it. Task not done: the fighter still
    ranks; the rider is told what was short in one plain sentence and can go again as often as they like (the
    fighter comes back as practice, which the Proving already allows).
  - **What a thread rank is.** Saved beside the fought rank (`threadRank`). The story, the Tasks page, the town,
    the bells and the next strand read the higher of the two. What makes the world harder (the gates the Director
    rolls for you, fair-damage numbers) reads only the rank you fought for, so a thread rank never puts a person
    in danger alone. Reach the level later and fight the same Warden yourself: the thread comes off the letter.
  - **The gift.** Whatever the Warden leaves (the Ringer's chain is the Tether) goes to the rider too. Movement is
    for everyone.
- **A friend short of the level** may come as the rider, or wait. They are told which, never skipped in silence.
- **Lose.** As today: no fail day, wake at the door, go again.

### 4.6 Levels apart
- **The thread is the first answer**: the weaker player rides in the stronger one's land and can't go down.
- **Match my friend** (a switch in TOGETHER, off by default): within 32 blocks of a party member ten or more
  levels under you, in land at their level, your hits are evened down to their level (the mechanism the story's
  Ascension tiers already use, `story/Ascension`), and you earn at your own level's rate. For the friend who wants
  to play the early game with a newcomer rather than clear it for them. Guild Wars 2 does the same by zone (7).
  It changes hits only: the land's dread look and sound stay on each player's real level, so the weaker player's
  warning never fades because a strong friend is near (world-moods' request, section 8).
- **Pay above your rank**: a fighter in a gate above their rank is paid what a gate of their own rank would pay,
  as riders already are (`Runs.riderReward`).

### 4.7 The journey's joins
- **Joining late.** The newcomer plays the opening (it is the game's first impression and it is theirs), and the
  host gets one line: "Ana has arrived in Carillon." After the Registry they are offered the party. The skiff takes
  them out. From their first landing on, the **Answering Peal** works.
- **The Answering Peal.** A partner rings their handbell for you; you hear it from its bearing wherever you are;
  ring yours back, stand still 5 seconds, and you're beside them. Both in the overworld, neither in a fight, a gate, a
  trial or a dream; every 10 minutes. It is Homeward's rule pointed at a person. No menu.
- **The skiff.** Three rules for the Far Landing session to confirm: a party member without the coal may ride as
  a passenger; the film and the crossing play for everyone on the deck; a helm used while the skiff is away says so
  and brings it back.
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
| Strand, light, thread rank | attachment `awaken:thread` on the player | 1 | kept through death; light is not saved |
| Tell target | synced entity data on the boss | none | cleared with the tell |
| Pings | in memory, 8 s | none | never saved |
| Shown-the-way leads, Ours | the Journal's own book | +1 | optional lists |
All new fields are optional with defaults, so `together_old_saves_load_the_same` holds and gains a twin,
`alone_plays_the_same`: one player, every switch on, the same packets and the same numbers as today.

### 4.9 Performance and the wire
- Stage 0 measures the baseline with the pair kit: packets and bytes per player per second, alone and as two, in a
  gate fight (the test player's channel already records every packet, `AwakenTests.Live.sent`).
- Budget for everything in this plan: under 12 messages a second per player in a fight, under 2 at rest; no
  allocation per tick on the server for the strip (state diffed in place); pings rate-limited; motes are client
  particles spawned from one message per blow with a count, never entities.
- Server tick: the spec's own budget (p95 at or under 25 ms) with two players in a Lord fight on the CI runner.

### 4.10 Failure register
| Case | What happens | Test |
|---|---|---|
| Partner logs off mid-gate | The run goes on for the other; scaling drops at the next room; the thread lets go as §55 | `pair_logout_mid_gate` |
| Partner logs off while you're down | Down ends as a death (nobody can come) | `pair_down_partner_leaves` |
| Both down the same tick | Death for both, as today | `pair_both_down` |
| Haul into a wall, a pit, another floor | Server tests the path; refused with the light given back | `thread_haul_safe` |
| Rider out of reach | §55: penalties stay, protection goes, a toast once | `bond_range` (exists) |
| Fighter dies with the last thread spent | Death; the rider is brought out as §55 | `bond_anchor_dies` (exists) |
| Ping spam | Throttle; the fifth in two seconds is dropped silently | `pair_ping_throttle` |
| A boss's target logs off mid-phrase | Target passes at once, with the notice | `pair_target_leaves` |
| Party member in another dimension | Strip shows where ("in a gate", "asleep"); no arrow | `pair_strip_far` |
| Thread task done but the fighter leaves by the way out | No rank for either; the task's beads are kept for the next try that day | `thread_rank_needs_win` |
| Answering Peal into unloaded or unsafe ground | Lands on the partner's last safe standing spot | `pair_peal_safe` |
| Old world, one player, all switches on | Exactly today | `alone_plays_the_same` |

## 5. Concrete demonstrations
The page carries them as drawings (they are mockups, not screenshots; nothing here is built):
- **M1** the party strip and an edge arrow, in the fighter's view of a Lord fight.
- **M2** pings: a place, a gate door, shared focus.
- **M3** the gate door's card: two ways.
- **M4** the rider's view of the same moment as M1: motes, the thread, the light, the powers bar, the thread task.
- **M5** the TOGETHER section of the band.
- **S1** a storyboard of ninety seconds of a Lord fight, both views side by side, six beats.
- **S2** a Reassessment with one riding, four beats.
- Before and after for four cases: an ordinary gate with two fighters; the disappointing case (a partner who doesn't
  fight); a hard case (levels 8 and 35); a failure (the fighter falls with the rider out of light).

## 6. Evaluation and delivery

### 6.1 How co-op gets tested
- **The pair kit** (`core.test.Pair`, stage 0): two real survival test players in a party, at a site, with each
  one's packets readable. Every test below uses it. Naming: `pair_` (two fighters and the party), `thread_` (the
  rider's kit), beside the existing `together_` and `bond_`.
- **One test per mechanism** (about 40): party forms by card and by the opening; second keeper; strip state on
  change only; ping kinds and throttle; shared focus; tell target and the ring's owner; target hold and its notice;
  per-player reads; stance with two; down, lift, both down, logout while down; the door's respawn and way back in;
  gather, light cap, leftover bonus; each thread power's rule and refusal; haul safety; ward takes one blow; mend
  pauses on a hit; fold spills light; strands; pay by work; the Proving with a rider (no extra health, never picked
  first, task counted, thread rank, the gift); a friend short of the level told, not skipped; Match my friend; the
  Answering Peal's refusals; shown the way; leads from a partner; `alone_plays_the_same`.
- **The journey as a pair** (five longer tests): opening to Registry with two; Ilse to the landing with two; a whole
  E gate, fighter and rider; a D Proving, fighter and rider; a home founded by two and a bell rung for both.
- **Tours with a partner on CI.** One client films while the server plays a second player from a script (a
  **tour partner**: the same real test player the GameTests use, with a short list of moves: go, look, strike,
  cast, use, fall). The client is the fighter in one tour and the rider in the next, so both screens are
  photographed: strip, pings, the door card, a Lord fight from each side, a fold, a lift, a Proving with a rider, the
  Atlas. Same runners, same script language, no second client.
- **Two real clients, once.** A stage 0 probe on a Linux CI runner (16 GB): one dedicated server in offline mode,
  two clients under a virtual display, five scripted minutes, then the logs are read for kicks, errors and
  disagreements. If the clients won't draw there, the probe is dropped and nothing else changes: the tour partner
  covers the pictures and the last check covers the feel. Never two clients on the Mac.
- **The wire.** Packet and byte counts per player from the pair kit, alone against two, before and after each stage.
- **The real test.** Euan and his partner play: the opening, a gate each way, a Reassessment with one riding. Both
  play logs are copied at once (the black box is overwritten by the next session).

### 6.2 Acceptance gates
- Every row of 2.3 has a passing pair test or a tour picture.
- `alone_plays_the_same` and `together_old_saves_load_the_same` pass; the full suite is green (the manager runs it once).
- In the scripted Lord fight the rider's verbs fire at least once each and the fighter's damage taken with a rider is
  within 10% of the fight alone before any Mend or Ward (the rider doesn't make the boss softer by standing there).
- Wire budget of 4.9 holds.
- No picture shows text cut off or a strip over the boss plate.

### 6.3 Stages (each behind a switch in `awaken-together.toml`; off is exactly today)
| Stage | What | Depends on | Builder hours (AI pace) |
|---|---|---|---|
| 0 | Pair kit, tour partner, wire baseline, stance measurement, two-client probe. No behaviour changes | nothing | 2 to 3 |
| 1 | See and say: the party, the TOGETHER section, home for two, party strip, Atlas dots, pings, shared focus | 0 | 5 to 7 |
| 2 | Two fighters: tell targets and the ring's owner, target hold, per-player reads, stance, standing back up, the door respawn | 0; the boss plan's ring if it lands first | 5 to 7 |
| 3 | The rider's kit I: gather and light, Mark, Mend, the rider's strip, pay by work, the door's two-way card | 1 | 6 to 8 |
| 4 | The rider's kit II and the Reassessment: Bind, Haul, Ward, Hush, Twin, the last thread, seams, strands, the thread's task and rank | 3 | 7 to 10 |
| 5 | The joins: late friends, the Answering Peal, skiff rules, shown the way, leads, Ours, Tasks marks, Match my friend, the shared finishing film | 1; Far Landing merged; boss plan built | 5 to 8 |
| 6 | The pair plays; fixes from two play logs | all | as found |
About 30 to 43 builder hours. One builder, in order, coded whole and tested at the end as his rule says; the tour
partner's pictures at each stage are the look check.

### 6.4 Rollback
Each stage's switch off returns that stage to today. Saved fields are optional, so a world that used them loads on a
build without them (parties and strands are ignored, never corrupt).

### 6.5 Risks
- **The rider's kit misses** (the thing most likely to need his eye). Mitigation: strand I is three verbs; the
  pictures show both screens before he plays; every power is data in the power catalog and cheap to change.
- **Hauls annoy the fighter.** The setting, the no-interrupt rule, the 20-second wait.
- **The strip crowds the screen.** It is one line per person, hides when alone, and M1 shows it against the boss plate.
- **Pings on the lock key misfire.** Lock always wins on a monster; a ping never fires while a lock is held.
- **Thread rank feels like being carried.** The task, the mark on the letter, and the fought rank staying the
  measure of danger.
- **Three other plans move the same ground** (boss ring, Far Landing, pets). Section 8; no file of theirs is edited
  before they merge.

## 7. Research
| Claim | Source | What it means here |
|---|---|---|
| Mario Galaxy's second player collects star bits and stuns enemies with a pointer and can't be hurt; guides call the first game's role thin ("stop enemies or gather star bits, but little else"), and Galaxy 2 widened it | mariowiki.com/Co-Star_Luma; imore.com (Co-Star Mode guide) | Safety plus collecting is the right base; the role must be wider than two verbs, which is why the thread has eight |
| Apex Legends' one-button, context-aware ping lets a team play with no microphone; Respawn tested with voice banned | gamedeveloper.com (Respawn played with muted mics); pcgamer.com | One input that says the right thing by what you look at |
| Minecraft Dungeons: a downed player is revived by a teammate standing near; the team only loses a life when all are down; enemies scale with players | minecraftdungeons.wiki.fextralife.com/Multiplayer; gamewith.net | Standing back up is what Minecraft's own co-op game does |
| Guild Wars 2 lowers a stronger character to the area's level so friends can play early zones together | wiki.guildwars2.com/wiki/Dynamic_level_adjustment | Match my friend, as a choice |
None of these was measured in AWAKEN; they pick the direction, the tests and his play decide.

## 8. Other plans in flight
Asked by message on 11 Nov; answers are folded in below as they arrive.
- **Far Landing** (`spires-and-journey`): passengers without the coal, the film for everyone on deck, a helm used
  while the skiff is away, anything keyed to one player, a way straight to a friend.
- **The Whole Boss Fight** (`boss-experience-plan`): whether the new ring knows who a blow is for; the target hold;
  more than one client filming one finishing blow.
- **Aya and Dubu** (`hearth-pets-plan`): who may ask a pet along in a shared home; pets and the skiff and gates.
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
