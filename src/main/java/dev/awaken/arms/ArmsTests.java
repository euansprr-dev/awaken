package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Arms' rules and their server path (SPEC §24): the chain and its rhythm, the arc, the damage stack intact, the guard's
 * answers, the latency grace, the dodge's evade and perfect ticks, stances that break, and the world switch.
 */
final class ArmsTests {
    private ArmsTests() {}

    static void register() {
        AwakenTests.register("arms_timeline", 20, ArmsTests::timeline);
        AwakenTests.register("arms_defence_rules", 20, ArmsTests::defenceRules);
        AwakenTests.register("arms_strike_arc", 60, ArmsTests::strikeArc);
        AwakenTests.register("arms_damage_stack", 60, ArmsTests::damageStack);
        AwakenTests.register("arms_deflect", 60, ArmsTests::deflect);
        AwakenTests.register("arms_deflect_latency", 80, ArmsTests::deflectLatency);
        AwakenTests.register("arms_dodge", 60, ArmsTests::dodge);
        AwakenTests.register("arms_posture_break", 60, ArmsTests::postureBreak);
        AwakenTests.register("arms_kill_switch", 40, ArmsTests::killSwitch);
        AwakenTests.register("arms_sounds", 20, ArmsTests::sounds);
        AwakenTests.register("arms_stance_rules", 20, ArmsTests::stanceRules);
    }

