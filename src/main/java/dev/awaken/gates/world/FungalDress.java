package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Fungal Hollow's caves (SPEC §38.10): violet rock veined with light, moss and loam underfoot, lumen roots hanging
 * from every roof, sprouts and threads on the floors and glowcaps as tall as the caverns let them be. The Glowing Deep
 * has its lake and its glowing falls, the Root Warrens hang thick with roots, the Spore Canopy is a forest of mushrooms
 * forty high, and Inside the Bloom the walls are the gills of one impossible fungus. The Mycelium Mind is threads.
 */
final class FungalDress extends Dress {
    final BlockState rock, veined, loam, moss, stem, cap, gills, roots, sprouts, threads, fall, puff;
    final boolean deep, warrens, canopy, bloom;

    FungalDress(CaveEngine.Cave c) {
        super(c);
        rock = mat("wall", "awaken:fungal_rock");
        veined = mat("wall_glow", "awaken:fungal_rock_veined");
        loam = mat("floor", "awaken:fungal_loam");
        moss = mat("floor_glow", "awaken:fungal_moss");
        stem = mat("stem", "awaken:glowcap_stem");
        cap = mat("cap", "awaken:glowcap_cap");
        gills = mat("gills", "awaken:glowcap_gills");
        roots = mat("roots", "awaken:lumen_roots");
        sprouts = mat("sprouts", "awaken:glowcap_sprouts");
        threads = mat("threads", "awaken:mycelium_threads");
        fall = mat("fall", "awaken:fungal_glowfall");
        puff = mat("touch", "awaken:spore_puffball");
        String n = name();
        deep = n.equals("glowing_deep");
        warrens = n.equals("root_warrens");
        canopy = n.equals("spore_canopy");
        bloom = n.equals("inside_the_bloom");
    }

    @Override
    int glow() {
        return 0x8ff0d8;
    }

    @Override
    BlockState skin(int x, int y, int z, boolean floor, boolean ceiling) {
        if (floor) {
            if (bloom) return Hash.noise(seed, x, z, 4) > 0.6 ? gills : cap;
            return Hash.noise(seed + 3, x, z, 5) > 0.56 ? moss : loam;
        }
        double n = Hash.noise3(seed + 5, x, y, z, 4);
        if (bloom && !ceiling) return n > 0.5 ? gills : cap;
        if (bloom) return cap;
        return n > (ceiling ? 0.6 : 0.66) ? veined : rock;
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return (lat + rise) % 4 == 0 ? veined : rock;
    }

