# Powers (`dev.awaken.powers`)

The power framework and every launch power: Power Cores, energy and cooldowns, key casting with client prediction,
the Five Forces, combat feel (hit-stop, camera shake), the power bar HUD and the POWERS section of the Status screen.
SPEC section 6 is the contract; this file is the authoritative catalog with numbers.

## Try it in game

| Command | What it does |
|---|---|
| `/awaken admin power all` | Learn every power (variant 0) and fill energy. Passives switch on at once. |
| `/awaken admin power give <id> [variant]` | Give a Power Core item (`awaken:power_core` with `{power, variant}`). |
| `/awaken admin power learn <id> [variant]` | Learn directly (a known power with a new variant swaps to it). |
| `/awaken admin power cast <id>` | Cast a power by id with your current aim (learns it first if needed). |
| `/awaken admin power reset` | Clear cooldowns and fill energy. `/awaken admin power forget <id>` removes one. |
| `/awaken admin energy full` | Fill energy. |
| `/awaken powers list` | Your energy, the six keys and everything you own (variant, level, key). |
| `/awaken powers equip <1-6> <id>` | Put a power on a key from chat (1-4 = Z X C V, 5 = R, 6 = Left Alt). |

Keys: Z X C V (actives), R (ultimate), Left Alt (movement), all rebindable in Controls → AWAKEN. Channelled powers
(Beam, Haymaker, Charged Shot, Telekinesis, Railgun, Prism Lance) are held; toggles (Hover, Flight) are pressed again
to stop. The Status screen (K) has a POWERS section: click a power, then a key. Click a key first to pick up what is
on it; click the same key again to clear it. Power Cores are in the AWAKEN creative tab (one per power).

## How it works

- **Registry.** `PowerRegistry` is a static, ordered registry of `PowerType`s (id `awaken:<name>`), filled once from
  `catalog/PowerCatalog`. SPEC asks for a custom registry `awaken:power_type`; powers are code with fixed ids saved by
  string, so a static registry gives the same guarantees without a synced NeoForge registry. `PowerType` holds id,
  name, family, forces, slot (ACTIVE / ULTIMATE / MOVEMENT / PASSIVE), mode (INSTANT / CHANNEL / TOGGLE), energy cost and
  drain, cooldown, base damage, scaling stat, description, unlock rank, hidden flag, variants and behaviour.
- **Loadout** (`PowerContent.LOADOUT`, attachment `awaken:loadout`, saved, kept on death, synced to its owner): owned
  powers with variant and level (1-5), the six keys, cooldowns (end game-time + length), energy. The network form also
  carries max energy, regen per tick, the regen pause and the running powers so the HUD extrapolates energy between
  syncs (sent on change and once a second while regenerating).
- **Energy.** Pool 100 + 5 x INT (125 at INT 5). Regen 4/s + 0.08 x INT/s, x (1 + `awaken:energy_regen` perk), paused
  for 1 s after any spend (including channel drain). `PowerMath` holds the maths (tested).
- **Cooldowns.** Base x variant x (1 - 4% per power level above 1) x (1 - `awaken:cooldown_reduction`, capped at 50%).
  Channels and toggles start their cooldown when they end.
- **Damage scaling.** base x (1 + 0.075 x player level) x (1 + 1.5% per point of the family stat above 5) x
  (1 + 12% per power level above 1) x variant. The level factor matches mob health growth per content level (SPEC
  section 2: health x (1 + 0.075 lv)), so a power keeps the same hits-to-kill against on-level mobs at every rank.
  Example: Ground Slam at level 35 with balanced stats deals ~22 to a C-rank zombie with 72 HP (about 3 slams); a
  C-rank boss (~1000 HP) takes ~100 from Overload. No single power hit takes more than 20% of a boss's max health.
- **Casting.** A key press sends `awaken:power_cast {slot, phase, predicted, yaw, pitch}`. The server checks owned,
  key, cooldown, energy and state, runs the behaviour, spends energy, starts the cooldown and counts the use. Refusals
  come back as `awaken:power_feedback` (red slot flash and a short reason above the bar). Movement powers with a
  predictor (Dash, Leap, Jet Burst, Grapple, Ember Step) apply their motion on the client at once; the server repeats
  the maths for players who didn't predict, and if it refuses it stops the motion (velocity packet + feedback).
  Blinks (Lightning Step, Shadow Step, Ghost Step, Dash: Blink) are server teleports. Wall Run and Water Run are client
  physics for owners of those passives.
