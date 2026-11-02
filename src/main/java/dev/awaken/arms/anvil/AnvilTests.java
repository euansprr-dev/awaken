package dev.awaken.arms.anvil;

import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.Goals;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.Upgrades;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** The War Anvil's GameTests (SPEC §46): one per mechanism. */
final class AnvilTests {
    private AnvilTests() {}

    static void register() {
        AwakenTests.register("arms_anvil_song", 40, AnvilTests::song);
        AwakenTests.registerIsolated("arms_anvil_goals", 100, AnvilTests::goals);
        AwakenTests.register("arms_anvil_costs", 20, AnvilTests::costs);
        AwakenTests.register("arms_anvil_hand", 40, AnvilTests::hand);
        AwakenTests.register("arms_anvil_memory", 40, AnvilTests::memory);
    }

    /**
     * E by hand (SPEC §46.7): a pattern held isn't known until it's used (used up, and a second stays); the Wildhide
     * Daggers need no Forge; eight flakes at the smith's own pace (one too soon after the last isn't taken, one wide scores
     * less), then four even turns of the cord; it stands in the slot by its knap and bind, no flaw. The bind's rules:
     * uneven turns, a hold too quick and a release before the four all score less.
     */
    private static void hand(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(new BlockPos(1, 1, 1), AnvilContent.ANVIL.get().defaultBlockState());
        WarAnvilBlockEntity be = (WarAnvilBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(1, 1, 1)));
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, pos, "anvil-hand", 3);
        String daggers = "wildhide_daggers";
        h.assertTrue("You don't know this pattern yet.".equals(WarAnvil.missing(p, level, pos, daggers, false)), "an unknown style can't be forged");
        Item print = dev.awaken.arms.ArmsItems.blueprintItem("daggers");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(print, 2));
        h.assertTrue(!dev.awaken.core.api.ArmsApi.knows(p, "daggers"), "holding its Blueprint isn't knowing it");
        print.use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(dev.awaken.core.api.ArmsApi.knows(p, "daggers") && p.getMainHandItem().getCount() == 1, "used, it teaches the Daggers and is used up");
        print.use(level, p, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().getCount() == 1, "used again, known: it stays for someone else");
        p.getInventory().clearContent();
        give(p, AnvilCosts.needs(p, daggers));
        h.assertTrue(WarAnvil.missing(p, level, pos, daggers, false) == null, "by hand at E: no Forge needed");
        h.assertTrue(WarAnvil.begin(p, level, be, daggers, false), "begun");
        AnvilWork w = be.work();
        h.assertTrue(w != null && w.stage == AnvilSong.KNAP && w.hand() && w.forge.equals(pos) && !w.materials.isEmpty(), "the knap first, the parts in the work");

        flake(h, p, be, 0F, 0F);
        flake(h, p, be, 3F, 0F);
        h.assertTrue(w.scores.size() == 1, "a flake struck too soon after the last isn't taken: " + w.scores);
        flake(h, p, be, 9F, 1.25F);
        h.assertTrue(w.scores.size() == 2 && Math.abs(w.scores.get(1) - 0.5F) < 0.01F, "wide of its point it scores half: " + w.scores);
        for (int i = 2; i < AnvilSong.FLAKES; i++) flake(h, p, be, 9F * i + 1F, 0F);
        h.assertTrue(w.stage == AnvilSong.BIND, "eight flakes, then the bind");

        h.assertTrue(AnvilSong.evenness(new float[]{10, 10, 10, 20}, 4) < 0.8F && AnvilSong.evenness(new float[]{10, 11, 10, 10}, 4) == 1F,
                "a slow turn among even ones scores less; a tick off is even");
        h.assertTrue(AnvilSong.bind(1F, 4, 12F) == 0.5F && AnvilSong.bind(1F, 2, 30F) == 0.5F, "a hold too quick, or let go at half, scores half");
        for (int k = 0; k <= AnvilSong.TURNS; k++) {
            w.stageStart = level.getGameTime() - 10L * k;
            act(p, pos, AnvilPayloads.WIND, k, 10F * k, 0F);
        }
        ItemStack made = be.slot();
        WeaponForged f = made.get(AnvilContent.FORGED.get());
        float expect = AnvilSong.handScore((7F + 0.5F) / 8F, 1F);
        h.assertTrue(made.is(WarAnvil.item(daggers)) && f != null && f.quality() == AnvilSong.quality(expect, false).ordinal()
                && f.flaw() == AnvilSong.NO_FLAW && !f.memory(), "the Daggers stand in the slot by the knap and the bind (" + expect + "), got " + f);
        act(p, pos, AnvilPayloads.DRAW, 0, 0F, 0F);
        h.assertTrue(p.getMainHandItem().is(WarAnvil.item(daggers)) && SmithMemory.of(p).hasForged("daggers") && SmithMemory.of(p).best() == 0,
                "drawn, and remembered as forged");
        h.succeed();
    }

    private static void flake(GameTestHelper h, ServerPlayer p, WarAnvilBlockEntity be, float t, float aim) {
        AnvilWork w = be.work();
        w.stageStart = h.getLevel().getGameTime() - (long) t;
        act(p, be.getBlockPos(), AnvilPayloads.KNAP, w.scores.size(), t, aim);
    }

    /**
     * From memory (SPEC §46.7): only a weapon forged Masterwork or better is in it; then one heat, one strike, the quench
     * and the draw (no hilt), and however it's struck it comes out Fine. A v1 memory reads empty. A weapon of ours held
     * with no story gains a Plain one; a Marked one never does.
     */
    private static void memory(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(new BlockPos(1, 1, 1), AnvilContent.ANVIL.get().defaultBlockState());
        h.setBlock(new BlockPos(3, 1, 1), BuiltInRegistries.BLOCK.getValue(Awaken.id("forge")).defaultBlockState());
        WarAnvilBlockEntity be = (WarAnvilBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(1, 1, 1)));
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, pos, "anvil-memory", 13);
        give(p, AnvilCosts.needs(p, WarAnvil.WEAPON));
        h.assertTrue(WarAnvil.missing(p, level, pos, WarAnvil.WEAPON, true) != null, "not in memory before it's forged well");
        SmithMemory.of(p).forged("longsword", 1, AnvilSong.Quality.FINE);
        h.assertTrue(!SmithMemory.of(p).remembers("longsword", 1), "a Fine one isn't remembered");
        SmithMemory.of(p).forged("longsword", 1, AnvilSong.Quality.MASTERWORK);
        h.assertTrue(SmithMemory.of(p).remembers("longsword", 1) && !SmithMemory.of(p).remembers("longsword", 0), "a Masterwork is, at its tier");
        h.assertTrue(WarAnvil.begin(p, level, be, WarAnvil.WEAPON, true), "begun from memory");
        AnvilWork w = be.work();
        w.stageStart = level.getGameTime() - 20;
        act(p, pos, AnvilPayloads.PULL, 0, 20F, 0F); // pulled cold
        float due = w.nextDue();
        w.stageStart = level.getGameTime() - (long) (due + 6);
        act(p, pos, AnvilPayloads.STRIKE, 0, due + 6, 3F); // late and wide
        h.assertTrue(w.stage == AnvilSong.QUENCH && w.scores.size() == 1, "one strike draws it out, got " + w.scores);
        w.stageStart = level.getGameTime() - 10;
        act(p, pos, AnvilPayloads.PLUNGE, 0, 10F, 0F);
        w.stageStart = level.getGameTime() - 14;
        act(p, pos, AnvilPayloads.LIFT, 0, 14F, 0F); // lifted early
        WeaponForged f = be.slot().get(AnvilContent.FORGED.get());
        h.assertTrue(be.work() == null && f != null && f.quality() == AnvilSong.Quality.FINE.ordinal() && f.flaw() == AnvilSong.NO_FLAW && f.memory(),
                "no hilt, and Fine however it went: " + f);
        h.assertTrue(f.line().equals("Fine, from memory"), "its words: " + f.line());

        var v1 = com.google.gson.JsonParser.parseString("{\"schemaVersion\": 1}");
        SmithMemory old = SmithMemory.CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, v1).getOrThrow();
        h.assertTrue(old.best() == -1 && !old.remembers("longsword", 1) && !old.hasForged("longsword"), "a v1 memory reads empty");

        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(WarAnvil.item("wildhide_axe")));
        WarAnvil.storyOnHold(p);
        WeaponForged plain = p.getMainHandItem().get(AnvilContent.FORGED.get());
        h.assertTrue(plain != null && plain.quality() == 0 && plain.maker().isEmpty(), "an old weapon held gains its Plain story: " + plain);
        ItemStack marked = new ItemStack(WarAnvil.item("wildhide_axe"));
        dev.awaken.arms.Marks.put(marked, dev.awaken.arms.Marks.Instance.EMPTY.withMark(dev.awaken.arms.Marks.all().getFirst().id()));
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, marked);
        WarAnvil.storyOnHold(p);
        h.assertTrue(!p.getMainHandItem().has(AnvilContent.FORGED.get()), "a Marked one, found, never does");
        h.succeed();
    }

    private static void give(ServerPlayer p, java.util.List<dev.awaken.arms.net.ArmsPayloads.NeedRow> needs) {
        for (var n : needs) {
            if (n.have() >= n.need()) continue;
            Item item = n.kind().equals("tag") ? first(net.minecraft.resources.Identifier.parse(n.id())) : BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(n.id()));
            p.getInventory().add(new ItemStack(item, n.need() - n.have()));
        }
    }

    /**
     * Weapons are never made on the grid (SPEC §46.7): every forged weapon and Hunter blade has its War Anvil cost (its old
     * recipe minus the Blueprint: the style's shape counted) and no crafting recipe makes any of them; a carried blade
     * stands in for its parts, else they're added.
     */
    private static void costs(GameTestHelper h) {
        int weapons = 0;
        for (var w : dev.awaken.arms.ArmsData.WEAPONS) {
            if (dev.awaken.arms.LordForged.byWeapon(w.id()) != null) continue;
            var parts = AnvilCosts.of(w.id());
            h.assertTrue(parts != null && parts.stream().anyMatch(AnvilCosts::blade), w.id() + " has its cost, its tier's blade in it: " + parts);
            h.assertTrue(parts.stream().noneMatch(q -> q.id().endsWith("_blueprint")), w.id() + " takes no Blueprint");
            weapons++;
        }
        h.assertTrue(weapons == 96, "all 96 forged weapons: " + weapons);
        for (String set : new String[]{"wildhide", "beastbound", "gatewrought", "veilwoven", "lordscale", "sovereign"}) {
            h.assertTrue(AnvilCosts.of(set + "_blade") != null, set + "'s Hunter blade has its cost");
        }
        // the shapes counted: two blades in a Greatblade, a Precursor Arm and Twinblades; the tier's metal from D
        h.assertTrue(count(AnvilCosts.of("beastbound_greatblade"), "awaken:beastbound_blade") == 2
                && count(AnvilCosts.of("beastbound_greatblade"), "awaken:tier_metal_d") == 1, "a Beastbound Greatblade: two blades, a core, the metal");
        h.assertTrue(count(AnvilCosts.of("wildhide_daggers"), "awaken:hunter_bind_e") == 1, "E binds with a binding");
        for (var r : h.getLevel().getServer().getRecipeManager().getRecipes()) {
            for (var d : r.value().display()) {
                Item out = d.result() instanceof net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay(var t) ? t.item().value()
                        : d.result() instanceof net.minecraft.world.item.crafting.display.SlotDisplay.ItemSlotDisplay(Holder<Item> i) ? i.value() : null;
                if (out == null) continue;
                String path = BuiltInRegistries.ITEM.getKey(out).getPath();
                h.assertTrue(dev.awaken.arms.ArmsItems.of(out) == null && AnvilCosts.of(path) == null, r.id().identifier() + " makes " + path + " on the grid");
            }
        }
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.getInventory().clearContent();
        var bare = AnvilCosts.needs(p, "wildhide_daggers");
        h.assertTrue(bare.stream().noneMatch(n -> n.id().endsWith("wildhide_blade")) && bare.stream().mapToInt(n -> n.need()).sum() == 6,
                "without a Fang the Daggers ask for its parts too (3 parts, 2 bindings, an essence): " + bare);
        p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(Awaken.id("wildhide_blade"))));
        var withFang = AnvilCosts.needs(p, "wildhide_daggers");
        h.assertTrue(withFang.size() == 3 && withFang.stream().anyMatch(n -> n.id().endsWith("wildhide_blade") && n.have() == 1),
                "a carried Fang stands in for its parts: " + withFang);
        h.succeed();
    }

    private static int count(java.util.@org.jspecify.annotations.Nullable List<AnvilCosts.Part> parts, String id) {
        if (parts == null) return -1;
        for (var q : parts) if (q.id().equals(id)) return q.n();
        return 0;
    }

    /**
     * The Weapons tab from your awakening (SPEC §46.4): a new hunter sees "Set up a War Anvil" with its recipe and "Forge
     * your first weapon", off the main path until D-rank and on it from there; an anvil set down drops the first step and
     * points the second at it; broken, the first comes back; one weapon forged and both are gone.
     */
    private static void goals(GameTestHelper h) {
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "anvil-goals", BlockPos.ZERO);
        AwakenTests.onEnd(h, live::leave);
        ServerPlayer p = live.player();
        Progression.awaken(p);
        p.getInventory().clearContent();
        Goals.Step anvil = step(p, WeaponGoals.ANVIL), first = step(p, WeaponGoals.FIRST);
        h.assertTrue(anvil != null && anvil.goal() == Goals.Goal.WEAPONS && anvil.needs().size() == 3 && !anvil.ready(),
                "a new hunter's Weapons tab says to set up a War Anvil, with what it takes: " + keys(p));
        h.assertTrue(first != null && first.goal() == Goals.Goal.WEAPONS && first.path() == Goals.NO_PATH && !first.ready(),
                "and to forge a first weapon, off the main path before D-rank: " + keys(p));
        while (Progression.rank(p).ordinal() < Rank.D.ordinal()) Progression.passReassessment(p);
        h.assertTrue(step(p, WeaponGoals.ANVIL).path() == Goals.PATH_WAR_ANVIL && step(p, WeaponGoals.FIRST).path() == Goals.PATH_FIRST_WEAPON,
                "from D-rank both are on the main path, the anvil first");
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(at, AnvilContent.ANVIL.get().defaultBlockState());
        AnvilContent.ANVIL.get().setPlacedBy(h.getLevel(), at, h.getLevel().getBlockState(at), p, ItemStack.EMPTY);
        first = step(p, WeaponGoals.FIRST);
        h.assertTrue(step(p, WeaponGoals.ANVIL) == null && first != null && first.where().equals(java.util.Optional.of(at)),
                "an anvil set down: its step is done and the first weapon's points at it: " + keys(p));
        h.getLevel().removeBlock(at, false);
        h.assertTrue(step(p, WeaponGoals.ANVIL) != null, "the anvil broken: setting one up is a step again");
        Progression.count(p, WeaponGoals.FORGED);
        h.assertTrue(step(p, WeaponGoals.ANVIL) == null && step(p, WeaponGoals.FIRST) == null, "a weapon forged: both are done: " + keys(p));

        // an E weapon forged, then D reached: "Make your D-rank weapon", and the Now line once its parts are in the pack
        SmithMemory.of(p).forged("longsword", 0, AnvilSong.Quality.PLAIN);
        for (int i = 0; i < 40 && Progression.profile(p).level() < dev.awaken.arms.ArmsArmoury.LEVEL[1]; i++) Progression.grantLevels(p, 6, Progression.XpSource.COMMAND);
        Goals.Step rank = step(p, WeaponGoals.RANK);
        h.assertTrue(rank != null && rank.title().equals("Make your D-rank weapon") && rank.covers().contains("arms:forge/"),
                "a tier above the best forged: a step to make one, over the Now line: " + keys(p));
        dev.awaken.core.api.ArmsApi.learn(p, "longsword", "test");
        h.assertTrue(AnvilNow.next(p) == null, "its parts not in the pack: no Now line");
        give(p, AnvilCosts.needs(p, "beastbound_longsword"));
        h.assertTrue("beastbound_longsword".equals(AnvilNow.next(p)), "every part in the pack: the Now line says it can be forged, got " + AnvilNow.next(p));
        p.getInventory().add(new ItemStack(WarAnvil.item("beastbound_longsword")));
        h.assertTrue(AnvilNow.next(p) == null, "one of its tier already carried: nothing to say");
        h.succeed();
    }

    private static Goals.@org.jspecify.annotations.Nullable Step step(ServerPlayer p, String key) {
        for (Goals.Step s : Goals.all(p)) if (s.key().equals(key)) return s;
        return null;
    }

    private static java.util.List<String> keys(ServerPlayer p) {
        return Goals.all(p).stream().map(Goals.Step::key).toList();
    }

    /**
     * A D smith forges the Beastbound Longsword, judged by the server's clock: the window wants a Forge, then takes the
     * blade, a core and iron; the heat pulled in the gold; one strike late and one far ahead (not taken), the rest on the
     * beat; plunged and lifted at the notch; three taps. It stands in the slot Flawless, held to the Forge's Temper limit
     * (one level with no Forge room), and is drawn into the hand. Soft and brittle quenches and early and late heats score as
     * they should. Breaking the anvil mid-work gives back what was taken in, and what stands in the slot.
     */
    private static void song(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        h.setBlock(new BlockPos(1, 1, 1), AnvilContent.ANVIL.get().defaultBlockState());
        WarAnvilBlockEntity be = (WarAnvilBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(1, 1, 1)));
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, pos, "anvil-test", 13);
        Item core = first(Awaken.id("hunter_core_d"));
        p.getInventory().add(new ItemStack(core));
        p.getInventory().add(new ItemStack(WarAnvil.item(WarAnvil.BLADE)));
        p.getInventory().add(new ItemStack(Items.IRON_INGOT));
        h.assertTrue(WarAnvil.missing(p, level, pos, WarAnvil.WEAPON, false) != null, "no Forge near: it says so");
        h.setBlock(new BlockPos(3, 1, 1), BuiltInRegistries.BLOCK.getValue(Awaken.id("forge")).defaultBlockState());
        h.assertTrue(WarAnvil.missing(p, level, pos, WarAnvil.WEAPON, false) == null, "a Forge near and the parts in the pack: nothing missing");
        WarAnvil.start(p, level, be);
        AnvilWork w = be.work();
        h.assertTrue(w != null && w.stage == AnvilSong.HEAT && w.materials.size() == 3 && count(p, WarAnvil.item(WarAnvil.BLADE)) == 0
                && count(p, Items.IRON_INGOT) == 0 && count(p, core) == 0, "the blade, the core and the iron go into the work");

        // the heat's curve, then a pull in the gold
        h.assertTrue(AnvilSong.heatScore(0.65F) == 1F && AnvilSong.earlyFactor(0.4F) < 1F && !AnvilSong.burnt(0.8F) && AnvilSong.burnt(0.9F),
                "gold scores whole, early weakens the strikes, too long burns the tip");
        float gold = 0.65F * AnvilSong.HEAT_FULL;
        w.stageStart = level.getGameTime() - (long) gold;
        act(p, pos, AnvilPayloads.PULL, 0, gold, 0F);
        h.assertTrue(w.stage == AnvilSong.SHAPE && Math.abs(w.heat - 0.65F) < 0.01F, "pulled in the gold, onto the anvil");

        // a strike far ahead of its beat isn't taken; one late scores less; the rest on the beat and on the mark
        float due = w.nextDue();
        w.stageStart = level.getGameTime() - (long) (due - 20);
        act(p, pos, AnvilPayloads.STRIKE, 0, due - 20, 0F);
        h.assertTrue(w.scores.isEmpty(), "far ahead of the beat: not taken");
        w.stageStart = level.getGameTime() - (long) (due + 5);
        act(p, pos, AnvilPayloads.STRIKE, 0, due + 5, 0F);
        h.assertTrue(w.scores.size() == 1 && !AnvilSong.clean(w.scores.getFirst()), "five ticks late: taken, not clean, got " + w.scores);

        // a helper's strikes between the beats go into the blade (three make it twin-struck); one on a beat doesn't
        ServerPlayer helper = h.makeMockServerPlayerInLevel();
        AnvilSong.Rhythm r = w.rhythm();
        float between = (r.due(0) + r.due(1)) / 2F;
        for (int i = 0; i < 3; i++) {
            w.stageStart = level.getGameTime() - (long) between;
            w.helpLast -= 100;
            WarAnvil.open(helper, be);
        }
        w.stageStart = level.getGameTime() - (long) r.due(1);
        w.helpLast -= 100;
        WarAnvil.open(helper, be);
        h.assertTrue(w.help == 3 && w.scores.size() == 1, "three off-beats taken, the one on the beat not, got " + w.help);
        while (w.stage == AnvilSong.SHAPE) beatOnTime(h, p, be, AnvilPayloads.STRIKE);
        h.assertTrue(w.scores.size() == w.song.strikes() && w.song.strikes() == 10 && w.stage == AnvilSong.QUENCH, "ten strikes, then the quench");

        // the quench: soft early, brittle late, true at the notch
        h.assertTrue(AnvilSong.quench(20F) < 0.5F && !AnvilSong.brittle(20F) && AnvilSong.quench(32F) == 1F && AnvilSong.brittle(40F),
                "lifted early is soft, at the notch true, late brittle");
        w.stageStart = level.getGameTime() - 10;
        act(p, pos, AnvilPayloads.PLUNGE, 0, 10F, 0F);
        w.stageStart = level.getGameTime() - 42;
        act(p, pos, AnvilPayloads.LIFT, 0, 42F, 0F);
        h.assertTrue(w.stage == AnvilSong.HILT && w.quench == 1F, "held to the notch: quenched true, got " + w.quench);

        while (be.work() == w) beatOnTime(h, p, be, AnvilPayloads.TAP);
        ItemStack made = be.slot();
        WeaponForged f = made.get(AnvilContent.FORGED.get());
        h.assertTrue(made.is(WarAnvil.item(WarAnvil.WEAPON)) && f != null && f.quality() == AnvilSong.Quality.FLAWLESS.ordinal()
                && f.flaw() == AnvilSong.NO_FLAW && f.maker().equals(p.getGameProfile().name()) && f.twin(), "it stands in the slot, Flawless and twin-struck, its maker named, got " + f);
        h.assertTrue(SmithMemory.of(p).remembers("longsword", 1), "a Flawless one goes into memory");
        h.assertTrue(Upgrades.temperOf(made) == 1, "Flawless leaves at Temper III, held to the Forge's limit of I with no Forge room, got " + Upgrades.temperOf(made));

        act(p, pos, AnvilPayloads.DRAW, 0, 0F, 0F);
        h.assertTrue(be.slot().isEmpty() && p.getMainHandItem().is(WarAnvil.item(WarAnvil.WEAPON)), "drawn out of the stone into the hand");

        // breaking it mid-work gives back what went in, and what stands in the slot
        p.getInventory().add(new ItemStack(core));
        p.getInventory().add(new ItemStack(WarAnvil.item(WarAnvil.BLADE)));
        p.getInventory().add(new ItemStack(Items.IRON_INGOT));
        WarAnvil.start(p, level, be);
        h.assertTrue(be.work() != null && WarAnvil.at(p.getUUID(), pos), "a second one begun, its smith at it");
        be.setSlot(new ItemStack(Items.IRON_SWORD));
        level.removeBlock(pos, false);
        int blades = 0, iron = 0, swords = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) {
            if (e.getItem().is(WarAnvil.item(WarAnvil.BLADE))) blades += e.getItem().getCount();
            if (e.getItem().is(Items.IRON_INGOT)) iron += e.getItem().getCount();
            if (e.getItem().is(Items.IRON_SWORD)) swords++;
        }
        h.assertTrue(blades == 1 && iron == 1 && swords == 1, "broken: the blade and iron back, and the sword in the slot, got " + blades + ", " + iron + ", " + swords);
        h.succeed();
    }

    /** A survival smith of this level in front of the anvil (a mock player is always creative, which forges for nothing). */
    private static ServerPlayer smith(GameTestHelper h, BlockPos at, String name, int level) {
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, name, BlockPos.ZERO);
        AwakenTests.onEnd(h, live::leave);
        ServerPlayer p = live.player();
        Progression.awaken(p);
        if (level > 3) while (Progression.rank(p).ordinal() < Rank.D.ordinal()) Progression.passReassessment(p);
        for (int i = 0; i < 40 && Progression.profile(p).level() < level; i++) Progression.grantLevels(p, 6, Progression.XpSource.COMMAND);
        p.getInventory().clearContent();
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() - 1.5, 180F, 30F);
        return p;
    }

    private static void act(ServerPlayer p, BlockPos anvil, int act, int a, float t, float aim) {
        WarAnvil.onAct(p, new AnvilPayloads.Act(anvil, act, a, t, aim));
    }

    /** The next beat struck dead on time and on its mark (the stage's clock set so the server sees it on time). */
    private static void beatOnTime(GameTestHelper h, ServerPlayer p, WarAnvilBlockEntity be, int act) {
        AnvilWork w = be.work();
        float due = w.nextDue();
        w.stageStart = h.getLevel().getGameTime() - (long) due;
        act(p, be.getBlockPos(), act, w.beats().size(), due, 0F);
    }

    private static Item first(net.minecraft.resources.Identifier tag) {
        for (Holder<Item> i : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, tag))) return i.value();
        return CoreApi.ESSENCE_SHARD.get();
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(item)) n += s.getCount();
        return n;
    }
}
