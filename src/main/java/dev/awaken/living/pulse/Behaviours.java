package dev.awaken.living.pulse;

import dev.awaken.Awaken;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.mobs.MobTypes;
import dev.awaken.terrain.api.CityBasin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** The behaviours by id, and what they share: finding a place, bringing creatures, moving them along a line. */
public final class Behaviours {
    private Behaviours() {}

    private static final Map<String, Behaviour> ALL = new HashMap<>();

    static {
        ALL.put("migration", new Herds.Migration());
        ALL.put("stampede", new Herds.Stampede());
        ALL.put("murmuration", new Skies.Murmuration());
        ALL.put("carrion_circle", new Skies.CarrionCircle());
        ALL.put("rolling_storm", new Skies.RollingStorm());
        ALL.put("wild_hunt", new Hunts.WildHunt());
        ALL.put("beast_duel", new Hunts.BeastDuel());
        ALL.put("deep_tide", new Tides.DeepTide());
        ALL.put("caravan", new People.Caravan());
    }

    public static @Nullable Behaviour get(String id) {
        return ALL.get(id);
    }

    // ---- the land ---------------------------------------------------------------------------------------------------

    /** The fauna biome groups (their tags {@code #awaken:fauna/<group>[_...]}). */
    static final List<String> GROUPS = List.of("greenwild", "greenwild_woods", "frostwild", "frostwild_taiga", "sunscorch", "crag",
            "blossom", "blossom_meadow", "canopy", "duskwood", "mire", "spore", "tidewater_rivers", "tidewater_lakes");
    private static final Map<String, TagKey<Biome>> TAGS = new HashMap<>();

    /** The fauna group here, by its main name (greenwild_woods is greenwild), or "" for none. */
    static String group(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        for (String g : GROUPS) {
            TagKey<Biome> tag = TAGS.computeIfAbsent(g, k -> TagKey.create(Registries.BIOME, Awaken.id("fauna/" + k)));
            if (biome.is(tag)) {
                int cut = g.indexOf('_');
                return g.startsWith("tidewater") ? "tidewater" : cut > 0 ? g.substring(0, cut) : g;
            }
        }
        return "";
    }

    static boolean groupFits(HappeningDef def, String group) {
        return def.groups().isEmpty() || def.groups().contains(group);
    }

    /** The ground column top here (motion-blocking, no leaves). */
    static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Never a Hearth's land, the city, near a gate or in an unloaded chunk. */
    static boolean allowed(ServerLevel level, BlockPos at) {
        if (!level.hasChunkAt(at) || CityBasin.reserved(at.getX(), at.getZ())) return false;
        if (HearthHooks.inside(level, at)) return false;
        return GateHooks.near(level, at, 32).isEmpty();
    }

    /** Dry, solid, roughly level ground here. */
    static boolean dry(ServerLevel level, BlockPos at) {
        return level.getFluidState(at).isEmpty() && level.getFluidState(at.below()).isEmpty() && level.getBlockState(at.below()).isSolid();
    }

