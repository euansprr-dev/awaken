package dev.awaken.world.landmark;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.above.scan.Scanning;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import dev.awaken.world.anomaly.Spots;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /awaken landmark ...}: {@code why} (anyone: the landmarks around you, which you've reached, what the Horizon
 * line shows and why, the recent decisions), and for game masters {@code near} (the full list with ids within
 * 1,500 blocks, spending more terrain samples), {@code markers} (resend your markers now), {@code check} (look around
 * for landmarks now), {@code goto <kind>} (stand south of the nearest one, facing it) and {@code forget} (forget the
 * landmarks you reached; discoveries stay paid).
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
            lm.then(Commands.literal("hear").requires(AwakenCommands.admin()).executes(c -> {
                // as if told of every landmark within 700 blocks (and the Well): they go on the Gate Map, outlined
                ServerPlayer p = c.getSource().getPlayerOrException();
                LandmarkMemory m = Landmarks.memory(p);
                int n = 0;
                for (Landmarks.Landmark l : Landmarks.around(p, Landmarks.NEAR, 8)) if (m.place(l.id(), l.kind(), l.pos())) n++;
                if (n > 0) Landmarks.sync(p);
                int heard = n;
                c.getSource().sendSuccess(() -> Component.literal(heard + " landmark(s) put on your Gate Map."), false);
                return n;
            }));
            lm.then(Commands.literal("check").requires(AwakenCommands.admin()).executes(c -> {
                int n = LandmarkWatch.check(c.getSource().getPlayerOrException(), true);
                c.getSource().sendSuccess(() -> Component.literal(n == 0 ? "No new landmark here." : n + " landmark(s) reached."), false);
                return n;
            }));
            lm.then(Commands.literal("goto").requires(AwakenCommands.admin())
                    .then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(LandmarkKind.values())
                                    .filter(k -> k != LandmarkKind.GATE).map(k -> k.id), b))
                            .executes(c -> goTo(c, StringArgumentType.getString(c, "kind")))));
            lm.then(Commands.literal("forget").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                LandmarkMemory m = Landmarks.memory(p);
                int n = m.reached.size();
                m.reached.clear();
                m.told.clear();
                m.places.clear();
                m.whispered = "";
                Landmarks.sync(p);
                LandmarkHorizon.forget(p.getUUID());
                Scanning.forget(p.getUUID());
                c.getSource().sendSuccess(() -> Component.literal("Forgot " + n + " landmark(s) you had reached."), false);
                return n;
            }));
            root.then(lm);
        });
    }

    /**
     * Stands you {@link #VIEW} blocks south of the nearest landmark of a kind (within 4,000 blocks), facing it: for
     * screenshots of its marker. Sky platforms: from below, looking up at them.
     */
    private static int goTo(CommandContext<CommandSourceStack> c, String kindId) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        LandmarkKind kind = null;
        for (LandmarkKind k : LandmarkKind.values()) if (k.id.equals(kindId) && k != LandmarkKind.GATE) kind = k;
        if (kind == null) {
            c.getSource().sendFailure(Component.literal("Unknown landmark kind " + kindId + "."));
            return 0;
        }
        BlockPos target = null;
        if (kind == LandmarkKind.WELL) {
            target = Landmarks.wellPos(c.getSource().getServer()).orElse(null);
        } else {
            for (RuinKind r : RuinKind.values()) {
                if (LandmarkKind.of(r) != kind) continue;
                for (RuinLocator.Found f : RuinLocator.near(p.level(), p.blockPosition(), 4000, List.of(r), null, false, 1, 64)) {
                    if (target == null || Spots.horizontal(p.blockPosition(), f.pos()) < Spots.horizontal(p.blockPosition(), target)) target = f.pos();
                }
            }
        }
        if (target == null) {
            c.getSource().sendFailure(Component.literal("No " + kind.display + " found nearby."));
            return 0;
        }
        ServerLevel level = p.level();
        double x = target.getX() + 0.5, z = target.getZ() + VIEW + 0.5;
        level.getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4); // load where you'll stand
        double y = Spots.surface(level, Mth.floor(x), Mth.floor(z)).getY() + 1 + (kind == LandmarkKind.OCEAN_LAB ? 0 : 6);
        float pitch = kind == LandmarkKind.SKY_PLATFORM ? -35.0F : 10.0F;
        p.teleportTo(level, x, y, z, java.util.Set.of(), 180.0F, pitch, true);
        LandmarkMarkers.forget(p.getUUID());
        BlockPos t = target;
        c.getSource().sendSuccess(() -> Component.literal(kindId + " at " + t.getX() + " " + t.getZ() + "; you stand " + VIEW + " blocks south."), false);
        return 1;
    }

    static final int VIEW = 48;

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
        var line = dev.awaken.core.api.HorizonHooks.collect(p);
        c.getSource().sendSuccess(() -> Component.literal(line.isEmpty() ? "Horizon line: nothing (at home, in a gate, or nothing worth it yet)"
                : "Horizon line: " + line.get().name() + (line.get().tease() ? " · needs " + line.get().need() + " (a tease)" : "")), false);
        int logged = 0;
        for (var e : DirectorLog.recent(p.getUUID(), 40)) {
            if (!e.system().equals("landmark")) continue;
            c.getSource().sendSuccess(() -> Component.literal("[landmark] " + e.detail()), false);
            if (++logged >= 5) break;
        }
        return all.size();
    }
}
