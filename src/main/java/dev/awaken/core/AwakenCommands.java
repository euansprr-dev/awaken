package dev.awaken.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The /awaken command root. Modules add subcommands at init with {@link #add}. Player-facing subcommands use
 * {@link Commands#LEVEL_ALL}; debug and admin ones require game master permission.
 */
public final class AwakenCommands {
    private AwakenCommands() {}

    private static final List<BiConsumer<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext>> CONTRIBUTORS = new ArrayList<>();

    public static void add(BiConsumer<LiteralArgumentBuilder<CommandSourceStack>, CommandBuildContext> contributor) {
        CONTRIBUTORS.add(contributor);
    }

    static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("awaken");
        root.then(Commands.literal("playday").executes(c -> {
            long day = PlayDays.current(c.getSource().getServer());
            c.getSource().sendSuccess(() -> Component.literal("Play-day " + day), false);
            return (int) day;
        }));
        for (var c : CONTRIBUTORS) c.accept(root, event.getBuildContext());
        event.getDispatcher().register(root);
    }

    /** Requirement for debug/admin subcommands. */
    public static java.util.function.Predicate<CommandSourceStack> admin() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }
}
