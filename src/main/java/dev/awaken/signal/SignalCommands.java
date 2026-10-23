package dev.awaken.signal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.Later;
import dev.awaken.core.api.NowHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /awaken signal ...}: see what's waiting, and (game masters) show one of each System v2 surface so the look can
 * be checked by eye or by the screenshot autopilot.
 */
final class SignalCommands {
    private SignalCommands() {}

    static final String DEMO_ACTION = "signal:demo";

    private record Demo(long until, BlockPos where) {}

    private static final Map<UUID, Demo> DEMO_NOW = new HashMap<>();

    static void register() {
        Signal.onAction("signal", (p, action, accepted) -> {
            if (action.startsWith(DEMO_ACTION)) {
                Signal.toast(p, Component.literal(accepted ? "You chose: leave with your loot" : "You chose: keep going down"));
            }
        });
        NowHooks.register(SignalCommands::demoSuggestions);
        AwakenCommands.add((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> signal = Commands.literal("signal");
            signal.then(Commands.literal("pending").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                List<SignalWindow> list = Signal.pending(p);
                if (list.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Nothing is waiting for you."), false);
                for (SignalWindow w : list) {
                    c.getSource().sendSuccess(() -> Component.literal("· ").append(w.title().copy())
                            .append(Component.literal("  (" + w.action().orElse("") + ")").withStyle(ChatFormatting.GRAY)), false);
                }
                return list.size();
            }));
            LiteralArgumentBuilder<CommandSourceStack> demo = Commands.literal("demo").requires(AwakenCommands.admin());
            for (String kind : List.of("toast", "burst", "choice", "legacy", "story", "panel", "now", "all")) {
                demo.then(Commands.literal(kind).executes(c -> {
                    demo(c.getSource().getPlayerOrException(), kind);
                    return 1;
                }));
            }
            signal.then(demo);
            signal.then(Commands.literal("withdraw").requires(AwakenCommands.admin())
                    .then(Commands.argument("action", StringArgumentType.greedyString()).executes(c -> {
                        boolean was = Signal.withdraw(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "action"));
                        c.getSource().sendSuccess(() -> Component.literal(was ? "Withdrawn." : "Not pending."), false);
                        return was ? 1 : 0;
                    })));
            root.then(signal);
        });
    }

    static void demo(ServerPlayer p, String kind) {
        switch (kind) {
            case "toast" -> Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("New: Mossback"))
                    .body(Component.literal("+XP for the sighting. It grazes and fights back if you hit it."))
                    .presentation(SignalWindow.Presentation.TOAST).build());
            case "burst" -> {
                Signal.toast(p, Component.literal("Level 3"));
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("+3 stat points")).body(Component.literal("Spend them in Status.")).build());
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("New: Lumen moth")).build());
                Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Recipe: Hunter's lantern")).build());
            }
            case "choice" -> Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Leave now?"))
                    .body(Component.literal("Floor 1 cleared. +480 XP in your Satchel."))
                    .row("Satchel", "6 / 27 slots")
                    .choice(DEMO_ACTION, "Leave with your loot", "Keep going down")
                    .ttl(20 * 120).build());
            case "legacy" -> Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("Rest Shrine"))
                    .row("Satchel", "6 slots · 14 items").row("Satchel XP", "480").row("Floor", "1 of 3")
                    .body(Component.literal("Carry it out now, or break the Seal and go deeper for more."))
                    .footer(Component.literal("[Y] Extract · [N] Stay"))
                    .action(DEMO_ACTION + "_legacy").build());
            case "story" -> Signal.send(p, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal("The gates were always there."))
                    .body(Component.literal("You just couldn't see them. Now the world has noticed you back: the motes in the grass, "
                            + "the lines over the ore, the thing watching from the trees."))
                    .build());
            case "panel" -> Signal.send(p, SignalWindow.builder(SignalWindow.Kind.GATE,
                            Component.literal("E-rank gate").withColor(0x9FB7CC))
                    .row("Breaks in", "never · the gate will wait")
                    .row("Omen", "a bell that rings under the ground")
                    .row("Layout", "Crypt halls")
                    .row("Modifiers", "None")
                    .row("Floors", "3")
                    .footer(Component.literal("Walk in to enter · scout it from the Veil to learn more"))
                    .ttl(20 * 9).priority(3).build());
            case "now" -> {
                BlockPos at = p.blockPosition().north(12);
                DEMO_NOW.put(p.getUUID(), new Demo(p.level().getGameTime() + 20 * 60 * 5, at));
                NowSync.update(p);
            }
            case "all" -> {
                var server = p.level().getServer();
                demo(p, "now");
                demo(p, "toast");
                Later.run(server, 40, () -> demo(p, "choice"));
                Later.run(server, 60, () -> demo(p, "story"));
                Later.run(server, 80, () -> demo(p, "panel"));
            }
            default -> {}
        }
    }

    private static List<NowHooks.Suggestion> demoSuggestions(ServerPlayer p) {
        Demo d = DEMO_NOW.get(p.getUUID());
        if (d == null) return List.of();
        if (p.level().getGameTime() > d.until()) {
            DEMO_NOW.remove(p.getUUID());
            return List.of();
        }
        return List.of(
                NowHooks.Suggestion.at("signal:demo/shrine", 70, "Rest Shrine", d.where()),
                NowHooks.Suggestion.of("signal:demo/points", 55, "Spend 3 points · K"),
                NowHooks.Suggestion.of("signal:demo/crypt", 40, "Bell Crypt · floor 2"));
    }

    static void forget(UUID player) {
        DEMO_NOW.remove(player);
    }

    static void clear() {
        DEMO_NOW.clear();
    }
}
