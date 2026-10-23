package dev.awaken.world.fauna;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Playtest;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * First sightings: the first time an awakened player sees a kind of overworld creature (within {@value #RANGE}
 * blocks, roughly where they're looking, with line of sight) they get the discovery XP and a toast. Checked twice a
 * second per player against the census (never the level), at most {@value #RAYS} line-of-sight rays per check, and
 * only for kinds the player hasn't met; once every kind is known the check costs nothing.
 */
public final class Sightings {
    private Sightings() {}

    public static final int RANGE = 24;
    static final int RAYS = 2;
    static final int PERIOD = 10;
    /** Share of a level a first sighting pays (a named beast pays more). */
    static final double XP = 0.1, BEAST_XP = 0.25;
    /** At most one sighting toast every three seconds, so a burst of new creatures arrives one at a time. */
    static final int TOAST_GAP = 60;

    private static final Map<UUID, Set<String>> KNOWN = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    static String key(String id) {
        return "creature/" + id;
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) check(p);
    }

    /** One look around for one player. Returns the creature newly sighted, if any. */
    public static @Nullable MobDef check(ServerPlayer p) {
        if (p.isSpectator() || !Progression.profile(p).awakened()) return null;
        List<FaunaMob> live = FaunaCensus.in(p.level());
        if (live.isEmpty()) return null;
        Set<String> known = known(p);
        if (known.size() >= Bestiary.fauna().size()) return null;
        Long last = LAST.get(p.getUUID());
        long now = p.level().getGameTime();
        if (last != null && now - last < TOAST_GAP && now >= last) return null;
        Vec3 eye = p.getEyePosition();
        Vec3 view = p.getViewVector(1.0F);
        int rays = 0;
        for (int i = 0, n = live.size(); i < n; i++) {
            FaunaMob m = live.get(i);
            if (!m.isAlive() || known.contains(m.def().id)) continue;
            double dx = m.getX() - eye.x, dy = m.getY() + m.getBbHeight() * 0.5 - eye.y, dz = m.getZ() - eye.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > RANGE * RANGE) continue;
            double len = Math.sqrt(d2);
            // Roughly in view (about 60 degrees off the crosshair), unless it's right beside you.
            if (len > 3 && (dx * view.x + dy * view.y + dz * view.z) / len < 0.5) continue;
            if (rays++ >= RAYS) break;
            if (!p.hasLineOfSight(m)) continue;
            discover(p, m.def());
            return m.def();
        }
        return null;
    }

    /** Records the sighting, pays the discovery once, and says so in a toast. */
    static boolean discover(ServerPlayer p, MobDef d) {
        known(p).add(d.id);
        boolean beast = d.fauna == FaunaRole.BEAST;
        boolean fresh = Progression.discover(p, key(d.id), beast ? BEAST_XP : XP);
        if (!fresh) return false;
        LAST.put(p.getUUID(), p.level().getGameTime());
        Habitat h = Habitat.of(d.id);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("New creature · " + d.name).withColor(d.glow))
                .body(Component.literal(h.hint().isEmpty() ? "+XP for the sighting" : h.hint() + " · +XP"))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
        Playtest.log(p, "sighting", "creature", d.id);
        DirectorLog.log(p, "fauna", "first sighting: " + d.id);
        return true;
    }

    /** Creature kinds this player has met (cached from the profile's discovery counters). */
    static Set<String> known(ServerPlayer p) {
        return KNOWN.computeIfAbsent(p.getUUID(), u -> {
            Set<String> s = new HashSet<>();
            Profile prof = Progression.profile(p);
            for (MobDef d : Bestiary.fauna()) if (prof.counter("awaken:discovered/" + key(d.id)) > 0) s.add(d.id);
            return s;
        });
    }

    public static boolean hasSeen(ServerPlayer p, String id) {
        return known(p).contains(id);
    }

    static void forget(UUID player) {
        KNOWN.remove(player);
        LAST.remove(player);
    }

    static void clear() {
        KNOWN.clear();
        LAST.clear();
    }
}
