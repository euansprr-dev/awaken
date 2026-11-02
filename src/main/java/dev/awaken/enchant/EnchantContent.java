package dev.awaken.enchant;

import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
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

/**
 * Enchanting's registrations (SPEC §45): the Inscribing Table, the Enchantment Page, our enchantment effect types (wards,
 * banes, Clear Blood, Harvest), what each player knows, and the table's sounds.
 */
public final class EnchantContent {
    private EnchantContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    static final DeferredRegister<DataComponentType<?>> EFFECTS = DeferredRegister.create(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final DeferredBlock<InscribingTableBlock> TABLE = BLOCKS.registerBlock("inscribing_table", InscribingTableBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.5F, 1200F).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> 5));
    public static final DeferredItem<BlockItem> TABLE_ITEM = ITEMS.registerSimpleBlockItem(TABLE, p -> p.rarity(Rarity.UNCOMMON));
    public static final Supplier<BlockEntityType<InscribingTableBlockEntity>> TABLE_BE = BLOCK_ENTITIES.register("inscribing_table",
            () -> new BlockEntityType<>(InscribingTableBlockEntity::new, TABLE.get()));

    /** One enchantment, held in vanilla's {@code stored_enchantments} so every tooltip and JEI read it; used, it teaches. */
    public static final DeferredItem<PageItem> PAGE = ITEMS.registerItem("enchantment_page", PageItem::new,
            p -> p.stacksTo(16).rarity(Rarity.RARE));
    /** The table's burin: only ever drawn in your hand while you write (never held, never in a tab). */
    public static final DeferredItem<net.minecraft.world.item.Item> BURIN = ITEMS.registerSimpleItem("burin", p -> p.stacksTo(1));

    /** A ward against one Force (SPEC §45.3): 2.5% a level a worn piece, capped for a whole suit. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EnchantEffects.Ward>> WARD = EFFECTS.register("ward",
            () -> DataComponentType.<EnchantEffects.Ward>builder().persistent(EnchantEffects.Ward.CODEC).build());
    /** A bane against one kind of foe: +8% a level, never against a Lord. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EnchantEffects.Bane>> BANE = EFFECTS.register("bane",
            () -> DataComponentType.<EnchantEffects.Bane>builder().persistent(EnchantEffects.Bane.CODEC).build());
    /** Poison, wither and spores wear off faster. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EnchantEffects.Marker>> CLEAR_BLOOD = EFFECTS.register("clear_blood",
            () -> DataComponentType.<EnchantEffects.Marker>builder().persistent(EnchantEffects.Marker.CODEC).build());
    /** More hide, bone and pelt from wild beasts. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EnchantEffects.Marker>> HARVEST = EFFECTS.register("harvest",
            () -> DataComponentType.<EnchantEffects.Marker>builder().persistent(EnchantEffects.Marker.CODEC).build());

    /** What you know (SPEC §45.2): kept through death, synced to you alone for the book, the Journal and Tasks. */
    public static final Supplier<AttachmentType<Known>> KNOWN = ATTACHMENTS.register("enchant_known",
            () -> AttachmentType.builder(Known::fresh).serialize(Known.CODEC).copyOnDeath()
                    .sync((holder, to) -> holder == to, Known.STREAM_CODEC).build());

    /** The book's leather and pages, the burin on metal and on leather, each family's note, a dull scratch off the line,
     *  the piece waking, a page learned, an old table's flare, the Study's letters drifting. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOK_OPEN = sound("book_open"), PAGE_TURN = sound("page_turn"),
            BURIN_METAL = sound("burin_metal"), BURIN_LEATHER = sound("burin_leather"), SCRATCH = sound("scratch"),
            NOTE_WARD = sound("note_ward"), NOTE_BANE = sound("note_bane"), NOTE_SPOILS = sound("note_spoils"),
            NOTE_CARRY = sound("note_carry"), NOTE_KEEP = sound("note_keep"), WAKE = sound("wake"), LEARN = sound("learn"),
            FLARE = sound("flare"), DRIFT = sound("drift"), SCRAPE = sound("scrape");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register("enchant." + name, () -> SoundEvent.createVariableRangeEvent(Awaken.id("enchant." + name)));
    }

    public static SoundEvent note(Family f) {
        return (switch (f) {
            case WARD -> NOTE_WARD;
            case BANE -> NOTE_BANE;
            case SPOILS -> NOTE_SPOILS;
            case CARRYING -> NOTE_CARRY;
            case KEEPING -> NOTE_KEEP;
        }).get();
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(EnchantContent::creativeTab);
    }

    /** The table, and one page of each of the thirty (the creative tab only: in play pages are found). */
    private static void creativeTab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e) {
        if (!e.getTabKey().equals(dev.awaken.core.CoreModule.TAB_KEY)) return;
        e.accept(TABLE_ITEM.get());
        var reg = e.getParameters().holders().lookupOrThrow(Registries.ENCHANTMENT);
        for (Inscriptions.Entry en : Inscriptions.ALL) reg.get(en.key()).ifPresent(h -> e.accept(Pages.page(h, "")));
    }
}
