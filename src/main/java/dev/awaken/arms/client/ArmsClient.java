package dev.awaken.arms.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.arms.Arms;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.CombatFeedback;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.core.api.Stances;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/** Client entry of Arms (SPEC §25): the verbs' input and prediction, the weapon in first person, the feel, the HUD. */
public final class ArmsClient {
    private ArmsClient() {}

    /** Every fighting weapon draws its first-person moves through its own hand transform. */
    private static final IClientItemExtensions HANDS = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack poseStack, PlayerRenderState player, HumanoidArm arm, ItemStack stack, float partialTick,
                                               float equipProcess, float swingProcess) {
            return ArmsHands.transform(poseStack, arm, stack, partialTick, equipProcess);
        }
    };

    public static void init(IEventBus modBus) {
        // the first-person swings (1.1 MB of JSON) load off the render thread, before anyone draws a blade
        java.util.concurrent.CompletableFuture.runAsync(dev.awaken.arms.FpSwings::warm, net.minecraft.util.Util.backgroundExecutor());
        ArmsComfort.register();
        ArmourySection.register();
        CombatFeedback.install(ArmsFeel.INSTANCE);
        dev.awaken.progression.client.CodexEntries.addSource(ArmsCodex::entries);
        CombatFeedback.engaged = ArmsPredict::engaged;
        FirstPerson.view(ArmsHands::view);
        Stances.install(ArmsStances.INSTANCE);
        modBus.addListener(ArmsClient::onPayloads);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(dev.awaken.arms.ArmsStands.standType(), PatternStandRenderer::new));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CROSSHAIR, Awaken.id("arms_hud"), ArmsHud::render));
        modBus.addListener((RegisterRenderStateModifiersEvent e) ->
                e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, (entity, state) -> {
                    ArmsFeel.modify(entity, state);
                    ArmsOffHand.modify(entity, state);
                    ArmsWrist.modify(entity, state); // last: its hands' items as the others left them, marked with their arm
                }));
        modBus.addListener(ArmsClient::onExtensions);
        // vanilla blades seen from outside lay their ribbon from the posed hand
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers e) -> {
            for (var type : e.getSkins()) {
                var r = e.getPlayerRenderer(type);
                if (r != null) BladeTraceLayer.addTo(r);
            }
        });
        // the sculpted weapons: their meshes, their shader, the item model type that draws them
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("arms_weapons"),
                (ResourceManagerReloadListener) WeaponMeshes::reload));
        modBus.addListener(RegisterRenderPipelinesEvent.class, WeaponPipelines::onRegister);
        modBus.addListener(RegisterSpecialModelRendererEvent.class, e -> e.register(WeaponRenderer.TYPE, WeaponRenderer.Unbaked.MAP_CODEC));
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ArmsTooltip::onTooltip);
        // the off hand's twin (a pair): after Hands' own off-hand pass (Anims registers before Arms, both LOWEST)
        bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, net.neoforged.neoforge.client.event.RenderHandEvent.class, ArmsOffHand::onRenderHand);
        bus.addListener(EventPriority.HIGH, InputEvent.InteractionKeyMappingTriggered.class, ArmsPredict::onKey);
        bus.addListener(ClientTickEvent.Post.class, e -> {
            ArmsPredict.tick();
            ArmsAutopilot.tick();
        });
        bus.addListener(EventPriority.LOW, MovementInputUpdateEvent.class, ArmsPredict::onInput);
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, ArmsFeel::onCameraAngles);
        bus.addListener(ViewportEvent.ComputeFov.class, ArmsFeel::onFov);
        bus.addListener(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent.class, BladeTrail::onSubmit);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            ArmsPredict.reset();
            ArmsCodex.reset();
            ArmouryCache.reset();
        });
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> ArmsPredict.reset());
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(ArmsPayloads.Result.TYPE, (msg, ctx) -> ArmsPredict.onResult(msg));
        event.register(ArmsPayloads.Seen.TYPE, (msg, ctx) -> ArmsPredict.onSeen(msg));
        event.register(ArmsPayloads.Woven.TYPE, (msg, ctx) -> ArmsCodex.onWoven(msg));
        event.register(ArmsPayloads.ArmouryView.TYPE, (msg, ctx) -> ArmouryCache.onView(msg));
        event.register(ArmsPayloads.ArmouryDetail.TYPE, (msg, ctx) -> ArmouryCache.onDetail(msg));
        event.register(ArmsPayloads.IssueOffer.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(new IssueScreen(msg.styles()))));
        event.register(ArmsPayloads.Mastery.TYPE, (msg, ctx) -> ArmsPredict.onMastery(msg));
    }

    private static void onExtensions(RegisterClientExtensionsEvent event) {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) if (Arms.styleOf(item) != null && !event.isItemRegistered(item)) items.add(item);
        if (!items.isEmpty()) event.registerItem(HANDS, items.toArray(Item[]::new));
    }
}
