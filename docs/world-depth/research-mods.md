# World depth: what mods, packs and Mojang do between the set pieces

Research for AWAKEN's "time between gates" problem: living villages, mining where every ore matters, caves built for movement, a Hearth that reads the player's own base, and the smaller loops (farming, trading, taming, fishing, exploring). Compiled 2026-10-26. Companion files: `research-games.md` (other games) and `audit-*.md` (what AWAKEN does today).

## How to read this

**Tags**
- **[V]**: read on the page itself (a mod listing, a README, an official wiki page, a review page).
- **[P]**: partly verified. A search excerpt of a page that blocked the fetch, a single poster's memory, or a table the extractor garbled. Numbers may be off.
- **[U]**: a lead only.

**Entries** are numbered M1 to M63 so the designer can cite them. Each gives the mechanism, numbers where known, what it gets right, where it turns to fluff, grind or overwhelm, and one line on what to take. Quotes are under 15 words and there is at most one per source.

**Dates.** "upd" is the "last updated" date on the listing on 2026-10-26. "pub" is the date the page was first published on that site, which for old mods is the date of the Modrinth page and not the original release. Wiki pages carry no date, so the release date of the update they describe is given instead.

**Download counts** are as shown on the listing on 2026-10-26. Modrinth data was read through its public API. I give the human page URL (`https://modrinth.com/project/<slug>`), which redirects to the right type. Counts are a crude vote: they show demand, not quality.

**What I could not get**
- Reddit is blocked for both search and fetch in this tool. No Reddit thread is cited. Player criticism comes from ModDex reviews (moddex.gg, which absorbed MMC Reviews), a 2012 Minecraft Forum thread, GitHub, mod-page notes and the add-on market itself (what players patch tells you what hurts).
- minecraft.net pages timed out and feedback.minecraft.net returned 403. Mojang facts come from minecraft.wiki (which cites the patch notes) and from search excerpts, tagged [P] where I lean on them.
- MineColonies has almost no written player reviews on the sites I could read (ModDex shows 5 ratings, no text). Its criticism is therefore indirect.
- I found no independent review of Dawncraft, and no mod that does a kidnapping-into-a-lair or burned-village chain (M63).
- Ore numbers in M25 are the one table I checked against the game's own data (`/Volumes/MEDIA/Awaken/.ref/assets`, Minecraft 26.3), not the web.

## Reading map
A. Villages and people M1 to M18 · B. Mojang's own villages, trades and updates M19 to M27 · C. Mining, ores and tools M28 to M36 · D. Caves and structures M37 to M44 · E. Movement M45 to M47 · F. Bases that read what you build M48 to M51 · G. Smaller loops M52 to M53 · H. Packs M54 to M62 · Gaps M63 · Principles at the end.

---

# A. Villages and people

### M1. MineColonies (Let's Dev Together) [V]
- **Source:** "MineColonies", CurseForge, upd 2026-10-21, 89.7M downloads, https://www.curseforge.com/minecraft/mc-mods/minecolonies . Official wiki (minecolonies.com/wiki, undated, read 2026-10-26): Miner http://minecolonies.com/wiki/buildings/miner/ , Happiness http://minecolonies.com/wiki/needs/happiness/ , Food http://minecolonies.com/wiki/needs/food/ , Gear http://minecolonies.com/wiki/needs/gear/ , Research http://minecolonies.com/wiki/systems/research/ , Raids http://minecolonies.com/wiki/systems/raid/ , Quests http://minecolonies.com/wiki/systems/quest/ , Requests http://minecolonies.com/wiki/systems/request/ , Traits http://minecolonies.com/wiki/systems/traits/ , Residence http://minecolonies.com/wiki/buildings/residence/ , Schematics http://minecolonies.com/wiki/tutorials/schematics/ .
- **Mechanism.** The player places a hut block, a Builder builds it from a schematic, and a citizen takes the job. Citizens ask for what they need: they check their own inventory, hut and racks, then post a request. A Courier delivers from the Warehouse. If the Warehouse lacks it, the colony looks for a citizen who can craft it. If nobody can, the citizen shows a red gear overhead and waits for the player. Quests come from citizens (talk, deliver, build or upgrade, break or place blocks, kill, research), can chain, and pay items. Raids start at the start of a night at random gaps set in config. Chat gives a direction, a bar counts raiders, the raider type follows the biome (Nordic, Mummy, Pirate ship, Amazon, Barbarian), and colonists stop work and go home.
- **Numbers.** Skill cap by home level: 10 at level 0, 99 at level 5. Tool tier a worker may use, by hut level: wood or gold 0, stone 1, iron 2, diamond 3, netherite 4. The Miner digs a shaft first and puts a platform every 3 blocks. Its ore levels are copper Y48, iron Y16, gold Y-16, diamond bedrock. Hut level sets depth: L1 Y40, L2 Y20, L3 Y0, L4 bedrock. Guard armour by tower level: leather or gold 1, chain 2, iron 3, diamond 4, netherite 5. Food has three tiers (tier 1 restores about 5 to 7, tier 3 about 12 to 13), and each crop grows in one biome class (cold, temperate, humid, dry), so top dishes need trade. Happiness needs security (at least two guards per three citizens), housing (home above level 2.5) and saturation. A death makes others mourn for 3 days. Research has four trees; each column needs a University level (column 1 level 1, column 6 level 5); the items come from the player's inventory and a cancel or undo refunds nothing. "Outpost" raises max citizens to 50 and "City" to 500. A residence houses one citizen per level, and more than 25 citizens needs research.
- **Gets right.** Every need is a chain of vanilla blocks, so an ore or a crop matters to a job. The ore ladder is the colony's ladder (hut level sets tool tier and dig depth). Friction is visible (the red gear). Surviving a raid without a loss gives a colony-wide mood boost. Research is a clear sink for rare items (one Diamond Ore buys "Motherlode", miners find +100% ore). Food shows the idea: citizens "will not eat food that doesn't meet (or exceed) their requirements".
- **Turns to a job.** Until a Courier and Warehouse run, the player carries everything (a guide excerpt calls this the top early frustration [P], minecraft-guides.com, page not fetched). A 2012 forum poster says it needs a lot of micromanagement [P, M3]. The community patches pathing and eating (M2). The schematics page says functional buildings must be built by a Builder, so the player cannot build a hut and move citizens in. The player's own architecture is never read.
- **Take.** Steal the needs chain and the ore-to-tier ladder. Do not copy the hauling or the Builder gate.

### M2. MineColonies add-ons: what players patch [V; MASS is P]
- **Source:** "Pathfinding Edition for MineColonies", Modrinth, pub 2025-09-12, upd 2026-10-21, 39,122 downloads, https://modrinth.com/project/pathfinding-edition-for-minecolonies . "Advanced Colonies", CurseForge, upd 2026-10-20, 217 downloads, https://www.curseforge.com/minecraft/mc-mods/advanced-colonies . "MineColonies Live: Automation", CurseForge, upd 2026-10-15, 729 downloads, https://www.curseforge.com/minecraft/mc-addons/minecolonies-live-automation . "ColonyWorks", CurseForge, upd 2026-10-04, 620 downloads, https://www.curseforge.com/minecraft/mc-mods/colonyworks . "MASS (Max Added Some Stuff)", seen only in a search excerpt of modpackindex.com (page blocked), https://www.modpackindex.com/mod/206424/mass-max-added-some-stuff .
- **Mechanism.** Pathfinding Edition lists the base AI's faults: citizens get stuck on random blocks, ignore roads, take absurd detours and waste time trying to eat. It makes them prefer roads, handle ladders and rails, and eat in the sensible place (inventory, then workplace, then restaurant). Advanced Colonies stops guards clumping into one "deathball" and spreads patrols in peacetime. Live: Automation routes requests to crafters, keeps stock buffers, auto-upgrades, auto-starts research, adds a merchant on a credit system, and has autopilot levels 0 to 6. ColonyWorks puts a Mechanization tree in the University that follows Create's age ladder. MASS adds a bulletin board where happier citizens post delivery, monster-slaying and visit quests [P].
- **Gets right.** These pages are the community's backlog for a colony sim: roads must matter, interruptions must drop, and logistics must run on their own.
- **Turns to a job.** That the backlog exists is the finding. Pathfinding Edition opens with "Struggling with MineColonies' AI?" and warns its alpha builds may be unstable.
- **Take.** If villagers use roads, route around obstacles and eat sensibly from day one, you skip the whole add-on genre.

### M3. Millénaire [V]
- **Source:** "Millénaire", millenaire.org, 9.0.3 for Minecraft 1.21.1 dated 2026-10-21, https://millenaire.org/ . CurseForge, upd 2026-09-18 (9.0.2), 3.9M downloads, https://www.curseforge.com/minecraft/mc-mods/millenaire . Minecraft Forum thread "Millenaire versus Minecolony", 2012-07-14, https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/mods-discussion/1389722-millenaire-versus-minecolony [P: one poster, remembering old versions].
- **Mechanism.** Seven cultures set in the year 1000: Normans, Indians, Japanese, Mayans, Byzantines, Inuit, Seljuk Turks. Villagers have names, trades and daily routines. Normans farm wheat, Indians rice. Couples have children and the village grows. Trading with villagers helps the village grow and earns culture items (food, statues, tapestries). If they like you enough, they may build you a house. 9.0 adds diplomacy, raids and villages you lead. Villages can trade with each other or go to war, and bandits and soldiers roam the wild.
- **Numbers.** Seven cultures. No reputation numbers on the pages I read.
- **Gets right.** The village runs without you, and the difference is cultural (crops, buildings, goods), so each village is somewhere. Trade moves the village, and the reward for being liked is a home.
- **Turns to grind.** The 2012 poster likes that Millénaire "feels like an RPG" but finds the progression slow and drawn out. An 8.1.1 release note says villager data could corrupt and leave dead villages [P, search excerpt].
- **Take.** Culture sets the economy. Reputation pays out as a place of your own.

### M4. MCA Reborn (Minecraft Comes Alive) [V]
- **Source:** "MCA Reborn", Modrinth, pub 2022-03-15, upd 2026-10-18, 4.26M downloads, https://modrinth.com/project/minecraft-comes-alive-reborn . CurseForge, upd 2026-08-29 (8.1.11 for 26.2), 15.3M downloads, https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn . GitHub Luke100000/minecraft-comes-alive (135 stars, 121 open issues), https://github.com/Luke100000/minecraft-comes-alive . Wiki, https://github.com/Luke100000/minecraft-comes-alive/wiki .
- **Mechanism.** Villagers get human faces, names, personalities and moods. Gifts and chats build a relationship in hearts. Enough hearts allows marriage. Children can be given chores (farming, lumber) and grow up. You can extend a village, raise its rank up to king, and register a new building with a Blueprint. Defeating the Reaper brings back fallen villagers. Free chat through an AI and text to speech are optional.
- **Numbers.** None on the pages I read (no heart thresholds, growth times).
- **Gets right.** A person known by name, mood and family has weight. The Blueprint step is a precedent for telling a village "this building is yours".
- **Turns to fluff.** The pages document few numbers [U on balance]. I found no critical review text. Free-text AI chat is a novelty, not a loop.
- **Take.** Persistent names, moods and a family tree are cheap. Marriage and children are a separate fantasy from AWAKEN's.

