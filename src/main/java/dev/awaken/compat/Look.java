package dev.awaken.compat;

import dev.awaken.core.AwakenConfig.Client.LensLook;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The Look switch (docs/plan-pack.html "The one new screen", SPEC §14): one choice sets Lens, Distant Horizons and Iris
 * together and writes only the keys listed here, so every mod's own menu keeps working for everything else. Sodium
 * owns nothing: it has no supported way for another mod to set its options while the game runs. Plain Java with no
 * companion types, so the GameTests check the mapping, the config writing and the Ultra rule on their own.
 */
public final class Look {
    private Look() {}

    public enum Tier {
        SWIFT("SWIFT", "Horizon and soft light. Built for 120 fps.", LensLook.SWIFT, false, 1000f / 110f, 16f),
        CINEMATIC("CINEMATIC", "Haze, light shafts, a wider horizon. 60+ fps.", LensLook.CINEMATIC, false, 1000f / 60f, 25f),
        // Lens stays on CINEMATIC under Ultra: it steps aside while the shaderpack draws and is the look again if the
        // player turns shaders off in Iris's own menu
        ULTRA("ULTRA", "Full shaders with shadows and water. Experimental on Mac.", LensLook.CINEMATIC, true, 0f, 0f);

        public final String label, blurb;
        public final LensLook lens;
        public final boolean irisShaders;
        /** The frame-time budget the tier is built for (SPEC §14 Looks); 0 = none promised (Ultra is experimental). */
        public final float p50Ms, p99Ms;

        Tier(String label, String blurb, LensLook lens, boolean irisShaders, float p50Ms, float p99Ms) {
            this.label = label;
            this.blurb = blurb;
            this.lens = lens;
            this.irisShaders = irisShaders;
            this.p50Ms = p50Ms;
            this.p99Ms = p99Ms;
        }

