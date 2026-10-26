# Guiding without a guidebook, gating without frustration
Research for AWAKEN's "out of the city, what now?" problem and the grapple-cheese question. Compiled 2026-10-26.

**Verification tags**
- **[V]**: checked against the developer's own words or the primary page (or a direct report quoting them, fetched and read).
- **[P]**: partly verified. Either a secondary report of a primary source, or a search excerpt where the page itself was blocked.
- **[U]**: unverified. Treat it as a lead, not a fact.

Format for each entry: **CLAIM** → **SOURCE** (who, where, date, URL) → **AWAKEN** (how it applies).

---

## A. Landmarks, sightlines and "gravity" (the world pulls, so the UI doesn't have to push)

### 1. Gravity (引力): make places attract the player instead of routing them
- **CLAIM:** BotW's team guided players by giving places "gravity", meaning a pull that comes from real value, not from a marker. Fujibayashi likened it to insects drawn to light. Stables gained useful NPC information; shrines were made visible even by day. Scarcity (weapons only from enemies or chests, rupees hard to earn) made ordinary finds worth a detour. Odd rock shapes raise a question from far away. Each pull has a strength that varies with its size, with what the player currently wants, and with time of day. **[V]**
- **SOURCE:** Hidemaro Fujibayashi (director) and Makoto Yonezu (senior lead artist, terrain), "Field level design in *Zelda: Breath of the Wild*", CEDEC 2017, 31 Aug 2017. Famitsu report: http://www.famitsu.com/news/201708/31140870.html
- **AWAKEN:** Every site the Horizon line can name needs a reason to go that a player can read from a distance: a Precursor monolith's glow, smoke from a Wayfarer camp, a sky platform's silhouette. Rewards in the overworld should be scarce enough that a detour feels worth it. A site's pull should also shift with the player's state. A Tether spire should glow more once the player holds Rank D, and a Lord gate should pulse at dusk.

### 2. The triangle rule: terrain that hides, reveals, and forks
- **CLAIM:** BotW's hills are triangles at three scales. Large ones are landmarks, medium ones screen routes, small ones pace the player. A triangle asks the player to choose between going over and going around. Walking round it reveals a tower bit by bit, so there is always a next target. The eye goes to the tip, so that is where a reward (or a decoy) goes. Squares hide surprises completely, and trapezoids block the view while their flat tops suggest "something is up there". **[V]**
- **SOURCE:** Yonezu, CEDEC 2017 (Famitsu, as above); Capcom's Matt Walker's live summary via Gigazine, 4 Oct 2017: https://gigazine.net/gsc_news/en/20171004-breath-of-the-wild-design-secret ; Mark Brown (GMTK), "How Nintendo Solved Zelda's Open World Problem", 9 May 2023: https://gmtk.substack.com/p/how-nintendo-solved-zeldas-open-world
- **AWAKEN:** The terrain plan (Awakened Lands) should place Precursor sites on peaks and behind ridges so they reveal themselves as the player walks, not as icons. A flat-topped mesa near Carillon reads as "there's something up there" for free. That is the ideal home for the first Tether structure.

### 3. Lines fail, points with pull succeed (Nintendo's own playtest data)
- **CLAIM:** The first BotW layout tied towers together with event-filled routes (points and lines). Playtest heatmaps split testers about 80/20: most followed the obvious route ("an invisible, but obvious guide rope") and felt railroaded, while the rest wandered and found nothing. Scattering pull (shrines, stables, camps, smoke, skull rocks) along sightlines from the towers spread paths across the whole map, and almost everyone still reached the key locations. **[V]** (from secondary reports of the talk, which was never uploaded)
- **SOURCE:** CEDEC 2017 talk, as reported by Famitsu (31 Aug 2017), Gigazine (4 Oct 2017) and GMTK (9 May 2023), all linked above.
- **AWAKEN:** Don't fix "what now?" with a quest chain out of the gate. That is a line, and lines produce the same 80/20 split. Seed several attractors visible from the city gate and from every high point. AWAKEN's autopilot tours could log player paths and render a BotW-style heatmap of the first hour outside Carillon.

### 4. "If you can see it in the distance, you should be able to go there"
- **CLAIM:** Pearl Abyss built Crimson Desert's world around "filling the world with reasons you actually want to explore it". Their principle: "if you can see it in the distance, you should actually be able to go there." Distant towers and villages are reachable, landmarks, quests and events are hand-placed, and vegetation density was tuned for movement rather than realism. **[V]**
- **SOURCE:** Geuntae Ahn (Art Level Room Lead) with Jinhwan Kim and Jinho Yun, Gamescom dev 2026, reported 27 Aug 2026: https://www.gamemeca.com/en/view.php?gid=1779680 ; also Inven Global: https://www.invenglobal.com/articles/25231/pearl-abyss-shares-crimson-desert-development-philosophy-at-gamescom-dev . Earlier, Will Powers (Pearl Abyss PR/marketing), PlayDay.One, 29 Jan 2026 ("a world that you want to get lost in and you want to get distracted by"): https://playday.one/2026/01/29/1v1-will-powers-and-pearl-abyss-on-crimson-desert-interview/
- **AWAKEN:** This is the promise behind Far Sight. Whatever the far renderer shows (city towers, monoliths, sky platforms) must be a real, reachable, rewarding place. A sky platform you can see but can't reach yet is fine, provided the game makes clear *what* will get you there (see section C).

