package dev.awaken.delve;

import dev.awaken.delve.api.Lodes;
import dev.awaken.progression.Progression;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delver lamps (SPEC §34.6): strike a dead lamp with a pick and it rings; light floods the cavern from it as a wave (16
 * blocks a second, out to {@value #REACH}), and where the wave reached the server leaves invisible light
 * ({@code minecraft:light}, level {@value #LEVEL}) on a {@value #LATTICE}-block lattice through the air (one per lattice
 * cube, the cell nearest its middle; at most {@value #MAX_LIGHTS}, nearest the lamp first). The hall stays lit for
 * everyone, forever: no monster spawns in it. It's named, pinned on the Atlas, and its lights are kept per lamp in
 * {@link DelveData}, so breaking the lamp takes them back. The flood and the placing are spread over ticks (at most
 * {@value #BUDGET_NANOS} ns a tick between them); a chunk not loaded is a wall to it. One lamp, one lattice: a second
 * strike while it floods does nothing.
 */
public final class Lamps {
    private Lamps() {}

    public static final int REACH = 40, LATTICE = 6, MAX_LIGHTS = 120, LEVEL = 11, MAX_CELLS = 24_000;
    /** The wave's speed: blocks a tick (16 a second). */
    public static final float WAVE = 0.8F;
    static final long BUDGET_NANOS = 1_000_000L;

    private static final class Job {
        final ResourceKey<Level> dim;
        final BlockPos lamp;
        final @Nullable UUID by;
        final String name;
        final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        final LongOpenHashSet seen = new LongOpenHashSet();
        /** Lattice cube → its best cell so far. */
        final Long2LongOpenHashMap best = new Long2LongOpenHashMap();
        long[] lights;
        float[] dist;
        final LongArrayList placed = new LongArrayList();
        int next;
        long waveStart = -1;

        Job(ResourceKey<Level> dim, BlockPos lamp, @Nullable UUID by, String name) {
            this.dim = dim;
            this.lamp = lamp;
            this.by = by;
            this.name = name;
        }
    }

    private static final Map<Long, Job> JOBS = new HashMap<>();

    private static long key(ResourceKey<Level> dim, BlockPos pos) {
        return pos.asLong() * 31 + dim.identifier().hashCode();
    }

    /** A pick struck a dead lamp: it lights (the event's left-click doesn't start breaking it). */
    static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        BlockState s = e.getLevel().getBlockState(e.getPos());
        if (!s.is(DelveContent.DELVER_LAMP.get()) || s.getValue(DelverLampBlock.LIT) || !e.getItemStack().is(ItemTags.PICKAXES)) return;
        e.setCanceled(true);
        if (e.getEntity() instanceof ServerPlayer p) strike(p.level(), p, e.getPos());
    }

    /** Lights the lamp at {@code pos} (struck by {@code by}, or by a test); false when it's lit, lighting, or no lamp. */
    public static boolean strike(ServerLevel level, @Nullable ServerPlayer by, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(DelveContent.DELVER_LAMP.get()) || s.getValue(DelverLampBlock.LIT)) return false;
        long k = key(level.dimension(), pos);
        if (JOBS.containsKey(k)) return false;
        level.setBlock(pos, s.setValue(DelverLampBlock.LIT, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.4F, 0.7F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.6F, 0.5F);
        Job j = new Job(level.dimension(), pos.immutable(), by == null ? null : by.getUUID(), name(level, pos));
        for (Direction d : Direction.values()) {
            long c = pos.relative(d).asLong();
            if (j.seen.add(c)) j.queue.enqueue(c);
        }
        JOBS.put(k, j);
        DelveData.get(level).putLamp(new DelveData.Lamp(pos.asLong(), List.of(), j.name));
        if (by != null) PickVerses.deed(by, PickVerses.Verse.ECHO);
        return true;
    }

    /** The hall's name: a cave place's own, else by the nearest lode's ore, else by its depth. */
    static String name(ServerLevel level, BlockPos pos) {
        String place = dev.awaken.delve.cave.CavePlaces.placeName(level, pos);
        if (place != null) return place.toUpperCase(java.util.Locale.ROOT);
        Lodes.View best = null;
        for (Ore o : Ore.values()) {
            var v = Lodes.nearest(level, pos, o, 32);
            if (v.isPresent() && (best == null || v.get().distance() < best.distance())) best = v.get();
        }
        if (best != null) return "THE " + best.ore().word().toUpperCase(java.util.Locale.ROOT) + " HALL";
        String[] names = pos.getY() < 0 ? new String[]{"THE DEEP HALL", "THE DARK HALL", "THE STILL HALL"}
                : new String[]{"THE LONG HALL", "THE QUIET HALL", "THE DRIPPING HALL", "THE WIDE HALL", "THE OLD HALL"};
        return names[Math.floorMod((int) (pos.asLong() ^ pos.asLong() >>> 29), names.length)];
    }

    // ---- the flood and the wave ---------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        long until = System.nanoTime() + BUDGET_NANOS;
        for (Iterator<Job> it = JOBS.values().iterator(); it.hasNext(); ) {
            Job j = it.next();
            ServerLevel level = server.getLevel(j.dim);
            if (level == null || !level.getBlockState(j.lamp).is(DelveContent.DELVER_LAMP.get())) {
                it.remove();
                continue;
            }
            if (j.lights == null) flood(level, j, until);
            if (j.lights != null && place(level, j)) it.remove();
            if (System.nanoTime() > until) break;
        }
    }

    /** Walks the air from the lamp until the budget runs out; when the flood is done, picks the lattice. */
    private static void flood(ServerLevel level, Job j, long until) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int lx = j.lamp.getX(), ly = j.lamp.getY(), lz = j.lamp.getZ();
        int walked = 0;
        while (!j.queue.isEmpty() && j.seen.size() < MAX_CELLS) {
            if ((++walked & 63) == 0 && System.nanoTime() > until) return;
            long c = j.queue.dequeueLong();
            m.set(c);
            if (!level.isLoaded(m) || !level.getBlockState(m).isAir()) continue;
            int x = m.getX(), y = m.getY(), z = m.getZ();
            // the cell nearest its lattice cube's middle keeps the cube's light
            long cube = BlockPos.asLong(Math.floorDiv(x, LATTICE), Math.floorDiv(y, LATTICE), Math.floorDiv(z, LATTICE));
            long had = j.best.getOrDefault(cube, Long.MIN_VALUE);
            if (had == Long.MIN_VALUE || middle(c) < middle(had)) j.best.put(cube, c);
            for (Direction d : Direction.values()) {
                long n = BlockPos.offset(c, d);
                int dx = BlockPos.getX(n) - lx, dy = BlockPos.getY(n) - ly, dz = BlockPos.getZ(n) - lz;
                if (dx * dx + dy * dy + dz * dz > REACH * REACH || level.isOutsideBuildHeight(BlockPos.getY(n)) || !j.seen.add(n)) continue;
                j.queue.enqueue(n);
            }
        }
        // done: the lattice, nearest the lamp first, at most MAX_LIGHTS
        List<long[]> cells = new ArrayList<>();
        for (long c : j.best.values()) cells.add(new long[]{c, (long) BlockPos.of(c).distSqr(j.lamp)});
        cells.sort((a, b) -> Long.compare(a[1], b[1]));
        int n = Math.min(cells.size(), MAX_LIGHTS);
        j.lights = new long[n];
        j.dist = new float[n];
        for (int i = 0; i < n; i++) {
            j.lights[i] = cells.get(i)[0];
            j.dist[i] = (float) Math.sqrt(cells.get(i)[1]);
        }
        j.waveStart = level.getGameTime();
        j.seen.clear();
        j.best.clear();
        var lit = new DelveNet.LampLit(j.lamp.asLong(), j.lights, j.name);
        DelveNet.near(level, null, j.lamp.getX() + 0.5, j.lamp.getY() + 0.5, j.lamp.getZ() + 0.5, REACH + 24, lit);
        Component caption = Component.literal(j.name + " · lamp lit").withColor(0xFFE2B0);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(j.lamp) <= (double) (REACH + 8) * (REACH + 8)) p.sendOverlayMessage(caption);
        }
    }

    /** How far a cell is from its lattice cube's middle (squared, in half-blocks). */
    private static int middle(long c) {
        int x = BlockPos.getX(c), y = BlockPos.getY(c), z = BlockPos.getZ(c);
        int mx = Math.floorMod(x, LATTICE) * 2 - (LATTICE - 1), my = Math.floorMod(y, LATTICE) * 2 - (LATTICE - 1), mz = Math.floorMod(z, LATTICE) * 2 - (LATTICE - 1);
        return mx * mx + my * my + mz * mz;
    }

    /** Sets the lights the wave has reached by now; true when all are set (the lamp is saved with them as they go). */
    private static boolean place(ServerLevel level, Job j) {
        float front = (level.getGameTime() - j.waveStart + 1) * WAVE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LEVEL);
        boolean any = false;
        while (j.next < j.lights.length && j.dist[j.next] <= front) {
            m.set(j.lights[j.next++]);
            if (!level.isLoaded(m) || !level.getBlockState(m).isAir()) continue;
            level.setBlock(m, light, Block.UPDATE_ALL);
            j.placed.add(m.asLong());
            any = true;
        }
        if (any || j.next >= j.lights.length) DelveData.get(level).putLamp(new DelveData.Lamp(j.lamp.asLong(), List.copyOf(j.placed), j.name));
        if (j.next < j.lights.length) return false;
        ServerPlayer p = j.by == null ? null : level.getServer().getPlayerList().getPlayer(j.by);
        if (p != null) Progression.count(p, "awaken:delve/lamps_lit");
        sync(level);
        return true;
    }

    /** The lamp at {@code pos} is gone: its lights go with it (only blocks that are still its light). */
    static void removed(ServerLevel level, BlockPos pos) {
        JOBS.remove(key(level.dimension(), pos));
        DelveData.Lamp l = DelveData.get(level).removeLamp(pos.asLong());
        if (l == null) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long c : l.lights()) {
            m.set(c);
            if (level.isLoaded(m) && level.getBlockState(m).is(Blocks.LIGHT)) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        sync(level);
    }

    /** Whether a lamp is still flooding or placing (tests wait on it). */
    public static boolean busy(ServerLevel level, BlockPos pos) {
        return JOBS.containsKey(key(level.dimension(), pos));
    }

    // ---- the Atlas ------------------------------------------------------------------------------------------------------

    /** The lit lamps of the level, to everyone in it (for the Atlas's pins). */
    static void sync(ServerLevel level) {
        DelveNet.LampsKnown m = known(level);
        for (ServerPlayer p : level.players()) DelveNet.send(p, m);
    }

    private static DelveNet.LampsKnown known(ServerLevel level) {
        List<DelveData.Lamp> lit = new ArrayList<>();
        for (DelveData.Lamp l : DelveData.get(level).lamps()) if (!l.lights().isEmpty()) lit.add(l);
        int n = Math.min(lit.size(), 512);
        long[] at = new long[n];
        List<String> names = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            at[i] = lit.get(i).pos();
            names.add(lit.get(i).name());
        }
        return new DelveNet.LampsKnown(at, names);
    }

    static void onJoin(ServerPlayer p) {
        DelveNet.send(p, known(p.level()));
    }

    static void clear() {
        JOBS.clear();
    }
}
