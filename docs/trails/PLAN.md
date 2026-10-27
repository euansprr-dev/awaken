# Trails: optional guidance for everything (plan for Euan's approval, 2026-10-27)

Status: **plan only, nothing built.** Branch `pack/trails-plan`. Approval page: `docs/trails/page.html` (published as an
Artifact, link in the hand-back). Labels: **VERIFIED** (read in this repo at 2a0b6da3), **PROPOSAL** (not built, nothing
measured), **UNVERIFIED** (the first builder checks it).

## 0. What Euan asked, and the one-line answer

> "Look at everything the hearth does overall. And then let's make a plan over how we can create like a kind of quest
> system ... in the K menu ... whenever you start something new, so it doesn't get overwhelming. Obviously, like the
> moment you place your hearth, you unlock a whole hearth discovery program, basically, that you don't have to follow.
> ... when you go in it, you can click it, and then it creates the side checklist for that thing ... if you want to be
> guided, you can, but it's not like a forced thing. And then we should make it in a way that it can be built into other
> stuff ... so that whenever you don't know what to do at all, you can always go in there and find, okay, these are the
> things that are available for me to do right now ... not just for the hearth, really plan this for everything."

**The answer: Trails.** A *trail* is a short, optional list of things to try in one part of the game. It opens at a
moment (you set your fire down), waits in the K menu's Journal page, and does nothing until you choose **Follow**. A
followed trail puts its next few steps on the checklist card top right and its current step on the Now line. Progress is
read from the world, so a step you already did is already ticked, and a step you do without following still counts. The
top of the Journal page always answers "what can I do now?" with the three best next steps across every trail you have
open. One engine, one data format, and each area of the game adds its own trails as plain JSON.

## 1. What the Hearth does (the inventory the first trails come from)

**VERIFIED** from `docs/modules/hearth.md` and the code in `dev.awaken.hearth`. Every row is something a player can do or
find, which is what makes it a step.

