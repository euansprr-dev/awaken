package dev.awaken.powers.client.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.powers.Fx;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.Voice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * A power's voice on this client (SPEC §50.7, "Scores"; Euan, 10 Nov 2026: "the sounds are a big one"). The server names a
 * beat of a cast ({@code FxKind.VOICE}); this plays that beat's score from {@code assets/awaken/power_voices.json}: a few
 * layers, each one of the game's own recordings (an {@code awaken:voice.*} event in sounds.json that points at Minecraft's
 * files by name) at a volume, a pitch and a start tick. No two casts pick the same takes or quite the same pitch. A hit adds
 * the sound of the ground it lands on. A let-go cuts what was still gathering.
 * <p>
 * With Power sounds set to Classic, or for a power that has no score for the beat, the classic sound the event carries plays
 * instead, exactly as before.
 */
final class PowerVoice {
    private PowerVoice() {}

    static final Identifier FILE = Awaken.id("power_voices.json");
    static final int SCHEMA = 1;
    /** Layers of one beat that may sound, and how many of them a far listener hears. */
    private static final int MAX_LAYERS = 12, FAR_LAYERS = 2;
    private static final double FAR = 24 * 24;

    /** One layer: an event of ours, when it starts, how loud and how high, how much the pitch may wander, where to cut it (0: never). */
    private record Layer(Identifier event, int tick, float volume, float pitch, float jitter, int cutTicks) {}

    /** One beat of one power: its alternative sets, and what else goes with it. */
    private record Score(Layer[][] sets, boolean surface, int duck, boolean cut) {}

    private static @Nullable Map<String, Score[]> voices;
    private static boolean failed;

    static void reload() {
        voices = null;
        failed = false;
    }

