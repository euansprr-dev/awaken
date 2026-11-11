package dev.awaken.frontier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.TheLull;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * /awaken frontier status · goto ridge|landing · fly [quick] · ready · letter · invite &lt;bell&gt; · homecoming (SPEC §67).
 * Dev and tours: where everything is, standing at either helm, a flight from where you stand, the skiff's go-ahead
 * without Ilse's scene, a letter, an invitation and the homecoming now.
 */
final class FrontierCommands {
    private FrontierCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("frontier").requires(AwakenCommands.admin())
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("goto")
                        .then(Commands.literal("ridge").executes(c -> go(c.getSource(), Moorings.Which.RIDGE)))
                        .then(Commands.literal("landing").executes(c -> go(c.getSource(), Moorings.Which.LANDING))))
                .then(Commands.literal("look")
                        .then(Commands.literal("ridge").executes(c -> look(c.getSource(), Moorings.Which.RIDGE, 0))
                                .then(Commands.argument("view", IntegerArgumentType.integer(0, 2)).executes(c -> look(c.getSource(), Moorings.Which.RIDGE, IntegerArgumentType.getInteger(c, "view")))))
                        .then(Commands.literal("landing").executes(c -> look(c.getSource(), Moorings.Which.LANDING, 0))
                                .then(Commands.argument("view", IntegerArgumentType.integer(0, 2)).executes(c -> look(c.getSource(), Moorings.Which.LANDING, IntegerArgumentType.getInteger(c, "view"))))))
                .then(Commands.literal("fly").executes(c -> fly(c.getSource(), false))
                        .then(Commands.literal("quick").executes(c -> fly(c.getSource(), true))))
                .then(Commands.literal("ready").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    FrontierData.get(p.level().getServer()).mark(p.getUUID(), "ready");
                    c.getSource().sendSuccess(() -> Component.literal("The skiff will take you now."), false);
                    return 1;
                }))
                .then(Commands.literal("letter").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    FrontierPost.letter(p, "Bram", "Just checking the skiff carries letters. If you're reading this, it does. Don't write back, I've enough paper on my desk.");
                    return 1;
                }))
                .then(Commands.literal("invite").then(Commands.argument("bell", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return Invitations.send(p, StringArgumentType.getString(c, "bell")) ? 1 : 0;
                })))
                .then(Commands.literal("homecoming").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    return Homecoming.begin(p) ? 1 : 0;
                }))));
    }

    private static int status(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        BlockPos landing = TheLull.landing(server);
        if (landing == null) {
            src.sendSuccess(() -> Component.literal("Not a Far Landing world (rules below 4, or no Carillon)."), false);
            return 0;
        }
        FrontierData d = FrontierData.get(server);
        BlockPos c = TheLull.centre(server);
        int km = (int) Math.round(Math.hypot(landing.getX() - c.getX(), landing.getZ() - c.getZ()));
        src.sendSuccess(() -> Component.literal("The landing: " + landing.getX() + ", " + landing.getZ() + " (" + km + " from Carillon's middle)"), false);
        for (Moorings.Which w : Moorings.Which.values()) {
            FrontierData.Mooring m = Moorings.get(server, w);
            String line = w.name().toLowerCase(java.util.Locale.ROOT) + " mooring: " + (m == null ? "not chosen yet" : m.origin().toShortString() + " facing "
                    + m.facing().getSerializedName() + (m.built() ? ", built" : ", not built"));
            src.sendSuccess(() -> Component.literal(line), false);
        }
        Awaken.LOG.info("[frontier] status: landing {}, {} from the middle; ridge {}; landing mooring {}", landing.toShortString(), km, d.ridge(), d.landing());
        return 1;
    }

    /** At the helm of a mooring (built first if its land is loaded; else at its spot, high up, to load it). */
    private static int go(CommandSourceStack src, Moorings.Which which) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        ServerLevel level = server.overworld();
        if (!TheLull.farLanding(server)) {
            src.sendFailure(Component.literal("Not a Far Landing world."));
            return 0;
        }
        Moorings.ensure(level, which);
        FrontierData.Mooring m = Moorings.get(server, which);
        if (m != null && m.built()) {
            double[] s = Moorings.stand(m);
            p.teleportTo(level, s[0], s[1], s[2], Set.of(), (float) s[3], 10F, true);
            Awaken.LOG.info("[frontier] goto {}: at the helm, {}", which, m.origin().toShortString());
            return 1;
        }
        BlockPos spot = which == Moorings.Which.RIDGE ? Moorings.ridgeSpot(server) : Moorings.landingSpot(server);
        if (spot == null) return 0;
        p.teleportTo(level, spot.getX() + 0.5, 200, spot.getZ() + 0.5, Set.of(), p.getYRot(), 40F, true);
        src.sendSuccess(() -> Component.literal("Its land is loading; run this again in a moment."), false);
        Awaken.LOG.info("[frontier] goto {}: loading its land at {}", which, spot.toShortString());
        return 0;
    }

    /** The camera on a built mooring for a picture: 0 the bow quarter, 1 the stern quarter with the jetty, 2 high over it. */
    private static int look(CommandSourceStack src, Moorings.Which which, int view) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        FrontierData.Mooring m = Moorings.get(server, which);
        if (m == null || !m.built()) {
            src.sendFailure(Component.literal("Not built yet (/awaken frontier goto " + which.name().toLowerCase(java.util.Locale.ROOT) + ")."));
            return 0;
        }
        int[][] eyes = {{11, 5, 13}, {-12, 6, -15}, {6, 26, -8}};
        int[] e = eyes[view];
        BlockPos eye = Skiff.at(m.origin(), m.facing(), e[0], e[1], e[2]);
        double tx = m.origin().getX() + 0.5, ty = m.origin().getY() + 3, tz = m.origin().getZ() + 0.5;
        double dx = tx - (eye.getX() + 0.5), dy = ty - (eye.getY() + 1.62), dz = tz - (eye.getZ() + 0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        p.teleportTo(server.overworld(), eye.getX() + 0.5, eye.getY(), eye.getZ() + 0.5, Set.of(), yaw, pitch, true);
        Awaken.LOG.info("[frontier] look {} view {}: from {} at the skiff {}", which, view, eye.toShortString(), m.origin().toShortString());
        return 1;
    }

    /** A flight from the mooring you stand on (as if you'd used its helm). */
    private static int fly(CommandSourceStack src, boolean quick) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        MinecraftServer server = src.getServer();
        for (Moorings.Which w : Moorings.Which.values()) {
            FrontierData.Mooring m = Moorings.get(server, w);
            if (m != null && m.built() && Skiff.onDeck(m.origin(), m.facing(), p.getX(), p.getY(), p.getZ())) {
                if (quick) FrontierData.get(server).mark(p.getUUID(), "flown");
                Flights.helm(p, w, quick);
                return 1;
            }
        }
        src.sendFailure(Component.literal("Stand on a skiff's deck first (/awaken frontier goto ridge)."));
        return 0;
    }
}
