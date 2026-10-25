package dev.awaken.citizens;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.citizens.client.CityFaunaData;
import dev.awaken.city.CityPlan;
import dev.awaken.core.audio.Intensity;
import dev.awaken.core.audio.LayerManifest;
import dev.awaken.core.audio.Loudness;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.client.anim.HeroRig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The city's drawn life (M4 B3b, prefix {@code citizens_}), one test per mechanism where breakage is silent: the Watch's
 * lanterns place no light blocks (and the A/B path and old saves still clean up after themselves); every district bed
 * and the night bed resolve to equal-length loops whose crowd stems rise with the crowd, at the beds' loudness; the
 * animals' rigs parse and carry every clip the client's brain plays (a missing clip just freezes the animal).
 */
final class CityLifeTests {
    private CityLifeTests() {}

    static void register() {
        AwakenTests.registerIsolated("citizens_lanterns_drawn", 200, CityLifeTests::lanterns);
        // decoding ten 48 s stems runs on its own thread; the test server ticks as fast as it can, so a generous ceiling
        AwakenTests.register("citizens_soundscape_beds", 20 * 60 * 60 * 12, CityLifeTests::beds);
        AwakenTests.register("citizens_fauna_rigs", 40, CityLifeTests::rigs);
    }

    // ---- lanterns ----------------------------------------------------------------------------------------------------

