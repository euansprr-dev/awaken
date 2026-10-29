package dev.awaken.armour.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.armour.client.ArmourMeshes.Bone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/** Client entry of the armour (docs/armour/PLAN.md): its meshes, its shader, its layer, the gauntlet in first person. */
public final class ArmourClient {
    private ArmourClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("armour_meshes"),
                (ResourceManagerReloadListener) ArmourMeshes::reload));
        modBus.addListener(RegisterRenderPipelinesEvent.class, ArmourPipelines::onRegister);
        modBus.addListener(ArmourClient::onAddLayers);
        modBus.addListener((RegisterRenderStateModifiersEvent e) -> e.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {}, ArmourClient::readWorn));
        NeoForge.EVENT_BUS.addListener(RenderArmEvent.class, ArmourClient::onArm);
    }

    /** Our pieces each slot wears, by {@link ArmorType} ordinal (null where none), for {@link ArmourLayer}. */
    static final ContextKey<ItemStack[]> WORN = new ContextKey<>(Awaken.id("armour_worn"));

    private static final ArmorType[] SLOTS = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

    /**
     * Vanilla hands its layers only armour with an equipment asset of its own (HumanoidMobRenderer's
     * getEquipmentIfRenderable), and ours has none so that vanilla's shell stays off: our pieces are read from the entity
     * here instead. A helm of ours is not drawn again as a hat item on the head, and a set's own cape hangs where
     * vanilla's would, so vanilla's is not drawn over it.
     */
    private static void readWorn(LivingEntity entity, LivingEntityRenderState state) {
        ItemStack[] worn = null;
        for (ArmorType slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot.getSlot());
            ArmourLooks.Look look = ArmourLooks.of(stack);
            if (look == null || look.piece() != slot) continue;
            if (worn == null) worn = new ItemStack[ArmorType.values().length];
            worn[slot.ordinal()] = stack;
        }
        if (worn == null) return;
        state.setRenderData(WORN, worn);
        if (worn[ArmorType.HELMET.ordinal()] != null) state.headItem.clear();
        if (state instanceof AvatarRenderState a && hasCape(worn[ArmorType.CHESTPLATE.ordinal()])) a.showCape = false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onAddLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerModelType type : e.getSkins()) {
            AvatarRenderer<?> player = e.getPlayerRenderer(type);
            if (player != null) ((AvatarRenderer) player).addLayer(new ArmourLayer<>(player));
            AvatarRenderer<?> mannequin = e.getMannequinRenderer(type);
            if (mannequin != null) ((AvatarRenderer) mannequin).addLayer(new ArmourLayer<>(mannequin));
        }
        ArmorStandRenderer stand = e.getRenderer(EntityTypes.ARMOR_STAND);
        if (stand != null) stand.addLayer(new ArmourLayer<>(stand));
    }

    private static boolean hasCape(@Nullable ItemStack chest) {
        if (chest == null) return false;
        ArmourLooks.Look look = ArmourLooks.of(chest);
        if (look == null || look.piece() != ArmorType.CHESTPLATE) return false;
        ArmourMesh[][] set = ArmourMeshes.of(look.set());
        return set != null && set[ArmorType.CHESTPLATE.ordinal()][Bone.CAPE0.ordinal()] != null;
    }

    /**
     * Vanilla's empty hand in first person: the chestplate's arm mesh on the arm as vanilla poses it there (its rest
     * pose, turned a tenth of a radian out), drawn before vanilla's own arm, which it covers.
     */
    private static void onArm(RenderArmEvent e) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ArmourLooks.Look look = ArmourLooks.of(chest);
        if (look == null || look.piece() != ArmorType.CHESTPLATE) return;
        ArmourMesh[][] set = ArmourMeshes.of(look.set());
        if (set == null) return;
        boolean right = e.getArm() == HumanoidArm.RIGHT;
        ArmourMesh mesh = set[ArmorType.CHESTPLATE.ordinal()][(right ? Bone.RIGHT_ARM : Bone.LEFT_ARM).ordinal()];
        if (mesh == null) return;
        PoseStack ps = e.getPoseStack();
        ModelPart arm = e.getArmPart();
        ps.pushPose();
        arm.resetPose();
        arm.zRot = right ? 0.1F : -0.1F;
        arm.translateAndRotate(ps);
        ArmourLayer.draw(e.getSubmitNodeCollector(), ps, mesh, look.set(), e.getLightCoords(), chest.hasFoil());
        ps.popPose();
    }
}
