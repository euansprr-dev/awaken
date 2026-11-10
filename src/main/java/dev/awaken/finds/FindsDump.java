package dev.awaken.finds;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes every place's scene as text (its shaped ground, blocks, parts, cameras and creatures, unturned, on flat
 * ground), for {@code tools/content/finds_render.py}: pictures of all thirty in a minute without the game, to look at a
 * build before a tour does (SPEC §59.7). Only when the folder {@code finds-dump} stands in the test server's game folder.
 */
final class FindsDump {
    private FindsDump() {}

    static void write(Path dir) {
        int n = 0;
        for (Kind k : Kinds.places()) {
            Scene sc;
            try {
                sc = Sites.build(Sites.forced(k, 0, 64, 0, 0, 7L), Terrain.flat(64));
            } catch (RuntimeException e) {
                Awaken.LOG.error("[finds] dump: {} doesn't build", k.id(), e);
                continue;
            }
            write(dir, k.id(), k, k.number(), k.name(), sc);
            n++;
        }
        for (Kind k : Kinds.remains()) {
            try {
                write(dir, k.id(), k, 300 + k.number(), k.name(), Sites.build(Sites.forced(k, 0, 64, 0, 0, 7L), Terrain.flat(64)));
                n++;
            } catch (RuntimeException e) {
                Awaken.LOG.error("[finds] dump: {} doesn't build", k.id(), e);
            }
        }
        // the painted one of each family again, on a hillside: how each meets land that is not level (SPEC §64.3)
        for (Kind k : Kinds.monuments()) {
            if (k.tier() != 3) continue;
            try {
                Site site = Sites.forced(k, 0, 64, 0, 0, 11L);
                Scene sc = Sites.build(site, Terrain.hill(64));
                Terrain hill = Terrain.hill(64);
                natural = (x, z) -> hill.floor(x, z) - 64;
                write(dir, k.id() + "_hill", k, 200 + k.number(), k.name() + " on a hill", sc);
                n++;
            } catch (RuntimeException e) {
                Awaken.LOG.error("[finds] dump: {} doesn't build on a hill", k.id(), e);
            } finally {
                natural = null;
            }
        }
        // generators given other numbers (a plan's proofs): the same file, under the proof's name, on the dish's own site
        Kind host = Kinds.byId("listening_dish");
        if (host != null)
            for (var e : dev.awaken.finds.sets.AllSets.proofs().entrySet()) {
                Scene sc = new Scene(Sites.forced(host, 0, 64, 0, 0, 7L), Terrain.flat(64));
                try {
                    e.getValue().accept(sc);
                } catch (RuntimeException ex) {
                    Awaken.LOG.error("[finds] dump: proof {} doesn't build", e.getKey(), ex);
                    continue;
                }
                write(dir, e.getKey(), host, 90 + n, e.getKey().replace('_', ' '), sc);
                n++;
            }
        Awaken.LOG.info("[finds] dump: {} places written to {}", n, dir.toAbsolutePath());
    }

    /** The land a dump is standing on when it is not level (a column's ground, relative to the heart's); null on a level pad. */
    private static java.util.function.IntBinaryOperator natural;
    /** How far past its reach a dump shows the land's own ground, and the water standing on it (an offset to its top, or null). */
    private static int margin = 2;
    private static java.util.function.IntBinaryOperator water;