| Group | What is there | Where the code reads it today |
|---|---|---|
| The carried flame | The Ember (given by Ilse or on awakening); set it on open ground to make the Hearthfire (tier 0: radius 24, respawn, rest heals, calm land); name your ground; lift it again until the first room stands | `Embers`, `HearthService` |
| The Anchor and its tiers | A Lord's Heart (or the Anchor) turns the fire into the Anchor (tier I, radius 48); tiers II to V at D, C, B, A (radius 56 / 64 / 80 / 96); using it opens **Your Hearth** (rooms, town, next upgrade) | `Upgrades`, `TierRules`, `guide/Guide` |
| Reading rooms | Every closed room is graded Bare / Snug / Fine / Grand from light, warmth, room, care and detail; names itself; says what to fix; says why it is not recognised | `room/Reading`, `RoomScanner`, `guide/WhyNot` |
| Fifteen kinds of room | Shelter, Bedroom, Sanctuary, Kitchen, Forge (Armory), Study, Greenhouse, Stable, Hall, Bell tower, Lookout, Trophy Hall, Workshop, Barracks, Mine head, each with a gain | `RoomType`, `RoomEffects` |
| Comfort and Rested | Comfort 0 to 12 from your bed, warmth, table, soft things, plants, land; waking in your own bed gives Rested (energy and craft rate) | `Comfort`, `Rested` |
| Fracture heals at home | Stand still out of combat, or sleep in a Sanctuary | `HearthService` |
| Oren's cottage | A free first Sanctuary built by Oren, once | `guide/Cottage` |
| The town | Oren, Mira, Teodor and Sun arrive by rank, each with a service (tempering, a rumour a day, reading your Codex, clearing Fracture); celebrations at each rank | `town/` |
| Your village | Settlers move in when a Snug spare bedroom stands; they work with what you bring home (commissions, harvest, tonics, the miner's haul); tiers by rooms and people (1 / 2 / 4 / 8 / 12) | `settle/`, `TierRules` |
| The Forge | Tempering (+1 damage a level, armour too), Forge tiers II (D) and III (B), the Armory room (anvil + Forge + armour stand) tempers higher; now also the Armoury door (draw a Blueprint copy, Raise, attune) | `Upgrades`, `ForgeBlock`, arms |
| Trophies | The first of each boss drops a trophy; a Trophy Mount shows it; a Trophy Hall pays +1% damage and +0.5 health per different boss | `trophy/` |
| Echoes | Bind a fallen elite, warden or boss (B near it, 10 s); a field army and a roster; role pads set Guard, Haul, Farm, Mine, Train; Echoes work while you are away | `echo/` |
| Training Grounds | Start Gate, numbered Checkpoint Rings, Finish Gate, Target Dummies; laps, records, a ghost of your best | `training/` |
| Breaks and the Bell tower | Gates that break near home are held by Guards; the Hearth shields you during one; a bell warns two days ahead | `Breaks` |
| Rank trials | The Monolith and the Spire Board run the Reassessment trials in their own dimension | `trial/` |
| Home crafting and kits | A crafting table at home pulls from chests within 8 blocks; drainpipe, trellis, window box, ivy, hoist beam | `HomeCrafting`, `kit/` |
| Requests | The Anchor posts small requests for the household | `request/` |

**What teaches this today** (VERIFIED): the Now line (`HearthNow`, one line at a time: "Use the Anchor for a guide", then
the next room with live ticks, then why a room is not recognised), the **Your Hearth** screen (each room's checklist and
recipe grids), toasts when a room is recognised, Oren's cottage offer, and the recipe-book unlock. That is good once you
are inside the Hearth screen. Everything else (Echoes, Trophies, the Training Grounds, the village, the Forge's Armoury
side, rank trials) has no pointer at all until you stumble into it. That is the gap Trails fills, for the Hearth first
and then for the rest of the game.

## 2. What guides today, and why it is not enough (VERIFIED)

| Channel | What it does | Why it does not answer "what can I do now?" |
|---|---|---|
| Now line (`NowHooks`) | One line, the top suggestion, with a compass | One thing at a time, chosen by priority, no way to ask for more |
| Task card (`Checklist`) | Top right: your body's four moves, then the tracked chain or the Armoury's Track | Shows what you already chose; offers nothing |
| Journal page | Filed finds, waiting promises, chains, Directives, Daily Regimen, Records, Requests | A log, not a menu of things to try |
| Directives and Regimen (`progression`) | Random small tasks, some with progress | Not curated, not about discovery, not an ordered path |
| Quest chains (`living`) | Named people's chains with steps | Story, one at a time; not for "how do I use the Forge" |
| Promises ("What waits") | Locks you met, called back when you can open them | Only locks |
| Tips (`TipHooks`) | A line the first time a context comes up | Once, then gone |
| Horizon / Atlas | Sites and places | Places, not deeds |

Each is right at its job. None lets a player who is out of ideas open one place and see a list of things they could do,
pick one, and be walked through it. Trails is that place, and it feeds the channels that already exist instead of adding a
new HUD.

## 3. The design

### 3.1 Principles (each is a test a feature must pass)

1. **Optional, always.** A trail never starts by itself, never blocks anything, never repeats a toast, and can be hidden.
   The only thing that changes the screen is the player pressing **Follow**.
2. **Opens at a moment, not all at once.** Placing the Ember, reaching Tier I, binding the first Echo, taking the first
   Blueprint: each opens its trail. At most two new trails are announced per play-day; the rest wait quietly in the list.
3. **Never lists what you have not met.** A trail whose lock you have not seen is invisible. One whose lock you have seen
   (a rank, a level) shows as a silhouette with its need, the same rule as "What waits" (The Way Forward, pillar 1).
4. **Read the world, do not ask the player to report.** Steps are conditions on state (a room of this kind stands, this
   counter is at least 1, you hold this, you know this style). A step you did before the trail opened is ticked the moment
   it opens. A step latches once seen, so breaking the room later does not un-tick it.
5. **One card, one voice.** The task card shows one thing. Following a trail replaces what was on it; the previous thing
   is one press away in the Journal. Nothing new is added to the HUD.
6. **Always an answer.** The top of the Journal page lists the three best next steps. If nothing is open, it says what
   opens the next trail and where to find it. The clerk's and Hale's "Anything for me?" lines read the same list.
7. **Plain speech.** Every line a player reads is complete natural sentences, read aloud before it ships (Euan's rule).
8. **Multiplayer-safe.** State is per player. Steps about a shared Hearth read the household's Hearth; a step the owner
   already did shows as done for a member.

### 3.2 Anatomy of a trail

A trail is one JSON file, `data/awaken/awaken/trail/<id>.json` (schemaVersion 1), loaded on both reloads like quests and
bounties. **PROPOSAL.**

```
{
  "schemaVersion": 1,
  "title": "Your Hearth",
  "area": "home",                       // home, weapons, gates, powers, body, world, deep, story
  "blurb": "Make a home: a fire, a roof, a bed, and the town that comes with them.",
  "opens": ["hearth"],                  // all must hold; the Requirements tokens plus the ones modules add (3.3)
  "after": [],                          // trails that must be finished first
  "order": "free",                      // free: any step; linear: one at a time
  "steps": [
    { "id": "open_guide", "text": "Use your fire to open Your Hearth.", "done": ["counter:awaken:hearth_guide_seen"] },
    { "id": "first_room", "text": "Build a closed, roofed room next to it.", "done": ["room:shelter"],
      "hint": "Your Hearth says what a room is missing.", "tip": "hearth/first_room" },
    { "id": "sleep", "text": "Sleep in your own bed at home.", "done": ["counter:awaken:rested_once"], "key": null }
  ],
  "finish": { "line": "Your Hearth is a home now.", "levels": 0.05 }
}
```
Fields a step may carry: `text` (with `{key}` where a key cap is drawn, like the task card), `done` (tokens, all must
hold), `show` (tokens that must hold before the step is listed, so a long trail reveals itself), `where` (a kind of place
for the compass: `hearth`, `forge`, `nearest:gate`, `nearest:camp`; only places the player has met), `hint` (one plain
sentence behind a "Why?" tap), `tip` (a tip id offered once when the step becomes current), `manual` (the step may be
marked done by hand: the escape hatch for anything the world cannot read).

### 3.3 Conditions: tokens, owned by the module that knows the thing

The existing `Requirements` tokens are the base (VERIFIED): `level:N`, `rank:X`, `counter:id`, `unlock:flag`, `hearth`,
`path`, `power:id`, `family:x`, `dash`, `movement`, `active`, `forces`, `tier:name`. Trails adds a small registry,
`TrailsApi.condition(prefix, predicate)`, so a module adds a family of tokens in a line and owns their meaning:

| Family | Owner | Examples |
|---|---|---|
| `room:` `grade:` `hearth_tier:` `settlers:` `cottage` `echoes:` `pads:` `forge_tier:` | hearth | `room:sanctuary`, `grade:fine`, `hearth_tier:2`, `settlers:2` |
| `style:` `forged:` `marked` `raised` | arms | `style:spear`, `forged:beastbound` |
| `holds:` `crafted:` `used:` | core (inventory and counters) | `holds:awaken:lord_heart`, `crafted:awaken:forge` |
| `gate:` `lord:` | gates | `gate:cleared`, `lord:killed` |
| `met:` | atlas | `met:camp`, `met:first_gate` |

Most steps need no new code: the game already counts nearly everything in `awaken:` counters (`docs/COUNTERS.md`, e.g.
`trophies_placed`, `echoes_bound`, `training_laps`, `gear_tempered`). Where a step has no counter, the owning module adds
one counter at the place the thing happens (one line) or a token that reads existing state. The one new hook the Hearth
pilot needs is `HearthHooks.facts(player)`: a small record (rooms by kind and grade, tier, settlers, Forge tier, cottage,
echoes) built from `HearthRecord`, which already holds all of it. **UNVERIFIED** until the first builder opens
`HearthRecord`; `docs/modules/hearth.md` says it is saved with rooms, `folk`, `forgeTier` and `cottage`.

### 3.4 Where the player sees it

**Opening (once).** When a trail opens, one IMPORTANT-tier toast through the notification director (topic `trail`, so it
merges and never lands in a fight): "A new trail: Your Hearth. Open the Journal (K) to follow it." The Journal's rail
icon carries a small unread dot until the trail has been looked at. Nothing else, no window, no sound beyond the toast's
own.

**K, Journal page, top section: TRAILS.** (The band has six pages and never more, SPEC §30, so Trails is a section on the
Journal page and a browse view inside it, not a seventh page.)
- **What you can do now.** Three rows. Each is a trail's name, its next step in one sentence, and **Follow** (or **Show
  me** if you already follow it). Ranked by: your followed trail's next step; then steps of trails that just opened; then
  steps you are close to (one more block, one more kill); then the rest in the order of the catalogue. If nothing is open,
  one line says what opens the next one ("Set your Ember down to open your first trail").
- **All trails ›.** Opens the browse view in the same page: area chips (Home, Weapons, Gates, Powers, Body, World, Deep,
  Story), a card per trail with a row of pips (4 of 7), the blurb, and Follow. Finished trails fold to the bottom.
  Silhouettes (3.1, rule 3) sit last: "Opens at D-rank".
- **A trail's card expands** to its steps: ticked or open, with the key cap, the hint behind "Why?", and **Show me** on a
  step with a place (it points the Now compass). **Not for me** hides the trail (a "Hidden" fold brings it back).

**Task card, top right.** A followed trail is the card: its title and up to five steps, done ones ticked, the current
one with its key cap and compass. It is the existing `Checklist` (VERIFIED: modules register a `Source` returning a
`Task` of `Row`s), so the card, the 148 px width rule and the tick animation already exist.

**Now line, top left.** The followed trail's current step is a Now suggestion at priority 55: under danger, rifts and a
tracked chain (68), over bookkeeping. With a `where`, it carries the compass.

**People.** The clerk's "Anything for me?" and Hale's, Oren's and Mira's idle lines can call `Trails.suggest(player)` and
say the first step in their own voice. (The Way Forward already plans the clerk line; this gives it a source.)

**What it feeds.** A silhouette trail files a Promise ("What waits"). A step's `tip` is offered through `TipHooks` when
the step becomes current. A finished trail files one line in the Journal and ticks the Director's "new thing" rhythm
(`Progression.markNewThing`).

### 3.5 Not overwhelming (the rules, as numbers)

- At most **2** new trails announced a play-day; at most **3** rows under "What you can do now"; at most **5** steps on
  the card; at most **1** followed trail.
- A `linear` trail lists only its current step and the one after; a `free` trail lists up to five open steps and folds
  the rest ("and 4 more").
- Locked trails are invisible until their lock is met; a trail you ignore for three play-days stops being "new" and
  just sits in the list.
- Following never changes the world or your inventory. Unfollowing is one press.
- A finished trail never reopens. A `repeat` field is left out on purpose.

### 3.6 Rewards (kept small and never required)

Finishing a trail pays **5% of a level once** (the Discovery XP source, which exists, VERIFIED:
`Progression.XpSource.DISCOVERY`) and files a Journal line. A trail's last step is usually its own reward (a recipe, a
room, a tier). Nothing a trail teaches is gated behind it. **Decision 5.**

