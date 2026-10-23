# The road to 50 (plan v2 P3)

"Every level gives +3 stat points and one visible unlock, so there are no dead levels." P3 fills the level road from 6
to 50, lets powers grow by use (mastery), teaches the Veil and dreams in play, and answers the gear module's energy
requests. Built in `progression/**` (`Lessons`, `RoadTests`, goals on `LevelRoad`), `powers/**` (`Mastery`,
`MasteryCommands`, `MasteryTests`; hooks in `Casting`, `Combat`, `PowerState`, `Powers`, `Loadout`, `PowersSection`),
`veil/VeilLessons`, `lucidium/dream/DreamLessons`, `gear/GearCombat` (energy only), `tools/sim/{pacing_sim,road_content}.py`.

## 1. The level road, every level 1 → 50

Steps marked **P3** are new (`tools/sim/road_content.py` writes them; run it after editing). Each P3 step grants
something real and carries a **goal**: the concrete thing to do, shown on the Now tracker from the moment the step
unlocks until its condition holds, then a toast. Goals with `requires` only show while they can be acted on (a stratum
needs its rank). Only the newest open goal shows, so the tracker never turns into a checklist.

| Lv | Step | Grants | Goal (Now line → done when) |
|---|---|---|---|
| 1-12 | (P1) first power, crafts, Dash + Wildhide, a core, Truths, Hearth Anchor, Echo binding, essence, a core, Reassessment, Meditation, Lucid dreams | see onboarding.md | meditation and dreams are taught by lessons (§3) |
| 13 | Beastbound gear · D | 7 recipes | |
| 14 | **Training Grounds** | Start Gate, Checkpoint Ring, Finish Gate recipes | Lay Start, Rings, Finish: run a lap → `training_laps` |
| 15 | The Veil | Stillpoint Shrine recipe, flag | taught by a lesson (§3) |
| 16 | **The Scanner** | a Precursor Scanner + its recipe | Sweep the Scanner: find a ruin → `ruins_found` |
| 17 | **The Fabricator** | Fabricator, circuit recipes | Scan a broken machine in a ruin → `machines_scanned` |
| 18 | **Machines, not mounts** | an essence cell; cell, void glass recipes | Build a machine at the Fabricator → `machines_built` |
| 19 | **Scout from the Veil** | 8 essence | As a spirit, drift into a gate → `gates_scouted` (needs the Veil) |
| 20 | The Well | flag, a core | |
| 21 | **The Dream Bell** | Dream Bell recipe, dream residue | Arm a Dream Bell; be lucid again → `lucid_dreams` 2 |
| 22 | Gatewrought gear · C | 7 recipes | |
| 23 | **Shape the dream** | dream glass, cloud, shifting stone recipes, 2 residue | Lucid: hold H on a wall for a door → `dream_manifests` |
| 24 | **The Hanging Gardens** | 8 essence | Descend to stratum 2 → `depth_record` 2 (D-rank) |
| 25 | Second Awakening | flag | |
| 26 | **The town grows** | Spire Board recipe, 10 essence | Hang a Lord's trophy in your Hall → `trophies_placed` (Hearth) |
| 27 | **Face the nightmare** | 2 dream residue | Journal 'face my fear', then win → `nightmares_beaten` |
| 28 | **Twisted gates** | a Power Core | Clear a gate with a twist → `twists_seen` |
| 29 | **Your mentor** | a Lord's Heart | Beat your mentor in a dream → `mentor_duels_won` (needs a Path) |
| 30 | Echo roles | 5 role pad recipes, flag | |
| 31 | **The Drowned Choir** | 12 Precursor scrap | Descend to stratum 3 → `depth_record` 3 |
| 32 | **Named beasts** | 10 essence | Fell a named beast → `beasts_felled` |
| 33 | **The Bone Orchard** | 12 essence | Descend to stratum 4 → `depth_record` 4 (C-rank) |
| 34 | **The Deep Lift** | 12 Precursor scrap | Light three Descent Waystones → `waystones_lit` 3 |
| 35 | Overload | the R power | |
| 36 | **The Silent Halls** | 14 essence | Descend to stratum 5 → `depth_record` 5 (C-rank) |
| 37 | **Precursor Frames** | six Precursor masonry recipes | Forge a Frame at a Frame Forge → `frames_forged` |
| 38 | **Stratum guardians** | 12 essence | Beat a guardian → `guardians_defeated` |
| 39 | **Beasts everywhere** | a Power Core | Fell three named beasts → `beasts_felled` 3 |
| 40 | The deep calls | flag, a core | |
| 41 | **Hold the line** | a Lord's Heart | Hold a gate break at your Hearth → `breaks_held_at_hearth` |
| 42 | Veilwoven gear · B | 7 recipes | |
| 43 | **Push past comfort** | 16 essence | Clear a floor deeper than usual → `floors_pushed_past_comfort` |
| 44 | **An army of Echoes** | 12 essence | Fight beside four Echoes at once → `echo_army_peak` 4 |
| 45 | B-rank trial | flag | |
| 46 | **The Foundry** | a Power Core | Descend to stratum 6 → `depth_record` 6 (B-rank) |
| 47 | **The Observatory** | a Lord's Heart | Find the Halo with an Observatory → `story/halo_seen` (B-rank) |
| 48 | **The Inverted Sea** | 20 essence | Descend to stratum 7 → `depth_record` 7 (B-rank) |
| 49 | **The Launch Ring** | ring frame and alloy recipes | Ride a Launch Ring to the Halo → `story/halo_reached` (B-rank) |
| 50 | The Hollow Court stirs | flag, two cores | |

