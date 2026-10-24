package dev.awaken.story.client;

import dev.awaken.progression.client.CodexEntries;
import dev.awaken.story.StoryModule;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;

/** Client side of the story: Vael's renderer, the ending choice, the credits, and Revelations on the Codex board. */
public final class StoryClient {
    private StoryClient() {}

    private static final List<String> SEEN = new ArrayList<>();

    public static void init(IEventBus modBus) {
        modBus.addListener(StoryClient::renderers);
        modBus.addListener(StoryClient::payloads);
        CodexEntries.addSource(StoryClient::codex);
        RitualClient.init(modBus);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(StoryModule.VAEL.get(), VaelRenderer::new);
    }

    private static void payloads(RegisterClientPayloadHandlersEvent e) {
        e.register(StoryPayloads.OpenEnding.TYPE, (msg, ctx) -> Minecraft.getInstance().gui.setScreen(new EndingScreen()));
        e.register(StoryPayloads.Credits.TYPE, (msg, ctx) -> Minecraft.getInstance().gui.setScreen(new CreditsScreen(msg.endingTitle())));
        e.register(StoryPayloads.RitualScene.TYPE, (msg, ctx) -> RitualClient.onScene(msg));
        e.register(StoryPayloads.RitualCue.TYPE, (msg, ctx) -> RitualClient.onCue(msg));
        e.register(StoryPayloads.Seen.TYPE, (msg, ctx) -> {
            SEEN.clear();
            SEEN.addAll(msg.entries());
        });
    }

    private static List<CodexEntries.Entry> codex() {
        List<CodexEntries.Entry> out = new ArrayList<>();
        int i = 0;
        for (String s : SEEN) {
            int sep = s.indexOf('\u0001');
            if (sep < 0) continue;
            out.add(new CodexEntries.Entry("revelation/" + i++, "Revelations", s.substring(0, sep), s.substring(sep + 1), true, List.of()));
        }
        return out;
    }
}
