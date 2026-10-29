# AWAKEN: the game director's report

Written 29 Oct 2026 by the game-director session for Euan, from the build at `732f8a05` on `build/awaken-v1`.
Nothing here is approved or built. Euan picks; each pick then gets its own plan through `.claude/PLANNING-PROCESS.md`.

The brief, in Euan's words: track everything from start to finish, "especially once you actually build your hearth",
look at what people love in the best packs and games, find the gaps, and invent about twenty big things you can
actually do, "custom solutions that would make it even better and more in line with what we're doing".

---------------------------------------------------------------------------------------------------------------------

## In one page

**What I found.**
1. **Nobody has played past hour four.** Euan's longest world (`Awaken-play/run-pack/saves/New World (7)`) is 3.4 hours:
   level 11, still E-rank, one D trial failed on day 10, a Hearth with Oren's cottage, three gate themes met, one Lord
   (Mother of Spores), no Truths declared, no Path, never in the Descent. His fifteen other worlds are all under 40
   minutes. Everything after hour four exists as code, tests and tours only.
2. **The depth is all at the front.** Lines of Java by module: gates 47,520 · living 29,931 · mobs 26,528 · hearth 26,462
   · body 26,242 · powers 25,259 · village 24,488. The end game: descent 6,840 · story 6,319 · veil 6,289 · lucidium
   7,738 · above 7,879, of which the Halo is 454. Those were written on day one (23 Oct) and barely grown since.
3. **The end game is a list, not a place.** The Hollow Court is "the next undefeated Sovereign spawns near an S-rank
   player in stratum 9" with a two-line intro and a one-line thanks (`story/Court.java`, SPEC §13). New Game+ is +30%
   health and +15% damage per tier (`story/Ascension.java`). After the Seed, nothing new opens except the Endless.
4. **The Hearth asks for building and gives little back to the rest of the game.** It reads every room on six readings,
   grades it, names it, and settlers walk in on the road (§28.2, §28.8): that's genuinely good. But nothing threatens
   it (vanilla night spawns unchanged; only tier-4 camps raid within 800, `living/camp/Camps.java`), the core loop
   (gates) never touches it except trophies (+1% damage a boss), and after Tier V it has no further goal.
5. **The Solo Leveling fantasy has the smallest system.** Echoes, the shadow army, are 1,795 lines: a bind on B, a
   field cap of 3 + SPI/6, five work pads, and the creature's own body with a violet outline
   (`hearth/client/HearthClient.java:29`). No generals, no growth you can see, no command, no rise moment.
6. **The pack adds no content.** Every mod in `pack/mods.json` is performance, look, sound or quality of life. No
   automation, no storage, no building palette, no cooking or farming depth, no vehicles. All variety has to come from
   Awaken, and today the only "making" thread after the house is thirteen Above machines with no logistics.
7. **No group content and no guild module.** The Together section waits "until the guild module installs real
   parties" (SPEC Together); a party is whoever is in your gate run or shares your Hearth.

**My top five** (reasons in §4): **Tide Nights** (the home under siege), **The Answering** (your Echo legion),
**The Eight Domains** (the Hollow Court as places), **Shaping Hands** (building with your powers), **After the Seed**
(each ending opens its own end game).

**For Euan's next playthrough** (the hours he actually plays): **Shaping Hands**, **Tide Nights** (its First Night
lands on the first evening), and **The Company** (a fellow rookie walks out of the Registry with you). Cheap add-on
with a big feel: **The Hall of Moments** (his Mother of Spores kill standing in his Hearth as a monument).

---------------------------------------------------------------------------------------------------------------------

## 1. The journey as it really is

### How I read it
- SPEC (§1–§41, read by section intros, "as built" notes and every "Not built / Left" line), the earlier audits
  (`docs/progression/audit-journey.md`, `docs/world-depth/audit-hearth.md`), and the code where a claim needed it.
- Pacing from `tools/sim/pacing_results.json` (balanced player): level 5 at ~1 h, 10 at ~4 h, 25 at ~17 h, 50 at
  ~56 h, 135 at ~101 h. Ranks need level D 10, C 25, B 45, A 70, S 100, Sovereign 135 (§2), so roughly D ~4 h,
  C ~17 h, B ~45 h, A ~70 h, S ~88 h, Sovereign ~100 h at the default story length.
- Euan's real saves (read only, his player data decoded with a small NBT reader in my scratchpad).
- No tours, no GameTests run: the question was what the game is, not whether it passes.

### Hour by hour

| When | Rank · level | What you do | What you build | What you chase | How it feels | Where it runs thin |
|---|---|---|---|---|---|---|
| 0:00–0:20 | none → E 2 | The opening: card, sky flight over Carillon, the bell rings wrong, the rooftop run, the gate in Choir Square, death, the Void, "Will you accept?", the infirmary, the Registry, the licence, the band, the first power | nothing | the light column | Strong. Cinematic, the movement sells itself | The most-replayed 20 minutes of the game (fifteen worlds) |
| 0:20–1:00 | E 2–5 | The Last Delivery out of the High Gate to the Wayfarer camp; first rift; first creatures and biomes (Finds); first E gate rolled on purpose (`gates/Director.java:203` `firstGate`); Ilse gives the Hearth Ember | a Hearthfire (radius 24), a first shelter | the task card, the gate pillar, level 3 Dash, level 4 Core | Guided now (§27 fixed the "so what's next?" gap). Busy with toasts | Alone. Nobody walks with you. Directives still easy to ignore (Euan accepted none) |
| 1–4 h | E 5–11 | E gates floor by floor; the first Lord; the Lord's Heart makes the Anchor (Tier I); Oren arrives and builds the cottage over a day; rooms read and graded; Truths offered at level 5; meditation, first dream, first Veil | rooms for the reading: Bedroom, Kitchen, Sanctuary, Forge | the D Reassessment at level 10 | The Hearth is the warmest part of the game. Building finally "counts" | The D trial is a wall (Euan failed it and stopped). Building is vanilla block by block. Nothing at home is at stake |
| 4–17 h | D → C | D gates; the Tether at a vault door (~5 h); the Leaper (~8–12 h); villages and their troubles; roads and wayhouses; Arms' forged weapons and patterns; the Singing Deep below y 0; Descent strata 1–5; settlers you've helped move in; regions by level | Tier II–III Anchor, Forge II, Stable, Hall, Bell tower, Lookout; the Mine head | C-rank, the Second Awakening and a Path; better gear tiers (D iron, C diamond) | The richest stretch on paper: many systems open at once | Unplayed by anyone. The loop is gate → level → rank → gate. The non-combat verbs (mining, dreams, Veil) don't feed each other |
| 17–45 h | C → B | C/B gates; Wings from a sky core (~20–25 h); strata 6–7; Vael's B beat (a Red Gate); the Observatory's Halo line at B; town tier, up to 8 settlers | Tier IV; machines from Above (13) | B-rank, then A | Flight changes everything about the overworld | Flight shrinks the world and nothing new is built for the sky. Above/Halo is 7,879 lines (Halo 454). The Hearth has done everything it does |
| 45–100 h | B → S → Sovereign | A/S gates; Skyborn at A; stratum 8–9; the eight Sovereigns spawned one by one near you in stratum 9; level 135 | Tier V (radius 80) | the Seed | Should be the peak | The thinnest part of the game. Eight fights in one place with a line each. The story bible is 73 lines; between Rival beats there is no story |
| 100 h + | Sovereign | Three endings (Become, Break, Wake); New Game+ Ascension; the Endless | nothing new | nothing | Should be a new beginning | NG+ is a number bump. "Break" makes gates calm and stops there. The Endless is cells with stacking rules |

### Where it runs thin (the evidence)

**T1. The first half hour is polished, the rest is untested by a human.** The opening has had at least fifteen
restarts (save folder dates 23–29 Oct). The longest run ended at level 11 / E with "reassessment_ready" and a failed
trial (`hearth_player.trial_fail_day: 10`). That's the real "where you run out of reasons to go on": a hard trial,
then nothing pulling past it but more E gates.

**T2. Variety runs out into one loop.** From 4 h on, every reason to go on routes through gate XP and rank. The
non-combat systems exist but each stands alone: mining (§34) feeds Arms' metals, dreams (Lucidium) feed lucidity,
the Veil feeds sight and possession, building feeds the Hearth's reading. None of them feeds the others, and none
gives a reason to go back to the Hearth with something to do there.

**T3. What the Hearth gives once it's built, and what it doesn't.**
Gives: respawn, rest heals Fracture, Rested (8 + Comfort minutes of energy and craft bonus), calm land, no gates or
world events on your land, break warnings, room effects (home-cooked food ×1.5 effects, the Hall's shared meal, the
Stable's whistle range, the Lookout's reveal, the Bell tower's warning, the Workshop's essence discount, the Trophy
Hall's +1% damage a Lord), settlers who walk in with a day of their own (meals, work, the fire in the evening),
Oren's tempers and commissions, Hettie's harvests, the miner's daily haul, sister homes from Tier III (§28.2, §28.8).
Doesn't: any threat that makes the walls matter; any building help (every block by hand; building XP 0.05% a block,
§4.3); any big project after Tier V; any link to gates beyond trophies; any reason for the Echoes to live there
beyond five pads; any stage for what you've done (the Trophy Hall is a stat); any change of look as it tiers up
beyond the brazier (§28.11).

