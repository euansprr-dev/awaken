package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.Strikes;
import dev.awaken.body.Body;
import dev.awaken.veil.Veil;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The server half of Arms (SPEC §25). Presses are judged against the server's own clock and the style's row; a
 * move's active ticks sweep its arc and strike each creature in it once through vanilla's own attack (so every
 * damage listener, crit, enchantment and momentum hit still applies), scaled first by the move. Blows on a fighter
 * are answered here: a dodge's evade ticks, a deflect or block on the guard, and the latency grace that lets a guard
 * timed to the screen count. Stances fill, drain and break here.
 */
final class ArmsServer {
    private ArmsServer() {}

    static final Identifier FULL_STRENGTH = dev.awaken.Awaken.id("arms_full_strength");
    private static final AttributeModifier FULL_STRENGTH_MOD = new AttributeModifier(FULL_STRENGTH, 1024, AttributeModifier.Operation.ADD_VALUE);

    // the Arms hit being resolved (one at a time on the server thread)
    private static int pendingBy = -1, pendingTarget = -1;
    private static float pendingMotion;
    private static int pendingPosture;
    private static float pendingCap;
    private static boolean pendingLanded;
    // a blow re-applied after its latency grace: how long it waited, and its class
    private static int heldTicks;
    private static Strikes.@Nullable Kind heldKind;
    private static boolean reapplying;

    /** A guardable blow waiting out the latency grace. */
    private record Hold(ServerPlayer victim, DamageSource source, float amount, long at, int grace, Strikes.Kind kind) {}

    private static final List<Hold> HOLDS = new ArrayList<>();

    /** Attackers slowed by a perfect dodge: entity id → the tick the beat ends. */
    private static final Map<Integer, Long> SLOWED = new HashMap<>();

    static long now(Entity e) {
        var server = e.level().getServer();
        return server != null ? server.overworld().getGameTime() : e.level().getGameTime();
    }

    static ArmsState state(ServerPlayer p) {
        return p.getData(ArmsContent.STATE);
    }

    /** No move starts while riding, sleeping, out of the body, meditating, flying or gliding. */
    static boolean off(ServerPlayer p) {
        return p.isPassenger() || p.isSleeping() || p.isSpectator() || !p.isAlive() || p.getAbilities().flying || p.isFallFlying()
                || Veil.isSpirit(p) || Veil.isMeditating(p);
    }

    // ---- presses ----------------------------------------------------------------------------------------------------

    static void onPress(ServerPlayer p, ArmsPayloads.Press msg) {
        ArmsState s = state(p);
        if (msg.seq() < s.seq) return; // stale
        if (!Arms.fights(p) || off(p)) {
            refuse(p, msg.seq());
            return;
        }
        long now = now(p);
        Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
        if (style == null) return;
        switch (msg.what()) {
            case ArmsPayloads.PRESS_STRIKE -> {
                s.seq = msg.seq();
                s.aimed = msg.target();
                strike(p, s, style, now, msg.seq());
            }
            case ArmsPayloads.PRESS_CHARGE -> {
                if (msg.seq() != s.seq) return;
                s.chargeStart = now;
                s.chargeSeq = msg.seq();
                seen(p, new ArmsPayloads.Seen(p.getId(), ArmsPayloads.CHARGE, -1, 0, 0));
            }
            case ArmsPayloads.PRESS_RELEASE -> {
                if (msg.seq() != s.chargeSeq || s.chargeStart < 0) return;
                Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
                if (heavy == null || heavy.charge().isEmpty()) return;
                int held = heavy.charge().get().min() + (int) (now - s.chargeStart);
                s.chargeStart = -1;
                if (style.move(Archetype.Move.ART) != null && ArmsRules.artReady(held, heavy.charge().get(), s.resonance)) {
                    resonance(p, s, -ArmsRules.RESONANCE_MAX);
                    begin(p, s, style, Archetype.Move.ART, -1, 0, now, msg.seq());
                    return;
                }
                heavy(p, s, style, heavy, Math.min(held, heavy.charge().get().max()), now, msg.seq());
            }
            default -> { }
        }
    }

