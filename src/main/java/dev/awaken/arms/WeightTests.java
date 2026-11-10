package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

import java.util.Map;

/**
 * SPEC §25 "Weight in the Hand", stages 1 to 5, one test a mechanism: the blow's direction is the baked blade's; a hit the
 * hands showed lands by their word inside the limits and never outside them; a blow always lands whole; the weight
 * layers' curves keep their rules; blows stop at walls; a sweep cuts wild grass and leaves a flower.
 */
final class WeightTests {
    private WeightTests() {}

    static void register() {
        AwakenTests.register("arms_blow_dir", 40, WeightTests::blowDir);
        AwakenTests.register("arms_claim", 60, WeightTests::claim);
        AwakenTests.register("arms_weight", 40, WeightTests::weight);
        AwakenTests.register("arms_walls", 60, WeightTests::walls);
        AwakenTests.register("arms_cut_grass", 60, WeightTests::cutGrass);
    }

    /** Every cut carries the baked blade's own direction, the side rule reads it, and the moves that threw a body against the cut don't. */
    static void blowDir(GameTestHelper h) {
        int cuts = 0;
        for (Archetypes.Loaded st : Archetypes.all()) {
            FpSwings.Style fs = FpSwings.style(st.id());
            for (Map.Entry<Archetype.Move, Archetype.MoveDef> e : st.moves().entrySet()) {
                Archetype.MoveDef def = e.getValue();
                String name = st.id() + " " + e.getKey().key;
                if (ArmsRules.thrusts(def)) {
                    h.assertTrue(!def.swing().hasBlow() && ArmsRules.side(def, false) == 0F, name + ": a thrust has no side");
                    continue;
                }
                if (!def.swing().hasBlow()) continue; // a hand-keyed pair cutting out both ways, or a move with no travel across: the row's poses stand
                cuts++;
                float x = def.swing().blow().get(0), y = def.swing().blow().get(1);
                h.assertTrue(Math.abs(Math.sqrt(x * x + y * y) - 1) < 0.02, name + ": its blow is a unit (" + x + ", " + y + ")");
                h.assertTrue(ArmsRules.blowX(def) == x && ArmsRules.blowY(def) == y, name + ": the rule reads the baked blade's way");
                float side = ArmsRules.side(def, false);
                h.assertTrue(Math.abs(x) < 0.05F || Math.signum(side) == Math.signum(x), name + ": thrown to the side the blade went");
                h.assertTrue(ArmsRules.side(def, true) == -side, name + ": mirrored for a left hand");
                h.assertTrue(FpSwings.clip(fs, e.getKey()) != null, name + ": a blow comes from baked keys");
            }
        }
        h.assertTrue(cuts >= 130, "only " + cuts + " cuts carry a blow (tools/arms/blows.py writes them after a bake)");
        // the blows that threw a body the opposite way from the blade, by name
        h.assertTrue(side("maul", Archetype.Move.STRIKE_2) < -0.3F, "the Maul's second strike travels right to left");
        h.assertTrue(side("axe", Archetype.Move.STRIKE_4) < -0.3F, "the Axe's fourth strike travels right to left");
        h.assertTrue(side("warstaff", Archetype.Move.STRIKE_4) > 0.2F, "the War-staff's fourth strike travels left to right");
        h.assertTrue(side("twinblades", Archetype.Move.STRIKE_2) < -0.2F, "the Twin Blades' second strike travels right to left");
        h.succeed();
    }

    private static float side(String style, Archetype.Move move) {
        Archetypes.Loaded st = Archetypes.get(style);
        return st == null || st.move(move) == null ? 0F : ArmsRules.side(st.move(move), false);
    }

