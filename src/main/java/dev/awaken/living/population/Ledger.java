package dev.awaken.living.population;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.living.api.Regions;
import dev.awaken.mobs.MobTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Creatures live somewhere (SPEC §21.7): a ledger, not entities. Each 512×512 cell gets, the first time a player comes
 * near, up to six populations from the world seed: herds that graze between three pastures (the same herd in the same
 * valley tomorrow, a little further along), packs with a den that sleep there by day and trail their herd by night, and
 * roosts that leave at dawn and dusk. Where a population is is a pure function of its seed, the play-day and the time,
 * so nothing simulates while nobody watches. Within 96 blocks of a player it takes bodies; past 128 they go back into
 * its count. A cell visited again catches up in one step: herds regrow, each night a pack hunted cost its herd one, a
 * wiped pack lets its herd grow faster. Saved data {@code awaken:living_world}, schema 1, only cells players reached.
 */
public final class Ledger extends SavedData {
    static final int SCHEMA = 1, CELL = 512, NEAR = 96, FAR = 128, PER_PLAYER = 24;
    public static final String TAG = "awaken_ledger";

    /** Herds, their hunters, the flyers' roosts, by fauna land. */
    static final Map<String, String> HERDS = Map.of("greenwild", "mossback", "frostwild", "frost_stag", "sunscorch", "dune_ray",
            "crag", "crag_ram", "blossom", "honey_bear");
    static final Map<String, String> PACKS = Map.of("greenwild", "gloomstalker", "frostwild", "wisp_hound", "canopy", "canopy_prowler",
            "duskwood", "thornling");
    static final Map<String, String> ROOSTS = Map.of("frostwild", "frostfinch", "crag", "peak_kite", "duskwood", "hollow_owl");
    private static final Set<String> HERD_SPECIES = new HashSet<>(HERDS.values());

    /** Whether natural spawning leaves this species to the ledger (its herds). */
    public static boolean ledgered(String speciesId) {
        return HERD_SPECIES.contains(speciesId);
    }

