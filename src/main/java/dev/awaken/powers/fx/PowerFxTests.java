package dev.awaken.powers.fx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Fx;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.Powers;
import dev.awaken.powers.Running;
import dev.awaken.powers.net.FxPayloads;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** GameTests for power FX: coverage of the catalog, the wire format, the caps and the effect settings. */
final class PowerFxTests {
    private PowerFxTests() {}

    static void register() {
        AwakenTests.register("power_fx_every_power_has_fx", 20, h -> {
            List<String> missing = new ArrayList<>();
            for (PowerType t : PowerRegistry.all()) {
                PowerFxDefs.Def d = PowerFxDefs.get(t.path);
                if (d == null) {
                    missing.add(t.path);
                    continue;
                }
                h.assertTrue(d.release() != null && d.style() != null, t.path + " needs a release recipe and a style");
                if (!t.isPassive()) h.assertTrue(d.anim() != CastAnim.NONE, t.path + " needs a cast animation");
                if (t.slot == PowerType.Slot.ULTIMATE) h.assertTrue(d.ultimate(), t.path + " is an ultimate and needs the ultimate treatment");
                if (t.mode == PowerType.Mode.CHANNEL) h.assertTrue(d.windAnim() != null && d.windAnim().held, t.path + " channels and needs a held wind-up pose");
            }
            h.assertTrue(missing.isEmpty(), "powers without an FX definition: " + missing);
            for (String id : PowerFxDefs.ALL.keySet()) h.assertTrue(PowerRegistry.get(id) != null, "FX definition for an unknown power: " + id);
            h.assertTrue(PowerFxDefs.ALL.size() == PowerRegistry.all().size(), "one FX definition per power");
            h.succeed();
        });

        AwakenTests.register("power_fx_payload_round_trip", 20, h -> {
            FxEvent e = FxEvent.of(FxKind.SLAM, FxStyle.KINETIC, new Vec3(1234.5, -61.25, -98765.125)).dir(new Vec3(0.25, -1.5, 3))
                    .colour(0xFF123456).scale(4.5).aux(-2.5).seed(-123456789).ticks(77).flag(FxEvent.ULT | FxEvent.FOLLOW).variant(2)
                    .anim(CastAnim.FLURRY).build();
            e = new FxEvent(e.kind(), e.style(), 4242, -1, e.x(), e.y(), e.z(), e.dx(), e.dy(), e.dz(), e.colour(), e.scale(), e.aux(), e.seed(),
                    e.ticks(), e.flags());
            ByteBuf buf = Unpooled.buffer();
            FxPayloads.Fx.STREAM_CODEC.encode(buf, new FxPayloads.Fx(e));
            int size = buf.readableBytes();
            FxEvent back = FxPayloads.Fx.STREAM_CODEC.decode(buf).e();
            h.assertTrue(back.equals(e), "an FX event survives the wire: " + e + " vs " + back);
            h.assertTrue(back.anim() == CastAnim.FLURRY && back.variant() == 2 && back.has(FxEvent.ULT), "flags, variant and animation survive");
            h.assertTrue(size <= 64, "an FX event is compact (<= 64 bytes), was " + size);
            for (FxKind k : FxKind.values()) {
                ByteBuf b = Unpooled.buffer();
                FxEvent ev = FxEvent.of(k, FxStyle.values()[k.ordinal() % FxStyle.values().length], Vec3.ZERO).build();
                FxPayloads.Fx.STREAM_CODEC.encode(b, new FxPayloads.Fx(ev));
                h.assertTrue(FxPayloads.Fx.STREAM_CODEC.decode(b).e().equals(ev), k + " round trips");
            }
            h.succeed();
        });

        AwakenTests.register("power_fx_pool_caps", 20, h -> {
            FxPool pool = new FxPool(FxBudget.spriteCap(FxBudget.Level.FULL));
            for (int i = 0; i < 10_000; i++) pool.add(i, 0, 0, 0, 0, 0, 20 + i % 60, 1, 1, -1, -1, 0, FxPool.ADD);
            h.assertTrue(pool.live() == FxBudget.spriteCap(FxBudget.Level.FULL), "the pool fills to its cap and no further: " + pool.live());
            // a sprite added to a full pool replaces a spent one, so the new impact is visible
            for (int i = 0; i < 30; i++) pool.tick();
            int slot = pool.add(1, 2, 3, 0, 0, 0, 40, 1, 1, -1, -1, 0, FxPool.ADD);
            h.assertTrue(pool.age[slot] == 0 && pool.x[slot] == 1, "a new sprite gets a slot when the pool is full");
            pool.setCap(FxBudget.spriteCap(FxBudget.Level.REDUCED));
            h.assertTrue(pool.live() <= FxBudget.spriteCap(FxBudget.Level.REDUCED), "lowering the setting trims the pool");
            for (int i = 0; i < 200; i++) pool.tick();
            h.assertTrue(pool.live() == 0, "every sprite dies on time, " + pool.live() + " left");
            h.assertTrue(FxBudget.count(100_000, 1f) == FxBudget.PER_EFFECT, "one effect never spawns more than the per-effect cap");
            h.succeed();
        });

        AwakenTests.register("power_fx_settings_cut_counts", 20, h -> {
            int[] bases = {1, 4, 12, 20, 60, 200};
            for (int base : bases) {
                int full = FxBudget.count(base, FxBudget.factor(FxBudget.Level.FULL, 0, 8));
                int reduced = FxBudget.count(base, FxBudget.factor(FxBudget.Level.REDUCED, 0, 8));
                int minimal = FxBudget.count(base, FxBudget.factor(FxBudget.Level.MINIMAL, 0, 8));
                h.assertTrue(full >= reduced && reduced >= minimal, "base " + base + ": " + full + " >= " + reduced + " >= " + minimal);
                if (base >= 12) h.assertTrue(full > reduced && reduced > minimal, "base " + base + " is visibly cut at each level");
                int decreased = FxBudget.count(base, FxBudget.factor(FxBudget.Level.FULL, 1, 8));
                int vanillaMin = FxBudget.count(base, FxBudget.factor(FxBudget.Level.FULL, 2, 8));
                h.assertTrue(full >= decreased && decreased >= vanillaMin, "the vanilla particle setting thins effects too");
                h.assertTrue(FxBudget.count(base, FxBudget.factor(FxBudget.Level.FULL, 0, 60)) <= full, "far effects are thinner");
            }
            for (FxBudget.Level a : new FxBudget.Level[]{FxBudget.Level.FULL, FxBudget.Level.REDUCED}) {
                FxBudget.Level b = FxBudget.Level.values()[a.ordinal() + 1];
                h.assertTrue(FxBudget.spriteCap(a) > FxBudget.spriteCap(b) && FxBudget.shapeCap(a) > FxBudget.shapeCap(b)
                        && FxBudget.debrisCap(a) > FxBudget.debrisCap(b) && FxBudget.afterimageCap(a) > FxBudget.afterimageCap(b), a + " caps exceed " + b);
            }
            h.assertTrue(FxBudget.screen(FxBudget.Level.MINIMAL) == 0 && FxBudget.afterimageCap(FxBudget.Level.MINIMAL) == 0,
                    "minimal has no screen flashes and no afterimages");
            FxRand a = new FxRand().seed(99), b = new FxRand().seed(99);
            for (int i = 0; i < 50; i++) h.assertTrue(a.f() == b.f(), "the same seed builds the same effect");
            h.succeed();
        });

        AwakenTests.register("power_fx_assets", 20, h -> {
            for (PowerFxParticles.Sprite s : PowerFxParticles.SPRITES) {
                h.assertTrue(PowerFxTests.class.getResource("/assets/awaken/particles/" + s.id() + ".json") != null, "particle JSON for " + s.id());
                for (int f = 0; f < s.frames(); f++) {
                    String tex = "/assets/awaken/textures/particle/" + s.id() + "_" + f + ".png";
                    h.assertTrue(PowerFxTests.class.getResource(tex) != null, "missing " + tex);
                }
            }
            for (int i = 0; i < 4; i++) {
                h.assertTrue(PowerFxTests.class.getResource("/assets/awaken/post_effect/power_fx_impact_" + i + ".json") != null, "impact frame " + i);
            }
            h.assertTrue(PowerFxTests.class.getResource("/assets/awaken/shaders/post/power_fx_impact.fsh") != null, "impact shader");
            h.succeed();
        });

        AwakenTests.register("power_fx_cast_clips", 20, PowerFxTests::castClips);
        AwakenTests.registerIsolated("power_fx_every_cast_emits", 200, PowerFxTests::everyCastEmits);
        AwakenTests.registerIsolated("power_fx_reach_matches_hits", 400, PowerFxTests::reachMatchesHits);
        PowerCastTests.register();
    }

