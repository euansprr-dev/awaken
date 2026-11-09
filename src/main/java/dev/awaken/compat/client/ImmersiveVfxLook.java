package dev.awaken.compat.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.compat.Look;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

/**
 * ImmersiveVFX awake or asleep with the Look (SPEC §14, the VFX tier). The mod has no off switch and a jar can't be
 * unloaded, so asleep is every one of its settings off or vanilla, set the way its own menu sets them
 * ({@link ModConfigSpec#save()} writes the file and fires the reload it listens for). It's reached through NeoForge's
 * config registry by key, so Awaken doesn't compile against it and a setting a later version renames is simply skipped.
 * What the player had waits in a small file while it sleeps and comes back when VFX is chosen again.
 */
final class ImmersiveVfxLook {
    private ImmersiveVfxLook() {}

    private static final int SCHEMA_VERSION = 1;
    private static final Path KEPT = FMLPaths.CONFIGDIR.get().resolve("awaken-immersivevfx-kept.json");

    /** Wakes the mod or puts it to sleep; nothing when it's already so. Never throws: the rest of the Look still applies. */
    static void set(boolean on) {
        try {
            ModConfigSpec spec = spec();
            if (spec == null) {
                Awaken.LOG.warn("[look] ImmersiveVFX has no loaded client config: left as it is");
                return;
            }
            Map<String, ConfigValue<Object>> values = new TreeMap<>();
            collect(spec.getValues(), values);
            if (on ? wake(values) : sleep(values)) {
                spec.save();
                Awaken.LOG.info("[look] ImmersiveVFX {}", on ? "awake" : "asleep");
            }
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.warn("[look] couldn't set ImmersiveVFX {}: {}", on ? "awake" : "asleep", e.toString());
        }
    }

    private static @Nullable ModConfigSpec spec() {
        for (ModConfig config : ModConfigs.getModConfigs(Look.VFX_MOD)) {
            if (config.getType() == ModConfig.Type.CLIENT && config.getSpec() instanceof ModConfigSpec spec && spec.isLoaded()) return spec;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void collect(UnmodifiableConfig config, Map<String, ConfigValue<Object>> into) {
        for (Object value : config.valueMap().values()) {
            if (value instanceof UnmodifiableConfig inner) collect(inner, into);
            else if (value instanceof ConfigValue<?> v) into.put(String.join(".", v.getPath()), (ConfigValue<Object>) v);
        }
    }

    private static boolean sleep(Map<String, ConfigValue<Object>> values) throws IOException {
        if (asleep(values)) return false;
        // already kept means the player's own menu woke part of it while another tier was on: their real settings are the kept ones
        if (!Files.exists(KEPT)) keep(values);
        values.forEach((key, v) -> v.set(asleepValue(key, v.get())));
        return true;
    }

    private static boolean wake(Map<String, ConfigValue<Object>> values) throws IOException {
        JsonObject kept = kept();
        if (kept == null) {
            // nothing kept: it's awake as the player left it, unless the file was lost while it slept
            if (!asleep(values)) return false;
            values.values().forEach(v -> v.set(v.getDefault()));
        } else {
            values.forEach((key, v) -> {
                Object value = kept.has(key) ? parse(kept.get(key).getAsString(), v.get()) : null;
                if (value != null) v.set(value);
            });
        }
        Files.deleteIfExists(KEPT);
        return true;
    }

    private static boolean asleep(Map<String, ConfigValue<Object>> values) {
        for (var e : values.entrySet()) {
            Object now = e.getValue().get();
            if (!now.equals(asleepValue(e.getKey(), now))) return false;
        }
        return true;
    }

    private static Object asleepValue(String key, Object now) {
        if (now instanceof Boolean) return Boolean.FALSE;
        String named = Look.VFX_ASLEEP.get(key);
        Object value = named == null ? null : parse(named, now);
        return value == null ? now : value;
    }

    private static void keep(Map<String, ConfigValue<Object>> values) throws IOException {
        JsonObject kept = new JsonObject();
        values.forEach((key, v) -> kept.addProperty(key, v.get() instanceof Enum<?> e ? e.name() : String.valueOf(v.get())));
        JsonObject file = new JsonObject();
        file.addProperty("schemaVersion", SCHEMA_VERSION);
        file.add("values", kept);
        Path tmp = KEPT.resolveSibling(KEPT.getFileName() + ".tmp");
        Files.writeString(tmp, file.toString());
        Files.move(tmp, KEPT, StandardCopyOption.REPLACE_EXISTING);
    }

    /** The kept settings, or null when there are none or the file can't be read. */
    private static @Nullable JsonObject kept() {
        try {
            return Files.exists(KEPT) ? JsonParser.parseString(Files.readString(KEPT)).getAsJsonObject().getAsJsonObject("values") : null;
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.warn("[look] couldn't read {}: {}", KEPT.getFileName(), e.toString());
            return null;
        }
    }

    /** Text as a value of the same type as {@code like}, or null when it can't be (a choice a later version dropped). */
    private static @Nullable Object parse(String text, Object like) {
        try {
            return switch (like) {
                case Boolean _ -> Boolean.valueOf(text);
                case Integer _ -> Integer.valueOf(text);
                case Long _ -> Long.valueOf(text);
                case Double _ -> Double.valueOf(text);
                case Enum<?> e -> named(e, text);
                case String _ -> text;
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object named(Enum<?> like, String name) {
        return Enum.valueOf((Class) like.getDeclaringClass(), name);
    }
}
