package dev.awaken.above.client;

import dev.awaken.above.AboveData;
import dev.awaken.above.MachineType;
import dev.awaken.above.logs.PrecursorLogs;
import dev.awaken.above.net.AboveNet;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.progression.Progression;
import dev.awaken.progression.client.CodexEntries;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** The client's copy of the Above state (from the server's sync payloads) and the Codex entries built from it. */
public final class ClientAbove {
    private ClientAbove() {}

    static AboveData data = new AboveData();
    static List<AboveNet.GateMark> gateMarks = List.of();
    static long gateMarksAt;

    public static AboveData data() {
        return data;
    }

    static void reset() {
        data = new AboveData();
        gateMarks = List.of();
    }

    /** The Gate Scanner's last reading, for the Atlas: each gate it read, marked as a reading (SPEC §30). */
    static List<dev.awaken.core.api.MapMarks.Gate> gates(net.minecraft.world.entity.player.Player player) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return List.of();
        List<dev.awaken.core.api.MapMarks.Gate> out = new ArrayList<>();
        for (AboveNet.GateMark m : gateMarks) {
            var rank = dev.awaken.progression.Rank.of(m.rank());
            String when = "your scanner read it · " + (m.breaksIn() == AboveNet.GateMark.WAITING ? "it won't break until someone goes near it"
                    : m.breaksIn() == AboveNet.GateMark.NEVER ? "it never breaks"
                    : "breaks in " + m.breaksIn() + (m.breaksIn() == 1 ? " day" : " days"));
            out.add(new dev.awaken.core.api.MapMarks.Gate(new net.minecraft.core.BlockPos(m.x(), 64, m.z()), rank.label, rank.colour,
                    m.name().isEmpty() ? rank.label + "-rank gate" : m.name(), when, false, true));
        }
        return out;
    }

    private static List<CodexEntries.Entry> cached = List.of();
    private static Object cachedData, cachedLogs;
    private static boolean cachedReached;

    /** Only what the player has found: blueprints scanned, logs read, ruins found, the Halo once it has been seen. */
    static List<CodexEntries.Entry> codex() {
        var mc = Minecraft.getInstance();
        boolean reached = mc.player != null && Progression.profile(mc.player).counter("awaken:story/halo_reached") > 0;
        Object logs = PrecursorLogs.all();
        if (cachedData == data && cachedLogs == logs && cachedReached == reached) return cached;
        cached = List.copyOf(build(reached));
        cachedData = data;
        cachedLogs = logs;
        cachedReached = reached;
        return cached;
    }

    private static List<CodexEntries.Entry> build(boolean reached) {
        List<CodexEntries.Entry> out = new ArrayList<>();
        AboveData d = data;
        String reactor = "above/bp/" + MachineType.REACTOR.id;
        for (MachineType t : MachineType.values()) {
            if (!d.knows(t)) continue;
            StringBuilder parts = new StringBuilder();
            for (MachineType.Part p : t.parts()) {
                if (!parts.isEmpty()) parts.append(", ");
                parts.append(p.count()).append(' ').append(p.item().get().getName(p.stack()).getString());
            }
            boolean built = d.built().contains(t.id);
            out.add(new CodexEntries.Entry("above/bp/" + t.id, "Precursor Machines", t.displayName + (built ? " ✓" : ""),
                    t.blurb + " Parts: " + parts + ".", true, t == MachineType.REACTOR ? List.of() : List.of(reactor)));
        }
        String prevAscendant = null, prevKeeper = null, prevSleeper = null, prevUnknown = null;
        for (String id : d.logs()) {
            PrecursorLogs.LogDef def = PrecursorLogs.get(id);
            if (def == null) continue;
            String entryId = "above/log/" + id;
            String prev = switch (def.faction()) {
                case "ascendant" -> prevAscendant;
                case "keeper" -> prevKeeper;
                case "sleeper" -> prevSleeper;
                default -> prevUnknown;
            };
            out.add(new CodexEntries.Entry(entryId, "Precursor Logs", def.title() + " · " + PrecursorLogs.faction(def.faction()), def.text(), true,
                    prev == null ? List.of() : List.of(prev)));
            switch (def.faction()) {
                case "ascendant" -> prevAscendant = entryId;
                case "keeper" -> prevKeeper = entryId;
                case "sleeper" -> prevSleeper = entryId;
                default -> prevUnknown = entryId;
            }
        }
        for (int i = 0; i < d.ruins().size(); i++) {
            AboveData.RuinMark r = d.ruins().get(i);
            RuinKind k = RuinKind.byId(r.kind());
            out.add(new CodexEntries.Entry("above/ruin/" + i, "Ruins", k == null ? "Precursor ruin" : k.displayName,
                    "Found at " + r.center().getX() + ", " + r.center().getZ() + ". A Waypoint Pylon can take you back.", true, List.of()));
        }
        if (d.haloSeen() || reached) {
            out.add(new CodexEntries.Entry("above/halo", "The Above", "The Halo",
                    reached ? "The Ascendants' station in orbit. Their archive is there. So is the view."
                            : "A Precursor station in orbit. A Launch Ring could reach it.",
                    true, prevAscendant == null ? List.of() : List.of(prevAscendant)));
        } else if (d.logs().contains("awaken:a03_leaving")) {
            // heard of it, not found: a "?" card
            out.add(new CodexEntries.Entry("above/halo", "The Above", "Where they went up", "Something above the sky. They built a ring to reach it.",
                    false, List.of("above/log/awaken:a03_leaving")));
        }
        return out;
    }
}