    /** A stance drains by one rule on both sides; a focus holds while looked at. */
    static void stanceRules(GameTestHelper h) {
        h.assertTrue(ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS, false) == 20, "untouched for its idle time: nothing drains yet");
        h.assertTrue(Math.abs(ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, false) - (20 - 20 * ArmsRules.POSTURE_REGEN)) < 1e-4,
                "then it drains at its rate");
        h.assertTrue(ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, true) < ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, false),
                "a Lord's stance comes back faster");
        h.assertTrue(ArmsRules.drained(5, 10_000, false) == 0, "never below empty");
        Posture p = new Posture();
        p.value = 20;
        p.max = 40;
        p.lastHitAt = 100;
        p.settle(100 + ArmsRules.POSTURE_IDLE_TICKS + 20, false);
        h.assertTrue(Math.abs(p.value - ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, false)) < 1e-4, "the server's settle is the same rule");
        p.settle(100 + ArmsRules.POSTURE_IDLE_TICKS + 20, false);
        h.assertTrue(Math.abs(p.value - ArmsRules.drained(20, ArmsRules.POSTURE_IDLE_TICKS + 20, false)) < 1e-4, "and never drains the same time twice");
        h.assertTrue(ArmsRules.lookedAt(170, -170) && !ArmsRules.lookedAt(90, 0), "the focus is looked at within 60 degrees, across the wrap");
        h.succeed();
    }

    /** A blow sounds like what it meets and how hard it lands; every class of tell has its one voice. */
    static void sounds(GameTestHelper h) {
        Archetypes.Loaded a = longsword();
        Archetype.MoveDef s1 = a.move(Archetype.Move.STRIKE_1), heavy = a.move(Archetype.Move.HEAVY), exec = a.move(Archetype.Move.EXECUTION);
        h.assertTrue(ArmsSounds.swish(s1) == ArmsSounds.SWISH_LIGHT.get() && ArmsSounds.swish(heavy) == ArmsSounds.SWISH_HEAVY.get()
                && ArmsSounds.swish(a.move(Archetype.Move.STRIKE_4)) == ArmsSounds.SWISH_THRUST.get(), "cuts, the heavy and the thrust each have their swish");
        h.assertTrue(ArmsSounds.swishPitch(2) > ArmsSounds.swishPitch(1) && ArmsSounds.swishPitch(0) == 1F, "the rhythm climbs a semitone a step");
        LivingEntity zombie = put(h, EntityTypes.ZOMBIE, 1, 1, 20);
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_FLESH.get(), "a cut into a zombie is flesh");
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.HEAVY, heavy) == ArmsSounds.HIT_FLESH_HEAVY.get(), "a heavy into it lands heavy");
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.EXECUTION, exec) == ArmsSounds.EXECUTION.get(), "an Execution is its own blow");
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(15);
        h.assertTrue(ArmsSounds.impact(zombie, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_ARMOR.get(), "plate worn over flesh rings as armour");
        LivingEntity skeleton = put(h, EntityTypes.SKELETON, 2, 1, 20), golem = put(h, EntityTypes.IRON_GOLEM, 3, 1, 20),
                slime = put(h, EntityTypes.SLIME, 1, 3, 20), vex = put(h, EntityTypes.VEX, 2, 3, 20);
        h.assertTrue(ArmsSounds.impact(skeleton, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_BONE.get(), "a skeleton is bone");
        h.assertTrue(ArmsSounds.impact(golem, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_STONE.get(), "a golem is stone and iron");
        h.assertTrue(ArmsSounds.impact(slime, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_SLIME.get(), "a slime is slime");
        h.assertTrue(ArmsSounds.impact(vex, Archetype.Move.STRIKE_1, s1) == ArmsSounds.HIT_SPIRIT.get(), "a vex is a spirit");
        h.assertTrue(ArmsSounds.impactPitch(golem, heavy) < 1F && ArmsSounds.impactPitch(zombie, s1) == 1F, "a heavy on a hard body lands lower");
        for (Strikes.Kind k : Strikes.Kind.values()) {
            h.assertTrue((Arms.tellSound(k) == null) == (k == Strikes.Kind.NONE), k + ": every class of blow has its tell's voice");
        }
        for (LivingEntity e : new LivingEntity[]{zombie, skeleton, golem, slime, vex}) e.discard();
        h.succeed();
    }

    static Archetypes.Loaded longsword() {
        Archetypes.Loaded a = Archetypes.get(Arms.LONGSWORD);
        if (a == null) throw new IllegalStateException("no longsword in the timing table");
        return a;
    }

    // ---- pure rules -------------------------------------------------------------------------------------------------

    static void timeline(GameTestHelper h) {
        Archetypes.Loaded a = longsword();
        Archetype.MoveDef s1 = a.move(Archetype.Move.STRIKE_1);
        h.assertTrue(s1 != null && s1.hasCadence() && s1.windup() == 2 && s1.active() == 2, "strike 1 is 2 + 2 with a cadence window");
        ArmsRules.Decision idle = ArmsRules.tap(a, null, 0, -1, 0, 100, 0);
        h.assertTrue(idle != null && idle.move() == Archetype.Move.STRIKE_1 && idle.startAt() == 100, "idle: the chain starts at once");
        int c0 = s1.cadence().get(0), c1 = s1.cadence().get(1);
        ArmsRules.Decision onBeat = ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 0, 100 + c0, 0);
        h.assertTrue(onBeat != null && onBeat.move() == Archetype.Move.STRIKE_2 && onBeat.cadence() == 1 && onBeat.startAt() == 100 + c0,
                "a press in the window: strike 2 now, one rhythm step");
        ArmsRules.Decision mash = ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 2, 100 + c0 - 2, 0);
        h.assertTrue(mash != null && mash.cadence() == 0 && mash.startAt() == 100 + c0, "a press just early is buffered to the window and loses the rhythm");
        h.assertTrue(ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 0, 100, 0) == null, "a press far ahead of the window counts for nothing");
        ArmsRules.Decision late = ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 2, 100 + c1 + 2, 0);
        h.assertTrue(late != null && late.move() == Archetype.Move.STRIKE_2 && late.cadence() == 0, "a late press continues the chain without the rhythm");
        ArmsRules.Decision gone = ArmsRules.tap(a, Archetype.Move.STRIKE_1, 100, 0, 2, 100 + s1.total() + a.def().late(), 0);
        h.assertTrue(gone != null && gone.move() == Archetype.Move.STRIKE_1, "long after: the chain starts again");
        Archetype.MoveDef fin = a.move(Archetype.Move.STRIKE_3);
        ArmsRules.Decision after = ArmsRules.tap(a, Archetype.Move.STRIKE_3, 100, 2, 3, 100 + fin.total(), 0);
        h.assertTrue(after != null && after.move() == Archetype.Move.STRIKE_1, "after the finisher the chain starts again");
        ArmsRules.Decision mastered = ArmsRules.tap(a, Archetype.Move.STRIKE_3, 100, 2, 3, 100 + fin.total(), 1);
        h.assertTrue(mastered != null && mastered.move() == Archetype.Move.STRIKE_1, "a finisher has no window: mastery adds a step only through it");
        h.assertTrue(Math.abs(ArmsRules.motion(s1, 3, false) - s1.motion() * 1.24F) < 1e-4, "three rhythm steps: +24%");
        Archetype.MoveDef heavy = a.move(Archetype.Move.HEAVY);
        h.assertTrue(heavy != null && heavy.charge().isPresent() && heavy.charge().get().perfect(heavy.charge().get().glint() + 2)
                && !heavy.charge().get().perfect(heavy.charge().get().glint() + 3), "the glint: a release within 2 ticks is perfect");
        h.assertTrue(ArmsRules.recovery(s1, a, a.def().baseSpeed() * 2) < s1.recovery() && ArmsRules.recovery(s1, a, a.def().baseSpeed()) == s1.recovery(),
                "faster arms recover sooner");
        h.succeed();
    }

    static void defenceRules(GameTestHelper h) {
        Archetype.Guard g = longsword().def().guard();
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 0, 0, false, true) == ArmsRules.Answer.DEFLECT, "the raise's first tick deflects");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, g.deflect() - 1, 0, 0, false, true) == ArmsRules.Answer.DEFLECT, "the window's last tick deflects");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, g.deflect(), 0, 0, false, true) == ArmsRules.Answer.BLOCK, "after the window: a block");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.HEAVY, g.deflect() + 4, 0, 0, false, true) == ArmsRules.Answer.GUARD_BREAK, "a heavy breaks a held guard");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.HEAVY, 1, 0, 0, false, true) == ArmsRules.Answer.DEFLECT, "a heavy can be deflected");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.PERILOUS, 0, 0, 0, false, true) == ArmsRules.Answer.PASS, "a perilous blow passes any guard");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 0, 0, true, true) == ArmsRules.Answer.BLOCK, "locked out: a spammed raise only blocks");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 0, 0, false, false) == ArmsRules.Answer.PASS, "a blow from behind passes the guard");
        // the grace: a raise that arrived after the blow, within the grace, still deflects as the player saw it
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 2, 2, false, true) == ArmsRules.Answer.DEFLECT, "held 2 ticks, grace 2: a deflect");
        h.assertTrue(ArmsRules.guard(g, Strikes.Kind.LIGHT, 0, 3, 2, false, true) == ArmsRules.Answer.BLOCK, "beyond the grace: a block");
        h.assertTrue(ArmsRules.grace(20) == 0 && ArmsRules.grace(150) == 2 && ArmsRules.grace(300) == 3 && ArmsRules.grace(900) == ArmsRules.GRACE_MAX,
                "grace: none near, 2 ticks at 150 ms, capped at 3");
        Archetype.Dodge d = longsword().def().dodge();
        double run = 0;
        for (int t = 0; t < d.ticks(); t++) run += ArmsRules.dodgeSpeed(d, t);
        h.assertTrue(Math.abs(run - d.distance()) < 1e-6, "the dodge covers its distance: " + run);
        h.assertTrue(ArmsRules.dodgeSpeed(d, 0) > ArmsRules.dodgeSpeed(d, d.ticks() - 1), "a fast start easing out");
        h.assertTrue(ArmsRules.evades(d, d.evade() - 1, Strikes.Kind.PERILOUS) && !ArmsRules.evades(d, d.evade(), Strikes.Kind.LIGHT)
                && !ArmsRules.evades(d, 0, Strikes.Kind.NONE), "evade ticks, nothing after, never a fall or fire");
        h.assertTrue(ArmsRules.perfect(d, d.perfect() - 1) && !ArmsRules.perfect(d, d.perfect()), "perfect ticks");
        h.assertTrue(ArmsRules.postureMax(CreatureTag.NONE, MobRole.Role.BOSS) == ArmsRules.POSTURE_LORD
                && ArmsRules.postureMax(CreatureTag.NONE, MobRole.Role.ELITE) == ArmsRules.POSTURE_ELITE
                && ArmsRules.postureMax(CreatureTag.NONE, MobRole.Role.NONE) == ArmsRules.POSTURE_NORMAL, "stances by role");
        h.assertTrue(ArmsRules.capExecution(500, 1000, 0.08F, true) == 80 && ArmsRules.capExecution(500, 1000, 0.08F, false) == 500,
                "an Execution takes at most 8% of a Lord");
        h.assertTrue(ArmsRules.focusTurn(20, 0) == ArmsRules.FOCUS_TURN && ArmsRules.focusTurn(90, 0) == 0, "soft focus turns at most 15°, only near");
        h.succeed();
    }

    // ---- the server path --------------------------------------------------------------------------------------------

    static ServerPlayer fighter(GameTestHelper h, double x, double z, float yaw) {
        for (int bx = -3; bx <= 6; bx++) for (int bz = -3; bz <= 6; bz++) {
            h.setBlock(new BlockPos(bx, 0, bz), Blocks.STONE);
            for (int y = 1; y <= 3; y++) h.setBlock(new BlockPos(bx, y, bz), Blocks.AIR);
        }
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.connection.markClientLoaded();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        // a mock player never ticks, so its held item's modifiers never apply: give it the diamond sword's 7 by hand
        p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7);
        Vec3 v = h.absoluteVec(new Vec3(x, 1, z));
        p.snapTo(v.x, v.y, v.z, yaw, 0F);
        p.setYHeadRot(yaw);
        p.setOnGround(true);
        p.removeEffect(MobEffects.SLOWNESS);
        return p;
    }

    static <T extends Mob> T put(GameTestHelper h, net.minecraft.world.entity.EntityType<T> type, double x, double z, float health) {
        T m = type.create(h.getLevel(), EntitySpawnReason.COMMAND);
        m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        m.setHealth(health);
        m.getAttribute(Attributes.ARMOR).setBaseValue(0);
        Vec3 v = h.absoluteVec(new Vec3(x, 1, z));
        m.snapTo(v.x, v.y, v.z, 180F, 0F);
        m.setNoAi(true);
        h.getLevel().addFreshEntity(m);
        return m;
    }

    static void done(GameTestHelper h, ServerPlayer... ps) {
        for (ServerPlayer p : ps) {
            ArmsServer.clear(p);
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    /** Runs a move's active ticks on the server's clock, as the player tick would. */
    static void runActive(ServerPlayer p) {
        ArmsState s = ArmsServer.state(p);
        if (s.move == null || s.style == null) return;
        Archetype.MoveDef def = s.style.move(s.move);
        for (int i = 0; i < def.active(); i++) ArmsServer.sweep(p, s, def, ArmsServer.now(p));
    }

    /** The aimed creature is struck at the move's multiplier; a hostile in the arc too; nothing behind or friendly. */
    static void strikeArc(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F); // facing +z
        LivingEntity aimed = put(h, EntityTypes.PIG, 1.5, 2.3, 200);
        LivingEntity side = put(h, EntityTypes.ZOMBIE, 2.6, 2.0, 200);
        LivingEntity behind = put(h, EntityTypes.ZOMBIE, 1.5, -1.6, 200);
        LivingEntity calm = put(h, EntityTypes.COW, 0.4, 2.0, 200);
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, aimed.getId()));
        ArmsState s = ArmsServer.state(p);
        h.assertTrue(s.move == Archetype.Move.STRIKE_1, "a tap starts strike 1, was " + s.move);
        runActive(p);
        float want = 7F * longsword().move(Archetype.Move.STRIKE_1).motion();
        h.assertTrue(Math.abs(200 - aimed.getHealth() - want) < 0.05, "the aimed pig takes 7 x 0.9 = " + want + ", took " + (200 - aimed.getHealth()));
        h.assertTrue(side.getHealth() < 200, "a hostile in the arc is struck too");
        h.assertTrue(behind.getHealth() == 200, "nothing behind is struck");
        h.assertTrue(calm.getHealth() == 200, "a calm creature in the arc is left alone (only the aimed one is struck)");
        // the same move never strikes twice
        float was = aimed.getHealth();
        runActive(p);
        h.assertTrue(aimed.getHealth() == was, "one hit per creature per move");
        done(h, p);
    }

    /** Every listener after the Arms stage still applies: the momentum hit's +30% from a slide-speed run. */
    static void damageStack(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        LivingEntity pig = put(h, EntityTypes.PIG, 1.5, 2.3, 200);
        p.setSprinting(true);
        p.setKnownMovement(new Vec3(0, 0, 0.45));
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, pig.getId()));
        runActive(p);
        float base = 7F * longsword().move(Archetype.Move.STRIKE_1).motion();
        float dealt = 200 - pig.getHealth();
        h.assertTrue(dealt > base * 1.25F, "the momentum hit still adds its share on an Arms strike: " + dealt + " vs " + base);
        // and vanilla's own attack never resolves for an Arms weapon
        float before = pig.getHealth();
        pig.damageCooldownTime = 0;
        p.attack(pig);
        h.assertTrue(pig.getHealth() == before, "a vanilla attack with an Arms weapon does nothing (the moves strike)");
        done(h, p);
    }

    /** A blow in the raise's first ticks is deflected: no damage, the attacker's stance takes it; a perilous one lands. */
    static void deflect(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        Mob z = put(h, EntityTypes.ZOMBIE, 1.5, 1.9, 60);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        p.startUsingItem(InteractionHand.MAIN_HAND);
        ArmsServer.onUseStart(new net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start(p, p.getMainHandItem(), InteractionHand.MAIN_HAND, 72000));
        h.assertTrue(p.isBlocking(), "a sword raises a guard at once (the blocking component, no delay)");
        float hp = p.getHealth();
        Strikes.deliver(z, Strikes.Kind.LIGHT, () -> z.doHurtTarget(h.getLevel(), p));
        h.assertTrue(p.getHealth() == hp, "a LIGHT blow on the raise is deflected: no damage (took " + (hp - p.getHealth()) + ")");
        h.assertTrue(Arms.posture(z) > 0, "the attacker's stance took the deflect");
        ArmsState s = ArmsServer.state(p);
        h.assertTrue(s.raiseDeflected && ArmsServer.now(p) - s.deflectAt <= 1, "the deflect is remembered (the riposte opens)");
        // the riposte: the next tap is the riposte
        p.stopUsingItem();
        ArmsServer.onPress(p, new ArmsPayloads.Press(2, ArmsPayloads.PRESS_STRIKE, 0F, 0F, z.getId()));
        h.assertTrue(s.move == Archetype.Move.RIPOSTE, "a tap right after a deflect is a riposte, was " + s.move);
        // a perilous blow passes the guard
        p.startUsingItem(InteractionHand.MAIN_HAND);
        z.damageCooldownTime = 0;
        p.damageCooldownTime = 0;
        hp = p.getHealth();
        Strikes.deliver(z, Strikes.Kind.PERILOUS, () -> p.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(z), 4F));
        h.assertTrue(p.getHealth() < hp, "a PERILOUS blow passes any guard");
        done(h, p);
    }

    /** At 150 ms a blow on an unguarded fighter waits out the grace; a guard raised in it still deflects. */
    static void deflectLatency(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        Mob z = put(h, EntityTypes.ZOMBIE, 1.5, 1.9, 60);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        ArmsServer.state(p).testGrace = ArmsRules.grace(150);
        float hp = p.getHealth();
        Strikes.deliver(z, Strikes.Kind.LIGHT, () -> z.doHurtTarget(h.getLevel(), p));
        h.assertTrue(p.getHealth() == hp && ArmsServer.holds(p) == 1, "the blow is held for the grace, not landed");
        // the press arrives a tick later: the guard rises, the held blow meets it on the next server tick
        p.startUsingItem(InteractionHand.MAIN_HAND);
        ArmsServer.onUseStart(new net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start(p, p.getMainHandItem(), InteractionHand.MAIN_HAND, 72000));
        h.startSequence().thenIdle(2).thenExecute(() -> {
            h.assertTrue(ArmsServer.holds(p) == 0, "the held blow was landed");
            h.assertTrue(p.getHealth() == hp, "and deflected by the guard raised inside the grace (took " + (hp - p.getHealth()) + ")");
            h.assertTrue(ArmsServer.state(p).raiseDeflected, "counted as a deflect");
            // no guard at all: the blow lands once its grace is over
            p.stopUsingItem();
            z.damageCooldownTime = 0;
            p.damageCooldownTime = 0;
            Strikes.deliver(z, Strikes.Kind.LIGHT, () -> z.doHurtTarget(h.getLevel(), p));
        }).thenIdle(ArmsRules.grace(150) + 1).thenExecute(() -> {
            h.assertTrue(p.getHealth() < hp, "unanswered, it lands after the grace");
            done(h, p);
        });
    }

    /** The dodge's first ticks evade a blow; the first of them are perfect and slow the attacker alone. */
    static void dodge(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        Mob z = put(h, EntityTypes.ZOMBIE, 1.5, 1.9, 60);
        z.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        ArmsServer.onDodge(p, new ArmsPayloads.Dodge(1, -1F, 0F, p.getX(), p.getY(), p.getZ()));
        ArmsState s = ArmsServer.state(p);
        h.assertTrue(s.dodgeAt == ArmsServer.now(p), "the dodge was accepted");
        float hp = p.getHealth();
        Strikes.deliver(z, Strikes.Kind.PERILOUS, () -> z.doHurtTarget(h.getLevel(), p));
        h.assertTrue(p.getHealth() == hp, "a blow on the dodge's first tick is evaded");
        h.assertTrue(ArmsServer.slowed(z), "a perfect dodge slows the attacker");
        Archetype.Dodge d = longsword().def().dodge();
        h.assertTrue(s.dodgeCharges == d.charges() - 1, "a charge spent");
        ArmsServer.onDodge(p, new ArmsPayloads.Dodge(2, 1F, 0F, p.getX(), p.getY(), p.getZ()));
        h.assertTrue(s.dodgeCharges == d.charges() - 1, "no second dodge while one is running");
        h.startSequence().thenIdle(d.evade() + 1).thenExecute(() -> {
            z.damageCooldownTime = 0;
            p.damageCooldownTime = 0;
            Strikes.deliver(z, Strikes.Kind.LIGHT, () -> z.doHurtTarget(h.getLevel(), p));
            h.assertTrue(p.getHealth() < hp, "after the evade ticks a blow lands");
            // a dodge far from where the server saw the feet is refused
            int before = s.dodgeCharges;
            ArmsServer.onDodge(p, new ArmsPayloads.Dodge(3, 1F, 0F, p.getX() + 5, p.getY(), p.getZ()));
            h.assertTrue(s.dodgeCharges == before, "a dodge claimed from somewhere else is refused");
            done(h, p);
        });
    }

    /** A full stance breaks: the creature staggers, it can't break again at once, and a heavy on it is an Execution. */
    static void postureBreak(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        Mob z = put(h, EntityTypes.ZOMBIE, 1.5, 2.0, 400);
        Posture post = ArmsServer.posture(z);
        h.assertTrue(post.max == ArmsRules.POSTURE_NORMAL, "a plain zombie holds " + ArmsRules.POSTURE_NORMAL + ", holds " + post.max);
        ArmsServer.addPosture(z, ArmsRules.POSTURE_NORMAL - 1, p);
        h.assertTrue(!Arms.broken(z), "one short of full: not broken");
        ArmsServer.addPosture(z, 1, p);
        h.assertTrue(Arms.broken(z) && z.hasEffect(MobEffects.SLOWNESS), "full: broken, and it staggers");
        h.assertTrue(post.value == 0, "a broken stance starts again from nothing");
        // a heavy on a broken creature is an Execution
        ArmsState s = ArmsServer.state(p);
        s.aimed = z.getId();
        Archetypes.Loaded a = longsword();
        ArmsServer.heavy(p, s, a, a.move(Archetype.Move.HEAVY), 10, ArmsServer.now(p), 5);
        h.assertTrue(s.move == Archetype.Move.EXECUTION, "a heavy on a broken creature is an Execution, was " + s.move);
        // it can't break again for a while
        post.brokenUntil = 0;
        ArmsServer.addPosture(z, ArmsRules.POSTURE_NORMAL, p);
        h.assertTrue(!Arms.broken(z), "no second Break inside the cooldown");
        // idle stances drain
        Posture q = new Posture();
        q.max = 20;
        q.value = 10;
        q.lastHitAt = 0;
        q.settle(ArmsRules.POSTURE_IDLE_TICKS + 20, false);
        h.assertTrue(Math.abs(q.value - (10 - 20 * ArmsRules.POSTURE_REGEN)) < 1e-4, "a stance left alone drains after 2 s");
        q.settle(ArmsRules.POSTURE_IDLE_TICKS + 20, false);
        h.assertTrue(Math.abs(q.value - (10 - 20 * ArmsRules.POSTURE_REGEN)) < 1e-4, "and never drains twice for the same time");
        done(h, p);
    }

    /** Off: vanilla melee everywhere, the Arms stage stands aside. */
    static void killSwitch(GameTestHelper h) {
        ServerPlayer p = fighter(h, 1.5, 0.5, 0F);
        LivingEntity pig = put(h, EntityTypes.PIG, 1.5, 2.3, 200);
        ArmsConfig.enabledOverride = false;
        try {
            h.assertTrue(!Arms.fights(p), "off: Arms runs no one's melee");
            p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1024);
            p.attack(pig);
            h.assertTrue(Math.abs(200 - pig.getHealth() - 7F) < 0.05, "off: vanilla's attack deals the sword's 7, dealt " + (200 - pig.getHealth()));
            p.startUsingItem(InteractionHand.MAIN_HAND);
            h.assertTrue(!p.isUsingItem(), "off: a sword raises no guard, as in vanilla");
        } finally {
            ArmsConfig.enabledOverride = null;
        }
        h.assertTrue(Arms.fights(p), "on again");
        p.startUsingItem(InteractionHand.MAIN_HAND);
        h.assertTrue(p.isUsingItem() && p.isBlocking(), "on: the sword's guard rises");
        p.stopUsingItem();
        done(h, p);
    }
}
