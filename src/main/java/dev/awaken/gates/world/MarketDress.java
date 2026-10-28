package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Lantern Night Market (SPEC §38.11): a town under a night sky that never ends. White plaster houses with red timber
 * frames and paper windows lit from inside, tiled roofs, cobbled squares full of stalls under red cloth, strings of lights
 * and paper lanterns over every way. Lantern Street runs along canals, the Rooftops are squares on the roofs joined by
 * railed bridges over deep streets, the Floating Market is boardwalks and boats on a lagoon, and the Festival has a
 * lantern as big as a house hung over its great square. The Broker's Vault is a room walled with lanterns.
 */
final class MarketDress extends Dress {
    final BlockState boards, cobbles, plaster, timber, timberX, timberZ, roof, paper, lantern, lights, cloth, goods, boatLamp, wish, rail, gold, water;
    final boolean street, rooftops, floating, festival, sea;

    MarketDress(CaveEngine.Cave c) {
        super(c);
        boards = mat("boards", "awaken:market_boards");
        cobbles = mat("floor", "awaken:market_cobbles");
        plaster = mat("wall", "awaken:market_plaster");
        timber = mat("timber", "awaken:market_timber");
        timberX = WorldMats.with(timber, "axis", "x");
        timberZ = WorldMats.with(timber, "axis", "z");
        roof = mat("roof", "awaken:market_roof");
        paper = mat("wall_glow", "awaken:market_paper_wall");
        lantern = mat("lantern", "awaken:market_lantern");
        lights = mat("lights", "awaken:market_lights");
        cloth = mat("cloth", "awaken:market_cloth");
        goods = mat("goods", "awaken:market_goods");
        boatLamp = mat("boat_lamp", "awaken:market_boat_lamp");
        wish = mat("touch", "awaken:market_wish_lantern");
        rail = GatePainter.state("minecraft:mangrove_fence");
        gold = GatePainter.state("minecraft:gold_block");
        water = c.fluid;
        String n = name();
        street = n.equals("lantern_street");
        rooftops = n.equals("rooftops");
        floating = n.equals("floating_market");
        festival = n.equals("festival");
        sea = c.sea;
    }

    @Override
    int glow() {
        return 0xffb060;
    }

    @Override
    BlockState rail() {
        return rail;
    }

    // ---- the town's shape ---------------------------------------------------------------------------------------------