## 4. The catalogue: trails for everything

About 30 trails across eight areas, ordered by where a new player meets them. The Hearth is the pilot and is written out in
full (4.1). The rest are sketches: owner, what opens it, and what its steps cover. Step lists are written by the builder
who owns the area, from the module's docs, in the same format.

### 4.1 Home (Hearth) , the pilot, 7 trails, about 45 steps

| Trail | Opens | Steps (all `free` unless noted) |
|---|---|---|
| **Set Your Fire** (`linear`) | you carry an Ember | Carry the Ember; find open, level ground away from gates and the city; set it down; name your ground; walk off and come back |
| **Your Hearth** | the fire is placed | Open Your Hearth (use the fire); build a closed roofed room beside it (Shelter); sleep in your own bed (Rested); make a Sanctuary (bed and Meditation Mat); take Oren's cottage or build your own; turn the fire into the Anchor with a Lord's Heart (tier I) |
| **A Room for Everything** | your first room is read | Make a Kitchen and cook there; a Study; a Greenhouse; a Stable; a Hall; a Lookout; a Bell tower; a Workshop; raise any room to Fine; raise one to Grand |
| **Your Village** | tier I | A spare Snug bedroom; someone moves in; take a commission; tier II (four rooms, one Fine, a field or a pen); tier III (a Grand room); a Mine head; tier IV (bell tower, four people: the village is named on the Atlas); tier V |
| **The Forge** | you hold or have crafted a Forge | Craft a Forge; set up an Armory (anvil, Forge, armour stand); temper a weapon; draw a copy of a Blueprint you know; attune a weapon; raise a Marked weapon a tier; upgrade the Forge (tier II at D) |
| **Trophies and Echoes** | your first boss trophy or elite kill | Hang a trophy; make a Trophy Hall; bind an Echo; set a role pad; put an Echo on duty; make Barracks; hold a break at home |
| **The Training Grounds** | tier I | Place a Start Gate; place numbered rings; place a Finish Gate; run the course; beat your best; add a dummy and strike it |

