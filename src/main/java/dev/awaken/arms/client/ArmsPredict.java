package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsConfig;
import dev.awaken.arms.ArmsRules;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.body.Body;
import dev.awaken.core.api.CombatFeedback;
import dev.awaken.core.api.MovementHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's half of Arms (SPEC §24): it reads the three verbs, predicts the move with the same rules the server
 * judges by ({@link ArmsRules}), starts it on the frame of the press, and takes the server's word when it comes back.
 * Strike: the attack key (tap = the chain, held = a heavy). Guard: the use key (vanilla's blocking, judged by the
 * server). Dodge: the sprint key with back, left, right or no direction, on the ground.
 */
public final class ArmsPredict {
    private ArmsPredict() {}

    /** The client's own tick count (moves are timed on it, the server's clock judges them). */
    static int ticks;
    static int seq;
    // the move running
    static Archetype.@Nullable Move move;
    static Archetypes.@Nullable Loaded style;
    static int moveStart, step = -1, cadence, recovery;
    /** Wall-clock start of the move, and the hold time at that start (the hands' clock skips hit-stop). */
    static long moveNanos, movePaused;
    static ArmsRules.@Nullable Decision buffered;
    static int bufferedSeq;
    /** A refused move fades out instead of snapping: the tick it started fading. */
    static int fadeFrom = -100;
    // the strike key held into a heavy
    static boolean pressHeld, charging;
    static int heldTicks, chargeStart;
    // what the server told us
    static int deflectAt = -1000, dodgeAt = -1000, resonance;
    static final Map<Integer, Integer> BROKEN = new HashMap<>();
    // dodge
    static boolean dodging;
    static int dodgeTick;
    static double dodgeX, dodgeZ;
    static int dodgeCharges = -1, dodgeRechargeAt;
    private static boolean sprintWas;
    static int fightAt = -1000;

    static int nextSeq() {
        return ++seq;
    }

    /** Whether Arms takes this player's attack key now (a weapon with a style, the world switch on). */
    static boolean fights(LocalPlayer p) {
        return ArmsConfig.enabled() && !p.isSpectator() && Arms.styleOf(p.getMainHandItem()) != null;
    }

    // ---- strike -----------------------------------------------------------------------------------------------------

