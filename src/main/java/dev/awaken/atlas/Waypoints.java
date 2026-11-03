package dev.awaken.atlas;

import dev.awaken.core.AwakenNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Waypoints on the server (SPEC §16.8; Euan 2026-11-02): places a player marks on the Atlas, named, coloured and with a
 * mark, kept in their {@link AtlasData} (so per world, each with its dimension, kept through death). Each one is the
 * owner's alone unless they share it, and then their party members online in that dimension see it too. Only ground
 * you've explored takes one, {@link AtlasData#WAYPOINTS_MAX} at most, and nothing is ever removed for you. They show on
 * the table only; out in the world only the destination's compass points to one. Where you last fell (outside gates) is
 * one more, grey, until you get back to it or fall again.
 */
public final class Waypoints {
    private Waypoints() {}

    /** Where you fell clears once you're this close (blocks across, and up or down). */
    static final int FELL_REACH = 8;
    /** Ticks between checks that the party's view hasn't changed (someone joined or left the party). */
    static final int PERIOD = 100;
    /** Where you fell's grey. */
    public static final int FELL_COLOUR = 0xA7B2C2;

    /** What a request came to. */
    public enum Result { OK, FULL, UNKNOWN_GROUND, NOT_MAPPED, NOT_YOURS, BAD }

    /** A request from the table: added, changed or removed, then the party hears; the reason when it can't be done. */
    static void request(ServerPlayer p, AtlasNet.WaypointEdit m) {
        Result r = switch (m.op()) {
            case AtlasNet.WaypointEdit.ADD -> add(p, m.x(), m.y(), m.z(), m.name(), m.colour(), m.mark(), m.shared());
            case AtlasNet.WaypointEdit.EDIT -> edit(p, m.id(), m.name(), m.colour(), m.mark(), m.shared());
            case AtlasNet.WaypointEdit.REMOVE -> remove(p, m.id());
            default -> Result.BAD;
        };
        switch (r) {
            case FULL -> p.sendSystemMessage(Component.translatable("atlas.awaken.waypoints.full", AtlasData.WAYPOINTS_MAX), true);
            case UNKNOWN_GROUND -> p.sendSystemMessage(Component.translatable("atlas.awaken.waypoints.unknown"), true);
            default -> {}
        }
    }

    /** A new waypoint at (x, y, z) in the player's dimension, on ground they know. */
    public static Result add(ServerPlayer p, int x, int y, int z, String name, int colour, int mark, boolean shared) {
        String dim = Discovery.dim(p.level());
        if (!Discovery.mapped(dim)) return Result.NOT_MAPPED;
        if (!Discovery.explored(p, new BlockPos(x, y, z))) return Result.UNKNOWN_GROUND;
        AtlasData d = Discovery.data(p);
        if (d.waypoints.size() >= AtlasData.WAYPOINTS_MAX) return Result.FULL;
        if (!colourOk(colour) || mark < 0 || mark >= AtlasData.MARKS) return Result.BAD;
        int cy = Math.clamp(y, p.level().getMinY(), p.level().getMaxY());
        d.waypoints.add(new AtlasData.Waypoint(d.nextId++, dim, x, cy, z, clean(name), colour, mark, shared));
        changed(p);
        return Result.OK;
    }

    /** A waypoint of your own renamed, recoloured, re-marked, shared or not. */
    public static Result edit(ServerPlayer p, int id, String name, int colour, int mark, boolean shared) {
        if (!colourOk(colour) || mark < 0 || mark >= AtlasData.MARKS) return Result.BAD;
        List<AtlasData.Waypoint> list = Discovery.data(p).waypoints;
        for (int i = 0; i < list.size(); i++) {
            AtlasData.Waypoint w = list.get(i);
            if (w.id() != id) continue;
            list.set(i, new AtlasData.Waypoint(id, w.dim(), w.x(), w.y(), w.z(), clean(name), colour, mark, shared));
            changed(p);
            return Result.OK;
        }
        return Result.NOT_YOURS;
    }

    /** A waypoint of your own taken off the map (id 0: where you fell). */
    public static Result remove(ServerPlayer p, int id) {
        AtlasData d = Discovery.data(p);
        if (id == 0 && d.fell != null) {
            d.fell = null;
            send(p);
            return Result.OK;
        }
        if (!d.waypoints.removeIf(w -> w.id() == id)) return Result.NOT_YOURS;
        changed(p);
        return Result.OK;
    }

