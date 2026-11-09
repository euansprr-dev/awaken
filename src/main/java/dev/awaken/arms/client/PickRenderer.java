package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.arms.WeaponMesh;
import dev.awaken.delve.forged.ForgedPicks;
import dev.awaken.delve.forged.PickForged;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A forged pick in the hands, on a back, on a stand (SPEC §49): its mesh through the weapon shader, carrying what was done
 * to it: the temper colour, the wear of the hands and the rock, a notch for each ten lodes' hearts, and a ring for every
 * rank it was really raised through. Which end of the head leads depends on the hold ({@code plus_leads}: the item
 * JSON says): a Breaker's and a Sounder's working point always leads, a Mattock's turns in the hand to suit what you're
 * looking at ({@link PickTurn}). Item models name it as {@code {"type": "awaken:pick", "pick": "<id>"}}.
 */
final class PickRenderer implements SpecialModelRenderer<PickRenderer.State> {
    static final Identifier TYPE = Awaken.id("pick");

    /** UV1 as the shader takes it (without foil), the rings' first group, whose hand it is (-1 none) and its form. */
    record State(int x, int y, int rings, int holder) {}

    private final String pick;
    private final Identifier texture;
    private final boolean plusLeads;
    private final boolean mattock, working;

    private PickRenderer(String pick, boolean plusLeads) {
        this.pick = pick;
        this.plusLeads = plusLeads;
        this.texture = Awaken.id("textures/item/arms/" + pick + ".png");
        this.mattock = pick.endsWith("_mattock");
        this.working = pick.endsWith("_breaker") || pick.endsWith("_sounder");
    }

    /** Where the fist closes (item space) and the way the haft runs: a turn about it leaves the fist where it is. */
    private static final float GRIP = 3F / 16F, MID = 0.5F, AXIS = Mth.SQRT_OF_TWO / 2;
    /** How far up the haft the toss carries it (item space, about half the fist's reach). */
    private static final float TOSS = 0.5F;
    private static final Quaternionf TURN = new Quaternionf();

    @Override
    public void submit(@Nullable State state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outlineColor) {
        WeaponMesh mesh = WeaponMeshes.get(pick);
        if (mesh == null) return;
        boolean ours = WeaponPipelines.ok();
        int y = state == null ? PICK : state.y();
        int packed = ours ? (state == null ? 0 : state.x()) | (y | (foil ? 256 : 0)) << 16 : overlay;
        int rings = state == null ? 0 : state.rings();
        int holder = state == null ? -1 : state.holder();
        // the end that should lead: +x half-turns even, -x odd; the toss only when in the hand
        float halves = mattock ? PickTurn.halves(holder) : working ? 1F : 0F;
        if (!plusLeads) halves += 1F;
        float toss = mattock ? PickTurn.toss(holder) : 0F;
        boolean turned = toss > 0F || Mth.positiveModulo(halves, 2F) != 0F;
        if (turned) {
            poseStack.pushPose();
            if (toss > 0F) poseStack.translate(toss * TOSS * AXIS, toss * TOSS * AXIS, 0F);
            TURN.setAngleAxis(halves * Mth.PI, AXIS, AXIS, 0F);
            poseStack.rotateAround(TURN, GRIP, GRIP, MID);
        }
        collector.submitCustomGeometry(poseStack, ours ? WeaponPipelines.weapon(texture) : WeaponPipelines.fallback(texture),
                (pose, buffer) -> WeaponRenderer.emit(mesh, pose, buffer, light, ours ? packed : OverlayTexture.NO_OVERLAY, ours, 0, rings));
        if (ours && !mesh.anchors().isEmpty()) collector.submitCustomGeometry(poseStack, WeaponPipelines.glow(), (pose, buffer) -> WeaponRenderer.halos(mesh, pose, buffer, 0F, 0F));
        if (turned) poseStack.popPose();
    }

    /** y's top bit: the shader's pick mode. */
    private static final int PICK = 1 << 15, KEEN = 1 << 15;

    @Override
    public State extractArgument(ItemStack stack) {
        PickForged d = ForgedPicks.data(stack);
        int colour = Math.round(Mth.clamp(d.colour(), 0F, 1F) * 255);
        int max = stack.getMaxDamage();
        int wear = max <= 0 ? 0 : Math.min(255, stack.getDamageValue() * 255 / max);
        int notches = Math.min(6, d.tally().hearts() / 10);
        int x = colour | (dev.awaken.core.api.EnchantApi.glowBits(stack) & 127) << 8 | (d.tally().keen() > 0 ? KEEN : 0);
        int y = wear | notches << 11 | PICK;
        return new State(x, y, d.from(), holderOf(stack));
    }

    /** Whose main hand this very stack is in, -1 for nobody's (a frame, a stand, the off hand). */
    private static int holderOf(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1;
        if (mc.player != null && mc.player.getMainHandItem() == stack) return mc.player.getId();
        for (var o : mc.level.players()) if (o.getMainHandItem() == stack) return o.getId();
        return -1;
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        WeaponMesh mesh = WeaponMeshes.get(pick);
        float[] b = mesh == null ? new float[]{0, 0, 0, 1, 1, 1} : mesh.bounds();
        // the turn sweeps the head round the haft, and the toss lifts it
        float r = TOSS + 0.5F;
        for (int k = 0; k < 8; k++) output.accept(new Vector3f(b[(k & 1) * 3] + ((k & 1) == 0 ? -r : r), b[1 + (k >> 1 & 1) * 3] + ((k >> 1 & 1) == 0 ? -r : r), b[2 + (k >> 2 & 1) * 3] + ((k >> 2 & 1) == 0 ? -r : r)));
    }

    record Unbaked(String pick, boolean plusLeads) implements SpecialModelRenderer.Unbaked<State> {
        static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("pick").forGetter(Unbaked::pick),
                Codec.BOOL.optionalFieldOf("plus_leads", false).forGetter(Unbaked::plusLeads)).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<State> bake(SpecialModelRenderer.BakingContext context) {
            return new PickRenderer(pick, plusLeads);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
