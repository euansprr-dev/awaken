package dev.awaken.mobs;

import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobHooks;
import dev.awaken.core.api.MobRole;
import dev.awaken.mobs.def.Tier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * SPEC section 2 mob scaling (health 1+0.075lv, damage 1+0.04lv, armour +lv/12) with the same role multipliers as
 * {@code MobHooks.scale} (elite x2.5, warden x5, roaming Lord of a broken gate x8 and x1.3 damage, boss x14 health and
 * x1.6 damage) and the story's Ascension tier. Unlike the core fallback this uses
 * <b>permanent</b> modifiers so a scaled mob keeps its health across chunk unloads and restarts.
 */
public final class MobScaling {
    private MobScaling() {}

    static final Identifier HEALTH = Awaken.id("mobs_level_health");
    static final Identifier DAMAGE = Awaken.id("mobs_level_damage");
    static final Identifier ARMOR = Awaken.id("mobs_level_armor");
    static final Identifier ROLE_SCALE = Awaken.id("mobs_role_scale");

    public static double healthMultiplier(int lv) {
        return 1.0 + 0.075 * lv;
    }

    public static double damageMultiplier(int lv) {
        return 1.0 + 0.04 * lv;
    }

    public static double armorBonus(int lv) {
        return lv / 12.0;
    }

    /** Health multiplier of a role, matching MobHooks.scale; nightmares sit between wardens and bosses. */
    public static double roleHealth(MobRole.Role role) {
        return switch (role) {
            case ELITE -> 2.5;
            case WARDEN -> 5.0;
            case BREAK -> 8.0;
            case BOSS -> 14.0;
            case NIGHTMARE -> 6.0;
            default -> 1.0;
        };
    }

    public static double roleDamage(MobRole.Role role) {
        return switch (role) {
            case BOSS -> 1.6;
            case BREAK -> 1.3;
            case NIGHTMARE -> 1.25;
            default -> 1.0;
        };
    }

    /** Final max-health multiplier (over base) for a mob of this tier at level lv in this role. */
    public static double totalHealth(int lv, MobRole.Role role, Tier tier) {
        double extra = role == MobRole.Role.BOSS || role == MobRole.Role.BREAK ? tier.extraHealth : 1.0;
        return healthMultiplier(lv) * roleHealth(role) * extra;
    }

    public static void apply(LivingEntity e, int lv, MobRole role, Tier tier) {
        int level = Math.max(0, lv);
        set(e, Attributes.MAX_HEALTH, HEALTH, totalHealth(level, role.role(), tier) * MobHooks.ascensionHealth(e, role) - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.ATTACK_DAMAGE, DAMAGE, damageMultiplier(level) * roleDamage(role.role()) * MobHooks.ascensionDamage(e, role) - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(e, Attributes.ARMOR, ARMOR, armorBonus(level), AttributeModifier.Operation.ADD_VALUE);
        double grow = switch (role.role()) {
            case ELITE -> tier == Tier.ELITE ? 0.0 : 0.12;
            case WARDEN -> tier == Tier.WARDEN ? 0.0 : 0.25;
            default -> 0.0;
        };
        set(e, Attributes.SCALE, ROLE_SCALE, grow, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        e.setHealth(e.getMaxHealth());
        e.setData(CoreApi.MOB_ROLE.get(), role);
    }

    private static void set(LivingEntity e, Holder<Attribute> attr, Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        if (Math.abs(amount) < 1e-9) {
            inst.removeModifier(id);
        } else {
            inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, op));
        }
    }
}
