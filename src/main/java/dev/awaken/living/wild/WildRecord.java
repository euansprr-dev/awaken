package dev.awaken.living.wild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.living.api.Wildness;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Each chunk's wildness, kept (SPEC §41.1): sixteen cells of 4 by 4 blocks, worked out once from the land and saved with
 * the chunk, so the value never shifts under a player as roads and settlements are found later. Chunks without one (new,
 * or from before the Wild Lands) are queued as they load and filled a few a tick, within {@link #FILL_BUDGET_NS}; a read
 * of one not filled yet fills it at once. Synced to the players who see the chunk (sixteen bytes), for the client's looks.
 */
public final class WildRecord {
    private WildRecord() {}

    /** Chunks filled per tick per level from the queue, at most, and the time the filling may take a tick (a chunk is ~0.2 ms). */
    public static final int FILL_PER_TICK = 8;
    public static final long FILL_BUDGET_NS = 400_000;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<Cells>> WILD = ATTACHMENTS.register("wild", () -> AttachmentType.builder(Cells::new)
            .serialize(Cells.CODEC)
            .sync(new Sync())
            .build());

    /** Per level, the loaded chunks still to fill (server thread). */
    private static final Map<ResourceKey<Level>, LongArrayFIFOQueue> PENDING = new HashMap<>();

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(WildRecord::loaded);
        NeoForge.EVENT_BUS.addListener(WildRecord::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> PENDING.clear());
    }

    /** A chunk's cells: filled from the land on first need. */
    public static Cells of(ServerLevel level, LevelChunk chunk) {
        Cells c = chunk.getExistingDataOrNull(WILD.get());
        return c != null ? c : fill(level, chunk);
    }

    /** The chunk's cells if it has them yet (either side), else null. */
    public static @Nullable Cells existing(LevelChunk chunk) {
        return chunk.getExistingDataOrNull(WILD.get());
    }

    /** Works out the chunk's cells from the land (each cell's centre) and keeps them. */
    public static Cells fill(ServerLevel level, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        Cells c = new Cells();
        for (int i = 0; i < 16; i++) {
            int x = cp.getMinBlockX() + (i & 3) * 4 + 2, z = cp.getMinBlockZ() + (i >> 2) * 4 + 2;
            c.cells[i] = (byte) Math.round(Wildness.land(level, x, z) * 255);
        }
        chunk.setData(WILD.get(), c); // saves and syncs
        return c;
    }

    private static void loaded(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !Wildness.on(level)) return;
        if (e.getChunk().getExistingDataOrNull(WILD.get()) != null) return;
        PENDING.computeIfAbsent(level.dimension(), k -> new LongArrayFIFOQueue()).enqueue(e.getChunk().getPos().pack());
    }

    private static void tick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        LongArrayFIFOQueue q = PENDING.get(level.dimension());
        long t0 = System.nanoTime();
        for (int n = 0; q != null && !q.isEmpty() && n < FILL_PER_TICK && System.nanoTime() - t0 < FILL_BUDGET_NS; ) {
            long c = q.dequeueLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
            if (chunk == null || chunk.getExistingDataOrNull(WILD.get()) != null) continue; // gone, or read (and filled) since
            fill(level, chunk);
            n++;
        }
    }

    /** Chunks waiting to be filled in a level (status, tests). */
    public static int pending(ServerLevel level) {
        LongArrayFIFOQueue q = PENDING.get(level.dimension());
        return q == null ? 0 : q.size();
    }

    /** Sixteen cells, 4 by 4 blocks each, row by row from the chunk's north-west corner; each 0..255 for 0..1. */
    public static final class Cells {
        public static final int SCHEMA = 1;
        final byte[] cells = new byte[16];

        public double at(int x, int z) {
            return cell(((z & 15) >> 2) * 4 + ((x & 15) >> 2));
        }

        public double cell(int i) {
            return (cells[i] & 0xFF) / 255.0;
        }

        public static final MapCodec<Cells> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(c -> SCHEMA),
                Codec.INT.listOf().fieldOf("cells").forGetter(Cells::list))
                .apply(i, (v, list) -> {
                    Cells c = new Cells();
                    for (int k = 0; k < Math.min(16, list.size()); k++) c.cells[k] = (byte) (int) list.get(k);
                    return c;
                }));

        private List<Integer> list() {
            List<Integer> out = new ArrayList<>(16);
            for (byte b : cells) out.add(b & 0xFF);
            return out;
        }
    }

    static final class Sync implements AttachmentSyncHandler<Cells> {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Cells c, boolean initialSync) {
            buf.writeBytes(c.cells);
        }

        @Override
        public Cells read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable Cells previous) {
            Cells c = new Cells();
            buf.readBytes(c.cells);
            return c;
        }
    }
}
