# The Forged Pick (draft SPEC §49)

Plan only. Nothing in production has changed. Approval page: https://claude.ai/artifact/Smj9nEuNHbq6MZ4y2X1CaB (source `docs/forged-pick/page.html`).
Concept meshes and mockups: `docs/forged-pick/proof/picks.py` (real voxel kit and weapon shader; writes only to `docs/forged-pick/shots`).

Labels used below: **[seen]** read in the code or his saves; **[source]** an outside claim, with its link; **[guess]** a hypothesis;
**[plan]** a proposal.

## 1. Understanding and success contract

**His words:** "I don't like the current pickaxe progression with that only that picks the pick that it gives you. I think I would much rather
like some kind of forging progression for pickaxes as well, similar to how you forge weapons. So we need to plan a full pickaxe evolution
system on par with the current forging experience for armor and for like weapons. Again, pushed to the maximum level and as like polished as it can be."

- **Job:** get and improve the tool you hold most, in a way that feels as earned as a sword from the War Anvil.
- **Pain:** the game hands you one fixed pick, and it advances along one fixed line. You never make it or choose anything about it.
- **Desired:** forging progression for picks, level with the anvil and the armour forge.
- **His solution:** forge picks the way weapons are forged. The evidence backs this, so it is the direction rather than just an idea to test.
- **Constraints:** old picks keep a defined path; everything saved is versioned; no third forge unless it's better; every item has a source
  (`journey_ledger`); steps on the Tasks page (`core.api.Goals`); plain speech; own look, not vanilla; start from how the real tool is used;
  multiplayer-safe; no non-redistributable assets.
- **Unknowns left after the audit:** how the two-handed dig feels (only play can show it); whether the wide head blocks the view while digging
  (stage 1 checks); whether wear can use the weapon shader's existing draw data without a new uniform.

| | What it looks like |
|---|---|
| Strong | Your first real pick is one you made at home in about a minute. Every step up is a scene you'd watch again. In the mine it looks and rings like yours. After a long game it shows where you've been. |
| Weak | A forge screen that swaps numbers. Seven new sprites. A tier table with a nicer name. |
| Unacceptable | An update that loses or weakens a pick. A scene that can fail and eat materials. A forced chore. Mining made trivial. Seeing through rock. |

**Non-goals:** a new forge block; picks as weapons; 3x3 or vein mining beyond today's three-block heavy strike and Call; new ores; changing lodes, seams or caves;
gate worlds (separate planner); any change to how weapons are forged.

**Must not regress:** heavy strike, true strikes, the hum, lamps, Kindle, Hush, Call, `#minecraft:pickaxes` behaviour, vanilla picks, enchant room,
kept through death, lost-pick recovery.

## 2. Verified audit and diagnosis

| Component | What it does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| The stone | One use gives a finished pick | `delve/WakingPick.java:66` `pull`, `:101` `give` | The pick arrives complete. This is his complaint. | Change: gives a head |
| Hearth hand-over | No adit near: a window hands you the pick | `WakingPick.java:84`, `cave/MouthPointer.java:95` | Same, with no moment at all | Change: hands over the head |
| Verses | Seven fixed steps, each sets TOOL, MAX_DAMAGE, attributes, model | `delve/PickVerses.java:56-131` | One line, no choice | Replace for new picks, keep for old |
| Deeds | Heart, lamp, Deepglass, seep; per-player counters | `PickVerses.java:136-153` | Good: they're things you do underground | Keep, reuse |
| Rites | Two right-clicks at the Forge with a vanilla sound | `delve/PickGifts.java` `forge()` | The weakest moments in the system | Replace with anvil acts |
| Gifts | Kindle, Echo, Glass, Hush, Call | `PickGifts.java` | Good, except two break the "no cheats" rule from 29 Oct | Keep; Echo and Glass become hearing |
| Hold and look | Flat 16 px sprite, parent `item/handheld` | `models/item/waking_pick*.json` | Every weapon and armour piece is a sculpted mesh | Replace with mesh |
| Dig motion | Additive one-hand keys, vanilla third person | `delve/client/DigMotion.java` | Reads as an add-on | Replace |
| Strike sound | Depends only on the ore | `delve/client/StrikeFeel.java` | The pick has no voice | Extend |
| Mattocks | Six grid recipes, still craft | `gear/GearItems.java:77-95`, SPEC §34.4 "Not done" | The grid ladder he rejected in the mining plan | Retire into the Mattock form |
| War Anvil | Heat, song, acts, memory, quality, rebuilds, the draw | `arms/anvil/*` | The strongest thing to reuse | Reuse |
| Tasks | No pick steps at all (Trail only) | `core/api/Goals.java:30`, `the_delvers_pick.json` | Breaks the Tasks rule | Add |
| Temper check | Any `awaken:` item with attack damage counts as a weapon | `hearth/Upgrades.java:280-305` | A forged pick would wrongly take weapon Temper | One-line exclusion |

