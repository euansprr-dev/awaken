package dev.awaken.kin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Hearth's family (SPEC §69): Aya and Dubu's entity types, their voices (the game's own cat and small-dog
 * recordings, pitched for them in sounds.json) and the game rule that switches the whole thing off.
 */
public final class FamilyContent {
    private FamilyContent() {}

    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<GameRule<?>> RULES = DeferredRegister.create(Registries.GAME_RULE, Awaken.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FamilyPet>> AYA = ENTITIES.registerEntityType("family_aya", FamilyPet::new,
            MobCategory.CREATURE, b -> b.sized(0.45F, 0.5F).eyeHeight(0.36F).clientTrackingRange(10).fireImmune());
    public static final DeferredHolder<EntityType<?>, EntityType<FamilyPet>> DUBU = ENTITIES.registerEntityType("family_dubu", FamilyPet::new,
            MobCategory.CREATURE, b -> b.sized(0.5F, 0.55F).eyeHeight(0.4F).clientTrackingRange(10).fireImmune());

    public static final DeferredHolder<SoundEvent, SoundEvent> AYA_PURR = sound("family.aya.purr"), AYA_TRILL = sound("family.aya.trill"),
            AYA_MEOW = sound("family.aya.meow"), DUBU_BARK = sound("family.dubu.bark"), DUBU_PANT = sound("family.dubu.pant"),
            DUBU_WHINE = sound("family.dubu.whine"), DUBU_HOWL = sound("family.dubu.howl");

    /** {@code /gamerule awaken:hearth_family false}: their bodies go and nothing of theirs runs; the record of them is kept. */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> RULE = RULES.register("hearth_family",
            () -> new GameRule<>(GameRuleCategory.MOBS, GameRuleType.BOOL, BoolArgumentType.bool(), (visitor, rule) -> visitor.visitBoolean(rule),
                    Codec.BOOL, b -> b ? 1 : 0, true, FeatureFlagSet.of()));

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Awaken.id(id)));
    }

    public static boolean on(ServerLevel level) {
        return level.getGameRules().get(RULE.get());
    }

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        RULES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(AYA.get(), FamilyPet.attributes().build());
            e.put(DUBU.get(), FamilyPet.attributes().build());
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> FamilyService.tick(e.getServer()));
        bus.addListener((LivingChangeTargetEvent e) -> FamilyService.onTarget(e));
        bus.addListener((ServerStoppedEvent e) -> FamilyService.serverStopped());
        dev.awaken.core.api.Goals.register(FamilyService::steps); // one row on Tasks until you have asked one along (SPEC §69.8)
        FamilyCommands.register();
        FamilyTests.register();
    }
}
