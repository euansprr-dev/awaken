package dev.awaken.armour;

import dev.awaken.Awaken;
import dev.awaken.armour.net.ArmourPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.FightMoments;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.Forces;
import dev.awaken.powers.Powers;
import dev.awaken.powers.Scheduler;
import dev.awaken.gear.GearCombat;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Fx;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * What wearing a set does (SPEC §44.5, §44.6). Two pieces of a set with items of its own give its archetype's half bonus;
 * four give the archetype's ability (a Hunter set its own bonuses, then the ability of its archetype) and the set's twist.
 * The abilities are power you feel and others see: a charge that lights the armour as it fills (§44.5, "The look"), and
 * a release you can't miss. Nothing here shows what you couldn't see without it. Server side; the state is unsaved, so a
 * relog starts it empty.
 */
public final class ArmourAbilities {
    private ArmourAbilities() {}

    // ---- numbers (SPEC §44.5) ---------------------------------------------------------------------------------------

    /** A charge's size: Bastion, Flow and Edge fill to it. */
    public static final int FULL = 100;
    /** Bastion: a guarded blow, a deflect, a blow taken; the burst's reach and stagger. */
    public static final int BASTION_GUARD = 20, BASTION_DEFLECT = 30, BASTION_TAKEN = 8, BASTION_STAGGER = 30;
    public static final double BASTION_RADIUS = 3.5;
    /** Flow: a perfect dodge, a tick of wall-running, a tick of sliding; the rush after the afterimage takes a blow. */
    public static final int FLOW_DODGE = 35, FLOW_WALL = 2, FLOW_SLIDE = 1, FLOW_RUSH = 40;
    public static final double FLOW_RUSH_SPEED = 0.30;
    /** Edge: a deflect, a finisher in rhythm, a clean hit, a perfect dodge; the wave's reach, half angle and share of a blow. */
    public static final int EDGE_DEFLECT = 35, EDGE_FINISHER = 30, EDGE_HIT = 6, EDGE_DODGE = 20;
    public static final double EDGE_RANGE = 5.0, EDGE_HALF_ANGLE = 50.0;
    public static final float EDGE_SHARE = 0.8F;
    /** Runes: how many circle you, how long they last after the last cast; an overchannel's reach, its power and how long. */
    public static final int RUNES = 3, RUNE_LIFE = 400, OVERCHANNEL_TICKS = 40;
    public static final double RUNE_REACH = 8.0;
    public static final float OVERCHANNEL = 0.35F;
    /** An ability's own blow: this, and this again for each rank above E. */
    public static final float BLOW = 4F, BLOW_RANK = 2F;
    /** A body this big (max health) takes at most this share of it from one of these blows (a Lord is no pushover). */
    public static final float BIG_BODY = 150F, BIG_SHARE = 0.03F;

    /** Half bonuses: health (Bulwark, Lord-forged), falls (Strider), power damage (Channeler), swing (Duelist), footing (Lord-forged). */
    public static final double HALF_HEALTH = 2.0, HALF_FALL = -1.0 / 3.0, HALF_SWING = 0.06, LORD_KNOCKBACK = 0.2;
    public static final float HALF_POWER = 0.10F;

    /** Twists (SPEC §44.6). */
    public static final int RING_STAGGER = 20, RING_COOLDOWN = 60;
    public static final double RING_RADIUS = 3.0;
    public static final int ROOTED_TICKS = 40, ROOTED_COOLDOWN = 40;
    public static final float ROOTED_HEAL = 1F;
    public static final int TEMPO_TICKS = 60;
    public static final double TEMPO_SWING = 0.15, TEMPO_SPEED = 0.10;
    public static final int SLIP_TICKS = 30;
    public static final double SLIP_SPEED = 0.25;
    public static final int KINDLE_TICKS = 60;
    public static final double KINDLE_REACH = 4.0;
    public static final int WARD_TICKS = 40;
    public static final float WARD_SHARE = 0.5F;
    public static final int GORE_SPRINT = 25, GORE_COOLDOWN = 100, GORE_STAGGER = 30;
    public static final double GORE_REACH = 1.6;
    public static final float GORE_DAMAGE = 6F;

    /** P3's twists (SPEC §44.6). */
    public static final int VAULT_STAGGER = 15, VAULT_COOLDOWN = 10;
    public static final double VAULT_PUSH = 1.0;
    public static final int RIME_FREEZE = 3, RIME_SLOW = 60;
    public static final double RIME_RADIUS = 3.0;
    public static final double NOON_RADIUS = 5.0;
    public static final int NOON_FIRE = 80;
    public static final float NOON_HEAL = 2F;
    public static final int SHATTER_TICKS = 60, SHATTER_COOLDOWN = 600;
    public static final double SHATTER_RADIUS = 3.5;
    public static final float SHATTER_SHARE = 2F / 3F;
    public static final float SHELL_SHARE = 0.6F;
    public static final double UNDERTOW_PULL = 0.7;
    public static final int PULSE_BEATS = 5;
    public static final double PULSE_RADIUS = 4.0;
    public static final float PULSE_HEAL = 1F;

    /** P4's twists (SPEC §44.6). */
    public static final double ABYSS_PULL_RADIUS = 6.0, ABYSS_PULL = 0.9;
    public static final int ABYSS_DELAY = 8;
    public static final double GUST_RADIUS = 3.0, GUST_PUSH = 0.9, GUST_LIFT = 0.35;
    public static final int GUST_COOLDOWN = 40;
    public static final float HYMN_SHARE = 0.8F;
    public static final int HYMN_STAGGER = 30;
    public static final double HYMN_RADIUS = 5.0;
    public static final float MIRROR_SHARE = 0.5F;
    public static final int MIRROR_COOLDOWN = 20;
    public static final double METEOR_FALL = 6.0, METEOR_RADIUS = 4.0;
    public static final int METEOR_FIRE = 80, METEOR_COOLDOWN = 100;
    public static final double RIFT_BEHIND = 1.5, RIFT_REACH = 12.0;
    public static final int RIPOSTE_TICKS = 40;
    public static final float RIPOSTE = 0.6F, ECHO_SHARE = 0.75F;
    public static final int ECHO_DELAY = 20;

    /** How the four abilities grow with the set's rank (SPEC §44.5, "By rank"). */
    public static final double SLAM_FALL = 4.0;
    public static final int AFTER_STAGGER = 20;
    public static final double AFTER_RADIUS = 3.0, AIR_RUSH = 1.1, AIR_LIFT = 0.25;
    public static final float REFUND = 0.25F;
    public static final int BLEED_TICKS = 60, BLEED_EVERY = 10;
    public static final float BLEED = 1F;
    /** A: a second charge held (Bastion, Flow, Edge) or a fourth rune. S: everything the ability does reaches half again as far. */
    public static final int RUNES_A = 4;
    public static final double S_REACH = 1.5;

    /** Mastery experience: a blow dealt or taken with the whole set on; a strong creature's kill; a gate Lord's. Written to the pieces in tens. */
    public static final int XP_BLOW = 1, XP_STRONG = 5, XP_LORD = 60, XP_FLUSH = 10;

