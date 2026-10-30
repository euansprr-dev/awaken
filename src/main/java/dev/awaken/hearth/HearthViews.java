package dev.awaken.hearth;

import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.hearth.room.Furniture;
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
                records, home, tiles(sp, h), looks(h));
    }

    /** How many rooms "Make it look good" lists (the weakest first). */
    static final int LOOKS = 8;

    /** The pieces only found in the world (a lode heart, a perch): a kind made of one stays "?" until you've come across it. */
    private static final java.util.Set<Furniture> RARE = java.util.EnumSet.of(Furniture.TROPHY, Furniture.MACHINE, Furniture.LODE_HEART,
            Furniture.VENT_STONE, Furniture.PERCH, Furniture.CRUCIBLE, Furniture.DROP, Furniture.LEDGER);

    /** (SPEC §27.13) Every room kind as a tile, in the order the home grows: built, started, open, or "?". */
    static List<HearthView.Tile> tiles(ServerPlayer sp, HearthRecord h) {
        List<HearthView.Tile> out = new ArrayList<>();
        for (RoomType t : HearthNow.ORDER) {
            if (h.hasRoom(t)) {
                out.add(new HearthView.Tile(t.name(), HearthView.Tile.BUILT, 0, true));
                continue;
            }
            boolean hidden = false;
            for (Furniture f : t.furniture) {
                if (!RARE.contains(f)) continue;
                boolean met = h.furniture.length > f.ordinal() && h.furniture[f.ordinal()] > 0 || HearthNow.holds(sp, f.icon().getItem());
                if (!met) hidden = true;
            }
            if (hidden) {
                out.add(new HearthView.Tile(t.name(), HearthView.Tile.HIDDEN, 0, false));
                continue;
            }
            if (t.furniture.isEmpty()) {
                out.add(new HearthView.Tile(t.name(), HearthView.Tile.OPEN, 0, false));
                continue;
            }
            var c = dev.awaken.hearth.guide.Guide.card(t, h);
            out.add(new HearthView.Tile(t.name(), c.have() != 0 ? HearthView.Tile.STARTED : HearthView.Tile.OPEN, c.have(), c.closed()));
        }
        return out;
    }

    /** (SPEC §27.13) The rooms as they read, the weakest first: what to lift to make the home look good. */
    static List<HearthView.Look> looks(HearthRecord h) {
        List<Room> rooms = new ArrayList<>(h.rooms);
        rooms.sort(java.util.Comparator.comparingInt((Room r) -> r.reading().grade().ordinal()).thenComparingInt(r -> r.reading().weakest()));
        List<HearthView.Look> out = new ArrayList<>();
        for (Room r : rooms) {
            if (out.size() >= LOOKS) break;
            var rd = r.reading();
            List<Integer> at = new ArrayList<>(6);
            for (int i = 0; i < 6; i++) at.add(rd.at(i));
            StringBuilder kinds = new StringBuilder();
            for (RoomType t : r.types()) {
                if (t == RoomType.SHELTER && r.types().size() > 1) continue;
                kinds.append(kinds.isEmpty() ? "" : " · ").append(t.label);
            }
            out.add(new HearthView.Look(rd.name(), rd.grade().ordinal(), List.copyOf(at), rd.lift(), kinds.toString()));
        }
        return out;
    }
}