### M5. Live Villages [V, small, in development]
- **Source:** "Live Villages", Modrinth, pub 2026-06-17, upd 2026-08-29, 612 downloads, Minecraft 26.1.1 and 26.1.2, https://modrinth.com/project/live-villages .
- **Mechanism.** Each settlement keeps a record of population, housing, stock, wealth, security, comfort, growth, projects and routes. When chunks unload, abstract rules catch the village up, so it keeps running. A Trade Board shows what the village wants, its surplus, its projects and its route status; the player can trade or donate from the inventory. Builds appear as staged projects in the world; the player previews them as a wireframe (key O), sees the missing materials and helps finish. Roles include farmers, bakers, foresters, gardeners, beekeepers, shepherds, masons, carpenters, smiths and clerics, shown doing visible work in loaded villages. A guard workstation hires up to 5 armed guards; palisades have gatehouses. Routes run over land and water through Mileposts, Portmaster's Anchors and Lighthouses. Version 1.0.3 adds churches, full-moon services, renovation of damaged vanilla buildings, urgent-project priority and demolition of obsolete buildings. Outposts start hostile; the player can build trust, become a banner-bearer and trigger raids (unfinished).
- **Gets right.** It is the closest public match to "a shared simulation". One record per village, abstract catch-up, and a board the player reads.
- **Turns to fluff / risk.** 612 downloads and a self-declared work in progress: untested at scale. The page says settlements advance in tier and that tier changes the screen colour scheme; it names no other tier effect.
- **Take.** The ledger plus a wants-and-surplus board plus staged visible projects is the whole loop.

### M6. Village Quest (Living Kingdoms) [V, small]
- **Source:** "Village Quest", Modrinth, pub 2026-05-16, upd 2026-10-25, 11,192 downloads, release 2.6.1 for Minecraft 26.2 and 26.3 (Fabric, Java 25), https://modrinth.com/project/village-quest .
- **Mechanism.** Daily challenges, weekly tasks, story quests and special adventures earn reputation and relics. Villages have mayors, innkeepers and merchants with personalities and dialogue screens. Regional markets, traveling merchants, supply routes and caravans link settlements. Abandoned villages can be restored. There is a journal, an interactive map, a guild network, village projects and Wayshrines.
- **Gets right.** Versions track Minecraft 26.x, so it is the freshest example of an NPC-and-economy layer on current Minecraft.
- **Turns to chore.** Daily and weekly challenge boards are a pattern that turns into a checklist (my inference, not a review finding). No numbers on the page.
- **Take.** Caravans between villages are the "routes" AWAKEN wants. Skip the dailies.

### M7. Pueblo Vivo [V, tiny, beta]
- **Source:** "Pueblo Vivo", Modrinth, pub 2026-07-09, upd 2026-08-14, 547 downloads, Forge 1.20.1, https://modrinth.com/project/pueblo_vivo .
- **Mechanism.** Village stats: food, security, prosperity, fear, trust, morale, population, beds, golems. Player reputation per village and contracts. Builder villagers walk between the shared storage chest and the site and build one block at a time (walls, wheat fields, houses, torch zones, watchtowers, wells, corrals, plazas). Ages: Refuge, Village, Villa, Bastion, City. Threat phases run rumours, then scouts, then dangerous nights, then small attacks, and emergencies "are triggered by actual village conditions". It uses only vanilla blocks and villagers.
- **Gets right.** Escalation is readable and tied to the village's real state, not a timer.
- **Turns to fluff / risk.** Beta; balance and progression still change. No numbers published.
- **Take.** Copy the threat ladder (rumour, scout, bad night, attack) and the visible one-block-at-a-time builder.

### M8. VillageLife+ [V, tiny]
- **Source:** "VillageLife+", ModDex listing, last updated 2025-12-06, 9k downloads, Forge 1.20.1, https://moddex.gg/mod/villagelife .
- **Mechanism.** Persistent auto-generated names with inherited surnames. Villagers talk by profession, time and mood. Partnerships with intimacy scores and parent-child bonds. Hunger, mood (happy, sad, angry, scared) and tiredness change behaviour. Per-player reputation: good acts unlock interactions, bad acts make villagers avoid you. A mayor system claims villagers in an area, issues decrees and declares festivals.
- **Gets right.** Festivals by decree and a mood that shows in dialogue.
- **Turns to fluff / risk.** One version for 1.20.1, no reviews, 100+ subcommands is a lot of surface.
- **Take.** A festival is a village-wide state change on a calendar, not a quest.

### M9. Towns and Towers [V]
- **Source:** "Towns and Towers", Modrinth, pub 2022-05-22, upd 2026-09-16, 18,993,573 downloads, 1.18.2 to 26.3, https://modrinth.com/project/towns-and-towers .
- **Mechanism.** New villages, pillager outposts and ships, more detailed than vanilla and inspired by real buildings. The page cites 50+ structures as of update 1.9 and says its text is partly out of date. Profession chests and workstations are in the new villages. It does not touch vanilla structures and only generates in new chunks. Loot is mostly vanilla.
- **Gets right.** Variety of homes and silhouettes at scale: 19M downloads is the largest count among the village-content mods I found on Modrinth.
- **Turns to fluff.** It changes how villages look, not how they live. The page describes no villager behaviour change.
- **Take.** Looks sell; they do not make a village alive. Use it as the bar for variety, not for depth.

### M10. ChoiceTheorem's Overhauled Village (CTOV) [V]
- **Source:** "ChoiceTheorem's Overhauled Village", Modrinth, pub 2022-05-16, upd 2026-08-29, 11,081,240 downloads, 1.18.2 to 26.2, https://modrinth.com/project/ct-overhaul-village .
- **Mechanism.** 23 village variants and 14 pillager outpost variants, tailored to terrain, theme and biome using vanilla biome tags. Newer versions add separate structures instead of editing vanilla. Mostly vanilla loot with a few custom tables. Mods that add villagers need compatibility packs. Server-side only.
- **Gets right.** Biome-matched village families reuse the biome tag system, so worldgen mods inherit fitting villages.
- **Turns to fluff.** Visual only again.
- **Take.** Twenty-odd variants keyed to biome tags is the count to beat for "varied homes".

### M11. Integrated Villages, IDAS and Better Villages [V]
- **Source:** "Integrated Villages", Modrinth, pub 2024-06-21, upd 2026-05-28, 854,920 downloads, https://modrinth.com/project/integrated-villages . "Integrated Dungeons and Structures (IDAS)", Modrinth, pub 2022-04-03, upd 2026-06-11, 1,289,764 downloads, https://modrinth.com/project/idas . "Better Villages", Modrinth, pub 2024-12-15, upd 2026-10-12, 3,124,731 downloads, https://modrinth.com/project/better-village .
- **Mechanism.** Integrated Villages ships six themed villages (Tavern, Airship, Kutcha, Oasis, Pirate, Mediterranean) with heavily detailed houses, Create doors, puzzles and traps, and Supplementaries and Quark decoration. It disables vanilla villages by default and needs Create, Supplementaries and an API. IDAS claims 50+ structures: about 20 small world-building ones (camps, markets, animal dens), 7 endgame dungeons, 15 houses and inns, 6 wizard towers, event structures such as a Winter Wagon and Haunted Manor. Better Villages improves the five vanilla biomes' villages and gives no detail on layout or villagers.
- **Gets right.** Themed one-offs with interiors and decoration give each village a story; IDAS adds "event structures".
- **Turns to fluff.** Dependency stacks (Create, Supplementaries, Quark) and hand-built set pieces do not scale to generated hamlets.
- **Take.** Theme per village beats noise per house. Interiors and decor are what the player sees first.

### M12. Defenders: Guard Villagers and Villager Recruits [V]
- **Source:** "Guard Villagers", Modrinth, pub 2022-09-03, upd 2026-10-05, 13,933,787 downloads, https://modrinth.com/project/guard-villagers . "Villager Recruits", Modrinth, pub 2023-05-30, upd 2026-06-29, 465,685 downloads, Forge up to 1.20.1, https://modrinth.com/project/villager-recruits . "Villager Recruits: Village Expansion", Modrinth, upd 2026-10-20, 5,121 downloads, https://modrinth.com/project/villager-recruits-village-expansion .
- **Mechanism.** Guard Villagers spawns guards in groups of six with iron swords or crossbows, lets the player recruit from nitwits and unemployed villagers, and unlocks a follow command through Hero of the Village. Witches attack villagers, clerics and smiths give limited healing and repair, villagers fear polar bears. Recruits are hired with emeralds, grouped into armies, and support factions, claims and sieges.
- **Gets right.** 13.9M downloads for Guard Villagers shows real demand for a village that defends itself and reacts to threats.
- **Turns to fluff.** Recruits drifts into a war game. Hired armies and faction PvP are a different genre.
- **Take.** Guards that patrol, fear and heal are cheap. Armies are not the brief.

### M13. Trading friction: Easy Villagers, Trading Post, Trade Cycling, Infinite Trading, Bulk Trading [V]
- **Source:** Modrinth listings read 2026-10-26: "Trading Post" 9,701,025 downloads, upd 2026-09-24, https://modrinth.com/project/trading-post ; "Trade Cycling" 7,045,711, upd 2026-10-13, https://modrinth.com/project/trade-cycling ; "Bulk Villager Trading" 6,392,520, upd 2024-09-14, https://modrinth.com/project/bulk-villager-trading ; "Infinite Trading" 2,826,356, upd 2026-10-18, https://modrinth.com/project/infinite-trading ; "Easy Villagers" 1,722,039, upd 2026-10-22, https://modrinth.com/project/easy-villagers .
- **Mechanism.** Trading Post gives one block that opens every nearby villager's trades. Trade Cycling refreshes trades without breaking the workstation. Infinite Trading removes locks and, by default, removes demand price rises, with an option to stop trade loops. Easy Villagers lets you pick villagers up as items and adds a trader block, an auto trader (hopper fed), a breeder block, a converter (golden apple, weakness potion, about five minutes) and an iron farm block (a golem every four minutes).
- **Gets right.** Together about 27M downloads for fixes to one pain: vanilla trading is tedious. The pain is locks, cycling and walking between villagers.
- **Turns to fluff.** My read: farms in a block (Easy Villagers) delete the village, and they show that players use villagers as machines.
- **Take.** Design one trade board per village and let a trade never lock. Do not make villagers farmable.

### M14. Identity and jobs: Villager Names, Smarter Farmers, VillagersPlus, Chef's Delight [V]
- **Source:** "Villager Names", Modrinth, upd 2026-10-20, 12,779,501 downloads, https://modrinth.com/project/villager-names-serilum . "Smarter Farmers (farmers replant)", upd 2025-10-18, 13,157,788, https://modrinth.com/project/smarter-farmers-farmers-replant . "VillagersPlus", pub 2023-01-28, upd 2026-10-22, 9,250,589, https://modrinth.com/project/villagersplus . "Chef's Delight", upd 2026-10-04, 9,508,680, https://modrinth.com/project/chefs-delight .
- **Mechanism.** Villager Names gives every villager a name from a list of over 5,000 and shows the profession on the trade screen. Smarter Farmers makes farmers replant the crop they harvested, pick crops by neighbours (no lone patches), equip a hoe by their level, and stop trampling farmland. VillagersPlus adds five professions with workstations: Horticulturist, Oceanographer (aquarium), Alchemist, Occultist (stores XP) and Miner, whose Ore Grinder doubles ore, with speed set by the pickaxe, Fortune adding yield, and a Miner's house with a shaft to a vanilla-style mineshaft. Chef's Delight adds chef and cook professions to Farmer's Delight.
- **Gets right.** Names, visible competence (replanting) and workstations with a verb. The Ore Grinder is an ore-with-a-job in a village.
- **Turns to fluff.** Names with no memory are labels.
- **Take.** A name is the first step; a villager who remembers something is the second.