The four Forge teaching beats proposed earlier fold into **The Forge** (its steps are the pointers the Forge lacked:
raising, copying a Blueprint, attuning), and the open toast of a new gear tier can name the Forge trail. **Rank trials**
belong to the Story area below.

### 4.2 The rest (sketches)

| Area | Trails | Opens at | Covers |
|---|---|---|---|
| **Body** (`body`, `anims`) | Moving Like a Hunter; The Line; Wings | awakening; Tether; Wings | the four moves (absorbs the task card's "Your body remembers"), the rope and rappel, flight |
| **Powers** (`powers`) | Your First Power; The Five Forces; Weaves; The Second Awakening | the first power pick; D-rank; a weave; C-rank | casting keys, energy, combos with forces, Paths |
| **Weapons** (`arms`) | Your First Weapon; Finding Patterns; Making Your Next Weapon; Boss Weapons | awakening; the issue taken; each tier opening; the first Lord | Hale's issue, the Armoury, bearers, villages, stands, part jobs, Spoils, Raise (the arms plan's Track becomes a step type) |
| **Gates** (`gates`, `mobs`) | Your First Gate; Floors and the Satchel; Gate Lords; Breaks | the first gate seen; first clear; first Lord; first break | cards, floors, extraction, Lords and their parts, holding a break |
| **World** (`living`, `village`, `world/fauna`, `delve`) | The Commons; Wayfarers and Camps; Villages; Beasts and Lairs; Digging Down; Rumours | leaving the walls; the camp; a village entered; a lair rumour; first ore | regions and the Atlas, chains and bounties, trade and standing, named beasts and mounts, mining and caves |
| **Deep** (`descent`, `veil`, `lucidium`, `above`) | The Well; The Veil; Dreams; The Precursors | the Well found; meditation unlocked; first sleep; first ruin | strata and Waystones, spirit projection, lucid dreams, scanners and machines |
| **Story** (`story`, `progression`) | Ranks and Trials; Titles and Truths | D-rank level; the first Truth | Reassessment and the Monolith, titles, latents |

