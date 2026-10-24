# The Awakened Body (`dev.awaken.body`)

The awakening felt in the body, and fights that move. Approved by Euan on 2026-10-24 (proposal:
`docs/proposals/awakened-body.md`, widened the same day toward Call of Duty's movement). Numbers: SPEC §13 "Awakened Body".
Grounded moves only: anything airborne or long-range stays a power. A player who never awakened moves exactly like vanilla.

| Unlocks | Move | Input | What happens |
|---|---|---|---|
| Awakening | Soft landing | land sprinting or crouching (≥ 3 blocks) | a roll: safe fall +3, ×0.6 beyond |
| Awakening | Tactical sprint | double-tap sprint while running | +25% speed up to 3 s, hands lowered, view pushed wide; recharge 4 s |
| D | Slide | crouch while sprinting | 14 ticks low and fast (~4.6 blocks with forward held, ~5.5 out of a tactical sprint), crawl-height box; jump out to keep the speed |
| C | Mantle | jump at a ledge 1.1–1.6 above the feet | a 6-tick pull up and over |

In a fight: **momentum hits** (a full swing while sprinting or sliding: up to +30% damage and more knockback by speed),
**trips** (a slide knocks small hostile creatures off their feet), **slide-casting** (a cast mid-slide keeps the slide), and
in powers the **chain refund** and the **family twists** (Kinetic slide-punch, Velocity dash-out, Shadow smoke trail; see
docs/modules/power-fx.md "Move twists"). No invincibility frames: Dash stays the dodge.

## How it works

- **Client prediction, server authority** (SPEC §6, like the Velocity powers). Everything happens in NeoForge's
  `MovementInputUpdateEvent`, right after the tick's keys are read and before vanilla moves the body: a move sets the
  velocity for that tick (or edits the keys it lets through) and vanilla's own collision does the rest, so the walls, gaps
  and ledges are exactly the ones the server checks. One `body_start` per move; `body_end` when the client stops early;
  `body_refuse` stops the client (a refused mantle is also teleported back where it began, a refused slide stopped).
- **Server checks** (`BodyServer`): unlocked, not riding / sleeping / projecting / meditating / flying / gliding, cooldowns,
  sprinting, and the geometry from the server's own position: the ledge is found again (`BodyRules.findLedge`) and must
  match the client's within 1 block and 0.25 in height; the crawl box must fit. The slide forces `Pose.SWIMMING` on both
  sides (NeoForge `setForcedPose`), so the server's box fits the gaps the client slides under and other players see the pose.
- **One fall path**: `BodyServer.onFall` at LOW priority. A power's landing never reaches it (powers cancel the fall in their
  no-fall window); riders, elytra and spectators are skipped; another rule that is already kinder (Low Gravity, jet boots,
  a fall-resist perk) wins instead of stacking. The client's half of the same listener only plays the roll.
- **Tactical sprint** is a transient `MOVEMENT_SPEED` modifier (`awaken:body_tactical_sprint`), predicted on the client with
  the same id and owned by the server (it syncs). The attack key and `MovementHooks.castStarting` end it.
- **Powers** see the body only through `core/api/MovementHooks` (sliding, tactical sprinting, last move tick, slide
  direction; `castStarting` from `Casting.cast`, `clientCastStarting` from `ClientCasting.press`). The body never reaches
  into powers; powers never imports `dev.awaken.body`.
- **Momentum hits** use `AttackEntityEvent` (remember the swing's target), `LivingIncomingDamageEvent` (scale by the speed
  the client last reported, `ServerPlayer.getKnownMovement()`) and `LivingKnockBackEvent` (the extra push, once).
- **Per tick**: the server looks only at players with a move running (plain fields, no allocation); the trip check runs
  every other tick of a slide. The client keeps everything in static fields; the cached `Input` table avoids allocating
  when it edits keys.

## Looks and feel

