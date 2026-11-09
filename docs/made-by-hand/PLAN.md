# Made by Hand

The chill side of AWAKEN: building, furniture, farms and co-op for someone who doesn't fight.
Plan only. Nothing in production has changed. Approval page source: `docs/made-by-hand/page.src.html`, built by
`build_page.py` into `page.html`. SPEC gets its section (next free number) in the first commit after approval.

Labels used below: **Verified** means I read it in the code or the shipped jar. **Not run** means nobody has seen it in
the game. **Proposal** means a design choice, open to change.

## 1. Understanding and success contract

**The job.** Spend hours making a home that is entirely yours and looks like nothing else in Minecraft, with the same
moment-to-moment joy the fighting now has. A partner who doesn't try hard at games has just as good a time.

**The pain (verified, section 2).** Building is vanilla clicking. The game's best-looking pieces can't be had. One look
can take a chain of crafting steps. Furniture means stairs as seats and a post with a carpet as a table. Nothing can be
strung between two points. Farms have nothing new to grow. A partner who can't fight loses their Satchel when they die
and makes the gate harder for you by coming along.

**Euan's rulings, in order given.**
- Nothing builds or grows on its own. "Automating it makes it less fun." The Dressing Brush and the self-growing house are out.
- No architecture-mod complexity. Simple, fun, a whole new way to build, not vanilla with helpers.
- Mallet: yes, must work on custom blocks, must change one stone look to another without a ten-step process, with an easy way to set that look and change many blocks of that kind at once.
- Moves: yes, not limited to home, usable outside for movement, ripping out a hill is fine, levelling up so you aren't overpowered at once.
- Hang: yes, canopies and ropes, and you can climb them.
- Bosses give furniture: yes. Kin move in: yes.
- Riding Along: yes, preferred over the Home Bell. Can be switched on and off, keeps attacking and powers, effectively can't die, must stay balanced.
- Rooms That Aren't There and the Walking Hearth: no.
- Farms: every idea so far missed. Hands-on chores add complexity and automatic growth takes the fun away. He wants "variation and new custom things" and more options, with prettier farms kept but never a task list.

**What strong, weak and unacceptable look like.**
- Strong: within ten minutes of making a Mallet someone has tried three looks on a wall, set one, knocked an arch into a doorway, strung a canopy over it and climbed the rope. Their reaction is "I made that".
- Weak: it all works but feels like operating a tool. Blocks pop, particles look vanilla, menus appear.
- Unacceptable: the game builds or harvests for you. A wheel of forty options. Lost or duplicated blocks. Frame drops from a finished house. Grief on a shared server. Skipping the mining game.

**Outcome contract.**
| Area | Promise |
|---|---|
| Usefulness | Change a block's look in one hold and one click, against a chain of up to five crafting steps today |
| Correctness | A knock or a look lands on the block you aimed at, and what you saw in the preview is what you get |
| Effort | No crafting chains for looks, no scaffolding, no rebuilding a wall to move it, one recipe for all furniture |
| Responsiveness | Knock and look show on your screen the same tick; the server confirms within a round trip |
| Control | Nothing changes unless you did it; every Mallet and Lift action can be undone |
| Reliability | Nothing is ever lost: a failed action leaves the world as it was and tells you why in one line |
| Cost | A finished shaped house costs the same each frame as a normal one once meshed |

**Non-goals.** Rooms That Aren't There, the Walking Hearth, the Dressing Brush, schematic paste, crop mini-games, machines
that farm or build, new Hearth tiers, Home Bell.

**Must not regress.** Mining and the Forged Pick's economy, the Hearth's room reading, Runner and Ascent movement, ward
and gate protection, Far Sight's distant look, frame rate in existing builds.

## 2. Verified audit

