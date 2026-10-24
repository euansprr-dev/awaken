# Awakened Body — proposal for review

Status: **approved by Euan on 2026-10-24 ("the awakened body moves look good, build them"); being built on branch
`pack/awakened-body`.** Open questions take the defaults below: the System announces each unlock, and players who have not awakened keep exactly vanilla movement.

## Why

No parkour mod runs on 26.3 NeoForge (ParCool stops at 1.21.8), and one would hurt anyway: Awaken already has a full
movement kit as powers (Dash, Blink, Wall Run, Water Run, Hover, Glide, Flight, Leap, Grapple, Jet Burst, Shadow
Step). If the base body could already do all that, the powers would feel smaller.

The idea instead is to make the awakening itself felt in the body. Before you awaken, you move like a vanilla
player. After it, a few small, physical abilities unlock with rank. They never compete with powers. They make the
in-between moments (a ledge, a gap, a drop) feel fluid, so the world feels built for you.

## The three moves (v1)

| Unlocks | Move | What you do | What happens | Numbers |
|---|---|---|---|---|
| Awakening (E) | **Soft landing** | Land while sprinting or crouching | A roll: the camera dips and recovers, and the fall hurts less | Safe fall +3 blocks; beyond that, damage ×0.6. No effect on elytra or powers' own landings |
| D | **Mantle** | Jump at a ledge up to 1.6 blocks above your feet | You pull yourself up over it in one motion | 6 ticks; needs 2 free blocks above the ledge; not while holding a shield up or using an item; 0.05 exhaustion |
| C | **Slide** | Crouch while sprinting on the ground | A low slide that keeps your speed and fits under 1-block gaps; jumping out of it carries momentum | 14 ticks, about 4 blocks, crawl-height hitbox, 1 s cooldown, 0.1 exhaustion; jump-out adds 0.15 horizontal speed |

Later candidates, not in v1: a single wall kick at B, and better air control at A.

## How it fits Awaken

- **Progression:** each move is a passive tied to rank (`Profile.rank`), announced by the System the first time it
  can trigger ("Your body remembers how to fall"). Optional: a Latent could unlock one early.
- **Powers:** no overlap. Slide keeps the Movement slot (Left Alt) free for powers, and a power's movement always
  takes priority. Example: Dash out of a slide works and keeps the slide's direction.
- **Server-authoritative**, like the Velocity powers (SPEC §6). The client predicts the move; the server validates
  the geometry (the ledge exists, the gap is real) and corrects it. One new payload per move start.
- **Multiplayer:** other players see the move as an animation. This uses the Player Animation Library when the pack
  has it, and vanilla poses otherwise (crawl pose for the slide, a crouch for the roll).
- **Feel:** each move gets its own sound and a small camera motion, following the visual quality bar. No vanilla
  particles reused.

## Tests

GameTests:
- mantle succeeds on a 1.5-block ledge, fails on a 2-block ledge, and fails under a 1-block ceiling
- the slide passes a 1-block gap and stops at a wall
- soft-landing damage at 6, 9 and 12 blocks
- nothing triggers before awakening

Plus an autopilot tour with a frame strip of each move, in first and third person.

## Questions for Euan

1. Are these the right three, and is the unlock order right?
2. Should the System announce each unlock, or should you discover it by doing it?
3. Should vanilla players (not awakened) keep exactly vanilla movement? Proposed: yes.
