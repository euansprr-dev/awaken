package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** GameTests for the weapon progression (docs/arms-progression/PLAN.md): one per mechanism, `arms_` prefix. */
final class ArmsProgressionTests {
    private ArmsProgressionTests() {}

    static void register() {
        AwakenTests.register("arms_issue", 40, ArmsProgressionTests::issue);
        AwakenTests.register("arms_shard_pity", 40, ArmsProgressionTests::shardPity);
        AwakenTests.register("arms_rack", 40, ArmsProgressionTests::rack);
        AwakenTests.register("arms_armoury", 40, ArmsProgressionTests::armoury);
    }

    /** A mock player in survival (the helper's are creative, where nothing is paid). */
    private static ServerPlayer survivor(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Progression.awaken(sp);
        return sp;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Awaken.id(id));
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Hale's issue: waiting for a new hunter, one pick gives the finished weapon, its Blueprint and a Fang, once. */
    static void issue(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Progression.awaken(sp);
        h.assertTrue(ArmsApi.issuePending(sp), "a new awakened hunter with no styled weapon is waiting for the issue");
        ArmsApi.openIssue(sp);
        ArmsIssue.onPick(sp, "katana");
        h.assertTrue(count(sp, item("katana_blueprint")) == 0 && ArmsApi.issuePending(sp), "a style that wasn't offered gives nothing");
        ArmsIssue.onPick(sp, "maul");
        h.assertTrue(count(sp, item("wildhide_maul")) == 1, "the finished Wildhide Maul");
        h.assertTrue(count(sp, item("maul_blueprint")) == 1, "and its Blueprint");
        h.assertTrue(count(sp, item("wildhide_blade")) == 1, "and a spare Fang");
        h.assertTrue(ArmsApi.knows(sp, "maul") && !ArmsApi.knows(sp, "axe"), "the Maul is known, the Axe isn't");
        h.assertTrue(!ArmsApi.issuePending(sp), "the issue is taken");
        ArmsApi.openIssue(sp);
        ArmsIssue.onPick(sp, "daggers");
        h.assertTrue(count(sp, item("wildhide_daggers")) == 0 && count(sp, item("wildhide_maul")) == 1, "once");

        ServerPlayer armed = h.makeMockServerPlayerInLevel();
        Progression.awaken(armed);
        armed.getInventory().add(new ItemStack(item("wildhide_longsword")));
        h.assertTrue(!ArmsApi.issuePending(armed), "a hunter who already carries a styled weapon needs no issue");
        h.succeed();
    }

