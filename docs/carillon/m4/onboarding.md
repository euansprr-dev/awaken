# Carillon · Onboarding: tips, the Registry walk, the first stretch outside

Added to Phase A by Euan (2026-10-25), in his words: "the starter actually should show you the different commands and
everything. Because otherwise nobody's really gonna know what they can do. And even myself [...] As you go, every core
thing kind of shows you a small tip, like do this to do that. And then maybe when you wake up, you know, it shows you
your K menu [...] you wake up in the infirmary and then the person says, oh, you have to do the assessment there. So you
walk with him [...] here's your hollow band [...] this is to track your progress and to improve your skills over time
[...] you can go into different gates and XYZ, and the more you do this, the more you get stronger as a hunter. [...]
Maybe he also shows you that you have a map [...] oh, you can exit the city by going to the gate here, and then the
camera actually kind of pans out and shows you where to exit the city."

Three parts: one tip system (§1), the Registry as a guided walk (§2), light guidance for the first stretch outside (§3).
The lines are in `story.md` ("The Registry walk"); this file is the staging and the system.

**This reverses one approved rule.** SPEC §17.2 says the Runner's own moves are "found, not told". With this, they are
taught just in time, once, with your real keys, and then the tips get out of the way. Euan's call; the SPEC edit is listed
in §5.

## 1. Tips: one system, just in time

### What the player sees
- A small tip appears the first time a context comes up: running beside a wall, at a ledge, at a gap, sprinting on open
  ground, hanging, falling from a height, near a gate. One line (two at most), with the real keys drawn as keycaps:
  "Sprint along a wall, jump, then press [Space] again to run on it."
- The moment you do the move, the tip glitches out with a soft tick (done, and felt as done). If you walk away from the
  context, it fades.
- It comes back at most a couple of times, then never again. It never shows during a cinematic, over a screen, or while
  a story line is speaking.
- Two looks, one system: before the awakening tips wear the **Runners' marks** (a bronze hairline and paper text, the
  opening's own look, the city teaching you); after it they are **System glass** (`HudLook`, projected when the Holo look
  is on). Reduced motion: no scan-in or glitch, a plain fade.
- One client option turns them off: `tips` in `awaken-client.toml` (on by default), plus a "Reset tips" button beside it
  and a client command, `/awaken tips reset`, for a fresh start.

### Where it draws (checked against the HUD today)
- `SignalLayout` puts the tracker top-left (the Now line, `TrackerHud`), toasts top-right, the card slot right of centre
  below the crosshair, story lines above the hotbar (`STORY_BOTTOM` 72), and keeps the 16 × 16 crosshair box clear.
- The **tip slot** is new and goes into `SignalLayout`: centred, just above the story line's slot, at most 220 wide,
  never inside the crosshair box (a pure function, so `signal_layout_every_gui_size` checks it at every GUI size). A story
  line on screen makes the tip wait; the card slot and the tip slot never overlap (the card is right of centre).
