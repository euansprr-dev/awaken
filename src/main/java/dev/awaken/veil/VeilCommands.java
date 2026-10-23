package dev.awaken.veil;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /awaken veil ...
 * <ul>
 *   <li>{@code project [player]} (admin): step out now, no meditation.</li>
 *   <li>{@code return} (anyone; admin with a player): back into the body.</li>
 *   <li>{@code stillpoint [player]} (admin): go to the Stillpoint, or leave it.</li>
 *   <li>{@code spirits} (admin): the spirits standing near you.</li>
 *   <li>{@code status}: cord, Veil time and Awareness from your stats.</li>
 * </ul>
 */
final class VeilCommands {
    private VeilCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("veil")
                .then(Commands.literal("project").requires(AwakenCommands.admin())
                        .executes(c -> project(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> project(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("return")
                        .executes(c -> back(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(AwakenCommands.admin())
                                .executes(c -> back(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("stillpoint").requires(AwakenCommands.admin())
                        .executes(c -> stillpoint(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> stillpoint(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("spirits").requires(AwakenCommands.admin())
                        .executes(VeilCommands::spirits))
                .then(Commands.literal("status")
                        .executes(VeilCommands::status))));
    }

    private static int project(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        boolean ok = Projection.start(p, true, null);
        c.getSource().sendSuccess(() -> Component.literal(ok ? p.getScoreboardName() + " stepped out of their body."
                : "Couldn't project " + p.getScoreboardName() + " (already out, dead, asleep or a spectator)."), true);
        return ok ? 1 : 0;
    }

    private static int back(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        boolean out = Veil.isSpirit(p);
        if (out) Projection.end(p, Projection.EndReason.COMMAND);
        else Projection.restoreFromState(p, false);
        c.getSource().sendSuccess(() -> Component.literal(out ? p.getScoreboardName() + " is back in their body." : p.getScoreboardName() + " wasn't out."), false);
        return out ? 1 : 0;
    }

    private static int stillpoint(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        if (Veil.isSpirit(p)) Projection.end(p, Projection.EndReason.COMMAND);
        boolean entering = !Veil.inStillpoint(p);
        if (entering) {
            boolean ok = Stillpoint.enter(p);
            if (!ok) {
                c.getSource().sendFailure(Component.literal("The Stillpoint is unavailable (Veil disabled or dimension missing)."));
                return 0;
            }
        } else {
            Stillpoint.leave(p);
        }
        c.getSource().sendSuccess(() -> Component.literal(p.getScoreboardName() + (entering ? " is in the Stillpoint." : " left the Stillpoint.")), true);
        return 1;
    }

    private static int spirits(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        List<Spirits.Spot> spots = Spirits.nearestSpots(p.level(), p.position(), 3);
        if (spots.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("No spirits stand near here (they live in the overworld, in loaded ground)."), false);
            return 0;
        }
        VeilMemory mem = p.getData(VeilContent.MEMORY.get());
        int n = 0;
        for (Spirits.Spot s : spots) {
            if (n++ >= 12) break;
            SpiritDef def = Spirits.def(s.spiritId());
            if (def == null) continue;
            int heard = mem.spiritLines.getOrDefault(s.spiritId(), 0);
            String line = String.format("%s · %s · %d blocks · (%d, %d, %d) · heard %d/%d", def.displayName(), s.biome(),
                    (int) s.pos().distanceTo(p.position()), (int) s.pos().x, (int) s.pos().y, (int) s.pos().z, heard, def.lines().size());
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return spots.size();
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String msg = String.format("Cord %d blocks · Veil time %d s · Awareness %d blocks · Secret sense %d blocks",
                Projection.cordRadius(p), Projection.veilTicks(p) / 20, Projection.awareness(p),
                Cord.secretSense(dev.awaken.progression.Progression.stat(p, dev.awaken.progression.Stat.PER),
                        dev.awaken.progression.Progression.perk(p, dev.awaken.progression.Perks.SECRET_GLOW_RANGE)));
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }
}
