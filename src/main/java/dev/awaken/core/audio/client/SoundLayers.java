package dev.awaken.core.audio.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A sound made of several of Minecraft's own recordings (SPEC §66). sounds.json plays one recording an event, so the
 * rest of a recipe is added here wherever the event plays, each at its own volume, pitch and delay, at the same place:
 * a second flutter 60 ms after the first, the eleven knocks of a woodpecker's drum, the notes of a bird's phrase.
 * Any module adds a file {@code assets/awaken/sound_layers/<name>.json} (written by tools/art/sound_audit.py):
 * <pre>{"awaken:wild.flush": [{"sound": "minecraft:entity.bat.takeoff", "volume": 0.2, "pitch": 1.3, "delay": 0,
 *   "spread": 0.06, "jitter": 50, "chance": 1.0}]}</pre>
 * {@code delay} and {@code jitter} are milliseconds (a tick is 50), {@code spread} is the share a layer's pitch may
 * wander either way, {@code chance} how often it plays at all: so no two bursts are the same. Armour's own layers
 * (SPEC §44.12) stay with armour; an event listed there is never listed here.
 */
public final class SoundLayers {
    private SoundLayers() {}

    private record Layer(Identifier sound, float volume, float pitch, int delay, int jitter, float spread, float chance) {}

    private static Map<Identifier, Layer[]> layers = Map.of();
    /** Layers waiting for their tick. */
    private static final ArrayList<SoundInstance> LATER = new ArrayList<>();
    private static final ArrayList<Long> DUE = new ArrayList<>();
    private static final RandomSource R = RandomSource.create();
    private static long ticks;
    /** True while a layer is being started, so a layer never brings layers of its own. */
    private static boolean nested;

    static void init(IEventBus modBus) {
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("sound_layers"),
                (ResourceManagerReloadListener) SoundLayers::reload));
        NeoForge.EVENT_BUS.addListener(PlaySoundEvent.class, SoundLayers::onPlay);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            LATER.clear();
            DUE.clear();
        });
    }

    private static void reload(ResourceManager manager) {
        Map<Identifier, Layer[]> read = new HashMap<>();
        for (Map.Entry<Identifier, Resource> file : manager.listResources("sound_layers", id -> id.getPath().endsWith(".json")).entrySet()) {
            if (!file.getKey().getNamespace().equals(Awaken.MODID)) continue;
            try (var in = new InputStreamReader(file.getValue().open(), StandardCharsets.UTF_8)) {
                for (Map.Entry<String, JsonElement> entry : JsonParser.parseReader(in).getAsJsonObject().entrySet()) {
                    var list = entry.getValue().getAsJsonArray();
                    Layer[] out = new Layer[list.size()];
                    for (int i = 0; i < out.length; i++) {
                        JsonObject o = list.get(i).getAsJsonObject();
                        out[i] = new Layer(Identifier.parse(o.get("sound").getAsString()), num(o, "volume", 1F), num(o, "pitch", 1F),
                                Math.round(num(o, "delay", 0F) / 50F), Math.round(num(o, "jitter", 0F) / 50F), num(o, "spread", 0F),
                                num(o, "chance", 1F));
                    }
                    read.put(Identifier.parse(entry.getKey()), out);
                }
            } catch (Exception e) {
                Awaken.LOG.error("[audio] {} did not load, so its layered sounds play their first recording alone: {}", file.getKey(), e.toString());
            }
        }
        layers = read;
    }

    private static float num(JsonObject o, String key, float otherwise) {
        return o.has(key) ? o.get(key).getAsFloat() : otherwise;
    }

    /** How many events have layers (for the log and tests). */
    public static int count() {
        return layers.size();
    }

    /**
     * An event with layers is about to play: its other recordings go with it, at the same place, scaled by the volume
     * and pitch the caller asked for (a quiet call stays quiet, a pitched one stays in tune).
     */
    private static void onPlay(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (nested || s == null || layers.isEmpty()) return;
        Layer[] more = layers.get(s.getIdentifier());
        if (more == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (s.getOrResolve(mc.getSoundManager()) == null) return;
        Sound first = s.getSound();
        if (first == null) return;
        // what the caller asked for, without the first recording's own share
        var random = SoundInstance.createUnseededRandom();
        float volume = s.getVolume() / Math.max(0.01F, first.getVolume().sample(random));
        float pitch = s.getPitch() / Math.max(0.01F, first.getPitch().sample(random));
        for (Layer l : more) {
            if (l.chance < 1F && R.nextFloat() >= l.chance) continue;
            float p = pitch * l.pitch * (l.spread > 0F ? 1F + (R.nextFloat() * 2F - 1F) * l.spread : 1F);
            SoundInstance layer = new SimpleSoundInstance(l.sound, s.getSource(), volume * l.volume, p, random, false, 0,
                    s.getAttenuation(), s.getX(), s.getY(), s.getZ(), s.isRelative());
            int wait = l.delay + (l.jitter > 0 ? R.nextInt(l.jitter + 1) : 0);
            if (wait <= 0) {
                nested = true;
                try {
                    mc.getSoundManager().play(layer); // before the first recording starts: together
                } finally {
                    nested = false;
                }
            } else {
                LATER.add(layer);
                DUE.add(ticks + wait);
            }
        }
    }

    private static void tick() {
        ticks++;
        if (LATER.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        for (int i = LATER.size() - 1; i >= 0; i--) {
            if (DUE.get(i) > ticks) continue;
            SoundInstance layer = LATER.remove(i);
            DUE.remove(i);
            nested = true;
            try {
                mc.getSoundManager().play(layer);
            } finally {
                nested = false;
            }
        }
    }
}
