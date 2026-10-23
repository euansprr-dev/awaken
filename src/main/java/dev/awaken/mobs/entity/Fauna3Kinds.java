package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * P3's entity classes and the mounts' table. {@link MobTypes} asks {@link #register} first for every overworld
 * creature: the water creatures get {@link WaterFauna} (the Lantern Maw {@link DeepBeast}) in their own spawn category
 * {@code awaken:fauna_water}, the cave creatures {@link CaveFauna}, and the Frost Stag and Dune Ray become
 * {@link WildMount}s. Everything else falls through to the P1/P2 classes.
 */
public final class Fauna3Kinds {
    private Fauna3Kinds() {}

    /**
     * The water creatures' own spawn category ({@code awaken:fauna_water}, META-INF/enumextensions.json): 10 per player,
     * friendly, despawns like water ambient (instant beyond 64 blocks). Its own cap means the Tidewater never takes a
     * slot from vanilla's fish, squid and dolphins, nor from the land's creatures in {@code awaken:fauna}.
     */
    public static final MobCategory FAUNA_WATER = waterCategory();

    private static MobCategory waterCategory() {
        try {
            return MobCategory.valueOf("AWAKEN_FAUNA_WATER");
        } catch (IllegalArgumentException e) {
            Awaken.LOG.error("[mobs] the awaken:fauna_water spawn category is missing (enum extension not applied); water creatures use WATER_AMBIENT");
            return MobCategory.WATER_AMBIENT;
        }
    }

    public static final Set<String> WATER = Set.of("glimmerfin", "breathbell", "brook_otter", "tidesteed", "lantern_maw");
    public static final Set<String> CAVE = Set.of("chime_cricket", "wickling", "drip_lurker", "hushling");
    /** Water creatures that also walk on land (the rest flop and suffocate out of water, like fish). */
    public static final Set<String> AMPHIBIOUS = Set.of("brook_otter", "tidesteed");

    /** Configures an entity type builder (MobTypes' own configure, passed in so this file needs no access to it). */
    public interface Configure {
        <E extends AwakenMob> EntityType.Builder<E> apply(EntityType.Builder<E> b, MobDef d);
    }

    /** The entity type for a P3 creature or a mount, or null when the P1/P2 classes take it. */
    public static @Nullable DeferredHolder<EntityType<?>, ? extends EntityType<? extends AwakenMob>> register(DeferredRegister.Entities reg, MobDef d, Configure c) {
        if (d.id.equals("lantern_maw")) return reg.registerEntityType(d.id, DeepBeast::new, FAUNA_WATER, b -> c.apply(b, d));
        if (WATER.contains(d.id)) return reg.registerEntityType(d.id, WaterFauna::new, FAUNA_WATER, b -> c.apply(b, d));
        if (CAVE.contains(d.id)) return reg.registerEntityType(d.id, CaveFauna::new, MobTypes.FAUNA, b -> c.apply(b, d));
        if (MOUNTS.containsKey(d.id)) return reg.registerEntityType(d.id, WildMount::new, MobTypes.FAUNA, b -> c.apply(b, d));
        return null;
    }

    // ---- mounts ------------------------------------------------------------------------------------------------------

    /** How a mount moves under a rider. */
    public enum Style {
        /** Runs and jumps (a charged jump, like a horse). */
        GROUND,
        /** Hovers a block above the ground and glides; jump soars up, then it glides down slowly. */
        GLIDE,
        /** Swims where you look; jump is a dash, a leap from the waves at the surface; the rider breathes. */
        SWIM
    }

    /**
     * One rideable creature: its favourite food (fed while sneaking, {@code trust} times, wins it over), how it moves,
     * where the rider sits (blocks above its feet and behind its centre, before scale) and its ridden speed factor.
     */
    public record Kind(String id, Identifier favourite, int trust, Style style, float seatY, float seatZ, float speed, float jump) {
        public boolean favourite(ItemStack s) {
            if (s.isEmpty()) return false;
            Item item = BuiltInRegistries.ITEM.getValue(favourite);
            return item != Items.AIR && s.is(item);
        }

        public Item favouriteItem() {
            return BuiltInRegistries.ITEM.getValue(favourite);
        }
    }

    public static final Map<String, Kind> MOUNTS = Map.of(
            "frost_stag", new Kind("frost_stag", Identifier.withDefaultNamespace("sweet_berries"), 3, Style.GROUND, 1.12F, 0.05F, 1.0F, 0.9F),
            "dune_ray", new Kind("dune_ray", Identifier.withDefaultNamespace("cactus_flower"), 4, Style.GLIDE, 0.28F, 0.0F, 0.75F, 1.0F),
            "tidesteed", new Kind("tidesteed", Identifier.withDefaultNamespace("kelp"), 3, Style.SWIM, 0.95F, 0.1F, 1.0F, 1.0F));

    public static @Nullable Kind mount(String id) {
        return MOUNTS.get(id);
    }

    /** The Hunter's Bridle (registered by the world module). */
    public static final Identifier BRIDLE = Awaken.id("hunters_bridle");

    public static boolean isBridle(ItemStack s) {
        if (s.isEmpty()) return false;
        Item item = BuiltInRegistries.ITEM.getValue(BRIDLE);
        return item != Items.AIR && s.is(item);
    }
}
