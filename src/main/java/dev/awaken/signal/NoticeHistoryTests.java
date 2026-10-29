package dev.awaken.signal;

import dev.awaken.core.api.NoticeEvents;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.List;

final class NoticeHistoryTests {
    private NoticeHistoryTests() {}
    static NoticeEvents.Event event(String id) {
        return new NoticeEvents.Event(id, "Power upgraded", "Barrier", "The shield is stronger.",
                List.of("Shield capacity: 8 → 9.2 health.", "This is a permanent upgrade."), NoticeEvents.Kind.PERMANENT, 0x7FC3FF);
    }
    static void register() {
        AwakenTests.register("signal_notice_history_lifecycle", 20, h -> {
            var p = h.makeMockServerPlayerInLevel();
            var q = h.makeMockServerPlayerInLevel();
            var first = event("power/barrier/2");
            NoticeEvents.tell(p, first);
            NoticeEvents.tell(p, first);
            var original = p.getData(NoticeHistory.HISTORY);
            h.assertTrue(original.sequence() == 1 && original.entries().size() == 1, "repeat delivery never creates a second record or toast");
            h.assertTrue(q.getData(NoticeHistory.HISTORY).entries().isEmpty(), "history belongs to one player");
            NoticeHistory.read(p, Long.MAX_VALUE);
            h.assertTrue(p.getData(NoticeHistory.HISTORY).read() == 1, "client cannot acknowledge future events");
            NoticeEvents.remember(p, event("reward/2"));
            var current = p.getData(NoticeHistory.HISTORY);
            h.assertTrue(current.read() == 1 && current.entries().getFirst().sequence() == 2, "quiet scene reward is still a new readable record");
            CompoundTag saved = (CompoundTag) NoticeHistory.Data.CODEC.codec().encodeStart(NbtOps.INSTANCE, current).getOrThrow();
            var restored = NoticeHistory.Data.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow();
            q.setData(NoticeHistory.HISTORY, restored); // same saved value loaded by a rejoining/recreated player
            h.assertTrue(restored.equals(current) && saved.getInt("schemaVersion").orElse(0) == NoticeHistory.SCHEMA, "version, details and read cursor survive save/load");
            var update = new NoticeHistory.Update(true, false, restored.read(), 200, restored.entries());
            var bytes = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            NoticeHistory.Update.STREAM_CODEC.encode(bytes, update);
            var received = NoticeHistory.Update.STREAM_CODEC.decode(bytes);
            bytes.release();
            h.assertTrue(received.equals(update) && !received.announce() && received.entries().getFirst().event().details().size() == 2,
                    "login restores the exact Journal details without replaying old announcements");
            NoticeHistory.Data bounded = restored;
            for (int i = 0; i < NoticeHistory.MAX + 5; i++) bounded = bounded.add(event("reward/" + (i + 10)), 201 + i);
            h.assertTrue(bounded.entries().size() == NoticeHistory.MAX && bounded.entries().getFirst().at() > bounded.entries().getLast().at(), "history is bounded and newest first");
            var replaced = bounded.add(new NoticeEvents.Event(bounded.entries().getFirst().event().id(), "Variant changed", "Barrier", "Overshield", List.of("The variant changed."), NoticeEvents.Kind.PERMANENT, 0), 400);
            h.assertTrue(replaced.entries().size() == NoticeHistory.MAX && replaced.sequence() == bounded.sequence() + 1, "a changed same-id fact replaces its older record and becomes unread");
            h.assertTrue(q.getData(NoticeHistory.HISTORY).equals(restored), "immutable history values cannot affect another player");
            saved.remove("schemaVersion");
            saved.remove("read");
            h.assertTrue(NoticeHistory.Data.CODEC.codec().parse(NbtOps.INSTANCE, saved).getOrThrow().read() == 0, "optional legacy fields load safely");
            h.succeed();
        });
    }
}
