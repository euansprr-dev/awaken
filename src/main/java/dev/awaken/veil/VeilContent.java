package dev.awaken.veil;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.veil.block.CloudBlock;
import dev.awaken.veil.block.GhostDoorBlock;
import dev.awaken.veil.block.GhostLeverBlock;
import dev.awaken.veil.block.StillpointDoorBlock;
import dev.awaken.veil.block.StillpointShrineBlock;
import dev.awaken.veil.block.VeilCacheBlock;
import dev.awaken.veil.block.VeilGlyphBlock;
import dev.awaken.veil.block.VeilMarkBlock;
import dev.awaken.veil.entity.VeilSpirit;
import dev.awaken.veil.world.StillpointGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/** Every registry entry the Veil owns. */
public final class VeilContent {
    private VeilContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Awaken.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    public static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, Awaken.MODID);
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);

    // ---- blocks -----------------------------------------------------------------------------------------------------

    /** Spirit matter: glows blue, only a spirit can work it. Physical things pass through. */
    private static BlockBehaviour.Properties ghost(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6F, 6.0F).sound(SoundType.AMETHYST).noCollision()
                .emissiveRendering(s -> true).pushReaction(PushReaction.IMMOVEABLE);
    }

    public static final DeferredBlock<GhostLeverBlock> GHOST_LEVER = BLOCKS.registerBlock("ghost_lever", GhostLeverBlock::new,
            p -> ghost(p).lightLevel(s -> s.getValue(GhostLeverBlock.POWERED) ? 9 : 5));
    public static final DeferredBlock<GhostDoorBlock> GHOST_DOOR = BLOCKS.registerBlock("ghost_door", GhostDoorBlock::new,
            p -> ghost(p).lightLevel(s -> s.getValue(GhostDoorBlock.OPEN) ? 2 : 6));
    public static final DeferredBlock<VeilCacheBlock> VEIL_CACHE = BLOCKS.registerBlock("veil_cache", VeilCacheBlock::new,
            p -> ghost(p).strength(-1.0F, 3_600_000.0F).noLootTable().lightLevel(s -> 11));
    /** The walls of a Veil Gate: as solid to a spirit as stone is to you. */
    public static final DeferredBlock<Block> VEIL_STONE = BLOCKS.registerSimpleBlock("veil_stone",
            p -> p.mapColor(MapColor.COLOR_BLUE).strength(-1.0F, 3_600_000.0F).sound(SoundType.AMETHYST).lightLevel(s -> 3)
                    .noLootTable().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<VeilGlyphBlock> VEIL_GLYPH = BLOCKS.registerBlock("veil_glyph", VeilGlyphBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLUE).strength(-1.0F, 3_600_000.0F).sound(SoundType.AMETHYST)
                    .lightLevel(s -> s.getValue(VeilGlyphBlock.LIT) ? 10 : 2).noLootTable().emissiveRendering(s -> s.getValue(VeilGlyphBlock.LIT)));
    /** An invisible marker that glows as a secret in spirit sight. Builders and other modules place it in hidden rooms. */
    public static final DeferredBlock<VeilMarkBlock> VEIL_MARK = BLOCKS.registerBlock("veil_mark", VeilMarkBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(-1.0F, 3_600_000.0F).noCollision().noLootTable().replaceable()
                    .pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<StillpointShrineBlock> STILLPOINT_SHRINE = BLOCKS.registerBlock("stillpoint_shrine", StillpointShrineBlock::new,
            p -> p.mapColor(MapColor.QUARTZ).strength(2.0F, 6.0F).sound(SoundType.AMETHYST).lightLevel(s -> 7).noOcclusion());
    public static final DeferredBlock<StillpointDoorBlock> DOOR_OUT = BLOCKS.registerBlock("stillpoint_door_out",
            p -> new StillpointDoorBlock(StillpointDoorBlock.Kind.OUT, p),
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3_600_000.0F).sound(SoundType.AMETHYST).noCollision()
                    .lightLevel(s -> 12).emissiveRendering(s -> true).noLootTable());
    public static final DeferredBlock<StillpointDoorBlock> DOOR_IN = BLOCKS.registerBlock("stillpoint_door_in",
            p -> new StillpointDoorBlock(StillpointDoorBlock.Kind.IN, p),
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3_600_000.0F).sound(SoundType.AMETHYST).noCollision()
                    .lightLevel(s -> 2).noLootTable());
    public static final DeferredBlock<CloudBlock> STILLPOINT_CLOUD = BLOCKS.registerBlock("stillpoint_cloud", CloudBlock::new,
            p -> p.mapColor(MapColor.SNOW).strength(0.2F).sound(SoundType.WOOL).noLootTable());

    // ---- items ------------------------------------------------------------------------------------------------------

    public static final DeferredItem<BlockItem> GHOST_LEVER_ITEM = ITEMS.registerSimpleBlockItem(GHOST_LEVER);
    public static final DeferredItem<BlockItem> GHOST_DOOR_ITEM = ITEMS.registerSimpleBlockItem(GHOST_DOOR);
    public static final DeferredItem<BlockItem> VEIL_CACHE_ITEM = ITEMS.registerSimpleBlockItem(VEIL_CACHE);
    public static final DeferredItem<BlockItem> VEIL_STONE_ITEM = ITEMS.registerSimpleBlockItem(VEIL_STONE);
    public static final DeferredItem<BlockItem> VEIL_GLYPH_ITEM = ITEMS.registerSimpleBlockItem(VEIL_GLYPH);
    public static final DeferredItem<BlockItem> VEIL_MARK_ITEM = ITEMS.registerSimpleBlockItem(VEIL_MARK);
    public static final DeferredItem<BlockItem> SHRINE_ITEM = ITEMS.registerSimpleBlockItem(STILLPOINT_SHRINE, p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> DOOR_OUT_ITEM = ITEMS.registerSimpleBlockItem(DOOR_OUT);
    public static final DeferredItem<BlockItem> DOOR_IN_ITEM = ITEMS.registerSimpleBlockItem(DOOR_IN);
    public static final DeferredItem<BlockItem> CLOUD_ITEM = ITEMS.registerSimpleBlockItem(STILLPOINT_CLOUD);

    // ---- entities ---------------------------------------------------------------------------------------------------

    public static final DeferredHolder<EntityType<?>, EntityType<VeilSpirit>> VEIL_SPIRIT = ENTITIES.registerEntityType("veil_spirit",
            VeilSpirit::new, MobCategory.MISC, b -> b.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8).noSave().noSummon().fireImmune());

    // ---- effects ----------------------------------------------------------------------------------------------------

    /** The cord was cut. Weak and slow for a while, and the Veil stays shut until it fades. */
    public static final DeferredHolder<MobEffect, MobEffect> SPIRIT_SHOCK = EFFECTS.register("spirit_shock", () -> new ShockEffect()
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, Awaken.id("effect.spirit_shock.speed"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.ATTACK_DAMAGE, Awaken.id("effect.spirit_shock.damage"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    static final class ShockEffect extends MobEffect {
        ShockEffect() { super(MobEffectCategory.HARMFUL, 0x7A86E6); }
    }

    // ---- sounds -----------------------------------------------------------------------------------------------------

    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = sound("veil.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIFT = sound("veil.drift");
    public static final DeferredHolder<SoundEvent, SoundEvent> RISE = sound("veil.rise");
    public static final DeferredHolder<SoundEvent, SoundEvent> RETURN = sound("veil.return");
    public static final DeferredHolder<SoundEvent, SoundEvent> THRESHOLD = sound("veil.threshold");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOUCH = sound("veil.touch");
    public static final DeferredHolder<SoundEvent, SoundEvent> WHISPER = sound("veil.whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> CORD_SNAP = sound("veil.cord_snap");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT = sound("veil.rift");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL = sound("veil.bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> CACHE = sound("veil.cache");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    // ---- attachments ------------------------------------------------------------------------------------------------

    /** The live projection, saved so a crash or restart always puts the player back in their body. Lost on death. */
    public static final Supplier<AttachmentType<ProjectionState>> PROJECTION = ATTACHMENTS.register("veil_projection", () -> AttachmentType
            .builder(() -> ProjectionState.NONE)
            .serialize(ProjectionState.MAP_CODEC)
            .build());

    /** What the Veil remembers about a player: spirits heard, gates scouted, rifts cleared, the Stillpoint way home. */
    public static final Supplier<AttachmentType<VeilMemory>> MEMORY = ATTACHMENTS.register("veil_memory", () -> AttachmentType
            .builder(VeilMemory::new)
            .serialize(VeilMemory.MAP_CODEC)
            .copyOnDeath()
            .build());

    /** Marks a mannequin as someone's Body. */
    public static final Supplier<AttachmentType<BodyTag>> BODY_TAG = ATTACHMENTS.register("veil_body", () -> AttachmentType
            .builder(() -> BodyTag.NONE)
            .serialize(BodyTag.MAP_CODEC)
            .build());

    // ---- world ------------------------------------------------------------------------------------------------------

    /** Keeps the Body's chunk loaded and ticking (and its dimension awake) while its owner is out. Refreshed every second. */
    public static final DeferredHolder<TicketType, TicketType> BODY_TICKET = TICKETS.register("veil_body",
            () -> new TicketType(60L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<StillpointGenerator>> STILLPOINT_GEN =
            CHUNK_GENERATORS.register("stillpoint", () -> StillpointGenerator.CODEC);

    // ---- tags -------------------------------------------------------------------------------------------------------

    /** Blocks a spirit can touch (they glow blue). */
    public static final TagKey<Block> VEIL_INTERACTABLE = TagKey.create(Registries.BLOCK, Awaken.id("veil_interactable"));
    /** Blocks a spirit cannot pass through. */
    public static final TagKey<Block> VEIL_SOLID = TagKey.create(Registries.BLOCK, Awaken.id("veil_solid"));
    /** Blocks that glow as secrets in spirit sight. */
    public static final TagKey<Block> VEIL_SECRETS = TagKey.create(Registries.BLOCK, Awaken.id("veil_secrets"));
    /** Mobs that can never be possessed. */
    public static final TagKey<EntityType<?>> UNPOSSESSABLE = TagKey.create(Registries.ENTITY_TYPE, Awaken.id("veil_unpossessable"));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        SOUNDS.register(modBus);
        ATTACHMENTS.register(modBus);
        TICKETS.register(modBus);
        CHUNK_GENERATORS.register(modBus);
        modBus.addListener(VeilContent::onAttributes);
        modBus.addListener(VeilContent::creativeTab);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(VEIL_SPIRIT.get(), VeilSpirit.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (var item : List.of(SHRINE_ITEM, GHOST_LEVER_ITEM, GHOST_DOOR_ITEM, VEIL_CACHE_ITEM, VEIL_GLYPH_ITEM, VEIL_STONE_ITEM,
                VEIL_MARK_ITEM, DOOR_OUT_ITEM, DOOR_IN_ITEM, CLOUD_ITEM)) {
            event.accept(item.get());
        }
    }
}
