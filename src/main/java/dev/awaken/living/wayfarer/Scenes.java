package dev.awaken.living.wayfarer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.living.LivingContent;
import dev.awaken.living.api.Regions;
import dev.awaken.living.quest.QuestBook;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import dev.awaken.living.region.RegionService;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Requirements;
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
 * Where the Wayfarers are (SPEC §21, Phase C; saved data {@code awaken:living_scenes}, schema 1). Two kinds:
 * <ul>
 * <li><b>Residents</b> live in fixed places: Ilse and Hettie at the Wayfarer camp up the High Gate road (in classic worlds
 * north of spawn), Hale at the High Gate's yard (until Carillon's spot exists, at the camp). Placed once, when a player
 * first comes near.</li>
 * <li><b>Scenes</b> are found: for one player, now and then (every ~25 minutes outside, never in a Hearth or a gate), a
 * Wayfarer in trouble appears 64-160 blocks away, in land that suits the chain (a cliff ledge, a road, high crags).
 * The Horizon line names it; a scene nobody takes fades after two play-days.</li>
 * </ul>
 * Only loaded chunks are ever searched: finding a scene never generates terrain.
 */
public final class Scenes extends SavedData {
    static final int SCHEMA = 1;
    static final long NONE = Long.MIN_VALUE;
    static final long SCENE_EVERY = 20L * 60 * 25;
    static final int MIN_DIST = 64, MAX_DIST = 160;
    static final int CAMP_DIST_CITY = 450, CAMP_DIST_CLASSIC = 300;

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
            Codec.BOOL.optionalFieldOf("camp_built", false).forGetter(s -> s.campBuilt)
    ).apply(i, (schema, scenes, residents, next, camp, built) -> {
        Scenes s = new Scenes(scenes, residents, next, camp.orElse(NONE));
        s.campBuilt = built;
        return s;
    }));

    public static final SavedDataType<Scenes> TYPE = new SavedDataType<>(Awaken.id("living_scenes"), Scenes::new, CODEC);

    final List<Scene> scenes;
    final List<String> residents;
    long nextId;
    /** The camp's ground once found (dry land nearest the nominal spot), else NONE. */
    long camp;
    /** Whether the camp round the fire has been laid (bedrolls, logs, packs, the pot: {@link Camp#build}). */
    boolean campBuilt;
    private final transient Map<UUID, Long> lastScene = new HashMap<>();

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

    /** Every 100 ticks: residents near players, new scenes, old scenes faded. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) return;
        ServerLevel level = server.overworld();
        Scenes s = get(server);
        s.residents(level);
        Camp.rest(server);
        long day = PlayDays.current(server);
        boolean changed = s.scenes.removeIf(sc -> sc.owner.isPresent() && day - sc.day >= 2 && !taken(server, sc) && fade(level, sc));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level() != level || p.isSpectator() || !Progression.profile(p).awakened()) continue;
            changed |= s.maybeScene(level, p, day);
            dev.awaken.core.AwakenNet.send(p, new dev.awaken.living.LivingNet.Tells(s.tells(p)));
            // the camp's smoke, seen from afar (vanilla campfire smoke only rises within ~32 blocks of you)
            BlockPos smoke = s.camp == NONE ? null : Camp.fire(server);
            if (smoke != null && smoke.distSqr(p.blockPosition().atY(smoke.getY())) < 1000 * 1000) {
                dev.awaken.core.AwakenNet.send(p, new dev.awaken.living.LivingNet.CampSmoke(smoke));
            }
        }
        if (changed) s.setDirty();
    }

    /** The player's untaken scenes within 400 blocks: they send up flares (the Hilltop Test). */
    private List<BlockPos> tells(ServerPlayer p) {
        List<BlockPos> out = new ArrayList<>(2);
        QuestState qs = QuestEngine.state(p);
        for (Scene sc : scenes) {
            if (sc.owner.isEmpty() || !sc.owner.get().equals(p.getUUID()) || sc.kind.equals("resident") || qs.has(sc.chain)) continue;
            BlockPos at = BlockPos.of(sc.pos);
            if (at.distSqr(p.blockPosition()) < 400 * 400 && out.size() < 8) out.add(at);
        }
        return out;
    }

    private static boolean taken(MinecraftServer server, Scene sc) {
        ServerPlayer owner = sc.owner.map(u -> server.getPlayerList().getPlayer(u)).orElse(null);
        return owner != null && QuestEngine.state(owner).has(sc.chain);
    }

    private static boolean fade(ServerLevel level, Scene sc) {
        BlockPos at = BlockPos.of(sc.pos);
        if (level.hasChunkAt(at)) {
            for (Wayfarer w : level.getEntitiesOfClass(Wayfarer.class, new net.minecraft.world.phys.AABB(at).inflate(16), w -> w.scene() == sc.id)) w.discard();
        }
        return true;
    }

    /** For one player: a new scene when it's been long enough, a chain they can take exists, and the land offers a spot. */
    private boolean maybeScene(ServerLevel level, ServerPlayer p, long day) {
        long now = level.getGameTime();
        Long last = lastScene.get(p.getUUID());
        if (last == null) {
            lastScene.put(p.getUUID(), now - SCENE_EVERY / 2); // the first one comes sooner
            return false;
        }
        if (now - last < SCENE_EVERY) return false;
        if (HearthHooks.inside(level, p.blockPosition()) || CityBasin.reserved(p.getBlockX(), p.getBlockZ())) return false;
        for (Scene sc : scenes) if (sc.owner.isPresent() && sc.owner.get().equals(p.getUUID())) return false;
        QuestState qs = QuestEngine.state(p);
        String chain = null;
        QuestDef def = null;
        for (var e : QuestBook.all().entrySet()) {
            QuestDef d = e.getValue();
            if (d.scene().equals("resident") || qs.has(e.getKey()) || qs.done().contains(e.getKey()) || !Requirements.met(p, d.requires())) continue;
            if (def == null || d.band() < def.band()) {
                chain = e.getKey();
                def = d;
            }
        }
        if (def == null) return false;
        BlockPos spot = findSpot(level, p.blockPosition(), def.scene(), p.getRandom());
        lastScene.put(p.getUUID(), now - (spot == null ? SCENE_EVERY - 20L * 60 : 0)); // nothing suitable: try again in a minute
        if (spot == null) return false;
        open(level, p, chain, def, spot, day);
        return true;
    }

    private Scene open(ServerLevel level, ServerPlayer p, String chain, QuestDef def, BlockPos spot, long day) {
        Map<String, Long> markers = new HashMap<>();
        markers.put("site", site(level, spot, p.getRandom()).asLong());
        if (def.scene().equals("cliff")) markers.put("foot", foot(level, spot).asLong());
        Scene sc = new Scene(nextId++, chain, def.scene(), spot.asLong(), Optional.of(p.getUUID()), day, markers);
        scenes.add(sc);
        setDirty();
        spawn(level, def.giver(), spot, def.scene().equals("cliff") ? 1 : 4, sc.id);
        Awaken.LOG.info("[living] scene {} for {}: {} at {}", sc.id, p.getName().getString(), chain, spot.toShortString());
        return sc;
    }

    /** Dev and tours: a chain's scene now, close by (10-28 blocks; straight ahead if the land offers no fitting spot). */
    public static @Nullable BlockPos force(ServerPlayer p, String chain) {
        QuestDef def = QuestBook.get(chain);
        ServerLevel level = p.level().getServer().overworld();
        if (def == null || p.level() != level) return null;
        BlockPos spot = findSpot(level, p.blockPosition(), def.scene(), p.getRandom(), 10, 28);
        if (spot == null) spot = dryLand(level, p.blockPosition().offset(0, 0, -16), 64);
        if (spot == null) {
            var ahead = p.getLookAngle().multiply(1, 0, 1).normalize().scale(12);
            int x = p.getBlockX() + (int) ahead.x, z = p.getBlockZ() + (int) ahead.z;
            spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        }
        Scenes s = get(level.getServer());
        Scene sc = s.open(level, p, QuestBook.all().containsKey(chain) ? chain : Awaken.MODID + ":" + chain, def, spot, PlayDays.current(level.getServer()));
        return BlockPos.of(sc.pos);
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
                if (dx == 0 && dz == 0 || !level.hasChunkAt(new BlockPos(x + dx, 0, z + dz))) continue;
                most = Math.max(most, y - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz));
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
        return new BlockPos(map.centreX() + 6, 0, map.centreZ() - (city ? CAMP_DIST_CITY : CAMP_DIST_CLASSIC));
    }

    /** A resident Wayfarer ({@code ilse}, {@code hettie}) in the loaded world near the camp, or null. */
    public static @Nullable Wayfarer resident(MinecraftServer server, String who) {
        ServerLevel level = server.overworld();
        BlockPos at = marker(server, 0, "camp");
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

    /** Dev and tours: the player's newest open scene, or null. */
    public static @Nullable BlockPos newest(ServerPlayer p) {
        Scene best = null;
        for (Scene sc : get(p.level().getServer()).scenes) {
            if (sc.owner.isPresent() && sc.owner.get().equals(p.getUUID()) && !sc.kind.equals("resident") && (best == null || sc.id > best.id)) best = sc;
        }
        return best == null ? null : BlockPos.of(best.pos);
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

    /**
     * The nearest dry, level, solid ground to {@code around} within {@code radius} (rings 8 blocks apart, loaded chunks
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
                if (!level.hasChunkAt(new BlockPos(x, 0, z)) || !cityOk && CityBasin.reserved(x, z)) continue;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
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
        if (!residents.contains("ilse") || !residents.contains("hettie")) {
            BlockPos nominal = campColumn(server);
            // founded while still a column of smoke in the distance (render distance 16), not when you're at its edge
            if (camp == NONE && near(level, nominal, 256)) {
                // the camp stands on dry land: the nearest to its nominal spot (a river or a bay may sit there)
                BlockPos found = dryLand(level, nominal, 160);
                if (found != null) {
                    camp = found.asLong();
                    setDirty();
                }
            }
            if (camp != NONE) {
                BlockPos c = BlockPos.of(camp);
                place(level, "ilse", c, 0, false);
                place(level, "hettie", c.offset(9, 0, -4), 12, false);
            }
        }
        if (camp != NONE) {
            campFire(level, BlockPos.of(camp));
            BlockPos fire = campBuilt ? null : Camp.fire(server);
            if (fire != null && near(level, fire, 96)) {
                Camp.build(level, fire);
                campBuilt = true;
                setDirty();
            }
        }
        // Hale stands on Carillon's quartermaster spot (kept clear by the city); in a classic world, by the camp
        BlockPos post = dev.awaken.core.api.CityHooks.spot(level, "quartermaster");
        if (post != null) placeAt(level, "hale", post);
        else if (CityBasin.site(server).isEmpty()) place(level, "hale", haleColumn(server), 24, true);
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
        if (!level.hasChunkAt(column)) return;
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

    /** The open scene meant for this player, while it waits (the Horizon line names it: "Wren, stranded"). */
    static List<HorizonHooks.Site> sites(ServerPlayer p) {
        if (!Regions.on(p.level())) return List.of();
        List<HorizonHooks.Site> out = new ArrayList<>();
        // the Wayfarer camp, until you've been: the first place past the walls with people in it (SPEC §27.6)
        var prof = dev.awaken.progression.Progression.profile(p);
        if (prof.counter(CAMP_REACHED) == 0 && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            BlockPos camp = marker(p.level().getServer(), 0, "camp");
            if (camp != null) {
                if (camp.distSqr(p.blockPosition().atY(camp.getY())) < 24 * 24) dev.awaken.progression.Progression.setCounter(p, CAMP_REACHED, 1);
                else if (camp.distSqr(p.blockPosition().atY(camp.getY())) < 1200 * 1200) out.add(new HorizonHooks.Site("living:camp", "Wayfarers' fire", camp, "", false));
            }
        }
        QuestState qs = QuestEngine.state(p);
        for (Scene sc : get(p.level().getServer()).scenes) {
            if (sc.owner.isEmpty() || !sc.owner.get().equals(p.getUUID()) || sc.kind.equals("resident")) continue;
            QuestDef d = QuestBook.get(sc.chain);
            if (d == null || qs.has(sc.chain)) continue;
            BlockPos at = BlockPos.of(sc.pos);
            if (at.distSqr(p.blockPosition()) > 300 * 300) continue;
            Wayfarers.Person who = Wayfarers.get(d.giver());
            String need = sc.kind.equals("cliff") ? HorizonHooks.TETHER : "";
            out.add(new HorizonHooks.Site("scene/" + sc.id, (who == null ? d.giver() : who.name()) + ", " + hook(sc.kind), at, need, true));
        }
        return out;
    }

    private static String hook(String kind) {
        return switch (kind) {
            case "cliff" -> "stranded";
            case "road" -> "on the road";
            case "crag" -> "up the crags";
            default -> "waiting";
        };
    }

    public static void init() {
        HorizonHooks.register(Scenes::sites);
    }

    /** A chain was dropped: a scene made for a resident's chain goes; a found scene stays to be taken again. */
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
