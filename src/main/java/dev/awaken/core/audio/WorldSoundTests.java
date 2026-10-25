package dev.awaken.core.audio;

import com.google.gson.JsonObject;
import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.core.audio.world.DayCurve;
import dev.awaken.core.audio.world.EventClock;
import dev.awaken.core.audio.world.Families;
import dev.awaken.core.audio.world.SoundscapeDef;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * World sound (docs/sound/PLAN.md §11 "Tests"): the census (every overworld biome in exactly one family; every
 * soundscape file valid, its air layers real 44.1 kHz Vorbis loops whose lengths repeat together no sooner than half an
 * hour, every sound it names a real event, no positional sound named {@code ambient.*}, which Sound Physics skips)
 * and the event clock (rates, thinning, lulls, answers, determinism). The Conductor itself is client-only.
 */
final class WorldSoundTests {
    private WorldSoundTests() {}

    static void register() {
        AwakenTests.register("audio_world_census", 40, WorldSoundTests::census);
        AwakenTests.register("audio_world_clock", 20, WorldSoundTests::clock);
        AwakenTests.register("audio_world_ambientsounds", 20, WorldSoundTests::ambientSounds);
    }

    /**
     * AmbientSounds plays the nature (the pack's sound profile): our jar's data for it parses, mutes every awaken:
     * dimension, and names only real blocks; pack/mods.json ships it (and CreativeCore by dependency) in the sound profile.
     */
    static void ambientSounds(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        JsonObject dim = AudioTests.json("/assets/ambientsounds/basic/dimensions/awaken_dimensions.json");
        if (dim == null) problems.add("no awaken_dimensions.json for AmbientSounds");
        else {
            if (!dim.has("mute") || !dim.get("mute").getAsBoolean()) problems.add("awaken: dimensions aren't muted");
            boolean ours = false;
            for (var n : dim.getAsJsonArray("dimension-names")) ours |= n.getAsString().equals("awaken:");
            if (!ours) problems.add("the dimension file doesn't name awaken:");
        }
        for (String group : List.of("rock", "grass_block", "grass", "ice")) {
            try (java.io.InputStream in = WorldSoundTests.class.getResourceAsStream("/assets/ambientsounds/basic/blockgroups/" + group + ".json")) {
                if (in == null) {
                    problems.add("no blockgroup " + group);
                    continue;
                }
                var arr = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
                for (var e : arr) {
                    Identifier b = Identifier.parse(e.getAsString());
                    if (!BuiltInRegistries.BLOCK.containsKey(b)) problems.add(group + ": no block " + b);
                }
            } catch (java.io.IOException | RuntimeException e) {
                problems.add(group + ": " + e);
            }
        }
        java.nio.file.Path root = AudioLayerTests.repoRoot();
        if (root != null) {
            try {
                var mods = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("pack/mods.json"))).getAsJsonObject();
                boolean shipped = false;
                for (var m : mods.getAsJsonArray("mods")) {
                    var o = m.getAsJsonObject();
                    if (o.get("slug").getAsString().equals("ambientsounds")) {
                        for (var p : o.getAsJsonArray("profiles")) shipped |= p.getAsString().equals("sound");
                    }
                }
                if (!shipped) problems.add("pack/mods.json doesn't ship ambientsounds in the sound profile");
            } catch (java.io.IOException | RuntimeException e) {
                problems.add("pack/mods.json: " + e);
            }
        }
        h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        h.succeed();
    }

    static void census(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        var biomes = h.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
        int overworld = 0;
        for (Holder<Biome> b : biomes.listElements().toList()) {
            if (!b.is(BiomeTags.IS_OVERWORLD)) continue;
            overworld++;
            int in = 0;
            for (int f = 0; f < Families.COUNT; f++) if (b.is(Families.tag(f))) in++;
            if (in != 1) problems.add(b.getRegisteredName() + " is in " + in + " world sound families");
        }
        JsonObject sounds = AudioTests.json("/assets/awaken/sounds.json");
        h.assertTrue(sounds != null, "merged sounds.json is on the classpath");
        int scapes = 0;
        List<Identifier> ids = new ArrayList<>(List.of(Families.WORLD, Awaken.id("state/veil")));
        for (int f = 0; f < Families.COUNT; f++) ids.add(Families.soundscape(f));
        // every biome may have a place soundscape (<ns>:place/<path>): the ones that do must be sound
        for (Holder<Biome> b : biomes.listElements().toList()) {
            b.unwrapKey().ifPresent(k -> ids.add(Identifier.fromNamespaceAndPath(k.identifier().getNamespace(), "place/" + k.identifier().getPath())));
        }
        List<String> places = new ArrayList<>();
        for (Identifier id : ids) {
            JsonObject o = AudioTests.json("/assets/" + id.getNamespace() + "/soundscape/" + id.getPath() + ".json");
            if (o == null) {
                if (id.equals(Families.WORLD) || id.getPath().startsWith("state/")) problems.add("the soundscape " + id + " is missing");
                continue; // a family or a place without a file is silent
            }
            scapes++;
            if (id.getPath().startsWith("place/")) places.add(id.getPath());
            SoundscapeDef def;
            try {
                def = SoundscapeDef.parse(id, o);
            } catch (RuntimeException e) {
                problems.add(e.getMessage());
                continue;
            }
            long[] lengths = new long[def.air().size()];
            for (int i = 0; i < lengths.length; i++) {
                Identifier file = def.air().get(i).file();
                String path = "/assets/" + file.getNamespace() + "/sounds/" + file.getPath() + ".ogg";
                int[] hdr = AudioTests.vorbisHeader(path);
                if (hdr == null) problems.add(id + ": air " + file + " missing or not Ogg Vorbis");
                else if (hdr[1] != 44100) problems.add(id + ": air " + file + " at " + hdr[1] + " Hz");
                lengths[i] = AudioLayerTests.oggFrames(path);
            }
            long period = 1;
            for (long l : lengths) period = l <= 0 ? period : lcm(period, l);
            if (lengths.length > 1 && period < 30L * 60 * 44100) problems.add(id + ": air layers repeat together after " + period / 44100 + " s");
            for (SoundscapeDef.Texture t : def.textures()) known(t.sound(), id, sounds, problems);
            for (SoundscapeDef.Event e : def.events()) known(e.sound(), id, sounds, problems);
        }
        h.assertTrue(overworld > 50, "the overworld biomes are there (" + overworld + ")");
        h.assertTrue(scapes >= 2, "the world soundscape and at least one family (" + scapes + ")");
        for (String want : List.of("place/stillpoint", "place/halo_orbit", "place/lucidium_dream", "place/elderwood", "place/lumen_forest")) {
            if (!places.contains(want)) problems.add("no soundscape for awaken:" + want + " (or its biome is gone)");
        }
        h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        h.succeed();
    }

    private static void known(Identifier sound, Identifier scape, JsonObject sounds, List<String> problems) {
        boolean ours = sound.getNamespace().equals(Awaken.MODID);
        if (ours ? !sounds.has(sound.getPath()) : !BuiltInRegistries.SOUND_EVENT.containsKey(sound)) {
            problems.add(scape + ": no sound event " + sound);
        }
        if (sound.getPath().startsWith("ambient.")) problems.add(scape + ": " + sound + " is ambient.* (Sound Physics skips it)");
    }

    private static long lcm(long a, long b) {
        long x = a, y = b;
        while (y != 0) {
            long t = x % y;
            x = y;
            y = t;
        }
        return a / x * b;
    }

    static void clock(GameTestHelper h) {
        // two events: 6 a minute always kept, 6 a minute kept half the time and answered every time 1-2 s later
        JsonObject o = com.google.gson.JsonParser.parseString("""
                {"schemaVersion": 1, "events": [
                  {"sound": "awaken:amb.world", "anchor": "leaves", "per_minute": 6},
                  {"sound": "awaken:amb.world", "anchor": "leaves", "per_minute": 6, "answer": 1, "answer_delay": [1, 2]}]}
                """).getAsJsonObject();
        SoundscapeDef def = SoundscapeDef.parse(Awaken.id("test"), o);
        int[] heard = new int[4];
        EventClock c = new EventClock(42L);
        c.start(def);
        float[] keep = {1F, 0.5F};
        for (int t = 0; t < 20 * 600; t++) c.advance(0.05, keep, (i, answer) -> heard[i * 2 + (answer ? 1 : 0)]++);
        // ten minutes: about 60 of the first, 30 of the second and as many answers
        h.assertTrue(heard[0] > 42 && heard[0] < 80, "a steady event comes at its rate: " + heard[0]);
        h.assertTrue(heard[2] > 16 && heard[2] < 46, "a half-kept event comes half as often: " + heard[2]);
        h.assertTrue(heard[1] == 0, "an event without an answer is never answered");
        h.assertTrue(Math.abs(heard[3] - heard[2]) <= 8, "every kept call is answered (" + heard[3] + " of " + heard[2] + ")");
        // the same seed hears the same thing
        int[] again = new int[4];
        EventClock d = new EventClock(42L);
        d.start(def);
        for (int t = 0; t < 20 * 600; t++) d.advance(0.05, keep, (i, answer) -> again[i * 2 + (answer ? 1 : 0)]++);
        h.assertTrue(java.util.Arrays.equals(heard, again), "deterministic for a seed");
        // a lull of depth 0 that never ends silences everything
        SoundscapeDef still = SoundscapeDef.parse(Awaken.id("still"), com.google.gson.JsonParser.parseString("""
                {"schemaVersion": 1, "events": [{"sound": "awaken:amb.world", "anchor": "far", "per_minute": 30}],
                 "lull": {"every": [0.1, 0.1], "length": [100000, 100000], "depth": 0}}
                """).getAsJsonObject());
        int[] quiet = new int[1];
        EventClock q = new EventClock(7L);
        q.start(still);
        for (int t = 0; t < 20 * 120; t++) q.advance(0.05, new float[]{1F}, (i, answer) -> quiet[0]++);
        h.assertTrue(quiet[0] <= 1 && q.lull(), "a lull holds the soundscape still (" + quiet[0] + ")");
        // the day curve eases: dawn peaks at 05:30, night holds, and noon sits between its anchors' values
        DayCurve dawn = new DayCurve(1F, 0.2F, 0.1F, 0.5F, 0F, 0F);
        h.assertTrue(dawn.at(23500) > 0.99F && dawn.at(18000) < 0.01F, "dawn and night anchors");
        h.assertTrue(dawn.at(1000) < 0.99F && dawn.at(1000) > 0.2F, "the chorus fades through the morning: " + dawn.at(1000));
        h.succeed();
    }
}
