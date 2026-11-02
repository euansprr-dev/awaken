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
        ServerPlayer p = smith(h, pos);
        Item core = first(Awaken.id("hunter_core_d"));
        p.getInventory().add(new ItemStack(core));
        p.getInventory().add(new ItemStack(WarAnvil.item(WarAnvil.BLADE)));
        p.getInventory().add(new ItemStack(Items.IRON_INGOT));
        h.assertTrue(WarAnvil.missing(p, WarAnvil.forge(level, pos), WarAnvil.cost(p)) != null, "no Forge near: it says so");
        h.setBlock(new BlockPos(3, 1, 1), BuiltInRegistries.BLOCK.getValue(Awaken.id("forge")).defaultBlockState());
        h.assertTrue(WarAnvil.missing(p, WarAnvil.forge(level, pos), WarAnvil.cost(p)) == null, "a Forge near and the parts in the pack: nothing missing");
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
        while (w.stage == AnvilSong.SHAPE) beatOnTime(h, p, be, AnvilPayloads.STRIKE);
        h.assertTrue(w.scores.size() == AnvilSong.STRIKES && w.stage == AnvilSong.QUENCH, "ten strikes, then the quench");

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
                && f.flaw() == AnvilSong.NO_FLAW && f.maker().equals(p.getGameProfile().name()), "it stands in the slot, Flawless, its maker named, got " + f);
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

    /** A D-rank survival smith in front of the anvil (a mock player is always creative, which forges for nothing). */
    private static ServerPlayer smith(GameTestHelper h, BlockPos at) {
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "anvil-test", BlockPos.ZERO);
        AwakenTests.onEnd(h, live::leave);
        ServerPlayer p = live.player();
        Progression.awaken(p);
        while (Progression.rank(p).ordinal() < Rank.D.ordinal()) Progression.passReassessment(p);
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
