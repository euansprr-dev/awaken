package dev.awaken.lens.client;

import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.lens.LensGrade;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The grades on offer and the one in use (SPEC §22 "Grades"): the lensGrade setting names a grade by its id, the Look
 * page steps through them, and what Lens draws eases from one to the next in about a third of a second. A grade is
 * laid over the place's look after it has eased, as a scale and an add on each of the look's numbers, plus the colour
 * step's own eight numbers and the near haze's thickness; a story moment's look plays as it was authored, so the grade
 * eases out for its length.
 */
final class LensGrades {
    private LensGrades() {}

    /** The grade's part of the parameter texture: the colour step's eight numbers, then the near haze over 1, halved. */
    static final int SHADER = LensGrade.COLOUR.length + 1;
    private static final List<LensGrade> ALL = new ArrayList<>(List.of(LensGrade.AWAKEN));
    // what is drawn now, eased toward the grade in use: the look's scale and add, and the shader's numbers
    private static final float[] SCALE = new float[Look.SIZE], ADD = new float[Look.SIZE], SHADE = new float[SHADER];
    private static final float[] toScale = new float[Look.SIZE], toAdd = new float[Look.SIZE], toShade = new float[SHADER];
    private static @Nullable LensGrade target;
    private static @Nullable String forced;
    private static boolean snap = true;

    static {
        java.util.Arrays.fill(SCALE, 1f);
        java.util.Arrays.fill(toScale, 1f);
    }

    /** Every {@code lens_grade/*.json} in every namespace; a later pack's file replaces an earlier one's of the same name. */
    static void reload(ResourceManager manager) {
        List<LensGrade> found = new ArrayList<>();
        for (var file : manager.listResources("lens_grade", id -> id.getPath().endsWith(".json")).entrySet()) {
            String path = file.getKey().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            if (id.equals(LensGrade.AWAKEN.id())) continue;
            try (Reader r = file.getValue().openAsReader()) {
                LensGrade g = LensGrade.parse(id, JsonParser.parseReader(r).getAsJsonObject());
                for (String key : g.scale().keySet()) if (Look.index(key) < 0) Awaken.LOG.warn("[lens] grade {}: the look has no number \"{}\", left out", id, key);
                for (String key : g.add().keySet()) if (Look.index(key) < 0) Awaken.LOG.warn("[lens] grade {}: the look has no number \"{}\", left out", id, key);
                found.removeIf(o -> o.id().equals(id));
                found.add(g);
            } catch (RuntimeException | java.io.IOException e) {
                Awaken.LOG.warn("[lens] grade {} unreadable, skipped: {}", file.getKey(), e.toString());
            }
        }
        found.sort(Comparator.comparingInt(LensGrade::order).thenComparing(LensGrade::id));
        ALL.clear();
        ALL.add(LensGrade.AWAKEN);
        ALL.addAll(found);
        target = null;
        Awaken.LOG.info("[lens] {} grades: {}", ALL.size(), ALL.stream().map(LensGrade::id).toList());
    }

    static List<LensGrade> all() {
        return ALL;
    }

    /** The grade in use: the autopilot's, else the setting's; the look as built when no grade has that id. */
    static LensGrade current() {
        String id = forced != null ? forced : AwakenConfig.Client.SPEC.isLoaded() ? AwakenConfig.Client.LENS_GRADE.get() : LensGrade.AWAKEN.id();
        for (LensGrade g : ALL) if (g.id().equalsIgnoreCase(id)) return g;
        return LensGrade.AWAKEN;
    }

    /** The grade {@code step} on from the one in use, wrapping. */
    static LensGrade next(int step) {
        return ALL.get(Math.floorMod(ALL.indexOf(current()) + step, ALL.size()));
    }

    /** Sets the setting, now and saved. */
    static void choose(LensGrade g) {
        AwakenConfig.Client.LENS_GRADE.set(g.id());
        AwakenConfig.Client.LENS_GRADE.save();
    }

    /** Autopilot {@code lens grade <id>|setting}: a grade for the following scenes without touching the setting. */
    static String force(String arg) {
        String a = arg.trim();
        forced = a.isEmpty() || a.equals("setting") ? null : a;
        snap = true;
        LensGrade g = current();
        return "grade " + g.id() + (forced == null ? " (the setting)" : g.id().equalsIgnoreCase(forced) ? "" : " (no grade \"" + forced + "\")")
                + ", of " + ALL.stream().map(LensGrade::id).toList();
    }

    /** Once a client tick: eases what is drawn toward the grade in use, or toward none while a story moment plays. */
    static void tick(boolean scene) {
        LensGrade g = scene ? LensGrade.AWAKEN : current();
        if (g != target) {
            target = g;
            java.util.Arrays.fill(toScale, 1f);
            java.util.Arrays.fill(toAdd, 0f);
            for (Map.Entry<String, Float> e : g.scale().entrySet()) {
                int i = Look.index(e.getKey());
                if (i >= 0) toScale[i] = e.getValue();
            }
            for (Map.Entry<String, Float> e : g.add().entrySet()) {
                int i = Look.index(e.getKey());
                if (i >= 0) toAdd[i] = e.getValue();
            }
            System.arraycopy(g.colour(), 0, toShade, 0, LensGrade.COLOUR.length);
            toShade[SHADER - 1] = (g.haze() - 1f) * 0.5f;
        }
        float k = snap ? 1f : 0.3f;
        snap = false;
        for (int i = 0; i < Look.SIZE; i++) {
            SCALE[i] += (toScale[i] - SCALE[i]) * k;
            ADD[i] += (toAdd[i] - ADD[i]) * k;
        }
        for (int i = 0; i < SHADER; i++) SHADE[i] += (toShade[i] - SHADE[i]) * k;
    }

    /** The look with the grade over it, into {@code out}. */
    static void apply(float[] look, float[] out) {
        for (int i = 0; i < Look.SIZE; i++) out[i] = look[i] * SCALE[i] + ADD[i];
    }

    /** The shader's numbers as drawn now ({@link #SHADER} of them, each 0..1). */
    static float[] shader() {
        return SHADE;
    }
}