    /**
     * Monuments where a world really put them, on the generator's own ground (SPEC §64.9): what the count stood, drawn
     * with the land twenty blocks past each one's reach and the water that lies on it, to see how each meets its land
     * without the game. Trees and what else grows are not in it.
     */
    static void real(net.minecraft.server.level.ServerLevel level, Path dir, java.util.List<Site> sites) {
        Terrain t = Terrain.of(level);
        int n = 0;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return;
        }
        for (Site s : sites) {
            try {
                Scene sc = Sites.build(s, t);
                natural = (x, z) -> t.floor(s.x() + x, s.z() + z) - s.y();
                water = (x, z) -> t.surface(s.x() + x, s.z() + z) - s.y();
                margin = 20;
                BlockPos spawn = level.getRespawnData().pos();
                write(dir, String.format("real%02d_%s", n, s.kind().id()), s.kind(), 400 + n, s.kind().name() + ", " + (int) Math.hypot(s.x() - spawn.getX(), s.z() - spawn.getZ())
                        + " from the start, " + s.group() + " rank " + s.rank() + ", at " + s.x() + " " + s.y() + " " + s.z(), sc, s.group());
                n++;
            } catch (RuntimeException e) {
                Awaken.LOG.error("[finds] dump: {} doesn't build where it stands", s.kind().id(), e);
            } finally {
                natural = null;
                water = null;
                margin = 2;
            }
        }
        Awaken.LOG.info("[finds] dump: {} standing monuments written to {}", n, dir.toAbsolutePath());
    }

    /**
     * The same monuments as the world really made them (SPEC §64.9): the chunks each stands in are generated and every
     * block open to the air is written, trees, snow and all, so the drawing is the game's own with no client. It also
     * says what is wrong where one stands: ground its terrace asked for that is not there.
     */
    static void world(net.minecraft.server.level.ServerLevel level, Path dir, java.util.List<Site> sites) {
        Terrain t = Terrain.of(level);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return;
        }
        BlockPos spawn = level.getRespawnData().pos();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
        int n = 0;
        for (Site s : sites) {
            Kind k = s.kind();
            int r = k.reach() + 20;
            long t0 = System.currentTimeMillis();
            for (int cx = (s.x() - r) >> 4; cx <= (s.x() + r) >> 4; cx++)
                for (int cz = (s.z() - r) >> 4; cz <= (s.z() + r) >> 4; cz++) level.getChunk(cx, cz);
            Scene sc = Sites.scene(level, s);
            String file = String.format("world%02d_%s", n, k.id());
            StringBuilder b = new StringBuilder(1 << 22);
            b.append("K ").append(file).append(' ').append(500 + n).append(' ').append(k.reach()).append(' ').append(k.name()).append(", ")
                    .append((int) Math.hypot(s.x() - spawn.getX(), s.z() - spawn.getZ())).append(" from the start, ").append(s.group()).append(" rank ").append(s.rank())
                    .append(", at ").append(s.x()).append(' ').append(s.y()).append(' ').append(s.z()).append(", as the world made it\n");
            b.append("L ").append(s.group()).append('\n').append("W ").append(r).append('\n');
            int holes = 0, bare = 0;
            StringBuilder said = new StringBuilder();
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++) {
                    int wx = s.x() + x, wz = s.z() + z;
                    int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                    int want = s.y() + sc.groundAt(x, z);
                    int low = Math.max(s.y() - 70, Math.min(t.floor(wx, wz), want) - 3);
                    b.append("G ").append(x).append(' ').append(z).append(' ').append(low - s.y()).append('\n');
                    for (int y = top; y > low; y--) {
                        var st = level.getBlockState(m.set(wx, y, wz));
                        if (st.isAir()) continue;
                        boolean open = !st.canOcclude();
                        for (var d : net.minecraft.core.Direction.values()) {
                            if (open) break;
                            open = !level.getBlockState(q.setWithOffset(m, d)).canOcclude();
                        }
                        if (!open) continue;
                        String name = st.toString();
                        b.append("B ").append(x).append(' ').append(y - s.y()).append(' ').append(z).append(' ').append(name, 6, name.length()).append('\n');
                    }
                    // a column the terrace shaped and built nothing on: its ground should be there, in the land's own skin
                    if (!sc.shaped.contains(BlockPos.asLong(x, 0, z)) || sc.blocks().containsKey(BlockPos.asLong(x, want - s.y(), z))
                            || sc.blocks().containsKey(BlockPos.asLong(x, want + 1 - s.y(), z))) continue;
                    // (not a whole block: a plant left standing at the floor falls as soon as the game looks at it)
                    var at = level.getBlockState(m.set(wx, want, wz));
                    if (at.getFluidState().isEmpty() && !at.isCollisionShapeFullBlock(level, m)) {
                        if (holes++ < 10) {
                            said.append("\n    hole at ").append(x).append(' ').append(want - s.y()).append(' ').append(z).append(" (the land stood at ").append(t.floor(wx, wz) - s.y()).append("):");
                            for (int y = want + 2; y >= want - 4; y--) said.append(' ').append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(m.set(wx, y, wz)).getBlock()).getPath());
                        }
                    } else if (at.is(net.minecraft.world.level.block.Blocks.DIRT) && level.getBlockState(m.set(wx, want + 1, wz)).isAir()) bare++;
                }
            try {
                Files.writeString(dir.resolve(file + ".txt"), b);
            } catch (IOException e) {
                Awaken.LOG.error("[finds] dump: {} not written", file, e);
            }
            // Dips: a shaped column whose top whole block lies under its floor and under all four of its neighbours' tops,
            // whoever put it there. (The count of holes passes over what the scene itself wrote at the floor; a moss mat
            // the scene laid in place of the turf made fifty pits in one lawn and none of them was counted.)
            int dips = 0;
            for (long key : sc.shaped) {
                int x = BlockPos.getX(key), z = BlockPos.getZ(key), want = s.y() + sc.groundAt(x, z);
                int mine = solidTop(level, m, s.x() + x, want, s.z() + z);
                if (mine >= want) continue;
                boolean pit = true;
                for (int side = 0; side < 4 && pit; side++) {
                    int ox = side == 0 ? 1 : side == 1 ? -1 : 0, oz = side == 2 ? 1 : side == 3 ? -1 : 0;
                    pit = solidTop(level, m, s.x() + x + ox, s.y() + sc.groundAt(x + ox, z + oz), s.z() + z + oz) > mine;
                }
                if (pit && dips++ < 6) said.append("\n    dip at ").append(x).append(' ').append(mine - s.y()).append(' ').append(z).append(" under a floor at ").append(want - s.y()).append(": ")
                        .append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(m.set(s.x() + x, mine + 1, s.z() + z)).getBlock()).getPath());
            }
            double town = Double.MAX_VALUE, thing = Double.MAX_VALUE;
            for (int[] p : t.starts(s.x() - 600, s.z() - 600, s.x() + 600, s.z() + 600, true)) town = Math.min(town, Math.hypot(p[0] - s.x(), p[1] - s.z()));
            for (int[] p : t.starts(s.x() - 600, s.z() - 600, s.x() + 600, s.z() + 600, false)) thing = Math.min(thing, Math.hypot(p[0] - s.x(), p[1] - s.z()));
            Awaken.LOG.info("[finds] census as made: {} at {} {} {} ({}, reach {}), {} s: {} holes in its ground, {} one-block dips, {} bare earth; nearest settlement {}, nearest other landmark {}{}",
                    k.id(), s.x(), s.y(), s.z(), s.group(), k.reach(), (System.currentTimeMillis() - t0) / 1000, holes, dips, bare,
                    town > 1e9 ? "none in 600" : String.valueOf((int) town), thing > 1e9 ? "none in 600" : String.valueOf((int) thing), said);
            n++;
        }
        Awaken.LOG.info("[finds] dump: {} monuments written as the world made them to {}", n, dir.toAbsolutePath());
    }

    /** The highest whole block of a column at or under two over its floor (looking eight down), for the count of dips. */
    private static int solidTop(net.minecraft.server.level.ServerLevel level, BlockPos.MutableBlockPos m, int x, int floor, int z) {
        for (int y = floor + 2; y > floor - 8; y--) if (level.getBlockState(m.set(x, y, z)).isCollisionShapeFullBlock(level, m)) return y;
        return floor - 8;
    }

    private static void write(Path dir, String file, Kind k, int number, String name, Scene sc) {
        write(dir, file, k, number, name, sc, null);
    }

    private static void write(Path dir, String file, Kind k, int number, String name, Scene sc, dev.awaken.living.region.RegionMap.@org.jspecify.annotations.Nullable Group land) {
        StringBuilder b = new StringBuilder(1 << 18);
        b.append("K ").append(file).append(' ').append(number).append(' ').append(k.reach()).append(' ').append(name).append('\n');
        b.append("L");
        if (land != null) b.append(' ').append(land);
        else for (var g : k.lands()) b.append(' ').append(g);
        b.append('\n');
        if (margin > 2) b.append("W ").append(k.reach() + margin).append('\n');
        for (long key : sc.shaped) {
            int x = BlockPos.getX(key), z = BlockPos.getZ(key);
            b.append("G ").append(x).append(' ').append(z).append(' ').append(sc.groundAt(x, z)).append('\n');
        }
        if (natural != null)                                        // and the land's own ground wherever the scene left it alone
            for (int x = -k.reach() - margin; x <= k.reach() + margin; x++)
                for (int z = -k.reach() - margin; z <= k.reach() + margin; z++) {
                    if (sc.shaped.contains(BlockPos.asLong(x, 0, z))) continue;
                    int g = natural.applyAsInt(x, z);
                    b.append("G ").append(x).append(' ').append(z).append(' ').append(g).append('\n');
                    if (water != null) for (int y = g + 1, top = water.applyAsInt(x, z); y <= top; y++) b.append("B ").append(x).append(' ').append(y).append(' ').append(z).append(" {minecraft:water}\n");
                }
        for (var e : sc.blocks().long2ObjectEntrySet()) {
            long key = e.getLongKey();
            String s = e.getValue().toString(); // Block{minecraft:oak_stairs}[facing=north,...]
            b.append("B ").append(BlockPos.getX(key)).append(' ').append(BlockPos.getY(key)).append(' ').append(BlockPos.getZ(key)).append(' ')
                    .append(s, 6, s.length()).append('\n');
        }
        for (var e : sc.parts().entrySet())
            for (BlockPos p : e.getValue()) b.append("P ").append(e.getKey()).append(' ').append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append('\n');
        for (int[] c : sc.cams()) b.append("C ").append(c[0]).append(' ').append(c[1]).append(' ').append(c[2]).append(' ').append(c[3]).append(' ').append(c[4]).append('\n');
        for (Scene.Spawn s : sc.spawns())
            b.append("S ").append(BuiltInRegistries.ENTITY_TYPE.getKey(s.type())).append(' ').append(s.rel().getX()).append(' ').append(s.rel().getY()).append(' ').append(s.rel().getZ()).append('\n');
        try {
            Files.writeString(dir.resolve(file + ".txt"), b);
        } catch (IOException e) {
            Awaken.LOG.error("[finds] dump: {} not written", file, e);
        }
    }
}
