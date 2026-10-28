package dev.awaken.delve.cave;

import dev.awaken.Awaken;
import dev.awaken.core.api.Destinations;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.delve.Lode;
import dev.awaken.delve.LodePlans;
import dev.awaken.delve.Ore;
import dev.awaken.progression.Checklist;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The Hearth points (SPEC §34.1): when your home's next tier is short only of a metal, the task card says how much you
 * need and, in plain words, where the old Delvers left some: the nearest mouth within {@value #RADIUS} of the home whose
 * way in reaches a planned lode of that metal (an adit driven to it, or a hill mouth whose cave runs past one), found
 * from the seed alone off the server thread (no chunk is loaded). A window offers to mark it on your map; the
 * destination is set only on that click. With none, it says so honestly and never invents a place.
 */
public final class MouthPointer {
    private MouthPointer() {}

    static final int RADIUS = 400, AT_HOME = 160, LODE_NEAR = 48, LODE_MIN = 20;
    static final String ACTION = "delve_mark";

    /** What the search found for one home and metal: the mouth, or nothing (a finished search with no answer). */
    record Answer(BlockPos home, String metal, @Nullable Spec mouth) {}

    private record Asked(BlockPos home, String metal, CompletableFuture<Answer> answer) {}

    private static final Map<UUID, Asked> ASKED = new HashMap<>();
    /** The answers whose window was sent, per player (home + metal), so it's offered once. */
    private static final Map<UUID, String> OFFERED = new HashMap<>();
    private static final Map<UUID, Destinations.Target> PENDING = new HashMap<>();

    static void register() {
        Checklist.register(MouthPointer::task);
        Signal.onAction(ACTION, MouthPointer::answer);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            UUID id = e.getEntity().getUUID();
            ASKED.remove(id);
            OFFERED.remove(id);
            PENDING.remove(id);
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            ASKED.clear();
            OFFERED.clear();
            PENDING.clear();
        });
    }

    /** The task card's metal task: at home, while the next tier is short only of a metal. */
    static Checklist.@Nullable Task task(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return null;
        Optional<HearthHooks.MetalNeed> got = HearthHooks.metalNeed(p);
        if (got.isEmpty()) return null;
        HearthHooks.MetalNeed need = got.get();
        if (p.blockPosition().distSqr(need.home()) > (double) AT_HOME * AT_HOME) return null;
        String title = cap(need.metal()) + " for your home";
        Checklist.Row gather = new Checklist.Row("Gather " + need.metal() + " ingots for " + need.what(), "", false, need.have(), need.need(), null);
        Answer a = ask(p, level, need);
        if (a == null) return new Checklist.Task("delve:metal", title, List.of(gather));
        if (a.mouth() == null) return new Checklist.Task("delve:metal", title, List.of(gather, Checklist.Row.of(honest(need.metal()), false)));
        offer(p, a);
        BlockPos at = new BlockPos(a.mouth().x(), a.mouth().y(), a.mouth().z());
        // the mouth is a hint on the card's compass; the Now line only follows it once you mark it
        return new Checklist.Task("delve:metal", title, List.of(gather, new Checklist.Row(line(a.mouth(), a.home(), need.metal()), "", false, 0, 0, at, "")));
    }

    /** The answer for this home and metal if the search is done; starts it (off the server thread) when not asked yet. */
    private static @Nullable Answer ask(ServerPlayer p, ServerLevel level, HearthHooks.MetalNeed need) {
        Asked asked = ASKED.get(p.getUUID());
        if (asked == null || !asked.home().equals(need.home()) || !asked.metal().equals(need.metal())) {
            BlockPos home = need.home();
            String metal = need.metal();
            CompletableFuture<Answer> f = CompletableFuture.supplyAsync(() -> find(level, home, metal), Util.backgroundExecutor())
                    .exceptionally(t -> {
                        Awaken.LOG.warn("[delve] the Hearth's mouth search failed", t);
                        return new Answer(home, metal, null);
                    });
            asked = new Asked(home, metal, f);
            ASKED.put(p.getUUID(), asked);
        }
        return asked.answer().getNow(null);
    }

    /** The nearest mouth within {@value #RADIUS} of the home whose way in reaches a lode of the metal (pure: the seed's plan). */
    static Answer find(ServerLevel level, BlockPos home, String metal) {
        Ore ore = ore(metal);
        Spec s = ore == null ? null : CaveLocate.nearest(level, home, CaveKind.MOUTH, RADIUS, false, m -> leads(level.getSeed(), m, ore));
        return new Answer(home, metal, s);
    }

    /** Whether a mouth's way in reaches a lode of {@code ore}: an adit driven to one, or a lode of 20+ near its throat's end or joint. */
    static boolean leads(long seed, Spec s, Ore ore) {
        if (s.p(Mouth.FORM) == Mouth.ADIT) return s.p(Mouth.ORE) == ore.ordinal() + 1;
        int[] e = Mouth.end(s);
        int[][] near = s.joined() ? new int[][]{e, {s.jx(), s.jy(), s.jz()}} : new int[][]{e};
        for (int[] q : near) {
            int x0 = q[0] - LODE_NEAR, z0 = q[2] - LODE_NEAR, x1 = q[0] + LODE_NEAR, z1 = q[2] + LODE_NEAR;
            for (int rx = Math.floorDiv(x0, LodePlans.REGION); rx <= Math.floorDiv(x1, LodePlans.REGION); rx++) {
                for (int rz = Math.floorDiv(z0, LodePlans.REGION); rz <= Math.floorDiv(z1, LodePlans.REGION); rz++) {
                    for (Lode l : LodePlans.region(seed, rx, rz)) {
                        if (l.ore() != ore || l.size() < LODE_MIN || !l.touches(x0, z0, x1, z1)) continue;
                        if (Math.abs(BlockPos.getY(l.heart()) - q[1]) <= LODE_NEAR / 2) return true;
                    }
                }
            }
        }
        return false;
    }

    /** What the card says of a mouth, from home: "The old Delvers cut an adit into the hillside 210 m north-east of your home. ..." */
    static String line(Spec s, BlockPos home, String metal) {
        double dx = s.x() - home.getX(), dz = s.z() - home.getZ();
        int m = Math.max(10, (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 10.0) * 10);
        String where = m + " m " + compass(dx, dz) + " of your home";
        return s.p(Mouth.FORM) == Mouth.ADIT
                ? "The old Delvers cut an adit into the hillside " + where + ". It should still have " + metal + " in it."
                : "There's a cave mouth in the hillside " + where + ". The cave behind it runs past " + metal + ".";
    }

    static String honest(String metal) {
        return "I don't know of any old workings near your home. " + cap(metal) + " shows in cave walls: find a cave and follow its walls.";
    }

    static String compass(double dx, double dz) {
        String[] dirs = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        double a = Math.atan2(dx, -dz); // 0 = north (-z), clockwise
        return dirs[Math.floorMod((int) Math.round(a / (Math.PI / 4)), 8)];
    }

    /** Once per home and metal: the window that can put the mouth on your map. */
    private static void offer(ServerPlayer p, Answer a) {
        Spec s = a.mouth();
        if (s == null) return;
        String key = a.home().asLong() + ":" + a.metal() + ":" + s.id();
        if (key.equals(OFFERED.get(p.getUUID()))) return;
        OFFERED.put(p.getUUID(), key);
        boolean adit = s.p(Mouth.FORM) == Mouth.ADIT;
        PENDING.put(p.getUUID(), new Destinations.Target(adit ? "The old adit" : "The cave mouth", Level.OVERWORLD.identifier(),
                new BlockPos(s.x(), s.y(), s.z()), "spot"));
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Old workings"))
                .body(Component.literal(line(s, a.home(), a.metal())))
                .choice(ACTION + ":mark", "Mark it on my map", "Not now").topic("delve_mark").ttl(20 * 60).build());
    }

    private static void answer(ServerPlayer p, String action, boolean yes) {
        Destinations.Target t = PENDING.remove(p.getUUID());
        if (yes && t != null) Destinations.set(p, t);
    }

    static @Nullable Ore ore(String metal) {
        return switch (metal) {
            case "iron" -> Ore.IRON;
            case "copper" -> Ore.COPPER;
            case "gold" -> Ore.GOLD;
            default -> null;
        };
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