### 5. Crimson Desert's quiet cost: the first lock needed a day-one hint
- **CLAIM:** Crimson Desert gives "little to no guidance" (TechRadar, Mar 2026). Its sky-island ("Abyss") puzzles come in chains where each island unlocks the next, and they pay out Abyss Artifacts that buy skills. But the very first Abyss puzzle ("Activate the device") stumped reviewers. Pearl Abyss's day-one patch added a text explanation and big light beams pointing the way, and admitted the tutorial puzzle was too hard. Reviewers praise the open world and criticise the opaque early hours. **[P]**
- **SOURCE:** GoHa.ru, 20 Mar 2026: https://www.goha.ru/too-hard-for-tutorial-pearl-abyss-oblegchila-pervuyu-zagadku-crimson-desert-posle-togo-kak-na-nej-zastryali-igrozhury-i-blogery-xqZyMQ ; TechRadar review (Mar 2026): https://www.techradar.com/gaming/crimson-desert-review ; Abyss chain structure (guide, Apr 2026): https://vulkk.com/2026/04/06/crimson-desert-skyloop-bridge-abyss-puzzle-walkthrough/ . **[U]:** fan-wiki claims that quests come mainly from overheard tavern talk and that the map is fogged until explored. These could not be traced to Pearl Abyss.
- **AWAKEN:** This is the Crimson Desert Euan admires, and it also shows the risk. "No guidebook" still needs a fallback for the *first* instance of every lock type. The first Precursor door, the first Tether spire and the first rank seal each need a readable cue that has been playtested (autopilot plus one human run).

---

## B. Diegetic guidance: the world talks before the UI does

### 6. Ghost of Tsushima: wind, animals, smoke, and rumours with a budget
- **CLAIM:** Sucker Punch kept "UI out of your face and your eyes on the beautiful art". The Guiding Wind replaced the minimap and waypoint line, and "ideas like following a fox or guide birds" followed from that goal. Art built "unique looking trees and mountains that beckoned people" (Connell). Per notes on Parker Hamilton's GDC talk:
  - Landmarks are never hidden by foliage.
  - Sound and particles act as cues (Mongol drums near camps).
  - Quest start and end points are placed far apart, so players stumble on content in between.
  - Every location pays something.
  - Guide animals each have a niche: deer for nearby spots, birds where deer can't climb, foxes on fixed routes to shrines.
  - Playtests showed the wind was hard to read at speed and pointed straight into cliffs. Sucker Punch made it stronger, made its particles follow the terrain, and added a button to summon a gust on demand.
  - NPC rumours point to content, with density, spacing, cooldowns and **a cap of two hints per content type**.

  **[V]** for Connell, **[P]** for the talk details (from a user's notes).
- **SOURCE:** Jason Connell (creative director), VFX Voice, 15 Sep 2020: https://vfxvoice.com/creating-the-way-of-the-warrior-for-ghost-of-tsushima/ ; Parker Hamilton, "Exploration in Ghost of Tsushima: Letting the Island Guide You", GDC 2021: https://www.gdcvault.com/play/1027399/Exploration-in-Ghost-of-Tsushima (video posted 11 Oct 2022: https://www.youtube.com/watch?v=b5rUPBWgwuw ); talk notes (gcores, 23 Jul 2023): https://www.gcores.com/articles/168695
- **AWAKEN:** The Horizon line is already AWAKEN's wind. Give it an on-demand "pulse" (a key that sends a visible streak toward the named site). Add AWAKEN-native guide creatures (Veil motes, Precursor-drawn birds) that lead to secrets, a different creature for each kind of secret. Cap Wayfarer and NPC rumours per region per site type so the world never turns into a checklist.

### 7. The Witcher 3's "?" marks began as circling seagulls, and the designer calls the change a mistake
- **CLAIM:** Skellige's notorious "?" icons (smugglers' caches) were originally marked only by seagulls circling overhead: "it wasn't planned to actually have an icon on the map." Late in production (late 2014) they became map icons. The designer who placed them now calls it "a mistake" he "wouldn't do… again". **[V]**
- **SOURCE:** Philipp Weber (then junior quest designer; later Witcher 4 campaign director), livestream 21 Jul 2022, reported by Dexerto 22 Jul 2022: https://www.dexerto.com/gaming/the-witcher-4-dev-reveals-big-lesson-from-witcher-3-development-1879892/
- **AWAKEN:** Never promote a world cue to an Atlas icon before the player has seen the thing itself. The Atlas should only record places the player has actually seen (or been told about by a Wayfarer). Unseen caches stay as circling birds or motes.

