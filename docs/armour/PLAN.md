# The Forged Armour (armour progression plan)

Plan for Euan's 2026-10-29 request, written by the armour-forge-plan session on branch
`claude/armour-forge-plan-c98ed9`. Status: **APPROVED in full** (Euan in chat, 2026-10-29: "go ahead with the entire
plan at the quality bar of this first set you did [...] Get creative too so each stands out from the other while
staying at this quality bar"). Built phase by phase on the same branch; the rules live in SPEC §44. Follows `.claude/PLANNING-PROCESS.md`. Started fresh: no earlier
armour plan, page, branch or session's work was read.

## 1. Understanding and success contract

### 1.1 What Euan asked for (his words, lightly trimmed)

> "Build a whole armor progression system that's really epic and awesome. It ties into the whole mining experience, the
> whole Hearth, the current progression system [...]. Right now the usual armor is really bad, and in game we only have
> like 4 or 5 armor sets. Make it on par with the current weapon system, maybe every rank you have 4 or 5 different
> armor options, how you build them, and they have cool abilities. I don't want abilities like seeing mobs and ores or
> blocks. That feels like cheats. I want something that feels like you're becoming really powerful and look really cool,
> most of all. Looking really cool is the biggest one: really epic, not the boring flat vanilla look. [...] It should be
> more interesting than a boring crafting table, part of the forge in the Hearth. Make it very cinematic [...] where
> you're cinematically crafting your stuff in an intuitive way that doesn't feel like a task and feels like a fun game in
> itself."

### 1.2 The brief, as a checklist

In his order of importance:

1. **The look.** Every set reads as a sculpted, three-dimensional suit with its own silhouette, materials and theme:
   depth (layers, bevels, rims, overlaps), light that responds (metal shine, glowing seams), and motion (a cape and
   cloth that swing). Never the flat vanilla shell. It must look as good on a stand and on other players as on you.
2. **The making.** Armour is made at the Forge in your Hearth as a short cinematic game you play with your hands: heat,
   strike, quench, fit. Fun the 20th time, not a chore. Not a crafting grid.
3. **The power.** Abilities change how you fight and move and how you look doing it. **Nothing that shows hidden
   things**: no seeing creatures, ores or blocks through walls, no outlines, no detection.
4. **The count.** On par with weapons: four or five sets to choose from at each rank, each a different way to play.
5. **The fit.** Ties into mining (metals live in the caves), the Hearth (the Forge), the approved Journey (SPEC §43) and
   the weapons (patterns, the Armoury). Multiplayer-safe; anything cosmetic stays cosmetic.

### 1.3 Non-goals

- No new information abilities of any kind (Euan's rule). The one we have today goes (§2.2, decision 4).
- Not a weapons change: weapons, styles and the Armoury's weapon side stay as they are.
- No paid power. Looks and accents are earned in play; if a guild server ever sells anything, it is looks only.
- No mixins or renderer internals: the look is drawn through public NeoForge hooks (§4.1), so no Turbo feature.
- No third-party art. The reference images set the bar only; every mesh and texture comes from our own pipeline.

### 1.4 How we will know it worked

| Goal | Recognised by |
|---|---|
| Epic look | Euan's eye on CI tour shots of each set (stand, worn front and back, night, first person) before it ships; the probe (§5) first |
| Reads on others | each set names itself by silhouette at 20 blocks in a tour shot (horns, halo, cape, crest: never colour alone) |
| Making is fun | Euan forges a set in game and wants the next one; a piece takes 20-40 s, a known piece 6 s from memory |
| Power, not cheats | every ability changes only your body, your blows or your movement (a GameTest walks each ability's code path for anything that marks, outlines or reveals) |
| On par with weapons | 4 sets at E, 5 at each rank from D to S (29 in all), each with its own ability and look |
| Fits the budgets | ≤ 2,500 triangles a set (the art check enforces it), far sets drawn simpler, fps measured before the last phase ships |

## 2. Verified audit and diagnosis

### 2.1 What exists today (read in the code)

| Thing | Where | What it is |
|---|---|---|
| Six Hunter sets, one a rank | `gear/HunterTier.java` | Wildhide (E), Beastbound (D), Gatewrought (C), Veilwoven (B), Lordscale (A), Sovereign Weave (S); armour points, toughness and knockback on a vanilla-like ladder (E 10 points to S 24) |
| Their set bonuses | `gear/SetBonus.java`, `GearCombat.java` | half at 2 pieces, full at 4, tiers mix. Wildhide's full bonus is **Hunter's Sense**: sneak to see creatures within 12+ blocks through walls (`gear/client/HunterSense.java`) |
| Their look | vanilla equipment layers + `gear/Silhouette.java` | flat textures on vanilla's armour shell; A and S add boxes (horns, crest, pauldrons, crown, cape) as plain cubes from `gear_silhouettes/<set>.json` |
| How they are made | `data/awaken/recipe/<set>_<piece>.json` | crafting recipes (essence + the tier's metal) |
| Tempering | `hearth/Upgrades.java` | Temper I-VI at the Forge (hold the piece, use the Forge), weapons and armour alike |
| Vanilla armour | vanilla | leather, chain, copper, iron, gold, diamond, netherite: the "4 or 5 sets" Euan sees most |
| Weapons | SPEC §25, `docs/arms-progression/PLAN.md` | styles with a tier a rank, patterns carried by gate creatures and shown on Pattern Stands, the Armoury list, Lord-forged weapons, mastery, item level; drawn as sculpted meshes through our own shader (`tools/art/arms_weapons.py`, AWMS meshes) |
| The Forge in the Journey | SPEC §43 (approved 29 Oct) | "You forge. The Forge is yours alone: patterns, the Armoury, tempering. Emberkits keep it hot and smelt the ore; the hammer is yours." The Forge work arrives at E→D (iron and a gate pattern); before it, the Hearth's works table |
| Metals by rank | SPEC §43, §34.2 | D iron, C diamond, B amethyst and Deepglass, A netherite, S starmetal; metals live in the caves |

### 2.2 Why it disappoints (root causes)

1. **The look is flat because the shape is a shell.** Vanilla's armour is a texture wrapped on a box half a pixel off the
   body. Painting it better cannot add depth; the cube add-ons add bulk without bevels, overlaps or material, so they
   read as boxes stuck on. The weapons had the same problem and were fixed by sculpted meshes with painted ramps and a
   material-aware shader. Armour never got that path.
2. **One set a rank is a stat ladder, not a choice.** There is nothing to choose between, so a set says nothing about
   how you play.
3. **Making has no act and no place.** A recipe in a grid takes a second and happens anywhere. The Forge only tempers.
4. **The bonuses are numbers, and the one you can feel is a cheat.** Cornered, wards and Overflow are invisible numbers;
   Hunter's Sense is the felt one, and it is exactly what Euan does not want.

### 2.3 What we reuse

The weapon art pipeline (voxel boxes at a skin pixel, hue-shifted ramps, bevel bands, contact shadows, the material map
and the AWMS mesh format), the weapons' shader (copied for armour as its own pipeline, so the two can part later), the
Armoury and pattern rules, the Forge block and its Emberkits, the tier metals, Temper, the archetype timing table for
guard and dodge (abilities hook into its moments), and the CI tour runner.

## 3. Alternatives and the recommendation

### 3.1 The look

| Way | What it gives | Why not / why |
|---|---|---|
| A. Better textures on vanilla's shell | cheap | still flat; it is the thing Euan calls boring |
| B. Bigger cube models (vanilla model parts) | some silhouette | boxes without bevels or material read clunky; this is the family the earlier rejected look came from |
| C. Third-party model library (GeckoLib, Blockbench sets) | rich models | a new dependency, hand-sculpted per set by an artist we don't have, and its art is not ours |
| **D. Sculpted meshes on the body's bones, our own shader** | depth, silhouettes, metal and glow, cloth that moves; all from our code pipeline | **recommended, and built as the probe (§5)** |

### 3.2 The making

| Way | Fun the first time | Fun the 20th time |
|---|---|---|
| Crafting grid plus an animation | no | no |
| A long skill game for every piece | yes | no: a chore |
| **The Forge Song** (recommended): a few physical acts, each 3-10 s; skill sets the quality, never success; each set has its own rhythm you learn; a piece you have mastered can be forged from memory in 6 s | yes | yes: short, learnable, and quality is worth chasing |

### 3.3 The roster

Recommended: **four archetypes, one set of each per rank, plus one Lord-forged set per rank from D up.** The archetypes
are four ways to fight (they match what the weapons and powers already reward), so a rank's sets are a real choice:

| Archetype | You wear it to | Its ability family (§4.3) |
|---|---|---|
| **Bulwark** | stand your ground, guard and break | Bastion: blows you guard build a charge that bursts as a shockwave |
| **Strider** | move: dodge, run walls, dash | Flow: perfect dodges build a charge for an afterimage and a rush |
| **Channeler** | fight with your powers | Runes: your casts leave runes that circle you, then overchannel |
| **Duelist** | fight with the blade | Edge: deflects and rhythm build an edge that sends a cutting wave |
| **Lord-forged** | wear a Lord's power | the Lord's own move, made from its heart |

The six Hunter sets keep their ids and become one set in each rank, each re-made in the new look (§4.2).

## 4. Behaviour and architecture

### 4.1 The look: how a set is drawn

**Built and compiled in the probe** (`dev.awaken.armour.client`, no mixin):

- **Meshes on bones.** Each piece is a set of meshes, one per bone it rides: the helm on the head; the cuirass on the
  body, both arms and three cape links; the greaves on the body (belt), both legs and two cloth panels; the sabatons on
  the legs. `ArmourLayer` (a `RenderLayer`) draws each mesh where its bone is posed this frame
  (`ModelPart.translateAndRotate` after `setupAnim`), so armour follows every animation the body does, including the
  power and weapon poses.
- **Where it is drawn.** Players (both arm widths), mannequins, armour stands and every living renderer posing a
  `HumanoidModel` (zombies, skeletons, piglins, ours) get the layer (`EntityRenderersEvent.AddLayers`).
- **Vanilla's shell is off.** A render state modifier (`RegisterRenderStateModifiersEvent`, on every living entity's
  renderer) reads our pieces from the entity into the state's render data (vanilla only hands a render state armour
  that has an equipment asset; the probe's first CI tour caught this, a flat helm floating over the head). For each piece
  whose set has meshes, it empties the state's equipment slot, so vanilla's armour layer and the old Hunter silhouettes
  draw nothing, hides the skin's outer layer under it, clears the hat item, and hides the player's own cape while a
  set's cape is worn. A piece whose set has no meshes keeps vanilla's look (the Hunter sets keep their equipment assets
  as that fallback).
- **The cape and cloth move.** The cape hangs in three links from a yoke behind the shoulders, swung by the same lean
  and flap vanilla's cape uses, each link curling a little further. Cloth panels over the legs are pushed by the stride
  (the front by the leg swung forward, the back by the leg swung back).
- **The shader.** `awaken:core/armour` (the weapons' shader, its own copy): the painted texture plus a material map
  (metal, gloss, emissive, flow): metal catches a studio reflection, a sheen runs from feet to crown, emissive seams
  glow in the dark and pulse up the body, enchanted pieces shimmer. If a shader pack or driver can't build it, the same
  mesh draws through vanilla's entity cutout (`registerOptionalPipeline`, checked before use).
- **First person.** Your empty hand shows the cuirass's arm mesh as a gauntlet (`RenderArmEvent`). Our own arm drawers
  (Hands, powers' cast poses, the wrist) call the hand renderer directly, so P0 adds one shared "dress the arm" call they
  use, agreed with the first-person animations session (local_347a1a8e).
- **Multiplayer.** Nothing new is sent to draw armour: equipment is already synced to everyone who can see you, and the
  look rides on the item (its set, mastery and accent are item components). Others see exactly what you see.
- **Stands and the Hearth.** Stands and mannequins draw the full set (a stand's legs sit a texel lower; corrected).
  The Hall of Arms (P5) is a room of them.

**The art kit** (`tools/art/specs/armour_sets.py`, built for the probe): a set is a Python design in design space
(x your left, y up, z forward) made of plates, scales, lames, hems, rims, seams, gems, spikes and horns, chained links for
horns and capes, mirrored for left and right. It packs every piece's faces into one 128-texel atlas painted with the
weapons' ramps, writes `assets/awaken/armour/<set>/<piece>_<bone>.mesh` and the texture, and renders previews on a
skin mannequin (five turns, a head close-up, night). `check` refuses a set over budget, with an unknown bone, or with two
faces in one plane on different texel grids (they would flicker).

**Rules the kit enforces for depth:** plates overlap in layers (the chest's chevrons, the pauldron's lames stepping in
under its top plate), no two pieces share a face plane (they would fight for the same pixels), the silhouette always
breaks the body's outline somewhere a stranger would name (horns, crest, wings, halo, cape tongues).

**Rules for proportion, learned from the probe's first shots** (the first Lordscale read squat and toy-like beside
netherite): the helm stays within a texel or two of the skull and its height comes from horns or a crest, never a
taller box; shoulders rise outward, never droop; cloth over the legs stops at the knee so the legs read as legs; gold
hems only on the top plate of a stack, so no ring of gold lines up across the whole figure at the waist; glowing parts
are few and sit where the eye should go (the slit, the heart, the forearms).

**Mastery's look (P2-P3).** Each set has five looks that wake as you master it (§4.3): trims brighten, seams start to
glow, the awakened geometry grows (longer horns, an extra crest, a longer cape: a second mesh set the kit writes as
`<piece>_<bone>_awakened.mesh`), and at V the set's own aura (embers rising off Lordscale, frost breath off Frostwalker).
When an ability fires, the set's emissive flares for a moment (a per-draw value packed into the overlay channel the
shader already reads).

**Performance.** The probe's Lordscale is 1,024 triangles (helm 276, cuirass 464, greaves 136, sabatons 148). Budget:
2,500 a set, enforced by the art check. Beyond 24 blocks a set draws its far mesh (the kit merges the small details and
keeps the silhouette parts, about 400 triangles). Twenty armoured players in view is at most 50,000 triangles, small for
the M4; the CPU cost is writing their vertices each frame, which P0 makes allocation-free and P5 measures on the Mac
(perf tours stay on the Mac and need Euan's OK).

**Other mods.** Sodium draws chunks, not entities: no interaction. Iris is not in the pack; with any shader pack the
armour falls back to the entity cutout. Skin Layers 3D draws the skin's outer layer as voxels that can poke through thin
armour: P0 hides the outer-layer parts a worn piece covers (the render state's hat and jacket flags).

### 4.2 The sets (29)

Each set: its archetype, what it is made from, the shape a stranger would name, and its palette. Sets marked *(Hunter)*
are today's set ids, kept and re-made. Names are working names; Euan can rename any.

**E: 4 sets (0-4 h, made by hand at the works table, before the Forge).** Materials: hide, bone, copper from the first
caves, resin and wood from the wilds, essence shards.

| Set | Archetype | Theme and shape | Palette |
|---|---|---|---|
| **Wildhide** *(Hunter)* | Strider | layered hide with bone toggles, a hood with a fang brow, a fur mantle on one shoulder | tan, ochre, bone white |
| **Bellguard** | Bulwark | copper hammered like bell bronze: a bell-mouthed helm, round pauldrons, a skirt of copper plates | verdigris, bronze |
| **Rootwarden** | Channeler | bark plates bound with living root, a crown of branching antler-wood, moss cloth, sap that glows | bark, moss, amber glow |
| **Amberwing** | Duelist | light leather with resin-hard wing cases on the shoulders and back, a long sash | amber, black |

**D: 5 sets (4-17 h, the Forge opens).** Iron from the caves, gate patterns, hides and horn from D gates.

| Set | Archetype | Theme and shape | Palette |
|---|---|---|---|
| **Beastbound** *(Hunter)* | Bulwark | iron over beast hide, a skull visor with tusks, chain skirts | iron, rust leather, bone |
| **Cleftrunner** | Strider | slim iron lamellar, wrapped legs, a half mask and a long scarf that streams behind you | iron, dusk blue |
| **Lanternlight** | Channeler | a robe under an iron collar, lantern cages on the shoulders that burn in your Force's colour | soot, brass, Force glow |
| **Bladeward** | Duelist | a fencer's brigandine, high collar, one great pauldron on the off arm, a half cape | oxblood, steel |
| **Hornlord** | Lord-forged | the horns and hide of a D gate's beast Lord on blackened iron | black iron, horn, ember |

**C: 5 sets (17-45 h).** Diamond from the deep caves, gate crystals, frost pelts, catalysts for inlay.

| Set | Archetype | Theme and shape | Palette |
|---|---|---|---|
| **Gatewrought** *(Hunter)* | Duelist | gate-stone plates with the Five Forces set in a ring on the chest, a split tabard | slate, five Force colours |
| **Vaultbreaker** | Bulwark | massive diamond-studded plate, a ram-headed helm, a shield-like left pauldron | steel, diamond white |
| **Frostwalker** | Strider | pelts and clear crystal plates, icicle crest, a cape that frosts at the hem | white, ice blue |
| **Sunshard** | Channeler | gold and diamond plates with a sun disc behind the head, long tabards | gold, sunlit white |
| **Shardcrown** | Lord-forged | crystal growing out of the plates, a crown of shards, from a C Lord's heart | violet crystal, dark steel |

**B: 5 sets (45-70 h).** Amethyst and Deepglass from the Singing Deep, Veil thread, carapace from the Living Deep.

| Set | Archetype | Theme and shape | Palette |
|---|---|---|---|
| **Veilwoven** *(Hunter)* | Channeler | layered veils over fine plate, a hooded mantle, hems that drift as if under water | pale violet, silver |
| **Deepcarapace** | Bulwark | Deepglass chitin like a cave beetle, ridged dome helm, glowing seams | black glass, cave cyan |
| **Cloudrunner** | Strider | airy plates with feathered fins at the heels and shoulders | sky white, slate |
| **Tidebreaker** | Duelist | amethyst-edged scale, a fin crest, a cape cut like a breaking wave | sea green, amethyst |
| **Hollowheart** | Lord-forged | a B Lord's hollowed heart worn in an open cage on the chest | bone, deep red |

**A: 5 sets (70-88 h).** Netherite, Lords' scales, sky feathers.

| Set | Archetype | Theme and shape | Palette |
|---|---|---|---|
| **Lordscale** *(Hunter)* | Lord-forged | the probe: horned helm with a burning slit, chevron breast, rising pauldrons, three-link cape, ember heart | lord red-black, gold, ember |
| **Leviathan** | Bulwark | deep-sea netherite with fin spines down the back and a jaw-like helm | abyss blue, bone |
| **Galewing** | Strider | feathered netherite whose cape opens like wings when you fly | storm grey, white |
| **Halo Choir** | Channeler | bell-shaped skirts and a winged halo that rings when you cast | ivory, gold |
| **Dreamsteel** | Duelist | mirror-thin plate with fragments drifting a finger's width off it | pearl, night blue |

**S: 5 sets (88-100 h).** Starmetal, the Hollow Court's thread.

| Set | Archetype | Theme and shape | Palette |
|---|---|---|---|
| **Sovereign Weave** *(Hunter)* | Lord-forged | the Hollow Court's crown, collar and fins, a long court cape | black, silver, court violet |
| **Starfall** | Bulwark | starmetal plate with a burning star core in the chest | starmetal, white fire |
| **Riftwalker** | Strider | plates floating apart with darkness between them | void black, rift violet |
| **Nightcourt** | Duelist | a court duelist's high collar, long tails, a mask | midnight, silver |
| **Signal's Echo** | Channeler | robes that glitch like the opening's big moments, the System's own look | white, signal cyan |

**Numbers.** Every set sits on its rank's Hunter numbers today (E 10 armour points, toughness 0, up to S 24, 3.5, 0.15
knockback). Bulwark +1 point on the cuirass and greaves, +0.5 toughness, +0.05 knockback; Strider -1 on the cuirass,
+5% speed per piece; Channeler -1 on the cuirass, +3% power damage per piece; Duelist the rank's numbers; Lord-forged
the rank's numbers +1 on the cuirass. A spec table and a stats GameTest, as today.

### 4.3 Abilities: power you feel and see

**The rule:** an ability may change your body, your blows and your movement, and what everyone in front of you can see.
It never shows you anything you could not see without it: no outlines, no glowing creatures, no ores or blocks through
stone, no marks, no danger sense.

**Set bonus.** Two pieces of a set give its archetype's half bonus (numbers); four give the archetype's ability and the
set's own twist. Sets mix as today (two and two give both halves).

| Archetype | The ability (full set) | How it grows by rank |
|---|---|---|
| **Bulwark: Bastion** | blows you guard fill Bastion; full, your next deflect or heavy bursts a shockwave around you (staggers, knocks back, plays on the set's emissive) | C: a hard landing also slams; B: the burst carries your Force; A and S: two charges |
| **Strider: Flow** | perfect dodges and wall-runs fill Flow; full, a dodge leaves an afterimage that takes the next blow, and your next dodge is a Rush (twice as far, through creatures) | C: wall-runs last longer; B: a Rush in the air; A and S: two afterimages |
| **Channeler: Runes** | each power cast leaves a rune circling you (up to three); casting with three overchannels (the power hits harder and wider) | C: runes block one projectile each; B: overchannel refunds a cast; A and S: a fourth rune |
| **Duelist: Edge** | deflects and rhythm steps fill Edge; full, your next strike sends a cutting wave along its arc | C: Edge holds through a dodge; B: the wave bleeds; A and S: two waves |
| **Lord-forged** | the Lord's own move | e.g. Lordscale keeps Overflow (every fifth power hit of one Force bursts), Sovereign Weave keeps Unbowed and Sovereign's Measure, Hornlord charges with lowered horns |

**Each set's twist** (one line each, on top of the family): Wildhide **Pounce** (a sprint jump from sneaking leaps far
and its first hit lands hard; replaces Hunter's Sense), Bellguard **Ring** (a perfect guard rings like a bell and
staggers those near), Rootwarden **Rooted** (casting while still: no knockback and a little mending), Amberwing
**Tempo** (rhythm steps last twice as long), and so on for every set in the spec table (P2 to P4 write each set's twist
into SPEC as it is built).

**Mastery I-V.** A set grows as you wear it into fights: experience from blows taken and dealt while the full set is on,
more for gate Lords and Provings (numbers in the pacing sim, P2). I the set as forged; II its trims brighten; III its
seams glow and pulse with your heartbeat when you are low; IV its awakened geometry grows; V its aura and its ability's
full effect. Mastery is shown under the item's name and in the Armoury, and it is on the item, so others see it.

### 4.4 The Forge Song: making armour

**Where.** From D up at your Forge in the Hearth (the Journey's rule: the hammer is yours, Emberkits keep the fire and
work the bellows). At E, before the Forge, at the Hearth's works table by hand. Patterns come as the weapons' do: a gate
creature carries one, structures show them on Pattern Stands, and the Armoury lists every set with what it needs and
where it comes from.

**What you do, piece by piece (D and up):**

1. **Lay.** Pick the piece in the Armoury. The materials leave your pack and land on the bench: the bar on the anvil,
   the trims and gems beside it.
2. **Heat.** The bar goes into the fire; the Emberkits pump the bellows. Its colour climbs from dull red to orange to
   yellow; a gold band shows on the colour scale. Hold use, let go in the band (3-5 s). Early: the strikes count for less.
   Late: a scale flaw on the finished piece (it shows).
3. **Shape.** The bar on the anvil shows the piece's outline as glowing marks. The Forge plays the set's own rhythm
   (Lordscale's is slow and heavy, Cleftrunner's quick); you strike the marks on its beats, aiming with the mouse
   (6 to 12 strikes by piece). The metal takes the shape strike by strike: the blank becomes rough, the rough becomes the
   piece (three meshes the kit writes). On-beat and on-mark strikes add the set's melody note by note; a whole clean run
   plays the melody whole. Misses never fail the piece, they lower its quality.
4. **Quench.** Plunge it in the trough on the cue: steam, a hiss, the colour drops to the metal's own.
5. **Fit.** It goes onto the Fitting Stand by the Forge. The last piece of a set completes the stand's suit and the set
   wakes: seams light, the cape unfurls, the camera circles once. The first time you make a set, the System names it
   and its ability in plain words, with the glitch of the opening's big moments.

**The ranks add a verb**, so the game grows with you: C **Inlay** (turn a crystal or catalyst into its socket; your
choice sets the accent colour and a small extra stat), B **Tune** (Deepglass rings true only when struck at the right
point: listen and find it, the Singing Deep's sound), A **Fold** (netherite is folded: paired double strikes), S
**Starfire** (the heat goes white; a fast chain of marks that fade).

**At E (the works table):** cut the hide along the line (drag), lace the marks in order, set the rivets (strike), fit it
on. The same quality rules, shorter.

**Quality.** Plain, Fine, Masterwork, Flawless from heat, beat and aim. Quality sets the piece's starting temper (Plain
0, Fine I, Masterwork II, Flawless III and a maker's mark); the Forge's Temper goes on from there as today.

**Fun the 20th time.** A piece takes 20-40 s; each set has its own rhythm, so a new set is a new song; quality is worth
chasing; and once you have made a piece at Masterwork you may **forge it from memory**: one heat and one strike, 6 s,
Fine. Another player at your anvil can take the off-beats (a twin-struck mark on the piece).

**Camera, sound and light.** A cinematic camera by default (wide on the Forge, over the shoulder at the anvil, close on
the metal for each strike, low for the quench, a turn around the stand for the fit), with a setting for first person.
Each material has its own strike sound (hide, copper, iron, diamond, Deepglass, netherite, starmetal); the bellows,
the quench, the set's melody. The hot metal glows through the armour shader's emissive and lights the scene.

**Server rules.** The Forge's block entity holds the session (schemaVersion 1): the piece, the materials taken in, the
stage, the beat clock's start tick and the marks. The client predicts the strikes for feel; the server judges each by
its tick and mark, with the same tolerance the weapons use for presses. Leaving, logging out or a crash leaves the work
on the anvil, kept; you pick up where you were. Breaking the Forge returns the materials. Others nearby see the sparks,
the glowing bar and the shape forming.

### 4.5 Materials: where everything comes from

| Rank | Metal (SPEC §43) | From the world | From gates and bosses |
|---|---|---|---|
| E | copper | hide and bone from the wilds' beasts, resin and wood, essence shards | none |
| D | iron (the caves, §34.2) | horn and thick hide | the D gate patterns; a D Lord's heart for Hornlord |
| C | diamond | frost pelts, catalysts for inlay | gate crystals; a C Lord's heart for Shardcrown |
| B | amethyst and Deepglass (the Singing Deep, §34) | Veil thread, Living Deep carapace | a B Lord's heart for Hollowheart |
| A | netherite | sky feathers (the Ascent) | Lords' scales for Lordscale |
| S | starmetal | Hollow Court thread | the Hollow Court for Sovereign Weave |

Each item names itself and where more comes from (the Armoury's three questions: what is it, what is it for, where do I
get more). Every new item has a source, so the Journey's ledger test stays green.

### 4.6 Fit with the Journey, hour by hour

| Hours (SPEC §43) | Rank | Armour |
|---|---|---|
| 0-4 | E | Wildhide first (the wilds' hide), then any of the four by hand at the works table |
| ~4 | E→D | the Forge opens (the Ringer's Proving); your first Forge Song is a D piece |
| 4-17 | D | four D sets from iron and gate patterns; Hornlord after a D Lord |
| 17-45 | C | Inlay arrives; diamond sets; Shardcrown after a C Lord |
| 45-70 | B | Tune arrives with Deepglass; Hollowheart |
| 70-88 | A | Fold arrives; Galewing's wings meet the Ascent's flight; Lordscale |
| 88-100 | S | Starfire; Sovereign Weave from the Hollow Court |

The Provings' gifts stay exactly as approved; Lord-forged armour comes from gate Lords' hearts, agreed with the bosses
and director sessions.

### 4.7 Data, state and old worlds

- **Item component `awaken:forged`** (schemaVersion 1; codec and stream codec): set id, quality, mastery experience,
  look (another set's look you have forged, or none), accent, maker (name, opt-in).
- **Player attachment `awaken:armoury`** (schemaVersion 1): armour patterns known, sets forged (for Looks), pieces made
  at Masterwork (for forging from memory).
- **Forge session** on the Forge's block entity (schemaVersion 1), as §4.4.
- **Old worlds.** The six Hunter sets keep their item ids, so every piece in a chest or on a player stays itself, gains
  its new look, and gets `awaken:forged` (Plain, mastery 0) the first time it is worn; its Temper is kept. Wildhide's
  Hunter's Sense becomes Pounce. Recipes for the Hunter sets move from the crafting grid to the Forge in the phase that
  builds the Forge (decision 9). Vanilla armour stays as vanilla made it.

### 4.8 Looks and accents (cosmetic)

Any set you have forged can be worn as the look of any other piece of the same slot (Looks); its stats and ability are
the piece's own. Accent colours come from inlays and dyes made in play. Looks and accents never change a number. On a
future guild server they are the only thing that could ever be sold.

### 4.9 Failure and recovery

| What fails | What happens |
|---|---|
| The armour shader does not build (driver, shader pack) | the same meshes draw through vanilla's entity cutout; a warning in the log |
| A set's mesh is missing | that set falls back to its painted flat look (kept from today's equipment textures until P5) and the log names the file |
| A client disconnects mid-forge | the work stays on the anvil, kept |
| A mod hides our layer | the set would draw nothing: the probe's shots and each set's CI tour catch it |

## 5. Demonstrations

### 5.1 The look probe (built on the branch)

**Lordscale, the A set, made in the new kit and drawn in the real game**: on an armour stand beside vanilla netherite,
close, from behind and the side, by night, worn by a player from the front and back and walking, and the gauntlet in
first person. Shot on GitHub's macOS runner with the CI tour `tools/autopilot/armour-probe.txt`.

Shots (in `docs/armour/shots/`, from CI run 40849793142 at commit 80aff6e0):

| Shot | What it shows |
|---|---|
| `armour_stands.jpg` | Lordscale on a stand beside vanilla netherite, same light |
| `armour_stand_close.jpg` | the helm, the T of gold over the burning slit, the rising pauldrons, the heart-stone |
| `armour_stand_back.jpg` | the cape in three links with the Lord's chevron, the horns rising behind |
| `armour_stand_side.jpg` | the horns sweeping back, the plates standing proud of the body |
| `armour_stand_night.jpg` | by night: the slit, the heart and the forearm lines glow |
| `armour_player_front.jpg`, `armour_player_back.jpg`, `armour_player_walk.jpg` | worn by a player, standing and walking; the set's cape replaces the player's own |
| `armour_fp_gauntlet.jpg` | your own forearm in first person |
| `lordscale_kit.jpg` | the kit's preview turntable (front, three-quarter, back, side, head close-up, night) |

**How it got here (four CI rounds, all on GitHub's runner):**

1. Run 40844472646: nothing but a flat helm floating over the head. Vanilla only hands armour that has an equipment
   asset to the render state; ours has none. Fixed with a render state modifier (commit ccece356, §4.1).
2. Run 40846146851: drawn everywhere, but it read squat and toy-like beside netherite: the helm twice the head with a
   crown of spines, the shoulders drooping, a gold ring at each elbow lining up with the belt, the tabard to the shin.
3. Run 40848430120: reshaped (commit f342ae3f) by the proportion rules now in §4.1. The figure reads tall and
   dangerous; the first-person forearm was still a plain box with a glowing slab.
4. Run 40849793142: the vambrace gets gold corners and a flush molten line (commit 80aff6e0).

**Not seen yet:** the cape in strong wind, the set on zombies (P0), slim arms, shader packs (falls back to the entity
cutout). The tours ran with Lens on Cinematic (`# ci knobs: LENS=cinematic`). At first that was because Lens Swift didn't
compile on the base this branch was cut from, and a new world's opening waited on it. That was fixed on `build/awaken-v1`
in 73cb1035, which is now merged here. The knob stays because Cinematic is the better light for hero shots.

### 5.2 The Forge Song, a piece at a glance

```
LAY      materials fly to the bench            1 s
HEAT     colour climbs   [dull ▓▓▓▓▓|GOLD|▓ white]   let go in the gold      3-5 s
SHAPE    ● marks glow on the bar; strike on the beat, on the mark
         beat: |  x  .  x  .  x  x  .  x |   (the set's rhythm)            6-12 strikes
         blank ─▶ rough ─▶ the piece; each clean strike adds a note
QUENCH   plunge on the cue: steam, hiss           1 s
FIT      onto the Fitting Stand; last piece: the set wakes              2 s
```

## 6. Evaluation and delivery

### 6.1 Checks

- **Art:** `armour_sets.py check` (budget, bones, plane conflicts) and previews for every set; a CI tour per set (stand,
  worn front and back, walking, night, first person) reviewed before merge; Euan's eye at each phase.
- **GameTests:** stats per set, set bonuses and each ability's numbers, the forge session's server rules (timing,
  marks, quality, resuming, breaking the Forge), codecs round trip with schemaVersion, old Hunter items gaining
  `awaken:forged`, the Journey ledger (every item has a source), and a check that no ability uses glowing, outlines or
  through-wall drawing.
- **Perf:** frame time with 10 and 20 armoured figures in view, on the Mac with Euan's OK, before P5 ships.

### 6.2 Phases (each one shippable; what Euan sees after it)

| Phase | Builds | Euan sees |
|---|---|---|
| **P0 The look, everywhere** | the probe made real: render path hardened (mobs, Skin Layers 3D, far meshes, the shared first-person arm call, no per-frame allocation); the six Hunter sets re-made in the kit; Hunter's Sense becomes Pounce | his current six sets transformed, on him, on stands and on others |
| **P1 The Forge Song** | the Forge session (heat, shape, quench, fit), the Fitting Stand, the cinematic camera and sounds, quality and starting temper, the Armoury's armour list and patterns, `awaken:forged`, the works table's E making; Hunter recipes move to the Forge | forging Beastbound at his Forge |
| **P2 E and D** | the seven new E and D sets, the four archetype abilities, mastery I-III | a real choice of four or five sets in the first 17 hours |
| **P3 C and B** | eight sets, Inlay and Tune, mastery IV-V (awakened geometry, auras), Looks and accents | the middle game's sets and the first auras |
| **P4 A and S** | eight sets, Fold and Starfire, the Lord-forged recipes with the bosses' Lords | the end game's sets |
| **P5 Polish** | the Hall of Arms, perf pass on the Mac, far-mesh tuning, the pacing sim, a sound pass | the finished ladder |

At AI pace (the probe, kit included, took this session a few hours): P0 about a day, each later phase a day or two.

### 6.3 Rollout and rollback

Each phase merges behind green CI and reviewed tour shots. The look has a client setting (Sculpted armour: on, near only,
off); off draws the flat painted look. Item ids never change, so rolling a phase back leaves every item in every world.

### 6.4 Risks

| Risk | What we do |
|---|---|
| Euan does not like the look | the probe is on the page before anything else; nothing ships until a set passes his eye |
| 29 sets is a lot of art | the kit makes a set a design function plus previews; sets ship in phases |
| Our first-person arm drawers miss the gauntlet | one shared call, agreed with the animations session in P0 |
| Many armoured players cost frames | far meshes, the triangle budget, a measured perf pass |
| Overlap with other sessions | listed in §8 and sent to the orchestrator |

## 7. Decisions for Euan

1. **The look:** sculpted meshes on the body's bones through our own shader, as the probe. *Recommended: yes.*
2. **The roster:** four archetypes (Bulwark, Strider, Channeler, Duelist) a rank, plus a Lord-forged set a rank from D:
   29 sets. *Recommended: yes.*
3. **The six Hunter sets** keep their ids and become one set each in their rank, re-made in the new look. *Recommended: yes.*
4. **Hunter's Sense goes** (it shows creatures through walls); Wildhide gets Pounce. *Recommended: yes.*
5. **Ore Sense and Vein Sight** (the pick shows ores through stone, SPEC §21.9 and §34) are the same kind of thing you
   called cheats. Not armour, so not this plan: should the mining owner take them out too? *Recommended: yes, by the
   caves/mining session.*
6. **The Forge Song:** heat, shape on the beat, quench, fit; skill sets quality, never success. *Recommended: yes.*
7. **A new verb a rank** (Inlay, Tune, Fold, Starfire). *Recommended: yes.*
8. **Forge from memory** after a Masterwork piece (6 s, Fine). *Recommended: yes.*
9. **Hunter recipes move from the crafting grid to the Forge** when the Forge Song lands (E sets by hand at the works
   table). *Recommended: yes.*
10. **Quality sets starting temper** (Plain 0 to Flawless III); Temper goes on as today. *Recommended: yes.*
11. **Mastery I-V** with the set's look waking as it grows. *Recommended: yes.*
12. **Looks and accents**, earned, never numbers. *Recommended: yes.*
13. **The cinematic camera on by default**, first person in settings. *Recommended: yes.*
14. **P0 first**: the look on today's six sets, before the Forge Song. *Recommended: yes.*

## 8. Overlaps with other sessions

- **Weapons (local_3ef9bc06):** the shader and mesh format are shared in kind (armour has its own copy); the Armoury
  gains an armour list; the Forge's use rules (empty hand opens the Armoury) stay.
- **First-person animations (local_347a1a8e):** the shared "dress the arm" call for the gauntlet in our own arm poses.
- **Bosses (local_ba9e45bf) and the game director (local_d7346187):** which Lords' hearts make the Lord-forged sets;
  Provings unchanged; the Journey ledger for every new item.
- **Caves and mining (local_d98fb048):** region materials (carapace, Deepglass); decision 5 (Ore Sense, Vein Sight).
- **The probe items** (`probe_lordscale_*`) have no source and would fail the Journey ledger: the branch is not for
  merge as it stands; P0 replaces them with the real Lordscale.
