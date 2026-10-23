package dev.awaken.mobs.def;

import dev.awaken.core.Forces;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** What a mob projectile is made of: its Force (damage type), its look (a vanilla item sprite + trail) and its sting. */
public enum BoltKind {
    PLAIN(null, Items.FIREWORK_STAR, ParticleTypes.CRIT, null, 0),
    FIRE(Forces.Force.HEAT, Items.FIRE_CHARGE, ParticleTypes.FLAME, null, 0),
    FROST(Forces.Force.FROST, Items.SNOWBALL, ParticleTypes.SNOWFLAKE, MobEffects.SLOWNESS, 40),
    SPIRIT(Forces.Force.SPIRIT, Items.ECHO_SHARD, ParticleTypes.SOUL_FIRE_FLAME, MobEffects.WEAKNESS, 40),
    SPORE(null, Items.BROWN_MUSHROOM, ParticleTypes.SPORE_BLOSSOM_AIR, MobEffects.POISON, 60),
    WATER(null, Items.PRISMARINE_CRYSTALS, ParticleTypes.BUBBLE_POP, MobEffects.BLINDNESS, 25),
    CHARGE(Forces.Force.CHARGE, Items.PRISMARINE_SHARD, ParticleTypes.ELECTRIC_SPARK, MobEffects.SLOWNESS, 15),
    GLASS(null, Items.AMETHYST_SHARD, ParticleTypes.END_ROD, null, 0),
    HONEY(null, Items.HONEYCOMB, ParticleTypes.FALLING_HONEY, MobEffects.SLOWNESS, 50),
    THORN(null, Items.SWEET_BERRIES, ParticleTypes.HAPPY_VILLAGER, MobEffects.POISON, 30),
    DREAM(Forces.Force.SPIRIT, Items.PHANTOM_MEMBRANE, ParticleTypes.WITCH, MobEffects.NAUSEA, 60),
    GRAVITY(Forces.Force.GRAVITY, Items.ENDER_PEARL, ParticleTypes.REVERSE_PORTAL, MobEffects.LEVITATION, 20),
    VOID(Forces.Force.SPIRIT, Items.ENDER_EYE, ParticleTypes.PORTAL, MobEffects.DARKNESS, 50),
    SIGNAL(Forces.Force.CHARGE, Items.NETHER_STAR, ParticleTypes.END_ROD, MobEffects.GLOWING, 60);

    public final Forces.@Nullable Force force;
    public final Item item;
    public final ParticleOptions trail;
    public final @Nullable Holder<MobEffect> effect;
    public final int effectTicks;

    BoltKind(Forces.@Nullable Force force, Item item, ParticleOptions trail, @Nullable Holder<MobEffect> effect, int effectTicks) {
        this.force = force;
        this.item = item;
        this.trail = trail;
        this.effect = effect;
        this.effectTicks = effectTicks;
    }

    public static BoltKind byOrdinal(int i) {
        BoltKind[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
