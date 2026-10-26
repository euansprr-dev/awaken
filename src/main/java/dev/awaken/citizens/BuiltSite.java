package dev.awaken.citizens;

import dev.awaken.citizens.api.RosterSource;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground of a settlement built in code (SPEC §28.4): what its RosterBuilder laid out, at its origin. Inside is its
 * radius; its districts are the builder's names (0 is the settlement itself); the ground's height is its street graph's
 * (the nearest node; never a chunk read); water is the cells the builder gave (a well); every house with a door can
 * burn and every street can take a cart.
 */
final class BuiltSite implements Site {
    private final int cx, cz, baseY, radius;
    private final String name;
    private final StreetNet net;
    private final List<String> districts;
    private final List<RosterSource.Lot> lots;
    private final LongOpenHashSet water = new LongOpenHashSet();
    private final java.util.Map<String, List<Lot>> byTag = new java.util.HashMap<>();
    /** Each node's district: the nearest district-tagged place's, filled once from the roster. */
    private final int[] nodeDistrict;

    BuiltSite(String name, int cx, int baseY, int cz, int radius, RosterSource.Built built, Roster r, StreetNet net) {
        this.name = name;
        this.cx = cx;
        this.cz = cz;
        this.baseY = baseY;
        this.radius = radius;
        this.net = net;
        this.districts = built.districts().isEmpty() ? List.of("") : built.districts();
        this.lots = built.lots();
        for (int[] w : built.water()) water.add(key(cx + w[0], cz + w[1]));
        nodeDistrict = new int[net.n];
        for (int p = 0; p < r.places; p++) {
            int node = r.pNode[p];
            if (node >= 0 && node < net.n && r.pDistrict[p] > 0) nodeDistrict[node] = r.pDistrict[p];
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    @Override
    public int cx() {
        return cx;
    }

    @Override
    public int cz() {
        return cz;
    }

    @Override
    public int baseY() {
        return baseY;
    }

    @Override
    public boolean carillon() {
        return false;
    }

    @Override
    public boolean inside(int x, int z) {
        long dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    @Override
    public int district(int x, int z) {
        if (!inside(x, z)) return 0;
        int n = net.nearest(x + 0.5, z + 0.5, false);
        return n < 0 ? 0 : nodeDistrict[n];
    }

    @Override
    public int districts() {
        return districts.size();
    }

    @Override
    public String districtName(int id) {
        String d = id > 0 && id < districts.size() ? districts.get(id) : "";
        return d.isEmpty() ? name : d;
    }

    @Override
    public int districtFor(String tag, int x, int z) {
        return district(x, z);
    }

    @Override
    public int ground(int x, int z) {
        int n = net.nearest(x + 0.5, z + 0.5, false);
        return n < 0 ? baseY : net.y[n];
    }

    @Override
    public boolean walkable(int x, int z) {
        return inside(x, z) && !water.contains(key(x, z));
    }

    @Override
    public boolean water(int x, int z) {
        return water.contains(key(x, z));
    }

    @Override
    public List<Lot> lots(String tag) {
        return byTag.computeIfAbsent(tag, t -> {
            List<Lot> out = new ArrayList<>();
            for (RosterSource.Lot l : lots) if (l.tags().contains(t)) out.add(new Lot(l.id(), l.x0(), l.z0(), l.x1(), l.z1(), l.top(), l.doorX(), l.doorZ()));
            return List.copyOf(out);
        });
    }

    @Override
    public boolean tagged(String tag, double x, double z) {
        return CART.equals(tag) && inside((int) Math.floor(x), (int) Math.floor(z));
    }
}
