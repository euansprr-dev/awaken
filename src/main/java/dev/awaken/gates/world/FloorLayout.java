package dev.awaken.gates.world;

import dev.awaken.gates.content.Theme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One floor: a grid of slots, the rooms on it, and where it starts and ends. Immutable once built. A world floor (SPEC
 * §38) also carries its plot, the place map its rooms (places) are found by, its world and mood, the biome it shows, its
 * Gate Air data and the model that carves it.
 */
public final class FloorLayout {
    /** 0 = main dungeon, 1 = annex (Double Gate second dungeon or the inner gate). */
    public final int area;
    /** 1-based floor number inside its area. */
    public final int floor;
    public final int baseY;
    /** Grid origin in cell slots. */
    public final int originSlotX, originSlotZ;
    public final int w, h;
    public final Theme theme;
    public final boolean bossFloor;
    public final List<Room> rooms;
    private final int[] slotRoom;
    public final int start;
    /** Shrine room, boss arena or annex exit room. */
    public final int end;
    /** Monsters planned on this floor (kill XP is split across them). */
    public final int plannedMobs;
    /** The floor's plot (content version 6+), or null for the stacked bands of older gates. */
    public final Plots.Plot plot;
    /** The y range this floor paints (both inclusive): its band, or a world's height inside its plot. */
    public final int yLo, yHi;
    /** Places of a world floor, any shape and stacked; null on rooms floors (the slot grid answers). */
    public final PlaceMap places;
    /** The world recipe id and mood ("" on rooms floors), and the biome the floor shows ("" = the theme's). */
    public final String world, mood, biome;
    public final FloorAir air;
    /** Carves a world floor's chunks; null on rooms floors ({@link GatePainter} paints them). */
    public final WorldModel model;
    /** Which way you face on arriving (a world's best view), or NaN: toward the start room's first door. */
    public final float arrivalYaw;
    /** Places that are a world of their own inside this one (a secret world behind an odd rope): room id → world id. */
    public final Map<Integer, String> placeWorlds;
    /** The biome each of those places shows (its own world's). */
    public final Map<Integer, String> placeBiomes;

    /** Spots a world's live layer finds again (SPEC §38.8), floor-local x/z and y above the base like Gate Air. */
    public final List<Mark> marks;

    /**
     * One spot or box: a bell (its lip's centre ± its radius, lip to crown), a secret (where it's found; {@code id} the
     * recipe's secret), a basin the Flood fills, an event's trigger ({@code id} the event).
     */
    public record Mark(String kind, String id, int x0, int y0, int z0, int x1, int y1, int z1) {
        public static final String BELL = "bell", SECRET = "secret", BASIN = "basin", TRIGGER = "trigger";
        /** Something the live layer shows as a display entity and moves (a swinging bell: id "bell:" + 10 x radius). */
        public static final String PROP = "prop";
        /** A bell mark's id for a handbell on the floor (the rest are hung bells; "still" and "lowest" are named ones). */
        public static final String HANDBELL = "handbell";

        public double cx() { return (x0 + x1 + 1) / 2.0; }
        public double cz() { return (z0 + z1 + 1) / 2.0; }
        /** Half its width: a hung bell's radius. */
        public double r() { return (x1 - x0 + 1) / 2.0; }
        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    /** What a floor is framed by beyond its rooms: where it stands, how it's found, what it is. */
    record Frame(Plots.Plot plot, int yLo, int yHi, PlaceMap places, String world, String mood, String biome, FloorAir air,
                 WorldModel model, float arrivalYaw, Map<Integer, String> placeWorlds, Map<Integer, String> placeBiomes, List<Mark> marks) {
        Frame {
            placeWorlds = Map.copyOf(placeWorlds);
            placeBiomes = Map.copyOf(placeBiomes);
            marks = List.copyOf(marks);
        }

        static Frame band(Plots.Plot plot, int baseY) {
            return new Frame(plot, baseY - Cells.BAND_BELOW, baseY + Cells.BAND_ABOVE - 1, null, "", "", "", FloorAir.NONE, null, Float.NaN, Map.of(), Map.of(),
                    List.of());
        }
    }

    /** The marks of a kind. */
    public List<Mark> marks(String kind) {
        List<Mark> out = new java.util.ArrayList<>();
        for (Mark m : marks) if (m.kind().equals(kind)) out.add(m);
        return out;
    }

    FloorLayout(int area, int floor, int baseY, int originSlotX, int originSlotZ, int w, int h, Theme theme,
                boolean bossFloor, List<Room> rooms, int start, int end) {
        this(area, floor, baseY, originSlotX, originSlotZ, w, h, theme, bossFloor, rooms, start, end, Frame.band(null, baseY));
    }

