# Enchanting, revamped (the Inscribing Table plan)

Plan for Euan's request of 2026-11-02, written by the enchanting-plan session on branch `claude/enchanting-plan-c9d533`.
Status: **approved in full by Euan on 2026-11-02** ("Go ahead with the full plan. AAA LEVEL BABY. Make it look AMAZING").
Stages 0-3 are built on this branch (see §9); the rules are SPEC **§45**. It followed `.claude/PLANNING-PROCESS.md`. Approval page: https://claude.ai/artifact/TsFe2Y1CfJrTxeyFZMKYiF
(source `docs/enchanting/page.html`; answers in its `decisions` collection, d01 to d13).

## 1. Understanding and success contract

### 1.1 What Euan asked for

> "revamp entire enchanting process to be on par and linked to the weapons and armour system we built."

The orchestrator's brief adds what "on par" means. The armour has 29 sculpted sets, the Forge Song and a forge camera
("This looks so fucking good"). The weapons have 16 styles, tiers, Lord-forged weapons, Marks, Weaves and tempering, and
their patterns come from gates, never chests. Enchanting must reach that bar:
- its own look;
- cinematic;
- discovered through the world;
- part of how a weapon or a set of armour grows;
- not a vanilla table bolted on.

### 1.2 The brief as a checklist

1. **Its own look.** A station, a scene and items that look like nothing vanilla has. Every enchantment shows on the item and
   shows when it does its job.
2. **Cinematic, with the hands.** A first-person scene at the station, in the Forge Song's family (its camera, hands, sounds),
   short enough to be fun the twentieth time.
3. **Found in the world.** Every enchantment has a place it comes from, nothing is a random roll, and there's a way to
   every one (the Journey's ledger rule).
4. **Linked to weapons and armour.** How well a piece was forged, its rank and your Hearth change what it can carry.
5. **No overlap.** Enchanting doesn't do the forge's job (numbers, tiers, tempering), an attunement's (an element),
   a Mark's (fight moments) or a set's (its ability).
6. **Fits the pages.** It shows on the Journal and Tasks through their existing hooks, with no new menu and no daily list.
7. **Multiplayer-safe.** Knowledge is per player, the server judges everything, and two players can't break each other's
   work.

### 1.3 Non-goals

- Not a weapons or armour change. Forging, tiers, tempering, Raise, attunement, inlay, Marks, Weaves, set abilities
  and mastery stay with their owners: the weapon-forge session, arms and armour.
- Not a combat change. No new fight-moment triggers, nothing that changes deflects, dodges or posture (the boss-fights
  session owns those).
- No information powers. No enchantment shows creatures, ores or blocks you couldn't see (Euan's armour rule, kept here).
- Nothing paid. If a server ever sells anything, it's looks only.
- No mixins.

### 1.4 How we'll know it worked

| Goal | Recognised by |
|---|---|
| On par with the armour | Euan inscribes his first enchantment in game and wants the next one; CI tour shots of the scene and an inscribed set read as AWAKEN, not vanilla |
| Found, never rolled | the vanilla table rolls nothing; a GameTest checks every enchantment in the Journal has at least one way in |
| Linked to the forge | a Flawless piece holds four enchantments and a Plain piece one (GameTest); depth follows the item's rank and your Study |
| No overlap | no enchantment changes base damage or armour, adds an element or fires on a fight moment (GameTest over the data) |
| Fun the 20th time | a new enchantment takes 10-15 s; one you've written cleanly before takes 2 s, from memory |
| Safe | a wrong or replayed packet can at most lose a third of the levels' refund; nothing gets written to the wrong item (GameTests) |

## 2. Verified audit and diagnosis

### 2.1 What exists today (read in the code, not seen in game)

