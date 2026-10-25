package dev.awaken.city;

import dev.awaken.terrain.api.CitySite;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The plan placed at one site: where its centre is in the world, its street height, and which pieces touch each chunk
 * (built once per server start; immutable after, so any worldgen thread may read it).
 *
 * <p>Pieces are numbered: plots first, then landmark parts, then trees, then lamps, then the street-life props. Each is
 * listed in every chunk its box plus a margin touches (3 for plots, whose sign brackets reach three out), in that order,
 * so every chunk paints them in the same order.
 */
public final class CityLayout {
    public final CityPlan plan;
    public final CitySite site;
    /** World x, z of plan (0, 0). */
    public final int cx, cz;
    public final int streetY;
    /** Whether terrain flattened the basin (else the painter carries its ground down to rock itself). */
    public final boolean carved;
    final int nPlots, nParts, nTrees, nLamps, nProps;
    private final Long2ObjectOpenHashMap<int[]> byChunk = new Long2ObjectOpenHashMap<>();
    /** Every chunk the city writes into (inside the wall plus the fields and roads round it). */
    public final long[] chunks;
    static final int MARGIN = 2;
    /** How far out a landmark part paints: the wall's towers and gatehouses reach four (galleries, eaves, bartizans). */
    static int margin(CityPlan.Part p) {
        return switch (p.kind()) {
            case "tower", "gate_tower", "gate_passage" -> CityTowers.MARGIN;
            default -> MARGIN;
        };
    }

    /** Plots paint up to three out from their walls (B3a: a sign bracket's end over the street). */
    static final int PLOT_MARGIN = 3;
    /** How far out the painter reaches (the fields end at 318; the roads run to the basin's edge). */
    public final int reach;

    public CityLayout(CityPlan plan, CitySite site) {
        this.plan = plan;
        this.site = site;
        this.cx = site.x();
        this.cz = site.z();
        this.streetY = site.streetY();
        this.carved = site.basinCarved();
        this.reach = carved ? Math.min(site.coreRadius() - 4, plan.fieldsTo + 8) : 262;
        nPlots = plan.plots.size();
        nParts = plan.parts.size();
        nTrees = plan.trees.size();
        nLamps = plan.lamps.size();
        nProps = plan.props.size();
        Long2ObjectOpenHashMap<List<Integer>> lists = new Long2ObjectOpenHashMap<>();
        int id = 0;
        for (CityPlan.Plot p : plan.plots) index(lists, id++, p.x0() - PLOT_MARGIN, p.z0() - PLOT_MARGIN, p.x1() + PLOT_MARGIN, p.z1() + PLOT_MARGIN);
        for (CityPlan.Part p : plan.parts) index(lists, id++, p.x0() - margin(p), p.z0() - margin(p), p.x1() + margin(p), p.z1() + margin(p));
        for (int[] t : plan.trees) index(lists, id++, t[0] - 4, t[1] - 4, t[0] + 4, t[1] + 4);
        for (int[] l : plan.lamps) index(lists, id++, l[0] - 1, l[1] - 1, l[0] + 1, l[1] + 1);
        for (CityPlan.Prop p : plan.props) index(lists, id++, p.x0() - 1, p.z0() - 1, p.x1() + 1, p.z1() + 1);
        for (var e : lists.long2ObjectEntrySet()) {
            List<Integer> l = e.getValue();
            int[] a = new int[l.size()];
            for (int i = 0; i < a.length; i++) a[i] = l.get(i);
            byChunk.put(e.getLongKey(), a);
        }
        // every chunk within reach of the centre
        List<Long> cs = new ArrayList<>();
        int r = reach + 16;
        for (int x = (cx - r) >> 4; x <= (cx + r) >> 4; x++) {
            for (int z = (cz - r) >> 4; z <= (cz + r) >> 4; z++) {
                if (touches(x, z)) cs.add(ChunkPos.pack(x, z));
            }
        }
        chunks = cs.stream().mapToLong(Long::longValue).toArray();
    }

    private void index(Long2ObjectOpenHashMap<List<Integer>> lists, int id, int x0, int z0, int x1, int z1) {
        for (int x = (cx + x0) >> 4; x <= (cx + x1) >> 4; x++) {
            for (int z = (cz + z0) >> 4; z <= (cz + z1) >> 4; z++) {
                lists.computeIfAbsent(ChunkPos.pack(x, z), k -> new ArrayList<>()).add(id);
            }
        }
    }

    /** The pieces whose boxes (plus margin) touch this chunk, in paint order; empty when none. */
    int[] pieces(int chunkX, int chunkZ) {
        int[] a = byChunk.get(ChunkPos.pack(chunkX, chunkZ));
        return a == null ? EMPTY : a;
    }

    private static final int[] EMPTY = new int[0];

    /** Whether the city writes anything into this chunk. */
    public boolean touches(int chunkX, int chunkZ) {
        // nearest point of the chunk to the centre
        int x0 = chunkX << 4, z0 = chunkZ << 4;
        long dx = Math.max(Math.max(x0 - cx, cx - (x0 + 15)), 0), dz = Math.max(Math.max(z0 - cz, cz - (z0 + 15)), 0);
        return dx * dx + dz * dz <= (long) reach * reach;
    }

    // ---- world <-> plan ------------------------------------------------------------------------------------

    public int px(int worldX) {
        return worldX - cx;
    }

    public int pz(int worldZ) {
        return worldZ - cz;
    }

    /** Inside the wall (its own blocks included): what CityHooks.inCity means. */
    public boolean inCity(int worldX, int worldZ) {
        return plan.kind(worldX - cx, worldZ - cz) != CityPlan.VOID;
    }

    /** The district id at a column inside the wall, else 0. */
    public int district(int worldX, int worldZ) {
        return plan.district(worldX - cx, worldZ - cz);
    }

    /** The ground's top block at a column (street level plus the terrace's lift). */
    public int groundY(int worldX, int worldZ) {
        return streetY + plan.lift(worldX - cx, worldZ - cz);
    }
}
