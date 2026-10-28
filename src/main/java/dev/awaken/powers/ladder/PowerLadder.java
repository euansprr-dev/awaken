package dev.awaken.powers.ladder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every power's ladder (SPEC 34): levels 2-5 each add a named step with a look and a play change. The table is
 * {@code data/awaken/powers/ladders.json}, written with its text ({@code awaken.ladder.<power>.<level>.name|look|does|body})
 * by tools/art/specs/power_ladder_table.py; both sides read it from the jar. Public API for other modules (Codex hover,
 * the Journal): {@link #name}, {@link #does}, {@link #text}.
 */
public final class PowerLadder {
    private PowerLadder() {}

    public static final String FILE = "data/awaken/powers/ladders.json";
    public static final int SCHEMA = 1;
    public static final int TOP = Loadout.MAX_LEVEL;

    /**
     * One step: its riders, its look layers (read by the client's LadderFx), the numbers its own code reads and whether its
     * play change is code (rather than riders alone).
     */
    public record Step(String power, int level, List<Rider> riders, List<JsonObject> looks, Map<String, Float> num, boolean code) {}

    public record Entry(String power, String family, boolean starter, boolean hidden, List<Step> steps) {}

    private static final Map<String, Entry> BY_POWER = new LinkedHashMap<>();
    private static final Map<String, Kit[]> KITS = new HashMap<>();

    static {
        try (InputStream in = PowerLadder.class.getClassLoader().getResourceAsStream(FILE)) {
            if (in == null) throw new IllegalStateException("missing " + FILE + " (python3 tools/art/specs/power_ladder_table.py build)");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int schema = root.get("schemaVersion").getAsInt();
            if (schema != SCHEMA) throw new IllegalStateException(FILE + " schemaVersion " + schema + ", expected " + SCHEMA);
            for (var e : root.getAsJsonObject("powers").entrySet()) {
                JsonObject p = e.getValue().getAsJsonObject();
                List<Step> steps = new ArrayList<>();
                for (JsonElement se : p.getAsJsonArray("steps")) {
                    JsonObject s = se.getAsJsonObject();
                    List<Rider> riders = new ArrayList<>();
                    for (JsonElement r : s.getAsJsonArray("riders")) riders.add(Rider.parse(r.getAsJsonObject()));
                    List<JsonObject> looks = new ArrayList<>();
                    for (JsonElement l : s.getAsJsonArray("looks")) looks.add(l.getAsJsonObject());
                    Map<String, Float> num = new HashMap<>();
                    for (var n : s.getAsJsonObject("num").entrySet()) num.put(n.getKey(), n.getValue().getAsFloat());
                    steps.add(new Step(e.getKey(), s.get("level").getAsInt(), List.copyOf(riders), List.copyOf(looks), Map.copyOf(num),
                            s.get("code").getAsBoolean()));
                }
                BY_POWER.put(e.getKey(), new Entry(e.getKey(), p.get("family").getAsString(), p.get("starter").getAsBoolean(),
                        p.get("hidden").getAsBoolean(), List.copyOf(steps)));
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String path(String power) {
        int colon = power.indexOf(':');
        return colon >= 0 ? power.substring(colon + 1) : power;
    }

    public static Set<String> powers() {
        return Collections.unmodifiableSet(BY_POWER.keySet());
    }

    public static @Nullable Entry entry(String power) {
        return BY_POWER.get(path(power));
    }

    /** Steps 2..5 of a power (empty for one without a ladder). */
    public static List<Step> steps(String power) {
        Entry e = entry(power);
        return e == null ? List.of() : e.steps();
    }

    public static @Nullable Step step(String power, int level) {
        for (Step s : steps(power)) if (s.level() == level) return s;
        return null;
    }

    /** What a power adds at a level, summed over its steps (cached; level 1 and unknown powers: {@link Kit#NONE}). */
    public static Kit kit(PowerType type, int level) {
        if (level <= 1) return Kit.NONE;
        Kit[] kits = KITS.computeIfAbsent(type.path, p -> new Kit[TOP + 1]);
        int l = Math.min(TOP, level);
        Kit k = kits[l];
        if (k == null) kits[l] = k = steps(type.path).isEmpty() ? Kit.NONE : new Kit(l, steps(type.path));
        return k;
    }

    /** The cooldown's scale at a level: every step up to it that names a {@code cd} multiplies it (Blueprint halves it). */
    public static float cdScale(String power, int level) {
        float k = 1f;
        for (Step s : steps(power)) {
            Float v = s.level() <= level ? s.num().get("cd") : null;
            if (v != null) k *= v;
        }
        return k;
    }

    /** A number a step's own code reads, or {@code def} when the table has none. */
    public static float num(String power, int level, String key, float def) {
        Step s = step(power, level);
        if (s == null) return def;
        Float v = s.num().get(key);
        return v == null ? def : v;
    }

    private static String key(String power, int level, String part) {
        return "awaken.ladder." + path(power) + "." + level + "." + part;
    }

    /** The step's name; level 1 is the power's own name. */
    public static Component name(String power, int level) {
        if (level <= 1 || step(power, level) == null) {
            PowerType t = PowerRegistry.get(power);
            return Component.literal(t == null ? path(power) : t.name);
        }
        return Component.translatable(key(power, level, "name"));
    }

    /** What the step changes in play (empty at level 1). */
    public static Component does(String power, int level) {
        return level <= 1 || step(power, level) == null ? Component.empty() : Component.translatable(key(power, level, "does"));
    }

    /** What the step looks like. */
    public static Component look(String power, int level) {
        return level <= 1 || step(power, level) == null ? Component.empty() : Component.translatable(key(power, level, "look"));
    }

    /** The level-5 cast gesture, in words. */
    public static Component body(String power) {
        return step(power, TOP) == null ? Component.empty() : Component.translatable(key(power, TOP, "body"));
    }

    /** One line for a hover or a list: "Lance: the beam goes through one foe for 60%." */
    public static Component text(String power, int level) {
        if (level <= 1 || step(power, level) == null) return name(power, level);
        return Component.empty().append(name(power, level)).append(": ").append(does(power, level));
    }

    /** Every lang key the table promises (tests hold the lang file to it). */
    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        for (Entry e : BY_POWER.values()) {
            for (Step s : e.steps()) {
                for (String part : new String[]{"name", "look", "does"}) out.add(key(e.power(), s.level(), part));
                if (s.level() == TOP) out.add(key(e.power(), TOP, "body"));
            }
        }
        return out;
    }

    /** The look layers of a step (client LadderFx parses them). */
    public static List<JsonObject> looks(String power, int level) {
        Step s = step(power, level);
        return s == null ? List.of() : s.looks();
    }
}
