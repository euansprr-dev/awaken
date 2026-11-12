package dev.awaken.gates.realms.check;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.Piece;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.kit.piece.Hops;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A land seen from straight above, for whoever designs its run by hand (SPEC §50.9 "Open lands, fixed"): drawn exactly as the
 * game draws it, each column its top block's map colour shaded by height, red where something built stands over the ground,
 * with a grid every 16 blocks (labelled every 64), the sites, the run's legs and their exits, the stones to jump along and
 * the Lord's door. Written beside a text list of every site and leg. Dev tooling: tests call it, nothing in the game does.
 */
public final class LandMap {
    private LandMap() {}

    /** Pixels a block. */
    private static final int S = 2;

    public static void write(Land land, Path dir, String name) throws IOException {
        write(land, dir, name, false);
        // and the floor under any roof or crown: the top block no more than 4 over the ground (a temple under its tree)
        write(land, dir, name + "_floor", true);
    }

    private static void write(Land land, Path dir, String name, boolean floor) throws IOException {
        Files.createDirectories(dir);
        int half = land.halfSize(), n = 2 * half + 1, min = -half;
        int[] top = new int[n * n], colour = new int[n * n];
        int cx0 = min >> 4, cx1 = half >> 4, w = cx1 - cx0 + 1;
        java.util.stream.IntStream.range(0, w * w).parallel().forEach(i -> {
            int cx = cx0 + i % w, cz = cx0 + i / w;
            V v = new V(cx * 16, cz * 16);
            land.paint(v);
            for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                if (x < min || x > half || z < min || z > half) continue;
                int y = floor ? Math.min(V.H - 1, land.ground(x, z) + 4) : V.H - 1;
                BlockState s = null;
                for (; y >= 0; y--) if ((s = v.get(x, y, z)) != null && !s.isAir()) break;
                int k = (x - min) + (z - min) * n;
                top[k] = y;
                colour[k] = y < 0 ? 0x000000 : mapColour(s);
            }
        });
        BufferedImage img = new BufferedImage(n * S, n * S, BufferedImage.TYPE_INT_RGB);
        for (int z = min; z <= half; z++) for (int x = min; x <= half; x++) {
            int k = (x - min) + (z - min) * n, y = top[k], c = colour[k];
            double shade = 0.55 + 0.45 * Math.max(0, Math.min(1, (y - 40) / 140.0));
            // light from the north-west: a step down to the south-east reads as a shadow
            int prev = x > min && z > min ? top[k - 1 - n] : y;
            shade *= y > prev ? 1.12 : y < prev ? 0.85 : 1.0;
            int r = (int) Math.min(255, (c >> 16 & 255) * shade), g = (int) Math.min(255, (c >> 8 & 255) * shade), b = (int) Math.min(255, (c & 255) * shade);
            if (y > land.ground(x, z) + 2) { // built over the ground: no stone or camp here
                r = (r + 255 * 2) / 3;
                g = g * 2 / 3;
                b = b * 2 / 3;
            }
            int rgb = r << 16 | g << 8 | b;
            for (int a = 0; a < S; a++) for (int bb = 0; bb < S; bb++) img.setRGB((x - min) * S + a, (z - min) * S + bb, rgb);
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        } catch (RuntimeException ignored) {
        }
        for (int c = (min / 16) * 16; c <= half; c += 16) {
            boolean major = c % 64 == 0;
            g.setColor(new Color(0, 0, 0, major ? 110 : 40));
            g.drawLine(px(c, min), 0, px(c, min), n * S);
            g.drawLine(0, px(c, min), n * S, px(c, min));
            if (major) label(g, String.valueOf(c), px(c, min) + 3, 14, Color.WHITE);
            if (major) label(g, String.valueOf(c), 3, px(c, min) - 3, Color.WHITE);
        }
        // the Lord's door
        g.setColor(new Color(255, 40, 40));
        for (Land.Edit e : land.change("heart_open")) g.fillRect(px(e.x(), min), px(e.z(), min), S, S);
        // the stones to jump along
        for (Piece<?> p : land.pieces()) {
            if (!(p instanceof Hops h)) continue;
            for (Hops.Stone s : h.stones) {
                g.setColor(new Color(255, 230, 40));
                g.fillOval(px(s.x() - s.r(), min), px(s.z() - s.r(), min), (2 * s.r() + 1) * S, (2 * s.r() + 1) * S);
            }
        }
        // the run: each leg and where it leads
        Map<String, Land.Leg> byId = new HashMap<>();
        for (Land.Leg l : land.route()) byId.put(l.id(), l);
        g.setStroke(new BasicStroke(2f));
        for (Land.Leg l : land.route()) for (String to : l.exits()) {
            Land.Leg t = byId.get(to);
            if (t == null) continue;
            g.setColor(new Color(60, 230, 255, 200));
            g.drawLine(px(l.x(), min), px(l.z(), min), px(t.x(), min), px(t.z(), min));
        }
        for (Land.Leg l : land.route()) {
            g.setColor(new Color(60, 230, 255));
            g.fillOval(px(l.x(), min) - 5, px(l.z(), min) - 5, 10, 10);
            label(g, l.id(), px(l.x(), min) + 7, px(l.z(), min) - 6, new Color(160, 245, 255));
        }
        // the sites
        for (Land.Site s : land.sites()) {
            Color c = switch (s.kind()) {
                case MOUTH -> Color.WHITE;
                case DEN -> new Color(255, 150, 40);
                case WARDEN -> new Color(255, 60, 255);
                case LORD -> new Color(255, 30, 30);
                default -> new Color(140, 255, 140);
            };
            int r = Math.max(4, s.radius()) * S;
            g.setColor(c);
            g.setStroke(new BasicStroke(2f));
            g.drawOval(px(s.x(), min) - r, px(s.z(), min) - r, 2 * r, 2 * r);
            label(g, s.kind().name().charAt(0) + "" + s.index(), px(s.x(), min) - 8, px(s.z(), min) + 5, c);
        }
        g.dispose();
        ImageIO.write(img, "png", dir.resolve(name + ".png").toFile());

