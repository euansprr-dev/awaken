# The whole boss fight, levelled up (plan; nothing here is built)

Euan, 11 Nov 2026, after fighting the Ringer on build 69fee63de with a longsword: the fight was "decent", but the
E-rank sword broke mid-fight, the closing rings are "sometimes a bit jittery" and "you're not exactly sure when the ring
is going to close", the final blow is "too boring" ("maybe like a cinematic blow ... similar to Mortal Kombat"), and a
perfect parry is "a very small ding, and the screen barely moves". "Still challenging, obviously. This is not me saying
I want it to be simpler." For all bosses, not just this one.

The approval page is `docs/boss-experience/plan.html` (built from `plan.src.html` by `build_page.py`). This file is the
long form a builder holds to. Harness: `harness/durability.py`, `harness/fight_read.py`. Tours for the "before"
pictures: `tools/autopilot/boss-before.txt`, `tools/autopilot/boss-finisher-frames.txt`.

## 1. What he needs, and how we will know

- **The job.** Fight a boss of your rank with your rank's gear and come out feeling it was the best five minutes of the
  session.
- **The pain.** Four things broke that feeling in one fight: the weapon vanished, the ring could not be trusted, the
  parry felt small, and the win had no moment.
- **Hard constraints.** No easier fights (no window, health, damage, stance or AI number changes). One side decides and
  a reply never changes what is on screen (`animation-glitch-rules`). No float clocks. No synthetic dings; sounds start
  from Minecraft's own recordings by name, judged on a local listening page. First-person arms stay one straight
  vanilla arm. Player-facing words in plain speech. Multiplayer-safe.
- **Strong result.** A fresh weapon of your rank finishes any boss of that rank several times over. The ring glides at
  one speed and you can say out loud when to press. A parry makes you grin. The last blow is the thing you remember.
- **Unacceptable.** Any change that makes a fight shorter or safer by rule; a ring that lies even once; a cinematic
  that clips into a wall, leaves the camera stuck, or plays differently for the server and the client.

Success is measured (section 6): shares of durability a fight costs, ring-against-blow agreement in his own log,
presses in the band answered as deflects, the simulator's table unchanged, and his own verdict on the pilots.

## 2. What his fight showed (verified)

Sources: his `latest.log`, his save's player file and statistics (read from copies, never committed), the code at
69fee63de, `tools/sim/combat_sim.py`.

| Finding | Evidence | Consequence |
|---|---|---|
| The fight took about 300 landed blows; the sword holds 220. | The first Wildhide Longsword broke after 239 strike moves (statistics: one broken; uses 335). He went to creative for five seconds at 13:47:33 and came back with a new one, which then took 78 wear from 83 more strike moves. The fight ran 13:43:27 to 13:48:37. | A fresh E weapon cannot finish the first Proving at his pace. |
| Every boss costs 90 to 300 blows; E holds 220, D 500. | `harness/durability.py`: E Lords 44 to 81% of a fresh E weapon, the Ringer 107%, the Examiner 47% of a D weapon; C and up 4 to 15%. | The problem is the E and D rows, not the ladder above. |
| A whole E gate is about 500 blows. | His morning: 93 hive creatures and the Regent killed; on-level creatures take a median of 4 blows. | More than two E weapons a gate. |
| Axe-style weapons wear twice as fast. | `ArmsItems` builds them with vanilla's axe preset: `Weapon(2)`, two uses a blow; every other style one. | An E axe lasts 110 blows. |
| Armour wears a point a blow whatever the blow. | Vanilla `max(1, damage / 4)` per piece; the duels were retuned to many light blows. His four Wildhide pieces each stood at 126 worn after one morning; the helmet holds 179. | Armour is one bad fight from gone. |
| A broken weapon is destroyed. | `minecraft:broken` counts it; the stack is removed. | Marks, inscriptions and Lord-forged weapons can be lost to wear. |
| The ring moves in four jumps and never closes. | `FightRunner.step`: the tell's count is sent only when `(t & 3) == 0`. `Reticle.target`: `(tell.ticks() - pt) / tellTotal`, the server's stepped count minus the client's part-tick. The count is cleared at the wind-up's last tick; the blow lands in the 1 to 12 ticks after. | For the Ringer's Swing the ring holds at 16, 12, 8, 4 (trembling by a tick's travel each tick), vanishes a quarter open, and the bell lands 50 to 200 ms later. |
| The same on every creature. | `AbilityRunner.windup`: `(left & 3) == 0`; `MobGoals`: every second tick. | Not a Ringer fault. |
| The light blow's white glint never shows. | `ArmsTells.GLINT_AT` 5: the count is never 5 when it steps 16, 12, 8, 4. | The cue to raise the guard is dead on most kit moves. |
| The right moment is where the ring is not. | Longsword deflect: guard raised 0 to 3 ticks before the blow. The last ring he sees (count 4) is 5 to 8 ticks before it: a press there is a block. | The ring teaches the wrong moment. |
| A deflect's answer is small. | `ArmsPredict.onResult` DEFLECT: hit-stop 100 ms, kick 1.8 degrees, field of view 3.5% for 9 ticks; `ArmsSounds.mix`: an anvil at 0.45 volume pitched up 1.6 over a shield block. The same for a light blow, a heavy one and the one that makes it reel. | "A very small ding, and the screen barely moves." |
| The kill is an ordinary blow. | His last entries are `1 2 3`: a plain third strike. `confirmed()`: 40 ms more freeze, a kill sound, the field of view out 2.5%; then the body's own death clip. | No moment. |
| The pieces of a moment exist. | Every body has `kneel` and `death`; every style has an Execution in both views; `opening.client.OpeningCamera` flies a client camera; the plate already says "Broken! Hold ... to finish it". | The finishing blow is assembly, not invention. |
| Three server stalls in five minutes. | `[fights] slow tick` 51, 88 and 101 ms (the last an autosave). | The body and ring hold for that long each time. |
| His frame rate was 54 a second. | The black box written six minutes later: median 18.4 ms a frame. | 2.7 frames a tick: the tremble is visible. |