    /**
     * A tap: a riposte or a Slipcut when one is open, an Undercut out of a slide, a Plunge armed in a fall, else the
     * chain's next strike (now, or buffered for its window).
     */
    static void strike(ServerPlayer p, ArmsState s, Archetypes.Loaded style, long now, int seq) {
        Archetype.MoveDef riposte = style.move(Archetype.Move.RIPOSTE);
        if (riposte != null && now - s.deflectAt <= riposte.window()) {
            s.deflectAt = Long.MIN_VALUE / 2;
            begin(p, s, style, Archetype.Move.RIPOSTE, -1, 0, now, seq);
            return;
        }
        Archetype.MoveDef slip = style.move(Archetype.Move.SLIPCUT);
        if (slip != null && now - s.dodgeAt <= style.def().dodge().ticks() + slip.window() && now - s.dodgeAt >= 1) {
            s.dodgeAt = Long.MIN_VALUE / 2;
            begin(p, s, style, Archetype.Move.SLIPCUT, -1, 0, now, seq);
            return;
        }
        if (style.move(Archetype.Move.UNDERCUT) != null && Body.isSliding(p)) {
            begin(p, s, style, Archetype.Move.UNDERCUT, -1, 0, now, seq);
            return;
        }
        if (style.move(Archetype.Move.PLUNGE) != null && ArmsRules.plunges(p.onGround(), p.fallDistance, p.isInLiquid())) {
            s.plungeSeq = seq;
            s.plungeAt = now;
            s.plungeFall = p.fallDistance;
            return; // it strikes where it lands
        }
        ArmsRules.Decision d = ArmsRules.tap(style, s.style == style ? s.move : null, s.moveStart, s.step, s.cadence, now, 0);
        if (d == null) return; // too early: a mash far ahead of the window counts for nothing
        if (d.startAt() > now) {
            s.buffered = d;
            s.bufferedSeq = seq;
            return;
        }
        begin(p, s, style, d.move(), d.step(), d.cadence(), now, seq);
    }

    static void heavy(ServerPlayer p, ArmsState s, Archetypes.Loaded style, Archetype.MoveDef heavy, int held, long now, int seq) {
        LivingEntity target = target(p, s.aimed);
        Archetype.MoveDef exec = style.move(Archetype.Move.EXECUTION);
        if (exec != null && target != null && Arms.broken(target) && p.distanceTo(target) <= exec.reach() + target.getBbWidth()) {
            begin(p, s, style, Archetype.Move.EXECUTION, -1, 0, now, seq);
            return;
        }
        begin(p, s, style, Archetype.Move.HEAVY, -1, 0, now, seq);
        s.perfectHeavy = heavy.charge().isPresent() && heavy.charge().get().perfect(held);
    }

