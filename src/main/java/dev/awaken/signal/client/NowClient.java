package dev.awaken.signal.client;

import dev.awaken.core.api.NowHooks;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/** The client's copy of the Now tracker's suggestions, and the compass that points at them. */
public final class NowClient {
    private NowClient() {}

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static SignalPayloads.Now current = new SignalPayloads.Now(net.minecraft.resources.Identifier.withDefaultNamespace("overworld"), List.of());
    private static String topId = "";
    private static int changedAt;
    private static int ticks;
    private static int version;

    static void accept(SignalPayloads.Now now) {
        version++;
        String id = now.top().map(NowHooks.Suggestion::id).orElse("");
        if (!id.equals(topId)) {
            topId = id;
            changedAt = ticks;
        }
        current = now;
    }

    /** Changes whenever new suggestions arrive (the tracker rebuilds its text then). */
    static int version() {
        return version;
    }

    static void tick() {
        ticks++;
    }

    /** Ticks since the top suggestion last changed (the tracker fades a new one in). */
    static int sinceChange() {
        return ticks - changedAt;
    }

    public static List<NowHooks.Suggestion> suggestions() {
        return current.suggestions();
    }

    /** " ↗ 40 m" for a suggestion with a place in the player's dimension; empty otherwise. */
    public static Optional<String> compass(NowHooks.Suggestion s) {
        Minecraft mc = Minecraft.getInstance();
        if (s.where().isEmpty() || mc.player == null) return Optional.empty();
        if (!mc.player.level().dimension().identifier().equals(current.dimension())) return Optional.empty();
        BlockPos at = s.where().get();
        double dx = at.getX() + 0.5 - mc.player.getX(), dz = at.getZ() + 0.5 - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 4) return Optional.of("here");
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        int idx = Math.floorMod((int) Math.round(rel / 45.0), 8);
        return Optional.of(ARROWS[idx] + " " + Math.round(dist) + " m");
    }

    static void clear() {
        version++;
        current = new SignalPayloads.Now(current.dimension(), List.of());
        topId = "";
    }
}