- Drawn by a new `signal.client.TipHud` through `HudLook.card(...)` and `HudLook.text(...)` (so the Holo module's
  projected look applies with no new code there) and registered with the other System layers in `SystemHud`. Keycaps are
  a small glass key (the Runner's eye already draws one; it moves here).

### Rules (pure, in `signal.ui.TipRules`, GameTest-checked)
- One tip at a time; the higher `priority` wins when two contexts arrive together; the other waits.
- A tip appears once its context has held for 6 ticks (no flicker), stays while the context holds (up to `show_for`,
  default 140 ticks), fades over 8 ticks when it ends, glitches out at once when done.
- It retires after the move is done `retire_after_done` times (default 2) or after it has shown `retire_after_shown`
  times (default 3). After the first done it shows its `short` text (just the keys) if it has one.
- `cooldown` between showings of the same tip (default 600 ticks); at least 80 ticks between any two tips.
- Held while any owner holds tips (`hold(owner, true)`): the sky shot, the arrival, the Ritual, the wake, the Registry
  pan, gate cinematics, the Ascent's "remember" scenes, any scripted camera; and for 20 ticks after.
- Never over a screen (the K menu, inventories, the Status reveal), never while a story line speaks.
- Counts live on the client, per person not per world (you learn your controls once): `config/awaken-tips.json`, ids to
  {shown, done}. New tip ids show once even for a veteran.

### Data: one file per tip
Client resources, `assets/awaken/tips/<group>/<name>.json`, reloaded with resource packs (F3+T), so the text is a
one-line edit and a resource pack can restyle or translate it:

```json
{
  "text": "Sprint along a wall, jump, then press {key.jump} again to run on it.",
  "short": "{key.jump} in the air beside a wall",
  "priority": 60,
  "show_for": 140,
  "retire_after_done": 2,
  "retire_after_shown": 3,
  "cooldown": 600,
  "awake": "any"
}
```

- `{key.<mapping>}` is replaced by that key mapping's current binding (`KeyMapping.getTranslatedKeyMessage()`), so a
  player who rebinds jump sees their own key. Vanilla names: `key.forward`, `key.back`, `key.left`, `key.right`,
  `key.jump`, `key.sneak`, `key.sprint`, `key.use`, `key.attack`, `key.inventory`, `key.togglePerspective`; ours:
  `key.awaken.status`, `key.awaken.power_1` … `_4`, `key.awaken.ultimate`, `key.awaken.movement`, `key.awaken.focus`.
  `{key.sprint}×2` draws a double-tap.
- `{power:1}` names the power in slot 1. Unknown placeholders are dropped, never shown raw.
- `awake`: `before`, `after` or `any` (which look, and whether it applies).
- When the Ascent's one-button free-run lands ("hold sprint and the body picks the move"), only these files change:
  the runner tips' `text` becomes the free-run wording; new ids if they should show again to players who retired the old.

### The API (SPEC §12 addition: `dev.awaken.core.api.TipHooks`)

```java
package dev.awaken.core.api;

/** Just-in-time tips (docs/carillon/m4/onboarding.md). Install-once bridge, both sides; the defaults do nothing. */
public final class TipHooks {
    /** Server: a context came up for this player (one small payload; the client decides whether it shows). */
    public static void offer(ServerPlayer player, Identifier tip);

    /** Server: the player did what the tip teaches (counts toward retiring it; fades it if it is showing). */
    public static void done(ServerPlayer player, Identifier tip);

    /** Client access (client code only). */
    public interface Client {
        /** The context holds now. Idempotent: call it every tick the context holds. */
        void offer(Identifier tip);
        /** The player did it. */
        void done(Identifier tip);
        /** Holds every tip back while any owner holds (cinematics, scripted cameras); release with false. */
        void hold(Object owner, boolean held);
        /** Whether this tip can still teach this player (tips on, not retired). Callers skip their probe when false. */
        boolean wants(Identifier tip);
    }

    public static Client client();
    public static void install(Server server, Client client);   // the signal module, once
}
```

`wants()` matters for cost: a detector asks it first and skips its probe for retired tips, so after the first hour the
tips cost nothing. Detectors reuse the probes that already run (the Runner's `ProbeWindow`); no new per-tick allocation.

### Who calls it
| Caller | Tips |
|---|---|
| body (client, `RunnerMoves`, `ClientMoves`) | the Runner tips below; `done` from each move's own start |
| opening (client) | the View key after the question; the Runner's eye hands its key glyph to `offer` (one system for keys); `hold` during the sky shot, arrival and wake; the K menu and power tips in the walk |
| story (client) | `hold` during the Ritual and the Void |
| gates, powers, progression (server) | the first-stretch tips in §3 |
| holo (client) | `done` for `status_open` when the K menu opens |
| the Ascent (both) | its tiers: the line at the Tether Vow, the leap after the Leaper's edge, the wings at a sky core, flight; later the free-run |

The awakening's one toast that lists the body's moves (SPEC §17.2) becomes redundant once tips have taught them; it
turns into a Codex page.

### The Runner tips (the opening's run, and anywhere after until retired)
| Id | Context (client) | Text | Done when |
|---|---|---|---|
| `runner/sprint` | walking forward 40 ticks without sprinting (opening only) | Hold {key.sprint} to run. | a sprint starts |
| `runner/tactical` | sprinting 30 ticks on open ground, tactical sprint ready | Tap {key.sprint}×2 to sprint harder. | a tactical sprint |
| `runner/leap` | sprinting at a roof edge with a landing 2 to 4 away, level or lower | Keep sprinting and jump at the edge to clear the gap. | a landing across a 2+ gap |
| `runner/mantle` | moving toward a ledge 1.1 to 1.6 high | Jump at a low wall to climb onto it. | a mantle |
| `runner/hang` | airborne with a lip ahead near the 1.2 to 2.4 band | Jump at a high ledge to grab it. | a hang |
| `runner/lower` | standing at an edge with a drop of 4+ | Hold {key.sneak} and walk off an edge to hang from it. | a lower |
| `runner/shimmy` | hanging 10 ticks | {key.left} {key.right} to move along · {key.jump} to climb · {key.sneak} to let go | a shimmy, ledge jump or climb-out |
| `runner/ledge_jump` | hanging with a lip within 2.5 above or 3 to a side | {key.jump} to reach up · hold {key.left} or {key.right} and jump to leap across | a ledge jump |
| `runner/wall_run` | sprinting within 1.5 of a runnable wall within 35° of your heading, with a drop or gap ahead | Sprint along a wall, jump, then press {key.jump} again to run on it. | a wall-run |
| `runner/wall_jump` | wall-running with another wall across within 4 | Press {key.jump} on the wall to leap off it. | a wall jump |
| `runner/slide` | sprinting at a gap 1 high within 5 | Press {key.sneak} while sprinting to slide under. | a slide |
| `runner/roll` | falling, 4+ already fallen | Hold {key.sneak} as you land to roll. | a roll |
| `runner/climb` | touching a drainpipe, rope, ivy or ladder | Walk into a drainpipe or ivy to climb it. | 3 blocks climbed |
| `opening/view` | 20 s after the view question | {key.togglePerspective} switches between first and third person. | the view switched |

The Runner's eye stays as it is (after 6 s of hesitation at an edge, the next hold pulses bronze): the eye shows
*where*, the tip shows *how*.

## 2. The Registry as a guided walk

Today (M2): you wake in the infirmary, the officer says "the Registry's across the hall", and the Registry is a stub: a
walk through the atrium, the Lens, the Status reveal, the licence, the band, the first power, the first Directive, "THE
CITY IS YOURS." (`OpeningRegistry` steps, `RegistryScene`). M4 makes it a walk with a person who explains things, ends
on a view of the way out, and teaches the K menu and the map on the way.

### Who walks you
**Bram**, an Association officer: a citizen body (M3's bodies and skins) with a new role, `ESCORT`, used only here.
- He leads by 2 to 4 blocks at walking pace and turns his head to check on you.
- 8+ behind: he stops, turns, says a nudge line, waits. 6+ ahead or off the way: "Wrong way, Runner." once, then keeps
  going. He never teleports while you can see him; if you're 32+ away for 20 s he waits at the next stop and the Now line
  says where ("Bram is waiting at the Registry").
- Each stop's step starts only when you're within 4 of it.
- Party: Bram walks the group; the steps run per player in the same room (the Lens reads each in turn); the pan waits
  until everyone is on the balcony, or 60 s after the first.
- Relog: the step is saved (`OpeningState.step`, as today); Bram reappears at that stop.

### The walk, stop by stop (about 3 minutes, mostly your own pace)
| # | Where | What happens | Waits for |
|---|---|---|---|
| 0 | the infirmary bed | the wake (story.md); Bram: "Up you get … I'll walk you." | you to get up (any move) |
| 1 | infirmary door → atrium | walking lines | you to follow |
| 2 | the contract board | "That's the board …" (the board is there, notices greyed until you're registered) | passing it |
| 3 | the archive door | "A gate inside the walls …" | passing it |
| 4 | the Lens (Registry hall) | stand on the mark; the Assessment Lens reads you: ASSESSING → AWAKENED · rank E (kept), played down: "E. Nothing too interesting." | standing on the mark |
| 5 | | the Status reveal (kept) | the screen closed |
| 6 | the clerk's desk | the licence (kept) | (automatic) |
| 7 | | the Holo band fitted and booting (kept), then Bram's three lines: it tracks you; you improve what you use; gates make you stronger and the Lens moves you up | (automatic) |
| 8 | | "Go on, open it." The tip shows your Status key; Bram waits (30 s, then he says it again once) | the K menu opened |
| 9 | | the panel opens on the Status tab; Bram names the four tabs (Status, Truths, Codex, Gate Map); closing it moves on | the K menu closed |
| 10 | | the first power: the System's offer (kept) | a pick |
| 11 | | the map (below) | (automatic) |
| 12 | | the first Directive (kept) and Bram's "three at most" | (automatic) |
| 13 | the Registry balcony (new, facing north) | "Come and see." Bram leads you out | you on the balcony |
| 14 | | **the pan to the High Gate** (below) | the pan's end |
| 15 | | Bram's last two lines; THE CITY IS YOURS. (kept); control returns | |

### The map, now and later
- **Until the Atlas (M5):** the band's map is the K menu's fourth tab, the Gate Map (gates you've seen). Bram points to
  it ("It's on there, last tab"). If the pack's Xaero's World Map is installed, a tip shows its key (found by scanning the
  key mappings for one whose name contains both "xaero" and "map"; no tip if none is found).
- **With the Atlas:** step 11 opens it (SPEC §16.4: "the Atlas opens with the run revealed"), clouds everywhere but the
  streak you ran this morning, and Bram's second line. The M5 builder slots it in here; nothing else in the walk changes.

### The pan to the gate
- Through `OpeningCamera` (the sky shot's camera: client-only, placed every frame, one `finish(reason)`), so no new
  camera code: from Bram's shoulder the camera rises 6, pulls back over the Terrace's balustrade, glides north along
  High Gate Road and frames the **High Gate** with the wilds and the morning beyond it (about 90 blocks from the
  Association; well inside the camera's 215 and under its 200-above-the-ground limit), holds 2 s with a place caption
  "THE HIGH GATE" in the letterbox, and returns to your eyes. About 9 s. Lens: Carillon's day look. Hold jump to skip.
- Bram's two lines play over it (story.md).
- After control returns, for the first ten minutes (or until you leave the walls), the Now line carries "The High
  Gate · north" with its compass (`NowHooks.Suggestion.at`). That's the only waypoint the opening ever gives.

### Skipping
- Lines never block walking; each step that waits is waiting for something you do.
- Hold {key.jump} for 1 s to skip the walk (offered after you've seen it once on this client, like the sky shot).
- The `awaken:skip_opening` game rule and `/awaken opening skip` skip it as today (you get the licence, the band, a power
  offer and the Directive at once).

## 3. The first stretch outside: light guidance

One tip at a time, the first time each core thing comes up, then out of the way. All server-detected
(`TipHooks.offer(player, id)` from the module that owns the moment; `done` from the same place).

| Id | When | Text | Done when |
|---|---|---|---|
| `first/gate` | the first gate within 48 (gates' Now source already finds the nearest) | A gate. Walk into its core to read its card. | its card seen |
| `first/gate_card` | the first gate card | Rank, when it breaks, what it hides. Yours is E. | the card closed |
| `first/satchel` | the first thing picked up inside a gate | What you find in a gate goes in your Satchel. Leave by a Rest Shrine or the Lord's portal to keep it. | an extraction |
| `first/shrine` | the first Rest Shrine within 6 | {key.use} at the Shrine: leave with your loot, or go deeper. | a shrine used |
| `first/power` | holding a power not yet used, 30 s outside the city | Press {key.awaken.power_1} to use {power:1}. | the first cast |
| `first/level` | the first level with points to spend | Points to spend. {key.awaken.status} to open your band. | points spent |
| `first/now` | the Now line's first suggestion after the walk | The line in the corner is your next good thing to do. | shown twice |
| `first/night` | the first dusk outside the walls | Monsters walk in the dark. The city's lamps keep its streets safe. | shown once |
| `first/fracture` | the first death in a gate | You lost your Satchel. Your gear stays. The Fracture fades with time. | shown once |
| `first/reassessment` | the first "Reassessment available" | You've outgrown E. Take the Reassessment at a Monolith or the Spire. | the trial taken |
| `first/directive` | the first Directive finished | Directives pay when they're done. The next comes in its own time. | shown once |

**Then it gets out of the way:** the `first/*` group retires as a whole once you reach rank D or after your first
three play-days, whichever comes first; from then on only new things teach (the Ascent's tiers, a new kind of gate).

(If Euan approves the Registry hosting the Reassessment in Carillon worlds, `first/reassessment` names it instead. Not
part of this package.)

## 4. Build notes for Phase B

| Part | Module | New or changed |
|---|---|---|
| `core.api.TipHooks` | core | new (SPEC §12) |
| `signal.ui.TipRules` (pure), `signal.client.TipHud`, `TipClient`, the tip slot in `SignalLayout`, the `tips` option, `/awaken tips reset`, `assets/awaken/tips/**` | signal | new |
| Runner detectors | body (client) | small calls in `RunnerMoves` / `ClientMoves`, behind `wants()` |
| `RunnerEye` key glyph → `TipHooks` | opening | changed |
| `WakeShot` lines, `OpeningRegistry` steps (new stops 0–15), Bram (`ESCORT` role), the balcony place, the pan (`OpeningCamera`), the Now suggestion | opening, citizens | changed |
| The Registry balcony | city (`CityLandmarks`, the Association) | new part |
| First-stretch offers | gates, powers, progression | one call each |
| The awakening's moves toast → a Codex page | body | changed |

Tests: `signal_tip_rules` (one GameTest on the pure rules: retire, cooldown, one at a time, holds, priority) and
`opening_registry_walk` (a fake player following, lagging, running ahead, relogging mid-walk; every stop reached). One
tour (`tools/autopilot/`) photographs every Runner tip in the run, each stop of the walk and the pan.

## 5. SPEC changes on approval
- §17.2: "the Runner's own moves are found, not told" → "taught just in time by tips, once, with the player's own keys
  (onboarding.md; Euan, 2026-10-25)".
- §16.4: the Registry beat becomes the guided walk (Bram, the K menu, the map, the pan to the High Gate); the Runner's
  eye's key glyph moves to the tip system.
- §12: `core.api.TipHooks`; the tip slot in `SignalLayout`.
- Client options: `tips` (on).
