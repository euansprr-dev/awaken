package dev.awaken.gates.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * The Gate Rig's payloads (SPEC §17 "Gate Rig"). The server swaps the blocks; these tell the client what to draw over
 * them: a {@link Scene} of the rig rooms on your floor (where the lenses shine, where the wells are, whether the room
 * surges) and {@link Cue}s for each moment (a row cracking, a tile falling, a mirror swinging round, glass shattering).
 */
public final class RigPayloads {
    private RigPayloads() {}

    /**
     * A rig room as the client needs it. Mirror Hall: the lens, its direction and the seal. Wells: their boxes (min, max
     * pairs). Gauntlet: its checkpoint rings, each as the two end cells of the row it stands over (floor level).
     */
    public record Room(int kind, int theme, BlockPos min, BlockPos max, BlockPos lens, int dir, BlockPos seal, List<BlockPos> wells,
                       boolean surge) {
        public static final StreamCodec<ByteBuf, Room> CODEC = StreamCodec.of((buf, r) -> {
            ByteBufCodecs.VAR_INT.encode(buf, r.kind);
            ByteBufCodecs.VAR_INT.encode(buf, r.theme);
            BlockPos.STREAM_CODEC.encode(buf, r.min);
            BlockPos.STREAM_CODEC.encode(buf, r.max);
            BlockPos.STREAM_CODEC.encode(buf, r.lens);
            ByteBufCodecs.VAR_INT.encode(buf, r.dir);
            BlockPos.STREAM_CODEC.encode(buf, r.seal);
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(16)).encode(buf, r.wells);
            ByteBufCodecs.BOOL.encode(buf, r.surge);
        }, buf -> new Room(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), BlockPos.STREAM_CODEC.decode(buf),
                BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                BlockPos.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(16)).decode(buf), ByteBufCodecs.BOOL.decode(buf)));
    }

    /** The rig rooms of the floor you are on (empty when you are on none). */
    public record Scene(List<Room> rooms) implements CustomPacketPayload {
        public static final Type<Scene> TYPE = new Type<>(Awaken.id("gate_rig_scene"));
        public static final StreamCodec<ByteBuf, Scene> STREAM_CODEC = Room.CODEC.apply(ByteBufCodecs.list(32)).map(Scene::new, Scene::rooms);
        public static final Scene NONE = new Scene(List.of());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One moment of a rig room. {@code cells}: packed block positions; {@code states}: the block state ids drawn there
     * (one per cell, or one for all); {@code ticks}: how long it plays; {@code a}: per kind (a cave-in's drop, a mirror's
     * old turn, a surge on or off).
     */
    public record Cue(int kind, int theme, List<Long> cells, List<Integer> states, int ticks, float a) implements CustomPacketPayload {
        /** PRESS: a press comes down out of the ceiling ({@code a}: how far); LIFT: it goes back up; HAZARD: its floor glows. */
        public static final int WARN = 0, FALL = 1, RISE = 2, SPIN = 3, SHATTER = 4, IGNITE = 5, DROP = 6, SURGE = 7, PIT = 8, CAVE = 9,
                PRESS = 10, LIFT = 11, HAZARD = 12;
        public static final Type<Cue> TYPE = new Type<>(Awaken.id("gate_rig_cue"));
        public static final StreamCodec<ByteBuf, Cue> STREAM_CODEC = StreamCodec.of((buf, c) -> {
            ByteBufCodecs.VAR_INT.encode(buf, c.kind);
            ByteBufCodecs.VAR_INT.encode(buf, c.theme);
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(512)).encode(buf, c.cells);
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512)).encode(buf, c.states);
            ByteBufCodecs.VAR_INT.encode(buf, c.ticks);
            ByteBufCodecs.FLOAT.encode(buf, c.a);
        }, buf -> new Cue(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(512)).decode(buf), ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(512)).decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.FLOAT.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Your ghost on a Gauntlet's course (only to you, as your run starts): your best run of it, a sample every
     * {@code step} ticks, positions in eighths of a block from {@code origin} ({@code xz}: x then z as two shorts;
     * {@code yw}: y as a short, then the yaw in 256ths of a turn), and its time.
     */
    public record Ghost(BlockPos origin, int step, int ticks, List<Integer> xz, List<Integer> yw) implements CustomPacketPayload {
        public static final int MAX = 1600;
        public static final Type<Ghost> TYPE = new Type<>(Awaken.id("gate_rig_ghost"));
        public static final StreamCodec<ByteBuf, Ghost> STREAM_CODEC = StreamCodec.of((buf, g) -> {
            BlockPos.STREAM_CODEC.encode(buf, g.origin);
            ByteBufCodecs.VAR_INT.encode(buf, g.step);
            ByteBufCodecs.VAR_INT.encode(buf, g.ticks);
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX)).encode(buf, g.xz);
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX)).encode(buf, g.yw);
        }, buf -> new Ghost(BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX)).decode(buf), ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX)).decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Your own run of a Gauntlet's course (only to the runner): the clock started, a ring passed ({@code ring} of
     * {@code rings}), the far landing reached ({@code ticks} on the clock, the {@code medal} won against {@code par}), or
     * the run abandoned. Medals: 0 none, 1 bronze, 2 silver, 3 gold. At the start {@code ticks} is your best time on this
     * course (0: none yet).
     */
    public record Run(int phase, int ticks, int ring, int rings, int medal, int par) implements CustomPacketPayload {
        public static final int START = 0, RING = 1, FINISH = 2, STOP = 3;
        public static final int BRONZE = 1, SILVER = 2, GOLD = 3;
        public static final Type<Run> TYPE = new Type<>(Awaken.id("gate_rig_run"));
        public static final StreamCodec<ByteBuf, Run> STREAM_CODEC = StreamCodec.of((buf, r) -> {
            ByteBufCodecs.VAR_INT.encode(buf, r.phase);
            ByteBufCodecs.VAR_INT.encode(buf, r.ticks);
            ByteBufCodecs.VAR_INT.encode(buf, r.ring);
            ByteBufCodecs.VAR_INT.encode(buf, r.rings);
            ByteBufCodecs.VAR_INT.encode(buf, r.medal);
            ByteBufCodecs.VAR_INT.encode(buf, r.par);
        }, buf -> new Run(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
