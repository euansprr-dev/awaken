package dev.awaken.frontier;

import dev.awaken.core.api.FarLanding;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.region.RegionMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Far Landing's checks (SPEC §67), one per mechanism and all pure: the landing is fair and the same every time on
 * twenty seeds; the land under rules 4 is measured from the landing (E round it, the city's ring kept) while rules 3 is
 * untouched; the skiff and its jetty fit their footprint in every facing, with the helm on the deck. One is not pure:
 * a landed player with no Hearth wakes at the landing, through the server's own respawn (§67.12).
 */
final class FrontierTests {
    private FrontierTests() {}

    static final String[] LANDS = {"plains", "forest", "desert", "river", "jagged_peaks", "meadow", "swamp", "ocean", "birch_forest", "taiga"};

    static void register() {
        AwakenTests.register("frontier_landing_fair", 100, h -> {
            for (long seed = 1; seed <= 20; seed++) {
                long sd = seed;
                FarLanding.Sampler s = new FarLanding.Sampler() {
                    @Override
                    public String biome(int x, int z) {
                        // land in 400-block cells, picked by the seed: a mix of fair and unfair ground, with a sea to the west
                        if (x < -4000) return "ocean";
                        long k = (Math.floorDiv(x, 400) * 341873128712L + Math.floorDiv(z, 400) * 132897987541L) ^ sd * 0x9E3779B97F4A7C15L;
                        k ^= k >>> 29;
                        return LANDS[(int) Math.floorMod(k, LANDS.length)];
                    }

                    @Override
                    public int top(int x, int z) {
                        return 70 + (int) Math.floorMod((x / 64) * 31 + (z / 64) * 17 + sd, 9);
                    }

                    @Override
                    public int seaLevel() {
                        return 62;
                    }
                };
                int[] a = FarLanding.find(seed, 100, -50, s), b = FarLanding.find(seed, 100, -50, s);
                h.assertTrue(a[0] == b[0] && a[1] == b[1], "seed " + seed + ": the same landing every time");
                double d = Math.hypot(a[0] - 100, a[1] + 50);
                h.assertTrue(d >= FarLanding.MIN - 2 && d <= FarLanding.MAX + 2, "seed " + seed + ": 5 to 7 km out, not " + Math.round(d));
                String land = s.biome(a[0], a[1]);
                h.assertTrue(!List.of("ocean", "river", "desert", "jagged_peaks", "swamp").contains(land), "seed " + seed + ": fair ground, not " + land);
            }
            h.succeed();
        });
        AwakenTests.register("frontier_levels_rules_four", 200, h -> {
            RegionMap.Probe plain = (x, z) -> RegionMap.Land.PLAIN;
            int lx = 6000, lz = 0;
            RegionMap far = new RegionMap(4245L, 0, 0, plain, 4, lx, lz);
            h.assertTrue(far.farLanding() && far.landingX() == lx, "a rules 4 map measures from its landing");
            // E land round the landing, to the safe line, in every direction
            int top = 0;
            for (int k = 0; k < 24; k++) {
                double ang = k * Math.PI / 12;
                for (int r = 0; r <= 1300; r += 260) top = Math.max(top, far.levelAt(lx + (int) (Math.cos(ang) * r), lz + (int) (Math.sin(ang) * r)));
            }
            h.assertTrue(top <= 9, "E land to 1,300 round the landing, not level " + top);
            // the city keeps its ring: 8 or 9 just past the Commons
            int ring = far.levelAt(850, 0);
            h.assertTrue(ring >= 8 && ring <= 9, "the city's calm ring: level " + ring);
            // rules 3 ignores a landing: the same land as a map made without one
            RegionMap three = new RegionMap(4245L, 0, 0, plain, 3), threeWith = new RegionMap(4245L, 0, 0, plain, 3, lx, lz);
            h.assertTrue(!threeWith.farLanding(), "rules 3 has no far landing");
            for (int i = 0; i < 40; i++) {
                int x = (i * 1237) % 9000 - 4500, z = (i * 3571) % 9000 - 4500;
                h.assertTrue(three.levelAt(x, z) == threeWith.levelAt(x, z), "rules 3 unchanged at " + x + ", " + z);
            }
            // far from both, the land still climbs: somewhere 3 to 8 km past the landing is harder than E
            int past = 0;
            for (int i = 0; i < 10; i++)
                for (int j = 0; j < 10; j++) past = Math.max(past, far.levelAt(lx + 3000 + i * 500, lz - 2500 + j * 500));
            h.assertTrue(past > 9, "the land gets harder past the landing: " + past);
            h.succeed();
        });
        AwakenTests.register("frontier_ground_under_trees", 60, h -> {
            // a giant's bough high over open ground, and a crowned trunk standing on it: the land under both is the floor,
            // where the no-leaves heightmap stops on the bough (Euan's ridge mooring was set on Elder Colossi, 12 Nov)
            BlockPos floor = AwakenTests.site(h, new BlockPos(0, 1, 0), new BlockPos(4, 20, 4), Blocks.GRASS_BLOCK.defaultBlockState());
            ServerLevel level = h.getLevel();
            BlockPos bough = floor.offset(1, 0, 1), trunk = floor.offset(3, 0, 3);
            level.setBlock(bough.above(11), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(bough.above(12), Blocks.OAK_WOOD.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(bough.above(13), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int y = 1; y <= 6; y++) level.setBlock(trunk.above(y), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(trunk.above(7), Blocks.OAK_LEAVES.defaultBlockState(), Block.UPDATE_CLIENTS);
            int want = floor.getY() + 1, map = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bough.getX(), bough.getZ());
            h.assertTrue(map == bough.getY() + 13, "the no-leaves heightmap stops on the bough (" + map + ")");
            for (BlockPos c : List.of(bough, trunk, floor)) {
                int got = Moorings.ground(level, c.getX(), c.getZ());
                h.assertTrue(got == want, "the land at " + c.toShortString() + " is " + got + ", not the floor's " + want);
            }
            h.succeed();
        });
        AwakenTests.register("frontier_skiff_shape", 40, h -> {
            BlockPos origin = new BlockPos(1000, 90, -2000);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                Set<BlockPos> taken = new HashSet<>();
                for (List<Skiff.Part> parts : List.of(Skiff.skiff(), Skiff.jetty(), Skiff.landingExtras())) {
                    for (Skiff.Part part : parts) {
                        BlockPos at = Skiff.at(origin, facing, part.x(), part.y(), part.z());
                        h.assertTrue(taken.add(at), facing + ": two parts at " + part.x() + ", " + part.y() + ", " + part.z());
                        h.assertTrue(Skiff.inFootprint(origin, facing, at), facing + ": a part outside the footprint at " + part.x() + ", " + part.z());
                    }
                }
                // every lashed side of canvas, turned with the skiff, runs into a spar (SPEC §67.4)
                Map<BlockPos, BlockState> placed = new HashMap<>();
                Rotation rot = Skiff.rotation(facing);
                for (List<Skiff.Part> parts : List.of(Skiff.skiff(), Skiff.jetty(), Skiff.landingExtras()))
                    for (Skiff.Part part : parts) placed.put(Skiff.at(origin, facing, part.x(), part.y(), part.z()), part.state().rotate(rot));
                int lashed = 0;
                for (Map.Entry<BlockPos, BlockState> e : placed.entrySet()) {
                    if (!e.getValue().is(SkiffBlocks.SAIL.get())) continue;
                    for (Direction d : Direction.values()) {
                        if (!e.getValue().getValue(SkiffBlocks.Sail.side(d))) continue;
                        lashed++;
                        BlockState there = placed.get(e.getKey().relative(d));
                        h.assertTrue(there != null && there.is(SkiffBlocks.SPAR.get()), facing + ": canvas at " + e.getKey().toShortString()
                                + " lashed " + d + " to " + there);
                    }
                }
                h.assertTrue(lashed >= 16, facing + ": the canvas is lashed to its spars (" + lashed + " sides)");
                BlockPos stand = Skiff.at(origin, facing, Skiff.STAND);
                h.assertTrue(Skiff.onDeck(origin, facing, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5), facing + ": the helm's stand is on the deck");
                // the bow points the way it faces: the bow light six ahead of the berth's middle
                h.assertTrue(Skiff.at(origin, facing, 0, 0, 6).equals(origin.relative(facing, 6)), facing + ": the bow points " + facing);
            }
            h.assertTrue(Flights.compass(0, -100).equals("north") && Flights.compass(100, 0).equals("east"), "the compass reads the way Minecraft does");
            h.succeed();
        });
        AwakenTests.registerIsolated("frontier_wakes_at_landing", 100, h -> {
            // Euan 12 Nov: "if you die before placing your hearth, it spawns you ALL THE WAY BACK IN CARILLON" (SPEC §67.12)
            Set<BlockPos> plan = new HashSet<>();
            for (List<Skiff.Part> parts : List.of(Skiff.skiff(), Skiff.jetty(), Skiff.landingExtras()))
                for (Skiff.Part part : parts) plan.add(new BlockPos(part.x(), part.y(), part.z()));
            BlockPos bay = Waking.BAY;
            h.assertTrue(plan.contains(bay.below()) && !plan.contains(bay) && !plan.contains(bay.above()), "the bay has pier underfoot and room to stand");
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            FrontierData d = FrontierData.get(server);
            FrontierData.Mooring before = d.landing();
            ServerPlayer a = h.makeMockServerPlayerInLevel(), b = h.makeMockServerPlayerInLevel();
            try {
                // a has crossed and fell out by the landing, with no Hearth and no bed: they wake on its pier, by the hold
                BlockPos fell = a.blockPosition();
                FrontierData.Mooring m = new FrontierData.Mooring(fell, Direction.EAST, true, Moorings.LOOK);
                d.landing(m);
                d.mark(a.getUUID(), "landed");
                h.assertTrue(a.getRespawnConfig() == null && HearthHooks.hearthsOf(a).isEmpty(), "no Hearth and no bed");
                Waking.onDeath(new LivingDeathEvent(a, level.damageSources().generic()));
                a = server.getPlayerList().respawn(a, false, Entity.RemovalReason.KILLED);
                Vec3 want = Vec3.atBottomCenterOf(Skiff.at(m.origin(), m.facing(), bay));
                h.assertTrue(a.level() == server.overworld() && Math.abs(a.getX() - want.x) < 0.01 && Math.abs(a.getZ() - want.z) < 0.01,
                        "a wakes at the landing's bay " + want + ", not at " + a.position());
                BlockPos hold = Skiff.at(m.origin(), m.facing(), Skiff.HOLD);
                Vec3 look = Vec3.directionFromRotation(0F, a.getYRot());
                h.assertTrue(look.x * (hold.getX() - want.x) + look.z * (hold.getZ() - want.z) > 0.5, "facing the hold (yaw " + a.getYRot() + ")");
                // b, in the same world, never crossed: world spawn, as before (each by their own journey)
                Vec3 spawn = Vec3.atBottomCenterOf(server.findRespawnDimension().getRespawnData().pos());
                Waking.onDeath(new LivingDeathEvent(b, level.damageSources().generic()));
                b = server.getPlayerList().respawn(b, false, Entity.RemovalReason.KILLED);
                h.assertTrue(b.position().distanceToSqr(want) > 4, "b hasn't landed: world spawn, not the landing (at " + b.position() + ")");
                // a falls by the city with the landing far off: the city is nearer, world spawn
                d.landing(new FrontierData.Mooring(BlockPos.containing(spawn).offset(6000, 0, 0), Direction.EAST, true, Moorings.LOOK));
                a.snapTo(spawn.x, spawn.y, spawn.z);
                Waking.onDeath(new LivingDeathEvent(a, level.damageSources().generic()));
                a = server.getPlayerList().respawn(a, false, Entity.RemovalReason.KILLED);
                h.assertTrue(Math.abs(a.getX() - spawn.x) < 64 && Math.abs(a.getZ() - spawn.z) < 64, "a fall by the city wakes in the city (at " + a.position() + ")");
                h.succeed();
            } finally {
                d.landing(before);
                for (ServerPlayer p : List.of(a, b)) {
                    d.marks.remove(p.getUUID());
                    server.getPlayerList().remove(p);
                }
            }
        });
    }
}
