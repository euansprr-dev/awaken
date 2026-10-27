package dev.awaken.powers.fx;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A cast profile: what makes one power's cast its own (docs/power-anims/PLAN.md section 5.1). A power without one keeps
 * its family's clip and its {@link CastAnim}. A profile names the full-body clips (third person), the first-person clips,
 * the tick the effect leaves on (the same tick in both, the clips are authored to it) and how long the server waits
 * before the power happens (a short wind-up: the foot has to land before the wave leaves). The id travels in an
 * {@link FxEvent}'s high flag bits with a beat ({@link FxEvent#profile()}); the table is built in a fixed order, so both
 * sides agree on ids. Clips are written by tools/art/specs/power_casts.py and fp_casts.py; the {@code cast_profiles}
 * GameTest holds every name here to those files.
 */
public final class CastProfiles {
    private CastProfiles() {}

    /** The beats an event can carry. */
    public static final int BEGIN = 0, RELEASE = 1, END = 2;

    /**
     * @param windup   server ticks between the key press and the power happening (0: on the press tick)
     * @param release  the tick of the clips the effect leaves on (the plant, the thrust)
     * @param fallback the vanilla pose used when the animation library is not installed
     * @param begin3p  third-person clip started at the press; {@code hold3p} the loop it moves into after {@code holdAt} ticks
     *                 (null: none); {@code end3p} the clip that lets go of the hold
     * @param begin1p  first-person clip started at the press; {@code hold1p} the loop it moves into when {@code begin1p} ends
     */
    public record Profile(int id, String power, int windup, int release, CastAnim fallback, String begin3p, @Nullable String hold3p,
                          int holdAt, @Nullable String end3p, String begin1p, @Nullable String hold1p) {}

    private static final List<Profile> LIST = new ArrayList<>();
    private static final Map<String, Profile> BY_POWER = new HashMap<>();

    private static void add(String power, int windup, int release, CastAnim fallback, String begin3p, @Nullable String hold3p, int holdAt,
                            @Nullable String end3p, String begin1p, @Nullable String hold1p) {
        Profile p = new Profile(LIST.size() + 1, power, windup, release, fallback, begin3p, hold3p, holdAt, end3p, begin1p, hold1p);
        LIST.add(p);
        BY_POWER.put(power, p);
    }

    static {
        // Kinetic's stamp: knee up, hang, plant at tick 5 (250 ms); the wave leaves from the planted foot
        add("shockwave", 5, 5, CastAnim.STOMP, "stomp", null, 0, null, "fp_stomp", null);
        // Energy's beam through the core: gather at the sternum, the arm to the aim by tick 5, then a braced, trembling hold
        add("beam", 0, 5, CastAnim.AIM_HOLD, "beam_gather", "beam_hold", 5, "beam_hold_end", "fp_beam_gather", "fp_beam_hold");
    }

    public static @Nullable Profile byPower(String powerPath) {
        int colon = powerPath.indexOf(':');
        return BY_POWER.get(colon >= 0 ? powerPath.substring(colon + 1) : powerPath);
    }

    public static @Nullable Profile byId(int id) {
        return id >= 1 && id <= LIST.size() ? LIST.get(id - 1) : null;
    }

    public static List<Profile> all() {
        return LIST;
    }

    /** Every clip a profile names (third person, then first person): for the asset test. */
    public static List<String> clips3p() {
        List<String> out = new ArrayList<>();
        for (Profile p : LIST) {
            out.add(p.begin3p());
            if (p.hold3p() != null) out.add(p.hold3p());
            if (p.end3p() != null) out.add(p.end3p());
        }
        return out;
    }

    public static List<String> clips1p() {
        List<String> out = new ArrayList<>();
        for (Profile p : LIST) {
            out.add(p.begin1p());
            if (p.hold1p() != null) out.add(p.hold1p());
        }
        return out;
    }
}
