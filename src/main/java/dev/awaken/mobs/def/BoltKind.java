package dev.awaken.mobs.def;

import dev.awaken.core.Forces;
import dev.awaken.mobs.MobParticles.Shot;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** What a mob projectile is made of: its Force (damage type), its look (a trail of our own, a body's shape) and its sting. */
public enum BoltKind {
    PLAIN(null, Items.FIREWORK_STAR, Shot.SPARK, 0xE8E4D8, Form.ROUND, null, 0),
    FIRE(Forces.Force.HEAT, Items.FIRE_CHARGE, Shot.EMBER, 0xFF8A2A, Form.ROUND, null, 0),
    FROST(Forces.Force.FROST, Items.SNOWBALL, Shot.FLAKE, 0xA8E4FF, Form.ROUND, MobEffects.SLOWNESS, 40),
    SPIRIT(Forces.Force.SPIRIT, Items.ECHO_SHARD, Shot.WISP, 0x6FE8E0, Form.ROUND, MobEffects.WEAKNESS, 40),
    SPORE(null, Items.BROWN_MUSHROOM, Shot.PUFF, 0xC4B06A, Form.ROUND, MobEffects.POISON, 60),
    WATER(null, Items.PRISMARINE_CRYSTALS, Shot.DROP, 0x6FB8E8, Form.DROP, MobEffects.BLINDNESS, 25),
    CHARGE(Forces.Force.CHARGE, Items.PRISMARINE_SHARD, Shot.SPARK, 0x8FE0FF, Form.ROUND, MobEffects.SLOWNESS, 15),
    GLASS(null, Items.AMETHYST_SHARD, Shot.SLIVER, 0xE0C8FF, Form.DART, null, 0),
    HONEY(null, Items.HONEYCOMB, Shot.DROP, 0xF2A91E, Form.DROP, MobEffects.SLOWNESS, 50),
    THORN(null, Items.SWEET_BERRIES, Shot.SLIVER, 0x8FB04A, Form.DART, MobEffects.POISON, 30),
    DREAM(Forces.Force.SPIRIT, Items.PHANTOM_MEMBRANE, Shot.WISP, 0xC89BFF, Form.ROUND, MobEffects.NAUSEA, 60),
    GRAVITY(Forces.Force.GRAVITY, Items.ENDER_PEARL, Shot.EMBER, 0x9C7CFF, Form.ROUND, MobEffects.LEVITATION, 20),
    VOID(Forces.Force.SPIRIT, Items.ENDER_EYE, Shot.WISP, 0x6A4FB0, Form.ROUND, MobEffects.DARKNESS, 50),
    SIGNAL(Forces.Force.CHARGE, Items.NETHER_STAR, Shot.SPARK, 0xFFF0A0, Form.ROUND, MobEffects.GLOWING, 60),
    /** A Deepglass shard (the seep heart): drawn as awaken:deepglass_shard with a trail of the Deep's motes (AwBolt). */
    DEEP(null, Items.AMETHYST_SHARD, Shot.EMBER, 0xB48CFF, Form.ROUND, null, 0),
    /** A hive's stinger: a dart of venom-amber (saved by ordinal, so new kinds go last). */
    STINGER(null, Items.FLINT, Shot.SLIVER, 0xFFC23A, Form.DART, MobEffects.POISON, 30);

    /** The body a shot is drawn with: a glow, a needle along its flight, or a falling bead. */
    public enum Form { ROUND, DART, DROP }

    public final Forces.@Nullable Force force;
    public final Item item;
    public final Form form;
    private final Shot shape;
    private final int colour;
    private @Nullable ParticleOptions trail;
    public final @Nullable Holder<MobEffect> effect;
    public final int effectTicks;

    BoltKind(Forces.@Nullable Force force, Item item, Shot shape, int colour, Form form, @Nullable Holder<MobEffect> effect, int effectTicks) {
        this.force = force;
        this.item = item;
        this.shape = shape;
        this.colour = colour;
        this.form = form;
        this.effect = effect;
        this.effectTicks = effectTicks;
    }

    /** Its trail, made once (the particle types don't exist yet when the enum is built). */
    public ParticleOptions trail() {
        if (trail == null) trail = dev.awaken.mobs.MobParticles.shot(shape, colour);
        return trail;
    }

    public static BoltKind byOrdinal(int i) {
        BoltKind[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
