package dev.awaken.core.audio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Progression;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * GameTests for the audio module (tools/gradle.sh 1200 runGameTestServer, or /test run awaken:audio_*):
 * every sound event resolves to real files and has a subtitle, the music sits in the biomes with vanilla-length
 * silences, the cue cooldowns hold, and the payloads round-trip.
 */
final class AudioTests {
    private AudioTests() {}

    static final java.util.Set<String> POSITIONAL_UI = java.util.Set.of("ui_level_up", "ui_rank_up", "ui_warning");

    static void register() {
        AwakenTests.register("audio_events_resolve", 40, AudioTests::eventsResolve);
        AwakenTests.register("audio_cues_have_subtitles", 40, AudioTests::cuesHaveSubtitles);
        AwakenTests.register("audio_biome_music", 40, AudioTests::biomeMusic);
        AwakenTests.register("audio_cue_cooldowns", 40, AudioTests::cooldowns);
        AwakenTests.register("audio_cue_triggers", 60, AudioTests::triggers);
        AwakenTests.register("audio_payloads_round_trip", 40, AudioTests::payloads);
    }

    // ---- resources ---------------------------------------------------------------------------------------------------

    static JsonObject json(String path) {
        try (InputStream in = AudioTests.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            return null;
        }
    }

    /** {channels, sample rate} from an Ogg Vorbis identification header, or null if the file isn't one. */
    static int[] vorbisHeader(String path) {
        try (InputStream in = AudioTests.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] b = in.readNBytes(128);
            if (b.length < 58 || b[0] != 'O' || b[1] != 'g' || b[2] != 'g' || b[3] != 'S') return null;
            for (int i = 4; i + 16 < b.length; i++) {
                if (b[i] == 1 && b[i + 1] == 'v' && b[i + 2] == 'o' && b[i + 3] == 'r' && b[i + 4] == 'b' && b[i + 5] == 'i' && b[i + 6] == 's') {
                    int ch = b[i + 11] & 0xFF;
                    int rate = (b[i + 12] & 0xFF) | (b[i + 13] & 0xFF) << 8 | (b[i + 14] & 0xFF) << 16 | (b[i + 15] & 0xFF) << 24;
                    return new int[]{ch, rate};
                }
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    static String sub(JsonElement s) {
        return s.isJsonPrimitive() ? s.getAsString() : s.getAsJsonObject().get("name").getAsString();
    }

    static boolean isEvent(JsonElement s) {
        return s.isJsonObject() && s.getAsJsonObject().has("type") && s.getAsJsonObject().get("type").getAsString().equals("event");
    }

    // ---- tests ---------------------------------------------------------------------------------------------------------

    /** Every awaken sound event is in sounds.json, has a subtitle with a lang line, and every sound it names exists. */
    static void eventsResolve(GameTestHelper h) {
        JsonObject sounds = json("/assets/awaken/sounds.json");
        JsonObject lang = json("/assets/awaken/lang/en_us.json");
        h.assertTrue(sounds != null && lang != null, "merged sounds.json and en_us.json are on the classpath");
        List<String> problems = new ArrayList<>();
        // registered -> described
        for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (id.getNamespace().equals("awaken") && !sounds.has(id.getPath())) problems.add("registered but not in sounds.json: " + id);
        }
        int files = 0;
        for (var e : sounds.entrySet()) {
            JsonObject ev = e.getValue().getAsJsonObject();
            if (!BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.fromNamespaceAndPath("awaken", e.getKey()))) {
                problems.add("in sounds.json but never registered: " + e.getKey());
            }
            String subtitle = ev.has("subtitle") ? ev.get("subtitle").getAsString() : null;
            if (subtitle == null) problems.add(e.getKey() + ": no subtitle");
            else if (!lang.has(subtitle)) problems.add(e.getKey() + ": subtitle " + subtitle + " has no lang line");
            for (JsonElement s : ev.getAsJsonArray("sounds")) {
                String name = sub(s);
                Identifier ref = Identifier.parse(name);
                if (isEvent(s)) {
                    if (!BuiltInRegistries.SOUND_EVENT.containsKey(ref)) problems.add(e.getKey() + ": refers to unknown event " + name);
                } else if (ref.getNamespace().equals("awaken")) {
                    String path = "/assets/awaken/sounds/" + ref.getPath() + ".ogg";
                    int[] hdr = vorbisHeader(path);
                    files++;
                    if (hdr == null) problems.add(e.getKey() + ": " + path + " missing or not Ogg Vorbis");
                    else if (hdr[1] != 44100 && hdr[1] != 48000) problems.add(path + ": sample rate " + hdr[1]);
                    // positional sounds must be mono or OpenAL can't place them in the world (ambience beds, and the
                    // System sounds the server plays at a player: level up, rank up, the warning)
                    else if ((ref.getPath().startsWith("amb_") || POSITIONAL_UI.contains(ref.getPath())) && hdr[0] != 1) {
                        problems.add(path + ": played in the world, must be mono, has " + hdr[0] + " channels");
                    }
                }
            }
        }
        // the module's own events, each by name
        for (String path : AudioSounds.all().keySet()) if (!sounds.has(path)) problems.add("audio event missing: " + path);
        h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        h.assertTrue(files >= 30, "the mod's own audio files are referenced (" + files + ")");
        h.succeed();
    }