    private static Map<String, Score[]> voices() {
        if (voices != null) return voices;
        Map<String, Score[]> out = new HashMap<>();
        voices = out;
        if (failed) return out;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(FILE);
            if (res.isEmpty()) return out;
            try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                if (root.get("schemaVersion").getAsInt() != SCHEMA) throw new IllegalStateException("schemaVersion " + root.get("schemaVersion"));
                for (Map.Entry<String, JsonElement> p : root.getAsJsonObject("voices").entrySet()) {
                    Score[] beats = new Score[Voice.Beat.values().length];
                    JsonObject o = p.getValue().getAsJsonObject();
                    for (Voice.Beat b : Voice.Beat.values()) if (o.has(b.key)) beats[b.ordinal()] = score(o.getAsJsonObject(b.key));
                    out.put(p.getKey(), beats);
                }
            }
        } catch (Exception ex) {
            failed = true;
            out.clear();
            Awaken.LOG.warn("Power voices could not be read; the classic sounds play", ex);
        }
        return out;
    }

    private static Score score(JsonObject o) {
        JsonArray sets = o.getAsJsonArray("sets");
        Layer[][] out = new Layer[sets.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonArray set = sets.get(i).getAsJsonArray();
            out[i] = new Layer[Math.min(MAX_LAYERS, set.size())];
            for (int j = 0; j < out[i].length; j++) {
                JsonArray l = set.get(j).getAsJsonArray();
                out[i][j] = new Layer(Awaken.id(l.get(0).getAsString()), l.get(1).getAsInt(), l.get(2).getAsFloat(), l.get(3).getAsFloat(),
                        l.size() > 4 ? l.get(4).getAsFloat() : 0.04f, l.size() > 5 ? l.get(5).getAsInt() : 0);
            }
        }
        return new Score(out, o.has("surface") && o.get("surface").getAsBoolean(), o.has("duck") ? o.get("duck").getAsInt() : 0,
                o.has("cut") && o.get("cut").getAsBoolean());
    }

    /** Whether a power has a score for a beat (the listening tools and the tests ask). */
    static boolean has(String power, Voice.Beat beat) {
        Score[] s = voices().get(power);
        return s != null && s[beat.ordinal()] != null;
    }

    // ---- sounds that are still to start or to stop --------------------------------------------------------------------

    /** A sounding layer we may have to stop: who cast it, the instance, when to cut it (FxWorld ticks, or never), whether a let-go cuts it. */
    private static final class Live {
        int caster = -1;
        SoundInstance sound;
        int stopAt;
        boolean onLetGo;
    }

    private static final Live[] LIVE = new Live[48];

    static {
        for (int i = 0; i < LIVE.length; i++) LIVE[i] = new Live();
    }

    private static void track(int caster, SoundInstance s, int stopAt, boolean onLetGo) {
        Live slot = null;
        for (Live l : LIVE) {
            if (l.sound == null) {
                slot = l;
                break;
            }
        }
        if (slot == null) return; // more than the ring holds: it plays out
        slot.caster = caster;
        slot.sound = s;
        slot.stopAt = stopAt;
        slot.onLetGo = onLetGo;
    }

    static void tick() {
        var sm = Minecraft.getInstance().getSoundManager();
        for (Live l : LIVE) {
            if (l.sound == null) continue;
            if (FxWorld.ticks >= l.stopAt) {
                sm.stop(l.sound);
                l.sound = null;
            }
        }
    }

    /** A caster let go of what they held: whatever was still gathering stops. */
    static void letGo(int caster) {
        var sm = Minecraft.getInstance().getSoundManager();
        for (Live l : LIVE) {
            if (l.sound != null && l.onLetGo && l.caster == caster) {
                sm.stop(l.sound);
                l.sound = null;
            }
        }
    }

    static void reset() {
        for (Live l : LIVE) l.sound = null;
    }

    // ---- playing ----------------------------------------------------------------------------------------------------

    private static final java.util.Random RNG = new java.util.Random();
    private static final Object DUCK = new Object();

    static void play(FxEvent e, ClientLevel level) {
        Voice.Beat beat = Voice.beat(e.ticks());
        PowerType type = e.power() > 0 ? PowerRegistry.byIndex(e.power() - 1) : null;
        if (beat == Voice.Beat.END || beat == Voice.Beat.RELEASE) letGo(e.caster());
        if (type == null || !playAt(level, type.path, beat, e.x(), e.y(), e.z(), e.scale(), e.seed(), e.caster(), Fx.levelOf(e))) classic(e, level);
    }

    /**
     * Plays a power's beat at a place from this client alone (a held charge that only its caster's hands start). False when the
     * player has chosen Classic or the power has no score for the beat: the caller plays the classic sound. A score with no
     * layers is a beat that is meant to be silent, and counts as played.
     */
    static boolean playAt(ClientLevel level, String power, Voice.Beat beat, double x, double y, double z, float scale, int seed, int caster, int powerLevel) {
        Score[] beats = !PowerFxConfig.newVoice() ? null : voices().get(power);
        Score score = beats == null ? null : beats[beat.ordinal()];
        if (score == null) return false;
        if (score.sets().length == 0) return true;
        float intensity = Math.max(0.2f, Math.min(1.5f, scale));
        RNG.setSeed(seed * 0x9E3779B97F4A7C15L + beat.ordinal());
        layers(x, y, z, caster, score, score.sets()[Math.floorMod(seed, score.sets().length)], intensity);
        // level 5 wears its own layer under the release, or under the hit when the power has one
        if (powerLevel >= 5 && (beat == Voice.Beat.IMPACT || beat == Voice.Beat.RELEASE && beats[Voice.Beat.IMPACT.ordinal()] == null)) {
            Score five = beats[Voice.Beat.LEVEL5.ordinal()];
            if (five != null && five.sets().length > 0) layers(x, y, z, caster, five, five.sets()[Math.floorMod(seed, five.sets().length)], intensity);
        }
        if (score.surface()) surface(level, x, y, z, intensity);
        if (score.duck() > 0 && near(x, y, z, 16 * 16)) dev.awaken.core.audio.client.Soundscape.muffle(DUCK, 1400f, 0.35f, score.duck());
        return true;
    }

    private static boolean near(double x, double y, double z, double within) {
        var cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz <= within;
    }

    private static void layers(double x, double y, double z, int caster, Score score, Layer[] set, float intensity) {
        var sm = Minecraft.getInstance().getSoundManager();
        int n = near(x, y, z, FAR) ? set.length : Math.min(FAR_LAYERS, set.length);
        for (int i = 0; i < n; i++) {
            Layer l = set[i];
            float pitch = Math.max(0.5f, Math.min(2f, l.pitch() * (1f + (RNG.nextFloat() * 2f - 1f) * l.jitter())));
            float volume = Math.min(1f, l.volume() * intensity);
            if (volume <= 0.01f) continue;
            SimpleSoundInstance s = new SimpleSoundInstance(l.event(), SoundSource.PLAYERS, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                    SoundInstance.Attenuation.LINEAR, x, y, z, false);
            if (l.tick() > 0) sm.playDelayed(s, l.tick());
            else sm.play(s);
            if (l.cutTicks() > 0 || score.cut()) {
                track(caster, s, l.cutTicks() > 0 ? FxWorld.ticks + l.tick() + l.cutTicks() : Integer.MAX_VALUE, score.cut());
            }
        }
    }

    /** The ground joins in: the break sound of the block the beat landed on, lower and under the score. */
    private static void surface(ClientLevel level, double x, double y, double z, float intensity) {
        BlockPos pos = BlockPos.containing(x, y - 0.3, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            pos = pos.below();
            state = level.getBlockState(pos);
            if (state.isAir()) return;
        }
        SoundType type = state.getSoundType();
        level.playLocalSound(x, y, z, type.getBreakSound(), SoundSource.PLAYERS, Math.min(1f, 0.8f * intensity), type.getPitch() * 0.72f, false);
    }

    /** The classic sound the event carries, as a plain sound event always played (and the ladder's layer with it). */
    private static void classic(FxEvent e, ClientLevel level) {
        if (e.colour() < 0) return;
        net.minecraft.sounds.SoundEvent ev = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.byId(e.colour());
        if (ev == null) return;
        level.playLocalSound(e.x(), e.y(), e.z(), ev, SoundSource.PLAYERS, e.dx(), e.aux(), false);
        LadderFx.tierSound(e, level);
    }
}
