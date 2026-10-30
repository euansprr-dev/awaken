package dev.awaken.signal.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.Notices;
import dev.awaken.core.api.TipHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.ui.TipRules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Just-in-time tips on the client (docs/carillon/m4/onboarding.md §1, SPEC §27.7): one small line above the story
 * line's slot the first time a context comes up, in the player's own keys, gone the moment they do it, back at most a
 * couple of times, then never. Shown only through the notification director ({@code Notices.claim(IMPORTANT, "tip")}),
 * so tips never pile onto toasts, a fight or a scene. Data: {@code assets/awaken/tips/<group>/<name>.json}; counts per
 * person in {@code config/awaken-tips.json}. Until now nothing installed TipHooks, so every tip in the game showed
 * nothing.
 */
public final class Tips {
    private Tips() {}

    private static Map<String, TipRules.Def> defs = Map.of();
    private static final Map<String, TipRules.Count> COUNTS = new HashMap<>();
    private static final Map<String, Long> SINCE = new HashMap<>(), UNTIL = new HashMap<>(), LAST = new HashMap<>();
    private static final Set<Object> HOLDERS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long ticks, lastAny = -100_000, startedAt, endAt = -1;
    private static String current;
    private static boolean loadedCounts;

    static void install() {
        TipHooks.installClient(new TipHooks.Client() {
            @Override public void offer(Identifier tip) {
                if (wants(tip)) keep(tip.toString(), 2, false);
            }

            @Override public void done(Identifier tip) {
                Tips.done(tip.toString());
            }

            @Override public void hold(Object owner, boolean held) {
                if (held) HOLDERS.add(owner);
                else HOLDERS.remove(owner);
            }

            @Override public boolean wants(Identifier tip) {
                TipRules.Def d = defs.get(tip.toString());
                return on() && d != null && !TipRules.retired(d, count(tip.toString()));
            }
        });
    }

    /**
     * The server offered a tip: a moment, not a lasting context. It waits up to {@link #SERVER_WAIT} for a quiet moment
     * (the director holds tips in a fight, a scene, the world's first minutes), then shows for its own {@code show_for}.
     */
    static void serverOffer(String id) {
        TipRules.Def d = defs.get(id);
        if (!on() || d == null || TipRules.retired(d, count(id))) return;
        // a new world's first minutes don't count against the wait: the Herald holds everything then, and a tip offered
        // at registration would otherwise lapse just as the world settles
        var level = Minecraft.getInstance().level;
        long unsettled = level == null ? 0 : Math.max(0, Herald.SETTLE - level.getGameTime());
        keep(id, (int) (Math.max(d.showFor(), SERVER_WAIT) + unsettled), true);
    }

    static final int SERVER_WAIT = 20 * 60 * 5;

    private static void keep(String id, int forTicks, boolean now) {
        SINCE.putIfAbsent(id, now ? ticks - TipRules.SETTLE : ticks);
        UNTIL.merge(id, ticks + forTicks, Math::max);
    }

    static void done(String id) {
        TipRules.Count c = count(id);
        COUNTS.put(id, new TipRules.Count(c.shown(), c.done() + 1));
        save();
        if (id.equals(current)) {
            if (endAt < 0) endAt = ticks; // done: it goes at once, felt as done
        } else {
            // still waiting for a quiet moment: done already, so it never shows (SPEC §27.12)
            SINCE.remove(id);
            UNTIL.remove(id);
        }
    }

    static boolean on() {
        try {
            return AwakenConfig.Client.TIPS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    static TipRules.Count count(String id) {
        loadCounts();
        return COUNTS.getOrDefault(id, TipRules.Count.NONE);
    }

    // ---- data ---------------------------------------------------------------------------------------------------------

    /** Reads every tip file (resource reload). */
    static void load(ResourceManager rm) {
        Map<String, TipRules.Def> out = new HashMap<>();
        rm.listResources("tips", id -> id.getPath().endsWith(".json")).forEach((file, res) -> {
            String path = file.getPath().substring("tips/".length(), file.getPath().length() - ".json".length());
            try (Reader r = res.openAsReader()) {
                TipRules.Def.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(r)).resultOrPartial(e -> Awaken.LOG.warn("[tips] {}: {}", file, e))
                        .ifPresent(d -> out.put(Identifier.fromNamespaceAndPath(file.getNamespace(), path).toString(), d));
            } catch (Exception e) {
                Awaken.LOG.warn("[tips] can't read {}: {}", file, e.toString());
            }
        });
        defs = Map.copyOf(out);
        Awaken.LOG.info("[tips] {} tips", defs.size());
    }

    private static Path countsFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("awaken-tips.json");
    }