    private static final Set<String> CLIP_BONES = Set.of("body", "torso", "head", "right_arm", "left_arm", "right_leg", "left_leg");

    /**
     * The optional full-body clips (Player Animation Library): every clip the table can name is in the generated file,
     * with its Bendable Cuboids twin, with only player bones, one-shot clips ease out (endTick) and wind-ups loop, elbows
     * fold forward and knees back within reach; and every power's family is read back from its own events, so each power
     * plays its family's clip.
     */
    private static void castClips(GameTestHelper h) {
        JsonObject anims;
        try (InputStream in = PowerFxTests.class.getResourceAsStream("/" + CastClips.FILE)) {
            h.assertTrue(in != null, "missing " + CastClips.FILE + " (python3 tools/art/specs/power_casts.py build)");
            anims = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        List<String> names = new ArrayList<>();
        for (String name : CastClips.all()) {
            names.add(name);
            names.add(name + "_bent"); // the Bendable Cuboids twin
        }
        for (String name : names) {
            h.assertTrue(anims.has(name), "clip " + name + " is missing from " + CastClips.FILE);
            JsonObject a = anims.getAsJsonObject(name);
            JsonObject bones = a.getAsJsonObject("bones");
            for (String bone : bones.keySet()) {
                h.assertTrue(CLIP_BONES.contains(bone), name + " moves an unknown bone " + bone);
                JsonObject b = bones.getAsJsonObject(bone);
                h.assertTrue(!b.has("bend") || name.endsWith("_bent") && bone.matches("(right|left)_(arm|leg)"), name + ": only the bent twin bends, and only limbs (" + bone + ")");
                if (b.has("bend")) {
                    for (var key : b.getAsJsonObject("bend").entrySet()) {
                        float v = key.getValue().getAsJsonObject().getAsJsonArray("vector").get(0).getAsFloat();
                        h.assertTrue(bone.endsWith("arm") ? v <= 0 && v >= -150 : v >= 0 && v <= 150, name + " " + bone + " bends the wrong way or too far: " + v);
                    }
                }
            }
            boolean loops = a.has("loop") && a.get("loop").getAsBoolean();
            h.assertTrue(loops == name.replace("_bent", "").matches("(hold_[a-z]+|slam_wind|hover)"), name + (loops ? " loops but is not a wind-up" : " should loop"));
            if (!loops) {
                JsonElement end = a.has("player_animation_library") ? a.getAsJsonObject("player_animation_library").get("endTick") : null;
                h.assertTrue(end != null && end.getAsFloat() < a.get("animation_length").getAsFloat() * 20, name + " eases back to the vanilla pose (endTick)");
            }
        }
        for (PowerType.Family f : PowerType.Family.values()) {
            for (CastAnim k : CastAnim.values()) {
                for (boolean after : new boolean[]{false, true}) {
                    String c = CastClips.clip(f, k, after);
                    h.assertTrue(k == CastAnim.NONE ? c == null : c != null && anims.has(c), f + " " + k + " names a clip that exists: " + c);
                }
                String letGo = CastClips.letGo(f, k);
                h.assertTrue(k.held == (letGo != null) && (letGo == null || anims.has(letGo)), f + " " + k + " lets go cleanly: " + letGo);
            }
        }
        List<String> wrong = new ArrayList<>();
        for (PowerFxDefs.Def d : PowerFxDefs.ALL.values()) {
            PowerType t = PowerRegistry.get(d.power());
            if (t == null) continue;
            for (CastAnim a : new CastAnim[]{d.anim(), d.windAnim()}) {
                if (a == null || a == CastAnim.NONE) continue;
                PowerType.Family got = CastClips.family(d.style(), a, d.ultimate());
                if (got != t.family) wrong.add(t.path + " " + a + " -> " + got);
            }
        }
        h.assertTrue(wrong.isEmpty(), "powers whose events read as another family: " + wrong);
        h.succeed();
    }

    /** Area and wave powers whose hit volume the effect draws; probes run along +X (rings) or +Z (cones, the aim). */
    private record Probe(String power, int ticks, boolean forward) {}

    private static final List<Probe> PROBES = List.of(
            new Probe("ground_slam", 0, false), new Probe("nova", 0, false), new Probe("frost_nova", 7, false),
            new Probe("thunderclap", 8, false), new Probe("gravity_flip", 7, false), new Probe("overload", 22, false),
            new Probe("brace", 68, false), new Probe("shockwave", 17, true), new Probe("spire", 7, false)); // wind-ups wait their profile's ticks; spire last: it builds a pillar

    /**
     * Every area power draws exactly the volume it hits: mobs stand in a row at 1..13 blocks, the power is cast, and
     * the reach its effect reports must sit between the farthest mob it hurt and the nearest it missed.
     */
    private static void reachMatchesHits(GameTestHelper h) {
        for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(0, 1, 0)));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f);
        sp.setGameMode(GameType.SURVIVAL);
        List<FxEvent> seen = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        Fx.testSink = seen::add;
        try {
            for (Probe probe : PROBES) {
                PowerType t = PowerRegistry.get(probe.power());
                h.assertTrue(t != null, "probe power " + probe.power() + " exists");
                Powers.learn(sp, t.id, 0);
                Powers.resetCooldowns(sp);
                Powers.fillEnergy(sp);
                sp.snapTo(at.x, at.y, at.z, 0f, 0f);
                sp.setDeltaMovement(Vec3.ZERO);
                List<Zombie> row = new ArrayList<>();
                for (int d = 1; d <= 13; d++) {
                    Zombie z = h.spawn(EntityTypes.ZOMBIE, probe.forward() ? new BlockPos(0, 1, d) : new BlockPos(d, 1, 0));
                    z.setNoAi(true);
                    row.add(z);
                }
                seen.clear();
                PowerType.Result r = Casting.cast(sp, t, 0, false, 0f, 0f);
                h.assertTrue(r.ok(), probe.power() + " casts for the reach probe: " + r.reason());
                Fx.testTick(sp, probe.ticks());
                FxEvent reach = null;
                for (FxEvent e : seen) {
                    if (e.has(FxEvent.REACH)) {
                        reach = e;
                        break;
                    }
                }
                if (reach == null) {
                    wrong.add(probe.power() + ": no effect reports its reach");
                } else {
                    double farthestHit = 0, nearestMiss = Double.MAX_VALUE;
                    for (int d = 1; d <= 13; d++) {
                        Zombie z = row.get(d - 1);
                        boolean hit = !z.isAlive() || z.getHealth() < z.getMaxHealth()
                                || z.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION);
                        if (hit) farthestHit = Math.max(farthestHit, d);
                        else nearestMiss = Math.min(nearestMiss, d);
                    }
                    double drawn = reach.scale();
                    // probes stand on whole blocks and bodies are 0.6 wide: allow a block of slack either way
                    if (farthestHit > drawn + 1.0 || nearestMiss < drawn - 1.0) {
                        wrong.add(probe.power() + ": drawn " + drawn + " but hit up to " + farthestHit + ", missed from " + nearestMiss);
                    }
                }
                Casting.endAll(sp, Running.Why.CANCELLED);
                for (Zombie z : row) z.discard();
                sp.removeAllEffects();
            }
        } finally {
            Fx.testSink = null;
        }
        h.assertTrue(wrong.isEmpty(), "effects that don't match their hits: " + wrong);
        h.succeed();
    }

    /** Casts every active power at a zombie and checks the cast itself sent at least one designed effect. */
    private static void everyCastEmits(GameTestHelper h) {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(-3, 1, 0)));
        sp.snapTo(at.x, at.y, at.z, 0f, 0f);
        sp.setGameMode(GameType.SURVIVAL);
        List<FxEvent> seen = new ArrayList<>();
        Set<String> silent = new HashSet<>();
        Fx.testSink = seen::add;
        try {
            for (PowerType t : PowerRegistry.all()) {
                if (t.isPassive()) continue;
                Powers.learn(sp, t.id, 0);
                Powers.resetCooldowns(sp);
                Powers.fillEnergy(sp);
                Zombie z = h.spawn(EntityTypes.ZOMBIE, new BlockPos(0, 1, 0));
                z.setNoAi(true);
                Vec3 to = z.getEyePosition().subtract(sp.getEyePosition());
                float yRot = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                seen.clear();
                PowerType.Result r = Casting.cast(sp, t, 0, false, yRot, 0f);
                if (r.ok() && seen.isEmpty()) silent.add(t.path);
                if (r.ok()) {
                    for (FxEvent e : seen) {
                        h.assertTrue(e.caster() < 0 || e.caster() == sp.getId() || h.getLevel().getEntity(e.caster()) != null,
                                t.path + " names a caster that exists");
                    }
                }
                Casting.endAll(sp, Running.Why.CANCELLED);
                z.discard();
                for (var e : h.getLevel().getEntitiesOfClass(dev.awaken.powers.content.PowerSummon.class, sp.getBoundingBox().inflate(48),
                        e -> e.getOwner() == sp)) e.discard();
            }
        } finally {
            Fx.testSink = null;
        }
        h.assertTrue(silent.isEmpty(), "these powers cast without a designed effect: " + silent);
        h.succeed();
    }
}