    /** Every cue has a sound event, a file, a subtitle with a lang line and a name for the Now Playing toast. */
    static void cuesHaveSubtitles(GameTestHelper h) {
        JsonObject sounds = json("/assets/awaken/sounds.json");
        JsonObject lang = json("/assets/awaken/lang/en_us.json");
        h.assertTrue(sounds != null && lang != null, "resources present");
        for (Cue cue : Cue.values()) {
            String path = cue.eventPath();
            h.assertTrue(sounds.has(path), "sounds.json has " + path);
            JsonObject ev = sounds.getAsJsonObject(path);
            h.assertTrue(ev.has("subtitle") && lang.has(ev.get("subtitle").getAsString()), path + " has a subtitle line");
            String file = null;
            for (JsonElement s : ev.getAsJsonArray("sounds")) if (!isEvent(s)) file = sub(s);
            h.assertTrue(file != null && file.startsWith("awaken:music_cue_"), path + " plays its own file, got " + file);
            h.assertTrue(vorbisHeader("/assets/awaken/sounds/" + Identifier.parse(file).getPath() + ".ogg") != null, file + " exists");
            h.assertTrue(lang.has("awaken." + Identifier.parse(file).getPath()), file + " has a Now Playing name");
            h.assertTrue(AudioSounds.cue(cue).getId().getPath().equals(path), cue + " is registered as " + path);
        }
        h.succeed();
    }

    /** Gate themes and strata point their background music at our events, with vanilla-length silences between. */
    static void biomeMusic(GameTestHelper h) {
        for (String theme : AudioSounds.GATE_THEMES) {
            JsonObject b = json("/data/awaken/worldgen/biome/gate_" + theme + ".json");
            h.assertTrue(b != null, "biome gate_" + theme);
            JsonObject m = b.getAsJsonObject("attributes").getAsJsonObject("minecraft:audio/background_music").getAsJsonObject("default");
            h.assertTrue(m.get("sound").getAsString().equals("awaken:music.gate." + theme), theme + " plays its own track: " + m);
            h.assertTrue(m.get("min_delay").getAsInt() >= 3600 && m.get("max_delay").getAsInt() > m.get("min_delay").getAsInt(),
                    theme + ": silence stays the default (" + m + ")");
        }
        JsonObject abyss = json("/data/awaken/worldgen/biome/gate_abyssal_shrine.json");
        h.assertTrue(abyss != null && abyss.getAsJsonObject("attributes").get("minecraft:audio/music_volume").getAsFloat() == 0F,
                "the Abyssal Shrine keeps its silence");
        for (int s = 1; s <= AudioSounds.STRATA; s++) {
            JsonObject b = json("/data/awaken/worldgen/biome/stratum_" + s + ".json");
            h.assertTrue(b != null, "biome stratum_" + s);
            JsonObject m = b.getAsJsonObject("attributes").getAsJsonObject("minecraft:audio/background_music").getAsJsonObject("default");
            h.assertTrue(m.get("sound").getAsString().equals("awaken:music.descent." + s), "stratum " + s + " plays the Descent track: " + m);
            h.assertTrue(m.get("min_delay").getAsInt() >= 3600, "stratum " + s + ": long silences");
        }
        h.succeed();
    }

