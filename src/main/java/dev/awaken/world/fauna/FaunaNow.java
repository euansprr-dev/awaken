package dev.awaken.world.fauna;

import dev.awaken.core.api.NowHooks;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * The Now tracker's fauna line: a named beast that is known and near ("The Old Antler · 120 m NE"). Known means the
 * player has seen it before, or it is close enough that the System senses it. Never more than one line, and only for
 * an awakened player; the beast itself warns before it fights, so this is an invitation, not an alarm.
 */
final class FaunaNow {
    private FaunaNow() {}

    /** A beast this close shows in the tracker. */
    static final int SHOW = 160;
    /** A beast this close is sensed even if never seen. */
    static final int SENSE = 96;

    static List<NowHooks.Suggestion> suggest(ServerPlayer sp) {
        if (!Progression.profile(sp).awakened() || sp.level().getServer() == null) return List.of();
        BeastRegistry reg = BeastRegistry.get(sp.level().getServer());
        BlockPos at = sp.blockPosition();
        BeastRegistry.Entry e = reg.nearest(sp.level().dimension().identifier().toString(), at, SHOW);
        if (e == null) return List.of();
        double dx = e.pos().getX() - at.getX(), dz = e.pos().getZ() - at.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > SENSE && !Sightings.hasSeen(sp, e.def())) return List.of();
        MobDef d = Bestiary.get(e.def());
        String name = d != null ? d.name : e.def();
        int priority = Progression.profile(sp).level() >= 3 ? 40 : 22;
        String text = name + " · " + Math.round(dist) + " m " + compass(dx, dz);
        return List.of(new NowHooks.Suggestion("fauna:beast:" + e.id(), priority, Component.literal(text), Optional.of(e.pos())));
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
