package dev.awaken.village.road;

import dev.awaken.core.api.RoadHooks;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The roads for other modules (SPEC §28.9, {@link RoadHooks}): a stretch of a laid road near a place for the Caravan to
 * walk, bound for the settlement at its end (arriving at its edge when that's in reach), and what a caravan brings when it
 * comes in: the village's prosperity, its stalls' goods from where the caravan set out, the news in its history.
 */
public final class RoadAccess implements RoadHooks.Access {
    /** (SPEC §28.9) Caravans to Tier IV homes too, by W6's {@code Hearth.villages}. */
    public static void installHearths() {
        RoadHooks.installHearths(new HearthRoutes());
    }

    /** (SPEC §28.9, Cost) A chunk loaded: if the server thread had to wait for it far from every player, who asked. */
    public static void chunkLoaded(net.neoforged.neoforge.event.level.ChunkEvent.Load e) {
        RoadTimer.loaded(e);
    }

    /** (SPEC §28.9, Cost) The server's tick begins or ends: the planners' jobs ahead of the players hold back while one runs late. */
    public static void serverTick(boolean begins) {
        if (begins) RoadTimer.tickBegins();
        else RoadTimer.tickEnds();
    }

    @Override
    public Optional<RoadHooks.RoadPoint> nearestRoad(ServerLevel level, BlockPos pos, int reach) {
        RoadNet.Nearest n = RoadNet.nearestWithin(pos.getX() + 0.5, pos.getZ() + 0.5, reach);
        if (n == null) return Optional.empty();
        Road r = n.road();
        int i = n.point(), a = Math.max(0, i - 1), b = Math.min(r.points() - 1, i + 1);
        double dx = r.x()[b] - r.x()[a], dz = r.z()[b] - r.z()[a], len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            dx = 1;
            len = 1;
        }
        return Optional.of(new RoadHooks.RoadPoint(new BlockPos((int) Math.floor(r.x()[i]), r.y()[i], (int) Math.floor(r.z()[i])), dx / len, dz / len));
    }

    @Override
    public Optional<RoadHooks.Route> stretch(ServerLevel level, BlockPos pos, int min, int max, int length, long seed) {
        Random r = new Random(seed);
        int want = Math.max(8, length / RoadPlanner.STEP);
        List<RoadHooks.Route> found = new ArrayList<>();
        for (Road road : RoadNet.laid()) {
            int n = road.points();
            if (n < want + 2) continue;
            // the stretch's middle within min..max of the place, every point of it in loaded land
            for (int dir = 0; dir < 2; dir++) {
                boolean toEnd = dir == 0;
                Road.End bound = toEnd ? road.to() : road.from();
                if (bound.kind() != Road.ENTRY) continue;
                // arriving at the settlement when its end is in reach, else a stretch of the way there
                int last = toEnd ? n - 1 : 0;
                for (int k = 0; k < 6; k++) {
                    int end = toEnd ? last - k * want / 2 : last + k * want / 2;
                    int start = toEnd ? end - want : end + want;
                    if (start < 0 || start >= n || end < 0 || end >= n) break;
                    int mid = (start + end) / 2;
                    double d = Math.hypot(road.x()[mid] - pos.getX(), road.z()[mid] - pos.getZ());
                    if (d < min || d > max) continue;
                    List<Vec3> pts = new ArrayList<>();
                    boolean loaded = true;
                    for (int i = start; toEnd ? i <= end : i >= end; i += toEnd ? 1 : -1) {
                        BlockPos at = BlockPos.containing(road.x()[i], road.y()[i] + 1, road.z()[i]);
                        if (!level.isLoaded(at)) {
                            loaded = false;
                            break;
                        }
                        pts.add(new Vec3(road.x()[i], road.y()[i] + 1, road.z()[i]));
                    }
                    if (!loaded) continue;
                    Road.End from = toEnd ? road.from() : road.to();
                    found.add(new RoadHooks.Route(pts, from.name(), bound.name(), from.settlement(), bound.settlement(), k == 0));
                }
            }
        }
        // (SPEC §28.9) a Tier IV home near is the caravan's first choice: the road's reward for growing it
        List<RoadHooks.Route> homes = RoadHooks.hearthStretches(level, pos, min, max, length);
        if (!homes.isEmpty()) return Optional.of(homes.get(r.nextInt(homes.size())));
        if (found.isEmpty()) return Optional.empty();
        // a stretch that arrives first, else any
        List<RoadHooks.Route> arriving = found.stream().filter(RoadHooks.Route::arrives).toList();
        List<RoadHooks.Route> pool = arriving.isEmpty() ? found : arriving;
        return Optional.of(pool.get(r.nextInt(pool.size())));
    }

    @Override
    public void arrived(ServerLevel level, String key, String from, boolean defended) {
        VillageService.Live l = VillageService.live(key);
        if (l == null) return;
        var server = level.getServer();
        Villages.addProsperity(server, key, defended ? 4 : 2);
        Villages.history(server, key, "A caravan came in from " + from + (defended ? ", safe because someone stood with it on the road." : "."));
        VillageService.caravan(server, l, from);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(l.plan().ox, l.plan().baseY, l.plan().oz) > 200 * 200) continue;
            dev.awaken.signal.Signal.toast(p, Component.literal("A caravan came into " + l.plan().name + " from " + from),
                    dev.awaken.core.api.Notices.Tier.AMBIENT, "village_caravan");
        }
    }
}
