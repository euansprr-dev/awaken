package dev.awaken.powers.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A cast profile: what makes one power's cast its own (docs/power-anims/PLAN.md section 5.1). A power without one keeps
 * its family's clip and its {@link CastAnim}. A profile names the full-body clips (third person), the first-person clips,
 * the tick the effect leaves on (the same tick in both: the clips are authored to it), how long the server waits before the
 * power happens (a short wind-up: the foot has to land before the wave leaves), the screen moment at the release and the
 * Lens look. The table is {@code data/awaken/powers/casts.json}, written by tools/art/specs/power_gestures.py from
 * power_cast_table.py; ids are the rows' order (1 up), so both sides, built from one jar, agree. An id and a beat
 * ({@link FxEvent#profile()}) ride an event's high flag bits. The {@code power_cast_profiles} GameTest holds every name to
 * the clip files.
 */
public final class CastProfiles {
    private CastProfiles() {}

    public static final String FILE = "data/awaken/powers/casts.json";
    public static final int SCHEMA = 1;

    /** The screen moments a profile may name (powers.client.fx.CastFeel plays them; the test holds the table to this list). */
    public static final java.util.Set<String> MOMENTS = java.util.Set.of("thump_heavy", "thump_light", "recoil_light", "release_big", "push",
            "breath", "lift", "none");

    /** The beats an event can carry. */
    public static final int BEGIN = 0, RELEASE = 1, END = 2;

    /**
     * @param windup    server ticks between the key press and the power happening (0: on the press tick)
     * @param release   the tick of the clips the effect leaves on (the plant, the thrust); events wait for it (decision D4)
     * @param fallback  the vanilla pose used when the animation library is not installed (from PowerFxDefs)
     * @param begin3p   third-person clip started at the press; {@code hold3p} the loop it moves into after {@code holdAt} ticks
     *                  (null: none); {@code end3p} the clip that lets go (a strike or a plain fade)
     * @param begin1p   first-person clip started at the press; {@code hold1p} the loop it moves into when {@code begin1p} ends
     * @param moment    the screen moment at the release tick ({@code CastFeel.MOMENTS}), {@code endMoment} at a channel's let-go
     * @param lens      a Lens look id, or null
     * @param origin    where a beam or bolt starts: "palm" or "core"
     */
    public record Profile(int id, String power, int windup, int release, CastAnim fallback, String begin3p, @Nullable String hold3p,
                          int holdAt, @Nullable String end3p, String begin1p, @Nullable String hold1p, boolean channel, String moment,
                          String endMoment, @Nullable String lens, String origin, @Nullable String beginSound) {
        public boolean core() {
            return "core".equals(origin);
        }
    }

    private static final List<Profile> LIST = new ArrayList<>();
    private static final Map<String, Profile> BY_POWER = new HashMap<>();
    /** The ladder's level rows (SPEC 34): per profile id, [level 3, level 5] (the same id and timing, their own clips). */
    private static final Map<Integer, Profile[]> LEVELS = new HashMap<>();

    static {
        try (InputStream in = CastProfiles.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in == null) throw new IllegalStateException("missing " + FILE + " (python3 tools/art/specs/power_casts.py build)");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int schema = root.get("schemaVersion").getAsInt();
            if (schema != SCHEMA) throw new IllegalStateException(FILE + " schemaVersion " + schema + ", expected " + SCHEMA);
            JsonArray rows = root.getAsJsonArray("profiles");
            for (JsonElement e : rows) {
                JsonObject r = e.getAsJsonObject();
                String power = r.get("power").getAsString();
                boolean channel = r.get("channel").getAsBoolean();
                PowerFxDefs.Def def = PowerFxDefs.get(power);
                CastAnim fallback = def == null ? CastAnim.PALM : channel && def.windAnim() != null ? def.windAnim() : def.anim();
                if (fallback == CastAnim.NONE) fallback = CastAnim.PALM;
                Profile p = new Profile(LIST.size() + 1, power, r.get("windup").getAsInt(), r.get("release").getAsInt(), fallback,
                        r.get("begin3p").getAsString(), str(r, "hold3p"), r.get("holdAt").getAsInt(), str(r, "end3p"), r.get("begin1p").getAsString(),
                        str(r, "hold1p"), channel, r.get("moment").getAsString(), r.get("endMoment").getAsString(), str(r, "lens"),
                        r.get("origin").getAsString(), str(r, "beginSound"));
                LIST.add(p);
                BY_POWER.put(power, p);
                JsonObject lv = r.has("levels") ? r.getAsJsonObject("levels") : null;
                if (lv != null) LEVELS.put(p.id(), new Profile[] {level(p, lv, "3"), level(p, lv, "5")});
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** A level's row: the profile with that level's clips (a field the level leaves out keeps the level-1 clip). */
    private static Profile level(Profile p, JsonObject levels, String key) {
        if (!levels.has(key)) return p;
        JsonObject r = levels.getAsJsonObject(key);
        return new Profile(p.id(), p.power(), p.windup(), p.release(), p.fallback(), r.has("begin3p") ? r.get("begin3p").getAsString() : p.begin3p(),
                r.has("hold3p") ? str(r, "hold3p") : p.hold3p(), p.holdAt(), r.has("end3p") ? str(r, "end3p") : p.end3p(),
                r.has("begin1p") ? r.get("begin1p").getAsString() : p.begin1p(), r.has("hold1p") ? str(r, "hold1p") : p.hold1p(), p.channel(),
                p.moment(), p.endMoment(), p.lens(), p.origin(), p.beginSound());
    }

    /**
     * The profile at a power level: levels 3-4 and 5 have their own clips (the family's accent, the level-5 gesture; SPEC 34), with
     * the same id, timing and moments, so a level never changes when the power happens. Null in, null out.
     */
    public static @Nullable Profile at(@Nullable Profile p, int level) {
        if (p == null || level < 3) return p;
        Profile[] l = LEVELS.get(p.id());
        return l == null ? p : l[level >= 5 ? 1 : 0];
    }

    private static @Nullable String str(JsonObject r, String key) {
        JsonElement e = r.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
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

    /** Every profile row, the level rows included. */
    public static List<Profile> allLevels() {
        List<Profile> out = new ArrayList<>(LIST);
        for (Profile p : LIST) {
            Profile[] l = LEVELS.get(p.id());
            if (l != null) out.addAll(List.of(l));
        }
        return out;
    }

    /** Every third-person clip a profile names (at every level). */
    public static List<String> clips3p() {
        List<String> out = new ArrayList<>();
        for (Profile p : allLevels()) {
            out.add(p.begin3p());
            if (p.hold3p() != null) out.add(p.hold3p());
            if (p.end3p() != null) out.add(p.end3p());
        }
        return out;
    }

    /** Every first-person clip a profile names (at every level). */
    public static List<String> clips1p() {
        List<String> out = new ArrayList<>();
        for (Profile p : allLevels()) {
            out.add(p.begin1p());
            if (p.hold1p() != null) out.add(p.hold1p());
        }
        return out;
    }
}
