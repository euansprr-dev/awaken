package dev.awaken.world.fauna;

import dev.awaken.core.api.NowHooks;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Now tracker's fauna lines. A named beast that is known and near ("Hunt the Old Antler · 120 m NE"): known means the
 * player has seen it before, or it is close enough that the System senses it. And the nearest lair the player has
 * heard of and not yet stood in, unless its beast has just fallen ("Find the lair of the Briar Sow · 1.2 km NE"). Only for an
 * awakened player; the beast warns before it fights, so these are invitations, not alarms.
 */
final class FaunaNow {
    private FaunaNow() {}

    /** A beast this close shows in the tracker. */
    static final int SHOW = 160;
    /** A beast this close is sensed even if never seen. */
    static final int SENSE = 96;
    /** A lair within this many blocks shows (a rumour reaches about as far). */
    static final int LAIR_SHOW = Lairs.HEAR_RANGE + 200;

    static List<NowHooks.Suggestion> suggest(ServerPlayer sp) {
        if (!Progression.profile(sp).awakened() || sp.level().getServer() == null) return List.of();
        List<NowHooks.Suggestion> out = new ArrayList<>(2);
        String beastShown = beast(sp, out);
        lair(sp, beastShown, out);
        return out;
    }

    /** The beast line; returns the def id it points at (or null). */
    private static @Nullable String beast(ServerPlayer sp, List<NowHooks.Suggestion> out) {
        BeastRegistry reg = BeastRegistry.get(sp.level().getServer());
        BlockPos at = sp.blockPosition();
        BeastRegistry.Entry e = reg.nearest(sp.level().dimension().identifier().toString(), at, SHOW);
        if (e == null) return null;
        double dx = e.pos().getX() - at.getX(), dz = e.pos().getZ() - at.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > SENSE && !Sightings.hasSeen(sp, e.def())) return null;
        MobDef d = Bestiary.get(e.def());
        String name = d != null ? d.name : e.def();
        int priority = Progression.profile(sp).level() >= 3 ? 40 : 22;
        String text = "Hunt " + (name.startsWith("The ") ? "the " + name.substring(4) : name) + " · " + Math.round(dist) + " m " + compass(dx, dz);
        out.add(new NowHooks.Suggestion("fauna:beast:" + e.id(), priority, Component.literal(text), Optional.of(e.pos())));
        return e.def();
    }

    /** The lair line: the nearest lair heard of and not yet found, whose beast isn't the one already shown. */
    static void lair(ServerPlayer sp, @Nullable String beastShown, List<NowHooks.Suggestion> out) {
        if (sp.level().dimension() != Level.OVERWORLD) return;
        FaunaState s = sp.getData(FaunaModule.STATE.get());
        if (s.lairs().isEmpty()) return;
        LairRegistry reg = LairRegistry.get(sp.level().getServer());
        BlockPos at = sp.blockPosition();
        FaunaState.KnownLair best = null;
        BlockPos bestPos = null;
        double bestD = (double) LAIR_SHOW * LAIR_SHOW;
        for (FaunaState.KnownLair k : s.lairs()) {
            if (k.found() || k.beast().equals(beastShown)) continue;
            LairRegistry.Entry e = reg.get(k.key());
            BlockPos pos = e != null ? e.den() : k.pos();
            if (e != null && e.resident().isEmpty() && e.felledAt() > 0 && sp.level().getGameTime() - e.felledAt() < Lairs.RESPAWN) continue;
            double dx = pos.getX() - at.getX(), dz = pos.getZ() - at.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = k;
                bestPos = pos;
            }
        }
        if (best == null) return;
        MobDef d = Bestiary.get(best.beast());
        String whose = d == null ? "a beast" : d.name.startsWith("The ") ? "the " + d.name.substring(4) : d.name;
        double dx = bestPos.getX() - at.getX(), dz = bestPos.getZ() - at.getZ();
        String text = "Find the lair of " + whose + " · " + Lairs.distance(Math.sqrt(bestD)) + " " + compass(dx, dz) + depth(bestPos.getY() - at.getY());
        int priority = Progression.profile(sp).level() >= 3 ? 30 : 14;
        out.add(new NowHooks.Suggestion("fauna:lair:" + best.key(), priority, Component.literal(text), Optional.of(bestPos)));
    }

    /** How far down (or up) a lair is: " · 40 below" (W5, PLAN 8.2: cave lairs get a depth word); nothing within 8. */
    static String depth(int dy) {
        if (Math.abs(dy) < 8) return "";
        return " · " + (Math.round(Math.abs(dy) / 5.0) * 5) + (dy < 0 ? " below" : " above");
    }

    /** Eight-point compass from a horizontal offset (Minecraft: -z is north). */
    static String compass(double dx, double dz) {
        if (dx * dx + dz * dz < 4) return "here";
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) deg += 360;
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return names[(int) Math.round(deg / 45.0) % 8];
    }
}
