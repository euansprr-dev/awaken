package dev.awaken.gates.worlds;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.WorldPayloads;
import dev.awaken.gates.world.FloorLayout;
import net.minecraft.server.level.ServerPlayer;

/** Gate Worlds while you play (SPEC §38): arriving fills your Atlas and tells your client which world this is. */
public final class WorldsLive {
    private WorldsLive() {}

    /** On arriving on a floor (after the teleport). */
    public static void arrive(ServerPlayer p, GateRecord g, FloorLayout f) {
        if (f == null || f.world.isEmpty()) {
            AwakenNet.send(p, WorldPayloads.Arrival.none(g.id, f == null ? 0 : f.area, f == null ? 0 : f.floor));
            return;
        }
        GateWorld w = GateWorlds.get(f.world);
        GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
        boolean first = atlas.arrive(f.world, f.mood, PlayDays.current(p.level().getServer()));
        p.setData(GatesRegistry.ATLAS.get(), atlas);
        GateWorld.Mood m = w == null ? null : w.mood(f.mood);
        AwakenNet.send(p, new WorldPayloads.Arrival(g.id, f.area, f.floor, f.world, w == null ? "" : w.name(), f.mood,
                m == null || m.name().isEmpty() ? "" : m.name(), w == null ? "" : w.rarity().label(),
                w == null ? "" : w.tier().name().toLowerCase(java.util.Locale.ROOT), w == null ? "" : w.line(), first,
                GateService.world(g, f, 0, 0, 0), f.air));
    }

    /** An ordinary floor cleared: mastery of its theme grows. */
    public static void cleared(ServerPlayer p, FloorLayout f) {
        GateAtlas atlas = p.getData(GatesRegistry.ATLAS.get());
        atlas.floorCleared(f.theme.id);
        p.setData(GatesRegistry.ATLAS.get(), atlas);
    }
}
