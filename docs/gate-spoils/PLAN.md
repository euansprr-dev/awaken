# Gate spoils: nothing the gate gives you is lost, and the Satchel in E

Session `gate-spoils`, branch `claude/gate-spoils-ef58b5`, 2 Nov 2026. Approval page: https://claude.ai/artifact/CbMF6oU8dgX5dJQAFbtA4p (`docs/gate-spoils/page.html`).
Status: **approved by Euan, 2 Nov 2026: "go ahead with the gate loot plan"** (relayed by the orchestrator: all four
decisions yes, as recommended). Building on this branch; SPEC §7 (the Satchel) and §13 (Gates, "Gate spoils") carry the rules.

## 1. What Euan asked

Euan, 2 Nov 2026, while playing: "inventory got full from the gate so a ton of items just got lost, not sure what the
solution to this is at all, but let's find a AAA solution. Also I want to make it so the satchel in a gate is visible
when you open the E inventory so I know what I'm fighting for."

- **The job**: run a gate and come home with everything it gave you.
- **The pain**: loot vanished, silently or nearly so, and the Satchel is invisible unless you dig for the band's
  "View Satchel" button.
- **What good looks like**: no path loses an item except the deliberate ones (dying, retreating through floor 1's
  door after being asked). When something doesn't fit, it is kept and you're told where. Pressing E in a gate shows
  the Satchel beside your pockets: what's in it, its Satchel XP, and what dying would cost.
- **Keep**: the death stake (Hunter loses the Satchel, Story keeps half), server authority, one Satchel per player
  (multiplayer-safe), the band's look.
- **Not this**: a storage system, a bigger inventory for everyday play, changes to bells or boss fights.

## 2. Where the items went (verified)

Reproduced in GameTests, `gates/run/SpoilsTests.java` (`gates_spoils_repro_*`). All three pass today, which means
the loss happens. One trap found on the way: a GameTest mock player is in creative, and creative pockets swallow
whatever doesn't fit, so the test player has to be in survival to see the drop at all.

| # | Path | What happens today | Evidence |
|---|------|--------------------|----------|
| 1 | Coming out (Rest Shrine, the Lord's exit, a gate that collapsed, a relog) with full pockets | Whatever doesn't fit is thrown at your feet at the gate mouth, where it despawns in 5 minutes. The only warning is one row ("Pockets full: N stacks dropped at your feet") in a 7-second card, shown while the gate's other cards are up. When the land at the mouth wasn't loaded, the payout comes up to 5 s after you arrive, when you may already be walking away. A Lord's hoard is added past the 27-slot limit, so a boss clear often brings out 30+ stacks. | `Claims.pay`; repro `gates_spoils_repro_full_pockets` (5 of 5 stacks on the ground, lifespan 6000 ticks) |
| 2 | Walking over loot with a full Satchel (27 slots) | Refused. It stays on the gate's floor and is erased with the gate's cell. The only cue is a toast, "Your Satchel is full." | `Runs.onPickup`; repro `gates_spoils_repro_full_satchel` (the loot still lies in the cell after extracting) |
| 3 | A cache, an elite's roll, the twist rewards (beating your echo, Stealth Halls, the lost spirit, the Hoarder) with a full Satchel | Spills on the floor, then as path 2. The cache's toast still says "N items into your Satchel". | `Interactions.openCache`, `Runs.onKill`, `Runs.satchelOrDrop`, `Twists`; repro `gates_spoils_repro_cache_full` |
| 4 | Gifts handed straight to your pockets inside a gate: a world's keepsake and set piece, a Lord's weapon (Arms spoils), a pattern's shard refund, the first bell's handbell and frame, the Heart-clapper, cooked food and honey from theme blocks | With full pockets they're dropped on the floor with no owner, so the Satchel picks them up. Then they're lost if you die later, or left in the gate if the Satchel is full. A keepsake is given once ever (its flag is set first), so that loss is permanent. Bells stage 2-4 will give bell items through this same path. | `WorldLive.keepsake`, `WorldSets.after`, `ArmsIssue.giveItem` (`placeItemBackInInventory`), `Bells.give`, `ThemeBlocks` |
| 5 | Monster drops that fall into the void, lava or a corner you never walk to (sky islands, chasms, arenas) | Never picked up; gone with the gate. | Read from the code: drops are plain item entities on the floor |
| 6 | Smaller | A claim whose id clashes puts the loot back into a run that then ends (gone). Gear handed back on respawn with full pockets is thrown at your feet. | `Runs.lost`, scripted-gate death, `Runs.restoreGear` |

Kept on purpose: dying (the stake), retreating through floor 1's door (you're asked first), the story's forced
extraction.

