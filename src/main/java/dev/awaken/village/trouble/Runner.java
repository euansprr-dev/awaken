package dev.awaken.village.trouble;

import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A runner on the road (SPEC §28.7, PLAN 6.2 "found, never listed"): while a village is in trouble and nobody has said
 * Help, someone runs out to its edge toward a player it knows (Known or better) coming along within 180 blocks, and
 * waves them down; close enough, they tell them and the asker's card shows. Strangers get no runner: they find the
 * trouble by its call (the bell, the smoke) or not at all.
 */
final class Runner {
    private Runner() {}

    static final int SEEN = 180, TELL = 10;

    static int of(Trouble t) {
        return (int) t.get("runner", -1);
    }

    static void tick(Plot.Ctx c, Trouble t, VillageService.Live l) {
        if (!t.helpers.isEmpty() || c.tod() > 13000 && c.tod() < 23000) {
            release(c, t, l);
            return;
        }
        Vec3 o = TroubleDirector.well(l);
        double edge = l.plan().radius() + 3;
        ServerPlayer who = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : c.level().players()) {
            if (p.isSpectator() || t.helped(p.getUUID()) || VillageService.tier(p, l) < Villages.KNOWN) continue;
            double d = Math.hypot(p.getX() - o.x, p.getZ() - o.z);
            if (d < edge + 6 || d > edge + SEEN) continue;
            if (d < bd) {
                bd = d;
                who = p;
            }
        }
        if (who == null) {
            release(c, t, l);
            return;
        }
        int r = of(t);
        if (r < 0) {
            Set<Integer> not = new HashSet<>(TroubleDirector.household(l, Math.max(0, t.kin)));
            if (t.person >= 0) not.add(t.person);
            List<Integer> pick = TroubleDirector.crowd(l, 1, not, "hunter", "farmer", "woodcutter", "carter");
            if (pick.isEmpty()) return;
            r = pick.getFirst();
            t.put("runner", r);
        }
        double a = Math.atan2(who.getZ() - o.z, who.getX() - o.x);
        Vec3 at = TroubleDirector.stand(c.level(), l, o.x + Math.cos(a) * edge, o.z + Math.sin(a) * edge);
        TroubleDirector.stage(l, r, at, TroubleDirector.yaw(at, who.position()), "wave", "");
        if (who.position().distanceToSqr(at) < TELL * TELL && t.get("told_" + who.getUUID().hashCode(), 0) == 0) {
            t.put("told_" + who.getUUID().hashCode(), 1);
            TroubleDirector.plot(t).ask(c, t, who, r);
        }
    }

    static void release(Plot.Ctx c, Trouble t, VillageService.Live l) {
        int r = of(t);
        if (r < 0) return;
        TroubleDirector.release(l, r);
        t.data.remove("runner");
    }
}
