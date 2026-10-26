package dev.awaken.body.ascent;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /awaken ascent} (game masters, Phase 0): {@code grant <all|none|tether|wings|skyborn>} sets the tiers you
 * hold, {@code sandbox} grows a grove of giant trees and a launch tower in front of you and puts you on the tower,
 * {@code pilot <line>} runs a line of the tour pilot on your client (scripted input: the autopilot can't hold keys).
 */
final class AscentCommands {
    private AscentCommands() {}

    private static final String[] GRANTS = {"all", "none", "tether", "wings", "skyborn"};

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var ascent = Commands.literal("ascent").requires(AwakenCommands.admin()).executes(c -> show(c.getSource()));
            ascent.then(Commands.literal("grant").then(Commands.argument("what", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(GRANTS, b))
                    .executes(c -> grant(c.getSource(), StringArgumentType.getString(c, "what")))));
            ascent.then(Commands.literal("sandbox").executes(c -> sandbox(c.getSource())));
            // replays "your body remembers" for the tiers held (Wings if none): for tours and the clip recorder
            ascent.then(Commands.literal("remember").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                int bits = AscentServer.tiers(p);
                AwakenNet.send(p, new AscentPayloads.Remember(bits != 0 ? bits : 0b111, AscentPayloads.REMEMBER));
                return 1;
            }));
            // the Tether Vow at the world's spawn vault: there, wait for its chunks, then stand in its forecourt (tours)
            ascent.then(Commands.literal("vow").then(Commands.literal("goto").executes(c -> vowGoto(c.getSource(), false))));
            // the same, with the vault's sealed door opened first (the vow needs it open: tours at C-rank)
            ascent.then(Commands.literal("vow").then(Commands.literal("open").executes(c -> vowGoto(c.getSource(), true))));
            // the Leaper's Gauntlet, opened in front of you now whatever you hold (tours)
            ascent.then(Commands.literal("trial").then(Commands.literal("leaper").executes(c -> {
                int id = AscentTrials.openLeaper(c.getSource().getPlayerOrException());
                if (id < 0) c.getSource().sendFailure(Component.literal("No trial gate could form here"));
                return Math.max(0, id);
            })));
            // the Sky Leviathan's trial, opened in front of you now whatever you hold (tours)
            ascent.then(Commands.literal("trial").then(Commands.literal("skyborn").executes(c -> {
                int id = AscentTrials.openSkyborn(c.getSource().getPlayerOrException());
                if (id < 0) c.getSource().sendFailure(Component.literal("No trial gate could form here"));
                return Math.max(0, id);
            })));
            // the nearest Tether Spire: you on its plaza beside the column (tours)
            ascent.then(Commands.literal("spire").then(Commands.literal("goto").executes(c -> spireGoto(c.getSource()))));
            // the nearest sky platform: rigged if it isn't yet, and you under its first ring (tours)
            ascent.then(Commands.literal("sky").then(Commands.literal("goto").executes(c -> skyGoto(c.getSource()))));
            ascent.then(Commands.literal("crown").then(Commands.literal("grow")
                    .then(Commands.literal("colossus").executes(c -> crownGrow(c.getSource(), dev.awaken.terrain.hero.HeroTree.ELDER_COLOSSUS, 7919L))
                            .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                    .executes(c -> crownGrow(c.getSource(), dev.awaken.terrain.hero.HeroTree.ELDER_COLOSSUS,
                                            com.mojang.brigadier.arguments.LongArgumentType.getLong(c, "seed")))))
                    .then(Commands.literal("lumen").executes(c -> crownGrow(c.getSource(), dev.awaken.terrain.hero.HeroTree.LUMEN_SPIRE, 7919L))
                            .then(Commands.argument("seed", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                    .executes(c -> crownGrow(c.getSource(), dev.awaken.terrain.hero.HeroTree.LUMEN_SPIRE,
                                            com.mojang.brigadier.arguments.LongArgumentType.getLong(c, "seed")))))));
            ascent.then(Commands.literal("windway").executes(c -> windwayInfo(c.getSource()))
                    .then(Commands.literal("goto").executes(c -> windwayGoto(c.getSource()))));
            ascent.then(Commands.literal("pilot").then(Commands.argument("line", StringArgumentType.greedyString())
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        AwakenNet.send(p, new AscentPayloads.Pilot(StringArgumentType.getString(c, "line")));
                        return 1;
                    })));
            root.then(ascent);
        });
    }

    private static int show(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int t = AscentServer.tiers(p);
        StringBuilder sb = new StringBuilder();
        for (AscentPayloads.Tier tier : AscentPayloads.Tier.values()) if (tier.in(t)) sb.append(sb.isEmpty() ? "" : ", ").append(tier.name().toLowerCase());
        src.sendSuccess(() -> Component.literal("Ascent tiers: " + (sb.isEmpty() ? "none (Runner)" : sb)).withStyle(ChatFormatting.AQUA), false);
        return t;
    }

    private static int skyGoto(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = src.getServer().overworld();
        // vanilla's own search: only starts that really generated
        var sky = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE, dev.awaken.Awaken.id("precursor_sky_platform")));
        if (sky.isEmpty()) {
            src.sendFailure(Component.literal("No sky platform structure"));
            return 0;
        }
        var near = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(sky.get()),
                p.blockPosition(), 100, false);
        if (near == null) {
            src.sendFailure(Component.literal("No sky platform within 1600 blocks"));
            return 0;
        }
        var at = near.getFirst();
        int chx = at.getX() >> 4, chz = at.getZ() >> 4;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) level.getChunk(chx + x, chz + z);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            for (var start : level.getChunk(chx + x, chz + z).getAllStarts().entrySet()) {
                if (!(start.getKey() instanceof dev.awaken.above.ruin.PrecursorRuinStructure prs) || prs.kind() != dev.awaken.above.ruin.RuinKind.SKY) continue;
                var box = start.getValue().getPieces().getFirst().getBoundingBox();
                int cx = box.minX() + 24, cz = box.minZ() + 24, deckY = box.minY() + 12;
                if (!level.getBlockState(new net.minecraft.core.BlockPos(cx, deckY + 1, cz)).is(AscentContent.WING_CORE.get())) {
                    dev.awaken.above.ruin.SkyRig.rig(level, cx, deckY, cz);
                }
                var l = dev.awaken.above.ruin.SkyRig.layout(cx, deckY, cz, dev.awaken.above.ruin.SkyRig.groundUnder(level, cx, cz));
                var first = l.rings().getLast();
                int fg = dev.awaken.above.ruin.SkyRig.groundUnder(level, cx, cz);
                p.teleportTo(level, first.getX() + 0.5, fg, first.getZ() + 0.5, java.util.Set.of(), 0, -80, true);
                dev.awaken.Awaken.LOG.info("[ascent] sky goto: the platform at {}, {} (deck {}), under its first ring {}", cx, cz, deckY,
                        first.toShortString());
                return 1;
            }
        }
        src.sendFailure(Component.literal("Found a platform near " + at.toShortString() + " but no start in its chunks"));
        return 0;
    }

    /**
     * Grows a hero tree where you stand (as /awaken terrain hero does), crowns it at once with its ring route, and stands
     * you on the ground past its roots looking up at it.
     */
    private static int crownGrow(CommandSourceStack src, dev.awaken.terrain.hero.HeroTree tree, long seed) {
        net.minecraft.server.level.ServerLevel level = src.getLevel();
        var at = net.minecraft.core.BlockPos.containing(src.getPosition());
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        var centre = new net.minecraft.core.BlockPos(at.getX(), y, at.getZ());
        var piece = new dev.awaken.terrain.hero.HeroPiece(tree, centre, seed);
        piece.build(level, piece.getBoundingBox());
        HeroCrowns.Layout l = HeroCrowns.layout(tree, dev.awaken.terrain.hero.HeroWorldgen.blueprint(tree, seed), seed);
        HeroCrowns.rig(level, tree, centre, seed, l);
        var crown = centre.offset(l.crown());
        // out of the trunk: on the ground just past the tree's reach to the south, looking up at it
        if (src.getEntity() instanceof ServerPlayer p) {
            int z = piece.getBoundingBox().maxZ() + 3;
            int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), z);
            p.teleportTo(level, centre.getX() + 0.5, gy, z + 0.5, java.util.Set.of(), 180, -35, true);
        }
        src.sendSuccess(() -> Component.literal(String.format("Grew and crowned %s (seed %d): crown at %s, %d rings", tree.getSerializedName(), seed,
                crown.toShortString(), l.rings().size())).withStyle(ChatFormatting.AQUA), false);
        return l.rings().size();
    }

    /** The windways the server knows, and the nearest to you. */
    private static int windwayInfo(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        WindwaysServer.scan(p, src.getServer().overworld().getGameTime(), 64);
        var ways = Windways.serverWays();
        Windways.Way near = null;
        for (Windways.Way w : ways) if (near == null || w.reach(p.getX(), p.getZ()) < near.reach(p.getX(), p.getZ())) near = w;
        Windways.Way n = near;
        src.sendSuccess(() -> Component.literal(ways.size() + " windway lanes known" + (n == null ? "" : String.format(
                "; the nearest %.0f blocks off, %.0f long, %s", n.reach(p.getX(), p.getZ()), n.length(),
                Windways.holds(n, p.getX(), p.getY(), p.getZ()) ? "and you're in it" : "from " + (int) n.ax() + ", " + (int) n.ay() + ", " + (int) n.az())))
                .withStyle(ChatFormatting.AQUA), false);
        return ways.size();
    }

    /**
     * Onto the deck of the platform the nearest windway starts from, at its rim on the lane's side, facing along it (a
     * run off the edge and open wings drop into it). Checks the deck's height against the seed's prediction.
     */
    private static int windwayGoto(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = src.getServer().overworld();
        WindwaysServer.scan(p, level.getGameTime(), 64);
        Windways.Way best = null;
        double bd = Double.MAX_VALUE;
        for (Windways.Way w : Windways.serverWays()) {
            double d = Math.hypot(w.ax() - p.getX(), w.az() - p.getZ());
            if (d < bd) {
                bd = d;
                best = w;
            }
        }
        if (best == null) {
            src.sendFailure(Component.literal("No windway within " + WindwaysServer.LOOK + " blocks (no two sky platforms within "
                    + Windways.MAX_LINK + " of each other yet: run it again, the search finishes over a few calls)"));
            return 0;
        }
        double hl = Math.hypot(best.bx() - best.ax(), best.bz() - best.az()), ux = (best.bx() - best.ax()) / hl, uz = (best.bz() - best.az()) / hl;
        double rx = -uz, rz = ux;
        int cx = (int) Math.floor(best.ax() - ux * Windways.OUT - rx * Windways.LANE);
        int cz = (int) Math.floor(best.az() - uz * Windways.OUT - rz * Windways.LANE);
        int deck = (int) Math.round(best.ay()) + Windways.BELOW;
        level.getChunk(cx >> 4, cz >> 4);
        level.getChunk((cx + 11) >> 4, cz >> 4);
        boolean confirmed = level.getBlockState(new net.minecraft.core.BlockPos(cx + 11, deck, cz)).is(net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE)
                && level.getBlockState(new net.minecraft.core.BlockPos(cx + 11, deck - 1, cz)).isAir();
        double x = cx + 0.5 + ux * 6 + rx * 8, z = cz + 0.5 + uz * 6 + rz * 8;
        float yaw = (float) Math.toDegrees(Math.atan2(-ux, uz));
        p.teleportTo(level, x, deck + 1, z, java.util.Set.of(), yaw, 0, true);
        // the lanes round where they are now
        WindwaysServer.forget(p.getUUID());
        WindwaysServer.scan(p, level.getGameTime(), 64);
        dev.awaken.Awaken.LOG.info("[ascent] windway goto: from the platform at {}, {} (deck {} predicted, {}), {} long, to {}, {}, {}",
                cx, cz, deck, confirmed ? "confirmed" : "NOT where predicted", (int) best.length(), (int) best.bx(), (int) best.by(), (int) best.bz());
        Windways.Way b = best;
        src.sendSuccess(() -> Component.literal(String.format("On the deck at %d, %d: the windway runs %.0f blocks from here%s", cx, cz, b.length(),
                confirmed ? "" : " (the deck isn't where the seed says!)")).withStyle(confirmed ? ChatFormatting.AQUA : ChatFormatting.RED), false);
        return confirmed ? 1 : 0;
    }

    private static int spireGoto(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = src.getServer().overworld();
        var spire = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .get(dev.awaken.above.ruin.RuinKind.SPIRE.structure);
        if (spire.isEmpty()) {
            src.sendFailure(Component.literal("No Tether Spire structure"));
            return 0;
        }
        var near = level.getChunkSource().getGenerator().findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(spire.get()),
                p.blockPosition(), 100, false);
        if (near == null) {
            src.sendFailure(Component.literal("No Tether Spire within 1600 blocks"));
            return 0;
        }
        var at = near.getFirst();
        int chx = at.getX() >> 4, chz = at.getZ() >> 4;
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.getChunk(chx + x, chz + z);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            for (var start : level.getChunk(chx + x, chz + z).getAllStarts().entrySet()) {
                if (!(start.getKey() instanceof dev.awaken.above.ruin.PrecursorRuinStructure prs) || prs.kind() != dev.awaken.above.ruin.RuinKind.SPIRE) continue;
                var box = start.getValue().getPieces().getFirst().getBoundingBox();
                int cx = box.minX() + dev.awaken.above.ruin.RuinBuilder.SPIRE_C, cz = box.minZ() + dev.awaken.above.ruin.RuinBuilder.SPIRE_C;
                int feet = box.minY() + dev.awaken.above.ruin.RuinBuilder.SPIRE_PLAZA + 1;
                // under the first platform (the piece may be turned: find its lit heart, 5 out from the column)
                int py = box.minY() + dev.awaken.above.ruin.RuinBuilder.spirePetal(0)[1];
                double ux = cx + 4.5, uz = cz + 0.5;
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) < 4) continue;
                    if (level.getBlockState(new net.minecraft.core.BlockPos(cx + dx, py, cz + dz)).is(dev.awaken.above.AboveBlocks.GLOW_PANEL.get())) {
                        ux = cx + dx + 0.5;
                        uz = cz + dz + 0.5;
                    }
                }
                p.teleportTo(level, ux, feet, uz, java.util.Set.of(), 90F, -30F, true);
                dev.awaken.Awaken.LOG.info("[ascent] spire goto: the spire at {}, {} (plaza {}, crown {}), box {}, column {} / {}, crown block {}", cx, cz, feet - 1,
                        box.minY() + dev.awaken.above.ruin.RuinBuilder.SPIRE_CROWN, box, level.getBlockState(new net.minecraft.core.BlockPos(cx, feet + 3, cz)),
                        level.getBlockState(new net.minecraft.core.BlockPos(cx, feet + 30, cz)),
                        level.getBlockState(new net.minecraft.core.BlockPos(cx, box.minY() + dev.awaken.above.ruin.RuinBuilder.SPIRE_CROWN + 1, cz)));
                return 1;
            }
        }
        src.sendFailure(Component.literal("Found a spire near " + at.toShortString() + " but no start in its chunks"));
        return 0;
    }

    private static int vowGoto(CommandSourceStack src, boolean open) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = src.getServer().overworld();
        var chunk = dev.awaken.above.ruin.RuinLocator.spawnVault(level);
        if (chunk == null) {
            src.sendFailure(Component.literal("No spawn vault in this world"));
            return 0;
        }
        // a dev tool: generate the vault's chunks now (the structure's references come with them)
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.getChunk(chunk.x() + x, chunk.z() + z);
        int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        for (int dy = -24; dy <= 12; dy += 2) {
            for (int dx = -24; dx <= 24; dx += 6) {
                for (int dz = -24; dz <= 24; dz += 6) {
                    TetherVow.Site site = TetherVow.siteAt(level, new net.minecraft.core.BlockPos(cx + dx, ground + dy, cz + dz));
                    if (site == null) continue;
                    if (open && !site.doorOpen(level)) dev.awaken.above.block.DoorCoreBlock.open(level, site.door());
                    net.minecraft.core.BlockPos f = site.world(11, 1, 2);
                    float yaw = switch (site.rot()) {
                        case 1 -> 90F;
                        case 2 -> 180F;
                        case 3 -> -90F;
                        default -> 0F;
                    };
                    p.teleportTo(level, f.getX() + 0.5, f.getY(), f.getZ() + 0.5, java.util.Set.of(), yaw, 0, true);
                    dev.awaken.Awaken.LOG.info("[ascent] vow goto: the spawn vault at {} (turn {})", site.box().getCenter().toShortString(), site.rot());
                    return 1;
                }
            }
        }
        dev.awaken.Awaken.LOG.warn("[ascent] vow goto: no vault found round {}, {} (ground {})", cx, cz, ground);
        // what is there: every structure start the chunks round it know, and how the vow reads each vault
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            var ch = level.getChunk(chunk.x() + x, chunk.z() + z);
            for (var e : ch.getAllStarts().entrySet()) {
                for (var piece : e.getValue().getPieces()) {
                    var box = piece.getBoundingBox();
                    var c = box.getCenter();
                    dev.awaken.Awaken.LOG.warn("[ascent] vow goto: {} piece box {} turn {}; site at its centre {}", level.registryAccess()
                                    .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(e.getKey()), box,
                            TetherVow.rotationOf(level, box), TetherVow.siteAt(level, c));
                }
            }
        }
        src.sendFailure(Component.literal("No vault found near the spawn vault's chunk"));
        return 0;
    }

    private static int grant(CommandSourceStack src, String what) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int bits = switch (what) {
            case "all" -> AscentPayloads.Tier.TETHER.bit() | AscentPayloads.Tier.LEAPER.bit() | AscentPayloads.Tier.WINGS.bit()
                    | AscentPayloads.Tier.SKYBORN.bit();
            case "none" -> 0;
            case "tether" -> AscentPayloads.Tier.TETHER.bit();
            case "wings" -> AscentPayloads.Tier.TETHER.bit() | AscentPayloads.Tier.LEAPER.bit() | AscentPayloads.Tier.WINGS.bit();
            case "skyborn" -> AscentPayloads.Tier.TETHER.bit() | AscentPayloads.Tier.LEAPER.bit() | AscentPayloads.Tier.WINGS.bit()
                    | AscentPayloads.Tier.SKYBORN.bit();
            // the story's end's gift (SPEC §17.19): every tier
            case "sovereign" -> AscentPayloads.Tier.TETHER.bit() | AscentPayloads.Tier.LEAPER.bit() | AscentPayloads.Tier.WINGS.bit()
                    | AscentPayloads.Tier.SKYBORN.bit() | AscentPayloads.Tier.SOVEREIGN.bit();
            default -> -1;
        };
        if (bits < 0) {
            src.sendFailure(Component.literal("grant all, none, tether, wings, skyborn or sovereign"));
            return 0;
        }
        AscentServer.setTiers(p, bits);
        return show(src);
    }

    private static int sandbox(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (AscentServer.tiers(p) == 0) grant(src, "all");
        int n = AscentSandbox.build(p);
        src.sendSuccess(() -> Component.literal("Sandbox: " + n + " giant trees ahead. Hold Left Alt (Tether) at a trunk or branch "
                + "to swing, W at the bottom to pump, let go to fly, Space to jump off the rope. Space in the air opens the wings; "
                + "Space again while gliding takes flight (Ctrl to boost).").withStyle(ChatFormatting.AQUA), false);
        return n;
    }
}
