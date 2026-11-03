package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Brambles (SPEC §47.13): our wild things live among berry bushes, so a bush neither pricks nor holds them. The Briar Sow
 * walking her own bramble ring was pricked twice a second and caught at every step: from afar she shook.
 */
public final class BrambleTests {
    private BrambleTests() {}

    public static void register() {
        AwakenTests.registerIsolated("mobs_brambles", 300, BrambleTests::brambles);
    }

    /** A Briar Sow driven across eight blocks of ripe berry bushes, walled in so there's no way round: unhurt, and across in time. */
    private static void brambles(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var bush = Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3);
        for (int x = -2; x <= 14; x++) for (int z = -4; z <= 4; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.DIRT.defaultBlockState());
            if (Math.abs(z) == 4) for (int y = 1; y <= 2; y++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState());
        }
        for (int x = 2; x <= 9; x++) for (int z = -3; z <= 3; z++) h.setBlock(new BlockPos(x, 1, z), bush);
        MobDef def = Bestiary.get("briar_sow");
        AwakenMob sow = Mobs.spawn(level, def, h.absoluteVec(new Vec3(0.5, 1, 0.5)), 5,
                new MobRole(def.tier.role, 5, def.theme.id, Optional.empty(), ""), Set.of());
        h.assertTrue(sow != null, "a Briar Sow spawned");
        Vec3 to = h.absoluteVec(new Vec3(12.5, 1, 0.5));
        float full = sow.getHealth();
        long start = level.getGameTime();
        h.onEachTick(() -> {
            h.assertTrue(sow.hurtTime == 0 && sow.getHealth() >= full, "the brambles never prick her (" + sow.getHealth() + " of " + full + ")");
            if (h.relativeVec(sow.position()).x < 11) {
                h.assertTrue(level.getGameTime() - start < 140, "nor hold her: she's across eight blocks of them in seven seconds (at x "
                        + String.format(java.util.Locale.ROOT, "%.1f", h.relativeVec(sow.position()).x) + ")");
                sow.getNavigation().stop(); // straight through, never round
                sow.getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.0);
                return;
            }
            sow.discard();
            h.succeed();
        });
    }
}
