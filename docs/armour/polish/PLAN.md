# The Finish (armour polish plan)

Plan for Euan's 2026-11-10 request, written by the armour-polish session on branch `claude/armour-polish-plan-5e3628`.
Status: **waiting for Euan**. Nothing here is built for real. Follows `.claude/PLANNING-PROCESS.md` and holds every
choice to `.claude/UI-EXCELLENCE.md`, read for armour. The approval page is `docs/armour/polish/page/` (link in §9).

## 1. Understanding and success contract

### 1.1 His words
> "And then finally, a third one that looks at the level of polish for the armor sets."
> "I don't want to completely scrap everything. It's more so a way to really see what's the kind of small polish pass
> that we can do to massively bring it to the most extreme and awesome level."

### 1.2 The brief
- **The job.** Say how polished the 29 forged sets really are, in every place a player meets them, and find the
  changes that lift them most.
- **The pain.** He approved one set (Lordscale, 29 Oct: "so fucking good") and asked for 28 more at that bar. They were
  built in a day and he has not looked at them closely since. He does not know which are at the bar.
- **Wanted.** A small pass with a large effect. Bigger options too, each with its cost.
- **Must keep.** The sets, their helms and silhouettes, their abilities, the Forge Song, the Hall of Arms. His rule of
  29 Oct: bold first designs beat reworks, no sculpted faces, epic and mysterious. His verdict on the earlier look
  ("so clunky"): no boxes stuck on, and the game is the judge, never my own renders.
- **Unknown until checked.** How each set looks worn, from your own eyes, at night, far off and in the new K menu.
  All checked below.

### 1.3 What "polished" means here, in things you can see or hear
| Where you meet a set | Strong | Weak | Not acceptable |
|---|---|---|---|
| Worn, by day | a stranger names it from its shape and its two colours | reads as "a dark suit with pale frames" | parts float, clip or flicker |
| Worn, at night | you still know which set it is | a black shape with one lit dot | a band of light crossing it in the dark |
| From your own eyes | a gauntlet you would know as that set's, lit by its own charge | a box with a stripe | bare skin where the armour should be |
| On a stand, in the Hall, in the K menu | the same suit, lit to show its metal | flat | a different look from the worn one |
| At 24 and 48 blocks | shape and glow carry | a grey figure | shimmer |
| Moving | cloth, plumes and hanging things move; the thing that should float, floats | a statue that walks | parts swing through the body |
| Sound | putting it on sounds like its metal; whole, it has a voice | one vanilla clink for every set | synthetic rings |
| Getting it | the last piece going on is a moment | a number changes | nothing at all |

### 1.4 Non-goals
- No new sets, no new abilities, no new numbers.
- No reworked helms or silhouettes.
- No denser textures and no smooth meshes: the sets keep the weapons' texel size, so the two stay one family.
- No renderer internals (no mixin). No Mojang audio in the repo: sounds are named from the game's own files.

## 2. Verified audit

How it was checked: the code read end to end (`dev.awaken.armour`, its shader, the art kit); every set rendered offline
with the kit (front, back, close, night); every set shot in the real game on GitHub's runners in the same views
(`docs/armour/polish/tours.py`): worn front and back, first person, the K menu's Status figure, an armour stand beside
a Fitting Stand, rank rows at 12, 24 and 48 blocks by day and at 12 and 24 by night, and a slowed cast in first person.
Numbers come from `armour_sets.py check` and from scripts over the built textures and meshes.

### 2.1 What is strong (keep)
- The render path: meshes on the body's bones through our own shader, on players, stands, mannequins and mobs. It works
  everywhere it was meant to and follows every animation.
- Silhouettes. On a grid of all 29 at thumbnail size, about twenty name themselves at once: the wolf hood, the bell,
  the antlers, the skull, the lanterns, the horns, the sun disc, the crystal crown, the ribs, the halo, the hat, the
  diamond marker.
