# Hero bodies, the creatures' voices, and the animation pass

The owner said the overworld creatures looked "very vanilla, like add-ons instead of mods": most named beasts were
scaled-up recolours of their family's small creature on a vanilla body. This pass gives every named beast its own
body, texture, glow and keyframe animation tied to what it is really doing, gives the Hollow Owl and the Bogwick real
bodies, reworks the noisiest textures, animates the small custom bodies, and gives every overworld creature its own
voice.

## The hero bodies

| creature | body | signature move (wind-up, then strike) | other moves |
|---|---|---|---|
| The Briar Sow | hunched forest boar, high shoulders sloping to the rump, long low skull with curled tusks, moss mane, a thicket of thorny canes, berries and wild roses on her hump | Bramble Charge: head right down, tusks forward, the canes bristle, a hoof scrapes twice, the rush | Root Stomp (rears, slams), Pollen Haze / Thorn Burst (shakes the hedge), Last Stand (roar) |
| The Dune Wyrm | plated sand-swimmer with a heavy head, three-way jaw (side mandibles), a crest and fins of amber sun-glass, molten seams; swims with an up-and-down wave | Surface: dives head-first as if into sand, then bursts up, jaws flung wide | Sand Dive (S-coil, snaps straight), Sandfall / Sunscorch Ring / Glass Shards (rears and rattles its glass) |
| The Old Antler | ancient elk, pale shag skirt and mane, bark grown over the shoulders with faint runes, a royal crown of antlers with spirit-lights hanging from it | Antler Rush: crown lowered level, a hoof scraped twice, haunches gathered, the charge, a toss | Hoof Quake / Root Snare / Rime Ring (rears and crashes down), Bellow, Crown Light (lowers the crown slowly as the lights swell) |
| The Mire Mother | toad nearly all head, heavy brows, bulging glowing eyes, a toadstone set in bone knobs, a throat sac, the bog growing on her back (mud, lily pads, reeds, mushrooms) | Tongue Lash: head back, the sac balloons, the mouth gapes, the tongue shoots out 30 px | Belly Flop, Great Leap, Bog Breath, Toadstone Shards (bows the blazing crown) |
| The Plumed Coil | slender jade serpent with gold diamonds, a feathered headdress, a collar of plumes, side plumes and quetzal streamers; slithers side to side | Coil Strike: rears into a tall S, the collar flares and rattles, then the lunge | Canopy Drop, Venom Spray, Venom Rain / Plume Storm / Wingbeat (every feather flares) |
| The Weaver Queen | spider queen with jointed legs held knee-high, a crowned cephalothorax with eight glowing eyes, hooked fangs, a great banded abdomen with an hourglass crown mark, silk from the spinnerets | Pounce: rears on her back legs, forelegs spread, fangs open, then drops on you | Web Snare / Silk Drop (abdomen swings under, flick), Venom Spray, Shroud, Web Burst; dies on her back, legs curled |
| The Empress Moth | furred, collared body, feathered gold antennae, great dusk-rose wings with gold eyespots and trailing tails | Wingbeat: wings up and back, then one huge downstroke | Petal Storm / Scale Burst (wings shiver), Slumber Dust, Dive, Eyespot Glare (wings held flat, eyes staring) |
| The Stonehorn | boulder-sized ram, a cairn of lichened rock on its back with cold light in the cracks, stone ram's curls for horns, a matted wool skirt | Ram Charge: backs a step, drops the horns level, scrapes twice, the charge | Stamp / Rockfall / Quake Ring (rears, crashes, the rocks jump), Avalanche Bellow |
| The Mycelord | walking mushroom tree on root feet, a hollow old face with pink lights, shelf fungi, root arms to the knees, a vast spotted cap with glowing gills | Cap Slam: both arms overhead, leans back, then bows and brings the cap down | the spore moves (cap swells and lifts, gills open, then exhales), Mycel Ring |

Every hero also has idle (breathing, secondary motion on manes, canes, lamps, reeds), walk and run (or a flight loop),
a generic wind-up and strike for anything unmapped, a melee bite, a roar (fights, phase changes), a warning (the
territory warning), a flinch, and a death that replaces vanilla's sideways flip (the boar falls on her side, the
queen rolls onto her back, the toad dies belly-up, the moth spirals down, the Mycelord topples with its cap).

The Hollow Owl is a real owl: round body, facial disc with two great eyes and a blink, ear tufts, folded wings that
spread for a silent glide (no wingbeat, the body rocks on the air), and a head that turns almost to its back (its
idle swivels the head 115 degrees and back; `getMaxHeadYRot` is 160 for owls). The Bogwick is a firefly: elytra,
buzzing translucent wings, and a banded lantern sac that swells as its glow pulses.

