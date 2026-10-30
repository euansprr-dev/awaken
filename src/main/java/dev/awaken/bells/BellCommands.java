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
 * Admin tools for the bells: {@code /awaken admin bells give <id> | tower <n> | song | belfry | ilse | deep | page}.
 * {@code tower} rings the first n bells home and sets a frame by the player's Hearth (for tours).
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
            p.sendSystemMessage(Component.literal("No bell frame at home: /awaken admin bells tower 50 first."));
            return 0;
        }
        if (home.stage < 2) {
            if (home.hung.size() < BellTower.TOWER) tower(p, BellTower.TOWER);
            BellTower.grow(level, h.get(), home);
        }
        home.clapper = home.relic = true;
        BellWorld.get(level.getServer()).setDirty();
        return GreatBelfry.build(level, h.get(), home) ? 1 : 0;
    }

    /** Rings the first {@code n} bells (the built ones first) for the player, sets a frame at home if there's none, grows it. */
    private static int tower(ServerPlayer p, int n) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty()) {
            p.sendSystemMessage(Component.literal("No home: found one first (/awaken admin kin yard)."));
            return 0;
        }
        // the Seed Bell only ever comes with the Great Belfry (/awaken admin bells belfry)
        int rung = Bells.count(p);
        for (BellCatalog.Def d : BellCatalog.ALL) if (rung < n && d.built() && d.no() != 0 && Bells.ring(p, d, "given by an admin")) rung++;
        ServerLevel level = p.level().getServer().overworld();
        BellWorld.Home home = BellWorld.get(level.getServer()).home(h.get().pos);
        if (home.frame == null || !level.getBlockState(home.frame).is(BellContent.BELL_FRAME.get())) {
            Direction face = p.getDirection();
            BlockPos at = p.blockPosition().relative(face, 6);
            at = at.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
            level.setBlock(at, BellContent.BELL_FRAME.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face.getOpposite()), Block.UPDATE_ALL);
            BellTower.placed(level, at, p);
        } else BellTower.hang(level.getServer(), h.get());
        return rung;
    }
}
