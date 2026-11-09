package dev.awaken.hall.client;

import dev.awaken.armour.Archetype;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.ForgeSong;
import dev.awaken.armour.client.ArmourMesh;
import dev.awaken.armour.client.ArmourMeshes;
import dev.awaken.armour.client.FittingStandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** The terraces on the sheet (SPEC §52): six ranks of suits climbing away, staggered so each row looks between the last. */
final class TerraceLayout {
    private TerraceLayout() {}

    /** Each rank's terrace, E first: where its figures' feet stand, how tall they are, how far apart. */
    private static final int[][] TERRACE = {{940, 265, 322}, {786, 213, 299}, {646, 175, 278}, {520, 142, 238}, {408, 116, 202}, {312, 94, 170}};
    /** Each row's first figure: found by search so that a figure at the back is hidden by as little as possible (the rows zigzag). */
    private static final float[] FIRST_X = {371F, 255.6F, 431F, 378.8F, 342.8F, 513.2F};
    /** Stage pixels a block of figure takes, for a figure this tall. */
    private static final float SCALE = .482F;
    private static final Archetype[] ACROSS = {Archetype.STRIDER, Archetype.BULWARK, Archetype.CHANNELER, Archetype.DUELIST, Archetype.LORD};

    record Fig(ArmourSet set, int tier, int col, float x, float feet, float h) {
        float scale() {
            return h * SCALE;
        }

        /** Where the figure's middle (y = 0.5 in model space) stands. */
        float cy() {
            return feet - scale();
        }

        boolean hit(float px, float py) {
            return Math.abs(px - x) < h * .36F && py >= feet - h * 1.08F && py < feet + 6F;
        }
    }

    private static final Fig[] FIGS = build();
    private static final Map<ArmourSet, Fig> BY_SET = index();
    private static final Map<ArmourSet, float[]> BOUNDS = new EnumMap<>(ArmourSet.class);

    private static Fig[] build() {
        Fig[] out = new Fig[ArmourSet.values().length];
        int n = 0;
        for (ArmourSet s : ArmourSet.values()) {
            int tier = s.rank.ordinal(), col = column(s.archetype), slot = s.rank.ordinal() == 0 ? col : col;
            int[] t = TERRACE[tier];
            out[n++] = new Fig(s, tier, col, FIRST_X[tier] + slot * t[2], t[0], t[1]);
        }
        // the front rows first, so the nearest figure is the one a click finds
        java.util.Arrays.sort(out, java.util.Comparator.comparingInt(Fig::tier));
        return out;
    }

    private static Map<ArmourSet, Fig> index() {
        Map<ArmourSet, Fig> m = new EnumMap<>(ArmourSet.class);
        for (Fig f : FIGS) m.put(f.set, f);
        return m;
    }

    static int column(Archetype a) {
        for (int i = 0; i < ACROSS.length; i++) if (ACROSS[i] == a) return i;
        return 0;
    }

    static Fig[] figs() {
        return FIGS;
    }

    static Fig of(ArmourSet set) {
        return BY_SET.get(set);
    }

    /** The suit of this kind at this rank, or null (no Lord-forged at E). */
    static @Nullable ArmourSet set(int col, int tier) {
        for (Fig f : FIGS) if (f.col == col && f.tier == tier) return f.set;
        return null;
    }

    static String kind(int col) {
        return ACROSS[col].label;
    }

    static @Nullable Fig at(float x, float y) {
        for (Fig f : FIGS) if (f.hit(x, y)) return f;
        return null;
    }

    /** Where the line of your level runs: just above your rank's figures. */
    static int tide(int tier) {
        return TERRACE[tier][0] - TERRACE[tier][1] - 30;
    }

    static float letterX(int tier) {
        return Math.max(16F, FIRST_X[tier] - TERRACE[tier][1] * .42F - 42F);
    }

    static float letterY(int tier) {
        int[] t = TERRACE[tier];
        return t[0] - t[1] * .56F;
    }

    /** A suit's reach up and down in model space (y runs down): horns and crests go past the head, some capes past the heels. */
    static float[] bounds(ArmourSet set) {
        return BOUNDS.computeIfAbsent(set, TerraceLayout::measure);
    }

    private static float[] measure(ArmourSet set) {
        float y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        PoseStack ps = new PoseStack();
        Vector3f v = new Vector3f();
        for (var piece : ForgeSong.PIECES) {
            ArmourMesh[] bones = ArmourMeshes.piece(set, piece);
            if (bones == null) continue;
            for (ArmourMeshes.Bone b : ArmourMeshes.BONES) {
                ArmourMesh m = bones[b.ordinal()];
                if (m == null) continue;
                ps.pushPose();
                FittingStandRenderer.rest(ps, b);
                float[] p = m.pos();
                for (int i = 0, n = m.vertices(); i < n; i++) {
                    ps.last().pose().transformPosition(p[i * 3], p[i * 3 + 1], p[i * 3 + 2], v);
                    y0 = Math.min(y0, v.y);
                    y1 = Math.max(y1, v.y);
                }
                ps.popPose();
            }
        }
        return y0 > y1 ? new float[] {-.5F, 1.5F} : new float[] {y0, y1};
    }
}