    /** Houses on lots nine across with an alley between, each two to four storeys under a pitched roof. */
    @Override
    int skyline(int x, int z) {
        int u = Math.floorMod(x + 4, 9), v = Math.floorMod(z + 2, 9);
        if (u == 0 || v == 0) return c.lid - 1;
        int lx = Math.floorDiv(x + 4, 9), lz = Math.floorDiv(z + 2, 9);
        int h = c.lid + 1 + (int) (Hash.unit(seed, lx * 41 + 3, lz) * 9);
        boolean ridgeX = Hash.unit(seed, lx * 41 + 7, lz) < 0.5;
        int across = ridgeX ? v : u;
        return h + (4 - (int) Math.abs(across - 4.5));
    }

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        int rise = y - W;
        if (fl) {
            if (sea && rise == -1) return boards;
            if (rise >= c.lid - 2) return roof;
            return rooftops && rise == -1 ? boards : cobbles;
        }
        if (ceiling) return boards;
        return plaster;
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        if (lat % 4 == 0) return timber;
        return rise % 5 == 2 ? paper : plaster;
    }

    @Override
    List<FloorAir.Mist> mists() {
        return mist(0x2a2040, 0.3f, 0x1a1428, 0.18f);
    }

    /**
     * The houses' faces: a red post every four blocks and a beam every five up, paper windows lit between, and here and
     * there an awning of cloth over the way. Only faces you can see (the inside of the land stays plaster).
     */
    void facades() {
        c.s.op(0, W - c.deep - 1, 0, Plots.SIDE - 1, W + CaveEngine.SKY_MAX, Plots.SIDE - 1, (b, x, y, z) -> {
            short v = b.get(x, y, z);
            if (v == Sculpt.SOLID) {
                boolean ex = b.open(x + 1, y, z) || b.open(x - 1, y, z), ez = b.open(x, y, z + 1) || b.open(x, y, z - 1);
                if (!ex && !ez || b.open(x, y + 1, z)) return;
                int along = ex ? z : x, rise = Math.floorMod(y - W, 5);
                if (Math.floorMod(along, 4) == 0) b.set(x, y, z, timber);
                else if (rise == 4) b.set(x, y, z, ex ? timberZ : timberX);
                else if (rise == 2 && Math.floorMod(along, 4) == 2 && y > W - c.deep) b.set(x, y, z, paper);
            } else if (v == Sculpt.OPEN && y == W + 3 && Hash.unit(seed + 71, x, z) < 0.12) {
                // an awning: cloth out over the way from a wall, never in front of a doorway
                if (b.get(x + 1, y, z) == Sculpt.SOLID || b.get(x - 1, y, z) == Sculpt.SOLID || b.get(x, y, z + 1) == Sculpt.SOLID
                        || b.get(x, y, z - 1) == Sculpt.SOLID) b.set(x, y, z, cloth);
            }
        });
    }

    @Override
    void world() {
        facades();
        if (floating) boats();
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        if (k != CaveEngine.Kind.ARRIVAL && !sea) stalls(r);
        if (festival && k == CaveEngine.Kind.GREAT && hero("the_great_lantern")) greatLantern(r);
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, wish, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_wish", 60);
        if (k == CaveEngine.Kind.GREAT && sh != null) {
            int nx = (int) sh.cx(), nz = (int) sh.cz();
            // a square that fills its slots has no wall to dig into: the nook is under its middle instead
            if (street && wallNook(r, "the_canal_shrine", 70) == null) nook("the_canal_shrine", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
            if (rooftops) pavilion(r, "the_highest_roof");
            if (floating) nook("the_lost_boat", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
            if (festival && wallNook(r, "the_wish_tree", 70) == null) nook("the_wish_tree", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
        }
        // strings of lights across the square from side to side, a lantern hung in the middle of each
        if (sh != null && !sea) for (int i = -2; i <= 2; i++) {
            double off = i * 4.5;
            boolean alongX = unit(id, 9) < 0.5;
            int y = W + 6 + Math.abs(i) % 2;
            int[] b = box(r);
            c.s.op(b[0], y, b[1], b[2], y, b[3], (buf, x, yy, z) -> {
                if (!buf.open(x, yy, z)) return;
                double rel = alongX ? z + 0.5 - sh.cz() : x + 0.5 - sh.cx();
                if (Math.abs(rel - off) < 0.5) buf.set(x, yy, z, lights);
            });
        }
        if (sh != null) light(sh.cx(), 4, sh.cz(), 0xffa850, 12f + r.gw * 3, 0.6f);
    }

    /** Stalls round a square: goods on a counter under a red cloth on two posts, a lantern hung under the cloth. */
    void stalls(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int n = r.gw * r.gh >= 4 ? 5 : 2;
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, 80 + i * 4, 0.5, 0.78, 2.5);
            if (at == null) continue;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            boolean alongX = Math.abs(at[0] - sh.cx()) < Math.abs(at[1] - sh.cz());
            for (int t = -1; t <= 1; t++) {
                int gx = x + (alongX ? t : 0), gz = z + (alongX ? 0 : t);
                c.s.putOpen(gx, W, gz, goods);
                c.s.putOpen(gx, W + 3, gz, cloth);
                if (t != 0) {
                    c.s.putOpen(gx + (alongX ? t : 0), W, gz + (alongX ? 0 : t), timber);
                    c.s.putOpen(gx + (alongX ? t : 0), W + 1, gz + (alongX ? 0 : t), timber);
                    c.s.putOpen(gx + (alongX ? t : 0), W + 2, gz + (alongX ? 0 : t), timber);
                    c.s.putOpen(gx + (alongX ? t : 0), W + 3, gz + (alongX ? 0 : t), cloth);
                }
            }
            c.s.putOpen(x, W + 2, z, lantern);
            block(at[0], at[1], 2.5);
        }
    }

    /** The Festival's lantern: a paper lantern as big as a house hung over the great square, its glow on everything. */
    void greatLantern(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz(), cy = W + 11.5;
        c.s.op((int) cx - 5, W + 6, (int) cz - 5, (int) cx + 5, W + 18, (int) cz + 5, (b, x, y, z) -> {
            if (!b.open(x, y, z)) return;
            double dx = x + 0.5 - cx, dy = (y + 0.5 - cy) / 1.25, dz = z + 0.5 - cz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean cap = Math.abs(dx) < 2 && Math.abs(dz) < 2 && (y == (int) (cy + 4.6) || y == (int) (cy - 4.6));
            if (cap) b.set(x, y, z, roof);
            else if (d < 4 && d > 3) b.set(x, y, z, Math.floorMod(x + z, 3) == 0 ? timber : paper);
            else if (Math.abs(dx) < 0.6 && Math.abs(dz) < 0.6 && y > cy + 4.6) b.set(x, y, z, lights);
        });
        light(cx, 11, cz, 0xffb060, 26f, 1f);
        c.shafts.add(new FloorAir.Shaft((int) cx, 16, (int) cz, (int) cx, 0, (int) cz, 5f, 0xffc080, 0.5f));
    }

    /** A pavilion on the Rooftops' great square: four red posts under a tiled roof, the nook under it. */
    void pavilion(Room r, String secret) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        int x = (int) sh.cx(), z = (int) sh.cz();
        c.s.op(x - 2, W, z - 2, x + 2, W + 4, z + 2, (b, xx, y, zz) -> {
            if (!b.open(xx, y, zz)) return;
            boolean corner = Math.abs(xx - x) == 2 && Math.abs(zz - z) == 2;
            if (y < W + 4 && corner) b.set(xx, y, zz, timber);
            else if (y == W + 4) b.set(xx, y, zz, roof);
            else if (y == W + 3 && (xx == x || zz == z)) b.set(xx, y, zz, lantern);
        });
        nook(secret, x - 1, 0, z - 1, x + 1, 2, z + 1);
        block(x + 0.5, z + 0.5, 3);
    }

    /** The Floating Market's boats on the lagoon: a hull of boards, a post with a lantern, lamps floating round. */
    void boats() {
        for (int i = 0; i < 18; i++) {
            int chunk = 1 + (int) (Hash.unit(seed, 700 + i, 1) * 14), chunkZ = 1 + (int) (Hash.unit(seed, 700 + i, 2) * 14);
            int x = chunk * 16 + 4 + (int) (Hash.unit(seed, 700 + i, 3) * 6), z = chunkZ * 16 + 4 + (int) (Hash.unit(seed, 700 + i, 4) * 6);
            boolean alongX = Hash.unit(seed, 700 + i, 5) < 0.5;
            int hx = alongX ? 2 : 1, hz = alongX ? 1 : 2;
            c.s.op(x - hx - 2, W - 1, z - hz - 2, x + hx + 2, W + 2, z + hz + 2, (b, xx, y, zz) -> {
                // only out on open water: nothing by an island or a boardwalk
                if (!b.water(x - hx - 2, W - 1, z - hz - 2) || !b.water(x + hx + 2, W - 1, z + hz + 2) || !b.water(x, W - 1, z)) return;
                int dx = Math.abs(xx - x), dz = Math.abs(zz - z);
                boolean hull = dx <= hx && dz <= hz, rim = hull && (dx == hx || dz == hz);
                if (y == W - 1 && hull) b.set(xx, y, zz, boards);
                else if (y == W && rim) b.set(xx, y, zz, boards);
                else if (dx == 0 && dz == 0 && y > W - 1) b.set(xx, y, zz, y == W + 2 ? lantern : timber);
                else if (y == W && !hull && (dx + dz) % 3 == 0 && b.water(xx, W - 1, zz)) b.set(xx, y, zz, boatLamp);
            });
            light(x, 2, z, 0xffb060, 8f, 0.6f);
        }
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        if (sea) return;
        int[] b = box(r);
        ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
        int ox = b[0], oz = b[1];
        c.s.op(ox, W - 2, oz, ox + 15, W + 7, oz + 15, (buf, x, y, z) -> {
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w == null) return;
            double lat = Math.abs(w[0]);
            int along = (int) Math.round(w[1]);
            boolean inner = x - ox >= 3 && x - ox <= 12 && z - oz >= 3 && z - oz <= 12;
            // Lantern Street's canal: two deep down the middle of the way, kept off the doorways
            if (street && hero("canals") && inner && lat < 1.0 && y < W) {
                buf.set(x, y, z, water);
                return;
            }
            if (street && hero("canals") && inner && lat < 1.0 && y == W && Math.floorMod(along, 5) == 2 && buf.open(x, y, z)) {
                buf.set(x, y, z, boatLamp);
                return;
            }
            if (!buf.open(x, y, z)) return;
            if (y == W + 6 && Math.floorMod(along, 6) == 3) buf.set(x, y, z, lights);
            else if (y == W + 5 && lat < 0.5 && Math.floorMod(along, 6) == 3) buf.set(x, y, z, lantern);
        });
    }

    @Override
    void secret(Room r) {
        // the Broker's Vault: every wall hung with lanterns, heaps of gold, the ledger in the middle
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int[] b = box(r);
        c.s.op(b[0], W + 1, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof), b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || nearDoor(r, x + 0.5, z + 0.5, 2.5)) return;
            boolean wall = buf.get(x + 1, y, z) == Sculpt.SOLID || buf.get(x - 1, y, z) == Sculpt.SOLID
                    || buf.get(x, y, z + 1) == Sculpt.SOLID || buf.get(x, y, z - 1) == Sculpt.SOLID;
            if (wall && Hash.unit(seed + 81, x * 13 + y, z) < 0.4) buf.set(x, y, z, lantern);
        });
        for (int i = 0; i < 4; i++) {
            double[] at = spot(r, 90 + i * 3, 0.4, 0.7, 1.5);
            if (at == null) continue;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            c.s.putOpen(x, W, z, gold);
            if (i % 2 == 0) c.s.putOpen(x, W + 1, z, gold);
            block(at[0], at[1], 0.8);
        }
        nook("the_wish_ledger", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
        light(sh.cx(), 4, sh.cz(), 0xffc070, 16f, 1f);
    }
}
