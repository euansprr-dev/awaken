package dev.awaken.core;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The Five Forces. Powers deal damage with one of these damage types; environments and gate modifiers tag entities
 * with {@link Tag}s. Interactions between them live in the powers module ({@code ForceInteractions}).
 */
public final class Forces {
    private Forces() {}

    public enum Force { HEAT, CHARGE, GRAVITY, SPIRIT, FROST }

    /**
     * Environmental tags that the forces react with. Stored as a bitmask on the entity. {@code ECHO} is Echoing: the
     * monster gets up once after its first death (see {@link #markRisen}). Player-owned Echoes are
     * {@code MobRole.Role.ECHO}, never this tag.
     */
    public enum Tag { SPORES, WATER, VOLATILE, MIRROR, FROSTED, CHARGED, ECHO, HAUNTED }

    public static final ResourceKey<DamageType> HEAT = damage("heat");
    public static final ResourceKey<DamageType> CHARGE = damage("charge");
    public static final ResourceKey<DamageType> GRAVITY = damage("gravity");
    public static final ResourceKey<DamageType> SPIRIT = damage("spirit");
    public static final ResourceKey<DamageType> FROST = damage("frost");
    /** Plain power damage with no force (Kinetic hits, Architect collapses). */
    public static final ResourceKey<DamageType> KINETIC = damage("kinetic");

    private static ResourceKey<DamageType> damage(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Awaken.id(name));
    }

    public static ResourceKey<DamageType> key(Force f) {
        return switch (f) {
            case HEAT -> HEAT;
            case CHARGE -> CHARGE;
            case GRAVITY -> GRAVITY;
            case SPIRIT -> SPIRIT;
            case FROST -> FROST;
        };
    }

    public static Force forceOf(DamageSource source) {
        for (Force f : Force.values()) {
            if (source.is(key(f))) return f;
        }
        return null;
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    /** Bitmask of {@link Tag}s. Saved with the entity. */
    public static final Supplier<AttachmentType<Integer>> TAGS = ATTACHMENTS.register("force_tags",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("bits"), bits -> bits != 0).build());

    static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static boolean has(Entity e, Tag t) {
        return (e.getExistingData(TAGS).orElse(0) & (1 << t.ordinal())) != 0;
    }

    public static void tag(Entity e, Tag t) {
        e.setData(TAGS, e.getData(TAGS) | (1 << t.ordinal()));
    }

    public static void untag(Entity e, Tag t) {
        e.setData(TAGS, e.getData(TAGS) & ~(1 << t.ordinal()));
    }

    private static final String RISEN = "awaken_risen";

    /** Echoing bookkeeping: this one already got up once, whoever raised it, and stays down next time. */
    public static void markRisen(Entity e) {
        if (has(e, Tag.ECHO)) untag(e, Tag.ECHO);
        e.getPersistentData().putBoolean(RISEN, true);
    }

    public static boolean hasRisen(Entity e) {
        return e.getPersistentData().getBooleanOr(RISEN, false);
    }
}
