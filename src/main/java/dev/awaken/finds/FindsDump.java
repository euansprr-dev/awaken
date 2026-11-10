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
            StringBuilder b = new StringBuilder(1 << 18);
            b.append("K ").append(k.id()).append(' ').append(k.number()).append(' ').append(k.reach()).append(' ').append(k.name()).append('\n');
            b.append("L");
            for (var g : k.lands()) b.append(' ').append(g);
            b.append('\n');
            for (long key : sc.shaped) {
                int x = BlockPos.getX(key), z = BlockPos.getZ(key);
                b.append("G ").append(x).append(' ').append(z).append(' ').append(sc.groundAt(x, z)).append('\n');
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
                Files.writeString(dir.resolve(k.id() + ".txt"), b);
                n++;
            } catch (IOException e) {
                Awaken.LOG.error("[finds] dump: {} not written", k.id(), e);
            }
        }
        Awaken.LOG.info("[finds] dump: {} places written to {}", n, dir.toAbsolutePath());
    }
}
