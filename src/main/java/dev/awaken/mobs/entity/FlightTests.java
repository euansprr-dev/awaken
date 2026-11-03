package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Fliers keep their room (FlightRoom; Euan 2026-10-28, the Empress Moth sitting still inside a birch canopy): every idle
 * spot a flier picks has room for its body and wings and a straight way there. Fliers find their way (the Plumed Coil
 * stuck in a tree, the same day): round a tree, and out of a canopy it was saved inside, by moving.
 */
public final class FlightTests {
    private FlightTests() {}

    public static void register() {
        AwakenTests.registerIsolated("mobs_flier_room", 40, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(new BlockPos(0, 1, 0));
            // a wood: a floor, trunks, and a leaf canopy from 6 to 8 up, with a clearing open to the sky in the middle
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                level.setBlockAndUpdate(o.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 1; y <= 30; y++) level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
                boolean clearing = Math.abs(x) <= 4 && Math.abs(z) <= 4;
                if (!clearing) for (int y = 6; y <= 8; y++) {
                    level.setBlockAndUpdate(o.offset(x, y, z), Blocks.BIRCH_LEAVES.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                }
                if (!clearing && Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) {
                    for (int y = 1; y <= 7; y++) level.setBlockAndUpdate(o.offset(x, y, z), Blocks.BIRCH_LOG.defaultBlockState());
                }
            }
            MobDef d = Bestiary.get("empress_moth");
            h.assertTrue(d != null && d.flies(), "the Empress Moth is a flier");
            AwakenMob m = Mobs.spawn(level, d, Vec3.atBottomCenterOf(o.offset(0, 2, 0)), 6,
                    new MobRole(d.tier.role, 6, d.theme.id, Optional.empty(), ""), Set.of());
            h.assertTrue(m instanceof NamedBeast, "a named beast");
            m.setNoAi(true);
            m.setHomeTo(o, d.arena);
            h.assertTrue(FlightRoom.big(m) && FlightRoom.reach(m) > m.getBbWidth() * 0.5 + 1.0, "its wings are wider than its hitbox");

            // from its clearing: every spot it would fly to has room and a clear way there, and it can rise over the canopy
            MobGoals.FlyWander wander = new MobGoals.FlyWander(m);
            int found = 0, over = 0;
            for (int i = 0; i < 200; i++) {
                Vec3 at = wander.pick();
                if (at == null) continue;
                found++;
                if (at.y > o.getY() + 9) over++;
                h.assertTrue(FlightRoom.fits(m, at.x, at.y, at.z), "an idle spot with room for the wings: " + at.subtract(Vec3.atBottomCenterOf(o)));
                h.assertTrue(FlightRoom.clearLine(m, at.x, at.y, at.z), "a straight way to it: " + at.subtract(Vec3.atBottomCenterOf(o)));
                m.snapTo(at.x, at.y, at.z); // fly there and choose again from there
            }
            h.assertTrue(found > 50, "it keeps finding places to go: " + found);
            h.assertTrue(over > 0, "it rises out of its clearing to circle over the canopy");
            h.assertTrue(FlightRoom.free(m), "wherever it ends up, its wings are clear");

            m.discard();
            h.succeed();
        });
        AwakenTests.registerIsolated("mobs_flier_canopy", 400, FlightTests::canopy);
    }

    /**
     * Fliers find their way (SPEC "Fliers find their way"; Euan 2026-10-28, the Plumed Coil stuck in a tree): a Plumed
     * Coil flies round a tree to a spot behind it, its body touching nothing on the way; one saved inside a dense canopy
     * gets clear by moving, within twenty seconds, never more than a block a tick, its collision on, every leaf still there.
     */
    static void canopy(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos c = AwakenTests.site(h, new BlockPos(-14, 0, -14), new BlockPos(14, 0, 14), Blocks.GRASS_BLOCK.defaultBlockState()).offset(14, 1, 14);
        BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState log = Blocks.JUNGLE_LOG.defaultBlockState();
        // a tree in the way: a wall of leaves round a trunk
        for (int x = -1; x <= 0; x++) for (int z = -10; z <= -6; z++) for (int y = 0; y <= 7; y++) level.setBlock(c.offset(x, y, z), leaves, 2);
        for (int y = 0; y <= 7; y++) level.setBlock(c.offset(0, y, -8), log, 2);
        // a dense canopy, five leaves deep, on four trunks
        for (int x = -5; x <= 5; x++) for (int z = 2; z <= 12; z++) for (int y = 6; y <= 10; y++) level.setBlock(c.offset(x, y, z), leaves, 2);
        for (int[] t : new int[][] {{-3, 4}, {3, 4}, {-3, 11}, {3, 11}}) for (int y = 0; y <= 10; y++) level.setBlock(c.offset(t[0], y, t[1]), log, 2);
        int leafCount = leaves(level, c);
        MobDef d = Bestiary.get("plumed_coil");
        MobRole role = new MobRole(d.tier.role, 5, d.theme.id, Optional.empty(), "");

        // 1. round the tree: its controller and its body's own collision, a tick at a time
        Vec3 start = Vec3.atBottomCenterOf(c.offset(-8, 2, -8)), goal = Vec3.atBottomCenterOf(c.offset(8, 2, -8));
        AwakenMob coil = Mobs.spawn(level, d, start, 5, role, Set.of());
        h.assertTrue(coil != null && coil.getMoveControl() instanceof FlyerMoveControl, "the Plumed Coil flies by the flier's controller");
        coil.setNoAi(true);
        coil.snapTo(start.x, start.y, start.z);
        h.assertTrue(Math.abs(coil.getBbWidth() - d.width * d.scale) < 0.01, "its hitbox is its scaled body from birth: " + coil.getBbWidth());
        coil.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 0.6);
        boolean touched = false;
        int steps = 0;
        for (; steps < 300 && coil.getMoveControl().hasWanted(); steps++) {
            coil.tickCount++;
            coil.getMoveControl().tick();
            coil.move(MoverType.SELF, coil.getDeltaMovement());
            Vec3 v = coil.getDeltaMovement();
            coil.setDeltaMovement(v.x * 0.91, v.y * 0.98, v.z * 0.91); // the air's drag, as travel applies it
            touched |= !level.noBlockCollision(coil, coil.getBoundingBox().inflate(0.05));
        }
        h.assertTrue(coil.position().distanceTo(goal) < 1.5, "it got round the tree to the spot behind it: " + coil.position().subtract(goal));
        h.assertFalse(touched, "its body never touched the tree on the way");
        coil.discard();
        int round = steps;

        // 2. saved inside the canopy: it gets clear by moving, the game ticking it as it would
        AwakenMob caught = Mobs.spawn(level, d, Vec3.atBottomCenterOf(c.offset(0, 14, 7)), 5, role, Set.of());
        caught.snapTo(c.getX() + 0.5, c.getY() + 7, c.getZ() + 7.5);
        h.assertTrue(Unwedge.inside(caught), "the test has it inside the leaves");
        Vec3[] last = {caught.position()};
        double[] longest = {0};
        long began = level.getGameTime();
        h.onEachTick(() -> {
            longest[0] = Math.max(longest[0], last[0].distanceTo(caught.position()));
            last[0] = caught.position();
            h.assertFalse(caught.noPhysics, "it never switches its collision off");
        });
        h.succeedWhen(() -> {
            h.assertFalse(Unwedge.inside(caught), "its body is out of the leaves");
            h.assertTrue(FlightRoom.free(caught), "its wings and coils are clear of every block");
            h.assertTrue(longest[0] < 1.0, "it moved out, never more than a block a tick (no teleport): " + longest[0]);
            h.assertTrue(leaves(level, c) == leafCount, "every leaf is still there");
            dev.awaken.Awaken.LOG.info("[flight] round the tree in {} ticks; out of the canopy in {} ticks, {} blocks up, longest step {}",
                    round, level.getGameTime() - began, String.format("%.1f", caught.getY() - c.getY() - 7), String.format("%.2f", longest[0]));
            caught.discard();
        });
    }

    /** The test's leaves: the tree's wall and the canopy. */
    private static int leaves(ServerLevel level, BlockPos c) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-5, 0, -10), c.offset(5, 10, 12))) if (level.getBlockState(p).is(Blocks.JUNGLE_LEAVES)) n++;
        return n;
    }
}
