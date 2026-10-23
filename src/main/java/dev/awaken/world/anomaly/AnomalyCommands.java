package dev.awaken.world.anomaly;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * {@code /awaken anomaly ...}: {@code why} (anyone: the countdown and the director's recent reasons), and for game
 * masters {@code now [kind] [grove variant]} (start one in front of you), {@code list}, {@code clear}, {@code skip}
 * (jump the active one to its next stage: a star lands, its guardian fades or it cools; a grove wakes), {@code timer
 * <seconds>} and {@code on|off} (the world switch).
 */
final class AnomalyCommands {
    private AnomalyCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var an = Commands.literal("anomaly");
            an.then(Commands.literal("why").executes(AnomalyCommands::why));
            an.then(Commands.literal("now").requires(AwakenCommands.admin())
                    .executes(c -> now(c, null, null))
                    .then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AnomalyKind.values()).map(k -> k.id), b))
                            .executes(c -> now(c, StringArgumentType.getString(c, "kind"), null))
                            .then(Commands.argument("variant", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(GroveVariant.values()).map(v -> v.id), b))
                                    .executes(c -> now(c, StringArgumentType.getString(c, "kind"), StringArgumentType.getString(c, "variant"))))));
            an.then(Commands.literal("list").requires(AwakenCommands.admin()).executes(AnomalyCommands::list));
            an.then(Commands.literal("clear").requires(AwakenCommands.admin()).executes(c -> {
                int n = AnomalyDirector.clearAll(c.getSource().getServer());
                c.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " anomal" + (n == 1 ? "y" : "ies") + "."), true);
                return n;
            }));
            an.then(Commands.literal("skip").requires(AwakenCommands.admin()).executes(AnomalyCommands::skip));
            an.then(Commands.literal("goto").requires(AwakenCommands.admin()).executes(AnomalyCommands::goTo));
            an.then(Commands.literal("timer").requires(AwakenCommands.admin())
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        int sec = IntegerArgumentType.getInteger(c, "seconds");
                        AnomalyDirector.memory(p).timer = sec * 20.0;
                        c.getSource().sendSuccess(() -> Component.literal("Next anomaly after " + sec + " s of outdoor play."), false);
                        return sec;
                    })));
            an.then(Commands.literal("on").requires(AwakenCommands.admin()).executes(c -> toggle(c, true)));
            an.then(Commands.literal("off").requires(AwakenCommands.admin()).executes(c -> toggle(c, false)));
            root.then(an);
        });
    }

    private static int why(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        for (String line : AnomalyDirector.status(p)) c.getSource().sendSuccess(() -> Component.literal(line), false);
        int shown = 0;
        for (var e : DirectorLog.recent(p.getUUID(), 40)) {
            if (!e.system().equals("anomaly")) continue;
            c.getSource().sendSuccess(() -> Component.literal("[anomaly] " + e.detail()), false);
            if (++shown >= 8) break;
        }
        return shown;
    }

    private static int now(CommandContext<CommandSourceStack> c, String kindId, String variantId) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        AnomalyKind kind;
        if (kindId == null) {
            AnomalyMemory mem = AnomalyDirector.memory(p);
            kind = Pacing.pick(Pacing.weights(false, p.level().isDarkOutside(), false, Progression.profile(p).level(), mem.lastKind,
                    AnomalyDirector.met(p)), p.getRandom());
            if (kind == null) kind = AnomalyKind.RIFT_TEAR;
        } else {
            kind = AnomalyKind.byId(kindId);
            if (kind == null) {
                c.getSource().sendFailure(Component.literal("Unknown kind " + kindId + ": "
                        + String.join(", ", Arrays.stream(AnomalyKind.values()).map(k -> k.id).toList()) + "."));
                return 0;
            }
        }
        GroveVariant variant = null;
        if (variantId != null) {
            variant = GroveVariant.byId(variantId);
            if (variant == null || kind != AnomalyKind.SHIMMER_GROVE) {
                c.getSource().sendFailure(Component.literal("A variant only goes with shimmer_grove: "
                        + String.join(", ", Arrays.stream(GroveVariant.values()).map(v -> v.id).toList()) + "."));
                return 0;
            }
        }
        StringBuilder why = new StringBuilder();
        Anomaly a = AnomalyDirector.start(p, kind, true, variant, why);
        if (a == null) {
            c.getSource().sendFailure(Component.literal("No " + kind.display.toLowerCase() + ": " + why));
            return 0;
        }
        AnomalyKind k = kind;
        c.getSource().sendSuccess(() -> Component.literal(k.display + " #" + a.id + " at " + a.focus().toShortString()
                + " (" + Spots.where(p.blockPosition(), a.focus()) + ")"), true);
        return a.id;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        MinecraftServer s = c.getSource().getServer();
        AnomalyData data = AnomalyData.get(s);
        long now = s.overworld().getGameTime();
        if (data.active().isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No active anomalies" + (data.enabled() ? "." : " (switched off).")), false);
        for (Anomaly a : data.active()) {
            ServerPlayer owner = s.getPlayerList().getPlayer(a.owner);
            String detail = switch (a.kind) {
                case RIFT_TEAR -> a.hitsLeft + "/" + a.maxHits + " hits, " + a.mobs.size() + " out (" + a.spawned + " total), " + a.theme;
                case SPIRIT_PROCESSION -> (a.stage == Anomaly.CACHE ? "cache placed" : a.stage == Anomaly.LEADING ? "leading" : "waiting")
                        + String.format(", %.0f%% of %.0f m", a.progress * 100, Processions.pathLength(a));
                case WANDERING_GATE -> "gate " + a.gateId;
                case FALLEN_STAR -> (!a.landed ? "falling" : switch (a.guardState) {
                    case Anomaly.GUARD_UP -> "guarded";
                    case Anomaly.GUARD_SLAIN -> "guardian slain, cooled";
                    case Anomaly.GUARD_FADED -> "cooled";
                    default -> "sealed, waiting";
                }) + ", guardian " + a.theme;
                case SHIMMER_GROVE -> Groves.variant(a).id + (a.stage == Anomaly.ACTIVE ? ", bending the rules" : ", waiting")
                        + (a.touched.isEmpty() ? "" : ", " + a.touched.size() + " touched");
            };
            String line = "#" + a.id + " " + a.kind.id + " at " + a.focus().toShortString() + " for " + (owner == null ? a.owner : owner.getGameProfile().name())
                    + " · " + detail + " · ends in " + Math.max(0, (a.endsAt - now) / 20) + " s";
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        int removals = data.removals().size();
        if (removals > 0) c.getSource().sendSuccess(() -> Component.literal(removals + " block(s) waiting for their chunk to load to be taken down."), false);
        return data.active().size();
    }

    private static int skip(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MinecraftServer s = c.getSource().getServer();
        AnomalyData data = AnomalyData.get(s);
        Anomaly a = data.activeFor(p.getUUID());
        if (a == null) {
            c.getSource().sendFailure(Component.literal("You have no active anomaly."));
            return 0;
        }
        ServerLevel level = s.overworld();
        long now = level.getGameTime();
        switch (a.kind) {
            case SPIRIT_PROCESSION -> {
                if (a.stage == Anomaly.CACHE) a.endsAt = now; // the cache fades
                else {
                    a.stage = Anomaly.LEADING;
                    a.progress = 1.0;
                }
            }
            case FALLEN_STAR -> {
                if (!a.landed) Stars.land(level, a.id); // straight into the ground
                else if (a.guardState == Anomaly.GUARD_UP) a.guardUntil = now; // the guardian fades on the next pass
                else if (a.guardState == Anomaly.GUARD_NONE) {
                    a.guardState = Anomaly.GUARD_FADED; // no guardian: it cools at once
                    Stars.cool(level, data, a, now);
                } else a.endsAt = Math.min(a.endsAt, now + 20 * 5);
            }
            case SHIMMER_GROVE -> {
                if (a.stage != Anomaly.ACTIVE) Groves.activate(level, data, a, p, now); // wake it for you, wherever you stand
                else a.endsAt = Math.min(a.endsAt, now + 20 * 5);
            }
            default -> a.endsAt = Math.min(a.endsAt, now + 20 * 5); // the last five seconds
        }
        data.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("Skipped " + a.kind.display.toLowerCase() + " #" + a.id + " ahead."), false);
        return 1;
    }

    /** Stands you two blocks from your active anomaly's heart, facing it (a grove wakes as you arrive). */
    private static int goTo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Anomaly a = AnomalyData.get(c.getSource().getServer()).activeFor(p.getUUID());
        if (a == null) {
            c.getSource().sendFailure(Component.literal("You have no active anomaly."));
            return 0;
        }
        net.minecraft.core.BlockPos f = a.focus();
        double x = f.getX() + 0.5, z = f.getZ() + 2.5;
        double y = Spots.surface(p.level(), net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z)).getY() + 1;
        p.teleportTo(p.level(), x, y, z, java.util.Set.of(), 180.0F, 20.0F, true);
        c.getSource().sendSuccess(() -> Component.literal("At " + a.kind.display.toLowerCase() + " #" + a.id + "."), false);
        return 1;
    }

    private static int toggle(CommandContext<CommandSourceStack> c, boolean on) {
        AnomalyData.get(c.getSource().getServer()).setEnabled(on);
        c.getSource().sendSuccess(() -> Component.literal("Anomalies " + (on ? "on" : "off") + " in this world."), true);
        return 1;
    }
}
