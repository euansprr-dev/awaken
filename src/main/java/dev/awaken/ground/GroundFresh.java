package dev.awaken.ground;

import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Fresh sides (SPEC §31.1): a grass side bared by a hand, a blast or a block set under the open ground beside it shows dirt
 * and grows green in stages, where seamless ground would have wrapped it at once. Recorded when it happens (a player's
 * break, an explosion, a placed block), never by scanning: per chunk in {@link #SIDES}, saved and synced to the players
 * who see the chunk. Every {@link #CHECK} ticks the chunks holding records (a handful, near where someone dug) grow
 * their lit sides; a side that is no longer a riser's face (filled, its grass gone) loses its record, and one fully grown
 * loses it too and is a riser like any other.
 */
public final class GroundFresh {
    private GroundFresh() {}

    /** How often the records grow (ticks). */
    public static final int CHECK = 100;
    /** The light the open block beside a side needs for its grass to grow, as vanilla's grass needs to spread. */
    public static final int LIGHT = 9;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<FreshSides>> SIDES = ATTACHMENTS.register("fresh_sides", () -> AttachmentType.builder(FreshSides::new)
            .serialize(FreshSides.CODEC, f -> !f.isEmpty())
            .sync(new Sync())
            .build());

    /** The client's hook: a chunk's records arrived (its key to stage map, the client's own copy). Set by {@code ground.client.FreshView}. */
    public static volatile @Nullable BiConsumer<ChunkPos, Int2IntMap> clientSink;

    /** Per dimension, the loaded chunks holding records (server thread). */
    private static final Map<ResourceKey<Level>, LongOpenHashSet> ACTIVE = new HashMap<>();

    static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, GroundFresh::broken);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, GroundFresh::placed);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, GroundFresh::blast);
        NeoForge.EVENT_BUS.addListener(GroundFresh::loaded);
        NeoForge.EVENT_BUS.addListener(GroundFresh::unloaded);
        NeoForge.EVENT_BUS.addListener(GroundFresh::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> ACTIVE.clear());
    }

    // ---- what bares a side ------------------------------------------------------------------------------------------------

    private static void broken(BreakBlockEvent e) {
        if (e.isCanceled() || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockState state = e.getState();
        if (SeamlessRules.wrapperBlock(state.getBlock())) forgetAll(level, e.getPos());
        if (state.isSolidRender()) bared(level, e.getPos(), Set.of());
    }

    private static void blast(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        List<BlockPos> gone = e.getAffectedBlocks();
        if (gone.isEmpty()) return;
        Set<BlockPos> set = new HashSet<>(gone);
        for (BlockPos p : gone) {
            if (level.getBlockState(p).isSolidRender()) bared(level, p, set);
        }
    }

    private static void placed(BlockEvent.EntityPlaceEvent e) {
        if (e.isCanceled() || !(e.getLevel() instanceof ServerLevel level)) return;
        BlockPos q = e.getPos();
        BlockState placed = e.getPlacedBlock();
        // filled beside a fresh side: the face is hidden, its record goes
        if (placed.isSolidRender()) {
            for (int i = 0; i < 4; i++) forget(level, q.relative(SeamlessRules.SIDES[i]), opposite(i));
        }
        // set under the open block beside a grass block: a cliff's dirt face is now a riser's, and starts as dirt
        BlockState before = e.getBlockSnapshot().getState();
        if (placed.isFaceSturdy(level, q, Direction.UP) && !before.isFaceSturdy(level, q, Direction.UP) && !level.getBlockState(q.above()).isSolidRender()) {
            for (int i = 0; i < 4; i++) {
                BlockPos g = q.above().relative(SeamlessRules.SIDES[i]);
                if (SeamlessRules.wrapperBlock(level.getBlockState(g).getBlock()) && SeamlessRules.sideWraps(level, g, opposite(i))) cut(level, g, opposite(i));
            }
        }
        // a grass block set down beside open ground: its risers are new too
        if (SeamlessRules.wrapperBlock(placed.getBlock())) {
            for (int i = 0; i < 4; i++) if (SeamlessRules.sideWraps(level, q, i)) cut(level, q, i);
        }
    }

    /**
     * The hider at {@code p} goes (with {@code goneToo}, a blast's other blocks): every grass block beside it whose side
     * toward {@code p} becomes a riser's face (the ground under {@code p} stays and is sturdy) gets a fresh record.
     */
    static void bared(ServerLevel level, BlockPos p, Set<BlockPos> goneToo) {
        BlockPos under = p.below();
        if (goneToo.contains(under) || !level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) return;
        for (int i = 0; i < 4; i++) {
            BlockPos g = p.relative(SeamlessRules.SIDES[i]);
            if (goneToo.contains(g) || !level.isLoaded(g) || !SeamlessRules.wrapperBlock(level.getBlockState(g).getBlock())) continue;
            cut(level, g, opposite(i));
        }
    }

    private static int opposite(int side) {
        return (side + 2) & 3;
    }

    // ---- records ----------------------------------------------------------------------------------------------------------

    /** Records side {@code side} of the grass block at {@code g} as bare dirt (again, if it had grown). */
    public static void cut(ServerLevel level, BlockPos g, int side) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(g.getX() >> 4, g.getZ() >> 4);
        if (chunk == null) return;                        // past the loaded land (a blast's edge): never load a chunk for this
        FreshSides f = chunk.getExistingDataOrNull(SIDES.get());
        int key = FreshSides.key(g, side);
        if (f == null) {
            f = new FreshSides();
            f.sides.put(key, 0);
            chunk.setData(SIDES.get(), f);                // saves and syncs
        } else {
            if (f.sides.containsKey(key) && f.sides.get(key) == 0) return;
            f.sides.put(key, 0);
            chunk.markUnsaved();
            chunk.syncData(SIDES.get());
        }
        ACTIVE.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(chunk.getPos().pack());
    }

    private static void forget(ServerLevel level, BlockPos g, int side) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(g.getX() >> 4, g.getZ() >> 4);
        FreshSides f = chunk == null ? null : chunk.getExistingDataOrNull(SIDES.get());
        if (f == null || !f.sides.containsKey(FreshSides.key(g, side))) return;
        f.sides.remove(FreshSides.key(g, side));
        chunk.markUnsaved();
        chunk.syncData(SIDES.get());
    }

    private static void forgetAll(ServerLevel level, BlockPos g) {
        for (int i = 0; i < 4; i++) forget(level, g, i);
    }

    /** The stage side {@code side} of the block at {@code g} shows: 0 to 2, or {@link FreshSides#NONE} (no record: the rule alone). */
    public static int stage(Level level, BlockPos g, int side) {
        FreshSides f = level.getChunkAt(g).getExistingDataOrNull(SIDES.get());
        if (f == null || !f.sides.containsKey(FreshSides.key(g, side))) return FreshSides.NONE;
        return level.isClientSide() ? f.sides.get(FreshSides.key(g, side)) : FreshSides.stage(f.sides.get(FreshSides.key(g, side)));
    }

    /** The records in a level's loaded chunks (status, tests). */
    public static int count(ServerLevel level) {
        LongOpenHashSet set = ACTIVE.get(level.dimension());
        if (set == null) return 0;
        int n = 0;
        for (long c : set) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
            FreshSides f = chunk == null ? null : chunk.getExistingDataOrNull(SIDES.get());
            if (f != null) n += f.size();
        }
        return n;
    }

    // ---- growing ----------------------------------------------------------------------------------------------------------

    private static void tick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel level && level.getGameTime() % CHECK == 0) grow(level, CHECK);
    }

    /** Grows every record in the level's loaded chunks by {@code ticks} where its side is lit; drops what is no longer a riser's face. */
    public static void grow(ServerLevel level, int ticks) {
        LongOpenHashSet set = ACTIVE.get(level.dimension());
        if (set == null || set.isEmpty()) return;
        LongArrayList done = new LongArrayList();
        IntArrayList drop = new IntArrayList();
        BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
        for (long c : set) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
            FreshSides f = chunk == null ? null : chunk.getExistingDataOrNull(SIDES.get());
            if (f == null || f.isEmpty()) {
                done.add(c);
                continue;
            }
            boolean staged = false;
            drop.clear();
            for (Int2IntMap.Entry en : f.sides.int2IntEntrySet()) {
                int key = en.getIntKey(), grown = en.getIntValue(), side = FreshSides.side(key);
                BlockPos g = FreshSides.pos(chunk.getPos(), key);
                if (!SeamlessRules.wrapperBlock(level.getBlockState(g).getBlock()) || !SeamlessRules.sideWraps(level, g, side)) {
                    drop.add(key);
                    staged = true;
                    continue;
                }
                beside.setWithOffset(g, SeamlessRules.SIDES[side]);
                if (level.getMaxLocalRawBrightness(beside) < LIGHT) continue;      // dark or covered: stays as it is
                int next = grown + ticks;
                if (FreshSides.stage(next) != FreshSides.stage(grown)) staged = true;
                if (next >= FreshSides.WRAPPED) drop.add(key);
                else en.setValue(next);
            }
            for (int k = 0; k < drop.size(); k++) f.sides.remove(drop.getInt(k));
            chunk.markUnsaved();
            if (staged) chunk.syncData(SIDES.get());
            if (f.isEmpty()) done.add(c);
        }
        for (int k = 0; k < done.size(); k++) set.remove(done.getLong(k));
    }

    private static void loaded(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        FreshSides f = e.getChunk().getExistingDataOrNull(SIDES.get());
        if (f != null && !f.isEmpty()) ACTIVE.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(e.getChunk().getPos().pack());
    }

    private static void unloaded(ChunkEvent.Unload e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        LongOpenHashSet set = ACTIVE.get(level.dimension());
        if (set != null) set.remove(e.getChunk().getPos().pack());
    }

    // ---- sync -------------------------------------------------------------------------------------------------------------

    /** Sends each record's stage (not its ticks), so the players' copies change only when a side's look does. */
    private static final class Sync implements AttachmentSyncHandler<FreshSides> {
        @Override
        public void write(RegistryFriendlyByteBuf buf, FreshSides f, boolean initialSync) {
            if (initialSync && f.isEmpty()) return;
            buf.writeVarInt(f.size());
            for (Int2IntMap.Entry en : f.sides.int2IntEntrySet()) {
                buf.writeVarInt(en.getIntKey());
                buf.writeByte(FreshSides.stage(en.getIntValue()));
            }
        }

        @Override
        public FreshSides read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable FreshSides previous) {
            FreshSides f = new FreshSides();
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                int key = buf.readVarInt();
                f.sides.put(key, buf.readByte());
            }
            BiConsumer<ChunkPos, Int2IntMap> sink = clientSink;
            if (sink != null && holder instanceof LevelChunk chunk) sink.accept(chunk.getPos(), new Int2IntOpenHashMap(f.sides));
            return f;
        }
    }
}
