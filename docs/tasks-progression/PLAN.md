# The Next Thing: how Tasks leads you through the whole game

Plan, 9 Nov 2026. Nothing here is built. Euan's process (`.claude/PLANNING-PROCESS.md`) and UI prompt (`.claude/UI-EXCELLENCE.md`).
Approval page: published from `page.html`. Measured proof: `proof/`.

Labels used throughout: **Measured** (a GameTest or a count from the code), **Read** (in the code or the SPEC), **Research**
(an outside source, cited), **Proposal** (what I recommend), **Hypothesis** (what I think but haven't shown).

---

## 1. Understanding and success contract

**The job.** At any moment in a playthrough, open K, see a short list, and know what you can build, add or improve next.

**The pain.** Each system was built apart and registers its own steps. Some unlocks are told nowhere (reach C-rank: nothing says
the Storehouse exists). Others crowd the list (level 3 already shows 20 rows). The list has no idea what else is on it.

**What Euan proposed** (ideas to test, not the design): a maximum per thing, "like 1 or 2, or maybe 3 per thing"; look at how the
games people love do it; progression that "naturally happens as you do certain tasks".

**Hard constraints.** Discovery stays: bosses are never pre-announced and rumours give kind and region, never the method (SPEC
§43.5). No daily task lists (`goals-before-features`). No new discovery toasts (§26). No automation on the chill side. Every new
progression system must reach Tasks (`new-systems-in-tasks`). Plain natural speech on every line (`plain-dialogue`). Keep designs
multiplayer-safe. Don't touch other plans' work.

**How we'll recognise it.**

| | Strong | Weak | Unacceptable |
|---|---|---|---|
| Length | Now fits one screen with no scroll on the MacBook at his GUI scale; a tab holds at most 3 rows | Capped, but rows are generic | Still 20+ rows |
| Coverage | Every unlock has a teaching step or a written reason it hasn't | The known gaps fixed, new ones free to appear | The next plan reopens the hole |
| Lead-on | Finishing a step puts the next in the same place | Next appears somewhere else | List reshuffles while you read it |
| Discovery | A rare part is told as kind and place; bosses stay hidden | Some spoilers | A list of every secret, or coordinates for everything |
| Stuck | Always at least one thing you can do now | Rows that all wait on rank | Empty page or a row that can never finish |

**Non-goals.** No quest book or tree. No new toast. No new notification tier. No new daily or weekly anything. No reward attached
to finishing a task (trails keep their small share of a level). No change to any unlock's rules, apart from telling them.

**Must not regress.** One pin. Where-lines. Armour previews on Choose. The Hearth page's "next" step. Trails stay. An old save
loads and its pin survives.

---

## 2. Verified audit and diagnosis

### How the page works today (Read)

`Goals` sources → `TaskBook` (merge, sort by path, priority, key) → `TaskPage` / `TaskTabs`.
Now = Do this next + one main row per goal + Out there + **every** optional row under When you want. Tabs show everything of that goal.
`Goals.all` skips a failing source with a warning. No limit exists anywhere in that path.

| Component | What it actually does | Consequence | Verdict |
|---|---|---|---|
| `Goals.Step` | key, goal, title, why, needs, where, path, optional, priority, choices, covers | Good object: nothing to replace | Keep, extend |
| `TaskBook.top()` | path first, else priority; `URGENT=90` jumps the path | Lessons at 90/92 hold Do this next from C to A | Fix |
| `TaskPage` Now | prints every optional row | 13-14 "when you want" rows from level 7 | Replace |
| Tabs | every row of the goal | Hearth has 12 rows; six are trails | Replace |
| `HearthNow.nextRoom` | hard-codes `false` for Lookout, Ore Shed, Smeltery, Rookery, Foundry, Storehouse | They can never be suggested | Replace |
| `KinGoals` | `SHOWN=2` in fixed order | Totter is 9th of 16, and the Storehouse needs one | Reorder by what your home needs |
| Trails | one optional row each, always | Fine as content, wrong as competition for the list | Keep, fill empty slots only |
| `journey_ledger` | fails when an item has no way in | The right kind of test, for the wrong question | Copy for "no way to learn" |

### Measured baseline (GameTest `tasks_probe_stops`, `proof/rows-at-each-stop.txt`)

A deliberately minimal player: empty pack, no beasts read, no bell leads, points unspent, nothing worn. A real player would have **more**.
The probe placed no armour stand, so `hearth/forge` stays open at later stops.

| Stop | Rows | Optional | Hearth tab | Now page |
|---|---|---|---|---|
| A: just awakened, L1 | 7 | 2 | 0 | 5 |
| B: Hearthfire + bedroom, L3 | 20 | 9 | 7 | 14 |
| C: Anchor + Armoury, L7 | 24 | 13 | 12 | 17 |
| D: D-rank, L12, tier 1 | 27 | 14 | 12 | 20 |
| E: C-rank, L26, tier 2 | 28 | 14 | 12 | 20 |
| F: B-rank, L46, tier 3 | 28 | 14 | 12 | 20 |
| G: A-rank, L72, tier 4 | 28 | 14 | 12 | 20 |

The count stops rising at 28 while the game keeps opening things, because nothing new is ever told. The Hearth tab has 12 rows and
still never names the Storehouse, Foundry, Ore Shed or a Forge tier at any stop.

### Where the useful information disappears

| Symptom | Cause | Evidence | Against | Smallest test | If fixed |
|---|---|---|---|---|---|
| Too many rows | No limit; trails all print | Measured table | Rows are real tasks, could be fine in tabs | Count rows per stop | Now 20 → at most 7 |
| Storehouse untold | No step, `nextRoom` false, Totter 9th, keystone source untold | `HearthNow:169`, `Sources.declare` only | Kin need line mentions Totters | Probe at stop E | The reported gap closes |
| Forge tiers untold | No Goals step | grep, only a trail step at the far end | Trail "The Forge" | Same | |
| Stuck on a lesson | Priority 90/92 ≥ URGENT | Stops D-G, Do this next is a lesson | Lessons are worth learning | Skip the Veil, read top | Next step reflects the path |
| Rows that can't finish | `Settlers.learn` has no callers | grep | Admin `forceKnow` works | `settlers:1` token never set | Trail can be finished |
| Wrong titles | `any` only checks worn armour | Stop G: "Choose your first armour" at A-rank | Rare in real play | Same probe, armour worn | |
| New plans reopen the hole | Nothing checks | Six plans in flight | Standing rule exists | Ledger test on purpose-removed step | No silent unlocks |

**Hypothesis, not shown:** that a cap of 3 feels right in play. No playtest has run. Stage 2 stops for Euan on exactly this.

### The gap table

The full table with every row and its fix is on the approval page and in the `GAPS` data of `page.html`. Totals: 74 things checked. Told 24, Thin 32, Silent 7, Broken 5 (rows that exist but mislead or can't finish), Hidden on purpose 5, Not built 1.

Things that matter most:

- **Silent:** Storehouse, Foundry, Forge tiers II and III, armour patterns for non-Hunter sets, power levels, every Ascent rung until
  you already have it, the Lookout outside a trail.
- **Broken:** Your Village trail (`settlers:N` can't finish, and SPEC §43.6 says nobody moves in), lessons holding Do this next,
  the War Anvil's line at E-rank asks for a Forge it doesn't need, "Choose your first armour" at higher ranks (only if you're wearing nothing), stale trail text (The Forge says "Armory"; Your Hearth names
  Oren's cottage; Training Grounds' last step has no done token).
- **Hidden on purpose (and it should stay):** bosses, Provings, events, gate rooms, Truths and Titles (they come true by themselves).
- **Not built:** the Works, Shrine and Ringing Gate named in JOURNEY.

---

## 3. Research that decided something

Each answers a question this plan had to settle. Full citations on the page.

| Question | Finding | Used for | Where it wouldn't fit |
|---|---|---|---|
| How much at once? | Minecraft shows up to two advancements ahead of a completed one. ACNH shows five Nook Miles+ tasks and replaces each at once. FFXIV pins the next main quest, plus side quests that unlock features | One ahead (Then:), a small visible set, feature-unlocking steps singled out | ACNH's daily churn breaks "no daily task lists" |
| Does choice help or hurt? | Scheibehenne 2010: over 63 conditions the mean overload effect is about zero. Chernev 2015: overload appears with complex sets, unclear preference, and effortful choice | Tightest for new players, more for experts (Later) | Neither study names a number for quest lists |
| What can people hold? | Cowan 2001: about 4 chunks, range 3-5 | Upper bound: 3 rows plus the Now pick | Lab recall of items, not game goals |
| How to show building goals? | Palworld's Palbox lists what the next base level needs, "a guided tour of the technology tree"; stalls when tech lags. Satisfactory: Tier 0 in strict order, then you pick milestones and each lists exact items | A tier's needs shown exactly; strict at first, choice after | Palworld's list is the only goal; ours is one of three |
| Telling without spoiling? | Valheim reveals recipes when you pick up a material, and boss drops open the next biome. Elden Ring shows Guidance of Grace at some sites only | "Doing one thing reveals the next". Leads give kind and place | Elden Ring's no-log approach is criticised as cumbersome |
| What makes it a checklist? | Opinion pieces on Ubisoft-style maps (NME, GamingBolt, PC Gamer); BotW moved towers to encourage sidetracking | Don't reward completion, don't show a total, keep hidden things out | Opinion, not data |
| Does a quest tree fix it? | FTB Quests: dependency hide settings and team soft-locks are common complaints | Steps stay derived from world state | Hand-authored packs work when one person curates them |
| What about autonomy? | Ryan, Rigby, Przybylski 2006: autonomy and competence predict enjoyment. Kivetz 2006 goal gradient: effort rises nearer the end | One row, several options. READY first, closest-to-done first | Effect sizes vary across games |

**The BotW "80%" figure I found could not be traced to a primary source, so it isn't used.**

---

## 4. Alternatives and recommendation

| | A: Targeted repair | **B: One short list (recommended)** | C: Quest-book tree |
|---|---|---|---|
| What | Add missing steps; trim the view | Threads, a cap in the book, a ledger test, one rule | Hand-authored tree of all goals |
| Fixes the Storehouse | Yes | Yes | Yes |
| Stops the next hole | No | Yes (test) | No (hand upkeep) |
| Discovery | Same as today | Three voices, kept hidden things | Spoilers: a tree shows what's coming |
| Failure risk | Low | Medium: the book changes | High: soft-locks if a flag is missed |
| Upkeep | Each plan on its own | Each plan adds one line per step | One file everyone edits |

A's fixes are needed anyway, so they become B's Stage 0. C loses because a tree needs flags that drift from the world, and it is the
list Euan asked us not to make. Evidence that would change this: if the cap feels wrong at Stage 2, change the number; if threads
can't describe a real system at Stage 3, add a thread rather than widen the cap.

---

## 5. The design

### The tension, stated plainly

Euan wants "direction without telling" and "you always know what you can add or build". Both hold, because they apply to different
things. §27.13 already draws the line: "what to do next and where each piece comes from is said plainly; what the world holds beyond
it is still found".

| Voice | Applies to | How it reads | Example (game strings) |
|---|---|---|---|
| **Plain** | Anything you build or make at home; every Hearth, Forge, armour and weapon step | Name, needs, where | "Grow your Armoury into a Forge. An anvil, the Forge and an armour stand in the same room…" |
| **Lead** | Rare parts, beasts, bells, new ways of moving | Kind and type of place. No coordinates until the game has given you a bearing | "Every town's granary keeps one against a ground-floor wall." |
| **Hidden** | Bosses, Provings, events, gate rooms, story, Truths, Titles | Never listed | (nothing) |

The one case that needs Euan's call: whether a rare part's source may be named at all (decision 4).

### The cap (Proposal)

**1 Do this next. 1 per thread. 3 rows per tab.** Now holds at most 1 + one per goal (5) + 1 Out there = 7, and When you want leaves Now.

- Why a thread: a tab can hold different kinds of work. The Hearth tab has three (grow the home, add to it, bring beasts). Three is the
  number of real lines of work, which is why 3 fits there and why it doesn't come out of a study.
- Why not 1 or 2: 2 hides one of the three Hearth lines. 1 turns the Hearth into a corridor. Why not 5: measured rows show 1-4 threads
  per tab, so 5 would only be filled by padding.
- Evidence honestly: no study names 3 for quest lists. Cowan bounds it, Minecraft and FFXIV sit near it, and the count of real threads
  decides it. It is a number to test in play, and one constant to change.

### How one task leads to the next (Proposal)

1. A step belongs to a **thread**. A thread shows one row, its lowest order first. Finish it and the next one takes the same place, with NEW.
2. Under a plain row, a quiet **Then:** line names the next step, one ahead (Minecraft shows two; one is enough with the Later fold).
3. **Add to your home** is one row with up to three options instead of three rows. Pick one and it becomes that step; "Change" goes back.
4. **READY** when every need is met, sorted first. After the main path, closest-to-done first.
5. **A rank footer**: "At B-rank your Forge can go to Tier III." Names what the next rank opens for your home, replacing the "At level N" row.
6. **Later · n** at the foot of each tab, read-only with Pin, for anyone who wants to look ahead.
7. **Set aside** with Undo takes a row off the list for now.
8. Tabs appear the first time they have a main row, then stay.
9. Trails only fill empty slots, marked WHEN YOU WANT, never on Now.

### The one rule other plans follow

```java
Goals.Step.of("hearth/storehouse", Goal.HEARTH, "Build a Storehouse")
    .thread("hearth.build", 40)          // one row per thread shows, lowest number first
    .lead()                              // Plain unless you say otherwise; urgent() is rare
    .teaches("room:storehouse", "block:awaken:storehouse_drop")
    .opens("Drop everything in one place; a Totter shelves it")
    .build();
```

And one test, `tasks_ledger`, walks everything the game can unlock: `RoomType`, `WorkPlans.PLANS`, Hearth tiers, Forge tiers, `KinKind`,
`ArmourSet` by rank, `Ascent.Tier`, the level road, and Awaken's block entities and menus. Each needs a step that `teaches` it, or a line
in `Teaches.quiet(id, reason)` saying why it stays quiet. Failure message:

```
tasks_ledger: 2 things can be unlocked but nothing teaches them:
  room:storehouse (hearth.RoomType.STOREHOUSE)
  block:awaken:storehouse_drop
Add .teaches("room:storehouse") to the step that leads to it,
or Teaches.quiet("room:storehouse", "why the player finds this alone").
```

Cap enforcement lives in `TaskBook.shown`, so no plan can exceed it, and every plan gets it for free.

### Threads per tab

| Tab | Threads |
|---|---|
| Hearth | `hearth.home` (tiers), `hearth.build` (rooms, works, Forge tiers), `hearth.beasts` |
| Armour | `armour.set`, `armour.enchant`, `armour.pattern` |
| Weapons | `arms.forge`, `arms.craft`, `arms.pick`, `arms.enchant` |
| Bells | tower, then each lead is its own thread (shows tower + 2 leads) |
| Gates | `rank.next`, `rank.power`, `rank.move`, road goals |
| Out there | lessons, hunts, the deep, the wider world: one on Now |

Weapons has 4 threads and a cap of 3: the fourth waits in Later until a slot frees.

### Behaviour contract

Trigger → inputs → processing → output → actions → state → failure.

- **Trigger.** Same as today: page open, 20 ticks; otherwise 100. `CACHE_TICKS=10`.
- **Inputs.** Each source's `Step`s (derived from world state, no quest flags), `TaskState`.
- **Processing.** Book merges as today → groups by thread → keeps the lowest order in each → drops aside rows → picks slots in order: held
  slots first, then path, READY, closest-to-done → at most 3 per tab → Later gets the rest.
- **Output.** Same `StepRow` plus `then`, `ready`, `voice`, `isNew`, `thread`; `later[]` per tab; footer.
- **Actions.** Pin, Choose, Set aside, Undo, Open.
- **Priority when these conflict.** Urgent > Pin > path > held slot > READY > closest-to-done.
- **Invariants.** Never more than the cap. A row in a held slot isn't reordered while the page is open. Hidden things are never rows. Nothing
  on the list is impossible to do. No row names a boss.
- **Partial or empty.** An empty tab says what opens next. If every slot is waiting on rank, the never-stuck nudge names the nearest thing you
  can do now.

### State

`TaskState` schema 2, additive: `held` (goal → threads in slot order), `aside` (step keys with time), `tabsSeen`. Schema 1 loads as empty and
slots fill on first look. No world data changes. Steps stay derived, so nothing can drift. Deleted thread ids fall out of `held` on read.

### Failure register

| Case | Detection | What the player sees | Recovery | Test |
|---|---|---|---|---|
| A source throws | `Goals.all` catch | Other goals fine, a warning in the log | Next tick | Existing |
| Pinned step gone | Key not in book | Unpins | Automatic | `tasks_slots_stay` |
| Empty tab | Zero rows | Says what opens next | | `tasks_caps` |
| All slots wait on rank | Every row is rank-locked | Nudge to the nearest doable thing | | `tasks_caps` |
| Row can never finish | Token unreachable | Never shipped | Fails CI | `tasks_ledger` |
| Thread renamed by a later plan | Not in `held` | Slot refills | Automatic | `tasks_slots_stay` |
| Multiplayer | Per-player state; Hearth rows read the shared home | Each player sees their own list | | Existing per-player attachment |
| Old save | Schema 1 | Filled on first open | | Load test |

Privacy: not applicable, single-player data on the player's own save. Performance: same cached build, no per-tick work added, measured by the same
`CACHE_TICKS`. AI: not applicable.

---

## 6. Before and after (same inputs)

Illustrative outputs; the "today" column is measured.

**Ordinary, first hour.** Level 3, Hearthfire and bedroom. Today: 14 rows on Now, 9 of them "when you want". Proposed: Build your Armoury, then one
per tab. You finish the Armoury: Choose your first armour moves up, and Make your Bedroom a Sanctuary appears in the Armoury's slot marked NEW.

**The Storehouse.** C-rank, level 26. Today: nothing. Proposed: the Hearth tab's Add to your home row offers Storehouse, Foundry or Kitchen. Pick the
Storehouse and it lists The Drop, a Ledger desk and a Totter, and says "Every town's granary keeps one against a ground-floor wall" for the part you
can't make at home. It names no coordinates and adds no new lock.

**Ambiguous, you skipped the Veil.** Today: "Meditate 10 s, let go of G" holds Do this next from C-rank to A-rank. Proposed: the lesson sits in the Out
there slot on Now, Do this next follows the main path.

**Missing data, all slots wait on rank.** Today: a list of rows you can't act on. Proposed: rows say what they wait for, and the top line names something you
can do now.

---

## 7. Evaluation and delivery

**Acceptance gates**

- `tasks_caps` at the same seven stops as the baseline: Now ≤ 7, tab ≤ 3, Do this next is never a lesson.
- `tasks_ledger`: fails on a purpose-removed step; passes when all are taught or quiet.
- `tasks_slots_stay`: finishing a step leaves other rows in place; a next step takes the same slot.
- Trail tokens are all reachable.
- Regression: pin, Choose previews, Hearth page next, an old save.
- Human: Euan plays after Stage 2. A GameTest can't judge whether 3 feels right.

| Stage | What | Go/no-go | Roll back |
|---|---|---|---|
| 0 | Defects: Your Village rewrite, lessons not urgent, the E-rank anvil line, titles, stale trail text | Tests | Each alone |
| 1 | Book, cap, page: threads, `shown`, Then, Later, Set aside | `tasks_caps`, `tasks_slots_stay` | Config `tasks.cap=off` shows the old list |
| 2 | Hearth coverage: `hearth.build`, Storehouse, Forge tiers, kin reorder | **Euan plays** | Config |
| 3 | Ledger, all sources onto threads, new steps (cores, power levels, Ascent lead, patterns, mounts) | `tasks_ledger` | Quiet list |
| 4 | Sibling plans plug in, polish | Each plan's own gate | |

Effort at AI pace: one builder, roughly 4 to 8 hours of work (estimate; the unknown is how many Ledger misses Stage 3 turns up).

**Dependencies.** Section numbers on sibling branches collide (49, 50, 52); this plan proposes SPEC §53 and rechecks at build time. Forged Pick, Gates as Worlds,
Powers in Full Voice, Riding Along, Made by Hand and the Storage plan each add `.thread(...)` to their steps. The Storage plan had not said which steps it needs
when this was written; its steps slot into `hearth.build` under the same rule.

---

## 8. Challenging this plan

- **Is this the real frustration?** He hit C-rank and nothing told him about the Storehouse, and wants to always know what's next. The ledger answers the first, threads and the cap answer the second. Row counts are only the symptom.
- **Does it cut effort or move it?** Fewer rows, and one less decision per tab. The risk is that Later becomes a second list to read; it's closed by default and read-only.
- **Could something simpler do most of it?** Stage 0 plus a view-only cap would shorten the page, but it wouldn't fix the gaps or stop new ones. The extra cost over that is one builder line per step and one test.
- **What would show this is wrong?** If Euan plays Stage 2 and keeps opening Later, three is too few. If he ignores rows that aren't the top one, three is too many. If a real system needs more than a thread to describe it, the model needs another idea.
- **What happens with stale or missing data?** A source that fails drops out for that tick; steps are derived, so there's no flag to go stale.
- **Can he correct it?** Pin, Choose/Change, Set aside/Undo, Later, and a config switch that brings the old list back.
- **Can we prove no loss?** `tasks_caps` and the seven-stop baseline, the Choose previews and pin kept, and every row that disappears from a tab still reachable in Later.

---

## 9. Decisions for Euan

All sixteen are on the page with Yes/No, each with a recommendation. Summary:

1. Cap: 1 next, 1 per thread, 3 per tab.
2. Now drops When you want and has at most one Out there row.
3. Three voices: Plain, Lead, Hidden.
4. Rare parts told as kind and place, no coordinates.
5. "Add to your home" as one row with options.
6. A "Then:" line.
7. A footer naming what the next rank opens.
8. Later fold.
9. Set aside.
10. Tabs appear when first used.
11. Trails only fill empty slots.
12. Rewrite Your Village around beasts and works.
13. Lessons aren't urgent.
14. The ledger test fails the build.
15. Storehouse told at C-rank, no new hard lock.
16. Build in this order; Euan plays after Stage 2.

## Proposed SPEC section

§53 "One short list" (recheck the number at build time): the book's cap, threads, the three voices, `Teaches`, `tasks_ledger`.