Bands (plan §4.4): 11–20 the Veil, travel, gear D, the Well. **Mounts don't exist**, so 16–18 point at what does: the
Above's Scanner → Fabricator → your own machines (Waypoint Pylons and the Deep Lift carry you; Frames at 37 add Jet
Boots). 21–30 C-rank, the Path and its mentor, dream control (Bell, shaping, the nightmare), the town (Spire Board,
trophies; Teodor arrives at C). 31–45 strata 3–5, named beasts, guardians, breaks, the ultimate (35). 46–50 the B
strata and the Ascension hints (Observatory → Launch Ring → the Halo), then the Hollow Court.

Toast-sized text: a toast shows the title and the text's **first sentence** in 144 px, the tracker ~200 px. So each P3
text starts with the grant ("Stratum 2, 8 essence.") and says more after the full stop (kept in Recent messages).
`road_content.py` and `progression_level_road_every_level` check every P3 text against those widths.

Data: `LevelRoadDef` schema 2 adds an optional `goal` {`now`, `done` (a progression Condition), `requires` (Requirements
tokens), `toast`, `priority` 35}. Requirements gain `rank:X` and `path`. Saved state is profile counters only:
`awaken:goal/<step>` (met). `/awaken road` lists the steps around your level, their goals and your current lessons.

## 2. Power mastery (`powers/Mastery`)

A power grows by being used for real. Mastery points (MP):

| Use | MP |
|---|---|
| a cast that lands a hit on a living target (not your side) | its whole energy cost, channel drain included |
| energy spent in a fight that hit nothing (a Dash out of a swing, a Barrier, a miss) | half |
| energy spent at the air, out of a fight | 0 |
| a kill | 6 (elite or warden 18, boss 60); nothing under 6 max health; ordinary kills draw from 6 tokens, one back every 10 s |
| a hit with nothing to claim (summons, passives) | 1, at most once a second (passives every 2 s) |

A **fight** is a real exchange in the last 5 s: you landed a hit (melee or power) or something hit you. A cast beside an
idle mob is not one (`markCombatIfFoesNear` no longer counts as an exchange). A cast whose hits land later (a bolt, a held
Haymaker, a thrown rock) leaves its energy waiting 8 s; the first of its hits claims about one cast's worth. A new cast's
own hit never claims energy older casts spent at the air, so spamming and then landing once earns one cast.

