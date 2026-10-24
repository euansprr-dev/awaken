package dev.awaken.core;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
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
        // dev: /summon for any entity type, even the ones it refuses (noSummon: ghosts, followers), for tours
        root.then(Commands.literal("admin").requires(admin()).then(Commands.literal("spawn")
                .then(Commands.argument("entity", ResourceArgument.resource(event.getBuildContext(), Registries.ENTITY_TYPE))
                        .then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> spawn(c, new CompoundTag(), true))
                                .then(Commands.argument("nbt", CompoundTagArgument.compoundTag())
                                        .executes(c -> spawn(c, CompoundTagArgument.getCompoundTag(c, "nbt"), false)))))));
        for (var c : CONTRIBUTORS) c.accept(root, event.getBuildContext());
        var node = event.getDispatcher().register(root);
        // "/awaken as <player> <any awaken command>": every subcommand that acts on its caller acts on that player, so
        // the server console (and command blocks) can run the admin tools for someone (SPEC "Together", dedicated server)
        event.getDispatcher().register(Commands.literal("awaken").then(Commands.literal("as").requires(admin())
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).redirect(node, c -> {
                    var p = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player");
                    return c.getSource().withEntity(p).withPosition(p.position()).withRotation(p.getRotationVector()).withLevel(p.level());
                }))));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, CompoundTag nbt, boolean finalize) throws CommandSyntaxException {
        Entity e = SummonCommand.createEntity(c.getSource(), ResourceArgument.getResource(c, "entity", Registries.ENTITY_TYPE),
                Vec3Argument.getVec3(c, "pos"), nbt, finalize);
        c.getSource().sendSuccess(() -> Component.literal("Spawned " + e.getDisplayName().getString()), false);
        return 1;
    }

    /** Requirement for debug/admin subcommands. */
    public static java.util.function.Predicate<CommandSourceStack> admin() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }
}
