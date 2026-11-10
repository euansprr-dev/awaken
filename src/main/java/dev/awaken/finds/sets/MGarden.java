package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Family;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.G;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/**
 * The Dream Gardens (SPEC §64.8, family 8): where some of those who went in chose to sleep standing, in the open, among
 * flowers. A walled garden round a still pool. Its gate opens only at night; then the flowers light one after another
 * from the gate to the pavilion in the pool, and you follow them. (From inside, the gate opens at any hour: nobody is
 * ever shut in.)
 */
final class MGarden {
    private MGarden() {}

    /** The gate: shut by day, open after dark to anyone who stands at it; from inside it opens to a hand at any hour. */
    private static final class NightGate implements Script {
        private void open(Ctx c, ServerPlayer p) {
            Mon.open(c, "gate", SoundEvents.IRON_DOOR_OPEN);
            c.sound(c.pos("way", 0), SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.6F);
            c.complete(p);
        }

        @Override
        public void tick(Ctx c) {
            if (!c.night() || c.count("gate") == 0) return;
            var at = c.pos("gate", 0);
            for (ServerPlayer p : c.players(c.kind().reach() + 4))
                if (p.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 9 * 9) {
                    open(c, p);
                    return;
                }
        }

        @Override
        public boolean use(Ctx c, ServerPlayer p, String part, int i, ItemStack held) {
            if (!part.equals("gate")) return false;
            var in = c.pos("way", 1);                              // the first waypoint inside the wall
            var gate = c.pos("gate", i);
            boolean inside = p.distanceToSqr(in.getX() + 0.5, in.getY(), in.getZ() + 0.5) < p.distanceToSqr(gate.getX() + 0.5 + (gate.getX() - in.getX()), gate.getY(), gate.getZ() + 0.5 + (gate.getZ() - in.getZ()));
            if (c.night() || inside) open(c, p);
            else {
                c.puff(gate, ParticleTypes.ASH, 6, 0.3, 0.01);
                c.sound(gate, SoundEvents.CHAIN_HIT, 0.8F, 0.6F);
            }
            return true;
        }
    }

