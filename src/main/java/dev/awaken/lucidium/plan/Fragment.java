package dev.awaken.lucidium.plan;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * An immutable block volume (a remembered place or an authored one). Index 0 of the palette is always "nothing"
 * (air is never written, so islands show through holes). Safe to share between worldgen threads.
 */
public final class Fragment {
    public final int sx, sy, sz;
    private final BlockState[] palette;
    private final short[] cells;

    private Fragment(int sx, int sy, int sz, BlockState[] palette, short[] cells) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.palette = palette;
        this.cells = cells;
    }

    public int index(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    /** The block at a local position, or null for "nothing". */
    public BlockState get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return null;
        short c = cells[index(x, y, z)];
        return c == 0 ? null : palette[c];
    }

    public int paletteSize() {
        return palette.length - 1;
    }

    /** Counts of each non-empty state (for labels and the manifest palette). */
    public Map<BlockState, Integer> histogram() {
        int[] counts = new int[palette.length];
        for (short c : cells) counts[c]++;
        Map<BlockState, Integer> out = new HashMap<>();
        for (int i = 1; i < palette.length; i++) if (counts[i] > 0) out.merge(palette[i], counts[i], Integer::sum);
        return out;
    }

    public int solidCount() {
        int n = 0;
        for (short c : cells) if (c != 0) n++;
        return n;
    }

    // ---- building --------------------------------------------------------------------------------------------------

    public static final class Builder {
        private final int sx, sy, sz;
        private final List<BlockState> palette = new ArrayList<>();
        private final Map<BlockState, Short> ids = new HashMap<>();
        private final short[] cells;

        public Builder(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.cells = new short[sx * sy * sz];
            palette.add(Blocks.AIR.defaultBlockState());
        }

        public Builder set(int x, int y, int z, BlockState state) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return this;
            short id = 0;
            if (state != null && !state.isAir()) {
                Short existing = ids.get(state);
                if (existing == null) {
                    existing = (short) palette.size();
                    palette.add(state);
                    ids.put(state, existing);
                }
                id = existing;
            }
            cells[(y * sz + z) * sx + x] = id;
            return this;
        }

        public Builder fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                    for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) set(x, y, z, state);
            return this;
        }

        public BlockState get(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return null;
            short c = cells[(y * sz + z) * sx + x];
            return c == 0 ? null : palette.get(c);
        }

        public Fragment build() {
            return new Fragment(sx, sy, sz, palette.toArray(BlockState[]::new), cells.clone());
        }
    }

    /**
     * Reads a vanilla structure template tag (palette + blocks). Block entity data is ignored on purpose: chests
     * and other containers come back empty, so nothing in a dream copies real loot.
     */
    public static Fragment fromTemplate(HolderGetter<Block> blocks, CompoundTag tag) {
        ListTag size = tag.getListOrEmpty("size");
        int sx = Math.max(1, Math.min(32, size.getIntOr(0, 16)));
        int sy = Math.max(1, Math.min(32, size.getIntOr(1, 12)));
        int sz = Math.max(1, Math.min(32, size.getIntOr(2, 16)));
        ListTag paletteTag = tag.getListOrEmpty("palette");
        if (paletteTag.isEmpty()) {
            ListTag palettes = tag.getListOrEmpty("palettes");
            if (!palettes.isEmpty()) paletteTag = palettes.getListOrEmpty(0);
        }
        BlockState[] states = new BlockState[paletteTag.size()];
        for (int i = 0; i < states.length; i++) states[i] = NbtUtils.readBlockState(blocks, paletteTag.getCompoundOrEmpty(i));
        Builder b = new Builder(sx, sy, sz);
        ListTag list = tag.getListOrEmpty("blocks");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag bt = list.getCompoundOrEmpty(i);
            ListTag pos = bt.getListOrEmpty("pos");
            int s = bt.getIntOr("state", -1);
            if (s < 0 || s >= states.length) continue;
            b.set(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0), states[s]);
        }
        return b.build();
    }

    // ---- transforms ------------------------------------------------------------------------------------------------

    /** Rotates around Y in quarter turns (footprint must be square to keep its size; ours always is). */
    public Fragment rotated(int quarterTurns) {
        int q = Math.floorMod(quarterTurns, 4);
        if (q == 0 || sx != sz) return this;
        Rotation rot = switch (q) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            default -> Rotation.COUNTERCLOCKWISE_90;
        };
        Builder b = new Builder(sx, sy, sz);
        int n = sx - 1;
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    BlockState s = get(x, y, z);
                    if (s == null) continue;
                    int nx, nz;
                    switch (q) {
                        case 1 -> { nx = n - z; nz = x; }
                        case 2 -> { nx = n - x; nz = n - z; }
                        default -> { nx = z; nz = n - x; }
                    }
                    b.set(nx, y, nz, s.rotate(rot));
                }
        return b.build();
    }

    /**
     * Turns the volume upside down (a house hanging under its island). Things that cannot hang (doors, beds, plants,
     * torches, carpets) are dropped; slabs, stairs and lanterns are flipped.
     */
    public Fragment flipped() {
        Builder b = new Builder(sx, sy, sz);
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    BlockState s = get(x, y, z);
                    if (s == null) continue;
                    BlockState f = flipState(s);
                    if (f != null) b.set(x, sy - 1 - y, z, f);
                }
        return b.build();
    }

    private static BlockState flipState(BlockState s) {
        Block block = s.getBlock();
        if (block instanceof DoorBlock || block instanceof BedBlock) return null;
        if (s.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            SlabType t = s.getValue(BlockStateProperties.SLAB_TYPE);
            return t == SlabType.DOUBLE ? s : s.setValue(BlockStateProperties.SLAB_TYPE, t == SlabType.TOP ? SlabType.BOTTOM : SlabType.TOP);
        }
        if (s.hasProperty(BlockStateProperties.HALF)) {
            Half h = s.getValue(BlockStateProperties.HALF);
            return s.setValue(BlockStateProperties.HALF, h == Half.TOP ? Half.BOTTOM : Half.TOP);
        }
        if (s.hasProperty(BlockStateProperties.HANGING)) {
            return s.setValue(BlockStateProperties.HANGING, !s.getValue(BlockStateProperties.HANGING));
        }
        if (s.hasProperty(BlockStateProperties.FACING)) {
            Direction d = s.getValue(BlockStateProperties.FACING);
            if (d.getAxis() == Direction.Axis.Y) return s.setValue(BlockStateProperties.FACING, d.getOpposite());
        }
        // anything without a full collision box (plants, torches, rails, carpets, buttons...) cannot hang
        if (s.getFluidState().isEmpty()
                && s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty()) {
            return null;
        }
        return s;
    }

    /**
     * Applies a per-state remap and a deterministic sprinkle (a few stones turn to shifting stone, a few glass panes
     * glow): the same seed always gives the same result.
     */
    public Fragment remapped(UnaryOperator<BlockState> remap, long seed, BlockState sprinkle, int sprinkleOneIn) {
        Builder b = new Builder(sx, sy, sz);
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    BlockState s = get(x, y, z);
                    if (s == null) continue;
                    BlockState r = remap.apply(s);
                    if (sprinkle != null && sprinkleOneIn > 0 && r.isSolidRender()
                            && Math.floorMod(hash(seed, x, y, z), sprinkleOneIn) == 0) {
                        r = sprinkle;
                    }
                    b.set(x, y, z, r);
                }
        return b.build();
    }

    static int hash(long seed, int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h ^= (h >>> 31);
        h *= 0x7FB5D329728EA185L;
        h ^= (h >>> 27);
        return (int) h;
    }
}
