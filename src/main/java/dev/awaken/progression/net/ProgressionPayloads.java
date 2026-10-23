package dev.awaken.progression.net;

import dev.awaken.Awaken;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;

/** Network messages for progression. */
public final class ProgressionPayloads {
    private ProgressionPayloads() {}

    /** Server → client: all progression content (names and text for the Status screen). */
    public record ContentSync(CompoundTag data) implements CustomPacketPayload {
        public static final Type<ContentSync> TYPE = new Type<>(Awaken.id("progression_content"));
        public static final StreamCodec<ByteBuf, ContentSync> STREAM_CODEC =
                ByteBufCodecs.compoundTagCodec(net.minecraft.nbt.NbtAccounter::unlimitedHeap).map(ContentSync::new, ContentSync::data);
        @Override public Type<ContentSync> type() { return TYPE; }

        public static ContentSync build() {
            CompoundTag tag = new CompoundTag();
            for (Content.Store<?> s : Content.ALL) tag.put(s.folder, encode(s));
            return new ContentSync(tag);
        }

        private static <T> Tag encode(Content.Store<T> s) {
            return s.mapCodec().encodeStart(NbtOps.INSTANCE, Map.copyOf(s.map)).getOrThrow();
        }

        public void apply() {
            for (Content.Store<?> s : Content.ALL) decode(s, data.get(s.folder));
        }

        private static <T> void decode(Content.Store<T> s, Tag t) {
            if (t == null) return;
            s.mapCodec().parse(NbtOps.INSTANCE, t).ifSuccess(s::replace);
        }
    }

    /** Client → server: one Status screen action. kind = spend|declare|undeclare|title|path, arg = stat or id. */
    public record StatusAction(String kind, String arg) implements CustomPacketPayload {
        public static final Type<StatusAction> TYPE = new Type<>(Awaken.id("status_action"));
        public static final StreamCodec<ByteBuf, StatusAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StatusAction::kind,
                ByteBufCodecs.STRING_UTF8, StatusAction::arg,
                StatusAction::new);
        @Override public Type<StatusAction> type() { return TYPE; }
    }

    static {
        // keep Defs referenced so content codecs initialise before first use
        Defs.TruthDef.class.getName();
        Identifier.class.getName();
    }
}
