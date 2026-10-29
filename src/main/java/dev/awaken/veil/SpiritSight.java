package dev.awaken.veil;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.veil.entity.VeilSpirit;
import dev.awaken.veil.net.VeilPayloads;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Spirit sight. The server scans what lies inside a spirit's Awareness, a few chunk sections per tick, nearest first,
 * and sends only those positions. The client draws them; it never learns anything outside the radius or near another
 * player's base. Awake, an awakened player keeps a small "secret sense" (SPEC section 3: 4 + PER/4 blocks).
 */
public final class SpiritSight {
    private SpiritSight() {}

    public static final byte NONE = 0, COAL = 1, IRON = 2, COPPER = 3, GOLD = 4, REDSTONE = 5, LAPIS = 6, DIAMOND = 7, EMERALD = 8,
            QUARTZ = 9, OTHER_ORE = 10, SECRET = 20;
    private static final int SECTIONS_PER_TICK = 8;
    private static final int MAX_ORES = 384;
    private static final int MAX_SECRETS = 64;
    private static final int MAX_MOBS = 64;
    private static final double FOUND_RANGE = 3.5;

    private static final Reference2ByteOpenHashMap<BlockState> CLASS = new Reference2ByteOpenHashMap<>();

    /** Clears the block classification cache (tags may have changed after /reload). */
    static void clearCache() {
        CLASS.clear();
    }

    public static byte classify(BlockState state) {
        byte b = CLASS.getOrDefault(state, (byte) -1);
        if (b >= 0) return b;
        b = compute(state);
        CLASS.put(state, b);
        return b;
    }

    private static byte compute(BlockState s) {
        if (s.isAir()) return NONE;
        if (s.is(VeilContent.VEIL_SECRETS)) return SECRET;
        if (!s.is(Tags.BlockItems.ORES.block())) return NONE;
        if (s.is(Tags.BlockItems.ORES_DIAMOND.block())) return DIAMOND;
        if (s.is(Tags.BlockItems.ORES_EMERALD.block())) return EMERALD;
        if (s.is(Tags.BlockItems.ORES_GOLD.block())) return GOLD;
        if (s.is(Tags.BlockItems.ORES_IRON.block())) return IRON;
        if (s.is(Tags.BlockItems.ORES_COPPER.block())) return COPPER;
        if (s.is(Tags.BlockItems.ORES_COAL.block())) return COAL;
        if (s.is(Tags.BlockItems.ORES_REDSTONE.block())) return REDSTONE;
        if (s.is(Tags.BlockItems.ORES_LAPIS.block())) return LAPIS;
        if (s.is(Tags.BlockItems.ORES_QUARTZ.block())) return QUARTZ;
        return OTHER_ORE;
    }

    private static final Predicate<BlockState> ANY = s -> classify(s) != NONE;
    private static final Predicate<BlockState> SECRETS_ONLY = s -> classify(s) == SECRET;

    /** Incremental scanner state for one viewer. */
    public static final class Scanner {
        private final Long2ObjectOpenHashMap<Finds> sections = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<long[]> chunkSecrets = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet chunksDone = new LongOpenHashSet();
        private final List<Long> queue = new ArrayList<>();
        private int cursor;
        private Vec3 center = new Vec3(0, -9999, 0);
        private ResourceKey<Level> dim;
        private int sweepAge;
        private RevealFilter filter = RevealFilter.OPEN;
        final Set<String> gatesShown = new HashSet<>();
        int age;
        long[] lastSecrets = new long[0];
    }

    // ---- spirit tick ------------------------------------------------------------------------------------------------

    static void tick(ServerPlayer sp, Projection.Spirit s) {
        Scanner sc = s.scanner;
        sc.age++;
        ServerLevel level = sp.level();
        s.awareness = Projection.awareness(sp);
        int radius = s.awareness;
        if (sc.dim != level.dimension() || sc.center.distanceToSqr(sp.position()) > 12 * 12 || (sc.cursor >= sc.queue.size() && ++sc.sweepAge > 60)) {
            restart(sc, level, sp, sp.position(), radius);
        }
        scan(sc, level, SECTIONS_PER_TICK, false);
        if (sc.age % 20 == 1) send(sp, s, sc, level, radius);
        if (sc.age % 10 == 5) checkFound(sp, level, sc);
        if (sc.age % 20 == 10) scoutGates(sp, s, sc, level, radius);
    }

