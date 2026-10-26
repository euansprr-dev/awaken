package dev.awaken.delve.cave;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.delve.Lode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /awaken delve place <kind>} lays a cave place round you as a site search would (tours: away from Carillon, whose
 * footprint is protected at every depth), with a pocket of natural cave at its joint, and puts you at its way in;
 * {@code place go <mark>} takes you to one of its marks (start, goal, dive, pool, stope, incline, crawl, bridge, mouth,
 * lip, curtain, pool, socket, nest, vent, spire0..3, office); {@code places} lists the places known near you;
 * {@code seep quiet|wake} sets the seep you're in. A sinkhole is laid with its rim where you stand; every other place
 * with its floor at your feet, facing the way you face.
 */
final class CaveCommands {
    private CaveCommands() {}

    private static Map<String, BlockPos> lastMarks = new HashMap<>();
    private static float lastYaw;

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var kinds = Arrays.stream(CaveKind.values()).map(k -> k.id).toList();
            var d = Commands.literal("delve");
            d.then(Commands.literal("place").requires(AwakenCommands.admin())
                    .then(Commands.literal("go").then(Commands.argument("mark", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(lastMarks.keySet(), b)).executes(c -> go(c, null))
                            .then(Commands.argument("toward", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(lastMarks.keySet(), b))
                                    .executes(c -> go(c, StringArgumentType.getString(c, "toward"))))))
                    .then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(kinds, b))
                            .executes(CaveCommands::place)));
            d.then(Commands.literal("goto").requires(AwakenCommands.admin())
                    .then(Commands.argument("kind", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(kinds, b))
                            .executes(CaveCommands::gotoPlace)));
            d.then(Commands.literal("places").executes(CaveCommands::places));
            d.then(Commands.literal("seep").requires(AwakenCommands.admin())
                    .then(Commands.literal("quiet").executes(c -> seep(c, true)))
                    .then(Commands.literal("wake").executes(c -> seep(c, false))));
            root.then(d);
        });
    }

    private static int place(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CaveKind kind = CaveKind.byId(StringArgumentType.getString(c, "kind"));
        if (kind == null) {
            c.getSource().sendFailure(Component.literal("No such place: sinkhole, mine, seep, chute or grotto"));
            return 0;
        }
        ServerLevel level = p.level();
        BlockPos at = p.blockPosition();
        Direction facing = p.getDirection();
        int rot = switch (facing) {
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
        long seed = level.getSeed() ^ at.asLong() * 31 + kind.ordinal();
        Spec s = CaveStaging.spec(kind, at.getX(), at.getY(), at.getZ(), rot, seed);
        long t0 = System.nanoTime();
        BoundingBox box = s.box();
        CavePiece.LevelSink cave = new CavePiece.LevelSink(level, box, level.getRandom());
        CaveStaging.cave(s, cave);
        Map<String, BlockPos> marks = CavePiece.build(level, s, box);
        if (kind == CaveKind.SINKHOLE) layLode(level, s);
        CavePlaces.stage(s);
        double ms = (System.nanoTime() - t0) / 1e6;
        lastMarks = marks;
        lastYaw = p.getYRot();
        BlockPos start = marks.get("start");
        if (start != null) teleport(p, start, kind == CaveKind.SINKHOLE ? yawToward(start, s.x(), s.z()) : p.getYRot());
        c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s \"%s\" laid in %.0f ms; marks: %s",
                kind.id, CaveNames.name(s), ms, String.join(", ", marks.keySet()))).withColor(0x9FE6D6), false);
        return 1;
    }

    /**
     * To the nearest place of a kind the world really made (within 1,600), at its way in, its marks kept for {@code place
     * go}; with none near, one is laid round you as {@code place} would.
     */
    private static int gotoPlace(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CaveKind kind = CaveKind.byId(StringArgumentType.getString(c, "kind"));
        if (kind == null) {
            c.getSource().sendFailure(Component.literal("No such place: sinkhole, mine, seep, chute or grotto"));
            return 0;
        }
        ServerLevel level = p.level();
        long t0 = System.nanoTime();
        Spec s = CaveLocate.nearest(level, p.blockPosition(), kind, 1600);
        if (s == null) {
            c.getSource().sendSuccess(() -> Component.literal("No " + kind.id + " within 1600: laying one here").withColor(0xC8C2B0), false);
            return place(c);
        }
        // its marks, from the place built in memory (the world builds the real one as its chunks generate)
        Grid g = CaveTests.grid(s);
        s.build(g);
        Map<String, BlockPos> marks = new HashMap<>();
        for (var e : g.marks.entrySet()) marks.put(e.getKey(), new BlockPos(e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        lastMarks = marks;
        CavePlaces.KNOWN.put(s.id(), s);
        BlockPos start = marks.getOrDefault("start", new BlockPos(s.x(), s.y(), s.z()));
        level.getChunk(start.getX() >> 4, start.getZ() >> 4);
        BlockPos at = safe(level, start);
        BlockPos look = kind == CaveKind.SINKHOLE ? new BlockPos(s.x(), at.getY() - 6, s.z()) : marks.getOrDefault("goal", at);
        double dx = look.getX() - at.getX(), dy = look.getY() + 1.0 - (at.getY() + 1.62), dz = look.getZ() - at.getZ();
        teleport(p, at, (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        double ms = (System.nanoTime() - t0) / 1e6;
        dev.awaken.Awaken.LOG.info("[delve] goto {} \"{}\" at {} {} {} ({} ms), you at {}", kind.id, CaveNames.name(s), s.x(), s.y(), s.z(),
                Math.round(ms), at.toShortString());
        c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s \"%s\" at %d %d %d (found in %.0f ms); marks: %s",
                kind.id, CaveNames.name(s), s.x(), s.y(), s.z(), ms, String.join(", ", marks.keySet()))).withColor(0x9FE6D6), false);
        return 1;
    }

    /** A spot to stand at or just above {@code at} (the world's own ground may differ a little from a mark's). */
    static BlockPos safe(ServerLevel level, BlockPos at) {
        BlockPos.MutableBlockPos m = at.mutable();
        for (int i = 0; i < 24; i++) {
            boolean feet = level.getBlockState(m).getCollisionShape(level, m).isEmpty() && level.getFluidState(m).isEmpty();
            boolean head = level.getBlockState(m.above()).getCollisionShape(level, m.above()).isEmpty();
            if (feet && head) return m.immutable();
            m.move(0, 1, 0);
        }
        return at;
    }

    /** The staged sinkhole's lode: its blocks written into the rock (worldgen writes planned lodes itself) and laid for every query. */
    private static void layLode(ServerLevel level, Spec s) {
        Lode l = CaveStaging.lode(s);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (long cell : l.cells()) {
            m.set(cell);
            BlockState here = level.getBlockState(m);
            if (!here.isSolidRender()) continue;
            boolean deep = m.getY() < 0;
            level.setBlock(m, cell == l.heart() ? l.ore().heart().defaultBlockState() : l.ore().block(deep).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        CaveStaging.stageLode(s);
    }

    /** To a mark, facing another one when given (its eye on that mark's middle). */
    private static int go(CommandContext<CommandSourceStack> c, String toward) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos at = lastMarks.get(StringArgumentType.getString(c, "mark"));
        BlockPos to = toward == null ? null : lastMarks.get(toward);
        if (at == null || toward != null && to == null) {
            c.getSource().sendFailure(Component.literal("No such mark in the last place laid: " + String.join(", ", lastMarks.keySet())));
            return 0;
        }
        at = safe(p.level(), at);
        if (to == null) {
            teleport(p, at, p.getYRot(), p.getXRot());
            return 1;
        }
        double dx = to.getX() - at.getX(), dy = to.getY() + 1.0 - (at.getY() + 1.62), dz = to.getZ() - at.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        teleport(p, at, yaw, pitch);
        return 1;
    }

    private static void teleport(ServerPlayer p, BlockPos at, float yaw) {
        teleport(p, at, yaw, p.getXRot());
    }

    private static void teleport(ServerPlayer p, BlockPos at, float yaw, float pitch) {
        p.teleportTo(p.level(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), yaw, pitch, true);
    }

    private static float yawToward(BlockPos from, int x, int z) {
        return (float) Math.toDegrees(Math.atan2(-(x + 0.5 - from.getX() - 0.5), z + 0.5 - from.getZ() - 0.5));
    }

    private static int places(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = 0;
        for (Spec s : CavePlaces.KNOWN.values()) {
            double dx = s.x() - p.getX(), dz = s.z() - p.getZ();
            if (dx * dx + dz * dz > 1024 * 1024) continue;
            n++;
            boolean quiet = s.kind() == CaveKind.SEEP && CaveData.get(p.level()).quiet(s.id());
            c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s \"%s\" at %d %d %d, %.0f blocks%s", s.kind().id,
                    CaveNames.name(s), s.x(), s.y(), s.z(), Math.sqrt(dx * dx + dz * dz), quiet ? " (quiet)" : "")).withColor(0xC8C2B0), false);
        }
        Spec here = CavePlaces.at(p.level(), p.blockPosition());
        if (here != null) c.getSource().sendSuccess(() -> Component.literal("You are in " + CaveNames.name(here)).withColor(0x9FE6D6), false);
        if (n == 0) c.getSource().sendSuccess(() -> Component.literal("No cave place known within 1024 (they become known as their chunks load).")
                .withColor(0xC8C2B0), false);
        return n;
    }

    private static int seep(CommandContext<CommandSourceStack> c, boolean quiet) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Spec s = CavePlaces.at(p.level(), p.blockPosition());
        if (s == null || s.kind() != CaveKind.SEEP) {
            c.getSource().sendFailure(Component.literal("You're not in a seep"));
            return 0;
        }
        if (quiet) CaveData.get(p.level()).quieten(s.id());
        else CaveData.get(p.level()).wake(s.id());
        return 1;
    }
}
