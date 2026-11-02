package dev.awaken.arms.anvil;

import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** The War Anvil's registrations (SPEC §46): the block, its item and entity, the {@code awaken:weapon_forged} story, its sounds. */
public final class AnvilContent {
    private AnvilContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final DeferredBlock<WarAnvilBlock> ANVIL = BLOCKS.registerBlock("war_anvil", WarAnvilBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(5f, 1200f).requiresCorrectToolForDrops().sound(SoundType.ANVIL).noOcclusion());
    public static final DeferredItem<BlockItem> ANVIL_ITEM = ITEMS.registerSimpleBlockItem(ANVIL);
    public static final Supplier<BlockEntityType<WarAnvilBlockEntity>> ANVIL_BE = BLOCK_ENTITIES.register("war_anvil",
            () -> new BlockEntityType<>(WarAnvilBlockEntity::new, ANVIL.get()));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponForged>> FORGED =
            COMPONENTS.registerComponentType("weapon_forged", b -> b.persistent(WeaponForged.CODEC).networkSynchronized(WeaponForged.STREAM_CODEC));

    /**
     * The fire and bellows under a heat, the pull, a strike on the blade and its ring when clean, a missed beat, the tongs
     * turning it over, the plunge and the lift, the hilt's taps, the count-in, the scrape as it's drawn and the blade
     * free. The forge kit's recordings until the anvil's own (plan stage 5).
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> BELLOWS = sound("bellows"), PULL = sound("pull"), STRIKE = sound("strike"),
            RING = sound("ring"), MISS = sound("miss"), FLIP = sound("flip"), QUENCH = sound("quench"), LIFT = sound("lift"), TAP = sound("tap"),
            COUNT = sound("count"), SCRAPE = sound("scrape"), DRAWN = sound("drawn");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("anvil." + name, () -> SoundEvent.createVariableRangeEvent(Awaken.id("anvil." + name)));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}
