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
 *   <li>E Wildhide (Perception): sneak faster; full set: Hunter's Sense, sneak to see creatures within 12 + PER/2 blocks
 *       through walls (drawn by the client, {@code gear.client.HunterSense}).</li>
 *   <li>D Beastbound (Strength, Vitality): more health; full set: Cornered, below half health +15% melee damage
 *       (+1% per 2 STR above 5, up to +30%) and knockback resistance.</li>
 *   <li>C Gatewrought (the Five Forces): force damage taken -15% / -25%; full set: a power hit primes your next melee
 *       hit (+35%, carrying the Force: Heat burns, Frost slows, Charge hits harder, Gravity lifts, Spirit mends you).</li>
 *   <li>B Veilwoven (the Veil, Spirit): spirit damage taken -30%, meditating mends; full set: Veil Flicker, below 40%
 *       health a hit can pass through you (10% + 1% per SPI above 5, up to 35%; once per 8 s).</li>
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
    public static final double HEALTH_HALF = 2.0, HEALTH_FULL = 4.0;
    public static final double CORNERED_BELOW = 0.5, CORNERED_KNOCKBACK = 0.3;
    public static final double WARD_HALF = 0.15, WARD_FULL = 0.25;
    public static final double PRIME_BONUS = 0.35, PRIME_CHARGE_EXTRA = 0.15, PRIME_SPIRIT_LEECH = 0.15;
    public static final int PRIME_TICKS = 80;
    public static final double SPIRIT_WARD = 0.30;
    public static final int MEDITATION_HEAL_TICKS = 40;
    public static final double FLICKER_BELOW = 0.4;
    public static final int FLICKER_COOLDOWN = 160;

    public static final Identifier SNEAK_ID = Awaken.id("gear_wildhide_sneak");
    public static final Identifier HEALTH_ID = Awaken.id("gear_beastbound_health");
    public static final Identifier CORNERED_ID = Awaken.id("gear_cornered");

    public record Line(int pieces, String text) {}

    /** What each tier's set does, for tooltips and the {@code /awaken gear} command. */
    public static List<Line> lines(HunterTier tier) {
        return switch (tier) {
            case E -> List.of(new Line(HALF, "sneak 15% faster"),
                    new Line(FULL, "Hunter's Sense: sneak to see creatures through walls (12 + PER/2 blocks)"));
            case D -> List.of(new Line(HALF, "+1 heart"),
                    new Line(FULL, "+2 hearts; Cornered: below half health, +15% melee damage (more with STR) and you hold your ground"));
            case C -> List.of(new Line(HALF, "Force damage taken -15%"),
                    new Line(FULL, "-25%; a power hit primes your next melee hit: +35% and it carries the Force"));
            case B -> List.of(new Line(HALF, "Spirit damage taken -30%; meditating mends you"),
                    new Line(FULL, "Veil Flicker: below 40% health a hit may pass through you (more with SPI)"));
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

    public static int senseRadius(int perception) {
        return Math.min(32, 12 + Math.max(0, perception) / 2);
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
        boolean cornered = beast >= FULL && sp.isAlive() && sp.getHealth() < sp.getMaxHealth() * CORNERED_BELOW;
        double bonus = cornered ? corneredBonus(Progression.stat(sp, Stat.STR)) : 0;
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
