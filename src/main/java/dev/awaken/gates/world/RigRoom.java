package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Gate Rig's view of one room (SPEC §17 "Gate Rig"): which blocks can change, on what timeline, and what sets it
 * off. Built once per room by {@link FloorBuilder} (content version 4) from the room alone, so the painter, the server's
 * rig director and the client agree on every block. Immutable; coordinates are floor-local (x, rows above the room's
 * floor, z) like every room feature.
 *
 * <p>The vocabulary is the Ritual's (story.Ritual): a <b>group</b> is a set of blocks swapped together between their
 * painted (rest) state and an active state, a <b>track</b> is a timeline of beats on groups, a <b>trigger</b> starts a
 * track. A group with no cells is a flag the room's archetype reads (a gravity surge).
 */
public final class RigRoom {

    public enum Kind {
        COLLAPSE_RUN("Collapse Run"), MIRROR_HALL("Mirror Hall"), GRAVITY_WELLS("Gravity Wells");

        public final String displayName;

        Kind(String displayName) {
            this.displayName = displayName;
        }
    }

    /** How the client draws a group's change (the server swaps the blocks; the client draws the motion). */
    public enum Anim { NONE, FALL, RISE, SPIN, SHATTER, IGNITE }

    /**
     * A block that rises back into place, or a mirror swinging round, is drawn by the client for this long before the
     * server puts the real block there (the motion ends where the block appears).
     */
    public static final int RISE_TICKS = 6, SPIN_TICKS = 5;

    /** What a beat does to its group: show what is about to happen, change it, or put it back. */
    public enum Cue { WARN, APPLY, RESTORE }

    /** What starts a track: a player in a box, a player on a block, the light reaching a block, a monster fight. */
    public enum On { ZONE, PLATE, BEAM, ENCOUNTER }

    /** A track nobody is left to see: RESET puts back what it changed and stops; KEEP plays on (latched work). */
    public enum Empty { RESET, KEEP }

    public record Cell(int x, int dy, int z, BlockState rest, BlockState active) {}

    public record Group(int id, List<Cell> cells, Anim apply, Anim restore) {
        public boolean flag() {
            return cells.isEmpty();
        }
    }

    public record Beat(int at, Cue cue, int group) {}

    /** A timeline: beats at ticks into it, in order. A loop starts again after {@code length}; otherwise it ends. */
    public record Track(int id, int length, boolean loop, Empty empty, List<Beat> beats) {}

    /** Floor-local inclusive box (rows relative to the room's floor). */
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
        public boolean contains(double x, double dy, double z) {
            return x >= x0 && x < x1 + 1 && z >= z0 && z < z1 + 1 && dy >= y0 && dy < y1 + 1;
        }
    }

    /**
     * Starts {@code track} when it fires. {@code once}: it fires a single time per gate (latched, saved); otherwise it
     * fires again after the track ends and {@code cooldown} ticks pass. For BEAM the box is the receiver block.
     */
    public record Trigger(On on, Box box, int track, boolean once, int cooldown) {}

    public final Kind kind;
    public final List<Group> groups;
    public final List<Track> tracks;
    public final List<Trigger> triggers;
    /** The archetype's own geometry: exactly one is set, the one for {@link #kind}. */
    public final RigCollapse.Run run;
    public final RigMirrors.Puzzle mirrors;
    public final RigWells.Field wells;

    RigRoom(Kind kind, List<Group> groups, List<Track> tracks, List<Trigger> triggers, RigCollapse.Run run,
            RigMirrors.Puzzle mirrors, RigWells.Field wells) {
        this.kind = kind;
        this.groups = List.copyOf(groups);
        this.tracks = List.copyOf(tracks);
        this.triggers = List.copyOf(triggers);
        this.run = run;
        this.mirrors = mirrors;
        this.wells = wells;
    }

    public Group group(int id) {
        return groups.get(id);
    }

    public Track track(int id) {
        return tracks.get(id);
    }

    /** A stable fingerprint of everything the rig changes, for the determinism test. */
    public long signature() {
        long h = Hash.of(kind.ordinal(), groups.size(), tracks.size());
        for (Group g : groups) {
            h = Hash.of(h, g.id, g.apply.ordinal() * 8 + g.restore.ordinal());
            for (Cell c : g.cells) h = Hash.of(h, c.x * 64 + c.dy, c.z * 31 + c.rest.toString().hashCode() + c.active.toString().hashCode());
        }
        for (Track t : tracks) {
            h = Hash.of(h, t.id * 7 + (t.loop ? 1 : 0), t.length);
            for (Beat b : t.beats) h = Hash.of(h, b.at, b.cue.ordinal() * 1000 + b.group);
        }
        for (Trigger t : triggers) h = Hash.of(h, t.on.ordinal() * 100 + t.track, t.box.hashCode() + t.cooldown + (t.once ? 1 : 0));
        return h;
    }
}