    /** The attack key: a block under the crosshair outside a fight is still mined; anything else is a strike. */
    static void onKey(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !fights(p)) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && ticks - fightAt > 100) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        press(p, mc);
    }

    static void press(LocalPlayer p, Minecraft mc) {
        Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
        if (st == null) return;
        int target = mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getId() : -1;
        int s = nextSeq();
        pressHeld = true;
        heldTicks = 0;
        charging = false;
        fightAt = ticks;
        ClientPacketDistributor.sendToServer(new ArmsPayloads.Press(s, ArmsPayloads.PRESS_STRIKE, p.getYRot(), p.getXRot(), target));
        Archetype.MoveDef riposte = st.move(Archetype.Move.RIPOSTE);
        if (riposte != null && ticks - deflectAt <= riposte.window()) {
            deflectAt = -1000;
            begin(p, st, Archetype.Move.RIPOSTE, -1, 0, s);
            return;
        }
        Archetype.MoveDef slip = st.move(Archetype.Move.SLIPCUT);
        if (slip != null && ticks - dodgeAt >= 1 && ticks - dodgeAt <= st.def().dodge().ticks() + slip.window()) {
            dodgeAt = -1000;
            begin(p, st, Archetype.Move.SLIPCUT, -1, 0, s);
            return;
        }
        ArmsRules.Decision d = ArmsRules.tap(st, style == st ? move : null, moveStart, step, cadence, ticks, 0);
        if (d == null) return;
        if (d.startAt() > ticks) {
            buffered = d;
            bufferedSeq = s;
            return;
        }
        begin(p, st, d.move(), d.step(), d.cadence(), s);
    }

    static void begin(LocalPlayer p, Archetypes.Loaded st, Archetype.Move m, int st2, int cad, int s) {
        begin(p, st, m, st2, cad, s, false);
    }

    /** perfect: a heavy released on its glint. */
    static void begin(LocalPlayer p, Archetypes.Loaded st, Archetype.Move m, int st2, int cad, int s, boolean perfect) {
        Archetype.MoveDef def = st.move(m);
        if (def == null) return;
        perfectHeavy = perfect;
        style = st;
        move = m;
        step = st2;
        cadence = cad;
        moveStart = ticks;
        moveNanos = System.nanoTime();
        movePaused = ArmsFeel.paused();
        recovery = ArmsRules.recovery(def, st, p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
        buffered = null;
        fadeFrom = -100;
        ArmsAudio.swing(p, st, m, cad);
        ArmsFx.move(p, st, m, cad, perfect);
    }

    /** Ticks into the running move on the hands' clock (hit-stop holds pause it), with the frame's fraction. */
    static float moveTime() {
        long nanos = System.nanoTime() - moveNanos - (ArmsFeel.paused() - movePaused);
        return nanos / 50_000_000F;
    }

    static void tick() {
        ticks++;
        ArmsFeel.tick();
        ArmsFx.tick();
        ArmsStances.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (buffered != null && ticks >= buffered.startAt()) {
            ArmsRules.Decision d = buffered;
            Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
            buffered = null;
            if (st != null) begin(p, st, d.move(), d.step(), d.cadence(), bufferedSeq);
        }
        if (move != null && style != null) {
            Archetype.MoveDef def = style.move(move);
            if (def == null || moveTime() > def.windup() + def.active() + recovery + style.def().late()) {
                move = null; // past the late press: the next tap starts the chain again either way
                step = -1;
            }
        }
        if (pressHeld) heldOrReleased(p, mc);
        if (dodgeCharges >= 0 && style != null && ticks >= dodgeRechargeAt && dodgeCharges < style.def().dodge().charges()) {
            dodgeCharges++;
            dodgeRechargeAt = ticks + style.def().dodge().recharge();
        }
        if (!BROKEN.isEmpty()) BROKEN.values().removeIf(until -> until < ticks);
    }

    private static void heldOrReleased(LocalPlayer p, Minecraft mc) {
        Archetypes.Loaded st = style;
        Archetype.MoveDef heavy = st == null ? null : st.move(Archetype.Move.HEAVY);
        if (heavy == null || heavy.charge().isEmpty() || !fights(p)) {
            pressHeld = false;
            return;
        }
        Archetype.Charge c = heavy.charge().get();
        if (mc.options.keyAttack.isDown()) {
            heldTicks++;
            if (!charging && heldTicks >= c.min()) {
                charging = true;
                chargeStart = ticks;
                move = Archetype.Move.HEAVY;
                moveStart = ticks;
                moveNanos = System.nanoTime();
                movePaused = ArmsFeel.paused();
                step = -1;
                ClientPacketDistributor.sendToServer(new ArmsPayloads.Press(seq, ArmsPayloads.PRESS_CHARGE, p.getYRot(), p.getXRot(), -1));
                ArmsAudio.charge(p);
            }
            if (charging && c.min() + (ticks - chargeStart) == c.glint()) ArmsAudio.glint(p);
            if (!charging || c.min() + (ticks - chargeStart) < c.max()) return;
        }
        pressHeld = false;
        if (!charging) return;
        charging = false;
        int target = mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getId() : -1;
        ClientPacketDistributor.sendToServer(new ArmsPayloads.Press(seq, ArmsPayloads.PRESS_RELEASE, p.getYRot(), p.getXRot(), target));
        boolean execution = target >= 0 && BROKEN.containsKey(target) && st.move(Archetype.Move.EXECUTION) != null;
        begin(p, st, execution ? Archetype.Move.EXECUTION : Archetype.Move.HEAVY, -1, 0, seq,
                !execution && c.perfect(c.min() + (ticks - chargeStart)));
    }

    /** The running heavy was released on its glint (its arc burns gold). */
    static boolean perfectHeavy;

    /** How far a held heavy has charged (0..1, the glint at its mark), or -1 when not charging. */
    static float charge() {
        if (!charging || style == null) return -1;
        Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
        if (heavy == null || heavy.charge().isEmpty()) return -1;
        Archetype.Charge c = heavy.charge().get();
        return Mth.clamp((c.min() + (ticks - chargeStart)) / (float) c.max(), 0, 1);
    }

    // ---- dodge --------------------------------------------------------------------------------------------------------

    /** After the body's own input (LOW): the sprint key with no forward on the ground is a dodge. */
    static void onInput(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e.getEntity() instanceof LocalPlayer p) || p != mc.player) return;
        ClientInput in = e.getInput();
        Input k = in.keyPresses;
        boolean sprintPressed = k.sprint() && !sprintWas;
        sprintWas = k.sprint();
        if (dodging) {
            dodgeTick(p);
            return;
        }
        if (!sprintPressed || k.forward() || !canDodge(p)) return;
        Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
        if (st == null) st = Archetypes.get(Arms.LONGSWORD);
        if (st == null) return;
        Archetype.Dodge d = st.def().dodge();
        if (dodgeCharges < 0) dodgeCharges = d.charges();
        if (dodgeCharges <= 0) return;
        // a strike in its wind-up or blow can't be dodged out of; its recovery can
        if (move != null && style != null) {
            Archetype.MoveDef def = style.move(move);
            if (def != null && moveTime() < def.activeEnd()) return;
        }
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), lx = Mth.cos(yaw), lz = Mth.sin(yaw);
        double x = 0, z = 0;
        if (k.left()) { x += lx; z += lz; }
        if (k.right()) { x -= lx; z -= lz; }
        if (k.backward() || (x == 0 && z == 0)) { x -= fx; z -= fz; }
        double len = Math.sqrt(x * x + z * z);
        dodgeX = x / len;
        dodgeZ = z / len;
        if (p.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(p); // the dodge drops the guard
        if (dodgeCharges == d.charges()) dodgeRechargeAt = ticks + d.recharge();
        dodgeCharges--;
        dodging = true;
        dodgeTick = 0;
        dodgeAt = ticks;
        style = st;
        move = null;
        step = -1;
        cadence = 0;
        buffered = null;
        fightAt = ticks;
        ClientPacketDistributor.sendToServer(new ArmsPayloads.Dodge(nextSeq(), (float) dodgeX, (float) dodgeZ, p.getX(), p.getY(), p.getZ()));
        ArmsAudio.dodge(p);
        CombatFeedback.kick((float) (dodgeX * fz - dodgeZ * fx) * -2.5F, 0F, (float) (dodgeX * lx + dodgeZ * lz) * 3.5F, 8);
        dodgeTick(p);
    }

    private static boolean canDodge(LocalPlayer p) {
        boolean engaged = fights(p) || ticks - fightAt < 100;
        return engaged && ArmsConfig.enabled() && p.onGround() && !p.isInLiquid() && !p.isPassenger() && !p.isSpectator() && !p.getAbilities().flying
                && !Body.isSliding(p) && !Body.isMantling(p) && !MovementHooks.wallRunning(p) && !MovementHooks.hanging(p);
    }

    private static void dodgeTick(LocalPlayer p) {
        Archetype.Dodge d = style == null ? null : style.def().dodge();
        if (d == null || dodgeTick >= d.ticks()) {
            dodging = false;
            return;
        }
        double v = ArmsRules.dodgeSpeed(d, dodgeTick++);
        Vec3 dm = p.getDeltaMovement();
        p.setDeltaMovement(dodgeX * v, dm.y, dodgeZ * v);
    }

    // ---- the server's word --------------------------------------------------------------------------------------------

    static void onResult(ArmsPayloads.Result r) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        switch (r.what()) {
            case ArmsPayloads.MOVE -> {
                Archetype.Move m = Archetype.Move.byId(r.aux());
                if (r.seq() == seq && m != null && m != move && style != null && !charging) {
                    move = m; // the server ran another move than we guessed: take its word, keep the clock
                    cadence = r.aux2();
                }
                if (r.seq() == seq) cadence = r.aux2();
            }
            case ArmsPayloads.REFUSE -> {
                if (r.entity() == -2) {
                    dodging = false;
                } else if (r.seq() == seq) {
                    fadeFrom = ticks;
                    move = null;
                    step = -1;
                    charging = false;
                }
            }
            case ArmsPayloads.HIT -> {
                fightAt = ticks;
                ArmsAudio.impact(p, r.entity(), style, r.aux2());
                hitFx(p, r.entity(), style, r.aux2());
                ArmsStances.struck(r.entity());
                if (r.aux() > 0) {
                    CombatFeedback.hitstop(r.entity(), r.aux());
                    CombatFeedback.hold(r.aux());
                    Archetype.Move m = Archetype.Move.byId(r.aux2());
                    Archetype.MoveDef def = style == null || m == null ? null : style.move(m);
                    if (def != null) {
                        java.util.List<Float> from = def.swing().from(), to = def.swing().to();
                        // along the blow's travel on screen (y up there, pitch down here)
                        float dx = to.get(0) - from.get(0), dy = to.get(1) - from.get(1);
                        float n = Math.max(1e-3F, (float) Math.sqrt(dx * dx + dy * dy));
                        float s = Math.min(2.4F, r.aux() / 40F);
                        CombatFeedback.kick(dx / n * s, -dy / n * s, dx / n * s * 0.4F, 6);
                    }
                }
            }
            case ArmsPayloads.DEFLECT -> {
                deflectAt = ticks;
                fightAt = ticks;
                CombatFeedback.hitstop(r.entity(), 100);
                CombatFeedback.hold(100);
                CombatFeedback.kick(0F, -1.8F, 1.2F, 7);
                ArmsAudio.deflect(p);
            }
            case ArmsPayloads.BLOCK -> {
                fightAt = ticks;
                CombatFeedback.kick(0F, 1.2F, 0F, 5);
                ArmsAudio.block(p);
            }
            case ArmsPayloads.GUARD_BREAK -> {
                fightAt = ticks;
                CombatFeedback.kick(0F, 3.5F, -2F, 10);
                ArmsAudio.guardBreak(p);
            }
            case ArmsPayloads.PERFECT_DODGE -> {
                fightAt = ticks;
                CombatFeedback.hitstop(r.entity(), 60);
                ArmsAudio.perfectDodge(p);
            }
            case ArmsPayloads.BREAK -> {
                BROKEN.put(r.entity(), ticks + r.aux());
                CombatFeedback.hitstop(r.entity(), 160);
                CombatFeedback.hold(120);
                CombatFeedback.kick(0F, 2.5F, 0F, 10);
            }
            case ArmsPayloads.RESONANCE -> resonance = r.aux();
            case ArmsPayloads.HURT -> hurt(p, r.aux(), r.aux2() / 10F);
            default -> { }
        }
    }

    /** A blow landed on us: its thump, the view nudged away from where it came from, the HUD's shard toward it. */
    private static void hurt(LocalPlayer p, int fromYaw, float damage) {
        ArmsAudio.hurt(p);
        if (fromYaw == Integer.MIN_VALUE) return; // no place it came from (a blast's centre is gone, a thorn)
        hurtYaw = fromYaw;
        hurtAt = ticks;
        // vanilla already rolls the view toward the blow; this adds the jolt of its weight, never more than a few degrees
        float rel = Mth.wrapDegrees(fromYaw - p.getYRot()) * Mth.DEG_TO_RAD;
        float w = Mth.clamp(damage / 6F, 0.4F, 1.5F);
        CombatFeedback.kick(-Mth.sin(rel) * 1.6F * w, -Mth.cos(rel) * 1.4F * w, 0F, 7);
    }

    /** The world yaw a blow on us last came from, and the tick it landed (the HUD's shard). */
    static int hurtYaw, hurtAt = -1000;

    /** Sparks where our blade landed. */
    private static void hitFx(Entity by, int targetId, Archetypes.@Nullable Loaded st, int moveId) {
        Minecraft mc = Minecraft.getInstance();
        Entity t = mc.level == null ? null : mc.level.getEntity(targetId);
        Archetype.Move m = Archetype.Move.byId(moveId);
        Archetype.MoveDef def = st == null || m == null ? null : st.move(m);
        if (t != null && def != null) ArmsFx.hit(t, by, def);
    }

    /** Another fighter near us: their move's arc, their blow's sparks and freeze, a stance breaking. */
    static void onSeen(ArmsPayloads.Seen s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (s.what()) {
            case ArmsPayloads.MOVE -> {
                Entity e = mc.level.getEntity(s.entity());
                Archetypes.Loaded st = Archetypes.byIndex(s.aux());
                Archetype.Move m = Archetype.Move.byId(s.aux2());
                if (e != null && st != null && m != null) ArmsFx.move(e, st, m, 0, false);
            }
            case ArmsPayloads.HIT -> {
                Entity by = mc.level.getEntity(s.other());
                Archetypes.Loaded st = by instanceof net.minecraft.world.entity.LivingEntity l ? Arms.styleOf(l.getMainHandItem()) : null;
                if (by != null) hitFx(by, s.entity(), st, s.aux2());
                CombatFeedback.hitstop(s.entity(), s.aux());
            }
            case ArmsPayloads.BREAK -> BROKEN.put(s.entity(), ticks + s.aux());
            case ArmsPayloads.POSTURE -> ArmsStances.onSnapshot(s.entity(), s.aux() / 10F, s.aux2(), s.other());
            default -> { }
        }
    }

    static void reset() {
        move = null;
        style = null;
        step = -1;
        buffered = null;
        pressHeld = charging = dodging = false;
        dodgeCharges = -1;
        resonance = 0;
        BROKEN.clear();
        ArmsFeel.reset();
        ArmsFx.reset();
        ArmsStances.reset();
    }
}
