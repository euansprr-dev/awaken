# Bells: hand-off note (30 Oct 2026, for a session with no context)

You are taking over the **bells build** for AWAKEN, a NeoForge mod for Minecraft 26.3 (Java 25). Euan (the owner) has
approved the whole bells plan. Stage 1 is built and pushed. **Start with stage 2.** Nothing is uncommitted.

## Where things are
- Worktree: `/Volumes/MEDIA/Awaken/.claude/worktrees/journey-kin-908d47`, branch `claude/bells-early-72e69b`, repo
  `euansprr-dev/awaken`. Always `cd` to this absolute path; the shell's cwd drifts.
- Read first: `CLAUDE.md` (build rules), `docs/director/BELLS.md` (the approved plan, sections 4, 6, 7, 8),
  `docs/SPEC.md` §43.7, §43.14, §43.15 and **§43.16 "Bells you can see"** (stage 1's rules, the source of truth).
- Code: `src/main/java/dev/awaken/bells/` (+ `bells/client/`). Art: `tools/art/specs/bells.py`, `tools/art/specs/audio_bells.py`.
- The session that started this work (the orchestrator): `local_460b18dc-28ec-4e59-8e59-ed73535cf750`. Shared notes:
  `/Users/euanspencer/Library/Application Support/Claude/side-session-notes/local_460b18dc-28ec-4e59-8e59-ed73535cf750/`
  (update `bells-early.md` there when you hand back).

## What Euan approved (his words, 30 Oct)
1. On the bell count and where bells come from: "I don't think we should just randomly place a bell in the world." Bells come
   from gates, dream puzzles, quests and deep underground, never from lying in the open.
2. On the plan: "100 is great. Go ahead with the full plan. just inside of gates, let's not make hidden bells … I don't like
   that you have to like ring something … I think like drops by bosses is probably the best thing, like the boss is
   hiding the bell or something. And then when you kill the boss, that's when maybe you find the bell in his room."

So, **changes to BELLS.md that are not written down yet** (do this first, small doc commit):
- §4.7, §4.8, §4.10 and §4.11 are dropped for gates: no gate bell rooms, no hum to follow inside a gate, no handbell
  "answer", and Bram does not hand out the handbell to find bells.
- Decision 6 (the handbell answering) is rejected.
- Gate bells: **the gate's Lord keeps the bell. When the Lord dies, a bell lies in its arena (the boss room), near where
  it fell.** Party members each lift their own.
- The stage 4 row becomes "Lord bells lie in the boss room", with no bell room, no hum and no handbell answer.
- The `bells_gate_holds`, `bells_hum_cadence` and `bells_handbell_answers` tests are replaced by `bells_lord_keeps`
  (it exists) plus a test that the found bell lies in the arena.

## Hard rules (from Euan and the orchestrator)
- Commit only to `claude/bells-early-72e69b` and push it. Never force-push. Never push or merge `build/awaken-v1`.
- **Never touch `/Volumes/MEDIA/Awaken-play`**: Euan plays from there.
- Keep the Mac light. Every Gradle run goes through the slot script:
  `SLOT_WHO=bells-early /Volumes/MEDIA/Awaken-carillon/tools/carillon/slot.sh build tools/gradle.sh 900 compileJava`
  (run from the worktree). Logs land in `logs/`. To find errors: `ls -t logs/*.log | head -1`, then grep `\.java:[0-9]+: error`.
- GameTests run on CI only. A push to `claude/**` does **not** trigger CI, so start a run yourself:
  `gh workflow run gametests.yml --repo euansprr-dev/awaken --ref claude/bells-early-72e69b`.
  Never `runClient`. Builders run no tours (Euan tests in game).
- No sub-agents. One test per mechanism.
- Verify every 26.3 API in `/Volumes/MEDIA/Awaken/.ref/src` (Minecraft + NeoForge patches), `.ref/neo` (NeoForge API) and
  `.ref/assets`. 26.3 is not 1.21 (`Identifier`, `ValueInput`/`ValueOutput`, `EntityTypes.ZOMBIE`, render states...).
- All player-facing text is plain, natural speech (read it aloud). System lines are in square brackets: "[...]".
- Every new item or block needs a way in: a recipe, loot, or `Sources.declare(...)`; add a row in
  `docs/director/LEDGER.md` (the `journey_ledger` test checks this).
- SPEC first: new rules go into `docs/SPEC.md` §43.16 in the same commit as the code.
- Commits end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- The **tasks-one-list** session owns band pages, the task card and the Hearth page layout. Bells only offers hooks:
  `Shown.register("bells", ...)`, `Destinations.set` / `doneWhen("bell", ...)`, and `core.api.BellHooks` (add `tab(p)` and
  `home(p)` for them later).
- Design rules: design from the goal down; the Hearth asks and beasts do boring work; events are rare; never pre-announce
  bosses; looks are bold; no daily tasks.

## State
- **Stage 1 is merged into `build/awaken-v1` at 8c7de144b.** Its last CI run was green: 41042539142 on 500813d3f.
  Our branch was then fast-forwarded to 8c7de144b, so it also carries the tasks session's first batch and the kin
  test fixes. Start stage 2 from here; no merge is needed first. The orchestrator wants each stage handed back green
  on CI, with the run id.
- Test lesson from stage 1: `makeMockServerPlayerInLevel` puts the player at world spawn. In the full CI batch that's
  far from your test's structure, so if a rule depends on distance, `snapTo` the player next to the thing first.
  (`bells_lord_keeps` failed on CI for this reason; fixed in 500813d3f.)
- To check a failure: `gh run view <id> --repo euansprr-dev/awaken --log-failed | grep -iE "bells_|fail"`.
- Stage 1 delivered:
  - Nothing lies in the open. `BellSites` is retired: old built places are cleared when a player comes within 96, and old
    leads close once, with a line.
  - Every bell sound is mono, so it can be placed in the world.
  - The song comes from `SongPlan`: one bell tolls three times, 2 to 9 bells ring rounds, 10 or more play the tune. A
    missing bell is silence; `bells.gap` is never played.
  - `SongPlayer` plays each bell from `Song.place(no)`.
  - Nothing plays at your feet.
  - The bare fire never sings (`ringsFrom` returns null without a frame).
  - Gate Lords keep swallowed bells: `Bells.swallowed`/`lordFell`. Your first Lord always has the Wren, then about 1 in 3
    (`LORDS_WITHOUT=3`, `LORD_ODDS=3`). This still **rings the bell straight into the Book**; stage 4 makes it a bell
    lying in the room.
  - BellBook is at schema 4, with the `said` set, `once(key)` and `lordsSince`.
  - Tests in `BellTests`: catalog_ring, peals, tower_plans, none_in_the_open, song_arrangement, sounds_placed,
    lord_keeps, hundred_peal, story_start.

## Stage 2: "bells you carry and hang" (designed, not coded; APIs already verified)
Today a bell is only an entry in `BellBook.held`, and `BellWorld.Home.hung` = every bell held by anyone living there.
Make bells real things:

1. **Item `awaken:bell`** (new `BellItem`), `stacksTo(1)`, and a data component `awaken:bell` (int, the bell number).
   Register it like `arms/ArmsContent.java:28-31`:
   `DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID)` and
   `registerComponentType("bell", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT))`.
   - Name: override `getName(ItemStack)` (Item.java:373) to return the catalog name. Tooltip: override
     `appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` (Item.java:361)
     with the number, the rank and what its voice does.
   - Protection, from `IItemExtension`:
     - `canBeHurtBy` returns false, so fire, lava, cactus and explosions can't destroy it.
     - `getEntityLifespan` returns `Integer.MAX_VALUE`.
     - `onEntityItemUpdate`: below `level.getMinY()-40`, give the bell back to its thrower
       (`ItemEntity.getOwner()`), or lift it to spawn.
   - Death: in `LivingDropsEvent`, take bell item entities out of a player's drops, store their numbers in a new
     BellBook list `owed` (the Book is copyOnDeath), and give them back from `Bells.moments()` once the player is alive.
   - `static ItemStack BellItem.of(int no)`.
2. **Hooks.** BellFrameBlock implements `EntityBlock` and gets a new `HooksEntity`:
   - It holds `int[] bells` (-1 for empty) and `UUID[] by`.
   - Save with `out.store(name, codec, value)` and read with `in.read(name, codec)` (ValueOutput/ValueInput).
   - Sync: `getUpdateTag` returns `saveCustomOnly`, and `getUpdatePacket` returns `ClientboundBlockEntityDataPacket.create(this)`.
     Copy the pattern from `arms/PatternStandBlockEntity.java`.
   - The frame has 3 hooks. Stage 3's Bell Beam has 4 and the Great Beam has 1, so write `hooks(BlockState)` generically.
   - When the block goes, `BlockEntity.preRemoveSideEffects(pos, oldState)` drops the bells (it's called from
     LevelChunk.setBlockState) and removes them from the index.
3. **Hanging rules** (new `Hanging` class):
   - **Hang** (use a bell on a hook block):
     - Only inside a Hearth the player belongs to (`HearthService.data(server).containing(pos, dim)`, then
       `h.belongs(uuid)`).
     - No Great Bell on the frame. A Great Bell is `Source.PROVING`: tenor, second, third, fourth, fifth, bourdon.
     - One of each number per home.
     - It needs a free hook: the one nearest the click, else the first free.
     - When it hangs: the item is used up, its note rings once from the hook, and the card reads "Bell #12 · The Nook
       Bell. It's hanging by your fire. Its note is the thirteenth in your song. While it hangs, …". The first bell
       anyone ever hangs schedules the Signal's line (`SIGNAL_ONCE`, the `ONCE` map in Bells; move it out of `ring()`).
   - **Take down**: sneak-use with an empty hand. The hook is chosen by where you click. Whoever hung the bell can take
     it down, and so can the home's owner (`HearthRecord.owner`). Its hole comes back in the song.
   - Plain refusal lines for each failed rule. The bell stays in your hand.
4. **World data.** `BellWorld.Home` goes to schema 3. Today the codec keeps "hung", "frame" and the rest; change it to:
   - Save `placed` (bell number → where it hangs), `far` (bell number → where it hangs, for the six still rung by
     people) and `roost` (the roost bell's pos).
   - `hung` stops being saved and becomes **derived**: `placed ∪ far`, rebuilt after load and after every change.
   - `BellTower.hang()` becomes a sync: it copies online members' far bells (a new BellBook map `far`) into the home and
     rebuilds `hung`, then grows the tower as before (stage 2 still has the self-building belfry).
   - At song time, check each loaded `placed` entry is still on its hook. Drop the ones that aren't.
5. **`Bells.ring()`** still files the bell in the Book.
   - For the **far six** (roost, square, market, harbour, watch, curfew), store where it hangs in Book.far and the
     home's far (city bells use `CityHooks` spots or the player's position; holdfast bells use the bell's pos).
   - Every other bell **gives the item**. Card: "… It's yours. Take it home and hang it."
   - The first bell gives the handbell only (no frame any more). Line: "[A fire and a bell make a home. Make a Bell
     Frame from planks and logs, set it down by your fire and hang the bell on it. The handbell's yours too: it sends
     wild beasts running, and rings the peals your bells make.]"
   - **Frame recipe**: planks and logs only. Today `data/awaken/recipe/bell_frame.json` uses `minecraft:bell`. Change the
     frame's `Sources.declare` in BellModule to match.
6. **The song** sends each bell's place: `BellNet.Song(at, lo, hi, List<Placed>)` already exists. `ringsFrom` is the
   frame, else any placed bell. It returns null when nothing is `placed` (far bells alone never sing).
   `SongPlayer.struck(pos)` should key by (pos, no) so the renderer swings the right bell.
7. **Renderer** (`bells/client/HooksRenderer`): draw each hung bell as its item model, as `arms/client/PatternStandRenderer`
   does (`ItemModelResolver.updateForTopItem`, then `ItemStackRenderState.submit`).
   - On the frame, bells hang at x = 4.5, 8 and 11.5 (model units) under the beam (y 12.5/16), about 0.2 scale, using
     `ItemDisplayContext.NONE`.
   - They swing when struck.
   - Register in `BellClient` with `EntityRenderersEvent.RegisterRenderers`.
   - For bells drawn outside the block, override the render bounds (`IBlockEntityRendererExtension.getRenderBoundingBox`).
8. **Found bells** (new `FoundBellBlock` + `FoundBellEntity`) are how a bell sits where it was won:
   - The block: unbreakable, no loot, `RenderShape.INVISIBLE`, drawn by a renderer, with a HANGING boolean.
   - The entity holds the number, the Book's "where" text, claimants (empty means anyone), who has lifted it, and an
     optional home anchor.
   - Use: `ring()` gives that player the item, then the bell rings clear at the block.
   - A player who already has that bell is told so and gets no second one.
   - The block goes when every claimant has lifted it (or after the first lift when it's anyone's).
9. **Ilse**, in `living/FirstScenes.java`:
   - `ILSE[12]` (COAL) becomes: "Here. A coal from this fire, and this. It's the little bell from over my fire at home.
     I've carried it eleven years and never once rung it. Put the coal down wherever you want to live, and hang the bell
     beside it. A fire keeps you warm. A bell keeps the ground shut. That's all a home is, out there."
   - Insert after it (index 13): "The gates took a lot of the bells. Whatever lives at the bottom of a gate likes to sit
     on what the gate swallowed. If you ever kill one and there's a bell lying there, bring it home."
   - `Bells.told(p)` gives the **Ember bell item** (with `ring`) instead of filing a Remembered lead. Players who already
     had a fire skip line 12, so `told()` also says one line about her bell.
   - The fallback `ring(p,"ember",…)` in `moments()` stays, for players who never met her.
10. **The roost bell (Nook, #12)** replaces "the day a beast came to live with you".
    - Watch for the lesson Lampwing (entity tag `awaken_kin_lesson`). `kin/KinHome.lessonTick` spawns it at the home
      anchor + (12.5, 4, 9.5).
    - Put a hanging FoundBell under a branch of the nearest tree within 24 of it (a leaves block with air under it, 3 or
      more up). With no tree, use a dead post at the trees' edge.
    - Before any beast lives at the home (`KinWorld.get(server).at(anchor).isEmpty()`) it says "[Something's asleep in
      it.]". After that it's the home's to take.
    - If no Lampwing ever came but a beast lives there, place it anyway.
    - Don't place it for a player who already holds the Nook.
11. **Old saves**: bump BellBook to schema 5.
    - In `upgrade()`, give each held bell that isn't one of the far six as an item ("[Your bells are waiting to be hung.
      Use each one on your Bell Frame by your fire.]"). Seed `far` for the far six.
    - Euan's world has the Ember and the Nook and already has a frame and a handbell.
    - `FRAME_TOLD` (in Bells) still explains the frame to old Books.