### M15. Decor that makes a place: Supplementaries and Amendments [V]
- **Source:** "Supplementaries", Modrinth, pub 2023-03-09, upd 2026-10-16, 29,584,658 downloads, https://modrinth.com/project/supplementaries . "Amendments", pub 2024-02-21, upd 2026-10-25, 20,479,080, https://modrinth.com/project/amendments .
- **Mechanism.** Jars, signposts, faucets, weather vanes, sconces, planters, lights, spring launchers and automation blocks; Amendments adds wall lanterns, cauldron potion mixing, stackable skulls with candles and an improved lectern.
- **Gets right.** Small functional objects (a signpost, a lantern, a jar) make a room look lived in. Other structure mods depend on it (Integrated Villages and IDAS need Supplementaries).
- **Turns to fluff.** The mods add objects, not rules. Nothing reads them.
- **Take.** If the Hearth is to read a base, these are the objects it can count.

### M16. Farmer's Delight [V]
- **Source:** "Farmer's Delight", Modrinth, pub 2022-08-07, upd 2026-08-29, 26,054,370 downloads, 1.18.2 to 1.21.1, https://modrinth.com/project/farmers-delight .
- **Mechanism.** A simple cooking system (pot, skillet, stove, cutting board), crops (rice, cabbage, tomato, onion), Feasts to share, Rich Soil, and tools. Straw and rope are farmed and returned as compost-style material.
- **Gets right.** A cozy loop with a meal as the reward and a Feast as a shared event.
- **Turns to fluff.** Food buffs are the only reason to cook. No page on this listing describes a village hook.
- **Take.** A Feast is a ready-made festival object for village events (M8).

