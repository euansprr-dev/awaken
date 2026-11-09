package dev.awaken.world.fauna;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Playtest;
import dev.awaken.core.api.Spotting;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Finds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * First sightings (SPEC §26.3: discovery is earned): the first time an awakened player studies a kind of overworld
 * creature it is a discovery (XP, the Codex, and the Finds' telling). Studying means looking at it: the crosshair on
 * its box (widened by {@value #AIM_DEG} degrees) for {@value #DWELL} looks (about half a second), within
 * {@value #RANGE} blocks ({@value #BEAST_RANGE} for a named beast) and in sight (a few blocks of foliage don't hide it).
 * Up to {@value #GRACE} looks in a row may slip off a moving creature without starting over, and another of its kind
 * keeps the count. Hitting or touching it counts
 * at once. Proximity alone never does, except for something big in plain view (a named beast, or a creature
 * {@value #BIG} blocks tall) within {@value #PLAIN} blocks and roughly in front of you. Checked four times a second per
 * player against the census (never the level), at most {@value #RAYS} line-of-sight rays per check, and only for
 * kinds the player hasn't met; once every kind is known the check costs nothing.
 */
public final class Sightings {
    private Sightings() {}

    public static final int RANGE = 24, BEAST_RANGE = 48, PLAIN = 16;
    static final int RAYS = 2;
    static final int PERIOD = 5;
    /** Looks in a row on the same creature that make a study (PERIOD apart: about half a second). */
    public static final int DWELL = 3;
    /** Looks in a row that may miss without starting over (a creature walking, a shaky aim). */
    public static final int GRACE = 2;
    /** Slack round a creature's own angular size that still counts as the crosshair on it. */
    static final double AIM_DEG = 3.0, AIM_TAN = Math.tan(Math.toRadians(AIM_DEG));
    /** A creature this tall (blocks) is big enough to notice without studying it. */
    static final double BIG = 2.5;
    /** Share of a level a first sighting pays (a named beast pays more). */
    static final double XP = 0.1, BEAST_XP = 0.25;

    private static final Map<UUID, Set<String>> KNOWN = new HashMap<>();
    /** The kind each player's crosshair rests on, for how many looks, and how many since it last was. */
    private static final Map<UUID, Dwell> DWELLING = new HashMap<>();

    private static final class Dwell {
        @Nullable String kind;
        int looks, misses;

        void clear() {
            kind = null;
            looks = misses = 0;
        }
    }

    static String key(String id) {
        return "creature/" + id;
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) check(p);
    }

    /** One look around for one player. Returns the creature newly discovered, if any. */
    public static @Nullable MobDef check(ServerPlayer p) {
        if (p.isSpectator() || !Progression.profile(p).awakened()) return null;
        if (!Finds.counts(p)) { // the city's animals and citizens are no discovery
            DWELLING.remove(p.getUUID());
            return null;
        }
        List<FaunaMob> live = FaunaCensus.in(p.level());
        if (live.isEmpty()) return null;
        Set<String> known = known(p);
        if (known.size() >= Bestiary.fauna().size()) return null;
        Vec3 eye = p.getEyePosition();
        Vec3 view = p.getViewVector(1.0F);
        // the nearest few under the look get the rays: a herd on the crosshair never starves the one in front
        FaunaMob near = null, second = null;
        double nearDist = Double.MAX_VALUE, secondDist = Double.MAX_VALUE;
        boolean nearBig = false, secondBig = false;
        for (int i = 0, n = live.size(); i < n; i++) {
            FaunaMob m = live.get(i);
            if (!m.isAlive() || known.contains(m.def().id)) continue;
            boolean beast = m.def().fauna == FaunaRole.BEAST;
            double dx = m.getX() - eye.x, dy = m.getY() + m.getBbHeight() * 0.5 - eye.y, dz = m.getZ() - eye.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            int range = beast ? BEAST_RANGE : RANGE;
            if (d2 > range * range) continue;
            double len = Math.max(0.01, Math.sqrt(d2));
            double cos = (dx * view.x + dy * view.y + dz * view.z) / len;
            // something big in plain view needs no study
            boolean big = (beast || m.getBbHeight() >= BIG) && len <= PLAIN && cos >= 0.5;
            // the crosshair's ray against its box, widened by AIM_DEG of slack at this distance
            boolean onCrosshair = false;
            if (!big && cos > 0.7) {
                AABB box = m.getBoundingBox().inflate(len * AIM_TAN);
                onCrosshair = box.contains(eye) || box.clip(eye, eye.add(view.scale(len + 4))).isPresent();
            }
            if (!big && !onCrosshair) continue;
            if (len < nearDist) {
                second = near;
                secondDist = nearDist;
                secondBig = nearBig;
                near = m;
                nearDist = len;
                nearBig = big;
            } else if (len < secondDist) {
                second = m;
                secondDist = len;
                secondBig = big;
            }
        }
        FaunaMob aimed = null;
        for (int k = 0; k < RAYS && aimed == null; k++) {
            FaunaMob m = k == 0 ? near : second;
            if (m == null || !inSight(p, eye, m)) continue;
            if (k == 0 ? nearBig : secondBig) {
                discover(p, m.def());
                return m.def();
            }
            aimed = m;
        }
        Dwell d = DWELLING.computeIfAbsent(p.getUUID(), u -> new Dwell());
        if (aimed == null) {
            if (d.kind != null && ++d.misses > GRACE) d.clear();
            return null;
        }
        d.misses = 0;
        if (!aimed.def().id.equals(d.kind)) {
            d.kind = aimed.def().id;
            d.looks = 0;
        }
        if (++d.looks < DWELL) return null;
        d.clear();
        discover(p, aimed.def());
        return aimed.def();
    }

    /** Its head or its middle can be seen from the eyes (its head may be behind a branch while its flank isn't). */
    private static boolean inSight(ServerPlayer p, Vec3 eye, FaunaMob m) {
        return Spotting.clearLine(p.level(), eye, m.getEyePosition()) || Spotting.clearLine(p.level(), eye, m.getBoundingBox().getCenter());
    }

    /** The player hit or touched a creature: that is studying it. */
    static void touched(ServerPlayer p, FaunaMob m) {
        if (!Progression.profile(p).awakened() || !Finds.counts(p) || known(p).contains(m.def().id)) return;
        discover(p, m.def());
    }

    /** Records the sighting, pays the discovery once, and has the Finds tell it. */
    static boolean discover(ServerPlayer p, MobDef d) {
        known(p).add(d.id);
        boolean beast = d.fauna == FaunaRole.BEAST;
        boolean fresh = Progression.discover(p, key(d.id), beast ? BEAST_XP : XP);
        if (!fresh) return false;
        Habitat h = Habitat.of(d.id);
        Finds.Find f = Finds.Find.of("creature", "New creature · " + d.name, d.name, h.hint(), d.glow, true);
        Finds.tell(p, beast ? f.big("Named beast") : f);
        Playtest.log(p, "sighting", "creature", d.id);
        DirectorLog.log(p, "fauna", "studied a new creature: " + d.id);
        Lairs.onSighting(p, d);
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
        DWELLING.remove(player);
    }

    static void clear() {
        KNOWN.clear();
        DWELLING.clear();
    }
}
