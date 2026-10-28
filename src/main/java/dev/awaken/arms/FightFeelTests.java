package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Fight Feel (SPEC §25 "Fight Feel"): one test per mechanism; the feel on screen is Euan's to judge in play. */
final class FightFeelTests {
    private FightFeelTests() {}

    static void register() {
        AwakenTests.register("arms_knock_dir", 40, FightFeelTests::knockDir);
        AwakenTests.register("arms_kill", 40, FightFeelTests::kill);
        AwakenTests.register("arms_recoil_spring", 20, FightFeelTests::recoilSpring);
    }

    /**
     * A blow pushes the way the blade went: strike 1 (right to left) throws a zombie to the fighter's left, at least 30%
     * sideways; the thrust pushes it straight back; the overhead chop pins it (no lift); the rising heavy lifts it.
     */
    static void knockDir(GameTestHelper h) {
        Blow b = new Blow(5, true, false, true, 3, -1, 200, -5);
        h.assertTrue(Blow.unpack(b.pack()).equals(b) && Blow.moveOf(b.pack()) == 5 && b.pack() > 0, "a blow packs and unpacks whole (" + Blow.unpack(b.pack()) + ")");
        ServerPlayer p = ArmsTests.fighter(h, 1.5, 0.5, 0F); // facing +z: the fighter's left is +x
        Archetypes.Loaded ls = ArmsTests.longsword();
        Vec3 cut = struck(h, p, ls, Archetype.Move.STRIKE_1, 1);
        double flat = cut.horizontalDistance();
        h.assertTrue(flat > 0.2 && cut.x > 0 && cut.x / flat >= 0.3, "a right-to-left cut throws it to the left, 30% sideways at least (" + cut + ")");
        Vec3 thrust = struck(h, p, ls, Archetype.Move.STRIKE_4, 2);
        h.assertTrue(thrust.z > 0.3 && Math.abs(thrust.x) < 0.05 * thrust.z, "a thrust pushes it straight back (" + thrust + ")");
        Vec3 chop = struck(h, p, ls, Archetype.Move.STRIKE_3, 3);
        h.assertTrue(chop.z > 0.1 && chop.y < 0.05, "an overhead chop pins it: no lift (" + chop + ")");
        Vec3 rise = struck(h, p, ls, Archetype.Move.HEAVY, 4);
        h.assertTrue(rise.y > cut.y + 0.1, "the rising heavy lifts it more than a cut (" + rise.y + " against " + cut.y + ")");
        // a Lord keeps vanilla's push: straight along the look, whatever the cut
        Vec3 lord = struck(h, p, ls, Archetype.Move.STRIKE_1, 5, 200);
        h.assertTrue(Math.abs(lord.x) < 0.02, "a Lord is pushed as vanilla pushes it (" + lord + ")");
        ArmsTests.done(h, p);
    }

    private static Vec3 struck(GameTestHelper h, ServerPlayer p, Archetypes.Loaded ls, Archetype.Move move, int seq) {
        return struck(h, p, ls, move, seq, 40);
    }