12. **Book page** (`BellPage`): a bell is "hung" if it's in the home's song, "held" if it's yours but not hung, else
    "lead". Check `client/BellBookSection` for the words it shows for each.
13. **Assets**, from `tools/art/specs/bells.py`. Run it directly: `python3 tools/art/specs/bells.py`.
    - Remove the bronze bell and its chain link from `frame_model()`, and add three small iron hooks at x 4.5, 8 and 11.5.
    - Write `items/bell.json` and `models/item/bell.json`: `bell_shape(8, 15, "#bronze", 1.6)` with `fix_bell_boxes`,
      parent `minecraft:block/block`.
    - Give the found bell a blockstate whose model has just a particle texture.
    - Add item and block names to `src/main/merge/bells/assets/awaken/lang/en_us.json`.
    - If you re-run `python3 tools/art/audio_build.py bell_`, revert its unrelated changes to other modules' merge JSON
      (`git checkout`).
14. **Tests** (in `BellTests.register()`):
    - `bells_hang_rules`: three hooks on the frame, no Great Bell, taking one down brings its hole back.
    - `bells_nothing_from_nowhere`: ring gives an item; a far-six bell doesn't.
    - `bells_song_from_bells`: each placed bell's place is in the song.
    - `bells_roost_bell`.
    - `bells_old_save`.
    - Extend `bells_catalog_ring`.
    - Spawn mobs with `net.minecraft.world.entity.EntityTypes.ZOMBIE`. Read light only after `AwakenTests.awaitLight`.
    - Never wait fixed ticks for chunks.