    /** Per-cue cooldowns, the gap between cues, stage cues, the awakening only once. */
    static void cooldowns(GameTestHelper h) {
        CueClock c = new CueClock();
        h.assertTrue(c.tryFire(Cue.BEAST, 1000), "a first cue plays");
        h.assertTrue(!c.tryFire(Cue.BEAST, 1001), "the same cue doesn't play twice in a row");
        h.assertTrue(!c.tryFire(Cue.LAIR, 1000 + Cue.GAP - 1), "another ordinary cue waits for the gap");
        h.assertTrue(c.tryFire(Cue.GATE_BROKE, 1002), "a stage cue ignores the gap");
        h.assertTrue(!c.tryFire(Cue.GATE_BROKE, 1002 + Cue.GATE_BROKE.cooldownTicks - 1), "but not its own cooldown");
        h.assertTrue(c.tryFire(Cue.LAIR, 1002 + Cue.GAP), "after the gap another cue plays");
        h.assertTrue(!c.tryFire(Cue.BEAST, 1000 + Cue.BEAST.cooldownTicks - 1), "the beast cue keeps its cooldown");
        h.assertTrue(c.tryFire(Cue.BEAST, 1000 + Cue.BEAST.cooldownTicks + Cue.GAP), "and plays again after it");
        h.assertTrue(c.tryFire(Cue.AWAKENING, 5), "the awakening plays");
        h.assertTrue(!c.tryFire(Cue.AWAKENING, 5 + 20L * 60 * 60 * 24), "the awakening plays once");
        for (Cue q : Cue.values()) {
            h.assertTrue(q == Cue.AWAKENING || q.cooldownTicks >= 20 * 60 * 5, q + " has a cooldown of minutes");
            h.assertTrue(q.waitTicks > 0 && q.waitTicks <= 20 * 30, q + " waits at most 30 s for the music slot");
        }
        h.succeed();
    }

    /** The awakening hook, the per-player clock, the discovery watch (a beast's first sighting, a lair found). */
    static void triggers(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!AudioCues.play(p, Cue.BEAST), "nothing plays before awakening");
        Progression.awaken(p);
        h.assertTrue(!AudioCues.play(p, Cue.AWAKENING), "awakening already played its cue (hook) and plays once");
        AudioCues.forget(p.getUUID());
        h.assertTrue(AudioCues.play(p, Cue.SHRINE), "a cue plays for an awakened player");
        h.assertTrue(!AudioCues.play(p, Cue.SHRINE), "its cooldown holds");
        h.assertTrue(!AudioCues.play(p, Cue.LAIR), "and the gap holds back the next one");

        // discoveries: a baseline first, then only what's new
        AudioCues.forget(p.getUUID());
        h.assertTrue(AudioWatch.discoveries(p) == null, "the first look is a baseline");
        MobDef beast = null, critter = null;
        for (MobDef d : Bestiary.fauna()) {
            if (d.fauna == FaunaRole.BEAST && beast == null) beast = d;
            if (d.fauna == FaunaRole.AMBIENT && critter == null) critter = d;
        }
        h.assertTrue(beast != null && critter != null, "the bestiary has named beasts and ambient creatures");
        Progression.discover(p, "creature/" + critter.id, 0);
        h.assertTrue(AudioWatch.discoveries(p) == null, "an ordinary creature plays no cue");
        Progression.discover(p, "creature/" + beast.id, 0);
        h.assertTrue(AudioWatch.discoveries(p) == Cue.BEAST, "a named beast's first sighting plays its cue");
        AudioCues.forget(p.getUUID());
        Progression.discover(p, "lair/" + beast.id, 0);
        h.assertTrue(AudioWatch.discoveries(p) == Cue.LAIR, "a lair found plays its cue");
        h.assertTrue(AudioWatch.classify("awaken:discovered/landmark/well") == null, "landmarks have no cue");
        h.assertTrue(AudioWatch.compute(p).size() <= AudioNet.MAX_EMITTERS, "emitters are capped");
        AudioWatch.forget(p.getUUID());
        AudioCues.forget(p.getUUID());
        h.succeed();
    }

    static void payloads(GameTestHelper h) {
        ByteBuf buf = Unpooled.buffer();
        var em = new AudioNet.Emitters(List.of(new AudioNet.Emitter(AudioNet.WELL, 10, -20, 30), new AudioNet.Emitter(AudioNet.STAR, -5, 70, 9)));
        AudioNet.Emitters.STREAM_CODEC.encode(buf, em);
        h.assertTrue(AudioNet.Emitters.STREAM_CODEC.decode(buf).equals(em), "emitters round-trip");
        buf.clear();
        AudioNet.PlayCue.STREAM_CODEC.encode(buf, new AudioNet.PlayCue(Cue.LAIR.ordinal()));
        h.assertTrue(Cue.byOrdinal(AudioNet.PlayCue.STREAM_CODEC.decode(buf).cue()) == Cue.LAIR, "cue round-trips");
        buf.clear();
        AudioNet.PlayUi.STREAM_CODEC.encode(buf, new AudioNet.PlayUi(UiSound.QUEST_DONE.ordinal()));
        h.assertTrue(UiSound.byOrdinal(AudioNet.PlayUi.STREAM_CODEC.decode(buf).sound()) == UiSound.QUEST_DONE, "ui sound round-trips");
        h.assertTrue(Cue.byOrdinal(99) == null && UiSound.byOrdinal(-1) == null, "unknown ids are ignored");
        h.succeed();
    }
}