Each is written when its owner's wave runs. A sketch is not a promise to ship all of them: the first wave ships the Hearth,
Weapons and Body; the rest follow in the order Euan plays into.

## 5. How it plugs into what exists

| Existing system | What changes | Size |
|---|---|---|
| `Checklist` (task card) | A `Source` for the followed trail; a per-player "what is on the card" choice so Follow, the tracked chain and the Armoury's Track share one slot (the last one chosen shows) | small (progression) |
| `NowHooks` | One source: the followed trail's current step, priority 55 | a few lines |
| `Requirements` | Tokens stay; `TrailsApi.condition` adds module families | small |
| Journal page (`StatusSections.Tab.JOURNAL`) | A TRAILS section at the top (order below the filters) and a browse view; the rail dot | the client part |
| `Promises` | A silhouette trail files a promise | a few lines |
| `TipHooks` | A step's `tip` is offered when current | a few lines |
| `HearthNow` | Keeps its first-ten-minutes nudge; the room-by-room lines hand over to the Hearth trail when you follow it, so two voices never say different things | small (hearth) |
| Arms Track | Becomes a trail step type (a dynamic row list from the Armoury) in a later wave; works as now until then | later |
| Directives and Regimen | Untouched. They stay random and daily; Trails is curated and finite | none |
| Quest chains (`living`) | Untouched. A step may point at a chain ("finish Ilse's tonics") | none |
| The notification director | The opening toast is IMPORTANT, topic `trail`, so it merges and waits for a calm moment | none |

## 6. The engine (PROPOSAL)

New small module `trails` (`dev.awaken.trails`), listed in SPEC §1 and given `docs/modules/trails.md`.

- `TrailDef` (record and Codec, schemaVersion 1), `Trails` (the reload listener, id to def), `Conditions` (the token
  registry: base `Requirements` plus module families), `TrailState` (the attachment `awaken:trails`, schemaVersion 1,
  kept through death: opened ids, finished ids, hidden ids, the followed id, latched step ids, a `lastNew` play-day).
