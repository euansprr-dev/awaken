package dev.awaken.veil.client;

import com.google.common.reflect.TypeToken;
import dev.awaken.Awaken;
import dev.awaken.progression.Perks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import dev.awaken.progression.client.CodexEntries;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.veil.Cord;
import dev.awaken.veil.Veil;
import dev.awaken.veil.VeilContent;
import dev.awaken.veil.net.VeilPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry point for the veil module. Only loaded on the client. */
public final class VeilClient {
    private VeilClient() {}

    private static LoopSound hum;
    private static LoopSound drift;

    public static void init(IEventBus modBus) {
        modBus.addListener(VeilClient::onPayloads);
        modBus.addListener(VeilClient::onLayers);
        modBus.addListener(VeilClient::onRenderers);
        modBus.addListener(VeilClient::onModifiers);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Pre.class, e -> VeilInput.preTick());
        bus.addListener(ClientTickEvent.Post.class, e -> postTick());
        bus.addListener(ViewportEvent.RenderFog.class, VeilWorld::onFog);
        bus.addListener(ViewportEvent.ComputeFogColor.class, VeilWorld::onFogColour);
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, VeilWorld::onCamera);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            VeilClientState.reset();
            VeilInput.reset();
        });
        Veil.installClientQuery(p -> p == Minecraft.getInstance().player && VeilClientState.spirit());
        CodexEntries.addSource(VeilClientState::codexEntries);
        StatusSections.add(StatusSections.Tab.STATUS, new VeilSection());
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(VeilPayloads.State.TYPE, (msg, ctx) -> VeilClientState.onState(msg));
        event.register(VeilPayloads.Meditation.TYPE, (msg, ctx) -> VeilClientState.onMeditation(msg));
        event.register(VeilPayloads.Sight.TYPE, (msg, ctx) -> VeilClientState.onSight(msg));
        event.register(VeilPayloads.Codex.TYPE, (msg, ctx) -> VeilClientState.onCodex(msg));
        event.register(VeilPayloads.Glimpse.TYPE, (msg, ctx) -> VeilClientState.onGlimpse(msg));
    }

    private static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Awaken.id("veil"), VeilHud::render);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VeilContent.VEIL_SPIRIT.get(), VeilSpiritRenderer::new);
    }

    /** Mob ranks, the Body and Cord-Eaters glow in their colours, for a spirit only. */
    private static void onModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {}, (entity, state) -> {
            if (!VeilClientState.spirit()) return;
            int c = VeilClientState.OUTLINES.getOrDefault(entity.getId(), 0);
            if (c != 0) state.outlineColor = c | 0xFF000000;
        });
    }

    private static void postTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        VeilClientState.tick();
        VeilWorld.tick();
        boolean meditating = !VeilClientState.spirit() && VeilClientState.meditation.ticks() >= dev.awaken.veil.Meditation.SETTLE;
        if (meditating && (hum == null || !mc.getSoundManager().isActive(hum))) {
            hum = new LoopSound(VeilContent.HUM.get(), () -> !VeilClientState.spirit() && VeilClientState.meditation.ticks() > 0, 0.55F, 1.0F);
            mc.getSoundManager().play(hum);
        }
        if (VeilClientState.spirit() && (drift == null || !mc.getSoundManager().isActive(drift))) {
            drift = new LoopSound(VeilContent.DRIFT.get(), VeilClientState::spirit, 0.35F, 1.0F);
            mc.getSoundManager().play(drift);
        }
    }

    /** Status tab: what your stats give you in the Veil. */
    static final class VeilSection extends dev.awaken.signal.client.UiSection {
        public String title() { return "THE VEIL"; }
        public int order() { return 320; }
        public boolean visible() {
            var p = Minecraft.getInstance().player;
            return p != null && Progression.profile(p).awakened();
        }
        @Override
        protected void build(dev.awaken.signal.client.Ui ui) {
            var p = Minecraft.getInstance().player;
            if (p == null) return;
            int spi = Progression.stat(p, Stat.SPI), per = Progression.stat(p, Stat.PER);
            int cord = Cord.radius(spi, Progression.perk(p, Perks.CORD_LENGTH));
            int secs = Cord.timeTicks(spi) / 20;
            int aware = Cord.awareness(per, spi, Progression.perk(p, Perks.SECRET_GLOW_RANGE));
            int sense = Cord.secretSense(per, Progression.perk(p, Perks.SECRET_GLOW_RANGE));
            String[][] rows = {{"Cord", cord + " blocks"}, {"Time out of body", secs + " s"}, {"Awareness", aware + " blocks"},
                    {"Secret sense", sense + " blocks"}};
            for (String[] r : rows) ui.pair(r[0], r[1], Glass.a(Glass.LABEL, 1F), Glass.a(Glass.TITLE, 1F));
            ui.text("Sneak and hold G for 10 s to step out of your body.", Glass.a(Glass.LABEL, 0.8F));
        }
    }
}