- **Third person / other players** (`BodyAnims`, render-state modifier + `RenderPlayerEvent` lean, no mixins): the slide sits
  back feet first (legs out, a hand trailing, the body down the slide's line while the head keeps looking); the roll is a
  tucked shoulder roll over the landing; the mantle reaches up with both arms, then leans over the lip; the tactical sprint
  leans in with a longer stride. Others learn of a move from `body_seen` (players within 96 blocks).
- **First person** (`BodyCamera`, `ViewportEvent.ComputeCameraAngles`, FOV event, hand event): the roll dips the view 32°
  and swings it back; the mantle lifts the eyes to the lip then tips over it, hands reaching up; the slide drops to crawl
  eye height, tilts 7.5° with a faint ground judder and +7% FOV; the slide-cancel kicks the view up; the tactical sprint
  lowers the hands and widens the view (plus vanilla's own speed push). Only the drawn camera moves, never the aim.
- **Dust** (`awaken:body_dust`, four pixel-art frames from `tools/art/specs/body.py`, a clump breaking into specks): tinted
  by the ground it came off (map colour softened toward grey); the run's own sprint footprints pause during a slide so only
  this grit shows: slide spray every tick, a roll's ring, chips off a mantled lip, a kick-off puff, a burst
  at a tripped or momentum-hit body. No vanilla particles.
- **Sounds** (`tools/art/specs/audio_body.py`, synthesised, mono, sfx loudness): `body.roll` (shoulder thump, cloth, grit,
  feet coming round), `body.sprint` (heel, scuff, cloth snap, air), `body.slide` (hip drop, a friction slide falling in pitch,
  gravel), `body.mantle` (two palm slaps, cloth over the lip, a boot on top), `body.impact` (a low punch under vanilla's hit).
  The mover hears its own at once; the server plays them for everyone else.

## Unlocks and the System

Each unlock is one toast, sent in the same tick as the awakening (`Progression.onAwakened`) or the rank-up
(`RankHooks.onRankUp`), so it joins that moment's burst ("N new things · K") instead of standing alone. The awakening's:
"Your body remembers" (roll on landing · double-tap sprint). Announced moves are saved in `awaken:body_memory` (schema 1,
copied on death); an old save that already holds a move hears about it once, 3 s after logging in. The Status screen's
**THE BODY** section lists only the moves you have (never the ones still to come) with how to do them; so does
`/awaken body`. Counters: `awaken:body_roll`, `_sprint`, `_slide`, `_mantle` (for Truths and Directives later).

## Try it

- `/awaken body`: your moves.
- Game masters: `/awaken body try <roll|sprint|slide|tacslide|slidejump|mantle|hit|release> [delay]` makes your client
  press the move's keys (W, sprint, crouch, jump, attack) for a few ticks, exactly the path real input takes; the delay
  lines a move's frame up with an autopilot shot. `/awaken body forget`. In autopilot runs every move's start and end
  (and why it ended) is logged as `[body] t<tick> ...`.
- Autopilot: `tools/autopilot.sh tools/autopilot/body.txt` (every move in first and third person, the twists, the Status line).
- Sounds: `python3 tools/art/specs/audio_body.py`; dust: `python3 tools/art/build_all.py body`.

## Tests (`tools/gradle.sh 1200 runGameTestServer`)

`body_mantle_ledges` (1.5 block + slab yes; 2 blocks, one block of room, a fence, a wall, a plain step, out of reach, a ceiling
over the head no; rank C yes, D no; a claimed ledge that isn't there refused), `body_mantle_gate_walls` (five real gate floors from
the generator: from every standing spot in every room, eight directions, every accepted ledge is inside a room or a doorway and
under its ceiling), `body_slide_gap_and_wall` (crawl box passes a 1-block gap a standing box can't, ~4 blocks, a wall stops it,
~5 out of a tactical sprint; the server forces and releases the pose; the cooldown), `body_slide_trips_small_foes`,
`body_soft_landing_damage` (6/9/12 blocks: vanilla 3/6/9, rolling 0/1/3), `body_soft_landing_never_stacks` (Hover's landing,
a horse, an elytra, Low Gravity, perks), `body_nothing_before_awakening`, `body_unlocks_announced_once` (and a save and load),
`body_tactical_sprint` (modifier on and off, +25%, hunger, stop, cast, riding, recharge), `body_momentum_hit_bands` (walk 10,
sprint ×1.096, tactical ×1.18, slide ×1.3, more knockback, a weak swing nothing). In powers: `powers_twist_slide_punch_lineup`,
`powers_twist_smoke_trail_lineup`, `powers_twist_dash_out_and_chain_refund`.

## Later (not in v1)

A dive, a single wall kick at B, a "Flow" bonus for chaining moves, enemy telegraphs and lunges, better air control at A.
The Player Animation Library (when the pack has it) could replace the vanilla-pose approximations.

## Known gaps

- Nobody has played it with real hands yet: judged by tests and autopilot frames. The sounds were judged by spectrogram only.
- Vanilla's player model has no knees, so the slide and the roll are poses of rigid limbs (sitting back; a tucked flip).
- The client can't see a power's no-fall window (server-only), so after Dash or Meteor Dive a sprinting landing may play the
  roll's look although the power already cancelled the fall. Flight and Hover landings are recognised (flight within 7 s).
- A dedicated server's players see another player's slide pose from the server's forced pose; their dust and lean come from
  `body_seen`, so a player more than 96 blocks away sees a plain crawl.