    private static void loadCounts() {
        if (loadedCounts) return;
        loadedCounts = true;
        try {
            Path f = countsFile();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (var e : o.entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                COUNTS.put(e.getKey(), new TipRules.Count(c.get("shown").getAsInt(), c.get("done").getAsInt()));
            }
        } catch (Exception e) {
            Awaken.LOG.warn("[tips] can't read the tip counts: {}", e.toString());
        }
    }

    private static void save() {
        JsonObject o = new JsonObject();
        COUNTS.forEach((id, c) -> {
            JsonObject j = new JsonObject();
            j.addProperty("shown", c.shown());
            j.addProperty("done", c.done());
            o.add(id, j);
        });
        try {
            Files.createDirectories(countsFile().getParent());
            Files.writeString(countsFile(), o.toString());
        } catch (Exception e) {
            Awaken.LOG.warn("[tips] can't save the tip counts: {}", e.toString());
        }
    }

    /** `/awaken tips reset`: every tip teaches again. */
    static void reset() {
        COUNTS.clear();
        save();
    }

    // ---- the tick -----------------------------------------------------------------------------------------------------

    static void tick() {
        ticks++;
        SINCE.keySet().removeIf(id -> !id.equals(current) && UNTIL.getOrDefault(id, 0L) < ticks);
        UNTIL.keySet().removeIf(id -> !SINCE.containsKey(id));
        Minecraft mc = Minecraft.getInstance();
        if (current != null) {
            // its context ended (or it was done, or it has said its piece): it fades
            boolean ended = UNTIL.getOrDefault(current, 0L) < ticks;
            TipRules.Def d = defs.get(current);
            if (endAt < 0 && (ended || d == null || ticks - startedAt >= d.showFor())) endAt = ticks;
            if (endAt >= 0 && ticks - endAt >= FADE) {
                SINCE.remove(current);
                current = null;
                lastAny = ticks;
            }
            return;
        }
        if (mc.player == null || mc.gui.screen() != null || !HOLDERS.isEmpty() || Notices.client().quiet() || SINCE.isEmpty()) return;
        boolean awakened = Progression.profile(mc.player).awakened();
        loadCounts();
        String id = TipRules.pick(ticks, SINCE, defs, COUNTS, LAST, lastAny, awakened, x -> Notices.client().claim(Notices.Tier.IMPORTANT, "tip"));
        if (id == null) return;
        current = id;
        startedAt = ticks;
        endAt = -1;
        LAST.put(id, ticks);
        TipRules.Count c = count(id);
        COUNTS.put(id, new TipRules.Count(c.shown() + 1, c.done()));
        save();
    }

    static final int FADE = 8, IN = 6;

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** Centred above the story line's slot: one line, two at most, in the System's glass. */
    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.player == null || mc.gui.hud.isHidden()) return;
        TipRules.Def d = defs.get(current);
        if (d == null) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        float age = ticks - startedAt + pt;
        float a = Math.min(1F, age / IN);
        if (endAt >= 0) a = Math.min(a, Math.max(0F, 1F - (ticks - endAt + pt) / FADE));
        if (a <= 0.01F) return;
        Font font = mc.font;
        String text = KeyText.resolve(TipRules.text(d, count(current))).replaceAll("\\{[a-z_]+:[0-9]+}", "");
        List<FormattedCharSequence> lines = font.split(Component.literal(text), 240);
        if (lines.size() > 2) lines = lines.subList(0, 2);
        int w = 0;
        for (var l : lines) w = Math.max(w, font.width(l));
        int cx = g.guiWidth() / 2, y = g.guiHeight() - 72 - 14 - lines.size() * 11;
        HudLook.glow(g, cx - w / 2 - 8, y - 4, w + 16, lines.size() * 11 + 6, Glass.EDGE, 0.4F * a);
        for (int i = 0; i < lines.size(); i++) {
            var l = lines.get(i);
            HudLook.text(g, font, l, cx - font.width(l) / 2, y + i * 11, Glass.a(Glass.TEXT, a), true);
        }
    }

    // ---- tests ----------------------------------------------------------------------------------------------------------

    static String showing() {
        return current;
    }
}
