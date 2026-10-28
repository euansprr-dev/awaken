package dev.awaken.signal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.NoticeEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Saved recent event explanations. Never stores live damage ticks, guesses meaning from a toast, or mutates rewards. */
public final class NoticeHistory {
    private NoticeHistory() {}
    public static final int SCHEMA = 1, MAX = 120;
    public record Entry(long sequence, long at, NoticeEvents.Event event) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("sequence").forGetter(Entry::sequence), Codec.LONG.fieldOf("at").forGetter(Entry::at),
                NoticeEvents.Event.CODEC.fieldOf("event").forGetter(Entry::event)
        ).apply(i, Entry::new));
    }
    public record Data(int schemaVersion, long sequence, long read, List<Entry> entries) {
        public static final Data EMPTY = new Data(SCHEMA, 0, 0, List.of());
        public Data {
            schemaVersion = SCHEMA;
            sequence = Math.max(0, sequence);
            read = Math.max(0, Math.min(sequence, read));
            entries = List.copyOf(entries.subList(0, Math.min(MAX, entries.size())));
        }
        public static final MapCodec<Data> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion").xmap(v -> v.orElse(SCHEMA), java.util.Optional::of).forGetter(Data::schemaVersion),
                Codec.LONG.optionalFieldOf("sequence", 0L).forGetter(Data::sequence),
                Codec.LONG.optionalFieldOf("read", 0L).forGetter(Data::read),
                Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Data::entries)
        ).apply(i, Data::new));

        public Data add(NoticeEvents.Event event, long now) {
            for (Entry e : entries) if (e.event().equals(event)) return this;
            List<Entry> next = new ArrayList<>(Math.min(MAX, entries.size() + 1));
            next.add(new Entry(sequence + 1, now, event));
            for (Entry e : entries) if (!e.event().id().equals(event.id()) && next.size() < MAX) next.add(e);
            return new Data(SCHEMA, sequence + 1, read, List.copyOf(next));
        }
        public Data readThrough(long seen) {
            return new Data(SCHEMA, sequence, Math.max(read, Math.min(sequence, seen)), entries);
        }
    }
    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<Data>> HISTORY = ATTACHMENTS.register("notice_history", () -> AttachmentType
            .builder(() -> Data.EMPTY).serialize(Data.CODEC, d -> !d.entries().isEmpty()).copyOnDeath().build());

    private static long now(ServerPlayer p) { return p.level().getServer().overworld().getGameTime(); }
    static void tell(ServerPlayer p, NoticeEvents.Event event, boolean announce) {
        Data before = p.getData(HISTORY), after = before.add(event, now(p));
        if (before == after) return;
        p.setData(HISTORY, after);
        AwakenNet.send(p, new Update(false, announce, after.read(), now(p), List.of(after.entries().getFirst())));
    }
    static void sync(ServerPlayer p) {
        Data data = p.getData(HISTORY);
        AwakenNet.send(p, new Update(true, false, data.read(), now(p), data.entries()));
    }
    static void read(ServerPlayer p, long sequence) { p.setData(HISTORY, p.getData(HISTORY).readThrough(sequence)); }

    /** Login sends a bounded snapshot; a new event sends one entry. Only the server can add entries. */
    public record Update(boolean reset, boolean announce, long read, long now, List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(Awaken.id("notice_history"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> STREAM_CODEC = StreamCodec.ofMember((m, b) -> {
            b.writeBoolean(m.reset); b.writeBoolean(m.announce); b.writeVarLong(m.read); b.writeVarLong(m.now); b.writeVarInt(m.entries.size());
            for (Entry e : m.entries) {
                b.writeVarLong(e.sequence); b.writeVarLong(e.at);
                var v = e.event; b.writeUtf(v.id(), 160); b.writeUtf(v.category(), 48); b.writeUtf(v.name(), 160); b.writeUtf(v.summary(), 320);
                b.writeEnum(v.kind()); b.writeInt(v.colour()); b.writeVarInt(v.details().size());
                for (String detail : v.details()) b.writeUtf(detail, 1200);
            }
        }, b -> {
            boolean reset = b.readBoolean(), announce = b.readBoolean(); long read = b.readVarLong(), now = b.readVarLong(); int count = b.readVarInt();
            if (count < 0 || count > MAX) throw new IllegalArgumentException("Notice history length");
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long sequence = b.readVarLong(), at = b.readVarLong();
                String id = b.readUtf(160), category = b.readUtf(48), name = b.readUtf(160), summary = b.readUtf(320);
                var kind = b.readEnum(NoticeEvents.Kind.class); int colour = b.readInt(), details = b.readVarInt();
                if (details < 0 || details > 16) throw new IllegalArgumentException("Notice detail length");
                List<String> lines = new ArrayList<>(details);
                for (int j = 0; j < details; j++) lines.add(b.readUtf(1200));
                entries.add(new Entry(sequence, at, new NoticeEvents.Event(id, category, name, summary, lines, kind, colour)));
            }
            return new Update(reset, announce, read, now, List.copyOf(entries));
        });
        @Override public Type<Update> type() { return TYPE; }
    }
    public record Read(long sequence) implements CustomPacketPayload {
        public static final Type<Read> TYPE = new Type<>(Awaken.id("notice_history_read"));
        public static final StreamCodec<ByteBuf, Read> STREAM_CODEC = ByteBufCodecs.VAR_LONG.map(Read::new, Read::sequence);
        @Override public Type<Read> type() { return TYPE; }
    }
}