    /** One population: a herd, a pack or a roost. */
    public static final class Pop {
        static final Codec<Pop> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(p -> p.id),
                Codec.STRING.fieldOf("kind").forGetter(p -> p.kind),
                Codec.STRING.fieldOf("species").forGetter(p -> p.species),
                Codec.INT.fieldOf("hx").forGetter(p -> p.hx),
                Codec.INT.fieldOf("hz").forGetter(p -> p.hz),
                Codec.INT.listOf().optionalFieldOf("pastures", List.of()).forGetter(p -> p.pastures),
                Codec.INT.fieldOf("count").forGetter(p -> p.count),
                Codec.INT.fieldOf("max").forGetter(p -> p.max),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(p -> p.day),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(p -> p.seed),
                Codec.INT.optionalFieldOf("herd", -1).forGetter(p -> p.herd)
        ).apply(i, Pop::new));

        public final int id;
        public final String kind, species;
        public final int hx, hz;
        /** Three pastures (x, z pairs) for a herd. */
        public final List<Integer> pastures;
        public int count;
        public final int max;
        public long day;
        public final long seed;
        /** A pack's herd (its id), or -1. */
        public final int herd;

        Pop(int id, String kind, String species, int hx, int hz, List<Integer> pastures, int count, int max, long day, long seed, int herd) {
            this.id = id;
            this.kind = kind;
            this.species = species;
            this.hx = hx;
            this.hz = hz;
            this.pastures = List.copyOf(pastures);
            this.count = count;
            this.max = max;
            this.day = day;
            this.seed = seed;
            this.herd = herd;
        }

        int cap() {
            return kind.equals("herd") ? 12 : kind.equals("pack") ? 5 : 6;
        }
    }

    record Cell(long key, List<Pop> pops) {
        static final Codec<Cell> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("key").forGetter(Cell::key),
                Pop.CODEC.listOf().optionalFieldOf("pops", List.of()).forGetter(Cell::pops)
        ).apply(i, (k, p) -> new Cell(k, new ArrayList<>(p))));
    }

    public static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(l -> SCHEMA),
            Cell.CODEC.listOf().optionalFieldOf("cells", List.of()).forGetter(l -> List.copyOf(l.cells.values())),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(l -> l.nextId)
    ).apply(i, (schema, cells, next) -> new Ledger(cells, next)));

    public static final SavedDataType<Ledger> TYPE = new SavedDataType<>(Awaken.id("living_world"), Ledger::new, CODEC);

    final Map<Long, Cell> cells = new HashMap<>();
    int nextId;
    /** Bodies of the populations that have them now (not saved: bodies are never saved as ledger bodies). */
    private final transient Map<Integer, List<UUID>> bodies = new HashMap<>();
    private final transient Map<Integer, Pop> byId = new HashMap<>();

    public Ledger() {
        this(List.of(), 1);
    }

    Ledger(List<Cell> list, int next) {
        for (Cell c : list) {
            cells.put(c.key(), c);
            for (Pop p : c.pops()) byId.put(p.id, p);
        }
        this.nextId = next;
    }

    public static Ledger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public @Nullable Pop pop(int id) {
        return byId.get(id);
    }

    public List<Pop> popsNear(int x, int z, int cellRadius) {
        List<Pop> out = new ArrayList<>();
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                Cell c = cells.get(key(cx + dx, cz + dz));
                if (c != null) out.addAll(c.pops());
            }
        }
        return out;
    }

    // ---- generation ---------------------------------------------------------------------------------------------------

    private static final Map<String, TagKey<Biome>> GROUP_TAGS = new HashMap<>();
    private static final List<String> GROUP_NAMES = List.of("greenwild", "greenwild_woods", "frostwild", "frostwild_taiga", "sunscorch",
            "crag", "blossom", "blossom_meadow", "canopy", "duskwood");

    /** The fauna land at a column, from the biome source (never loads a chunk). */
    static String land(ServerLevel level, int x, int z) {
        var biome = level.getUncachedNoiseBiome(x >> 2, 64 >> 2, z >> 2);
        for (String g : GROUP_NAMES) {
            TagKey<Biome> tag = GROUP_TAGS.computeIfAbsent(g, k -> TagKey.create(Registries.BIOME, Awaken.id("fauna/" + k)));
            if (biome.is(tag)) {
                int cut = g.indexOf('_');
                return cut > 0 ? g.substring(0, cut) : g;
            }
        }
        return "";
    }

    /** A cell's populations, made once from the seed: two herds, a pack, a roost at most, by its land. */
    Cell generate(ServerLevel level, int cx, int cz) {
        long seed = level.getSeed() ^ (cx * 341873128712L) ^ (cz * 132897987541L) ^ 0x4C69766EL;
        RandomSource r = RandomSource.create(seed);
        Cell cell = new Cell(key(cx, cz), new ArrayList<>());
        long day = PlayDays.current(level.getServer());
        int baseX = cx * CELL + CELL / 2, baseZ = cz * CELL + CELL / 2;
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int x = baseX + (i == 0 ? 0 : (i % 2 == 0 ? 1 : -1) * 160), z = baseZ + (i == 0 ? 0 : (i < 3 ? 1 : -1) * 160);
            String g = land(level, x, z);
            if (!g.isEmpty()) seen.add(g);
        }
        int herdId = -1;
        for (int i = 0; i < seen.size() && cell.pops().size() < 2; i++) {
            String species = HERDS.get(seen.get(i));
            if (species == null || cell.pops().stream().anyMatch(p -> p.species.equals(species))) continue;
            int hx = baseX + r.nextInt(321) - 160, hz = baseZ + r.nextInt(321) - 160;
            List<Integer> pastures = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                pastures.add(hx + r.nextInt(241) - 120);
                pastures.add(hz + r.nextInt(241) - 120);
            }
            Pop p = new Pop(nextId++, "herd", species, hx, hz, pastures, 6 + r.nextInt(5), 12, day, r.nextLong(), -1);
            cell.pops().add(p);
            if (herdId < 0) herdId = p.id;
        }
        for (String g : seen) {
            String species = PACKS.get(g);
            if (species == null) continue;
            cell.pops().add(new Pop(nextId++, "pack", species, baseX + r.nextInt(301) - 150, baseZ + r.nextInt(301) - 150, List.of(),
                    3 + r.nextInt(3), 5, day, r.nextLong(), herdId));
            break;
        }
        for (String g : seen) {
            String species = ROOSTS.get(g);
            if (species == null) continue;
            cell.pops().add(new Pop(nextId++, "roost", species, baseX + r.nextInt(301) - 150, baseZ + r.nextInt(301) - 150, List.of(),
                    4 + r.nextInt(3), 6, day, r.nextLong(), -1));
            break;
        }
        for (Pop p : cell.pops()) byId.put(p.id, p);
        cells.put(cell.key(), cell);
        setDirty();
        dev.awaken.living.camp.Camps.seedCell(level, baseX, baseZ, seed); // its camps and dens, placed when someone comes near
        return cell;
    }

    // ---- where they are -----------------------------------------------------------------------------------------------

    /**
     * Where a population is now, x and z (a pure function of its seed, the play-day and the time). A herd: at one
     * pasture by day and the next by night, walking between them at dawn and dusk. A pack: at its den by day, trailing
     * its herd by night. A roost: home, but out circling at dawn and dusk.
     */
    public double[] where(Pop p, long day, long timeOfDay) {
        long tod = Math.floorMod(timeOfDay, 24000L);
        switch (p.kind) {
            case "herd" -> {
                int idx = (int) Math.floorMod(day + p.seed, 3L);
                double[] a = pasture(p, idx), b = pasture(p, idx + 1), c = pasture(p, idx + 2);
                double[] at;
                if (tod < 2000) at = lerp(a, b, tod / 2000.0);
                else if (tod < 12000) at = b;
                else if (tod < 13500) at = lerp(b, c, (tod - 12000) / 1500.0);
                else at = c;
                double w = tod / 24000.0 * Math.PI * 2;
                return new double[]{at[0] + Math.sin(w * 3 + p.seed % 7) * 10, at[1] + Math.cos(w * 2 + p.seed % 5) * 10};
            }
            case "pack" -> {
                boolean night = tod >= 13000 && tod < 23000;
                Pop herd = p.herd >= 0 ? byId.get(p.herd) : null;
                if (!night || herd == null) return new double[]{p.hx, p.hz};
                double[] h = where(herd, day, timeOfDay);
                double dx = p.hx - h[0], dz = p.hz - h[1], len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                return new double[]{h[0] + dx / len * 24, h[1] + dz / len * 24};
            }
            default -> {
                boolean out = tod >= 23000 || tod < 1000 || tod >= 12000 && tod < 13500;
                if (!out) return new double[]{p.hx, p.hz};
                double a = tod / 300.0 + p.seed % 11;
                return new double[]{p.hx + Math.cos(a) * 40, p.hz + Math.sin(a) * 40};
            }
        }
    }

    private static double[] pasture(Pop p, int i) {
        int k = Math.floorMod(i, 3) * 2;
        return new double[]{p.pastures.get(k), p.pastures.get(k + 1)};
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    // ---- catch-up -----------------------------------------------------------------------------------------------------

    /**
     * Days passed since a population was last seen, settled in one step: a herd regrows one a day (two while its pack
     * is gone), a pack one every two days (one every four once wiped out); each night a pack hunted, its herd lost one
     * (never under two).
     */
    void catchUp(Pop p, long today) {
        long days = today - p.day;
        if (days <= 0) return;
        switch (p.kind) {
            case "herd" -> {
                boolean packGone = true;
                for (Pop o : byId.values()) if (o.herd == p.id && o.count > 0) packGone = false;
                p.count = (int) Math.min(p.max, p.count + days * (packGone ? 2 : 1));
            }
            case "pack" -> {
                Pop herd = p.herd >= 0 ? byId.get(p.herd) : null;
                if (herd != null && p.count > 0) {
                    catchUp(herd, today);
                    herd.count = (int) Math.max(Math.min(herd.count, 2), herd.count - days);
                }
                p.count = (int) Math.min(p.max, p.count + days / (p.count == 0 ? 4 : 2));
            }
            default -> p.count = (int) Math.min(p.max, p.count + days);
        }
        p.day = today;
        setDirty();
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    /** Every 2 s: cells near players exist, populations near them take bodies, those left behind give theirs back. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 7) return;
        ServerLevel level = server.overworld();
        if (!Regions.on(level)) return;
        Ledger l = get(server);
        long day = PlayDays.current(server), tod = level.getOverworldClockTime();
        boolean generated = false;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            int cx = Math.floorDiv(p.getBlockX(), CELL), cz = Math.floorDiv(p.getBlockZ(), CELL);
            for (int dx = -1; dx <= 1 && !generated; dx++) {
                for (int dz = -1; dz <= 1 && !generated; dz++) {
                    if (!l.cells.containsKey(key(cx + dx, cz + dz))) {
                        l.generate(level, cx + dx, cz + dz); // one a tick at most
                        generated = true;
                    }
                }
            }
            int near = 0;
            for (List<UUID> b : l.bodies.values()) near += b.size();
            for (Pop pop : l.popsNear(p.getBlockX(), p.getBlockZ(), 1)) {
                if (l.bodies.containsKey(pop.id)) continue;
                double[] at = l.where(pop, day, tod);
                double dx = at[0] - p.getX(), dz = at[1] - p.getZ();
                if (dx * dx + dz * dz > (double) NEAR * NEAR || near >= PER_PLAYER) continue;
                near += l.materialize(level, pop, at, day);
            }
        }
        l.steer(level, day, tod);
        l.release(level);
    }

    /** A population takes bodies at its place (if it's loaded, dry land, and the population has any); how many. */
    int materialize(ServerLevel level, Pop p, double[] at, long day) {
        catchUp(p, day);
        if (p.count <= 0) return 0;
        BlockPos col = new BlockPos((int) at[0], 0, (int) at[1]);
        if (!level.hasChunkAt(col)) return 0;
        BlockPos ground = col.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ()));
        if (!level.getFluidState(ground.below()).isEmpty() || !level.getFluidState(ground).isEmpty()) return 0;
        EntityType<?> type = MobTypes.type(p.species);
        if (type == null) return 0;
        RandomSource r = level.getRandom();
        int n = Math.min(p.count, p.cap()), spread = p.kind.equals("herd") ? 8 : 4;
        List<UUID> made = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int x = ground.getX() + r.nextInt(spread * 2 + 1) - spread, z = ground.getZ() + r.nextInt(spread * 2 + 1) - spread;
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (p.kind.equals("roost")) pos = pos.above(6);
            Entity e = type.spawn(level, pos, EntitySpawnReason.EVENT);
            if (e == null) continue;
            e.addTag(TAG);
            e.addTag(TAG + "_" + p.id);
            if (e instanceof Mob m) m.setPersistenceRequired();
            made.add(e.getUUID());
        }
        if (made.isEmpty()) return 0;
        bodies.put(p.id, made);
        return made.size();
    }

    /** Bodies walk toward where their population is now (a herd crossing to its next pasture, a pack home at dawn). */
    private void steer(ServerLevel level, long day, long tod) {
        for (var e : bodies.entrySet()) {
            Pop p = byId.get(e.getKey());
            if (p == null || p.kind.equals("roost")) continue;
            double[] at = where(p, day, tod);
            int k = 0;
            for (UUID id : e.getValue()) {
                if (!(level.getEntity(id) instanceof PathfinderMob m) || !m.isAlive() || m.getTarget() != null) continue;
                double dx = at[0] - m.getX(), dz = at[1] - m.getZ();
                if (dx * dx + dz * dz < 12 * 12) continue; // close enough: let it graze
                k++;
                m.getNavigation().moveTo(at[0] + (k % 5) * 2 - 4, m.getY(), at[1] + (k / 5) * 2 - 2, p.kind.equals("herd") ? 0.9 : 1.1);
            }
        }
    }

    /**
     * Bodies nobody is near go back into their population (its count already lost whoever died: {@link #onDeath}). An
     * unloaded body counts as unwatched; it's discarded when its chunk loads again ({@link #onJoin}).
     */
    private void release(ServerLevel level) {
        var it = bodies.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            boolean watched = false;
            for (UUID id : e.getValue()) {
                Entity b = level.getEntity(id);
                if (b != null && b.isAlive() && level.getNearestPlayer(b, FAR) != null) watched = true;
            }
            if (watched) continue;
            for (UUID id : e.getValue()) {
                Entity b = level.getEntity(id);
                if (b != null && b.isAlive()) b.discard();
            }
            it.remove();
        }
    }

    /** A body died: its population is one fewer (a wiped pack lets its herd grow faster). */
    public static void onDeath(net.minecraft.world.entity.LivingEntity dead) {
        if (!dead.entityTags().contains(TAG) || !(dead.level() instanceof ServerLevel level)) return;
        get(level.getServer()).died(dead);
    }

    void died(net.minecraft.world.entity.LivingEntity dead) {
        Ledger l = this;
        for (String t : dead.entityTags()) {
            if (!t.startsWith(TAG + "_")) continue;
            try {
                Pop p = l.byId.get(Integer.parseInt(t.substring(TAG.length() + 1)));
                if (p != null && p.count > 0) {
                    p.count--;
                    l.setDirty();
                }
            } catch (NumberFormatException ignored) {
                // not ours
            }
        }
        for (List<UUID> b : l.bodies.values()) b.remove(dead.getUUID());
    }

    /** Tests: a hand-made cell. */
    void add(Cell cell) {
        cells.put(cell.key(), cell);
        for (Pop p : cell.pops()) byId.put(p.id, p);
    }

    List<UUID> bodiesOf(int popId) {
        return bodies.getOrDefault(popId, List.of());
    }

    /** Tests: every body goes back, watched or not. */
    void releaseAll(ServerLevel level) {
        for (List<UUID> b : bodies.values()) {
            for (UUID id : b) {
                Entity e = level.getEntity(id);
                if (e != null && e.isAlive()) e.discard();
            }
        }
        bodies.clear();
    }

    /** A ledger body loaded from disk after a restart is nobody's now: it goes (its population will take a new one). */
    public static void onJoin(Entity e, MinecraftServer server) {
        if (!e.entityTags().contains(TAG)) return;
        Ledger l = get(server);
        for (List<UUID> b : l.bodies.values()) if (b.contains(e.getUUID())) return;
        e.discard();
    }

    /** Dev: the populations near a player, one line each. */
    public static List<String> describe(ServerPlayer p) {
        Ledger l = get(p.level().getServer());
        long day = PlayDays.current(p.level().getServer()), tod = p.level().getOverworldClockTime();
        List<String> out = new ArrayList<>();
        for (Pop pop : l.popsNear(p.getBlockX(), p.getBlockZ(), 1)) {
            double[] at = l.where(pop, day, tod);
            int d = (int) Math.sqrt((at[0] - p.getX()) * (at[0] - p.getX()) + (at[1] - p.getZ()) * (at[1] - p.getZ()));
            out.add(pop.kind + " " + pop.species + " ×" + pop.count + " · " + d + " m" + (l.bodies.containsKey(pop.id) ? " · here" : ""));
        }
        return out;
    }
}