        StringBuilder t = new StringBuilder(name + ": half " + half + ", water " + land.water() + ", designed " + land.designed() + "\nsites:\n");
        for (Land.Site s : land.sites()) t.append(String.format(Locale.ROOT, "  %s %d at %d %d %d r%d %s (ground %d, edge %.0f)%n", s.kind(), s.index(), s.x(), s.y(), s.z(), s.radius(), s.what(),
                land.ground(s.x(), s.z()), land.edge(s.x(), s.z())));
        t.append("legs:\n");
        for (Land.Leg l : land.route()) t.append(String.format(Locale.ROOT, "  %s %s at %d %d %d -> %s%n", l.id(), l.kind(), l.x(), l.y(), l.z(), l.exits()));
        t.append("pieces (label: box):\n");
        for (Piece<?> p : land.pieces()) if (p.x1 - p.x0 < 2 * half) t.append(String.format(Locale.ROOT, "  %s: %d %d .. %d %d%n", p.label, p.x0, p.z0, p.x1, p.z1));
        if (!floor) Files.writeString(dir.resolve(name + ".txt"), t.toString());
    }

    private static int px(int c, int min) {
        return (c - min) * S;
    }

    private static void label(Graphics2D g, String s, int x, int y, Color c) {
        try {
            g.setColor(new Color(0, 0, 0, 170));
            g.drawString(s, x + 1, y + 1);
            g.setColor(c);
            g.drawString(s, x, y);
        } catch (RuntimeException ignored) { // no fonts on this machine: the map without its words
        }
    }

    private static int mapColour(BlockState s) {
        try {
            if (s.is(Blocks.WATER)) return 0x3F76E4;
            int c = s.getMapColor(null, BlockPos.ZERO).col;
            return c == 0 ? 0x808080 : c;
        } catch (RuntimeException e) {
            return 0x808080;
        }
    }
}
