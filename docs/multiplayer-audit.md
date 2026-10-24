# Multiplayer audit (pack plan stage 6, "Together")

**Status (2026-10-24, branch `pack/together`): every item below is fixed; what is left for later is listed under
"Deferred" at the end.** The rules are in SPEC "Together"; the GameTests are named `together_*` and each item's tests
use two or more players (the old-save test is one player on purpose). What each fix does is listed under "Fixed" at the
end; the audit text is kept as it was written.

**Euan's decisions (2026-10-24):** the story **starts together** (the prologue and the Ritual of the Bell are shared,
see docs/proposals/the-ritual.md), and friends share **one Hearth as members**, with a way to grow it into several
Hearths over time.

Read-only audit of `pack/awakened-pack` at c63adfb, 2026-10-24. It covers what breaks, or behaves wrongly, when 2–4
players share one world (LAN, an e4mc link, or a dedicated server). Paths are under `src/main/java/dev/awaken/`.
"Unverified" means it was read in the code but not tried in a game.

## What already works
- Per-player state (gate runs, dreams, Veil projection, the Descent, powers, sightings, trials) is keyed by UUID,
  cleared on logout and server stop, and backed by attachments.
- No code picks "the player": no `getPlayers().get(0)`, and no `Minecraft.getInstance()` outside client packages.
- `power_fx` reaches every player near the caster, and auras sync to everyone tracking the player.
- The Signal UI and its attachments go to their owner only.
- Most payloads are validated: casts, equip, starter, Descent travel, Veil actions (reach 5.5, possess within 8),
  Echo ids, requests, StatusAction (rate-limited) and ChooseEnding.
- Party mechanics that work in gates:
  - the shared `inside` set (`gates/run/Runs.java:113-117`) and per-player floor pay
  - pressure grouped by floor (`gates/run/Pressure.java:62-71`)
  - the Lord's hoard, paid to everyone on the floor (`gates/run/Runs.java:425-429`)
- Elsewhere:
  - Sleepers within 10 blocks share a dream (`lucidium/dream/Sleep.java:171`), and the morning waits for everyone.
  - Two players can project into the Veil at once.
  - Each S-rank player in the Hollow Court gets their own Sovereign.

## Blockers for two players on a LAN
1. **One world-wide story.** StoryWorld holds one `ending`, and the last player to choose overwrites it.
   `ascension++` runs for each player, so tiers stack. `gatesCalm` and `colourRestored` apply to the whole world
   (`story/StoryWorld.java:22-25`, `story/Court.java:169-177`). A friend who joins mid-story finds that:
   - their gates stop breaking (`gates/GateRecord.java:94-96`);
   - monsters get +30% health and +15% damage per tier (`core/api/MobHooks.java:75-88`), with more XP too.
   **Needs Euan's decision:** is the story per player, or shared by the world?
2. **Projected Bodies take player melee.** If the Body dies, its owner really dies, even with PvP off
   (`veil/VeilEvents.java:124-126`, `veil/Projection.java:409-427`). Powers already treat Mannequins as allies
   (`powers/Combat.java:46`).
3. **Arena and boss sealing isn't party-aware.** The first player in seals the room with bars and locks teammates
   out, yet those teammates still get the hoard (`gates/run/Rooms.java:166-176`).
4. **A Hearth is one per owner** (`hearth/HearthData.java:19,30`). A friend sharing the base can't use the Anchor,
   can't build there with powers (`powers/Constructs.java:47-54`), sees nothing with spirit sight there
   (`veil/RevealFilter.java:15`), and gets no Town, requests or defence events.
   **Needs Euan's decision:** a shared Hearth with members, or one Hearth each?
5. **The gate Director rolls for whoever triggered the upkeep.** The four-gate cap counts every gate within 1500
   blocks, whoever owns it (`gates/Director.java:203,244`). So a veteran's A-rank gates and their breaks land on a
   newcomer's Hearth (`gates/Breaks.java:72-90`).
6. **Above machines** store an owner but never check it (`above/machine/Machines.java:74-77`), and a Clone Vat's
   "bind" hands it to whoever clicks (`above/machine/CloneVats.java:119-122`).
7. **Rewards go to the killer only.** Kill XP and elite loot (`gates/run/RunEvents.java:86`,
   `gates/run/Runs.java:154-185`) and Echo binding (`hearth/echo/Echoes.java:213`) skip assisting players, and gates
   don't scale with party size.

