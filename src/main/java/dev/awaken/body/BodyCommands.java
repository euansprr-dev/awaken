package dev.awaken.body;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.body.net.BodyPayloads;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /awaken body}: the moves your body has and how to do them (never the ones still to come). Game masters:
 * {@code try <roll|sprint|slide|mantle|slidejump|tacslide|hit|release> [delay]} makes your client press the keys of a
 * move as a player would (the autopilot can't hold keys; the delay lines a frame up with a screenshot), {@code forget}
 * clears which unlocks were announced.
 */
final class BodyCommands {
    private BodyCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var body = Commands.literal("body").executes(c -> list(c.getSource()));
            var admin = Commands.literal("try").requires(AwakenCommands.admin())
                    .then(Commands.argument("move", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(BodyPayloads.TRIES, b))
                            .executes(c -> tryMove(c.getSource(), StringArgumentType.getString(c, "move"), 0))
                            .then(Commands.argument("delay", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 200))
                                    .executes(c -> tryMove(c.getSource(), StringArgumentType.getString(c, "move"),
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "delay")))));
            body.then(admin);
            // spike S3: the latency knob, the test course, the bench
            body.then(Commands.literal("latency").requires(AwakenCommands.admin())
                    .then(Commands.argument("ms", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1000)).executes(c -> {
                        RunnerServer.latencyTicks = Math.round(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "ms") / 50F);
                        c.getSource().sendSuccess(() -> Component.literal("Runner starts wait " + RunnerServer.latencyTicks + " ticks"), false);
                        return 1;
                    })));
            body.then(Commands.literal("course").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                RunnerServer.build(p.level(), p.blockPosition());
                c.getSource().sendSuccess(() -> Component.literal("Runner course built at " + RunnerServer.course.toShortString()), false);
                return 1;
            }));
            body.then(Commands.literal("bench").requires(AwakenCommands.admin())
                    .then(Commands.argument("what", StringArgumentType.word())
                            .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 200))
                                    .then(Commands.argument("ms", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1000))
                                            .executes(c -> bench(c, true, false))
                                            .then(Commands.literal("now").executes(c -> bench(c, false, false)))
                                            .then(Commands.literal("press").executes(c -> bench(c, true, true)))))));
            // tours (SPEC §17.27): stand in front of the nearest real fence, gate or wall with a run-up, facing it
            body.then(Commands.literal("rail").requires(AwakenCommands.admin())
                    .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(4, 64))
                            .then(Commands.argument("kind", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"any", "vault", "onto", "high"}, b))
                                    .executes(c -> rail(c.getSource(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"),
                                            StringArgumentType.getString(c, "kind"))))));
            body.then(Commands.literal("forget").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                p.setData(BodyContent.MEMORY, BodyMemory.EMPTY);
                c.getSource().sendSuccess(() -> Component.literal("The body forgot its announcements"), false);
                return 1;
            }));
            root.then(body);
        });
    }

    private static int bench(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean history, boolean press)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int ticks = RunnerServer.startBench(p, StringArgumentType.getString(c, "what"),
                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "count"),
                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "ms"), history, press);
        if (ticks == 0) c.getSource().sendFailure(Component.literal("Unknown bench or no course (/awaken body course)"));
        return ticks;
    }

    private static int list(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int n = 0;
        for (Move m : Move.values()) {
            if (!Body.unlocked(p, m)) continue;
            src.sendSuccess(() -> Component.literal(m.title + ": ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(m.how).withStyle(ChatFormatting.WHITE)), false);
            n++;
        }
        if (n == 0) src.sendSuccess(() -> Component.literal("You move like anyone else. For now.").withStyle(ChatFormatting.GRAY), false);
        return n;
    }

    /**
     * The nearest fence, gate or wall (by tag, or any collision taller than its block) within the radius that a running
     * body crosses as asked ({@code vault}: over it, {@code onto}: up onto it, {@code high}: a rail on a block, {@code
     * any}), from 4 blocks of level run-up square on to it: the player is put at the run-up's start, facing it.
     */
    private static int rail(CommandSourceStack src, int r, String want) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var level = p.level();
        var ctx = net.minecraft.world.phys.shapes.CollisionContext.empty();
        net.minecraft.core.BlockPos at = p.blockPosition(), best = null;
        net.minecraft.core.Direction bestDir = null;
        int bestKind = -1;
        double bestD = Double.MAX_VALUE;
        var m = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -8; dy <= 8; dy++) {
            m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
            var s = level.getBlockState(m);
            if (s.isAir()) continue;
            boolean rail = s.is(net.minecraft.tags.BlockTags.FENCES) || s.is(net.minecraft.tags.BlockTags.WALLS) || s.is(net.minecraft.tags.BlockTags.FENCE_GATES);
            if (!rail) continue;
            double d = dx * dx + dz * dz + dy * dy * 4;
            if (d >= bestD) continue;
            for (int c = 0; c < 8 && d < bestD; c++) {
                var dir = net.minecraft.core.Direction.from2DDataValue(c & 3);
                // 4 blocks of level ground out from it on that side, the feet at the rail's own base (or a block under
                // it: a rail on a block)
                var base = m.below(c >> 2);
                boolean ok = true;
                for (int k = 1; k <= 4 && ok; k++) {
                    var q = base.relative(dir, k);
                    ok = !level.getBlockState(q.below()).getCollisionShape(level, q.below()).isEmpty()
                            && level.getBlockState(q).getCollisionShape(level, q).isEmpty() && level.getBlockState(q.above()).getCollisionShape(level, q.above()).isEmpty();
                }
                if (!ok) continue;
                var near = base.relative(dir, 1);
                float yaw = dir.getOpposite().toYRot();
                VaultRules.Vault v = VaultRules.find(level, ctx, near.getX() + 0.5 + dir.getStepX() * 0.3, base.getY(), near.getZ() + 0.5 + dir.getStepZ() * 0.3,
                        yaw, VaultRules.REACH_RUN, true, 0.3);
                if (v == null) continue;
                boolean match = switch (want) {
                    case "vault" -> v.kind() == VaultRules.VAULT;
                    case "onto" -> v.kind() == VaultRules.ONTO;
                    case "high" -> v.kind() == VaultRules.HIGH;
                    default -> true;
                };
                if (!match) continue;
                best = base.immutable();
                bestDir = dir;
                bestKind = v.kind();
                bestD = d;
            }
        }
        if (best == null) {
            src.sendFailure(Component.literal("No fence or wall to cross within " + r));
            return 0;
        }
        var start = best.relative(bestDir, 4);
        p.connection.teleport(start.getX() + 0.5, best.getY(), start.getZ() + 0.5, bestDir.getOpposite().toYRot(), 10F);
        String what = level.getBlockState(best).getBlock().getDescriptionId() + " at " + best.toShortString() + ", kind " + bestKind;
        dev.awaken.Awaken.LOG.info("[body] rail: {}", what);
        src.sendSuccess(() -> Component.literal("Run at " + what), false);
        return 1;
    }

    private static int tryMove(CommandSourceStack src, String name, int delay) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int what = -1;
        for (int i = 0; i < BodyPayloads.TRIES.length; i++) if (BodyPayloads.TRIES[i].equals(name)) what = i;
        if (what < 0) {
            src.sendFailure(Component.literal("Unknown move " + name));
            return 0;
        }
        // the server's own input checks (sprinting, a held crouch) are what the keys would satisfy; the client presses
        // them for real, this only covers the moment before the server has seen those key states
        Move move = switch (name) {
            case "roll" -> Move.ROLL;
            case "sprint" -> Move.TACTICAL_SPRINT;
            case "slide", "slidejump", "tacslide" -> Move.SLIDE;
            case "mantle" -> Move.MANTLE;
            default -> null;
        };
        BodyServer.assist(p, move, 20 * 10 + delay);
        AwakenNet.send(p, new BodyPayloads.Try(what, delay));
        return 1;
    }
}
