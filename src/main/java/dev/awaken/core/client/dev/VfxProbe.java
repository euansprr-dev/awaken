package dev.awaken.core.client.dev;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;

import java.util.Map;
import java.util.TreeMap;

/**
 * Measurement only (the look-steady-60 plan): sets ImmersiveVFX's own settings from a tour, the way its menu does, so a
 * perf window can time the mod with one feature off. Reached through NeoForge's config registry by key, as
 * {@code compat.client.ImmersiveVfxLook} reaches it; nothing of the mod is compiled against or read.
 */
final class VfxProbe {
    private VfxProbe() {}

    /** {@code dump}, or {@code key=value;key=value}: what was set, or why not. */
    static String run(String arg) {
        ModConfigSpec spec = null;
        for (ModConfig config : ModConfigs.getModConfigs("immersivevfx")) {
            if (config.getType() == ModConfig.Type.CLIENT && config.getSpec() instanceof ModConfigSpec s && s.isLoaded()) spec = s;
        }
        if (spec == null) return "no loaded ImmersiveVFX client config";
        Map<String, ConfigValue<Object>> values = new TreeMap<>();
        collect(spec.getValues(), values);
        String a = arg.trim();
        StringBuilder out = new StringBuilder();
        if (a.equals("dump")) {
            values.forEach((k, v) -> {
                if (!k.startsWith("grading") && !k.equals("customValues")) out.append(k).append('=').append(v.get()).append(' ');
            });
            return out.toString().trim();
        }
        for (String pair : a.split(";")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = pair.substring(0, eq).trim(), text = pair.substring(eq + 1).trim();
            ConfigValue<Object> v = values.get(key);
            if (v == null) {
                out.append(key).append(":unknown ");
                continue;
            }
            Object value = parse(text, v.get());
            if (value == null) {
                out.append(key).append(":bad-value ");
                continue;
            }
            v.set(value);
            out.append(key).append('=').append(value).append(' ');
        }
        spec.save();
        return out.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static void collect(UnmodifiableConfig config, Map<String, ConfigValue<Object>> into) {
        for (Object value : config.valueMap().values()) {
            if (value instanceof UnmodifiableConfig inner) collect(inner, into);
            else if (value instanceof ConfigValue<?> v) into.put(String.join(".", v.getPath()), (ConfigValue<Object>) v);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parse(String text, Object like) {
        try {
            return switch (like) {
                case Boolean _ -> Boolean.valueOf(text);
                case Integer _ -> Integer.valueOf(text);
                case Long _ -> Long.valueOf(text);
                case Double _ -> Double.valueOf(text);
                case Enum<?> e -> Enum.valueOf((Class) e.getDeclaringClass(), text);
                case String _ -> text;
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