### 8. Diegetic UI removes "a wall of safety"
- **CLAIM:** Dead Space put health, ammo, inventory and the objective "locator" onto Isaac's suit and the world as holograms, to "remove a wall of safety between the player and the game". **[V]** (The locator is a path drawn on the floor on demand, which is how it works in the game itself. **[P]**)
- **SOURCE:** Dino Ignacio (lead UI designer, Visceral), "Crafting Destruction: The Evolution of the Dead Space User Interface", GDC 2013; Game Developer, 12 Jun 2013: https://gamedeveloper.com/design/video-designing-i-dead-space-i-s-immersive-user-interface
- **AWAKEN:** Any "you need X" message should come from something in the fiction: the Registry licence humming, the Awakened sense, the anchor ring itself. It should be summoned on demand (like the locator), not shown all the time.

### 9. Far Cry 5 dropped towers: people and places tell you what's out there
- **CLAIM:** Ubisoft removed radio towers and the minimap from Far Cry 5. Players learn about content by "talking to people", and NPCs you help "might put a hint on your map". **[V]** Lead writer Drew Holmes said the aim was to break the climb-a-tower habit and make players pay attention to people and surroundings. **[P]**
- **SOURCE:** Dan Hay (creative director), PlayStation Blog, 15 Mar 2018: https://blog.playstation.com/archive/2018/03/15/six-ways-far-cry-5-is-re-writing-the-series-long-running-open-world-formula ; Holmes via CogConnected, Jun 2017: https://cogconnected.com/2017/06/ubisoft-explains-towers-map-far-cry-5/
- **AWAKEN:** Wayfarers, bounty boards and the Registry clerk are AWAKEN's towers. Each one adds two or three Atlas hints in return for talking to them or doing them a favour. Don't add a "climb the monolith to reveal the region" mechanic.

---

## C. Locks you can see, keys you can name, and danger that steers

### 10. Show the lock early, keep few locks open, keep keys near their locks
- **CLAIM:** Metroid Dread rarely loses players despite its sprawling map. It temporarily confines them to small sections, uses points of no return to block wrong turns, places locks near their keys, and limits how many open locks a player has to remember. **[P]** (GMTK video, date unverified, c. late 2021)
- **SOURCE:** Mark Brown, GMTK *Boss Keys*, "Why You (Probably) Didn't Get Lost in Metroid Dread": https://amara.org/en-gb/videos/Aci4iDbgsAtY (YouTube original)
- **AWAKEN:** Within sight of Carillon's gate, the player should see **one** Tether lock and **one** rank-sealed door, and both keys should be reachable in the first session (the Tether at Rank D, the door at the stated rank). Don't scatter Wings-only platforms near spawn: a lock whose key is twenty hours away stops reading as a promise and starts reading as a tease.

### 11. BotW cut the hookshot because it "completely breaks" the movement ladder
- **CLAIM:** Nintendo prototyped double hookshots ("incredible" mobility, per Fujibayashi) and cut them because the hookshot "completely breaks the climbing mechanic". Aonuma added that in earlier Zeldas the hookshot had to be held back until late in the game, or "players could just go anywhere". **[V]** (as reported)
- **SOURCE:** Eiji Aonuma and Hidemaro Fujibayashi, IGN interview 9 Dec 2017, via GoNintendo 10 Dec 2017: https://gonintendo.com/stories/297479-nintendo-considered-and-tested-the-hookshot-for-zelda-breath-of
- **AWAKEN:** The Tether is AWAKEN's hookshot. Nintendo's lesson cuts both ways. A grapple invalidates every lock built below it, so Tether locks must need something the Runner moveset can't fake (anchor rings, pull-to-open seals). And because Minecraft *already* lets everyone climb anywhere with blocks, the Tether's value has to come from speed, style and Tether-only mechanisms, not from reach alone (see section F).

### 12. The Great Plateau: a soft wall that says exactly what's needed
- **CLAIM:** BotW's opening plateau was the first area built, "a culmination of what we wanted to include in the game" (Fujibayashi). **[V]** In play, its cliffs are deadly or the game returns you, and the Old Man tells the player plainly that they need his paraglider to leave and how to earn it (the four plateau shrines). The world's most "open" Zelda opens with an explicit, in-fiction statement of the key. **[P]** (from the game itself and wiki documentation)
- **SOURCE:** Fujibayashi, IGN 22 Mar 2017, via Nintendo Everything 26 Mar 2017: https://nintendoeverything.com/zelda-breath-of-the-wild-devs-on-starting-out-with-10-people-showing-miyamoto-the-2d-prototype-great-plateau-made-first-more/ ; Old Man and paraglider: https://www.zeldadungeon.net/wiki/Old_Man_(Breath_of_the_Wild) , https://en.wikipedia.org/wiki/Great_Plateau
- **AWAKEN:** This is the precedent for Euan's "it says you need a grapple" idea. Nintendo's freest game names the key, names who holds it and names the price, all through a character, once.

