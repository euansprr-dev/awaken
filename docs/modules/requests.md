# Anchor requests (hearth module, `hearth/request`)

Plan v2 §4.5: "A System request turns gathering into a goal ('Bring 8 moss hide to the Anchor'), the Vault Hunters
altar pattern." Once a player has a Hearth Anchor, the System asks for one thing at a time (two from C-rank) that they
can really get where they play; they carry it home and the Anchor pays.

Code: `src/main/java/dev/awaken/hearth/request/**` (`Requests` the service, `RequestRules` the pure rules, `RequestDef`
and `RequestPools` the data, `ActiveRequest` and `RequestState` the saved state, `RequestFx` the delivery moment,
`RequestNet`, `RequestCommands`, `RequestContent`, `CoreShardItem`, `RequestTests`, `client/RequestClient` and
`client/RequestSection`). Wired by one line in `HearthModule.init` and one in `HearthService.onAnchorUsed` (the
Anchor's use handler: an owner carrying request items delivers first; otherwise the Anchor's window shows as before).
The client side registers itself (`@EventBusSubscriber`, client only), so nothing in common code refers to it.

## What the player sees

1. **The offer.** A little after the Anchor takes root (90 s, so it isn't lost in "Home exists."), and again each
   play-day and after each delivery, a quiet offer: a toast "Anchor request · 8 Mire Hide", then
   "1 decision waiting · K" in the tracker (`Signal.offerQuietly`). The Status screen's *Waiting for you* and the
   ANCHOR REQUESTS section both have **Take it on** / **Not now**. The decision says what it pays ("about 4 essence ·
   14% of a level · Forge credit") and where to look ("Bog Toads in the swamps drop it"). It stays answerable for the
   play-day; the next day brings a fresh one. Offers wait while you fight, sleep, stand in a gate or are away from the
   overworld.
2. **Gathering.** A taken request is a Now line with progress that counts what's in your pack (inventory and off hand,
   never worn armour): "Mire Hide 3/8 · Anchor ↗ 120 m" (priority 42, an idea), then "Mire Hide 8/8 · bring it home"
   (64) and "· use the Anchor" once you're beside it. The compass points at the Anchor.
3. **Delivery.** Use your Anchor while carrying any of it: what you carry streams out of your hands in an arc into the
   Anchor (the items themselves and embers, 0.7 s), the Anchor flares (a ring of light, a bell over an amethyst
   resonance), then a toast: "Delivered · Mire Hide 3/8 / 5 more and the Anchor pays." Partial delivery counts and is
   remembered. The last of it pays: "Request complete · Mire Hide / +4 essence, 14% of a level, Forge credit 10%." with
   the rest in its rows (shards, an early recipe, the streak, the town's regard). Only what a request still needs is
   taken; extra stays in your pack.
4. **The town notices.** Reputation unlocks small touches: **Known** (6) townsfolk gather to watch a delivery and the
   nearest says something in their voice ("Good haul. The forge won't go hungry." — Oren); **Trusted** (18) at night,
   while you're home, embers rise round the Anchor (the town keeps its lamps lit; only you see them); **Valued** (40)
   +25% essence on every request; **Beloved** (80) each play-day the town leaves bread, essence and sometimes a Power
   Core Shard at the Anchor (you get it the next time you use it). A toast marks each new tier.
5. **Status → ANCHOR REQUESTS**: each request with its icon, what's in the Anchor and in your pack, a progress bar,
   where to look and a **Drop it** button; offers with their buttons; done count, streak and its bonus, Forge credit;
   the town's standing with a bar to the next tier and what it adds.

Ignoring or declining costs nothing. Dropping a taken request costs nothing (a new one comes the next play-day).

## What it asks for: only what you can get

Pools are data: `data/<ns>/awaken/request/<id>.json`, loaded on (re)load like progression's content and sent to every
client (`RequestNet.Pools`, for the hints). 88 kinds ship (E 36, D 55, C 37, B 32, A 17, S 9):

```json
{ "schema": 1, "item": "awaken:mire_hide",          // or "tag": "minecraft:logs" with a "name"
  "count": {"E": 6, "D": 8, "C": 10},                // offered only at these ranks, this many (Sovereign = S)
  "weight": 12, "value": 1.2,                        // pick weight; effort per item (about a minute of play = 1)
  "any_of": ["seen:bog_toad", "seen:mire_mother"],   // at least one must hold, if any are listed
  "requires": [],                                     // all must hold
  "hint": "Bog Toads in the swamps drop it",         // where to look
  "group": "mire" }                                   // never two of one group at once
```

Requirement tokens: `seen:<creature>` (a fauna sighting, `awaken:discovered/creature/<id>`), `killed:<entity>`,
`mined:<block>`, `got:<item>` (picked up or crafted) from vanilla's statistics, `depth:<y>` (the lowest Y you've stood on
in the overworld, tracked by this module), and every progression token (`rank:`, `level:`, `counter:`, `unlock:`,
`hearth`, `path`, powers). Unknown or malformed tokens are unmet. A def whose item doesn't exist is dropped at load; a
tag is checked again when an offer is made.

The kinds: basics anyone can do (logs, wool, string, leather, bone, feathers, wheat: low pay); ores you've mined before
(coal, copper, iron, gold, redstone, lapis, deepslate below Y 0, amethyst, diamonds, emeralds, obsidian, quartz,
netherite scrap); every overworld family's materials once you've sighted the family (Mossback moss and scutes, Lumen
glowstone, Dune Ray membrane, the twelve P2 materials, honeycomb, slime); named beasts' trophies once you've felled the
beast; a Gate Lord's part once you've beaten that Lord (E to A); Descent guardians' parts and strata stone you've
mined; Precursor scrap, dream residue, and vanilla's Nether and End drops once you've been there.

A pick is weighted, never repeats one of the last six, never doubles a group, and is never empty for a new player
(the basics have no requirement). If nothing at all is doable it looks again in five minutes.

## Rewards (scaled to effort)

Effort E = count × value (Mire Hide at E-rank: 6 × 1.2 = 7.2). Streak multiplier m = 1 + 10% per delivery day in a row
after the first, at most +50%.

| Reward | Amount |
|---|---|
| Essence | max(2, E × (0.6 + 0.2 × rank)) × m (× 1.25 when Valued) |
| A share of a level | min(0.4, 0.02 × E) × m, paid through `Progression.worldXp(player, "hearth_request", …)`: its own diminishing-returns budget (XpBudget's module default, 0.25 of a level per ~10 minutes), so a burst of deliveries pays less, like kills and ore |
| Forge credit | +10% (+15% for E ≥ 12), at most 50%: the next temper or Forge upgrade gives that share of its price back (iron, gold, diamonds, essence; never nothing) |
| Power Core Shard | chance min(60%, 4% × E), plus one on streak days 3, 5, 10, 15… Four fuse into a Power Core rolled for your rank (use them) |
| An early recipe | 25%: one recipe the level road would grant within the next 6 levels that you don't know yet |
| Reputation | max(1, E / 2) |

Paid once: a finished request leaves the active list before anything is paid.

## Pacing

One slot, two from C-rank. New requests are owed one per free slot each play-day and one after each delivery (30 s
later). "Not now" spends that offer; the next comes with the next delivery or play-day. Offers are 20 s apart. The
streak counts delivery days and lapses after 3 play-days without one (shown as 0 from the next morning).

## Saved state

`awaken:hearth_requests` attachment (`RequestState`): `schema` (1, always written), active requests (id, target,
name, count, delivered, value, since), offers (nonce, request, day), credits, day, next offer time, streak, last delivery
day, completed, reputation, Forge credit with the last seen temper counter and Forge tier, the last six ids, lowest Y,
the gift day, the nonce. Kept on death, synced to its owner. Older shapes load with defaults for what they lack.
Counter `awaken:requests_done`.

## Forge credit (how it pays without touching the Forge)

The Forge's prices live in `hearth/Upgrades`/`Costs`, outside this package, so credit is paid back rather than taken
off: once a second the module compares `awaken:gear_tempered` and the Hearth's Forge tier with what it saw last; when
either rose while credit stands, it gives back that share of what the temper (the metal of the level now on the held
item) or upgrade cost, with a toast, and the credit resets. A Costs hook would turn it into a true discount (Requests).

## Commands

- `/awaken hearth requests`: your requests, offers, done count, streak, standing, Forge credit.
- Admin `/awaken admin request …`: `anchor` (root an Anchor three blocks ahead, claimed), `offer` (one now, if a slot is
  free), `take` (answer the waiting offer yes), `fill [n]` (carry up to n of what each request needs), `deliver` (as if
  you used your Anchor), `pools` (every kind, ✔ for those you could be asked now, else why not), `reputation <n>`,
  `streak <n>`, `reset`.

## Tests (`RequestTests`)

`hearth_request_pools_valid` (88 kinds, every item/tag resolves, every token is known, every rank has 5+, E and D always
have something with no requirement) · `hearth_request_offers_only_doable` (a new player: only basics, never Mire Hide;
after a Bog Toad sighting Mire Hide comes up; busy groups never doubled; an E-rank Lord slayer is never asked for B-rank
relics; the real player's tokens: sighting, mined, killed, depth, a bad token) · `hearth_request_partial_delivery`
(3 of 8 in, nothing paid; empty-handed nothing; 7 more: only 5 taken, paid; tags match any member) ·
`hearth_request_rewards_once` (a second delivery takes and pays nothing; two slots each paid once) ·
`hearth_request_streak` (the rules; days 1-2-3 give streak 3, +20% and a shard; a gap restarts it; declining costs
nothing) · `hearth_request_state_saved_versioned` (NBT, JSON with its schema, network; an old smaller save loads) ·
`hearth_request_rewards_scale` (effort, rank, caps, Forge credit steps, Valued, standings, slots, refunds) ·
`hearth_request_core_shard_fuses` · isolated `hearth_request_offer_flow_and_now` (with a real Hearth record: offer as a
quiet decision, one slot at E, take it, Now line counts the pack and points home, ready priority, a second slot at C,
a new day withdraws yesterday's offer and owes a fresh one, dropping costs nothing).

Autopilot: `tools/autopilot/requests.txt` (run by the integrator).

## Known gaps

- Not seen in a real client (builders don't run one): the stream, the Status section and the toasts are checked by code
  and the autopilot script.
- Forge credit is a refund noticed within a second of the temper, not a lower price on the offer.
- The Beloved gift arrives when you next use the Anchor (no item is placed in the world).

## SPEC-worthy decisions (for the integrator)

1. New §: Anchor requests (hearth). Slots 1, 2 from C-rank; offers quiet (`offerQuietly`), one play-day ttl; owed per
   free slot each play-day and after each delivery; nothing for ignoring, declining or dropping.
2. Pools `data/*/awaken/request/*.json` (schema 1) with requirement tokens `seen:`, `killed:`, `mined:`, `got:`,
   `depth:` plus progression's.
3. Rewards as the table above; XP source key `hearth_request` through `Progression.worldXp`.
4. Reputation tiers 6/18/40/80 (Known, Trusted, Valued, Beloved) and their touches.
5. Streak: delivery days, lapses after 3 play-days, +10%/day after the first up to +50%, shards on days 3, 5, 10, 15…
6. Power Core Shard item `awaken:core_shard` (four fuse into a core for your rank).
7. Counter `awaken:requests_done`; attachment `awaken:hearth_requests` (schema 1).

## Requests to other modules

- **hearth (Costs/Upgrades)**: a price hook (`Costs.price` consulting a discount source) would make Forge credit a true
  discount shown on the Forge's window instead of a refund.
- **signal**: `Signal.offerQuietly` could take a "decision text" separate from the toast so the tracker line reads
  "1 request waiting · K" for requests.