Which power owns a hit: the power whose code is running (`Casting` marks a cast, a channel's tick and end, a passive);
else the power whose cast made the summon that struck (summons are recorded when Legion, Drone, Turret or Drone Swarm
casts); else the power of the same Force whose energy is still waiting (bolts, thrown blocks, delayed echoes,
reflections). Nothing allocates per hit (tracks are per player and power, ids cached).

Levels need (`Mastery.need`) **7,000 / 13,000 / 32,000 / 50,000 MP** for 1→2 / 2→3 / 3→4 / 4→5. A full bar raises the
level exactly like a duplicate Power Core (same `Loadout.Owned.level`: +12% damage, −4% cooldown, +5% duration per
level), with a toast "<Power> · level N" / "+12% damage, -4% cooldown." A core is still a shortcut and keeps the bar's
share toward the new next level. Saved in the loadout (schema 2, `mastery` map), synced once a second at most; the
Status POWERS card shows a hairline bar under the level pips and "Mastery 34% → Lv 3 · grows in real fights".

### Simulator (`python3 tools/sim/pacing_sim.py`, 300 runs, seed 42; all targets pass)

The main power (45% of your fight energy, 70% of regen spent while fighting, 85% of casts landing) from minute 8, no
duplicate cores. Combat is ~34% of play time for the balanced player.

| Player | main power Lv 2 | Lv 3 | Lv 4 | Lv 5 |
|---|---|---|---|---|
| balanced | player 10 (4.4 h) | **20 (11.8 h)** | 32 (28 h) | **44 (51 h)** |
| careful | 10 | 19 | 32 | 45 |
| greedy | 10 | 17 | 29 | 40 |
| builder | 11 | 21 | 34 | 50 |

XP pacing is untouched (mastery uses no random draws): level 5 at 0.78 h, 10 at 3.6 h, 25 at 17.0 h, 50 at 56.0 h, 135
at 100.9 h; level 3 at 30 min, 5 at 60 min. Nothing but fighting (a farm) takes 15–20 hours of pure combat to max one
power: energy regen is the ceiling.

## 3. The Veil and dreams taught in play (`progression/Lessons`)

A lesson starts when its level-road flag unlocks. Its current step shows one Now line; a step may ask one real decision
with a choice card; a toast says when it's done, and the next step starts. Steps already done when they come up
complete silently. Progress is profile counters (`awaken:lesson/<id>` = steps done; `…/<step>` = card 1 sent, 2 yes,
3 no). Checked once a second in the progression player tick.

| Lesson (flag, level) | Steps (Now line → done when) | Card |
|---|---|---|
| meditation (`awaken:meditation`, 11) | Meditate: sneak and hold G, keep still → 5 s of `meditate_ticks` | |
| dreams (`awaken:dreams`, 12) | Press H: a reality check → `reality_checks`; tonight's intention; Sleep in a bed tonight → `dreams`; Next dream: press H until it clears → `lucid_dreams` | at nightfall, awake, overworld, not hurt in 10 s: "Tonight's dream" [Y] Tonight I will fly (sets the fly intention, as the Dream Journal does) · [N] Just sleep |
| veil (`awaken:veil`, 15) | Meditate 10 s, let go of G → `veil_ticks`; Find one of the dead; press G → `spirits_spoken`; Hold G for 20 s while meditating → `stillpoint_visits` | when calm (awake, overworld, nothing hunting you): "The Stillpoint" [Y] Take me there now · [N] Later (the step stays, never asked twice) |

Veil and lucidium only register their lessons (`VeilLessons`, `DreamLessons`, one line in each module's init) and read
their own counters; no mechanic changed. The plan's example line "Sneak and hold G somewhere quiet to meditate" is
230 px, wider than the tracker, so it reads "Meditate: sneak and hold G, keep still".

## 4. Energy and perks

- `Powers.restoreEnergy(ServerPlayer, float)`: gives energy back, never above the pool; returns what it added.
  Restored energy is never "spent", so it never counts toward mastery.
- A Hunter **focus** in either hand gives energy back on a power hit, at most once a second: E 0.5, D 1, C 1.5, B 2
  (`GearCombat.focusEnergy`). Regen is 4.4/s at INT 5, so it stretches a fight a little and never pays for a cast.
- `Progression.addPerkSource((player, perkId) -> amount)`: summed into `Progression.perk` with Truths, the Title,
  Latents and the Path, so every perk reader sees it. No source is registered yet (gear can now grant perks).

## How to try it

`/awaken admin levels N` walks the road (toasts, Now lines). `/awaken road`. `/awaken powers mastery` lists your bars;
`/awaken admin power mastery <id> <points>` adds mastery. `/awaken admin counter "awaken:meditate_ticks" 100` (and
the other counters above) moves a lesson along. Autopilot: `tools/autopilot/road.txt` (not run by builders).

## Tests

`powers_mastery_real_hits_not_air` (air pays 0, beside an idle mob 0, a landed slam its cost, a miss in the fight half,
a kill +6 and old air energy unclaimed), `powers_mastery_levels_like_a_core` (same level, damage and cooldown; a core
keeps the bar's share; overflow; nothing past 5), `powers_mastery_saved_and_versioned` (round trip, schema-1 loads,
stream codec), `powers_restore_energy`, `gear_focus_in_world` (now also the focus refund and its once-a-second limit),
`progression_level_road_every_level` (1–50 all present; every recipe, item, power, core and flag resolves; goals well
formed; every P3 text fits), `progression_road_goals_track` (Now line, toast, silent when met early, hidden until its
rank), `progression_lessons_advance` (all three lessons, both cards, "Later" keeps the step).

## Known gaps

- Mastery attribution for hits that come late (bolts, echoes) is by Force and recency, not by the exact cast: two
  powers of the same Force with energy waiting can trade a claim. Exact tagging would need `Bolts`, `FlyingBlocks`,
  `Scheduler` and `PowerSummon` to carry the power (the integrator asked me to stay out of them while the FX builder
  works there).
- Passives without hits (Wall Run, Water Run, Danger Sense) only grow by cores.
- The older road texts (P1, gear) are wider than a toast's 144 px; they're generated by `progression_content.py` and
  `tools/art/specs/gear.py`, so they're left to those owners.
- No Status page lists the whole road yet; the Now tracker and `/awaken road` carry it.

## SPEC-worthy decisions (for the integrator)

1. §4.11 Level road: every level 1–50 has a step. Schema 2 steps carry a `goal` {now, done, requires, toast, priority};
   met goals saved as `awaken:goal/<step>`; only the newest open goal is shown (priority 35). Requirement tokens
   `rank:X` and `path`.
2. §6 Power mastery: MP as in §2 above; levels at 7,000 / 13,000 / 32,000 / 50,000 MP; same effect as a core; cores
   keep the bar's share. Loadout schema 2 adds `mastery`. "In a fight" = a real exchange in the last 5 s.
3. New §4.12 Lessons: meditation (11), dreams (12), the Veil (15) as in §3; progress in `awaken:lesson/*` counters.
4. §12 API: `Powers.restoreEnergy`, `Powers.masteryProgress`, `Progression.addPerkSource`, `Lessons.define`.
5. Gear: a focus refunds 0.5/1/1.5/2 energy (E–B) per power hit, at most once a second.