    @Override
    List<FloorAir.Mist> mists() {
        return bloom ? mist(0x5a3a6a, 0.5f, 0x2a1a3a, 0.25f) : mist(0x24524e, 0.5f, 0x162a38, 0.22f);
    }

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        boolean big = r.gw * r.gh >= 4;
        hang(r, warrens ? 0.16 : 0.06, warrens ? 6 : 4, 3, 11, (x, y, z, len, i) -> roots);
        grow(r, warrens ? 0.05 : 0.09, 13, (x, y, z, len, i) -> i < 5 ? sprouts : threads);
        if (bloom) fins(r);
        // the glowcaps: a few small ones in a small cavern, more and taller in a big one, a forest in the Canopy's deep one
        int n = k == CaveEngine.Kind.ARRIVAL ? 0 : canopy && k == CaveEngine.Kind.GREAT ? 6 : big ? 3 : warrens ? 0 : 1;
        for (int i = 0; i < n; i++) {
            boolean giant = canopy && k == CaveEngine.Kind.GREAT;
            double cr = giant ? 7 + unit(id, 40 + i) * 5 : big ? 4 + unit(id, 40 + i) * 3 : 3 + unit(id, 40 + i);
            double[] at = spot(r, 20 + i * 5, giant ? 0.1 : 0.2, giant ? 0.55 : 0.6, cr * 0.35 + 2);
            if (at == null) continue;
            double ch = Math.max(2.5, cr * 0.42);
            int want = giant ? 22 + (int) (unit(id, 50 + i) * 16) : big ? 8 + (int) (unit(id, 50 + i) * 6) : 6 + (int) (unit(id, 50 + i) * 2);
            int h = (int) Math.min(want, c.ceil(id, at[0], at[1]) - ch - 2);
            if (h < 6) continue;
            double sr = giant ? 1.8 + cr * 0.12 : 1.1 + cr * 0.08;
            mushroom(at[0], at[1], h, sr, cr, stem, cap, gills, giant ? 6 : 3);
            light(at[0], h - 1, at[1], 0x7fe8d8, (float) (cr * 1.6), 0.8f);
        }
        if (k == CaveEngine.Kind.GREAT && deep && hero("glowfall")) glowfall(r);
        // touchstones: a puffball in most caverns, two in the deep one; the floor's first keeps the old puffball's secret
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, puff, k == CaveEngine.Kind.GREAT ? 2 : 1, "old_puffball", 60);
        if (k == CaveEngine.Kind.GREAT) {
            if (deep) wallNook(r, "behind_the_falls", 70);
            if (warrens) wallNook(r, "root_heart", 70);
            if (canopy) wallNook(r, "hollow_stem", 70);
            if (bloom) wallNook(r, "heart_gill", 70);
        } else if (warrens && k == CaveEngine.Kind.CAVERN && firstCalm(r)) wallNook(r, "root_heart", 71);
        PassageEngine.Shape sh = c.shapes.get(id);
        if (sh != null) light(sh.cx(), 3, sh.cz(), bloom ? 0xd890e8 : 0x6fd8c8, 10f + r.gw * 3, 0.55f);
    }

    /** Whether a place is the floor's first calm cavern on the way (where a world keeps one of its secrets). */
    boolean firstCalm(Room r) {
        for (int w : c.chain.places) {
            CaveEngine.Kind k = c.kinds.get(w);
            if (k == CaveEngine.Kind.CAVERN && !p.sealable(p.world(w))) return w == r.id;
        }
        return false;
    }

    /** Inside the Bloom: gill fins standing out from the walls toward the middle, every few degrees, up to the roof. */
    void fins(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int[] b = box(r);
        int id = r.id;
        c.s.op(b[0], W, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 2, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double px = x + 0.5, pz = z + 0.5;
            double q = c.q(id, px, pz);
            if (q < 0.72 || nearDoor(r, px, pz, 3)) return;
            double a = Math.atan2(pz - sh.cz(), px - sh.cx());
            double step = Math.PI * 2 / Math.max(16, (int) (sh.a() * 1.6));
            double off = Math.abs(((a % step) + step) % step - step / 2) * Math.hypot(px - sh.cx(), pz - sh.cz());
            if (off < 0.55) buf.set(x, y, z, (y + x + z) % 5 == 0 ? cap : gills);
        });
    }

    /** The Glowing Deep's falls: a curtain of glowing water from the deep cavern's roof into its lake, on its far side. */
    void glowfall(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null || !c.inLakeAny(r.id)) return;
        int id = r.id;
        double a0 = unit(id, 81) * Math.PI * 2;
        int[] b = box(r);
        c.s.op(b[0], W - 3, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 2, b[3], (buf, x, y, z) -> {
            double px = x + 0.5, pz = z + 0.5;
            double q = c.q(id, px, pz);
            if (q < 0.36 || q > 0.48) return;
            double a = Math.atan2(pz - sh.cz(), px - sh.cx());
            double da = Math.abs(Math.atan2(Math.sin(a - a0), Math.cos(a - a0)));
            // over the lake, never in it (the water beside it would run)
            if (da > 0.45 || y < W || !buf.open(x, y, z)) return;
            buf.set(x, y, z, fall);
        });
        light(sh.cx() + Math.cos(a0) * sh.a() * 0.42, 6, sh.cz() + Math.sin(a0) * sh.b() * 0.42, 0x8ff8f0, 18f, 0.9f);
        c.shafts.add(new FloorAir.Shaft((int) (sh.cx() + Math.cos(a0) * sh.a() * 0.42), c.roofs.getOrDefault(id, c.roof),
                (int) (sh.cz() + Math.sin(a0) * sh.b() * 0.42), (int) sh.cx(), 0, (int) sh.cz(), 3f, 0x8ff8f0, 0.5f));
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int[] b = box(r);
        int ox = b[0], oz = b[1];
        ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
        c.s.op(ox, W + 2, oz, ox + 15, W + 9, oz + 15, (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y + 1, z)) return;
            long h = Hash.of(seed + 17, x, z);
            if (Hash.unit(h) >= (warrens ? 0.3 : 0.12)) return;
            int len = 1 + (int) (Hash.unit(Hash.mix(h)) * (warrens ? 4 : 2));
            for (int i = 0; i < len && buf.open(x, y - i, z) && y - i > W + 1; i++) buf.set(x, y - i, z, roots);
        });
        c.s.op(ox, W, oz, ox + 15, W, oz + 15, (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y - 1, z)) return;
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w == null || Math.abs(w[0]) < 0.8) return;
            if (Hash.unit(seed + 19, x, z) < 0.12) buf.set(x, y, z, Hash.unit(seed + 23, x, z) < 0.5 ? sprouts : threads);
        });
    }

    @Override
    void secret(Room r) {
        // the Mycelium Mind: every surface threads and light, a knot of gills at its heart, and the heart a nook
        hang(r, 0.2, 5, 3, 31, (x, y, z, len, i) -> roots);
        grow(r, 0.6, 33, (x, y, z, len, i) -> threads);
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int h = (int) Math.min(7, c.ceil(r.id, sh.cx(), sh.cz()) - 5);
        if (h >= 5) mushroom(sh.cx() + 4, sh.cz(), h, 1.0, 3.5, stem, gills, gills, 1);
        nook("the_mind", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
        light(sh.cx(), 3, sh.cz(), 0xb0fff0, 14f, 0.9f);
    }
}