Seen on CI (`boss-before`, run 42977120489, frames on consecutive client ticks through one Swing): the ring shows four
sizes only, 377, 317, 266 and 223 px (the formula gives 372, 323, 273, 223), is still at the fourth as the bell comes
across, and is gone in the next frame. The same run's death frames: vanilla's red flash, a two-second fold, vanilla's
white puff.

Not proven: his black box of the fight was overwritten by the next session's six minutes later, so there is no
frame-by-frame record of the ring in his own play. His log says 91 blows were "played at once" by the hands against about 300 landed; whether that is the
server's HIT being held for the blade or a prediction gap on big bodies is not known (stage 0 measures it).

## 3. Directions compared

| | Targeted repair | Structural (recommended) | More ambitious |
|---|---|---|---|
| Gear | Raise E and D uses. | Raise them, one use a blow for every style, armour wears by how hard it is hit, and nothing forged is destroyed: at zero it is Worn out. | No wear in fights at all; wear becomes an edge you keep honed. |
| Ring | Send the count every tick. | The ring reads the body's own clock, closes at one speed onto the moment the blow reaches you, and shows the parry window as a band. | The press carries what the hands saw and the server takes their word. |
| Parry | Louder sound, bigger kick. | Three sizes of clash by what you turned aside, the ring bursting outward, a block that reads as a block. | Slow motion on every parry. |
| Final blow | A longer freeze and a flash on the killing blow. | The boss can only die to a finishing blow: it kneels at zero, and your Execution on it is filmed, slowed and answered by its own ending. | A bespoke authored fatality per boss and weapon (752 pairs). |

Why structural: each targeted repair leaves the fault's cause in place (a count sent every tick still vanishes before
the blow; a longer freeze is still an ordinary strike), and each ambitious option costs more than it returns before he
has played the structural one. The hands' word for a parry is held back as a later step, taken only if stage 0's
numbers ask for it.

## 4. The design

### 4.1 Gear that lasts (`arms`, `armour`, `gear`)
- **Uses.** E 220 to 900, D 500 to 1,300; C, B, A, S unchanged (1,700 to 2,800). Lord-forged weapons follow their
  tier. Armoury-made stacks carry their own `MAX_DAMAGE` (x1.25): re-derived once from the new base when next held.
- **One use a blow** for every style: the axe row sets `Weapon(1)` itself.
- **Armour wears by how hard it is hit:** `ArmorHurtEvent` sets each piece's wear to `damage / 4` with the remainder
  carried on the stack (no floor of 1). E armour's multiplier 13 to 24, D 18 to 28 (C stays diamond's 33).
