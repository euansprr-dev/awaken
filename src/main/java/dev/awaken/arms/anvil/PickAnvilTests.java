package dev.awaken.arms.anvil;

import com.mojang.serialization.JsonOps;
import dev.awaken.Awaken;
import dev.awaken.arms.net.ArmsPayloads.NeedRow;
import dev.awaken.core.api.Sources;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.HeavyStrikes;
import dev.awaken.delve.PickVerses;
import dev.awaken.delve.TrueStrikes;
import dev.awaken.delve.WakingPick;
import dev.awaken.delve.api.Pick;
import dev.awaken.delve.forged.DelveForged;
import dev.awaken.delve.forged.ForgedPickItem;
import dev.awaken.delve.forged.ForgedPicks;
import dev.awaken.delve.forged.PickForged;
import dev.awaken.delve.forged.PickForm;
import dev.awaken.delve.forged.PickStats;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The picks at the War Anvil (SPEC §49): one test per mechanism. */
final class PickAnvilTests {
    private PickAnvilTests() {}

    static void register() {
        AwakenTests.register("pick_forge_all", 100, PickAnvilTests::forgeAll);
        AwakenTests.register("pick_raise_keeps", 60, PickAnvilTests::raiseKeeps);
        AwakenTests.register("pick_migrate", 40, PickAnvilTests::migrate);
        AwakenTests.register("pick_mattock_convert", 40, PickAnvilTests::mattockConvert);
        AwakenTests.register("pick_gifts", 20, PickAnvilTests::gifts);
        AwakenTests.register("pick_gift_late", 40, PickAnvilTests::giftLate);
        AwakenTests.register("pick_blunt", 40, PickAnvilTests::blunt);
        AwakenTests.register("pick_forms", 20, PickAnvilTests::forms);
        AwakenTests.register("pick_schema", 20, PickAnvilTests::schema);
        AwakenTests.register("pick_make_again", 40, PickAnvilTests::again);
        AwakenTests.register("pick_head", 40, PickAnvilTests::head);
        AwakenTests.register("pick_cancel_and_break", 40, PickAnvilTests::cancelAndBreak);
        AwakenTests.register("pick_gates", 40, PickAnvilTests::gates);
        AwakenTests.register("pick_kept_on_death", 40, PickAnvilTests::keptOnDeath);
        AwakenTests.register("pick_goals", 40, PickAnvilTests::goals);
        AwakenTests.register("pick_sounds", 20, PickAnvilTests::sounds);
    }

    // ---- the bench -------------------------------------------------------------------------------------------------------

