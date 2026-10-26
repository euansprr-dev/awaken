package dev.awaken.village.road;

import dev.awaken.core.api.RoadHooks;
import dev.awaken.hearth.Hearth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Caravans to your village (SPEC §28.9, PLAN: "caravans on your road are Tier IV's reward"): a home grown to Tier IV
 * ({@link Hearth#villages}) gets the World Pulse's caravan too. A Hearth has no road of the world's own laid to it, so the
 * caravan comes along the laid road nearest the village (within {@link #REACH}) and then over the land to where its
 * people gather, and arrives there.
 */
final class HearthRoutes implements RoadHooks.HearthRoads {
    /** A village this near a laid road is on it. */
    static final int REACH = 400;

    @Override
    public List<RoadHooks.Route> stretches(ServerLevel level, BlockPos pos, int min, int max, int length) {
        if (level.dimension() != Level.OVERWORLD) return List.of();
        List<RoadHooks.Route> out = new ArrayList<>();
        for (Hearth.Village v : Hearth.villages(level.getServer())) {
            BlockPos g = v.gather();
            if (Math.hypot(g.getX() - pos.getX(), g.getZ() - pos.getZ()) > max + REACH) continue;
            var near = RoadNet.nearest(g.getX(), g.getZ(), REACH);
            if (near.isEmpty()) continue;
            Road r = near.get().road();
            int k = near.get().point(), n = r.points();
            double ox = r.x()[k], oz = r.z()[k], tx = g.getX() + 0.5, tz = g.getZ() + 0.5;
            int over = (int) Math.ceil(Math.hypot(tx - ox, tz - oz) / RoadPlanner.STEP);
            int want = Math.max(4, length / RoadPlanner.STEP - over);
            // along the road from its longer side to the turn-off, then over the land to the village
            int dir = k >= n / 2 ? 1 : -1;
            int start = Math.clamp(k - dir * want, 0, n - 1);
            List<Vec3> pts = new ArrayList<>();
            boolean loaded = true;
            for (int i = start; dir > 0 ? i <= k : i >= k; i += dir) {
                loaded &= level.isLoaded(BlockPos.containing(r.x()[i], r.y()[i], r.z()[i]));
                pts.add(new Vec3(r.x()[i], r.y()[i] + 1, r.z()[i]));
            }
            for (int s = 1; s <= over && loaded; s++) {
                double x = ox + (tx - ox) * s / over, z = oz + (tz - oz) * s / over;
                BlockPos at = BlockPos.containing(x, 0, z);
                if (!level.isLoaded(at)) {
                    loaded = false;
                    break;
                }
                pts.add(new Vec3(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), z));
            }
            if (!loaded || pts.size() < 4) continue;
            Vec3 mid = pts.get(pts.size() / 2);
            double d = Math.hypot(mid.x - pos.getX(), mid.z - pos.getZ());
            if (d < min || d > max) continue;
            Road.End from = dir > 0 ? r.from() : r.to();
            out.add(new RoadHooks.Route(pts, from.name(), v.name(), from.settlement(), RoadHooks.HEARTH + v.owner(), true));
        }
        return out;
    }

    @Override
    public void arrived(ServerLevel level, String hearthKey, String from, boolean defended) {
        UUID owner;
        try {
            owner = UUID.fromString(hearthKey.substring(RoadHooks.HEARTH.length()));
        } catch (IllegalArgumentException e) {
            return;
        }
        for (Hearth.Village v : Hearth.villages(level.getServer())) {
            if (!v.owner().equals(owner)) continue;
            String line = "A caravan came into " + v.name() + " from " + from + (defended ? ", safe because someone stood with it on the road." : ".");
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(Vec3.atCenterOf(v.gather())) > 200 * 200) continue;
                dev.awaken.signal.Signal.toast(p, Component.literal(line), dev.awaken.core.api.Notices.Tier.AMBIENT, "village_caravan");
            }
            dev.awaken.Awaken.LOG.info("[road] a caravan from {} came into {} (Tier {}){}", from, v.name(), v.tier(), defended ? ", defended" : "");
            return;
        }
    }
}
