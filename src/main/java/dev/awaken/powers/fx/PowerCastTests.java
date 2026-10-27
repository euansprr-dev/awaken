package dev.awaken.powers.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.anims.FpClipSet;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Fx;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.Powers;
import dev.awaken.powers.Running;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GameTests for cast profiles (docs/power-anims/PLAN.md): the table holds to the clip files (third and first person, the
 * release tick a key in both), a profile's beat survives the wire, and a power with a wind-up begins on the press and
 * happens when the foot lands.
 */
final class PowerCastTests {
    private PowerCastTests() {}

    static void register() {
        AwakenTests.register("power_cast_profiles", 20, PowerCastTests::profiles);
        AwakenTests.registerIsolated("power_cast_windup", 200, PowerCastTests::windup);
    }

    private static void profiles(net.minecraft.gametest.framework.GameTestHelper h) {
        JsonObject anims;
        try (InputStream in = PowerCastTests.class.getResourceAsStream("/" + CastClips.FILE)) {
            h.assertTrue(in != null, "missing " + CastClips.FILE);
            anims = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        StringBuilder errors = new StringBuilder();
        Map<String, FpClipSet.Clip> fp = FpClipSet.load(errors);
        h.assertTrue(errors.isEmpty(), "first-person clips read: " + errors);
        JsonObject looks;
        try (InputStream in = PowerCastTests.class.getResourceAsStream("/assets/awaken/lens/looks_powers.json")) {
            h.assertTrue(in != null, "missing assets/awaken/lens/looks_powers.json");
            looks = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("looks");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        Set<Integer> ids = new HashSet<>();
        for (CastProfiles.Profile p : CastProfiles.all()) {
            h.assertTrue(ids.add(p.id()) && CastProfiles.byId(p.id()) == p && CastProfiles.byPower("awaken:" + p.power()) == p, p.power() + ": ids and lookups agree");
            PowerType t = PowerRegistry.get(p.power());
            h.assertTrue(t != null && !t.isPassive(), p.power() + " is a castable power");
            // third person: every clip, with its bent twin; the begin clip has a key on the release tick; only the hold loops
            List<String> names = new ArrayList<>(List.of(p.begin3p()));
            if (p.hold3p() != null) names.add(p.hold3p());
            if (p.end3p() != null) names.add(p.end3p());
            for (String n : names) {
                for (String name : new String[]{n, n + "_bent"}) {
                    h.assertTrue(anims.has(name), p.power() + ": clip " + name + " missing from " + CastClips.FILE + " (python3 tools/art/specs/power_casts.py build)");
                    JsonObject a = anims.getAsJsonObject(name);
                    boolean loops = a.has("loop") && a.get("loop").getAsBoolean();
                    // only a hold loops (or a channel's begin clip that is itself the family's wind-up loop)
                    h.assertTrue(!loops || n.equals(p.hold3p()) || n.equals(p.begin3p()) && p.channel() && p.hold3p() == null, name + " loops but is not a hold");
                    if (!loops) {
                        JsonElement end = a.has("player_animation_library") ? a.getAsJsonObject("player_animation_library").get("endTick") : null;
                        h.assertTrue(end != null && end.getAsFloat() < a.get("animation_length").getAsFloat() * 20, name + " eases back to the vanilla pose (endTick)");
                    }
                }
            }
            boolean beginLoops = anims.getAsJsonObject(p.begin3p()).has("loop");
            h.assertTrue(beginLoops || hasKeyAt(anims.getAsJsonObject(p.begin3p()), p.release() / 20.0), p.power() + ": " + p.begin3p() + " has a key on the release tick " + p.release());
            h.assertTrue(p.windup() == 0 || p.windup() == p.release(), p.power() + ": a wind-up ends on the release tick");
            h.assertTrue(p.lens() == null || looks.has(p.lens()), p.power() + ": its Lens look " + p.lens() + " exists");
            h.assertTrue(p.beginSound() == null || net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(net.minecraft.resources.Identifier.parse(p.beginSound())), p.power() + ": its begin sound exists");
            h.assertTrue(CastProfiles.MOMENTS.contains(p.moment()) && CastProfiles.MOMENTS.contains(p.endMoment()), p.power() + ": its screen moments exist");
            // first person: the clips exist; the begin clip says its release (the plant, the hold) on the profile's tick
            for (String n : p.hold1p() == null ? List.of(p.begin1p()) : List.of(p.begin1p(), p.hold1p())) {
                h.assertTrue(fp.containsKey(n), p.power() + ": first-person clip " + n + " missing (python3 tools/art/specs/fp_casts.py build)");
            }
            FpClipSet.Clip c = fp.get(p.begin1p());
            boolean says = false;
            for (int i = 0; i < c.events.length; i++) says |= Math.abs(c.eventAt[i] - p.release()) < 1e-3 && (c.events[i].equals("plant") || c.events[i].equals("hold") || c.events[i].equals("release"));
            h.assertTrue(says, p.power() + ": " + p.begin1p() + " has its release event on tick " + p.release());
            h.assertTrue(p.hold1p() == null || fp.get(p.hold1p()).loop, p.power() + ": the first-person hold loops");
            // the beat rides the wire flags and is not read as a pose
            FxEvent e = FxEvent.of(FxKind.POSE, FxStyle.KINETIC, Vec3.ZERO).profile(p.id(), CastProfiles.END).build();
            h.assertTrue(e.profile() == p.id() && e.beat() == CastProfiles.END && e.anim() == CastAnim.NONE, p.power() + ": a profile beat is not a pose");
        }
        FxEvent plain = FxEvent.of(FxKind.CAST, FxStyle.ENERGY, Vec3.ZERO).anim(CastAnim.SKYWARD).build();
        h.assertTrue(plain.profile() == 0 && plain.anim() == CastAnim.SKYWARD, "a family pose is not a profile");
        h.succeed();
    }

    private static boolean hasKeyAt(JsonObject clip, double seconds) {
        for (String bone : clip.getAsJsonObject("bones").keySet()) {
            for (String target : clip.getAsJsonObject("bones").getAsJsonObject(bone).keySet()) {
                for (String key : clip.getAsJsonObject("bones").getAsJsonObject(bone).getAsJsonObject(target).keySet()) {
                    if (Math.abs(Double.parseDouble(key) - seconds) < 1e-4) return true;
                }
            }
        }
        return false;
    }

    /** The stamp: the press sends its beat at once, the wave comes when the foot lands (the profile's wind-up), from a stamp event. */
    private static void windup(net.minecraft.gametest.framework.GameTestHelper h) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(0, 1, 0)));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f);
        sp.setGameMode(GameType.SURVIVAL);
        PowerType t = PowerRegistry.get("shockwave");
        h.assertTrue(t != null, "shockwave exists");
        CastProfiles.Profile prof = CastProfiles.byPower("shockwave");
        h.assertTrue(prof != null && prof.windup() > 0, "shockwave has a wind-up profile");
        Powers.learn(sp, t.id, 0);
        Powers.resetCooldowns(sp);
        Powers.fillEnergy(sp);
        List<FxEvent> seen = new ArrayList<>();
        Fx.testSink = seen::add;
        try {
            PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
            h.assertTrue(r.ok(), "the stamp casts: " + r.reason());
            h.assertTrue(seen.stream().anyMatch(e -> e.kind() == FxKind.POSE && e.profile() == prof.id() && e.beat() == CastProfiles.BEGIN), "the press begins the pose at once");
            h.assertTrue(seen.stream().noneMatch(e -> e.kind() == FxKind.CONE), "no wave on the press tick");
            Fx.testTick(sp, prof.windup() - 2);
            h.assertTrue(seen.stream().noneMatch(e -> e.kind() == FxKind.CONE), "no wave before the foot lands");
            Fx.testTick(sp, 3);
            FxEvent wave = seen.stream().filter(e -> e.kind() == FxKind.CONE).findFirst().orElse(null);
            h.assertTrue(wave != null, "the wave leaves when the foot lands");
            h.assertTrue(wave.profile() == prof.id() && wave.beat() == CastProfiles.RELEASE && wave.has(FxEvent.REACH), "the wave is the profile's release and reports its reach");
        } finally {
            Fx.testSink = null;
            Casting.endAll(sp, Running.Why.CANCELLED);
        }
        h.succeed();
    }
}
