package dev.awaken.arms.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.arms.Arms;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.api.CombatFeedback;
import dev.awaken.core.api.Stances;
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

/** Client entry of Arms (SPEC §24): the verbs' input and prediction, the weapon in first person, the feel, the HUD. */
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
        CombatFeedback.install(ArmsFeel.INSTANCE);
        Stances.install(ArmsStances.INSTANCE);
        modBus.addListener(ArmsClient::onPayloads);
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CROSSHAIR, Awaken.id("arms_hud"), ArmsHud::render));
        modBus.addListener((RegisterRenderStateModifiersEvent e) ->
                e.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, ArmsFeel::modify));
        modBus.addListener(ArmsClient::onExtensions);
        // the sculpted weapons: their meshes, their shader, the item model type that draws them
        modBus.addListener(AddClientReloadListenersEvent.class, e -> e.addListener(Awaken.id("arms_weapons"),
                (ResourceManagerReloadListener) WeaponMeshes::reload));
        modBus.addListener(RegisterRenderPipelinesEvent.class, WeaponPipelines::onRegister);
        modBus.addListener(RegisterSpecialModelRendererEvent.class, e -> e.register(WeaponRenderer.TYPE, WeaponRenderer.Unbaked.MAP_CODEC));
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGH, InputEvent.InteractionKeyMappingTriggered.class, ArmsPredict::onKey);
        bus.addListener(ClientTickEvent.Post.class, e -> {
            ArmsPredict.tick();
            ArmsAutopilot.tick();
        });
        bus.addListener(EventPriority.LOW, MovementInputUpdateEvent.class, ArmsPredict::onInput);
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, ArmsFeel::onCameraAngles);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> ArmsPredict.reset());
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> ArmsPredict.reset());
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(ArmsPayloads.Result.TYPE, (msg, ctx) -> ArmsPredict.onResult(msg));
        event.register(ArmsPayloads.Seen.TYPE, (msg, ctx) -> ArmsPredict.onSeen(msg));
    }

    private static void onExtensions(RegisterClientExtensionsEvent event) {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) if (Arms.styleOf(item) != null && !event.isItemRegistered(item)) items.add(item);
        if (!items.isEmpty()) event.registerItem(HANDS, items.toArray(Item[]::new));
    }
}
