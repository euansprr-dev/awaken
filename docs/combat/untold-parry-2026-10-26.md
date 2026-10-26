# Untold blows and the parry, 2026-10-26

Euan's playtest: parrying a zombie is "very hard to time", because nothing tells you when it will hit. This note
measures that and the rule now in the game (SPEC §25, "Untold blows"):

- **Untold blows.** A creature's own melee with no wind-up to read gets a deflect window
  `ArmsRules.UNTOLD_DEFLECT` = 4 ticks wider than the style's: 8 ticks for the longsword instead of 4.
  These are the creatures that aren't ours and aren't in `#awaken:told_melee`: zombie, husk, drowned, spider, cave
  spider, silverfish, endermite, piglin and the like.
- **Smaller reward.** An untold deflect pays `ArmsRules.UNTOLD_REWARD` = 0.6 of a told one (Resonance and stance).
- **Told blows are unchanged.** Every Awaken creature, an iron golem, a ravager and a warden keep the tight window.
- **No prediction.** Nothing is shown on screen.

Run it:

```
python3 tools/sim/combat_sim.py --untold
```

The script reads the window, the guard's reduction, the reward and the deflect payouts from `archetypes.json` and
`ArmsRules.java`.

## The model

The model is in `tools/sim/untold.py`. It is solved exactly, so there are no seeds.

**What the mob does.** Vanilla's `MeleeAttackGoal` swings and deals damage on the same tick, the moment the mob is in
reach and off its cooldown. After that it waits 20 ticks. So the only things a player can time an untold blow by are:

- the rhythm of the blows before it;
- the moment the mob walks into reach.

**How a raise lands.** Each raise lands `aim + e` ticks ahead of the blow, where the error `e` is a normal spread:

- **Skill.** This is the profile's own spread. It is calibrated so a raise aimed at a told window's middle lands as
  often as the Lord table's profile does: 0.60 for the Parrier, 0.85 for the Expert.
- **Cue.** This is the extra spread from having no tell.
  - A blow that keeps the 20-tick rhythm: 2 ticks (interval timing near 1 s holds to about 5–10%).
  - The first blow on contact, or after a knockback: 4 ticks.
  - The figure quoted below is the 3-tick blend. The table sweeps 2 to 6.

**Outcomes.**

| Lead (ticks between raise and blow) | Result |
|---|---|
| Below 0 (raised after the blow) | A hit. Singleplayer has no latency grace. |
| Inside the window | A deflect. |
| Past the window (raised early) | A block, taking 30% of the blow. |

**A told blow** is played as the Lord table plays it. A miss there is always early (the tell lets you be early), so it
blocks and never hits.

**Damage** is a zombie's 3 on Normal. "Blows" is how many it takes to fall from 20 HP.

## Results (longsword: window 4 → 8, cue 3)

| Who, which case | Deflect | Block | Hit | Damage per 100 blows | Blows to fall | Resonance per blow | Stance per blow |
|---|---|---|---|---|---|---|---|
| Anyone holding the guard up (no parry) | 0% | 100% | 0% | 90 | 22 | 0 | 0 |
| Parrier vs a told blow (reference) | 60% | 40% | 0% | 36 | 56 | 12.0 | 10.8 |
| **Parrier, untold, before** | **40%** | 30% | 30% | **117** | 17 | 8.0 | 7.2 |
| Parrier, untold, after, keeping the told habit | 64% | 6% | 30% | 95 | 21 | 7.7 | 6.4 |
| **Parrier, untold, after, raising at the wider window's middle** | **70%** | 15% | 15% | **58** | 35 | 8.4 | 7.0 |
| Expert, untold, before | 45% | 27% | 27% | 106 | 19 | 9.1 | 8.2 |
| Expert, untold, after, raising at the wider middle | 77% | 11% | 11% | 44 | 45 | 9.3 | 7.7 |

The sweep over the cue (Parrier, before → after raising at the wider middle):

| Cue | Deflect | Damage per 100 blows |
|---|---|---|
| 2 ticks | 48% → 80% | 101 → 39 |
| 3 ticks | 40% → 70% | 117 → 58 |
| 4 ticks | 33% → 61% | 130 → 76 |
| 6 ticks | 24% → 46% | 148 → 104 |

## What it says

1. **Parrying a zombie didn't pay before.** A Parrier trying to time zombies took 117 damage per 100 blows. That's more
   than the 90 they'd take just holding the guard up, because three raises in ten came after the blow. This matches
   what Euan felt.
2. **Most of the gain comes from players learning to raise earlier.** The wider window only opens on the early side.
   A raise that comes after the blow still lets it land, so a player who keeps the told habit converts blocks into
   deflects (40% → 64%) but takes about as many hits. The wider window makes raising early safe, and a player who does
   it (aiming at the middle of 8 rather than 4) halves both the hits and the damage: 117 → 58.

   No late-side window was added. That would mean holding every zombie blow back a few ticks to see whether a guard
   comes up, and on screen that reads as lag.
3. **The reward cut keeps the payout about even.** An untold deflect pays 0.6, so Resonance per blow moves
   8.0 → 8.4 and stance per blow 7.2 → 7.0.

   Untold deflects happen more often but are worth less, so a zombie's stance still breaks at about the same pace.
   What the player gains is safety, plus more ripostes (every deflect opens one), not a faster meter.
4. **Told play still pays far better.** A Parrier against a told creature takes 36 per 100 blows and earns 12
   Resonance per blow. Untold play is still the worse fight: easier than before, but reading a tell remains the skill.

## Does it shift the open pacing proposal (`sim-2026-10-26.md`)?

**No.** The proposal's numbers stand.

- Every Gate Lord and every summon is an Awaken creature: `Mobs.spawnMinion` makes an `AwakenMob`, which tells its
  blows through `MobGoals.ToldMelee`.
- `Arms.untold` is false for the `awaken` namespace, so no blow in that table changes.
- Its bug 4 is the Mirror Knight's Perfect Parry counter, a `doHurtTarget` outside `Strikes.deliver`. That counter
  stays on the tight window, as a Lord's blow should. Its fix is still to tell the blow (mobs).

Where the rule does matter is wherever vanilla's melee mobs fight you: zombies, spiders and piglins in the overworld,
the caves and the Nether.

## What it can't tell you

- **The cue spread is assumed.** It is the number that matters most (see the sweep); a playtest of zombie parries
  settles it.
- **One blow at a time.** The model doesn't play out a crowd, knockback or the riposte's damage.
- **Vanilla mob numbers.** Damage uses vanilla's zombie on Normal. Hard deals 1.5×; the shares don't change.
