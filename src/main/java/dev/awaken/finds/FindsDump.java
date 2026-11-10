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
        // the painted one of each family again, on a hillside: how each meets land that is not level (SPEC §62.3)
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
     * Monuments where a world really put them, on the generator's own ground (SPEC §62.9): what the count stood, drawn
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
