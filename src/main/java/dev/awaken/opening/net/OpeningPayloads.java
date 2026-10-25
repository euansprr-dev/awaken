package dev.awaken.opening.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** The opening's messages (SPEC §16.4): the server owns the timeline, the client plays it from the age it is sent. */
public final class OpeningPayloads {
    private OpeningPayloads() {}

    // beats, as the client sees them (the story's states in between are the Ritual's own scene)
    public static final int NONE = 0, SKY = 1, RUN = 2, ARRIVAL = 3, WAKE = 4, REGISTRY = 5, HANDOFF = 6;

    // scene flags
    /** The sky shot was seen before (on this server): hold jump to skip it. */
    public static final int F_SEEN = 1;
    /** ARRIVAL: the cinematic is playing ({@code n} = its age); without it the player waits in the square. */
    public static final int F_CINEMATIC = 2;
    /** RUN: the Bell Bridge is already down. */
    public static final int F_BRIDGE_DOWN = 4;
    /** The world asked for no first-run questions (tests, tours): land in the view you have. */
    public static final int F_NO_QUESTIONS = 8;
    /** A tour wants the sky shot's four frames and the hand-off saved as screenshots. */
    public static final int F_SHOTS = 16;

    /**
     * Server → client: where this player is in the opening. {@code age} is ticks into the beat (a relog resumes there).
     * {@code origin} is Carillon's centre (x, street y, z) so the client can place the plan's points; {@code eye} the
     * player's eyes where the beat begins (the Roost, the bed); {@code yaw} the view to land on; {@code at} the beat's
     * place (Choir Square's centre, the bed, the Assessment Lens); {@code n} the beat's number (the hunters still running,
     * the cinematic's age, the Registry's step); {@code text} the beat's words.
     */
    public record Scene(int beat, int age, Vec3 origin, Vec3 eye, float yaw, Vec3 at, int n, int flags, String text)
            implements CustomPacketPayload {
        public static final Type<Scene> TYPE = new Type<>(Awaken.id("opening_scene"));
        public static final StreamCodec<ByteBuf, Scene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Scene::beat,
                ByteBufCodecs.VAR_INT, Scene::age,
                Vec3.STREAM_CODEC, Scene::origin,
                Vec3.STREAM_CODEC, Scene::eye,
                ByteBufCodecs.FLOAT, Scene::yaw,
                Vec3.STREAM_CODEC, Scene::at,
                ByteBufCodecs.VAR_INT, Scene::n,
                ByteBufCodecs.VAR_INT, Scene::flags,
                ByteBufCodecs.stringUtf8(512), Scene::text,
                Scene::new);

        public static Scene none() {
            return new Scene(NONE, 0, Vec3.ZERO, Vec3.ZERO, 0F, Vec3.ZERO, 0, 0, "");
        }

        public boolean flag(int f) {
            return (flags & f) != 0;
        }

        @Override
        public Type<Scene> type() {
            return TYPE;
        }
    }

    // cue kinds
    /** The great bell rings out of time and the light falls into the square ({@code a} the square). */
    public static final int CUE_BELL_WRONG = 0;
    /** The gate pulls at the city: a tremor ({@code f} strength, {@code a} the square). */
    public static final int CUE_TREMOR = 1;
    /** The Bell Bridge goes into the river ({@code a} its middle). */
    public static final int CUE_BRIDGE = 2;
    /** The market empties toward you ({@code a} the square). */
    public static final int CUE_RUSH = 3;
    /** One toll of the great bell ({@code n} which). */
    public static final int CUE_TOLL = 4;
    /** Someone speaks ({@code text} "Name|line", {@code a} where they stand, {@code n} ticks on screen). */
    public static final int CUE_LINE = 5;
    /** The Registry moves on ({@code n} the step). */
    public static final int CUE_STEP = 6;
    /** A title card ({@code text} "TITLE|subtitle", {@code n} ticks). */
    public static final int CUE_TITLE = 7;
    /** The light reaches for a straggler: a flash, then you stand in the square. */
    public static final int CUE_PULLED = 8;

    /** Server → client: one moment of the opening to see and hear. */
    public record Cue(int kind, Vec3 a, float f, int n, String text) implements CustomPacketPayload {
        public static final Type<Cue> TYPE = new Type<>(Awaken.id("opening_cue"));
        public static final StreamCodec<ByteBuf, Cue> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cue::kind,
                Vec3.STREAM_CODEC, Cue::a,
                ByteBufCodecs.FLOAT, Cue::f,
                ByteBufCodecs.VAR_INT, Cue::n,
                ByteBufCodecs.stringUtf8(512), Cue::text,
                Cue::new);

        public static Cue of(int kind, Vec3 a) {
            return new Cue(kind, a, 0F, 0, "");
        }

        @Override
        public Type<Cue> type() {
            return TYPE;
        }
    }

    /**
     * Server → client (development: tours and golden runs): drive a route by its points with this client's own keys
     * ({@code rec}: record it as a golden run with the Ascent's route recorder). {@code origin} is Carillon's centre.
     */
    public record Drive(String route, boolean rec, Vec3 origin) implements CustomPacketPayload {
        public static final Type<Drive> TYPE = new Type<>(Awaken.id("opening_drive"));
        public static final StreamCodec<ByteBuf, Drive> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Drive::route,
                ByteBufCodecs.BOOL, Drive::rec,
                Vec3.STREAM_CODEC, Drive::origin,
                Drive::new);

        @Override
        public Type<Drive> type() {
            return TYPE;
        }
    }

    // what the client reports
    /** The sky shot landed and the first-run view question (if any) is answered. */
    public static final int READY_SKY = 0;
    /** The sky shot was skipped (held jump). */
    public static final int READY_SKY_SKIPPED = 1;
    /** The infirmary scene's camera handed the body back. */
    public static final int READY_WAKE = 2;
    /** A Registry screen (the Status, the first power) was closed: {@code n} the step. */
    public static final int READY_STEP = 3;

    /** Client → server: the client finished a part of the timeline only it can see end. */
    public record Ready(int what, int n) implements CustomPacketPayload {
        public static final Type<Ready> TYPE = new Type<>(Awaken.id("opening_ready"));
        public static final StreamCodec<ByteBuf, Ready> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Ready::what,
                ByteBufCodecs.VAR_INT, Ready::n,
                Ready::new);

        @Override
        public Type<Ready> type() {
            return TYPE;
        }
    }
}
