package dev.awaken.descent.client;

import dev.awaken.Awaken;
import dev.awaken.descent.DescentContent;
import dev.awaken.descent.Strata;
import dev.awaken.descent.player.DeepPack;
import dev.awaken.progression.client.CodexEntries;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.descent.net.DescentPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/** Client entry point for the descent module. Only loaded on the client. */
public final class DescentClient {
    private DescentClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(DescentClient::onPayloads);
        modBus.addListener(DescentClient::onRenderers);
        modBus.addListener(DescentClient::onLayers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> DescentHud.markers());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> ClientDescent.clear());
        CodexEntries.addSource(DescentClient::codex);
        StatusSections.add(StatusSections.Tab.STATUS, new PackSection());
    }

    private static void onPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(DescentPayloads.Sync.TYPE, (msg, ctx) -> ClientDescent.onSync(msg));
        event.register(DescentPayloads.Hud.TYPE, (msg, ctx) -> ClientDescent.onHud(msg));
        event.register(DescentPayloads.OpenTravel.TYPE, (msg, ctx) -> Minecraft.getInstance().gui.setScreen(new TravelScreen(msg)));
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DescentContent.GRAVE_ECHO.get(), GraveEchoRenderer::new);
        event.registerEntityRenderer(DescentContent.SHADOW_SELF.get(), ShadowSelfRenderer::new);
    }

    private static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Awaken.id("descent_hud"), DescentHud::render);
    }

    /** Codex: strata you've been to, and a rumour of the next one ("?"). Undiscovered strata are never listed. */
    static List<CodexEntries.Entry> codex() {
        List<CodexEntries.Entry> out = new ArrayList<>();
        DescentPayloads.Sync s = ClientDescent.sync;
        boolean any = !s.visited().isEmpty();
        out.add(new CodexEntries.Entry("descent:well", "The Descent", any ? "The Well" : "?",
                any ? "A Precursor ring around a shaft near spawn. Jump in, or take the lift on the rim."
                    : "Somewhere near spawn there is a pit. People say there are lights at the bottom.",
                any, List.of("descent:stratum_1")));
        int deepestStratum = 0;
        for (int n = 1; n <= Strata.COUNT; n++) {
            if (!s.visited().contains(n)) continue;
            deepestStratum = Math.max(deepestStratum, n);
            Strata.Def d = Strata.def(n);
            String lit = s.lit().contains(n) ? " Its Waystone is lit." : "";
            out.add(new CodexEntries.Entry("descent:stratum_" + n, "The Descent", Strata.label(n),
                    d.flavour() + "\nRule: " + d.rule() + lit, true,
                    List.of(n == 1 ? "descent:well" : "descent:stratum_" + (n - 1), "descent:stratum_" + (n + 1))));
        }
        if (any && deepestStratum < Strata.COUNT) {
            int next = deepestStratum + 1;
            out.add(new CodexEntries.Entry("descent:stratum_" + next, "The Descent", "?",
                    "Something lies below " + Strata.def(deepestStratum).name() + ". The pit there leads down.",
                    false, List.of("descent:stratum_" + deepestStratum)));
        }
        if (s.seedCleared()) {
            int deepest = Math.max(0, s.deepest() - Strata.ENDLESS_BASE);
            out.add(new CodexEntries.Entry("descent:endless", "The Descent", "The Endless",
                    "Generated strata under the Seed, with stacking rules." + (deepest > 0 ? " Your record: depth " + deepest + "." : ""),
                    true, List.of("descent:stratum_10")));
        }
        return out;
    }

    /** The Deep Pack in the Status screen (read-only: it's not yours until you rest or climb out). */
    private static final class PackSection implements StatusSections.Section {
        @Override
        public String title() {
            return "DEEP PACK";
        }

        @Override
        public int order() {
            return 420;
        }

        private static DeepPack pack() {
            var p = Minecraft.getInstance().player;
            return p == null ? new DeepPack() : p.getData(DescentContent.DEEP_PACK.get());
        }

        @Override
        public boolean visible() {
            return !pack().isEmpty() || ClientDescent.depth > 0;
        }

        @Override
        public int height(int width) {
            return 14 + 3 * 18 + 4;
        }

        @Override
        public void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick) {
            var font = Minecraft.getInstance().font;
            DeepPack pack = pack();
            g.text(font, pack.used() + " / " + DeepPack.SIZE + " · becomes yours at a Waystone or the surface", x, y,
                    Glass.a(Glass.LABEL, 1f), false);
            for (int i = 0; i < DeepPack.SIZE; i++) {
                int sx = x + (i % 9) * 18, sy = y + 14 + (i / 9) * 18;
                g.fill(sx, sy, sx + 17, sy + 17, Glass.a(0x0A1020, 0.85f));
                g.outline(sx, sy, 17, 17, Glass.a(Glass.LANTERN, 0.25f));
                ItemStack s = pack.get(i);
                if (!s.isEmpty()) {
                    g.item(s, sx, sy);
                    g.itemDecorations(font, s, sx, sy);
                }
            }
        }
    }
}
