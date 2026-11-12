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
        AwakenTests.register("power_hold_pose_has_an_owner", 20, PowerCastTests::holdPose);
        AwakenTests.registerIsolated("power_held_casts_say_they_ended", 200, PowerCastTests::heldCastsEnd);
        AwakenTests.registerIsolated("power_every_hold_ends", 300, PowerCastTests::everyHoldEnds);
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

    /** No order of casting, swapping and letting go leaves the hands in a hold pose (Euan 9 Nov; SPEC §32). */
    private static void holdPose(net.minecraft.gametest.framework.GameTestHelper h) {
        CastProfiles.Profile shot = CastProfiles.byPower("charged_shot"), beam = CastProfiles.byPower("beam"), lance = CastProfiles.byPower("prism_lance");
        h.assertTrue(shot != null && beam != null && lance != null && shot.hold1p() != null, "the held casts exist");
        Set<String> playing = new HashSet<>();
        HeldHands.Clips clips = new HeldHands.Clips() {
            @Override
            public boolean play(String clip) {
                return playing.add(clip);
            }

            @Override
            public void stop(String clip) {
                playing.remove(clip);
            }
        };
        // his fight: Charged Shot held to its loop, something else cast over it, then the key let go
        HeldHands hands = new HeldHands(3, clips);
        hands.press(0, shot);
        hands.reached(hands.byBegin(shot.begin1p()));
        hands.press(1, beam);
        h.assertTrue(playing.contains(shot.hold1p()), "still held: the hands stay up");
        hands.release(0);
        h.assertTrue(playing.isEmpty() && hands.loop() == null, "let go with another cast made since: still up: " + playing);
        hands.release(1);

        CastProfiles.Profile[] casts = {shot, beam, lance};
        java.util.Random r = new java.util.Random(9);
        for (int run = 0; run < 4000; run++) {
            playing.clear();
            HeldHands in = new HeldHands(3, clips);
            boolean[] down = new boolean[3], serverOnly = new boolean[3];
            for (int step = 0; step < 40; step++) {
                int s = r.nextInt(3);
                switch (r.nextInt(6)) {
                    case 0 -> {
                        if (!down[s]) {
                            down[s] = true;
                            in.press(s, casts[s]);
                        }
                    }
                    case 1 -> {
                        down[s] = false;
                        in.release(s);
                    }
                    case 2 -> {
                        CastProfiles.Profile k = in.byBegin(casts[s].begin1p());
                        if (k != null) in.reached(k);
                    }
                    case 3 -> {
                        in.ended(casts[s]);
                        down[s] = false; // the key may still be down, but it holds nothing now
                        serverOnly[s] = false;
                    }
                    case 4 -> {
                        serverOnly[s] = true;
                        in.reached(casts[s]);
                    }
                    default -> in.tick(c -> java.util.Arrays.stream(casts).anyMatch(x -> x.id() == c.id() && serverOnly[java.util.Arrays.asList(casts).indexOf(x)]));
                }
                h.assertTrue(playing.size() <= 1, "two hold poses at once: " + playing);
            }
            for (int s = 0; s < 3; s++) {
                in.release(s);
                in.ended(casts[s]);
            }
            in.tick(c -> false);
            h.assertTrue(playing.isEmpty() && in.loop() == null, "run " + run + " left the hands up: " + playing);
        }
        // a key down on nothing, and one that's forgotten, can't hold a pose either
        HeldHands lost = new HeldHands(3, clips);
        playing.clear();
        lost.press(0, shot);
        lost.reached(shot);
        lost.reset();
        h.assertTrue(playing.isEmpty(), "forgotten keys left the hands up");

        // only a held cast's own loop may loop at all: any other looping clip would have no owner
        StringBuilder errors = new StringBuilder();
        Set<String> owned = new HashSet<>();
        for (CastProfiles.Profile p : CastProfiles.allLevels()) if (p.hold1p() != null) owned.add(p.hold1p());
        List<String> orphans = new ArrayList<>();
        for (Map.Entry<String, FpClipSet.Clip> c : FpClipSet.load(errors).entrySet()) if (c.getValue().loop && !owned.contains(c.getKey())) orphans.add(c.getKey());
        h.assertTrue(orphans.isEmpty(), "these clips loop with no cast to end them: " + orphans);
        h.succeed();
    }

    /** Every cast that holds a pose tells the caster's client when it is over, on the server's own path (SPEC §32). */
    private static void heldCastsEnd(net.minecraft.gametest.framework.GameTestHelper h) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(0, 1, 0)));
        sp.setGameMode(GameType.SURVIVAL);
        List<FxEvent> seen = new ArrayList<>();
        List<String> silent = new ArrayList<>(), tried = new ArrayList<>();
        Fx.testSink = seen::add;
        try {
            for (CastProfiles.Profile prof : CastProfiles.all()) {
                if (prof.hold1p() == null) continue;
                PowerType t = PowerRegistry.get(prof.power());
                if (t == null) continue;
                Powers.learn(sp, t.id, 0);
                sp.snapTo(at.x, at.y, at.z, 0f, 0f);
                Powers.resetCooldowns(sp);
                Powers.fillEnergy(sp);
                Casting.endAll(sp, Running.Why.CANCELLED);
                seen.clear();
                if (!Casting.cast(sp, t, 0, false, 0f, 0f).ok()) continue; // nothing to aim at: telekinesis
                tried.add(prof.power());
                Fx.testTick(sp, Math.max(1, prof.windup()) + 2);
                Casting.endAll(sp, Running.Why.RELEASED);
                if (seen.stream().noneMatch(e -> e.kind() == FxKind.CHARGE_END && e.profile() == prof.id() && e.beat() == CastProfiles.END)) silent.add(prof.power());
            }
        } finally {
            Fx.testSink = null;
        }
        h.assertTrue(tried.containsAll(List.of("charged_shot", "railgun", "beam")), "the held casts were tried: " + tried);
        h.assertTrue(silent.isEmpty(), "these held casts never tell the caster they ended: " + silent);
        h.succeed();
    }

    /**
     * One client's view of one caster's held pose, by the client's own rules: a profile's beat starts its pose when
     * {@link HeldHands#startsPose} says so (FxClient.onProfile), a family pose replaces the one before (CastAnims), and the
     * end of a charge lets the hold go (FxRecipes, HeldHands.ended). Every beat is read as late as it can come: the client's
     * pose of that cast has already gone (its key let go, or another cast was made since).
     */
    private static final class Watch {
        final boolean own;
        boolean held;

        Watch(boolean own) {
            this.own = own;
        }

        void see(FxEvent e) {
            if (e.kind() == FxKind.CHARGE_END && Math.round(e.aux()) == 0) {
                held = false;
                return;
            }
            CastProfiles.Profile p = CastProfiles.byId(e.profile());
            if (p != null) {
                if (e.beat() != CastProfiles.END && HeldHands.startsPose(e.beat(), p.channel(), own, e.keyed(), -1f)) held = p.channel();
                return;
            }
            if (e.anim() != CastAnim.NONE) held = e.anim().held;
        }
    }

    /**
     * No way a held cast ends leaves a hold up on any client (Euan 12 Nov: "Sometimes my fist stays blocked up after using
     * haymaker"; SPEC §32, "A reply never starts a hold"). Every power that shows a held pose is cast from a key press, held,
     * and ended each way a cast ends; the server's events then reach the caster's own client and a watcher, each as late as
     * it can. Neither may be left holding. A cast nobody's key made (a command) still shows everywhere.
     */
    private static void everyHoldEnds(net.minecraft.gametest.framework.GameTestHelper h) {
        h.assertTrue(HeldHands.startsPose(CastProfiles.BEGIN, true, true, false, -1f), "a cast the server began alone (a command) shows on its caster");
        h.assertTrue(HeldHands.startsPose(CastProfiles.BEGIN, true, false, true, -1f), "a watcher sees every held cast begin");
        h.assertTrue(HeldHands.startsPose(CastProfiles.RELEASE, false, false, false, -1f), "a watcher sees a one-shot cast that sent only its release");
        h.assertTrue(!HeldHands.startsPose(CastProfiles.RELEASE, false, false, false, 2f), "a cast already showing doesn't start again");

        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(0, 1, 0)));
        sp.setGameMode(GameType.SURVIVAL);
        // every power that shows a held pose: the held casts' profiles, the family poses that hold, and the comet's dive (set by hand)
        Set<String> powers = new java.util.LinkedHashSet<>();
        for (CastProfiles.Profile p : CastProfiles.all()) if (p.channel()) powers.add(p.power());
        for (PowerType t : PowerRegistry.all()) {
            PowerFxDefs.Def d = PowerFxDefs.get(t.path);
            if (d != null && (d.anim().held || d.windAnim() != null && d.windAnim().held)) powers.add(t.path);
        }
        powers.add("meteor_dive");
        List<FxEvent> seen = new ArrayList<>();
        List<String> stuck = new ArrayList<>(), unkeyed = new ArrayList<>();
        Set<String> tried = new java.util.LinkedHashSet<>();
        Fx.testSink = seen::add;
        try {
            for (String power : powers) {
                PowerType t = PowerRegistry.get(power);
                if (t == null) continue;
                Powers.learn(sp, t.id, 0);
                for (Running.Why why : new Running.Why[]{Running.Why.RELEASED, Running.Why.CANCELLED, Running.Why.EXPIRED}) {
                    Casting.endAll(sp, Running.Why.CANCELLED);
                    // Ground Slam drives down from above the floor; everything else starts on it
                    boolean dive = power.equals("ground_slam");
                    sp.snapTo(at.x, at.y + (dive ? 4 : 0), at.z, 0f, 0f);
                    sp.setDeltaMovement(Vec3.ZERO);
                    sp.setOnGround(!dive);
                    Powers.resetCooldowns(sp);
                    Powers.fillEnergy(sp);
                    seen.clear();
                    // the hands' path: a key press that claimed a way
                    if (!Casting.cast(sp, t, 0, false, 0f, 0f, 0f, 0f, Ways.STAND, 0).ok()) continue; // nothing to aim at: telekinesis
                    tried.add(power);
                    Fx.testTick(sp, 17); // held, short of a full charge; the comet dives but hasn't landed
                    Casting.endAll(sp, why);
                    Watch own = new Watch(true), watcher = new Watch(false);
                    boolean keyed = false;
                    for (FxEvent e : seen) {
                        if (e.caster() != sp.getId()) continue;
                        own.see(e);
                        watcher.see(e);
                        keyed |= e.profile() > 0 && e.keyed();
                    }
                    if (own.held || watcher.held) stuck.add(power + " " + why + (own.held ? ", its caster" : "") + (watcher.held ? ", a watcher" : ""));
                    if (CastProfiles.byPower(power) != null && !keyed) unkeyed.add(power);
                }
            }
            // a command's cast is the server's alone: its beats are not keyed
            PowerType hay = PowerRegistry.get("haymaker");
            Casting.endAll(sp, Running.Why.CANCELLED);
            sp.snapTo(at.x, at.y, at.z, 0f, 0f);
            sp.setOnGround(true);
            Powers.resetCooldowns(sp);
            Powers.fillEnergy(sp);
            seen.clear();
            h.assertTrue(hay != null && Casting.cast(sp, hay, 0, false, 0f, 0f).ok(), "a command casts the Haymaker");
            h.assertTrue(seen.stream().anyMatch(e -> e.profile() > 0 && e.beat() == CastProfiles.BEGIN && !e.keyed()), "a command's cast begins unkeyed");
        } finally {
            Fx.testSink = null;
            Casting.endAll(sp, Running.Why.CANCELLED);
        }
        h.assertTrue(tried.containsAll(List.of("haymaker", "charged_shot", "railgun", "beam", "ground_slam", "meteor_dive")), "the held casts were tried: " + tried);
        h.assertTrue(unkeyed.isEmpty(), "a key press made these casts, but their beats don't say so: " + unkeyed);
        h.assertTrue(stuck.isEmpty(), "these ways of ending leave a hold up: " + stuck);
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
