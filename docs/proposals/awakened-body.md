# Awakened Body — proposal (approved, v1 scope)

Status: **approved by Euan on 2026-10-24** ("the awakened body moves look good, build them"), then widened the same
day ("add some kind of shift sprint or smth similar to call of duty movement... sliding, spells, etc. Basically make
the combat of minecraft way more dynamic"). Being built on branch `pack/awakened-body` as module `dev.awaken.body`.
Players who have not awakened keep exactly vanilla movement. The System announces each unlock quietly, folded into
the awakening or rank-up burst, and the Status screen lists the moves you have.

## Why

No parkour mod runs on 26.3 NeoForge (ParCool stops at 1.21.8), and one would hurt anyway: Awaken already has a full
movement kit as powers (Dash, Blink, Wall Run, Water Run, Hover, Glide, Flight, Leap, Grapple, Jet Burst, Shadow
Step). If the base body could already do all that, the powers would feel smaller.

The idea instead is to make the awakening itself felt in the body, and to make fights move. Before you awaken, you
move like a vanilla player. After it, a few grounded, physical abilities unlock with rank, in the spirit of a modern
shooter's movement (tactical sprint, slide, slide-cancel, vault). They never compete with powers: anything airborne or
long-range stays a power. They make the in-between moments (a ledge, a gap, a drop, the run into a fight) fluid.

## The four moves (v1)

| Unlocks | Move | What you do | What happens | Numbers |
|---|---|---|---|---|
| Awakening (E) | **Soft landing** | Land while sprinting or crouching | A roll: the camera dips and recovers, and the fall hurts less | Safe fall +3 blocks; beyond that, damage ×0.6. No effect on elytra, mounts or powers' own landings; never stacks with another softer-fall rule |
| Awakening (E) | **Tactical sprint** | Double-tap the sprint key | A harder run, hands lowered, the view pushed wider | +25% sprint speed for up to 3 s; recharges 4 s after it ends; costs hunger. A swing or a cast ends it and lands at once |
| D | **Slide** | Crouch while sprinting on the ground | A low slide that keeps your speed and fits under 1-block gaps; jump out of it to keep the momentum (the slide-cancel) | 14 ticks, about 4 blocks (about 5 out of a tactical sprint), crawl-height hitbox, 1 s cooldown, 0.1 exhaustion; jumping out adds 0.15 horizontal speed |
| C | **Mantle** | Jump at a ledge up to 1.6 blocks above your feet | You pull yourself up over it in one motion | 6 ticks; needs 2 free blocks above the ledge; not onto fences or walls; not while holding a shield up or using an item; 0.05 exhaustion |

## Combat

- **Momentum hits**: a melee hit while sprinting or sliding hits harder and knocks further, scaled by your horizontal
  speed (nothing at a walk, about +10% at a sprint, +30% at most from a slide). Vanilla's attack cooldown still rules:
  a weak swing gets nothing.
- **Trip**: sliding into a small hostile creature knocks its legs out (a brief stagger and slow).
- **Slide-casting**: casting a power mid-slide never ends the slide; it keeps its momentum. A movement power still wins
  (Dash out of a slide works and keeps the slide's direction).
- **No invincibility frames.** Dash stays the dodge; the low hitbox already makes arrows and wide swings miss.
- **Chains**: a cast within half a second of a move refunds a little energy (at most once a second).
- **Family twists** (in powers): a Kinetic cast mid-slide releases a forward shockwave, a Velocity Dash out of a slide
  goes further, a Shadow cast mid-slide leaves a smoke trail that loses your pursuers.

## How it fits Awaken

- **Progression:** each move is a passive tied to rank (`Profile.rank`), announced by the System when it unlocks
  ("Your body remembers how to fall"), folded into that moment's burst of toasts, and listed on the Status screen.
- **Powers:** no overlap. The moves never use the Movement slot (Left Alt) or any power key, and a power's movement
  always takes priority. Body state reaches powers only through `core/api/MovementHooks`.
- **Server-authoritative**, like the Velocity powers (SPEC §6). The client predicts the move; the server validates the
  rules and the geometry (the ledge exists, the space is free) and corrects it. One payload per move start.
- **Off** while riding, sleeping, projecting in the Veil and meditating.
- **Multiplayer:** other players see each move as an animation of the body (a roll, a lean, a feet-first slide).
- **Feel:** each move gets its own synthesised sound, its own camera motion and its own dust; no vanilla particles.

## Tests

GameTests:
- mantle succeeds on a 1.5-block ledge and on a block topped with a slab, fails on a 2-block ledge, under a 1-block
  ceiling, onto a fence or a wall, and never leaves a real gate room or reaches its roof
- the slide passes a 1-block gap and stops at a wall; out of a tactical sprint it goes further
- soft-landing damage at 6, 9 and 12 blocks against vanilla; no stacking with a power's landing, a mount or Low Gravity
- tactical sprint: the modifier is applied and removed, the recharge, refused while riding
- momentum-hit damage bands
- nothing triggers before awakening; each unlock is announced once and that survives a save

Plus an autopilot tour (`tools/autopilot/body.txt`) with frames of every move, in first and third person.

## Later (not in v1)

- A dive (sprint, jump, crouch).
- A single wall kick at B.
- A "Flow" bonus for chaining moves.
- Enemy telegraphs and lunges, so the moves have something to answer.
- Better air control at A.
