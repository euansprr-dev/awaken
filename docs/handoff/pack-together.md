# Hand-off: pack/together (multiplayer, "Together")

Cloud session, 2026-10-24. The work list is docs/multiplayer-audit.md; the rules are SPEC "Together". Euan's decisions:
the story starts together, friends share one Hearth as members, and a Hearth can grow into several over time.

## Done
- **The WIP (ebf8841) finished.** It built as it was; its one failing test (`together_server_throttle_and_as`) passed
  a UUID to `/awaken as`, which vanilla's player argument never takes. The test now names its two players.
- **origin/build/awaken-v1 merged in**, twice: 2e05a76 (the CI workflow, the cloud hand-off, holo cards, lands
  polish), then 90801b4 (adaptive gate music) when it moved during the session. One SPEC §12 conflict: both sides
  added an API line; both are kept.
- **All 17 audit items are fixed**, each with GameTests of two or more players; the audit doc lists them per item with
  the player count, and a "Deferred" section for what is left.
- **Found and fixed while testing:**
  - Lucidium cells leaked when a plan was never dreamed: thrown away at morning, or replaced each time a player changed
    their intention (the audit's item 8 path). Now every dropped plan gives its cell back, a build cancelled while
    compiling releases it when it completes, and a sleeper about to dream a dropped plan keeps it until their night
    ends. Dream builds count against the 3-per-1000-ticks limit only when a dream is actually compiled.
  - The kill-sharing loop was copied four times (gate, Descent, home kills, Echo binding). It is one rule now,
    `core.api.Parties.sharing`, so the tested home-kill path and the Descent path run the same code.
  - The hearth module's server-stop cleanup was three listeners; it is one method (`HearthService.forgetPeople`).
- **Old single-player saves** load and play the same: `together_old_saves_load_the_same`.
- **`core/api/PartyHooks.java` is unchanged** since it was added (3e3c0ca; blob sha1 a8d1487...). Note:
  `origin/carillon/main` does not exist on GitHub (no carillon branch or repo is reachable from this session), so the
  byte-for-byte check against it could not be run here. Run
  `git diff origin/carillon/main -- src/main/java/dev/awaken/core/api/PartyHooks.java` on the Mac, where that branch
  lives, before merging.

## Hearth membership API
Server side only; cheap to call. A Hearth has one owner and any number of members; "belongs" and "member of" mean
either. Anchors are always in the overworld.

`dev.awaken.hearth.Hearth` (the module's public API, SPEC §12):
```java
public record HearthInfo(UUID owner, String ownerName, BlockPos anchor, int tier, int radius, List<UUID> members,
                         Optional<UUID> foundedFrom) {
    public boolean has(UUID player);                              // owner or member
}
public static List<HearthInfo> hearthsOf(MinecraftServer server, UUID player); // own first, then shared in join order; offline players too
public static List<HearthInfo> hearthsOf(ServerPlayer player);
public static Optional<HearthInfo> home(ServerPlayer player);                  // own Hearth, else the first shared one
public static boolean isMember(ServerPlayer player, BlockPos anchor);
public static boolean isMember(MinecraftServer server, UUID player, UUID hearthOwner); // offline players too
public static Optional<HearthInfo> hearthAt(ServerLevel level, BlockPos pos);  // whose radius holds pos (nearest Anchor where radii overlap)
public static Optional<BlockPos> anchor(ServerPlayer player);                  // the home Anchor
public static boolean atHome(ServerPlayer player);                             // inside a Hearth they belong to
public static boolean hasRoom(ServerPlayer player, RoomType type);             // reads the home Hearth
```

`dev.awaken.core.api.HearthHooks` (the bridge for modules that can't depend on hearth):
```java
public static Optional<BlockPos> hearthOf(ServerPlayer player);                // home Anchor
public static boolean belongs(ServerPlayer player, BlockPos anchor);
public static List<BlockPos> hearthsOf(ServerPlayer player);                   // own first
public static Set<UUID> household(ServerPlayer player);                        // everyone sharing a Hearth with them, never the player
public static boolean shareHearth(ServerPlayer a, UUID b);
public static List<BlockPos> within(ServerLevel level, BlockPos pos, int radius); // every Anchor nearby, whoever's
public static Optional<BlockPos> nearest(ServerLevel level, BlockPos pos, int radius);
public static boolean inside(ServerLevel level, BlockPos pos);
```

Changing membership (`dev.awaken.hearth.Household`, used by the commands and the Anchor; `HearthRecord` is the
module's saved record):
```java
public static final int SISTER_TIER = 3;                                       // a member's own Anchor founds a sister home from here
public static int sisterSlots(HearthRecord h);                                 // 0 below III, then one per tier
public static Optional<String> invite(ServerPlayer owner, ServerPlayer who);   // a choice card, open 2 minutes; empty = sent
public static boolean invited(MinecraftServer server, UUID who, UUID owner);
public static boolean accept(ServerPlayer who, UUID owner);
public static boolean join(MinecraftServer server, HearthRecord h, ServerPlayer who); // no invitation needed
public static boolean leave(MinecraftServer server, HearthRecord h, UUID who, boolean removed);
public static List<ServerPlayer> online(MinecraftServer server, HearthRecord h);
```
Commands: `/awaken hearth invite <player>`, `join <owner>`, `leave [owner]`, `remove <member>`, `members`.

Parties (`dev.awaken.core.api`): `PartyHooks.members(Player)`, `PartyHooks.sameParty(Player, Player)`,
`PartyHooks.install(Access)` (the city's guilds replace the interim rule here) and `Parties.sharing(ServerPlayer,
Entity)` (who of the party shares a kill; `Parties.SHARE_RANGE` 32).

## Left
- The interim party rule (gate run + shared Hearth) stays until the city's guild module installs real parties.
- Test reach (behaviour is in place): no GameTest stands in the Descent dimension for its kill sharing (same
  `Parties.sharing` as the tested home kills), and the stop test can't fill hearth `Breaks.ACTIVE` or Echo `Duty`
  before clearing them.
- Not this branch: earlier tests leave awakened mock players and blocks at the shared isolated-test spot, which trips
  order-dependent tests. `gates_surface_respects_builds` failed twice locally that way (each time right after
  `hearth_cottage_is_a_sanctuary`); pack/throughput's fix for it (bb3dc21) is ported here unchanged. The Together tests
  clean up after themselves. `fauna_p3_caves_spawn` failed once locally: it is on the hand-off's known-flake list until
  pack/throughput lands.

## Tours for the Mac
No new looks, so no screenshot tour. Worth one manual two-client LAN session before release: invite and share a
Hearth, a party gate (sealing, shared kills), and a shared dream.

## CI
Built and tested in the cloud VM (JDK 25.0.5 from foojay): `./gradlew build runGameTestServer`, "All 352 required tests
passed" on the final merged code (346 before pack/sound's tests came in with the second merge). GitHub Actions: the
GameTests run for this branch's head on pack/together.
