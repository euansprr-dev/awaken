package dev.awaken.living.camp;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.living.api.Regions;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Theme;
import dev.awaken.mobs.def.Tier;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.terrain.api.CityBasin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Hostiles come from places (SPEC §21.8): riftborn camps (gate creatures that got out) and hollow dens seeded in the
 * ledger's cells from level 8, and blight nests left by a broken gate nobody cleared for two play-days. A camp builds
 * into the world when a player comes within 96, wakes at 40 (its creatures and its heart, the camp's boss, then a wave
 * more per tier), sleeps again when they go. Kill the heart and it's cleared: ash for five play-days, then it can come
 * back. Ignored, a camp grows a tier every two play-days while players are around (tier 1-5: +2 levels, +1 wave, a
 * bigger build each); at tier 4 it raids the nearest Wayfarer camp or Hearth the next night, warned a play-day ahead.
 * Saved data {@code awaken:living_camps}, schema 1.
 */
public final class Camps extends SavedData {
    static final int SCHEMA = 1, BUILD = 96, WAKE = 40, SLEEP = 104, CLEAR_DAYS = 5, MAX_TIER = 5;

    public static final class Camp {
        static final Codec<Camp> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(c -> c.id),
                Codec.STRING.fieldOf("kind").forGetter(c -> c.kind),
                Codec.STRING.optionalFieldOf("theme", "").forGetter(c -> c.theme),
                BlockPos.CODEC.fieldOf("pos").forGetter(c -> c.pos),
                Codec.BOOL.optionalFieldOf("resolved", false).forGetter(c -> c.resolved),
                Codec.INT.optionalFieldOf("tier", 1).forGetter(c -> c.tier),
                Codec.INT.optionalFieldOf("built", 0).forGetter(c -> c.built),
                Codec.LONG.optionalFieldOf("cleared_until", 0L).forGetter(c -> c.clearedUntil),
                Codec.LONG.optionalFieldOf("last_grow", 0L).forGetter(c -> c.lastGrow),
                Codec.LONG.optionalFieldOf("raid_day", -1L).forGetter(c -> c.raidDay),
                Codec.STRING.optionalFieldOf("gate", "").forGetter(c -> c.gate),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(c -> c.seed),
                Codec.LONG.listOf().optionalFieldOf("ground", List.of()).forGetter(c -> c.ground),
                Codec.LONG.listOf().optionalFieldOf("stood", List.of()).forGetter(c -> c.stood),
                Codec.STRING.optionalFieldOf("raid_village", "").forGetter(c -> c.raidVillage)
        ).apply(i, Camp::new));

        public final int id;
        public final String kind, theme, gate;
        BlockPos pos;
        boolean resolved;
        public int tier, built;
        long clearedUntil, lastGrow, raidDay;
        final long seed;
        final List<Long> ground, stood;
        /** (SPEC §28.7) A village it means to raid: the village's Burning runs the raid. */
        String raidVillage;

        Camp(int id, String kind, String theme, BlockPos pos, boolean resolved, int tier, int built, long clearedUntil, long lastGrow,
             long raidDay, String gate, long seed, List<Long> ground, List<Long> stood) {
            this(id, kind, theme, pos, resolved, tier, built, clearedUntil, lastGrow, raidDay, gate, seed, ground, stood, "");
        }

        Camp(int id, String kind, String theme, BlockPos pos, boolean resolved, int tier, int built, long clearedUntil, long lastGrow,
             long raidDay, String gate, long seed, List<Long> ground, List<Long> stood, String raidVillage) {
            this.raidVillage = raidVillage;
            this.id = id;
            this.kind = kind;
            this.theme = theme;
            this.pos = pos;
            this.resolved = resolved;
            this.tier = tier;
            this.built = built;
            this.clearedUntil = clearedUntil;
            this.lastGrow = lastGrow;
            this.raidDay = raidDay;
            this.gate = gate;
            this.seed = seed;
            this.ground = new ArrayList<>(ground);
            this.stood = new ArrayList<>(stood);
        }

        public BlockPos pos() {
            return pos;
        }

        public boolean cleared(long day) {
            return clearedUntil > day;
        }

        /** Whether it found its ground (a seeded camp's place is found the first time a player comes within 160). */
        public boolean resolved() {
            return resolved;
        }

        /** (SPEC §28.7) The village it means to raid ("" none): a Burning runs that raid, not this camp. */
        public String raidVillage() {
            return raidVillage;
        }

        public long raidDay() {
            return raidDay;
        }

        public String name() {
            return switch (kind) {
                case "riftborn" -> "Riftborn Camp";
                case "blight_nest" -> "Blight Nest";
                default -> "Hollow Den";
            };
        }
    }

    public static final Codec<Camps> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(c -> SCHEMA),
            Camp.CODEC.listOf().optionalFieldOf("camps", List.of()).forGetter(c -> List.copyOf(c.camps)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("broken_since", Map.of()).forGetter(c -> Map.copyOf(c.brokenSince)),
            Codec.INT.optionalFieldOf("next_id", 1).forGetter(c -> c.nextId)
    ).apply(i, (schema, list, broken, next) -> new Camps(list, broken, next)));

    public static final SavedDataType<Camps> TYPE = new SavedDataType<>(Awaken.id("living_camps"), Camps::new, CODEC);

    final List<Camp> camps;
    final Map<String, Long> brokenSince;
    int nextId;
    /** A camp's creatures while it's awake, and the waves it has left (not saved: an awake camp sleeps on restart). */
    private final transient Map<Integer, List<UUID>> bodies = new HashMap<>();
    private final transient Map<Integer, Integer> waves = new HashMap<>();

    public Camps() {
        this(List.of(), Map.of(), 1);
    }

    Camps(List<Camp> list, Map<String, Long> broken, int next) {
        this.camps = new ArrayList<>(list);
        this.brokenSince = new HashMap<>(broken);
        this.nextId = next;
    }

    public static Camps get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Camp> all() {
        return List.copyOf(camps);
    }

    // ---- seeding ------------------------------------------------------------------------------------------------------

    /** A ledger cell was made: from level 8, a riftborn camp (45%) and a hollow den (30%), their places found later. */
    public static void seedCell(ServerLevel level, int baseX, int baseZ, long seed) {
        if (Regions.levelAt(level, baseX, baseZ) < 8) return;
        Camps c = get(level.getServer());
        RandomSource r = RandomSource.create(seed ^ 0x43616D70L);
        String[] themes = {"awaken:ember_mines", "awaken:bell_crypt", "awaken:fungal_hollow", "awaken:overgrown_temple", "awaken:hive"};
        if (r.nextFloat() < 0.45F) c.add("riftborn", themes[r.nextInt(themes.length)], new BlockPos(baseX + r.nextInt(301) - 150, 0, baseZ + r.nextInt(301) - 150), false, "", r.nextLong());
        if (r.nextFloat() < 0.30F) c.add("hollow_den", "", new BlockPos(baseX + r.nextInt(301) - 150, 0, baseZ + r.nextInt(301) - 150), false, "", r.nextLong());
    }

    Camp add(String kind, String theme, BlockPos pos, boolean resolved, String gate, long seed) {
        Camp camp = new Camp(nextId++, kind, theme, pos, resolved, 1, 0, 0, 0, -1, gate, seed, List.of(), List.of());
        camps.add(camp);
        setDirty();
        return camp;
    }

    /** Where a seeded camp stands: dry, level, allowed ground within 48 of its seed spot (loaded chunks only), or null. */
    static @Nullable BlockPos site(ServerLevel level, BlockPos around, RandomSource r) {
        for (int i = 0; i < 24; i++) {
            int x = around.getX() + r.nextInt(97) - 48, z = around.getZ() + r.nextInt(97) - 48;
            if (!level.hasChunkAt(new BlockPos(x, 0, z)) || CityBasin.reserved(x, z)) continue;
            BlockPos at = CampPainter.surface(level, x, z);
            if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at.below()).isSolid()) continue;
            if (HearthHooks.nearest(level, at, 120).isPresent() || !GateHooks.near(level, at, 48).isEmpty()) continue;
            int rough = 0;
            for (int[] d : new int[][]{{5, 0}, {-5, 0}, {0, 5}, {0, -5}}) {
                if (level.hasChunkAt(at.offset(d[0], 0, d[1]))) rough = Math.max(rough, Math.abs(CampPainter.surface(level, x + d[0], z + d[1]).getY() - at.getY()));
            }
            if (rough <= 2) return at;
        }
        return null;
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 11) return;
        ServerLevel level = server.overworld();
        if (!Regions.on(level)) return;
        get(server).run(level, PlayDays.current(server));
    }

    void run(ServerLevel level, long day) {
        long tod = Math.floorMod(level.getOverworldClockTime(), 24000L);
        for (int i = 0; i < camps.size(); i++) {
            Camp c = camps.get(i);
            ServerPlayer near = nearest(level, c.pos);
            double d = near == null ? Double.MAX_VALUE : Math.sqrt(near.distanceToSqr(c.pos.getX() + 0.5, near.getY(), c.pos.getZ() + 0.5));
            if (!c.resolved) {
                if (d > 160 || !level.hasChunkAt(c.pos)) continue;
                BlockPos at = site(level, c.pos, RandomSource.create(c.seed));
                if (at == null) {
                    camps.remove(i--); // no ground for it here: it never was
                    setDirty();
                    continue;
                }
                c.pos = at;
                c.resolved = true;
                setDirty();
            }
            if (c.cleared(day)) continue;
            if (c.clearedUntil > 0 && !c.cleared(day)) { // back after its ash years: a fresh camp
                c.clearedUntil = 0;
                c.tier = 1;
                c.built = 0;
                setDirty();
            }
            if (d <= BUILD && c.built < c.tier && level.hasChunkAt(c.pos)) build(level, c);
            List<UUID> b = bodies.get(c.id);
            if (b == null && d <= WAKE && c.built > 0) wake(level, c);
            else if (b != null && d > SLEEP) sleep(level, c);
            else if (b != null) nextWave(level, c);
            if (c.raidDay == day && tod >= 14000 && tod < 14400) raid(level, c, day);
        }
    }

    private static @Nullable ServerPlayer nearest(ServerLevel level, BlockPos at) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            double d = p.distanceToSqr(at.getX() + 0.5, p.getY(), at.getZ() + 0.5);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Builds the camp at its tier (a grown camp is rebuilt bigger: its old build goes first). */
    void build(ServerLevel level, Camp c) {
        if (c.built > 0) CampPainter.ash(level, c.ground, c.stood);
        CampPainter.paint(level, c, c.ground, c.stood);
        c.built = c.tier;
        setDirty();
    }

    /** Someone came close: the camp's first wave and its heart. */
    void wake(ServerLevel level, Camp c) {
        List<UUID> made = new ArrayList<>();
        Entity heart = spawn(level, c, true);
        if (heart != null) made.add(heart.getUUID());
        for (int i = 0; i < waveSize(c); i++) {
            Entity e = spawn(level, c, false);
            if (e != null) made.add(e.getUUID());
        }
        bodies.put(c.id, made);
        waves.put(c.id, c.tier - 1);
    }

    static int waveSize(Camp c) {
        return Math.min(9, 2 + c.tier);
    }

    /** The wave is down and the heart still stands: another, while the camp has waves left. */
    private void nextWave(ServerLevel level, Camp c) {
        List<UUID> b = bodies.get(c.id);
        int left = waves.getOrDefault(c.id, 0);
        if (left <= 0) return;
        int fighters = 0;
        for (UUID id : b) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive() && !e.entityTags().contains(HEART)) fighters++;
        }
        if (fighters > 0) return;
        for (int i = 0; i < waveSize(c); i++) {
            Entity e = spawn(level, c, false);
            if (e != null) b.add(e.getUUID());
        }
        waves.put(c.id, left - 1);
    }

    /** Everyone left: the camp sleeps, its creatures go (it's whole again next time; its heart is what matters). */
    void sleep(ServerLevel level, Camp c) {
        List<UUID> b = bodies.remove(c.id);
        waves.remove(c.id);
        if (b == null) return;
        for (UUID id : b) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) e.discard();
        }
    }

    static final String TAG = "awaken_camp", HEART = "awaken_camp_heart";

    /** One of the camp's creatures (or its heart), at its level for the camp's tier, near its middle. */
    @Nullable Entity spawn(ServerLevel level, Camp c, boolean heart) {
        RandomSource r = level.getRandom();
        MobDef def = pick(c, heart, r);
        if (def == null) return null;
        int lv = Regions.creatureLevel(level, c.pos, Regions.Role.CAMP) + (c.tier - 1) * 2 + (heart ? 2 : 0);
        int x = c.pos.getX() + r.nextInt(9) - 4, z = c.pos.getZ() + r.nextInt(9) - 4;
        BlockPos at = level.hasChunkAt(new BlockPos(x, 0, z)) ? CampPainter.surface(level, x, z) : c.pos;
        var role = new MobRole(heart ? MobRole.Role.ELITE : MobRole.Role.NORMAL, lv, def.theme.id, Optional.empty(), "");
        Entity e = Mobs.spawn(level, def, Vec3.atBottomCenterOf(at), lv, role, Set.of());
        if (e == null) return null;
        e.addTag(TAG);
        e.addTag(TAG + "_" + c.id);
        if (heart) {
            e.addTag(HEART);
            e.setCustomName(Component.literal("Heart of the " + c.name()));
        }
        if (e instanceof Mob m) m.setPersistenceRequired();
        e.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(lv, CreatureTag.Kind.CAMP, heart, heart ? "Heart of the " + c.name() : ""));
        return e;
    }

    private static @Nullable MobDef pick(Camp c, boolean heart, RandomSource r) {
        Theme theme = c.kind.equals("hollow_den") ? Theme.HOLLOWS : c.theme.isEmpty() ? Theme.EMBER : Theme.byId(c.theme);
        if (theme == null) theme = Theme.EMBER;
        List<MobDef> pool = heart ? Bestiary.of(theme, Tier.ELITE) : List.of();
        if (pool.isEmpty()) pool = Bestiary.of(theme, Tier.NORMAL);
        return pool.isEmpty() ? null : pool.get(r.nextInt(pool.size()));
    }

    // ---- the heart --------------------------------------------------------------------------------------------------

    /** A camp's heart died: it's cleared. Ash, its creatures gone, and the players there paid. */
    public static void onDeath(LivingEntity dead) {
        if (!dead.entityTags().contains(HEART) || !(dead.level() instanceof ServerLevel level)) return;
        Camps cs = get(level.getServer());
        for (Camp c : cs.camps) {
            if (!dead.entityTags().contains(TAG + "_" + c.id)) continue;
            cs.clear(level, c, PlayDays.current(level.getServer()));
            return;
        }
    }

    void clear(ServerLevel level, Camp c, long day) {
        sleep(level, c);
        CampPainter.ash(level, c.ground, c.stood);
        c.clearedUntil = Math.max(c.clearedUntil, day + CLEAR_DAYS);
        c.built = 0;
        c.raidDay = -1;
        c.raidVillage = "";
        setDirty();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(c.pos)) > 56 * 56) continue;
            Progression.worldXp(p, "camp", 0.4 * (1 + 0.25 * (c.tier - 1))); // pacing_sim CAMP_K, more by tier
            Progression.count(p, "awaken:camps_cleared");
            Signal.toast(p, Component.literal("Cleared · " + c.name() + (c.tier > 1 ? " (tier " + c.tier + ")" : "")));
        }
    }

    // ---- the days -----------------------------------------------------------------------------------------------------

    /**
     * A new play-day: broken gates left two days seed blight nests; camps with players around grow a tier every two
     * days; a camp at tier 4 picks its target and warns the people there it'll raid tomorrow night.
     */
    public static void onNewDay(MinecraftServer server, long day) {
        ServerLevel level = server.overworld();
        if (!Regions.on(level)) return;
        Camps cs = get(server);
        // blight nests from gates nobody cleared
        for (ServerPlayer p : level.players()) {
            for (var g : GateHooks.near(level, p.blockPosition(), 600)) {
                if (!g.state().equals("BROKEN")) continue;
                cs.brokenSince.putIfAbsent(g.id(), day);
                if (day - cs.brokenSince.get(g.id()) >= 2 && cs.camps.stream().noneMatch(c -> c.gate.equals(g.id()))) {
                    cs.add("blight_nest", g.theme(), g.pos(), true, g.id(), day * 31 + g.id().hashCode());
                }
            }
        }
        cs.brokenSince.keySet().removeIf(id -> GateHooks.get(level, id).map(g -> !g.state().equals("BROKEN")).orElse(true));
        for (Camp c : cs.camps) {
            if (!c.resolved || c.cleared(day)) continue;
            boolean around = false;
            for (ServerPlayer p : level.players()) if (p.distanceToSqr(Vec3.atCenterOf(c.pos)) < 1500.0 * 1500.0) around = true;
            if (around && day - c.lastGrow >= 2 && c.tier < MAX_TIER) {
                c.tier++;
                c.lastGrow = day;
            }
            if (c.tier >= 4 && c.raidDay < day) {
                BlockPos target = target(level, c.pos);
                // (SPEC §28.7) a village nearer than any Hearth or the Wayfarer camp: its Burning runs the raid, with its
                // own ladder of warnings (if it takes it: a calm world, or a village already in trouble, doesn't)
                var village = dev.awaken.village.api.Villages.nearest(level, c.pos, 800);
                if (village.isPresent() && (target == null || village.get().origin().distSqr(c.pos) < target.distSqr(c.pos))
                        && dev.awaken.village.api.Troubles.raidComing(level, village.get().key(), c.id, c.name(), c.tier, day + 1)) {
                    c.raidDay = day + 1;
                    c.raidVillage = village.get().key();
                    continue;
                }
                if (target != null) {
                    c.raidDay = day + 1;
                    for (ServerPlayer p : level.players()) {
                        if (p.distanceToSqr(Vec3.atCenterOf(target)) < 800.0 * 800.0) {
                            Signal.toast(p, Component.literal("Scouts saw the " + c.name() + " stirring · it raids tomorrow night"));
                        }
                    }
                }
            }
        }
        cs.setDirty();
    }

    /** What a raid goes for: the nearest Hearth or the Wayfarer camp within 800. */
    static @Nullable BlockPos target(ServerLevel level, BlockPos from) {
        BlockPos best = HearthHooks.nearest(level, from, 800).orElse(null);
        BlockPos camp = dev.awaken.living.wayfarer.Scenes.marker(level.getServer(), 0, "camp");
        if (camp != null && camp.distSqr(from) < 800.0 * 800.0 && (best == null || camp.distSqr(from) < best.distSqr(from))) best = camp;
        return best;
    }

    /** The raid: the camp's creatures come out of the dark 24 blocks short of their target, toward it. */
    private void raid(ServerLevel level, Camp c, long day) {
        c.raidDay = -1;
        setDirty();
        if (!c.raidVillage.isEmpty()) {
            c.raidVillage = ""; // the village's Burning ran (or will resolve) this raid
            return;
        }
        BlockPos target = target(level, c.pos);
        if (target == null) return;
        Vec3 dir = Vec3.atCenterOf(c.pos).subtract(Vec3.atCenterOf(target)).multiply(1, 0, 1).normalize().scale(24);
        BlockPos at = BlockPos.containing(Vec3.atCenterOf(target).add(dir));
        if (!level.hasChunkAt(at)) return;
        BlockPos saved = c.pos;
        c.pos = CampPainter.surface(level, at.getX(), at.getZ());
        for (int i = 0; i < 4 + c.tier; i++) {
            Entity e = spawn(level, c, false);
            if (e instanceof Mob m) m.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 1.0);
        }
        c.pos = saved;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(target)) < 200.0 * 200.0) Signal.toast(p, Component.literal("The " + c.name() + " is raiding!"));
        }
    }

    List<UUID> bodiesOf(int id) {
        return bodies.getOrDefault(id, List.of());
    }

    // ---- (SPEC §28.7) villages: the Burning's raiders, the Taken's den ------------------------------------------------------

    /** The camp by id, or null. */
    public @Nullable Camp byId(int id) {
        for (Camp c : camps) if (c.id == id) return c;
        return null;
    }

    /** Whether a camp's heart still stands (asleep, it does: it wakes when someone comes). */
    public static boolean heartStanding(ServerLevel level, Camp c) {
        if (c.cleared(PlayDays.current(level.getServer()))) return false;
        List<UUID> b = get(level.getServer()).bodies.get(c.id);
        if (b == null) return true;
        for (UUID id : b) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive() && e.entityTags().contains(HEART)) return true;
        }
        return false;
    }

    /** Cleared for good: the den never comes back (a village's Taken ended there). */
    public static void sealForGood(ServerLevel level, int id) {
        Camps cs = get(level.getServer());
        Camp c = cs.byId(id);
        if (c == null) return;
        if (!c.cleared(PlayDays.current(level.getServer()))) cs.clear(level, c, PlayDays.current(level.getServer()));
        c.clearedUntil = Long.MAX_VALUE / 4;
        cs.setDirty();
    }

    /**
     * A village's Burning: {@code count} of the camp's creatures come out of the dark at {@code from}, toward {@code target}
     * (at the camp's level for its tier, tagged as raiders). Returns them.
     */
    public static List<Entity> raidAt(ServerLevel level, int id, BlockPos from, BlockPos target, int count) {
        Camps cs = get(level.getServer());
        Camp c = cs.byId(id);
        List<Entity> out = new ArrayList<>();
        if (c == null || !level.hasChunkAt(from)) return out;
        BlockPos saved = c.pos;
        c.pos = CampPainter.surface(level, from.getX(), from.getZ());
        for (int i = 0; i < count; i++) {
            Entity e = cs.spawn(level, c, false);
            if (e == null) continue;
            e.addTag(RAIDER);
            if (e instanceof Mob m) m.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            out.add(e);
        }
        c.pos = saved;
        return out;
    }

    /** Dev, tours and tests (SPEC §28.7): a camp of this kind on the ground at a place (found within 48 of it), at a tier. */
    public static @Nullable Camp placeAt(ServerLevel level, String kind, BlockPos around, int tier, long seed) {
        Camps cs = get(level.getServer());
        BlockPos at = site(level, around, RandomSource.create(seed));
        if (at == null) return null;
        Camp c = cs.add(kind, kind.equals("riftborn") ? "awaken:ember_mines" : "", at, true, "", seed);
        c.tier = Math.max(1, Math.min(MAX_TIER, tier));
        cs.setDirty();
        return c;
    }

    /** Raiders out of a camp at a village (the Burning finds them by this tag). */
    public static final String RAIDER = "awaken_village_raider";

    /** Dev: camps near a player. */
    public static List<String> describe(ServerPlayer p) {
        long day = PlayDays.current(p.level().getServer());
        List<String> out = new ArrayList<>();
        for (Camp c : get(p.level().getServer()).camps) {
            int d = (int) Math.sqrt(p.distanceToSqr(Vec3.atCenterOf(c.pos)));
            if (d > 2000) continue;
            out.add(c.id + " " + c.name() + " tier " + c.tier + " · " + d + " m" + (c.resolved ? "" : " (unplaced)") + (c.cleared(day) ? " · cleared" : "")
                    + (c.raidDay >= 0 ? " · raids day " + c.raidDay : ""));
        }
        return out;
    }

    /** Dev and tours: a camp of this kind here (resolved, tier as given). */
    public static @Nullable Camp place(ServerPlayer p, String kind, int tier) {
        ServerLevel level = p.level().getServer().overworld();
        Camps cs = get(level.getServer());
        BlockPos at = site(level, p.blockPosition().offset(0, 0, -24), p.getRandom());
        if (at == null) return null;
        Camp c = cs.add(kind, kind.equals("riftborn") ? "awaken:ember_mines" : "", at, true, "", p.getRandom().nextLong());
        c.tier = Math.max(1, Math.min(MAX_TIER, tier));
        return c;
    }
}
