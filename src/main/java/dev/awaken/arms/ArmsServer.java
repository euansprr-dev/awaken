package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.Aim;
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
import net.minecraft.tags.DamageTypeTags;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
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
    // its push (SPEC §25 "Fight Feel"): the flat way the blade went, and how hard against vanilla's strength
    private static final float[] PUSH = new float[2];
    private static float pendingPushScale;
    private static boolean pendingPush;
    // a blow re-applied after its latency grace: how long it waited, and its class
    private static int heldTicks;
    private static Strikes.@Nullable Kind heldKind;
    private static boolean reapplying;

    /** A guardable blow waiting out the latency grace. */
    private record Hold(ServerPlayer victim, DamageSource source, float amount, long at, int grace, Strikes.Kind kind) {}

    private static final List<Hold> HOLDS = new ArrayList<>();

    /**
     * A shot the guard stopped this tick (SPEC §25 "Shots"): at the tick's end, after its own hit has run, it leaves
     * the blade ({@code from}: the way it came, flat), and a block's share ({@code through}) comes through the guard.
     */
    private record Stopped(ServerPlayer victim, @Nullable Projectile shot, Vec3 from, boolean deflected, float through, DamageSource source) {}

    private static final List<Stopped> STOPPED = new ArrayList<>();
    // a stopped shot's share coming through the guard: the guard lets it by, and it's no new blow on the fighter
    private static boolean chipping;
    private static @Nullable LivingEntity chipVictim;
    private static float chipShare;

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
                s.aimed = Aim.meantId(p, msg.target(), msg.yaw(), msg.pitch(), pressReach(style));
                // the press is down: if it is held into a charge, the strike it begins must never land (Euan
                // 2026-10-26: "when you're charging up, it attacks once and then charges up")
                s.holdSeq = msg.seq();
                s.holdFrom = now;
                s.holdOpen = chargeable(style);
                strike(p, s, style, now, msg.seq());
            }
            case ArmsPayloads.PRESS_TAP -> {
                if (msg.seq() != s.holdSeq) return;
                s.holdOpen = false;
                s.waitHit = false; // a tap: the strike lands as it comes
                if (s.waitSeen) shown(p, s);
            }
            case ArmsPayloads.PRESS_CHARGE -> {
                if (msg.seq() != s.seq) return;
                if (s.waitHit && s.moveSeq == msg.seq()) s.clearMove(); // held: the strike never landed, the charge is all
                if (s.buffered != null && s.bufferedSeq == msg.seq()) s.buffered = null; // nor will one queued mid-combo
                s.holdOpen = false;
                s.waitHit = false;
                s.chargeStart = now;
                s.chargeSeq = msg.seq();
                seen(p, new ArmsPayloads.Seen(p.getId(), ArmsPayloads.CHARGE, -1, 0, 0));
            }
            case ArmsPayloads.PRESS_RELEASE -> {
                if (msg.seq() != s.chargeSeq || s.chargeStart == ArmsState.NO_CHARGE) return;
                if (msg.target() >= 0) s.aimed = Aim.meantId(p, msg.target(), msg.yaw(), msg.pitch(), pressReach(style));
                Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
                if (heavy == null || heavy.charge().isEmpty()) return;
                int held = heavy.charge().get().min() + (int) (now - s.chargeStart);
                s.chargeStart = ArmsState.NO_CHARGE;
                if (style.move(Archetype.Move.ART) != null && ArmsRules.artReady(held, heavy.charge().get(), s.resonance, ArmsMastery.artAt(ArmsMastery.step(p, style)))) {
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
        int mastery = ArmsMastery.step(p, style);
        if (slip != null && now - s.dodgeAt <= style.def().dodge().ticks() + slip.window() + ArmsMastery.slipcutBonus(mastery) && now - s.dodgeAt >= 1) {
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
        ArmsRules.Decision d = ArmsRules.tap(style, s.style == style ? s.move : null, s.moveStart, s.step, s.cadence, now, ArmsMastery.strikes(mastery));
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
        s.perfectHeavy = heavy.charge().isPresent() && heavy.charge().get().perfect(held + GLINT_GRACE);
    }

    /**
     * The furthest a press's creature may be and still be the one it meant (SPEC §40): the style's longest reach, the
     * blade's growth over a full chain, and the step in.
     */
    static double pressReach(Archetypes.Loaded style) {
        float most = 0;
        for (Archetype.MoveDef d : style.moves().values()) most = Math.max(most, d.art().isPresent() ? Math.max(d.reach(), d.art().get().range()) : d.reach());
        return most + 0.3 + style.def().traits().reachPerStep() * 5 + Aim.stepIn(style.id());
    }

    /** A chain's first strike or a heavy steps in toward the meant creature (SPEC §40); the rest strike where they stand. */
    static boolean stepsIn(ArmsState s) {
        return s.move == Archetype.Move.HEAVY || (s.move != null && s.move.strike() && s.step == 0);
    }

    /** Whether a style's press can be held into a charge (its heavy charges). */
    static boolean chargeable(Archetypes.Loaded style) {
        Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
        return heavy != null && heavy.charge().isPresent();
    }

    /**
     * Ticks a press may stay down with its strike waiting at the hit before the strike lands anyway (the client says
     * tap or charge well before: the charge at its heavy's min, ArmsPredict): no word from it never holds a blow back.
     */
    static final int HOLD_WAIT = 4;

    /**
     * Ticks the server's count of a charge may run behind the fighter's and still be full: its start is the charge's
     * press heard, a tick or so after the fighter's, so a release on the ting the fighter heard is never short here.
     */
    static final int GLINT_GRACE = 1;

    static void begin(ServerPlayer p, ArmsState s, Archetypes.Loaded style, Archetype.Move move, int step, int cadence, long now, int seq) {
        Archetype.MoveDef def = style.move(move);
        if (def == null) return;
        // a chain strike begun by a press still down waits at its hit for the press's end: a tap lands it, a hold
        // becomes the charge instead (the swing starts at once either way: a tap is never late)
        s.waitHit = move.strike() && s.holdOpen && seq == s.holdSeq;
        s.swishAt = 0;
        s.drawn = ArmsRules.drawn(style.def().traits(), move, now - s.moveEnd);
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
        s.unleashed = false;
        s.rhythm = false;
        s.buffered = null;
        s.fightAt = now;
        // a finisher reached with every press in rhythm (the chain starts at 0, each press in the window adds one, so
        // a full rhythm is a cadence equal to the step): the old rule asked for cadence 3, which the 3-strike chain
        // never reaches (combat sim, 2026-10-26)
        if (def.finisher() && step > 0 && cadence >= Math.min(ArmsRules.CADENCE_MAX, step)) {
            resonance(p, s, ArmsRules.RESONANCE_CADENCE3);
            ArmsWeaves.open(p, s, ArmsWeaves.RHYTHM, now, -1);
            s.rhythm = true;
        }
        AwakenNet.send(p, new ArmsPayloads.Result(seq, ArmsPayloads.MOVE, -1, move.ordinal(), cadence));
        s.waitSeen = s.waitHit; // a strike waiting on its press shows itself only once the press is a tap
        if (!s.waitHit) shown(p, s);
    }

    /**
     * The running move as everyone near sees and hears it: the vanilla arm (without the clips), the swish, the clip. The
     * swish plays where the swing's own clock puts it (FpSwings: its whoosh peaks as the blade crosses), so those near
     * hear it with the blow, not on the press; a waited strike shown on its tap is already at its hit and plays at once.
     */
    static void shown(ServerPlayer p, ArmsState s) {
        boolean waited = s.waitSeen;
        s.waitSeen = false;
        Archetype.MoveDef def = s.style == null || s.move == null ? null : s.style.move(s.move);
        if (def == null) return;
        p.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        int at = waited ? 0 : Math.round(FpSwings.swish(FpSwings.style(s.style.id()), s.move));
        s.swishAt = 0;
        if (at > 0) s.swishAt = s.moveStart + at;
        else soundForOthers(p, ArmsSounds.swish(def, FpSwings.swishSound(FpSwings.style(s.style.id()), s.move)), 1.0F, ArmsSounds.swishPitch(s.cadence));
        seen(p, new ArmsPayloads.Seen(p.getId(), ArmsPayloads.MOVE, -1, s.style.index(), s.move.ordinal()));
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
        moveTick(p, s, now);
        if (s.focus >= 0 && (now & 3) == 0) holdFocus(p, s, now);
        if (s.echoAt != 0) ArmsWeaves.tick(p, s, now);
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

    /**
     * The running move's tick: a strike waiting on its press holds at the hit (its clock stopped there) until the press
     * ends as a tap, becomes a charge, or has waited HOLD_WAIT past the charge's mark; then each active tick sweeps.
     */
    static void moveTick(ServerPlayer p, ArmsState s, long now) {
        if (s.move == null || s.style == null) return;
        Archetype.MoveDef def = s.style.move(s.move);
        if (def == null) return;
        long rel = now - s.moveStart;
        if (s.waitHit && rel >= def.windup()) {
            Archetype.MoveDef heavy = s.style.move(Archetype.Move.HEAVY);
            int limit = heavy != null && heavy.charge().isPresent() ? heavy.charge().get().min() + HOLD_WAIT : 0;
            if (s.holdOpen && now - s.holdFrom < limit) {
                s.moveStart++;
                s.moveEnd++;
                return;
            }
            s.waitHit = false;
            if (s.waitSeen) shown(p, s);
        }
        if (s.swishAt != 0 && now >= s.swishAt) {
            s.swishAt = 0;
            soundForOthers(p, ArmsSounds.swish(def, FpSwings.swishSound(FpSwings.style(s.style.id()), s.move)), 1.0F, ArmsSounds.swishPitch(s.cadence));
        }
        if (rel >= def.windup() && rel < def.activeEnd() && Arms.fights(p)) {
            if (s.move == Archetype.Move.ART && !s.unleashed) {
                s.unleashed = true;
                LordForged.Forged forged = LordForged.held(p);
                if (forged != null) LordForged.unleash(p, forged);
            }
            sweep(p, s, def, now);
        }
    }

    /** The arc's work for one active tick: every creature in reach and in the arc, once. */
    static void sweep(ServerPlayer p, ArmsState s, Archetype.MoveDef def, long now) {
        ServerLevel level = p.level();
        // the Precursor Arm's blade grows with the chain
        float grow = s.style == null || s.step < 0 || s.move == null || !s.move.strike() ? 0F : s.style.def().traits().reachPerStep() * s.step;
        double reach = def.reachAt(now - s.moveStart) + 0.3 + grow;
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot() + focusTurn(p, s);
        // the meant creature (a press's, checked by Aim.meant) is reached by the step in and above the band: a flier
        // circling overhead is struck when it is the one you meant (SPEC §40)
        double meantReach = reach + (s.style != null && stepsIn(s) ? Aim.stepIn(s.style.id()) : 0F);
        AABB box = p.getBoundingBox().inflate(meantReach + 1, Math.max(1.5, meantReach), meantReach + 1);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, t -> t != p && t.isAlive() && !s.hasHit(t.getId()))) {
            boolean aimed = t.getId() == s.aimed;
            if (!aimed && !foe(p, t)) continue;
            boolean meant = aimed && !(t instanceof Player);
            Vec3 c = t.getBoundingBox().getCenter();
            if (Aim.gap(eye, t.getBoundingBox()) > (meant ? meantReach : reach)) continue;
            float toward = (float) (Mth.atan2(c.z - p.getZ(), c.x - p.getX()) * Mth.RAD_TO_DEG) - 90F;
            if (!aimed && !ArmsRules.inArc(toward - yaw, def.arc())) continue;
            if (!meant && (t.getBoundingBox().maxY < p.getY() - 1.0 || t.getBoundingBox().minY > p.getEyeY() + 1.5)) continue;
            s.markHit(t.getId());
            hit(p, s, def, t, now);
        }
        // what isn't alive but takes a blow, as vanilla's attack strikes it (Euan 2026-10-26: "rifts can only be broken
        // at the core with your hands"): the aimed one (a rift's core, an end crystal, a boat, a minecart, an item
        // frame), and in the arc only what a sweep is meant to strike (#awaken:struck_by_sweeps: the anomalies)
        for (Entity t : level.getEntities(p, box, t -> !(t instanceof LivingEntity) && t.isAlive() && !s.hasHit(t.getId())
                && t.isAttackable() && !t.skipAttackInteraction(p))) {
            boolean aimed = t.getId() == s.aimed;
            if (!aimed && !t.getType().builtInRegistryHolder().is(STRUCK_BY_SWEEPS)) continue;
            Vec3 closest = new Vec3(Mth.clamp(eye.x, t.getBoundingBox().minX, t.getBoundingBox().maxX),
                    Mth.clamp(eye.y, t.getBoundingBox().minY, t.getBoundingBox().maxY), Mth.clamp(eye.z, t.getBoundingBox().minZ, t.getBoundingBox().maxZ));
            if (closest.distanceTo(eye) > reach) continue;
            Vec3 c = t.getBoundingBox().getCenter();
            float toward = (float) (Mth.atan2(c.z - p.getZ(), c.x - p.getX()) * Mth.RAD_TO_DEG) - 90F;
            if (!aimed && !ArmsRules.inArc(toward - yaw, def.arc())) continue;
            s.markHit(t.getId());
            strikeThing(p, s, def, t, now);
        }
    }

    /** The anomalies a sweep strikes in its arc (anything else not alive is struck only when aimed, as vanilla's is). */
    static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> STRUCK_BY_SWEEPS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, dev.awaken.Awaken.id("struck_by_sweeps"));

    /**
     * Strikes something not alive through the same source vanilla's attack does (the player's attack, at the move's
     * strength): it decides what a blow does to it (a rift's core loses a hit, a crystal bursts, a boat breaks). Landed,
     * it is felt as any blow is: the impact, the hit-stop, the ribbon's flash.
     */
    static void strikeThing(ServerPlayer p, ArmsState s, Archetype.MoveDef def, Entity t, long now) {
        float damage = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * ArmsRules.motion(def, s.cadence, s.perfectHeavy);
        if (!t.hurtServer(p.level(), p.damageSources().playerAttack(p), Math.max(1F, damage))) return;
        p.level().playSound(p, t.getX(), t.getY(0.6), t.getZ(), ArmsSounds.impact(t, s.move, def, s.style == null ? null : s.style.id()),
                SoundSource.PLAYERS, 1.0F, ArmsSounds.impactPitch(t, def));
        s.fightAt = now;
        int ms = def.hitstop();
        if (now - s.lastHitstopAt < ArmsRules.HITSTOP_GAP) ms = 0;
        else s.lastHitstopAt = now;
        int blow = Blow.of(s.move == null ? 0 : s.move.ordinal()).pack();
        AwakenNet.send(p, new ArmsPayloads.Result(s.moveSeq, ArmsPayloads.HIT, t.getId(), ms, blow));
        seen(p, new ArmsPayloads.Seen(t.getId(), ArmsPayloads.HIT, p.getId(), ms, blow));
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
        Archetype.Traits traits = s.style == null ? Archetype.Traits.NONE : s.style.def().traits();
        if (s.drawn) {
            pendingMotion *= traits.drawMotion();
            pendingPosture = Math.round(pendingPosture * traits.drawPosture());
        }
        if (traits.spirit() != 1F && dev.awaken.core.api.BodyMaterial.of(t) == dev.awaken.core.api.BodyMaterial.SPIRIT) pendingMotion *= traits.spirit();
        if (traits.backstab() > 1F) {
            float toStriker = (float) (Mth.atan2(p.getZ() - t.getZ(), p.getX() - t.getX()) * Mth.RAD_TO_DEG) - 90F;
            if (ArmsRules.behind(t.yBodyRot, toStriker)) pendingMotion *= traits.backstab();
        }
        pendingCap = def.bossCap();
        boolean primed = ArmsForge.bladePrimed(p, s, now);
        if (primed) pendingMotion *= ArmsForge.BLADE_PRIME;
        pendingLanded = false;
        // the blow pushes the way the blade went: away from the striker, turned along a side cut
        float side = ArmsRules.side(def, p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
        double ax = t.getX() - p.getX(), az = t.getZ() - p.getZ(), an = Math.sqrt(ax * ax + az * az);
        if (an < 1e-4) {
            ax = -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD);
            az = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
        } else {
            ax /= an;
            az /= an;
        }
        ArmsRules.push(ax, az, side, PUSH);
        boolean pushes = ArmsConfig.directionalKnockback() && !ArmsRules.lordBody(t.getMaxHealth());
        pendingPush = pushes;
        pendingPushScale = ArmsRules.pushScale(def);
        // a combo's blows come faster than vanilla's half second of immunity: each move strikes each creature once
        if (t.getLastHurtByMob() == p) t.damageCooldownTime = 0;
        try {
            if (speed != null) speed.addOrUpdateTransientModifier(FULL_STRENGTH_MOD);
            p.attack(t);
        } finally {
            if (speed != null) speed.removeModifier(FULL_STRENGTH);
            pendingBy = pendingTarget = -1;
            pendingPush = false;
        }
        if (!pendingLanded) return;
        int lift = ArmsRules.lift(def);
        // a player's push is sent and put back by vanilla's attack; only a creature is pinned or lifted here
        if (pushes && lift != 0 && !(t instanceof Player)) {
            Vec3 v = t.getDeltaMovement();
            double resist = 1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            t.setDeltaMovement(v.x, lift < 0 ? Math.min(v.y, 0) : v.y + ArmsRules.LIFT * resist, v.z);
            t.needsSync = true;
        }
        boolean kill = t.isDeadOrDying();
        if (s.bodiesSeq != s.moveSeq) {
            s.bodiesSeq = s.moveSeq;
            s.bodies = 0;
        }
        s.bodies++;
        landedTraits(p, s, t, traits);
        ArmsForge.landed(p, s, t, now, primed);
        Marks.fire(p, Marks.Trigger.HIT, t);
        if (def.finisher() && s.rhythm) Marks.fire(p, Marks.Trigger.FINISHER, t);
        if (s.perfectHeavy) Marks.fire(p, Marks.Trigger.PERFECT_HEAVY, t);
        if (s.edgeHits > 0) ArmsWeaves.edge(p, s, t, now, (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * pendingMotion);
        p.level().playSound(p, t.getX(), t.getY(0.6), t.getZ(), ArmsSounds.impact(t, s.move, def, s.style == null ? null : s.style.id()), SoundSource.PLAYERS, 1.0F, ArmsSounds.impactPitch(t, def));
        s.fightAt = now;
        if (s.focus != t.getId()) s.focus = t.getId();
        s.focusSeenAt = now;
        int base = s.perfectHeavy && def.charge().isPresent() ? def.charge().get().glintHitstop() : def.hitstop();
        int ms;
        if (now - s.lastHitstopAt >= ArmsRules.HITSTOP_GAP) {
            s.lastHitstopAt = now;
            s.freezeSeq = s.moveSeq;
            s.freezeFrom = s.bodies;
            s.freezeBase = base;
            ms = base;
        } else if (now == s.lastHitstopAt && s.freezeSeq == s.moveSeq) {
            // another body in the same sweep: the one freeze grows (a kill's own share is added by whoever sees it)
            ms = ArmsRules.hitstop(s.freezeBase, s.bodies - s.freezeFrom + 1);
        } else {
            ms = 0;
        }
        boolean strong = def.finisher() || s.perfectHeavy || s.move == Archetype.Move.EXECUTION;
        int blow = new Blow(s.move == null ? 0 : s.move.ordinal(), kill, kill && noneHunting(p, t), strong, s.bodies, lift,
                Blow.yawByte(PUSH[0], PUSH[1]), Math.round(side * 7)).pack();
        AwakenNet.send(p, new ArmsPayloads.Result(s.moveSeq, ArmsPayloads.HIT, t.getId(), ms, blow));
        seen(p, new ArmsPayloads.Seen(t.getId(), ArmsPayloads.HIT, p.getId(), ms, blow));
        if (blowSeen != null) blowSeen.seen(t.getId(), ms, blow);
        if (def.finisher()) resonance(p, s, ArmsRules.RESONANCE_FINISHER);
    }

    /** Test stand-in: told every HIT as it is sent (the struck body, the freeze, the packed {@link Blow}). */
    interface BlowSeen {
        void seen(int target, int ms, int blow);
    }

    static @Nullable BlowSeen blowSeen;

    /** The fight is over with this kill: no other hostile within 16 blocks hunts the striker. */
    static boolean noneHunting(ServerPlayer p, LivingEntity dead) {
        return p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(ArmsRules.LAST_RANGE),
                m -> m != dead && m.isAlive() && m.getTarget() == p && m.distanceToSqr(p) <= ArmsRules.LAST_RANGE * ArmsRules.LAST_RANGE).isEmpty();
    }

    /** An Arms blow pushes the way the blade went (SPEC §25 "Fight Feel"); a kill throws the body further along it. */
    static void onKnockBack(net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent e) {
        if (!pendingPush || e.getEntity().getId() != pendingTarget) return;
        // vanilla pushes against the ratio
        e.setRatioX(-PUSH[0]);
        e.setRatioZ(-PUSH[1]);
        e.setStrength(e.getStrength() * pendingPushScale * (e.getEntity().isDeadOrDying() ? ArmsRules.KILL_PUSH : 1F));
    }

    /** A Break held longer (a Weave through the broken body). */
    static void extendBreak(LivingEntity target, int ticks) {
        Posture p = target.getExistingDataOrNull(ArmsContent.POSTURE);
        long now = now(target);
        if (p == null || !p.broken(now)) return;
        p.brokenUntil += ticks;
        Strikes.stagger(target, (int) (p.brokenUntil - now));
        stance(target, p, now);
    }

    /** What a style's blow does beyond its damage once it lands: a pull, a throw off a riposte, energy back. */
    private static void landedTraits(ServerPlayer p, ArmsState s, LivingEntity t, Archetype.Traits traits) {
        boolean heavyBody = t.getMaxHealth() >= 150; // a Lord is neither reeled in nor thrown
        double resist = 1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (traits.pull() > 0 && !heavyBody) {
            net.minecraft.world.phys.Vec3 to = p.position().subtract(t.position());
            double d = Math.max(1e-3, to.horizontalDistance());
            if (d > 1.6) {
                double k = Math.min(traits.pull(), d - 1.5) * 0.35 * resist;
                t.setDeltaMovement(t.getDeltaMovement().add(to.x / d * k, 0.12, to.z / d * k));
                t.needsSync = true;
            }
        }
        if (traits.riposteThrow() > 0 && s.move == Archetype.Move.RIPOSTE && !heavyBody) {
            net.minecraft.world.phys.Vec3 away = t.position().subtract(p.position());
            double d = Math.max(1e-3, away.horizontalDistance());
            double k = traits.riposteThrow() * resist;
            t.setDeltaMovement(t.getDeltaMovement().add(away.x / d * k, 0.25 + 0.15 * k, away.z / d * k));
            t.needsSync = true;
        }
        if (traits.energy() > 0) dev.awaken.powers.Powers.restoreEnergy(p, traits.energy());
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
        Strikes.Kind kind = kind(src);
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
        // never a shot: holding it would stop it at your body at once (it bounces off), and land it after
        if (grace > 0 && kind.deflectable() && by != null && !shot(src) && !p.isBlocking() && Arms.guards(p.getMainHandItem())) {
            e.setCanceled(true);
            HOLDS.add(new Hold(p, src, e.getAmount(), now, grace, kind));
        }
    }

    /** Whether a blow is a shot: something that flew to you (a projectile struck, or its damage says it was one). */
    static boolean shot(DamageSource src) {
        return src.getDirectEntity() instanceof Projectile || src.is(DamageTypeTags.IS_PROJECTILE);
    }

    /**
     * A blow's class as Arms answers it: Strikes' word, except that a shot is never nothing. A blaze's or a ghast's
     * fireball strikes as fire, which Strikes counts as no attack, so it passed every guard and dodge unanswered; any
     * shot is at least LIGHT. A blast is still PERILOUS, and what bypasses invulnerability still nothing.
     */
    static Strikes.Kind kind(DamageSource src) {
        Strikes.Kind k = Strikes.of(src);
        if (k == Strikes.Kind.NONE && shot(src) && !src.is(DamageTypeTags.IS_EXPLOSION) && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return Strikes.Kind.LIGHT;
        }
        return k;
    }

    /**
     * Where a shot came from, flat and unit: by its own path (it flies in from the way it's going, wherever its archer
     * stands); straight down, or with nothing flying, by where it meets the body (vanilla's own judge). Null: nowhere.
     */
    static @Nullable Vec3 shotFrom(ServerPlayer p, DamageSource src) {
        if (src.getDirectEntity() instanceof Projectile pr) {
            Vec3 v = pr.getDeltaMovement();
            if (v.x * v.x + v.z * v.z > 1e-4) return new Vec3(-v.x, 0, -v.z).normalize();
        }
        Vec3 at = src.getSourcePosition();
        if (at == null) return null;
        Vec3 d = new Vec3(at.x - p.getX(), 0, at.z - p.getZ());
        return d.lengthSqr() < 1e-6 ? null : d.normalize();
    }

    /** Whether something from {@code from} meets the guard: within its angle of where the head faces (vanilla's rule). */
    static boolean inFront(ServerPlayer p, Vec3 from, float angle) {
        float yaw = p.getYHeadRot() * Mth.DEG_TO_RAD;
        double dot = -Mth.sin(yaw) * from.x + Mth.cos(yaw) * from.z;
        return Math.acos(Mth.clamp(dot, -1, 1)) <= angle * Mth.DEG_TO_RAD;
    }

    /** The guard's answer: deflect in its first ticks, block after, a broken guard on a heavy, nothing for the rest. */
    static void onShieldBlock(LivingShieldBlockEvent e) {
        if (chipping) {
            e.setBlocked(false);
            return;
        }
        if (!(e.getEntity() instanceof ServerPlayer p) || !Arms.fights(p) || !Arms.guards(p.getUseItem())) return;
        Archetypes.Loaded style = Arms.styleOf(p.getUseItem());
        if (style == null) return;
        ArmsState s = state(p);
        DamageSource src = e.getDamageSource();
        Strikes.Kind kind = heldKind != null ? heldKind : kind(src);
        int grace = grace(p);
        Archetype.Guard g = style.def().guard();
        // a shot's front is judged by its own path, not by the point on the body it reached
        boolean shot = shot(src);
        Vec3 from = shot ? shotFrom(p, src) : null;
        boolean inArc = from != null ? inFront(p, from, g.angle()) : e.getOriginalBlockedDamage() > 0;
        boolean untold = Arms.untold(src);
        ArmsRules.Answer a = ArmsRules.guard(g, kind, p.getTicksUsingItem(), heldTicks, grace, s.lockedOut, inArc, untold);
        float incoming = e.getDamageContainer().getNewDamage();
        Entity by = src.getEntity();
        long now = now(p);
        switch (a) {
            case DEFLECT -> {
                e.setBlockedDamage(incoming);
                e.setShieldDamage(0);
                s.raiseDeflected = true;
                if (!shot) { // a riposte answers a blow in reach, never an archer's
                    s.deflectAt = now;
                    s.deflected = by == null ? -1 : by.getId();
                }
                // mastery III: a charged deflect; an untold blow's wider window pays a little less
                float charged = ArmsMastery.deflect(ArmsMastery.step(p, style)) * (untold ? ArmsRules.UNTOLD_REWARD : 1F);
                resonance(p, s, ArmsRules.RESONANCE_DEFLECT * charged);
                if (by instanceof LivingEntity attacker && src.getDirectEntity() == by) {
                    addPosture(attacker, (int) ((kind == Strikes.Kind.HEAVY ? ArmsRules.DEFLECT_POSTURE_HEAVY : ArmsRules.DEFLECT_POSTURE_LIGHT) * charged), p);
                    // a flier's dive turned aside brings it down (SPEC §40): the opening a fight in the air needs
                    Strikes.ground(attacker, Aim.GROUNDED_TICKS);
                }
                ArmsWeaves.open(p, s, ArmsWeaves.DEFLECT, now, by == null ? -1 : by.getId());
                Marks.fire(p, Marks.Trigger.DEFLECT, by instanceof LivingEntity l ? l : null);
                if (shot) {
                    stop(p, s, src, from, true, 0F, by);
                } else {
                    soundForOthers(p, ArmsSounds.DEFLECT.get(), 1.0F, 1.0F);
                    answer(p, s, ArmsPayloads.DEFLECT, by);
                }
            }
            case BLOCK -> {
                Marks.fire(p, Marks.Trigger.GUARD, by instanceof LivingEntity l ? l : null);
                if (shot) {
                    // the shot stops on the blade (it never reaches the body to stick, poison or burn); its share
                    // through the guard lands at the tick's end
                    e.setBlockedDamage(incoming);
                    stop(p, s, src, from, false, incoming * (1F - g.reduction()), by);
                } else {
                    e.setBlockedDamage(incoming * g.reduction());
                    soundForOthers(p, ArmsSounds.BLOCK.get(), 1.0F, 1.0F);
                    answer(p, s, ArmsPayloads.BLOCK, by);
                }
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

    /**
     * The guard stopped a shot: it leaves the blade at the tick's end; the fighter feels it there (the hands jolt, the
     * spark, the ring) and so does everyone near (the body's jolt, the spark).
     */
    private static void stop(ServerPlayer p, ArmsState s, DamageSource src, @Nullable Vec3 from, boolean deflected, float through, @Nullable Entity by) {
        Vec3 way = from != null ? from : flatFacing(p);
        STOPPED.add(new Stopped(p, src.getDirectEntity() instanceof Projectile pr ? pr : null, way, deflected, through, src));
        if (deflected) soundForOthers(p, ArmsSounds.GLANCE.get(), 1.0F, 1.0F);
        else soundForOthers(p, ArmsSounds.BLOCK.get(), ArmsSounds.SHOT_BLOCK_VOLUME, ArmsSounds.SHOT_BLOCK_PITCH);
        int yaw = (int) (Mth.atan2(way.z, way.x) * Mth.RAD_TO_DEG) - 90; // HURT's convention: the world yaw it came from
        int id = by == null ? -1 : by.getId(), what = deflected ? ArmsPayloads.DEFLECT : ArmsPayloads.BLOCK;
        AwakenNet.send(p, new ArmsPayloads.Result(s.seq, ArmsPayloads.SHOT, id, what, yaw));
        seen(p, new ArmsPayloads.Seen(p.getId(), ArmsPayloads.SHOT, id, what, yaw));
    }

    private static Vec3 flatFacing(ServerPlayer p) {
        float yaw = p.getYHeadRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /**
     * A block's share comes in already carrying every scaling the shot had (a creature's level, a buff's cut); the
     * second pass through the listeners would apply each again, so the share is pinned back last.
     */
    static void onIncomingPinned(LivingIncomingDamageEvent e) {
        if (chipping && e.getEntity() == chipVictim) e.setAmount(chipShare);
    }

    /** This tick's stopped shots leave the guard, after their own hits ran: each off the blade, then a block's share. */
    private static void releaseStopped() {
        int n = STOPPED.size();
        for (int i = 0; i < n; i++) {
            Stopped st = STOPPED.get(i);
            Projectile pr = st.shot;
            if (pr != null && !pr.isRemoved() && pr.level() == st.victim.level()) {
                // a bolt or a fireball broke on the steel by its own hit; one still whole leaves the blade, and
                // one with no weight to fall by breaks on it
                if (pr.getGravity() > 0) glance(st.victim, pr, st.from, st.deflected);
                else pr.discard();
            }
            if (st.through > 0 && st.victim.isAlive() && !st.victim.isRemoved() && st.victim.level() instanceof ServerLevel level) {
                chipping = reapplying = true;
                chipVictim = st.victim;
                chipShare = st.through;
                try {
                    st.victim.hurtServer(level, st.source, st.through);
                } finally {
                    chipping = reapplying = false;
                    chipVictim = null;
                }
            }
        }
        STOPPED.subList(0, n).clear();
    }

    /**
     * A stopped shot leaves the guard: set just off the blade, clear of the body, and sent away. A deflect knocks it
     * up and aside, back the way it came, to drop a few blocks off; a block lets it fall at your feet. It hurts
     * nothing after (an arrow's damage is spent on the steel).
     */
    static void glance(ServerPlayer p, Projectile pr, Vec3 from, boolean deflected) {
        Vec3 face = flatFacing(p);
        // it skips off on the side it came in from; one dead ahead goes either way
        double cross = face.x * from.z - face.z * from.x;
        double way = Math.abs(cross) > 0.15 ? Math.signum(cross) : (pr.getId() & 1) == 0 ? 1 : -1;
        Vec3 side = new Vec3(-from.z, 0, from.x).scale(way);
        Vec3 at = new Vec3(p.getX(), p.getY() + p.getBbHeight() * 0.7, p.getZ()).add(from.scale(p.getBbWidth() * 0.5 + 0.5));
        Vec3 v = deflected ? from.scale(0.18).add(side.scale(0.16)).add(0, 0.26, 0) : from.scale(0.07).add(side.scale(0.04)).add(0, 0.05, 0);
        pr.setPos(at);
        pr.setDeltaMovement(v);
        pr.needsSync = true;
        if (pr instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(0);
            arrow.setCritArrow(false);
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
        LordForged.revealPending();
        if (!STOPPED.isEmpty()) releaseStopped();
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
        // a stopped shot's share through the guard is no blow landing: the guard's own answer already told it
        if (!(e.getEntity() instanceof ServerPlayer p) || !ArmsConfig.enabled() || e.getInflictedDamage() <= 0 || chipping) return;
        if (kind(e.getSource()) == Strikes.Kind.NONE) return;
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
        s.dodgeFromX = p.getX();
        s.dodgeFromY = p.getY();
        s.dodgeFromZ = p.getZ();
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
        ArmsWeaves.open(p, s, ArmsWeaves.DODGE, now, attacker.getId());
        Marks.fire(p, Marks.Trigger.PERFECT_DODGE, attacker);
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
            ArmsWeaves.open(by, state(by), ArmsWeaves.BREAK, now, target.getId());
            Marks.fire(by, Marks.Trigger.BREAK, target);
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
        if (delta > 0) { // mastery IV: Resonance fills faster
            Archetypes.Loaded style = Arms.styleOf(p.getMainHandItem());
            if (style != null) delta *= ArmsMastery.resonanceGain(ArmsMastery.step(p, style));
        }
        s.resonance = Mth.clamp(s.resonance + delta, 0, ArmsRules.RESONANCE_MAX);
        if ((int) was != (int) s.resonance) AwakenNet.send(p, new ArmsPayloads.Result(s.seq, ArmsPayloads.RESONANCE, -1, (int) s.resonance, 0));
    }

    // ---- housekeeping -------------------------------------------------------------------------------------------------

    static void onLeave(EntityLeaveLevelEvent e) {
        if (!SLOWED.isEmpty()) SLOWED.remove(e.getEntity().getId());
        if (e.getEntity() instanceof ServerPlayer p) {
            HOLDS.removeIf(h -> h.victim == p);
            STOPPED.removeIf(st -> st.victim == p);
        }
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
        STOPPED.removeIf(st -> st.victim == p);
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
