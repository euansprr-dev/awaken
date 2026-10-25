package dev.awaken.core.audio.client.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.core.audio.world.SoundscapeDef;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every soundscape the resources hold ({@code assets/<ns>/soundscape/**.json}), read again on every resource reload,
 * and the encoded bytes of the air layers they name, loaded once on an IO thread and kept (a few hundred KB each; the
 * decoder reads them lap after lap).
 */
final class Soundscapes {
    private Soundscapes() {}

    private static Map<Identifier, SoundscapeDef> defs = Map.of();
    private static final Map<Identifier, byte[]> BYTES = new ConcurrentHashMap<>();
    private static final Map<Identifier, Boolean> LOADING = new ConcurrentHashMap<>();

    static void reload(ResourceManager manager) {
        Map<Identifier, SoundscapeDef> found = new HashMap<>();
        for (Map.Entry<Identifier, Resource> e : manager.listResources("soundscape", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            String path = file.getPath();
            Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), path.substring("soundscape/".length(), path.length() - 5));
            try (Reader in = e.getValue().openAsReader()) {
                JsonObject o = JsonParser.parseReader(in).getAsJsonObject();
                found.put(id, SoundscapeDef.parse(id, o));
            } catch (Exception ex) {
                Awaken.LOG.warn("[audio] world: soundscape {} skipped: {}", id, ex.toString());
            }
        }
        defs = Map.copyOf(found);
        BYTES.clear();
        LOADING.clear();
        Awaken.LOG.info("[audio] world: {} soundscapes", defs.size());
    }

    static SoundscapeDef get(Identifier id) {
        return defs.get(id);
    }

    static int count() {
        return defs.size();
    }

    /** An air layer's encoded bytes, or null while they load (the first ask starts the load). */
    static byte[] bytes(Identifier file) {
        byte[] b = BYTES.get(file);
        if (b != null || LOADING.putIfAbsent(file, Boolean.TRUE) != null) return b;
        Identifier path = Identifier.fromNamespaceAndPath(file.getNamespace(), "sounds/" + file.getPath() + ".ogg");
        Util.nonCriticalIoPool().execute(() -> {
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(path)) {
                BYTES.put(file, in.readAllBytes());
            } catch (Exception e) {
                Awaken.LOG.warn("[audio] world: air layer {} can't be read: {}", path, e.toString());
            }
        });
        return null;
    }
}
