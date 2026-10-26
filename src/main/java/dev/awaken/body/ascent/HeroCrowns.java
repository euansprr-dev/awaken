package dev.awaken.body.ascent;

import dev.awaken.core.api.WardHooks;
import dev.awaken.terrain.hero.Blueprint;
import dev.awaken.terrain.hero.HeroPiece;
import dev.awaken.terrain.hero.HeroStructure;
import dev.awaken.terrain.hero.HeroTree;
import dev.awaken.terrain.hero.HeroWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The hero trees' crowns (SPEC §17.16; the Wilds brief, "Hero trees"). The Elder Colossus and the Lumen Spire each get,
 * once, when their chunks first load: a crown at the very top over the trunk (the Tether Spire's crown block, keeping
 * this tree's climbs and records), a cache beside it, and a route of glowing anchor rings spiralling up the trunk to
 * where the branches begin, each ring a zip from the one below (an eighth of a turn round the trunk, 11-13 higher). On
 * the Lumen Spire the rings are its night route, a spiral of light up to the pads; on the Colossus they carry you into
 * the dome, whose limbs and masses are the swinging. The layout comes from the tree's own blueprint
 * ({@link HeroWorldgen#blueprint}), so it fits the tree exactly; nothing is placed but into air. A ward covers the
 * whole canopy from a little over the foot (a climb: a block is refused there, SPEC §17.25), and the Horizon line names
 * each crowned tree near you as a rope's climb ("Elder Colossus · needs Tether") until you have reached its crown.
 */
public final class HeroCrowns {
    private HeroCrowns() {}

    /** A tree's route, in blueprint coordinates (from the trunk's foot): the crown, its cache, the rings and their chain stubs. */
    public record Layout(BlockPos crown, @Nullable BlockPos cache, List<BlockPos> rings, List<BlockPos> stubs, List<Direction.Axis> stubAxes) {}

    /** Where a crown can stand: within this of the trunk's foot (the trunk leans a little). */
    static final int SUMMIT_REACH = 6;
    /** The first ring's height over the foot, the climb between rings, the turn between them (an eighth: a zip, not a swing). */
    static final int FIRST_COLOSSUS = 18, STEP_COLOSSUS = 13, FIRST_LUMEN = 12, STEP_LUMEN = 11;
    static final double TURN = Math.PI / 4;
    /** The turns tried for each next ring, widest first; and how far across a ring may be from the last (of the rise:
     * a zip takes under 0.45, AscentRules.ZIP_UP_SLOPE). */
    static final double[] TURNS = {Math.PI / 4, Math.PI / 6, Math.PI / 9, Math.PI / 18, 0};
    static final double ZIP_SIDE = 0.4;
    /**
     * The crown's ward: the whole canopy (this far round the crown), from this far over the tree's foot (a home under the
     * tree still builds) to a little over the crown, so a tower beside the trunk meets it as it reaches the branches. It
     * was a column of radius 9 under a canopy 24-30 wide (radii from the progression builder's draft, 2026-10-26).
     */
    static final int WARD_COLOSSUS = 32, WARD_LUMEN = 16, WARD_FROM = 10;
    /** The Horizon line names a crowned tree within this many blocks. */
    static final int HORIZON_REACH = 640;
    static final ResourceKey<LootTable> CACHE = ResourceKey.create(Registries.LOOT_TABLE, dev.awaken.Awaken.id("chests/precursor_spire"));

    static boolean crowned(HeroTree tree) {
        return tree == HeroTree.ELDER_COLOSSUS || tree == HeroTree.LUMEN_SPIRE;
    }

    static boolean wood(byte c) {
        return c == Blueprint.LOG || c == Blueprint.LOG_X || c == Blueprint.LOG_Z || c == Blueprint.WOOD;
    }

    /** The route for a tree from its blueprint ({@code seed} turns the spiral's start). */
    public static Layout layout(HeroTree tree, Blueprint bp, long seed) {
        // the summit: the highest cell over the trunk
        int sx = 0, sz = 0, top = Integer.MIN_VALUE;
        for (int x = -SUMMIT_REACH; x <= SUMMIT_REACH; x++) for (int z = -SUMMIT_REACH; z <= SUMMIT_REACH; z++) {
            if (x * x + z * z > SUMMIT_REACH * SUMMIT_REACH) continue;
            int t = topOf(bp, x, z);
            if (t > top) {
                top = t;
                sx = x;
                sz = z;
            }
        }
        BlockPos crown = new BlockPos(sx, top + 1, sz);
        // the cache: a neighbour on the same top (or one lower), beside the crown
        BlockPos cache = null;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            int t = topOf(bp, sx + d[0], sz + d[1]);
            if (t >= top - 1 && t <= top) {
                cache = new BlockPos(sx + d[0], t + 1, sz + d[1]);
                break;
            }
        }
        // the rings: an eighth of a turn round the trunk each, out past its bark, until the branches begin
        boolean colossus = tree == HeroTree.ELDER_COLOSSUS;
        int first = colossus ? FIRST_COLOSSUS : FIRST_LUMEN, step = colossus ? STEP_COLOSSUS : STEP_LUMEN;
        double a0 = (Math.floorMod(seed, 8)) * TURN;
        List<BlockPos> rings = new ArrayList<>();
        List<BlockPos> stubs = new ArrayList<>();
        List<Direction.Axis> axes = new ArrayList<>();
        double ax = 0, az = 0, ang = a0;
        BlockPos last = null;
        int y = first;
        while (y < top - 4) {
            // the widest turn round the trunk (an eighth at most) that keeps this ring a zip from the last and has room
            // round it; past a tier or a branch, a few blocks higher
            int[] found = null;
            double fx = 0, fz = 0, turned = 0;
            int at = y;
            for (int lift = 0; lift <= 6 && found == null; lift += 2) {
                at = y + lift;
                double[] c = axis(bp, at, ax, az);
                if (c == null) break;
                for (double turn : TURNS) {
                    double a = last == null ? ang : ang + turn, cx = Math.cos(a), cz = Math.sin(a);
                    int[] r = ringOut(bp, at, c[0], c[1], cx, cz);
                    double rise = last == null ? at : at - last.getY();
                    if (last != null && Math.hypot(r[0] - last.getX(), r[1] - last.getZ()) >= rise * ZIP_SIDE) continue;
                    if (!room(bp, at, r)) continue;
                    found = r;
                    fx = cx;
                    fz = cz;
                    turned = last == null ? 0 : turn;
                    ax = c[0];
                    az = c[1];
                    break;
                }
            }
            if (found == null) break;
            ang += turned;
            last = new BlockPos(found[0], at, found[1]);
            rings.add(last);
            if (bp.get(found[2], at, found[3]) == Blueprint.EMPTY && (found[2] != found[0] || found[3] != found[1])) {
                stubs.add(new BlockPos(found[2], at, found[3]));
                axes.add(Math.abs(fx) >= Math.abs(fz) ? Direction.Axis.X : Direction.Axis.Z);
            }
            y = at + step;
        }
        return new Layout(crown, cache, rings, stubs, axes);
    }

    /** Out from the axis along (ux, uz) past the bark: the ring's cell (2 out) and its stub's (just off the bark). */
    private static int[] ringOut(Blueprint bp, int y, double ax, double az, double ux, double uz) {
        double surf = 0;
        while (surf < 12 && wood(bp.get((int) Math.floor(ax + ux * surf + 0.5), y, (int) Math.floor(az + uz * surf + 0.5)))) surf += 0.5;
        return new int[]{(int) Math.floor(ax + ux * (surf + 2.0) + 0.5), (int) Math.floor(az + uz * (surf + 2.0) + 0.5),
                (int) Math.floor(ax + ux * (surf + 0.9) + 0.5), (int) Math.floor(az + uz * (surf + 0.9) + 0.5)};
    }

    /** Room for a ring: the ring, two to hang under it, three over it for a zip's arrival, and its stub in air or bark. */
    private static boolean room(Blueprint bp, int y, int[] r) {
        for (int dy = -2; dy <= 3; dy++) if (bp.get(r[0], y + dy, r[1]) != Blueprint.EMPTY) return false;
        byte stub = bp.get(r[2], y, r[3]);
        return stub == Blueprint.EMPTY || wood(stub);
    }

    private static int topOf(Blueprint bp, int x, int z) {
        if (!bp.inside(x, bp.minY, z)) return Integer.MIN_VALUE;
        for (int y = bp.maxY; y >= bp.minY; y--) if (bp.get(x, y, z) != Blueprint.EMPTY) return y;
        return Integer.MIN_VALUE;
    }

    /** The middle of the wood at height {@code y} within 9 of (px, pz), or null when there is none. */
    private static double @Nullable [] axis(Blueprint bp, int y, double px, double pz) {
        double sx = 0, sz = 0;
        int n = 0;
        int x0 = (int) Math.floor(px) - 9, z0 = (int) Math.floor(pz) - 9;
        for (int x = x0; x <= x0 + 18; x++) for (int z = z0; z <= z0 + 18; z++) {
            double dx = x - px, dz = z - pz;
            if (dx * dx + dz * dz > 81 || !wood(bp.get(x, y, z))) continue;
            sx += x;
            sz += z;
            n++;
        }
        return n == 0 ? null : new double[]{sx / n, sz / n};
    }

    // ---- placing ------------------------------------------------------------------------------------------------------

    private record Pending(HeroTree tree, BlockPos centre, long seed) {}

    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    private static final Map<Long, BoundingBox> WARDS = new ConcurrentHashMap<>();
    /** Which tree each warded crown crowns ({@link SpireCrownBlockEntity#SITE_COLOSSUS} or {@code SITE_LUMEN}). */
    private static final Map<Long, Integer> TREES = new ConcurrentHashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(HeroCrowns::onChunkLoad);
        bus.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> tick(e.getServer()));
        WardHooks.register((level, pos, out) -> {
            if (level.dimension() != Level.OVERWORLD || WARDS.isEmpty()) return;
            for (var w : WARDS.entrySet()) {
                if (w.getValue().isInside(pos)) out.accept(new WardHooks.Ward("hero/" + w.getKey(), "hero_crown", w.getValue()));
            }
        });
        dev.awaken.core.api.HorizonHooks.register(HeroCrowns::horizon);
    }

    /**
     * The Horizon's crowned trees near a player (the ones loaded: their crowns ward and tick only then): each a rope's
     * climb, until this player has reached its crown. The id is the crown's promise key ("landmark:hero/<pos>").
     */
    static List<dev.awaken.core.api.HorizonHooks.Site> horizon(net.minecraft.server.level.ServerPlayer p) {
        if (WARDS.isEmpty() || p.level().dimension() != Level.OVERWORLD) return List.of();
        List<dev.awaken.core.api.HorizonHooks.Site> out = new ArrayList<>(2);
        for (var w : WARDS.entrySet()) {
            BlockPos crown = BlockPos.of(w.getKey());
            double dx = crown.getX() - p.getX(), dz = crown.getZ() - p.getZ();
            if (dx * dx + dz * dz > (double) HORIZON_REACH * HORIZON_REACH) continue;
            if (p.level().getBlockEntity(crown) instanceof SpireCrownBlockEntity be && be.hasClimbed(p.getUUID())) continue;
            boolean colossus = TREES.getOrDefault(w.getKey(), SpireCrownBlockEntity.SITE_COLOSSUS) == SpireCrownBlockEntity.SITE_COLOSSUS;
            out.add(new dev.awaken.core.api.HorizonHooks.Site("landmark:hero/" + w.getKey(), (colossus ? "Elder Colossus" : "Lumen Spire")
                    + " · a light in its crown", crown, dev.awaken.core.api.HorizonHooks.TETHER, false));
        }
        return out;
    }

    /** ChunkEvent.Load: a crowned hero tree starting here is queued (rigged on a later tick, its crown's chunks loaded). */
    static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        var starts = e.getChunk().getAllStarts();
        if (starts.isEmpty()) return;
        for (var s : starts.entrySet()) {
            if (!(s.getKey() instanceof HeroStructure hs) || !crowned(hs.tree())) continue;
            StructureStart start = s.getValue();
            if (!start.isValid()) continue;
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof HeroPiece hp) PENDING.add(new Pending(hp.tree(), hp.centre(), hp.seed()));
            }
        }
    }

    /** A few a tick: rig the queued trees whose crown and rings are loaded (once: the crown's there after). */
    static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        ServerLevel level = server.overworld();
        for (int n = PENDING.size(); n > 0; n--) {
            Pending p = PENDING.poll();
            if (p == null) return;
            Layout l = layout(p.tree(), HeroWorldgen.blueprint(p.tree(), p.seed()), p.seed());
            BlockPos crown = p.centre().offset(l.crown());
            boolean loaded = level.isLoaded(crown) && level.isLoaded(p.centre());
            for (BlockPos r : l.rings()) loaded &= level.isLoaded(p.centre().offset(r));
            if (!loaded) {
                PENDING.add(p); // later
                return;
            }
            if (!level.getBlockState(crown).is(AscentContent.SPIRE_CROWN.get())) rig(level, p.tree(), p.centre(), p.seed(), l);
        }
    }

    /** Places a tree's route and crown (into air only). */
    public static void rig(ServerLevel level, HeroTree tree, BlockPos centre, long seed, Layout l) {
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
        BlockState ring = AscentContent.ANCHOR_RING.get().defaultBlockState();
        int rings = 0;
        for (BlockPos r : l.rings()) {
            BlockPos w = centre.offset(r);
            if (level.getBlockState(w).isAir()) {
                level.setBlock(w, ring, flags);
                rings++;
            }
        }
        for (int i = 0; i < l.stubs().size(); i++) {
            BlockPos w = centre.offset(l.stubs().get(i));
            if (level.getBlockState(w).isAir()) {
                level.setBlock(w, Blocks.IRON_CHAIN.defaultBlockState().setValue(BlockStateProperties.AXIS, l.stubAxes().get(i)), flags);
            }
        }
        BlockPos crown = centre.offset(l.crown());
        if (level.getBlockState(crown).canBeReplaced()) {
            level.setBlock(crown, AscentContent.SPIRE_CROWN.get().defaultBlockState(), flags);
            if (level.getBlockEntity(crown) instanceof SpireCrownBlockEntity be) {
                be.site(tree == HeroTree.ELDER_COLOSSUS ? SpireCrownBlockEntity.SITE_COLOSSUS : SpireCrownBlockEntity.SITE_LUMEN, centre.getY());
            }
        }
        if (l.cache() != null) {
            BlockPos c = centre.offset(l.cache());
            if (level.getBlockState(c).canBeReplaced()) {
                level.setBlock(c, Blocks.CHEST.defaultBlockState(), flags);
                RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), c, CACHE);
            }
        }
        dev.awaken.Awaken.LOG.info("[ascent] {} at {} crowned: the crown at {}, {} rings up the trunk", tree.getSerializedName(),
                centre.toShortString(), crown.toShortString(), rings);
    }

    /** The crown's ward (its block entity calls this as it loads, so wards come back after a restart). */
    static void ward(BlockPos crown, int ground, int site) {
        int r = site == SpireCrownBlockEntity.SITE_LUMEN ? WARD_LUMEN : WARD_COLOSSUS;
        WARDS.put(crown.asLong(), new BoundingBox(crown.getX() - r, ground + WARD_FROM, crown.getZ() - r,
                crown.getX() + r, crown.getY() + 6, crown.getZ() + r));
        TREES.put(crown.asLong(), site);
    }

    static void unward(BlockPos crown) {
        WARDS.remove(crown.asLong());
        TREES.remove(crown.asLong());
    }
}