- **Never destroyed.** Our weapons and armour clamp their own wear at one use left (`IItemExtension.damageItem`) and
  take the `awaken:worn_out` mark: a weapon hits and fills a stance half as hard and still parries; armour protects
  half as well. Icon cracked, blade drawn dull. Tooltip: "Worn out. It hits half as hard until you mend it at an
  anvil." Mended above zero, the mark goes.
- **Said once.** Under 15%: the Hearth's Now list at home reads "Your sword is nearly worn out. Mend it at your anvil."
- Kept: repair by the tier's material at an anvil, the War Anvil, the Armoury's quarter, enchantments.

### 4.2 A ring you can read (`aim`, `arms`, `mobs`)
- **One clock.** `left = contact - ServerPace.now` in ticks, as a double. For a kit body `contact = kitStart +
  windup + hold + 1 + k`, from the move table the client already holds, `k` the first active tick whose capsule reaches
  where you stand (one shared rule both sides call, as `ArmsRules.sweeps`/`predicts`; if none reaches you the ring is
  drawn dim: this one will miss). For any other creature `contact` is fixed when its tell first appears. Later counts
  are never read for time: a tell that ends early fades the ring; a count that rises is a new tell.
- **One speed.** Every ring appears 12 ticks (0.6 s) before contact at 2.15 times the lock ring and closes onto it in a
  straight line of time. A longer wind-up shows the class's flare and glow first and the ring later; a held blow's ring
  simply comes later. No pulsing of its brightness.
- **The band.** The lock ring thickens outward by the distance the ring travels in your weapon's window (3 to 5 ticks;
  the dodge's evade ticks for red and violet). While the ring is inside it, ring and band burn white: a guard raised
  now is a deflect. The hand's white glint fires on the same tick.
- **After.** Deflected: both rings burst outward (4.3). Guard too early (a block): the band dims and "Too early" shows
  under it for half a second. Struck: "Too late". A setting turns the words off.
- **On the boss without a lock.** While a boss fights you its ring shows on it whenever it is on screen.
- **Safety margin.** The band ends one tick before contact, so a press at its inner edge still reaches the server
  first. Stage 0 logs every guard press with the ring's phase and the server's answer.

### 4.3 A parry that feels great (`arms`; built by the weapon-feel session from these numbers)
Server decides, as now; the client plays it on the reply (in single player within a tick).

| | Today | Light blow | Heavy blow | The blow that makes it reel, or breaks its stance |
|---|---|---|---|---|
| Freeze (hands and boss) | 100 ms | 110 | 160 | 200 |
| View kick (pitch / roll) | 1.8 / 1.2 | 2.6 / 1.6 | 4.0 / 2.4 | 4.5 / 2.8 |
| View shake | none | 4 ticks | 6 | 8 |
| Field of view | -3.5%, 9 ticks | -4.5%, 9 | -6%, 11 | -8%, 14 |
| Light | a small spark | sparks x2.5, lit, thrown the way the blow went; a white-gold flash at the meeting | the same, wider | the same, and the frame's edges flash |
| Ring | nothing | bursts outward | bursts | bursts, the plate's stance chunk lands a beat later |
| Sound | shield block + quiet anvil at 1.6 | three recordings by the boss weapon's material, no layer pitched above 1.2 | lower and longer | the heavy one and the body's own voice |

A block keeps its thud and has no flash. Every layer sits behind the existing comfort switches.
Sound candidates (Minecraft's own, by name, auditioned on the local page after the boss's own whoosh): anvil land,
mace smash (air, ground, heavy), iron golem damage, trident hit ground, chain, copper and heavy core hits, the bell
(for bell-armed bodies).

### 4.4 A final blow worth remembering (`mobs`, `arms`, a new `mobs.client.finish`)
- **The last stand.** A boss whose health would reach zero is held at one point and beaten: every move stops, its adds
  fall, its carriers and fields end, a flier is grounded, it kneels (its own `kneel`), it can neither hurt nor be hurt
  by anything but the finishing blow. Plate: gold, "It's beaten. Hold {attack} to finish it." Left alone for 20 s it
  dies by its plain death, so nothing can stall.
- **The finishing blow.** Your Execution on it (or an Execution whose damage would have killed it, marked as the
  finishing blow as it begins). The server commits it once: who, which style, the tick; from then the boss is dead by
  rule and the reward's timers run from that tick. The client plays a fixed timeline on `ServerPace`.
