package dev.awaken.world.stilled;

import dev.awaken.citizens.api.Settlement;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.RoadHooks;
import dev.awaken.core.api.TheLull;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.village.api.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sets the Stilled down as players come near (SPEC §43.3). A spot is worked out once a player is within
 * {@link #RESOLVE} (beside its road, out at its settlement's edge on the city's side, or dropped) and the figure is placed
 * once one is within {@link #NEAR} and its ground is loaded: natural ground with room for them, never within 150 of
 * another, never in the city, inside the ridge (a cell's), in a Hearth or on a settlement's ground. Once placed or
 * dropped, a spot is never tried again.
 */
final class StilledPlacer {
    private StilledPlacer() {}

    static final int CHECK = 100, RESOLVE = 640, NEAR = 96;
    /** A road spot's figure sits this far to the road's side. */
    static final int ROADSIDE = 4;
    /** How far a spot's road is looked for (the north road's own; a cell's, past an empty settlement). */
    static final int ROAD_FIXED = 40, ROAD_CELL = 300;
    /** An empty settlement this near a cell's spot is where its figure sits; a key's chest is looked for this far. */
    static final int HOME_NEAR = 400, KEY_REACH = 1200;
    /** Room out from a settlement's ground, and how far the ground is searched round the chosen point. */
    static final int PAST_GROUND = 8, SEARCH = 6;

    /** A spot worked out: where the figure goes and which way they face (in memory only: it follows from the seed). */
    record Ready(StilledSpots.Spot spot, BlockPos at, float yaw, @Nullable String home) {}

    private static final Map<String, Ready> READY = new HashMap<>();
    private static final Ready DROP = new Ready(new StilledSpots.Spot("", 0, 0, StilledSpots.Where.CELL, StilledText.Pose.WALKING,
            StilledText.Look.MAN, "", -1, "", 0L), BlockPos.ZERO, 0F, null);

    static void serverStopped() {
        READY.clear();
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK != 0) return;
        ServerLevel level = server.overworld();
        if (level.players().isEmpty()) return;
        Stilled data = Stilled.get(server);
        BlockPos c = TheLull.centre(server);
        int ridge = TheLull.ridge(server);
        List<StilledSpots.Spot> fixed = StilledSpots.fixed(c.getX(), c.getZ(), ridge, firstHamlet(level, c, ridge), level.getSeed());
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            List<StilledSpots.Spot> near = new ArrayList<>();
            for (StilledSpots.Spot s : fixed) if (Math.hypot(s.x() - p.getX(), s.z() - p.getZ()) < RESOLVE) near.add(s);
            near.addAll(StilledSpots.cells(c.getX(), c.getZ(), ridge, level.getSeed(), p.getBlockX(), p.getBlockZ(), RESOLVE));
            for (StilledSpots.Spot s : near) {
                if (data.placed.containsKey(s.id())) continue;
                Ready r = READY.get(s.id());
                if (r == null) {
                    r = resolve(level, s, c, ridge, Math.hypot(s.x() - p.getX(), s.z() - p.getZ()));
                    if (r == null) continue;
                    READY.put(s.id(), r);
                }
                if (r == DROP) {
                    skip(data, s.id());
                    continue;
                }
                if (Math.hypot(r.at().getX() - p.getX(), r.at().getZ() - p.getZ()) > NEAR || !level.isLoaded(r.at())) continue;
                place(level, data, r, fixed);
            }
        }
    }

    /** The first hamlet's anchor (an empty settlement keyed "first" up the north road), or null while unknown. */
    static int @Nullable [] firstHamlet(ServerLevel level, BlockPos c, int ridge) {
        for (Villages.Empty e : Villages.emptyNear(level, new BlockPos(c.getX(), 0, c.getZ() - 870), 220))
            if (e.key().equals("first")) return new int[]{e.anchor().getX(), e.anchor().getZ()};
        return null;
    }

    static void skip(Stilled data, String id) {
        data.placed.put(id, Stilled.SKIPPED);
        data.setDirty();
        READY.remove(id);
    }

    /** Works out a spot: a {@link Ready}, {@link #DROP} (never here), or null (not yet: a plan or a road isn't known). */
    static @Nullable Ready resolve(ServerLevel level, StilledSpots.Spot s, BlockPos c, int ridge, double playerDistance) {
        switch (s.where()) {
            case ROAD -> {
                Optional<RoadHooks.RoadPoint> road = RoadHooks.nearestRoad(level, new BlockPos(s.x(), 0, s.z()), ROAD_FIXED);
                if (road.isPresent()) return beside(s, road.get(), c, null);
                // The north road isn't laid this near yet: wait, then sit on the line it takes.
                if (playerDistance > NEAR) return null;
                return new Ready(s, new BlockPos(s.x() + ROADSIDE, 0, s.z()), facing(s.pose(), 0, -1, -1, 0), null);
            }
            case HOME_EDGE -> {
                Villages.Empty home = emptyAt(level, s.x(), s.z());
                if (home == null || home.name() == null) return null;
                return edge(level, s, home, c.getX(), c.getZ());
            }
            default -> {
                if (Math.hypot(s.x() - c.getX(), s.z() - c.getZ()) < ridge + 40 || CityBasin.reservedWithin(s.x(), s.z(), 32)) return DROP;
                List<Villages.Empty> homes = Villages.emptyNear(level, new BlockPos(s.x(), 0, s.z()), HOME_NEAR);
                if (!homes.isEmpty()) {
                    Villages.Empty home = homes.getFirst();
                    if (home.name() == null) return null;
                    return edge(level, s, home, s.x(), s.z());
                }
                Optional<RoadHooks.RoadPoint> road = RoadHooks.nearestRoad(level, new BlockPos(s.x(), 0, s.z()), ROAD_CELL);
                if (road.isPresent()) return beside(s, road.get(), c, null);
                return playerDistance > NEAR ? null : DROP;
            }
        }
    }

    static Villages.@Nullable Empty emptyAt(ServerLevel level, int x, int z) {
        for (Villages.Empty e : Villages.emptyNear(level, new BlockPos(x, 0, z), 16))
            if (e.anchor().getX() == x && e.anchor().getZ() == z) return e;
        return null;
    }

    /** Beside a road point, on the side the seed picks, facing the way the pose wants (along it away from the city, or at it). */
    static Ready beside(StilledSpots.Spot s, RoadHooks.RoadPoint r, BlockPos c, @Nullable String home) {
        double ax = r.alongX(), az = r.alongZ();
        int dx = r.at().getX() - c.getX(), dz = r.at().getZ() - c.getZ();
        if (ax * dx + az * dz < 0) {
            ax = -ax;
            az = -az;
        }
        int side = (s.seed() & 1) == 0 ? 1 : -1;
        double px = -az * side, pz = ax * side;
        BlockPos at = new BlockPos((int) Math.round(r.at().getX() + px * ROADSIDE), r.at().getY(), (int) Math.round(r.at().getZ() + pz * ROADSIDE));
        return new Ready(s, at, facing(s.pose(), ax, az, -px, -pz), home);
    }

    /** Out from a settlement's anchor toward (tx, tz) until past its ground, facing back at it: they were going home. */
    static Ready edge(ServerLevel level, StilledSpots.Spot s, Villages.Empty home, int tx, int tz) {
        double dx = tx - home.anchor().getX(), dz = tz - home.anchor().getZ(), len = Math.hypot(dx, dz);
        if (len < 1) {
            double a = StilledSpots.unit(s.seed()) * Math.PI * 2;
            dx = Math.cos(a);
            dz = Math.sin(a);
        } else {
            dx /= len;
            dz /= len;
        }
        int out = 0;
        while (out < 200 && Villages.reserved(level, new BlockPos((int) (home.anchor().getX() + dx * out), 64, (int) (home.anchor().getZ() + dz * out)), 2)) out += 4;
        out += PAST_GROUND;
        BlockPos at = new BlockPos((int) Math.round(home.anchor().getX() + dx * out), 0, (int) Math.round(home.anchor().getZ() + dz * out));
        return new Ready(s, at, yaw(-dx, -dz), home.key());
    }

    /** Walking and looking back go along the road away from the city; the rest sit facing the road. */
    static float facing(StilledText.Pose pose, double ax, double az, double tx, double tz) {
        return switch (pose) {
            case WALKING, LOOKING_BACK, REACHING, LANTERN, CHILD -> yaw(ax, az);
            default -> yaw(tx, tz);
        };
    }

    /** Minecraft's yaw for facing along (dx, dz): 0 faces south (+z), 90 west. */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ---- placing ----------------------------------------------------------------------------------------------------

    /** Sets one down (or drops the spot for good); a cell's keeps clear of where the named ones will sit. */
    static void place(ServerLevel level, Stilled data, Ready r, List<StilledSpots.Spot> fixed) {
        String id = r.spot().id();
        BlockPos at = ground(level, r.at());
        if (at == null || HearthHooks.inside(level, at) || Villages.reserved(level, at, 0) || CityBasin.reservedWithin(at.getX(), at.getZ(), 16)) {
            skip(data, id);
            return;
        }
        // Only the land's own figures keep apart: the named ones on the north road stand where the story puts them,
        // however close the road brings them together (a winding road once dropped the second for standing near Aldo).
        if (r.spot().where() == StilledSpots.Where.CELL) {
            for (StilledSpots.Spot f : fixed)
                if (!data.placed.containsKey(f.id()) && Math.hypot(f.x() - at.getX(), f.z() - at.getZ()) < StilledSpots.APART + 120) {
                    skip(data, id);
                    return;
                }
        }
        if (r.spot().where() == StilledSpots.Where.CELL) for (Map.Entry<String, Long> e : data.placed.entrySet()) {
            if (e.getValue() == Stilled.SKIPPED) continue;
            BlockPos o = BlockPos.of(e.getValue());
            if (Math.hypot(o.getX() - at.getX(), o.getZ() - at.getZ()) < StilledSpots.APART) {
                skip(data, id);
                return;
            }
        }
        BlockPos head = at.above();
        if (!level.getBlockState(head).isAir()) level.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(at, StilledContent.STILLED.get().defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(at) instanceof StilledBlockEntity be)) {
            skip(data, id);
            return;
        }
        fill(level, data, be, r);
        be.changed();
        data.placed.put(id, at.asLong());
        data.setDirty();
        READY.remove(id);
        dev.awaken.Awaken.LOG.info("[stilled] {} sat down at {} ({}, {})", id, at.toShortString(), be.pose(), be.carry);
    }

    /** Natural ground with two free cells above it, nearest the point first (null: none within {@link #SEARCH}). */
    static @Nullable BlockPos ground(ServerLevel level, BlockPos near) {
        for (int ring = 0; ring <= SEARCH; ring++)
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = near.getX() + dx, z = near.getZ() + dz;
                    if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (fits(level, feet)) return feet;
                }
        return null;
    }

    static boolean fits(ServerLevel level, BlockPos feet) {
        BlockState under = level.getBlockState(feet.below());
        if (!natural(under) || !under.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) return false;
        for (BlockPos p : new BlockPos[]{feet, feet.above()}) {
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty() || !(s.isAir() || s.canBeReplaced())) return false;
        }
        return true;
    }

    /** Ground nobody built: no wood, glass, wool or path, nothing with a block entity, nothing frozen or in leaves. */
    static boolean natural(BlockState s) {
        if (s.hasBlockEntity() || s.is(Blocks.DIRT_PATH) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.PLANKS)) return false;
        if (s.is(BlockTags.WOOL) || s.is(BlockTags.ICE) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.IMPERMEABLE)) return false;
        return !s.is(Blocks.COBBLESTONE) && !s.is(BlockTags.STONE_BRICKS) && !s.is(Blocks.FARMLAND);
    }

    /** Who sits here and what they carry, rolled from the spot's seed. */
    static void fill(ServerLevel level, Stilled data, StilledBlockEntity be, Ready r) {
        StilledSpots.Spot s = r.spot();
        long seed = s.seed();
        be.figure = s.id();
        be.name = s.name();
        be.pose = s.pose().ordinal();
        be.look = s.look().ordinal();
        be.look2 = (s.pose() == StilledText.Pose.CHILD ? StilledText.Look.CHILD
                : s.look().male() ? StilledText.Look.WOMAN : StilledText.Look.MAN).ordinal();
        be.yaw = r.yaw();
        Settlement city = TheLull.ridge(level.getServer()) == TheLull.RIDGE_CITY ? Settlements.byId(Settlements.CARILLON).orElse(null) : null;
        String carry = s.carry();
        if (carry.equals("keepsake") && (city == null || city.records() == 0)) carry = Math.floorMod(seed >>> 5, 11L) < 6 ? "letter" : "key";
        List<Villages.Empty> homes = Villages.emptyNear(level, be.getBlockPos(), KEY_REACH);
        if (carry.equals("key")) {
            Villages.Empty to = keyFor(data, homes, r.home(), s.where() == StilledSpots.Where.HOME_EDGE);
            if (to == null) carry = "letter";
            else {
                data.keyed.add(to.key());
                be.settlement = to.key();
                be.place = to.name() == null ? "" : to.name();
                be.targetX = to.anchor().getX();
                be.targetZ = to.anchor().getZ();
            }
        }
        if (carry.equals("letter")) {
            be.n = s.n() >= 0 ? s.n() : 1 + (int) Math.floorMod(seed >>> 9, (long) (StilledText.LETTERS.length - 1));
            Villages.Empty lead = r.home() != null ? homes.stream().filter(e -> e.key().equals(r.home())).findFirst().orElse(null)
                    : homes.isEmpty() ? null : homes.getFirst();
            if (lead != null) {
                be.settlement = lead.key();
                be.place = lead.name() == null ? "" : lead.name();
                be.targetX = lead.anchor().getX();
                be.targetZ = lead.anchor().getZ();
            }
        }
        if (carry.equals("keepsake") && city != null) {
            int rec = (int) Math.floorMod(seed >>> 11, (long) city.records());
            String full = city.personName(rec);
            be.family = full.substring(full.lastIndexOf(' ') + 1);
            be.relation = StilledText.relation(s.look(), seed >>> 17)[0];
            be.n = (int) Math.floorMod(seed >>> 23, (long) StilledText.KEEPSAKES.length);
        }
        be.carry = carry;
    }

    /**
     * The empty settlement a key is for: its own home when the figure sits at the edge of it on the way in, else the
     * nearest one no other key opens yet (the home settlement last), within {@link #KEY_REACH}; null: none.
     */
    static Villages.@Nullable Empty keyFor(Stilled data, List<Villages.Empty> homes, @Nullable String home, boolean ownHome) {
        if (ownHome && home != null && !data.keyed.contains(home))
            for (Villages.Empty e : homes) if (e.key().equals(home)) return e;
        Villages.Empty fallback = null;
        for (Villages.Empty e : homes) {
            if (data.keyed.contains(e.key())) continue;
            if (e.key().equals(home)) {
                fallback = e;
                continue;
            }
            return e;
        }
        return fallback;
    }
}