- `TrailEngine`: evaluates only what matters. It re-checks on events (level, rank, counter changes through
  `Progression`'s existing evaluate hooks, hearth scans, a Blueprint learned) and on a 40-tick pass for the followed
  trail; the K menu asks for a full view when opened. No per-tick allocation; a step is a few token lookups.
- `TrailNet`: `TrailView` (server to client: the trails visible to this player with their steps and ticks; sent when it
  changes and when K opens) and `TrailAct` (client to server: follow, unfollow, hide, unhide, mark done, show me).
- `core.api.TrailsApi`: `unlock(player, id)` for a moment that cannot be expressed as a token, `condition(prefix,
  predicate)`, `suggest(player)` for the people who answer "Anything for me?".
- Client: `TrailCache`, `TrailSection` (the Journal's top section), `TrailBrowse` (the browse view), the unread dot.
- Tests (one per mechanism, plus the content check): `trail_defs_valid` (every trail parses, every token resolves, step
  ids unique, every line fits the card and toast widths, no `after` cycle), `trail_progress_derived` (a step done before
  the trail opens is ticked at once; latching), `trail_follow_card` (Follow puts the steps on the card and the Now line;
  Unfollow clears them), `trail_open_once` (an opening toasts once, two a play-day at most), `trail_suggest` (the three
  best next steps), `trail_hearth_facts` (the Hearth tokens against a built room).

## 7. Phases and estimate (at AI pace, scaled from git history: all of Awaken was built in about 30 hours)

| Phase | What | Hours |
|---|---|---|
| **T0 Engine** | defs, loader, state, conditions, engine, nets, `TrailsApi`, the Checklist and Now sources, the validity test | 4 to 5 |
| **T1 The K menu** | the Journal section, the browse view, the rail dot, the opening toast, a look tour on the CI runners | 3 to 4 |
| **T2 Hearth pilot** | `HearthHooks.facts`, the Hearth tokens, the seven trails, the Forge beats, the `HearthNow` hand-over | 3 to 4 |
| **T3 Waves** | Weapons and Body first; then Gates and Powers; then World; then Deep and Story. One builder per wave, in parallel, each 1.5 to 3 hours | 8 to 10 |
| **T4 Polish** | the clerk's "Anything for me?", silhouette promises, tips tie-in, Arms Track as a step type | 1 to 2 |
| | **Total** | **about 20 to 25 builder hours**, roughly a third of that in wall-clock time with T3 in parallel |

T0 and T1 are the part that can go wrong (the K menu is where everything meets); T2 proves the format on the biggest
area before the waves copy it. A tour for the K menu runs once, at the end of T1, on the CI Mac runners (`tools/tour-ci.sh`).

## 8. Risks

- **Nag fatigue.** Mitigated by the numbers in 3.5 and by the rule that only Follow changes the HUD. A "quiet mode"
  setting that turns the opening toast off is a one-line config if wanted.
- **A step that never ticks** because the world does it a way the token misses. Mitigated by latching, `manual` steps,
  and `trail_defs_valid` checking that every token resolves; the first-wave builders play each trail through in a test.
- **Content volume.** About 200 steps. Mitigated by JSON, by the module owners writing their own, and by shipping in
  waves.
- **Two voices** (HearthNow and a trail saying different things). Mitigated by the hand-over in section 5.
- **Spoilers.** Mitigated by rule 3: nothing you have not met is listed.
- **The K menu is shared.** The Journal page already has about ten sections (filters, waiting, directives, regimen, records,
  chains, requests, the Hearth, the Now list, the log). Trails goes on top and the browse view takes the page over, so it adds no
  scroll to the rest. **UNVERIFIED** until a tour shows it.

## 9. Decisions for Euan (my recommendation first)

1. **Name.** Trails. Alternatives: Guides, Pursuits, "Things to try". ("Guide" already names the Your Hearth screen.)
2. **Home.** A section at the top of the Journal page with a browse view inside it. Alternative: a seventh page (breaks the
   six-page rule in SPEC §30).
3. **Announcing.** One toast when a trail opens plus an unread dot on the Journal icon. Alternative: the dot only.
4. **One followed trail at a time.** Alternative: up to three, with the card cycling.
5. **Rewards.** 5% of a level once on finishing, nothing gated. Alternative: none.
6. **Locked trails.** Invisible until you have met their lock, then a silhouette. Alternative: show all from the start.
7. **Progress.** Read from the world, latched, with a manual tick for the rare step the world cannot read.
8. **The Hearth's own Now lines.** Keep the first-ten-minutes nudge, hand the rest to the Hearth trail when followed.
9. **Order of work.** Engine and K menu, then the Hearth, then the waves (Weapons and Body first).
10. **The Forge beats.** Folded into The Forge trail instead of four separate prompts.

## 10. UNVERIFIED (the first builder checks, and says what they found)

- `HearthRecord` holds everything `HearthHooks.facts` needs (rooms by kind and grade, tier, folk, Forge tier, cottage).
- Whether `Checklist` can carry a per-player "owner of the card" without touching the living module's tracked-chain code.
- The Journal page's section order and whether a browse view can take over the page body (the Map page already opens
  something of its own through `setPageAction`).
- Which counters exist for each Hearth step (`docs/COUNTERS.md` lists most; Rested, room recognition and a Kitchen's first
  meal may need one line each).
- Whether the CI tour runner shows the Journal page the way the Mac does (it did for the K menu build).
