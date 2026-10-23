# System v2: the Signal UI, the Now tracker, the Status screen

Plan v2 §4.6. The owner's complaint: the System's pop-ups were huge (one 260 px window at top centre, 69% of the width
and ~50% of the height at his auto GUI scale of 378×246), covered the crosshair, came in bursts, and never said what
Y and N would do. System v2 replaces the presentation and keeps the server API.

## What the player sees

| Surface | For | Size and place | Behaviour |
|---|---|---|---|
| Toast | information | 160×32 (vanilla toast), top right, through vanilla's ToastManager so it stacks with advancement toasts | Title + one detail line (body's first sentence, else the rows collapsed, else the footer). 5 s × the player's *Notification Time* option. At most 3 of ours on screen. More than two within ~2 s merge into one: "3 new things · K". |
| Choice card | a real decision (any message with an action) | ~170×39–49 at scale 1, right side just below the crosshair box, ≤ 20% of the screen height | The question, an optional detail line, then `[Y] <yes words>` / `[N] <no words>` (real key names from the bindings). One card at a time. Unanswered for 20 s, it folds into the tracker ("1 decision waiting · K") and the Status screen's *Waiting for you*, and stays pending until its ttl. |
| Gate card (panel) | GATE messages without an action | ≤ 200 wide, same slot as the choice card, ≤ 20% of the height | Title in the rank colour, rows label-left/value-right, shortened with "…"; rows that don't fit become "+N more · K". N puts it away. If a choice is up or you're in a fight, it shows as a toast instead. |
| Story line | REVELATION (the Signal's voice) | 1–2 lines centred above the hotbar (above the action bar) | Types out, holds long enough to read, fades; long text pages as successive subtitles. If a card is up and would touch it, it moves into the free span left of the card. A revelation that asks something ("Will you accept?") gets its card **and** its story line. |
| Now tracker | "what could I do now?" | one line top left (below the Descent HUD inside the Descent) | The top suggestion from `NowHooks`, plus `↗ 40 m` when it has a place in your dimension. A second line when decisions are waiting. Nothing when there is nothing. |

Nothing is lost: everything the System says this session is in **Recent messages** at the bottom of the Status tab, in
full (rows a toast dropped, panels cut short, story text, and which answer you chose).

### Keys (the stale-Y bug)
Y and N are drained every client tick. They only count for the card on screen once it is *armed*: 8 ticks in a row with
no Y/N press since it appeared. A press before that (meant for the previous card, or a held/mashed key) restarts the
wait. The key lines are dimmed until the card is armed. Warnings and priority ≥ 9 pre-empt an ordinary card (it comes
back afterwards). The fold timer stops while a screen is open.

### The Status screen (K)
- Narrow screens (panel under 440 px: the owner's 378×246) get the tabs in a row on top; wider ones keep the side column.
- Every section lays out with `signal.client.Ui` (one `build(ui)` for measure, draw and click), so text wraps, rows
  collapse to one column when narrow, and nothing overlaps. Each section only gets the mouse while it is over the content.
- Glossary words (Satchel, Extract, Seal, play-day, Veil, Fracture, Reassessment, Echo, Flow, Kinetic, Truth, Directive,
  Latent, with endings like "Echoes", "Fractured") get a faint underline and a tooltip anywhere in the Status screen.
- Top of the Status tab: **Now** (all three suggestions), **Waiting for you** (pending decisions with a button for each
  answer and the time left), then the spine: level and XP to the next level, points to spend, the way to the next rank
  (or where to take the Reassessment), Fracture, and today's tally.
- Truths and Directives show their reward in plain words ("Reward: +2 stat points, +5% move speed").

## Settings (client config, `awaken-client.toml`)
- `systemStyle` = `V2` (default) | `CLASSIC` (the old window, for rollback; the tracker and Status sections still work).
- `nowTracker` = true.
- `systemScale` 0.75–1.5 (cards, story line, tracker; toasts stay vanilla-sized). Cards shrink below the chosen scale
  when needed to stay under 20% of the screen height and to leave room for the story line.

## Try it
- `/awaken signal demo toast|burst|choice|legacy|story|panel|now|all` (game master): one of each surface. `legacy` is
  an old-style call (footer "[Y] Extract · [N] Stay", no labels) to show the fallback labels and hint stripping.
- `/awaken signal pending`: what is waiting for you. `/awaken signal withdraw <action>` (game master).
- Autopilot: `tools/autopilot/system.txt` (GUI scale 0 and 2; run by the integrator, it opens a window).

## Code
- `signal.ui` (common, no client classes, all GameTested): `SignalLayout` (every rect), `StatusLayout` (Status screen
  frame), `SignalText` (hint stripping, labels, wrap/ellipsize), `ChoiceDeck` (pending decisions and key rules),
  `ToastBurst` (merging), `StoryPager` (subtitles), `Glossary`.
- Server: `PendingChoices` (attachment `awaken:pending_choices`, `schemaVersion` 1, copyOnDeath, ttl in world game time
  so a single-player relog keeps the time left), `NowSync` (every 40 ticks, only when changed), `SignalCommands`.
- Payloads: `signal_show`, `signal_toast`, `signal_answer` (unchanged), new `signal_pending` (the full pending list:
  login, answer, expiry, withdraw) and `signal_now` (dimension + up to 3 suggestions).
- Client: `SystemHud` (V2), `ClassicHud` (old window), `SignalToast`, `TrackerHud`, `NowClient`, `RecentLog`,
  `SignalSections` (Now, Waiting for you, Recent messages), `Ui`/`UiSection` (shared Status layout kit).
- Public API additions (stable, SPEC §12 candidates): `Signal.withdraw(player, action)`, `Signal.isPending(player,
  action)`, `Signal.pending(player)`, `Signal.answer(player, action, accepted)`. `send/toast/onAction` and the
  `SignalWindow` builder are unchanged.

## Call sites that should use `choice(...)` (for the integrator)
Old calls keep working (fallback labels "Accept"/"Not now", hand-written "[Y] …" footers are stripped), but each should
say what its keys do and drop its footer hint. Each ttl must not outlive the handler's own offer.

| File | Today | Suggested |
|---|---|---|
| `gates/run/Interactions.java:128-135` Rest Shrine | footer "[Y] Extract · [N] Stay", `action(ACTION+":extract")` | `choice(ACTION+":extract", "Leave with your loot", last ? "Go down to the Lord" : "Keep going down")`, title "Leave now?" |
| `gates/run/Interactions.java:199-203` Retreat? | footer "[Y] Leave without it · [N] Stay" | `choice(ACTION+":retreat", "Leave without your Satchel", "Stay")` |
| `mobs/Mobs.java:181-187` Broker duel | `footer(duel_footer).action("mobs:duel")` | `choice("mobs:duel", "Accept the duel", "Refuse")` (lang keys), ttl 30 s = the offer |
| `veil/Projection.java:414-419` body under attack | `footer("Return").action("veil:return")` | `choice("veil:return", "Go back to your body", "Stay in the Veil")` (WARNING + priority 9 pre-empts) |
| `progression/Directives.java:75-78` | `action(ACTION+":"+key)` | `choice(..., "Take it on", "Not now")`; a longer ttl is fine (the Directive stays answerable in Status) |
| `descent/DescentTravel.java:249-255` Climb back up? | `action("descent:ascend:"+here)` | `choice(..., "Climb up", "Stay down")` |
| `hearth/Upgrades.java:114, 182, 266` | `w.action(Offers.offer(...))` | `w.choice(Offers.offer(...), "Upgrade the Anchor" / "Upgrade the forge" / "Temper it", "Not now")`, ttl ≤ 30 s (Offers expire at 32 s) |
| `hearth/trial/Trials.java:219` | `action(Offers.offer(sp, "trial", …))` | `choice(..., "Start the trial", "Not now")` |
| `hearth/town/Town.java:306, 320, 330` | rumour / codex / heal offers | `choice(..., "Hear the rumour" / "Read the Codex" / "Get patched up", "Not now")` |
| `story/Prologue.java:188-192` | `action("story:accept_only")` | `choice(ACCEPT_ACTION, "Accept", "Accept anyway")` (already the fallback) and a longer ttl |

Also: `veil/Projection.java:248` sends the toast "[G] speak to the dead · [Y] go back to your body" with hard-coded keys;
use `Component.keybind("key.awaken.focus")` / `("key.awaken.accept")` so rebinding shows the right letter.
`core/Playtest.java:165` (check-in) is now a toast whose second sentence (the commands) only shows in Recent messages;
make it `presentation(PANEL)` or put the commands in the title.

## Known gaps
- Not seen on a real screen yet (builders can't run the client): the autopilot tour is the check.
- Other modules' Status sections are clipped to the content area but don't all wrap: `veil` (fixed 44 px),
  `hearth` (right-aligned values), `gates`/`above` Gate Map lines. Moving them to `UiSection` fixes that.
- `NowHooks.Suggestion` has no dimension: `where` is taken to be in the player's dimension at collection time.
- If vanilla toasts already fill slots 3–5, one of ours can land beside the card slot (vanilla decides slots).
- Recent messages live for the session only (cleared on logout); pending decisions are the saved part.

## SPEC-worthy decisions
1. Any message with an action is a choice card regardless of its presentation; a CHOICE without an action is a toast.
2. A card is armed after 8 ticks without a Y/N press; it folds after 20 s on screen (timer paused while a screen is
   open); 10 ticks between cards. Warnings and priority ≥ 9 pre-empt.
3. Pending decisions persist per player until answered, withdrawn or their ttl (world game time). On login they come
   back folded (tracker + Status), never as cards. At most 12 kept.
4. Toasts are vanilla toasts (160×32, 5 s × notification time), at most 3 of ours; a burst (3+ within ~2 s, or more
   waiting than room) merges into "N new things · K".
5. Card slot: right side, top 4 px below the crosshair box; every card ≤ 20% of screen height; a card never takes so
   much width that a story line beside it gets under 150 px.
6. The Now tracker is synced every 40 ticks only when changed; the client computes compass and distance.
7. `systemStyle`, `nowTracker`, `systemScale` (0.75–1.5) are client settings; toasts don't scale.
