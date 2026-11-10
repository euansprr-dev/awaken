# Gates as Worlds, Round Two: findings and plan (gates planning session, 10 Nov 2026)

Approval page: https://claude.ai/artifact/VGSkfcKhLahyckPstoWWvN (private to Euan's account; waits for his answers; nothing built).
Page source and figures: scratchpad `page/` of session 42d84c75, also committed on `claude/gates-as-worlds-plan-fcb81b` under `docs/gates-as-worlds/round-two/`.

## Findings that matter to Wonders too (all measured, not guessed)

1. **CI look tours can silently photograph the overworld.** 7 of 21 gate walks never entered their Realm ("Gate 1 is still forming",
   "No dry ground nearby for a gate", then "You're not in a Realm" on every shot). Check any CI shot folder with
   `grep -c "You're not in a Realm" tour.log`. Saltcomb's and Kingsroot's only walks are shots of Carillon. The Hour Crypt's
   "Lens off" walk (run 42716771731) is a meadow at midnight, so Lens was never ruled out as a cause of darkness.
   Ten walks also ran to the 1500 s watchdog because their scripts have no `quit` line.
2. **Dark in game = dark textures times thin light.** The game shows texture x light (vanilla `lightmap.fsh`: ambient + sky + block,
   block brightness = L/(4-3L) x 1.4). Family median texture luminance (0-255): Salt 140, Amber 122, Fane 99, Keep 69, Knell 53
   (Knell wall stone 22-38). Light blocks at 9-12 every 8-10 blocks add about 0.2, which on a texture at 38 is 8: "no measurable
   difference" was expected. Rule proposed: family median 95-150, no wall block under 60; darkness comes from light, never from
   the texture. The Wonders note already measured the Lens Cinematic darkening; this is the other half.
3. **Only Amber has a Lens look** (`assets/awaken/lens/looks_gates.json`, keys `biome:awaken:realm/...`). Everything else falls back
   to the default outdoor look. The blueprint's `sky.lens` field is read by nothing.
4. **The preview can take swapped assets without touching the repo.** `AWAKEN_PREVIEW_ROOT` points it at any tree holding
   `src/main/resources/assets` and `data`. A scratch root of symlinks plus changed textures ran in 35 s. Lifting Knell to Amber's
   range raised the preview's contrast by half to double in every shot; still not the painting, because candles in ones and twos
   only light 2-5 blocks.
5. **The long gates were a stall, not slow work.** From ~19:20 UTC 9 Nov every tool call in the old main session and both sub-agents
   returned after exactly 10 minutes: "PreToolUse hook did not respond before its timeout (host client may be unreachable)".
   Each wake re-wrote the whole context cold. About half of the two builders' usage was this. Real building time: 40-75 min a gate.
6. **Usage per gate** (weighted: input 1, cache write 1.25, cache read 0.1, output 5; millions): Amber 9.9, Cinder 5.4, Vine x2 3.6,
   Hour 3.2, Glowroot 3.1, Kingsroot 2.3, Sporefall 2.2, Saltcomb 1.2; Long Shaft 6.7, Wax 6.2, Ash 3.3 (mostly stall).
   About 60% of real cost was re-reading a 250k-token session each step. Builders ran the float/reach tests 13-16 times per gate.
   Calibration on the new account: about 5.3M weighted Fable tokens moved the weekly Fable meter 5 points.
7. The Hour Crypt's biome plays `awaken:music.gate.hive` and `awaken:realm.amber.hall` (copied from Amber, never changed).
8. The Amber stop page (PowaTjaubXomxNtcYfbc7z) can't be opened from this account.

## The plan in one paragraph

Three options: A Tighten (hand-built, leaks fixed), B the Gate Engine (wall grammar, supports so nothing floats, light by rule with a
true-light preview and a lit-rooms test, the painting as the plan, one spec file per gate, a bench that answers in seconds),
C Maximum (B plus shapes smaller than a block, moving set pieces, built Lord arenas, sound and arrival per gate).
Recommended: stage 0 "see truly" (3% of a week), stage 1 the engine proven on Wax Galleries and the Vine Seat's rooms (8%),
stage 2 The Hour Crypt at maximum for Euan to play (8%), then he picks the level for the rest. Per new gate: A about 2%,
B 1-1.5%, C 2.5-4%. Six decisions on the page; the first to look at are the level and the weekly share for gates; the handover's
question (who merges build/awaken-v1) is decision 2.

## Shared tools Wonders could reuse once built (stage 0 and 1)

Straight-in walk command with fail-fast; true-light preview; the texture brightness rule in `tools/art`; a Lens look made from a
painting's palette; the painting / preview / game three-up sheet; the wall grammar; the non-cube shape set (Maximum only).
