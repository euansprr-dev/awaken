# Veil module (`dev.awaken.veil`)

Out of the body and into the quiet: meditation, spirit projection with a Body left behind, the cord, Awareness, spirit
sight, the dead who talk, possession, Cord-Eaters, Veil Gates and the Stillpoint. SPEC section 9 is the contract; this
page records how it was built and the decisions made along the way. Feature flag: `AwakenConfig.VEIL` (restoring a
player to their body always runs, even with the flag off).

## Try it

| What | How |
|---|---|
| Meditate | Sneak and hold **G** (Focus), standing still, not in combat. A ring of dots fills around the crosshair. |
| Step out | Keep holding past 10 s (the ring turns full blue and chimes), then **let go of G**. |
| The Stillpoint | Keep holding G to 20 s (the ring fills amber). Or right-click a **Stillpoint Shrine**. |
| Just sit | While meditating press **N**: no transition at all, meditate as long as you like. Standing up just stops. |
| Leave the Stillpoint | Meditate there 10 s and let go of G (you return to where you came from). |
| As a spirit | **Y** back to your body · **G** (looking at one of the dead) hear the next line · **B** wear a creature (again to let go, or Shift) · **right click** touch something blue · **attack** while wearing a creature to act (a creeper lights up). |
| Commands | `/awaken veil project [player]` (admin, no meditation) · `/awaken veil return [player]` · `/awaken veil stillpoint [player]` (admin, enter or leave) · `/awaken veil spirits` (admin, the dead near you) · `/awaken veil status` (your cord, time, Awareness, secret sense). |
| Blocks (creative tab) | Stillpoint Shrine (craftable: lantern / amethyst, calcite, amethyst / smooth quartz x3), Ghost Lever, Ghost Door, Veil Cache, Veil Glyph, Veil Stone, Veil Mark, Door Out, Door In, Cloud. |

## The numbers (`Cord`)

```
cord radius   = 32 + 6 x SPI + Perks.CORD_LENGTH          (cap 1024)     SPI 5 -> 62 blocks
Veil time     = 30 s + 3 s x SPI                                          SPI 5 -> 45 s
Awareness     = 16 + 2 x PER + SPI + Perks.SECRET_GLOW_RANGE (16..160)    PER 5, SPI 5 -> 31 blocks
secret sense  = 4 + PER / 4 + Perks.SECRET_GLOW_RANGE        (awake, cap 32)
cord warnings at 85% (amber) and 95% (red); past 100% the spirit is pulled back just inside the edge
```
Awareness is the spirit's render distance: grey-blue fog starts at 35% of it and closes completely at the radius.

## How it works

**Meditation** (`Meditation`). The client only reports "Focus held with sneak" (and "just sit"); the server checks sneak,
on-ground, stillness (< 0.02 blocks per tick) and combat (hurt in the last 5 s, or a hostile within 10 blocks targeting
you) every tick, and resets the progress if any fails. After 1 s the screen ripples (post effect `awaken:veil_ripple`) and
a low hum plays. While meditating: `awaken:meditate_ticks` counts, `awaken:meditate_high_ticks` counts above Y 150
(not in the Stillpoint), Fracture heals three times as fast, one health point returns every 3 s, and
`Veil.energyRegenMultiplier` doubles. The dawn latent is set when meditating between ticks 22800 and 1200 of the
dimension's day, above the dimension's cloud height, under open sky, in a dimension with a day cycle. The first
meditation shows the Signal line `first_meditation` from `signal_line/lines.json`. Unawakened players can meditate but
never travel. Releasing Focus completes; standing up or opening a screen only ends.