- **The timeline** (cinematic, about 4 s to hand-back):
  0.0 commit: sound ducks, bars slide in, the HUD goes;
  0.2 the view leaves your eyes for a low three-quarter shot of you and the kneeling body (`OpeningCamera`);
  0.2 to 1.5 your body's own finishing move for the weapon's style;
  1.5 the blow: the clip slowed to a third for its last swing, 280 ms freeze, one white frame, the material's impact;
  1.8 to 3.4 its ending: its own `death` and its family's last word (bells fall and toll once; a forge goes dark; glass
  runs its length; a swarm lets go);
  3.4 to 4.0 the view returns to your eyes, bars out, control back.
- **Through your own eyes** (setting, and the fallback): the same beats with no camera move; the straight vanilla arm
  plays the Execution it already has.
- **By weapon.** Sixteen finishing moves, each the style's third-person Execution with a second beat. **By boss.** Six
  families of ending (biped, beast, serpent, flier, machine, growth) with each body's material and palette; the six
  Provings, eight Sovereigns and the Signal's Shadow get authored ones later.
- **Never glitchy.** The server places you on a clear spot in front (none: own-eyes mode). Three candidate shots, each
  tested for a clear path and view every frame; none clear: own-eyes mode. Any interruption (pause, disconnect,
  dimension change, the camera taken) runs the one way back. Durations in ticks. Skippable by holding Sneak from the
  second time you see that boss's ending.
- **With others.** Only the one who struck gets the camera; everyone else sees their body make the move and the boss
  fall, in real time. The striker cannot be hurt or pushed while it plays.

### 4.5 Found in passing
- The black box is one file, overwritten by the next session: keep the last five, named by time (stage 0).
- An autosave stalled his fight for 101 ms: hold the world's autosave while a boss fights, save when it ends.
- Counter-hits (approved 28 Oct, never built) change difficulty: not in this pass.
- 91 blows "played at once" against 300 landed: measure in stage 0 before calling it a fault.

## 5. How difficulty holds
- Nothing a fight is judged by changes: windows, health, damage, stances, minds, phrases.
- The ring shows the true window; it does not widen it. A fight that is read better is played nearer the players it was
  tuned for (parrier 80 to 96% wins, tank at most 10%).
- The finishing blow adds no damage and begins only when the health is gone.
- Gates: the simulator's table for all 15 Lords and 6 Provings is identical before and after (no number it reads
  changes); if his deflect rate on told blows passes 75% over three fights, the answer is the Wardens' shorter tells
  already planned (0.8 s down to 0.5 s), never a blurred ring.

## 6. Build and proof
- **Stage 0, truth (nothing a player sees).** Log lines: ring said tick X, blow landed X+d; guard pressed at ring phase
  R, answered deflect, block or struck; contact to feedback in ms; wear a fight cost each item. Black boxes kept by
  time. The local listening page for the clash and the finishing blow (`tools/art/weapon_listen.py`'s kind).
- **Stage 1, gear.** 4.1. Tests: a fresh rank weapon survives the sim's worst fight three times; an axe costs one a
  blow; nothing of ours breaks; a worn-out weapon halves.
- **Stage 2, the ring.** 4.2 for every creature at once. Tests: the ring's contact equals the server's first hit tick
  on every damaging kit move (all 47 tables); a held blow; a cancelled blow.
- **Stage 3, the clash.** 4.3, with the weapon-feel session.
- **Stage 4, the finishing blow's pilots.** The Ringer, the Hive Regent and the Gearwright Colossus with the Longsword,
  Maul, Twin Blades and Daggers. Filmed on CI from both views, every frame read.
- **He plays stages 1 to 4 once.** Gates: no weapon of his rank under half after a boss; at least 99% of in-band
  presses answered as deflects and ring-to-blow within a tick on 95% of blows, from his own log; his verdict.
- **Stage 5, everyone.** The other 12 styles and 44 bodies by family; authored endings for the Provings.
- Rollback: each part has its own setting or game rule (ring clock, band and words, clash sizes, finishing blows,
  worn-out rule); the old ring and the plain death stay in the code until he has played the pilots.
- Effort, at this project's pace: stages 0 to 4 about one long session; stage 5 another; authored endings a third.
  The camera's shot solver is the part most likely to grow.

## 7. Decisions (the page's numbered list)
See the page; recorded in SPEC when he answers.
