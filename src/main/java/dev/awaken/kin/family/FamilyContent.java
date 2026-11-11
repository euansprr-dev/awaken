package dev.awaken.kin.family;

import dev.awaken.Awaken;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Hearth's family (plan docs/family/PLAN.md): Aya and Dubu's entity types. Until the plan is approved these are the
 * preview bodies only ({@link FamilyPet}); nothing spawns them but {@code /awaken admin family}.
 */
public final class FamilyContent {
    private FamilyContent() {}

    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FamilyPet>> AYA = ENTITIES.registerEntityType("family_aya", FamilyPet::new,
            MobCategory.CREATURE, b -> b.sized(0.45F, 0.5F).eyeHeight(0.36F).clientTrackingRange(8));
    public static final DeferredHolder<EntityType<?>, EntityType<FamilyPet>> DUBU = ENTITIES.registerEntityType("family_dubu", FamilyPet::new,
            MobCategory.CREATURE, b -> b.sized(0.5F, 0.55F).eyeHeight(0.4F).clientTrackingRange(8));

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(AYA.get(), FamilyPet.attributes().build());
            e.put(DUBU.get(), FamilyPet.attributes().build());
        });
        FamilyCommands.register();
    }
}
