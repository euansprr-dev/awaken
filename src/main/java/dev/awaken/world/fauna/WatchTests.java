package dev.awaken.world.fauna;

import dev.awaken.core.api.CameraHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.CaveFauna;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** A creature you are watching stays, and draws, and the lurker's leap reaches its roof (SPEC §39.11; Euan 9 Nov). */
final class WatchTests {
    private WatchTests() {}

    private static final int UP = FaunaTests.UP;

    static void register() {
        // at every height of roof the leap can be asked for, a real lurker on its own AI ends up hanging from it
        AwakenTests.registerIsolated("fauna3_lurker_reaches_the_roof", 400, h -> {
            ServerLevel level = h.getLevel();
            List<AwakenMob> made = new ArrayList<>();
            List<CaveFauna> lurkers = new ArrayList<>();
            List<Integer> roofs = new ArrayList<>();
            int[][] spots = {{-12, -8}, {-4, -8}, {4, -8}, {12, -8}, {-12, 8}, {-4, 8}, {4, 8}, {12, 8}};
            for (int i = 0; i < spots.length; i++) {
                int air = 3 + i; // the room's height: a roof 3 to 10 blocks over the floor, every one ceilingAbove accepts
                BlockPos c = h.absolutePos(new BlockPos(spots[i][0], UP, spots[i][1]));
                shaft(level, c, air);
                CaveFauna m = (CaveFauna) Fauna3Tests.spawn(level, "drip_lurker", Vec3.atBottomCenterOf(c.above()), made);
                h.assertTrue(m != null, "the lurker spawned");
                lurkers.add(m);
                roofs.add(c.getY() + air + 1);
            }
            h.succeedWhen(() -> {
                for (int i = 0; i < lurkers.size(); i++) {
                    CaveFauna m = lurkers.get(i);
                    h.assertTrue(m.hanging(), "a roof " + (i + 3) + " blocks up: the lurker never got there");
                    h.assertTrue(Math.abs(m.getY() + m.getBbHeight() - roofs.get(i)) < 0.01, "it hangs against its roof");
                }
                made.forEach(AwakenMob::discard);
            });
        });

        // the far-away roll asks whether anyone is looking: in front of them and in clear sight keeps it; otherwise it goes
        AwakenTests.registerIsolated("mob_watched_stays", 100, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = FaunaTests.realPlayer(h, new BlockPos(0, UP, -40));
            face(p, 0.0F);
            List<AwakenMob> made = new ArrayList<>();
            BlockPos at = h.absolutePos(new BlockPos(0, UP, 0));
            AwakenMob m = Fauna3Tests.spawn(level, "hushling", Vec3.atBottomCenterOf(at), made);
            h.assertTrue(m != null, "the creature spawned");
            m.setNoAi(true);
            double d2 = p.distanceToSqr(m);
            h.assertTrue(d2 > 32 * 32, "it is far enough to roll");
            h.assertTrue(!m.removeWhenFarAway(d2), "you are looking at it: it stays");
            face(p, 180.0F);
            h.assertTrue(m.removeWhenFarAway(d2), "you turned away: the normal rules apply");
            face(p, 0.0F);
            BlockPos wall = h.absolutePos(new BlockPos(0, UP, -20));
            for (int x = -2; x <= 2; x++) for (int y = -1; y <= 3; y++) level.setBlockAndUpdate(wall.offset(x, y, 0), Blocks.STONE.defaultBlockState());
            h.assertTrue(m.removeWhenFarAway(d2), "rock between you: you can't see it, so it can go");
            for (int x = -2; x <= 2; x++) for (int y = -1; y <= 3; y++) level.setBlockAndUpdate(wall.offset(x, y, 0), Blocks.AIR.defaultBlockState());
            made.forEach(AwakenMob::discard);
            h.succeed();
        });

        // the distance a creature stops drawing at scales with the zoom
        AwakenTests.register("mob_render_reach_zoom", 20, h -> {
            List<AwakenMob> made = new ArrayList<>();
            AwakenMob m = Fauna3Tests.spawn(h.getLevel(), "drip_lurker", Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))), made);
            h.assertTrue(m != null, "the creature spawned");
            double hundred = 100.0 * 100.0;
            h.assertTrue(!m.shouldRenderAtSqrDistance(hundred), "at the bare eye a body this size stops drawing before 100 blocks");
            CameraHooks.installZoom(() -> 12.0);
            boolean zoomed = m.shouldRenderAtSqrDistance(hundred);
            CameraHooks.installZoom(() -> 1.0);
            h.assertTrue(zoomed, "through the binoculars it draws");
            made.forEach(AwakenMob::discard);
            h.succeed();
        });
    }

    private static void face(ServerPlayer p, float yaw) {
        p.setYRot(yaw);
        p.setXRot(0.0F);
        p.setYHeadRot(yaw);
    }

    /** A closed 3 by 3 room of stone, {@code air} blocks high, its floor at {@code c}. */
    private static void shaft(ServerLevel level, BlockPos c, int air) {
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-2, 0, -2), c.offset(2, air + 1, 2))) {
            int dx = Math.abs(q.getX() - c.getX()), dz = Math.abs(q.getZ() - c.getZ());
            boolean shell = q.getY() == c.getY() || q.getY() == c.getY() + air + 1 || dx == 2 || dz == 2;
            level.setBlockAndUpdate(q, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }
}
