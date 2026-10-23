package dev.awaken.lucidium;

import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.lucidium.block.DreamBellBlock;
import dev.awaken.lucidium.block.DreamCloudBlock;
import dev.awaken.lucidium.data.DreamState;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.effect.ShakenEffect;
import dev.awaken.lucidium.item.DreamJournalItem;
import dev.awaken.lucidium.world.DreamChunkGenerator;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Every lucidium registration: blocks, items, the Shaken effect, the dream generator, attachments, sounds. */
public final class LucidiumContent {
    private LucidiumContent() {}

    public static final ResourceKey<Level> LUCIDIUM = ResourceKey.create(Registries.DIMENSION, Awaken.id("lucidium"));

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Awaken.MODID);
    static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS = DeferredRegister.create(Registries.CHUNK_GENERATOR, Awaken.MODID);
    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, Awaken.MODID);

    // ---- blocks ------------------------------------------------------------------------------------------------------
    public static final DeferredBlock<DreamCloudBlock> DREAM_CLOUD = BLOCKS.registerBlock("dream_cloud", DreamCloudBlock::new,
            p -> p.mapColor(MapColor.SNOW).strength(0.4f).sound(SoundType.WOOL));
    public static final DeferredBlock<TransparentBlock> DREAM_GLASS = BLOCKS.registerBlock("dream_glass", TransparentBlock::new,
            p -> p.mapColor(MapColor.COLOR_PINK).strength(0.4f).sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 7)
                    .isValidSpawn((s, l, pos, t) -> false).isRedstoneConductor((s, l, pos) -> false)
                    .isSuffocating((s, l, pos) -> false).isViewBlocking((s, l, pos, box) -> false));
    public static final DeferredBlock<Block> SHIFTING_STONE = BLOCKS.registerSimpleBlock("shifting_stone",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(1.5f, 6f).sound(SoundType.AMETHYST).lightLevel(s -> 4));
    public static final DeferredBlock<DreamBellBlock> DREAM_BELL = BLOCKS.registerBlock("dream_bell", DreamBellBlock::new,
            p -> p.mapColor(MapColor.GOLD).strength(2f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> s.getValue(DreamBellBlock.ARMED) ? 7 : 0));

    // ---- items -------------------------------------------------------------------------------------------------------
    public static final DeferredItem<DreamJournalItem> DREAM_JOURNAL = ITEMS.registerItem("dream_journal", DreamJournalItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> DREAM_RESIDUE = ITEMS.registerSimpleItem("dream_residue", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> DREAM_CLOUD_ITEM = ITEMS.registerSimpleBlockItem(DREAM_CLOUD);
    public static final DeferredItem<BlockItem> DREAM_GLASS_ITEM = ITEMS.registerSimpleBlockItem(DREAM_GLASS);
    public static final DeferredItem<BlockItem> SHIFTING_STONE_ITEM = ITEMS.registerSimpleBlockItem(SHIFTING_STONE);
    public static final DeferredItem<BlockItem> DREAM_BELL_ITEM = ITEMS.registerSimpleBlockItem(DREAM_BELL);

    // ---- effect ------------------------------------------------------------------------------------------------------
    public static final DeferredHolder<MobEffect, MobEffect> SHAKEN = EFFECTS.register("shaken", () -> new ShakenEffect()
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, Awaken.id("effect.shaken.speed"), -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.ATTACK_SPEED, Awaken.id("effect.shaken.attack"), -0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    // ---- world -------------------------------------------------------------------------------------------------------
    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<DreamChunkGenerator>> DREAM_CELLS =
            GENERATORS.register("dream_cells", () -> DreamChunkGenerator.CODEC);
    /** Keeps the arrival island loaded while the sleeper fades out. */
    public static final DeferredHolder<TicketType, TicketType> PRELOAD = TICKETS.register("dream_preload",
            () -> new TicketType(400L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAM_DROP = PARTICLES.register("dream_drop", () -> new SimpleParticleType(false));

    // ---- attachments ---------------------------------------------------------------------------------------------------
    public static final Supplier<AttachmentType<Dreamer>> DREAMER = ATTACHMENTS.register("dreamer",
            () -> AttachmentType.serializable(Dreamer::new).copyOnDeath().build());
    public static final Supplier<AttachmentType<DreamState>> DREAM = ATTACHMENTS.register("dream",
            () -> AttachmentType.serializable(DreamState::new).copyOnDeath().build());

    // ---- sounds ------------------------------------------------------------------------------------------------------
    public static final DeferredHolder<SoundEvent, SoundEvent> ENTER = CoreModule.sound("dream.enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> WAKE = CoreModule.sound("dream.wake");
    public static final DeferredHolder<SoundEvent, SoundEvent> REALITY_CHECK = CoreModule.sound("dream.reality_check");
    public static final DeferredHolder<SoundEvent, SoundEvent> LUCID = CoreModule.sound("dream.lucid");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGN = CoreModule.sound("dream.sign");
    public static final DeferredHolder<SoundEvent, SoundEvent> MANIFEST = CoreModule.sound("dream.manifest");
    public static final DeferredHolder<SoundEvent, SoundEvent> NIGHTMARE = CoreModule.sound("dream.nightmare");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR = CoreModule.sound("dream.door");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL = CoreModule.sound("dream.bell");

    static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        GENERATORS.register(modBus);
        PARTICLES.register(modBus);
        ATTACHMENTS.register(modBus);
        TICKETS.register(modBus);
        modBus.addListener(LucidiumContent::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        event.accept(DREAM_JOURNAL.get());
        event.accept(DREAM_RESIDUE.get());
        event.accept(DREAM_BELL_ITEM.get());
        event.accept(DREAM_CLOUD_ITEM.get());
        event.accept(DREAM_GLASS_ITEM.get());
        event.accept(SHIFTING_STONE_ITEM.get());
    }
}