    /** The no-drought rule: the fifth kill in a row of an awakened creature that left no shard drops one. */
    static void shardPity(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Progression.awaken(sp);
        var mob = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        mob.setData(CoreApi.MOB_ROLE, new MobRole(MobRole.Role.NORMAL, 3, "awaken:greenwild", Optional.empty(), ""));
        var source = h.getLevel().damageSources().playerAttack(sp);
        int dropped = -1;
        for (int kill = 1; kill <= 5; kill++) {
            List<ItemEntity> drops = new ArrayList<>();
            ArmsIssue.onDrops(new LivingDropsEvent(mob, source, drops, true));
            boolean shard = drops.stream().anyMatch(d -> d.getItem().is(CoreApi.ESSENCE_SHARD.get()));
            if (shard) dropped = kill;
            h.assertTrue(shard == (kill == 5), "the shard comes on the fifth kill with none, not before: kill " + kill + " shard " + shard);
        }
        h.assertTrue(dropped == 5 && ArmsProgress.of(sp).shardMisses == 0, "and the count starts again");
        List<ItemEntity> good = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(CoreApi.ESSENCE_SHARD.get()))));
        ArmsProgress.of(sp).shardMisses = 3;
        ArmsIssue.onDrops(new LivingDropsEvent(mob, source, good, true));
        h.assertTrue(ArmsProgress.of(sp).shardMisses == 0 && good.size() == 1, "a shard that dropped by itself resets the count");
        h.succeed();
    }

    /** Hale's rack: what a rank opens, the price in essence, a refusal below the rank or without the essence. */
    static void rack(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = survivor(h);
        sp.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get(), 10));
        var view = ArmsArmoury.view(sp, ArmsArmoury.HALE, false);
        long onOffer = view.styles().stream().filter(e -> e.state() == 1).count();
        h.assertTrue(onOffer == 5 && view.hidden() == 10 && view.hiddenRank().equals("D"), "an E-rank hunter sees the five E patterns and ten more waiting from D: " + onOffer + " / " + view.hidden() + " / " + view.hiddenRank());
        h.assertTrue(view.styles().stream().allMatch(e -> e.state() == 0 || e.price() == 4), "an E pattern is 4 essence");
        h.assertTrue(!ArmsArmoury.buy(sp, "spear") && count(sp, CoreApi.ESSENCE_SHARD.get()) == 10, "a D pattern is refused at E and costs nothing");
        h.assertTrue(ArmsArmoury.buy(sp, "scythe"), "an E pattern is bought");
        h.assertTrue(count(sp, CoreApi.ESSENCE_SHARD.get()) == 6 && count(sp, item("scythe_blueprint")) == 1 && ArmsApi.knows(sp, "scythe"), "4 essence paid, its Blueprint in the pack, the style known");
        h.assertTrue(!ArmsArmoury.buy(sp, "scythe"), "never twice");
        ServerPlayer poor = survivor(h);
        h.assertTrue(!ArmsArmoury.buy(poor, "daggers") && !ArmsApi.knows(poor, "daggers"), "without the essence nothing is bought");
        Progression.passReassessment(sp);
        var d = ArmsArmoury.view(sp, ArmsArmoury.HALE, false);
        h.assertTrue(d.styles().stream().filter(e -> e.state() == 1).count() == 8 && d.hiddenRank().equals("C"), "D rank opens its four: eight on offer, the rest from C");
        h.assertTrue(d.styles().stream().filter(e -> e.id().equals("spear")).findFirst().map(e -> e.price() == 6).orElse(false), "a D pattern is 6 essence");
        h.succeed();
    }

    /** The Armoury's doors, what a weapon needs, drawing a copy at a Forge, and Track. */
    static void armoury(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = survivor(h);
        ArmsIssue.INSTANCE.learn(sp, "daggers", "a test");
        var needs = ArmsArmoury.needs(sp, "daggers", 0);
        h.assertTrue(needs.size() == 4 && needs.stream().allMatch(n -> n.have() == 0 || n.id().endsWith("daggers_blueprint")), "the Wildhide Daggers need a Fang, a Part, a Binding and the Blueprint: " + needs);
        sp.getInventory().add(new ItemStack(item("wildhide_blade")));
        sp.getInventory().add(new ItemStack(Items.BONE));
        sp.getInventory().add(new ItemStack(Items.STRING));
        h.assertTrue(ArmsArmoury.needs(sp, "daggers", 0).stream().allMatch(n -> n.have() >= n.need()), "with the parts in the pack nothing is missing");
        // a door has to be opened to use it
        sp.getInventory().add(new ItemStack(Items.PAPER, 4));
        sp.getInventory().add(new ItemStack(Items.LAPIS_LAZULI));
        sp.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get()));
        int before = count(sp, item("daggers_blueprint"));
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.DRAW, "daggers", ""));
        h.assertTrue(count(sp, item("daggers_blueprint")) == before, "no copy is drawn without the Forge's door");
        ArmsArmoury.open(sp, ArmsArmoury.FORGE);
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.DRAW, "daggers", ""));
        h.assertTrue(count(sp, item("daggers_blueprint")) == before + 1, "at the Forge a known pattern is drawn again");
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.BUY, "axe", ""));
        h.assertTrue(!ArmsApi.knows(sp, "axe"), "and nothing is bought at the Forge");
        // Track: the weapon, its rows, and it lets go when it is made
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.TRACK, "daggers", "E"));
        h.assertTrue(ArmsProgress.of(sp).tracked.equals("daggers:E"), "tracked");
        var task = ArmsTrack.task(sp);
        h.assertTrue(task != null && task.rows().size() == 4 && task.title().contains("Wildhide Daggers"), "the task card lists the four things the weapon needs");
        sp.getInventory().add(new ItemStack(item("wildhide_daggers")));
        h.assertTrue(ArmsTrack.task(sp) == null && ArmsProgress.of(sp).tracked.isEmpty(), "and lets go once the weapon is in the pack");
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.TRACK, "katana", "E"));
        h.assertTrue(ArmsProgress.of(sp).tracked.isEmpty(), "a style you don't know can't be tracked");
        h.succeed();
    }
}
