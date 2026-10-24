package dev.awaken.turbo;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Turbo's on/off switches (SPEC §16). Read once, early: the mixin plugin asks before Minecraft's classes load, so a
 * change needs a restart. Sources, later wins: the defaults below, {@code config/awaken-turbo.properties}
 * ({@code name=on|off} lines), then {@code -Dawaken.turbo=name=on,name=off} (or just {@code off}) for experiments.
 * No Minecraft classes here: this class loads during mixin setup.
 */
public final class TurboSwitches {
    private TurboSwitches() {}

    public enum Feature {
        /** The master switch: off means Turbo does nothing at all. */
        TURBO("turbo", true),
        /** The compute bridge (TurboCompute) and its self-test. */
        COMPUTE("compute", true),
        /** A VkPipelineCache kept in the game folder (mixin on VulkanRenderPipeline.compile). */
        PIPELINE_CACHE("pipelineCache", false),
        /** shaderc's performance optimisation for Minecraft's own shaders (mixin on GlslCompiler). */
        SHADER_OPT("shaderOpt", false),
        /** Ask for VK_EXT_metal_objects at device creation (Pixels' MetalFX; off until MetalFX earns its place). */
        METAL_OBJECTS("metalObjects", false),
        /** The compute-built grid drawn after the sky (a dev showcase, also /awaken turbo demo). */
        DEMO("demo", false);

        public final String key;
        final boolean byDefault;

        Feature(String key, boolean byDefault) {
            this.key = key;
            this.byDefault = byDefault;
        }
    }

    private static Map<Feature, Boolean> values;

    /** True when the master switch and this feature's switch are both on. */
    public static boolean on(Feature f) {
        Map<Feature, Boolean> v = load();
        return v.get(Feature.TURBO) && v.get(f);
    }

    /** "turbo=on compute=on ..." for the log line. */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Feature, Boolean> e : load().entrySet()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(e.getKey().key).append('=').append(e.getValue() ? "on" : "off");
        }
        return sb.toString();
    }

    private static synchronized Map<Feature, Boolean> load() {
        if (values != null) return values;
        Map<Feature, Boolean> v = new EnumMap<>(Feature.class);
        for (Feature f : Feature.values()) v.put(f, f.byDefault);
        Path file = configDir().resolve("awaken-turbo.properties");
        if (Files.isRegularFile(file)) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
                for (String k : p.stringPropertyNames()) apply(v, k, p.getProperty(k));
            } catch (IOException | IllegalArgumentException e) {
                TurboVersions.LOG.warn("[turbo] can't read {}: {}", file, e.toString());
            }
        }
        String prop = System.getProperty("awaken.turbo");
        if (prop != null) {
            for (String part : prop.split(",")) {
                String s = part.trim();
                if (s.isEmpty()) continue;
                int eq = s.indexOf('=');
                if (eq < 0) apply(v, "turbo", s);
                else apply(v, s.substring(0, eq).trim(), s.substring(eq + 1).trim());
            }
        }
        values = v;
        return v;
    }

    private static void apply(Map<Feature, Boolean> v, String key, String value) {
        for (Feature f : Feature.values()) {
            if (f.key.equalsIgnoreCase(key)) {
                String s = value.trim().toLowerCase(Locale.ROOT);
                v.put(f, s.equals("on") || s.equals("true") || s.equals("1"));
                return;
            }
        }
        TurboVersions.LOG.warn("[turbo] unknown switch '{}'", key);
    }

    private static Path configDir() {
        try {
            return FMLPaths.CONFIGDIR.get();
        } catch (RuntimeException e) {
            return Path.of("config");
        }
    }
}
