package dev.awaken.above.ruin;

import dev.awaken.Awaken;
import dev.awaken.above.AboveBlocks;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * The redesigned ruins built in the world on ground made for each (a meadow for the vault, dunes for the monolith, a
 * sea floor under water for the lab, open sky for the platform): their silhouettes reach the heights the design asks
 * for, they are lit, the loot and the outer cache are there, the vault's sealed chamber can only be reached through its
 * rank door, the monolith's chamber through its hatch, the lab's domes are dry. Each build is written to
 * run/structures/ruin_&lt;kind&gt;.json for {@code tools/art/specs/structures.py}.
 */
final class RuinTests {
    private RuinTests() {}

    /** Ruins build this high over the test origin (isolated batches share the spot). */
    static final int UP = 140;

    static void register() {
        spacing();
        AwakenTests.registerIsolated("ruins_redesign_build_and_contracts", 400, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(new BlockPos(-24, UP, -24));
            try {
                for (RuinKind kind : RuinKind.values()) {
                    if (kind == RuinKind.SPAWN_VAULT) continue;
                    RuinBuilder.Size s = RuinBuilder.size(kind);
                    wipe(level, origin, s);
                    int ground = ground(level, origin, s, kind);
                    BlockPos corner = origin.above(kind == RuinKind.MONOLITH ? ground - 7 : kind == RuinKind.OCEAN ? -1 : kind == RuinKind.VAULT ? ground - 1 : 4);
                    int[] lootChests = {0}, outer = {0};
                    RuinBuilder.build(kind, 99L + kind.ordinal(), 2, new RuinBuilder.Sink() {
                        @Override
                        public void set(int x, int y, int z, BlockState state) {
                            level.setBlock(corner.offset(x, y, z), state, Block.UPDATE_CLIENTS);
                        }

                        @Override
                        public void fillDown(int x, int y, int z, BlockState state, int depth) {
                            BlockPos.MutableBlockPos m = corner.offset(x, y, z).mutable();
                            for (int i = 0; i < depth; i++) {
                                BlockState here = level.getBlockState(m);
                                if (!here.isAir() && here.getFluidState().isEmpty() && !here.canBeReplaced()) break;
                                level.setBlock(m, state, Block.UPDATE_CLIENTS);
                                m.move(Direction.DOWN);
                            }
                        }

                        @Override
                        public void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> table) {
                            BlockPos at = corner.offset(x, y, z);
                            level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_CLIENTS);
                            RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), at, table);
                            if (table.equals(RuinBuilder.loot("landmark_outer"))) outer[0]++;
                            else lootChests[0]++;
                        }
                    });
                    String k = kind.id;
                    int machines = 0, cores = 0, lights = 0, membranes = 0, top = 0;
                    for (BlockPos q : BlockPos.betweenClosed(corner, corner.offset(s.x() - 1, s.y() - 1, s.z() - 1))) {
                        BlockState st = level.getBlockState(q);
                        if (st.isAir()) continue;
                        if (st.is(AboveBlocks.BROKEN_MACHINE.get())) machines++;
                        if (st.is(AboveBlocks.DOOR_CORE.get())) cores++;
                        if (st.is(AboveBlocks.MEMBRANE.get())) membranes++;
                        if (st.getLightEmission() >= 10) lights++;
                        top = Math.max(top, q.getY() - corner.getY());
                    }
                    h.assertTrue(machines >= 1 && machines <= 3, k + ": machines " + machines);
                    h.assertTrue(lootChests[0] >= 1 && outer[0] == 1, k + ": loot " + lootChests[0] + ", outer cache " + outer[0]);
                    h.assertTrue(lights >= 8, k + ": lit (" + lights + " bright blocks)");
                    switch (kind) {
                        case VAULT -> {
                            h.assertTrue(cores == 1, "vault: one door core");
                            h.assertTrue(top >= 16, "vault: its crown stands 16+ over the ground, " + top);
                            // from the forecourt, through the portal and the antechamber, the machines can't be reached
                            // without passing the rank door
                            Set<BlockPos> seen = reach(level, corner.offset(11, 1, 1), corner, s);
                            h.assertTrue(seen.contains(corner.offset(11, 1, 8).immutable()), "vault: the antechamber is open to the forecourt");
                            h.assertFalse(seen.contains(corner.offset(11, 1, 17).immutable()), "vault: the sealed chamber is sealed");
                        }
                        case MONOLITH -> {
                            h.assertTrue(top - 6 >= 34, "monolith: it towers 34+ over the plaza, " + (top - 6));
                            Set<BlockPos> seen = reach(level, corner.offset(4, 8, 8), corner, s);
                            h.assertTrue(seen.contains(corner.offset(8, 2, 8).immutable()), "monolith: the hatch leads down to the chamber");
                        }
                        case OCEAN -> {
                            h.assertTrue(membranes > 0, "ocean lab: an airlock");
                            h.assertTrue(level.getBlockState(corner.offset(15, 2, 17)).isAir() && level.getBlockState(corner.offset(15, 2, 4)).isAir()
                                    && level.getBlockState(corner.offset(4, 2, 17)).isAir(), "ocean lab: the domes are dry inside");
                            h.assertTrue(level.getBlockState(corner.offset(29, 3, 3)).is(Blocks.WATER), "ocean lab: the sea stays outside");
                        }
                        case SKY -> h.assertTrue(top >= 24, "sky platform: a spire over the cabin, " + top);
                        default -> {}
                    }
                    dump(level, "ruin_" + k, corner, corner.offset(-2, -3, -2), corner.offset(s.x() + 1, s.y(), s.z() + 1));
                }
            } finally {
                for (RuinKind kind : RuinKind.values()) wipe(level, origin, RuinBuilder.size(kind));
            }
            h.succeed();
        });
    }

    /** Lays ground for a kind and returns its height above the origin. */
    private static int ground(ServerLevel level, BlockPos origin, RuinBuilder.Size s, RuinKind kind) {
        int g = switch (kind) {
            case VAULT -> 4;
            case MONOLITH -> 10;
            case OCEAN -> 0;
            default -> -1;
        };
        if (g < 0) return 0;
        for (int x = -3; x <= s.x() + 2; x++) {
            for (int z = -3; z <= s.z() + 2; z++) {
                for (int y = 0; y <= g; y++) {
                    BlockState st = switch (kind) {
                        case MONOLITH -> y == g ? Blocks.SAND.defaultBlockState() : Blocks.SANDSTONE.defaultBlockState();
                        case OCEAN -> Blocks.SAND.defaultBlockState();
                        default -> y == g ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    };
                    level.setBlock(origin.offset(x, y - 1, z), st, Block.UPDATE_CLIENTS);
                }
                if (kind == RuinKind.OCEAN) {
                    for (int y = 0; y <= 20; y++) level.setBlock(origin.offset(x, y, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        return g;
    }

    /** Air cells reachable by walking/swimming from {@code from} inside the ruin's box (doors and membranes block). */
    private static Set<BlockPos> reach(ServerLevel level, BlockPos from, BlockPos corner, RuinBuilder.Size s) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(from.immutable());
        seen.add(from.immutable());
        while (!q.isEmpty() && seen.size() < 60000) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) continue;
                if (n.getX() < corner.getX() - 2 || n.getZ() < corner.getZ() - 2 || n.getX() > corner.getX() + s.x() + 1
                        || n.getZ() > corner.getZ() + s.z() + 1 || n.getY() < corner.getY() - 2 || n.getY() > corner.getY() + s.y() + 1) continue;
                BlockState st = level.getBlockState(n);
                boolean open = st.isAir() || st.is(Blocks.LADDER) || st.is(Blocks.VINE) || st.is(Blocks.MOSS_CARPET) || st.is(Blocks.COBWEB)
                        || st.is(Blocks.HANGING_ROOTS) || st.is(Blocks.WATER);
                if (!open) continue;
                seen.add(n);
                q.add(n);
            }
        }
        return seen;
    }

    private static void wipe(ServerLevel level, BlockPos origin, RuinBuilder.Size s) {
        for (BlockPos q : BlockPos.betweenClosed(origin.offset(-4, -4, -4), origin.offset(s.x() + 3, s.y() + 12, s.z() + 3))) {
            if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The built ruin as JSON (the format tools/art/specs/structures.py reads). */
    static void dump(ServerLevel level, String name, BlockPos origin, BlockPos lo, BlockPos hi) {
        StringBuilder sb = new StringBuilder("{\"name\":\"").append(name).append("\",\"kind\":\"ruin\",\"blocks\":[");
        boolean first = true;
        for (BlockPos q : BlockPos.betweenClosed(lo, hi)) {
            BlockState st = level.getBlockState(q);
            if (st.isAir()) continue;
            if (!first) sb.append(',');
            first = false;
            var id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
            sb.append('[').append(q.getX() - origin.getX()).append(',').append(q.getY() - origin.getY()).append(',').append(q.getZ() - origin.getZ())
                    .append(",\"").append(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()).append("\",\"");
            boolean firstProp = true;
            for (Property<?> p : st.getProperties()) {
                if (!firstProp) sb.append(',');
                firstProp = false;
                sb.append(p.getName()).append('=').append(value(st, p));
            }
            sb.append("\"]");
        }
        sb.append("]}");
        try {
            Path dir = Path.of("structures");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".json"), sb.toString());
        } catch (IOException e) {
            Awaken.LOG.warn("[above] could not write the ruin dump: {}", e.toString());
        }
    }

    private static <T extends Comparable<T>> String value(BlockState s, Property<T> p) {
        return p.getName(s.getValue(p));
    }

    /**
     * Room between the blue rope sites (SPEC §17.11): on land that takes every spot, no two standing spires within 320, no
     * two platforms within 600, no spire within 250 of a standing platform, and a fair share of each still stands; the rule
     * off keeps every spot; the locator only names spots the rule keeps; a saved chunk's start is read as the save has it.
     */
    static void spacing() {
        AwakenTests.register("ruins_spacing", 200, h -> {
            ServerLevel level = h.getLevel();
            RuinSpacing.Ground every = (k, c) -> Boolean.TRUE;
            for (long seed : new long[] {level.getSeed(), 4242L, 3761469094609221698L}) {
                RuinSpacing.Grids g = RuinSpacing.Grids.of(level.registryAccess(), seed);
                h.assertTrue(g != null, "the spire and sky platform sets spread at random");
                java.util.List<net.minecraft.world.level.ChunkPos> spires = new java.util.ArrayList<>(), skies = new java.util.ArrayList<>();
                int spireSpots = 0, skySpots = 0;
                for (int gx = -14; gx <= 14; gx++)
                    for (int gz = -14; gz <= 14; gz++) {
                        var c = g.spot(RuinKind.SPIRE, gx, gz);
                        spireSpots++;
                        if (Boolean.TRUE.equals(RuinSpacing.rule(RuinKind.SPIRE, c, g, every))) spires.add(c);
                    }
                for (int gx = -8; gx <= 8; gx++)
                    for (int gz = -8; gz <= 8; gz++) {
                        var c = g.spot(RuinKind.SKY, gx, gz);
                        skySpots++;
                        if (Boolean.TRUE.equals(RuinSpacing.rule(RuinKind.SKY, c, g, every))) skies.add(c);
                    }
                for (var a : spires) {
                    for (var b : spires)
                        if (a != b) h.assertTrue(RuinSpacing.distance(a, b) >= RuinSpacing.SPIRE_APART, "two spires " + (int) RuinSpacing.distance(a, b) + " apart (seed " + seed + ")");
                    for (var b : skies)
                        h.assertTrue(RuinSpacing.distance(a, b) >= RuinSpacing.SPIRE_FROM_SKY, "a spire " + (int) RuinSpacing.distance(a, b) + " from a platform (seed " + seed + ")");
                }
                for (var a : skies)
                    for (var b : skies)
                        if (a != b) h.assertTrue(RuinSpacing.distance(a, b) >= RuinSpacing.SKY_APART, "two platforms " + (int) RuinSpacing.distance(a, b) + " apart (seed " + seed + ")");
                double sp = spires.size() / (double) spireSpots, sk = skies.size() / (double) skySpots;
                h.assertTrue(sp > 0.35 && sp < 0.65, "about half the spire spots stand where every spot's land would take one: " + sp);
                h.assertTrue(sk > 0.45 && sk < 0.75, "most sky platform spots stand: " + sk);
                RuinSpacing.set(false);
                try {
                    h.assertTrue(Boolean.TRUE.equals(RuinSpacing.keeps(RuinKind.SPIRE, g.spot(RuinKind.SPIRE, 0, 0), g, every))
                            && Boolean.TRUE.equals(RuinSpacing.keeps(RuinKind.SKY, g.spot(RuinKind.SKY, 0, 0), g, every)), "with the rule off every spot keeps its place");
                } finally {
                    RuinSpacing.set(true);
                }
            }
            // the locator names only spots the rule keeps (in this world, with its own land)
            RuinSpacing.Grids g = RuinSpacing.Grids.of(level.registryAccess(), level.getSeed());
            var structures = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            RuinSpacing.Ground land = (k, c) -> RuinSpacing.validAt(k, c, structures.getValue(k.structure), level.getChunkSource().getGenerator(),
                    level.getChunkSource().randomState(), level, level.uncachedBiomeResolver());
            for (RuinKind kind : java.util.List.of(RuinKind.SPIRE, RuinKind.SKY)) {
                for (var f : RuinLocator.near(level, h.absolutePos(BlockPos.ZERO), 4000, java.util.List.of(kind), null, false, 4, 4000)) {
                    var c = new net.minecraft.world.level.ChunkPos(f.pos().getX() >> 4, f.pos().getZ() >> 4);
                    h.assertTrue(Boolean.TRUE.equals(RuinSpacing.rule(kind, c, g, land)) || RuinLocator.standsFromBefore(level, kind, structures.get(kind.structure).orElseThrow(), c),
                            "the locator named a " + kind.displayName + " the rule passes over at " + c);
                }
            }
            // a saved chunk: a start stands, an INVALID one (a spot that made nothing) doesn't
            String id = RuinKind.SPIRE.structure.identifier().toString();
            var start = new net.minecraft.nbt.CompoundTag();
            start.putString("id", id);
            var starts = new net.minecraft.nbt.CompoundTag();
            starts.put(id, start);
            var structuresTag = new net.minecraft.nbt.CompoundTag();
            structuresTag.put("starts", starts);
            var chunk = new net.minecraft.nbt.CompoundTag();
            chunk.put("structures", structuresTag);
            h.assertTrue(RuinLocator.holdsStart(chunk, id), "a saved start is read");
            start.putString("id", "INVALID");
            h.assertTrue(!RuinLocator.holdsStart(chunk, id), "a saved INVALID start is no start");
            h.assertTrue(!RuinLocator.holdsStart(new net.minecraft.nbt.CompoundTag(), id), "a chunk without starts holds none");
            h.succeed();
        });
    }
}
