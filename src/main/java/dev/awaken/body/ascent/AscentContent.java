package dev.awaken.body.ascent;

import dev.awaken.Awaken;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** The Ascent's registrations: the saved record, the live motion, its sounds and its light. */
public final class AscentContent {
    private AscentContent() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);

    /** Saved with the player and kept through death: the tiers held. */
    public static final Supplier<AttachmentType<AscentMemory>> MEMORY = ATTACHMENTS.register("ascent_memory",
            () -> AttachmentType.builder(() -> AscentMemory.EMPTY).serialize(AscentMemory.MAP_CODEC, m -> !m.isEmpty()).copyOnDeath().build());

    /** The state in progress (server only, never saved). */
    static final Supplier<AttachmentType<AscentMotion>> MOTION = ATTACHMENTS.register("ascent_motion",
            () -> AttachmentType.builder(AscentMotion::new).build());

    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_FIRE = sound("ascent.rope_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_BITE = sound("ascent.rope_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_STRAIN = sound("ascent.rope_strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_RELEASE = sound("ascent.rope_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH = sound("ascent.launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = sound("ascent.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINGS_OPEN = sound("ascent.wings_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> WINGS_FOLD = sound("ascent.wings_fold");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAP = sound("ascent.flap");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOM = sound("ascent.boom");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND = sound("ascent.land");
    public static final DeferredHolder<SoundEvent, SoundEvent> REMEMBER = sound("ascent.remember");
    public static final DeferredHolder<SoundEvent, SoundEvent> UPDRAFT = sound("ascent.updraft");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOW_CALL = sound("ascent.vow_call");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOW_LINE = sound("ascent.vow_line");

    /** An anchor ring: a Precursor ring for the rope (the sky platforms' ladders, the spires); unbreakable, alight. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block> ANCHOR_RING = BLOCKS.registerSimpleBlock(
            "anchor_ring", p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN).strength(-1F, 3600000F)
                    .sound(net.minecraft.world.level.block.SoundType.AMETHYST).lightLevel(s -> 12).noLootTable());
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> ANCHOR_RING_ITEM =
            ITEMS.registerSimpleBlockItem(ANCHOR_RING);
    /** A sky platform's core: Wings to whoever reaches it by moving (SPEC §17.9); unbreakable, alight. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<WingCoreBlock> WING_CORE = BLOCKS.registerBlock("wing_core",
            WingCoreBlock::new, p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_LIGHT_BLUE).strength(-1F, 3600000F)
                    .sound(net.minecraft.world.level.block.SoundType.AMETHYST).lightLevel(s -> 15).noLootTable());
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> WING_CORE_ITEM =
            ITEMS.registerSimpleBlockItem(WING_CORE);
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<WingCoreBlockEntity>> WING_CORE_BE =
            BLOCK_ENTITIES.register("wing_core", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(WingCoreBlockEntity::new,
                    WING_CORE.get()));

    /** A Precursor grate that breathes a column of rising air (the Wilds' pylons stand on them; SPEC §17.6). */
    public static final net.neoforged.neoforge.registries.DeferredBlock<UpdraftVentBlock> UPDRAFT_VENT = BLOCKS.registerBlock("updraft_vent",
            UpdraftVentBlock::new, p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN).strength(3.5F, 9F)
                    .requiresCorrectToolForDrops().sound(net.minecraft.world.level.block.SoundType.DEEPSLATE_TILES).lightLevel(s -> 10));
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> UPDRAFT_VENT_ITEM =
            ITEMS.registerSimpleBlockItem(UPDRAFT_VENT);
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<UpdraftVentBlockEntity>> UPDRAFT_VENT_BE =
            BLOCK_ENTITIES.register("updraft_vent", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(UpdraftVentBlockEntity::new,
                    UPDRAFT_VENT.get()));

    /** Motes of the Signal's light: sparks where a rope bites, feathers off the wings, the shock ring's grit. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE = PARTICLES.register("ascent_mote", () -> new SimpleParticleType(false));

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
