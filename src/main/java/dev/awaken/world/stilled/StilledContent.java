package dev.awaken.world.stilled;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * The Stilled's blocks and what they carry (SPEC §43.3): the stone figure and the chest someone left behind (neither
 * has an item: the world places them), the letter, the key and the keepsake.
 */
public final class StilledContent {
    private StilledContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);

    /** Unbreakable and unmovable as bedrock: no tool, explosion, piston or power touches one (only the remove command). */
    public static final DeferredBlock<StilledBlock> STILLED = BLOCKS.registerBlock("stilled", StilledBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().sound(SoundType.STONE)
                    .pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((s, l, pos, t) -> false));
    public static final DeferredBlock<LeftChestBlock> LEFT_CHEST = BLOCKS.registerBlock("left_chest", LeftChestBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().sound(SoundType.WOOD)
                    .pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((s, l, pos, t) -> false));

    public static final Supplier<BlockEntityType<StilledBlockEntity>> STILLED_BE = BLOCK_ENTITIES.register("stilled",
            () -> new BlockEntityType<>(StilledBlockEntity::new, STILLED.get()));

    /** A letter: which of the letters, the settlement it names (its name and key: stage 4's lead), the Stilled it came from. */
    public record LetterData(int n, String place, String lead, String figure) {
        static final Codec<LetterData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("n").forGetter(LetterData::n),
                Codec.STRING.optionalFieldOf("place", "").forGetter(LetterData::place),
                Codec.STRING.optionalFieldOf("lead", "").forGetter(LetterData::lead),
                Codec.STRING.optionalFieldOf("figure", "").forGetter(LetterData::figure)
        ).apply(i, LetterData::new));
        static final StreamCodec<ByteBuf, LetterData> STREAM = StreamCodec.composite(ByteBufCodecs.VAR_INT, LetterData::n,
                ByteBufCodecs.STRING_UTF8, LetterData::place, ByteBufCodecs.STRING_UTF8, LetterData::lead, ByteBufCodecs.STRING_UTF8, LetterData::figure,
                LetterData::new);
    }

    /** A key: the empty settlement its tag names (key, name once known), its anchor, the Stilled it came from. */
    public record KeyData(String settlement, String name, int x, int z, String figure) {
        static final Codec<KeyData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("settlement").forGetter(KeyData::settlement),
                Codec.STRING.optionalFieldOf("name", "").forGetter(KeyData::name),
                Codec.INT.fieldOf("x").forGetter(KeyData::x),
                Codec.INT.fieldOf("z").forGetter(KeyData::z),
                Codec.STRING.optionalFieldOf("figure", "").forGetter(KeyData::figure)
        ).apply(i, KeyData::new));
        static final StreamCodec<ByteBuf, KeyData> STREAM = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, KeyData::settlement,
                ByteBufCodecs.STRING_UTF8, KeyData::name, ByteBufCodecs.VAR_INT, KeyData::x, ByteBufCodecs.VAR_INT, KeyData::z,
                ByteBufCodecs.STRING_UTF8, KeyData::figure, KeyData::new);
    }

    /** A keepsake: which thing, the Carillon family whose name it carries, who it was to them ("dad"), the Stilled. */
    public record KeepsakeData(int n, String family, String relation, String figure) {
        static final Codec<KeepsakeData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("n").forGetter(KeepsakeData::n),
                Codec.STRING.fieldOf("family").forGetter(KeepsakeData::family),
                Codec.STRING.optionalFieldOf("relation", "dad").forGetter(KeepsakeData::relation),
                Codec.STRING.optionalFieldOf("figure", "").forGetter(KeepsakeData::figure)
        ).apply(i, KeepsakeData::new));
        static final StreamCodec<ByteBuf, KeepsakeData> STREAM = StreamCodec.composite(ByteBufCodecs.VAR_INT, KeepsakeData::n,
                ByteBufCodecs.STRING_UTF8, KeepsakeData::family, ByteBufCodecs.STRING_UTF8, KeepsakeData::relation,
                ByteBufCodecs.STRING_UTF8, KeepsakeData::figure, KeepsakeData::new);
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LetterData>> LETTER = COMPONENTS.registerComponentType("stilled_letter",
            b -> b.persistent(LetterData.CODEC).networkSynchronized(LetterData.STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KeyData>> KEY = COMPONENTS.registerComponentType("stilled_key",
            b -> b.persistent(KeyData.CODEC).networkSynchronized(KeyData.STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KeepsakeData>> KEEPSAKE = COMPONENTS.registerComponentType("stilled_keepsake",
            b -> b.persistent(KeepsakeData.CODEC).networkSynchronized(KeepsakeData.STREAM));

    public static final DeferredItem<StilledItems.LetterItem> LETTER_ITEM = ITEMS.registerItem("stilled_letter", StilledItems.LetterItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<StilledItems.KeyItem> KEY_ITEM = ITEMS.registerItem("stilled_key", StilledItems.KeyItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<StilledItems.KeepsakeItem> KEEPSAKE_ITEM = ITEMS.registerItem("stilled_keepsake", StilledItems.KeepsakeItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
    }
}
