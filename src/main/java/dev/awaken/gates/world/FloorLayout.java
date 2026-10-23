package dev.awaken.gates.world;

import dev.awaken.gates.content.Theme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One floor: a grid of slots, the rooms on it, and where it starts and ends. Immutable once built. */
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

    FloorLayout(int area, int floor, int baseY, int originSlotX, int originSlotZ, int w, int h, Theme theme,
                boolean bossFloor, List<Room> rooms, int start, int end) {
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

    /** Room at cell-local block coordinates (x, z inside the cell), or null. */
    public Room roomAtLocal(int cellLocalX, int cellLocalZ) {
        int gx = Math.floorDiv(cellLocalX, 16) - originSlotX;
        int gz = Math.floorDiv(cellLocalZ, 16) - originSlotZ;
        return roomAtSlot(gx, gz);
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