    static void begin(ServerPlayer p, ArmsState s, Archetypes.Loaded style, Archetype.Move move, int step, int cadence, long now, int seq) {
        Archetype.MoveDef def = style.move(move);
        if (def == null) return;
        s.style = style;
        s.move = move;
        s.step = step;
        s.cadence = cadence;
        s.moveSeq = seq;
        s.moveStart = now;
        s.recovery = ArmsRules.recovery(def, style, p.getAttributeValue(Attributes.ATTACK_SPEED));
        s.moveEnd = now + def.windup() + def.active() + s.recovery;
        s.hitCount = 0;
        s.perfectHeavy = false;
        s.buffered = null;
        s.fightAt = now;
        if (cadence == ArmsRules.CADENCE_MAX && def.finisher()) resonance(p, s, ArmsRules.RESONANCE_CADENCE3);
        AwakenNet.send(p, new ArmsPayloads.Result(seq, ArmsPayloads.MOVE, -1, move.ordinal(), cadence));
        p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true); // the vanilla arm for anyone without the clips
        soundForOthers(p, ArmsSounds.swish(def), 1.0F, ArmsSounds.swishPitch(cadence));
        seen(p, new ArmsPayloads.Seen(p.getId(), ArmsPayloads.MOVE, -1, style.index(), move.ordinal()));
    }

    static void refuse(ServerPlayer p, int seq) {
        AwakenNet.send(p, new ArmsPayloads.Result(seq, ArmsPayloads.REFUSE, -1, 0, 0));
    }

    // ---- the move's clock ---------------------------------------------------------------------------------------------

    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ArmsState s = p.getExistingDataOrNull(ArmsContent.STATE);
        if (s == null) return;
        long now = now(p);
        if (s.buffered != null && now >= s.buffered.startAt()) {
            ArmsRules.Decision d = s.buffered;
            Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
            s.buffered = null;
            if (style != null && Arms.fights(p) && !off(p)) begin(p, s, style, d.move(), d.step(), d.cadence(), now, s.bufferedSeq);
        }
        if (s.move != null && s.style != null) {
            Archetype.MoveDef def = s.style.move(s.move);
            long rel = now - s.moveStart;
            if (def != null && rel >= def.windup() && rel < def.activeEnd() && Arms.fights(p)) sweep(p, s, def, now);
        }
        if (s.focus >= 0 && (now & 3) == 0) holdFocus(p, s, now);
        if (s.plungeSeq >= 0) plunge(p, s, now);
        if (s.guardBreak) {
            s.guardBreak = false;
            breakGuard(p, s);
        }
        if (s.dodgeCharges >= 0 && s.style != null && now >= s.dodgeRechargeAt && s.dodgeCharges < s.style.def().dodge().charges()) {
            s.dodgeCharges++;
            s.dodgeRechargeAt = now + s.style.def().dodge().recharge();
        }
        if (s.resonance > 0 && now - s.fightAt > 100 && (now & 19) == 0) resonance(p, s, -ArmsRules.RESONANCE_DRAIN);
    }

    /** The arc's work for one active tick: every creature in reach and in the arc, once. */
    static void sweep(ServerPlayer p, ArmsState s, Archetype.MoveDef def, long now) {
        ServerLevel level = p.level();
        double reach = def.reachAt(now - s.moveStart) + 0.3;
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot() + focusTurn(p, s);
        AABB box = p.getBoundingBox().inflate(reach + 1, 1.5, reach + 1);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, t -> t != p && t.isAlive() && !s.hasHit(t.getId()))) {
            boolean aimed = t.getId() == s.aimed;
            if (!aimed && !foe(p, t)) continue;
            Vec3 c = t.getBoundingBox().getCenter();
            Vec3 closest = new Vec3(Mth.clamp(eye.x, t.getBoundingBox().minX, t.getBoundingBox().maxX),
                    Mth.clamp(eye.y, t.getBoundingBox().minY, t.getBoundingBox().maxY), Mth.clamp(eye.z, t.getBoundingBox().minZ, t.getBoundingBox().maxZ));
            if (closest.distanceTo(eye) > reach) continue;
            float toward = (float) (Mth.atan2(c.z - p.getZ(), c.x - p.getX()) * Mth.RAD_TO_DEG) - 90F;
            if (!aimed && !ArmsRules.inArc(toward - yaw, def.arc())) continue;
            if (t.getBoundingBox().maxY < p.getY() - 1.0 || t.getBoundingBox().minY > p.getEyeY() + 1.5) continue;
            s.markHit(t.getId());
            hit(p, s, def, t, now);
        }
    }

    /** A Plunge in the air: it lands as the move (all round, harder the further it fell), or gives up in water or after 5 s. */
    static void plunge(ServerPlayer p, ArmsState s, long now) {
        Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
        if (style == null || !Arms.fights(p) || off(p) || p.isInLiquid() || now - s.plungeAt > 100) {
            s.plungeSeq = -1;
            return;
        }
        s.plungeFall = Math.max(s.plungeFall, p.fallDistance);
        if (!p.onGround()) return;
        s.plungePower = ArmsRules.plungePower(s.plungeFall);
        int seq = s.plungeSeq;
        s.plungeSeq = -1;
        begin(p, s, style, Archetype.Move.PLUNGE, -1, 0, now, seq);
    }

    /** A Plunge's landing takes half the fall: the blade went into the ground first. */
    static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ArmsState s = p.getExistingDataOrNull(ArmsContent.STATE);
        if (s != null && (s.plungeSeq >= 0 || (s.move == Archetype.Move.PLUNGE && now(p) - s.moveStart <= 1))) {
            e.setDamageMultiplier(e.getDamageMultiplier() * 0.5F);
        }
    }

    /** The focus lets go when it's gone or the fighter has looked well away from it for a second. */
    private static void holdFocus(ServerPlayer p, ArmsState s, long now) {
        LivingEntity f = target(p, s.focus);
        if (f == null) {
            s.focus = -1;
            return;
        }
        float toward = (float) (Mth.atan2(f.getZ() - p.getZ(), f.getX() - p.getX()) * Mth.RAD_TO_DEG) - 90F;
        if (ArmsRules.lookedAt(toward, p.getYRot())) s.focusSeenAt = now;
        else if (now - s.focusSeenAt >= ArmsRules.FOCUS_DROP_TICKS) s.focus = -1;
    }

    private static float focusTurn(ServerPlayer p, ArmsState s) {
        LivingEntity f = target(p, s.focus >= 0 ? s.focus : s.aimed);
        if (f == null) return 0;
        float toward = (float) (Mth.atan2(f.getZ() - p.getZ(), f.getX() - p.getX()) * Mth.RAD_TO_DEG) - 90F;
        return ArmsRules.focusTurn(toward, p.getYRot());
    }

    /** What a sweep may strike besides the aimed creature: hostiles and anything hunting the player; never a person. */
    static boolean foe(ServerPlayer p, LivingEntity t) {
        if (t instanceof Player || t instanceof Npc) return false;
        if (t instanceof OwnableEntity o && o.getOwner() == p) return false;
        if (t instanceof Mob m && m.getTarget() == p) return true;
        return t instanceof Enemy;
    }

    static @Nullable LivingEntity target(ServerPlayer p, int id) {
        if (id < 0) return null;
        return p.level().getEntity(id) instanceof LivingEntity l && l.isAlive() && l.distanceToSqr(p) < 64 ? l : null;
    }

    /** Strikes one creature through vanilla's attack at full strength; the Arms stage scales it by the move. */
    static void hit(ServerPlayer p, ArmsState s, Archetype.MoveDef def, LivingEntity t, long now) {
        AttributeInstance speed = p.getAttribute(Attributes.ATTACK_SPEED);
        pendingBy = p.getId();
        pendingTarget = t.getId();
        pendingMotion = ArmsRules.motion(def, s.cadence, s.perfectHeavy) * (s.move == Archetype.Move.PLUNGE ? s.plungePower : 1);
        pendingPosture = ArmsRules.posture(def, s.cadence, s.perfectHeavy);
        pendingCap = def.bossCap();
        pendingLanded = false;
        // a combo's blows come faster than vanilla's half second of immunity: each move strikes each creature once
        if (t.getLastHurtByMob() == p) t.damageCooldownTime = 0;
        try {
            if (speed != null) speed.addOrUpdateTransientModifier(FULL_STRENGTH_MOD);
            p.attack(t);
        } finally {
            if (speed != null) speed.removeModifier(FULL_STRENGTH);
            pendingBy = pendingTarget = -1;
        }
        if (!pendingLanded) return;
        p.level().playSound(p, t.getX(), t.getY(0.6), t.getZ(), ArmsSounds.impact(t, s.move, def), SoundSource.PLAYERS, 1.0F, ArmsSounds.impactPitch(t, def));
        s.fightAt = now;
        if (s.focus != t.getId()) s.focus = t.getId();
        s.focusSeenAt = now;
        int ms = s.perfectHeavy && def.charge().isPresent() ? def.charge().get().glintHitstop() : def.hitstop();
        if (now - s.lastHitstopAt < ArmsRules.HITSTOP_GAP) ms = 0;
        else s.lastHitstopAt = now;
        AwakenNet.send(p, new ArmsPayloads.Result(s.moveSeq, ArmsPayloads.HIT, t.getId(), ms, s.move == null ? 0 : s.move.ordinal()));
        seen(p, new ArmsPayloads.Seen(t.getId(), ArmsPayloads.HIT, p.getId(), ms, s.move == null ? 0 : s.move.ordinal()));
        if (def.finisher()) resonance(p, s, ArmsRules.RESONANCE_FINISHER);
    }

    // ---- damage -------------------------------------------------------------------------------------------------------

    /** The Arms stage, first of every listener: an Arms hit is scaled by its move; blows on a fighter are answered. */
    static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        DamageSource src = e.getSource();
        Entity by = src.getEntity();
        if (by != null && by.getId() == pendingBy && victim.getId() == pendingTarget && src.getDirectEntity() == by) {
            float amount = e.getAmount() * pendingMotion;
            Posture post = posture(victim);
            amount = ArmsRules.capExecution(amount, victim.getMaxHealth(), pendingCap, ArmsRules.lordly(post.max));
            e.setAmount(amount);
            pendingLanded = true;
            if (by instanceof ServerPlayer sp) addPosture(victim, pendingPosture, sp);
            return;
        }
        if (!(victim instanceof ServerPlayer p) || !Arms.fights(p) || reapplying) return;
        Strikes.Kind kind = Strikes.of(src);
        if (kind == Strikes.Kind.NONE) return;
        ArmsState s = state(p);
        long now = now(p);
        s.fightAt = now;
        Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
        if (style == null) return;
        Archetype.Dodge dodge = style.def().dodge();
        long since = now - s.dodgeAt;
        if (ArmsRules.evades(dodge, (int) Math.min(Integer.MAX_VALUE, since), kind)) {
            e.setCanceled(true);
            boolean perfect = ArmsRules.perfect(dodge, (int) since) && by instanceof LivingEntity;
            if (perfect) perfectDodge(p, s, (LivingEntity) by, dodge);
            else AwakenNet.send(p, new ArmsPayloads.Result(s.seq, ArmsPayloads.EVADE, by == null ? -1 : by.getId(), 0, 0));
            return;
        }
        int grace = grace(p);
        if (grace > 0 && kind.deflectable() && by != null && !p.isBlocking() && Arms.guards(p.getMainHandItem())) {
            e.setCanceled(true);
            HOLDS.add(new Hold(p, src, e.getAmount(), now, grace, kind));
        }
    }

    /** The guard's answer: deflect in its first ticks, block after, a broken guard on a heavy, nothing for the rest. */
    static void onShieldBlock(LivingShieldBlockEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Arms.fights(p) || !Arms.guards(p.getUseItem())) return;
        Archetypes.Loaded style = Arms.styleOf(p.getUseItem());
        if (style == null) return;
        ArmsState s = state(p);
        Strikes.Kind kind = heldKind != null ? heldKind : Strikes.of(e.getDamageSource());
        int grace = grace(p);
        boolean inArc = e.getOriginalBlockedDamage() > 0;
        Archetype.Guard g = style.def().guard();
        ArmsRules.Answer a = ArmsRules.guard(g, kind, p.getTicksUsingItem(), heldTicks, grace, s.lockedOut, inArc);
        float incoming = e.getDamageContainer().getNewDamage();
        Entity by = e.getDamageSource().getEntity();
        long now = now(p);
        switch (a) {
            case DEFLECT -> {
                e.setBlockedDamage(incoming);
                e.setShieldDamage(0);
                s.raiseDeflected = true;
                s.deflectAt = now;
                s.deflected = by == null ? -1 : by.getId();
                resonance(p, s, ArmsRules.RESONANCE_DEFLECT);
                if (by instanceof LivingEntity attacker && e.getDamageSource().getDirectEntity() == by) {
                    addPosture(attacker, kind == Strikes.Kind.HEAVY ? ArmsRules.DEFLECT_POSTURE_HEAVY : ArmsRules.DEFLECT_POSTURE_LIGHT, p);
                }
                soundForOthers(p, ArmsSounds.DEFLECT.get(), 1.0F, 1.0F);
                answer(p, s, ArmsPayloads.DEFLECT, by);
            }
            case BLOCK -> {
                e.setBlockedDamage(incoming * g.reduction());
                soundForOthers(p, ArmsSounds.BLOCK.get(), 1.0F, 1.0F);
                answer(p, s, ArmsPayloads.BLOCK, by);
            }
            case GUARD_BREAK -> {
                e.setBlockedDamage(incoming * g.heavyReduction());
                s.guardBreak = true; // after this blow resolves: the guard drops and can't rise for a moment
                soundForOthers(p, ArmsSounds.GUARD_BREAK.get(), 1.0F, 1.0F);
                answer(p, s, ArmsPayloads.GUARD_BREAK, by);
            }
            case PASS -> e.setBlocked(false);
        }
    }

    static int grace(ServerPlayer p) {
        ArmsState s = p.getExistingDataOrNull(ArmsContent.STATE);
        if (s != null && s.testGrace >= 0) return s.testGrace;
        return ArmsRules.grace(p.connection == null ? 0 : p.connection.latency());
    }

    private static void breakGuard(ServerPlayer p, ArmsState s) {
        Archetypes.Loaded style = s.style != null ? s.style : Arms.styleOf(p.getMainHandItem());
        p.stopUsingItem();
        if (style != null) p.getCooldowns().addCooldown(p.getMainHandItem(), style.def().guard().breakTicks());
    }

    private static void answer(ServerPlayer p, ArmsState s, int what, @Nullable Entity by) {
        int id = by == null ? -1 : by.getId();
        AwakenNet.send(p, new ArmsPayloads.Result(s.seq, what, id, 0, 0));
        seen(p, new ArmsPayloads.Seen(p.getId(), what, id, 0, 0));
    }

    /** Waits out each held blow's grace, then lands it (a guard raised in the meantime meets it). */
    static void onServerTick(ServerTickEvent.Post e) {
        if (!HOLDS.isEmpty()) {
            long now = e.getServer().overworld().getGameTime();
            for (Iterator<Hold> it = HOLDS.iterator(); it.hasNext(); ) {
                Hold h = it.next();
                if (h.victim.isRemoved() || !h.victim.isAlive()) {
                    it.remove();
                    continue;
                }
                if (!h.victim.isBlocking() && now - h.at < h.grace) continue;
                it.remove();
                heldTicks = (int) (now - h.at);
                heldKind = h.kind;
                reapplying = true;
                try {
                    h.victim.hurtServer(h.victim.level(), h.source, h.amount);
                } finally {
                    heldTicks = 0;
                    heldKind = null;
                    reapplying = false;
                }
            }
        }
        if (!SLOWED.isEmpty() && (e.getServer().getTickCount() % 20) == 0) {
            long now = e.getServer().overworld().getGameTime();
            SLOWED.values().removeIf(until -> until < now);
        }
    }

    /** Vanilla's own attack never resolves for an Arms weapon: the moves strike instead. */
    static void onAttack(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && pendingBy != p.getId() && Arms.fights(p)) e.setCanceled(true);
    }

    /** An Arms hit never sweeps: its arc already chose what it hits. */
    static void onSweep(SweepAttackEvent e) {
        if (e.getEntity().getId() == pendingBy) e.setSweeping(false);
    }

    /** An Arms hit makes its own sound by what it meets, so vanilla's attack sounds stay quiet while one resolves. */
    static void onSound(PlayLevelSoundEvent.AtPosition e) {
        if (pendingBy >= 0 && e.getSound() != null && vanillaAttack(e.getSound())) e.setCanceled(true);
    }

    private static boolean vanillaAttack(Holder<SoundEvent> sound) {
        SoundEvent s = sound.value();
        return s == SoundEvents.PLAYER_ATTACK_STRONG || s == SoundEvents.PLAYER_ATTACK_WEAK || s == SoundEvents.PLAYER_ATTACK_CRIT
                || s == SoundEvents.PLAYER_ATTACK_KNOCKBACK || s == SoundEvents.PLAYER_ATTACK_NODAMAGE || s == SoundEvents.PLAYER_ATTACK_SWEEP;
    }

    /** A blow that lands on a player tells their client where it came from and how hard (the hurt's sound and kick). */
    static void onDamaged(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !ArmsConfig.enabled() || e.getInflictedDamage() <= 0) return;
        if (Strikes.of(e.getSource()) == Strikes.Kind.NONE) return;
        Vec3 from = e.getSource().getSourcePosition();
        int yaw = from == null ? Integer.MIN_VALUE : (int) (Mth.atan2(from.z - p.getZ(), from.x - p.getX()) * Mth.RAD_TO_DEG) - 90;
        Entity by = e.getSource().getEntity();
        AwakenNet.send(p, new ArmsPayloads.Result(state(p).seq, ArmsPayloads.HURT, by == null ? -1 : by.getId(), yaw,
                Math.round(e.getInflictedDamage() * 10)));
    }

    // ---- guard raises -------------------------------------------------------------------------------------------------

    static void onUseStart(LivingEntityUseItemEvent.Start e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Arms.guards(e.getItem())) return;
        ArmsState s = state(p);
        Archetypes.Loaded style = Arms.styleOf(e.getItem());
        long now = now(p);
        int lockout = style == null ? 0 : style.def().guard().lockout();
        // a raise right after one that deflected nothing can only block: spamming the guard isn't a deflect
        s.lockedOut = !s.raiseDeflected && now - s.loweredAt <= lockout;
        s.raisedAt = now;
        s.raiseDeflected = false;
    }

    static void onUseStop(LivingEntityUseItemEvent.Stop e) {
        if (e.getEntity() instanceof ServerPlayer p && Arms.guards(e.getItem())) state(p).loweredAt = now(p);
    }

    // ---- dodge --------------------------------------------------------------------------------------------------------

    static void onDodge(ServerPlayer p, ArmsPayloads.Dodge msg) {
        ArmsState s = state(p);
        long now = now(p);
        Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
        boolean engaged = style != null || now - s.fightAt < 100;
        if (style == null) style = Archetypes.get(Arms.LONGSWORD);
        if (style == null || !ArmsConfig.enabled() || !engaged || off(p) || !p.onGround() || p.isInLiquid()
                || p.position().distanceTo(new Vec3(msg.x(), msg.y(), msg.z())) > 1.5) {
            refuseDodge(p, msg.seq());
            return;
        }
        Archetype.Dodge d = style.def().dodge();
        if (s.dodgeCharges < 0) s.dodgeCharges = d.charges();
        if (s.dodgeCharges <= 0 || now - s.dodgeAt < d.ticks()) {
            refuseDodge(p, msg.seq());
            return;
        }
        // a move in its wind-up or strike can't be dodged out of; its recovery can (the rhythm is lost)
        if (s.move != null && s.style != null && s.moving(now)) {
            Archetype.MoveDef def = s.style.move(s.move);
            if (def != null && now - s.moveStart < def.activeEnd()) {
                refuseDodge(p, msg.seq());
                return;
            }
        }
        if (s.dodgeCharges == d.charges()) s.dodgeRechargeAt = now + d.recharge();
        s.dodgeCharges--;
        s.dodgeAt = now;
        float len = Mth.sqrt(msg.dx() * msg.dx() + msg.dz() * msg.dz());
        s.dodgeX = len > 1e-4 ? msg.dx() / len : 0;
        s.dodgeZ = len > 1e-4 ? msg.dz() / len : 0;
        if (s.style == null) s.style = style;
        s.clearMove();
        s.cadence = 0;
        s.buffered = null;
        p.causeFoodExhaustion(0.1F);
        soundForOthers(p, ArmsSounds.DODGE.get(), 1.0F, 1.0F);
        seen(p, new ArmsPayloads.Seen(p.getId(), ArmsPayloads.DODGE, -1, (int) (Mth.atan2(s.dodgeZ, s.dodgeX) * Mth.RAD_TO_DEG), 0));
    }

    private static void refuseDodge(ServerPlayer p, int seq) {
        AwakenNet.send(p, new ArmsPayloads.Result(seq, ArmsPayloads.REFUSE, -2, 0, 0));
        if (p.connection == null) return;
        Vec3 v = new Vec3(0, p.getDeltaMovement().y, 0);
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(), v));
    }

    private static void perfectDodge(ServerPlayer p, ArmsState s, LivingEntity attacker, Archetype.Dodge d) {
        long now = now(p);
        SLOWED.put(attacker.getId(), now + d.slow());
        resonance(p, s, ArmsRules.RESONANCE_PERFECT_DODGE);
        AwakenNet.send(p, new ArmsPayloads.Result(s.seq, ArmsPayloads.PERFECT_DODGE, attacker.getId(), d.slow(), 0));
    }

    /** The perfect dodge's beat: the attacker alone runs at half speed (every other tick). */
    static void onEntityTick(EntityTickEvent.Pre e) {
        if (SLOWED.isEmpty() || e.getEntity() instanceof Player) return;
        Long until = SLOWED.get(e.getEntity().getId());
        if (until == null) return;
        long now = now(e.getEntity());
        if (now >= until) return;
        if (((now + e.getEntity().getId()) & 1) != 0) {
            e.setCanceled(true);
            if (e.getEntity() instanceof LivingEntity l && l.damageCooldownTime > 0) l.damageCooldownTime--;
        }
    }

    // ---- posture ------------------------------------------------------------------------------------------------------

    static Posture posture(LivingEntity e) {
        Posture p = e.getData(ArmsContent.POSTURE);
        if (p.max <= 0) {
            MobRole role = e.getExistingDataOrNull(CoreApi.MOB_ROLE.get());
            p.max = ArmsRules.postureMax(CreatureTag.of(e), role == null ? MobRole.Role.NONE : role.role());
        }
        return p;
    }

    /** Fills a stance; full, it breaks: the wind-up ends, the body sags, a heavy becomes an Execution. */
    static void addPosture(LivingEntity target, int amount, ServerPlayer by) {
        if (amount <= 0 || target instanceof Player) return;
        Posture p = posture(target);
        long now = now(target);
        boolean lord = ArmsRules.lordly(p.max);
        p.settle(now, lord);
        if (p.broken(now)) return;
        p.value = Math.min(p.max, p.value + amount);
        p.lastHitAt = now;
        boolean breaks = p.value >= p.max && now >= p.nextBreakAt;
        if (!breaks) stance(target, p, now);
        if (breaks) {
            int ticks = lord ? ArmsRules.BROKEN_TICKS_LORD : ArmsRules.BROKEN_TICKS;
            p.brokenUntil = now + ticks;
            p.nextBreakAt = now + ArmsRules.BREAK_COOLDOWN;
            p.value = 0;
            stance(target, p, now);
            Strikes.stagger(target, ticks);
            sound(target, ArmsSounds.BREAK.get(), 1.0F, 1.0F);
            resonance(by, state(by), ArmsRules.RESONANCE_BREAK);
            AwakenNet.send(by, new ArmsPayloads.Result(state(by).seq, ArmsPayloads.BREAK, target.getId(), ticks, 0));
            seen(by, new ArmsPayloads.Seen(target.getId(), ArmsPayloads.BREAK, by.getId(), ticks, 0));
        }
    }

    /** Everyone near the creature (the fighter too) gets its stance on every change; their clients drain it themselves. */
    private static void stance(LivingEntity target, Posture p, long now) {
        int broken = (int) Math.max(0, p.brokenUntil - now);
        ArmsPayloads.Seen msg = new ArmsPayloads.Seen(target.getId(), ArmsPayloads.POSTURE, broken, Math.round(p.value * 10), p.max);
        if (!(target.level() instanceof ServerLevel level)) return;
        for (ServerPlayer o : level.players()) if (o.distanceToSqr(target) < 48 * 48) AwakenNet.send(o, msg);
    }

    // ---- resonance ----------------------------------------------------------------------------------------------------

    static void resonance(ServerPlayer p, ArmsState s, float delta) {
        float was = s.resonance;
        s.resonance = Mth.clamp(s.resonance + delta, 0, ArmsRules.RESONANCE_MAX);
        if ((int) was != (int) s.resonance) AwakenNet.send(p, new ArmsPayloads.Result(s.seq, ArmsPayloads.RESONANCE, -1, (int) s.resonance, 0));
    }

    // ---- housekeeping -------------------------------------------------------------------------------------------------

    static void onLeave(EntityLeaveLevelEvent e) {
        if (!SLOWED.isEmpty()) SLOWED.remove(e.getEntity().getId());
        if (e.getEntity() instanceof ServerPlayer p) HOLDS.removeIf(h -> h.victim == p);
    }

    private static void seen(ServerPlayer p, ArmsPayloads.Seen msg) {
        for (ServerPlayer o : p.level().players()) {
            if (o != p && o.distanceToSqr(p) < 48 * 48) AwakenNet.send(o, msg);
        }
    }

    /** Heard by everyone near but the fighter, whose client already played it on its own prediction or result. */
    private static void soundForOthers(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(p, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Tests: the hold queue is empty between tests. */
    /** Tests: forget what was held for this player (tests run side by side, so never anyone else's). */
    static void clear(ServerPlayer p) {
        HOLDS.removeIf(h -> h.victim == p);
    }

    static boolean slowed(Entity e) {
        Long until = SLOWED.get(e.getId());
        return until != null && until > now(e);
    }

    static int holds(ServerPlayer p) {
        int n = 0;
        for (Hold h : HOLDS) if (h.victim == p) n++;
        return n;
    }
}
