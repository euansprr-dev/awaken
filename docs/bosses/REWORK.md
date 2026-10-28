# Bosses, premium rework (plan, awaiting Euan's approval)

Written 2026-10-28 after Euan's first look at the three kit bosses. Process: `.claude/PLANNING-PROCESS.md`.
The approval page carries the same plan with pictures. Nothing here is built yet, apart from the quick fixes listed
under "Already fixed" (commit 8a95cecc and the turn-step fix after it).

## What Euan asked for
- **Look.** Today the Tyrant is "too derpy", "too blocky and rushed and vanilla-like". He wants premium, the kind that
  makes you think "this is insane". No round shapes.
- **Motion.** Today it is rough, glitchy and not smooth. The bar is Fresh Animations on vanilla mobs. Turning looks bad.
- **Sound.** Today stomps and floor hits are near silent, whooshes weak, roars sometimes mute, and Unit Zero beeps.
- **Fight.** The balance is fine, but it is a slow hit-block loop. He doesn't want more minions. He wants Elden Ring
  level choreography.
- **Readability.** Unit Zero swung at the air from afar, and one of its hits threw a projectile you couldn't predict.

## Already fixed (not yet heard or seen in game)
- **Turning.** The body eases after the fight's facing; the turn step fades with the turn (SPEC §35.4).
- **Sound palettes.** Class tells, swings, casts, ground impacts, roars and footfalls per boss (SPEC §35.8).
- **One imp.** Stoke the Imps calls one, not two.
- **Unit Zero.**
  - The step lands behind where you stand when it strikes, facing you.
  - It ends with its string if it can't reach you.
  - The lance fires from 6 blocks, its line locked bright for the last half second.

## What the audit found (verified)
- **The Tyrant is a stack of plain boxes.**
  - 45 cubes and 31 bones on a 512² sheet.
  - A small head with two dot eyes sits on a broad, shallow slab of a chest. The axe is hand-sized.
  - The same brown-and-orange cracks cover every surface, so nothing leads the eye.
  - The side view is thin.
- **Size and key counts are not the gap.** Cataclysm's Netherite Monstrosity is 86 cubes on a 512² sheet. Its
  moves average about 5 keys per channel, with 19 to 28 distinct key times a move. Ours average about 9 to 10 keys a
  move, with 19 to 20 distinct times. What differs is the craft.
- **Every move starts and ends at rest.**
  - 100% of recovery tracks end at the rest pose, and every wind-up starts there.
  - In a string, the body snaps back to the same stance between blows.
  - It crossfades in 0.15 s, and the next blow starts from rest again.
- **Whole-body freezes.** The Tyrant has 300 holds of a bone at one value for over 0.15 s; Mother has 395. At contact
  the hammer freezes about 0.5 s with nothing moving.
- **Three clips per move.** The wind-up, the strike and the recovery are separate splines, so the curve breaks at the
  peak and at contact.
- **Ground IK switches off for the whole move.** On uneven ground the hips jump when a move starts and drop when it
  ends.
- **The rhythm.** One blow, a long recovery (the hammer 20 ticks), then 6-14 idle ticks standing still. Strings are
  fixed lists of 2-3 moves. Nothing reacts to what you did. That is the hit-block loop.

## Recommendation: a structural rework, proved on the Tyrant first
Three ways were weighed:
1. **Polish.** Re-key, add sound, repaint. This is cheap, but it stays "vanilla-like".
2. **Structural rework (recommended).** New art direction, a motion kit and a fight grammar, on the existing kit.
3. **GeckoLib or Blockbench hand animation.** This adds a dependency, ports the synced move system, and there is no
   artist to drive it.

The rework has five parts:
- **R0 Tools.**
  - Animated lab previews (GIF strips of every move and string).
  - A smoothness meter: per-frame jumps, freezes, snaps through rest.
  - Fight cadence numbers from the sim: blows a minute, idle time, longest still spell, combo lengths.
  - A baseline report.
- **R1 Tyrant look.**
  - Three silhouettes at true size beside a player. **Euan picks one.**
  - Then the model, with a parts library: stepped tapers, layered plates, angled cubes, a real great axe, a heavy
    head with brow and jaw, the core inside a ribcage.
  - About 120-200 cubes, painted with material ramps and a value hierarchy.
- **R2 Motion kit.**
  - One continuous clip per move, with stage markers.
  - Link poses: a string's next wind-up starts where the last blow ended.
  - Successive breaking: hips, chest, shoulder, elbow, then the weapon.
  - Anticipation, overshoot and settle; no whole-body freezes.
  - A render-time spring layer, so no transition ever snaps.
  - IK stays on through moves, and secondary chains move.
  - Clip markers drive the footfalls, whooshes and impacts, camera shake and a 2-frame hit pause (client only, a
    setting).
- **R3 Tyrant fight.**
  - Phrases of 3-5 blows with branches: after two deflects, a guard breaker; when you back off, the charge.
  - It moves between attacks (circles, steps back, repositions).
  - A delayed strike always has a visible held tell.
  - Signature spectacles, and a body that changes by phase. No imps.
  - A sim retune to the targets, plus cadence targets.
- **Gate.**
  - Euan plays the Tyrant.
  - Go: R4 Unit Zero, then R5 Mother.
  - No-go: revise from his notes.

## Decisions for Euan
1. The structural rework, the Tyrant first. (Recommended)
2. A look gate before modelling: three silhouettes, and you pick. (Recommended)
3. No summons in the three showcase fights: Stoke, Deploy and Birth become boss moves. (Recommended)
4. A delayed strike only with a visible held tell. (Recommended)
5. Impact feel: camera shake and a 2-frame hit pause, client only, with a setting. (Recommended)
6. Sound: judge today's palettes first; custom-made boss sounds only if they still sound cheap, as their own plan.
   (Recommended)
7. A Blockbench export to view the models: not now. (Recommended)
8. The Lord damage and health curve stays tied to this gate, as decided in PLAN.md. (Recommended)
