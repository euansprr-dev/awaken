package dev.awaken.armour;

import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
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

/** Armour's registrations (SPEC §44): the Fitting Stand, the {@code awaken:forged} component, the Armoury memory, the Forge's sounds. */
public final class ArmourContent {
    private ArmourContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final DeferredBlock<FittingStandBlock> STAND = BLOCKS.registerBlock("fitting_stand", FittingStandBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2f, 6f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> STAND_ITEM = ITEMS.registerSimpleBlockItem(STAND);
    public static final Supplier<BlockEntityType<FittingStandBlockEntity>> STAND_BE = BLOCK_ENTITIES.register("fitting_stand",
            () -> new BlockEntityType<>(FittingStandBlockEntity::new, STAND.get()));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Forged>> FORGED =
            COMPONENTS.registerComponentType("forged", b -> b.persistent(Forged.CODEC).networkSynchronized(Forged.STREAM_CODEC));

    static final Supplier<AttachmentType<ArmouryMemory>> ARMOURY = ATTACHMENTS.register("armoury",
            () -> AttachmentType.builder(ArmouryMemory::fresh).serialize(ArmouryMemory.CODEC).copyOnDeath().build());

    static final Supplier<AttachmentType<ArmourAbilities.State>> STATE = ATTACHMENTS.register("armour_state",
            () -> AttachmentType.builder(ArmourAbilities.State::new).build());

    /** A strike's ring by what is struck: hide and copper at E, then iron, diamond, Deepglass, netherite and starmetal. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIKE_HIDE = sound("strike_hide"), STRIKE_COPPER = sound("strike_copper"),
            STRIKE_IRON = sound("strike_iron"), STRIKE_DIAMOND = sound("strike_diamond"), STRIKE_DEEPGLASS = sound("strike_deepglass"),
            STRIKE_NETHERITE = sound("strike_netherite"), STRIKE_STARMETAL = sound("strike_starmetal");
    /** The fire and bellows under a heat, the pull, the quench's hiss, the count-in tap, the melody's bell (C5, pitched), a cut,
     * a piece set on the stand, a set waking, a missed beat. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BELLOWS = sound("bellows"), PULL = sound("pull"), QUENCH = sound("quench"),
            COUNT = sound("count"), NOTE = sound("note"), CUT = sound("cut"), FIT = sound("fit"), WAKE = sound("wake"), MISS = sound("miss");

    /** The set abilities (SPEC §44.5, §44.6): a charge filling to full, Bastion's burst, Flow's afterimage, Edge's wave, a rune
     * joining the circle, an overchannel, Bellguard's ring, Rootwarden's rooting, Hornlord's gore. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGED = ability("charged"), BASTION = ability("bastion"), FLOW = ability("flow"),
            EDGE = ability("edge"), RUNE = ability("rune"), OVERCHANNEL = ability("overchannel"), RING = ability("ring"),
            ROOTED = ability("rooted"), GORE = ability("gore");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("forge." + name, () -> SoundEvent.createVariableRangeEvent(Awaken.id("forge." + name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> ability(String name) {
        return SOUNDS.register("armour." + name, () -> SoundEvent.createVariableRangeEvent(Awaken.id("armour." + name)));
    }

    /** The ring a set's metal makes under the hammer. */
    public static SoundEvent strikeSound(ArmourSet set) {
        return (switch (set.rank) {
            case E -> STRIKE_COPPER;
            case D -> STRIKE_IRON;
            case C -> STRIKE_DIAMOND;
            case B -> STRIKE_DEEPGLASS;
            case A -> STRIKE_NETHERITE;
            default -> STRIKE_STARMETAL;
        }).get();
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}