## Dedicated server
8. Nothing limits how often JournalAction "intent" or `Planner.request` runs, and each call compiles a dream in the
   background and takes a fresh cell. Lucidium cells are never reused; `cellAt` returns -1 past 4096
   (`lucidium/world/Cells.java:27-49`, `lucidium/dream/JournalActions.java:63-70`).
9. Gate cells and region files are only freed by a restart (`gates/GateService.java:350-406`).
10. Admin commands act on the caller only, with no player argument, so they can't be used from the server console.
11. RealityCheck and FlagUnfair have no throttle (`lucidium/dream/Dreams.java:612`, `core/CorePayloads.java:22`).

## Nice to have
12. A spirit can possess another player's bonded mount, because the owner check covers TamableAnimal only and
    WildMount isn't one (`veil/Possession.java:68`).
13. Fauna content level follows the nearest player; it should follow the group (`world/fauna/FaunaRules.java:114,173`).
14. The cast-fail sound plays to everyone nearby (`powers/Casting.java:62`).
15. Duty, `Breaks.ACTIVE` and `Projection.PENDING_DEATH` aren't cleared on server stop.
16. The "solo" counter uses the `party` set, which only ever grows (`gates/run/Runs.java:458`).
17. The Rival's scene shows to everyone nearby (`story/Rival.java:69-78`).

