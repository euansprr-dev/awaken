package dev.awaken.wildlife;

import dev.awaken.core.audio.world.Families;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.wildlife.client.WildFaunaData;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Wild life's silent rules (SPEC "Wild life"): one test per mechanism; how it looks and moves is proven on tours. */
final class WildlifeTests {
    private WildlifeTests() {}

    /** Every clip the brains (wildlife.client.Birds, Critters) play by name, per body. */
    static final Map<String, List<String>> CLIPS = Map.ofEntries(
            Map.entry("wild_songbird", List.of("idle", "hop", "peck", "fly", "bound", "flare", "sing", "preen", "fluff")),
            Map.entry("wild_woodpecker", List.of("cling", "peck", "drum", "hitch", "fly", "bound", "flare")),
            Map.entry("wild_crow", List.of("idle", "walk", "peck", "caw", "fly", "glide", "flare")),
            Map.entry("wild_owl", List.of("idle", "hoot", "fluff", "fly", "glide", "flare")),
            Map.entry("wild_raptor", List.of("idle", "soar", "fly", "hover", "stoop", "flare")),
            Map.entry("wild_gull", List.of("idle", "fly", "glide", "flare")),
            Map.entry("wild_duck", List.of("idle", "swim", "dabble", "preen", "quack", "fly", "glide", "flare")),
            Map.entry("wild_heron", List.of("stand", "alert", "stalk", "strike", "fly", "glide", "flare")),
            Map.entry("wild_squirrel", List.of("idle", "run", "climb", "descend", "cling", "chatter", "forage")),
            Map.entry("wild_rabbit", List.of("idle", "graze", "hop", "run", "alert", "groom")),
            Map.entry("wild_fox", List.of("idle", "trot", "run", "sit", "sniff", "listen", "pounce", "lie")),
            Map.entry("wild_frog", List.of("idle", "croak", "hop")));

