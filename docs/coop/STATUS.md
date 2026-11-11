# Together, All the Way: where the build stands

SPEC §70. Approved in full by Euan on 11 Nov 2026 ("Go ahead with the full plan"), after his change the same day: no
healer, her own difficulty (My fights: Full or Gentle). Built on `claude/coop-plan-630b2d` by the planning session,
with no local Java (Euan was playing): everything was compiled and tested through CI.

## Built (code in `dev.awaken.together`, hooks in `core.api.FightsHooks`)
| Part | What stands | Where |
|---|---|---|
| The party | Saved per world, up to four, by a card, by the opening, or by a shared home (auto-join; `apart` for those who left) | `PartyData`, `PartyService` |
| The band | TOGETHER section: members, Leave party, Travel together, Share my home, My fights Full or Gentle | `client/TogetherSection` |
| Seeing | Party strip (name, health, state, edge arrows with distance), marks on the Atlas, a visible knee, the down screen | `client/PartyStrip`, `client/Kneeling`, `TogetherNet.Party` |
| Pings | Middle click in a party points; twice says "Careful here"; a lock is shared as "Their target" | `client/Pings`, `AimClient.freePress` |
| Gentle | Half damage, three second winds, caught falls in gates, two small monsters at a time, Story's death cost, half a fighter | `Fights`, `FightsHooks` |
| Gentle at a boss | Plain blows only where the phase has any, two in a row, no forks or mind answers, +8 ticks of hold, opening x1.5, one phrase in four, a held guard stops amber | `FightRunner`, `ArmsServer` |
| Any two in a fight | A tell carries its target and the ring is thin for anyone else; a boss holds its target through a phrase and turns at openings; its back is turned (+25%); stance grows with the party | `Strikes.Tell`, `AwakenMob`, `AwakenBoss`, `Reticle`, `Rooms`, `ArmsServer` |
| Standing back up | Down for 20 s in a gate with a partner standing; Use held on them lifts; once a floor | `Fights.goesDown`, `Runs.onDeath` |
| The door | A real death wakes you at the gate's door; going back in takes you to your party's floor | `Fights.onRespawnPosition`, `Runs.rejoinFloor` |
| Reassessments | Everyone of the party near the bell comes on their own setting; short of the level they are told; Warden sized by fighters | `Provings` |
| The Answering Peal | A handbell calls far partners; ringing back within a minute brings you to them | `Answering`, `BellHooks.onRung` |
| A home for two | The Hearth's invitation from the band, and made for you when a home is founded beside a partner with none | `HomeForTwo` |
| Arrival | Awakened players are told when someone who hasn't awakened logs in | `TogetherModule` |
| Riding Along (§55) | Retired: the package is gone; `RidingHooks` answers "nobody rides"; `folded` now means on a knee | `core/api/RidingHooks` |
| Tests | `core.test.Pair`; `pair_` and `gentle_` tests, `alone_plays_the_same` | `TogetherTests` |
| Tours | A server-played partner (`/awaken together partner ...`); `coop-full.txt`, `coop-gentle.txt` | `TourPartner`, `tools/autopilot` |

## Not built yet (each is named "not built yet" in SPEC §70)
- The second keeper of a Hearth (owner's rights for one member): needs the Hearth's owner checks opened one by one.
- `Go to <name>` as a destination that follows; pings on the Atlas; shared binocular marks.
- A read per player in `FightMind` (it still starts again on a new target; with the hold that is once a phrase).
- Match my friend; shown the way; leads from a partner's finds; the Ours page; marks on the Tasks page.
- The finishing film for everyone (the boss plan isn't built); the helm rule for the Far Landing (not merged here).
- Hard-feat titles and records refusing Gentle (no counter is touched yet: list them with the progression owner).
- The two-client CI probe and the wire baseline numbers (the pair kit can count packets: `Pair.count`).
- Dead branches left where code still asks `RidingHooks.rider` or `run.riding` (always false now): gates, progression,
  trials. Harmless; a cleanup for whoever next touches those files.

## Unseen and unmeasured
Nobody has played any of it. Every Gentle number is a first value in `awaken-together.toml`. The strip's place on the
screen, the knee, the markers and every line were chosen without a picture until the two partner tours ran.