    /** A fresh zombie straight ahead, on the ground and still, struck once by {@code move}: its motion after. */
    private static Vec3 struck(GameTestHelper h, ServerPlayer p, Archetypes.Loaded ls, Archetype.Move move, int seq, float health) {
        LivingEntity z = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 2.0, health);
        z.setOnGround(true);
        z.setDeltaMovement(Vec3.ZERO);
        ArmsState s = ArmsServer.state(p);
        s.style = ls;
        s.move = move;
        s.moveSeq = seq;
        ArmsServer.hit(p, s, ls.move(move), z, ArmsServer.now(p));
        Vec3 v = z.getDeltaMovement();
        z.discard();
        return v;
    }

    /**
     * A kill says so, and the last of a fight says that too: a hit that doesn't kill sends neither; a sweep through three
     * hunting wolves kills each (KILL), counts its bodies (CLEAVE 1, 2, 3), grows its one freeze by 12 ms a body, and
     * only the third, with nobody left hunting, is LAST.
     */
    static void kill(GameTestHelper h) {
        ServerPlayer p = survivalFighter(h); // a creature never hunts a creative player, as the mock is
        Archetypes.Loaded ls = ArmsTests.longsword();
        List<int[]> sent = new ArrayList<>();
        ArmsServer.blowSeen = (t, ms, blow) -> sent.add(new int[] {t, ms, blow});
        try {
            LivingEntity tough = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 2.0, 40);
            ArmsState s = ArmsServer.state(p);
            s.style = ls;
            s.move = Archetype.Move.STRIKE_1;
            s.moveSeq = 1;
            ArmsServer.hit(p, s, ls.move(Archetype.Move.STRIKE_1), tough, ArmsServer.now(p));
            h.assertTrue(sent.size() == 1, "the hit is sent (" + sent.size() + ")");
            Blow hit = Blow.unpack(sent.get(0)[2]);
            h.assertTrue(!hit.kill() && !hit.last() && hit.cleave() == 1, "a hit that doesn't kill is neither KILL nor LAST (" + hit + ")");
            tough.discard();
            s.clearMove();
            s.lastHitstopAt = Long.MIN_VALUE / 2;
            List<Mob> three = List.of(hunter(h, p, 1.5, 2.0), hunter(h, p, 0.7, 1.9), hunter(h, p, 2.3, 1.9));
            h.assertTrue(three.get(1).getTarget() == p, "they hunt the fighter (" + three.get(1).getTargetUnchecked() + ")");
            ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_STRIKE, 0F, 0F, -1));
            h.assertTrue(s.move == Archetype.Move.STRIKE_1, "a tap starts strike 1 (" + s.move + ")");
            ArmsTests.runActive(p);
            h.assertTrue(sent.size() == 4, "the sweep strikes all three (" + (sent.size() - 1) + ")");
            int base = ls.move(Archetype.Move.STRIKE_1).hitstop();
            for (int i = 1; i <= 3; i++) {
                Blow b = Blow.unpack(sent.get(i)[2]);
                int ms = sent.get(i)[1];
                h.assertTrue(b.kill() && b.cleave() == i, "body " + i + " is killed and counted (" + b + ")");
                h.assertTrue(b.last() == (i == 3), "only the third is the last foe (" + i + ": " + b + ")");
                h.assertTrue(ms == base + ArmsRules.CLEAVE_HITSTOP * (i - 1), "the one freeze grows by 12 ms a body (" + i + ": " + ms + " ms)");
            }
            h.assertTrue(ArmsRules.killHitstop(45) == 85 && ArmsRules.killHitstop(150) == 160 && ArmsRules.killHitstop(220) == 220
                    && ArmsRules.killHitstop(0) == 0, "a kill's freeze: 40 ms more, to at most 160, never shorter");
        } finally {
            ArmsServer.blowSeen = null;
        }
        ArmsTests.done(h, p);
    }

    /** As {@link ArmsTests#fighter} (at 1.5, 0.5 facing +z), but a survival player the server sees online. */
    private static ServerPlayer survivalFighter(GameTestHelper h) {
        ServerPlayer mock = ArmsTests.fighter(h, 1.5, 0.5, 0F); // lays the floor
        h.getLevel().getServer().getPlayerList().remove(mock);
        ServerPlayer p = AwakenTests.survivalPlayer(h, "arms-kill", new net.minecraft.core.BlockPos(1, 1, 0)).player();
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(7);
        Vec3 v = h.absoluteVec(new Vec3(1.5, 1, 0.5));
        p.snapTo(v.x, v.y, v.z, 0F, 0F);
        p.setYHeadRot(0F);
        p.setOnGround(true);
        return p;
    }

    /**
     * A wolf on 1 health hunting the fighter (a hunter that isn't a monster: a newly joined player may be under the
     * first scene's shield, which no monster targets through).
     */
    private static Mob hunter(GameTestHelper h, ServerPlayer p, double x, double z) {
        Mob m = EntityTypes.WOLF.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1);
        m.setHealth(1);
        Vec3 v = h.absoluteVec(new Vec3(x, 1, z));
        m.snapTo(v.x, v.y, v.z, 180F, 0F);
        m.setNoAi(true);
        h.getLevel().addFreshEntity(m);
        m.setTarget(p);
        return m;
    }

    /** The recoil's spring: out to its peak, back past rest by less than 30%, settled by 0.4 s. */
    static void recoilSpring(GameTestHelper h) {
        float peak = 0, under = 0, late = 0;
        for (int i = 0; i <= 1000; i++) {
            float t = i / 1000F, x = ArmsRules.recoil(t);
            peak = Math.max(peak, x);
            under = Math.min(under, x);
            if (t >= 0.4F) late = Math.max(late, Math.abs(x));
        }
        h.assertTrue(ArmsRules.recoil(0) == 0 && ArmsRules.recoil(-0.1F) == 0, "nothing before the freeze lets go");
        h.assertTrue(Math.abs(peak - 1) < 0.01, "the shove is its peak (" + peak + ")");
        h.assertTrue(under < 0 && -under < 0.3F, "it swings back past rest, by less than 30% (" + under + ")");
        h.assertTrue(late < 0.1F, "settled by 0.4 s (" + late + ")");
        h.succeed();
    }
}