15. **SPEC §43.16**: add the item, hooks, hanging rules, the found bell, Ilse's bell, the roost bell and old saves.
    **LEDGER**: add rows for bell, found_bell, the frame recipe and the roost bell.

## Stages 3 to 6 (from BELLS.md §4 and §8, with the boss change)
- **3**: Bell Beam (three logs and two iron nuggets; 4 hooks; counts in the song only 8 or more above the fire) and
  Great Beam (three logs, two chains, an iron ingot; 1 hook for Great Bells; 12 or more above the fire).
  - The "frame's full" line points to them.
  - Thatchback Work Plans "Belfry" and "Bell Tower" (the kin works module) replace the self-building belfry and tower.
  - Old self-built towers' bell spots count as beams.
  - The Hearth's needs: tier II asks for "a bell hanging 8 or more above your fire", tier IV for "six rooms and thirty
    bells in your song".
  - Great Bells arrive by the Registry wagon and wait in the yard.
  - `GreatBelfry.ready` becomes: the clapper and the relic are set, and 50 or more bells are in the song. Today it needs
    `stage == 2`.
- **4**: `Bells.lordFell` places a FoundBell (claimants = the party nearby) on the arena floor near the Lord's death spot
  instead of ringing.
  - Get the floor with `GateService.roomFloor(g, floor, room, x, z)`; the arena is a `Room.Type.BOSS` room. The call
    comes from `GateEvents.onMobDeath`.
  - Gate-world theme bells (`GATE_BELLS`) work the same way.
  - If the gate closes before anyone lifts the bell, it goes back to the pool.
  - The Heart-clapper and the Sovereign relic stay as they are.
