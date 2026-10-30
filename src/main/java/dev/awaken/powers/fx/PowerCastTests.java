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
        AwakenTests.registerIsolated("power_cast_beats", 300, PowerCastTests::beats);
        AwakenTests.registerIsolated("power_cast_ball", 200, PowerCastTests::ball);
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
        // every row, the ladder's level rows too (SPEC 36: the same id and timing, their own clips, held to the same rules)
        for (CastProfiles.Profile p : CastProfiles.allLevels()) {
            CastProfiles.Profile base = CastProfiles.byId(p.id());
            if (base == p) h.assertTrue(ids.add(p.id()) && CastProfiles.byPower("awaken:" + p.power()) == p, p.power() + ": ids and lookups agree");
            else h.assertTrue(base != null && base.power().equals(p.power()) && base.release() == p.release() && base.windup() == p.windup()
                    && CastProfiles.at(base, 1) == base, p.power() + ": a level row keeps the power's id and timing");
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
            h.assertTrue(p.channel() == (t.mode == PowerType.Mode.CHANNEL), p.power() + ": the profile matches the power's lifetime");
            h.assertTrue(p.hold1p() == null || p.channel() && fp.get(p.hold1p()).loop, p.power() + ": only a channel has a first-person hold");
            h.assertTrue(p.end1p() == null || p.channel() && fp.containsKey(p.end1p()) && !fp.get(p.end1p()).loop,
                    p.power() + ": a first-person let-go is a channel's, exists and ends by itself");
            h.assertTrue(p.holdSound() == null || p.channel() && net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(
                    net.minecraft.resources.Identifier.parse(p.holdSound())), p.power() + ": a held sound is a channel's and exists");
            h.assertTrue(List.of("palm", "core", "ball").contains(p.origin()), p.power() + ": its origin is a named cast point");
            if (!p.channel()) {
                h.assertTrue(!c.loop, p.power() + ": an instant first-person cast ends by itself");
                for (String event : c.events) h.assertTrue(!event.equals("hold"), p.power() + ": an instant cast never starts a held loop");
            }
            // the beat rides the wire flags and is not read as a pose
            FxEvent e = FxEvent.of(FxKind.POSE, FxStyle.KINETIC, Vec3.ZERO).profile(p.id(), CastProfiles.END).build();
            h.assertTrue(e.profile() == p.id() && e.beat() == CastProfiles.END && e.anim() == CastAnim.NONE, p.power() + ": a profile beat is not a pose");
        }
        FxEvent plain = FxEvent.of(FxKind.CAST, FxStyle.ENERGY, Vec3.ZERO).anim(CastAnim.SKYWARD).build();
        h.assertTrue(plain.profile() == 0 && plain.anim() == CastAnim.SKYWARD, "a family pose is not a profile");
        h.succeed();
    }

    /** Every profile power that casts sends its profile beat within the cast (observers see the pose), and never a family pose beside it. */
    private static void beats(net.minecraft.gametest.framework.GameTestHelper h) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(-3, 1, 0)));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f);
        sp.setGameMode(GameType.SURVIVAL);
        List<FxEvent> seen = new ArrayList<>();
        List<String> silent = new ArrayList<>(), posed = new ArrayList<>();
        int cast = 0;
        Fx.testSink = seen::add;
        try {
            for (CastProfiles.Profile p : CastProfiles.all()) {
                PowerType t = PowerRegistry.get(p.power());
                Powers.learn(sp, t.id, 0);
                Powers.resetCooldowns(sp);
                Powers.fillEnergy(sp);
                net.minecraft.world.entity.monster.zombie.Zombie z = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(0, 1, 0));
                z.setNoAi(true);
                Vec3 to = z.getEyePosition().subtract(sp.getEyePosition());
                float yRot = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                seen.clear();
                PowerType.Result r = Casting.cast(sp, t, 0, false, yRot, 0f);
                if (r.ok()) {
                    cast++;
                    if (seen.stream().noneMatch(e -> e.profile() == p.id())) silent.add(p.power());
                    if (seen.stream().anyMatch(e -> e.profile() == 0 && e.anim() != CastAnim.NONE && e.caster() == sp.getId())) posed.add(p.power());
                }
                Casting.endAll(sp, Running.Why.CANCELLED);
                z.discard();
                for (var e : h.getLevel().getEntitiesOfClass(dev.awaken.powers.content.PowerSummon.class, sp.getBoundingBox().inflate(48), e -> e.getOwner() == sp)) e.discard();
            }
        } finally {
            Fx.testSink = null;
        }
        h.assertTrue(cast >= 40, "most profile powers cast against a zombie (" + cast + " of " + CastProfiles.all().size() + ")");
        h.assertTrue(silent.isEmpty(), "these powers sent no profile beat, so others would not see their pose: " + silent);
        h.assertTrue(posed.isEmpty(), "these powers also sent a family pose beside their profile: " + posed);
        h.succeed();
    }

    /**
     * Charged Shot leaves from between the hands (SPEC 32): at a short and a full charge, looking up, level and down, the muzzle
     * burst is at {@link CastPoints#ball} (centred on the look, not off to the right hand as it was), and the bolt's first step
     * starts there.
     */
    private static void ball(net.minecraft.gametest.framework.GameTestHelper h) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(0, 1, 0)));
        sp.setGameMode(GameType.SURVIVAL);
        PowerType t = PowerRegistry.get("charged_shot");
        CastProfiles.Profile prof = CastProfiles.byPower("charged_shot");
        h.assertTrue(t != null && prof != null && prof.ball(), "charged_shot's profile fires from the ball");
        Powers.learn(sp, t.id, 0);
        List<FxEvent> seen = new ArrayList<>();
        Fx.testSink = seen::add;
        List<Vec3> balls = new ArrayList<>();
        try {
            float yaw = 37f;
            for (float pitch : new float[] {-30f, 0f, 30f}) {
                for (int held : new int[] {6, 30}) {
                    sp.snapTo(at.x, at.y, at.z, yaw, pitch);
                    Powers.resetCooldowns(sp);
                    Powers.fillEnergy(sp);
                    Casting.endAll(sp, Running.Why.CANCELLED);
                    seen.clear();
                    PowerType.Result r = Casting.cast(sp, t, 0, false, yaw, pitch);
                    h.assertTrue(r.ok(), "the charge starts: " + r.reason());
                    Fx.testTick(sp, held);
                    Casting.endAll(sp, Running.Why.RELEASED);
                    Vec3 ball = CastPoints.ball(sp);
                    FxEvent muzzle = seen.stream().filter(e -> e.kind() == FxKind.IMPACT).findFirst().orElse(null);
                    h.assertTrue(muzzle != null, "a shot at pitch " + pitch + " after " + held + " ticks");
                    Vec3 m = new Vec3(muzzle.x(), muzzle.y(), muzzle.z());
                    Vec3 right = sp.getLookAngle().cross(sp.getUpVector(1f));
                    double side = m.subtract(sp.getEyePosition()).dot(right);
                    h.assertTrue(m.distanceTo(ball) < 1e-3, "pitch " + pitch + ", " + held + " ticks: the shot leaves the ball " + ball + ", not " + m);
                    h.assertTrue(Math.abs(side) < 1e-3, "pitch " + pitch + ": the shot is centred between the hands (" + side + " to the side)");
                    balls.add(ball);
                }
            }
        } catch (RuntimeException ex) {
            Fx.testSink = null;
            throw ex;
        }
        // each bolt's own first step (bolts move with the server's tick, after this one)
        h.runAfterDelay(2, () -> {
            Fx.testSink = null;
            for (Vec3 ball : balls) {
                h.assertTrue(seen.stream().anyMatch(e -> e.kind() == FxKind.BOLT && new Vec3(e.x(), e.y(), e.z()).distanceTo(ball) < 1e-3),
                        "a bolt's first step starts at its ball " + ball);
            }
            h.succeed();
        });
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
