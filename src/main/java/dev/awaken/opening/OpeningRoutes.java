package dev.awaken.opening;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The run's routes (SPEC §16.4: three acts, three routes each: rooftop, canal, street), written by
 * {@code tools/opening/routes.py} into {@code data/awaken/opening/routes.json} from M1's plan, its roof graph and the
 * painted city. Read once from the mod's own resources on either side (never from data packs: the city is fixed). Points
 * are plan coordinates (blocks from Carillon's centre, heights from the street) with the move that reaches each one;
 * the stage list is the run's furniture (handholds, planks, a beam) placed into the standing city once.
 */
public final class OpeningRoutes {
    public static final int SCHEMA = 1;
    public static final String RESOURCE = "/data/awaken/opening/routes.json";

    /** How a point is reached from the one before. */
    public enum Move {
        RUN, JUMP, DROP, MANTLE, CLIMB, SWIM, WALLRUN_L, WALLRUN_R, WALLJUMP, HANG, SHIMMY, LEDGE_UP, SLIDE, WALK,
        /** A sprint-jump over a gap (JUMP is a hop up onto the next block). */
        LEAP;

        static Move of(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return RUN;
            }
        }

        /** The key a hesitating runner is shown for this move (the Runner's eye, second time). */
        public String key() {
            return switch (this) {
                case JUMP, LEAP, MANTLE, LEDGE_UP, WALLJUMP -> "jump";
                case WALLRUN_L, WALLRUN_R -> "sprint+jump";
                case HANG, DROP -> "sneak";
                case SHIMMY -> "left/right";
                case SLIDE -> "sneak";
                case SWIM -> "jump";
                default -> "sprint";
            };
        }
    }

    public record Point(float x, float ry, float z, Move move) {}

    public record Route(String name, int act, String kind, List<Point> points) {}

    public final List<Route> routes;
    final List<String[]> stage;
    public final String sha;

    private static volatile OpeningRoutes instance;

    public static OpeningRoutes get() {
        OpeningRoutes r = instance;
        if (r == null) {
            synchronized (OpeningRoutes.class) {
                r = instance;
                if (r == null) instance = r = load();
            }
        }
        return r;
    }

    private static OpeningRoutes load() {
        try (InputStream in = OpeningRoutes.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                Awaken.LOG.warn("[opening] no {}: the run has no routes", RESOURCE);
                return new OpeningRoutes(List.of(), List.of(), "");
            }
            byte[] raw = in.readAllBytes();
            return parse(new String(raw, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Awaken.LOG.warn("[opening] {} unreadable: {}", RESOURCE, e.toString());
            return new OpeningRoutes(List.of(), List.of(), "");
        }
    }

    static OpeningRoutes parse(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        int schema = o.get("schemaVersion").getAsInt();
        if (schema != SCHEMA) throw new IllegalStateException("routes schema " + schema + ", expected " + SCHEMA);
        List<Route> routes = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("routes")) {
            JsonObject r = e.getAsJsonObject();
            List<Point> pts = new ArrayList<>();
            for (JsonElement pe : r.getAsJsonArray("points")) {
                JsonArray a = pe.getAsJsonArray();
                pts.add(new Point(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat(),
                        a.size() > 3 ? Move.of(a.get(3).getAsString()) : Move.RUN));
            }
            routes.add(new Route(r.get("name").getAsString(), r.get("act").getAsInt(), r.get("kind").getAsString(), List.copyOf(pts)));
        }
        List<String[]> stage = new ArrayList<>();
        if (o.has("stage")) {
            for (JsonElement e : o.getAsJsonArray("stage")) {
                JsonArray a = e.getAsJsonArray();
                stage.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsString()});
            }
        }
        String sha = o.has("planSha256") ? o.get("planSha256").getAsString() : "";
        return new OpeningRoutes(List.copyOf(routes), List.copyOf(stage), sha);
    }

    private OpeningRoutes(List<Route> routes, List<String[]> stage, String sha) {
        this.routes = routes;
        this.stage = stage;
        this.sha = sha;
    }

    public Route route(String name) {
        for (Route r : routes) if (r.name().equals(name)) return r;
        return null;
    }

    /** Places the run's furniture into the standing city (once per world: OpeningStage keeps the version). */
    int buildStage(ServerLevel level, OpeningPlaces at) {
        int n = 0;
        for (String[] s : stage) {
            n += OpeningStage.set(level, at, Integer.parseInt(s[0]), Integer.parseInt(s[1]), Integer.parseInt(s[2]), StageBlocks.of(s[3]));
        }
        return n;
    }
}