## Already promised in the docs
plan.html:853 promises party gates for up to four with separate loot rolls, shared dreams, Veil co-op ("one of you
guards the bodies"), a shared or separate Hearth, and friends who join late progressing at their own pace (line 1248).
plan-v2 kept single player first (lines 174, 182). SPEC has no co-op section yet.

## Fixed (pack/together, 2026-10-24)
Parties first: `core.api.PartyHooks` (the city's hook, verbatim) with an interim rule installed by gates (`GateParty`):
your party is everyone inside your gate run plus everyone who shares a Hearth with you. Who of the party shares a kill
is one rule, `core.api.Parties.sharing` (online, alive, not spectating, same level, within 32 blocks).

Tests per item (players in brackets):

1. **World story.** One ending per world (`StoryWorld` schema 2); later players are told it (`Court.tell`), never
   overwrite it. Become the Signal raises the world's Ascension once. Each player meets min(world tier, own tier):
   creatures are scaled for the nearest player and every hit with another player is evened out to that player's
   tier (`story/Ascension`), XP too (`Progression.xpScale`). Calm gates still break for an owner who hasn't reached
   the ending (`StoryHooks.gatesCalm(server, owner)`). Old saves record their chooser on login. "Wake up" restores the
   colour for the whole world (it is cosmetic and applies once).
   Tests: `together_story_one_ending_per_world` [3], `together_story_late_friend_own_tier` [2],
   `together_story_calm_gates_per_owner` [2].
2. **Bodies.** No player damage unless PvP is on and the attacker is neither the owner nor in the owner's party; a
   friend-felled Body never kills its owner (`VeilEvents.onBodyIncoming`, `Projection.playerMayHurtBody`).
   Test: `together_veil_bodies_spare_friends` [3].
3. **Sealing.** Party members on the floor within 48 step through as an arena or Lord's room seals; one who reaches
   the bars is let in (`Rooms.gatherParty`, `Rooms.admitAtBars`). Test: `together_gates_sealing_brings_the_party` [2].
4. **Shared Hearth.** Owner + members (`HearthRecord` household, `Household`), invite / join / leave / remove /
   members commands and asking at the Anchor; members use the Anchor, wake there, build with powers, see with spirit
   sight, share the town, requests, breaks, Trophy Hall and upgrades. Sister homes from tier III (one per tier above
   II); an owner moving their Anchor keeps the household and never founds a sister home. API: `Hearth.hearthsOf`,
   `Hearth.home`, `Hearth.isMember`, `Hearth.hearthAt`, `Hearth.HearthInfo` (signatures in docs/handoff/pack-together.md).
   Tests: `together_hearth_members_share` [3], `together_hearth_sister_home` [3].
5. **Director per owner.** The 1–4 cap counts your own gates; your gates never form within 256 of a Hearth you don't
   share, and one that comes due there folds instead of breaking. Test: `together_gates_director_per_owner` [3].
6. **Above machines** answer to their owner and the owner's household (and to anyone using one inside a Hearth they
   belong to); binding a Clone Vat never changes its owner (`Machines.mayUse`). Test: `together_hearth_members_share`
   [3] (a member works and binds the owner's Vat, which stays the owner's; a stranger can do neither).
7. **Shared rewards.** Gate kills credit the party on the floor within 32 (own kill XP share, own elite roll);
   Descent and home kills share XP; Echo binding gives each party member in range their own lingering Echo. Gates
   scale with the party: +35% monsters and +1 elite per two per extra player, the Lord +50% health per extra player.
   Tests: `together_gates_kill_credits_the_party` [3], `together_gates_scale_with_the_party` [2],
   `together_rewards_shared_outside_gates` [3] (home XP and Echo binding).
8. JournalAction 4/s, dream builds 3 per player per 1000 ticks (counted when a dream is actually compiled), reality
   checks 1 per 10 ticks (`core.Throttle`; the handlers are `LucidiumNet.journalAction` / `realityCheck`). Lucidium
   cells are erased and reused (`Cells.release`, `core.RegionRecycler`) after each dream and also when a plan is never
   dreamed: thrown away at morning, replaced by a new intention (a build still compiling gives its cell back when it
   completes), or taken by someone who couldn't enter it; a sleeper about to dream a dropped plan keeps it until their
   night ends (`Planner.discard`, `Sleep.adopt`). Chunks still generating when a cell was let go are erased before
   the cell is used again. Stale dream entities leave on load.
   Tests: `together_dream_rate_limits` [2], `together_dream_cells_come_back` [2], `together_server_throttle_and_as` [2].
9. Released gate cells are erased and reused while running (`GateService.recycleWhileRunning`), with a per-cell
   generation so stale entities leave on load (`GateRegistry` schema 2, `GateEvents.staleInCell`).
   Test: `together_cells_reused_while_running` [2] (one player's finished gate cell becomes the other's).
10. `/awaken as <player> <any awaken command>` (game masters and the console; a name or selector, never a UUID).
    Test: `together_server_throttle_and_as` [2].
11. RealityCheck and FlagUnfair are throttled (`CorePayloads.flagUnfair`, one per 2 s). Tests:
    `together_dream_rate_limits` [2], `together_server_throttle_and_as` [2].
12. Possession checks a bonded mount's owner (`MobHooks.bondedTo`, `Possession.ownedByAnother`).
    Test: `together_fauna_follow_the_group` [2].
13. Fauna level follows the lowest awakened player within 48 of the spawn. Test: `together_fauna_follow_the_group` [2].
14. The cast-fail sound goes to the caster only. Test: `together_powers_fizzle_is_private` [2].
15. Duty, hearth `Breaks.ACTIVE`, `Projection.PENDING_DEATH`, Hearth offers and invitations are cleared on server stop
    (the hearth module's in one place, `HearthService.forgetPeople`). Tests: `together_hearth_stop_forgets` [3]
    (invitations, askings, offers), `together_veil_stop_forgets_owed_deaths` [2].
16. "Solo" means nobody else was inside during your run (`RunState` schema 2 `company`). Test: `together_gates_solo_means_alone` [2].
17. The Rival's Vael is visible to its own player only (`VaelEntity.broadcastToPlayer`). Test: `together_story_rival_scene_is_private` [2].

Old single-player saves: `together_old_saves_load_the_same` (story, Hearth, Hearth player data, gate run; a party of one).

## Deferred
- **Real parties.** The interim rule (gate run + shared Hearth) stands until the city's guild module installs its own
  `PartyHooks.Access`; `core/api/PartyHooks.java` is kept as the city wrote it so Carillon can replace the rule without
  touching gates, rewards or the Veil.
- **Test reach, not behaviour:** Descent kills share XP through the same `Parties.sharing` as home kills, but no
  GameTest stands in the Descent dimension for it; hearth `Breaks.ACTIVE` and Echo `Duty` are cleared by the same
  `forgetPeople` call the stop test drives, but the test has no gate break or duty Echo to fill them first.
- **Screenshot tours** (the Mac): none of this needs a new look; a two-client LAN session (invite, share the Anchor, a
  party gate, a shared dream) is worth one manual play before release.
