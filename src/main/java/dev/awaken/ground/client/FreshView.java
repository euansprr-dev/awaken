package dev.awaken.ground.client;

import dev.awaken.ground.FreshSides;
import dev.awaken.ground.GroundFresh;
import dev.awaken.ground.SeamlessRules;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The client's copy of the fresh sides (SPEC §31.1) for {@link SeamlessModel}, read on Sodium's meshing threads: per chunk
 * an immutable key-to-stage map, swapped whole when the server's copy arrives (and the sections whose sides changed are
 * redrawn). While the local player mines a block beside grass, the sides it will bare are guessed as fresh for a moment,
 * so the hole is dirt the frame it opens rather than green until the server's word comes.
 */
final class FreshView {
    private FreshView() {}

    /** How long a guess holds without the server's record (client ticks). */
    private static final int GUESS_TICKS = 40;

    private static final ConcurrentHashMap<Long, Int2IntMap> SERVER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Int2LongMap> GUESS = new ConcurrentHashMap<>();
    private static volatile long now;

    static void init() {
        GroundFresh.clientSink = FreshView::arrived;
        NeoForge.EVENT_BUS.addListener(FreshView::mining);
        NeoForge.EVENT_BUS.addListener(FreshView::tick);
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Unload e) -> {
            if (e.getLevel().isClientSide()) {
                SERVER.remove(e.getChunk().getPos().pack());
                GUESS.remove(e.getChunk().getPos().pack());
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e) -> {
            if (e.getLevel().isClientSide()) clear();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    private static void clear() {
        SERVER.clear();
        GUESS.clear();
    }

    /**
     * The stage each side of the block at {@code pos} shows, two bits per side in the order north, east, south, west
     * ({@link FreshSides#NONE} for a side with no record): {@code 0xFF} when none has one. Safe on any thread.
     */
    static int stages(BlockPos pos) {
        if (SERVER.isEmpty() && GUESS.isEmpty()) return 0xFF;
        long c = ChunkPos.pack(pos);
        Int2IntMap s = SERVER.get(c);
        Int2LongMap g = GUESS.get(c);
        if (s == null && g == null) return 0xFF;
        int out = 0;
        long t = now;
        for (int i = 0; i < 4; i++) {
            int key = FreshSides.key(pos, i), stage = FreshSides.NONE;
            if (s != null && s.containsKey(key)) stage = s.get(key);
            else if (g != null && g.get(key) > t) stage = 0;
            out |= stage << (i * 2);
        }
        return out;
    }

    /** The server's records for a chunk arrived (any thread): kept, and the sections whose sides changed redrawn. */
    private static void arrived(ChunkPos chunk, Int2IntMap stages) {
        long c = chunk.pack();
        Int2IntMap before = stages.isEmpty() ? SERVER.remove(c) : SERVER.put(c, stages);
        LongOpenHashSet sections = new LongOpenHashSet();
        for (Int2IntMap.Entry en : stages.int2IntEntrySet()) {
            if (before == null || !before.containsKey(en.getIntKey()) || before.get(en.getIntKey()) != en.getIntValue()) {
                sections.add(SectionPos.asLong(FreshSides.pos(chunk, en.getIntKey())));
            }
        }
        if (before != null) {
            for (int key : before.keySet()) if (!stages.containsKey(key)) sections.add(SectionPos.asLong(FreshSides.pos(chunk, key)));
        }
        if (sections.isEmpty()) return;
        Minecraft.getInstance().execute(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            for (long s : sections) {
                int x = SectionPos.x(s), y = SectionPos.y(s), z = SectionPos.z(s);
                level.setSectionRangeDirty(x, y, z, x, y, z);
            }
        });
    }

    /** The local player mines a block: the grass sides it would bare are fresh until the server says (the block's face is hidden till then). */
    private static void mining(PlayerInteractEvent.LeftClickBlock e) {
        Level level = e.getLevel();
        if (!level.isClientSide()) return;
        BlockPos p = e.getPos();
        BlockState state = level.getBlockState(p);
        if (!state.isSolidRender()) return;
        BlockPos under = p.below();
        if (!level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) return;
        long until = now + GUESS_TICKS;
        for (int i = 0; i < 4; i++) {
            BlockPos g = p.relative(SeamlessRules.SIDES[i]);
            if (!SeamlessRules.wrapperBlock(level.getBlockState(g).getBlock())) continue;
            long c = ChunkPos.pack(g);
            int key = FreshSides.key(g, (i + 2) & 3);
            Int2LongMap old = GUESS.get(c);
            if (old != null && old.get(key) > now + GUESS_TICKS / 2) continue;       // fresh enough: no copy every tick of a long dig
            Int2LongOpenHashMap next = old == null ? new Int2LongOpenHashMap() : new Int2LongOpenHashMap(old);
            next.put(key, until);
            GUESS.put(c, next);
        }
    }

    private static void tick(ClientTickEvent.Post e) {
        long t = ++now;
        if (t % 100 != 0 || GUESS.isEmpty()) return;
        // lapsed guesses go (the block was never broken, or the server's record has long replaced them)
        GUESS.entrySet().removeIf(en -> {
            for (long until : en.getValue().values()) if (until > t) return false;
            return true;
        });
    }
}
