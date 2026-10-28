package dev.awaken.delve;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.awaken.Awaken;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * The deep's registrations (SPEC §28.3): Deepglass and its shard, the lode hearts, starmetal, bell bronze, the Tuning
 * Fork, pitons and Runner's rope, the miner's lamp, the lode feature, and the strike's particles and sounds. Textures,
 * models, recipes, loot and tags come from tools/art/specs/delve.py; sounds from tools/art/specs/audio_delve.py.
 */
public final class DelveContent {
    private DelveContent() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, Awaken.MODID);
    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    static final DeferredRegister<MapCodec<? extends net.neoforged.neoforge.common.world.BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Awaken.MODID);
    /** Vanilla's scattered iron, copper, coal and gold at 35% in new land (SPEC §34.2). */
    public static final DeferredHolder<MapCodec<? extends net.neoforged.neoforge.common.world.BiomeModifier>, MapCodec<ThinOres>> THIN_ORES =
            BIOME_MODIFIERS.register("thin_ores", () -> ThinOres.CODEC);

    // ---- tags -------------------------------------------------------------------------------------------------------

    /** Picks and Hunter mattocks: Ore Sense, the strike's feel and the heavy strike read this. */
    public static final TagKey<Item> MINING_TOOLS = TagKey.create(Registries.ITEM, Awaken.id("mining_tools"));

    /** The signature mineral of each tier from D up (PLAN 7.1): D iron, C diamond, B Deepglass, A netherite, S starmetal. */
    public static TagKey<Item> tierMetal(String tier) {
        return TagKey.create(Registries.ITEM, Awaken.id("tier_metal_" + tier.toLowerCase(java.util.Locale.ROOT)));
    }

    // ---- Deepglass, the hearts, the metals ----------------------------------------------------------------------------

    /** Black-violet crystal where the Deep presses up into the rock: below y 0, and in the Descent's strata walls. */
    public static final DeferredBlock<DeepglassBlock> DEEPGLASS = BLOCKS.registerBlock("deepglass", DeepglassBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(5.5F, 8F).requiresCorrectToolForDrops().sound(SoundType.AMETHYST)
                    .lightLevel(s -> 3));
    public static final DeferredItem<BlockItem> DEEPGLASS_ITEM = ITEMS.registerSimpleBlockItem(DEEPGLASS);
    public static final DeferredItem<Item> DEEPGLASS_SHARD = ITEMS.registerSimpleItem("deepglass_shard", p -> p.rarity(Rarity.UNCOMMON));

    /** One heart per ore (so each takes its own tool tier), each a crystallised knot of its ore that glints. */
    public static final Map<Ore, DeferredBlock<LodeHeartBlock>> HEARTS = new EnumMap<>(Ore.class);
    public static final Map<Ore, DeferredItem<BlockItem>> HEART_ITEMS = new EnumMap<>(Ore.class);

    static {
        for (Ore o : Ore.values()) {
            DeferredBlock<LodeHeartBlock> b = BLOCKS.registerBlock(o.id() + "_lode_heart", p -> new LodeHeartBlock(p, o),
                    p -> p.mapColor(MapColor.DEEPSLATE).strength(6F, 6F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)
                            .lightLevel(s -> 4).pushReaction(PushReaction.IMMOVEABLE));
            HEARTS.put(o, b);
            HEART_ITEMS.put(o, ITEMS.registerSimpleBlockItem(b, p -> p.rarity(Rarity.RARE)));
        }
    }

    public static final DeferredItem<Item> RAW_STARMETAL = ITEMS.registerSimpleItem("raw_starmetal", p -> p.rarity(Rarity.RARE).fireResistant());
    public static final DeferredItem<Item> STARMETAL_INGOT = ITEMS.registerSimpleItem("starmetal_ingot", p -> p.rarity(Rarity.EPIC).fireResistant());
    public static final DeferredItem<Item> BELL_BRONZE_INGOT = ITEMS.registerSimpleItem("bell_bronze_ingot");

    // ---- tools --------------------------------------------------------------------------------------------------------

    /** The ore a Tuning Fork is tuned to (an {@link Ore} id). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TUNED = COMPONENTS.registerComponentType("tuned",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** The Delver's Pick's verses (SPEC §34.4): saved on the item, schema 1. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PickVerses.State>> PICK_VERSES = COMPONENTS.registerComponentType("pick_verses",
            b -> b.persistent(PickVerses.State.CODEC).networkSynchronized(PickVerses.State.STREAM_CODEC));

    public static final DeferredItem<TuningForkItem> TUNING_FORK = ITEMS.registerItem("tuning_fork", TuningForkItem::new,
            p -> p.stacksTo(1).durability(256).rarity(Rarity.UNCOMMON));

    /** The Delver's Pick (SPEC §34.4): asleep, it mines like a stone pick. Kept through death ({@link WakingPick}). */
    public static final DeferredItem<WakingPick.PickItem> WAKING_PICK = ITEMS.registerItem("waking_pick", WakingPick.PickItem::new,
            p -> p.pickaxe(net.minecraft.world.item.ToolMaterial.STONE, 1.0F, -2.8F).rarity(Rarity.RARE));
    /** The pick in the stone at an adit's end: each player pulls their own; unbreakable, no item. */
    public static final DeferredBlock<PickStoneBlock> WAKING_PICK_STONE = BLOCKS.registerBlock("waking_pick_stone", PickStoneBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(-1F, 3600000F).sound(SoundType.DEEPSLATE).noOcclusion().noLootTable()
                    .pushReaction(PushReaction.IMMOVEABLE));

    public static final DeferredBlock<PitonBlock> PITON = BLOCKS.registerBlock("piton", PitonBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.5F, 6F).sound(SoundType.CHAIN).noOcclusion().noCollision()
                    .pushReaction(PushReaction.POPPED));
    public static final DeferredItem<BlockItem> PITON_ITEM = ITEMS.registerSimpleBlockItem(PITON);
    /** The rope a piton hangs: climbable with the pipe grip (#awaken:runner_climbs), nothing to bump into. */
    public static final DeferredBlock<RunnerLineBlock> RUNNER_LINE = BLOCKS.registerBlock("runner_line", RunnerLineBlock::new,
            p -> p.mapColor(MapColor.WOOL).strength(0.4F).sound(SoundType.WOOL).noOcclusion().noCollision().noLootTable()
                    .pushReaction(PushReaction.POPPED));
    /** A coil of Runner's rope: use it on a piton and it pays out down to 32 blocks. */
    public static final DeferredItem<RunnerRopeItem> RUNNER_ROPE = ITEMS.registerItem("runner_rope", RunnerRopeItem::new, p -> p.stacksTo(16));

    public static final ResourceKey<EquipmentAsset> LAMP_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Awaken.id("miners_lamp"));
    /** Worn on the head: its light is drawn on the rock ahead (client: delve.client.LampGlow). */
    public static final DeferredItem<Item> MINERS_LAMP = ITEMS.registerSimpleItem("miners_lamp", p -> p.stacksTo(1)
            .component(net.minecraft.core.component.DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setAsset(LAMP_ASSET)
                    .setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_CHAIN).build()));

    // ---- worldgen ---------------------------------------------------------------------------------------------------

    static {
        FEATURES.register("lodes", () -> LodeFeature.CODEC);
        FEATURES.register("delver_lamps", () -> LampFeature.CODEC);
        FEATURES.register("cave_styles", () -> CaveStyleFeature.CODEC);
    }

    // ---- the Delver lamps (SPEC §34.6) ----------------------------------------------------------------------------------

    /** An old iron cage round a clouded crystal, hanging in a cavern: dead it barely glows; struck, it lights the hall. */
    public static final DeferredBlock<DelverLampBlock> DELVER_LAMP = BLOCKS.registerBlock("delver_lamp", DelverLampBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5F, 6F).sound(SoundType.LANTERN).noOcclusion().pushReaction(PushReaction.IMMOVEABLE)
                    .lightLevel(s -> s.getValue(DelverLampBlock.LIT) ? 14 : 3));
    public static final DeferredItem<BlockItem> DELVER_LAMP_ITEM = ITEMS.registerSimpleBlockItem(DELVER_LAMP);

    // ---- particles (client providers in delve.client.DelveParticles) -------------------------------------------------

    /** A spark struck off hard stone or ore (tinted by the client). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = PARTICLES.register("delve_spark", () -> new SimpleParticleType(false));
    /** Grit knocked off soft ground, or shaken off a roof about to fall. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GRIT = PARTICLES.register("delve_grit", () -> new SimpleParticleType(false));
    /** A thread of dust trickling down from a roof that is going to come down. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST = PARTICLES.register("delve_dust", () -> new SimpleParticleType(false));
    /** An ore's glint where light touches a lode. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GLINT = PARTICLES.register("delve_glint", () -> new SimpleParticleType(false));
    /** A mote of the Deep drifting off Deepglass. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MOTE = PARTICLES.register("delve_mote", () -> new SimpleParticleType(false));
    /** Dust in the air your light reaches (SPEC §34.5): the mote's sprite, lit by the world, so it shows only in light. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LIGHT_MOTE = PARTICLES.register("delve_light_mote", () -> new SimpleParticleType(false));

    // ---- sounds (tools/art/specs/audio_delve.py) ------------------------------------------------------------------------

    public static final Map<Ore, DeferredHolder<SoundEvent, SoundEvent>> STRIKE = new EnumMap<>(Ore.class);

    static {
        for (Ore o : Ore.values()) STRIKE.put(o, sound("delve.strike." + o.id()));
    }

    /** The last blow on an ore: the rock lets go of it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> STRIKE_LAST = sound("delve.strike_last");
    /** A true strike's chime (SPEC §34.3), played at the ore's own note. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TRUE_STRIKE = sound("delve.true_strike");
    /** A lode's heart breaks: a deep crack and the whole vein ringing. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VEIN_GIVES = sound("delve.vein_gives");
    /** The cracks running along the vein. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VEIN_CRACK = sound("delve.vein_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAVE_CREAK = sound("delve.cave_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAVE_FALL = sound("delve.cave_fall");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_CHARGE = sound("delve.heavy_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_GLINT = sound("delve.heavy_glint");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEAVY_STRIKE = sound("delve.heavy_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORK_TUNE = sound("delve.fork_tune");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORK_STRIKE = sound("delve.fork_strike");
    public static final DeferredHolder<SoundEvent, SoundEvent> FORK_RING = sound("delve.fork_ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> PITON_SET = sound("delve.piton_set");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROPE_PAY = sound("delve.rope_pay");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEEPGLASS_HUM = sound("delve.deepglass_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEEP_EAR_CAVE = sound("delve.deep_ear_cave");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEEP_EAR_GLASS = sound("delve.deep_ear_glass");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(Awaken.id(path)));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        FEATURES.register(modBus);
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
        BIOME_MODIFIERS.register(modBus);
    }
}
