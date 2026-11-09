# The Hall of Arms

Approved by Euan on 9 Nov 2026: "This looks awesome go ahead with the full plan and a very high quality bar like this."
Plan page and clickable mock-up: https://claude.ai/artifact/UZX5KofQPGeXHeiPxJ9sxK (private). What it does: SPEC §52.

## Why
The War Anvil window, the Fitting Stand and the Armoury were "just a block of text... ugly... not creative... not magical", and
choosing armour was "too linear". They become places: a wall of weapons, terraces of suits, a weapon hanging over your own fire.

## The ten decisions
1. Direction B: the Hall, and an Anvil with no window.
2. Unfound weapons and suits are black shapes with no name. Lord-forged are fully sealed.
3. Hold to commit by default (0.6 s), one click as a setting.
4. A higher rank's true look shows once you know the pattern.
5. The Hall opens from K, for looking and tracking only.
6. Jacquard 24 baked to a bitmap (OFL); the font file is not in the repo.
7. The weapon hangs in the air at the Anvil and lands on it when you commit.
8. Attune with any stone you carry, the off hand's chosen first.
9. `claude/armoury-says-what` merges first (this branch is built on it).
10. Old screens behind `classicScreens` for one build, then deleted.

Added after approval (Euan, 9 Nov): clicking a weapon or suit brings it close, as the hero, with a side panel of what it does and what it
takes. Everything shown is the real icon of the actual thing, "so you actually know when you find it". No drawn stones or sigils.

## Stages
All built on `claude/hall-of-arms`: 0 spike (live weapon meshes in a screen), 1 the Wall, 2 a weapon brought close, 3 the Armour wing,
4 the Anvil, 5 K's line-up, 6 the moment it's made, sounds, accessibility.

## Still to do after Euan has played it
- Delete the old screens one build later: `arms/client/ArmouryScreen`, `armour/client/ForgeScreen`, `arms/anvil/client/AnvilHeldScreen`,
  `tasks/client/SetPreviewScreen`, and `classicScreens` itself.
- Frame time on his Mac against the K menu at Cinematic (a Mac test, in a quiet moment).
- The sounds have not been heard by anyone yet.
