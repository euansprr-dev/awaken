package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.FpSwings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * A weapon in each hand (SPEC §25 "Forms": the Daggers): the off hand, left empty, holds the main hand's twin, drawn
 * mirrored in first person (on Hands' off-hand pass, after its moves, so the off hand's strikes move it) and in the
 * other hand seen from outside. One item: the twin is a copy that is drawn, never held, and it lays the ribbon only
 * while the off hand strikes.
 */
final class ArmsOffHand {
    private ArmsOffHand() {}

    /** Each held pair's twin (a copy of the main-hand stack, kept while that stack lives; ItemStack is compared as itself). */
    private static final java.util.Map<ItemStack, ItemStack> TWINS = new java.util.WeakHashMap<>();
    private static final ItemStackRenderState FIRST = new ItemStackRenderState();

    /** The twin of a holder's main-hand stack, if its style is a pair and the off hand is free (else empty). */
    static ItemStack twinOf(Player p) {
        ItemStack main = p.getMainHandItem();
        if (main.isEmpty() || !p.getOffhandItem().isEmpty()) return ItemStack.EMPTY;
        Archetypes.Loaded style = Arms.styleOf(main);
        FpSwings.Style st = style == null ? null : FpSwings.style(style.id());
        if (st == null || !st.twin()) return ItemStack.EMPTY;
        return TWINS.computeIfAbsent(main, ItemStack::copy);
    }

    /** First person: the twin in the empty off hand, after Hands has moved it (registered after Anims, LOWEST). */
    static void onRenderHand(RenderHandEvent e) {
        if (e.getHand() != InteractionHand.OFF_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson() || p.isScoping() || !ArmsPredict.fights(p)) return;
        ItemStack t = twinOf(p);
        if (t.isEmpty()) return;
        HumanoidArm arm = p.getMainArm().getOpposite();
        int inv = arm == HumanoidArm.RIGHT ? 1 : -1;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(inv * 0.56F, -0.52F, -0.72F); // vanilla's resting hand, on the off side
        mc.getItemModelResolver().updateForLiving(FIRST, t, arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, p);
        FIRST.submit(ps, e.getSubmitNodeCollector(), e.getPackedLight(), OverlayTexture.NO_OVERLAY, 0);
        ps.popPose();
    }

    /** From outside: the twin in the other hand of anyone holding a pair (a render-state modifier, ArmsFeel's). */
    static void modify(Entity entity, EntityRenderState state) {
        if (!(entity instanceof Player p) || !(state instanceof ArmedEntityRenderState armed)) return;
        ItemStack t = twinOf(p);
        if (t.isEmpty()) return;
        boolean leftOff = p.getMainArm() == HumanoidArm.RIGHT;
        Minecraft.getInstance().getItemModelResolver().updateForLiving(leftOff ? armed.leftHandItemState : armed.rightHandItemState, t,
                leftOff ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, p);
        if (leftOff) armed.leftHandItemStack = t;
        else armed.rightHandItemStack = t;
    }
}