### 13. Danger is the honest soft gate
- **CLAIM:** Where FromSoftware didn't want explicit signposting in Elden Ring, it used enemy difficulty: "Maybe there are tough enemies, and [the player] might feel 'oh, this is not the right way'." The core rule is "Trust players… Players will figure out what to do next." **[V]** Grace guidance is "the minimum level of guidance. There are also discovery and drama if you stray away from it." **[V]** (translated) Players "can come back to something later when they're at an impasse." **[V]**
- **SOURCE:** Miyazaki at DICE 2023, Game Developer (Bryant Francis), 28 Feb 2023: https://www.gamedeveloper.com/design/hidetaka-miyazaki-s-advice-for-challenging-game-design-trust-players- ; Famitsu via Siliconera, 22 Feb 2022: https://www.siliconera.com/elden-ring-gameplay-development-explained-by-hidetaka-miyazaki/ ; PlayStation Blog (Tim Turi), 28 Jan 2022: https://blog.playstation.com/2022/01/28/an-interview-with-fromsoftwares-hidetaka-miyazki/
- **AWAKEN:** Region levels should gate through real, legible danger: out-levelled mobs, a Lord whose name plate shows its rank, a Veil that drains you. A text box claiming "this terrain needs a higher level" is not a gate. The player can see the blocks would hold them, and the first time they test it by pillaring, the claim is exposed as a lie.

### 14. Design for the blind player; treat guides as a fallback, never a requirement
- **CLAIM:** Miyazaki: "Of course, players are going to consult guides… We try to cater to the player who is completely blind and wants to go through organically." He frames dependence on guides as room for FromSoftware to improve. **[P]** (PC Gamer interview, Jun 2024, via syndication) Before launch he hoped players would "steer clear of spoilers or guides" (PS Blog, 2022). **[V]** Before that: "We wanted to create this world that was full of the joy of exploration of the unknown… lots of enticing things for the budding adventurer." **[P]** (Edge #367, Dec 2021, via NME)
- **SOURCE:** VideoGamer, Jun 2024: https://videogamer.com/news/miyazaki-elden-ring-cater-to-the-player-blind ; NME, Dec 2021: https://www.nme.com/news/elden-ring-is-the-culmination-of-fromsoftwares-games-says-miyazaki-3127812
- **AWAKEN:** Euan's rule ("no guidebook") matches this. Treat every Discord question of the form "how do I get the grapple?" as a design bug, not a documentation gap.

### 15. When a sandbox gate leaks, close it with an overwhelming, legible "no"
- **CLAIM:** Terraria's Dungeon Guardian was added in 1.0.5 to stop players skipping Skeletron through an exploit (killing weak early Skeletron heads unlocked the Dungeon). The Guardian kills in one hit (now 9,999 damage), spawns only inside the Dungeon before Skeletron is dead, and killing it does not open the Dungeon. **[V]** (community wiki, citing version history)
- **SOURCE:** Terraria Wiki, Dungeon Guardian: https://terraria.wiki.gg/wiki/Dungeon_Guardian
- **AWAKEN:** For true hard gates (gate interiors above your rank, Lord arenas), a single, unmistakable in-fiction guardian beats invisible walls: a Registry seal-warden, or the Veil itself. Pair it with a warning *before* contact, as Terraria does with the Old Man at the Dungeon door.

---

## D. Reasons to wander: people, rumours, and helpers who know what you've done

### 16. Hand-made encounters over procedural fill
- **CLAIM:** Rockstar tried procedural companion missions for RDR2 and dropped them because it "could not get the level of connectedness and sophistication that we wanted". The goal was a world players "can get lost in", whose places "feel like they exist when you're not even in them… like they're waiting for you", with "options for you" everywhere. **[V]** (as reported from IGN)
- **SOURCE:** Rob Nelson (Rockstar North), IGN, 4 May 2018, via PlayStation LifeStyle 5 May 2018: https://www.playstationlifestyle.net/?p=651867 ; GameStar: https://www.gamestar.de/artikel/red-dead-redemption-2-rockstar-experimentierte-mit-generierten-missionen-entschied-sich-aber-dagegen,3329337.html
- **AWAKEN:** Wayfarers and world events should be a small, hand-written pool per region, with a few variables (who, where, the weather), rather than an endless generator. Twelve memorable roadside moments in the first biome are worth more than a thousand fetch quests.

### 17. Radiant quests send you to places you haven't been
- **CLAIM:** Skyrim's Radiant system generates small tasks (for example, an innkeeper asking you to clear bandits from a place you haven't found) and is "designed to lead players into interesting parts of the world they haven't visited before". **[P]** (PC Gamer's paraphrase of Howard's Wired interview) For Oblivion, Howard wanted to keep "pointing you to something fun in the game, even if you don't know it's there". **[V]** (GameSpy quote via RPG Codex, 22 Oct 2004) Of Oblivion's markers he said hidden goals like "find the secret cave" stay unmarked: "we only mark things we want you to know about". **[P]** (GameSpot E3 2005 Q&A; page blocked, quote from search excerpt)
- **SOURCE:** PC Gamer, Nov 2011: https://www.pcgamer.com/uk/the-elder-scrolls-v-skyrim-has-infinite-quests-says-howard ; RPG Codex/GameSpy, 22 Oct 2004: https://rpgcodex.net/article.php?id=3108 ; GameSpot, May 2005: https://www.gamespot.com/articles/the-elder-scrolls-iv-oblivion-qanda-oblivion-at-e3-2005/1100-6124431/
- **AWAKEN:** Bounties and Wayfarer requests should deliberately target sites the player hasn't discovered, using the Atlas's own discovery data. And there are two clear classes: the movement-ladder locks get marked, the secrets never do.