- **Running powers.** Channels, toggles and timed effects are `Running` entries ticked every server tick. Their end
  callback always runs once (released, expired, out of energy, death, logout, world change, server stop), which is
  where attribute modifiers, flight and post effects are removed. Exceptions in a behaviour are caught and logged.
- **Post effects** (`awaken:flow`, `foresight`, `overload`, `third_eye`, `cloak`, `void`; one shader
  `awaken:post/power_tint`) are added with `ServerPlayer.addPostEffect` and removed when their power ends. Because they
  persist in player NBT, every one of ours is also stripped on login, respawn, death and dimension change.
- **Constructs** (`awaken:construct`, `awaken:dream_construct`): translucent hard light, four ages on scheduled ticks,
  gone after 30 s (scheduled ticks persist with the chunk, so they decay across restarts too). Placed only into air,
  never inside a living thing, never inside another player's Hearth (via `HearthHooks`). No drops.
- **Temporary terrain** (`TerrainLedger`, per-dimension SavedData `awaken:powers_terrain`): Terrain Rip craters and
  Waking Dream doors lift full, block-entity-free blocks (hardness 0-5) out silently and put them back after 12-15 s.
  Entries are saved, restored on server stop for loaded chunks and on load otherwise; a restore never overwrites
  something a player placed in the hole. Thrown chunks are vanilla block displays moved by the server (`FlyingBlocks`);
  they place nothing when they land and are tagged `awaken_temp` so they're discarded if ever loaded from disk.
- **Time slow** (Flow 25%, Foresight 10%): entities within 18-20 blocks of the caster only tick every Nth tick
  (`EntityTickEvent.Pre` cancel), so mobs, their swings and their arrows all slow down while the caster doesn't. Damage
  cooldowns keep counting in real time so the caster's hits still land. The caster's own projectiles and summons are
  exempt.
- **Summons**: shadow soldiers (wither-skeleton model tinted violet) and drones/turrets (vex model tinted cyan/amber).
  They belong to a player, are never saved, never hurt their side, credit kills to their owner and fade on time.

## Five Forces (`ForceInteractions`)

Forces react the same way wherever they meet, whoever caused them: every damage carrying a force is checked against
what the target is. Each reaction a player causes counts `awaken:force_interactions` and shows a toast the first time
in a while.

| When | meets | you get | Implementation |
|---|---|---|---|
| Heat | Spores | chain explosion | spore-tagged things burst in turn (4 ticks apart), each blast 3.5 blocks, spreading to spores within 6 |
| Charge | Water | everything in the water is stunned, you too | every living thing in water (or tagged WATER) within 8 is stunned 2.5 s and shocked; the caster only stunned |
| Gravity | Volatile | they detonate each other | a volatile mob (tag VOLATILE or a creeper) hit by gravity with another within 3, or two pulled within 2.5 of each other in a Gravity Well, both go off (no block damage) |
| Spirit | Mirror | your spirit walks through mirrors | spirit damage shatters MIRROR-tagged things (bonus damage, jumps to other mirrors); Ghost Step passes through any thickness of `#awaken:mirror` blocks |
| Heat | Frost | steam | heat on a frosted/freezing target, frost on a burning one, or a Beam on ice/snow: a 4-block steam cloud for 8 s that blinds mobs and hides anyone sneaking in it |
| Charge | your Echoes | overcharged | charge on your own summon or an Echo (`MobRole.ECHO`) doesn't hurt it: it hits twice as hard for 10 s, then expires |
| Gravity | Architect walls | battering ram | Telekinesis on one of your constructs throws the whole connected structure; a Gravity Well tears your constructs loose into itself |

Frost Nova tags mobs FROSTED for 8 s (and freezes them), so Heat right after it makes steam.

## Counters (docs/COUNTERS.md)