    /** The largest step from this column to its neighbours four blocks off. */
    static int rough(ServerLevel level, BlockPos at) {
        int most = 0;
        for (int[] d : new int[][]{{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            BlockPos n = new BlockPos(at.getX() + d[0], 0, at.getZ() + d[1]);
            if (!level.hasChunkAt(n)) continue;
            most = Math.max(most, Math.abs(surface(level, n.getX(), n.getZ()).getY() - at.getY()));
        }
        return most;
    }

    /**
     * A place {@code min}..{@code max} blocks from the player in the land {@code def} wants (its groups), allowed and
     * passing {@code ok}; null when 40 tries find none.
     */
    static @Nullable BlockPos spot(ServerLevel level, ServerPlayer p, HappeningDef def, int min, int max, RandomSource r, Predicate<BlockPos> ok) {
        for (int i = 0; i < 40; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            int x = p.getBlockX() + (int) (Math.cos(a) * d), z = p.getBlockZ() + (int) (Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos at = surface(level, x, z);
            if (!allowed(level, at) || !groupFits(def, group(level, at)) || !ok.test(at)) continue;
            return at;
        }
        return null;
    }

    /**
     * A line through {@code centre} across the player's view (so it passes in front of them), {@code half} blocks each
     * way, its ends on the surface (or at the centre's height where unloaded).
     */
    static Behaviour.Plan across(ServerLevel level, ServerPlayer p, BlockPos centre, int half) {
        double dx = centre.getX() - p.getX(), dz = centre.getZ() - p.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        double px = -dz / len, pz = dx / len;
        if (p.getRandom().nextBoolean()) {
            px = -px;
            pz = -pz;
        }
        BlockPos a = end(level, centre, -px * half, -pz * half), b = end(level, centre, px * half, pz * half);
        return new Behaviour.Plan(a, b);
    }

    private static BlockPos end(ServerLevel level, BlockPos centre, double ox, double oz) {
        int x = centre.getX() + (int) ox, z = centre.getZ() + (int) oz;
        return level.hasChunkAt(new BlockPos(x, 0, z)) ? surface(level, x, z) : new BlockPos(x, centre.getY(), z);
    }

    // ---- creatures --------------------------------------------------------------------------------------------------

    /** One of the happening's creatures here (persistent, tagged, listed); null if it couldn't be made. */
    static @Nullable Entity bring(ServerLevel level, Happening h, EntityType<?> type, BlockPos at) {
        if (type == null || !level.hasChunkAt(at)) return null;
        Entity e = type.spawn(level, at, EntitySpawnReason.EVENT);
        if (e == null) return null;
        if (e instanceof Mob m) m.setPersistenceRequired();
        e.addTag(Pulse.TAG);
        h.participants.add(e.getUUID());
        return e;
    }

    static @Nullable EntityType<?> fauna(String id) {
        return MobTypes.type(id);
    }

    /** Somewhere near {@code at} for one of a group: a ring of {@code spread}, on the surface. */
    static BlockPos near(ServerLevel level, BlockPos at, int spread, RandomSource r) {
        int x = at.getX() + r.nextInt(spread * 2 + 1) - spread, z = at.getZ() + r.nextInt(spread * 2 + 1) - spread;
        return level.hasChunkAt(new BlockPos(x, 0, z)) ? surface(level, x, z) : at;
    }

    /**
     * Its creatures walk the line: each heads for the point {@code lead} ahead of where the happening is now, spread a
     * little so they move as a body. The centre follows the ones that are loaded (or the line, while none is).
     */
    static void walk(ServerLevel level, Happening h, float t, float lead, double speed, int spread) {
        Vec3 target = h.along(Math.min(1, t + lead));
        double sx = 0, sy = 0, sz = 0;
        int n = 0, k = 0;
        for (var id : h.participants) {
            Entity e = level.getEntity(id);
            k++;
            if (!(e instanceof PathfinderMob m) || !m.isAlive()) continue;
            sx += m.getX();
            sy += m.getY();
            sz += m.getZ();
            n++;
            if (m.getTarget() != null) continue; // busy fighting
            double ox = ((k * 37) % (spread * 2 + 1)) - spread, oz = ((k * 53) % (spread * 2 + 1)) - spread;
            m.getNavigation().moveTo(target.x + ox, target.y, target.z + oz, speed);
        }
        h.centre = n > 0 ? new Vec3(sx / n, sy / n, sz / n) : h.along(t);
    }

    /** Living creatures still with the happening. */
    static int alive(ServerLevel level, Happening h) {
        int n = 0;
        for (var id : h.participants) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) n++;
        }
        return n;
    }

    /** Leaving: creatures no player is near go now; the others stay and become ordinary creatures (untagged). */
    static void leave(ServerLevel level, Happening h, int stayIfWithin) {
        for (var id : h.participants) {
            Entity e = level.getEntity(id);
            if (e == null || !e.isAlive()) continue;
            if (level.getNearestPlayer(e, stayIfWithin) == null) e.discard();
            else {
                e.removeTag(Pulse.TAG);
                if (e instanceof Mob m) m.setPersistenceRequired();
            }
        }
        h.participants.clear();
    }
}
