package dev.awaken.bells;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.Optional;

/**
 * Admin tools for the bells: {@code /awaken admin bells give <id> | tower <n> | song | roost | plans | wagon | belfry |
 * ilse | deep | page}. {@code tower} rings the first n bells home, sets a frame by the player's Hearth and hangs them all;
 * {@code plans} raises both Thatchback bell plans by the home; {@code wagon} brings the Registry's wagon now (for tours).
 */
final class BellCommands {
    private BellCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("bells")
                .then(Commands.literal("give").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(BellCatalog.ALL.stream().map(BellCatalog.Def::id), b)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            return Bells.ring(p, StringArgumentType.getString(c, "id"), "given by an admin") ? 1 : 0;
                        })))
                .then(Commands.literal("tower").then(Commands.argument("n", IntegerArgumentType.integer(1, 100)).executes(c ->
                        tower(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("song").executes(c -> BellTower.songNow(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("roost").executes(c -> roost(c.getSource().getPlayerOrException())))
                .then(Commands.literal("plans").executes(c -> plans(c.getSource().getPlayerOrException())))
                .then(Commands.literal("wagon").executes(c -> wagon(c.getSource().getPlayerOrException())))
                .then(Commands.literal("belfry").executes(c -> belfry(c.getSource().getPlayerOrException())))
                .then(Commands.literal("ilse").executes(c -> Bells.ilseNow(c.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("deep").executes(c -> {
                    BellWorld w = BellWorld.get(c.getSource().getServer());
                    if (w.deepAnswered < 0) w.deepAnswered = dev.awaken.core.PlayDays.current(c.getSource().getServer());
                    w.setDirty();
                    c.getSource().sendSuccess(() -> Component.literal("The Deep has answered (play-day " + w.deepAnswered + ")."), false);
                    return 1;
                }))
                .then(Commands.literal("page").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (var r : BellPage.build(p).tower()) p.sendSystemMessage(Component.literal(String.join(" | ", r)));
                    return 1;
                })))));
    }

    /** Sets both of the Great Belfry's pieces into the home's frame and raises it now (the tower first, if it isn't up). */
    private static int belfry(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        ServerLevel level = p.level().getServer().overworld();
        BellWorld.Home home = h.map(r -> BellWorld.get(level.getServer()).peek(r.pos)).orElse(null);
        if (h.isEmpty() || home == null || home.frame == null) {
            p.sendSystemMessage(Component.literal("No bell frame at home: /awaken admin bells tower 3 first."));
            return 0;
        }
        if (home.hung.size() < BellTower.GREAT) tower(p, BellTower.GREAT);
        home.clapper = home.relic = true;
        BellWorld.get(level.getServer()).setDirty();
        return GreatBelfry.build(level, h.get(), home) ? 1 : 0;
    }

    /** For tours: the Lampwing's old bell hung by your home now if it isn't, and you stood looking up at it. */
    private static int roost(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty()) {
            p.sendSystemMessage(Component.literal("No home: found one first (/awaken admin kin yard)."));
            return 0;
        }
        ServerLevel level = p.level().getServer().overworld();
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Home home = w.home(h.get().pos);
        if (home.roost == null || !level.getBlockState(home.roost).is(BellContent.FOUND_BELL.get())) {
            BlockPos at = FoundBells.hangRoost(level, h.get().pos.offset(FoundBells.ROOST_DX, 0, FoundBells.ROOST_DZ), FoundBells.ROOST_REACH, h.get().pos.asLong());
            if (at == null) {
                p.sendSystemMessage(Component.literal("Nowhere loaded to hang it."));
                return 0;
            }
            home.roost = at;
            home.roosted = true;
            w.setDirty();
        }
        BlockPos r = home.roost;
        p.teleportTo(level, r.getX() + 2.5, r.getY() - 1.2, r.getZ() + 2.5, java.util.Set.of(), 135F, -22F, true);
        return 1;
    }

    /**
     * For tours: a Belfry 8 east and 10 south of the Anchor and a Bell Tower 8 west (past the yard), each raised from its Work Plan on
     * cleared ground, arches toward the fire; the small bells in the bag hung on the Belfry's beams, a Great Bell on the
     * Tower's.
     */
    private static int plans(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty()) {
            p.sendSystemMessage(Component.literal("No home: found one first (/awaken admin kin yard)."));
            return 0;
        }
        ServerLevel level = p.level().getServer().overworld();
        BlockPos a = h.get().pos, belfry = a.offset(8, 0, 10), tower = a.offset(-8, 0, 10);
        for (BlockPos c : new BlockPos[]{belfry, tower}) {
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-3, -1, -3), c.offset(5, -1, 3))) level.setBlock(q, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-3, 0, -3), c.offset(5, 17, 3))) level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        boolean built = dev.awaken.kin.works.WorkPlans.raise(level, belfry, Direction.NORTH, "belfry")
                & dev.awaken.kin.works.WorkPlans.raise(level, tower, Direction.NORTH, "bell_tower");
        BellWorld.Home home = BellWorld.get(level.getServer()).home(a);
        BlockPos[] beams = {belfry.offset(1, 9, 0), belfry.offset(-1, 9, 0), belfry.offset(0, 9, 1), belfry.offset(0, 9, -1)};
        BlockPos[] greats = {tower.offset(1, 13, 0), tower.offset(-1, 13, 0)};
        int b = 0, g = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            BellCatalog.Def d = BellItem.def(stack);
            if (d == null || home.hung.contains(d.no())) continue;
            BlockPos at = Hanging.great(d) ? (g < greats.length ? greats[g++] : null) : (b < beams.length * 4 ? beams[b++ / 4] : null);
            if (at == null || !(level.getBlockEntity(at) instanceof HooksEntity be)) continue;
            int hook = Hanging.nearest(be, net.minecraft.world.phys.Vec3.atCenterOf(at), true);
            if (hook < 0) continue;
            Hanging.hang(level, at, p, stack, Hanging.hookPoint(level.getBlockState(at), hook).add(at.getX(), at.getY(), at.getZ()), true);
        }
        return built ? 1 : 0;
    }

    /** For tours: the Registry's wagon comes now, leaving every Great Bell it carries for the player in the yard. */
    private static int wagon(ServerPlayer p) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty()) return 0;
        BellBook b = Bells.book(p);
        long yesterday = dev.awaken.core.PlayDays.current(p.level().getServer()) - 1;
        b.wagon.replaceAll((no, day) -> yesterday);
        Bells.save(p, b);
        FoundBells.wagon(p, h.get());
        return 1;
    }

    /** Rings the first {@code n} bells (the built ones first) for the player, sets a frame at home if there's none, hangs them. */
    private static int tower(ServerPlayer p, int n) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty()) {
            p.sendSystemMessage(Component.literal("No home: found one first (/awaken admin kin yard)."));
            return 0;
        }
        // the Seed Bell only ever comes with the Great Belfry (/awaken admin bells belfry)
        int rung = Bells.count(p);
        for (BellCatalog.Def d : BellCatalog.ALL) if (rung < n && d.built() && d.no() != 0 && Bells.ring(p, d, "given by an admin")) rung++;
        // the Great Bells come now, not by tomorrow's wagon
        BellBook b = Bells.book(p);
        for (int no : b.wagon.keySet()) Bells.giveBack(p, BellItem.of(no));
        b.wagon.clear();
        Bells.save(p, b);
        ServerLevel level = p.level().getServer().overworld();
        BellWorld.Home home = BellWorld.get(level.getServer()).home(h.get().pos);
        if (home.frame == null || !level.getBlockState(home.frame).is(BellContent.BELL_FRAME.get())) {
            Direction face = p.getDirection();
            BlockPos at = p.blockPosition().relative(face, 6);
            at = at.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
            level.setBlock(at, BellContent.BELL_FRAME.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face.getOpposite()), Block.UPDATE_ALL);
            BellTower.placed(level, at, p);
        }
        hangAll(level, h.get(), home, p);
        BellTower.hang(level.getServer(), h.get());
        return rung;
    }

    /**
     * Hangs every bell in the player's bag (for tours): the frame's hooks first, then Bell Beams laid in a row beside the
     * frame, nine above the fire (away from where a belfry would rise), and a Great Beam thirteen up for each Great Bell.
     */
    private static void hangAll(ServerLevel level, HearthRecord h, BellWorld.Home home, ServerPlayer p) {
        if (home.frame == null) return;
        Direction face = level.getBlockState(home.frame).getOptionalValue(HorizontalDirectionalBlock.FACING).orElse(Direction.NORTH);
        Direction side = face.getClockWise();
        int beams = 0, greats = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            BellCatalog.Def d = BellItem.def(stack);
            if (d == null || home.hung.contains(d.no())) continue;
            BlockPos at;
            if (Hanging.great(d)) {
                at = home.frame.relative(side, 4 + greats++ * 2).relative(face, -4).atY(h.pos.getY() + Hanging.GREAT_ABOVE + 1);
                place(level, at, BellContent.GREAT_BEAM.get().defaultBlockState(), face);
            } else if (level.getBlockEntity(home.frame) instanceof HooksEntity f && Hanging.nearest(f, net.minecraft.world.phys.Vec3.atCenterOf(home.frame), true) >= 0) {
                at = home.frame;
            } else {
                int k = beams++ / 4; // four to a beam, the beams on both sides of the frame
                at = home.frame.relative(k % 2 == 0 ? side : side.getOpposite(), 4 + k / 2).atY(h.pos.getY() + Hanging.BEAM_ABOVE + 1);
                place(level, at, BellContent.BELL_BEAM.get().defaultBlockState(), face);
            }
            if (!(level.getBlockEntity(at) instanceof HooksEntity be)) continue;
            int hook = Hanging.nearest(be, net.minecraft.world.phys.Vec3.atCenterOf(at), true);
            if (hook < 0) continue;
            var hp = Hanging.hookPoint(level.getBlockState(at), hook).add(at.getX(), at.getY(), at.getZ());
            Hanging.hang(level, at, p, stack, hp, true);
        }
    }

    private static void place(ServerLevel level, BlockPos at, net.minecraft.world.level.block.state.BlockState s, Direction face) {
        if (!level.getBlockState(at).is(s.getBlock())) level.setBlock(at, s.setValue(HorizontalDirectionalBlock.FACING, face), Block.UPDATE_ALL);
    }
}