**Cases, end to end [seen]:**

1. *Disappointing:* `New World (11)`, level 8, rank E, 18 ores: `waking_pick` verse 0, damage 20 of 131, no deed counter set. `Full test 1`: verse 0, plus a wooden and a stone pick in use. No save has got past Asleep.
2. *Strong:* a Sovereign Longsword at the anvil (CI run 41507062640): heat, draw out, twist, starlight, grind, hilt, balance, card, the draw.
3. *Ordinary:* rank D with iron. A vanilla iron pick is 6/250 and takes seconds; the Asleep pick is 4/131, so there is no reason to hold it.
4. *Ambiguous:* a rank E player whose pick is at Tempered (verse 3, diamond level). The rank and the pick disagree, and the migration has to handle it.
5. *Failure:* no adit near; or the pick lost in lava. Today the Forge remakes it for 3 iron at the furthest verse.

The want disappears at one line: `WakingPick.give` creates the finished item. From there no step involves making anything.

**Hypotheses:**

| Symptom | Possible cause | For | Against | Smallest test | If fixed |
|---|---|---|---|---|---|
| "only the pick it gives you" | It's given, not made | His words; the code path | He may like the stone itself | Stage 1 keeps the pull, changes what it gives | Removes the stated complaint |
| Feels lesser than weapons | Flat sprite vs meshes | Model JSON | He hasn't said so | Render side by side (on the page) | Large |
| Never advanced in play | First deed needs a lode's heart | Saves: verse 0 at 18 ores | Few hours played per world | Forging gives a step up with no deed | Large |
| Upgrades feel empty | Rites are one click | `PickGifts.forge` | Few players reached them | Replace with acts | Medium |
| Two ladders | Mattocks still craft | Recipes, SPEC note | | Retire | Small, removes confusion |

**Outside claims:**