- Lordscale, Sovereign Weave, Veilwoven, Rootwarden and Wildhide carry 7 to 12 surface treatments each.
- Capes and leg cloth move. Mastery grows parts and an aura. Looks and accents work.
- The Forge Song, the Fitting Stand and the Hall of Arms exist and show the suits.

### 2.2 What is not (each one verified, with where)
| # | Finding | Evidence | Consequence |
|---|---|---|---|
| F1 | **In first person the armour comes off your arms whenever our own code draws them.** `ArmourClient.dressArm` is public and nothing calls it. | No caller in `src/main/java` outside its own file. `CastAnims.drawArm`, `Hands.drawArm`, `Wrist` (two sites) and `WorldBody.limb` all draw the skin arm only. CI shots `c_rest` (Lordscale arm) and `c_cast_a..c` (bare skin, mid-cast). The original plan listed this as a risk (§6.4) and it was never closed. | Casting, the wrist band, two-handed moves, climbing, vaulting and sliding all show bare skin. This is the view he plays in. |
| F2 | **Your own set's charge never shows to you.** | `dressArm` passes energy 0 and flash 0 to the shader. Everyone else sees your set fill and flare; you do not. | The one place a charge meter could live without a HUD is dark. |
| F3 | **A pale band crosses armour in the dark.** | `armour.fsh` adds the feet-to-crown sheen after the world's light is applied, and mastery's trims too. Night shots show lit boots and shins on dark figures. | In caves and at night every armoured figure has a light crawling up it every five seconds. |
| F4 | **The sixteen sets built last share one surface.** | Distinct surface painters per set: Lordscale 12, Sovereign 12, Veilwoven 9, Rootwarden 9; every C to S set that is not a Hunter set has 3 to 5, almost always "lames, rims, folds". Their plates are one rimmed panel. Boxes per set fall from 67 (Lordscale) to 32 (Dreamsteel, Signal's Echo). | In first person and at a distance these read as framed boxes. Nine sets sit in one blue-grey average colour (Leviathan, Galewing, Dreamsteel, Sovereign, Starfall, Nightcourt, Vaultbreaker, Gatewrought, Cloudrunner). |
| F5 | **At night most sets are a black shape.** | Share of the front that glows: 0% on 3 sets, under 2% on 13. From behind, 13 of 29 glow nowhere. The halo (Halo Choir), the diamond marker (Signal's Echo), the wings (Galewing) and the crown (Sovereign) are dark at night. | The thing a stranger would name disappears after sunset. |
| F6 | **The hand is not a hand.** | Every set's forearm shell runs to the knuckles as one box, six texels wide; the hand is the same box. | Your own view is a box with a stripe. In third person the hands are mittens. |
| F7 | **No sound of its own on the body.** | 29 sets share five vanilla equip sounds (A and S both netherite). No movement sound. | Nothing tells hide from plate by ear. |
| F8 | **The sounds that exist predate his sound rules.** | `audio_forge.py` (29 Oct) builds all 25 armour and forge events from Kenney impacts and the synth kit's rings, the method he called "super synthetic" on 9 Nov. His checker flags 34 of the 57 files (13 strongly). It also flags real metal, so it is a tripwire, not a verdict. Nobody has heard them. | The ability sounds and the Forge Song's strikes likely fail his ear. |
| F9 | **A set becoming whole on you is silent.** | `ArmourAbilities.count` knows the moment (`was` against `s.full`) and does nothing with it. | The ability switches on with no light, sound or word. |
| F10 | **What the forge did does not show.** | `Forged` carries quality, flaw, twin-struck, fold and true ring. The armour shader draws none (the weapons' shader draws flaws, a pattern weld and temper colours). | A Flawless piece and a Plain one look the same on the body. |
| F11 | **Promised motion never built.** | Plan §4.2: Galewing's cape "opens like wings when you fly", Dreamsteel's "fragments drifting". Both are rigid. Halos, markers, lantern cages, plumes and wings are rigid on every set. | Suits read as statues that walk. |
| F12 | **Skin shows on five sets.** | Share of the front that is the wearer's own skin: Wildhide 9%, Sunward 6%, Cleftrunner 4%, Veilwoven 3%, Amberwing 2%. | With a bright skin the set's palette breaks. |
| F13 | **A smear in two old night shots.** | Two of the building session's five night row shots (runs 40886121168, 40896380184) show screen-wide coloured sheets. Six new night rows with the same sets at mastery V are clean. | Not reproduced. The runner's GPU is known to scramble some draws (SPEC §13). Watch item, no fix planned. |
| F14 | **Never measured.** | The original plan's perf pass (P5) did not run. A figure is about 17 draws and 2,000 to 4,000 corners written each frame. | Unknown cost with many armoured players in view. |

### 2.3 Set by set
Rank, archetype, triangles today, surface treatments, then what the eye says.

| Set | | Tris | Surf. | Today | The finish gives it |
|---|---|---|---|---|---|
| Wildhide | E Strider | 1066 | 7 | The wolf hood reads at once. Bare arm shows your skin (9%). Hand is bare. | A wrapped glove and rolled cuff. An under-sleeve where the arm is bare. |
| Bellguard | E Bulwark | 888 | 4 | The bell reads. Bronze and verdigris are its own. | A glove under its bell-mouth cuff. Little else needed. |
| Rootwarden | E Channeler | 1080 | 9 | Antlers and moss, strong. | A bark glove. Leaves that stir. |
| Amberwing | E Duelist | 780 | 6 | The back (crossed amber wings) is one of the best. The front helm is a dark box with a band. | Wings lit more, an amber cuff. One break in the helm's brow at build. |
| Beastbound | D Bulwark | 968 | 6 | The skull and tusks he chose. No light at all, right for D. | A tusk-bone cuff and a riveted hand plate. |
| Cleftrunner | D Strider | 660 | 4 | Thinnest build of all (33 boxes). Reads by blue and red alone. | A scarf tail that streams. Depth in the hood's drape. |
| Lanternlight | D Channeler | 776 | 4 | The shoulder lanterns read. | A brass cuff, lamp-light on the hand. Lantern cages that swing. |
| Bladeward | D Duelist | 714 | 7 | The T helm, plume and one great pauldron read. | A steel gauntlet. A plume that moves. |
| Hornlord | D Lord | 872 | 5 | Horns and ember, strong. | A gold cuff, its ember line run onto the hand. |
| Gatewrought | C Duelist | 792 | 6 | The ring of five stones is good. The back is a blank slab. Grey on grey. | Gate-light on the hand. The gate's arch on the back. |
| Vaultbreaker | C Bulwark | 1086 | 4 | Ram horns and the shield pauldron read. All grey. | Diamonds that hold light. Riveted plate, not rims. |
| Frostwalker | C Strider | 826 | 5 | The icicle crown reads. Its palette is its own. | Cut-ice plates. A lit ice plate on the hand. |
| Sunward | C Channeler | 1410 | 5 | The sun disc is one of the best shapes in the game. Dark at night. | The disc lit after sunset. Rays that turn slowly. |
| Shardcrown | C Lord | 864 | 3 | The crystal crown reads. The body is dark on dark. | Crystals lit from inside. Cut faces on the steel. |
| Veilwoven | B Channeler | 1034 | 9 | Hood and veils, approved. | A glove. Hems that drift. |
| Deepcarapace | B Bulwark | 682 | 3 | Lowest contrast of all: a dark blob with a teal brim. | Shell segments with pale lips. The seam run onto the hand. |
| Cloudrunner | B Strider | 1028 | 3 | White and blue, fins. Fine, plain plates. | Feathered plates. |
| Tidebreaker | B Duelist | 808 | 5 | One teal mass. | Amethyst spines that hold light, a violet cuff, scales. |
| Hollowheart | B Lord | 986 | 3 | Ribs and the caged heart, strong. | Rib segments, a bone cuff. A heart that beats. |
| Lordscale | A Lord | 1204 | 12 | The bar. | Its molten line run onto the back of the hand. Nothing else. |
| Leviathan | A Bulwark | 908 | 4 | Dark blue with pale frames. Mistaken for Galewing at a distance. | Scales for frames, fins that glow like deep-sea things, a bone cuff. |
| Galewing | A Strider | 1192 | 3 | Frames. Sits in the blue-grey crowd. Wings rigid. | Lighter steel, feathered plates, wing edges in storm light. Wings that open when you fall or glide. |
| Halo Choir | A Channeler | 1222 | 4 | Halo and wings, strong by day. The halo is dark at night. | The halo lit. It tilts and breathes. Fluted porcelain. |
| Dreamsteel | A Duelist | 558 | 3 | Thinnest A set. The helm is a plain pale box. Its drifting shards do not drift. | Shards that drift and catch light. Cut-mirror plates. |
| Sovereign Weave | S Lord | 1070 | 12 | The dark faceplate and lilac crown he chose. | The crown lit at night, its line run onto the hand. |
| Starfall | S Bulwark | 838 | 3 | The star in the breast reads. Limbs are frames. | Cut faces, a silver cuff. Seams that flare on a hard landing. |
| Riftwalker | S Strider | 810 | 3 | The rift behind you is the idea. The hood is 7 boxes. | The rift flickers and tears wider when you dodge. |
| Nightcourt | S Duelist | 692 | 4 | Hat, mask and coat: its own. Thin build. | A leather glove under its silver cuff. Tails and plume that move. |
| Signal's Echo | S Channeler | 634 | 3 | White robes and the System's marker. Thinnest S set. Its "glitch" is still. | The marker lit, turning and bobbing. Robes that really glitch. |

### 2.4 Menus and the stand
- The Fitting Stand and the Hall draw the same meshes through the same shader. They match the worn look.
- The K menu's Status figure (the new stage) draws the wearer large and well. Nothing near it says which set you wear,
  how whole it is or its mastery.
- Item icons are drawn from the meshes at 32 pixels, with the weapons' outline. They read.

## 3. Root causes
| Symptom | Cause | Evidence | Smallest test | If fixed |
|---|---|---|---|---|
| Bare arms in first person (F1, F2) | The shared "dress the arm" call was written and never wired in; the drawers were written by other sessions later | no callers | call it from the five drawers (drafted, compiled, shot on CI) | every arm and leg you see is armoured and shows your charge |
| Frames and sameness (F4) | 23 sets in one day: each later set got a helm of its own and limbs from one recipe | painters per set, boxes per set | swap the rim painter for a language per set on the limbs (drafted) | each set's plate is its own up close |
| Black at night (F3, F5) | The plan's rule "glow is few and placed" was applied to the named feature too; the shader gives a dark figure no edge | glow share per set; shader read | light the named feature from C up; a moon edge under open sky (drafted) | a set is itself after sunset |
| The box hand (F6) | The forearm shell was one box to the knuckles on the probe and every set copied it | every set's lower arm boxes | cut the shell at the wrist, a slimmer glove, one plate (drafted) | a gauntlet in your own view |
| Sound (F7, F8) | The sound pass was left for later and the rules came after | `audio_forge.py`, the checker | name Minecraft's own recordings, audition locally | sets you can tell by ear |
| Silent moments (F9, F10) | Built as state, never staged | `count`, `Forged`, the two shaders | a wake on the body; the weapons' forge marks on armour | getting and making show |
| Statues (F11) | The layer has bones only for body, cape and cloth | `ArmourLayer.place` | a "living part" bone the kit can tag | halos, plumes, wings and lanterns move |

Still a guess: whether the form light and moon edge read well under each Look (Swift, Cinematic, VFX). The CI tours run
Cinematic only.

## 4. Alternatives and the recommendation
| Way | What it is | Why / why not |
|---|---|---|
| A. Fix the faults only | F1, F2, F3, F9: wiring and the shader | A day. Needed whatever else happens. Does not touch the sameness or the dark. |
| **B. The Finish (recommended)** | A plus one shared pass that lands on all 29 through the shader, the kit and the layer, then a hand-finish of the sixteen later sets' limbs and backs. Helms and silhouettes untouched. | The large effect he asked for from the smallest set of changes: most of it is written once and every set gets it. |
| C. Second design pass on the later sets | New helms and silhouettes for the weak ones | Breaks his own rule (first instincts beat reworks) and risks the "weird faces" he rejected. |
| D. Denser textures or smooth meshes | Twice the texels, or sculpted surfaces | The road the rejected look went down, and it would part armour from the weapons. |

What would change the recommendation: if the after shots do not read better to him in the game, stop at A.

## 5. The Finish, change by change
Each: problem → change → what you get → what it keeps → cost or risk → how it is checked.

**5.1 Every arm and leg you see wears your armour.** F1, F2 → one hook in `core.api.FirstPerson` that armour registers
on; the cast arms, the off arm, the wrist, the world body's arms, fists and legs call it; your own charge and flare go
to your own arm → casting, climbing and the wrist band show your gauntlet, and it fills with light as your set charges
→ keeps every animation as it is → the powers session plans an arm with an elbow and wrist: the arm mesh is cut in
three there (upper arm, forearm, hand) so it bends with theirs; weapon-feel films the grips once → a CI tour of a cast,
a climb and the wrist in three sets; a GameTest that the dresser is asked for by each drawer.

**5.2 A hand that is a hand.** F6 → the forearm's shell ends at the wrist in a flared cuff of the set's trim; under it
a glove half a texel proud (slimmer than the forearm), fingers grooved; a plate on the back of the hand carrying the
set's light from C up; the four sets with a molten line keep it running down the hand → a gauntlet you would know as
that set's, in the view you play in; finer hands in third person → built from each set's own materials, at the same
texel size → the first try added stud cubes and looked stuck on, so the rule is: nothing added that is one cube →
before and after of all 29 in first person on CI.

**5.3 Light.** F3, F5 → the shader: edges turned to the sky take a little of it (bright metal most, cloth a soft fuzz
instead); under an open night sky what is turned up still catches the moon (never underground or by a fire); the sheen
and trims go under the world's light. The kit: what glows spills a third of its light onto the texels beside it. From
C up, the feature a stranger would name is lit (halo, sun disc, crown, crystals, marker, wing edges). E and D stay
dark: rank reads by how much of a set is lit → a set is itself at night and its plates part from one another by day →
no change to any colour by day → a little glow added to approved sets' crowns (Sovereign) → day, dusk and night rows
on CI in Swift and Cinematic; his eye.

**5.4 A language for each set.** F4 → on limbs, belts and backs of the sixteen later sets the one rimmed panel becomes
the set's own: riveted vault plate, cut ice, shell segments, feather lames, scales, fluted porcelain, cut mirror; elbow
and knee cops and an under-lame where Lordscale has them and they do not; the plain backs get one feature each; the
blue-grey crowd is pulled apart (Galewing to pale steel, Leviathan to deep teal and bone) → each set is its own up
close and from behind, at Lordscale's depth → helms, silhouettes and palettes of the approved six untouched → the
largest part of the work: sixteen sets, each with a tour → the kit's check (budget, planes), a tour per set, his eye
per rank.

**5.5 Living parts.** F11 → a new kind of bone the kit can tag on a part, with a pivot and one of five motions: turn,
bob, sway (from your movement, like the cape), spread (from falling or gliding) and drift. One or two per set: halo,
marker, sun rays, lantern cages, plumes, scarf tails, wing pairs, shards, the rift. Mastery's grown parts grow in over
half a second, not in one frame → suits that live → nothing moves on a set that should be still (Bellguard, Beastbound)
→ motion can clip: every moving part gets a sweep check in the kit → a filmed tour per motion kind.

**5.6 Sound.** F7, F8 → from Minecraft's own recordings, named in `sounds.json`: an equip sound per material family
(hide, cloth, mail, plate, heavy plate, crystal, bone, feather); the set's own three notes when it becomes whole on you
(the notes of the Forge Song you made it with); soft movement sound by weight and material, quiet when you sneak, with
its own volume setting; the 25 old events remade the same way → sets you can tell by ear → no Mojang audio in the repo →
I cannot hear: a local listening page (best guess and two alternatives per row), then his ear → his ear is the gate.

**5.7 Moments and marks.** F9, F10 → when the last piece goes on: the seams light feet to crown, the set's three notes,
one line from the System the first time; the weapons' forge marks on armour (a flaw shows, a fold shows as a weld
pattern, a Flawless piece carries its maker's mark); the Status figure gets one quiet line under your name: the set,
pieces worn, mastery → getting and making a set show on your body → no new screen, no toast → the K menu is another
session's: the line is offered to them, not built over them → a tour of the wake; GameTests for the marks' data.

### 5.8 Contracts
| Piece | Trigger and input | What it does | State kept | When it fails |
|---|---|---|---|---|
| The dresser | a first-person drawer has drawn a skin limb and asks `FirstPerson.dress(limb, pose, collector, light)` with the pose on the limb's bone | armour (registered on the hook) draws the worn piece's mesh for that limb with the wearer's own charge, flare, mastery and accent | none | Sculpted armour off, or no mesh: draws nothing and says so (the drawer keeps its skin limb). An exception turns the dresser off for the session and logs once. |
| The arm in parts | the powers plan's elbow and wrist, if it lands | the kit writes the arm as upper arm, forearm and hand; third person draws all three on the arm bone as today | none | parts missing: the one-piece mesh is used |
| Night light | none: it is in each set's design | an emissive value on the named feature's material; the spill is baked into the texture | none | n/a |
| Form light and the moon edge | every armour fragment; the lightmap, the sky light in UV2, vanilla's two light directions | adds light to edges turned to the sky; at night only under open sky and only as far as the world is dark | none; no new uniform | the armour pipeline did not build: vanilla's cutout draws the same mesh, as today |
| Living parts | a part tagged in the kit: its bone, a pivot, a motion (turn, bob, sway, spread, drift), a speed and an amount, written beside the meshes | the layer moves that part's mesh from the render state alone (age, stride, fall speed, the cape's lean) | none per entity, so nothing to sync or save | no tag file: the part is rigid, as today. Setting: Armour motion on / off. |
| Equip sound | a piece is put on (vanilla's own equip event) | the piece's material family's sound, set on the item, so everyone near hears the same | none | the event is missing from `sounds.json`: vanilla's rank sound, as today |
| A set made whole | `ArmourAbilities.count` sees `full` change from another value to a set | server: the set's three notes to those near, a `Whole` message to trackers. Client: the seams light feet to crown over a second. The System's line once per set per player. | which sets you have ever worn whole (in the armoury memory, schema +1, old saves read as none) | swapped in and out fast: ten seconds between plays for one wearer |
| Movement sound | a visible armoured player's step, jump, landing or sprint, on each client | a soft sound by weight and family, none while sneaking, at most one every few ticks, its own slider | none | n/a |
| Forge marks | a piece's `Forged` story at draw time | quality and fold ride in the overlay's spare bits; the shader draws the weapons' marks | none | bits run out: twin-struck and true ring stay in the tooltip |

### 5.9 Cost in frames (to measure, not promised)
- A gauntlet adds 50 to 170 triangles a set (measured on the draft: the heaviest set is 1,564 of a 3,000 budget).
- A living part is one more draw. The shader's new terms are a handful of operations with no texture read.
- First person draws two more meshes whenever an arm of ours is on screen.
- Twenty armoured players in view has never been measured, before or after. That run needs the Mac and his OK.

### 5.10 Faults the plan must not add
| Risk | Guard |
|---|---|
| A cuff or plate that sits in the same plane as an existing part and flickers | the kit's plane check, already extended to the new parts (it caught six sets in the draft) |
| A moving part that swings through the body | a sweep check in the kit: the part at both ends of its motion against the figure's boxes |
| The moon edge reading as an outline in caves | it is multiplied by sky light; a tour underground |
| The night features too bright by day | day rows on CI; his eye |
| Slim-armed skins | the arm mesh is cut for the wide arm; a tour with a slim skin before stage 2 merges |
| Other mods' first-person arms | the vanilla path (`RenderArmEvent`) is untouched |

## 6. Bigger options (not in the recommendation; each can be added)
| Option | What you get | Cost at our pace | Note |
|---|---|---|---|
| Admire | Hold a key: the camera circles you once in a hero light | half a day | The cheapest way to enjoy a set you made |
| Abilities that use the suit | Galewing's wings beat on Gust, the halo rings on Hymn, the rift opens on Riftstep, Starfall's seams blaze on Meteor | two days (29 bespoke) | The step from polished to alive in a fight |
| Show your face | A Looks switch: helm shown, or only its crown and horns | half a day | Some players want their skin's face |
| Footprints and trails | Frost prints, embers, feathers: one per set from A up | a day | Needs our own particles, not vanilla's |
| Many players | Far meshes and a measured run with 20 armoured figures on the Mac | a day, and his OK to launch the client | Only matters for the server dream |

## 7. Evaluation and delivery
- **Baseline:** the "before" tours (same commit as build plus the tours), 191 shots.
- **Gates per stage:** `armour_sets.py check` clean; the stage's CI tour reviewed shot by shot for clipping, floating
  parts and scale; the GameTests green; SPEC §44 updated in the same commit; his eye before the next stage where the
  look changes.
- **Stages** (each lands on its own, each can be turned back by reverting one commit; item ids and saves never change):
  0. Faults: F1, F2, F3, F9's hook (wiring, the shader fix). 
  1. Light: the shader, the spill, the night features.
  2. Hands: the gauntlets.
  3. Languages: the sixteen sets, rank by rank (C, B, A, S), a stop for his eye after C.
  4. Living parts.
  5. Sound: listening page first.
  6. Moments and marks; the Status line with the K menu's owner.
- **Before building:** merge `build/awaken-v1` (the One Stage UI lands there) into this branch.
- **Rollback:** the look has its setting already (Sculpted armour on, near, off). Living parts and movement sound each
  get a setting. A stage that he dislikes is reverted whole.
- **Effort at our pace:** stages 0 to 2 a day together; stage 3 a day and a half; stages 4 to 6 a day each. About five
  days of one session, less if stage 3 stops early.
- **Risks:** the hand change touches every set he has seen (kept true to each set's materials, shown before and after
  for all 29). Sound without ears (his). Motion that clips (sweep check). Other sessions in the same first-person code
  (notes exchanged; the hook lives in core).

## 8. Where armour and the weapon plans meet
- **Weapon models:** the Gauntlets weapon wins the hand, armour keeps the forearm; armour takes the weapons' forge
  marks; a Lord's weapon and that Lord's armour share metal and glow; if blades get a ground edge, armour trims take the
  same edge treatment.
- **Weapon feel:** one film check of the grips with the new gauntlets; one family of sounds for hits on armour.
- **Powers:** their light on the arm sits over the gauntlet, or drives the gauntlet's own glow through the shader's
  energy and accent; their elbow and wrist set where the arm mesh is cut.

## 9. Decisions for Euan
See the page. In short: the pass as a whole; the night rule; the hands; movement sound on or off by default; which
bigger options; a Mac run for the many-players measure.
