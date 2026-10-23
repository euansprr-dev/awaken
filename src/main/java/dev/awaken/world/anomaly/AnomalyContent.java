package dev.awaken.world.anomaly;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Everything the anomalies register: the rift and mote entities, the spirit cache block, sounds and the player memory. */
public final class AnomalyContent {
    private AnomalyContent() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    /** What a spirit procession leaves behind. No item: it can only be found, never placed. */
    public static final DeferredBlock<SpiritCacheBlock> SPIRIT_CACHE = BLOCKS.registerBlock("anomaly_spirit_cache", SpiritCacheBlock::new,
            p -> p.strength(0.3F, 1200.0F).sound(SoundType.AMETHYST).lightLevel(s -> 12).noOcclusion()
                    .pushReaction(PushReaction.IMMOVEABLE).mapColor(MapColor.COLOR_YELLOW));

    /** The tear and its core. Never saved: the director re-creates it while the rift is open, and cleans up after a restart. */
    public static final DeferredHolder<EntityType<?>, EntityType<RiftEntity>> RIFT = ENTITIES.registerEntityType("rift_tear",
            RiftEntity::new, MobCategory.MISC, b -> b.sized(0.9F, 1.6F).clientTrackingRange(8).updateInterval(20).noSave().noSummon().fireImmune());

    /** One light of a spirit procession. Never saved. */
    public static final DeferredHolder<EntityType<?>, EntityType<AnomalyMote>> MOTE = ENTITIES.registerEntityType("anomaly_mote",
            AnomalyMote::new, MobCategory.MISC, b -> b.sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(2).noSave().noSummon().fireImmune());

    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_OPEN = sound("anomaly.rift_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_HUM = sound("anomaly.rift_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_HIT = sound("anomaly.rift_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_CLOSE = sound("anomaly.rift_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_FADE = sound("anomaly.rift_fade");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTES = sound("anomaly.motes");
    public static final DeferredHolder<SoundEvent, SoundEvent> CACHE = sound("anomaly.cache");

    public static final Supplier<AttachmentType<AnomalyMemory>> MEMORY = ATTACHMENTS.register("anomaly_memory", () -> AttachmentType
            .builder(AnomalyMemory::new).serialize(AnomalyMemory.MAP_CODEC).copyOnDeath().build());

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
