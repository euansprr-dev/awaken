package dev.awaken.tasks;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.Goals;
import dev.awaken.tasks.net.TaskPayloads;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /awaken tasks} lists your Tasks page as the server sees it (goal, key, the top step marked), {@code /awaken tasks
 * pin <key>} and {@code unpin} do what the page's Pin button does (for tours and tests).
 */
final class TasksCommands {
    private TasksCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("tasks")
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    TaskBook.Book book = TaskBook.fresh(p);
                    for (Goals.Step s : book.steps()) {
                        String mark = s.key().equals(book.top()) ? "▶ " : s.key().equals(book.pinned()) ? "◆ " : "  ";
                        String line = mark + s.goal().label + " · " + s.key() + " · " + s.title() + (s.optional() ? " (optional)" : "");
                        c.getSource().sendSuccess(() -> Component.literal(line), false);
                    }
                    return book.steps().size();
                })
                .then(Commands.literal("pin").then(Commands.argument("key", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    TasksModule.act(p, new TaskPayloads.Act(TaskPayloads.PIN, StringArgumentType.getString(c, "key")));
                    return 1;
                })))
                .then(Commands.literal("unpin").executes(c -> {
                    TasksModule.act(c.getSource().getPlayerOrException(), new TaskPayloads.Act(TaskPayloads.UNPIN, ""));
                    return 1;
                }))));
    }
}
