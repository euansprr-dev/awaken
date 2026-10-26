package dev.awaken.village.road;

import dev.awaken.city.SettlementPaint;
import dev.awaken.village.plan.VillageBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * A waymarker as built (SPEC §28.9): a post of the land's stripped timber on a stone foot, a lantern on its top, and an
 * arm for each way: a hanging sign out of the post's side that way, readable from either side as you come along the
 * road, with the place's name and how far it is (none on the arm for the place you're at). At the pass, a cairn of
 * field stones beside it.
 */
final class RoadMarks {
    private RoadMarks() {}

    static final String[] DIR = {"north", "east", "south", "west"};
    static final int[] SX = {0, 1, 0, -1}, SZ = {-1, 0, 1, 0};

    /** The post, its lantern, its arms' boards (their words come after the chunk is written) and a cairn at the pass. */
    static void post(RoadPaint l, SettlementPaint.Canvas c, Road.Marker m) {
        int x = m.x() - l.ox, z = m.z() - l.oz, g = m.y() - l.baseY;
        String w = RoadPaint.wood(woodFamily(l, m));
        if (c.contains(x, z)) {
            c.set(x, g, z, RoadPaint.pct(m.x(), m.z(), 61) < 50 ? RoadPaint.COBBLE : RoadPaint.MOSSY_COBBLE);
            c.plate(x, z);
            c.column(x, g + 1, g + 3, z, VillageBlocks.of("minecraft:stripped_" + RoadPaint.log(w) + "[axis=y]"));
            c.set(x, g + 4, z, RoadPaint.LANTERN);
            c.column(x, g + 5, g + 6, z, RoadPaint.AIR);
        }
        for (Road.Arm a : m.arms()) {
            int sx = x + SX[a.side()], sz = z + SZ[a.side()];
            if (!c.contains(sx, sz)) continue;
            c.set(sx, g + 3, sz, VillageBlocks.of("minecraft:" + w + "_wall_hanging_sign[facing=" + DIR[(a.side() + 1) & 3] + "]"));
            c.column(sx, g + 1, g + 2, sz, RoadPaint.AIR);
        }
        if (m.cairn()) {
            // field stones heaped on the side away from the road
            int[][] stones = {{1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {1, 1, 1}, {-1, 1, 1}, {0, 2, 1}};
            int wi = Math.clamp(m.point() - l.w0, 0, l.xs.length - 1);
            // away from the road: the post's side of the line
            double fx = m.x() + 0.5 - l.xs[wi], fz = m.z() + 0.5 - l.zs[wi];
            int ax = Math.abs(fx) >= Math.abs(fz) ? (int) Math.signum(fx) : 0, az = Math.abs(fx) >= Math.abs(fz) ? 0 : (int) Math.signum(fz);
            for (int[] s : stones) {
                int px = x + s[0] * (az != 0 ? 1 : 0) + s[2] * ax * 2, pz = z + s[0] * (ax != 0 ? 1 : 0) + s[2] * az * 2;
                if (!c.contains(px, pz) || px == x && pz == z) continue;
                int roll = RoadPaint.pct(l.ox + px, l.oz + pz, 62 + s[1]);
                BlockState st = s[1] == 2 ? RoadPaint.STONE_SLAB : roll < 40 ? RoadPaint.MOSSY_COBBLE : roll < 75 ? RoadPaint.COBBLE : RoadPaint.STONE;
                c.set(px, g + s[1] - 1, pz, st == RoadPaint.STONE_SLAB ? RoadPaint.COBBLE : st);
                if (s[1] == 2) c.set(px, g + 1, pz, RoadPaint.STONE_SLAB);
                c.plate(px, pz);
            }
        }
    }

    /** The land's family where the marker stands (its timber). */
    static int woodFamily(RoadPaint l, Road.Marker m) {
        return l.fam[Math.clamp(m.point() - l.w0, 0, l.fam.length - 1)];
    }

    /** After the chunk is written: each arm's words, the same on both faces. */
    static void words(ChunkAccess chunk, RoadPaint l, Road.Marker m) {
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        String w = RoadPaint.wood(woodFamily(l, m));
        DyeColor ink = w.equals("dark_oak") || w.equals("mangrove") || w.equals("spruce") || w.equals("jungle") ? DyeColor.WHITE : DyeColor.BLACK;
        for (Road.Arm a : m.arms()) {
            BlockPos pos = new BlockPos(m.x() + SX[a.side()], m.y() + 3, m.z() + SZ[a.side()]);
            if (pos.getX() >> 4 != cx || pos.getZ() >> 4 != cz) continue;
            if (!(chunk.getBlockEntity(pos) instanceof SignBlockEntity sign)) continue;
            SignText text = text(a, ink);
            sign.setText(text, SignTextSlot.FRONT);
            sign.setText(text, SignTextSlot.BACK);
            sign.setWaxed(true);
        }
    }

    /** An arm's four lines: the name (split where it's too long for the board), how far, centred on the board. */
    static SignText text(Road.Arm a, DyeColor ink) {
        String[] name = split(a.name());
        String far = a.distance() <= 0 ? "" : distance(a.distance());
        String[] lines = new String[4];
        java.util.Arrays.fill(lines, "");
        if (name.length == 1) {
            lines[1] = name[0];
            lines[2] = far;
        } else {
            lines[0] = name[0];
            lines[1] = name[1];
            lines[2] = far;
        }
        SignText.Mutable t = SignText.EMPTY.asMutable().setColor(ink);
        for (int i = 0; i < 4; i++) t.setLine(i, Component.literal(lines[i]));
        return t.asImmutable();
    }

    /** "320 blocks", "1.2k blocks" (the board is narrow). */
    static String distance(int blocks) {
        int r = blocks < 1000 ? Math.max(10, Math.round(blocks / 10f) * 10) : 0;
        return blocks < 1000 ? r + " blocks" : String.format(java.util.Locale.ROOT, "%.1fk blocks", blocks / 1000f);
    }

    /** The default font's advance widths for what names hold (pixels; a hanging sign's board takes 60). */
    static int width(String s) {
        int w = 0;
        for (char ch : s.toCharArray()) {
            w += switch (ch) {
                case 'i', '!', '.', ',', ':', ';', '\'', '|' -> 2;
                case 'l' -> 3;
                case 't', 'I', ' ', '[', ']' -> 4;
                case 'f', 'k', '<', '>', '(', ')' -> 5;
                default -> 6;
            };
        }
        return w;
    }

    /** A name too wide for the board, broken where its parts meet (a capital, a space, "-"), else near the middle. */
    static String[] split(String name) {
        if (width(name) <= 58) return new String[]{name};
        int sp = name.lastIndexOf(' ', name.length() / 2 + 4);
        if (sp > 0) return new String[]{name.substring(0, sp), name.substring(sp + 1)};
        // the grid's names join a first part and an ending: break after the first part when it's one we know
        for (String first : FIRSTS) {
            if (name.startsWith(first) && name.length() > first.length() + 1) return new String[]{first + "-", name.substring(first.length())};
        }
        int mid = name.length() / 2;
        return new String[]{name.substring(0, mid) + "-", name.substring(mid)};
    }

    private static final String[] FIRSTS = {"Hollow", "Bright", "Kettle", "Thorn", "Elder", "Brook", "Hazel", "Stone", "Alder", "Green", "Cross",
            "Bram", "Wren", "Mill", "Rook", "Cold", "Fair", "Long", "Mere", "Lark", "Wick", "Bell", "Ash", "Fen", "Oak", "Low"};
}
