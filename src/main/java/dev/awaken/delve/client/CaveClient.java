package dev.awaken.delve.client;

import dev.awaken.core.api.MapMarks;
import dev.awaken.core.api.Notices;
import dev.awaken.delve.Ore;
import dev.awaken.delve.cave.CaveContent;
import dev.awaken.delve.cave.CaveItems;
import dev.awaken.delve.cave.CaveKind;
import dev.awaken.delve.cave.CaveNet;
import dev.awaken.progression.client.CodexEntries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * The cave places on the client (SPEC §28.5): the place you are in ({@link CaveState}, read by the senses), its quiet
 * caption through the notification director ({@link CaveHud}), a sinkhole's mist and a chute's draught from outside
 * ({@link CaveFx}), the Delvers' logs read like a book and kept in the Codex, the lode map's marks on the Gate Map.
 */
public final class CaveClient {
    private CaveClient() {}

    private static int logsRead;
    private static List<CaveNet.Mark> marks = List.of();
    private static CaveNet.Now pending;
    private static int pendingSince, ticks;
    static final int CAPTION_WAIT = 200;

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(CaveNet.Now.TYPE, (m, ctx) -> now(m));
            e.register(CaveNet.Near.TYPE, (m, ctx) -> CaveFx.near(m));
            e.register(CaveNet.Logs.TYPE, (m, ctx) -> logsRead = m.read());
            e.register(CaveNet.LodeMarks.TYPE, (m, ctx) -> marks = List.copyOf(m.marks()));
        });
        modBus.addListener(RegisterParticleProvidersEvent.class, CaveFx::register);
        modBus.addListener(RegisterGuiLayersEvent.class, CaveHud::register);
        CaveItems.openLog = CaveClient::openLog;
        CodexEntries.addSource(CaveClient::codex);
        MapMarks.register(p -> {
            if (marks.isEmpty()) return List.of();
            List<MapMarks.Mark> out = new ArrayList<>(marks.size());
            for (CaveNet.Mark m : marks) {
                Ore o = Ore.values()[Math.floorMod(m.ore(), Ore.values().length)];
                out.add(new MapMarks.Mark(m.pos(), o.tint, "Lode · " + o.word(), false));
            }
            return out;
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> {
            ticks++;
            CaveHud.tick();
            CaveState.tick();
            CaveFx.tick();
            if (pending != null && ticks % 5 == 0) caption();
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            CaveState.reset();
            CaveFx.reset();
            CaveHud.reset();
            pending = null;
            marks = List.of();
        });
    }

    private static void now(CaveNet.Now m) {
        CaveKind kind = CaveKind.byOrdinal(m.kind());
        CaveState.set(kind == null ? null : kind.id, m.name(), m.seep(), m.quiet());
        if (kind == null) {
            pending = null;
            return;
        }
        if (m.caption()) {
            pending = m;
            pendingSince = ticks;
            caption();
        }
    }

    /** The caption takes the screen when the director gives it the moment; a small place's is filed if it can't. */
    private static void caption() {
        CaveNet.Now m = pending;
        if (m == null) return;
        CaveKind kind = CaveKind.byOrdinal(m.kind());
        if (kind == null) {
            pending = null;
            return;
        }
        var notices = Notices.client();
        Notices.Tier tier = kind.big ? Notices.Tier.IMPORTANT : Notices.Tier.AMBIENT;
        if (notices.claim(tier, "region")) {
            pending = null;
            CaveHud.show(m.name(), kind.what);
            var mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(CaveContent.NAME.get(), 1.0F, 0.7F));
        } else if (ticks - pendingSince > CAPTION_WAIT || !kind.big) {
            pending = null;
            notices.file(Notices.Tier.AMBIENT, Component.literal("Found · " + m.name()), Component.literal(kind.what));
        }
    }

    private static void openLog(int i) {
        List<Component> pages = new ArrayList<>(CaveItems.PAGES);
        for (int k = 0; k < CaveItems.PAGES; k++) pages.add(Component.translatable("item.awaken.delvers_log." + i + ".page" + k));
        Minecraft.getInstance().gui.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }

    /** The Delvers' logs this player has read, as Codex entries threaded to each other. */
    private static List<CodexEntries.Entry> codex() {
        if (logsRead == 0) return List.of();
        List<CodexEntries.Entry> out = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < CaveItems.LOGS; i++) if ((logsRead & (1 << i)) != 0) ids.add("delve_log_" + i);
        for (int i = 0; i < CaveItems.LOGS; i++) {
            if ((logsRead & (1 << i)) == 0) continue;
            StringBuilder body = new StringBuilder();
            for (int k = 0; k < CaveItems.PAGES; k++) {
                if (k > 0) body.append(' ');
                body.append(Component.translatable("item.awaken.delvers_log." + i + ".page" + k).getString());
            }
            String id = "delve_log_" + i;
            out.add(new CodexEntries.Entry(id, "The Delvers", Component.translatable("item.awaken.delvers_log." + i + ".title").getString(),
                    body.toString(), true, ids.stream().filter(x -> !x.equals(id)).toList()));
        }
        return out;
    }
}
