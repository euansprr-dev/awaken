# pack/wilds-base: hand-off (cloud session, 2026-10-24)

The Wilds' foundations from docs/plan-ascent.html §6: everything that doesn't wait on the movement tiers. New module
`dev.awaken.wilds`; three small `core.api` hooks; the Horizon line in `signal`; content data; telemetry in
`progression`. The rules are in SPEC §17 "The Ascent" › "The Wilds". Built from `origin/build/awaken-v1` (8007f43).

## Done
- **Precursor wards** (`wilds.ward`, config `features.wards`). Any structure in `#awaken:warded` (vaults, the spawn
  vault, ocean labs, monoliths) or `#awaken:warded_sky` (sky platforms, reaching 48 further down) has a ward: its box
  grown by 12. Inside it, outside every Hearth's radius: a block a survival player places is marked (threads of light,
  a glassy tick) and crumbles 3 s later into grains of its own colour with the Signal's light rising out of it, and
  the item goes back to them. `#awaken:ward_sealed` blocks (every Precursor block plus the vault's deepslate and
  blackstone dressing) refuse picks, explosions and pistons, so a rank door can't be dug around (AUDIT: the plating
  was mineable by any pickaxe). Own particles (`ward_grain`, `ward_spark`; tools/art/specs/wilds.py) and sounds
  (`wilds.ward_mark/crumble/hold`; tools/art/specs/audio_wilds.py), nothing vanilla.
  - Hooks for later sites: a new structure (Tether Spires) only joins the tag; a site placed by code (hero-tree
    caches) registers a `core.api.WardHooks.Source`. Existing ruins get wards at once (the saved structure start).
- **How you arrived** (`wilds.arrival`, behind `core.api.ArrivalHooks`). The server samples every player every tick
  into 80 quarter-second slots (20 s): still, walk, sprint, swim, climb, ride, airborne, a power's move (powers reports
  its movement casts and running Flight/Hover), the Ascent (for the tiers to report), built (standing on or climbing a
  block they placed in the last 10 minutes). Entering a ward freezes the verdict for the visit, so tower, wait 20 s,
  open the chest still reads as a tower. Loot condition `awaken:arrived_by_movement` gates each site's best roll;
  `ArrivalHooks.byMovement` is the call for route records and Flow XP when they exist.
- **The Horizon line** (`core.api.HorizonHooks`, `signal.HorizonSync`, `TrackerHud`). A second card under the Now
  tracker in the Horizon's sea-green, through `HudLook`: the nearest site you can reach, and for 15 s in every 5 minutes
  a tease of one you can't ("Sky Platform · needs Wings", dimmer, the need in amber). Landmarks moved off the Now
  tracker to it, so gates, requests and unspent points no longer hide them, and an unreachable sky platform no longer
  holds the slot. `Reach` answers today: the Flight power (powers) and Jet Boots with essence (above). Client setting
  `horizonLine`.
- **Discovery that pays.** Biome discoveries (first steps in each vanilla-dimension biome, 0.1 of a level, a toast,
  `awaken:biomes_discovered`). The ten idle counters (plus the new `awaken:lairs_heard` and the biome count) feed 5
  Truths, 11 Titles and 11 exploration directives (SPEC §17 lists them). Sky platforms have their own loot table
  (things that fly, fall slowly or fold distance; best rolls: 2 Power Core Shards, then a 1000-essence cell or Jet
  Boots); vault, monolith and ocean tables gain a best roll of 1 shard.
- **XP telemetry.** `Progression.grantXp` now records every grant in `awaken:xp_ledger` by `XpSource` and by world-XP
  key; `/awaken xp` prints the split, and playtests log it at logout (`xp_sources`).
- **Tools:** `/awaken wilds why` (ward, last 20 s, verdict, Horizon), GM `/awaken wilds place|strike` for tours.
- **GameTests** (one per mechanism): `wilds_ward_rules`, `wilds_arrival_history`, `wilds_discovery_pays`,
  `landmark_horizon_line` (replaces `landmark_now_line_gating`), the Horizon box in the System layout test.

## Left for the movement session (by design)
- Report the tiers' states: `ArrivalHooks.report(player, Way.ASCENT, ticks)` from the state machine each tick a swing,
  leap, glide or flight runs.
- Answer `HorizonHooks.addReach((p, need) -> need.equals(HorizonHooks.WINGS) && <has a way up>)` for Tether+Leaper and
  Wings; if the first sky platform should read "needs Leaper" instead, change its need in `LandmarkHorizon`.
- Spires, rebuilt sky platforms, hero-tree routes: join `#awaken:warded` / register a `WardHooks.Source`; put route
  records and Flow XP behind `ArrivalHooks.byMovement`.

## Tours to shoot on the Mac (none run here: they need the GPU)
- `tools/autopilot/wilds.txt` (light, about 7 minutes): the Horizon card for a vault (both headings), the ward's mark,
  the crumble in three frames, `/awaken wilds why`, the vault wall holding, and a sky platform's tease (one of twelve
  shots 15 s apart lands in the tease window). Check: the grains read as the block's own colour, the light as the
  System's blue, nothing looks vanilla; the Horizon card sits flush under the Now card in both the flat and the holo look.
- Listen: `wilds_ward_mark`, `wilds_ward_crumble`, `wilds_ward_hold` in game (they were judged only by their numbers:
  RMS -23.0 / -21.2 / -19.5 dB, true peak ≤ -1.7 dB).

## CI
- VM, one full run at the end: every test passed except `wilds_arrival_history`, whose walk passed back over its own
  tower block (a test bug: the rule was right). Fixed; the wilds, Horizon and System layout tests then passed (16/16).
- GitHub Actions: the GameTests workflow on `pack/wilds-base` (run id in the hand-off message to the pack session).
