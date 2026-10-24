package dev.awaken.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.compat.Look.DhSetting;
import dev.awaken.compat.Look.Tier;
import dev.awaken.core.AwakenConfig.Client.LensLook;
import dev.awaken.core.test.AwakenTests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The far-view policy covers every dimension the data packs define: vanilla and the Stillpoint open, the rest closed.
 * The Look switch: each tier's keys, writing DistantHorizons.toml (only the owned keys, nothing else), the Ultra rule
 * and the frame-time line's percentiles, all as plain logic.
 */
final class CompatTests {
    private CompatTests() {}

    static void register() {
        AwakenTests.register("compat_far_view_policy", 20, h -> {
            int closed = 0;
            // every dimension the data packs define (the GameTest world itself only has the overworld)
            var files = h.getLevel().getServer().getResourceManager().listResources("dimension", f -> f.getPath().endsWith(".json"));
            for (var file : files.keySet()) {
                String path = file.getPath();
                String id = file.getNamespace() + ":" + path.substring("dimension/".length(), path.length() - ".json".length());
                boolean awaken = id.startsWith(Awaken.MODID + ":");
                boolean open = FarView.OPEN.contains(id);
                h.assertTrue(FarView.allowed(id) == (!awaken || open), "far view for " + id);
                if (awaken && !open) closed++;
            }
            h.assertTrue(FarView.allowed("minecraft:overworld") && FarView.allowed("awaken:stillpoint")
                    && !FarView.allowed("awaken:gates"), "far view basics");
            h.assertTrue(closed >= 10, "instanced Awaken dimensions found: " + closed);
            h.succeed();
        });

        AwakenTests.register("compat_look_tiers", 20, h -> {
            h.assertTrue(Tier.SWIFT.lens == LensLook.SWIFT && !Tier.SWIFT.irisShaders, "Swift: Lens Swift, no shaders");
            h.assertTrue(Tier.CINEMATIC.lens == LensLook.CINEMATIC && !Tier.CINEMATIC.irisShaders, "Cinematic: Lens Cinematic, no shaders");
            h.assertTrue(Tier.ULTRA.lens == LensLook.CINEMATIC && Tier.ULTRA.irisShaders, "Ultra: shaders, Lens Cinematic behind them");
            for (Tier t : Tier.values()) {
                List<DhSetting> dh = Look.dh(t);
                h.assertTrue(dh.size() == Look.DH_KEYS.size(), t + " sets every owned DH key");
                for (int i = 0; i < dh.size(); i++) h.assertTrue(dh.get(i).key() == Look.DH_KEYS.get(i), t + " DH key order " + i);
            }
            // the switch owns only keys the tiers disagree on: the rest (threads, cloud layers) stay the player's
            for (int i = 0; i < Look.DH_KEYS.size(); i++) {
                h.assertFalse(Look.dh(Tier.SWIFT).get(i).value().equals(Look.dh(Tier.CINEMATIC).get(i).value()),
                        "owned key differs between Swift and Cinematic: " + Look.DH_KEYS.get(i).key());
            }
            h.assertTrue(Look.dh(Tier.ULTRA).equals(Look.dh(Tier.CINEMATIC)), "Ultra keeps Cinematic's horizon");
            Map<String, String> swift = Map.of("lodChunkRenderDistanceRadius", "128", "maxHorizontalResolution", "\"TWO_BLOCKS\"",
                    "verticalQuality", "\"LOW\"", "horizontalQuality", "\"LOW\"", "enableSsao", "false", "enableTexturedLods", "false",
                    "enableAntiAliasing", "false");
            Map<String, String> cinematic = Map.of("lodChunkRenderDistanceRadius", "192", "maxHorizontalResolution", "\"BLOCK\"",
                    "verticalQuality", "\"MEDIUM\"", "horizontalQuality", "\"MEDIUM\"", "enableSsao", "true", "enableTexturedLods", "true",
                    "enableAntiAliasing", "true");
            for (DhSetting s : Look.dh(Tier.SWIFT)) h.assertTrue(s.toml().equals(swift.get(s.key().key())), "Swift " + s.key().key() + " = " + s.toml());
            for (DhSetting s : Look.dh(Tier.CINEMATIC)) h.assertTrue(s.toml().equals(cinematic.get(s.key().key())), "Cinematic " + s.key().key() + " = " + s.toml());
            // the installer's presets (pack/dh/*.json, when the project folder is next to the test run) say the same
            for (Tier t : new Tier[]{Tier.SWIFT, Tier.CINEMATIC}) {
                Path preset = Path.of("..", "pack", "dh", t.name().toLowerCase(java.util.Locale.ROOT) + ".json");
                if (!Files.exists(preset)) continue;
                try {
                    JsonObject json = JsonParser.parseString(Files.readString(preset)).getAsJsonObject();
                    for (DhSetting s : Look.dh(t)) {
                        if (json.has(s.key().key())) h.assertTrue(json.get(s.key().key()).getAsString().equals(s.toml()),
                                preset + " agrees on " + s.key().key());
                    }
                } catch (java.io.IOException e) {
                    h.fail("can't read " + preset + ": " + e);
                }
            }
            h.assertTrue(Look.current(LensLook.SWIFT, false) == Tier.SWIFT && Look.current(LensLook.CINEMATIC, false) == Tier.CINEMATIC
                    && Look.current(LensLook.OFF, false) == null && Look.current(LensLook.SWIFT, true) == Tier.ULTRA, "current tier");
            h.assertTrue(Boolean.TRUE.equals(Look.fits(Tier.SWIFT, 8.0f, 14f)) && Boolean.FALSE.equals(Look.fits(Tier.SWIFT, 10f, 14f))
                    && Boolean.FALSE.equals(Look.fits(Tier.SWIFT, 8f, 20f)) && Boolean.TRUE.equals(Look.fits(Tier.CINEMATIC, 15f, 24f))
                    && Look.fits(Tier.ULTRA, 30f, 60f) == null, "frame budgets");
            h.assertTrue(Look.dhMatches(Tier.SWIFT, k -> k == Look.RADIUS ? 128 : null), "DH matches Swift");
            h.assertFalse(Look.dhMatches(Tier.SWIFT, k -> k == Look.RADIUS ? 256 : null), "DH radius changed in its own menu");
            h.assertTrue(Look.dhMatches(Tier.CINEMATIC, k -> k == Look.MAX_RESOLUTION ? "BLOCK" : k == Look.SSAO ? Boolean.TRUE : null),
                    "DH matches Cinematic by enum name and boolean");
            h.succeed();
        });

        AwakenTests.register("compat_look_dh_file", 20, h -> {
            String toml = String.join("\n",
                    "_version = 5",
                    "[server]",
                    "\t[common.multiThreading]",
                    "\t\tnumberOfThreads = 5",
                    "[client]",
                    "\t[client.advanced.debugging]",
                    "\t\t# enableSsao = true is not this one",
                    "\t\tenableSsao = true",
                    "\t[client.advanced.graphics]",
                    "\t\t#",
                    "\t\t# Enabling Anti-Aliasing smooths edges.",
                    "\t\tenableAntiAliasing = true",
                    "\t\tenableSsao = true",
                    "\t\t[client.advanced.graphics.texture]",
                    "\t\t\tenableTexturedLods = true",
                    "\t\t[client.advanced.graphics.quality]",
                    "\t\t\tmaxHorizontalResolution = \"BLOCK\"",
                    "\t\t\tverticalQuality = \"MEDIUM\"",
                    "\t\t\t# lodChunkRenderDistanceRadius = 999",
                    "\t\t\tlodChunkRenderDistanceRadius = 256",
                    "\t\t\thorizontalQuality = \"MEDIUM\"",
                    "\t\t\tbrightnessMultiplier = \"1.0\"",
                    "");
            Look.Edit swift = Look.writeDh(toml, Look.dh(Tier.SWIFT));
            String[] before = toml.split("\n", -1), after = swift.text().split("\n", -1);
            h.assertTrue(before.length == after.length, "no lines added or lost: " + before.length + " vs " + after.length);
            int differ = 0;
            for (int i = 0; i < before.length; i++) if (!before[i].equals(after[i])) differ++;
            h.assertTrue(differ == 7, "exactly the seven owned keys changed: " + differ);
            h.assertTrue(swift.changed().size() == 7 && swift.added().isEmpty(), "changed " + swift.changed() + " added " + swift.added());
            h.assertTrue(after[7].equals("\t\tenableSsao = true"), "a same-named key in another table is untouched");
            h.assertTrue(after[6].equals(before[6]) && after[18].equals(before[18]), "comments untouched");
            h.assertTrue(after[3].equals("\t\tnumberOfThreads = 5") && after[21].equals("\t\t\tbrightnessMultiplier = \"1.0\""),
                    "keys the switch doesn't own are untouched");
            h.assertTrue(after[11].equals("\t\tenableAntiAliasing = false") && after[12].equals("\t\tenableSsao = false")
                    && after[14].equals("\t\t\tenableTexturedLods = false") && after[16].equals("\t\t\tmaxHorizontalResolution = \"TWO_BLOCKS\"")
                    && after[17].equals("\t\t\tverticalQuality = \"LOW\"") && after[19].equals("\t\t\tlodChunkRenderDistanceRadius = 128")
                    && after[20].equals("\t\t\thorizontalQuality = \"LOW\""), "Swift's values in place, indentation kept");
            Look.Edit again = Look.writeDh(swift.text(), Look.dh(Tier.SWIFT));
            h.assertTrue(again.text().equals(swift.text()) && again.changed().isEmpty(), "writing the same tier twice changes nothing");
            Look.Edit back = Look.writeDh(swift.text(), Look.dh(Tier.CINEMATIC));
            h.assertTrue(back.text().replace("lodChunkRenderDistanceRadius = 192", "lodChunkRenderDistanceRadius = 256").equals(toml),
                    "Swift then Cinematic gives back the original file (apart from Cinematic's 192-chunk horizon)");

            // a file missing keys and a table: keys go under their header, a missing table at the end; CRLF kept
            String partial = "[client]\r\n\t[client.advanced.graphics]\r\n\t\tenableSsao = true\r\n";
            Look.Edit filled = Look.writeDh(partial, Look.dh(Tier.CINEMATIC));
            h.assertTrue(filled.added().size() == 6, "added " + filled.added());
            h.assertTrue(filled.text().startsWith("[client]\r\n\t[client.advanced.graphics]\r\n\t\tenableAntiAliasing = true\r\n\t\tenableSsao = true\r\n"),
                    "missing key under its header: " + filled.text().replace("\r", "\\r").replace("\n", "\\n"));
            h.assertTrue(filled.text().contains("[client.advanced.graphics.quality]\r\n\tlodChunkRenderDistanceRadius = 192\r\n\tmaxHorizontalResolution = \"BLOCK\"\r\n")
                    && filled.text().contains("[client.advanced.graphics.texture]\r\n\tenableTexturedLods = true\r\n") && filled.text().endsWith("\r\n"),
                    "missing tables appended with their keys in order");
            Look.Edit refill = Look.writeDh(filled.text(), Look.dh(Tier.CINEMATIC));
            h.assertTrue(refill.text().equals(filled.text()) && refill.added().isEmpty(), "a filled file stays as it is");
            h.succeed();
        });

        AwakenTests.register("compat_look_ultra_gate", 20, h -> {
            Look.Availability none = Look.ultra(null, "OpenGL");
            h.assertTrue(!none.available() && none.reason().contains("Iris") && none.reason().contains("0.9.2"), "no Iris: " + none.reason());
            Look.Availability bad = Look.ultra("1.11.7+26.3-neoforge", "OpenGL");
            h.assertTrue(!bad.available() && bad.reason().startsWith("Iris 1.11.7 "), "Iris 1.11.7: " + bad.reason());
            h.assertTrue(!Look.ultra("1.10.2", "OpenGL").available(), "an older Iris either");
            h.assertTrue(Look.ultra("1.11.8+26.3-neoforge", "OpenGL").available(), "a newer Iris on OpenGL");
            Look.Availability vulkan = Look.ultra("1.12.0", "Vulkan");
            h.assertTrue(!vulkan.available() && vulkan.reason().contains("Vulkan"), "Iris needs OpenGL: " + vulkan.reason());
            h.assertTrue(Look.compareVersions("1.11.7+26.3-neoforge", "1.11.7") == 0 && Look.compareVersions("1.11.10", "1.11.7") > 0
                    && Look.compareVersions("1.12", "1.11.7") > 0 && Look.compareVersions("1.9.9-beta.1", "1.11.7") < 0, "version order");
            h.succeed();
        });

        AwakenTests.register("compat_look_frame_stats", 20, h -> {
            FrameStats stats = new FrameStats(32768);
            float[] out = new float[4];
            h.assertFalse(stats.percentiles(60_000_000_000L, 30, out), "nothing measured yet");
            for (int i = 0; i < 990; i++) stats.record(8_000_000L);
            for (int i = 0; i < 10; i++) stats.record(30_000_000L);
            h.assertTrue(stats.percentiles(60_000_000_000L, 30, out), "a thousand frames");
            h.assertTrue(Math.abs(out[0] - 8.05f) < 0.01f, "p50 " + out[0]);
            h.assertTrue(Math.abs(out[1] - 8.05f) < 0.01f, "p99 is the 990th frame: " + out[1]);
            h.assertTrue(out[3] == 1000 && Math.abs(out[2] - 8.22f) < 0.01f, "frames " + out[3] + " seconds " + out[2]);
            for (int i = 0; i < 20; i++) stats.record(30_000_000L);
            stats.percentiles(60_000_000_000L, 30, out);
            h.assertTrue(Math.abs(out[1] - 30.05f) < 0.01f, "3% slow frames reach p99: " + out[1]);
            // only the last minute counts: 70 s of 16 ms frames hide the earlier ones
            for (int i = 0; i < 4375; i++) stats.record(16_000_000L);
            stats.percentiles(60_000_000_000L, 30, out);
            h.assertTrue(out[2] <= 60.0f && out[2] > 59.9f && Math.abs(out[0] - 16.05f) < 0.01f && Math.abs(out[1] - 16.05f) < 0.01f,
                    "a 60 s window: " + out[2] + " s, p50 " + out[0] + ", p99 " + out[1]);
            // a frame beyond the histogram's range still counts, as its last bin
            stats.clear();
            for (int i = 0; i < 99; i++) stats.record(5_000_000L);
            stats.record(900_000_000L);
            stats.percentiles(60_000_000_000L, 30, out);
            h.assertTrue(Math.abs(out[1] - 5.05f) < 0.001f && out[3] == 100, "p99 of 100 is the 99th: " + out[1]);
            h.succeed();
        });
    }
}
