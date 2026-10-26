package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsConfig;
import dev.awaken.arms.ArmsMastery;
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
 * The client's half of Arms (SPEC §25): it reads the three verbs, predicts the move with the same rules the server
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
    /** Each style's mastery step, by its index (ArmsMastery; the server sends them). */
    static int[] mastery = new int[0];
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

    /** In a fight now, as the shoulder camera sees it: a fighting weapon in hand and a blow either way in the last 3 s. */
    static boolean engaged() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && ticks - Math.max(fightAt, hurtAt) < 60 && fights(p) && ArmsComfort.combatCamera();
    }

    /** Presses taken so far (the autopilot's trace). */
    static int presses;

    /** The attack key has stayed down since the last press it gave (cleared at a tick's end once it's up), and that press's tick. */
    private static boolean attackHeld;
    private static int pressedAt = -1000;

    /**
     * The attack key: every new press is a strike, wherever it points; outside a fight a block the blade itself cuts
     * faster than a hand (a web, bamboo, leaves) is still cut. Vanilla asks again every tick the key stays down over a
     * block (its mining): that is a hold, never another press (each one restarted the swing, and a held heavy shook in
     * place, seen by Euan 2026-10-25).
     */
    static void onKey(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !fights(p)) return;
        if (ticks - fightAt > 100 && cuts(mc, p)) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (attackHeld || ticks == pressedAt) return;
        pressedAt = ticks;
        attackHeld = true;
        press(p, mc);
    }

    /** The crosshair is on a block the weapon in hand breaks faster than a bare hand (survival only). */
    private static boolean cuts(Minecraft mc, LocalPlayer p) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc.level == null) return false;
        if (p.isCreative()) return false; // a sword breaks nothing in creative
        return p.getMainHandItem().getDestroySpeed(mc.level.getBlockState(hit.getBlockPos())) > 1.0F;
    }

    static void press(LocalPlayer p, Minecraft mc) {
        Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
        if (st == null) return;
        int target = mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getId() : -1;
        int s = nextSeq();
        presses++;
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
        int steps = mastery(st);
        if (slip != null && ticks - dodgeAt >= 1 && ticks - dodgeAt <= st.def().dodge().ticks() + slip.window() + ArmsMastery.slipcutBonus(steps)) {
            dodgeAt = -1000;
            begin(p, st, Archetype.Move.SLIPCUT, -1, 0, s);
            return;
        }
        if (st.move(Archetype.Move.UNDERCUT) != null && Body.isSliding(p)) {
            begin(p, st, Archetype.Move.UNDERCUT, -1, 0, s);
            return;
        }
        if (st.move(Archetype.Move.PLUNGE) != null && ArmsRules.plunges(p.onGround(), p.fallDistance, p.isInLiquid())) {
            // armed: the blade raised, a dive to the ground, the blow where it lands
            plunging = true;
            plungeSeq = s;
            style = st;
            move = null;
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.6, Math.min(v.y, -1.1), v.z * 0.6);
            return;
        }
        ArmsRules.Decision d = ArmsRules.tap(st, style == st ? move : null, moveStart, step, cadence, ticks, ArmsMastery.strikes(steps));
        if (d == null) return;
        if (d.startAt() > ticks) {
            buffered = d;
            bufferedSeq = s;
            return;
        }
        begin(p, st, d.move(), d.step(), d.cadence(), s);
    }

    static int mastery(Archetypes.Loaded st) {
        return st.index() < mastery.length ? mastery[st.index()] : 0;
    }

    static void onMastery(ArmsPayloads.Mastery msg) {
        int[] m = new int[msg.steps().size()];
        for (int i = 0; i < m.length; i++) m[i] = msg.steps().get(i);
        mastery = m;
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
        ArmsBodies.move(p, st, m);
        if (m == Archetype.Move.ART) ArmsFeel.art();
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
        ArmsTells.tick();
        ArmsStances.tick();
        ArmsBodies.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (plunging) {
            Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
            if (st == null || p.isInLiquid() || p.getAbilities().flying) plunging = false;
            else if (p.onGround()) {
                plunging = false;
                begin(p, st, Archetype.Move.PLUNGE, -1, 0, plungeSeq);
                CombatFeedback.kick(0F, 3F, 0F, 8);
            }
        }
        if (buffered != null && ticks >= buffered.startAt()) {
            ArmsRules.Decision d = buffered;
            Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
            buffered = null;
            if (st != null) begin(p, st, d.move(), d.step(), d.cadence(), bufferedSeq);
        }
        if (move != null && style != null && !charging) {
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
        if (!mc.options.keyAttack.isDown()) attackHeld = false;
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
                ArmsBodies.charge(p);
            }
            if (charging && c.min() + (ticks - chargeStart) == c.glint()) ArmsAudio.glint(p);
            if (!charging || c.min() + (ticks - chargeStart) < c.max()) return;
        }
        pressHeld = false;
        if (!charging) return;
        charging = false;
        int target = mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getId() : -1;
        ClientPacketDistributor.sendToServer(new ArmsPayloads.Press(seq, ArmsPayloads.PRESS_RELEASE, p.getYRot(), p.getXRot(), target));
        int held = c.min() + (ticks - chargeStart);
        if (st.move(Archetype.Move.ART) != null && ArmsRules.artReady(held, c, resonance, ArmsMastery.artAt(mastery(st)))) {
            begin(p, st, Archetype.Move.ART, -1, 0, seq); // held through with the meter full: the Art
            return;
        }
        boolean execution = target >= 0 && BROKEN.containsKey(target) && st.move(Archetype.Move.EXECUTION) != null;
        begin(p, st, execution ? Archetype.Move.EXECUTION : Archetype.Move.HEAVY, -1, 0, seq, !execution && c.perfect(held));
    }

    /** The running heavy was released on its glint (its arc burns gold). */
    static boolean perfectHeavy;
    /** A Plunge armed in a fall (the blade held high until it lands), and its press. */
    static boolean plunging;
    static int plungeSeq;

    /** How far a held heavy has charged (0..1, the glint at its mark), or -1 when not charging. */
    static float charge() {
        if (!charging || style == null) return -1;
        Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
        if (heavy == null || heavy.charge().isEmpty()) return -1;
        Archetype.Charge c = heavy.charge().get();
        return Mth.clamp((c.min() + (ticks - chargeStart)) / (float) c.max(), 0, 1);
    }

    /** Whether a held heavy is on its glint right now (a release would be perfect). */
    static boolean glinting() {
        if (!charging || style == null) return false;
        Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
        return heavy != null && heavy.charge().isPresent() && heavy.charge().get().perfect(heavy.charge().get().min() + (ticks - chargeStart));
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
        ArmsBodies.dodge(p, dodgeX, dodgeZ);
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
                    ArmsBodies.settle(p);
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
                    if (m == Archetype.Move.EXECUTION) ArmsFeel.executed();
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
                ArmsFeel.deflected();
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
                ArmsFeel.guardBroken();
                ArmsAudio.guardBreak(p);
            }
            case ArmsPayloads.PERFECT_DODGE -> {
                fightAt = ticks;
                CombatFeedback.hitstop(r.entity(), 60);
                ArmsFeel.breath();
                ArmsAudio.perfectDodge(p);
            }
            case ArmsPayloads.BREAK -> {
                BROKEN.put(r.entity(), ticks + r.aux());
                CombatFeedback.hitstop(r.entity(), 160);
                CombatFeedback.hold(120);
                CombatFeedback.kick(0F, 2.5F, 0F, 10);
                ArmsFeel.broke();
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
                if (e != null && st != null && m != null) {
                    ArmsFx.move(e, st, m, 0, false);
                    ArmsBodies.move(e, st, m);
                }
            }
            case ArmsPayloads.CHARGE -> {
                Entity e = mc.level.getEntity(s.entity());
                if (e != null) ArmsBodies.charge(e);
            }
            case ArmsPayloads.DODGE -> {
                Entity e = mc.level.getEntity(s.entity());
                float a = s.aux() * Mth.DEG_TO_RAD;
                if (e != null) ArmsBodies.dodge(e, Mth.cos(a), Mth.sin(a));
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
        pressHeld = charging = dodging = plunging = false;
        dodgeCharges = -1;
        resonance = 0;
        BROKEN.clear();
        attackHeld = false;
        BladeTrail.reset();
        ArmsTells.reset();
        ArmsFeel.reset();
        ArmsFx.reset();
        ArmsStances.reset();
        ArmsBodies.reset();
    }
}
