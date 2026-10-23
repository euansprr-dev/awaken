package dev.awaken.signal;

import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import dev.awaken.core.AwakenNet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public API: the Signal speaks to one player. How a message looks is its {@link SignalWindow.Presentation}: a toast,
 * a choice card, a story line or a panel (System v2; the old window is kept behind the {@code systemStyle} setting).
 * A message with an action is a decision: it stays pending for the player (saved, survives relog) until answered,
 * withdrawn or its ttl runs out. Action ids are "prefix:rest"; the module that owns the prefix handles the answer via
 * {@link #onAction}.
 */
public final class Signal {
    private Signal() {}

    public interface ActionHandler {
        void handle(ServerPlayer player, String action, boolean accepted);
    }

    private static final Map<String, ActionHandler> HANDLERS = new HashMap<>();

    public static void send(ServerPlayer player, SignalWindow window) {
        if (window.action().isPresent()) PendingChoices.add(player, window);
        AwakenNet.send(player, new SignalPayloads.Show(window));
        if (window.action().isPresent()) PendingChoices.sync(player);
    }

    public static void toast(ServerPlayer player, Component text) {
        AwakenNet.send(player, new SignalPayloads.Toast(text));
    }

    /** Register the handler for actions whose id starts with {@code prefix + ":"}. */
    public static void onAction(String prefix, ActionHandler handler) {
        HANDLERS.put(prefix, handler);
    }

    /**
     * Takes back a decision that no longer applies (the player left the gate, the offer ran out). Its card and its
     * "waiting" entry disappear. Returns whether it was pending.
     */
    public static boolean withdraw(ServerPlayer player, String action) {
        boolean was = PendingChoices.remove(player, action);
        if (was) PendingChoices.sync(player);
        return was;
    }

    /** Whether a decision with this action id is still waiting for the player. */
    public static boolean isPending(ServerPlayer player, String action) {
        for (PendingChoices.Entry e : PendingChoices.live(player)) if (e.action().equals(action)) return true;
        return false;
    }

    /** The decisions waiting for the player, oldest first. */
    public static List<SignalWindow> pending(ServerPlayer player) {
        List<SignalWindow> out = new ArrayList<>();
        for (PendingChoices.Entry e : PendingChoices.live(player)) out.add(e.window());
        return out;
    }

    /**
     * The player answered (a key on a card, a button in the Status screen, or a module answering for them). The
     * decision stops being pending and its handler runs. Answers for actions that aren't pending are still routed:
     * some sections answer things the player sees elsewhere (Directives in the Status screen).
     */
    public static void answer(ServerPlayer player, String action, boolean accepted) {
        if (PendingChoices.remove(player, action)) PendingChoices.sync(player);
        dispatch(player, action, accepted);
    }

    static void dispatch(ServerPlayer player, String action, boolean accepted) {
        int colon = action.indexOf(':');
        String prefix = colon < 0 ? action : action.substring(0, colon);
        ActionHandler h = HANDLERS.get(prefix);
        if (h != null) h.handle(player, action, accepted);
    }
}
