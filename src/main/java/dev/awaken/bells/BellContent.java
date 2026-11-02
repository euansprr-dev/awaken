package dev.awaken.bells;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The bells' blocks, items, sounds and the Bell Book (SPEC §43.7). */
public final class BellContent {
    private BellContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    /** A sleeping bell out in the land: dull and grey until it's struck awake. Can't be broken or moved. */
    public static final DeferredBlock<SleepingBellBlock> SLEEPING_BELL = BLOCKS.registerBlock("sleeping_bell", SleepingBellBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(-1F, 3600000F).sound(SoundType.COPPER).noOcclusion().noLootTable()
                    .pushReaction(PushReaction.IMMOVEABLE));
    /** The frame your first bells hang on, at home (three hooks): where the song starts. */
    public static final DeferredBlock<BellFrameBlock> BELL_FRAME = BLOCKS.registerBlock("bell_frame", BellFrameBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredItem<BlockItem> BELL_FRAME_ITEM = ITEMS.registerSimpleBlockItem(BELL_FRAME, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    /** A beam that hangs four bells high over your home (8 or more above the fire). */
    public static final DeferredBlock<BellBeamBlock> BELL_BEAM = BLOCKS.registerBlock("bell_beam", p -> new BellBeamBlock(p, false),
            p -> p.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredItem<BlockItem> BELL_BEAM_ITEM = ITEMS.registerSimpleBlockItem(BELL_BEAM, p -> p.stacksTo(16));
    /** A beam strong enough for one Great Bell (12 or more above the fire). */
    public static final DeferredBlock<BellBeamBlock> GREAT_BEAM = BLOCKS.registerBlock("great_beam", p -> new BellBeamBlock(p, true),
            p -> p.mapColor(MapColor.WOOD).strength(3.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredItem<BlockItem> GREAT_BEAM_ITEM = ITEMS.registerSimpleBlockItem(GREAT_BEAM, p -> p.stacksTo(16).rarity(Rarity.UNCOMMON));
    /** A bell lying where it was won, to be lifted by hand (drawn by its renderer; it can't be broken). */
    public static final DeferredBlock<FoundBellBlock> FOUND_BELL = BLOCKS.registerBlock("found_bell", FoundBellBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(-1F, 3600000F).sound(SoundType.COPPER).noOcclusion().noLootTable()
                    .pushReaction(PushReaction.IMMOVEABLE).lightLevel(s -> s.getValue(FoundBellBlock.GLOW) ? 6 : 0));

    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    /** Which of the hundred a bell item is (its number). */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Integer>> BELL_NO =
            COMPONENTS.registerComponentType("bell", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));
    /** One of the hundred, as a thing you carry home and hang. */
    public static final DeferredItem<BellItem> BELL = ITEMS.registerItem("bell", BellItem::new, p -> p.stacksTo(1).rarity(Rarity.RARE).fireResistant());

    static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<HooksEntity>> HOOKS_BE = BLOCK_ENTITIES.register("bell_hooks",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(HooksEntity::new, BELL_FRAME.get(), BELL_BEAM.get(), GREAT_BEAM.get()));
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<FoundBellEntity>> FOUND_BELL_BE = BLOCK_ENTITIES.register("found_bell",
            () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(FoundBellEntity::new, FOUND_BELL.get()));
    /** The band's handbell: rings the peals your bells make. */
    public static final DeferredItem<Handbell> HANDBELL = ITEMS.registerItem("handbell", Handbell::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    /** The Great Belfry's two needs (SPEC §43.14): the Heart-clapper and a Sovereign's relic. */
    public static final DeferredItem<BelfryPiece> HEART_CLAPPER = ITEMS.registerItem("heart_clapper", BelfryPiece::new,
            p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    public static final DeferredItem<BelfryPiece> SOVEREIGN_RELIC = ITEMS.registerItem("sovereign_relic", BelfryPiece::new,
            p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    /** The tower's fifteen tuned bells (D major pentatonic, D3 to B5). */
    static final List<DeferredHolder<SoundEvent, SoundEvent>> NOTES = new ArrayList<>();

    static {
        for (int k = 0; k < 15; k++) NOTES.add(sound("bells.note_" + k));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> GAP = sound("bells.gap");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANDBELL_RING = sound("bells.handbell");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOLL = sound("bells.toll");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = sound("bells.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAKE = sound("bells.wake");

    public static final Supplier<AttachmentType<BellBook>> BOOK = ATTACHMENTS.register("bell_book",
            () -> AttachmentType.builder(BellBook::new).serialize(BellBook.CODEC).copyOnDeath().build());

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(Awaken.id(id)));
    }

    /** Bell #n's note in the song. */
    public static SoundEvent note(int no) {
        return NOTES.get(BellCatalog.MELODY[no]).get();
    }

    /** The semitones each tower bell sits above D. */
    private static final int[] SEMIS = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24, 26, 28, 31, 33};

    /** A one-note sound (the hum, the toll, both on D) pitched to bell #n's note, folded into one octave. */
    public static float pitch(int no) {
        return (float) Math.pow(2, (SEMIS[BellCatalog.MELODY[no]] % 12) / 12.0);
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
