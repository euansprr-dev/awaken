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
            d.then(Commands.literal("way").requires(AwakenCommands.admin()).executes(CaveCommands::way));
            d.then(Commands.literal("places").executes(CaveCommands::places));
            d.then(Commands.literal("mouths").requires(AwakenCommands.admin())
                    .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(32, 4096)).executes(CaveCommands::mouths)));
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
        Spec s = CaveLocate.nearest(level, p.blockPosition(), kind, 1600, true, sp -> real(level, sp));
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
        lastGoto = s;
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

    private static @org.jspecify.annotations.Nullable Spec lastGoto;

    /**
     * The last {@code goto}'s mouth walked on the world's real blocks: how much natural cave a body reaches on foot from
     * the lip, whether it can walk back, and how much cave lies past the joint: the pack's terrain, which the tests can't reach (SPEC §34.1; a tour reads it from the log).
     */
    private static int way(CommandContext<CommandSourceStack> c) {
        Spec s = lastGoto;
        if (s == null || s.kind() != CaveKind.MOUTH) {
            c.getSource().sendFailure(Component.literal("Go to a mouth first: /awaken delve goto mouth"));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        Grid built = CaveTests.grid(s);
        s.build(built);
        int[] goal = built.mark("goal"), mark = built.mark(s.p(Mouth.FORM) == Mouth.DOLINE ? "start" : "mouth");
        Grid real = new Grid(built.minX(), built.minY(), built.minZ(), built.maxX(), built.maxY(), built.maxZ());
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = real.minX(); x <= real.maxX(); x++) for (int z = real.minZ(); z <= real.maxZ(); z++) for (int y = real.minY(); y <= real.maxY(); y++)
            real.set(x, y, z, level.getBlockState(m.set(x, y, z)));
        // the real ground by a mark can stand a little off the plan
        int[] lip = {mark[0], mark[1], mark[2]};
        for (int y = mark[1] + 8; y >= mark[1] - 8; y--) {
            if (!Double.isNaN(Routes.feet(real, mark[0], y, mark[2])) && real.get(mark[0], y, mark[2]).isAir()) {
                lip[1] = y;
                break;
            }
        }
        // a goal out of the box: the search then says everywhere a body can get to from the lip
        Routes.Result all = Routes.find(real, lip, new int[]{real.minX() - 64, real.minY(), real.minZ() - 64});
        int cave = 0;
        int[] deepest = null;
        double best = Double.MAX_VALUE;
        for (int x = real.minX(); x <= real.maxX(); x++) for (int z = real.minZ(); z <= real.maxZ(); z++) for (int y = real.minY(); y <= real.maxY(); y++) {
            if (!Routes.reached(real, all.seen(), x, y, z) || !real.get(x, y, z).isAir() || built.get(x, y, z).isAir() || Double.isNaN(Routes.feet(real, x, y, z))) continue;
            if (y >= level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)) continue;
            cave++;
            double d = Math.abs(x - goal[0]) + Math.abs(z - goal[2]) + Math.abs(y - goal[1]) * 1.5;
            if (d < best) {
                best = d;
                deepest = new int[]{x, y, z};
            }
        }
        boolean back = deepest != null && Routes.find(real, deepest, lip).found();
        String line = String.format(Locale.ROOT, "form %d at %d %d %d, joint %d %d %d, %d legs: on foot %d floor cells of natural cave reached, nearest the joint %s, back out from there %s; %d cells of cave past the joint",
                s.p(Mouth.FORM), s.x(), s.y(), s.z(), s.jx(), s.jy(), s.jz(), Math.max(1, s.p(Mouth.LEGS)), cave, deepest == null ? "none" : deepest[0] + " " + deepest[1] + " " + deepest[2],
                back ? "yes" : "NO", caveBeyond(level, built, goal));
        dev.awaken.Awaken.LOG.info("[delve] mouth way: {}", line);
        c.getSource().sendSuccess(() -> Component.literal(line).withColor(0x9FE6D6), false);
        return 1;
    }

    /** Underground air reached from the joint, not counting what the place carved itself; stops at 4,000 cells. */
    private static int caveBeyond(ServerLevel level, Grid built, int[] goal) {
        BlockPos from = new BlockPos(goal[0], goal[1], goal[2]);
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
        q.add(from);
        seen.add(from);
        int cave = 0;
        while (!q.isEmpty() && cave < 4000) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || Math.abs(n.getX() - goal[0]) > 64 || Math.abs(n.getZ() - goal[2]) > 64 || !level.getBlockState(n).isAir()) continue;
                if (n.getY() >= level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, n.getX(), n.getZ())) continue;
                boolean own = built.inside(n.getX(), n.getY(), n.getZ()) && built.get(n.getX(), n.getY(), n.getZ()).isAir();
                // the bore is walked only at the joint, or the search would climb back up it
                if (own && n.distManhattan(from) > 4) continue;
                seen.add(n);
                q.add(n);
                if (!own) cave++;
            }
        }
        return cave;
    }

    /**
     * Whether the world really holds this place, dry: its goal's chunk generated, the place's piece there and no water
     * in the goal (a tour must never stand in a flooded or phantom place; each miss is logged, it says the site search
     * and the world disagree).
     */
    private static boolean real(ServerLevel level, Spec s) {
        Grid g = CaveTests.grid(s);
        s.build(g);
        int[] m = g.mark("goal");
        if (m == null) return true;
        BlockPos goal = new BlockPos(m[0], m[1], m[2]);
        level.getChunk(goal.getX() >> 4, goal.getZ() >> 4);
        Spec here = CavePlaces.at(level, goal);
        boolean there = here != null && here.id() == s.id();
        boolean open = level.getBlockState(goal).getCollisionShape(level, goal).isEmpty();
        boolean dry = level.getFluidState(goal).isEmpty() && level.getFluidState(goal.above()).isEmpty();
        if (!there || !open || !dry) dev.awaken.Awaken.LOG.warn("[delve] goto passes {} at {} {} {}: {}", s.kind().id, s.x(), s.y(), s.z(),
                !there ? "the world has no such place there" : !open ? "its goal is solid rock (not built)" : "its goal is under water");
        return there && open && dry;
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

    /** {@code /awaken delve mouths <radius>}: the mouth census round you, on this world's own terrain, nothing generated. */
    private static int mouths(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String line = CaveLocate.census(p.level(), p.blockPosition(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"));
        c.getSource().sendSuccess(() -> Component.literal(line).withColor(0x9FE6D6), false);
        return 1;
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
