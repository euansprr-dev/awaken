package dev.awaken.finds;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** What the finds register (SPEC §58): the heart and its block entity, the feature, and what a player has found. */
public final class FindsContent {
    private FindsContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);

    public static final DeferredBlock<FindHeartBlock> HEART = BLOCKS.registerBlock("find_heart", FindHeartBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK));

    public static final Supplier<BlockEntityType<FindHeartBlockEntity>> HEART_BE = BLOCK_ENTITIES.register("find_heart",
            () -> new BlockEntityType<>(FindHeartBlockEntity::new, HEART.get()));

    /** What a player has found: saved, kept through death, synced to its owner. */
    public static final Supplier<AttachmentType<FindsData>> FINDS = ATTACHMENTS.register("finds",
            () -> AttachmentType.builder(() -> FindsData.EMPTY).serialize(FindsData.CODEC)
                    .sync((holder, to) -> holder == to, ByteBufCodecs.fromCodec(FindsData.CODEC.codec())).copyOnDeath().build());

    static void init(IEventBus modBus) {
        FEATURES.register("finds", () -> FindsFeature.CODEC);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        FEATURES.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