**T4. Systems that don't talk.** Echoes ↔ gates (bound, then mostly parked); Lucidium ↔ building (the Day Journal
captures structure snapshots of your best moments, `lucidium/journal/DayJournal.java`, and they're only used to build
dreams); Veil possession (C) ↔ the Wilds' creatures (wear one, then nothing); the Descent ↔ the Hearth (no camps, no
road down, Strain on every climb); Carillon ↔ the mid game (after the Registry the city is a place you pass through);
the Architect power family ↔ building (hard-light combat walls that vanish: `powers/PowerType.java:58-144`).

**T5. The end game.** As point 3 of the summary. Also: the Sovereigns have no place. Strata 8–10 are terrain plus a
rule each (`descent/world/Terrain.java` is 1,100 lines for all of them). The Seed's finale exists; what comes after
is a numbers tier.

**T6. Alone.** In single player you're alone outside the walls except Wayfarers and settlers. In multiplayer there's no
guild, no group content designed for 3–5 people, no world-scale event, no asynchronous layer (messages, ghosts).

### Promised in the plans but not really there
- Living Wilds Phase F: 10 of 16 wonder kinds, treasure maps, the three legendary beasts (Old Greymane, Hush, the Glass
  Wyrm), 6 of the 11 chains and all 19 happenings of Build 6 (§21, "Not built, and why").