**Projection** (`Projection`). The real player becomes the spirit: spectator mode under our rules. A vanilla Mannequin
with the player's resolved profile (skin), main arm, a copy of their armour and held items (looks only), crouching,
immovable, knockback-immune, with the player's max health and current health, stays behind as the Body, tagged with the
attachment `awaken:veil_body`. A ticket `awaken:veil_body` (loading + simulation + keep dimension active, 60 tick timeout,
refreshed every second) keeps its chunk and dimension running. The saved attachment `awaken:veil_projection` holds the
previous game mode, the Body's dimension/position/yaw/UUID and health, so:
- **return** (Y, time out, command): teleport to where the Body is now, restore the old game mode, take the Body's health,
  remove the spirit post effect and the Body.
- **logout / server stop**: same, before the save (PlayerLoggedOutEvent fires before `PlayerList.save`). If the spirit is in
  another dimension than the Body (Out door, Veil Gate) we never teleport mid-save: the saved state finishes it on login.
- **crash**: the next login restores from the saved state alone; the stale Body is removed when its chunk loads (an
  orphaned Body never joins the world).
- **death while out** (e.g. `/kill`): game mode restored, Body removed.
Spectator abuse is blocked server-side: the camera is reset if it is anything but the player or the worn creature (no
spectating players), any jump of more than 24 blocks in a tick is undone (the spectator teleport menu), dimension changes
are cancelled unless the Veil itself is moving the player, block/item/entity interactions are cancelled (no peeking into
chests, no portal clicks), and the game mode cannot be changed from outside while out. The client also swallows the
spectator attack, pick and hotbar keys. `awaken:veil_ticks` counts every tick out (batched per second).

**The Body.** Hostile mobs within 16 blocks that have no target and can see the Body are pointed at it every second (a
Mannequin is a LivingEntity, not a Player). When it takes damage its owner gets a WARNING window ("Your body is under
attack." / "A zombie found you meditating. Two hearts lost.") with the action `veil:return` (Y); at most one every 3 s.
If it dies, the spirit is pulled home and then dies with the Body's damage source as a normal death (deferred a few ticks
if the player is mid-teleport and cannot be hurt yet). A Body never drops anything.

**The one rule.** "You pass through everything; you can only touch what glows blue." Touching goes through our own
message (`VeilInteract`): the server checks the player is a spirit, not wearing a creature, within 5.5 blocks of the
block's centre, and that the block is in `#awaken:veil_interactable` and implements `SpiritUsable`. The other half: blue
things are real to a spirit. `#awaken:veil_solid` blocks (Veil Stone, Veil Glyphs) and closed Ghost Doors stop a spirit;
the server checks the swept path each tick and puts the spirit back. Ghost blocks have no collision for the living, and a
living hand gets "Your hand goes straight through."
- Ghost Lever: a real lever (powers redstone) only a spirit can pull.
- Ghost Door: a pane of spirit matter; touching toggles the whole connected pane (up to 48 blocks); follows redstone;
  `locked=true` panes only open by their puzzle.
- Veil Cache: pays a Veil Gate once per player.

**Spirit sight** (`SpiritSight`). Server scan of the sections inside Awareness, nearest first, 8 sections per tick, with
an identity cache of block classifications. Sent once a second, only positions within Awareness and never within 48
blocks of another player's Hearth or bed (`RevealFilter`: `HearthHooks.nearest` probed over the sight area, plus every
other online player's respawn point and `HearthHooks.hearthOf`). Shown on the client as gizmo outlines through walls:
- ores (`c:ores` by kind, faint, nearest 384);
- secrets in amber: unopened loot containers that are hidden (3+ blocks under the surface: dungeons, temples, ruins,
  wrecks), spawners and trial spawners, suspicious sand/gravel, and anything in `#awaken:veil_secrets` (the invisible
  Veil Mark is ours). Coming within 3.5 blocks of one counts `awaken:secrets_found` once per position per player;
- mobs outlined in their rank colour (`MobRole` content level; plain hostiles are E; creatures you can wear glow signal
  blue, others grey; Cord-Eaters crimson; your Body amber; other Bodies pale);
- gates from `GateHooks.near` with a rank-coloured frame and label; drifting within 6 blocks of a gate's mouth calls
  `GateHooks.markScouted`, counts `awaken:gates_scouted` (once per gate) and shows the full gate card (rank, breaks-in,
  layout, modifiers, twist, floors, boss, omen);
