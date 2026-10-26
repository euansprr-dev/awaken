package dev.awaken.core.api;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import org.jspecify.annotations.Nullable;

/**
 * What a blade meets when it lands (SPEC §24, Arms): the impact's sound and, later, its sparks or splinters. Both
 * sides. The mobs module installs the answer for its own bodies; the defaults know vanilla's. Plate worn over flesh
 * or bone answers as {@link #ARMOR}.
 */
public enum BodyMaterial {
    FLESH, ARMOR, BONE, WOOD, STONE, SPIRIT, SLIME;

    /** Armour points from which a body rings as plate (a full iron set is 15, a chestplate alone 6). */
    public static final int PLATE = 10;

    public interface Source {
        /** This body's material, or null to fall back to the vanilla defaults. */
        @Nullable BodyMaterial of(Entity e);
    }

    private static Source source = e -> null;

    public static void install(Source s) {
        source = s;
    }

    public static BodyMaterial of(Entity e) {
        BodyMaterial m = source.of(e);
        if (m == null) m = vanilla(e);
        if ((m == FLESH || m == BONE) && e instanceof LivingEntity l && l.getArmorValue() >= PLATE) return ARMOR;
        return m;
    }

    private static BodyMaterial vanilla(Entity e) {
        EntityType<?> t = e.getType();
        if (e instanceof AbstractCubeMob) return SLIME;
        if (t.builtInRegistryHolder().is(EntityTypeTags.SKELETONS) || t == EntityTypes.WITHER) return BONE;
        if (t == EntityTypes.CREAKING || t == EntityTypes.ARMOR_STAND) return WOOD;
        if (t == EntityTypes.IRON_GOLEM || t == EntityTypes.COPPER_GOLEM) return STONE;
        if (t == EntityTypes.SHULKER) return ARMOR;
        if (t == EntityTypes.VEX || t == EntityTypes.ALLAY || t == EntityTypes.BREEZE || t == EntityTypes.BLAZE) return SPIRIT;
        return FLESH;
    }
}
