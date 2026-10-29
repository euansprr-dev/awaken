package dev.awaken.armour.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.ArmourLooks;
import dev.awaken.armour.Forged;
import dev.awaken.armour.client.ArmourMeshes.Bone;
import dev.awaken.armour.net.ArmourPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Client entry of the armour (SPEC §44.1): its meshes, its shader, its layer, the forearm in first person; the Forge (§44.4). */
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
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(ArmourPayloads.Charge.TYPE,
                (msg, ctx) -> Minecraft.getInstance().execute(() -> ArmourGlow.on(msg))));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> ArmourGlow.clear());
        ForgeClient.init(modBus);
    }

    /** Our pieces each slot wears, by {@link ArmorType} ordinal (null where none), for {@link ArmourLayer}. */
    static final ContextKey<ItemStack[]> WORN = new ContextKey<>(Awaken.id("armour_worn"));
    /** The wearer's set ability glow ({@link ArmourGlow}), and whether they are low (mastery III's heartbeat). */
    static final ContextKey<ArmourGlow.Glow> GLOW = new ContextKey<>(Awaken.id("armour_glow"));
    static final ContextKey<Boolean> LOW = new ContextKey<>(Awaken.id("armour_low"));
    /** Below this share of health a mastered set beats with your heart. */
    private static final float LOW_HEALTH = 0.35F;

    private static final ArmorType[] SLOTS = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};

    /** The entity types whose renderers carry our layer: only these have vanilla's armour taken off. */
    private static final Set<EntityType<?>> LAYERED = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Each piece of ours whose meshes are loaded is drawn by {@link ArmourLayer} in place of vanilla's flat shell (and the
     * Hunter sets' old silhouettes), so it is taken out of the render state's equipment and the skin's outer layer under
     * it is hidden (a hat or jacket would poke through the plate). A helm of ours is not drawn again as a hat item, and a
     * set's own cape hangs where vanilla's would. A piece whose set has no meshes is left to vanilla.
     */
    private static void readWorn(LivingEntity entity, LivingEntityRenderState state) {
        if (!(state instanceof HumanoidRenderState h) || !LAYERED.contains(entity.getType())) return;
        ItemStack[] worn = null;
        for (ArmorType slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot.getSlot());
            ArmourLooks.Look look = ArmourLooks.drawn(stack);
            if (look == null || look.piece() != slot || ArmourMeshes.piece(look.set(), slot) == null) continue;
            if (worn == null) worn = new ItemStack[ArmorType.values().length];
            worn[slot.ordinal()] = stack;
        }
        if (worn == null) return;
        state.setRenderData(WORN, worn);
        ArmourGlow.Glow glow = ArmourGlow.of(entity.getId());
        if (glow != null) state.setRenderData(GLOW, glow);
        if (entity.getHealth() < entity.getMaxHealth() * LOW_HEALTH) state.setRenderData(LOW, Boolean.TRUE);
        AvatarRenderState a = state instanceof AvatarRenderState av ? av : null;
        if (worn[ArmorType.HELMET.ordinal()] != null) {
            h.headEquipment = ItemStack.EMPTY;
            state.headItem.clear();
            if (a != null) a.showHat = false;
        }
        if (worn[ArmorType.CHESTPLATE.ordinal()] != null) {
            h.chestEquipment = ItemStack.EMPTY;
            if (a != null) {
                a.showJacket = a.showLeftSleeve = a.showRightSleeve = false;
                if (hasCape(worn[ArmorType.CHESTPLATE.ordinal()])) a.showCape = false;
            }
        }
        if (worn[ArmorType.LEGGINGS.ordinal()] != null) {
            h.legsEquipment = ItemStack.EMPTY;
            if (a != null) a.showLeftPants = a.showRightPants = false;
        }
        if (worn[ArmorType.BOOTS.ordinal()] != null) h.feetEquipment = ItemStack.EMPTY;
    }

    /**
     * Our layer on every renderer that draws vanilla armour: players and mannequins (both arm widths), armour stands and
     * every living renderer posing a {@link HumanoidModel} (zombies, skeletons, piglins, ours), whose state is then a
     * {@link HumanoidRenderState} by HumanoidModel's bound.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onAddLayers(EntityRenderersEvent.AddLayers e) {
        LAYERED.clear();
        for (PlayerModelType type : e.getSkins()) {
            AvatarRenderer<?> player = e.getPlayerRenderer(type);
            if (player != null) ((AvatarRenderer) player).addLayer(new ArmourLayer<>(player));
            AvatarRenderer<?> mannequin = e.getMannequinRenderer(type);
            if (mannequin != null) ((AvatarRenderer) mannequin).addLayer(new ArmourLayer<>(mannequin));
        }
        LAYERED.add(EntityTypes.PLAYER);
        LAYERED.add(EntityTypes.MANNEQUIN);
        for (EntityType<?> type : e.getEntityTypes()) {
            EntityRenderer<?, ?> renderer = e.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel) {
                living.addLayer(new ArmourLayer(living));
                LAYERED.add(type);
            }
        }
    }

    private static boolean hasCape(@Nullable ItemStack chest) {
        if (chest == null) return false;
        ArmourLooks.Look look = ArmourLooks.drawn(chest);
        if (look == null) return false;
        ArmourMesh[] bones = ArmourMeshes.piece(look.set(), ArmorType.CHESTPLATE);
        return bones != null && bones[Bone.CAPE0.ordinal()] != null;
    }

    /**
     * Vanilla's empty hand in first person: the arm as vanilla poses it there (its rest pose, turned a tenth of a radian
     * out), dressed, before vanilla's own arm, which the mesh covers.
     */
    private static void onArm(RenderArmEvent e) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        PoseStack ps = e.getPoseStack();
        ModelPart arm = e.getArmPart();
        boolean right = e.getArm() == HumanoidArm.RIGHT;
        ps.pushPose();
        arm.resetPose();
        arm.zRot = right ? 0.1F : -0.1F;
        arm.translateAndRotate(ps);
        dressArm(player, e.getArm(), ps, e.getSubmitNodeCollector(), e.getLightCoords());
        ps.popPose();
    }

    /**
     * Public for every drawer of a first-person arm (the move animations): draws the worn chestplate's forearm and
     * pauldron on {@code side}'s arm, with the pose already on that arm's bone (after its {@code translateAndRotate}).
     * Returns whether anything was drawn, so a caller can skip a sleeve of its own.
     */
    public static boolean dressArm(Player player, HumanoidArm side, PoseStack ps, SubmitNodeCollector out, int light) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ArmourLooks.Look look = ArmourLooks.drawn(chest);
        if (look == null || look.piece() != ArmorType.CHESTPLATE) return false;
        ArmourMesh[] bones = ArmourMeshes.piece(look.set(), ArmorType.CHESTPLATE);
        ArmourMesh mesh = bones == null ? null : bones[(side == HumanoidArm.RIGHT ? Bone.RIGHT_ARM : Bone.LEFT_ARM).ordinal()];
        if (mesh == null) return false;
        Forged f = chest.get(ArmourContent.FORGED.get());
        ArmourLayer.draw(out, ps, mesh, look.set(), light, chest.hasFoil(), 0, 0, -1, f == null ? 0 : f.masteryStep(), false, f == null ? 0 : f.accent());
        return true;
    }
}