### 18. A helper who knows what you've done (Terraria's Guide)
- **CLAIM:** Terraria's Guide NPC gives one tip per "Help" press, chosen by game state: tools owned, maximum health, bosses beaten, NPCs present. For example: "There is an old dungeon not far from here. Now would be a good time to go check it out." Showing him an item lists the recipes that use it. **[V]** (community wiki, quoting the game's lines)
- **SOURCE:** Terraria Wiki, Guide: https://terraria.wiki.gg/wiki/Guide
- **AWAKEN:** This is the cleanest answer to "what now?" that isn't a book. The Registry clerk (and any Wayfarer) answers "Anything for me?" with **one** state-aware line, the single most relevant next step: "You've hit Rank D. The Registry can fit you a Tether now." Repeated asks cycle to the next most relevant line. It costs a walk back to a person, so it never becomes a checklist.

### 19. Tell players where a thing is, not where to stand
- **CLAIM:** Assassin's Creed Odyssey's recommended "Exploration" mode hides objective markers. Quest-givers give a region and a direction ("east of…"), and the player finds the exact spot with the map and the eagle. "Guided" mode keeps markers. **[P]** (press coverage of Ubisoft's description; pages blocked)
- **SOURCE:** Push Square, Sep 2018: https://www.pushsquare.com/news/2018/09/assassins_creed_odyssey_explorer_mode_turns_off_automatic_objective_markers_for_a_more_organic_experience
- **AWAKEN:** Wayfarer rumours should be phrased as directions relative to things the player can see ("past the twin monoliths, where the river forks"). The Horizon line then confirms the name once the site is in range. It points, it doesn't pin.

### 20. Give the task; leave the method free
- **CLAIM:** Dragon Quest Builders' producer described the game as structured but still a sandbox. Players get tasks, such as building a house for a character, but how they do it is up to them. **[P]**
- **SOURCE:** Noriyoshi Fujimoto (producer), MCV interview "When Dragon Quest met Minecraft", 2016: https://www.mcvuk.com/business-news/interview-when-dragon-quest-met-minecraft/
- **AWAKEN:** Directives should name outcomes ("reach the spire's crown", "bring back a Precursor shard"), never methods. Then pillaring up becomes one valid solution among several, not a cheat.

---

## E. Knowledge as progress: journals that aren't guidebooks

### 21. Outer Wilds: curiosity only, no waypoints, a log that organises what you've seen
- **CLAIM:** Beachum's first design pillar was players "exploring because they're curious about the world, and not for any other reason". Clues point you somewhere, "but we don't put a giant waypoint on your map". **[V]** Mobius's biggest playtest-driven change was redesigning the ship computer, the player's journal, into a visual "detective board", because the way it organises information changes how players interpret it. **[V]** Two cautions from the same team: players missed clues placed near the warp receivers, and decorative Morse-code lighthouses were cut because players read them as a puzzle. **[V]**
- **SOURCE:** Alex Beachum and Logan Ver Hoef, MCV, 3 Apr 2020: https://mcvuk.com/?p=208916 ; Mobius Digital dev post, 20 Dec 2017: https://www.mobiusdigitalgames.com/news/archives/12-2017 ; GDC 2020 talk "Curiosity-Driven Exploration: The Design of Outer Wilds" (Beachum and Verneau; listing only): https://www.gdconf.com/news/attend-gdc-and-learn-how-outer-wilds-nailed-curiosity-driven-game-design
- **AWAKEN:** The Atlas should be the "non-guidebook guidebook". It holds only what the player has seen or been told, linked like a detective board: anchor ring seen at X → Tether → Registry, Rank D. Every visible Precursor cue reads as a promise, so don't ship decorative glyphs that look like locks.

### 22. Hollow Knight: the map is a thing you find, buy and fill in
- **CLAIM:** Hollow Knight starts with no map. In each area, Cornifer (found by his humming and a trail of paper scraps) sells a partial map. If you miss him or can't pay, it waits at Iselda's shop. Her quill fills the map as you explore, and her pins mark only places already visited. Navigation help is something you work towards. Co-director Ari Gibson said the game is "built around this sense of discovery". **[P]** (SUPERJUMP quoting PC Gamer; Cornifer details from the community wiki) **[U]:** a PC Gamer line that Team Cherry later "chipped away at a lot of the hard gating" appeared in a search summary but could not be confirmed.
- **SOURCE:** SUPERJUMP (Abhishek Iyer), 29 Nov 2022: https://superjumpmagazine.com/getting-lost-by-design-in-hollow-knight ; PC Gamer: https://www.pcgamer.com/how-to-design-a-great-metroidvania-map/ ; Cornifer: https://hollowknight.wiki/w/Cornifer_the_Cartographer
- **AWAKEN:** Wayfarers are Cornifers. Give each a sound signature (a whistle, a song) heard from about 40 blocks away, and have them sell or trade Atlas fragments for their region. Anything missed falls back to a city vendor, so nothing is lost for good.

### 23. Structure a sandbox with signals, not quests
- **CLAIM:** Subnautica added "structure to a sandbox through radio signals", used "story beats that guide the player without overly directing them", and wrote "mysterious tooltips" that hint without explaining. **[V]** (session description)
- **SOURCE:** Charlie Cleveland (Unknown Worlds), "The Design of Subnautica", GDC 2019; Game Developer, 10 Jul 2019: https://www.gamedeveloper.com/design/video-inside-the-design-of-i-subnautica-i- ; GDC Vault: https://gdcvault.com/play/1025745/The-Design-of-Subnautica
- **AWAKEN:** The Hunter licence can receive Registry "signals": short, rare, in-fiction calls ("Rift reported near the Ashen ford") that create a goal and a direction without a quest log. Item tooltips for Precursor finds can hint at their lock ("A hook-scarred ring. It was made to be pulled.").

---

## F. Goals and pacing

### 24. Teach through play so the game becomes the player's
- **CLAIM:** World 1-1 was built so players "gradually and naturally understand what they're doing", so they can "start to play more freely". "Once the player realizes what they need to do, it becomes their game." **[V]**
- **SOURCE:** Shigeru Miyamoto (with Takashi Tezuka), Eurogamer video interview, 2015; Game Developer, 8 Sep 2015: https://gamedeveloper.com/design/how-miyamoto-built-i-super-mario-bros-i-legendary-world-1-1
- **AWAKEN:** The road out of Carillon is AWAKEN's 1-1. Its first 200 blocks should *show* (not tell) one Runner move, one site worth a detour, one lock with its key named, and one person with a rumour.

### 25. Balance short and long goals; let players choose their own
- **CLAIM:** Schell's lenses ask designers to weigh "the balance of long term and short term goals for the player" and to look past the game's stated goals to the player's real motivation (Lens of Curiosity). **[P]** (the goals wording is quoted in Daniel Cook's review; the Lens of Curiosity wording was confirmed via a lens index) Sid Meier's GDC 2012 talk revisited his line that "a game is a series of interesting decisions". **[V]** (GDC description)
- **SOURCE:** Jesse Schell, *The Art of Game Design: A Book of Lenses* (2008; 3rd ed. 2019); Lost Garden review, 19 Feb 2009: https://lostgarden.com/2009/02/19/review-of-the-art-of-game-design-by-jesse-schell/ ; Sid Meier, "Interesting Decisions", GDC 2012; Game Developer, 27 Sep 2018: https://gamedeveloper.com/design/video-sid-meier-explores-interesting-decisions-in-gameplay
- **AWAKEN:** AWAKEN already has all three horizons: Directives (minutes), region levels and rank trials (hours), Truths (the long game). The fix is to make sure **all three are visible the moment the player leaves the city**. Offer a Directive at the gate that points at something on the skyline, show the region level on entry, and have the licence show the next rank's Reassessment. Every fork should be an interesting decision: the Tether spire or the Wayfarer's bounty, not "the next quest".