    FloorLayout(int area, int floor, int baseY, int originSlotX, int originSlotZ, int w, int h, Theme theme,
                boolean bossFloor, List<Room> rooms, int start, int end, Frame frame) {
        this.area = area;
        this.floor = floor;
        this.baseY = baseY;
        this.originSlotX = originSlotX;
        this.originSlotZ = originSlotZ;
        this.w = w;
        this.h = h;
        this.theme = theme;
        this.bossFloor = bossFloor;
        this.rooms = Collections.unmodifiableList(rooms);
        this.slotRoom = new int[w * h];
        Arrays.fill(slotRoom, -1);
        int planned = 0;
        for (Room r : rooms) {
            for (int x = r.gx; x < r.gx + r.gw; x++) for (int z = r.gz; z < r.gz + r.gh; z++) slotRoom[x + z * w] = r.id;
            planned += r.normals + r.elites + r.wave2Normals + r.wave2Elites;
        }
        this.start = start;
        this.end = end;
        this.plannedMobs = planned;
        this.plot = frame.plot();
        this.yLo = frame.yLo();
        this.yHi = frame.yHi();
        this.places = frame.places();
        this.world = frame.world();
        this.mood = frame.mood();
        this.biome = frame.biome();
        this.air = frame.air();
        this.model = frame.model();
        this.arrivalYaw = frame.arrivalYaw();
        this.placeWorlds = frame.placeWorlds();
        this.placeBiomes = frame.placeBiomes();
        this.marks = frame.marks();
    }

    /** The world a place belongs to: its own (a secret world inside this floor's) or the floor's. */
    public String worldOf(Room r) {
        return r == null ? world : placeWorlds.getOrDefault(r.id, world);
    }

    /** Whether this floor paints height y (tests and tools filter a chunk's blocks by floor with it). */
    public boolean paints(int y) {
        return y >= yLo && y <= yHi;
    }

    public boolean isWorld() {
        return model != null;
    }

    public Room room(int id) {
        return id >= 0 && id < rooms.size() ? rooms.get(id) : null;
    }

    /** Room at grid slot, or null. */
    public Room roomAtSlot(int gx, int gz) {
        if (gx < 0 || gz < 0 || gx >= w || gz >= h) return null;
        int id = slotRoom[gx + gz * w];
        return id < 0 ? null : rooms.get(id);
    }

    /** Room at cell-local block coordinates (x, z inside the cell), or null. On a world floor: the ground place. */
    public Room roomAtLocal(int cellLocalX, int cellLocalZ) {
        if (places != null) return room(places.groundAt(toFloorX(cellLocalX), toFloorZ(cellLocalZ)));
        int gx = Math.floorDiv(cellLocalX, 16) - originSlotX;
        int gz = Math.floorDiv(cellLocalZ, 16) - originSlotZ;
        return roomAtSlot(gx, gz);
    }

    /** Room at cell-local x/z and absolute y: a world floor's places can stack (a gallery over an aisle). */
    public Room roomAt(int cellLocalX, int y, int cellLocalZ) {
        if (places != null) return room(places.at(toFloorX(cellLocalX), y - baseY, toFloorZ(cellLocalZ)));
        return roomAtLocal(cellLocalX, cellLocalZ);
    }

    /** Whether a cell slot column belongs to this floor's grid (plus a one-slot margin of solid rock). */
    public boolean coversSlot(int cellSlotX, int cellSlotZ, int margin) {
        int gx = cellSlotX - originSlotX, gz = cellSlotZ - originSlotZ;
        return gx >= -margin && gz >= -margin && gx < w + margin && gz < h + margin;
    }

    /** Floor-local X of a cell-local X. */
    public int toFloorX(int cellLocalX) {
        return cellLocalX - originSlotX * 16;
    }

    public int toFloorZ(int cellLocalZ) {
        return cellLocalZ - originSlotZ * 16;
    }

    /** Shortest room path from start to end (for ghosts and followers). */
    public List<Integer> criticalPath() {
        return path(start, end);
    }

    public List<Integer> path(int from, int to) {
        int n = rooms.size();
        int[] prev = new int[n];
        Arrays.fill(prev, -2);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(from);
        prev[from] = -1;
        while (!q.isEmpty()) {
            int cur = q.poll();
            if (cur == to) break;
            for (Room.Door d : rooms.get(cur).doors) {
                if (prev[d.toRoom] == -2) {
                    prev[d.toRoom] = cur;
                    q.add(d.toRoom);
                }
            }
        }
        List<Integer> out = new ArrayList<>();
        if (prev[to] == -2) return out;
        for (int c = to; c != -1; c = prev[c]) out.add(c);
        Collections.reverse(out);
        return out;
    }
}