**What Euan most likely hit**: path 1, his own words ("inventory got full from the gate"). Path 2 is likely on long
gates too, because 27 slots of mixed drops fill up over a few floors.

## 3. How the best games handle it

- Monster Hunter: what your pouch can't hold goes to your item box.
- Elden Ring: anything past your carry limit goes to your storage chest.
- Lost Ark, Genshin Impact: rewards that don't fit arrive by mail.
- Destiny 2: the Postmaster holds overflow but deletes the oldest once it's full, and that deletion is the part players
  hate.
- Solo Leveling, AWAKEN's source: the System's inventory holds everything, and the hunter takes things out when needed.

The lesson: overflow is kept, shown, and has no hidden limit, and "full" is a moment you see, never a silent loss.

## 4. The plan

### 4.1 The Satchel never refuses (decision 1)
The 27-slot cap goes. Everything a gate gives you goes in: what you walk over, caches, elites, twist rewards, the hoard.
The panel shows it a page at a time. The HUD line reads "Satchel 14 stacks · 340 XP". `Satchel.add` stops checking
`SLOTS`; `satchelOrDrop` and the cache's floor spill disappear because nothing is left over.

### 4.2 Held for you (decision 2)
When you come out, the Satchel goes into your pockets as now (empty slots, then topping up stacks). What doesn't fit
is **held for you**: a list saved with your player (`awaken:gate_held`, schema 1, kept through death). The claim card
says so: "6 stacks didn't fit in your pockets. They're held for you: open your inventory (E) to take them." On login, if
anything is still held, one line says so. No limit and no expiry. You can't put things in, so it isn't a free chest.
Outside a gate the E panel turns into "Held for you": click a stack to take it (as much as fits), or "Take all".

### 4.3 The Satchel in E (decision 3)
A glass panel to the right of the inventory, in the band's look (`HudLook.card`, wells like the holo inventory's, the
Signal's colours). At the top: "SATCHEL", the gate's rank and name, Satchel XP in amber. Then the stacks, with full
tooltips on hover. At the bottom, in plain words, what dying costs here:
- Hunter and Hardline: "If you die in here, you lose all of this."
- Story: "If you die in here, half of it comes back with you."
- The story's first gate: "This first gate is forgiving. You keep all of it even if you die."
- With a Clone Vat ready: "Your Clone Vat will bring it back once today."

**Look only.** Moving Satchel loot into your pockets would dodge the stake (pockets are kept when you die in a gate);
moving pockets into the Satchel only adds risk. So the Satchel can't be clicked, only read. It fades in with the
inventory. With the recipe book open there's no room beside it, so it folds to a slim tab with the count. The band's
"View Satchel" button opens E, and the old Satchel screen goes.

### 4.4 Drops fly to you (decision 4)
A gate monster's drops go into its killer's Satchel the moment it dies, so the void, lava and far corners can't take
them. You still see them: each drop pops out of the monster, hangs a moment (about half a second, nobody can pick it
up) and streaks into you with Minecraft's own pickup flight. In a party the killer gets the drops, as the elite rolls
work now.

