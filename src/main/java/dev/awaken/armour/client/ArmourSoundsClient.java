package dev.awaken.armour.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.ArmourSounds;
import dev.awaken.armour.ForgeSong;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * The client's part of armour's sounds (SPEC §44.12). Three things:
 * <ul>
 * <li><b>Layers.</b> sounds.json plays one recording an event; a sound made of two of Minecraft's recordings has its
 * second (and third) added here, each at its own volume, pitch and delay, wherever the event plays
 * ({@code assets/awaken/armour/sound_layers.json}, written by tools/art/armour_sounds.py).</li>
 * <li><b>Movement.</b> One soft sound a stride from every armoured player near, by what their cuirass is made of:
 * louder at a run, silent at a sneak, with its own level in the settings.</li>
 * <li><b>The three notes</b> when a set becomes whole on someone: the first three of the Forge Song it is made with.</li>
 * </ul>
 */
final class ArmourSoundsClient {
    private ArmourSoundsClient() {}

    /** One more recording under an event: Minecraft's own event by name, its share of the volume and pitch, ticks late. */
    private record Layer(Identifier sound, float volume, float pitch, int delay) {}

    private static Map<Identifier, Layer[]> layers = Map.of();
    /** Sounds waiting for their tick (a layer that starts late, the second and third notes). */
    private static final ArrayList<SoundInstance> LATER = new ArrayList<>();
    private static final ArrayList<Long> DUE = new ArrayList<>();
    private static long ticks;

    static void init(IEventBus modBus) {
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("armour_sound_layers"),
                (ResourceManagerReloadListener) ArmourSoundsClient::reload));
        NeoForge.EVENT_BUS.addListener(PlaySoundEvent.class, ArmourSoundsClient::onPlay);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            LATER.clear();
            DUE.clear();
            WALKED.clear();
        });
    }

    private static void reload(ResourceManager manager) {
        Map<Identifier, Layer[]> read = new HashMap<>();
        var res = manager.getResource(Awaken.id("armour/sound_layers.json"));
        if (res.isPresent()) {
            try (var in = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                for (var entry : JsonParser.parseReader(in).getAsJsonObject().entrySet()) {
                    var list = entry.getValue().getAsJsonArray();
                    Layer[] out = new Layer[list.size()];
                    for (int i = 0; i < out.length; i++) {
                        JsonObject o = list.get(i).getAsJsonObject();
                        out[i] = new Layer(Identifier.parse(o.get("sound").getAsString()), o.get("volume").getAsFloat(), o.get("pitch").getAsFloat(),
                                Math.round(o.get("delay").getAsFloat() / 50F));
                    }
                    read.put(Identifier.parse(entry.getKey()), out);
                }
            } catch (IOException | RuntimeException e) {
                Awaken.LOG.error("[armour] sound_layers.json did not load, so layered sounds play their first recording alone: {}", e.toString());
                read.clear();
            }
        }
        layers = read;
    }

    /**
     * An event of ours with layers is about to play: its other recordings go with it, at the same place, scaled by the
     * volume and pitch the caller asked for (a pitched note stays in tune, a quiet strike stays quiet).
     */
    private static void onPlay(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || layers.isEmpty()) return;
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
            SoundInstance layer = new SimpleSoundInstance(l.sound, s.getSource(), volume * l.volume, pitch * l.pitch, random, false, 0,
                    s.getAttenuation(), s.getX(), s.getY(), s.getZ(), s.isRelative());
            if (l.delay <= 0) mc.getSoundManager().play(layer); // nested, before the first recording starts: together
            else later(layer, l.delay);
        }
    }

    private static void later(SoundInstance sound, int inTicks) {
        LATER.add(sound);
        DUE.add(ticks + inTicks);
    }

    // ---- movement ------------------------------------------------------------------------------------------------------

    /** A stride's length in blocks, how near a player must be to be heard, and the setting's default (at it a stride
     * plays as loud as its recipe says). */
    private static final float STRIDE = 1.7F, NEAR = 12F, DEFAULT_LEVEL = 0.6F;
    /** How far each player near has walked since their armour last sounded (entity id → blocks). */
    private static final Int2FloatOpenHashMap WALKED = new Int2FloatOpenHashMap();

    private static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        for (int i = LATER.size() - 1; i >= 0; i--) {
            if (DUE.get(i) > ticks) continue;
            mc.getSoundManager().play(LATER.remove(i));
            DUE.remove(i);
        }
        float level = ArmourClientConfig.moveSound();
        if (mc.level == null || mc.player == null || mc.isPaused() || level <= 0F) return;
        if (WALKED.size() > 256) WALKED.clear();
        Entity camera = mc.getCameraEntity() == null ? mc.player : mc.getCameraEntity();
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(camera) > NEAR * NEAR || !p.onGround() || p.isPassenger() || p.isSpectator()) continue;
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            float step = (float) Math.sqrt(dx * dx + dz * dz);
            if (step < 0.01F || step > 2F) continue; // standing, or moved by something that is not walking
            float walked = WALKED.get(p.getId()) + step;
            if (walked < STRIDE) {
                WALKED.put(p.getId(), walked);
                continue;
            }
            WALKED.put(p.getId(), 0F);
            if (p.isShiftKeyDown()) continue; // sneaking is silent
            ArmourSounds.Family family = family(p);
            if (family == null) continue;
            float volume = level / DEFAULT_LEVEL * family.stride * (p.isSprinting() ? 1.25F : 1F);
            mc.level.playLocalSound(p.getX(), p.getY() + 0.9, p.getZ(), ArmourSounds.move(family), SoundSource.PLAYERS, volume,
                    0.94F + p.getRandom().nextFloat() * 0.12F, false);
        }
    }

    /** What the player's set sounds like: their cuirass's family, else their greaves'; null in neither. */
    private static ArmourSounds.@org.jspecify.annotations.Nullable Family family(Player p) {
        ArmourLooks.Look look = ArmourLooks.of(p.getItemBySlot(EquipmentSlot.CHEST));
        if (look == null) look = ArmourLooks.of(p.getItemBySlot(EquipmentSlot.LEGS));
        return look == null ? null : ArmourSounds.of(look.set());
    }

    // ---- a set becomes whole -------------------------------------------------------------------------------------------

    /** The gap between the three notes, in ticks. */
    private static final int NOTE_GAP = 3;

    /**
     * The set's three notes at its wearer (SPEC §44.11): the first three of the Forge Song it is made with, the last a
     * little louder, the stand's wake sound quiet under them.
     */
    static void whole(int entity, int setOrdinal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || setOrdinal < 0 || setOrdinal >= ArmourSet.values().length) return;
        Entity e = mc.level.getEntity(entity);
        if (e == null) return;
        int[] melody = ForgeSong.song(ArmourSet.values()[setOrdinal]).melody();
        var random = SoundInstance.createUnseededRandom();
        for (int i = 0; i < 3 && melody.length > 0; i++) {
            SoundInstance note = new SimpleSoundInstance(ArmourContent.NOTE.get(), SoundSource.PLAYERS, i == 2 ? 0.8F : 0.6F,
                    ForgeSong.notePitch(melody[Math.min(i, melody.length - 1)]), random, e.getX(), e.getY() + 1.0, e.getZ());
            if (i == 0) mc.getSoundManager().play(note);
            else later(note, i * NOTE_GAP);
        }
        mc.level.playLocalSound(e.getX(), e.getY() + 1.0, e.getZ(), ArmourContent.WAKE.get(), SoundSource.PLAYERS, 0.35F, 1F, false);
    }
}
