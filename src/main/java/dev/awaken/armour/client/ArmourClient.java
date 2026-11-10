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
        ArmourSoundsClient.init(modBus);
        ArmourAdmire.init(modBus);
        modBus.addListener(RegisterRenderPipelinesEvent.class, ArmourPipelines::onRegister);
        // a piece in a hand, on the ground, in a frame: the piece itself, not its icon (SPEC §44.11)
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent.class,
                e -> e.register(ArmourPieceRenderer.TYPE, ArmourPieceRenderer.Unbaked.MAP_CODEC));
        modBus.addListener(ArmourClient::onAddLayers);
        modBus.addListener((RegisterRenderStateModifiersEvent e) -> e.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {}, ArmourClient::readWorn));
        NeoForge.EVENT_BUS.addListener(RenderArmEvent.class, ArmourClient::onArm);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(ArmourPayloads.Charge.TYPE,
                (msg, ctx) -> Minecraft.getInstance().execute(() -> ArmourGlow.on(msg))));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(ArmourPayloads.Whole.TYPE,
                (msg, ctx) -> Minecraft.getInstance().execute(() -> ArmourGlow.whole(msg))));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            ArmourGlow.clear();
            ArmourLive.clear();
        });
        // every first-person limb our own code draws (casts, the off arm, the wrist, the world body) wears the armour too
        dev.awaken.core.client.FirstPersonDress.register(ArmourClient::dressLimb);
        ForgeClient.init(modBus);
        ArmourClientConfig.register(modBus);
    }

    /** Our pieces each slot wears, by {@link ArmorType} ordinal (null where none), for {@link ArmourLayer}. */
    static final ContextKey<ItemStack[]> WORN = new ContextKey<>(Awaken.id("armour_worn"));
    /** The wearer's set ability glow ({@link ArmourGlow}), and whether they are low (mastery III's heartbeat). */
    static final ContextKey<ArmourGlow.Glow> GLOW = new ContextKey<>(Awaken.id("armour_glow"));
    static final ContextKey<Boolean> LOW = new ContextKey<>(Awaken.id("armour_low"));
    /** You, while Admire's camera circles you: the set is drawn in its hero light ({@link ArmourAdmire}). */
    static final ContextKey<Boolean> ADMIRED = new ContextKey<>(Awaken.id("armour_admired"));
    /** When the wearer's awakened parts (mastery IV) first showed, so they grow in ({@link ArmourLive.Wake}). */
    static final ContextKey<ArmourLive.Wake> WAKE = new ContextKey<>(Awaken.id("armour_wake"));
    /** Below this share of health a mastered set beats with your heart. */
    private static final float LOW_HEALTH = 0.35F;

    private static final ArmorType[] SLOTS = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
    private static final ArmorType[] LEG_SLOTS = {ArmorType.LEGGINGS, ArmorType.BOOTS};

    /** The entity types whose renderers carry our layer: only these have vanilla's armour taken off. */
    private static final Set<EntityType<?>> LAYERED = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Each piece of ours whose meshes are loaded is drawn by {@link ArmourLayer} in place of vanilla's flat shell (and the
     * Hunter sets' old silhouettes), so it is taken out of the render state's equipment and the skin's outer layer under
     * it is hidden (a hat or jacket would poke through the plate). A helm of ours is not drawn again as a hat item, and a
     * set's own cape hangs where vanilla's would. A piece whose set has no meshes is left to vanilla, and so is every piece
     * the Sculpted armour setting leaves flat (off, or near only and this figure far off).
     */
    private static void readWorn(LivingEntity entity, LivingEntityRenderState state) {
        if (!(state instanceof HumanoidRenderState h) || !LAYERED.contains(entity.getType()) || !ArmourClientConfig.sculpts(entity)) return;
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
        wake(entity, state, worn);
        ArmourGlow.Glow glow = ArmourGlow.of(entity.getId());
        if (glow != null) state.setRenderData(GLOW, glow);
        if (entity.getHealth() < entity.getMaxHealth() * LOW_HEALTH) state.setRenderData(LOW, Boolean.TRUE);
        if (ArmourAdmire.on() && entity == Minecraft.getInstance().player) state.setRenderData(ADMIRED, Boolean.TRUE);
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

    /** Notes when each slot's awakened parts first show on this wearer (and when they go), for their grow-in. */
    private static void wake(LivingEntity entity, LivingEntityRenderState state, ItemStack[] worn) {
        ArmourLive.Wake w = ArmourLive.woken(entity.getId());
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = worn[SLOTS[i].ordinal()];
            Forged f = stack == null ? null : stack.get(ArmourContent.FORGED.get());
            boolean awake = f != null && f.masteryStep() >= ArmourLayer.AWAKENED;
            if (!awake) {
                if (w != null) w.since[i] = 0;
                continue;
            }
            if (w == null) w = ArmourLive.wake(entity.getId());
            if (w.since[i] == 0) w.since[i] = net.minecraft.util.Util.getMillis();
        }
        if (w != null) state.setRenderData(WAKE, w);
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

    /** The armour as {@link dev.awaken.core.client.FirstPersonDress}'s dresser: an arm wears the cuirass's, a leg the greaves' and sabatons'. */
    private static boolean dressLimb(dev.awaken.core.client.FirstPersonDress.Limb limb, PoseStack ps, SubmitNodeCollector out, int light) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return switch (limb) {
            case RIGHT_ARM -> dressArm(player, HumanoidArm.RIGHT, ps, out, light);
            case LEFT_ARM -> dressArm(player, HumanoidArm.LEFT, ps, out, light);
            case RIGHT_LEG -> dressLeg(player, true, ps, out, light);
            case LEFT_LEG -> dressLeg(player, false, ps, out, light);
        };
    }

    /**
     * Public for every drawer of a first-person arm (the move animations): draws the worn chestplate's forearm and
     * pauldron on {@code side}'s arm, with the pose already on that arm's bone (after its {@code translateAndRotate}).
     * Returns whether anything was drawn, so a caller can skip a sleeve of its own.
     */
    public static boolean dressArm(Player player, HumanoidArm side, PoseStack ps, SubmitNodeCollector out, int light) {
        if (ArmourClientConfig.sculpted() == ArmourClientConfig.Sculpted.OFF) return false;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ArmourLooks.Look look = ArmourLooks.drawn(chest);
        if (look == null || look.piece() != ArmorType.CHESTPLATE) return false;
        ArmourMesh[] bones = ArmourMeshes.piece(look.set(), ArmorType.CHESTPLATE);
        ArmourMesh mesh = bones == null ? null : bones[(side == HumanoidArm.RIGHT ? Bone.RIGHT_ARM : Bone.LEFT_ARM).ordinal()];
        if (mesh == null) return false;
        Forged f = chest.get(ArmourContent.FORGED.get());
        // your own set's charge and flare show on your own arm, as everyone else sees them on you
        ArmourGlow.Glow glow = ArmourGlow.of(player.getId());
        boolean low = player.getHealth() < player.getMaxHealth() * LOW_HEALTH;
        ArmourLayer.draw(out, ps, mesh, look.set(), light, chest.hasFoil(), ArmourGlow.energy(glow), ArmourGlow.flash(glow, ArmorType.CHESTPLATE), -1,
                f == null ? 0 : f.masteryStep(), low, f == null ? 0 : f.accent(), dev.awaken.core.api.EnchantApi.glowBits(chest),
                ArmourLayer.marks(f, look.set()));
        return true;
    }

    /**
     * Your own leg in first person (the world body's legs on a wall, a slide, a ledge): the worn greaves' and sabatons'
     * meshes on that leg, the pose already on the leg's bone.
     */
    public static boolean dressLeg(Player player, boolean right, PoseStack ps, SubmitNodeCollector out, int light) {
        if (ArmourClientConfig.sculpted() == ArmourClientConfig.Sculpted.OFF) return false;
        boolean drew = false;
        for (ArmorType slot : LEG_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot.getSlot());
            ArmourLooks.Look look = ArmourLooks.drawn(stack);
            if (look == null || look.piece() != slot) continue;
            ArmourMesh[] bones = ArmourMeshes.piece(look.set(), slot);
            ArmourMesh mesh = bones == null ? null : bones[(right ? Bone.RIGHT_LEG : Bone.LEFT_LEG).ordinal()];
            if (mesh == null) continue;
            Forged f = stack.get(ArmourContent.FORGED.get());
            ArmourGlow.Glow glow = ArmourGlow.of(player.getId());
            ArmourLayer.draw(out, ps, mesh, look.set(), light, stack.hasFoil(), ArmourGlow.energy(glow), ArmourGlow.flash(glow, slot), -1,
                    f == null ? 0 : f.masteryStep(), false, f == null ? 0 : f.accent(), dev.awaken.core.api.EnchantApi.glowBits(stack),
                    ArmourLayer.marks(f, look.set()));
            drew = true;
        }
        return drew;
    }
}
