package dev.awaken.story.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Story network messages. */
public final class StoryPayloads {
    private StoryPayloads() {}

    /** Server → client: open the ending choice. */
    public record OpenEnding() implements CustomPacketPayload {
        public static final Type<OpenEnding> TYPE = new Type<>(Awaken.id("story_open_ending"));
        public static final StreamCodec<ByteBuf, OpenEnding> STREAM_CODEC = StreamCodec.unit(new OpenEnding());
        @Override public Type<OpenEnding> type() { return TYPE; }
    }

    /** Client → server: the player chose an ending (become | break | wake). */
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
}