---

## G. Minecraft-specific evidence

### 26. Vanilla already guides without a book: advancements, recipe unlocks, explorer maps
- **CLAIM:**
  - Advancements replaced achievements in Java 1.12 (snapshot 17w13a, 30 Mar 2017). They use a tab tree and data-driven JSON criteria. The next snapshot (17w14a) added an "adventure" tab and notifications "when they advance, or unlock new recipes".
  - The Recipe Book unlocks recipes as their criteria are met, so the player learns what's craftable from what they've picked up. **[V]** (Minecraft Wiki changelog)
  - The cartographer villager (1.11, Nov 2016) sells explorer maps to ocean monuments and woodland mansions: a person who sells you a destination. **[P]**
- **SOURCE:** https://minecraft.wiki/w/Java_Edition_17w13a ; Mojang, "Minecraft snapshot 17w14a" (Apr 2017): https://www.minecraft.net/en-us/article/minecraft-snapshot-17w14a ; Shacknews on 1.11: https://shacknews.com/article/97716/minecraft-update-111-now-available-for-pc-and-mac
- **AWAKEN:** Use the advancement toast as the delivery channel players already trust for "you found a lock type" and "you earned a key". Unlock Atlas entries the way the recipe book unlocks recipes: by contact.

### 27. Vanilla's soft gates put the reward behind the gate, not the route
- **CLAIM:**
  - Elytra (1.9, 2016) exist only in End ships on the outer End islands, so flight sits behind the dragon and the End gateway. **[V]**
  - The Trial Chambers' vault (1.21, 2024) can be broken, but "does not drop anything when destroyed". Its loot comes only from unlocking it with a trial key. **[V]**
  - Adventure mode (1.3.1, 2012; tightened in 1.8 with CanDestroy/CanPlaceOn) is the tool for maps that must stop building, and it is *not* used in survival. **[P]**