        public String pretty() {
            return label.charAt(0) + label.substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Which tier the current settings are, or null when none (Lens off). A shaderpack on means Ultra. */
    public static @Nullable Tier current(LensLook lens, boolean shadersOn) {
        if (shadersOn) return Tier.ULTRA;
        return switch (lens) {
            case SWIFT -> Tier.SWIFT;
            case CINEMATIC -> Tier.CINEMATIC;
            case OFF -> null;
        };
    }

    /** Whether measured frame times fit the tier's budget; null when the tier promises none. */
    public static @Nullable Boolean fits(Tier tier, float p50Ms, float p99Ms) {
        if (tier.p50Ms <= 0f) return null;
        return p50Ms <= tier.p50Ms && p99Ms <= tier.p99Ms;
    }

    // ---- Distant Horizons -----------------------------------------------------------------------------------------

    /**
     * A Distant Horizons setting the switch owns: its table and key in config/DistantHorizons.toml, and whether DH
     * 3.3.4's config API can set it while the game runs ({@code live}); the others apply when DH next reads its file.
     */
    public record DhKey(String table, String key, boolean live) {
        public String path() {
            return table + "." + key;
        }
    }

    public static final DhKey RADIUS = new DhKey("client.advanced.graphics.quality", "lodChunkRenderDistanceRadius", true);
    public static final DhKey MAX_RESOLUTION = new DhKey("client.advanced.graphics.quality", "maxHorizontalResolution", true);
    public static final DhKey VERTICAL_QUALITY = new DhKey("client.advanced.graphics.quality", "verticalQuality", true);
    public static final DhKey HORIZONTAL_QUALITY = new DhKey("client.advanced.graphics.quality", "horizontalQuality", true);
    public static final DhKey SSAO = new DhKey("client.advanced.graphics", "enableSsao", true);
    public static final DhKey TEXTURED_LODS = new DhKey("client.advanced.graphics.texture", "enableTexturedLods", true);
    // not in DH 3.3.4's config API: written to its file, applies when DH next reads it (its own menu, or a restart)
    public static final DhKey ANTI_ALIASING = new DhKey("client.advanced.graphics", "enableAntiAliasing", false);

    /** Every DH key the switch writes. Keys the tiers agree on (threads, cloud layers) stay the player's. */
    public static final List<DhKey> DH_KEYS = List.of(RADIUS, MAX_RESOLUTION, VERTICAL_QUALITY, HORIZONTAL_QUALITY, SSAO,
            TEXTURED_LODS, ANTI_ALIASING);

    /** One owned key's value for a tier: an Integer, a Boolean, or an enum constant's name (a String). */
    public record DhSetting(DhKey key, Object value) {
        /** As DistantHorizons.toml writes it: enums quoted, numbers and booleans bare. */
        public String toml() {
            return value instanceof String s ? "\"" + s + "\"" : String.valueOf(value);
        }
    }

    // the same values as pack/dh/swift.json and pack/dh/cinematic.json (what tools/pack.py writes into a new game folder)
    private static final List<DhSetting> DH_SWIFT = List.of(new DhSetting(RADIUS, 128), new DhSetting(MAX_RESOLUTION, "TWO_BLOCKS"),
            new DhSetting(VERTICAL_QUALITY, "LOW"), new DhSetting(HORIZONTAL_QUALITY, "LOW"), new DhSetting(SSAO, false),
            new DhSetting(TEXTURED_LODS, false), new DhSetting(ANTI_ALIASING, false));
    private static final List<DhSetting> DH_CINEMATIC = List.of(new DhSetting(RADIUS, 192), new DhSetting(MAX_RESOLUTION, "BLOCK"),
            new DhSetting(VERTICAL_QUALITY, "MEDIUM"), new DhSetting(HORIZONTAL_QUALITY, "MEDIUM"), new DhSetting(SSAO, true),
            new DhSetting(TEXTURED_LODS, true), new DhSetting(ANTI_ALIASING, true));

    /** The tier's Distant Horizons values, in {@link #DH_KEYS} order. Ultra uses Cinematic's horizon (Iris draws it). */
    public static List<DhSetting> dh(Tier tier) {
        return tier == Tier.SWIFT ? DH_SWIFT : DH_CINEMATIC;
    }

    /**
     * Whether DH's live values still match the tier (the player may have changed them in DH's own menu since).
     * {@code current} answers with the same types as {@link DhSetting#value()}, or null for a key it can't read.
     */
    public static boolean dhMatches(Tier tier, Function<DhKey, @Nullable Object> current) {
        for (DhSetting s : dh(tier)) {
            if (!s.key().live()) continue;
            Object v = current.apply(s.key());
            if (v != null && !v.toString().equals(s.value().toString())) return false;
        }
        return true;
    }

    /** What writing a tier into DistantHorizons.toml did: the new text, the keys whose value changed, the keys added. */
    public record Edit(String text, List<String> changed, List<String> added) {}

    /**
     * Writes the owned keys into a DistantHorizons.toml text and leaves every other byte as it was: other keys,
     * comments, order, indentation and line endings. A key is matched by its table and name, so a key of the same
     * name in another table is never touched. Missing keys go under their table's header (a missing table at the end).
     */
    public static Edit writeDh(String toml, List<DhSetting> settings) {
        String[] lines = toml.split("\n", -1);
        List<String> out = new ArrayList<>(lines.length + settings.size() + 2);
        List<String> changed = new ArrayList<>(), added = new ArrayList<>();
        boolean[] found = new boolean[settings.size()];
        Map<String, Integer> headerAt = new java.util.HashMap<>();
        Map<String, String> headerIndent = new java.util.HashMap<>();
        String table = "";
        for (String raw : lines) {
            String cr = raw.endsWith("\r") ? "\r" : "";
            String line = cr.isEmpty() ? raw : raw.substring(0, raw.length() - 1);
            String t = line.strip();
            if (t.startsWith("[") && !t.startsWith("[[") && t.endsWith("]")) {
                table = t.substring(1, t.length() - 1).strip();
                headerAt.put(table, out.size());
                headerIndent.put(table, line.substring(0, line.indexOf('[')));
                out.add(raw);
                continue;
            }
            int eq = line.indexOf('=');
            if (t.isEmpty() || t.startsWith("#") || eq < 0) {
                out.add(raw);
                continue;
            }
            String key = line.substring(0, eq).strip();
            int i = indexOf(settings, table, key);
            if (i < 0) {
                out.add(raw);
                continue;
            }
            found[i] = true;
            String indent = line.substring(0, line.length() - line.stripLeading().length());
            String value = settings.get(i).toml();
            if (!line.substring(eq + 1).strip().equals(value)) changed.add(settings.get(i).key().path());
            out.add(indent + key + " = " + value + cr);
        }
        // missing keys: right under their table's header, one level deeper; a missing table goes at the end
        String eol = toml.contains("\r\n") ? "\r" : "";
        for (int i = settings.size() - 1; i >= 0; i--) {
            if (found[i]) continue;
            DhSetting s = settings.get(i);
            Integer at = headerAt.get(s.key().table());
            String entry = s.key().key() + " = " + s.toml() + eol;
            added.addFirst(s.key().path());
            if (at != null) {
                out.add(at + 1, headerIndent.get(s.key().table()) + "\t" + entry);
                for (var e : headerAt.entrySet()) if (e.getValue() > at) e.setValue(e.getValue() + 1);
            } else {
                int end = out.size();
                // keep a final empty line (the file's last newline) last
                if (end > 0 && out.get(end - 1).strip().isEmpty()) end--;
                out.add(end, "[" + s.key().table() + "]" + eol);
                out.add(end + 1, "\t" + entry);
                headerAt.put(s.key().table(), end);
                headerIndent.put(s.key().table(), "");
            }
        }
        return new Edit(String.join("\n", out), List.copyOf(changed), List.copyOf(added));
    }

    private static int indexOf(List<DhSetting> settings, String table, String key) {
        for (int i = 0; i < settings.size(); i++) {
            DhKey k = settings.get(i).key();
            if (k.table().equals(table) && k.key().equals(key)) return i;
        }
        return -1;
    }

    // ---- Iris -------------------------------------------------------------------------------------------------------

    /** Whether Ultra can be offered, and if not, the honest reason. */
    public record Availability(boolean available, String reason) {}

    /** The newest Iris known not to run here: 1.11.7 pins Sodium 0.9.2, and Sodium 0.9.2 crashes on NeoForge 26.3.0.95. */
    public static final String IRIS_KNOWN_BAD = "1.11.7";

    /**
     * Ultra needs Iris newer than {@link #IRIS_KNOWN_BAD} on the OpenGL renderer (Iris draws only there). Every reason
     * says Iris needs OpenGL (the pack runs on Vulkan); until a newer Iris is installed it also names Iris's Sodium pin.
     * Short enough for the Look screen's three note lines. {@code irisVersion} is null when Iris isn't installed;
     * {@code backend} is the device's backend name.
     */
    public static Availability ultra(@Nullable String irisVersion, String backend) {
        boolean openGl = "OpenGL".equalsIgnoreCase(backend);
        String api = openGl ? "needs OpenGL." : "needs OpenGL; this game runs on " + backend + ".";
        String pin = " pins Sodium 0.9.2, which crashes on this NeoForge: Ultra waits for an Iris update. Iris also " + api;
        if (irisVersion == null) return new Availability(false, "Needs Iris. Iris " + IRIS_KNOWN_BAD + pin);
        if (compareVersions(irisVersion, IRIS_KNOWN_BAD) <= 0) return new Availability(false, "Iris " + shortVersion(irisVersion) + pin);
        if (!openGl) return new Availability(false, "Iris " + api + " Switch in Video Settings → Graphics API, then restart.");
        return new Availability(true, "");
    }

    /** Compares the leading numbers of two versions ("1.11.7+26.3-neoforge" reads as 1.11.7). */
    public static int compareVersions(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    private static int[] numbers(String v) {
        String head = shortVersion(v);
        String[] parts = head.split("\\.");
        int[] n = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String digits = parts[i].replaceAll("\\D.*$", "");
            n[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        return n;
    }

    private static String shortVersion(String v) {
        int cut = v.length();
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '+' || c == '-' || c == ' ') {
                cut = i;
                break;
            }
        }
        return v.substring(0, cut);
    }
}
