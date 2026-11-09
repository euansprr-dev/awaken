package dev.awaken.shaping;

import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ShapingContent {
    private ShapingContent() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final DeferredBlock<ShapedBlock> SHAPED = BLOCKS.registerBlock("shaped", ShapedBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(1.5F, 6.0F).noLootTable().lightLevel(s -> s.getValue(ShapedBlock.GLOW)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShapedBlockEntity>> SHAPED_BE = BLOCK_ENTITIES.register("shaped",
            () -> new BlockEntityType<>(ShapedBlockEntity::new, SHAPED.get()));
    public static final DeferredItem<MalletItem> MALLET = ITEMS.registerItem("mallet", MalletItem::new, p -> p.stacksTo(1));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MalletData>> MALLET_DATA =
            COMPONENTS.registerComponentType("mallet", b -> b.persistent(MalletData.CODEC).networkSynchronized(MalletData.STREAM));
    public static final Supplier<AttachmentType<LookBook>> LOOK_BOOK = ATTACHMENTS.register("look_book",
            () -> AttachmentType.builder(LookBook::fresh).serialize(LookBook.CODEC.fieldOf("book")).copyOnDeath().build());
    public static final DeferredHolder<SoundEvent, SoundEvent> KNOCK = SOUNDS.register("shaping.knock",
            () -> SoundEvent.createVariableRangeEvent(Awaken.id("shaping.knock")));

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}