| Claim | Source | Applies | Limits | Means here |
|---|---|---|---|---|
| Slit and drift a tool's eye hot, from each face in turn, keep metal thick round it | [FAO forge manual](https://www.fao.org/4/ah635e/AH635E15.htm), [iForgeIron](https://www.iforgeiron.com/topic/10211-slitter-geometry/page/6/) | Directly | FAO is a hammer and tool manual, not picks alone | The Cut the eye act |
| Tempering by oxide colours running from a hot part to the working end; straw is harder, blue tougher | [Polytech forum thread](https://www.polytechforum.com/blacksmithing/tempering-a-miners-pick-5463-.htm) | Yes | Hobbyist; repliers disagree on which end is which | Run the Colours, tip colour decides |
| A pick head seats by rapping the haft's wide end on something solid | [Wonkee Donkee](https://www.wonkeedonkeetools.co.uk/pick-axes/how-do-you-replace-a-wooden-pick-axe-handle), [Instructables](https://www.instructables.com/Pick-Handle), [US4585370](https://patents.google.com/patent/US4585370) | Directly | | Hang the Head |
| Miners owned their picks for a career and handed them on | [Tarleton: Implements or Heirlooms?](https://www.tarleton.edu/the-industrious-historian/2011/03/30/mining-tools-implements-or-heirlooms/) | Yes | One coalfield | "You keep it" |
| Picks were re-sharpened at the mine forge after the shift | [Gezähe](https://en.wikipedia.org/wiki/Gez%C3%A4he) | Yes | Encyclopaedia summary | Dressing |
| Miners tap the roof and listen for tight or drummy rock | [NIOSH](https://www.cdc.gov/NIOSH/Mining/UserFiles/works/pdfs/foulb.pdf), [PMC8802129](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8802129/) | Directly | | Sounding replaces sight |
| Tools that improve through use are liked; part systems get criticised for their documentation | [Tetra](https://www.curseforge.com/minecraft/mc-mods/tetra), [Moddex](https://moddex.gg/mod/tetra) | Partly | Anecdotal | Why no part inventories |
| Repeated mining sounds tire; per-hit feedback carries the feel | [Lucky Dig comments](https://jetamp.itch.io/lucky-dig/comments), [Rift Miner devlog](https://larsi-official.itch.io/rift-miner/devlog/1010791/rift-miner-version-001000-the-visual-identity-game-juice-update) | Yes | Weak, indie comments | Pitch and take variation |
| **Not found:** a good written account of how a pick is swung | Searched; only "two hands, controlled swing" and [USDA notes on grubbing tools](https://www.fs.usda.gov/eng/pubs/htmlpubs/htm00232839/page13a.htm) | | | The builder works from film of real swings |

## 3. Alternatives

| | A. Repair | B. Into the War Anvil (recommended) | C. Modular, third station |
|---|---|---|---|
| What | Keep the given pick; rites become scenes; give it a mesh | Picks forged at the anvil: four forms, six ranks, one pick raised | Heads, hafts and bindings as parts, a Delver's forge underground |
| Fixes "given" | No | Yes | Yes |
| On par with weapons | Partly | Yes, same scene and rules | Beyond, at a cost |
| New decisions | None | Form once; one colour press | Many, per part |
| New systems | Few | Adds acts to an existing song | A third forge, part inventories |
| Risk | Low | Medium (24 designs, the dig) | High |

C loses because a third forge has to be better and this one wouldn't be: part systems add decisions and paperwork, and he asked for "similar to how you forge weapons". A loses because the pick is still handed over.
**Would change this:** if he wants to choose head, haft and binding separately, take B and add parts later. If stage 1 shows the first-person anvil doesn't suit a pick, the armour forge's camera is the fallback.

## 4. Behaviour and architecture

### The goal, down
The tool you hold most should be the one you made, the one you keep and the one that shows where you've been.

### The first pick
- **Trigger:** use the stone (or the Hearth's hand-over). **Output:** `awaken:delver_old_head`, once per player. The stone stays for the next player.
- At the anvil's new **Picks** tab the head and a log and a string make a Wildhide Delver. Anyone can forge an E Delver or E Mattock without the head, from the same parts as today's mattock (2 of the rank's metal, 1 core, 1 Essence Shard) with a log instead of the sticks.

### Forms (one decision, changeable)
| Form | Speed | Uses | Way of digging | Forge beat | Opens |
|---|---|---|---|---|---|
| Delver | 100% | 100% | Fastest in plain rock; hums toward metal within 16 | tink tink BANG | From the start |
| Breaker | 85% | 130% | Heavy strike glints at 14 ticks (20 now), wears x2 (x3 now). Still 3 blocks. | two slow blows | First lode's heart |
| Mattock | Stone 90%, earth and wood 120% | 110% | One tool for stone, wood and earth | chop, pull | From the start |
| Sounder | 90% | 80% | Seam radius 0.40 (0.22 now), bonus ore 40% (25% now), hum 24, tap and listen +50% | three taps, listen | First lamp lit |

Forms open by deeds already counted, so there are no blueprints to carry. Re-draw at the anvil changes form at half cost: never a trap.

### Ranks: same gates as weapons (levels 3/13/22/42/67/97, anvil rebuilds at C and A, metal rule §28.0.7)
| Rank | Item prefix | Harvest | Delver speed | Delver uses | Damage | New act | Gift (deed) |
|---|---|---|---|---|---|---|---|
| E | wildhide | stone | 5 | 300 | 2 | Knap, bind, **Hang the Head** | Hum (Delver, Sounder) |
| D | beastbound | iron | 7 | 650 | 3 | **Cut the eye**, draw the points, **Run the Colours**, hang | Kindle (heart) |
| C | gatewrought | diamond | 9 | 1,800 | 4 | **Steel the tips** with diamond | Sounding (lamp); wider seam (the diamonds) |
| B | veilwoven | netherite | 10 | 2,200 | 4.5 | **Thread the spine** with Deepglass | Long Sounding (Deepglass) |
| A | lordscale | netherite | 11 | 2,700 | 5 | **Pour the seam** | Hush (seep) |
| S | sovereign | netherite | 12.5 | 3,400 | 6 | Starlight (reused) | Call (the starmetal) |

Vanilla: stone 4/131, iron 6/250, diamond 8/1561, netherite 9/2031. No tag requires netherite over diamond in our data [seen], so B's tag only protects migrated picks.
Quality lifts speed and uses: Plain 0, Fine +4%, Masterwork +8%, Flawless +12%, never above the Forge's limit. Colour: straw +8% speed, -20% uses; peacock none; blue -6% speed, +30% uses (continuous). Picks don't take weapon Temper.

### Contracts
| Trigger | Inputs | Processing | Output | Persistence | Failure |
|---|---|---|---|---|---|
| Open Picks tab | rank, level, deeds, inventory | `WarAnvil.status` by id | forms, ranks, cost; Forge it and Track. No part button. | none | Missing parts listed in words |
| Forge | id `<prefix>_pick_<form>` | `AnvilWork` kind FORGE, style `pick_<form>` in `AnvilSong.SONGS`, server judged | Item with `awaken:pick_forged` | Item; `AnvilWork` schema 6 | Leaving: the work waits |
| Raise | pick in hand, next rank's parts minus 1 metal | `transmuteCopy` to next id, new acts | Same pick, one rank up, history kept | Item | As forge; nothing consumed until the end |
| Work in | pick, deed done since its last visit | 5 s scene, free | Gift bit set | Item | Can't do it early; the deed list says why |
| Dress | pick, 1 of the rank's metal | 3-5 s scene | Mends a quarter; keen for 200 blocks (+10% speed) | Item | Never required |
| Re-draw | pick, half cost | Full scene at its rank | Other form, same rank and history | Item | Back out free before the first blow |
| Make it again | no pick on you; best rank remembered | Half cost at that rank | Same form and rank, new history | Per-player counters | Only when you've none |
| Bring old pick | `waking_pick` in hand | Free, 10 s, on any anvil, exempt from rank and anvil gates | Delver at verse's rank | Item | The old pick keeps working if you don't |

Saves: verse 0→E, 1-2→D, 3-4→C, 5→B, 6→A; every gift it had; deeds stay valid. Mattocks: free to the Mattock form at the same rank; the numbers above beat every old mattock.

### Invariants
- An update never removes, downgrades or transforms a pick by itself.
- The server judges every strike; the client only predicts.
- One action never breaks more than three blocks, except Call, as today.
- Nothing shows ores or caves through rock (decision 8).
- Every form can harvest everything its rank allows.
- A forged pick never breaks: at its last use it goes blunt, 30% speed, until dressed (`Item.damageItem` clamps; `ItemStack.java:481` calls it).
- Forged picks are kept through death.
- Vanilla picks stay exactly as they are.

### Data
`awaken:pick_forged`, schema 1: `form, quality, smith id, smith name, colour, day, blocks, ores, hearts, gifts (bits), keen, from`. Rank comes from the item id. Blocks and ores flush every 20 breaks or on a slot change, so a crash loses at most 19 of a flavour number.
Items `<prefix>_pick_<form>`: the old mattock ids are taken and stay registered. `awaken:waking_pick` stays registered for good. New tag `awaken:forged_picks`.
Everything asking `PickVerses.has` moves to `delve.api.Pick.has(stack, Gift)`, which answers for old and forged picks: `Lamps`, `Veins`, `CavePlaces`, `HeavyStrikes`, `StrikeNoise`, `TrueStrikes`, `EnchantRules`.

### Beyond the weapons and armour
Because you swing this thousands of times: the mesh in hand; a two-handed dig; a ring of its own that harmonises with the ore's note; rank rings on the haft; wear that shows; a tally on the item; a lit eye.

### Failure register
| Case | Detection | Result | Recovery | Test |
|---|---|---|---|---|
| Leave or disconnect mid-scene | `WarAnvil` presence | Work waits | Come back | Reuses anvil tests |
| Server restart mid-forge | `AnvilWork` saved | Continues | Automatic | Schema 5→6 round trip |
| Pick lost in lava | none held, rank known | Door offers Make it again | Half cost | `pick_remake` |
| Rank lower than an old pick's | migration table | Keeps its rank | None needed | `pick_migrate` all verses |
| Pick worn out | `damageItem` | Blunt, never breaks | Dress | `pick_blunt` |
| Deed done after the rank | counter | Next visit offers Work in | 5 s | `pick_gift_late` |
| No adit near | `MouthPointer` | Hearth gives the head | Existing path | `pick_head_handover` |
| Two players at one anvil | per-player work, reused | Twin-strike or blocked as now | | Reused |

**Privacy:** the maker's name on the item, same as weapons. **Performance:** one mesh per held pick, same as a weapon; no per-tick work beyond the hum, which already exists. **Cost:** none outside the build. **AI:** not applicable.

## 5. Concrete demonstrations
On the page: the seven real sprites beside six real meshes; the four forms; the whole table of 24; the billet, colours and hang mockups on real anvil shots (labelled); a live colour run; before and after walk-throughs; current and proposed flows.

The proof shows the designs work in the mesh pipeline and shader. It doesn't show the scene, the dig or the sound in game.

## 6. Evaluation and delivery

**Baseline:** his saves above; today's numbers. **Tests:** `pick_forge_all` (each form and rank), `pick_raise_keeps`, `pick_migrate` (verses 0-6, rank mismatch), `mattock_convert`, `pick_gifts`, `pick_three_block_cap`, `pick_blunt`, `pick_kept_on_death`, `pick_schema`, `journey_ledger`, full suite in CI.
**Gates:** suite green; CI tour shots of each stage; Euan's eye at stage 1. "Feels right" is his call; tests can't decide it.

| Stage | What | Hours (builder) |
|---|---|---|
| 1 | One Beastbound Delver forged at the anvil: mesh, door tab, Cut the eye, draw, colours, hang, card, CI shots. Stop for Euan. | 5-7 |
| 2 | All 24 designs, four beats, acts for C, B, A, S, numbers, forms' ways of digging, Re-draw | 9-12 |
| 3 | The dig in first and third person, one form, Euan judges, then the rest; the ring | 6-9 |
| 4 | The switch: stone gives the head, saves, mattocks, Tasks, trail, JEI, ledger, enchant | 3-4 |
| 5 | Rings, wear, tally, Dress, blunt | 3-4 |

Total about 26-36 hours, scaled from the War Anvil (55 commits, about 25 clock hours on 2-3 Nov) and the Singing Deep (about 31). The biggest unknown is stage 3: Swing Studio's rounds passed their numbers and got worse, so it gets at most two rounds before asking Euan.

**Reversible:** stages 1-3 add things and change nothing that exists. Stage 4 is the only switch, one commit to revert. **Go/no-go:** after stage 1, Euan. **Rollback trigger:** any save test fails, or he dislikes the look at stage 1.

**Risks:** the wide head may block the view while digging (stage 1 checks in first person); other sessions are in the anvil code now (camera hold, weapon charging), so build after they merge; 24 designs need the bar held to the end; the Sounder's fork is unusual and may not read; Long Sounding needs care to stay useful without sight.

**Challenge:** Real frustration: yes, his sentence names it. Less effort: mining gets a clear route, and each forge costs under a minute, less from memory, with a skip for repeats. Simpler: A gets maybe a third of it and leaves the complaint standing. What proves us wrong: stage 1 feels slow for a tool. Uncertain state: every path saved on the item; leaving a scene is safe. Inspect and correct: Re-draw, Work in, Make it again. Regression: old picks untouched until the player chooses.

## 7. Decisions
On the page, numbered, yes or no.

## Follow-ups outside this plan
"Forge the blade" sits directly above "Forge it" on the anvil door and probably explains the Wildhide Fang report: see the shared note `forged-pick-fang-button.md`.