    static boolean colourOk(int colour) {
        return colour == AtlasData.OWN_COLOUR || colour >= 0 && colour < AtlasData.COLOURS.length;
    }

    /** A name as the server keeps it: chat's characters only, spaces folded, at most {@link AtlasData#NAME_MAX}; never empty. */
    static String clean(String raw) {
        String s = StringUtil.filterText(raw == null ? "" : raw).strip().replaceAll("\\s+", " ");
        if (s.codePointCount(0, s.length()) > AtlasData.NAME_MAX) s = s.substring(0, s.offsetByCodePoints(0, AtlasData.NAME_MAX)).strip();
        return s.isEmpty() ? "Waypoint" : s;
    }

    // ---- where you fell -----------------------------------------------------------------------------------------------

    /** A death outside gates leaves where you fell on your map, in place of the last one. */
    static void died(ServerPlayer p) {
        String dim = Discovery.dim(p.level());
        if (!Discovery.mapped(dim)) return;
        Discovery.data(p).fell = new AtlasData.Fell(dim, p.getBlockX(), p.getBlockY(), p.getBlockZ());
    }

    /** Once you're back where you fell, it goes (checked with the rest of the Atlas's tick). */
    static void tick(ServerPlayer p, Discovery.Live l, int phase) {
        AtlasData.Fell f = Discovery.data(p).fell;
        if (f != null && f.dim().equals(l.dim) && Math.abs(p.getBlockX() - f.x()) <= FELL_REACH && Math.abs(p.getBlockZ() - f.z()) <= FELL_REACH
                && Math.abs(p.getBlockY() - f.y()) <= FELL_REACH * 3) {
            Discovery.data(p).fell = null;
            send(p);
            return;
        }
        if (phase % PERIOD == 0) send(p, l, false);
    }

    // ---- what each player sees ------------------------------------------------------------------------------------------

    /** The viewer's waypoints in their dimension, their party's shared ones there, and where they fell if it's there. */
    static List<AtlasNet.WaypointView> of(ServerPlayer viewer) {
        String dim = Discovery.dim(viewer.level());
        List<AtlasNet.WaypointView> out = new ArrayList<>();
        for (ServerPlayer m : Discovery.party(viewer)) {
            boolean me = m == viewer;
            int own = Discovery.colour(m.getUUID());
            String owner = m.getName().getString();
            for (AtlasData.Waypoint w : Discovery.data(m).waypoints) {
                if (!w.dim().equals(dim) || !me && !w.shared()) continue;
                int flags = (w.shared() ? AtlasNet.WaypointView.SHARED : 0) | (me ? AtlasNet.WaypointView.MINE : 0);
                out.add(new AtlasNet.WaypointView(w.id(), owner, own, w.name(), w.rgb(own), w.mark(), w.x(), w.y(), w.z(), flags));
            }
        }
        AtlasData.Fell f = Discovery.data(viewer).fell;
        if (f != null && f.dim().equals(dim))
            out.add(new AtlasNet.WaypointView(0, viewer.getName().getString(), FELL_COLOUR, "Where you fell", FELL_COLOUR, 0, f.x(), f.y(), f.z(),
                    AtlasNet.WaypointView.MINE | AtlasNet.WaypointView.FELL));
        return out;
    }

    /** Sends the viewer's waypoints now. */
    static void send(ServerPlayer viewer) {
        send(viewer, Discovery.live(viewer), true);
    }

    /** Sends them when they differ from what the client last heard ({@code force}: a join, a new dimension, a change). */
    static void send(ServerPlayer viewer, Discovery.Live l, boolean force) {
        List<AtlasNet.WaypointView> list = of(viewer);
        int hash = list.hashCode();
        if (!force && hash == l.waypoints) return;
        l.waypoints = hash;
        AwakenNet.send(viewer, new AtlasNet.WaypointList(list));
    }

    /** This player's waypoints changed: they and their party online hear at once. */
    static void changed(ServerPlayer p) {
        for (ServerPlayer m : Discovery.party(p)) send(m);
    }
}