- Veil Gate rifts (violet rings), blue blocks within 6 blocks, and the cord itself, sagging from the Body to you and
  turning amber then red as it stretches.
Awake, an awakened player keeps a small secret sense (secrets only, SPEC section 3 radius) and also finds secrets.

**The dead** (`Spirits`, `SpiritPlacer`). The overworld is cut into 96-block cells; half are haunted (from the world
seed). A haunted cell has one fixed spot; the spirit standing there is chosen by seed among those whose `biome_hint`
matches the biome at the spot (exact id, else the longest hint the biome name contains, so a birch forest gets the forest
spirit). Underground hints (deep dark, caves) are sampled at Y 0 and -40. Around a projecting player at most 3 spirits
stand (nearest per spirit within 80 blocks); the ghosts are real entities (`awaken:veil_spirit`, never saved) that are
only ever sent to players who are out of their bodies. Look at one and press G: its next line as a Signal window. Each
conversation counts `awaken:spirits_spoken` (repeats too, at most once a minute per spirit: the emergent Truth listens for
repeats). Hearing a whole story for the first time sets `awaken:latent/spirit_named` and adds Codex cards (synced to the
client and fed to `CodexEntries.addSource`): the spirit (Spirits), and what it gives: `rumour:` cards start as "?"
(Rumours), `hint:` and `lore:` are found (Hints, Lore), linked by a thread to the spirit.

**Possession** (`Possession`). C-rank: any creature that is not hostile (not NPCs, not bosses). A-rank: any mob that is
not a boss, warden-role, Echo, NPC or in `#awaken:veil_unpossessable`. Look at it within 8 blocks, press B. The server
sets the player's camera to it; the creature's own tick is replaced by ours while worn (its AI cannot fight you): the
client sends movement intent and look every tick; walkers walk and jump (auto-step up one block), flyers and swimmers
move along the look. Attack: bite/kick the living thing in front (never players), or light a creeper (then it is left to
vanilla: it swells and goes off; "Worth it."). The cord still applies (stretching too far lets go) and the Body is still
vulnerable.

**Cord-Eaters** (`CordEaters`). Out of the Stillpoint (and not in a Veil Gate) for about 25 s, the first comes, then one
every ~15 s, at most 3, from `MobHooks.spawnThemed(level, "awaken:veil", NORMAL, ...)` (vanilla fallback when the mobs
module has no such theme). Their tick is replaced by a hunt: they drift through walls straight at the spirit, slowly
speeding up. If one reaches you, the cord is cut: back into the body with **Spirit Shock** (30 s: -15% speed and damage,
the Veil stays shut). They vanish when you return; strays saved by a crash are removed on load.

**Veil Gates** (`VeilGates`). One in three 64-block cells (by world seed) that hold a hidden loot chest opens a rift two
blocks above it, visible only in the Veil. Drifting into it moves the spirit into a pocket built of Veil Stone in the
Stillpoint (x, z >= 8192, y 160; 35 x 9 x 13; built once on first entry, reset when nobody is inside):
1. a lights-out lever puzzle: 4 or 5 Ghost Levers under a row of glyphs that show the target; some levers also flip
   their right-hand neighbour (always at least one), every pattern is solvable (tested). Matching opens the first door;
2. a held breath: a lever that keeps the second door open for 5 seconds, ten blocks away;
3. the Veil Cache: +1 SPI, `awaken:secrets_found` +1, 0.15 of a level, once per player per rift; then back out through
   the rift. Time keeps running inside; Y or the end of your Focus takes you home as usual.