    private static final Identifier HEALTH_ID = Awaken.id("armour_half_health"), FALL_ID = Awaken.id("armour_half_fall"),
            SWING_ID = Awaken.id("armour_half_swing"), FOOTING_ID = Awaken.id("armour_half_footing"),
            RUSH_ID = Awaken.id("armour_rush"), TEMPO_ID = Awaken.id("armour_tempo"), SLIP_ID = Awaken.id("armour_slipstream"),
            ROOTED_ID = Awaken.id("armour_rooted"), INLAY_HEAT_ID = Awaken.id("armour_inlay_heat"),
            INLAY_FROST_ID = Awaken.id("armour_inlay_frost"), INLAY_GRAVITY_ID = Awaken.id("armour_inlay_gravity"),
            INLAY_CHARGE_ID = Awaken.id("armour_inlay_charge"), INLAY_SPIRIT_ID = Awaken.id("armour_inlay_spirit");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** A player's live armour state (attachment {@code awaken:armour_state}, unsaved). */
    public static final class State {
        final int[] pieces = new int[ArmourSet.values().length];
        /** Four of one set, or null. */
        @Nullable ArmourSet full;
        /** Power damage from worn Channeler pieces and the Channeler half. */
        float power;
        boolean dirty = true;
        int charge, runes;
        long runesUntil, overUntil, rushUntil, tempoUntil, slipUntil, wardUntil, rootedUntil;
        long ringReady, rootedReady, goreReady;
        long vaultReady, shatterReady, shatterUntil;
        long gustReady, mirrorReady, meteorReady, riposteUntil;
        /** The Force of the wearer's last power hit (a B Bastion's burst carries it), or null. */
        Forces.@Nullable Force force;
        /** The furthest fall since the wearer left the ground (a C Bastion's landing). */
        double fall;
        int beats;
        int sprint;
        boolean moving;
        int xp;
        int sentCharge = -1, sentRunes = -1;

        public int charge() {
            return charge;
        }

        public int runes() {
            return runes;
        }

        public @Nullable ArmourSet full() {
            return full;
        }
    }

    public static State state(ServerPlayer p) {
        return p.getData(ArmourContent.STATE);
    }

    static void init() {
        FightMoments.listen(ArmourAbilities::onMoment);
    }

    // ---- what is worn -----------------------------------------------------------------------------------------------

