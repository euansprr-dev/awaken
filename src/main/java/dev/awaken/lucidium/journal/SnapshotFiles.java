package dev.awaken.lucidium.journal;

import dev.awaken.Awaken;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.plan.Fragment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Day Journal files: vanilla structure templates in {@code <world>/awaken/lucidium/<player>/<id>.nbt} (gzipped,
 * openable with a structure block). Capture runs on the server thread (a 16x12x16 read); writing and reading run on
 * the IO pool.
 */
public final class SnapshotFiles {
    private SnapshotFiles() {}

    public static Path folder(MinecraftServer server, UUID player) {
        return server.getWorldPath(LevelResource.ROOT).resolve("awaken").resolve("lucidium").resolve(player.toString());
    }

    public static Path file(MinecraftServer server, UUID player, String id) {
        return folder(server, player).resolve(id + ".nbt");
    }

    /** Reads the place around {@code feet} into a template tag. Block entity data (chest contents...) is stripped. */
    public static CompoundTag capture(ServerLevel level, BlockPos feet) {
        StructureTemplate t = new StructureTemplate();
        BlockPos corner = feet.offset(-Snapshot.SX / 2, -Snapshot.FLOOR, -Snapshot.SZ / 2);
        t.fillFromWorld(level, corner, new Vec3i(Snapshot.SX, Snapshot.SY, Snapshot.SZ), false,
                List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR, Blocks.STRUCTURE_VOID, Blocks.BARRIER, Blocks.LIGHT));
        t.setAuthor("awaken:lucidium");
        CompoundTag tag = t.save(new CompoundTag());
        ListTag blocks = tag.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) blocks.getCompoundOrEmpty(i).remove("nbt");
        tag.put("entities", new ListTag());
        return tag;
    }

    public static CompletableFuture<Void> write(MinecraftServer server, UUID player, String id, CompoundTag tag) {
        Path file = file(server, player, id);
        return CompletableFuture.runAsync(() -> writeNow(file, tag), Util.ioPool());
    }

    public static void writeNow(Path file, CompoundTag tag) {
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file);
        } catch (IOException e) {
            Awaken.LOG.warn("Lucidium: could not save snapshot {}", file, e);
        }
    }

    public static void delete(MinecraftServer server, UUID player, List<String> ids) {
        if (ids.isEmpty()) return;
        List<Path> files = ids.stream().map(id -> file(server, player, id)).toList();
        Util.ioPool().execute(() -> {
            for (Path p : files) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    Awaken.LOG.warn("Lucidium: could not delete snapshot {}", p, e);
                }
            }
        });
    }

    /** Loads a snapshot as a fragment (any thread). Empty if the file is gone or unreadable. */
    public static Optional<Fragment> load(Path file) {
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.create(16L * 1024 * 1024));
            return Optional.of(Fragment.fromTemplate(BuiltInRegistries.BLOCK, tag));
        } catch (Exception e) {
            Awaken.LOG.warn("Lucidium: could not read snapshot {}", file, e);
            return Optional.empty();
        }
    }
}