- **5**: Quests and puzzles.
  - Five village quests: a Carillon rumour, then an empty village, then a letter or keepsake, then a hiding place with a
    muffled hum within 32, then the bell.
  - Six dream puzzles with the Lucidium. They replace `DreamBells`.
  - Veil spirits keep bells.
  - Every other source in `moments()` (Provings, Descent strata, the Above, the Court, raids, stampede, flood, eclipse)
    becomes a bell found at a place, not a bell rung into the Book.
- **6**: Looks and sounds.
  - Bells by rank (size) and family (metal), using `CustomModelData` strings and a `select` on
    `minecraft:custom_model_data` in the item JSON (example: `items/waking_pick.json`).
  - The frame and beams.
  - New sounds: the hang, and the muffled hum. Every bell sound is mono (`Snd(..., mono=True)`).

## Gotchas met so far
- In zsh, `grep --include=*.java` fails with "no matches" when the cwd moved. Use `grep -rn` with absolute paths.
- The only compile error in stage 1 was `EntityType.ZOMBIE`; it's `EntityTypes.ZOMBIE` in 26.3.
- Tours `tools/autopilot/journey-6.txt` and `journey-7.txt` use `admin bells tower`, `song`, `belfry` and `ilse`. Keep
  those commands working (`BellCommands`: give | tower | song | belfry | ilse | deep | page).
- The kin module calls into bells: `KinHelp` calls `Bells.found` and `Bells.sung`, which now return false, and
  `KinApi.clapper(p)` gives the woken clapper when a Lord kept a bell.

## When you finish a stage
Compile through slot.sh, commit and push, then dispatch CI. Then update `bells-early.md` in the shared notes and tell
the orchestrator in one message. Lead with the commit hashes, then what was built, then what's unverified (nobody has
seen any of it in game).
