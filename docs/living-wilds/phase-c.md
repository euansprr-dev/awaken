# Living Wilds · Phase C build notes (Wayfarers, quests, bounties, rumours)

Working notes for the Phase C builder (SPEC §21.0 decisions 7, 8, 9, 14, 19; PLAN.md §2). Decisions that change
anything land in SPEC §21 in the same commit.

## Engine
- `living.quest.QuestDef` from `data/awaken/awaken/quest/<chain>.json` (schema 1): `id`, `title`, `band` (0..4, the
  region band it opens in), `requires` (progression `Requirements` tokens: `rank:X`, `level:N`, `tier:tether`,
  `flag:...`), `scene` (opening scene kind), `giver` (Wayfarer id), `steps[]`, `relic` (item id), `rewards` (progression
  `Reward` shape plus `renown`), `lines` (keyed text, authored, no em dashes).
- Step: `id`, `objective` {`type`, params}, `text` (Journal line), `lines_start`/`lines_done`, `reward?`, `needs?`
  (an Ascent tier or flag: the step waits and says so).
- Objective types: `talk` (Wayfarer id), `reach` (scene marker within r), `kill` (entity type or fauna family or tag
  kind, count, within the scene's region or anywhere), `collect` (item or tag, count; checked on inventory change,
  every 20 ticks while active), `deliver` (to a Wayfarer, items taken), `escort` (an NPC to a marker; fails if it's
  hurt below 30% → retry), `protect` (survive waves at a scene for N s), `use` (reported by owners through
  `Quests.advance(player, "awaken:use/<what>", detail)`: `ascent:zip`, `ascent:glide`, `veil:enter`, `dream:lucid`,
  `mount:ride`, `power:<family>`), `discover` (a discovery key: wonder, landmark, lair).
- State: attachment `awaken:quests` (schema 1, copyOnDeath, synced to owner): `active` (≤ 5: chain, step, progress,
  scene id), `tracked`, `done` (chain ids), `relics` (claimed). Claim ids `quest/<chain>/<step>` through progression
  so a reward pays once.
- Journal: until the holo tab hook exists, a section in the K menu's Status tab (`StatusSections`); then its own tab.
- Horizon: one `HorizonHooks.Source` per player for the tracked step's target (raised priority).

## Scenes and Wayfarers
- `living.wayfarer.Scenes`: per player one open scene, every 25-40 outdoor minutes (shared rhythm with anomalies: ask
  `world.anomaly` whether one is running; never both), in a region of the chain's band near the player (64-160
  blocks), terrain checks per kind: `cliff` (a face 12+ tall with a 2x2 ledge 6-14 up), `road` (a path or flat open
  run 24 long), `ruin` (a Precursor ruin's forecourt), `lakeshore`, `camp` (flat 9x9). Never in a Hearth radius, a ward,
  the city reserve, or within 24 of a gate.
- Tells from afar (the Hilltop Test): a distress flare (a red column of light for 20 s every 90 s), a caravan's smoke,
  a lantern on a ledge; client-drawn from one record like the gate pillars.
- `Wayfarer` entity (living namespace), drawn with `core.client.skin.SkinnedMobRenderer`; hurt, never killed (Carillon's
  citizen rule); talk by use (right-click) → lines through Signal story lines; accept through a choice card.
- Wayfarer camps: one in the Commons on the High Gate road (Carillon worlds: from `CityBasin.site` + the High Gate's
  bearing; classic: 300-500 from spawn on flat ground), then one per ~2,000 blocks of road; no spawns within 24.

## Bounties
- `living.bounty`: templates `data/awaken/awaken/bounty/*.json` (cull / hunt elite / gather family material / clear a
  camp seen); three notices per player for their level and rank; one taken → a new one next play-day; no expiry.
- Boards: Quartermaster Hale's post outside the High Gate (Carillon city module places the yard; agree first) and the
  Association's board (Carillon's contracts board, deferred: bounties are its first slice). Pay: XP (0.15-0.3 of a
  level), essence, `EconomyHooks.pay` once coin exists; renown via `RenownHooks.witness` at turn-in.

## Rumours
- `living.pulse.Rumours` (lands with Phase D's Pulse, seeded in C): a Wayfarer or Pell's post tells one rumour a
  play-day about a chain's scene, a lair or a happening in a named region; the Atlas region gets a "?" (via
  `AtlasHooks` when it exists).

## Relics (effects through perks)
- Each relic is an item with component `awaken:relic` (chain id, owner UUID). Kept on death (bound): restored on
  respawn like copyOnDeath data. While carried it adds a perk source (`Progression.addPerkSource`): perk ids the owning
  modules read (`awaken:rope_reach` (Ascent), `awaken:mount_sprint`, `awaken:whistle_range` (fauna mounts),
  `awaken:scanner_range` (above), `awaken:dream_lucidity` (lucidium), `awaken:veil_time` (veil), `awaken:flow_drain`
  (Ascent), `awaken:night_ward` (living: hunters keep 8 away from a held Shepherd's Lantern)...). Owners that don't read
  a perk yet get a one-line change each, agreed with their session (Ascent for rope and Flow).

## The first drop (eight chains, levels 1-20)
1 The Last Delivery (Kestrel; with Carillon) · 2 Ten Sheep Short · 3 Moth Lights · 4 Oren's First Steel ·
5 Wren's Rope · 6 The Caravan Road · 7 The Hermit's Horn · 8 Pell's Message. Chain 9 (The Drowned Bell) moves to the
second drop if the Veil's lesson (level 15) isn't reachable in a short test.

## Tests (one per mechanism)
`living_quest_engine` (a data chain: start, steps, a tier-gated step waits, reward once per claim, relog keeps it),
`living_bounty_board` (three notices, refresh, claim once), `living_scene_finder` (a cliff and a road scene in a real
world; never in a Hearth, a ward or the city).
