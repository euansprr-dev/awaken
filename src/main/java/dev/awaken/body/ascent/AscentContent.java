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
    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Awaken.MODID);

    /** Saved with the player and kept through death: the tiers held. */
    public static final Supplier<AttachmentType<AscentMemory>> MEMORY = ATTACHMENTS.register("ascent_memory",
            () -> AttachmentType.builder(() -> AscentMemory.EMPTY).serialize(AscentMemory.MAP_CODEC, m -> !m.isEmpty()).copyOnDeath().build());

    /** Saved with the player and kept through death: the fittings on the Tether (a line, a reel; SPEC §28.5). */
    public static final Supplier<AttachmentType<RopeKit>> ROPE_KIT = ATTACHMENTS.register("rope_kit",
            () -> AttachmentType.builder(() -> RopeKit.EMPTY).serialize(RopeKit.MAP_CODEC, k -> !k.isEmpty()).copyOnDeath().build());

    /** The rope's fittings, forged from metal and used to fit them to the Tether (SPEC §28.5). */
    public static final net.neoforged.neoforge.registries.DeferredItem<RopeFittingItem> IRON_LINE = ITEMS.registerItem("iron_line",
            p -> new RopeFittingItem(p, RopeFittingItem.Kind.IRON_LINE), p -> p.stacksTo(4));
    public static final net.neoforged.neoforge.registries.DeferredItem<RopeFittingItem> GOLD_REEL = ITEMS.registerItem("gold_reel",
            p -> new RopeFittingItem(p, RopeFittingItem.Kind.GOLD_REEL), p -> p.stacksTo(4));
    public static final net.neoforged.neoforge.registries.DeferredItem<RopeFittingItem> DEEPGLASS_LINE = ITEMS.registerItem("deepglass_line",
            p -> new RopeFittingItem(p, RopeFittingItem.Kind.DEEPGLASS_LINE), p -> p.stacksTo(4).rarity(net.minecraft.world.item.Rarity.UNCOMMON));

    /** The state in progress (server only, never saved). */
    static final Supplier<AttachmentType<AscentMotion>> MOTION = ATTACHMENTS.register("ascent_motion",
            () -> AttachmentType.builder(AscentMotion::new).build());

    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_FIRE = sound("ascent.rope_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_BITE = sound("ascent.rope_bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_STRAIN = sound("ascent.rope_strain");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_RELEASE = sound("ascent.rope_release");
    /** The bottom of an assisted swing, and a let-go in its window (SPEC §17.31). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_SWOOP = sound("ascent.rope_swoop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_SNAP = sound("ascent.rope_snap");
    /** The real swing's hoist (SPEC §62.9): the line hauled hand over hand up to where the swing starts. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_HOIST = sound("ascent.rope_hoist");
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
    /** The aimed dive (SPEC §17.33): the phantom's swoop as it goes, the mace's smash where it lands (vanilla recordings). */
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVE = sound("ascent.dive");
    public static final DeferredHolder<SoundEvent, SoundEvent> DIVE_LAND = sound("ascent.dive_land");
    // the rappel and the fittings (the game's own recordings: tools/art/specs/audit_rest.py, SPEC §66.7)
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPPEL_PAY = sound("ascent.rappel_pay");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPPEL_PAY_GLASS = sound("ascent.rappel_pay_glass");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPPEL_CATCH = sound("ascent.rappel_catch");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPPEL_KICK = sound("ascent.rappel_kick");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPPEL_PLANT = sound("ascent.rappel_plant");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPPEL_REEL = sound("ascent.rappel_reel");
    public static final DeferredHolder<SoundEvent, SoundEvent> RAPPEL_FIT = sound("ascent.rappel_fit");

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

    /** The Lesson's figure of light (SPEC §71): made by a client for itself, never summoned, sent or saved. */
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<LessonFigure>> FIGURE =
            ENTITIES.registerEntityType("lesson_figure", LessonFigure::new, net.minecraft.world.entity.MobCategory.MISC,
                    b -> b.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8).updateInterval(1).noSave().noSummon());

    /** A stand of the Leaper's Gauntlet (SPEC §71.3): a low plate that gives one of the leap's moves; unbreakable, alight. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<LeapStandBlock> LEAP_STAND = BLOCKS.registerBlock("leap_stand",
            LeapStandBlock::new, p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_ORANGE).strength(-1F, 3600000F)
                    .sound(net.minecraft.world.level.block.SoundType.COPPER).lightLevel(s -> 9).noOcclusion().noLootTable());
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> LEAP_STAND_ITEM =
            ITEMS.registerSimpleBlockItem(LEAP_STAND);
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<LeapStandBlockEntity>> LEAP_STAND_BE =
            BLOCK_ENTITIES.register("leap_stand", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(LeapStandBlockEntity::new,
                    LEAP_STAND.get()));

    /** A Tether Spire's crown: it times the climb and keeps the spire's records (SPEC §17.11); unbreakable, alight. */
    public static final net.neoforged.neoforge.registries.DeferredBlock<SpireCrownBlock> SPIRE_CROWN = BLOCKS.registerBlock("spire_crown",
            SpireCrownBlock::new, p -> p.mapColor(net.minecraft.world.level.material.MapColor.COLOR_CYAN).strength(-1F, 3600000F)
                    .sound(net.minecraft.world.level.block.SoundType.AMETHYST).lightLevel(s -> 14).noLootTable());
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.BlockItem> SPIRE_CROWN_ITEM =
            ITEMS.registerSimpleBlockItem(SPIRE_CROWN);
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<SpireCrownBlockEntity>> SPIRE_CROWN_BE =
            BLOCK_ENTITIES.register("spire_crown", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(SpireCrownBlockEntity::new,
                    SPIRE_CROWN.get()));

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

    static {
        // the scores' layers (SPEC §62): each names one of the game's own recordings in sounds.json (ascent_scores.py)
        for (int i = 0; i < AscentScoreTable.NAMES.length; i++) {
            for (int j = 0; j < AscentScoreTable.DELAYS[i].length; j++) sound("ascent.score." + AscentScoreTable.NAMES[i] + "." + j);
        }
    }

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
        ENTITIES.register(modBus);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) -> e.put(FIGURE.get(), LessonFigure.createAttributes().build()));
        // the rope's fittings with the climbing gear; the movement blocks with the courses
        dev.awaken.core.api.Tabs.fill(dev.awaken.core.api.Tabs.Shelf.CLIMBING, IRON_LINE, GOLD_REEL, DEEPGLASS_LINE);
        dev.awaken.core.api.Tabs.fill(dev.awaken.core.api.Tabs.Shelf.COURSES, ANCHOR_RING_ITEM, UPDRAFT_VENT_ITEM, SPIRE_CROWN_ITEM, WING_CORE_ITEM,
                LEAP_STAND_ITEM);
    }
}
