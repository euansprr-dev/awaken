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
            if (mark != null) lines.add(mark.name() + ": " + Marks.say(mark, instance.attunement()));
        }
        return lines;
    }
    /** "3 seconds", "0.15 seconds", "1 second". */
    static String seconds(double s) {
        return number(s) + (s == 1 ? " second" : " seconds");
    }

    /** "4", "4.5", "0.15": no trailing zeros, no float noise. */
    static String number(double n) {
        return n == Math.rint(n) ? Long.toString((long) n) : Float.toString((float) n);
    }

    static void reward(ServerPlayer p, String id, String category, ItemStack stack, String summary, List<String> more) {
        List<String> lines = new ArrayList<>(more);
        List<String> marks = marks(stack);
        lines.addAll(marks);
        if (!marks.isEmpty()) lines.add("Its Marks only work on this weapon, while you're holding it.");
        NoticeEvents.tell(p, new NoticeEvents.Event(id, category, stack.getHoverName().getString(), summary, lines, NoticeEvents.Kind.REWARD, 0xE2B450));
    }
}