    private static void restart(Scanner sc, ServerLevel level, ServerPlayer viewer, Vec3 center, int radius) {
        if (sc.dim != level.dimension()) {
            sc.sections.clear();
            sc.chunkSecrets.clear();
        }
        sc.dim = level.dimension();
        sc.center = center;
        sc.cursor = 0;
        sc.sweepAge = 0;
        sc.queue.clear();
        sc.chunksDone.clear();
        sc.filter = RevealFilter.build(level, viewer, center, radius);
        int scx = SectionPos.blockToSectionCoord(center.x), scy = SectionPos.blockToSectionCoord(center.y), scz = SectionPos.blockToSectionCoord(center.z);
        int r = (radius >> 4) + 1;
        int minSy = level.getMinSectionY(), maxSy = level.getMaxSectionY();
        double limit = (radius + 14.0) * (radius + 14.0);
        List<long[]> candidates = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                int sy = scy + dy;
                if (sy < minSy || sy > maxSy) continue;
                for (int dz = -r; dz <= r; dz++) {
                    double cx = ((scx + dx) << 4) + 8 - center.x, cy = (sy << 4) + 8 - center.y, cz = ((scz + dz) << 4) + 8 - center.z;
                    double d = cx * cx + cy * cy + cz * cz;
                    if (d > limit) continue;
                    candidates.add(new long[]{SectionPos.asLong(scx + dx, sy, scz + dz), (long) d});
                }
            }
        }
        candidates.sort(Comparator.comparingLong(a -> a[1]));
        for (long[] c : candidates) sc.queue.add(c[0]);
        // forget sections far outside the new sphere
        sc.sections.keySet().removeIf(k -> {
            double cx = (SectionPos.x(k) << 4) + 8 - center.x, cy = (SectionPos.y(k) << 4) + 8 - center.y, cz = (SectionPos.z(k) << 4) + 8 - center.z;
            return cx * cx + cy * cy + cz * cz > limit * 1.6;
        });
    }

    private static void scan(Scanner sc, ServerLevel level, int budget, boolean secretsOnly) {
        for (int n = 0; n < budget && sc.cursor < sc.queue.size(); n++) {
            long key = sc.queue.get(sc.cursor++);
            int sx = SectionPos.x(key), sy = SectionPos.y(key), sz = SectionPos.z(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
            if (chunk == null) continue;
            long chunkKey = net.minecraft.world.level.ChunkPos.pack(sx, sz);
            if (sc.chunksDone.add(chunkKey)) sc.chunkSecrets.put(chunkKey, blockEntitySecrets(level, chunk, sc.filter));
            int idx = chunk.getSectionIndexFromSectionY(sy);
            if (idx < 0 || idx >= chunk.getSections().length) continue;
            LevelChunkSection section = chunk.getSection(idx);
            if (section.hasOnlyAir() || !section.maybeHas(secretsOnly ? SECRETS_ONLY : ANY)) {
                sc.sections.put(key, Finds.EMPTY);
                continue;
            }
            it.unimi.dsi.fastutil.longs.LongArrayList found = new it.unimi.dsi.fastutil.longs.LongArrayList();
            it.unimi.dsi.fastutil.bytes.ByteArrayList kinds = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
            int bx = sx << 4, by = sy << 4, bz = sz << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        byte kind = classify(section.getBlockState(x, y, z));
                        if (kind == NONE || secretsOnly && kind != SECRET) continue;
                        int wx = bx + x, wy = by + y, wz = bz + z;
                        if (!sc.filter.allowed(wx, wy, wz)) continue;
                        found.add(BlockPos.asLong(wx, wy, wz));
                        kinds.add(kind);
                    }
                }
            }
            sc.sections.put(key, found.isEmpty() ? Finds.EMPTY : new Finds(found.toLongArray(), kinds.toByteArray()));
        }
    }

    /** What one chunk section holds: positions and their kinds, side by side. */
    private record Finds(long[] pos, byte[] kind) {
        static final Finds EMPTY = new Finds(new long[0], new byte[0]);
    }

    private static long[] blockEntitySecrets(ServerLevel level, LevelChunk chunk, RevealFilter filter) {
        List<Long> out = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockPos p = be.getBlockPos();
            boolean secret = false;
            if (be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) secret = true;
            else if (be instanceof RandomizableContainer rc && rc.getLootTable() != null) {
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX() & 15, p.getZ() & 15);
                // a hidden, unopened loot chest: dungeons, temples, ruins, wrecks (a village larder is not a secret)
                secret = p.getY() < surface - 3
                        && !level.structureManager().getStructureWithPieceAt(p, net.minecraft.tags.StructureTags.VILLAGE).isValid();
                if (secret) VeilGates.considerRift(level, p);
            }
            if (secret && filter.allowed(p)) out.add(p.asLong());
        }
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    private static void send(ServerPlayer sp, Projection.Spirit s, Scanner sc, ServerLevel level, int radius) {
        Vec3 c = sp.position();
        double r2 = (double) radius * radius;
        List<long[]> ores = new ArrayList<>();
        List<Long> secrets = new ArrayList<>();
        for (Finds f : sc.sections.values()) {
            for (int i = 0; i < f.pos().length; i++) {
                long pos = f.pos()[i];
                byte kind = f.kind()[i];
                double d = dist2(pos, c);
                if (d > r2) continue;
                if (kind == SECRET) secrets.add(pos);
                else ores.add(new long[]{pos, kind, (long) d});
            }
        }
        for (long[] arr : sc.chunkSecrets.values()) for (long p : arr) if (dist2(p, c) <= r2) secrets.add(p);
        ores.sort(Comparator.comparingLong(a -> a[2]));
        int n = Math.min(MAX_ORES, ores.size());
        long[] orePos = new long[n];
        byte[] oreKind = new byte[n];
        for (int i = 0; i < n; i++) {
            orePos[i] = ores.get(i)[0];
            oreKind[i] = (byte) ores.get(i)[1];
        }
        secrets.sort(Comparator.comparingDouble(p -> dist2(p, c)));
        long[] sec = new long[Math.min(MAX_SECRETS, secrets.size())];
        for (int i = 0; i < sec.length; i++) sec[i] = secrets.get(i);
        sc.lastSecrets = sec;
        long[] rifts = VeilGates.riftsFor(sp, level, c, radius);
        List<VeilPayloads.GateMark> gates = new ArrayList<>();
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        for (GateHooks.GateView g : GateHooks.near(level, BlockPos.containing(c), radius)) {
            Rank rank = Rank.of(g.rank());
            gates.add(new VeilPayloads.GateMark(g.pos(), rank.colour, rank.label + " · " + g.themeName(), mem.gatesScouted.contains(g.id())));
            if (gates.size() >= 16) break;
        }
        // mobs: every rank, and anything we can wear
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(radius), m -> m.isAlive() && !(m instanceof VeilSpirit)
                && m.distanceToSqr(c) <= r2 && sc.filter.allowed(m.getX(), m.getY(), m.getZ()));
        mobs.sort(Comparator.comparingDouble(m -> m.distanceToSqr(c)));
        int level2 = Projection.possessLevel(sp);
        int count = Math.min(MAX_MOBS, mobs.size());
        List<int[]> marks = new ArrayList<>(count + 4);
        for (int i = 0; i < count; i++) {
            Mob m = mobs.get(i);
            if (m == s.possessed) continue;
            marks.add(new int[]{m.getId(), mobColour(m, level2)});
        }
        for (Mannequin body : level.getEntitiesOfClass(Mannequin.class, new AABB(c, c).inflate(radius),
                b -> b.getExistingData(VeilContent.BODY_TAG.get()).map(BodyTag::isBody).orElse(false))) {
            boolean mine = body == s.body;
            marks.add(new int[]{body.getId(), mine ? 0xFFF2A541 : 0xFFD6ECFF});
        }
        int[] ids = new int[marks.size()], cols = new int[marks.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = marks.get(i)[0];
            cols[i] = marks.get(i)[1];
        }
        AwakenNet.send(sp, new VeilPayloads.Sight(false, orePos, oreKind, sec, rifts, gates, ids, cols));
    }

    static int mobColour(Mob m, int possessLevel) {
        if (CordEaters.isEater(m)) return 0xFFD84A4F;
        MobRole role = m.getExistingData(CoreApi.MOB_ROLE).orElse(MobRole.NONE);
        if (role.role() != MobRole.Role.NONE && role.contentLevel() > 0) return 0xFF000000 | rankFor(role.contentLevel()).colour;
        if (m instanceof Enemy) return 0xFF000000 | Rank.E.colour;
        return possessLevel >= 1 && Possession.eligible(m, possessLevel) ? 0xFF7FC3FF : 0xFF9AA4AE;
    }

    static Rank rankFor(int contentLevel) {
        Rank best = Rank.E;
        for (Rank r : Rank.values()) if (r.contentLevel <= contentLevel) best = r;
        return best;
    }

    private static void checkFound(ServerPlayer sp, ServerLevel level, Scanner sc) {
        Vec3 c = sp.position().add(0, 0.9, 0);
        for (long p : sc.lastSecrets) {
            if (dist2(p, c) > FOUND_RANGE * FOUND_RANGE) continue;
            found(sp, level, p);
        }
    }

    /** First time this player gets close to this secret: it counts. */
    static void found(ServerPlayer sp, ServerLevel level, long pos) {
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        long key = pos * 31 + level.dimension().identifier().hashCode();
        if (!mem.markSecret(key)) return;
        Progression.count(sp, Counters.SECRETS_FOUND, 1);
        Signal.toast(sp, Component.literal("A secret. Nobody was meant to find this."));
    }

    /** Drifting into a gate's mouth as a spirit shows its whole card (and marks it scouted for the gates module). */
    private static void scoutGates(ServerPlayer sp, Projection.Spirit s, Scanner sc, ServerLevel level, int radius) {
        for (GateHooks.GateView g : GateHooks.near(level, sp.blockPosition(), 6)) {
            if (g.pos().distToCenterSqr(sp.position()) > 6.5 * 6.5) continue;
            if (!sc.gatesShown.add(g.id())) continue;
            GateHooks.markScouted(sp, g.id());
            VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
            if (mem.markGate(g.id())) Progression.count(sp, "awaken:gates_scouted", 1);
            Signal.send(sp, gateCard(level, g));
        }
    }

    static SignalWindow gateCard(ServerLevel level, GateHooks.GateView g) {
        Rank rank = Rank.of(g.rank());
        long days = g.breaksOnPlayDay() - PlayDays.current(level.getServer());
        // no clock: it never breaks, or it waits for someone to come close before it starts (SPEC §7)
        String breaks = "BROKEN".equals(g.state()) ? "Broken"
                : g.breaksOnPlayDay() == Long.MAX_VALUE ? (GateHooks.waiting(level, g.id()) ? "Not until someone goes near it" : "Never")
                : days <= 0 ? "Today" : days == 1 ? "1 play-day" : days + " play-days";
        var b = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal(g.themeName().isEmpty() ? "Gate" : g.themeName()))
                .row("Rank", rank.label)
                .row("Breaks in", breaks)
                .row("Layout", g.layout().isEmpty() ? "?" : Spirits.pretty(g.layout()))
                .row("Modifiers", g.modifiers().isEmpty() ? "None" : String.join(", ", g.modifiers().stream().map(Spirits::pretty).toList()))
                .row("Twist", g.twist() == null || g.twist().isEmpty() ? "None" : Spirits.pretty(g.twist()))
                .row("Floors", String.valueOf(g.floors()))
                .row("Boss", g.boss().isEmpty() ? "?" : Spirits.pretty(g.boss().substring(g.boss().indexOf(':') + 1)))
                .footer(Component.literal("Scouted from the Veil"))
                .ttl(20 * 14).priority(9);
        if (!g.omen().isEmpty()) b.body(Component.literal("“" + g.omen() + "”"));
        return b.build();
    }

    // ---- awake secret sense -----------------------------------------------------------------------------------------

    private static final Map<UUID, Scanner> AWAKE = new HashMap<>();

    static void tickAwake(ServerPlayer sp) {
        if (sp.tickCount % 5 != 0) return;
        if (!Veil.enabled() || !Progression.profile(sp).awakened() || Veil.isSpirit(sp)) {
            if (AWAKE.remove(sp.getUUID()) != null) AwakenNet.send(sp, VeilPayloads.Sight.EMPTY);
            return;
        }
        Scanner sc = AWAKE.computeIfAbsent(sp.getUUID(), k -> new Scanner());
        sc.age++;
        ServerLevel level = sp.level();
        int radius = Cord.secretSense(Progression.stat(sp, Stat.PER), Progression.perk(sp, Perks.SECRET_GLOW_RANGE));
        if (sc.dim != level.dimension() || sc.center.distanceToSqr(sp.position()) > 4 * 4 || (sc.cursor >= sc.queue.size() && ++sc.sweepAge > 12)) {
            restart(sc, level, sp, sp.position(), radius);
        }
        scan(sc, level, 4, true);
        if (sc.age % 4 == 0) {
            Vec3 c = sp.position();
            double r2 = (double) radius * radius;
            List<Long> secrets = new ArrayList<>();
            for (Finds f : sc.sections.values()) for (long p : f.pos()) if (dist2(p, c) <= r2) secrets.add(p);
            for (long[] arr : sc.chunkSecrets.values()) for (long p : arr) if (dist2(p, c) <= r2) secrets.add(p);
            long[] sec = new long[Math.min(MAX_SECRETS, secrets.size())];
            for (int i = 0; i < sec.length; i++) sec[i] = secrets.get(i);
            boolean unchanged = java.util.Arrays.equals(sec, sc.lastSecrets);
            sc.lastSecrets = sec;
            if (!unchanged || sc.age % 40 == 0) {
                AwakenNet.send(sp, new VeilPayloads.Sight(true, new long[0], new byte[0], sec, new long[0], List.of(), new int[0], new int[0]));
            }
            Vec3 eye = c.add(0, 0.9, 0);
            for (long p : sec) if (dist2(p, eye) <= 2.5 * 2.5) found(sp, level, p);
        }
    }

    static void forgetAwake(UUID id) {
        AWAKE.remove(id);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private static double dist2(long pos, Vec3 c) {
        double dx = BlockPos.getX(pos) + 0.5 - c.x, dy = BlockPos.getY(pos) + 0.5 - c.y, dz = BlockPos.getZ(pos) + 0.5 - c.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
