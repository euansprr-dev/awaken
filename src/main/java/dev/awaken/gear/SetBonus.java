package dev.awaken.gear;

import dev.awaken.Awaken;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Set bonuses: every tier's twist. Two pieces of one tier give the half bonus, four the full one; tiers mix freely with
 * each other and with vanilla armour (two Wildhide + two Beastbound gives both half bonuses).
 * <ul>
 *   <li>E Wildhide (Perception, the hunt): sneak faster; full set: Pounce, crouch half a second and jump to leap far the
 *       way you look; the first melee hit soon after lands hard (SPEC §44.6; Hunter's Sense, which showed creatures through
 *       walls, is gone: abilities never show hidden things).</li>
 *   <li>D Beastbound (Strength, Vitality): more health; full set: Cornered, below half health +15% melee damage
 *       (+1% per 2 STR above 5, up to +30%) and knockback resistance.</li>
 *   <li>C Gatewrought (the Five Forces): force damage taken -15% / -25%; full set: a power hit primes your next melee
 *       hit (+35%, carrying the Force: Heat burns, Frost slows, Charge hits harder, Gravity lifts, Spirit mends you).</li>
 *   <li>B Veilwoven (the Veil, Spirit): spirit damage taken -30%, meditating mends; full set: Veil Flicker, below 40%
 *       health a hit can pass through you (10% + 1% per SPI above 5, up to 35%; once per 8 s).</li>
 *   <li>A Lordscale (the Lords' Forces): Gravity and Charge damage taken -20%; full set: Overflow, every fifth power hit
 *       of one Force bursts around the target (half the hit to every foe within 4 blocks, with the Force's touch).</li>
 *   <li>S Sovereign Weave (the Hollow Court): Unbowed, dying in a gate below your rank leaves no Fracture; full set:
 *       Sovereign's Measure, your highest stat counts half again (its attribute bonus, powers +0.75% damage a point
 *       above 5, and the stat-scaled parts of these sets).</li>
 * </ul>
 * Attribute parts are re-applied every 10 ticks per player (staggered) and on equipment changes; combat parts live in
 * {@link GearCombat}.
 */
public final class SetBonus {
    private SetBonus() {}

    public static final int HALF = 2;
    public static final int FULL = 4;

    // ---- numbers (SPEC-worthy; docs/modules/gear.md) ------------------------------------------------------------------
    public static final double SNEAK_HALF = 0.15, SNEAK_FULL = 0.30;
    /** Pounce (Wildhide, four pieces): crouched this long before the jump; the leap's speed along your look and up. */
    public static final int POUNCE_CROUCH = 10;
    public static final double POUNCE_FORWARD = 0.95, POUNCE_UP = 0.62;
    /** Ticks before the next Pounce; ticks after the leap its first melee hit lands hard; that hit's bonus and shove. */
    public static final int POUNCE_COOLDOWN = 60, POUNCE_WINDOW = 50;
    public static final double POUNCE_BONUS = 0.5, POUNCE_SHOVE = 0.7;
    public static final double HEALTH_HALF = 2.0, HEALTH_FULL = 4.0;
    public static final double CORNERED_BELOW = 0.5, CORNERED_KNOCKBACK = 0.3;
    public static final double WARD_HALF = 0.15, WARD_FULL = 0.25;
    public static final double PRIME_BONUS = 0.35, PRIME_CHARGE_EXTRA = 0.15, PRIME_SPIRIT_LEECH = 0.15;
    public static final int PRIME_TICKS = 80;
    public static final double SPIRIT_WARD = 0.30;
    public static final int MEDITATION_HEAL_TICKS = 40;
    public static final double FLICKER_BELOW = 0.4;
    public static final int FLICKER_COOLDOWN = 160;
    public static final double LORD_WARD = 0.20;
    public static final int OVERFLOW_EVERY = 5;
    public static final double OVERFLOW_SHARE = 0.5, OVERFLOW_RADIUS = 4.0, OVERFLOW_CHARGE_EXTRA = 0.15, OVERFLOW_SPIRIT_LEECH = 0.10;
    /** Sovereign's Measure: the highest stat's points above 5 count this much again. */
    public static final double MEASURE = 0.5;
    /** Powers scale 1.5% a point of their stat (PowerMath.statScale); the Measure adds half of that for the highest stat. */
    public static final double MEASURE_POWER_PER_POINT = 0.0075;

    public static final Identifier SNEAK_ID = Awaken.id("gear_wildhide_sneak");
    public static final Identifier HEALTH_ID = Awaken.id("gear_beastbound_health");
    public static final Identifier CORNERED_ID = Awaken.id("gear_cornered");
    public static final Identifier MEASURE_ID = Awaken.id("gear_sovereign_measure");

    public record Line(int pieces, String text) {}

    /** What each tier's set does, for tooltips and the {@code /awaken gear} command. */
    public static List<Line> lines(HunterTier tier) {
        return switch (tier) {
            case E -> List.of(new Line(HALF, "sneak 15% faster"),
                    new Line(FULL, "Pounce: crouch, then jump to leap far; your first hit after it lands hard"));
            case D -> List.of(new Line(HALF, "+1 heart"),
                    new Line(FULL, "+2 hearts; Cornered: below half health, +15% melee damage (more with STR) and you hold your ground"));
            case C -> List.of(new Line(HALF, "Force damage taken -15%"),
                    new Line(FULL, "-25%; a power hit primes your next melee hit: +35% and it carries the Force"));
            case B -> List.of(new Line(HALF, "Spirit damage taken -30%; meditating mends you"),
                    new Line(FULL, "Veil Flicker: below 40% health a hit may pass through you (more with SPI)"));
            case A -> List.of(new Line(HALF, "Gravity and Charge damage taken -20%"),
                    new Line(FULL, "Overflow: every fifth power hit of one Force bursts around the target"));
            case S -> List.of(new Line(HALF, "Unbowed: dying in a gate below your rank leaves no Fracture"),
                    new Line(FULL, "Sovereign's Measure: your highest stat counts half again, for gear and powers"));
        };
    }

    // ---- counting ---------------------------------------------------------------------------------------------------

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Pieces of {@code tier} worn in the four armour slots. */
    public static int count(LivingEntity e, HunterTier tier) {
        int n = 0;
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack s = e.getItemBySlot(slot);
            if (s.getItem() instanceof HunterGearItem g && g.tier == tier && g.piece.isArmour()) n++;
        }
        return n;
    }

    public static boolean has(LivingEntity e, HunterTier tier, int pieces) {
        return count(e, tier) >= pieces;
    }

    /** Whether a jump now is a Pounce: four Wildhide pieces, crouched long enough, and not on cooldown. */
    public static boolean pounces(Player p, int crouchedTicks, long now, long readyAt) {
        return crouchedTicks >= POUNCE_CROUCH && now >= readyAt && has(p, HunterTier.E, FULL);
    }

    /** The leap's speed: along the look's heading, and up. */
    public static net.minecraft.world.phys.Vec3 pounceVelocity(float yRot) {
        double a = Math.toRadians(yRot);
        return new net.minecraft.world.phys.Vec3(-Math.sin(a) * POUNCE_FORWARD, POUNCE_UP, Math.cos(a) * POUNCE_FORWARD);
    }

    public static double corneredBonus(int strength) {
        return Math.min(0.30, 0.15 + 0.01 * (Math.max(0, strength - 5) / 2));
    }

    public static double forceWard(int pieces) {
        return pieces >= FULL ? WARD_FULL : pieces >= HALF ? WARD_HALF : 0;
    }

    public static double flickerChance(int spirit) {
        return Math.min(0.35, 0.10 + 0.01 * Math.max(0, spirit - 5));
    }

    /** Lordscale's ward against the Sky Leviathan's and the Herald's Forces. */
    public static double lordWard(int pieces) {
        return pieces >= HALF ? LORD_WARD : 0;
    }

    /** Unbowed (two Sovereign Weave pieces): a death in a gate below your rank leaves no Fracture. */
    public static boolean waivesFracture(int pieces, int playerRank, int gateRank) {
        return pieces >= HALF && gateRank >= 0 && gateRank < playerRank;
    }

    // ---- Sovereign's Measure ----------------------------------------------------------------------------------------

    private static final Stat[] STATS = Stat.values();

    /** The player's highest stat (ties: the first in STR, AGI, VIT, INT, PER, SPI order). */
    public static Stat highest(Player p) {
        Stat best = Stat.STR;
        int v = Integer.MIN_VALUE;
        for (Stat s : STATS) {
            int n = Progression.stat(p, s);
            if (n > v) {
                v = n;
                best = s;
            }
        }
        return best;
    }

    /** Extra points the Measure gives a stat of {@code raw}: half its points above 5. */
    public static int measurePoints(int raw) {
        return (int) Math.floor(Math.max(0, raw - Stat.START) * MEASURE);
    }

    /** Whether Sovereign's Measure is on (four Sovereign Weave pieces). */
    public static boolean measure(LivingEntity e) {
        return count(e, HunterTier.S) >= FULL;
    }

    /**
     * A stat as gear sees it: the player's own, plus Sovereign's Measure on the highest one. Cornered and Veil Flicker read
     * this, so the Measure lifts them too.
     */
    public static int stat(Player p, Stat s) {
        int raw = Progression.stat(p, s);
        return measure(p) && highest(p) == s ? raw + measurePoints(raw) : raw;
    }

    /** The Measure's extra share of power damage for this player (0 without the full set). */
    public static double measurePower(Player p) {
        if (!measure(p)) return 0;
        return MEASURE_POWER_PER_POINT * Math.max(0, Progression.stat(p, highest(p)) - Stat.START);
    }

    // ---- applying ---------------------------------------------------------------------------------------------------

    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || (sp.tickCount + sp.getId()) % 10 != 0) return;
        refresh(sp);
    }

    /** Re-applies every attribute part of the set bonuses for what the player wears now. Cheap and idempotent. */
    public static void refresh(ServerPlayer sp) {
        int wild = count(sp, HunterTier.E);
        int beast = count(sp, HunterTier.D);
        int veil = count(sp, HunterTier.B);
        modify(sp, Attributes.SNEAKING_SPEED, SNEAK_ID, wild >= FULL ? SNEAK_FULL : wild >= HALF ? SNEAK_HALF : 0, false);
        modify(sp, Attributes.MAX_HEALTH, HEALTH_ID, beast >= FULL ? HEALTH_FULL : beast >= HALF ? HEALTH_HALF : 0, true);
        applyMeasure(sp);
        boolean cornered = beast >= FULL && sp.isAlive() && sp.getHealth() < sp.getMaxHealth() * CORNERED_BELOW;
        double bonus = cornered ? corneredBonus(stat(sp, Stat.STR)) : 0;
        modify(sp, Attributes.ATTACK_DAMAGE, CORNERED_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modify(sp, Attributes.KNOCKBACK_RESISTANCE, CORNERED_ID, cornered ? CORNERED_KNOCKBACK : 0, false);
        if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());
        if (veil >= HALF && sp.isAlive() && dev.awaken.veil.Veil.isMeditating(sp)) {
            GearState st = GearState.of(sp);
            long now = sp.level().getGameTime();
            if (now - st.lastHeal >= MEDITATION_HEAL_TICKS && sp.getHealth() < sp.getMaxHealth()) {
                st.lastHeal = now;
                sp.heal(1.0f);
            }
        }
    }

    /**
     * Sovereign's Measure on the attribute stats, with progression's own numbers (STR +2.5% attack a point, AGI +0.4% speed
     * and +1% attack speed a point under its 40% / 60% caps, VIT +0.6 health a point) for the extra points.
     */
    static void applyMeasure(ServerPlayer sp) {
        boolean on = measure(sp);
        Stat hi = on ? highest(sp) : null;
        int str = Progression.stat(sp, Stat.STR), agi = Math.max(0, Progression.stat(sp, Stat.AGI) - Stat.START);
        double strExtra = hi == Stat.STR ? measurePoints(str) * 0.025 : 0;
        int agiExtra = hi == Stat.AGI ? measurePoints(agi + Stat.START) : 0;
        double speed = Math.min(0.40, (agi + agiExtra) * 0.004) - Math.min(0.40, agi * 0.004);
        double swing = Math.min(0.60, (agi + agiExtra) * 0.01) - Math.min(0.60, agi * 0.01);
        double health = hi == Stat.VIT ? measurePoints(Progression.stat(sp, Stat.VIT)) * 0.6 : 0;
        modify(sp, Attributes.ATTACK_DAMAGE, MEASURE_ID, strExtra, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modify(sp, Attributes.MOVEMENT_SPEED, MEASURE_ID, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modify(sp, Attributes.ATTACK_SPEED, MEASURE_ID, swing, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, false);
        modify(sp, Attributes.MAX_HEALTH, MEASURE_ID, health, AttributeModifier.Operation.ADD_VALUE, true);
    }

    /** Whether the Cornered bonus is on right now (for tests and the command). */
    public static boolean cornered(Player p) {
        AttributeInstance a = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        return a != null && a.getModifier(CORNERED_ID) != null;
    }

    private static void modify(ServerPlayer p, Holder<Attribute> attr, Identifier id, double amount, boolean permanent) {
        modify(p, attr, id, amount, AttributeModifier.Operation.ADD_VALUE, permanent);
    }

    /**
     * Adds, updates or removes one modifier. Max health is permanent (saved with the player) so a relog never clamps
     * your health below the bonus before the next refresh; everything else is transient and re-applied.
     */
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
}
