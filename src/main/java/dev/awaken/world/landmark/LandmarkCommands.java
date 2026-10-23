package dev.awaken.world.landmark;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.above.scan.Scanning;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import dev.awaken.world.anomaly.Spots;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /awaken landmark ...}: {@code why} (anyone: the landmarks around you, which you've reached, which one the Now
 * tracker suggests and why, the recent decisions), and for game masters {@code near} (the full list with ids within
 * 1,500 blocks, spending more terrain samples), {@code markers} (resend your markers now), {@code check} (look around
 * for landmarks now) and {@code forget} (forget the landmarks you reached; discoveries stay paid).
 */
final class LandmarkCommands {
    private LandmarkCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var lm = Commands.literal("landmark");
            lm.then(Commands.literal("why").executes(c -> why(c, 6)));
            lm.then(Commands.literal("near").requires(AwakenCommands.admin()).executes(c -> why(c, 64)));
            lm.then(Commands.literal("markers").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                LandmarkMarkers.forget(p.getUUID());
                LandmarkMarkers.refresh(p);
                int n = LandmarkMarkers.compute(p).size();
                c.getSource().sendSuccess(() -> Component.literal(n + " landmark marker(s) sent."), false);
                return n;
            }));
            lm.then(Commands.literal("check").requires(AwakenCommands.admin()).executes(c -> {
                int n = LandmarkWatch.check(c.getSource().getPlayerOrException(), true);
                c.getSource().sendSuccess(() -> Component.literal(n == 0 ? "No new landmark here." : n + " landmark(s) reached."), false);
                return n;
            }));
            lm.then(Commands.literal("forget").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                LandmarkMemory m = Landmarks.memory(p);
                int n = m.reached.size();
                m.reached.clear();
                m.told.clear();
                m.whispered = "";
                LandmarkNow.forget(p.getUUID());
                Scanning.forget(p.getUUID());
                c.getSource().sendSuccess(() -> Component.literal("Forgot " + n + " landmark(s) you had reached."), false);
                return n;
            }));
            root.then(lm);
        });
    }

    private static int why(CommandContext<CommandSourceStack> c, int budget) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int level = Progression.profile(p).level();
        List<Landmarks.Landmark> all = Landmarks.around(p, budget > 6 ? 1500 : Landmarks.NEAR, budget);
        if (all.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No landmarks known around you (the Well may not be built yet)."), false);
        int shown = 0;
        for (Landmarks.Landmark l : all) {
            if (shown++ >= (budget > 6 ? 16 : 6)) break;
            boolean known = Landmarks.reached(p, l.id());
            String why = known ? "reached" : level >= l.reasonLevel() ? "worth a visit now" : "from level " + l.reasonLevel();
            String line = l.kind().display + (l.spawnVault() ? " (the one near spawn)" : "") + " · " + Spots.where(p.blockPosition(), l.pos())
                    + " · " + why + (budget > 6 ? " · " + l.id() : "");
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        var now = LandmarkNow.suggest(p);
        c.getSource().sendSuccess(() -> Component.literal(now.isEmpty() ? "Now tracker: no landmark line (at home, in a gate, or nothing worth it yet)"
                : "Now tracker: " + now.get(0).text().getString()), false);
        int logged = 0;
        for (var e : DirectorLog.recent(p.getUUID(), 40)) {
            if (!e.system().equals("landmark")) continue;
            c.getSource().sendSuccess(() -> Component.literal("[landmark] " + e.detail()), false);
            if (++logged >= 5) break;
        }
        return all.size();
    }
}
