package dev.awaken.gates.worlds;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.net.WorldPayloads;
import dev.awaken.gates.world.FloorAir;
import dev.awaken.gates.world.FloorLayout;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Gate Worlds while you play (SPEC §38): arriving fills your Atlas and tells your client which world this is. */
public final class WorldsLive {
    private WorldsLive() {}

    /** On arriving on a floor (after the teleport). */
    public static void arrive(ServerPlayer p, GateRecord g, FloorLayout f) {
        WorldLive.left(p);
        if (f == null || f.world.isEmpty()) {
            AwakenNet.send(p, WorldPayloads.Arrival.none(g.id, f == null ? 0 : f.area, f == null ? 0 : f.floor));
            return;
        }
        GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
        boolean first = atlas.arrive(f.world, f.mood, PlayDays.current(p.level().getServer()));
        p.setData(GatesRegistry.ATLAS.get(), atlas);
        send(p, g, f, f.world, f.mood, first, true);
        sync(p);
        WorldLive.arrived(p, g, f, f.world);
    }

    /**
     * Stepping into a place that's a world of its own (the Bellmaker's Rest off the Belfry's Rope Shaft): it's found like
     * any world, with its own card and sound, while the floor stays the floor (SPEC §38.8).
     */
    public static void arrivePlace(ServerPlayer p, GateRecord g, FloorLayout f, String world) {
        GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
        boolean first = atlas.arrive(world, "", PlayDays.current(p.level().getServer()));
        p.setData(GatesRegistry.ATLAS.get(), atlas);
        send(p, g, f, world, "", first, true);
        sync(p);
        WorldLive.arrived(p, g, f, world);
    }

    /** Tells your client which world this is again, with no card and no visit counted (out of a place, after the Flood). */
    public static void resend(ServerPlayer p, GateRecord g, FloorLayout f) {
        if (f.world.isEmpty()) return;
        send(p, g, f, f.world, f.mood, false, false);
    }

    private static void send(ServerPlayer p, GateRecord g, FloorLayout f, String world, String mood, boolean first, boolean card) {
        GateWorld w = GateWorlds.get(world);
        GateWorld.Mood m = w == null || mood.isEmpty() ? null : w.mood(mood);
        AwakenNet.send(p, new WorldPayloads.Arrival(g.id, f.area, f.floor, world, w == null ? "" : w.name(), mood,
                m == null || m.name().isEmpty() ? "" : m.name(), w == null ? "" : w.rarity().label(),
                w == null ? "" : w.tier().name().toLowerCase(Locale.ROOT), w == null ? "" : w.line(), where(g, f),
                w == null ? "" : w.dressing().sound(), w == null ? List.of() : w.dressing().life(), first, card,
                GateService.world(g, f, 0, 0, 0), WorldLive.air(g, f)));
    }

    /** The title card's floor line: the toast it replaces on a world floor. */
    static String where(GateRecord g, FloorLayout f) {
        String gate = f.area == 1 ? (g.plan.is(Twist.GATE_WITHIN) ? "Inner gate · " : "Second gate · ") : "";
        return gate + "Floor " + f.floor + (f.bossFloor ? " · the Lord waits" : "");
    }

    /** Breaking the Seal down to a world floor: sent just before the teleport, so the screen is dark when it lands. */
    public static void journey(ServerPlayer p, FloorLayout next) {
        if (next == null || next.world.isEmpty()) return;
        GateWorld w = GateWorlds.get(next.world);
        AwakenNet.send(p, new WorldPayloads.Journey(next.world, w == null ? "" : w.dressing().sound(), glow(next.air)));
    }

    /** The light the next world rises in: the mean colour of its first lights, or a candle's. */
    static int glow(FloorAir air) {
        int n = Math.min(16, air.lights().size());
        if (n == 0) return 0xFFB060;
        int r = 0, gr = 0, b = 0;
        for (int i = 0; i < n; i++) {
            int c = air.lights().get(i).rgb();
            r += c >> 16 & 0xFF;
            gr += c >> 8 & 0xFF;
            b += c & 0xFF;
        }
        return (r / n) << 16 | (gr / n) << 8 | b / n;
    }

    /** An ordinary floor cleared: mastery of its theme grows. */
    public static void cleared(ServerPlayer p, FloorLayout f) {
        GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
        atlas.floorCleared(f.theme.id);
        p.setData(GatesRegistry.ATLAS.get(), atlas);
    }

    /** Sends your Atlas to your client (on login and whenever it changes). */
    public static void sync(ServerPlayer p) {
        AwakenNet.send(p, view(p.getData(GatesRegistry.ATLAS.get())));
    }

    /**
     * Your Atlas as the Codex shows it: every enabled world in theme, tier and name order; unfound ones carry only their
     * theme, tier and rarity. Secret worlds count in the total but get no card until found.
     */
    public static WorldPayloads.AtlasView view(GateAtlas atlas) {
        List<java.util.Map.Entry<String, GateWorld>> all = new ArrayList<>();
        for (var e : GateWorlds.all().entrySet()) if (e.getValue().enabled() || atlas.entry(e.getKey()) != null) all.add(e);
        all.sort(Comparator.<java.util.Map.Entry<String, GateWorld>>comparingInt(e -> e.getValue().theme().ordinal())
                .thenComparingInt(e -> e.getValue().tier().ordinal()).thenComparing(e -> e.getValue().name()));
        List<WorldPayloads.Card> cards = new ArrayList<>();
        int found = 0;
        for (var e : all) {
            GateWorld w = e.getValue();
            GateAtlas.Entry a = atlas.entry(e.getKey());
            String tier = w.tier().name().toLowerCase(Locale.ROOT);
            if (a == null) {
                if (w.tier() != GateWorld.Tier.SECRET) {
                    cards.add(new WorldPayloads.Card(e.getKey(), w.theme().id, tier, w.rarity().label(), false, "", "", List.of(),
                            w.moods().size(), List.of(), w.dressing().secrets().size(), "", 0, false));
                }
                continue;
            }
            found++;
            List<String> moods = new ArrayList<>();
            for (GateWorld.Mood m : w.moods()) if (a.moodVisits(m.id()) > 0) moods.add(m.name().isEmpty() ? pretty(m.id()) : m.name());
            List<String> secrets = new ArrayList<>();
            for (GateWorld.Secret s : w.dressing().secrets()) if (a.secrets.contains(s.id())) secrets.add(s.name());
            String sig = w.dressing().signature();
            String met = !sig.isEmpty() && atlas.creatures.contains(sig) ? pretty(sig) : "";
            cards.add(new WorldPayloads.Card(e.getKey(), w.theme().id, tier, w.rarity().label(), true, w.name(), w.line(), moods,
                    w.moods().size(), secrets, w.dressing().secrets().size(), met, a.visits, a.keepsake));
        }
        return new WorldPayloads.AtlasView(found, all.size(), cards);
    }

    /** "the_ringer" → "The Ringer". */
    static String pretty(String id) {
        String s = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        StringBuilder out = new StringBuilder();
        for (String word : s.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
