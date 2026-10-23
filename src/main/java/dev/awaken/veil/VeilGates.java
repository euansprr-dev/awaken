package dev.awaken.veil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.veil.block.GhostDoorBlock;
import dev.awaken.veil.block.GhostLeverBlock;
import dev.awaken.veil.block.VeilGlyphBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Veil Gates: spirit-only rifts that open near some ancient structures (a hidden, unopened loot chest in one of every
 * three 64-block cells). Entering one drops the spirit into a small pocket in the Stillpoint built of Veil stone, where
 * only spirit matter is real: a lights-out lever puzzle opens the first door, a held breath (a lever that keeps the second
 * door open for five seconds) opens the next, and the cache at the end pays once per player (+1 SPI, a secret, some XP).
 */
public final class VeilGates {
    private VeilGates() {}

    // ---- saved rifts ------------------------------------------------------------------------------------------------

    public record Rift(String key, ResourceKey<Level> dim, BlockPos pos, int index, long seed) {
        static final Codec<Rift> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Rift::key),
                Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(Rift::dim),
                BlockPos.CODEC.fieldOf("pos").forGetter(Rift::pos),
                Codec.INT.fieldOf("index").forGetter(Rift::index),
                Codec.LONG.fieldOf("seed").forGetter(Rift::seed)
        ).apply(i, Rift::new));

        Rift withIndex(int index) {
            return new Rift(key, dim, pos, index, seed);
        }
    }

    public static final class Store extends SavedData {
        public static final int VERSION = 1;
        final Map<String, Rift> rifts = new LinkedHashMap<>();
        final Map<Integer, String> byIndex = new HashMap<>();
        final List<Integer> built = new ArrayList<>();
        int nextIndex;

        public Store() {}

        private Store(int version, List<Rift> list, List<Integer> built, int nextIndex) {
            for (Rift r : list) {
                rifts.put(r.key(), r);
                if (r.index() >= 0) byIndex.put(r.index(), r.key());
            }
            this.built.addAll(built);
            this.nextIndex = nextIndex;
        }

        static final Codec<Store> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schema_version", VERSION).forGetter(s -> VERSION),
                Rift.CODEC.listOf().fieldOf("rifts").forGetter(s -> List.copyOf(s.rifts.values())),
                Codec.INT.listOf().optionalFieldOf("built", List.of()).forGetter(s -> List.copyOf(s.built)),
                Codec.INT.optionalFieldOf("next_index", 0).forGetter(s -> s.nextIndex)
        ).apply(i, Store::new));

        static final SavedDataType<Store> TYPE = new SavedDataType<>(Awaken.id("veil_rifts"), Store::new, CODEC);

        static Store get(MinecraftServer server) {
            return server.getDataStorage().computeIfAbsent(TYPE);
        }
    }

    // ---- where rifts are --------------------------------------------------------------------------------------------

    static String keyFor(ResourceKey<Level> dim, int cx, int cz) {
        return dim.identifier() + "/" + cx + "," + cz;
    }

    /** Whether the 64-block cell holding this chest opens a rift (one in three, fixed by the world seed). */
    public static boolean isRiftCell(long seed, int cx, int cz) {
        return Math.floorMod(SpiritPlacer.mix(seed, cx, cz, 7), 3L) == 0L;
    }

    /** A hidden structure chest was seen by spirit sight: its cell may hold a rift. */
    static void considerRift(ServerLevel level, BlockPos chest) {
        if (level.dimension() == Veil.STILLPOINT) return;
        String dimId = level.dimension().identifier().toString();
        if (dimId.equals("awaken:gates")) return;
        int cx = chest.getX() >> 6, cz = chest.getZ() >> 6;
        if (!isRiftCell(level.getSeed(), cx, cz)) return;
        Store store = Store.get(level.getServer());
        String key = keyFor(level.dimension(), cx, cz);
        if (store.rifts.containsKey(key)) return;
        store.rifts.put(key, new Rift(key, level.dimension(), chest.above(2), -1, SpiritPlacer.mix(level.getSeed(), cx, cz, 11)));
        store.setDirty();
    }

    static long[] riftsFor(ServerPlayer sp, ServerLevel level, Vec3 center, int radius) {
        Store store = Store.get(level.getServer());
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        double r2 = (double) radius * radius;
        List<Long> out = new ArrayList<>();
        for (Rift r : store.rifts.values()) {
            if (r.dim() != level.dimension() || mem.riftsCleared.contains(r.key())) continue;
            if (r.pos().distToCenterSqr(center) <= r2) out.add(r.pos().asLong());
            if (out.size() >= 16) break;
        }
        long[] a = new long[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    // ---- pockets ----------------------------------------------------------------------------------------------------

    static final int POCKET_X0 = 8192, POCKET_Z0 = 8192, POCKET_Y = 160, SPACING = 64, PER_ROW = 32;
    static final int LENGTH = 35, HEIGHT = 9, WIDTH = 13;
    static final int BREATH_TICKS = 100;

    /** The fixed layout of pocket {@code index}, from its seed. */
    public record Layout(BlockPos origin, Vec3 arrival, List<BlockPos> levers, List<BlockPos> glyphs, boolean[] target, boolean[] coupled,
                         BlockPos door1, BlockPos door2, BlockPos breathLever, BlockPos cache) {
        public static Layout of(int index, long seed) {
            BlockPos o = new BlockPos(POCKET_X0 + (index % PER_ROW) * SPACING, POCKET_Y, POCKET_Z0 + (index / PER_ROW) * SPACING);
            int n = 4 + (int) (seed & 1L);
            List<BlockPos> levers = new ArrayList<>(), glyphs = new ArrayList<>();
            int start = n == 4 ? 3 : 2;
            boolean[] target = new boolean[n], coupled = new boolean[n];
            long bits = seed >>> 8;
            boolean any = false;
            for (int i = 0; i < n; i++) {
                int z = start + 2 * i;
                levers.add(o.offset(9, 1, z));
                glyphs.add(o.offset(12, 5, z));
                target[i] = ((bits >>> i) & 1L) != 0;
                any |= target[i];
                coupled[i] = i < n - 1 && ((bits >>> (16 + i)) & 1L) != 0;
            }
            if (!any) target[n - 1] = true;
            boolean anyCoupling = false;
            for (boolean c : coupled) anyCoupling |= c;
            if (!anyCoupling) coupled[0] = true; // always a real puzzle: at least one lever moves a neighbour
            return new Layout(o, Vec3.atBottomCenterOf(o.offset(4, 1, 6)), List.copyOf(levers), List.copyOf(glyphs), target, coupled,
                    o.offset(12, 1, 5), o.offset(24, 1, 5), o.offset(14, 1, 6), o.offset(30, 1, 6));
        }

        boolean contains(BlockPos p) {
            int dx = p.getX() - origin.getX(), dy = p.getY() - origin.getY(), dz = p.getZ() - origin.getZ();
            return dx >= 0 && dx < LENGTH && dy >= 0 && dy < HEIGHT && dz >= 0 && dz < WIDTH;
        }
    }

    static @Nullable Layout layoutAt(MinecraftServer server, BlockPos p) {
        int ix = Math.floorDiv(p.getX() - POCKET_X0, SPACING), iz = Math.floorDiv(p.getZ() - POCKET_Z0, SPACING);
        if (ix < 0 || ix >= PER_ROW || iz < 0) return null;
        int index = iz * PER_ROW + ix;
        Store store = Store.get(server);
        String key = store.byIndex.get(index);
        Rift r = key == null ? null : store.rifts.get(key);
        if (r == null) return null;
        Layout l = Layout.of(index, r.seed());
        return l.contains(p) ? l : null;
    }

    static void build(ServerLevel level, Layout l) {
        BlockPos o = l.origin();
        for (int cx = o.getX() >> 4; cx <= (o.getX() + LENGTH) >> 4; cx++) {
            for (int cz = o.getZ() >> 4; cz <= (o.getZ() + WIDTH) >> 4; cz++) level.getChunk(cx, cz);
        }
        BlockState stone = VeilContent.VEIL_STONE.get().defaultBlockState();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < LENGTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < WIDTH; z++) {
                    boolean shell = x == 0 || x == LENGTH - 1 || y == 0 || y == HEIGHT - 1 || z == 0 || z == WIDTH - 1;
                    boolean wall = (x == 12 || x == 24) && !(z >= 5 && z <= 7 && y >= 1 && y <= 3);
                    m.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                    level.setBlock(m, shell || wall ? stone : Blocks.AIR.defaultBlockState(), flags);
                }
            }
        }
        // doors: 3 wide, 3 high, locked (only the puzzle opens them)
        BlockState door = VeilContent.GHOST_DOOR.get().defaultBlockState().setValue(GhostDoorBlock.LOCKED, true);
        for (int dx : new int[]{12, 24}) {
            for (int y = 1; y <= 3; y++) for (int z = 5; z <= 7; z++) level.setBlock(o.offset(dx, y, z), door, flags);
        }
        // glyphs show the pattern; levers sit under them
        for (int i = 0; i < l.levers().size(); i++) {
            level.setBlock(l.glyphs().get(i), VeilContent.VEIL_GLYPH.get().defaultBlockState().setValue(VeilGlyphBlock.LIT, l.target()[i]), flags);
            level.setBlock(l.levers().get(i), lever(false), flags);
        }
        level.setBlock(l.breathLever(), lever(false), flags);
        // a few lights along the corridor ceiling and in the vault
        for (int x : new int[]{16, 20, 28, 32}) level.setBlock(o.offset(x, HEIGHT - 1, 6), VeilContent.VEIL_GLYPH.get().defaultBlockState()
                .setValue(VeilGlyphBlock.LIT, true), flags);
        level.setBlock(l.cache(), VeilContent.VEIL_CACHE.get().defaultBlockState(), flags);
    }

    private static BlockState lever(boolean on) {
        return VeilContent.GHOST_LEVER.get().defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
                .setValue(LeverBlock.FACING, Direction.EAST).setValue(GhostLeverBlock.POWERED, on);
    }

    /** Puts the puzzle back the way it started (nobody else is inside). */
    static void reset(ServerLevel level, Layout l) {
        int flags = Block.UPDATE_CLIENTS;
        for (BlockPos p : l.levers()) if (level.getBlockState(p).getBlock() instanceof GhostLeverBlock) level.setBlock(p, lever(false), flags);
        if (level.getBlockState(l.breathLever()).getBlock() instanceof GhostLeverBlock) level.setBlock(l.breathLever(), lever(false), flags);
        GhostDoorBlock.setPane(level, l.door1(), false);
        GhostDoorBlock.setPane(level, l.door2(), false);
        BREATH.remove(l.origin().asLong());
    }

    /** Pocket origin -> game time when the second door closes again. */
    private static final Map<Long, Long> BREATH = new HashMap<>();

    /** A ghost lever moved. In a pocket: apply the coupling, check the pattern, or hold the breath door. */
    public static void onLever(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Veil.STILLPOINT) return;
        Layout l = layoutAt(level.getServer(), pos);
        if (l == null) return;
        int i = l.levers().indexOf(pos);
        if (i >= 0) {
            if (l.coupled()[i] && i + 1 < l.levers().size()) {
                BlockPos n = l.levers().get(i + 1);
                BlockState ns = level.getBlockState(n);
                if (ns.getBlock() instanceof GhostLeverBlock gl) gl.pull(ns, level, n, null);
            }
            boolean solved = true;
            for (int k = 0; k < l.levers().size(); k++) {
                BlockState s = level.getBlockState(l.levers().get(k));
                if (!(s.getBlock() instanceof GhostLeverBlock) || s.getValue(GhostLeverBlock.POWERED) != l.target()[k]) solved = false;
            }
            boolean wasOpen = level.getBlockState(l.door1()).getBlock() instanceof GhostDoorBlock && level.getBlockState(l.door1()).getValue(GhostDoorBlock.OPEN);
            if (solved != wasOpen) {
                GhostDoorBlock.setPane(level, l.door1(), solved);
                if (solved) level.playSound(null, l.door1(), VeilContent.BELL.get(), SoundSource.BLOCKS, 0.8F, 1.2F);
            }
        } else if (pos.equals(l.breathLever())) {
            BlockState s = level.getBlockState(pos);
            if (s.getBlock() instanceof GhostLeverBlock && s.getValue(GhostLeverBlock.POWERED)) {
                GhostDoorBlock.setPane(level, l.door2(), true);
                BREATH.put(l.origin().asLong(), level.getGameTime() + BREATH_TICKS);
            }
        }
    }

    /** Closes breath doors whose time is up (server tick). */
    static void serverTick(MinecraftServer server) {
        if (BREATH.isEmpty()) return;
        ServerLevel level = server.getLevel(Veil.STILLPOINT);
        if (level == null) {
            BREATH.clear();
            return;
        }
        long now = level.getGameTime();
        BREATH.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            BlockPos origin = BlockPos.of(e.getKey());
            Layout l = layoutAt(server, origin);
            if (l != null) {
                GhostDoorBlock.setPane(level, l.door2(), false);
                BlockState s = level.getBlockState(l.breathLever());
                if (s.getBlock() instanceof GhostLeverBlock && s.getValue(GhostLeverBlock.POWERED)) level.setBlock(l.breathLever(), lever(false), Block.UPDATE_ALL);
            }
            return true;
        });
    }

    // ---- entering and leaving ---------------------------------------------------------------------------------------

    static void tick(ServerPlayer sp, Projection.Spirit s) {
        if (s.pocket != null || s.riftCooldown > 0 || s.age % 5 != 0 || s.possessed != null) return;
        ServerLevel level = sp.level();
        Store store = Store.get(level.getServer());
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        Vec3 c = sp.position().add(0, 0.9, 0);
        for (Rift r : store.rifts.values()) {
            if (r.dim() != level.dimension() || mem.riftsCleared.contains(r.key())) continue;
            if (Vec3.atCenterOf(r.pos()).distanceToSqr(c) <= 1.6 * 1.6) {
                enter(sp, s, r);
                return;
            }
        }
    }

    static void enter(ServerPlayer sp, Projection.Spirit s, Rift rift) {
        MinecraftServer server = sp.level().getServer();
        ServerLevel still = server.getLevel(Veil.STILLPOINT);
        if (still == null) return;
        Store store = Store.get(server);
        Rift r = store.rifts.get(rift.key());
        if (r == null) return;
        if (r.index() < 0) {
            r = r.withIndex(store.nextIndex++);
            store.rifts.put(r.key(), r);
            store.byIndex.put(r.index(), r.key());
            store.setDirty();
        }
        Layout l = Layout.of(r.index(), r.seed());
        if (!store.built.contains(r.index())) {
            build(still, l);
            store.built.add(r.index());
            store.setDirty();
        } else if (!occupied(r.key(), sp)) {
            reset(still, l);
        }
        s.riftReturnDim = sp.level().dimension();
        s.riftReturn = sp.position();
        s.savedAnchorDim = s.anchorDim;
        s.savedAnchor = s.anchor;
        s.savedAnchorIsBody = s.anchorIsBody;
        s.pocket = r.key();
        s.anchorDim = Veil.STILLPOINT;
        s.anchor = l.arrival();
        s.anchorIsBody = false;
        s.lastGood = l.arrival();
        s.dirty = true;
        Projection.withTransit(sp, () -> Projection.teleport(sp, still, l.arrival(), -90F, 0F));
        still.playSound(null, l.arrival().x, l.arrival().y, l.arrival().z, VeilContent.RIFT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("A Veil Gate"))
                .body(Component.literal("Only a spirit can walk here. Here the blue is solid, and it listens. Make the levers match the lights."))
                .ttl(20 * 9).priority(5).build());
        DirectorLog.log(sp, "veil", "entered Veil Gate " + r.key() + " (pocket " + r.index() + ")");
    }

    private static boolean occupied(String key, ServerPlayer except) {
        for (var e : Projection.LIVE.entrySet()) {
            if (!e.getKey().equals(except.getUUID()) && key.equals(e.getValue().pocket)) return true;
        }
        return false;
    }

    /** Back out through the rift to where the spirit entered. */
    static void exit(ServerPlayer sp, Projection.Spirit s) {
        if (s.pocket == null) return;
        s.pocket = null;
        s.anchorDim = s.savedAnchorDim;
        s.anchor = s.savedAnchor;
        s.anchorIsBody = s.savedAnchorIsBody;
        s.riftCooldown = 80;
        s.lastGood = s.riftReturn;
        s.dirty = true;
        ServerLevel back = sp.level().getServer().getLevel(s.riftReturnDim);
        if (back == null) return;
        Projection.withTransit(sp, () -> Projection.teleport(sp, back, s.riftReturn, sp.getYRot(), sp.getXRot()));
    }

    /** The cache: paid once per player per rift, then out through the rift. */
    public static boolean claimCache(ServerPlayer sp, ServerLevel level, BlockPos pos) {
        Projection.Spirit s = Projection.live(sp);
        if (s == null || s.pocket == null) {
            Signal.toast(sp, Component.literal("Empty. Whatever was here went somewhere else."));
            return false;
        }
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        level.playSound(null, pos, VeilContent.CACHE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (mem.riftsCleared.add(s.pocket)) {
            Progression.addStat(sp, Stat.SPI, 1);
            Progression.count(sp, Counters.SECRETS_FOUND, 1);
            Progression.grantLevels(sp, 0.15, Progression.XpSource.STORY);
            Progression.markNewThing(sp, "veil gate");
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Veil Gate cleared"))
                    .row("Spirit", "+1")
                    .row("Secrets found", "+1")
                    .footer(Component.literal("The rift closes behind you."))
                    .ttl(20 * 8).priority(6).build());
            DirectorLog.log(sp, "veil", "cleared Veil Gate " + s.pocket);
        } else {
            Signal.toast(sp, Component.literal("Empty. You've been here before."));
        }
        exit(sp, s);
        return true;
    }
}