    /**
     * {@code W}: the wall's radius. {@code wallH}: its height. {@code P}: the pool's radius. {@code n}: how many
     * sleepers stand round the pool. {@code state}: 0 small and broken, 1 fallen, 2 as the painting has it, 3 flooded
     * from wall to wall, 4 whole, with a covered walk inside the wall.
     */
    static void garden(Scene s, int W, int wallH, int P, int n, int state, int reach) {
        boolean flooded = state == 3, whole = state == 4, fallen = state == 1;
        G.platform(s, G.disc(0, 0, W + 3), 0, reach);
        s.clear(reach);
        // -- the wall: two thick, a coping, pilasters inside with a panel between each pair
        for (int x = -W - 2; x <= W + 2; x++)
            for (int z = -W - 2; z <= W + 2; z++) {
                double r = Math.sqrt(x * x + z * z), a = Math.floorMod(Math.round(Math.toDegrees(Math.atan2(z, x))), 360L);
                int in = (int) Math.floorMod(Math.round(a), 30L);
                boolean gate = Math.abs(a - 90) <= Math.toDegrees(2.6 / W);
                if (r > W - 1.5 && r <= W + 0.5) {
                    if (gate) continue;
                    for (int y = 0; y <= wallH; y++) s.put(x, y, z, y == wallH ? M.block() : y == wallH - 2 ? M.band() : M.course(y));
                    if (Math.floorMod(x + z, 2) == 0) s.put(x, wallH + 1, z, M.blockSlab());
                } else if (r > W - 2.5 && r <= W - 1.5 && !gate) {
                    if (in <= 1 || in >= 29) { for (int y = 1; y <= wallH - 1; y++) s.put(x, y, z, y == wallH - 1 ? M.capital() : M.stone()); }
                    else if (in >= 13 && in <= 17) for (int y = 2; y <= Math.min(4, wallH - 2); y++) s.put(x, y, z, M.panel());
                } else if (whole && r > W - 6.5 && r <= W - 2.5 && !gate) {         // the covered walk
                    s.put(x, 0, z, M.flag());
                    s.put(x, wallH, z, top(M.blockSlab()));
                    if (r <= W - 5.5 && in >= 9 && in <= 11) for (int y = 1; y < wallH; y++) s.put(x, y, z, y == 1 || y == wallH - 1 ? M.capital() : M.pillar(Direction.Axis.Y));
                }
            }
        // -- the gate on the south: two pylons, an arch, bronze leaves that are shut by day
        int gh = state == 0 ? 10 : 14;
        for (int side = -1; side <= 1; side += 2) {
            for (int x = 3; x <= 5; x++) for (int z = W - 2; z <= W + 1; z++) for (int y = 0; y <= gh; y++) s.put(side * x, y, z, y % 5 == 0 ? M.band() : y == gh ? M.block() : M.course(y));
            s.put(side * 4, gh + 1, W, M.lamp(!fallen));
            s.put(side * 4, gh + 2, W, M.blockSlab());
            for (int y = 2; y <= 5; y++) s.put(side * 4, y, W + 2, M.panel());
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 7; y <= 9; y++) for (int z = W - 1; z <= W; z++) s.put(x, y, z, y == 7 && Math.abs(x) <= 1 ? AIR : y == 8 && x == 0 ? M.seal() : y == 9 ? M.band() : M.block());
            for (int y = 1; y <= 6; y++) {
                if (fallen && (x < 0 || y > 3)) { if (x < 0 && y <= 2) s.put(x - 3, s.g(x - 3, W + 3 + y) + 1, W + 3 + y, M.door()); continue; }   // one leaf lies in the grass
                s.put(x, y, W, M.door());
                s.part("gate", x, y, W);
            }
            s.put(x, 0, W, M.flag());
            s.put(x, 0, W - 1, M.flag());
        }
        if (fallen) { s.put(2, 1, W, M.door()); if (s.part("gate").isEmpty()) s.part("gate", 2, 1, W); }
        // -- the pool, the island in it, stepping stones from the south
        for (int x = -P - 2; x <= P + 2; x++)
            for (int z = -P - 2; z <= P + 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r <= 3.4) { for (int y = -3; y <= 0; y++) s.put(x, y, z, y == 0 ? (r < 1.5 ? M.mosaic() : M.flag()) : M.course(y)); continue; }
                if (r <= P) {
                    boolean stone = x == 0 && z > 0 && Math.floorMod(z, 2) == 0;
                    boolean wet = state != 0 && (state != 1 || n(s, x * 1.3, z * 1.3, 4.0) > 0.45);
                    s.put(x, -2, z, Math.floorMod(x * 3 + z * 5, 7) == 0 ? M.block() : M.mosaic());
                    s.put(x, -1, z, stone ? M.block() : wet ? WATER : state == 1 ? st(Blocks.MUD) : AIR);
                    s.put(x, 0, z, stone ? M.flag() : wet ? WATER : state == 1 ? st(Blocks.MOSS_CARPET) : AIR);
                    if (wet && !stone && h(s, x, 7, z) < 26) s.put(x, 1, z, st(Blocks.LILY_PAD));
                } else if (r <= P + 1.5) s.put(x, 0, z, Math.floorMod(Math.round(Math.toDegrees(Math.atan2(z, x))), 30L) < 4 ? M.block() : M.band());
            }
        // -- flooded from wall to wall: a hand of water over a mosaic floor, the path kept dry on stones
        if (flooded)
            for (int x = -W; x <= W; x++)
                for (int z = -W; z <= W; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    if (r <= P + 1.5 || r > W - 2.5) continue;
                    boolean stone = Math.abs(x) <= 1 && z > 0 && (Math.abs(x) + z) % 2 == 0;
                    s.put(x, -1, z, Math.floorMod(x + z * 3, 5) == 0 ? M.block() : M.mosaic());
                    s.put(x, 0, z, stone ? M.flag() : WATER);
                    if (!stone && h(s, x, 7, z) < 20) s.put(x, 1, z, st(Blocks.LILY_PAD));
                }
        // -- the pavilion on the island: four columns, a broken ring of lintel, the lamp, and under it the plinth
        for (int q = 0; q < 4; q++) M.column(s, q < 2 ? -2 : 2, q % 2 == 0 ? -2 : 2, 1, 5);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2, cross = x == 0 || z == 0;
            if (edge || cross) s.put(x, 6, z, x == 0 && z == 0 ? M.lamp(true) : edge ? M.band() : M.block());
            if (whole && !edge) s.put(x, 7, z, cross ? M.block() : M.blockSlab());
        }
        if (whole) s.put(0, 8, 0, M.bronzeBand());
        if (!whole) M.ruin(s, -2, 0, 2, 2, 5, 1, 7);
        s.put(0, 1, 0, M.block());
        s.put(0, 2, 0, M.seal());
        s.part("prize", 0, 2, 0);
        // -- the sleepers: standing round the pool, facing it, hands folded
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(i * 360.0 / n + (n % 4 == 0 ? 360.0 / n / 2 : 30) + 90 + 180.0 / n);
            int x = (int) Math.round(Math.cos(a) * (P + 4)), z = (int) Math.round(Math.sin(a) * (P + 4));
            if (Math.abs(x) <= 3 && z > 0) continue;               // never across the way in
            Direction face = Math.abs(x) >= Math.abs(z) ? (x > 0 ? Direction.WEST : Direction.EAST) : (z > 0 ? Direction.NORTH : Direction.SOUTH);
            if (flooded) for (int ox = -3; ox <= 3; ox++) for (int oz = -3; oz <= 3; oz++) if (Math.abs(ox) + Math.abs(oz) <= 4 && Math.hypot(x + ox, z + oz) > P + 1.6) { s.put(x + ox, 0, z + oz, M.block()); s.put(x + ox, -1, z + oz, M.stone()); }
            if (fallen && i % 2 == 0) M.giant(s, x, 1, z, face.getOpposite(), 15, false, i % 4 == 0 ? 1 : 0, true);
            else M.colossus(s, x, 1, z, face, state == 0 ? 8 : 10, fallen || (state == 0 && i % 2 == 1) ? 1 + i % 2 : 0);
        }
        // -- the way: from the gate to the pool's edge, over the stones, to the island. A flower by each step of it on land.
        s.part("way", 0, 1, W + 3);
        for (int z = W - 3; z >= P + 2; z -= 3) {
            s.part("way", 0, 1, z);
            int fx = (z / 3) % 2 == 0 ? -2 : 2;
            if (flooded) { s.put(fx, 0, z, M.block()); s.put(fx, -1, z, M.stone()); }
            s.put(fx, 1, z, st(Blocks.CLOSED_EYEBLOSSOM));
            s.part("bloom", fx, 1, z);
            s.put(fx, 0, z, flooded ? M.block() : st(Blocks.MOSS_BLOCK));
        }
        s.part("way", 0, 1, P - 2);
        s.part("way", 0, 1, 2);
        if (!flooded) path(s, 0, W - 2, 0, P + 2, 0, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        // -- carvings: on the gate's pylon (it says when to come), by the pool, on the wall at the back
        Mon.carving(s, 6, 1, W + 1);
        Mon.carving(s, -(P + 2), 1, -2);
        Mon.carving(s, 0, 1, -(W - 3));
        if (whole) for (int q = 0; q < 12; q++) { double a = Math.toRadians(q * 30 + 15); s.put((int) Math.round(Math.cos(a) * (W - 3)), wallH - 1, (int) Math.round(Math.sin(a) * (W - 3)), M.lamp(true)); }
        if (state == 0) M.ruin(s, -W - 2, -W - 2, W + 2, W - 3, 3, 3, wallH + 2);
        if (fallen) M.ruin(s, -W - 2, -W - 2, W + 2, W - 3, 2, 2, wallH + 2);
        if (state == 2 || flooded) M.ruin(s, -W - 2, -W - 2, -3, -3, wallH - 3, 3, wallH + 2);
        M.finish(s, 6, 36);
        // -- what grows: flowers that glow, in drifts between the pool and the wall; great trees leaning in over it
        for (int x = -W + 3; x <= W - 3; x++)
            for (int z = -W + 3; z <= W - 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < P + 2.5 || r > W - (whole ? 7 : 3) || s.taken(x, 1, z) || (Math.abs(x) <= 1 && z > 0)) continue;
                double drift = n(s, x * 1.2 + 9, z * 1.2 - 4, 5.0);
                int hh = h(s, x, 5, z);
                if (flooded) continue;
                if (s.taken(x, 0, z) && !s.get(x, 0, z).is(Blocks.MOSS_BLOCK)) continue;
                net.minecraft.world.level.block.Block f = drift > 0.62 ? (hh < 70 ? Blocks.FIREFLY_BUSH : hh < 120 ? Blocks.OPEN_EYEBLOSSOM : hh < 170 ? Blocks.BLUE_ORCHID : null)
                        : drift > 0.45 ? (hh < 36 ? Blocks.ALLIUM : hh < 60 ? Blocks.FERN : hh < 90 ? Blocks.SHORT_GRASS : null) : hh < 14 ? Blocks.SHORT_GRASS : null;
                if (f != null) s.put(x, 1, z, st(f));
                if (drift > 0.7 && hh < 20) s.put(x, 3, z, light(7));
            }
        for (int q = 0; q < 4; q++) {
            double a = Math.toRadians(q * 90 + 40);
            int x = (int) Math.round(Math.cos(a) * (W + 5)), z = (int) Math.round(Math.sin(a) * (W + 5));
            if (reach >= W + 10) giant(s, x, z, s.g(x, z) + 1, 13 + q % 2 * 3, 1, st(Blocks.DARK_OAK_LOG), leaf(Blocks.DARK_OAK_LEAVES), 3.6);
        }
        int steps = G.stairs(s, 0, W + 4, Direction.SOUTH, 2, 0);
        path(s, 0, W + 4 + steps + 8, 0, W + 4 + steps, 1, st(Blocks.COARSE_DIRT), st(Blocks.PODZOL));
        Mon.camp(s, -9, Math.min(reach - 5, W + 9));
        wild(s, W + 5, reach - 1, 12);
        s.cam(-10, 12, W + 14, -160, 14).cam(W, W + 14, W + 6, 140, 46).cam(0, 2, W + 8, 180, -8).cam(3, 2, P + 3, 160, -4);
    }

    static void init() {
        Object[][] five = {
                {1, "garden_small", "The Small Garden", new int[] {11, 6, 5, 4, 0}, 27,
                        "Two stone gateposts and what is left of a round wall in the wood. Through the bars of the gate you can see a dry basin, and four stone people standing round it with their hands folded.",
                        "You came back after dark. The gate stood open. The flowers lit one by one to the basin, and you followed them to the little house in the middle.",
                        "It says: the gate opens at night. They did not want to be looked at while they slept, only visited. Come back after dark.",
                        "It says four of them chose to sleep standing, out of doors. They said a bed felt too much like giving up.",
                        "It says: they asked for flowers that close by day. I have kept them alive. That is the whole of my work."},
                {2, "garden_fallen", "The Fallen Garden", new int[] {16, 7, 7, 6, 1}, 32,
                        "A ring of broken wall, a gate with one leaf lying in the grass, and inside a marsh where a pool used to be. Stone sleepers lie on their backs among the flowers.",
                        "You came back after dark. The flowers still lit, one after another, past the fallen sleepers to the island.",
                        "It says: the gate opens at night. Come back after dark.",
                        "It says a storm laid them down. We tried to stand them up. They are heavier than they look, and we were afraid of what would happen if we dropped one.",
                        "It says: they are still dreaming. We are sure of it. Lying down has not changed their faces."},
                {3, "garden", "The Dream Garden", new int[] {20, 8, 9, 8, 2}, 36,
                        "A round walled garden gone wild in the forest, with tall gateposts and bronze gates shut across the way in. Over the wall you can see the heads of stone figures, and at night a glow.",
                        "You came back after dark and the gate stood open. The flowers lit one after another, down to the still pool and over the stepping stones, and you followed them to the pavilion.",
                        "It says: the gate opens at night. They are not dead. Come back after dark, and follow the flowers.",
                        "They are not dead. Please do not move them. Water the flowers if you pass.",
                        "It says: in the dream they are walking in this same garden, and it is always evening. That is what they asked for. We do not know if it is what they got."},
                {4, "garden_drowned", "The Drowned Garden", new int[] {20, 8, 8, 8, 3}, 36,
                        "A walled garden with tall gateposts, and inside it nothing but still water from wall to wall, with stone sleepers standing in it up to their knees and a line of stones running in from the gate.",
                        "You came back after dark, and went in over the stones as the flowers lit. The water did not move at all. The pavilion's lamp was burning.",
                        "It says: the gate opens at night. Keep to the stones. Come back after dark.",
                        "It says the pool was meant to rise, a little each year, until it covered them. They wanted to wake under water, or not at all.",
                        "It says: it has stopped rising. It stopped the year the last of us forgot what day it was. We think that is a coincidence."},
                {5, "garden_whole", "The Garden That Still Dreams", new int[] {24, 8, 10, 12, 4}, 40,
                        "A walled garden, whole, with lamps on its gateposts and a covered walk inside the wall. Twelve stone sleepers stand round a pool, and the flowers are tended.",
                        "You came back after dark. The gate was open and the lamps were lit. The flowers led you round the pool and over the stones, and in the pavilion something had been left for whoever came.",
                        "It says: the gate opens at night. You are welcome. Come back after dark.",
                        "It says: somebody still weeds this garden. We have never seen who. We leave bread at the gate and it is gone by morning.",
                        "Cut on the pavilion, small. It says: I woke. One of the twelve is only stone now, and I am the one who tends the flowers. Do not tell the others. They are happy."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            int[] d = (int[]) v[3];
            int reach = (Integer) v[4];
            Mon.kind(Family.GARDEN, tier, (String) v[1], (String) v[2]).verb(Verb.FOLLOW)
                    .lands(Group.FOREST, Group.DARK, Group.SWAMP, Group.JUNGLE, Group.PLAINS).slope(15)
                    .see((String) v[5]).learned((String) v[6]).reach(reach)
                    .air(Air.of(0x1B2B4A, 70).mote(() -> ParticleTypes.FIREFLY, 0.5F, 9, 0.002F).loop(() -> SoundEvents.AMETHYST_BLOCK_RESONATE, 300, 0.2F, 1.4F))
                    .scene(s -> garden(s, d[0], d[1], d[2], d[3], d[4], reach))
                    .script(() -> Lore.with(Steps.of(
                                    new NightGate(),
                                    Guide.along("way").as(ParticleTypes.GLOW).note(SoundEvents.AMETHYST_BLOCK_CHIME).speed(0.2)
                                            .each((c, i) -> { if (i - 1 >= 0 && i - 1 < c.count("bloom")) c.set("bloom", i - 1, Blocks.OPEN_EYEBLOSSOM.defaultBlockState()); }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> {
                        t.use("carving", 0).hour(18000).stand("gate", 0).ticks(3);
                        for (int i = 0; i < t.count("way"); i++) t.stand("way", i).ticks(120);
                        t.use("prize", 0);
                    })
                    .add();
        }
    }
}
