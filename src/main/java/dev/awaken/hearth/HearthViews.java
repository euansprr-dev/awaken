package dev.awaken.hearth;

import dev.awaken.hearth.echo.Duty;
import dev.awaken.hearth.echo.EchoRecord;
import dev.awaken.hearth.echo.EchoRole;
import dev.awaken.hearth.echo.Echoes;
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
import java.util.UUID;

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
        long now = sp.level().getGameTime();
        // pads are worked by the owner's Echoes: a member's Echoes never show as on duty at a shared Hearth
        Map<UUID, Duty.Assignment> duties = ho.filter(h -> h.owner.equals(sp.getUUID())).map(h -> Duty.assignments(hp, h)).orElse(Map.of());
        List<HearthView.EchoView> echoes = new ArrayList<>();
        for (EchoRecord e : hp.echoes) {
            Duty.Assignment a = duties.get(e.id());
            echoes.add(new HearthView.EchoView(e.id(), e.name(), e.level(), e.field(), a == null ? "" : a.role().id, e.downUntil() > now, e.isBoss()));
        }
        int records = hp.bests.size();
        if (ho.isEmpty()) {
            return new HearthView(false, BlockPos.ZERO, 0, 0, 0, List.of(), List.of(), 0, Echoes.fieldCap(sp), 0, hp.fieldOut,
                    echoes, List.copyOf(hp.rumours), records);
        }
        HearthRecord h = ho.get();
        Map<RoomType, Integer> counts = new EnumMap<>(RoomType.class);
        for (Room r : h.rooms) for (RoomType t : r.types()) counts.merge(t, 1, Integer::sum);
        List<String> rooms = new ArrayList<>();
        counts.forEach((t, n) -> rooms.add(n > 1 ? t.label + " ×" + n : t.label));
        List<String> town = new ArrayList<>();
        for (String id : h.arrived) town.add(Townsfolk.displayName(id));
        return new HearthView(true, h.pos, h.tier, h.radius(), h.forgeTier, rooms, town, h.hallTrophies.size(), Echoes.fieldCap(sp),
                h.pads.size(), hp.fieldOut, echoes, List.copyOf(hp.rumours), records);
    }
}