    private static WarAnvilBlockEntity anvil(GameTestHelper h, int build) {
        BlockState st = AnvilContent.ANVIL.get().defaultBlockState().setValue(WarAnvilBlock.BUILD, build);
        h.setBlock(new BlockPos(1, 1, 1), st);
        h.setBlock(new BlockPos(3, 1, 1), BuiltInRegistries.BLOCK.getValue(Awaken.id("forge")).defaultBlockState());
        return (WarAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 1, 1)));
    }

    /** A survival smith who has every rank open and has done every deed. */
    private static ServerPlayer master(GameTestHelper h, BlockPos at, String name) {
        ServerPlayer p = smith(h, at, name, 100);
        for (String d : new String[]{"awaken:delve/deed_heart", "awaken:delve/deed_lamp", "awaken:delve/deed_deepglass", "awaken:delve/deed_seep"}) Progression.setCounter(p, d, 1);
        return p;
    }

    private static ServerPlayer smith(GameTestHelper h, BlockPos at, String name, int level) {
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, name, BlockPos.ZERO);
        AwakenTests.onEnd(h, live::leave);
        ServerPlayer p = live.player();
        Progression.awaken(p);
        Rank need = level >= 97 ? Rank.S : level >= 67 ? Rank.A : level >= 42 ? Rank.B : level >= 22 ? Rank.C : level > 3 ? Rank.D : Rank.E;
        while (Progression.rank(p).ordinal() < need.ordinal()) Progression.passReassessment(p);
        for (int i = 0; i < 60 && Progression.profile(p).level() < level; i++) Progression.grantLevels(p, 6, Progression.XpSource.COMMAND);
        p.getInventory().clearContent();
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() - 1.5, 180F, 30F);
        return p;
    }

    private static Item first(String tag) {
        for (Holder<Item> i : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.parse(tag)))) return i.value();
        throw new IllegalStateException("empty tag " + tag);
    }

    private static void give(ServerPlayer p, List<NeedRow> needs) {
        for (NeedRow n : needs) {
            Item item = n.kind().equals("tag") ? first(n.id()) : BuiltInRegistries.ITEM.getValue(Identifier.parse(n.id()));
            int left = n.need() - n.have();
            while (left > 0) {
                int c = Math.min(left, new ItemStack(item).getMaxStackSize());
                p.getInventory().add(new ItemStack(item, c));
                left -= c;
            }
        }
    }

    private static void act(ServerPlayer p, BlockPos anvil, int act, int a, float t, float aim) {
        WarAnvil.onAct(p, new AnvilPayloads.Act(anvil, act, a, t, aim));
    }

    private static void beat(GameTestHelper h, ServerPlayer p, WarAnvilBlockEntity be, int act) {
        AnvilWork w = be.work();
        float due = w.nextDue();
        w.stageStart = h.getLevel().getGameTime() - (long) due;
        act(p, be.getBlockPos(), act, w.beats().size(), due, 0F);
    }

    /** Every act done cleanly until the work is finished; returns the stages it went through. */
    private static List<Integer> walk(GameTestHelper h, ServerPlayer p, WarAnvilBlockEntity be, float colourHold) {
        List<Integer> stages = new ArrayList<>();
        long now = h.getLevel().getGameTime();
        BlockPos pos = be.getBlockPos();
        for (int guard = 0; guard < 400 && be.work() != null; guard++) {
            AnvilWork w = be.work();
            if (stages.isEmpty() || stages.getLast() != w.stage) stages.add(w.stage);
            switch (w.stage) {
                case AnvilSong.HEAT -> {
                    float gold = 0.65F * AnvilSong.HEAT_FULL;
                    w.stageStart = now - (long) gold;
                    act(p, pos, AnvilPayloads.PULL, 0, gold, 0F);
                }
                case AnvilSong.SHAPE, AnvilSong.EYE, AnvilSong.TIPS -> beat(h, p, be, AnvilPayloads.STRIKE);
                case AnvilSong.HANG, AnvilSong.TEND, AnvilSong.HILT -> beat(h, p, be, AnvilPayloads.TAP);
                case AnvilSong.KNAP -> {
                    w.knapLast = -1F;
                    w.stageStart = now - 60;
                    act(p, pos, AnvilPayloads.KNAP, w.scores.size(), 60F, 0F);
                }
                case AnvilSong.BIND -> {
                    int k = w.taps.size();
                    w.stageStart = now - 10L * k;
                    act(p, pos, AnvilPayloads.WIND, k, 10F * k, 0F);
                }
                case AnvilSong.SPINE -> {
                    w.grindLast = -1F;
                    w.stageStart = now - 40;
                    act(p, pos, AnvilPayloads.GRIND, w.taps.size(), 40F, 0F);
                }
                case AnvilSong.COLOURS -> {
                    w.stageStart = now - 10;
                    act(p, pos, AnvilPayloads.PLUNGE, 0, 10F, 0F);
                    w.stageStart = now - 10 - (long) colourHold;
                    act(p, pos, AnvilPayloads.LIFT, 0, 10F + colourHold, 0F);
                }
                case AnvilSong.SEAM -> {
                    w.stageStart = now - 10;
                    act(p, pos, AnvilPayloads.PLUNGE, 0, 10F, 0F);
                    w.stageStart = now - 42;
                    act(p, pos, AnvilPayloads.LIFT, 0, 42F, 0F);
                }
                case AnvilSong.STAR -> {
                    w.stageStart = now - (long) AnvilSong.STAR_AT;
                    act(p, pos, AnvilPayloads.STAR, 0, AnvilSong.STAR_AT, 0F);
                }
                default -> throw new IllegalStateException("a pick reached stage " + w.stage);
            }
        }
        if (be.work() != null) throw new IllegalStateException("stuck at stage " + be.work().stage + " after " + stages);
        return stages;
    }

    private static ItemStack make(GameTestHelper h, ServerPlayer p, WarAnvilBlockEntity be, PickForm f, int tier) {
        String id = ForgedPicks.id(f, tier);
        give(p, AnvilCosts.needs(p, id));
        String why = WarAnvil.missing(p, h.getLevel(), be.getBlockPos(), id, false);
        h.assertTrue(why == null, id + " can be begun: " + why);
        h.assertTrue(WarAnvil.begin(p, h.getLevel(), be, id, false), id + " begins");
        walk(h, p, be, 36F);
        act(p, be.getBlockPos(), AnvilPayloads.DRAW, 0, 0F, 0F);
        return p.getMainHandItem();
    }

    // ---- the tests -------------------------------------------------------------------------------------------------------

    /** Each of the 24, walked through every stage cleanly: the right item, in the hand, Flawless, peacock, with its rank's stages only. */
    private static void forgeAll(GameTestHelper h) {
        WarAnvilBlockEntity be = anvil(h, 3);
        ServerPlayer p = master(h, be.getBlockPos(), "pick-all");
        int[][] stages = {{AnvilSong.KNAP, AnvilSong.BIND, AnvilSong.HANG},
                {AnvilSong.HEAT, AnvilSong.EYE, AnvilSong.SHAPE, AnvilSong.COLOURS, AnvilSong.HANG},
                {AnvilSong.HEAT, AnvilSong.EYE, AnvilSong.SHAPE, AnvilSong.TIPS, AnvilSong.COLOURS, AnvilSong.HANG},
                {AnvilSong.HEAT, AnvilSong.EYE, AnvilSong.SHAPE, AnvilSong.TIPS, AnvilSong.SPINE, AnvilSong.COLOURS, AnvilSong.HANG},
                {AnvilSong.HEAT, AnvilSong.EYE, AnvilSong.SHAPE, AnvilSong.TIPS, AnvilSong.SPINE, AnvilSong.SEAM, AnvilSong.COLOURS, AnvilSong.HANG},
                {AnvilSong.HEAT, AnvilSong.EYE, AnvilSong.SHAPE, AnvilSong.TIPS, AnvilSong.SPINE, AnvilSong.SEAM, AnvilSong.COLOURS, AnvilSong.STAR, AnvilSong.HANG}};
        for (PickForm f : PickForm.values()) for (int t = 0; t < PickStats.TIERS; t++) {
            p.getInventory().clearContent();
            String id = ForgedPicks.id(f, t);
            give(p, AnvilCosts.needs(p, id));
            h.assertTrue(WarAnvil.begin(p, h.getLevel(), be, id, false), id + " begins");
            List<Integer> walked = walk(h, p, be, 36F);
            h.assertTrue(walked.equals(java.util.Arrays.stream(stages[t]).boxed().toList()), id + " goes through its rank's stages: " + walked);
            h.assertTrue(be.slot().is(ForgedPicks.get(f, t)), id + " stands in the stone");
            act(p, be.getBlockPos(), AnvilPayloads.DRAW, 0, 0F, 0F);
            ItemStack made = p.getMainHandItem();
            PickForged d = ForgedPicks.data(made);
            h.assertTrue(made.is(ForgedPicks.get(f, t)) && d.quality() == 3 && d.from() == t, id + " drawn Flawless, begun at its rank: " + d);
            h.assertTrue(t == 0 ? d.colour() == 0.5F : Math.abs(d.colour() - 0.5F) < 0.01F, id + " peacock: " + d.colour());
            h.assertTrue(made.getMaxDamage() == PickStats.uses(f, t, d), id + " wears as its numbers say: " + made.getMaxDamage());
        }
        // from memory: the Flawless C Delver is remembered, one strike, no acts, a choice of colour, Fine
        p.getInventory().clearContent();
        String id = ForgedPicks.id(PickForm.DELVER, 2);
        give(p, AnvilCosts.needs(p, id));
        h.assertTrue(WarAnvil.begin(p, h.getLevel(), be, id, true), "from memory, it begins");
        List<Integer> walked = walk(h, p, be, 20F);
        h.assertTrue(walked.equals(List.of(AnvilSong.HEAT, AnvilSong.SHAPE, AnvilSong.COLOURS)), "from memory it's the heat, a strike and the colours: " + walked);
        h.assertTrue(ForgedPicks.data(be.slot()).quality() == 1 && ForgedPicks.data(be.slot()).is(PickForged.MEMORY) && ForgedPicks.data(be.slot()).colour() == 0F,
                "Fine, and straw for a short hold: " + ForgedPicks.data(be.slot()));
        h.succeed();
    }

    /** A raise keeps everything; above D it's only the new rank's work. A re-draw is half the parts. Enchantments survive both, and Tool rules are the new item's. */
    private static void raiseKeeps(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 3);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = master(h, pos, "pick-raise");
        ItemStack s = make(h, p, be, PickForm.DELVER, 0).copy();
        s.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 2);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
        PickForged d = ForgedPicks.data(s);
        ForgedPicks.write(s, d.withTally(new PickForged.Tally(500, 40, 3, 0)));
        s.setDamageValue(50);
        p.getInventory().clearContent();
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        give(p, PickAnvil.raiseNeeds(p, ForgedPicks.id(PickForm.DELVER, 1)));
        h.assertTrue(PickAnvil.begin(p, level, be, AnvilWork.RAISE), "the raise begins");
        h.assertTrue(p.getMainHandItem().isEmpty() && be.work().piece.is(ForgedPicks.get(PickForm.DELVER, 0)), "the pick is on the anvil, out of your hand");
        List<Integer> to = walk(h, p, be, 44F);
        h.assertTrue(to.equals(List.of(AnvilSong.HEAT, AnvilSong.EYE, AnvilSong.SHAPE, AnvilSong.COLOURS, AnvilSong.HANG)), "E to D is the whole work: " + to);
        act(p, pos, AnvilPayloads.DRAW, 0, 0F, 0F);
        ItemStack up = p.getMainHandItem();
        PickForged e = ForgedPicks.data(up);
        h.assertTrue(up.is(ForgedPicks.get(PickForm.DELVER, 1)) && e.from() == 0, "D, begun at E: " + e);
        h.assertTrue(up.getEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING)) == 2, "enchantment kept");
        h.assertTrue("Old Faithful".equals(up.getHoverName().getString()), "name kept: " + up.getHoverName().getString());
        h.assertTrue(e.tally().blocks() == 500 && e.tally().hearts() == 3 && up.getDamageValue() == 0, "tally kept, wear gone: " + e.tally());
        h.assertTrue(!up.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()) && up.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()),
                "it digs as iron: the item's own rules, not the old stack's");
        h.assertTrue(Math.abs(e.colour() - 0.75F) < 0.01F, "blue for a long hold: " + e.colour());

        give(p, PickAnvil.raiseNeeds(p, ForgedPicks.id(PickForm.DELVER, 2)));
        h.assertTrue(PickAnvil.begin(p, level, be, AnvilWork.RAISE), "D to C");
        to = walk(h, p, be, 36F);
        h.assertTrue(to.equals(List.of(AnvilSong.HEAT, AnvilSong.SHAPE, AnvilSong.TIPS, AnvilSong.COLOURS, AnvilSong.HANG)), "above D only the new work: " + to);
        act(p, pos, AnvilPayloads.DRAW, 0, 0F, 0F);

        List<NeedRow> half = PickAnvil.halved(p, ForgedPicks.id(PickForm.MATTOCK, 2));
        for (NeedRow n : half) h.assertTrue(n.need() >= 1, "no empty row");
        int whole = 0, cut = 0;
        for (NeedRow n : AnvilCosts.needs(p, ForgedPicks.id(PickForm.MATTOCK, 2))) whole += n.need();
        for (NeedRow n : half) cut += n.need();
        h.assertTrue(cut < whole, "a re-draw costs less: " + cut + " of " + whole);
        give(p, half);
        int code = AnvilWork.REDRAW + 100 * (PickForm.MATTOCK.ordinal() + 1);
        h.assertTrue(PickAnvil.begin(p, level, be, code), "re-draws");
        walk(h, p, be, 36F);
        act(p, pos, AnvilPayloads.DRAW, 0, 0F, 0F);
        ItemStack mattock = p.getMainHandItem();
        h.assertTrue(mattock.is(ForgedPicks.get(PickForm.MATTOCK, 2)) && ForgedPicks.data(mattock).tally().blocks() == 500, "a Mattock now, the same pick");
        h.assertTrue(mattock.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) > 1F && mattock.getDestroySpeed(Blocks.DIRT.defaultBlockState()) > 1F, "and it cuts earth and wood");
        h.succeed();
    }

    /** Every old verse comes across as SPEC §49.7 says: rank, gifts, room, enchantments, the same share of wear, free and with no Forge. */
    private static void migrate(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 0);
        h.setBlock(new BlockPos(3, 1, 1), Blocks.AIR.defaultBlockState());
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, pos, "pick-migrate", 3);
        int[] rank = {0, 1, 1, 2, 2, 3, 4};
        var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        var unbreaking = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        for (int v = 0; v <= 6; v++) {
            ItemStack old = new ItemStack(DelveContent.WAKING_PICK.get());
            PickVerses.set(old, v, blocks);
            old.enchant(unbreaking, 1);
            old.setDamageValue(old.getMaxDamage() / 2);
            p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.MAIN_HAND, old);
            var options = PickAnvil.options(p, level, pos, old);
            h.assertTrue(options.size() == 1 && options.getFirst().kind() == AnvilWork.MIGRATE && options.getFirst().ready() && options.getFirst().costs().isEmpty(), "verse " + v + ": one free row, ready: " + options);
            h.assertTrue(PickAnvil.begin(p, level, be, AnvilWork.MIGRATE), "verse " + v + " begins, with no Forge anywhere");
            h.assertTrue(be.work().stage == AnvilSong.TEND && be.work().beatsNeeded() == AnvilSong.MIGRATE_BLOWS, "a few quiet taps");
            walk(h, p, be, 0F);
            ItemStack made = p.getMainHandItem();
            PickForged d = ForgedPicks.data(made);
            h.assertTrue(made.is(ForgedPicks.get(PickForm.DELVER, rank[v])), "verse " + v + " is rank " + rank[v] + ": " + made);
            h.assertTrue(be.slot().isEmpty(), "it comes straight back: nothing to draw");
            for (Pick.Gift g : Pick.Gift.values())
                h.assertTrue(Pick.has(made, g) == (g.ordinal() <= v) || g == Pick.Gift.WIDE_SEAM && rank[v] >= 2 || g == Pick.Gift.CALL && rank[v] >= 5, "verse " + v + " " + g);
            h.assertTrue(d.room() == 1 + (v >= 2 ? 1 : 0) + (v >= 4 ? 1 : 0) + (v >= 6 ? 1 : 0), "room kept: " + d.room());
            h.assertTrue(d.is(PickForged.OLD) && d.maker().isEmpty(), "remade from an old pick, no maker claimed");
            h.assertTrue(made.getEnchantmentLevel(unbreaking) == 1, "enchantment kept");
            h.assertTrue(Math.abs(made.getDamageValue() / (float) made.getMaxDamage() - 0.5F) < 0.01F, "the same share of wear: " + made.getDamageValue() + "/" + made.getMaxDamage());
        }
        h.succeed();
    }

    private static void mattockConvert(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 0);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, pos, "pick-mattock", 3);
        for (int t = 0; t < ForgedPicks.PREFIX.length; t++) {
            Item old = BuiltInRegistries.ITEM.getValue(Awaken.id(ForgedPicks.PREFIX[t] + "_mattock"));
            h.assertTrue(old != Items.AIR, ForgedPicks.PREFIX[t] + " mattock still exists for old saves");
            ItemStack s = new ItemStack(old);
            p.getInventory().clearContent();
            p.setItemInHand(InteractionHand.MAIN_HAND, s);
            h.assertTrue(PickAnvil.begin(p, level, be, AnvilWork.MIGRATE), "converts free at rank " + t);
            walk(h, p, be, 0F);
            h.assertTrue(p.getMainHandItem().is(ForgedPicks.get(PickForm.MATTOCK, t)), "rank " + t + " mattock becomes the forged Mattock: " + p.getMainHandItem());
        }
        h.succeed();
    }

    /** What a pick can do comes from its rank, its form and the deeds written into it, and old picks answer by verse. */
    private static void gifts(GameTestHelper h) {
        ServerPlayer p = master(h, h.absolutePos(new BlockPos(1, 1, 1)), "pick-gifts");
        ItemStack e = new ItemStack(ForgedPicks.get(PickForm.DELVER, 0)), c = new ItemStack(ForgedPicks.get(PickForm.DELVER, 2)), s = new ItemStack(ForgedPicks.get(PickForm.DELVER, 5));
        h.assertTrue(ForgedPicks.earned(p, 0) == 0, "at E no deed can be held yet");
        h.assertTrue(ForgedPicks.earned(p, 2) == (Pick.Gift.KINDLE.bit() | Pick.Gift.SOUNDING.bit()), "at C, heart and lamp: " + ForgedPicks.earned(p, 2));
        h.assertTrue(ForgedPicks.earned(p, 4) == (Pick.Gift.KINDLE.bit() | Pick.Gift.SOUNDING.bit() | Pick.Gift.LONG_SOUNDING.bit() | Pick.Gift.HUSH.bit()), "at A, every deed");
        h.assertTrue(Pick.has(e, Pick.Gift.HUM) && Pick.humReach(e) == 16, "a Delver hums within 16");
        h.assertFalse(Pick.has(new ItemStack(ForgedPicks.get(PickForm.BREAKER, 3)), Pick.Gift.HUM), "a Breaker doesn't");
        h.assertTrue(Pick.humReach(new ItemStack(ForgedPicks.get(PickForm.SOUNDER, 0))) == 24, "a Sounder within 24");
        h.assertTrue(!Pick.has(e, Pick.Gift.WIDE_SEAM) && Pick.has(c, Pick.Gift.WIDE_SEAM), "the seam widens at C");
        h.assertTrue(!Pick.has(c, Pick.Gift.CALL) && Pick.has(s, Pick.Gift.CALL), "the Call comes with S");
        h.assertFalse(Pick.has(new ItemStack(Items.DIAMOND_PICKAXE), Pick.Gift.HUM), "a vanilla pick has nothing");
        h.assertTrue(TrueStrikes.radius(new ItemStack(ForgedPicks.get(PickForm.SOUNDER, 0))) == TrueStrikes.SOUNDER_RADIUS, "the Sounder's seam is 0.40");
        h.succeed();
    }

    /** A deed done after forging: the window offers it, three taps work it in, free, the pick back in your hand. */
    private static void giftLate(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 3);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, pos, "pick-late", 22);
        ItemStack s = new ItemStack(ForgedPicks.get(PickForm.DELVER, 2));
        ForgedPicks.write(s, ForgedPicks.data(s));
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        h.assertTrue(ForgedPicks.waiting(p, s) == 0, "no deed done");
        h.assertFalse(PickAnvil.begin(p, level, be, AnvilWork.WORK_IN), "so nothing to work in");
        Progression.setCounter(p, "awaken:delve/deed_lamp", 1);
        h.assertTrue(ForgedPicks.waiting(p, s) == Pick.Gift.SOUNDING.bit(), "the lamp is waiting");
        h.assertFalse(Pick.has(s, Pick.Gift.SOUNDING), "and not in the steel yet");
        h.assertTrue(PickAnvil.options(p, level, pos, s).stream().anyMatch(o -> o.kind() == AnvilWork.WORK_IN && o.ready()), "the window offers it");
        h.assertTrue(PickAnvil.begin(p, level, be, AnvilWork.WORK_IN), "begins");
        walk(h, p, be, 0F);
        h.assertTrue(Pick.has(p.getMainHandItem(), Pick.Gift.SOUNDING) && be.slot().isEmpty(), "worked in, and back in the hand");
        h.succeed();
    }

    /** It wears to its last use and stops there, digs slowly, and a Dressing brings it back. Unbreaking can't push it past. */
    private static void blunt(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 3);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = master(h, pos, "pick-blunt");
        ItemStack s = new ItemStack(ForgedPicks.get(PickForm.DELVER, 1));
        ForgedPicks.write(s, ForgedPicks.data(s));
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        s.setDamageValue(s.getMaxDamage() - 2);
        h.assertFalse(ForgedPicks.blunt(s), "one use left: not yet");
        float sharp = ForgedPicks.speedFactor(s);
        s.hurtAndBreak(5, p, EquipmentSlot.MAINHAND);
        h.assertTrue(p.getMainHandItem() == s && s.getDamageValue() == s.getMaxDamage() - 1, "it stops at its last use and doesn't break: " + s.getDamageValue());
        h.assertTrue(ForgedPicks.blunt(s) && Math.abs(ForgedPicks.speedFactor(s) / sharp - PickStats.BLUNT) < 1e-4F, "blunt digs at 30%");
        h.assertFalse(dev.awaken.delve.HeavyStrikes.canStrike(s), "and takes no heavy strike, whose wear it could no longer pay");
        s.hurtAndBreak(5, p, EquipmentSlot.MAINHAND);
        h.assertTrue(p.getMainHandItem() == s, "and goes on not breaking");
        int blunted = s.getDamageValue();
        give(p, List.of(new NeedRow("tag", "awaken:tier_metal_d", 1, 0)));
        h.assertTrue(PickAnvil.begin(p, level, be, AnvilWork.DRESS), "dresses");
        walk(h, p, be, 0F);
        ItemStack done = p.getMainHandItem();
        h.assertTrue(done.getDamageValue() == blunted - done.getMaxDamage() / 4, "a quarter of its wear off: " + done.getDamageValue());
        h.assertTrue(ForgedPicks.data(done).tally().keen() == PickStats.KEEN_BLOCKS && !ForgedPicks.blunt(done), "and keen");
        h.assertTrue(dev.awaken.delve.HeavyStrikes.canStrike(done), "dressed, it strikes again");
        h.assertTrue(ForgedPicks.speedFactor(done) > sharp, "keen cuts faster");
        h.assertFalse(done.supportsEnchantment(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY)), "no Efficiency");
        h.succeed();
    }

    /** The forms' ways: the tags, a Breaker's quick glint, a Mattock's two ends, and every item has a source. */
    private static void forms(GameTestHelper h) {
        for (Item i : ForgedPicks.all()) {
            ItemStack s = new ItemStack(i);
            h.assertTrue(s.is(net.minecraft.tags.ItemTags.PICKAXES) && s.is(TagKey.create(Registries.ITEM, Awaken.id("forged_picks"))), i + " is a pickaxe");
            h.assertTrue(s.has(DataComponents.TOOL) && s.has(DataComponents.MAX_DAMAGE), i + " is whole with no story: /give and creative are safe");
            h.assertTrue(s.isCorrectToolForDrops(Blocks.STONE.defaultBlockState()), i + " takes stone");
            h.assertTrue(ForgedPicks.data(s).colour() == 0.5F, i + " reads peacock with no story");
        }
        // held as vanilla's pickaxe (SPEC §49.8; Euan 2026-11-12, "held the wrong way ... match vanilla animations and
        // angle"): in either first-person hand the haft lies along vanilla item/handheld's, and the end ahead is the one
        // the item JSON says leads there (plus_leads: the right hand's +x end, the left hand's -x), as the engine applies a
        // hold (ItemTransform.apply: rotationXYZ, a left hand's y and z turned back)
        org.joml.Vector3f vanilla = hand(0F, -90F, 25F, 1F, new org.joml.Vector3f(1F, 1F, 0F));
        for (Item i : ForgedPicks.all()) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath();
            var display = model("/assets/awaken/models/item/" + id + "_in_hand.json").getAsJsonObject("display");
            for (String side : new String[]{"firstperson_righthand", "firstperson_lefthand"}) {
                var r = display.getAsJsonObject(side).getAsJsonArray("rotation");
                float k = side.endsWith("lefthand") ? -1F : 1F;
                org.joml.Vector3f haft = hand(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(), k, new org.joml.Vector3f(1F, 1F, 0F));
                org.joml.Vector3f plusEnd = hand(r.get(0).getAsFloat(), r.get(1).getAsFloat(), r.get(2).getAsFloat(), k, new org.joml.Vector3f(1F, -1F, 0F));
                h.assertTrue(haft.dot(vanilla) > 0.995F, id + "'s haft stands as vanilla's pickaxe in " + side + " (" + haft + ")");
                h.assertTrue(plusEnd.z * k < -0.9F, id + "'s leading end is ahead in " + side + " (+x end " + plusEnd + ")");
            }
        }
        ForgedPickItem s = ForgedPicks.item(new ItemStack(ForgedPicks.get(PickForm.DELVER, 5)));
        h.assertTrue(new ItemStack(s).has(DataComponents.DAMAGE_RESISTANT), "S shrugs off fire");
        h.assertFalse(new ItemStack(ForgedPicks.get(PickForm.DELVER, 3)).has(DataComponents.DAMAGE_RESISTANT), "B doesn't");
        h.assertTrue(HeavyStrikes.glintOf(new ItemStack(ForgedPicks.get(PickForm.BREAKER, 1))) == HeavyStrikes.BREAKER_GLINT, "a Breaker's glint comes early");
        h.assertTrue(HeavyStrikes.glintOf(new ItemStack(ForgedPicks.get(PickForm.DELVER, 1))) == HeavyStrikes.GLINT, "any other's doesn't");
        h.assertTrue(ForgedPicks.softSide(Blocks.DIRT.defaultBlockState()) && ForgedPicks.softSide(Blocks.OAK_LOG.defaultBlockState()), "the blade meets earth and wood");
        h.assertFalse(ForgedPicks.softSide(Blocks.STONE.defaultBlockState()), "the point meets stone");
        ItemStack mattock = new ItemStack(ForgedPicks.get(PickForm.MATTOCK, 2));
        ItemStack delver = new ItemStack(ForgedPicks.get(PickForm.DELVER, 2));
        float stone = delver.getDestroySpeed(Blocks.STONE.defaultBlockState());
        h.assertTrue(Math.abs(mattock.getDestroySpeed(Blocks.STONE.defaultBlockState()) - stone * 0.9F) < 0.01F, "the Mattock is 90% in rock");
        h.assertTrue(Math.abs(mattock.getDestroySpeed(Blocks.DIRT.defaultBlockState()) - stone * 1.2F) < 0.01F, "and 120% in earth");
        h.assertTrue(delver.getDestroySpeed(Blocks.DIRT.defaultBlockState()) == 1F, "a Delver digs earth as a hand does");
        h.succeed();
    }

    /** A direction in item space as a first-person hold turns it (degrees; k -1 for a left hand), unit length. */
    private static org.joml.Vector3f hand(float rx, float ry, float rz, float k, org.joml.Vector3f v) {
        return new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(rx), k * (float) Math.toRadians(ry), k * (float) Math.toRadians(rz))
                .transform(v.normalize());
    }

    private static com.google.gson.JsonObject model(String path) {
        try (var in = PickAnvilTests.class.getResourceAsStream(path)) {
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void schema(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        PickForged d = new PickForged(PickForged.SCHEMA, 2, 1, 0.3F, "id", "Smith", 12L, new PickForged.Tally(4, 3, 2, 1), 5, 1, PickForged.TWIN, 2);
        var json = PickForged.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow();
        h.assertTrue(d.equals(PickForged.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()), "a story survives a save");
        var empty = PickForged.CODEC.parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
        h.assertTrue(empty.colour() == 0.5F && empty.schemaVersion() == PickForged.SCHEMA, "an empty one reads plain");
        // a work saved before picks existed still reads, its pick's acts undone
        AnvilWork w = AnvilWork.fresh("beastbound_longsword", 3L, List.of(), java.util.UUID.randomUUID(), BlockPos.ZERO, AnvilWork.FORGE);
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var saved = AnvilWork.CODEC.encodeStart(ops, w).getOrThrow().getAsJsonObject();
        for (String field : new String[]{"eye", "colour", "hang", "tips", "spine", "seam"}) saved.remove(field);
        AnvilWork back = AnvilWork.CODEC.parse(ops, saved).getOrThrow();
        h.assertTrue(back.eye == -1F && back.colour == -1F && !back.pick, "a v5 work reads with nothing done");
        AnvilWork p = AnvilWork.CODEC.parse(ops, AnvilWork.CODEC.encodeStart(ops, AnvilWork.picked("beastbound_pick_sounder", AnvilSong.COLOURS, 3L, List.of(), ItemStack.EMPTY, java.util.UUID.randomUUID(), BlockPos.ZERO, AnvilWork.RAISE, 1)).getOrThrow()).getOrThrow();
        h.assertTrue(p.pick && p.stage == AnvilSong.COLOURS && p.kind == AnvilWork.RAISE && p.tier == 1 && p.style.equals("pick_sounder"), "a pick's work comes back as it was");
        h.succeed();
    }

    /** Make it again: only your best rank, only when you carry none, at half the parts. */
    private static void again(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 3);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = master(h, pos, "pick-again");
        String c = ForgedPicks.id(PickForm.BREAKER, 2);
        ItemStack first = make(h, p, be, PickForm.BREAKER, 2);
        h.assertTrue(ForgedPicks.is(first) && Progression.profile(p).counter(ForgedPicks.BEST_TIER) == 3, "your best is C");
        give(p, PickAnvil.halved(p, c));
        h.assertFalse(PickAnvil.again(p, level, pos, c), "not while you carry one");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        h.assertTrue(PickAnvil.again(p, level, pos, c), "lost, it's on offer");
        h.assertFalse(PickAnvil.again(p, level, pos, ForgedPicks.id(PickForm.BREAKER, 1)), "only at your best rank");
        h.assertTrue(PickAnvil.forgeAgain(p, pos, c), "begins");
        walk(h, p, be, 36F);
        h.assertTrue(be.slot().is(ForgedPicks.get(PickForm.BREAKER, 2)), "made again");
        h.succeed();
    }

    /** The stone gives the head once; it hangs for a log and string, and with it lost the E Delver has parts of its own. */
    private static void head(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 0);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, pos, "pick-head", 3);
        h.setBlock(new BlockPos(5, 1, 1), DelveContent.WAKING_PICK_STONE.get().defaultBlockState());
        BlockPos stone = h.absolutePos(new BlockPos(5, 1, 1));
        String id = ForgedPicks.id(PickForm.DELVER, 0);
        List<NeedRow> plain = AnvilCosts.needs(p, id);
        h.assertTrue(plain.size() > 3, "with no head, the rank's own parts: " + plain);
        h.assertTrue(WakingPick.pull(p, stone), "the first pull gives it");
        h.assertFalse(WakingPick.pull(p, stone), "only once");
        WakingPick.handOver(p);
        h.assertTrue(p.getInventory().countItem(DelveForged.OLD_HEAD.get()) == 1, "nor does the Hearth add a second");
        List<NeedRow> needs = AnvilCosts.needs(p, id);
        h.assertTrue(needs.size() == 3 && needs.getFirst().id().endsWith("delver_old_head"), "the head, a log and string: " + needs);
        give(p, needs);
        h.assertTrue(WarAnvil.begin(p, level, be, id, false), "begins");
        h.assertTrue(p.getInventory().countItem(DelveForged.OLD_HEAD.get()) == 0 && be.work().materials.stream().anyMatch(m -> m.is(DelveForged.OLD_HEAD.get())), "the head goes in");
        walk(h, p, be, 0F);
        act(p, pos, AnvilPayloads.DRAW, 0, 0F, 0F);
        h.assertTrue(p.getMainHandItem().is(ForgedPicks.get(PickForm.DELVER, 0)), "and it's a pick");
        h.assertTrue(Sources.declared().containsKey("delver_old_head"), "the ledger knows where the head comes from");
        h.succeed();
    }

    /** A pick is yours for a career: it isn't dropped, it's held on the player (a restart can't lose it), and it's back on getting up. */
    private static void keptOnDeath(GameTestHelper h) {
        ServerPlayer p = smith(h, BlockPos.ZERO, "pick_dead", 30);
        ItemStack pick = new ItemStack(ForgedPicks.get(PickForm.MATTOCK, 1));
        pick.setDamageValue(7);
        p.getInventory().add(pick);
        p.getInventory().add(new ItemStack(DelveForged.OLD_HEAD.get()));
        p.getInventory().add(new ItemStack(Items.STONE, 3));
        List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
        for (ItemStack s : p.getInventory()) if (!s.isEmpty()) drops.add(new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ(), s.copy()));
        var event = new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(p, h.getLevel().damageSources().generic(), drops, false);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.getDrops().size() == 1 && event.getDrops().iterator().next().getItem().is(Items.STONE), "only the stone falls");
        var saved = p.getData(DelveForged.KEPT);
        h.assertTrue(saved.items().size() == 2, "held on the player");
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        out.store("v", DelveForged.KeptPicks.CODEC, saved);
        p.getInventory().clearContent();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p, false));
        h.assertTrue(p.getInventory().countItem(ForgedPicks.get(PickForm.MATTOCK, 1)) == 1 && p.getInventory().countItem(DelveForged.OLD_HEAD.get()) == 1, "back in the pack on getting up");
        for (ItemStack s : p.getInventory()) if (s.is(ForgedPicks.get(PickForm.MATTOCK, 1))) h.assertTrue(s.getDamageValue() == 7, "with its wear");
        h.assertTrue(p.getData(DelveForged.KEPT).items().isEmpty(), "and only once");
        h.succeed();
    }

    /** Tasks follow what you carry: the head, the first pick, an old one to remake, a rank to raise to, a blunt edge, a deed to work in. */
    private static void goals(GameTestHelper h) {
        ServerPlayer p = smith(h, BlockPos.ZERO, "pick_goals", 30);
        java.util.function.Function<String, Boolean> shown = key -> PickGoals.steps(p).stream().anyMatch(s -> s.key().equals(key));
        h.assertTrue(shown.apply(PickGoals.HEAD) && !shown.apply(PickGoals.FIRST), "first the head");
        p.getInventory().add(new ItemStack(DelveForged.OLD_HEAD.get()));
        h.assertTrue(shown.apply(PickGoals.FIRST) && !shown.apply(PickGoals.HEAD), "with it, the first pick");
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(dev.awaken.delve.DelveContent.WAKING_PICK.get()));
        h.assertTrue(shown.apply(PickGoals.REMAKE) && !shown.apply(PickGoals.FIRST), "an old pick is remade instead");
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(ForgedPicks.get(PickForm.DELVER, 0)));
        h.assertTrue(shown.apply(PickGoals.RAISE), "behind your level, raise it");
        h.assertTrue(!shown.apply(PickGoals.DRESS), "not blunt yet");
        for (ItemStack s : p.getInventory()) if (ForgedPicks.is(s)) s.setDamageValue(s.getMaxDamage() - 1);
        h.assertTrue(shown.apply(PickGoals.DRESS), "blunt, dress it");
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(ForgedPicks.get(PickForm.DELVER, dev.awaken.arms.ArmsArmoury.bestTier(p))));
        h.assertTrue(!shown.apply(PickGoals.RAISE), "at your rank there's nothing to raise");
        h.succeed();
    }

    /** Every event the picks play has an entry in the merged sounds.json, and every file it names is in the jar. */
    private static void sounds(GameTestHelper h) {
        try (var in = Awaken.class.getResourceAsStream("/assets/awaken/sounds.json")) {
            h.assertTrue(in != null, "sounds.json");
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> events = new java.util.ArrayList<>(List.of("pick.sounding_tap", "pick.sounding_hollow", "pick.mattock_toss", "pick.mattock_catch", "pick.strike_earth", "pick.strike_wood", "pick.strike_blunt",
                    "anvil.eye", "anvil.tip", "anvil.hang", "anvil.tend", "anvil.thread", "anvil.pour", "anvil.colours"));
            for (PickForm f : PickForm.values()) events.add("pick.strike_" + f.id);
            for (String e : events) {
                h.assertTrue(root.has(e), "no sound entry for " + e);
                for (var v : root.getAsJsonObject(e).getAsJsonArray("sounds")) {
                    // the game's own recordings, by name (SPEC §49.10): no file of ours, and none of Mojang's in the jar
                    String name = v.getAsJsonObject().get("name").getAsString();
                    h.assertTrue(name.startsWith("minecraft:"), e + " should name one of the game's sounds: " + name);
                }
            }
        } catch (java.io.IOException e) {
            h.fail(e.toString());
        }
        h.succeed();
    }

    /** Backed out before the first blow, everything comes back; once struck, it doesn't; a broken anvil drops what it held. */
    private static void cancelAndBreak(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 3);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = master(h, pos, "pick-cancel");
        ItemStack s = new ItemStack(ForgedPicks.get(PickForm.DELVER, 1));
        ForgedPicks.write(s, ForgedPicks.data(s));
        p.setItemInHand(InteractionHand.MAIN_HAND, s);
        List<NeedRow> needs = PickAnvil.raiseNeeds(p, ForgedPicks.id(PickForm.DELVER, 2));
        give(p, needs);
        int total = 0;
        for (ItemStack i : p.getInventory().getNonEquipmentItems()) if (!ForgedPicks.is(i)) total += i.getCount();
        h.assertTrue(PickAnvil.begin(p, level, be, AnvilWork.RAISE), "begins");
        h.assertTrue(be.work() != null && p.getMainHandItem().isEmpty(), "on the anvil");
        act(p, pos, AnvilPayloads.CANCEL, 0, 0F, 0F);
        int back = 0;
        for (ItemStack i : p.getInventory().getNonEquipmentItems()) if (!ForgedPicks.is(i)) back += i.getCount();
        h.assertTrue(be.work() == null && ForgedPicks.is(p.getMainHandItem()) && back == total, "the pick and every part back: " + back + " of " + total);

        give(p, PickAnvil.raiseNeeds(p, ForgedPicks.id(PickForm.DELVER, 2)));
        h.assertTrue(PickAnvil.begin(p, level, be, AnvilWork.RAISE), "begins again");
        float gold = 0.65F * AnvilSong.HEAT_FULL;
        be.work().stageStart = level.getGameTime() - (long) gold;
        act(p, pos, AnvilPayloads.PULL, 0, gold, 0F);
        h.assertTrue(be.work().stage == AnvilSong.SHAPE, "pulled");
        act(p, pos, AnvilPayloads.CANCEL, 0, 0F, 0F);
        h.assertTrue(be.work() != null, "past the heat there's no backing out");
        level.destroyBlock(pos, false);
        var around = new net.minecraft.world.phys.AABB(pos).inflate(3);
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around);
        h.assertTrue(drops.stream().anyMatch(e -> ForgedPicks.is(e.getItem())), "broken, the anvil gives the pick back: " + drops);
        h.assertTrue(drops.size() > 1, "and the parts");
        h.succeed();
    }

    /** The level, the Forge and the anvil's build all gate a pick; a weapon's pattern or a grindstone doesn't; the wording says picks. */
    private static void gates(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        WarAnvilBlockEntity be = anvil(h, 0);
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, pos, "pick-gates", 3);
        String d = ForgedPicks.id(PickForm.DELVER, 1);
        String line = WarAnvil.missing(p, level, pos, d, false);
        h.assertTrue(line != null && line.contains("picks open at level"), "a level gate: " + line);
        for (int i = 0; i < 40 && Progression.profile(p).level() < 22; i++) {
            while (Progression.rank(p).ordinal() < Rank.C.ordinal()) Progression.passReassessment(p);
            Progression.grantLevels(p, 6, Progression.XpSource.COMMAND);
        }
        give(p, AnvilCosts.needs(p, ForgedPicks.id(PickForm.DELVER, 2)));
        line = WarAnvil.missing(p, level, pos, ForgedPicks.id(PickForm.DELVER, 2), false);
        h.assertTrue(line != null && line.startsWith("C-rank picks are forged on an Ironbound anvil."), "the build: " + line);
        h.setBlock(new BlockPos(1, 1, 1), level.getBlockState(pos).setValue(WarAnvilBlock.BUILD, 1));
        WarAnvilBlockEntity built = (WarAnvilBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(WarAnvil.missing(p, level, pos, ForgedPicks.id(PickForm.DELVER, 2), false) == null, "and no grindstone or pattern is asked");
        h.setBlock(new BlockPos(3, 1, 1), Blocks.AIR.defaultBlockState());
        h.assertTrue(AnvilHeld.NO_FORGE.equals(WarAnvil.missing(p, level, pos, ForgedPicks.id(PickForm.DELVER, 2), false)), "metal needs a Forge");
        line = WarAnvil.missing(p, level, pos, ForgedPicks.id(PickForm.BREAKER, 0), false);
        h.assertTrue(line != null && line.contains("lode's heart"), "the Breaker opens with a heart: " + line);
        Progression.setCounter(p, "awaken:delve/deed_heart", 1);
        h.assertTrue(ForgedPicks.open(p, PickForm.BREAKER), "and then it's open");
        h.assertTrue(built != null, "the anvil is still here");
        h.succeed();
    }
}
