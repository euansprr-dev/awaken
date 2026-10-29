package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.Aim;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static dev.awaken.arms.ArmsTests.done;
import static dev.awaken.arms.ArmsTests.fighter;
import static dev.awaken.arms.ArmsTests.put;
import static dev.awaken.arms.ArmsTests.runActive;

/**
 * Lock-on's server path (SPEC §40): a press's creature is checked before it is struck as the meant one, the meant one
 * is reached overhead and by the step in, people are never meant from the side, and a flier can be brought down.
 */
final class AimStrikeTests {
    private AimStrikeTests() {}

    static void register() {
        AwakenTests.register("aim_flier_locked_hits", 60, AimStrikeTests::flierLockedHits);
        AwakenTests.register("aim_step_in", 60, AimStrikeTests::stepIn);
        AwakenTests.register("aim_no_wall", 40, AimStrikeTests::noWall);
        AwakenTests.register("aim_no_player", 40, AimStrikeTests::noPlayer);
        AwakenTests.register("aim_flier_brought_down", 140, AimStrikeTests::broughtDown);
    }

    private static <T extends Mob> T hover(GameTestHelper h, EntityType<T> type, double x, double z, double up) {
        T m = put(h, type, x, z, 200);
        m.setNoGravity(true);
        Vec3 v = h.absoluteVec(new Vec3(x, 1 + up, z));
        m.snapTo(v.x, v.y, v.z, 180F, 0F);
        return m;
    }

    /**
     * A creature circling 3.2 blocks over the feet (above the old band: its bottom over the eye + 1.5) is struck when it
     * is the one the press meant; a hostile just as high beside it, not meant, is still out of the band.
     */
    static void flierLockedHits(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        LivingEntity meant = hover(h, EntityTypes.PIG, 1.5, 3.5, 3.2);
        LivingEntity beside = hover(h, EntityTypes.ZOMBIE, 0.4, 3.5, 3.2);
        h.assertTrue(meant.getBoundingBox().minY > p.getEyeY() + 1.5, "the flier sits above the old band");
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, -34F, meant.getId()));
        ArmsState s = ArmsServer.state(p);
        h.assertTrue(s.aimed == meant.getId(), "the press's creature passes the check (in reach, 34° up, seen)");
        runActive(p);
        h.assertTrue(meant.getHealth() < 200, "the meant flier overhead is struck");
        h.assertTrue(beside.getHealth() == 200, "a hostile as high, not meant, stays out of the band");
        done(h, p);
    }

    /**
     * A chain's first strike reaches the meant creature one step (the longsword's 1.0) past its reach; one further than
     * that is not reached, nor is a creature in the step that wasn't meant.
     */
    static void stepIn(GameTestHelper h) {
        ServerPlayer near = fighter(h, 0.5, 0.5, 0F);
        LivingEntity stepped = put(h, EntityTypes.PIG, 0.5, 4.5, 200); // 3.6 from the eye: past 3.3, inside 4.3
        LivingEntity notMeant = put(h, EntityTypes.ZOMBIE, -0.6, 4.4, 200);
        ArmsServer.onPress(near, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 10F, stepped.getId()));
        h.assertTrue(ArmsServer.state(near).aimed == stepped.getId(), "the stepped-to pig is the meant one");
        runActive(near);
        h.assertTrue(stepped.getHealth() < 200, "the step in reaches the meant pig");
        h.assertTrue(notMeant.getHealth() == 200, "a hostile in the step, not meant, isn't reached");

        ServerPlayer far = fighter(h, 4.5, 0.5, 0F);
        LivingEntity beyond = put(h, EntityTypes.PIG, 4.5, 5.6, 200); // 4.7 from the eye: past the step
        ArmsServer.onPress(far, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 8F, beyond.getId()));
        runActive(far);
        h.assertTrue(beyond.getHealth() == 200, "a pig past reach and step is not reached");
        done(h, near, far);
    }

    /** A creature behind a wall is never the meant one, however the client names it. */
    static void noWall(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        LivingEntity hidden = put(h, EntityTypes.PIG, 1.5, 3.5, 200);
        for (int y = 1; y <= 3; y++) for (int x = 0; x <= 3; x++) h.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 10F, hidden.getId()));
        h.assertTrue(ArmsServer.state(p).aimed == -1, "a pig behind stone fails the check");
        runActive(p);
        h.assertTrue(hidden.getHealth() == 200, "and isn't struck");
        done(h, p);
    }

    /** A person is the meant one only right under the view (today's click), never from the side as a lock would. */
    static void noPlayer(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        ServerPlayer ahead = h.makeMockServerPlayerInLevel();
        Vec3 a = h.absoluteVec(new Vec3(1.5, 1, 2.6));
        ahead.snapTo(a.x, a.y, a.z, 180F, 0F);
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, ahead.getId()));
        h.assertTrue(ArmsServer.state(p).aimed == ahead.getId(), "a person straight ahead is still struck as clicked");
        Vec3 b = h.absoluteVec(new Vec3(3.4, 1, 2.0));
        ahead.snapTo(b.x, b.y, b.z, 180F, 0F);
        float off = Aim.offEdge(p.getEyePosition(), ahead.getBoundingBox(), 0F, 0F);
        h.assertTrue(off > Aim.PERSON_CAP && off < Aim.AIM_CAP, "the person stands where a creature would pass, off " + off);
        ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_STRIKE, 0F, 0F, ahead.getId()));
        h.assertTrue(ArmsServer.state(p).aimed == -1, "a person to the side is never the meant one");
        done(h, p, ahead);
    }

    /**
     * Brought down: a normal flier grounded drops (gravity on), stands open, and flies again after
     * {@link Aim#GROUNDED_TICKS}; a creature that doesn't fly isn't grounded.
     */
    static void broughtDown(GameTestHelper h) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Awaken.id("gale_harpy"));
        if (!(type.create(h.getLevel(), EntitySpawnReason.COMMAND) instanceof Mob harpy)) {
            h.fail("no gale_harpy");
            return;
        }
        for (int bx = -2; bx <= 4; bx++) for (int bz = -2; bz <= 4; bz++) h.setBlock(new BlockPos(bx, 0, bz), Blocks.STONE);
        Vec3 v = h.absoluteVec(new Vec3(1.5, 5, 1.5));
        harpy.snapTo(v.x, v.y, v.z, 0F, 0F);
        h.getLevel().addFreshEntity(harpy);
        double startY = harpy.getY();
        LivingEntity walker = put(h, EntityTypes.ZOMBIE, 3.5, 3.5, 20);
        h.assertTrue(!Strikes.ground(walker, Aim.GROUNDED_TICKS), "a zombie has no air to come down from");
        h.assertTrue(harpy.isNoGravity(), "a harpy flies");
        h.assertTrue(Strikes.ground(harpy, Aim.GROUNDED_TICKS), "a normal flier is brought down");
        h.assertTrue(!harpy.isNoGravity(), "gravity has it");
        h.runAfterDelay(Aim.GROUNDED_TICKS - 10, () -> {
            h.assertTrue(harpy.getY() < startY - 2.5, "it fell (from " + startY + " to " + harpy.getY() + ")");
            h.assertTrue(!harpy.isNoGravity(), "still down");
        });
        h.runAfterDelay(Aim.GROUNDED_TICKS + 15, () -> {
            h.assertTrue(harpy.isNoGravity(), "it flies again after " + Aim.GROUNDED_TICKS + " ticks");
            h.succeed();
        });
    }
}
