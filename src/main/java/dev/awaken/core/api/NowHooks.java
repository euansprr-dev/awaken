package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Now tracker (System v2): "what could I do right now?" Every module registers a source that looks at one player
 * and offers up to a few concrete suggestions; the Signal shows the best three in one line and in the Status screen.
 * A source never invents work: no suggestion is a fine answer. Server side, called a few times a second at most.
 */
public final class NowHooks {
    private NowHooks() {}

    /**
     * One suggestion. {@code id} is stable for the same underlying thing (so the tracker doesn't flicker);
     * {@code priority} 0–100, higher shows first (an urgent danger 90+, a clear next step 50–80, a nice idea 10–40);
     * {@code where} points a compass when it is in the player's dimension.
     */
    public record Suggestion(String id, int priority, Component text, Optional<BlockPos> where) {
        public static Suggestion of(String id, int priority, String text) {
            return new Suggestion(id, priority, Component.literal(text), Optional.empty());
        }

        public static Suggestion at(String id, int priority, String text, BlockPos where) {
            return new Suggestion(id, priority, Component.literal(text), Optional.of(where));
        }
    }

    @FunctionalInterface
    public interface Source {
        List<Suggestion> suggest(ServerPlayer player);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** The best {@code max} suggestions for this player, highest priority first; a failing source is skipped. */
    public static List<Suggestion> collect(ServerPlayer player, int max) {
        List<Suggestion> all = new ArrayList<>();
        for (Source s : SOURCES) {
            try {
                all.addAll(s.suggest(player));
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.warn("[now] a suggestion source failed: {}", e.toString());
            }
        }
        all.sort(Comparator.comparingInt(Suggestion::priority).reversed().thenComparing(Suggestion::id));
        return all.size() > max ? List.copyOf(all.subList(0, max)) : List.copyOf(all);
    }
}
