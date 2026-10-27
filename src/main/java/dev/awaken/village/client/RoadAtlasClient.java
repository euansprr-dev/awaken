package dev.awaken.village.client;

import dev.awaken.core.api.MapMarks;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.road.RoadAtlas;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The roads and settlements on the client's Atlas (SPEC §28.9): the stretches of road this player has walked, drawn as
 * lines on the land they know (a highroad wider and paler than a lane), and the settlements they've found, marked and
 * named in their kind's colour.
 */
public final class RoadAtlasClient {
    private RoadAtlasClient() {}

    private static final List<RoadAtlas.Walked> WALKED = new CopyOnWriteArrayList<>();
    private static final List<RoadAtlas.Found> FOUND = new CopyOnWriteArrayList<>();

    static void sync(RoadAtlas.Sync msg) {
        if (msg.whole()) {
            WALKED.clear();
            FOUND.clear();
        }
        WALKED.addAll(msg.state().walked());
        FOUND.addAll(msg.state().found());
    }

    static void init() {
        MapMarks.registerLines(p -> {
            List<MapMarks.Line> out = new ArrayList<>(WALKED.size());
            for (RoadAtlas.Walked w : WALKED) {
                int[] xz = new int[w.xz().size()];
                for (int i = 0; i < xz.length; i++) xz[i] = w.xz().get(i);
                out.add(new MapMarks.Line(xz, w.cls() == 0 ? 0xE8D2A6 : 0xC9AE80, w.cls() == 0 ? 2 : 1));
            }
            return out;
        });
        MapMarks.register(p -> {
            List<MapMarks.Mark> out = new ArrayList<>(FOUND.size());
            for (RoadAtlas.Found f : FOUND) out.add(new MapMarks.Mark(new BlockPos(f.x(), 64, f.z()), colour(f.kind()), label(f), true));
            return out;
        });
    }

    static int colour(int kind) {
        VillagePlan.Kind[] ks = VillagePlan.Kind.values();
        return switch (ks[Math.clamp(kind, 0, ks.length - 1)]) {
            case TOWN -> 0xFF9A5A;
            case MINING -> 0xB8C4D8;
            case WAYHOUSE -> 0x9CE6A8;
            case VILLAGE -> 0xF2C66B;
            default -> 0xF0DDB0;
        };
    }

    static String label(RoadAtlas.Found f) {
        VillagePlan.Kind[] ks = VillagePlan.Kind.values();
        return switch (ks[Math.clamp(f.kind(), 0, ks.length - 1)]) {
            case TOWN -> f.name() + " · town";
            case MINING -> f.name() + " · mining town";
            case WAYHOUSE -> f.name() + " · inn";
            default -> f.name();
        };
    }
}
