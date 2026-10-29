package dev.awaken.gates;

import dev.awaken.gates.block.GateBlocks;
import dev.awaken.gates.block.GateFissureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The surface gate (docs/modules/structures.md, "Gates"): a vortex hanging over a scorched pad, drawn by the anchor
 * core's renderer; invisible core blocks fill the opening and carry the interaction; rock spurs lean out from both
 * flanks; fissures in the rank colour run from the foot of the vortex across the pad and the crater ring around it.
 * {@code pos} is the ground block under the centre of the opening; {@code axis} 0 means the opening faces +-Z.
 *
 * <p>Only natural blocks are ever replaced (terrain, plants, trees), so a gate never eats a build; the placement checks
 * ({@link #untouched}) refuse spots near one in the first place.
 */
public final class Overworld {
    private Overworld() {}

    /** Gates whose structure may contain a block within this distance of the block (structure lookups). */
    public static final int REACH = 26;

    public record Placed(BlockPos pos, BlockState state) {}

    /**
     * Ground the vanilla tags don't know: the Awakened Lands' own (fused glass, riven shale, hoarfrost...) and the Soft
     * Land's step slabs (SPEC §31.2). A gate stands on it and replaces it like any terrain (SPEC §7, "Where a gate stands").
     */
    public static final TagKey<Block> GROUND = TagKey.create(Registries.BLOCK, dev.awaken.Awaken.id("gates_ground"));

    /** The anchor core (bottom centre of the opening): its block entity draws the vortex. */
    public static BlockPos anchor(BlockPos pos) {
        return pos.above();
    }

    /** a = along the gate's face, b = through it. */
    static BlockPos at(BlockPos pos, int axis, int a, int y, int b) {
        return axis == 0 ? pos.offset(a, y, b) : pos.offset(b, y, a);
    }

    // ---- the fixed part: cores and rock spurs --------------------------------------------------------------------------

    /** Every fixed block of the structure: the opening's cores (the anchor first) and the rock spurs. */
    public static List<Placed> blocks(BlockPos pos, int axis, int rank, boolean broken) {
        List<Placed> out = new ArrayList<>();
        int r = GateVortex.clampRank(rank);
        BlockState core = GatesRegistry.CORE.get().defaultBlockState().setValue(GateBlocks.Core.RANK, r).setValue(GateBlocks.Core.BROKEN, broken);
        out.add(new Placed(anchor(pos), core.setValue(GateBlocks.Core.ANCHOR, true)));
        int half = GateVortex.coreHalfWidth(r) + 1, top = GateVortex.coreTop(r);
        for (int y = 1; y <= top; y++) {
            for (int a = -half; a <= half; a++) {
                if ((a == 0 && y == 1) || !GateVortex.core(r, a, y)) continue;
                out.add(new Placed(at(pos, axis, a, y, 0), core));
            }
        }
        long seed = seed(pos, axis);
        java.util.Set<Long> taken = new java.util.HashSet<>();
        for (Fang f : fangs(r, seed)) fang(out, taken, pos, axis, f, seed, broken);
        return out;
    }

    /**
     * A rock fang torn up by the gate: its foot on the pad (a, b), leaning along (da, 1, db), {@code length} long,
     * {@code radius} thick at the foot, tapering to a point.
     */
    record Fang(double a, double b, double da, double db, double length, double radius) {}

    /**
     * Two great fangs flank the opening and lean away from it, two more stand off them, a crown of smaller ones rings
     * the pad (never across the walk in and out), and rubble spikes sit at its edge. Everything grows with rank.
     */
    static List<Fang> fangs(int rank, long seed) {
        GateVortex.Size s = GateVortex.size(rank);
        double h = s.ry() * 2, pad = s.padRadius();
        List<Fang> out = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            double n = noise(seed, 11, side), m = noise(seed, 13, side);
            out.add(new Fang(side * (s.rx() + 1.3), (m - 0.5) * 1.2, side * (0.3 + 0.14 * n), (m - 0.5) * 0.3,
                    h * (0.82 + 0.16 * n), 1.25 + 0.11 * rank));
            out.add(new Fang(side * (s.rx() + 2.7), side * 1.6, side * (0.62 + 0.2 * m), side * 0.2, h * (0.48 + 0.12 * m),
                    0.95 + 0.07 * rank));
        }
        int crown = 2 + rank / 2;
        for (int i = 0; i < crown; i++) {
            // within 50 degrees of the face axis, alternating left and right, front and back: the walk through the
            // opening (along b) stays clear
            double off = Math.toRadians(12 + 38 * noise(seed, 20, i)) * ((i / 2) % 2 == 0 ? 1 : -1);
            double ang = (i % 2 == 0 ? 0 : Math.PI) + off;
            double rho = pad - 1.2 - noise(seed, 21, i) * 1.5;
            double ca = Math.cos(ang), cb = Math.sin(ang);
            double lean = 0.45 + 0.35 * noise(seed, 22, i);
            out.add(new Fang(ca * rho, cb * rho, ca * lean, cb * lean, s.ry() * (0.55 + 0.35 * noise(seed, 23, i)), 0.8 + 0.06 * rank));
        }
        int rubble = 3 + rank;
        for (int i = 0; i < rubble; i++) {
            double ang = Math.PI * 2 * (i + noise(seed, 30, i)) / rubble;
            if (Math.abs(Math.sin(ang)) > 0.92) continue;
            double rho = pad - 0.3 - noise(seed, 31, i) * 2;
            double ca = Math.cos(ang), cb = Math.sin(ang);
            out.add(new Fang(ca * rho, cb * rho, ca * 0.7, cb * 0.7, 1.6 + 1.6 * noise(seed, 32, i), 0.65));
        }
        return out;
    }

    private static void fang(List<Placed> out, java.util.Set<Long> taken, BlockPos pos, int axis, Fang f, long seed, boolean broken) {
        double norm = Math.sqrt(f.da() * f.da() + 1 + f.db() * f.db());
        double ua = f.da() / norm, uy = 1 / norm, ub = f.db() / norm;
        BlockState frame = (broken ? GatesRegistry.FRAME_CRACKED : GatesRegistry.FRAME).get().defaultBlockState();
        for (double t = 0; t <= f.length(); t += 0.35) {
            double k = t / f.length();
            if (broken && k > 0.72) break; // a broken gate's fangs are snapped off
            double r = f.radius() * Math.pow(1 - k, 0.85) + 0.22;
            double ca = f.a() + ua * t, cy = 0.6 + uy * t, cb = f.b() + ub * t;
            int R = (int) Math.ceil(r);
            for (int da = -R; da <= R; da++) {
                for (int dy = -R; dy <= R; dy++) {
                    for (int db = -R; db <= R; db++) {
                        int a = (int) Math.floor(ca) + da, y = (int) Math.floor(cy) + dy, b = (int) Math.floor(cb) + db;
                        if (y < 1) continue;
                        double ex = a + 0.5 - ca, ey = y + 0.5 - cy, ez = b + 0.5 - cb;
                        if (ex * ex + ey * ey + ez * ez > r * r) continue;
                        long key = ((long) (a + 512) << 40) | ((long) (y + 512) << 20) | (b + 512);
                        if (!taken.add(key)) continue;
                        double n = noise(seed, a * 7 + b * 131, y);
                        if (broken && n > 0.88) continue;
                        BlockState st;
                        if (k > 0.62) st = n < 0.7 ? frame : Blocks.BLACKSTONE.defaultBlockState();
                        else if (k > 0.3) st = n < 0.18 ? frame : n < 0.55 ? Blocks.BLACKSTONE.defaultBlockState()
                                : n < 0.8 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : basalt();
                        else st = n < 0.45 ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.8 ? basalt() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                        out.add(new Placed(at(pos, axis, a, y, b), st));
                    }
                }
            }
        }
    }

    private static BlockState basalt() {
        return Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
    }

    static long seed(BlockPos pos, int axis) {
        return pos.asLong() * 31L + axis;
    }

    /** A stable 0..1 value per cell. */
    static double noise(long seed, int x, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + z * 0x85157AF5L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
    }

    /** Smoother noise (patches rather than speckle). */
    static double patch(long seed, int x, int z) {
        double v = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) v += noise(seed, (x + dx) >> 1, (z + dz) >> 1);
        return v / 9.0;
    }

    /** True when {@code p} is one of this gate's cores or spur blocks (the structure as {@link #blocks} lists it). */
    public static boolean isPart(GateRecord g, BlockPos p) {
        int dy = p.getY() - g.pos.getY();
        int a = g.axis == 0 ? p.getX() - g.pos.getX() : p.getZ() - g.pos.getZ();
        int b = g.axis == 0 ? p.getZ() - g.pos.getZ() : p.getX() - g.pos.getX();
        int rank = g.plan.rank().ordinal();
        if (b == 0 && GateVortex.core(rank, a, dy)) return true;
        if (Math.abs(a) > REACH || Math.abs(b) > REACH || dy < 1 || dy > 24) return false;
        for (Placed q : blocks(g.pos, g.axis, rank, false)) if (q.pos().equals(p)) return true;
        return false;
    }

    // ---- placing and removing ------------------------------------------------------------------------------------------

    /**
     * Builds the gate (the chunks must be loaded): clears the opening, lays the scorched pad and the crater ring with
     * their fissures, raises the spurs and places the cores. Used again when the gate breaks ({@code broken}): the cores
     * turn, the spurs crack and the fissures run red. Only natural blocks are replaced, except in the vortex's own column.
     * Returns whether the gate stands ({@link #stands}).
     */
    public static boolean place(ServerLevel level, GateRecord g, boolean broken) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        int rank = g.plan.rank().ordinal();
        long seed = seed(g.pos, g.axis);
        dressGround(level, g.pos, g.axis, rank, broken, seed, flags);
        // the opening and the space round it: trees and plants give way
        int half = GateVortex.coreHalfWidth(rank) + 2, top = GateVortex.coreTop(rank) + 2;
        for (int a = -half; a <= half; a++) {
            for (int b = -2; b <= 2; b++) {
                for (int y = 1; y <= top; y++) {
                    BlockPos p = at(g.pos, g.axis, a, y, b);
                    BlockState here = level.getBlockState(p);
                    if (!here.isAir() && natural(here) && !ours(here)) level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
                }
            }
        }
        // the old spurs come down first when the look changes (a break cracks them)
        for (Placed p : blocks(g.pos, g.axis, rank, false)) {
            if (p.state().is(GatesRegistry.CORE.get())) continue;
            BlockState here = level.getBlockState(p.pos());
            if (spurBlock(here)) level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), flags);
        }
        for (Placed p : blocks(g.pos, g.axis, rank, broken)) {
            BlockState here = level.getBlockState(p.pos());
            // the vortex's own column always forms (the spot was checked for builds): anything else that grew there
            // (a boulder, a glass spire) would leave a beam over nothing
            boolean core = p.state().is(GatesRegistry.CORE.get());
            if (!here.isAir() && !natural(here) && !ours(here) && !(core && yields(level, p.pos(), here))) continue;
            level.setBlock(p.pos(), p.state(), flags);
        }
        if (!(level.getBlockEntity(anchor(g.pos)) instanceof GateVortexBlockEntity be)) return false;
        be.configure(g, level.getGameTime());
        return true;
    }

    /** Whether the gate stands: its anchor core holds the vortex (chunks loaded). A gate that doesn't is never shown. */
    public static boolean stands(ServerLevel level, GateRecord g) {
        BlockState anchor = level.getBlockState(anchor(g.pos));
        return anchor.is(GatesRegistry.CORE.get()) && anchor.getValue(GateBlocks.Core.ANCHOR)
                && level.getBlockEntity(anchor(g.pos)) instanceof GateVortexBlockEntity;
    }

    /** A block the vortex's column may take: not a block entity, not unbreakable. */
    private static boolean yields(ServerLevel level, BlockPos p, BlockState here) {
        return level.getBlockEntity(p) == null && here.getDestroySpeed(level, p) >= 0;
    }

    /** The scorched pad (flat, at the gate's ground level) and the crater ring (on the terrain), with fissures. */
    private static void dressGround(ServerLevel level, BlockPos pos, int axis, int rank, boolean broken, long seed, int flags) {
        GateVortex.Size s = GateVortex.size(rank);
        float pad = s.padRadius(), ring = s.ringRadius();
        int R = (int) Math.ceil(ring);
        int rays = 5 + rank / 2;
        int clearHigh = GateVortex.coreTop(rank) + 3, half = (int) Math.ceil(s.rx()) + 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int a = -R; a <= R; a++) {
            for (int b = -R; b <= R; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > ring + 0.5) continue;
                BlockPos col = at(pos, axis, a, 0, b);
                if (!level.hasChunkAt(col)) continue;
                double crack = fissure(seed, a, b, d, rays, pad, ring);
                boolean fissure = !Double.isNaN(crack);
                if (d <= pad + 0.4) {
                    // the pad: flat at y = 0, founded below, cleared above
                    BlockState here = level.getBlockState(col);
                    if (!natural(here) && !ours(here)) continue;
                    BlockState top = fissure ? fissureState(rank, broken, crack, axis) : scorched(seed, a, b, d / pad, broken);
                    if (!here.is(GatesRegistry.FISSURE.get()) || fissure) level.setBlock(col, top, flags);
                    for (int y = -1; y >= -4; y--) {
                        m.set(col.getX(), col.getY() + y, col.getZ());
                        BlockState under = level.getBlockState(m);
                        if (!under.isAir() && under.getFluidState().isEmpty() && !under.canBeReplaced()) break;
                        level.setBlock(m, y == -1 ? Blocks.TUFF.defaultBlockState() : Blocks.DIRT.defaultBlockState(), flags);
                    }
                    // trees and plants give way over the whole pad; the ground itself is cut back only round the opening
                    // and a few blocks up elsewhere, so a gate on a slope sits in a notch rather than a quarry
                    boolean opening = Math.abs(a) <= half && Math.abs(b) <= 2;
                    for (int y = 1; y <= clearHigh; y++) {
                        m.set(col.getX(), col.getY() + y, col.getZ());
                        BlockState above = level.getBlockState(m);
                        if (above.isAir() || !natural(above) || ours(above)) continue;
                        boolean soft = above.canBeReplaced() || above.is(BlockTags.LOGS) || above.is(BlockTags.LEAVES) || above.is(BlockTags.FLOWERS);
                        if (soft || opening || y <= 3) level.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                    }
                    // a ragged lip of thrown-up rubble round the pad's edge, open where you walk in and out
                    if (d / pad > 0.9 && Math.abs(a) > 2 && !fissure && smoothNoise(seed + 41, a, b, 2.5) > 0.42) {
                        m.set(col.getX(), col.getY() + 1, col.getZ());
                        if (level.getBlockState(m).isAir()) level.setBlock(m, rubble(seed, a, b), flags);
                    }
                    continue;
                }
                // the crater ring: the terrain's own top block, scorched by distance and noise
                int gy = groundY(level, col.getX(), col.getZ());
                if (Math.abs(gy - pos.getY()) > 5) continue;
                m.set(col.getX(), gy, col.getZ());
                BlockState here = level.getBlockState(m);
                if (here.is(GatesRegistry.FISSURE.get())) {
                    level.setBlock(m, fissureState(rank, broken, fissure ? crack : 0, axis), flags);
                    continue;
                }
                if (!here.getFluidState().isEmpty() || !natural(here) || ours(here) || here.isAir()) continue;
                double t = (d - pad) / (ring - pad);
                double n = smoothNoise(seed + 5, a, b, 2.2);
                if (!fissure && n < t * 0.95 + 0.05) continue; // patchy, thinning out with distance
                level.setBlock(m, fissure && t < 0.85 ? fissureState(rank, broken, crack, axis) : scorched(seed, a, b, 1.0 + t, broken), flags);
                m.move(Direction.UP);
                BlockState plant = level.getBlockState(m);
                if (!plant.isAir() && plant.canBeReplaced() && plant.getFluidState().isEmpty()) level.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
            }
        }
    }

    /**
     * Fissures: a few jagged rays from the foot of the opening out across the pad and into the crater, and a broken
     * ring round the foot. Returns the crack's direction in the gate's (a, b) frame (radians), or NaN for no crack.
     */
    static double fissure(long seed, int a, int b, double d, int rays, float pad, float ring) {
        if (d < 1.2) return Double.NaN;
        double ang = Math.atan2(b, a);
        for (int i = 0; i < rays; i++) {
            double th = Math.PI * 2 * (i + 0.6 * noise(seed, 400 + i, 0)) / rays;
            double len = pad * 0.75 + (ring - pad * 0.75) * (0.4 + 0.6 * noise(seed, 500 + i, 1));
            if (d > len) continue;
            // the ray wanders a little as it runs out
            double wob = 0.55 * Math.sin(d * 0.7 + i * 2.1) / Math.max(1.5, d);
            double rel = ang - th - wob;
            if (Math.cos(rel) > 0 && Math.abs(Math.sin(rel)) * d < 0.5) return th + wob;
        }
        if (Math.abs(d - 2.0) < 0.5 && smoothNoise(seed + 3, a, b, 1.5) < 0.55) return ang + Math.PI / 2;
        return Double.NaN;
    }

    static BlockState fissureState(int rank, boolean broken) {
        return fissureState(rank, broken, 0, 0);
    }

    /** A fissure block whose crack runs along {@code angle} (gate frame), snapped to the four ways a block can show it. */
    static BlockState fissureState(int rank, boolean broken, double angle, int axis) {
        double ax = Math.cos(angle), az = Math.sin(angle);
        double wx = axis == 0 ? ax : az, wz = axis == 0 ? az : ax;
        int dir = Math.floorMod((int) Math.round(Math.atan2(wz, wx) / (Math.PI / 4)), 4);
        return GatesRegistry.FISSURE.get().defaultBlockState().setValue(GateFissureBlock.RANK, broken ? 7 : GateVortex.clampRank(rank))
                .setValue(GateFissureBlock.DIR, dir);
    }

    /** Value noise with cells of {@code cell} blocks, smoothly blended: patches of one material, not speckle. */
    static double smoothNoise(long seed, int x, int z, double cell) {
        double gx = x / cell, gz = z / cell;
        int x0 = (int) Math.floor(gx), z0 = (int) Math.floor(gz);
        double fx = gx - x0, fz = gz - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double v00 = noise(seed, x0, z0), v10 = noise(seed, x0 + 1, z0), v01 = noise(seed, x0, z0 + 1), v11 = noise(seed, x0 + 1, z0 + 1);
        return (v00 * (1 - fx) + v10 * fx) * (1 - fz) + (v01 * (1 - fx) + v11 * fx) * fz;
    }

    /**
     * Scorched ground ({@code t} 0..2 from the foot of the gate out to the crater's edge): polished, glassy black at the
     * centre, basalt and tuff through the middle, coarse dirt and gravel at the edge; materials come in patches.
     */
    static BlockState scorched(long seed, int a, int b, double t, boolean broken) {
        double n = smoothNoise(seed + 11, a, b, 2.6), k = noise(seed + 13, a, b);
        double tt = t + (smoothNoise(seed + 17, a, b, 3.5) - 0.5) * 0.35;
        if (tt < 0.5) {
            if (k < 0.08) return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
            if (broken && k > 0.9) return Blocks.MAGMA_BLOCK.defaultBlockState();
            return n < 0.55 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        }
        if (tt < 1.0) {
            if (n < 0.35) return basalt();
            if (n < 0.62) return Blocks.BLACKSTONE.defaultBlockState();
            return Blocks.TUFF.defaultBlockState();
        }
        if (n < 0.4) return Blocks.TUFF.defaultBlockState();
        if (n < 0.62) return Blocks.COARSE_DIRT.defaultBlockState();
        return k < 0.5 ? Blocks.GRAVEL.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState();
    }

    private static BlockState rubble(long seed, int a, int b) {
        double k = noise(seed + 19, a, b);
        return k < 0.4 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : k < 0.75 ? Blocks.TUFF.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    /** Terrain and plants (never someone's build): what a gate may replace. */
    static boolean natural(BlockState s) {
        if (s.isAir() || s.canBeReplaced()) return true;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA)
                || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SNOW) || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.SUBSTRATE_OVERWORLD)) return true;
        // ground slabs only as the soft pass lays them (a bottom half); a top or double one is someone's
        if (s.is(GROUND)) return !(s.getBlock() instanceof SlabBlock) || s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        return s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.WATER)
                || s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.PUMPKIN) || s.is(Blocks.MELON) || s.is(Blocks.BAMBOO)
                || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUD) || s.is(Blocks.COARSE_DIRT)
                || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.COBBLED_DEEPSLATE);
    }

    /** The gate's own blocks and its scorched materials (safe to replace when it is rebuilt). */
    static boolean ours(BlockState s) {
        return s.is(GatesRegistry.CORE.get()) || s.is(GatesRegistry.FRAME.get()) || s.is(GatesRegistry.FRAME_CRACKED.get())
                || s.is(GatesRegistry.FISSURE.get());
    }

    private static boolean spurBlock(BlockState s) {
        return s.is(GatesRegistry.FRAME.get()) || s.is(GatesRegistry.FRAME_CRACKED.get()) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT)
                || s.is(Blocks.POLISHED_BLACKSTONE);
    }

    /** Removes the structure: cores and spurs go, fissures go dark; the scorched pad stays as a scar. */
    public static void remove(ServerLevel level, GateRecord g) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        int rank = g.plan.rank().ordinal();
        for (Placed p : blocks(g.pos, g.axis, rank, false)) {
            if (!level.hasChunkAt(p.pos())) continue;
            BlockState here = level.getBlockState(p.pos());
            boolean core = p.state().is(GatesRegistry.CORE.get());
            if (core ? here.is(GatesRegistry.CORE.get()) : spurBlock(here)) level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), flags);
        }
        darkenFissures(level, g.pos, g.axis, GateVortex.size(rank).ringRadius() + 1, flags);
        sweepLegacy(level, g, flags);
    }

    private static void darkenFissures(ServerLevel level, BlockPos pos, int axis, float radius, int flags) {
        int R = (int) Math.ceil(radius);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int a = -R; a <= R; a++) {
            for (int b = -R; b <= R; b++) {
                BlockPos col = at(pos, axis, a, 0, b);
                if (!level.hasChunkAt(col)) continue;
                for (int y = -6; y <= 6; y++) {
                    m.set(col.getX(), col.getY() + y, col.getZ());
                    if (level.getBlockState(m).is(GatesRegistry.FISSURE.get())) level.setBlock(m, Blocks.COARSE_DIRT.defaultBlockState(), flags);
                }
            }
        }
    }

    /**
     * Gates built before the vortex stood in a small doorway (posts, lintel, a 7x5 platform of frame). Takes that down
     * so a rebuild or a removal leaves nothing behind.
     */
    static void sweepLegacy(ServerLevel level, GateRecord g, int flags) {
        for (int a = -3; a <= 3; a++) {
            for (int b = -2; b <= 2; b++) {
                BlockPos p = at(g.pos, g.axis, a, 0, b);
                BlockState here = level.getBlockState(p);
                if (here.is(GatesRegistry.FRAME.get()) || here.is(GatesRegistry.FRAME_CRACKED.get())) level.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), flags);
            }
        }
        for (int y = 1; y <= 7; y++) {
            for (int a = -2; a <= 2; a++) {
                BlockPos p = at(g.pos, g.axis, a, y, 0);
                BlockState here = level.getBlockState(p);
                boolean legacyFrame = (here.is(GatesRegistry.FRAME.get()) || here.is(GatesRegistry.FRAME_CRACKED.get())) && (Math.abs(a) == 2 || y >= 6);
                boolean legacyCore = here.is(GatesRegistry.CORE.get()) && !here.getValue(GateBlocks.Core.ANCHOR) && Math.abs(a) <= 1 && y <= 5
                        && !GateVortex.core(g.plan.rank().ordinal(), a, y);
                if (legacyFrame || legacyCore) level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
            }
        }
    }

    /** True when the gate's surface still has the old doorway (built before the vortex): it needs a rebuild. */
    static boolean legacy(ServerLevel level, GateRecord g) {
        BlockState anchor = level.getBlockState(anchor(g.pos));
        if (anchor.is(GatesRegistry.CORE.get()) && anchor.getValue(GateBlocks.Core.ANCHOR)) return false;
        BlockState post = level.getBlockState(at(g.pos, g.axis, 2, 3, 0));
        BlockState middle = level.getBlockState(g.pos.above(3));
        return post.is(GatesRegistry.FRAME.get()) || post.is(GatesRegistry.FRAME_CRACKED.get()) || middle.is(GatesRegistry.CORE.get());
    }

    // ---- placement checks ------------------------------------------------------------------------------------------------

    /** Ground under a column (top solid, non-liquid block). */
    public static int groundY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    /**
     * Nobody built here: no block entities (chests, furnaces, beds' owners), no crafted building blocks, and not
     * inside anyone's Hearth. A gate never carves into a base or a village. Checks the largest gate's footprint, so the
     * answer holds whatever rank the gate turns out to be.
     */
    public static boolean untouched(ServerLevel level, BlockPos pos, int axis) {
        return untouched(level, pos, axis, 6);
    }

    /** As above for one rank's footprint: the pad, the crater ring, the spurs and the opening. */
    public static boolean untouched(ServerLevel level, BlockPos pos, int axis, int rank) {
        if (dev.awaken.core.api.HearthHooks.nearest(level, pos, 64).isPresent()) return false;
        GateVortex.Size s = GateVortex.size(rank);
        int R = (int) Math.ceil(s.ringRadius());
        int half = (int) Math.ceil(s.padRadius()), top = GateVortex.coreTop(rank) + 3;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int a = -R; a <= R; a++) {
            for (int b = -R; b <= R; b++) {
                if (a * a + b * b > (R + 0.5) * (R + 0.5)) continue;
                boolean tall = Math.abs(a) <= half && Math.abs(b) <= 3;
                int y1 = tall ? top : 6;
                BlockPos col = at(pos, axis, a, 0, b);
                if (!level.hasChunkAt(col)) continue;
                for (int y = -3; y <= y1; y++) {
                    m.set(col.getX(), col.getY() + y, col.getZ());
                    if (built(level, m)) return false;
                }
            }
        }
        return true;
    }

    private static boolean built(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        // game-master blocks (structure and test blocks) are nobody's base
        if (s.getBlock() instanceof net.minecraft.world.level.block.GameMasterBlock) return false;
        if (level.getBlockEntity(p) != null) return !(level.getBlockEntity(p) instanceof GateVortexBlockEntity);
        if (groundStep(level, p, s)) return false;
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.BEDS) || s.is(BlockTags.DOORS)
                || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.FENCES) || s.is(BlockTags.FENCE_GATES)
                || s.is(BlockTags.WALLS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.WOOL_CARPETS)
                || s.is(net.neoforged.neoforge.common.Tags.BlockItems.GLASS_BLOCKS.block()) || s.is(Blocks.BRICKS)
                || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.LANTERN) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH)
                || s.is(BlockTags.RAILS) || s.is(Blocks.CRAFTING_TABLE);
    }

    /** The nearest spot within 24 blocks of {@code center} (same axis) that is good dry ground nobody built on. */
    public static Optional<BlockPos> clearSpotNear(ServerLevel level, BlockPos center, int axis) {
        for (int r = 0; r <= 24; r += 8) {
            for (int i = 0; i < (r == 0 ? 1 : 8); i++) {
                double ang = Math.PI / 4 * i;
                int x = center.getX() + (int) Math.round(Math.cos(ang) * r), z = center.getZ() + (int) Math.round(Math.sin(ang) * r);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                BlockPos ground = new BlockPos(x, groundY(level, x, z), z);
                if (goodGround(level, ground) && untouched(level, ground, axis)) return Optional.of(ground);
            }
        }
        return Optional.empty();
    }

    /** A good spot: dry, solid, not a tree top. A soft step's half slab counts as the ground it lies on. */
    public static boolean goodGround(ServerLevel level, BlockPos ground) {
        BlockState s = level.getBlockState(ground);
        if (!s.getFluidState().isEmpty() || s.isAir()) return false;
        if (!level.getBlockState(ground.above()).getFluidState().isEmpty()) return false;
        if (groundStep(level, ground, s)) return level.getBlockState(ground.below()).isFaceSturdy(level, ground.below(), Direction.UP);
        return s.isFaceSturdy(level, ground, Direction.UP);
    }

    /**
     * A half slab of ground lying on ground: the Soft Land's step (SPEC §31.2), terrain and never a build. A bottom slab of
     * a ground material over a natural block reads as one, whoever placed it.
     */
    static boolean groundStep(ServerLevel level, BlockPos p, BlockState s) {
        return s.getBlock() instanceof SlabBlock && s.is(GROUND) && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                && natural(level.getBlockState(p.below()));
    }
}
