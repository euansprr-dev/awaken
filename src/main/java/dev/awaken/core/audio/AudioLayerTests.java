package dev.awaken.core.audio;

import com.google.gson.JsonObject;
import dev.awaken.Awaken;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.Theme;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * GameTests for the layered music and the loudness rules (docs/modules/audio.md "Layered music", "Mix"):
 * the intensity state machine (inputs to the level, holds, a Lord's fall), which stems each level sounds, every gate
 * theme's stems as equal-length 44.1 kHz loops, every level's mix within the music loudness rule after encoding
 * (decoded in Java, measured like audio_mix.py), the server's scan and damage hooks on real mobs, the payload, and
 * the Sound Physics Remastered dry list the pack ships.
 */
final class AudioLayerTests {
    private AudioLayerTests() {}

    static final String[] STEMS = {"bed", "tension", "combat", "lord"};
    /** The music rule (SPEC "Plan v2 · audio"). */
    static final double MUSIC_RMS = -19.0, MUSIC_TOL = 2.0, MUSIC_TP = -1.0;

    static void register() {
        AwakenTests.register("audio_layers_intensity", 40, AudioLayerTests::intensity);
        AwakenTests.register("audio_layers_manifests", 40, AudioLayerTests::manifests);
        // the test server ticks as fast as it can, so ticks are no clock: a generous ceiling, the work runs on its own thread
        AwakenTests.register("audio_layers_loudness", 20 * 60 * 60 * 4, AudioLayerTests::loudness);
        AwakenTests.register("audio_layers_watch", 80, AudioLayerTests::watch);
        AwakenTests.register("audio_layers_payload", 40, AudioLayerTests::payload);
        AwakenTests.register("compat_spr_dry_list", 40, AudioLayerTests::sprDryList);
    }

    // ---- the state machine -------------------------------------------------------------------------------------------

    static void intensity(GameTestHelper h) {
        Intensity in = new Intensity();
        Intensity.Signals s = new Intensity.Signals();
        long t = 1000;
        h.assertTrue(in.update(t, s) == Intensity.CALM, "nothing around: calm");
        s.near = 1;
        h.assertTrue(in.update(t += 10, s) == Intensity.TENSION, "a hostile within 10 blocks: tension");
        s.clearScan();
        h.assertTrue(in.update(t += 10, s) == Intensity.TENSION, "tension holds after the hostile is gone");
        h.assertTrue(in.update(t += Intensity.HOLD[Intensity.TENSION], s) == Intensity.CALM, "and lets go after its hold");
        s.pressure = 2;
        h.assertTrue(in.update(t += 10, s) == Intensity.TENSION, "the gate's waves are tension");
        s.clearScan();
        s.pressure = 1;
        in.update(t += Intensity.HOLD[Intensity.TENSION] + 10, s);
        h.assertTrue(in.level() == Intensity.CALM, "a stirring gate alone is not tension yet");
        s.clearScan();
        s.hunting = 1;
        s.lastCombat = t + 10;
        h.assertTrue(in.update(t += 10, s) == Intensity.COMBAT, "a blow traded: combat at once");
        s.clearScan();
        h.assertTrue(in.update(t += Intensity.COMBAT_WINDOW + 10, s) == Intensity.COMBAT, "combat holds after the last blow");
        h.assertTrue(in.update(t += Intensity.HOLD[Intensity.COMBAT], s) == Intensity.TENSION || in.level() == Intensity.CALM,
                "then steps down");
        in.update(t += Intensity.HOLD[Intensity.TENSION] + 10, s);
        h.assertTrue(in.level() == Intensity.CALM, "and comes to rest");
        s.huntingClose = Intensity.SWARM;
        s.hunting = Intensity.SWARM;
        h.assertTrue(in.update(t += 10, s) == Intensity.COMBAT, Intensity.SWARM + " hostiles hunting close: combat before a blow");
        s.clearScan();
        s.lordAlive = true;
        s.lordDistance = 35;
        h.assertTrue(in.update(t += 10, s) == Intensity.COMBAT, "a Lord far off doesn't override a running fight");
        s.lordDistance = Intensity.LORD_ARENA - 1;
        h.assertTrue(in.update(t += 10, s) == Intensity.LORD, "in the Lord's arena: the Lord's layer");
        s.lordDistance = 35;
        s.lordHunting = true;
        h.assertTrue(in.update(t += 10, s) == Intensity.LORD, "a Lord that hunts you from afar too");
        s.lordFell = t + 5;
        s.lordAlive = s.lordHunting = false;
        s.lordDistance = Double.MAX_VALUE;
        int after = in.update(t += 10, s);
        h.assertTrue(after < Intensity.COMBAT, "the Lord fell: the fight is over at once (got " + Intensity.name(after) + ")");
        s.forced = Intensity.LORD;
        h.assertTrue(in.update(t += 10, s) == Intensity.LORD, "a pinned level wins");
        s.forced = -1;
        in.reset();
        s.reset();
        h.assertTrue(in.update(t += 10, s) == Intensity.CALM, "reset: calm");
        for (int l = 0; l < 4; l++) h.assertTrue(Intensity.byName(Intensity.name(l)) == l, "level names round-trip");
        h.succeed();
    }

    // ---- manifests and files -----------------------------------------------------------------------------------------

    static LayerManifest manifest(Identifier ctx) {
        Identifier path = LayerManifest.path(ctx);
        JsonObject o = AudioTests.json("/assets/" + path.getNamespace() + "/" + path.getPath());
        return o == null ? null : LayerManifest.parse(ctx, o);
    }

    /** Total samples of an Ogg Vorbis file: the granule position of its last page. */
    static long oggFrames(String resource) {
        try (InputStream in = AudioLayerTests.class.getResourceAsStream(resource)) {
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

    static String file(LayerManifest.Stem s) {
        return "/assets/" + s.file().getNamespace() + "/sounds/" + s.file().getPath() + ".ogg";
    }

    /** Every gate theme with music has a context of four stems; each level adds its stem; the stems loop together. */
    static void manifests(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        int themes = 0;
        for (Theme t : Theme.values()) {
            Identifier ctx = GateMusic.contextOf(t);
            if (t == Theme.ABYSSAL_SHRINE) {
                h.assertTrue(ctx == null, "the Abyssal Shrine keeps its silence (no layered music)");
                continue;
            }
            h.assertTrue(ctx != null && ctx.equals(Awaken.id("gate/" + t.id)), t + ": context awaken:gate/" + t.id);
            LayerManifest m;
            try {
                m = manifest(ctx);
            } catch (RuntimeException e) {
                problems.add(t.id + ": " + e.getMessage());
                continue;
            }
            if (m == null) {
                problems.add(t.id + ": no manifest at " + LayerManifest.path(ctx));
                continue;
            }
            themes++;
            if (!m.music()) problems.add(t.id + ": gate music must be on the music source");
            if (m.stems().size() != 4) problems.add(t.id + ": " + m.stems().size() + " stems");
            for (int k = 0; k < Math.min(4, m.stems().size()); k++) {
                if (!m.stems().get(k).name().equals(STEMS[k])) problems.add(t.id + ": stem " + k + " is " + m.stems().get(k).name());
            }
            // which layers each level sounds: calm the bed alone, then each level brings in its own stem
            for (int l = 0; l < 4; l++) {
                float[] g = m.gainsAt(l);
                for (int k = 0; k < g.length; k++) {
                    boolean on = g[k] > 0F;
                    if (on != (k <= l)) problems.add(t.id + " " + Intensity.name(l) + ": " + STEMS[k] + (on ? " sounds" : " is silent"));
                }
                if (g[l] < 0.99F) problems.add(t.id + " " + Intensity.name(l) + ": its own stem at " + g[l]);
            }
            // equal-length, phase-aligned loops: every stem exactly the manifest's frames, whole bars at the tempo
            JsonObject o = AudioTests.json("/assets/awaken/music_layers/gate/" + t.id + ".json");
            double bars = o.get("bars").getAsDouble() * o.get("beats").getAsDouble() * 60.0 / o.get("bpm").getAsDouble() * 44100.0;
            if (Math.abs(bars - m.frames()) > 1) problems.add(t.id + ": " + m.frames() + " frames is not whole bars (" + bars + ")");
            for (LayerManifest.Stem s : m.stems()) {
                int[] hdr = AudioTests.vorbisHeader(file(s));
                long frames = oggFrames(file(s));
                if (hdr == null) problems.add(file(s) + " missing or not Ogg Vorbis");
                else if (hdr[1] != 44100) problems.add(file(s) + ": " + hdr[1] + " Hz");
                if (frames != m.frames()) problems.add(file(s) + ": " + frames + " frames, the loop is " + m.frames());
                if (s.attack() <= 0 || s.release() <= 0 || s.duck() <= 0) problems.add(file(s) + ": fade times");
            }
            if (m.stems().get(2).attack() > 1.0F) problems.add(t.id + ": combat must hit within a second");
        }
        h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        h.assertTrue(themes == Theme.values().length - 1, "every gate theme with music has layers (" + themes + ")");
        h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(AudioSounds.MUSIC_LAYERS.getId()), "music.layers is registered");
        h.succeed();
    }

    // ---- loudness ----------------------------------------------------------------------------------------------------

    /**
     * Every level's mix of every gate theme, decoded from the shipped files: gated RMS within the music rule and true
     * peak under -1 dBTP; each stem alone under -1 dBTP; JOrbis decodes exactly the loop's frames. Runs off-thread.
     */
    static void loudness(GameTestHelper h) {
        CompletableFuture<List<String>> job = new CompletableFuture<>();
        Thread worker = new Thread(() -> {
            try {
                job.complete(measureAll());
            } catch (Throwable e) {
                job.completeExceptionally(e);
            }
        }, "awaken-loudness-test");
        worker.setDaemon(true);
        worker.start();
        h.succeedWhen(() -> {
            h.assertTrue(job.isDone(), "measuring the layered music");
            List<String> problems = job.join();
            h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        });
    }

    static List<String> measureAll() {
        {
            List<String> problems = new ArrayList<>();
            StringBuilder report = new StringBuilder();
            long t0 = System.nanoTime();
            for (Theme t : Theme.values()) {
                Identifier ctx = GateMusic.contextOf(t);
                if (ctx == null) continue;
                LayerManifest m = manifest(ctx);
                if (m == null) {
                    problems.add(t.id + ": no manifest");
                    continue;
                }
                float[][][] stems = new float[m.stems().size()][][];
                for (int k = 0; k < stems.length; k++) {
                    LayerManifest.Stem s = m.stems().get(k);
                    try (InputStream in = AudioLayerTests.class.getResourceAsStream(file(s))) {
                        if (in == null) {
                            problems.add(file(s) + " missing");
                            continue;
                        }
                        Loudness.Pcm pcm = Loudness.decode(in);
                        if (pcm.rate() != 44100) problems.add(file(s) + ": " + pcm.rate() + " Hz");
                        if (pcm.frames() != m.frames()) problems.add(file(s) + ": decodes to " + pcm.frames() + " frames, loop " + m.frames());
                        stems[k] = pcm.channels();
                        double tp = Loudness.truePeak(pcm.channels());
                        if (tp > MUSIC_TP + 0.05) problems.add(file(s) + String.format(": alone, true peak %.2f", tp));
                    } catch (IOException e) {
                        problems.add(file(s) + ": " + e);
                    }
                }
                if (java.util.Arrays.stream(stems).anyMatch(x -> x == null)) continue;
                int n = stems[0][0].length;
                for (float[][] st : stems) if (st[0].length != n) problems.add(t.id + ": stems differ in length");
                report.append('\n').append(t.id);
                for (int l = 0; l < 4; l++) {
                    Loudness.Measure me = Loudness.measure(Loudness.mix(stems, m.gainsAt(l)));
                    report.append(String.format(java.util.Locale.ROOT, "  %s %.1f/%.1f", Intensity.name(l), me.rms(), me.truePeak()));
                    if (Math.abs(me.rms() - MUSIC_RMS) > MUSIC_TOL) {
                        problems.add(String.format("%s %s: RMS %.2f dBFS (music %.0f +-%.0f)", t.id, Intensity.name(l), me.rms(), MUSIC_RMS, MUSIC_TOL));
                    }
                    if (me.truePeak() > MUSIC_TP + 0.05) problems.add(String.format("%s %s: true peak %.2f dBTP", t.id, Intensity.name(l), me.truePeak()));
                    if (me.lufs() < -30 || me.lufs() > -10) problems.add(String.format("%s %s: %.1f LUFS", t.id, Intensity.name(l), me.lufs()));
                }
                report.append(String.format(java.util.Locale.ROOT, "  (%.0f s)", (System.nanoTime() - t0) / 1e9));
            }
            Awaken.LOG.info("[audio] layered music loudness (level RMS/true peak):{}", report);
            return problems;
        }
    }

    // ---- the server's watch on real mobs -----------------------------------------------------------------------------

    static void watch(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = survivalPlayer(h);
        BlockPos base = h.absolutePos(new BlockPos(1, 1, 1));
        p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        Intensity.Signals s = new Intensity.Signals();

        h.assertTrue(MusicLayers.resolve(MusicLayers.Slot.MUSIC, p, s) == null, "outside a gate: no layered music");
        Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.MOB_SUMMONED);
        husk.snapTo(base.getX() + 6.5, base.getY(), base.getZ() + 0.5);
        husk.setNoAi(true);
        level.addFreshEntity(husk);
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.MOB_SUMMONED);
        pig.snapTo(base.getX() - 3.5, base.getY(), base.getZ() + 0.5);
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        h.assertTrue(LayerWatch.hostile(husk), "a monster is hostile");
        h.assertTrue(!LayerWatch.hostile(pig), "a pig is not");
        LayerWatch.scan(p, s);
        h.assertTrue(s.near == 1 && s.hunting == 0, "a monster 6 blocks off, unaware: near, not hunting (" + s.near + "/" + s.hunting + ")");
        s.clearScan();
        husk.setTarget(p);
        LayerWatch.scan(p, s);
        h.assertTrue(s.hunting == 1 && s.huntingClose == 1, "once it targets you it hunts you");
        s.clearScan();

        // the whole loop: audition a context (game master), let the watch decide, hurt the player, kill a Lord
        LayerWatch.PlayerState st = LayerWatch.state(p);
        Identifier ctx = Awaken.id("gate/bell_crypt");
        st.override = ctx;
        long now = level.getServer().getTickCount();
        LayerWatch.evaluate(p, now);
        h.assertTrue(ctx.equals(MusicLayers.current(p, MusicLayers.Slot.MUSIC)), "the auditioned context is the music slot's");
        h.assertTrue(MusicLayers.level(p, MusicLayers.Slot.MUSIC) == Intensity.TENSION, "a monster hunting you: tension, got "
                + Intensity.name(MusicLayers.level(p, MusicLayers.Slot.MUSIC)));
        p.hurtServer(level, p.damageSources().mobAttack(husk), 1F);
        LayerWatch.evaluate(p, now + 10);
        h.assertTrue(MusicLayers.level(p, MusicLayers.Slot.MUSIC) == Intensity.COMBAT, "a blow taken: combat, got "
                + Intensity.name(MusicLayers.level(p, MusicLayers.Slot.MUSIC)));
        LivingEntity lord = MobHooks.spawnBoss(level, "awaken:bell_warden", base.offset(10, 0, 0), 5, "");
        if (lord instanceof Mob m) m.setNoAi(true);
        LayerWatch.evaluate(p, now + 20);
        h.assertTrue(MusicLayers.level(p, MusicLayers.Slot.MUSIC) == Intensity.LORD, "a Lord 10 blocks off (in its arena): the Lord's layer, got "
                + Intensity.name(MusicLayers.level(p, MusicLayers.Slot.MUSIC)));
        lord.kill(level);
        h.assertTrue(st.signals.lordFell >= now, "the Lord's death was heard");
        lord.discard();
        LayerWatch.evaluate(p, level.getServer().getTickCount());
        int after = MusicLayers.level(p, MusicLayers.Slot.MUSIC);
        h.assertTrue(after < Intensity.LORD, "after the Lord falls its layer goes, got " + Intensity.name(after));
        st.override = null;
        LayerWatch.evaluate(p, level.getServer().getTickCount());
        h.assertTrue(MusicLayers.current(p, MusicLayers.Slot.MUSIC) == null, "no context again");
        husk.discard();
        pig.discard();
        LayerWatch.forget(p.getUUID());
        level.getServer().getPlayerList().remove(p);
        h.succeed();
    }

    /**
     * A connected mock player that really is in survival: the helper's own mock reports creative whatever its game
     * mode, and monsters never target a creative player.
     */
    static ServerPlayer survivalPlayer(GameTestHelper h) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "sound-test"), false);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        player.connection.markClientLoaded(); // otherwise a fresh player cannot be hurt
        return player;
    }

    static void payload(GameTestHelper h) {
        ByteBuf buf = Unpooled.buffer();
        var msg = new AudioNet.LayerState(MusicLayers.Slot.MUSIC.ordinal(), "awaken:gate/frost_monastery", Intensity.LORD,
                AudioNet.LayerState.ARRIVAL | AudioNet.LayerState.VICTORY);
        AudioNet.LayerState.STREAM_CODEC.encode(buf, msg);
        h.assertTrue(AudioNet.LayerState.STREAM_CODEC.decode(buf).equals(msg), "layer state round-trips");
        buf.clear();
        var none = new AudioNet.LayerState(MusicLayers.Slot.AMBIENCE.ordinal(), "", 0, AudioNet.LayerState.DEATH);
        AudioNet.LayerState.STREAM_CODEC.encode(buf, none);
        h.assertTrue(AudioNet.LayerState.STREAM_CODEC.decode(buf).equals(none), "an empty context round-trips");
        h.assertTrue(MusicLayers.Slot.byOrdinal(7) == null, "unknown slots are ignored");
        h.succeed();
    }

    // ---- Sound Physics Remastered ------------------------------------------------------------------------------------

    /** The repository root (GameTests run from run/ or a worktree's run/): the folder holding pack/mods.json. */
    static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && p != null; i++, p = p.getParent()) {
            if (Files.exists(p.resolve("pack/mods.json"))) return p;
        }
        return null;
    }

    /**
     * The pack's SPR config keeps the System's voice, the UI, every music event and the Ritual's voices dry (value 0),
     * every key it names is a real sound event, and pack/mods.json ships it with the sound profile.
     */
    static void sprDryList(GameTestHelper h) {
        Path root = repoRoot();
        h.assertTrue(root != null, "the repository (pack/mods.json) is found from " + System.getProperty("user.dir"));
        Map<String, String> rates = new HashMap<>();
        try {
            for (String line : Files.readAllLines(root.resolve("pack/spr/sound_rates.properties"), StandardCharsets.UTF_8)) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                int eq = l.indexOf('=');
                rates.put(l.substring(0, eq).replace("\\:", ":").trim(), l.substring(eq + 1).trim());
            }
            String mods = Files.readString(root.resolve("pack/mods.json"));
            h.assertTrue(mods.contains("\"pack/spr/sound_rates.properties\"") && mods.contains("config/sound_physics_remastered/sound_rates.properties"),
                    "pack/mods.json ships the SPR config");
        } catch (IOException e) {
            h.fail("reading the pack's SPR config: " + e);
            return;
        }
        List<String> problems = new ArrayList<>();
        for (var e : rates.entrySet()) {
            Identifier id = Identifier.tryParse(e.getKey());
            if (id == null || !BuiltInRegistries.SOUND_EVENT.containsKey(id)) problems.add("not a sound event: " + e.getKey());
            if (!e.getValue().equals("0")) problems.add(e.getKey() + " = " + e.getValue() + " (the dry list only holds 0)");
        }
        List<String> mustBeDry = new ArrayList<>(List.of("awaken:level_up", "awaken:rank_up", "awaken:signal.chime", "awaken:signal.open",
                "awaken:signal.warning", "awaken:music.layers", "awaken:amb.layers"));
        for (String v : new String[]{"words", "tear", "close", "drain", "heartbeat", "void"}) mustBeDry.add("awaken:story.ritual." + v);
        for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (id.getNamespace().equals("awaken") && (id.getPath().startsWith("music.") || id.getPath().startsWith("ui."))) mustBeDry.add(id.toString());
        }
        for (String id : mustBeDry) if (!"0".equals(rates.get(id))) problems.add(id + " is not kept dry");
        h.assertTrue(problems.isEmpty(), String.join("; ", problems));
        h.succeed();
    }
}
