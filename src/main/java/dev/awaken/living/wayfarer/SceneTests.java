package dev.awaken.living.wayfarer;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * The scene finder's GameTest (SPEC §21.5): a cliff ledge and flat road ground in real terrain, never on forbidden land;
 * and the Wayfarers' camp's (SPEC §27.6): laid round its fire, keeping its ground, its bedrolls' rule.
 */
public final class SceneTests {
    private SceneTests() {}

    public static void register() {
        AwakenTests.registerIsolated("living_scene_finder", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(new BlockPos(0, 1, 0));
            // isolated tests land under real terrain: build the scene on a floor above everything around, so the
            // heightmap sees only what the test built. Every column the finder can read is scanned (a sample every few
            // blocks missed narrow things, a spire or a floating shard, which then stood over the road ground)
            int top = origin.getY();
            for (int dx = -14; dx <= 38; dx++) for (int dz = -14; dz <= 14; dz++) {
                top = Math.max(top, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX() + dx, origin.getZ() + dz));
            }
            BlockPos base = new BlockPos(origin.getX(), Math.min(top + 2, level.getMaxY() - 14), origin.getZ());
            // a 7x7 stone tower ten high on a grass floor, and open floor 24 blocks off for the road
            AwakenTests.clearAbove(level, base, 30, 0, Math.max(12, top - base.getY()));
            for (int dx = -12; dx <= 36; dx++) for (int dz = -12; dz <= 12; dz++) level.setBlock(base.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            for (int dx = 0; dx < 7; dx++) for (int dz = 0; dz < 7; dz++) for (int dy = 0; dy < 10; dy++) {
                level.setBlock(base.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
            }
            BlockPos centre = base.offset(3, 0, 3);
            Scenes.Forbidden was = Scenes.forbidden;
            try {
                Scenes.forbidden = (l, pos) -> false;
                BlockPos cliff = Scenes.findSpot(level, centre, "cliff", RandomSource.create(1L), 0, 4);
                h.assertTrue(cliff != null, "a cliff spot on the tower");
                h.assertTrue(cliff.getY() == base.getY() + 10, "on its top: " + cliff.getY() + " vs " + (base.getY() + 10));
                h.assertTrue(Scenes.drop(level, cliff.getX(), cliff.getY(), cliff.getZ()) >= 6, "with a drop beside it");
                BlockPos foot = Scenes.foot(level, cliff);
                h.assertTrue(foot.getY() <= cliff.getY() - 6, "its foot is at the bottom: " + foot.getY());
                BlockPos road = Scenes.findSpot(level, base.offset(24, 0, 0), "road", RandomSource.create(2L), 0, 6);
                h.assertTrue(road != null && road.getY() == base.getY(), "flat open ground for a road scene");
                h.assertTrue(Scenes.findSpot(level, base.offset(24, 0, 0), "cliff", RandomSource.create(3L), 0, 6) == null, "no cliff on flat ground");
                // forbidden land (a Hearth's, the city's) is never used
                Scenes.forbidden = (l, pos) -> true;
                h.assertTrue(Scenes.findSpot(level, centre, "cliff", RandomSource.create(1L), 0, 4) == null, "never on a Hearth's land or the city's");
                h.assertTrue(Scenes.findSpot(level, base.offset(24, 0, 0), "road", RandomSource.create(2L), 0, 6) == null, "nor a road there");
            } finally {
                Scenes.forbidden = was;
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("living_camp_keeps", 200, h -> {
            ServerLevel level = h.getLevel();
            var server = level.getServer();
            BlockPos origin = h.absolutePos(new BlockPos(0, 1, 0));
            int top = origin.getY();
            for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
                top = Math.max(top, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX() + dx, origin.getZ() + dz));
            }
            BlockPos fire = new BlockPos(origin.getX(), Math.min(top + 2, level.getMaxY() - 14), origin.getZ());
            AwakenTests.clearAbove(level, fire, 12, 0, 10);
            for (int dx = -10; dx <= 10; dx++) for (int dz = -10; dz <= 10; dz++) level.setBlock(fire.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(fire.offset(1, 0, 1), Blocks.SHORT_GRASS.defaultBlockState(), 2);
            level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState(), 2);
            Scenes s = Scenes.get(server);
            long wasCamp = s.camp;
            boolean wasBuilt = s.campBuilt;
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            Runnable restore = () -> {
                s.camp = wasCamp;
                s.campBuilt = wasBuilt;
                Camp.forget();
                server.getPlayerList().remove(p);
            };
            net.minecraft.world.entity.monster.zombie.Zombie z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            try {
                s.camp = fire.offset(3, 0, -3).asLong();
                Camp.forget();
                h.assertTrue(fire.equals(Camp.fire(server)), "the camp finds its fire: " + Camp.fire(server));
                Camp.build(level, fire);
                var foot = level.getBlockState(fire.offset(-2, 0, 0));
                var head = level.getBlockState(fire.offset(-3, 0, 0));
                h.assertTrue(foot.is(dev.awaken.living.LivingContent.BEDROLL.get()) && head.is(dev.awaken.living.LivingContent.BEDROLL.get())
                        && head.getValue(Bedroll.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD, "a bedroll by the fire, feet to it");
                h.assertTrue(level.getBlockState(fire.offset(0, 0, 2)).is(dev.awaken.living.LivingContent.BEDROLL.get()), "and a second");
                h.assertTrue(level.getBlockState(fire.above()).is(dev.awaken.living.LivingContent.CAMP_POT.get()), "the pot hung over the fire");
                h.assertTrue(level.getBlockState(fire.offset(-1, 0, -2)).is(Blocks.STRIPPED_SPRUCE_LOG), "logs to sit on");
                h.assertTrue(level.getBlockState(fire.offset(1, 0, 1)).isAir(), "the grass round the fire trodden flat");
                // a bedroll sleeps you when it's dark and never moves your spawn
                h.assertTrue(Bedroll.RULE.canSleep() == net.minecraft.world.attribute.BedRule.Rule.WHEN_DARK
                        && !Bedroll.RULE.canSetSpawn(level), "a bedroll: sleep when it's dark, never the spawn");
                // its ground: nothing hunts you there; out past the edge the wilds are the wilds
                p.snapTo(fire.getX() + 4.5, fire.getY(), fire.getZ() + 0.5);
                h.assertTrue(Camp.inside(level, p.blockPosition(), Camp.SAFE) && !Camp.inside(level, fire.offset(40, 0, 0), Camp.SAFE), "safe within 32");
                z.snapTo(fire.getX() + 8.5, fire.getY(), fire.getZ() + 0.5, 0, 0);
                level.addFreshEntity(z);
                var LT = net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.LivingTargetType.MOB_TARGET;
                var hunt = new net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent(z, p, LT);
                Camp.onTarget(hunt);
                h.assertTrue(hunt.isCanceled(), "nothing hunts a player on the camp's ground");
                p.snapTo(fire.getX() + 60.5, fire.getY(), fire.getZ() + 0.5);
                var outside = new net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent(z, p, LT);
                Camp.onTarget(outside);
                h.assertFalse(outside.isCanceled(), "out past the edge, the wilds are the wilds");
                p.snapTo(fire.getX() + 4.5, fire.getY(), fire.getZ() + 0.5);
                // a step that needs the dark: the bedroll says so instead of sleeping it away
                String moths = dev.awaken.living.quest.QuestBook.get("awaken:moth_lights") != null ? "awaken:moth_lights" : "moth_lights";
                h.assertTrue(Camp.nightNeeded(p) == null, "no step needs the night yet");
                h.assertTrue(dev.awaken.living.quest.QuestEngine.devStep(p, moths, "moths"), "Moth Lights at its first step");
                h.assertTrue(Camp.nightNeeded(p) != null && Camp.nightNeeded(p).contains("dark"), "the moths need the dark: " + Camp.nightNeeded(p));
            } catch (RuntimeException e) {
                z.discard();
                restore.run();
                throw e;
            }
            // a monster on the camp's ground, once it stands on it, is sent back out past the edge
            h.startSequence().thenWaitUntil(() -> h.assertTrue(z.onGround(), "the zombie lands")).thenExecute(() -> {
                try {
                    Camp.turnAway(server);
                    var to = z.getNavigation().getTargetPos();
                    h.assertTrue(z.getNavigation().isInProgress() && to != null && to.distSqr(fire) > (double) (Camp.SAFE - 2) * (Camp.SAFE - 2),
                            "a monster on it is sent back out past the edge: " + to);
                } finally {
                    z.discard();
                    restore.run();
                }
            }).thenSucceed();
        });
    }
}