| Component | What it really does | Evidence | Consequence | Call |
|---|---|---|---|---|
| Carillon kit | About 50 finished pieces: bench, market table, goods shelf, cask, crate, awning, signboard, brazier, wall lantern, scarecrow, plaster, timber, ashlar, slate and clay tiles. 5 have items | `city/kit/*`, `assets/awaken/items/` (wall_ivy, flower_box, drainpipe, trellis, hoist_beam) | The best furniture in the game is out of reach | Extend: release through Stock |
| Custom full blocks | 224 cube blocks. 101 belong to gates, where only two construct blocks can be broken. 19 have neither loot nor recipe | Model parents counted; `gate_breakable.json`; `GateEvents.onBreak` | A huge palette exists that no survival player can use | Extend: Looks |
| Hearth furniture reading | SEAT is a stair with room to sit, TABLE a post with a carpet or slab | `hearth/room/Furniture.java` | Furniture is a workaround today; nothing to sit on | Extend with tags |
| Hearth room reading | Reads materials per block state, cached per block | `hearth/room/Materials.java` | A shaped block would read as "other" unless taught | Extend |
| Power protection | `Constructs.mayChange`: never inside another's Hearth, 48 blocks | `powers/Constructs.java` | Reuse as the one permission check | Keep |
| Temporary terrain | `TerrainLedger` lifts blocks and restores them, survives crashes, never overwrites | `powers/TerrainLedger.java` | The safety pattern for Lift already exists | Reuse |
| Flying blocks | Server-moved block displays | `powers/FlyingBlocks.java` | Held pieces for Lift already have a proven body | Reuse |
| Block model wrapping | `DelegateBlockStateModel` with `collectParts(level, pos, ...)`, shipped under Sodium | `ground/client/SeamlessModel`, `terrain/client/WavingModel` | A shaped model can use the same door | Reuse |
| Model data in Sodium | The shipped jar's `LevelSliceMixin` has `getModelData(BlockPos)` | `javap` on `sodium-neoforge-0.9.3-beta.1+mc26.3` | Supported path for per-block data on meshing threads. Not run | Investigate in Stage 0 |
| Strung lights | `CityFestoons` draws sagging lantern strings with custom geometry, built once | `citizens/client/CityFestoons.java` | Rendering path for lines exists | Reuse |
| Hang line block | Axis-aligned bar, sag 0 to 2, no item, placed by the city and a settler routine | `city/kit/StreetBlocks.HangLine`, `hearth/settle/Ropes` | Can't go at an angle or be player-made | Replace for players |
| `#awaken:runner_lines` | No Java outside tests reads it. The block comment says the runner catches its collision top | grep | "Climb a line" is not a real system yet | Build |
| Ascent states | TETHER, ZIP, CARRY, GLIDE, SOAR with server checks; new ordinals only at the end | SPEC §17 | Line climbing can be one more state | Extend |
| Terrain powers | Terrain Rip, Collapse, Architect constructs are temporary and do damage | `KineticPowers`, `Constructs` | Moves must be distinct: real, permanent, no real damage | Keep apart |
| Power keys | Z X C V, R, read in `ClientCasting` | `core/client/AwakenKeys`, `powers/client/ClientCasting` | Left alone: every Mallet control is on the mouse, and powers stay live with the Mallet out | Keep |
| Kin nooks | The game picks each kin's spot by scan | `kin/KinHome.nookFor` | Player can't choose where a kin lives | Extend |
| Kin automation | Burrowbuns harvest and replant, Mossbacks speed crops | `kin/KinHome` | Approved in §43 for boring work; must not touch the new plants | Scope |
| Crops | Vanilla, wild rye, blackberries, herbs read from natural plants | `ground/wild/WildBlocks`, `living/craft` | Nothing new to farm at home | Build |
| Trophies | First boss kill always drops one, then 20% | `hearth/trophy/Trophies` | Drop hook for keepsakes exists | Extend |
| Boss bodies | 41 bosses built from `kit/parts.py` | `docs/bosses/ROLLOUT.md` | Furniture can be cut from the real bodies | Reuse |
| Gate party scaling | +35% monsters and +50% Lord health per extra player, shared rewards through `Parties.sharing` | SPEC "Together" | Hook for Riding Along | Extend |
| Gate death | `Runs.onDeath`, including a cheat-death path that already cancels death | `gates/run/Runs.java` | Clean place to add the fold | Extend |
| Goals | Every system registers its steps on the K menu | `core/api/Goals.java` | Light guidance has a home | Reuse |

## 3. The disappointment, traced

