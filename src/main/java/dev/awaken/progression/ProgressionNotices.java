package dev.awaken.progression;

import dev.awaken.core.api.NoticeEvents;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

final class ProgressionNotices {
    private ProgressionNotices() {}
    static void tell(ServerPlayer p, String id, String category, String name, String summary, NoticeEvents.Kind kind, String... details) {
        NoticeEvents.tell(p, new NoticeEvents.Event(id, category, name, summary, List.of(details), kind, 0x86C9F0));
    }
    static void remember(ServerPlayer p, String id, String category, String name, String summary, String... details) {
        NoticeEvents.remember(p, new NoticeEvents.Event(id, category, name, summary, List.of(details), NoticeEvents.Kind.PERMANENT, 0x86C9F0));
    }
}
