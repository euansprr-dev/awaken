package dev.awaken.village.world;

import dev.awaken.village.plan.VillageBlocks;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A mining town's workings under the ground (SPEC §28.9): the shaft lined with stone and timber cribs, a ladder down its
 * town side and a roped piton's line down another wall, the cage at its foot; the first level's gallery, three wide and
 * four high, timber sets flush in its walls every four with a lantern on every other, rails down its middle; the landing
 * with its barrels, a table and a cart on the rails; the winze down to the second level, its ladder on a side wall; the
 * second level's gallery to its face: W2's lode where the mine was driven to one (never cut: the ore stays in the walls),
 * else a seam of the land's ore; behind the face, sealed, the cavern with the Hollows' nest the Deep Mine breaks into.
 * Wet rock round any of it is walled off. Self-contained: it keeps the mine's numbers, so after a restart it paints
 * without the town being planned again.
 */
public class MinePiece extends StructurePiece implements net.neoforged.neoforge.common.world.PieceBeardifierModifier {
    /** The plan's origin and street level, its land and seed, and the mine (see {@link VillagePlan.Mine}). */
    private final int ox, oz, baseY, family;
    private final long seed;
    private final VillagePlan.Mine m;

    public MinePiece(VillagePlan p, VillagePlan.Mine m) {
        super(VillageWorldgen.MINE.get(), 0, box(p.ox, p.oz, p.baseY, m));
        this.ox = p.ox;
        this.oz = p.oz;
        this.baseY = p.baseY;
        this.family = p.family;
        this.seed = p.seed;
        this.m = m;
        setOrientation(null);
    }

