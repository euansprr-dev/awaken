package dev.awaken.hearth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.hearth.block.CheckpointRingBlock;
import dev.awaken.hearth.block.CheckpointRingBlockEntity;
import dev.awaken.hearth.block.CourseGateBlock;
import dev.awaken.hearth.block.ForgeBlock;
import dev.awaken.hearth.block.HearthAnchorBlock;
import dev.awaken.hearth.block.MeditationMatBlock;
import dev.awaken.hearth.block.MonolithBlock;
import dev.awaken.hearth.block.RolePadBlock;
import dev.awaken.hearth.block.SpireBoardBlock;
import dev.awaken.hearth.block.SpireBoardBlockEntity;
import dev.awaken.hearth.block.TargetDummyBlock;
import dev.awaken.hearth.block.TrophyMountBlock;
import dev.awaken.hearth.block.TrophyMountBlockEntity;
import dev.awaken.hearth.echo.EchoLink;
import dev.awaken.hearth.echo.EchoRole;
import dev.awaken.hearth.player.Aura;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.hearth.training.TrainingGhost;
import dev.awaken.hearth.trial.TrialChunkGenerator;
import dev.awaken.hearth.trophy.TrophyData;
import dev.awaken.hearth.trophy.TrophyItem;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Every registration the hearth module owns. */
public final class HearthContent {
    private HearthContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);

    /** Blocks the Above module (and anyone else) marks as machines; one is enough for a Workshop. */
    public static final TagKey<net.minecraft.world.level.block.Block> MACHINES = TagKey.create(Registries.BLOCK, Awaken.id("machines"));

    // ---- blocks -----------------------------------------------------------------------------------------------------

    public static final DeferredBlock<HearthAnchorBlock> HEARTH_ANCHOR = BLOCKS.registerBlock("hearth_anchor", HearthAnchorBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(25f, 1200f).sound(SoundType.LODESTONE).lightLevel(s -> 14)
                    .pushReaction(PushReaction.IMMOVEABLE).instrument(NoteBlockInstrument.BASEDRUM));
    public static final DeferredBlock<MeditationMatBlock> MEDITATION_MAT = BLOCKS.registerBlock("meditation_mat", MeditationMatBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(0.3f).sound(SoundType.WOOL).noOcclusion());
    public static final DeferredBlock<ForgeBlock> FORGE = BLOCKS.registerBlock("forge", ForgeBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(3.5f, 6f).sound(SoundType.STONE).lightLevel(s -> 11));
    public static final DeferredBlock<TrophyMountBlock> TROPHY_MOUNT = BLOCKS.registerBlock("trophy_mount", TrophyMountBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD).noOcclusion());
    public static final Map<EchoRole, DeferredBlock<RolePadBlock>> ROLE_PADS = rolePads();
    public static final DeferredBlock<SpireBoardBlock> SPIRE_BOARD = BLOCKS.registerBlock("spire_board", SpireBoardBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> 6));
    public static final DeferredBlock<MonolithBlock> REASSESSMENT_MONOLITH = BLOCKS.registerBlock("reassessment_monolith", MonolithBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(30f, 1200f).sound(SoundType.DEEPSLATE_TILES).lightLevel(s -> 8)
                    .pushReaction(PushReaction.IMMOVEABLE).noOcclusion());
    public static final DeferredBlock<CourseGateBlock> START_GATE = BLOCKS.registerBlock("start_gate",
            p -> new CourseGateBlock(p, CourseGateBlock.Kind.START),
            p -> p.mapColor(MapColor.COLOR_GREEN).strength(1f).sound(SoundType.STONE).noCollision().noOcclusion().lightLevel(s -> 5));
    public static final DeferredBlock<CourseGateBlock> FINISH_GATE = BLOCKS.registerBlock("finish_gate",
            p -> new CourseGateBlock(p, CourseGateBlock.Kind.FINISH),
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1f).sound(SoundType.STONE).noCollision().noOcclusion().lightLevel(s -> 5));
    public static final DeferredBlock<CheckpointRingBlock> CHECKPOINT_RING = BLOCKS.registerBlock("checkpoint_ring", CheckpointRingBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.8f).sound(SoundType.AMETHYST).noCollision().noOcclusion().lightLevel(s -> 9));
    public static final DeferredBlock<TargetDummyBlock> TARGET_DUMMY = BLOCKS.registerBlock("target_dummy", TargetDummyBlock::new,
            p -> p.mapColor(MapColor.COLOR_YELLOW).strength(1.5f).sound(SoundType.GRASS).noOcclusion());

    private static Map<EchoRole, DeferredBlock<RolePadBlock>> rolePads() {
        Map<EchoRole, DeferredBlock<RolePadBlock>> m = new EnumMap<>(EchoRole.class);
        for (EchoRole r : EchoRole.values()) {
            m.put(r, BLOCKS.registerBlock("role_pad_" + r.id, p -> new RolePadBlock(p, r),
                    p -> p.mapColor(MapColor.COLOR_GRAY).strength(1.5f).sound(SoundType.STONE).noOcclusion().lightLevel(s -> 3)));
        }
        return m;
    }

    // ---- items ------------------------------------------------------------------------------------------------------

    public static final DeferredItem<BlockItem> HEARTH_ANCHOR_ITEM = ITEMS.registerSimpleBlockItem(HEARTH_ANCHOR, p -> p.rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> MEDITATION_MAT_ITEM = ITEMS.registerSimpleBlockItem(MEDITATION_MAT);
    public static final DeferredItem<BlockItem> FORGE_ITEM = ITEMS.registerSimpleBlockItem(FORGE);
    public static final DeferredItem<BlockItem> TROPHY_MOUNT_ITEM = ITEMS.registerSimpleBlockItem(TROPHY_MOUNT);
    public static final Map<EchoRole, DeferredItem<BlockItem>> ROLE_PAD_ITEMS = rolePadItems();
    public static final DeferredItem<BlockItem> SPIRE_BOARD_ITEM = ITEMS.registerSimpleBlockItem(SPIRE_BOARD);
    public static final DeferredItem<BlockItem> MONOLITH_ITEM = ITEMS.registerSimpleBlockItem(REASSESSMENT_MONOLITH, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> START_GATE_ITEM = ITEMS.registerSimpleBlockItem(START_GATE);
    public static final DeferredItem<BlockItem> FINISH_GATE_ITEM = ITEMS.registerSimpleBlockItem(FINISH_GATE);
    public static final DeferredItem<BlockItem> CHECKPOINT_RING_ITEM = ITEMS.registerSimpleBlockItem(CHECKPOINT_RING);
    public static final DeferredItem<BlockItem> TARGET_DUMMY_ITEM = ITEMS.registerSimpleBlockItem(TARGET_DUMMY);

    private static Map<EchoRole, DeferredItem<BlockItem>> rolePadItems() {
        Map<EchoRole, DeferredItem<BlockItem>> m = new EnumMap<>(EchoRole.class);
        for (EchoRole r : EchoRole.values()) m.put(r, ITEMS.registerSimpleBlockItem(ROLE_PADS.get(r)));
        return m;
    }

    // ---- components -------------------------------------------------------------------------------------------------

    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<TrophyData>> TROPHY =
            COMPONENTS.registerComponentType("trophy", b -> b.persistent(TrophyData.CODEC).networkSynchronized(TrophyData.STREAM_CODEC));
    /** Forge temper level on a weapon or armour piece. */
    public static final DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<Integer>> TEMPER =
            COMPONENTS.registerComponentType("temper", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredItem<TrophyItem> TROPHY_ITEM = ITEMS.registerItem("trophy", TrophyItem::new,
            p -> p.stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    // ---- block entities ---------------------------------------------------------------------------------------------

    public static final Supplier<BlockEntityType<TrophyMountBlockEntity>> TROPHY_MOUNT_BE = BLOCK_ENTITIES.register("trophy_mount",
            () -> new BlockEntityType<>(TrophyMountBlockEntity::new, TROPHY_MOUNT.get()));
    public static final Supplier<BlockEntityType<SpireBoardBlockEntity>> SPIRE_BOARD_BE = BLOCK_ENTITIES.register("spire_board",
            () -> new BlockEntityType<>(SpireBoardBlockEntity::new, SPIRE_BOARD.get()));
    public static final Supplier<BlockEntityType<CheckpointRingBlockEntity>> CHECKPOINT_RING_BE = BLOCK_ENTITIES.register("checkpoint_ring",
            () -> new BlockEntityType<>(CheckpointRingBlockEntity::new, CHECKPOINT_RING.get()));

    // ---- entities ---------------------------------------------------------------------------------------------------

    public static final DeferredHolder<EntityType<?>, EntityType<TownNpc>> TOWNSFOLK = ENTITIES.registerEntityType("townsfolk",
            TownNpc::new, MobCategory.MISC, b -> b.sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10));
    public static final DeferredHolder<EntityType<?>, EntityType<TrainingGhost>> TRAINING_GHOST = ENTITIES.registerEntityType("training_ghost",
            TrainingGhost::new, MobCategory.MISC, b -> b.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(8).updateInterval(1)
                    .noSave().noSummon().noLootTable());

    // ---- attachments ------------------------------------------------------------------------------------------------

    /** Everything the hearth module remembers about a player: Echo roster, rumours, bests, trial cooldown. */
    public static final Supplier<AttachmentType<HearthPlayer>> PLAYER = ATTACHMENTS.register("hearth_player", () -> AttachmentType
            .builder(HearthPlayer::new).serialize(HearthPlayer.MAP_CODEC).copyOnDeath().build());
    /** What the owner's client needs to draw the HEARTH section, the Echo chooser and Mira's rumours. Not saved. */
    public static final Supplier<AttachmentType<HearthView>> VIEW = ATTACHMENTS.register("hearth_view", () -> AttachmentType
            .builder(() -> HearthView.EMPTY).sync((holder, to) -> holder == to, HearthView.STREAM_CODEC).build());
    /** Rank aura shown to everyone who can see the player. */
    public static final Supplier<AttachmentType<Aura>> AURA = ATTACHMENTS.register("aura", () -> AttachmentType
            .builder(() -> Aura.NONE).sync(Aura.STREAM_CODEC).build());
    /** Marks an entity as somebody's Echo. Saved with the entity and synced so clients can draw it as a shadow. */
    public static final Supplier<AttachmentType<EchoLink>> ECHO_LINK = ATTACHMENTS.register("echo_link", () -> AttachmentType
            .builder(() -> EchoLink.NONE).serialize(EchoLink.MAP_CODEC, l -> l != EchoLink.NONE).sync(EchoLink.STREAM_CODEC).build());

    // ---- particles, sounds, world -----------------------------------------------------------------------------------

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ECHO_WISP = PARTICLES.register("echo_wisp", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HEARTH_EMBER = PARTICLES.register("hearth_ember", () -> new SimpleParticleType(false));

    public static final DeferredHolder<SoundEvent, SoundEvent> SND_BIND = sound("hearth.bind");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_BIND_FAIL = sound("hearth.bind_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_SUMMON = sound("hearth.summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_ANCHOR = sound("hearth.anchor");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_CHECKPOINT = sound("hearth.checkpoint");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_RECORD = sound("hearth.record");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_TRIAL = sound("hearth.trial");
    public static final DeferredHolder<SoundEvent, SoundEvent> SND_FORGE = sound("hearth.forge");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<TrialChunkGenerator>> TRIAL_GENERATOR =
            CHUNK_GENERATORS.register("trials", () -> TrialChunkGenerator.CODEC);

    /** Items shown in the shared creative tab, in order. */
    static List<DeferredItem<?>> tabItems() {
        List<DeferredItem<?>> out = new java.util.ArrayList<>(List.of(HEARTH_ANCHOR_ITEM, MEDITATION_MAT_ITEM, FORGE_ITEM, TROPHY_MOUNT_ITEM));
        for (EchoRole r : EchoRole.values()) out.add(ROLE_PAD_ITEMS.get(r));
        out.addAll(List.of(SPIRE_BOARD_ITEM, MONOLITH_ITEM, START_GATE_ITEM, CHECKPOINT_RING_ITEM, FINISH_GATE_ITEM, TARGET_DUMMY_ITEM));
        return out;
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
        CHUNK_GENERATORS.register(modBus);
    }

    static boolean isRolePad(Block b) {
        return b instanceof RolePadBlock;
    }
}
