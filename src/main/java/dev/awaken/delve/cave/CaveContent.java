package dev.awaken.delve.cave;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The cave places' registrations (SPEC §28.5): the structure type, its piece and the placement of the first sinkhole,
 * the Delvers' things (logs, the lode map, the old pick), the mist and the draught, the caption's sound.
 */
public final class CaveContent {
    private CaveContent() {}

    static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Awaken.MODID);
    static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Awaken.MODID);
    static final DeferredRegister<MapCodec<? extends StructurePlacement>> PLACEMENTS = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, Awaken.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<CaveStructure>> TYPE = TYPES.register("cave_place", () -> () -> CaveStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("cave_place",
            () -> (StructurePieceType) (context, tag) -> new CavePiece(tag));
    public static final DeferredHolder<MapCodec<? extends StructurePlacement>, MapCodec<NearCityPlacement>> NEAR_CITY = PLACEMENTS.register("near_city",
            () -> NearCityPlacement.CODEC);

    /** Which of the Delvers' logs a log is (0..{@link CaveItems#LOGS} - 1). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LOG_INDEX = COMPONENTS.registerComponentType("delver_log",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredItem<CaveItems.LogItem> DELVERS_LOG = ITEMS.registerItem("delvers_log", CaveItems.LogItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<CaveItems.MapItem> LODE_MAP = ITEMS.registerItem("lode_map", CaveItems.MapItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<CaveItems.PickItem> DELVERS_PICK = ITEMS.registerItem("delvers_pick", CaveItems.PickItem::new,
            p -> p.stacksTo(1).rarity(Rarity.RARE));

    /** The logs a player has read (bits; kept through death), for the Codex. */
    public static final Supplier<AttachmentType<Integer>> LOGS_READ = ATTACHMENTS.register("delver_logs",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("logs")).copyOnDeath().build());

    /** Mist rising from a sinkhole by day; a breath of cold air at a chute's mouth. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST = PARTICLES.register("delve_mist", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DRAUGHT = PARTICLES.register("delve_draught", () -> new SimpleParticleType(false));
    /** A grotto's own light, drawn: a soft halo over its pool and in its curtain, and the glints in the falling water. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GLOW = PARTICLES.register("delve_grotto_glow", () -> new SimpleParticleType(true));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GLINT = PARTICLES.register("delve_grotto_glint", () -> new SimpleParticleType(true));

    /** A place's name arriving: a low, open note. */
    public static final DeferredHolder<SoundEvent, SoundEvent> NAME = SOUNDS.register("delve.cave.name",
            () -> SoundEvent.createVariableRangeEvent(Awaken.id("delve.cave.name")));

    public static ResourceKey<Structure> structure(CaveKind kind) {
        return ResourceKey.create(Registries.STRUCTURE, Awaken.id("delve/" + kind.id));
    }

    /** The first sinkhole near the Commons' edge (its own structure, so its placement can be on purpose). */
    public static final ResourceKey<Structure> FIRST = ResourceKey.create(Registries.STRUCTURE, Awaken.id("delve/first_sinkhole"));

    static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
        PLACEMENTS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
    }
}