### 4.5 Gifts never land on a gate floor
Inside a gate, anything the game drops at your feet for you because your pockets are full (not something you throw
with Q) goes to "Held for you". One listener in the gates module (`ItemTossEvent`, item with no thrower, player in a
run) catches it for every module, so bells and arms need no change. Bell items and the Holo band never go into the
Satchel either: an item tag `awaken:never_in_satchel` (bells' item listed as optional, so it works before stage 2 lands)
sends them to your pockets, or "Held for you" when full.

### 4.6 Small fixes
A refused claim and the scripted gate's death bank put loot in "Held for you", never back into an ending run. Gear
handed back on respawn with full pockets goes to "Held for you".

## 5. How it fits together

- **Server** (gates): `Satchel` (no cap), `Held` (new attachment + codec), `Claims.pay` (overflow to Held),
  `Runs` (drops, pickups, gifts, small fixes), `GateEvents` or `RunEvents` (toss catch, drop flight tick),
  `Hud` (one view payload, sent when the Satchel or Held changes, at most every 10 ticks).
- **Network**: `GatePayloads.SpoilsView(satchel, xp, held, deathRule, title)` replaces `SatchelView`;
  `TakeHeld(index, all)` to the server, which checks the index and item, moves what fits and answers with a new view.
  The view sends at most 10 pages; the saved lists have no cap.
- **Client** (gates.client): `SpoilsPanel` drawn on `ScreenEvent.Render.Post` for `InventoryScreen`, clicks on
  `ScreenEvent.MouseButtonPressed.Pre`, scroll on the mouse wheel. No change to the holo package.
- **Multiplayer**: every list is per player and server-side; a take is validated and done in one tick, so it can't dupe.
- **SPEC**: §7's Satchel line and §13's gates notes rewritten in the same commit as the code. No new items or blocks,
  so no ledger rows; the tag goes in `src/main/merge/gates/`.

## 6. Tests (one per mechanism) and the tour

- `gates_spoils_satchel_never_refuses`: past 27 stacks, walking over loot and a cache both go in (repros 2 and 3
  turned round).
- `gates_spoils_held_for_you`: extracting with full pockets leaves nothing on the ground; Held has it; a take moves it;
  it survives a codec round trip and death (repro 1 turned round).
- `gates_spoils_gifts_never_fall`: in a gate, a drop made for you with full pockets goes to Held; a Q throw still drops.
- `gates_spoils_drops_fly_in`: a gate monster's drops are in its killer's Satchel at once; the flying copy can't be
  picked up and is gone after the flight.
- `gates_spoils_never_in_satchel`: a tagged item picked up in a gate goes to the pockets.
- Update `gates_satchel_death_rules` (it asserted that a full Satchel refuses).
- One CI tour (`tools/tour-ci.sh`) at the MacBook's pixels and GUI scale: E in a gate with a full Satchel (Hunter and
  Story lines), over 30 stacks (pages), recipe book open (the tab), and outside with stacks held. I look at every shot.

## 7. Decisions for Euan

1. **The Satchel never refuses** (no 27 cap; it shows a page at a time). *Recommended.* The other way keeps the cap as
   "the bag is full, go home" pressure, with a clear full moment, but then loot left behind is the one thing that can
   still be lost.
2. **What doesn't fit your pockets is held for you**, taken from the E screen anywhere, no limit, no expiry.
   *Recommended.* The other way sends it to your Hearth's chests (the original plan's line), which needs a Hearth and a
   trip home, and mixes it into your chests.
3. **The Satchel panel is look-only** in a gate. *Recommended*, because taking from it would dodge the death stake.
4. **Monster drops fly into the killer's Satchel** (you still see them pop and fly to you). *Recommended.* The other way
   leaves them on the ground for you to walk over, and drops that fall into the void or lava stay lost.

## 8. Delivery

One stage, in this order: server rules and tests, then the panel, then SPEC, then one CI tour. About half a day at our
pace. Rollback is one revert: saved data only gains the new Held list, and an old build ignores it.

What can't be verified here: how the flight looks at speed in real play, and sound (none added; the pickup's own
sound plays).
