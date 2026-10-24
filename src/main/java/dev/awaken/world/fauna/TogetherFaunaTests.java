package dev.awaken.world.fauna;

import dev.awaken.core.api.MobHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.WildMount;
import dev.awaken.progression.Curve;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GameTests for the overworld's creatures among friends (SPEC "Together"): their level follows the group, and only its
 * owner may possess a bonded mount.
 */
final class TogetherFaunaTests {
    private TogetherFaunaTests() {}

    static void register() {
        AwakenTests.registerIsolated("together_fauna_follow_the_group", 200, h -> {
            var level = h.getLevel();
            AwakenTests.Live vet = AwakenTests.survivalPlayer(h, "fauna-vet", new BlockPos(1, 1, 1));
            AwakenTests.Live late = AwakenTests.survivalPlayer(h, "fauna-late", new BlockPos(3, 1, 1));
            List<AwakenMob> made = new ArrayList<>();
            try {
                ServerPlayer v = vet.player(), l = late.player();
                Progression.awaken(v);
                Progression.awaken(l);
                for (int i = 0; i < 20; i++) Progression.grantXp(v, Curve.xpToNext(Progression.profile(v).level()) + 1, Progression.XpSource.COMMAND);
                int vl = Progression.profile(v).level(), ll = Progression.profile(l).level();
                h.assertTrue(vl > ll + 10, "a veteran well above the friend: " + vl + " vs " + ll);
                BlockPos spawn = h.absolutePos(new BlockPos(2, 1, 6));
                // an isolated test runs alone: any other player this close is left over from an earlier test
                for (ServerPlayer o : java.util.List.copyOf(level.players())) {
                    if (o != v && o != l && o.distanceToSqr(Vec3.atCenterOf(spawn)) < 160 * 160) level.getServer().getPlayerList().remove(o);
                }
                int group = FaunaRules.contentLevel(level, spawn, FaunaRole.NEUTRAL);
                h.assertTrue(group == Math.max(0, ll - 1), "near both, a creature takes the friend's level (" + group + ")");
                l.teleportTo(level, spawn.getX() + 100.5, spawn.getY(), spawn.getZ() + 0.5, java.util.Set.of(), 0F, 0F, true);
                int alone = FaunaRules.contentLevel(level, spawn, FaunaRole.NEUTRAL);
                h.assertTrue(alone > group, "with only the veteran near, the veteran's (" + alone + ")");

                // a bonded mount answers to its owner only
                AwakenMob m = Fauna3Tests.spawn(level, "frost_stag", Vec3.atBottomCenterOf(spawn), made);
                h.assertTrue(m instanceof WildMount, "a Frost Stag");
                ((WildMount) m).tameTo(v, true);
                h.assertTrue(MobHooks.bondedTo(m).equals(Optional.of(v.getUUID())), "bonded to the veteran");
                h.assertTrue(dev.awaken.veil.Possession.ownedByAnother(m, l), "the friend's spirit can't possess it");
                h.assertFalse(dev.awaken.veil.Possession.ownedByAnother(m, v), "its owner's can");
            } finally {
                for (AwakenMob m : made) m.discard();
                vet.leave();
                late.leave();
            }
            h.succeed();
        });
    }
}