### M17. Requests: Bountiful and Village Bounties [V]
- **Source:** "Bountiful", Modrinth, pub 2021-08-29, upd 2026-04-13, 7,894,656 downloads, https://modrinth.com/project/bountiful . "Village Bounties", pub 2026-05-01, upd 2026-10-19, 3,678 downloads, https://modrinth.com/project/village-bounties .
- **Mechanism.** A board block in villages shows bounties: bring items or kill mobs. Four rarity tiers. A Decree sets the kinds of bounties and rewards, a board holds up to three, and decrees come as rewards or are crafted. Each board has its own reputation; higher reputation makes rare bounties more common and unlocks some rewards. Village Bounties adds Easy, Medium, Hard and Elite tiers, one reroll, a HUD tracker, and a rule that villages with four or fewer buildings get no board.
- **Gets right.** Board reputation and decrees turn a list into a choice: the player picks the flavour of the work.
- **Turns to chore.** Fetch and kill templates are generic (Bountiful's page gives no depth on generation).
- **Take.** Let villages post wants from their ledger (M5), not from a random table.

### M18. Routes and travel: Settlement Roads, Waystones, Explorer's Compass [V]
- **Source:** "Countered's Settlement Roads", Modrinth, pub 2024-11-15, upd 2025-11-29, 237,014 downloads, https://modrinth.com/project/countereds-settlement-roads . "Waystones", Modrinth, pub 2022-06-15, upd 2026-09-25, 28,082,006, https://modrinth.com/project/waystones . "Explorer's Compass", listing upd 2026-10-04, 12,667,872, https://modrinth.com/project/explorers-compass .
- **Mechanism.** Settlement Roads generates roads between villages with pathfinding; its page plans to avoid water and mountains, add bridges and tunnels, and already adds buoys, distance signs and lanterns. Waystones lets players activate stones and warp between them or by scroll, and puts a waystone structure in villages. Explorer's Compass locates structures.
- **Gets right.** Players pay in downloads for travel infrastructure: 28M for Waystones, 12.7M for a locator.
- **Turns to fluff.** Warping skips the world; roads without life on them are lines on the ground.
- **Take.** A road that caravans use (M6) and that the player can follow is travel and story at once.

---

# B. Mojang's own villages, trades and updates

### M19. Village & Pillage (Java 1.14) and the village rules [V]
- **Source:** "Village & Pillage", minecraft.wiki, release 2019-04-23, https://minecraft.wiki/w/Village_and_Pillage . "Village", minecraft.wiki, https://minecraft.wiki/w/Village . "Villager", https://minecraft.wiki/w/Villager .
- **Mechanism.** A village exists while a villager is linked to a bed, job site or bell (detection switched from doors to these in 19w11a). Beds set the population. Job-site blocks give professions: an unemployed villager searches a 48-block sphere, the claim is dropped if it cannot reach the site in 60 seconds, and it must come within 2 blocks to finish claiming. The Java day runs work (around tick 2000), meeting at the bell (around 9000), sleep (12000) [P: the wiki's schedule table came out jumbled]. Villagers gain levels from trade XP (novice 0, apprentice 10, journeyman 70, expert 150, master 250). Breeding needs 12 nutrition points (bread gives 4) and a free reachable bed; after breeding they are unwilling for 5 minutes. Seven biome skins; hitting a villager raises prices. 2021 to 2024: meadow villages (1.18), more spread out (21w40a), camels in desert villages (1.20). Villages generate with an iron golem from 1.14.
- **Gets right.** The world reads placed objects (beds, workstations, bells) and builds behaviour from them. The bell gathering gives a daily beat players can see.
- **Turns to fluff.** Villages are static. Events change population and little else. Villagers are trade vending machines (M13).
- **Take.** The "object defines the role" pattern is exactly the Hearth's room detection. Vanilla's day beat (work, bell, sleep) is a free rhythm to build on.

### M20. Raids, Hero of the Village, Hero Proof [V]
- **Source:** "Raid", minecraft.wiki, https://minecraft.wiki/w/Raid . "Hero of the Village", https://minecraft.wiki/w/Hero_of_the_Village . "Hero Proof", Modrinth, pub 2023-10-20, upd 2024-12-09, 846,739 downloads, https://modrinth.com/project/hero-proof .
- **Mechanism.** Bad Omen turns into Raid Omen on entering a village; a raid starts unless the player drinks milk. Waves: Easy 3, Normal 5, Hard 7 (Java), one more at Raid Omen II or higher. Java totals about 14 to 19 raiders on Easy, 31 to 43 on Normal, 51 to 83 on Hard. Hero of the Village lasts 40 minutes, discounts trades 30% plus 6.25% per level above I (55% at V), and in Java villagers who see you within 5 blocks throw gifts by profession (toolsmith stone tools, cleric lapis and redstone, farmer bread and pie). Gift cooldown is 30 seconds to 5 minutes 30 seconds. Hero Proof adds a medal item that keeps the buff and discounts while held.
- **Gets right.** Gifts are profession-flavoured, so a win is felt per person. The mod's popularity (847K) shows players want a keepsake for it.
- **Turns to fluff.** The reward evaporates in 40 minutes. A village forgets.
- **Take.** A raid win should leave something that persists (M63).

### M21. Zombie sieges, abandoned villages, and the cure [V; mod [V]]
- **Source:** "Zombie siege", minecraft.wiki, https://minecraft.wiki/w/Zombie_siege . "Trading", https://minecraft.wiki/w/Trading . "80% Abandoned Villages", Modrinth, upd 2024-06-13, 100,044 downloads, https://modrinth.com/project/abandoned-villages .
- **Mechanism.** At midnight there is a 10% chance of a siege in the Overworld if a player stands in a village (claimed bed, bell or job site). It tries 20 zombie spawns, one every 3 ticks, near a point on a 32-block circle around the player, and ends at sunrise. Since 1.10, 2% of villages generate abandoned (zombie villages). Curing a zombie villager gives permanent major positive reputation, the largest single price discount source. The mod raises the abandoned rate from 2% to 80%.
- **Gets right.** Cure is the one vanilla event with a lasting, personal result. A tiny cost (golden apple, weakness potion) buys a villager who loves you for good.
- **Turns to fluff.** Sieges are a spawn burst. Nothing in the village changes afterwards.
- **Take.** Make "cure" the template: a short, costly act with a permanent personal result.

### M22. Villager Trade Rebalance and the wandering trader [V]
- **Source:** "Villager Trade Rebalance", minecraft.wiki, https://minecraft.wiki/w/Trade_Rebalance ; also https://minecraft.wiki/w/Villager_trade_rebalancing . "Java Edition 26.1", https://minecraft.wiki/w/Java_Edition_26.1 . "Wandering Trader", https://minecraft.wiki/w/Wandering_Trader . Reaction: search excerpts of feedback.minecraft.net and Sportskeeda, 2023 [P].
- **Mechanism.** Added as an experimental data pack in snapshot 23w31a (August 2023, 1.20.2). Librarians in different biomes sell different enchantment lists; each biome has one book only a master sells; crossbow, fishing rod and trident books left trading. Book price runs from 5 to 19 emeralds at level I to 17 to 64 at level V. Structure loot was tuned to match (Ancient City mending, mineshaft efficiency, pyramid unbreaking). Armorers need real diamonds for diamond armour; chainmail only from jungle and swamp armorers; cartographers sell seven new maps. Cartographer and wandering trader changes became default in 1.21.5 (25w07a). The wandering trader offers 9 trades: 2 purchase, 2 special, 5 ordinary, with equal-chance picks inside each group. In 26.1 (March 2026) trades became data-driven by profession level tags with deterministic random draws, and a master librarian always has 3 trades so a book is always on offer.
- **Gets right.** Mojang's stated aim: "librarian trades are considered to be too overpowered" and players should visit villages in different biomes. Tying goods to place makes travel pay.
- **Turns to grind / backlash.** Reaction in 2023 was mostly negative [P]: players said making enchantments harder was unnecessary. Mojang kept the librarian part experimental for years, and in 26.1 (pre-release 2) gave master librarians three trades so a book is always on offer.
- **Take.** Place-bound goods are good. Removing a pool players rely on is not; keep a floor.

### M23. The 2026 game drops: Tiny Takeover, Chaos Cubed, Wilderness Bound, and what is next [V]
- **Source:** minecraft.wiki: "Java Edition 26.1" (released 2026-03-24) https://minecraft.wiki/w/Java_Edition_26.1 ; "Java Edition 26.2" ("Chaos Cubed", 2026-06-16) https://minecraft.wiki/w/Java_Edition_26.2 ; "Sulfur Caves" https://minecraft.wiki/w/Sulfur_Caves ; "Java Edition 26.3" ("Wilderness Bound", 2026-09-15) https://minecraft.wiki/w/Java_Edition_26.3 ; "Java Edition 26.4" (unreleased, 5 snapshots) https://minecraft.wiki/w/Java_Edition_26.4 .
- **Mechanism.** 26.2 added sulfur caves: bands of sulfur and cinnabar, acid-green pools, potent sulfur that makes nausea gas and, over magma, geysers every 50 seconds, sulfur spikes, and a sulfur cube mob with 12 archetypes. They sit at lush and dripstone depth, are small and fragmented, and ores are rare there. 26.3 added the dappled forest, straw beds (four from three hay bales), cushions (a seat-like entity in 16 colours) and abandoned camps in 18 biomes with copper gear, iron, diamonds and gold in chests; explorer maps for camps cover 8 biomes, and secret chests hold maps to an Ancient City, a Mineshaft, a Desert Pyramid and Warm Ocean Ruins. 26.4 (planned Q4 2026) shows ice caves with crevasses, brittle ice (Silk Touch only), icicles that fall and hurt, and frostbite zombies.
- **Gets right.** Mojang is now shipping cave biomes with a hazard and a rule, and giving bases furniture entities (cushions, beds) and reasons to leave (maps).
- **Turns to fluff.** None of it touches villages. Sulfur caves are sparse in ore on purpose.
- **Take.** Mojang's direction supports AWAKEN's: hazard-rule caves and seat-like base objects are on-trend, and vanilla is leaving the village gap open.

### M24. Copper Age and Mounts of Mayhem [V]
- **Source:** "Java Edition 1.21.9" ("The Copper Age", 2025-09-30), https://minecraft.wiki/w/Java_Edition_1.21.9 . "Mounts of Mayhem" (1.21.11, 2025-12-09), https://minecraft.wiki/w/Mounts_of_Mayhem .
- **Mechanism.** Copper tools and armour between stone/leather and iron; copper pickaxes have stone-tier damage and speed and mine no more than stone. The copper golem (12 HP) sorts items from a copper chest into your chests: it searches 32 blocks horizontally and 8 vertically, visits up to 10 chests, idles 7 seconds when nothing matches, and oxidises into a statue unless waxed. Shelves hold three stacks. Spears (1.21.11) have a Lunge enchantment (3 levels) that propels the player forward at a hunger and durability cost.
- **Gets right.** A helper with a clear, readable AI (chest sorting) and a visible life cycle (oxidation) is a small but sticky loop.
- **Turns to fluff.** The wiki says copper tools "Cannot mine any more blocks than stone tools can": a new tier with no new reach is a tier without a job.
- **Take.** An ore tier needs a verb that the one below cannot do (M30, M35).

### M25. Caves & Cliffs: ore distribution and caves [V]
- **Source:** "Caves & Cliffs", minecraft.wiki, 1.17 on 2021-06-08, 1.18 on 2021-11-30, https://minecraft.wiki/w/Caves_%26_Cliffs . "Cave", https://minecraft.wiki/w/Cave . "Ore (feature)", https://minecraft.wiki/w/Ore_(feature) . Numbers below read from the game's own data, `/Volumes/MEDIA/Awaken/.ref/assets/data/minecraft/worldgen/placed_feature/ore_*.json` and `feature/ore_*.json` (26.3). Tester feedback on 1.18, search excerpts of feedback.minecraft.net, 2021 [P].
- **Mechanism.** Ores moved to height distributions that peak by depth (the triangle shape), with raw ore drops from 1.17 so Fortune works on iron and gold. Deepslate begins around Y8 and is harder to mine. Caves became noise-built: cheese (large), spaghetti (long tubes), noodle (1 to 5 blocks wide), plus old carver caves and aquifers (cells 16 by 12 by 16; below Y-54 is always a lava layer).
- **Numbers (26.3 data).** Per-chunk tries, shape and range, vein size, air-exposure discard:
  - Coal: 20, trapezoid 0 to 192, size 17 (plus 30 uniform from 136 up).
  - Copper: 16, trapezoid -16 to 112, size 10 (large 20 in dripstone caves).
  - Iron: middle 10, trapezoid -24 to 56, size 9; small 10, uniform bottom to 72, size 4; upper 90, trapezoid 80 to 384, size 9.
  - Gold: 4, trapezoid -64 to 32, size 9, discard 0.5; lower 0 to 1 at -64 to -48.
  - Redstone: 4, uniform bottom to 15, size 8; lower 8, trapezoid bottom -32 to +32.
  - Lapis: 2, trapezoid -32 to 32, size 7; buried 4, uniform bottom to 64, discard 1.0.
  - Diamond: normal 7, trapezoid, size 4, discard 0.5; medium 2, uniform -64 to -4, size 8, 0.5; large 1 chunk in 9, size 12, discard 0.7; buried 4, size 8, discard 1.0.
  - Emerald: 100, trapezoid -16 to 480, size 3 (mountain biomes).
- **Gets right.** Depth means something: each ore has a band and a peak, so the player reads the Y level.
- **Turns to fluff.** Early testers said the new caves left much of the underground empty and wanted ore discovery to be a real reason to enter caves [P]. Most ores are found by walking, not by reading.
- **Take.** The bands are fixed. Added jobs, not added ore, are what make a band matter (M1 miner levels).

### M26. Archaeology and Trail Ruins (1.20) [V]
- **Source:** "Archaeology", minecraft.wiki, https://minecraft.wiki/w/Archaeology . "Brush", https://minecraft.wiki/w/Brush . "Trail Ruins", https://minecraft.wiki/w/Trail_Ruins . "Trails & Tales", release 2023-06-07, https://minecraft.wiki/w/Trails_%26_Tales .
- **Mechanism.** A brush (feather, copper ingot, stick) works suspicious sand or gravel for 96 ticks (4.8 seconds) per block, with a slowdown, particles and pitched sound, costing 1 durability of 64. Each block pays one item. The wiki estimates roughly 4 to 8 brushes per common item and 7.5 to 45 for rarer ones. Trail ruins sit mostly underground with only the tower tip above ground, mix suspicious gravel into the fill so it looks like misplaced blocks, and give 4 armour trims, 7 pottery sherds and a disc. There are 23 sherd variants. Desert wells, desert pyramids and warm ocean ruins pay different loot. The wiki gives the update's design aim as self-expression and storytelling.
- **Gets right.** The act of digging is the reward: slow, tactile, with feedback at each step. The structure hides itself in plain sight.
- **Turns to grind.** Sherds are cosmetic; the tail of rare items needs dozens of brushes per find.
- **Take.** A 4.8 second act with sound and a possible reward is the vanilla standard for mining as a ritual (M34).

### M27. Trial Chambers (1.21) [V]
- **Source:** "Tricky Trials", minecraft.wiki, release 2024-06-13, https://minecraft.wiki/w/Tricky_Trials . "Trial Chambers", https://minecraft.wiki/w/Trial_Chambers .
- **Mechanism.** A large tuff-and-copper structure at Y-40 to -20, most rooms between -20 and 0, one per 34 by 34 chunk region. Trial spawners wave-spawn mobs; clearing one ejects rewards; vaults pay once per player; an ominous bottle upgrades spawners and unlocks an ominous vault (heavy core for the mace). The breeze fires wind charges that knock players back; the mace turns a fall into a smash attack; the crafter and copper bulb arrive with it.
- **Gets right.** A repeatable mid-game dive with per-player loot and a movement toy (wind charge, mace smash).
- **Turns to fluff.** The wiki records no reception data. It is a combat set piece, not a place to live.
- **Take.** Wind charge knockback and the mace's fall smash show Mojang using launch-and-fall as underground play.

---

# C. Mining, ores and tools

### M28. Create (and Create Deco) [V]
- **Source:** "Create", CurseForge, 216.9M downloads, upd 2026-04-21 (6.0.10 for 1.21.1), https://www.curseforge.com/minecraft/mc-mods/create ; Modrinth 28,359,252 downloads, https://modrinth.com/project/create . "Create Deco", Modrinth, upd 2026-04-18, 8,118,659 downloads, https://modrinth.com/project/create-deco . "Create: Simple Ore Doubling", CurseForge, upd 2026-01-11, 384K downloads, https://www.curseforge.com/minecraft/mc-mods/create-simple-ore-doubling . ModDex reviews, 2026, https://moddex.gg/mod/create .
- **Mechanism.** Gameplay is contraptions of animated components, not menus, taught in-game by Ponder. Ore processing: per an add-on page, base Create's crushing wheels guarantee a double only for deepslate raw ore blocks, and other ores average 1.75x. The add-on shows how far players push this: a lava press doubles, a heated press triples, washing slag returns a nugget (7%) or an experience nugget (35%). Create Deco builds industrial decor in six materials (andesite, zinc, iron, industrial iron, copper, brass) and has shipping containers in 16 colours.
- **Gets right.** Ore becomes a flow you can watch: raw ore in, crushed ore out, a wheel turning. Decor in the same materials ties looks to the machines. ModDex score 4.8 from 36 ratings, with one reviewer saying "Install just Create and let your ingenuity run wild."
- **Turns to grind / overwhelm.** Reviewers note performance drops with big factories, logistics harder than pipe mods, an exploitable stress system, and that solo players must invent their own goals.
- **Take.** Show ore moving. A processing step the player can see and hear is worth more than a multiplier.

### M29. Mekanism [V]
- **Source:** "Mekanism", Modrinth, pub 2022-05-27, upd 2026-04-10, 3,988,600 downloads, https://modrinth.com/project/mekanism . Ore Processing, Mekanism wiki, https://wiki.aidancbrady.com/wiki/Ore_Processing . ModDex reviews, 2024 to 2026, https://moddex.gg/mod/mekanism .
- **Mechanism.** Smelting gives 1 ingot per ore. Enrichment gives x2. Purification with oxygen gives x3 (silk-touched ore) or x2 (raw). Chemical injection with hydrogen chloride gives x4. Dissolution, washing and crystallising with sulfuric acid give x5. Sulfuric acid itself starts from gunpowder, sulfur, sulfur dioxide and sulfur trioxide.
- **Gets right.** A clear ladder: each rung is a machine and a resource, and the payoff is concrete (1 ore becomes 5 ingots).
- **Turns to grind / overwhelm.** ModDex 4.4 from 10 ratings, but reviewers call the number of gases and fluids overwhelming, say they got bored, and say third-party ore compatibility can take 1 to 2 hours of KubeJS work.
- **Take.** A "why mine" loop works when the first rung is cheap. The 5x rung is a chemistry plant; keep AWAKEN to two rungs.

### M30. Tinkers' Construct [V]
- **Source:** "Tinkers' Construct", CurseForge, upd 2026-09-22, 195.5M downloads, https://www.curseforge.com/minecraft/mc-mods/tinkers-construct . Modrinth, upd 2026-09-22, 4,046,484, https://modrinth.com/project/tinkers-construct . ModDex, 17 ratings (4.4), 7 written, 2023 to 2026, https://moddex.gg/mod/tinkers-construct . Material and trait description: search excerpts of wiki pages [P].
- **Mechanism.** Tools are built from parts (head, handle, sometimes a binding). The material of each part sets stats and adds a trait; the head sets mining speed, mining level, durability and attack. A smeltery melts ore into molten metal that is cast into parts. Slime islands, slimy geodes and Nether cobalt ore give reasons to travel. Modifiers replace vanilla enchanting.
- **Gets right.** Every ore, every drop and every hammered alloy is a possible part, so nothing in the ground is dead. Players name and keep their tools.
- **Turns to grind / overwhelm.** Reviewers praise the depth but flag balance (alloys and ore multiplication make the foundry overpowered), complexity (some systems depend on wikis or videos), and a mid-to-late game that feels limited once mending moss removes repair. One review title: "Fun but oddly balanced and annoying to configure".
- **Take.** Material-as-trait is the mechanism that makes every ore matter. Keep parts to 2 or 3 and one trait per material.

### M31. Silent Gear and vanilla-hook tool upgrades [V]
- **Source:** "Silent Gear", Modrinth, pub 2023-06-09, upd 2026-10-18, 517,083 downloads, https://modrinth.com/project/silent-gear . ModDex review (staff), June 2026, https://moddex.gg/mod/silent-gear . "Better Pickaxe Trims", Modrinth, upd 2026-05-01, 526,425, https://modrinth.com/project/better-pickaxe-trims . "Tough Environment", Modrinth, upd 2026-09-12, 131,089, https://modrinth.com/project/tough-environment .
- **Mechanism.** Silent Gear builds each item from a main part, a rod, a cord or binding, with materials graded E to MAX in a grader that consumes catalysts, compound materials from up to four inputs, reusable blueprints against consumed templates, and coatings applied at a smithing table. Better Pickaxe Trims adds a trim template that upgrades a pickaxe's durability, XP and mining speed (the listing; I did not read how it hooks vanilla). Tough Environment makes tool progression harder (a better tool to mine iron).
- **Gets right.** Silent Gear is data-driven and, in a staff reviewer's title, "Simple but effective modular tools". Its coatings use the smithing table the player already knows.
- **Turns to fluff.** The reviewer says presentation is the weak spot and balance suffers beside overpowered modded high-tier materials.
- **Take.** A familiar station (the smithing table) beats a new multiblock.

### M32. Deposits and prospecting: Geolosys, TerraFirmaCraft, detectors, Create Ore Excavation, Minerally [V]
- **Source:** "Geolosys", Modrinth, listing upd 2026-10-25, 23,498 downloads, https://modrinth.com/project/geolosys . "TerraFirmaCraft", Modrinth, pub 2023-09-24, upd 2026-10-20, 760,604, https://modrinth.com/project/terrafirmacraft . "Precision Prospecting", Modrinth, upd 2026-08-17, 46,925, https://modrinth.com/project/precision-prospecting . "Balanced Ore Detector", upd 2026-08-03, 17,825, https://modrinth.com/project/oredetector . "Create Ore Excavation", pub 2022-07-24, upd 2026-09-27, 3,678,212, https://modrinth.com/project/create-ore-excavation . "Minerally", upd 2026-06-08, 1,683,479, https://modrinth.com/project/minerally . "EMI Ores", upd 2026-06-15, 8,151,999, https://modrinth.com/project/emi-ores . "X Ray (no cheats)", upd 2026-08-14, 922,876, https://modrinth.com/project/x_ray .
- **Mechanism.** Geolosys reshapes ore into data-driven deposits (dense, layered) found with prospecting, with a field manual; its page says updates stopped in November 2022 and asks for a maintainer. TerraFirmaCraft has rock layers and sparse, large deposits; its default Prospector's Pick is named on its add-on's page. Precision Prospecting adds a Prospector's Hammer (short range), a Drill (scans in the direction faced) and a Mineral Prospector (non-metals only). Balanced Ore Detector is echo location: tap a surface and three sounds play; volume and delay give distance, pitch and particles hint at the block; focused versions are narrow and cost more; durability is limited. Create Ore Excavation hides infinite veins, at most one per chunk, found with a Vein Finder and mined with a drill on rotational force. Minerally adds ore piles and crystals that need several hits.
- **Gets right.** Finding ore becomes a skill with feedback (sound, direction, range). Deposits are places, not noise.
- **Turns to fluff.** The sheer size of EMI Ores (8.2M) and x-ray (over 1.4M across two mods) says that without a read-the-ground tool, players cheat or look it up. Geolosys is stalled.
- **Take.** Give the player a way to read the ground that is itself fun (echo, sound), before they reach for a lookup.

### M33. Vein mining: VeinMiner, FTB Ultimine, Crackt [V]
- **Source:** "VeinMiner", Modrinth, pub 2023-05-24, upd 2026-10-20, about 103M downloads, https://modrinth.com/project/veinminer . "FTB Ultimine", CurseForge, upd 2026-10-16, 119.7M, https://www.curseforge.com/minecraft/mc-mods/ftb-ultimine-forge . "Vein Mining" (enchantment), 9,884,168, https://modrinth.com/project/vein-mining . "Crackt", Modrinth, pub 2025-11-20, upd 2026-09-17, 5,494, https://modrinth.com/project/crackt . "Ultimine Unchained", upd 2026-10-20, 10,354, https://modrinth.com/project/ultimine-unchained .
- **Mechanism.** One break mines a connected vein (VeinMiner v2 has cooldown, max chain and durability settings; its page warns big veins lag). Ultimine selects shapes with a key and outlines the blocks. Crackt makes the block shrink with each break and the whole vein crumble when it is worn down, up to 24 breaks for veins that scale to 128 ores; progress persists, players can share a vein, and durability and enchantments count as if hand-mined. Ultimine Unchained caps durability cost per operation.
- **Gets right.** The size of the numbers (about 223M across two) is the clearest sign of mining boredom: the repeated identical break, not the walking, is the tedium.
- **Turns to fluff.** Mass break deletes the act. Crackt's page frames the answer as a fair, vanilla-like middle path.
- **Take.** Keep the act, change its shape: the vein, not the block, is the unit.

### M34. Mining as an act: Cobblemon Underground Mining Minigame, the brush, animation packs [V]
- **Source:** "Cobblemon: Underground Mining Minigame", Modrinth, pub 2026-01-12, upd 2026-03-03, 685,134 downloads, https://modrinth.com/project/cobblemon-underground-mining-minigame . "Mining & Placing Animations", pub 2025-07-22, upd 2026-05-02, 167,192, https://modrinth.com/project/mining_and_placing_animations . "Mine Treasure", upd 2026-09-19, 785,246, https://modrinth.com/project/mine-treasure . "Block Motion" (springy mine and place animations), 3,746, https://modrinth.com/project/blockmotion . Brush: M26.
- **Mechanism.** The minigame puts sparkling spots in stone, deepslate, sandstone and netherrack. Interacting starts an excavation of a hidden wall of layers. A precise tool clears single tiles; a heavy tool clears a wider area at higher cost. Each action lowers stability, and at zero the wall collapses and the remaining items are lost. Version 1.3 added large, very large and huge deposits; 1.4 added a remastered soundtrack. Mining & Placing Animations plays keyframed block animations through a resource pack. Mine Treasure adds five treasure tiers (common to mythical), 23 biome loot tables and progression-locked strong items.
- **Gets right.** A hidden-layer puzzle with a risk (collapse) turns a break into a decision and a payoff. It sits on a vanilla block and takes seconds.
- **Turns to fluff.** Animations and luck tables decorate the break without changing it. Mine Treasure is random loot, a per-block lottery.
- **Take.** The cinematic mining model: a rare spot, a short read-the-wall act, a loss if you are greedy.

### M35. New ores that earn a place: Oreganized, Mythic Metals, Spelunkery [V]
- **Source:** "Oreganized", Modrinth, pub 2022-12-14, upd 2026-09-18, 316,809 downloads, https://modrinth.com/project/oreganized . "Mythic Metals", pub 2020-12-02, upd 2026-10-18, 1,266,453, https://modrinth.com/project/mythicmetals . "Spelunkery", M43.
- **Mechanism.** Oreganized adds lead and silver and the alloy electrum, and was inspired by copper's arrival in 1.17. Silver makes mirrors, scribes and gargoyles; lead makes decoration, melting and Lead Bolts as a new projectile. Mythic Metals adds many ores, each in its own part of the underground, plus an Alloy Forge, tools and armour.
- **Gets right.** Oreganized gives each metal a verb in the world (a bolt, a mirror), not a stat. Placement matters in Mythic Metals: each ore has its own place underground.
- **Turns to fluff.** Mythic Metals is a catalogue (its page boasts swords stronger than netherite) with no page-level story of why. Copper tools (M24) show the failure case.
- **Take.** A new ore earns a place if it unlocks a verb and has its own address. Otherwise it is a stat.

### M36. Mining dimensions [V; threads P]
- **Source:** Modrinth listings read 2026-10-26: "Advanced Mining Dimension" 33,110 downloads, https://modrinth.com/project/advanced-mining-dimension ; "Every Mining Dimension" 30,777, https://modrinth.com/project/every-mining-dimension ; "Create: Dimension, Steamworks Realm" 30,279, https://modrinth.com/project/create-dimension-steamworks-realm ; "Mining Dimension" 5,257, https://modrinth.com/project/miningdimension . Threads (search excerpts): StoneBlock 2 reset discussion, Mine Your Mind forum, 2019 https://mineyourmind.net/forum/threads/stoneblock-2-reset-of-mining-and-end-dimensions.32349/ ; ATM-8 issue #1434 https://github.com/AllTheMods/ATM-8/issues/1434 ; FTB-Modpack-Issues #5291 https://github.com/FTBTeam/FTB-Modpack-Issues/issues/5291 .
- **Mechanism.** A separate dimension of caves and ore that can be reset. The smallest, "Mining Dimension", pitches itself as a place to move quarry machines so the overworld is not wasted. One variant's author warns it defines no ores or gameplay itself [P].
- **Gets right.** It protects the overworld from machines and gives a safe place to strip-mine.
- **Turns to fluff.** All top results stay under 35K downloads, against 11.6M for Alex's Caves on Modrinth. Resets destroy player setups (a StoneBlock 2 thread debated this, with an admin saying the reset exists because the island system cannot have a world border [P]). Bug reports include a world-bricking entry and a copy with no ores.
- **Take.** Mining that lives apart from the world has no place to be memorable. Put the depth under the places people live.

---

# D. Caves and structures

### M37. Alex's Caves [V]
- **Source:** "Alex's Caves", Modrinth, pub 2023-11-04, upd 2024-10-26, 11,559,742 downloads, 1.20.1 only, https://modrinth.com/project/alexs-caves . CurseForge, upd 2024-10-26 (2.0.2), 34.9M, https://www.curseforge.com/minecraft/mc-mods/alexs-caves . Wiki: Magnetic Caves, Toxic Caves, Abyssal Chasm, https://alexscaves.wiki.gg/wiki/Magnetic_Caves , .../Toxic_Caves , .../Abyssal_Chasm . ModDex review, 2026-06-08, https://moddex.gg/mod/alexs-caves .
- **Mechanism.** Six rare cave biomes (Magnetic, Primordial, Toxic, Abyssal Chasm, Forlorn Hollows, Candy Cavity). The way in is an Underground Cabin whose barrels hold cave tablets and a guidebook. Each biome sets its own particles, fog, brightness and light colour, its own blocks, items, mobs and rule. Magnetic: Galena stone and neodymium crystals; scarlet attracts iron and netherite gear, azure repels; metal boots let you walk on any face of scarlet neodymium; Tesla Bulbs zap; Galena clusters float. Toxic: Radrock, acid that corrodes armour then dissolves creatures, vents with toxic air, radiation from picking up waste, uranium only here, a hazmat set. Abyssal: under non-warm oceans, hundreds of blocks of water, light barely reaches, Muck slows creatures, a diving suit and submarine.
- **Gets right.** Each biome changes how you move, what you wear and what you carry, and has its own resource only here. The magnetic boots are a movement tool born inside a cave.
- **Turns to fluff / overwhelm.** The reviewer scores it 4.5 ("But when it does work, it is a blast") but notes the look clashes with most shaders, mob spawns misbehave on servers, and the rare biomes mean a multi-thousand-block trip. The Modrinth listing shows 1.20.1 only.
- **Take.** A biome is a rule, a resource and a tool, not a palette. Test the look with Iris and Sodium (AWAKEN's Lens 2) from day one.

### M38. Cave biome sets: Galosphere, YUNG's Cave Biomes, Terralith, Tectonic, Profundis [V]
- **Source:** "Galosphere", Modrinth, pub 2022-11-11, upd 2026-06-23, 7,271,610 downloads, https://modrinth.com/project/galosphere . "YUNG's Cave Biomes", pub 2024-09-30, upd 2026-08-19, 4,715,185, https://modrinth.com/project/yungs-cave-biomes . "Terralith", pub 2022-11-13, upd 2026-09-27, 25,091,274, https://modrinth.com/project/terralith . "Tectonic", CurseForge, upd 2026-10-18, 34.6M, https://www.curseforge.com/minecraft/mc-mods/tectonic . "Profundis", upd 2024-12-26, 1,507,173, https://modrinth.com/project/profundis .
- **Mechanism.** Galosphere has Crystal Canyons (allurite and lumiere crystals), Lichen Caves, Pink Salt Caves with an ancient shrine, and Forgotten Ruins with suspicious blocks to brush and moles that offer side quests. YUNG's Cave Biomes adds Frosted Caves and Lost Caves (sandstorm fog, a Sand Snapper). Terralith adds underground jungle, infested, fungal and Frostfire caves ("a dangerous sister to the Deep Dark") and can replace stone blobs with stone-type cave biomes. Tectonic carves rivers that continue underground where mountains are too tall for surface rivers, and lava tunnels deep down. Profundis adds nine cave biomes.
- **Gets right.** Biome identity by block, light and creature, and hooks to other systems (brushing, quests).
- **Turns to fluff / risk.** YUNG's page lists Sodium, Iris/OptiFine and Distant Horizons fog bugs, so fog-driven caves break on common render mods.
- **Take.** Mojang is also adding cave biomes (M23); the gap is rules and resources per biome (M37).

### M39. Cave generators: YUNG's Better Caves, WF's Cave Overhaul, Speleogenesis, Caves & Canyons [V]
- **Source:** "YUNG's Better Caves", Modrinth, pub 2025-08-29, upd 2026-09-04, 1,601,355 downloads, https://modrinth.com/project/yungs-better-caves . "WF's Cave Overhaul", pub 2023-12-16, upd 2024-12-12, 264,483, https://modrinth.com/project/wfs-cave-overhaul . "Speleogenesis Overhaul", pub 2026-08-06, 100, https://modrinth.com/project/speleogenesis-overhaul . "Caves & Canyons", pub 2023-10-25, upd 2026-07-31, 997,253, https://modrinth.com/project/caves__canyons . Also "Cave Dust" 7,702,558 https://modrinth.com/project/cave-dust and "True Darkness" 852,139 https://modrinth.com/project/true-darkness .
- **Mechanism.** Better Caves adds lava caverns, lakes and rivers, flooded caverns, small near the surface and more dramatic at depth, with per-dimension settings. WF's Cave Overhaul replaces noise caves with ones that have underground rivers, waterfalls that can drop you into lava and labyrinthine passages, with domain warping that gets stranger with depth. Speleogenesis (a new Fabric 1.20.1 experiment, 100 downloads) builds a Voronoi tunnel network guaranteed to connect, spherical mega-caverns 60 to 200 blocks wide with flat floors, and vertical rifts hundreds of blocks tall, Y-512 to 511. Caves & Canyons keeps the surface intact and gives dry-land mineshafts a spiral access with timber supports.
- **Gets right.** Connectivity, scale change and verticality are the three levers; flat-floor mega-caverns even leave room to build.
- **Turns to fluff.** Generators alone give shape but no content: tunnels with nothing in them. The large downloads for Cave Dust and True Darkness show players patch the mood, not the layout.
- **Take.** Guaranteed connection plus vertical rifts is what a movement system needs (M44).

### M40. YUNG's Better Mineshafts, Dungeons and Strongholds [V]
- **Source:** "YUNG's Better Mineshafts", Modrinth, pub 2022-11-28, upd 2026-09-10, 21,593,459 downloads, https://modrinth.com/project/yungs-better-mineshafts . "YUNG's Better Dungeons", 23,370,423, https://modrinth.com/project/yungs-better-dungeons . "YUNG's Better Strongholds", 19,429,434, https://modrinth.com/project/yungs-better-strongholds .
- **Mechanism.** Mineshafts: 13 biome variants (one a rare mushroom shaft), web-like tunnel layouts in place of straight corridors, abandoned workstations and workstation cellars, ore deposits at the ends of some tunnels (coal, iron, redstone, gold, lapis, emerald, very rare diamond), abandoned miners' outposts, surface openings. Dungeons: catacombs, Fortresses of the Undead, spider caves. Strongholds: 15+ rooms, traps, hidden areas. All server-side.
- **Gets right.** A tunnel with a destination at the end (an ore deposit, an outpost) is a reason to follow it.
- **Turns to fluff.** Dungeons and strongholds are rooms with chests; the pages describe no new verb.
- **Take.** Put the payoff at the end of a long tunnel and make mine-site variants match the biome.

### M41. Structure packs and spacing: Dungeons and Taverns, Explorify, Sparse Structures, Lithostitched [V]
- **Source:** "Dungeons and Taverns", Modrinth, pub 2023-01-18, upd 2026-10-01, 22,425,517 downloads, https://modrinth.com/project/dungeons-and-taverns . "Explorify", pub 2022-11-06, upd 2026-05-13, 10,094,875, https://modrinth.com/project/explorify . "Sparse Structures", pub 2023-09-07, upd 2026-10-03, 8,676,330, https://modrinth.com/project/sparsestructures . "Lithostitched", pub 2023-08-03, upd 2026-10-14, 25,905,475, https://modrinth.com/project/lithostitched . "Dungeons and Taverns Stronghold Overhaul", 3,188,013, https://modrinth.com/project/dungeons-and-taverns-stronghold-overhaul .
- **Mechanism.** Dungeons and Taverns lists about 40 structures and ties them together: villagers in taverns level into apprentice cartographers who sell maps to other structures, and custom enchantments are found only in structure loot and sold by "secret librarians". Its Stronghold Overhaul has 417 rooms and 5 substructure types, and its listing warns it may freeze world generation the first time. Explorify aims for structures that "feel interesting yet familiar". Sparse Structures multiplies structure spacing (default factor 2) and fixes a `/locate` bug; its 8.7M downloads suggest (my inference) that packs often stack too many structures. Lithostitched is a data-driven library with guaranteed structure elements, per-structure music and spawn conditions such as flatness checks.
- **Gets right.** Cartographers who sell maps to structures tie villages to exploration. Spacing tools admit the problem: too many finds make no find special.
- **Turns to fluff / overwhelm.** 417 rooms is generation cost, not depth. Kitchen-sink packs stack structure mods (M58).
- **Take.** Spread, do not stack. Let villages sell directions (as Mojang's cartographer maps do, M22).

### M42. Deeper and Darker, The Undergarden [V]
- **Source:** "Deeper and Darker", Modrinth, pub 2022-08-15, upd 2026-07-11, 16,576,141 downloads, https://modrinth.com/project/deeperdarker . "The Undergarden", pub 2023-11-06, upd 2026-08-24, 3,123,362, https://modrinth.com/project/the-undergarden .
- **Mechanism.** Deeper and Darker expands the Deep Dark with an Otherside dimension, entered after defeating the Warden and using its heart, 100+ blocks, a Warden set and 8 mobs; version 2.0 is in alpha. The Undergarden is a dimension below bedrock with 15 biomes, 10+ entities and ores with special tool and weapon properties, entered with a Catalyst and a stone-brick frame.
- **Gets right.** A boss-gated descent with its own ores and tools.
- **Turns to fluff.** Both are dimensions, so they sit apart from the world (M36).
- **Take.** Boss-gated depth with a signature ore is the pattern; AWAKEN's Descent is its own version.

### M43. Spelunkery [V]
- **Source:** "Spelunkery", Modrinth, pub 2023-01-07, upd 2026-09-08, 2,481,719 downloads, https://modrinth.com/project/spelunkery . CurseForge, last file 2025-01-25, 7.4M downloads, https://www.curseforge.com/minecraft/mc-mods/spelunkery .
- **Mechanism.** The stated goals are tweaked material progression, underground landmarks and ore variations, and tools. Redstone ores drop Rough Cinnabar, polished on a grindstone into 1 to 3 redstone dust; large cinnabar veins sit at the bottom of the world. Raw diamond blocks generate near lava pools. Glowsticks are made from phosphor fungi. Rope ladders hang in abandoned mineshafts. I did not find the tuning-fork or portal-fluid mechanics on any page [U].
- **Gets right.** An ore changes its processing step (a grindstone) and a rope ladder is a portable, visible way down.
- **Turns to fluff.** Its own pages leave most mechanics to a wiki I could not read.
- **Take.** Rope ladders in mines are the cheapest vertical toy.

### M44. What makes a cave memorable and what makes a noise cave skippable [synthesis; P]
- **Sources:** M25, M37 to M43, M45 to M47; tester excerpts from feedback.minecraft.net on the 1.18 experimental snapshots (2021, via search, not fetched) [P].
- **What the evidence says.** Memorable caves have: (1) a rule that changes movement, sight or inventory (magnetic boots, acid corroding armour, muck slowing creatures, sulfur gas giving nausea, ice icicles that fall); (2) a resource that only exists there; (3) a signature light, fog and sound set (Alex's Caves sets particles, fog, brightness and light colour per biome); (4) a way in that is a story (an Underground Cabin, a surface sulfur spring above sulfur caves, spiral shafts); (5) a vertical or connected route, not a dead-end pocket (Speleogenesis, Caves & Canyons, WF's rivers and waterfalls).
- **What makes a cave skippable.** A big noise cave with nothing in it (testers on 1.18 said much of the underground felt empty [P]); one palette; a lookup or x-ray as the faster way to find ore (M32).
- **Take.** Five tests for any cave: a rule, a resource, a sensory set, a story entrance, a route.

---

# E. Movement

### M45. ParCool! [V]
- **Source:** "ParCool!", CurseForge, upd 2026-10-15, 22.3M downloads, https://www.curseforge.com/minecraft/mc-mods/parcool . Modrinth, pub 2024-01-09, upd 2026-10-16, 3,446,006, https://modrinth.com/project/parcool . GitHub alRex-U/ParCool (137 stars, inspired by SmartMoving), https://github.com/alRex-U/ParCool .
- **Mechanism.** Breakfall, Hang On, Charge Jump, Crawl, Dodge, Dive, Fast Run, Trick Jump, Horizontal Wall Run and Vault, plus Grapple, Zipline and equipment. Since v4.0 there is a skill tree that can be disabled. Stamina is not described on the page; optional mods (Feathers, Paragliders, Epic Fight) can act as a stamina system. Config on client and server; some settings in game by Alt+P.
- **Gets right.** 22M downloads says players want body-skill movement. That it added a skill tree in v4.0 suggests progression gating suits movement.
- **Turns to fluff / risk.** The page does not document limits or vanilla balance, and both sides must have it installed. Wall runs in the base mod are horizontal.
- **Take.** Ship a move set with a progression tree and stamina; it reads as a character, not a cheat.

### M46. Grappling Hook Mod (Yyon) [V]
- **Source:** "Grappling Hook Mod", Modrinth, pub 2021-11-12, upd 2026-10-01, 766,882 downloads, https://modrinth.com/project/grappling-hook-mod . GitHub yyon/grapplemod (233 commits, 38 stars, GPL-3.0), https://github.com/yyon/grapplemod .
- **Mechanism.** The page says the mod is "aimed at getting around large builds like cities". Version 12 added a rocket upgrade (fuel regenerates automatically), a sticky rope, and boot enchantments for wallrunning, sliding and double jump. Double hooks have separate keybinds. Hooks are craftable; config is in game or `grapplingHook.cfg`. Rope length, motors and balance are on the wiki, which I could not read [U].
- **Gets right.** Grapple plus boot enchantments for wall run, slide and double jump is the exact AWAKEN set (rope, wall run, slide) already in a shipping mod.
- **Turns to fluff.** Modrinth lists 32 grappling-hook mods, most with small counts, so the field is crowded. The rope-length, motor and balance rules are unread [U].
- **Take.** Put wall run, slide and double jump on boots and keep the rope separate.

### M47. Ropes and climbing: Climbable Ropes, Better Climbing, Ninja Moves, Abseil [V]
- **Source:** "Climbable Ropes for Create Aeronautics", Modrinth, pub 2026-04-29, upd 2026-10-19, 332,212 downloads, https://modrinth.com/project/create-aeronautics-climbable-rope . "Better Climbing", upd 2026-05-20, 3,563,959, https://modrinth.com/project/better-climbing . "Ninja Moves", pub 2024-05-17, upd 2026-06-30, 16,746, https://modrinth.com/project/ninja-moves . "Abseil", 6,611, https://modrinth.com/project/abseil . "shrimpLiquid's Climbing Pick", 2,481, https://modrinth.com/project/shrimpliquids-climbing-pick .
- **Mechanism.** Climbable ropes: right-click an empty-handed rope, W/S climb, Ctrl+S slide down with smooth acceleration and a coast to stop, the mouse swings you around the rope, Space jumps off and at the top mantles onto the block above, Shift releases, and plunger ropes zipline or slide. Better Climbing ramps ladder speed up over about 3 seconds and lets you jump while climbing. Ninja Moves is a datapack with rolls, wall jumps and wall runs; its author says it does not suit survival play.
- **Gets right.** A rope is a state with grab, climb, slide, swing, jump-off and mantle: each is a verb, each is readable.
- **Turns to fluff.** Ladder speed-ups only fix tedium. The Ninja Moves note flags the balance risk of free parkour in survival.
- **Take.** The rope state machine (grab, climb, slide, swing, release, mantle) is the cave vertical toolkit.

---

# F. Bases that read what you build

### M48. Villager Comfort (and Villager Inspector) [V]
- **Source:** "Villager Comfort", ModDex, created 2022-10-23, last updated 2022-11-02, 7.8M downloads, 303 modpacks, Forge 1.18 to 1.19, https://moddex.gg/mod/villager-comfort . "Villager Comfort Continued", ModDex, created 2026-02-26, last updated 2026-03-03, about 7k downloads, NeoForge 1.21, https://moddex.gg/mod/villager-comfort-continued . "Villager Inspector" (companion), https://moddex.gg/mod/villager-inspector [P].
- **Mechanism.** A comfort stat per villager changes trade prices. Bedroom: larger helps, window light helps, sharing a room with other sleepers hurts, a workstation in the bedroom hurts, more distance between bed and work helps. Workplace: spacious, bright, uncrowded. Lifestyle: a minute outdoors in the day, rest over several nights, each day without a monster attack raises it. Prices: a very uncomfortable villager can charge close to a stack of emeralds, a very comfortable one a couple. In Continued, right-clicking with paper shows the breakdown and decor is listed as a future factor.
- **Gets right.** It reads the player's actual build in the villagers' own terms (space, light, privacy) and pays back through prices, a number the player already cares about. The inspect gesture explains the score.
- **Turns to fluff.** The original calls itself "extremely early in development" and has been quiet since 2022; Continued has 7k downloads. It ignores decor and comfort objects.
- **Take.** Read space, light and privacy; pay in prices; explain on request.

### M49. Confluence: Otherworld housing (a Terraria-style system in Minecraft) [V]
- **Source:** "Housing", Confluence: Otherworld wiki, wiki.gg, undated, read 2026-10-26, https://confluenceotherworld.wiki.gg/wiki/Housing . Mod listing, Modrinth, upd 2026-10-13, 447,194 downloads, https://modrinth.com/project/confluence . Terraria rule details: search excerpt of a guide [P].
- **Mechanism.** A valid house has a light block (sunlight does not count), a chair or bed, and a table or crafting table, in an interior of at least 5 by 2 by 5 along one axis (irregular cavities can qualify). One valid house is enough for every NPC. A housing detector toggles add/delete, and from v1.2 NPCs return to their houses at night. Some NPCs need extra conditions (a Merchant, an Arms Dealer). Each 16 by 16 chunk has its own NPC set, up to four at once, and invitations spawn duplicates. In Terraria itself the check has four categories and a size band of about 60 to 750 tiles, with status messages that say why a house is invalid [P]; players there report a house that passes and then fails after a later change.
- **Gets right.** A short, legible rule (light, seat, table, size) that a player can satisfy in a minute and that grows to unlock people.
- **Turns to fluff.** Chairs and tables must carry house tags (v1.2), so modded furniture needs tagging.
- **Take.** Keep the rule list to four things the player can see, and name the missing one in plain words.

### M50. Who recognises a player's own building, and who does not [V]
- **Source:** MineColonies schematics page (M1); MCA Reborn wiki (M4); Live Villages (M5); Villager Comfort (M48); Confluence (M49); Mojang Village page (M19).
- **Mechanism.** MineColonies: functional buildings must be built by a Builder from a schematic; you may scan your own build into a style pack, but the colony does not recognise a hut you hand-build. MCA: register a new building with a Blueprint. Live Villages: it renovates damaged vanilla buildings. Villager Comfort and Confluence: they read the space, light, seats and tables the player placed. Mojang: beds, job sites and bells define a village.
- **Gets right.** Object-based recognition (beds, workstations, light, seat) is what lasts.
- **Turns to fluff.** Full-building recognition by shape is brittle; every mod that attempts it falls back to a tool or a list of objects.
- **Take.** Recognise by contents, not by shape; do not demand a builder.

### M51. Comfort and environment: Comforts, Serene Seasons, Tough As Nails [V]
- **Source:** "Comforts", Modrinth, pub 2023-02-03, upd 2026-06-22, 24,537,695 downloads, https://modrinth.com/project/comforts . "Serene Seasons", pub 2023-11-19, upd 2026-09-21, 7,758,981, https://modrinth.com/project/serene-seasons . "Tough As Nails", upd 2026-10-17, 2,202,672, https://modrinth.com/project/tough-as-nails . ModDex review of Tough As Nails, 2024-06-10, https://moddex.gg/mod/tough-as-nails/reviews/420 .
- **Mechanism.** Comforts adds sleeping bags and hammocks without setting a spawn; its options include a Well-Rested mechanic, a sleepiness factor and bag break chance. Serene Seasons changes colours and temperatures and affects crop growth. Tough As Nails adds body temperature and thirst.
- **Gets right.** Comforts is a 24.5M-download answer to the wish to sleep anywhere without losing your home spawn.
- **Turns to grind.** A 100-hour reviewer says "Barely any modpack actually puts effort into testing and configuring TAN"; the defaults caused heatstroke and hypothermia, and players bypass temperature with immunity baubles, which makes it moot.
- **Take.** Ship tuned defaults. An environment system is a base system only if the base is how you beat it.

---

# G. Smaller loops

### M52. Fishing: Tide 2, Stardew Fishing, Fishing Real [V]
- **Source:** "Tide 2", Modrinth, pub 2024-06-12, upd 2026-08-03, 1,636,761 downloads, https://modrinth.com/project/tide . "Stardew Fishing", pub 2024-07-20, upd 2026-07-16, 1,180,998, https://modrinth.com/project/stardew-fishing . "Fishing Real", upd 2026-05-28, 3,560,890, https://modrinth.com/project/fishing-real .
- **Mechanism.** Tide adds 100+ fish, rods with bait slots, an Angling Table for hooks and lines, and a timing minigame (keep a bar in a coloured zone; each fish has its own movement and strength). A journal tracks completion, biggest catches and habitats, and found "fishy notes" hint where a fish lives. Stardew Fishing uses a bobber bar and a progress bar with datapack difficulty per fish; quality depends on accuracy. Fishing Real makes catches live entities.
- **Gets right.** A journal that tracks habitats and a note that points you somewhere turns fishing into exploring.
- **Turns to fluff.** Minigames risk becoming the same ten seconds every cast.
- **Take.** Fishing = a collection with a habitat clue; let the clue send you to a place.

### M53. Collecting and taming: Cobblemon [V]
- **Source:** "Cobblemon", Modrinth, pub 2022-11-06, upd 2026-10-25, 37,270,350 downloads, https://modrinth.com/project/cobblemon . Small taming mods for contrast: "Tameable Foxes" 107,915, https://modrinth.com/project/tameable-foxes .
- **Mechanism.** Over 800 Pokémon spawn in the world and can be caught; battles use the Showdown engine but you can move freely to build, mine or fight mobs; Alpha Pokémon are tougher with rewards; fossils come from suspicious gravel and sand and are revived in a machine; riding and pasturing. Its add-on Cobblemon Quests has 1.25M downloads and the Underground Mining Minigame has 685K (M34).
- **Gets right.** A collection with a spawn in the world gives every biome a reason to be visited.
- **Turns to fluff.** It is its own game on top of Minecraft.
- **Take.** A codex of creatures with biome and time conditions is the taming loop. My read: the small stand-alone taming mods (under 110K) suggest taming wants a larger system around it.

---

# H. Packs

### M54. Prominence II: Hasturian Era [V]
- **Source:** CurseForge, upd 2026-10-23 (v4.1.5hf), 13.2M downloads, https://www.curseforge.com/minecraft/modpacks/prominence-2-hasturian-era . ModDex, 3.2/5 from 12 reviews and 18 ratings, https://moddex.gg/modpack/prominence-2-hasturian-era .
- **Mechanism.** A custom talent tree (10 fates, 16 passives, 5 actives, 100 stat talents), two quest storylines, Mythic Challenges with random affixes and a timer (multiplayer gives up to 3 lives), and a capital city, the Kingdom of Vaaz, whose NPCs and content change as the campaign advances. The pack says it is built on custom content and its lore.
- **Gets right.** Reviewers praise custom items, music, resource pack and storyline, and a few note tech is optional. A city that changes with the story is the best-reviewed part.
- **Turns to grind / bloat.** ModDex reviews: grind and RNG relic drops, max gear in days, quests either overwhelming or poorly guided, tech mods that read as padding, ranged builds dominating. One review title: "Pack of all Trades Master of None".
- **Take.** A hub that changes with the campaign is worth copying. Padding mods with no tie to the story is the failure.

### M55. Better MC [FORGE] BMC4 [V; small sample]
- **Source:** CurseForge, latest file 2026-10-09 (v62hf), 18.8M downloads, https://www.curseforge.com/minecraft/modpacks/better-mc-forge-bmc4 . ModDex, 0.9/5 from 7 ratings (3 written), https://moddex.gg/modpack/better-mc-forge-bmc4 .
- **Mechanism.** "A Proper Vanilla+ Modpack", 350+ mods, 100+ quests, "thousands" of structures from When Dungeons Arise and AdoraBuild, reworked world generation with Biomes O' Plenty, Waystones, an Aether with villages, rideable dragons.
- **Gets right.** The three written reviews find the content good (world, structures, mobs), and one says it is easy for newer modded players.
- **Turns to bloat.** The reviews say it lacks focus (a kitchen sink), integration across 300+ mods is weak, documentation is poor, and updates are barely tested. Only 7 ratings, so weigh lightly.
- **Take.** "Vanilla+" with 350 mods is not vanilla. A pitch needs one identity (M62).

### M56. Vault Hunters Third Edition [V]
- **Source:** CurseForge, upd 2026-07-30 (3.21.7, Minecraft 1.18.2), 5.9M downloads, https://www.curseforge.com/minecraft/modpacks/vault-hunters-1-18-2 . ModDex, 4.3 from 6 ratings, 4 reviews from 2024, https://moddex.gg/modpack/vault-hunters-1-18-2 .
- **Mechanism.** The vault is a randomly generated dungeon; higher vault levels are harder and pay better. Progress through vault levels unlocks mods through research. You start with resources, craft an altar and Vault Rock into a Vault Crystal to open a portal. Collecting 25 artifacts leads to a final boss, then a "Greed" endgame. The listing mentions the base loop only as building a base and farms.
- **Gets right.** A reviewer says vault entry has a cost, a random mix of vanilla items, so resource gathering has purpose and farms matter. Another calls it a true roguelite (run, loot, die, restart) with good co-op.
- **Turns to grind.** The same reviewer notes the cost can be tedious. A June 2024 review: recipes keep needing vault gems that do not get easier, automation is gated behind vault gems, so basic items are farmed by hand, and "you don't feel like you made any major progress".
- **Take.** Link base to dungeon by a cost the base can pay. Do not gate the base's automation behind the dungeon's currency.

### M57. RLCraft (and Dregora) [V]
- **Source:** "RLCraft", CurseForge, last update 2023-06-27 (2.9.3, 1.12.2), 30.4M downloads, https://www.curseforge.com/minecraft/modpacks/rlcraft . "RLCraft Dregora", main file 2025-12-02, 2.08M, https://www.curseforge.com/minecraft/modpacks/rlcraft-dregora . ModDex, 2.9 from 24 ratings (16 reviews), https://moddex.gg/modpack/rlcraft .
- **Mechanism.** Forced Hard, spawns within 10,000 blocks, thirst and temperature, no healing from food (salves and bandages), spawners that destroy themselves after 10 kills, a Bloodmoon on about 1% of nights with quadrupled spawns, skill gating via Reskillable. Dregora adds a custom world generator with 300+ biomes and 300+ structures, persistent lore regions, 100+ lore books, a random-encounter entity system, and booby-trapped houses.
- **Gets right.** Replayability and a late-game power curve; players say it becomes memorable. Exploration is encouraged because ruins carry real loot.
- **Turns to grind.** Reviews: "Kinda unfair but one of the very best RPG packs out there" (a title); an unfair early game, grinding for enchants and XP, weak endgame, performance, and a librarian-trade grind.
- **Take.** Place danger and loot in ruins and shelters so exploring is the safety net. Keep the early game fair.

### M58. Craftoria [V]
- **Source:** CurseForge, upd 2026-09-13 (1.37.0), 2.98M downloads, https://www.curseforge.com/minecraft/modpacks/craftoria . ModDex, 3.5 from 10 ratings, 5 reviews, https://moddex.gg/modpack/craftoria .
- **Mechanism.** NeoForge 1.21.1, "500+ mods. 2,000+ quests", automation, magic, exploration, adventure and building pillars, dynamic seasons, a handcrafted endgame. Quests introduce mods gradually.
- **Gets right.** Reviewers praise quests, cross-mod integration, structures and exploration. One review title: "Well made kitchen sink with actual thought put into it".
- **Turns to bloat.** Performance, mod bloat (overlapping tech and structure mods), tech balance, a basic skill tree, and one reviewer objected to AI-written quest text.
- **Take.** A quest book that introduces one system at a time is welcomed. My view: 2,000 quests is more than a player can hold in mind.

### M59. Dawncraft [V; no independent review found]
- **Source:** CurseForge, last update 2025-11-20 (2.0.16_hf, 1.18.2 Forge), 10.6M downloads, https://www.curseforge.com/minecraft/modpacks/dawn-craft . A roundup mention, https://sportskeeda.com/minecraft/how-turn-minecraft-soulsborne-game [P].
- **Mechanism.** A Souls-like combat system, dozens of bosses with unique attack patterns, hundreds of new animals, monsters and structures, a custom quest line, a "unique reputation system", 200+ mods.
- **Gets right.** The listing's claims are focused: one genre (Souls), one combat feel, one quest line.
- **Turns to fluff.** These are the pack's own claims; I found no review text to test them.
- **Take.** A single genre promise is the strongest pitch in this list.

### M60. Create: Above and Beyond [V]
- **Source:** CurseForge, last file 2021-12-15 (v1.3), 2.73M downloads, https://www.curseforge.com/minecraft/modpacks/create-above-and-beyond . ModDex, 4.6 from 9 ratings, 4 reviews, https://moddex.gg/modpack/create-above-and-beyond .
- **Mechanism.** A challenge pack around Create toward space travel, "About fifty inventions lie between you and the moon", a slow early game that speeds up once the first components are automated, a coin-based trading economy, and a questing interface.
- **Gets right.** Reviewers praise the helpful quest book, deep automation and short chapters. "Linear progression at its finest" is one review title.
- **Turns to fluff.** It is old (last file 2021), with dated textures, and one dropped player found magic mods tacked on.
- **Take.** A linear spine with short chapters and a helpful book beats an open buffet.

### M61. Valhelsia 6 [V]
- **Source:** CurseForge, upd 2025-04-21 (6.2.3), 1.9M downloads, https://www.curseforge.com/minecraft/modpacks/valhelsia-6 . "Valhelsia Structures", Modrinth, upd 2025-10-05, 962,721, https://modrinth.com/project/valhelsia-structures .
- **Mechanism.** A building and decoration pack on 1.20.1 with Biomes O' Plenty, Galosphere, Deeper and Darker, Twilight Forest, Undergarden, Create, Ars Nouveau, Alex's Mobs, Farmer's Delight, Supplementaries and Valhelsia Furniture, with shaders. The listing describes no progression system or quests.
- **Gets right.** It treats the base and its furniture as the pack's centre.
- **Turns to fluff.** It does not say what the player is working toward.
- **Take.** Building packs sell looks. A base that reacts to the looks is the missing half.

### M62. What reviewers say makes a pack feel new, and what makes it feel bloated [synthesis from M54 to M61]
- **New:** one genre promise (Dawncraft's Souls-like, Vault Hunters' roguelite, Create: Above and Beyond's factory spine); a hub that changes with progress (Prominence II's Vaaz); a quest book that introduces one system at a time (Craftoria, Create: Above and Beyond); a cost that ties base to dungeon (Vault Hunters); ruins and shelters carrying real loot (RLCraft).
- **Bloated:** 300+ mods with weak integration (BMC4); tech mods with no tie to the setting (Prominence II); overlapping tech and structure mods (Craftoria); grind and RNG gating (Prominence II, RLCraft, Vault Hunters); an unfair early game (RLCraft); gates that block the base from automating (Vault Hunters); defaults left harsh (Tough As Nails, M51).
- **Take.** The word reviewers keep using is "kitchen sink". The fix they praise is not fewer mods but a single loop that every mod feeds.

---

# Gaps nobody fills

### M63. Event chains that leave a lasting change [mixed; the gap is [U]]
- **What exists.** Vanilla: zombie siege (a 10% midnight spawn burst, M21), raids (waves and a 40 minute Hero buff, M20), and cure (a permanent discount, M21). MineColonies: a no-loss raid gives a colony-wide mood bonus (M1). Live Villages: outposts start hostile and can be won to trust; damaged buildings are renovated (M5). Village Quest: abandoned villages can be restored (M6). Pueblo Vivo: rumour to scout to bad night to attack (M7). Millénaire 9.0: diplomacy, raids and war (M3). Hero Proof: a medal (M20). "Annoying Villagers Trades" (469 downloads, Modrinth, upd 2026-08-08, https://modrinth.com/project/annoying-villagers-trades) adds villager kidnapping and trades to another mod; I did not read it. "80% Abandoned Villages" (100K) changes how many villages are ruined (M21).
- **What I could not find.** A mod where a village burns and is rebuilt over play days, or where villagers are taken to a beast's lair and brought back changed, with the village remembering. No mod, pack or Mojang feature in my sources does a multi-step chain whose last step scars or heals the village for good.
- **Take.** This is open ground. The nearest parts to compose are: Pueblo Vivo's threat ladder (M7), Live Villages' renovation and trust tiers (M5), vanilla's cure (M21), and MineColonies' no-loss mood bonus (M1).

---

# Principles for AWAKEN

One line each, with the best source. Aimed at: living villages on a shared simulation, event chains that leave a lasting change, a base that recognises player building, every ore with a job, tools with feel, caves as movement playgrounds, nothing overwhelming.

1. **Give every village a ledger, not a script:** population, housing, stock, wealth, security, comfort, projects and routes, with catch-up maths when chunks unload, so the village is the same whoever looks. Best source: M5.
2. **Let the player move the village by what they bring:** a board of wants and surplus, trade and donation that change a stat, and staged projects they can help finish, never a chore list. Best source: M5 (Trade Board), M3 (trade grows the village).
3. **Make each job a workstation, a need and a product, and show the unmet need as a small sign on the worker,** not a menu. Best source: M1 (red gear overhead), M19 (job sites).
4. **Tie every settlement tier to a mined material,** so each ore holds a standing job (hut level sets tool tier and dig depth: stone 1, iron 2, diamond 3; copper Y48, iron Y16, gold Y-16, diamond bedrock). Best source: M1.
5. **Escalate danger in readable phases tied to real village conditions** (rumour, scouts, bad night, attack), with a direction and a count, not a timer. Best source: M7, M1 (raids).
6. **End every event on a persistent mark:** a mood bonus for no losses, a permanent discount for a cure, a restored ruin, a keepsake in hand, because a 40-minute buff is forgotten. Best source: M21 (cure), M1 (no-loss bonus), M5.
7. **No hauling in the first hour:** give the player a courier answer early and ask for decisions, not carrying, or an add-on genre will grow to fix it. Best source: M1, M2.
8. **Read the player's build by contents, with a short legible rule** (a light, a seat, a table, a minimum interior, plus bedroom space, window light and privacy), pay it back in prices and moods, name the missing piece in plain words, and show the breakdown only when asked. Best source: M49, M48.
9. **Never demand a builder for the player's own rooms:** recognise by objects, not by shape. Best source: M50 (the MineColonies contrast).
10. **Keep the act of mining, change the unit:** a vein that crumbles, a short read-the-wall puzzle, a 4.8-second brush, because about 223M downloads across two vein-mining mods say the identical break is what bores people. Best source: M33, M34, M26.
11. **Let the ground be read with a sense, not a lookup:** echo and pitch, hidden one-per-chunk veins, piles that need several hits, so finding ore is a skill and not an x-ray. Best source: M32.
12. **Make tools from two or three parts and one trait per material,** hooked to the smithing table the player knows, because Tinkers' wins on depth and loses on overwhelm. Best source: M30 (vs M31).
13. **Give every cave a rule that changes how you move, see or carry, plus a resource that exists only there:** magnetic boots, acid on armour, muck that slows, gas that sickens. Best source: M37, M23 (sulfur caves).
14. **Build caves as three-dimensional routes** (guaranteed connection, vertical rifts, spiral shafts, rope ladders) and unlock the verbs in steps (rope, grapple, wall run, slide) behind a skill tree. Best source: M39, M43, M45, M46, M47.
15. **Put the payoff at the end of the tunnel,** an ore deposit, an outpost, a workshop, and space structures out so each find is an event, with villagers who sell directions. Best source: M40, M41, M22.
16. **Link base to dungeon by a cost the base can pay,** and do not gate the base's automation behind the dungeon's currency. Best source: M56.
17. **Ship one identity and tuned defaults:** every system must feed the same loop, and nothing like Tough As Nails' harsh defaults or a 300-mod buffet. Best source: M62, M51.
18. **Do not build a separate mining dimension:** all the leaders sit under 35K downloads. Put depth under the places people live. Best source: M36.