    /**
     * A body carrying a lit lantern next to a player, walked for 200 ticks: no light block is written (the client draws
     * the light). The measuring path ({@code lanterns blocks}) does write one, and going back to drawn light takes it
     * away; a light block an older save left behind is cleared when its chunk is in.
     */
    private static void lanterns(GameTestHelper h) {
        Roster r = Roster.get();
        h.assertTrue(r != null, "the roster is in the jar");
        ServerLevel level = h.getLevel();
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        StreetNet g = StreetNet.of(CityPlan.get(), origin.getX(), origin.getZ(), origin.getY(), r.edgeLane);
        Citizens c = new Citizens(level, r, g, new CitizensData(), origin.getX(), origin.getZ(), origin.getY());
        c.crowd.on = false;
        c.work.edits = false;
        CitizenBody b = new CitizenBody(CitizensContent.CITIZEN.get(), level);
        BlockPos feet = h.absolutePos(new BlockPos(2, 1, 2));
        b.setPos(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        b.hold(EquipmentSlot.OFFHAND, Props.LANTERN);
        b.nearestSq = 4;
        c.ledger.body[0] = b;
        boolean legacy = Lanterns.legacy;
        try {
            Lanterns.legacy = false;
            Lanterns.writes = 0;
            long now = level.getGameTime();
            for (int t = 0; t < 200; t++) {
                // walk it along the row so every move a light block would follow is tried
                b.setPos(feet.getX() + 0.5 + (t % 40) * 0.1, feet.getY(), feet.getZ() + 0.5);
                c.lanterns.tick(now + t);
            }
            h.assertTrue(Lanterns.writes == 0, "drawn lanterns wrote " + Lanterns.writes + " light blocks");
            h.assertTrue(c.lanterns.active() == 0 && c.data.lights.length == 0, "no light is held or saved");
            h.assertTrue(lightsNear(level, feet) == 0, "no light block next to the bearer");

            // the A/B path: one light block at the bearer's head, written to the save; back to drawn light removes it
            Lanterns.legacy = true;
            b.setPos(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            long t2 = now + 200 + Math.floorMod(2 - (now + 200), 5);
            c.lanterns.tick(t2);
            h.assertTrue(Lanterns.writes == 1 && c.lanterns.active() == 1 && c.data.lights.length == 1,
                    "the measuring path places one light (writes " + Lanterns.writes + ", active " + c.lanterns.active() + ")");
            h.assertTrue(lightsNear(level, feet) == 1, "and it is in the world");
            Lanterns.legacy = false;
            c.lanterns.tick(t2 + 5);
            h.assertTrue(c.lanterns.active() == 0 && c.data.lights.length == 0 && lightsNear(level, feet) == 0,
                    "back to drawn light: the block is gone and forgotten");

            // a light an old save left: cleared once its chunk is loaded (every 100 ticks)
            BlockPos old = feet.above(2);
            level.setBlock(old, Blocks.LIGHT.defaultBlockState(), 3);
            c.data.lights = new long[]{old.asLong()};
            Lanterns again = new Lanterns(c);
            again.tick(now + 300 - Math.floorMod(now + 300, 100));
            h.assertTrue(!level.getBlockState(old).is(Blocks.LIGHT) && c.data.lights.length == 0, "an old save's light is cleared");
        } finally {
            Lanterns.legacy = legacy;
            Lanterns.writes = 0;
        }
        h.succeed();
    }

    private static int lightsNear(ServerLevel level, BlockPos at) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(6, 4, 2))) if (level.getBlockState(p).is(Blocks.LIGHT)) n++;
        return n;
    }

    // ---- the beds ----------------------------------------------------------------------------------------------------

    /** The stems that carry people: their gains never fall as the crowd grows. */
    private static final List<String> CROWD = List.of("murmur_far", "murmur_near", "bustle", "works", "tavern_far");
    /**
     * The beds' loudness (SPEC §16.0 decision 30): each level's mix -37 to -26 dBFS gated RMS, under -6 dBTP. They sit
     * under the places that sound, quieter than the ambience rule's -28 ± 3 for a single bed.
     */
    static final double BED_MIN = -37, BED_MAX = -26, BED_TP = -6;

    /**
     * Every district's key matches the plan, every bed (seven districts and the night) is an ambient context whose stems
     * are equal-length 44.1 kHz loops, its crowd stems rise level by level, and a district's crowd picks calm when empty
     * and packed at its busiest. The mixes are decoded and measured off-thread.
     */
    private static void beds(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        CityPlan plan = CityPlan.get();
        for (CityPlan.District d : plan.districts) {
            if (d.id() < 0 || d.id() >= Soundscape.KEYS.length || !Soundscape.KEYS[d.id()].equals(d.key())) {
                problems.add("district " + d.id() + " is " + d.key() + " in the plan");
            }
        }
        List<LayerManifest> beds = new ArrayList<>();
        List<String> names = new ArrayList<>(List.of(Soundscape.KEYS).subList(1, Soundscape.KEYS.length));
        names.add("night");
        for (String key : names) {
            Identifier ctx = Awaken.id("city/" + key);
            Identifier path = LayerManifest.path(ctx);
            JsonObject o = json("/assets/" + path.getNamespace() + "/" + path.getPath());
            if (o == null) {
                problems.add(ctx + ": no manifest");
                continue;
            }
            LayerManifest m;
            try {
                m = LayerManifest.parse(ctx, o);
            } catch (RuntimeException e) {
                problems.add(e.getMessage());
                continue;
            }
            beds.add(m);
            if (m.music()) problems.add(ctx + ": a bed plays on the ambient source");
            for (LayerManifest.Stem s : m.stems()) {
                long frames = oggFrames(file(s));
                if (frames != m.frames()) problems.add(file(s) + ": " + frames + " frames, the loop is " + m.frames());
                if (s.attack() <= 0 || s.duck() <= 0 || s.release() <= 0) problems.add(file(s) + ": fade times");
            }
            for (int k = 0; k < m.stems().size(); k++) {
                if (!CROWD.contains(m.stems().get(k).name())) continue;
                for (int l = 1; l < Intensity.NAMES.length; l++) {
                    if (m.gainsAt(l)[k] < m.gainsAt(l - 1)[k]) problems.add(ctx + ": " + m.stems().get(k).name() + " falls at " + Intensity.name(l));
                }
            }
        }
        h.assertTrue(problems.isEmpty(), String.join("; ", problems));

        // the crowd's level: empty is calm, the busiest hour packed, never stepping down as people come out
        for (int d = 1; d < Soundscape.KEYS.length; d++) {
            h.assertTrue(Soundscape.level(d, 0) == Intensity.CALM, Soundscape.KEYS[d] + ": an empty district is calm");
            h.assertTrue(Soundscape.level(d, Soundscape.PEAK[d]) == Intensity.LORD, Soundscape.KEYS[d] + ": its busiest hour is packed");
            for (int n = 1; n <= Soundscape.PEAK[d]; n++) {
                h.assertTrue(Soundscape.level(d, n) >= Soundscape.level(d, n - 1), Soundscape.KEYS[d] + ": the level falls at " + n);
            }
        }
        for (int tod = 0; tod < 24000; tod += 100) {
            if (!Soundscape.night(tod)) continue;
            int lv = Soundscape.nightLevel(tod);
            h.assertTrue(lv >= Intensity.CALM && lv <= Intensity.COMBAT, "the night bed's level at " + tod);
        }
        h.assertTrue(Soundscape.night(18000) && !Soundscape.night(6000), "night at midnight, day at noon");

        CompletableFuture<List<String>> job = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                job.complete(measure(beds));
            } catch (Throwable e) {
                job.completeExceptionally(e);
            }
        }, "awaken-city-beds-test");
        worker.setDaemon(true);
        worker.start();
        h.succeedWhen(() -> {
            h.assertTrue(job.isDone(), "measuring the city's beds");
            List<String> out = job.join();
            h.assertTrue(out.isEmpty(), String.join("; ", out));
        });
    }

    private static List<String> measure(List<LayerManifest> beds) throws IOException {
        List<String> problems = new ArrayList<>();
        Map<Identifier, float[][]> cache = new java.util.HashMap<>();
        StringBuilder report = new StringBuilder();
        for (LayerManifest m : beds) {
            float[][][] stems = new float[m.stems().size()][][];
            for (int k = 0; k < stems.length; k++) {
                LayerManifest.Stem s = m.stems().get(k);
                float[][] pcm = cache.get(s.file());
                if (pcm == null) {
                    try (InputStream in = CityLifeTests.class.getResourceAsStream(file(s))) {
                        if (in == null) {
                            problems.add(file(s) + " missing");
                            continue;
                        }
                        Loudness.Pcm p = Loudness.decode(in);
                        if (p.rate() != 44100) problems.add(file(s) + ": " + p.rate() + " Hz");
                        pcm = p.channels();
                        cache.put(s.file(), pcm);
                    }
                }
                stems[k] = pcm;
            }
            if (java.util.Arrays.stream(stems).anyMatch(x -> x == null)) continue;
            report.append('\n').append(m.id().getPath());
            double calm = 0;
            for (int l = 0; l < Intensity.NAMES.length; l++) {
                Loudness.Measure me = Loudness.measure(Loudness.mix(stems, m.gainsAt(l)));
                report.append(String.format(Locale.ROOT, "  %s %.1f/%.1f", Intensity.name(l), me.rms(), me.truePeak()));
                if (me.rms() < BED_MIN || me.rms() > BED_MAX) problems.add(String.format(Locale.ROOT, "%s %s: RMS %.1f dBFS", m.id(), Intensity.name(l), me.rms()));
                if (me.truePeak() > BED_TP) problems.add(String.format(Locale.ROOT, "%s %s: true peak %.1f dBTP", m.id(), Intensity.name(l), me.truePeak()));
                if (l == 0) calm = me.rms();
                else if (me.rms() < calm - 0.5) problems.add(m.id() + " " + Intensity.name(l) + " is quieter than calm");
            }
        }
        Awaken.LOG.info("[citizens] city beds (level RMS/true peak):{}", report);
        return problems;
    }

    // ---- the animals -------------------------------------------------------------------------------------------------

    /** The clips citizens.client.CityCritters plays for each animal, by name (the brain finds them by name). */
    static final Map<String, List<String>> CLIPS = Map.of(
            "city_pigeon", List.of("idle", "walk", "peck", "call", "fly", "glide", "flare"),
            "city_gull", List.of("idle", "walk", "peck", "call", "fly", "glide", "flare"),
            "city_cat", List.of("sit", "sleep", "loaf", "groom", "walk", "trot"),
            "city_dog", List.of("idle", "walk", "trot", "sit", "pant", "sniff", "lie"),
            "city_horse", List.of("idle", "walk", "toss"),
            "city_cart", List.of());

    /** Every rig parses without problems, carries the clips its brain plays, and every coat (and its blink) is a texture. */
    private static void rigs(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        Map<String, HeroRig> rigs = new java.util.HashMap<>();
        for (String[] src : CityFaunaData.SOURCES) {
            try {
                HeroRig r = HeroRig.parse(src);
                rigs.put(r.id, r);
                problems.addAll(r.problems());
            } catch (RuntimeException e) {
                problems.add("a rig fails to parse: " + e.getMessage());
            }
        }
        for (var e : CLIPS.entrySet()) {
            HeroRig r = rigs.get(e.getKey());
            if (r == null) {
                problems.add(e.getKey() + ": no rig");
                continue;
            }
            for (String clip : e.getValue()) if (r.clip(clip) == null) problems.add(e.getKey() + ": no " + clip + " clip");
            List<String> coats = CityFaunaData.VARIANTS.get(e.getKey());
            if (coats == null || coats.isEmpty()) {
                problems.add(e.getKey() + ": no coats");
                continue;
            }
            for (String v : coats) {
                String base = "/assets/awaken/textures/entity/city/" + e.getKey() + (v.isEmpty() ? "" : "_" + v);
                if (missing(base + ".png")) problems.add(base + ".png missing");
                // living animals blink (the cart doesn't)
                if (!e.getKey().equals("city_cart") && missing(base + "_blink.png")) problems.add(base + "_blink.png missing");
                if (e.getKey().equals("city_cat") && missing(base + "_glow.png")) problems.add(base + "_glow.png missing (eyeshine)");
            }
        }
        h.assertTrue(rigs.size() == CLIPS.size(), "one rig per animal and the cart (" + rigs.keySet() + ")");
        h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        h.succeed();
    }

    // ---- files -------------------------------------------------------------------------------------------------------

    private static boolean missing(String resource) {
        try (InputStream in = CityLifeTests.class.getResourceAsStream(resource)) {
            return in == null;
        } catch (IOException e) {
            return true;
        }
    }

    private static JsonObject json(String path) {
        try (InputStream in = CityLifeTests.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            return null;
        }
    }

    private static String file(LayerManifest.Stem s) {
        return "/assets/" + s.file().getNamespace() + "/sounds/" + s.file().getPath() + ".ogg";
    }

    /** Total samples of an Ogg Vorbis file: the granule position of its last page (-1 if missing). */
    private static long oggFrames(String resource) {
        try (InputStream in = CityLifeTests.class.getResourceAsStream(resource)) {
            if (in == null) return -1;
            byte[] b = in.readAllBytes();
            for (int i = b.length - 27; i >= 0; i--) {
                if (b[i] == 'O' && b[i + 1] == 'g' && b[i + 2] == 'g' && b[i + 3] == 'S' && (b[i + 5] & 4) != 0) {
                    long g = 0;
                    for (int k = 7; k >= 0; k--) g = (g << 8) | (b[i + 6 + k] & 0xFF);
                    return g;
                }
            }
            return -1;
        } catch (IOException e) {
            return -1;
        }
    }
}