- **SOURCE:** https://minecraft.wiki/w/Elytra ; https://minecraft.wiki/w/Vault ; Adventure-mode history (community): https://forums.spongepowered.org/t/adventure-mode-blocks-not-breakable/15627
- **AWAKEN:** Mojang's own answer to "players will bypass the lock" is the **vault pattern**: let them reach the lock, but make the lock the only way to the reward. Players can still bridge or pillar to the outer End, and vanilla has never forbidden it. For AWAKEN, a Tether-sealed Precursor cache that yields nothing when broken is the faithful translation.

### 28. Mod-pack quest books vs. in-place help
- **CLAIM:** Community complaints about pack quest books centre on checklists of "every item and fluid under the sun", and on vague quests that send you out of the game to look things up. Fans of quest-heavy packs exist too. **[U]** (community threads, no systematic data) The best-loved in-pack help is contextual: Create's **Ponder** shows an animated in-world scene of a block when you hold W on it. **[P]** (mod docs)
- **SOURCE:** r/feedthememes thread (2024), via https://reddit.rtrace.io/r/feedthememes/comments/1csdji7/if_i_boot_up_a_new_modpack_and_it_doesnt_have/ ; a pack-review complaint about non-explanatory quests: https://forum.feed-the-beast.com/goto/post?id=1735442 ; Create Ponder: https://www.curseforge.com/minecraft/mc-mods/create-ponder
- **AWAKEN:** Euan's instinct is right: the annoying part is the *book* (a detached checklist), not the *information*. Put the information where the question arises (on the lock, on the item, at the person), shown once and recallable on demand. A Ponder-style "show me" on a Precursor mechanism (hold a key) is fine. A chapter list isn't.

---

## H. Players building around a designed obstacle (sandbox trust)

### 29. Nintendo embraced the "cheat" when it served exploration
- **CLAIM:** TotK's Ascend (rising through ceilings) "was actually the result of a debug feature… And to be blunt and honest, cheating can be fun" (Fujibayashi). **[V]** (as reported from Polygon) In Nintendo's own interview, Dohta: Zonai devices create "many moments where you can cheat in a good way". The team balanced things so elaborate builders and simple progressors can both "enjoy the game in their own way". **[V]** BotW's GDC talk framed "multiplicative gameplay" and a rule-based chemistry engine as ways to let players find "their own unique solutions". **[V]**
- **SOURCE:** Polygon via GoNintendo, 12 May 2023: https://gonintendo.com/contents/20253-zelda-tears-of-the-kingdom-devs-reveal-what-inspired-link-s-ultrahand-and-ascend ; Nintendo, "Ask the Developer Vol. 9: Tears of the Kingdom", May 2023: https://www.nintendo.com/sg/interview/totk/04.html ; GDC 2017 "Change and Constant" (Fujibayashi, Takizawa, Dohta): https://gdcvault.com/play/1024562/Change-and-Constant-Breaking-Conventions ; Thumbsticks report, 2 Mar 2017: https://www.thumbsticks.com/gdc-17-breath-of-the-wild-science-lies/
- **AWAKEN:** A player who pillars up to a sky platform has *solved* a traversal problem in Minecraft's language. Reward the cleverness (a view, an Atlas reveal, a lore fragment), and keep the *core payoff* tied to the Tether mechanism. Don't punish the climb.

### 30. Rule-based gates are accepted when they're consistent and in-world, and resented when they look arbitrary
- **CLAIM:** Valheim gates its world through boss drops (tools and keys for the next biome) and a hard rule that ore and metal can't travel through portals. Iron Gate's CEO said the portal rule "is something that has changed many times during development" and that they're "listening to community feedback". **[P]** Designer Jeff Vogel argued the rule "is what gives Valheim its epic quality". **[P]** Players still complain about it. **[P]**
- **SOURCE:** Richard Svensson via PCGamesN (2021): https://www.pcgamesn.com/valheim/ore-portal ; Jeff Vogel via GameBanshee: https://www.gamebanshee.com/ah99q ; boss-gated progression summary (community): https://guildorder.com/games/valheim/wiki/boss-and-biome-progression
- **AWAKEN:** If AWAKEN ever needs a no-building rule (inside gates, around a Lord arena), make it a consistent law of the place with a visible cause: the Veil dissolves placed blocks, and the dissolve is shown. Never a silent "can't place here". Players accept physics. They resent exceptions.

---

