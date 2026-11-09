package dev.awaken.living.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.living.LivingContent;
import dev.awaken.living.api.Regions;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.living.region.RegionService;
import dev.awaken.progression.Progression;
import dev.awaken.terrain.api.CityBasin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where the Wayfarers are (SPEC §21, §43.1; saved data {@code awaken:living_scenes}, schema 2).
 * <ul>
 * <li><b>Residents</b> live in fixed places: Ilse alone at the Wayfarer camp, the Last Fire, up the High Gate road (in
 * classic worlds north of spawn); Hettie in Carillon's market square; Hale at the High Gate's yard (in a classic world, by
 * the camp). Placed once, when a player first comes near.</li>
 * <li><b>Scenes</b> are a chain's places (a den, a site) for a chain taken at a resident. Nobody is found in trouble out in
 * the wild any more (SPEC §43.1): the found scenes are gone.</li>
 * </ul>
 * Only loaded chunks are ever searched: placing someone never generates terrain. Schema 2 (§43.1): old found scenes are
 * dropped on load and Hettie is placed again in the city (her camp body leaves when it next loads: {@link Wayfarer}).
 */
public final class Scenes extends SavedData {
    static final int SCHEMA = 2;
    static final long NONE = Long.MIN_VALUE;
    static final int MIN_DIST = 64, MAX_DIST = 160;

