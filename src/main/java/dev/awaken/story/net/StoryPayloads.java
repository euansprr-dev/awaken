package dev.awaken.story.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Story network messages. */
public final class StoryPayloads {
    private StoryPayloads() {}

    /** Server → client: open the ending choice. */
    /** {@code wake}: whether Wake up is open to this player (it needs their Hundred Peal, SPEC §43.14). */
    public record OpenEnding(boolean wake) implements CustomPacketPayload {
        public static final Type<OpenEnding> TYPE = new Type<>(Awaken.id("story_open_ending"));
        public static final StreamCodec<ByteBuf, OpenEnding> STREAM_CODEC = ByteBufCodecs.BOOL.map(OpenEnding::new, OpenEnding::wake);
        @Override public Type<OpenEnding> type() { return TYPE; }
    }

    /** Client → server: the player chose an ending (become | break | wake), or "later" (not yet: the Seed waits). */
    public record ChooseEnding(String which) implements CustomPacketPayload {
        public static final Type<ChooseEnding> TYPE = new Type<>(Awaken.id("story_choose_ending"));
        public static final StreamCodec<ByteBuf, ChooseEnding> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.map(ChooseEnding::new, ChooseEnding::which);
        @Override public Type<ChooseEnding> type() { return TYPE; }
    }

    /** Server → client: roll the credits. */
    public record Credits(String endingTitle) implements CustomPacketPayload {
        public static final Type<Credits> TYPE = new Type<>(Awaken.id("story_credits"));
        public static final StreamCodec<ByteBuf, Credits> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.map(Credits::new, Credits::endingTitle);
        @Override public Type<Credits> type() { return TYPE; }
    }

    /** Server → client: revelation texts the player has seen, for the Codex. */
    public record Seen(java.util.List<String> entries) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(Awaken.id("story_seen"));
        public static final StreamCodec<ByteBuf, Seen> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(Seen::new, Seen::entries);
        @Override public Type<Seen> type() { return TYPE; }
    }

    /**
     * Server → client: where this player is in the Ritual of the Bell (story/Ritual), so the client runs the scene's
     * look, camera and words from it. Sent on every beat and again on join. {@code beat} is a Ritual.Beat ordinal, or
     * {@link #NONE}; {@code age} is ticks into the beat; positions are the hall's (zero outside it). VOID and TEAR are
     * the darkness of the question and the moment it tears open (any prologue path, the old Bell Crypt too).
     */
    public record RitualScene(int beat, int age, int seated, Vec3 throne, Vec3 altar, Vec3 words, Vec3 door, java.util.List<Vec3> plates,
            String text) implements CustomPacketPayload {
        public static final int NONE = -1, VOID = 100, TEAR = 101;
        public static final Type<RitualScene> TYPE = new Type<>(Awaken.id("story_ritual_scene"));
        public static final StreamCodec<ByteBuf, RitualScene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RitualScene::beat,
                ByteBufCodecs.VAR_INT, RitualScene::age,
                ByteBufCodecs.VAR_INT, RitualScene::seated,
                Vec3.STREAM_CODEC, RitualScene::throne,
                Vec3.STREAM_CODEC, RitualScene::altar,
                Vec3.STREAM_CODEC, RitualScene::words,
                Vec3.STREAM_CODEC, RitualScene::door,
                Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(3)), RitualScene::plates,
                ByteBufCodecs.stringUtf8(512), RitualScene::text,
                RitualScene::new);
        public static RitualScene of(int beat) {
            return of(beat, "");
        }
        /** A scene with words: the void carries what the System says in the black (the question's card holds the keys). */
        public static RitualScene of(int beat, String text) {
            return new RitualScene(beat, 0, -1, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, java.util.List.of(), text);
        }
        @Override public Type<RitualScene> type() { return TYPE; }
    }

    /**
     * Server → client: one moment of the ritual the client draws and sounds (a telegraph, a strike, a toll). Kinds are
     * Ritual.Cue ordinals; {@code id} an entity id or an index, {@code a}/{@code b} points, {@code f} a strength or a
     * radius, {@code ticks} how long it lasts.
     */
    public record RitualCue(int kind, int id, Vec3 a, Vec3 b, float f, int ticks) implements CustomPacketPayload {
        public static final Type<RitualCue> TYPE = new Type<>(Awaken.id("story_ritual_cue"));
        public static final StreamCodec<ByteBuf, RitualCue> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RitualCue::kind,
                ByteBufCodecs.VAR_INT, RitualCue::id,
                Vec3.STREAM_CODEC, RitualCue::a,
                Vec3.STREAM_CODEC, RitualCue::b,
                ByteBufCodecs.FLOAT, RitualCue::f,
                ByteBufCodecs.VAR_INT, RitualCue::ticks,
                RitualCue::new);
        @Override public Type<RitualCue> type() { return TYPE; }
    }
}