- Living Wilds Phase 3: parts and trophies by land, richer game, first-step discoveries (§21).
- Between the Gates: festivals (village and the Hall's), a hamlet growing into a town, caravans' own trade at your
  Hearth, travellers on foot, horses for hire, the Deep Mine, settlers sleeping visibly, the grass tint on your land
  (§28.6–28.9 "Left").
- The guild module (Together, §PartyHooks: "until the guild module installs real parties").
- The Workshop room does one thing (×0.75 machine essence); the Halo is a 454-line station; the Endless is cells.
- The Hollow Court as a court: SPEC §8 lists "the Seed (finale)" as a stratum rule; the Sovereigns live nowhere.
- Previously flagged and now fixed (checked): TipHooks is installed (`signal/SignalModule.java:40`); the first gate is
  placed on purpose (`gates/Director.java:233` `firstGate`); the Study's and Greenhouse's effects are wired (§28.1).

### What the pack's other mods add, and what they leave missing
`pack/mods.json`: Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, MoreCulling, Dynamic FPS, Distant
Horizons, Tectonic, Player Animation Library, Bendable Cuboids, Camera Overhaul, Shoulder Surfing, Not Enough
Animations, 3D Skin Layers, EMF/ETF + Fresh Animations, Sound Physics Remastered, AmbientSounds, JEI, Jade,
AppleSkin, Mouse Tweaks, Controlling, e4mc, Simple Voice Chat, Iris, spark, Tread Lightly, Immersive Thunder.
They make the world look, sound and run better. None adds a thing to do. Missing entirely, and what the best packs
lean on: making machines (Create, GregTech), storage and logistics, a big building palette (Chipped, Macaw's),
cooking and farming depth (Farmer's Delight), vehicles and travel builds, companions, guilds/claims. The design
choice to keep Awaken self-contained is right; it just means each of those holes is Awaken's to fill or leave.

---------------------------------------------------------------------------------------------------------------------

## 2. What players love (research notes)

Search results were thin on Reddit (blocked to the crawler) and heavy on wikis and press, so each note pairs a
sourced fact with the feeling I take from it. I've written what to learn, never what to copy.

**Valheim.** Bosses gate the next biome, and base raids scale with the bosses you've killed; reviewers praise "the
anticipation and buildup before each boss" and never feeling lost. Comfort lengthens the Rested buff, which is what
makes decorating gameplay instead of a distraction ("it tricks you into slowly building not just a base, but a
home", Shacknews). Some players hate raids interrupting builds. *Learn:* threat at home is what makes walls matter;
warn first, never ambush a builder. Awaken already has Comfort and Rested; it lacks the threat.
Sources: [Shacknews 1.0 review](https://www.shacknews.com/article/150866/valheim-10-review-worth-the-wait),
[PC Gamer comfort](https://pcgamer.com/increase-valheim-comfort-level-max),
[PC Gamer biome order](https://www.pcgamer.com/games/survival-crafting/valheim-biome-order/),
[PC Gamer difficulty curve](https://www.pcgamer.com/valheim-difficulty-curve/),
[News-Miner on comfort](https://www.newsminer.com/features/latitude_65/great_indoors/exploration-has-never-been-as-good-as-it-is-in-valheim/article_14df1f30-822a-11eb-90c0-4b167eb9e37f.html).

**Enshrouded.** The Flame Altar grows your buildable land and your time in the deadly Shroud; you rescue craftspeople
from vaults and they need an enclosed building to move in; a construction hammer places big pieces at once.
*Learn:* rescue → resident → new craft is the loop that makes a base feel alive (Awaken's settlers do this). A tool
that builds big is what keeps building fun past the first house. Its server-wide quest progression was criticised
for co-op. Sources: [GameSpot flame altar](https://www.gamespot.com/articles/enshrouded-strengthen-flame-altar-upgrade-guide/1100-6520562/),
[GameSpot base building](https://www.gamespot.com/articles/enshrouded-base-building-guide/1100-6520568/),
[PC Gamer on server-wide quests](https://www.pcgamer.com/enshroudeds-promising-early-access-has-a-problemquest-progression-is-server-wide-which-means-a-single-player-could-hog-all-the-fun-times-to-themselves/).

**Palworld.** The base "starts working without you": creatures with work skills turn a campsite into a running
place, and crafting pulls from every nearby chest. Reviewers single out how the parts "constantly strengthen one
another". *Learn:* things you caught working for you is the fantasy; Awaken's Echoes and settlers already work, they
just aren't the star. Sources: [PC Gamer 1.0 review](https://www.pcgamer.com/games/survival-crafting/palworld-review/),
[PC Gamer on chopping 400 trees](https://www.pcgamer.com/games/survival-crafting/for-years-survival-games-have-convinced-us-that-chopping-down-400-trees-is-somehow-part-of-the-fantasy-palworld-proves-them-wrong/),
[GamesRadar](https://www.gamesradar.com/its-almost-embarrassing-that-pokemon-got-upstaged-by-something-as-sloppy-as-palworld-a-janky-survival-game-carried-by-one-good-idea/).

**Zelda: Tears of the Kingdom.** Ultrahand works because "every single thing" was rebuilt as physics bodies, and the
team aimed for a system that makes fun things happen, "multiplicative gameplay", not a feature. Objects were made
thicker than real so they're easy to see and grab. *Learn:* the magic is lifting a thing you made and setting it
somewhere else; build tools should feel like a power. Sources: [Game Developer on TotK's physics](https://www.gamedeveloper.com/programming/how-nintendo-did-the-impossible-with-tears-of-the-kingdom-s-physics-system),
[GamesRadar on Ultrahand's development](https://www.gamesradar.com/tears-of-the-kingdom-dev-describes-utterly-chaotic-ultrahand-development-it-broke-it-went-flying/).

**Monster Hunter.** Two progressions at once: your gear and you ("learning the environments and the monsters'
moves"); "craft gear to kill monsters, to craft better gear, to kill bigger monsters"; "clever monster design makes
all the grinding feel less like a grind". *Learn:* a fight you study is the content; what it leaves behind should be
of that monster (Arms does this). Sources: [Game Developer, Confessions of a Monster Hunter](https://www.gamedeveloper.com/design/confessions-of-a-monster-hunter),
[GameSpot Wilds review](https://www.gamespot.com/reviews/monster-hunter-wilds-review-new-frontier/1900-6418338/).

**Elden Ring.** Messages and bloodstains share danger without anyone online at once; a bloodstain is a recording of
someone's last seconds; groups highlight friends' messages. Trolling and clutter are the costs. *Learn:* a light,
template-only trace of other players makes a world feel inhabited. Legacy dungeons (big, looping, with shortcuts back
to a rest point) are what people remember. Sources: [PC Gamer on message griefers](https://www.pcgamer.com/elden-ring-message-griefers-are-breaking-my-spirit/),
[GamesRadar on groups](https://www.gamesradar.com/elden-ring-multiplayer-groups-will-let-you-find-friends-more-easily/).

**Satisfactory.** "The satisfaction of automation with some really great animation"; the space elevator is both the
goal and a landmark; the joy comes "when you stop building to complete the objectives and start creating for the sake
of it". Wiring is the tedium. *Learn:* machines must be lovely to watch; one giant monument gives building a horizon.
Sources: [PC Gamer review](https://www.pcgamer.com/games/sim/satisfactory-review/),
[PC Gamer on its animations](https://www.pcgamer.com/satisfactorys-animations-make-watching-stuff-as-much-fun-as-building-stuff/).

**Terraria.** Bosses gate townsfolk, townsfolk gate services, happy towns sell pylons; Hardmode changes the world
itself; events (Blood Moon turns every pool of water red, invasions march in from the edge) have their own sky and
sound. *Learn:* a world that visibly changes after a victory, and events you see coming. Sources:
[NPC happiness](https://terraria.wiki.gg/wiki/Guide:NPC_Happiness), [Events](https://terraria.wiki.gg/wiki/Events),
[Hardmode](https://terraria.wiki.gg/wiki/Hardmode).

**No Man's Sky.** Expeditions are seasons that "unite the whole community for several weeks"; settlements celebrate;
rendezvous planets get crowded. *Learn:* shared, time-limited goals bring people back; data-driven so the world can
change without a client update (fits Euan's live-events dream). Sources: [Expeditions update](https://www.nomanssky.com/expeditions-update/),
[Beacon update](https://www.nomanssky.com/beacon-update/),
[PC Gamer on expeditions](https://www.pcgamer.com/no-mans-skys-expeditions-update-is-taking-us-on-a-galactic-road-trip/),
[GamesRadar on community](https://www.gamesradar.com/games/survival/i-became-a-space-trash-collector-in-no-mans-sky-and-fell-in-love-with-a-community-doing-the-same/).

**Vault Hunters.** The vault run: a timer, a themed generated dungeon, objectives, get out with the loot or lose it;
you shape your next vault with crystals (themes, objectives, permanent modifiers); co-op splits roles; the end goal
is collecting artifacts. The complaint is grind as difficulty rises. *Learn:* letting players shape the next run is
what makes repeats feel chosen. Awaken's gates have the extract rule; nothing lets you shape one. Sources:
[Vault Hunters wiki, crystals](https://vaulthunters.wiki.gg/wiki/Modifying_a_Vault_Crystal),
[Objectives](https://vaulthunters.wiki.gg/wiki/Objectives),
[Sportskeeda guide](https://sportskeeda.com/minecraft/minecraft-vault-hunters-modpack-guide-all-need-know),
[3rd Edition review](https://mmcreviews.com/all/modpacks/vault-hunters-3rd-edition).

**Create (and Create packs).** Its draw is motion: builds that "feel like a real machine you could reach out and
touch", automation "both functional and fun to watch", Rube Goldberg machines and clockwork cities. *Learn:* the joy is
seeing the work happen. Awaken's version should be seen and heard. Source: [Create's own guide](https://createmod.com/guides/getting-started-with-create-mod).

**GregTech New Horizons.** Tiers ("ages") where each one makes you much stronger and the advance feels earned by
solutions you devised; a 3,000-quest book; famously grindy. *Learn:* long-term making needs tiers you can name and
feel. Sources: [moddex review](https://moddex.gg/modpack/gt-new-horizons/reviews/1960), [GTNH wiki](https://gtnh.miraheze.org/wiki/Beginner_Tips).

**RLCraft.** A cruel world where the danger shapes how you travel (scarce waystones make the world feel bigger); death
is common and remembered; mixed reviews on fairness. *Learn:* danger gives travel meaning, but fairness keeps people.
Sources: [Windows Central](https://www.windowscentral.com/rlcraft-turns-minecraft-brutal-survival-game-and-i-cant-stop-playing-it),
[Sportskeeda](https://www.sportskeeda.com/minecraft/why-rlcraft-one-best-minecraft-modpacks).

**DawnCraft.** Souls-like bosses, an exploration RPG; players praise story progression, but say it's cryptic, most
never finish it, and performance suffers. *Learn:* boss quality and clear direction beat content volume. Sources:
[moddex review](https://moddex.gg/modpack/dawn-craft/reviews/747), [Better DawnCraft](https://www.modpackindex.com/modpack/60940/better-dawncraft).

**Prominence II.** RPG classes (6 paths), two story campaigns gated by progression, a talent tree, 1,782 quests;
reviewers find the quests overwhelming and "holes in its progression and incentives" make it stale. *Learn:* volume of
quests isn't depth; incentives must chain. Sources: [CurseForge](https://www.curseforge.com/minecraft/modpacks/prominence-2-fabric),
[moddex review](https://moddex.gg/modpack/prominence-2-hasturian-era/reviews/1684),
[community guide](https://www.minecraft-guides.com/modpacks/prominence-ii-hasturian-era/).

**All the Mods / SkyFactory / Better Minecraft / Medieval MC.** ATM's end goal is one huge craft (the ATM Star) that
pulls from every mod. SkyFactory grows a world from a tree and pays for achievements with prestige perks; Better
Minecraft and Medieval MC sell exploration: structures everywhere, villages that feel lived in, a big building palette
(from general knowledge of the packs; one source for ATM). *Learn:* one far monument goal gives a sandbox a spine.
Source: [SiriusMC ATM10 guide](https://wiki.siriusmc.net/link/103).

**Cobblemon servers.** Gyms as progression (a gym win raises your level cap), player-built gyms joined in a league,
riding companions by land, sea and air, player-run towns with frequent events. *Learn:* players building challenges
for other players is the server glue. Sources: [Trainers gym mod](https://www.modpackindex.com/mod/93673/brechers-trainers-a-cobblemon-gym-mod),
[Cobblemon.gg](https://cobblemon.gg/what-should-you-expect-from-cobblemon-gg).

**Hypixel SkyBlock.** A calendar year of 5 real days; elected mayors with global perks that schedule events (a mining
fiesta with double drops); a private island; minions that work while you're away. *Learn:* a rhythm of events and a
shared vote make a server feel like a society. Sources: [Mayor elections](https://hypixelskyblock.minecraft.wiki/w/Mayor_Elections),
[Events](https://hypixelskyblock.minecraft.wiki/w/Event), [Private Island](https://hypixelskyblock.minecraft.wiki/w/Player%27s_Island).

**Wynncraft.** 100+ authored quests across a big map; guild wars over territories whose ownership changes the map
(connected land strengthens your tower; trade routes can be cut). *Learn:* guild land that changes the world is the
end game of an MMO-like. Sources: [Guild War](https://wynncraft.wiki.gg/wiki/Guild_War), [Quests](https://wynncraft.wiki.gg/wiki/Quest),
[Mein-MMO](https://mein-mmo.de/en/mmorpg-innerhalb-von-minecraft,156834/).

**Solo Leveling.** The underdog becoming unstoppable, and the shadow army as the multiplier: "he literally turns his
enemies into his greatest assets"; a few generals (Igris, Beru, Bellion) became nearly as famous as the hero, while
early grunts faded; the rise moment's grandeur matters (fans were let down when the anime's version lacked it).
Critics note the army removes him from danger. *Learn:* the army needs a few named stars, a staged rise and a reason
to still be in danger. Sources: [Sportskeeda on the rise moment](https://sportskeeda.com/anime/why-sung-jinwoo-s-arise-solo-leveling-episode-12-next-fuga-jujutsu-kaisen-explained),
[Soap Central on shadow soldiers](https://www.soapcentral.com/anime/solo-leveling-which-shadow-soldier-sung-jinwoo-really-need).

**What runs through all of them.** (1) Threat at the place you love. (2) Tools that make building feel like a power.
(3) Things you beat working for you. (4) A world that changes when you win. (5) A far monument goal. (6) Traces of
other players. (7) Machines you can watch. (8) Named companions. Awaken has the bones of every one of these; it's
missing the flesh on most.

---------------------------------------------------------------------------------------------------------------------

## 3. The twenty ideas

Every idea grows out of something Awaken already has. Each has its own look (no vanilla particles or vanilla
silhouettes), plain speech for every player-facing line, and works for a group as well as one player.

Sizes are at AI pace, scaled from git history (the whole first game took about 30 hours from the MDK on 23 Oct).
For scale: Trails (1,298 lines) and Binoculars (2,046) each took a few builder-hours; Wild life (5,270) about half a
day; the Singing Deep (`delve`, 18,518) and Between the Gates' villages (`village`, 24,488, W0–W7) about one to two
days each with several builders. **S** = a few hours · **M** = half a day to a day · **L** = one to two days ·
**XL** = two days or more, several phases. Art and Euan's look rounds come on top (the bosses took several).

Quick list by when it starts:
- **Start (first five hours):** 1 Shaping Hands · 2 Dream Drafting · 3 Tide Nights · 4 The Company · 5 Runner's
  Lines · 6 The Hall of Moments · 7 The Turning Year
- **Mid (D to A):** 8 The Answering · 9 The Choirworks · 10 A Bell of Your Own · 11 The Home Gate · 12 Waycamps ·
  13 Wild Bonds · 14 Sworn Vows · 15 Toll Gates · 16 The Siege of Carillon
- **End (A and after):** 17 The Eight Domains · 18 Skyhold · 19 After the Seed · 20 The Signal's Chair

---------------------------------------------------------------------------------------------------------------------

### 1. Shaping Hands · building with your powers
*Starts: the first Hearthfire (hour ~1). Grows with every rank.*

**The feeling.** Building feels like being Awakened. A wall goes up in one sweep. A roof runs along its ridge. You pick
up the porch you got wrong and set it down two blocks left. You stop thinking "this'll take an hour" and start
thinking "what if".

**Minute to minute.**
- Inside land you belong to, hold the Shape key: your band draws its hard-light brackets on what you look at.
- **Sweep:** look at a block, hold and drag. A run of wall rises in the block in your hand, each block flying from
  your palm into place. Blocks come from your inventory and your home's chests (the chests-at-home rule already
  exists, `hearth/HomeCrafting`).
- **Lift:** grab a built piece (everything connected that you placed) and it floats as hard light. Turn it, carry it,
  set it down. Chests keep their contents, beds their owners.
- **Repeat:** "remember this window", then stamp it along a wall; mirror a wing across a line.
- **Ridge:** point at a roof line, pick stairs or slabs, and the roof lays itself at the pitch you chose.
- **Take back:** the last five shapes undo for free within a minute.
- **The ladder:** E sweep and take back · D lift up to 64 blocks · C repeat and mirror · B ridges and arches · A lift a
  whole house (4,096) · S shape the ground itself (the Gravity force raises and lowers soil). The Architect Path
  doubles reach.
- The room reading answers live: while you shape, the room panel's six pips move.

**Ties.** Hearth land (24 → 80 radius by tier), chests at home, the room reading (§28.2), the Architect power family
(today combat walls only), energy (INT), the Gravity force, Fracture (no shaping while fractured: it's a power).

**Why it's ours.** Ultrahand moves objects; Valheim and Enshrouded place pieces. Here building is a rank ladder of
the same power fantasy as fighting, in the Signal's own hard light, with the Hearth reading every change.

**The gap it fills.** T3: the Hearth reads and grades every room, and the settlers wait for Snug bedrooms and trade
rooms, yet every block is placed by hand. Euan's brief puts building first.

**Size.** L. Server-side moves spread over ticks, a client preview, the ladder, data-carrying blocks, tests. The lift
code is shared with Skyhold (18) and Dream Drafting (2).

**Risks.** Duplication (it only moves or spends real blocks; loot moves, never copied); griefing (only land you
belong to; the owner can switch members' lifting off); block entities and light updates on big lifts (spread, budget
≤ 3 ms a tick).

**Art, sound, performance.** The band's blue-white hard light and corner brackets; blocks fly in on a short arc and
land with a soft settle; each block in a sweep plays a note a step higher, so a wall "plays a scale"; a deep thud when
a lift sets down. A 4,096-block lift over 40 ticks.

**Multiplayer.** Members shape their shared Hearth together; guild halls can be shaped by anyone with the role.

---------------------------------------------------------------------------------------------------------------------

### 2. Dream Drafting · design it in your sleep
*Starts: the first dream at home (hour ~2–3). Full power with lucidity.*

**The feeling.** The dream is your studio. You wake to see your castle drawn in light over your land, and each day it
fills in a little more.

**Minute to minute.**
- Sleep at home and dream (40% + SPI, raised by a Sanctuary). The dream has a new door: **the drafting room**, a copy
  of your land as it is today, in dream light.
- Lucid (lucidity 50+, as today: reality checks, dream signs, intention): you fly, and you can place any block you've
  ever held. Shaping Hands works here at every rank.
- Dream time is short (3–8 minutes). Save the draft when you like ("Keep this.").
- Wake: the draft stands in your land as faint hard-light blocks, a **ghost**, with a ledger beside the Anchor: what
  it needs, and the grades its rooms will earn (the reading runs on the ghost).
- Fill it: a matching block placed on a ghost snaps in; a sweep fills ghosts at a discount; settlers with building
  work (Oren, a hand) fill ghosts from your chests in their work hours, the way Oren already builds the cottage over a
  day (§28.11).
- Household members asleep at the same time share the drafting room.

**Ties.** Lucidium (dream cells, lucidity, manifest from the Day Journal's palette), the Hearth reading, settlers' work
hours, Oren's building, Shaping Hands.

**Why it's ours.** The Lucidium is where minds make things (STORY.md). Building your home in a lucid dream and waking
to its ghost is the lore made into a tool.

**The gap it fills.** T4: the Lucidium is a whole pillar (7,738 lines) that connects to nothing you keep; Euan saw
"first lucid" in his save and then had nothing to do with it. T3: building at scale is all by hand.

**Size.** L, reusing dream cells and Shaping Hands.

**Risks.** It must never be free creative (every block is still paid); ghosts of many blocks (instanced, culled, capped
at 20,000); dreams are chance (a Dream Recorder machine already exists in Above's list: let it guarantee a drafting
dream once a play-day).

**Art, sound, performance.** Lucidium's own palette and bloom; ghosts as soft glass with drifting motes; a block
"wakes" from ghost to solid with a ripple and a quiet bell.

**Multiplayer.** Shared drafts per household; a guild's draft board where anyone can contribute materials.

---------------------------------------------------------------------------------------------------------------------

### 3. Tide Nights · the home under siege
*Starts: the First Night, the evening you set down your Ember. Then every new moon.*

**The feeling.** The place you built is worth defending, and you find out whether you built it well. At dawn you walk
the walls and count what held.

**Minute to minute.**
- **The First Night** (gentle, once): after the Ember is set down, the dark presses on the new fire. Three small waves
  of creatures are drawn to it; Ilse and Hettie come up the road with torches and help. You learn in one night that
  walls, a door and light matter.
- **Tide Nights** (each new moon, when the Deep Tide already rises, §21): gate pressure in the region spills toward
  Hearths. The Bell tower warns a play-day ahead, the Lookout shows where it comes from, the Signal says it plainly:
  "Tide tonight. It's coming from the north-east."
- **Dusk:** settlers go to the Hall; the Hearthfire burns blue. You close gaps (the reading's "why not" fill already
  finds every hole, shown in red), set Echoes on Guard pads, light the yard, place tide wards.
- **The night:** waves come along real paths from tide seams (cracks of gate light in the ground). They go for the
  fire and the doors. They break only soft things (doors, glass, wood, leaves); stone holds; a Grand room's walls
  never break. A big tide ends with a Tide Lord.
- **Dawn:** "The Hearth held." A card: what fell, who was hurt, what you earned: **tide glass** (a glowing glass that
  only tides leave, wanted by Grand rooms, the Choirworks and the Bell), essence, settlers' trust.
- If it falls, the fire is never lost; the damage stays to rebuild (W4's damage record and rebuild already exist
  for villages).
- A world option "calm tides" as there is "calm villages".

**Ties.** The Deep Tide (new moon), gate breaks and the Hearth Shield perk, the Bell tower and Lookout, Barracks and
Guard Echoes, village troubles (villages get tides too: ride out to help one), the reading's "why not".

**Why it's ours.** Valheim's raids are random monsters; Terraria's invasions march in. Awaken's tides are the Deep
pressing up through the gates (the story's truth), on the moon the game already uses, read by the Hearth's own scan.

**The gap it fills.** T3: nothing threatens the Hearth (vanilla spawns unchanged; camps raid only at tier 4). Rooms
that should matter for defence (Barracks, Bell tower, Lookout) give small numbers.

**Size.** L. A wave director, pathing to the fire and doors, soft-block rules, the prep and dawn cards, tide glass,
tests.

**Risks.** Wrecking a build (soft blocks only, Grand walls safe, always warned, calm option); frustration on a long
build session (the warning gives a whole play-day); entity budget (≤ 60 combat entities; waves keep to it).

**Art, sound, performance.** A tide sky: the moon dims, a gate-coloured aurora rises from the tide's side; seams of
light in the ground; the bell's alarm peal; the Hearthfire's blue flame through Lens. Creatures come in their own
forms (the mob-forms revamp's bodies).

**Multiplayer.** Neighbouring Hearths within 512 share a tide; guilds defend each other's halls; Euan's live event:
"the Great Tide".

---------------------------------------------------------------------------------------------------------------------

### 4. The Company · people to hunt with, then a guild
*Starts: the Registry (minute ~15).*

**The feeling.** The wild stops being lonely. Someone walks the road with you, says what you were thinking about the
pillar on the horizon, saves you once and needs saving once.

**Minute to minute.**
- At the Registry the clerk pairs you with another new licence. You pick one of three (authored people with a style
  and a temper: a nervous healer who awakened yesterday, a loud spear fighter who wants your gate, an old miner who
  awakened at sixty). They walk out with you.
- They talk about what's in front of you, keyed to world state (the rift, the gate pillar, a Lord's roar, your Hearth's
  first room). Plain speech: "That light's a gate. Mine's the one past the river. You want to go first?"
- They fight with their own kit (an Arms style and one or two powers), take Fracture, go down rather than die (unless
  Hardline), and hold the line when you climb somewhere they can't.
- They rank up beside you, not always on time: one may fail their Reassessment and need your help (a personal chain).
- Up to four companions. They live at your Hearth (they're settlers with their own rooms). A Company board in your
  Hall takes Association contracts (bounties exist).
- In multiplayer the Company is a guild: founded at the Registry, a banner, a Hall, contracts, a guild rank; empty
  places are filled by companions.

**Ties.** The opening's Registry, the citizens' people engine, Arms styles, powers, Fracture, settlers, bounties,
`PartyHooks` (the guild module the Together section waits for), Vael (the hunter who won't join you).

**Why it's ours.** Hunters under the same System, licensed by Carillon's Association, ranking up at the same Lens.

**The gap it fills.** T6: alone outside the walls; no guild module; Euan's multiplayer dream is guilds.

**Size.** XL. Ally AI that follows the Ascent's movement (or waits well), combat use of powers, lines, personal chains,
the guild layer.

**Risks.** Companion AI quality (they must never look lost: below a climb they wait and call out, then catch up by the
road); writing volume (plain speech, short); difficulty (gates scale for party size as they already do).

**Art, sound, performance.** Each companion a premium, recognisable skin and silhouette; short voiced barks (sound
plus text); party pathing kept under the entity budget.

**Multiplayer.** This is the guild module.

---------------------------------------------------------------------------------------------------------------------

### 5. Runner's Lines · courses you build, ghosts you race
*Starts: day one (you're a Runner from the start).*

**The feeling.** Shaving tenths off a route across your own roofs, then seeing your friend's ghost take a gap you
didn't know was possible.

**Minute to minute.**
- Place Line Posts (start, checkpoints, finish), a new piece of the home kit.
- Run it: your band records the run as a ghost (positions 20 times a second); next time your best ghost runs ahead of
  you in hard light; times to the hundredth; medals set from your own best.
- Build for it with the home kit (drainpipes, trellises, hoist beams, wall ivy already exist, §28 kit), Tether anchors,
  Leaper pads.
- Carillon has authored Lines: the opening's rooftop run as a time trial from the Runners' Roost, with the Roost's
  runners as NPC ghosts to beat.
- Wild Lines appear with each rung: Tether Spires, canyon swings, the first flight line through a sky arch.

**Ties.** The Ascent (the most praised thing in Awaken), Carillon's Runners' Roost and opening run, the home kit,
the Training Grounds' best lap (already on the Spire Board), the Daily Regimen's lap drill.

**Why it's ours.** Carillon is a runners' city and the Ascent is Awaken's signature; this gives movement a stage after
you've learned it.

**The gap it fills.** Movement has nowhere to be shown off once learned; the Training Grounds lap is a number.

**Size.** M. Recording and playback, posts, the board, Carillon's authored lines.

**Risks.** Few. Ghost rendering is one cheap client figure per ghost.

**Art, sound, performance.** Ghosts as your skin in thin hard light with a trailing ribbon; split chimes at each
checkpoint; a bell from the Roost for a new best.

**Multiplayer.** Friends' ghosts, boards at the Roost and your Spire, guild races.

---------------------------------------------------------------------------------------------------------------------

### 6. The Hall of Moments · your best moments as monuments
*Starts: your first Lord (hour ~1–2).*

**The feeling.** Pride. You walk a friend past the moment you parried the Mother of Spores' last swing, frozen in light
in your hall.

**Minute to minute.**
- When something big happens (a Lord falls, a first flight, a near death, a Sovereign), the Day Journal already keeps
  a snapshot of the place (`lucidium/journal/DayJournal.java`, structure templates up to 16 × 12 × 16) and the Day's
  End card names the best moment (`Progression.bestMoment`). Now it also keeps the poses: you and the creature at the
  killing blow, and five seconds of movement.
- At home a **Moment Plinth** shows one as a hard-light diorama: the stones of the place, you mid-swing, the beast
  mid-fall, slowly lit from turning light. Walk round it. Use it to replay the five seconds.
- A plaque in plain words: "Day 10. The Mother of Spores fell in the Fungal Hollow. You were level 9."
- Six plinths in one read room make it a **Hall of Moments** (a room kind): Comfort, settlers who stop to look and say
  something about it, visitors at festivals.

**Ties.** The Day Journal, the Day's End card, the Trophy Hall (today +1% damage a Lord, a number with no story),
the room reading, settlers' talk.

**Why it's ours.** It's built on Awaken's dream memory: the same snapshots that build your dreams become your monuments.

**The gap it fills.** T3: no stage for what you've done.

**Size.** M. A pose ring buffer, the plinth's baked hard-light mesh, the room kind.

**Risks.** Capturing a boss's pose (its model parts at that tick); plinth rendering cost (bake once to a mesh, cap
blocks per plinth).

**Art, sound, performance.** The Holo's hard-light palette, dust in the light, a soft chord as you come near.

**Multiplayer.** Visitors see your hall; a guild hall shows every member's best.

---------------------------------------------------------------------------------------------------------------------

### 7. The Turning Year · seasons and festivals
*Starts: day one.*

**The feeling.** Time passing. You remember your first winter at your Hearth, and the first spring the cherry trees
came back over the road.

**Minute to minute.**
- A year of 32 play-days, four seasons of eight.
- **Spring:** blossom on the Lands' trees, migrations north, crops quick, the Bell Festival in Carillon (a ringing
  contest in Choir Square).
- **Summer:** long days, the Rolling Storm at its worst, gates restless; the Midsummer Hunt (a Toll Gate, 15).
- **Autumn:** leaves turn and fall (the Lands' wind and falling leaves exist), the harvest, herds fat; the Harvest
  Feast at your Hall (the Hall's shared meal exists: everyone fed).
- **Winter:** the snow line falls, rivers freeze, packs grow bold, the Long Night (the year's biggest Tide Night);
  Lantern Night in the villages.
- Festivals are data, like happenings, so a server can add one without a client update.

**Ties.** The Lands' foliage and wind, wild life and the Wild Lands' herds, the Living Wilds Pulse, villages and your
Hall, Tide Nights, the storm, herbs and crops.

**Why it's ours.** Festivals of a city of bells; seasons drawn by Awaken's own Lands rather than a texture swap.

**The gap it fills.** The world is the same on day 40 as on day 4; festivals are promised and unbuilt (§28.6–28.8).

**Size.** XL. The colour change needs the biome tint (§28.2 calls it "mixin territory": a Turbo feature, approved
first), snow placed only near players, festival content.

**Risks.** The tint mixin; snow and Distant Horizons' far terrain agreeing; making each festival worth attending.

**Art, sound, performance.** Season grades through Lens, leaf colours per tree kind, festival bunting in each
settlement's own style, a seasonal bell peal.

**Multiplayer.** Server-wide festivals; Euan's live events.

---------------------------------------------------------------------------------------------------------------------

### 8. The Answering · your Echo legion
*Starts: the first bindable Lord (D-rank, hour ~4–6).*

**The feeling.** Solo Leveling's best moment, made Awaken's. You stand over a Lord, the Signal asks, and it rises in
your colour. Twenty hours later fifty of them walk behind you, and three have names.

**Minute to minute.**
- **The rise.** After a bindable kill the body lies in light for ten seconds (today's bind on B). Now it's staged: the
  camera dips, the light gathers, the Signal says one word ("Rise."), and the creature rebuilds out of motes as an
  Echo of its own look: a dark ink-glass body with a rim of your aura colour, eyes as two points of light. Not a
  tint, not an outline.
- **The legion.** Today's field cap (3 + SPI/6) becomes a legion that grows with rank and with a new room, the **Echo
  Hall** (the Barracks grown up), where they stand in rows of light.
- **Growth you can see.** Every Echo already has a level (`EchoRecord`). At set levels it changes form: a Mossback
  Echo becomes a Mossback Bulwark, bigger, with a new move.
- **Generals.** A bound Gate Lord becomes a General: you name it, it keeps its fight's moves (the boss kit), it leads a
  squad of four to eight, it speaks to you alone in a few plain lines.
- **Command.** Hold B for a wheel: follow, hold here, charge my mark (lock-on, §40), guard me, scatter.
- **Away from you.** From the Echo Hall's table send a squad on an errand: into a gate theme you've cleared (they bring
  back its loot next play-day), to scout a region (the Atlas clears), to guard a village through a tide. Each comes
  back with a line in your Journal.
- **Loss.** An Echo can be broken (down for a play-day, as `downUntil` already does). A General broken holding a door
  in a Tide Night is a moment you'll remember.

**Ties.** Echoes (bind, field, roles, levels), lock-on, the Barracks and Training pads, cleared gate themes, Tide
Nights, the Hall of Moments, SPI, the boss kit's moves for Generals, a hidden Path ("the Answered").

**Why it's ours.** Awaken's Echoes are "what a world refuses to feel" given back a shape; they work your Hearth as well
as fight; your Generals are Lords you actually beat.

**The gap it fills.** T4 and summary point 5: the signature fantasy has 1,795 lines next to the gates' 47,520.

**Size.** XL. The Echo look (a render layer for every body), the rise, legion command, forms, Generals, errands, tests.
Generals reuse the boss plan's bodies and moves.

**Risks.** The entity budget (≤ 60 combat entities): only a front line of twelve fights at once; the rest stand as
banners of light behind you and step in when one falls. Balance (an army makes fights trivial: Generals have a
cooldown; the legion scales the gate's spawns through world state, which the patent rule allows). Look quality (the
ink-glass must pass Euan's bar on first sight).

**Art, sound, performance.** The Echo material is the whole idea; the rise sound is a choir chord climbing to the
Signal's tone; command barks as short sounds; each General a voice colour.

**Multiplayer.** Legions fight side by side; each player sees their own Generals named; a guild muster at a Toll Gate.

---------------------------------------------------------------------------------------------------------------------

### 9. The Choirworks · machines that run on song
*Starts: bell bronze and the Tuning Fork (C-rank, hour ~12–17).*

**The feeling.** Create's joy of watching your machine work, but what you watch is sound: rings of light running down
bronze lines, chords that make the floor hum, a workshop that sings.

**Minute to minute.**
- **Notes.** The Tuning Fork (exists, §34) struck on a vein gives its note; bell bronze (exists) makes resonators.
- **Build.** Sounders (a bell rung by a waterwheel or hoist, a Deep vein's song carried up, a Lord's Heart's hum),
  Lines (bronze pipes; you see each tone travel along them as a ring of light), Resonators (machines tuned to a note:
  a crusher, a smelter, a press, a sorter, a lift).
- **Chords.** Two or three notes together run bigger machines; a wrong note in a line stalls it; you tune by ear, and
  the band shows the chord.
- **What it makes.** Essence refined from gate shards, Power Core shards into Cores, Deepglass from seep water, tide
  glass from tide ash, ripened crops, a Choir Lift (straight up or down), a **Ringing Gate** (fast travel between
  your Hearths and places you've belled).
- **The end piece.** The **Lesser Choir**, a machine the size of a tower that lets you hear the layers from home: which
  gates are near breaking, the Veil's spirits nearby, a Sovereign's domain stirring.

**Ties.** The Singing Deep (veins sing, the Fork, bell bronze), Carillon's bells, Above's machines and essence reactor,
the Workshop room, the Mine head and the miners who feed it, the story's Choir, Lens, the sound conductor.

**Why it's ours.** Nobody's machines run on music; it comes straight from the lore (the Precursors' machine was a
Choir) and from the city of bells.

**The gap it fills.** Summary point 6: no making after the house; Above is 7,879 lines and 13 machines with no
logistics; the Workshop room does one thing.

**Size.** XL. A note network spread across ticks, 8–12 machines, a pitched sound system (made with the audio tools),
visuals, recipes, tests.

**Risks.** Performance (network updates and ring visuals); sound fatigue (a base that hums: soft, musical, falls off
with distance, never shrill); complexity (three rules only: notes, lines, chords).

**Art, sound, performance.** The hardest audio in the game: a tuned palette of bells, bowls and hummed choir;
patinated bronze; rings of light through Lens.

**Multiplayer.** A guild's Choir; Hearths linked by Ringing Gates; a server-wide Great Choir as a live event.

---------------------------------------------------------------------------------------------------------------------

### 10. A Bell of Your Own · your Hearth becomes a town
*Starts: a Tier III Anchor and a Bell tower (C-rank, hour ~15).*

**The feeling.** Carillon has its bells; now you have one. When it rings at dawn, the road fills.

**Minute to minute.**
- A chain from Carillon's bell-founders: learn to cast. Gather bell bronze, a Lord's Heart, build the mould (a pit
  and a furnace ring you actually build), the pour (a set piece: molten bronze, the furnace roaring, a timing at the
  vent), then tune it in the Deep with the Fork. Every bell has its own note.
- Hang it in your Bell tower. Your Hearth is **chartered**: Carillon records it, it's named on every Atlas and on the
  Horizon line ("Ashcombe · a chartered hearth"), roads route to it, caravans stop and trade (today they only pass).
- Set its peal at the bell frame (four to eight notes). It rings at dawn and dusk and for events, heard 256 blocks
  out. People know your bell.
- Grow it into a town: a market square you build (stalls are a room kind), an inn, more settlers than the cap of 12,
  festivals at your Hall.
- Ring it for help in a Tide Night and the nearest village's watch, your Company and your guild come.

**Ties.** Carillon and its bells, the Bell tower room, bell bronze and the Fork, settlers' caps, W7's roads, wayhouses
and caravans, the Atlas, the Choirworks (your bell is a Sounder).

**Why it's ours.** The city of bells makes the bell the charter.

**The gap it fills.** T3: after Tier V there's no further goal; §28 "Left": a hamlet growing into a town, caravans'
own trade, festivals.

**Size.** L, plus art (bells, the mould, the pour).

**Risks.** Overlaps W6/W7 and Carillon's sessions (needs one owner); the pour as a minigame must be one clean beat,
not a chore.

**Art, sound, performance.** A real bell per note (made with the audio tools), the pour's light through Lens, banners
in your town's colours.

**Multiplayer.** Guild bells; every chartered Hearth on the server's map; visiting a friend's town by its bell.

---------------------------------------------------------------------------------------------------------------------

### 11. The Home Gate · a gate that lives at your Hearth
*Starts: a Gate Stabiliser and C-rank (hour ~17).*

**The feeling.** The thing that threatened you now works for you: your own dungeon behind the house, a floor deeper
every rank.

**Minute to minute.**
- With a Gate Stabiliser (an Above machine) you can **anchor** a gate standing in your land, or plant a gate seed taken
  from a cleared gate's core. It stays and never breaks.
- Choose its theme from themes you've cleared (your gate memory keeps them), its floors (by rank) and its modifiers
  (harder pays more).
- Shape it: keep a room you liked from a cleared gate and it can appear in yours; set traps (Above machines); station
  Echoes as its garrison for practice.
- Use it: a daily run at reduced pay; a training ground for new moves and weapons; a party run for friends (they run
  your gate, you take a share).
- The trade-off: an anchored gate feeds the tide. Tide Nights at your Hearth come a little stronger.

**Ties.** Gates (themes, rooms, Satchel, modifiers), Gate Worlds (their looks), the Gate Stabiliser, Hearth land,
Echoes, Tide Nights, the Company.

**Why it's ours.** Gates are Awaken's core; the Break ending makes gates calm doors. This gives a personal taste of
that far earlier.

**The gap it fills.** T3/T4: gates and the Hearth barely touch (a gate never forms within 64 of an Anchor).

**Size.** L, on top of Gate Worlds.

**Risks.** Farming (reduced pay, a play-day cooldown); a cell that stays (gates use 512 × 512 cells that the recycler
frees; a Home Gate keeps one); building on the Gate Worlds work in flight.

**Art, sound, performance.** The anchored gate's frame grows into your land (roots of light, your aura colour); its
hum joins your Hearth's sound.

**Multiplayer.** Friends run your gate; guild challenges ("clear ours in under ten minutes").

---------------------------------------------------------------------------------------------------------------------

### 12. Waycamps · build your road into the Deep
*Starts: D-rank and the Well (hour ~5).*

**The feeling.** The Descent stops being a place you visit and becomes a road you build: camps, lifts and lamps of
your own, all the way down. Every stratum you hold is yours.

**Minute to minute.**
- Split your Ember: carry a spark into the Well. Set it in a stratum and it's a **Waycamp**: a small fire (rest,
  respawn, a chest, light).
- Build the camp; the Hearth reads it like any home. Each stratum's rule hits camps differently: rising water (build
  high), gravity flipping with the tides (build on both faces), rooms changing when unobserved (lamps keep them still),
  Listeners drawn by sound (pad the floors).
- Build lifts: a Deep Lift (an Above machine) chain you construct from camp to camp, with bronze chain and
  counterweights, so climbing home costs no Strain.
- Staff it: a miner settler can live at a Waycamp; Echo guards; supply crates up and down the lift.
- Your road lets you take the Descent in pieces, and your party can use your camps.

**Ties.** The Descent (strata, rules, Waystones, Strain, Grave Echo, the Deep Lift), the Ember and the reading,
settlers, Echoes, the Choirworks, the Singing Deep.

**Why it's ours.** The Descent and the Ember are both Awaken's; building your way down ties them.

**The gap it fills.** T4/T5: the Descent is 6,840 lines for ten strata and the Endless; Euan never entered it; it's
a visit, not a project.

**Size.** L.

**Risks.** Keeping strata chunks loaded (only when a player is near, as today); coordination with the caves plan
(which is about overworld caves, not the strata).

**Art, sound, performance.** Camp lanterns in each stratum's own light (Lens underground); a lift's chain clank and
counterweight; camps glimpsed far below down the shaft.

**Multiplayer.** A guild's road down; shared camps; a party descends together.

---------------------------------------------------------------------------------------------------------------------

### 13. Wild Bonds · wear a beast in the Veil, and it remembers you
*Starts: the Veil (D, "ride along"); full at C with possession.*

**The feeling.** Taming that means something: you've been the animal. Your bonded beasts carry, scout and plough, and
grow old with your Hearth.

**Minute to minute.**
- In spirit form you can already wear a creature at C (SPEC §27.1 "Wearing a creature, in the Veil"). At D you can
  ride along: see through its eyes for a while.
- While you wear it, move as it and sense its world: a kite's long sight, a mole's sound map, a herd's alarm.
- Do it a kindness as it (lead it to water, guard its young, take it home), then let go. It may come to find you: it
  walks to your Ember and stays in your Stable or Pen.
- Bonded beasts: a mount that grows with the bond (the whistle and range exist), a worker (plough the fields, haul to
  the market, mind a pen), a scout (a bird clears the Atlas round home), a light fighter.
- They have names, young, and old age; the Stable's reading cares for them.

**Ties.** The Veil, the Wild Lands' herds and hunts (in flight), Mounts and the whistle, the Stable and the Pen,
Hettie, the Atlas.

**Why it's ours.** Possession through the Veil is a taming verb no other game has; no ball, no bait.

**The gap it fills.** T4: possession is gated at C with nothing after it; creatures are hunted or ridden, never bonded.

**Size.** L, plus animation (with the mob-anims session).

**Risks.** Overlap with the Wild Lands' herds (build on theirs); scope creep toward a monster collector (keep four
roles).

**Art, sound, performance.** The Veil's look while you wear a creature; a bond mark in your aura colour on the beast;
its own call when it comes home.

**Multiplayer.** Trade a bonded beast's young; visitors meet your animals.

---------------------------------------------------------------------------------------------------------------------

### 14. Sworn Vows · rules you swear for more
*Starts: the Stillpoint (hour ~3).*

**The feeling.** Choosing your own hard mode as part of who you are. "Fists only" isn't a challenge run; it's your
name, and the System marks you for it.

**Minute to minute.**
- At the Stillpoint, a Vow Stone: twelve vows (no healing items; no Echoes; fists only; no Extract before the Lord;
  lantern-dark; one life a gate; no movement power; the Signal's silence, no HUD).
- Each vow puts a mark on your body (a sigil burned into the back of your hand in first person), raises gate pay
  (+15% each, stacking), and after ten gates kept gives its Title.
- Break a vow in a gate and you lose that run's bonus; the mark cracks and mends over a play-day.
- Vows feed Truths ("I keep my word") and open hidden Paths.

**Ties.** Truths and their conditions, Titles, Paths, Hardline, gate rewards, Hands (first-person arms, §24).

**Why it's ours.** Built on the Truths' declare-and-become system and drawn on the arms you see all game.

**The gap it fills.** Truths aren't landing (Euan's save: three offered, none declared); the gate loop repeats; no
self-chosen challenge.

**Size.** M.

**Risks.** Balance; vows that fight other systems (check each against Arms and the Ascent).

**Art, sound, performance.** Sigils in each vow's own shape, glowing faintly; a crack sound and a small flare when
one breaks.

**Multiplayer.** A party vow everyone in the run keeps; a guild's vow.

---------------------------------------------------------------------------------------------------------------------

### 15. Toll Gates · the gates a city rings its bells for
*Starts: C-rank (hour ~17), never in a world's first five hours.*

**The feeling.** An event. The world stops: everyone heads to the same pillar on the horizon.

**Minute to minute.**
- Rarely (once in several play-days for your rank range) a Toll Gate forms 1–2 km out: a pillar twice as tall, and
  Carillon's great bell tolls. The Horizon line and the Atlas mark it; it will break into a region-scale break in
  two play-days.
- Muster: the Association sends hunters (your Company), your Echo legion comes, guildmates answer.
- Inside: a sealed gate (no Extract until its Warden falls), one big open floor in Gate Worlds' look, objectives split
  for a party (hold three bells while the rest push to the Warden).
- Reward: Toll Marks (paid by the Association only for these) for unique things: a Company banner, a General's place,
  bell bronze plates.

**Ties.** Gates and Gate Worlds, breaks, Carillon's bells, the Company, the Answering.

**Why it's ours.** The bells and the gates together; the city mobilising.

**The gap it fills.** T6: no group content, no world event at a gate's scale.

**Size.** L, after Gate Worlds.

**Risks.** It needs the Company (or a legion) to feel right alone.

**Art, sound, performance.** The great bell's toll across the whole map (one sound, heard everywhere); a pillar with
a slow ring of light climbing it.

**Multiplayer.** The defining server event.

---------------------------------------------------------------------------------------------------------------------

### 16. The Siege of Carillon · the night the city fell
*Starts: A-rank (hour ~70), or earlier as an act break.*

**The feeling.** A place you love in danger. Your Company, your Echoes and the citizens you've known since the first
morning fight beside you, and you take the city back street by street.

**Minute to minute.**
- The bells ring wrong again (the opening's callback). The Court reaches up through Choir Square; blight spreads
  district by district; citizens flee to your Hearth and the villages (refugees who live with you for a while).
- Retake each district: ring its bell to clear it (Atlas districts and bells already exist), hold it through a wave,
  free its people.
- Rebuild: you can give a design (from Dream Drafting) to a ruined block, and it's built under your name.
- Vael's A-rank beat ("It said you'd be the one who makes it. It said that to me first.") is the siege's heart.

**Ties.** Carillon (city, citizens, districts, bells), the Rival, breaks, settlers as refugees, the Company, Echoes,
Dream Drafting.

**Why it's ours.** "The bell rings wrong" is how Awaken begins; this is how its middle turns.

**The gap it fills.** T5: no story between Rival beats; Carillon fades after the Registry.

**Size.** XL, with the Carillon sessions.

**Risks.** The city is the most complex place in the game (city and citizens ~32,000 lines) and must restore exactly;
the city's frame rate (Euan saw 55 fps there).

**Art, sound, performance.** Blight in Carillon through Lens, bells out of tune, smoke over the canals.

**Multiplayer.** The whole server defends the city as a live event.

---------------------------------------------------------------------------------------------------------------------

### 17. The Eight Domains · the Hollow Court as places
*Starts: S-rank (hour ~88). The first door can open earlier as a glimpse.*

**The feeling.** Elden Ring's legacy dungeons and Terraria's turning world, in Awaken's lore. Every Sovereign is a place
you're afraid of, a fight you remember, and a change you see from your Hearth's window.

**Minute to minute.**
- Stratum 9 becomes **the Court**: a hall of the Keepers with eight doors.
- Each door is a domain built from what its Sovereign carries:
  - **Hunger** (Ilyr): a feast hall that eats itself; food spoils in your pack as you go.
  - **Silence:** a cathedral where every sound draws death; your own footsteps are the danger.
  - **Sleep:** a dream city where you must stay lucid (reality checks matter here).
  - **Machines:** the Precursors' own foundry, the Choir's works.
  - **Mirrors:** halls where your shadow fights you.
  - **Storms:** a palace in a storm that never ends.
  - **Roots:** a forest grown through stone; the plants hunt.
  - **Ash:** a burnt city, the last before the Seed.
- Each domain takes 30–60 minutes: a route that loops back to its door through shortcuts you open, Court Keepers
  (elites), a Keeper's spirit in the Veil who tells you what this one carried, and the Sovereign's fight.
- At the end, a choice. **Set it down** (free them) or **carry it** (take their Burden: a lasting power with a cost.
  Hunger: you heal from every hit, but you starve twice as fast. Silence: sound-hunters can't hear you, and nor can
  you hear the Signal's chimes). Your Burdens shape the Wake ending.
- **The world above changes** when one is freed: Silence, and birdsong comes back to the Wilds; Roots, and blight
  grows over into forest; Storms, and the Rolling Storm calms; Hunger, and every crop ripens faster; Machines, and
  Precursor ruins wake; Mirrors, and lakes show the Veil; Sleep, and everyone dreams more; Ash, and the sky clears.

**Ties.** The story (Sovereign data, `story/Court.java`), Descent strata rules, the boss plan's bodies, Gate Worlds'
atmosphere tools, Lens, Veil spirits, the Lucidium, the storm, the wild's plants, the endings.

**Why it's ours.** Straight from the story bible's truth: the Keepers became the Court by carrying the world's pain.
Each domain uses one of Awaken's pillars.

**The gap it fills.** T5 and summary point 3: the Court is a spawn rule with a line each; the end game is the
thinnest part of the game.

**Size.** XL (several days). Phase it: one domain first as the bar, the way the bosses started with the Living Forge.

**Risks.** Eight premium bosses (Euan's bar is high: "derpy" was the verdict on the first kit bosses); nobody plays
90 hours to test it (commands, tours, a `/awaken story court` hop); fights must stay readable (no minion spam, as the
bosses verdict says).

**Art, sound, performance.** Each domain its own look and sound bed; world changes visible from far away (Far Sight).

**Multiplayer.** Domains scale for parties; a guild's first clear announced to the server; world changes are
server-wide, Burdens are each player's own.

---------------------------------------------------------------------------------------------------------------------

### 18. Skyhold · raise your Hearth into the sky
*Starts: A-rank and Skyborn (hour ~70).*

**The feeling.** The monument. The ground shakes, your house tears free, and the land you built rises through the
clouds.

**Minute to minute.**
- With a Launch Ring (Above) and Gravity anchors (the Lands' Fault Seam crystals are already Gravity catalysts) set in
  a ring round part of your land, you start **the Lifting**.
- Over one play-day the land inside the ring (up to 48 across and 64 deep) rises: copied block by block 200 up, its
  underside grown with hanging roots and floating rock; below, the old ground fills as a crater lake, the Hearth's
  Hollow.
- Life in the sky: wind, sky creatures, settlers who love it or hate it, your bell heard below, sky tides on Tide
  Nights (flyers), the Halo's route nearer.
- Getting up: Wings, a Choir Lift, a rope from the crater, a sky stair you build, a chain bridge from a mountain top.

**Ties.** The Ascent (Wings, Skyborn), Above (Launch Ring, Halo), Gravity crystals, the Hearth, Shaping Hands' lift,
the Choirworks' lift.

**Why it's ours.** The Ascendants went up (STORY.md). The movement ladder ends in flight; this gives the sky a home.

**The gap it fills.** T3/T5: after Tier V there's no monument; flight has nothing built for it (the Halo is 454 lines).

**Size.** L, reusing Shaping Hands' lift.

**Risks.** Copying ~150,000 blocks (spread over a play-day under 3 ms a tick, light recalculated in batches); far
terrain showing a floating island (Far Sight and Distant Horizons); settlers falling (rails, and they won't walk off).

**Art, sound, performance.** The Lifting is the set piece: dust, roots tearing, a long low tone rising; then clouds
below your windows.

**Multiplayer.** A guild skyhold; a sky town of several households.

---------------------------------------------------------------------------------------------------------------------

### 19. After the Seed · each ending opens its own end game
*Starts: the ending (hour ~100).*

**The feeling.** The choice mattered, and the world shows it every day after.

**Minute to minute.**
- **Become the Signal.** You take the cradle and see as the Signal sees. New Game+ stops being a number: each Ascension
  tier adds something (a new family of gate modifiers, a Sovereign who comes back changed, one relic of your choice
  carried over), and the Signal remembers you ("You again. Good."). Before the loop turns you write up to ten
  Directives your next self will be given.
- **Break the Signal.** No more System. Gates become calm doors (as now). The freed Sovereigns walk the world as people
  who teach their crafts (Ilyr's cooking feeds a whole town; the Sovereign of Machines teaches the Choirworks' largest
  machine). Your end game is building a world without the System: your town, your Company, the Turning Year.
- **Wake up.** The colour comes back; blight never returns; the Lucidium becomes a place anyone can dream into
  together; the Endless becomes a pilgrimage where each depth is a memory of the world to set down, and the Burdens
  you carried matter there.

**Ties.** The three endings, Ascension, the Endless, Directives, Sovereign data, the Stillpoint, the Lucidium.

**Why it's ours.** The three endings are Awaken's; their end games come from what each one means.

**The gap it fills.** Summary point 3: NG+ is +30% health and +15% damage a tier; Break stops at calm gates.

**Size.** L for each ending's end game; XL for all three.

**Risks.** Far from Euan's next playthrough; per-player endings in a shared world (each player's story finishes as
their own, SPEC Together).

**Art, sound, performance.** The Wake ending's colour return is a Lens grade over the whole world; Become's sight is the
Signal's own UI over the world.

**Multiplayer.** Players who chose Become can speak through the Signal (20).

---------------------------------------------------------------------------------------------------------------------

### 20. The Signal's Chair · veterans become the voice
*Starts: Sovereign rank, or the Become ending.*

**The feeling.** You are the System now. A new hunter gets "Directive · The gate north is mine. Clear its second floor
and I'll leave you something." from someone real.

**Minute to minute.**
- A seat in the Stillpoint. Sitting there you see the newer hunters on the server as threads of light over a map.
- **Send a Directive** built from a phrase kit (no free text), paid from your own stock (loot moves, never copied).
- **Leave a mark** at a place, from the same kit, seen by others ("Danger ahead", "Sealed vault · try the roof").
- **Open a gate** for a hunter (pay a Lord's Heart; it forms at their rank).
- **Watch** a hunter's run as a ghost in the Veil.
- Deaths leave a short "last moment" others can watch where they fell (the Day Journal's recording).
- In single player, the Chair writes to your next loop (New Game+) and plays back the marks your last loop left.

**Ties.** Directives, Signal windows, the Stillpoint, the Veil's spirit sight, the story's twist (the Signal is you
from a past loop, and every Directive is advice from someone who made those mistakes).

**Why it's ours.** The story's twist becomes a mechanic.

**The gap it fills.** T6: no asynchronous layer; nothing for veterans to do for newcomers.

**Size.** M to L.

**Risks.** Abuse (phrase kit only, rate limits, a mute); single-player value is lower (it lives through New Game+).

**Art, sound, performance.** A Directive from a player arrives in the Signal's own window with a thin gold edge.

**Multiplayer.** Built for it.

---------------------------------------------------------------------------------------------------------------------

## 4. The ranking

Scores 1–5. **Feel** (how much it changes what playing feels like, weight 3) · **Gap** (how big a hole it fills, by the
evidence in §1, weight 2) · **Ours** (how much it grows from Awaken rather than borrows, weight 2) · **Group** (how much
it gives multiplayer, weight 1) · **Cost** (5 is cheap and safe, weight 1). Highest possible 45.

| Rank | Idea | Starts | Feel | Gap | Ours | Group | Cost | Score |
|---|---|---|---|---|---|---|---|---|
| 1 | 3 Tide Nights | First evening | 5 | 5 | 4 | 5 | 3 | **41** |
| 2 | 8 The Answering | D | 5 | 5 | 4 | 4 | 3 | **40** |
| 3 | 17 The Eight Domains | S | 5 | 5 | 5 | 4 | 1 | **40** |
| 4 | 1 Shaping Hands | First fire | 5 | 4 | 4 | 4 | 3 | **38** |
| 5 | 19 After the Seed | The ending | 4 | 5 | 5 | 3 | 2 | **37** |
| 6 | 9 The Choirworks | C | 4 | 4 | 5 | 4 | 2 | **36** |
| 7 | 2 Dream Drafting | First dream | 4 | 4 | 5 | 3 | 3 | **36** |
| 8 | 10 A Bell of Your Own | C | 4 | 3 | 5 | 5 | 3 | **36** |
| 9 | 6 The Hall of Moments | First Lord | 4 | 3 | 5 | 4 | 4 | **36** |
| 10 | 16 The Siege of Carillon | A | 5 | 4 | 4 | 4 | 1 | **36** |
| 11 | 4 The Company | Registry | 4 | 5 | 3 | 5 | 2 | **35** |
| 12 | 11 The Home Gate | C | 4 | 4 | 4 | 4 | 3 | **35** |
| 13 | 18 Skyhold | A | 5 | 3 | 4 | 3 | 2 | **34** |
| 14 | 5 Runner's Lines | Day one | 4 | 2 | 4 | 5 | 4 | **33** |
| 15 | 12 Waycamps | D | 4 | 4 | 4 | 3 | 2 | **33** |
| 16 | 15 Toll Gates | C | 4 | 4 | 3 | 5 | 2 | **33** |
| 17 | 20 The Signal's Chair | Sovereign | 3 | 3 | 5 | 5 | 3 | **33** |
| 18 | 13 Wild Bonds | D / C | 4 | 3 | 4 | 3 | 2 | **31** |
| 19 | 7 The Turning Year | Day one | 4 | 3 | 3 | 5 | 1 | **30** |
| 20 | 14 Sworn Vows | Stillpoint | 3 | 3 | 3 | 3 | 4 | **28** |

### My top five, and why
1. **Tide Nights.** It turns the warmest part of the game (the Hearth) into the part with stakes, so every block you
   place has a reason, and it starts on the first evening, where Euan actually plays. It also gives Echoes, settlers,
   the Bell tower, the Barracks and the reading's "why not" a night when they matter.
2. **The Answering.** The game is "Solo-Leveling-style" and its signature fantasy has the smallest system in the code.
   Done to the visual bar, the rise is the moment people clip and share; the legion makes every hour from D onward
   feel like growing an army, not just a level.
3. **The Eight Domains.** The end game is the thinnest part of Awaken by far, and the story's best idea (the Keepers
   carrying the world's pain) has no place to happen. Eight places, eight fights and eight world changes give the
   whole climb a destination worth 90 hours.
4. **Shaping Hands.** Euan put building first. The Hearth already rewards good rooms; this makes making them fast,
   powerful and Awakened, and its lift code carries Dream Drafting and Skyhold later.
5. **After the Seed.** A choice of three endings that all lead to a numbers bump wastes the story. Each ending opening
   its own end game is what keeps a world alive past the credits, and it sets up the multiplayer dream (Become players
   speaking through the Signal).

Just outside: **The Choirworks** (the long-term making thread for players who love Create) and **Dream Drafting** (the
most original idea here). **The Company** scores lower only because it's expensive; as the guild module it's the
foundation for everything multiplayer.

### What would change Euan's next playthrough most
He plays the first four hours, over and over. In that window:
- **Shaping Hands** changes every building session from his first Hearthfire.
- **Tide Nights**: the First Night is the evening of his first fire; the first real Tide comes at the first new moon
  (about hour 3–4, just as he'd stall at the D trial).
- **The Company**: someone walks out of the Registry with him, which changes the feel of every minute outside the walls
  and gives the D trial a friend who's taking it too.
- Cheap add-on: **The Hall of Moments** (his Mother of Spores kill, standing in his hall).

A note on the wall he hit: the D trial (survive three waves, §4.4) stopped his longest run. That's not one of the twenty
(it's tuning, not a new thing), but the next plan touching the early game should look at it.

---------------------------------------------------------------------------------------------------------------------

## 5. Notes for whoever plans a pick

- **Shared tech.** Shaping Hands' lift is reused by Dream Drafting (filling ghosts) and Skyhold (the Lifting); plan
  Shaping Hands first if more than one is picked.
- **Overlaps with work in flight** (check with the session before planning): Gate Worlds (`gate-worlds-plan`, the Home
  Gate and Toll Gates build on it); the bosses plan (`boss-plan`, bodies for Generals and Sovereigns); the mob forms
  and creature animation sessions (tide creatures, Echo forms, bonded beasts); the Wild Lands (`wild-lands-plan`,
  herds for Wild Bonds); the caves plan (overworld caves; Waycamps is the Descent's strata); the storm session (Tide
  skies, the Storm domain, seasons); a Wild start world (`plan-a-wild-start-world`, the Company's Registry pairing needs
  a version for a world with no city); Carillon's sessions (the Bell, the Siege, the Company).
- **Multiplayer rules** (memory, `multiplayer-ambition`): world state over per-player singletons, server-authoritative
  checks (Shaping Hands' lifts and the Chair especially), data-driven events (Tides, festivals, Toll Gates), no free
  text between players (the Chair's phrase kit).
- **The visual bar** (memory, `visual-quality-bar`): every idea above names its own look; none should ship with vanilla
  particles, tints or outlines standing in for it (the Echoes' violet outline today is exactly that).
- **Plain speech** (memory, `plain-dialogue`): every quoted line above is meant to be read aloud as written.
