package dev.awaken.mobs;

import dev.awaken.Awaken;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Trait;
import dev.awaken.mobs.entity.AwBolt;
import dev.awaken.mobs.entity.AwakenBoss;
import dev.awaken.mobs.entity.AwakenMob;
import dev.awaken.mobs.entity.BrokerBoss;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.entity.NamedBeast;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One entity type, spawn egg and loot table per bestiary def ({@code awaken:<id>}, {@code awaken:<id>_spawn_egg}),
 * plus the boss part items and the shared mob projectile.
 */
public final class MobTypes {
    private MobTypes() {}

    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    /**
     * The overworld creatures' own spawn category ({@code awaken:fauna}, added by META-INF/enumextensions.json): 16 per
     * player, friendly (spawns in Peaceful too; the spawn rules keep hunters out), despawns like monsters. Its own cap
     * means the Awakened layer never takes a slot from cows, zombies or the rest of vanilla.
     */
    public static final MobCategory FAUNA = faunaCategory();

    private static MobCategory faunaCategory() {
        try {
            return MobCategory.valueOf("AWAKEN_FAUNA");
        } catch (IllegalArgumentException e) {
            Awaken.LOG.error("[mobs] the awaken:fauna spawn category is missing (enum extension not applied); overworld creatures use CREATURE");
            return MobCategory.CREATURE;
        }
    }

    public static final DeferredHolder<EntityType<?>, EntityType<AwBolt>> BOLT = ENTITIES.registerEntityType("mob_bolt", AwBolt::new, MobCategory.MISC,
            b -> b.sized(0.4F, 0.4F).clientTrackingRange(4).updateInterval(10).noLootTable());

    private static final Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<? extends AwakenMob>>> TYPES = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<SpawnEggItem>> EGGS = new LinkedHashMap<>();
    private static final Map<String, DeferredItem<Item>> PARTS = new LinkedHashMap<>();
    private static final Map<EntityType<?>, MobDef> DEFS = new ConcurrentHashMap<>();

    static {
        for (MobDef d : Bestiary.all()) {
            DeferredHolder<EntityType<?>, ? extends EntityType<? extends AwakenMob>> holder;
            if (d.isFauna()) {
                // The Awakened layer's overworld creatures: their own spawn category and cap.
                if (d.fauna == FaunaRole.BEAST) {
                    holder = ENTITIES.registerEntityType(d.id, NamedBeast::new, FAUNA, b -> configureFauna(b, d));
                } else {
                    holder = ENTITIES.registerEntityType(d.id, FaunaMob::new, FAUNA, b -> configureFauna(b, d));
                }
            } else if (d.has(Trait.FRIENDLY)) {
                holder = ENTITIES.registerEntityType(d.id, BrokerBoss::new, MobCategory.MONSTER, b -> configure(b, d));
            } else if (d.tier.isBoss()) {
                holder = ENTITIES.registerEntityType(d.id, AwakenBoss::new, MobCategory.MONSTER, b -> configure(b, d));
            } else {
                holder = ENTITIES.registerEntityType(d.id, AwakenMob::new, MobCategory.MONSTER, b -> configure(b, d));
            }
            TYPES.put(d.id, holder);
            EGGS.put(d.id, ITEMS.registerItem(d.id + "_spawn_egg", SpawnEggItem::new, p -> p.spawnEgg(holder.get())));
            for (MobDef.Part part : d.parts) {
                PARTS.put(part.id(), ITEMS.registerSimpleItem(part.id(), p -> p.rarity(d.tier == dev.awaken.mobs.def.Tier.LORD ? Rarity.UNCOMMON : Rarity.RARE)));
            }
        }
    }

    private static <E extends AwakenMob> EntityType.Builder<E> configureFauna(EntityType.Builder<E> b, MobDef d) {
        configure(b, d);
        if (d.fauna == FaunaRole.BEAST) b.clientTrackingRange(10);
        // Night hunters and beasts leave in Peaceful, like vanilla monsters; the rest stay.
        if (d.fauna == FaunaRole.HUNTER || d.fauna == FaunaRole.BEAST) b.notInPeaceful();
        return b;
    }

    private static <E extends AwakenMob> EntityType.Builder<E> configure(EntityType.Builder<E> b, MobDef d) {
        b.sized(d.width, d.height).eyeHeight(d.height * 0.85F).clientTrackingRange(d.tier.isBoss() ? 10 : 8);
        if (d.has(Trait.FIRE_IMMUNE)) b.fireImmune();
        return b;
    }

    static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(MobTypes::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        for (MobDef d : Bestiary.all()) {
            event.put(TYPES.get(d.id).get(), attributesOf(d).build());
        }
    }

    public static AttributeSupplier.Builder attributesOf(MobDef d) {
        AttributeSupplier.Builder b = Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, d.health)
                .add(Attributes.ATTACK_DAMAGE, d.damage)
                .add(Attributes.MOVEMENT_SPEED, d.speed)
                .add(Attributes.ARMOR, d.armor)
                .add(Attributes.KNOCKBACK_RESISTANCE, d.knockbackResist)
                .add(Attributes.FOLLOW_RANGE, d.followRange)
                .add(Attributes.SCALE, d.scale)
                .add(Attributes.FLYING_SPEED, d.speed)
                .add(Attributes.STEP_HEIGHT, d.tier.isBoss() ? 1.0 : 0.6);
        if (d.tier.isBoss()) b.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
        return b;
    }

    /** The def behind one of our entity types (null for anything else). */
    public static @Nullable MobDef def(EntityType<?> type) {
        MobDef d = DEFS.get(type);
        if (d != null) return d;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (!Awaken.MODID.equals(key.getNamespace())) return null;
        d = Bestiary.get(key.getPath());
        if (d != null) DEFS.put(type, d);
        return d;
    }

    public static @Nullable EntityType<? extends AwakenMob> type(String id) {
        var h = TYPES.get(id);
        return h == null ? null : h.get();
    }

    public static @Nullable EntityType<? extends AwakenMob> type(MobDef d) {
        return type(d.id);
    }

    public static Map<String, DeferredItem<SpawnEggItem>> eggs() {
        return Collections.unmodifiableMap(EGGS);
    }

    public static Map<String, DeferredItem<Item>> parts() {
        return Collections.unmodifiableMap(PARTS);
    }

    public static @Nullable Item part(String id) {
        var h = PARTS.get(id);
        return h == null ? null : h.get();
    }
}
