package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A rare world's colours turned (SPEC §38.13). Every block the carve sets passes through: full blocks you can't walk
 * through only (glass, plants, light, fluids, shapes and veils stay as they are). The Reflection greys them by how bright they are into the four
 * concrete greys, and whatever glows turns red; the Other Gate turns every colour 150 degrees round the wheel, a little
 * stronger, into the nearest concrete or terracotta, its lights into the nearest light block. One patch per floor keeps
 * its true colours (the world's secret there). The answer per state is kept, so a chunk's carve stays cheap.
 */
final class Tint {
    enum Way { GREY, TURN }

    final Way way;
    private double kx, kz, kr;
    private final ConcurrentHashMap<BlockState, BlockState> memo = new ConcurrentHashMap<>();

    Tint(Way way) {
        this.way = way;
    }

    /** Keeps a round patch (floor-local x and z, every height) in its true colours. */
    void keep(double x, double z, double r) {
        kx = x;
        kz = z;
        kr = r;
    }

    /** A block on its way out, x and z floor-local. */
    BlockState at(int x, int y, int z, BlockState s) {
        if (!s.isSolidRender()) return s;
        if (kr > 0) {
            double dx = x + 0.5 - kx, dz = z + 0.5 - kz;
            if (dx * dx + dz * dz < kr * kr) return s;
        }
        return memo.computeIfAbsent(s, this::turn);
    }

    private BlockState turn(BlockState s) {
        // a full-looking block you walk through (a veil, a curtain of roots) stays: turned to concrete it would block the way
        if (s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).isEmpty()) return s;
        int rgb = s.getBlock().defaultMapColor().col;
        boolean lit = s.getLightEmission() > 0;
        if (way == Way.GREY) {
            if (lit) return Pal.RED;
            double l = lum(rgb);
            return Pal.GREYS[l > 200 ? 3 : l > 118 ? 2 : l > 52 ? 1 : 0];
        }
        int to = turn(rgb, 150);
        return lit ? nearest(Pal.LIGHTS, Pal.LIGHT_RGB, to) : nearest(Pal.SOLIDS, Pal.SOLID_RGB, to);
    }

    /** A colour turned {@code deg} round the hue wheel, its saturation lifted so even greys take the new hue a little. */
    static int turn(int rgb, double deg) {
        double r = (rgb >> 16 & 255) / 255.0, g = (rgb >> 8 & 255) / 255.0, b = (rgb & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        double h = d == 0 ? 0 : max == r ? ((g - b) / d) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
        double sat = max == 0 ? 0 : d / max, v = max;
        h = ((h * 60 + deg) % 360 + 360) % 360;
        sat = Math.min(1, sat * 1.2 + 0.2);
        double c = v * sat, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = v - c;
        double[] p = switch ((int) (h / 60)) {
            case 0 -> new double[]{c, x, 0};
            case 1 -> new double[]{x, c, 0};
            case 2 -> new double[]{0, c, x};
            case 3 -> new double[]{0, x, c};
            case 4 -> new double[]{x, 0, c};
            default -> new double[]{c, 0, x};
        };
        return (int) Math.round((p[0] + m) * 255) << 16 | (int) Math.round((p[1] + m) * 255) << 8 | (int) Math.round((p[2] + m) * 255);
    }

    static double lum(int rgb) {
        return 0.299 * (rgb >> 16 & 255) + 0.587 * (rgb >> 8 & 255) + 0.114 * (rgb & 255);
    }

    private static BlockState nearest(BlockState[] states, int[] rgbs, int rgb) {
        int best = 0;
        long bd = Long.MAX_VALUE;
        for (int i = 0; i < rgbs.length; i++) {
            long dr = (rgbs[i] >> 16 & 255) - (rgb >> 16 & 255), dg = (rgbs[i] >> 8 & 255) - (rgb >> 8 & 255), db = (rgbs[i] & 255) - (rgb & 255);
            long dd = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (dd < bd) {
                bd = dd;
                best = i;
            }
        }
        return states[best];
    }

    /** The blocks colours turn into, read once the blocks exist. */
    private static final class Pal {
        static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
                "cyan", "purple", "blue", "brown", "green", "red", "black"};
        static final BlockState[] GREYS = {GatePainter.state("minecraft:black_concrete"), GatePainter.state("minecraft:gray_concrete"),
                GatePainter.state("minecraft:light_gray_concrete"), GatePainter.state("minecraft:white_concrete")};
        static final BlockState RED = GatePainter.state("awaken:rare_red_light");
        static final BlockState[] SOLIDS = new BlockState[DYES.length * 2];
        static final int[] SOLID_RGB = new int[DYES.length * 2];
        static final BlockState[] LIGHTS = {GatePainter.state("minecraft:ochre_froglight"), GatePainter.state("minecraft:verdant_froglight"),
                GatePainter.state("minecraft:pearlescent_froglight"), GatePainter.state("minecraft:sea_lantern"),
                GatePainter.state("minecraft:shroomlight"), GatePainter.state("minecraft:glowstone")};
        static final int[] LIGHT_RGB = new int[LIGHTS.length];

        static {
            for (int i = 0; i < DYES.length; i++) {
                SOLIDS[i] = GatePainter.state("minecraft:" + DYES[i] + "_concrete");
                SOLIDS[DYES.length + i] = GatePainter.state("minecraft:" + DYES[i] + "_terracotta");
            }
            for (int i = 0; i < SOLIDS.length; i++) SOLID_RGB[i] = SOLIDS[i].getBlock().defaultMapColor().col;
            for (int i = 0; i < LIGHTS.length; i++) LIGHT_RGB[i] = LIGHTS[i].getBlock().defaultMapColor().col;
        }
    }
}
