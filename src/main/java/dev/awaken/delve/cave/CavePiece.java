package dev.awaken.delve.cave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.List;

/**
 * A whole cave place as one code-built piece: its {@link Spec} builds the part inside each chunk as the chunk generates
 * (the builders draw everything in the same order, so the chunks agree). A piece saved by an older builder
 * ({@link Spec#STYLE}) keeps what it already built.
 */
public class CavePiece extends StructurePiece {
    private final Spec spec;
    private final int style;

    public CavePiece(Spec spec) {
        super(CaveContent.PIECE.get(), 0, spec.box());
        this.spec = spec;
        this.style = Spec.STYLE;
        setOrientation(null);
    }

    public CavePiece(CompoundTag tag) {
        super(CaveContent.PIECE.get(), tag);
        this.spec = Spec.load(tag.getCompoundOrEmpty("spec"));
        this.style = tag.getIntOr("style", 0);
        setOrientation(null);
    }

    public Spec spec() {
        return spec;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.put("spec", spec.save());
        tag.putInt("style", style);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        if (style != Spec.STYLE) return;
        spec.build(new LevelSink(level, chunkBB, random));
    }

    /** Builds the part of the place inside {@code clip} into a level (worldgen: one chunk; commands: all of it); its marks. */
    public static java.util.Map<String, BlockPos> build(WorldGenLevel level, Spec spec, BoundingBox clip) {
        LevelSink k = new LevelSink(level, clip, RandomSource.create(spec.seed()));
        k.marks = new java.util.HashMap<>();
        spec.build(k);
        return k.marks;
    }

    /** A sink over the world, clipped to a box. Block updates stay off (as worldgen's are); water is told to flow. */
    static final class LevelSink implements Sink {
        private final WorldGenLevel level;
        private final BoundingBox clip;
        private final RandomSource random;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        java.util.Map<String, BlockPos> marks;

        LevelSink(WorldGenLevel level, BoundingBox clip, RandomSource random) {
            this.level = level;
            this.clip = clip;
            this.random = random;
        }

        @Override
        public int minX() {
            return clip.minX();
        }

        @Override
        public int maxX() {
            return clip.maxX();
        }

        @Override
        public int minZ() {
            return clip.minZ();
        }

        @Override
        public int maxZ() {
            return clip.maxZ();
        }

        @Override
        public int minY() {
            return Math.max(clip.minY(), level.getMinY() + 1);
        }

        @Override
        public int maxY() {
            return Math.min(clip.maxY(), level.getMaxY() - 1);
        }

        @Override
        public BlockState get(int x, int y, int z) {
            return level.getBlockState(m.set(x, y, z));
        }

        @Override
        public void set(int x, int y, int z, BlockState state) {
            level.setBlock(m.set(x, y, z), state, Block.UPDATE_CLIENTS);
        }

        @Override
        public void flow(int x, int y, int z) {
            if (!inside(x, y, z)) return;
            BlockState s = get(x, y, z);
            if (!s.getFluidState().isEmpty()) level.scheduleTick(m.set(x, y, z), s.getFluidState().getType(), 0);
        }

        @Override
        public void container(int x, int y, int z, BlockState state, List<ItemStack> items) {
            if (!inside(x, y, z)) return;
            set(x, y, z, state);
            if (level.getBlockEntity(m.set(x, y, z)) instanceof Container c) {
                for (int i = 0; i < Math.min(items.size(), c.getContainerSize()); i++) c.setItem(i * 2 % c.getContainerSize(), items.get(i).copy());
            }
        }

        @Override
        public void lootChest(int x, int y, int z, BlockState state, String table, long seed) {
            if (!inside(x, y, z)) return;
            set(x, y, z, state);
            RandomizableContainer.setBlockEntityLootTable(level, random, m.set(x, y, z),
                    ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(table)));
        }

        @Override
        public void mark(String what, int x, int y, int z) {
            if (marks != null) marks.put(what, new BlockPos(x, y, z));
        }

        @Override
        public void minecart(int x, int y, int z) {
            if (!inside(x, y, z)) return;
            var cart = EntityTypes.MINECART.create(level.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
            if (cart == null) return;
            cart.setInitialPos(x + 0.5, y + 0.5, z + 0.5);
            level.addFreshEntity(cart);
        }
    }
}