## I. Counter-examples worth remembering
- **Towers and "?" marks.** Ubisoft removed towers in Far Cry 5 (entry 9), and Witcher 3's designer disowns the Skellige icons (entry 7). Both turned discovery into a checklist.
- **Lines instead of points.** BotW's own first layout made 80% of testers feel railroaded (entry 3).
- **Opaque first locks.** Crimson Desert needed a day-one patch for its first Abyss puzzle (entry 5), and Outer Wilds players missed clues placed where the designers thought they were obvious (entry 21).

---

# Synthesis: the eight principles that matter most for AWAKEN

**1. Pull, don't push (gravity over quests).** Fix "out of the city, what now?" with attractors visible from the gate and from every high point (a monolith, a sky platform, smoke, a Wayfarer on the road), not with a quest line. *(Entries 1–4, 24)*

**2. Three goal horizons, visible at once.** On leaving Carillon the player should hold a Directive aimed at something on the skyline (minutes), see the region level and the next rank's Reassessment (hours), and know their Truth (long term). Every fork should be a choice between two pulls. *(Entry 25)*

**3. Show the lock early, keep the key near, and keep few locks open.** The first Tether lock and the first rank seal should be within sight of the gate, with both keys earnable in the first session. Put Wings and flight locks further out. *(Entries 10, 11, 12)*

**4. Name the key, never the solution. Say it once, in-world, then file it.** Like the Old Man and the paraglider: one in-fiction line on first contact with each lock *type*, recorded in the Atlas and offered as a Directive. *(Entries 12, 17, 18, 20)*

**5. Danger is the honest soft gate; text is not.** Gate regions by real, legible threat (out-levelled mobs, Lords with visible ranks, the Veil). Never by a message the player can disprove with a stack of dirt. *(Entries 13, 15)*

**6. World cues first, UI on demand.** The Horizon line is the Guiding Wind: add an on-demand pulse, guide creatures for secrets, sound signatures for Wayfarers, and no "?" icons for unseen things. *(Entries 6–9, 22)*

**7. People are the quest log.** Wayfarers, the Registry clerk and bounty boards hand out a state-aware next step (Terraria's Guide), rumours phrased as directions (Odyssey, Far Cry 5), and hand-made encounters (RDR2), each capped per region (Ghost of Tsushima's two hints per type). *(Entries 16–19)*

**8. Trust the sandbox: gate the reward, not the route.** Let players pillar and bridge. Make the Tether the only way to the *payoff* (Minecraft's vault pattern), and reward the climb with something smaller. Never ban block placement silently. *(Entries 11, 27, 29, 30)*

(A standing caution across all eight: playtest the **first** instance of every lock type. That is where Crimson Desert and Outer Wilds both stumbled.)

---

# Recommendation on Euan's question

**Choose the explicit route, but deliver it in-world, make it specific, show it once, and link it to the Atlas, not to a guidebook. Reject the hidden "you need a higher level for this terrain."**

Why:
- **The hidden version fails three ways in Minecraft.** It gives the player no goal: they can't want a key they can't name. It claims something the player can disprove, since they can see blocks would hold them. And it reads as an invisible wall, which is the one thing a sandbox player never forgives. Elden Ring's "not the right way" works because the *danger is real* (entry 13), not because a message says so.
- **The explicit version has the best precedents.** BotW's freest-feeling game names the paraglider, who holds it and its price (entry 12). Terraria's Guide names the next dungeon (entry 18). Bethesda marks "things we want you to know about" and leaves secrets unmarked (entry 17). Metroid-likes make every lock readable (entry 10).
- **A link to the Atlas is not a guidebook.** It opens the player's own record of what they've seen (Outer Wilds, Hollow Knight). That gives the "hyperlink" feel Euan wants without a book.

How it would play:
1. **The structure speaks first.** As the player nears a Tether spire, its anchor ring catches the light and hums. Looking at it, the Horizon line names it: "Anchor ring: Tether point".
2. **One line, first time only per lock type,** framed as the Awakened sense or the Registry licence: *"An anchor ring. A Tether line could catch here. The Registry fits Tethers from Rank D."* The Atlas key opens the entry, and an optional "Make it a Directive" sits beside it. After the first time, the line only reappears when the player sneak-looks at a ring. Rank seals simply state their rank ("Sealed to Rank C"), since the rank system is already legible.
3. **Pillaring is allowed and partly rewarded.** The climber gets the view, an Atlas reveal of the region below, and a Precursor fragment. The cache or vault itself opens only to a Tether pull (the Minecraft vault pattern: breaking it yields nothing). Make the structure physically costly to build up to (overhangs, platforms over open air, Veil wind), but never forbidden.
4. **Keep mysteries mysterious.** Secrets (hidden vaults, rift sites) get only world cues: birds, motes, sounds. The explicit line is reserved for the movement ladder and rank seals, the things the player needs in order to grow.
5. **Close the loop in the first session.** The first Tether lock should be within sight of Carillon's gate, and Rank D reachable in the first hour or two, so "I need a grapple" becomes "I got the grapple, and now that spire is mine" in one sitting.
