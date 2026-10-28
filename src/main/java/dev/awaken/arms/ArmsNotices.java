package dev.awaken.arms;

import dev.awaken.core.api.NoticeEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

final class ArmsNotices {
    private ArmsNotices() {}
    static List<String> marks(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        var instance = Marks.of(stack);
        for (String id : instance.marks()) {
            var mark = Marks.get(id);
            if (mark != null) lines.add(mark.name() + ": " + Marks.describe(mark, instance.attunement()));
        }
        return lines;
    }
    static void reward(ServerPlayer p, String id, String category, ItemStack stack, String summary, List<String> more) {
        List<String> lines = new ArrayList<>(more);
        lines.addAll(marks(stack));
        lines.add("These effects belong to this weapon. Wield it to use them; receiving it does not change every weapon you own.");
        NoticeEvents.tell(p, new NoticeEvents.Event(id, category, stack.getHoverName().getString(), summary, lines, NoticeEvents.Kind.REWARD, 0xE2B450));
    }
}
