package dev.awaken.hearth;

import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.town.Townsfolk;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds the owner's {@link HearthView} and pushes it when it changed. Cheap; call after anything it shows changes. */
public final class HearthViews {
    private HearthViews() {}

    public static void refresh(ServerPlayer sp) {
        HearthView next = build(sp);
        if (!next.equals(sp.getData(HearthContent.VIEW.get()))) sp.setData(HearthContent.VIEW.get(), next);
    }

    static HearthView build(ServerPlayer sp) {
        HearthPlayer hp = HearthService.player(sp);
        Optional<HearthRecord> ho = HearthService.homeOf(sp);
        int records = hp.bests.size();
        if (ho.isEmpty()) {
            HearthView.Home rested = new HearthView.Home("", hp.restedUntil, Comfort.restedMinutes(hp.restedComfort), 0, "");
            return new HearthView(false, BlockPos.ZERO, 0, 0, 0, List.of(), List.of(), 0, List.copyOf(hp.rumours), records, rested);
        }
        HearthRecord h = ho.get();
        Map<RoomType, Integer> counts = new EnumMap<>(RoomType.class);
        for (Room r : h.rooms) for (RoomType t : r.types()) counts.merge(t, 1, Integer::sum);
        List<String> rooms = new ArrayList<>();
        counts.forEach((t, n) -> rooms.add(n > 1 ? t.label + " ×" + n : t.label));
        List<String> town = new ArrayList<>();
        for (dev.awaken.hearth.settle.Folk.Settler s : h.folk.settlers) {
            if (s.home()) town.add(s.who.given() + " the " + dev.awaken.hearth.settle.Settlers.title(s.who));
        }
        List<BlockPos> greenhouses = new ArrayList<>();
        for (Room r : h.rooms) {
            if (!r.types().contains(RoomType.GREENHOUSE) || greenhouses.size() >= 16) continue;
            greenhouses.add(r.min());
            greenhouses.add(r.max());
        }
        HearthView.Home home = new HearthView.Home(h.displayName(), hp.restedUntil, Comfort.restedMinutes(hp.restedComfort), h.comfort, h.style,
                List.copyOf(greenhouses));
        return new HearthView(true, h.pos, h.tier, h.radius(), h.forgeTier, rooms, town, h.hallTrophies.size(), List.copyOf(hp.rumours),
                records, home);
    }
}
