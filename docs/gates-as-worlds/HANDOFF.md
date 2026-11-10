# Gates as Worlds: handover to a new account (10 Nov 2026)

You're picking up AWAKEN's biggest open plan from a session on Euan's other account. Read this page, then the files it points to,
before you touch anything. Euan is switching accounts because a new model just came out, and he wants that model to do two things:

1. **Learn what's been built and the bar it's built to**, from the code, the paintings and the shots, not from summaries.
2. **Find a better way.** Build gates faster, at higher quality, in more volume, and push the bar past where it is now, closer to AAA.
   Propose options, including ones that push it to the maximum, with what each costs. Euan picks; then you build.

His words, roughly: take everything built so far and the plan, find how to build these even faster, even higher quality, possibly more
volume and more AAA; learn everything we've been doing at the quality bar, think about how to bring the bar further, propose options
pushing it to the maximum.

So your first job is a plan, not more gates. Follow his planning process in `/Volumes/MEDIA/Awaken/.claude/PLANNING-PROCESS.md`
(19 steps, ends at an approval page published with the Artifact tool). Nothing gets built from your proposal until he says yes.

## Read these first, in this order

| What | Where | Why |
|---|---|---|
| Engineering guide | `CLAUDE.md` (repo root) | Build commands, API rules for Minecraft 26.3, module rules, commit trailer |
| The bar | `docs/gates-as-worlds/BAR.md` | What "good" means, written after Euan rejected a gate. The six things every gate must have, the preview metrics, real-game light |
| The roster | `docs/gates-as-worlds/ROSTER.md` | All 14 E gates and 16 D gates: form, theme, rule, great thing, Lord's place. Who built what |
| The approved plan | `docs/gates-as-worlds/PLAN.md` and `page.html` (the approval page's source; the published page is on the old account) | The 18 decisions Euan approved on 9 Nov |
| The spec | `docs/SPEC.md` from line ~20330, "## 50. Gates as Worlds", especially 50.6 (nothing floats), 50.7 (Realms), 50.8 (the Build Kit and every gate's notes) | Source of truth for how it works |
| Paintings | `docs/gates-as-worlds/img/paint-*.jpg` | Every gate is painted first; the build is judged beside its painting |
| The two bar gates | `src/main/java/dev/awaken/gates/realms/land/AmberReach.java`, `HourCrypt.java` | What good looks like in code: `Heart`, `Spire`, `Plinth`, `growth`, `shade`; `Nave.column/vault/dress`, `Drum`, `GreatBell`, `Hall.dress`, `lay` |
| Euan's memory notes | `/Users/euanspencer/.claude/projects/-Volumes-MEDIA-Awaken/memory/` (index `MEMORY.md`) | Every standing rule and verdict he's given across the whole mod. Gates: `gates-as-worlds-approved.md`, `gates-bar-verdict.md`, `gates-paintings-bar.md`, `visual-quality-bar.md`, `no-synthetic-dings.md`, `vanilla-sounds-by-name.md`, `plain-dialogue.md`, `goals-before-features.md` |

Look at the pictures, not just the words. `run-preview/realm-amber/sheet.png` and `run-preview/realm-hour/sheet.png` are the bar. Real-game
shots from GitHub's runners are in `run-autopilot/screenshots-ci/realm-*/` (all in the worktree below).

## What Euan has said about gates (the verdicts that shaped the bar)

- 9 Nov, first look proofs: "way too boring and flat and vanilla"; "the foot of the comb is literally just one wall"; "I don't want a maze";
  "the two first gates in the world I went to were exactly the same". He wants underground, temples and deserts, a mix of forms, and to
  "massively level up" in a gate.
- 9 Nov, on the paintings and the Amber build: "This looks much better... There's some stuff floating. We need to make sure that never
  happens." Then: "go ahead with the full plan... Make sure every single build... is on par with the examples you just did here, or even
  better." He also asked for a really cool loading screen between the overworld and a gate (built: SPEC 50.5).
- 9 Nov, on time: Amber took 2-3 hours. "It looks FANTASTIC. So we can defo put this amount of effort into each gate... But if we can find
  a way to get same quality for a lot less time, that would be awesome... 88 gates... I don't even know if I would have enough usage."
- 10 Nov, on the first pass of The Vine Seat: "semi-right in composition, but it's still like very blocky... it should never be vanilla
  blocky... there's no atmosphere, there's no environment, there's no nothing that makes this world feel like truly alive." The bar is
  Amber Reach and The Hour Crypt. He allowed two sub-agents for gates only.
- Before that, on creatures in gates: wings disconnected from the body; animations must match Fresh Animations and the rest of our mobs.

## Where everything is

All work lives on this Mac at `/Volumes/MEDIA/Awaken`. Gate work is in three branches; none of the new gates is in `build/awaken-v1` yet.

| Branch | Worktree on this Mac | Pushed as | Holds |
|---|---|---|---|
| `claude/gates-as-worlds-plan-fcb81b` | `/Volumes/MEDIA/Awaken/.claude/worktrees/gates-as-worlds-plan-fcb81b` | same name | Main line: roster, bar, kit, preview, Hour Crypt, Vine Seat, Kingsroot Keep, Saltcomb Flats, this page |
| Builder A `worktree-agent-a8d1c3ce927409cc8` | `.claude/worktrees/agent-a8d1c3ce927409cc8` | `claude/gates-builder-a` | Glowroot Hollow, Sporefall, Wax Galleries first pass |
| Builder B `worktree-agent-a5737058bb2717c16` | `.claude/worktrees/agent-a5737058bb2717c16` | `claude/gates-builder-b` | Cinder Stair, The Long Shaft, The Ash Isles, plus 5 uncommitted files (below) |

`build/awaken-v1` already has Amber Reach, the kit, the preview and the crossing screen (merge `a12d67705`). New gates sit behind the
`gateRealms` switch (`AwakenConfig.GATE_REALMS`, default false) until the E wave is ready.

Builder B was stopped mid-run. Its worktree still has uncommitted, untested edits to `AshIsles.java`, `LongShaft.java`,
`worldgen/biome/realm/ash_sky.json`, `realm-ash_isles.txt` and `realm-long_shaft.txt` (17 lines). It had just started on The Quiet Barrow
(nothing written). Don't delete either builder worktree.

### Every gate, honestly

| # | Gate | State | What's known |
|---|---|---|---|
| 1 | Amber Reach | In build | The bar. Euan has it to play. Golden hashes guard it: seed 1 `50cc4328...`, 2 `8947ec1e...`, 3 `bc407d30...` (kit edits must keep them) |
| 2 | The Hour Crypt | Main line | The bar by design, but too dark in the real game: median brightness ~15 of 255 with Lens, ~5 with Lens off, 98% black in the worst rooms |
| 3 | The Vine Seat | Main line | Remade after Euan's rejection (`bd592235c`); he hasn't seen the remake. Real-game interiors median 24-32, about Amber's hall (19) |
| 10 | Kingsroot Keep | Main line | Walk and float tests green; one real-game walk (run 42675573152) |
| 7 | Saltcomb Flats | Main line | Tests green; real-game walk (run 42696210593) measures well (outdoor medians 81-150). The final round of looks wasn't checked by eye |
| 4 | Glowroot Hollow | Builder A | Tests green; no real-game walk; unseen |
| 9 | Sporefall | Builder A | Tests green; no real-game walk; unseen |
| 11 | The Wax Galleries | Builder A | First pass only. 10 of 11 preview shots flagged PLAIN. `wax_galleries.json` uses creature ids that don't exist (`wax_moth`, `comb_guard`): use `pollen_grazer`, `drone_bee`, `honey_slime`, `amberfly` |
| 5 | Cinder Stair | Builder B | Committed; Builder B's test runs before it stopped passed (which tests, unchecked); unseen |
| 8 | The Long Shaft | Builder B | Committed; unseen |
| 12 | The Ash Isles | Builder B | Committed pass plus the untested edits above |
| 6, 13, 14 | Quiet Barrow, Flooded Ossuary, Broodwarren | Not started | |
| 15-30 | The D wave | Designed in ROSTER, not started | |

Merging the three lines will conflict in the shared registries: `LandPrograms.java` (now `Map.ofEntries`), `RealmLive.wake`,
`RealmState`, `RealmTests`, `RealmLife`, `data/awaken/dimension/realms.json`, lang merges, SPEC 50.8 and ROSTER. Both builder branches
also carry build's ImmersiveVFX merge commits, which build already has.

Who merges into `build/awaken-v1`: on the old account only the orchestrator session did, and nothing else pushed build. Ask Euan who
does that now before you merge or push build. Pushing your own branches to `euansprr-dev/awaken` for CI is fine; never force-push.

## How a gate is made today

The code is under `src/main/java/dev/awaken/gates/realms/`.

- **A Realm** is the dimension `awaken:realms`. `RealmChunkGenerator` asks the gate's `Land` to `paint(v)` each block. A `Land`
  (`land/*.java`) places `site`s (Mouth, Portal, Lord, wardens, village, dens, Waystones, finds), and answers `ground`, `edge`,
  `inHeart`, `biome`, `air()` and `change(moment)`.
- **Its rule** is its own `Realm<Rule>.java` with one line in `RealmLive` (e.g. `RealmToll` for the bell, `RealmSalt` for the salt).
  Rules flip blocks through `change("heart_open")` etc. on a timer, skipping cells someone stands in.
- **The blueprint** is `data/awaken/realms/<id>.json`: line, form, theme, program, biomes, lens, air, life, folk, law, wardens, finds, Lord,
  moods. Biomes are `worldgen/biome/realm/<id>.json` and must be added to `dimension/realms.json`.
- **The kit** (`kit/`): `KitLand`, `Mats` and `Accents` (materials by role), `Piece` with pieces in `kit/piece/` (`Find Hut MouthRing Stall Pavilion
  Pylon Tree Updraft Boulder Lamp` and more), `V` (block view), `N` (noise, hexes), `Build`, `Road`, `River`, `AirPlan` (mist, water, shafts, lights).
- **Materials**: each theme has its own family of 20+ blocks made by `tools/art/specs/realm_<x>.py` (Knell, Fane, amber, salt, Mycel, Ember).
- **Tests**: every gate has `realm_<id>_walks_to_everything` (BFS from the Mouth: step 1, drop 4, swims, climbs) and
  `realm_<id>_nothing_floats` (`check/FloatCheck`), plus `journey_ledger` (every new block needs a recipe, loot or `Sources.declare`).
- **Seeing it**: `tools/preview/preview.sh tools/autopilot/realm-<x>.txt` renders a land's real blocks from its tour's cameras in about a
  minute, no game client, and flags BLOCKED, ALL-SKY, ONE-BLOCK, VANILLA, FLAT, PLAIN, WASHED (metrics in BAR.md). The preview draws no
  darkness. For real-game light, push and run `tools/tour-ci.sh tools/autopilot/realm-<x>.txt 1500 LENS=cinematic RD=28` (20-30 min).

Commands (always through the slot wrapper, the Mac is shared):
`/Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 560 runGameTestServer -Ptests=realm_saltcomb`

## What we learned about speed

Time per gate from git history (start of work to commit, one builder each; noisy but real):

| Gate | Time | Notes |
|---|---|---|
| Amber Reach | 2-3 h of building, more with its rounds | The kit, preview and life came out of it afterwards |
| Kingsroot Keep | ~40 min | Straight after the Vine remake, same theme family |
| Glowroot Hollow, Sporefall | ~45 and ~40 min | New family (Mycel) for the first |
| Cinder Stair | ~75 min | New family (Ember), 275 files with art |
| Saltcomb Flats | ~90 min | Plus a long fix loop |
| Wax Galleries, Ash Isles | ~3 h each | Still not at the bar |
| The Long Shaft | ~6 h | |

Where the time actually goes:
- **Fix loops on floats and reach.** Saltcomb's first test run found 76 floating blocks and unreachable finds. Every piece that carves or
  hangs can strand something, and each round is a two-minute test run plus reading coordinates.
- **Hand-placed detail.** Each gate is 900-1,500 lines of its own Java. The bar's rule "no wall plane wider than five cells without
  something stepping in or out" is met by hand, wall by wall. When a builder runs short, that's what gets skipped, and that's what Euan
  calls "vanilla blocky".
- **Two pictures that disagree.** The preview is fast but has no darkness; the real game is slow to see (CI, 20-30 min). Gates that
  look fine in the preview come out black in the game.
- **Every builder re-learns the bar.** Builders that hadn't seen Amber and the Crypt by eye made first passes that fell short.
- **Rounds that measure, not look.** A metric flag clears without the picture getting better. Euan judges pictures.

## Open problems you inherit

1. **Real-game light.** Invisible light blocks in big rooms and an ambient light floor (`#2a3038`) made no measurable difference to the
   CI shots, and I don't know why. The Hour Crypt is near-black even with Lens off, so Lens isn't the cause. Unchecked: the light levels of
   `crypt_candles` (`dev.awaken.gates.block.WorldBlocks.Candles`), whether CI's `gamma:0.5` hides it, and a GameTest that reads real block
   light in a Realm's rooms.
2. **Lens looks.** Only Amber has its own per-biome Lens look (`assets/awaken/lens/looks_gates.json`). Every other gate falls back to
   `default` in `looks.json`. The blueprint's `sky.lens` field isn't read by anything. Each gate needs its own look; the recipe should say so.
3. **The Lord's places.** Decision 8 (all 41 Lords get a built arena by the eight rules, with `arena_reach_all` and telegraph-contrast
   tests) has no kit yet. Each gate so far built its Lord's room by hand. The three to five great creatures per Land (decision 12) aren't built.
4. **Nothing has been seen in game by Euan** except Amber Reach. Sounds are unheard.
5. **SPEC numbering clash.** Two sections are numbered 50 (Powers in Full Voice and Gates as Worlds).
6. **Shots not yet looked at**: Saltcomb's final real-game walk, Kingsroot, Vine and Hour after the light fixes (folders above).

## Ideas to start from (test them, don't take them on trust)

For speed and volume:
- **A grammar instead of hand detail.** Make depth come by construction: a `Facade` that takes any wall plane and gives it plinth,
  pilasters, recessed panels with stair-cut reveals, cornice, niches and lamps on a rhythm; `Vault`, `Arcade`, `Gallery`, `Buttress`,
  `Tier` pieces. Then "vanilla blocky" can't happen by running out of time. Amber and the Crypt already contain these, inline.
- **Float-proof by construction.** Pieces declare what holds them up; carving can't cut a support; dressing never lands on air. Fix
  loops shrink.
- **A light planner with a real-game check.** Place sources by rule (spacing, contrast, emissive surfaces), and a GameTest that reads
  block and sky light in every room and fails dark ones, so the preview/game gap closes without waiting on CI.
- **Generated look per gate.** Biome colours, fog and the Lens look made from the painting's palette in one step.
- **A gate scaffold.** One spec produces blueprint JSON, biome JSONs, the tour, the two tests, lang lines and the ROSTER/SPEC rows, so
  a builder starts at the interesting part.
- **Judging pictures.** Put the painting, the preview sheet and the real-game sheet side by side for every round, and look at them.
- **Reuse with care.** The 90 older worlds are meant to become Dungeons and inner acts (decision 16). Shared set pieces could carry
  volume, but Euan wants every gate "completely different from the other": any reuse must not show.

For a higher bar:
- **Shapes that aren't cubes**: our own block models for arches, rounded columns, slopes, ribs and broken edges; more of each family.
- **Things that move**: swinging bells and chains, turning gears, curtains, falling water, rising lava, built as display entities or
  animated block entities, not just particles.
- **Lords' places that change during the fight**, by the eight rules, and the great creatures that open the way.
- **Sound beds per gate** from vanilla recordings named in `sounds.json` (no synthetic dings, nothing that crackles).
- **Lens per gate**: fog colour, shafts, haze and exposure tuned to the painting; Far Sight for the far views (decision 17).
- **Arrival moments**: the first view framed like the painting, every time.

## Rules that don't bend

- Never launch the game client (`runClient`) on the Mac without asking Euan. Look tours run on CI (`tools/tour-ci.sh`).
- Every Gradle run and tour through `slot.sh`. Run only your tests while iterating; the full suite once at hand-off.
- Verify every Minecraft/NeoForge API against `docs/api/` and `/Volumes/MEDIA/Awaken/.ref/` before using it (26.3 isn't 1.21).
- Nothing floats, everything is reachable, Amber's golden hashes never change.
- All player-facing text is natural speech. The System is the only guide; never announce a boss ahead. No allies.
- Sounds start from Minecraft's own recordings, named in `sounds.json`. No synthetic dings, no static.
- The repo is public: no Mojang or Fresh Animations files.
- Commits are small, `git add` by path (never `-A`), and end with the co-author line in `CLAUDE.md`. Never bare `git stash`.
- Check every shot for broken builds before showing it to Euan, and say plainly what still falls short.
- Euan's time and usage are the budget. One decisive check, then apply; CI runs the full suite.

## What to hand back

An approval page, by the planning process: what you found (with pictures), where the time and the quality really go, options from
"same approach, tightened" up to "push it to the maximum", each with cost per gate, risk, and what it would look like, and your
recommendation. Show at least one gate rebuilt or partly rebuilt your way as proof, beside Amber and the Crypt, so he can judge with
his eyes. Tell him the moment the page is up.
