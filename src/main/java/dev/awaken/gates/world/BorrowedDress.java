package dev.awaken.gates.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A rare world in another theme's clothes (SPEC §38.13). The theme's own dress does the work; this one passes every hook
 * on and adds what the rare world has on top ({@link #more}). The Reflection is the gate you came in by, everything in it
 * grey and its lights red, with the floor under your feet glass and the same rooms upside down under it; the Other Gate
 * is a cave theme you know with every colour wrong. Their carved blocks pass through a {@link Tint}, and one patch on a
 * calm cavern's floor keeps its true colours: standing in it finds the world's secret.
 */
class BorrowedDress extends Dress {
    final Dress in;
    private final Tint tint;
    private final boolean grey;

    BorrowedDress(CaveEngine.Cave c, Dress in, boolean tinted) {
        super(c);
        this.in = in;
        this.grey = tinted && !c.c.worldId().endsWith("/the_other_gate");
        this.tint = !tinted ? null : new Tint(grey ? Tint.Way.GREY : Tint.Way.TURN);
    }

    /** What the rare world adds to a place, after the theme has dressed it. */
    void more(Room r, CaveEngine.Kind k) {}

    /** What the rare world adds round the places, after the theme's own. */
    void moreWorld() {}

    @Override
    Tint tint() {
        return tint;
    }

    @Override
    int glow() {
        return tint == null ? in.glow() : grey ? 0xd83a3a : Tint.turn(in.glow(), 150);
    }

    @Override
    BlockState underGlass() {
        BlockState g = in.underGlass();
        return g != null || !grey ? g : GatePainter.state("minecraft:glass");
    }

    @Override
    List<FloorAir.Mist> mists() {
        if (tint == null) return in.mists();
        List<FloorAir.Mist> l = new ArrayList<>();
        for (FloorAir.Mist m : in.mists()) {
            int rgb = grey ? greyOf(m.rgb()) : Tint.turn(m.rgb(), 150);
            l.add(new FloorAir.Mist(m.y(), m.thickness(), rgb, m.density()));
        }
        return l;
    }

    private static int greyOf(int rgb) {
        int v = (int) Math.round(Tint.lum(rgb));
        return v << 16 | v << 8 | v;
    }

    @Override
    void world() {
        in.world();
        moreWorld();
        if (tint == null) return;
        // the patch that kept its colours: on a calm cavern's floor, away from its mouths
        String secret = grey ? "the_true_colour" : "the_right_colour";
        List<Integer> ids = new ArrayList<>(c.kinds.keySet());
        java.util.Collections.sort(ids);
        for (int id : ids) {
            if (c.kinds.get(id) != CaveEngine.Kind.CAVERN) continue;
            Room r = p.world(id);
            if (p.sealable(r)) continue;
            double[] at = spot(r, 811, 0.15, 0.55, 3);
            if (at == null) continue;
            tint.keep(at[0], at[1], 4.5);
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            nook(secret, x - 1, 0, z - 1, x + 1, 2, z + 1);
            light(at[0], 2, at[1], grey ? 0xffd890 : 0xffffff, 7f, 0.5f);
            return;
        }
    }

    // ---- the theme's own dress does the rest ----------------------------------------------------------------------------

    @Override
    void programme() {
        in.programme();
    }

    @Override
    BlockState skin(int x, int y, int z, boolean floor, boolean ceiling) {
        return in.skin(x, y, z, floor, ceiling);
    }

    @Override
    int skyline(int x, int z) {
        return in.skyline(x, z);
    }

    @Override
    BlockState rail() {
        return in.rail();
    }

    @Override
    boolean walk(Room r, int x, int z) {
        return in.walk(r, x, z);
    }

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        in.cavern(r, k);
        more(r, k);
        clearDoorways(r);
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        in.tunnel(r, l);
        clearDoorways(r);
    }

    /**
     * The theme's dress meets rooms here it never meets at home (the gate's own Rig rooms, another theme's), so a piece of
     * it can land across a doorway: the middle of every doorway is kept open at foot and head height, four blocks in.
     */
    void clearDoorways(Room r) {
        for (Room.Door d : r.doors) {
            int[] b = WorldPlan.doorBox(d, 4);
            boolean alongX = d.side == 0 || d.side == 2;
            c.s.op(b[0], W, b[2], b[1], W + 1, b[3], (buf, x, y, z) -> {
                int lat = alongX ? x - d.gx * 16 : z - d.gz * 16;
                if (lat <= RoomGeometry.DOOR_LO || lat >= RoomGeometry.DOOR_HI || !CaveEngine.Cave.insideSlots(r, x, z)) return;
                BlockState st = buf.state(x, y, z);
                if (st != null && !st.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) buf.mark(x, y, z, Sculpt.OPEN);
            });
        }
    }

    @Override
    void secret(Room r) {
        in.secret(r);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return in.screen(lat, rise, side);
    }
}
