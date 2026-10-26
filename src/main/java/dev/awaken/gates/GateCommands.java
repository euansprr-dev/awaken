package dev.awaken.gates;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.PlayDays;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.run.Hud;
import dev.awaken.gates.run.Runs;
import dev.awaken.gates.world.Cells;
import dev.awaken.progression.Rank;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** {@code /awaken gate ...}: open, list, trace, enter, break, satchel, leave, scout, director. */
final class GateCommands {
    private GateCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var gate = Commands.literal("gate");
            gate.then(Commands.literal("list").executes(GateCommands::list));
            gate.then(Commands.literal("satchel").executes(c -> {
                Hud.sendSatchel(c.getSource().getPlayerOrException(), true);
                return 1;
            }));
            gate.then(Commands.literal("trace").then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(GateCommands::trace)));
            gate.then(Commands.literal("open").requires(AwakenCommands.admin())
                    .then(Commands.argument("rank", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Rank.values()).map(Rank::name), b))
                            .executes(c -> open(c, null, false))
                            .then(Commands.argument("theme", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Theme.values()).map(t -> t.id), b))
                                    .executes(c -> open(c, StringArgumentType.getString(c, "theme"), false))
                                    // tours: a gate that has a Gate Rig room
                                    .then(Commands.literal("rig").executes(c -> open(c, StringArgumentType.getString(c, "theme"), true))))));
            gate.then(Commands.literal("hop").requires(AwakenCommands.admin()).executes(GateCommands::hop));
            // tours: the nearest standing gate's card, wherever it formed (a card shows by itself within 24 blocks)
            gate.then(Commands.literal("card").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                GateRecord best = null;
                for (GateRecord g : GateRegistry.get(c.getSource().getServer()).near(p.blockPosition(), 96, false)) {
                    if (g.structurePlaced && (best == null || g.pos.distSqr(p.blockPosition()) < best.pos.distSqr(p.blockPosition()))) best = g;
                }
                if (best == null) {
                    c.getSource().sendFailure(Component.literal("No standing gate within 96 blocks."));
                    return 0;
                }
                GateCard.send(p, best);
                return 1;
            }));
            gate.then(Commands.literal("enter").requires(AwakenCommands.admin())
                    .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        GateRecord g = GateRegistry.get(c.getSource().getServer()).gate(IntegerArgumentType.getInteger(c, "id"));
                        if (g == null) {
                            c.getSource().sendFailure(Component.literal("No such gate."));
                            return 0;
                        }
                        if (g.state == GateState.BUILDING) {
                            c.getSource().sendFailure(Component.literal("Gate " + g.id + " is still building (" + GateService.buildsInProgress() + " in progress)."));
                            return 0;
                        }
                        return Runs.enter(p, g) ? 1 : 0;
                    })));
            gate.then(Commands.literal("break").requires(AwakenCommands.admin())
                    .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> {
                        GateRecord g = GateRegistry.get(c.getSource().getServer()).gate(IntegerArgumentType.getInteger(c, "id"));
                        if (g == null || !(g.state.standing())) {
                            c.getSource().sendFailure(Component.literal("That gate can't break (missing, broken, cleared or released)."));
                            return 0;
                        }
                        Breaks.breakGate(c.getSource().getServer(), g);
                        c.getSource().sendSuccess(() -> Component.literal("Gate " + g.id + " broke open."), true);
                        return 1;
                    })));
            gate.then(Commands.literal("leave").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                return Gates.forceExtract(p, true) ? 1 : 0;
            }));
            gate.then(Commands.literal("scout").requires(AwakenCommands.admin())
                    .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> {
                        Gates.markScouted(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "id"));
                        return 1;
                    })));
            gate.then(Commands.literal("director").requires(AwakenCommands.admin()).executes(c -> {
                GateRecord g = Director.upkeep(c.getSource().getServer(), c.getSource().getPlayerOrException(), true);
                c.getSource().sendSuccess(() -> Component.literal(g == null ? "The Director found no spot." : "Rolled gate " + g.id + " (see /awaken why)"), false);
                return g == null ? 0 : 1;
            }));
            // tours: your first gate, on purpose, now (level 3 if you're under it; SPEC §27.6)
            gate.then(Commands.literal("first").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                if (dev.awaken.progression.Progression.profile(p).level() < 3) {
                    dev.awaken.progression.Progression.topUpToLevel(p, 3, dev.awaken.progression.Progression.XpSource.COMMAND);
                }
                GateRecord g = Director.firstGate(c.getSource().getServer(), p);
                c.getSource().sendSuccess(() -> Component.literal(g == null ? "No first gate: not ready, in the city, or no dry ground." : "First gate " + g.id + " at " + g.pos.toShortString()), false);
                return g == null ? 0 : 1;
            }));
            root.then(gate);
        });
    }

    private static int open(CommandContext<CommandSourceStack> c, String themeId, boolean rig) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Rank rank;
        try {
            rank = Rank.valueOf(StringArgumentType.getString(c, "rank").toUpperCase());
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("Rank must be one of E D C B A S SOVEREIGN."));
            return 0;
        }
        Theme theme = themeId == null ? null : Theme.parse(themeId);
        if (themeId != null && theme == null) {
            c.getSource().sendFailure(Component.literal("Unknown theme " + themeId));
            return 0;
        }
        if (GateService.gateLevel(c.getSource().getServer()) == null) {
            c.getSource().sendFailure(Component.literal("The gate dimension isn't loaded in this world."));
            return 0;
        }
        GateRecord g = Director.spawnFor(c.getSource().getServer(), p, rank, theme, 18, 30, rig);
        if (g == null) {
            c.getSource().sendFailure(Component.literal("No dry ground nearby for a gate."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Gate " + g.id + " forming: " + g.plan.rank().label + " " + g.plan.theme().displayName
                + " at " + g.pos.getX() + " " + g.pos.getY() + " " + g.pos.getZ()), true);
        return g.id;
    }

    /**
     * Dev and tours: to dry land at least 160 blocks from every gate, searching rings outward from here, so a following
     * "gate open" has room. (Hopping a fixed distance with spreadplayers fails over the sea and leaves you by the last gate.)
     */
    private static int hop(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MinecraftServer server = c.getSource().getServer();
        ServerLevel over = server.overworld();
        GateRegistry reg = GateRegistry.get(server);
        for (int r = 200; r <= 3000; r += 100) {
            for (int k = 0; k < 24; k++) {
                double a = Math.PI * 2 * k / 24;
                int x = p.getBlockX() + (int) Math.round(Math.cos(a) * r), z = p.getBlockZ() + (int) Math.round(Math.sin(a) * r);
                BlockPos at = Director.surfaceEstimate(over, x, z);
                if (at == null || !reg.near(at, 160, false).isEmpty()) continue;
                boolean room = true;
                for (int d = 0; d < 8 && room; d++) {
                    double b = Math.PI * 2 * d / 8;
                    room = Director.surfaceEstimate(over, x + (int) Math.round(Math.cos(b) * 26), z + (int) Math.round(Math.sin(b) * 26)) != null;
                }
                if (!room) continue;
                over.getChunk(x >> 4, z >> 4);
                int y = over.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                p.teleportTo(over, x + 0.5, y, z + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
                int dist = r;
                c.getSource().sendSuccess(() -> Component.literal("Hopped " + dist + " blocks to dry land at " + x + " " + y + " " + z), false);
                return 1;
            }
        }
        c.getSource().sendFailure(Component.literal("No dry land clear of gates within 3,000 blocks."));
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        MinecraftServer s = c.getSource().getServer();
        ServerPlayer p = c.getSource().getPlayer();
        boolean admin = c.getSource().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        long today = PlayDays.current(s);
        List<GateRecord> gates = new ArrayList<>();
        for (GateRecord g : GateRegistry.get(s).all()) if (g.state != GateState.RELEASED) gates.add(g);
        if (gates.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No gates."), false);
            return 0;
        }
        for (GateRecord g : gates) {
            StringBuilder sb = new StringBuilder();
            sb.append('#').append(g.id).append(' ').append(g.plan.rank().label).append("-rank ");
            boolean known = admin || (p != null && Gates.isScouted(p, g.id));
            if (known) sb.append(g.plan.theme().displayName).append(' ');
            sb.append('[').append(g.state.name().toLowerCase()).append(']');
            if (p != null && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                sb.append(' ').append((int) Math.sqrt(p.blockPosition().distSqr(g.pos))).append(" m ").append(Breaks.direction(p.blockPosition(), g.pos));
            }
            if (g.state.standing() && !g.scripted) sb.append(" · breaks in ").append(g.breaksIn(today)).append(" play-days");
            if (!g.inside.isEmpty()) sb.append(" · ").append(g.inside.size()).append(" inside");
            Component line = Component.literal(sb.toString()).withColor(g.plan.rank().colour)
                    .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/awaken gate trace " + g.id))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Trace this gate"))));
            c.getSource().sendSuccess(() -> line, false);
        }
        return gates.size();
    }

    private static int trace(CommandContext<CommandSourceStack> c) {
        MinecraftServer s = c.getSource().getServer();
        GateRecord g = GateRegistry.get(s).gate(IntegerArgumentType.getInteger(c, "id"));
        if (g == null) {
            c.getSource().sendFailure(Component.literal("No such gate."));
            return 0;
        }
        boolean admin = c.getSource().permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        ServerPlayer p = c.getSource().getPlayer();
        if (!admin && (p == null || !g.owner.map(o -> o.equals(p.getUUID())).orElse(false))) {
            c.getSource().sendFailure(Component.literal("Only the Director's own gates for you (or game masters) can be traced."));
            return 0;
        }
        var plan = g.plan;
        List<String> lines = new ArrayList<>();
        lines.add("/awaken trace gate " + Long.toHexString(plan.seed()) + "  (#" + g.id + ", " + g.state.name().toLowerCase() + ")");
        lines.add("plan      " + plan.rank().label + " · " + plan.theme().displayName + " · " + plan.layout().displayName + " · "
                + plan.floors() + " floors · twist " + plan.twist().displayName + " · content v" + plan.contentVersion());
        lines.add("mods      " + (plan.modifiers().isEmpty() ? "none" : String.join(", ", plan.modifiers().stream().map((Modifier m) -> m.displayName).toList())));
        lines.add("lord      " + plan.boss() + (plan.hasAnnex() ? " · annex " + plan.annexTheme().displayName : ""));
        lines.add("where     " + g.pos.getX() + " " + g.pos.getY() + " " + g.pos.getZ() + " · cell " + g.cell + " (" + Cells.regionFile(g.cell) + ")");
        lines.add("timer     created play-day " + g.createdDay + " · breaks on " + (g.scripted ? "never" : g.breaksOnPlayDay));
        lines.addAll(g.trace);
        lines.add("invariants: no theme from the last 3 · no layout from the last 2 · modifiers within budget · twist at least every 12");
        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
