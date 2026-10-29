package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Hive (SPEC §38.10): walls of comb, six-sided cells some glowing with honey and some capped, a rim of dark wax round
 * each; wax floors dusted with pollen, honey dripping from every roof, wax candles. The Comb's caverns and tunnels are
 * six-sided, the Brood Hall is a vast hive ringed with ledges, the Honey Lake is wax islands on honey, and in the Swarm the
 * walls move round you. The Queen's Garden is a sunlit meadow of petals inside it all.
 */
final class HiveDress extends Dress {
    final BlockState wax, waxDark, comb, capped, honey, floor, pollen, drip, drum, royal, petals, candle;
    final boolean theComb, brood, lake, swarm;

    HiveDress(CaveEngine.Cave c) {
        super(c);
        wax = mat("wall", "awaken:hive_wax");
        waxDark = mat("rim", "awaken:hive_wax_dark");
        comb = mat("wall_glow", "awaken:hive_comb");
        capped = mat("capped", "awaken:hive_comb_capped");
        honey = mat("honey", "awaken:hive_honey_glow");
        floor = mat("floor", "awaken:hive_floor");
        pollen = mat("floor_soft", "awaken:hive_pollen");
        drip = mat("drip", "awaken:hive_drip");
        drum = mat("touch", "awaken:hive_drum");
        royal = mat("royal", "awaken:hive_royal");
        petals = mat("petals", "awaken:hive_petals");
        candle = mat("candle", "awaken:hive_candle");
        String n = name();
        theComb = n.equals("the_comb");
        brood = n.equals("brood_hall");
        lake = c.sea;
        swarm = n.equals("the_swarm");
    }

    @Override
    int glow() {
        return 0xffd060;
    }

    /** A six-sided cell lattice on a wall: 0 rim, 1 capped, 2 honey, 3 royal (rare). */
    int cell(int x, int y, int z) {
        // cells across the wall (x and z together) and up it, every other row shifted half a cell
        double u = (x + z) / 4.0, v = y / 3.4;
        int row = (int) Math.floor(v);
        double fu = u + ((row & 1) == 0 ? 0 : 0.5);
        int col = (int) Math.floor(fu);
        double du = Math.abs(fu - col - 0.5), dv = Math.abs(v - row - 0.5);
        if (du > 0.4 || dv > 0.38 || du * 0.5 + dv > 0.55) return 0;
        long h = Hash.of(seed + 77, col, row);
        double k = Hash.unit(h);
        return k < 0.04 ? 3 : k < 0.45 ? 2 : 1;
    }

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        if (fl) return Hash.noise(seed + 3, x, z, 4) > 0.62 ? pollen : floor;
        if (ceiling) return Hash.noise3(seed + 5, x, y, z, 3) > 0.65 ? capped : wax;
        return switch (cell(x, y, z)) {
            case 0 -> waxDark;
            case 2 -> comb;
            case 3 -> royal;
            default -> capped;
        };
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return switch (cell(lat, rise, 0)) {
            case 0 -> waxDark;
            case 2 -> comb;
            default -> capped;
        };
    }

    @Override
    List<FloorAir.Mist> mists() {
        return mist(0x6a4a1a, 0.45f, 0x3a2a12, 0.22f);
    }

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        hang(r, 0.07, 3, 3, 11, (x, y, z, len, i) -> drip);
        grow(r, 0.02, 13, (x, y, z, len, i) -> candle);
        if (brood && k == CaveEngine.Kind.GREAT) ledges(r);
        if (swarm && k != CaveEngine.Kind.ARRIVAL) slides(r);
        if (theComb && k != CaveEngine.Kind.ARRIVAL && sh != null) {
            // a honey pool in a comb cavern's middle: a ring of wax round a pool of glowing honey, sunk in the floor
            int cx = (int) sh.cx(), cz = (int) sh.cz();
            c.s.op(cx - 3, W - 1, cz - 3, cx + 3, W - 1, cz + 3, (b, x, y, z) -> {
                double d = Math.hypot(x + 0.5 - sh.cx(), z + 0.5 - sh.cz());
                if (d < 2.2) b.set(x, y, z, honey);
                else if (d < 3.2) b.set(x, y, z, waxDark);
            });
            light(sh.cx(), 0, sh.cz(), 0xffa020, 9f, 0.7f);
        }
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, drum, k == CaveEngine.Kind.GREAT ? 2 : 1, "humming_drum", 60);
        if (k == CaveEngine.Kind.GREAT) {
            if (theComb) wallNook(r, "sealed_cell", 70);
            if (brood) wallNook(r, "the_nursery", 70);
            if (swarm) wallNook(r, "still_cell", 70);
            if (lake && sh != null) nook("drowned_comb", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
        }
        if (sh != null) light(sh.cx(), 3, sh.cz(), 0xffc050, 10f + r.gw * 3, 0.6f);
    }

    /** The Brood Hall's ledges: rings of wax round its wall every seven blocks up, as deep as a step is wide. */
    void ledges(Room r) {
        int[] b = box(r);
        int id = r.id;
        int roof = c.roofs.getOrDefault(id, c.roof);
        c.s.op(b[0], W + 6, b[1], b[2], W + roof, b[3], (buf, x, y, z) -> {
            if ((y - W) % 7 != 6 || !buf.open(x, y, z)) return;
            double d = c.d(id, x + 0.5, z + 0.5);
            if (d > -2.5 && d < 0) buf.set(x, y, z, Hash.unit(seed + 5, x, z) < 0.2 ? comb : wax);
        });
        PassageEngine.Shape sh = c.shapes.get(id);
        if (sh != null) for (int i = 1; i <= roof / 7; i++) light(sh.cx() + sh.a() * 0.8, i * 7, sh.cz(), 0xffb040, 10f, 0.5f);
    }

    /** The Swarm's moving walls: panels of comb that slide back and forth across a cavern (block displays, no collision). */
    void slides(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int n = r.gw * r.gh >= 4 ? 3 : 1;
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, 80 + i * 5, 0.2, 0.55, 3);
            if (at == null) continue;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            int h = Math.min(6, top(r.id, x, z) - W);
            if (h < 4) continue;
            p.mark(FloorLayout.Mark.PROP, "comb:" + 25, x - 2, 0, z - 2, x + 2, h - 1, z + 2);
        }
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int[] b = box(r);
        c.s.op(b[0], W + 2, b[1], b[0] + 15, W + 9, b[1] + 15, (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y + 1, z) || Hash.unit(seed + 17, x, z) >= 0.08) return;
            buf.set(x, y, z, drip);
        });
    }

    @Override
    void secret(Room r) {
        // the Queen's Garden: petals over every floor, wax candles, the roof a skylight of glowing honey
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        grow(r, 0.7, 33, (x, y, z, len, i) -> i == 0 ? candle : petals);
        int[] b = box(r);
        int id = r.id;
        c.s.op(b[0], W + 4, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 3, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y + 1, z) || c.q(id, x + 0.5, z + 0.5) > 0.6) return;
            buf.set(x, y + 1, z, honey);
        });
        nook("the_queens_seat", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
        c.shafts.add(new FloorAir.Shaft((int) sh.cx(), c.roofs.getOrDefault(id, c.roof), (int) sh.cz(), (int) sh.cx(), 0, (int) sh.cz(), 5f, 0xfff0b0, 0.8f));
        light(sh.cx(), 6, sh.cz(), 0xfff0c0, 18f, 1f);
    }
}
