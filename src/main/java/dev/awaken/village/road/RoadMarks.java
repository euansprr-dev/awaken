package dev.awaken.village.road;

import dev.awaken.city.SettlementPaint;
import dev.awaken.village.plan.VillageBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

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

    /**
     * After the chunk is written: each arm's words, the same on both faces, loaded into the sign the way a structure
     * template loads its blocks' data (in worldgen the sign has no level yet, and {@code setText} would tell it).
     */
    static void words(ChunkAccess chunk, HolderLookup.Provider registries, RoadPaint l, Road.Marker m) {
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        String w = RoadPaint.wood(woodFamily(l, m));
        DyeColor ink = w.equals("dark_oak") || w.equals("mangrove") || w.equals("spruce") || w.equals("jungle") ? DyeColor.WHITE : DyeColor.BLACK;
        for (Road.Arm a : m.arms()) {
            BlockPos pos = new BlockPos(m.x() + SX[a.side()], m.y() + 3, m.z() + SZ[a.side()]);
            if (pos.getX() >> 4 != cx || pos.getZ() >> 4 != cz) continue;
            if (!(chunk.getBlockEntity(pos) instanceof SignBlockEntity sign)) continue;
            write(sign, registries, text(a, ink));
        }
    }

    /** The same words on both faces, waxed; never touches the sign's level (it has none in worldgen). */
    static void write(SignBlockEntity sign, HolderLookup.Provider registries, SignText text) {
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        out.store("front_text", SignText.CODEC, text);
        out.store("back_text", SignText.CODEC, text);
        out.putBoolean("is_waxed", true);
        sign.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, out.buildResult()));
    }

    /** An arm's four lines: the name (split where it's too long for the board), how far, centred on the board. */
    static SignText text(Road.Arm a, DyeColor ink) {
        String[] name = split(a.name());
        String far = a.distance() <= 0 ? "" : distance(a.distance());
        String[] lines = new String[4];
        java.util.Arrays.fill(lines, "");
        // one line in the middle of the board, two from the top, three filling it; the distance under the name
        int top = name.length == 1 ? 1 : 0;
        for (int i = 0; i < name.length; i++) lines[top + i] = name[i];
        lines[Math.min(3, top + name.length)] = far;
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

    /**
     * A name on the board's lines (at most three, each within its {@link #LINE} pixels: a wider line loses its end): word by
     * word, and a word too wide on its own broken where its parts meet (a first part we know), else near its middle.
     */
    static String[] split(String name) {
        if (width(name) <= LINE) return new String[]{name};
        java.util.List<String> words = new java.util.ArrayList<>();
        for (String w : name.split(" ")) {
            if (width(w) <= LINE) {
                words.add(w);
                continue;
            }
            String first = null;
            for (String f : FIRSTS) if (w.startsWith(f) && w.length() > f.length() + 1) first = f;
            String rest = w;
            if (first != null) {
                words.add(first + "-");
                rest = w.substring(first.length());
            }
            // what still won't fit, as much as fits each line
            while (width(rest) > LINE) {
                int cut = rest.length() - 1;
                while (cut > 1 && width(rest.substring(0, cut) + "-") > LINE) cut--;
                words.add(rest.substring(0, cut) + "-");
                rest = rest.substring(cut);
            }
            words.add(rest);
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            // a broken word's parts join again where they share a line
            String next = line.isEmpty() ? w : line.charAt(line.length() - 1) == '-' ? line.substring(0, line.length() - 1) + w : line + " " + w;
            if (!line.isEmpty() && width(next) > LINE) {
                out.add(line.toString());
                line = new StringBuilder(w);
            } else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) out.add(line.toString());
        while (out.size() > 3) {
            out.set(1, out.get(1) + " " + out.get(2));
            out.remove(2);
        }
        return out.toArray(String[]::new);
    }

    /** How wide a line of the board may be. */
    static final int LINE = 58;

    private static final String[] FIRSTS = {"Hollow", "Bright", "Kettle", "Thorn", "Elder", "Brook", "Hazel", "Stone", "Alder", "Green", "Cross",
            "Bram", "Wren", "Mill", "Rook", "Cold", "Fair", "Long", "Mere", "Lark", "Wick", "Bell", "Ash", "Fen", "Oak", "Low"};
}
