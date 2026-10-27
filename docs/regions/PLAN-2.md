# The Wild Grows Teeth (regions plan 2, Living Wilds finished)

Status (2026-10-27, after Euan's "go with all recommendations"): **built in `pack/regions-plan`; SPEC §21.11 is the record of what and how**, and says what was not built. In short:
built are the five forms with their bodies, moves and voices (Builds 1 and 2), the land's signs, a quieter land, ambient air and the Atlas's smooth shading (Build 3), six wonders and the surface secrets with their loot
(Builds 4 and 5), five more chains for C, B and A land, first sights of forms as discoveries. Not built: ten of the sixteen wonder kinds, treasure maps, the legendary beasts, six of the eleven chains and all nineteen
new happenings, the creepers' moves, a sound bed and colour grade per rank, the Almanac's Forms page. Page: https://claude.ai/artifact/LhA8jeGxTmYTktau4mmgia (source `docs/regions/page2.html`).

---

## 0. What Euan asked, and the one-line answer

Euan, 2026-10-27, after Phase 3 landed: build the pieces left over (tusk and scar overlays, wild kin art, a quieter land, wonders and secrets ranked by land, the
Atlas's smooth shading), finish the Living Wilds phases, and make it so he thinks "WHATTTT?? I didn't even know this game could be so insanely awesome." He wants
real variety as he goes, the vanilla mobs **evolving** over the levels, and a land that **visibly** feels more dangerous the further he walks.

**The answer in one line:** make rank something you *see, hear and fight*. Every common creature gets a ladder of five forms (D, C, B, A, S): a new body, a new move
and a new voice at each rung, not a recolour. The land around them is dressed and sounds the part. And the unbuilt half of the Living Wilds (wonders, secrets,
legends, treasure maps, the other chains and happenings) goes in as the reasons to walk into that land.

---

## 1. Where things stand (audit; counted in the repo, 2026-10-27)

| Part | Built | Not built |
|---|---|---|
| **B** combat layer, regions, XP | all, and regions v2 (field, dread, warnings, wild kin that fight back, heat-coloured plates, alphas with their land's hide) | tusks and scars, wild kin art, a quieter land, the Atlas's smooth shading |
| **C** Wayfarers, chains, bounties, rumours | the engine, 9 chains (`data/awaken/awaken/quest`, **all for levels 1-20**), bounties, relics, the Journal | the other **11 chains**; nothing exists for C land and up |
| **D** World Pulse, ledger, camps | the Pulse, **9 happenings** of the ~28 planned, herds, packs, camps and nests | **19 happenings**; none is tied to a land's rank |
| **E** crafts | all six | - |
| **F** wonders, secrets, legends | **nothing** under `living.wonder`. Underground secrets and the grotto's legendary fishing spot were taken by Between the Gates W2 (`delve.cave.Grotto`) | wonders (~40 kinds), surface secrets, eight legendary beasts, treasure maps (no code anywhere) |
| **G** settlers, village, roads | built by Between the Gates: W6 settlers and the village's name, W7 roads and caravans | one line I found no code for: migrations crossing your fields (fences turn them) |
| Regions v2 leftovers | - | tusk and scar overlays, wild kin art, a quieter land, wonders and secrets ranked by land, Atlas shading; Arms' parts (a generic "Terror" item, shard scaling) stay Arms' |

So "finish the Living Wilds" is: all of **F**, the rest of **C** and **D**, one line of **G**, and the five regions leftovers. The new idea (forms) is what ties them together:
every new chain, happening, wonder and legend gets a reason to be in a particular rank because the creatures there *are* different.

---

## 2. What other games teach (and what we will not do)

| Game | What it shows | We take | We leave |
|---|---|---|---|
| **Breath of the Wild** | The Bokoblin tiers are red, blue, black, silver (gold in Master Mode): the colour says the tier at a glance ([Zelda Wiki](https://zeldawiki.wiki/wiki/Bokoblins)) | **A colour you read from far away** | Colour as the only change |
| **Elden Ring** | One base enemy, several status forms with different attacks: the Putrid Corpse has maggot, rot, blood-blister and burning forms ([wiki](https://eldenring.fandom.com/wiki/Putrid_Corpse)). Players on Steam say reskins make the game feel repetitive, and forgive it more in an open world ([thread](https://steamcommunity.com/app/1245620/discussions/0/6982351509017034882)) | **A form changes the attack, not just the paint** | Copy-paste enemies |
| **Valheim** | The biome is the tier: Greydwarf at 40 health in the Black Forest, the Shaman beside it, Fulings and their Berserker (700 health) on the Plains ([Game8](https://game8.co/games/Valheim/archives/320152) and a creature database; the sources disagree on some placements) | **The land owns the creature ladder** | - |
| **Monster Hunter Wilds** | Arch-Tempered monsters hit harder and have bigger pools; one player says the only difference is damage and aggression and asks for variants that *look* more distinct ([thread](https://steamcommunity.com/app/2246340/discussions/0/599646660884180818)); that is opinion, not Capcom | **Stat-only evolution disappoints; the look and the moves have to change too** | A bigger number as the whole answer |

The lesson, in the order it will be used as design law:
1. Change the **silhouette, the move and the voice**, not only the colour.
2. **Teach, then test:** a rung's new move is met alone first, mixed with others later.
3. **Tell before strike:** every move winds up where you can see it (the combat layer's own rule).
4. **The first meeting is a moment,** not a toast.
5. **Place and biome multiply variety for free:** the same rung looks different in snow, sand and forest.
6. **Danger is legible at 50 blocks:** a silhouette, a glow and a sound before a number.

---

## 3. The idea: three layers

- **Bodies** (what you see). A shared kit of pieces (tusks, horns, plates, spines, crowns, rags, scars, cracks) is hung on the *vanilla* renderers by rank, with a skin
  overlay and an emissive glow. Rank sets the glow colour (the Atlas's: D gold, C orange, B red, A violet, S indigo), the biome's family sets the material (moss and
  bark, sand-baked bone, rime). The plate chip stays *relative to you* (blue, orange, red); the world-visible glow is *absolute rank* so every player in a party sees
  the same thing. A key for both is in the Almanac.
- **Moves** (what they do). One new telegraphed move per rung, from about twelve shapes parameterised per species (lunge, rally, guard, volley, stomp ring, blink,
  spit cloud, rise, ambush, summon, charge, overcharge). Each blow is delivered with `Strikes.deliver(mob, kind, ...)` naming its class (LIGHT, HEAVY, PERILOUS or GRAB), the way the gate abilities do (`AbilityRunner`), so Arms' guard, deflect and dodge answer it.
- **Land** (what the world shows). Signs dressed into new chunks by rank, an ambient layer per rank (particles, a sound bed, a gentle grade), a land that grows quieter, and
  the set pieces: wonders, secrets, legends.

**Variety on top of rank.** A rank is not one form. Within a land the mix is ~70% the land's rung, ~25% the rung below, ~5% the rung above (a *foreshadow*: the first
Ironhusk in D land makes you stop, never in E land and never in the first hour). Near a rank border the mix is 50/50 over three levels (the borders are soft already).
Alphas (decision 8) wear the rung's **crown** piece and carry a second move from C up. Three biome families times five ranks times the mix means two zombies are
rarely the same.

---

## 4. The ladder

E land stays exactly vanilla (the first hour is untouched). D starts at level 10, 700-1,000 blocks out. Lens's dread, the warnings and the plate colours (Phase 2) keep working on top.

### 4.1 What a rung looks like (the language, all species)

| Rung | Glow | Body language | Voice | Ground |
|---|---|---|---|---|
| **D** Wild | gold, faint | scars, rags, small tusks and horns, bristle ridges, breath mist | a little lower, a growl under the call | scuffs and dust |
| **C** Hardened | orange | plates and scrap iron, bigger horns, spine rows, shields | lower still, a second growl layered | a heavier footfall |
| **B** Veined | red | glowing cracks and veins (ember, cold or spore by biome), trailing motes | deep, with a rasp | scorch or frost trail where it runs |
| **A** Monstrous | violet | oversized parts, antlers and crowns, a halo of motes | very low, a ring after the call | a tremor on its big move |
| **S** Sovereign | indigo-white | dark halo, void veins, parts that float | a far echo, the land goes quiet round it | the grass bends away |

### 4.2 The species (first drop). Names are working names; every move has a tell and a counter

Monsters wear the same ladder in their variants (zombie also husk, drowned, villager; skeleton also stray, bogged, parched).

| Species | D | C | B | A | S |
|---|---|---|---|---|---|
| **Zombie** | **Gnawer**: torn rags, a bone showing. *Groan*: calls two or three zombies within 24 | **Ironhusk**: scrap helm, a pauldron, a dented shield. *Shield bash* (tell: shield up); hit it from the side | **Reaver**: long raking claws, ember cracks. *Pounce*: lunges 6 blocks after a crouch and glow | **Revenant**: lit eyes, burning seams. *Rise*: stands again once at 40% unless you finish it fast | **Hollow King**: a thorn crown, shadow trailing. *Dread call*: the ground cracks, four lesser rise in a ring |
| **Skeleton** | **Bonepicker**: strafes and keeps its range | **Marksman**: long glowing draw, a piercing shot (tell: a bright arrow) | **Volley**: three arrows fan out, then rain in a patch (tell: bow to the sky) | **Wraith archer**: smoke body, blinks after a shot | **Bone warden**: a ring of bones turns round it, arrows through it |
| **Spider** | **Webspinner**: webs strung between trees at its territory, webs under your feet when it hits | **Ambusher**: waits motionless in a canopy or ceiling, a faint chitter as you pass, drops on you (tell: the chitter stops) | **Spitter**: an acid arc that leaves a cloud (tell: the abdomen swells) | **Broodmother**: bursts into spiderlings when it dies or is struck hard | **Void weaver**: strands pull you in, it steps along its own web |
| **Creeper** | **Hisser**: a longer fuse and a bigger blast, so it is readable | **Stormbred**: naturally charged, sparks crawl on it | **Cinder**: leaves a small fire patch, red cracks | **Sapper**: the blast pulls you in first, then throws you | **Stormwalker**: lightning where it detonates |
| **Enderman** | calm until looked at, as vanilla, with scars and a darker eye | **Stalker**: appears behind you after a looked-away breath | **Rift-eye**: a small rift pulls loose items and arrows | **Gaze-bound**: looking away is what angers it | **Nowhere**: it is simply not where you looked |
| **Slime** | **Gel**: splits into more, smaller | **Acidic**: leaves a slow puddle | **Boiler**: bounces to a slam | **Mother**: a cluster of smaller ones orbits it | **Ocean-heart**: one slow, huge, glowing |
| **Witch** | **Hedge witch**: a thrown potion with a tell | **Coven**: two, healing each other | **Hexer**: a root curse on a circle | **Brewmother**: a cloud of her own | **Storm-caller**: rain and one bolt on a mark |
| **Pig** | **Tusker**: two tusks, scars, a bristle ridge. *Gore* when struck (head down, paws) | **Rootbreaker**: curved tusks, hide plates. *Root-up*: a stomp that heaves a ring of ground. The herd rallies | **Ember boar**: cracks along the back, steam. *Trample*: a long charge through you | **Boarlord**: x1.3, a mane of bone spines, a crown of tusks. *Quake* | **Hollow tusk**: tusks of light. *Roar* staggers |
| **Cow** | **Bullock**: horns, scars. *Back-kick* when struck from behind | **Aurochs**: big horns, a hump. *Charge* | **Thunderhorn**: stomp shock | **Stormbull**: static on its horns, lightning on its charge | **Skyhorn**: motes lift off it |
| **Sheep** | **Bighorn**: curled horns. *Headbutt* launches | **Ironram**: iron-banded horns | **Cinderfleece**: smouldering wool, sparks when hit | **Stormfleece** | **Moonram** |
| **Chicken** | **Spurhen**: spurs, a flap-jump | **Cockerel**: a crest, a loud crow that rallies | **Firecrest**: a plume of embers | **Roc-chick**: x1.5, glides | **Sunfowl** |
| **Mooshroom** | **Sporecow**: a spore puff when struck (blind 2 s) | **Mycelid**: caps grow big | **Bloom**: spores heal its herd | **Blightbull**: a poison cloud | **Heartshroom** |

Rows for sheep, chicken and mooshroom are shorter in the first drop (D to B full, A and S at the second pass). The per-rung *move* is the thing built and tested; names
and looks are art, reviewed on contact sheets and one CI parade tour.

### 4.3 Six moments the walk should have

1. **The first Tusker** (D): a pig that doesn't run. It lowers its head. You were never told.
2. **A web forest** (C, forest family): the canopy strung with silk; a chitter overhead; then it stops.
3. **Ash-fall** (B, desert family): embers drifting down, skeletons in scorched rags, everything lit orange at dusk.
4. **A Thunderhorn herd at dusk on a ridge** (B/A): the ground hums before you see them.
5. **Revenant night** (A, new moon): a gravefield in the dressing opens and bells toll (a happening, section 8).
6. **The hush** (S): the birds are gone, your steps and a low bed are all there is, and something with a crown watches from a hill.

---

## 5. How it is built

All in `living` unless noted; the module rules stand (client code in `...client`, the server decides, nothing client-side runs on a dedicated server).

- **Form state.** A new small attachment `awaken:wild_form` (rung 0-5, a bitmask of marks, synced to trackers, saved): set on join by `Forms.of(species, land level, uuid, spawn)`
  (a pure function; the mix in section 3), so creatures already in an old world get their form when they next load. `CreatureTag` (core) is untouched.
- **Bodies (client).** `FormLayers`, added through `EntityRenderersEvent.AddLayers` to the vanilla renderers (the way `BridleLayer` and `AwLayers` already do), reading the
  form through `RegisterRenderStateModifiersEvent`. Three layers: a **skin overlay** on the vanilla UV (generated by `tools/art/specs`, reusing `skin_layer` and `face_mask`
  so faces stay readable), an **emissive glow** (rank colour), and an **attachment model** (an `EntityModel` of kit pieces whose parts copy the parent's `head`, `body`, `arms`
  poses). Texture sets: one overlay per species per rank per family, built by the generator from the vanilla texture. No mixin, no Turbo.
- **Performance.** Attachments beyond 48 blocks and overlays beyond 96 are skipped; at most 32 formed creatures a frame get attachments, nearest first; glow is one emissive
  submit. Setting `forms` in the client config: **Full / Lite** (overlay and glow only) **/ Off**. A resource pack that renames the vanilla parts falls back to Lite (a probe at load).
- **Moves (server).** `living.combat.marks.MarkRunner`: every 4 ticks, only with a target within 20 blocks, one move at a time, a cap on telegraphs per area. Tells are particles and
  a sound (the `Telegraph` helpers in `mobs` made public: a one-line seam, the mobs owner asked). Tell lengths: D 1.2 s, C 1.0, B 0.9, A 0.8, S 0.7. A move's damage is at most 1.3x
  that creature's normal hit at its level and is delivered through `Strikes.deliver` with its class named (a lunge is GRAB or PERILOUS: dodge only; a shield bash HEAVY). Wild kin keep "only when struck" (decision 8).
- **Voices (server).** `PlayLevelSoundEvent.AtEntity` lowers the pitch and layers a growl per rung for the formed creature's own sounds (the event has `setNewPitch`, `setNewVolume`,
  `setSound`); each move gets a tell sound from `tools/art/specs/audio_living.py`.
- **First sight.** The Almanac gains a Forms page ("Forms seen n / ??"); the first time you see a form (a ray, 24 blocks, like plates) it is a discovery (0.1 of a level,
  `form/<species>/<rung>`) and a one-line corner note, not a toast stack.
- **Pacing.** Forms change no XP and no health; they change risk, not reward. The sim (`ELITE_BY_RANK`, `role_mix`) gains a "move risk" factor per rung to check the first-hour and
  16-hour marks still hold. The first hour is E land, so it is untouched by construction.
- **Old worlds and multiplayer.** Forms are derived, so an old world switches on at load. Signs and wonders only appear in chunks not yet generated. Every form, glow and move is
  server-authoritative and identical for every player; discoveries are per player; legends are one per world.

---

## 6. The land looks the part

**Signs (worldgen, decision 24).** A feature added to every overworld land biome through a biome modifier, reading `RegionMap.levelAt` (pure, already used on worldgen threads by
`RoadWorld`). Density per chunk: E none, D 0.25, C 0.5, B 0.9, A 1.3, S 1.6. Never within the road and settlement calm, never in a Hearth, ward or the city. About ten sets,
vanilla blocks plus a handful of our own soft decor blocks (no loot, no harvest value):
- D: scratched trunks (stripped logs), flattened rings of podzol, mud wallows, bone piles, tusk-marked boulders; rust-stained outcrops of red sand and terracotta as the
  **surface sign of iron** (a pull; iron stays in lodes by depth).
- C: skulls on fence posts, a broken cart, a cold camp (soul campfire), webs strung high between trees in spider land.
- B: bare snag trees, ribs of a great beast (bone blocks), scorched rings, vents of smoke.
- A: gravefields (rows of mossy wall-heads with soul lanterns), black spires.
- S: weeping stones (our own block with a slow drip of violet motes).
Biome family dresses them (frost: rime on everything; sun: sand and glass; forest: moss and silk).

**Ambient by rank (client).** Particles near the player (D dust, C ash, B embers or cold motes by biome, A drifting violet specks, S almost none), a sound bed per rank (the dread bed
stays the gap's), a gentle colour grade per rank through the Lens place look (layer 1; layer 2 stays the gap's dread). Off with the existing `dread` setting; it is atmosphere.

**A quieter land (decision 23).** Passive animal spawn weight by rank: E x1, D x0.8, C x0.55, B x0.35, A x0.2, S x0.1 (a `MobSpawnEvent` check, never at a settlement or a Hearth).
Wild kin bolt from 24-40 blocks when you sprint or carry a light, and still fight when struck or cornered. The wild-life layer thins birds and insects by the same table and drops
them to silence in A and S. Hunting pays more per animal as it thins (the extra drop per rank already exists), and alphas still carry hide, so the hide supply holds.

---

## 7. Phase F: wonders, secrets, legends, maps

- **Wonders** (`living.wonder`): one per region, chosen from a library by the region's land and rank, placed as structures by worldgen through the region map, warded
  (no hostile spawns within 40 blocks, so the place can be *seen in peace*, getting there is the challenge). A discovery (0.15 of a level, rising to 0.5 at S), a Codex page, an
  Atlas icon, a unique effect or resource, and a hook (a chain or a happening). First drop **16 kinds**, in three steps:
  - the plan's twelve: Frozen Gate, Bell Tree, Glass Sea, Upside-down Falls, Giant's Arch, Singing Canyon, Sunken Spire, Statue Field, Petrified Herd, Star Crater, Chain Bridge, Mirror Lake;
  - four from the forms: **Boar Throne** (a tusk-and-bone seat, D/C), **Web Cathedral** (C), **Gravefield of the Revenants** (A), **The Weeping Stones** (S).
  Rank flavours the same kind (the Statue Field in B land has cracks that glow).
- **Secrets**: three per region from eight surface kinds (a cairn over a buried cache, a spirit's grave with a lore log, a three-stone puzzle shrine, a hollow behind a waterfall, an old camp with a
  journal, a standing stone that glows on one moon, a den with a gift, a ruined shrine). Never listed: the region card says "Secrets found 1 / ??". Per-player found state; the chest is per-player loot.
- **Treasure maps**: from camps, ruins and merchants; a map is a clue (terrain words and a drawn outline on the Atlas), never a pin; the dig spot is one of the region's secrets.
- **Legends**: eight, three first: **Old Greymane** (a white stag, D/C, who walks with migrations; can be calmed with the Herdcaller Horn), **Hush** (a silent hound, B, on new-moon nights),
  **the Glass Wyrm** (A, sun family). One of each per world, rumours first, they fall for good (everyone hears), and each wears the S kit's crown piece. The other five in a later pass.

## 8. Finishing C and D

- **11 chains** (the Living Wilds' other 11): written by rank so there is something for every land: D 4, C 3, B 2, A 1, S 1. Each is a reason to meet a form ("The Tusker of Black Glades",
  "Silk Road", "Bones for the Bell"), ends in a relic with a perk (capped like the others), and is found the way the nine already are.
- **19 happenings** to reach the planned 28; ten are tied to forms and rank: Tusker Moot (D), Silk Bloom (C), Ashfall (B), Thunderhead Herd (B), Crown Hunt (an alpha stalks a road),
  Revenant Night (A, new moon), Gravebell (A), Weeping (S), the Hush (S: all sound stops, then something comes), Migration across your fields (the G line, fences turn it).

## 9. The Atlas, smooth

The server sends a level per explored 16x16 cell, beside the zone bytes it already sends (`AtlasNet` tile: +1 KB a tile). `ZoneMap`/`AtlasMesh` interpolate the colour between cell centres on
a continuous ramp (green, gold, orange, red, violet, indigo by level on a soft log scale) and keep the region borders. A toggle (the legend, click) switches between **land rank** and **how hard it
is for you** (the plate's blue, orange, red). Wonders found are icons; a rumour leaves its "?". An old world shades its explored cells at the next login (the field is pure).

---

## 10. The decisions (16 to 31; my recommendation is yes on all)

| # | Decision | Recommended |
|---|---|---|
| **16** | Every common creature gets five forms (D to S): a new body, one new move, a new voice. E stays vanilla | Yes |
| **17** | First drop species: zombie, skeleton, spider, creeper, enderman, slime, witch; pig, cow, sheep, chicken, mooshroom (wolf, fox, goat, horse later) | Yes |
| **18** | One kit on the vanilla renderers (overlay, glow, attachments); rank sets the glow colour, the biome family the material; setting Full / Lite / Off; the plate chip stays relative | Yes |
| **19** | One telegraphed move per rung (a second on alphas from C), tell 1.2 s down to 0.7 s, damage 1.3x a normal hit at most, through `Strikes` | Yes |
| **20** | Voices drop and layer by rung (`PlayLevelSoundEvent`), a tell sound per move | Yes |
| **21** | First sight of a form is a moment: Almanac page, a tenth of a level, a one-line corner note | Yes |
| **22** | The mix: ~70% the land's rung, 25% below, 5% the rung above (never in E land), 50/50 across a rank border | Yes |
| **23** | A quieter land: passive animals thin by rank, flee far, birds go silent in A and S; hunting pays more | Yes |
| **24** | The land is dressed by rank (signs in new chunks, a handful of our own soft blocks); rust-stained outcrops as the surface sign of iron (Arms informed) | Yes |
| **25** | An ambient layer per rank: particles, a bed, a gentle grade, off with the `dread` setting | Yes |
| **26** | Atlas smooth shading with a "rank / hard for me" toggle | Yes |
| **27** | Wonders: one per region, 16 kinds first, warded, rank-flavoured | Yes |
| **28** | Three surface secrets per region (8 kinds) and treasure maps that are clues, not pins | Yes |
| **29** | Eight legends, three first (Old Greymane, Hush, the Glass Wyrm), one per world, fall for good | Yes |
| **30** | Finish C and D: the other 11 chains by rank, 19 more happenings, ten tied to forms | Yes |
| **31** | Order: P4 to P9 below, one builder per phase at xhigh, no sub-agents, Arms and the mobs owner asked first | Yes |

## 11. Order, estimate, who

| Phase | What | Builder time (git-history pace) | You can see |
|---|---|---|---|
| **P4** Bodies | form state, `FormLayers`, the kit and overlays, pig/cow/zombie/skeleton ladders first, then the rest; contact sheets; one CI parade tour | 4-5 h | the Tusker, the Ironhusk, in the world |
| **P5** Moves and voices | `MarkRunner`, ~12 move shapes, tells, voices, the pacing check, tests | 4-5 h | a pig that lowers its head; a zombie that bashes |
| **P6** Land | signs, ambient layer, a quieter land, Atlas smooth shading (one CI tour for both) | 4-5 h | a web forest, ash-fall, a coloured Atlas |
| **P7** Phase F-1 | the wonder engine, 6 wonders, surface secrets | 3-4 h | the first wonder |
| **P8** Phase F-2 | 10 more wonders, treasure maps, three legends | 4-5 h | a legend rumour paying off |
| **P9** C and D | 11 chains, 19 happenings, the G line | 5-6 h | the world fills with reasons to go |

Total **about 25-30 builder hours**, ~12-14 wall hours with three builders side by side (P4 then P5 is one builder; P6 a second; P7-P8 a third; P9 after). Based on the git history: Living Wilds B
to E (four phases) landed in about six hours on 2026-10-25; this is larger because it is art and worldgen heavy, so I have not scaled it down. The estimate has the least certainty in P4 (art review
loops) and P7-P8 (structures).

Coordination: the **mobs owner** (make `Telegraph`'s helpers public; the rest stays in `living`), **Arms** (alphas' hide already agreed; the dressing's iron outcrops informed; no new tier items),
**Lens/wild-life** (ambient layer 1; birds thinning), **Atlas** (the tile payload), **Between the Gates** (secrets and caves stay theirs; F adds only surface secrets).

## 12. Tests and tours

One test per mechanism (filtered runs while building, the suite once at hand-off, CI on push): `living_form_mix` (the pure mix, the borders, no foreshadow in E), `living_form_state` (join, save,
old creature gets its form), `living_mark_moves` (each shape runs its tell then its strike, damage cap, `Strikes` called), `living_form_voice` (pitch by rung), `living_quiet_land` (weights by rank, no change
at a settlement), `living_signs` (density by rank, none on roads or in a Hearth), `living_atlas_levels` (the tile carries levels), `living_wonder_placement`, `living_secret_found`, `living_legend_unique`,
`living_map_clue`, `living_chain_bands` (every rank has a chain), `living_happening_bands`. Tours (CI, one per hand-back): a **form parade** (each species' five rungs in a row, the way `lineup` does), a
**walk outward** from the city (E to S at dusk), the Atlas, and the first wonders. Sound is for the Mac's ears.

## 13. Risks and what this plan does not claim

- **Art is the schedule.** Attachments on moving vanilla parts may need a few rounds; the form parade tour is the review loop. I have not seen any of it on screen yet: Phase 3's wild animals and plate
  colours are also still unseen in game.
- **Fresh Animations and other packs** change vanilla models: the Lite fallback and the probe handle a renamed part; a pack that moves a part without renaming it could float a tusk. A known limit.
- **Fairness.** Every move is telegraphed and capped; a pack that rallies is capped by the summon budget (no more than 40 creatures within 48 blocks, as `mobs`). The first hour never sees a form.
- **Two colour systems** (rank glow, relative chip) could confuse; the Almanac key and the "hard for me" Atlas toggle exist for that.
- **Griefing:** no form adds block damage beyond vanilla's creeper rules; signs are soft and lootless.
- **Not claimed:** that these names are final, that the moves are balanced (the sim checks the long marks, a playtest checks the feel), or that old worlds get signs and wonders in chunks already generated.

## 14. Files (planned)

`living/combat/Forms.java`, `FormState.java`, `marks/MarkRunner.java`, `marks/Moves.java`, `FormVoices.java`; `living/client/FormLayers.java`, `FormKit.java`, `FormFx.java`;
`living/signs/WildSigns.java` (feature) and `data/awaken/worldgen/*`; `living/wonder/*`; `tools/art/specs/forms.py` (overlays, glow, kit textures), `audio_living.py` (voices, tells);
`atlas/AtlasNet.java`, `atlas/client/ZoneMap.java`, `AtlasMesh.java`; `data/awaken/awaken/quest/*`, `happening/*`; docs `docs/regions/PLAN-2.md`, SPEC §21.11.