    /** The hands' word lands a blow a little past the server's reach, once, and never past the limits, through a wall, or with the rule off. */
    static void claim(GameTestHelper h) {
        ServerPlayer p = ArmsTests.fighter(h, 1.5, 0.5, 0F);
        ArmsState s = ArmsServer.state(p);
        Archetype.MoveDef s1 = ArmsTests.longsword().move(Archetype.Move.STRIKE_1);
        double reach = s1.reach() + 0.3; // the sweep's own
        // the eye stands at z 0.5 and a zombie's box is 0.3 deep toward it: its near face is 0.8 past the reach, then 2.2
        LivingEntity near = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 0.5 + reach + 0.8 + 0.3, 200);
        try {
            strike(p, 1);
            h.assertTrue(near.getHealth() == 200, "0.8 past the reach, the server's own sweep leaves it");
            float before = near.getHealth();
            ArmsConfig.claimedHitsOverride = false;
            ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_HIT, 0F, 0F, near.getId(), Archetype.Move.STRIKE_1.ordinal()));
            h.assertTrue(near.getHealth() == before, "with the rule off the hands' word lands nothing");
            ArmsConfig.claimedHitsOverride = true;
            ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_HIT, 0F, 0F, near.getId(), Archetype.Move.STRIKE_1.ordinal()));
            h.assertTrue(near.getHealth() < before, "a hit the hands showed lands 0.8 past the server's reach");
            float once = near.getHealth();
            ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_HIT, 0F, 0F, near.getId(), Archetype.Move.STRIKE_1.ordinal()));
            ArmsTests.runActive(p);
            h.assertTrue(near.getHealth() == once, "and only once a move");

            near.discard();
            LivingEntity far = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 0.5 + reach + ArmsServer.CLAIM_REACH + 0.5 + 0.3, 200);
            strike(p, 2);
            ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_HIT, 0F, 0F, far.getId(), Archetype.Move.STRIKE_2.ordinal()));
            h.assertTrue(far.getHealth() == 200, "past the limit the word lands nothing");
            ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_HIT, 0F, 0F, far.getId(), Archetype.Move.STRIKE_3.ordinal()));
            h.assertTrue(far.getHealth() == 200 && s.move == Archetype.Move.STRIKE_2, "nor a word for a move that isn't running");
            ArmsTests.done(h, p);
        } finally {
            ArmsConfig.claimedHitsOverride = null;
        }
    }

    private static void strike(ServerPlayer p, int seq) {
        ArmsState s = ArmsServer.state(p);
        s.moveEnd = Long.MIN_VALUE / 2; // each strike on a rested chain: the test isn't about the window
        s.move = null;
        s.step = -1;
        ArmsServer.onPress(p, new ArmsPayloads.Press(seq, ArmsPayloads.PRESS_STRIKE, 0F, 0F, -1, seq == 1 ? Archetype.Move.STRIKE_1.ordinal() : Archetype.Move.STRIKE_2.ordinal()));
        ArmsServer.onPress(p, new ArmsPayloads.Press(seq, ArmsPayloads.PRESS_TAP, 0F, 0F, -1));
        ArmsTests.runActive(p);
    }

    /** The layers' curves: small, bounded, nothing at rest, and every baked move's stop is a real one. */
    static void weight(GameTestHelper h) {
        h.assertTrue(SwingWeight.shake(-1F, 0L) == 0F && SwingWeight.shake(1F, 0L) == 0F && SwingWeight.shake(Float.NaN, 0L) == 0F, "no shake outside a freeze");
        h.assertTrue(SwingWeight.shake(0F, 0L) == 1F && SwingWeight.shake(0F, SwingWeight.SHAKE_NANOS) == -1F, "hardest on the hit, turning each step");
        h.assertTrue(Math.abs(SwingWeight.shake(0.5F, 0L)) == 0.5F && Math.abs(SwingWeight.shake(0.99F, 0L)) < 0.02F, "and dying away by the freeze's end");

        float last = 0F, most = 0F;
        boolean fell = false, rose = false;
        for (float t = 0F; t <= 2.2F * SwingWeight.SETTLE_END; t += 0.05F) {
            float v = SwingWeight.settle(t, 2.2F);
            h.assertTrue(v >= 0F && v <= 1.0001F, "the settle never passes its size or swings back through (" + v + " at " + t + ")");
            if (v > last + 1e-4F) {
                h.assertTrue(!fell, "the settle comes back once: it rose again at " + t);
                rose = true;
            } else if (v < last - 1e-4F) fell = true;
            most = Math.max(most, v);
            last = v;
        }
        h.assertTrue(rose && fell && most > 0.99F && SwingWeight.settle(0F, 2.2F) == 0F && SwingWeight.settle(2.2F * SwingWeight.SETTLE_END, 2.2F) == 0F,
                "from nothing, to its size at its peak, to nothing");
        for (long n = 0; n < SwingWeight.BREATH_NANOS * 2; n += 37_000_000L) {
            h.assertTrue(Math.abs(SwingWeight.breath(n + 90L * 86_400_000_000_000L)) <= SwingWeight.BREATH + 1e-6F, "the breath stays within its 3 mm on a machine up three months");
        }
        h.assertTrue(SwingWeight.guardUp(0F, 0F) == 0F && Math.abs(SwingWeight.guardUp(SwingWeight.GUARD_RISE, 0F) - 1F) < 1e-5F, "the guard is up in the server's two ticks");
        h.assertTrue(SwingWeight.guardUp(1.5F, 0F) > 0.9F, "and nearly there by a tick and a half");
        for (float t = 0F; t <= 3F; t += 0.02F) {
            h.assertTrue(SwingWeight.guardUp(t, 0.3F) <= 1F + SwingWeight.GUARD_OVER && SwingWeight.guardUp(t, 0.3F) >= 0.3F - 1e-5F, "the raise never falls back or carries far past");
        }
        h.assertTrue(SwingWeight.guardDown(0F, 0.8F) == 0.8F && SwingWeight.guardDown(SwingWeight.GUARD_FALL, 0.8F) == 0F && SwingWeight.guardDown(1.5F, 1F) == 0.5F,
                "it lowers from where it is, over three ticks");
        for (SwingWeight.Weight w : new SwingWeight.Weight[] {SwingWeight.SNAP, SwingWeight.BLADE, SwingWeight.HEAVY}) {
            h.assertTrue(SwingWeight.drawSink(0F, w.drawTicks()) == 1F && SwingWeight.drawSink(w.drawTicks(), w.drawTicks()) == 0F, "a draw starts under the frame and ends in place");
            for (float t = 0F; t <= w.drawTicks(); t += 0.1F) {
                float sink = SwingWeight.drawSink(t, w.drawTicks());
                h.assertTrue(sink <= 1F && sink > -0.05F, "the draw only seats a hair above its place (" + sink + ")");
            }
            h.assertTrue(w.drawTicks() <= 10F && w.shake() <= 0.012F && w.settle() <= 0.04F, "the layers stay small");
        }
        h.assertTrue(SwingWeight.of("daggers") == SwingWeight.SNAP && SwingWeight.of("maul") == SwingWeight.HEAVY && SwingWeight.of("longsword") == SwingWeight.BLADE, "weighed as the swings are");

        // every baked move: a stop is after its hit and away from where the move ends (a hand at rest is never settled)
        int stops = 0, moves = 0;
        Vector3f way = new Vector3f(), axis = new Vector3f();
        for (Archetypes.Loaded st : Archetypes.all()) {
            FpSwings.Style fs = FpSwings.style(st.id());
            for (Map.Entry<Archetype.Move, Archetype.MoveDef> e : st.moves().entrySet()) {
                FpSwings.Clip clip = FpSwings.clip(fs, e.getKey());
                if (clip == null) continue;
                moves++;
                float hit = clip.lands() >= 0 ? clip.lands() : e.getValue().windup();
                float stop = SwingWeight.stop(clip.keys(), FpSwings.dt(), hit, way, axis);
                if (stop < 0) continue;
                stops++;
                h.assertTrue(stop > hit, st.id() + " " + e.getKey().key + ": its stop " + stop + " is after its hit " + hit);
                h.assertTrue(Math.abs(way.length() - 1F) < 1e-3F || Math.abs(axis.length() - 1F) < 1e-3F, st.id() + " " + e.getKey().key + ": the settle has a way to go");
            }
        }
        h.assertTrue(stops >= moves / 4, "only " + stops + " of " + moves + " baked moves have a stop to settle at");
        System.out.printf("[arms_weight] %d of %d baked moves stop away from rest after their hit%n", stops, moves);

        // the recorded sounds: a hit on flesh is Minecraft's own attack over the kit's thump, and the switch gives the kit back
        ArmsSounds.Voice[] mix = ArmsSounds.mix(ArmsSounds.HIT_FLESH.get());
        h.assertTrue(mix != null && mix.length == 2 && mix[0].sound().get() != ArmsSounds.HIT_FLESH.get() && mix[1].volume() < 0.5F, "flesh is a recording over the quiet thump");
        h.assertTrue(ArmsSounds.mix(ArmsSounds.HIT_BONE.get()) == null, "a sound that passed the audit plays as it was");
        ArmsSounds.recorded = false;
        boolean kit = ArmsSounds.mix(ArmsSounds.HIT_FLESH.get()) == null;
        ArmsSounds.recorded = true;
        h.assertTrue(kit, "switched off, every sound is the first kit's");
        h.succeed();
    }

    /** A blow doesn't land through a wall; half behind one, the body is still struck. */
    static void walls(GameTestHelper h) {
        ServerPlayer p = ArmsTests.fighter(h, 1.5, 0.5, 0F);
        LivingEntity z = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 2.8, 200);
        h.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        h.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
        h.assertTrue(!ArmsWalls.sees(p.level(), p.getEyePosition(), z.getBoundingBox()), "a wall two blocks high hides it");
        strike(p, 1);
        h.assertTrue(z.getHealth() == 200, "and no blow lands through it");
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_HIT, 0F, 0F, z.getId(), Archetype.Move.STRIKE_1.ordinal()));
        h.assertTrue(z.getHealth() == 200, "nor by the hands' word");
        h.setBlock(new BlockPos(1, 2, 1), Blocks.AIR);
        h.assertTrue(ArmsWalls.sees(p.level(), p.getEyePosition(), z.getBoundingBox()), "over a wall one block high its top shows");
        strike(p, 2);
        h.assertTrue(z.getHealth() < 200, "and the blow lands");
        h.assertTrue(ArmsWalls.glance(p.level(), p.getEyePosition(), p.getYRot(), 35F, 3.0, false) != null, "a strike aimed down at the wall meets it");
        h.assertTrue(ArmsWalls.kind(Blocks.STONE.defaultBlockState()) == ArmsWalls.GLANCE_HARD && ArmsWalls.kind(Blocks.OAK_PLANKS.defaultBlockState()) == ArmsWalls.GLANCE_WOOD
                && ArmsWalls.kind(Blocks.DIRT.defaultBlockState()) == ArmsWalls.GLANCE_EARTH && ArmsWalls.kind(Blocks.OAK_LEAVES.defaultBlockState()) == ArmsWalls.GLANCE_SOFT,
                "stone rings, wood knocks, earth thuds, leaves rustle");
        h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
        ArmsTests.done(h, p);
    }

    /** A sweep cuts the wild grass in its arc and leaves a flower. */
    static void cutGrass(GameTestHelper h) {
        ServerPlayer p = ArmsTests.fighter(h, 1.5, 0.5, 0F);
        for (int x = -3; x <= 6; x++) for (int z = -3; z <= 6; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
        BlockPos grass = new BlockPos(1, 1, 2), fern = new BlockPos(2, 1, 2), poppy = new BlockPos(0, 1, 2), behind = new BlockPos(1, 1, -2);
        h.setBlock(grass, Blocks.SHORT_GRASS);
        h.setBlock(fern, Blocks.FERN);
        h.setBlock(poppy, Blocks.POPPY);
        h.setBlock(behind, Blocks.SHORT_GRASS);
        strike(p, 1);
        h.assertTrue(h.getBlockState(grass).isAir() && h.getBlockState(fern).isAir(), "the grass and the fern in the arc are cut");
        h.assertTrue(h.getBlockState(poppy).is(Blocks.POPPY), "a flower is left");
        h.assertTrue(h.getBlockState(behind).is(Blocks.SHORT_GRASS), "and the grass behind the fighter");
        for (int x = -3; x <= 6; x++) for (int z = -3; z <= 6; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
        ArmsTests.done(h, p);
    }
}
