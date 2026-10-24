# Multiplayer audit (pack plan stage 6, "Together")

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
