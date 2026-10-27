package dev.awaken.trails.client;

import dev.awaken.trails.net.TrailPayloads;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** The client's copy of the player's trails, and what the Journal's TRAILS section is showing (docs/trails/PLAN.md §3.4). */
final class TrailCache {
    private TrailCache() {}

    static volatile TrailPayloads.@Nullable View view;
    /** The browse view is open, the area chosen in it, and the trails whose steps are unfolded. */
    static boolean browse;
    static String area = "";
    static final Set<String> expanded = new HashSet<>();

    static void onView(TrailPayloads.View v) {
        view = v;
    }

    static void act(int op, String trail, String step) {
        ClientPacketDistributor.sendToServer(new TrailPayloads.Act(op, trail, step));
    }

    static void reset() {
        view = null;
        browse = false;
        area = "";
        expanded.clear();
    }
}
