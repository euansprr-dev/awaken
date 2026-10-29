package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/**
 * Fliers keep their room (FlightRoom; Euan 2026-10-28, the Empress Moth sitting still inside a birch canopy): every idle
 * spot a flier picks has room for its body and wings and a straight way there, and one saved inside a canopy gets free.
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

            // saved inside the canopy (a gap the body fits but the wings don't): within a few picks it is clear of the leaves
            BlockPos gap = o.offset(8, 6, 6);
            level.setBlockAndUpdate(gap, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(gap.above(), Blocks.AIR.defaultBlockState());
            m.snapTo(gap.getX() + 0.5, gap.getY(), gap.getZ() + 0.5);
            h.assertFalse(FlightRoom.free(m), "the test sets it wedged in the leaves");
            h.assertTrue(escapes(m, wander), "a wedged flier gets clear of the canopy");

            // boxed in under the canopy among the trunks (no room anywhere it could fly straight to): it gets out too
            m.snapTo(o.getX() - 8.5, o.getY() + 1.0, o.getZ() - 9.5);
            h.assertFalse(FlightRoom.free(m), "the test sets it under the canopy with a trunk in its wings' reach");
            h.assertTrue(escapes(m, wander), "a flier boxed in under the canopy gets clear");
            m.discard();
            h.succeed();
        });
    }

    /** Runs the idle goal as the game would, flying each chosen leg at once, until the flier has its room. */
    private static boolean escapes(AwakenMob m, MobGoals.FlyWander wander) {
        for (int i = 0; i < 4 * MobGoals.FlyWander.WEDGED_AFTER && !FlightRoom.free(m); i++) {
            wander.start();
            var move = m.getMoveControl();
            if (move.hasWanted()) {
                if (!FlightRoom.clearLine(m, move.getWantedX(), move.getWantedY(), move.getWantedZ())) return false;
                m.snapTo(move.getWantedX(), move.getWantedY(), move.getWantedZ());
                move.setWantedPosition(m.getX(), m.getY(), m.getZ(), 0.5);
                move.tick(); // arrives: the flight ends
            }
        }
        return FlightRoom.free(m);
    }
}
