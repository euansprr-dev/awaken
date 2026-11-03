package dev.awaken.gates.world;

import dev.awaken.Awaken;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.DepthBlocks;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.block.ThemeBlocks;
import dev.awaken.gates.block.WorldBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What holds each placed thing up (SPEC §38.16, Euan 2026-11-03: "a lot of floating things in gates instead of placement
 * all being thought out"): a plant, a prop, crumbs or a candle stand on a solid face; a lantern, roots or a drip hang from
 * one (through a chain); a string of lights is tied at both ends; a vestment or a torch is against a wall; a fall of light
 * runs from something. The gate painters keep to it on content version 9 ({@link #settle}: a thing whose spot doesn't
 * hold it is left out, never left floating) and {@code gates_placement} holds every floor to it.
 */
final class Holds {
    private Holds() {}

    static final byte NONE = 0, SOLID = 1, STAND = 2, HANG = 3, WALL = 4, CHAIN = 5, FALL = 6, FACES = 7, ATTACH = 8, STRAND = 9,
            FLOATS = 10, STRING = 11;
    static final String[] NAMES = {"-", "cluster", "stand", "hang", "wall", "chain", "fall", "face", "attach", "strand", "float", "string"};

    /** Strung across a street or a court (lights, prayer flags): tied at both ends. */
    static final Set<String> STRUNG = Set.of("market_lights", "frost_flags");
    /** Meant to float on water (a lamp boat, a lily candle). */
    static final Set<String> AFLOAT = Set.of("market_boat_lamp", "crypt_lily_candle");
    /** Sky, not things: the abyss's stars. */
    static final Set<String> SKY = Set.of("abyss_star");

    /** A rule and, for a wall piece, the side its wall is on (for a chain, its axis as a direction). */
    record Need(byte rule, Direction to) {
        static final Need NOTHING = new Need(NONE, null), SOLIDS = new Need(SOLID, null), STANDS = new Need(STAND, null),
                HANGS = new Need(HANG, null), STRANDS = new Need(STRAND, null), ATTACHES = new Need(ATTACH, null);
    }

    private static final Map<BlockState, Need> NEEDS = new ConcurrentHashMap<>();
    private static volatile Map<Block, ThemeBlocks.Shape> themeShapes;
    private static volatile Set<Block> bits;

    private static synchronized void tables() {
        if (themeShapes != null) return;
        Map<Block, ThemeBlocks.Shape> m = new HashMap<>();
        for (ThemeBlocks.Def d : ThemeBlocks.DEFS) {
            Block b = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(Awaken.MODID, d.name()));
            if (b != Blocks.AIR) m.put(b, d.shape());
        }
        Set<Block> bs = new java.util.HashSet<>();
        for (DepthBlocks.Def d : DepthBlocks.DEFS) {
            if (d.kind() != DepthBlocks.Kind.BITS) continue;
            Block b = BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(Awaken.MODID, d.name()));
            if (b != Blocks.AIR) bs.add(b);
        }
        bits = bs;
        themeShapes = m;
    }

    static Need need(BlockState s) {
        Need n = NEEDS.get(s);
        if (n == null) {
            n = classify(s);
            NEEDS.put(s, n);
        }
        return n;
    }

    static byte rule(BlockState s) {
        return need(s).rule();
    }

    private static Need classify(BlockState s) {
        if (themeShapes == null) tables();
        Block b = s.getBlock();
        if (s.isAir() || b instanceof LiquidBlock || b instanceof BubbleColumnBlock || s.getRenderShape() == RenderShape.INVISIBLE
                || b instanceof GateBlocks.Core || b instanceof GateBlocks.Portal) return Need.NOTHING;
        String name = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (SKY.contains(name)) return Need.NOTHING;
        if (STRUNG.contains(name)) return new Need(STRING, null);
        if (AFLOAT.contains(name) || b instanceof LilyPadBlock || b instanceof FrogspawnBlock) return new Need(FLOATS, null);
        ThemeBlocks.Shape shape = themeShapes.get(b);
        if (shape != null) {
            return switch (shape) {
                case PLANT, CARPET, TOUCH, PROP, FIGURE -> Need.STANDS;
                case HANG -> Need.STRANDS;
                case FALL -> new Need(FALL, null);
                case CLOUD -> Need.NOTHING;
                default -> Need.SOLIDS;
            };
        }
        if (bits.contains(b) || b instanceof DepthBlocks.Drift) return Need.STANDS;
        if (b == GatesRegistry.CRYPT_BELL_ROPE.get()) return Need.STRANDS;
        if (b instanceof WorldBlocks.Facing && s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty())
            return new Need(WALL, s.getValue(WorldBlocks.Facing.FACING).getOpposite());
        if (b instanceof ChainBlock) return new Need(CHAIN, Direction.fromAxisAndDirection(s.getValue(ChainBlock.AXIS), Direction.AxisDirection.POSITIVE));
        if (b instanceof LanternBlock) return s.getValue(LanternBlock.HANGING) ? Need.HANGS : Need.STANDS;
        if (b instanceof HangingRootsBlock || b instanceof SporeBlossomBlock || b instanceof CaveVines || b instanceof WeepingVinesBlock
                || b instanceof WeepingVinesPlantBlock) return Need.HANGS;
        if (b instanceof KelpBlock || b instanceof KelpPlantBlock || b instanceof TwistingVinesBlock || b instanceof TwistingVinesPlantBlock
                || b instanceof PointedDripstoneBlock) return Need.STRANDS;
        if (b instanceof VineBlock || b instanceof MultifaceBlock) return new Need(FACES, null);
        if (b instanceof BellBlock) {
            BellAttachType a = s.getValue(BellBlock.ATTACHMENT);
            return switch (a) {
                case FLOOR -> Need.STANDS;
                case CEILING -> Need.HANGS;
                default -> new Need(WALL, s.getValue(BellBlock.FACING).getOpposite());
            };
        }
        if (b instanceof FaceAttachedHorizontalDirectionalBlock) {
            AttachFace f = s.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
            return f == AttachFace.FLOOR ? Need.STANDS : f == AttachFace.CEILING ? Need.HANGS
                    : new Need(WALL, s.getValue(FaceAttachedHorizontalDirectionalBlock.FACING).getOpposite());
        }
        if (b instanceof WallTorchBlock || b instanceof LadderBlock || b instanceof WallBannerBlock || b instanceof WallSignBlock
                || b instanceof WallSkullBlock || b instanceof TripWireHookBlock || b instanceof BaseCoralWallFanBlock)
            return new Need(WALL, s.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        if ((b instanceof AmethystClusterBlock || b instanceof RodBlock) && s.hasProperty(BlockStateProperties.FACING)) {
            Direction d = s.getValue(BlockStateProperties.FACING);
            return d == Direction.UP ? Need.STANDS : d == Direction.DOWN ? Need.HANGS : new Need(WALL, d.getOpposite());
        }
        if (b instanceof VegetationBlock || b instanceof CarpetBlock || b instanceof CandleBlock || b instanceof CandleCakeBlock
                || b instanceof CakeBlock || b instanceof BasePressurePlateBlock || b instanceof FlowerPotBlock || b instanceof BaseCoralPlantTypeBlock
                || b instanceof SeaPickleBlock || b instanceof TorchBlock || b instanceof SnowLayerBlock || b instanceof BaseRailBlock
                || b instanceof LeafLitterBlock || b instanceof FlowerBedBlock || b instanceof StandingSignBlock
                || b instanceof BannerBlock || b instanceof SkullBlock || b instanceof DriedGhastBlock || b instanceof TurtleEggBlock
                || b instanceof CampfireBlock || b instanceof GateBlocks.Shrine || b instanceof GateBlocks.Seal || b instanceof GateBlocks.Cache
                || b instanceof GateBlocks.Rune || b instanceof GateBlocks.Lantern || b instanceof GateBlocks.Updraft
                || b instanceof WorldBlocks.Candles || b instanceof ThemeBlocks.Shaped)
            return Need.STANDS;
        if (b instanceof WebBlock) return Need.ATTACHES;
        if (!s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() || s.isSolidRender()) return Need.SOLIDS;
        return Need.ATTACHES;
    }

    // ---- holding ---------------------------------------------------------------------------------------------------------

    /** The blocks round a spot: {@code null} where it can't see (outside the chunk being painted): never a reason to drop. */
    interface Grid {
        BlockState at(int x, int y, int z);
    }

    private static final Map<BlockState, Byte> STURDY = new ConcurrentHashMap<>();

    /** The face of {@code s} toward {@code face} holds something small up (a candle, a lantern's chain): its middle is there. */
    static boolean sturdy(BlockState s, Direction face) {
        if (s == null) return true;
        Byte bits = STURDY.get(s);
        if (bits == null) {
            int v = 0;
            for (Direction d : Direction.values())
                if (s.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, d, SupportType.CENTER)) v |= 1 << d.ordinal();
            bits = (byte) v;
            STURDY.put(s, bits);
        }
        return (bits & (1 << face.ordinal())) != 0;
    }

    private static byte kind(BlockState s) {
        return s == null ? NONE : rule(s);
    }

    /**
     * Whether the thing {@code s} at (x, y, z) is held by what's round it. Strings are judged whole by the census (a
     * painter ties them itself); anything the grid can't see counts as holding.
     */
    static boolean held(Grid g, BlockState s, int x, int y, int z) {
        Need nd = need(s);
        switch (nd.rule()) {
            case STAND -> {
                BlockState below = g.at(x, y - 1, z);
                if (below == null || sturdy(below, Direction.UP)) return true;
                // the upper half of a tall plant stands on its lower half
                return below.getBlock() == s.getBlock() && s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            }
            case FLOATS -> {
                BlockState below = g.at(x, y - 1, z);
                return below == null || below.getBlock() == Blocks.WATER || sturdy(below, Direction.UP);
            }
            case HANG -> {
                return hangs(g, x, y, z);
            }
            case STRAND, FALL -> {
                // a strand (roots, a drip, kelp, a rope) or a fall is held at one end: from above, or up from below
                byte k = nd.rule();
                int top = y, bot = y;
                BlockState t;
                while ((t = g.at(x, top + 1, z)) != null && kind(t) == k && top < y + 64) top++;
                BlockState over = g.at(x, top + 1, z);
                if (over == null || sturdy(over, Direction.DOWN) || kind(over) == SOLID || kind(over) == CHAIN) return true;
                while ((t = g.at(x, bot - 1, z)) != null && kind(t) == k && bot > y - 64) bot--;
                BlockState under = g.at(x, bot - 1, z);
                return under == null || sturdy(under, Direction.UP) || kind(under) == SOLID
                        || k == FALL && under.getBlock() == Blocks.WATER;
            }
            case CHAIN -> {
                Direction ax = nd.to();
                if (ax == Direction.UP) return hangs(g, x, y, z) || end(g, x, y, z, Direction.DOWN);
                return end(g, x, y, z, ax) && end(g, x, y, z, ax.getOpposite());
            }
            case WALL -> {
                Direction d = nd.to();
                BlockState back = g.at(x + d.getStepX(), y, z + d.getStepZ());
                return back == null || sturdy(back, d.getOpposite()) || kind(back) == SOLID;
            }
            case FACES -> {
                boolean any = false;
                BlockState above = g.at(x, y + 1, z);
                for (Direction d : Direction.values()) {
                    var p = MultifaceBlock.getFaceProperty(d);
                    if (!s.hasProperty(p) || !s.getValue(p)) continue;
                    any = true;
                    BlockState n = g.at(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                    if (n == null || sturdy(n, d.getOpposite())) continue;
                    // a vine's side held by the vine above it with the same side
                    if (s.getBlock() instanceof VineBlock && above != null && above.getBlock() == s.getBlock() && above.hasProperty(p)
                            && above.getValue(p)) continue;
                    return false;
                }
                return any || above == null || above.getBlock() == s.getBlock();
            }
            case ATTACH -> {
                for (Direction d : Direction.values()) {
                    BlockState n = g.at(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
                    if (n == null || kind(n) == SOLID) return true;
                }
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    /** Hung from above: the block over it holds it, or a chain does that is itself hung. */
    private static boolean hangs(Grid g, int x, int y, int z) {
        for (int yy = y + 1; yy < y + 64; yy++) {
            BlockState s = g.at(x, yy, z);
            if (s == null) return true;
            if (s.getBlock() instanceof ChainBlock && s.getValue(ChainBlock.AXIS) == Direction.Axis.Y) continue;
            if (s.getBlock() instanceof CaveVines || s.getBlock() instanceof WeepingVinesPlantBlock) continue;
            // a lamp on a string of lights hangs from the string (the census ties the string itself)
            return sturdy(s, Direction.DOWN) || kind(s) == SOLID || kind(s) == STRING;
        }
        return false;
    }

    /** The far end of a chain run along {@code d} meets something solid. */
    private static boolean end(Grid g, int x, int y, int z, Direction d) {
        for (int k = 1; k < 64; k++) {
            BlockState s = g.at(x + d.getStepX() * k, y + d.getStepY() * k, z + d.getStepZ() * k);
            if (s == null) return true;
            if (s.getBlock() instanceof ChainBlock) continue;
            return sturdy(s, d.getOpposite()) || kind(s) == SOLID;
        }
        return false;
    }

    // ---- settling a painted chunk (content version 9) ---------------------------------------------------------------------

    /** Things a settle looks at: everything that hangs, stands, leans or runs (not solid blocks, not strings). */
    static boolean settles(byte rule) {
        return rule != NONE && rule != SOLID && rule != STRING;
    }

    /**
     * What a settle may leave out: never a gate's own pieces (a shrine, a seal, a cache, a rune, a lost lantern, an
     * updraft), which the game looks for where the plan put them; their placers stand them where they're held.
     */
    static boolean settles(BlockState s) {
        Block b = s.getBlock();
        return settles(rule(s)) && !(b instanceof GateBlocks.Shrine || b instanceof GateBlocks.Seal || b instanceof GateBlocks.Cache
                || b instanceof GateBlocks.Rune || b instanceof GateBlocks.Lantern || b instanceof GateBlocks.Updraft);
    }

    /**
     * Leaves out what nothing holds up among {@code n} painted things at (xs, ys, zs), listed from the bottom up
     * ({@code grid} sees the painting, {@code drop} empties a block): hung things from the top down, standing things from
     * the bottom up, twice, so a chain left out takes its lantern with it. Returns how many were left out.
     */
    static int settle(Grid grid, int[] xs, int[] ys, int[] zs, int n, Drop drop) {
        int out = 0;
        boolean[] gone = new boolean[n];
        for (int pass = 0; pass < 2; pass++) {
            for (int k = n - 1; k >= 0; k--) {
                if (gone[k]) continue;
                BlockState s = grid.at(xs[k], ys[k], zs[k]);
                byte r = rule(s);
                if (r == STAND || r == FLOATS || held(grid, s, xs[k], ys[k], zs[k])) continue;
                drop.at(xs[k], ys[k], zs[k]);
                gone[k] = true;
                out++;
            }
            for (int k = 0; k < n; k++) {
                if (gone[k]) continue;
                BlockState s = grid.at(xs[k], ys[k], zs[k]);
                byte r = rule(s);
                if (r != STAND && r != FLOATS || held(grid, s, xs[k], ys[k], zs[k])) continue;
                drop.at(xs[k], ys[k], zs[k]);
                gone[k] = true;
                out++;
            }
        }
        return out;
    }

    interface Drop {
        void at(int x, int y, int z);
    }
}
