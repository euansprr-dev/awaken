package dev.awaken.terrain;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import net.minecraft.util.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /awaken terrain ...}, the terrain studio (game masters only): {@code survey <name> [radius] [step]} samples the
 * land around you (from the server console: round the world spawn) into {@code terrain/<name>.json} for
 * tools/terrain/survey.py; {@code visit <biome|#tag>} stands you in
 * the heart of the nearest surface patch of that biome; {@code lookout [radius]} takes you to the highest ground nearby;
 * {@code at} prints the biome, ground height and climate where you stand; {@code city} goes to the city site;
 * {@code hero <tree> [seed]} grows a hero tree where you stand and {@code herovisit <tree> [distance above]} stands you
 * before the nearest generated one. The searches run off the main thread and
 * log {@code [terrain]} lines the autopilot can wait on.
 */
final class TerrainCommands {
    private TerrainCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var t = Commands.literal("terrain").requires(AwakenCommands.admin());
            t.then(Commands.literal("survey").then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> survey(c, 2048, 16))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(128, 8192))
                            .executes(c -> survey(c, IntegerArgumentType.getInteger(c, "radius"), 16))
                            .then(Commands.argument("step", IntegerArgumentType.integer(4, 256))
                                    .executes(c -> survey(c, IntegerArgumentType.getInteger(c, "radius"), IntegerArgumentType.getInteger(c, "step")))))));
            t.then(Commands.literal("visit").then(Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(ctx, Registries.BIOME))
                    .executes(TerrainCommands::visit)));
            t.then(Commands.literal("lookout")
                    .executes(c -> lookout(c, 256))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(16, 2048))
                            .executes(c -> lookout(c, IntegerArgumentType.getInteger(c, "radius")))));
            t.then(Commands.literal("at").executes(TerrainCommands::at));
            for (var tree : dev.awaken.terrain.hero.HeroTree.values()) {
                t.then(Commands.literal("hero").then(Commands.literal(tree.getSerializedName())
                        .executes(c -> hero(c, tree, c.getSource().getLevel().getRandom().nextLong()))
                        .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                .executes(c -> hero(c, tree, com.mojang.brigadier.arguments.LongArgumentType.getLong(c, "seed"))))));
                t.then(Commands.literal("herovisit").then(Commands.literal(tree.getSerializedName())
                        .executes(c -> heroVisit(c, tree, 80, 2))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(0, 512))
                                .then(Commands.argument("above", IntegerArgumentType.integer(0, 256))
                                        .executes(c -> heroVisit(c, tree, IntegerArgumentType.getInteger(c, "distance"),
                                                IntegerArgumentType.getInteger(c, "above")))))));
            }
            t.then(Commands.literal("city").executes(c -> {
                // the city site (Carillon's basin): print it and stand at its centre
                ServerPlayer sp = c.getSource().getPlayerOrException();
                var site = dev.awaken.terrain.api.CityBasin.site(c.getSource().getServer()).orElse(null);
                if (site == null) {
                    c.getSource().sendFailure(Component.literal("No city site in this world"));
                    return 0;
                }
                ServerLevel level = c.getSource().getServer().overworld();
                stand(sp, level, site.x(), site.z(), 1, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
                Awaken.LOG.info("[terrain] city site {} {} street {} carved {}", site.x(), site.z(), site.streetY(), site.basinCarved());
                c.getSource().sendSuccess(() -> Component.literal("City site " + site.x() + ", " + site.z() + " (street " + site.streetY()
                        + (site.basinCarved() ? ", basin carved)" : ", basin not carved)")), false);
                return 1;
            }));
            root.then(t);
        });
    }

    /** Grows a hero tree with its foot where you stand (for looking at it; worldgen places them by itself). */
    private static int hero(CommandContext<CommandSourceStack> c, dev.awaken.terrain.hero.HeroTree tree, long seed) {
        ServerLevel level = c.getSource().getLevel();
        var at = net.minecraft.core.BlockPos.containing(c.getSource().getPosition());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        long t0 = System.nanoTime();
        var piece = new dev.awaken.terrain.hero.HeroPiece(tree, new net.minecraft.core.BlockPos(at.getX(), y, at.getZ()), seed);
        int placed = piece.build(level, piece.getBoundingBox());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Awaken.LOG.info("[terrain] hero {} seed {} at {} {} {}: {} blocks in {} ms", tree.getSerializedName(), seed, at.getX(), y, at.getZ(), placed, ms);
        c.getSource().sendSuccess(() -> Component.literal("Grew " + tree.getSerializedName() + " (seed " + seed + "): " + placed + " blocks"), false);
        return placed;
    }

    /**
     * Finds the nearest hero tree of that kind the world generated and stands you {@code distance} blocks south of its
     * trunk, {@code above} blocks over the ground there, looking at it (for screenshots: {@code [terrain] herovisit}).
     */
    private static int heroVisit(CommandContext<CommandSourceStack> c, dev.awaken.terrain.hero.HeroTree tree, int distance, int above)
            throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level().getServer().overworld();
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(dev.awaken.terrain.hero.HeroWorldgen.structure(tree));
        if (structure.isEmpty()) {
            Awaken.LOG.info("[terrain] herovisit {} none (no lands pack)", tree.getSerializedName());
            c.getSource().sendFailure(Component.literal("This world has no " + tree.getSerializedName()));
            return 0;
        }
        var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(structure.get()),
                sp.blockPosition(), 100, false);
        if (found == null) {
            Awaken.LOG.info("[terrain] herovisit {} none within 1600 blocks", tree.getSerializedName());
            c.getSource().sendFailure(Component.literal("No " + tree.getSerializedName() + " nearby"));
            return 0;
        }
        var start = found.getFirst();
        var chunk = level.getChunk(start.getX() >> 4, start.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
        var st = chunk.getStartForStructure(structure.get().value());
        net.minecraft.core.BlockPos base = start;
        int height = 40;
        if (st != null) {
            for (var piece : st.getPieces()) {
                if (piece instanceof dev.awaken.terrain.hero.HeroPiece hp) {
                    base = hp.centre();
                    height = hp.getBoundingBox().maxY() - base.getY();
                }
            }
        }
        net.minecraft.core.BlockPos foot = base;
        int top = height, vx = foot.getX(), vz = foot.getZ() + distance;
        // there once its land is made (SPEC §28.9, "Arrivals")
        dev.awaken.core.api.Passage.go(sp, level, vx + 0.5, vz + 0.5, () -> {
            // the ground there, under any tree (a limb or a root crest is not the ground)
            int vy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, vx, vz);
            while (vy > level.getMinY() + 1) {
                var below = level.getBlockState(new net.minecraft.core.BlockPos(vx, vy - 1, vz));
                if (!below.is(net.minecraft.tags.BlockTags.LOGS) && !below.is(net.minecraft.tags.BlockTags.LEAVES) && !below.canBeReplaced()) break;
                vy--;
            }
            vy = Math.max(vy, level.getSeaLevel() + 1) + above; // over the sea: from its surface, not its floor
            double aim = foot.getY() + top * 0.42 - (vy + 1.62);
            float pitch = (float) -Math.toDegrees(Math.atan2(aim, Math.max(4, distance)));
            sp.teleportTo(level, vx + 0.5, vy + 0.1, vz + 0.5, Set.<Relative>of(), 180.0F, pitch, true);
            Awaken.LOG.info("[terrain] herovisit {} foot {} {} {} top +{}, viewed from {} {} {}", tree.getSerializedName(), foot.getX(), foot.getY(),
                    foot.getZ(), top, vx, vy, vz);
            // light beside the trunk's south face, low to high (worldgen lighting check)
            StringBuilder light = new StringBuilder();
            for (int k = 4; k <= top; k += 8) {
                for (int d = 3; d <= 12; d++) {
                    var p = foot.offset(0, k, d);
                    if (!level.getBlockState(p).isAir()) continue;
                    light.append(String.format(" +%d:%d@%d", k, level.getBrightness(net.minecraft.world.level.LightLayer.SKY, p), d));
                    break;
                }
            }
            Awaken.LOG.info("[terrain] herovisit {} sky light south of the trunk:{}", tree.getSerializedName(), light);
            c.getSource().sendSuccess(() -> Component.literal(tree.getSerializedName() + " at " + foot.toShortString()), false);
        });
        return 1;
    }

    private static int survey(CommandContext<CommandSourceStack> c, int radius, int step) {
        // where the command runs: a player where they stand, the server console at the world spawn (a headless survey)
        String name = StringArgumentType.getString(c, "name");
        ServerLevel level = c.getSource().getServer().overworld();
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int x = (int) Math.floor(c.getSource().getPosition().x), z = (int) Math.floor(c.getSource().getPosition().z);
        Path dir = level.getServer().getServerDirectory().resolve("terrain");
        c.getSource().sendSuccess(() -> Component.literal("Surveying " + name + "..."), false);
        CompletableFuture.supplyAsync(() -> {
            Survey.Grid g = Survey.sample(name, level.getSeed(), gen, rs, level, x, z, radius, step);
            try {
                return Survey.write(g, dir).toString() + " (" + g.size() + "² samples, " + g.millis() + " ms)";
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, Util.backgroundExecutor()).whenCompleteAsync((msg, err) -> {
            if (err != null) {
                Awaken.LOG.error("[terrain] survey {} failed", name, err);
                c.getSource().sendFailure(Component.literal("Survey failed: " + err.getMessage()));
            } else {
                Awaken.LOG.info("[terrain] survey {} done {}", name, msg);
                c.getSource().sendSuccess(() -> Component.literal("Survey " + name + ": " + msg), false);
            }
        }, level.getServer());
        return 1;
    }

    private static int visit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ResourceOrTagArgument.Result<net.minecraft.world.level.biome.Biome> want = ResourceOrTagArgument.getResourceOrTag(c, "biome", Registries.BIOME);
        ServerLevel level = sp.level().getServer().overworld();
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int x = sp.getBlockX(), z = sp.getBlockZ();
        String label = want.asPrintable();
        CompletableFuture.supplyAsync(() -> Survey.findHeart(gen, rs, level, want, x, z, 8192), Util.backgroundExecutor())
                .whenCompleteAsync((spot, err) -> {
                    if (err != null || spot == null) {
                        Awaken.LOG.info("[terrain] visit {} none", label);
                        c.getSource().sendFailure(Component.literal("No surface " + label + " within 8192 blocks"));
                        return;
                    }
                    stand(sp, level, spot.x(), spot.z(), 0, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
                    Awaken.LOG.info("[terrain] visit {} at {} {} {} ({})", label, spot.x(), spot.floor(), spot.z(), Survey.id(spot.biome()));
                    c.getSource().sendSuccess(() -> Component.literal("Visiting " + Survey.id(spot.biome()) + " at "
                            + spot.x() + ", " + spot.z()), false);
                }, level.getServer());
        return 1;
    }

    private static int lookout(CommandContext<CommandSourceStack> c, int radius) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level().getServer().overworld();
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        int x = sp.getBlockX(), z = sp.getBlockZ();
        CompletableFuture.supplyAsync(() -> Survey.lookout(gen, rs, level, x, z, radius), Util.backgroundExecutor())
                .whenCompleteAsync((spot, err) -> {
                    if (err != null || spot == null) {
                        Awaken.LOG.info("[terrain] lookout none");
                        c.getSource().sendFailure(Component.literal("No dry ground within " + radius + " blocks"));
                        return;
                    }
                    stand(sp, level, spot.x(), spot.z(), 2, Heightmap.Types.MOTION_BLOCKING);
                    Awaken.LOG.info("[terrain] lookout at {} {} {} ({})", spot.x(), spot.floor(), spot.z(), Survey.id(spot.biome()));
                    c.getSource().sendSuccess(() -> Component.literal("Lookout at " + spot.x() + ", " + spot.floor() + ", " + spot.z()), false);
                }, level.getServer());
        return 1;
    }

    private static int at(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerLevel level = sp.level().getServer().overworld();
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        RandomState rs = level.getChunkSource().randomState();
        Survey.Spot s = Survey.spot(gen, rs, level, gen.getBiomeSource().createCachingResolver(rs), sp.getBlockX(), sp.getBlockZ());
        Climate.TargetPoint p = rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED)
                .sample(QuartPos.fromBlock(sp.getBlockX()), QuartPos.fromBlock(Math.max(s.floor(), gen.getSeaLevel())), QuartPos.fromBlock(sp.getBlockZ()));
        String msg = String.format("%s ground %d · T %.2f H %.2f C %.2f E %.2f D %.2f W %.2f", Survey.id(s.biome()), s.floor(),
                Climate.unquantizeCoord(p.temperature()), Climate.unquantizeCoord(p.humidity()), Climate.unquantizeCoord(p.continentalness()),
                Climate.unquantizeCoord(p.erosion()), Climate.unquantizeCoord(p.depth()), Climate.unquantizeCoord(p.weirdness()));
        Awaken.LOG.info("[terrain] at {} {} {}", sp.getBlockX(), sp.getBlockZ(), msg);
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    /** Stands the player at (x, z) on the given surface (the ground under trees, or the treetops for a lookout). */
    private static void stand(ServerPlayer sp, ServerLevel level, int x, int z, int above, Heightmap.Types surface) {
        // there once its land is made (SPEC §28.9, "Arrivals")
        dev.awaken.core.api.Passage.go(sp, level, x + 0.5, z + 0.5, () -> {
            int y = level.getHeight(surface, x, z);
            // on the ground means under the trees: a trunk's top or a limb is no ground (the treetops are for a lookout)
            while (surface == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES && y > level.getMinY() + 1) {
                var below = level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z));
                if (!below.is(net.minecraft.tags.BlockTags.LOGS) && !below.is(net.minecraft.tags.BlockTags.LEAVES) && !below.canBeReplaced()) break;
                y--;
            }
            sp.teleportTo(level, x + 0.5, y + 0.1 + above, z + 0.5, Set.<Relative>of(), sp.getYRot(), sp.getXRot(), true);
        });
    }
}
