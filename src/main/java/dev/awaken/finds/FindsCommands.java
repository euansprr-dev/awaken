package dev.awaken.finds;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /awaken finds ...} for game masters and tours (SPEC §58.9): {@code place <kind>} stands one up on a level pad
 * where you are; {@code gallery [set]} stands every kind (or one set) in a row running east from you, each on its own
 * pad; {@code near} lists the world's own finds within 400 blocks; {@code locate <kind>} looks for the nearest.
 */
final class FindsCommands {
    private FindsCommands() {}

    static void init() {
        AwakenCommands.add((root, ctx) -> {
            var f = Commands.literal("finds").requires(AwakenCommands.admin());
            f.then(Commands.literal("place").then(Commands.argument("kind", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Kinds.all().stream().map(Kind::id), b)).executes(FindsCommands::place)));
            f.then(Commands.literal("gallery").executes(c -> gallery(c, ""))
                    .then(Commands.argument("set", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Kind.FSet.values()).map(s -> s.name().toLowerCase(java.util.Locale.ROOT)), b))
                            .executes(c -> gallery(c, StringArgumentType.getString(c, "set")))));
            f.then(Commands.literal("near").executes(FindsCommands::near));
            f.then(Commands.literal("view").then(Commands.argument("kind", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Kinds.places().stream().map(Kind::id), b))
                    .then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 9)).executes(FindsCommands::view))));
            f.then(Commands.literal("grant").then(Commands.argument("kind", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Kinds.all().stream().map(Kind::id), b)).executes(FindsCommands::grant)));
            f.then(Commands.literal("visit").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99)).executes(FindsCommands::visit)));
            f.then(Commands.literal("locate").then(Commands.argument("kind", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Kinds.all().stream().map(Kind::id), b)).executes(FindsCommands::locate)));
            root.then(f);
        });
    }

    private static int place(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Kind k = Kinds.byId(StringArgumentType.getString(c, "kind"));
        if (k == null) {
            c.getSource().sendFailure(Component.literal("No find is called that."));
            return 0;
        }
        // where and which way the command stands, so "execute positioned ... rotated ..." can stand one anywhere loaded
        ServerLevel level = c.getSource().getLevel();
        BlockPos at = BlockPos.containing(c.getSource().getPosition()).below();
        int rot = net.minecraft.core.Direction.fromYRot(c.getSource().getRotation().y).get2DDataValue();
        Site s = FindsFeature.stamp(level, k, at, rot, level.getRandom().nextLong());
        c.getSource().sendSuccess(() -> Component.literal(k.name() + " at " + s.x() + " " + s.y() + " " + s.z()), false);
        return 1;
    }

    private static int gallery(CommandContext<CommandSourceStack> c, String set) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos at = p.blockPosition().below();
        int x = at.getX() + 6, n = 0;
        for (Kind k : Kinds.all()) {
            if (!set.isEmpty() && !k.set().name().equalsIgnoreCase(set)) continue;
            int r = k.reach() + 4;
            x += r;
            FindsFeature.stamp(level, k, new BlockPos(x, at.getY(), at.getZ()), 0, 1000L + n * 7919L);
            x += r;
            n++;
        }
        int count = n, end = x;
        c.getSource().sendSuccess(() -> Component.literal(count + " finds stood east of you, as far as x " + end), false);
        return n;
    }

    private static final java.util.Map<String, Site> VIEWED = new java.util.HashMap<>();

    /**
     * Takes you to the nearest place of a kind that the world itself made and stands you at one of its cameras (0 is from
     * afar; a place has four). Looks out to 12,000 blocks from the spawn. For checking places in their own land.
     */
    private static int view(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        String id = StringArgumentType.getString(c, "kind");
        int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n");
        Site s = VIEWED.get(id);
        if (s == null) {
            BlockPos from = level.getRespawnData().pos();
            for (int r = 3000; r <= 12000 && s == null; r += 3000) {
                double best = Double.MAX_VALUE;
                for (Site o : Sites.near(level, from.getX() - r, from.getZ() - r, from.getX() + r, from.getZ() + r, 0)) {
                    double d = o.heart().distSqr(from);
                    if (o.kind().id().equals(id) && d < best) {
                        best = d;
                        s = o;
                    }
                }
            }
            if (s == null) {
                Awaken.LOG.info("[finds] view {}: none within 12000 of the spawn", id);
                c.getSource().sendFailure(Component.literal("No " + id + " within 12,000 blocks of the spawn."));
                return 0;
            }
            VIEWED.put(id, s);
        }
        var cams = Sites.scene(level, s).cams();
        int[] cam = cams.isEmpty() ? new int[] {0, 14, -(s.kind().reach() + 6), 0, 25} : cams.get(Math.min(n, cams.size() - 1));
        BlockPos stand = new BlockPos(s.x() + cam[0], s.y() + cam[1], s.z() + cam[2]);
        level.getChunk(stand); // the land has to be there before the unseen block is set under the camera
        if (level.getBlockState(stand.below()).isAir()) level.setBlock(stand.below(), net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
        p.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), (float) cam[3], (float) cam[4], false);
        Site at = s;
        Awaken.LOG.info("[finds] view {} {}: at {} {} {} ({}, rank {})", id, n, at.x(), at.y(), at.z(), at.group(), at.rank());
        c.getSource().sendSuccess(() -> Component.literal(at.kind().name() + " at " + at.x() + " " + at.y() + " " + at.z()), false);
        return 1;
    }

    /** Files a kind in your Journal as done, for checking the Journal's pages: a kind's id, a set's name, or "all". */
    private static int grant(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "kind");
        int n = 0;
        for (Kind k : Kinds.all()) {
            if (!id.equals("all") && !k.id().equals(id) && !k.set().name().equalsIgnoreCase(id)) continue;
            p.setData(FindsContent.FINDS, p.getData(FindsContent.FINDS).withDone(k.id(), k.id() + "@" + (p.getBlockX() + n) + "," + p.getBlockZ()));
            n++;
        }
        int count = n;
        c.getSource().sendSuccess(() -> Component.literal(count + " kinds filed as done."), false);
        return n;
    }

    /** Takes you to the n-th nearest find the world itself made (0 is the nearest), set down a little north of it and above, looking at it. */
    private static int visit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        BlockPos from = level.getRespawnData().pos();
        var sites = new java.util.ArrayList<>(Sites.near(level, from.getX() - 600, from.getZ() - 600, from.getX() + 600, from.getZ() + 600, 0));
        sites.sort(java.util.Comparator.comparingDouble(s -> s.heart().distSqr(from)));
        int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n");
        if (n >= sites.size()) {
            c.getSource().sendFailure(Component.literal("Only " + sites.size() + " finds within 600 blocks of the spawn."));
            return 0;
        }
        Site s = sites.get(n);
        // a little off to one side and above head height, on an unseen block so the view holds still (a check command: it leaves that block)
        int back = Math.min(s.kind().reach(), 10) + 5;
        BlockPos stand = new BlockPos(s.x() + 3, s.y() + 3 + back / 3, s.z() - back);
        if (level.getBlockState(stand.below()).isAir()) level.setBlock(stand.below(), net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState(), 3);
        level.setBlock(stand, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(stand.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        float pitch = (float) Math.toDegrees(Math.atan2(3 + back / 3.0, back)) + 4;
        p.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, java.util.Set.of(), (float) -Math.toDegrees(Math.atan2(-3, back)), pitch, false);
        c.getSource().sendSuccess(() -> Component.literal(s.kind().name() + " (" + s.kind().id() + ") at " + s.x() + " " + s.y() + " " + s.z()), true);
        Awaken.LOG.info("[finds] visit {}: {} at {} {} {}", n, s.kind().id(), s.x(), s.y(), s.z());
        return 1;
    }

    private static int near(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        var sites = Sites.near(level, p.getBlockX() - 400, p.getBlockZ() - 400, p.getBlockX() + 400, p.getBlockZ() + 400, 0);
        StringBuilder sb = new StringBuilder(sites.size() + " finds within 400 blocks:");
        for (Site s : sites) sb.append("\n  ").append(s.kind().name()).append(" at ").append(s.x()).append(' ').append(s.y()).append(' ').append(s.z());
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return sites.size();
    }

    private static int locate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = (ServerLevel) p.level();
        Kind k = Kinds.byId(StringArgumentType.getString(c, "kind"));
        if (k == null) {
            c.getSource().sendFailure(Component.literal("No find is called that."));
            return 0;
        }
        Site best = null;
        long bestD = Long.MAX_VALUE;
        for (int ring = 0; ring <= 6 && best == null; ring++) {
            int r = 600 * (ring + 1);
            for (Site s : Sites.near(level, p.getBlockX() - r, p.getBlockZ() - r, p.getBlockX() + r, p.getBlockZ() + r, 0)) {
                if (s.kind() != k) continue;
                long dx = s.x() - p.getBlockX(), dz = s.z() - p.getBlockZ(), d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
        }
        if (best == null) {
            c.getSource().sendFailure(Component.literal("None within 4,200 blocks."));
            return 0;
        }
        Site s = best;
        c.getSource().sendSuccess(() -> Component.literal(k.name() + " at " + s.x() + " " + s.y() + " " + s.z()), false);
        return 1;
    }
}
