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
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

/** The War Anvil's registrations (SPEC §46): the block, its item and entity, the {@code awaken:weapon_forged} story, its sounds. */
public final class AnvilContent {
    private AnvilContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final DeferredBlock<WarAnvilBlock> ANVIL = BLOCKS.registerBlock("war_anvil", WarAnvilBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(5f, 1200f).requiresCorrectToolForDrops().sound(SoundType.ANVIL).noOcclusion());
    public static final DeferredItem<BlockItem> ANVIL_ITEM = ITEMS.registerSimpleBlockItem(ANVIL);
    public static final Supplier<BlockEntityType<WarAnvilBlockEntity>> ANVIL_BE = BLOCK_ENTITIES.register("war_anvil",
            () -> new BlockEntityType<>(WarAnvilBlockEntity::new, ANVIL.get()));

    /** Where a smith's War Anvil is (SPEC §46.4; saved, kept through death). */
    static final Supplier<AttachmentType<SmithMemory>> SMITH = ATTACHMENTS.register("anvil_smith",
            () -> AttachmentType.builder(SmithMemory::fresh).serialize(SmithMemory.CODEC).copyOnDeath().build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponForged>> FORGED =
            COMPONENTS.registerComponentType("weapon_forged", b -> b.persistent(WeaponForged.CODEC).networkSynchronized(WeaponForged.STREAM_CODEC));

    /**
     * The anvil's own kit (SPEC §46.10; tools/art/specs/audio_anvil.py): a strike on each tier's metal and the blade's ring
     * when clean (pitched to the style's true note), the tongs turning it over, the plunge's roar and the lift, the hilt's
     * peening taps, the scrape as it's drawn from the stone and the blade free; the ranks' acts: a flake knapped, the cord
     * wound, the wheel on the edge, the bar's twist, the Deepglass horn's hum and the starlight. The fire and bellows, the
     * pull, the count-in and a missed beat are the Forge's (the armour forge kit's recordings).
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> BELLOWS = sound("bellows"), PULL = sound("pull"), STRIKE = sound("strike"),
            RING = sound("ring"), MISS = sound("miss"), FLIP = sound("flip"), QUENCH = sound("quench"), LIFT = sound("lift"), TAP = sound("tap"),
            COUNT = sound("count"), SCRAPE = sound("scrape"), DRAWN = sound("drawn"), KNAP = sound("knap"), CORD = sound("cord"),
            WHEEL = sound("wheel"), TWIST = sound("twist"), HUM = sound("hum"), STAR = sound("star"),
            STRIKE_GATE = sound("strike_gate"), STRIKE_VEIL = sound("strike_veil"), STRIKE_LORD = sound("strike_lord"),
            STRIKE_STAR = sound("strike_star");
    /** The picks' acts (§49.4): the eye punched, the colours running, the head rapped onto its haft, the tips steeled, the spine threaded, the seam poured, quiet work. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE = sound("eye"), COLOURS = sound("colours"), HANG = sound("hang"), TIP = sound("tip"),
            THREAD = sound("thread"), POUR = sound("pour"), TEND = sound("tend");

    /** The hammer on a tier's metal: iron to D (and an E reforge's), then gatewrought, veilwoven, lordscale, sovereign. */
    public static SoundEvent strike(int tier) {
        return (switch (tier) {
            case 2 -> STRIKE_GATE;
            case 3 -> STRIKE_VEIL;
            case 4 -> STRIKE_LORD;
            case 5 -> STRIKE_STAR;
            default -> STRIKE;
        }).get();
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("anvil." + name, () -> SoundEvent.createVariableRangeEvent(Awaken.id("anvil." + name)));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
