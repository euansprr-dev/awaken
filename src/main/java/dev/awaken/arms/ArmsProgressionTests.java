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
}
