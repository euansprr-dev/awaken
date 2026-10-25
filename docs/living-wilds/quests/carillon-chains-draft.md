# Two Wayfarer chains for Carillon's review (draft 1, 2026-10-25)

Living Wilds Phase C (SPEC §21.0.8). Carillon owns Kestrel's and Bram's voices (docs/carillon/m4/story.md) and the city
spine; these drafts carry them into the wilds. No em dashes in player text. Tone: bright, funny and sincere, then
suddenly brutal. Patent rule: consequences go to world state, never to another NPC's stats.

## 1. The Last Delivery (the Roost's word)

Opens: the first time an awakened E-rank player stands on the Runners' Roost after the Registry walk (Kestrel is there).
Levels 1-8, the Commons. Uses: Runner moves, the first stretch outside. Relic: **Courier's Knot** (tactical sprint
recharges 25% faster). Runners' Guild renown.

| Step | Objective | Lines |
|---|---|---|
| 1 | talk: Kestrel (the Roost) | Kestrel: There you are. You owe me word from the square. Three days late, Runner. · Kestrel: The Association said you died. I said you'd be late to your own funeral. · Kestrel: So? What did you see? |
| 2 | talk: Kestrel | (you tell her; the band shows the gate's light as a memory) · Kestrel: A gate. Under the bells. Right. · Kestrel: Well. You brought it. Late, but you brought it. |
| 3 | talk: Kestrel (she hands over a bag) | Kestrel: One last bag. The camp up the north road has waited a week for its letters. · Kestrel: Out the High Gate. Hale will wave you through. Don't stop for sheep. |
| 4 | reach: `high_gate_yard` (CityHooks.spot) | Hale: Licence? Fine. The Commons bite less than they look. Mostly. |
| 5 | reach: the Wayfarer camp; talk: Ilse | Ilse: Letters! With Kestrel's knot on them. She still ties them like she's angry at the string. · Ilse: Sit a minute. Nobody out here runs anywhere. |
| 6 | talk: Kestrel (back on the Roost) | Kestrel: Delivered? · Kestrel: Good. That was your last bag. You don't run letters anymore. · Kestrel: You run at the things everyone else runs from. Come home when you can. The roof's still yours. |

## 10. Bell Metal (the C-rank spine)

Opens: at rank C, Bram stops the player in the Association (or the Journal offers it after Wren's Rope, whose crate of
bell bronze went missing). Levels 20-45. Uses: the Veil (scouting), stealth, a human fight, Runner moves over the roofs.
Reward: title **Bellward**, the Bellbronze blade recipe (its ring staggers gate creatures). Association renown.

| Step | Objective | Lines |
|---|---|---|
| 1 | talk: Bram (the Association) | Bram: Bell bronze is walking off. Crates on the roads. A bell off the Quay tower last night, clapper and all. · Bram: Wren's crate wasn't the first. Find out where it goes. Quietly, if you can. You can't. Try. |
| 2 | reach: the Unbound camp (a scene in a level 20-45 region) | (a camp of rogue Awakened round a crucible; bronze glowing in the coals) |
| 3 | use: the Veil within 48 of the camp | Signal: Seven. Two awake. One is counting coin. |
| 4 | kill: the camp's Unbound (or drive them off: the heart of the camp falls) | Unbound: Bells keep the gates quiet. Quiet doesn't pay. |
| 5 | collect: the crucible ledger | (the ledger names a buyer in Bellwatch, paid in marks, delivery "under the old bell") |
| 6 | reach: Bellwatch; the fence runs | (the buyer bolts over the roofs: the city's thief route; catch them on the roofs) |
| 7 | reach: under Bellwatch (a cellar landmark: CityHooks spot to agree) | (a melting room under the old town: half a bell, still warm) · Bram: Under the bells. Of course it was. · Bram: The bells are why the walls are enough. Someone wanted them not to be. |
| 8 | talk: Bram | Bram: You'll want to know who paid. So will I. Keep your band on. |

Needs from Carillon for chain 10: a CityHooks spot for the cellar under Bellwatch (e.g. "bellwatch_cellar"), and the
roof route the fence runs (the M3 thief route or the roof graph). The patent rule holds: the camp's fall changes
world state (bell theft stops, the cellar empties), never another NPC's stats.