| Piece | What it does | Evidence | Consequence | Verdict |
|---|---|---|---|---|
| Vanilla enchanting table | random rolls from lapis, levels and bookshelves | no override anywhere in `src/main/resources/data/minecraft`; no enchanting mod in `pack/mods.json` | a gamble, nothing to do with gates or the forge | replace (fold) |
| Vanilla anvil | glues books onto items, merges items | only `living/craft/CraftEvents.onAnvil` touches it (Patch perk: −1 level) | bypasses any rule we set | keep repair and rename, stop enchanting |
| The scribe's books | 3 random tradeable books a day, 9-25 emeralds | `village/Trade.books` | since the Journey, only holdfasts and Carillon have people; random, no direction | replace with pages |
| Vanilla loot | enchanted books and gear in vanilla structures and our ruins' outer caches | `data/awaken/loot_table/chests/landmark_outer.json` uses `enchant_randomly` and `enchant_with_levels` | random, but at least it's found | keep, as "old books" you read |
| **The 23 sculpted armour sets** | **take no enchantment at all** | `ArmourItems` registers `ForgedArmourItem`s; they're in no `#minecraft:*_armor` tag (`src/main/merge/*/data/minecraft/tags/item`) and don't override `supportsEnchantment` (NeoForge `IItemExtension:405` checks the enchantment's `supported_items` tag) | **a player in Protection IV diamond loses it by switching to a sculpted set.** Only the six Hunter sets can be enchanted | fix first (stage 0) |
| Weapons' and armour's look | one "foil" bit drives a generic violet shimmer | `WeaponRenderer:68` (bit 256), `arms_weapon.fsh:81`, `armour.fsh:95` | every enchantment looks the same; Marked weapons use the same glint with no enchantment (`ArmsSpoils:144`, `ArmsPatterns:78`) | replace with a look per family |
| Smithing perks | "masterwork" 10% Unbreaking I, "keen" 20% Sharpness I on crafted gear | `CraftEvents:250-251` | Sharpness stacks on tempering | keen changes (§8) |
| Hearth tempering | +1 damage or +0.5 armour and +0.25 toughness a level, up to 6 | `hearth/Upgrades.applyTemper` | this is the forge's "how strong"; Sharpness and Protection stack on it | enchanting stays out of it |
| Marks | 48 trigger + effect perks on found weapons, two at most | SPEC §25 "Marks", `awaken:arms` component | already enchantment-like for fight moments | untouched; enchantments never fire on fight moments |
| Armour inlay, attunement | a Force and a small stat | SPEC §44.4, §25 "The Forge" | the element is theirs | untouched |
| The Study | a Hearth room (lectern and bookshelves); its only gift is halving Blueprint paper | `RoomType.STUDY`, `HearthHooks.study`, SPEC §28 line 8354 | a room with little to do | give it enchanting's depth |
| Delver's Pick | six verses set its speed and uses; in `#minecraft:pickaxes` "so enchanting takes it" | SPEC §34.4 | Efficiency V would dwarf the verse ladder (4 → 10) | no Efficiency on the pick |
| Hands | a page turned at an enchanting table (`fp_page`) | `anims/client/Touches:245` | the only first-person touch enchanting has | reuse, then the full scene |

**26.3 enchantments are data** (checked in `.ref`): `data/<ns>/enchantment/*.json` with effect components such as
`damage_protection`, `damage`, `attributes`, `mob_experience`, `block_experience` and `repair_with_xp`, each with
loot-condition `requirements` (damage-source tags, entity predicates). Which enchantments the table, random loot, trades
and mob gear use is set by enchantment tags: `in_enchanting_table`, `on_random_loot` (vanilla's dungeon books use it),
`tradeable`, `on_mob_spawn_equipment` and `on_traded_equipment`. Mods can register their own effect component types
(`Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE` is an ordinary built-in registry). NeoForge's `neoforge:never` condition
turns a vanilla recipe off. Our Forces already have damage types (`data/awaken/damage_type/heat|frost|charge|gravity|spirit`).

### 2.2 Why today's enchanting disappoints (root causes)

1. **It's a separate, older game.** The table rolls dice from lapis and levels; nothing about it knows gates, the forge,
   ranks or the Hearth. That's the "bolted on" feeling, and it's structural, not a polish problem.
2. **It does the forge's job.** Sharpness and Protection add the same kind of number tempering and tiers add. The combat sim
   was tuned on unenchanted gear (docs/combat/PLAN.md line 125: "full unenchanted diamond armour"), so today's
   enchantments push every fight past its design.
3. **It actively works against the new armour.** The 23 sculpted sets can't take enchantments, so the best-looking gear can
   be the weaker choice.
4. **It has no look.** One violet shimmer for everything, shared with "this weapon is Marked".
5. **It has no path.** Books come from dice, a scribe almost nobody can reach, or luck in a chest. The Journal and Tasks
   know nothing about it.

What's not the cause: vanilla's enchantment effects themselves. They're data, they're solid, and JEI, Jade and the
tooltips all understand them. The problem is how you get them and what they're allowed to do.

### 2.3 What we reuse

- Vanilla's enchantment engine: effects, tooltips, the `minecraft:enchantments` item component, the grindstone.
- The Forge Song's shape: `ForgeCamera` (cinematic or first person), the act-and-clamp packets (`ArmourForge.clamp`),
  "from memory", the result card and the sound pipeline (`tools/art/specs/audio_*.py`).
- How armour patterns drop (`ArmourPatterns.onDeath`: `MobRole` gives the gate, role and content level), and how
  Blueprints go straight into the pack (`ArmsPatterns`).
- The Journal's catalogue (`Catalogue`, a fixed list of numbered slots per category) and Tasks' `core.api.Goals`.
- The Hearth's Study room and `HearthHooks`.
- The effects engine (`FxClient`: pools, budgets, our own sprites) and the weapon and armour shaders.

## 3. Cases, before and after

Today's half is read in the code, not reproduced in game. The "after" half is the proposal.

1. **Ordinary: a D-rank hunter with a Beastbound Longsword.**
   - *Today:* they build a vanilla table, gather 15 bookshelves and grind 30 levels. They roll "Sharpness III, Unbreaking
     II", with a violet shimmer.
   - *After:* the first Ember Mines Lord they killed gave them a page, and reading it taught them Heat Ward. Their Bell
     Crypt clear taught Bane of the Dead. At their Inscribing Table, the blade lies on the slate and the book opens to what
     fits. They trace Bane of the Dead's mark and its light runs down the blade in red. Then they go back to the Bell
     Crypt to farm it.
2. **The disappointing case: the forged set that can't be enchanted.**
   - *Today:* they forge a Masterwork Cleftrunner set and take it to the table, which offers nothing. Their old
     iron set with Protection III protects them better.
   - *After:* stage 0 alone fixes the refusal. With the plan built, the Masterwork set has room for three enchantments a
     piece, while their old vanilla iron has room for two.
3. **Strong today: the Forge Song.** It sets the bar this plan matches: a camera, hands, a short skill game, quality
   that matters, "from memory".
4. **Ambiguous: a Lord-forged weapon.**
   - *Today:* it takes Sharpness V on top of Temper VI and its Mark, so its damage runs far past the sim.
   - *After:* it has room for three enchantments, none of which add raw damage. Its Mark and Art stay its identity.
5. **Missing data: no scribe, no luck.**
   - *Today:* a hunter far from a holdfast never sees a book.
   - *After:* every kind of gate keeps its own enchantment, its Lord carries the page, and the System says so once.
     Carillon's market sells a few common ones, and any old book they find still teaches its enchantment.

## 4. Alternatives and the recommendation

| | A. Repair | **B. Fold in (recommended)** | C. Replace |
|---|---|---|---|
| What | keep the table and anvil, tag the 23 sets, recolour the shimmer | keep vanilla's enchantment *effects*; replace how you get them, write them and what they may do | our own "inscription" component and effect engine; vanilla enchantments gone |
| Look and scene | none new | a station, a scene, a look per family | the same as B |
| Found in the world | no | yes | yes |
| Overlap fixed | no | yes, by a rule | yes |
| Compatibility | full | full (JEI, Jade, tooltips, old items) | old enchanted items and vanilla loot become meaningless |
| Risk and size | tiny | medium | large: rewrites what vanilla's data engine already does well |

**B wins** because it delivers everything in the brief while reusing an engine that already works and that every other
tool understands. A fails the brief: it's the bolted-on table with a coat of paint. C spends its extra cost on
re-implementing effects instead of on the look and the scene, which is what Euan will judge. A fourth option, hiding
enchanting entirely, throws away Mending, Fortune and Silk Touch for no gain.

**What would change this:** if the scene tests badly (it feels like a chore), the trace step shrinks to "from memory" for
everything and the cinematic part becomes the wake alone. Or if 26.3's data engine can't express a ward cap: it can't, so
the five Force wards are written in our own code (§5.6).

## 5. The design

### 5.1 The one rule

> **Your forge decides how strong a thing is. Enchantments add what the forge can't.**

An enchantment may add one of these:
- what it guards against (wards);
- which kind of foe it's deadly to (banes, never Lords);
- what you get from what you break or kill (work and spoils);
- where it carries you (water, falls, quiet steps);
- how it keeps itself (Unbreaking, Mending).

It never adds base damage, armour or speed, never an element, and never fires on a fight moment. So Sharpness,
Protection, Power, Fire Aspect, Knockback, Sweeping Edge, Thorns and the rest leave every source in new worlds. Items
that already carry them keep them.

### 5.2 The enchantments (30, as the Journal numbers them)

Engine: **V** = vanilla's own JSON, **O** = our own JSON in `data/awaken/enchantment/`, **C** = our JSON plus a small effect
type in our code. "Gate" is the kind of gate whose Lord keeps its page.

| # | Enchantment | Does | Fits | Top | Engine | Comes from (stage 1) |
|---|---|---|---|---|---|---|
| | **Wards** | | armour | | | |
| 1 | Heat Ward | less Heat damage; fire and lava burn you shorter | armour | III | C | Ember Mines |
| 2 | Frost Ward | less Frost damage; freezing hurts less | armour | III | C | Frost Monastery |
| 3 | Charge Ward | less Charge damage, lightning too | armour | III | C | Clockwork Foundry |
| 4 | Gravity Ward | less Gravity damage | armour | III | C | Sky Archipelago |
| 5 | Spirit Ward | less Spirit damage and magic | armour | III | C | Mirror Palace |
| 6 | Blast Protection | less from explosions | armour | IV | V | Precursor Lab |
| 7 | Projectile Protection | less from arrows and thrown things | armour | IV | V | Glass Desert |
| 8 | Feather Falling | falls hurt less | boots | IV | V | the market, old books |
| 9 | Clear Blood | poison, wither and spores wear off twice as fast (three times at II) | armour | II | C | Fungal Hollow |
| | **Banes** (never on a Lord, boss, guardian, sovereign or named beast) | | weapons | | | |
| 10 | Bane of the Dead | +8% a level against the undead | weapons | III | C | Bell Crypt |
| 11 | Bane of Crawlers | +8% a level against spiders, bees and other crawlers | weapons | III | C | Hive |
| 12 | Bane of Beasts | +8% a level against wild herds, packs and hunted quarry (never kin) | weapons | III | C | the market |
| 13 | Bane of the Deep | +8% a level against the Hollows and the Descent's creatures | weapons | III | C | the market, old books |
| | **Work and spoils** | | | | | |
| 14 | Looting | more drops from creatures | weapons | III | V | Lantern Night Market |
| 15 | Fortune | more from ores | tools | III | V | old books |
| 16 | Silk Touch | blocks come away whole | tools | I | V | old books |
| 17 | Efficiency | digs faster (never on the Delver's Pick: its verses are its speed) | tools | V | V | the market, old books |
| 18 | Harvest | a one-in-two chance a level of one more hide, bone or pelt from wild beasts | weapons | III | C | the market |
| 19 | Insight | 15% more experience a level from what you kill and mine | weapons, tools | III | O | the market, old books |
| | **Carrying** | | | | | |
| 20 | Respiration | breathe longer under water | helm | III | V | Drowned Archive |
| 21 | Aqua Affinity | dig at full speed under water | helm | I | V | the market, old books |
| 22 | Depth Strider | swim and walk faster under water | boots | III | V | the market, old books |
| 23 | Swift Sneak | move faster while sneaking | leggings | III | V | Nightmare gates |
| | **Keeping** | | | | | |
| 24 | Unbreaking | wears out slower | anything | III | V | Overgrown Temple |
| 25 | Mending | experience you pick up mends it | anything | I | V | Abyssal Shrine; rarely old books |
| | **Bow, trident and line** | | | | | |
| 26 | Infinity | one arrow is enough | bows | I | V | old books |
| 27 | Loyalty | a thrown trident comes back | tridents | III | V | old books |
| 28 | Riptide | a trident throws you in water and rain | tridents | III | V | old books |
| 29 | Luck of the Sea | better catches | rods | III | V | the market, old books |
| 30 | Lure | quicker bites | rods | III | V | the market, old books |

**Left out of every source in new worlds**, with the reason:
- Protection, Sharpness, Power and Efficiency on the pick: the forge's numbers.
- Fire Aspect, Flame and Channeling: elements and attunement.
- Knockback, Punch, Sweeping Edge, Thorns, Breach, Density and Wind Burst: fight moments and how Arms swings.
- Fire Protection: folded into Heat Ward.
- Smite and Bane of Arthropods: replaced by our banes, which spare Lords.
- Frost Walker: the Frostwalker set's twist.
- Soul Speed: nothing in our world uses it.
- Impaling, Piercing, Multishot, Quick Charge and Lunge: strength or fight timing.
- The curses: no fun.

**Gate to enchantment map** (data, `data/awaken/enchant/catalogue.json`): Hive Bane of Crawlers, Ember Mines Heat Ward,
Bell Crypt Bane of the Dead, Fungal Hollow Clear Blood, Overgrown Temple Unbreaking (the five E gates); Drowned Archive
Respiration, Glass Desert Projectile Protection, Lantern Night Market Looting, Clockwork Foundry Charge Ward (D); Frost
Monastery Frost Ward, Mirror Palace Spirit Ward, Precursor Lab Blast Protection, Nightmare Swift Sneak (C); Sky
Archipelago Gravity Ward (B); Abyssal Shrine Mending (A). An S gate of a theme gives that theme's.

### 5.3 Learning: pages, once and for good

- **An Enchantment Page** (`awaken:enchantment_page`, one item, its enchantment held in the vanilla `stored_enchantments`
  component so tooltips work). Use it to learn the enchantment for good; the page is spent. A page you already know says so
  and can be given to a friend. Our art: a dark blue-black sheet with the enchantment's mark in lapis light and a seal in its
  family's colour.
- **Knowledge** is per player: attachment `awaken:enchant_known` (schemaVersion 1: known ids with where and when, the marks
  in memory, the gate kinds already paid out, the one-time line told). It's kept through death.
- **Where pages come from (stage 1):**
  - **Gates.** The first time you kill a kind of gate's Lord, its page goes straight into your pack (never the Satchel),
    to every fighter who hit it (like Blueprints). A later Lord of a kind you've cleared gives the page again one time in
    five, as a spare. Handled by our own death listener reading `MobRole`, the way `ArmourPatterns` does.
  - **The market.** Wherever a scribe sells books today (`village/Trade.books`), they sell pages instead: three a day from
    the market list, priced by the enchantment's top level, never one you know.
  - **Old books.** A vanilla enchanted book from any chest, fish or old save teaches you every enchantment on it that
    can still be written, and becomes a plain book. One that can't ("This one can't be learned any more") stays as it is.
    `on_random_loot` is set to the inscribable list (no gate-only ones), so old books can carry our own enchantments too.
- **Direction without the answer:** with your first page, the System says once: "Every kind of gate keeps its own
  enchantment, and its Lord carries the page." Gate cards never name it.
- **Stage 3 adds** Precursor Word Stones (a carved stone in ruins: hold your hand to it to copy its enchantment, once per
  player, like Pattern Stands), the Deep (Fortune, Silk Touch, Bane of the Deep) and the hunts (Harvest, Bane of
  Beasts).

### 5.4 The Inscribing Table and the scene

**The block** (`awaken:inscribing_table`):
- A heavy desk of dark wood under a slate top, a lapis-inlaid channel running round its edge, and a brass book stand at
  the back. Your book floats open over the stand, turning to the page you look at.
- Its recipe is a book, three lapis, an essence shard, planks and stone. It unlocks when you first hold a page.
- In a Study, the bookshelves' letters drift to the book: vanilla's best effect, redone with our own script sprites.
- While someone writes, the channel fills with the enchantment's colour.

**The scene** (`enchant.client`; camera like `ForgeCamera`: cinematic by default, first person in settings; HUD, block
outline and body step aside; nothing pauses the game):
1. **Lay it down.** Use the table holding the piece. The camera settles over the desk, and your off hand lays the piece flat
   on the slate: the weapon's own mesh, or the armour piece's.
2. **The book.**
   - The left page shows the piece: its name, what's on it and its room ("Room for two more").
   - The right page shows the enchantments you know that fit it. Each one has its mark, one plain line on what it does,
     the depth it can take here, and its price ("6 levels and 2 lapis").
   - An enchantment the piece can't take is greyed, with the reason: "It can't go with Projectile Protection", "No room
     left: scrape one off first", "Deeper than II needs your Study".
3. **Choose.** Its page lifts, and the mark burns faint onto the piece: on a blade's flat, an armour piece's breast or a
   tool's head.
4. **Trace.** Your main hand holds the table's burin and draws the mark's strokes (3-6, about a second each). Staying on
   the line fills the cut with lapis light and rings that stroke's note. Wandering scratches dully, but nothing ever
   fails.
5. **Wake.** The piece lifts a finger's width, turns, and the new light runs along it in its family's colour. All of its
   enchantments ring together as one chord. A result card follows, for example: "Bane of the Dead II is on your
   Beastbound Longsword. A clean hand: you got 2 levels back."

**Skill, but never failure:**
- A clean hand (every stroke finished, 80% or more on the line) gives back a third of the levels.
- The first clean trace of an enchantment puts its mark **in memory**. From then on, "From memory" writes it in one
  stroke, in 2 s, at full price.
- **Scrape** takes an enchantment off (the burin across it). It's free and gives nothing back. What you've learned is never
  lost, so trying things is cheap.

### 5.5 Room, depth and price: the links to the forge and the Hearth

- **Room** is how many enchantments a piece holds:

  | Piece | Room |
  |---|---|
  | Forged armour, by the Forge Song's quality | Plain 1, Fine 2, Masterwork 3, Flawless 4 |
  | Forged weapons, by the War Anvil's quality | the same scale (agreed with the weapon-forge session, §8.1). Until their quality lands, 2; after it, weapons made before the anvil count as Plain (their rule) |
  | Found weapons (Spoils, Marked, Lord-forged) | 3 |
  | The Delver's Pick, by its verse | 1, then one more at II, IV and VI (up to 4) |
  | Unforged armour, weapons and tools (vanilla gear, the Hunter sets) | 1, the same as Plain, so a forged piece is never behind |
  | Bows, crossbows, tridents and rods (nothing forges them) | 2 |

- **Depth** follows the piece's rank: E pieces I, D II, C III, B and up the enchantment's top. Vanilla gear counts by its
  material: leather, gold and chain E, iron D, diamond C, netherite A; bows, tridents and rods C.
- **Your Study.** Anything deeper than II needs a table inside your Study: the Hearth's existing room of a lectern and
  bookshelves, read through a new `HearthHooks.studyAt(level, pos)`. The Study's gift line becomes "Enchantments written
  here can go deeper". It matters from C-rank pieces on.
- **Price** is three levels of experience and one lapis per level of depth (I costs 3 and 1, III costs 9 and 3). Mending and
  Silk Touch cost 10 levels and 4 lapis. Deepening pays only the difference. Creative mode is free. This keeps vanilla's XP
  bar meaningful. The numbers are a start, to check against a gate run's XP on the first CI tour.
- **Archetype affinity:** each armour archetype writes its own kind one level deeper than its rank allows (still within
  the top):
  - Bulwark: wards;
  - Strider: carrying;
  - Channeler: keeping;
  - Duelist: none, because its pieces go to banes on the weapon;
  - Lord-forged: any one enchantment.
  The piece's set is read from `ArmourLooks.of(stack)`.

### 5.6 What the new effects do, exactly

- **Force wards** (C: effect type `awaken:ward` {force, per_level}; our `LivingIncomingDamageEvent` listener, not
  vanilla's shared protection pool):
  - each worn piece takes 2.5% a level off that Force's damage. Only hits typed as a Force count (`core.Forces`' damage
    types, which creatures' abilities already use through `Abilities`); melee is never warded. Boss fights confirmed a
    kit boss's melee lands as `mobAttack` (`FightRunner`), and a themed boss's charges, leaps and slams are abilities, so
    they're Force-typed and warded;
  - a whole suit takes at most 30% off one Force from ordinary creatures, and at most **20%** from anything banes skip
    (Lords, Wardens, guardians, sovereigns, named beasts), agreed with the boss-fights session (§8): their fights are
    tuned so a player who stands and trades wins at most 10% of the time, and 30% against a themed Lord's own Force
    would let trading through;
  - Heat Ward also covers `#minecraft:is_fire` and shortens burning (vanilla's `burning_time` attribute, as Fire
    Protection does).
- **Banes** (C: `awaken:bane` {kind}):
  - +8% a level, applied last, after the Arms damage stack;
  - kinds: undead and crawlers by vanilla entity tags; beasts by the Wild Lands' herd, pack and hunt tags; the Deep by
    the delve and descent creature tags;
  - never against a `MobRole` of BOSS, guardian or sovereign, a named beast, or another player.
- **Clear Blood** (C): poison, wither and our spore effects lose an extra tick (two at II) each tick.
- **Harvest** (C): on a wild beast's death, a one-in-two chance a level of one more of its hide, bone or pelt drop.
- **Insight** (O): vanilla `mob_experience` and `block_experience`, ×1.15 a level.
- **Efficiency** (V override): `data/minecraft/enchantment/efficiency.json` with its `supported_items` listed so the
  Delver's Pick isn't in it.

### 5.7 The look

Every family has a colour and a mark style, used everywhere the enchantment appears: the page, the book, the item, the
Journal and the effects.

| Family | Colour | Mark style |
|---|---|---|
| Wards | each Force's own: Heat ember, Frost ice, Charge yellow, Gravity violet, Spirit teal; steel for the others | closed shapes and rings |
| Banes | blood red | points and spears |
| Work and spoils | gold | open cups |
| Carrying | sea blue | waves |
| Keeping | warm white | knots |

- **On our weapons and armour:**
  - The single violet shimmer gives way to the piece's own enchantment light: a thin script line running along the
    blade's length (the mesh's flow coordinate) or the armour's seams, in each enchantment's colour, lighting in turn.
  - A Marked weapon with no enchantments keeps its violet glint.
  - The colour reaches the shaders through the packed overlay. The weapon shader's foil test changes from `>= 256` to a bit
    test. Read in the shaders: there's room.
  - Vanilla items keep vanilla's glint.
- **Enchantments at work** (stage 2, on the effects engine with its budgets):
  - a ward's mark flashes where the blow landed, in the Force's colour;
  - a bane leaves a brief mark of light on the creature;
  - Looting, Fortune and Harvest make the extra drop glint gold as it falls;
  - Silk Touch lifts the block out whole with a soft ring;
  - Mending's orbs bend into the piece along a thread of light;
  - Depth Strider leaves a faint wake, and Feather Falling a puff of light on landing.
- **Sounds** (`awaken:enchant.*`, `tools/art/specs/audio_enchant.py`; every CC0 download listed to Euan first, as for
  mining):
  - the book's heavy leather and its pages;
  - the burin on metal or on leather;
  - each family a voice for its notes: a low bowl for wards, struck steel for banes, chimes for spoils, a breathy pipe
    for carrying, a warm hum for keeping;
  - the piece's chord at the wake;
  - a quiet paper hum while letters drift in a Study.

### 5.8 The vanilla fold, and old worlds

- **The vanilla enchanting table:** its recipe is off (`neoforge:never`). Any vanilla table you use, in an old world or
  anywhere else, turns into an Inscribing Table in place, with a soft flare, then opens the scene.
- **Tags:**
  - `in_enchanting_table` is empty;
  - `on_random_loot` is the inscribable list (no gate-only ones);
  - `tradeable` is the market list, so old worlds' vanilla librarians still sell books, and reading one teaches it;
  - `on_mob_spawn_equipment` and `on_traded_equipment` are keeping and carrying only, so no mob spawns with a ward or a
    bane.
- **The anvil** repairs and renames. It never adds or merges enchantments (the left item keeps its own).
- **The grindstone** still strips enchantments, but gives back little experience: never more than a third of what they
  cost.
- **Old items keep everything**, including Sharpness and Protection. A piece already over its room says "Full" until
  you scrape one off.
- **The 23 sculpted sets take enchantments** from stage 0: our own merge file adds them to vanilla's `enchantable/*` armour
  tags (no armour files touched). A GameTest checks every `ForgedArmourItem` is in.

### 5.9 Tasks, the Journal and tooltips (through their existing hooks)

- **Journal:** a new category, **ENCHANTMENTS n/30** (one `StatusSections.Shelf` constant and a `Catalogue` slot list).
  - Found: its mark, name, what it does in plain words, what it fits, its top, and where you learned it ("From the Ember
    Tyrant, the Ember Mines' Lord").
  - Not found: "?" and its number, nothing else.
- **Tasks:** every step of enchanting shows on the K menu's Tasks page (Euan 2026-11-02: so you know it's possible).
  One `Goals.Source` from our package (`enchant.EnchantGoals`, registered with `core.api.Goals.register`) gives these
  steps, each keyed, with one plain why line and have/need rows. A step leaves the page the moment it's done. "Its tab"
  is Armour for an enchantment that fits armour and Weapons for one that fits weapons or tools.

  | Key | Tab | Shows from | Step and its why | Needs (have/need) | Place | Leaves when |
  |---|---|---|---|---|---|---|
  | `enchant/page/armour` | Armour | your first armour made | **Find an enchantment for your armour.** "Wards against the five Forces are written onto armour. Every kind of gate keeps its own enchantment, and its Lord carries the page." | An enchantment page 0/1 | optional | you know one that fits armour |
  | `enchant/page/weapons` | Weapons | your first weapon held | **Find an enchantment for your weapon.** "Banes, Looting and Unbreaking are written onto weapons. Carillon's market sells a few pages, and gates keep the rest." | An enchantment page 0/1 | optional | you know one that fits weapons |
  | `enchant/read` | its tab | you carry a page you haven't read | **Read the page you're carrying.** "Reading it teaches you Heat Ward for good." | Read it | main path, `PATH_FIRST_GATE + 1` | read |
  | `enchant/table` | the tab of the first one you know | you know an enchantment and have never set down a table | **Make an Inscribing Table.** "It's where you write what you've learned onto your own gear." | Book 1, Lapis 3, Essence Shard 1, planks and stone | main path, `PATH_FIRST_GATE + 2` | you set one down |
  | `enchant/first` | the piece's tab | you know one and have a table | **Inscribe your first enchantment.** "Heat Ward fits your Fine chestplate." | A piece it fits, Levels 3, Lapis 1 | main path, `PATH_FIRST_GATE + 3` | your first inscription |
  | `enchant/room/armour`, `enchant/room/weapons` | Armour, Weapons | after your first inscription, while a piece you carry has room and you know something that fits it | **Inscribe your armour** (or weapon). "Your Masterwork chestplate has room for two more, and you know three that fit." | Levels and Lapis for the cheapest | optional | full, or nothing you know fits |
  | `enchant/study` | the piece's tab | you carry a C-rank piece or better and know an enchantment it could take past II | **Set your Inscribing Table in your Study.** "Written there, enchantments can go deeper than II." | A Study, The table inside it | optional | a table stands in your Study |

  - The three main-path steps sit between your first gate and the Anchor (`PATH_FIRST_GATE` 50 < 51-53 < `PATH_ANCHOR`
    55), so right after the first Lord hands you a page, the top step on Now is to read it, make the table and write it.
    Each takes a minute, and none shows until you hold a page, so nobody who skips enchanting is held up.
  - The page steps never name which gate keeps which enchantment; the Journal fills that in once you've found it.
  - No step is daily and none repeats; the room steps are one row per tab at most.
- **Where-from lines:** `awaken.where.awaken.enchantment_page` ("A gate's Lord carries one the first time you clear that
  kind of gate. Carillon's market sells a few, and old books teach theirs.") and the table's.
- **Tooltips:** every enchantable piece says its room plainly ("Room for one more enchantment", "No room left"). The
  vanilla lines name what's on it.

### 5.10 Multiplayer and safety

- Knowledge, memory and gate payouts are per player. Pages are items, so friends can trade them. The market's daily list is
  shared, filtered per player.
- One writer per table at a time: the block's synced state shows the piece on the slate and its light to everyone near.
  Anyone else gets "Someone is writing here".
- **Server-authoritative.** Choosing takes the price and opens a session: table, hand, a snapshot of the piece, the
  enchantment and level, the start tick. The client sends each stroke's coverage and time (clamped as the Forge Song's acts
  are). Writing always succeeds, so the trace only decides the refund, and a cheating client gains at most a third of the
  levels.
- **Cancels refund everything:** Esc, walking 6 blocks away, logging out, the table broken, or the piece changed or swapped.
  The enchantment is written only onto the same piece the session started with.

### 5.11 Data, state and lifecycle

- **What's on an item** is vanilla's `minecraft:enchantments` component, the single source of truth. Room is derived from
  what the item already says, read through public hooks only: `core.api.ForgeHooks.quality` and `rank` for forged pieces
  (8.1), a new `ArmsApi.found(ItemStack)` (Spoils, Marked or Lord-forged) and a new `delve.api.Pick.verse(ItemStack)`
  (stage 3). No new item state is saved, and this module never reads another module's component directly.
- **Saved:** `awaken:enchant_known` (player). Not saved: the open session (refunded at logout).
- **Data:** `data/awaken/enchant/catalogue.json` (schemaVersion 1: order, family, gate theme, market, note, colour, mark
  strokes), read on both sides. The marks are generated by `tools/art/specs/enchant_marks.py`.
- **A removed or renamed enchantment id** is skipped and logged. Its page says "The writing has faded" and turns to paper.

### 5.12 Failure register (the important cases)

| Case | Detection | What the player sees | State kept | Test |
|---|---|---|---|---|
| Piece swapped mid-scene | hand stack ≠ snapshot | the scene closes; "Nothing was written" | price refunded | `enchant_inscribe` |
| Table broken mid-scene | block gone | the same | refunded | `enchant_inscribe` |
| Not enough levels or lapis | at choose | the price shown in amber; Inscribe disabled | — | `enchant_inscribe` |
| Exclusive enchantments | vanilla `exclusive_set` | greyed, with the reason | — | `enchant_room_depth` |
| Over room (old item) | count > room | "Full"; scrape offered | everything on it | `enchant_room_depth` |
| A page you know | known set | "You already know this. You can give it away." | page kept | `enchant_learn` |
| An old book of a left-out enchantment | not inscribable | "This one can't be learned any more" | book kept | `enchant_learn` |
| A Lord killed by a decoy or reflection | no player killer | no page (as patterns) | — | `enchant_gate_page` |
| Two players at one table | the table's writer slot | "Someone is writing here" | — | `enchant_inscribe` |
| A replayed or forged packet | session id and clamp | ignored | — | `enchant_inscribe` |
| Shader pipeline fails | as weapons and armour | vanilla entity light, no script line | — | existing fallback |

### 5.13 Performance and cost

- The scene runs on the client.
- The server does work only at choose and finish, plus one damage-event check for wards and banes, reading the worn
  pieces' enchantments. There's no per-tick work and nothing allocated in hot paths.
- The effects use the existing engine's pools and budgets.
- There's no new mesh budget: the piece on the slate is its own mesh, and the table is one block model plus the book.

## 6. Evaluation and delivery

### 6.1 Checks

- **GameTests (one per mechanism, prefix `enchant_`):**
  - `enchant_rule`: no left-out enchantment in any source tag; every catalogue entry has a way in; no inscribable effect
    adds base damage, armour or an element;
  - `enchant_learn`;
  - `enchant_gate_page`;
  - `enchant_room_depth`, which also covers the Study and archetype affinity;
  - `enchant_inscribe`: price, write, clean refund, cancels, swap, two writers;
  - `enchant_ward_cap`: 30% from a creature, 20% from a Lord, nothing off its melee;
  - `enchant_tasks`: the steps appear and leave as in §5.9, on the right tabs;
  - `enchant_bane_spares_lords`;
  - `enchant_fold`: anvil, old table, grindstone refund;
  - `enchant_forged_sets_take`;
  - `journey_ledger` for the table and the page.
- **One CI tour per stage** (`tools/tour-ci.sh`), with shots I review before hand-off:
  - the table in a Study;
  - each beat of the scene;
  - an inscribed Lordscale Longsword and a Bellguard set on a stand, by day and at night;
  - the Journal's ENCHANTMENTS with found and "?" slots;
  - the Tasks steps.
- **Euan in game:** his first gate's page, the table, his first inscription.

### 6.2 Stages (each one shippable)

0. **The sculpted sets take enchantments** (an hour; can ship before anything else). Tags and `enchant_forged_sets_take`.
1. **The table and the rule.** The block and its first art, the scene (all five beats, from memory, scrape), pages and
   knowledge, all 30 enchantments, the vanilla fold, room, depth, the Study and price, the gate, market and old-book doors,
   the Journal category, the Tasks steps, tooltips, SPEC §45, the ledger rows. A CI tour.
2. **The look and the sound.** The script line and family colours on our weapons and armour, the enchantments at work,
   the table's final art and the Study's drifting letters, the sounds (CC0 list to Euan first). A CI tour.
3. **More doors and links.** Precursor Word Stones, the Deep, the hunts, the Delver's Pick room by verse, the weapon
   forge's quality as room once the War Anvil's quality lands (their stage 2), and a "Room for N enchantments" line on the
   Forge Song's and War Anvil's result cards through `ForgeHooks.cardLine`.

**Effort:** at the pace the armour was built (P0-P5 in about a day), stage 1 is about a day and stages 2 and 3 about half a
day each. The scene's camera and the shader line are the parts most likely to take longer.

### 6.3 Rollout and rollback

- Each stage merges only on green CI and reviewed shots.
- If stage 1 has to be rolled back, the tags and the old-table swap go with it, and vanilla enchanting comes back as it
  was. Items keep whatever was written on them, since it's vanilla's component.
- The scene has a fallback: a setting skips the trace and writes from the book alone (still costing the full price).

### 6.4 Risks

- **Losing Protection and Sharpness feels like a loss.** Old items keep them. The forged numbers already beat
  vanilla's, and wards and banes add real choices for the gate you're going into.
- **The trace gets old.** From memory after one clean trace; a new one takes 10-15 s.
- **A Lord's death drops a lot.** The page goes straight into the pack with one line, never onto the floor.
- **Wards against bosses.** 20% at most against a Lord's, Warden's or named beast's Force abilities, never their melee;
  banes never touch them. The boss-fights session adds a warded tank to its combat sim and tunes against it (§8).
- **Old worlds' vanilla villagers.** `tradeable` stays non-empty, so librarians keep working.

## 7. Decisions for Euan

1. **Fold vanilla enchanting into our own way.** Vanilla's effects stay; we replace the table's random rolls, books glued at
   the anvil and the scribe's random books. *Recommended: yes.*
2. **The rule:** enchantments never make a thing stronger, so Sharpness, Protection and Power leave every source in new
   worlds (old items keep them). *Recommended: yes.*
3. **Learn once, write many:** a page teaches an enchantment for good. *Recommended: yes.*
4. **Where they come from:**
   - each kind of gate keeps its own (its Lord's page the first time);
   - the market;
   - old books;
   - later, Precursor stones, the Deep and the hunts.
   *Recommended: yes.*
5. **The scene:** trace the mark; a clean hand gives a third of the levels back; from memory after one clean trace.
   *Recommended: yes.*
6. **Room from the forge** (Plain 1 to Flawless 4; found weapons 3; unforged gear 1, the same as Plain; bows, tridents and rods 2) and **depth from the piece's rank**.
   *Recommended: yes.*
7. **Deeper than II only in your Study.** *Recommended: yes.*
8. **The price is experience levels and lapis.** *Recommended: yes.*
9. **Wards against the five Forces and banes that never touch Lords.** A whole suit takes at most 30% off one Force from
   ordinary creatures and 20% from Lords, Wardens and named beasts, and only from Force abilities, never melee.
   *Recommended: yes.*
10. **Names:** vanilla's enchantments keep their names, ours use the same plain style, and the station is the Inscribing
    Table. *Recommended: yes.*
11. **Old worlds keep everything,** and old tables turn into Inscribing Tables. *Recommended: yes.*
12. **Stages 0 to 3 in order,** with stage 0 (the sculpted sets take enchantments) shipping first. *Recommended: yes.*
13. **Every enchanting step on the Tasks page** (§5.9), on the Armour and Weapons tabs, from your first armour on. Reading
    your first page, making the table and writing your first enchantment sit on the main path right after your first
    gate. *Recommended: yes.*

## 8. Overlaps with other sessions

- **weapon-forge (local_eb5d4b8b):** owns weapon crafting, the War Anvil, tiers, quality, tempering, Reforge and
  attunement. The line and the hook are agreed and named the same in both plans (8.1; their PLAN §4.9 and decision 12).
- **boss-fights (local_c937b1f5): agreed 2026-11-02.** No clash with the fight rules. Banes never touch a Lord, Warden,
  guardian, sovereign or named beast. Wards keep working on them, capped at 20% for a whole suit and only on their
  Force-typed hits, never melee (their `docs/fights/PLAN.md` §4.5 tunes each Lord so standing and trading wins at most
  10%). Once this plan lands they add a warded tank to the combat sim and tune against it.
- **armour:** one line registering a `ForgeHooks` quality reader for `awaken:forged` (stage 1, reported), the result
  card's room line (stage 3) and the overlay bits for the colour (stage 2). Stage 0 adds no armour file.
- **arms:** a new one-line `ArmsApi.found(ItemStack)` (stage 1) and `WeaponRenderer` passing the enchantment colour
  (stage 2). Marks, Weaves and the Forge are untouched.
- **hearth:** `HearthHooks.studyAt(level, pos)` and the Study's gift line.
- **progression (Journal):** one `Shelf` constant and a `Catalogue` registration.
- **village:** `Trade.books` sells pages.
- **living:** the Smithing perk "keen" (20% Sharpness I on a crafted weapon) breaks the rule; it should give Unbreaking I
  instead.
- **delve:** room by verse reads a new `delve.api.Pick.verse(ItemStack)` wrapping `PickVerses.verse` (stage 3);
  Efficiency stays off the pick.
- **gate-spoils (local_caaf7fb0):** pages go straight into the pack, never the Satchel, so its loot rules aren't touched.
- **above, wilds and delve (stage 3):** the Word Stone placement calls and the hunts' quarry tag.

### 8.1 The line with the weapon forge (agreed 2026-11-02)

- **The weapon forge owns the metal:** weapon crafting, forging at the War Anvil, tiers, quality, tempering, Reforge,
  attunement to a Force, Blueprints and Marks. **This plan owns what is written into an item after it exists**, on
  weapons and armour alike, found and Lord-forged included.
- Enchanting adds no flat damage or armour (tempering and tiers), no element (attunement and inlay) and no fight-moment
  triggers (Marks). Tempering keeps its numbers.
- **No enchanting step inside the forge scene.** It keeps the forge scene focused; the Inscribing Table is its own place.
- **The hook, `core.api.ForgeHooks`**, one small class named the same in both plans:

  | Call | What it does | Who |
  |---|---|---|
  | `quality(ItemStack)` → `OptionalInt` 0-3 | how well an item was forged (Plain to Flawless); empty if never forged. Enchanting reads it for room | arms registers a reader for `awaken:weapon_forged`, armour one for `awaken:forged` |
  | `rank(ItemStack)` → `"E".."S"` or null | the item's tier. Enchanting reads it for depth | the same readers |
  | `forged(player, stack, quality, station)` | an event after every forge and reforge | arms fires it (armour may); enchanting may listen |
  | `cardLine(...)` (optional) | one line under the forge's result card, "Room for 3 enchantments"; nothing shows if none is registered | enchanting registers it in stage 3 |

- Whichever module lands first creates the class with exactly these calls. Neither module reads or writes the other's
  component. Until the War Anvil's quality lands, `quality` is empty for weapons and they get the default room of 2.

## 9. Build log

- **S0** (d9233f5c9): the 23 sculpted sets take enchantments (tags; `enchant_forged_sets_take`). SPEC §45.1.
- **S1** (13e232d76 to 876b2e465): the neighbours' hooks (`ForgeHooks`, `ArmsApi.found`, `HearthHooks.studyAt`,
  `EnchantApi`); pages and knowledge, the thirty, room, depth and price, the table and its sessions, the vanilla fold,
  Tasks; the table's art and sounds (audiokit); the writing scene, the book's pages and the Journal category; the admin
  commands and the autopilot's pilot. SPEC §45.2-45.10.
- **S2** (b1f75ab58 to b9c69abf6): the script line on our weapons and armour (each enchantment's own colour, in turn), the
  wake's turn; enchantments at work (ward and bane marks, gold on Looting and Fortune drops, Silk Touch, Mending's
  thread, Feather Falling's puff, Depth Strider's wake); the Study's drifting letters; the step to the desk and the band
  framing; the card after the turn. SPEC §45.10-45.13.
- **S3** (da9c808dd, f14e48c76): the hunts' and the Deep's doors (Bane of Beasts, Harvest, Bane of the Deep, Fortune,
  Silk Touch), the Delver's Pick's room by verse (`delve.api.Pick`), the room line on the Forge Song's card, Precursor
  Word Stones in every ruin; the book's lines fit a narrow page. SPEC §45.14.
- **S3, after:** the wake's chord (each other enchantment on the piece adds its family's note over the new one's).
- **Live polish** (2 Nov, Euan: "test all of this in-game ... until it's literally perfect"; driven in a real client by
  `LiveHand`, a dev mouse through Minecraft's own input path): the trace follows the pen and scores by distance (it ran
  ahead and called a careful hand sloppy), a slow frame costs nothing (a hitch to 19 fps had cost 0.54), the burin's
  point at the pen, the off hand out of view, the mark scored into the piece, sparks, a climbing tick and a running cut
  (`EngraveLoop`, metal or leather) on the line, a nudge when a press misses, each stroke sealed (a sweep, sparks, a nod),
  the book down from trace to wake (it flashed up) and up through a scrape (it blinked), the wake's turn framed close,
  the scrape's mark crumbling on the piece, the piece without vanilla's shimmer, lapis-centred pips, "from memory" off
  the level, the Word Stone's hint stepping aside once learned. Checked live: Esc refunds (36/30 to 30/28 and back),
  clean refund, memory, scrape, bane and ward marks, Feather Falling's puff, Mending's thread, a Word Stone learned.
- **Not built:** the War Anvil's quality as weapon room arrives by itself when arms' `ForgeHooks.Reader.quality` returns it (until then our
  forged weapons hold two), and its result card shows the room line once it calls `ForgeHooks.cardLines`.
- **Tours** (CI, `tools/autopilot/enchant-table.txt`): 41434309046 (S2 first look), 41436600832 (desk step, framing,
  card timing), the S3 run with the Word Stone.