    static void register() {
        // the tables reach every sound family and hit the targets of a real walk: a few birds in a forest at noon, none at
        // night but owls and fireflies, a meadow's butterflies gone in the rain, no insects in the snow; a chunk's draw
        // is fixed and the land averages the expected number
        AwakenTests.register("wildlife_density", 20, h -> {
            List<String> problems = new ArrayList<>();
            if (Families.COUNT != 16 || !Families.NAMES[0].equals("greenwood") || !Families.NAMES[15].equals("hollows")) problems.add("sound families changed order");
            if (Density.AFFINITY.length != WildKinds.COUNT || Density.HOURS.length != WildKinds.COUNT || Density.PEAK.length != WildKinds.COUNT
                    || WildKinds.REACH.length != WildKinds.COUNT || WildKinds.CAP.length != WildKinds.COUNT) problems.add("a table misses a kind");
            for (int k = 0; k < WildKinds.COUNT; k++) {
                if (Density.AFFINITY[k].length != Families.COUNT) problems.add(WildKinds.NAMES[k] + ": affinity misses a family");
                float[] hr = Density.HOURS[k];
                for (int i = 0; i + 2 < hr.length; i += 2) if (hr[i + 2] <= hr[i]) problems.add(WildKinds.NAMES[k] + ": hours out of order");
                for (int t = 0; t < 24000; t += 250) if (Density.hour(k, t) < 0F) problems.add(WildKinds.NAMES[k] + ": negative at " + t);
            }
            Density.Habitat any = new Density.Habitat();
            for (int f = 0; f < Families.COUNT; f++) {
                any.set(f, 0.4F, 0.8F, 30, 4, 30, 12, 30, 12, 2);
                int bodies = 0, insects = 0;
                for (int k = 0; k < WildKinds.COUNT; k++) {
                    float best = 0F;
                    for (int t = 0; t < 24000; t += 500) best = Math.max(best, Density.lambda(k, any, t, 0F, 0F));
                    if (best > 0F) {
                        if (WildKinds.body(k)) bodies++;
                        else insects++;
                    }
                }
                if (bodies == 0) problems.add(Families.NAMES[f] + " has no animals");
                if (insects == 0 && f != Density.OPEN_SEA && f != Density.SNOWFIELD) problems.add(Families.NAMES[f] + " has no insects");
            }
            Density.Habitat forest = new Density.Habitat().set(Density.GREENWOOD, 0.6F, 0.7F, 60, 4, 20, 2, 0, 0, 0);
            float noon = Density.lambda(WildKinds.SONGBIRD, forest, 6000, 0F, 0F);
            if (noon < 0.2F || noon > 0.6F) problems.add("forest songbirds at noon " + noon + " per chunk (want 0.2-0.6: one to three in view)");
            if (Density.lambda(WildKinds.SONGBIRD, forest, 0, 0F, 0F) <= noon) problems.add("the dawn chorus isn't busier than noon");
            if (Density.lambda(WildKinds.SONGBIRD, forest, 18000, 0F, 0F) != 0F) problems.add("songbirds about at midnight");
            if (Density.lambda(WildKinds.OWL, forest, 18000, 0F, 0F) <= 0F || Density.lambda(WildKinds.OWL, forest, 6000, 0F, 0F) != 0F) problems.add("owls by day or not at night");
            if (Density.lambda(WildKinds.FIREFLY, forest, 18000, 0F, 0F) < 1F) problems.add("too few fireflies in a forest at night");
            Density.Habitat meadow = new Density.Habitat().set(Density.OPEN_GRASS, 0F, 0.8F, 2, 0, 200, 24, 0, 0, 0);
            float flutter = Density.lambda(WildKinds.BUTTERFLY, meadow, 6000, 0F, 0F);
            if (flutter < 1F || flutter > 3F) problems.add("meadow butterflies at noon " + flutter + " per chunk (want 1-3)");
            if (Density.lambda(WildKinds.BUTTERFLY, meadow, 6000, 1F, 0F) != 0F) problems.add("butterflies in the rain");
            Density.Habitat snow = new Density.Habitat().set(Density.SNOWFIELD, 0.2F, -0.5F, 10, 2, 100, 10, 10, 4, 1);
            for (int k = WildKinds.BODIES; k < WildKinds.COUNT; k++) if (Density.lambda(k, snow, 6000, 0F, 0F) != 0F) problems.add(WildKinds.NAMES[k] + " in the snow");
            // fixed per chunk, and the expected number on average
            long key = 0x12345678ABL;
            if (Density.count(0.37F, key, 3) != Density.count(0.37F, key, 3)) problems.add("a chunk's count isn't fixed");
            double sum = 0;
            for (int i = 0; i < 20000; i++) sum += Density.count(0.37F, i * 7919L + 3, WildKinds.SONGBIRD);
            if (Math.abs(sum / 20000 - 0.37) > 0.02) problems.add("land averages " + sum / 20000 + " for 0.37");
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });

        // the caps hold however rich the land, and nothing lives inside Carillon's walls (those already there leave)
        AwakenTests.register("wildlife_census", 20, h -> {
            List<String> problems = new ArrayList<>();
            Census c = new Census();
            Density.Habitat rich = new Density.Habitat().set(Density.MIRE, 0.5F, 0.9F, 80, 8, 80, 40, 120, 40, 4);
            for (int tod : new int[]{0, 6000, 13500, 18000}) {
                c.reset();
                for (int s = 0; s < 400; s++) {
                    long key = s * 31L + 7;
                    for (int k = 0; k < WildKinds.COUNT; k++) {
                        int have = 0;
                        while (c.decide(k, 5, false, rich, key, have, tod, 0F, 0F, 4F) > 0) {
                            c.add(k);
                            have++;
                        }
                    }
                }
                for (int k = 0; k < WildKinds.COUNT; k++) if (c.count[k] > WildKinds.CAP[k]) problems.add(WildKinds.NAMES[k] + " " + c.count[k] + " over its cap at " + tod);
                if (c.bodies > WildKinds.MAX_BODIES || c.insects > WildKinds.MAX_INSECTS) problems.add(c.bodies + " bodies, " + c.insects + " insects at " + tod);
                if (tod == 6000 && c.bodies == 0) problems.add("a rich marsh at noon holds nothing");
            }
            c.reset();
            for (int k = 0; k < WildKinds.COUNT; k++) {
                if (c.decide(k, 5, true, rich, 99L, 0, 6000, 0F, 0F, 4F) > 0) problems.add(WildKinds.NAMES[k] + " made inside the city");
                if (c.decide(k, 5, true, rich, 99L, 2, 6000, 0F, 0F, 1F) >= 0) problems.add(WildKinds.NAMES[k] + " stays inside the city");
                if (c.decide(k, WildKinds.REACH[k] + 1, false, rich, 99L, 0, 6000, 0F, 0F, 4F) != 0) problems.add(WildKinds.NAMES[k] + " made out of reach");
            }
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });

        // every body parses, carries every clip its brain plays (a missing one freezes the animal) and every coat
        AwakenTests.register("wildlife_rigs", 20, h -> {
            List<String> problems = new ArrayList<>();
            Map<String, HeroRig> rigs = new java.util.HashMap<>();
            for (String[] src : WildFaunaData.SOURCES) {
                try {
                    HeroRig r = HeroRig.parse(src);
                    rigs.put(r.id, r);
                    // squirrels, rabbits and frogs run and hop: the walk-or-fly rule is for bodies with a gait clip
                    for (String q : r.problems()) if (!q.contains("neither walk nor fly")) problems.add(q);
                } catch (RuntimeException e) {
                    problems.add("a rig fails to parse: " + e.getMessage());
                }
            }
            for (int k = 0; k < WildKinds.BODIES; k++) {
                String id = WildKinds.RIGS[k];
                HeroRig r = rigs.get(id);
                if (r == null) {
                    problems.add(id + ": no rig");
                    continue;
                }
                for (String clip : CLIPS.get(id)) if (r.clip(clip) == null) problems.add(id + ": no " + clip + " clip");
                List<String> coats = WildFaunaData.VARIANTS.get(id);
                if (coats == null || coats.isEmpty() || !WildFaunaData.SCALE.containsKey(id)) {
                    problems.add(id + ": no coats or scale");
                    continue;
                }
                for (String v : coats) {
                    String base = "/assets/awaken/textures/entity/wild/" + id + (v.isEmpty() ? "" : "_" + v);
                    if (WildlifeTests.class.getResource(base + ".png") == null) problems.add(base + ".png missing");
                    if (WildlifeTests.class.getResource(base + "_blink.png") == null) problems.add(base + "_blink.png missing");
                }
            }
            if (WildlifeTests.class.getResource("/assets/awaken/textures/entity/wild/insects.png") == null) problems.add("insects.png missing");
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
            h.succeed();
        });
    }
}