    public MinePiece(CompoundTag t) {
        super(VillageWorldgen.MINE.get(), t);
        ox = t.getIntOr("ox", 0);
        oz = t.getIntOr("oz", 0);
        baseY = t.getIntOr("by", 64);
        family = t.getIntOr("fam", 0);
        seed = t.getLongOr("seed", 0L);
        int[] v = t.getIntArray("mine").orElse(new int[16]);
        String ore = t.getStringOr("ore", "iron");
        m = new VillagePlan.Mine(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], ore, v[10], v[11], v[12], v[13], v[14], v[15]);
        setOrientation(null);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag t) {
        t.putInt("ox", ox);
        t.putInt("oz", oz);
        t.putInt("by", baseY);
        t.putInt("fam", family);
        t.putLong("seed", seed);
        t.putIntArray("mine", new int[]{m.x(), m.z(), m.lift(), m.side(), m.level1(), m.level2(), m.landing(), m.winze(), m.reach2(), m.side2(),
                m.oreX(), m.oreY(), m.oreZ(), m.cavernX(), m.cavernY(), m.cavernZ()});
        t.putString("ore", m.ore() == null ? "iron" : m.ore());
    }

    static final int[] SX = {0, 1, 0, -1}, SZ = {-1, 0, 1, 0};

    @Override
    public BoundingBox getBeardifierBox() {
        return boundingBox;
    }

    @Override
    public net.minecraft.world.level.levelgen.structure.TerrainAdjustment getTerrainAdjustment() {
        return net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }


    private static BoundingBox box(int ox, int oz, int baseY, VillagePlan.Mine m) {
        int x0 = m.x() - 3, x1 = m.x() + 3, z0 = m.z() - 3, z1 = m.z() + 3;
        int[] w = m.along(m.winze() + 2), c = {m.cavernX(), m.cavernZ()};
        int[] f = m.along2(m.reach2() + 2);
        for (int[] q : new int[][]{w, c, f}) {
            x0 = Math.min(x0, q[0] - 8);
            x1 = Math.max(x1, q[0] + 8);
            z0 = Math.min(z0, q[1] - 8);
            z1 = Math.max(z1, q[1] + 8);
        }
        return new BoundingBox(ox + x0, baseY + m.cavernY() - 8, oz + z0, ox + x1, baseY + m.lift() + 1, oz + z1);
    }

    // ---- painting ------------------------------------------------------------------------------------------------------

    private transient List<dev.awaken.delve.Lode> lodes;

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        Paint p = new Paint(level, chunkBB);
        if (lodes == null) {
            List<dev.awaken.delve.Lode> ls = new ArrayList<>();
            BoundingBox b = boundingBox;
            for (int rx = Math.floorDiv(b.minX() - 48, dev.awaken.delve.LodePlans.REGION); rx <= Math.floorDiv(b.maxX() + 48, dev.awaken.delve.LodePlans.REGION); rx++)
                for (int rz = Math.floorDiv(b.minZ() - 48, dev.awaken.delve.LodePlans.REGION); rz <= Math.floorDiv(b.maxZ() + 48, dev.awaken.delve.LodePlans.REGION); rz++)
                    for (dev.awaken.delve.Lode l : dev.awaken.delve.LodePlans.region(level.getSeed(), rx, rz))
                        if (l.touches(b.minX(), b.minZ(), b.maxX(), b.maxZ())) ls.add(l);
            lodes = ls;
        }
        int g = baseY + m.lift(), y1 = baseY + m.level1(), y2 = baseY + m.level2();
        String w = wood();
        BlockState log = VillageBlocks.of("minecraft:stripped_" + w + "_log[axis=y]"), planks = VillageBlocks.of("minecraft:" + w + "_planks");
        int back = (m.side() + 2) & 3, cross = (m.side() + 1) & 3;
        // ---- the shaft
        int hx = ox + m.x(), hz = oz + m.z();
        for (int y = y1; y <= g - 1; y++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                    int x = hx + dx, z = hz + dz;
                    if (inner) p.set(x, y, z, y == y1 ? planks : AIR);
                    else p.set(x, y, z, (g - y) % 4 == 0 ? VillageBlocks.of("minecraft:stripped_" + w + "_log[axis=" + (Math.abs(dx) == 2 ? "z" : "x") + "]")
                            : pick(x, y, z, LINING));
                }
            }
        }
        // the ladder on the town side, the roped piton and its line on the next wall, the cage's posts at the foot
        int lx = hx + SX[back], lz = hz + SZ[back];
        for (int y = y1 + 1; y <= g; y++) p.set(lx, y, lz, LADDER[m.side()]);
        int rx = hx + SX[cross], rz = hz + SZ[cross];
        p.set(rx, g - 1, rz, PITON[(cross + 2) & 3]);
        for (int y = y1 + 1; y <= g - 2; y++) p.set(rx, y, rz, RUNNER_LINE);
        for (int y = y1 + 4; y <= g - 1; y++) p.set(hx, y, hz, CHAIN);
        int ox2 = hx - SX[cross], oz2 = hz - SZ[cross];
        p.set(ox2 + SX[m.side()], y1 + 1, oz2 + SZ[m.side()], VillageBlocks.of("minecraft:" + w + "_fence"));
        p.set(ox2 + SX[back], y1 + 1, oz2 + SZ[back], VillageBlocks.of("minecraft:" + w + "_fence"));
        // ---- the first level: the gallery, the landing, the winze
        gallery(p, hx, hz, m.side(), y1, 2, m.winze() - 2, true, w);
        int[] land = m.along(m.landing());
        room(p, ox + land[0], oz + land[1], m.side(), y1, w, true);
        int[] wz = m.along(m.winze());
        int wx = ox + wz[0], wzz = oz + wz[1];
        int ladderSide = m.side2() == cross ? (cross + 2) & 3 : cross;
        for (int y = y2; y <= y1 + 4; y++) {
            for (int dz = -2; dz <= 2; dz++)
                for (int dx = -2; dx <= 2; dx++) {
                    boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                    int x = wx + dx, z = wzz + dz;
                    if (inner) p.set(x, y, z, y == y2 ? planks : AIR);
                    else if (y < y1) p.set(x, y, z, (y1 - y) % 4 == 0 ? planks : pick(x, y, z, LINING));
                }
        }
        int wlx = wx + SX[ladderSide], wlz = wzz + SZ[ladderSide];
        for (int y = y2 + 1; y <= y1 + 1; y++) p.set(wlx, y, wlz, LADDER[(ladderSide + 2) & 3]);
        p.set(wx, y1 + 4, wzz, LANTERN_HANGING);
        // ---- the second level to the face
        gallery(p, wx, wzz, m.side2(), y2, 2, m.reach2(), true, w);
        int[] mid = m.along2(Math.max(4, m.reach2() / 2));
        p.set(ox + mid[0] + SX[(m.side2() + 1) & 3] * 1, y2 + 1, oz + mid[1] + SZ[(m.side2() + 1) & 3] * 1, BARREL);
        face(p, w);
        cavern(p);
        // the carts: one at the landing, one on the second level
        cart(level, chunkBB, ox + land[0], y1 + 1, oz + land[1]);
        int[] c2 = m.along2(3);
        cart(level, chunkBB, ox + c2[0], y2 + 1, oz + c2[1]);
        p.seal();
    }

    private String wood() {
        return switch (family) {
            case dev.awaken.city.VillageStyles.FROST, dev.awaken.city.VillageStyles.HIGHLAND, dev.awaken.city.VillageStyles.COAST -> "spruce";
            case dev.awaken.city.VillageStyles.DESERT, dev.awaken.city.VillageStyles.SAVANNA -> "acacia";
            case dev.awaken.city.VillageStyles.MIRE -> "mangrove";
            case dev.awaken.city.VillageStyles.ELDER -> "dark_oak";
            case dev.awaken.city.VillageStyles.JUNGLE -> "jungle";
            default -> "oak";
        };
    }

    /**
     * A gallery from (x, z) along {@code side}: three wide and four high from d0 to d1, its floor at y; a timber set every
     * four (posts flush in the walls, a cap across the roof), a lantern hung from every other cap, rails down the middle.
     */
    private void gallery(Paint p, int x, int z, int side, int y, int d0, int d1, boolean rails, String w) {
        int cx = SX[(side + 1) & 3], cz = SZ[(side + 1) & 3];
        BlockState post = VillageBlocks.of("minecraft:stripped_" + w + "_log[axis=y]");
        BlockState cap = VillageBlocks.of("minecraft:stripped_" + w + "_log[axis=" + (cx != 0 ? "x" : "z") + "]");
        BlockState rail = VillageBlocks.of("minecraft:rail[shape=" + (SX[side] != 0 ? "east_west" : "north_south") + "]");
        for (int d = d0; d <= d1; d++) {
            int px = x + SX[side] * d, pz = z + SZ[side] * d;
            boolean set = Math.floorMod(d, 4) == 0;
            for (int c = -2; c <= 2; c++) {
                int qx = px + cx * c, qz = pz + cz * c;
                if (Math.abs(c) == 2) {
                    if (!set) continue;
                    for (int k = 1; k <= 3; k++) p.set(qx, y + k, qz, post);
                    p.set(qx, y + 4, qz, cap);
                    continue;
                }
                p.floor(qx, y, qz);
                for (int k = 1; k <= 4; k++) p.set(qx, y + k, qz, set && k == 4 ? cap : AIR);
            }
            if (rails) p.set(px, y + 1, pz, rail);
            if (set && Math.floorMod(d, 8) == 4) p.set(px, y + 3, pz, LANTERN_HANGING);
        }
    }

    /** The landing: a chamber seven square and four high off the gallery, its barrels, crates, a table and a lantern. */
    private void room(Paint p, int x, int z, int side, int y, String w, boolean rails) {
        BlockState planks = VillageBlocks.of("minecraft:" + w + "_planks");
        BlockState post = VillageBlocks.of("minecraft:stripped_" + w + "_log[axis=y]");
        for (int dz = -3; dz <= 3; dz++)
            for (int dx = -3; dx <= 3; dx++) {
                int qx = x + dx, qz = z + dz;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                p.set(qx, y, qz, planks);
                for (int k = 1; k <= 4; k++) p.set(qx, y + k, qz, corner ? post : AIR);
            }
        p.set(x, y + 4, z, LANTERN_HANGING);
        int cxs = SX[(side + 1) & 3], czs = SZ[(side + 1) & 3];
        // barrels and crates along one wall, a table and a seat along the other
        for (int k = -2; k <= 2; k += 2) p.set(x + cxs * 3 + SX[side] * k, y + 1, z + czs * 3 + SZ[side] * k, k == 0 ? CRAFTING : BARREL);
        p.set(x + cxs * 3 + SX[side] * -2, y + 2, z + czs * 3 + SZ[side] * -2, BARREL);
        p.set(x - cxs * 3, y + 1, z - czs * 3, VillageBlocks.of("minecraft:" + w + "_fence"));
        p.set(x - cxs * 3, y + 2, z - czs * 3, PRESSURE_PLATE);
        p.set(x - cxs * 2, y + 1, z - czs * 2, VillageBlocks.of("minecraft:" + w + "_stairs[facing=" + DIR[(side + 3 + 2) & 3] + ",half=bottom,shape=straight]"));
        // the rails run on through the landing
        if (rails) {
            BlockState rail = VillageBlocks.of("minecraft:rail[shape=" + (SX[side] != 0 ? "east_west" : "north_south") + "]");
            for (int k = -3; k <= 3; k++) p.set(x + SX[side] * k, y + 1, z + SZ[side] * k, rail);
        }
    }

    static final String[] DIR = {"north", "east", "south", "west"};

    /** The face: a seam of the land's ore where the mine was driven to none of W2's lodes (a real lode needs nothing). */
    private void face(Paint p, String w) {
        int[] f = m.along2(m.reach2() + 1);
        int y2 = baseY + m.level2();
        if (lodeNear(ox + f[0], y2 + 2, oz + f[1])) return;
        Block ore = switch (m.ore() == null ? "iron" : m.ore()) {
            case "copper" -> y2 < 0 ? net.minecraft.world.level.block.Blocks.DEEPSLATE_COPPER_ORE : net.minecraft.world.level.block.Blocks.COPPER_ORE;
            case "coal" -> y2 < 0 ? net.minecraft.world.level.block.Blocks.DEEPSLATE_COAL_ORE : net.minecraft.world.level.block.Blocks.COAL_ORE;
            case "gold" -> y2 < 0 ? net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE : net.minecraft.world.level.block.Blocks.GOLD_ORE;
            default -> y2 < 0 ? net.minecraft.world.level.block.Blocks.DEEPSLATE_IRON_ORE : net.minecraft.world.level.block.Blocks.IRON_ORE;
        };
        int cx = SX[(m.side2() + 1) & 3], cz = SZ[(m.side2() + 1) & 3];
        for (int c = -2; c <= 2; c++)
            for (int k = 0; k <= 4; k++) {
                int x = ox + f[0] + cx * c, z = oz + f[1] + cz * c;
                if (hash(x, y2 + k, z) % 100 < (Math.abs(c) <= 1 && k >= 1 && k <= 3 ? 70 : 30)) p.setSolid(x, y2 + k, z, ore.defaultBlockState());
            }
    }

    /**
     * The cavern behind the face (the Deep Mine's): an uneven hollow eight across and five high, a rock wall of at least a
     * block between it and the gallery's end, its floor strewn with bone and its roof with cobwebs and dripstone: a Hollows'
     * nest, sealed until the miners break into it.
     */
    private void cavern(Paint p) {
        int cx = ox + m.cavernX(), cy = baseY + m.cavernY(), cz = oz + m.cavernZ();
        int[] end = m.along2(m.reach2());
        int ex = ox + end[0], ez = oz + end[1], ey = baseY + m.level2() + 2;
        for (int dy = -4; dy <= 5; dy++)
            for (int dz = -7; dz <= 7; dz++)
                for (int dx = -7; dx <= 7; dx++) {
                    double n = (hash(cx + dx, cy + dy, cz + dz) % 100) / 100.0;
                    double e = (dx * dx) / 36.0 + (dz * dz) / 36.0 + (dy * dy) / (dy < 0 ? 9.0 : 16.0);
                    if (e > 1.0 + n * 0.25) continue;
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    // never into the gallery: a wall between
                    if (Math.abs(x - ex) + Math.abs(z - ez) + Math.abs(y - ey) * 0.5 < 3) continue;
                    p.set(x, y, z, AIR);
                }
        // the nest: bone and cobweb on the floor and the roof
        for (int k = 0; k < 26; k++) {
            int h = hash(cx, k, cz);
            int dx = h % 11 - 5, dz = (h / 11) % 11 - 5;
            int x = cx + dx, z = cz + dz;
            int fy = cy - 4;
            while (fy < cy + 4 && !p.air(x, fy + 1, z)) fy++;
            if (fy >= cy + 4) continue;
            if (k % 3 == 0) p.set(x, fy + 1, z, COBWEB);
            else if (k % 3 == 1) p.setIfAir(x, fy + 1, z, BONE);
            else {
                int ry = cy + 5;
                while (ry > cy && p.air(x, ry, z)) ry--;
                p.setIfAir(x, ry - 1, z, DRIP);
            }
        }
    }

    private boolean lodeNear(int x, int y, int z) {
        for (dev.awaken.delve.Lode l : lodes) {
            for (int dz = -2; dz <= 2; dz++)
                for (int dx = -2; dx <= 2; dx++)
                    for (int dy = -1; dy <= 2; dy++) if (l.holds(BlockPos.asLong(x + dx, y + dy, z + dz))) return true;
        }
        return false;
    }

    private static void cart(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z) {
        if (!chunkBB.isInside(x, y, z)) return;
        var cart = EntityTypes.MINECART.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (cart == null) return;
        cart.snapTo(x + 0.5, y, z + 0.5, 0, 0);
        level.addFreshEntity(cart);
    }

    static int hash(int x, int y, int z) {
        long h = x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 31)) * 0xbf58476d1ce4e5b9L;
        return (int) Math.floorMod(h ^ (h >>> 29), 100_000L);
    }

    BlockState pick(int x, int y, int z, BlockState[] from) {
        return from[hash(x, y, z) % from.length];
    }

    /** Writes inside the chunk being painted, never through a lode's ore; what it opens is walled off from water and lava. */
    private final class Paint {
        final WorldGenLevel level;
        final BoundingBox bb;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final it.unimi.dsi.fastutil.longs.LongArrayList opened = new it.unimi.dsi.fastutil.longs.LongArrayList();

        Paint(WorldGenLevel level, BoundingBox bb) {
            this.level = level;
            this.bb = bb;
        }

        boolean lode(int x, int y, int z) {
            long k = BlockPos.asLong(x, y, z);
            for (dev.awaken.delve.Lode l : lodes) if (l.holds(k)) return true;
            return false;
        }

        void set(int x, int y, int z, BlockState s) {
            if (!bb.isInside(x, y, z) || lode(x, y, z)) return;
            level.setBlock(pos.set(x, y, z), s, Block.UPDATE_CLIENTS);
            if (s.isAir() || !s.isSolid()) opened.add(BlockPos.asLong(x, y, z));
        }

        void setSolid(int x, int y, int z, BlockState s) {
            if (!bb.isInside(x, y, z) || lode(x, y, z)) return;
            level.setBlock(pos.set(x, y, z), s, Block.UPDATE_CLIENTS);
        }

        void setIfAir(int x, int y, int z, BlockState s) {
            if (bb.isInside(x, y, z) && level.getBlockState(pos.set(x, y, z)).isAir()) level.setBlock(pos, s, Block.UPDATE_CLIENTS);
        }

        boolean air(int x, int y, int z) {
            return bb.isInside(x, y, z) && level.getBlockState(pos.set(x, y, z)).isAir();
        }

        /** A gallery's floor: the rock where it's sound, rubble where the floor would be air, water or soft ground. */
        void floor(int x, int y, int z) {
            if (!bb.isInside(x, y, z) || lode(x, y, z)) return;
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (!s.isSolid() || !s.getFluidState().isEmpty() || s.is(net.minecraft.tags.BlockTags.SAND) || s.is(net.minecraft.world.level.block.Blocks.GRAVEL))
                level.setBlock(pos, pick(x, y, z, LINING), Block.UPDATE_CLIENTS);
        }

        /** Water or lava touching what was opened is walled off with the land's stone. */
        void seal() {
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            for (int i = 0; i < opened.size(); i++) {
                long k = opened.getLong(i);
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    q.set(BlockPos.getX(k) + d.getStepX(), BlockPos.getY(k) + d.getStepY(), BlockPos.getZ(k) + d.getStepZ());
                    if (!bb.isInside(q) || level.getFluidState(q).isEmpty()) continue;
                    level.setBlock(q, q.getY() < 0 ? DEEPSLATE : STONE, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    static final BlockState AIR = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    static final BlockState STONE = VillageBlocks.of("minecraft:stone"), DEEPSLATE = VillageBlocks.of("minecraft:cobbled_deepslate");
    static final BlockState[] LINING = {VillageBlocks.of("minecraft:cobblestone"), VillageBlocks.of("minecraft:stone_bricks"),
            VillageBlocks.of("minecraft:mossy_cobblestone"), VillageBlocks.of("minecraft:andesite"), VillageBlocks.of("minecraft:cobblestone")};
    static final BlockState[] LADDER = VillageBlocks.dirs("minecraft:ladder[facing=%s]");
    static final BlockState[] PITON = VillageBlocks.dirs("awaken:piton[facing=%s,roped=true]");
    static final BlockState RUNNER_LINE = VillageBlocks.of("awaken:runner_line"), CHAIN = VillageBlocks.of("minecraft:iron_chain[axis=y]"),
            LANTERN_HANGING = VillageBlocks.of("minecraft:lantern[hanging=true]"), BARREL = VillageBlocks.of("minecraft:barrel[facing=up]"),
            CRAFTING = VillageBlocks.of("minecraft:crafting_table"), PRESSURE_PLATE = VillageBlocks.of("minecraft:stone_pressure_plate"),
            COBWEB = VillageBlocks.of("minecraft:cobweb"), BONE = VillageBlocks.of("minecraft:bone_block[axis=y]"),
            DRIP = VillageBlocks.of("minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]");
}
