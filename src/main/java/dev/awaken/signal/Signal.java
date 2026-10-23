package dev.awaken.signal;

import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import dev.awaken.core.AwakenNet;

import java.util.HashMap;
import java.util.Map;

/**
 * Public API: the Signal speaks to one player. Windows queue on the client; in combat they collapse to toasts.
 * Action ids are "prefix:rest"; the module that owns the prefix handles the answer via {@link #onAction}.
 */
public final class Signal {
    private Signal() {}

    public interface ActionHandler {
        void handle(ServerPlayer player, String action, boolean accepted);
    }

    private static final Map<String, ActionHandler> HANDLERS = new HashMap<>();

    public static void send(ServerPlayer player, SignalWindow window) {
        AwakenNet.send(player, new SignalPayloads.Show(window));
    }

    public static void toast(ServerPlayer player, Component text) {
        AwakenNet.send(player, new SignalPayloads.Toast(text));
    }

    /** Register the handler for actions whose id starts with {@code prefix + ":"}. */
    public static void onAction(String prefix, ActionHandler handler) {
        HANDLERS.put(prefix, handler);
    }

    static void dispatch(ServerPlayer player, String action, boolean accepted) {
        int colon = action.indexOf(':');
        String prefix = colon < 0 ? action : action.substring(0, colon);
        ActionHandler h = HANDLERS.get(prefix);
        if (h != null) h.handle(player, action, accepted);
    }
}