| Case | Path today | Where it fails |
|---|---|---|
| Ordinary: cracked stone brick on a wall | Mine cobble, smelt to stone, craft to brick, smelt to cracked. Four steps and two furnace runs, then break and replace each block | The recipes are real; the cost is out of proportion to a decorative try |
| Disappointing: furnish a room | Bench, table and shelf exist as blocks with models. No item, no recipe. The room counts stairs as seats | Content exists but never reaches the player |
| Strong existing: Carillon | Authored kit, sagging festoons, laundry that moves | Proves the look is achievable; it's locked to the city generator |
| Ambiguous: "change all of this stone" | Doesn't exist. A wall and the hill behind it are the same block | Needs a preview and a cap so a hillside can't change by surprise |
| Failure: a block that can't be shaped | Doesn't exist | Needs a refusal in one plain line |
| Co-op: partner dies in a gate | `Runs.onDeath` keeps half at best on Story, the party count raises monsters 35% and Lord health 50% | The non-fighter pays and makes it worse for you |

Not reproduced in the running game: this is a reading of code, data and recipes. No play session was run for this plan.

## 4. Root causes

| Symptom | Cause | Evidence | Against it | Smallest test | If fixed |
|---|---|---|---|---|---|
| Building feels vanilla | No new verbs exist | No building tool in the source | None found | Stage 0 Mallet | Biggest single gain |
| Not enough variety | Art exists but no route to it | 45 kit pieces with no item, 101 gate blocks unbreakable | Some players may not want gate looks | Release through Looks | Hundreds of looks at once |
| Furniture is thin | Rooms were designed around vanilla stand-ins | `Furniture.SEAT`, `TABLE` | None | Stock plus chairs | High |
| Farms are dull | Nothing new to plant | `WildBlocks` list | The earlier fixes added rules, and he rejected them | Gallery flags, six hero plants | Unknown until he sees them |
| Partner has a bad time | Death punishes, scaling punishes | `Runs.onDeath`, SPEC "Together" | Story difficulty softens some | Riding Along | High for co-op |

## 5. Research

