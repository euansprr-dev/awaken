package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Sight (SPEC §47.11): a creature notices only someone it sees or hears close by, lets go of one it can't for three
 * seconds and goes to look where they were, and never fires at someone behind a wall.
 */
public final class SightTests {
    private SightTests() {}

    public static void register() {
        AwakenTests.registerIsolated("mobs_sight", 400, SightTests::sight);
    }

    /**
     * An ink-spitter in a stone pen open to the sky, you eight blocks off (out of its ear): it doesn't notice you; set on
     * you as a woken room sets it, it never fires and lets you go, then goes to look where you were; the wall comes
     * down and it notices you.
     */
    private static void sight(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 10; z++) h.setBlock(new BlockPos(x, 0, z), stone);
        for (int x = 0; x <= 2; x++) for (int z = 0; z <= 2; z++) for (int y = 1; y <= 3; y++) {
            if (x != 1 || z != 1) h.setBlock(new BlockPos(x, y, z), stone);
        }
        MobDef def = Bestiary.get("ink_drowned");
        AwakenMob m = Mobs.spawn(level, def, h.absoluteVec(new Vec3(1.5, 1, 1.5)), 5,
                new MobRole(MobRole.Role.NORMAL, 5, def.theme.id, Optional.empty(), ""), Set.of());
        h.assertTrue(m != null, "an ink-spitter spawned");
        var live = AwakenTests.survivalPlayer(h, "Unseen", new BlockPos(1, 1, 9));
        ServerPlayer you = live.player();
        float hp = you.getHealth();
        AABB around = new AABB(h.absoluteVec(new Vec3(-4, -1, -4)), h.absoluteVec(new Vec3(7, 7, 14)));
        int[] step = {0};
        long[] at = {level.getGameTime()};
        h.onEachTick(() -> {
            long now = level.getGameTime();
            if (step[0] < 2) {
                h.assertTrue(level.getEntitiesOfClass(AwBolt.class, around).isEmpty(), "nothing is fired at someone it can't see");
                h.assertTrue(you.getHealth() == hp, "and nothing strikes them");
            }
            switch (step[0]) {
                case 0 -> {
                    h.assertTrue(m.getTarget() == null, "it doesn't notice you through the wall");
                    if (now - at[0] < 80) return;
                    m.setTarget(you); // set on you unseen, as a room its door woke
                    at[0] = now;
                    step[0] = 1;
                }
                case 1 -> {
                    if (m.getTarget() != null) {
                        h.assertTrue(now - at[0] <= AwakenMob.LOSE_TICKS + 5, "it lets you go within three seconds of neither seeing nor hearing you");
                        return;
                    }
                    h.assertTrue(m.heardPos != null && m.heardPos.distanceTo(you.position()) < 1, "and goes to look where it knew you were");
                    for (int x = 0; x <= 2; x++) for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(x, y, 2), Blocks.AIR.defaultBlockState());
                    at[0] = now;
                    step[0] = 2;
                }
                case 2 -> {
                    if (m.getTarget() != you) {
                        h.assertTrue(now - at[0] < 160, "with the wall down it notices you");
                        return;
                    }
                    m.discard();
                    live.leave();
                    h.succeed();
                }
                default -> { }
            }
        });
    }
}