Sizes: heroes are modelled near true size (1.0 to 1.8 entity scale instead of 1.5 to 4.2), so texels stay close to
vanilla's and they no longer look like blown-up small creatures. Every collision box is the one it had before
(`Model.<HERO>` width and height times the def's scale); antlers, canes and wings exceed it a little.

## How it works

- One source per creature: `tools/art/specs/fauna_heroes.py` (geometry, paint, keyframe clips, the ability-to-move
  map) on the toolkit `fauna_rig.py` (bone tree, skyline-packed UVs, face painting with clean value masses, a
  sampler identical to vanilla's KeyframeAnimation, posed rendering) and `fauna_anim.py` (cycles, gaits, waves).
- `tools/art/specs/heroes.py build` (also `build_all.py heroes`) writes the textures and
  `mobs/client/anim/HeroData.java`, the rigs as plain text data; `HeroRig` parses it with no Minecraft classes, so the
  server and GameTests check every rig (what vanilla's bake would refuse, UV bounds, required clips, moves).
- `mobs/client/model/HeroModel` builds the LayerDefinition and bakes every clip into a vanilla AnimationDefinition.
  setupAnim layers: idle, a walk and run locked to distance travelled (no foot sliding even in a charge), a flight
  loop for fliers, the glide when an owl is airborne, head look, the cue clip, the melee bite, the flinch, the fall.
- `mobs/client/anim/HeroRenderer` (registered for the hero models in `AwRenderers`) fills `HeroRenderState` from the
  entity, draws the emissive glow layer (it flares during wind-ups and fades as a beast dies) and skips the death flip.
- The entity side (`FaunaMob`) syncs one int, the cue: the move slot (index in the def's distinct ability names), the
  stage (wind-up, strike, roar, warning) and a serial. It follows the real `AbilityRunner`: a wind-up cue when an
  ability starts winding up, a strike cue when it fires; the client stretches the wind-up clip to the ability's real
  wind-up (heavy moves +8 ticks) so it peaks as the move lands, and fades a wind-up that was cut short. `NamedBeast`
  cues the territory warning, the roar when it is provoked and at each phase change (interrupting any wind-up), and
  holds its death for 36 ticks so the fall plays out. The signature move of each beast (`FaunaMob.SIGNATURES`) opens
  with the beast's roar on top of the usual telegraph.

## The animation pass on our small bodies

`mobs/client/model/Motion` gives the lizard, snail, mushroom, marmot, moth, tortoise, scarab, ray and serpent (and
the stag) a breathing idle, head look toward what they watch, and a flinch when struck (`AwLook.hurt` from
FaunaMob; gate serpents shudder while their hurt tint shows): the tortoise pulls its head in, the snail its eye
stalks, the mushroom squashes like jelly, the moth's wings snap up, the scarab clamps down. Gaits stay locked to
distance travelled.

## Textures reworked

The Honey Bear and the Scree Marmot (fauna2.py) and the Old Antler (now a hero) are painted with clean value masses:
light from above, a lit top edge, a dark foot, a soft gradient, sparse structured strokes instead of per-pixel noise.

## Voices

`tools/art/specs/fauna_sounds.py build` synthesises (numpy, offline) ambient, hurt, death and step sounds for all 34
overworld creatures (a flier's step is its wingbeat, played as it flies) and a roar or cry for each beast: the Sow's
squealing roar, the Wyrm's gravel bellow with glass chimes, the Old Antler's bugle (a groan breaking into a whistle),
the Mire Mother's three booming croaks, the Coil's rattle, the Queen's chittering shriek, the Empress's choir, the
Stonehorn's bellow with falling rock, the Mycelord's hollow moan. 209 mono Ogg Vorbis files (1.8 MB), 145 events in
`src/main/merge/fauna/assets/awaken/sounds.json`, subtitles in the fauna lang file. `sheet DIR` draws a spectrogram
of each to judge by eye.

Quiet by design: ambient creatures call about every 45 s at 0.45 volume, neutral ones every 30 s, hunters every 21 s,
beasts every 18 s and louder (their roars carry 48 blocks). Steps play the creature's own footfall over the block's.

## Try it

- `/summon awaken:briar_sow` (any hero id), `/awaken fauna lineup p1|p2`, or strike a beast in survival to see the
  warning, the roar, its wind-ups and strikes.
- Autopilot: `tools/autopilot/heroes.txt` (every hero NoAI on a sky platform from three sides by day and by night,
  then each beast with AI on, struck by the player: shots at its wind-up, its strike, and its fall).
- Previews: `python3 tools/art/specs/heroes.py preview DIR [ids] --before OLD_TEXTURES` (three day views, a night
  view, old body beside the new one and a player, poses: idle, walk, run or flight, the signature wind-up and strike,
  roar, flinch, death).

## Tests (`mobs.entity.HeroTests`)

`heroes_beasts_wear_their_rigs` (every named beast on a hero body, the owl and the bogwick too; each rig sound, its
moves real abilities, the signature move animated, the required clips), `heroes_textures_exist` (skin and glow at
the rig's size; the reworked textures), `fauna_sounds_registered` (every creature's sounds in the sound registry,
in sounds.json, with their files), `heroes_cue_encoding`, and isolated `heroes_cue_follows_abilities` (a real Briar
Sow fighting a target: the cue names the ability being wound up, then cues its strike, and the beast takes its full
fall before it is removed).

## Changes outside the listed paths (small, additive)

- `mobs/def/Model.java`: eleven hero body constants and `hero()`.
- `mobs/MobSounds.java`: registers `fauna.<id>.<kind>` for every overworld creature (the module's sound register).
- `mobs/MobsTests.java`: one line registering `HeroTests`.
- `mobs/entity/AwLook.java`: one field, `hurt`, for the flinch.

## Known gaps

- Not seen in a real client yet (builders don't run one): judged on the offline previews, which render the real
  geometry posed by the same keyframes; `heroes.txt` is for the integrator.
- The sounds are synthesised and judged by spectrogram only; nobody has listened to them in game.
- The Plumed Coil and the Dune Wyrm fly through `Move.FLY` steering like the old serpent; their swims are visual.

## SPEC-worthy decisions

1. Named beasts are modelled near true size (entity scale 1.0 to 1.8) on their own rigs; collision boxes unchanged.
2. A beast's death takes 36 ticks (the fall), not vanilla's 20; loot and counters are unchanged (they happen on death).
3. Each beast's signature move opens with its roar; phase changes and provocation roar too.
4. Overworld creatures speak with their own sounds; ambient intervals 900 / 600 / 420 / 360 ticks (ambient, neutral,
   hunter, beast), volumes 0.45 / 0.8 / 0.9 / 1.5.
5. Owls may turn their heads 160 degrees from their bodies.
