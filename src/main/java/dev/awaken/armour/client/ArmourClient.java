package dev.awaken.armour.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.armour.client.ArmourMeshes.Bone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
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

/** Client entry of the armour (docs/armour/PLAN.md): its meshes, its shader, its layer, the gauntlet in first person. */
public final class ArmourClient {
    private ArmourClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("armour_meshes"),
                (ResourceManagerReloadListener) ArmourMeshes::reload));
        modBus.addListener(RegisterRenderPipelinesEvent.class, ArmourPipelines::onRegister);
        modBus.addListener(ArmourClient::onAddLayers);
        // a set's own cape hangs where vanilla's would: vanilla's is not drawn over it
        modBus.addListener((RegisterRenderStateModifiersEvent e) -> e.registerEntityModifier(
                new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, ArmourClient::hideCape));
        NeoForge.EVENT_BUS.addListener(RenderArmEvent.class, ArmourClient::onArm);
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

    private static void hideCape(Entity entity, EntityRenderState state) {
        if (state instanceof AvatarRenderState a && hasCape(a.chestEquipment)) a.showCape = false;
    }

    private static boolean hasCape(ItemStack chest) {
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
        AvatarRenderState s = e.getPlayerRenderState().avatarRenderState;
        ItemStack chest = s != null ? s.chestEquipment : ItemStack.EMPTY;
        var player = Minecraft.getInstance().player;
        if (chest.isEmpty() && player != null) chest = player.getItemBySlot(EquipmentSlot.CHEST);
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