**The Stillpoint** (`Stillpoint`, `world/StillpointGenerator`, `world/TempleBuilder`). Dimension `awaken:stillpoint`
(type, level stem and biome are data: fixed time, sun just above the eastern horizon, warm horizon, pale sky, soft
dawn light, sky light 13, cherry grove music, no weather via `has_ceiling`, no spawns, no sleeping). Generator: an
endless sea of soft cloud blocks (Y 72..96, walkable, no fall damage) with gaps down into the void, the vanilla clouds
drifting at Y 108, and the temple floating at the origin: a pale quartz disc with a dark slate edge on a rock that tapers
into the air, eight quartz pillars under a floating ring of dark slate with amber lanterns, a low round seat in the centre,
two small white chedis with gold crowns and lit spires, cherry planters, the **Door Out** under a cherry gate facing the
sunrise (east), the **Door In** in a dark arch (west), and a stair to the north that ends above the clouds.
Arrive at the seat facing the sun; your return point is remembered. Rules for you here: no hunger, energy x1.5, falling
below Y 40 hands you back to the seat, survival hands cannot change the temple or the pockets.
- Door Out (walk into it or touch it): you sit at the seat, your spirit steps out at your return point in the Waking
  world; the cord is measured from there and your Body waits safe in the temple.
- Door In: dark unless `DreamHooks.isDreaming` (then it glows for you and calls `DreamHooks.replay(player, 0)`).

## Public API (stable, `dev.awaken.veil.Veil`)

`isSpirit(Player)` (both sides), `isMeditating(Player)`, `inStillpoint(Entity)`, `energyRegenMultiplier(Player)`,
`project(ServerPlayer)`, `returnToBody(ServerPlayer)`, `enterStillpoint(ServerPlayer)`, `leaveStillpoint(ServerPlayer)`,
`STILLPOINT` (level key). Tags other modules may extend through their merge folders: `#awaken:veil_interactable`,
`#awaken:veil_solid`, `#awaken:veil_secrets` (blocks), `#awaken:veil_unpossessable` (entity types). Space in the
Stillpoint for other builders (trial arenas): anywhere with x <= -8192 is free (the temple is the origin, Veil Gate pockets fill x 8192..10240, z >= 8192).

## Counters owned

`awaken:veil_ticks`, `awaken:meditate_ticks`, `awaken:meditate_high_ticks`, `awaken:spirits_spoken`,
`awaken:gates_scouted`, `awaken:secrets_found`, `awaken:latent/dawn_meditation_above_clouds`,
`awaken:latent/spirit_named`, plus `awaken:stillpoint_visits` (new, unused by content yet).

## Tests (`VeilTests`, all passing)

`veil_cord_maths`, `veil_restore_after_logout` (logout path and crash path), `veil_body_damage_warns` (warning, then the
Body dies and so does the player), `veil_interaction_rule` (living rejected, stone rejected, too far rejected, ghost lever
accepted), `veil_spirit_placement` (determinism, cells, biome matching), `veil_no_reveal_near_hearth` (explicit zones,
another player's bed hidden, your own not), `veil_ghost_door_and_solidity`, `veil_gate_puzzles_solvable` (200 seeds),
`veil_temple_layout`, `veil_stillpoint_dimension_decodes`.

## Known gaps

- **Energy**: the powers module owns energy and was built in parallel; it needs to multiply its regen by
  `Veil.energyRegenMultiplier(player)` (meditation x2, Stillpoint x1.5). Until then meditation's energy bonus is inert.
- **The Stillpoint level cannot be created in GameTests** (the GameTest server only makes vanilla dimensions), so the
  generator is covered by decoding the real dimension JSON and checking the temple layout; the world itself, the shaders,
  sounds, HUD and renderers have not been seen in a running client (the shaders were validated with glslangValidator).
- Spirits only stand in the overworld. Possessed creatures skip their own tick (no fire, drowning or pushing while worn).
- "Time passes slowly" is expressed as no hunger and faster energy, not as slower timers.
- The first near-death glimpse needs `GateHooks.inGate` (gates module) to ever fire.
- Cord-Eaters look like whatever the mobs module spawns for the `awaken:veil` theme (vanilla zombies/skeletons/spiders
  named "Cord-Eater" without it).
