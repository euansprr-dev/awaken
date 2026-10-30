package dev.awaken.core;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Awaken's blocks in 26.3's motion tags (SPEC §1, "Blocks that stop motion"). 26.3 builds the heightmaps rain, snow,
 * lightning, spawning and Lens read from {@code #minecraft:blocks_motion}, and suffocation, fluid push and teleports
 * from the tags it feeds, so a mod block is air to all of them until a tag adds it: rain fell inside Carillon's houses
 * (Euan 2026-10-30). The rule is vanilla's own: GameTest {@code core_blocks_motion} holds every block to it, and old
 * chunks re-prime their saved heightmaps once on load.
 */
public final class MotionTags {
    private MotionTags() {}

    /**
     * Bumped whenever blocks join the motion tags: a chunk whose saved heightmaps are older re-primes them once when it
     * loads (1: Awaken's building blocks joined, 2026-10-30).
     */
    public static final int HEIGHTS = 1;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    /** The {@link #HEIGHTS} a chunk's heightmaps were made under (0, unsaved: before Awaken's blocks were tagged). */
    public static final Supplier<AttachmentType<Integer>> HEIGHTS_SEEN = ATTACHMENTS.register("motion_heights",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("v"), v -> v > 0).build());

    private static final Set<Heightmap.Types> MOTION = EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    private static final Predicate<BlockState> OURS = st -> st.is(BlockTags.BLOCKS_MOTION)
            && Awaken.MODID.equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace());

    static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(MotionTags::loaded);
    }

    /**
     * Whether a block stops motion, as vanilla decides for its own: any of its states stands solid ({@code isSolid}: a
     * collision box most of a block or a whole block high, or {@code forceSolidOn}; the shape rule the tag replaced),
     * unless it is one of the kinds vanilla leaves out ({@link #leftOut}). Leaves stop motion through
     * {@code #minecraft:leaves}; everything else through {@code #minecraft:blocks_motion_no_leaves}.
     */
    @SuppressWarnings("deprecation")
    public static boolean blocksMotion(Block b) {
        if (leftOut(b)) return false;
        for (BlockState s : b.getStateDefinition().getPossibleStates()) if (s.isSolid()) return true;
        return false;
    }

    /**
     * The solid kinds vanilla keeps out of the tag: webs (they slow, never stop), layers (snow's thin layers would float
     * the rain above the ground; our drifts are the same), standing and wall signs (their own {@code #all_signs} stops
     * fluid flow), the bamboo sapling.
     */
    public static boolean leftOut(Block b) {
        return b instanceof WebBlock || b instanceof StandingSignBlock || b instanceof WallSignBlock || b instanceof BambooSaplingBlock
                || b.defaultBlockState().hasProperty(BlockStateProperties.LAYERS);
    }

    private static void loaded(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel)) return;
        Integer seen = e.getChunk().getExistingDataOrNull(HEIGHTS_SEEN.get());
        if (seen != null && seen >= HEIGHTS) return;
        // A chunk generated now built its heightmaps under today's tags: mark it, no second pass over its blocks.
        if (e.isNewChunk()) e.getChunk().setData(HEIGHTS_SEEN.get(), HEIGHTS);
        else reprime(e.getChunk());
    }

    /**
     * A chunk saved before {@link #HEIGHTS}: its two motion heightmaps primed again from its blocks (only when it holds
     * one of ours; heights only rise, since blocks only joined), then marked, so this runs once in a chunk's life. Before
     * the chunk reaches the client, which takes its heightmaps from the server.
     */
    public static void reprime(LevelChunk chunk) {
        for (LevelChunkSection s : chunk.getSections()) {
            if (!s.hasOnlyAir() && s.maybeHas(OURS)) {
                Heightmap.primeHeightmaps(chunk, MOTION);
                break;
            }
        }
        chunk.setData(HEIGHTS_SEEN.get(), HEIGHTS);
    }
}