| Question | Finding | Source | Limits | What it means here |
|---|---|---|---|---|
| Can one block wear any block's texture on NeoForge? | FramedBlocks offers more than sixty shapes that take the texture and tint of any full solid block, including modded ones | [FramedBlocks guide](https://craftdownunder.co/guides/mods/framedblocks) | Doesn't say how it's built | The idea is proven in this ecosystem |
| Does the model-data route work with Sodium? | Craftmark says its retexturing uses the official NeoForge hooks Sodium-family renderers honour | [Craftmark](https://www.modpackindex.com/mod/158559/craftmark-data-driven-texture-variants) | A mod's own claim | Backed by the direct `javap` check; still Not run here |
| What made the best modern building tool feel good? | Analysis of Ultrahand names simplicity among its biggest design principles | [Zelda Dungeon on the GMTK analysis](https://www.zeldadungeon.net/informative-new-video-analyzes-how-nintendo-designed-ultrahand-for-tears-of-the-kingdom/) | Secondary source | No menus for the core verbs |
| Do ropes need a physics engine? | Light rope mods exist alongside full physics mods; Verlet cost depends on solver steps and collision | [LeadPhysics](https://www.modpackindex.com/mod/106898/leadphysics), [Velthoric](https://www.curseforge.com/minecraft/mc-mods/velthoric) | None measured in AWAKEN | Start with drawn curves and wind; add simulation only if it looks dead |

## 6. Alternatives and recommendation

**Targeted repair.** A Mallet item and released kit pieces, nothing else. Cheap and worthwhile, and it's Stages 0 to 2 of
the recommended plan. It doesn't give the new way of building he asked for.

**Recommended: one set of hands.** The Mallet is the one tool and the door to every building move. Real blocks wherever a
real block exists, a shaped block only where none does. Reuses protection, ledger, flying blocks, model wrapping, kit art.

**More ambitious: a free-form building engine.** Off-grid meshes and physics for everything. Rejected: it stops feeling
like Minecraft, adds exactly the complexity he dislikes, and breaks room reading, movement and Far Sight.

Sub-choices:
- **Shapes:** a registered block per material and shape would be thousands of blocks and could never cover future or other mods' blocks. A single shaped block that reads its look from data covers everything.
- **Lines:** blocks can't go at angles; an entity per line is heavy and fragile. Pegs (real blocks) own the lines and one client list draws them.
- **Where the moves live:** normal power slots force a trade with attacks, and every letter on the keyboard is already bound. Euan, 9 Nov: "the keybinds need to not interfere with the current power keybinds etc". So the moves live on the Mallet and on the mouse: right click does what the Mallet is set to, Sneak and scroll changes that. No power key changes meaning, ever. Decision 1, as changed.

What would change this: if Stage 0 shows knocking by aim is fiddly, shapes fall back to a plain cycle. If shaped blocks
cost too much when meshing, they get limited to detail work and big walls stay real blocks.

## 7. Behaviour contracts

### 7.1 The Mallet
Crafted early (two sticks and cobble) and a Hearth step on the K menu. Every control is on the mouse and only with the Mallet in your main hand; Z X C V and R stay your powers, with the Mallet out too.

| Control | What it does |
|---|---|
| Right click | What the Mallet is set to: Knock, or put the look on (Hang, Lift and Raise join in their stages) |
| Sneak and scroll | Change what right click does |
| Sneak and right click on a block | The Looks ring, open while Sneak is held |
| Middle click | Copy that block's look onto the Mallet and learn it |
| Sneak and left click | Undo |

**Knock (right click).** Trigger: a block in reach. Input: the block, the face and where on the face you hit. Aim picks the
part: middle of a face takes the slab cut, an edge takes the step, then bevel, then round, then back to whole; a corner
works the same way; under an edge gives the cove and arch. Output: the real vanilla or kit shape if one exists, otherwise
a shaped block. Refused with one line if the block has a block entity, isn't a full cube, or the permission check fails.

**Looks (Sneak and right click).** A ring of looks for that kind of block opens round the crosshair. Moving over a look shows it on the block
on your screen only. Left click sets it on that block. Right click sets it on every touching block of the same kind, up to the
level's cap, rippling outward, and the preview shows exactly which ones first. Letting go changes nothing. Sneak while
clicking changes one face (level 4). The look you set stays on the Mallet: with the Mallet set to Look, right click puts it on what you're aiming at,
sneak and tap does the whole touching run. Pick block (middle click) copies a look from anything in the world.

**Undo (Sneak and left click).** The last 32 Mallet and Lift actions, per player, kept in memory. An undo that would overwrite something changed
since is skipped with one line.

Invariants: never changes a block the permission check refuses; preview is client-only; server re-reads the target and
refuses if it changed; a knock never loses the block's identity.

### 7.2 Looks and the Look Book
- **Ordinary looks** (all vanilla stone, wood, clay, concrete and colour families, from a generated table): free from the start. Swaps are real blocks.
- **Learned looks** (kit and custom blocks): tap any block with the Mallet, anywhere including Carillon and gates, to learn its look. The tap changes nothing there.
- **Skins:** a learned look goes on as a skin. The block keeps what it was underneath: it breaks and drops as its base, sounds like its look. So nobody can make rare materials, and recipes and the economy don't move.
- Ore, lode hearts, Deepglass, metal blocks, gate function blocks and the like are never offered.
- Every new block must be classified. A `looks_every_block_classified` test fails the build for a new full block with no decision, as `journey_ledger` does for items.
- K menu, Journal, Looks: a grid of what you've found. A record, never orders. Members of a Hearth share theirs.

### 7.3 Stock and furniture
One recipe: Stock, from planks and sticks. Place a Stock block, Sneak and right click it, pick a piece from what you know, knock it into shape.
A starter set is known from the start; everything else is learned by tapping it in the world (Carillon's bench, a gate's
lamp). Tapping a part of a piece changes that part (legs, top, back, cushion), and the ring changes its materials, in any wood or
colour you know. About 40 new home pieces plus the freed Carillon kit. Breaking a piece returns Stock. Empty hand on a seat sits
down. The room reading counts them through tags.

**Set Anything Down (Decision).** Sneak and right click with any item on a surface and it sits exactly there, scroll to turn. At most 4 in a block space, 64 in a chunk.

**First Light (Decision).** Light the first lamp in a room that has just reached Fine or better and the camera glides through it once, 5 seconds, any key skips, once per room, setting to turn off.

### 7.4 Hang (Mallet set to Hang)
Right click one spot, right click another, a line strings between them. The ring picks rope, chain, lanterns, bunting or vines
while a line is pending. Keep clicking more corners to make cloth (3 to 6 corners); clicking the first corner again closes it. A real peg block goes on each
end, on any solid face. Rope comes from the bag at one per 4 blocks; cloth at one per corner.

Lines hang with a real sag and move with the wind. Lanterns give real light. Cloth casts real shade through invisible
sky-blocking cells at its height, so it keeps the rain off too.

**Climbing.** Jump or sprint into a line to grab it. Forward and back to go along it hand over hand. A slope slides you
down, sneak brakes. A steep line climbs like a ladder. Jump lets go with your momentum. Cloth gives a soft bounce when you land
on it. New Ascent state LINE (ordinal at the end), predicted, the server checking the line exists. The arrival system counts it as LINE, so wards still hold.

Break a peg or its wall and the line drops as an item.

### 7.5 Lift (Mallet set to Lift)
Hold right click and sweep the view across blocks. They light up, up to the level's count. Let go and they tear out and hang in front
of you as one piece. Scroll turns it. Right click slams it down. Left click throws it, a small hit, and it lands as real blocks. Can't take block
entities except shaped blocks, liquids, unbreakable blocks, sealed ward blocks, ore (Decision), gate or city blocks, or anything `Constructs.mayChange` refuses.

### 7.6 Raise (Mallet set to Raise)
Aim at natural ground or stone and hold right click: a column climbs, as far as your level. Sweep sideways to make a wall. Anything on top goes up
with it, so raising under your own feet throws you up. Costs energy, no blocks (Decision). Sand comes up as sandstone. Refused on built things.

### 7.7 Levels
Shaping level 1 to 10, the same curve as Ascent mastery (level = floor(sqrt(points / 25)), level 10 at 2,500 points). Points come
from new looks, new lines, moved blocks, and most of all from rooms the Hearth reads: Snug 10, Fine 25, Grand 60. Repeats on the
same block give nothing. A level gained is one quiet line above the hotbar, the same as Ascent.

| Level | Mallet | Hang | Lift | Raise |
|---|---|---|---|---|
| 1 | Slab, step, bevel. Run of 16 | Rope, 8 long | none | none |
| 2 | Round edges. Run of 32 | Chain, lanterns, 12 long. Climbing | 1 block | none |
| 3 | Cove and arch. Run of 64 | Bunting, vines, 16 long. Sliding | 4 | Pillar 3 high |
| 4 | One face only. Run of 128 | Cloth with 3 or 4 corners, 24 long | 8, throwing | 4 high, lifts you |
| 5 | Lean by 15 degrees. Run of 256 | Rope bridges, 32 long | 16 | 6 high, short walls |
| 6 | Any tilt, sink. Run of 512 | Cloth with up to 6 corners, 40 long | 27 | 8 high, walls of 8 |
| 7 | Run of 768 | 48 long | 48 | 12 high |
| 8 | Run of 1024 | 56 long | 64, stand on it | 16 high |
| 9 | Run of 1536 | 64 long | 96 | 20 high |
| 10 | Run of 2048 | 80 long | 128, a small building | 24 high |

Numbers are a first pass, tuned by play.

**In the world and in fights.** The moves work anywhere you're allowed to change blocks. Your powers stay on their keys with the Mallet out; the trade in a fight is that your hand holds a Mallet and not your weapon, so that's the
balance. A thrown piece never beats a same-rank power. In gates (Decision): no Mallet changes or Lift; Raise and Hang are
temporary and crumble after 10 seconds. In wards: the existing crumble rule.

### 7.8 Boss pieces
One signature piece per boss (41), a guaranteed first-kill drop beside the trophy, cut from that boss's real body in
`kit/parts.py`. One small life each: the Hive Regent's crown chandelier hums and wax motes drift, the Gearwright's table has turning gears. Pilot three, Euan sees them, then roll out.

### 7.9 Kin move in
A Kin Door block you place anywhere: tiny door, burrow mouth, perch, knocked into a design with the Mallet. A kin that's coming home picks a door before it scans.
Existing saves keep their nooks. Kin-sized furniture in the Stock ring. Kin use yours: nap on rugs, sit on seats, curl by fires. Count stays set by the Hearth's tier. New act `sit`, first pass on existing clips.

### 7.10 The Garden
Same as vanilla to grow: plant it, it grows, you pick it. No chores, no new rules, no minigames. About 24 new plants with shapes Minecraft doesn't have, from the
world, gates and kin (page gallery, Euan marks each). Everything you grow does something fun when you eat it. One quiet rule: the more different things grow near each other, the
faster everything grows and the more it gives, up to 1.6 times. No score, no list; a Journal page records what you've grown.
Beds, planters, hanging baskets on Hang lines, scarecrows you can dress, all from Stock. Burrowbuns and Mossbacks leave new plants alone (tag).

### 7.11 Riding Along
Switched on at a gate door or the K menu by both players; off the same way, never mid-fight. One rider per run. Rider keeps powers and weapons, hits count for 40% (Decision).
A killing blow folds them into your shadow for 4 seconds, then they spring out at half health near you. Nothing targets them first. They don't count in the party size for scaling. They
get their own loot and boss pieces, half XP, and no rank credit: Reassessments can't be done riding. Works anywhere within 48 blocks of the other player (Decision). A thread of light shows the bond.

## 8. Data and lifecycle

| Data | Where | Notes |
|---|---|---|
| Shaped block | Block state: shape, orientation, waterlogged. Block entity: base state, six face looks, tilt, sink, `schema` | Shape is in the state because occlusion is read per state (`BlockBehaviour.getOcclusionShape(state)`). Not ticking |
| Client mesh cache | Bounded map keyed by shape, orientation, tilt and looks | Derived only; cleared on resource reload |
| Look Book, Level, points | Player attachment, `schema` 1 | Per player, shared by reading members' at use time |
| Carried piece | Player attachment, written before the blocks leave the world | Crash-safe. On relog or death: blocks go home if the spot is air, otherwise to the bag, otherwise at the feet |
| Undo | Memory, 32 per player | Not saved |
| Lines | Peg block entities own them | Natural save, sync, unload and removal |
| Client line list, server line index | Rebuilt on block entity load | Derived only |
| Canopy shade cells | Invisible real blocks owned by the first peg | Removed with the cloth |
| Stock pieces | Kit blocks and one furniture block with parts and materials in a block entity | |
| Kin Door | Block plus a claim in `KinWorld.Homed` | Old `nook` values untouched |
| Plants | Normal blocks with ages, reading neighbours on random ticks only | No saved score |
| Bond | Party state, saved with the run | Dropped on either player leaving |

Concurrency: the server re-reads each target before applying; a preview that's gone stale is refused. Lift writes to the Carried record first, then clears the world; placing sets each block and then removes it from the record, so a crash can't duplicate or lose one.

Old worlds need nothing: everything new is added, nothing is migrated.

## 9. Failure register

| Case | Detect | Contain | Player sees | Test |
|---|---|---|---|---|
| Block changed after preview | Server re-read | Refuse | "That changed." | `shaping_stale_refused` |
| Logout or crash while carrying | Carried record exists on join | Blocks go home, or bag, or feet | Blocks are back | `lift_never_loses` |
| Attempted duplication by relog or chest | Record and world written in order; block entities refused | No duplicate | Nothing | `lift_no_dupe` |
| Look's block missing after a mod is removed | Registry lookup fails | Keeps the id; draws the base | Looks like its base | `shaping_missing_look` |
| Shape model can't read sprites | Build-time check, safe fallback | Draws a plain cube of its base | Plain block, one log line | `shaping_every_cube_model` |
| Sodium or Iris trouble | Setting off | Full cube of its look | Plain blocks | Stage 0 proof |
| Peg's wall broken | Block update | Line drops | Item falls | `hang_anchor_lost` |
| Line runs into unloaded chunk | Index by chunk; drawn only when both pegs are known | Nothing changes | Line shows when near | `hang_chunk_edge` |
| Grab a line that's gone | Server check per tick | State ends with momentum | You drop | `hang_line_gone` |
| Whole run reaches a hillside | Cap and preview | Stops at the cap | Preview showed it | `shaping_run_cap` |
| Griefing on a server | `Constructs.mayChange` for every move | Refused | One line | `shaping_protection` |
| Economy leak | Skins keep their base; test over recipes | Can't happen by construction | Nothing | `shaping_no_arbitrage` |
| Rider exploited as a tank | Mobs don't pick them first | | | `bond_no_aggro` |
| Rider used to boost rank | No rank credit, XP from own budget | | | `bond_no_rank` |
| Fold while the anchor died | Fold ends, normal run-end rules | | Same as a party wipe | `bond_anchor_dies` |

## 10. Ownership and privacy

Server decides everything. One permission check (`Constructs.mayChange`) for every move, and Hearth membership decides who can change a home. City protection, wards and gates keep their rules. Look Books are per player and only the pair's Hearth members read each other's. No external services or personal data involved. AI and retrieval: not applicable.

## 11. Performance and cost

- **Static shaped blocks** live in the chunk mesh, so they cost nothing per frame beyond their triangles. A bevel or round has more quads than a cube. **Not measured.** Target: remeshing a section full of them takes under 1.5 times a section of plain blocks.
- **Block entities** aren't ticking and carry a small update tag. Stage 0 measures chunk load with 5,000 in a build.
- **Lines** draw through one custom geometry pass within 96 blocks. Target: 200 lines and 16 cloths in view under 0.5 ms a frame on his Mac under Cinematic. **Not measured.**
- **Held pieces** use block displays, capped by level at 128. If that's heavy, merge into one mesh, only if measured.
- Perf tours stay on the Mac under `slot.sh`. Euan's 4K numbers (Cinematic 40-55 fps) make this a real budget.
- Art and sound follow the visual bar: no vanilla particles for any of this. A thud, dust, chips and each material's own sound.
- Cost: no paid services. Agent hours in section 13.

## 12. Evaluation

Per-mechanism GameTests named above (`shaping_`, `hang_`, `lift_`, `raise_`, `bond_`, `garden_`), one per mechanism, then the full suite once at hand-off. CI runs the suite and CI tours provide look shots.

Acceptance is Euan's, playing:
| Stage | Gate |
|---|---|
| 0 | He plays with the Mallet for ten minutes on three materials and says the feel is right. Shapes look correct on a vanilla and a custom block under Sodium. Chunk meshing within the target |
| 1 | Can restyle a real wall by hand without being told how |
| 3 | Strings a canopy and climbs a rope and likes it |
| 4 | Finds Lift and Raise fun out in the world; not overpowered |
| 5 | Pilot boss pieces pass; bosses showcase verdict in mind |
| 6 | Six hero plants pass before the rest are made |
| A | Plays a gate with a real partner |

Regression: existing Hearth, Runner, Ascent, Far Sight and gate suites stay green.

## 13. Stages

Each stage stops at a playable point. Every stage is behind a config switch and adds rather than changes.

| Stage | What | Needs | Rough agent hours |
|---|---|---|---|
| 0 Proof | Knock and Looks on the shaped block, three materials, Sodium | none | 3 to 6 |
| 1 Mallet | All shapes, Look Book, whole runs, undo, faces, tilt, HUD, sound and feel | 0 | 8 to 12 |
| 2 Stock | One recipe, the freed kit, starter set, parts, sitting, room reading | 1 | 10 to 16 |
| 3 Hang | Pegs, lines, cloth, climbing, shade | 1 | 8 to 14 |
| 4 Lift and Raise | Moves, levels, Goals step, wider rules | 1 | 8 to 12 |
| 5 Boss pieces and Kin | Three pilots, roll-out, Kin Door, sit | 2 | 10 to 18 |
| 6 Garden | Six hero plants, then what he marked | 2, gallery answers | 10 to 18 |
| A Riding Along | The bond, fold, scaling, rules | Gates as Worlds' scaling agreed | 4 to 8 |

Total 60 to 100 agent hours, calibrated on Armour Forge (approved and merged the same day) and Enchanting. What doesn't compress: CI time, Euan's play, boss-piece art rounds.

Rollback: every stage has a switch. With Shaping off, shaped blocks draw as full cubes of their look and nothing is lost. Go/no-go at Stage 0: if the feel isn't there, stop and rethink before building the rest.

**Dependencies.** Gates as Worlds (Looks' sources, gate rules, scaling), Powers in Full Voice (Terrain Rip and Architect stay distinct), Forged Pick (ore rule agreed), Fights That Read You (bosses ignore the rider). Each is coordinated at the start of its stage.

**Risks.** Tilted blocks may look wrong against neighbours. Cloth without physics may look stiff. A ring may feel like a menu; the Stage 0 play decides. 41 boss pieces is a lot of art at a high bar. New plants have now missed twice, which is why the page has a gallery and Stage 6 starts with six.

## 14. What I challenged

- Is this still too much? The core is one tool and one ring. Everything else is optional in order.
- Does it move work rather than remove it? It removes crafting chains and rebuilds and adds only choosing, which is the fun.
- Can less do most of it? Stages 0 to 2 alone give a large gain, and they come first.
- What would prove me wrong? Stage 0, in his hands.