| Counter | Counted when |
|---|---|
| `awaken:power_uses/<family>` | every successful cast, by family (kinetic, velocity, flight, energy, shadow, mind, architect, tech, spirit) |
| `awaken:flow_ticks` | each tick spent in Flow (batched) |
| `awaken:dashes` | each Dash, Lightning Step or Ember Step |
| `awaken:dash_chain_airborne_best` | record: dashes in one airborne chain (a chain starts with a dash and ends on landing) |
| `awaken:dash_chain_airborne_best_session` | the current airborne chain, reset to 0 on landing and on login. It is the live chain rather than a best-of-session so Directive baselines (taken when accepted, normally 0) measure "N dashes without landing" correctly |
| `awaken:blocks_placed_in_combat` | construct blocks placed and normal blocks placed while in combat (hit or hurt in the last 5 s, or a mob hunting you within 8) |
| `awaken:force_interactions` | each Five Forces reaction a player causes |
| `awaken:kills_airborne` | a kill while the killer isn't on the ground (flying counts) |

`Progression.markNewThing(player, "power <name>")` fires on every newly learned power.

## Integration

- `Powers.learn(ServerPlayer, Identifier, variant)`, `Powers.energy(Player)`, `Powers.forcesOf(DamageSource)` (SPEC 12).
- `RewardHooks.powerLearner` is installed: Truth, Latent and Path rewards learn powers (or level them up).
- `PowerCoreHooks` is installed: `roll(rank, random)` picks a non-hidden power unlocked at or below the rank (powers of
  the player's own rank weighted x3) with a random variant (Standard half the time); `forPower(id, variant)` gives a
  specific core (boss signatures). Hidden powers (path signatures, latents) never roll.
- Using a core: learn it; a known power levels up (max 5); sneak-use takes the core's variant instead (keeps the level);
  at max level a different variant swaps. The core is kept if nothing happened.
- Block tag `#awaken:mirror` (merged; starts with tinted glass): gates should add their mirror blocks.
- Entities tagged with `Forces.Tag` (SPORES, WATER, VOLATILE, MIRROR, FROSTED) react as above.

## Decisions

- Static registry instead of a NeoForge custom registry (see above).
- Power damage types are the core `awaken:<force>` types, KINETIC for forceless powers; they don't bypass armour.
- Powers never hurt players, pets, armor stands, mannequins (decoys, the Veil body), Echoes or summons; aimed powers can
  hit any other living thing, area powers only hostiles and anything hunting you or your side.
- Flight costs more in the Deep (x2, `awaken:stratum_*` / `awaken:endless`) and in gates (x1.5) to keep exploration
  meaningful (plan: "Keeping flight fun").
- Hover and Flight use the NeoForge `CREATIVE_FLIGHT` attribute (transient modifier), so nothing persists.
- Hit-stop freezes the struck entity's render state (position, animation, age) for 40-80 ms with a tiny tremble; the
  camera kick obeys `AwakenConfig.Client.CAMERA_SHAKE`.

## Tests (`tools/gradle.sh 1200 runGameTestServer`)

energy and cooldown maths, catalog integrity (≥50 powers, every behaviour present, signatures/latents hidden and never
rolled, E-rank cores roll E powers only), loadout codec round trip, learning and levelling via a core and the reward
hook, the core roller, Ground Slam and Spirit Palm on a fake player (damage, cooldown, energy, counter), the energy
gate, construct walls (never overwrite, decay), terrain restore, the Five Forces table, the spore chain, charge in
water, Flow (time-slow zone and post effect cleaned up), and an isolated batch that casts every power once and checks
nothing throws or lingers.

## Known gaps

- Client visuals (HUD layout, beams, outlines, hit-stop, post-effect shader, summon renderers, icons in game) compile
  and follow the API sheets but were not seen in a running client (the owner's rule: never launch the client).
- Power levels come only from duplicate cores; there is no other levelling path yet.
- Danger Sense marks are server-decided every 5 ticks (a quarter second), not frame-perfect.
- Afterimage/Decoy use vanilla mannequins; mobs that target by brain (piglins, wardens) may ignore them.

## Catalog

Base numbers before scaling. Cooldowns in seconds. "hold" = channel (drain per second while held), "toggle" = press
again to stop. Every power has variant 0 "Standard"; the others roll from cores.

<!-- catalog:start -->
63 powers.

### Kinetic (scales with STR)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Ground Slam** `ground_slam` | active | E | 22 | 6 s | 6 | - | Slam the ground: a shockwave hurts and throws everything around you. In the air you crash down first, harder the further you fall. | **Quake**: 50% wider, a little weaker; **Shatter**: Tighter and harder; stuns for a second |
| **Leap** `leap` | movement | E | 15 | 4 s | 3 | - | A huge bound forward. You land without fall damage and the landing knocks back what's around you. | **Bound**: Further and flatter; **Pounce**: Higher; the landing hits far harder |
| **Haymaker** `haymaker` | active | E | 18 (hold) | 5 s | 7 | - | Hold to wind up, release to punch. A full charge launches the target across the room. | **Uppercut**: Launches straight up into the sky; **Piledriver**: Drives the target into the ground and stuns it |
| **Terrain Rip** `terrain_rip` | active | C | 30 | 10 s | 14 | - | Tear a chunk of ground out and throw it. The crater fills back in after a few seconds. | **Boulder**: One huge rock: harder, wider splash; **Scatter**: Five smaller rocks in a spread |
| **Shockwave** `shockwave` | active | D | 20 | 7 s | 5 | - | Stamp a rolling wave of force forward that bowls over everything in its path. | **Wide**: A broad, shorter wave; **Lance**: A narrow, long, harder wave |
| **Brace** `brace` | active | D | 15 | 12 s | 3 | - | Plant your feet for 3 s: far less damage, no knockback, and melee attackers take a share back. | **Iron**: Takes even less damage but reflects nothing; **Thorns**: Takes more damage but reflects all of it |
| **Thunderclap** `thunderclap` | active | C · hidden | 40 | 14 s | 12 | charge | Stormbreaker's signature. Clap your hands and the sky answers: lightning and a stunning shock all around you. | **Rolling**: Three rings of thunder rolling outward; **Focused**: One bolt on your target that forks to three more |

### Velocity (scales with AGI)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Dash** `dash` | movement | E | 10 | 0.7 s | 2 | - | Burst forward. Works in the air, so dashes chain: ten without touching the ground is a feat. | **Blink**: Teleport a short way instead of moving; **Double**: Shorter, cheaper, and back almost at once |
| **Flow** `flow` | active | E | 35 | 20 s | - | - | The world slows to a quarter speed around you while you don't. Lasts 4 s plus 0.05 s per AGI. | **Deep**: The world slows to a tenth, but it's shorter; **Long**: Half again as long, the world at a third |
| **Afterimage** `afterimage` | active | D | 20 | 12 s | 6 | - | Leave a copy of yourself behind. Enemies chase it; it bursts when it fades. You get a burst of speed. | **Mirror Rush**: Three afterimages, smaller bursts; **Shade**: Lasts twice as long but doesn't burst |
| **Wall Run** `wall_run` | passive | D | - | - | - | - | Sprint along a wall and you run on it for a couple of seconds instead of falling. | - |
| **Water Run** `water_run` | passive | D | - | - | - | - | Sprint and you run across water instead of sinking. | - |
| **Lightning Step** `lightning_step` | movement | C · hidden | 22 | 3 s | 6 | charge | Redline's signature. Become lightning for an instant: flash up to 14 blocks, shocking everything you pass through. | **Arc**: The shock forks to three more foes where you land; **Long**: Up to 20 blocks |

### Flight & Gravity (scales with INT)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Hover** `hover` | active | E | 15 (toggle) | 10 s | - | - | Hang in the air for 5 s and drift slowly. Press again to drop. No fall damage after. | **Long**: Hover for 8 s; **Glide**: Shorter, but you glide down slowly afterwards |
| **Flight** `flight` | movement | A | 10 + 6/s (toggle) | 2 s | - | - | Real flight. Drains energy while you fly (twice as fast in the Deep, half again in gates). Press again to land. | **Swift**: 40% faster, drains 40% faster; **Efficient**: Slower, drains 40% less |
| **Gravity Well** `gravity_well` | active | D | 28 | 14 s | 2 | gravity | Open a well of gravity where you aim. It drags enemies (and your own walls) into a crushing knot for 4 s. | **Singularity**: Smaller, pulls twice as hard, implodes at the end; **Repulsor**: Throws everything outward instead |
| **Telekinesis** `telekinesis` | active | C | 20 + 6/s (hold) | 8 s | 9 | gravity | Hold to lift a mob in front of you, release to throw it. Aim at your own wall to throw the whole wall. | **Crush**: Squeeze while you hold; release slams it down; **Fling**: Throws half again as hard, holds half as long |
| **Meteor Dive** `meteor_dive` | active | C | 30 | 12 s | 10 | gravity | Launch up, then come down like a meteor. The impact grows with the height you fell. | **Comet**: Burning: sets everything it hits on fire; **Crater**: A wider crater that stuns |
| **Gravity Flip** `gravity_flip` | active | B · hidden | 30 | 15 s | 8 | gravity | A latent gift from the Inverted Sea. Everything near you falls up, then comes crashing down. You feel light for a moment. | - |

### Energy (scales with INT)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Beam** `beam` | active | E | 6 + 18/s (hold) | 3 s | 2.2 | heat | Hold to pour a searing beam from your hand. Burns what it touches; ice and snow turn to steam. | **Prism**: The beam splits into three; **Focused**: Narrow and long: half again the damage, drains faster |
| **Nova** `nova` | active | D | 30 | 10 s | 9 | heat | Release a sphere of burning light all around you that throws enemies back. | **Pulse**: Two smaller pulses; **Implosion**: Pulls everything in, then detonates |
| **Barrier** `barrier` | active | E | 25 | 18 s | 4 | charge | Wrap yourself in an energy shield (6 + INT/4 absorption) for 10 s. When it breaks it shocks everything near you. | **Reflect**: A thinner shield that throws back a third of melee damage; **Overshield**: Half again as strong, but shorter |
| **Charged Shot** `charged_shot` | active | C | 15 (hold) | 4 s | 6 | charge | Hold to charge a bolt of lightning, release to fire. Fully charged it explodes and stuns. | **Ricochet**: Bounces off walls twice; **Volley**: No charge: three quick bolts at once |
| **Overload** `overload` | ultimate | C | 60 | 60 s | 28 | heat+charge | Gather everything you have for a second, then explode. Afterwards your energy powers hit harder and cost half for 8 s. | **Supernova**: A bigger blast, no afterglow; **Conduit**: No blast: energy regenerates three times as fast for 15 s |
| **Prism Lance** `prism_lance` | ultimate | C · hidden | 40 + 10/s (hold) | 45 s | 5 | heat | Prism's signature. A lance of light that splits at the first thing it hits into four beams that seek out more enemies. | **Spectrum**: Splits into six weaker beams; **Lancer**: Never splits: pierces everything in a line |

### Shadow (scales with SPI)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Shadow Step** `shadow_step` | active | E | 15 | 6 s | - | - | Step through the shadows to just behind your target. Your next hit within 3 s lands as a backstab (x1.6). | **Assassin**: Backstabs hit x2.2, a little longer to recover; **Phantom**: You stay unseen for 2 s after the step |
| **Cloak** `cloak` | active | D | 25 | 20 s | 5 | - | Vanish for 6 s. Enemies lose you and can't find you unless you strike; the first strike from the dark hits x1.8. | **Long**: Vanish for 10 s; **Ambush**: Striking from the cloak bursts shadow around your target |
| **Echo Swap** `echo_swap` | active | C | 18 | 8 s | 5 | spirit | Trade places with one of your summons, decoys or Echoes. Enemies around where you appear are struck and blinded. | **Shroud**: Both places fill with blinding smoke; **Relay**: No strike, but back twice as fast |
| **Legion** `legion` | ultimate | C · hidden | 60 | 60 s | 3.5 | spirit | Shadowbinder's signature. Raise shadow soldiers from your own shadow (4, plus 1 per 15 SPI) to fight beside you for 20 s. | **Knights**: Two great knights, far tougher; **Horde**: Eight frail shades |

### Mind (scales with PER)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Danger Sense** `danger_sense` | passive | D | - | - | - | - | Anything about to hit you glows red a moment before it does: a raised claw, a drawn bow, a swelling creeper, an arrow in flight. | - |
| **Dominate** `dominate` | active | C | 35 | 30 s | - | - | Bend a mob's will for 8 s (more with PER): it turns on its own kind. Elites resist for half as long; bosses can't be bent. | **Mass**: Up to three weaker minds at once, for less time; **Frenzy**: Your puppet hits half again as hard |
| **Read Intent** `read_intent` | active | D | 20 | 20 s | - | - | For 10 s you see who is hunting you (red) and who isn't (amber). Read enemies take 15% more from you and deal 15% less. | **Long**: Lasts 16 s; **Wide**: Reads twice as far |
| **Decoy** `decoy` | active | E | 18 | 12 s | 4 | spirit | Throw an image of yourself where you aim. Enemies attack it for 6 s, then it bursts and blinds them. | **Lure**: Lasts twice as long, draws from further away; **Trap**: A much bigger burst |
| **Foresight** `foresight` | active | C · hidden | 35 | 30 s | - | - | Seer's signature. For 3 s you see a heartbeat ahead: the world crawls at a tenth of its speed and the first blow that reaches you misses. | **Long**: Lasts 5 s |

### Architect (scales with INT)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Wall** `wall` | active | E | 12 | 2 s | - | - | A 5x3 wall of hard light two blocks ahead. Constructs fade after 30 s and never replace anything. | **Wide**: 7 blocks wide; **Tall**: 3 wide, 5 high |
| **Ramp** `ramp` | active | E | 12 | 2 s | - | - | A walkable ramp rising ahead of you, 3 wide and 4 high: run straight up it. | **Wide**: 5 wide; **Long**: Twice as long and twice as high |
| **Floor** `floor` | active | E | 12 | 2 s | - | - | A 5x5 platform: under your feet if you're falling, otherwise over the ground ahead. Bridges any gap. | **Wide**: 7x7; **Quick**: 3x3, ready again almost at once |
| **Cone** `cone` | active | D | 14 | 2 s | - | - | A stepped roof over your head: cover from arrows and anything falling on you. | **Wide**: 7 across; **Low**: Built right on top of you, tighter |
| **Rampart** `rampart` | active | C | 30 | 15 s | - | - | Raise a square fort around you, three high with battlements and a door facing the way you look. | **Keep**: Bigger and taller; **Bastion**: Tight and tall |
| **Spire** `spire` | active | C | 22 | 8 s | 6 | - | A pillar erupts under your feet and throws you skyward, and everything standing next to you with it. | **Tower**: A taller pillar, a bigger launch; **Spikes**: No launch for you: three spikes erupt under enemies around you |
| **Collapse** `collapse` | active | B | 25 | 10 s | 4 | - | Bring your constructs down on your enemies: every block of yours nearby flies at the closest foe. | **Avalanche**: Pulls in constructs from twice as far; **Crush**: Fewer blocks, each one harder and stunning |
| **Citadel** `citadel` | ultimate | C · hidden | 50 | 60 s | - | - | Architect's signature. A dome of hard light rises around you. Inside it you regenerate and take less damage. | **Bastion**: A wider dome; **Sanctum**: A tight dome with stronger healing |

### Tech (scales with INT)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Grapple** `grapple` | movement | E | 10 | 2.5 s | 3 | - | Fire a hook up to 28 blocks. Hook a wall and you're yanked to it; hook a mob and it's yanked to you. | **Zipline**: A faster, flatter pull; **Harpoon**: Hooked mobs take more and are stunned |
| **Jet Burst** `jet_burst` | movement | D | 14 | 2 s | 2 | heat | Kick on your boot jets for a burst in the direction you're looking. The exhaust scorches what's behind you. | **Afterburner**: Faster, slower to recover; **Vertical**: Straight up, high |
| **Drone** `drone` | active | D | 25 | 15 s | 2.5 | charge | Launch a combat drone that orbits you for 20 s and zaps what you're fighting. Up to two at once. | **Gunship**: Slower, heavier shots; **Twin**: Two lighter drones at once |
| **Turret** `turret` | active | C | 30 | 20 s | 3.5 | charge | Deploy a turret where you aim. It holds the spot for 30 s, shooting anything hostile within 20 blocks. | **Mortar**: Slow explosive shells; **Rapid**: Fast, light shots |
| **Railgun** `railgun` | active | B | 35 (hold) | 12 s | 18 | charge | Hold to charge, release to fire a hypersonic slug that pierces every enemy in a 48-block line. | **Coil**: Charges twice as fast, a little weaker; **Breach**: Heavier and staggering |
| **Nanite Skin** `nanite_skin` | active | C | 25 | 25 s | - | - | For 10 s a nanite film hardens over you: +8 armour, +4 toughness, slow healing, and melee hits shock the attacker. | **Hardened**: Twice the armour, no healing; **Mending**: Less armour, heals three times as fast |
| **Drone Swarm** `drone_swarm` | ultimate | C · hidden | 60 | 60 s | 2.5 | charge | Engineer's signature. Six drones pour out and focus fire on whatever you aim at for 20 s. | **Hive**: Ten lighter drones; **Strike**: Three heavy drones that fire fast |

### Spirit (scales with SPI)

| Power | Key | Rank | Energy | Cooldown | Base dmg | Forces | What it does | Variants |
|---|---|---|---|---|---|---|---|---|
| **Aura** `aura` | passive | E | - | - | 1.2 | spirit | Your spirit leaks out. Hostiles right next to you take a little spirit damage, and out of combat you slowly heal. | - |
| **Spirit Palm** `spirit_palm` | active | E | 16 | 5 s | 8 | spirit | A palm strike that sends your spirit through the target: it's thrown back, and a second later the echo hits again. | **Echo Palm**: A lighter strike with a doubled echo; **Push**: A wide push that throws everything in front of you |
| **Mend** `mend` | active | E | 25 | 20 s | - | - | Heal over 2 s (more the more you're missing) and wash off poison, wither and weakness. Players near you heal half as much. | **Surge**: All at once, just for you; **Circle**: Heals everyone near you fully as much |
| **Third Eye** `third_eye` | active | D | 15 | 20 s | - | - | Open your third eye for 8 s: every hostile within 32 blocks shows through walls, even invisible ones. | **Far**: Sees 64 blocks, for less time; **Long**: Stays open 14 s |
| **Cord Lash** `cord_lash` | active | D | 18 | 6 s | 6 | spirit | Lash a target up to 14 blocks away with your spirit cord and haul it to your feet, dazed. | **Anchor**: Haul yourself to the target instead; **Bind**: Don't pull: bind it in place for longer |
| **Bulwark** `bulwark` | active | C · hidden | 40 | 30 s | - | spirit | Warden's signature. A dome of will for 8 s: allies inside take half damage, arrows dissolve against it, enemies are pushed out. | **Aegis**: A wider dome; **Fortress**: Lasts 12 s |
| **Hundred Palms** `hundred_palms` | ultimate | C · hidden | 50 | 45 s | 1.6 | spirit | Monk's signature. Two seconds of palms too fast to see in front of you, ending in one that launches. Harder bare-handed. | **Storm**: A wide, lighter flurry; **Focus**: Every palm on the one target in front of you |
| **Void Maw** `void_maw` | ultimate | C · hidden | 55 | 60 s | 3 | gravity+spirit | Hollow's signature. Tear open a maw where you aim. It drags everything in, chews, and swallows the weak whole, feeding you. | **Abyss**: Wider and slower; **Hunger**: Swallows anything below a quarter of its health |
| **Waking Dream** `waking_dream` | active | C · hidden | 25 | 10 s | - | - | Dreamwalker's signature. Shape the world like a lucid dream: look at a wall for a door, at a gap for a bridge, up a ledge for stairs. | - |
| **Storm Call** `storm_call` | active | D · hidden | 45 | 30 s | 14 | charge | A latent gift from the storm. Call lightning down on up to five enemies around you. Stronger in the rain. | **Tempest**: Up to eight weaker strikes |
| **Ember Step** `ember_step` | movement | D · hidden | 14 | 1.5 s | 2 | heat | A latent gift from the lava. Dash forward leaving a trail of fire that burns whatever follows you. | - |
| **Frost Nova** `frost_nova` | active | D · hidden | 28 | 12 s | 7 | frost | A latent gift from the cold. A ring of frost freezes everything around you: slowed, frosted, and ready to burst into steam. | - |
| **Ghost Step** `ghost_step` | movement | D · hidden | 14 | 2.5 s | 3 | spirit | A latent gift from the Silent Halls. Slip 7 blocks forward through enemies and thin walls. Mirrors let you through any distance. | - |
| **Twin Strike** `twin_strike` | active | C · hidden | 25 | 20 s | - | spirit | A latent gift from the Double Gate. For 8 s your twin soul repeats every blow you land, half a second later. | - |
<!-- catalog:end -->
