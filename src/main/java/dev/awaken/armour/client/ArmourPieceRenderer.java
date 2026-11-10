package dev.awaken.armour.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.Forged;
import dev.awaken.armour.client.ArmourMeshes.Bone;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A piece of armour seen in the world as the piece itself (SPEC §44.11): in a hand, lying on the ground, in a frame, on a
 * shelf. Its own meshes, each bone at rest as on the Fitting Stand's form, through the armour shader, with what the piece
 * carries (mastery, accent, enchantment light, forge marks). The inventory keeps the flat icon. Item models name it as
 * {@code {"type": "awaken:armour_piece", "set": "<set>", "piece": "helmet|chestplate|leggings|boots"}}.
 */
final class ArmourPieceRenderer implements SpecialModelRenderer<ArmourPieceRenderer.State> {
    static final Identifier TYPE = Awaken.id("armour_piece");

    /** What a stack carries that shows: its mastery step, accent (0 the set's own), enchantment light and forge marks. */
    record State(int mastery, int accent, int glow, int marks) {}

    private final ArmourSet set;
    private final ArmorType piece;
    /** The middle of the piece on the figure (model texels down from the shoulders) and how much larger it is drawn, so
     * each piece fills about a block and the item's display transforms treat it as one. */
    private final float centre, scale;

    private ArmourPieceRenderer(ArmourSet set, ArmorType piece) {
        this.set = set;
        this.piece = piece;
        switch (piece) {
            case HELMET -> {
                centre = -4.5F;
                scale = 1.7F;
            }
            case CHESTPLATE -> {
                centre = 6F;
                scale = 0.95F;
            }
            case LEGGINGS -> {
                centre = 16.5F;
                scale = 1.15F;
            }
            default -> {
                centre = 21F;
                scale = 1.6F;
            }
        }
    }

    @Override
    public void submit(@Nullable State state, PoseStack ps, SubmitNodeCollector out, int light, int overlay, boolean foil, int outlineColor) {
        ArmourMesh[] bones = ArmourMeshes.piece(set, piece);
        if (bones == null) return;
        int mastery = state == null ? 0 : state.mastery(), accent = state == null ? 0 : state.accent();
        int glow = state == null ? 0 : state.glow(), marks = state == null ? 0 : state.marks();
        ArmourMesh[] awake = mastery >= ArmourLayer.AWAKENED ? ArmourMeshes.awakened(set, piece) : null;
        ps.pushPose();
        ps.translate(0.5F, 0.5F, 0.5F);
        ps.scale(-scale, -scale, scale); // the model's y runs down, as on a figure
        ps.translate(0F, -centre / 16F, 0F);
        for (Bone b : ArmourMeshes.BONES) {
            ArmourMesh mesh = bones[b.ordinal()], extra = awake == null ? null : awake[b.ordinal()];
            if (mesh == null && extra == null) continue;
            ps.pushPose();
            FittingStandRenderer.rest(ps, b);
            if (mesh != null) ArmourLayer.draw(out, ps, mesh, set, light, foil, 0, 0, -1, mastery, false, accent, glow, marks);
            if (extra != null) ArmourLayer.draw(out, ps, extra, set, light, foil, 0, 0, -1, mastery, false, accent, glow, marks);
            ps.popPose();
        }
        ps.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int k = 0; k < 8; k++) output.accept(new Vector3f((k & 1) * 1.4F - 0.2F, (k >> 1 & 1) * 1.4F - 0.2F, (k >> 2 & 1) * 1.4F - 0.2F));
    }

    // a few states kept, so a piece in view makes nothing each frame
    private static final State[] KEPT = new State[32];

    @Override
    public @Nullable State extractArgument(ItemStack stack) {
        Forged f = stack.get(ArmourContent.FORGED.get());
        int mastery = f == null ? 0 : f.masteryStep(), accent = f == null ? 0 : f.accent();
        int glow = dev.awaken.core.api.EnchantApi.glowBits(stack), marks = ArmourLayer.marks(f, set);
        if (mastery == 0 && accent == 0 && glow == 0 && marks == 0) return null;
        int slot = (mastery * 31 + accent * 17 + glow * 7 + marks) & (KEPT.length - 1);
        State s = KEPT[slot];
        if (s == null || s.mastery() != mastery || s.accent() != accent || s.glow() != glow || s.marks() != marks)
            KEPT[slot] = s = new State(mastery, accent, glow, marks);
        return s;
    }

    record Unbaked(String set, String piece) implements SpecialModelRenderer.Unbaked<State> {
        static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("set").forGetter(Unbaked::set), Codec.STRING.fieldOf("piece").forGetter(Unbaked::piece)).apply(i, Unbaked::new));

        @Override
        public @Nullable SpecialModelRenderer<State> bake(SpecialModelRenderer.BakingContext context) {
            ArmourSet s = ArmourSet.byId(set);
            ArmorType type = null;
            for (ArmorType t : ArmorType.values()) if (t.getName().equals(piece)) type = t;
            if (s == null || type == null) {
                Awaken.LOG.error("[armour] an item model names the piece {} of {}, which is neither", piece, set);
                return null;
            }
            return new ArmourPieceRenderer(s, type);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