    public record Scene(long id, String chain, String kind, long pos, Optional<UUID> owner, long day, Map<String, Long> markers) {
        public static final Codec<Scene> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Scene::id),
                Codec.STRING.fieldOf("chain").forGetter(Scene::chain),
                Codec.STRING.fieldOf("kind").forGetter(Scene::kind),
                Codec.LONG.fieldOf("pos").forGetter(Scene::pos),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Scene::owner),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Scene::day),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("markers", Map.of()).forGetter(Scene::markers)
        ).apply(i, Scene::new));
    }

    public static final Codec<Scenes> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Scene.CODEC.listOf().optionalFieldOf("scenes", List.of()).forGetter(s -> List.copyOf(s.scenes)),
            Codec.STRING.listOf().optionalFieldOf("residents", List.of()).forGetter(s -> List.copyOf(s.residents)),
            Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(s -> s.nextId),
            Codec.LONG.optionalFieldOf("camp").forGetter(s -> s.camp == NONE ? Optional.empty() : Optional.of(s.camp)),
            Codec.BOOL.optionalFieldOf("camp_built", false).forGetter(s -> s.campBuilt),
            Codec.BOOL.optionalFieldOf("camp_checked", false).forGetter(s -> s.campChecked)
    ).apply(i, (schema, scenes, residents, next, camp, built, checked) -> {
        Scenes s = new Scenes(scenes, residents, next, camp.orElse(NONE));
        s.campBuilt = built;
        s.campChecked = checked;
        if (schema < 2) {
            // §43.1: the found scenes are gone and Hettie lives in the city now
            s.scenes.removeIf(sc -> !sc.kind.equals("resident"));
            s.residents.remove("hettie");
            s.setDirty();
        }
        return s;
    }));

    public static final SavedDataType<Scenes> TYPE = new SavedDataType<>(Awaken.id("living_scenes"), Scenes::new, CODEC);

    final List<Scene> scenes;
    final List<String> residents;
    long nextId;
    /** The camp's ground once found (dry land nearest the nominal spot), else NONE. */
    long camp;
    /** Whether this camp's ground has been checked for a hole (or it was founded by the search that looks for one). */
    boolean campChecked;
    /** Whether the camp round the fire has been laid (bedrolls, logs, packs, the pot: {@link Camp#build}). */
    boolean campBuilt;

    public Scenes() {
        this(List.of(), List.of(), 1L, NONE);
    }

    Scenes(List<Scene> scenes, List<String> residents, long nextId, long camp) {
        this.scenes = new ArrayList<>(scenes);
        this.residents = new ArrayList<>(residents);
        this.nextId = nextId;
        this.camp = camp;
    }

    public static Scenes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static @Nullable Scene scene(MinecraftServer server, long id) {
        for (Scene s : get(server).scenes) if (s.id == id) return s;
        return null;
    }

    /** A named place of a scene (its Wayfarer's spot is "scene"), or null. */
    public static @Nullable BlockPos marker(MinecraftServer server, long id, String name) {
        // places that aren't a scene's own: the city's named spots and the Wayfarer camp
        if (name.startsWith("spot:")) return dev.awaken.core.api.CityHooks.spot(server.overworld(), name.substring(5));
        if (name.equals("camp")) {
            // before anyone has come near, the camp's nominal spot (the compass still knows the way: SPEC §27.6)
            long c = get(server).camp;
            return c == NONE ? campColumn(server).atY(server.overworld().getSeaLevel()) : BlockPos.of(c);
        }
        Scene s = scene(server, id);
        if (s == null) return null;
        if (name.isEmpty() || name.equals("scene")) return BlockPos.of(s.pos);
        Long m = s.markers.get(name);
        return m == null ? null : BlockPos.of(m);
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    /** Every 100 ticks: residents near players, the camp's smoke for those within sight of it. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        ServerLevel level = server.overworld();
        Scenes s = get(server);
        s.residents(level);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != level || p.isSpectator() || !Progression.profile(p).awakened()) continue;
            // the camp's smoke, seen from afar (vanilla campfire smoke only rises within ~32 blocks of you)
            BlockPos smoke = s.camp == NONE ? null : Camp.fire(server);
            if (smoke != null && smoke.distSqr(p.blockPosition().atY(smoke.getY())) < 1000 * 1000) {
                dev.awaken.core.AwakenNet.send(p, new dev.awaken.living.LivingNet.CampSmoke(smoke));
            }
        }
    }

    /** A chain taken at a resident has no scene yet: one is made for its places (a den, a site) around the player. */
    public static long sceneFor(ServerPlayer p, String chain) {
        ServerLevel level = p.level().getServer().overworld();
        Scenes s = get(level.getServer());
        for (Scene sc : s.scenes) if (sc.chain.equals(chain) && sc.owner.isPresent() && sc.owner.get().equals(p.getUUID())) return sc.id;
        Map<String, Long> markers = new HashMap<>();
        if (p.level() == level) markers.put("site", site(level, p.blockPosition(), p.getRandom()).asLong());
        Scene sc = new Scene(s.nextId++, chain, "resident", p.blockPosition().asLong(), Optional.of(p.getUUID()), PlayDays.current(level.getServer()), markers);
        s.scenes.add(sc);
        s.setDirty();
        return sc.id;
    }

    /** The chain finished: its scene goes (its Wayfarer leaves a little later). */
    public static void done(MinecraftServer server, long id) {
        Scenes s = get(server);
        Scene sc = scene(server, id);
        if (sc == null) return;
        s.scenes.remove(sc);
        s.setDirty();
    }

    // ---- places -----------------------------------------------------------------------------------------------------

    /**
     * A spot near {@code from} for a scene kind, in loaded chunks only: {@code cliff} a ledge with a drop of 6+ beside it;
     * {@code crag} high ground (y 100+); {@code site} plain ground 40-80 away; anything else ({@code road}, {@code camp})
     * flat open ground. Never in a Hearth's radius, the city reserve or water.
     */
    static @Nullable BlockPos findSpot(ServerLevel level, BlockPos from, String kind, RandomSource r) {
        return findSpot(level, from, kind, r, kind.equals("site") ? 40 : MIN_DIST, kind.equals("site") ? 80 : MAX_DIST);
    }

    /** Ground no scene may use: a Hearth's land or the city reserve. Tests swap it inside a try/finally. */
    interface Forbidden {
        boolean at(ServerLevel level, BlockPos pos);
    }

    static Forbidden forbidden = (level, pos) -> CityBasin.reserved(pos.getX(), pos.getZ()) || HearthHooks.inside(level, pos);

    static @Nullable BlockPos findSpot(ServerLevel level, BlockPos from, String kind, RandomSource r, int min, int max) {
        for (int tries = 0; tries < 48; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = from.getX() + (int) (Math.cos(a) * d), z = from.getZ() + (int) (Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at.below()).isSolid()) continue;
            if (forbidden.at(level, at)) continue;
            boolean ok = switch (kind) {
                case "cliff" -> drop(level, x, y, z) >= 6;
                case "crag" -> y >= 100;
                case "site" -> true;
                default -> drop(level, x, y, z) <= 1;
            };
            if (ok) return at;
        }
        return null;
    }

    /** A chain's site: a fitting spot when the land offers one, else plain ground 56 blocks off (a chain never stalls on it). */
    static BlockPos site(ServerLevel level, BlockPos from, RandomSource r) {
        BlockPos found = findSpot(level, from, "site", r);
        if (found != null) return found;
        double a = r.nextDouble() * Math.PI * 2;
        int x = from.getX() + (int) (Math.cos(a) * 56), z = from.getZ() + (int) (Math.sin(a) * 56);
        int y = level.hasChunkAt(new BlockPos(x, 0, z)) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : from.getY();
        return new BlockPos(x, y, z);
    }

    /** The bottom of a cliff spot: the lowest column beside it (where someone lowered on a rope lands). */
    static BlockPos foot(ServerLevel level, BlockPos top) {
        BlockPos best = top;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = top.getX() + dx, z = top.getZ() + dz;
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y < best.getY()) best = new BlockPos(x, y, z);
            }
        }
        return best;
    }

    /** A step moves the scene's Wayfarer to one of its places (Wren, lowered to the foot of her cliff). */
    public static void move(MinecraftServer server, long id, String marker) {
        Scene sc = scene(server, id);
        BlockPos to = marker(server, id, marker);
        if (sc == null || to == null) return;
        ServerLevel level = server.overworld();
        BlockPos at = BlockPos.of(sc.pos);
        if (!level.hasChunkAt(at)) return;
        for (Wayfarer w : level.getEntitiesOfClass(Wayfarer.class, new net.minecraft.world.phys.AABB(at).inflate(32), w -> w.scene() == id)) {
            w.snapTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, w.getYRot(), 0);
            w.become(w.who(), to, 3, id);
        }
    }

    /** The largest fall from this column to a neighbour within 2 blocks. */
    static int drop(ServerLevel level, int x, int y, int z) {
        int most = 0;
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                if (dx == 0 && dz == 0) continue;
                int nx = x + dx, nz = z + dz;
                LevelChunk chunk = level.getChunkSource().getChunkNow(nx >> 4, nz >> 4);
                if (chunk == null) continue;
                most = Math.max(most, y - (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx & 15, nz & 15) + 1));
            }
        }
        return most;
    }

    /** Dev: a Wayfarer with no scene, standing here. */
    public static void summon(ServerLevel level, String who, BlockPos at) {
        spawn(level, who, at, 3, 0);
    }

    static @Nullable Wayfarer spawn(ServerLevel level, String who, BlockPos at, int roam, long scene) {
        Wayfarer w = LivingContent.WAYFARER.get().create(level, EntitySpawnReason.EVENT);
        if (w == null) return null;
        w.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        w.become(who, at, roam, scene);
        level.addFreshEntity(w);
        return w;
    }

    // ---- residents --------------------------------------------------------------------------------------------------

    /** Where the Wayfarer camp is: up the High Gate road in a Carillon world, north of spawn in a classic one. */
    static BlockPos campColumn(MinecraftServer server) {
        var map = RegionService.map(server);
        boolean city = CityBasin.site(server).isPresent();
        return new BlockPos(map.centreX() + 6, 0, map.centreZ() - (city ? dev.awaken.core.api.TheLull.CAMP_CITY : dev.awaken.core.api.TheLull.CAMP_CLASSIC));
    }

    /** A resident Wayfarer ({@code ilse} at the camp, {@code hettie} in the city) in the loaded world near their home, or null. */
    public static @Nullable Wayfarer resident(MinecraftServer server, String who) {
        ServerLevel level = server.overworld();
        BlockPos at = who.equals("hettie") ? dev.awaken.core.api.CityHooks.spot(level, "hettie") : marker(server, 0, "camp");
        if (at == null || !level.hasChunkAt(at)) return null;
        var near = level.getEntitiesOfClass(Wayfarer.class, new net.minecraft.world.phys.AABB(at).inflate(48, 24, 48), w -> w.who().equals(who) && w.isAlive());
        return near.isEmpty() ? null : near.getFirst();
    }

    /** Dev and tours: the ground at a column near {@code at} (dry land within 6, else the top block). */
    public static BlockPos ground(ServerLevel level, BlockPos at) {
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        BlockPos dry = dryLand(level, at, 6);
        return dry != null ? dry : at.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
    }

    /** Where a resident stands now (the Now line's compass points at Ilse herself), or null. */
    public static @Nullable BlockPos residentAt(MinecraftServer server, String who) {
        Wayfarer w = resident(server, who);
        return w == null ? null : w.blockPosition();
    }

    /** Dev and tours: where to stand to see the camp (12 blocks south of it, on the ground; loads the chunk). */
    public static BlockPos campStand(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Scenes s = get(server);
        BlockPos base = s.camp != NONE ? BlockPos.of(s.camp) : campColumn(server);
        BlockPos c = base.offset(4, 0, 12);
        level.getChunk(c.getX() >> 4, c.getZ() >> 4);
        BlockPos dry = dryLand(level, c, 16);
        return dry != null ? dry : c.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING, c.getX(), c.getZ()));
    }

    // ---- the camp's ground (SPEC §27.6) ---------------------------------------------------------------------------------

    /** How far from its nominal spot the camp may stand. */
    static final int CAMP_REACH = 160;
    /** The land is read round a site at these distances, in eight directions. */
    private static final int[] LAND_RADII = {10, 18, 28, 40};
    private static final int LAND_LOOKS = LAND_RADII.length * 8;
    /** How many of those looks may rise clearly above the site, or fall clearly away from it. */
    static final int OFF_LAND = LAND_LOOKS / 4;
    /** More than this many and an old camp is in a hole, and is moved. */
    static final int IN_A_HOLE = LAND_LOOKS / 2;

    /** The end of the road the camp is reached from: it stops 26 short of the nominal spot, on the world's centre line. */
    static BlockPos roadEnd(BlockPos nominal) {
        return nominal.offset(-6, 0, 26);
    }

    /**
     * The nearest ground to {@code nominal} a camp can stand on: dry, level, at the height of the land round it, under open
     * sky, and walkable from {@code from} and seen from there. Never a pit, a ravine, a basin or a cave mouth: from any of
     * those the land stands clearly above you on most sides.
     *
     * <p>Every chunk the checks read must be finished, all the land out to 40 blocks round the site as well as its own. That
     * also keeps a later cave out: a chunk is only finished once it and all its neighbours have been through features, and
     * after that no sinkhole or cave mouth (they are structures, built as the chunk generates) writes into it again.
     *
     * <p>With {@code settle}, when nothing is good enough: the dry ground that sits best in its land. Null while the
     * nominal spot's own land is still generating (unless settling), so a far-off founder doesn't pull the camp toward them.
     */
    static @Nullable BlockPos campSite(ServerLevel level, BlockPos nominal, BlockPos from, int radius, boolean settle) {
        if (!settle && offLand(level, nominal.atY(0)) < 0) return null;
        BlockPos best = null;
        int bestOff = Integer.MAX_VALUE;
        for (int r = 0; r <= radius; r += 4) {
            int steps = r == 0 ? 1 : Math.max(8, (int) (2 * Math.PI * r / 6));
            for (int k = 0; k < steps; k++) {
                double a = 2 * Math.PI * k / steps;
                int x = nominal.getX() + (int) Math.round(Math.cos(a) * r), z = nominal.getZ() + (int) Math.round(Math.sin(a) * r);
                int y = top(level, x, z);
                if (y == Integer.MIN_VALUE) continue;
                BlockPos at = new BlockPos(x, y, z);
                if (!level.getFluidState(at.below()).isEmpty() || !level.getFluidState(at).isEmpty() || !level.getBlockState(at.below()).isSolid()) continue;
                if (drop(level, x, y, z) > 1 || forbidden.at(level, at)) continue;
                int off = offLand(level, at);
                if (off < 0) continue;
                if (off <= OFF_LAND && openSky(level, at) && walkIn(level, at, from)) return at;
                if (settle && off < bestOff) {
                    best = at;
                    bestOff = off;
                }
            }
        }
        return best;
    }

    /** The feet's height on a column (leaves don't count), or MIN_VALUE when its chunk isn't finished. */
    private static int top(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        return chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    /**
     * Of the looks at the land round this ground, how many stand clearly above it or fall clearly below it (the larger
     * count), or -1 while any of that land is still generating.
     */
    static int offLand(ServerLevel level, BlockPos at) {
        int y = at.getY() == 0 ? top(level, at.getX(), at.getZ()) : at.getY();
        if (y == Integer.MIN_VALUE) return -1;
        int above = 0, below = 0;
        for (int r : LAND_RADII) {
            for (int k = 0; k < 8; k++) {
                double a = Math.PI / 4 * k;
                int h = top(level, at.getX() + (int) Math.round(Math.cos(a) * r), at.getZ() + (int) Math.round(Math.sin(a) * r));
                if (h == Integer.MIN_VALUE) return -1;
                if (h > y + 3) above++;
                else if (h < y - 5) below++;
            }
        }
        return Math.max(above, below);
    }

    /** Nothing overhead, at the camp's ground or where its fire will go (leaves count here: a canopy hides the smoke). */
    private static boolean openSky(ServerLevel level, BlockPos at) {
        for (BlockPos q : new BlockPos[]{at, at.offset(-3, 0, 3)}) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(q.getX() >> 4, q.getZ() >> 4);
            if (chunk == null || chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, q.getX() & 15, q.getZ() & 15) + 1 > at.getY() + 1) return false;
        }
        return true;
    }

    /** You could walk here from {@code from} in a straight line, no climbs or drops or wide water, and see it on the way. */
    private static boolean walkIn(ServerLevel level, BlockPos at, BlockPos from) {
        int dx = from.getX() - at.getX(), dz = from.getZ() - at.getZ(), n = Math.max(Math.abs(dx), Math.abs(dz));
        if (n == 0) return true;
        int end = top(level, from.getX(), from.getZ());
        if (end == Integer.MIN_VALUE) return false;
        int prev = at.getY(), snags = 0, hidden = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= n; i++) {
            int x = at.getX() + dx * i / n, z = at.getZ() + dz * i / n, h = top(level, x, z);
            if (h == Integer.MIN_VALUE) return false;
            m.set(x, h - 1, z);
            if (level.getBlockState(m).is(net.minecraft.tags.BlockTags.LOGS)) continue; // a trunk: you walk round it
            if (h - prev >= 4 || prev - h >= 6) return false;
            if (h - prev > 1 || !level.getFluidState(m).isEmpty()) snags++;
            double eye = at.getY() + 2 + (end - at.getY()) * (double) i / n;
            if (h > eye + 1) hidden++;
            prev = h;
        }
        return snags <= 2 + n / 24 && hidden <= n / 8;
    }

    /**
     * The nearest dry, level, solid ground to {@code around} within {@code radius} (rings 8 blocks apart, finished chunks
     * only, never the city reserve), or null.
     */
    static @Nullable BlockPos dryLand(ServerLevel level, BlockPos around, int radius) {
        return dryLand(level, around, radius, false);
    }

    /** As {@link #dryLand(ServerLevel, BlockPos, int)}; {@code cityOk}: the city reserve may be used (Hale's yard). */
    static @Nullable BlockPos dryLand(ServerLevel level, BlockPos around, int radius, boolean cityOk) {
        for (int r = 0; r <= radius; r += 4) {
            int steps = r == 0 ? 1 : Math.max(8, (int) (2 * Math.PI * r / 6));
            for (int k = 0; k < steps; k++) {
                double a = 2 * Math.PI * k / steps;
                int x = around.getX() + (int) Math.round(Math.cos(a) * r), z = around.getZ() + (int) Math.round(Math.sin(a) * r);
                if (!cityOk && CityBasin.reserved(x, z)) continue;
                // finished chunks only: one still generating (land a player flies over) would be waited for on the server thread
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) continue;
                int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
                BlockPos at = new BlockPos(x, y, z);
                if (!level.getFluidState(at.below()).isEmpty() || !level.getFluidState(at).isEmpty() || !level.getBlockState(at.below()).isSolid()) continue;
                if (drop(level, x, y, z) > 1 || HearthHooks.inside(level, at)) continue;
                return at;
            }
        }
        return null;
    }

    /** Hale's spot in a classic world: by the camp (a Carillon world uses the city's quartermaster spot). */
    static BlockPos haleColumn(MinecraftServer server) {
        return campColumn(server).offset(-5, 0, 3);
    }

    private void residents(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (!residents.contains("ilse")) {
            BlockPos nominal = campColumn(server);
            // founded while still a column of smoke in the distance (render distance 16), not when you're at its edge
            if (camp == NONE && near(level, nominal, 256)) {
                // the nearest good ground to its nominal spot (a river, a bay or a hole may sit there). Someone at the
                // spot and nothing good in reach (a mountain's side): the best there is, so Ilse is never missing
                BlockPos found = campSite(level, nominal, roadEnd(nominal), CAMP_REACH, near(level, nominal, 64));
                if (found != null) {
                    camp = found.asLong();
                    campChecked = true;
                    setDirty();
                }
            }
            if (camp != NONE) {
                BlockPos c = BlockPos.of(camp);
                place(level, "ilse", c, 0, false);
            }
        }
        if (camp != NONE && !campChecked) regroundCamp(level);
        if (camp != NONE) {
            campFire(level, BlockPos.of(camp));
            BlockPos fire = campBuilt ? null : Camp.fire(server);
            if (fire != null && near(level, fire, 96)) {
                Camp.build(level, fire);
                campBuilt = true;
                setDirty();
            }
        }
        // Hettie lives in the market square (SPEC §43.1; a classic world has no city, and no Hettie)
        BlockPos square = dev.awaken.core.api.CityHooks.spot(level, "hettie");
        if (square != null) placeAt(level, "hettie", square);
        // Hale stands on Carillon's quartermaster spot (kept clear by the city); in a classic world, by the camp
        BlockPos post = dev.awaken.core.api.CityHooks.spot(level, "quartermaster");
        if (post != null) placeAt(level, "hale", post);
        else if (CityBasin.site(server).isEmpty()) place(level, "hale", haleColumn(server), 24, true);
        haleRack(level, post != null ? post : haleColumn(server));
    }

    /** Whether Hale's rack has been looked for this session (arms places it beside him once, and finds it again after a restart). */
    private boolean haleRackDone;

    /** Hale's rack (docs/arms-progression/PLAN.md §3.3) stands beside him, once he is in the world and a player is near. */
    private void haleRack(ServerLevel level, BlockPos anchor) {
        if (haleRackDone || !residents.contains("hale") || !near(level, anchor, 96)) return;
        var found = level.getEntitiesOfClass(Wayfarer.class, new net.minecraft.world.phys.AABB(anchor).inflate(32), w -> w.who().equals("hale") && w.isAlive());
        if (found.isEmpty()) return;
        haleRackDone = dev.awaken.core.api.ArmsApi.placeHaleRack(level, found.getFirst().blockPosition());
    }

    /** A resident exactly here (a spot another module keeps clear), once a player is within 96. */
    private void placeAt(ServerLevel level, String who, BlockPos at) {
        if (residents.contains(who) || !level.hasChunkAt(at) || !near(level, at, 96)) return;
        if (spawn(level, who, at, 2, 0) != null) {
            residents.add(who);
            setDirty();
            Awaken.LOG.info("[living] resident {} placed on its spot {}", who, at.toShortString());
        }
    }

    private static boolean near(ServerLevel level, BlockPos column, int dist) {
        return level.players().stream().anyMatch(p -> p.blockPosition().distSqr(column.atY(p.getBlockY())) < (double) dist * dist);
    }

    /** A camp founded before the search knew about holes: moved once, if it's in one, the first time someone comes near. */
    private void regroundCamp(ServerLevel level) {
        BlockPos c = BlockPos.of(camp);
        if (!near(level, c, 64)) return;
        int off = offLand(level, c);
        if (off < 0) return;
        campChecked = true;
        setDirty();
        if (off <= IN_A_HOLE) return;
        MinecraftServer server = level.getServer();
        BlockPos nominal = campColumn(server);
        BlockPos to = campSite(level, nominal, roadEnd(nominal), CAMP_REACH, true);
        if (to == null || offLand(level, to) >= off) return;
        Wayfarer ilse = resident(server, "ilse");
        BlockPos oldFire = Camp.fire(server);
        if (oldFire != null) level.removeBlock(oldFire, false);
        camp = to.asLong();
        campBuilt = false;
        Camp.forget();
        if (ilse != null) {
            ilse.snapTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, ilse.getYRot(), 0);
            ilse.become("ilse", to, 3, 0);
        } else {
            residents.remove("ilse");
        }
        Awaken.LOG.info("[living] the camp was in a hole at {}: moved to {}", c.toShortString(), to.toShortString());
    }

    /** A resident at the nearest dry ground within {@code search} of {@code column}, once a player is within 96. */
    private void place(ServerLevel level, String who, BlockPos column, int search, boolean cityOk) {
        if (residents.contains(who) || !level.hasChunkAt(column) || !near(level, column, 96)) return;
        BlockPos at = dryLand(level, column, search, cityOk);
        if (at == null) return;
        if (spawn(level, who, at, 3, 0) != null) {
            residents.add(who);
            setDirty();
            Awaken.LOG.info("[living] resident {} placed at {}", who, at.toShortString());
        }
    }

    /**
     * The camp's fire (SPEC §27.6): a signal fire on a bale by Ilse, its smoke a column you see from the High Gate, lit
     * once a player is within 256 (before they can make out the camp) and lit again if it's gone. The camp had no structure, no smoke, nothing to walk toward.
     */
    private static void campFire(ServerLevel level, BlockPos camp) {
        if (!near(level, camp, 256)) return;
        BlockPos column = camp.offset(-3, 0, 3);
        // the scan's chunks finished, as dryLand's: one still generating would be waited for on the server thread (and a
        // fire there missed, so lit twice)
        for (int cx = camp.getX() - 6 >> 4; cx <= camp.getX() + 6 >> 4; cx++) for (int cz = camp.getZ() - 6 >> 4; cz <= camp.getZ() + 6 >> 4; cz++) {
            if (level.getChunkSource().getChunkNow(cx, cz) == null) return;
        }
        for (BlockPos q : BlockPos.betweenClosed(camp.offset(-6, -3, -6), camp.offset(6, 3, 6))) {
            if (level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.CAMPFIRE)) return;
        }
        BlockPos fire = dryLand(level, column, 6);
        if (fire == null) return;
        Camp.forget();
        level.setBlock(fire.below(), net.minecraft.world.level.block.Blocks.HAY_BLOCK.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.setBlock(fire, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.SIGNAL_FIRE, true), net.minecraft.world.level.block.Block.UPDATE_ALL);
        Awaken.LOG.info("[living] the camp's fire lit at {}", fire.toShortString());
    }

    /** Whether this player has stood at the camp (once: counter {@code awaken:camp_reached}). */
    static final String CAMP_REACHED = "awaken:camp_reached";

    // ---- the Horizon line ---------------------------------------------------------------------------------------------

    /** The Last Fire on the Horizon line until you've stood at it. */
    static List<HorizonHooks.Site> sites(ServerPlayer p) {
        if (!Regions.on(p.level())) return List.of();
        List<HorizonHooks.Site> out = new ArrayList<>();
        // the Wayfarer camp, until you've been: the first place past the walls with people in it (SPEC §27.6)
        var prof = dev.awaken.progression.Progression.profile(p);
        if (prof.counter(CAMP_REACHED) == 0 && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            BlockPos camp = marker(p.level().getServer(), 0, "camp");
            if (camp != null) {
                if (camp.distSqr(p.blockPosition().atY(camp.getY())) < 24 * 24) dev.awaken.progression.Progression.setCounter(p, CAMP_REACHED, 1);
                else if (camp.distSqr(p.blockPosition().atY(camp.getY())) < 1200 * 1200) out.add(new HorizonHooks.Site("living:camp", "The Last Fire", camp, "", false));
            }
        }
        return out;
    }

    public static void init() {
        HorizonHooks.register(Scenes::sites);
        // the Lull's ridge is measured from the region map's centre: the city's, or spawn's (SPEC §43.1)
        dev.awaken.core.api.TheLull.install(s -> {
            var map = RegionService.map(s);
            return new BlockPos(map.centreX(), 0, map.centreZ());
        }, s -> CityBasin.site(s).isPresent());
    }

    /** A chain was dropped: the scene made for its places goes. */
    public static void dropped(MinecraftServer server, long id) {
        Scene sc = scene(server, id);
        if (sc != null && sc.kind.equals("resident")) done(server, id);
    }

    /** A step brings creatures to its scene (raiders on the road, the den's hunters): at the marker, if it's loaded. */
    public static void bring(ServerPlayer p, long scene, QuestDef.Spawn spawn) {
        ServerLevel level = p.level().getServer().overworld();
        BlockPos at = marker(level.getServer(), scene, spawn.at());
        if (at == null || p.level() != level || !level.hasChunkAt(at)) return;
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(spawn.entity())).orElse(null);
        if (type == null) {
            Awaken.LOG.warn("[living] scene spawn {} doesn't exist", spawn.entity());
            return;
        }
        RandomSource r = level.getRandom();
        for (int i = 0; i < spawn.count(); i++) {
            int x = at.getX() + r.nextInt(7) - 3, z = at.getZ() + r.nextInt(7) - 3;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            Entity e = type.spawn(level, pos, EntitySpawnReason.EVENT);
            if (e instanceof Mob mob) mob.setPersistenceRequired();
        }
    }
}
