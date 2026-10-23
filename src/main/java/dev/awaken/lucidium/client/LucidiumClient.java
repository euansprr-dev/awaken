package dev.awaken.lucidium.client;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.net.LucidiumNet;
import dev.awaken.progression.client.CodexEntries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Client side of the Lucidium: the lucidity HUD, the H key (reality check, and holding it to shape the dream), sneak in
 * bed for a dreamless night, the flickering hand, rain that falls up, the creature with no shadow, the journal screen
 * and the Codex visions.
 */
public final class LucidiumClient {
    private LucidiumClient() {}

    /** Ticks of holding H before the dream is shaped. */
    public static final int HOLD_TICKS = 24;
    private static final RandomSource RANDOM = RandomSource.create();

    public static void init(IEventBus modBus) {
        modBus.addListener(LucidiumClient::onPayloads);
        modBus.addListener(LucidiumClient::onLayers);
        modBus.addListener(LucidiumClient::onParticles);
        modBus.addListener(LucidiumClient::onRenderState);
        NeoForge.EVENT_BUS.addListener(LucidiumClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(LucidiumClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> ClientDream.clear());
        CodexEntries.addSource(LucidiumClient::codex);
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(LucidiumNet.DreamHud.TYPE, (msg, ctx) -> ClientDream.update(msg));
        event.register(LucidiumNet.OpenJournal.TYPE, (msg, ctx) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof DreamJournalScreen open) open.refresh(msg);
            else mc.gui.setScreen(new DreamJournalScreen(msg));
        });
        event.register(LucidiumNet.CodexSync.TYPE, (msg, ctx) -> ClientDream.codex = List.copyOf(msg.lines()));
    }

    private static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Awaken.id("lucidity"), LucidityHud::render);
    }

    private static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(LucidiumContent.DREAM_DROP.get(), DreamDropParticle.Provider::new);
    }

    /** The dream sign "a creature with no shadow": its shadow is simply never drawn. */
    private static void onRenderState(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, (entity, state) -> {
            if (entity.getId() == ClientDream.shadowless()) {
                state.shadowRadius = 0f;
                state.shadowPieces.clear();
            }
        });
    }

    private static List<CodexEntries.Entry> codex() {
        List<CodexEntries.Entry> out = new ArrayList<>();
        for (LucidiumNet.CodexLine l : ClientDream.codex) out.add(new CodexEntries.Entry(l.id(), l.category(), l.title(), l.body(), l.found(), List.of()));
        return out;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ClientDream.tick();

        // sneak while lying in bed: a dreamless night (the bed screen hides the key from the game, so read it raw)
        if (player.isSleeping()) {
            if (!ClientDream.dreamlessSent && sneakHeld(mc)) {
                ClientDream.dreamlessSent = true;
                ClientPacketDistributor.sendToServer(LucidiumNet.Dreamless.INSTANCE);
            }
        } else {
            ClientDream.dreamlessSent = false;
        }

        while (AwakenKeys.REALITY_CHECK.consumeClick()) {
            ClientPacketDistributor.sendToServer(LucidiumNet.RealityCheck.INSTANCE);
            if (ClientDream.dreaming()) ClientDream.realityCheck = 30;
        }
        LucidiumNet.DreamHud hud = ClientDream.hud;
        boolean canShape = hud.has(LucidiumNet.DreamHud.SHAPE) || hud.has(LucidiumNet.DreamHud.SUMMON);
        if (ClientDream.dreaming() && canShape && AwakenKeys.REALITY_CHECK.isDown() && mc.gui.screen() == null) {
            ClientDream.hold++;
            if (ClientDream.hold >= HOLD_TICKS && !ClientDream.manifestSent) {
                ClientDream.manifestSent = true;
                boolean summon = player.isShiftKeyDown() && hud.has(LucidiumNet.DreamHud.SUMMON);
                ClientPacketDistributor.sendToServer(new LucidiumNet.Manifest(summon));
            }
        } else {
            ClientDream.hold = 0;
            ClientDream.manifestSent = false;
        }

        // rain falling up
        if (ClientDream.dreaming() && hud.has(LucidiumNet.DreamHud.RAIN_UP) && mc.level != null) {
            for (int i = 0; i < 5; i++) {
                double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 24;
                double y = player.getY() - 2 + RANDOM.nextDouble() * 4;
                double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 24;
                mc.level.addParticle(LucidiumContent.DREAM_DROP.get(), x, y, z, 0, 0.12 + RANDOM.nextDouble() * 0.1, 0);
            }
        }
    }

    private static boolean sneakHeld(Minecraft mc) {
        var key = mc.options.keyShift.getKey();
        return key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue());
    }

    /** During a reality check your hand won't stay put: it jitters, stretches and blinks out. */
    private static void onRenderHand(RenderHandEvent event) {
        int t = ClientDream.realityCheck;
        if (t <= 0) return;
        if ((t / 2) % 4 == 1) {
            event.setCanceled(true);
            return;
        }
        float k = t / 30f;
        var pose = event.getPoseStack();
        pose.translate((RANDOM.nextFloat() - 0.5f) * 0.06f * k, (RANDOM.nextFloat() - 0.5f) * 0.05f * k, 0);
        pose.scale(1f + (RANDOM.nextFloat() - 0.5f) * 0.25f * k, 1f, 1f);
    }
}
