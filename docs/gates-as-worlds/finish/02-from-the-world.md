# Brief 02: a gate you find in the world leads in, and everything a gate does still works there

**Why.** On 11 Nov Euan walked into a hive gate in his world, expected a new dungeon and got the old kind: "it put me in a
normal-ish gate... very similar to the legacy gates. Did I enter the right one?" The switch (`gateRealms`) was off. Every walk of a
new gate so far used an admin command that forms the gate and puts you at its way in. Nobody, and no test, has stood in the
overworld with the switch on, found a gate, walked through its portal, played, left, come back, cleared it and watched it close.
He wants "nothing broken" and "fully featured". This brief proves that whole loop and turns the switch on for everyone.

**Done looks like.** With a fresh game folder and no command but the ones a tour needs, a gate found in the world leads into a
finished new dungeon; leaving, returning, dying, logging out, a restart, the Break clock, the Satchel, pay, the Lord's payout, his
bell, the Atlas and the Tasks page behave as they do for an old gate; each twist and modifier either works in a new gate or is
knowingly kept on the old floors, written down; and `gateRealms` is on by default with the old gates' tests still green.

## Read first

- `docs/SPEC.md` §50.2 to §50.7 and §50.9, and `docs/gates-as-worlds/PLAN.md` section 5 ("Integrations", "Failure register").
- `gates/GateService.java` (where a gate is made: `Realms.choose` then `attach`), `gates/realms/Realms.java`, `Blueprints.java`
  (`open`, `pick`; a blueprint must say `"ready": true` to be rolled), `gates/run/Runs.java` (`enter`, `gateOf`, leaving),
  `realms/RealmLive.java`, `RealmRun.java`, `RealmData.java`, `RealmState.java`, `GateCommands.java` (`realm`, `realm_enter`, `hop`,
  `interior enter`, `leave`).
- `gates/content/Twist.java`: `DOUBLE_GATE`, `RED_GATE`, `HOLLOW_GATE`, `TREASURE_GATE`, `INVERTED`, `FRIENDLY`, `ECHO_GATE`,
  `GATE_WITHIN`. Today `Realms.choose` returns nothing for any twist, a ritual or a trial, so those gates are floors.

## Part 1: a tour can turn the switch on

`/awaken gate realms on|off` (admin), setting `AwakenConfig.GATE_REALMS` for the running server. Say in its feedback that it is
saved to the config file. This is also how Euan can turn it on in game.

## Part 2: the walk from the world, on GitHub

A new tour, `tools/autopilot/realm-from-world.txt`: switch on; form an ordinary gate with the old command (`awaken gate open E hive`,
no forced blueprint, so `Blueprints.pick` chooses); go to it; shot of it standing in the land; **go through its portal the way a
player does** (find how a tour can: move the player into the portal blocks; do not use `interior enter` or `realm_enter` here);
`need realm`; shot at the way in; play far enough for a fight to wake (the camera is in survival for this tour, difficulty
normal); leave by the way home; shot back in the overworld beside the gate; go in again and confirm the dungeon is as you left it
(same blocks, the woken den still woken); kill the Lord by command; shot of the payout; leave; confirm the gate closes and the
claim arrives as for an old gate. Then the same once more for a rank and theme whose pick is a hand-built gate (bell crypt at E can
be The Hour Crypt). Fix what the walk shows.

## Part 3: go through the list, one row at a time

For each row: read the code path for a Realm gate, decide **works**, **broken** or **not built**, fix or build it, and where the
mechanism has no test of its own in a Realm write one (`realm_world_<thing>`, in a new `realms/RealmWorldTests.java`). Hand back
this table filled in.

| Thing | What must be true in a new gate |
|---|---|
| The bag | two standing gates are never the same Realm; entering marks it seen; a gate that closes unentered goes back in the bag |
| Break clock | counts down outside, holds while someone is inside, breaks the gate as for an old one |
| Leaving and coming back | by the way in; the Realm is as you left it; `RealmState` is saved and loaded |
| Dying inside | respawn and Satchel stake exactly as an old gate |
| Logging out inside, and a server restart | you come back where you were, or safely at the way in; `Realms.onStarted` puts every standing Realm's plan back before any chunk is asked for |
| Two players | the same Realm for both; one leaves, it stands for the other |
| Satchel and Spoils, gate pay, the Lord's payout | SPEC §50.3, §50.4: same numbers as an old gate of that rank |
| Bells | the Lord's own bell rings; a bell the gate had swallowed is where SPEC's bells section says it is in a gate |
| Atlas and Journal | a Realm gate shows on the map and in the Journal as a gate does (name, rank, cleared) |
| Tasks page | "go into a gate" and every other gate step counts for a new gate (`core.api.Goals`) |
| Modifiers (Low Gravity, Flooding, Lightless and the rest) | each applies in a Realm as it does on a floor, or the gate rolls floors and SPEC says why |
| Twists | see part 4 |
| Release | when the gate is released its cell is erased and `RealmState` deleted |

## Part 4: twists

Every twist must end in one of two states, written in SPEC §50.7: **works in a new gate**, or **this twist rolls the old floors,
because...**. Build these two, which the run already has the pieces for:

- **Red Gate**: the way home is sealed until the Lord falls. The fall catch and the way in must still work; only leaving is shut.
  Say so on the gate's card exactly as an old Red Gate does.
- **Treasure Gate**: the loot-carrier that runs. Put it in a late den or the vault of a branch; it flees along the run's own legs.

For the other six, read what each does on a floor and write one honest paragraph each: what it would take in a run of legs. Do not
build them. They go to Euan as a list.

## Part 5: on for everyone

Change the default of `gateRealms` to on. The old gates' tests make gates and expect floors: find every test that makes an
unscripted gate (search for `GateService.create`, `GateLiveTests.plan`, `open(` in test classes) and pin the switch off for the
length of that test, the way `realm_flag_off_rolls_floors` saves and restores it. This brief alone may run whole test families on
GitHub, once, at the end: `tools/check-ci.sh tests gates_,gateworlds_,realm_`. Do not touch `/Volumes/MEDIA/Awaken-play`; the
manager session turns it on in Euan's own config.

## Yours

`realms/Realms.java`, `Blueprints.java`, `RealmRun.java`, `RealmData.java`, `RealmState.java`, the new `RealmWorldTests.java`, the new
tour, the switch command in `GateCommands.java` (one command; brief 04 changes another), `core/AwakenConfig.java` (the one default),
the twist code for Red and Treasure, and the pin in old tests. `RealmLive.java` only for a twist's hook and a fix the list finds.
SPEC §50.7.

## Traps

- A gate made by a test with the switch on becomes a Realm and takes a cell: release what a test makes.
- `Blueprints.replace` in a test replaces the loaded Realms for the whole server. Put back what you replace.
- The crossing screen (SPEC §50.5) names the place. Never the Lord.
- A rolled kind takes its seed from the gate's plan, so every gate in the world is a different dungeon. Do not cache a land by
  blueprint alone.