    /** Counts what is worn and puts the half bonuses on or off. */
    static void count(ServerPlayer p, State s) {
        flush(p, s);
        ArmourSet was = s.full;
        Arrays.fill(s.pieces, 0);
        s.full = null;
        s.power = 0;
        int[] inlays = new int[Forces.Force.values().length];
        for (EquipmentSlot slot : SLOTS) {
            ArmourLooks.Look l = ArmourLooks.of(p.getItemBySlot(slot));
            if (l == null || l.piece().getSlot() != slot) continue;
            Forged story = p.getItemBySlot(slot).get(ArmourContent.FORGED.get());
            Forces.Force inlaid = story == null ? null : story.inlayForce();
            if (inlaid != null) inlays[inlaid.ordinal()]++;
            if (++s.pieces[l.set().ordinal()] == 4) s.full = l.set();
            if (l.set().hunter == null) s.power += (float) ArmourItems.numbers(l.set()).power();
        }
        boolean bulwark = false, strider = false, channeler = false, duelist = false, lord = false;
        for (ArmourSet set : ArmourSet.values()) {
            if (set.hunter != null || s.pieces[set.ordinal()] < 2) continue;
            switch (set.archetype) {
                case BULWARK -> bulwark = true;
                case STRIDER -> strider = true;
                case CHANNELER -> channeler = true;
                case DUELIST -> duelist = true;
                case LORD -> lord = true;
            }
        }
        if (channeler) s.power += HALF_POWER;
        modify(p, Attributes.MAX_HEALTH, HEALTH_ID, (bulwark ? HALF_HEALTH : 0) + (lord ? HALF_HEALTH : 0), AttributeModifier.Operation.ADD_VALUE, true);
        modify(p, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, strider ? HALF_FALL : 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        modify(p, Attributes.ATTACK_SPEED, SWING_ID, duelist ? HALF_SWING : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modify(p, Attributes.KNOCKBACK_RESISTANCE, FOOTING_ID, lord ? LORD_KNOCKBACK : 0, AttributeModifier.Operation.ADD_VALUE, false);
        // each inlaid piece adds its Force's small share, whatever set it is on (SPEC §44.4, "Inlay")
        modify(p, Attributes.ATTACK_DAMAGE, INLAY_HEAT_ID, inlays[Forces.Force.HEAT.ordinal()] * ArmourInlay.HEAT_DAMAGE,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modify(p, Attributes.ARMOR_TOUGHNESS, INLAY_FROST_ID, inlays[Forces.Force.FROST.ordinal()] * ArmourInlay.FROST_TOUGHNESS,
                AttributeModifier.Operation.ADD_VALUE, false);
        modify(p, Attributes.KNOCKBACK_RESISTANCE, INLAY_GRAVITY_ID, inlays[Forces.Force.GRAVITY.ordinal()] * ArmourInlay.GRAVITY_FOOTING,
                AttributeModifier.Operation.ADD_VALUE, false);
        modify(p, Attributes.MOVEMENT_SPEED, INLAY_CHARGE_ID, inlays[Forces.Force.CHARGE.ordinal()] * ArmourInlay.CHARGE_SPEED,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modify(p, Attributes.MAX_HEALTH, INLAY_SPIRIT_ID, inlays[Forces.Force.SPIRIT.ordinal()] * ArmourInlay.SPIRIT_HEALTH,
                AttributeModifier.Operation.ADD_VALUE, true);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        // a set taken off (or swapped for another) takes its charge with it
        if (s.full != was || s.full == null || ability(s.full) == null) {
            s.charge = 0;
            s.runes = 0;
            s.beats = 0;
        }
        s.dirty = false;
    }

    /** The archetype ability a whole set gives (a Hunter set its archetype's too), or null: the Lord-forged have their own move. */
    public static @Nullable Archetype ability(ArmourSet set) {
        return set.archetype == Archetype.LORD ? null : set.archetype;
    }

    /** Whether a set's ability has grown to this rank (it grows with the set's own rank). */
    static boolean grown(ArmourSet set, dev.awaken.progression.Rank rank) {
        return set.rank.ordinal() >= rank.ordinal();
    }

    // ---- the moments ------------------------------------------------------------------------------------------------

    static void onMoment(ServerPlayer p, FightMoments.Moment m, @Nullable LivingEntity target) {
        State s = state(p);
        if (s.dirty) count(p, s);
        ArmourSet set = s.full;
        if (set == null) return;
        long now = p.level().getGameTime();
        Archetype a = ability(set);
        if (a != null) {
            switch (a) {
                case BULWARK -> {
                    if (m == FightMoments.Moment.GUARD || m == FightMoments.Moment.DEFLECT || m == FightMoments.Moment.PERFECT_HEAVY) {
                        if (s.charge >= FULL) bastion(p, s, set);
                        else fill(p, s, m == FightMoments.Moment.GUARD ? BASTION_GUARD : m == FightMoments.Moment.DEFLECT ? BASTION_DEFLECT : 0);
                    }
                }
                case STRIDER -> {
                    if (m == FightMoments.Moment.PERFECT_DODGE) fill(p, s, FLOW_DODGE);
                }
                case DUELIST -> {
                    if (m == FightMoments.Moment.HIT && s.charge >= FULL) edge(p, s, set, target, null);
                    // from C a full Edge answers a perfect dodge at once, toward the one dodged
                    else if (m == FightMoments.Moment.PERFECT_DODGE && s.charge >= FULL && grown(set, dev.awaken.progression.Rank.C))
                        edge(p, s, set, null, target);
                    else {
                        int n = switch (m) {
                            case DEFLECT -> EDGE_DEFLECT;
                            case FINISHER -> EDGE_FINISHER;
                            case HIT -> EDGE_HIT;
                            case PERFECT_DODGE -> EDGE_DODGE;
                            default -> 0;
                        };
                        // Tidebreaker: in water or rain the Edge fills twice as fast
                        fill(p, s, set == ArmourSet.TIDEBREAKER && p.isInWaterOrRain() ? n * 2 : n);
                    }
                }
                case CHANNELER -> {
                    if (m == FightMoments.Moment.CAST) {
                        if (s.runes >= runeCap(set)) overchannel(p, s, set);
                        else {
                            s.runes++;
                            sound(p, ArmourContent.RUNE.get(), 0.6F, 0.9F + 0.15F * s.runes);
                        }
                        s.runesUntil = now + RUNE_LIFE;
                    }
                }
                default -> {}
            }
        }
        switch (set) {
            case BELLGUARD -> {
                if (m == FightMoments.Moment.DEFLECT && now >= s.ringReady) ring(p, s, set, now);
            }
            case ROOTWARDEN -> {
                if (m == FightMoments.Moment.CAST && !s.moving && p.onGround() && now >= s.rootedReady) rooted(p, s, set, now);
            }
            case AMBERWING -> {
                if (m == FightMoments.Moment.DEFLECT || m == FightMoments.Moment.PERFECT_DODGE) {
                    s.tempoUntil = now + TEMPO_TICKS;
                    modify(p, Attributes.ATTACK_SPEED, TEMPO_ID, TEMPO_SWING, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
                    modify(p, Attributes.MOVEMENT_SPEED, TEMPO_ID, TEMPO_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
                    Fx.at(p.level(), FxKind.TRAIL, FxStyle.NEUTRAL, p.position()).caster(p).colour(glow(set)).aux(1).ticks(TEMPO_TICKS).send();
                }
            }
            case BLADEWARD -> {
                if (m == FightMoments.Moment.DEFLECT) {
                    s.wardUntil = now + WARD_TICKS;
                    Fx.at(p.level(), FxKind.RING, FxStyle.NEUTRAL, p.position().add(0, 0.1, 0)).caster(p).colour(glow(set)).scale(1.4).ticks(6).send();
                }
            }
            case VAULTBREAKER -> {
                if (m == FightMoments.Moment.GUARD && target != null && now >= s.vaultReady) vault(p, s, set, target, now);
            }
            case FROSTWALKER -> {
                if (m == FightMoments.Moment.PERFECT_DODGE) rime(p, set);
            }
            case GALEWING -> {
                if (m == FightMoments.Moment.PERFECT_DODGE && now >= s.gustReady) gust(p, s, set, now);
            }
            case DREAMSTEEL -> {
                if (m == FightMoments.Moment.DEFLECT && target != null && now >= s.mirrorReady) mirror(p, s, set, target, now);
            }
            case NIGHTCOURT -> {
                if (m == FightMoments.Moment.DEFLECT) {
                    s.riposteUntil = now + RIPOSTE_TICKS;
                    Fx.at(p.level(), FxKind.GLYPH, FxStyle.NEUTRAL, p.position().add(0, 0.05, 0)).caster(p).colour(glow(set)).scale(1.1).ticks(RIPOSTE_TICKS).send();
                    sound(p, ArmourContent.EDGE.get(), 0.5F, 1.6F);
                }
            }
            case HOLLOWHEART -> {
                if (m == FightMoments.Moment.HIT) {
                    if (++s.beats >= PULSE_BEATS) pulse(p, s, set);
                    s.charge = s.beats * FULL / PULSE_BEATS;
                }
            }
            default -> {}
        }
        if (m == FightMoments.Moment.HIT) s.xp += XP_BLOW;
        else if (m == FightMoments.Moment.KILL && target != null) {
            MobRole.Role r = MobRole.of(target).role();
            if (r == MobRole.Role.BOSS) s.xp += XP_LORD;
            else if (r == MobRole.Role.ELITE || r == MobRole.Role.WARDEN) s.xp += XP_STRONG;
        }
        if (s.xp >= XP_FLUSH) flush(p, s);
    }

    private static void fill(ServerPlayer p, State s, int amount) {
        int cap = s.full == null ? FULL : cap(s.full);
        if (amount <= 0 || s.charge >= cap) return;
        int was = s.charge;
        s.charge = Math.min(cap, s.charge + amount);
        if (was < FULL && s.charge >= FULL) sound(p, ArmourContent.CHARGED.get(), 0.7F, 1F);
        else if (was < cap && s.charge >= cap) sound(p, ArmourContent.CHARGED.get(), 0.8F, 1.3F); // the second charge
    }

    /** How much charge a set holds: from A a second one, kept for the next release. */
    static int cap(ArmourSet set) {
        return grown(set, dev.awaken.progression.Rank.A) ? 2 * FULL : FULL;
    }

    /** How many runes circle a Channeler before the next cast overchannels them: from A a fourth. */
    static int runeCap(ArmourSet set) {
        return grown(set, dev.awaken.progression.Rank.A) ? RUNES_A : RUNES;
    }

    /** An ability's reach at the set's rank: at S half again as far. */
    static double reach(ArmourSet set, double r) {
        return grown(set, dev.awaken.progression.Rank.S) ? r * S_REACH : r;
    }

    // ---- the four abilities -----------------------------------------------------------------------------------------

    /** Bastion's burst: everyone near thrown back and staggered, the ground cracked around you. */
    static void bastion(ServerPlayer p, State s, ArmourSet set) {
        // from B the burst carries the Force of your last power hit (its damage type, so the Forces' reactions follow)
        Forces.Force force = grown(set, dev.awaken.progression.Rank.B) ? s.force : null;
        if (set == ArmourSet.LEVIATHAN) {
            // Abyss Pull: the deep drags everyone near in first, then the burst
            ServerLevel level = p.level();
            double r = reach(set, ABYSS_PULL_RADIUS);
            for (LivingEntity t : foes(p, p.position(), r)) push(p, t, -ABYSS_PULL, 0.1);
            Fx.at(level, FxKind.VORTEX, FxStyle.KINETIC, p.position().add(0, 0.6, 0)).caster(p).colour(glow(set)).scale(r).ticks(ABYSS_DELAY + 4).send();
            sound(p, ArmourContent.BASTION.get(), 0.7F, 0.55F);
            release(p, s);
            Scheduler.after(ABYSS_DELAY, () -> {
                if (p.isAlive() && !p.isRemoved()) burst(p, s, set, force);
            });
            return;
        }
        burst(p, s, set, force);
        release(p, s);
    }

    private static void burst(ServerPlayer p, State s, ArmourSet set, Forces.@Nullable Force force) {
        ServerLevel level = p.level();
        float dmg = blow(set);
        double r = reach(set, BASTION_RADIUS);
        DamageSource src = force != null ? Combat.source(level, force, null, p) : p.damageSources().playerAttack(p);
        for (LivingEntity t : foes(p, p.position(), r)) {
            hurt(level, t, src, dmg);
            push(p, t, 1.1, 0.35);
            Strikes.stagger(t, BASTION_STAGGER);
        }
        FxStyle style = force != null ? FxStyle.valueOf(force.name()) : FxStyle.KINETIC;
        Fx.at(level, FxKind.SLAM, style, p.position()).caster(p).colour(glow(set)).scale(r).send();
        if (force != null) Fx.at(level, FxKind.NOVA, style, p.position().add(0, 1.0, 0)).caster(p).scale(r * 0.8).send();
        Fx.quake(level, p.position(), 12, 0.8F);
        sound(p, ArmourContent.BASTION.get(), 1F, 1F);
        flash(p, s);
    }

    /**
     * Edge's wave: a cut along the swing for everyone in front but the one struck ({@code toward}: a C Edge's answer to a
     * dodge, cut toward the one dodged).
     */
    static void edge(ServerPlayer p, State s, ArmourSet set, @Nullable LivingEntity struck, @Nullable LivingEntity toward) {
        ServerLevel level = p.level();
        Vec3 look = toward != null ? toward.position().subtract(p.position()) : p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
        double cos = Math.cos(Math.toRadians(EDGE_HALF_ANGLE));
        float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * EDGE_SHARE;
        DamageSource src = p.damageSources().playerAttack(p);
        double range = reach(set, EDGE_RANGE);
        for (LivingEntity t : foes(p, p.position(), range)) {
            if (t == struck) continue;
            Vec3 to = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
            if (to.lengthSqr() > 1e-6 && to.normalize().dot(flat) < cos) continue;
            hurt(level, t, src, dmg);
            // Tidebreaker's Undertow drags them in; any other wave throws them back
            if (set == ArmourSet.TIDEBREAKER) push(p, t, -UNDERTOW_PULL, 0.1);
            else push(p, t, 0.5, 0.15);
            if (grown(set, dev.awaken.progression.Rank.B)) bleed(p, t);
        }
        Fx.at(level, FxKind.CONE, FxStyle.NEUTRAL, p.position().add(0, 1.0, 0)).caster(p).colour(glow(set)).dir(flat)
                .scale(range).aux(EDGE_HALF_ANGLE).ticks(8).send();
        sound(p, ArmourContent.EDGE.get(), 1F, 1F);
        release(p, s);
    }

    /** Overchannel: the runes fly out at the foes nearest you (three, from A four), and your powers hit harder for a moment. */
    static void overchannel(ServerPlayer p, State s, ArmourSet set) {
        ServerLevel level = p.level();
        int n = runeCap(set);
        s.runes = 0;
        s.overUntil = level.getGameTime() + OVERCHANNEL_TICKS;
        runesOut(p, set, n, 1F);
        Fx.at(level, FxKind.NOVA, FxStyle.ENERGY, p.position().add(0, 1.4, 0)).caster(p).colour(glow(set)).scale(2.0).send();
        sound(p, ArmourContent.OVERCHANNEL.get(), 1F, 1F);
        // from B an overchannel gives back a quarter of your energy
        if (grown(set, dev.awaken.progression.Rank.B)) Powers.restoreEnergy(p, Powers.maxEnergy(p) * REFUND);
        if (set == ArmourSet.SUNWARD) noon(p, set);
        if (set == ArmourSet.HALO_CHOIR) hymn(p, set);
        // Signal's Echo: the overchannel comes again a second later, a little fainter
        if (set == ArmourSet.SIGNALS_ECHO) Scheduler.after(ECHO_DELAY, () -> {
            if (!p.isAlive() || p.isRemoved()) return;
            runesOut(p, set, n, ECHO_SHARE);
            Fx.at(p.level(), FxKind.ECHO, FxStyle.ENERGY, p.position().add(0, 1.4, 0)).caster(p).colour(glow(set)).scale(2.0).send();
            sound(p, ArmourContent.OVERCHANNEL.get(), 0.7F, 1.25F);
            flash(p, state(p));
        });
        flash(p, s);
    }

    /** The runes flying at the nearest foes in reach, each the ability's blow (times share) as magic. */
    private static void runesOut(ServerPlayer p, ArmourSet set, int n, float share) {
        ServerLevel level = p.level();
        List<LivingEntity> near = foes(p, p.position(), reach(set, RUNE_REACH));
        near.sort(Comparator.comparingDouble(t -> t.distanceToSqr(p)));
        float dmg = blow(set) * share;
        DamageSource src = p.damageSources().indirectMagic(p, p);
        Vec3 from = p.position().add(0, 1.4, 0);
        for (int i = 0; i < Math.min(n, near.size()); i++) {
            LivingEntity t = near.get(i);
            Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
            Fx.at(level, FxKind.BOLT, FxStyle.ENERGY, from).caster(p).colour(glow(set)).dir(at.subtract(from)).send();
            Fx.at(level, FxKind.IMPACT, FxStyle.ENERGY, at).colour(glow(set)).scale(1.4).send();
            hurt(level, t, src, dmg);
        }
    }

    /** Flow's afterimage: the blow strikes a ghost of you, and you run on faster ({@code by}: who struck it, for Riftwalker). */
    static void afterimage(ServerPlayer p, State s, ArmourSet set, @Nullable LivingEntity by) {
        ServerLevel level = p.level();
        Fx.at(level, FxKind.AFTERIMAGE, FxStyle.FLIGHT, p.position()).caster(p).colour(glow(set)).ticks(16).send();
        // from C those close swing at the ghost and stagger
        if (grown(set, dev.awaken.progression.Rank.C))
            for (LivingEntity t : foes(p, p.position(), reach(set, AFTER_RADIUS))) Strikes.stagger(t, AFTER_STAGGER);
        // Riftwalker's Riftstep: you step out of the rift behind the one who struck the ghost
        if (set == ArmourSet.RIFTWALKER && by != null && riftstep(p, set, by)) {
            s.rushUntil = level.getGameTime() + FLOW_RUSH;
            modify(p, Attributes.MOVEMENT_SPEED, RUSH_ID, FLOW_RUSH_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
            sound(p, ArmourContent.FLOW.get(), 1F, 0.7F);
            release(p, s);
            return;
        }
        // from B a ghost left in the air rushes you on along your look
        if (grown(set, dev.awaken.progression.Rank.B) && Combat.airborne(p)) {
            Vec3 f = p.getLookAngle();
            Vec3 flat = new Vec3(f.x, 0, f.z);
            if (flat.lengthSqr() > 1e-4) {
                Vec3 v = flat.normalize().scale(AIR_RUSH);
                Combat.push(p, new Vec3(v.x, Math.max(p.getDeltaMovement().y, 0) + AIR_LIFT, v.z));
                Fx.at(level, FxKind.DASH, FxStyle.FLIGHT, p.position()).caster(p).colour(glow(set)).dir(v).send();
            }
        }
        s.rushUntil = level.getGameTime() + FLOW_RUSH;
        modify(p, Attributes.MOVEMENT_SPEED, RUSH_ID, FLOW_RUSH_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        Fx.at(level, FxKind.TRAIL, FxStyle.FLIGHT, p.position()).caster(p).colour(glow(set)).aux(0).ticks(FLOW_RUSH).send();
        sound(p, ArmourContent.FLOW.get(), 1F, 1F);
        release(p, s);
    }

    // ---- the twists -------------------------------------------------------------------------------------------------

    /** Bellguard's Ring: the armour rings like a bell on a deflect, staggering everyone close. */
    static void ring(ServerPlayer p, State s, ArmourSet set, long now) {
        s.ringReady = now + RING_COOLDOWN;
        for (LivingEntity t : foes(p, p.position(), RING_RADIUS)) Strikes.stagger(t, RING_STAGGER);
        Fx.at(p.level(), FxKind.RING, FxStyle.NEUTRAL, p.position().add(0, 0.9, 0)).caster(p).colour(glow(set)).scale(RING_RADIUS).ticks(8).send();
        sound(p, ArmourContent.RING.get(), 1F, 1F);
        flash(p, s);
    }

    /** Rootwarden's Rooted: a cast standing still roots you (no knockback) for a moment and mends a little. */
    static void rooted(ServerPlayer p, State s, ArmourSet set, long now) {
        s.rootedReady = now + ROOTED_COOLDOWN;
        s.rootedUntil = now + ROOTED_TICKS;
        modify(p, Attributes.KNOCKBACK_RESISTANCE, ROOTED_ID, 1.0, AttributeModifier.Operation.ADD_VALUE, false);
        p.heal(ROOTED_HEAL);
        Fx.at(p.level(), FxKind.GLYPH, FxStyle.SPIRIT, p.position().add(0, 0.05, 0)).caster(p).colour(glow(set)).scale(1.3).ticks(ROOTED_TICKS).send();
        sound(p, ArmourContent.ROOTED.get(), 0.8F, 1F);
    }

    /** Hornlord's Gore: the first creature you run into with your horns lowered is gored and thrown. */
    static void gore(ServerPlayer p, State s, ArmourSet set, LivingEntity t, long now) {
        s.goreReady = now + GORE_COOLDOWN;
        s.sprint = 0;
        ServerLevel level = p.level();
        hurt(level, t, p.damageSources().playerAttack(p), GORE_DAMAGE + BLOW_RANK * set.rank.ordinal());
        push(p, t, 1.5, 0.45);
        Strikes.stagger(t, GORE_STAGGER);
        Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
        Fx.at(level, FxKind.IMPACT, FxStyle.HEAT, at).caster(p).colour(glow(set)).dir(p.getLookAngle()).scale(2.2).send();
        Fx.at(level, FxKind.SLAM, FxStyle.HEAT, t.position()).caster(p).colour(glow(set)).scale(1.6).send();
        Fx.feel(p, t, 90, 0.6F);
        sound(p, ArmourContent.GORE.get(), 1F, 1F);
        flash(p, s);
    }

    /** Vaultbreaker's Vault Wall: a guarded blow glances off the plate and throws its striker back. */
    static void vault(ServerPlayer p, State s, ArmourSet set, LivingEntity t, long now) {
        s.vaultReady = now + VAULT_COOLDOWN;
        push(p, t, VAULT_PUSH, 0.2);
        Strikes.stagger(t, VAULT_STAGGER);
        Vec3 at = p.position().add(0, 1.1, 0).add(t.position().subtract(p.position()).normalize().scale(0.6));
        Fx.at(p.level(), FxKind.IMPACT, FxStyle.KINETIC, at).caster(p).colour(glow(set)).dir(t.position().subtract(p.position())).scale(1.2).send();
        sound(p, ArmourContent.RING.get(), 0.7F, 1.6F);
        flash(p, s);
    }

    /** Frostwalker's Rime: a perfect dodge breathes frost where you stood, slowing everyone close. */
    static void rime(ServerPlayer p, ArmourSet set) {
        for (LivingEntity t : foes(p, p.position(), RIME_RADIUS)) {
            t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RIME_SLOW, 1), p);
            Forces.tag(t, Forces.Tag.FROSTED);
        }
        Fx.at(p.level(), FxKind.FROST, FxStyle.FROST, p.position().add(0, 0.5, 0)).caster(p).colour(glow(set)).scale(RIME_RADIUS).send();
    }

    /** Frostwalker's path: still water under your feet freezes as you go (vanilla's Frost Walker, a disk of 3). */
    static void freeze(ServerPlayer p) {
        ServerLevel level = p.level();
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        BlockPos under = p.blockPosition().below();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(), above = new BlockPos.MutableBlockPos();
        for (int dx = -RIME_FREEZE; dx <= RIME_FREEZE; dx++)
            for (int dz = -RIME_FREEZE; dz <= RIME_FREEZE; dz++) {
                if (dx * dx + dz * dz > RIME_FREEZE * RIME_FREEZE) continue;
                at.set(under.getX() + dx, under.getY(), under.getZ() + dz);
                above.set(at.getX(), at.getY() + 1, at.getZ());
                if (!level.getBlockState(above).isAir() || !level.getBlockState(at).is(Blocks.WATER)
                        || !level.getFluidState(at).is(Fluids.WATER) || !ice.canSurvive(level, at)
                        || !level.isUnobstructed(ice, at, CollisionContext.empty())) continue;
                level.setBlockAndUpdate(at, ice);
            }
    }

    /** Sunward's Noon: an overchannel flares the sun at your back: foes near burn, you and your friends near mend. */
    static void noon(ServerPlayer p, ArmourSet set) {
        ServerLevel level = p.level();
        for (LivingEntity t : foes(p, p.position(), NOON_RADIUS)) t.igniteForTicks(NOON_FIRE);
        p.heal(NOON_HEAL);
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(NOON_RADIUS),
                o -> o != p && Combat.isAlly(p, o) && o.distanceToSqr(p) <= NOON_RADIUS * NOON_RADIUS)) {
            o.heal(NOON_HEAL);
            Fx.at(level, FxKind.HEAL, FxStyle.HEAT, o.position()).caster(o).colour(glow(set)).ticks(20).send();
        }
        Fx.at(level, FxKind.NOVA, FxStyle.HEAT, p.position().add(0, 1.6, 0)).caster(p).colour(glow(set)).scale(NOON_RADIUS).send();
        Fx.at(level, FxKind.RING, FxStyle.HEAT, p.position().add(0, 0.1, 0)).caster(p).colour(glow(set)).scale(NOON_RADIUS).ticks(10).send();
    }

    /** Shardcrown's Shatter: a blow that takes you under half bursts crystal out of the armour. */
    static void shatter(ServerPlayer p, State s, ArmourSet set, long now) {
        s.shatterReady = now + SHATTER_COOLDOWN;
        s.shatterUntil = now + SHATTER_TICKS;
        ServerLevel level = p.level();
        DamageSource src = p.damageSources().playerAttack(p);
        for (LivingEntity t : foes(p, p.position(), SHATTER_RADIUS)) {
            hurt(level, t, src, blow(set));
            push(p, t, 1.2, 0.4);
        }
        Fx.at(level, FxKind.PILLAR, FxStyle.PRISM, p.position()).caster(p).colour(glow(set)).scale(2.6).send();
        Fx.at(level, FxKind.NOVA, FxStyle.PRISM, p.position().add(0, 1.0, 0)).caster(p).colour(glow(set)).scale(SHATTER_RADIUS).send();
        Fx.quake(level, p.position(), 10, 0.6F);
        sound(p, ArmourContent.BASTION.get(), 1F, 1.4F);
        flash(p, s);
    }

    /** Hollowheart's Pulse: the caged heart beats on every fifth blow, hurting everyone close and mending you for each. */
    static void pulse(ServerPlayer p, State s, ArmourSet set) {
        s.beats = 0;
        ServerLevel level = p.level();
        DamageSource src = p.damageSources().playerAttack(p);
        int struck = 0;
        for (LivingEntity t : foes(p, p.position(), PULSE_RADIUS)) {
            hurt(level, t, src, blow(set));
            struck++;
        }
        if (struck > 0) p.heal(PULSE_HEAL * Math.min(struck, 4));
        Fx.at(level, FxKind.RING, FxStyle.NEUTRAL, p.position().add(0, 1.0, 0)).caster(p).colour(glow(set)).scale(PULSE_RADIUS).ticks(6).send();
        Fx.at(level, FxKind.NOVA, FxStyle.NEUTRAL, p.position().add(0, 1.2, 0)).caster(p).colour(glow(set)).scale(1.4).send();
        sound(p, ArmourContent.GORE.get(), 0.8F, 0.6F);
        flash(p, s);
    }

    /** Galewing's Gust: a perfect dodge beats the wings: everyone close is thrown back and you lift. */
    static void gust(ServerPlayer p, State s, ArmourSet set, long now) {
        s.gustReady = now + GUST_COOLDOWN;
        ServerLevel level = p.level();
        for (LivingEntity t : foes(p, p.position(), GUST_RADIUS)) push(p, t, GUST_PUSH, 0.2);
        Vec3 v = p.getDeltaMovement();
        Combat.push(p, new Vec3(v.x, Math.max(v.y, 0) + GUST_LIFT, v.z));
        Fx.at(level, FxKind.RING, FxStyle.FLIGHT, p.position().add(0, 0.2, 0)).caster(p).colour(glow(set)).scale(GUST_RADIUS).ticks(8).send();
        Fx.at(level, FxKind.LIFT, FxStyle.FLIGHT, p.position()).caster(p).colour(glow(set)).ticks(10).send();
        sound(p, ArmourContent.FLOW.get(), 0.8F, 1.4F);
        flash(p, s);
    }

    /** Halo Choir's Hymn: an overchannel rings the halo; everyone close reels. */
    static void hymn(ServerPlayer p, ArmourSet set) {
        double r = reach(set, HYMN_RADIUS);
        for (LivingEntity t : foes(p, p.position(), r)) Strikes.stagger(t, HYMN_STAGGER);
        Fx.at(p.level(), FxKind.RING, FxStyle.SPIRIT, p.position().add(0, 2.1, 0)).caster(p).colour(glow(set)).scale(r).ticks(12).send();
        sound(p, ArmourContent.RING.get(), 1F, 1.5F);
    }

    /** Dreamsteel's Mirror: a deflected blow comes back on its striker, half as hard. */
    static void mirror(ServerPlayer p, State s, ArmourSet set, LivingEntity t, long now) {
        s.mirrorReady = now + MIRROR_COOLDOWN;
        var attack = t.getAttribute(Attributes.ATTACK_DAMAGE);
        float dmg = Math.max(1F, (attack == null ? 2F : (float) attack.getValue()) * MIRROR_SHARE);
        hurt(p.level(), t, p.damageSources().thorns(p), dmg);
        Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
        Fx.at(p.level(), FxKind.IMPACT, FxStyle.PRISM, at).caster(p).colour(glow(set)).dir(t.position().subtract(p.position())).scale(1.2).send();
        sound(p, ArmourContent.RING.get(), 0.6F, 1.9F);
        flash(p, s);
    }

    /** Starfall's Meteor: a long fall lands like a falling star; everyone close burns and is thrown. */
    static void meteor(ServerPlayer p, State s, ArmourSet set, long now) {
        s.meteorReady = now + METEOR_COOLDOWN;
        ServerLevel level = p.level();
        double r = reach(set, METEOR_RADIUS);
        DamageSource src = Combat.source(level, Forces.Force.HEAT, null, p);
        for (LivingEntity t : foes(p, p.position(), r)) {
            hurt(level, t, src, blow(set));
            t.igniteForTicks(METEOR_FIRE);
            push(p, t, 1.2, 0.5);
        }
        Fx.at(level, FxKind.NOVA, FxStyle.HEAT, p.position().add(0, 0.6, 0)).caster(p).colour(glow(set)).scale(r).send();
        Fx.at(level, FxKind.SLAM, FxStyle.HEAT, p.position()).caster(p).colour(glow(set)).scale(r).send();
        Fx.quake(level, p.position(), 14, 0.9F);
        sound(p, ArmourContent.BASTION.get(), 1F, 0.75F);
        flash(p, s);
    }

    /** Riftwalker's Riftstep: out of the rift a step behind the one who struck, facing them; false if there's no room. */
    static boolean riftstep(ServerPlayer p, ArmourSet set, LivingEntity by) {
        if (by.level() != p.level() || by.distanceToSqr(p) > RIFT_REACH * RIFT_REACH) return false;
        Vec3 away = new Vec3(by.getX() - p.getX(), 0, by.getZ() - p.getZ());
        if (away.lengthSqr() < 1e-4) return false;
        Vec3 to = by.position().add(away.normalize().scale(by.getBbWidth() / 2 + RIFT_BEHIND));
        ServerLevel level = p.level();
        AABB box = p.getBoundingBox().move(to.subtract(p.position()));
        if (!level.noCollision(p, box)) return false;
        Vec3 from = p.position();
        float yaw = (float) Math.toDegrees(Math.atan2(-(by.getX() - to.x), by.getZ() - to.z));
        Fx.at(level, FxKind.BLINK, FxStyle.FLIGHT, from.add(0, 1, 0)).caster(p).colour(glow(set)).dir(to.subtract(from)).send();
        p.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, p.getXRot(), false);
        Fx.at(level, FxKind.BLINK, FxStyle.FLIGHT, to.add(0, 1, 0)).caster(p).colour(glow(set)).send();
        return true;
    }

    /** A B wave's bleeding: a little every half second for three seconds. */
    private static void bleed(ServerPlayer p, LivingEntity t) {
        for (int k = BLEED_EVERY; k <= BLEED_TICKS; k += BLEED_EVERY) {
            Scheduler.after(k, () -> {
                if (!t.isAlive() || p.isRemoved() || !(t.level() instanceof ServerLevel level)) return;
                hurt(level, t, p.damageSources().playerAttack(p), BLEED);
                Fx.at(level, FxKind.IMPACT, FxStyle.VELOCITY, t.position().add(0, t.getBbHeight() * 0.6, 0)).scale(0.4).send();
            });
        }
    }

    // ---- ticks and blows ----------------------------------------------------------------------------------------------

    static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = state(p);
        if (s.dirty) count(p, s);
        long now = p.level().getGameTime();
        expire(p, s, now);
        ArmourSet set = s.full;
        s.moving = p.getDeltaMovement().horizontalDistanceSqr() > 4.0E-4;
        if (set != null) {
            // from C a hard landing bursts a full Bastion (Starfall's long fall lands as a meteor first)
            if (ability(set) == Archetype.BULWARK && grown(set, dev.awaken.progression.Rank.C)) {
                if (!p.onGround()) s.fall = Math.max(s.fall, p.fallDistance);
                else {
                    if (set == ArmourSet.STARFALL && s.fall >= METEOR_FALL && now >= s.meteorReady) meteor(p, s, set, now);
                    if (s.fall >= SLAM_FALL && s.charge >= FULL) bastion(p, s, set);
                    s.fall = 0;
                }
            }
            if (set == ArmourSet.LEVIATHAN && p.isUnderWater()) {
                MobEffectInstance had = p.getEffect(MobEffects.WATER_BREATHING);
                if (had == null || had.getDuration() < 10) p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, false, false, false));
            }
            if (set == ArmourSet.FROSTWALKER && p.onGround() && s.moving && !p.isPassenger()) freeze(p);
            if (set == ArmourSet.CLOUDRUNNER && p.isShiftKeyDown() && Combat.airborne(p) && p.getDeltaMovement().y < -0.1
                    && !p.isFallFlying()) {
                MobEffectInstance had = p.getEffect(MobEffects.SLOW_FALLING);
                if (had == null || had.getDuration() < 4) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false, false));
            }
            if (ability(set) == Archetype.STRIDER) {
                if (MovementHooks.wallRunning(p)) fill(p, s, FLOW_WALL);
                else if (MovementHooks.sliding(p)) fill(p, s, FLOW_SLIDE);
            }
            if (set == ArmourSet.CLEFTRUNNER) {
                boolean running = MovementHooks.wallRunning(p) || MovementHooks.sliding(p);
                if (!running && s.sprint < 0) { // the run just ended (sprint < 0 marks a wall-run or slide in progress)
                    s.slipUntil = now + SLIP_TICKS;
                    modify(p, Attributes.MOVEMENT_SPEED, SLIP_ID, SLIP_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
                    Fx.at(p.level(), FxKind.TRAIL, FxStyle.FLIGHT, p.position()).caster(p).colour(glow(set)).aux(0).ticks(SLIP_TICKS).send();
                }
                s.sprint = running ? -1 : 0;
            }
            if (set == ArmourSet.HORNLORD) {
                s.sprint = p.isSprinting() && p.onGround() ? s.sprint + 1 : 0;
                // the horns' charge shows as the set's charge: they glow as they lower
                s.charge = now >= s.goreReady ? Math.min(FULL, s.sprint * FULL / GORE_SPRINT) : 0;
                if (s.sprint >= GORE_SPRINT && now >= s.goreReady) {
                    LivingEntity t = ahead(p);
                    if (t != null) gore(p, s, set, t, now);
                }
            }
        }
        if (s.runes > 0 && now >= s.runesUntil) s.runes = 0;
        sync(p, s, 0);
    }

    /** Ends what has run its time. */
    private static void expire(ServerPlayer p, State s, long now) {
        if (s.rushUntil != 0 && now >= s.rushUntil) {
            s.rushUntil = 0;
            modify(p, Attributes.MOVEMENT_SPEED, RUSH_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        }
        if (s.tempoUntil != 0 && now >= s.tempoUntil) {
            s.tempoUntil = 0;
            modify(p, Attributes.ATTACK_SPEED, TEMPO_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
            modify(p, Attributes.MOVEMENT_SPEED, TEMPO_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        }
        if (s.slipUntil != 0 && now >= s.slipUntil) {
            s.slipUntil = 0;
            modify(p, Attributes.MOVEMENT_SPEED, SLIP_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        }
        if (s.rootedUntil != 0 && now >= s.rootedUntil) {
            s.rootedUntil = 0;
            modify(p, Attributes.KNOCKBACK_RESISTANCE, ROOTED_ID, 0, AttributeModifier.Operation.ADD_VALUE, false);
        }
    }

    /** Blows on a player in a set (Flow's afterimage, Parry Ward, Kindle) and blows by one (Channeler power). */
    static void onIncoming(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (e.getEntity() instanceof ServerPlayer p) {
            State s = state(p);
            if (s.dirty) count(p, s);
            ArmourSet set = s.full;
            if (set == ArmourSet.CLOUDRUNNER && src.is(DamageTypeTags.IS_FALL)) {
                e.setCanceled(true);
                return;
            }
            // Starfall: falls never hurt you; a long one lands as a meteor (the landing itself is the tick's)
            if (set == ArmourSet.STARFALL && src.is(DamageTypeTags.IS_FALL)) {
                e.setCanceled(true);
                return;
            }
            Strikes.Kind kind = Strikes.of(src);
            if (set != null && kind != Strikes.Kind.NONE) {
                if (ability(set) == Archetype.STRIDER && s.charge >= FULL) {
                    e.setCanceled(true);
                    afterimage(p, s, set, src.getEntity() instanceof LivingEntity by ? by : null);
                    return;
                }
                long now = p.level().getGameTime();
                // from C each rune stops one arrow or bolt
                if (ability(set) == Archetype.CHANNELER && grown(set, dev.awaken.progression.Rank.C) && s.runes > 0
                        && src.is(DamageTypeTags.IS_PROJECTILE)) {
                    e.setCanceled(true);
                    s.runes--;
                    Fx.at(p.level(), FxKind.IMPACT, FxStyle.ENERGY, p.position().add(0, 1.3, 0)).caster(p).colour(glow(set)).scale(0.9).send();
                    sound(p, ArmourContent.RUNE.get(), 0.7F, 0.7F);
                    return;
                }
                if (set == ArmourSet.BLADEWARD && now < s.wardUntil) e.setAmount(e.getAmount() * WARD_SHARE);
                if (set == ArmourSet.SHARDCROWN && now < s.shatterUntil) e.setAmount(e.getAmount() * SHATTER_SHARE);
                if (set == ArmourSet.HALO_CHOIR && s.runes >= RUNES) e.setAmount(e.getAmount() * HYMN_SHARE);
                if (shelled(p, s)) {
                    e.setAmount(e.getAmount() * SHELL_SHARE);
                    Fx.at(p.level(), FxKind.IMPACT, FxStyle.NEUTRAL, p.position().add(0, 1.0, 0)).caster(p).colour(glow(set)).scale(0.7).send();
                }
                if (set == ArmourSet.LANTERNLIGHT && s.runes > 0 && src.getEntity() instanceof LivingEntity by && src.getDirectEntity() == by
                        && by.distanceToSqr(p) <= KINDLE_REACH * KINDLE_REACH) {
                    by.igniteForTicks(KINDLE_TICKS);
                    Fx.at(p.level(), FxKind.IMPACT, FxStyle.HEAT, by.position().add(0, by.getBbHeight() * 0.5, 0)).colour(glow(set)).scale(0.8).send();
                }
            }
        }
        if (src.getEntity() instanceof ServerPlayer by && by != e.getEntity() && GearCombat.isPower(src)) {
            State s = state(by);
            Forces.Force f = Forces.forceOf(src);
            if (f != null) s.force = f;
            float more = s.power + (by.level().getGameTime() < s.overUntil ? OVERCHANNEL : 0);
            if (more > 0) e.setAmount(e.getAmount() * (1 + more));
        } else if (src.getEntity() instanceof ServerPlayer by && by != e.getEntity() && src.getDirectEntity() == by) {
            // Nightcourt's Riposte: the first blade blow after a deflect lands hard
            State s = state(by);
            if (s.full == ArmourSet.NIGHTCOURT && by.level().getGameTime() < s.riposteUntil) {
                s.riposteUntil = 0;
                e.setAmount(e.getAmount() * (1 + RIPOSTE));
                Fx.at(by.level(), FxKind.STRIKE, FxStyle.NEUTRAL, e.getEntity().position().add(0, e.getEntity().getBbHeight() * 0.6, 0)).caster(by)
                        .colour(glow(s.full)).dir(e.getEntity().position().subtract(by.position())).scale(1.3).send();
            }
        }
    }

    /** A blow that landed on a player in a whole set fills Bastion and counts toward mastery. */
    static void onDamaged(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || Strikes.of(e.getSource()) == Strikes.Kind.NONE || e.getInflictedDamage() <= 0) return;
        State s = state(p);
        if (s.full == null) return;
        if (ability(s.full) == Archetype.BULWARK) fill(p, s, shelled(p, s) ? BASTION_TAKEN * 2 : BASTION_TAKEN);
        long now = p.level().getGameTime();
        if (s.full == ArmourSet.SHARDCROWN && now >= s.shatterReady && p.isAlive() && p.getHealth() < p.getMaxHealth() / 2
                && p.getHealth() + e.getHealthDamage() >= p.getMaxHealth() / 2) shatter(p, s, s.full, now);
        s.xp += XP_BLOW;
        if (s.xp >= XP_FLUSH) flush(p, s);
    }

    /** A player who comes into view is told the charge others already see. */
    static void onTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer p && e.getEntity() instanceof ServerPlayer viewer) {
            State s = state(p);
            if (s.charge > 0 || s.runes > 0) dev.awaken.core.AwakenNet.send(viewer, new ArmourPayloads.Charge(p.getId(), Math.min(FULL, s.charge), s.runes, 0));
        }
    }

    static void onLogout(ServerPlayer p) {
        flush(p, state(p));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** Deepcarapace's Shell: closed while the wearer sneaks and stands still. */
    static boolean shelled(ServerPlayer p, State s) {
        return s.full == ArmourSet.DEEPCARAPACE && p.isShiftKeyDown() && !s.moving;
    }

    /** A release spends one charge (from A a second may be waiting). */
    private static void release(ServerPlayer p, State s) {
        s.charge = Math.max(0, s.charge - FULL);
        flash(p, s);
    }

    /** The armour flares for everyone watching. */
    private static void flash(ServerPlayer p, State s) {
        sync(p, s, 255);
    }

    /** How far others see a set's charge (a player's tracking range). */
    private static final double SYNC_RANGE = 80;

    /** The charge and runes to the wearer and everyone near enough to see them, when they change enough to see. */
    private static void sync(ServerPlayer p, State s, int flash) {
        boolean changed = s.runes != s.sentRunes || Math.abs(s.charge - s.sentCharge) >= 5 || (s.charge >= FULL) != (s.sentCharge >= FULL)
                || (s.charge == 0) != (s.sentCharge == 0);
        if (!changed && flash == 0) return;
        s.sentCharge = s.charge;
        s.sentRunes = s.runes;
        // through AwakenNet: a test's or another mod's fake player has no channel for it, and sending would throw
        ArmourPayloads.Charge msg = new ArmourPayloads.Charge(p.getId(), Math.min(FULL, s.charge), s.runes, flash);
        for (ServerPlayer o : ((ServerLevel) p.level()).players())
            if (o == p || o.distanceToSqr(p) < SYNC_RANGE * SYNC_RANGE) AwakenNet.send(o, msg);
    }

    /** Writes the experience earned so far onto every piece of the whole set worn. */
    static void flush(ServerPlayer p, State s) {
        if (s.xp <= 0) return;
        int xp = s.xp;
        s.xp = 0;
        if (s.full == null) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = p.getItemBySlot(slot);
            ArmourLooks.Look l = ArmourLooks.of(stack);
            if (l == null || l.set() != s.full) continue; // a piece just taken off loses these few
            Forged f = stack.get(ArmourContent.FORGED.get());
            if (f == null) f = Forged.plain(s.full);
            int before = f.masteryStep();
            Forged after = f.withMastery(f.mastery() + xp);
            stack.set(ArmourContent.FORGED.get(), after);
            if (after.masteryStep() > before && slot == EquipmentSlot.CHEST) {
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your " + s.full.setName + " has grown into mastery "
                        + Forged.numeral(after.masteryStep()) + ".").withColor(s.full.rank.colour), false);
                sound(p, ArmourContent.WAKE.get(), 0.6F, 1.2F);
            }
        }
    }

    /** An ability's own blow at the set's rank. */
    static float blow(ArmourSet set) {
        return BLOW + BLOW_RANK * set.rank.ordinal();
    }

    private static void hurt(ServerLevel level, LivingEntity t, DamageSource src, float dmg) {
        t.damageCooldownTime = 0;
        t.hurtServer(level, src, t.getMaxHealth() >= BIG_BODY ? Math.min(dmg, Math.max(2F, t.getMaxHealth() * BIG_SHARE)) : dmg);
    }

    private static void push(ServerPlayer p, LivingEntity t, double strength, double lift) {
        double dx = t.getX() - p.getX(), dz = t.getZ() - p.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-4) {
            Vec3 f = p.getLookAngle();
            dx = f.x;
            dz = f.z;
            d = Math.max(1e-4, Math.sqrt(dx * dx + dz * dz));
        }
        double k = strength * (1 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (k <= 0) return;
        t.setDeltaMovement(t.getDeltaMovement().add(dx / d * k, lift * k, dz / d * k));
        t.needsSync = true;
    }

    static List<LivingEntity> foes(ServerPlayer p, Vec3 at, double r) {
        return new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                t -> t != p && Combat.isFoe(p, t) && t.distanceToSqr(at) <= r * r));
    }

    /** The first foe close in front of a charging Hornlord. */
    private static @Nullable LivingEntity ahead(ServerPlayer p) {
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 c = p.position().add(f.scale(0.9)).add(0, 0.9, 0);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity t : foes(p, c, GORE_REACH)) {
            Vec3 to = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
            if (to.lengthSqr() > 1e-6 && to.normalize().dot(f) < 0.5) continue;
            double d = t.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void modify(ServerPlayer p, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op, boolean permanent) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(id);
        if (Math.abs(amount) < 1e-9) {
            if (existing != null) inst.removeModifier(id);
            return;
        }
        if (existing != null && existing.amount() == amount && existing.operation() == op) return;
        inst.removeModifier(id);
        AttributeModifier m = new AttributeModifier(id, amount, op);
        if (permanent) inst.addPermanentModifier(m);
        else inst.addTransientModifier(m);
    }

    /** A set's glow: the colour its abilities light (its accent). */
    public static int glow(ArmourSet set) {
        return switch (set) {
            case WILDHIDE -> 0xFFF2A541;
            case BEASTBOUND -> 0xFFE07A3A;
            case GATEWROUGHT -> 0xFF7FC3FF;
            case VEILWOVEN -> 0xFFB8A6FF;
            case LORDSCALE -> 0xFFFF6A2A;
            case SOVEREIGN -> 0xFFC9B8FF;
            case BELLGUARD -> 0xFF6EC4A4;
            case ROOTWARDEN -> 0xFFFFC25A;
            case AMBERWING -> 0xFFF49A2A;
            case CLEFTRUNNER -> 0xFF6AA8DC;
            case LANTERNLIGHT -> 0xFFFFD88A;
            case BLADEWARD -> 0xFFF05A4E;
            case HORNLORD -> 0xFFFFAE3E;
            case VAULTBREAKER -> 0xFFE8F4FF;
            case FROSTWALKER -> 0xFF9AD8FF;
            case SUNWARD -> 0xFFFFD27A;
            case SHARDCROWN -> 0xFFB08BFF;
            case DEEPCARAPACE -> 0xFF5FE0D0;
            case CLOUDRUNNER -> 0xFFBFE2FF;
            case TIDEBREAKER -> 0xFFA98BFF;
            case HOLLOWHEART -> 0xFFF0504A;
            case LEVIATHAN -> 0xFF4FD4A6;
            case GALEWING -> 0xFFB0D4FF;
            case HALO_CHOIR -> 0xFFEFBF55;
            case DREAMSTEEL -> 0xFFF2D8FF;
            case STARFALL -> 0xFFFFE2A0;
            case RIFTWALKER -> 0xFFA88CFF;
            case NIGHTCOURT -> 0xFFE8EEFF;
            case SIGNALS_ECHO -> 0xFF98D4FF;
            default -> 0xFFD6ECFF;
        };
    }
}
